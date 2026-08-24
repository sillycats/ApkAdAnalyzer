package com.shinegirls.apkadanalyzer.core

import android.content.Context
import com.shinegirls.apkadanalyzer.utils.Format
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 内置广告厂商广告特征库（本地读取）。
 *
 * 厂商特征随 APK 打包在 assets 的混淆加密文件（online_ad_vendors_default.enc）中，
 * 全程离线读取，不发起任何网络请求。文件以混淆加密形式存储，运行态经
 * [NativeCrypto]（libnative_crypto.so）解密后解析为 [Vendor] 列表，供 APK 分析时
 * 与内置特征合并使用：
 * - 覆盖范围：国内厂商（广点通 / 百度 / 快手 / 穿山甲 / 华为 / Sigmob / TopOn 等）+ 国际厂商
 *   （AdMob / Meta / AppLovin / Unity / ironSource / Mintegral / Pangle / Vungle 等）。
 * - 合并策略：将每个厂商的 features 累加为一份 [AdPatternConfig.AdPatterns]（去重），
 *   与内置（assets 默认配置 + 本地 ad_patterns.json）合并后再交给 [AdFeatureAnalyzer] 分析。
 *
 * 厂商库 JSON 结构（供应商列表）：
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

    /** 内置广告厂商库（assets 中，混淆加密）。随 APK 打包，全程离线加载，不发起任何网络请求。 */
    private const val EMBEDDED_ASSET_NAME = "online_ad_vendors_default.enc"

    /** 应用首次分析时，将内置厂商库加密落盘到外部存储的文件名（便于后续直接调用本地加密副本）。 */
    private const val EXTERNAL_FILE_NAME = "online_ad_vendors.enc"

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
     * 解密广告厂商库原始字节（兼容旧版明文与新版加密格式）。
     *
     * 厂商库以 [NativeCrypto]（libnative_crypto.so）混淆加密存储，避免广告厂商特征
     * 以明文暴露在 APK 资源中；通过首字节判断：明文 JSON 以 '{' 开头，其余按加密格式解密。
     */
    private fun decryptVendorBytes(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        // 明文 JSON（旧版兼容）
        if (bytes[0] == '{'.code.toByte()) {
            return String(bytes, Charsets.UTF_8)
        }
        // 加密格式：原生解密
        return try {
            NativeCrypto.decryptToString(bytes)
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

    /**
     * 读取内置广告厂商库（优先外部加密副本，缺失则从 assets 复制并落到外部后再调用）。
     *
     * 选择 APK 分析时调用。策略：
     * 1. 先确保 /storage/emulated/0/ApkAnalyzer/online_ad_vendors.enc 存在：
     *    若外部不存在，则把 assets 中的 online_ad_vendors_default.enc（混淆加密字节原样复制）
     *    写入外部目录，实现"把内置配置写到外部并调用"。
     * 2. 优先从外部加密副本解密调用；外部缺失或解密失败时，回退直接从 assets 解密调用。
     *
     * @return 厂商库；assets 与外部副本均不可用时返回 null。
     */
    fun readEmbedded(context: Context): VendorLibrary? {
        // 1. 确保外部加密副本存在（assets 原始加密字节赋值到外部目录）
        val externalFile = File(Format.EXPORT_DIR, EXTERNAL_FILE_NAME)
        if (!externalFile.exists()) {
            copyAssetToExternal(context, EMBEDDED_ASSET_NAME, externalFile)
        }

        // 2. 优先从外部加密副本解密调用
        val externalStr = NativeCrypto.readEncryptedFile(externalFile)
        if (externalStr != null) {
            val lib = parseVendorLibrary(externalStr, "external")
            if (lib != null) return lib
        }

        // 3. 回退直接从 assets 解密调用
        return try {
            val bytes = context.assets.open(EMBEDDED_ASSET_NAME).use { it.readBytes() }
            val jsonStr = decryptVendorBytes(bytes) ?: return null
            parseVendorLibrary(jsonStr, "embedded")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将 assets 内的加密文件原样复制到外部存储目录（保持混淆加密字节不变）。
     * 写失败会被忽略——assets 仍可直接解密回退，不影响功能。
     */
    private fun copyAssetToExternal(context: Context, assetName: String, target: File) {
        try {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            if (bytes.isEmpty()) return
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        } catch (_: Exception) {
        }
    }
}