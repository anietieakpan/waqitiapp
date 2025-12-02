/**
 * Security Service Client
 * Handles communication with the backend security service for biometric authentication
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import Config from 'react-native-config';
import { 
  ISecurityServiceClient,
  BiometricRegistrationRequest,
  BiometricAuthenticationRequest,
  BiometricVerificationResponse,
  BiometricEvent,
  BiometricSettings,
  DeviceFingerprint,
  BiometricError,
  BiometricAuthError 
} from './types';

export class SecurityServiceClient implements ISecurityServiceClient {
  private static instance: SecurityServiceClient;
  private axiosInstance: AxiosInstance;
  private readonly baseURL: string;
  private readonly timeout: number = 10000;
  private accessToken: string | null = null;

  constructor() {
    this.baseURL = Config.SECURITY_SERVICE_URL || 'http://localhost:8080';
    
    this.axiosInstance = axios.create({
      baseURL: this.baseURL,
      timeout: this.timeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Client-Platform': 'mobile',
        'X-Client-Version': Config.APP_VERSION || '1.0.0',
      },
    });

    this.setupInterceptors();
  }

  public static getInstance(): SecurityServiceClient {
    if (!SecurityServiceClient.instance) {
      SecurityServiceClient.instance = new SecurityServiceClient();
    }
    return SecurityServiceClient.instance;
  }

  /**
   * Set access token for authenticated requests
   */
  setAccessToken(token: string): void {
    this.accessToken = token;
    this.axiosInstance.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  }

  /**
   * Clear access token
   */
  clearAccessToken(): void {
    this.accessToken = null;
    delete this.axiosInstance.defaults.headers.common['Authorization'];
  }

  /**
   * Register biometric authentication for a user
   */
  async registerBiometric(request: BiometricRegistrationRequest): Promise<{ success: boolean; keyId: string }> {
    try {
      console.log('Registering biometric authentication for user:', request.userId);
      
      const response: AxiosResponse = await this.axiosInstance.post(
        '/api/v1/security/biometric/register',
        {
          userId: request.userId,
          publicKey: request.publicKey,
          signature: request.signature,
          biometryType: request.biometryType,
          deviceFingerprint: request.deviceFingerprint,
          securityAssessment: request.securityAssessment,
          timestamp: Date.now(),
        }
      );

      const { success, keyId, message } = response.data;
      
      if (success) {
        console.log('Biometric registration successful, keyId:', keyId);
        return { success: true, keyId };
      } else {
        throw new BiometricError(
          BiometricAuthError.SYSTEM_ERROR,
          message || 'Registration failed',
          null,
          true,
          false
        );
      }
    } catch (error) {
      console.error('Biometric registration failed:', error);
      throw this.handleApiError(error, 'Failed to register biometric authentication');
    }
  }

  /**
   * Authenticate user using biometrics
   */
  async authenticateBiometric(request: BiometricAuthenticationRequest): Promise<BiometricVerificationResponse> {
    try {
      console.log('Authenticating biometric for user:', request.userId);
      
      const response: AxiosResponse = await this.axiosInstance.post(
        '/api/v1/security/biometric/authenticate',
        {
          userId: request.userId,
          challenge: request.challenge,
          signature: request.signature,
          biometryType: request.biometryType,
          deviceFingerprint: request.deviceFingerprint,
          timestamp: request.timestamp,
        }
      );

      const verificationResponse: BiometricVerificationResponse = response.data;
      
      if (verificationResponse.verified) {
        console.log('Biometric authentication successful');
      } else {
        console.warn('Biometric authentication failed:', verificationResponse.message);
      }
      
      return verificationResponse;
    } catch (error) {
      console.error('Biometric authentication failed:', error);
      throw this.handleApiError(error, 'Failed to authenticate biometric');
    }
  }

  /**
   * Verify device trustworthiness
   */
  async verifyDevice(deviceFingerprint: DeviceFingerprint): Promise<{ trusted: boolean; score: number }> {
    try {
      console.log('Verifying device trustworthiness');
      
      const response: AxiosResponse = await this.axiosInstance.post(
        '/api/v1/security/device/verify',
        {
          deviceFingerprint,
          timestamp: Date.now(),
        }
      );

      const { trusted, score, reasons } = response.data;
      
      if (!trusted) {
        console.warn('Device not trusted, score:', score, 'reasons:', reasons);
      }
      
      return { trusted, score };
    } catch (error) {
      console.error('Device verification failed:', error);
      throw this.handleApiError(error, 'Failed to verify device');
    }
  }

  /**
   * Report security event to backend
   */
  async reportSecurityEvent(event: BiometricEvent): Promise<void> {
    try {
      console.log('Reporting security event:', event.eventType);
      
      await this.axiosInstance.post(
        '/api/v1/security/events',
        event
      );
      
      console.log('Security event reported successfully');
    } catch (error) {
      console.error('Failed to report security event:', error);
      // Don't throw error for event reporting failures
      // as it shouldn't block the main flow
    }
  }

  /**
   * Get security settings for user
   */
  async getSecuritySettings(userId: string): Promise<BiometricSettings> {
    try {
      console.log('Getting security settings for user:', userId);
      
      const response: AxiosResponse = await this.axiosInstance.get(
        `/api/v1/security/settings/${userId}`
      );

      return response.data;
    } catch (error) {
      console.error('Failed to get security settings:', error);
      throw this.handleApiError(error, 'Failed to retrieve security settings');
    }
  }

  /**
   * Update security settings for user
   */
  async updateSecuritySettings(userId: string, settings: Partial<BiometricSettings>): Promise<void> {
    try {
      console.log('Updating security settings for user:', userId);
      
      await this.axiosInstance.put(
        `/api/v1/security/settings/${userId}`,
        {
          settings,
          timestamp: Date.now(),
        }
      );
      
      console.log('Security settings updated successfully');
    } catch (error) {
      console.error('Failed to update security settings:', error);
      throw this.handleApiError(error, 'Failed to update security settings');
    }
  }

  /**
   * Get biometric challenge for authentication
   */
  async getBiometricChallenge(userId: string): Promise<{ challenge: string; expiresAt: number }> {
    try {
      console.log('Getting biometric challenge for user:', userId);
      
      const response: AxiosResponse = await this.axiosInstance.post(
        '/api/v1/security/challenge',
        {
          userId,
          timestamp: Date.now(),
        }
      );

      const { challenge, expiresAt } = response.data;
      return { challenge, expiresAt };
    } catch (error) {
      console.error('Failed to get biometric challenge:', error);
      throw this.handleApiError(error, 'Failed to get authentication challenge');
    }
  }

  /**
   * Revoke biometric authentication
   */
  async revokeBiometric(userId: string, reason?: string): Promise<{ success: boolean }> {
    try {
      console.log('Revoking biometric authentication for user:', userId);
      
      const response: AxiosResponse = await this.axiosInstance.delete(
        `/api/v1/security/biometric/${userId}`,
        {
          data: {
            reason: reason || 'User requested revocation',
            timestamp: Date.now(),
          }
        }
      );

      return response.data;
    } catch (error) {
      console.error('Failed to revoke biometric authentication:', error);
      throw this.handleApiError(error, 'Failed to revoke biometric authentication');
    }
  }

  /**
   * Get user's biometric authentication history
   */
  async getBiometricHistory(userId: string, limit: number = 50): Promise<BiometricEvent[]> {
    try {
      console.log('Getting biometric history for user:', userId);
      
      const response: AxiosResponse = await this.axiosInstance.get(
        `/api/v1/security/biometric/${userId}/history`,
        {
          params: { limit }
        }
      );

      return response.data.events || [];
    } catch (error) {
      console.error('Failed to get biometric history:', error);
      throw this.handleApiError(error, 'Failed to retrieve biometric history');
    }
  }

  /**
   * Check if user has biometric authentication enabled
   */
  async checkBiometricStatus(userId: string): Promise<{ 
    enabled: boolean; 
    biometryType?: string; 
    enrolledAt?: number;
    lastUsed?: number;
  }> {
    try {
      console.log('Checking biometric status for user:', userId);
      
      const response: AxiosResponse = await this.axiosInstance.get(
        `/api/v1/security/biometric/${userId}/status`
      );

      return response.data;
    } catch (error) {
      console.error('Failed to check biometric status:', error);
      throw this.handleApiError(error, 'Failed to check biometric status');
    }
  }

  /**
   * Refresh authentication token
   */
  async refreshAuthToken(): Promise<{ token: string; expiresIn: number }> {
    try {
      console.log('Refreshing authentication token');
      
      const refreshToken = await this.getStoredRefreshToken();
      if (!refreshToken) {
        throw new BiometricError(
          BiometricAuthError.TOKEN_EXPIRED,
          'No refresh token available',
          null,
          false,
          true
        );
      }

      const response: AxiosResponse = await this.axiosInstance.post(
        '/api/v1/auth/refresh',
        {
          refreshToken,
        }
      );

      const { accessToken, expiresIn } = response.data;
      this.setAccessToken(accessToken);

      return { token: accessToken, expiresIn };
    } catch (error) {
      console.error('Token refresh failed:', error);
      throw this.handleApiError(error, 'Failed to refresh authentication token');
    }
  }

  /**
   * Health check for security service
   */
  async healthCheck(): Promise<{ healthy: boolean; latency: number }> {
    const startTime = Date.now();
    
    try {
      await this.axiosInstance.get('/api/v1/security/health');
      const latency = Date.now() - startTime;
      
      return { healthy: true, latency };
    } catch (error) {
      const latency = Date.now() - startTime;
      console.error('Security service health check failed:', error);
      
      return { healthy: false, latency };
    }
  }

  /**
   * Private helper methods
   */
  private setupInterceptors(): void {
    // Request interceptor
    this.axiosInstance.interceptors.request.use(
      (config: AxiosRequestConfig) => {
        // Add timestamp to all requests
        config.headers = {
          ...config.headers,
          'X-Request-Timestamp': Date.now().toString(),
        };

        // Add device info if available
        if (config.data) {
          config.data.clientInfo = {
            platform: 'react-native',
            timestamp: Date.now(),
          };
        }

        return config;
      },
      (error) => {
        console.error('Request interceptor error:', error);
        return Promise.reject(error);
      }
    );

    // Response interceptor
    this.axiosInstance.interceptors.response.use(
      (response: AxiosResponse) => {
        return response;
      },
      async (error) => {
        const originalRequest = error.config;

        // Handle 401 errors with token refresh
        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          try {
            const { token } = await this.refreshAuthToken();
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return this.axiosInstance(originalRequest);
          } catch (refreshError) {
            // Refresh failed, clear token and redirect to login
            this.clearAccessToken();
            throw refreshError;
          }
        }

        return Promise.reject(error);
      }
    );
  }

  private handleApiError(error: any, defaultMessage: string): BiometricError {
    if (error.response) {
      const { status, data } = error.response;
      
      switch (status) {
        case 400:
          return new BiometricError(
            BiometricAuthError.SYSTEM_ERROR,
            data.message || 'Bad request',
            error,
            true,
            false
          );
        case 401:
          return new BiometricError(
            BiometricAuthError.TOKEN_EXPIRED,
            'Authentication required',
            error,
            true,
            true
          );
        case 403:
          return new BiometricError(
            BiometricAuthError.DEVICE_NOT_TRUSTED,
            'Access forbidden',
            error,
            false,
            true
          );
        case 404:
          return new BiometricError(
            BiometricAuthError.SYSTEM_ERROR,
            'Resource not found',
            error,
            false,
            false
          );
        case 429:
          return new BiometricError(
            BiometricAuthError.TEMPORARILY_LOCKED,
            'Too many requests',
            error,
            true,
            true
          );
        case 500:
        case 502:
        case 503:
        case 504:
          return new BiometricError(
            BiometricAuthError.SYSTEM_ERROR,
            'Server error',
            error,
            true,
            false
          );
        default:
          return new BiometricError(
            BiometricAuthError.SYSTEM_ERROR,
            data.message || defaultMessage,
            error,
            true,
            false
          );
      }
    } else if (error.request) {
      return new BiometricError(
        BiometricAuthError.NETWORK_ERROR,
        'Network error occurred',
        error,
        true,
        false
      );
    } else {
      return new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        defaultMessage,
        error,
        true,
        false
      );
    }
  }

  private async getStoredRefreshToken(): Promise<string | null> {
    try {
      // This would typically get the refresh token from secure storage
      // For now, return null as placeholder
      return null;
    } catch (error) {
      console.error('Failed to get stored refresh token:', error);
      return null;
    }
  }

  /**
   * Get service configuration
   */
  getConfiguration(): {
    baseURL: string;
    timeout: number;
    hasToken: boolean;
  } {
    return {
      baseURL: this.baseURL,
      timeout: this.timeout,
      hasToken: !!this.accessToken,
    };
  }

  /**
   * Test connection to security service
   */
  async testConnection(): Promise<{ 
    connected: boolean; 
    latency: number; 
    version?: string; 
    error?: string; 
  }> {
    const startTime = Date.now();
    
    try {
      const response = await this.axiosInstance.get('/api/v1/security/version', {
        timeout: 5000,
      });
      
      const latency = Date.now() - startTime;
      
      return {
        connected: true,
        latency,
        version: response.data.version || 'unknown',
      };
    } catch (error) {
      const latency = Date.now() - startTime;
      
      return {
        connected: false,
        latency,
        error: error.message || 'Connection failed',
      };
    }
  }
}

export default SecurityServiceClient;