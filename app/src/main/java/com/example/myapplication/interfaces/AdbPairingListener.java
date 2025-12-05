package com.example.myapplication.interfaces;

public interface AdbPairingListener {
    void onPairingServiceFound(String ip, int port);
    void onConnectServiceFound(String ip, int port);
    void onPairingResult(boolean success, String message);
    void onConnectionResult(boolean success, String message);
}