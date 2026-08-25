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

    /** 授权配置文件远程地址（仓库根目录，raw 直链）。 */
    private const val AUTH_CONFIG_URL =
        "https://raw.githubusercontent.com/sillycats/ApkAdAnalyzer/main/auth_config.json"

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
        try {
            val conn = URL(AUTH_CONFIG_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) return@withContext 0
                val bytes = conn.inputStream.use { stream ->
                    stream.readBytes().let { if (it.size > MAX_BYTES) it.copyOf(MAX_BYTES) else it }
                }
                if (bytes.isEmpty()) return@withContext 0
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
