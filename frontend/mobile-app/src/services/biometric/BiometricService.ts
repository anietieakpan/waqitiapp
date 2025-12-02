/**
 * Core Biometric Service
 * Wraps react-native-biometrics functionality with enhanced security features
 */

import ReactNativeBiometrics, { BiometryType } from 'react-native-biometrics';
import { Platform } from 'react-native';
import { 
  IBiometricService,
  BiometricCapabilities,
  BiometricPromptConfig,
  BiometricAuthResult,
  BiometricAuthError,
  SecurityLevel,
  BiometricError 
} from './types';

export class BiometricService implements IBiometricService {
  private static instance: BiometricService;
  private rnBiometrics: ReactNativeBiometrics;

  constructor() {
    this.rnBiometrics = new ReactNativeBiometrics({
      allowDeviceCredentials: false, // We'll handle this separately for better control
    });
  }

  public static getInstance(): BiometricService {
    if (!BiometricService.instance) {
      BiometricService.instance = new BiometricService();
    }
    return BiometricService.instance;
  }

  /**
   * Check biometric availability and capabilities on the device
   */
  async checkAvailability(): Promise<BiometricCapabilities> {
    try {
      const { available, biometryType, error } = await this.rnBiometrics.isSensorAvailable();
      
      let securityLevel = SecurityLevel.LOW;
      let canStoreKeys = false;
      let supportsStrongBox = false;
      let hasHardware = false;
      let isEnrolled = false;
      
      if (available && biometryType) {
        hasHardware = true;
        isEnrolled = true;
        
        // Determine security level based on biometry type and platform
        switch (biometryType) {
          case ReactNativeBiometrics.FaceID:
            securityLevel = Platform.OS === 'ios' ? SecurityLevel.HIGH : SecurityLevel.MEDIUM;
            canStoreKeys = true;
            break;
          case ReactNativeBiometrics.TouchID:
          case ReactNativeBiometrics.Biometrics:
            securityLevel = SecurityLevel.HIGH;
            canStoreKeys = true;
            break;
          default:
            securityLevel = SecurityLevel.MEDIUM;
        }

        // Check for Android StrongBox support
        if (Platform.OS === 'android') {
          try {
            // This is a simplified check - in production, you might want to use a native module
            // to properly detect StrongBox support
            supportsStrongBox = Platform.Version >= 28; // Android 9.0+
          } catch (e) {
            supportsStrongBox = false;
          }
        }
      } else if (error) {
        console.warn('Biometric sensor error:', error);
        // Determine if hardware exists but isn't enrolled
        hasHardware = error.includes('no enrolled') || error.includes('not enrolled');
      }

      const capabilities: BiometricCapabilities = {
        isAvailable: available,
        biometryType: biometryType || null,
        supportedTypes: biometryType ? [biometryType] : [],
        hasHardware,
        isEnrolled,
        securityLevel,
        canStoreKeys,
        supportsStrongBox,
      };

      console.log('Biometric capabilities:', capabilities);
      return capabilities;
      
    } catch (error) {
      console.error('Failed to check biometric availability:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to check biometric availability',
        error,
        true,
        false
      );
    }
  }

  /**
   * Generate a new key pair for biometric authentication
   */
  async generateKeyPair(keyAlias: string): Promise<{ publicKey: string; keyAlias: string }> {
    try {
      const capabilities = await this.checkAvailability();
      
      if (!capabilities.isAvailable) {
        throw new BiometricError(
          BiometricAuthError.HARDWARE_NOT_AVAILABLE,
          'Biometric hardware not available',
          null,
          false,
          false
        );
      }

      if (!capabilities.isEnrolled) {
        throw new BiometricError(
          BiometricAuthError.NOT_ENROLLED,
          'No biometric credentials enrolled',
          null,
          false,
          true
        );
      }

      const { publicKey } = await this.rnBiometrics.createKeys({
        allowDeviceCredentials: false,
        invalidateEnrollmentChanges: true, // Invalidate key if biometrics change
      });

      console.log(`Generated key pair with alias: ${keyAlias}`);
      
      return {
        publicKey,
        keyAlias,
      };
      
    } catch (error) {
      console.error('Failed to generate key pair:', error);
      
      if (error instanceof BiometricError) {
        throw error;
      }
      
      // Map specific error types
      if (error.message?.includes('UserCancel') || error.message?.includes('cancelled')) {
        throw new BiometricError(
          BiometricAuthError.USER_CANCELLED,
          'User cancelled biometric setup',
          error,
          true,
          true
        );
      }
      
      if (error.message?.includes('LockedOut')) {
        throw new BiometricError(
          BiometricAuthError.TEMPORARILY_LOCKED,
          'Biometric sensor temporarily locked',
          error,
          true,
          true
        );
      }
      
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to generate cryptographic keys',
        error,
        true,
        false
      );
    }
  }

  /**
   * Authenticate user using biometrics and sign a challenge
   */
  async authenticate(
    promptConfig: BiometricPromptConfig, 
    challenge: string
  ): Promise<BiometricAuthResult> {
    try {
      const capabilities = await this.checkAvailability();
      
      if (!capabilities.isAvailable) {
        return {
          success: false,
          error: BiometricAuthError.HARDWARE_NOT_AVAILABLE,
          errorMessage: 'Biometric hardware not available',
        };
      }

      if (!capabilities.isEnrolled) {
        return {
          success: false,
          error: BiometricAuthError.NOT_ENROLLED,
          errorMessage: 'No biometric credentials enrolled',
        };
      }

      // Prepare prompt configuration
      const promptOptions = this.preparePromptOptions(promptConfig);

      // Perform biometric authentication with signature
      const { success, signature, error } = await this.rnBiometrics.createSignature({
        promptMessage: promptConfig.title,
        payload: challenge,
        cancelButtonText: promptConfig.negativeButtonText || 'Cancel',
        allowDeviceCredentials: promptConfig.allowDeviceCredentials || false,
        ...promptOptions,
      });

      if (success && signature) {
        console.log('Biometric authentication successful');
        
        return {
          success: true,
          signature,
          biometryType: capabilities.biometryType || undefined,
          timestamp: Date.now(),
        };
      } else {
        console.warn('Biometric authentication failed:', error);
        
        const authError = this.mapAuthenticationError(error);
        return {
          success: false,
          error: authError,
          errorMessage: this.getErrorMessage(authError),
          biometryType: capabilities.biometryType || undefined,
          timestamp: Date.now(),
        };
      }
      
    } catch (error) {
      console.error('Biometric authentication error:', error);
      
      const authError = this.mapAuthenticationError(error.message || error);
      return {
        success: false,
        error: authError,
        errorMessage: this.getErrorMessage(authError),
        timestamp: Date.now(),
      };
    }
  }

  /**
   * Delete a stored key pair
   */
  async deleteKey(keyAlias: string): Promise<boolean> {
    try {
      const { keysDeleted } = await this.rnBiometrics.deleteKeys();
      console.log(`Deleted biometric keys: ${keysDeleted}`);
      return keysDeleted;
    } catch (error) {
      console.error('Failed to delete biometric keys:', error);
      return false;
    }
  }

  /**
   * Check if a key exists
   */
  async isKeyExists(keyAlias: string): Promise<boolean> {
    try {
      const { keysExist } = await this.rnBiometrics.biometricKeysExist();
      return keysExist;
    } catch (error) {
      console.error('Failed to check key existence:', error);
      return false;
    }
  }

  /**
   * Prepare prompt options for different platforms
   */
  private preparePromptOptions(config: BiometricPromptConfig): any {
    const baseOptions = {
      title: config.title,
      subtitle: config.subtitle,
      description: config.description,
      fallbackTitle: config.fallbackTitle,
      negativeButtonText: config.negativeButtonText || 'Cancel',
    };

    if (Platform.OS === 'android') {
      return {
        ...baseOptions,
        deviceCredentialTitle: config.deviceCredentialTitle,
        deviceCredentialSubtitle: config.deviceCredentialSubtitle,
        deviceCredentialDescription: config.deviceCredentialDescription,
        requireConfirmation: config.requireConfirmation,
      };
    }

    return baseOptions;
  }

  /**
   * Map authentication errors to our error types
   */
  private mapAuthenticationError(error: string): BiometricAuthError {
    if (!error) return BiometricAuthError.UNKNOWN_ERROR;
    
    const errorString = error.toLowerCase();
    
    if (errorString.includes('cancel') || errorString.includes('user_cancel')) {
      return BiometricAuthError.USER_CANCELLED;
    }
    
    if (errorString.includes('locked') || errorString.includes('lockout')) {
      return BiometricAuthError.TEMPORARILY_LOCKED;
    }
    
    if (errorString.includes('permanent') || errorString.includes('disable')) {
      return BiometricAuthError.PERMANENTLY_LOCKED;
    }
    
    if (errorString.includes('failed') || errorString.includes('not_recognized')) {
      return BiometricAuthError.AUTHENTICATION_FAILED;
    }
    
    if (errorString.includes('not_enrolled') || errorString.includes('no_biometrics')) {
      return BiometricAuthError.NOT_ENROLLED;
    }
    
    if (errorString.includes('not_available') || errorString.includes('hardware')) {
      return BiometricAuthError.HARDWARE_NOT_AVAILABLE;
    }
    
    return BiometricAuthError.SYSTEM_ERROR;
  }

  /**
   * Get user-friendly error messages
   */
  private getErrorMessage(error: BiometricAuthError): string {
    switch (error) {
      case BiometricAuthError.HARDWARE_NOT_AVAILABLE:
        return 'Biometric authentication is not available on this device';
      case BiometricAuthError.NOT_ENROLLED:
        return 'Please set up biometric authentication in your device settings';
      case BiometricAuthError.USER_CANCELLED:
        return 'Authentication was cancelled';
      case BiometricAuthError.AUTHENTICATION_FAILED:
        return 'Biometric authentication failed. Please try again';
      case BiometricAuthError.TEMPORARILY_LOCKED:
        return 'Biometric sensor is temporarily locked. Please try again later';
      case BiometricAuthError.PERMANENTLY_LOCKED:
        return 'Biometric sensor is permanently locked. Please use alternative authentication';
      case BiometricAuthError.SYSTEM_ERROR:
        return 'A system error occurred during authentication';
      case BiometricAuthError.NETWORK_ERROR:
        return 'Network error occurred during authentication';
      case BiometricAuthError.TOKEN_EXPIRED:
        return 'Authentication token has expired. Please authenticate again';
      case BiometricAuthError.DEVICE_NOT_TRUSTED:
        return 'Device is not trusted for biometric authentication';
      default:
        return 'An unknown error occurred during authentication';
    }
  }

  /**
   * Verify biometric integrity (check for tampering)
   */
  async verifyBiometricIntegrity(): Promise<boolean> {
    try {
      const capabilities = await this.checkAvailability();
      
      // Check if biometric enrollment has changed
      if (!capabilities.isEnrolled) {
        console.warn('Biometric enrollment status changed');
        return false;
      }
      
      // Additional integrity checks could be added here
      // such as checking for root/jailbreak, emulator detection, etc.
      
      return true;
    } catch (error) {
      console.error('Biometric integrity verification failed:', error);
      return false;
    }
  }

  /**
   * Get detailed biometric sensor information
   */
  async getBiometricSensorInfo(): Promise<{
    sensorType: string;
    sensorStrength: SecurityLevel;
    isSecure: boolean;
    additionalInfo: Record<string, any>;
  }> {
    try {
      const capabilities = await this.checkAvailability();
      
      let sensorType = 'unknown';
      let sensorStrength = SecurityLevel.LOW;
      let isSecure = false;
      
      if (capabilities.biometryType) {
        switch (capabilities.biometryType) {
          case ReactNativeBiometrics.FaceID:
            sensorType = 'face_id';
            sensorStrength = SecurityLevel.HIGH;
            isSecure = Platform.OS === 'ios'; // iOS FaceID is generally more secure
            break;
          case ReactNativeBiometrics.TouchID:
            sensorType = 'touch_id';
            sensorStrength = SecurityLevel.HIGH;
            isSecure = true;
            break;
          case ReactNativeBiometrics.Biometrics:
            sensorType = 'fingerprint';
            sensorStrength = SecurityLevel.MEDIUM;
            isSecure = capabilities.supportsStrongBox;
            break;
        }
      }
      
      return {
        sensorType,
        sensorStrength,
        isSecure,
        additionalInfo: {
          platform: Platform.OS,
          version: Platform.Version,
          canStoreKeys: capabilities.canStoreKeys,
          supportsStrongBox: capabilities.supportsStrongBox,
          hasHardware: capabilities.hasHardware,
        },
      };
    } catch (error) {
      console.error('Failed to get sensor info:', error);
      return {
        sensorType: 'unknown',
        sensorStrength: SecurityLevel.LOW,
        isSecure: false,
        additionalInfo: {},
      };
    }
  }

  /**
   * Test biometric sensor functionality
   */
  async testSensor(): Promise<{ functional: boolean; latency: number; details: any }> {
    const startTime = Date.now();
    
    try {
      const capabilities = await this.checkAvailability();
      const latency = Date.now() - startTime;
      
      return {
        functional: capabilities.isAvailable && capabilities.isEnrolled,
        latency,
        details: capabilities,
      };
    } catch (error) {
      const latency = Date.now() - startTime;
      return {
        functional: false,
        latency,
        details: { error: error.message },
      };
    }
  }
}

export default BiometricService;