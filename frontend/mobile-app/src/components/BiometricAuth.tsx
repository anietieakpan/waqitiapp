import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  Image,
  Alert,
  Platform,
  ActivityIndicator,
  Animated,
  Vibration,
} from 'react-native';
import TouchID from 'react-native-touch-id';
import FaceID from 'react-native-face-id';
import PasscodeAuth from 'react-native-passcode-auth';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useDispatch, useSelector } from 'react-redux';
import { setBiometricEnabled, setSecurityLevel } from '../../store/slices/authSlice';
import { colors, fonts } from '../../theme';
import { HapticService } from '../../services/HapticService';

interface BiometricAuthProps {
  onSuccess: () => void;
  onCancel?: () => void;
  onFallback?: () => void;
  reason?: string;
  title?: string;
  subtitle?: string;
  fallbackLabel?: string;
  cancelLabel?: string;
  visible: boolean;
  required?: boolean;
  showPasscodeOption?: boolean;
}

interface BiometricConfig {
  title: string;
  imageUrl?: string;
  sensorDescription: string;
  sensorErrorDescription: string;
  cancelText: string;
  fallbackLabel?: string;
  unifiedErrors: boolean;
  passcodeFallback: boolean;
}

const BiometricAuth: React.FC<BiometricAuthProps> = ({
  onSuccess,
  onCancel,
  onFallback,
  reason = 'Authenticate to continue',
  title = 'Authentication Required',
  subtitle,
  fallbackLabel = 'Use Passcode',
  cancelLabel = 'Cancel',
  visible,
  required = false,
  showPasscodeOption = true,
}) => {
  const dispatch = useDispatch();
  const { biometricType, biometricEnabled } = useSelector((state: any) => state.auth);
  
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempts, setAttempts] = useState(0);
  const [showPasscode, setShowPasscode] = useState(false);
  const animatedValue = new Animated.Value(0);
  
  const MAX_ATTEMPTS = 3;
  const LOCKOUT_DURATION = 30000; // 30 seconds

  useEffect(() => {
    if (visible && biometricEnabled) {
      authenticate();
    }
  }, [visible]);

  useEffect(() => {
    checkBiometricSupport();
  }, []);

  const checkBiometricSupport = async () => {
    try {
      if (Platform.OS === 'ios') {
        const biometryType = await TouchID.isSupported();
        dispatch(setBiometricType(biometryType));
      } else {
        // Android
        const isSupported = await TouchID.isSupported();
        if (isSupported) {
          dispatch(setBiometricType('TouchID'));
        }
      }
    } catch (error) {
      console.log('Biometric not supported:', error);
      dispatch(setBiometricType(null));
    }
  };

  const authenticate = useCallback(async () => {
    if (isAuthenticating) return;
    
    setIsAuthenticating(true);
    setError(null);
    
    try {
      // Check if user is locked out
      const lockoutTime = await AsyncStorage.getItem('biometric_lockout');
      if (lockoutTime) {
        const timePassed = Date.now() - parseInt(lockoutTime, 10);
        if (timePassed < LOCKOUT_DURATION) {
          const remainingTime = Math.ceil((LOCKOUT_DURATION - timePassed) / 1000);
          setError(`Too many attempts. Try again in ${remainingTime} seconds.`);
          setIsAuthenticating(false);
          return;
        } else {
          await AsyncStorage.removeItem('biometric_lockout');
        }
      }
      
      const biometricConfig: BiometricConfig = {
        title: title,
        sensorDescription: reason,
        sensorErrorDescription: 'Authentication failed',
        cancelText: cancelLabel,
        fallbackLabel: showPasscodeOption ? fallbackLabel : undefined,
        unifiedErrors: false,
        passcodeFallback: showPasscodeOption,
      };
      
      if (Platform.OS === 'ios' && biometricType === 'FaceID') {
        // Use Face ID
        const result = await FaceID.authenticate(reason, fallbackLabel);
        handleAuthSuccess();
      } else {
        // Use Touch ID or Android biometrics
        const result = await TouchID.authenticate(reason, biometricConfig);
        handleAuthSuccess();
      }
      
    } catch (error: any) {
      handleAuthError(error);
    } finally {
      setIsAuthenticating(false);
    }
  }, [isAuthenticating, attempts]);

  const handleAuthSuccess = async () => {
    // Clear any lockout
    await AsyncStorage.removeItem('biometric_lockout');
    setAttempts(0);
    
    // Success animation
    Animated.spring(animatedValue, {
      toValue: 1,
      useNativeDriver: true,
      tension: 50,
      friction: 3,
    }).start(() => {
      HapticService.success();
      onSuccess();
    });
    
    // Record successful auth
    await recordAuthEvent('success');
    
    // Update security level
    dispatch(setSecurityLevel('high'));
  };

  const handleAuthError = async (error: any) => {
    const newAttempts = attempts + 1;
    setAttempts(newAttempts);
    
    HapticService.error();
    Vibration.vibrate(100);
    
    // Shake animation
    Animated.sequence([
      Animated.timing(animatedValue, { toValue: 10, duration: 100, useNativeDriver: true }),
      Animated.timing(animatedValue, { toValue: -10, duration: 100, useNativeDriver: true }),
      Animated.timing(animatedValue, { toValue: 10, duration: 100, useNativeDriver: true }),
      Animated.timing(animatedValue, { toValue: 0, duration: 100, useNativeDriver: true }),
    ]).start();
    
    if (error.code === 'UserCancel') {
      if (required) {
        setError('Authentication is required to continue');
        setTimeout(() => authenticate(), 1500);
      } else {
        onCancel?.();
      }
    } else if (error.code === 'UserFallback') {
      if (showPasscodeOption) {
        setShowPasscode(true);
      } else {
        onFallback?.();
      }
    } else if (error.code === 'BiometryNotEnrolled') {
      setError('Please set up biometric authentication in your device settings');
      if (showPasscodeOption) {
        setTimeout(() => setShowPasscode(true), 2000);
      }
    } else if (error.code === 'PasscodeNotSet') {
      setError('Please set up a device passcode');
    } else {
      setError(error.message || 'Authentication failed');
      
      if (newAttempts >= MAX_ATTEMPTS) {
        // Lock out user
        await AsyncStorage.setItem('biometric_lockout', Date.now().toString());
        setError('Too many failed attempts. Please try again later.');
        
        // Record security event
        await recordAuthEvent('lockout');
        
        // Notify security service
        await notifySecurityService('biometric_lockout', {
          attempts: newAttempts,
          timestamp: Date.now(),
        });
      }
    }
  };

  const authenticateWithPasscode = async () => {
    try {
      const result = await PasscodeAuth.authenticate(reason);
      if (result) {
        handleAuthSuccess();
      }
    } catch (error) {
      setError('Passcode authentication failed');
    }
  };

  const recordAuthEvent = async (type: 'success' | 'failure' | 'lockout') => {
    try {
      const events = await AsyncStorage.getItem('auth_events');
      const eventList = events ? JSON.parse(events) : [];
      
      eventList.push({
        type,
        timestamp: Date.now(),
        biometricType,
        deviceId: await getDeviceId(),
      });
      
      // Keep last 100 events
      if (eventList.length > 100) {
        eventList.splice(0, eventList.length - 100);
      }
      
      await AsyncStorage.setItem('auth_events', JSON.stringify(eventList));
    } catch (error) {
      console.error('Failed to record auth event:', error);
    }
  };

  const notifySecurityService = async (event: string, data: any) => {
    try {
      // Send security event to backend
      await fetch('/api/security/events', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          event,
          data,
          timestamp: Date.now(),
        }),
      });
    } catch (error) {
      console.error('Failed to notify security service:', error);
    }
  };

  const getDeviceId = async (): Promise<string> => {
    let deviceId = await AsyncStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = `${Platform.OS}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      await AsyncStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  };

  const getBiometricIcon = () => {
    if (Platform.OS === 'ios' && biometricType === 'FaceID') {
      return 'face';
    }
    return 'fingerprint';
  };

  const getBiometricText = () => {
    if (Platform.OS === 'ios' && biometricType === 'FaceID') {
      return 'Face ID';
    }
    return 'Touch ID';
  };

  if (!visible) return null;

  if (showPasscode) {
    return (
      <Modal
        animationType="slide"
        transparent={true}
        visible={visible}
        onRequestClose={() => !required && onCancel?.()}
      >
        <View style={styles.container}>
          <View style={styles.passcodeContainer}>
            <Text style={styles.title}>Enter Passcode</Text>
            <Text style={styles.subtitle}>{reason}</Text>
            {/* Passcode input UI would go here */}
            <TouchableOpacity
              style={styles.fallbackButton}
              onPress={() => setShowPasscode(false)}
            >
              <Text style={styles.fallbackText}>Use {getBiometricText()}</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    );
  }

  return (
    <Modal
      animationType="fade"
      transparent={true}
      visible={visible}
      onRequestClose={() => !required && onCancel?.()}
    >
      <View style={styles.container}>
        <Animated.View
          style={[
            styles.authContainer,
            {
              transform: [
                {
                  translateX: animatedValue,
                },
                {
                  scale: animatedValue.interpolate({
                    inputRange: [0, 1],
                    outputRange: [1, 1.1],
                  }),
                },
              ],
            },
          ]}
        >
          <View style={styles.iconContainer}>
            <Icon
              name={getBiometricIcon()}
              size={80}
              color={colors.primary}
            />
            {isAuthenticating && (
              <ActivityIndicator
                style={styles.loader}
                size="large"
                color={colors.primary}
              />
            )}
          </View>
          
          <Text style={styles.title}>{title}</Text>
          {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
          <Text style={styles.reason}>{reason}</Text>
          
          {error && (
            <View style={styles.errorContainer}>
              <Icon name="error-outline" size={20} color={colors.error} />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}
          
          {attempts > 0 && attempts < MAX_ATTEMPTS && (
            <Text style={styles.attemptsText}>
              {MAX_ATTEMPTS - attempts} attempts remaining
            </Text>
          )}
          
          <View style={styles.buttonContainer}>
            {!isAuthenticating && (
              <>
                <TouchableOpacity
                  style={styles.primaryButton}
                  onPress={authenticate}
                  disabled={isAuthenticating}
                >
                  <Icon name={getBiometricIcon()} size={24} color="white" />
                  <Text style={styles.primaryButtonText}>
                    Try {getBiometricText()} Again
                  </Text>
                </TouchableOpacity>
                
                {showPasscodeOption && (
                  <TouchableOpacity
                    style={styles.secondaryButton}
                    onPress={() => setShowPasscode(true)}
                  >
                    <Text style={styles.secondaryButtonText}>{fallbackLabel}</Text>
                  </TouchableOpacity>
                )}
                
                {!required && (
                  <TouchableOpacity
                    style={styles.cancelButton}
                    onPress={onCancel}
                  >
                    <Text style={styles.cancelButtonText}>{cancelLabel}</Text>
                  </TouchableOpacity>
                )}
              </>
            )}
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  authContainer: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 30,
    width: '85%',
    maxWidth: 400,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  passcodeContainer: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 30,
    width: '85%',
    maxWidth: 400,
    alignItems: 'center',
  },
  iconContainer: {
    marginBottom: 20,
    position: 'relative',
  },
  loader: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginTop: -20,
    marginLeft: -20,
  },
  title: {
    fontSize: 24,
    fontFamily: fonts.bold,
    color: colors.text.primary,
    marginBottom: 10,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    fontFamily: fonts.regular,
    color: colors.text.secondary,
    marginBottom: 5,
    textAlign: 'center',
  },
  reason: {
    fontSize: 14,
    fontFamily: fonts.regular,
    color: colors.text.secondary,
    marginBottom: 20,
    textAlign: 'center',
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.error + '20',
    padding: 10,
    borderRadius: 8,
    marginBottom: 15,
  },
  errorText: {
    fontSize: 14,
    fontFamily: fonts.regular,
    color: colors.error,
    marginLeft: 8,
  },
  attemptsText: {
    fontSize: 14,
    fontFamily: fonts.regular,
    color: colors.warning,
    marginBottom: 15,
  },
  buttonContainer: {
    width: '100%',
  },
  primaryButton: {
    flexDirection: 'row',
    backgroundColor: colors.primary,
    paddingVertical: 15,
    paddingHorizontal: 30,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 15,
  },
  primaryButtonText: {
    color: 'white',
    fontSize: 16,
    fontFamily: fonts.semiBold,
    marginLeft: 10,
  },
  secondaryButton: {
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 10,
  },
  secondaryButtonText: {
    color: colors.primary,
    fontSize: 16,
    fontFamily: fonts.medium,
  },
  cancelButton: {
    paddingVertical: 12,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: colors.text.secondary,
    fontSize: 16,
    fontFamily: fonts.regular,
  },
  fallbackButton: {
    marginTop: 20,
    paddingVertical: 12,
  },
  fallbackText: {
    color: colors.primary,
    fontSize: 16,
    fontFamily: fonts.medium,
  },
});

export default BiometricAuth;