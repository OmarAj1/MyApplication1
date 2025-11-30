package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

public class UserMainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar loader;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConsolidatedWebAppInterface mInterface;
    private NsdHelper nsdHelper;

    public class CommonInterface {
        private final AppCompatActivity mActivity;
        private final Context mContext;

        public CommonInterface(AppCompatActivity activity) {
            this.mActivity = activity;
            this.mContext = activity.getApplicationContext();
        }

        @JavascriptInterface
        public String getNativeCoreVersion() {
            return "4.2.0-PERSISTENT";
        }

        @JavascriptInterface
        public void hapticFeedback(String type) {
            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(20);
                }
            }
        }

        @JavascriptInterface
        public void showToast(String toast) {
            if (mActivity != null && !mActivity.isFinishing()) {
                mActivity.runOnUiThread(() ->
                        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
                );
            }
        }

        @JavascriptInterface
        public void shareText(String title, String content) {
            mActivity.runOnUiThread(() -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                shareIntent.putExtra(Intent.EXTRA_TEXT, content);
                Intent chooser = Intent.createChooser(shareIntent, title);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(chooser);
            });
        }
    }

    public class ShieldInterface {
        private final CommonInterface mCommon;

        public ShieldInterface(AppCompatActivity activity, CommonInterface common) {
            this.mCommon = common;
        }

        @JavascriptInterface
        public void startVpn() {
            Log.i("NEXUS_SHIELD", "Attempting to start VPN service...");
            mCommon.showToast("Shield Activated");
        }

        @JavascriptInterface
        public void stopVpn() {
            Log.i("NEXUS_SHIELD", "Attempting to stop VPN service...");
            mCommon.showToast("Shield Deactivated");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loader = findViewById(R.id.loader);

        if (webView == null || loader == null) return;

        webView.setBackgroundColor(0x00000000);
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        AdbSingleton.getInstance().init(this);
        nsdHelper = new NsdHelper(this);
        mInterface = new ConsolidatedWebAppInterface(this, executor, webView, nsdHelper);
        webView.addJavascriptInterface(mInterface, "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.postDelayed(() -> {
                    if (loader != null) loader.setVisibility(View.GONE);
                    view.setAlpha(0f);
                    view.animate().alpha(1.0f).setDuration(500).start();

                    // CRITICAL FIX: Restore state if we are ALREADY connected
                    // This handles cases where the WebView reloads but the Java process is alive
                    MyAdbManager manager = AdbSingleton.getInstance().getManager();
                    if (manager != null && manager.isConnected()) {
                        Log.d("NEXUS", "Restoring active connection state to UI");
                        webView.evaluateJavascript("window.adbStatus('Connected');", null);
                        mInterface.getInstalledPackages(); // Auto-refresh data
                    }
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

    // --- CRITICAL FIX: RESTART DISCOVERY ON RESUME ---
    @Override
    protected void onResume() {
        super.onResume();
        if (nsdHelper != null) {
            // Restart scanning when user comes back to the app
            nsdHelper.startMdnsDiscoveryInternal();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nsdHelper != null) {
            nsdHelper.stopMdnsDiscoveryInternal();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
        if (nsdHelper != null) nsdHelper.stopMdnsDiscoveryInternal();
    }

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
                    .setProvider(new BouncyCastleProvider()).build(mPrivateKey);
            X509CertificateHolder certHolder = certBuilder.build(signer);
            mCertificate = new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider()).getCertificate(certHolder);
        }

        @NonNull @Override protected PrivateKey getPrivateKey() { return mPrivateKey; }
        @NonNull @Override protected Certificate getCertificate() { return mCertificate; }
        @NonNull @Override protected String getDeviceName() { return "NexusUAD"; }

        public String runShellCommand(String cmd) throws Exception {
            if (!isConnected()) throw new IllegalStateException("ADB Not Connected");
            try (AdbStream stream = openStream("shell:" + cmd)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long startTime = System.currentTimeMillis();
                final long TIMEOUT = 40000;

                while (!stream.isClosed()) {
                    if (System.currentTimeMillis() - startTime > TIMEOUT) break;
                    try {
                        int bytesRead = stream.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            outputStream.write(buffer, 0, bytesRead);
                            startTime = System.currentTimeMillis();
                        } else if (bytesRead < 0) {
                            break;
                        }
                        Thread.sleep(5);
                    } catch (Exception e) { break; }
                }
                return outputStream.toString("UTF-8");
            }
        }
    }

    private class NsdHelper {
        private NsdManager nsdManager;
        private NsdManager.DiscoveryListener pairingListener, connectListener;
        private String autoPairIp, autoConnectIp;
        private int autoPairPort = -1, autoConnectPort = -1;
        private final AppCompatActivity activity;
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
            if (nsdManager == null || isDiscoveryActive) return;
            try {
                if (pairingListener == null) setupPairingListener();
                if (connectListener == null) setupConnectListener();

                nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, pairingListener);
                nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, connectListener);
                isDiscoveryActive = true;
                Log.d("NEXUS", "MDNS Discovery Started");
            } catch (Exception e) {
                Log.e("NEXUS", "MDNS Start Error", e);
                isDiscoveryActive = false;
            }
        }

        private void setupPairingListener() {
            pairingListener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String t) {}
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
                @Override public void onStartDiscoveryFailed(String t, int e) { stopInternal(); }
                @Override public void onStopDiscoveryFailed(String t, int e) { stopInternal(); }
            };
        }

        private void setupConnectListener() {
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
                @Override public void onStartDiscoveryFailed(String t, int e) { stopInternal(); }
                @Override public void onStopDiscoveryFailed(String t, int e) { stopInternal(); }
            };
        }

        private void stopInternal() {
            try { if(pairingListener != null) nsdManager.stopServiceDiscovery(pairingListener); } catch(Exception e){}
            try { if(connectListener != null) nsdManager.stopServiceDiscovery(connectListener); } catch(Exception e){}
            isDiscoveryActive = false;
        }

        public void stopMdnsDiscoveryInternal() {
            if (!isDiscoveryActive) return;
            stopInternal();
        }

        public void retrieveConnectionInfoInternal() {
            if (autoPairIp != null && autoPairPort != -1) sendToJs("onPairingServiceFound", autoPairIp, autoPairPort);
            if (autoConnectIp != null && autoConnectPort != -1) sendToJs("onConnectServiceFound", autoConnectIp, autoConnectPort);

            activity.runOnUiThread(() -> {
                try {
                    ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = cb.getPrimaryClip().getItemAt(0).getText();
                        if (text != null) {
                            Matcher m = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{4,5})").matcher(text);
                            if (m.find()) {
                                sendToJs("onPairingServiceFound", m.group(1), Integer.parseInt(m.group(2)));
                                Toast.makeText(activity, "Pasted: " + m.group(1), Toast.LENGTH_SHORT).show();
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
                    common.showToast("Core initializing...");
                    return;
                }
                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : nsdHelper.getAutoPairIp();
                    int targetPort = -1;
                    try { targetPort = Integer.parseInt(portStr); } catch (Exception e) { targetPort = nsdHelper.getAutoPairPort(); }

                    if (targetIp == null || targetPort == -1) {
                        common.showToast("Missing Pairing Info");
                        return;
                    }
                    boolean success = manager.pair(targetIp, targetPort, code);
                    common.showToast(success ? "Pairing Success!" : "Pairing Failed. Check Code.");
                } catch (Exception e) {
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
                        try { targetPort = Integer.parseInt(portStr); } catch (Exception e){}
                    } else {
                        targetPort = nsdHelper.getAutoConnectPort();
                        if (targetPort == -1) targetPort = nsdHelper.getAutoPairPort();
                    }

                    if (targetIp == null || targetPort == -1) {
                        common.showToast("Connection Info Missing");
                        return;
                    }

                    boolean connected = manager.connect(targetIp, targetPort);
                    if (connected) {
                        common.showToast("Connected to Shell");
                        activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connected');", null));
                    } else {
                        common.showToast("Connection Failed");
                        activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connection Failed');", null));
                    }
                } catch (Exception e) {
                    common.showToast("Connect Error: " + e.getMessage());
                    activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connect Error');", null));
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
                    if ("uninstall".equals(action)) cmd = "pm uninstall --user 0 " + pkg;
                    else if ("disable".equals(action)) cmd = "pm disable-user --user 0 " + pkg;
                    else if ("enable".equals(action)) cmd = "pm enable " + pkg;
                    else if ("restore".equals(action)) cmd = "cmd package install-existing " + pkg;

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
                String base64Data = "";
                try {
                    MyAdbManager manager = AdbSingleton.getInstance().getManager();
                    if (manager == null || !manager.isConnected()) {
                        Log.e("NEXUS", "ADB Disconnected during fetch");
                        base64Data = Base64.encodeToString("[]".getBytes(), Base64.NO_WRAP);
                    } else {
                        String rawList = manager.runShellCommand("pm list packages");

                        if (rawList == null || rawList.isEmpty()) {
                            base64Data = Base64.encodeToString("[]".getBytes(), Base64.NO_WRAP);
                        } else {
                            String[] lines = rawList.split("\n");
                            JSONArray jsonArray = new JSONArray();
                            for (String line : lines) {
                                String pkg = line.replace("package:", "").trim();
                                if (!pkg.isEmpty()) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("pkg", pkg);
                                    obj.put("name", pkg);
                                    obj.put("type", "User");
                                    obj.put("status", "Unknown");
                                    jsonArray.put(obj);
                                }
                            }
                            base64Data = Base64.encodeToString(jsonArray.toString().getBytes("UTF-8"), Base64.NO_WRAP);
                        }
                    }
                } catch (Exception e) {
                    Log.e("NEXUS", "Fetch Error", e);
                    base64Data = Base64.encodeToString("[]".getBytes(), Base64.NO_WRAP);
                }

                final String finalData = base64Data;
                activity.runOnUiThread(() ->
                        webView.evaluateJavascript("if(window.receiveAppList) window.receiveAppList('" + finalData + "');", null)
                );
            });
        }
    }
}