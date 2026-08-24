package com.shinegirls.apkadanalyzer

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.shinegirls.apkadanalyzer.core.LocaleManager

/**
 * 全局多语言基类。
 *
 * 所有 Activity 继承本类，在 attachBaseContext 阶段按当前选择语言包裹 baseContext，
 * 使 setContentView 加载的布局与 getString 解析均使用所选语言资源（values-xx）。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}
