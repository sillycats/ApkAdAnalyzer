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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
class AdAnalyzerActivity : AppCompatActivity() {

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
                UiUtils.warning(this, "请先选择 APK 并完成分析")
                return@setOnClickListener
            }
            val json = result.toConfigJson()
            val clip = ClipData.newPlainText("广告特征配置", json)
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            UiUtils.success(this, "配置已复制到剪贴板")
        }

        btnSaveConfig.setOnClickListener {
            val result = currentResult
            if (result == null) {
                UiUtils.warning(this, "请先选择 APK 并完成分析")
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
                UiUtils.error(this@AdAnalyzerActivity, "应用授权已被远程吊销，功能已停用")
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
            R.id.action_sync_vendors -> {
                syncOnlineVendors()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
            UiUtils.warning(this, "请先授予\"所有文件访问\"权限")
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
        startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQUEST_CODE_PICK_APK)
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
        appendLog("▶ 开始分析: $displayName")

        lifecycleScope.launch(Dispatchers.IO) {
            var apkFile: File? = null
            try {
                // 1. 拷贝 APK 到缓存
                appendLog("  · 读取 APK 文件...")
                apkFile = File(cacheDir, "analysis_${System.currentTimeMillis()}.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    apkFile!!.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取所选文件")

                // 2. 加载广告特征配置
                var config = AdPatternConfig.loadConfig(this@AdAnalyzerActivity)
                appendLog("  · 加载特征配置: 共 ${config.totalCount()} 条特征，分类 ${config.flutterPatterns.size + 1}（含 Flutter）")

                // 2.1 合并在线广告厂商特征库后再分析（在线 + 内置联合分析）
                //     优先使用本地缓存；无缓存时自动联网获取一次，保证联网/离线用户都能同时调用在线特征
                val onlineMerged = loadAndMergeOnlineFeatures(config)
                if (onlineMerged != null) {
                    config = onlineMerged
                    appendLog("  · 已合并在线广告厂商特征库，当前特征: 共 ${config.totalCount()} 条")
                } else {
                    appendLog("  · 未获取到在线广告厂商特征库，仅使用内置特征（可在菜单中手动同步）")
                }

                // 3. 执行分析
                val result = AdFeatureAnalyzer.analyze(
                    apkFile!!,
                    config,
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
                appendLog("  ✗ 分析失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    isAnalyzing = false
                    renderLogOnly()
                    UiUtils.error(this@AdAnalyzerActivity, "分析失败: ${e.message}")
                }
            } finally {
                apkFile?.let {
                    try { it.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * 合并在线广告厂商特征库到基础配置中（在线 + 内置联合分析）。
     *
     * 获取在线特征依赖顺序：
     * 1. 优先读取本地缓存（离线可用，避免每次分析都联网拉取）；
     * 2. 无有效缓存时，自动联网拉取一次在线厂商库并写入缓存；
     * 3. 均失败（离线且无缓存）时返回 null，仅使用内置特征。
     *
     * @return 合并后的新配置；无在线特征可用时返回 null。
     */
    private fun loadAndMergeOnlineFeatures(
        base: AdPatternConfig.AdPatterns
    ): AdPatternConfig.AdPatterns? {
        return try {
            // 1) 缓存优先
            var cacheText: String? = AdVendorLibrary.readCached(this)
            var lib: AdVendorLibrary.VendorLibrary? = cacheText?.let { AdVendorLibrary.parseCached(it) }
            if (lib == null || lib.vendors.isEmpty()) {
                // 2) 无有效缓存 -> 自动联网获取一次
                lib = AdVendorLibrary.fetchVendorLibrary(AdVendorLibrary.getVendorUrls())
                if (lib != null) {
                    // 写缓存（离线兜底：下次分析直接读缓存，不联网）
                    try {
                        AdVendorLibrary.writeCache(this@AdAnalyzerActivity, libraryJsonOf(lib))
                    } catch (_: Exception) {
                    }
                    appendLog("  · 已自动联网获取在线广告厂商特征库")
                }
            }
            if (lib == null || lib.vendors.isEmpty()) return null
            val onlineFeatures = AdVendorLibrary.toMergedFeatures(lib)
            if (onlineFeatures.totalCount() == 0) return null
            // 合并基础配置与在线厂商特征（并集去重）
            AdPatternConfig.merge(listOf(base, onlineFeatures))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 同步在线广告厂商特征库（菜单入口）。
     *
     * 后台拉取在线厂商库 -> 写入本地缓存 -> 展示同步结果（厂商数、国内外分布、特征总数）。
     * 同步成功后，下次分析 APK 时会自动合并在线特征进行联合分析。
     */
    private fun syncOnlineVendors() {
        if (isAnalyzing) {
            UiUtils.warning(this, "正在分析中，请稍后再同步")
            return
        }
        UiUtils.info(this, "正在同步在线广告厂商特征库…")
        lifecycleScope.launch(Dispatchers.IO) {
            val library = AdVendorLibrary.fetchVendorLibrary(AdVendorLibrary.getVendorUrls())
            withContext(Dispatchers.Main) {
                if (library == null) {
                    // 网络失败时提示使用本地缓存
                    val hasCache = AdVendorLibrary.readCached(this@AdAnalyzerActivity) != null
                    if (hasCache) {
                        UiUtils.warning(
                            this@AdAnalyzerActivity,
                            "网络获取失败，将使用本地缓存（${libraryInfoFromCache() ?: "未知"}）"
                        )
                        appendLog("  ⚠ 在线厂商库网络获取失败，将使用本地缓存继续分析")
                    } else {
                        UiUtils.error(this@AdAnalyzerActivity, "获取在线广告厂商特征库失败，请检查网络")
                        appendLog("  ✗ 获取在线广告厂商特征库失败")
                    }
                    return@withContext
                }
                // 写入缓存以便后续离线合并
                try {
                    AdVendorLibrary.writeCache(this@AdAnalyzerActivity, libraryJsonOf(library))
                } catch (_: Exception) {
                }
                val summary = AdVendorLibrary.summarize(library)
                appendLog(
                    "  ✓ 已同步在线广告厂商特征库: 厂商 ${summary.vendorCount} 家 " +
                        "(国内 ${summary.domesticCount} / 国际 ${summary.foreignCount}), " +
                        "合并特征 ${summary.featureCount} 条"
                )
                UiUtils.success(
                    this@AdAnalyzerActivity,
                    "同步成功: ${summary.vendorCount} 家厂商, ${summary.featureCount} 条特征"
                )
            }
        }
    }

    /** 从缓存读取厂商库概况文本，便于网络失败时的提示。 */
    private fun libraryInfoFromCache(): String? {
        return try {
            val cached = AdVendorLibrary.readCached(this) ?: return null
            val lib = AdVendorLibrary.parseCached(cached) ?: return null
            val s = AdVendorLibrary.summarize(lib)
            "${s.vendorCount} 家厂商, ${s.featureCount} 条特征"
        } catch (_: Exception) {
            null
        }
    }

    /** 将 [AdVendorLibrary.VendorLibrary] 序列化为 JSON 文本写入缓存。 */
    private fun libraryJsonOf(library: AdVendorLibrary.VendorLibrary): String {
        return try {
            org.json.JSONObject().apply {
                put("version", library.version)
                put("updated", library.updated)
                put(
                    "vendors",
                    org.json.JSONArray().apply {
                        library.vendors.forEach { v ->
                            put(
                                org.json.JSONObject().apply {
                                    put("id", v.id)
                                    put("name", v.name)
                                    put("region", v.region)
                                    put("homepage", v.homepage)
                                    put("features", AdPatternConfig.toJson(v.features))
                                }
                            )
                        }
                    }
                )
            }.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    private fun renderResult(result: AdAnalysisResult) {
        val sb = StringBuilder()
        sb.append("════════ 分析汇总 ════════\n")
        sb.append("APK   : ${result.apkName}\n")
        if (result.packageName.isNotBlank()) sb.append("包名  : ${result.packageName}\n")
        sb.append("DEX   : ${result.dexCount} 个\n")
        sb.append("文件  : ${result.fileCount} 个\n")
        if (result.isFlutter) {
            sb.append("Flutter: ${result.flutterLibappCount} 个 libapp.so\n")
        }
        sb.append("命中  : ${result.totalHitCount} 条特征\n\n")

        val hits = result.matches
        if (hits.isEmpty()) {
            sb.append("未命中任何已配置的广告特征。\n")
        } else {
            for (category in AdPatternConfig.Category.values()) {
                val values = hits[category].orEmpty()
                if (values.isEmpty()) continue
                val display = if (category == AdPatternConfig.Category.FLUTTER_PATTERNS) {
                    "Flutter 字符串特征"
                } else {
                    category.displayName
                }
                sb.append("▶ ${display} (${values.size})\n")
                values.take(10).forEach { sb.append("   · $it\n") }
                if (values.size > 10) sb.append("   · 其余 ${values.size - 10} 条省略\n")
                sb.append('\n')
            }
        }

        // 完整配置不再平铺进日志区，交由下方"复制配置 / 保存配置"按钮导出
        sb.append("⚠ 提示: 分析结果仅供参考，导入前请人工复核。\n")
        sb.append("完整配置请使用下方\"复制配置\"或\"保存配置\"导出。\n")

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
        setResultText("选择一个有广告的 APK 文件开始分析。\n分析完成后可复制或保存广告特征配置文件。")
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
            tvProgress.text = "正在分析 ${shortName(fileName)}   已处理 $done/$total ($pct%)"
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
            result.apkName.substringBeforeLast('.').ifBlank { "广告特征" }
        } else {
            "广告特征"
        }
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val configDir = File(PathPreferences.getOutputDir(this))
        if (!configDir.exists()) configDir.mkdirs()
        val outFile = File(configDir, "${fileName}_广告特征_$time.json")

        try {
            outFile.writeText(result.toConfigJson(), Charsets.UTF_8)
            UiUtils.success(this, "配置已保存: ${outFile.absolutePath}")
            appendLog("  ✓ 配置已保存: ${outFile.absolutePath}")
        } catch (e: Exception) {
            UiUtils.error(this, "保存失败: ${e.message}")
            appendLog("  ✗ 保存失败: ${e.message}")
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