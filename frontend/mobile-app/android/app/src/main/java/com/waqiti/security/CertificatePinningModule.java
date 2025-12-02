package com.waqiti.security;

import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
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
    private String enforcementMode = "strict"; // strict, report, disabled
    private final List<Map<String, Object>> validationReports;
    private OkHttpClient pinnedClient;
    
    // Default certificate pins for Waqiti services
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
        this.certificatePins = new ConcurrentHashMap<>(DEFAULT_PINS);
        this.validationReports = new ArrayList<>();
        initializePinnedClient();
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    private void initializePinnedClient() {
        CertificatePinner.Builder pinnerBuilder = new CertificatePinner.Builder();
        
        // Add all configured pins
        for (Map.Entry<String, List<String>> entry : certificatePins.entrySet()) {
            String hostname = entry.getKey();
            List<String> pins = entry.getValue();
            
            for (String pin : pins) {
                pinnerBuilder.add(hostname, pin);
            }
        }
        
        pinnedClient = new OkHttpClient.Builder()
            .certificatePinner(pinnerBuilder.build())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
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
            initializePinnedClient(); // Reinitialize client with new pins
            savePinsToStorage();
            
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
            initializePinnedClient();
            savePinsToStorage();
            
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
            initializePinnedClient();
            savePinsToStorage();
            
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
                this.enforcementMode = mode;
                
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
            config.putInt("reportCount", validationReports.size());
            
            promise.resolve(config);
        } catch (Exception e) {
            Log.e(TAG, "Get config error", e);
            promise.reject("GET_CONFIG_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void testPinningForHost(String hostname, Promise promise) {
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
                result.putString("error", "Connection failed");
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

    private String calculatePinFromCertificate(String certString) throws Exception {
        byte[] certData = Base64.decode(certString, Base64.DEFAULT);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(certData)
        );
        
        byte[] publicKeyBytes = cert.getPublicKey().getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(publicKeyBytes);
        
        return "sha256/" + Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    private void reportPinningFailure(String hostname, String reason) {
        if ("disabled".equals(enforcementMode)) return;

        Map<String, Object> report = new HashMap<>();
        report.put("timestamp", System.currentTimeMillis() / 1000);
        report.put("hostname", hostname);
        report.put("reason", reason);
        report.put("enforcementMode", enforcementMode);
        report.put("platform", "Android");
        report.put("osVersion", android.os.Build.VERSION.RELEASE);
        
        validationReports.add(report);
        
        // Send event to JavaScript
        WritableMap event = Arguments.createMap();
        event.putDouble("timestamp", (Double) report.get("timestamp"));
        event.putString("hostname", hostname);
        event.putString("reason", reason);
        event.putString("enforcementMode", enforcementMode);
        event.putString("platform", "Android");
        event.putString("osVersion", android.os.Build.VERSION.RELEASE);
        
        sendEvent("CertificatePinningFailure", event);
        
        // Send report to server
        sendReportToServer(report);
    }

    private void sendEvent(String eventName, WritableMap params) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        }
    }

    private void sendReportToServer(Map<String, Object> report) {
        // Implementation for sending security reports to server
        // This should be done asynchronously in production
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                String json = new org.json.JSONObject(report).toString();
                
                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    json, okhttp3.MediaType.parse("application/json")
                );
                
                Request request = new Request.Builder()
                    .url("https://security.example.com/pinning-report")
                    .post(body)
                    .build();
                
                client.newCall(request).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send report to server", e);
            }
        }).start();
    }

    private void savePinsToStorage() {
        // Save pins to SharedPreferences or encrypted storage
        try {
            android.content.SharedPreferences prefs = reactContext
                .getSharedPreferences("certificate_pins", android.content.Context.MODE_PRIVATE);
            
            org.json.JSONObject pinsJson = new org.json.JSONObject();
            for (Map.Entry<String, List<String>> entry : certificatePins.entrySet()) {
                org.json.JSONArray pinsArray = new org.json.JSONArray(entry.getValue());
                pinsJson.put(entry.getKey(), pinsArray);
            }
            
            prefs.edit()
                .putString("pins", pinsJson.toString())
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save pins", e);
        }
    }

    private void loadPinsFromStorage() {
        try {
            android.content.SharedPreferences prefs = reactContext
                .getSharedPreferences("certificate_pins", android.content.Context.MODE_PRIVATE);
            
            String pinsString = prefs.getString("pins", null);
            if (pinsString != null) {
                org.json.JSONObject pinsJson = new org.json.JSONObject(pinsString);
                
                certificatePins.clear();
                java.util.Iterator<String> keys = pinsJson.keys();
                while (keys.hasNext()) {
                    String hostname = keys.next();
                    org.json.JSONArray pinsArray = pinsJson.getJSONArray(hostname);
                    
                    List<String> pins = new ArrayList<>();
                    for (int i = 0; i < pinsArray.length(); i++) {
                        pins.add(pinsArray.getString(i));
                    }
                    
                    certificatePins.put(hostname, pins);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load pins", e);
            // Fall back to default pins
            certificatePins.putAll(DEFAULT_PINS);
        }
    }
    
    // Public method to get the pinned OkHttpClient for use in networking
    public OkHttpClient getPinnedClient() {
        return pinnedClient;
    }
}