package com.example.myapplication.interfaces;

import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.webkit.JavascriptInterface;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.UserMainActivity;
import com.example.myapplication.services.ShieldVpnService;

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
            // This calls back to UserMainActivity.onActivityResult
            mActivity.startActivityForResult(prepareIntent, UserMainActivity.VPN_REQUEST_CODE);
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

    @JavascriptInterface
    public boolean getVpnStatus() {
        // Implement logic to check if ShieldVpnService is actually running
        // For now, returning false as default
        return false;
    }

    public void startShieldServiceInternal() {
        try {
            Intent intent = new Intent(mCommon.mContext, ShieldVpnService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mCommon.mContext.startForegroundService(intent);
            } else {
                mCommon.mContext.startService(intent);
            }
            mCommon.showToast("Shield Activated");
        } catch (Exception e) {
            mCommon.showToast("VPN Error: " + e.getMessage());
        }
    }
}