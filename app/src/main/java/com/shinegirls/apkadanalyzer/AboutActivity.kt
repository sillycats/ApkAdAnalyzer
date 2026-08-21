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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.shinegirls.apkadanalyzer.core.UpdateChecker
import com.shinegirls.apkadanalyzer.utils.UiUtils

/**
 * 关于页面。
 *
 * 展示应用信息、作者信息、开源项目、隐私声明与免责声明。
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // 版本号
        findViewById<TextView>(R.id.tvVersion).text = "版本 ${getVersionName()}"

        // 检查更新
        findViewById<MaterialButton>(R.id.btnCheckUpdate)
            .setOnClickListener { checkForUpdate() }

        // 立即下载：无需检测更新，直接用手机浏览器访问蓝奏云下载最新版
        findViewById<MaterialButton>(R.id.btnDirectDownload)
            .setOnClickListener { openLanZouDownload() }

        // 开源项目信息
        findViewById<TextView>(R.id.tvOpenSource).text = OPEN_SOURCE_TEXT
        findViewById<TextView>(R.id.tvOpenSource).movementMethod = LinkMovementMethod.getInstance()

        // 参考内容与代码出处
        findViewById<TextView>(R.id.tvReference).text = REFERENCE_TEXT

        // 隐私声明
        findViewById<TextView>(R.id.tvPrivacy).text = PRIVACY_TEXT

        // 免责声明
        findViewById<TextView>(R.id.tvDisclaimer).text = DISCLAIMER_TEXT

        // 版权信息
        findViewById<TextView>(R.id.tvCopyright).text = COPYRIGHT_TEXT

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
            row.findViewById<TextView>(R.id.tvFeatureText).text = feature.second
            container.addView(row)
        }
    }

    private fun bindAuthorClick() {
        val tvQq = findViewById<TextView>(R.id.tvAuthorQq)
        val tvEmail = findViewById<TextView>(R.id.tvAuthorEmail)

        // 点击 QQ 复制
        tvQq.setOnClickListener {
            val qq = getString(R.string.author_qq_note)
            val clip = ClipData.newPlainText("作者QQ", qq)
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            UiUtils.success(this, "QQ已复制到剪贴板")
        }

        // 点击邮箱发邮件
        tvEmail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${getString(R.string.author_email)}")
                    putExtra(Intent.EXTRA_SUBJECT, "APK广告特征分析工具反馈")
                }
                startActivity(Intent.createChooser(intent, "发送邮件"))
            } catch (_: Exception) {
                UiUtils.warning(this, "未找到邮件客户端")
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
         * 功能特性列表。Pair.first 用于选择图标（true=check，false=info）。
         */
        private val FEATURES = listOf(
            true to "一键分析 APK 广告特征，全程本地离线处理，无需上传任何文件",
            true to "选择带广告的 APK 即可快速识别其中的广告 SDK 与特征",
            true to "覆盖 DEX 字节码、AndroidManifest、res/layout、assets、根目录文件等",
            true to "自动检测 Flutter 应用并扫描 libapp.so 中的广告字符串特征",
            true to "19 类广告特征分类，涵盖 SDK 包名 / 权限 / 类 / 方法 / 资源 / URL 等",
            true to "实时彩色分析日志，支持进度条与当前分析文件展示",
            true to "分析结果自动聚合，命中的广告特征按分类清晰呈现",
            true to "支持一键生成与导出 ad_patterns.json 广告特征配置文件",
            true to "生成的配置格式与应用内置配置完全一致，可直接复制或保存到本地",
            true to "明暗双主题，支持跟随系统、白天、夜间三种模式自由切换"
        )

        private const val OPEN_SOURCE_TEXT = "本应用基于以下开源项目构建并调用，在此向各位作者表示诚挚感谢与敬意：\n\n" +
            "1. DTL-X (Gameye98)\n" +
            "   广告 SDK 包名 / 类名 / 域名 / 权限等特征规则参考来源\n" +
            "   - 主页: https://github.com/Gameye98/DTL-X\n" +
            "   - 仅供特征参考与学习\n\n" +
            "2. AndroidX (Android Open Source Project)\n" +
            "   core-ktx / appcompat / constraintlayout / lifecycle / coordinatorlayout\n" +
            "   Android 官方 Jetpack 支持库\n" +
            "   - 主页: https://developer.android.com/jetpack\n" +
            "   - 协议: Apache License 2.0\n\n" +
            "3. Material Components (Google)\n" +
            "   Material Design 组件库，提供卡片、按钮、对话框等 UI 组件\n" +
            "   - 主页: https://github.com/material-components/material-components-android\n" +
            "   - 协议: Apache License 2.0\n\n" +
            "4. Kotlin 标准库 (JetBrains)\n" +
            "   Kotlin 编程语言与标准库\n" +
            "   - 主页: https://kotlinlang.org/\n" +
            "   - 协议: Apache License 2.0\n\n" +
            "以上项目的完整版权与许可文本，请访问对应主页查看。"

        private const val REFERENCE_TEXT = "本应用的分析实现参考了以下公开的技术文档与社区资料，在此一并致谢，并说明出处：\n\n" +
            "1. Android APK 打包结构 (ZIP / AXML / DEX)\n" +
            "   参考 AOSP 与 Android 官方文档中关于 ZIP 结构、Android Binary XML 与 DEX 字节码的说明\n" +
            "   - 出处: https://developer.android.com/guide/components\n" +
            "   - 用于解包读取 classes*.dex、AndroidManifest、res/layout、assets 等条目内容\n\n" +
            "2. Dart AOT 快照解析 (Flutter libapp.so)\n" +
            "   参考 Dart VM 源码 snapshot.h 与社区 Flutter 逆向资料\n" +
            "   - snapshot.h 出处: https://github.com/dart-lang/sdk/blob/main/runtime/vm/snapshot.h\n" +
            "   - 用于定位 libapp.so 内 Dart 快照并扫描广告字符串特征\n\n" +
            "3. 广告特征规则整理\n" +
            "   广告 SDK 包名 / 域名 / 类名 / 权限关键词参考开源项目 DTL-X 的规则整理扩充\n" +
            "   - 主页: https://github.com/Gameye98/DTL-X\n" +
            "   - 参考其 adloader / 域名黑名单 / 方法关键词思路，结合主流广告 SDK 自行整理\n\n" +
            "4. Aho-Corasick 多模式匹配算法\n" +
            "   用于在文件字节流中一次遍历同时匹配数千条广告特征，显著提升分析速度\n" +
            "   - 出处: https://en.wikipedia.org/wiki/Aho–Corasick_algorithm\n\n" +
            "5. 界面主题与排版\n" +
            "   基于 Material Design 规范与 Material Components 组件库示例编写\n" +
            "   - 规范: https://m3.material.io/\n" +
            "   - 组件: https://github.com/material-components/material-components-android\n\n" +
            "以上内容仅作技术学习参考，最终实现均为本项目自研；涉及版权归原作者与作者所属机构所有。"

        private const val PRIVACY_TEXT = "本应用遵守最小化收集原则，高度重视并保护您的个人隐私：\n\n" +
            "1. 本地离线处理：所有 APK 的广告特征分析均在您的设备本地完成，应用不会上传任何 APK 文件或内部数据到服务器。\n\n" +
            "2. 联网行为透明：本应用仅在您主动点击\"检查更新\"时联网请求版本信息，其余时间不会在后台联网、收集或上传任何个人信息。\n\n" +
            "3. 权限最小化：本应用不读取、不存储、不访问您的通讯录、相册、定位、短信、通话记录等敏感信息。\n\n" +
            "4. 数据本地存储：应用的特征配置与分析结果仅保存在您的设备本地，应用卸载后即被清除，不会留存任何云端记录。\n\n" +
            "5. 第三方链接：关于与更新页面可能包含外部链接，点击后由第三方平台处理您的访问行为，建议您查阅相关第三方的隐私政策。\n\n" +
            "6. 若您在使用过程中有任何隐私疑问、建议或顾虑，欢迎通过作者联系方式与我们沟通，我们将尽力解答。"

        private const val DISCLAIMER_TEXT = "请在使用本应用前仔细阅读以下免责声明：\n\n" +
            "1. 合法用途限制：本应用仅供学习、研究与个人合法用途使用。请仅对您拥有版权、已获授权或有权分析的应用进行分析。\n\n" +
            "2. 结果仅供参考：分析出的广告特征仅供技术研究与参考，不构成任何形式的技术结论或担保。广告 SDK 归属可能随版本变化，请结合 APK 实际功能与人工复核后使用。\n\n" +
            "3. 分析范围限制：本应用仅检测预设特征规则，未命中的广告 SDK 不代表不存在广告，分析结果可能存在遗漏或误报。\n\n" +
            "4. 无担保声明：本应用按其现状提供，不附带任何形式的明示或默示担保。作者不对因使用或无法使用本应用而造成的任何直接或间接损失承担责任。\n\n" +
            "5. 第三方内容：本应用引用的开源项目与广告特征规则均来自公开渠道，仅供技术参考，其版权归原作者所有。\n\n" +
            "6. 条款变更：作者保留随时修改本免责声明的权利，更新后的内容将在新版本中生效。\n\n" +
            "7. 使用本应用即视为您已阅读、理解并同意以上全部条款。若不同意，请停止使用本应用。"

        private const val COPYRIGHT_TEXT = "© 2026 小奶瓶 · 保留所有权利\nPowered by dexlib2 / apksig / AndroidX"
    }
}