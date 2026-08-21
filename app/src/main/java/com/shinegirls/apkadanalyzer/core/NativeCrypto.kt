package com.shinegirls.apkadanalyzer.core

import java.io.File

/**
 * 广告特征混淆加密的原生解密适配层（JNI）。
 *
 * 内置特征与在线缓存均以二进制混淆形式存储，运行时通过本类调用
 * `libnative_crypto.so` 完成解密，避免明文特征直接暴露在 assets / 缓存文件中。
 * 密钥不在 so 中以明文常量存在，而是拆分种子在运行时动态派生，提高逆向提取门槛。
 */
object NativeCrypto {

    private var loadAttempted = false
    private var loaded = false

    @Synchronized
    private fun ensureLoaded(): Boolean {
        if (loadAttempted) return loaded
        loadAttempted = true
        loaded = try {
            System.loadLibrary("native_crypto")
            true
        } catch (_: Throwable) {
            // 原生库缺失或损坏时降级：解密将失败，下方调用会抛出并回退默认
            false
        }
        return loaded
    }

    /** 原生解密接口（由 native_crypto.cpp 导出）。 */
    private external fun nativeDecrypt(input: ByteArray): ByteArray

    /** 原生加密接口（供构造/缓存加密使用）。 */
    private external fun nativeEncrypt(input: ByteArray): ByteArray

    /** 是否已加载原生库。 */
    fun isAvailable(): Boolean = ensureLoaded()

    /**
     * 解密一段二进制数据，返回明文。
     *
     * @throws IllegalStateException 原生库不可用或解密失败。
     */
    @Throws(IllegalStateException::class)
    fun decrypt(data: ByteArray): ByteArray {
        if (!ensureLoaded()) throw IllegalStateException("原生解密库不可用")
        return nativeDecrypt(data)
    }

    /**
     * 加密一段二进制数据（用于在线缓存写入）。
     *
     * @throws IllegalStateException 原生库不可用或加密失败。
     */
    @Throws(IllegalStateException::class)
    fun encrypt(data: ByteArray): ByteArray {
        if (!ensureLoaded()) throw IllegalStateException("原生加密库不可用")
        return nativeEncrypt(data)
    }

    /** 便捷：解密返回 UTF-8 字符串。 */
    fun decryptToString(data: ByteArray): String = String(decrypt(data), Charsets.UTF_8)

    /** 便捷：加密字符串为字节。 */
    fun encryptBytes(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))

    /**
     * 从加密文件读取（不存在或失败返回 null）。
     */
    fun readEncryptedFile(file: File): String? {
        return try {
            if (!file.exists()) null
            else decryptToString(file.readBytes())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 写入加密文件（目录自动创建；异常返回 false）。
     */
    fun writeEncryptedFile(file: File, plainText: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(encryptBytes(plainText))
            true
        } catch (_: Exception) {
            false
        }
    }
    /** 设置远程授权配置（原生，见 native_crypto.cpp）。返回 0=无效 1=已授权 2=已吊销。 */
    private external fun nativeSetAuthConfig(config: ByteArray): Int

    /** 查询是否已被远程吊销（原生）。 */
    private external fun nativeIsRevoked(): Boolean

    /**
     * 提交远程授权配置原始字节给原生层校验并应用。
     *
     * 原生层会做 HMAC-SHA256 签名校验，防伪造；配置 authorized=false 即远程吊销，
     * 此后加密/解密返回 null 导致功能停用。配置不可达或签名无效时保持原状（默认未吊销）。
     *
     * @return 0=无效 1=已授权 2=已吊销
     */
    fun setAuthConfig(config: ByteArray): Int {
        if (!ensureLoaded()) return 0
        return try { nativeSetAuthConfig(config) }
        catch (_: Throwable) { 0 }
    }

    /** 当前是否已被远程吊销（原生库缺失时视为 false，保证正版离线可用）。 */
    fun isRevoked(): Boolean {
        if (!ensureLoaded()) return false
        return try { nativeIsRevoked() } catch (_: Throwable) { false }
    }
}