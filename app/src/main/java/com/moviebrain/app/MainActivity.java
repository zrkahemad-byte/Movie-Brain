package com.moviebrain.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView brainView;
    private WebView siteView;
    private Button bBrain;
    private Button bSite;
    private boolean onSite = false;
    private String lastFrameB64 = null;
    private String pendingJs = null;
    private int pendingDelay = 1200;
    private ValueCallback<Uri[]> fileCb = null;
    private final Set<Long> myDl = new HashSet<>();

    private final BroadcastReceiver rcv = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (!myDl.remove(id)) return;
            new Thread(() -> {
                try {
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    Uri uri = dm.getUriForDownloadedFile(id);
                    if (uri != null) {
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(MainActivity.this, uri);
                        String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (durStr == null && !hasFtyp(uri)) {
                            mmr.release();
                            notifyBad();
                            return;
                        }
                        long dur = durStr != null ? Long.parseLong(durStr) : 7000L;
                        long pos = Math.max(0L, (dur - 200L) * 1000L);
                        Bitmap bmp = mmr.getFrameAtTime(pos, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        mmr.release();
                        if (bmp != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                            lastFrameB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                            runOnUiThread(() -> brainView.evaluateJavascript("window.onFrame&&window.onFrame()", null));
                        }
                    }
                } catch (Exception e) {
                    notifyBad();
                }
            }).start();
        }
    };

    private boolean hasFtyp(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            byte[] h = new byte[12];
            int n = is.read(h);
            return n >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        } catch (Exception e) {
            return false;
        }
    }

    private void notifyBad() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Download kharab hai (video file nahi bani)", Toast.LENGTH_LONG).show();
            brainView.evaluateJavascript("window.onDl&&window.onDl(false)", null);
        });
    }

    private String buildJs(String prompt, String ori, boolean isExtend) {
        String tpl = readAsset("site_inject.js");
        if (tpl.isEmpty()) return null;
        String fB64 = (isExtend && lastFrameB64 != null) ? lastFrameB64 : "";
        return tpl
                .replace("__P__", JSONObject.quote(prompt))
                .replace("__O__", JSONObject.quote(ori))
                .replace("__F__", JSONObject.quote(fB64))
                .replace("__EXT__", isExtend ? "true" : "false");
    }

    private void runService(String text) {
        try {
            Intent i = new Intent(this, AutomationService.class);
            i.putExtra("text", text == null ? "" : text);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#10131a"));
        getWindow().setNavigationBarColor(Color.parseColor("#10131a"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#10131a"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#10131a"));

        bBrain = new Button(this);
        bBrain.setText("Movie Brain");
        bBrain.setAllCaps(false);

        bSite = new Button(this);
        bSite.setText("Site");
        bSite.setAllCaps(false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(bBrain, lp);
        bar.addView(bSite, lp);

        FrameLayout frame = new FrameLayout(this);
        brainView = new WebView(this);
        siteView = new WebView(this);
        setupWebView(brainView, true);
        setupWebView(siteView, false);

        frame.addView(brainView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(siteView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(frame, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        bBrain.setOnClickListener(v -> showView(false));
        bSite.setOnClickListener(v -> showView(true));
        showView(false);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(rcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(rcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
        SharedPreferences sp = getSharedPreferences("mb", MODE_PRIVATE);
        String savedUrl = sp.getString("url", "https://theresanaiforthat.com/");
        brainView.loadUrl("file:///android_asset/index.html");
        siteView.loadUrl(savedUrl);
    }

    private void showView(boolean site) {
        onSite = site;
        brainView.setVisibility(site ? View.GONE : View.VISIBLE);
        siteView.setVisibility(site ? View.VISIBLE : View.GONE);
        bBrain.setBackgroundColor(Color.parseColor(site ? "#202634" : "#2a8f82"));
        bSite.setBackgroundColor(Color.parseColor(site ? "#2a8f82" : "#202634"));
        bBrain.setTextColor(Color.WHITE);
        bSite.setTextColor(Color.WHITE);
    }

    private void setupWebView(WebView wv, boolean isBrain) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-E315F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebChromeClient(new Chrome());
        if (isBrain) {
            wv.addJavascriptInterface(new BrainBridge(), "Android");
            wv.setWebViewClient(new WebViewClient());
        } else {
            wv.addJavascriptInterface(new SiteBridge(), "Bot");
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (url != null && url.startsWith("http")) {
                        getSharedPreferences("mb", MODE_PRIVATE).edit().putString("url", url).apply();
                    }
                    if (pendingJs != null) {
                        String js = pendingJs;
                        pendingJs = null;
                        final int dl = pendingDelay;
                        pendingDelay = 1200;
                        view.postDelayed(() -> view.evaluateJavascript(js, null), dl);
                    }
                }
            });
            wv.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                    try {
                        String name = URLUtil.guessFileName(url, cd, mime);
                        if (mime != null && mime.startsWith("video") && !name.toLowerCase().endsWith(".mp4")) {
                            name = name.replaceAll("\\.[^.]*$", "") + ".mp4";
                        }
                        name = "MB_" + new java.text.SimpleDateFormat("MMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + "_" + name;
                        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (cookie != null) req.addRequestHeader("Cookie", cookie);
                        req.addRequestHeader("User-Agent", ua);
                        String ref = siteView.getUrl() != null ? siteView.getUrl() : "https://theresanaiforthat.com/";
                        req.addRequestHeader("Referer", ref);
                        req.setMimeType(mime);
                        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        long id = dm.enqueue(req);
                        myDl.add(id);
                        Toast.makeText(MainActivity.this, "Download shuru ho gaya", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Is link se download nahi hua. Video par dabaye rakho ya site ka download button use karo.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private String readAsset(String name) {
        try (InputStream is = getAssets().open(name);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    public class BrainBridge {
        @JavascriptInterface
        public String generate(String prompt, String ori, boolean isExtend) {
            final String js = buildJs(prompt, ori, isExtend);
            if (js == null) return "error";
            runOnUiThread(() -> {
                showView(true);
                siteView.evaluateJavascript(js, null);
            });
            return isExtend ? "extend" : "new";
        }

        @JavascriptInterface
        public String generateReload(String prompt, String ori, boolean isExtend) {
            final String js = buildJs(prompt, ori, isExtend);
            if (js == null) return "error";
            runOnUiThread(() -> {
                showView(true);
                pendingJs = js;
                pendingDelay = 2500;
                siteView.reload();
            });
            return "reload";
        }

        @JavascriptInterface
        public void ring(int sec, int vol) {
            final int s = Math.max(2, Math.min(5, sec));
            final int v = Math.max(5, Math.min(100, vol));
            runOnUiThread(() -> {
                try {
                    final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, v);
                    final Handler h = new Handler(Looper.getMainLooper());
                    for (int i = 0; i < s * 2; i++) {
                        h.postDelayed(() -> tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300), i * 500L);
                    }
                    h.postDelayed(() -> tg.release(), s * 1000L + 800L);
                } catch (Exception ignored) {
                }
            });
            vibrate();
        }

        @JavascriptInterface
        public void notifyAlert(String title, String text) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel ch = new NotificationChannel("mb_alert", "Movie Brain alerts", NotificationManager.IMPORTANCE_HIGH);
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                    nm.createNotificationChannel(ch);
                }
                PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 1, new Intent(MainActivity.this, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification.Builder b = Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(MainActivity.this, "mb_alert")
                        : new Notification.Builder(MainActivity.this);
                b.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(text)
                        .setContentIntent(pi).setAutoCancel(true);
                nm.notify(2, b.build());
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void startAutomation(String text) {
            runService(text);
        }

        @JavascriptInterface
        public void updateAutomation(String text) {
            if (AutomationService.running) runService(text);
        }

        @JavascriptInterface
        public void stopAutomation() {
            try {
                stopService(new Intent(MainActivity.this, AutomationService.class));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public boolean isAutomationActive() {
            return AutomationService.running;
        }

        @JavascriptInterface
        public void toSite() {
            runOnUiThread(() -> showView(true));
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void vibrate() {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) v.vibrate(250);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String saveFile(String name, String data) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) return "err";
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(data.getBytes(StandardCharsets.UTF_8));
                    }
                    return "ok";
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File f = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(data.getBytes(StandardCharsets.UTF_8));
                    }
                    return "ok";
                }
            } catch (Exception e) {
                return "err";
            }
        }
    }

    public class SiteBridge {
        @JavascriptInterface
        public void report(String status, String detail) {
            runOnUiThread(() -> {
                String js = "window.onBot&&window.onBot(" + JSONObject.quote(status) + "," + JSONObject.quote(detail) + ")";
                brainView.evaluateJavascript(js, null);
            });
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("Haan", (d, w) -> result.confirm())
                    .setNegativeButton("Nahi", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            EditText et = new EditText(MainActivity.this);
            et.setText(defaultValue);
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(et)
                    .setPositiveButton("Haan", (d, w) -> result.confirm(et.getText().toString()))
                    .setNegativeButton("Nahi", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (fileCb != null) fileCb.onReceiveV
