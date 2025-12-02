import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Alert,
  Platform,
  TouchableOpacity,
  Animated,
  Vibration,
} from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';

import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { loginWithBiometric, setBiometricEnabled } from '../../store/slices/authSlice';
import { showToast } from '../../utils/toast';
import { hapticFeedback } from '../../utils/haptics';
import { COLORS, FONTS, SIZES } from '../../constants/theme';

interface BiometricAuthProps {
  onSuccess: () => void;
  onFallback: () => void;
  showFallback?: boolean;
  title?: string;
  subtitle?: string;
}

export const BiometricAuth: React.FC<BiometricAuthProps> = ({
  onSuccess,
  onFallback,
  showFallback = true,
  title = 'Unlock with Biometrics',
  subtitle = 'Use your fingerprint or face to access your account',
}) => {
  const dispatch = useAppDispatch();
  const { biometricEnabled, loading } = useAppSelector((state) => state.auth);

  const [biometricType, setBiometricType] = useState<string>('');
  const [isAvailable, setIsAvailable] = useState(false);
  const [animatedValue] = useState(new Animated.Value(0));
  const [scanningAnimation] = useState(new Animated.Value(1));

  useEffect(() => {
    checkBiometricAvailability();
  }, []);

  useEffect(() => {
    if (isAvailable && biometricEnabled) {
      startScanningAnimation();
    }
  }, [isAvailable, biometricEnabled]);

  const checkBiometricAvailability = async () => {
    try {
      // Check if device has biometric hardware
      const hasHardware = await LocalAuthentication.hasHardwareAsync();
      if (!hasHardware) {
        console.log('Biometric hardware not available');
        return;
      }

      // Check if biometrics are enrolled
      const isEnrolled = await LocalAuthentication.isEnrolledAsync();
      if (!isEnrolled) {
        Alert.alert(
          'Biometric Not Set Up',
          'Please set up biometric authentication in your device settings.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Settings', onPress: () => LocalAuthentication.openSettingsAsync() },
          ]
        );
        return;
      }

      // Get supported authentication types
      const supportedTypes = await LocalAuthentication.supportedAuthenticationTypesAsync();
      
      let type = '';
      if (supportedTypes.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) {
        type = Platform.OS === 'ios' ? 'Face ID' : 'Face Recognition';
      } else if (supportedTypes.includes(LocalAuthentication.AuthenticationType.FINGERPRINT)) {
        type = Platform.OS === 'ios' ? 'Touch ID' : 'Fingerprint';
      } else if (supportedTypes.includes(LocalAuthentication.AuthenticationType.IRIS)) {
        type = 'Iris Recognition';
      }

      setBiometricType(type);
      setIsAvailable(true);

      // Auto-trigger biometric authentication if enabled
      if (biometricEnabled) {
        setTimeout(() => {
          authenticateWithBiometric();
        }, 500);
      }

    } catch (error) {
      console.error('Error checking biometric availability:', error);
    }
  };

  const authenticateWithBiometric = async () => {
    try {
      hapticFeedback.light();

      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: title,
        subtitle: subtitle,
        cancelLabel: 'Use PIN',
        fallbackLabel: showFallback ? 'Use PIN Instead' : '',
        disableDeviceFallback: !showFallback,
        requireConfirmation: false,
      });

      if (result.success) {
        // Successful biometric authentication
        hapticFeedback.success();
        startSuccessAnimation();
        
        // Retrieve stored biometric token
        const biometricToken = await SecureStore.getItemAsync('biometric_token');
        
        if (biometricToken) {
          // Login with biometric token
          dispatch(loginWithBiometric(biometricToken));
          
          setTimeout(() => {
            onSuccess();
          }, 500);
        } else {
          // No stored token, fall back to PIN
          showToast('Please log in with your PIN to enable biometric authentication');
          onFallback();
        }

      } else if (result.error === 'user_cancel') {
        // User cancelled
        hapticFeedback.light();
        if (showFallback) {
          onFallback();
        }
      } else if (result.error === 'user_fallback') {
        // User chose fallback option
        hapticFeedback.light();
        onFallback();
      } else {
        // Authentication failed
        hapticFeedback.error();
        Vibration.vibrate(400);
        
        showToast('Biometric authentication failed. Please try again.');
        
        if (showFallback) {
          setTimeout(() => {
            onFallback();
          }, 1000);
        }
      }

    } catch (error) {
      console.error('Biometric authentication error:', error);
      hapticFeedback.error();
      showToast('Authentication error. Please try again.');
      
      if (showFallback) {
        onFallback();
      }
    }
  };

  const startScanningAnimation = () => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(scanningAnimation, {
          toValue: 0.3,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(scanningAnimation, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    ).start();
  };

  const startSuccessAnimation = () => {
    Animated.sequence([
      Animated.timing(animatedValue, {
        toValue: 1,
        duration: 200,
        useNativeDriver: true,
      }),
      Animated.timing(animatedValue, {
        toValue: 0,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start();
  };

  const enableBiometric = async () => {
    try {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'Enable Biometric Authentication',
        subtitle: 'Verify your identity to enable biometric login',
        cancelLabel: 'Cancel',
      });

      if (result.success) {
        // Generate and store biometric token
        const biometricToken = await generateBiometricToken();
        await SecureStore.setItemAsync('biometric_token', biometricToken);
        
        dispatch(setBiometricEnabled(true));
        hapticFeedback.success();
        showToast('Biometric authentication enabled');
      }
    } catch (error) {
      console.error('Error enabling biometric:', error);
      showToast('Failed to enable biometric authentication');
    }
  };

  const generateBiometricToken = async (): Promise<string> => {
    // Generate a secure token for biometric authentication
    // This would typically be done on the server
    const timestamp = Date.now().toString();
    const random = Math.random().toString(36).substring(2, 15);
    return `bio_${timestamp}_${random}`;
  };

  const getBiometricIcon = () => {
    switch (biometricType) {
      case 'Face ID':
      case 'Face Recognition':
        return 'face-recognition-outline';
      case 'Touch ID':
      case 'Fingerprint':
        return 'finger-print-outline';
      case 'Iris Recognition':
        return 'eye-outline';
      default:
        return 'shield-checkmark-outline';
    }
  };

  if (!isAvailable) {
    return (
      <View style={styles.unavailableContainer}>
        <Ionicons name="shield-outline" size={64} color={COLORS.gray} />
        <Text style={styles.unavailableText}>
          Biometric authentication not available
        </Text>
        {showFallback && (
          <TouchableOpacity style={styles.fallbackButton} onPress={onFallback}>
            <Text style={styles.fallbackButtonText}>Use PIN Instead</Text>
          </TouchableOpacity>
        )}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <LinearGradient
        colors={['rgba(102, 126, 234, 0.1)', 'rgba(118, 75, 162, 0.1)']}
        style={styles.gradientBackground}
      />
      
      <View style={styles.content}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>

        <View style={styles.biometricContainer}>
          <Animated.View 
            style={[
              styles.biometricIcon,
              {
                opacity: scanningAnimation,
                transform: [
                  {
                    scale: animatedValue.interpolate({
                      inputRange: [0, 1],
                      outputRange: [1, 1.2],
                    }),
                  },
                ],
              },
            ]}
          >
            <TouchableOpacity
              style={styles.iconButton}
              onPress={authenticateWithBiometric}
              disabled={loading}
            >
              <LinearGradient
                colors={[COLORS.primary, COLORS.secondary]}
                style={styles.iconGradient}
              >
                <Ionicons
                  name={getBiometricIcon()}
                  size={48}
                  color={COLORS.white}
                />
              </LinearGradient>
            </TouchableOpacity>
          </Animated.View>

          <Text style={styles.biometricType}>{biometricType}</Text>
          <Text style={styles.instruction}>
            {biometricEnabled 
              ? 'Touch the sensor or look at the camera'
              : 'Set up biometric authentication for quick access'
            }
          </Text>
        </View>

        {!biometricEnabled && (
          <TouchableOpacity
            style={styles.enableButton}
            onPress={enableBiometric}
          >
            <Text style={styles.enableButtonText}>
              Enable {biometricType}
            </Text>
          </TouchableOpacity>
        )}

        {showFallback && (
          <TouchableOpacity
            style={styles.fallbackButton}
            onPress={onFallback}
          >
            <Text style={styles.fallbackButtonText}>
              Use PIN Instead
            </Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: COLORS.background,
  },
  gradientBackground: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
  content: {
    alignItems: 'center',
    paddingHorizontal: SIZES.padding,
  },
  title: {
    fontSize: SIZES.h2,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
    textAlign: 'center',
    marginBottom: SIZES.base,
  },
  subtitle: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    textAlign: 'center',
    marginBottom: SIZES.padding * 2,
  },
  biometricContainer: {
    alignItems: 'center',
    marginBottom: SIZES.padding * 2,
  },
  biometricIcon: {
    marginBottom: SIZES.padding,
  },
  iconButton: {
    borderRadius: 60,
    padding: 4,
  },
  iconGradient: {
    width: 120,
    height: 120,
    borderRadius: 60,
    justifyContent: 'center',
    alignItems: 'center',
  },
  biometricType: {
    fontSize: SIZES.h3,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
    marginBottom: SIZES.base,
  },
  instruction: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    textAlign: 'center',
    lineHeight: 20,
  },
  enableButton: {
    backgroundColor: COLORS.primary,
    paddingHorizontal: SIZES.padding * 2,
    paddingVertical: SIZES.base,
    borderRadius: SIZES.radius,
    marginBottom: SIZES.padding,
  },
  enableButtonText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
  },
  fallbackButton: {
    paddingHorizontal: SIZES.padding,
    paddingVertical: SIZES.base,
  },
  fallbackButtonText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.medium,
    color: COLORS.primary,
  },
  unavailableContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: SIZES.padding,
  },
  unavailableText: {
    fontSize: SIZES.body2,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    textAlign: 'center',
    marginTop: SIZES.padding,
    marginBottom: SIZES.padding * 2,
  },
});

export default BiometricAuth;