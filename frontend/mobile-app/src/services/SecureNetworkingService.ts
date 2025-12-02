/**
 * Secure Networking Service
 * 
 * Integrates certificate pinning with all network requests
 * and provides additional security features.
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import CertificatePinningService from './CertificatePinningService';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import CryptoJS from 'crypto-js';
import AppConfigService from './AppConfigService';

interface SecurityConfig {
  enableCertificatePinning: boolean;
  enableRequestSigning: boolean;
  enableResponseValidation: boolean;
  enableJailbreakDetection: boolean;
  maxRetries: number;
  timeout: number;
}

interface RequestSignature {
  timestamp: number;
  nonce: string;
  signature: string;
}

class SecureNetworkingService {
  private static instance: SecureNetworkingService;
  private axiosInstance: AxiosInstance;
  private securityConfig: SecurityConfig;
  private sessionKey: string | null = null;
  
  private readonly DEFAULT_CONFIG: SecurityConfig = {
    enableCertificatePinning: true,
    enableRequestSigning: true,
    enableResponseValidation: true,
    enableJailbreakDetection: true,
    maxRetries: 3,
    timeout: 30000,
  };

  private constructor() {
    this.securityConfig = this.DEFAULT_CONFIG;
    this.axiosInstance = this.createSecureAxiosInstance();
    this.setupInterceptors();
  }

  public static getInstance(): SecureNetworkingService {
    if (!SecureNetworkingService.instance) {
      SecureNetworkingService.instance = new SecureNetworkingService();
    }
    return SecureNetworkingService.instance;
  }

  /**
   * Initialize secure networking with session key
   */
  public async initialize(sessionKey?: string): Promise<void> {
    try {
      // Initialize certificate pinning
      await CertificatePinningService.initialize();
      
      // Set or generate session key
      if (sessionKey) {
        this.sessionKey = sessionKey;
      } else {
        this.sessionKey = await this.generateSessionKey();
      }
      
      // Check for jailbreak/root if enabled
      if (this.securityConfig.enableJailbreakDetection) {
        const isCompromised = await this.checkDeviceSecurity();
        if (isCompromised) {
          throw new Error('Device security compromised');
        }
      }
    } catch (error) {
      console.error('Secure networking initialization failed:', error);
      throw error;
    }
  }

  /**
   * Create a secure axios instance with certificate pinning
   */
  private createSecureAxiosInstance(): AxiosInstance {
    const instance = axios.create({
      timeout: this.securityConfig.timeout,
      headers: {
        'Content-Type': 'application/json',
        'X-App-Platform': Platform.OS,
        // App version will be set dynamically in request interceptor
      },
    });

    // Add certificate validation adapter for React Native
    if (Platform.OS === 'android') {
      // On Android, we need to use the native module's pinned client
      // This would require a custom network adapter
      this.configureAndroidNetworking(instance);
    } else if (Platform.OS === 'ios') {
      // iOS handles this through NSURLSession delegate
      this.configureIOSNetworking(instance);
    }

    return instance;
  }

  /**
   * Set up request and response interceptors
   */
  private setupInterceptors(): void {
    // Request interceptor
    this.axiosInstance.interceptors.request.use(
      async (config) => {
        try {
          // Add security headers
          config.headers = config.headers || {};
          
          // Add app version headers from AppConfigService
          try {
            const appHeaders = await AppConfigService.getAppHeaders();
            Object.assign(config.headers, appHeaders);
          } catch (error) {
            console.warn('Failed to add app headers:', error);
            // Fallback headers
            config.headers['X-App-Version'] = '1.0.0';
            config.headers['X-App-Platform'] = Platform.OS;
          }
          
          // Add request timestamp
          config.headers['X-Request-Timestamp'] = Date.now().toString();
          
          // Add auth token if available
          const authToken = await AsyncStorage.getItem('auth_token');
          if (authToken) {
            config.headers['Authorization'] = `Bearer ${authToken}`;
          }
          
          // Sign request if enabled
          if (this.securityConfig.enableRequestSigning && this.sessionKey) {
            const signature = await this.signRequest(config);
            config.headers['X-Request-Signature'] = signature.signature;
            config.headers['X-Request-Nonce'] = signature.nonce;
          }
          
          // Validate certificate pinning for the hostname
          if (this.securityConfig.enableCertificatePinning && config.url) {
            const hostname = this.extractHostname(config.url);
            const testResult = await CertificatePinningService.testPinning(hostname);
            
            if (!testResult) {
              throw new Error(`Certificate pinning test failed for ${hostname}`);
            }
          }
          
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
      async (response) => {
        try {
          // Validate response if enabled
          if (this.securityConfig.enableResponseValidation) {
            await this.validateResponse(response);
          }
          
          return response;
        } catch (error) {
          console.error('Response validation error:', error);
          return Promise.reject(error);
        }
      },
      async (error: AxiosError) => {
        // Handle certificate pinning failures
        if (error.code === 'PINNING_FAILED') {
          console.error('Certificate pinning failed:', error.message);
          
          // Report to security monitoring
          await this.reportSecurityIncident('certificate_pinning_failure', {
            url: error.config?.url,
            message: error.message,
          });
        }
        
        // Retry logic for certain errors
        if (this.shouldRetry(error) && error.config) {
          return this.retryRequest(error.config);
        }
        
        return Promise.reject(error);
      }
    );
  }

  /**
   * Make a secure GET request
   */
  public async get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.axiosInstance.get<T>(url, config);
    return response.data;
  }

  /**
   * Make a secure POST request
   */
  public async post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.axiosInstance.post<T>(url, data, config);
    return response.data;
  }

  /**
   * Make a secure PUT request
   */
  public async put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.axiosInstance.put<T>(url, data, config);
    return response.data;
  }

  /**
   * Make a secure DELETE request
   */
  public async delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.axiosInstance.delete<T>(url, config);
    return response.data;
  }

  /**
   * Update security configuration
   */
  public updateSecurityConfig(config: Partial<SecurityConfig>): void {
    this.securityConfig = { ...this.securityConfig, ...config };
  }

  // Private helper methods

  private async signRequest(config: AxiosRequestConfig): Promise<RequestSignature> {
    const timestamp = Date.now();
    const nonce = this.generateNonce();
    
    // Create signature payload
    const payload = {
      method: config.method?.toUpperCase(),
      url: config.url,
      timestamp,
      nonce,
      body: config.data ? JSON.stringify(config.data) : '',
    };
    
    // Generate HMAC signature
    const signature = CryptoJS.HmacSHA256(
      JSON.stringify(payload),
      this.sessionKey!
    ).toString(CryptoJS.enc.Base64);
    
    return { timestamp, nonce, signature };
  }

  private async validateResponse(response: AxiosResponse): Promise<void> {
    // Validate response signature if present
    const responseSignature = response.headers['x-response-signature'];
    if (responseSignature && this.sessionKey) {
      const payload = {
        status: response.status,
        timestamp: response.headers['x-response-timestamp'],
        body: JSON.stringify(response.data),
      };
      
      const expectedSignature = CryptoJS.HmacSHA256(
        JSON.stringify(payload),
        this.sessionKey
      ).toString(CryptoJS.enc.Base64);
      
      if (responseSignature !== expectedSignature) {
        throw new Error('Response signature validation failed');
      }
    }
    
    // Validate response timestamp (prevent replay attacks)
    const responseTimestamp = parseInt(response.headers['x-response-timestamp'] || '0');
    const currentTimestamp = Date.now();
    const maxTimeDiff = 5 * 60 * 1000; // 5 minutes
    
    if (Math.abs(currentTimestamp - responseTimestamp) > maxTimeDiff) {
      throw new Error('Response timestamp validation failed');
    }
  }

  private shouldRetry(error: AxiosError): boolean {
    // Don't retry if no config or max retries reached
    if (!error.config || (error.config as any).retryCount >= this.securityConfig.maxRetries) {
      return false;
    }
    
    // Retry on network errors or specific status codes
    if (!error.response) {
      return true; // Network error
    }
    
    const retryStatusCodes = [408, 429, 500, 502, 503, 504];
    return retryStatusCodes.includes(error.response.status);
  }

  private async retryRequest(config: AxiosRequestConfig): Promise<AxiosResponse> {
    // Increment retry count
    (config as any).retryCount = ((config as any).retryCount || 0) + 1;
    
    // Exponential backoff
    const delay = Math.min(1000 * Math.pow(2, (config as any).retryCount - 1), 10000);
    await new Promise(resolve => setTimeout(resolve, delay));
    
    return this.axiosInstance.request(config);
  }

  private extractHostname(url: string): string {
    try {
      const urlObj = new URL(url);
      return urlObj.hostname;
    } catch {
      // If URL parsing fails, try to extract from the string
      const match = url.match(/^https?:\/\/([^\/]+)/);
      return match ? match[1] : '';
    }
  }

  private generateNonce(): string {
    return CryptoJS.lib.WordArray.random(16).toString(CryptoJS.enc.Base64);
  }

  private async generateSessionKey(): Promise<string> {
    // Generate a random session key
    const sessionKey = CryptoJS.lib.WordArray.random(32).toString(CryptoJS.enc.Base64);
    
    // Store securely
    await AsyncStorage.setItem('session_key', sessionKey);
    
    return sessionKey;
  }

  private async checkDeviceSecurity(): Promise<boolean> {
    try {
      // Import jail-monkey for jailbreak/root detection
      const JailMonkey = require('jail-monkey');
      
      // Check for jailbreak/root
      const isJailBroken = JailMonkey.isJailBroken();
      const isRooted = JailMonkey.isRooted();
      const isDebugged = JailMonkey.isDebugged();
      const hasHooks = JailMonkey.hookDetected();
      
      // Check for dangerous apps/packages
      const hasDangerousApps = JailMonkey.canMockLocation();
      
      // Additional checks using DeviceInfo
      const DeviceInfo = require('react-native-device-info');
      const isEmulator = await DeviceInfo.isEmulator();
      
      // Log security findings (but don't log sensitive info in production)
      if (__DEV__) {
        console.log('Device Security Check:', {
          isJailBroken,
          isRooted,
          isDebugged,
          hasHooks,
          hasDangerousApps,
          isEmulator,
        });
      }
      
      // Device is considered secure if none of these conditions are true
      const isSecure = !isJailBroken && 
                      !isRooted && 
                      !isDebugged && 
                      !hasHooks && 
                      !hasDangerousApps &&
                      !isEmulator;
      
      // Report security status to backend for monitoring
      if (!isSecure) {
        await this.reportSecurityIssue({
          type: 'device_security',
          details: {
            jailbroken: isJailBroken,
            rooted: isRooted,
            debugged: isDebugged,
            hooked: hasHooks,
            mockLocation: hasDangerousApps,
            emulator: isEmulator,
          },
          timestamp: new Date().toISOString(),
          severity: 'high',
        });
      }
      
      return isSecure;
      
    } catch (error) {
      console.error('Device security check failed:', error);
      
      // If security check fails, assume device is NOT secure
      // This is a fail-safe approach for financial applications
      await this.reportSecurityIssue({
        type: 'security_check_failure',
        details: { error: error.message },
        timestamp: new Date().toISOString(),
        severity: 'high',
      });
      
      return false;
    }
  }


  private async reportSecurityIncident(type: string, details: any): Promise<void> {
    try {
      // Report security incidents to monitoring service
      console.error(`Security incident: ${type}`, details);
      
      // Send to security monitoring endpoint
      const securityEndpoint = await this.getSecurityEndpoint();
      if (securityEndpoint) {
        await axios.post(
          securityEndpoint,
          {
            type,
            details,
            timestamp: new Date().toISOString(),
            deviceId: await this.getDeviceId(),
            platform: Platform.OS,
          },
          {
            timeout: 5000,
            headers: {
              'X-Security-Report': 'true',
            },
          }
        ).catch(err => {
          console.error('Failed to send security incident:', err);
        });
      }
    } catch (error) {
      console.error('Failed to report security incident:', error);
    }
  }

  private async reportSecurityIssue(details: any): Promise<void> {
    try {
      console.error('Security issue detected:', details);
      
      // Send to backend for monitoring
      await this.reportSecurityIncident('device_security_issue', details);
      
      // Store locally for audit trail
      await this.storeSecurityAuditLog(details);
      
    } catch (error) {
      console.error('Failed to report security issue:', error);
    }
  }

  private configureAndroidNetworking(instance: AxiosInstance): void {
    // Android-specific networking configuration with certificate pinning
    try {
      const NativeModules = require('react-native').NativeModules;
      const { CertificatePinningModule } = NativeModules;
      
      if (CertificatePinningModule) {
        // Configure OkHttp client with certificate pinning
        CertificatePinningModule.configurePinnedClient({
          hosts: [
            {
              hostname: 'api.example.com',
              pins: [
                'sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=',
                'sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=', // Backup pin
              ],
            },
            {
              hostname: 'auth.example.com',
              pins: [
                'sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=',
                'sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=', // Backup pin
              ],
            },
          ],
          maxAge: 5184000, // 60 days
          includeSubdomains: true,
          enforceMode: true,
        });
      }
    } catch (error) {
      console.error('Failed to configure Android networking:', error);
    }
  }

  private configureIOSNetworking(instance: AxiosInstance): void {
    // iOS-specific networking configuration with certificate pinning
    try {
      const NativeModules = require('react-native').NativeModules;
      const { CertificatePinningModule } = NativeModules;
      
      if (CertificatePinningModule) {
        // Configure NSURLSession with certificate pinning
        CertificatePinningModule.configurePinnedSession({
          domains: [
            {
              domain: 'api.example.com',
              pins: [
                'sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=',
                'sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=', // Backup pin
              ],
              includeSubdomains: true,
            },
            {
              domain: 'auth.example.com',
              pins: [
                'sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=',
                'sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=', // Backup pin
              ],
              includeSubdomains: true,
            },
          ],
          validateDomainName: true,
          sslPinningMode: 'PublicKeyHash',
          requireCertificateTransparency: true,
        });
      }
    } catch (error) {
      console.error('Failed to configure iOS networking:', error);
    }
  }

  // Additional security helper methods
  private async getSecurityEndpoint(): Promise<string | null> {
    try {
      const config = await AppConfigService.getAppConfig();
      return config?.securityEndpoint || null;
    } catch {
      return null;
    }
  }

  private async getDeviceId(): Promise<string> {
    try {
      const DeviceInfo = require('react-native-device-info');
      return await DeviceInfo.getUniqueId();
    } catch {
      return 'unknown';
    }
  }

  private async storeSecurityAuditLog(details: any): Promise<void> {
    try {
      const existingLogs = await AsyncStorage.getItem('security_audit_logs');
      const logs = existingLogs ? JSON.parse(existingLogs) : [];
      
      // Keep only last 100 entries
      if (logs.length >= 100) {
        logs.shift();
      }
      
      logs.push({
        ...details,
        timestamp: new Date().toISOString(),
      });
      
      await AsyncStorage.setItem('security_audit_logs', JSON.stringify(logs));
    } catch (error) {
      console.error('Failed to store security audit log:', error);
    }
  }

  /**
   * Verify certificate pin manually for critical operations
   */
  public async verifyCertificatePin(hostname: string): Promise<boolean> {
    try {
      // Test the certificate pinning for the given hostname
      const result = await CertificatePinningService.testPinning(hostname);
      
      if (!result) {
        await this.reportSecurityIncident('manual_pin_verification_failed', {
          hostname,
          timestamp: new Date().toISOString(),
        });
      }
      
      return result;
    } catch (error) {
      console.error('Certificate pin verification failed:', error);
      return false;
    }
  }

  /**
   * Update certificate pins dynamically (with security checks)
   */
  public async updateCertificatePins(updates: any, adminToken: string): Promise<boolean> {
    try {
      // Verify admin token before allowing pin updates
      const isValidAdmin = await this.verifyAdminToken(adminToken);
      if (!isValidAdmin) {
        throw new Error('Invalid admin token for pin update');
      }
      
      // Update pins in the pinning service
      await CertificatePinningService.updatePins(updates);
      
      // Re-initialize networking with new pins
      this.axiosInstance = this.createSecureAxiosInstance();
      this.setupInterceptors();
      
      // Log the update for audit
      await this.reportSecurityIncident('certificate_pins_updated', {
        updateCount: Object.keys(updates).length,
        timestamp: new Date().toISOString(),
      });
      
      return true;
    } catch (error) {
      console.error('Failed to update certificate pins:', error);
      return false;
    }
  }

  private async verifyAdminToken(token: string): Promise<boolean> {
    try {
      // Verify the admin token with backend
      const response = await this.post('/api/v1/admin/verify', { token });
      return response.valid === true;
    } catch {
      return false;
    }
  }

  /**
   * Get current certificate pinning status
   */
  public async getPinningStatus(): Promise<any> {
    try {
      const status = await CertificatePinningService.getStatus();
      return {
        ...status,
        securityConfig: this.securityConfig,
        platform: Platform.OS,
      };
    } catch (error) {
      console.error('Failed to get pinning status:', error);
      return null;
    }
  }
}

export default SecureNetworkingService.getInstance();