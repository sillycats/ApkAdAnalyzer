package com.shinegirls.apkadanalyzer.core

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * 全局语言（多语言）管理器。
 *
 * 支持"跟随系统"以及一份显式语言清单（默认简体中文 + 17 种语言）。
 * 选择持久化到 SharedPreferences，Activity 通过 [wrap] 包裹 baseContext（attachBaseContext）
 * 使资源（values-xx/strings.xml）在该 Activity 生命周期内按所选语言解析。
 */

object LocaleManager {

    private const val PREFS = "locale_prefs"
    private const val KEY_LANG = "app_lang"

    /** 跟随系统 */
    const val FOLLOW_SYSTEM = "system"

    /**
     * 支持的语言：languageId -> 资源 qualifier 后缀（values- 之后的部分）。
     * [FOLLOW_SYSTEM] 之外的显式语言，首个即默认语言。
     */
    val SUPPORTED: LinkedHashMap<String, String> = linkedMapOf(
        "zh" to "",
        "en" to "en",
        "zh-rTW" to "zh-rTW",
        "ja" to "ja",
        "ko" to "ko",
        "es" to "es",
        "pt" to "pt",
        "de" to "de",
        "fr" to "fr",
        "it" to "it",
        "ru" to "ru",
        "ar" to "ar",
        "hi" to "hi",
        "in" to "in",
        "th" to "th",
        "vi" to "vi",
        "ms" to "ms",
        "uk" to "uk"
    )

    /** 语言原生显示名（用于语言选择界面） */
    val DISPLAY_NAMES: Map<String, String> = mapOf(
        FOLLOW_SYSTEM to "跟随系统",
        "zh" to "简体中文",
        "en" to "English",
        "zh-rTW" to "繁體中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "es" to "Español",
        "pt" to "Português",
        "de" to "Deutsch",
        "fr" to "Français",
        "it" to "Italiano",
        "ru" to "Русский",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "in" to "Bahasa Indonesia",
        "th" to "ไทย",
        "vi" to "Tiếng Việt",
        "ms" to "Bahasa Melayu",
        "uk" to "Українська"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读取当前语言 id，默认跟随系统 */
    fun getLangId(context: Context): String =
        prefs(context).getString(KEY_LANG, FOLLOW_SYSTEM) ?: FOLLOW_SYSTEM

    fun setLangId(context: Context, langId: String): Boolean {
        return try {
            prefs(context).edit().putString(KEY_LANG, langId).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 将给定语言 id 解析为 Locale。
     * "system" 返回 Locale.getDefault()（系统区域）。
     */
    fun resolveLocale(langId: String): Locale {
        if (langId == FOLLOW_SYSTEM) return Locale.getDefault()
        val qualifier = SUPPORTED[langId] ?: return Locale.getDefault()
        return localeFromQualifier(qualifier)
    }

    /**
     * 由资源 qualifier 后缀解析 Locale。
     * ""（默认）-> 简体中文；其余支持 "en" / "zh-rTW" 等格式。
     */
    fun localeFromQualifier(qualifier: String): Locale {
        return when {
            qualifier.isEmpty() -> Locale.SIMPLIFIED_CHINESE
            qualifier == "zh-rTW" -> Locale.TRADITIONAL_CHINESE
            qualifier == "in" -> Locale("in", "ID")
            qualifier == "zh" -> Locale.SIMPLIFIED_CHINESE
            qualifier.contains("-") -> {
                val parts = qualifier.split("-")
                if (parts.size >= 2) {
                    try {
                        Locale(parts[0], parts[1].uppercase())
                    } catch (_: Exception) {
                        Locale(parts[0])
                    }
                } else Locale(parts[0])
            }
            else -> try { Locale(qualifier) } catch (_: Exception) { Locale.getDefault() }
        }
    }

    /** 应用当前语言的 Configuration（后续 Activity 使用 wrap() 包裹） */
    fun currentConfig(context: Context): Configuration {
        val langId = getLangId(context)
        val locale = resolveLocale(langId)
        val config = Configuration(context.resources.configuration)
        @Suppress("DEPRECATION")
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }
        return config
    }

    /** 用所选语言包裹 baseContext，便于 attachBaseContext 中调用 */
    fun wrap(base: Context): Context {
        val config = currentConfig(base)
        return base.createConfigurationContext(config)
    }
}
