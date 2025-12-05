package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.interfaces.AdbPairingListener;
import com.example.myapplication.interfaces.ConsolidatedWebAppInterface;
import com.example.myapplication.managers.AdbPairingManager;
import com.example.myapplication.managers.AdbSingleton;
import com.example.myapplication.managers.MyAdbManager;

import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class UserMainActivity extends AppCompatActivity implements AdbPairingListener {

    private WebView webView;
    private ProgressBar loader;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConsolidatedWebAppInterface mInterface;
    private AdbPairingManager pairingManager;

    // Constants shared with Interfaces
    public static final int VPN_REQUEST_CODE = 0x0F;
    private static final int NOTIFICATION_REQUEST_CODE = 0x10;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- CRITICAL FIX: Fix BouncyCastle Provider for Android P+ ---
        // Android's internal "BC" provider is deprecated/restricted. We must remove it
        // and register our own full BouncyCastle implementation to generate ADB keys successfully.
        try {
            Security.removeProvider("BC");
            Security.addProvider(new BouncyCastleProvider());
        } catch (Exception e) {
            Log.e("NEXUS", "Error setting up Security Provider", e);
        }
        // -------------------------------------------------------------

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loader = findViewById(R.id.loader);

        setupWebViewUI();

        // 1. Init ADB Singleton with Callback
        // We pass 'this' context, and handle the completion in the callback
        AdbSingleton.getInstance().init(getApplicationContext(), new AdbSingleton.AdbInitListener() {
            @Override
            public void onInitComplete() {
                // This might run on a background thread, so we post to Main Thread for any UI updates
                mainHandler.post(() -> {
                    Log.d("NEXUS", "ADB Initialization Sequence Complete");
                    // Optional: If you needed to enable buttons or notify the webview immediately
                    if (webView != null) {
                        webView.evaluateJavascript("console.log('ADB Native Init Complete');", null);
                    }
                });
            }
        });

        // 2. Init Managers
        // Note: Ensure pairingManager doesn't require the internal 'adbManager' immediately in its constructor
        pairingManager = new AdbPairingManager(this, this);

        // 3. Init Web Interface (Pass dependencies)
        mInterface = new ConsolidatedWebAppInterface(this, executor, webView, pairingManager);
        webView.addJavascriptInterface(mInterface, "AndroidNative");

        // 4. Load Web Page
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.postDelayed(() -> {
                    if (loader != null) loader.setVisibility(View.GONE);
                    view.setAlpha(1.0f);

                    // Check initial connection state
                    // Updated to use .getAdbManager() to match the Singleton definition
                    MyAdbManager manager = AdbSingleton.getInstance().getAdbManager();

                    if (manager != null && manager.isConnected()) {
                        webView.evaluateJavascript("window.adbStatus('Connected');", null);
                        mInterface.getInstalledPackages();
                    }
                }, 200);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("NEXUS_WEB", cm.message());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/web/index.html");
        checkPermissions();
    }

    private void setupWebViewUI() {
        webView.setBackgroundColor(0xFF020617);
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Delegate VPN result to the interface logic
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            mInterface.shield.startShieldServiceInternal();
        }
    }

    @Override protected void onResume() { super.onResume(); if (pairingManager != null) pairingManager.startMdnsDiscovery(); }
    @Override protected void onPause() { super.onPause(); if (pairingManager != null) pairingManager.stopMdnsDiscovery(); }
    @Override protected void onDestroy() { super.onDestroy(); if (pairingManager != null) pairingManager.stopMdnsDiscovery(); }

    // --- PAIRING LISTENER IMPLEMENTATION ---

    @Override
    public void onPairingServiceFound(String ip, int port) {
        // Run on UI Thread to ensure WebView thread safety
        runOnUiThread(() -> {
            webView.evaluateJavascript(String.format("if(window.onPairingServiceFound) window.onPairingServiceFound('%s', '%d');", ip, port), null);
        });
    }

    @Override
    public void onConnectServiceFound(String ip, int port) {
        // Run on UI Thread to ensure WebView thread safety
        runOnUiThread(() -> {
            webView.evaluateJavascript(String.format("if(window.onConnectServiceFound) window.onConnectServiceFound('%s', '%d');", ip, port), null);
        });
    }

    @Override
    public void onPairingResult(boolean success, String message) {
        // Run on UI Thread to prevent "Toast already killed" and threading errors
        runOnUiThread(() -> {
            if (mInterface != null && mInterface.common != null) {
                mInterface.common.showToast(message);
            }
        });
    }

    @Override
    public void onConnectionResult(boolean success, String message) {
        // Run on UI Thread to prevent "Toast already killed" and threading errors
        runOnUiThread(() -> {
            if (mInterface != null && mInterface.common != null) {
                mInterface.common.showToast(message);
            }

            if (success) {
                webView.evaluateJavascript("window.adbStatus('Connected');", null);
            } else if (message != null && message.contains("Connection Failed")) {
                webView.evaluateJavascript("window.adbStatus('Connection Failed');", null);
            }
        });
    }
}