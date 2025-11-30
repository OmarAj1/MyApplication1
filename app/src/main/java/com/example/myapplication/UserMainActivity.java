package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.interfaces.CommonInterface;
import com.example.myapplication.interfaces.ShieldInterface;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

public class UserMainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar loader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConsolidatedWebAppInterface mInterface;
    private NsdHelper nsdHelper;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loader = findViewById(R.id.loader);

        if (webView == null || loader == null) {
            Log.e("NEXUS", "Critical: WebView or Loader not found in layout.");
            return;
        }

        // 1. Setup WebView styling
        webView.setBackgroundColor(0x00000000);
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // 2. Configure Settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        // 3. Initialize Core Logic
        // Start ADB Manager initialization immediately in background
        AdbSingleton.getInstance().init(this);

        nsdHelper = new NsdHelper(this);
        // We create the interface here, managing dependencies manually
        mInterface = new ConsolidatedWebAppInterface(this, executor, webView, nsdHelper);
        webView.addJavascriptInterface(mInterface, "AndroidNative");

        // 4. Setup Clients
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.postDelayed(() -> {
                    if (loader != null) loader.setVisibility(View.GONE);
                    view.setAlpha(0f);
                    view.animate().alpha(1.0f).setDuration(500).start();
                }, 200);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("NEXUS_WEB", cm.message() + " -- From line " + cm.lineNumber());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nsdHelper != null) nsdHelper.stopMdnsDiscoveryInternal();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
        if (nsdHelper != null) nsdHelper.stopMdnsDiscoveryInternal();
    }

    // --- SINGLETON TO HOLD ADB STATE ---
    public static class AdbSingleton {
        private static AdbSingleton instance;
        private MyAdbManager adbManager;
        private final AtomicBoolean isInitializing = new AtomicBoolean(false);

        public static synchronized AdbSingleton getInstance() {
            if (instance == null) instance = new AdbSingleton();
            return instance;
        }

        public void init(Context context) {
            if (adbManager != null || isInitializing.get()) return;
            isInitializing.set(true);
            new Thread(() -> {
                try {
                    Security.removeProvider("BC");
                    Security.addProvider(new BouncyCastleProvider());
                    adbManager = new MyAdbManager();
                    Log.d("NEXUS", "ADB Core Initialized Successfully");
                } catch (Exception e) {
                    Log.e("NEXUS", "Failed to init ADB Manager", e);
                } finally {
                    isInitializing.set(false);
                }
            }).start();
        }

        public MyAdbManager getManager() { return adbManager; }
    }

    // --- ADB MANAGER IMPLEMENTATION ---
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
            BigInteger serial = BigInteger.valueOf(Math.abs(System.currentTimeMillis()));
            Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
            Date notAfter = new Date(System.currentTimeMillis() + 1000L * 3600 * 24 * 365 * 10);
            X500Name subject = new X500Name("CN=NexusADB");

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, publicKey
            );

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider(new BouncyCastleProvider())
                    .build(mPrivateKey);

            X509CertificateHolder certHolder = certBuilder.build(signer);

            mCertificate = new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(certHolder);
        }

        @NonNull @Override protected PrivateKey getPrivateKey() { return mPrivateKey; }
        @NonNull @Override protected Certificate getCertificate() { return mCertificate; }
        @NonNull @Override protected String getDeviceName() { return "NexusUAD"; }

        public String runShellCommand(String cmd) throws Exception {
            if (!isConnected()) throw new IllegalStateException("ADB Not Connected");
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

    // --- NSD HELPER (Fixed: Crash Prevention & Compilation) ---
    private class NsdHelper {
        private NsdManager nsdManager;
        private NsdManager.DiscoveryListener pairingListener, connectListener;
        private String autoPairIp, autoConnectIp;
        private int autoPairPort = -1, autoConnectPort = -1;
        private final AppCompatActivity activity;

        // Prevents "Listener already registered" crash
        private boolean isDiscoveryActive = false;

        NsdHelper(AppCompatActivity activity) {
            this.activity = activity;
            this.nsdManager = (NsdManager) activity.getSystemService(Context.NSD_SERVICE);
        }

        private void sendToJs(String func, String ip, int port) {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            activity.runOnUiThread(() -> webView.evaluateJavascript(
                    String.format("if(window.%s) window.%s('%s', '%d');", func, func, ip, port), null));
        }

        public void startMdnsDiscoveryInternal() {
            if (nsdManager == null) return;

            if (isDiscoveryActive) {
                Log.d("NEXUS", "Discovery already active, skipping start.");
                return;
            }

            try {
                if (pairingListener == null) {
                    pairingListener = new NsdManager.DiscoveryListener() {
                        @Override public void onDiscoveryStarted(String t) { Log.d("NEXUS", "Pairing Discovery Started"); }
                        @Override public void onServiceFound(NsdServiceInfo s) {
                            if (s.getServiceType().contains("adb-tls-pairing")) {
                                nsdManager.resolveService(s, new NsdManager.ResolveListener() {
                                    @Override public void onResolveFailed(NsdServiceInfo s, int e) {}
                                    @Override public void onServiceResolved(NsdServiceInfo s) {
                                        autoPairIp = s.getHost().getHostAddress();
                                        autoPairPort = s.getPort();
                                        sendToJs("onPairingServiceFound", autoPairIp, autoPairPort);
                                    }
                                });
                            }
                        }
                        @Override public void onServiceLost(NsdServiceInfo s) {}
                        @Override public void onDiscoveryStopped(String t) {}
                        @Override public void onStartDiscoveryFailed(String t, int e) {
                            Log.e("NEXUS", "Pairing Start Failed: " + e);
                            nsdManager.stopServiceDiscovery(this);
                        }
                        @Override public void onStopDiscoveryFailed(String t, int e) {
                            nsdManager.stopServiceDiscovery(this);
                        }
                    };
                }

                if (connectListener == null) {
                    connectListener = new NsdManager.DiscoveryListener() {
                        @Override public void onDiscoveryStarted(String t) {}
                        @Override public void onServiceFound(NsdServiceInfo s) {
                            if (s.getServiceType().contains("adb-tls-connect")) {
                                nsdManager.resolveService(s, new NsdManager.ResolveListener() {
                                    @Override public void onResolveFailed(NsdServiceInfo s, int e) {}
                                    @Override public void onServiceResolved(NsdServiceInfo s) {
                                        autoConnectIp = s.getHost().getHostAddress();
                                        autoConnectPort = s.getPort();
                                        sendToJs("onConnectServiceFound", autoConnectIp, autoConnectPort);
                                    }
                                });
                            }
                        }
                        @Override public void onServiceLost(NsdServiceInfo s) {}
                        @Override public void onDiscoveryStopped(String t) {}
                        @Override public void onStartDiscoveryFailed(String t, int e) { nsdManager.stopServiceDiscovery(this); }
                        @Override public void onStopDiscoveryFailed(String t, int e) { nsdManager.stopServiceDiscovery(this); }
                    };
                }

                nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, pairingListener);
                nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, connectListener);
                isDiscoveryActive = true;

            } catch (Exception e) {
                Log.e("NEXUS", "MDNS Start Error", e);
                isDiscoveryActive = false;
            }
        }

        public void stopMdnsDiscoveryInternal() {
            try {
                if (nsdManager != null && isDiscoveryActive) {
                    if (pairingListener != null) nsdManager.stopServiceDiscovery(pairingListener);
                    if (connectListener != null) nsdManager.stopServiceDiscovery(connectListener);
                }
            } catch (Exception e) {
                Log.e("NEXUS", "MDNS Stop Error", e);
            } finally {
                isDiscoveryActive = false;
            }
        }

        public void retrieveConnectionInfoInternal() {
            // 1. Send cached NSD info
            if (autoPairIp != null && autoPairPort != -1) sendToJs("onPairingServiceFound", autoPairIp, autoPairPort);
            if (autoConnectIp != null && autoConnectPort != -1) sendToJs("onConnectServiceFound", autoConnectIp, autoConnectPort);

            // 2. Check Clipboard for "IP:PORT" string
            activity.runOnUiThread(() -> {
                try {
                    ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = cb.getPrimaryClip().getItemAt(0).getText();
                        if (text != null) {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{4,5})").matcher(text);
                            if (m.find()) {
                                String ip = m.group(1);
                                int port = Integer.parseInt(m.group(2));
                                sendToJs("onPairingServiceFound", ip, port);

                                // FIX: Use Toast directly
                                Toast.makeText(activity, "Pasted from Clipboard: " + ip, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                } catch (Exception e) { Log.e("NEXUS", "Clipboard Error", e); }
            });
        }

        public String getAutoPairIp() { return autoPairIp; }
        public int getAutoPairPort() { return autoPairPort; }
        public String getAutoConnectIp() { return autoConnectIp; }
        public int getAutoConnectPort() { return autoConnectPort; }
    }

    // --- CONSOLIDATED INTERFACE ---
    public class ConsolidatedWebAppInterface {
        public final CommonInterface common;
        private final ShieldInterface shield;
        private final AppCompatActivity activity;
        private final ExecutorService executor;
        private final NsdHelper nsdHelper;
        private final WebView webView;

        ConsolidatedWebAppInterface(AppCompatActivity activity, ExecutorService executor, WebView webView, NsdHelper nsdHelper) {
            this.activity = activity;
            this.executor = executor;
            this.webView = webView;
            this.nsdHelper = nsdHelper;
            this.common = new CommonInterface(activity);
            this.shield = new ShieldInterface(activity, common);
        }

        @JavascriptInterface public String getNativeCoreVersion() { return common.getNativeCoreVersion(); }
        @JavascriptInterface public void hapticFeedback(String type) { common.hapticFeedback(type); }
        @JavascriptInterface public void showToast(String toast) { common.showToast(toast); }
        @JavascriptInterface public void shareText(String t, String c) { common.shareText(t, c); }

        @JavascriptInterface public void pairAdb(String ip, String p, String c) { pairAdbInternal(ip, p, c); }
        @JavascriptInterface public boolean connectAdb(String ip, String p) { connectAdbInternal(ip, p); return true; }
        @JavascriptInterface public void executeCommand(String a, String p) { executeCommandInternal(a, p); }
        @JavascriptInterface public void getInstalledPackages() { fetchRealPackageListInternal(); }
        @JavascriptInterface public void startMdnsDiscovery() { nsdHelper.startMdnsDiscoveryInternal(); }
        @JavascriptInterface public void stopMdnsDiscovery() { nsdHelper.stopMdnsDiscoveryInternal(); }
        @JavascriptInterface public void retrieveConnectionInfo() { nsdHelper.retrieveConnectionInfoInternal(); }
        @JavascriptInterface public void startVpn() { shield.startVpn(); }
        @JavascriptInterface public void stopVpn() { shield.stopVpn(); }

        private void pairAdbInternal(String ip, String portStr, String code) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null) {
                    common.showToast("Core Initializing... Please wait.");
                    return;
                }

                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : nsdHelper.getAutoPairIp();
                    int targetPort = -1;
                    if (portStr != null && !portStr.isEmpty()) {
                        try { targetPort = Integer.parseInt(portStr); } catch(NumberFormatException e){}
                    } else {
                        targetPort = nsdHelper.getAutoPairPort();
                    }

                    if (targetIp == null || targetPort == -1) {
                        common.showToast("Missing IP or Port");
                        return;
                    }

                    boolean success = manager.pair(targetIp, targetPort, code);
                    common.showToast(success ? "Pairing Success! Now Connect." : "Pairing Failed (Check Code)");
                } catch (Exception e) {
                    Log.e("NEXUS", "Pair Exception", e);
                    common.showToast("Pair Error: " + e.getMessage());
                }
            });
        }

        private void connectAdbInternal(String ip, String portStr) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null) return;

                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : nsdHelper.getAutoConnectIp();
                    if (targetIp == null) targetIp = nsdHelper.getAutoPairIp();

                    int targetPort = -1;
                    if (portStr != null && !portStr.isEmpty()) {
                        try { targetPort = Integer.parseInt(portStr); } catch(NumberFormatException e){}
                    } else {
                        targetPort = nsdHelper.getAutoConnectPort();
                    }

                    if (targetIp == null || targetPort == -1) {
                        common.showToast("Connection Info Missing");
                        return;
                    }

                    boolean connected = manager.connect(targetIp, targetPort);
                    if (connected) {
                        common.showToast("Connected to Shell");
                        activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connected');", null));
                        fetchRealPackageListInternal();
                    } else {
                        common.showToast("Connection Failed");
                    }
                } catch (Exception e) {
                    common.showToast("Connect Error: " + e.getMessage());
                }
            });
        }

        private void executeCommandInternal(String action, String pkg) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null || !manager.isConnected()) {
                    common.showToast("Not Connected");
                    return;
                }
                try {
                    String cmd = "";
                    if (action.equals("uninstall")) cmd = "pm uninstall --user 0 " + pkg;
                    else if (action.equals("disable")) cmd = "pm disable-user --user 0 " + pkg;
                    else if (action.equals("enable")) cmd = "pm enable " + pkg;
                    else if (action.equals("restore")) cmd = "cmd package install-existing " + pkg;

                    if (!cmd.isEmpty()) {
                        manager.runShellCommand(cmd);
                        common.showToast("Executed: " + action);
                        fetchRealPackageListInternal();
                    }
                } catch(Exception e) {
                    common.showToast("Cmd Failed: " + e.getMessage());
                }
            });
        }

        private void fetchRealPackageListInternal() {
            executor.execute(() -> {
                try {
                    MyAdbManager manager = AdbSingleton.getInstance().getManager();
                    if (manager == null) return;

                    String rawList = manager.runShellCommand("pm list packages -f -u");
                    if (rawList == null || rawList.isEmpty()) return;

                    String[] lines = rawList.split("\n");
                    JSONArray jsonArray = new JSONArray();
                    PackageManager pm = activity.getPackageManager();

                    for (String line : lines) {
                        if (!line.contains("=")) continue;
                        String pkgName = line.substring(line.lastIndexOf('=') + 1).trim();
                        if (pkgName.isEmpty()) continue;

                        JSONObject obj = new JSONObject();
                        obj.put("pkg", pkgName);
                        try {
                            PackageInfo pInfo = pm.getPackageInfo(pkgName, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                            boolean isSystem = (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            boolean isEnabled = pInfo.applicationInfo.enabled;
                            CharSequence label = pInfo.applicationInfo.loadLabel(pm);
                            obj.put("name", label != null ? label.toString() : pkgName);
                            obj.put("type", isSystem ? "System" : "User");
                            obj.put("status", isEnabled ? "Enabled" : "Disabled");
                        } catch (PackageManager.NameNotFoundException e) {
                            obj.put("name", pkgName);
                            obj.put("type", "System");
                            obj.put("status", "Unknown");
                        }
                        jsonArray.put(obj);
                    }

                    String jsonStr = jsonArray.toString();
                    final String base64Data = Base64.encodeToString(jsonStr.getBytes("UTF-8"), Base64.NO_WRAP);

                    activity.runOnUiThread(() ->
                            webView.evaluateJavascript("window.receiveAppList('" + base64Data + "');", null)
                    );

                } catch (Exception e) {
                    Log.e("NEXUS", "Fetch Error", e);
                    activity.runOnUiThread(() -> common.showToast("Error fetching apps: " + e.getMessage()));
                }
            });
        }
    }
}