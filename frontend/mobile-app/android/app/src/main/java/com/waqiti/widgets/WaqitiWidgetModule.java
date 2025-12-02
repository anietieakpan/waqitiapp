package com.waqiti.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * React Native module for managing Waqiti widgets on Android
 */
public class WaqitiWidgetModule extends ReactContextBaseJavaModule {
    
    private static final String TAG = "WaqitiWidgetModule";
    private static final String MODULE_NAME = "WaqitiWidgetModule";
    
    private static ReactApplicationContext reactContext;
    
    public WaqitiWidgetModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void updateWidgets(ReadableMap widgetDataMap, Promise promise) {
        try {
            // Convert ReadableMap to JSON string
            JSONObject widgetDataJson = convertMapToJson(widgetDataMap);
            String dataString = widgetDataJson.toString();
            
            Log.d(TAG, "Updating widgets with data: " + dataString);
            
            // Save widget data and trigger updates
            WaqitiWidgetProvider.saveWidgetData(getReactApplicationContext(), dataString);
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putDouble("timestamp", System.currentTimeMillis());
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update widgets", e);
            promise.reject("UPDATE_ERROR", "Failed to update widgets: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void configureWidget(String widgetType, ReadableMap configMap, Promise promise) {
        try {
            JSONObject configJson = convertMapToJson(configMap);
            String configString = configJson.toString();
            
            Log.d(TAG, "Configuring widget: " + widgetType + " with config: " + configString);
            
            // Get all widget IDs for this type
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getReactApplicationContext());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(getReactApplicationContext(), WaqitiWidgetProvider.class));
            
            // Save configuration for each widget (in a real app, you'd track types per widget)
            for (int appWidgetId : appWidgetIds) {
                WaqitiWidgetProvider.saveWidgetConfig(getReactApplicationContext(), appWidgetId, configString);
            }
            
            // Trigger widget updates
            WaqitiWidgetProvider.updateAllWidgets(getReactApplicationContext());
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putString("widgetType", widgetType);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to configure widget", e);
            promise.reject("CONFIG_ERROR", "Failed to configure widget: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void setWidgetEnabled(String widgetType, boolean enabled, Promise promise) {
        try {
            Log.d(TAG, "Setting widget enabled: " + widgetType + " = " + enabled);
            
            // This would typically update widget configuration
            // For now, we'll just trigger an update
            WaqitiWidgetProvider.updateAllWidgets(getReactApplicationContext());
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putString("widgetType", widgetType);
            result.putBoolean("enabled", enabled);
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to set widget enabled state", e);
            promise.reject("ENABLE_ERROR", "Failed to set widget enabled state: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getWidgetInfo(Promise promise) {
        try {
            WritableMap widgetInfo = Arguments.createMap();
            
            // Check if widgets are supported (Android 3.0+)
            widgetInfo.putBoolean("supportsWidgets", android.os.Build.VERSION.SDK_INT >= 11);
            widgetInfo.putString("platform", "Android");
            widgetInfo.putString("version", android.os.Build.VERSION.RELEASE);
            
            // Get widget counts
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getReactApplicationContext());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(getReactApplicationContext(), WaqitiWidgetProvider.class));
            widgetInfo.putInt("activeWidgets", appWidgetIds.length);
            
            promise.resolve(widgetInfo);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get widget info", e);
            promise.reject("INFO_ERROR", "Failed to get widget info: " + e.getMessage(), e);
        }
    }

    @ReactMethod
    public void refreshAllWidgets(Promise promise) {
        try {
            Log.d(TAG, "Refreshing all widgets");
            
            WaqitiWidgetProvider.updateAllWidgets(getReactApplicationContext());
            
            // Send event to React Native
            sendEvent("WidgetDataRequested", Arguments.createMap());
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putString("message", "All widgets refreshed");
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh widgets", e);
            promise.reject("REFRESH_ERROR", "Failed to refresh widgets: " + e.getMessage(), e);
        }
    }

    // Static methods for external access
    public static void notifyWidgetTap(String widgetType, String actionId) {
        if (reactContext != null) {
            WritableMap eventData = Arguments.createMap();
            eventData.putString("widgetType", widgetType);
            if (actionId != null) {
                eventData.putString("actionId", actionId);
            }
            eventData.putDouble("timestamp", System.currentTimeMillis());
            
            sendEvent("WidgetTapped", eventData);
        }
    }

    public static void notifyDataRequested() {
        if (reactContext != null) {
            WritableMap eventData = Arguments.createMap();
            eventData.putDouble("timestamp", System.currentTimeMillis());
            
            sendEvent("WidgetDataRequested", eventData);
        }
    }

    public static void notifyConfigurationChanged(String widgetType, String config) {
        if (reactContext != null) {
            WritableMap eventData = Arguments.createMap();
            eventData.putString("type", widgetType);
            eventData.putString("config", config);
            
            sendEvent("WidgetConfigurationChanged", eventData);
        }
    }

    // Helper methods
    private static void sendEvent(String eventName, WritableMap eventData) {
        if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
        }
    }

    private JSONObject convertMapToJson(ReadableMap readableMap) throws JSONException {
        JSONObject object = new JSONObject();
        
        if (readableMap == null) {
            return object;
        }

        com.facebook.react.bridge.ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            com.facebook.react.bridge.ReadableType type = readableMap.getType(key);
            
            switch (type) {
                case Null:
                    object.put(key, JSONObject.NULL);
                    break;
                case Boolean:
                    object.put(key, readableMap.getBoolean(key));
                    break;
                case Number:
                    object.put(key, readableMap.getDouble(key));
                    break;
                case String:
                    object.put(key, readableMap.getString(key));
                    break;
                case Map:
                    object.put(key, convertMapToJson(readableMap.getMap(key)));
                    break;
                case Array:
                    object.put(key, convertArrayToJson(readableMap.getArray(key)));
                    break;
            }
        }
        
        return object;
    }

    private org.json.JSONArray convertArrayToJson(com.facebook.react.bridge.ReadableArray readableArray) throws JSONException {
        org.json.JSONArray array = new org.json.JSONArray();
        
        if (readableArray == null) {
            return array;
        }

        for (int i = 0; i < readableArray.size(); i++) {
            com.facebook.react.bridge.ReadableType type = readableArray.getType(i);
            
            switch (type) {
                case Null:
                    array.put(JSONObject.NULL);
                    break;
                case Boolean:
                    array.put(readableArray.getBoolean(i));
                    break;
                case Number:
                    array.put(readableArray.getDouble(i));
                    break;
                case String:
                    array.put(readableArray.getString(i));
                    break;
                case Map:
                    array.put(convertMapToJson(readableArray.getMap(i)));
                    break;
                case Array:
                    array.put(convertArrayToJson(readableArray.getArray(i)));
                    break;
            }
        }
        
        return array;
    }
}