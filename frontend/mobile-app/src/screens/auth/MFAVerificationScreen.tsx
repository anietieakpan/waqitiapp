import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Animated,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  Surface,
  IconButton,
} from 'react-native-paper';
import { useNavigation, useRoute } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useForm, Controller } from 'react-hook-form';
import HapticService from '../../services/HapticService';

interface VerificationFormData {
  code: string;
}

interface RouteParams {
  method: 'sms' | 'email' | 'totp';
}

/**
 * MFA Verification Screen - Verify MFA setup with code
 */
const MFAVerificationScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute();
  const { method } = route.params as RouteParams;
  
  const [isVerifying, setIsVerifying] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [verificationError, setVerificationError] = useState<string | null>(null);
  
  const shakeAnimation = useRef(new Animated.Value(0)).current;
  const countdownRef = useRef<NodeJS.Timeout | null>(null);

  const {
    control,
    handleSubmit,
    watch,
    formState: { errors },
    reset,
  } = useForm<VerificationFormData>({
    mode: 'onChange',
    defaultValues: {
      code: '',
    },
  });

  const watchedCode = watch('code');

  useEffect(() => {
    // Start countdown for resend
    if (method !== 'totp') {
      startCountdown(30);
    }

    return () => {
      if (countdownRef.current) {
        clearInterval(countdownRef.current);
      }
    };
  }, [method]);

  const startCountdown = (seconds: number) => {
    setCountdown(seconds);
    countdownRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          if (countdownRef.current) {
            clearInterval(countdownRef.current);
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  const handleVerification = async (data: VerificationFormData) => {
    try {
      setIsVerifying(true);
      setVerificationError(null);
      
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // Mock verification logic
      const isValidCode = data.code === '123456' || data.code === '000000';
      
      if (isValidCode) {
        HapticService.success();
        navigation.navigate('Main' as never);
      } else {
        throw new Error('Invalid verification code');
      }
    } catch (error: any) {
      HapticService.error();
      setVerificationError(error.message || 'Verification failed');
      
      // Shake animation
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
      
      // Clear the form
      reset();
    } finally {
      setIsVerifying(false);
    }
  };

  const handleResendCode = async () => {
    if (method === 'totp' || countdown > 0) return;
    
    try {
      setIsResending(true);
      setVerificationError(null);
      
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      HapticService.success();
      startCountdown(30);
    } catch (error: any) {
      HapticService.error();
      setVerificationError('Failed to resend code');
    } finally {
      setIsResending(false);
    }
  };

  const getMethodInfo = () => {
    switch (method) {
      case 'sms':
        return {
          title: 'SMS Verification',
          description: 'Enter the 6-digit code sent to your phone number',
          icon: 'message-text',
          contactInfo: '+1 (555) 123-4567',
        };
      case 'email':
        return {
          title: 'Email Verification',
          description: 'Enter the 6-digit code sent to your email',
          icon: 'email',
          contactInfo: 'user@example.com',
        };
      case 'totp':
        return {
          title: 'Authenticator Code',
          description: 'Enter the 6-digit code from your authenticator app',
          icon: 'shield-key',
          contactInfo: 'Google Authenticator or similar app',
        };
      default:
        return {
          title: 'Verification',
          description: 'Enter the verification code',
          icon: 'shield-check',
          contactInfo: '',
        };
    }
  };

  const validateCode = (code: string) => {
    if (!code) return 'Verification code is required';
    if (code.length !== 6) return 'Code must be 6 digits';
    if (!/^\d+$/.test(code)) return 'Code must contain only numbers';
    return true;
  };

  const methodInfo = getMethodInfo();

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
          <View style={styles.header}>
            <TouchableOpacity
              style={styles.backButton}
              onPress={() => navigation.goBack()}
            >
              <Icon name="arrow-left" size={24} color="white" />
            </TouchableOpacity>

            <Surface style={styles.iconContainer} elevation={4}>
              <Icon name={methodInfo.icon} size={60} color={theme.colors.primary} />
            </Surface>
            
            <Text style={styles.title}>{methodInfo.title}</Text>
            <Text style={styles.subtitle}>{methodInfo.description}</Text>
            
            {methodInfo.contactInfo && (
              <Surface style={styles.contactInfo} elevation={1}>
                <Text style={styles.contactText}>{methodInfo.contactInfo}</Text>
              </Surface>
            )}
          </View>

          <View style={styles.content}>
            <Animated.View
              style={[
                styles.formContainer,
                { transform: [{ translateX: shakeAnimation }] }
              ]}
            >
              <Controller
                control={control}
                name="code"
                rules={{
                  required: 'Verification code is required',
                  validate: validateCode,
                }}
                render={({ field: { onChange, onBlur, value } }) => (
                  <TextInput
                    label="Verification Code"
                    value={value}
                    onBlur={onBlur}
                    onChangeText={onChange}
                    mode="outlined"
                    keyboardType="numeric"
                    maxLength={6}
                    autoCapitalize="none"
                    autoCorrect={false}
                    autoComplete="one-time-code"
                    returnKeyType="done"
                    onSubmitEditing={handleSubmit(handleVerification)}
                    error={!!errors.code || !!verificationError}
                    style={styles.codeInput}
                    contentStyle={styles.codeInputContent}
                    outlineStyle={styles.codeInputOutline}
                    autoFocus
                  />
                )}
              />

              {(errors.code || verificationError) && (
                <View style={styles.errorContainer}>
                  <Icon name="alert-circle" size={16} color="#FF5252" />
                  <Text style={styles.errorText}>
                    {errors.code?.message || verificationError}
                  </Text>
                </View>
              )}

              <Button
                mode="contained"
                onPress={handleSubmit(handleVerification)}
                loading={isVerifying}
                disabled={!watchedCode || watchedCode.length !== 6 || isVerifying}
                style={styles.verifyButton}
                contentStyle={styles.buttonContent}
              >
                Verify Code
              </Button>
            </Animated.View>

            {/* Resend Code */}
            {method !== 'totp' && (
              <View style={styles.resendContainer}>
                {countdown > 0 ? (
                  <Text style={styles.countdownText}>
                    Resend code in {countdown} seconds
                  </Text>
                ) : (
                  <TouchableOpacity
                    onPress={handleResendCode}
                    disabled={isResending}
                    style={styles.resendButton}
                  >
                    <Text style={styles.resendButtonText}>
                      {isResending ? 'Sending...' : 'Resend Code'}
                    </Text>
                  </TouchableOpacity>
                )}
              </View>
            )}

            {/* Help Text */}
            <View style={styles.helpContainer}>
              {method === 'totp' ? (
                <Text style={styles.helpText}>
                  Open your authenticator app and enter the 6-digit code shown for Waqiti
                </Text>
              ) : (
                <Text style={styles.helpText}>
                  Didn't receive the code? Check your {method === 'sms' ? 'messages' : 'spam folder'} or try resending
                </Text>
              )}
            </View>
          </View>

          {/* Footer */}
          <View style={styles.footer}>
            <Surface style={styles.securityNotice} elevation={1}>
              <Icon name="shield-check" size={16} color="#4CAF50" />
              <Text style={styles.securityText}>
                This code will expire in 10 minutes
              </Text>
            </Surface>

            <TouchableOpacity
              style={styles.troubleButton}
              onPress={() => {
                // Navigate to help or contact support
                console.log('Having trouble?');
              }}
            >
              <Text style={styles.troubleText}>Having trouble?</Text>
            </TouchableOpacity>
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
    padding: 24,
  },
  header: {
    alignItems: 'center',
    marginTop: 20,
    marginBottom: 40,
  },
  backButton: {
    position: 'absolute',
    top: 0,
    left: 0,
    padding: 8,
    zIndex: 1,
  },
  iconContainer: {
    width: 100,
    height: 100,
    borderRadius: 50,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'white',
    marginBottom: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.9)',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 16,
  },
  contactInfo: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
  },
  contactText: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
  },
  formContainer: {
    alignItems: 'center',
  },
  codeInput: {
    width: '100%',
    maxWidth: 300,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    marginBottom: 8,
  },
  codeInputContent: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    letterSpacing: 8,
  },
  codeInputOutline: {
    borderWidth: 2,
    borderRadius: 12,
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    paddingHorizontal: 16,
    paddingVertical: 8,
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
  verifyButton: {
    width: '100%',
    maxWidth: 300,
    backgroundColor: 'white',
    marginTop: 8,
  },
  buttonContent: {
    height: 50,
  },
  resendContainer: {
    alignItems: 'center',
    marginTop: 24,
  },
  countdownText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 14,
  },
  resendButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  resendButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '500',
    textDecorationLine: 'underline',
  },
  helpContainer: {
    alignItems: 'center',
    marginTop: 24,
    paddingHorizontal: 20,
  },
  helpText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },
  footer: {
    alignItems: 'center',
    marginTop: 20,
  },
  securityNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    marginBottom: 16,
  },
  securityText: {
    marginLeft: 8,
    fontSize: 12,
    color: '#4CAF50',
    fontWeight: '500',
  },
  troubleButton: {
    padding: 8,
  },
  troubleText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 14,
    textDecorationLine: 'underline',
  },
});

export default MFAVerificationScreen;