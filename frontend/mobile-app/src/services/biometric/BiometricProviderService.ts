import ReactNativeBiometrics from 'react-native-biometrics';
import TouchID from 'react-native-touch-id';
import FingerprintScanner from 'react-native-fingerprint-scanner';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import DeviceInfo from 'react-native-device-info';
import { ApiService } from '../ApiService';

/**
 * Enhanced biometric provider service with support for multiple biometric libraries
 * Provides a unified interface for biometric authentication across different providers
 */

export interface BiometricProvider {
  name: string;
  isAvailable(): Promise<boolean>;
  authenticate(options: AuthenticationOptions): Promise<AuthenticationResult>;
  getSupportedBiometryType(): Promise<BiometryType | null>;
  setup(userId: string): Promise<boolean>;
  cleanup(): void;
}

export interface AuthenticationOptions {
  promptMessage: string;
  fallbackLabel?: string;
  cancelLabel?: string;
  requireConfirmation?: boolean;
  disableDeviceCredential?: boolean;
}

export interface AuthenticationResult {
  success: boolean;
  error?: string;
  biometryType?: BiometryType;
  signature?: string;
}

export type BiometryType = 
  | 'TouchID' 
  | 'FaceID' 
  | 'Biometrics' 
  | 'Fingerprint' 
  | 'Face' 
  | 'Iris'
  | 'Voice'
  | 'Unknown';

/**
 * ReactNativeBiometrics Provider (Primary)
 */
class ReactNativeBiometricsProvider implements BiometricProvider {
  name = 'ReactNativeBiometrics';
  private rnBiometrics = new ReactNativeBiometrics();

  async isAvailable(): Promise<boolean> {
    try {
      const { available } = await this.rnBiometrics.isSensorAvailable();
      return available;
    } catch (error) {
      console.error('ReactNativeBiometrics availability check failed:', error);
      return false;
    }
  }

  async authenticate(options: AuthenticationOptions): Promise<AuthenticationResult> {
    try {
      const result = await this.rnBiometrics.simplePrompt({
        promptMessage: options.promptMessage,
        fallbackPromptMessage: options.fallbackLabel,
        cancelButtonText: options.cancelLabel || 'Cancel',
      });

      if (result.success) {
        // Generate cryptographic signature for enhanced security
        const signature = await this.generateSignature();
        
        return {
          success: true,
          biometryType: await this.getSupportedBiometryType(),
          signature,
        };
      }

      return {
        success: false,
        error: 'Authentication cancelled',
      };
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Authentication failed',
      };
    }
  }

  async getSupportedBiometryType(): Promise<BiometryType | null> {
    try {
      const { available, biometryType } = await this.rnBiometrics.isSensorAvailable();
      if (!available) return null;
      
      switch (biometryType) {
        case 'TouchID':
          return 'TouchID';
        case 'FaceID':
          return 'FaceID';
        case 'Biometrics':
          return 'Biometrics';
        default:
          return 'Unknown';
      }
    } catch (error) {
      console.error('Failed to get biometry type:', error);
      return null;
    }
  }

  async setup(userId: string): Promise<boolean> {
    try {
      // Check if keys already exist
      const keysExist = await this.rnBiometrics.biometricKeysExist();
      
      if (keysExist.keysExist) {
        // Delete existing keys
        await this.rnBiometrics.deleteKeys();
      }

      // Create new keys
      const createKeysResult = await this.rnBiometrics.createKeys();
      
      if (createKeysResult.publicKey) {
        // Store public key on server
        await this.storePublicKey(userId, createKeysResult.publicKey);
        return true;
      }

      return false;
    } catch (error) {
      console.error('Biometric setup failed:', error);
      return false;
    }
  }

  cleanup(): void {
    // No cleanup needed for ReactNativeBiometrics
  }

  private async generateSignature(): Promise<string> {
    try {
      const payload = `${Date.now()}_${await DeviceInfo.getUniqueId()}`;
      const result = await this.rnBiometrics.createSignature({
        promptMessage: 'Authenticate',
        payload,
      });
      
      return result.signature || '';
    } catch (error) {
      console.error('Failed to generate signature:', error);
      return '';
    }
  }

  private async storePublicKey(userId: string, publicKey: string): Promise<void> {
    try {
      await ApiService.post('/api/v1/biometric/register', {
        userId,
        publicKey,
        deviceId: await DeviceInfo.getUniqueId(),
        deviceName: await DeviceInfo.getDeviceName(),
      });
    } catch (error) {
      console.error('Failed to store public key:', error);
      throw error;
    }
  }
}

/**
 * TouchID Provider (iOS fallback)
 */
class TouchIDProvider implements BiometricProvider {
  name = 'TouchID';

  async isAvailable(): Promise<boolean> {
    if (Platform.OS !== 'ios') return false;
    
    try {
      const biometryType = await TouchID.isSupported();
      return !!biometryType;
    } catch (error) {
      return false;
    }
  }

  async authenticate(options: AuthenticationOptions): Promise<AuthenticationResult> {
    try {
      const config = {
        title: options.promptMessage,
        fallbackLabel: options.fallbackLabel || '',
        passcodeFallback: !options.disableDeviceCredential,
      };

      await TouchID.authenticate('', config);
      
      return {
        success: true,
        biometryType: await this.getSupportedBiometryType(),
      };
    } catch (error: any) {
      return {
        success: false,
        error: this.mapError(error),
      };
    }
  }

  async getSupportedBiometryType(): Promise<BiometryType | null> {
    try {
      const type = await TouchID.isSupported();
      return type as BiometryType;
    } catch (error) {
      return null;
    }
  }

  async setup(userId: string): Promise<boolean> {
    // TouchID doesn't require explicit setup
    return this.isAvailable();
  }

  cleanup(): void {
    // No cleanup needed
  }

  private mapError(error: any): string {
    switch (error.code) {
      case 'UserCancel':
        return 'Authentication cancelled';
      case 'UserFallback':
        return 'User chose fallback';
      case 'SystemCancel':
        return 'System cancelled authentication';
      case 'PasscodeNotSet':
        return 'Device passcode not set';
      case 'TouchIDNotAvailable':
        return 'Touch ID not available';
      case 'TouchIDNotEnrolled':
        return 'No fingerprints enrolled';
      default:
        return error.message || 'Authentication failed';
    }
  }
}

/**
 * FingerprintScanner Provider (Android fallback)
 */
class FingerprintScannerProvider implements BiometricProvider {
  name = 'FingerprintScanner';

  async isAvailable(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;
    
    try {
      const isAvailable = await FingerprintScanner.isSensorAvailable();
      return true;
    } catch (error) {
      return false;
    }
  }

  async authenticate(options: AuthenticationOptions): Promise<AuthenticationResult> {
    try {
      await FingerprintScanner.authenticate({
        title: options.promptMessage,
        subTitle: '',
        description: '',
        cancelButton: options.cancelLabel || 'Cancel',
        onAttempt: (error) => {
          console.log('Authentication attempt:', error);
        },
      });

      return {
        success: true,
        biometryType: await this.getSupportedBiometryType(),
      };
    } catch (error: any) {
      return {
        success: false,
        error: this.mapError(error),
      };
    }
  }

  async getSupportedBiometryType(): Promise<BiometryType | null> {
    try {
      const type = await FingerprintScanner.isSensorAvailable();
      
      switch (type) {
        case 'Fingerprint':
          return 'Fingerprint';
        case 'Face':
          return 'Face';
        case 'Iris':
          return 'Iris';
        default:
          return 'Biometrics';
      }
    } catch (error) {
      return null;
    }
  }

  async setup(userId: string): Promise<boolean> {
    // FingerprintScanner doesn't require explicit setup
    return this.isAvailable();
  }

  cleanup(): void {
    // Release fingerprint scanner resources
    FingerprintScanner.release();
  }

  private mapError(error: any): string {
    switch (error.name) {
      case 'AuthenticationNotMatch':
        return 'Fingerprint not recognized';
      case 'AuthenticationFailed':
        return 'Authentication failed';
      case 'UserCancel':
        return 'Authentication cancelled';
      case 'UserFallback':
        return 'User chose fallback';
      case 'SystemCancel':
        return 'System cancelled authentication';
      case 'PasscodeNotSet':
        return 'Device passcode not set';
      case 'FingerprintScannerNotAvailable':
        return 'Fingerprint scanner not available';
      case 'FingerprintScannerNotEnrolled':
        return 'No fingerprints enrolled';
      case 'DeviceLocked':
        return 'Device is locked';
      default:
        return error.message || 'Authentication failed';
    }
  }
}

/**
 * Future Voice Biometric Provider (placeholder)
 */
class VoiceBiometricProvider implements BiometricProvider {
  name = 'VoiceBiometric';

  async isAvailable(): Promise<boolean> {
    // Voice biometric support to be implemented
    return false;
  }

  async authenticate(options: AuthenticationOptions): Promise<AuthenticationResult> {
    return {
      success: false,
      error: 'Voice biometric not yet supported',
    };
  }

  async getSupportedBiometryType(): Promise<BiometryType | null> {
    return 'Voice';
  }

  async setup(userId: string): Promise<boolean> {
    return false;
  }

  cleanup(): void {
    // No cleanup needed
  }
}

/**
 * Biometric Provider Manager
 */
export class BiometricProviderManager {
  private providers: BiometricProvider[] = [];
  private currentProvider: BiometricProvider | null = null;
  private providerCache = new Map<string, boolean>();

  constructor() {
    // Register providers in order of preference
    this.registerProvider(new ReactNativeBiometricsProvider());
    
    if (Platform.OS === 'ios') {
      this.registerProvider(new TouchIDProvider());
    } else if (Platform.OS === 'android') {
      this.registerProvider(new FingerprintScannerProvider());
    }
    
    // Future providers
    this.registerProvider(new VoiceBiometricProvider());
  }

  registerProvider(provider: BiometricProvider): void {
    this.providers.push(provider);
  }

  async getAvailableProvider(): Promise<BiometricProvider | null> {
    // Check cache first
    const cachedProvider = this.getCachedProvider();
    if (cachedProvider) {
      return cachedProvider;
    }

    // Test each provider
    for (const provider of this.providers) {
      try {
        const isAvailable = await provider.isAvailable();
        if (isAvailable) {
          this.currentProvider = provider;
          this.cacheProvider(provider.name);
          console.log(`Using biometric provider: ${provider.name}`);
          return provider;
        }
      } catch (error) {
        console.warn(`Provider ${provider.name} check failed:`, error);
      }
    }

    return null;
  }

  async authenticate(options: AuthenticationOptions): Promise<AuthenticationResult> {
    const provider = await this.getAvailableProvider();
    
    if (!provider) {
      return {
        success: false,
        error: 'No biometric authentication available',
      };
    }

    try {
      const result = await provider.authenticate(options);
      
      // Log authentication event
      await this.logAuthenticationEvent(provider.name, result);
      
      return result;
    } catch (error: any) {
      console.error('Authentication error:', error);
      return {
        success: false,
        error: error.message || 'Authentication failed',
      };
    }
  }

  async getSupportedBiometryType(): Promise<BiometryType | null> {
    const provider = await this.getAvailableProvider();
    
    if (!provider) {
      return null;
    }

    return provider.getSupportedBiometryType();
  }

  async setup(userId: string): Promise<boolean> {
    const provider = await this.getAvailableProvider();
    
    if (!provider) {
      return false;
    }

    return provider.setup(userId);
  }

  cleanup(): void {
    this.providers.forEach(provider => provider.cleanup());
  }

  // Provider fallback mechanism
  async authenticateWithFallback(
    options: AuthenticationOptions, 
    fallbackProviders?: string[]
  ): Promise<AuthenticationResult> {
    const primaryResult = await this.authenticate(options);
    
    if (primaryResult.success || !fallbackProviders?.length) {
      return primaryResult;
    }

    // Try fallback providers
    for (const providerName of fallbackProviders) {
      const provider = this.providers.find(p => p.name === providerName);
      
      if (provider && await provider.isAvailable()) {
        const fallbackResult = await provider.authenticate(options);
        
        if (fallbackResult.success) {
          return fallbackResult;
        }
      }
    }

    return primaryResult;
  }

  // Get all available providers
  async getAllAvailableProviders(): Promise<BiometricProvider[]> {
    const available: BiometricProvider[] = [];
    
    for (const provider of this.providers) {
      if (await provider.isAvailable()) {
        available.push(provider);
      }
    }
    
    return available;
  }

  // Cache management
  private cacheProvider(providerName: string): void {
    AsyncStorage.setItem('biometric_provider', providerName).catch(console.error);
    this.providerCache.set('current', true);
  }

  private getCachedProvider(): BiometricProvider | null {
    if (this.currentProvider && this.providerCache.has('current')) {
      return this.currentProvider;
    }
    return null;
  }

  private async logAuthenticationEvent(
    providerName: string, 
    result: AuthenticationResult
  ): Promise<void> {
    try {
      await ApiService.post('/api/v1/biometric/log-event', {
        provider: providerName,
        success: result.success,
        biometryType: result.biometryType,
        timestamp: new Date().toISOString(),
        deviceId: await DeviceInfo.getUniqueId(),
      });
    } catch (error) {
      console.error('Failed to log authentication event:', error);
    }
  }
}

// Export singleton instance
export default new BiometricProviderManager();