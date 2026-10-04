package com.taro.atmosphereplayer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final int FOLDER_CHOOSER_REQUEST = 7002;
    private static final int MAX_FOLDER_FILES = 1000;
    private static final String APK_FOLDER_MIME = "application/x-photo-atmosphere-folder";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private long lastBackAt = 0L;
    private boolean backCheckPending = false;
    private boolean nativeFullscreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;

                try {
                    if (isFolderChooser(params)) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        );
                        startActivityForResult(intent, FOLDER_CHOOSER_REQUEST);
                    } else {
                        Intent intent = buildDocumentIntent(params);
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    }
                    return true;
                } catch (ActivityNotFoundException e) {
                    clearFileCallback();
                    Toast.makeText(MainActivity.this, "找不到可用的檔案選擇器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private boolean isFolderChooser(WebChromeClient.FileChooserParams params) {
        String[] acceptTypes = params.getAcceptTypes();
        if (acceptTypes == null) return false;

        for (String raw : acceptTypes) {
            if (raw == null) continue;
            for (String part : raw.split(",")) {
                if (APK_FOLDER_MIME.equals(part.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Intent buildDocumentIntent(WebChromeClient.FileChooserParams params) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        String[] mimeTypes = resolveMimeTypes(params.getAcceptTypes());
        if (mimeTypes.length == 1 && !"application/octet-stream".equals(mimeTypes[0])) {
            intent.setType(mimeTypes[0]);
        } else {
            intent.setType("*/*");
            if (mimeTypes.length > 0 && !containsWildcardMime(mimeTypes)) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
        }

        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    private boolean containsWildcardMime(String[] mimeTypes) {
        if (mimeTypes == null) return false;
        for (String mime : mimeTypes) {
            if ("*/*".equals(mime)) return true;
        }
        return false;
    }

    private String[] resolveMimeTypes(String[] acceptTypes) {
        Set<String> out = new LinkedHashSet<>();
        if (acceptTypes != null) {
            for (String raw : acceptTypes) {
                if (raw == null) continue;
                for (String item : raw.split(",")) {
                    String token = item.trim().toLowerCase(Locale.ROOT);
                    if (token.isEmpty() || APK_FOLDER_MIME.equals(token)) continue;

                    if ("*/*".equals(token)) {
                        out.clear();
                        out.add("*/*");
                        return out.toArray(new String[0]);
                    }

                    if (token.startsWith(".")) {
                        switch (token) {
                            case ".lrc":
                            case ".srt":
                            case ".txt":
                                // Some Android document providers classify subtitle files as
                                // text/plain while others report application/octet-stream.
                                // Keep both so a subtitle file is not hidden by the picker.
                                out.add("text/plain");
                                out.add("application/octet-stream");
                                break;
                            case ".json":
                                out.add("application/json");
                                out.add("text/plain");
                                break;
                            case ".ttf":
                                out.add("font/ttf");
                                out.add("application/x-font-ttf");
                                out.add("application/octet-stream");
                                break;
                            case ".otf":
                                out.add("font/otf");
                                out.add("application/x-font-opentype");
                                out.add("application/octet-stream");
                                break;
                            case ".woff":
                                out.add("font/woff");
                                out.add("application/octet-stream");
                                break;
                            case ".woff2":
                                out.add("font/woff2");
                                out.add("application/octet-stream");
                                break;
                            default:
                                out.add("application/octet-stream");
                                break;
                        }
                    } else if (token.contains("/")) {
                        out.add(token);
                    }
                }
            }
        }
        return out.toArray(new String[0]);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FOLDER_CHOOSER_REQUEST) {
            handleFolderResult(resultCode, data);
            return;
        }

        if (requestCode == FILE_CHOOSER_REQUEST) {
            handleFileResult(resultCode, data);
        }
    }

    private void handleFileResult(int resultCode, Intent data) {
        if (fileCallback == null) return;

        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        if (result != null) {
            for (Uri uri : result) {
                persistReadPermission(data, uri);
            }
        }

        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    private void handleFolderResult(int resultCode, Intent data) {
        if (fileCallback == null) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
            return;
        }

        Uri treeUri = data.getData();
        persistReadPermission(data, treeUri);

        List<Uri> files = new ArrayList<>();
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
            collectTreeFiles(treeUri, rootDocument, files, 0);
        } catch (Exception e) {
            files.clear();
        }

        if (files.isEmpty()) {
            fileCallback.onReceiveValue(null);
            Toast.makeText(this, "這個資料夾沒有找到可載入的音樂或 LRC／SRT", Toast.LENGTH_LONG).show();
        } else {
            fileCallback.onReceiveValue(files.toArray(new Uri[0]));
            Toast.makeText(this, "已選取資料夾，共找到 " + files.size() + " 個可用檔案", Toast.LENGTH_SHORT).show();
        }
        fileCallback = null;
    }

    private void collectTreeFiles(Uri treeUri, Uri parentDocument, List<Uri> out, int depth) {
        if (depth > 12 || out.size() >= MAX_FOLDER_FILES) return;

        String parentId = DocumentsContract.getDocumentId(parentDocument);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);

        List<Uri> childFolders = new ArrayList<>();
        String[] columns = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return;

            int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);

            while (cursor.moveToNext() && out.size() < MAX_FOLDER_FILES) {
                String documentId = idIndex >= 0 ? cursor.getString(idIndex) : null;
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "";
                String mime = mimeIndex >= 0 ? cursor.getString(mimeIndex) : "";

                if (documentId == null) continue;
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    childFolders.add(childUri);
                } else if (isSupportedFolderFile(name, mime)) {
                    out.add(childUri);
                }
            }
        } catch (Exception ignored) {
            return;
        }

        for (Uri folder : childFolders) {
            if (out.size() >= MAX_FOLDER_FILES) break;
            collectTreeFiles(treeUri, folder, out, depth + 1);
        }
    }

    private boolean isSupportedFolderFile(String name, String mime) {
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);

        if (lowerMime.startsWith("audio/")) return true;
        return lowerName.endsWith(".mp3")
                || lowerName.endsWith(".m4a")
                || lowerName.endsWith(".aac")
                || lowerName.endsWith(".wav")
                || lowerName.endsWith(".ogg")
                || lowerName.endsWith(".flac")
                || lowerName.endsWith(".lrc")
                || lowerName.endsWith(".srt");
    }

    private void persistReadPermission(Intent data, Uri uri) {
        if (data == null || uri == null) return;
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;

        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // Some document providers grant temporary read access only; the current selection still works.
        }
    }

    private void setNativeFullscreen(boolean enabled) {
        nativeFullscreen = enabled;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            View decor = getWindow().getDecorView();
            if (enabled) {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public boolean isApk() {
            return true;
        }

        @JavascriptInterface
        public void setFullscreen(boolean enabled) {
            runOnUiThread(() -> setNativeFullscreen(enabled));
        }

        @JavascriptInterface
        public void saveTextFile(String requestedName, String text, String mimeType) {
            final String fileName = sanitizeFileName(requestedName);
            final String body = text == null ? "" : text;
            final String mime = (mimeType == null || mimeType.trim().isEmpty())
                    ? "text/plain"
                    : mimeType.split(";", 2)[0].trim();

            new Thread(() -> {
                boolean success = false;
                String message = "";
                String savedName = fileName;

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentResolver resolver = getContentResolver();
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new IllegalStateException("無法建立下載檔案");

                        boolean wrote = false;
                        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
                            if (out == null) throw new IllegalStateException("無法開啟下載檔案");
                            out.write(body.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            wrote = true;
                        } finally {
                            if (wrote) {
                                ContentValues done = new ContentValues();
                                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                                resolver.update(uri, done, null, null);
                            } else {
                                resolver.delete(uri, null, null);
                            }
                        }
                        success = true;
                        message = "已儲存到 Download／下載";
                    } else {
                        // Legacy fallback. Modern Android (the target tablet) uses the MediaStore path above.
                        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) throw new IllegalStateException("找不到下載資料夾");
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new IllegalStateException("無法建立下載資料夾");
                        }
                        File target = new File(dir, fileName);
                        try (FileOutputStream out = new FileOutputStream(target, false)) {
                            out.write(body.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                        success = true;
                        message = "已儲存到應用程式下載資料夾";
                    }
                } catch (Exception e) {
                    success = false;
                    message = e.getMessage() == null ? "儲存失敗" : e.getMessage();
                }

                final boolean ok = success;
                final String finalName = savedName;
                final String finalMessage = message;
                runOnUiThread(() -> dispatchDownloadResult(ok, finalName, finalMessage));
            }).start();
        }
    }

    private String sanitizeFileName(String raw) {
        String name = raw == null ? "export.txt" : raw.trim();
        if (name.isEmpty()) name = "export.txt";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.length() > 180) name = name.substring(0, 180);
        return name;
    }

    private void dispatchDownloadResult(boolean success, String fileName, String message) {
        if (webView == null) return;
        String js = "window.__apkDownloadResult&&window.__apkDownloadResult("
                + success + ","
                + JSONObject.quote(fileName == null ? "" : fileName) + ","
                + JSONObject.quote(message == null ? "" : message)
                + ");";
        webView.evaluateJavascript(js, null);
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }

        if (nativeFullscreen) {
            setNativeFullscreen(false);
            webView.evaluateJavascript(
                    "(function(){try{if(window.__apkExitFullscreen)window.__apkExitFullscreen();}catch(e){}})();",
                    null
            );
            lastBackAt = 0L;
            return;
        }

        if (backCheckPending) return;

        backCheckPending = true;
        webView.evaluateJavascript(
                "(function(){try{return !!(window.__apkExitFullscreen&&window.__apkExitFullscreen());}catch(e){return false;}})();",
                value -> {
                    backCheckPending = false;
                    if ("true".equals(value)) {
                        setNativeFullscreen(false);
                        lastBackAt = 0L;
                        return;
                    }
                    handleNormalBack();
                }
        );
    }

    private void handleNormalBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastBackAt < 1600) {
            super.onBackPressed();
        } else {
            lastBackAt = now;
            Toast.makeText(this, "再按一次返回離開播放器", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFileCallback() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        clearFileCallback();
        setNativeFullscreen(false);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
