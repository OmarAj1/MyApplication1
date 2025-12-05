package com.example.myapplication.interfaces;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CommonInterface {
    public final Context mContext;
    private final AppCompatActivity mActivity;

    public CommonInterface(AppCompatActivity activity) {
        this.mActivity = activity;
        this.mContext = activity.getApplicationContext();
    }

    @JavascriptInterface
    public String getNativeCoreVersion() {
        return "5.2.1-STABLE";
    }

    @JavascriptInterface
    public void hapticFeedback(String type) {
        try {
            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(20);
                }
            }
        } catch (Exception e) {
            Log.e("NEXUS_HAPTIC", "Error", e);
        }
    }

    @JavascriptInterface
    public void showToast(String toast) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show()
        );
    }

    @JavascriptInterface
    public void shareText(String title, String content) {
        // Run on UI Thread to safely launch Activities
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                shareIntent.putExtra(Intent.EXTRA_TEXT, content);

                Intent chooser = Intent.createChooser(shareIntent, title);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(chooser);
            } catch (Exception e) {
                showToast("Share failed: " + e.getMessage());
            }
        });
    }
}