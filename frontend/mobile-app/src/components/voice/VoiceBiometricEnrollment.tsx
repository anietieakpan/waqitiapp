import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Platform,
  Animated
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import LinearGradient from 'react-native-linear-gradient';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as LocalAuthentication from 'expo-local-authentication';

interface VoiceBiometricEnrollmentProps {
  onComplete: (enrolled: boolean) => void;
  onSkip: () => void;
}

const VoiceBiometricEnrollment: React.FC<VoiceBiometricEnrollmentProps> = ({
  onComplete,
  onSkip
}) => {
  const [biometricType, setBiometricType] = useState<string | null>(null);
  const [isAvailable, setIsAvailable] = useState(false);
  const [isEnrolling, setIsEnrolling] = useState(false);
  const [currentStep, setCurrentStep] = useState<'intro' | 'setup' | 'test' | 'complete'>('intro');
  const [fadeAnim] = useState(new Animated.Value(0));
  const [scaleAnim] = useState(new Animated.Value(0.8));

  useEffect(() => {
    checkBiometricAvailability();
    animateIn();
  }, []);

  useEffect(() => {
    animateIn();
  }, [currentStep]);

  const animateIn = () => {
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        tension: 100,
        friction: 8,
        useNativeDriver: true,
      })
    ]).start();
  };

  const checkBiometricAvailability = async () => {
    try {
      const compatible = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      const types = await LocalAuthentication.supportedAuthenticationTypesAsync();

      setIsAvailable(compatible && enrolled);

      if (types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) {
        setBiometricType(Platform.OS === 'ios' ? 'Face ID' : 'Face Unlock');
      } else if (types.includes(LocalAuthentication.AuthenticationType.FINGERPRINT)) {
        setBiometricType(Platform.OS === 'ios' ? 'Touch ID' : 'Fingerprint');
      } else {
        setBiometricType('Biometric Authentication');
      }
    } catch (error) {
      console.error('Failed to check biometric availability:', error);
      setIsAvailable(false);
    }
  };

  const handleEnrollBiometric = async () => {
    if (!isAvailable) {
      Alert.alert(
        'Biometric Not Available',
        'Biometric authentication is not available on this device. Please set up fingerprint or face unlock in your device settings first.',
        [{ text: 'OK' }]
      );
      return;
    }

    setIsEnrolling(true);
    setCurrentStep('setup');

    try {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'Set up biometric authentication for voice payments',
        fallbackLabel: 'Use Passcode',
        disableDeviceFallback: false,
      });

      if (result.success) {
        setCurrentStep('test');
        // Simulate a brief delay for user to see the success state
        setTimeout(() => {
          setCurrentStep('complete');
          setTimeout(() => {
            onComplete(true);
          }, 1500);
        }, 1000);
      } else {
        setIsEnrolling(false);
        setCurrentStep('intro');
        
        if (result.error === 'user_cancel') {
          // User cancelled, don't show error
          return;
        }
        
        Alert.alert(
          'Setup Failed',
          'Failed to set up biometric authentication. Please try again.',
          [{ text: 'OK' }]
        );
      }
    } catch (error) {
      console.error('Biometric enrollment failed:', error);
      setIsEnrolling(false);
      setCurrentStep('intro');
      Alert.alert(
        'Setup Error',
        'An error occurred while setting up biometric authentication. Please try again.',
        [{ text: 'OK' }]
      );
    }
  };

  const getBiometricIcon = (): string => {
    if (biometricType?.includes('Face')) {
      return 'face';
    } else if (biometricType?.includes('Touch') || biometricType?.includes('Fingerprint')) {
      return 'fingerprint';
    }
    return 'security';
  };

  const renderIntroStep = () => (
    <Animated.View 
      style={[
        styles.stepContainer,
        {
          opacity: fadeAnim,
          transform: [{ scale: scaleAnim }],
        }
      ]}
    >
      <View style={styles.iconContainer}>
        <Icon name={getBiometricIcon()} size={64} color="#667eea" />
      </View>
      
      <Text style={styles.title}>Secure Voice Payments</Text>
      <Text style={styles.subtitle}>
        Set up {biometricType} to secure your voice-activated payments
      </Text>

      <View style={styles.benefitsList}>
        <View style={styles.benefitItem}>
          <Icon name="security" size={20} color="#4CAF50" />
          <Text style={styles.benefitText}>Enhanced security for voice payments</Text>
        </View>
        <View style={styles.benefitItem}>
          <Icon name="speed" size={20} color="#4CAF50" />
          <Text style={styles.benefitText}>Quick and convenient authentication</Text>
        </View>
        <View style={styles.benefitItem}>
          <Icon name="verified" size={20} color="#4CAF50" />
          <Text style={styles.benefitText}>Verify your identity with {biometricType}</Text>
        </View>
      </View>

      <View style={styles.actionButtons}>
        <TouchableOpacity
          style={[styles.actionButton, styles.skipButton]}
          onPress={onSkip}
        >
          <Text style={styles.skipButtonText}>Skip for Now</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.actionButton, styles.enrollButton]}
          onPress={handleEnrollBiometric}
          disabled={!isAvailable}
        >
          <LinearGradient
            colors={['#667eea', '#764ba2']}
            style={styles.enrollButtonGradient}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
          >
            <Icon name="fingerprint" size={20} color="#FFFFFF" />
            <Text style={styles.enrollButtonText}>Set Up {biometricType}</Text>
          </LinearGradient>
        </TouchableOpacity>
      </View>

      {!isAvailable && (
        <View style={styles.warningContainer}>
          <Icon name="warning" size={16} color="#FF9800" />
          <Text style={styles.warningText}>
            Please set up {biometricType} in your device settings first
          </Text>
        </View>
      )}
    </Animated.View>
  );

  const renderSetupStep = () => (
    <Animated.View 
      style={[
        styles.stepContainer,
        {
          opacity: fadeAnim,
          transform: [{ scale: scaleAnim }],
        }
      ]}
    >
      <View style={styles.processingContainer}>
        <Animated.View 
          style={[
            styles.processingIcon,
            {
              transform: [{
                rotate: fadeAnim.interpolate({
                  inputRange: [0, 1],
                  outputRange: ['0deg', '360deg'],
                })
              }]
            }
          ]}
        >
          <Icon name={getBiometricIcon()} size={64} color="#667eea" />
        </Animated.View>
        
        <Text style={styles.processingTitle}>Setting Up {biometricType}</Text>
        <Text style={styles.processingSubtitle}>
          Please complete the {biometricType} authentication to continue
        </Text>
      </View>
    </Animated.View>
  );

  const renderTestStep = () => (
    <Animated.View 
      style={[
        styles.stepContainer,
        {
          opacity: fadeAnim,
          transform: [{ scale: scaleAnim }],
        }
      ]}
    >
      <View style={styles.successContainer}>
        <View style={styles.successIcon}>
          <Icon name="check-circle" size={64} color="#4CAF50" />
        </View>
        
        <Text style={styles.successTitle}>Authentication Successful!</Text>
        <Text style={styles.successSubtitle}>
          Testing your {biometricType} setup...
        </Text>
      </View>
    </Animated.View>
  );

  const renderCompleteStep = () => (
    <Animated.View 
      style={[
        styles.stepContainer,
        {
          opacity: fadeAnim,
          transform: [{ scale: scaleAnim }],
        }
      ]}
    >
      <View style={styles.completeContainer}>
        <View style={styles.completeIcon}>
          <Icon name="verified" size={64} color="#4CAF50" />
        </View>
        
        <Text style={styles.completeTitle}>Setup Complete!</Text>
        <Text style={styles.completeSubtitle}>
          {biometricType} is now enabled for voice payments. You can now use voice commands securely.
        </Text>

        <View style={styles.featuresList}>
          <View style={styles.featureItem}>
            <Icon name="mic" size={16} color="#667eea" />
            <Text style={styles.featureText}>Voice payments enabled</Text>
          </View>
          <View style={styles.featureItem}>
            <Icon name="security" size={16} color="#667eea" />
            <Text style={styles.featureText}>{biometricType} protection active</Text>
          </View>
        </View>
      </View>
    </Animated.View>
  );

  const renderStepContent = () => {
    switch (currentStep) {
      case 'setup':
        return renderSetupStep();
      case 'test':
        return renderTestStep();
      case 'complete':
        return renderCompleteStep();
      default:
        return renderIntroStep();
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.progressContainer}>
          <View style={styles.progressBar}>
            <View 
              style={[
                styles.progressFill,
                {
                  width: currentStep === 'intro' ? '25%' :
                         currentStep === 'setup' ? '50%' :
                         currentStep === 'test' ? '75%' : '100%'
                }
              ]}
            />
          </View>
          <Text style={styles.progressText}>
            Step {
              currentStep === 'intro' ? '1' :
              currentStep === 'setup' ? '2' :
              currentStep === 'test' ? '3' : '4'
            } of 4
          </Text>
        </View>

        {renderStepContent()}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  content: {
    flex: 1,
    padding: 24,
  },
  progressContainer: {
    marginBottom: 32,
  },
  progressBar: {
    height: 4,
    backgroundColor: '#E0E0E0',
    borderRadius: 2,
    marginBottom: 8,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#667eea',
    borderRadius: 2,
  },
  progressText: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },
  stepContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  iconContainer: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#F0F4FF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 32,
  },
  benefitsList: {
    width: '100%',
    marginBottom: 32,
  },
  benefitItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
  },
  benefitText: {
    fontSize: 16,
    color: '#333',
    marginLeft: 12,
    flex: 1,
  },
  actionButtons: {
    flexDirection: 'row',
    width: '100%',
    paddingHorizontal: 16,
  },
  actionButton: {
    flex: 1,
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 8,
  },
  skipButton: {
    backgroundColor: '#F5F5F5',
    borderWidth: 1,
    borderColor: '#E0E0E0',
  },
  skipButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#666',
  },
  enrollButton: {
    overflow: 'hidden',
  },
  enrollButtonGradient: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  enrollButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
    marginLeft: 8,
  },
  warningContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF8E1',
    padding: 12,
    borderRadius: 8,
    marginTop: 16,
    width: '100%',
  },
  warningText: {
    fontSize: 13,
    color: '#E65100',
    marginLeft: 8,
    flex: 1,
    textAlign: 'center',
  },
  processingContainer: {
    alignItems: 'center',
  },
  processingIcon: {
    marginBottom: 24,
  },
  processingTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  processingSubtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
  },
  successContainer: {
    alignItems: 'center',
  },
  successIcon: {
    marginBottom: 24,
  },
  successTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#4CAF50',
    marginBottom: 8,
  },
  successSubtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
  },
  completeContainer: {
    alignItems: 'center',
  },
  completeIcon: {
    marginBottom: 24,
  },
  completeTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#4CAF50',
    marginBottom: 8,
  },
  completeSubtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 24,
  },
  featuresList: {
    width: '100%',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
  },
  featureText: {
    fontSize: 14,
    color: '#333',
    marginLeft: 8,
    fontWeight: '500',
  },
});

export default VoiceBiometricEnrollment;