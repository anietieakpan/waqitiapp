package com.waqitimobile;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * React Native Package for Waqiti native modules
 * Registers all custom native modules for the app
 */
public class WaqitiPackage implements ReactPackage {

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        
        // Add Google Pay module
        modules.add(new GooglePayModule(reactContext));
        
        // Add App-to-App Payment module
        modules.add(new AppToAppPaymentModule(reactContext));
        
        return modules;
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}