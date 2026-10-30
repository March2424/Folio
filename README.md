# Folio · dontwannaforget

把不想忘記的生活，慢慢收進一本記憶冊。

Folio 是一個以本機儲存為主的 Android 生活記錄 App。你可以留下電影票根、日常聲音、閱讀摘抄、喜歡的旋律、沿途風景，或寫一封暫時不寄出的信。介面採用深色幕布、紙張、錯落卡片與透明毛玻璃，希望記錄生活的過程安靜而有留白。

**基礎功能不需要帳號、API Key 或後端服務。** 安裝開發環境後，克隆專案即可建置執行；AI 對話是可選功能，由使用者自行配置模型服務。

## 可以記錄什麼

| 模組 | 英文名稱 | 功能 |
| --- | --- | --- |
| 票根 | Tickets | 記錄電影與觀影感想，匯入票根圖片，選擇便籤或海報樣式。 |
| 日常留聲 | Everyday sounds | 現場錄音或匯入音訊，留下地點、場景照片與故事。 |
| 閱讀手記 | Reading notes | 保存書中摘抄與感悟，將圖片、文字放在同一張紙上。 |
| 旋律 | Melodies | 留下歌詞、照片與聽歌時的心情，自訂卡片外觀。 |
| 沿途足跡 | Along the way | 製作多頁紙質手帳，調整照片並放置文字。 |
| 寫一封信 | Write a letter | 以打字機介面寫信、選擇信封，拆信閱讀與翻頁。 |
| 與記憶對話 | Talk to memories | 連結自己的模型服務，搜尋記憶、對話、整理個人線索與階段回顧。 |

首頁會從已有記錄中選取近期內容，組成可拖動、縮放的幕布。小地圖顯示目前視野；點擊卡片可進入對應模組。介面預設繁體中文，可在「功能 → 語言 / Language」切換英文。

## 快速執行：Android Studio

### 環境要求

以下版本來自目前專案的建置設定：

| 項目 | 要求／版本 |
| --- | --- |
| Android Studio | 能支援 Android Gradle Plugin **9.3.3** 的版本 |
| Android SDK Platform | **API 37** |
| Gradle | **9.5.0**，由專案附帶的 Wrapper 管理 |
| Gradle Daemon JDK | **25**，由 `gradle/gradle-daemon-jvm.properties` 指定 |
| Java 原始碼相容性 | Java 11；這與執行 Gradle 所需的 JDK 版本不同 |
| 手機／模擬器 | Android **7.0 / API 24** 或以上 |

首次同步需要連線下載 Gradle、建置依賴，以及必要的工具鏈。依賴下載完成後，基礎記錄功能可離線使用。

### 執行步驟

1. 克隆自己的 GitHub 倉庫：

   ```bash
   git clone https://github.com/YOUR_NAME/dontwannaforget.git
   cd dontwannaforget
   ```

   將 `YOUR_NAME` 和倉庫名稱換成實際值。

2. 在 Android Studio 選擇 **Open**，開啟包含 `settings.gradle.kts` 的專案根目錄。
3. 在 **SDK Manager** 安裝 Android SDK Platform 37、Platform-Tools，以及同步時提示需要的 Build-Tools。
4. 等待 Gradle Sync 完成。專案指定 JDK 25；若 IDE 提示缺少 Java，依提示下載對應 JDK，並檢查 **Settings → Build Tools → Gradle** 的 JDK 設定。
5. 建立 API 24 以上的 Android 模擬器，或連接已開啟 USB 偵錯的 Android 手機。
6. 選擇 `app` 執行設定，點擊 **Run ▶**。

Android Studio 會在本機產生 SDK 路徑設定。**不需要複製作者的 `local.properties`，也不需要建立 `.env`。**

Debug 版本首次啟動會加入示例記錄，方便體驗各個模組；Release 版本不會自動加入這些記錄。

## 命令列建置與安裝

先安裝 JDK 25、Android SDK Platform 37 和 Platform-Tools，並讓 `JAVA_HOME` 指向 JDK、`ANDROID_HOME` 指向自己的 Android SDK。也可以透過本機的 `local.properties` 設定 `sdk.dir`，此檔案不應上傳。

macOS / Linux：

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

如果 macOS / Linux 提示無執行權限，先執行 `chmod +x gradlew`。不需要另外安裝系統版 Gradle。

產出的安裝檔：

```text
app/build/outputs/apk/debug/app-debug.apk
```

連接一台手機或啟動模擬器後，可以直接安裝：

```bash
./gradlew :app:installDebug
```

安裝後從裝置上的 App 圖示開啟。Debug APK 適合本機體驗；正式發佈請在 Android Studio 使用 **Generate Signed App Bundle / APK**，以自己的簽名金鑰產生 Release 版本。

## 使用方法

### 留下第一條記憶

1. 啟動後進入首頁，點擊右上角「全部記憶」。
2. 點擊底部「功能」，選擇想使用的模組。
3. 點擊「建立」，直接進入目前模組的編輯頁。
4. 填寫文字、選擇照片或錄製聲音，儲存後回到展示頁。
5. 點擊卡片閱讀內容，透過頁面上方的修改入口繼續編輯。

在「全部」頁面建立時，會沿用最近選擇的模組；首次預設為票根。錄音時需授予麥克風權限，匯入媒體時透過系統選擇器授權所選檔案。

### 瀏覽與返回

- 首頁可拖動幕布、雙指縮放，也可操作左下角縮放圖示與小地圖。
- 首頁預覽卡片會開啟對應的整個模組，進入模組後再點擊卡片閱讀詳情。
- 模組展示頁左上角「返回主頁」可回到首頁。
- 底部「功能」可切換模組、進入「與記憶對話」或修改語言。
- 語言切換只改變介面，原有記錄與信件不會自動翻譯。

### 配置「與記憶對話」（可選）

1. 從首頁或「功能」進入「與記憶對話」。
2. 在「模型」設定中填寫自己的 API Key 和模型名稱；使用其他供應商時，展開「模型服務地址」填寫其 HTTPS API 位址。
3. 預設地址為 `https://api.openai.com/v1`。也可使用提供相容 Chat Completions 介面的服務，例如 DeepSeek；請依供應商提供的地址與模型名稱填寫。
4. 點擊「儲存並測試模型」，連線成功後即可對話。完整記憶整理功能需要模型支援工具呼叫與 JSON 輸出，實際相容性取決於供應商和模型。

測試與對話都會請求使用者選擇的供應商，可能產生 API 費用。倉庫不附帶任何可用密鑰。

應用會參考本機六類記錄的文字，也支援額外匯入 UTF-8 `.txt` / `.md`。圖片、錄音與音樂目前使用你填寫的說明、歌詞、摘抄和感想作為記憶，不會自動辨識圖片或轉寫音訊。整理出的個人線索需要使用者確認。

更多對話、記憶整理、任務恢復與備份說明，見 [AGENT_README.md](AGENT_README.md)。

### 備份與資料

- 記錄與媒體主要保存在 App 的本機私有空間。
- 在「與記憶對話」的「管理」頁可匯出 ZIP 備份，亦可從備份恢復記錄。
- 卸載或清除應用資料前，請先匯出需要保留的內容。
- API Key 以 Android Keystore 支援的加密方式儲存在裝置，排除於應用匯出備份及系統備份／遷移設定之外。
- 使用 AI 功能時，相關記憶文字會傳送至你配置的模型服務。

## 專案結構

```text
app/                         Android App：介面、本機資料與裝置端記憶 Agent
  src/main/java/             Java 原始碼
  src/main/res/              圖片、版面、字型與中英文資源
  src/main/assets/fonts/     隨附字型的授權文件
gradle/                      Wrapper、工具鏈及依賴版本
backend/                     早期獨立服務實作，現行 App 不需要啟動
docs/                        字型、上傳流程等文件
example/                     設計參考圖片，非執行 App 的必要檔案
AGENT_README.md              裝置端記憶對話的詳細說明
roadmap.md                   功能與修改記錄
```

技術主要為 Java、Android Views / Canvas、Room / SQLite、Android Keystore 與直接請求模型服務的裝置端 Agent。專案沒有登入伺服器或預設的跨裝置同步服務。

## 常見問題

**找不到 SDK / `SDK location not found`**  
用 Android Studio 開啟並選擇本機 SDK，或設定自己的 `ANDROID_HOME` / `local.properties`。請勿使用其他電腦的絕對路徑。

**找不到 API 37、JDK 或 Gradle 同步失敗**  
先確認環境符合上方版本表，安裝缺少的 SDK、接受 Android SDK 授權，並確認可以連線到 Google Maven、Maven Central 與 Gradle 下載站。首次建置不要使用 `--offline`。

**沒有 API Key 可以用嗎？**  
可以。六類記錄的建立、編輯、瀏覽和媒體功能均不要求模型配置；AI 對話與模型整理功能才需要。

**需要部署 `backend/` 嗎？**  
不需要。現行 Android App 內建記憶 Agent，直接呼叫使用者配置的模型服務。

## 字型與授權

介面字型使用 Noto Serif TC、Noto Serif SC 與 IBM Plex Mono 的組合衍生字型，隨 App 離線提供。來源、處理方式與 SIL Open Font License 文件位置見 [docs/TYPOGRAPHY.md](docs/TYPOGRAPHY.md)。

目前專案尚未指定整體原始碼授權條款；字型授權不代表專案程式、示例圖片、文字或其他素材採用相同授權。

## 上傳自己的 GitHub 倉庫

見 [GitHub 上傳流程](docs/GITHUB_UPLOAD.md)。
