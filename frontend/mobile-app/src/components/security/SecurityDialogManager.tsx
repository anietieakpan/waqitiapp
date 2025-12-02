/**
 * SecurityDialogManager - Central manager for all security-related dialogs
 * Handles PIN entry, MFA codes, biometric prompts, and security confirmations
 */

import React, { useState, useCallback } from 'react';
import { Platform } from 'react-native';
import PinEntryDialog from './PinEntryDialog';
import MFACodeDialog, { MFAMethod } from './MFACodeDialog';
import BiometricService from '../../services/BiometricService';
import { showToast } from '../../utils/toast';

export interface SecurityDialogManager {
  promptForPin: (options?: PinPromptOptions) => Promise<string>;
  promptForMFACode: (options: MFAPromptOptions) => Promise<string>;
  promptForBiometric: (options?: BiometricPromptOptions) => Promise<boolean>;
  showSecurityConfirmation: (options: SecurityConfirmationOptions) => Promise<boolean>;
}

interface PinPromptOptions {
  title?: string;
  subtitle?: string;
  allowBiometric?: boolean;
  maxLength?: number;
}

interface MFAPromptOptions {
  method: MFAMethod;
  maskedContact?: string;
  onResendCode?: () => Promise<boolean>;
  codeLength?: number;
}

interface BiometricPromptOptions {
  title?: string;
  subtitle?: string;
  description?: string;
  fallbackEnabled?: boolean;
}

interface SecurityConfirmationOptions {
  title: string;
  message: string;
  requirePin?: boolean;
  requireBiometric?: boolean;
  allowBiometric?: boolean;
}

interface SecurityDialogState {
  pinDialog: {
    visible: boolean;
    options: PinPromptOptions;
    resolve?: (pin: string) => void;
    reject?: (error: Error) => void;
  };
  mfaDialog: {
    visible: boolean;
    options: MFAPromptOptions;
    resolve?: (code: string) => void;
    reject?: (error: Error) => void;
  };
}

const SecurityDialogManagerComponent: React.FC<{
  onRegisterManager: (manager: SecurityDialogManager) => void;
}> = ({ onRegisterManager }) => {
  const [state, setState] = useState<SecurityDialogState>({
    pinDialog: {
      visible: false,
      options: {},
    },
    mfaDialog: {
      visible: false,
      options: { method: 'TOTP' },
    },
  });

  const promptForPin = useCallback(async (options: PinPromptOptions = {}): Promise<string> => {
    return new Promise((resolve, reject) => {
      setState(prev => ({
        ...prev,
        pinDialog: {
          visible: true,
          options: {
            title: 'Enter PIN',
            subtitle: 'Enter your PIN to continue',
            allowBiometric: true,
            maxLength: 6,
            ...options,
          },
          resolve,
          reject,
        },
      }));
    });
  }, []);

  const promptForMFACode = useCallback(async (options: MFAPromptOptions): Promise<string> => {
    return new Promise((resolve, reject) => {
      setState(prev => ({
        ...prev,
        mfaDialog: {
          visible: true,
          options: {
            codeLength: 6,
            ...options,
          },
          resolve,
          reject,
        },
      }));
    });
  }, []);

  const promptForBiometric = useCallback(async (options: BiometricPromptOptions = {}): Promise<boolean> => {
    try {
      const isAvailable = await BiometricService.isBiometricAvailable();
      if (!isAvailable) {
        throw new Error('Biometric authentication is not available on this device');
      }

      const biometricOptions = {
        promptMessage: options.title || 'Authenticate',
        cancelButtonText: 'Cancel',
        fallbackPromptMessage: options.subtitle || 'Use PIN instead',
        ...options,
      };

      const result = await BiometricService.authenticateWithBiometric(biometricOptions);
      return result.success;
    } catch (error: any) {
      console.error('Biometric authentication error:', error);
      throw new Error(error.message || 'Biometric authentication failed');
    }
  }, []);

  const showSecurityConfirmation = useCallback(async (options: SecurityConfirmationOptions): Promise<boolean> => {
    try {
      // If biometric is required or allowed, try biometric first
      if (options.requireBiometric || (options.allowBiometric && !options.requirePin)) {
        try {
          const biometricResult = await promptForBiometric({
            title: options.title,
            subtitle: options.message,
          });
          if (biometricResult) {
            return true;
          }
          if (options.requireBiometric) {
            return false;
          }
        } catch (error) {
          if (options.requireBiometric) {
            throw error;
          }
          // Continue to PIN if biometric fails and PIN is allowed
        }
      }

      // If PIN is required or biometric failed, prompt for PIN
      if (options.requirePin || (!options.requireBiometric && !options.allowBiometric)) {
        try {
          const pin = await promptForPin({
            title: options.title,
            subtitle: options.message,
            allowBiometric: options.allowBiometric && !options.requirePin,
          });
          return !!pin;
        } catch (error) {
          return false;
        }
      }

      return true;
    } catch (error) {
      console.error('Security confirmation error:', error);
      return false;
    }
  }, [promptForPin, promptForBiometric]);

  // Register manager with parent
  React.useEffect(() => {
    const manager: SecurityDialogManager = {
      promptForPin,
      promptForMFACode,
      promptForBiometric,
      showSecurityConfirmation,
    };
    onRegisterManager(manager);
  }, [promptForPin, promptForMFACode, promptForBiometric, showSecurityConfirmation, onRegisterManager]);

  const handlePinSubmit = async (pin: string): Promise<boolean> => {
    // This should integrate with your PIN verification service
    try {
      // For now, simulate PIN verification
      // In production, this should call your backend PIN verification service
      if (pin.length === state.pinDialog.options.maxLength) {
        if (state.pinDialog.resolve) {
          state.pinDialog.resolve(pin);
          setState(prev => ({
            ...prev,
            pinDialog: { ...prev.pinDialog, visible: false, resolve: undefined, reject: undefined },
          }));
          return true;
        }
      }
      return false;
    } catch (error) {
      console.error('PIN verification error:', error);
      return false;
    }
  };

  const handleMFASubmit = async (code: string): Promise<boolean> => {
    try {
      // This should integrate with your MFA verification service
      // For now, simulate code verification
      if (code.length === state.mfaDialog.options.codeLength) {
        if (state.mfaDialog.resolve) {
          state.mfaDialog.resolve(code);
          setState(prev => ({
            ...prev,
            mfaDialog: { ...prev.mfaDialog, visible: false, resolve: undefined, reject: undefined },
          }));
          return true;
        }
      }
      return false;
    } catch (error) {
      console.error('MFA code verification error:', error);
      return false;
    }
  };

  const handlePinCancel = () => {
    if (state.pinDialog.reject) {
      state.pinDialog.reject(new Error('PIN entry cancelled by user'));
    }
    setState(prev => ({
      ...prev,
      pinDialog: { ...prev.pinDialog, visible: false, resolve: undefined, reject: undefined },
    }));
  };

  const handleMFACancel = () => {
    if (state.mfaDialog.reject) {
      state.mfaDialog.reject(new Error('MFA code entry cancelled by user'));
    }
    setState(prev => ({
      ...prev,
      mfaDialog: { ...prev.mfaDialog, visible: false, resolve: undefined, reject: undefined },
    }));
  };

  const handlePinBiometric = async () => {
    try {
      const success = await promptForBiometric({
        title: 'Biometric Authentication',
        subtitle: 'Use biometric authentication instead of PIN',
      });
      
      if (success && state.pinDialog.resolve) {
        state.pinDialog.resolve('BIOMETRIC_SUCCESS');
        setState(prev => ({
          ...prev,
          pinDialog: { ...prev.pinDialog, visible: false, resolve: undefined, reject: undefined },
        }));
      }
    } catch (error) {
      showToast('Biometric authentication failed', 'error');
    }
  };

  return (
    <>
      <PinEntryDialog
        visible={state.pinDialog.visible}
        title={state.pinDialog.options.title || 'Enter PIN'}
        subtitle={state.pinDialog.options.subtitle}
        onPinEntered={handlePinSubmit}
        onCancel={handlePinCancel}
        allowBiometric={state.pinDialog.options.allowBiometric}
        onBiometricPressed={handlePinBiometric}
        maxLength={state.pinDialog.options.maxLength || 6}
      />

      <MFACodeDialog
        visible={state.mfaDialog.visible}
        method={state.mfaDialog.options.method}
        maskedContact={state.mfaDialog.options.maskedContact}
        onCodeSubmit={handleMFASubmit}
        onCancel={handleMFACancel}
        onResendCode={state.mfaDialog.options.onResendCode}
        codeLength={state.mfaDialog.options.codeLength || 6}
      />
    </>
  );
};

let securityDialogManager: SecurityDialogManager | null = null;

export const SecurityDialogProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const handleRegisterManager = useCallback((manager: SecurityDialogManager) => {
    securityDialogManager = manager;
  }, []);

  return (
    <>
      {children}
      <SecurityDialogManagerComponent onRegisterManager={handleRegisterManager} />
    </>
  );
};

// Export singleton access to the manager
export const getSecurityDialogManager = (): SecurityDialogManager => {
  if (!securityDialogManager) {
    throw new Error('SecurityDialogManager not initialized. Make sure SecurityDialogProvider is rendered.');
  }
  return securityDialogManager;
};

// Convenience functions for common use cases
export const promptForPin = (options?: PinPromptOptions): Promise<string> => {
  return getSecurityDialogManager().promptForPin(options);
};

export const promptForMFACode = (options: MFAPromptOptions): Promise<string> => {
  return getSecurityDialogManager().promptForMFACode(options);
};

export const promptForBiometric = (options?: BiometricPromptOptions): Promise<boolean> => {
  return getSecurityDialogManager().promptForBiometric(options);
};

export const showSecurityConfirmation = (options: SecurityConfirmationOptions): Promise<boolean> => {
  return getSecurityDialogManager().showSecurityConfirmation(options);
};

export default SecurityDialogManagerComponent;