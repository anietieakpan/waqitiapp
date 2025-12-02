import React, { useState, useRef } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  Surface,
  Divider,
  HelperText,
  IconButton,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useForm, Controller } from 'react-hook-form';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useAuth } from '../../contexts/AuthContext';
import { useBiometric } from '../../hooks/useBiometric';
import HapticService from '../../services/HapticService';

interface LoginFormData {
  email: string;
  password: string;
}

/**
 * Login Screen - User authentication
 */
const LoginScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const { login, isLoading } = useAuth();
  const { isBiometricSupported, authenticateWithBiometrics } = useBiometric();
  
  const [showPassword, setShowPassword] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const passwordInputRef = useRef<any>(null);

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
    watch,
  } = useForm<LoginFormData>({
    mode: 'onChange',
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const watchedEmail = watch('email');

  const handleLogin = async (data: LoginFormData) => {
    try {
      Keyboard.dismiss();
      setLoginError(null);
      
      await login({
        email: data.email.toLowerCase().trim(),
        password: data.password,
      });
      
      HapticService.success();
    } catch (error: any) {
      HapticService.error();
      setLoginError(error.message || 'Login failed. Please try again.');
    }
  };

  const handleBiometricLogin = async () => {
    try {
      const result = await authenticateWithBiometrics();
      if (result.success) {
        // If biometric authentication is successful, proceed with stored credentials
        HapticService.success();
        // This would typically use stored credentials or a token
        navigation.navigate('Main' as never);
      }
    } catch (error) {
      HapticService.error();
      console.log('Biometric login failed:', error);
    }
  };

  const handleForgotPassword = () => {
    navigation.navigate('ForgotPassword' as never);
  };

  const handleRegister = () => {
    navigation.navigate('Register' as never);
  };

  const validateEmail = (email: string) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email) || 'Please enter a valid email address';
  };

  const validatePassword = (password: string) => {
    return password.length >= 8 || 'Password must be at least 8 characters';
  };

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
          <ScrollView
            style={styles.scrollView}
            contentContainerStyle={styles.scrollContent}
            showsVerticalScrollIndicator={false}
            keyboardShouldPersistTaps="handled"
          >
            <View style={styles.header}>
              <Surface style={styles.logoContainer} elevation={4}>
                <Icon name="wallet" size={60} color={theme.colors.primary} />
              </Surface>
              <Text style={styles.title}>Welcome back</Text>
              <Text style={styles.subtitle}>Sign in to your Waqiti account</Text>
            </View>

            <Surface style={styles.formContainer} elevation={2}>
              <View style={styles.formContent}>
                {loginError && (
                  <View style={styles.errorContainer}>
                    <Icon name="alert-circle" size={20} color={theme.colors.error} />
                    <Text style={styles.errorText}>{loginError}</Text>
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
                        returnKeyType="next"
                        onSubmitEditing={() => passwordInputRef.current?.focus()}
                        error={!!errors.email}
                        left={<TextInput.Icon icon="email" />}
                        style={styles.input}
                      />
                      <HelperText type="error" visible={!!errors.email}>
                        {errors.email?.message}
                      </HelperText>
                    </View>
                  )}
                />

                <Controller
                  control={control}
                  name="password"
                  rules={{
                    required: 'Password is required',
                    validate: validatePassword,
                  }}
                  render={({ field: { onChange, onBlur, value } }) => (
                    <View style={styles.inputContainer}>
                      <TextInput
                        ref={passwordInputRef}
                        label="Password"
                        value={value}
                        onBlur={onBlur}
                        onChangeText={onChange}
                        mode="outlined"
                        secureTextEntry={!showPassword}
                        autoCapitalize="none"
                        autoCorrect={false}
                        autoComplete="current-password"
                        returnKeyType="done"
                        onSubmitEditing={handleSubmit(handleLogin)}
                        error={!!errors.password}
                        left={<TextInput.Icon icon="lock" />}
                        right={
                          <TextInput.Icon
                            icon={showPassword ? 'eye-off' : 'eye'}
                            onPress={() => setShowPassword(!showPassword)}
                          />
                        }
                        style={styles.input}
                      />
                      <HelperText type="error" visible={!!errors.password}>
                        {errors.password?.message}
                      </HelperText>
                    </View>
                  )}
                />

                <TouchableOpacity
                  style={styles.forgotPasswordContainer}
                  onPress={handleForgotPassword}
                >
                  <Text style={styles.forgotPasswordText}>Forgot Password?</Text>
                </TouchableOpacity>

                <Button
                  mode="contained"
                  onPress={handleSubmit(handleLogin)}
                  loading={isLoading}
                  disabled={!isValid || isLoading}
                  style={styles.loginButton}
                  contentStyle={styles.loginButtonContent}
                >
                  Sign In
                </Button>

                {isBiometricSupported && watchedEmail && (
                  <>
                    <View style={styles.dividerContainer}>
                      <Divider style={styles.divider} />
                      <Text style={styles.dividerText}>or</Text>
                      <Divider style={styles.divider} />
                    </View>

                    <Button
                      mode="outlined"
                      onPress={handleBiometricLogin}
                      style={styles.biometricButton}
                      icon="fingerprint"
                    >
                      Use Biometric
                    </Button>
                  </>
                )}

                <View style={styles.socialLoginContainer}>
                  <Text style={styles.socialLoginText}>Or continue with</Text>
                  <View style={styles.socialButtons}>
                    <IconButton
                      icon="google"
                      size={24}
                      mode="contained"
                      onPress={() => {
                        // Handle Google Sign In
                        console.log('Google Sign In');
                      }}
                      style={styles.socialButton}
                    />
                    <IconButton
                      icon="apple"
                      size={24}
                      mode="contained"
                      onPress={() => {
                        // Handle Apple Sign In
                        console.log('Apple Sign In');
                      }}
                      style={styles.socialButton}
                    />
                    <IconButton
                      icon="facebook"
                      size={24}
                      mode="contained"
                      onPress={() => {
                        // Handle Facebook Sign In
                        console.log('Facebook Sign In');
                      }}
                      style={styles.socialButton}
                    />
                  </View>
                </View>
              </View>
            </Surface>

            <View style={styles.footer}>
              <Text style={styles.footerText}>Don't have an account? </Text>
              <TouchableOpacity onPress={handleRegister}>
                <Text style={styles.registerText}>Sign Up</Text>
              </TouchableOpacity>
            </View>
          </ScrollView>
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
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
  },
  header: {
    alignItems: 'center',
    marginBottom: 32,
  },
  logoContainer: {
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
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.8)',
    textAlign: 'center',
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
  },
  inputContainer: {
    marginBottom: 16,
  },
  input: {
    backgroundColor: 'white',
  },
  forgotPasswordContainer: {
    alignSelf: 'flex-end',
    marginBottom: 24,
  },
  forgotPasswordText: {
    color: '#2196F3',
    fontSize: 14,
  },
  loginButton: {
    marginBottom: 16,
  },
  loginButtonContent: {
    height: 48,
  },
  dividerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 16,
  },
  divider: {
    flex: 1,
  },
  dividerText: {
    marginHorizontal: 12,
    color: '#666',
    fontSize: 14,
  },
  biometricButton: {
    marginBottom: 24,
  },
  socialLoginContainer: {
    alignItems: 'center',
  },
  socialLoginText: {
    color: '#666',
    fontSize: 14,
    marginBottom: 16,
  },
  socialButtons: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 16,
  },
  socialButton: {
    backgroundColor: '#f5f5f5',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  footerText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 16,
  },
  registerText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default LoginScreen;