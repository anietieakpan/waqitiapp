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
import { useNavigation, useRoute } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useForm, Controller } from 'react-hook-form';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { userService } from '../../services/userService';
import HapticService from '../../services/HapticService';

interface ResetPasswordFormData {
  password: string;
  confirmPassword: string;
}

/**
 * Reset Password Screen - Set new password with token
 */
const ResetPasswordScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute();
  const { token } = route.params as { token: string };
  
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
    watch,
  } = useForm<ResetPasswordFormData>({
    mode: 'onChange',
    defaultValues: {
      password: '',
      confirmPassword: '',
    },
  });

  const watchedPassword = watch('password');

  const handleResetPassword = async (data: ResetPasswordFormData) => {
    try {
      setIsLoading(true);
      setError(null);
      
      await userService.resetPassword({
        token,
        password: data.password,
      });
      
      HapticService.success();
      navigation.navigate('Login' as never);
    } catch (error: any) {
      HapticService.error();
      setError(error.message || 'Failed to reset password. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleBackToLogin = () => {
    navigation.navigate('Login' as never);
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
              <Text style={styles.title}>Reset Password</Text>
              <Text style={styles.subtitle}>
                Create a new secure password for your account.
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
                  name="password"
                  rules={{
                    required: 'Password is required',
                    validate: validatePassword,
                  }}
                  render={({ field: { onChange, onBlur, value } }) => (
                    <View style={styles.inputContainer}>
                      <TextInput
                        label="New Password"
                        value={value}
                        onBlur={onBlur}
                        onChangeText={onChange}
                        mode="outlined"
                        secureTextEntry={!showPassword}
                        autoCapitalize="none"
                        autoCorrect={false}
                        autoComplete="new-password"
                        returnKeyType="next"
                        error={!!errors.password}
                        left={<TextInput.Icon icon="lock" />}
                        right={
                          <TextInput.Icon
                            icon={showPassword ? 'eye-off' : 'eye'}
                            onPress={() => setShowPassword(!showPassword)}
                          />
                        }
                        style={styles.input}
                        autoFocus
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
                        label="Confirm New Password"
                        value={value}
                        onBlur={onBlur}
                        onChangeText={onChange}
                        mode="outlined"
                        secureTextEntry={!showConfirmPassword}
                        autoCapitalize="none"
                        autoCorrect={false}
                        autoComplete="new-password"
                        returnKeyType="done"
                        onSubmitEditing={handleSubmit(handleResetPassword)}
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

                <Button
                  mode="contained"
                  onPress={handleSubmit(handleResetPassword)}
                  loading={isLoading}
                  disabled={!isValid || isLoading}
                  style={styles.resetButton}
                  contentStyle={styles.buttonContent}
                >
                  Reset Password
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
    marginBottom: 16,
  },
  input: {
    backgroundColor: 'white',
  },
  resetButton: {
    marginBottom: 12,
    marginTop: 8,
  },
  cancelButton: {
    marginTop: 8,
  },
  buttonContent: {
    height: 48,
  },
});

export default ResetPasswordScreen;