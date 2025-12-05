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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loader = findViewById(R.id.loader);

        setupWebViewUI();

        // 1. Init ADB Singleton
        AdbSingleton.getInstance().init(getApplicationContext());

        // 2. Init Managers
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
                    MyAdbManager manager = AdbSingleton.getInstance().getManager();
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
        webView.evaluateJavascript(String.format("if(window.onPairingServiceFound) window.onPairingServiceFound('%s', '%d');", ip, port), null);
    }

    @Override
    public void onConnectServiceFound(String ip, int port) {
        webView.evaluateJavascript(String.format("if(window.onConnectServiceFound) window.onConnectServiceFound('%s', '%d');", ip, port), null);
    }

    @Override
    public void onPairingResult(boolean success, String message) {
        mInterface.common.showToast(message);
    }

    @Override
    public void onConnectionResult(boolean success, String message) {
        mInterface.common.showToast(message);
        if (success) {
            webView.evaluateJavascript("window.adbStatus('Connected');", null);
        } else if (message.contains("Connection Failed")) {
            webView.evaluateJavascript("window.adbStatus('Connection Failed');", null);
        }
    }
}