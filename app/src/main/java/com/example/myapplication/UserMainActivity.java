package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.services.ShieldVpnService;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

public class UserMainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar loader;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConsolidatedWebAppInterface mInterface;
    private NsdHelper nsdHelper;
    private static final int VPN_REQUEST_CODE = 0x0F;
    private static final int NOTIFICATION_REQUEST_CODE = 0x10;

    public class CommonInterface {
        private final Context mContext;
        public CommonInterface(Context context) { this.mContext = context; }

        @JavascriptInterface public String getNativeCoreVersion() { return "5.2.1-STABLE"; }

        @JavascriptInterface
        public void hapticFeedback(String type) {
            try {
                Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Exception e) {}
        }

        @JavascriptInterface
        public void showToast(String toast) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show()
            );
        }

        @JavascriptInterface
        public void shareText(String title, String content) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, content);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(Intent.createChooser(shareIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    public class ShieldInterface {
        private final CommonInterface mCommon;
        private final AppCompatActivity mActivity;

        public ShieldInterface(AppCompatActivity activity, CommonInterface common) {
            this.mCommon = common;
            this.mActivity = activity;
        }

        @JavascriptInterface
        public void startVpn() {
            Intent prepareIntent = VpnService.prepare(mCommon.mContext);
            if (prepareIntent != null) {
                mActivity.startActivityForResult(prepareIntent, VPN_REQUEST_CODE);
            } else {
                startShieldServiceInternal();
            }
        }

        @JavascriptInterface
        public void stopVpn() {
            try {
                Intent intent = new Intent(mCommon.mContext, ShieldVpnService.class);
                intent.setAction("STOP");
                mCommon.mContext.startService(intent);
                mCommon.showToast("Shield Deactivated");
            } catch (Exception e) {}
        }

        @JavascriptInterface public boolean getVpnStatus() { return false; }

        public void startShieldServiceInternal() {
            try {
                Intent intent = new Intent(mCommon.mContext, ShieldVpnService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) mCommon.mContext.startForegroundService(intent);
                else mCommon.mContext.startService(intent);
                mCommon.showToast("Shield Activated");
            } catch (Exception e) { mCommon.showToast("VPN Error: " + e.getMessage()); }
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
//        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

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

        AdbSingleton.getInstance().init(getApplicationContext());

        nsdHelper = new NsdHelper(this);
        mInterface = new ConsolidatedWebAppInterface(this, executor, webView, nsdHelper);
        webView.addJavascriptInterface(mInterface, "AndroidNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mainHandler.postDelayed(() -> {
                    if (loader != null) loader.setVisibility(View.GONE);
                    view.setAlpha(1.0f);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            mInterface.shield.startShieldServiceInternal();
        }
    }

    @Override protected void onResume() { super.onResume(); if (nsdHelper != null) nsdHelper.startMdnsDiscoveryInternal(); }
    @Override protected void onPause() { super.onPause(); if (nsdHelper != null) nsdHelper.stopMdnsDiscoveryInternal(); }
    @Override protected void onDestroy() { super.onDestroy(); if (nsdHelper != null) nsdHelper.stopMdnsDiscoveryInternal(); }

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
                    adbManager = new MyAdbManager(context);
                } catch (Exception e) {
                    Log.e("NEXUS", "Failed to init ADB", e);
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
        private final File keyFile;
        private final File certFile;

        public MyAdbManager(Context context) throws Exception {
            keyFile = new File(context.getFilesDir(), "adb_key.pk8");
            certFile = new File(context.getFilesDir(), "adb_key.pem");
            setApi(Build.VERSION.SDK_INT);

            // NUCLEAR OPTION: If keys exist, try to load them. If that fails, delete and regen.
            if (keyFile.exists() && certFile.exists()) {
                try {
                    loadKeys();
                    Log.d("NEXUS", "Keys loaded successfully.");
                } catch (Exception e) {
                    Log.e("NEXUS", "Corrupted keys found. Deleting and regenerating.", e);
                    keyFile.delete();
                    certFile.delete();
                    generateAndSaveKeys();
                }
            } else {
                generateAndSaveKeys();
            }
        }

        private void loadKeys() throws Exception {
            byte[] keyBytes = new byte[(int) keyFile.length()];
            try (FileInputStream fis = new FileInputStream(keyFile)) { fis.read(keyBytes); }

            // --- CRITICAL FIX: ADD "BC" HERE ---
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
            mPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

            try (FileInputStream fis = new FileInputStream(certFile)) {
                // --- CRITICAL FIX: ADD "BC" HERE ---
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
                mCertificate = certFactory.generateCertificate(fis);
            }
        }

        private void generateAndSaveKeys() throws Exception {
            // --- CRITICAL FIX: ADD "BC" HERE ---
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();
            mPrivateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            X500Name issuer = new X500Name("CN=NexusADB");
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, BigInteger.valueOf(Math.abs(System.currentTimeMillis())),
                    new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24),
                    new Date(System.currentTimeMillis() + 1000L * 3600 * 24 * 365 * 10),
                    new X500Name("CN=NexusADB"), publicKey
            );
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").setProvider(new BouncyCastleProvider()).build(mPrivateKey);
            mCertificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certBuilder.build(signer));

            try (FileOutputStream fos = new FileOutputStream(keyFile)) { fos.write(mPrivateKey.getEncoded()); }
            try (FileOutputStream fos = new FileOutputStream(certFile)) { fos.write(mCertificate.getEncoded()); }
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
                    int bytesRead = stream.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) { outputStream.write(buffer, 0, bytesRead); startTime = System.currentTimeMillis(); }
                    else if (bytesRead < 0) break;
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
            activity.runOnUiThread(() -> webView.evaluateJavascript(
                    String.format("if(window.%s) window.%s('%s', '%d');", func, func, ip, port), null));
        }

        public void startMdnsDiscoveryInternal() {
            if (nsdManager == null || isDiscoveryActive) return;
            try {
                if (pairingListener == null) setupListeners();
                nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, pairingListener);
                nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, connectListener);
                isDiscoveryActive = true;
            } catch (Exception e) { isDiscoveryActive = false; }
        }

        private void setupListeners() {
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
                @Override public void onStartDiscoveryFailed(String t, int e) {}
                @Override public void onStopDiscoveryFailed(String t, int e) {}
            };
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
                @Override public void onStartDiscoveryFailed(String t, int e) {}
                @Override public void onStopDiscoveryFailed(String t, int e) {}
            };
        }

        public void stopMdnsDiscoveryInternal() {
            try {
                if (nsdManager != null && isDiscoveryActive) {
                    if (pairingListener != null) nsdManager.stopServiceDiscovery(pairingListener);
                    if (connectListener != null) nsdManager.stopServiceDiscovery(connectListener);
                }
            } catch (Exception e) {} finally { isDiscoveryActive = false; }
        }

        public void retrieveConnectionInfoInternal() {
            if (autoPairIp != null) sendToJs("onPairingServiceFound", autoPairIp, autoPairPort);
            if (autoConnectIp != null) sendToJs("onConnectServiceFound", autoConnectIp, autoConnectPort);
        }
        public String getAutoPairIp() { return autoPairIp; }
        public int getAutoPairPort() { return autoPairPort; }
        public String getAutoConnectIp() { return autoConnectIp; }
        public int getAutoConnectPort() { return autoConnectPort; }
    }

    public class ConsolidatedWebAppInterface {
        public final CommonInterface common;
        public final ShieldInterface shield;
        private final AppCompatActivity activity;
        private final ExecutorService executor;
        private final NsdHelper nsdHelper;
        private final WebView webView;

        ConsolidatedWebAppInterface(AppCompatActivity activity, ExecutorService executor, WebView webView, NsdHelper nsdHelper) {
            this.activity = activity;
            this.executor = executor;
            this.webView = webView;
            this.nsdHelper = nsdHelper;
            this.common = new CommonInterface(activity.getApplicationContext());
            this.shield = new ShieldInterface(activity, common);
        }

        @JavascriptInterface public String getNativeCoreVersion() { return common.getNativeCoreVersion(); }
        @JavascriptInterface public void hapticFeedback(String type) { common.hapticFeedback(type); }
        @JavascriptInterface public void showToast(String toast) { common.showToast(toast); }
        @JavascriptInterface public void shareText(String t, String c) { common.shareText(t, c); }
        @JavascriptInterface public void pairAdb(String ip, String p, String c) { pairAdbInternal(ip, p, c); }
        @JavascriptInterface public boolean connectAdb(String ip, String p) { connectAdbInternal(ip, p); return true; }
        @JavascriptInterface public void executeCommand(String a, String p, int userId) { executeCommandInternal(a, p, userId); }
        @JavascriptInterface public void getInstalledPackages() { fetchRealPackageListInternal(); }
        @JavascriptInterface public void getUsers() { fetchUsersInternal(); }
        @JavascriptInterface public void startMdnsDiscovery() { nsdHelper.startMdnsDiscoveryInternal(); }
        @JavascriptInterface public void stopMdnsDiscovery() { nsdHelper.stopMdnsDiscoveryInternal(); }
        @JavascriptInterface public void retrieveConnectionInfo() { nsdHelper.retrieveConnectionInfoInternal(); }
        @JavascriptInterface public void startVpn() { shield.startVpn(); }
        @JavascriptInterface public void stopVpn() { shield.stopVpn(); }
        @JavascriptInterface public boolean getVpnStatus() { return shield.getVpnStatus(); }

        private void pairAdbInternal(String ip, String portStr, String code) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null) { common.showToast("Core initializing..."); return; }
                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : nsdHelper.getAutoPairIp();
                    int targetPort = -1;
                    try { targetPort = Integer.parseInt(portStr); } catch (Exception e) { targetPort = nsdHelper.getAutoPairPort(); }
                    if (targetIp == null || targetPort == -1) { common.showToast("Missing Pairing Info"); return; }

                    // If pairing locally, ensure we use loopback if needed
                    if(targetIp == null) targetIp = "127.0.0.1";

                    boolean success = manager.pair(targetIp, targetPort, code);
                    common.showToast(success ? "Pairing Success!" : "Pairing Failed. Check Code.");
                } catch (Exception e) { common.showToast("Pair Error: " + e.getMessage()); }
            });
        }

        private void connectAdbInternal(String ip, String portStr) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null) return;
                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : nsdHelper.getAutoConnectIp();
                    int targetPort = -1;
                    try { targetPort = Integer.parseInt(portStr); } catch (Exception e) { targetPort = nsdHelper.getAutoConnectPort(); }

                    if (targetIp == null || targetPort == -1) { common.showToast("Connection Info Missing"); return; }

                    // FORCE LOCALHOST: This bypasses firewall issues on the device itself
                    if(targetIp.startsWith("192") || targetIp.startsWith("10.") || targetIp.equals(nsdHelper.getAutoConnectIp())) {
                        targetIp = "127.0.0.1";
                    }

                    common.showToast("Connecting to: " + targetIp + ":" + targetPort);
                    boolean connected = manager.connect(targetIp, targetPort);
                    if (connected) {
                        common.showToast("Connected to Shell");
                        activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connected');", null));
                    } else {
                        common.showToast("Connection Failed");
                        activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connection Failed');", null));
                    }
                } catch (Exception e) {
                    common.showToast("Err: " + e.toString());
                    Log.e("NEXUS_DEBUG", "Connection Failed", e);
                    activity.runOnUiThread(() -> webView.evaluateJavascript("window.adbStatus('Connect Error');", null));
                }
            });
        }

        private void executeCommandInternal(String action, String pkg, int userId) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null || !manager.isConnected()) { common.showToast("Not Connected"); return; }
                try {
                    String cmd = "";
                    if ("uninstall".equals(action)) cmd = "pm uninstall --user " + userId + " " + pkg;
                    else if ("disable".equals(action)) cmd = "pm disable-user --user " + userId + " " + pkg;
                    else if ("enable".equals(action)) cmd = "pm enable --user " + userId + " " + pkg;
                    else if ("restore".equals(action)) cmd = "cmd package install-existing --user " + userId + " " + pkg;

                    if (!cmd.isEmpty()) {
                        manager.runShellCommand(cmd);
                        common.showToast("Executed: " + action + " (User " + userId + ")");
                        fetchRealPackageListInternal();
                    }
                } catch(Exception e) { common.showToast("Cmd Failed: " + e.getMessage()); }
            });
        }

        private void fetchUsersInternal() {
            executor.execute(() -> {
                try {
                    MyAdbManager manager = AdbSingleton.getInstance().getManager();
                    if (manager != null && manager.isConnected()) {
                        String raw = manager.runShellCommand("pm list users");
                        String b64 = Base64.encodeToString(raw.getBytes(), Base64.NO_WRAP);
                        activity.runOnUiThread(() -> webView.evaluateJavascript("if(window.receiveUsers) window.receiveUsers('" + b64 + "');", null));
                    }
                } catch (Exception e) { Log.e("NEXUS", "User Fetch Error", e); }
            });
        }

        private void fetchRealPackageListInternal() {
            executor.execute(() -> {
                String base64Data = Base64.encodeToString("[]".getBytes(), Base64.NO_WRAP);
                try {
                    PackageManager pm = activity.getPackageManager();
                    List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
                    JSONArray jsonArray = new JSONArray();
                    for (PackageInfo pInfo : packages) {
                        if (pInfo.packageName.equals(activity.getPackageName())) continue;
                        boolean isSystem = (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        JSONObject obj = new JSONObject();
                        obj.put("pkg", pInfo.packageName);
                        obj.put("type", isSystem ? "System" : "User");
                        obj.put("status", pInfo.applicationInfo.enabled ? "Enabled" : "Disabled");
                        CharSequence label = pInfo.applicationInfo.loadLabel(pm);
                        obj.put("name", label != null ? label.toString() : pInfo.packageName);
                        jsonArray.put(obj);
                    }
                    base64Data = Base64.encodeToString(jsonArray.toString().getBytes("UTF-8"), Base64.NO_WRAP);
                } catch (Exception e) {}
                final String finalData = base64Data;
                activity.runOnUiThread(() -> webView.evaluateJavascript("if(window.receiveAppList) window.receiveAppList('" + finalData + "');", null));
            });
        }
    }
}