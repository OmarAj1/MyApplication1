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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
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

    private DnsProfile activeProfile = DnsProfile.CONTROLD_ADS;

    // --- BLOCKLIST ---
    private static final Set<String> BLOCKED_KEYWORDS = new HashSet<>(Arrays.asList(
            // Social / Video / Apps
            "tiktok", "musical.ly", "byteoversea", "ibytedtos",

            // Ads & Trackers
            "doubleclick", "ads", "analytics", "tracker", "metrics",

            // Consent Management / Cookie Banners (CMP)
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

        startForegroundServiceNotification();
        startVpn();
        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        createNotificationChannel();
        Intent stopIntent = new Intent(this, ShieldVpnService.class);
        stopIntent.setAction("STOP");
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Nexus Shield Active")
                .setContentText("Protected by: " + activeProfile.label)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .addAction(new Notification.Action.Builder(null, "Disconnect", pendingStopIntent).build())
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(1, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Start foreground failed", e);
            stopSelf();
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

        // 2. DNS Server: Tell Android to use the REAL public DNS IP
        // This makes the "DNS PROBE" pass because the IP is valid.
        builder.addDnsServer(activeProfile.ipv4);

        // 3. Route: Capture traffic destined for that DNS IP
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

            isRunning.set(true);
            blockedCount.set(0);
            broadcastStatus(true);

            // Start the Traffic Processor
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
                    dnsThreadPool.execute(() -> processPacket(packetData));
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
            // Basic IPv4/UDP Check
            byte version = (byte) ((packetData[0] >> 4) & 0x0F);
            byte protocol = packetData[9];

            if (version != 4 || protocol != 17) return;

            int ipHeaderLength = (packetData[0] & 0x0F) * 4;
            int dstPort = ((packetData[ipHeaderLength + 2] & 0xFF) << 8) | (packetData[ipHeaderLength + 3] & 0xFF);

            if (dstPort == 53) {
                int udpHeaderLength = 8;
                int dnsStart = ipHeaderLength + udpHeaderLength;

                String queryDomain = extractDomain(packetData, dnsStart, packetData.length);

                if (isBlocked(queryDomain)) {
                    Log.d(TAG, "BLOCKING: " + queryDomain);
                    blockedCount.incrementAndGet();
                    broadcastStatus(true);
                    return; // Drop packet
                }

                forwardDnsQuery(packetData, ipHeaderLength, dnsStart, packetData.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Process packet error", e);
        }
    }

    private void forwardDnsQuery(byte[] originalPacket, int ipHeaderLen, int dnsStart, int totalLength) {
        DatagramSocket tunnelSocket = null;
        try {
            tunnelSocket = new DatagramSocket();
            if (!protect(tunnelSocket)) {
                throw new IOException("Socket protection failed");
            }
            tunnelSocket.setSoTimeout(2000);

            byte[] dnsPayload = Arrays.copyOfRange(originalPacket, dnsStart, totalLength);
            InetAddress server = InetAddress.getByName(activeProfile.ipv4);
            DatagramPacket dnsPacket = new DatagramPacket(dnsPayload, dnsPayload.length, server, 53);
            tunnelSocket.send(dnsPacket);

            byte[] respBuf = new byte[4096];
            DatagramPacket respPacket = new DatagramPacket(respBuf, respBuf.length);
            tunnelSocket.receive(respPacket);

            // Construct Response
            byte[] rawResponse = buildResponsePacket(originalPacket, totalLength, respPacket.getData(), respPacket.getLength());

            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            out.write(rawResponse);

        } catch (Exception e) {
            // Log.w(TAG, "DNS Forward failed: " + e.getMessage());
        } finally {
            if (tunnelSocket != null) tunnelSocket.close();
        }
    }

    // --- HELPER METHODS (Same as before but critical for function) ---
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

        System.arraycopy(original, 0, response, 0, ipHeaderLen);
        System.arraycopy(original, 16, response, 12, 4);
        System.arraycopy(original, 12, response, 16, 4);

        response[2] = (byte) (totalLen >> 8);
        response[3] = (byte) (totalLen & 0xFF);

        response[10] = 0;
        response[11] = 0;
        int ipChecksum = calculateChecksum(response, 0, ipHeaderLen);
        response[10] = (byte) (ipChecksum >> 8);
        response[11] = (byte) (ipChecksum & 0xFF);

        response[ipHeaderLen] = original[ipHeaderLen + 2];
        response[ipHeaderLen + 1] = original[ipHeaderLen + 3];
        response[ipHeaderLen + 2] = original[ipHeaderLen];
        response[ipHeaderLen + 3] = original[ipHeaderLen + 1];

        int udpLen = 8 + dnsLen;
        response[ipHeaderLen + 4] = (byte) (udpLen >> 8);
        response[ipHeaderLen + 5] = (byte) (udpLen & 0xFF);
        response[ipHeaderLen + 6] = 0;
        response[ipHeaderLen + 7] = 0;

        System.arraycopy(dnsData, 0, response, ipHeaderLen + 8, dnsLen);

        return response;
    }

    private int calculateChecksum(byte[] buf, int offset, int length) {
        int sum = 0;
        for (int i = 0; i < length; i += 2) {
            int word = ((buf[offset + i] & 0xFF) << 8) | (buf[offset + i + 1] & 0xFF);
            sum += word;
            if ((sum & 0xFFFF0000) != 0) {
                sum &= 0xFFFF;
                sum++;
            }
        }
        return ~sum & 0xFFFF;
    }

    private String extractDomain(byte[] data, int offset, int max) {
        try {
            int pos = offset + 12;
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