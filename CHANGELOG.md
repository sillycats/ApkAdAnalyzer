# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范。

## [Unreleased]

### 计划中
- 待补充：更多广告特征订阅源、批量分析、命令行模式

## [1.0] - 2026-08-21

### 新增
- 全新 **APK广告特征分析工具**：基于自研 Aho-Corasick 多模式匹配引擎，无需反编译即可对 APK 做高速广告特征扫描
- **18 类广告特征覆盖**：SDK 包名 / 类名关键词 / 方法名 / 权限 / URL / View / Activity / Service / Receiver / 资源 / 布局 / assets / 根目录文件 / 原生库 / Flutter 字符串等
- **DEX 字节流扫描**：对 classes.dex 单遍扫描，命中 sdk 包名、类名、方法关键词
- **AXML 深度解析**：自研解析 AndroidManifest.xml 与 layout 二进制字符串池，提取组件类名、权限名与元素关键词
- **Flutter 应用适配**：解析 lib/(ABI)/libapp.so 字节，识别 Flutter 应用广告字符串特征
- **一键导出配置**：命中结果聚合为与 ad_patterns.json 格式一致的广告特征配置，支持复制或保存
- **精简分类日志**：每类特征至多展示前 10 条，避免海量结果刷屏
- **明暗双主题**：跟随系统、白天、夜间三种模式自由切换
- **内置更新检测**：可配置更新清单地址与蓝奏云下载链接
- 全程本地离线处理，APK 文件不离开设备

### 配置
- 启用 GitHub Discussions 讨论区
- 配置 GitHub Pages 项目主页与自动构建 Action（push / PR 自动编译 Debug 与 Release，打 `v*` 标签自动发布 Release）
- 补全开源文档：贡献指南、行为准则、安全政策、第三方许可声明、Issue / PR 模板
- 新增仓库根目录 `update.json`，供应用内更新检测使用