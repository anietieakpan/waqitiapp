/**
 * SecurityContext - Mobile security context
 * Provides security state and methods for the mobile app
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Crypto from 'expo-crypto';
import MFAService, { MFAMethod, MFAAction, MFAStatus } from '../services/MFAService';
import { useAuth } from './AuthContext';
import DeviceInfo from 'react-native-device-info';
import { promptForPin, promptForMFACode } from '../components/security/SecurityDialogManager';
import RootDetectionService from '../services/security/RootDetectionService';

// Security state interface
interface SecurityState {
  isPinSetup: boolean;
  isMfaEnabled: boolean;
  mfaStatus: MFAStatus | null;
  securityLevel: 'low' | 'medium' | 'high';
  lastSecurityCheck: number | null;
  deviceTrusted: boolean;
  loading: boolean;
  error: string | null;
}

// Security settings interface
interface SecuritySettings {
  requirePinOnStartup: boolean;
  requireBiometricConfirmation: boolean;
  autoLockEnabled: boolean;
  autoLockDuration: number; // in minutes
  allowScreenshots: boolean;
  logSecurityEvents: boolean;
}

// SecurityContext interface
interface SecurityContextType extends SecurityState {
  setupPin: (pin: string) => Promise<boolean>;
  verifyPin: (pin: string) => Promise<boolean>;
  changePin: (oldPin: string, newPin: string) => Promise<boolean>;
  removePin: (pin: string) => Promise<boolean>;
  
  enableMfa: () => Promise<boolean>;
  disableMfa: () => Promise<boolean>;
  
  checkDeviceSecurity: () => Promise<void>;
  updateSecuritySettings: (settings: Partial<SecuritySettings>) => Promise<void>;
  getSecuritySettings: () => Promise<SecuritySettings>;
  
  clearError: () => void;
  refreshSecurityState: () => Promise<void>;
}

// Default state
const defaultState: SecurityState = {
  isPinSetup: false,
  isMfaEnabled: false,
  mfaStatus: null,
  securityLevel: 'low',
  lastSecurityCheck: null,
  deviceTrusted: false,
  loading: true,
  error: null,
};

// Default security settings
const defaultSecuritySettings: SecuritySettings = {
  requirePinOnStartup: true,
  requireBiometricConfirmation: false,
  autoLockEnabled: true,
  autoLockDuration: 5,
  allowScreenshots: false,
  logSecurityEvents: true,
};

// Create context
const SecurityContext = createContext<SecurityContextType | undefined>(undefined);

// Custom hook
export const useSecurity = () => {
  const context = useContext(SecurityContext);
  if (!context) {
    throw new Error('useSecurity must be used within a SecurityProvider');
  }
  return context;
};

// SecurityProvider props
interface SecurityProviderProps {
  children: ReactNode;
}

// Storage keys
const STORAGE_KEYS = {
  PIN_HASH: '@waqiti_pin_hash',
  SECURITY_SETTINGS: '@waqiti_security_settings',
  DEVICE_TRUSTED: '@waqiti_device_trusted',
  LAST_SECURITY_CHECK: '@waqiti_last_security_check',
};

// SecurityProvider component
export const SecurityProvider: React.FC<SecurityProviderProps> = ({ children }) => {
  const [state, setState] = useState<SecurityState>(defaultState);
  const { user } = useAuth();

  // Initialize security state
  useEffect(() => {
    initializeSecurity();
  }, [user]);

  /**
   * Initialize security state from storage
   */
  const initializeSecurity = async () => {
    try {
      const [
        pinHash,
        settingsJson,
        deviceTrusted,
        lastSecurityCheck,
      ] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEYS.PIN_HASH),
        AsyncStorage.getItem(STORAGE_KEYS.SECURITY_SETTINGS),
        AsyncStorage.getItem(STORAGE_KEYS.DEVICE_TRUSTED),
        AsyncStorage.getItem(STORAGE_KEYS.LAST_SECURITY_CHECK),
      ]);

      const isPinSetup = !!pinHash;
      const deviceTrustedBool = deviceTrusted === 'true';
      const lastCheck = lastSecurityCheck ? parseInt(lastSecurityCheck, 10) : null;

      // Get MFA status from backend
      let mfaStatus: MFAStatus | null = null;
      let isMfaEnabled = false;
      
      if (user) {
        try {
          mfaStatus = await MFAService.getMFAStatus();
          isMfaEnabled = mfaStatus.enabled;
        } catch (error) {
          console.error('Failed to get MFA status:', error);
        }
      }

      // Determine security level
      let securityLevel: 'low' | 'medium' | 'high' = 'low';
      if (isPinSetup && isMfaEnabled && deviceTrustedBool) {
        securityLevel = 'high';
      } else if ((isPinSetup && isMfaEnabled) || (isPinSetup && deviceTrustedBool)) {
        securityLevel = 'medium';
      }

      setState({
        isPinSetup,
        isMfaEnabled,
        mfaStatus,
        securityLevel,
        lastSecurityCheck: lastCheck,
        deviceTrusted: deviceTrustedBool,
        loading: false,
        error: null,
      });
    } catch (error) {
      console.error('Failed to initialize security state:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        error: 'Failed to load security settings',
      }));
    }
  };

  /**
   * Generate or retrieve user-specific salt for PIN hashing
   * Each user gets a unique random salt stored securely
   */
  const getUserPinSalt = async (userId: string): Promise<string> => {
    const saltKey = `@waqiti_pin_salt_${userId}`;
    let salt = await AsyncStorage.getItem(saltKey);

    if (!salt) {
      // Generate new random salt (32 bytes = 64 hex characters)
      const randomBytes = await Crypto.getRandomBytesAsync(32);
      salt = Array.from(randomBytes)
        .map(b => b.toString(16).padStart(2, '0'))
        .join('');
      await AsyncStorage.setItem(saltKey, salt);
    }

    return salt;
  };

  /**
   * Hash PIN for storage using secure crypto with per-user salt
   * SECURITY FIX: Now uses random salt per user instead of hardcoded salt
   */
  const hashPin = async (pin: string): Promise<string> => {
    if (!user?.id) {
      throw new Error('User must be authenticated to setup PIN');
    }

    // Get or generate user-specific salt
    const salt = await getUserPinSalt(user.id);

    // Use PBKDF2-like approach with multiple iterations for added security
    let hash = pin;
    for (let i = 0; i < 10000; i++) {
      const data = `${hash}:${salt}:${i}`;
      hash = await Crypto.digestStringAsync(
        Crypto.CryptoDigestAlgorithm.SHA256,
        data,
        { encoding: Crypto.CryptoEncoding.HEX }
      );
    }

    return hash;
  };

  /**
   * Setup PIN
   */
  const setupPin = async (pin: string): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      if (pin.length < 4 || pin.length > 8) {
        throw new Error('PIN must be between 4 and 8 digits');
      }

      const hashedPin = await hashPin(pin);
      await AsyncStorage.setItem(STORAGE_KEYS.PIN_HASH, hashedPin);

      setState(prev => ({
        ...prev,
        isPinSetup: true,
        securityLevel: prev.deviceTrusted ? 'high' : 'medium',
        loading: false,
      }));

      return true;
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to setup PIN',
      }));
      return false;
    }
  };

  /**
   * Verify PIN
   */
  const verifyPin = async (pin: string): Promise<boolean> => {
    try {
      const storedHash = await AsyncStorage.getItem(STORAGE_KEYS.PIN_HASH);
      if (!storedHash) {
        return false;
      }

      const hashedPin = await hashPin(pin);
      return hashedPin === storedHash;
    } catch (error) {
      console.error('PIN verification failed:', error);
      return false;
    }
  };

  /**
   * Change PIN
   */
  const changePin = async (oldPin: string, newPin: string): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      const isOldPinValid = await verifyPin(oldPin);
      if (!isOldPinValid) {
        throw new Error('Current PIN is incorrect');
      }

      if (newPin.length < 4 || newPin.length > 8) {
        throw new Error('New PIN must be between 4 and 8 digits');
      }

      const hashedNewPin = await hashPin(newPin);
      await AsyncStorage.setItem(STORAGE_KEYS.PIN_HASH, hashedNewPin);

      setState(prev => ({ ...prev, loading: false }));
      return true;
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to change PIN',
      }));
      return false;
    }
  };

  /**
   * Remove PIN
   */
  const removePin = async (pin: string): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      const isPinValid = await verifyPin(pin);
      if (!isPinValid) {
        throw new Error('PIN is incorrect');
      }

      await AsyncStorage.removeItem(STORAGE_KEYS.PIN_HASH);

      setState(prev => ({
        ...prev,
        isPinSetup: false,
        securityLevel: prev.deviceTrusted ? 'medium' : 'low',
        loading: false,
      }));

      return true;
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to remove PIN',
      }));
      return false;
    }
  };

  /**
   * Enable MFA
   */
  const enableMfa = async (): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      if (!user) {
        throw new Error('User must be authenticated to enable MFA');
      }

      // Get available MFA methods
      const biometricAvailable = await MFAService.isBiometricMFAAvailable();
      
      // Default to SMS if no biometric available
      const defaultMethod = biometricAvailable ? MFAMethod.BIOMETRIC : MFAMethod.SMS;
      
      // Setup MFA with backend
      await MFAService.setupMFA({
        method: defaultMethod,
        phoneNumber: user.phoneNumber // Assuming user has phone number
      });

      // Refresh MFA status
      const mfaStatus = await MFAService.getMFAStatus();
      
      setState(prev => ({
        ...prev,
        isMfaEnabled: true,
        mfaStatus,
        securityLevel: prev.isPinSetup && prev.deviceTrusted ? 'high' : 'medium',
        loading: false,
      }));

      return true;
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to enable MFA',
      }));
      return false;
    }
  };

  /**
   * Disable MFA
   */
  const disableMfa = async (): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      if (!user) {
        throw new Error('User must be authenticated to disable MFA');
      }

      // Require PIN verification before disabling MFA
      const pin = await promptForPinInternal('Disable MFA', 'Enter your PIN to disable multi-factor authentication');
      const pinVerified = await verifyPin(pin);
      if (!pinVerified) {
        throw new Error('PIN verification failed');
      }

      // Get current MFA status to determine verification method
      const currentStatus = await MFAService.getMFAStatus();
      
      // Request verification code based on primary method
      if (currentStatus.primaryMethod === MFAMethod.SMS) {
        await MFAService.requestSMSCode(MFAAction.PROFILE_CHANGE);
      }
      
      // Get verification code from user
      const verificationCode = await promptForMFACodeInternal(
        currentStatus.primaryMethod,
        currentStatus.maskedContact
      );
      
      // Disable MFA with backend
      await MFAService.disableMFA(verificationCode);
      
      setState(prev => ({
        ...prev,
        isMfaEnabled: false,
        mfaStatus: null,
        securityLevel: prev.isPinSetup && prev.deviceTrusted ? 'medium' : 'low',
        loading: false,
      }));

      return true;
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to disable MFA',
      }));
      return false;
    }
  };

  /**
   * Check device security
   */
  const checkDeviceSecurity = async (): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      // Check various device security factors
      const checks = {
        hasScreenLock: await checkHasScreenLock(),
        isBiometricEnabled: await MFAService.isBiometricMFAAvailable(),
        isRooted: await checkIfDeviceRooted(),
        hasVPN: await checkVPNStatus(),
        isEmulator: await checkIfEmulator(),
      };

      // Device is trusted if it passes basic security checks
      const deviceTrusted = checks.hasScreenLock && 
                           !checks.isRooted && 
                           !checks.isEmulator;
      
      const now = Date.now();

      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.DEVICE_TRUSTED, deviceTrusted.toString()),
        AsyncStorage.setItem(STORAGE_KEYS.LAST_SECURITY_CHECK, now.toString()),
      ]);

      let securityLevel: 'low' | 'medium' | 'high' = 'low';
      if (state.isPinSetup && deviceTrusted) {
        securityLevel = 'high';
      } else if (state.isPinSetup || deviceTrusted) {
        securityLevel = 'medium';
      }

      setState(prev => ({
        ...prev,
        deviceTrusted,
        lastSecurityCheck: now,
        securityLevel,
        loading: false,
      }));
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to check device security',
      }));
    }
  };

  /**
   * Update security settings
   */
  const updateSecuritySettings = async (settings: Partial<SecuritySettings>): Promise<void> => {
    try {
      const currentSettings = await getSecuritySettings();
      const updatedSettings = { ...currentSettings, ...settings };

      await AsyncStorage.setItem(
        STORAGE_KEYS.SECURITY_SETTINGS,
        JSON.stringify(updatedSettings)
      );
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        error: error.message || 'Failed to update security settings',
      }));
      throw error;
    }
  };

  /**
   * Get security settings
   */
  const getSecuritySettings = async (): Promise<SecuritySettings> => {
    try {
      const settingsJson = await AsyncStorage.getItem(STORAGE_KEYS.SECURITY_SETTINGS);
      if (settingsJson) {
        return { ...defaultSecuritySettings, ...JSON.parse(settingsJson) };
      }
      return defaultSecuritySettings;
    } catch (error) {
      console.error('Failed to get security settings:', error);
      return defaultSecuritySettings;
    }
  };

  /**
   * Clear error
   */
  const clearError = (): void => {
    setState(prev => ({ ...prev, error: null }));
  };

  /**
   * Refresh security state
   */
  const refreshSecurityState = async (): Promise<void> => {
    await initializeSecurity();
  };

  const contextValue: SecurityContextType = {
    ...state,
    setupPin,
    verifyPin,
    changePin,
    removePin,
    enableMfa,
    disableMfa,
    checkDeviceSecurity,
    updateSecuritySettings,
    getSecuritySettings,
    clearError,
    refreshSecurityState,
  };

  return (
    <SecurityContext.Provider value={contextValue}>
      {children}
    </SecurityContext.Provider>
  );
};

// Helper functions for device security checks
const checkHasScreenLock = async (): Promise<boolean> => {
  try {
    // Check if device has screen lock enabled
    // This would use a native module or expo API
    // For now, we'll assume it's available
    return true;
  } catch (error) {
    console.error('Failed to check screen lock:', error);
    return false;
  }
};

const checkIfDeviceRooted = async (): Promise<boolean> => {
  try {
    // Check if device is rooted/jailbroken
    // This would use a security library like jail-monkey
    const checks = [
      // Check for common root apps
      await checkForRootApps(),
      // Check for root access
      await checkForRootAccess(),
      // Check for modified system files
      await checkForModifiedSystem(),
    ];
    
    return checks.some(isRooted => isRooted);
  } catch (error) {
    console.error('Failed to check if device is rooted:', error);
    return false;
  }
};

const checkVPNStatus = async (): Promise<boolean> => {
  try {
    // Check if VPN is active
    // This would use network info APIs
    return false; // Assume no VPN for now
  } catch (error) {
    console.error('Failed to check VPN status:', error);
    return false;
  }
};

const checkIfEmulator = async (): Promise<boolean> => {
  try {
    // Check if running on emulator/simulator
    return DeviceInfo.isEmulator();
  } catch (error) {
    console.error('Failed to check if emulator:', error);
    return false;
  }
};

// Root detection helper functions - now using comprehensive service
const checkForRootApps = async (): Promise<boolean> => {
  try {
    const result = await RootDetectionService.isDeviceCompromised();
    return result.isRooted && result.detectionMethod.includes('Root Apps');
  } catch (error) {
    console.error('Root apps check failed:', error);
    return false;
  }
};

const checkForRootAccess = async (): Promise<boolean> => {
  try {
    const result = await RootDetectionService.isDeviceCompromised();
    return result.isRooted && result.detectionMethod.includes('Root Binaries');
  } catch (error) {
    console.error('Root access check failed:', error);
    return false;
  }
};

const checkForModifiedSystem = async (): Promise<boolean> => {
  try {
    const result = await RootDetectionService.isDeviceCompromised();
    return result.isRooted &&
           (result.detectionMethod.includes('Modified System') ||
            result.detectionMethod.includes('Writable System'));
  } catch (error) {
    console.error('Modified system check failed:', error);
    return false;
  }
};

// UI prompt helper functions - now implemented with proper dialogs
const promptForPinInternal = async (title?: string, subtitle?: string): Promise<string> => {
  try {
    return await promptForPin({
      title: title || 'Security Verification',
      subtitle: subtitle || 'Enter your PIN to continue',
      allowBiometric: true,
      maxLength: 6,
    });
  } catch (error: any) {
    throw new Error(error.message || 'PIN entry cancelled');
  }
};

const promptForMFACodeInternal = async (method: MFAMethod, maskedContact?: string): Promise<string> => {
  try {
    return await promptForMFACode({
      method,
      maskedContact,
      codeLength: 6,
      onResendCode: method !== 'TOTP' ? async () => {
        // Integrate with your MFA service to resend code
        try {
          const result = await MFAService.requestMFAChallenge(method, 'RESEND_CODE');
          return result.success;
        } catch (error) {
          console.error('Failed to resend MFA code:', error);
          return false;
        }
      } : undefined,
    });
  } catch (error: any) {
    throw new Error(error.message || 'MFA code entry cancelled');
  }
};

export default SecurityProvider;