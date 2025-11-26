package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class UserMainActivity extends AppCompatActivity {

    // --- FIX: REMOVED NATIVE LIBRARY LOAD BLOCK ---
    // This was the cause of the crash. We don't need C++ for a WebView app.
    // static { System.loadLibrary("myapplication"); }
    // public native String getNativeVersionFromJNI();

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Full Screen / Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // Make Status bar transparent so React background shows through
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        // Ensure this ID matches your activity_main.xml (it does: @+id/webview)
        webView = findViewById(R.id.webview);

        if (webView == null) {
            Log.e("UserMainActivity", "WebView is NULL! Check activity_main.xml IDs.");
            return; // Prevent crash if ID is wrong
        }

        // 2. MODIFIED INSETS HANDLING
        // We only apply BOTTOM padding (for navigation bar).
        // We DO NOT apply TOP padding, because your React Code has "pt-10" hardcoded.
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Left, Top=0 (Let React handle it), Right, Bottom (Keep nav bar clear)
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        // Transparent background prevents white flash
        webView.setBackgroundColor(0x00000000);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Fade in WebView once loaded to avoid white flash
                view.animate().alpha(1.0f).setDuration(300);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public String getNativeCoreVersion() {
            // FIX: Return a static string instead of calling missing JNI method
            return "1.0.0";
        }

        @JavascriptInterface
        public void hapticFeedback(String type) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long duration = 10;
                int amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
                if (type.equals("heavy")) { duration = 50; amplitude = 255; }
                else if (type.equals("success")) { duration = 30; amplitude = 100; }
                v.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            } else {
                v.vibrate(20);
            }
        }

        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void shareText(String title, String content) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, content);
            sendIntent.setType("text/plain");
            Intent shareIntent = Intent.createChooser(sendIntent, title);
            mContext.startActivity(shareIntent);
        }

        @JavascriptInterface
        public void getInstalledPackages() {
            new Thread(() -> {
                try {
                    PackageManager pm = getPackageManager();
                    List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                    JSONArray jsonArray = new JSONArray();

                    for (ApplicationInfo packageInfo : packages) {
                        if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || (packageInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                            JSONObject obj = new JSONObject();
                            obj.put("name", pm.getApplicationLabel(packageInfo));
                            obj.put("pkg", packageInfo.packageName);
                            obj.put("category", "User Installed");
                            obj.put("risk", "safe"); // Defaulting to safe for demo

                            // Mock permissions for demo visualization
                            JSONArray perms = new JSONArray();
                            perms.put("INTERNET");
                            obj.put("permissions", perms);

                            obj.put("dataUsage", "12 MB");
                            obj.put("lastUsed", "Today");

                            jsonArray.put(obj);
                        }
                    }

                    // --- FIX 2: SEND DATA BACK TO REACT ---
                    // We must run this on the Main UI Thread
                    String jsonString = jsonArray.toString();
                    runOnUiThread(() -> {
                        // Calls: window.receiveAppList(...) in React
                        String js = "if(window.receiveAppList) { window.receiveAppList('" + jsonString.replace("'", "\\'") + "'); }";
                        webView.evaluateJavascript(js, null);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        @JavascriptInterface
        public void uninstallPackage(String packageName) {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void revokeInternet(String packageName) {
            Toast.makeText(mContext, "Revoking Net for: " + packageName, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void pairAdb(String ip, String port, String code) {
            Log.d("NEXUS_ADB", "Pairing attempt: " + ip + ":" + port + " key:" + code);
        }

        // --- FIX 1: CORRECT METHOD NAME ---
        @JavascriptInterface
        public boolean connectAdb(String ip, String port) { // Was connectAd
            Log.d("NEXUS_ADB", "Connecting: " + ip + ":" + port);
            return true;
        }

        @JavascriptInterface
        public void startVpn() {
            Toast.makeText(mContext, "Initializing VpnService...", Toast.LENGTH_SHORT).show();
        }
    }
}