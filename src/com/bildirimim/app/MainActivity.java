package com.bildirimim.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

/** HTML arayuzu WebView icinde gosterir; "Android" koprusu ile native bildirim katmanina baglar. */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(0xFF2E1B12);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        NotifReceiver.channels(this);
        if (Build.VERSION.SDK_INT >= 33 && !canNotify())
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotifReceiver.sync(this);
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        NotifReceiver.sync(this);
        refresh();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack ? window.onBack() : false", new android.webkit.ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (!"true".equals(v)) finish();
            }
        });
    }

    private void refresh() {
        if (web != null) web.evaluateJavascript("window.render && window.render()", null);
    }

    private boolean canNotify() {
        return ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled();
    }

    final class Bridge {
        @JavascriptInterface
        public String list() { return Store.all(MainActivity.this).toString(); }

        @JavascriptInterface
        public boolean save(String text, double start, double end, boolean sound) {
            try {
                JSONObject o = Store.add(MainActivity.this, text, (long) start, (long) end, sound);
                NotifReceiver.schedule(MainActivity.this, o);
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void remove(int id) { NotifReceiver.cancel(MainActivity.this, id); }

        @JavascriptInterface
        public boolean allowed() { return canNotify(); }

        @JavascriptInterface
        public void askPermission() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (Build.VERSION.SDK_INT >= 33
                            && shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                    } else {
                        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    }
                }
            });
        }
    }
}
