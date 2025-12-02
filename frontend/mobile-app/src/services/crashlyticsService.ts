/**
 * Crashlytics Service
 * Handles crash reporting and error logging
 */

import crashlytics from '@react-native-firebase/crashlytics';
import { Platform } from 'react-native';
import DeviceInfo from 'react-native-device-info';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface CrashlyticsUser {
  userId: string;
  email?: string;
  name?: string;
}

interface ErrorContext {
  screen?: string;
  action?: string;
  metadata?: Record<string, any>;
}

/**
 * Setup crashlytics
 */
export const setupCrashlytics = async (): Promise<void> => {
  try {
    console.log('Setting up crashlytics...');
    
    // Enable/disable crashlytics collection (can be controlled by user preference)
    const crashlyticsEnabled = await AsyncStorage.getItem('@waqiti_crashlytics_enabled');
    await crashlytics().setCrashlyticsCollectionEnabled(crashlyticsEnabled !== 'false');
    
    // Set device attributes
    await crashlytics().setAttributes({
      platform: Platform.OS,
      version: Platform.Version.toString(),
      deviceModel: DeviceInfo.getModel(),
      deviceBrand: DeviceInfo.getBrand(),
      systemVersion: DeviceInfo.getSystemVersion(),
      appVersion: DeviceInfo.getVersion(),
      buildNumber: DeviceInfo.getBuildNumber(),
      isTablet: DeviceInfo.isTablet().toString(),
      hasNotch: DeviceInfo.hasNotch().toString(),
    });
    
    // Set up global error handler for JavaScript errors
    const originalHandler = ErrorUtils.getGlobalHandler();
    ErrorUtils.setGlobalHandler((error, isFatal) => {
      logError(error, { isFatal });
      if (originalHandler) {
        originalHandler(error, isFatal);
      }
    });
    
    console.log('Crashlytics setup completed');
  } catch (error) {
    console.error('Failed to setup crashlytics:', error);
  }
};

/**
 * Set user information for crashlytics
 */
export const setUser = async (user: CrashlyticsUser): Promise<void> => {
  try {
    await crashlytics().setUserId(user.userId);
    
    if (user.email || user.name) {
      await crashlytics().setAttributes({
        ...(user.email && { userEmail: user.email }),
        ...(user.name && { userName: user.name }),
      });
    }
  } catch (error) {
    console.error('Failed to set crashlytics user:', error);
  }
};

/**
 * Clear user information (on logout)
 */
export const clearUser = async (): Promise<void> => {
  try {
    await crashlytics().setUserId('');
    await crashlytics().setAttributes({
      userEmail: '',
      userName: '',
    });
  } catch (error) {
    console.error('Failed to clear crashlytics user:', error);
  }
};

/**
 * Log error
 */
export const logError = (error: Error | string, context?: ErrorContext): void => {
  try {
    const errorMessage = typeof error === 'string' ? error : error.message;
    const errorStack = typeof error === 'string' ? '' : error.stack || '';
    
    console.error('Logging error:', errorMessage, context);
    
    // Record error in crashlytics
    crashlytics().recordError(new Error(errorMessage), errorStack);
    
    // Add context attributes if provided
    if (context) {
      const attributes: Record<string, string> = {};
      
      if (context.screen) {
        attributes.errorScreen = context.screen;
      }
      
      if (context.action) {
        attributes.errorAction = context.action;
      }
      
      if (context.metadata) {
        Object.entries(context.metadata).forEach(([key, value]) => {
          attributes[`error_${key}`] = String(value);
        });
      }
      
      crashlytics().setAttributes(attributes);
    }
  } catch (logError) {
    console.error('Failed to log error:', logError);
  }
};

/**
 * Log custom message
 */
export const log = (message: string): void => {
  try {
    crashlytics().log(message);
  } catch (error) {
    console.error('Failed to log message:', error);
  }
};

/**
 * Force a crash (for testing)
 */
export const crash = (): void => {
  if (__DEV__) {
    crashlytics().crash();
  }
};

/**
 * Check if crashlytics is enabled
 */
export const isCrashlyticsEnabled = async (): Promise<boolean> => {
  try {
    return await crashlytics().isCrashlyticsCollectionEnabled();
  } catch (error) {
    console.error('Failed to check crashlytics status:', error);
    return false;
  }
};

/**
 * Enable/disable crashlytics
 */
export const setCrashlyticsEnabled = async (enabled: boolean): Promise<void> => {
  try {
    await crashlytics().setCrashlyticsCollectionEnabled(enabled);
    await AsyncStorage.setItem('@waqiti_crashlytics_enabled', enabled.toString());
  } catch (error) {
    console.error('Failed to set crashlytics enabled:', error);
  }
};

export default {
  setupCrashlytics,
  setUser,
  clearUser,
  logError,
  log,
  crash,
  isCrashlyticsEnabled,
  setCrashlyticsEnabled,
};