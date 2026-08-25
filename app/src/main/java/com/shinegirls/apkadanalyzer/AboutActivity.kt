package com.shinegirls.apkadanalyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.shinegirls.apkadanalyzer.core.UpdateChecker
import com.shinegirls.apkadanalyzer.utils.UiUtils

/**
 * 关于页面。
 *
 * 展示应用信息、作者信息、开源项目、隐私声明与免责声明。
 */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // 标题由布局中的多语言 TextView 展示；隐藏 Toolbar 自带默认应用名，避免语言不跟随/重复显示
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // 版本号
        findViewById<TextView>(R.id.tvVersion).text = getString(R.string.about_version, getVersionName())

        // 检查更新
        findViewById<MaterialButton>(R.id.btnCheckUpdate)
            .setOnClickListener { checkForUpdate() }

        // 立即下载：无需检测更新，直接用手机浏览器访问蓝奏云下载最新版
        findViewById<MaterialButton>(R.id.btnDirectDownload)
            .setOnClickListener { openLanZouDownload() }

        // 开源项目信息
        findViewById<TextView>(R.id.tvOpenSource).text = getString(R.string.about_opensource_text)
        findViewById<TextView>(R.id.tvOpenSource).movementMethod = LinkMovementMethod.getInstance()

        // 参考内容与代码出处
        findViewById<TextView>(R.id.tvReference).text = getString(R.string.about_reference_text)

        // 隐私声明
        findViewById<TextView>(R.id.tvPrivacy).text = getString(R.string.about_privacy_text)

        // 免责声明
        findViewById<TextView>(R.id.tvDisclaimer).text = getString(R.string.about_disclaimer_text)

        // 版权信息
        findViewById<TextView>(R.id.tvCopyright).text = getString(R.string.about_copyright_text)

        // 功能特性
        bindFeatures()

        // 点击作者信息可复制或发送邮件
        bindAuthorClick()
    }

    /**
     * 动态填充"功能特性"列表。
     */
    private fun bindFeatures() {
        val container = findViewById<LinearLayout>(R.id.llFeatures)
        container.removeAllViews()

        for (feature in FEATURES) {
            val row = layoutInflater.inflate(R.layout.item_about_feature, container, false)
            row.findViewById<ImageView>(R.id.ivFeatureIcon).setImageResource(
                if (feature.first) R.drawable.ic_check else R.drawable.ic_info
            )
            row.findViewById<ImageView>(R.id.ivFeatureIcon)
                .setColorFilter(ContextCompat.getColor(this, R.color.accent))
            row.findViewById<TextView>(R.id.tvFeatureText).text = getString(feature.second)
            container.addView(row)
        }
    }

    private fun bindAuthorClick() {
        val tvQq = findViewById<TextView>(R.id.tvAuthorQq)
        val tvEmail = findViewById<TextView>(R.id.tvAuthorEmail)

        // 点击 QQ 复制
        tvQq.setOnClickListener {
            val qq = getString(R.string.author_qq_note)
            val clip = ClipData.newPlainText(getString(R.string.clip_author_qq), qq)
            (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
            UiUtils.success(this, getString(R.string.toast_qq_copied))
        }

        // 点击邮箱发邮件
        tvEmail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${getString(R.string.author_email)}")
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
                }
                startActivity(Intent.createChooser(intent, getString(R.string.chooser_send_email)))
            } catch (_: Exception) {
                UiUtils.warning(this, getString(R.string.toast_no_mail_app))
            }
        }
    }

    private fun getVersionName(): String = UpdateChecker.getCurrentVersionName(this)

    /**
     * 检测更新：后台拉取版本信息，UI 线程展示结果。
     * 若有强制更新，UpdateChecker 会弹出不可取消的对话框。
     */
    private fun checkForUpdate() {
        UpdateChecker.checkForUpdate(this)
    }

    /**
     * 立即下载：无需检测更新，用内置浏览器打开蓝奏云下载最新版。
     * 内置浏览器会拦截 APK 下载地址并自动使用应用内进度下载。
     */
    private fun openLanZouDownload() {
        UpdateChecker.openLanzouInBuiltInBrowser(this)
    }

    companion object {
        /**
         * 功能特性列表。Pair.first 用于选择图标（true=check，false=info），
         * Pair.second 为字符串资源 ID（feat_*）。
         */
        private val FEATURES = listOf(
            true to R.string.feat_01,
            true to R.string.feat_02,
            true to R.string.feat_03,
            true to R.string.feat_04,
            true to R.string.feat_05,
            true to R.string.feat_06,
            true to R.string.feat_07,
            true to R.string.feat_08,
            true to R.string.feat_09,
            true to R.string.feat_10,
            true to R.string.feat_11
        )
    }
}