/**
 * Biometric Error Handler and Fallback Mechanisms
 * Provides comprehensive error handling and recovery strategies
 */

import {
  BiometricError,
  BiometricAuthError,
  BiometricAuthResult,
  BiometricFallbackOptions,
  AuthenticationMethod,
  SecurityLevel,
  BiometricCapabilities,
} from './types';
import { BiometricService } from './BiometricService';
import { DeviceFingerprintService } from './DeviceFingerprintService';

export class BiometricErrorHandler {
  private static instance: BiometricErrorHandler;
  private biometricService: BiometricService;
  private fingerprintService: DeviceFingerprintService;
  
  // Error retry configuration
  private readonly maxRetries = 3;
  private readonly retryDelay = 1000; // 1 second
  private readonly lockoutDuration = 5 * 60 * 1000; // 5 minutes

  constructor() {
    this.biometricService = BiometricService.getInstance();
    this.fingerprintService = DeviceFingerprintService.getInstance();
  }

  public static getInstance(): BiometricErrorHandler {
    if (!BiometricErrorHandler.instance) {
      BiometricErrorHandler.instance = new BiometricErrorHandler();
    }
    return BiometricErrorHandler.instance;
  }

  /**
   * Handle biometric authentication errors with appropriate recovery strategies
   */
  async handleError(
    error: BiometricError | BiometricAuthError | Error,
    userId: string,
    fallbackOptions?: BiometricFallbackOptions
  ): Promise<BiometricAuthResult> {
    console.log('Handling biometric error:', error);

    let biometricError: BiometricError;
    
    // Convert error to BiometricError if needed
    if (error instanceof BiometricError) {
      biometricError = error;
    } else if (typeof error === 'string') {
      biometricError = new BiometricError(
        error as BiometricAuthError,
        this.getErrorMessage(error as BiometricAuthError),
        null,
        false,
        false
      );
    } else {
      biometricError = new BiometricError(
        BiometricAuthError.SYSTEM_ERROR,
        error.message || 'Unknown error occurred',
        error,
        false,
        false
      );
    }

    // Log error for analytics
    await this.logError(biometricError, userId);

    // Determine recovery strategy
    const strategy = this.determineRecoveryStrategy(biometricError, fallbackOptions);
    
    return this.executeRecoveryStrategy(strategy, biometricError, userId, fallbackOptions);
  }

  /**
   * Determine the appropriate recovery strategy for the error
   */
  private determineRecoveryStrategy(
    error: BiometricError,
    fallbackOptions?: BiometricFallbackOptions
  ): RecoveryStrategy {
    switch (error.code) {
      case BiometricAuthError.HARDWARE_NOT_AVAILABLE:
        return fallbackOptions?.allowPassword 
          ? RecoveryStrategy.PASSWORD_FALLBACK 
          : RecoveryStrategy.GRACEFUL_DEGRADATION;

      case BiometricAuthError.NOT_ENROLLED:
        return RecoveryStrategy.SETUP_GUIDANCE;

      case BiometricAuthError.USER_CANCELLED:
        return fallbackOptions?.allowPin 
          ? RecoveryStrategy.PIN_FALLBACK 
          : RecoveryStrategy.USER_RETRY;

      case BiometricAuthError.AUTHENTICATION_FAILED:
        return RecoveryStrategy.RETRY_WITH_DELAY;

      case BiometricAuthError.TEMPORARILY_LOCKED:
        return RecoveryStrategy.LOCKOUT_RECOVERY;

      case BiometricAuthError.PERMANENTLY_LOCKED:
        return fallbackOptions?.allowMFA 
          ? RecoveryStrategy.MFA_FALLBACK 
          : RecoveryStrategy.ACCOUNT_RECOVERY;

      case BiometricAuthError.DEVICE_NOT_TRUSTED:
        return RecoveryStrategy.DEVICE_VERIFICATION;

      case BiometricAuthError.TOKEN_EXPIRED:
        return RecoveryStrategy.TOKEN_REFRESH;

      case BiometricAuthError.NETWORK_ERROR:
        return RecoveryStrategy.OFFLINE_MODE;

      case BiometricAuthError.SYSTEM_ERROR:
      default:
        return error.recoverable 
          ? RecoveryStrategy.SYSTEM_RECOVERY 
          : RecoveryStrategy.GRACEFUL_DEGRADATION;
    }
  }

  /**
   * Execute the determined recovery strategy
   */
  private async executeRecoveryStrategy(
    strategy: RecoveryStrategy,
    error: BiometricError,
    userId: string,
    fallbackOptions?: BiometricFallbackOptions
  ): Promise<BiometricAuthResult> {
    console.log(`Executing recovery strategy: ${strategy}`);

    switch (strategy) {
      case RecoveryStrategy.RETRY_WITH_DELAY:
        return this.handleRetryWithDelay(error, userId);

      case RecoveryStrategy.PIN_FALLBACK:
        return this.handlePinFallback(userId);

      case RecoveryStrategy.PASSWORD_FALLBACK:
        return this.handlePasswordFallback(userId);

      case RecoveryStrategy.MFA_FALLBACK:
        return this.handleMFAFallback(userId);

      case RecoveryStrategy.SETUP_GUIDANCE:
        return this.handleSetupGuidance(error);

      case RecoveryStrategy.LOCKOUT_RECOVERY:
        return this.handleLockoutRecovery(error, fallbackOptions);

      case RecoveryStrategy.DEVICE_VERIFICATION:
        return this.handleDeviceVerification(userId);

      case RecoveryStrategy.TOKEN_REFRESH:
        return this.handleTokenRefresh(userId);

      case RecoveryStrategy.OFFLINE_MODE:
        return this.handleOfflineMode(userId);

      case RecoveryStrategy.SYSTEM_RECOVERY:
        return this.handleSystemRecovery(error, userId);

      case RecoveryStrategy.ACCOUNT_RECOVERY:
        return this.handleAccountRecovery(error, fallbackOptions);

      case RecoveryStrategy.GRACEFUL_DEGRADATION:
        return this.handleGracefulDegradation(error);

      case RecoveryStrategy.USER_RETRY:
      default:
        return this.handleUserRetry(error);
    }
  }

  /**
   * Recovery strategy implementations
   */
  private async handleRetryWithDelay(error: BiometricError, userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing retry with delay strategy');
    
    return {
      success: false,
      error: error.code,
      errorMessage: `${error.message}. Please try again in a moment.`,
      timestamp: Date.now(),
    };
  }

  private async handlePinFallback(userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing PIN fallback strategy');
    
    return {
      success: false,
      error: BiometricAuthError.USER_CANCELLED,
      errorMessage: 'Please use your PIN to authenticate',
      timestamp: Date.now(),
    };
  }

  private async handlePasswordFallback(userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing password fallback strategy');
    
    return {
      success: false,
      error: BiometricAuthError.HARDWARE_NOT_AVAILABLE,
      errorMessage: 'Please use your password to authenticate',
      timestamp: Date.now(),
    };
  }

  private async handleMFAFallback(userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing MFA fallback strategy');
    
    return {
      success: false,
      error: BiometricAuthError.PERMANENTLY_LOCKED,
      errorMessage: 'Please use multi-factor authentication',
      timestamp: Date.now(),
    };
  }

  private async handleSetupGuidance(error: BiometricError): Promise<BiometricAuthResult> {
    console.log('Implementing setup guidance strategy');
    
    const capabilities = await this.biometricService.checkAvailability();
    let guidance = 'Please set up biometric authentication in your device settings.';
    
    if (capabilities.hasHardware && !capabilities.isEnrolled) {
      guidance = 'Please enroll your biometric credentials in device settings to use this feature.';
    } else if (!capabilities.hasHardware) {
      guidance = 'This device does not support biometric authentication.';
    }
    
    return {
      success: false,
      error: error.code,
      errorMessage: guidance,
      timestamp: Date.now(),
    };
  }

  private async handleLockoutRecovery(
    error: BiometricError, 
    fallbackOptions?: BiometricFallbackOptions
  ): Promise<BiometricAuthResult> {
    console.log('Implementing lockout recovery strategy');
    
    const lockoutMessage = fallbackOptions?.emergencyContact
      ? `Biometric sensor is temporarily locked. Please try again later or contact ${fallbackOptions.emergencyContact} for assistance.`
      : 'Biometric sensor is temporarily locked. Please try again later.';
    
    return {
      success: false,
      error: error.code,
      errorMessage: lockoutMessage,
      timestamp: Date.now(),
    };
  }

  private async handleDeviceVerification(userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing device verification strategy');
    
    try {
      const securityAssessment = await this.fingerprintService.assessSecurity();
      
      if (securityAssessment.riskLevel === SecurityLevel.CRITICAL) {
        return {
          success: false,
          error: BiometricAuthError.DEVICE_NOT_TRUSTED,
          errorMessage: 'Device security verification failed. Please contact support.',
          timestamp: Date.now(),
        };
      }
      
      return {
        success: false,
        error: BiometricAuthError.DEVICE_NOT_TRUSTED,
        errorMessage: 'Device verification in progress. Please try again.',
        timestamp: Date.now(),
      };
    } catch (verificationError) {
      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage: 'Unable to verify device security.',
        timestamp: Date.now(),
      };
    }
  }

  private async handleTokenRefresh(userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing token refresh strategy');
    
    return {
      success: false,
      error: BiometricAuthError.TOKEN_EXPIRED,
      errorMessage: 'Authentication session expired. Please authenticate again.',
      timestamp: Date.now(),
    };
  }

  private async handleOfflineMode(userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing offline mode strategy');
    
    return {
      success: false,
      error: BiometricAuthError.NETWORK_ERROR,
      errorMessage: 'Network connection required for biometric authentication. Please check your connection.',
      timestamp: Date.now(),
    };
  }

  private async handleSystemRecovery(error: BiometricError, userId: string): Promise<BiometricAuthResult> {
    console.log('Implementing system recovery strategy');
    
    // Attempt to reset biometric system state
    try {
      const capabilities = await this.biometricService.checkAvailability();
      
      if (capabilities.isAvailable) {
        return {
          success: false,
          error: error.code,
          errorMessage: 'System recovered. Please try biometric authentication again.',
          timestamp: Date.now(),
        };
      }
    } catch (recoveryError) {
      console.error('System recovery failed:', recoveryError);
    }
    
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'System error occurred. Please restart the app and try again.',
      timestamp: Date.now(),
    };
  }

  private async handleAccountRecovery(
    error: BiometricError, 
    fallbackOptions?: BiometricFallbackOptions
  ): Promise<BiometricAuthResult> {
    console.log('Implementing account recovery strategy');
    
    const recoveryMessage = fallbackOptions?.emergencyContact
      ? `Account recovery required. Please contact ${fallbackOptions.emergencyContact} for assistance.`
      : 'Account recovery required. Please contact support for assistance.';
    
    return {
      success: false,
      error: error.code,
      errorMessage: recoveryMessage,
      timestamp: Date.now(),
    };
  }

  private async handleGracefulDegradation(error: BiometricError): Promise<BiometricAuthResult> {
    console.log('Implementing graceful degradation strategy');
    
    return {
      success: false,
      error: error.code,
      errorMessage: 'Biometric authentication is temporarily unavailable. Please use alternative authentication.',
      timestamp: Date.now(),
    };
  }

  private async handleUserRetry(error: BiometricError): Promise<BiometricAuthResult> {
    console.log('Implementing user retry strategy');
    
    return {
      success: false,
      error: error.code,
      errorMessage: error.message,
      timestamp: Date.now(),
    };
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
   * Log error for analytics and monitoring
   */
  private async logError(error: BiometricError, userId: string): Promise<void> {
    try {
      console.log('Logging biometric error:', {
        errorCode: error.code,
        message: error.message,
        userId,
        timestamp: Date.now(),
        recoverable: error.recoverable,
        fallbackAvailable: error.fallbackAvailable,
      });

      // In production, this would send to analytics service
      // await analyticsService.trackError(errorEvent);
    } catch (logError) {
      console.warn('Failed to log error:', logError);
    }
  }

  /**
   * Create a BiometricError from various error types
   */
  static createBiometricError(
    error: any,
    defaultCode: BiometricAuthError = BiometricAuthError.SYSTEM_ERROR
  ): BiometricError {
    if (error instanceof BiometricError) {
      return error;
    }

    let code = defaultCode;
    let message = 'Unknown error occurred';
    let recoverable = false;
    let fallbackAvailable = false;

    if (typeof error === 'string') {
      code = error as BiometricAuthError;
      message = BiometricErrorHandler.prototype.getErrorMessage(code);
      recoverable = true;
      fallbackAvailable = true;
    } else if (error instanceof Error) {
      message = error.message;
      
      // Try to map error message to specific error codes
      const errorMsg = error.message.toLowerCase();
      if (errorMsg.includes('cancel')) {
        code = BiometricAuthError.USER_CANCELLED;
        recoverable = true;
        fallbackAvailable = true;
      } else if (errorMsg.includes('locked')) {
        code = BiometricAuthError.TEMPORARILY_LOCKED;
        recoverable = true;
        fallbackAvailable = true;
      } else if (errorMsg.includes('not enrolled') || errorMsg.includes('no biometrics')) {
        code = BiometricAuthError.NOT_ENROLLED;
        recoverable = false;
        fallbackAvailable = true;
      } else if (errorMsg.includes('not available') || errorMsg.includes('hardware')) {
        code = BiometricAuthError.HARDWARE_NOT_AVAILABLE;
        recoverable = false;
        fallbackAvailable = true;
      }
    }

    return new BiometricError(code, message, error, recoverable, fallbackAvailable);
  }

  /**
   * Check if an error is recoverable
   */
  static isRecoverable(error: BiometricError | BiometricAuthError): boolean {
    if (error instanceof BiometricError) {
      return error.recoverable;
    }

    const recoverableErrors = [
      BiometricAuthError.USER_CANCELLED,
      BiometricAuthError.AUTHENTICATION_FAILED,
      BiometricAuthError.TEMPORARILY_LOCKED,
      BiometricAuthError.NETWORK_ERROR,
      BiometricAuthError.TOKEN_EXPIRED,
    ];

    return recoverableErrors.includes(error);
  }

  /**
   * Check if fallback is available for an error
   */
  static isFallbackAvailable(error: BiometricError | BiometricAuthError): boolean {
    if (error instanceof BiometricError) {
      return error.fallbackAvailable;
    }

    const fallbackAvailableErrors = [
      BiometricAuthError.HARDWARE_NOT_AVAILABLE,
      BiometricAuthError.NOT_ENROLLED,
      BiometricAuthError.USER_CANCELLED,
      BiometricAuthError.TEMPORARILY_LOCKED,
      BiometricAuthError.PERMANENTLY_LOCKED,
      BiometricAuthError.DEVICE_NOT_TRUSTED,
    ];

    return fallbackAvailableErrors.includes(error);
  }
}

/**
 * Recovery strategies for different error types
 */
enum RecoveryStrategy {
  RETRY_WITH_DELAY = 'retry_with_delay',
  PIN_FALLBACK = 'pin_fallback',
  PASSWORD_FALLBACK = 'password_fallback',
  MFA_FALLBACK = 'mfa_fallback',
  SETUP_GUIDANCE = 'setup_guidance',
  LOCKOUT_RECOVERY = 'lockout_recovery',
  DEVICE_VERIFICATION = 'device_verification',
  TOKEN_REFRESH = 'token_refresh',
  OFFLINE_MODE = 'offline_mode',
  SYSTEM_RECOVERY = 'system_recovery',
  ACCOUNT_RECOVERY = 'account_recovery',
  GRACEFUL_DEGRADATION = 'graceful_degradation',
  USER_RETRY = 'user_retry',
}

/**
 * Fallback Manager for coordinating alternative authentication methods
 */
export class BiometricFallbackManager {
  private static instance: BiometricFallbackManager;

  public static getInstance(): BiometricFallbackManager {
    if (!BiometricFallbackManager.instance) {
      BiometricFallbackManager.instance = new BiometricFallbackManager();
    }
    return BiometricFallbackManager.instance;
  }

  /**
   * Get available fallback methods based on device capabilities and user settings
   */
  async getAvailableFallbacks(
    userId: string, 
    capabilities: BiometricCapabilities
  ): Promise<AuthenticationMethod[]> {
    const fallbacks: AuthenticationMethod[] = [];

    try {
      // Check if PIN is available
      if (capabilities.hasHardware) {
        fallbacks.push(AuthenticationMethod.PIN);
      }

      // Password is always available as a fallback
      fallbacks.push(AuthenticationMethod.PASSWORD);

      // Check if MFA is configured for the user
      // This would require checking with the backend
      fallbacks.push(AuthenticationMethod.MFA);

      return fallbacks;
    } catch (error) {
      console.error('Failed to get available fallbacks:', error);
      return [AuthenticationMethod.PASSWORD]; // Always provide password as fallback
    }
  }

  /**
   * Execute a specific fallback method
   */
  async executeFallback(
    method: AuthenticationMethod, 
    userId: string
  ): Promise<BiometricAuthResult> {
    console.log(`Executing fallback method: ${method} for user: ${userId}`);

    switch (method) {
      case AuthenticationMethod.PIN:
        return this.handlePinAuthentication(userId);
      
      case AuthenticationMethod.PASSWORD:
        return this.handlePasswordAuthentication(userId);
      
      case AuthenticationMethod.MFA:
        return this.handleMFAAuthentication(userId);
      
      default:
        return {
          success: false,
          error: BiometricAuthError.SYSTEM_ERROR,
          errorMessage: 'Unsupported fallback method',
        };
    }
  }

  /**
   * Placeholder implementations for fallback methods
   * These would be implemented based on the app's authentication system
   */
  private async handlePinAuthentication(userId: string): Promise<BiometricAuthResult> {
    // Implement PIN-based authentication
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'PIN authentication not implemented',
    };
  }

  private async handlePasswordAuthentication(userId: string): Promise<BiometricAuthResult> {
    // Implement password-based authentication
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'Password authentication not implemented',
    };
  }

  private async handleMFAAuthentication(userId: string): Promise<BiometricAuthResult> {
    // Implement MFA-based authentication
    return {
      success: false,
      error: BiometricAuthError.SYSTEM_ERROR,
      errorMessage: 'MFA authentication not implemented',
    };
  }
}

export default BiometricErrorHandler;