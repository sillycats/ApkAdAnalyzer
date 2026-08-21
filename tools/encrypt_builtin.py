#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把内置广告特征明文 JSON 加密为 assets 混淆二进制 (.enc)。

必须与 app/src/main/cpp/native_crypto.cpp 中的算法完全一致：
- 密钥来源：同款种子常量 + 盐，deriveKeyByte 逐字节派生
- expandKeystream：64 字节一块 + 前向滚动混合
- process：密钥流异或 + 位反转扩散（往返对称）

用法: python3 tools/encrypt_builtin.py <input.json> <output.enc>
示例: python3 tools/encrypt_builtin.py patterns/ad_patterns_default.json app/src/main/assets/ad_patterns_default.enc
"""
import sys, os

# ---- 与 C++ 完全一致的常量与算法 ----
SEED_PART_A = 0x5A ^ 0x37
SEED_PART_B = 0xC3 ^ 0x6D
SEED_PART_C = 0x9E ^ 0x2B
SEED_PART_D = 0x71 ^ 0xD4
SALT = [0xD2, 0x4F, 0x1A, 0x8C, 0x67, 0xB3, 0xE9, 0x05]

def derive_key_byte(index, length, rnd):
    mix = SEED_PART_A
    mix ^= (SEED_PART_B ^ ((index * (rnd + 1)) & 0xFF))
    mix ^= (SEED_PART_C + ((index >> 2) & 0xFF) + (rnd * 7)) & 0xFF
    mix ^= (SEED_PART_D ^ SALT[index % len(SALT)])
    mix = ((mix << 3) | (mix >> 5)) & 0xFF
    mix ^= 0xA5
    mix = (mix * 31) & 0xFF
    mix ^= (length & 0xFF)
    mix ^= (rnd * 13) & 0xFF
    return mix & 0xFF

def expand_keystream(length):
    ks = [0] * length
    block = 64
    rnd = 0
    i = 0
    while i < length:
        for j in range(block):
            if i >= length:
                break
            ks[i] = derive_key_byte((j ^ (i >> 6)), length, rnd)
            i += 1
        rnd += 1
    # 前向滚动混合
    carry = 0x5F
    for i in range(length):
        ks[i] ^= carry
        carry = (ks[i] + (i & 0x1F)) & 0xFF
    return ks

def bit_reverse(b):
    r = (((b & 0x01) << 7) | ((b & 0x02) << 5) | ((b & 0x04) << 3) |
         ((b & 0x08) << 1) | ((b & 0x10) >> 1) | ((b & 0x20) >> 3) |
         ((b & 0x40) >> 5) | ((b & 0x80) >> 7))
    return r & 0xFF

def process(data: bytes, decrypt: bool = False):
    """加密 uses encrypt direction (bit_reverse(x)^k); decrypt=True 还原。"""
    ks = expand_keystream(len(data))
    out = bytearray(len(data))
    for i, b in enumerate(data):
        if decrypt:
            out[i] = bit_reverse(b ^ ks[i])
        else:
            out[i] = bit_reverse(b) ^ ks[i]
    return bytes(out)

def main():
    if len(sys.argv) != 3:
        print("usage: python3 encrypt_builtin.py <input.json> <output.enc>")
        sys.exit(1)
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "rb") as f:
        plain = f.read()
    enc = process(plain)
    os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
    with open(dst, "wb") as f:
        f.write(enc)
    print(f"encrypted {len(plain)} bytes -> {dst} ({len(enc)} bytes)")

if __name__ == "__main__":
    main()