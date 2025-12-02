/**
 * BiometricLoginButton - Simple biometric authentication button
 * A lightweight component that can be easily added to existing login screens
 * Provides a simple interface for biometric authentication with minimal UI.
 */

import React, { useState, useCallback } from 'react';
import {
  TouchableOpacity,
  Text,
  StyleSheet,
  ActivityIndicator,
  ViewStyle,
  TextStyle,
} from 'react-native';
import { useBiometric } from '../../hooks/useBiometric';
import { AuthenticationMethod } from '../../services/biometric/types';

// Simple icon component for biometric types
const BiometricIcon = ({ type, size = 20, color = '#007AFF' }: {
  type: 'fingerprint' | 'face' | 'biometric';
  size?: number;
  color?: string;
}) => {
  const iconMap = {
    fingerprint: '👆',
    face: '👤',
    biometric: '🔒',
  };
  
  return (
    <Text style={{ fontSize: size, color, marginRight: 8 }}>
      {iconMap[type]}
    </Text>
  );
};

export interface BiometricLoginButtonProps {
  userId: string;
  onSuccess: () => void;
  onFallback?: () => void;
  onError?: (error: string) => void;
  style?: ViewStyle;
  textStyle?: TextStyle;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'minimal';
  showIcon?: boolean;
  customText?: string;
  fallbackMethod?: AuthenticationMethod;
}

export const BiometricLoginButton: React.FC<BiometricLoginButtonProps> = ({
  userId,
  onSuccess,
  onFallback,
  onError,
  style,
  textStyle,
  disabled = false,
  variant = 'primary',
  showIcon = true,
  customText,
  fallbackMethod = AuthenticationMethod.PIN,
}) => {
  const {
    canAuthenticate,
    capabilities,
    authenticateWithFallback,
    getErrorMessage,
    isErrorRecoverable,
  } = useBiometric();

  const [isProcessing, setIsProcessing] = useState(false);

  // Determine biometric icon type and display name
  const biometricIconType = capabilities?.biometryType === 'FaceID' ? 'face' : 
                            capabilities?.biometryType === 'TouchID' || capabilities?.biometryType === 'Biometrics' ? 'fingerprint' : 
                            'biometric';
  
  const biometricName = capabilities?.biometryType === 'FaceID' ? 'Face ID' :
                        capabilities?.biometryType === 'TouchID' ? 'Touch ID' :
                        capabilities?.biometryType === 'Biometrics' ? 'Fingerprint' :
                        'Biometric';

  const buttonText = customText || `Sign in with ${biometricName}`;

  // Handle biometric authentication
  const handleAuthentication = useCallback(async () => {
    if (!canAuthenticate || isProcessing || disabled) return;

    try {
      setIsProcessing(true);
      
      const success = await authenticateWithFallback(userId, fallbackMethod);
      
      if (success) {
        onSuccess();
      } else {
        const errorMessage = getErrorMessage() || 'Authentication failed';
        
        if (onError) {
          onError(errorMessage);
        }
        
        // If error is not recoverable and fallback is available, trigger it
        if (!isErrorRecoverable() && onFallback) {
          onFallback();
        }
      }
    } catch (error: any) {
      const errorMessage = error.message || 'Authentication failed';
      
      if (onError) {
        onError(errorMessage);
      }
      
      if (onFallback) {
        onFallback();
      }
    } finally {
      setIsProcessing(false);
    }
  }, [
    canAuthenticate,
    isProcessing,
    disabled,
    authenticateWithFallback,
    userId,
    fallbackMethod,
    onSuccess,
    getErrorMessage,
    onError,
    isErrorRecoverable,
    onFallback,
  ]);

  // Don't render if biometric authentication is not available
  if (!canAuthenticate) {
    return null;
  }

  const buttonStyle = [
    styles.button,
    styles[`${variant}Button`],
    isProcessing && styles.buttonProcessing,
    disabled && styles.buttonDisabled,
    style,
  ];

  const textStyleCombined = [
    styles.buttonText,
    styles[`${variant}ButtonText`],
    isProcessing && styles.buttonTextProcessing,
    disabled && styles.buttonTextDisabled,
    textStyle,
  ];

  return (
    <TouchableOpacity
      style={buttonStyle}
      onPress={handleAuthentication}
      disabled={isProcessing || disabled || !canAuthenticate}
      activeOpacity={0.8}
    >
      {isProcessing ? (
        <ActivityIndicator 
          size="small" 
          color={variant === 'primary' ? '#FFFFFF' : '#007AFF'} 
        />
      ) : (
        <>
          {showIcon && (
            <BiometricIcon 
              type={biometricIconType} 
              size={16} 
              color={variant === 'primary' ? '#FFFFFF' : '#007AFF'} 
            />
          )}
          <Text style={textStyleCombined}>
            {buttonText}
          </Text>
        </>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    minHeight: 44,
  },
  
  // Variant styles
  primaryButton: {
    backgroundColor: '#007AFF',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    elevation: 3,
  },
  secondaryButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  minimalButton: {
    backgroundColor: 'transparent',
    paddingVertical: 8,
  },
  
  // Processing states
  buttonProcessing: {
    opacity: 0.8,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  
  // Text styles
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  primaryButtonText: {
    color: '#FFFFFF',
  },
  secondaryButtonText: {
    color: '#007AFF',
  },
  minimalButtonText: {
    color: '#007AFF',
    textDecorationLine: 'underline',
  },
  buttonTextProcessing: {
    opacity: 0.8,
  },
  buttonTextDisabled: {
    opacity: 0.5,
  },
});

export default BiometricLoginButton;