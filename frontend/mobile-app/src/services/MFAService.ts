/**
 * MFA Service - Multi-Factor Authentication implementation
 * Handles TOTP, SMS, and biometric authentication flows
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from './ApiService';
import DeviceInfo from 'react-native-device-info';
import * as Keychain from 'react-native-keychain';
import { BiometricAuthService } from './biometric/BiometricAuthService';
import { Platform } from 'react-native';

export enum MFAMethod {
  TOTP = 'TOTP',
  SMS = 'SMS',
  BIOMETRIC = 'BIOMETRIC',
  EMAIL = 'EMAIL',
  PUSH = 'PUSH'
}

export enum MFAAction {
  LOGIN = 'LOGIN',
  TRANSACTION = 'TRANSACTION',
  PROFILE_CHANGE = 'PROFILE_CHANGE',
  HIGH_VALUE_TRANSFER = 'HIGH_VALUE_TRANSFER',
  DEVICE_REGISTRATION = 'DEVICE_REGISTRATION'
}

export interface MFASetupRequest {
  method: MFAMethod;
  phoneNumber?: string;
  email?: string;
}

export interface MFASetupResponse {
  method: MFAMethod;
  qrCode?: string; // For TOTP
  secret?: string; // For TOTP backup
  backupCodes?: string[];
  phoneNumber?: string; // For SMS
  email?: string; // For email
}

export interface MFAVerificationRequest {
  method: MFAMethod;
  code?: string;
  biometricSignature?: string;
  deviceId: string;
  action: MFAAction;
  metadata?: Record<string, any>;
}

export interface MFAVerificationResponse {
  verified: boolean;
  token?: string;
  expiresIn?: number;
  remainingAttempts?: number;
}

export interface MFAStatus {
  enabled: boolean;
  methods: MFAMethod[];
  primaryMethod: MFAMethod | null;
  backupMethods: MFAMethod[];
  trustedDevices: string[];
  lastVerification?: Date;
}

export interface TrustedDevice {
  id: string;
  name: string;
  platform: string;
  lastUsed: Date;
  addedAt: Date;
  isCurrent: boolean;
}

class MFAService {
  private static instance: MFAService;
  private apiService: ApiService;
  private biometricService: BiometricAuthService;
  
  private readonly STORAGE_KEYS = {
    MFA_STATUS: '@waqiti_mfa_status',
    TRUSTED_DEVICES: '@waqiti_trusted_devices',
    MFA_TOKEN: '@waqiti_mfa_token',
    DEVICE_ID: '@waqiti_device_id',
    TOTP_SECRET: '@waqiti_totp_secret_encrypted'
  };

  private constructor() {
    this.apiService = ApiService.getInstance();
    this.biometricService = BiometricAuthService.getInstance();
  }

  public static getInstance(): MFAService {
    if (!MFAService.instance) {
      MFAService.instance = new MFAService();
    }
    return MFAService.instance;
  }

  /**
   * Get MFA status for the current user
   */
  async getMFAStatus(): Promise<MFAStatus> {
    try {
      const response = await this.apiService.get<MFAStatus>('/auth/mfa/status');
      
      // Cache the status
      await AsyncStorage.setItem(
        this.STORAGE_KEYS.MFA_STATUS,
        JSON.stringify(response)
      );
      
      return response;
    } catch (error) {
      // Fallback to cached status
      const cached = await AsyncStorage.getItem(this.STORAGE_KEYS.MFA_STATUS);
      if (cached) {
        return JSON.parse(cached);
      }
      throw error;
    }
  }

  /**
   * Setup MFA method
   */
  async setupMFA(request: MFASetupRequest): Promise<MFASetupResponse> {
    try {
      const deviceId = await this.getOrCreateDeviceId();
      
      const response = await this.apiService.post<MFASetupResponse>(
        '/auth/mfa/setup',
        {
          ...request,
          deviceId,
          deviceInfo: await this.getDeviceInfo()
        }
      );

      // For TOTP, securely store the secret
      if (request.method === MFAMethod.TOTP && response.secret) {
        await this.securelyStoreTOTPSecret(response.secret);
      }

      // Update cached MFA status
      await this.getMFAStatus();

      return response;
    } catch (error) {
      console.error('MFA setup failed:', error);
      throw error;
    }
  }

  /**
   * Verify MFA code/authentication
   */
  async verifyMFA(request: MFAVerificationRequest): Promise<MFAVerificationResponse> {
    try {
      // For biometric, get the signature first
      if (request.method === MFAMethod.BIOMETRIC) {
        const biometricResult = await this.biometricService.authenticate({
          reason: this.getAuthenticationReason(request.action),
          fallbackToPin: true
        });

        if (!biometricResult.success) {
          throw new Error('Biometric authentication failed');
        }

        request.biometricSignature = biometricResult.signature;
      }

      const response = await this.apiService.post<MFAVerificationResponse>(
        '/auth/mfa/verify',
        {
          ...request,
          deviceId: await this.getOrCreateDeviceId(),
          timestamp: Date.now()
        }
      );

      // Store MFA token if provided
      if (response.token) {
        await Keychain.setInternetCredentials(
          'waqiti-mfa',
          'mfa-token',
          response.token
        );
      }

      return response;
    } catch (error) {
      console.error('MFA verification failed:', error);
      throw error;
    }
  }

  /**
   * Disable MFA (requires current authentication)
   */
  async disableMFA(verificationCode: string): Promise<void> {
    try {
      await this.apiService.post('/auth/mfa/disable', {
        verificationCode,
        deviceId: await this.getOrCreateDeviceId()
      });

      // Clear cached data
      await this.clearMFAData();
    } catch (error) {
      console.error('Failed to disable MFA:', error);
      throw error;
    }
  }

  /**
   * Get trusted devices
   */
  async getTrustedDevices(): Promise<TrustedDevice[]> {
    try {
      const response = await this.apiService.get<TrustedDevice[]>('/auth/mfa/devices');
      const currentDeviceId = await this.getOrCreateDeviceId();
      
      // Mark current device
      return response.map(device => ({
        ...device,
        isCurrent: device.id === currentDeviceId
      }));
    } catch (error) {
      console.error('Failed to get trusted devices:', error);
      throw error;
    }
  }

  /**
   * Remove trusted device
   */
  async removeTrustedDevice(deviceId: string): Promise<void> {
    try {
      await this.apiService.delete(`/auth/mfa/devices/${deviceId}`);
      
      // Update cached trusted devices
      const devices = await this.getTrustedDevices();
      await AsyncStorage.setItem(
        this.STORAGE_KEYS.TRUSTED_DEVICES,
        JSON.stringify(devices)
      );
    } catch (error) {
      console.error('Failed to remove trusted device:', error);
      throw error;
    }
  }

  /**
   * Generate backup codes
   */
  async generateBackupCodes(): Promise<string[]> {
    try {
      const response = await this.apiService.post<{ codes: string[] }>(
        '/auth/mfa/backup-codes/generate'
      );
      
      return response.codes;
    } catch (error) {
      console.error('Failed to generate backup codes:', error);
      throw error;
    }
  }

  /**
   * Request SMS code
   */
  async requestSMSCode(action: MFAAction): Promise<void> {
    try {
      await this.apiService.post('/auth/mfa/sms/send', {
        action,
        deviceId: await this.getOrCreateDeviceId()
      });
    } catch (error) {
      console.error('Failed to send SMS code:', error);
      throw error;
    }
  }

  /**
   * Check if MFA is required for action
   */
  async isMFARequired(action: MFAAction, metadata?: Record<string, any>): Promise<boolean> {
    try {
      const response = await this.apiService.post<{ required: boolean }>(
        '/auth/mfa/check-requirement',
        {
          action,
          metadata,
          deviceId: await this.getOrCreateDeviceId()
        }
      );
      
      return response.required;
    } catch (error) {
      console.error('Failed to check MFA requirement:', error);
      // Default to requiring MFA on error for security
      return true;
    }
  }

  /**
   * Get or create unique device ID
   */
  private async getOrCreateDeviceId(): Promise<string> {
    try {
      // Check if we have a stored device ID
      let deviceId = await AsyncStorage.getItem(this.STORAGE_KEYS.DEVICE_ID);
      
      if (!deviceId) {
        // Generate new device ID
        const uniqueId = await DeviceInfo.getUniqueId();
        const deviceName = await DeviceInfo.getDeviceName();
        
        deviceId = `${Platform.OS}_${uniqueId}_${Date.now()}`;
        
        await AsyncStorage.setItem(this.STORAGE_KEYS.DEVICE_ID, deviceId);
      }
      
      return deviceId;
    } catch (error) {
      console.error('Failed to get device ID:', error);
      // Fallback to a random ID
      return `${Platform.OS}_${Math.random().toString(36).substring(2)}_${Date.now()}`;
    }
  }

  /**
   * Get device information
   */
  private async getDeviceInfo(): Promise<Record<string, any>> {
    try {
      return {
        platform: Platform.OS,
        version: Platform.Version,
        deviceName: await DeviceInfo.getDeviceName(),
        deviceModel: DeviceInfo.getModel(),
        deviceBrand: DeviceInfo.getBrand(),
        systemVersion: DeviceInfo.getSystemVersion(),
        appVersion: DeviceInfo.getVersion(),
        buildNumber: DeviceInfo.getBuildNumber(),
        hasNotch: DeviceInfo.hasNotch(),
        isTablet: DeviceInfo.isTablet()
      };
    } catch (error) {
      console.error('Failed to get device info:', error);
      return {
        platform: Platform.OS,
        version: Platform.Version
      };
    }
  }

  /**
   * Securely store TOTP secret
   */
  private async securelyStoreTOTPSecret(secret: string): Promise<void> {
    try {
      await Keychain.setInternetCredentials(
        'waqiti-totp',
        'totp-secret',
        secret
      );
    } catch (error) {
      console.error('Failed to store TOTP secret:', error);
      throw error;
    }
  }

  /**
   * Get TOTP secret
   */
  async getTOTPSecret(): Promise<string | null> {
    try {
      const credentials = await Keychain.getInternetCredentials('waqiti-totp');
      return credentials ? credentials.password : null;
    } catch (error) {
      console.error('Failed to get TOTP secret:', error);
      return null;
    }
  }

  /**
   * Clear all MFA data
   */
  private async clearMFAData(): Promise<void> {
    try {
      await Promise.all([
        AsyncStorage.removeItem(this.STORAGE_KEYS.MFA_STATUS),
        AsyncStorage.removeItem(this.STORAGE_KEYS.TRUSTED_DEVICES),
        AsyncStorage.removeItem(this.STORAGE_KEYS.MFA_TOKEN),
        Keychain.resetInternetCredentials('waqiti-mfa'),
        Keychain.resetInternetCredentials('waqiti-totp')
      ]);
    } catch (error) {
      console.error('Failed to clear MFA data:', error);
    }
  }

  /**
   * Get authentication reason based on action
   */
  private getAuthenticationReason(action: MFAAction): string {
    switch (action) {
      case MFAAction.LOGIN:
        return 'Authenticate to access your Waqiti account';
      case MFAAction.TRANSACTION:
        return 'Verify transaction';
      case MFAAction.HIGH_VALUE_TRANSFER:
        return 'Authorize high-value transfer';
      case MFAAction.PROFILE_CHANGE:
        return 'Confirm profile changes';
      case MFAAction.DEVICE_REGISTRATION:
        return 'Register new device';
      default:
        return 'Authenticate to continue';
    }
  }

  /**
   * Check if device supports biometric MFA
   */
  async isBiometricMFAAvailable(): Promise<boolean> {
    try {
      const biometricType = await this.biometricService.getBiometryType();
      return biometricType !== null && biometricType !== 'None';
    } catch (error) {
      console.error('Failed to check biometric availability:', error);
      return false;
    }
  }

  /**
   * Generate TOTP code locally (for apps that generate codes)
   */
  async generateTOTPCode(): Promise<string | null> {
    try {
      const secret = await this.getTOTPSecret();
      if (!secret) {
        return null;
      }

      // Implementation would use a TOTP library like speakeasy
      // This is a placeholder - actual implementation would generate real TOTP
      const timestamp = Math.floor(Date.now() / 30000);
      const code = Math.abs(timestamp % 1000000).toString().padStart(6, '0');
      
      return code;
    } catch (error) {
      console.error('Failed to generate TOTP code:', error);
      return null;
    }
  }
}

export default MFAService.getInstance();