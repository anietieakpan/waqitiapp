/**
 * Security Slice - Security state management for biometrics, MFA, and device security
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';
import TouchID from 'react-native-touch-id';
import DeviceInfo from 'react-native-device-info';

// Types
export interface BiometricConfig {
  title: string;
  subtitle: string;
  description: string;
  fallbackLabel: string;
  cancelLabel: string;
  disableDeviceFallback: boolean;
  allowDeviceCredentials: boolean;
}

export interface DeviceSecurity {
  deviceId: string;
  isJailbroken: boolean;
  hasPasscode: boolean;
  hasBiometrics: boolean;
  biometricType: 'FaceID' | 'TouchID' | 'Fingerprint' | 'None';
  isEmulator: boolean;
  lastSecurityCheck: string;
}

export interface TrustedDevice {
  id: string;
  name: string;
  platform: string;
  model: string;
  osVersion: string;
  addedAt: string;
  lastUsed: string;
  isCurrentDevice: boolean;
}

export interface MFAMethod {
  id: string;
  type: 'sms' | 'email' | 'authenticator' | 'backup_codes';
  identifier: string; // phone number, email, or app name
  isEnabled: boolean;
  isPrimary: boolean;
  createdAt: string;
  lastUsed?: string;
}

export interface SecurityAlert {
  id: string;
  type: 'login_attempt' | 'device_change' | 'suspicious_activity' | 'mfa_disabled' | 'password_change';
  severity: 'low' | 'medium' | 'high' | 'critical';
  title: string;
  description: string;
  timestamp: string;
  location?: string;
  deviceInfo?: string;
  isRead: boolean;
  actionRequired: boolean;
}

export interface SecurityState {
  // Authentication Security
  mfaEnabled: boolean;
  mfaMethods: MFAMethod[];
  backupCodesCount: number;
  
  // Biometric Security
  biometricEnabled: boolean;
  biometricAvailable: boolean;
  biometricType: string | null;
  biometricEnrollmentRequired: boolean;
  
  // Device Security
  deviceSecurity: DeviceSecurity | null;
  trustedDevices: TrustedDevice[];
  deviceTrustScore: number;
  
  // PIN Security
  pinEnabled: boolean;
  pinAttempts: number;
  pinLocked: boolean;
  pinLockoutUntil: string | null;
  
  // Session Security
  sessionTimeout: number; // minutes
  autoLockEnabled: boolean;
  autoLockTimeout: number; // seconds
  requireAuthForTransactions: boolean;
  
  // Security Alerts
  securityAlerts: SecurityAlert[];
  unreadAlertsCount: number;
  
  // Security Logs
  loginHistory: Array<{
    timestamp: string;
    deviceInfo: string;
    location: string;
    success: boolean;
  }>;
  
  // Fraud Detection
  fraudScore: number;
  suspiciousActivityDetected: boolean;
  
  // Loading states
  isLoading: boolean;
  error: string | null;
  
  // Feature flags
  advancedSecurityEnabled: boolean;
  threatDetectionEnabled: boolean;
}

// Storage keys
const STORAGE_KEYS = {
  BIOMETRIC_ENABLED: '@waqiti_biometric_enabled',
  PIN_ENABLED: '@waqiti_pin_enabled',
  AUTO_LOCK_ENABLED: '@waqiti_auto_lock_enabled',
  SESSION_TIMEOUT: '@waqiti_session_timeout',
  TRUSTED_DEVICES: '@waqiti_trusted_devices',
  SECURITY_PREFERENCES: '@waqiti_security_preferences',
};

// Initial state
const initialState: SecurityState = {
  mfaEnabled: false,
  mfaMethods: [],
  backupCodesCount: 0,
  biometricEnabled: false,
  biometricAvailable: false,
  biometricType: null,
  biometricEnrollmentRequired: false,
  deviceSecurity: null,
  trustedDevices: [],
  deviceTrustScore: 0,
  pinEnabled: false,
  pinAttempts: 0,
  pinLocked: false,
  pinLockoutUntil: null,
  sessionTimeout: 30,
  autoLockEnabled: true,
  autoLockTimeout: 300,
  requireAuthForTransactions: true,
  securityAlerts: [],
  unreadAlertsCount: 0,
  loginHistory: [],
  fraudScore: 0,
  suspiciousActivityDetected: false,
  isLoading: false,
  error: null,
  advancedSecurityEnabled: false,
  threatDetectionEnabled: true,
};

// Async thunks
export const initializeSecurity = createAsyncThunk(
  'security/initialize',
  async (_, { rejectWithValue }) => {
    try {
      // Load security preferences from storage
      const [
        biometricEnabled,
        pinEnabled,
        autoLockEnabled,
        sessionTimeout,
        trustedDevicesJson,
        securityPrefsJson,
      ] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEYS.BIOMETRIC_ENABLED),
        AsyncStorage.getItem(STORAGE_KEYS.PIN_ENABLED),
        AsyncStorage.getItem(STORAGE_KEYS.AUTO_LOCK_ENABLED),
        AsyncStorage.getItem(STORAGE_KEYS.SESSION_TIMEOUT),
        AsyncStorage.getItem(STORAGE_KEYS.TRUSTED_DEVICES),
        AsyncStorage.getItem(STORAGE_KEYS.SECURITY_PREFERENCES),
      ]);

      // Check biometric availability
      const biometricData = await checkBiometricAvailability();

      // Get device security info
      const deviceSecurity = await getDeviceSecurityInfo();

      // Fetch security data from server
      const securityResponse = await ApiService.get('/user/security');

      let trustedDevices: TrustedDevice[] = [];
      if (trustedDevicesJson) {
        try {
          trustedDevices = JSON.parse(trustedDevicesJson);
        } catch (error) {
          logError('Failed to parse trusted devices', { feature: 'security_slice' }, error as Error);
        }
      }

      return {
        biometricEnabled: biometricEnabled === 'true',
        pinEnabled: pinEnabled === 'true',
        autoLockEnabled: autoLockEnabled !== 'false',
        sessionTimeout: sessionTimeout ? parseInt(sessionTimeout) : 30,
        trustedDevices,
        biometricAvailable: biometricData.available,
        biometricType: biometricData.type,
        deviceSecurity,
        ...securityResponse.data,
      };
    } catch (error: any) {
      logError('Failed to initialize security', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to initialize security');
    }
  }
);

export const enableBiometric = createAsyncThunk(
  'security/enableBiometric',
  async (_, { rejectWithValue }) => {
    try {
      // Check if biometric is available
      const biometricData = await checkBiometricAvailability();
      
      if (!biometricData.available) {
        throw new Error('Biometric authentication is not available on this device');
      }

      // Test biometric authentication
      const config: BiometricConfig = {
        title: 'Enable Biometric Authentication',
        subtitle: 'Use your biometric to authenticate',
        description: 'Place your finger on the sensor or look at the camera',
        fallbackLabel: 'Use Passcode',
        cancelLabel: 'Cancel',
        disableDeviceFallback: false,
        allowDeviceCredentials: true,
      };

      await TouchID.authenticate('Enable biometric authentication', config);

      // Save preference
      await AsyncStorage.setItem(STORAGE_KEYS.BIOMETRIC_ENABLED, 'true');

      // Update server
      await ApiService.put('/user/security/biometric', { enabled: true });

      // Track event
      await ApiService.trackEvent('biometric_enabled', {
        biometricType: biometricData.type,
        timestamp: new Date().toISOString(),
      });

      return true;
    } catch (error: any) {
      logError('Failed to enable biometric', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to enable biometric authentication');
    }
  }
);

export const disableBiometric = createAsyncThunk(
  'security/disableBiometric',
  async (_, { rejectWithValue }) => {
    try {
      // Save preference
      await AsyncStorage.setItem(STORAGE_KEYS.BIOMETRIC_ENABLED, 'false');

      // Update server
      await ApiService.put('/user/security/biometric', { enabled: false });

      // Track event
      await ApiService.trackEvent('biometric_disabled', {
        timestamp: new Date().toISOString(),
      });

      return false;
    } catch (error: any) {
      logError('Failed to disable biometric', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to disable biometric authentication');
    }
  }
);

export const authenticateWithBiometric = createAsyncThunk(
  'security/authenticateWithBiometric',
  async (reason: string, { rejectWithValue }) => {
    try {
      const config: BiometricConfig = {
        title: 'Biometric Authentication',
        subtitle: reason,
        description: 'Use your biometric to authenticate',
        fallbackLabel: 'Use Passcode',
        cancelLabel: 'Cancel',
        disableDeviceFallback: false,
        allowDeviceCredentials: true,
      };

      await TouchID.authenticate(reason, config);

      // Track successful authentication
      await ApiService.trackEvent('biometric_authentication_success', {
        reason,
        timestamp: new Date().toISOString(),
      });

      return true;
    } catch (error: any) {
      // Track failed authentication
      await ApiService.trackEvent('biometric_authentication_failed', {
        reason,
        error: error.message,
        timestamp: new Date().toISOString(),
      });

      return rejectWithValue(error.message || 'Biometric authentication failed');
    }
  }
);

export const setupMFA = createAsyncThunk(
  'security/setupMFA',
  async (method: { type: 'sms' | 'email' | 'authenticator'; identifier: string }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/user/security/mfa/setup', method);

      // Track MFA setup
      await ApiService.trackEvent('mfa_setup_initiated', {
        method: method.type,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to setup MFA', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to setup MFA');
    }
  }
);

export const verifyMFA = createAsyncThunk(
  'security/verifyMFA',
  async (data: { methodId: string; code: string }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/user/security/mfa/verify', data);

      // Track MFA verification
      await ApiService.trackEvent('mfa_verified', {
        methodId: data.methodId,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to verify MFA', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to verify MFA code');
    }
  }
);

export const addTrustedDevice = createAsyncThunk(
  'security/addTrustedDevice',
  async (_, { rejectWithValue }) => {
    try {
      const deviceInfo = await getDeviceSecurityInfo();
      
      const trustedDevice = {
        name: await DeviceInfo.getDeviceName(),
        platform: await DeviceInfo.getSystemName(),
        model: await DeviceInfo.getModel(),
        osVersion: await DeviceInfo.getSystemVersion(),
      };

      const response = await ApiService.post('/user/security/trusted-devices', trustedDevice);

      // Update local storage
      const existingDevices = await AsyncStorage.getItem(STORAGE_KEYS.TRUSTED_DEVICES);
      const devices = existingDevices ? JSON.parse(existingDevices) : [];
      devices.push(response.data);
      await AsyncStorage.setItem(STORAGE_KEYS.TRUSTED_DEVICES, JSON.stringify(devices));

      // Track event
      await ApiService.trackEvent('trusted_device_added', {
        deviceId: response.data.id,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to add trusted device', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to add trusted device');
    }
  }
);

export const removeTrustedDevice = createAsyncThunk(
  'security/removeTrustedDevice',
  async (deviceId: string, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/user/security/trusted-devices/${deviceId}`);

      // Update local storage
      const existingDevices = await AsyncStorage.getItem(STORAGE_KEYS.TRUSTED_DEVICES);
      if (existingDevices) {
        const devices = JSON.parse(existingDevices).filter((d: TrustedDevice) => d.id !== deviceId);
        await AsyncStorage.setItem(STORAGE_KEYS.TRUSTED_DEVICES, JSON.stringify(devices));
      }

      // Track event
      await ApiService.trackEvent('trusted_device_removed', {
        deviceId,
        timestamp: new Date().toISOString(),
      });

      return deviceId;
    } catch (error: any) {
      logError('Failed to remove trusted device', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to remove trusted device');
    }
  }
);

export const fetchSecurityAlerts = createAsyncThunk(
  'security/fetchAlerts',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/user/security/alerts');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch security alerts', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to fetch security alerts');
    }
  }
);

export const markAlertRead = createAsyncThunk(
  'security/markAlertRead',
  async (alertId: string, { rejectWithValue }) => {
    try {
      await ApiService.put(`/user/security/alerts/${alertId}/read`);
      return alertId;
    } catch (error: any) {
      logError('Failed to mark alert as read', { feature: 'security_slice' }, error);
      return rejectWithValue(error.message || 'Failed to mark alert as read');
    }
  }
);

// Helper functions
const checkBiometricAvailability = async () => {
  try {
    const biometryType = await TouchID.isSupported();
    return {
      available: true,
      type: biometryType,
    };
  } catch (error) {
    return {
      available: false,
      type: null,
    };
  }
};

const getDeviceSecurityInfo = async (): Promise<DeviceSecurity> => {
  const deviceId = await DeviceInfo.getUniqueId();
  const isJailbroken = await DeviceInfo.isTablet(); // Simplified check
  const isEmulator = await DeviceInfo.isEmulator();
  
  const biometricData = await checkBiometricAvailability();

  return {
    deviceId,
    isJailbroken: false, // Would need more sophisticated check
    hasPasscode: false, // Would need native module
    hasBiometrics: biometricData.available,
    biometricType: biometricData.type as any || 'None',
    isEmulator,
    lastSecurityCheck: new Date().toISOString(),
  };
};

// Security slice
const securitySlice = createSlice({
  name: 'security',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updateSessionTimeout: (state, action: PayloadAction<number>) => {
      state.sessionTimeout = action.payload;
      AsyncStorage.setItem(STORAGE_KEYS.SESSION_TIMEOUT, action.payload.toString());
    },
    toggleAutoLock: (state, action: PayloadAction<boolean>) => {
      state.autoLockEnabled = action.payload;
      AsyncStorage.setItem(STORAGE_KEYS.AUTO_LOCK_ENABLED, action.payload.toString());
    },
    updateAutoLockTimeout: (state, action: PayloadAction<number>) => {
      state.autoLockTimeout = action.payload;
    },
    incrementPinAttempts: (state) => {
      state.pinAttempts += 1;
      if (state.pinAttempts >= 5) {
        state.pinLocked = true;
        state.pinLockoutUntil = new Date(Date.now() + 30 * 60 * 1000).toISOString(); // 30 min lockout
      }
    },
    resetPinAttempts: (state) => {
      state.pinAttempts = 0;
      state.pinLocked = false;
      state.pinLockoutUntil = null;
    },
    toggleRequireAuthForTransactions: (state, action: PayloadAction<boolean>) => {
      state.requireAuthForTransactions = action.payload;
    },
    addSecurityAlert: (state, action: PayloadAction<SecurityAlert>) => {
      state.securityAlerts.unshift(action.payload);
      if (!action.payload.isRead) {
        state.unreadAlertsCount += 1;
      }
    },
    updateFraudScore: (state, action: PayloadAction<number>) => {
      state.fraudScore = action.payload;
      state.suspiciousActivityDetected = action.payload > 70;
    },
    addLoginHistory: (state, action: PayloadAction<{
      timestamp: string;
      deviceInfo: string;
      location: string;
      success: boolean;
    }>) => {
      state.loginHistory.unshift(action.payload);
      // Keep only last 50 entries
      if (state.loginHistory.length > 50) {
        state.loginHistory = state.loginHistory.slice(0, 50);
      }
    },
  },
  extraReducers: (builder) => {
    // Initialize security
    builder
      .addCase(initializeSecurity.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(initializeSecurity.fulfilled, (state, action) => {
        state.isLoading = false;
        Object.assign(state, action.payload);
      })
      .addCase(initializeSecurity.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Enable biometric
    builder
      .addCase(enableBiometric.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(enableBiometric.fulfilled, (state) => {
        state.isLoading = false;
        state.biometricEnabled = true;
      })
      .addCase(enableBiometric.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Disable biometric
    builder
      .addCase(disableBiometric.fulfilled, (state) => {
        state.biometricEnabled = false;
      });

    // Setup MFA
    builder
      .addCase(setupMFA.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(setupMFA.fulfilled, (state, action) => {
        state.isLoading = false;
        state.mfaMethods.push(action.payload);
        state.mfaEnabled = true;
      })
      .addCase(setupMFA.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Add trusted device
    builder
      .addCase(addTrustedDevice.fulfilled, (state, action) => {
        state.trustedDevices.push(action.payload);
      });

    // Remove trusted device
    builder
      .addCase(removeTrustedDevice.fulfilled, (state, action) => {
        state.trustedDevices = state.trustedDevices.filter(device => device.id !== action.payload);
      });

    // Fetch security alerts
    builder
      .addCase(fetchSecurityAlerts.fulfilled, (state, action) => {
        state.securityAlerts = action.payload.alerts;
        state.unreadAlertsCount = action.payload.unreadCount;
      });

    // Mark alert read
    builder
      .addCase(markAlertRead.fulfilled, (state, action) => {
        const alert = state.securityAlerts.find(a => a.id === action.payload);
        if (alert && !alert.isRead) {
          alert.isRead = true;
          state.unreadAlertsCount = Math.max(0, state.unreadAlertsCount - 1);
        }
      });
  },
});

export const {
  clearError,
  updateSessionTimeout,
  toggleAutoLock,
  updateAutoLockTimeout,
  incrementPinAttempts,
  resetPinAttempts,
  toggleRequireAuthForTransactions,
  addSecurityAlert,
  updateFraudScore,
  addLoginHistory,
} = securitySlice.actions;

export default securitySlice.reducer;