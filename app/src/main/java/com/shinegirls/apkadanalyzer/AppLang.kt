package com.shinegirls.apkadanalyzer

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.shinegirls.apkadanalyzer.core.LocaleManager

/**
 * 应用全局入口。
 *
 * - 在 attachBaseContext 时按持久化的语言（跟随系统/指定语言）包裹 base 上下文，
 *   使 Application 层面的资源解析同步于所选语言。
 * - 系统语言/配置变化时更新语言。
 */
class AppLang : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
