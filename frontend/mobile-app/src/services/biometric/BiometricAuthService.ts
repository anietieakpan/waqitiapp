/**
 * Biometric Authentication Service
 * Main orchestrator for biometric authentication functionality
 */

import { 
  IBiometricAuthService,
  BiometricCapabilities,
  BiometricPromptConfig,
  BiometricAuthResult,
  BiometricSettings,
  SecurityAssessment,
  StoredBiometricData,
  BiometricToken,
  BiometricEvent,
  DeviceFingerprint,
  BiometricAuthError,
  SecurityLevel,
  AuthenticationMethod,
  BiometricError,
  BiometricConfig
} from './types';
import { BiometricService } from './BiometricService';
import { SecureStorageService } from './SecureStorageService';
import { DeviceFingerprintService } from './DeviceFingerprintService';
import { SecurityServiceClient } from './SecurityServiceClient';
import { BiometryType } from 'react-native-biometrics';
import Config from 'react-native-config';

export class BiometricAuthService implements IBiometricAuthService {
  private static instance: BiometricAuthService;
  
  private biometricService: BiometricService;
  private storageService: SecureStorageService;
  private fingerprintService: DeviceFingerprintService;
  private securityClient: SecurityServiceClient;
  
  private initialized: boolean = false;
  private config: BiometricConfig;

  constructor() {
    this.biometricService = BiometricService.getInstance();
    this.storageService = SecureStorageService.getInstance();
    this.fingerprintService = DeviceFingerprintService.getInstance();
    this.securityClient = SecurityServiceClient.getInstance();
    
    // Initialize configuration
    this.config = this.getDefaultConfig();
  }

  public static getInstance(): BiometricAuthService {
    if (!BiometricAuthService.instance) {
      BiometricAuthService.instance = new BiometricAuthService();
    }
    return BiometricAuthService.instance;
  }

  /**
   * Initialize the biometric authentication service
   */
  async initialize(): Promise<void> {
    try {
      console.log('Initializing BiometricAuthService...');
      
      // Check if already initialized
      if (this.initialized) {
        console.log('BiometricAuthService already initialized');
        return;
      }

      // Perform service health checks
      const [biometricCapabilities, securityHealth] = await Promise.all([
        this.biometricService.checkAvailability(),
        this.securityClient.healthCheck(),
      ]);

      console.log('Biometric capabilities:', biometricCapabilities);
      console.log('Security service health:', securityHealth);

      // Validate critical services
      if (!securityHealth.healthy) {
        console.warn('Security service is not healthy, some features may be limited');
      }

      // Initialize device fingerprint
      await this.fingerprintService.generateFingerprint();

      this.initialized = true;
      console.log('BiometricAuthService initialized successfully');

      // Report initialization event
      await this.reportEvent({
        eventType: 'INITIALIZATION',
        success: true,
        additionalData: {
          biometricAvailable: biometricCapabilities.isAvailable,
          biometryType: biometricCapabilities.biometryType,
          securityServiceHealthy: securityHealth.healthy,
        },
      });

    } catch (error) {
      console.error('Failed to initialize BiometricAuthService:', error);
      
      // Report initialization failure
      await this.reportEvent({
        eventType: 'INITIALIZATION',
        success: false,
        additionalData: { error: error.message },
      });

      throw new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        'Failed to initialize biometric authentication service',
        error,
        true,
        false
      );
    }
  }

  /**
   * Setup biometric authentication for a user
   */
  async setupBiometric(userId: string, promptConfig?: BiometricPromptConfig): Promise<BiometricAuthResult> {
    try {
      console.log('Setting up biometric authentication for user:', userId);
      
      await this.ensureInitialized();

      // Check if already setup
      const existingData = await this.storageService.getBiometricData(userId);
      if (existingData?.isActive) {
        console.log('Biometric authentication already setup for user');
        return {
          success: true,
          timestamp: Date.now(),
        };
      }

      // Check biometric availability
      const capabilities = await this.biometricService.checkAvailability();
      if (!capabilities.isAvailable) {
        return {
          success: false,
          error: BiometricAuthError.HARDWARE_NOT_AVAILABLE,
          errorMessage: 'Biometric authentication is not available on this device',
        };
      }

      if (!capabilities.isEnrolled) {
        return {
          success: false,
          error: BiometricAuthError.NOT_ENROLLED,
          errorMessage: 'Please set up biometric authentication in your device settings',
        };
      }

      // Generate device fingerprint and assess security
      const [deviceFingerprint, securityAssessment] = await Promise.all([
        this.fingerprintService.generateFingerprint(),
        this.fingerprintService.assessSecurity(),
      ]);

      // Check if device meets security requirements
      if (securityAssessment.riskLevel === SecurityLevel.CRITICAL) {
        return {
          success: false,
          error: BiometricAuthError.DEVICE_NOT_TRUSTED,
          errorMessage: 'Device does not meet security requirements for biometric authentication',
        };
      }

      // Generate key pair
      const keyAlias = this.generateKeyAlias(userId);
      const { publicKey } = await this.biometricService.generateKeyPair(keyAlias);

      // Create challenge for signature
      const challenge = this.generateChallenge();
      
      // Use default prompt config if not provided
      const config = promptConfig || this.getDefaultPromptConfig();
      
      // Authenticate to sign the challenge
      const authResult = await this.biometricService.authenticate(config, challenge);
      
      if (!authResult.success || !authResult.signature) {
        return authResult;
      }

      // Register with backend
      const registrationRequest = {
        userId,
        publicKey,
        signature: authResult.signature,
        biometryType: capabilities.biometryType as BiometryType,
        deviceFingerprint,
        securityAssessment,
      };

      const { success, keyId } = await this.securityClient.registerBiometric(registrationRequest);
      
      if (!success) {
        // Clean up generated keys
        await this.biometricService.deleteKey(keyAlias);
        return {
          success: false,
          error: BiometricAuthError.SYSTEM_ERROR,
          errorMessage: 'Failed to register biometric authentication with server',
        };
      }

      // Store biometric data locally
      const biometricData: StoredBiometricData = {
        userId,
        publicKey,
        keyAlias,
        biometryType: capabilities.biometryType as BiometryType,
        enrolledAt: Date.now(),
        deviceFingerprint: deviceFingerprint.hash,
        isActive: true,
        failureCount: 0,
        securityLevel: capabilities.securityLevel,
      };

      await this.storageService.storeBiometricData(biometricData);

      console.log('Biometric authentication setup completed successfully');

      // Report setup event
      await this.reportEvent({
        eventType: 'REGISTRATION',
        success: true,
        biometryType: capabilities.biometryType,
        additionalData: {
          keyId,
          securityLevel: capabilities.securityLevel,
        },
      });

      return {
        success: true,
        publicKey,
        biometryType: capabilities.biometryType,
        timestamp: Date.now(),
        deviceFingerprint: deviceFingerprint.hash,
      };

    } catch (error) {
      console.error('Biometric setup failed:', error);
      
      // Report setup failure
      await this.reportEvent({
        eventType: 'REGISTRATION',
        success: false,
        additionalData: { error: error.message },
      });

      if (error instanceof BiometricError) {
        return {
          success: false,
          error: error.code,
          errorMessage: error.message,
        };
      }

      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage: 'An unexpected error occurred during setup',
      };
    }
  }

  /**
   * Authenticate user using biometrics
   */
  async authenticate(userId: string, promptConfig?: BiometricPromptConfig): Promise<BiometricAuthResult> {
    try {
      console.log('Authenticating user with biometrics:', userId);
      
      await this.ensureInitialized();

      // Check if user has biometric setup
      const biometricData = await this.storageService.getBiometricData(userId);
      if (!biometricData?.isActive) {
        return {
          success: false,
          error: BiometricAuthError.NOT_ENROLLED,
          errorMessage: 'Biometric authentication is not set up for this user',
        };
      }

      // Check if user is locked out
      const isLockedOut = await this.storageService.isLockedOut(userId, this.config.maxFailureAttempts);
      if (isLockedOut) {
        return {
          success: false,
          error: BiometricAuthError.TEMPORARILY_LOCKED,
          errorMessage: 'Account temporarily locked due to too many failed attempts',
        };
      }

      // Check biometric availability
      const capabilities = await this.biometricService.checkAvailability();
      if (!capabilities.isAvailable || !capabilities.isEnrolled) {
        await this.storageService.incrementFailureCount(userId);
        return {
          success: false,
          error: BiometricAuthError.HARDWARE_NOT_AVAILABLE,
          errorMessage: 'Biometric authentication is not available',
        };
      }

      // Verify biometric integrity
      const integrityCheck = await this.biometricService.verifyBiometricIntegrity();
      if (!integrityCheck) {
        return {
          success: false,
          error: BiometricAuthError.SYSTEM_ERROR,
          errorMessage: 'Biometric setup has been modified. Please re-setup biometric authentication',
        };
      }

      // Generate challenge
      const { challenge } = await this.securityClient.getBiometricChallenge(userId);

      // Use default prompt config if not provided
      const config = promptConfig || this.getDefaultPromptConfig();
      
      // Perform biometric authentication
      const authResult = await this.biometricService.authenticate(config, challenge);
      
      if (!authResult.success || !authResult.signature) {
        await this.storageService.incrementFailureCount(userId);
        
        // Report authentication failure
        await this.reportEvent({
          eventType: 'AUTHENTICATION',
          success: false,
          biometryType: capabilities.biometryType,
          additionalData: {
            error: authResult.error,
            failureCount: await this.storageService.getBiometricData(userId).then(d => d?.failureCount || 0),
          },
        });

        return authResult;
      }

      // Get current device fingerprint
      const currentFingerprint = await this.fingerprintService.generateFingerprint();

      // Verify with backend
      const verificationRequest = {
        userId,
        challenge,
        signature: authResult.signature,
        biometryType: biometricData.biometryType,
        deviceFingerprint: currentFingerprint,
        timestamp: Date.now(),
      };

      const verificationResult = await this.securityClient.authenticateBiometric(verificationRequest);
      
      if (!verificationResult.verified) {
        await this.storageService.incrementFailureCount(userId);
        
        // Report verification failure
        await this.reportEvent({
          eventType: 'AUTHENTICATION',
          success: false,
          biometryType: capabilities.biometryType,
          additionalData: {
            verificationFailed: true,
            riskAssessment: verificationResult.riskAssessment,
          },
        });

        return {
          success: false,
          error: BiometricAuthError.AUTHENTICATION_FAILED,
          errorMessage: verificationResult.message || 'Authentication verification failed',
        };
      }

      // Reset failure count on successful authentication
      await this.storageService.resetFailureCount(userId);

      // Store authentication token if provided
      if (verificationResult.token) {
        const token: BiometricToken = {
          token: verificationResult.token,
          signature: authResult.signature,
          publicKey: biometricData.publicKey,
          expiresAt: Date.now() + (verificationResult.expiresIn || 3600) * 1000,
          issuedAt: Date.now(),
          deviceFingerprint: currentFingerprint.hash,
          userId,
          biometryType: biometricData.biometryType,
          securityLevel: verificationResult.securityLevel,
        };

        await this.storageService.storeToken(token);
      }

      console.log('Biometric authentication successful');

      // Report successful authentication
      await this.reportEvent({
        eventType: 'AUTHENTICATION',
        success: true,
        biometryType: capabilities.biometryType,
        additionalData: {
          securityLevel: verificationResult.securityLevel,
          riskAssessment: verificationResult.riskAssessment,
        },
      });

      return {
        success: true,
        signature: authResult.signature,
        biometryType: capabilities.biometryType,
        timestamp: Date.now(),
        deviceFingerprint: currentFingerprint.hash,
      };

    } catch (error) {
      console.error('Biometric authentication failed:', error);
      
      await this.storageService.incrementFailureCount(userId);
      
      // Report authentication error
      await this.reportEvent({
        eventType: 'AUTHENTICATION',
        success: false,
        additionalData: { error: error.message },
      });

      if (error instanceof BiometricError) {
        return {
          success: false,
          error: error.code,
          errorMessage: error.message,
        };
      }

      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage: 'An unexpected error occurred during authentication',
      };
    }
  }

  /**
   * Disable biometric authentication for a user
   */
  async disableBiometric(userId: string): Promise<boolean> {
    try {
      console.log('Disabling biometric authentication for user:', userId);
      
      await this.ensureInitialized();

      // Get existing data
      const biometricData = await this.storageService.getBiometricData(userId);
      if (!biometricData) {
        console.log('No biometric data found for user');
        return true;
      }

      // Revoke on backend
      try {
        await this.securityClient.revokeBiometric(userId, 'User requested disable');
      } catch (error) {
        console.warn('Failed to revoke biometric on backend:', error);
        // Continue with local cleanup even if backend fails
      }

      // Delete local keys
      if (biometricData.keyAlias) {
        await this.biometricService.deleteKey(biometricData.keyAlias);
      }

      // Remove local data
      await Promise.all([
        this.storageService.removeBiometricData(userId),
        this.storageService.removeToken(userId),
      ]);

      console.log('Biometric authentication disabled successfully');

      // Report disable event
      await this.reportEvent({
        eventType: 'DISABLED',
        success: true,
        biometryType: biometricData.biometryType,
      });

      return true;
    } catch (error) {
      console.error('Failed to disable biometric authentication:', error);
      
      // Report disable failure
      await this.reportEvent({
        eventType: 'DISABLED',
        success: false,
        additionalData: { error: error.message },
      });

      return false;
    }
  }

  /**
   * Check if biometric authentication is set up for a user
   */
  async isSetup(userId: string): Promise<boolean> {
    try {
      const biometricData = await this.storageService.getBiometricData(userId);
      return biometricData?.isActive === true;
    } catch (error) {
      console.error('Failed to check biometric setup:', error);
      return false;
    }
  }

  /**
   * Get biometric capabilities
   */
  async getCapabilities(): Promise<BiometricCapabilities> {
    await this.ensureInitialized();
    return this.biometricService.checkAvailability();
  }

  /**
   * Get security settings for a user
   */
  async getSettings(userId: string): Promise<BiometricSettings> {
    try {
      await this.ensureInitialized();
      return await this.securityClient.getSecuritySettings(userId);
    } catch (error) {
      console.error('Failed to get security settings:', error);
      throw error;
    }
  }

  /**
   * Update security settings for a user
   */
  async updateSettings(userId: string, settings: Partial<BiometricSettings>): Promise<void> {
    try {
      await this.ensureInitialized();
      await this.securityClient.updateSecuritySettings(userId, settings);
    } catch (error) {
      console.error('Failed to update security settings:', error);
      throw error;
    }
  }

  /**
   * Handle fallback authentication methods
   */
  async handleFallback(userId: string, method: AuthenticationMethod): Promise<BiometricAuthResult> {
    try {
      console.log(`Handling fallback authentication for user ${userId} with method: ${method}`);
      
      // This is a placeholder implementation
      // In a real implementation, you would integrate with other authentication methods
      
      switch (method) {
        case AuthenticationMethod.PIN:
          // Implement PIN authentication
          return this.handlePinFallback(userId);
        
        case AuthenticationMethod.PASSWORD:
          // Implement password authentication
          return this.handlePasswordFallback(userId);
        
        case AuthenticationMethod.MFA:
          // Implement MFA authentication
          return this.handleMFAFallback(userId);
        
        default:
          return {
            success: false,
            error: BiometricAuthError.SYSTEM_ERROR,
            errorMessage: 'Unsupported fallback method',
          };
      }
    } catch (error) {
      console.error('Fallback authentication failed:', error);
      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage: 'Fallback authentication failed',
      };
    }
  }

  /**
   * Check device security status
   */
  async checkDeviceSecurity(): Promise<SecurityAssessment> {
    await this.ensureInitialized();
    return this.fingerprintService.assessSecurity();
  }

  /**
   * Logout and clear biometric session
   */
  async logout(userId: string): Promise<void> {
    try {
      console.log('Logging out biometric session for user:', userId);
      
      // Remove stored token
      await this.storageService.removeToken(userId);
      
      // Clear any cached authentication data
      // Additional cleanup could be added here
      
      console.log('Biometric session logged out successfully');
    } catch (error) {
      console.error('Failed to logout biometric session:', error);
      // Don't throw error for logout failures
    }
  }

  /**
   * Private helper methods
   */
  private async ensureInitialized(): Promise<void> {
    if (!this.initialized) {
      await this.initialize();
    }
  }

  private generateKeyAlias(userId: string): string {
    return `${this.config.keyAlias}_${userId}`;
  }

  private generateChallenge(): string {
    const timestamp = Date.now().toString();
    const random = Math.random().toString(36).substring(2);
    return `${timestamp}_${random}`;
  }

  private getDefaultPromptConfig(): BiometricPromptConfig {
    return {
      title: 'Biometric Authentication',
      subtitle: 'Use your biometric to authenticate',
      description: 'Place your finger on the sensor or look at the camera',
      negativeButtonText: 'Cancel',
      fallbackTitle: 'Use Alternative',
      allowDeviceCredentials: false,
    };
  }

  private getDefaultConfig(): BiometricConfig {
    return {
      keyAlias: 'WaqitiBiometric',
      storageKey: 'biometric_data',
      challengeLength: 32,
      tokenExpirationTime: 3600000, // 1 hour
      maxFailureAttempts: 5,
      lockoutDuration: 300000, // 5 minutes
      securityLevel: SecurityLevel.HIGH,
      enableLogging: true,
      enableAnalytics: true,
      apiEndpoints: {
        register: '/api/v1/security/biometric/register',
        authenticate: '/api/v1/security/biometric/authenticate',
        verify: '/api/v1/security/device/verify',
        settings: '/api/v1/security/settings',
        events: '/api/v1/security/events',
      },
      timeouts: {
        authentication: 30000,
        network: 10000,
        keyGeneration: 15000,
      },
      retry: {
        maxAttempts: 3,
        backoffMs: 1000,
      },
    };
  }

  private async reportEvent(eventData: Partial<BiometricEvent>): Promise<void> {
    try {
      if (!this.config.enableAnalytics) return;

      const deviceFingerprint = await this.fingerprintService.generateFingerprint();
      
      const event: BiometricEvent = {
        eventId: this.generateEventId(),
        userId: eventData.userId || 'unknown',
        eventType: eventData.eventType || 'UNKNOWN',
        success: eventData.success || false,
        biometryType: eventData.biometryType,
        errorCode: eventData.errorCode,
        deviceFingerprint: deviceFingerprint.hash,
        timestamp: Date.now(),
        additionalData: eventData.additionalData,
      };

      await this.securityClient.reportSecurityEvent(event);
    } catch (error) {
      console.warn('Failed to report security event:', error);
      // Don't throw error for event reporting failures
    }
  }

  private generateEventId(): string {
    return `${Date.now()}_${Math.random().toString(36).substring(2)}`;
  }

  // Placeholder fallback implementations
  private async handlePinFallback(userId: string): Promise<BiometricAuthResult> {
    // Implement PIN-based fallback authentication
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'PIN fallback not implemented',
    };
  }

  private async handlePasswordFallback(userId: string): Promise<BiometricAuthResult> {
    // Implement password-based fallback authentication
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'Password fallback not implemented',
    };
  }

  private async handleMFAFallback(userId: string): Promise<BiometricAuthResult> {
    // Implement MFA-based fallback authentication
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'MFA fallback not implemented',
    };
  }
}

export default BiometricAuthService;