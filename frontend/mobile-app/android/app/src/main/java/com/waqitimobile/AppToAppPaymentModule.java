package com.waqitimobile;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.List;

/**
 * App-to-App Payment Module for React Native
 * Handles communication with external payment apps and app detection
 */
public class AppToAppPaymentModule extends ReactContextBaseJavaModule {
    
    private static final String MODULE_NAME = "AppToAppPaymentModule";
    private static final String TAG = "AppToAppPaymentModule";

    public AppToAppPaymentModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    @NonNull
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Check if a specific app is installed by package name
     */
    @ReactMethod
    public void isAppInstalled(String packageName, Promise promise) {
        try {
            PackageManager packageManager = getReactApplicationContext().getPackageManager();
            
            try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                Log.d(TAG, "App " + packageName + " is installed");
                promise.resolve(true);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "App " + packageName + " is not installed");
                promise.resolve(false);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking if app is installed", e);
            promise.reject("APP_CHECK_ERROR", "Error checking if app is installed: " + e.getMessage(), e);
        }
    }

    /**
     * Get list of installed payment apps
     */
    @ReactMethod
    public void getInstalledPaymentApps(Promise promise) {
        try {
            PackageManager packageManager = getReactApplicationContext().getPackageManager();
            
            // Common payment app package names
            String[] paymentApps = {
                "com.venmo",                           // Venmo
                "com.squareup.cash",                   // Cash App
                "com.paypal.android.p2pmobile",       // PayPal
                "com.zellepay.zelle",                  // Zelle
                "com.google.android.apps.nbu.paisa.user", // Google Pay
            };
            
            WritableMap installedApps = Arguments.createMap();
            
            for (String packageName : paymentApps) {
                try {
                    packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                    installedApps.putBoolean(packageName, true);
                    Log.d(TAG, "Payment app installed: " + packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    installedApps.putBoolean(packageName, false);
                }
            }
            
            promise.resolve(installedApps);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed payment apps", e);
            promise.reject("PAYMENT_APPS_ERROR", "Error getting installed payment apps: " + e.getMessage(), e);
        }
    }

    /**
     * Check if an intent can be resolved (app can handle the intent)
     */
    @ReactMethod
    public void canHandleIntent(String action, String data, Promise promise) {
        try {
            Intent intent = new Intent(action);
            if (data != null && !data.isEmpty()) {
                intent.setData(Uri.parse(data));
            }
            
            PackageManager packageManager = getReactApplicationContext().getPackageManager();
            List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
            
            boolean canHandle = !activities.isEmpty();
            Log.d(TAG, "Intent " + action + " can be handled: " + canHandle);
            promise.resolve(canHandle);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking intent handling", e);
            promise.reject("INTENT_CHECK_ERROR", "Error checking intent handling: " + e.getMessage(), e);
        }
    }

    /**
     * Create Google Pay payment request data for app-to-app communication
     */
    @ReactMethod
    public void createGooglePayRequest(ReadableMap requestData, Promise promise) {
        try {
            WritableMap googlePayData = Arguments.createMap();
            
            // Extract data from request
            double amount = requestData.hasKey("amount") ? requestData.getDouble("amount") : 0.0;
            String currency = requestData.hasKey("currency") ? requestData.getString("currency") : "USD";
            String merchantName = requestData.hasKey("merchantName") ? requestData.getString("merchantName") : "Waqiti";
            String requestId = requestData.hasKey("requestId") ? requestData.getString("requestId") : "";
            
            // Create Google Pay specific payment data structure
            googlePayData.putDouble("amount", amount);
            googlePayData.putString("currency", currency);
            googlePayData.putString("merchantName", merchantName);
            googlePayData.putString("requestId", requestId);
            googlePayData.putString("environment", "PRODUCTION"); // Change to TEST for testing
            
            // Add merchant info
            WritableMap merchantInfo = Arguments.createMap();
            merchantInfo.putString("merchantName", merchantName);
            merchantInfo.putString("merchantId", "waqiti-payments");
            googlePayData.putMap("merchantInfo", merchantInfo);
            
            // Add transaction info
            WritableMap transactionInfo = Arguments.createMap();
            transactionInfo.putString("totalPrice", String.valueOf(amount));
            transactionInfo.putString("totalPriceStatus", "FINAL");
            transactionInfo.putString("currencyCode", currency);
            googlePayData.putMap("transactionInfo", transactionInfo);
            
            Log.d(TAG, "Created Google Pay request for amount: " + amount);
            promise.resolve(googlePayData);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating Google Pay request", e);
            promise.reject("GOOGLE_PAY_REQUEST_ERROR", "Error creating Google Pay request: " + e.getMessage(), e);
        }
    }

    /**
     * Create Apple Pay request data for app-to-app communication (for consistency)
     */
    @ReactMethod
    public void createApplePayRequest(ReadableMap requestData, Promise promise) {
        try {
            WritableMap applePayData = Arguments.createMap();
            
            // Extract data from request
            double amount = requestData.hasKey("amount") ? requestData.getDouble("amount") : 0.0;
            String currency = requestData.hasKey("currency") ? requestData.getString("currency") : "USD";
            String merchantId = requestData.hasKey("merchantId") ? requestData.getString("merchantId") : "merchant.com.waqiti";
            String requestId = requestData.hasKey("requestId") ? requestData.getString("requestId") : "";
            
            // Create Apple Pay specific payment data structure
            applePayData.putString("merchantIdentifier", merchantId);
            applePayData.putString("currencyCode", currency);
            applePayData.putString("countryCode", "US");
            applePayData.putString("requestId", requestId);
            
            // Add payment summary item
            WritableMap paymentItem = Arguments.createMap();
            paymentItem.putString("label", "Waqiti Payment");
            paymentItem.putString("amount", String.valueOf(amount));
            paymentItem.putString("type", "final");
            
            applePayData.putMap("paymentItem", paymentItem);
            
            Log.d(TAG, "Created Apple Pay request for amount: " + amount);
            promise.resolve(applePayData);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating Apple Pay request", e);
            promise.reject("APPLE_PAY_REQUEST_ERROR", "Error creating Apple Pay request: " + e.getMessage(), e);
        }
    }

    /**
     * Launch external app with specific intent
     */
    @ReactMethod
    public void launchApp(String packageName, String action, String data, Promise promise) {
        try {
            PackageManager packageManager = getReactApplicationContext().getPackageManager();
            Intent intent;
            
            if (action != null && !action.isEmpty()) {
                intent = new Intent(action);
                if (data != null && !data.isEmpty()) {
                    intent.setData(Uri.parse(data));
                }
            } else {
                // Launch app's main activity
                intent = packageManager.getLaunchIntentForPackage(packageName);
            }
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getReactApplicationContext().startActivity(intent);
                Log.d(TAG, "Successfully launched app: " + packageName);
                promise.resolve(true);
            } else {
                Log.e(TAG, "Could not create intent for app: " + packageName);
                promise.reject("LAUNCH_FAILED", "Could not create intent for app: " + packageName);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching app", e);
            promise.reject("LAUNCH_ERROR", "Error launching app: " + e.getMessage(), e);
        }
    }

    /**
     * Get app information by package name
     */
    @ReactMethod
    public void getAppInfo(String packageName, Promise promise) {
        try {
            PackageManager packageManager = getReactApplicationContext().getPackageManager();
            
            try {
                android.content.pm.PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                android.content.pm.ApplicationInfo appInfo = packageInfo.applicationInfo;
                
                WritableMap appInfoMap = Arguments.createMap();
                appInfoMap.putString("packageName", packageName);
                appInfoMap.putString("appName", packageManager.getApplicationLabel(appInfo).toString());
                appInfoMap.putString("versionName", packageInfo.versionName);
                appInfoMap.putInt("versionCode", packageInfo.versionCode);
                appInfoMap.putBoolean("isInstalled", true);
                
                Log.d(TAG, "Retrieved app info for: " + packageName);
                promise.resolve(appInfoMap);
                
            } catch (PackageManager.NameNotFoundException e) {
                WritableMap appInfoMap = Arguments.createMap();
                appInfoMap.putString("packageName", packageName);
                appInfoMap.putBoolean("isInstalled", false);
                promise.resolve(appInfoMap);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting app info", e);
            promise.reject("APP_INFO_ERROR", "Error getting app info: " + e.getMessage(), e);
        }
    }

    /**
     * Send event to JavaScript layer
     */
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    /**
     * Notify JavaScript of payment response (called from deep link handler)
     */
    @ReactMethod
    public void notifyPaymentResponse(ReadableMap responseData) {
        try {
            WritableMap response = Arguments.createMap();
            
            if (responseData.hasKey("requestId")) {
                response.putString("requestId", responseData.getString("requestId"));
            }
            if (responseData.hasKey("status")) {
                response.putString("status", responseData.getString("status"));
            }
            if (responseData.hasKey("transactionId")) {
                response.putString("transactionId", responseData.getString("transactionId"));
            }
            if (responseData.hasKey("error")) {
                response.putMap("error", Arguments.fromBundle(responseData.getMap("error").toHashMap()));
            }
            
            response.putString("timestamp", String.valueOf(System.currentTimeMillis()));
            
            sendEvent("onPaymentResponse", response);
            Log.d(TAG, "Notified JavaScript of payment response");
            
        } catch (Exception e) {
            Log.e(TAG, "Error notifying payment response", e);
        }
    }

    /**
     * Notify JavaScript of app installation change
     */
    @ReactMethod
    public void notifyAppInstallationChange(String appId, boolean installed) {
        try {
            WritableMap data = Arguments.createMap();
            data.putString("appId", appId);
            data.putBoolean("installed", installed);
            
            sendEvent("onAppInstallationChanged", data);
            Log.d(TAG, "Notified JavaScript of app installation change: " + appId + " -> " + installed);
            
        } catch (Exception e) {
            Log.e(TAG, "Error notifying app installation change", e);
        }
    }
}