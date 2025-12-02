/**
 * QuickUnlockComponent - Quick biometric unlock for app resumption
 * Provides a streamlined biometric unlock experience when the app resumes
 * from background or when quick authentication is needed.
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  Animated,
  Dimensions,
  BackHandler,
  AppState,
  AppStateStatus,
} from 'react-native';
import { useBiometric } from '../../hooks/useBiometric';
import { AuthenticationMethod } from '../../services/biometric/types';

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

// Animated biometric icon component
const AnimatedBiometricIcon = ({ 
  type, 
  size = 64, 
  isAnimating = false 
}: {
  type: 'fingerprint' | 'face' | 'biometric';
  size?: number;
  isAnimating?: boolean;
}) => {
  const animatedValue = useRef(new Animated.Value(1)).current;
  const rotationValue = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (isAnimating) {
      const pulseAnimation = Animated.loop(
        Animated.sequence([
          Animated.timing(animatedValue, {
            toValue: 1.2,
            duration: 800,
            useNativeDriver: true,
          }),
          Animated.timing(animatedValue, {
            toValue: 1,
            duration: 800,
            useNativeDriver: true,
          }),
        ])
      );

      const rotationAnimation = Animated.loop(
        Animated.timing(rotationValue, {
          toValue: 1,
          duration: 2000,
          useNativeDriver: true,
        })
      );

      pulseAnimation.start();
      if (type === 'biometric') {
        rotationAnimation.start();
      }

      return () => {
        pulseAnimation.stop();
        rotationAnimation.stop();
      };
    }
  }, [isAnimating, animatedValue, rotationValue, type]);

  const iconMap = {
    fingerprint: '👆',
    face: '👤',
    biometric: '🔒',
  };

  const rotation = rotationValue.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  return (
    <Animated.View
      style={[
        styles.iconContainer,
        {
          transform: [
            { scale: animatedValue },
            ...(type === 'biometric' ? [{ rotate: rotation }] : []),
          ],
        },
      ]}
    >
      <Text style={[styles.biometricIcon, { fontSize: size }]}>
        {iconMap[type]}
      </Text>
    </Animated.View>
  );
};

export interface QuickUnlockComponentProps {
  userId: string;
  visible: boolean;
  onSuccess: () => void;
  onFallback?: () => void;
  onCancel?: () => void;
  autoTrigger?: boolean;
  fallbackMethod?: AuthenticationMethod;
  timeoutMs?: number;
  allowCancel?: boolean;
  title?: string;
  subtitle?: string;
}

export const QuickUnlockComponent: React.FC<QuickUnlockComponentProps> = ({
  userId,
  visible,
  onSuccess,
  onFallback,
  onCancel,
  autoTrigger = true,
  fallbackMethod = AuthenticationMethod.PIN,
  timeoutMs = 30000, // 30 seconds timeout
  allowCancel = true,
  title = 'Unlock App',
  subtitle = 'Use biometric authentication to continue',
}) => {
  const {
    canAuthenticate,
    capabilities,
    hasRecentAuth,
    quickAuth,
    authenticateWithFallback,
    getErrorMessage,
    isErrorRecoverable,
    clearError,
  } = useBiometric();

  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const [timeRemaining, setTimeRemaining] = useState(timeoutMs / 1000);
  
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const countdownRef = useRef<NodeJS.Timeout | null>(null);
  const appStateRef = useRef(AppState.currentState);

  const maxRetries = 3;

  // Determine biometric type and icon
  const biometricIconType = capabilities?.biometryType === 'FaceID' ? 'face' : 
                            capabilities?.biometryType === 'TouchID' || capabilities?.biometryType === 'Biometrics' ? 'fingerprint' : 
                            'biometric';
  
  const biometricName = capabilities?.biometryType === 'FaceID' ? 'Face ID' :
                        capabilities?.biometryType === 'TouchID' ? 'Touch ID' :
                        capabilities?.biometryType === 'Biometrics' ? 'Fingerprint' :
                        'Biometric';

  // Handle app state changes
  useEffect(() => {
    const handleAppStateChange = (nextAppState: AppStateStatus) => {
      if (
        appStateRef.current.match(/inactive|background/) &&
        nextAppState === 'active' &&
        visible
      ) {
        // App came to foreground, trigger quick auth if needed
        if (autoTrigger && canAuthenticate) {
          handleQuickAuth();
        }
      }
      appStateRef.current = nextAppState;
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => subscription?.remove();
  }, [visible, autoTrigger, canAuthenticate]);

  // Handle hardware back button
  useEffect(() => {
    if (!visible) return;

    const handleBackPress = () => {
      if (allowCancel && onCancel) {
        onCancel();
        return true;
      }
      return true; // Prevent default back action when unlock is required
    };

    const backHandler = BackHandler.addEventListener('hardwareBackPress', handleBackPress);
    return () => backHandler.remove();
  }, [visible, allowCancel, onCancel]);

  // Setup timeout and countdown
  useEffect(() => {
    if (!visible) return;

    // Reset states
    setError(null);
    setRetryCount(0);
    setTimeRemaining(timeoutMs / 1000);
    clearError();

    // Setup timeout
    timeoutRef.current = setTimeout(() => {
      if (onFallback) {
        onFallback();
      } else if (onCancel) {
        onCancel();
      }
    }, timeoutMs);

    // Setup countdown
    countdownRef.current = setInterval(() => {
      setTimeRemaining(prev => {
        if (prev <= 1) {
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
      if (countdownRef.current) {
        clearInterval(countdownRef.current);
      }
    };
  }, [visible, timeoutMs, onFallback, onCancel, clearError]);

  // Auto-trigger authentication
  useEffect(() => {
    if (visible && autoTrigger && canAuthenticate && !isProcessing) {
      // Check if we have recent authentication first
      if (hasRecentAuth) {
        onSuccess();
        return;
      }
      
      // Small delay to allow UI to render
      const timeout = setTimeout(() => {
        handleQuickAuth();
      }, 500);
      
      return () => clearTimeout(timeout);
    }
  }, [visible, autoTrigger, canAuthenticate, hasRecentAuth, isProcessing]);

  // Handle quick authentication
  const handleQuickAuth = useCallback(async () => {
    if (!canAuthenticate || isProcessing) return;

    try {
      setIsProcessing(true);
      setError(null);

      const success = await quickAuth(userId);
      
      if (success) {
        onSuccess();
      } else {
        const errorMessage = getErrorMessage() || 'Authentication failed';
        setError(errorMessage);
        setRetryCount(prev => prev + 1);

        // Auto-fallback after max retries
        if (retryCount >= maxRetries - 1) {
          setTimeout(() => {
            if (onFallback) {
              onFallback();
            }
          }, 1500);
        }
      }
    } catch (error: any) {
      const errorMessage = error.message || 'Authentication failed';
      setError(errorMessage);
      setRetryCount(prev => prev + 1);
    } finally {
      setIsProcessing(false);
    }
  }, [canAuthenticate, isProcessing, quickAuth, userId, onSuccess, getErrorMessage, retryCount, maxRetries, onFallback]);

  // Handle manual authentication with fallback
  const handleAuthenticate = useCallback(async () => {
    if (!canAuthenticate || isProcessing) return;

    try {
      setIsProcessing(true);
      setError(null);

      const success = await authenticateWithFallback(userId, fallbackMethod);
      
      if (success) {
        onSuccess();
      } else {
        const errorMessage = getErrorMessage() || 'Authentication failed';
        setError(errorMessage);
        setRetryCount(prev => prev + 1);

        if (retryCount >= maxRetries - 1 && onFallback) {
          setTimeout(() => onFallback(), 1500);
        }
      }
    } catch (error: any) {
      const errorMessage = error.message || 'Authentication failed';
      setError(errorMessage);
      setRetryCount(prev => prev + 1);
    } finally {
      setIsProcessing(false);
    }
  }, [canAuthenticate, isProcessing, authenticateWithFallback, userId, fallbackMethod, onSuccess, getErrorMessage, retryCount, maxRetries, onFallback]);

  // Handle fallback
  const handleFallback = useCallback(() => {
    if (onFallback) {
      onFallback();
    }
  }, [onFallback]);

  // Handle cancel
  const handleCancel = useCallback(() => {
    if (allowCancel && onCancel) {
      onCancel();
    }
  }, [allowCancel, onCancel]);

  if (!visible || !canAuthenticate) {
    return null;
  }

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={handleCancel}
    >
      <View style={styles.overlay}>
        <View style={styles.container}>
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.subtitle}>{subtitle}</Text>
          </View>

          {/* Biometric Icon */}
          <View style={styles.iconWrapper}>
            <AnimatedBiometricIcon
              type={biometricIconType}
              size={80}
              isAnimating={isProcessing}
            />
          </View>

          {/* Status Message */}
          <View style={styles.messageContainer}>
            {error ? (
              <Text style={styles.errorText}>{error}</Text>
            ) : isProcessing ? (
              <Text style={styles.statusText}>Authenticating...</Text>
            ) : (
              <Text style={styles.statusText}>
                Touch the {biometricName.toLowerCase()} sensor
              </Text>
            )}
          </View>

          {/* Retry Information */}
          {retryCount > 0 && (
            <Text style={styles.retryText}>
              Attempt {retryCount + 1} of {maxRetries}
            </Text>
          )}

          {/* Action Buttons */}
          <View style={styles.buttonContainer}>
            {!isProcessing && error && isErrorRecoverable() && retryCount < maxRetries && (
              <TouchableOpacity
                style={[styles.button, styles.primaryButton]}
                onPress={handleAuthenticate}
              >
                <Text style={styles.primaryButtonText}>Try Again</Text>
              </TouchableOpacity>
            )}

            {onFallback && (
              <TouchableOpacity
                style={[styles.button, styles.secondaryButton]}
                onPress={handleFallback}
              >
                <Text style={styles.secondaryButtonText}>Use PIN/Password</Text>
              </TouchableOpacity>
            )}

            {allowCancel && onCancel && (
              <TouchableOpacity
                style={[styles.button, styles.cancelButton]}
                onPress={handleCancel}
              >
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
            )}
          </View>

          {/* Timeout Indicator */}
          {timeRemaining > 0 && (
            <Text style={styles.timeoutText}>
              Timeout in {Math.ceil(timeRemaining)}s
            </Text>
          )}
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  container: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 24,
    marginHorizontal: 32,
    maxWidth: screenWidth - 64,
    alignItems: 'center',
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: '#000000',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
    lineHeight: 22,
  },
  iconWrapper: {
    marginVertical: 24,
  },
  iconContainer: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  biometricIcon: {
    textAlign: 'center',
  },
  messageContainer: {
    marginVertical: 16,
    alignItems: 'center',
  },
  statusText: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
  },
  errorText: {
    fontSize: 16,
    color: '#FF3B30',
    textAlign: 'center',
    lineHeight: 22,
  },
  retryText: {
    fontSize: 14,
    color: '#999999',
    marginBottom: 16,
  },
  buttonContainer: {
    width: '100%',
    marginTop: 16,
  },
  button: {
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 24,
    marginVertical: 6,
    alignItems: 'center',
  },
  primaryButton: {
    backgroundColor: '#007AFF',
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  secondaryButtonText: {
    color: '#007AFF',
    fontSize: 16,
    fontWeight: '500',
  },
  cancelButton: {
    backgroundColor: 'transparent',
  },
  cancelButtonText: {
    color: '#999999',
    fontSize: 16,
    fontWeight: '500',
  },
  timeoutText: {
    fontSize: 12,
    color: '#999999',
    marginTop: 16,
  },
});

export default QuickUnlockComponent;