/**
 * Analytics Service
 * Handles app analytics and user tracking
 */

import analytics from '@react-native-firebase/analytics';
import { Platform } from 'react-native';
import DeviceInfo from 'react-native-device-info';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { logDebug, logError, logInfo } from '../utils/Logger';

// Analytics event names
export const AnalyticsEvents = {
  // Authentication
  LOGIN: 'login',
  LOGOUT: 'logout',
  SIGNUP: 'sign_up',
  
  // Payments
  SEND_MONEY: 'send_money',
  REQUEST_MONEY: 'request_money',
  PAYMENT_SUCCESS: 'payment_success',
  PAYMENT_FAILED: 'payment_failed',
  
  // Cards
  CARD_ACTIVATED: 'card_activated',
  CARD_BLOCKED: 'card_blocked',
  VIRTUAL_CARD_CREATED: 'virtual_card_created',
  PHYSICAL_CARD_ORDERED: 'physical_card_ordered',
  
  // Banking
  BANK_ACCOUNT_LINKED: 'bank_account_linked',
  CHECK_DEPOSITED: 'check_deposited',
  WITHDRAWAL_INITIATED: 'withdrawal_initiated',
  
  // Security
  MFA_ENABLED: 'mfa_enabled',
  MFA_DISABLED: 'mfa_disabled',
  PIN_SETUP: 'pin_setup',
  BIOMETRIC_ENABLED: 'biometric_enabled',
  
  // Features
  QR_CODE_SCANNED: 'qr_code_scanned',
  NEARBY_PAYMENT: 'nearby_payment',
  RECURRING_PAYMENT_SETUP: 'recurring_payment_setup',
  CRYPTO_TRANSACTION: 'crypto_transaction',
  
  // User Actions
  PROFILE_UPDATED: 'profile_updated',
  SUPPORT_CONTACTED: 'support_contacted',
  REFERRAL_SENT: 'referral_sent',
  NOTIFICATION_OPENED: 'notification_opened',
  
  // App Usage
  APP_OPENED: 'app_open',
  SCREEN_VIEW: 'screen_view',
  FEATURE_DISCOVERED: 'feature_discovered',
  ERROR_OCCURRED: 'error_occurred',
} as const;

interface UserProperties {
  userId?: string;
  userType?: 'personal' | 'business' | 'merchant';
  kycLevel?: string;
  accountAge?: number;
  preferredCurrency?: string;
  hasLinkedBank?: boolean;
  hasPhysicalCard?: boolean;
  hasVirtualCard?: boolean;
  mfaEnabled?: boolean;
}

/**
 * Setup analytics
 */
export const setupAnalytics = async (): Promise<void> => {
  try {
    logInfo('Setting up analytics', { 
      feature: 'analytics', 
      action: 'setup_start' 
    });
    
    // Enable/disable analytics collection (can be controlled by user preference)
    const analyticsEnabled = await AsyncStorage.getItem('@waqiti_analytics_enabled');
    await analytics().setAnalyticsCollectionEnabled(analyticsEnabled !== 'false');
    
    // Set default user properties
    await analytics().setUserProperties({
      platform: Platform.OS,
      app_version: DeviceInfo.getVersion(),
      device_type: DeviceInfo.isTablet() ? 'tablet' : 'phone',
    });
    
    // Log app open event
    await trackEvent(AnalyticsEvents.APP_OPENED);
    
    logInfo('Analytics setup completed successfully', { 
      feature: 'analytics', 
      action: 'setup_complete' 
    });
  } catch (error) {
    logError('Failed to setup analytics', { 
      feature: 'analytics', 
      action: 'setup_failed' 
    }, error as Error);
  }
};

/**
 * Set user ID for analytics
 */
export const setUserId = async (userId: string | null): Promise<void> => {
  try {
    await analytics().setUserId(userId);
  } catch (error) {
    logError('Failed to set analytics user ID', { 
      feature: 'analytics', 
      action: 'set_user_id_failed' 
    }, error as Error);
  }
};

/**
 * Set user properties
 */
export const setUserProperties = async (properties: UserProperties): Promise<void> => {
  try {
    const formattedProperties: Record<string, string> = {};
    
    Object.entries(properties).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        formattedProperties[key] = String(value);
      }
    });
    
    await analytics().setUserProperties(formattedProperties);
  } catch (error) {
    logError('Failed to set user properties', { 
      feature: 'analytics', 
      action: 'set_user_properties_failed' 
    }, error as Error);
  }
};

/**
 * Track event
 */
export const trackEvent = async (
  eventName: string,
  parameters?: Record<string, any>
): Promise<void> => {
  try {
    if (__DEV__) {
      logDebug(`Analytics Event: ${eventName}`, { 
        feature: 'analytics', 
        action: 'track_event',
        metadata: { eventName, parameters } 
      });
    }
    
    // Firebase Analytics has limits on parameter values
    const sanitizedParams = parameters ? sanitizeParameters(parameters) : undefined;
    
    await analytics().logEvent(eventName, sanitizedParams);
  } catch (error) {
    logError('Failed to track event', { 
      feature: 'analytics', 
      action: 'track_event_failed',
      metadata: { eventName } 
    }, error as Error);
  }
};

/**
 * Track screen view
 */
export const trackScreenView = async (
  screenName: string,
  screenClass?: string
): Promise<void> => {
  try {
    await analytics().logScreenView({
      screen_name: screenName,
      screen_class: screenClass || screenName,
    });
  } catch (error) {
    logError('Failed to track screen view', { 
      feature: 'analytics', 
      action: 'track_screen_view_failed',
      metadata: { screenName, screenClass } 
    }, error as Error);
  }
};

/**
 * Track transaction
 */
export const trackTransaction = async (
  transactionId: string,
  amount: number,
  currency: string,
  type: 'send' | 'receive' | 'withdraw' | 'deposit',
  method?: string
): Promise<void> => {
  try {
    await trackEvent('transaction', {
      transaction_id: transactionId,
      value: amount,
      currency,
      transaction_type: type,
      payment_method: method,
    });
  } catch (error) {
    logError('Failed to track transaction', { 
      feature: 'analytics', 
      action: 'track_transaction_failed',
      metadata: { transactionId, amount, currency, type } 
    }, error as Error);
  }
};

/**
 * Track error
 */
export const trackError = async (
  errorType: string,
  errorMessage: string,
  screen?: string
): Promise<void> => {
  try {
    await trackEvent(AnalyticsEvents.ERROR_OCCURRED, {
      error_type: errorType,
      error_message: errorMessage,
      screen_name: screen,
    });
  } catch (error) {
    logError('Failed to track error event', { 
      feature: 'analytics', 
      action: 'track_error_failed',
      metadata: { errorType, errorMessage, screen } 
    }, error as Error);
  }
};

/**
 * Check if analytics is enabled
 */
export const isAnalyticsEnabled = async (): Promise<boolean> => {
  try {
    const enabled = await AsyncStorage.getItem('@waqiti_analytics_enabled');
    return enabled !== 'false';
  } catch (error) {
    logError('Failed to check analytics status', { 
      feature: 'analytics', 
      action: 'check_status_failed' 
    }, error as Error);
    return true;
  }
};

/**
 * Enable/disable analytics
 */
export const setAnalyticsEnabled = async (enabled: boolean): Promise<void> => {
  try {
    await analytics().setAnalyticsCollectionEnabled(enabled);
    await AsyncStorage.setItem('@waqiti_analytics_enabled', enabled.toString());
  } catch (error) {
    logError('Failed to set analytics enabled status', { 
      feature: 'analytics', 
      action: 'set_enabled_failed',
      metadata: { enabled } 
    }, error as Error);
  }
};

/**
 * Sanitize parameters for Firebase Analytics
 */
const sanitizeParameters = (params: Record<string, any>): Record<string, any> => {
  const sanitized: Record<string, any> = {};
  
  Object.entries(params).forEach(([key, value]) => {
    // Firebase Analytics has limits:
    // - Parameter names: 40 characters
    // - Parameter values: 100 characters for strings
    // - Max 25 parameters per event
    
    const sanitizedKey = key.slice(0, 40);
    let sanitizedValue = value;
    
    if (typeof value === 'string' && value.length > 100) {
      sanitizedValue = value.slice(0, 100);
    } else if (typeof value === 'object') {
      sanitizedValue = JSON.stringify(value).slice(0, 100);
    }
    
    sanitized[sanitizedKey] = sanitizedValue;
  });
  
  // Limit to 25 parameters
  return Object.fromEntries(Object.entries(sanitized).slice(0, 25));
};

export default {
  setupAnalytics,
  setUserId,
  setUserProperties,
  trackEvent,
  trackScreenView,
  trackTransaction,
  trackError,
  isAnalyticsEnabled,
  setAnalyticsEnabled,
  AnalyticsEvents,
};