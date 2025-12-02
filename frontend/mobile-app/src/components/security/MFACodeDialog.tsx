/**
 * MFACodeDialog - Multi-factor authentication code entry dialog
 * Supports TOTP, SMS, and Email MFA codes with proper validation
 */

import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  TouchableOpacity,
  Vibration,
  Animated,
  Dimensions,
} from 'react-native';
import {
  Modal,
  Portal,
  useTheme,
  Button,
  Surface,
  ActivityIndicator,
  Chip,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import HapticFeedback from 'react-native-haptic-feedback';

export type MFAMethod = 'TOTP' | 'SMS' | 'EMAIL';

interface MFACodeDialogProps {
  visible: boolean;
  method: MFAMethod;
  maskedContact?: string; // For SMS/Email: "***@***.com" or "***-***-1234"
  onCodeSubmit: (code: string) => Promise<boolean>;
  onCancel: () => void;
  onResendCode?: () => Promise<boolean>;
  codeLength?: number;
  allowResend?: boolean;
  resendCooldown?: number; // seconds
}

const MFACodeDialog: React.FC<MFACodeDialogProps> = ({
  visible,
  method,
  maskedContact,
  onCodeSubmit,
  onCancel,
  onResendCode,
  codeLength = 6,
  allowResend = true,
  resendCooldown = 30,
}) => {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [code, setCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [error, setError] = useState('');
  const [attempts, setAttempts] = useState(0);
  const [resendTimer, setResendTimer] = useState(0);
  const shakeAnimation = useRef(new Animated.Value(0)).current;
  const codeInputRefs = useRef<TextInput[]>([]);

  useEffect(() => {
    if (visible) {
      setCode('');
      setError('');
      setAttempts(0);
      setResendTimer(0);
      // Focus on first input when dialog opens
      setTimeout(() => {
        codeInputRefs.current[0]?.focus();
      }, 100);
    }
  }, [visible]);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (resendTimer > 0) {
      interval = setInterval(() => {
        setResendTimer((prev) => prev - 1);
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [resendTimer]);

  const getMethodIcon = () => {
    switch (method) {
      case 'TOTP':
        return 'shield-key';
      case 'SMS':
        return 'message-text';
      case 'EMAIL':
        return 'email';
      default:
        return 'shield-check';
    }
  };

  const getMethodTitle = () => {
    switch (method) {
      case 'TOTP':
        return 'Enter Authentication Code';
      case 'SMS':
        return 'Enter SMS Code';
      case 'EMAIL':
        return 'Enter Email Code';
      default:
        return 'Enter Verification Code';
    }
  };

  const getMethodSubtitle = () => {
    switch (method) {
      case 'TOTP':
        return 'Open your authenticator app to get the code';
      case 'SMS':
        return maskedContact ? `Code sent to ${maskedContact}` : 'Code sent to your phone';
      case 'EMAIL':
        return maskedContact ? `Code sent to ${maskedContact}` : 'Code sent to your email';
      default:
        return 'Enter the verification code';
    }
  };

  const handleCodeChange = (text: string, index: number) => {
    // Only allow numeric input
    if (!/^\d*$/.test(text)) {
      return;
    }

    const newCode = code.split('');
    newCode[index] = text;
    const updatedCode = newCode.join('').slice(0, codeLength);
    
    setCode(updatedCode);
    setError('');

    // Auto-focus next input
    if (text && index < codeLength - 1) {
      codeInputRefs.current[index + 1]?.focus();
    }

    // Auto-submit when code is complete
    if (updatedCode.length === codeLength) {
      handleSubmit(updatedCode);
    }
  };

  const handleKeyPress = (key: string, index: number) => {
    if (key === 'Backspace' && !code[index] && index > 0) {
      // Move to previous input on backspace if current is empty
      codeInputRefs.current[index - 1]?.focus();
    }
  };

  const handleSubmit = async (submittedCode?: string) => {
    const codeToSubmit = submittedCode || code;
    
    if (codeToSubmit.length !== codeLength) {
      setError(`Code must be ${codeLength} digits`);
      shakeInputs();
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const isValid = await onCodeSubmit(codeToSubmit);
      
      if (isValid) {
        // Success - provide haptic feedback
        HapticFeedback.trigger('notificationSuccess');
        setCode('');
        setAttempts(0);
      } else {
        // Invalid code
        const newAttempts = attempts + 1;
        setAttempts(newAttempts);
        setError(`Invalid code. ${Math.max(0, 5 - newAttempts)} attempts remaining.`);
        
        // Vibrate and shake on error
        Vibration.vibrate(200);
        HapticFeedback.trigger('notificationError');
        shakeInputs();
        
        // Clear code input
        setCode('');
        setTimeout(() => {
          codeInputRefs.current[0]?.focus();
        }, 100);

        // Auto-cancel after 5 failed attempts
        if (newAttempts >= 5) {
          setTimeout(() => {
            onCancel();
          }, 2000);
        }
      }
    } catch (error: any) {
      setError(error.message || 'Failed to verify code');
      HapticFeedback.trigger('notificationError');
      shakeInputs();
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendCode = async () => {
    if (!onResendCode || resendTimer > 0) return;

    setIsResending(true);
    setError('');

    try {
      const success = await onResendCode();
      if (success) {
        setResendTimer(resendCooldown);
        HapticFeedback.trigger('notificationSuccess');
        setError('');
        // Show success message briefly
        setError('New code sent successfully!');
        setTimeout(() => setError(''), 3000);
      } else {
        setError('Failed to resend code. Please try again.');
      }
    } catch (error: any) {
      setError(error.message || 'Failed to resend code');
    } finally {
      setIsResending(false);
    }
  };

  const shakeInputs = () => {
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
  };

  const handleCancel = () => {
    setCode('');
    setError('');
    setAttempts(0);
    setResendTimer(0);
    onCancel();
  };

  const renderCodeInputs = () => {
    return (
      <Animated.View 
        style={[
          styles.codeContainer,
          { transform: [{ translateX: shakeAnimation }] }
        ]}
      >
        {Array.from({ length: codeLength }, (_, index) => (
          <TextInput
            key={index}
            ref={(ref) => {
              if (ref) {
                codeInputRefs.current[index] = ref;
              }
            }}
            style={[
              styles.codeInput,
              {
                backgroundColor: theme.colors.surface,
                borderColor: code[index] ? theme.colors.primary : theme.colors.outline,
                color: theme.colors.onSurface,
              },
              error && !error.includes('sent') ? { borderColor: theme.colors.error } : {},
            ]}
            value={code[index] || ''}
            onChangeText={(text) => handleCodeChange(text.slice(-1), index)}
            onKeyPress={(e) => handleKeyPress(e.nativeEvent.key, index)}
            keyboardType="numeric"
            maxLength={1}
            textAlign="center"
            selectTextOnFocus
            editable={!isLoading}
          />
        ))}
      </Animated.View>
    );
  };

  return (
    <Portal>
      <Modal
        visible={visible}
        onDismiss={handleCancel}
        contentContainerStyle={[
          styles.modalContent,
          {
            backgroundColor: theme.colors.surface,
            marginTop: insets.top + 20,
            marginBottom: insets.bottom + 20,
          }
        ]}
      >
        <Surface style={[styles.dialogSurface, { backgroundColor: theme.colors.surface }]}>
          {/* Header */}
          <View style={styles.header}>
            <Icon
              name={getMethodIcon()}
              size={40}
              color={theme.colors.primary}
              style={styles.headerIcon}
            />
            <Text style={[styles.title, { color: theme.colors.onSurface }]}>
              {getMethodTitle()}
            </Text>
            <Text style={[styles.subtitle, { color: theme.colors.onSurfaceVariant }]}>
              {getMethodSubtitle()}
            </Text>
            
            {/* Method chip */}
            <Chip
              mode="outlined"
              style={styles.methodChip}
              textStyle={{ fontSize: 12 }}
            >
              {method}
            </Chip>
          </View>

          {/* Code Input */}
          <View style={styles.inputSection}>
            {renderCodeInputs()}
            
            {error ? (
              <Text style={[
                styles.messageText, 
                { 
                  color: error.includes('sent') ? theme.colors.primary : theme.colors.error 
                }
              ]}>
                {error}
              </Text>
            ) : (
              <Text style={[styles.helperText, { color: theme.colors.onSurfaceVariant }]}>
                Enter the {codeLength}-digit code
              </Text>
            )}
          </View>

          {/* Resend Option */}
          {allowResend && method !== 'TOTP' && (
            <View style={styles.resendSection}>
              <Text style={[styles.resendText, { color: theme.colors.onSurfaceVariant }]}>
                Didn't receive the code?
              </Text>
              <TouchableOpacity
                onPress={handleResendCode}
                disabled={resendTimer > 0 || isResending}
                style={styles.resendButton}
              >
                <Text style={[
                  styles.resendButtonText,
                  { 
                    color: resendTimer > 0 || isResending 
                      ? theme.colors.onSurfaceVariant 
                      : theme.colors.primary 
                  }
                ]}>
                  {isResending 
                    ? 'Sending...' 
                    : resendTimer > 0 
                      ? `Resend in ${resendTimer}s`
                      : 'Resend Code'
                  }
                </Text>
              </TouchableOpacity>
            </View>
          )}

          {/* Actions */}
          <View style={styles.actions}>
            <Button
              mode="outlined"
              onPress={handleCancel}
              disabled={isLoading || isResending}
              style={styles.actionButton}
            >
              Cancel
            </Button>
            
            <Button
              mode="contained"
              onPress={() => handleSubmit()}
              disabled={code.length !== codeLength || isLoading || isResending}
              style={styles.actionButton}
              loading={isLoading}
            >
              {isLoading ? 'Verifying...' : 'Verify'}
            </Button>
          </View>

          {/* Loading Overlay */}
          {(isLoading || isResending) && (
            <View style={styles.loadingOverlay}>
              <ActivityIndicator size="large" color={theme.colors.primary} />
            </View>
          )}
        </Surface>
      </Modal>
    </Portal>
  );
};

const { width } = Dimensions.get('window');

const styles = StyleSheet.create({
  modalContent: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  dialogSurface: {
    width: Math.min(width - 40, 400),
    borderRadius: 16,
    padding: 24,
    elevation: 8,
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  headerIcon: {
    marginBottom: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 12,
  },
  methodChip: {
    marginTop: 8,
  },
  inputSection: {
    marginBottom: 20,
  },
  codeContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 16,
  },
  codeInput: {
    width: 45,
    height: 55,
    borderWidth: 2,
    borderRadius: 8,
    fontSize: 24,
    fontWeight: '600',
    marginHorizontal: 4,
    elevation: 2,
  },
  messageText: {
    fontSize: 14,
    textAlign: 'center',
    fontWeight: '500',
    lineHeight: 20,
  },
  helperText: {
    fontSize: 14,
    textAlign: 'center',
  },
  resendSection: {
    alignItems: 'center',
    marginBottom: 24,
  },
  resendText: {
    fontSize: 14,
    marginBottom: 8,
  },
  resendButton: {
    padding: 8,
  },
  resendButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  actionButton: {
    flex: 1,
    marginHorizontal: 6,
  },
  loadingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 16,
  },
});

export default MFACodeDialog;