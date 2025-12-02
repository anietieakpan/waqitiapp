/**
 * Production-grade Logging Service
 * Provides structured logging with multiple transports and security features
 */

import * as Sentry from '@sentry/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import DeviceInfo from 'react-native-device-info';
import NetInfo from '@react-native-community/netinfo';

export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
  FATAL = 4
}

export interface LogEntry {
  timestamp: Date;
  level: LogLevel;
  message: string;
  category?: string;
  userId?: string;
  sessionId?: string;
  correlationId?: string;
  metadata?: Record<string, unknown>;
  stackTrace?: string;
  deviceInfo?: DeviceMetadata;
  networkInfo?: NetworkMetadata;
  performanceMetrics?: PerformanceMetrics;
}

export interface DeviceMetadata {
  deviceId: string;
  deviceType: string;
  osVersion: string;
  appVersion: string;
  buildNumber: string;
  brand: string;
  model: string;
  isTablet: boolean;
  hasNotch: boolean;
  memoryUsage: number;
  batteryLevel: number;
  isCharging: boolean;
}

export interface NetworkMetadata {
  type: string;
  isConnected: boolean;
  isInternetReachable: boolean;
  details?: Record<string, unknown>;
}

export interface PerformanceMetrics {
  duration?: number;
  memoryUsage?: number;
  cpuUsage?: number;
  fps?: number;
}

export interface LoggerConfig {
  minLevel: LogLevel;
  enableConsole: boolean;
  enableFile: boolean;
  enableRemote: boolean;
  enableSentry: boolean;
  maxFileSize: number;
  maxFiles: number;
  remoteEndpoint?: string;
  sentryDsn?: string;
  sanitizeKeys: string[];
  enablePerformanceTracking: boolean;
  enableNetworkLogging: boolean;
  enableDeviceInfo: boolean;
}

class LoggingService {
  private static instance: LoggingService;
  private config: LoggerConfig;
  private sessionId: string;
  private userId?: string;
  private logBuffer: LogEntry[] = [];
  private isInitialized = false;
  private performanceObserver?: PerformanceObserver;

  private readonly DEFAULT_CONFIG: LoggerConfig = {
    minLevel: __DEV__ ? LogLevel.DEBUG : LogLevel.INFO,
    enableConsole: __DEV__,
    enableFile: true,
    enableRemote: !__DEV__,
    enableSentry: !__DEV__,
    maxFileSize: 5 * 1024 * 1024, // 5MB
    maxFiles: 10,
    sanitizeKeys: ['password', 'token', 'secret', 'apiKey', 'creditCard', 'ssn', 'pin'],
    enablePerformanceTracking: true,
    enableNetworkLogging: true,
    enableDeviceInfo: true
  };

  private constructor() {
    this.config = this.DEFAULT_CONFIG;
    this.sessionId = this.generateSessionId();
  }

  public static getInstance(): LoggingService {
    if (!LoggingService.instance) {
      LoggingService.instance = new LoggingService();
    }
    return LoggingService.instance;
  }

  public async initialize(config?: Partial<LoggerConfig>): Promise<void> {
    if (this.isInitialized) return;

    this.config = { ...this.DEFAULT_CONFIG, ...config };

    // Initialize Sentry if enabled
    if (this.config.enableSentry && this.config.sentryDsn) {
      Sentry.init({
        dsn: this.config.sentryDsn,
        environment: __DEV__ ? 'development' : 'production',
        tracesSampleRate: 0.2,
        attachStacktrace: true,
        beforeSend: (event) => this.sanitizeEvent(event)
      });
    }

    // Setup performance tracking
    if (this.config.enablePerformanceTracking) {
      this.setupPerformanceTracking();
    }

    // Setup crash handler
    this.setupCrashHandler();

    // Flush buffered logs
    await this.flushBuffer();

    this.isInitialized = true;
    this.info('LoggingService initialized', { config: this.sanitizeData(config) });
  }

  public setUserId(userId: string): void {
    this.userId = userId;
    if (this.config.enableSentry) {
      Sentry.setUser({ id: userId });
    }
  }

  public clearUserId(): void {
    this.userId = undefined;
    if (this.config.enableSentry) {
      Sentry.setUser(null);
    }
  }

  // Main logging methods
  public debug(message: string, metadata?: Record<string, unknown>): void {
    this.log(LogLevel.DEBUG, message, metadata);
  }

  public info(message: string, metadata?: Record<string, unknown>): void {
    this.log(LogLevel.INFO, message, metadata);
  }

  public warn(message: string, metadata?: Record<string, unknown>): void {
    this.log(LogLevel.WARN, message, metadata);
  }

  public error(message: string, error?: Error | unknown, metadata?: Record<string, unknown>): void {
    const errorMetadata = this.extractErrorMetadata(error);
    this.log(LogLevel.ERROR, message, { ...errorMetadata, ...metadata });
    
    if (this.config.enableSentry && error instanceof Error) {
      Sentry.captureException(error, {
        extra: metadata
      });
    }
  }

  public fatal(message: string, error?: Error | unknown, metadata?: Record<string, unknown>): void {
    const errorMetadata = this.extractErrorMetadata(error);
    this.log(LogLevel.FATAL, message, { ...errorMetadata, ...metadata });
    
    if (this.config.enableSentry && error instanceof Error) {
      Sentry.captureException(error, {
        level: 'fatal',
        extra: metadata
      });
    }
  }

  // Performance tracking
  public startTimer(label: string): () => void {
    const startTime = performance.now();
    return () => {
      const duration = performance.now() - startTime;
      this.debug(`Timer: ${label}`, { duration: `${duration.toFixed(2)}ms` });
      return duration;
    };
  }

  public async trackAsync<T>(
    label: string,
    operation: () => Promise<T>,
    metadata?: Record<string, unknown>
  ): Promise<T> {
    const startTime = performance.now();
    try {
      const result = await operation();
      const duration = performance.now() - startTime;
      this.debug(`Async operation: ${label}`, {
        duration: `${duration.toFixed(2)}ms`,
        success: true,
        ...metadata
      });
      return result;
    } catch (error) {
      const duration = performance.now() - startTime;
      this.error(`Async operation failed: ${label}`, error, {
        duration: `${duration.toFixed(2)}ms`,
        ...metadata
      });
      throw error;
    }
  }

  // Network request logging
  public logNetworkRequest(
    method: string,
    url: string,
    status?: number,
    duration?: number,
    metadata?: Record<string, unknown>
  ): void {
    if (!this.config.enableNetworkLogging) return;

    const level = status && status >= 400 ? LogLevel.ERROR : LogLevel.DEBUG;
    this.log(level, `Network: ${method} ${url}`, {
      status,
      duration: duration ? `${duration}ms` : undefined,
      ...metadata
    });
  }

  // Analytics events
  public trackEvent(
    eventName: string,
    properties?: Record<string, unknown>
  ): void {
    this.info(`Analytics Event: ${eventName}`, this.sanitizeData(properties));
    
    if (this.config.enableSentry) {
      Sentry.addBreadcrumb({
        message: eventName,
        category: 'analytics',
        data: properties
      });
    }
  }

  // User actions
  public trackUserAction(
    action: string,
    target?: string,
    metadata?: Record<string, unknown>
  ): void {
    this.info(`User Action: ${action}`, {
      target,
      ...this.sanitizeData(metadata)
    });
  }

  // Screen tracking
  public trackScreen(screenName: string, metadata?: Record<string, unknown>): void {
    this.info(`Screen View: ${screenName}`, this.sanitizeData(metadata));
    
    if (this.config.enableSentry) {
      Sentry.addBreadcrumb({
        message: `Screen: ${screenName}`,
        category: 'navigation'
      });
    }
  }

  // Private methods
  private async log(
    level: LogLevel,
    message: string,
    metadata?: Record<string, unknown>
  ): Promise<void> {
    if (level < this.config.minLevel) return;

    const entry = await this.createLogEntry(level, message, metadata);

    // Buffer logs if not initialized
    if (!this.isInitialized) {
      this.logBuffer.push(entry);
      return;
    }

    // Write to different transports
    if (this.config.enableConsole) {
      this.writeToConsole(entry);
    }

    if (this.config.enableFile) {
      await this.writeToFile(entry);
    }

    if (this.config.enableRemote) {
      await this.writeToRemote(entry);
    }
  }

  private async createLogEntry(
    level: LogLevel,
    message: string,
    metadata?: Record<string, unknown>
  ): Promise<LogEntry> {
    const entry: LogEntry = {
      timestamp: new Date(),
      level,
      message,
      userId: this.userId,
      sessionId: this.sessionId,
      correlationId: this.generateCorrelationId(),
      metadata: this.sanitizeData(metadata)
    };

    // Add device info if enabled
    if (this.config.enableDeviceInfo) {
      entry.deviceInfo = await this.getDeviceInfo();
    }

    // Add network info if enabled
    if (this.config.enableNetworkLogging) {
      entry.networkInfo = await this.getNetworkInfo();
    }

    // Add stack trace for errors
    if (level >= LogLevel.ERROR) {
      entry.stackTrace = new Error().stack;
    }

    return entry;
  }

  private writeToConsole(entry: LogEntry): void {
    const timestamp = entry.timestamp.toISOString();
    const level = LogLevel[entry.level];
    const message = `[${timestamp}] [${level}] ${entry.message}`;

    switch (entry.level) {
      case LogLevel.DEBUG:
        console.log(message, entry.metadata);
        break;
      case LogLevel.INFO:
        console.info(message, entry.metadata);
        break;
      case LogLevel.WARN:
        console.warn(message, entry.metadata);
        break;
      case LogLevel.ERROR:
      case LogLevel.FATAL:
        console.error(message, entry.metadata);
        if (entry.stackTrace) {
          console.error(entry.stackTrace);
        }
        break;
    }
  }

  private async writeToFile(entry: LogEntry): Promise<void> {
    try {
      const filename = `waqiti_${new Date().toISOString().split('T')[0]}.log`;
      const logLine = JSON.stringify(entry) + '\n';
      
      // Get current file size
      const currentLogs = await AsyncStorage.getItem(filename) || '';
      
      // Check if we need to rotate
      if (currentLogs.length + logLine.length > this.config.maxFileSize) {
        await this.rotateLogFiles();
      }
      
      // Append to file
      await AsyncStorage.setItem(filename, currentLogs + logLine);
    } catch (error) {
      // Fail silently to avoid infinite loop
      if (__DEV__) {
        console.error('Failed to write log to file:', error);
      }
    }
  }

  private async writeToRemote(entry: LogEntry): Promise<void> {
    if (!this.config.remoteEndpoint) return;

    try {
      await fetch(this.config.remoteEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Session-Id': this.sessionId,
          'X-User-Id': this.userId || 'anonymous'
        },
        body: JSON.stringify(entry)
      });
    } catch (error) {
      // Fail silently to avoid infinite loop
      if (__DEV__) {
        console.error('Failed to send log to remote:', error);
      }
    }
  }

  private async rotateLogFiles(): Promise<void> {
    try {
      const keys = await AsyncStorage.getAllKeys();
      const logFiles = keys
        .filter(key => key.startsWith('waqiti_'))
        .sort()
        .reverse();
      
      // Remove oldest files if we have too many
      if (logFiles.length >= this.config.maxFiles) {
        const filesToRemove = logFiles.slice(this.config.maxFiles - 1);
        await AsyncStorage.multiRemove(filesToRemove);
      }
    } catch (error) {
      // Fail silently
      if (__DEV__) {
        console.error('Failed to rotate log files:', error);
      }
    }
  }

  private async flushBuffer(): Promise<void> {
    const buffer = [...this.logBuffer];
    this.logBuffer = [];
    
    for (const entry of buffer) {
      await this.log(entry.level, entry.message, entry.metadata);
    }
  }

  private sanitizeData(data?: Record<string, unknown>): Record<string, unknown> | undefined {
    if (!data) return undefined;

    const sanitized: Record<string, unknown> = {};
    
    for (const [key, value] of Object.entries(data)) {
      if (this.config.sanitizeKeys.some(k => key.toLowerCase().includes(k.toLowerCase()))) {
        sanitized[key] = '[REDACTED]';
      } else if (typeof value === 'object' && value !== null) {
        sanitized[key] = this.sanitizeData(value as Record<string, unknown>);
      } else {
        sanitized[key] = value;
      }
    }
    
    return sanitized;
  }

  private sanitizeEvent(event: any): any {
    // Sanitize Sentry events
    if (event.extra) {
      event.extra = this.sanitizeData(event.extra);
    }
    if (event.contexts) {
      event.contexts = this.sanitizeData(event.contexts);
    }
    return event;
  }

  private extractErrorMetadata(error: Error | unknown): Record<string, unknown> {
    if (!error) return {};
    
    if (error instanceof Error) {
      return {
        errorName: error.name,
        errorMessage: error.message,
        errorStack: error.stack
      };
    }
    
    return {
      error: String(error)
    };
  }

  private async getDeviceInfo(): Promise<DeviceMetadata> {
    return {
      deviceId: await DeviceInfo.getUniqueId(),
      deviceType: DeviceInfo.getDeviceType(),
      osVersion: DeviceInfo.getSystemVersion(),
      appVersion: DeviceInfo.getVersion(),
      buildNumber: DeviceInfo.getBuildNumber(),
      brand: DeviceInfo.getBrand(),
      model: DeviceInfo.getModel(),
      isTablet: DeviceInfo.isTablet(),
      hasNotch: DeviceInfo.hasNotch(),
      memoryUsage: await DeviceInfo.getUsedMemory(),
      batteryLevel: await DeviceInfo.getBatteryLevel(),
      isCharging: await DeviceInfo.isBatteryCharging()
    };
  }

  private async getNetworkInfo(): Promise<NetworkMetadata> {
    const state = await NetInfo.fetch();
    return {
      type: state.type,
      isConnected: state.isConnected || false,
      isInternetReachable: state.isInternetReachable || false,
      details: state.details as Record<string, unknown>
    };
  }

  private setupPerformanceTracking(): void {
    if (typeof PerformanceObserver === 'undefined') return;

    try {
      this.performanceObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        entries.forEach((entry) => {
          if (entry.duration > 100) {
            this.debug(`Performance: ${entry.name}`, {
              duration: `${entry.duration.toFixed(2)}ms`,
              startTime: entry.startTime
            });
          }
        });
      });

      this.performanceObserver.observe({ entryTypes: ['measure', 'navigation'] });
    } catch (error) {
      // Performance API not available
    }
  }

  private setupCrashHandler(): void {
    const originalHandler = ErrorUtils.getGlobalHandler();
    
    ErrorUtils.setGlobalHandler((error, isFatal) => {
      this.fatal('Unhandled error', error, { isFatal });
      
      if (originalHandler) {
        originalHandler(error, isFatal);
      }
    });
  }

  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private generateCorrelationId(): string {
    return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // Cleanup
  public async cleanup(): Promise<void> {
    await this.flushBuffer();
    
    if (this.performanceObserver) {
      this.performanceObserver.disconnect();
    }
    
    if (this.config.enableSentry) {
      await Sentry.close();
    }
  }
}

// Export singleton instance
export const Logger = LoggingService.getInstance();

// Export convenience functions
export const debug = (message: string, metadata?: Record<string, unknown>) => 
  Logger.debug(message, metadata);

export const info = (message: string, metadata?: Record<string, unknown>) => 
  Logger.info(message, metadata);

export const warn = (message: string, metadata?: Record<string, unknown>) => 
  Logger.warn(message, metadata);

export const error = (message: string, error?: Error | unknown, metadata?: Record<string, unknown>) => 
  Logger.error(message, error, metadata);

export const fatal = (message: string, error?: Error | unknown, metadata?: Record<string, unknown>) => 
  Logger.fatal(message, error, metadata);

export const trackEvent = (eventName: string, properties?: Record<string, unknown>) => 
  Logger.trackEvent(eventName, properties);

export const trackScreen = (screenName: string, metadata?: Record<string, unknown>) => 
  Logger.trackScreen(screenName, metadata);

export const trackUserAction = (action: string, target?: string, metadata?: Record<string, unknown>) => 
  Logger.trackUserAction(action, target, metadata);

export const startTimer = (label: string) => 
  Logger.startTimer(label);

export const trackAsync = <T>(label: string, operation: () => Promise<T>, metadata?: Record<string, unknown>) => 
  Logger.trackAsync(label, operation, metadata);

export default Logger;