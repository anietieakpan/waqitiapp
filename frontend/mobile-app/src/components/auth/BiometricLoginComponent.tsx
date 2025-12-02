/**
 * BiometricLoginComponent - Comprehensive biometric authentication component
 * Provides biometric login functionality that can be integrated into LoginScreen
 * and used for app unlock scenarios with comprehensive error handling.
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Platform,
  ActivityIndicator,
  Animated,
  Dimensions,
} from 'react-native';
import { useBiometric } from '../../hooks/useBiometric';
import {
  BiometricStatus,
  BiometricAuthError,
  AuthenticationMethod,
  SecurityLevel,
} from '../../services/biometric/types';

// Icon component placeholder - replace with your preferred icon library
const BiometricIcon = ({ type, size = 24, color = '#007AFF' }: {
  type: 'fingerprint' | 'face' | 'biometric' | 'error' | 'loading';
  size?: number;
  color?: string;
}) => {
  const iconMap = {
    fingerprint: '👆',
    face: '👤',
    biometric: '🔒',
    error: '❌',
    loading: '⏳',
  };
  
  return (
    <Text style={{ fontSize: size, color }}>
      {iconMap[type]}
    </Text>
  );
};

export interface BiometricLoginComponentProps {
  userId?: string;
  onSuccess: (authResult: { method: 'biometric' | 'fallback'; timestamp: number }) => void;
  onFallback?: () => void;
  onError?: (error: string) => void;
  mode?: 'login' | 'unlock' | 'setup';
  showSetupPrompt?: boolean;
  customPromptTitle?: string;
  disabled?: boolean;
  autoTrigger?: boolean;
  fallbackMethod?: AuthenticationMethod;
  style?: any;
  compact?: boolean;
}

export const BiometricLoginComponent: React.FC<BiometricLoginComponentProps> = ({
  userId,
  onSuccess,
  onFallback,
  onError,
  mode = 'login',
  showSetupPrompt = true,
  customPromptTitle,
  disabled = false,
  autoTrigger = false,
  fallbackMethod = AuthenticationMethod.PIN,
  style,
  compact = false,
}) => {
  const {
    isAvailable,
    isSetup,
    status,
    capabilities,
    canAuthenticate,
    shouldSetup,
    isSecure,
    hasRecentAuth,
    loading,
    error,
    isAuthenticating,
    quickAuth,
    setupWithPrompt,
    authenticateWithFallback,
    getStatusMessage,
    getErrorMessage,
    isErrorRecoverable,
    shouldPromptSetup,
    getSetupRecommendation,
    clearError,
  } = useBiometric();

  const [animatedValue] = useState(new Animated.Value(1));
  const [showDetails, setShowDetails] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [isProcessing, setIsProcessing] = useState(false);

  const maxRetries = 3;
  const { width: screenWidth } = Dimensions.get('window');

  // Determine biometric icon type based on capabilities
  const biometricIconType = useMemo(() => {
    if (!capabilities?.biometryType) return 'biometric';
    
    switch (capabilities.biometryType) {
      case 'TouchID':
      case 'Biometrics':
        return 'fingerprint';
      case 'FaceID':
        return 'face';
      default:
        return 'biometric';
    }
  }, [capabilities]);

  // Get user-friendly biometric type name
  const biometricTypeName = useMemo(() => {
    if (!capabilities?.biometryType) return 'Biometric';
    
    switch (capabilities.biometryType) {
      case 'TouchID':
        return 'Touch ID';
      case 'FaceID':
        return 'Face ID';
      case 'Biometrics':
        return 'Fingerprint';
      default:
        return 'Biometric';
    }
  }, [capabilities]);

  // Check if component should be visible
  const shouldShow = useMemo(() => {
    if (disabled) return false;
    
    switch (mode) {
      case 'setup':
        return isAvailable && !isSetup;
      case 'unlock':
      case 'login':
        return isAvailable && (isSetup || shouldSetup);
      default:
        return false;
    }
  }, [mode, isAvailable, isSetup, shouldSetup, disabled]);

  // Auto-trigger authentication effect
  useEffect(() => {
    if (autoTrigger && canAuthenticate && userId && !isProcessing) {
      handleQuickAuth();
    }
  }, [autoTrigger, canAuthenticate, userId, isProcessing]);

  // Pulse animation for biometric button
  const startPulseAnimation = useCallback(() => {
    Animated.sequence([
      Animated.timing(animatedValue, {
        toValue: 1.1,
        duration: 500,
        useNativeDriver: true,
      }),
      Animated.timing(animatedValue, {
        toValue: 1,
        duration: 500,
        useNativeDriver: true,
      }),
    ]).start();
  }, [animatedValue]);

  // Handle quick authentication
  const handleQuickAuth = useCallback(async () => {
    if (!userId || !canAuthenticate || isProcessing) return;

    try {
      setIsProcessing(true);
      clearError();
      
      // Use recent auth if available in unlock mode
      if (mode === 'unlock' && hasRecentAuth) {
        onSuccess({ method: 'biometric', timestamp: Date.now() });
        return;
      }

      const success = await quickAuth(userId);
      
      if (success) {
        onSuccess({ method: 'biometric', timestamp: Date.now() });
        setRetryCount(0);
      } else {
        setRetryCount(prev => prev + 1);
        handleAuthFailure('Authentication failed');
      }
    } catch (error: any) {
      handleAuthFailure(error.message || 'Authentication failed');
    } finally {
      setIsProcessing(false);
    }
  }, [userId, canAuthenticate, mode, hasRecentAuth, isProcessing, quickAuth, onSuccess, clearError]);

  // Handle authentication with fallback
  const handleAuthenticateWithFallback = useCallback(async () => {
    if (!userId || isProcessing) return;

    try {
      setIsProcessing(true);
      clearError();
      startPulseAnimation();

      const promptTitle = customPromptTitle || `Sign in with ${biometricTypeName}`;
      
      const success = await authenticateWithFallback(userId, fallbackMethod);
      
      if (success) {
        onSuccess({ method: 'biometric', timestamp: Date.now() });
        setRetryCount(0);
      } else {
        setRetryCount(prev => prev + 1);
        
        if (retryCount >= maxRetries - 1) {
          // Max retries reached, trigger fallback
          if (onFallback) {
            onFallback();
          } else {
            onSuccess({ method: 'fallback', timestamp: Date.now() });
          }
        } else {
          handleAuthFailure('Authentication failed');
        }
      }
    } catch (error: any) {
      handleAuthFailure(error.message || 'Authentication failed');
    } finally {
      setIsProcessing(false);
    }
  }, [
    userId,
    isProcessing,
    customPromptTitle,
    biometricTypeName,
    authenticateWithFallback,
    fallbackMethod,
    retryCount,
    maxRetries,
    onSuccess,
    onFallback,
    clearError,
    startPulseAnimation,
  ]);

  // Handle biometric setup
  const handleSetup = useCallback(async () => {
    if (!userId || isProcessing) return;

    try {
      setIsProcessing(true);
      clearError();

      const setupTitle = customPromptTitle || `Set up ${biometricTypeName}`;
      const success = await setupWithPrompt(userId, setupTitle);
      
      if (success) {
        Alert.alert(
          'Setup Complete',
          `${biometricTypeName} has been set up successfully! You can now use it to sign in quickly and securely.`,
          [{ text: 'OK' }]
        );
      } else {
        handleAuthFailure('Setup failed');
      }
    } catch (error: any) {
      handleAuthFailure(error.message || 'Setup failed');
    } finally {
      setIsProcessing(false);
    }
  }, [userId, isProcessing, customPromptTitle, biometricTypeName, setupWithPrompt, clearError]);

  // Handle authentication failure
  const handleAuthFailure = useCallback((errorMessage: string) => {
    const fullErrorMessage = getErrorMessage() || errorMessage;
    
    if (onError) {
      onError(fullErrorMessage);
    }

    // Show user guidance for recoverable errors
    if (isErrorRecoverable()) {
      if (retryCount < maxRetries - 1) {
        Alert.alert(
          'Authentication Failed',
          `${fullErrorMessage}\n\nAttempts remaining: ${maxRetries - retryCount - 1}`,
          [
            { text: 'Try Again', onPress: () => setRetryCount(prev => prev + 1) },
            { text: 'Use PIN/Password', onPress: onFallback },
          ]
        );
      } else {
        Alert.alert(
          'Too Many Attempts',
          'Please use your PIN or password to continue.',
          [{ text: 'OK', onPress: onFallback }]
        );
      }
    } else {
      Alert.alert(
        'Authentication Error',
        fullErrorMessage,
        [{ text: 'OK', onPress: onFallback }]
      );
    }
  }, [getErrorMessage, onError, isErrorRecoverable, retryCount, maxRetries, onFallback]);

  // Handle fallback button press
  const handleFallbackPress = useCallback(() => {
    if (onFallback) {
      onFallback();
    } else {
      onSuccess({ method: 'fallback', timestamp: Date.now() });
    }
  }, [onFallback, onSuccess]);

  // Don't render if shouldn't show
  if (!shouldShow) return null;

  // Compact mode for quick unlock scenarios
  if (compact) {
    return (
      <TouchableOpacity
        style={[styles.compactContainer, style]}
        onPress={mode === 'setup' ? handleSetup : handleQuickAuth}
        disabled={isProcessing || !isSecure}
      >
        <Animated.View style={[styles.compactButton, { transform: [{ scale: animatedValue }] }]}>
          {isProcessing ? (
            <ActivityIndicator size="small" color="#FFFFFF" />
          ) : (
            <BiometricIcon type={biometricIconType} size={20} color="#FFFFFF" />
          )}
        </Animated.View>
        <Text style={styles.compactText}>
          {mode === 'setup' ? 'Setup' : 'Unlock'}
        </Text>
      </TouchableOpacity>
    );
  }

  // Full component for login screen
  return (
    <View style={[styles.container, style]}>
      {/* Main Biometric Button */}
      <TouchableOpacity
        style={[
          styles.biometricButton,
          isProcessing && styles.biometricButtonProcessing,
          !isSecure && styles.biometricButtonDisabled,
        ]}
        onPress={mode === 'setup' ? handleSetup : handleAuthenticateWithFallback}
        disabled={isProcessing || !isSecure}
        activeOpacity={0.8}
      >
        <Animated.View style={[styles.buttonContent, { transform: [{ scale: animatedValue }] }]}>
          {isProcessing ? (
            <ActivityIndicator size="large" color="#FFFFFF" />
          ) : (
            <>
              <BiometricIcon 
                type={error ? 'error' : biometricIconType} 
                size={32} 
                color={error ? '#FF3B30' : '#FFFFFF'} 
              />
              <Text style={[styles.buttonText, error && styles.buttonTextError]}>
                {mode === 'setup' 
                  ? `Set up ${biometricTypeName}` 
                  : `Sign in with ${biometricTypeName}`
                }
              </Text>
            </>
          )}
        </Animated.View>
      </TouchableOpacity>

      {/* Status/Error Message */}
      {(error || getStatusMessage()) && (
        <View style={styles.messageContainer}>
          <Text style={[styles.messageText, error && styles.errorText]}>
            {error || getStatusMessage()}
          </Text>
        </View>
      )}

      {/* Setup Recommendation */}
      {mode === 'login' && shouldPromptSetup && showSetupPrompt && (
        <View style={styles.setupPromptContainer}>
          <Text style={styles.setupPromptText}>
            {getSetupRecommendation()}
          </Text>
          <TouchableOpacity 
            style={styles.setupButton}
            onPress={handleSetup}
            disabled={isProcessing}
          >
            <Text style={styles.setupButtonText}>Set Up Now</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Fallback Options */}
      {mode !== 'setup' && (
        <View style={styles.fallbackContainer}>
          <TouchableOpacity
            style={styles.fallbackButton}
            onPress={handleFallbackPress}
            disabled={isProcessing}
          >
            <Text style={styles.fallbackButtonText}>
              Use PIN/Password Instead
            </Text>
          </TouchableOpacity>
          
          {retryCount > 0 && (
            <Text style={styles.retryText}>
              {retryCount}/{maxRetries} attempts
            </Text>
          )}
        </View>
      )}

      {/* Debug/Details Toggle (Development Only) */}
      {__DEV__ && (
        <TouchableOpacity
          style={styles.debugButton}
          onPress={() => setShowDetails(!showDetails)}
        >
          <Text style={styles.debugButtonText}>
            {showDetails ? 'Hide' : 'Show'} Details
          </Text>
        </TouchableOpacity>
      )}

      {/* Debug Details (Development Only) */}
      {__DEV__ && showDetails && (
        <View style={styles.debugContainer}>
          <Text style={styles.debugText}>Status: {status}</Text>
          <Text style={styles.debugText}>Available: {isAvailable.toString()}</Text>
          <Text style={styles.debugText}>Setup: {isSetup.toString()}</Text>
          <Text style={styles.debugText}>Secure: {isSecure.toString()}</Text>
          <Text style={styles.debugText}>Type: {capabilities?.biometryType || 'Unknown'}</Text>
          <Text style={styles.debugText}>Recent Auth: {hasRecentAuth.toString()}</Text>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  biometricButton: {
    backgroundColor: '#007AFF',
    borderRadius: 12,
    paddingVertical: 16,
    paddingHorizontal: 24,
    minWidth: 200,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  biometricButtonProcessing: {
    backgroundColor: '#5856D6',
  },
  biometricButtonDisabled: {
    backgroundColor: '#8E8E93',
  },
  buttonContent: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    marginTop: 8,
    textAlign: 'center',
  },
  buttonTextError: {
    color: '#FF3B30',
  },
  messageContainer: {
    marginTop: 12,
    paddingHorizontal: 20,
  },
  messageText: {
    fontSize: 14,
    color: '#666666',
    textAlign: 'center',
    lineHeight: 20,
  },
  errorText: {
    color: '#FF3B30',
  },
  setupPromptContainer: {
    marginTop: 16,
    paddingHorizontal: 20,
    alignItems: 'center',
  },
  setupPromptText: {
    fontSize: 14,
    color: '#666666',
    textAlign: 'center',
    marginBottom: 12,
    lineHeight: 20,
  },
  setupButton: {
    backgroundColor: '#34C759',
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 16,
  },
  setupButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  fallbackContainer: {
    marginTop: 20,
    alignItems: 'center',
  },
  fallbackButton: {
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  fallbackButtonText: {
    color: '#007AFF',
    fontSize: 16,
    fontWeight: '500',
  },
  retryText: {
    fontSize: 12,
    color: '#999999',
    marginTop: 8,
  },
  compactContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  compactButton: {
    marginRight: 8,
  },
  compactText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  debugButton: {
    marginTop: 16,
    paddingVertical: 8,
    paddingHorizontal: 12,
    backgroundColor: '#F2F2F7',
    borderRadius: 6,
  },
  debugButtonText: {
    fontSize: 12,
    color: '#666666',
  },
  debugContainer: {
    marginTop: 12,
    padding: 12,
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    width: '100%',
  },
  debugText: {
    fontSize: 11,
    color: '#666666',
    marginVertical: 2,
  },
});

export default BiometricLoginComponent;