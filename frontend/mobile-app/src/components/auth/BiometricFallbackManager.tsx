/**
 * BiometricFallbackManager - Comprehensive fallback authentication management
 * Handles fallback authentication methods when biometric authentication fails
 * or is unavailable, providing seamless transition between authentication methods.
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  Alert,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { useBiometric } from '../../hooks/useBiometric';
import {
  AuthenticationMethod,
  BiometricAuthError,
  BiometricFallbackOptions,
} from '../../services/biometric/types';
import BiometricTypeIcon from './BiometricTypeIcon';

export interface BiometricFallbackManagerProps {
  userId: string;
  visible: boolean;
  onSuccess: (method: AuthenticationMethod) => void;
  onCancel: () => void;
  onError?: (error: string) => void;
  fallbackOptions?: BiometricFallbackOptions;
  primaryMethod?: AuthenticationMethod;
  reason?: string;
  title?: string;
  allowBiometricRetry?: boolean;
}

export const BiometricFallbackManager: React.FC<BiometricFallbackManagerProps> = ({
  userId,
  visible,
  onSuccess,
  onCancel,
  onError,
  fallbackOptions = {
    allowPin: true,
    allowPassword: true,
    allowMFA: false,
    maxFallbackAttempts: 3,
    lockoutDuration: 300000, // 5 minutes
  },
  primaryMethod = AuthenticationMethod.PIN,
  reason = 'Biometric authentication failed',
  title = 'Alternative Authentication',
  allowBiometricRetry = true,
}) => {
  const {
    canAuthenticate,
    capabilities,
    authenticate,
    handleFallback,
    clearError,
  } = useBiometric();

  const [currentMethod, setCurrentMethod] = useState<AuthenticationMethod>(primaryMethod);
  const [pinInput, setPinInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [mfaInput, setMfaInput] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [attemptCount, setAttemptCount] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  // Reset state when modal becomes visible
  useEffect(() => {
    if (visible) {
      setPinInput('');
      setPasswordInput('');
      setMfaInput('');
      setError(null);
      setAttemptCount(0);
      setCurrentMethod(primaryMethod);
      clearError();
    }
  }, [visible, primaryMethod, clearError]);

  // Get available fallback methods
  const getAvailableMethods = useCallback((): AuthenticationMethod[] => {
    const methods: AuthenticationMethod[] = [];
    
    if (fallbackOptions.allowPin) {
      methods.push(AuthenticationMethod.PIN);
    }
    if (fallbackOptions.allowPassword) {
      methods.push(AuthenticationMethod.PASSWORD);
    }
    if (fallbackOptions.allowMFA) {
      methods.push(AuthenticationMethod.MFA);
    }
    
    return methods;
  }, [fallbackOptions]);

  // Handle biometric retry
  const handleBiometricRetry = useCallback(async () => {
    if (!canAuthenticate || isProcessing) return;

    try {
      setIsProcessing(true);
      setError(null);

      const result = await authenticate(userId, {
        title: 'Retry Biometric Authentication',
        subtitle: 'Try again using biometric authentication',
        description: 'Use your biometric authentication to sign in',
        negativeButtonText: 'Cancel',
        allowDeviceCredentials: true,
      });

      if (result.success) {
        onSuccess(AuthenticationMethod.FINGERPRINT); // Or appropriate biometric method
      } else {
        setError(result.errorMessage || 'Biometric authentication failed');
        setAttemptCount(prev => prev + 1);
      }
    } catch (error: any) {
      setError(error.message || 'Authentication failed');
      setAttemptCount(prev => prev + 1);
    } finally {
      setIsProcessing(false);
    }
  }, [canAuthenticate, isProcessing, authenticate, userId, onSuccess]);

  // Handle PIN authentication
  const handlePinAuth = useCallback(async () => {
    if (!pinInput || pinInput.length < 4 || isProcessing) return;

    try {
      setIsProcessing(true);
      setError(null);

      const result = await handleFallback(userId, AuthenticationMethod.PIN);
      
      if (result.success) {
        onSuccess(AuthenticationMethod.PIN);
      } else {
        setError(result.errorMessage || 'PIN authentication failed');
        setAttemptCount(prev => prev + 1);
        setPinInput('');
      }
    } catch (error: any) {
      setError(error.message || 'PIN authentication failed');
      setAttemptCount(prev => prev + 1);
      setPinInput('');
    } finally {
      setIsProcessing(false);
    }
  }, [pinInput, isProcessing, handleFallback, userId, onSuccess]);

  // Handle password authentication
  const handlePasswordAuth = useCallback(async () => {
    if (!passwordInput || isProcessing) return;

    try {
      setIsProcessing(true);
      setError(null);

      const result = await handleFallback(userId, AuthenticationMethod.PASSWORD);
      
      if (result.success) {
        onSuccess(AuthenticationMethod.PASSWORD);
      } else {
        setError(result.errorMessage || 'Password authentication failed');
        setAttemptCount(prev => prev + 1);
        setPasswordInput('');
      }
    } catch (error: any) {
      setError(error.message || 'Password authentication failed');
      setAttemptCount(prev => prev + 1);
      setPasswordInput('');
    } finally {
      setIsProcessing(false);
    }
  }, [passwordInput, isProcessing, handleFallback, userId, onSuccess]);

  // Handle MFA authentication
  const handleMfaAuth = useCallback(async () => {
    if (!mfaInput || mfaInput.length < 6 || isProcessing) return;

    try {
      setIsProcessing(true);
      setError(null);

      const result = await handleFallback(userId, AuthenticationMethod.MFA);
      
      if (result.success) {
        onSuccess(AuthenticationMethod.MFA);
      } else {
        setError(result.errorMessage || 'MFA authentication failed');
        setAttemptCount(prev => prev + 1);
        setMfaInput('');
      }
    } catch (error: any) {
      setError(error.message || 'MFA authentication failed');
      setAttemptCount(prev => prev + 1);
      setMfaInput('');
    } finally {
      setIsProcessing(false);
    }
  }, [mfaInput, isProcessing, handleFallback, userId, onSuccess]);

  // Handle method switch
  const handleMethodSwitch = useCallback((method: AuthenticationMethod) => {
    setCurrentMethod(method);
    setError(null);
    setPinInput('');
    setPasswordInput('');
    setMfaInput('');
  }, []);

  // Handle cancel
  const handleCancel = useCallback(() => {
    if (onError && error) {
      onError(error);
    }
    onCancel();
  }, [onCancel, onError, error]);

  // Check if max attempts reached
  const isMaxAttemptsReached = attemptCount >= fallbackOptions.maxFallbackAttempts;

  // Get method display name
  const getMethodDisplayName = (method: AuthenticationMethod): string => {
    switch (method) {
      case AuthenticationMethod.PIN:
        return 'PIN';
      case AuthenticationMethod.PASSWORD:
        return 'Password';
      case AuthenticationMethod.MFA:
        return 'Two-Factor Authentication';
      default:
        return 'Authentication';
    }
  };

  // Get method icon
  const getMethodIcon = (method: AuthenticationMethod): string => {
    switch (method) {
      case AuthenticationMethod.PIN:
        return '🔢';
      case AuthenticationMethod.PASSWORD:
        return '🔐';
      case AuthenticationMethod.MFA:
        return '🔑';
      default:
        return '🔒';
    }
  };

  // Render PIN input
  const renderPinInput = () => (
    <View style={styles.inputContainer}>
      <Text style={styles.inputLabel}>Enter your PIN</Text>
      <TextInput
        style={styles.pinInput}
        value={pinInput}
        onChangeText={setPinInput}
        keyboardType="numeric"
        secureTextEntry
        maxLength={6}
        placeholder="••••••"
        autoFocus
        editable={!isProcessing}
      />
      <TouchableOpacity
        style={[styles.submitButton, (!pinInput || pinInput.length < 4 || isProcessing) && styles.submitButtonDisabled]}
        onPress={handlePinAuth}
        disabled={!pinInput || pinInput.length < 4 || isProcessing}
      >
        <Text style={styles.submitButtonText}>
          {isProcessing ? 'Verifying...' : 'Verify PIN'}
        </Text>
      </TouchableOpacity>
    </View>
  );

  // Render password input
  const renderPasswordInput = () => (
    <View style={styles.inputContainer}>
      <Text style={styles.inputLabel}>Enter your password</Text>
      <View style={styles.passwordContainer}>
        <TextInput
          style={styles.passwordInput}
          value={passwordInput}
          onChangeText={setPasswordInput}
          secureTextEntry={!showPassword}
          placeholder="Password"
          autoFocus
          editable={!isProcessing}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity
          style={styles.passwordToggle}
          onPress={() => setShowPassword(!showPassword)}
        >
          <Text style={styles.passwordToggleText}>
            {showPassword ? '🙈' : '👁️'}
          </Text>
        </TouchableOpacity>
      </View>
      <TouchableOpacity
        style={[styles.submitButton, (!passwordInput || isProcessing) && styles.submitButtonDisabled]}
        onPress={handlePasswordAuth}
        disabled={!passwordInput || isProcessing}
      >
        <Text style={styles.submitButtonText}>
          {isProcessing ? 'Verifying...' : 'Verify Password'}
        </Text>
      </TouchableOpacity>
    </View>
  );

  // Render MFA input
  const renderMfaInput = () => (
    <View style={styles.inputContainer}>
      <Text style={styles.inputLabel}>Enter authentication code</Text>
      <Text style={styles.inputDescription}>
        Enter the 6-digit code from your authenticator app
      </Text>
      <TextInput
        style={styles.mfaInput}
        value={mfaInput}
        onChangeText={setMfaInput}
        keyboardType="numeric"
        maxLength={6}
        placeholder="000000"
        autoFocus
        editable={!isProcessing}
      />
      <TouchableOpacity
        style={[styles.submitButton, (!mfaInput || mfaInput.length < 6 || isProcessing) && styles.submitButtonDisabled]}
        onPress={handleMfaAuth}
        disabled={!mfaInput || mfaInput.length < 6 || isProcessing}
      >
        <Text style={styles.submitButtonText}>
          {isProcessing ? 'Verifying...' : 'Verify Code'}
        </Text>
      </TouchableOpacity>
    </View>
  );

  // Render method input based on current method
  const renderMethodInput = () => {
    switch (currentMethod) {
      case AuthenticationMethod.PIN:
        return renderPinInput();
      case AuthenticationMethod.PASSWORD:
        return renderPasswordInput();
      case AuthenticationMethod.MFA:
        return renderMfaInput();
      default:
        return null;
    }
  };

  const availableMethods = getAvailableMethods();

  if (!visible) {
    return null;
  }

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={handleCancel}
    >
      <KeyboardAvoidingView
        style={styles.overlay}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View style={styles.container}>
          <ScrollView showsVerticalScrollIndicator={false}>
            {/* Header */}
            <View style={styles.header}>
              <Text style={styles.title}>{title}</Text>
              <Text style={styles.reason}>{reason}</Text>
            </View>

            {/* Biometric Retry Option */}
            {allowBiometricRetry && canAuthenticate && !isMaxAttemptsReached && (
              <View style={styles.biometricRetryContainer}>
                <TouchableOpacity
                  style={styles.biometricRetryButton}
                  onPress={handleBiometricRetry}
                  disabled={isProcessing}
                >
                  <BiometricTypeIcon
                    biometryType={capabilities?.biometryType || null}
                    size="medium"
                    state={isProcessing ? 'processing' : 'idle'}
                    animated
                  />
                  <Text style={styles.biometricRetryText}>
                    Try Biometric Again
                  </Text>
                </TouchableOpacity>
              </View>
            )}

            {/* Method Selection */}
            {availableMethods.length > 1 && (
              <View style={styles.methodSelection}>
                <Text style={styles.methodSelectionTitle}>
                  Choose alternative method:
                </Text>
                <View style={styles.methodButtons}>
                  {availableMethods.map((method) => (
                    <TouchableOpacity
                      key={method}
                      style={[
                        styles.methodButton,
                        currentMethod === method && styles.methodButtonActive,
                      ]}
                      onPress={() => handleMethodSwitch(method)}
                      disabled={isProcessing}
                    >
                      <Text style={styles.methodButtonIcon}>
                        {getMethodIcon(method)}
                      </Text>
                      <Text
                        style={[
                          styles.methodButtonText,
                          currentMethod === method && styles.methodButtonTextActive,
                        ]}
                      >
                        {getMethodDisplayName(method)}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            )}

            {/* Current Method Input */}
            {renderMethodInput()}

            {/* Error Message */}
            {error && (
              <View style={styles.errorContainer}>
                <Text style={styles.errorText}>{error}</Text>
              </View>
            )}

            {/* Attempt Counter */}
            {attemptCount > 0 && (
              <Text style={styles.attemptText}>
                Attempts: {attemptCount}/{fallbackOptions.maxFallbackAttempts}
              </Text>
            )}

            {/* Max Attempts Warning */}
            {isMaxAttemptsReached && (
              <View style={styles.lockoutContainer}>
                <Text style={styles.lockoutText}>
                  Maximum attempts reached. Please wait {Math.ceil(fallbackOptions.lockoutDuration / 60000)} minutes before trying again.
                </Text>
              </View>
            )}

            {/* Action Buttons */}
            <View style={styles.actionButtons}>
              <TouchableOpacity
                style={styles.cancelButton}
                onPress={handleCancel}
                disabled={isProcessing}
              >
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
            </View>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  container: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 24,
    paddingTop: 24,
    paddingBottom: 34,
    maxHeight: '80%',
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: '#000000',
    marginBottom: 8,
  },
  reason: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
    lineHeight: 22,
  },
  biometricRetryContainer: {
    marginBottom: 24,
    alignItems: 'center',
  },
  biometricRetryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  biometricRetryText: {
    marginLeft: 8,
    fontSize: 16,
    fontWeight: '500',
    color: '#007AFF',
  },
  methodSelection: {
    marginBottom: 24,
  },
  methodSelectionTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#000000',
    marginBottom: 12,
  },
  methodButtons: {
    flexDirection: 'row',
    gap: 8,
  },
  methodButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 8,
  },
  methodButtonActive: {
    backgroundColor: '#007AFF',
  },
  methodButtonIcon: {
    fontSize: 20,
    marginBottom: 4,
  },
  methodButtonText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#666666',
    textAlign: 'center',
  },
  methodButtonTextActive: {
    color: '#FFFFFF',
  },
  inputContainer: {
    marginBottom: 24,
  },
  inputLabel: {
    fontSize: 16,
    fontWeight: '500',
    color: '#000000',
    marginBottom: 8,
  },
  inputDescription: {
    fontSize: 14,
    color: '#666666',
    marginBottom: 12,
  },
  pinInput: {
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    paddingVertical: 16,
    paddingHorizontal: 16,
    fontSize: 18,
    fontWeight: '600',
    textAlign: 'center',
    letterSpacing: 4,
    marginBottom: 16,
  },
  passwordContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    marginBottom: 16,
  },
  passwordInput: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 16,
    fontSize: 16,
  },
  passwordToggle: {
    paddingHorizontal: 16,
  },
  passwordToggleText: {
    fontSize: 16,
  },
  mfaInput: {
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    paddingVertical: 16,
    paddingHorizontal: 16,
    fontSize: 18,
    fontWeight: '600',
    textAlign: 'center',
    letterSpacing: 2,
    marginBottom: 16,
  },
  submitButton: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingVertical: 16,
    alignItems: 'center',
  },
  submitButtonDisabled: {
    backgroundColor: '#C7C7CC',
  },
  submitButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  errorContainer: {
    backgroundColor: '#FFEBEE',
    borderColor: '#FFCDD2',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  errorText: {
    color: '#C62828',
    fontSize: 14,
    textAlign: 'center',
  },
  attemptText: {
    fontSize: 12,
    color: '#999999',
    textAlign: 'center',
    marginBottom: 16,
  },
  lockoutContainer: {
    backgroundColor: '#FFF3E0',
    borderColor: '#FFCC02',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  lockoutText: {
    color: '#F57C00',
    fontSize: 14,
    textAlign: 'center',
    fontWeight: '500',
  },
  actionButtons: {
    alignItems: 'center',
  },
  cancelButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
  },
  cancelButtonText: {
    color: '#999999',
    fontSize: 16,
    fontWeight: '500',
  },
});

export default BiometricFallbackManager;