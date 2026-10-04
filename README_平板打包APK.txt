照片氣氛動畫播放器 Android APK 專用修正版
APK 版本：v0.3.10
基準：v0.3.8 / Build APK #21 成功安裝版
日期時間：2026-10-04 12:25

本版只針對 APK / Android WebView 相容性調整，不改原本網頁母版。

本次修正：
1. 字幕、音樂、字型：改用較穩定的 Android 原生文件選擇流程，降低 WebView 對副檔名/MIME 判斷造成的載入失敗。
2. 載入資料夾：按鈕改由 Android 系統資料夾選擇器處理；會遞迴找出 MP3/M4A/AAC/WAV/OGG/FLAC 與 LRC/SRT，再交回原本播放器配對。
3. 全螢幕返回：v0.3.10 改由 Android 原生系統列控制，不再依賴 WebView 的網頁全螢幕；返回手勢先退出全螢幕。
4. APK 介面字型 fresh default：138% → 120%。舊 APK 若仍是舊預設 138%，第一次進 v0.3.9 以上版本會一次性調成 120%；之後仍可自行用－／＋調整。
5. GitHub Actions：只有 .build-trigger 更新或手動 Run workflow 才打包，避免上傳 ZIP 每個檔案都觸發一次 Build APK。

保留／回歸原則：
- v0.3.8 已確認可用的照片、MP4、播放、主題、字幕樣式、音波、氣氛、構圖等 HTML 功能不改邏輯。
- applicationId 保持 com.taro.atmosphereplayer，可直接覆蓋安裝測試版。
- 原 v0.3.8 基準專案另保留備份，不覆蓋。

打包：
- 用私人工具管理中心把本 ZIP 更新到既有 photo-atmosphere-player-apk repo。
- 建議使用「完整取代」。
- 上傳時工作流程不會每個檔案都跑；最後 .build-trigger 更新後才會跑一次。
- 成功後到 Actions → Build APK → 最新綠色勾勾 → Artifacts 下載 photo-atmosphere-player-v0.3.10-apk。


v0.3.10 修正（2026-10-04 12:19）：
- APK 全螢幕改由 Android 原生系統列控制，返回先退出全螢幕。
- 單獨載入字幕改為 Android 全檔案選擇後再由播放器驗證 LRC／SRT。
- 播放清單匯出真正寫入 Download／下載，成功提示改為存檔完成後才顯示。
- MP3 播放中再載入影片時，影片立即同步音樂時間與播放狀態。
- 強化頁面滑動手勢重置，降低偶發無法滑動。
- APK 圖示已改用使用者最終上傳的裁切圖；不重畫、不加播放符號。
