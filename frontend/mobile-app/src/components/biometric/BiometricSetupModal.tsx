/**
 * Biometric Setup Modal - Comprehensive biometric enrollment flow
 * Guides users through biometric authentication setup with fallback options
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Alert,
  ScrollView,
  TouchableOpacity,
  Animated,
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
  Chip,
  List,
  Divider,
  Checkbox,
  RadioButton,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation } from '@react-navigation/native';

import useBiometric from '../../hooks/useBiometric';
import { useAppSelector } from '../../hooks/redux';
import { AuthenticationMethod, SecurityLevel } from '../../services/biometric/types';
import { formatDateTime } from '../../utils/formatters';

interface BiometricSetupModalProps {
  visible: boolean;
  onClose: () => void;
  onSetupComplete: () => void;
  userId?: string;
  skipOption?: boolean;
}

const BiometricSetupModal: React.FC<BiometricSetupModalProps> = ({
  visible,
  onClose,
  onSetupComplete,
  userId: propUserId,
  skipOption = true,
}) => {
  const navigation = useNavigation();
  const { user } = useAppSelector((state) => state.auth);
  const biometric = useBiometric();
  
  const userId = propUserId || user?.id || '';
  
  // State
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [fallbackMethod, setFallbackMethod] = useState<AuthenticationMethod>(AuthenticationMethod.PIN);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [setupProgress, setSetupProgress] = useState(0);
  const fadeAnim = useState(new Animated.Value(0))[0];

  // Animation effect
  useEffect(() => {
    if (visible) {
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
    }
  }, [visible, fadeAnim]);

  // Get biometric type name
  const getBiometricTypeName = (): string => {
    if (!biometric.capabilities) return 'Biometric';
    
    switch (biometric.capabilities.biometryType) {
      case 'TouchID':
        return 'Touch ID';
      case 'FaceID':
        return 'Face ID';
      case 'Biometrics':
      default:
        return 'Fingerprint';
    }
  };

  // Get biometric icon
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

  // Setup steps
  const setupSteps = [
    {
      title: 'Welcome to Biometric Security',
      description: `Enhance your account security with ${getBiometricTypeName()}`,
      icon: getBiometricIcon(),
    },
    {
      title: 'Security Check',
      description: 'Verifying device security and biometric availability',
      icon: 'security',
    },
    {
      title: 'Choose Fallback Method',
      description: 'Select an alternative authentication method for when biometrics are unavailable',
      icon: 'shield-key',
    },
    {
      title: 'Terms & Conditions',
      description: 'Review and accept the biometric authentication terms',
      icon: 'file-document-outline',
    },
    {
      title: 'Setup Biometric',
      description: `Set up ${getBiometricTypeName()} for your account`,
      icon: getBiometricIcon(),
    },
  ];

  // Handle setup initiation
  const handleStartSetup = async () => {
    setCurrentStep(1);
    setLoading(true);
    setSetupProgress(0.2);

    try {
      // Check device security
      await biometric.checkDeviceSecurity();
      
      if (!biometric.isSecure) {
        Alert.alert(
          'Security Warning',
          'Your device may not meet all security requirements. Continue with setup?',
          [
            { text: 'Cancel', style: 'cancel', onPress: () => setCurrentStep(0) },
            { text: 'Continue', onPress: () => setCurrentStep(2) },
          ]
        );
      } else {
        setCurrentStep(2);
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to check device security. Please try again.');
      setCurrentStep(0);
    } finally {
      setLoading(false);
      setSetupProgress(0.4);
    }
  };

  // Handle biometric enrollment
  const handleEnrollBiometric = async () => {
    if (!termsAccepted) {
      Alert.alert('Terms Required', 'Please accept the terms and conditions to continue.');
      return;
    }

    setLoading(true);
    setSetupProgress(0.6);

    try {
      const customTitle = `Enable ${getBiometricTypeName()}`;
      const result = await biometric.setupWithPrompt(userId, customTitle);

      if (result) {
        setSetupProgress(1.0);
        
        // Save fallback method
        await biometric.updateSettings(userId, {
          fallbackMethod,
          allowDeviceCredentials: fallbackMethod === AuthenticationMethod.DEVICE_CREDENTIALS,
          requireConfirmation: true,
        });

        Alert.alert(
          'Success',
          `${getBiometricTypeName()} has been successfully set up for your account!`,
          [
            {
              text: 'OK',
              onPress: () => {
                onSetupComplete();
                handleClose();
              },
            },
          ]
        );
      } else {
        Alert.alert(
          'Setup Failed',
          biometric.getErrorMessage() || 'Failed to set up biometric authentication',
          [
            { text: 'Retry', onPress: handleEnrollBiometric },
            { text: 'Cancel', style: 'cancel' },
          ]
        );
      }
    } catch (error: any) {
      Alert.alert('Error', error.message || 'An error occurred during setup');
    } finally {
      setLoading(false);
    }
  };

  // Handle skip
  const handleSkip = () => {
    Alert.alert(
      'Skip Biometric Setup?',
      'You can enable biometric authentication later in Settings.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Skip',
          onPress: () => {
            onClose();
          },
        },
      ]
    );
  };

  // Handle close
  const handleClose = () => {
    setCurrentStep(0);
    setTermsAccepted(false);
    setSetupProgress(0);
    onClose();
  };

  // Render step content
  const renderStepContent = () => {
    const step = setupSteps[currentStep];

    switch (currentStep) {
      case 0: // Welcome
        return (
          <View style={styles.stepContent}>
            <View style={styles.iconContainer}>
              <Icon name={step.icon} size={80} color="#2196F3" />
            </View>
            <Title style={styles.title}>{step.title}</Title>
            <Paragraph style={styles.description}>{step.description}</Paragraph>
            
            <View style={styles.benefitsList}>
              <List.Item
                title="Quick Access"
                description="Login instantly without typing passwords"
                left={() => <List.Icon icon="flash" color="#4CAF50" />}
              />
              <List.Item
                title="Enhanced Security"
                description="Your biometric data never leaves your device"
                left={() => <List.Icon icon="shield-check" color="#4CAF50" />}
              />
              <List.Item
                title="Convenient"
                description="Authorize transactions with a simple touch or glance"
                left={() => <List.Icon icon="hand-okay" color="#4CAF50" />}
              />
            </View>

            <Button
              mode="contained"
              onPress={handleStartSetup}
              style={styles.primaryButton}
            >
              Get Started
            </Button>
            
            {skipOption && (
              <Button
                mode="text"
                onPress={handleSkip}
                style={styles.skipButton}
              >
                Skip for Now
              </Button>
            )}
          </View>
        );

      case 1: // Security Check
        return (
          <View style={styles.stepContent}>
            <View style={styles.iconContainer}>
              <ActivityIndicator size={60} color="#2196F3" />
            </View>
            <Title style={styles.title}>{step.title}</Title>
            <Paragraph style={styles.description}>{step.description}</Paragraph>
            
            {biometric.securityAssessment && (
              <Card style={styles.securityCard}>
                <Card.Content>
                  <List.Section>
                    <List.Item
                      title="Device Security"
                      description={biometric.securityAssessment.deviceIntegrity ? 'Secure' : 'Warning'}
                      left={() => (
                        <Icon 
                          name={biometric.securityAssessment!.deviceIntegrity ? 'check-circle' : 'alert-circle'} 
                          size={24} 
                          color={biometric.securityAssessment!.deviceIntegrity ? '#4CAF50' : '#FF9800'} 
                        />
                      )}
                    />
                    <List.Item
                      title="Biometric Hardware"
                      description={biometric.securityAssessment.biometricIntegrity ? 'Available' : 'Not Available'}
                      left={() => (
                        <Icon 
                          name={biometric.securityAssessment!.biometricIntegrity ? 'check-circle' : 'alert-circle'} 
                          size={24} 
                          color={biometric.securityAssessment!.biometricIntegrity ? '#4CAF50' : '#F44336'} 
                        />
                      )}
                    />
                    <List.Item
                      title="Risk Level"
                      description={SecurityLevel[biometric.securityAssessment.riskLevel]}
                      left={() => (
                        <Icon 
                          name="shield" 
                          size={24} 
                          color={biometric.securityAssessment!.riskLevel === SecurityLevel.LOW ? '#4CAF50' : '#FF9800'} 
                        />
                      )}
                    />
                  </List.Section>
                </Card.Content>
              </Card>
            )}
          </View>
        );

      case 2: // Fallback Method
        return (
          <View style={styles.stepContent}>
            <View style={styles.iconContainer}>
              <Icon name={step.icon} size={80} color="#2196F3" />
            </View>
            <Title style={styles.title}>{step.title}</Title>
            <Paragraph style={styles.description}>{step.description}</Paragraph>
            
            <RadioButton.Group
              onValueChange={(value) => setFallbackMethod(value as AuthenticationMethod)}
              value={fallbackMethod}
            >
              <Card style={styles.optionCard}>
                <TouchableOpacity
                  onPress={() => setFallbackMethod(AuthenticationMethod.PIN)}
                >
                  <Card.Content style={styles.radioOption}>
                    <RadioButton value={AuthenticationMethod.PIN} />
                    <View style={styles.radioContent}>
                      <Text style={styles.optionTitle}>PIN Code</Text>
                      <Text style={styles.optionDescription}>Use a 6-digit PIN as backup</Text>
                    </View>
                  </Card.Content>
                </TouchableOpacity>
              </Card>
              
              <Card style={styles.optionCard}>
                <TouchableOpacity
                  onPress={() => setFallbackMethod(AuthenticationMethod.PASSWORD)}
                >
                  <Card.Content style={styles.radioOption}>
                    <RadioButton value={AuthenticationMethod.PASSWORD} />
                    <View style={styles.radioContent}>
                      <Text style={styles.optionTitle}>Password</Text>
                      <Text style={styles.optionDescription}>Use your account password</Text>
                    </View>
                  </Card.Content>
                </TouchableOpacity>
              </Card>
              
              {Platform.OS === 'android' && (
                <Card style={styles.optionCard}>
                  <TouchableOpacity
                    onPress={() => setFallbackMethod(AuthenticationMethod.DEVICE_CREDENTIALS)}
                  >
                    <Card.Content style={styles.radioOption}>
                      <RadioButton value={AuthenticationMethod.DEVICE_CREDENTIALS} />
                      <View style={styles.radioContent}>
                        <Text style={styles.optionTitle}>Device Lock</Text>
                        <Text style={styles.optionDescription}>Use your device PIN/Pattern</Text>
                      </View>
                    </Card.Content>
                  </TouchableOpacity>
                </Card>
              )}
            </RadioButton.Group>

            <Button
              mode="contained"
              onPress={() => setCurrentStep(3)}
              style={styles.primaryButton}
            >
              Continue
            </Button>
          </View>
        );

      case 3: // Terms
        return (
          <View style={styles.stepContent}>
            <View style={styles.iconContainer}>
              <Icon name={step.icon} size={80} color="#2196F3" />
            </View>
            <Title style={styles.title}>{step.title}</Title>
            
            <ScrollView style={styles.termsContainer}>
              <Text style={styles.termsText}>
                By enabling biometric authentication, you agree to the following:
                {'\n\n'}
                1. Your biometric data (fingerprint/face) is stored securely on your device only
                {'\n\n'}
                2. Waqiti never has access to your actual biometric data
                {'\n\n'}
                3. You can disable biometric authentication at any time
                {'\n\n'}
                4. You will use the selected fallback method ({fallbackMethod}) when biometrics are unavailable
                {'\n\n'}
                5. You are responsible for keeping your device secure
              </Text>
            </ScrollView>

            <View style={styles.checkboxContainer}>
              <Checkbox
                status={termsAccepted ? 'checked' : 'unchecked'}
                onPress={() => setTermsAccepted(!termsAccepted)}
              />
              <Text style={styles.checkboxLabel}>
                I accept the terms and conditions
              </Text>
            </View>

            <Button
              mode="contained"
              onPress={() => setCurrentStep(4)}
              disabled={!termsAccepted}
              style={styles.primaryButton}
            >
              Continue
            </Button>
          </View>
        );

      case 4: // Setup
        return (
          <View style={styles.stepContent}>
            <View style={styles.iconContainer}>
              <Icon name={step.icon} size={80} color="#2196F3" />
            </View>
            <Title style={styles.title}>{step.title}</Title>
            <Paragraph style={styles.description}>{step.description}</Paragraph>
            
            {setupProgress > 0 && setupProgress < 1 && (
              <View style={styles.progressContainer}>
                <View style={[styles.progressBar, { width: `${setupProgress * 100}%` }]} />
              </View>
            )}

            <Card style={styles.infoCard}>
              <Card.Content>
                <Text style={styles.infoText}>
                  When prompted, please authenticate using your {getBiometricTypeName().toLowerCase()} to complete the setup.
                </Text>
              </Card.Content>
            </Card>

            <Button
              mode="contained"
              onPress={handleEnrollBiometric}
              disabled={loading}
              style={styles.primaryButton}
            >
              {loading ? <ActivityIndicator size="small" color="white" /> : `Enable ${getBiometricTypeName()}`}
            </Button>
            
            <Button
              mode="text"
              onPress={() => setCurrentStep(2)}
              disabled={loading}
              style={styles.backButton}
            >
              Back
            </Button>
          </View>
        );

      default:
        return null;
    }
  };

  return (
    <Portal>
      <Modal
        visible={visible}
        onDismiss={skipOption ? handleClose : undefined}
        contentContainerStyle={styles.modal}
      >
        <Animated.View style={[styles.container, { opacity: fadeAnim }]}>
          <Card style={styles.card}>
            <Card.Content>
              {/* Progress dots */}
              <View style={styles.progressDots}>
                {setupSteps.map((_, index) => (
                  <View
                    key={index}
                    style={[
                      styles.dot,
                      index === currentStep && styles.activeDot,
                      index < currentStep && styles.completedDot,
                    ]}
                  />
                ))}
              </View>

              {renderStepContent()}
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
    flex: 1,
    justifyContent: 'center',
  },
  card: {
    maxHeight: '90%',
  },
  progressDots: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 20,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#E0E0E0',
    marginHorizontal: 4,
  },
  activeDot: {
    backgroundColor: '#2196F3',
    width: 24,
  },
  completedDot: {
    backgroundColor: '#4CAF50',
  },
  stepContent: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  iconContainer: {
    marginBottom: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 10,
  },
  description: {
    textAlign: 'center',
    color: '#666',
    marginBottom: 20,
  },
  benefitsList: {
    width: '100%',
    marginVertical: 20,
  },
  securityCard: {
    width: '100%',
    marginVertical: 20,
  },
  optionCard: {
    width: '100%',
    marginVertical: 8,
  },
  radioOption: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  radioContent: {
    flex: 1,
    marginLeft: 10,
  },
  optionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  optionDescription: {
    fontSize: 12,
    color: '#666',
  },
  termsContainer: {
    maxHeight: 200,
    width: '100%',
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    padding: 16,
    marginVertical: 20,
  },
  termsText: {
    fontSize: 14,
    lineHeight: 20,
  },
  checkboxContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 16,
  },
  checkboxLabel: {
    flex: 1,
    marginLeft: 8,
  },
  infoCard: {
    width: '100%',
    marginVertical: 20,
    backgroundColor: '#E3F2FD',
  },
  infoText: {
    fontSize: 14,
    color: '#1976D2',
  },
  progressContainer: {
    width: '100%',
    height: 4,
    backgroundColor: '#E0E0E0',
    borderRadius: 2,
    marginVertical: 20,
  },
  progressBar: {
    height: '100%',
    backgroundColor: '#4CAF50',
    borderRadius: 2,
  },
  primaryButton: {
    marginTop: 20,
    paddingHorizontal: 32,
  },
  skipButton: {
    marginTop: 10,
  },
  backButton: {
    marginTop: 10,
  },
});

export default BiometricSetupModal;