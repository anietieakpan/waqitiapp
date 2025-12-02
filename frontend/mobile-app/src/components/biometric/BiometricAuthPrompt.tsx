/**
 * Biometric Auth Prompt - Reusable biometric authentication component
 * Handles authentication flow with fallback options and error recovery
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Animated,
  Vibration,
  Platform,
} from 'react-native';
import {
  Modal,
  Portal,
  Card,
  Title,
  Paragraph,
  Button,
  ActivityIndicator,
  IconButton,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

import useBiometric from '../../hooks/useBiometric';
import { useAppSelector } from '../../hooks/redux';
import { 
  BiometricAuthError, 
  AuthenticationMethod,
  BiometricPromptConfig 
} from '../../services/biometric/types';
import { promptForPin } from '../security/SecurityDialogManager';

interface BiometricAuthPromptProps {
  visible: boolean;
  onSuccess: (token?: string) => void;
  onCancel: () => void;
  userId?: string;
  title?: string;
  subtitle?: string;
  description?: string;
  allowCancel?: boolean;
  allowFallback?: boolean;
  autoAuthenticate?: boolean;
  requireConfirmation?: boolean;
}

const BiometricAuthPrompt: React.FC<BiometricAuthPromptProps> = ({
  visible,
  onSuccess,
  onCancel,
  userId: propUserId,
  title = 'Authentication Required',
  subtitle = 'Verify your identity to continue',
  description,
  allowCancel = true,
  allowFallback = true,
  autoAuthenticate = true,
  requireConfirmation = false,
}) => {
  const { user } = useAppSelector((state) => state.auth);
  const biometric = useBiometric();
  
  const userId = propUserId || user?.id || '';
  
  // State
  const [authenticating, setAuthenticating] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [showFallback, setShowFallback] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  
  // Animations
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.9)).current;
  const shakeAnim = useRef(new Animated.Value(0)).current;
  
  // Max retry attempts before showing fallback
  const MAX_RETRIES = 3;

  // Get biometric icon based on type
  const getBiometricIcon = (): string => {
    if (!biometric.capabilities) return 'fingerprint';
    
    switch (biometric.capabilities.biometryType) {
      case 'FaceID':
        return 'face-recognition';
      case 'TouchID':
      case 'Biometrics':
      default:
        return 'fingerprint';
    }
  };

  // Get biometric name
  const getBiometricName = (): string => {
    if (!biometric.capabilities) return 'biometric';
    
    switch (biometric.capabilities.biometryType) {
      case 'TouchID':
        return 'Touch ID';
      case 'FaceID':
        return 'Face ID';
      case 'Biometrics':
      default:
        return 'fingerprint';
    }
  };

  // Animation effects
  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.spring(scaleAnim, {
          toValue: 1,
          friction: 8,
          tension: 40,
          useNativeDriver: true,
        }),
      ]).start();

      if (autoAuthenticate && biometric.canAuthenticate) {
        setTimeout(() => {
          handleBiometricAuth();
        }, 500);
      }
    } else {
      fadeAnim.setValue(0);
      scaleAnim.setValue(0.9);
      setRetryCount(0);
      setShowFallback(false);
      setErrorMessage(null);
    }
  }, [visible]);

  // Shake animation for errors
  const triggerShakeAnimation = () => {
    Animated.sequence([
      Animated.timing(shakeAnim, {
        toValue: 10,
        duration: 100,
        useNativeDriver: true,
      }),
      Animated.timing(shakeAnim, {
        toValue: -10,
        duration: 100,
        useNativeDriver: true,
      }),
      Animated.timing(shakeAnim, {
        toValue: 10,
        duration: 100,
        useNativeDriver: true,
      }),
      Animated.timing(shakeAnim, {
        toValue: 0,
        duration: 100,
        useNativeDriver: true,
      }),
    ]).start();

    // Haptic feedback on error
    if (Platform.OS === 'ios' || Platform.OS === 'android') {
      Vibration.vibrate(100);
    }
  };

  // Handle biometric authentication
  const handleBiometricAuth = async () => {
    if (!biometric.canAuthenticate) {
      setErrorMessage('Biometric authentication is not available');
      setShowFallback(true);
      return;
    }

    setAuthenticating(true);
    setErrorMessage(null);

    try {
      const promptConfig: BiometricPromptConfig = {
        title,
        subtitle,
        description: description || `Authenticate using ${getBiometricName()}`,
        negativeButtonText: allowFallback ? 'Use Fallback' : 'Cancel',
        allowDeviceCredentials: false,
        requireConfirmation,
      };

      const result = await biometric.authenticate(userId, promptConfig);

      if (result.success) {
        // Success animation
        Animated.spring(scaleAnim, {
          toValue: 1.1,
          friction: 8,
          tension: 40,
          useNativeDriver: true,
        }).start(() => {
          onSuccess(result.token);
        });
      } else {
        // Handle authentication failure
        handleAuthError(result.error, result.errorMessage);
      }
    } catch (error: any) {
      setErrorMessage(error.message || 'Authentication failed');
      triggerShakeAnimation();
      
      if (retryCount >= MAX_RETRIES - 1) {
        setShowFallback(true);
      }
      setRetryCount(prev => prev + 1);
    } finally {
      setAuthenticating(false);
    }
  };

  // Handle authentication errors
  const handleAuthError = (
    error?: BiometricAuthError,
    errorMessage?: string
  ) => {
    triggerShakeAnimation();
    
    switch (error) {
      case BiometricAuthError.USER_CANCELLED:
        if (allowCancel) {
          onCancel();
        } else {
          setErrorMessage('Authentication is required to continue');
        }
        break;
        
      case BiometricAuthError.AUTHENTICATION_FAILED:
        setRetryCount(prev => prev + 1);
        if (retryCount >= MAX_RETRIES - 1) {
          setErrorMessage('Too many failed attempts');
          setShowFallback(true);
        } else {
          setErrorMessage(`Authentication failed. ${MAX_RETRIES - retryCount - 1} attempts remaining`);
        }
        break;
        
      case BiometricAuthError.TEMPORARILY_LOCKED:
        setErrorMessage('Biometric authentication is temporarily locked');
        setShowFallback(true);
        break;
        
      case BiometricAuthError.NOT_ENROLLED:
        setErrorMessage('No biometric credentials are enrolled');
        setShowFallback(true);
        break;
        
      default:
        setErrorMessage(errorMessage || 'Authentication failed');
        if (retryCount >= MAX_RETRIES - 1) {
          setShowFallback(true);
        }
        setRetryCount(prev => prev + 1);
    }
  };

  // Handle fallback authentication
  const handleFallbackAuth = async (method: AuthenticationMethod) => {
    setAuthenticating(true);
    setErrorMessage(null);

    try {
      let success = false;
      
      switch (method) {
        case AuthenticationMethod.PIN:
          const pinResult = await promptForPin(
            'Enter PIN',
            'Enter your 6-digit PIN to authenticate'
          );
          success = pinResult.success;
          break;
          
        case AuthenticationMethod.PASSWORD:
          // Implement password prompt
          const result = await biometric.handleFallback(userId, method);
          success = result.success;
          break;
          
        case AuthenticationMethod.DEVICE_CREDENTIALS:
          const deviceResult = await biometric.authenticateWithFallback(
            userId,
            AuthenticationMethod.DEVICE_CREDENTIALS
          );
          success = deviceResult;
          break;
          
        default:
          throw new Error('Unsupported fallback method');
      }

      if (success) {
        onSuccess();
      } else {
        setErrorMessage('Fallback authentication failed');
        triggerShakeAnimation();
      }
    } catch (error: any) {
      setErrorMessage(error.message || 'Fallback authentication failed');
      triggerShakeAnimation();
    } finally {
      setAuthenticating(false);
    }
  };

  // Render main authentication view
  const renderAuthView = () => (
    <View style={styles.authContent}>
      <View style={styles.iconContainer}>
        <Icon 
          name={getBiometricIcon()} 
          size={80} 
          color={errorMessage ? '#F44336' : '#2196F3'} 
        />
        {authenticating && (
          <ActivityIndicator 
            style={styles.loadingOverlay} 
            size="large" 
            color="#2196F3" 
          />
        )}
      </View>

      <Title style={styles.title}>{title}</Title>
      <Paragraph style={styles.subtitle}>{subtitle}</Paragraph>
      
      {description && (
        <Paragraph style={styles.description}>{description}</Paragraph>
      )}

      {errorMessage && (
        <Animated.View style={{ transform: [{ translateX: shakeAnim }] }}>
          <Card style={styles.errorCard}>
            <Card.Content>
              <Text style={styles.errorText}>{errorMessage}</Text>
            </Card.Content>
          </Card>
        </Animated.View>
      )}

      <View style={styles.buttonContainer}>
        {!authenticating && (
          <>
            <Button
              mode="contained"
              onPress={handleBiometricAuth}
              disabled={!biometric.canAuthenticate || retryCount >= MAX_RETRIES}
              style={styles.primaryButton}
              icon={getBiometricIcon()}
            >
              {`Try ${getBiometricName()} Again`}
            </Button>

            {(showFallback || !biometric.canAuthenticate) && allowFallback && (
              <Button
                mode="outlined"
                onPress={() => setShowFallback(true)}
                style={styles.fallbackButton}
                icon="key-variant"
              >
                Use Alternative Method
              </Button>
            )}

            {allowCancel && (
              <Button
                mode="text"
                onPress={onCancel}
                style={styles.cancelButton}
              >
                Cancel
              </Button>
            )}
          </>
        )}
      </View>
    </View>
  );

  // Render fallback options
  const renderFallbackView = () => (
    <View style={styles.fallbackContent}>
      <Title style={styles.title}>Alternative Authentication</Title>
      <Paragraph style={styles.subtitle}>
        Choose an alternative method to authenticate
      </Paragraph>

      <View style={styles.fallbackOptions}>
        <TouchableOpacity
          style={styles.fallbackOption}
          onPress={() => handleFallbackAuth(AuthenticationMethod.PIN)}
        >
          <Icon name="dialpad" size={40} color="#2196F3" />
          <Text style={styles.fallbackOptionText}>Use PIN</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.fallbackOption}
          onPress={() => handleFallbackAuth(AuthenticationMethod.PASSWORD)}
        >
          <Icon name="lock" size={40} color="#2196F3" />
          <Text style={styles.fallbackOptionText}>Use Password</Text>
        </TouchableOpacity>

        {Platform.OS === 'android' && (
          <TouchableOpacity
            style={styles.fallbackOption}
            onPress={() => handleFallbackAuth(AuthenticationMethod.DEVICE_CREDENTIALS)}
          >
            <Icon name="cellphone-lock" size={40} color="#2196F3" />
            <Text style={styles.fallbackOptionText}>Device Lock</Text>
          </TouchableOpacity>
        )}
      </View>

      <Button
        mode="text"
        onPress={() => setShowFallback(false)}
        style={styles.backButton}
        disabled={authenticating}
      >
        Back to Biometric
      </Button>
    </View>
  );

  return (
    <Portal>
      <Modal
        visible={visible}
        onDismiss={allowCancel ? onCancel : undefined}
        contentContainerStyle={styles.modal}
      >
        <Animated.View 
          style={[
            styles.container,
            {
              opacity: fadeAnim,
              transform: [{ scale: scaleAnim }],
            },
          ]}
        >
          <Card style={styles.card}>
            <Card.Content>
              {showFallback && allowFallback
                ? renderFallbackView()
                : renderAuthView()
              }
            </Card.Content>
          </Card>
        </Animated.View>
      </Modal>
    </Portal>
  );
};

const styles = StyleSheet.create({
  modal: {
    padding: 20,
  },
  container: {
    alignItems: 'center',
  },
  card: {
    width: '100%',
    maxWidth: 400,
  },
  authContent: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  fallbackContent: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  iconContainer: {
    position: 'relative',
    marginBottom: 20,
  },
  loadingOverlay: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginTop: -20,
    marginLeft: -20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    color: '#666',
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    textAlign: 'center',
    color: '#888',
    marginBottom: 20,
  },
  errorCard: {
    backgroundColor: '#FFEBEE',
    marginVertical: 16,
    width: '100%',
  },
  errorText: {
    color: '#C62828',
    fontSize: 14,
    textAlign: 'center',
  },
  buttonContainer: {
    width: '100%',
    marginTop: 20,
  },
  primaryButton: {
    marginBottom: 12,
  },
  fallbackButton: {
    marginBottom: 12,
  },
  cancelButton: {
    marginTop: 8,
  },
  fallbackOptions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    width: '100%',
    marginVertical: 30,
  },
  fallbackOption: {
    alignItems: 'center',
    padding: 16,
  },
  fallbackOptionText: {
    marginTop: 8,
    fontSize: 12,
    color: '#666',
  },
  backButton: {
    marginTop: 16,
  },
});

export default BiometricAuthPrompt;