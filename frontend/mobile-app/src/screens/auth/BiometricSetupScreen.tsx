import React, { useState, useEffect } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  Alert,
  Platform,
  Dimensions,
  StatusBar,
} from 'react-native';
import {
  Text,
  Button,
  Card,
  Avatar,
  ActivityIndicator,
  Surface,
  useTheme,
  Divider,
  IconButton,
} from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useBiometric } from '../../hooks/useBiometric';
import { useAuth } from '../../contexts/AuthContext';
import { useNavigation } from '@react-navigation/native';
import type { AuthStackNavigationProp } from '../../navigation/types';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

const { width, height } = Dimensions.get('window');

interface BiometricSetupScreenProps {}

const BiometricSetupScreen: React.FC<BiometricSetupScreenProps> = () => {
  const theme = useTheme();
  const navigation = useNavigation<AuthStackNavigationProp>();
  const { user } = useAuth();
  const {
    isAvailable,
    biometricType,
    setupBiometric,
    isLoading,
    error,
    clearError,
    canAuthenticate,
    securityAssessment,
  } = useBiometric();

  const [setupStep, setSetupStep] = useState<'intro' | 'setup' | 'success' | 'error'>('intro');
  const [isSettingUp, setIsSettingUp] = useState(false);

  useEffect(() => {
    // Clear any previous errors when component mounts
    clearError();
  }, [clearError]);

  useEffect(() => {
    // Auto-navigate if already set up
    if (canAuthenticate) {
      handleSuccess();
    }
  }, [canAuthenticate]);

  const getBiometricIcon = () => {
    switch (biometricType?.toLowerCase()) {
      case 'touchid':
        return 'fingerprint';
      case 'faceid':
        return 'face-recognition';
      case 'biometrics':
      case 'fingerprint':
        return 'fingerprint';
      default:
        return 'shield-check';
    }
  };

  const getBiometricTitle = () => {
    switch (biometricType?.toLowerCase()) {
      case 'touchid':
        return 'Touch ID';
      case 'faceid':
        return 'Face ID';
      case 'biometrics':
      case 'fingerprint':
        return 'Fingerprint';
      default:
        return 'Biometric Authentication';
    }
  };

  const getBiometricDescription = () => {
    switch (biometricType?.toLowerCase()) {
      case 'touchid':
        return 'Use your fingerprint to quickly and securely access your Waqiti account.';
      case 'faceid':
        return 'Use your face to quickly and securely access your Waqiti account.';
      case 'biometrics':
      case 'fingerprint':
        return 'Use your fingerprint to quickly and securely access your Waqiti account.';
      default:
        return 'Use biometric authentication to quickly and securely access your account.';
    }
  };

  const handleSetup = async () => {
    if (!user?.id) {
      Alert.alert('Error', 'User information not available. Please try again.');
      return;
    }

    setIsSettingUp(true);
    setSetupStep('setup');

    try {
      const result = await setupBiometric(user.id);
      
      if (result.success) {
        setSetupStep('success');
        setTimeout(() => {
          handleSuccess();
        }, 2000);
      } else {
        setSetupStep('error');
        Alert.alert(
          'Setup Failed', 
          result.error || 'Failed to set up biometric authentication. Please try again.',
          [
            { text: 'Retry', onPress: () => setSetupStep('intro') },
            { text: 'Skip', onPress: handleSkip, style: 'cancel' },
          ]
        );
      }
    } catch (err) {
      setSetupStep('error');
      Alert.alert(
        'Setup Error',
        'An unexpected error occurred. Please try again.',
        [
          { text: 'Retry', onPress: () => setSetupStep('intro') },
          { text: 'Skip', onPress: handleSkip, style: 'cancel' },
        ]
      );
    } finally {
      setIsSettingUp(false);
    }
  };

  const handleSkip = () => {
    Alert.alert(
      'Skip Biometric Setup?',
      'You can enable biometric authentication later in your security settings.',
      [
        {
          text: 'Go Back',
          style: 'cancel',
        },
        {
          text: 'Skip',
          style: 'destructive',
          onPress: () => {
            navigation.navigate('Dashboard');
          },
        },
      ]
    );
  };

  const handleSuccess = () => {
    navigation.navigate('Dashboard');
  };

  const handleGoToSettings = () => {
    navigation.navigate('SecuritySettings');
  };

  const renderIntroStep = () => (
    <ScrollView 
      contentContainerStyle={styles.scrollContainer}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.headerContainer}>
        <Avatar.Icon
          size={80}
          icon={getBiometricIcon()}
          style={[styles.icon, { backgroundColor: theme.colors.primary }]}
        />
        <Text variant="headlineMedium" style={styles.title}>
          Set up {getBiometricTitle()}
        </Text>
        <Text variant="bodyLarge" style={[styles.description, { color: theme.colors.onSurfaceVariant }]}>
          {getBiometricDescription()}
        </Text>
      </View>

      <Card style={styles.benefitsCard}>
        <Card.Content>
          <Text variant="titleMedium" style={styles.benefitsTitle}>
            Benefits of Biometric Authentication
          </Text>
          
          <View style={styles.benefitItem}>
            <Icon name="lightning-bolt" size={24} color={theme.colors.primary} />
            <Text variant="bodyMedium" style={styles.benefitText}>
              Quick and convenient access
            </Text>
          </View>
          
          <View style={styles.benefitItem}>
            <Icon name="shield-check" size={24} color={theme.colors.primary} />
            <Text variant="bodyMedium" style={styles.benefitText}>
              Enhanced security for your account
            </Text>
          </View>
          
          <View style={styles.benefitItem}>
            <Icon name="account-lock" size={24} color={theme.colors.primary} />
            <Text variant="bodyMedium" style={styles.benefitText}>
              Protect sensitive transactions
            </Text>
          </View>
        </Card.Content>
      </Card>

      {securityAssessment && (
        <Card style={styles.securityCard}>
          <Card.Content>
            <View style={styles.securityHeader}>
              <Icon name="security" size={24} color={theme.colors.primary} />
              <Text variant="titleMedium">Security Assessment</Text>
            </View>
            <Text variant="bodyMedium" style={styles.securityText}>
              Your device security level: {securityAssessment.level}
            </Text>
            <Text variant="bodySmall" style={[styles.securityDescription, { color: theme.colors.onSurfaceVariant }]}>
              Biometric authentication will enhance your account security
            </Text>
          </Card.Content>
        </Card>
      )}

      <View style={styles.buttonContainer}>
        <Button
          mode="contained"
          onPress={handleSetup}
          style={styles.primaryButton}
          disabled={!isAvailable || isLoading || isSettingUp}
          loading={isSettingUp}
        >
          Enable {getBiometricTitle()}
        </Button>
        
        <Button
          mode="text"
          onPress={handleSkip}
          style={styles.secondaryButton}
          disabled={isSettingUp}
        >
          Skip for now
        </Button>
      </View>
    </ScrollView>
  );

  const renderSetupStep = () => (
    <View style={styles.centerContainer}>
      <ActivityIndicator size="large" color={theme.colors.primary} />
      <Text variant="headlineSmall" style={styles.setupTitle}>
        Setting up {getBiometricTitle()}
      </Text>
      <Text variant="bodyLarge" style={[styles.setupDescription, { color: theme.colors.onSurfaceVariant }]}>
        Please follow the prompts to complete setup
      </Text>
    </View>
  );

  const renderSuccessStep = () => (
    <View style={styles.centerContainer}>
      <Avatar.Icon
        size={80}
        icon="check-circle"
        style={[styles.successIcon, { backgroundColor: theme.colors.primary }]}
      />
      <Text variant="headlineSmall" style={styles.successTitle}>
        {getBiometricTitle()} Enabled!
      </Text>
      <Text variant="bodyLarge" style={[styles.successDescription, { color: theme.colors.onSurfaceVariant }]}>
        Your account is now secured with biometric authentication
      </Text>
    </View>
  );

  const renderErrorStep = () => (
    <View style={styles.centerContainer}>
      <Avatar.Icon
        size={80}
        icon="alert-circle"
        style={[styles.errorIcon, { backgroundColor: theme.colors.error }]}
      />
      <Text variant="headlineSmall" style={styles.errorTitle}>
        Setup Failed
      </Text>
      <Text variant="bodyLarge" style={[styles.errorDescription, { color: theme.colors.onSurfaceVariant }]}>
        {error || 'Something went wrong during setup. You can try again or enable it later in settings.'}
      </Text>
      
      <View style={styles.buttonContainer}>
        <Button
          mode="contained"
          onPress={() => setSetupStep('intro')}
          style={styles.primaryButton}
        >
          Try Again
        </Button>
        
        <Button
          mode="outlined"
          onPress={handleGoToSettings}
          style={styles.secondaryButton}
        >
          Go to Settings
        </Button>
        
        <Button
          mode="text"
          onPress={handleSkip}
          style={styles.secondaryButton}
        >
          Skip
        </Button>
      </View>
    </View>
  );

  const renderUnavailable = () => (
    <View style={styles.centerContainer}>
      <Avatar.Icon
        size={80}
        icon="alert-circle-outline"
        style={[styles.warningIcon, { backgroundColor: theme.colors.outline }]}
      />
      <Text variant="headlineSmall" style={styles.unavailableTitle}>
        Biometric Authentication Unavailable
      </Text>
      <Text variant="bodyLarge" style={[styles.unavailableDescription, { color: theme.colors.onSurfaceVariant }]}>
        Your device doesn't support biometric authentication or it's not set up in your device settings.
      </Text>
      
      <View style={styles.buttonContainer}>
        <Button
          mode="outlined"
          onPress={handleGoToSettings}
          style={styles.primaryButton}
        >
          Go to Security Settings
        </Button>
        
        <Button
          mode="text"
          onPress={() => navigation.navigate('Dashboard')}
          style={styles.secondaryButton}
        >
          Continue without Biometrics
        </Button>
      </View>
    </View>
  );

  const renderContent = () => {
    if (!isAvailable) {
      return renderUnavailable();
    }

    switch (setupStep) {
      case 'intro':
        return renderIntroStep();
      case 'setup':
        return renderSetupStep();
      case 'success':
        return renderSuccessStep();
      case 'error':
        return renderErrorStep();
      default:
        return renderIntroStep();
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <StatusBar backgroundColor={theme.colors.surface} barStyle="dark-content" />
      
      <View style={styles.header}>
        <IconButton
          icon="arrow-left"
          size={24}
          onPress={() => navigation.goBack()}
          disabled={isSettingUp}
        />
        <Text variant="titleLarge" style={styles.headerTitle}>
          Security Setup
        </Text>
        <View style={{ width: 48 }} />
      </View>
      
      <Divider />
      
      {renderContent()}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 4,
    paddingVertical: 8,
  },
  headerTitle: {
    fontWeight: '600',
  },
  scrollContainer: {
    flexGrow: 1,
    padding: 20,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  headerContainer: {
    alignItems: 'center',
    marginBottom: 32,
  },
  icon: {
    marginBottom: 20,
  },
  title: {
    textAlign: 'center',
    marginBottom: 12,
    fontWeight: '600',
  },
  description: {
    textAlign: 'center',
    lineHeight: 24,
  },
  benefitsCard: {
    marginBottom: 20,
  },
  benefitsTitle: {
    marginBottom: 16,
    fontWeight: '600',
  },
  benefitItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  benefitText: {
    marginLeft: 12,
    flex: 1,
  },
  securityCard: {
    marginBottom: 32,
  },
  securityHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  securityText: {
    marginLeft: 32,
    marginBottom: 4,
    fontWeight: '500',
  },
  securityDescription: {
    marginLeft: 32,
  },
  buttonContainer: {
    gap: 12,
  },
  primaryButton: {
    marginVertical: 4,
  },
  secondaryButton: {
    marginVertical: 4,
  },
  setupTitle: {
    textAlign: 'center',
    marginTop: 20,
    marginBottom: 12,
    fontWeight: '600',
  },
  setupDescription: {
    textAlign: 'center',
    lineHeight: 24,
  },
  successIcon: {
    marginBottom: 20,
  },
  successTitle: {
    textAlign: 'center',
    marginBottom: 12,
    fontWeight: '600',
  },
  successDescription: {
    textAlign: 'center',
    lineHeight: 24,
  },
  errorIcon: {
    marginBottom: 20,
  },
  errorTitle: {
    textAlign: 'center',
    marginBottom: 12,
    fontWeight: '600',
  },
  errorDescription: {
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 32,
  },
  warningIcon: {
    marginBottom: 20,
  },
  unavailableTitle: {
    textAlign: 'center',
    marginBottom: 12,
    fontWeight: '600',
  },
  unavailableDescription: {
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 32,
  },
});

export default BiometricSetupScreen;