/**
 * Biometric Authentication Services
 * Main entry point for all biometric authentication functionality
 */

// Core service exports
export { BiometricService } from './BiometricService';
export { SecureStorageService } from './SecureStorageService';
export { DeviceFingerprintService } from './DeviceFingerprintService';
export { SecurityServiceClient } from './SecurityServiceClient';
export { BiometricAuthService } from './BiometricAuthService';

// Error handling exports
export { BiometricErrorHandler, BiometricFallbackManager } from './ErrorHandler';

// Type exports
export * from './types';

// Default service instances for easy access
export const biometricService = BiometricService.getInstance();
export const secureStorageService = SecureStorageService.getInstance();
export const deviceFingerprintService = DeviceFingerprintService.getInstance();
export const securityServiceClient = SecurityServiceClient.getInstance();
export const biometricAuthService = BiometricAuthService.getInstance();
export const biometricErrorHandler = BiometricErrorHandler.getInstance();
export const biometricFallbackManager = BiometricFallbackManager.getInstance();

/**
 * Main Biometric API - Simplified interface for common operations
 */
export class BiometricAPI {
  private static instance: BiometricAPI;
  
  private authService: BiometricAuthService;
  private errorHandler: BiometricErrorHandler;
  private fallbackManager: BiometricFallbackManager;

  constructor() {
    this.authService = BiometricAuthService.getInstance();
    this.errorHandler = BiometricErrorHandler.getInstance();
    this.fallbackManager = BiometricFallbackManager.getInstance();
  }

  public static getInstance(): BiometricAPI {
    if (!BiometricAPI.instance) {
      BiometricAPI.instance = new BiometricAPI();
    }
    return BiometricAPI.instance;
  }

  /**
   * Initialize biometric services
   */
  async initialize(): Promise<void> {
    return this.authService.initialize();
  }

  /**
   * Check if biometric authentication is available
   */
  async isAvailable(): Promise<boolean> {
    try {
      const capabilities = await this.authService.getCapabilities();
      return capabilities.isAvailable && capabilities.isEnrolled;
    } catch (error) {
      console.error('Failed to check biometric availability:', error);
      return false;
    }
  }

  /**
   * Check if biometric authentication is set up for a user
   */
  async isSetupForUser(userId: string): Promise<boolean> {
    return this.authService.isSetup(userId);
  }

  /**
   * Setup biometric authentication for a user
   */
  async setupBiometric(userId: string, promptConfig?: import('./types').BiometricPromptConfig): Promise<import('./types').BiometricAuthResult> {
    try {
      return await this.authService.setupBiometric(userId, promptConfig);
    } catch (error) {
      return this.errorHandler.handleError(error, userId);
    }
  }

  /**
   * Authenticate a user using biometrics
   */
  async authenticate(userId: string, promptConfig?: import('./types').BiometricPromptConfig): Promise<import('./types').BiometricAuthResult> {
    try {
      return await this.authService.authenticate(userId, promptConfig);
    } catch (error) {
      return this.errorHandler.handleError(error, userId);
    }
  }

  /**
   * Disable biometric authentication for a user
   */
  async disableBiometric(userId: string): Promise<boolean> {
    return this.authService.disableBiometric(userId);
  }

  /**
   * Get biometric capabilities
   */
  async getCapabilities(): Promise<import('./types').BiometricCapabilities> {
    return this.authService.getCapabilities();
  }

  /**
   * Get security settings for a user
   */
  async getSettings(userId: string): Promise<import('./types').BiometricSettings> {
    return this.authService.getSettings(userId);
  }

  /**
   * Update security settings for a user
   */
  async updateSettings(userId: string, settings: Partial<import('./types').BiometricSettings>): Promise<void> {
    return this.authService.updateSettings(userId, settings);
  }

  /**
   * Check device security status
   */
  async checkDeviceSecurity(): Promise<import('./types').SecurityAssessment> {
    return this.authService.checkDeviceSecurity();
  }

  /**
   * Handle fallback authentication
   */
  async handleFallback(userId: string, method: import('./types').AuthenticationMethod): Promise<import('./types').BiometricAuthResult> {
    return this.fallbackManager.executeFallback(method, userId);
  }

  /**
   * Get available fallback methods
   */
  async getAvailableFallbacks(userId: string): Promise<import('./types').AuthenticationMethod[]> {
    const capabilities = await this.getCapabilities();
    return this.fallbackManager.getAvailableFallbacks(userId, capabilities);
  }

  /**
   * Logout and clear biometric session
   */
  async logout(userId: string): Promise<void> {
    return this.authService.logout(userId);
  }

  /**
   * Get service health status
   */
  async getHealthStatus(): Promise<{
    biometric: boolean;
    storage: boolean;
    network: boolean;
    security: boolean;
  }> {
    try {
      const [capabilities, connectionTest] = await Promise.all([
        this.authService.getCapabilities().catch(() => null),
        securityServiceClient.testConnection().catch(() => ({ connected: false })),
      ]);

      return {
        biometric: capabilities?.isAvailable || false,
        storage: true, // Assume storage is working if we got this far
        network: connectionTest.connected,
        security: connectionTest.connected,
      };
    } catch (error) {
      console.error('Failed to get health status:', error);
      return {
        biometric: false,
        storage: false,
        network: false,
        security: false,
      };
    }
  }

  /**
   * Get usage statistics
   */
  async getUsageStats(): Promise<{
    totalUsers: number;
    activeUsers: number;
    successRate: number;
    lastActivity: number;
  }> {
    try {
      const storageStats = await secureStorageService.getStorageStats();
      
      return {
        totalUsers: storageStats.totalUsers,
        activeUsers: storageStats.activeUsers,
        successRate: 0.95, // This would be calculated from actual usage data
        lastActivity: Date.now(),
      };
    } catch (error) {
      console.error('Failed to get usage stats:', error);
      return {
        totalUsers: 0,
        activeUsers: 0,
        successRate: 0,
        lastActivity: 0,
      };
    }
  }
}

// Default API instance
export const biometricAPI = BiometricAPI.getInstance();

// Convenience functions for quick access
export const initializeBiometric = () => biometricAPI.initialize();
export const isBiometricAvailable = () => biometricAPI.isAvailable();
export const setupUserBiometric = (userId: string, config?: import('./types').BiometricPromptConfig) => 
  biometricAPI.setupBiometric(userId, config);
export const authenticateUser = (userId: string, config?: import('./types').BiometricPromptConfig) => 
  biometricAPI.authenticate(userId, config);
export const disableUserBiometric = (userId: string) => biometricAPI.disableBiometric(userId);
export const getBiometricCapabilities = () => biometricAPI.getCapabilities();
export const checkDeviceSecurityStatus = () => biometricAPI.checkDeviceSecurity();
export const logoutBiometricSession = (userId: string) => biometricAPI.logout(userId);

/**
 * React Hook helpers (to be used with React components)
 */
export const createBiometricHooks = () => {
  // These would be actual React hooks in a real implementation
  return {
    useBiometricAuth: (userId: string) => {
      // Hook implementation would go here
      return {
        isSetup: false,
        isAvailable: false,
        authenticate: () => Promise.resolve({ success: false }),
        setup: () => Promise.resolve({ success: false }),
        disable: () => Promise.resolve(false),
      };
    },
    
    useBiometricCapabilities: () => {
      // Hook implementation would go here
      return {
        capabilities: null,
        loading: false,
        error: null,
        refresh: () => Promise.resolve(),
      };
    },
    
    useDeviceSecurity: () => {
      // Hook implementation would go here
      return {
        assessment: null,
        loading: false,
        error: null,
        check: () => Promise.resolve(),
      };
    },
  };
};

// Configuration helpers
export const createBiometricConfig = (overrides: Partial<import('./types').BiometricConfig> = {}): import('./types').BiometricConfig => {
  const defaultConfig: import('./types').BiometricConfig = {
    keyAlias: 'WaqitiBiometric',
    storageKey: 'biometric_data',
    challengeLength: 32,
    tokenExpirationTime: 3600000, // 1 hour
    maxFailureAttempts: 5,
    lockoutDuration: 300000, // 5 minutes
    securityLevel: 'HIGH' as import('./types').SecurityLevel,
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

  return { ...defaultConfig, ...overrides };
};

export const createPromptConfig = (overrides: Partial<import('./types').BiometricPromptConfig> = {}): import('./types').BiometricPromptConfig => {
  const defaultConfig: import('./types').BiometricPromptConfig = {
    title: 'Biometric Authentication',
    subtitle: 'Use your biometric to authenticate',
    description: 'Place your finger on the sensor or look at the camera',
    negativeButtonText: 'Cancel',
    fallbackTitle: 'Use Alternative',
    allowDeviceCredentials: false,
  };

  return { ...defaultConfig, ...overrides };
};

// Error utility functions
export const isBiometricError = (error: any): error is import('./types').BiometricError => {
  return error && typeof error.code === 'string' && typeof error.message === 'string';
};

export const createBiometricError = (
  code: import('./types').BiometricAuthError,
  message: string,
  details?: any
): import('./types').BiometricError => {
  return BiometricErrorHandler.createBiometricError({ message, code, details }, code);
};

// Default export
export default biometricAPI;