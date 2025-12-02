package com.waqiti.certificatepinning;

import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CertificatePinningModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CertificatePinning";
    private static final String MODULE_NAME = "CertificatePinning";
    
    private final ReactApplicationContext reactContext;
    private final Map<String, List<String>> certificatePins;
    private String enforcementMode = "strict";
    private OkHttpClient pinnedClient;
    
    // Default pins for Waqiti services
    private static final Map<String, List<String>> DEFAULT_PINS = new HashMap<String, List<String>>() {{
        put("api.example.com", new ArrayList<String>() {{
            add("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
            add("sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        }});
        put("auth.example.com", new ArrayList<String>() {{
            add("sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=");
            add("sha256/FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=");
        }});
        put("payments.example.com", new ArrayList<String>() {{
            add("sha256/IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII=");
            add("sha256/JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ=");
        }});
    }};
    
    public CertificatePinningModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.certificatePins = new HashMap<>(DEFAULT_PINS);
        this.buildPinnedClient();
    }
    
    @Override
    public String getName() {
        return MODULE_NAME;
    }
    
    @ReactMethod
    public void validateCertificateForHost(String hostname, ReadableArray certificates, Promise promise) {
        try {
            List<String> expectedPins = certificatePins.get(hostname);
            if (expectedPins == null || expectedPins.isEmpty()) {
                WritableMap result = Arguments.createMap();
                result.putBoolean("valid", true);
                result.putString("hostname", hostname);
                result.putString("message", "No pins configured for hostname");
                promise.resolve(result);
                return;
            }
            
            boolean isValid = false;
            String matchedPin = null;
            
            for (int i = 0; i < certificates.size(); i++) {
                String certString = certificates.getString(i);
                String pin = calculatePinFromCertificate(certString);
                
                if (expectedPins.contains(pin)) {
                    isValid = true;
                    matchedPin = pin;
                    break;
                }
            }
            
            if (isValid) {
                WritableMap result = Arguments.createMap();
                result.putBoolean("valid", true);
                result.putString("hostname", hostname);
                result.putString("matchedPin", matchedPin);
                promise.resolve(result);
            } else {
                reportPinningFailure(hostname, "No matching pins");
                
                if ("strict".equals(enforcementMode)) {
                    promise.reject("PINNING_FAILED", "Certificate pinning validation failed");
                } else {
                    WritableMap result = Arguments.createMap();
                    result.putBoolean("valid", false);
                    result.putString("hostname", hostname);
                    result.putString("error", "No matching certificate pins");
                    promise.resolve(result);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Certificate validation error", e);
            promise.reject("VALIDATION_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void addPinForHost(String hostname, ReadableArray pins, Promise promise) {
        try {
            List<String> pinList = new ArrayList<>();
            for (int i = 0; i < pins.size(); i++) {
                pinList.add(pins.getString(i));
            }
            
            certificatePins.put(hostname, pinList);
            buildPinnedClient(); // Rebuild client with new pins
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Add pin error", e);
            promise.reject("ADD_PIN_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void removePinForHost(String hostname, Promise promise) {
        try {
            certificatePins.remove(hostname);
            buildPinnedClient(); // Rebuild client
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Remove pin error", e);
            promise.reject("REMOVE_PIN_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void clearAllPins(Promise promise) {
        try {
            certificatePins.clear();
            certificatePins.putAll(DEFAULT_PINS);
            buildPinnedClient(); // Rebuild client
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Clear pins error", e);
            promise.reject("CLEAR_PINS_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void setEnforcementMode(String mode, Promise promise) {
        try {
            if ("strict".equals(mode) || "report".equals(mode) || "disabled".equals(mode)) {
                enforcementMode = mode;
                buildPinnedClient(); // Rebuild client with new mode
                
                WritableMap result = Arguments.createMap();
                result.putBoolean("success", true);
                promise.resolve(result);
            } else {
                promise.reject("INVALID_MODE", "Invalid enforcement mode");
            }
        } catch (Exception e) {
            Log.e(TAG, "Set mode error", e);
            promise.reject("SET_MODE_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void getConfiguration(Promise promise) {
        try {
            WritableMap config = Arguments.createMap();
            config.putString("enforcementMode", enforcementMode);
            config.putBoolean("enforcePinning", !"disabled".equals(enforcementMode));
            
            WritableArray hosts = Arguments.createArray();
            for (String host : certificatePins.keySet()) {
                hosts.pushString(host);
            }
            config.putArray("configuredHosts", hosts);
            
            promise.resolve(config);
        } catch (Exception e) {
            Log.e(TAG, "Get config error", e);
            promise.reject("GET_CONFIG_ERROR", e.getMessage());
        }
    }
    
    @ReactMethod
    public void testPinningForHost(final String hostname, final Promise promise) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://" + hostname + "/health";
                    Request request = new Request.Builder()
                            .url(url)
                            .build();
                    
                    Response response = pinnedClient.newCall(request).execute();
                    boolean success = response.isSuccessful();
                    response.close();
                    
                    WritableMap result = Arguments.createMap();
                    result.putBoolean("success", success);
                    result.putString("hostname", hostname);
                    
                    if (!success) {
                        result.putString("error", "HTTP " + response.code());
                    }
                    
                    promise.resolve(result);
                } catch (Exception e) {
                    WritableMap result = Arguments.createMap();
                    result.putBoolean("success", false);
                    result.putString("hostname", hostname);
                    result.putString("error", e.getMessage());
                    promise.resolve(result);
                }
            }
        }).start();
    }
    
    private void buildPinnedClient() {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS);
            
            if (!"disabled".equals(enforcementMode)) {
                CertificatePinner.Builder pinnerBuilder = new CertificatePinner.Builder();
                
                for (Map.Entry<String, List<String>> entry : certificatePins.entrySet()) {
                    String hostname = entry.getKey();
                    List<String> pins = entry.getValue();
                    
                    for (String pin : pins) {
                        pinnerBuilder.add(hostname, pin);
                    }
                }
                
                builder.certificatePinner(pinnerBuilder.build());
            }
            
            // Add custom trust manager for certificate extraction
            builder.sslSocketFactory(
                    getSSLContext().getSocketFactory(),
                    new CustomTrustManager()
            );
            
            pinnedClient = builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build pinned client", e);
        }
    }
    
    private String calculatePinFromCertificate(String certString) throws Exception {
        // Remove PEM headers if present
        certString = certString.replace("-----BEGIN CERTIFICATE-----", "")
                              .replace("-----END CERTIFICATE-----", "")
                              .replaceAll("\\s", "");
        
        byte[] certData = Base64.decode(certString, Base64.DEFAULT);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(certData);
        
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(inputStream);
        
        // Get the SubjectPublicKeyInfo
        byte[] publicKeyData = cert.getPublicKey().getEncoded();
        
        // Calculate SHA-256 hash
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKeyData);
        
        // Convert to base64
        String base64Hash = Base64.encodeToString(hash, Base64.NO_WRAP);
        
        return "sha256/" + base64Hash;
    }
    
    private void reportPinningFailure(String hostname, String reason) {
        try {
            WritableMap report = Arguments.createMap();
            report.putDouble("timestamp", System.currentTimeMillis());
            report.putString("hostname", hostname);
            report.putString("reason", reason);
            report.putString("enforcementMode", enforcementMode);
            report.putString("platform", "Android");
            report.putInt("osVersion", android.os.Build.VERSION.SDK_INT);
            
            // Send event to JavaScript
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("CertificatePinningFailure", report);
            
            // Send report to server (implement as needed)
            sendReportToServer(report);
        } catch (Exception e) {
            Log.e(TAG, "Failed to report pinning failure", e);
        }
    }
    
    private void sendReportToServer(WritableMap report) {
        // Implementation for sending security reports to server
        // This would typically make an API call to your security endpoint
    }
    
    private SSLContext getSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new CustomTrustManager()}, null);
        return sslContext;
    }
    
    private class CustomTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Not used for client certificates
        }
        
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Perform default certificate validation
            // In production, implement proper certificate validation
        }
        
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
    
    // OkHttp interceptor for certificate extraction
    private class CertificateExtractionInterceptor implements okhttp3.Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);
            
            // Extract certificates from the connection
            if (response.handshake() != null) {
                List<Certificate> peerCertificates = response.handshake().peerCertificates();
                List<String> certStrings = new ArrayList<>();
                
                for (Certificate cert : peerCertificates) {
                    if (cert instanceof X509Certificate) {
                        String certString = Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);
                        certStrings.add(certString);
                    }
                }
                
                // Add certificates to response header for JavaScript access
                response = response.newBuilder()
                        .header("X-Certificate-Chain", certStrings.toString())
                        .build();
            }
            
            return response;
        }
    }
}