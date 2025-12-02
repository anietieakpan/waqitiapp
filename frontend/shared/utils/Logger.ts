/**
 * Production-ready logging utility for web applications
 * Replaces console statements with proper logging infrastructure
 */

interface LogContext {
  userId?: string;
  sessionId?: string;
  feature?: string;
  action?: string;
  metadata?: Record<string, any>;
}

export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
  FATAL = 4
}

class WebLogger {
  private static instance: WebLogger;
  private logLevel: LogLevel = process.env.NODE_ENV === 'development' ? LogLevel.DEBUG : LogLevel.WARN;
  private logQueue: Array<{ timestamp: number; level: LogLevel; message: string; context?: LogContext; error?: Error }> = [];
  private maxQueueSize = 200;
  private uploadInterval = 30000; // 30 seconds
  private isUploading = false;

  private constructor() {
    // Start periodic log upload
    setInterval(() => {
      this.uploadLogs();
    }, this.uploadInterval);

    // Upload logs on page unload
    window.addEventListener('beforeunload', () => {
      this.uploadLogs(true);
    });
  }

  public static getInstance(): WebLogger {
    if (!WebLogger.instance) {
      WebLogger.instance = new WebLogger();
    }
    return WebLogger.instance;
  }

  public setLogLevel(level: LogLevel): void {
    this.logLevel = level;
  }

  public debug(message: string, context?: LogContext): void {
    this.log(LogLevel.DEBUG, message, context);
  }

  public info(message: string, context?: LogContext): void {
    this.log(LogLevel.INFO, message, context);
  }

  public warn(message: string, context?: LogContext, error?: Error): void {
    this.log(LogLevel.WARN, message, context, error);
  }

  public error(message: string, context?: LogContext, error?: Error): void {
    this.log(LogLevel.ERROR, message, context, error);
    
    // Send to error tracking service (e.g., Sentry)
    if (window.Sentry && error) {
      window.Sentry.captureException(error, {
        tags: {
          feature: context?.feature || 'unknown',
          action: context?.action || 'unknown'
        },
        extra: context?.metadata
      });
    }
  }

  public fatal(message: string, context?: LogContext, error?: Error): void {
    this.log(LogLevel.FATAL, message, context, error);
    
    // Send to error tracking immediately
    if (window.Sentry && error) {
      window.Sentry.captureException(error, {
        level: 'fatal',
        tags: {
          feature: context?.feature || 'unknown',
          action: context?.action || 'unknown'
        },
        extra: context?.metadata
      });
    }
    
    // Force upload logs immediately
    this.uploadLogs(true);
  }

  private log(level: LogLevel, message: string, context?: LogContext, error?: Error): void {
    if (level < this.logLevel) {
      return;
    }

    const logEntry = {
      timestamp: Date.now(),
      level,
      message,
      context: {
        ...context,
        url: window.location.href,
        userAgent: navigator.userAgent,
        timestamp: new Date().toISOString()
      },
      error: error ? {
        name: error.name,
        message: error.message,
        stack: error.stack
      } : undefined
    };

    // Add to queue
    this.logQueue.push(logEntry);
    
    // Maintain queue size
    if (this.logQueue.length > this.maxQueueSize) {
      this.logQueue.shift();
    }

    // Console output in development only
    if (process.env.NODE_ENV === 'development') {
      this.consoleOutput(level, message, context, error);
    }
  }

  private consoleOutput(level: LogLevel, message: string, context?: LogContext, error?: Error): void {
    const timestamp = new Date().toISOString();
    const prefix = `[${timestamp}] [${LogLevel[level]}]`;
    const fullMessage = context ? `${prefix} ${message}` : `${prefix} ${message}`;

    switch (level) {
      case LogLevel.DEBUG:
        console.debug(fullMessage, context, error);
        break;
      case LogLevel.INFO:
        console.info(fullMessage, context);
        break;
      case LogLevel.WARN:
        console.warn(fullMessage, context, error);
        break;
      case LogLevel.ERROR:
      case LogLevel.FATAL:
        console.error(fullMessage, context, error);
        break;
    }
  }

  private async uploadLogs(force = false): Promise<void> {
    if (this.isUploading && !force) {
      return;
    }

    if (this.logQueue.length === 0) {
      return;
    }

    this.isUploading = true;

    try {
      const logs = [...this.logQueue];
      this.logQueue = [];

      // Upload to remote logging service
      await this.sendLogsToService(logs);
      
    } catch (error) {
      // If upload fails, put logs back in queue
      this.logQueue.unshift(...this.logQueue);
    } finally {
      this.isUploading = false;
    }
  }

  private async sendLogsToService(logs: any[]): Promise<void> {
    try {
      // In production, send to your logging service
      const response = await fetch('/api/v1/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          platform: 'web',
          app_version: process.env.REACT_APP_VERSION || '1.0.0',
          session_id: this.getSessionId(),
          logs
        })
      });

      if (!response.ok) {
        throw new Error(`Log upload failed: ${response.status}`);
      }
    } catch (error) {
      // Store in localStorage as fallback
      if (process.env.NODE_ENV === 'development') {
        localStorage.setItem('failed_logs', JSON.stringify(logs));
      }
    }
  }

  private getSessionId(): string {
    let sessionId = sessionStorage.getItem('sessionId');
    if (!sessionId) {
      sessionId = Date.now().toString(36) + Math.random().toString(36).substr(2);
      sessionStorage.setItem('sessionId', sessionId);
    }
    return sessionId;
  }

  public async getLogs(count = 50): Promise<any[]> {
    return this.logQueue.slice(-count);
  }

  public clearLogs(): void {
    this.logQueue = [];
  }
}

// Extend Window interface for Sentry
declare global {
  interface Window {
    Sentry?: any;
  }
}

// Export singleton instance
export const logger = WebLogger.getInstance();

// Convenience functions
export const logDebug = (message: string, context?: LogContext) => logger.debug(message, context);
export const logInfo = (message: string, context?: LogContext) => logger.info(message, context);
export const logWarn = (message: string, context?: LogContext, error?: Error) => logger.warn(message, context, error);
export const logError = (message: string, context?: LogContext, error?: Error) => logger.error(message, context, error);
export const logFatal = (message: string, context?: LogContext, error?: Error) => logger.fatal(message, context, error);

// Performance logging helpers
export const logPerformance = (operation: string, duration: number, context?: LogContext) => {
  logger.info(`Performance: ${operation} took ${duration}ms`, {
    ...context,
    feature: 'performance',
    action: operation,
    metadata: { duration }
  });
};

export const logApiCall = (endpoint: string, method: string, statusCode: number, duration: number, context?: LogContext) => {
  const level = statusCode >= 400 ? LogLevel.ERROR : LogLevel.INFO;
  const message = `API Call: ${method} ${endpoint} - ${statusCode} (${duration}ms)`;
  
  if (level === LogLevel.ERROR) {
    logger.error(message, {
      ...context,
      feature: 'api',
      action: 'api_call',
      metadata: { endpoint, method, statusCode, duration }
    });
  } else {
    logger.info(message, {
      ...context,
      feature: 'api',
      action: 'api_call',
      metadata: { endpoint, method, statusCode, duration }
    });
  }
};

export const logUserAction = (action: string, context?: LogContext) => {
  logger.info(`User Action: ${action}`, {
    ...context,
    feature: 'user_interaction',
    action
  });
};

export const logSecurityEvent = (event: string, severity: 'low' | 'medium' | 'high' | 'critical', context?: LogContext) => {
  const level = severity === 'critical' ? LogLevel.FATAL : 
                severity === 'high' ? LogLevel.ERROR :
                severity === 'medium' ? LogLevel.WARN : LogLevel.INFO;
  
  logger.log(level, `Security Event: ${event}`, {
    ...context,
    feature: 'security',
    action: event,
    metadata: { severity }
  });
};

// High-value transaction logger
export const logHighValueTransaction = (transaction: any, context?: LogContext) => {
  logger.warn('High-value transaction detected', {
    ...context,
    feature: 'transactions',
    action: 'high_value_alert',
    metadata: {
      transactionId: transaction.id,
      amount: transaction.amount,
      currency: transaction.currency,
      userId: transaction.userId
    }
  });
};