package com.shinegirls.apkadanalyzer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程授权管理。
 *
 * 授权配置文件 `auth_config.json` 位于公开仓库根目录，远程可访问。
 * 应用启动时拉取该文件原始字节交给原生层（NativeCrypto.setAuthConfig）做
 * HMAC-SHA256 签名校验并应用：authorized=false 即作者远程吊销，此后原生
 * 加密/解密一律失败，实现"远程停止授权、防止非法分发"。
 *
 * 网络不可达 / 签名无效时保持原状（默认未吊销），保证正版离线可用。
 */
object RemoteAuth {

    /** 授权配置文件地址列表（按优先级，jsDelivr CDN 为主，GitHub raw 为备用）。 */
    private val AUTH_CONFIG_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/sillycats/ApkAdAnalyzer@main/auth_config.json",
        "https://raw.githubusercontent.com/sillycats/ApkAdAnalyzer/main/auth_config.json"
    )

    /** 拉取超时（毫秒）。 */
    private const val TIMEOUT_MS = 8000

    /** 配置最大字节数（防止异常大文件）。 */
    private const val MAX_BYTES = 4096

    /**
     * 拉取并应用远程授权配置。
     *
     * @return 0=无效/失败 1=已授权 2=已吊销
     */
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        for (url in AUTH_CONFIG_URLS) {
            val result = fetchFrom(url)
            if (result != 0) return@withContext result
        }
        0
    }

    /** 从单个地址拉取授权配置并应用；成功(1/2)或失败不可达(0)返回对应值。 */
    private fun fetchFrom(url: String): Int {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) return 0
                val stream = conn.inputStream
                val bytes = stream.readBytes().let { if (it.size > MAX_BYTES) it.copyOf(MAX_BYTES) else it }
                if (bytes.isEmpty()) return 0
                NativeCrypto.setAuthConfig(bytes)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            0
        }
    }

    /** 当前是否已被远程吊销。 */
    fun isRevoked(): Boolean = NativeCrypto.isRevoked()
}
