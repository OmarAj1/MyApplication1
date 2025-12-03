package com.example.myapplication.interfaces;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class ShieldInterface {
    private Context context;
    private boolean isVpnRunning = false;

    public ShieldInterface(Context context) {
        this.context = context;
    }

    // This matches window.AndroidNative.startVpn() in App.tsx
    @JavascriptInterface
    public void startVpn() {
        // Real VPN service logic would go here
        isVpnRunning = true;

        // Feedback for debugging
        System.out.println("NEXUS_CORE: Shield Protocol Initiated");
    }

    // This matches window.AndroidNative.stopVpn() in App.tsx
    @JavascriptInterface
    public void stopVpn() {
        isVpnRunning = false;
        System.out.println("NEXUS_CORE: Shield Protocol Terminated");
    }

    // This matches window.AndroidNative.getVpnStatus()
    @JavascriptInterface
    public boolean getVpnStatus() {
        return isVpnRunning;
    }

    @JavascriptInterface
    public void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void hapticFeedback(String type) {
        // Simple haptic implementation
        // You can add Vibrator logic here if desired
    }
}