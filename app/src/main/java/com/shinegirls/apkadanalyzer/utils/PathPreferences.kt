package com.shinegirls.apkadanalyzer.utils

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * 路径偏好管理器。
 *
 * 管理两个可自定义路径：
 * - 广告特征配置文件路径（ad_patterns.json 的完整路径）
 * - 分析结果（广告特征配置）导出目录路径
 *
 * 使用 SharedPreferences 持久化，默认值与 [Format.EXPORT_DIR] 一致。
 */
object PathPreferences {

    private const val PREFS_NAME = "path_preferences"
    private const val KEY_CONFIG_PATH = "config_file_path"
    private const val KEY_OUTPUT_DIR = "output_apk_dir"

    /** 默认配置文件完整路径。 */
    val DEFAULT_CONFIG_PATH: String = "${Format.EXPORT_DIR}/ad_patterns.json"

    /** 默认结果导出目录。 */
    val DEFAULT_OUTPUT_DIR: String = Format.EXPORT_DIR

    /** 缓存实例，避免重复调用 getSharedPreferences。 */
    private val prefsCache = HashMap<Context, SharedPreferences>()

    private fun getPrefs(context: Context): SharedPreferences =
        synchronized(prefsCache) {
            prefsCache.getOrPut(context.applicationContext) {
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

    /**
     * 获取当前广告特征配置文件完整路径。
     * 若用户未自定义，返回默认路径。
     */
    fun getConfigFilePath(context: Context): String {
        return getPrefs(context).getString(KEY_CONFIG_PATH, DEFAULT_CONFIG_PATH) ?: DEFAULT_CONFIG_PATH
    }

    /**
     * 设置广告特征配置文件完整路径。
     * 设置后会自动确保目录存在。
     */
    fun setConfigFilePath(context: Context, path: String): Boolean {
        // 确保父目录存在
        val dir = File(path).parentFile
        if (dir != null) {
            try {
                if (!dir.exists()) dir.mkdirs()
            } catch (_: Exception) {
                return false
            }
        }
        return try {
            getPrefs(context).edit().putString(KEY_CONFIG_PATH, path).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取当前结果导出目录路径。
     * 若用户未自定义，返回默认目录。
     */
    fun getOutputDir(context: Context): String {
        return getPrefs(context).getString(KEY_OUTPUT_DIR, DEFAULT_OUTPUT_DIR) ?: DEFAULT_OUTPUT_DIR
    }

    /**
     * 设置结果导出目录路径。
     * 设置后会自动确保目录存在。
     */
    fun setOutputDir(context: Context, path: String): Boolean {
        try {
            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {
            return false
        }
        return try {
            getPrefs(context).edit().putString(KEY_OUTPUT_DIR, path).apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}