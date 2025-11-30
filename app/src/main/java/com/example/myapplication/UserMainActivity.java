package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import org.json.JSONArray;
import org.json.JSONObject;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

public class UserMainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar loader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static MyAdbManager adbManager;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener pairingListener;
    private NsdManager.DiscoveryListener connectListener;

    // Cache for Auto-Discovery
    private String autoPairIp = null;
    private int autoPairPort = -1;
    private String autoConnectIp = null;
    private int autoConnectPort = -1;

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
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webView.setBackgroundColor(0x00000000);

        initAdbManager();

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

    private void initAdbManager() {
        executor.execute(() -> {
            try {
                Security.removeProvider("BC");
                Security.addProvider(new BouncyCastleProvider());
                adbManager = new MyAdbManager();
            } catch (Exception e) {
                Log.e("NEXUS", "Failed to init ADB Manager", e);
            }
        });
    }

    // --- ADB MANAGER CLASS ---
    public static class MyAdbManager extends AbsAdbConnectionManager {
        private PrivateKey mPrivateKey;
        private Certificate mCertificate;

        public MyAdbManager() throws Exception {
            setApi(Build.VERSION.SDK_INT);
            generateKeys();
        }

        private void generateKeys() throws Exception {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();
            mPrivateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            X500Name issuer = new X500Name("CN=NexusADB");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + 1000L * 3600 * 24 * 365);
            X500Name subject = new X500Name("CN=NexusADB");

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, publicKey
            );

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider("BC")
                    .build(mPrivateKey);

            X509CertificateHolder certHolder = certBuilder.build(signer);

            mCertificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);
        }

        @NonNull
        @Override
        protected PrivateKey getPrivateKey() { return mPrivateKey; }

        @NonNull
        @Override
        protected Certificate getCertificate() { return mCertificate; }

        @NonNull
        @Override
        protected String getDeviceName() { return "NexusUAD"; }

        public String runShellCommand(String cmd) throws Exception {
            try (AdbStream stream = openStream("shell:" + cmd)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                while (!stream.isClosed()) {
                    try {
                        int bytesRead = stream.read(buffer, 0, buffer.length);
                        if (bytesRead < 0) break;
                        outputStream.write(buffer, 0, bytesRead);
                    } catch (Exception e) { break; }
                }
                return outputStream.toString("UTF-8");
            }
        }
    }

    // --- JAVASCRIPT INTERFACE ---
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public String getNativeCoreVersion() { return "4.3.0-RETRIEVE"; }

        @JavascriptInterface
        public void hapticFeedback(String type) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                int duration = type.equals("heavy") ? 50 : 10;
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }

        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void startMdnsDiscovery() {
            nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

            pairingListener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String t) { Log.d("NEXUS", "Pairing Scan Started"); }
                @Override public void onServiceFound(NsdServiceInfo s) {
                    if (s.getServiceType().contains("adb-tls-pairing")) {
                        nsdManager.resolveService(s, new NsdManager.ResolveListener() {
                            @Override public void onResolveFailed(NsdServiceInfo s, int e) {}
                            @Override public void onServiceResolved(NsdServiceInfo s) {
                                String host = s.getHost().getHostAddress();
                                int port = s.getPort();
                                autoPairIp = host;
                                autoPairPort = port;
                                sendToJs("onPairingServiceFound", host, port);
                            }
                        });
                    }
                }
                @Override public void onServiceLost(NsdServiceInfo s) {}
                @Override public void onDiscoveryStopped(String t) {}
                @Override public void onStartDiscoveryFailed(String t, int e) {}
                @Override public void onStopDiscoveryFailed(String t, int e) {}
            };

            connectListener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String t) { Log.d("NEXUS", "Connect Scan Started"); }
                @Override public void onServiceFound(NsdServiceInfo s) {
                    if (s.getServiceType().contains("adb-tls-connect")) {
                        nsdManager.resolveService(s, new NsdManager.ResolveListener() {
                            @Override public void onResolveFailed(NsdServiceInfo s, int e) {}
                            @Override public void onServiceResolved(NsdServiceInfo s) {
                                String host = s.getHost().getHostAddress();
                                int port = s.getPort();
                                autoConnectIp = host;
                                autoConnectPort = port;
                                sendToJs("onConnectServiceFound", host, port);
                            }
                        });
                    }
                }
                @Override public void onServiceLost(NsdServiceInfo s) {}
                @Override public void onDiscoveryStopped(String t) {}
                @Override public void onStartDiscoveryFailed(String t, int e) {}
                @Override public void onStopDiscoveryFailed(String t, int e) {}
            };

            try {
                nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, pairingListener);
                nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, connectListener);
            } catch (Exception e) {
                Log.e("NEXUS", "MDNS Error", e);
            }
        }

        @JavascriptInterface
        public void stopMdnsDiscovery() {
            try {
                if (nsdManager != null) {
                    if (pairingListener != null) nsdManager.stopServiceDiscovery(pairingListener);
                    if (connectListener != null) nsdManager.stopServiceDiscovery(connectListener);
                }
            } catch (Exception e) { }
        }

        // --- RETRIEVE FUNCTION (Clipboard & Cache) ---
        @JavascriptInterface
        public void retrieveConnectionInfo() {
            // 1. Check Cache
            if (autoPairIp != null && autoPairPort != -1) {
                sendToJs("onPairingServiceFound", autoPairIp, autoPairPort);
            }
            if (autoConnectIp != null && autoConnectPort != -1) {
                sendToJs("onConnectServiceFound", autoConnectIp, autoConnectPort);
            }

            // 2. Check Clipboard
            runOnUiThread(() -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                        if (text != null) {
                            String content = text.toString();
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{4,5})").matcher(content);
                            if (m.find()) {
                                String ip = m.group(1);
                                int port = Integer.parseInt(m.group(2));
                                String js = String.format("window.onPairingServiceFound('%s', '%d');", ip, port);
                                webView.evaluateJavascript(js, null);
                                showToast("Found in Clipboard: " + ip + ":" + port);
                            } else {
                                showToast("No IP:Port found in clipboard");
                            }
                        }
                    } else {
                        if (autoPairIp == null) showToast("Scan running... No data yet.");
                    }
                } catch (Exception e) {
                    Log.e("NEXUS", "Clipboard Error", e);
                }
            });
        }

        private void sendToJs(String functionName, String ip, int port) {
            runOnUiThread(() -> {
                String js = String.format("window.%s('%s', '%d');", functionName, ip, port);
                webView.evaluateJavascript(js, null);
            });
        }

        @JavascriptInterface
        public void pairAdb(String ip, String portStr, String code) {
            executor.execute(() -> {
                if (adbManager == null) return;
                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : autoPairIp;
                    int targetPort = -1;
                    if (portStr != null && !portStr.isEmpty()) targetPort = Integer.parseInt(portStr);
                    else if (autoPairPort != -1) targetPort = autoPairPort;

                    if (targetIp == null || targetPort == -1) {
                        runOnUiThread(() -> showToast("Wait for Scan or Paste Info"));
                        return;
                    }

                    boolean success = adbManager.pair(targetIp, targetPort, code);
                    runOnUiThread(() -> showToast(success ? "Pairing Success" : "Pairing Failed"));
                } catch (Exception e) {
                    runOnUiThread(() -> showToast("Pairing Error: " + e.getMessage()));
                }
            });
        }

        @JavascriptInterface
        public boolean connectAdb(String ip, String portStr) {
            executor.execute(() -> {
                if (adbManager == null) return;
                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : autoConnectIp;
                    int targetPort = -1;
                    if (portStr != null && !portStr.isEmpty()) targetPort = Integer.parseInt(portStr);
                    else if (autoConnectPort != -1) targetPort = autoConnectPort;

                    if (targetIp == null && autoPairIp != null) targetIp = autoPairIp;

                    if (targetIp == null || targetPort == -1) {
                        runOnUiThread(() -> showToast("Connection info missing"));
                        return;
                    }

                    boolean connected = adbManager.connect(targetIp, targetPort);
                    if (connected) {
                        runOnUiThread(() -> {
                            showToast("Connected to Shell");
                            fetchRealPackageList();
                        });
                    } else {
                        runOnUiThread(() -> showToast("Connection Refused"));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> showToast("Connect Error: " + e.getMessage()));
                }
            });
            return true;
        }

        private void fetchRealPackageList() {
            executor.execute(() -> {
                try {
                    String rawList = adbManager.runShellCommand("pm list packages -f -u");
                    String[] lines = rawList.split("\n");
                    JSONArray jsonArray = new JSONArray();
                    PackageManager pm = getPackageManager();

                    for (String line : lines) {
                        if (!line.contains("=")) continue;
                        String pkgName = line.substring(line.lastIndexOf('=') + 1).trim();
                        JSONObject obj = new JSONObject();
                        obj.put("pkg", pkgName);
                        try {
                            PackageInfo pInfo = pm.getPackageInfo(pkgName, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                            boolean isSystem = (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            boolean isEnabled = pInfo.applicationInfo.enabled;
                            obj.put("name", pInfo.applicationInfo.loadLabel(pm).toString());
                            obj.put("type", isSystem ? "System" : "User");
                            obj.put("status", isEnabled ? "Enabled" : "Disabled");
                        } catch (PackageManager.NameNotFoundException e) {
                            obj.put("name", pkgName);
                            obj.put("type", "System");
                            obj.put("status", "Unknown");
                        }
                        jsonArray.put(obj);
                    }
                    final String jsonStr = jsonArray.toString();
                    new Handler(Looper.getMainLooper()).post(() -> webView.evaluateJavascript("window.receiveAppList(" + jsonStr + ");", null));
                } catch (Exception e) { Log.e("NEXUS", "Fetch Error", e); }
            });
        }

        @JavascriptInterface
        public void executeCommand(String cmd, String pkgName) {
            executor.execute(() -> {
                if (adbManager == null) return;
                try {
                    String shellCmd = "";
                    switch (cmd) {
                        case "uninstall": shellCmd = "pm uninstall --user 0 " + pkgName; break;
                        case "disable": shellCmd = "pm disable-user --user 0 " + pkgName; break;
                        case "enable": shellCmd = "pm enable " + pkgName; break;
                        case "restore": shellCmd = "cmd package install-existing " + pkgName; break;
                    }
                    if (!shellCmd.isEmpty()) {
                        String result = adbManager.runShellCommand(shellCmd);
                        runOnUiThread(() -> { showToast("Result: " + result); fetchRealPackageList(); });
                    }
                } catch (Exception e) { runOnUiThread(() -> showToast("Cmd Error: " + e.getMessage())); }
            });
        }
    }
}