/**
 * PinEntryDialog - PIN entry modal component for security operations
 * Provides secure PIN input with proper validation and feedback
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
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import HapticFeedback from 'react-native-haptic-feedback';

interface PinEntryDialogProps {
  visible: boolean;
  title: string;
  subtitle?: string;
  onPinEntered: (pin: string) => Promise<boolean>;
  onCancel: () => void;
  allowBiometric?: boolean;
  onBiometricPressed?: () => void;
  maxLength?: number;
  allowNumericOnly?: boolean;
}

const PinEntryDialog: React.FC<PinEntryDialogProps> = ({
  visible,
  title,
  subtitle,
  onPinEntered,
  onCancel,
  allowBiometric = false,
  onBiometricPressed,
  maxLength = 6,
  allowNumericOnly = true,
}) => {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [pin, setPin] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [attempts, setAttempts] = useState(0);
  const shakeAnimation = useRef(new Animated.Value(0)).current;

  const pinInputRefs = useRef<TextInput[]>([]);

  useEffect(() => {
    if (visible) {
      setPin('');
      setError('');
      setAttempts(0);
      // Focus on first input when dialog opens
      setTimeout(() => {
        pinInputRefs.current[0]?.focus();
      }, 100);
    }
  }, [visible]);

  const handlePinChange = (text: string, index: number) => {
    if (allowNumericOnly && !/^\d*$/.test(text)) {
      return;
    }

    const newPin = pin.split('');
    newPin[index] = text;
    const updatedPin = newPin.join('').slice(0, maxLength);
    
    setPin(updatedPin);
    setError('');

    // Auto-focus next input
    if (text && index < maxLength - 1) {
      pinInputRefs.current[index + 1]?.focus();
    }

    // Auto-submit when PIN is complete
    if (updatedPin.length === maxLength) {
      handleSubmit(updatedPin);
    }
  };

  const handleKeyPress = (key: string, index: number) => {
    if (key === 'Backspace' && !pin[index] && index > 0) {
      // Move to previous input on backspace if current is empty
      pinInputRefs.current[index - 1]?.focus();
    }
  };

  const handleSubmit = async (submittedPin?: string) => {
    const pinToSubmit = submittedPin || pin;
    
    if (pinToSubmit.length !== maxLength) {
      setError(`PIN must be ${maxLength} digits`);
      shakeInputs();
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const isValid = await onPinEntered(pinToSubmit);
      
      if (isValid) {
        // Success - provide haptic feedback
        HapticFeedback.trigger('notificationSuccess');
        setPin('');
        setAttempts(0);
      } else {
        // Invalid PIN
        const newAttempts = attempts + 1;
        setAttempts(newAttempts);
        setError(`Invalid PIN. ${Math.max(0, 3 - newAttempts)} attempts remaining.`);
        
        // Vibrate and shake on error
        Vibration.vibrate(200);
        HapticFeedback.trigger('notificationError');
        shakeInputs();
        
        // Clear PIN input
        setPin('');
        setTimeout(() => {
          pinInputRefs.current[0]?.focus();
        }, 100);

        // Auto-cancel after 3 failed attempts
        if (newAttempts >= 3) {
          setTimeout(() => {
            onCancel();
          }, 2000);
        }
      }
    } catch (error: any) {
      setError(error.message || 'Failed to verify PIN');
      HapticFeedback.trigger('notificationError');
      shakeInputs();
    } finally {
      setIsLoading(false);
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

  const handleBiometric = () => {
    if (onBiometricPressed) {
      HapticFeedback.trigger('selection');
      onBiometricPressed();
    }
  };

  const handleCancel = () => {
    setPin('');
    setError('');
    setAttempts(0);
    onCancel();
  };

  const renderPinInputs = () => {
    return (
      <Animated.View 
        style={[
          styles.pinContainer,
          { transform: [{ translateX: shakeAnimation }] }
        ]}
      >
        {Array.from({ length: maxLength }, (_, index) => (
          <TextInput
            key={index}
            ref={(ref) => {
              if (ref) {
                pinInputRefs.current[index] = ref;
              }
            }}
            style={[
              styles.pinInput,
              {
                backgroundColor: theme.colors.surface,
                borderColor: pin[index] ? theme.colors.primary : theme.colors.outline,
                color: theme.colors.onSurface,
              },
              error ? { borderColor: theme.colors.error } : {},
            ]}
            value={pin[index] || ''}
            onChangeText={(text) => handlePinChange(text.slice(-1), index)}
            onKeyPress={(e) => handleKeyPress(e.nativeEvent.key, index)}
            keyboardType={allowNumericOnly ? 'numeric' : 'default'}
            secureTextEntry
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
              name="shield-lock"
              size={40}
              color={theme.colors.primary}
              style={styles.headerIcon}
            />
            <Text style={[styles.title, { color: theme.colors.onSurface }]}>
              {title}
            </Text>
            {subtitle && (
              <Text style={[styles.subtitle, { color: theme.colors.onSurfaceVariant }]}>
                {subtitle}
              </Text>
            )}
          </View>

          {/* PIN Input */}
          <View style={styles.inputSection}>
            {renderPinInputs()}
            
            {error ? (
              <Text style={[styles.errorText, { color: theme.colors.error }]}>
                {error}
              </Text>
            ) : (
              <Text style={[styles.helperText, { color: theme.colors.onSurfaceVariant }]}>
                Enter your {maxLength}-digit PIN
              </Text>
            )}
          </View>

          {/* Biometric Option */}
          {allowBiometric && (
            <TouchableOpacity
              style={[styles.biometricButton, { borderColor: theme.colors.outline }]}
              onPress={handleBiometric}
              disabled={isLoading}
            >
              <Icon
                name="fingerprint"
                size={24}
                color={theme.colors.primary}
              />
              <Text style={[styles.biometricText, { color: theme.colors.primary }]}>
                Use Biometric
              </Text>
            </TouchableOpacity>
          )}

          {/* Actions */}
          <View style={styles.actions}>
            <Button
              mode="outlined"
              onPress={handleCancel}
              disabled={isLoading}
              style={styles.actionButton}
            >
              Cancel
            </Button>
            
            <Button
              mode="contained"
              onPress={() => handleSubmit()}
              disabled={pin.length !== maxLength || isLoading}
              style={styles.actionButton}
              loading={isLoading}
            >
              {isLoading ? 'Verifying...' : 'Confirm'}
            </Button>
          </View>

          {/* Loading Overlay */}
          {isLoading && (
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
  },
  inputSection: {
    marginBottom: 24,
  },
  pinContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 16,
  },
  pinInput: {
    width: 45,
    height: 55,
    borderWidth: 2,
    borderRadius: 8,
    fontSize: 24,
    fontWeight: '600',
    marginHorizontal: 6,
    elevation: 2,
  },
  errorText: {
    fontSize: 14,
    textAlign: 'center',
    fontWeight: '500',
  },
  helperText: {
    fontSize: 14,
    textAlign: 'center',
  },
  biometricButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 12,
    borderWidth: 1,
    borderRadius: 8,
    marginBottom: 24,
  },
  biometricText: {
    marginLeft: 8,
    fontSize: 16,
    fontWeight: '500',
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

export default PinEntryDialog;