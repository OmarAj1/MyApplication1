package com.example.myapplication.managers;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.interfaces.AdbPairingListener;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdbPairingManager {
    private final Context context;
    private final WeakReference<AdbPairingListener> listenerRef;
    private final NsdManager nsdManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // State
    private String autoPairIp, autoConnectIp;
    private int autoPairPort = -1, autoConnectPort = -1;
    private boolean isDiscoveryActive = false;

    // Listeners
    private NsdManager.DiscoveryListener pairingListener;
    private NsdManager.DiscoveryListener connectListener;

    public AdbPairingManager(Context context, AdbPairingListener listener) {
        this.context = context.getApplicationContext();
        this.listenerRef = new WeakReference<>(listener);
        this.nsdManager = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    public void startMdnsDiscovery() {
        if (nsdManager == null || isDiscoveryActive) return;
        try {
            setupListeners();
            nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, pairingListener);
            nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, connectListener);
            isDiscoveryActive = true;
        } catch (Exception e) {
            isDiscoveryActive = false;
            Log.e("NEXUS_MDNS", "Discovery failed", e);
        }
    }

    public void stopMdnsDiscovery() {
        if (nsdManager != null && isDiscoveryActive) {
            try {
                if (pairingListener != null) nsdManager.stopServiceDiscovery(pairingListener);
                if (connectListener != null) nsdManager.stopServiceDiscovery(connectListener);
            } catch (Exception e) {
                Log.e("NEXUS_MDNS", "Stop discovery failed", e);
            } finally {
                isDiscoveryActive = false;
            }
        }
    }

    public void retrieveConnectionInfo() {
        if (autoPairIp != null) notifyPairingFound(autoPairIp, autoPairPort);
        if (autoConnectIp != null) notifyConnectFound(autoConnectIp, autoConnectPort);
    }

    public void pairAdb(String ip, String portStr, String code) {
        executor.execute(() -> {
            // FIX: Removed "UserMainActivity." prefix
            AdbSingleton singleton = AdbSingleton.getInstance();
            MyAdbManager manager = singleton.getAdbManager();

            if (manager == null) {
                notifyPairResult(false, "Core initializing... Try again.");
                return;
            }

            String cleanIp = (ip != null) ? ip.trim() : null;
            String cleanPortStr = (portStr != null) ? portStr.trim() : "";
            String cleanCode = (code != null) ? code.trim() : "";

            if (cleanCode.isEmpty()) {
                notifyPairResult(false, "Pairing Code Required");
                return;
            }

            try {
                String targetIp = (cleanIp != null && !cleanIp.isEmpty()) ? cleanIp : autoPairIp;
                int targetPort = -1;
                try {
                    targetPort = Integer.parseInt(cleanPortStr);
                } catch (Exception e) {
                    targetPort = autoPairPort;
                }

                if (targetPort <= 0) {
                    notifyPairResult(false, "Invalid Pair Port.");
                    return;
                }

                boolean success = false;
                if (targetIp != null) {
                    try {
                        success = manager.pair(targetIp, targetPort, cleanCode);
                    } catch (Exception e) {
                        Log.w("NEXUS_PAIR", "IP pair failed: " + e.getMessage());
                    }
                }

                if (!success) {
                    try {
                        success = manager.pair("127.0.0.1", targetPort, cleanCode);
                    } catch (Exception e) {
                        Log.w("NEXUS_PAIR", "Localhost pair failed: " + e.getMessage());
                    }
                }

                notifyPairResult(success, success ? "Pairing Success!" : "Pairing Failed. Check Code.");
            } catch (Exception e) {
                notifyPairResult(false, "Pair Error: " + e.getMessage());
            }
        });
    }

    public void connectAdb(String ip, String portStr) {
        executor.execute(() -> {
            // FIX: Removed "UserMainActivity." prefix
            AdbSingleton singleton = AdbSingleton.getInstance();
            MyAdbManager manager = singleton.getAdbManager();

            if (manager == null) {
                notifyConnectResult(false, "Core initializing... Try again.");
                return;
            }

            try {
                String cleanIp = (ip != null) ? ip.trim() : null;
                String cleanPortStr = (portStr != null) ? portStr.trim() : "";
                String targetIp = (cleanIp != null && !cleanIp.isEmpty()) ? cleanIp : autoConnectIp;

                int targetPort = -1;
                try {
                    targetPort = Integer.parseInt(cleanPortStr);
                } catch (Exception e) {
                    targetPort = autoConnectPort;
                }

                if (targetIp == null || targetPort <= 0) {
                    notifyConnectResult(false, "Connection Info Missing");
                    return;
                }

                boolean connected = false;
                notifyConnectResult(false, "Connecting to " + targetIp + "...");

                try {
                    connected = manager.connect(targetIp, targetPort);
                } catch (Exception e) {
                    Log.w("NEXUS_ADB", "IP connection failed: " + e.getMessage());
                }

                if (!connected) {
                    notifyConnectResult(false, "Retrying via Localhost...");
                    try {
                        connected = manager.connect("127.0.0.1", targetPort);
                    } catch (Exception e) {
                        Log.w("NEXUS_ADB", "Localhost failed: " + e.getMessage());
                    }
                }

                if (connected) {
                    notifyConnectResult(true, "Connected to Shell");
                } else {
                    notifyConnectResult(false, "Connection Failed.");
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                if (msg.contains("ECONNREFUSED")) {
                    notifyConnectResult(false, "Port Closed. Toggle Debugging.");
                } else {
                    notifyConnectResult(false, "Err: " + msg);
                }
            }
        });
    }

    private void setupListeners() {
        pairingListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String s) {}
            @Override public void onServiceFound(NsdServiceInfo s) {
                if (s.getServiceType().contains("adb-tls-pairing")) {
                    nsdManager.resolveService(s, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo s, int i) {}
                        @Override public void onServiceResolved(NsdServiceInfo s) {
                            autoPairIp = s.getHost().getHostAddress();
                            autoPairPort = s.getPort();
                            notifyPairingFound(autoPairIp, autoPairPort);
                        }
                    });
                }
            }
            @Override public void onServiceLost(NsdServiceInfo s) {
                autoPairIp = null;
                autoPairPort = -1;
            }
            @Override public void onDiscoveryStopped(String s) {}
            @Override public void onStartDiscoveryFailed(String s, int i) {}
            @Override public void onStopDiscoveryFailed(String s, int i) {}
        };

        connectListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String s) {}
            @Override public void onServiceFound(NsdServiceInfo s) {
                if (s.getServiceType().contains("adb-tls-connect")) {
                    nsdManager.resolveService(s, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo s, int i) {}
                        @Override public void onServiceResolved(NsdServiceInfo s) {
                            autoConnectIp = s.getHost().getHostAddress();
                            autoConnectPort = s.getPort();
                            notifyConnectFound(autoConnectIp, autoConnectPort);
                        }
                    });
                }
            }
            @Override public void onServiceLost(NsdServiceInfo s) {
                autoConnectIp = null;
                autoConnectPort = -1;
            }
            @Override public void onDiscoveryStopped(String s) {}
            @Override public void onStartDiscoveryFailed(String s, int i) {}
            @Override public void onStopDiscoveryFailed(String s, int i) {}
        };
    }

    private void notifyPairResult(boolean success, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            AdbPairingListener listener = listenerRef.get();
            if (listener != null) listener.onPairingResult(success, msg);
        });
    }

    private void notifyConnectResult(boolean success, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            AdbPairingListener listener = listenerRef.get();
            if (listener != null) listener.onConnectionResult(success, msg);
        });
    }

    private void notifyPairingFound(String ip, int port) {
        new Handler(Looper.getMainLooper()).post(() -> {
            AdbPairingListener listener = listenerRef.get();
            if (listener != null) listener.onPairingServiceFound(ip, port);
        });
    }

    private void notifyConnectFound(String ip, int port) {
        new Handler(Looper.getMainLooper()).post(() -> {
            AdbPairingListener listener = listenerRef.get();
            if (listener != null) listener.onConnectServiceFound(ip, port);
        });
    }
}