package com.example.myapplication.interfaces;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.managers.AdbPairingManager;
import com.example.myapplication.managers.AdbSingleton;
import com.example.myapplication.managers.MyAdbManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class ConsolidatedWebAppInterface {
    public final CommonInterface common;
    public final ShieldInterface shield;
    private final AppCompatActivity activity;
    private final ExecutorService executor;
    private final AdbPairingManager pairingManager;
    private final WebView webView;

    public ConsolidatedWebAppInterface(AppCompatActivity activity, ExecutorService executor, WebView webView, AdbPairingManager pairingManager) {
        this.activity = activity;
        this.executor = executor;
        this.webView = webView;
        this.pairingManager = pairingManager;

        // Initialize sub-interfaces
        this.common = new CommonInterface(activity);
        this.shield = new ShieldInterface(activity, common);
    }

    // --- Bridge Methods ---
    @JavascriptInterface public String getNativeCoreVersion() { return common.getNativeCoreVersion(); }
    @JavascriptInterface public void hapticFeedback(String type) { common.hapticFeedback(type); }
    @JavascriptInterface public void showToast(String toast) { common.showToast(toast); }
    @JavascriptInterface public void shareText(String t, String c) { common.shareText(t, c); }

    // --- ADB Pairing Methods ---
    @JavascriptInterface public void pairAdb(String ip, String p, String c) { pairingManager.pairAdb(ip, p, c); }
    @JavascriptInterface public boolean connectAdb(String ip, String p) { pairingManager.connectAdb(ip, p); return true; }
    @JavascriptInterface public void startMdnsDiscovery() { pairingManager.startMdnsDiscovery(); }
    @JavascriptInterface public void stopMdnsDiscovery() { pairingManager.stopMdnsDiscovery(); }
    @JavascriptInterface public void retrieveConnectionInfo() { pairingManager.retrieveConnectionInfo(); }

    // --- Shield Methods ---
    @JavascriptInterface public void startVpn() { shield.startVpn(); }
    @JavascriptInterface public void stopVpn() { shield.stopVpn(); }
    @JavascriptInterface public boolean getVpnStatus() { return shield.getVpnStatus(); }

    // --- App Management Methods ---
    @JavascriptInterface public void executeCommand(String a, String p, int userId) { executeCommandInternal(a, p, userId); }
    @JavascriptInterface public void getInstalledPackages() { fetchRealPackageListInternal(); }
    @JavascriptInterface public void getUsers() { fetchUsersInternal(); }

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
                    String output = manager.runShellCommand(cmd);
                    String cleanOutput = output != null ? output.trim().toLowerCase() : "";

                    // LOGIC FIX: Check for "success" OR "installed" (for restore)
                    if (cleanOutput.contains("success") || cleanOutput.contains("installed")) {
                        common.showToast(action + " Success");
                    } else {
                        common.showToast("Failed: " + (cleanOutput.length() > 50 ? cleanOutput.substring(0, 47) + "..." : cleanOutput));
                    }

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