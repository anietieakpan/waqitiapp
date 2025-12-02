/**
 * Error Handling Service - Comprehensive error management system
 * Provides centralized error handling, reporting, and recovery mechanisms
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';
import DeviceInfo from 'react-native-device-info';
import * as Sentry from '@sentry/react-native';
import { Alert, Linking } from 'react-native';
import Config from 'react-native-config';

export enum ErrorSeverity {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical',
}

export enum ErrorCategory {
  NETWORK = 'network',
  AUTHENTICATION = 'authentication',
  VALIDATION = 'validation',
  PAYMENT = 'payment',
  TRANSACTION = 'transaction',
  BIOMETRIC = 'biometric',
  OCR = 'ocr',
  PERMISSION = 'permission',
  STORAGE = 'storage',
  SYSTEM = 'system',
  UNKNOWN = 'unknown',
}

export interface ErrorContext {
  userId?: string;
  action?: string;
  component?: string;
  screen?: string;
  metadata?: Record<string, any>;
  timestamp?: string;
  deviceInfo?: {
    model?: string;
    os?: string;
    version?: string;
    appVersion?: string;
  };
  networkInfo?: {
    isConnected?: boolean;
    type?: string;
    isInternetReachable?: boolean;
  };
}

export interface AppError {
  id: string;
  code: string;
  message: string;
  userMessage?: string;
  category: ErrorCategory;
  severity: ErrorSeverity;
  context?: ErrorContext;
  stack?: string;
  timestamp: string;
  isRecoverable: boolean;
  retryable: boolean;
  retryCount?: number;
  maxRetries?: number;
  suggestions?: string[];
  actions?: ErrorAction[];
}

export interface ErrorAction {
  label: string;
  action: () => void | Promise<void>;
  type: 'primary' | 'secondary' | 'destructive';
}

export interface ErrorRecoveryStrategy {
  category: ErrorCategory;
  condition?: (error: AppError) => boolean;
  recover: (error: AppError) => Promise<boolean>;
  fallback?: (error: AppError) => Promise<void>;
}

export interface ErrorReport {
  errors: AppError[];
  summary: {
    total: number;
    byCategory: Record<ErrorCategory, number>;
    bySeverity: Record<ErrorSeverity, number>;
    recoverable: number;
    resolved: number;
  };
  deviceInfo: any;
  timestamp: string;
}

class ErrorHandlingService {
  private static instance: ErrorHandlingService;
  private errors: Map<string, AppError> = new Map();
  private errorQueue: AppError[] = [];
  private recoveryStrategies: Map<string, ErrorRecoveryStrategy> = new Map();
  private isOnline: boolean = true;
  private maxErrorsInMemory: number = 100;
  private maxErrorsInStorage: number = 500;
  private errorListeners: Set<(error: AppError) => void> = new Set();

  private constructor() {
    this.initializeDefaultRecoveryStrategies();
    this.setupNetworkListener();
    this.loadPersistedErrors();
  }

  static getInstance(): ErrorHandlingService {
    if (!ErrorHandlingService.instance) {
      ErrorHandlingService.instance = new ErrorHandlingService();
    }
    return ErrorHandlingService.instance;
  }

  /**
   * Initialize default recovery strategies
   */
  private initializeDefaultRecoveryStrategies(): void {
    // Network error recovery
    this.registerRecoveryStrategy({
      category: ErrorCategory.NETWORK,
      condition: (error) => error.code === 'NETWORK_ERROR',
      recover: async (error) => {
        const netInfo = await NetInfo.fetch();
        if (netInfo.isConnected) {
          // Network is back, retry the operation
          return true;
        }
        return false;
      },
      fallback: async (error) => {
        // Queue for retry when network is available
        this.queueError(error);
      },
    });

    // Authentication error recovery
    this.registerRecoveryStrategy({
      category: ErrorCategory.AUTHENTICATION,
      condition: (error) => error.code === 'TOKEN_EXPIRED',
      recover: async (error) => {
        try {
          // Attempt to refresh token
          const { default: AuthService } = await import('./AuthService');
          const refreshed = await AuthService.refreshToken();
          return refreshed;
        } catch {
          return false;
        }
      },
      fallback: async (error) => {
        // Navigate to login
        const { NavigationService } = await import('./NavigationService');
        NavigationService.navigate('Login');
      },
    });

    // Payment error recovery
    this.registerRecoveryStrategy({
      category: ErrorCategory.PAYMENT,
      condition: (error) => error.code === 'INSUFFICIENT_FUNDS',
      recover: async (error) => {
        // Can't auto-recover from insufficient funds
        return false;
      },
      fallback: async (error) => {
        Alert.alert(
          'Insufficient Funds',
          'Your account balance is insufficient for this transaction. Would you like to add funds?',
          [
            { text: 'Cancel', style: 'cancel' },
            { 
              text: 'Add Funds', 
              onPress: async () => {
                const { NavigationService } = await import('./NavigationService');
                NavigationService.navigate('AddFunds');
              }
            },
          ]
        );
      },
    });

    // Biometric error recovery
    this.registerRecoveryStrategy({
      category: ErrorCategory.BIOMETRIC,
      condition: (error) => error.code === 'BIOMETRIC_LOCKED',
      recover: async (error) => {
        // Prompt for fallback authentication
        const { promptForPin } = await import('../components/security/SecurityDialogManager');
        const result = await promptForPin('Enter PIN', 'Biometric authentication is locked. Please enter your PIN.');
        return result.success;
      },
    });

    // OCR error recovery
    this.registerRecoveryStrategy({
      category: ErrorCategory.OCR,
      condition: (error) => error.code === 'POOR_IMAGE_QUALITY',
      recover: async (error) => {
        Alert.alert(
          'Image Quality Issue',
          'The image quality is too poor for processing. Please retake the photo with better lighting.',
          [{ text: 'OK' }]
        );
        return false;
      },
    });
  }

  /**
   * Handle error with context
   */
  async handleError(
    error: Error | AppError | any,
    context?: ErrorContext
  ): Promise<AppError> {
    const appError = this.normalizeError(error, context);
    
    // Store error
    this.errors.set(appError.id, appError);
    this.pruneErrors();
    
    // Log to console in development
    if (__DEV__) {
      console.error(`[${appError.category}] ${appError.code}:`, appError.message, appError.context);
    }
    
    // Report to Sentry
    this.reportToSentry(appError);
    
    // Persist critical errors
    if (appError.severity === ErrorSeverity.CRITICAL || appError.severity === ErrorSeverity.HIGH) {
      await this.persistError(appError);
    }
    
    // Notify listeners
    this.notifyListeners(appError);
    
    // Attempt recovery
    const recovered = await this.attemptRecovery(appError);
    if (recovered) {
      appError.isRecoverable = true;
    } else {
      // Show user feedback if not recovered
      this.showUserFeedback(appError);
    }
    
    return appError;
  }

  /**
   * Normalize error to AppError format
   */
  private normalizeError(error: any, context?: ErrorContext): AppError {
    if (this.isAppError(error)) {
      return { ...error, context: { ...error.context, ...context } };
    }

    const errorCode = error.code || 'UNKNOWN_ERROR';
    const errorMessage = error.message || 'An unexpected error occurred';
    
    // Determine category and severity
    const { category, severity } = this.categorizeError(errorCode, errorMessage);
    
    // Create user-friendly message
    const userMessage = this.createUserMessage(category, errorCode, errorMessage);
    
    // Get suggestions
    const suggestions = this.getSuggestions(category, errorCode);
    
    // Create actions
    const actions = this.createActions(category, errorCode);
    
    return {
      id: this.generateErrorId(),
      code: errorCode,
      message: errorMessage,
      userMessage,
      category,
      severity,
      context: {
        ...context,
        timestamp: new Date().toISOString(),
        deviceInfo: this.getDeviceInfo(),
        networkInfo: this.getNetworkInfo(),
      },
      stack: error.stack,
      timestamp: new Date().toISOString(),
      isRecoverable: this.isRecoverable(category, errorCode),
      retryable: this.isRetryable(category, errorCode),
      retryCount: 0,
      maxRetries: 3,
      suggestions,
      actions,
    };
  }

  /**
   * Categorize error based on code and message
   */
  private categorizeError(code: string, message: string): {
    category: ErrorCategory;
    severity: ErrorSeverity;
  } {
    // Network errors
    if (code.includes('NETWORK') || code.includes('TIMEOUT') || message.includes('network')) {
      return { category: ErrorCategory.NETWORK, severity: ErrorSeverity.MEDIUM };
    }
    
    // Authentication errors
    if (code.includes('AUTH') || code.includes('TOKEN') || code.includes('UNAUTHORIZED')) {
      return { category: ErrorCategory.AUTHENTICATION, severity: ErrorSeverity.HIGH };
    }
    
    // Payment errors
    if (code.includes('PAYMENT') || code.includes('FUNDS') || code.includes('TRANSACTION')) {
      return { category: ErrorCategory.PAYMENT, severity: ErrorSeverity.HIGH };
    }
    
    // Validation errors
    if (code.includes('VALID') || code.includes('INVALID') || code.includes('REQUIRED')) {
      return { category: ErrorCategory.VALIDATION, severity: ErrorSeverity.LOW };
    }
    
    // Biometric errors
    if (code.includes('BIOMETRIC') || code.includes('FINGERPRINT') || code.includes('FACE')) {
      return { category: ErrorCategory.BIOMETRIC, severity: ErrorSeverity.MEDIUM };
    }
    
    // OCR errors
    if (code.includes('OCR') || code.includes('IMAGE') || code.includes('SCAN')) {
      return { category: ErrorCategory.OCR, severity: ErrorSeverity.LOW };
    }
    
    // Permission errors
    if (code.includes('PERMISSION') || code.includes('DENIED')) {
      return { category: ErrorCategory.PERMISSION, severity: ErrorSeverity.MEDIUM };
    }
    
    // Storage errors
    if (code.includes('STORAGE') || code.includes('DISK')) {
      return { category: ErrorCategory.STORAGE, severity: ErrorSeverity.HIGH };
    }
    
    // System errors
    if (code.includes('SYSTEM') || code.includes('FATAL')) {
      return { category: ErrorCategory.SYSTEM, severity: ErrorSeverity.CRITICAL };
    }
    
    return { category: ErrorCategory.UNKNOWN, severity: ErrorSeverity.MEDIUM };
  }

  /**
   * Create user-friendly error message
   */
  private createUserMessage(category: ErrorCategory, code: string, message: string): string {
    const messages: Record<ErrorCategory, Record<string, string>> = {
      [ErrorCategory.NETWORK]: {
        NETWORK_ERROR: 'Unable to connect to the server. Please check your internet connection.',
        TIMEOUT: 'The request took too long. Please try again.',
        DEFAULT: 'A network error occurred. Please try again.',
      },
      [ErrorCategory.AUTHENTICATION]: {
        TOKEN_EXPIRED: 'Your session has expired. Please log in again.',
        UNAUTHORIZED: 'You are not authorized to perform this action.',
        INVALID_CREDENTIALS: 'Invalid username or password.',
        DEFAULT: 'Authentication failed. Please try again.',
      },
      [ErrorCategory.PAYMENT]: {
        INSUFFICIENT_FUNDS: 'Insufficient funds in your account.',
        PAYMENT_FAILED: 'Payment processing failed. Please try again.',
        INVALID_AMOUNT: 'Invalid payment amount.',
        DEFAULT: 'Payment error occurred. Please try again.',
      },
      [ErrorCategory.VALIDATION]: {
        REQUIRED_FIELD: 'Please fill in all required fields.',
        INVALID_FORMAT: 'Please check the format of your input.',
        DEFAULT: 'Please check your input and try again.',
      },
      [ErrorCategory.BIOMETRIC]: {
        BIOMETRIC_LOCKED: 'Biometric authentication is locked. Please use your PIN.',
        NOT_ENROLLED: 'Biometric authentication is not set up.',
        DEFAULT: 'Biometric authentication failed.',
      },
      [ErrorCategory.OCR]: {
        POOR_IMAGE_QUALITY: 'Image quality is too poor. Please retake the photo.',
        OCR_FAILED: 'Failed to process the image. Please try again.',
        DEFAULT: 'Document processing failed.',
      },
      [ErrorCategory.PERMISSION]: {
        CAMERA_DENIED: 'Camera permission is required to take photos.',
        STORAGE_DENIED: 'Storage permission is required to save files.',
        DEFAULT: 'Permission denied. Please check app settings.',
      },
      [ErrorCategory.STORAGE]: {
        STORAGE_FULL: 'Device storage is full. Please free up space.',
        DEFAULT: 'Storage error occurred.',
      },
      [ErrorCategory.SYSTEM]: {
        DEFAULT: 'A system error occurred. Please restart the app.',
      },
      [ErrorCategory.UNKNOWN]: {
        DEFAULT: 'An unexpected error occurred. Please try again.',
      },
    };

    const categoryMessages = messages[category] || messages[ErrorCategory.UNKNOWN];
    return categoryMessages[code] || categoryMessages.DEFAULT || message;
  }

  /**
   * Get suggestions for error recovery
   */
  private getSuggestions(category: ErrorCategory, code: string): string[] {
    const suggestions: Record<ErrorCategory, string[]> = {
      [ErrorCategory.NETWORK]: [
        'Check your internet connection',
        'Try switching between Wi-Fi and mobile data',
        'Restart the app',
      ],
      [ErrorCategory.AUTHENTICATION]: [
        'Try logging in again',
        'Reset your password if forgotten',
        'Contact support if the issue persists',
      ],
      [ErrorCategory.PAYMENT]: [
        'Check your account balance',
        'Verify payment details',
        'Try a different payment method',
      ],
      [ErrorCategory.VALIDATION]: [
        'Check all required fields are filled',
        'Verify the format of your input',
        'Remove any special characters if not allowed',
      ],
      [ErrorCategory.BIOMETRIC]: [
        'Clean your fingerprint sensor',
        'Ensure proper lighting for face recognition',
        'Use your PIN as alternative',
      ],
      [ErrorCategory.OCR]: [
        'Ensure good lighting',
        'Hold the camera steady',
        'Place document on a flat surface',
        'Avoid shadows and glare',
      ],
      [ErrorCategory.PERMISSION]: [
        'Go to Settings > Apps > Waqiti > Permissions',
        'Enable the required permissions',
        'Restart the app after granting permissions',
      ],
      [ErrorCategory.STORAGE]: [
        'Delete unused apps or files',
        'Clear app cache',
        'Move files to cloud storage',
      ],
      [ErrorCategory.SYSTEM]: [
        'Restart the app',
        'Update to the latest version',
        'Restart your device',
      ],
      [ErrorCategory.UNKNOWN]: [
        'Try again',
        'Restart the app',
        'Contact support if the issue persists',
      ],
    };

    return suggestions[category] || suggestions[ErrorCategory.UNKNOWN];
  }

  /**
   * Create error actions
   */
  private createActions(category: ErrorCategory, code: string): ErrorAction[] {
    const actions: ErrorAction[] = [];

    switch (category) {
      case ErrorCategory.NETWORK:
        actions.push({
          label: 'Retry',
          action: () => this.retryLastAction(),
          type: 'primary',
        });
        actions.push({
          label: 'Settings',
          action: () => Linking.openSettings(),
          type: 'secondary',
        });
        break;

      case ErrorCategory.AUTHENTICATION:
        actions.push({
          label: 'Login',
          action: async () => {
            const { NavigationService } = await import('./NavigationService');
            NavigationService.navigate('Login');
          },
          type: 'primary',
        });
        break;

      case ErrorCategory.PAYMENT:
        if (code === 'INSUFFICIENT_FUNDS') {
          actions.push({
            label: 'Add Funds',
            action: async () => {
              const { NavigationService } = await import('./NavigationService');
              NavigationService.navigate('AddFunds');
            },
            type: 'primary',
          });
        }
        break;

      case ErrorCategory.PERMISSION:
        actions.push({
          label: 'Open Settings',
          action: () => Linking.openSettings(),
          type: 'primary',
        });
        break;

      case ErrorCategory.STORAGE:
        actions.push({
          label: 'Manage Storage',
          action: () => Linking.openSettings(),
          type: 'primary',
        });
        break;
    }

    // Add contact support for critical errors
    if (this.isCriticalError(category, code)) {
      actions.push({
        label: 'Contact Support',
        action: () => this.contactSupport(),
        type: 'secondary',
      });
    }

    return actions;
  }

  /**
   * Check if error is recoverable
   */
  private isRecoverable(category: ErrorCategory, code: string): boolean {
    const recoverableCategories = [
      ErrorCategory.NETWORK,
      ErrorCategory.AUTHENTICATION,
      ErrorCategory.BIOMETRIC,
      ErrorCategory.PERMISSION,
    ];

    const nonRecoverableCodes = [
      'ACCOUNT_BLOCKED',
      'DEVICE_BANNED',
      'FATAL_ERROR',
    ];

    return recoverableCategories.includes(category) && !nonRecoverableCodes.includes(code);
  }

  /**
   * Check if error is retryable
   */
  private isRetryable(category: ErrorCategory, code: string): boolean {
    const retryableCategories = [
      ErrorCategory.NETWORK,
      ErrorCategory.OCR,
      ErrorCategory.PAYMENT,
    ];

    const nonRetryableCodes = [
      'INSUFFICIENT_FUNDS',
      'INVALID_CREDENTIALS',
      'PERMISSION_DENIED',
    ];

    return retryableCategories.includes(category) && !nonRetryableCodes.includes(code);
  }

  /**
   * Check if error is critical
   */
  private isCriticalError(category: ErrorCategory, code: string): boolean {
    return category === ErrorCategory.SYSTEM || 
           code.includes('FATAL') || 
           code.includes('CRITICAL');
  }

  /**
   * Attempt to recover from error
   */
  private async attemptRecovery(error: AppError): Promise<boolean> {
    const strategy = Array.from(this.recoveryStrategies.values()).find(
      (s) => s.category === error.category && (!s.condition || s.condition(error))
    );

    if (!strategy) {
      return false;
    }

    try {
      const recovered = await strategy.recover(error);
      
      if (!recovered && strategy.fallback) {
        await strategy.fallback(error);
      }
      
      return recovered;
    } catch (recoveryError) {
      console.error('Recovery strategy failed:', recoveryError);
      return false;
    }
  }

  /**
   * Show user feedback for error
   */
  private showUserFeedback(error: AppError): void {
    if (error.severity === ErrorSeverity.LOW) {
      // For low severity, might just log or show toast
      return;
    }

    const buttons: any[] = [];

    // Add retry button if retryable
    if (error.retryable && error.retryCount! < error.maxRetries!) {
      buttons.push({
        text: 'Retry',
        onPress: () => this.retryError(error),
      });
    }

    // Add custom actions
    error.actions?.forEach((action) => {
      buttons.push({
        text: action.label,
        onPress: action.action,
        style: action.type === 'destructive' ? 'destructive' : 'default',
      });
    });

    // Add dismiss button
    buttons.push({
      text: 'Dismiss',
      style: 'cancel',
    });

    Alert.alert(
      'Error',
      error.userMessage || error.message,
      buttons,
      { cancelable: false }
    );
  }

  /**
   * Retry error
   */
  private async retryError(error: AppError): Promise<void> {
    if (!error.retryable || error.retryCount! >= error.maxRetries!) {
      return;
    }

    error.retryCount = (error.retryCount || 0) + 1;
    
    // Re-attempt the original action
    // This would need to be implemented based on the error context
    await this.retryLastAction();
  }

  /**
   * Retry last action
   */
  private async retryLastAction(): Promise<void> {
    // Implementation would depend on action tracking
    console.log('Retrying last action...');
  }

  /**
   * Register recovery strategy
   */
  registerRecoveryStrategy(strategy: ErrorRecoveryStrategy): void {
    const key = `${strategy.category}_${Math.random()}`;
    this.recoveryStrategies.set(key, strategy);
  }

  /**
   * Queue error for retry
   */
  private queueError(error: AppError): void {
    this.errorQueue.push(error);
    
    // Limit queue size
    if (this.errorQueue.length > 50) {
      this.errorQueue.shift();
    }
  }

  /**
   * Process error queue
   */
  async processErrorQueue(): Promise<void> {
    while (this.errorQueue.length > 0) {
      const error = this.errorQueue.shift();
      if (error) {
        await this.attemptRecovery(error);
      }
    }
  }

  /**
   * Report error to Sentry
   */
  private reportToSentry(error: AppError): void {
    if (!Config.SENTRY_ENABLED || __DEV__) {
      return;
    }

    Sentry.captureException(new Error(error.message), {
      level: this.mapSeverityToSentryLevel(error.severity),
      tags: {
        category: error.category,
        code: error.code,
        recoverable: String(error.isRecoverable),
      },
      contexts: {
        error: {
          ...error.context,
        },
      },
    });
  }

  /**
   * Map severity to Sentry level
   */
  private mapSeverityToSentryLevel(severity: ErrorSeverity): Sentry.SeverityLevel {
    switch (severity) {
      case ErrorSeverity.LOW:
        return 'info';
      case ErrorSeverity.MEDIUM:
        return 'warning';
      case ErrorSeverity.HIGH:
        return 'error';
      case ErrorSeverity.CRITICAL:
        return 'fatal';
      default:
        return 'error';
    }
  }

  /**
   * Persist error to storage
   */
  private async persistError(error: AppError): Promise<void> {
    try {
      const storedErrors = await this.getStoredErrors();
      storedErrors.unshift(error);
      
      // Limit stored errors
      if (storedErrors.length > this.maxErrorsInStorage) {
        storedErrors.splice(this.maxErrorsInStorage);
      }
      
      await AsyncStorage.setItem('@error_log', JSON.stringify(storedErrors));
    } catch (storageError) {
      console.error('Failed to persist error:', storageError);
    }
  }

  /**
   * Get stored errors
   */
  private async getStoredErrors(): Promise<AppError[]> {
    try {
      const data = await AsyncStorage.getItem('@error_log');
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  /**
   * Load persisted errors on startup
   */
  private async loadPersistedErrors(): Promise<void> {
    const storedErrors = await this.getStoredErrors();
    storedErrors.forEach((error) => {
      this.errors.set(error.id, error);
    });
    this.pruneErrors();
  }

  /**
   * Prune old errors from memory
   */
  private pruneErrors(): void {
    if (this.errors.size <= this.maxErrorsInMemory) {
      return;
    }

    const sortedErrors = Array.from(this.errors.values()).sort(
      (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    );

    const errorsToKeep = sortedErrors.slice(0, this.maxErrorsInMemory);
    this.errors.clear();
    errorsToKeep.forEach((error) => {
      this.errors.set(error.id, error);
    });
  }

  /**
   * Setup network listener
   */
  private setupNetworkListener(): void {
    NetInfo.addEventListener((state) => {
      const wasOffline = !this.isOnline;
      this.isOnline = state.isConnected || false;
      
      if (wasOffline && this.isOnline) {
        // Network recovered, process queued errors
        this.processErrorQueue();
      }
    });
  }

  /**
   * Get device info
   */
  private getDeviceInfo(): any {
    return {
      model: DeviceInfo.getModel(),
      os: DeviceInfo.getSystemName(),
      version: DeviceInfo.getSystemVersion(),
      appVersion: DeviceInfo.getVersion(),
      buildNumber: DeviceInfo.getBuildNumber(),
    };
  }

  /**
   * Get network info
   */
  private async getNetworkInfo(): Promise<any> {
    const netInfo = await NetInfo.fetch();
    return {
      isConnected: netInfo.isConnected,
      type: netInfo.type,
      isInternetReachable: netInfo.isInternetReachable,
    };
  }

  /**
   * Contact support
   */
  private async contactSupport(): Promise<void> {
    const supportEmail = 'support@example.com';
    const subject = 'App Error Report';
    const body = `Error Report\n\nPlease describe the issue you're experiencing:\n\n`;
    
    const url = `mailto:${supportEmail}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
    
    try {
      await Linking.openURL(url);
    } catch {
      Alert.alert('Error', 'Unable to open email client. Please contact support at ' + supportEmail);
    }
  }

  /**
   * Generate unique error ID
   */
  private generateErrorId(): string {
    return `err_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Check if object is AppError
   */
  private isAppError(error: any): error is AppError {
    return error && 
           typeof error === 'object' && 
           'id' in error && 
           'code' in error && 
           'category' in error;
  }

  /**
   * Add error listener
   */
  addErrorListener(listener: (error: AppError) => void): () => void {
    this.errorListeners.add(listener);
    return () => this.errorListeners.delete(listener);
  }

  /**
   * Notify error listeners
   */
  private notifyListeners(error: AppError): void {
    this.errorListeners.forEach((listener) => {
      try {
        listener(error);
      } catch (listenerError) {
        console.error('Error listener failed:', listenerError);
      }
    });
  }

  /**
   * Get error report
   */
  async getErrorReport(): Promise<ErrorReport> {
    const errors = Array.from(this.errors.values());
    const summary = {
      total: errors.length,
      byCategory: {} as Record<ErrorCategory, number>,
      bySeverity: {} as Record<ErrorSeverity, number>,
      recoverable: errors.filter((e) => e.isRecoverable).length,
      resolved: 0, // Would track resolved errors
    };

    // Count by category
    Object.values(ErrorCategory).forEach((category) => {
      summary.byCategory[category] = errors.filter((e) => e.category === category).length;
    });

    // Count by severity
    Object.values(ErrorSeverity).forEach((severity) => {
      summary.bySeverity[severity] = errors.filter((e) => e.severity === severity).length;
    });

    return {
      errors,
      summary,
      deviceInfo: this.getDeviceInfo(),
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Clear all errors
   */
  async clearErrors(): Promise<void> {
    this.errors.clear();
    this.errorQueue = [];
    await AsyncStorage.removeItem('@error_log');
  }

  /**
   * Get recent errors
   */
  getRecentErrors(limit: number = 10): AppError[] {
    return Array.from(this.errors.values())
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
      .slice(0, limit);
  }

  /**
   * Get errors by category
   */
  getErrorsByCategory(category: ErrorCategory): AppError[] {
    return Array.from(this.errors.values()).filter((e) => e.category === category);
  }

  /**
   * Get critical errors
   */
  getCriticalErrors(): AppError[] {
    return Array.from(this.errors.values()).filter(
      (e) => e.severity === ErrorSeverity.CRITICAL || e.severity === ErrorSeverity.HIGH
    );
  }
}

export default ErrorHandlingService.getInstance();