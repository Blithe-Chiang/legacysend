package com.blithe.legacysend.security;

import android.content.Context;
import android.security.KeyPairGeneratorSpec;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

public final class TlsIdentity {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "legacysend-device-identity";

    private final KeyStore keyStore;
    private final String fingerprint;

    private TlsIdentity(KeyStore keyStore, String fingerprint) {
        this.keyStore = keyStore;
        this.fingerprint = fingerprint;
    }

    public static TlsIdentity loadOrCreate(Context context) throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 20);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(ALIAS)
                    .setSubject(new X500Principal("CN=LegacySend"))
                    .setSerialNumber(new BigInteger(128, new SecureRandom()).abs().add(BigInteger.ONE))
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", STORE);
            generator.initialize(spec);
            generator.generateKeyPair();
            store.load(null);
        }
        X509Certificate certificate = (X509Certificate) store.getCertificate(ALIAS);
        return new TlsIdentity(store, hex(MessageDigest.getInstance("SHA-256")
                .digest(certificate.getEncoded())));
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public SSLServerSocket createServerSocket(int port) throws Exception {
        SSLContext context = createContext(new AcceptAllTrustManager());
        SSLServerSocketFactory factory = context.getServerSocketFactory();
        SSLServerSocket socket = (SSLServerSocket) factory.createServerSocket(port);
        socket.setReuseAddress(true);
        socket.setWantClientAuth(true);
        enableModernTls(socket);
        return socket;
    }

    public SSLSocketFactory createPinnedClientFactory(String expectedFingerprint) throws Exception {
        SSLContext context = createContext(new FingerprintTrustManager(expectedFingerprint));
        return new ModernTlsSocketFactory(context.getSocketFactory());
    }

    public static HostnameVerifier pinnedHostnameVerifier() {
        return new HostnameVerifier() {
            @Override public boolean verify(String hostname, SSLSession session) {
                return true; // 身份由证书指纹固定，不使用局域网 IP 与证书 CN 匹配。
            }
        };
    }

    private SSLContext createContext(X509TrustManager trustManager) throws Exception {
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, null);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), new TrustManager[] { trustManager }, new SecureRandom());
        return context;
    }

    public static String peerFingerprint(SSLSession session) {
        try {
            X509Certificate certificate = (X509Certificate) session.getPeerCertificates()[0];
            return hex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.US, "%02X", value));
        return result.toString();
    }

    private static void enableModernTls(SSLServerSocket socket) {
        List<String> supported = Arrays.asList(socket.getSupportedProtocols());
        List<String> enabled = new ArrayList<String>();
        for (String candidate : new String[] { "TLSv1.2", "TLSv1.1", "TLSv1" }) {
            if (supported.contains(candidate)) enabled.add(candidate);
        }
        if (!enabled.isEmpty()) socket.setEnabledProtocols(enabled.toArray(new String[enabled.size()]));
    }

    private static void enableModernTls(SSLSocket socket) {
        List<String> supported = Arrays.asList(socket.getSupportedProtocols());
        List<String> enabled = new ArrayList<String>();
        for (String candidate : new String[] { "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1" }) {
            if (supported.contains(candidate)) enabled.add(candidate);
        }
        if (!enabled.isEmpty()) socket.setEnabledProtocols(enabled.toArray(new String[enabled.size()]));
    }

    private static final class AcceptAllTrustManager implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { validateSelfSigned(chain); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { validateSelfSigned(chain); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    private static final class FingerprintTrustManager implements X509TrustManager {
        private final String expected;

        FingerprintTrustManager(String expected) {
            this.expected = normalize(expected);
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { validateSelfSigned(chain); }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("对方未提供证书");
            try {
                String actual = hex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));
                if (!actual.equals(expected)) {
                    throw new CertificateException("设备证书指纹不匹配");
                }
            } catch (CertificateException error) {
                throw error;
            } catch (Exception error) {
                throw new CertificateException("无法校验证书指纹", error);
            }
        }

        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }

        private static String normalize(String value) {
            return value == null ? "" : value.replace(":", "").trim().toUpperCase(java.util.Locale.US);
        }
    }

    private static void validateSelfSigned(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) throw new CertificateException("对方未提供证书");
        try {
            chain[0].checkValidity();
            chain[0].verify(chain[0].getPublicKey());
        } catch (Exception error) {
            throw new CertificateException("对方证书无效", error);
        }
    }

    private static final class ModernTlsSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        ModernTlsSocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }

        private Socket configure(Socket socket) {
            if (socket instanceof SSLSocket) enableModernTls((SSLSocket) socket);
            return socket;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(s, host, port, autoClose));
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            return configure(delegate.createSocket(host, port, local, localPort));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress address, int port, InetAddress local, int localPort)
                throws IOException {
            return configure(delegate.createSocket(address, port, local, localPort));
        }
    }
}
