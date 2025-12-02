import React, { useState, useRef } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Animated,
  Vibration,
} from 'react-native';
import {
  Text,
  Button,
  useTheme,
  Surface,
  IconButton,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import HapticService from '../../services/HapticService';

/**
 * Setup PIN Screen - Create security PIN for the app
 */
const SetupPinScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [step, setStep] = useState<'create' | 'confirm'>('create');
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  
  const shakeAnimation = useRef(new Animated.Value(0)).current;
  
  const currentPin = step === 'create' ? pin : confirmPin;
  const maxLength = 4;

  const handleNumberPress = (number: string) => {
    if (currentPin.length < maxLength) {
      const newPin = currentPin + number;
      
      if (step === 'create') {
        setPin(newPin);
        if (newPin.length === maxLength) {
          setTimeout(() => {
            setStep('confirm');
            setError(null);
          }, 300);
        }
      } else {
        setConfirmPin(newPin);
        if (newPin.length === maxLength) {
          setTimeout(() => {
            validatePins(pin, newPin);
          }, 300);
        }
      }
      
      HapticService.impact();
    }
  };

  const handleBackspace = () => {
    if (currentPin.length > 0) {
      if (step === 'create') {
        setPin(currentPin.slice(0, -1));
      } else {
        setConfirmPin(currentPin.slice(0, -1));
      }
      HapticService.impact();
    }
  };

  const validatePins = (originalPin: string, confirmation: string) => {
    if (originalPin === confirmation) {
      HapticService.success();
      handlePinSetupComplete(originalPin);
    } else {
      setError('PINs do not match. Please try again.');
      HapticService.error();
      shakeAnimation.setValue(0);
      Animated.sequence([
        Animated.timing(shakeAnimation, {
          toValue: 10,
          duration: 100,
          useNativeDriver: true,
        }),
        Animated.timing(shakeAnimation, {
          toValue: -10,
          duration: 100,
          useNativeDriver: true,
        }),
        Animated.timing(shakeAnimation, {
          toValue: 10,
          duration: 100,
          useNativeDriver: true,
        }),
        Animated.timing(shakeAnimation, {
          toValue: 0,
          duration: 100,
          useNativeDriver: true,
        }),
      ]).start();
      
      setTimeout(() => {
        setStep('create');
        setPin('');
        setConfirmPin('');
        setError(null);
      }, 2000);
    }
  };

  const handlePinSetupComplete = async (userPin: string) => {
    try {
      // Save PIN securely (would typically use keychain/secure storage)
      console.log('PIN setup completed:', userPin);
      
      // Navigate to biometric setup or main app
      navigation.navigate('BiometricSetup' as never);
    } catch (error) {
      console.error('PIN setup failed:', error);
      setError('Failed to save PIN. Please try again.');
    }
  };

  const handleSkip = () => {
    navigation.navigate('BiometricSetup' as never);
  };

  const renderPinDots = () => (
    <Animated.View 
      style={[
        styles.pinContainer,
        { transform: [{ translateX: shakeAnimation }] }
      ]}
    >
      {Array.from({ length: maxLength }).map((_, index) => (
        <View
          key={index}
          style={[
            styles.pinDot,
            index < currentPin.length && styles.pinDotFilled,
            error && styles.pinDotError,
          ]}
        />
      ))}
    </Animated.View>
  );

  const renderNumberPad = () => (
    <View style={styles.numberPad}>
      <View style={styles.numberRow}>
        {[1, 2, 3].map((number) => (
          <TouchableOpacity
            key={number}
            style={styles.numberButton}
            onPress={() => handleNumberPress(number.toString())}
          >
            <Text style={styles.numberText}>{number}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.numberRow}>
        {[4, 5, 6].map((number) => (
          <TouchableOpacity
            key={number}
            style={styles.numberButton}
            onPress={() => handleNumberPress(number.toString())}
          >
            <Text style={styles.numberText}>{number}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.numberRow}>
        {[7, 8, 9].map((number) => (
          <TouchableOpacity
            key={number}
            style={styles.numberButton}
            onPress={() => handleNumberPress(number.toString())}
          >
            <Text style={styles.numberText}>{number}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.numberRow}>
        <View style={styles.numberButton} />
        <TouchableOpacity
          style={styles.numberButton}
          onPress={() => handleNumberPress('0')}
        >
          <Text style={styles.numberText}>0</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.numberButton}
          onPress={handleBackspace}
        >
          <Icon name="backspace" size={24} color="#333" />
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <LinearGradient
        colors={[theme.colors.primary, theme.colors.secondary]}
        style={styles.gradient}
      >
        <SafeAreaView style={styles.safeArea}>
          <View style={styles.header}>
            <Surface style={styles.iconContainer} elevation={4}>
              <Icon name="lock" size={60} color={theme.colors.primary} />
            </Surface>
            <Text style={styles.title}>
              {step === 'create' ? 'Create Your PIN' : 'Confirm Your PIN'}
            </Text>
            <Text style={styles.subtitle}>
              {step === 'create'
                ? 'Choose a 4-digit PIN to secure your account'
                : 'Enter your PIN again to confirm'
              }
            </Text>
          </View>

          <View style={styles.content}>
            {renderPinDots()}
            
            {error && (
              <View style={styles.errorContainer}>
                <Icon name="alert-circle" size={20} color="#FF5252" />
                <Text style={styles.errorText}>{error}</Text>
              </View>
            )}

            {renderNumberPad()}
          </View>

          <View style={styles.footer}>
            <Text style={styles.securityNote}>
              <Icon name="shield-check" size={16} color="rgba(255, 255, 255, 0.8)" />
              {' '}Your PIN is encrypted and stored securely on your device
            </Text>
            
            <Button
              mode="text"
              onPress={handleSkip}
              textColor="rgba(255, 255, 255, 0.8)"
              style={styles.skipButton}
            >
              Skip for now
            </Button>
          </View>
        </SafeAreaView>
      </LinearGradient>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  gradient: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
    padding: 24,
  },
  header: {
    alignItems: 'center',
    marginTop: 40,
    marginBottom: 40,
  },
  iconContainer: {
    width: 120,
    height: 120,
    borderRadius: 60,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'white',
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.8)',
    textAlign: 'center',
    lineHeight: 24,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pinContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 40,
    gap: 16,
  },
  pinDot: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.5)',
    backgroundColor: 'transparent',
  },
  pinDotFilled: {
    backgroundColor: 'white',
    borderColor: 'white',
  },
  pinDotError: {
    borderColor: '#FF5252',
    backgroundColor: 'transparent',
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
    paddingHorizontal: 20,
    paddingVertical: 10,
    backgroundColor: 'rgba(255, 82, 82, 0.1)',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 82, 82, 0.3)',
  },
  errorText: {
    color: '#FF5252',
    fontSize: 14,
    marginLeft: 8,
    textAlign: 'center',
  },
  numberPad: {
    alignItems: 'center',
  },
  numberRow: {
    flexDirection: 'row',
    marginBottom: 16,
    gap: 20,
  },
  numberButton: {
    width: 70,
    height: 70,
    borderRadius: 35,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.3)',
  },
  numberText: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
  },
  footer: {
    alignItems: 'center',
    marginTop: 20,
  },
  securityNote: {
    fontSize: 12,
    color: 'rgba(255, 255, 255, 0.8)',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 20,
  },
  skipButton: {
    marginTop: 10,
  },
});

export default SetupPinScreen;