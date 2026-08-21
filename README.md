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

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。所使用第三方库的许可信息详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，参考代码出处详见 [开源声明.md](开源声明.md)。

> ⚠️ 本工具仅供学习、研究与个人合法用途使用。分析结果仅供参考，请结合 APK 实际功能与人工复核后使用。