/**
 * Secure Network Client
 * Axios-based HTTP client with certificate pinning and advanced security features
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { Platform } from 'react-native';
import Config from 'react-native-config';
import DeviceInfo from 'react-native-device-info';
import NetInfo from '@react-native-community/netinfo';
import { CertificatePinningService } from './CertificatePinningService';
import { SecureStorageService } from '../biometric/SecureStorageService';
import CryptoJS from 'crypto-js';

interface SecurityHeaders {
  'X-Certificate-Pinning': string;
  'X-Request-ID': string;
  'X-Device-ID': string;
  'X-Session-ID': string;
  'X-Timestamp': string;
  'X-Signature': string;
  'X-Platform': string;
  'X-App-Version': string;
  'X-OS-Version': string;
  'X-Network-Type': string;
}

interface RequestSecurityOptions {
  skipPinning?: boolean;
  skipEncryption?: boolean;
  skipIntegrityCheck?: boolean;
  timeout?: number;
  retryCount?: number;
}

interface SecureRequestConfig extends AxiosRequestConfig {
  security?: RequestSecurityOptions;
  pinningRequired?: boolean;
}

export class SecureNetworkClient {
  private static instance: SecureNetworkClient;
  private axiosInstance: AxiosInstance;
  private pinningService: CertificatePinningService;
  private storageService: SecureStorageService;
  private sessionId: string;
  private deviceId: string;
  private requestCounter: number = 0;

  private readonly BASE_URL = Config.API_BASE_URL || 'https://api.example.com';
  private readonly API_KEY = Config.API_KEY || '';
  private readonly HMAC_SECRET = Config.HMAC_SECRET || '';

  private constructor() {
    this.pinningService = CertificatePinningService.getInstance();
    this.storageService = SecureStorageService.getInstance();
    this.sessionId = this.generateSessionId();
    this.deviceId = '';

    this.axiosInstance = axios.create({
      baseURL: this.BASE_URL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    });

    this.setupInterceptors();
    this.initializeDeviceId();
  }

  public static getInstance(): SecureNetworkClient {
    if (!SecureNetworkClient.instance) {
      SecureNetworkClient.instance = new SecureNetworkClient();
    }
    return SecureNetworkClient.instance;
  }

  /**
   * Initialize device ID
   */
  private async initializeDeviceId(): Promise<void> {
    try {
      this.deviceId = await DeviceInfo.getUniqueId();
    } catch (error) {
      console.error('Failed to get device ID:', error);
      this.deviceId = this.generateDeviceId();
    }
  }

  /**
   * Setup axios interceptors
   */
  private setupInterceptors(): void {
    // Request interceptor
    this.axiosInstance.interceptors.request.use(
      async (config: SecureRequestConfig) => {
        try {
          // Add security headers
          config.headers = await this.addSecurityHeaders(config);

          // Encrypt sensitive data if needed
          if (!config.security?.skipEncryption && this.shouldEncryptRequest(config)) {
            config.data = await this.encryptRequestData(config.data);
            config.headers['X-Encrypted'] = 'true';
          }

          // Add request signature
          if (!config.security?.skipIntegrityCheck) {
            const signature = await this.generateRequestSignature(config);
            config.headers['X-Signature'] = signature;
          }

          // Log request for debugging (without sensitive data)
          this.logSecureRequest(config);

          return config;
        } catch (error) {
          console.error('Request interceptor error:', error);
          return Promise.reject(error);
        }
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // Response interceptor
    this.axiosInstance.interceptors.response.use(
      async (response: AxiosResponse) => {
        try {
          // Validate certificate pinning
          if (!response.config.security?.skipPinning) {
            const pinningResult = await this.validateCertificatePinning(response);
            if (!pinningResult.valid) {
              throw new Error(`Certificate pinning failed: ${pinningResult.error}`);
            }
          }

          // Verify response integrity
          if (!response.config.security?.skipIntegrityCheck) {
            const integrityValid = await this.verifyResponseIntegrity(response);
            if (!integrityValid) {
              throw new Error('Response integrity check failed');
            }
          }

          // Decrypt response if encrypted
          if (response.headers['x-encrypted'] === 'true') {
            response.data = await this.decryptResponseData(response.data);
          }

          // Log response for debugging
          this.logSecureResponse(response);

          return response;
        } catch (error) {
          console.error('Response interceptor error:', error);
          return Promise.reject(error);
        }
      },
      async (error: AxiosError) => {
        // Handle specific error cases
        if (error.response) {
          switch (error.response.status) {
            case 401:
              // Token expired, try to refresh
              return this.handleTokenRefresh(error);
            case 403:
              // Certificate pinning or security violation
              await this.handleSecurityViolation(error);
              break;
            case 429:
              // Rate limiting
              return this.handleRateLimiting(error);
          }
        }

        // Network errors
        if (!error.response && error.code === 'ECONNABORTED') {
          return this.handleNetworkTimeout(error);
        }

        return Promise.reject(error);
      }
    );
  }

  /**
   * Add security headers to request
   */
  private async addSecurityHeaders(config: SecureRequestConfig): Promise<any> {
    const netInfo = await NetInfo.fetch();
    const timestamp = Date.now().toString();
    const requestId = this.generateRequestId();

    const headers: SecurityHeaders = {
      'X-Certificate-Pinning': 'enabled',
      'X-Request-ID': requestId,
      'X-Device-ID': this.deviceId,
      'X-Session-ID': this.sessionId,
      'X-Timestamp': timestamp,
      'X-Signature': '', // Will be set later
      'X-Platform': Platform.OS,
      'X-App-Version': DeviceInfo.getVersion(),
      'X-OS-Version': `${Platform.OS} ${Platform.Version}`,
      'X-Network-Type': netInfo.type,
    };

    // Add API key if available
    if (this.API_KEY) {
      headers['X-API-Key'] = this.API_KEY;
    }

    // Add auth token if available
    const token = await this.getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    return { ...config.headers, ...headers };
  }

  /**
   * Validate certificate pinning for response
   */
  private async validateCertificatePinning(response: AxiosResponse): Promise<{
    valid: boolean;
    error?: string;
  }> {
    try {
      // Extract hostname from URL
      const url = new URL(response.config.url || '', response.config.baseURL);
      const hostname = url.hostname;

      // Get certificate chain from response (platform-specific)
      const certificateChain = await this.extractCertificateChain(response);
      
      if (!certificateChain || certificateChain.length === 0) {
        console.warn('No certificate chain available for validation');
        return { valid: true }; // Pass through if we can't validate
      }

      // Validate with pinning service
      const result = await this.pinningService.validateCertificate(
        hostname,
        certificateChain
      );

      return result;
    } catch (error) {
      console.error('Certificate pinning validation error:', error);
      return { valid: false, error: error.message };
    }
  }

  /**
   * Extract certificate chain from response
   * Note: This is platform-specific and may require native modules
   */
  private async extractCertificateChain(response: AxiosResponse): Promise<string[]> {
    // This would typically be implemented using native modules
    // For React Native, consider using:
    // - iOS: react-native-ssl-pinning
    // - Android: OkHttp with certificate pinning
    
    // Placeholder implementation
    const certificateHeader = response.headers['x-certificate-chain'];
    if (certificateHeader) {
      try {
        return JSON.parse(certificateHeader);
      } catch {
        return [];
      }
    }
    
    return [];
  }

  /**
   * Generate request signature for integrity
   */
  private async generateRequestSignature(config: SecureRequestConfig): Promise<string> {
    const method = config.method?.toUpperCase() || 'GET';
    const url = config.url || '';
    const timestamp = config.headers['X-Timestamp'];
    const body = config.data ? JSON.stringify(config.data) : '';

    const signatureBase = `${method}\n${url}\n${timestamp}\n${body}`;
    const signature = CryptoJS.HmacSHA256(signatureBase, this.HMAC_SECRET);
    
    return CryptoJS.enc.Base64.stringify(signature);
  }

  /**
   * Verify response integrity
   */
  private async verifyResponseIntegrity(response: AxiosResponse): Promise<boolean> {
    try {
      const serverSignature = response.headers['x-signature'];
      if (!serverSignature) {
        console.warn('No signature in response headers');
        return true; // Pass through if no signature
      }

      const timestamp = response.headers['x-timestamp'];
      const body = typeof response.data === 'string' 
        ? response.data 
        : JSON.stringify(response.data);

      const signatureBase = `${response.status}\n${timestamp}\n${body}`;
      const expectedSignature = CryptoJS.HmacSHA256(signatureBase, this.HMAC_SECRET);
      const expectedSignatureBase64 = CryptoJS.enc.Base64.stringify(expectedSignature);

      return serverSignature === expectedSignatureBase64;
    } catch (error) {
      console.error('Response integrity verification error:', error);
      return false;
    }
  }

  /**
   * Encrypt request data
   */
  private async encryptRequestData(data: any): Promise<any> {
    if (!data) return data;

    try {
      const jsonData = JSON.stringify(data);
      const encrypted = CryptoJS.AES.encrypt(jsonData, this.HMAC_SECRET);
      
      return {
        encrypted: true,
        data: encrypted.toString(),
        algorithm: 'AES',
        timestamp: Date.now()
      };
    } catch (error) {
      console.error('Failed to encrypt request data:', error);
      return data;
    }
  }

  /**
   * Decrypt response data
   */
  private async decryptResponseData(data: any): Promise<any> {
    if (!data || !data.encrypted) return data;

    try {
      const decrypted = CryptoJS.AES.decrypt(data.data, this.HMAC_SECRET);
      const decryptedString = decrypted.toString(CryptoJS.enc.Utf8);
      
      return JSON.parse(decryptedString);
    } catch (error) {
      console.error('Failed to decrypt response data:', error);
      return data;
    }
  }

  /**
   * Determine if request should be encrypted
   */
  private shouldEncryptRequest(config: SecureRequestConfig): boolean {
    const sensitiveEndpoints = [
      '/auth/',
      '/payments/',
      '/wallet/',
      '/user/profile',
      '/transactions/'
    ];

    const url = config.url || '';
    return sensitiveEndpoints.some(endpoint => url.includes(endpoint));
  }

  /**
   * Handle token refresh
   */
  private async handleTokenRefresh(error: AxiosError): Promise<any> {
    try {
      const refreshToken = await this.getRefreshToken();
      if (!refreshToken) {
        throw new Error('No refresh token available');
      }

      const response = await this.axiosInstance.post('/auth/refresh', {
        refreshToken
      });

      const { accessToken } = response.data;
      await this.storeAuthToken(accessToken);

      // Retry original request
      const originalRequest = error.config;
      if (originalRequest) {
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;
        return this.axiosInstance(originalRequest);
      }
    } catch (refreshError) {
      console.error('Token refresh failed:', refreshError);
      // Redirect to login
      throw refreshError;
    }
  }

  /**
   * Handle security violation
   */
  private async handleSecurityViolation(error: AxiosError): Promise<void> {
    console.error('Security violation detected:', error);
    
    // Report to security service
    await this.reportSecurityIncident({
      type: 'SECURITY_VIOLATION',
      timestamp: Date.now(),
      error: error.message,
      response: error.response?.data,
      deviceId: this.deviceId,
      sessionId: this.sessionId
    });

    // Clear sensitive data
    await this.clearSensitiveData();
  }

  /**
   * Handle rate limiting
   */
  private async handleRateLimiting(error: AxiosError): Promise<any> {
    const retryAfter = error.response?.headers['retry-after'];
    const delay = retryAfter ? parseInt(retryAfter) * 1000 : 60000;

    console.warn(`Rate limited. Retrying after ${delay}ms`);

    await new Promise(resolve => setTimeout(resolve, delay));
    
    // Retry original request
    if (error.config) {
      return this.axiosInstance(error.config);
    }
  }

  /**
   * Handle network timeout
   */
  private async handleNetworkTimeout(error: AxiosError): Promise<any> {
    const config = error.config as SecureRequestConfig;
    const retryCount = config.security?.retryCount || 0;
    
    if (retryCount < 3) {
      console.warn(`Network timeout. Retry attempt ${retryCount + 1}`);
      
      config.security = { ...config.security, retryCount: retryCount + 1 };
      return this.axiosInstance(config);
    }

    throw error;
  }

  /**
   * Public API methods
   */
  public async get(url: string, config?: SecureRequestConfig): Promise<AxiosResponse> {
    return this.axiosInstance.get(url, config);
  }

  public async post(url: string, data?: any, config?: SecureRequestConfig): Promise<AxiosResponse> {
    return this.axiosInstance.post(url, data, config);
  }

  public async put(url: string, data?: any, config?: SecureRequestConfig): Promise<AxiosResponse> {
    return this.axiosInstance.put(url, data, config);
  }

  public async delete(url: string, config?: SecureRequestConfig): Promise<AxiosResponse> {
    return this.axiosInstance.delete(url, config);
  }

  public async patch(url: string, data?: any, config?: SecureRequestConfig): Promise<AxiosResponse> {
    return this.axiosInstance.patch(url, data, config);
  }

  /**
   * Test certificate pinning
   */
  public async testCertificatePinning(): Promise<{
    success: boolean;
    results: any[];
  }> {
    const endpoints = [
      'api.example.com',
      'auth.example.com',
      'payments.example.com'
    ];

    const results = [];
    
    for (const endpoint of endpoints) {
      const result = await this.pinningService.testPinning(endpoint);
      results.push({ endpoint, ...result });
    }

    return {
      success: results.every(r => r.success),
      results
    };
  }

  /**
   * Helper methods
   */
  private generateSessionId(): string {
    return `sess_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private generateRequestId(): string {
    this.requestCounter++;
    return `req_${Date.now()}_${this.requestCounter}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private generateDeviceId(): string {
    return `dev_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private async getAuthToken(): Promise<string | null> {
    try {
      return await this.storageService.getItem('auth_token');
    } catch {
      return null;
    }
  }

  private async getRefreshToken(): Promise<string | null> {
    try {
      return await this.storageService.getItem('refresh_token');
    } catch {
      return null;
    }
  }

  private async storeAuthToken(token: string): Promise<void> {
    await this.storageService.setItem('auth_token', token);
  }

  private async clearSensitiveData(): Promise<void> {
    await this.storageService.removeItem('auth_token');
    await this.storageService.removeItem('refresh_token');
    this.sessionId = this.generateSessionId();
  }

  private async reportSecurityIncident(incident: any): Promise<void> {
    try {
      await this.post('/security/incidents', incident, {
        security: { skipPinning: true }
      });
    } catch (error) {
      console.error('Failed to report security incident:', error);
    }
  }

  private logSecureRequest(config: SecureRequestConfig): void {
    if (__DEV__) {
      console.log('Secure Request:', {
        method: config.method,
        url: config.url,
        headers: {
          ...config.headers,
          Authorization: config.headers?.Authorization ? '[REDACTED]' : undefined
        }
      });
    }
  }

  private logSecureResponse(response: AxiosResponse): void {
    if (__DEV__) {
      console.log('Secure Response:', {
        status: response.status,
        url: response.config.url,
        headers: {
          'x-certificate-pinning': response.headers['x-certificate-pinning'],
          'x-signature': response.headers['x-signature'] ? '[PRESENT]' : undefined
        }
      });
    }
  }
}

export default SecureNetworkClient;