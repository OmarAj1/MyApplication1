package com.example.myapplication.managers;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

public class MyAdbManager extends AbsAdbConnectionManager {
    private PrivateKey mPrivateKey;
    private Certificate mCertificate;
    private final File keyFile;
    private final File certFile;

    public MyAdbManager(Context context) throws Exception {
        keyFile = new File(context.getFilesDir(), "adb_key.pk8");
        certFile = new File(context.getFilesDir(), "adb_key.pem");
        setApi(Build.VERSION.SDK_INT);

        if (keyFile.exists() && certFile.exists()) {
            try {
                loadKeys();
                Log.d("NEXUS", "Keys loaded successfully.");
            } catch (Exception e) {
                Log.e("NEXUS", "Corrupted keys found. Deleting and regenerating.", e);
                keyFile.delete();
                certFile.delete();
                generateAndSaveKeys();
            }
        } else {
            generateAndSaveKeys();
        }
    }

    private void loadKeys() throws Exception {
        byte[] keyBytes = new byte[(int) keyFile.length()];
        try (FileInputStream fis = new FileInputStream(keyFile)) { fis.read(keyBytes); }
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
        mPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        try (FileInputStream fis = new FileInputStream(certFile)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
            mCertificate = certFactory.generateCertificate(fis);
        }
    }

    private void generateAndSaveKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair pair = keyGen.generateKeyPair();
        mPrivateKey = pair.getPrivate();
        PublicKey publicKey = pair.getPublic();

        X500Name issuer = new X500Name("CN=NexusADB");
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, BigInteger.valueOf(Math.abs(System.currentTimeMillis())),
                new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24),
                new Date(System.currentTimeMillis() + 1000L * 3600 * 24 * 365 * 10),
                new X500Name("CN=NexusADB"), publicKey
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").setProvider(new BouncyCastleProvider()).build(mPrivateKey);
        mCertificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certBuilder.build(signer));

        try (FileOutputStream fos = new FileOutputStream(keyFile)) { fos.write(mPrivateKey.getEncoded()); }
        try (FileOutputStream fos = new FileOutputStream(certFile)) { fos.write(mCertificate.getEncoded()); }
    }

    @NonNull @Override protected PrivateKey getPrivateKey() { return mPrivateKey; }
    @NonNull @Override protected Certificate getCertificate() { return mCertificate; }
    @NonNull @Override protected String getDeviceName() { return "NexusUAD"; }

    public String runShellCommand(String cmd) throws Exception {
        if (!isConnected()) throw new IllegalStateException("ADB Not Connected");
        try (AdbStream stream = openStream("shell:" + cmd)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long startTime = System.currentTimeMillis();
            final long TIMEOUT = 40000;
            while (!stream.isClosed()) {
                if (System.currentTimeMillis() - startTime > TIMEOUT) break;
                int bytesRead = stream.read(buffer, 0, buffer.length);
                if (bytesRead > 0) { outputStream.write(buffer, 0, bytesRead); startTime = System.currentTimeMillis(); }
                else if (bytesRead < 0) break;
            }
            return outputStream.toString("UTF-8");
        }
    }
}