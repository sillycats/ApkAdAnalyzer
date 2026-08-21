<div align="center">

# APK广告特征分析工具（ApkAdAnalyzer）

**一款面向逆向爱好者与开发者的专业级 APK 广告特征分析工具**

基于 Aho-Corasick 多模式匹配 · 单遍高速扫描 · 一键导出特征配置 · 全程本地离线

<br>

[![Version](https://img.shields.io/badge/version-1.0-blue.svg)](https://github.com/sillycats/ApkAdAnalyzer/releases)
[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen.svg)](https://github.com/sillycats/ApkAdAnalyzer)
[![Language](https://img.shields.io/badge/language-Kotlin-orange.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![OpenAPI](https://img.shields.io/badge/PRs-welcome-blueviolet.svg)](https://github.com/sillycats/ApkAdAnalyzer/pulls)

</div>

---

## 📑 目录

- [📥 下载安装](#-下载安装)
- [📖 项目简介](#-项目简介)
- [✨ 功能特性](#-功能特性)
- [🚀 快速上手](#-快速上手)
- [🧠 原理解析](#-原理解析)
- [🏗 技术架构](#-技术架构)
- [🔔 更新检测](#-更新检测)
- [🙏 开源项目致谢](#-开源项目致谢)
- [📚 参考代码与内容出处](#-参考代码与内容出处)
- [🤝 参与贡献](#-参与贡献)
- [📄 版权与许可证](#-版权与许可证)
- [📋 免责声明](#-免责声明)
- [📌 版本历史](#-版本历史)
- [⭐ 支持本项目](#-支持本项目)

---

## 📥 下载安装

前往 [Releases](https://github.com/sillycats/ApkAdAnalyzer/releases) 页面下载最新 APK：

- **GitHub Releases**：https://github.com/sillycats/ApkAdAnalyzer/releases
- **GitHub 仓库**：https://github.com/sillycats/ApkAdAnalyzer

> 安装时如提示"未知来源"，请在系统设置中允许安装来自此来源的应用。

---

## 📖 项目简介

一键分析任意 APK 中集成的广告特征：解析 **DEX 字节码、AndroidManifest.xml、res/layout 布局、assets 资源、原生库 (.so)** 与 **Flutter (libapp.so)**，自动识别命中的广告 SDK、类名、方法、权限、URL 等特征，并按分类聚合展示。

全程本地离线处理，无需网络，APK 文件不离开设备本地；分析结果可直接导出为与特征配置文件 (`ad_patterns.json`) 格式完全一致的 JSON。

**适用人群**：安全研究人员、广告 SDK 库维护者、应用开发者、UI/产品同事，以及一切对 APK 植入广告构成好奇的逆向爱好者。

---

## ✨ 功能特性

- **高速多模式匹配**：基于自研 Aho-Corasick 自动机，对所有特征单遍扫描字节流即可同时命中全部特征，数百上千条特征下性能依旧出色
- **18 类广告特征全覆盖**：SDK 包名 / 类名关键词 / 方法名 / 权限 / URL / View / Activity / Service / Receiver / 资源 / 布局 / assets / 根目录文件 / 原生库 / Flutter 字符串等
- **Flutter 应用适配**：解析 `lib/(ABI)/libapp.so` 字节，识别 Flutter / Dart 应用内嵌的广告字符串特征
- **AXML 深度解析**：直接解析二进制 AndroidManifest.xml 与布局文件字符串池，准确提取组件类名、权限名与布局元素关键词
- **大小写智能折叠**：对 ASCII 大小写不敏感，无需复制输入字节，内存占用更低
- **一键导出配置**：命中结果聚合为与 `ad_patterns.json` 格式完全一致的广告特征配置，支持复制或保存到本地
- **精简日志展示**：分类汇总 + 每类至多前 10 条特征列表（超出合并计数），避免海量结果刷屏
- **实时进度反馈**：分析进度条 + 当前文件提示，大 APK 分析过程清晰可见
- **明暗双主题**：跟随系统 / 白天 / 夜间三种模式
- **内置更新检测**：可配置更新清单地址与蓝奏云下载链接，联网可检出新版本

---

## 🚀 快速上手

### 编译

```bash
# 环境要求：Android Studio / JDK 17+ / Android SDK 34
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`，可直接安装使用。

### 使用

1. 选择需要分析的 APK 文件
2. 点击"开始分析"，自动完成特征扫描
3. 查看按分类聚合的命中结果，可"复制配置"或"保存配置"导出
4. 分析完成后，可按需点击"检查更新"以确认是否有新版本

---

## 🧠 原理解析

- **Aho-Corasick 多模式匹配**：将 "SDK 包名 / 类名 / 方法 / 权限 / URL" 等全部特征一次性编译进自动机，随后对任意字节流单遍扫描即可命中全部特征，时间复杂度为 O(S + n)
- **无需反编译的字节级扫描**：传统方案需借助 dexlib2/smali 反汇编 DEX 后逐条匹配；本工具直接对 DEX 与 AXML 原始字节扫描，不涉及任何字节码改写
- **AXML 字符串池解析**：直接解析 AndroidManifest.xml 与 `res/layout/*.xml` 的二进制 chunk 与 StringPool（UTF-8 / UTF-16LE），将字符串引用解析为可读文本
- **Flutter 特征识别**：Flutter 主要逻辑编译进 `libapp.so`，对该文件字节流做单遍扫描，可识别 Dart 快照内嵌的广告 SDK 字符串特征

---

## 🏗 技术架构

| 模块 | 技术方案 | 核心说明 |
|------|----------|----------|
| 特征匹配引擎 | Aho-Corasick 自动机 | 字节字母表（256 进），单遍扫描同时命中全部特征 |
| AXML 解析 | 自研解析器 | 解析 AXML 字符串池，提取类名 / 权限名 / 布局元素 |
| Flutter 检测 | 字节扫描 | 解析 libapp.so 广告字符串特征 |
| DEX 扫描 | 字节流匹配 | 覆盖 sdk 包名 / 类名 / 方法关键词 |
| UI | Kotlin | AppCompat + Material Components，三档明暗主题 |

---

## 🔔 更新检测

工具内置版本更新检测功能：

- **更新清单地址**（默认）：指向本仓库根目录的 `update.json`
- **下载备用地址**：蓝奏云分享链接（更新失败时浏览器打开）
- 检测到更高版本时，将提示前往 Releases 或备用地址下载新版

> 网络受限或更新清单未同步的场合，可能检测失败，属预期行为。

---

## 🙏 开源项目致谢

本项目在开发、构建与运行中使用了以下开源项目，向各位作者与组织表示诚挚感谢：

| 分类 | 开源项目 | 协议 |
|------|----------|------|
| 直接依赖 | [AndroidX](https://developer.android.com/jetpack) · [Material Components](https://github.com/material-components/material-components-android) · [Kotlin 标准库](https://kotlinlang.org/) · [JUnit](https://junit.org/) | Apache 2.0 / EPL 2.0 |
| 构建工具链 | [Android Gradle Plugin](https://developer.android.com/build) · [Gradle](https://gradle.org/) · [AAPT2 / zipalign](https://developer.android.com/tools/aapt2) | Apache 2.0 |
| 参考来源 | [DTL-X](https://github.com/Gameye98/DTL-X)（广告特征规则）· [AOSP](https://android.googlesource.com/)（AXML）· [Dart SDK](https://github.com/dart-lang/sdk)（Flutter 分析） | 见各项目主页 |

完整清单与许可全文见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

## 📚 参考代码与内容出处

本应用的实现参考了以下公开资料（出处与详细思路详见 [开源声明.md](开源声明.md)）：

- **Aho-Corasick 多模式匹配算法**：Aho & Corasick（1975），[算法说明](https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm)
- **AXML 二进制格式解析**：AOSP Asset Packaging，[AOSP frameworks/base](https://android.googlesource.com/platform/frameworks/base/+/core/res/)
- **广告特征规则整理**：参考 [DTL-X](https://github.com/Gameye98/DTL-X) 思路，结合主流广告 SDK 自行整理
- **Flutter 应用分析**：Dart VM 快照格式，[Dart SDK snapshot.h](https://github.com/dart-lang/sdk/blob/main/runtime/vm/snapshot.h)
- **Android DEX 结构**：[AOSP DEX 规范](https://source.android.com/docs/core/runtime/dex-format)
- **界面排版**：[Material Design 3 规范](https://m3.material.io/)

以上内容仅作技术学习参考，最终实现均为本项目自研；相关版权归原作者所有。

---

## 🤝 参与贡献

欢迎参与本项目开发，一起完善更多广告特征。请遵循 [CONTRIBUTING.md](CONTRIBUTING.md) 中的开发环境、代码规范与提交规范：

- **报告问题 / 功能建议**：请使用 [Issue](https://github.com/sillycats/ApkAdAnalyzer/issues)（参考 [Bug 模板](.github/ISSUE_TEMPLATE/bug_report.md) 与 [功能模板](.github/ISSUE_TEMPLATE/feature_request.md)）
- **提交代码**：Fork 后提交 [Pull Request](https://github.com/sillycats/ApkAdAnalyzer/pulls)（参考 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)）
- **构建验证**：提交前请确保 `./gradlew assembleDebug` 编译通过
- **行为准则**：请遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- **社区交流**：前往 [Discussions](https://github.com/sillycats/ApkAdAnalyzer/discussions) 讨论
- **安全漏洞**：请通过 [SECURITY.md](SECURITY.md) 描述的渠道报告，勿在公开 Issue 中披露

---

## 📄 版权与许可证

本项目基于 [MIT License](LICENSE) 开源，版权归 **© 2026 sillycats** 所有。

- **你可以**：自由使用、修改、分发本项目，但需保留原始版权声明与许可文本
- **你不可以**：使用本项目作者名义进行推广；对项目进行歪曲、误导性描述
- **无担保**：本项目按"现状"提供，不附带任何形式的明示或默示担保

所使用第三方库的许可信息详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，参考代码出处与更多开源说明详见 [开源声明.md](开源声明.md)。

---

## 📋 免责声明

请在使用本应用前仔细阅读以下免责声明：

- **合法用途限制**：本应用仅供学习、研究与个人合法使用；分析结果仅供参考，请结合 APK 实际功能与人工复核后使用
- **使用风险自担**：使用本应用所产生的任何后果均由使用者自行承担
- **结果准确性**：分析基于特征字符串匹配，可能因目标应用混淆、加壳或特征覆盖有限而产生误报 / 漏报
- **无担保声明**：本应用按"现状"提供，不附带任何形式的明示或默示担保；作者不对因使用或无法使用本应用造成的任何损失承担责任
- **同意条款**：使用本应用即视为您已阅读、理解并同意以上全部条款；若不同意，请停止使用

---

## 📌 版本历史

| 版本 | 说明 |
|------|------|
| **v1.0** | 首个正式发布版：自研 AC 引擎，覆盖 DEX / Manifest / layout / assets / 原生库 / Flutter 扫描，一键导出配置，精简分类日志，明暗主题，内置更新检测 |

完整更新记录见 [CHANGELOG.md](CHANGELOG.md)。

---

## ⭐ 支持本项目

- 如果本项目对你有帮助，欢迎 **Star** 支持，你的支持是我持续更新的最大动力
- 有任何问题或建议，欢迎提交 [Issue](https://github.com/sillycats/ApkAdAnalyzer/issues)

---

<div align="center">

**© 2026 sillycats · 本项目基于 [MIT License](LICENSE) 开源**  
Powered by AndroidX · Material Components · Kotlin

</div>