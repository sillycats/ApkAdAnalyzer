package com.shinegirls.apkadanalyzer.core

/**
 * APK 广告特征分析结果数据模型。
 *
 * 与 [AdPatternConfig] 的配置格式（ad_patterns.json）一一对应：
 * 对选定的 APK 扫描后，将"命中的广告特征"按分类聚合，可直接序列化为
 * 与分析所用配置文件格式一致的 JSON，从而让用户把分析结果保存为本地配置。
 */
object AdAnalysisModels {

    /**
     * 一次 APK 广告特征分析的结果。
     *
     * [matches] 以 [AdPatternConfig.Category] 为键，值为"在该分类下命中的特征模式"（已去重）。
     * 值为用户配置中的模式原文（例如 sdk_packages 命中时存 "com.google.android.gms.ads"），
     * 保证生成结果可直接作为 ad_patterns.json 配置使用。
     */
    data class AdAnalysisResult(
        var apkName: String = "",
        var packageName: String = "",
        var dexCount: Int = 0,
        var classCount: Int = 0,
        var methodCount: Int = 0,
        var fileCount: Int = 0,
        var isFlutter: Boolean = false,
        var flutterLibappCount: Int = 0,
        var flutterStringCount: Int = 0,
        var matches: Map<AdPatternConfig.Category, List<String>> = emptyMap()
    ) {

        /** 全部命中的广告特征总数。 */
        val totalHitCount: Int get() = matches.values.sumOf { it.size }

        /** 命中的分类列表（按枚举声明的顺序）。 */
        fun hitCategories(): List<AdPatternConfig.Category> =
            AdPatternConfig.Category.values().filter { matches[it].orEmpty().isNotEmpty() }

        /** 是否命中任何广告特征。 */
        val hasHit: Boolean get() = totalHitCount > 0

        /**
         * 将命中的特征聚合为一份可直接使用的 [AdPatternConfig.AdPatterns] 配置。
         * 仅包含本 APK 实际命中的特征，可作为该应用专用的去广告配置文件。
         */
        fun toConfig(): AdPatternConfig.AdPatterns {
            val config = AdPatternConfig.AdPatterns()
            for ((category, items) in matches) {
                if (items.isEmpty()) continue
                AdPatternConfig.getCategoryList(config, category)
                    .addAll(items.distinct())
            }
            return config
        }

        /**
         * 生成与分析所用配置文件（ad_patterns.json）格式一致的 JSON 文本。
         * 字段名与 [AdPatternConfig] 的 Category 键完全对应，可直接保存/复制。
         */
        fun toConfigJson(): String {
            return try {
                AdPatternConfig.toJson(toConfig()).toString(2)
            } catch (_: Exception) {
                "{}"
            }
        }
    }
}

/**
 * 顶层别名，便于各模块（分析引擎、UI 等）以简单名 [AdAnalysisResult] 引用。
 * 实际类型定义位于 [AdAnalysisModels] 内部。
 */
typealias AdAnalysisResult = AdAnalysisModels.AdAnalysisResult