package com.waqitimobile;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Google Pay Integration Module for React Native
 * Provides native Android integration with Google Pay API
 */
public class GooglePayModule extends ReactContextBaseJavaModule {
    
    private static final String MODULE_NAME = "GooglePay";
    private static final String TAG = "GooglePayModule";
    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;
    
    private PaymentsClient paymentsClient;
    private Promise currentPromise;
    
    private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
                handlePaymentResult(resultCode, intent);
            }
        }
    };

    public GooglePayModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(activityEventListener);
    }

    @Override
    @NonNull
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Initialize Google Pay client
     */
    @ReactMethod
    public void initialize(Promise promise) {
        try {
            // Create payments client for production environment
            // Change to WalletConstants.ENVIRONMENT_TEST for testing
            paymentsClient = Wallet.getPaymentsClient(
                getCurrentActivity(),
                new Wallet.WalletOptions.Builder()
                    .setEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)
                    .build()
            );
            
            Log.d(TAG, "Google Pay client initialized successfully");
            promise.resolve(true);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Google Pay client", e);
            promise.reject("INIT_FAILED", "Failed to initialize Google Pay: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Google Pay is ready to use
     */
    @ReactMethod
    public void isReadyToPay(ReadableMap request, Promise promise) {
        try {
            if (paymentsClient == null) {
                promise.reject("NOT_INITIALIZED", "Google Pay client not initialized");
                return;
            }

            // Convert ReadableMap to JSON
            JSONObject requestJson = convertMapToJson(request);
            IsReadyToPayRequest readyToPayRequest = IsReadyToPayRequest.fromJson(requestJson.toString());

            Task<Boolean> task = paymentsClient.isReadyToPay(readyToPayRequest);
            task.addOnCompleteListener(getCurrentActivity(), new OnCompleteListener<Boolean>() {
                @Override
                public void onComplete(@NonNull Task<Boolean> task) {
                    if (task.isSuccessful()) {
                        WritableMap result = Arguments.createMap();
                        result.putBoolean("result", task.getResult());
                        result.putBoolean("existingPaymentMethodRequired", false);
                        promise.resolve(result);
                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof ApiException) {
                            ApiException apiException = (ApiException) exception;
                            Log.e(TAG, "Google Pay readiness check failed", apiException);
                            promise.reject("READINESS_CHECK_FAILED", 
                                "Google Pay readiness check failed: " + apiException.getMessage(), 
                                apiException);
                        } else {
                            Log.e(TAG, "Google Pay readiness check failed", exception);
                            promise.reject("READINESS_CHECK_FAILED", 
                                "Google Pay readiness check failed: " + exception.getMessage(), 
                                exception);
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error checking Google Pay readiness", e);
            promise.reject("READINESS_CHECK_ERROR", "Error checking Google Pay readiness: " + e.getMessage(), e);
        }
    }

    /**
     * Load payment data (present payment sheet)
     */
    @ReactMethod
    public void loadPaymentData(ReadableMap request, Promise promise) {
        try {
            if (paymentsClient == null) {
                promise.reject("NOT_INITIALIZED", "Google Pay client not initialized");
                return;
            }

            Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                promise.reject("NO_ACTIVITY", "No current activity available");
                return;
            }

            currentPromise = promise;

            // Convert ReadableMap to JSON
            JSONObject requestJson = convertMapToJson(request);
            PaymentDataRequest paymentDataRequest = PaymentDataRequest.fromJson(requestJson.toString());

            // Launch Google Pay payment sheet
            AutoResolveHelper.resolveTask(
                paymentsClient.loadPaymentData(paymentDataRequest),
                currentActivity,
                LOAD_PAYMENT_DATA_REQUEST_CODE
            );

        } catch (Exception e) {
            Log.e(TAG, "Error loading payment data", e);
            promise.reject("LOAD_PAYMENT_DATA_ERROR", "Error loading payment data: " + e.getMessage(), e);
        }
    }

    /**
     * Handle the payment result from Google Pay
     */
    private void handlePaymentResult(int resultCode, Intent intent) {
        if (currentPromise == null) {
            Log.e(TAG, "No current promise to resolve");
            return;
        }

        switch (resultCode) {
            case Activity.RESULT_OK:
                try {
                    PaymentData paymentData = PaymentData.getFromIntent(intent);
                    if (paymentData != null) {
                        String paymentDataJson = paymentData.toJson();
                        
                        // Parse the payment data JSON and convert to WritableMap
                        JSONObject paymentDataObj = new JSONObject(paymentDataJson);
                        WritableMap result = convertJsonToMap(paymentDataObj);
                        
                        Log.d(TAG, "Payment successful");
                        currentPromise.resolve(result);
                        
                        // Send event to JavaScript
                        sendEvent("GooglePayPaymentSuccess", result);
                        
                    } else {
                        Log.e(TAG, "Payment data is null");
                        currentPromise.reject("PAYMENT_DATA_NULL", "Payment data is null");
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing payment data JSON", e);
                    currentPromise.reject("JSON_PARSE_ERROR", "Error parsing payment data: " + e.getMessage(), e);
                } catch (Exception e) {
                    Log.e(TAG, "Error handling payment result", e);
                    currentPromise.reject("PAYMENT_RESULT_ERROR", "Error handling payment result: " + e.getMessage(), e);
                }
                break;
                
            case Activity.RESULT_CANCELED:
                Log.d(TAG, "Payment cancelled by user");
                currentPromise.reject("PAYMENT_CANCELLED", "Payment cancelled by user");
                sendEvent("GooglePayPaymentCancelled", null);
                break;
                
            case AutoResolveHelper.RESULT_ERROR:
                try {
                    ApiException apiException = AutoResolveHelper.getStatusFromIntent(intent);
                    Log.e(TAG, "Payment failed with error: " + apiException.getStatusCode());
                    
                    WritableMap errorData = Arguments.createMap();
                    errorData.putInt("statusCode", apiException.getStatusCode());
                    errorData.putString("statusMessage", apiException.getStatusMessage());
                    
                    currentPromise.reject("PAYMENT_ERROR", "Payment failed: " + apiException.getStatusMessage());
                    sendEvent("GooglePayPaymentError", errorData);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error handling payment error", e);
                    currentPromise.reject("PAYMENT_ERROR_HANDLING", "Error handling payment error: " + e.getMessage(), e);
                }
                break;
                
            default:
                Log.e(TAG, "Unexpected result code: " + resultCode);
                currentPromise.reject("UNEXPECTED_RESULT", "Unexpected result code: " + resultCode);
                break;
        }
        
        currentPromise = null;
    }

    /**
     * Create base card payment method for readiness check
     */
    @ReactMethod
    public void createBaseCardPaymentMethod(Promise promise) {
        try {
            JSONObject cardPaymentMethod = new JSONObject();
            cardPaymentMethod.put("type", "CARD");
            
            JSONObject parameters = new JSONObject();
            parameters.put("allowedAuthMethods", new JSONArray().put("PAN_ONLY").put("CRYPTOGRAM_3DS"));
            parameters.put("allowedCardNetworks", new JSONArray()
                .put("AMEX")
                .put("DISCOVER")
                .put("MASTERCARD")
                .put("VISA"));
            parameters.put("allowPrepaidCards", true);
            parameters.put("allowCreditCards", true);
            
            cardPaymentMethod.put("parameters", parameters);
            
            JSONObject tokenizationSpec = new JSONObject();
            tokenizationSpec.put("type", "PAYMENT_GATEWAY");
            
            JSONObject gatewayParams = new JSONObject();
            gatewayParams.put("gateway", "waqiti");
            gatewayParams.put("gatewayMerchantId", "waqiti-payments");
            
            tokenizationSpec.put("parameters", gatewayParams);
            cardPaymentMethod.put("tokenizationSpecification", tokenizationSpec);
            
            WritableMap result = convertJsonToMap(cardPaymentMethod);
            promise.resolve(result);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating base card payment method", e);
            promise.reject("CREATE_PAYMENT_METHOD_ERROR", "Error creating payment method: " + e.getMessage(), e);
        }
    }

    /**
     * Convert ReadableMap to JSONObject
     */
    private JSONObject convertMapToJson(ReadableMap readableMap) throws JSONException {
        JSONObject object = new JSONObject();
        com.facebook.react.bridge.ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            Object value;
            
            switch (readableMap.getType(key)) {
                case Boolean:
                    value = readableMap.getBoolean(key);
                    break;
                case Number:
                    value = readableMap.getDouble(key);
                    break;
                case String:
                    value = readableMap.getString(key);
                    break;
                case Map:
                    value = convertMapToJson(readableMap.getMap(key));
                    break;
                case Array:
                    value = convertArrayToJson(readableMap.getArray(key));
                    break;
                case Null:
                    value = null;
                    break;
                default:
                    throw new IllegalArgumentException("Could not convert object with key: " + key + ".");
            }
            
            object.put(key, value);
        }
        
        return object;
    }

    /**
     * Convert ReadableArray to JSONArray
     */
    private JSONArray convertArrayToJson(ReadableArray readableArray) throws JSONException {
        JSONArray array = new JSONArray();
        
        for (int i = 0; i < readableArray.size(); i++) {
            Object value;
            
            switch (readableArray.getType(i)) {
                case Boolean:
                    value = readableArray.getBoolean(i);
                    break;
                case Number:
                    value = readableArray.getDouble(i);
                    break;
                case String:
                    value = readableArray.getString(i);
                    break;
                case Map:
                    value = convertMapToJson(readableArray.getMap(i));
                    break;
                case Array:
                    value = convertArrayToJson(readableArray.getArray(i));
                    break;
                case Null:
                    value = null;
                    break;
                default:
                    throw new IllegalArgumentException("Could not convert object at index " + i + ".");
            }
            
            array.put(value);
        }
        
        return array;
    }

    /**
     * Convert JSONObject to WritableMap
     */
    private WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
        WritableMap map = Arguments.createMap();
        JSONArray keys = jsonObject.names();
        
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                Object value = jsonObject.get(key);
                
                if (value == null) {
                    map.putNull(key);
                } else if (value instanceof Boolean) {
                    map.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    map.putInt(key, (Integer) value);
                } else if (value instanceof Double) {
                    map.putDouble(key, (Double) value);
                } else if (value instanceof String) {
                    map.putString(key, (String) value);
                } else if (value instanceof JSONObject) {
                    map.putMap(key, convertJsonToMap((JSONObject) value));
                } else if (value instanceof JSONArray) {
                    map.putArray(key, convertJsonToArray((JSONArray) value));
                }
            }
        }
        
        return map;
    }

    /**
     * Convert JSONArray to WritableArray
     */
    private com.facebook.react.bridge.WritableArray convertJsonToArray(JSONArray jsonArray) throws JSONException {
        com.facebook.react.bridge.WritableArray array = Arguments.createArray();
        
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            
            if (value == null) {
                array.pushNull();
            } else if (value instanceof Boolean) {
                array.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                array.pushInt((Integer) value);
            } else if (value instanceof Double) {
                array.pushDouble((Double) value);
            } else if (value instanceof String) {
                array.pushString((String) value);
            } else if (value instanceof JSONObject) {
                array.pushMap(convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                array.pushArray(convertJsonToArray((JSONArray) value));
            }
        }
        
        return array;
    }

    /**
     * Send event to JavaScript
     */
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }
}