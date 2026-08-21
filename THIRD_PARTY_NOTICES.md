# 第三方许可声明 (Third-Party Notices)

本文件列出了 **APK广告特征分析工具（ApkAdAnalyzer）** 所使用、调用或参考的第三方开源项目及其许可信息。

本项目遵循 MIT License 开源，但**不改变**以下第三方项目的原始许可条款。使用本项目时，请同时遵守本项目及其依赖项目的许可要求。

---

## 一、直接依赖（编译期引入）

### 1. AndroidX

| 项目 | 说明 |
|------|------|
| 主页 | https://developer.android.com/jetpack |
| 作者 | AOSP (Android Open Source Project) |
| 用途 | core-ktx / appcompat / constraintlayout / recyclerview / lifecycle / coordinatorlayout |
| 协议 | Apache License 2.0 |

### 2. Material Components for Android

| 项目 | 说明 |
|------|------|
| 主页 | https://github.com/material-components/material-components-android |
| 作者 | Google |
| 用途 | Material Design 组件库，提供卡片、按钮、对话框、进度条等 UI 组件 |
| 协议 | Apache License 2.0 |

### 3. Kotlin 标准库

| 项目 | 说明 |
|------|------|
| 主页 | https://kotlinlang.org/ |
| 作者 | JetBrains |
| 用途 | Kotlin 编程语言与标准库，自研 Aho-Corasick 匹配引擎基于它实现 |
| 协议 | Apache License 2.0 |

### 4. JUnit

| 项目 | 说明 |
|------|------|
| 主页 | https://junit.org/ |
| 作者 | JUnit Team |
| 用途 | 单元测试框架（testImplementation） |
| 协议 | Eclipse Public License 2.0 |

---

## 二、构建工具链（编译期使用，不随 APK 分发）

### 5. Android Gradle Plugin (AGP)

| 项目 | 说明 |
|------|------|
| 主页 | https://developer.android.com/build |
| 作者 | Google / AOSP |
| 用途 | Android 应用构建插件，管理依赖、资源与打包 |
| 协议 | Apache License 2.0 |

### 6. Gradle

| 项目 | 说明 |
|------|------|
| 主页 | https://gradle.org/ |
| 作者 | Gradle Inc. |
| 用途 | 通用构建系统，由 gradle wrapper 自动管理 |
| 协议 | Apache License 2.0 |

### 7. AAPT2 / zipalign

| 项目 | 说明 |
|------|------|
| 主页 | https://developer.android.com/tools/aapt2 |
| 作者 | AOSP |
| 用途 | Android 资源编译打包（AAPT2）与 APK 对齐（zipalign） |
| 协议 | Apache License 2.0 |

---

## 三、参考来源（仅作特征与思路参考）

### 8. DTL-X

| 项目 | 说明 |
|------|------|
| 主页 | https://github.com/Gameye98/DTL-X |
| 作者 | Gameye98 |
| 用途 | 广告 SDK 包名 / 类名 / 方法名 / URL 特征规则参考来源，仅供特征整理参考与学习一致 |
| 协议 | 原项目未标注许可证，仅供学习参考 |

> 说明：本项目未修改或发布其二进制，仅参考其特征整理思路，结合主流广告 SDK 自行整理规则。如原作者认为存在侵权，请联系我们处理。

### 9. AOSP (Android Asset Packaging / AXML)

| 项目 | 说明 |
|------|------|
| 主页 | https://android.googlesource.com/platform/frameworks/base/ |
| 作者 | AOSP |
| 用途 | 参考 AXML 二进制格式与 StringPool/ResourceMap 结构，自研解析器抽取组件类名、权限名 |
| 协议 | Apache License 2.0 |

### 10. Dart SDK（Flutter 快照分析参考）

| 项目 | 说明 |
|------|------|
| 主页 | https://github.com/dart-lang/sdk |
| 作者 | Google / Dart 团队 |
| 用途 | 参考 Dart VM 快照格式（snapshot.h）理解 libapp.so 结构，用于 Flutter 广告字符串特征扫描 |
| 协议 | BSD 3-Clause |

---

## 四、许可证全文

### Apache License 2.0

> 允许自由使用、修改、分发（含商业用途），需保留版权声明与许可文本；对修改后的文件需显著标注变更；如涉及专利声明需在 NOTICE 中说明。
>
> 完整文本：https://www.apache.org/licenses/LICENSE-2.0

### BSD 3-Clause License

> 允许自由使用、修改、分发（含商业用途），需保留版权声明、条件列表与免责声明；禁止使用作者名义进行推广。
>
> 完整文本：https://opensource.org/licenses/BSD-3-Clause

### MIT License

> 允许自由使用、修改、分发（含商业用途），需保留版权声明与许可文本；按"现状"提供，不附带任何担保。
>
> 完整文本：https://opensource.org/licenses/MIT

### Eclipse Public License 2.0

> 允许自由使用、修改、分发，修改后的代码需在相同协议下开源；提供专利授权。
>
> 完整文本：https://www.eclipse.org/legal/epl-2.0/

---

## 五、致谢

衷心感谢以上所有开源项目及其作者，正是他们的卓越工作让本项目成为可能。本项目对上述项目的使用均遵循其原始许可条款，如对使用方式有任何疑问，欢迎通过 Issue 与我们联系。

---

© 2026 sillycats · 本文件随项目以 MIT License 分发