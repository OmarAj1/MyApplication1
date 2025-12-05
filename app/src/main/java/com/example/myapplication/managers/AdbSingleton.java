package com.example.myapplication.managers;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;


public class AdbSingleton {

    private static AdbSingleton instance;
    private MyAdbManager adbManager;
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);

    // Callback interface
    public interface AdbInitListener {
        void onInitComplete();
    }

    private AdbSingleton() {
        // Private constructor to enforce Singleton pattern
    }

    public static synchronized AdbSingleton getInstance() {
        if (instance == null) {
            instance = new AdbSingleton();
        }
        return instance;
    }

    public MyAdbManager getAdbManager() {
        return adbManager;
    }

    public void init(Context context, AdbInitListener listener) {
        if (adbManager != null) {
            if (listener != null) {
                listener.onInitComplete(); // Already initialized
            }
            return;
        }

        // Prevent multiple init threads
        if (isInitializing.get()) {
            return;
        }

        isInitializing.set(true);
        new Thread(() -> {
            try {
                // Register BouncyCastle for crypto operations if needed
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }

                // Initialize your custom manager
                adbManager = new MyAdbManager(context);

                if (listener != null) {
                    listener.onInitComplete();
                }
            } catch (Exception e) {
                Log.e("NEXUS", "Failed to init ADB", e);
            } finally {
                isInitializing.set(false);
            }
        }).start();
    }
}