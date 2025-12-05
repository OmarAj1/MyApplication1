package com.example.myapplication.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.myapplication.R;
import com.example.myapplication.UserMainActivity;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ShieldVpnService extends VpnService {

    private static final String TAG = "ShieldVpnService";
    private static final String CHANNEL_ID = "NexusShieldChannel";

    public static final String ACTION_VPN_STATUS = "com.example.myapplication.VPN_STATUS";
    public static final String EXTRA_IS_RUNNING = "isRunning";
    public static final String EXTRA_BLOCKED_COUNT = "blockedCount";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong blockedCount = new AtomicLong(0);

    private ParcelFileDescriptor vpnInterface;
    private FileOutputStream vpnOutput; // Critical: Single output stream
    private ExecutorService dnsThreadPool;

    // Internal Virtual IP for the VPN Interface
    private static final String VPN_ADDRESS = "10.0.0.2";

    // --- DNS PROFILES ---
    public enum DnsProfile {
        CONTROLD_ADS("Control D (Ads)", "76.76.2.2"),
        CLOUDFLARE("Cloudflare", "1.1.1.1"),
        GOOGLE("Google", "8.8.8.8");

        final String label;
        final String ipv4;

        DnsProfile(String label, String ipv4) {
            this.label = label;
            this.ipv4 = ipv4;
        }
    }

    // Default to ControlD as requested
    private DnsProfile activeProfile = DnsProfile.CONTROLD_ADS;

    // --- BLOCKLIST (Full List) ---
    private static final Set<String> BLOCKED_KEYWORDS = new HashSet<>(Arrays.asList(
            // --- TikTok / ByteDance ---
            "log.tiktokv.com", "mon.tiktokv.com", "log-va.tiktokv.com",
            "ib.tiktokv.com", "toblog.ctobsnssdk.com", "log16-normal-c-useast1a.tiktokv.com",
            "mssdk.dns.tiktok.com", "ws-log.tiktokv.com", "p16-tiktokcdn-com.akamaized.net",

            // --- Analytics & Crash Reporting ---
            "app-measurement.com", "firebase-logging", "crashlytics",
            "segment.io", "adjust.com", "appsflyer", "kochava", "branch.io",
            "amplitude", "mixpanel", "newrelic", "bugsnag", "sentry.io",

            // --- Ad Networks ---
            "doubleclick", "googleadservices", "ads", "analytics", "tracker", "metrics",
            "scorecardresearch", "quantserve", "moatads", "adcolony", "unity3d.ads",
            "applovin", "vungle", "inmobi", "tapjoy",

            // --- Consent Management ---
            "onetrust", "didomi", "quantcast", "cookiebot", "usercentrics",
            "trustarc", "osano", "cookie-script", "termly", "iubenda",
            "civiccomputing", "cookiepro", "cookielaw", "consensu"
    ));

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        if (dnsThreadPool == null || dnsThreadPool.isShutdown()) {
            dnsThreadPool = Executors.newFixedThreadPool(50);
        }

        // Start Foreground Notification IMMEDIATELY
        startForegroundServiceNotification();
        startVpn();
        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        createNotificationChannel();
        Intent intent = new Intent(this, UserMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ShieldVpnService.class);
        stopIntent.setAction("STOP");
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Nexus Shield Active")
                .setContentText("Reqs Blocked: " + blockedCount.get())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "Disconnect", pendingStopIntent).build())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE);

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // FIXED: Must match Manifest "specialUse"
                startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(1, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Start foreground failed", e);
            stopSelf();
        }
    }

    private void updateNotification() {
        if (!isRunning.get()) return;
        // Simplified update logic to avoid creating new intents constantly
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            // We reuse the basic builder params. In production, you'd cache the builder.
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Nexus Shield Active")
                    .setContentText("Reqs Blocked: " + blockedCount.get())
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);
            nm.notify(1, builder.build());
        }
    }

    private void startVpn() {
        if (isRunning.get()) return;

        Log.i(TAG, "Starting VPN Proxy...");
        Builder builder = new Builder();
        builder.setSession("NexusShield");
        builder.setMtu(1500);

        // 1. Configure Interface
        builder.addAddress(VPN_ADDRESS, 32);

        // 2. Routing Strategy
        // We set the DNS to the active profile (e.g. 76.76.2.2)
        // And we route THAT specific IP into the tunnel.
        // This ensures normal internet traffic bypasses the VPN (faster speed),
        // but DNS requests are captured by us.
        builder.addDnsServer(activeProfile.ipv4);
        try {
            builder.addRoute(activeProfile.ipv4, 32);
        } catch (Exception e) {
            Log.e(TAG, "Route error", e);
        }

        try {
            if (vpnInterface != null) vpnInterface.close();
            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                stopSelf();
                return;
            }

            // CRITICAL FIX: Initialize Output Stream ONCE here.
            // Opening it for every packet causes "Stream Closed" or "File Busy" errors.
            vpnOutput = new FileOutputStream(vpnInterface.getFileDescriptor());

            isRunning.set(true);
            blockedCount.set(0);
            broadcastStatus(true);

            new Thread(this::listenForPackets).start();

        } catch (Exception e) {
            Log.e(TAG, "Establish error", e);
            stopVpn();
        }
    }

    private void listenForPackets() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        byte[] buffer = new byte[32767];

        try {
            while (isRunning.get() && vpnInterface != null) {
                int length = in.read(buffer);
                if (length > 0) {
                    byte[] packetData = Arrays.copyOf(buffer, length);
                    if (dnsThreadPool != null && !dnsThreadPool.isShutdown()) {
                        dnsThreadPool.execute(() -> processPacket(packetData));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Packet listener error", e);
        } finally {
            stopVpn();
        }
    }

    private void processPacket(byte[] packetData) {
        try {
            // IPv4 (Version=4) and UDP (Protocol=17)
            if (((packetData[0] >> 4) & 0x0F) != 4 || packetData[9] != 17) return;

            int ipHeaderLen = (packetData[0] & 0x0F) * 4;
            int dstPort = ((packetData[ipHeaderLen + 2] & 0xFF) << 8) | (packetData[ipHeaderLen + 3] & 0xFF);

            if (dstPort == 53) {
                int udpHeaderLen = 8;
                int dnsStart = ipHeaderLen + udpHeaderLen;

                String queryDomain = extractDomain(packetData, dnsStart, packetData.length);

                if (isBlocked(queryDomain)) {
                    Log.d(TAG, "BLOCKING: " + queryDomain);
                    blockedCount.incrementAndGet();
                    updateNotification();
                    broadcastStatus(true);
                    return; // Drop packet (Ad Blocked)
                }

                forwardDnsQuery(packetData, ipHeaderLen, dnsStart, packetData.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Process packet error", e);
        }
    }

    private void forwardDnsQuery(byte[] originalPacket, int ipHeaderLen, int dnsStart, int totalLength) {
        DatagramSocket tunnelSocket = null;
        try {
            tunnelSocket = new DatagramSocket();
            // CRITICAL: Protect socket to bypass VPN and avoid infinite loop
            if (!protect(tunnelSocket)) {
                throw new IOException("Socket protection failed");
            }
            tunnelSocket.setSoTimeout(2500); // 2.5s Timeout

            byte[] dnsPayload = Arrays.copyOfRange(originalPacket, dnsStart, totalLength);
            InetAddress server = InetAddress.getByName(activeProfile.ipv4);
            DatagramPacket dnsPacket = new DatagramPacket(dnsPayload, dnsPayload.length, server, 53);
            tunnelSocket.send(dnsPacket);

            byte[] respBuf = new byte[4096];
            DatagramPacket respPacket = new DatagramPacket(respBuf, respBuf.length);
            tunnelSocket.receive(respPacket);

            // Construct Response
            byte[] rawResponse = buildResponsePacket(originalPacket, totalLength, respPacket.getData(), respPacket.getLength());

            // CRITICAL FIX: Use the synchronized write method
            writeToVpn(rawResponse);

        } catch (Exception e) {
            // Log.w(TAG, "DNS Forward failed: " + e.getMessage());
        } finally {
            if (tunnelSocket != null) tunnelSocket.close();
        }
    }

    // CRITICAL FIX: Synchronized writing to prevent packet corruption
    private synchronized void writeToVpn(byte[] packet) {
        try {
            if (vpnOutput != null && isRunning.get()) {
                vpnOutput.write(packet);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing to VPN interface", e);
        }
    }

    // --- HELPER METHODS ---

    private boolean isBlocked(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase();
        for (String keyword : BLOCKED_KEYWORDS) {
            if (clean.contains(keyword)) return true;
        }
        return false;
    }

    private byte[] buildResponsePacket(byte[] original, int ipLen, byte[] dnsData, int dnsLen) {
        int ipHeaderLen = (original[0] & 0x0F) * 4;
        int totalLen = ipHeaderLen + 8 + dnsLen;

        byte[] response = new byte[totalLen];

        // 1. Copy IP Header
        System.arraycopy(original, 0, response, 0, ipHeaderLen);

        // 2. Fix IP Length
        response[2] = (byte) (totalLen >> 8);
        response[3] = (byte) (totalLen & 0xFF);

        // 3. Swap Src/Dst IP Addresses
        System.arraycopy(original, 16, response, 12, 4);
        System.arraycopy(original, 12, response, 16, 4);

        // 4. Calculate IP Checksum (must zero it first)
        response[10] = 0;
        response[11] = 0;
        int ipChecksum = calculateChecksum(response, 0, ipHeaderLen);
        response[10] = (byte) (ipChecksum >> 8);
        response[11] = (byte) (ipChecksum & 0xFF);

        // 5. Swap UDP Ports
        response[ipHeaderLen] = original[ipHeaderLen + 2]; // Dest port becomes Src port
        response[ipHeaderLen + 1] = original[ipHeaderLen + 3];
        response[ipHeaderLen + 2] = original[ipHeaderLen]; // Src port becomes Dest port
        response[ipHeaderLen + 3] = original[ipHeaderLen + 1];

        // 6. Fix UDP Length
        int udpLen = 8 + dnsLen;
        response[ipHeaderLen + 4] = (byte) (udpLen >> 8);
        response[ipHeaderLen + 5] = (byte) (udpLen & 0xFF);
        response[ipHeaderLen + 6] = 0; // UDP Checksum is optional (0)
        response[ipHeaderLen + 7] = 0;

        // 7. Copy DNS Data
        System.arraycopy(dnsData, 0, response, ipHeaderLen + 8, dnsLen);

        return response;
    }

    // CRITICAL FIX: Standard Internet Checksum Algorithm
    // The previous version had bugs that caused the OS to reject packets.
    private int calculateChecksum(byte[] buf, int offset, int length) {
        int sum = 0;
        for (int i = 0; i < length; i += 2) {
            int word = ((buf[offset + i] & 0xFF) << 8) | ((i + 1 < length) ? (buf[offset + i + 1] & 0xFF) : 0);
            sum += word;
        }
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return ~sum & 0xFFFF;
    }

    private String extractDomain(byte[] data, int offset, int max) {
        try {
            int pos = offset + 12; // Skip DNS Header (12 bytes)
            StringBuilder sb = new StringBuilder();
            while (pos < max) {
                int len = data[pos] & 0xFF;
                if (len == 0) break;
                if (sb.length() > 0) sb.append(".");
                pos++;
                for (int i = 0; i < len; i++) {
                    sb.append((char) data[pos + i]);
                }
                pos += len;
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void stopVpn() {
        Log.i(TAG, "Stopping VPN Service...");
        isRunning.set(false);
        broadcastStatus(false);

        if (dnsThreadPool != null) {
            dnsThreadPool.shutdownNow();
            dnsThreadPool = null;
        }

        // Close output stream safely
        if (vpnOutput != null) {
            try { vpnOutput.close(); } catch (IOException ignored) {}
            vpnOutput = null;
        }

        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (IOException ignored) {}
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void broadcastStatus(boolean running) {
        Intent intent = new Intent(ACTION_VPN_STATUS);
        intent.putExtra(EXTRA_IS_RUNNING, running);
        intent.putExtra(EXTRA_BLOCKED_COUNT, blockedCount.get());
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Nexus Shield Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}