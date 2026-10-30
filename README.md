# Folio 

Folio 是一个以本机保存为主的 Android 生活记录 App。你可以留下电影票根、日常声音、阅读摘抄、喜欢的旋律、沿途风景，或写一封暂时不寄出的信。接口采用深色幕布、纸张、错落卡片与透明毛玻璃，希望记录生活的过程安静而有留白。

**基础功能不需要账号、API Key 或后端服务。** 安装开发环境后，克隆项目即可构建运行；AI 对话是可选功能，由用户自行配置模型服务。

## 可以记录什么

| 模块 | 英文名称 | 功能 |
| --- | --- | --- |
| 票根 | Tickets | 记录电影与观影感想，导入票根图片，选择便签或海报样式。 |
| 日常留声 | Everyday sounds | 现场录音或导入音频，留下地点、场景照片与故事。 |
| 阅读手记 | Reading notes | 保存书中摘抄与感悟，将图片、文本放在同一张纸上。 |
| 旋律 | Melodies | 留下歌词、照片与听歌时的心情，自订卡片外观。 |
| 沿途足迹 | Along the way | 制作多页纸质手帐，调整照片并放置文本。 |
| 写一封信 | Write a letter | 以打字机接口写信、选择信封，拆信阅读与翻页。 |
| 与记忆对话 | Talk to memories | 链接自己的模型服务，搜索记忆、对话、整理个人线索与阶段回顾。 |

首页会从已有记录中选取近期内容，组成可拖动、缩放的幕布。小地图显示目前视野；点击卡片可进入对应模块。接口缺省繁体中文，可在「功能 → 语言 / Language」切换英文。

## 快速运行：Android Studio

### 环境要求

以下版本来自目前项目的构建设置：

| 项目 | 要求／版本 |
| --- | --- |
| Android Studio | 能支持 Android Gradle Plugin **9.3.3** 的版本 |
| Android SDK Platform | **API 37** |
| Gradle | **9.5.0**，由项目附带的 Wrapper 管理 |
| Gradle Daemon JDK | **25**，由 `gradle/gradle-daemon-jvm.properties` 指定 |
| Java 原代码兼容性 | Java 11；这与运行 Gradle 所需的 JDK 版本不同 |
| 手机／仿真器 | Android **7.0 / API 24** 或以上 |

首次同步需要连接下载 Gradle、构建依赖，以及必要的工具链。依赖下载完成后，基础记录功能可脱机使用。

### 运行步骤

1. 克隆GitHub 仓库

2. 在 Android Studio 选择 **Open**，打开包含 `settings.gradle.kts` 的项目根目录。
3. 在 **SDK Manager** 安装 Android SDK Platform 37、Platform-Tools，以及同步时提示需要的 Build-Tools。
4. 等待 Gradle Sync 完成。项目指定 JDK 25；若 IDE 提示缺少 Java，依提示下载对应 JDK，并检查 **Settings → Build Tools → Gradle** 的 JDK 设置。
5. 创建 API 24 以上的 Android 仿真器，或连接已打开 USB 调试的 Android 手机。
6. 选择 `app` 运行设置，点击 **Run ▶**。

Android Studio 会在本机产生 SDK 路径设置。**不需要拷贝作者的 `local.properties`，也不需要创建 `.env`。**

Debug 版本首次启动会加入示例记录，方便体验各个模块；Release 版本不会自动加入这些记录。

## 命令行构建与安装

先安装 JDK 25、Android SDK Platform 37 和 Platform-Tools，并让 `JAVA_HOME` 指向 JDK、`ANDROID_HOME` 指向自己的 Android SDK。也可以通过本机的 `local.properties` 设置 `sdk.dir`，此文件不应上传。

macOS / Linux：

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

如果 macOS / Linux 提示无运行权限，先运行 `chmod +x gradlew`。不需要另外安装系统版 Gradle。

产出的安装档：

```text
app/build/outputs/apk/debug/app-debug.apk
```

连接一台手机或启动仿真器后，可以直接安装：

```bash
./gradlew :app:installDebug
```

安装后从设备上的 App 图标打开。Debug APK 适合本机体验；正式发布请在 Android Studio 使用 **Generate Signed App Bundle / APK**，以自己的签名密钥产生 Release 版本。

## 使用方法

### 留下第一条记忆

1. 启动后进入首页，点击右上角「全部记忆」。
2. 点击底部「功能」，选择想使用的模块。
3. 点击「创建」，直接进入目前模块的编辑页。
4. 填写文本、选择照片或录制声音，保存后回到展示页。
5. 点击卡片阅读内容，通过页面上方的修改入口继续编辑。

在「全部」页面创建时，会沿用最近选择的模块；首次缺省为票根。录音时需授予麦克风权限，导入媒体时通过系统选择器授权所选文件。

### 浏览与返回

- 首页可拖动幕布、双指缩放，也可操作左下角缩放图标与小地图。
- 首页预览卡片会打开对应的整个模块，进入模块后再点击卡片阅读详情。
- 模块展示页左上角「返回主页」可回到首页。
- 底部「功能」可切换模块、进入「与记忆对话」或修改语言。
- 语言切换只改变接口，原有记录与信件不会自动翻译。

### 配置「与记忆对话」（可选）

1. 从首页或「功能」进入「与记忆对话」。
2. 在「模型」设置中填写自己的 API Key 和模型名称；使用其他供应商时，展开「模型服务地址」填写其 HTTPS API 地址。
3. 缺省地址为 `https://api.openai.com/v1`。也可使用提供兼容 Chat Completions 接口的服务，例如 DeepSeek；请依供应商提供的地址与模型名称填写。
4. 点击「保存并测试模型」，连接成功后即可对话。完整记忆整理功能需要模型支持工具调用与 JSON 输出，实际兼容性取决于供应商和模型。

测试与对话都会请求用户选择的供应商，可能产生 API 费用。仓库不附带任何可用密钥。

应用会参考本机六类记录的文本，也支持额外导入 UTF-8 `.txt` / `.md`。图片、录音与音乐目前使用你填写的说明、歌词、摘抄和感想作为记忆，不会自动辨识图片或转写音频。整理出的个人线索需要用户确认。

更多对话、记忆整理、任务恢复与备份说明，见 [AGENT_README.md](AGENT_README.md)。

### 备份与数据

- 记录与媒体主要保存在 App 的本机私有空间。
- 在「与记忆对话」的「管理」页可导出 ZIP 备份，亦可从备份恢复记录。
- 卸载或清除应用数据前，请先导出需要保留的内容。
- API Key 以 Android Keystore 支持的加密方式保存在设备，排除于应用导出备份及系统备份／迁移设置之外。
- 使用 AI 功能时，相关记忆文本会发送至你配置的模型服务。

## 项目结构

```text
app/                         Android App：接口、本机数据与设备端记忆 Agent
  src/main/java/             Java 原代码
  src/main/res/              图片、版面、字体与中英文资源
  src/main/assets/fonts/     随附字体的授权文档
gradle/                      Wrapper、工具链及依赖版本
backend/                     早期独立服务实作，现行 App 不需要启动
docs/                        字体、上传流程等文档
example/                     设计参考图片，非运行 App 的必要文件
AGENT_README.md              设备端记忆对话的详细说明
roadmap.md                   功能与修改记录
```

技术主要为 Java、Android Views / Canvas、Room / SQLite、Android Keystore 与直接请求模型服务的设备端 Agent。项目没有登录服务器或缺省的跨设备同步服务。

## 常见问题

**找不到 SDK / `SDK location not found`**  
用 Android Studio 打开并选择本机 SDK，或设置自己的 `ANDROID_HOME` / `local.properties`。请勿使用其他电脑的绝对路径。

**找不到 API 37、JDK 或 Gradle 同步失败**  
先确认环境符合上方版本表，安装缺少的 SDK、接受 Android SDK 授权，并确认可以连接到 Google Maven、Maven Central 与 Gradle 下载站。首次构建不要使用 `--offline`。

**没有 API Key 可以用吗？**  
可以。六类记录的创建、编辑、浏览和媒体功能均不要求模型配置；AI 对话与模型整理功能才需要。

**需要部署 `backend/` 吗？**  
不需要。现行 Android App 内置记忆 Agent，直接调用用户配置的模型服务。

