package com.moviebrain.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final String DEFAULT_SITE_URL = "https://www.google.com";
    private static final String ALERT_CHANNEL_ID = "movie_brain_alerts";

    private FrameLayout rootContainer;
    private WebView controllerWebView;
    private WebView siteWebView;
    private ValueCallback<Uri[]> fileCb;
    private boolean automationActive = false;
    private String lastSiteUrl = DEFAULT_SITE_URL;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createAlertChannel();

        rootContainer = new FrameLayout(this);
        rootContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        controllerWebView = createConfiguredWebView(true);
        siteWebView = createConfiguredWebView(false);

        rootContainer.addView(siteWebView);
        rootContainer.addView(controllerWebView);
        setContentView(rootContainer);

        showControllerView();
        controllerWebView.loadUrl("file:///android_asset/index.html");
        siteWebView.loadUrl(lastSiteUrl);
    }

    private WebView createConfiguredWebView(boolean isController) {
        WebView wv = new WebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        if (isController) {
            wv.addJavascriptInterface(new ControllerBridge(), "Android");
        } else {
            wv.addJavascriptInterface(new SiteBotBridge(), "Bot");
        }

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (fileCb != null) {
                    fileCb.onReceiveValue(null);
                }
                fileCb = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isController && url != null && !url.isEmpty()) {
                    lastSiteUrl = url;
                }
            }
        });

        if (!isController) {
            wv.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(
                        String url,
                        String userAgent,
                        String contentDisposition,
                        String mimetype,
                        long contentLength
                ) {
                    boolean ok = url != null && !url.trim().isEmpty();
                    notifyControllerDl(ok);
                }
            });
        }

        return wv;
    }

    private void showControllerView() {
        mainHandler.post(() -> {
            controllerWebView.setVisibility(WebView.VISIBLE);
            siteWebView.setVisibility(WebView.GONE);
        });
    }

    private void showSiteView() {
        mainHandler.post(() -> {
            siteWebView.setVisibility(WebView.VISIBLE);
            controllerWebView.setVisibility(WebView.GONE);
        });
    }

    private void notifyControllerBot(String status, String detail) {
        String safeStatus = jsQuote(status == null ? "" : status);
        String safeDetail = jsQuote(detail == null ? "" : detail);
        mainHandler.post(() -> controllerWebView.evaluateJavascript(
                "if (window.onBot) window.onBot(" + safeStatus + ", " + safeDetail + ");",
                null
        ));
    }

    private void notifyControllerDl(boolean ok) {
        mainHandler.post(() -> controllerWebView.evaluateJavascript(
                "if (window.onDl) window.onDl(" + ok + ");",
                null
        ));
    }

    public void notifyControllerFrame() {
        mainHandler.post(() -> controllerWebView.evaluateJavascript(
                "if (window.onFrame) window.onFrame();",
                null
        ));
    }

    private String loadAssetText(String fileName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }

    private String buildInjectedScript(String prompt, String orientation, String frameB64, boolean isExtend) {
        String template = loadAssetText("site_inject.js");
        if (template.isEmpty()) {
            return "";
        }
        return template
                .replace("__P__", jsQuote(prompt))
                .replace("__O__", jsQuote(orientation))
                .replace("__F__", jsQuote(frameB64))
                .replace("__EXT__", isExtend ? "true" : "false");
    }

    private static String jsQuote(String s) {
        if (s == null) return "\"\"";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: out.append(c); break;
            }
        }
        out.append("\"");
        return out.toString();
    }

    private void createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "Movie Brain Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                );
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void playBeep(int count) {
        mainHandler.post(() -> {
            try {
                ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
                int times = Math.max(1, Math.min(count, 6));
                for (int i = 0; i < times; i++) {
                    mainHandler.postDelayed(() -> {
                        try {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 160);
                        } catch (Exception ignored) {
                        }
                    }, i * 240L);
                }
                mainHandler.postDelayed(() -> {
                    try {
                        tg.release();
                    } catch (Exception ignored) {
                    }
                }, times * 260L + 200L);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileCb != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileCb.onReceiveValue(results);
                fileCb = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (siteWebView != null && siteWebView.getVisibility() == WebView.VISIBLE) {
            if (siteWebView.canGoBack()) {
                siteWebView.goBack();
            } else {
                showControllerView();
            }
            return;
        }
        if (controllerWebView != null && controllerWebView.canGoBack()) {
            controllerWebView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private class ControllerBridge {

        @JavascriptInterface
        public boolean isAutomationActive() {
            return automationActive;
        }

        @JavascriptInterface
        public void startAutomation(String statusText) {
            automationActive = true;
            mainHandler.post(() -> {
                Intent svc = new Intent(MainActivity.this, AutomationService.class);
                svc.putExtra("status", statusText == null ? "Movie Brain automation active" : statusText);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            });
        }

        @JavascriptInterface
        public void updateAutomation(String statusText) {
            if (!automationActive) return;
            mainHandler.post(() -> {
                Intent svc = new Intent(MainActivity.this, AutomationService.class);
                svc.putExtra("status", statusText == null ? "Running..." : statusText);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            });
        }

        @JavascriptInterface
        public void stopAutomation() {
            automationActive = false;
            mainHandler.post(() -> {
                stopService(new Intent(MainActivity.this, AutomationService.class));
            });
        }

        @JavascriptInterface
        public void keepAwake(boolean enable) {
            mainHandler.post(() -> {
                if (enable) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void toSite() {
            showSiteView();
        }

        @JavascriptInterface
        public String generate(String prompt, String orientation, boolean isExtend) {
            showSiteView();
            String script = buildInjectedScript(prompt, orientation, "", isExtend);
            if (script.isEmpty()) {
                return "missing_inject_script";
            }
            mainHandler.post(() -> siteWebView.evaluateJavascript(script, null));
            return "injected";
        }

        @JavascriptInterface
        public String generateReload(String url, String prompt, String orientation, boolean isExtend) {
            showSiteView();
            String targetUrl = (url == null || url.trim().isEmpty()) ? lastSiteUrl : url.trim();
            lastSiteUrl = targetUrl;
            mainHandler.post(() -> {
                siteWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String finishedUrl) {
                        super.onPageFinished(view, finishedUrl);
                        view.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onPageFinished(WebView v, String u) {
                                super.onPageFinished(v, u);
                                if (u != null && !u.isEmpty()) lastSiteUrl = u;
                            }
                        });
                        String script = buildInjectedScript(prompt, orientation, "", isExtend);
                        if (!script.isEmpty()) {
                            v.evaluateJavascript(script, null);
                        }
                    }
                });
                siteWebView.loadUrl(targetUrl);
            });
            return "reloading";
        }

        @JavascriptInterface
        public void ring(int count, int durationMs) {
            playBeep(count);
        }

        @JavascriptInterface
        public void notifyAlert(String title, String message) {
            mainHandler.post(() -> {
                if (isFinishing()) return;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title == null ? "Alert" : title)
                        .setMessage(message == null ? "" : message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    private class SiteBotBridge {

        @JavascriptInterface
        public void report(String status, String detail) {
            notifyControllerBot(status, detail);
        }

        @JavascriptInterface
        public void frameReady() {
            notifyControllerFrame();
        }
    }
    }
