/**
 * BiometricErrorHandler - Comprehensive error handling and user guidance
 * Provides detailed error handling, user guidance, and recovery suggestions
 * for biometric authentication failures.
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  Alert,
  Linking,
  Platform,
} from 'react-native';
import { useBiometric } from '../../hooks/useBiometric';
import {
  BiometricAuthError,
  BiometricStatus,
  SecurityLevel,
  AuthenticationMethod,
} from '../../services/biometric/types';

// Error severity levels
type ErrorSeverity = 'info' | 'warning' | 'error' | 'critical';

interface ErrorInfo {
  title: string;
  message: string;
  severity: ErrorSeverity;
  recoverable: boolean;
  actions: ErrorAction[];
  icon: string;
}

interface ErrorAction {
  label: string;
  action: () => void;
  isPrimary?: boolean;
  destructive?: boolean;
}

export interface BiometricErrorHandlerProps {
  visible: boolean;
  error: BiometricAuthError | null;
  customError?: string;
  onDismiss: () => void;
  onRetry?: () => void;
  onFallback?: () => void;
  onSetupGuide?: () => void;
  onContactSupport?: () => void;
  userId?: string;
}

export const BiometricErrorHandler: React.FC<BiometricErrorHandlerProps> = ({
  visible,
  error,
  customError,
  onDismiss,
  onRetry,
  onFallback,
  onSetupGuide,
  onContactSupport,
  userId,
}) => {
  const {
    status,
    capabilities,
    securityAssessment,
    isErrorRecoverable,
    checkAvailability,
    checkDeviceSecurity,
  } = useBiometric();

  const [showDetails, setShowDetails] = useState(false);

  // Get comprehensive error information
  const getErrorInfo = useCallback((): ErrorInfo => {
    if (customError) {
      return {
        title: 'Authentication Error',
        message: customError,
        severity: 'error',
        recoverable: true,
        actions: [
          { label: 'Try Again', action: onRetry || onDismiss, isPrimary: true },
          { label: 'Use PIN/Password', action: onFallback || onDismiss },
        ],
        icon: '⚠️',
      };
    }

    switch (error) {
      case BiometricAuthError.HARDWARE_NOT_AVAILABLE:
        return {
          title: 'Biometric Hardware Not Available',
          message: 'Your device does not support biometric authentication or the hardware is not available.',
          severity: 'error',
          recoverable: false,
          actions: [
            { label: 'Use PIN/Password', action: onFallback || onDismiss, isPrimary: true },
            { label: 'Close', action: onDismiss },
          ],
          icon: '🚫',
        };

      case BiometricAuthError.NOT_ENROLLED:
        return {
          title: 'No Biometric Data Enrolled',
          message: 'You need to set up biometric authentication in your device settings first.',
          severity: 'warning',
          recoverable: true,
          actions: [
            { label: 'Open Settings', action: () => openDeviceSettings(), isPrimary: true },
            { label: 'Setup Guide', action: onSetupGuide || onDismiss },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '📱',
        };

      case BiometricAuthError.USER_CANCELLED:
        return {
          title: 'Authentication Cancelled',
          message: 'Biometric authentication was cancelled. Please try again or use an alternative method.',
          severity: 'info',
          recoverable: true,
          actions: [
            { label: 'Try Again', action: onRetry || onDismiss, isPrimary: true },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '❌',
        };

      case BiometricAuthError.AUTHENTICATION_FAILED:
        return {
          title: 'Authentication Failed',
          message: 'Biometric authentication failed. Please ensure your finger/face is clean and properly positioned.',
          severity: 'warning',
          recoverable: true,
          actions: [
            { label: 'Try Again', action: onRetry || onDismiss, isPrimary: true },
            { label: 'Tips & Troubleshooting', action: () => showTroubleshootingTips() },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '👆',
        };

      case BiometricAuthError.TEMPORARILY_LOCKED:
        return {
          title: 'Temporarily Locked',
          message: 'Too many failed biometric attempts. Please wait a moment before trying again or use your PIN/password.',
          severity: 'error',
          recoverable: true,
          actions: [
            { label: 'Use PIN/Password', action: onFallback || onDismiss, isPrimary: true },
            { label: 'Wait and Retry', action: () => setTimeout(onRetry || onDismiss, 30000) },
          ],
          icon: '🔒',
        };

      case BiometricAuthError.PERMANENTLY_LOCKED:
        return {
          title: 'Permanently Locked',
          message: 'Biometric authentication has been permanently disabled due to security concerns. Please contact support.',
          severity: 'critical',
          recoverable: false,
          actions: [
            { label: 'Contact Support', action: onContactSupport || onDismiss, isPrimary: true },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '🚨',
        };

      case BiometricAuthError.SYSTEM_ERROR:
        return {
          title: 'System Error',
          message: 'A system error occurred during biometric authentication. Please try again or restart the app.',
          severity: 'error',
          recoverable: true,
          actions: [
            { label: 'Try Again', action: onRetry || onDismiss, isPrimary: true },
            { label: 'Restart App', action: () => restartApp() },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '⚙️',
        };

      case BiometricAuthError.NETWORK_ERROR:
        return {
          title: 'Network Error',
          message: 'Unable to verify biometric authentication due to network issues. Check your connection and try again.',
          severity: 'warning',
          recoverable: true,
          actions: [
            { label: 'Try Again', action: onRetry || onDismiss, isPrimary: true },
            { label: 'Check Connection', action: () => checkNetworkConnection() },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '📶',
        };

      case BiometricAuthError.TOKEN_EXPIRED:
        return {
          title: 'Session Expired',
          message: 'Your biometric session has expired. Please authenticate again.',
          severity: 'warning',
          recoverable: true,
          actions: [
            { label: 'Authenticate Again', action: onRetry || onDismiss, isPrimary: true },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
          ],
          icon: '⏰',
        };

      case BiometricAuthError.DEVICE_NOT_TRUSTED:
        return {
          title: 'Device Not Trusted',
          message: 'This device is not trusted for biometric authentication. Additional security verification may be required.',
          severity: 'critical',
          recoverable: true,
          actions: [
            { label: 'Verify Device', action: () => verifyDevice() },
            { label: 'Use PIN/Password', action: onFallback || onDismiss, isPrimary: true },
            { label: 'Contact Support', action: onContactSupport || onDismiss },
          ],
          icon: '🔐',
        };

      case BiometricAuthError.UNKNOWN_ERROR:
      default:
        return {
          title: 'Unknown Error',
          message: 'An unexpected error occurred during biometric authentication. Please try again or use an alternative method.',
          severity: 'error',
          recoverable: true,
          actions: [
            { label: 'Try Again', action: onRetry || onDismiss, isPrimary: true },
            { label: 'Use PIN/Password', action: onFallback || onDismiss },
            { label: 'Report Issue', action: () => reportIssue() },
          ],
          icon: '❓',
        };
    }
  }, [error, customError, onRetry, onFallback, onSetupGuide, onContactSupport, onDismiss]);

  // Helper functions
  const openDeviceSettings = useCallback(() => {
    if (Platform.OS === 'ios') {
      Linking.openURL('App-Prefs:TOUCHID_PASSCODE');
    } else {
      Linking.openSettings();
    }
  }, []);

  const showTroubleshootingTips = useCallback(() => {
    const biometricType = capabilities?.biometryType || 'Biometric';
    let tips = '';

    switch (biometricType) {
      case 'TouchID':
      case 'Biometrics':
        tips = `Fingerprint Tips:
• Ensure your finger is clean and dry
• Use the same finger you registered
• Cover the entire sensor
• Don't press too hard or too lightly
• Try a different registered finger`;
        break;
      case 'FaceID':
        tips = `Face ID Tips:
• Hold your device 10-20 inches away
• Ensure good lighting
• Keep your face visible and centered
• Remove sunglasses or mask if wearing
• Make sure the camera isn't blocked`;
        break;
      default:
        tips = `Biometric Tips:
• Ensure the sensor is clean
• Position correctly according to your device
• Try multiple times if needed
• Check device settings if issues persist`;
    }

    Alert.alert('Troubleshooting Tips', tips, [{ text: 'OK' }]);
  }, [capabilities]);

  const verifyDevice = useCallback(async () => {
    try {
      await checkDeviceSecurity();
      Alert.alert(
        'Device Verification',
        'Device security check initiated. Please follow any additional prompts.',
        [{ text: 'OK' }]
      );
    } catch (error) {
      Alert.alert(
        'Verification Failed',
        'Unable to verify device security. Please contact support.',
        [{ text: 'OK' }]
      );
    }
  }, [checkDeviceSecurity]);

  const checkNetworkConnection = useCallback(() => {
    Alert.alert(
      'Network Connection',
      'Please check your internet connection and try again. You can also use PIN/Password while offline.',
      [{ text: 'OK' }]
    );
  }, []);

  const restartApp = useCallback(() => {
    Alert.alert(
      'Restart App',
      'Please close and reopen the app to resolve system issues.',
      [{ text: 'OK' }]
    );
  }, []);

  const reportIssue = useCallback(() => {
    const deviceInfo = {
      platform: Platform.OS,
      version: Platform.Version,
      biometryType: capabilities?.biometryType,
      status,
      error,
    };

    Alert.alert(
      'Report Issue',
      `Please contact support with the following information:\n\nError: ${error}\nDevice: ${Platform.OS} ${Platform.Version}\nBiometric: ${capabilities?.biometryType || 'Unknown'}`,
      [
        { text: 'Copy Info', onPress: () => {/* Copy to clipboard */} },
        { text: 'Contact Support', onPress: onContactSupport },
        { text: 'Cancel' },
      ]
    );
  }, [error, status, capabilities, onContactSupport]);

  if (!visible || !error) {
    return null;
  }

  const errorInfo = getErrorInfo();
  const severityColors = {
    info: '#007AFF',
    warning: '#FF9500',
    error: '#FF3B30',
    critical: '#8B0000',
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View style={styles.overlay}>
        <View style={styles.container}>
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.icon}>{errorInfo.icon}</Text>
            <Text style={styles.title}>{errorInfo.title}</Text>
            <View 
              style={[
                styles.severityBadge, 
                { backgroundColor: severityColors[errorInfo.severity] }
              ]}
            >
              <Text style={styles.severityText}>
                {errorInfo.severity.toUpperCase()}
              </Text>
            </View>
          </View>

          {/* Message */}
          <Text style={styles.message}>{errorInfo.message}</Text>

          {/* Security Assessment Warning */}
          {securityAssessment && securityAssessment.riskLevel === SecurityLevel.CRITICAL && (
            <View style={styles.warningContainer}>
              <Text style={styles.warningText}>
                ⚠️ Security Risk Detected: {securityAssessment.threats.join(', ')}
              </Text>
            </View>
          )}

          {/* Actions */}
          <View style={styles.actionContainer}>
            {errorInfo.actions.map((action, index) => (
              <TouchableOpacity
                key={index}
                style={[
                  styles.actionButton,
                  action.isPrimary && styles.primaryActionButton,
                  action.destructive && styles.destructiveActionButton,
                ]}
                onPress={action.action}
              >
                <Text
                  style={[
                    styles.actionButtonText,
                    action.isPrimary && styles.primaryActionButtonText,
                    action.destructive && styles.destructiveActionButtonText,
                  ]}
                >
                  {action.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          {/* Debug Details Toggle */}
          {__DEV__ && (
            <TouchableOpacity
              style={styles.debugToggle}
              onPress={() => setShowDetails(!showDetails)}
            >
              <Text style={styles.debugToggleText}>
                {showDetails ? 'Hide' : 'Show'} Debug Details
              </Text>
            </TouchableOpacity>
          )}

          {/* Debug Details */}
          {__DEV__ && showDetails && (
            <View style={styles.debugContainer}>
              <Text style={styles.debugText}>Error Code: {error}</Text>
              <Text style={styles.debugText}>Status: {status}</Text>
              <Text style={styles.debugText}>Recoverable: {isErrorRecoverable().toString()}</Text>
              <Text style={styles.debugText}>Biometry Type: {capabilities?.biometryType || 'Unknown'}</Text>
              {securityAssessment && (
                <Text style={styles.debugText}>
                  Risk Level: {securityAssessment.riskLevel}
                </Text>
              )}
            </View>
          )}
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  container: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 24,
    maxWidth: 400,
    width: '100%',
  },
  header: {
    alignItems: 'center',
    marginBottom: 16,
  },
  icon: {
    fontSize: 48,
    marginBottom: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: '#000000',
    textAlign: 'center',
    marginBottom: 8,
  },
  severityBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  severityText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  message: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: 20,
  },
  warningContainer: {
    backgroundColor: '#FFF3CD',
    borderColor: '#FFEAA7',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  warningText: {
    color: '#856404',
    fontSize: 14,
    textAlign: 'center',
  },
  actionContainer: {
    gap: 8,
  },
  actionButton: {
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
  },
  primaryActionButton: {
    backgroundColor: '#007AFF',
  },
  destructiveActionButton: {
    backgroundColor: '#FF3B30',
  },
  actionButtonText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#000000',
  },
  primaryActionButtonText: {
    color: '#FFFFFF',
  },
  destructiveActionButtonText: {
    color: '#FFFFFF',
  },
  debugToggle: {
    marginTop: 16,
    padding: 8,
    alignItems: 'center',
  },
  debugToggleText: {
    color: '#007AFF',
    fontSize: 14,
  },
  debugContainer: {
    marginTop: 12,
    padding: 12,
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
  },
  debugText: {
    fontSize: 12,
    color: '#666666',
    marginVertical: 2,
  },
});

export default BiometricErrorHandler;