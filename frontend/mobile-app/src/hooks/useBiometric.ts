/**
 * useBiometric Hook - Enhanced biometric authentication hook
 * Provides additional utilities and helpers for biometric functionality
 */

import { useCallback, useEffect, useMemo } from 'react';
import { useBiometric as useBiometricContext } from '../contexts/BiometricContext';
import {
  BiometricPromptConfig,
  BiometricAuthResult,
  BiometricStatus,
  BiometricAuthError,
  AuthenticationMethod,
  SecurityLevel,
} from '../services/biometric/types';
import { Logger } from '../../../shared/services/src/LoggingService';

// Enhanced hook return type
interface UseBiometricReturn extends ReturnType<typeof useBiometricContext> {
  // Convenience flags
  canAuthenticate: boolean;
  shouldSetup: boolean;
  isSecure: boolean;
  hasRecentAuth: boolean;
  
  // Enhanced methods
  quickAuth: (userId: string) => Promise<boolean>;
  setupWithPrompt: (userId: string, customTitle?: string) => Promise<boolean>;
  authenticateWithFallback: (userId: string, fallbackMethod?: AuthenticationMethod) => Promise<boolean>;
  
  // Utility methods
  getStatusMessage: () => string;
  getErrorMessage: () => string;
  isErrorRecoverable: () => boolean;
  
  // Auto-setup helpers
  shouldPromptSetup: () => boolean;
  getSetupRecommendation: () => string;
}

/**
 * Enhanced useBiometric hook with additional utilities
 */
export const useBiometric = (): UseBiometricReturn => {
  const context = useBiometricContext();

  // Memoized convenience flags
  const canAuthenticate = useMemo(() => {
    return (
      context.isAvailable &&
      context.isSetup &&
      context.status === BiometricStatus.AVAILABLE
    );
  }, [context.isAvailable, context.isSetup, context.status]);

  const shouldSetup = useMemo(() => {
    return (
      context.isAvailable &&
      !context.isSetup &&
      context.status === BiometricStatus.AVAILABLE
    );
  }, [context.isAvailable, context.isSetup, context.status]);

  const isSecure = useMemo(() => {
    if (!context.securityAssessment) return true; // Assume secure if not assessed
    
    return (
      context.securityAssessment.riskLevel !== SecurityLevel.CRITICAL &&
      context.securityAssessment.deviceIntegrity &&
      context.securityAssessment.biometricIntegrity
    );
  }, [context.securityAssessment]);

  const hasRecentAuth = useMemo(() => {
    if (!context.lastAuthenticationTime) return false;
    
    const now = Date.now();
    const recentThreshold = 15 * 60 * 1000; // 15 minutes
    
    return (now - context.lastAuthenticationTime) < recentThreshold;
  }, [context.lastAuthenticationTime]);

  /**
   * Quick authentication with minimal UI
   */
  const quickAuth = useCallback(async (userId: string): Promise<boolean> => {
    if (!canAuthenticate) {
      return false;
    }

    try {
      const quickPromptConfig: BiometricPromptConfig = {
        title: 'Quick Authentication',
        subtitle: 'Touch sensor or look at camera',
        description: 'Authenticate to continue',
        negativeButtonText: 'Cancel',
        allowDeviceCredentials: false,
        requireConfirmation: false,
      };

      const result = await context.authenticate(userId, quickPromptConfig);
      return result.success;
    } catch (error) {
      Logger.error('Quick auth failed', error);
      return false;
    }
  }, [canAuthenticate, context.authenticate]);

  /**
   * Setup biometric with custom prompts
   */
  const setupWithPrompt = useCallback(async (
    userId: string,
    customTitle?: string
  ): Promise<boolean> => {
    if (!context.isAvailable) {
      return false;
    }

    try {
      const setupPromptConfig: BiometricPromptConfig = {
        title: customTitle || 'Setup Biometric Authentication',
        subtitle: 'Secure your account with biometrics',
        description: 'Use your fingerprint or face to quickly and securely access your account',
        negativeButtonText: 'Skip',
        allowDeviceCredentials: false,
        requireConfirmation: true,
      };

      const result = await context.setupBiometric(userId, setupPromptConfig);
      return result.success;
    } catch (error) {
      Logger.error('Setup with prompt failed', error);
      return false;
    }
  }, [context.isAvailable, context.setupBiometric]);

  /**
   * Authenticate with automatic fallback
   */
  const authenticateWithFallback = useCallback(async (
    userId: string,
    fallbackMethod: AuthenticationMethod = AuthenticationMethod.PIN
  ): Promise<boolean> => {
    if (!canAuthenticate) {
      // Try fallback directly if biometric not available
      try {
        const result = await context.handleFallback(userId, fallbackMethod);
        return result.success;
      } catch (error) {
        Logger.error('Fallback authentication failed', error);
        return false;
      }
    }

    try {
      const authPromptConfig: BiometricPromptConfig = {
        title: 'Authenticate',
        subtitle: 'Verify your identity',
        description: 'Use your biometric or tap "Use PIN" for alternative',
        negativeButtonText: 'Use PIN',
        fallbackTitle: 'Use PIN',
        allowDeviceCredentials: true,
      };

      const result = await context.authenticate(userId, authPromptConfig);
      
      if (!result.success && result.error === BiometricAuthError.USER_CANCELLED) {
        // User cancelled, try fallback
        const fallbackResult = await context.handleFallback(userId, fallbackMethod);
        return fallbackResult.success;
      }
      
      return result.success;
    } catch (error) {
      Logger.error('Authentication with fallback failed', error);
      return false;
    }
  }, [canAuthenticate, context.authenticate, context.handleFallback]);

  /**
   * Get user-friendly status message
   */
  const getStatusMessage = useCallback((): string => {
    switch (context.status) {
      case BiometricStatus.NOT_AVAILABLE:
        return 'Biometric authentication is not available on this device';
      case BiometricStatus.NOT_ENROLLED:
        return 'Please set up biometric authentication in your device settings';
      case BiometricStatus.AVAILABLE:
        return context.isSetup ? 'Biometric authentication is ready' : 'Biometric authentication is available';
      case BiometricStatus.DISABLED:
        return 'Biometric authentication is disabled';
      case BiometricStatus.TEMPORARILY_LOCKED:
        return 'Biometric authentication is temporarily locked due to too many failed attempts';
      case BiometricStatus.PERMANENTLY_LOCKED:
        return 'Biometric authentication is permanently locked. Please contact support';
      case BiometricStatus.UNKNOWN:
      default:
        return 'Checking biometric authentication status...';
    }
  }, [context.status, context.isSetup]);

  /**
   * Get user-friendly error message
   */
  const getErrorMessage = useCallback((): string => {
    if (!context.error && !context.lastError) {
      return '';
    }

    if (context.error) {
      return context.error;
    }

    switch (context.lastError) {
      case BiometricAuthError.HARDWARE_NOT_AVAILABLE:
        return 'Biometric hardware is not available on this device';
      case BiometricAuthError.NOT_ENROLLED:
        return 'No biometric credentials are enrolled on this device';
      case BiometricAuthError.USER_CANCELLED:
        return 'Authentication was cancelled by user';
      case BiometricAuthError.AUTHENTICATION_FAILED:
        return 'Biometric authentication failed. Please try again';
      case BiometricAuthError.TEMPORARILY_LOCKED:
        return 'Too many failed attempts. Please try again later';
      case BiometricAuthError.PERMANENTLY_LOCKED:
        return 'Account is permanently locked. Please contact support';
      case BiometricAuthError.SYSTEM_ERROR:
        return 'A system error occurred. Please try again';
      case BiometricAuthError.NETWORK_ERROR:
        return 'Network error. Please check your connection and try again';
      case BiometricAuthError.TOKEN_EXPIRED:
        return 'Session expired. Please authenticate again';
      case BiometricAuthError.DEVICE_NOT_TRUSTED:
        return 'Device is not trusted for biometric authentication';
      case BiometricAuthError.UNKNOWN_ERROR:
      default:
        return 'An unknown error occurred during biometric authentication';
    }
  }, [context.error, context.lastError]);

  /**
   * Check if current error is recoverable
   */
  const isErrorRecoverable = useCallback((): boolean => {
    if (!context.lastError) {
      return true;
    }

    const recoverableErrors = [
      BiometricAuthError.USER_CANCELLED,
      BiometricAuthError.AUTHENTICATION_FAILED,
      BiometricAuthError.NETWORK_ERROR,
      BiometricAuthError.TOKEN_EXPIRED,
    ];

    return recoverableErrors.includes(context.lastError);
  }, [context.lastError]);

  /**
   * Check if should prompt user to setup biometric
   */
  const shouldPromptSetup = useCallback((): boolean => {
    return (
      shouldSetup &&
      isSecure &&
      !context.error &&
      !context.loading &&
      !context.initializing
    );
  }, [shouldSetup, isSecure, context.error, context.loading, context.initializing]);

  /**
   * Get setup recommendation message
   */
  const getSetupRecommendation = useCallback((): string => {
    if (!context.isAvailable) {
      return '';
    }

    if (!context.capabilities) {
      return 'Enable biometric authentication for faster and more secure access';
    }

    const biometryType = context.capabilities.biometryType;
    let biometricName = 'biometric';
    
    if (biometryType) {
      switch (biometryType) {
        case 'TouchID':
          biometricName = 'Touch ID';
          break;
        case 'FaceID':
          biometricName = 'Face ID';
          break;
        case 'Biometrics':
        default:
          biometricName = 'biometric';
          break;
      }
    }

    return `Enable ${biometricName} authentication for faster and more secure access to your account`;
  }, [context.isAvailable, context.capabilities]);

  // Auto-check setup status when user ID is available
  useEffect(() => {
    // This would typically be called when user context is available
    // For now, it's a placeholder for integration with auth context
  }, []);

  return {
    ...context,
    canAuthenticate,
    shouldSetup,
    isSecure,
    hasRecentAuth,
    quickAuth,
    setupWithPrompt,
    authenticateWithFallback,
    getStatusMessage,
    getErrorMessage,
    isErrorRecoverable,
    shouldPromptSetup,
    getSetupRecommendation,
  };
};

export default useBiometric;