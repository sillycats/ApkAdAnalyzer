//
// native_crypto.cpp
//
// 广告特征库混淆加密/解密原生实现（JNI）。
//
// 设计要点：
// 1) 密钥不以明文常量存在于 so 中，而是拆成多段"种子常量"，在运行时
//    经过移位 / 异或 / 累加等运算动态重组，提高静态逆向提取门槛。
// 2) 采用自研流式混淆：基于密钥派生字节流的异或 + 位扩散，对明文逐块处理。
// 3) 同一算法同时支持 加密(encrypt) 与 解密(decrypt)，供运行态与缓存复用。
//
// 说明：客户端 so 加密无法做到"绝对不可破解"，此实现通过密钥拆分 + 动态重组
// 与算法隐藏，显著提高逆向成本，满足"避免被轻易解密"的目标。
//

#include <jni.h>
#include <cstdint>
#include <cstring>
#include <vector>

namespace {

// ==================== 密钥派生 ====================
// 种子常量不直接是密钥字节；先做加盐重组，再经非线性混合展开为完整密钥。
// 种子以"字段拼段 + 或位移"翻转存放，避免以明文 16 字节密钥直接出现在二进制。

constexpr uint8_t SEED_PART_A = 0x5A ^ 0x37;   // 动态位后仍非密钥本身
constexpr uint8_t SEED_PART_B = 0xC3 ^ 0x6D;
constexpr uint8_t SEED_PART_C = 0x9E ^ 0x2B;
constexpr uint8_t SEED_PART_D = 0x71 ^ 0xD4;

constexpr uint8_t SALT[] = {0xD2, 0x4F, 0x1A, 0x8C, 0x67, 0xB3, 0xE9, 0x05};

// 展开密钥：由种子 + 盐经多次异或/加法/取反混合，得到等长密钥流初始状态。
uint8_t deriveKeyByte(uint32_t index, size_t len, uint32_t round) {
    uint8_t mix = SEED_PART_A;
    mix ^= (uint8_t)(SEED_PART_B ^ (uint8_t)((index * (round + 1)) & 0xFF));
    mix ^= (uint8_t)(SEED_PART_C + (uint8_t)((index >> 2) & 0xFF) + (uint8_t)(round * 7));
    mix ^= (uint8_t)(SEED_PART_D ^ SALT[index % sizeof(SALT)]);
    // 位扩散：让相邻密钥字节差异最大化
    mix = (uint8_t)((mix << 3) | (mix >> 5));
    mix ^= (uint8_t)0xA5;
    mix = (uint8_t)((mix * 31) & 0xFF);
    mix ^= (uint8_t)(len & 0xFF);
    return (uint8_t)(mix ^ (uint8_t)(round * 13));
}

// 生成与明文等长的密钥流（按 64 字节一块 + 块内轮转，强化扩散）。
void expandKeystream(std::vector<uint8_t>& ks, size_t len) {
    ks.resize(len);
    size_t block = 64;
    uint32_t round = 0;
    size_t i = 0;
    while (i < len) {
        for (size_t j = 0; j < block && i < len; ++j, ++i) {
            ks[i] = deriveKeyByte((uint32_t)(j ^ (i >> 6)), len, round);
        }
        ++round;
    }
    // 二次扩散：前向滚动混合，使密钥流与位置强相关
    uint8_t carry = 0x5F;
    for (size_t i = 0; i < len; ++i) {
        ks[i] ^= carry;
        carry = (uint8_t)(ks[i] + (i & 0x1F));
    }
}

// ==================== 加解密核心 ====================
// 位反转(bit-reverse)是线性的：bitreverse(a^b)=bitreverse(a)^bitreverse(b)，
// 且对合：bitreverse(bitreverse(x))=x。据此构造可逆对，
//   加密: out[i] = bitreverse(x[i]) ^ k[i]
//   解密: out[i] = bitreverse(x[i] ^ k[i])
// 验证: 解密(加密) = bitreverse(bitreverse(x)^k ^ k) = bitreverse(bitreverse(x)) = x。
inline uint8_t bitReverse(uint8_t b) {
    return (uint8_t)(((b & 0x01) << 7) | ((b & 0x02) << 5) |
                     ((b & 0x04) << 3) | ((b & 0x08) << 1) |
                     ((b & 0x10) >> 1) | ((b & 0x20) >> 3) |
                     ((b & 0x40) >> 5) | ((b & 0x80) >> 7));
}

// 加密方向：out = bitreverse(input) ^ ks
bool encryptProcess(const uint8_t* input, size_t len, std::vector<uint8_t>& out) {
    if (input == nullptr) return false;
    std::vector<uint8_t> ks;
    expandKeystream(ks, len);
    out.resize(len);
    for (size_t i = 0; i < len; ++i) {
        out[i] = (uint8_t)(bitReverse(input[i]) ^ ks[i]);
    }
    return true;
}

// 解密方向：out = bitreverse(input ^ ks)
bool decryptProcess(const uint8_t* input, size_t len, std::vector<uint8_t>& out) {
    if (input == nullptr) return false;
    std::vector<uint8_t> ks;
    expandKeystream(ks, len);
    out.resize(len);
    for (size_t i = 0; i < len; ++i) {
        out[i] = bitReverse((uint8_t)(input[i] ^ ks[i]));
    }
    return true;
}

} // namespace

//
// JNI 导出（Kotlin 侧通过 System.loadLibrary("native_crypto") 加载）。
//

// nativeEncrypt([B)[B  加密
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_shinegirls_apkadanalyzer_core_NativeCrypto_nativeEncrypt(
        JNIEnv* env, jobject, jbyteArray input) {
    if (input == nullptr) return nullptr;
    jsize len = env->GetArrayLength(input);
    std::vector<uint8_t> data(static_cast<size_t>(len));
    env->GetByteArrayRegion(input, 0, len, reinterpret_cast<jbyte*>(data.data()));

    std::vector<uint8_t> out;
    if (!encryptProcess(data.data(), data.size(), out)) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()),
                            reinterpret_cast<const jbyte*>(out.data()));
    return result;
}

// nativeDecrypt([B)[B  解密
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_shinegirls_apkadanalyzer_core_NativeCrypto_nativeDecrypt(
        JNIEnv* env, jobject, jbyteArray input) {
    if (input == nullptr) return nullptr;
    jsize len = env->GetArrayLength(input);
    std::vector<uint8_t> data(static_cast<size_t>(len));
    env->GetByteArrayRegion(input, 0, len, reinterpret_cast<jbyte*>(data.data()));

    std::vector<uint8_t> out;
    if (!decryptProcess(data.data(), data.size(), out)) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()),
                            reinterpret_cast<const jbyte*>(out.data()));
    return result;
}