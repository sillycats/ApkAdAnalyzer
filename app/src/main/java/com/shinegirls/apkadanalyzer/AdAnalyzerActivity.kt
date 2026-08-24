package com.shinegirls.apkadanalyzer

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.shinegirls.apkadanalyzer.core.AdAnalysisResult
import com.shinegirls.apkadanalyzer.core.AdFeatureAnalyzer
import com.shinegirls.apkadanalyzer.core.AdPatternConfig
import com.shinegirls.apkadanalyzer.core.AdVendorLibrary
import com.shinegirls.apkadanalyzer.core.ThemeManager
import com.shinegirls.apkadanalyzer.core.RemoteAuth
import com.shinegirls.apkadanalyzer.core.LocaleManager
import com.shinegirls.apkadanalyzer.utils.Format
import com.shinegirls.apkadanalyzer.utils.PathPreferences
import com.shinegirls.apkadanalyzer.utils.UiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * APK 广告特征分析页面。
 *
 * 选择 APK 后，分析其包含的广告特征（含 Flutter 应用），将命中的特征按分类展示，
 * 并可生成与 ad_patterns.json 格式完全一致的广告特征配置文件，支持复制或保存到本地。
 */
class AdAnalyzerActivity : BaseActivity() {

    private companion object {
        private const val REQUEST_CODE_PICK_APK = 2001
        private const val REQUEST_CODE_PERMISSIONS = 2002
    }

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvResult: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvApkName: TextView
    private lateinit var tvProgress: TextView
    private lateinit var btnSelectApk: MaterialButton
    private lateinit var btnCopyConfig: MaterialButton
    private lateinit var btnSaveConfig: MaterialButton
    private lateinit var btnClear: ImageButton

    private var currentResult: AdAnalysisResult? = null
    private var currentApkUri: Uri? = null
    private var isAnalyzing = false

    private val logBuffer = StringBuilder()

    /** 最近一次上报的进度百分比，用于节流 UI 刷新。 */
    private var lastProgressPct = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_analyzer)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // 标题/副标题由布局中的多语言 TextView 展示；隐藏 Toolbar 自带的默认应用名，避免语言不跟随/重复显示
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)

        progressBar = findViewById(R.id.progressBar)
        tvResult = findViewById(R.id.tvResult)
        scrollView = findViewById(R.id.scrollView)
        tvApkName = findViewById(R.id.tvApkName)
        tvProgress = findViewById(R.id.tvProgress)
        btnSelectApk = findViewById(R.id.btnSelectApk)
        btnCopyConfig = findViewById(R.id.btnCopyConfig)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
        btnClear = findViewById(R.id.btnClear)

        btnSelectApk.setOnClickListener {
            if (isAnalyzing) return@setOnClickListener
            checkPermissionsAndPick()
        }

        btnCopyConfig.setOnClickListener {
            val result = currentResult
            if (result == null) {
                UiUtils.warning(this, getString(R.string.toast_select_apk_first))
                return@setOnClickListener
            }
            val json = result.toConfigJson()
            val clip = ClipData.newPlainText(getString(R.string.clip_ad_config), json)
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            UiUtils.success(this, getString(R.string.toast_config_copied))
        }

        btnSaveConfig.setOnClickListener {
            val result = currentResult
            if (result == null) {
                UiUtils.warning(this, getString(R.string.toast_select_apk_first))
                return@setOnClickListener
            }
            saveConfigLocally(result)
        }

        btnClear.setOnClickListener {
            clearResult()
        }

        checkPermissions()

        // 启动时异步刷新远程授权（仓库根目录 auth_config.json，原生层签名校验后应用）
        lifecycleScope.launch {
            val status = RemoteAuth.refresh()
            if (status == 2) {
                // 已被作者远程吊销：提示并停用分析能力
                UiUtils.error(this@AdAnalyzerActivity, getString(R.string.toast_auth_revoked))
                btnSelectApk.isEnabled = false
                btnCopyConfig.isEnabled = false
                btnSaveConfig.isEnabled = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            R.id.action_language -> {
                showLangDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 语言选择对话框：跟随系统 + 全部支持语言（原生显示名）。
     * 选中后持久化，并重启 Activity 重建资源以应用新语言。
     */
    private fun showLangDialog() {
        val current = LocaleManager.getLangId(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_language_choice, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.langListContainer)

        // 顺序：跟随系统 + SUPPORTED 全部语言
        val order = ArrayList<String>()
        order.add(LocaleManager.FOLLOW_SYSTEM)
        order.addAll(LocaleManager.SUPPORTED.keys)

        val buttons = ArrayList<RadioButton>()
        order.forEach { langId ->
            val label = when (langId) {
                LocaleManager.FOLLOW_SYSTEM -> getString(R.string.locale_system_default)
                else -> LocaleManager.DISPLAY_NAMES[langId] ?: langId
            }
            val rb = RadioButton(this)
            rb.text = label
            rb.id = View.generateViewId()
            rb.isChecked = langId == current
            rb.buttonTintList = ContextCompat.getColorStateList(this, R.color.accent)
            rb.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            rb.textSize = 15f
            rb.setPaddingRelative(
                4,
                (resources.displayMetrics.density * 12).toInt(),
                4,
                (resources.displayMetrics.density * 12).toInt()
            )
            rb.tag = langId
            rb.setOnClickListener { chooseLang(langId, buttons) }
            buttons.add(rb)
            container.addView(rb)
        }

        val langDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dlg_choose_language)
            .setView(dialogView)
            .setPositiveButton(R.string.dlg_cancel, null)
            .create()
        langDialog.show()
        UiUtils.fitDialogToScreen(langDialog)
    }

    private fun chooseLang(langId: String, buttons: List<RadioButton>) {
        buttons.forEach { it.isChecked = it.tag == langId }
        if (LocaleManager.getLangId(this) != langId) {
            LocaleManager.setLangId(this, langId)
            recreate()
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
            }
        }
    }

    private fun checkPermissionsAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            UiUtils.warning(this, getString(R.string.toast_grant_all_files))
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
            return
        }
        pickApkFile()
    }

    private fun pickApkFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/vnd.android.package-archive"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.chooser_pick_apk)), REQUEST_CODE_PICK_APK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data?.data != null && requestCode == REQUEST_CODE_PICK_APK) {
            currentApkUri = data.data
            analyzeApk(data.data!!)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            pickApkFile()
        }
    }

    private fun analyzeApk(uri: Uri) {
        isAnalyzing = true
        logBuffer.setLength(0)
        clearResult()
        showProgress(true)
        tvApkName.text = queryDisplayName(uri) ?: uri.toString()

        val displayName = queryDisplayName(uri) ?: "APK"
        appendLog(getString(R.string.log_start_analyze, displayName))

        lifecycleScope.launch(Dispatchers.IO) {
            var apkFile: File? = null
            try {
                // 0. 清理缓存目录中历史分析残留（analysis_*.apk 及临时解压目录），
                //    避免多次选择 APK 分析时因残留缓存冲突导致本次处理失败
                clearAnalysisCache()

                // 1. 拷贝 APK 到缓存
                appendLog(getString(R.string.log_read_apk))
                apkFile = File(cacheDir, "analysis_${System.currentTimeMillis()}.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    apkFile!!.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException(getString(R.string.err_cannot_read_file))

                // 2. 加载广告特征配置
                var config = AdPatternConfig.loadConfig(this@AdAnalyzerActivity)
                appendLog(getString(R.string.log_load_config, config.totalCount(), config.flutterPatterns.size + 1))

                // 2.1 加载内置（assets）广告厂商特征库并合并到基础配置（全程离线）
                val embeddedMerged = loadEmbeddedFeatures(config)
                if (embeddedMerged != null) {
                    config = embeddedMerged
                    appendLog(getString(R.string.log_merged_online, config.totalCount()))
                } else {
                    appendLog(getString(R.string.log_no_online))
                }

                // 3. 执行分析
                val result = AdFeatureAnalyzer.analyze(
                    apkFile!!,
                    config,
                    context = this@AdAnalyzerActivity,
                    logger = { msg -> appendLog(msg) },
                    progress = { done, total, name -> updateProgress(done, total, name) }
                )
                currentResult = result

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    renderResult(result)
                    isAnalyzing = false
                }
            } catch (e: Exception) {
                appendLog(getString(R.string.log_analyze_failed, e.message))
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    isAnalyzing = false
                    renderLogOnly()
                    UiUtils.error(this@AdAnalyzerActivity, getString(R.string.toast_analyze_failed, e.message))
                }
            } finally {
                apkFile?.let {
                    try { it.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * 清理分析缓存：删除本应用缓存目录中所有历史分析产生的临时文件与目录，
     * 保证每次选择 APK 后都能从干净状态重新开始分析，避免缓存冲突导致二次失败。
     */
    private fun clearAnalysisCache() {
        try {
            val root = cacheDir
            if (!root.exists() || !root.isDirectory) return
            val kids = root.listFiles() ?: return
            for (f in kids) {
                try {
                    if (f.name.startsWith("analysis_")) {
                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 合并内置广告厂商特征库（assets 中的本地厂商库）到基础配置中。
     *
     * 直接读取打包在 APK assets 的本地厂商特征库（online_ad_vendors_default.enc），
     * 全程离线，不发起任何网络请求：
     * - 读取内置厂商库并合并进基础配置（并集去重）；
     * - 内置库缺失或解包失败时返回 null，仅使用内置特征。
     *
     * @return 合并后的新配置；内置厂商库不可用时返回 null。
     */
    private fun loadEmbeddedFeatures(
        base: AdPatternConfig.AdPatterns
    ): AdPatternConfig.AdPatterns? {
        return try {
            val lib = AdVendorLibrary.readEmbedded(this)
            if (lib == null || lib.vendors.isEmpty()) return null
            val onlineFeatures = AdVendorLibrary.toMergedFeatures(lib)
            if (onlineFeatures.totalCount() == 0) return null
            // 合并基础配置与内置厂商特征（并集去重）
            AdPatternConfig.merge(listOf(base, onlineFeatures))
        } catch (_: Exception) {
            null
        }
    }

    private fun renderResult(result: AdAnalysisResult) {
        val sb = StringBuilder()
        sb.append(getString(R.string.report_header)).append('\n')
        sb.append(getString(R.string.report_apk, result.apkName)).append('\n')
        if (result.packageName.isNotBlank()) sb.append(getString(R.string.report_pkg, result.packageName)).append('\n')
        sb.append(getString(R.string.report_dex, result.dexCount)).append('\n')
        sb.append(getString(R.string.report_files, result.fileCount)).append('\n')
        if (result.isFlutter) {
            sb.append(getString(R.string.report_flutter, result.flutterLibappCount)).append('\n')
        }
        sb.append(getString(R.string.report_hits, result.totalHitCount)).append("\n\n")

        val hits = result.matches
        if (hits.isEmpty()) {
            sb.append(getString(R.string.report_no_hit)).append('\n')
        } else {
            for (category in AdPatternConfig.Category.values()) {
                val values = hits[category].orEmpty()
                if (values.isEmpty()) continue
                val display = category.displayName(this)
                sb.append(getString(R.string.report_cat_line, display, values.size)).append('\n')
                values.take(10).forEach { sb.append("   · $it\n") }
                if (values.size > 10) sb.append(getString(R.string.report_more, values.size - 10)).append('\n')
                sb.append('\n')
            }
        }

        // 完整配置不再平铺进日志区，交由下方"复制配置 / 保存配置"按钮导出
        sb.append(getString(R.string.report_tip)).append('\n')
        sb.append(getString(R.string.report_export_hint)).append('\n')

        setResultText(sb.toString())

        // 分析完成：解锁复制/保存按钮，允许导出配置文件
        btnCopyConfig.isEnabled = true
        btnSaveConfig.isEnabled = true
    }

    private fun renderLogOnly() {
        setResultText(logBuffer.toString())
    }

    private fun clearResult() {
        currentResult = null
        setResultText(getString(R.string.hint_initial))
        btnCopyConfig.isEnabled = false
        btnSaveConfig.isEnabled = false
    }

    private fun setResultText(text: String) {
        tvResult.text = colorizeAndHighlightResult(text)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    /**
     * 简单着色：步骤标题/分隔线用品牌色，成功绿色，失败红色，提示紫色。
     */
    private fun colorizeAndHighlightResult(text: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val colorError = ContextCompat.getColor(this, R.color.log_error)
        val colorWarning = ContextCompat.getColor(this, R.color.log_warning)
        val colorSuccess = ContextCompat.getColor(this, R.color.log_success)
        val colorInfo = ContextCompat.getColor(this, R.color.log_info)
        val colorStep = ContextCompat.getColor(this, R.color.log_step)
        for (line in text.split('\n')) {
            val start = sb.length
            sb.append(line).append('\n')
            val len = sb.length - start
            val trimmed = line.trimStart()
            when {
                trimmed.contains("✗") || trimmed.contains("失败") -> sb.setSpan(
                    ForegroundColorSpan(colorError), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                trimmed.startsWith("⚠") -> sb.setSpan(
                    ForegroundColorSpan(colorWarning), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                trimmed.startsWith("✓") -> sb.setSpan(
                    ForegroundColorSpan(colorSuccess), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                trimmed.startsWith("▶") || line.startsWith("════") -> sb.setSpan(
                    ForegroundColorSpan(colorStep), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                trimmed.startsWith("·") -> sb.setSpan(
                    ForegroundColorSpan(colorInfo), start, start + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private fun appendLog(message: String) {
        logBuffer.append(message).append('\n')
        // 仅在 UI 已就绪时即时刷新（分析过程中的简单文本）
        runOnUiThread {
            if (logBuffer.length < 200_000) {
                tvResult.text = colorizeAndHighlightResult(logBuffer.toString())
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            progressBar.isIndeterminate = true
            tvProgress.visibility = View.GONE
        }
        lastProgressPct = -1
        btnSelectApk.isEnabled = !show
    }

    /**
     * 分析进度回调：更新顶部进度条（百分比）与当前正在分析的文件/条目数。
     * 仅在百分比变化时刷新 UI，避免高频回调拖慢分析。
     */
    private fun updateProgress(done: Int, total: Int, fileName: String) {
        val pct = if (total <= 0) 100 else (done * 100 / total).coerceIn(0, 100)
        if (pct == lastProgressPct && done < total) return
        lastProgressPct = pct
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            if (progressBar.isIndeterminate) progressBar.isIndeterminate = false
            progressBar.max = 100
            progressBar.progress = pct
            tvProgress.text = getString(R.string.progress_analyzing, shortName(fileName), done, total, pct)
            tvProgress.visibility = View.VISIBLE
        }
    }

    /** 简化条目名为末尾文件名，过长时截断，便于进度展示。 */
    private fun shortName(entryName: String): String {
        val s = entryName.substringAfterLast('/')
        return if (s.length > 40) s.take(18) + "…" + s.takeLast(20) else s
    }

    /**
     * 保存生成的配置到本地（默认导出目录），文件名：apk名_广告特征_yyyyMMdd_HHmmss.json。
     */
    private fun saveConfigLocally(result: AdAnalysisResult) {
        val fileName = if (result.apkName.isNotBlank()) {
            result.apkName.substringBeforeLast('.').ifBlank { getString(R.string.default_feature_name) }
        } else {
            getString(R.string.default_feature_name)
        }
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val configDir = File(PathPreferences.getOutputDir(this))
        if (!configDir.exists()) configDir.mkdirs()
        val outFile = File(configDir, getString(R.string.save_file_pattern, fileName, time))

        try {
            outFile.writeText(result.toConfigJson(), Charsets.UTF_8)
            UiUtils.success(this, getString(R.string.toast_saved, outFile.absolutePath))
            appendLog(getString(R.string.log_saved, outFile.absolutePath))
        } catch (e: Exception) {
            UiUtils.error(this, getString(R.string.toast_save_failed, e.message))
            appendLog(getString(R.string.log_save_failed, e.message))
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}