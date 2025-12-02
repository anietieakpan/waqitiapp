import React, { useState, useEffect } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import {
  Text,
  Button,
  useTheme,
  Surface,
} from 'react-native-paper';
import { useNavigation, useRoute } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { userService } from '../../services/userService';
import HapticService from '../../services/HapticService';

/**
 * Verify Email Screen - Email verification after registration
 */
const VerifyEmailScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute();
  const { email } = route.params as { email: string };
  
  const [isLoading, setIsLoading] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resendCooldown, setResendCooldown] = useState(0);

  useEffect(() => {
    if (resendCooldown > 0) {
      const timer = setTimeout(() => {
        setResendCooldown(resendCooldown - 1);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [resendCooldown]);

  const handleResendEmail = async () => {
    try {
      setIsLoading(true);
      setError(null);
      
      await userService.resendVerificationEmail(email);
      
      HapticService.success();
      setResendCooldown(60); // 60 second cooldown
    } catch (error: any) {
      HapticService.error();
      setError(error.message || 'Failed to resend verification email.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleContinue = () => {
    navigation.navigate('SetupPin' as never);
  };

  const handleBackToLogin = () => {
    navigation.navigate('Login' as never);
  };

  const handleChangeEmail = () => {
    navigation.goBack();
  };

  if (isVerified) {
    return (
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <LinearGradient
          colors={['#4CAF50', '#45a049']}
          style={styles.gradient}
        >
          <SafeAreaView style={styles.safeArea}>
            <View style={styles.content}>
              <View style={styles.header}>
                <Surface style={styles.iconContainer} elevation={4}>
                  <Icon name="check-circle" size={80} color="#4CAF50" />
                </Surface>
                <Text style={styles.title}>Email Verified!</Text>
                <Text style={styles.subtitle}>
                  Great! Your email has been verified successfully.
                </Text>
              </View>

              <Surface style={styles.formContainer} elevation={2}>
                <View style={styles.formContent}>
                  <Text style={styles.instructionText}>
                    Your account is now verified. Let's continue setting up your security.
                  </Text>

                  <Button
                    mode="contained"
                    onPress={handleContinue}
                    style={styles.continueButton}
                    contentStyle={styles.buttonContent}
                  >
                    Continue Setup
                  </Button>
                </View>
              </Surface>
            </View>
          </SafeAreaView>
        </LinearGradient>
      </KeyboardAvoidingView>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <LinearGradient
        colors={[theme.colors.primary, theme.colors.secondary]}
        style={styles.gradient}
      >
        <SafeAreaView style={styles.safeArea}>
          <View style={styles.content}>
            <TouchableOpacity
              style={styles.backButton}
              onPress={handleChangeEmail}
            >
              <Icon name="arrow-left" size={24} color="white" />
            </TouchableOpacity>

            <View style={styles.header}>
              <Surface style={styles.iconContainer} elevation={4}>
                <Icon name="email-check-outline" size={80} color={theme.colors.primary} />
              </Surface>
              <Text style={styles.title}>Check Your Email</Text>
              <Text style={styles.subtitle}>
                We've sent a verification link to{'\n'}
                <Text style={styles.email}>{email}</Text>
              </Text>
            </View>

            <Surface style={styles.formContainer} elevation={2}>
              <View style={styles.formContent}>
                {error && (
                  <View style={styles.errorContainer}>
                    <Icon name="alert-circle" size={20} color={theme.colors.error} />
                    <Text style={styles.errorText}>{error}</Text>
                  </View>
                )}

                <Text style={styles.instructionText}>
                  Please check your email and click the verification link to activate your account. 
                  The link will expire in 24 hours.
                </Text>

                <View style={styles.actionButtons}>
                  <Button
                    mode="contained"
                    onPress={handleResendEmail}
                    loading={isLoading}
                    disabled={isLoading || resendCooldown > 0}
                    style={styles.resendButton}
                    contentStyle={styles.buttonContent}
                  >
                    {resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 'Resend Email'}
                  </Button>

                  <Button
                    mode="outlined"
                    onPress={handleChangeEmail}
                    style={styles.changeEmailButton}
                    contentStyle={styles.buttonContent}
                  >
                    Change Email
                  </Button>
                </View>

                <View style={styles.divider} />

                <Text style={styles.helpText}>
                  Having trouble? Check your spam folder or contact support.
                </Text>
              </View>
            </Surface>

            <View style={styles.footer}>
              <Text style={styles.footerText}>
                Already verified?{' '}
                <TouchableOpacity onPress={handleBackToLogin}>
                  <Text style={styles.signInText}>Sign In</Text>
                </TouchableOpacity>
              </Text>
            </View>
          </View>
        </SafeAreaView>
      </LinearGradient>
    </KeyboardAvoidingView>
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
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  backButton: {
    position: 'absolute',
    top: 20,
    left: 24,
    padding: 8,
    zIndex: 1,
  },
  header: {
    alignItems: 'center',
    marginBottom: 32,
  },
  iconContainer: {
    width: 140,
    height: 140,
    borderRadius: 70,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'white',
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.8)',
    textAlign: 'center',
    lineHeight: 24,
  },
  email: {
    fontWeight: 'bold',
    color: 'white',
  },
  formContainer: {
    borderRadius: 16,
    backgroundColor: 'white',
    marginBottom: 24,
  },
  formContent: {
    padding: 24,
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffebee',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  errorText: {
    color: '#d32f2f',
    marginLeft: 8,
    fontSize: 14,
    flex: 1,
  },
  instructionText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 24,
  },
  actionButtons: {
    gap: 12,
    marginBottom: 24,
  },
  resendButton: {
    marginBottom: 0,
  },
  changeEmailButton: {
    borderColor: '#2196F3',
  },
  continueButton: {
    marginBottom: 0,
  },
  buttonContent: {
    height: 48,
  },
  divider: {
    height: 1,
    backgroundColor: '#e0e0e0',
    marginVertical: 16,
  },
  helpText: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
    lineHeight: 20,
  },
  footer: {
    alignItems: 'center',
  },
  footerText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },
  signInText: {
    color: 'white',
    fontWeight: 'bold',
  },
});

export default VerifyEmailScreen;