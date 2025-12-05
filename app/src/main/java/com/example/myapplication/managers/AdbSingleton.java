package com.example.myapplication.managers;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdbSingleton {
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