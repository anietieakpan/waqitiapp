import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  Animated,
  Alert,
  Platform
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import LinearGradient from 'react-native-linear-gradient';
import { VoiceCommand } from '../../services/voice/VoicePaymentService';
import { AuthService } from '../../services/auth/AuthService';

interface VoiceConfirmationModalProps {
  visible: boolean;
  command: VoiceCommand | null;
  onConfirm: () => void;
  onCancel: () => void;
  onBiometricAuth?: () => Promise<boolean>;
}

const VoiceConfirmationModal: React.FC<VoiceConfirmationModalProps> = ({
  visible,
  command,
  onConfirm,
  onCancel,
  onBiometricAuth
}) => {
  const [slideAnim] = useState(new Animated.Value(0));
  const [scaleAnim] = useState(new Animated.Value(0.8));
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [authenticationStep, setAuthenticationStep] = useState<'confirm' | 'biometric' | 'processing'>('confirm');

  useEffect(() => {
    if (visible) {
      // Animate modal in
      Animated.parallel([
        Animated.timing(slideAnim, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.spring(scaleAnim, {
          toValue: 1,
          tension: 100,
          friction: 8,
          useNativeDriver: true,
        })
      ]).start();
    } else {
      // Reset animation values
      slideAnim.setValue(0);
      scaleAnim.setValue(0.8);
      setAuthenticationStep('confirm');
      setIsAuthenticating(false);
    }
  }, [visible]);

  const handleConfirm = async () => {
    if (!command) return;

    try {
      setIsAuthenticating(true);
      setAuthenticationStep('biometric');

      // Check if biometric auth is required
      const requiresBiometric = await shouldRequireBiometric(command);
      
      if (requiresBiometric && onBiometricAuth) {
        const authSuccess = await onBiometricAuth();
        if (!authSuccess) {
          setAuthenticationStep('confirm');
          setIsAuthenticating(false);
          return;
        }
      }

      setAuthenticationStep('processing');
      
      // Small delay to show processing state
      setTimeout(() => {
        onConfirm();
        setIsAuthenticating(false);
      }, 1000);

    } catch (error) {
      console.error('Authentication failed:', error);
      Alert.alert('Authentication Failed', 'Please try again.');
      setAuthenticationStep('confirm');
      setIsAuthenticating(false);
    }
  };

  const shouldRequireBiometric = async (cmd: VoiceCommand): Promise<boolean> => {
    // Always require biometric for send and request actions above $50
    if ((cmd.action === 'send' || cmd.action === 'request') && cmd.amount && cmd.amount > 50) {
      return true;
    }
    
    // Check user's voice settings preference
    try {
      const settings = await AuthService.getUserPreferences();
      return settings?.voiceSettings?.requireBiometric ?? true;
    } catch {
      return true; // Default to requiring biometric
    }
  };

  const formatCommand = (cmd: VoiceCommand): string => {
    switch (cmd.action) {
      case 'send':
        return `Send $${cmd.amount} to ${cmd.recipient?.name}`;
      case 'request':
        return `Request $${cmd.amount} from ${cmd.recipient?.name}`;
      case 'check_balance':
        return 'Check account balance';
      case 'check_transactions':
        return 'Show recent transactions';
      case 'split_bill':
        return `Split $${cmd.amount} - ${cmd.description}`;
      default:
        return cmd.rawText;
    }
  };

  const getCommandIcon = (action: string): string => {
    switch (action) {
      case 'send': return 'arrow-upward';
      case 'request': return 'arrow-downward';
      case 'check_balance': return 'account-balance';
      case 'check_transactions': return 'history';
      case 'split_bill': return 'group';
      default: return 'mic';
    }
  };

  const getSecurityLevel = (cmd: VoiceCommand): { level: 'low' | 'medium' | 'high', color: string, text: string } => {
    if (cmd.action === 'check_balance' || cmd.action === 'check_transactions') {
      return { level: 'low', color: '#4CAF50', text: 'Low Risk' };
    }
    
    if (cmd.amount && cmd.amount > 100) {
      return { level: 'high', color: '#F44336', text: 'High Value' };
    }
    
    if (cmd.amount && cmd.amount > 50) {
      return { level: 'medium', color: '#FF9800', text: 'Medium Value' };
    }
    
    return { level: 'low', color: '#4CAF50', text: 'Low Value' };
  };

  const renderConfirmationStep = () => {
    if (!command) return null;

    const securityLevel = getSecurityLevel(command);

    return (
      <View style={styles.modalContent}>
        <View style={styles.modalHeader}>
          <View style={styles.commandIconContainer}>
            <Icon 
              name={getCommandIcon(command.action)} 
              size={32} 
              color="#667eea" 
            />
          </View>
          <Text style={styles.modalTitle}>Confirm Voice Payment</Text>
          <Text style={styles.modalSubtitle}>Please review and confirm this transaction</Text>
        </View>

        <View style={styles.commandDetails}>
          <Text style={styles.commandText}>{formatCommand(command)}</Text>
          
          {command.description && (
            <Text style={styles.descriptionText}>{command.description}</Text>
          )}

          <View style={styles.securityBadge}>
            <View style={[styles.securityDot, { backgroundColor: securityLevel.color }]} />
            <Text style={[styles.securityText, { color: securityLevel.color }]}>
              {securityLevel.text}
            </Text>
          </View>
        </View>

        {command.confidence < 0.8 && (
          <View style={styles.confidenceWarning}>
            <Icon name="warning" size={16} color="#FF9800" />
            <Text style={styles.confidenceText}>
              Voice recognition confidence: {Math.round(command.confidence * 100)}%
            </Text>
          </View>
        )}

        <View style={styles.voiceRecognition}>
          <Icon name="format-quote" size={16} color="#999" />
          <Text style={styles.recognizedText}>"{command.rawText}"</Text>
        </View>
      </View>
    );
  };

  const renderBiometricStep = () => (
    <View style={styles.modalContent}>
      <View style={styles.biometricContainer}>
        <Animated.View 
          style={[
            styles.biometricIcon,
            {
              transform: [{
                scale: scaleAnim.interpolate({
                  inputRange: [0, 1],
                  outputRange: [0.8, 1.2],
                })
              }]
            }
          ]}
        >
          <Icon name="fingerprint" size={64} color="#667eea" />
        </Animated.View>
        <Text style={styles.biometricTitle}>Biometric Authentication</Text>
        <Text style={styles.biometricSubtitle}>
          {Platform.OS === 'ios' 
            ? 'Use Touch ID or Face ID to confirm' 
            : 'Use fingerprint or face unlock to confirm'
          }
        </Text>
      </View>
    </View>
  );

  const renderProcessingStep = () => (
    <View style={styles.modalContent}>
      <View style={styles.processingContainer}>
        <Animated.View 
          style={[
            styles.processingIcon,
            {
              transform: [{
                rotate: slideAnim.interpolate({
                  inputRange: [0, 1],
                  outputRange: ['0deg', '360deg'],
                })
              }]
            }
          ]}
        >
          <Icon name="hourglass-empty" size={48} color="#667eea" />
        </Animated.View>
        <Text style={styles.processingTitle}>Processing Payment</Text>
        <Text style={styles.processingSubtitle}>Please wait while we process your request...</Text>
      </View>
    </View>
  );

  const renderModalContent = () => {
    switch (authenticationStep) {
      case 'biometric':
        return renderBiometricStep();
      case 'processing':
        return renderProcessingStep();
      default:
        return renderConfirmationStep();
    }
  };

  const renderActionButtons = () => {
    if (authenticationStep === 'processing') {
      return null;
    }

    if (authenticationStep === 'biometric') {
      return (
        <View style={styles.actionButtons}>
          <TouchableOpacity
            style={[styles.actionButton, styles.cancelButton]}
            onPress={onCancel}
          >
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return (
      <View style={styles.actionButtons}>
        <TouchableOpacity
          style={[styles.actionButton, styles.cancelButton]}
          onPress={onCancel}
          disabled={isAuthenticating}
        >
          <Text style={styles.cancelButtonText}>Cancel</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.actionButton, styles.confirmButton]}
          onPress={handleConfirm}
          disabled={isAuthenticating}
        >
          <LinearGradient
            colors={['#667eea', '#764ba2']}
            style={styles.confirmButtonGradient}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
          >
            {isAuthenticating ? (
              <View style={styles.loadingContainer}>
                <Icon name="hourglass-empty" size={20} color="#FFFFFF" />
                <Text style={styles.confirmButtonText}>Authenticating...</Text>
              </View>
            ) : (
              <>
                <Icon name="check" size={20} color="#FFFFFF" />
                <Text style={styles.confirmButtonText}>Confirm</Text>
              </>
            )}
          </LinearGradient>
        </TouchableOpacity>
      </View>
    );
  };

  if (!visible || !command) {
    return null;
  }

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onCancel}
    >
      <View style={styles.overlay}>
        <Animated.View
          style={[
            styles.modal,
            {
              opacity: slideAnim,
              transform: [{ scale: scaleAnim }],
            },
          ]}
        >
          {renderModalContent()}
          {renderActionButtons()}
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modal: {
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    width: '100%',
    maxWidth: 400,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.3,
    shadowRadius: 20,
    elevation: 20,
  },
  modalContent: {
    padding: 24,
  },
  modalHeader: {
    alignItems: 'center',
    marginBottom: 24,
  },
  commandIconContainer: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#F0F4FF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  modalSubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
  commandDetails: {
    backgroundColor: '#F8F9FA',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  commandText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
    textAlign: 'center',
  },
  descriptionText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 12,
  },
  securityBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  securityDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  securityText: {
    fontSize: 12,
    fontWeight: '500',
  },
  confidenceWarning: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF8E1',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  confidenceText: {
    fontSize: 13,
    color: '#E65100',
    marginLeft: 8,
    flex: 1,
  },
  voiceRecognition: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  recognizedText: {
    fontSize: 13,
    color: '#666',
    fontStyle: 'italic',
    marginLeft: 8,
    flex: 1,
  },
  biometricContainer: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  biometricIcon: {
    marginBottom: 20,
  },
  biometricTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  biometricSubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    lineHeight: 20,
  },
  processingContainer: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  processingIcon: {
    marginBottom: 20,
  },
  processingTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  processingSubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
  actionButtons: {
    flexDirection: 'row',
    padding: 20,
    paddingTop: 0,
  },
  actionButton: {
    flex: 1,
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 8,
  },
  cancelButton: {
    backgroundColor: '#F5F5F5',
    borderWidth: 1,
    borderColor: '#E0E0E0',
  },
  cancelButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#666',
  },
  confirmButton: {
    overflow: 'hidden',
  },
  confirmButtonGradient: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  confirmButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
    marginLeft: 8,
  },
  loadingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
});

export default VoiceConfirmationModal;