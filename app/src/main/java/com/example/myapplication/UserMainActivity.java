package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import java.util.List;
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

        @JavascriptInterface public String getNativeCoreVersion() { return "5.1.2-STABLE"; }

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
                mActivity.runOnUiThread(() -> Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show());
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
        public ShieldInterface(AppCompatActivity activity, CommonInterface common) { this.mCommon = common; }
        @JavascriptInterface public void startVpn() { mCommon.showToast("Shield Activated"); }
        @JavascriptInterface public void stopVpn() { mCommon.showToast("Shield Deactivated"); }
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
                Log.d("NEXUS_WEB", cm.message() + " -- From line " + cm.lineNumber());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nsdHelper != null) nsdHelper.startMdnsDiscoveryInternal();
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
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, BigInteger.valueOf(Math.abs(System.currentTimeMillis())),
                    new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24),
                    new Date(System.currentTimeMillis() + 1000L * 3600 * 24 * 365 * 10),
                    new X500Name("CN=NexusADB"), publicKey
            );
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").setProvider(new BouncyCastleProvider()).build(mPrivateKey);
            mCertificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certBuilder.build(signer));
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
                @Override public void onStartDiscoveryFailed(String t, int e) { stopMdnsDiscoveryInternal(); }
                @Override public void onStopDiscoveryFailed(String t, int e) { stopMdnsDiscoveryInternal(); }
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
                @Override public void onStartDiscoveryFailed(String t, int e) { stopMdnsDiscoveryInternal(); }
                @Override public void onStopDiscoveryFailed(String t, int e) { stopMdnsDiscoveryInternal(); }
            };
        }

        public void stopMdnsDiscoveryInternal() {
            try {
                if (nsdManager != null && isDiscoveryActive) {
                    if (pairingListener != null) nsdManager.stopServiceDiscovery(pairingListener);
                    if (connectListener != null) nsdManager.stopServiceDiscovery(connectListener);
                }
            } catch (Exception e) {}
            finally { isDiscoveryActive = false; }
        }

        public void retrieveConnectionInfoInternal() {
            if (autoPairIp != null) sendToJs("onPairingServiceFound", autoPairIp, autoPairPort);
            if (autoConnectIp != null) sendToJs("onConnectServiceFound", autoConnectIp, autoConnectPort);
            activity.runOnUiThread(() -> {
                try {
                    ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = cb.getPrimaryClip().getItemAt(0).getText();
                        Matcher m = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{4,5})").matcher(text);
                        if (m.find()) sendToJs("onPairingServiceFound", m.group(1), Integer.parseInt(m.group(2)));
                    }
                } catch (Exception e) {}
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
        @JavascriptInterface public void executeCommand(String a, String p, int userId) { executeCommandInternal(a, p, userId); }
        @JavascriptInterface public void getInstalledPackages() { fetchRealPackageListInternal(); }
        @JavascriptInterface public void getUsers() { fetchUsersInternal(); }
        @JavascriptInterface public void startMdnsDiscovery() { nsdHelper.startMdnsDiscoveryInternal(); }
        @JavascriptInterface public void stopMdnsDiscovery() { nsdHelper.stopMdnsDiscoveryInternal(); }
        @JavascriptInterface public void retrieveConnectionInfo() { nsdHelper.retrieveConnectionInfoInternal(); }
        @JavascriptInterface public void startVpn() { shield.startVpn(); }
        @JavascriptInterface public void stopVpn() { shield.stopVpn(); }

        private void pairAdbInternal(String ip, String portStr, String code) {
            executor.execute(() -> {
                MyAdbManager manager = AdbSingleton.getInstance().getManager();
                if (manager == null) { common.showToast("Core initializing..."); return; }
                try {
                    String targetIp = (ip != null && !ip.isEmpty()) ? ip : nsdHelper.getAutoPairIp();
                    int targetPort = -1;
                    try { targetPort = Integer.parseInt(portStr); } catch (Exception e) { targetPort = nsdHelper.getAutoPairPort(); }
                    if (targetIp == null || targetPort == -1) { common.showToast("Missing Pairing Info"); return; }
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
                    if (targetIp == null) targetIp = nsdHelper.getAutoPairIp();
                    int targetPort = -1;
                    try { targetPort = Integer.parseInt(portStr); } catch (Exception e) { targetPort = nsdHelper.getAutoConnectPort(); }
                    if (targetPort == -1) targetPort = nsdHelper.getAutoPairPort();

                    if (targetIp == null || targetPort == -1) { common.showToast("Connection Info Missing"); return; }

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
                    // Fetch ALL installed packages (System + User) for the device
                    List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);

                    JSONArray jsonArray = new JSONArray();
                    for (PackageInfo pInfo : packages) {
                        JSONObject obj = new JSONObject();
                        String pkgName = pInfo.packageName;
                        obj.put("pkg", pkgName);

                        // --- CATEGORIZATION LOGIC ---
                        boolean isSystem = (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        obj.put("type", isSystem ? "System" : "User");

                        // --- STATUS LOGIC ---
                        boolean isEnabled = pInfo.applicationInfo.enabled;
                        boolean isInstalled = (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0;

                        if (!isInstalled) obj.put("status", "Uninstalled"); // Was uninstalled via pm uninstall -k
                        else if (isEnabled) obj.put("status", "Enabled");
                        else obj.put("status", "Disabled");

                        CharSequence label = pInfo.applicationInfo.loadLabel(pm);
                        obj.put("name", label != null ? label.toString() : pkgName);

                        // --- ICON EXTRACTION (For user apps only to save memory, or all if requested) ---
                        // Only fetch icons for User apps to keep JSON size manageable, or system if crucial
                        if (!isSystem) {
                            try {
                                Drawable icon = pInfo.applicationInfo.loadIcon(pm);
                                Bitmap bitmap;
                                if (icon instanceof BitmapDrawable) {
                                    bitmap = ((BitmapDrawable) icon).getBitmap();
                                } else {
                                    bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                                    Canvas canvas = new Canvas(bitmap);
                                    icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                                    icon.draw(canvas);
                                }
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 50, baos); // Compress 50% quality
                                byte[] iconBytes = baos.toByteArray();
                                obj.put("iconBase64", Base64.encodeToString(iconBytes, Base64.NO_WRAP));
                            } catch (Exception ignored) {}
                        }

                        jsonArray.put(obj);
                    }
                    base64Data = Base64.encodeToString(jsonArray.toString().getBytes("UTF-8"), Base64.NO_WRAP);

                } catch (Exception e) { Log.e("NEXUS", "Fetch Error", e); }

                final String finalData = base64Data;
                activity.runOnUiThread(() ->
                        webView.evaluateJavascript("if(window.receiveAppList) window.receiveAppList('" + finalData + "');", null)
                );
            });
        }
    }
}