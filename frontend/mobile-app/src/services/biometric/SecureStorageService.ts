/**
 * Secure Storage Service for Biometric Authentication
 * Handles secure storage of biometric tokens and authentication data
 */

import EncryptedStorage from 'react-native-encrypted-storage';
import Keychain from 'react-native-keychain';
import { 
  ISecureStorageService, 
  StoredBiometricData, 
  BiometricToken,
  BiometricError,
  BiometricAuthError 
} from './types';

export class SecureStorageService implements ISecureStorageService {
  private static instance: SecureStorageService;
  
  // Storage keys
  private readonly BIOMETRIC_DATA_PREFIX = 'biometric_data_';
  private readonly BIOMETRIC_TOKEN_PREFIX = 'biometric_token_';
  private readonly KEYCHAIN_SERVICE = 'WaqitiBiometric';
  private readonly KEYCHAIN_ACCESS_GROUP = 'group.com.waqiti.secure';

  public static getInstance(): SecureStorageService {
    if (!SecureStorageService.instance) {
      SecureStorageService.instance = new SecureStorageService();
    }
    return SecureStorageService.instance;
  }

  /**
   * Store biometric authentication data securely
   */
  async storeBiometricData(data: StoredBiometricData): Promise<void> {
    try {
      const key = this.getBiometricDataKey(data.userId);
      const encryptedData = JSON.stringify(data);
      
      // Store in encrypted storage
      await EncryptedStorage.setItem(key, encryptedData);
      
      // Also store key material in Keychain for additional security
      await Keychain.setInternetCredentials(
        `${this.KEYCHAIN_SERVICE}_${data.userId}`,
        data.userId,
        data.publicKey,
        {
          accessControl: Keychain.ACCESS_CONTROL.BIOMETRY_CURRENT_SET,
          accessGroup: this.KEYCHAIN_ACCESS_GROUP,
          authenticationType: Keychain.AUTHENTICATION_TYPE.BIOMETRICS,
          storage: Keychain.STORAGE_TYPE.AES_GCM_NO_AUTH,
        }
      );
      
      console.log(`Stored biometric data for user: ${data.userId}`);
    } catch (error) {
      console.error('Failed to store biometric data:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to store biometric authentication data',
        error,
        false,
        true
      );
    }
  }

  /**
   * Retrieve biometric authentication data
   */
  async getBiometricData(userId: string): Promise<StoredBiometricData | null> {
    try {
      const key = this.getBiometricDataKey(userId);
      const encryptedData = await EncryptedStorage.getItem(key);
      
      if (!encryptedData) {
        return null;
      }
      
      const data = JSON.parse(encryptedData) as StoredBiometricData;
      
      // Verify data integrity by checking Keychain
      try {
        const credentials = await Keychain.getInternetCredentials(
          `${this.KEYCHAIN_SERVICE}_${userId}`
        );
        
        if (credentials && credentials.username === userId) {
          // Update last used timestamp
          data.lastUsedAt = Date.now();
          await this.storeBiometricData(data);
          return data;
        }
      } catch (keychainError) {
        console.warn('Keychain validation failed:', keychainError);
        // Continue with encrypted storage data if Keychain fails
      }
      
      return data;
    } catch (error) {
      console.error('Failed to retrieve biometric data:', error);
      return null;
    }
  }

  /**
   * Remove biometric authentication data
   */
  async removeBiometricData(userId: string): Promise<void> {
    try {
      const key = this.getBiometricDataKey(userId);
      
      // Remove from encrypted storage
      await EncryptedStorage.removeItem(key);
      
      // Remove from Keychain
      try {
        await Keychain.resetInternetCredentials(`${this.KEYCHAIN_SERVICE}_${userId}`);
      } catch (keychainError) {
        console.warn('Failed to remove from Keychain:', keychainError);
        // Continue even if Keychain removal fails
      }
      
      console.log(`Removed biometric data for user: ${userId}`);
    } catch (error) {
      console.error('Failed to remove biometric data:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to remove biometric authentication data',
        error,
        true,
        false
      );
    }
  }

  /**
   * Store biometric authentication token
   */
  async storeToken(token: BiometricToken): Promise<void> {
    try {
      const key = this.getTokenKey(token.userId);
      const encryptedToken = JSON.stringify(token);
      
      await EncryptedStorage.setItem(key, encryptedToken);
      
      console.log(`Stored biometric token for user: ${token.userId}`);
    } catch (error) {
      console.error('Failed to store biometric token:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to store authentication token',
        error,
        true,
        false
      );
    }
  }

  /**
   * Retrieve biometric authentication token
   */
  async getToken(userId: string): Promise<BiometricToken | null> {
    try {
      const key = this.getTokenKey(userId);
      const encryptedToken = await EncryptedStorage.getItem(key);
      
      if (!encryptedToken) {
        return null;
      }
      
      const token = JSON.parse(encryptedToken) as BiometricToken;
      
      // Check if token is expired
      if (token.expiresAt < Date.now()) {
        await this.removeToken(userId);
        return null;
      }
      
      return token;
    } catch (error) {
      console.error('Failed to retrieve biometric token:', error);
      return null;
    }
  }

  /**
   * Remove biometric authentication token
   */
  async removeToken(userId: string): Promise<void> {
    try {
      const key = this.getTokenKey(userId);
      await EncryptedStorage.removeItem(key);
      console.log(`Removed biometric token for user: ${userId}`);
    } catch (error) {
      console.error('Failed to remove biometric token:', error);
      // Don't throw error for token removal failures
    }
  }

  /**
   * Clear all biometric data (for logout/reset)
   */
  async clearAllData(): Promise<void> {
    try {
      // Get all stored keys
      const allKeys = await EncryptedStorage.getAllKeys();
      
      // Filter biometric-related keys
      const biometricKeys = allKeys.filter(key => 
        key.startsWith(this.BIOMETRIC_DATA_PREFIX) || 
        key.startsWith(this.BIOMETRIC_TOKEN_PREFIX)
      );
      
      // Remove all biometric data
      await Promise.all(biometricKeys.map(key => EncryptedStorage.removeItem(key)));
      
      // Clear all Keychain entries (if possible)
      try {
        await Keychain.resetGenericPassword({
          service: this.KEYCHAIN_SERVICE,
        });
      } catch (keychainError) {
        console.warn('Failed to clear Keychain:', keychainError);
      }
      
      console.log('Cleared all biometric data');
    } catch (error) {
      console.error('Failed to clear biometric data:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to clear biometric data',
        error,
        true,
        false
      );
    }
  }

  /**
   * Get all stored biometric users
   */
  async getAllStoredUsers(): Promise<string[]> {
    try {
      const allKeys = await EncryptedStorage.getAllKeys();
      const biometricDataKeys = allKeys.filter(key => 
        key.startsWith(this.BIOMETRIC_DATA_PREFIX)
      );
      
      return biometricDataKeys.map(key => 
        key.replace(this.BIOMETRIC_DATA_PREFIX, '')
      );
    } catch (error) {
      console.error('Failed to get stored users:', error);
      return [];
    }
  }

  /**
   * Check if biometric data exists for user
   */
  async hasBiometricData(userId: string): Promise<boolean> {
    try {
      const data = await this.getBiometricData(userId);
      return data !== null && data.isActive;
    } catch (error) {
      return false;
    }
  }

  /**
   * Check if valid token exists for user
   */
  async hasValidToken(userId: string): Promise<boolean> {
    try {
      const token = await this.getToken(userId);
      return token !== null;
    } catch (error) {
      return false;
    }
  }

  /**
   * Update failure count for biometric data
   */
  async incrementFailureCount(userId: string): Promise<number> {
    try {
      const data = await this.getBiometricData(userId);
      if (data) {
        data.failureCount = (data.failureCount || 0) + 1;
        await this.storeBiometricData(data);
        return data.failureCount;
      }
      return 0;
    } catch (error) {
      console.error('Failed to increment failure count:', error);
      return 0;
    }
  }

  /**
   * Reset failure count for biometric data
   */
  async resetFailureCount(userId: string): Promise<void> {
    try {
      const data = await this.getBiometricData(userId);
      if (data) {
        data.failureCount = 0;
        data.lastUsedAt = Date.now();
        await this.storeBiometricData(data);
      }
    } catch (error) {
      console.error('Failed to reset failure count:', error);
    }
  }

  /**
   * Check if user is locked out due to too many failures
   */
  async isLockedOut(userId: string, maxFailures: number = 5): Promise<boolean> {
    try {
      const data = await this.getBiometricData(userId);
      return data ? data.failureCount >= maxFailures : false;
    } catch (error) {
      return false;
    }
  }

  /**
   * Store backup authentication data
   */
  async storeBackupData(userId: string, backupData: any): Promise<void> {
    try {
      const key = `backup_auth_${userId}`;
      const encryptedData = JSON.stringify(backupData);
      await EncryptedStorage.setItem(key, encryptedData);
    } catch (error) {
      console.error('Failed to store backup data:', error);
      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to store backup authentication data',
        error,
        true,
        false
      );
    }
  }

  /**
   * Retrieve backup authentication data
   */
  async getBackupData(userId: string): Promise<any | null> {
    try {
      const key = `backup_auth_${userId}`;
      const encryptedData = await EncryptedStorage.getItem(key);
      return encryptedData ? JSON.parse(encryptedData) : null;
    } catch (error) {
      console.error('Failed to retrieve backup data:', error);
      return null;
    }
  }

  /**
   * Private helper methods
   */
  private getBiometricDataKey(userId: string): string {
    return `${this.BIOMETRIC_DATA_PREFIX}${userId}`;
  }

  private getTokenKey(userId: string): string {
    return `${this.BIOMETRIC_TOKEN_PREFIX}${userId}`;
  }

  /**
   * Validate stored data integrity
   */
  async validateDataIntegrity(userId: string): Promise<boolean> {
    try {
      const data = await this.getBiometricData(userId);
      if (!data) return false;

      // Basic validation checks
      const isValid = !!(
        data.userId &&
        data.publicKey &&
        data.keyAlias &&
        data.biometryType &&
        data.enrolledAt &&
        data.deviceFingerprint &&
        typeof data.isActive === 'boolean' &&
        typeof data.failureCount === 'number'
      );

      return isValid;
    } catch (error) {
      console.error('Data integrity validation failed:', error);
      return false;
    }
  }

  /**
   * Get storage statistics
   */
  async getStorageStats(): Promise<{
    totalUsers: number;
    activeUsers: number;
    tokensStored: number;
    storageSize: number;
  }> {
    try {
      const allKeys = await EncryptedStorage.getAllKeys();
      const biometricDataKeys = allKeys.filter(key => 
        key.startsWith(this.BIOMETRIC_DATA_PREFIX)
      );
      const tokenKeys = allKeys.filter(key => 
        key.startsWith(this.BIOMETRIC_TOKEN_PREFIX)
      );

      let activeUsers = 0;
      for (const key of biometricDataKeys) {
        const userId = key.replace(this.BIOMETRIC_DATA_PREFIX, '');
        const data = await this.getBiometricData(userId);
        if (data?.isActive) {
          activeUsers++;
        }
      }

      return {
        totalUsers: biometricDataKeys.length,
        activeUsers,
        tokensStored: tokenKeys.length,
        storageSize: allKeys.length, // Approximation
      };
    } catch (error) {
      console.error('Failed to get storage stats:', error);
      return {
        totalUsers: 0,
        activeUsers: 0,
        tokensStored: 0,
        storageSize: 0,
      };
    }
  }
}

export default SecureStorageService;