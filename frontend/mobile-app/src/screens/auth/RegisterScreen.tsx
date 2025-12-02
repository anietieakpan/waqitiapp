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
  Checkbox,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useForm, Controller } from 'react-hook-form';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useAuth } from '../../contexts/AuthContext';
import HapticService from '../../services/HapticService';

interface RegisterFormData {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
}

/**
 * Register Screen - New user registration
 */
const RegisterScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const { register, isLoading } = useAuth();
  
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [acceptMarketing, setAcceptMarketing] = useState(false);
  const [registerError, setRegisterError] = useState<string | null>(null);
  
  const lastNameInputRef = useRef<any>(null);
  const emailInputRef = useRef<any>(null);
  const phoneInputRef = useRef<any>(null);
  const passwordInputRef = useRef<any>(null);
  const confirmPasswordInputRef = useRef<any>(null);

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
    watch,
  } = useForm<RegisterFormData>({
    mode: 'onChange',
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      phoneNumber: '',
      password: '',
      confirmPassword: '',
    },
  });

  const watchedPassword = watch('password');

  const handleRegister = async (data: RegisterFormData) => {
    try {
      if (!acceptTerms) {
        setRegisterError('Please accept the Terms & Conditions to continue.');
        return;
      }

      Keyboard.dismiss();
      setRegisterError(null);
      
      await register({
        firstName: data.firstName.trim(),
        lastName: data.lastName.trim(),
        email: data.email.toLowerCase().trim(),
        phoneNumber: data.phoneNumber.trim(),
        password: data.password,
        acceptMarketing,
      });
      
      HapticService.success();
      navigation.navigate('VerifyEmail', { email: data.email } as never);
    } catch (error: any) {
      HapticService.error();
      setRegisterError(error.message || 'Registration failed. Please try again.');
    }
  };

  const handleLogin = () => {
    navigation.navigate('Login' as never);
  };

  const validateEmail = (email: string) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email) || 'Please enter a valid email address';
  };

  const validatePhone = (phone: string) => {
    const phoneRegex = /^\+?[\d\s\-\(\)]{10,}$/;
    return phoneRegex.test(phone) || 'Please enter a valid phone number';
  };

  const validatePassword = (password: string) => {
    if (password.length < 8) return 'Password must be at least 8 characters';
    if (!/[A-Z]/.test(password)) return 'Password must contain an uppercase letter';
    if (!/[a-z]/.test(password)) return 'Password must contain a lowercase letter';
    if (!/\d/.test(password)) return 'Password must contain a number';
    if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) return 'Password must contain a special character';
    return true;
  };

  const validateConfirmPassword = (confirmPassword: string) => {
    return confirmPassword === watchedPassword || 'Passwords do not match';
  };

  const validateName = (name: string) => {
    return name.trim().length >= 2 || 'Name must be at least 2 characters';
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
              <TouchableOpacity
                style={styles.backButton}
                onPress={() => navigation.goBack()}
              >
                <Icon name="arrow-left" size={24} color="white" />
              </TouchableOpacity>
              
              <Surface style={styles.logoContainer} elevation={4}>
                <Icon name="account-plus" size={60} color={theme.colors.primary} />
              </Surface>
              <Text style={styles.title}>Create Account</Text>
              <Text style={styles.subtitle}>Join Waqiti and start sending money instantly</Text>
            </View>

            <Surface style={styles.formContainer} elevation={2}>
              <View style={styles.formContent}>
                {registerError && (
                  <View style={styles.errorContainer}>
                    <Icon name="alert-circle" size={20} color={theme.colors.error} />
                    <Text style={styles.errorText}>{registerError}</Text>
                  </View>
                )}

                <View style={styles.nameRow}>
                  <Controller
                    control={control}
                    name="firstName"
                    rules={{
                      required: 'First name is required',
                      validate: validateName,
                    }}
                    render={({ field: { onChange, onBlur, value } }) => (
                      <View style={[styles.inputContainer, styles.nameInput]}>
                        <TextInput
                          label="First Name"
                          value={value}
                          onBlur={onBlur}
                          onChangeText={onChange}
                          mode="outlined"
                          autoCapitalize="words"
                          autoCorrect={false}
                          returnKeyType="next"
                          onSubmitEditing={() => lastNameInputRef.current?.focus()}
                          error={!!errors.firstName}
                          style={styles.input}
                        />
                        <HelperText type="error" visible={!!errors.firstName}>
                          {errors.firstName?.message}
                        </HelperText>
                      </View>
                    )}
                  />

                  <Controller
                    control={control}
                    name="lastName"
                    rules={{
                      required: 'Last name is required',
                      validate: validateName,
                    }}
                    render={({ field: { onChange, onBlur, value } }) => (
                      <View style={[styles.inputContainer, styles.nameInput]}>
                        <TextInput
                          ref={lastNameInputRef}
                          label="Last Name"
                          value={value}
                          onBlur={onBlur}
                          onChangeText={onChange}
                          mode="outlined"
                          autoCapitalize="words"
                          autoCorrect={false}
                          returnKeyType="next"
                          onSubmitEditing={() => emailInputRef.current?.focus()}
                          error={!!errors.lastName}
                          style={styles.input}
                        />
                        <HelperText type="error" visible={!!errors.lastName}>
                          {errors.lastName?.message}
                        </HelperText>
                      </View>
                    )}
                  />
                </View>

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
                        ref={emailInputRef}
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
                        onSubmitEditing={() => phoneInputRef.current?.focus()}
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
                  name="phoneNumber"
                  rules={{
                    required: 'Phone number is required',
                    validate: validatePhone,
                  }}
                  render={({ field: { onChange, onBlur, value } }) => (
                    <View style={styles.inputContainer}>
                      <TextInput
                        ref={phoneInputRef}
                        label="Phone Number"
                        value={value}
                        onBlur={onBlur}
                        onChangeText={onChange}
                        mode="outlined"
                        keyboardType="phone-pad"
                        autoComplete="tel"
                        returnKeyType="next"
                        onSubmitEditing={() => passwordInputRef.current?.focus()}
                        error={!!errors.phoneNumber}
                        left={<TextInput.Icon icon="phone" />}
                        style={styles.input}
                      />
                      <HelperText type="error" visible={!!errors.phoneNumber}>
                        {errors.phoneNumber?.message}
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
                        autoComplete="new-password"
                        returnKeyType="next"
                        onSubmitEditing={() => confirmPasswordInputRef.current?.focus()}
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

                <Controller
                  control={control}
                  name="confirmPassword"
                  rules={{
                    required: 'Please confirm your password',
                    validate: validateConfirmPassword,
                  }}
                  render={({ field: { onChange, onBlur, value } }) => (
                    <View style={styles.inputContainer}>
                      <TextInput
                        ref={confirmPasswordInputRef}
                        label="Confirm Password"
                        value={value}
                        onBlur={onBlur}
                        onChangeText={onChange}
                        mode="outlined"
                        secureTextEntry={!showConfirmPassword}
                        autoCapitalize="none"
                        autoCorrect={false}
                        autoComplete="new-password"
                        returnKeyType="done"
                        onSubmitEditing={handleSubmit(handleRegister)}
                        error={!!errors.confirmPassword}
                        left={<TextInput.Icon icon="lock-check" />}
                        right={
                          <TextInput.Icon
                            icon={showConfirmPassword ? 'eye-off' : 'eye'}
                            onPress={() => setShowConfirmPassword(!showConfirmPassword)}
                          />
                        }
                        style={styles.input}
                      />
                      <HelperText type="error" visible={!!errors.confirmPassword}>
                        {errors.confirmPassword?.message}
                      </HelperText>
                    </View>
                  )}
                />

                <View style={styles.checkboxContainer}>
                  <Checkbox
                    status={acceptTerms ? 'checked' : 'unchecked'}
                    onPress={() => setAcceptTerms(!acceptTerms)}
                  />
                  <View style={styles.checkboxTextContainer}>
                    <Text style={styles.checkboxText}>
                      I agree to the{' '}
                      <Text style={styles.linkText}>Terms & Conditions</Text>
                      {' '}and{' '}
                      <Text style={styles.linkText}>Privacy Policy</Text>
                    </Text>
                  </View>
                </View>

                <View style={styles.checkboxContainer}>
                  <Checkbox
                    status={acceptMarketing ? 'checked' : 'unchecked'}
                    onPress={() => setAcceptMarketing(!acceptMarketing)}
                  />
                  <View style={styles.checkboxTextContainer}>
                    <Text style={styles.checkboxText}>
                      I'd like to receive promotional emails and updates
                    </Text>
                  </View>
                </View>

                <Button
                  mode="contained"
                  onPress={handleSubmit(handleRegister)}
                  loading={isLoading}
                  disabled={!isValid || !acceptTerms || isLoading}
                  style={styles.registerButton}
                  contentStyle={styles.registerButtonContent}
                >
                  Create Account
                </Button>

                <View style={styles.socialLoginContainer}>
                  <Text style={styles.socialLoginText}>Or sign up with</Text>
                  <View style={styles.socialButtons}>
                    <IconButton
                      icon="google"
                      size={24}
                      mode="contained"
                      onPress={() => {
                        // Handle Google Sign Up
                        console.log('Google Sign Up');
                      }}
                      style={styles.socialButton}
                    />
                    <IconButton
                      icon="apple"
                      size={24}
                      mode="contained"
                      onPress={() => {
                        // Handle Apple Sign Up
                        console.log('Apple Sign Up');
                      }}
                      style={styles.socialButton}
                    />
                  </View>
                </View>
              </View>
            </Surface>

            <View style={styles.footer}>
              <Text style={styles.footerText}>Already have an account? </Text>
              <TouchableOpacity onPress={handleLogin}>
                <Text style={styles.loginText}>Sign In</Text>
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
    padding: 24,
  },
  header: {
    alignItems: 'center',
    marginBottom: 32,
  },
  backButton: {
    position: 'absolute',
    top: 0,
    left: 0,
    padding: 8,
    zIndex: 1,
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
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    gap: 12,
  },
  nameInput: {
    flex: 1,
  },
  inputContainer: {
    marginBottom: 16,
  },
  input: {
    backgroundColor: 'white',
  },
  checkboxContainer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  checkboxTextContainer: {
    flex: 1,
    marginLeft: 8,
    marginTop: 8,
  },
  checkboxText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  linkText: {
    color: '#2196F3',
    textDecorationLine: 'underline',
  },
  registerButton: {
    marginBottom: 24,
  },
  registerButtonContent: {
    height: 48,
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
  loginText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default RegisterScreen;