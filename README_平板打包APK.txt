照片氣氛動畫播放器 Android APK 包裝專案
Web 版本：v0.3.8

這個專案已把 v0.3.8 單檔 HTML 放進 Android WebView。

最省事的雲端打包方式：
1. 把整個專案資料夾上傳到 GitHub repository。
2. GitHub → Actions → Build APK → Run workflow。
3. 建置完成後下載 artifact：photo-atmosphere-player-v0.3.8-apk。
4. 解壓後得到 app-debug.apk，可在 Android 裝置安裝測試。

本包裝已包含：
- JavaScript / localStorage / 音訊播放
- 照片、音樂、字幕、字型的檔案選擇器
- Web 版 v0.3.8 全部 HTML/CSS/JS 與內嵌素材
- Android 返回手勢防誤退出：第一次返回會提示，再按一次才離開

注意：
- Android WebView 對「整個資料夾選取」的支援可能跟 Chrome 不完全相同；單檔/多檔選取可先測。
- 系統螢幕錄影仍使用 Android 原生螢幕錄影即可。
