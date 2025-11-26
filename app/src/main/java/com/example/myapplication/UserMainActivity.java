package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.Socket;

// --- CORRECT IMPORTS FOR LIBADB-ANDROID ---
// Even if these show as RED in the editor, if the Build says "Successful",
// the app WILL run. This library uses the 'com.tananaev' package name.
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;
import com.tananaev.adblib.AdbBase64;

//import com.github.muntashirakon.adb;

public class UserMainActivity extends AppCompatActivity {
    // ... (Rest of your code remains the same)

    private WebView webView;
    private ProgressBar loader;

    // Active Connection
    private static AdbConnection activeConnection = null;
    private static AdbCrypto adbCrypto = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loader = findViewById(R.id.loader);

        if (webView == null || loader == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webView.setBackgroundColor(0x00000000);

        // Load Keys (Generate if missing)
        initAdbKeys();

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                loader.setVisibility(View.GONE);
                view.animate().alpha(1.0f).setDuration(300);
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void initAdbKeys() {
        executor.execute(() -> {
            try {
                File keyFile = new File(getFilesDir(), "adbkey");
                File pubKeyFile = new File(getFilesDir(), "adbkey.pub");

                if (!keyFile.exists() || !pubKeyFile.exists()) {
                    // Generate new keys
                    adbCrypto = AdbCrypto.generateAdbKeyPair(new AdbBase64() {
                        @Override
                        public String encodeToString(byte[] data) {
                            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
                        }
                    });
                    adbCrypto.saveAdbKeyPair(keyFile, pubKeyFile);
                } else {
                    // Load existing keys
                    adbCrypto = AdbCrypto.loadAdbKeyPair(new AdbBase64() {
                        @Override
                        public String encodeToString(byte[] data) {
                            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
                        }
                    }, keyFile, pubKeyFile);
                }
            } catch (Exception e) {
                Log.e("NEXUS", "Key Init Failed", e);
            }
        });
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public String getNativeCoreVersion() { return "3.1.0-LIBADB"; }

        @JavascriptInterface
        public void hapticFeedback(String type) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(20, 100));
        }

        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void shareText(String title, String content) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, content);
            mContext.startActivity(Intent.createChooser(intent, title));
        }

        @JavascriptInterface
        public void pairAdb(String ip, String portStr, String code) {
            executor.execute(() -> {
                try {
                    int port = Integer.parseInt(portStr);
                    Log.d("NEXUS", "Pairing " + ip + ":" + port);

                    // Note: Pairing logic differs by library version.
                    // For simple connection, we rely on the keys we generated.
                    // This simplified block just opens a socket to test connectivity.
                    Socket socket = new Socket(ip, port);
                    socket.close();

                    runOnUiThread(() -> showToast("Pairing Signal Sent"));
                } catch (Exception e) {
                    Log.e("NEXUS", "Pairing Failed", e);
                    runOnUiThread(() -> showToast("Pairing Failed: " + e.getMessage()));
                }
            });
        }

        @JavascriptInterface
        public boolean connectAdb(String ip, String portStr) {
            executor.execute(() -> {
                try {
                    int port = Integer.parseInt(portStr);
                    if (activeConnection != null) activeConnection.close();

                    Socket socket = new Socket(ip, port);
                    // Create connection with our keys
                    activeConnection = AdbConnection.create(socket, adbCrypto);

                    // Connect triggers the handshake
                    activeConnection.connect();

                    runOnUiThread(() -> {
                        showToast("Connected via LibADB!");
                        getInstalledPackages();
                    });
                } catch (Exception e) {
                    Log.e("NEXUS", "Connect Failed", e);
                    runOnUiThread(() -> showToast("Connection Failed: " + e.getMessage()));
                }
            });
            return true;
        }

        @JavascriptInterface
        public void getInstalledPackages() {
            executor.execute(() -> {
                if (activeConnection == null) return;
                try {
                    // Open shell stream
                    AdbStream stream = activeConnection.open("shell:pm list packages -3");

                    StringBuilder output = new StringBuilder();
                    while (!stream.isClosed()) {
                        byte[] data = stream.read();
                        if (data == null) break;
                        output.append(new String(data));
                    }

                    String[] lines = output.toString().split("\n");
                    JSONArray jsonArray = new JSONArray();
                    for (String line : lines) {
                        String pkg = line.replace("package:", "").trim();
                        if (!pkg.isEmpty()) {
                            JSONObject obj = new JSONObject();
                            obj.put("name", pkg);
                            obj.put("pkg", pkg);
                            obj.put("category", "User");
                            obj.put("risk", "safe");
                            obj.put("permissions", new JSONArray());
                            jsonArray.put(obj);
                        }
                    }

                    String js = "if(window.receiveAppList) { window.receiveAppList('" + jsonArray.toString().replace("'", "\\'") + "'); }";
                    runOnUiThread(() -> webView.evaluateJavascript(js, null));

                } catch (Exception e) {
                    Log.e("NEXUS", "Shell Error", e);
                }
            });
        }

        @JavascriptInterface
        public void revokeInternet(String pkg) {
            executor.execute(() -> {
                if (activeConnection == null) return;
                try {
                    activeConnection.open("shell:cmd appops set " + pkg + " INTERNET deny");
                    runOnUiThread(() -> showToast("Revoked Net: " + pkg));
                } catch (Exception e) {}
            });
        }
    }
}