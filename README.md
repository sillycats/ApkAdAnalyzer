<div align="center">

# APK广告特征分析工具（ApkAdAnalyzer）

**一款面向逆向爱好者与开发者的专业级 APK 广告特征分析工具**

基于 Aho-Corasick 多模式匹配 · 单遍高速扫描 · 一键导出特征配置

<br>

**Android 7.0+ · Kotlin · v1.0 · MIT License**

</div>

---

## 📥 下载安装

前往 [Releases](https://github.com/sillycats/ApkAdAnalyzer/releases) 页面下载最新 APK：

- **GitHub Releases**：https://github.com/sillycats/ApkAdAnalyzer/releases
- **GitHub 仓库**：https://github.com/sillycats/ApkAdAnalyzer

> 安装时如提示"未知来源"，请在系统设置中允许安装来自此来源的应用。

---

## 📖 项目简介

一键分析任意 APK 中集成的广告特征：解析 **DEX 字节码、AndroidManifest.xml、res/layout 布局、assets 资源、原生库 (.so)** 与 **Flutter (libapp.so)**，自动识别命中的广告 SDK、类名、方法、权限、URL 等特征，并按分类聚合展示。

全程本地离线处理，无需网络，分析结果可直接导出为与配置文件格式完全一致的 JSON。

## ✨ 功能特性

- **高速多模式匹配**：基于 Aho-Corasick 自动机，对所有特征单遍扫描字节流即可同时命中全部特征，数百上千条特征下性能依旧出色
- **18 类广告特征全覆盖**：SDK 包名 / 类名关键词 / 方法名 / 权限 / URL / View / Activity / Service / Receiver / 资源 / 布局 / assets / 根目录文件 / 原生库 / Flutter 字符串等
- **Flutter 应用适配**：解析 `libapp.so` 字节，识别 Flutter 应用内嵌的广告字符串特征
- **AXML 深度解析**：直接解析二进制 AndroidManifest.xml 与布局文件字符串池，准确提取组件类名与权限名
- **一键导出配置**：命中结果聚合为可直接使用的广告特征配置 JSON，支持复制或保存到本地
- **实时进度反馈**：分析进度条 + 当前文件提示，大 APK 分析过程清晰可见
- **精简日志展示**：分类汇总 + 每条特征精简列表，避免海量结果刷屏
- **明暗双主题**：跟随系统 / 白天 / 夜间三种模式

## 🚀 快速上手

### 编译

```bash
# 环境要求：Android Studio / JDK 17+ / SDK 34
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`，可直接安装使用。

### 使用

1. 选择需要分析的 APK 文件
2. 点击"开始分析"，自动完成特征扫描
3. 查看按分类聚合的命中结果，可"复制配置"或"保存配置"导出

## 🏗 技术架构

| 模块 | 技术方案 | 核心说明 |
|------|----------|----------|
| 特征匹配引擎 | Aho-Corasick | 所有特征编译为自动机，单遍扫描字节流同时命中 |
| AXML 解析 | 自研解析器 | 解析 AXML 字符串池，提取类名 / 权限名 / 布局元素 |
| Flutter 检测 | 字节扫描 | 解析 libapp.so 广告字符串特征 |
| 代码 | Kotlin | AppCompat + Material Components |

## 🙏 开源项目致谢

本项目在开发、构建与运行中使用了以下开源项目，向各位作者表示诚挚感谢：

| 分类 | 开源项目 | 协议 |
|------|----------|------|
| 直接依赖 | [AndroidX](https://developer.android.com/jetpack) · [Material Components](https://github.com/material-components/material-components-android) · [Kotlin 标准库](https://kotlinlang.org/) · [JUnit](https://junit.org/) | Apache 2.0 / EPL 2.0 |
| 构建工具链 | [Android Gradle Plugin](https://developer.android.com/build) · [Gradle](https://gradle.org/) · [AAPT2 / zipalign](https://developer.android.com/tools/aapt2) | Apache 2.0 |
| 参考来源 | [DTL-X](https://github.com/Gameye98/DTL-X)（广告特征规则）· [AOSP](https://android.googlesource.com/)（AXML）· [Dart SDK](https://github.com/dart-lang/sdk)（Flutter 分析） | 见各项目主页 |

完整清单与许可全文见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 📚 参考代码与内容出处

本应用的实现参考了以下公开资料（出处与思路详见 [开源声明.md](开源声明.md)）：

- **Aho-Corasick 多模式匹配算法**：Aho & Corasick（1975），[算法说明](https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm)
- **AXML 二进制格式解析**：AOSP Asset Packaging，[frameworks/base](https://android.googlesource.com/platform/frameworks/base/+/core/res/)
- **广告特征规则整理**：参考 [DTL-X](https://github.com/Gameye98/DTL-X) 思路结合主流广告 SDK 自研整理
- **Flutter 应用分析**：Dart VM 快照格式，[snapshot.h](https://github.com/dart-lang/sdk/blob/main/runtime/vm/snapshot.h)
- **Android DEX 结构**：[DEX 规范](https://source.android.com/docs/core/runtime/dex-format)
- **界面排版**：[Material Design 3](https://m3.material.io/)

## 🤝 参与贡献

欢迎参与本项目开发，一起完善更多广告特征：

- **报告问题 / 功能建议**：请使用 [Issue](https://github.com/sillycats/ApkAdAnalyzer/issues)（参考 [Bug 模板](.github/ISSUE_TEMPLATE/bug_report.md) 与 [功能模板](.github/ISSUE_TEMPLATE/feature_request.md)）
- **提交代码**：Fork 后提交 [Pull Request](https://github.com/sillycats/ApkAdAnalyzer/pulls)（参考 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)）
- **开发流程**：请遵循 [CONTRIBUTING.md](CONTRIBUTING.md) 中的开发环境、代码规范与提交规范
- **行为准则**：请遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **社区交流**：前往 [Discussions](https://github.com/sillycats/ApkAdAnalyzer/discussions) 讨论
- **安全漏洞**：请通过 [SECURITY.md](SECURITY.md) 描述的渠道报告，勿在公开 Issue 中披露

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源，版权归 **© 2026 sillycat** 所有。所使用第三方库的许可信息详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，参考代码出处与更多开源说明详见 [开源声明.md](开源声明.md)。

> ⚠️ 本工具仅供学习、研究与个人合法用途使用。分析结果仅供参考，请结合 APK 实际功能与人工复核后使用。