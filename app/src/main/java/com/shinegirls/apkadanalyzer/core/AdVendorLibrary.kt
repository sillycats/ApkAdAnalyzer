package com.shinegirls.apkadanalyzer.core

import android.content.Context
import com.shinegirls.apkadanalyzer.utils.PathPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线广告厂商广告特征库（网络获取）。
 *
 * 从远程地址拉取"按广告厂商组织"的广告特征（SDK 包名 / 类名 / 方法 / URL / 原生库 /
 * assets 等），覆盖国内外主流广告厂商。拉取成功后解析为 [Vendor] 列表并缓存到本地，
 * 供 APK 分析时与内置特征合并使用：
 * - 覆盖范围：国内厂商（广点通 / 百度 / 快手 / 穿山甲 / 华为 / Sigmob / TopOn 等）+ 国际厂商
 *   （AdMob / Meta / AppLovin / Unity / ironSource / Mintegral / Pangle / Vungle 等）。
 * - 合并策略：将每个厂商的 features 累加为一份 [AdPatternConfig.AdPatterns]（去重），
 *   与内置（assets 默认配置 + 本地 ad_patterns.json）合并后再交给 [AdFeatureAnalyzer] 分析。
 * - 离线兜底：成功拉取后缓存原始 JSON 到本地，网络不可用时优先读取缓存，保证分析不中断。
 *
 * 远程库 JSON 结构（供应商列表）：
 * {
 *   "version": 1,
 *   "updated": "2026-08-21",
 *   "vendors": [
 *     {
 *       "id": "admob_google",
 *       "name": "Google AdMob / AdManager",
 *       "region": "国际",
 *       "homepage": "https://admob.google.com",
 *       "features": { "sdk_packages": [...], "class_keywords": [...], ... }
 *     }
 *   ]
 * }
 */
object AdVendorLibrary {

    /** 在线厂商库主地址：使用 GitHub 仓库内的 online_ad_vendors.json。 */
    const val DEFAULT_VENDOR_URL =
        "https://raw.githubusercontent.com/sillycats/ApkAdAnalyzer/main/online_ad_vendors.json"

    /** 备用在线厂商库地址：与主地址一致时自动去重。 */
    const val FALLBACK_VENDOR_URL =
        "https://raw.githubusercontent.com/sillycats/ApkAdAnalyzer/main/online_ad_vendors.json"

    /** 缓存文件名（存放于除外存储的配置目录下，跟随用户自定义路径）。 */
    private const val CACHE_FILE_NAME = "online_ad_vendors.json"

    /** 网络超时（毫秒）。 */
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    /**
     * 单个广告厂商及其广告特征。
     *
     * @param id 厂商唯一标识
     * @param name 厂商名称
     * @param region 地区：国际 / 国内
     * @param homepage 官网地址
     * @param features 该厂商的广告特征（分类 -> 模式列表）
     */
    data class Vendor(
        val id: String,
        val name: String,
        val region: String,
        val homepage: String,
        val features: AdPatternConfig.AdPatterns
    )

    /** 厂商库拉取结果。 */
    data class VendorLibrary(
        val version: Int,
        val updated: String,
        val vendors: List<Vendor>,
        val sourceUrl: String
    )

    /** 合并统计结果。 */
    data class MergeSummary(
        val vendorCount: Int,
        val domesticCount: Int,
        val foreignCount: Int,
        val featureCount: Int,
        val sourceUrl: String
    )

    /**
     * 依次尝试多个在线厂商库地址拉取（同步，需在子线程执行）。
     *
     * @param urls 按优先级排列的地址列表
     * @return 首个成功解析的 [VendorLibrary]；全部失败返回 null。
     */
    fun fetchVendorLibrary(urls: List<String>): VendorLibrary? {
        for (url in urls) {
            val lib = fetchFromSingleUrl(url)
            if (lib != null) return lib
        }
        return null
    }

    /**
     * 从单个地址拉取并解析在线厂商库（同步，需在子线程执行）。
     */
    private fun fetchFromSingleUrl(url: String): VendorLibrary? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ApkAdAnalyzer/1.0")
                instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return null
                val sb = StringBuilder()
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append('\n')
                    }
                }
                parseVendorLibrary(sb.toString().trim(), url)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 解析厂商库 JSON 文本。 */
    private fun parseVendorLibrary(jsonStr: String, sourceUrl: String): VendorLibrary? {
        return try {
            val json = JSONObject(jsonStr)
            val vendorsArr = json.optJSONArray("vendors") ?: return null
            val vendors = mutableListOf<Vendor>()
            for (i in 0 until vendorsArr.length()) {
                val v = vendorsArr.getJSONObject(i)
                val features = v.optJSONObject("features") ?: JSONObject()
                vendors.add(
                    Vendor(
                        id = v.optString("id", ""),
                        name = v.optString("name", ""),
                        region = v.optString("region", ""),
                        homepage = v.optString("homepage", ""),
                        features = parseFeatures(features)
                    )
                )
            }
            VendorLibrary(
                version = json.optInt("version", 1),
                updated = json.optString("updated", ""),
                vendors = vendors,
                sourceUrl = sourceUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 将厂商的 features JSON 解析为 [AdPatternConfig.AdPatterns]。 */
    private fun parseFeatures(json: JSONObject): AdPatternConfig.AdPatterns {
        return AdPatternConfig.AdPatterns(
            sdkPackages = jsonStringList(json, "sdk_packages"),
            classKeywords = jsonStringList(json, "class_keywords"),
            methodPatterns = jsonStringList(json, "method_patterns"),
            urlPatterns = jsonStringList(json, "url_patterns"),
            adViewNames = jsonStringList(json, "ad_view_names"),
            adActivities = jsonStringList(json, "ad_activities"),
            adServices = jsonStringList(json, "ad_services"),
            adReceivers = jsonStringList(json, "ad_receivers"),
            forceTrueMethodNames = jsonStringList(json, "force_true_methods"),
            forceFalseMethodNames = jsonStringList(json, "force_false_methods"),
            adAssetPaths = jsonStringList(json, "ad_asset_paths"),
            libFileKeywords = jsonStringList(json, "lib_file_keywords"),
            assetKeywords = jsonStringList(json, "asset_keywords"),
            methodNeutralizeKeywords = jsonStringList(json, "method_neutralize_keywords"),
            adPermissions = jsonStringList(json, "ad_permissions"),
            rootFileKeywords = jsonStringList(json, "root_file_keywords"),
            resLayoutKeywords = jsonStringList(json, "res_layout_keywords"),
            stringPatterns = jsonStringList(json, "string_patterns"),
            flutterPatterns = jsonStringList(json, "flutter_patterns")
        )
    }

    private fun jsonStringList(json: JSONObject, key: String): MutableList<String> {
        val result = mutableListOf<String>()
        val arr = json.optJSONArray(key) ?: return result
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) result.add(s)
        }
        return result
    }

    /**
     * 将厂商库全部厂商的 features 合并为一份 [AdPatternConfig.AdPatterns]（并集去重）。
     */
    fun toMergedFeatures(library: VendorLibrary): AdPatternConfig.AdPatterns {
        val all = library.vendors.map { it.features }
        return if (all.isEmpty()) AdPatternConfig.AdPatterns() else AdPatternConfig.merge(all)
    }

    /**
     * 统计厂商库概况：厂商数、国内/国际、合并后的特征总数。
     */
    fun summarize(library: VendorLibrary): MergeSummary {
        val merged = toMergedFeatures(library)
        return MergeSummary(
            vendorCount = library.vendors.size,
            domesticCount = library.vendors.count { it.region.contains("国内") },
            foreignCount = library.vendors.count { it.region.contains("国际") || it.region.contains("国外") },
            featureCount = merged.totalCount(),
            sourceUrl = library.sourceUrl
        )
    }

    // ==================== 本地缓存 ====================

    /**
     * 读取缓存的厂商库 JSON 文本（位于配置目录下）。
     * 返回 null 表示无缓存或读取失败。
     */
    fun readCached(context: Context): String? {
        return try {
            val file = getCacheFile(context)
            if (!file.exists()) return null
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将厂商库原始 JSON 文本写入缓存（位于配置目录下）。
     */
    fun writeCache(context: Context, jsonStr: String): Boolean {
        return try {
            val file = getCacheFile(context)
            file.parentFile?.mkdirs()
            file.writeText(jsonStr, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从缓存文本解析为 [VendorLibrary]。
     */
    fun parseCached(jsonStr: String): VendorLibrary? {
        return try {
            parseVendorLibrary(jsonStr, "cache")
        } catch (_: Exception) {
            null
        }
    }

    /** 获取缓存文件路径（配置目录 / online_ad_vendors.json）。 */
    fun getCacheFile(context: Context): java.io.File {
        val configFile = AdPatternConfig.getConfigFile(context)
        val parent = configFile.parentFile ?: java.io.File(PathPreferences.getConfigFilePath(context)).parentFile
        return java.io.File(parent, CACHE_FILE_NAME)
    }

    /** 获取在线厂商库地址（预留自定义能力，目前使用默认+备用）。 */
    fun getVendorUrls(): List<String> {
        val urls = LinkedHashSet<String>()
        urls.add(DEFAULT_VENDOR_URL)
        urls.add(FALLBACK_VENDOR_URL)
        return urls.toList()
    }
}