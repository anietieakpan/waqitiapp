import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  Surface,
  HelperText,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useForm, Controller } from 'react-hook-form';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { userService } from '../../services/userService';
import HapticService from '../../services/HapticService';

interface ForgotPasswordFormData {
  email: string;
}

/**
 * Forgot Password Screen - Password recovery
 */
const ForgotPasswordScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [isLoading, setIsLoading] = useState(false);
  const [isEmailSent, setIsEmailSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
    watch,
  } = useForm<ForgotPasswordFormData>({
    mode: 'onChange',
    defaultValues: {
      email: '',
    },
  });

  const watchedEmail = watch('email');

  const handleForgotPassword = async (data: ForgotPasswordFormData) => {
    try {
      setIsLoading(true);
      setError(null);
      
      await userService.requestPasswordReset(data.email.toLowerCase().trim());
      
      HapticService.success();
      setIsEmailSent(true);
    } catch (error: any) {
      HapticService.error();
      setError(error.message || 'Failed to send reset email. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleBackToLogin = () => {
    navigation.navigate('Login' as never);
  };

  const handleResendEmail = async () => {
    if (watchedEmail) {
      try {
        setIsLoading(true);
        await userService.requestPasswordReset(watchedEmail);
        HapticService.success();
      } catch (error: any) {
        HapticService.error();
        setError(error.message || 'Failed to resend email.');
      } finally {
        setIsLoading(false);
      }
    }
  };

  const validateEmail = (email: string) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email) || 'Please enter a valid email address';
  };

  if (isEmailSent) {
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
                onPress={handleBackToLogin}
              >
                <Icon name="arrow-left" size={24} color="white" />
              </TouchableOpacity>

              <View style={styles.header}>
                <Surface style={styles.iconContainer} elevation={4}>
                  <Icon name="email-check" size={80} color={theme.colors.primary} />
                </Surface>
                <Text style={styles.title}>Check Your Email</Text>
                <Text style={styles.subtitle}>
                  We've sent a password reset link to{'\n'}
                  <Text style={styles.email}>{watchedEmail}</Text>
                </Text>
              </View>

              <Surface style={styles.formContainer} elevation={2}>
                <View style={styles.formContent}>
                  <Text style={styles.instructionText}>
                    Please check your email and click the link to reset your password. 
                    The link will expire in 24 hours.
                  </Text>

                  <Button
                    mode="contained"
                    onPress={handleResendEmail}
                    loading={isLoading}
                    disabled={isLoading}
                    style={styles.resendButton}
                    contentStyle={styles.buttonContent}
                  >
                    Resend Email
                  </Button>

                  <Button
                    mode="outlined"
                    onPress={handleBackToLogin}
                    style={styles.backToLoginButton}
                    contentStyle={styles.buttonContent}
                  >
                    Back to Sign In
                  </Button>
                </View>
              </Surface>

              <View style={styles.footer}>
                <Text style={styles.footerText}>
                  Didn't receive the email? Check your spam folder or{' '}
                  <TouchableOpacity onPress={handleResendEmail}>
                    <Text style={styles.resendText}>try again</Text>
                  </TouchableOpacity>
                </Text>
              </View>
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
              onPress={handleBackToLogin}
            >
              <Icon name="arrow-left" size={24} color="white" />
            </TouchableOpacity>

            <View style={styles.header}>
              <Surface style={styles.iconContainer} elevation={4}>
                <Icon name="lock-reset" size={80} color={theme.colors.primary} />
              </Surface>
              <Text style={styles.title}>Forgot Password?</Text>
              <Text style={styles.subtitle}>
                No worries! Enter your email and we'll send you a reset link.
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

                <Controller
                  control={control}
                  name="email"
                  rules={{
                    required: 'Email is required',
                    validate: validateEmail,
                  }}
                  render={({ field: { onChange, onBlur, value } }) => (
                    <View style={styles.inputContainer}>
                      <TextInput
                        label="Email Address"
                        value={value}
                        onBlur={onBlur}
                        onChangeText={onChange}
                        mode="outlined"
                        keyboardType="email-address"
                        autoCapitalize="none"
                        autoCorrect={false}
                        autoComplete="email"
                        returnKeyType="done"
                        onSubmitEditing={handleSubmit(handleForgotPassword)}
                        error={!!errors.email}
                        left={<TextInput.Icon icon="email" />}
                        style={styles.input}
                        autoFocus
                      />
                      <HelperText type="error" visible={!!errors.email}>
                        {errors.email?.message}
                      </HelperText>
                    </View>
                  )}
                />

                <Button
                  mode="contained"
                  onPress={handleSubmit(handleForgotPassword)}
                  loading={isLoading}
                  disabled={!isValid || isLoading}
                  style={styles.sendButton}
                  contentStyle={styles.buttonContent}
                >
                  Send Reset Link
                </Button>

                <Button
                  mode="text"
                  onPress={handleBackToLogin}
                  style={styles.cancelButton}
                  contentStyle={styles.buttonContent}
                >
                  Cancel
                </Button>
              </View>
            </Surface>

            <View style={styles.footer}>
              <Text style={styles.footerText}>
                Remember your password?{' '}
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
  inputContainer: {
    marginBottom: 24,
  },
  input: {
    backgroundColor: 'white',
  },
  instructionText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 24,
  },
  sendButton: {
    marginBottom: 12,
  },
  resendButton: {
    marginBottom: 12,
  },
  backToLoginButton: {
    borderColor: '#2196F3',
  },
  cancelButton: {
    marginTop: 8,
  },
  buttonContent: {
    height: 48,
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
  resendText: {
    color: 'white',
    fontWeight: 'bold',
    textDecorationLine: 'underline',
  },
});

export default ForgotPasswordScreen;