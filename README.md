# AU Launcher (Android)

一个基于 Jetpack Compose 的 Android 启动器应用，用于拉取游戏列表、展示/搜索、下载并安装游戏 APK，同时支持本地应用导入与游戏信息上传。

## 功能概览

- 在线游戏列表：从远端 `config.json` 拉取并缓存（30 分钟）。
- 分类与搜索：支持 `ALL / INSTALLED / HOT / NEW` 分类和关键词过滤。
- 下载管理：支持开始、暂停、断点续传和进度显示（`DownloadManager` + DataStore 持久化）。
- 安装与启动：下载完成后拉起系统安装器；已安装应用可直接启动。
- 本地导入：可从已安装应用中导入为本地游戏条目。
- 上传申请：可提交游戏名称、下载链接、描述和封面到 webhook。
- 个性化设置：支持语言（中/英）、背景图、模糊度、遮罩透明度和遮罩颜色。

## 技术栈

- Kotlin + Jetpack Compose
- AndroidX Navigation Compose
- Retrofit + Gson
- OkHttp
- Room
- DataStore Preferences
- Coil

## 环境要求

- Android Studio（建议最新稳定版）
- JDK 11（项目 `sourceCompatibility/targetCompatibility` 为 11）
- Android SDK：
  - `compileSdk = 35`
  - `targetSdk = 35`
  - `minSdk = 24`

## 快速开始

1. 使用 Android Studio 打开项目根目录：`D:\my`
2. 确认 `local.properties` 中 `sdk.dir` 指向本机 Android SDK
3. 同步 Gradle，等待依赖下载完成
4. 连接设备或启动模拟器后运行 `app`

如需命令行构建，可在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

安装到已连接设备：

```powershell
.\gradlew.bat installDebug
```

## 关键配置

### 1) 数据源与下载源

在 `app/src/main/java/com/au/launcher/utils/Constants.kt` 中维护：

- 全球源：`BASE_URL_GLOBAL`、`REPO_CONFIG_URL_GLOBAL`、`DOWNLOAD_URL_GLOBAL`
- 中国镜像：`BASE_URL_CN`、`REPO_CONFIG_URL_CN`、`DOWNLOAD_URL_CN`

应用会根据区域设置选择对应源。

### 2) 上传 webhook

上传功能使用 `WebhookApi`，当前 key 在 `UploadScreen.kt` 中写死。

建议改造为以下方式之一：

- 放入 `local.properties` / `gradle.properties` 并通过 `BuildConfig` 注入
- 使用后端中转，避免在客户端暴露 key

## 下载与安装流程说明

下载逻辑位于 `app/src/main/java/com/au/launcher/utils/DownloadManager.kt`：

- 点击“获取”后开始下载 APK
- 下载中会持续更新进度到 `downloadStates`
- 支持暂停与恢复（HTTP Range）
- 下载完成后通过 `FileProvider` 拉起系统安装器

> 说明：当前代码会在安装前拉起安装器，但不会自动删除下载的 APK 文件；文件保存在应用外部目录 `Android/data/<package>/files/Download/` 下。

## 项目结构（简要）

- `app/src/main/java/com/au/launcher/MainActivity.kt`：应用入口与导航
- `app/src/main/java/com/au/launcher/ui/screens/`：页面（Home/Settings/Upload/Import）
- `app/src/main/java/com/au/launcher/viewmodel/`：状态与业务协调
- `app/src/main/java/com/au/launcher/api/`：接口、模型、仓库
- `app/src/main/java/com/au/launcher/db/`：Room 数据库
- `app/src/main/java/com/au/launcher/utils/`：下载、网络、常量、包管理工具

## 常见问题

- 无法安装 APK：请确认系统允许“安装未知应用”。
- 下载无进度：检查网络与下载源可用性；确认设备有可用存储空间。
- 列表未更新：缓存默认 30 分钟，可通过刷新逻辑强制拉取。

## 备注

- 该 README 基于当前仓库代码结构与配置编写。
- 如果你希望，我可以继续补一版「开发者指南」（模块关系图 + 关键时序 + 常见改造点）。


