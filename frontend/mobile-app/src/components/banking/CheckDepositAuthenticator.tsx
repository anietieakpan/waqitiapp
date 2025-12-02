/**
 * CheckDepositAuthenticator - Biometric authentication component for check deposits
 * Integrates with existing biometric authentication for secure check deposits
 */

import React, { useState, useCallback, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Alert,
  TouchableOpacity,
} from 'react-native';
import DeviceInfo from 'react-native-device-info';
import {
  Surface,
  useTheme,
  Button,
  Portal,
  Modal,
  ActivityIndicator,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useSelector } from 'react-redux';

import { RootState } from '../../store/store';
import { useBiometric } from '../../hooks/useBiometric';
import { useSecurity } from '../../contexts/SecurityContext';
import BiometricLoginComponent from '../auth/BiometricLoginComponent';
import { formatCurrency } from '../../utils/formatters';
import CheckFraudDetectionService from '../../services/banking/CheckFraudDetectionService';

interface CheckDepositAuthenticatorProps {
  amount: number;
  visible: boolean;
  onSuccess: () => void;
  onCancel: () => void;
  onError?: (error: string) => void;
  requiresAuth?: boolean;
  checkImages?: {
    frontPath: string;
    backPath: string;
  };
}

/**
 * Check Deposit Authenticator Component
 */
const CheckDepositAuthenticator: React.FC<CheckDepositAuthenticatorProps> = ({
  amount,
  visible,
  onSuccess,
  onCancel,
  onError,
  requiresAuth = true,
  checkImages,
}) => {
  const theme = useTheme();
  const { 
    isAvailable, 
    isSetup, 
    canAuthenticate,
    capabilities,
  } = useBiometric();
  
  // Security context
  const { verifyPin } = useSecurity();
  
  // State
  const [showPinEntry, setShowPinEntry] = useState(false);
  const [pinCode, setPinCode] = useState('');
  const [pinError, setPinError] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [fraudCheckResult, setFraudCheckResult] = useState<any>(null);
  const [isCheckingFraud, setIsCheckingFraud] = useState(false);
  
  // Redux state
  const user = useSelector((state: RootState) => state.auth.user);
  
  // Determine if authentication is required based on amount
  const authRequired = useCallback(() => {
    if (!requiresAuth) return false;
    
    // Require authentication for deposits over $100
    return amount > 100;
  }, [amount, requiresAuth]);
  
  // Get authentication threshold message
  const getAuthMessage = useCallback(() => {
    if (amount <= 100) {
      return 'Verification required for your security.';
    } else if (amount <= 1000) {
      return 'Authentication required for deposits over $100.';
    } else {
      return 'Enhanced security verification required for large deposits.';
    }
  }, [amount]);
  
  // Perform fraud check when component mounts
  useEffect(() => {
    if (visible && checkImages && !fraudCheckResult) {
      performFraudCheck();
    }
  }, [visible, checkImages]);

  // Perform fraud detection
  const performFraudCheck = useCallback(async () => {
    if (!checkImages) return;
    
    setIsCheckingFraud(true);
    
    try {
      const result = await CheckFraudDetectionService.detectFraud({
        frontImagePath: checkImages.frontPath,
        backImagePath: checkImages.backPath,
        amount,
        timestamp: Date.now(),
        deviceId: await DeviceInfo.getUniqueId(),
      });
      
      setFraudCheckResult(result);
      
      // If high risk, prevent authentication
      if (result.riskScore > 80) {
        onError?.('This check cannot be deposited due to security concerns');
        onCancel();
      }
    } catch (error) {
      console.error('Fraud check failed:', error);
      setFraudCheckResult({ isValid: false, riskScore: 100 });
    } finally {
      setIsCheckingFraud(false);
    }
  }, [checkImages, amount, onError, onCancel]);

  // Handle biometric authentication success
  const handleBiometricSuccess = useCallback(async (authResult: any) => {
    console.log('Biometric authentication successful:', authResult);
    
    // If fraud check hasn't completed, wait for it
    if (isCheckingFraud) {
      Alert.alert('Please wait', 'Security verification in progress...');
      return;
    }
    
    // Check fraud detection results
    if (fraudCheckResult && fraudCheckResult.riskScore > 50) {
      Alert.alert(
        'Additional Verification Required',
        'Your deposit requires additional review and will be processed within 24 hours.',
        [{ text: 'OK', onPress: onSuccess }]
      );
    } else {
      onSuccess();
    }
  }, [onSuccess, isCheckingFraud, fraudCheckResult]);
  
  // Handle biometric authentication fallback (PIN/Password)
  const handleBiometricFallback = useCallback(() => {
    console.log('Biometric fallback requested');
    setShowPinEntry(true);
  }, []);
  
  // Handle biometric authentication error
  const handleBiometricError = useCallback((error: string) => {
    console.log('Biometric authentication error:', error);
    onError?.(error);
  }, [onError]);
  
  // Handle PIN submission
  const handlePinSubmit = useCallback(async () => {
    if (pinCode.length < 4) {
      setPinError('PIN must be at least 4 digits');
      return;
    }
    
    setIsAuthenticating(true);
    setPinError('');
    
    try {
      // Validate PIN with security context
      const isValidPin = await verifyPin(pinCode);
      
      if (isValidPin) {
        onSuccess();
      } else {
        setPinError('Invalid PIN. Please try again.');
      }
    } catch (error) {
      console.error('PIN validation error:', error);
      setPinError('Authentication failed. Please try again.');
    } finally {
      setIsAuthenticating(false);
    }
  }, [pinCode, user?.pin, onSuccess]);
  
  // Handle PIN digit press
  const handlePinDigit = useCallback((digit: string) => {
    if (pinCode.length < 6) {
      setPinCode(prev => prev + digit);
      setPinError('');
    }
  }, [pinCode]);
  
  // Handle PIN backspace
  const handlePinBackspace = useCallback(() => {
    setPinCode(prev => prev.slice(0, -1));
    setPinError('');
  }, []);
  
  // Handle PIN clear
  const handlePinClear = useCallback(() => {
    setPinCode('');
    setPinError('');
  }, []);
  
  // Handle skip authentication (for small amounts)
  const handleSkipAuth = useCallback(() => {
    if (!authRequired()) {
      onSuccess();
    }
  }, [authRequired, onSuccess]);
  
  // Close modal and reset state
  const handleClose = useCallback(() => {
    setPinCode('');
    setPinError('');
    setShowPinEntry(false);
    onCancel();
  }, [onCancel]);
  
  // Don't show if authentication is not required
  if (!visible || !authRequired()) {
    return null;
  }
  
  return (
    <Portal>
      <Modal
        visible={visible}
        onDismiss={handleClose}
        contentContainerStyle={styles.modal}
      >
        <View style={styles.modalContent}>
          {/* Header */}
          <View style={styles.header}>
            <Icon name="shield-check" size={48} color={theme.colors.primary} />
            <Text style={styles.title}>Secure Your Deposit</Text>
            <Text style={styles.subtitle}>
              {getAuthMessage()}
            </Text>
          </View>
          
          {/* Amount Display */}
          <Surface style={styles.amountContainer}>
            <Text style={styles.amountLabel}>Deposit Amount</Text>
            <Text style={styles.amountValue}>
              {formatCurrency(amount)}
            </Text>
          </Surface>
          
          {/* Authentication Methods */}
          {!showPinEntry ? (
            <View style={styles.authContainer}>
              {/* Biometric Authentication */}
              {isAvailable && isSetup && canAuthenticate && (
                <BiometricLoginComponent
                  userId={user?.id}
                  onSuccess={handleBiometricSuccess}
                  onFallback={handleBiometricFallback}
                  onError={handleBiometricError}
                  mode="unlock"
                  customPromptTitle={`Authenticate deposit of ${formatCurrency(amount)}`}
                  style={styles.biometricAuth}
                />
              )}
              
              {/* Fallback to PIN if biometrics not available */}
              {(!isAvailable || !isSetup || !canAuthenticate) && (
                <View style={styles.fallbackContainer}>
                  <Text style={styles.fallbackMessage}>
                    Biometric authentication is not available. Please use your PIN.
                  </Text>
                  <Button
                    mode="contained"
                    onPress={() => setShowPinEntry(true)}
                    style={styles.pinButton}
                  >
                    Enter PIN
                  </Button>
                </View>
              )}
            </View>
          ) : (
            /* PIN Entry */
            <View style={styles.pinContainer}>
              <Text style={styles.pinTitle}>Enter Your PIN</Text>
              
              {/* PIN Display */}
              <View style={styles.pinDisplay}>
                {Array.from({ length: 6 }, (_, index) => (
                  <View
                    key={index}
                    style={[
                      styles.pinDot,
                      index < pinCode.length && styles.pinDotFilled,
                    ]}
                  />
                ))}
              </View>
              
              {/* PIN Error */}
              {pinError && (
                <Text style={styles.pinErrorText}>{pinError}</Text>
              )}
              
              {/* PIN Keypad */}
              <View style={styles.keypad}>
                {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((digit) => (
                  <TouchableOpacity
                    key={digit}
                    style={styles.keypadButton}
                    onPress={() => handlePinDigit(digit.toString())}
                    disabled={isAuthenticating}
                  >
                    <Text style={styles.keypadButtonText}>{digit}</Text>
                  </TouchableOpacity>
                ))}
                
                <TouchableOpacity
                  style={styles.keypadButton}
                  onPress={handlePinClear}
                  disabled={isAuthenticating}
                >
                  <Text style={styles.keypadButtonText}>Clear</Text>
                </TouchableOpacity>
                
                <TouchableOpacity
                  style={styles.keypadButton}
                  onPress={() => handlePinDigit('0')}
                  disabled={isAuthenticating}
                >
                  <Text style={styles.keypadButtonText}>0</Text>
                </TouchableOpacity>
                
                <TouchableOpacity
                  style={styles.keypadButton}
                  onPress={handlePinBackspace}
                  disabled={isAuthenticating}
                >
                  <Icon name="backspace" size={24} color="#333" />
                </TouchableOpacity>
              </View>
              
              {/* PIN Submit */}
              <Button
                mode="contained"
                onPress={handlePinSubmit}
                disabled={pinCode.length < 4 || isAuthenticating}
                loading={isAuthenticating}
                style={styles.submitButton}
              >
                {isAuthenticating ? 'Verifying...' : 'Confirm'}
              </Button>
              
              {/* Back to Biometric */}
              {isAvailable && isSetup && canAuthenticate && (
                <Button
                  mode="text"
                  onPress={() => setShowPinEntry(false)}
                  style={styles.backButton}
                  disabled={isAuthenticating}
                >
                  Use Biometric Authentication
                </Button>
              )}
            </View>
          )}
          
          {/* Security Notice */}
          <View style={styles.securityNotice}>
            <Icon name="information" size={16} color={theme.colors.primary} />
            <Text style={styles.securityText}>
              Your authentication is encrypted and securely processed.
            </Text>
          </View>
          
          {/* Cancel Button */}
          <Button
            mode="outlined"
            onPress={handleClose}
            style={styles.cancelButton}
            disabled={isAuthenticating}
          >
            Cancel
          </Button>
        </View>
      </Modal>
    </Portal>
  );
};

const styles = StyleSheet.create({
  modal: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 16,
    maxHeight: '90%',
  },
  modalContent: {
    padding: 24,
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginTop: 16,
    marginBottom: 8,
    textAlign: 'center',
    color: '#333',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
  },
  amountContainer: {
    padding: 20,
    borderRadius: 12,
    marginBottom: 24,
    alignItems: 'center',
    elevation: 1,
  },
  amountLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8,
  },
  amountValue: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#333',
  },
  authContainer: {
    marginBottom: 24,
  },
  biometricAuth: {
    // Custom styles for biometric component if needed
  },
  fallbackContainer: {
    alignItems: 'center',
    padding: 20,
  },
  fallbackMessage: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 24,
  },
  pinButton: {
    borderRadius: 8,
  },
  pinContainer: {
    alignItems: 'center',
    marginBottom: 24,
  },
  pinTitle: {
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 24,
    color: '#333',
  },
  pinDisplay: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 16,
    gap: 12,
  },
  pinDot: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: '#E0E0E0',
  },
  pinDotFilled: {
    backgroundColor: '#333',
  },
  pinErrorText: {
    color: '#F44336',
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 16,
  },
  keypad: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    marginBottom: 24,
    gap: 8,
  },
  keypadButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#F5F5F5',
    justifyContent: 'center',
    alignItems: 'center',
    margin: 4,
  },
  keypadButtonText: {
    fontSize: 24,
    fontWeight: '500',
    color: '#333',
  },
  submitButton: {
    borderRadius: 8,
    marginBottom: 12,
    minWidth: 200,
  },
  backButton: {
    borderRadius: 8,
  },
  securityNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#F0F8FF',
    borderRadius: 8,
    marginBottom: 16,
  },
  securityText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 8,
    flex: 1,
    lineHeight: 16,
  },
  cancelButton: {
    borderRadius: 8,
  },
});

export default CheckDepositAuthenticator;