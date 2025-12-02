import AsyncStorage from '@react-native-async-storage/async-storage';
import EncryptedStorage from 'react-native-encrypted-storage';
import { ApiService } from '../ApiService';

export interface SecretConfig {
  googleMapsApiKey: string;
  stripePublishableKey: string;
  firebaseApiKey: string;
  mixpanelToken: string;
  sentryDsn: string;
}

export interface VaultAuthResponse {
  token: string;
  expiresAt: number;
  policies: string[];
}

class VaultConfigService {
  private secretsCache: Partial<SecretConfig> = {};
  private cacheTimestamp: number = 0;
  private readonly CACHE_TTL = 5 * 60 * 1000; // 5 minutes
  private readonly CACHE_KEY = 'vault_secrets_cache';
  private vaultToken: string | null = null;
  private tokenExpiresAt: number = 0;

  /**
   * Initialize the service and authenticate with backend
   */
  async initialize(): Promise<void> {
    try {
      console.log('Initializing VaultConfigService...');
      
      // Try to load cached secrets first for faster startup
      await this.loadCachedSecrets();
      
      // Authenticate with backend to get Vault access
      await this.authenticateWithBackend();
      
      // Refresh secrets from backend/vault
      await this.refreshSecrets();
      
      console.log('VaultConfigService initialized successfully');
    } catch (error) {
      console.error('Failed to initialize VaultConfigService:', error);
      
      // If initialization fails, try to use cached secrets
      if (Object.keys(this.secretsCache).length > 0) {
        console.warn('Using cached secrets due to initialization failure');
      } else {
        throw new Error('VaultConfigService initialization failed and no cached secrets available');
      }
    }
  }

  /**
   * Get Google Maps API key
   */
  async getGoogleMapsApiKey(): Promise<string> {
    const secret = await this.getSecret('googleMapsApiKey');
    
    if (!secret || secret === 'YOUR_API_KEY') {
      throw new Error('Google Maps API key not available. Please check Vault configuration.');
    }
    
    return secret;
  }

  /**
   * Check if Google Maps is properly configured
   */
  async isGoogleMapsConfigured(): Promise<boolean> {
    try {
      const apiKey = await this.getGoogleMapsApiKey();
      return !!(apiKey && apiKey !== 'YOUR_API_KEY');
    } catch {
      return false;
    }
  }

  /**
   * Get Stripe publishable key
   */
  async getStripePublishableKey(): Promise<string> {
    return await this.getSecret('stripePublishableKey');
  }

  /**
   * Get Firebase API key
   */
  async getFirebaseApiKey(): Promise<string> {
    return await this.getSecret('firebaseApiKey');
  }

  /**
   * Get Mixpanel token
   */
  async getMixpanelToken(): Promise<string> {
    return await this.getSecret('mixpanelToken');
  }

  /**
   * Get Sentry DSN
   */
  async getSentryDsn(): Promise<string> {
    return await this.getSecret('sentryDsn');
  }

  /**
   * Force refresh secrets from backend/vault
   */
  async refreshSecrets(): Promise<void> {
    try {
      console.log('Refreshing secrets from backend...');
      
      // Call backend API to get all mobile app secrets
      const response = await ApiService.post('/api/v1/mobile/secrets', {
        requiredSecrets: [
          'google.maps.api.key',
          'stripe.publishable.key', 
          'firebase.api.key',
          'mixpanel.token',
          'sentry.dsn'
        ]
      });

      if (!response.success) {
        throw new Error(`Failed to fetch secrets: ${response.message}`);
      }

      // Update cache with new secrets
      this.secretsCache = {
        googleMapsApiKey: response.data.secrets['google.maps.api.key'],
        stripePublishableKey: response.data.secrets['stripe.publishable.key'],
        firebaseApiKey: response.data.secrets['firebase.api.key'],
        mixpanelToken: response.data.secrets['mixpanel.token'],
        sentryDsn: response.data.secrets['sentry.dsn'],
      };

      this.cacheTimestamp = Date.now();
      
      // Persist to encrypted storage
      await this.persistSecrets();
      
      console.log('Secrets refreshed successfully');
    } catch (error) {
      console.error('Failed to refresh secrets:', error);
      throw error;
    }
  }

  /**
   * Get configuration for debugging (excludes actual secret values)
   */
  getDebugInfo(): object {
    return {
      cacheAge: this.cacheTimestamp ? Date.now() - this.cacheTimestamp : null,
      cacheValid: this.isCacheValid(),
      secretsAvailable: Object.keys(this.secretsCache),
      tokenExpired: this.tokenExpiresAt < Date.now(),
      hasVaultToken: !!this.vaultToken,
    };
  }

  /**
   * Clear all cached secrets
   */
  async clearCache(): Promise<void> {
    try {
      this.secretsCache = {};
      this.cacheTimestamp = 0;
      this.vaultToken = null;
      this.tokenExpiresAt = 0;
      
      await EncryptedStorage.removeItem(this.CACHE_KEY);
      await EncryptedStorage.removeItem('vault_auth_token');
      
      console.log('Vault cache cleared');
    } catch (error) {
      console.error('Failed to clear vault cache:', error);
    }
  }

  private async getSecret(key: keyof SecretConfig): Promise<string> {
    // Check if cache is still valid
    if (!this.isCacheValid()) {
      try {
        await this.refreshSecrets();
      } catch (error) {
        console.warn(`Failed to refresh secrets, using cached value for ${key}:`, error);
      }
    }

    const secret = this.secretsCache[key];
    if (!secret) {
      throw new Error(`Secret '${key}' not available in cache. Try refreshing secrets.`);
    }

    return secret;
  }

  private async authenticateWithBackend(): Promise<void> {
    try {
      // Check if we have a valid token
      if (this.vaultToken && this.tokenExpiresAt > Date.now()) {
        return;
      }

      console.log('Authenticating with backend for Vault access...');
      
      // Get device/user authentication token from your auth service
      const authToken = await ApiService.getAuthToken();
      if (!authToken) {
        throw new Error('No authentication token available');
      }

      // Request Vault access token from backend
      const response = await ApiService.post('/api/v1/auth/vault-token', {
        scope: 'mobile-secrets'
      });

      if (!response.success) {
        throw new Error(`Vault authentication failed: ${response.message}`);
      }

      this.vaultToken = response.data.token;
      this.tokenExpiresAt = response.data.expiresAt;

      // Persist auth token securely
      await EncryptedStorage.setItem('vault_auth_token', JSON.stringify({
        token: this.vaultToken,
        expiresAt: this.tokenExpiresAt,
      }));

      console.log('Vault authentication successful');
    } catch (error) {
      console.error('Vault authentication failed:', error);
      
      // Try to load cached token
      await this.loadCachedAuthToken();
      
      if (!this.vaultToken || this.tokenExpiresAt <= Date.now()) {
        throw new Error('Vault authentication failed and no valid cached token available');
      }
    }
  }

  private async loadCachedAuthToken(): Promise<void> {
    try {
      const cached = await EncryptedStorage.getItem('vault_auth_token');
      if (cached) {
        const { token, expiresAt } = JSON.parse(cached);
        if (expiresAt > Date.now()) {
          this.vaultToken = token;
          this.tokenExpiresAt = expiresAt;
          console.log('Loaded cached Vault auth token');
        }
      }
    } catch (error) {
      console.warn('Failed to load cached auth token:', error);
    }
  }

  private async loadCachedSecrets(): Promise<void> {
    try {
      const cached = await EncryptedStorage.getItem(this.CACHE_KEY);
      if (cached) {
        const { secrets, timestamp } = JSON.parse(cached);
        
        // Use cached secrets even if expired for faster startup
        this.secretsCache = secrets;
        this.cacheTimestamp = timestamp;
        
        console.log('Loaded cached secrets from encrypted storage');
      }
    } catch (error) {
      console.warn('Failed to load cached secrets:', error);
    }
  }

  private async persistSecrets(): Promise<void> {
    try {
      const cacheData = {
        secrets: this.secretsCache,
        timestamp: this.cacheTimestamp,
      };
      
      await EncryptedStorage.setItem(this.CACHE_KEY, JSON.stringify(cacheData));
    } catch (error) {
      console.error('Failed to persist secrets to cache:', error);
    }
  }

  private isCacheValid(): boolean {
    if (!this.cacheTimestamp || Object.keys(this.secretsCache).length === 0) {
      return false;
    }
    
    return (Date.now() - this.cacheTimestamp) < this.CACHE_TTL;
  }
}

export default new VaultConfigService();