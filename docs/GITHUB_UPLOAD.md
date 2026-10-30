# 將專案上傳 GitHub

## 1. 建立空倉庫

登入 GitHub，點擊右上角 **+ → New repository**，填寫倉庫名稱（例如 `dontwannaforget`），選擇 Public 或 Private。

建立時不要勾選自動產生 README、`.gitignore` 或 License；本機已經有 README 與忽略設定。建立後複製 HTTPS 倉庫網址。

## 2. 建立本機版本記錄

在專案根目錄執行：

```bash
cd /path/to/dontwannaforget
git init -b main
git add .
git status --short
```

提交清單應包含 `app/src/`、建置設定、`gradle/wrapper/gradle-wrapper.jar`、`gradlew`、`gradlew.bat` 與文件；不應包含 `.env`、`local.properties`、`.idea/`、建置目錄、私人簽名金鑰或個人資料備份。

確認後建立第一個提交：

```bash
git commit -m "Initial commit: Folio memory journal"
```

如果 Git 提示尚未設定身分，先設定本倉庫使用的名稱與電子郵件，再重試提交：

```bash
git config user.name "你的 GitHub 顯示名稱"
git config user.email "你的 GitHub 提交電子郵件"
```

可使用 GitHub 帳號設定中提供的 noreply 電子郵件。

## 3. 連結並上傳

以下網址換成自己的倉庫網址：

```bash
git remote add origin https://github.com/YOUR_NAME/dontwannaforget.git
git push -u origin main
```

若提示驗證，使用 GitHub 認可的登入方式，例如 GitHub CLI 的 `gh auth login`，或 HTTPS 的個人存取權杖。GitHub 帳號密碼不作為 Git 推送的 HTTPS 密碼使用。

推送成功後，重新整理倉庫首頁即可看到 README 與原始碼。

## 4. 讓其他人執行

分享倉庫網址即可，對方依 README 安裝對應 Android 開發環境後：

```bash
git clone https://github.com/YOUR_NAME/dontwannaforget.git
cd dontwannaforget
```

使用 Android Studio 開啟根目錄，等待同步後 Run `app`。本機 SDK 路徑由對方的 Android Studio 產生，基礎功能不需要 `.env`、後端或你的 API Key。

如果希望對方不安裝開發環境也能體驗，可另外在 GitHub Releases 附上你產生的 APK；APK 是安裝成品，與克隆原始碼後建置是兩種使用方式。

## 5. 以後更新

在本機完成修改後：

```bash
git add .
git diff --cached --stat
git commit -m "Describe your changes"
git push
```

第一次上傳後，可將 README 中的 `YOUR_NAME` 與倉庫名稱替換為實際值。

參考：[GitHub 官方的本機程式碼上傳說明](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)。
