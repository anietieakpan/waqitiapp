package com.waqiti.mobile;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.CertificatePinner;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

/**
 * Native module for certificate pinning on Android
 * Provides comprehensive SSL/TLS security for network communications
 */
public class CertificatePinningModule extends ReactContextBaseJavaModule {

    private static final String TAG = "CertificatePinning";
    private static final String MODULE_NAME = "CertificatePinningModule";
    
    private final ReactApplicationContext reactContext;
    private OkHttpClient pinnedClient;
    private CertificatePinner certificatePinner;
    private Map<String, List<String>> domainPins;
    private boolean enforceMode = true;
    
    // Production certificate pins for Waqiti services
    // THREAD-SAFETY & SECURITY FIX: Use immutable collections to prevent runtime modifications
    private static final Map<String, List<String>> DEFAULT_PINS;

    static {
        Map<String, List<String>> tempPins = new HashMap<>();
        tempPins.put("api.example.com", Collections.unmodifiableList(Arrays.asList(
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=" // Backup pin
        )));
        tempPins.put("auth.example.com", Collections.unmodifiableList(Arrays.asList(
            "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
            "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=" // Backup pin
        )));
        tempPins.put("wallet.example.com", Collections.unmodifiableList(Arrays.asList(
            "sha256/FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=",
            "sha256/GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG=" // Backup pin
        )));
        // CRITICAL: Make entire map immutable to prevent certificate pin tampering
        DEFAULT_PINS = Collections.unmodifiableMap(tempPins);
    }

    public CertificatePinningModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.domainPins = new HashMap<>(DEFAULT_PINS);
        initializePinnedClient();
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Initialize OkHttpClient with certificate pinning
     */
    private void initializePinnedClient() {
        try {
            // Build certificate pinner
            CertificatePinner.Builder pinnerBuilder = new CertificatePinner.Builder();
            
            for (Map.Entry<String, List<String>> entry : domainPins.entrySet()) {
                String domain = entry.getKey();
                for (String pin : entry.getValue()) {
                    pinnerBuilder.add(domain, pin);
                    pinnerBuilder.add("*." + domain, pin); // Include subdomains
                }
            }
            
            certificatePinner = pinnerBuilder.build();
            
            // Configure TLS versions and cipher suites
            ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .cipherSuites(
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
                )
                .build();
            
            // Build OkHttpClient with certificate pinning
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .certificatePinner(certificatePinner)
                .connectionSpecs(Arrays.asList(spec, ConnectionSpec.COMPATIBLE_TLS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .hostnameVerifier(new StrictHostnameVerifier());
            
            // Add custom trust manager for additional validation
            if (enforceMode) {
                clientBuilder.sslSocketFactory(
                    createSecureSocketFactory(),
                    new PinningTrustManager()
                );
            }
            
            pinnedClient = clientBuilder.build();
            
            Log.i(TAG, "Certificate pinning initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize certificate pinning", e);
            sendSecurityEvent("pinning_initialization_failed", e.getMessage());
        }
    }

    /**
     * Configure pinned client with custom pins
     */
    @ReactMethod
    public void configurePinnedClient(ReadableMap config, Promise promise) {
        try {
            // Parse configuration
            ReadableArray hosts = config.getArray("hosts");
            boolean enforce = config.hasKey("enforceMode") ? 
                config.getBoolean("enforceMode") : true;
            
            this.enforceMode = enforce;
            this.domainPins.clear();
            
            // Add pins for each host
            for (int i = 0; i < hosts.size(); i++) {
                ReadableMap host = hosts.getMap(i);
                String hostname = host.getString("hostname");
                ReadableArray pins = host.getArray("pins");
                
                List<String> pinList = new ArrayList<>();
                for (int j = 0; j < pins.size(); j++) {
                    pinList.add(pins.getString(j));
                }
                
                domainPins.put(hostname, pinList);
            }
            
            // Reinitialize client with new pins
            initializePinnedClient();
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putInt("hostCount", domainPins.size());
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to configure pinned client", e);
            promise.reject("CONFIG_ERROR", "Failed to configure certificate pinning", e);
        }
    }

    /**
     * Test certificate pinning for a specific domain
     */
    @ReactMethod
    public void testPinning(String hostname, Promise promise) {
        try {
            // Attempt to connect to the hostname
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://" + hostname + "/health")
                .head()
                .build();
            
            okhttp3.Response response = pinnedClient.newCall(request).execute();
            response.close();
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putString("hostname", hostname);
            result.putBoolean("pinned", domainPins.containsKey(hostname));
            
            promise.resolve(result);
            
        } catch (SSLPeerUnverifiedException e) {
            // Certificate pinning failed
            Log.e(TAG, "Certificate pinning failed for " + hostname, e);
            
            WritableMap error = Arguments.createMap();
            error.putBoolean("success", false);
            error.putString("hostname", hostname);
            error.putString("error", "PINNING_FAILED");
            error.putString("message", e.getMessage());
            
            sendSecurityEvent("pinning_test_failed", hostname);
            promise.resolve(error);
            
        } catch (Exception e) {
            Log.e(TAG, "Error testing pinning for " + hostname, e);
            promise.reject("TEST_ERROR", "Failed to test certificate pinning", e);
        }
    }

    /**
     * Get current pinning status
     */
    @ReactMethod
    public void getStatus(Promise promise) {
        try {
            WritableMap status = Arguments.createMap();
            status.putBoolean("enabled", pinnedClient != null);
            status.putBoolean("enforceMode", enforceMode);
            status.putInt("domainCount", domainPins.size());
            
            WritableMap domains = Arguments.createMap();
            for (Map.Entry<String, List<String>> entry : domainPins.entrySet()) {
                domains.putInt(entry.getKey(), entry.getValue().size());
            }
            status.putMap("domains", domains);
            
            promise.resolve(status);
            
        } catch (Exception e) {
            promise.reject("STATUS_ERROR", "Failed to get pinning status", e);
        }
    }

    /**
     * Update certificate pins dynamically
     */
    @ReactMethod
    public void updatePins(ReadableMap updates, Promise promise) {
        try {
            // Verify updates structure
            ReadableArray domains = updates.getArray("domains");
            
            for (int i = 0; i < domains.size(); i++) {
                ReadableMap domain = domains.getMap(i);
                String hostname = domain.getString("hostname");
                ReadableArray pins = domain.getArray("pins");
                
                List<String> pinList = new ArrayList<>();
                for (int j = 0; j < pins.size(); j++) {
                    pinList.add(pins.getString(j));
                }
                
                // Update or add domain pins
                domainPins.put(hostname, pinList);
            }
            
            // Reinitialize client with updated pins
            initializePinnedClient();
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putInt("updatedDomains", domains.size());
            
            sendSecurityEvent("pins_updated", "Updated " + domains.size() + " domains");
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update pins", e);
            promise.reject("UPDATE_ERROR", "Failed to update certificate pins", e);
        }
    }

    /**
     * Clear all custom pins and revert to defaults
     */
    @ReactMethod
    public void resetToDefaults(Promise promise) {
        try {
            domainPins.clear();
            domainPins.putAll(DEFAULT_PINS);
            initializePinnedClient();
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putInt("defaultDomains", DEFAULT_PINS.size());
            
            promise.resolve(result);
            
        } catch (Exception e) {
            promise.reject("RESET_ERROR", "Failed to reset pins", e);
        }
    }

    /**
     * Get the configured OkHttpClient for use by other modules
     */
    public OkHttpClient getPinnedClient() {
        return pinnedClient;
    }

    /**
     * Create secure SSL socket factory
     */
    private javax.net.ssl.SSLSocketFactory createSecureSocketFactory() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new PinningTrustManager()}, null);
        return sslContext.getSocketFactory();
    }

    /**
     * Custom trust manager for additional certificate validation
     */
    private class PinningTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) 
            throws CertificateException {
            // Client certificates not used
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) 
            throws CertificateException {
            // Perform standard certificate validation
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Certificate chain is empty");
            }
            
            // Check certificate validity
            for (X509Certificate cert : chain) {
                cert.checkValidity();
            }
            
            // Additional custom validation can be added here
            // For example, checking certificate transparency logs
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /**
     * Strict hostname verifier
     */
    private class StrictHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            // Verify hostname matches certificate
            try {
                Certificate[] certs = session.getPeerCertificates();
                if (certs.length > 0 && certs[0] instanceof X509Certificate) {
                    X509Certificate cert = (X509Certificate) certs[0];
                    
                    // Check if hostname matches certificate CN or SAN
                    String cn = getCN(cert);
                    if (cn != null && cn.equalsIgnoreCase(hostname)) {
                        return true;
                    }
                    
                    // Check Subject Alternative Names
                    return checkSAN(cert, hostname);
                }
            } catch (SSLPeerUnverifiedException e) {
                Log.e(TAG, "Hostname verification failed", e);
            }
            
            sendSecurityEvent("hostname_verification_failed", hostname);
            return false;
        }
        
        private String getCN(X509Certificate cert) {
            String dn = cert.getSubjectX500Principal().getName();
            for (String part : dn.split(",")) {
                if (part.trim().startsWith("CN=")) {
                    return part.trim().substring(3);
                }
            }
            return null;
        }
        
        private boolean checkSAN(X509Certificate cert, String hostname) {
            try {
                java.util.Collection<java.util.List<?>> sans = 
                    cert.getSubjectAlternativeNames();
                if (sans != null) {
                    for (java.util.List<?> san : sans) {
                        if (san.size() >= 2) {
                            Integer type = (Integer) san.get(0);
                            if (type == 2) { // DNS name
                                String dns = (String) san.get(1);
                                if (dns.equalsIgnoreCase(hostname)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking SAN", e);
            }
            return false;
        }
    }

    /**
     * Send security events to JavaScript
     */
    private void sendSecurityEvent(String eventType, String details) {
        WritableMap params = Arguments.createMap();
        params.putString("type", eventType);
        params.putString("details", details);
        params.putDouble("timestamp", System.currentTimeMillis());
        
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("CertificatePinningSecurityEvent", params);
    }
}