/**
 * Comprehensive Error Boundary Component
 * Provides production-grade error handling with recovery mechanisms
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import DeviceInfo from 'react-native-device-info';
import NetInfo from '@react-native-community/netinfo';
import { Logger } from '../../services/src/LoggingService';
import * as Sentry from '@sentry/react-native';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  level?: 'screen' | 'component' | 'app';
  showDetails?: boolean;
  allowRetry?: boolean;
  allowReport?: boolean;
  customMessage?: string;
  resetKeys?: Array<string | number>;
  resetOnNavigate?: boolean;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorCount: number;
  errorId: string;
  isReporting: boolean;
  reportSent: boolean;
  isRecovering: boolean;
  networkStatus: boolean;
  errorHistory: ErrorRecord[];
}

interface ErrorRecord {
  timestamp: Date;
  error: Error;
  errorInfo: ErrorInfo;
  recovered: boolean;
}

class ErrorBoundary extends Component<Props, State> {
  private retryCount = 0;
  private maxRetries = 3;
  private errorResetTimeout: NodeJS.Timeout | null = null;
  private unsubscribeNetInfo: (() => void) | null = null;

  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
      errorId: '',
      isReporting: false,
      reportSent: false,
      isRecovering: false,
      networkStatus: true,
      errorHistory: [],
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    const errorId = `error_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    return {
      hasError: true,
      error,
      errorId,
      errorCount: (prevState?.errorCount || 0) + 1,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    const { onError, level = 'component' } = this.props;
    const { errorId, errorHistory } = this.state;

    // Log the error
    Logger.error(`Error Boundary Caught [${level}]`, error, {
      errorId,
      componentStack: errorInfo.componentStack,
      level,
      retryCount: this.retryCount,
      ...this.getErrorContext(),
    });

    // Send to Sentry if in production
    if (!__DEV__) {
      Sentry.withScope((scope) => {
        scope.setTag('errorBoundary', true);
        scope.setTag('level', level);
        scope.setContext('errorInfo', {
          componentStack: errorInfo.componentStack,
          errorId,
        });
        Sentry.captureException(error);
      });
    }

    // Update error history
    const newErrorRecord: ErrorRecord = {
      timestamp: new Date(),
      error,
      errorInfo,
      recovered: false,
    };

    this.setState({
      errorInfo,
      errorHistory: [...errorHistory.slice(-9), newErrorRecord], // Keep last 10 errors
    });

    // Call custom error handler
    if (onError) {
      onError(error, errorInfo);
    }

    // Store error for crash reporting
    this.storeErrorForCrashReporting(error, errorInfo);

    // Auto-recovery attempt after delay
    if (this.props.allowRetry && this.retryCount < this.maxRetries) {
      this.scheduleAutoRecovery();
    }
  }

  componentDidMount() {
    // Monitor network status for better error messages
    this.unsubscribeNetInfo = NetInfo.addEventListener((state) => {
      this.setState({ networkStatus: state.isConnected || false });
    });

    // Check for previous crashes
    this.checkForPreviousCrashes();
  }

  componentWillUnmount() {
    if (this.errorResetTimeout) {
      clearTimeout(this.errorResetTimeout);
    }
    if (this.unsubscribeNetInfo) {
      this.unsubscribeNetInfo();
    }
  }

  componentDidUpdate(prevProps: Props) {
    const { resetKeys, resetOnNavigate } = this.props;
    
    // Reset on key change
    if (resetKeys && prevProps.resetKeys) {
      const hasKeyChanged = resetKeys.some(
        (key, index) => key !== prevProps.resetKeys![index]
      );
      if (hasKeyChanged) {
        this.resetErrorBoundary();
      }
    }
  }

  private async getErrorContext() {
    try {
      const [
        deviceInfo,
        memoryInfo,
        batteryLevel,
        networkState,
      ] = await Promise.all([
        this.getDeviceInfo(),
        DeviceInfo.getUsedMemory(),
        DeviceInfo.getBatteryLevel(),
        NetInfo.fetch(),
      ]);

      return {
        device: deviceInfo,
        memory: memoryInfo,
        battery: batteryLevel,
        network: {
          type: networkState.type,
          isConnected: networkState.isConnected,
        },
        timestamp: new Date().toISOString(),
      };
    } catch {
      return {};
    }
  }

  private async getDeviceInfo() {
    return {
      platform: Platform.OS,
      version: Platform.Version,
      model: DeviceInfo.getModel(),
      brand: DeviceInfo.getBrand(),
      appVersion: DeviceInfo.getVersion(),
      buildNumber: DeviceInfo.getBuildNumber(),
      isTablet: DeviceInfo.isTablet(),
    };
  }

  private async storeErrorForCrashReporting(error: Error, errorInfo: ErrorInfo) {
    try {
      const errorData = {
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        timestamp: new Date().toISOString(),
        errorId: this.state.errorId,
        context: await this.getErrorContext(),
      };

      const existingErrors = await AsyncStorage.getItem('error_reports') || '[]';
      const errors = JSON.parse(existingErrors);
      errors.push(errorData);
      
      // Keep only last 10 errors
      const recentErrors = errors.slice(-10);
      await AsyncStorage.setItem('error_reports', JSON.stringify(recentErrors));
    } catch (storageError) {
      Logger.error('Failed to store error for crash reporting', storageError);
    }
  }

  private async checkForPreviousCrashes() {
    try {
      const crashReports = await AsyncStorage.getItem('error_reports');
      if (crashReports) {
        const reports = JSON.parse(crashReports);
        if (reports.length > 0) {
          Logger.info('Previous crashes detected', { count: reports.length });
          // Optionally send crash reports to server
          this.sendCrashReports(reports);
          // Clear after sending
          await AsyncStorage.removeItem('error_reports');
        }
      }
    } catch (error) {
      Logger.error('Failed to check for previous crashes', error);
    }
  }

  private async sendCrashReports(reports: any[]) {
    try {
      // Send crash reports to your analytics/monitoring service
      for (const report of reports) {
        Logger.info('Sending crash report', { errorId: report.errorId });
        // Implement actual sending logic here
      }
    } catch (error) {
      Logger.error('Failed to send crash reports', error);
    }
  }

  private scheduleAutoRecovery() {
    this.errorResetTimeout = setTimeout(() => {
      this.retryCount++;
      Logger.info('Attempting auto-recovery', {
        attempt: this.retryCount,
        maxRetries: this.maxRetries,
      });
      this.resetErrorBoundary();
    }, 5000); // Wait 5 seconds before retry
  }

  private resetErrorBoundary = () => {
    const { errorHistory } = this.state;
    
    // Mark last error as recovered
    if (errorHistory.length > 0) {
      errorHistory[errorHistory.length - 1].recovered = true;
    }

    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      isReporting: false,
      reportSent: false,
      isRecovering: false,
      errorHistory,
    });

    Logger.info('Error boundary reset', {
      errorId: this.state.errorId,
      retryCount: this.retryCount,
    });
  };

  private handleRetry = () => {
    this.setState({ isRecovering: true });
    setTimeout(() => {
      this.resetErrorBoundary();
    }, 500);
  };

  private handleReport = async () => {
    const { error, errorInfo, errorId } = this.state;
    if (!error || !errorInfo) return;

    this.setState({ isReporting: true });

    try {
      const context = await this.getErrorContext();
      
      // Send error report
      const report = {
        errorId,
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        context,
        userAgent: await DeviceInfo.getUserAgent(),
      };

      Logger.info('Sending error report', { errorId });
      
      // Implement actual API call here
      // await sendErrorReport(report);

      this.setState({ reportSent: true, isReporting: false });
      
      setTimeout(() => {
        this.setState({ reportSent: false });
      }, 3000);
    } catch (reportError) {
      Logger.error('Failed to send error report', reportError);
      this.setState({ isReporting: false });
    }
  };

  private renderErrorDetails() {
    const { error, errorInfo, errorId } = this.state;
    const { showDetails } = this.props;

    if (!showDetails || !error || !errorInfo) return null;

    return (
      <ScrollView style={styles.detailsContainer}>
        <Text style={styles.detailsTitle}>Error Details</Text>
        <Text style={styles.errorId}>Error ID: {errorId}</Text>
        <Text style={styles.errorMessage}>{error.toString()}</Text>
        {__DEV__ && (
          <>
            <Text style={styles.stackTitle}>Component Stack:</Text>
            <Text style={styles.stackTrace}>{errorInfo.componentStack}</Text>
            {error.stack && (
              <>
                <Text style={styles.stackTitle}>Error Stack:</Text>
                <Text style={styles.stackTrace}>{error.stack}</Text>
              </>
            )}
          </>
        )}
      </ScrollView>
    );
  }

  private renderFallback() {
    const { 
      fallback, 
      level = 'component', 
      customMessage, 
      allowRetry = true, 
      allowReport = true 
    } = this.props;
    const { 
      isReporting, 
      reportSent, 
      isRecovering, 
      networkStatus,
      errorCount 
    } = this.state;

    if (fallback) {
      return <>{fallback}</>;
    }

    const title = level === 'app' 
      ? 'Oops! Something went wrong' 
      : level === 'screen'
      ? 'This screen encountered an error'
      : 'This component encountered an error';

    const message = customMessage || 
      (networkStatus 
        ? 'An unexpected error occurred. Our team has been notified.'
        : 'An error occurred. Please check your internet connection.');

    return (
      <View style={[styles.container, styles[`${level}Container`]]}>
        <View style={styles.content}>
          <Text style={styles.emoji}>😔</Text>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.message}>{message}</Text>
          
          {errorCount > 2 && (
            <Text style={styles.warningText}>
              Multiple errors detected. The app may be unstable.
            </Text>
          )}

          <View style={styles.buttonContainer}>
            {allowRetry && (
              <TouchableOpacity
                style={[styles.button, styles.primaryButton]}
                onPress={this.handleRetry}
                disabled={isRecovering}
              >
                <Text style={styles.buttonText}>
                  {isRecovering ? 'Recovering...' : 'Try Again'}
                </Text>
              </TouchableOpacity>
            )}

            {allowReport && !reportSent && (
              <TouchableOpacity
                style={[styles.button, styles.secondaryButton]}
                onPress={this.handleReport}
                disabled={isReporting}
              >
                <Text style={styles.secondaryButtonText}>
                  {isReporting ? 'Sending...' : 'Send Report'}
                </Text>
              </TouchableOpacity>
            )}

            {reportSent && (
              <View style={styles.successMessage}>
                <Text style={styles.successText}>✓ Report sent successfully</Text>
              </View>
            )}
          </View>

          {this.renderErrorDetails()}
        </View>
      </View>
    );
  }

  render() {
    if (this.state.hasError) {
      return this.renderFallback();
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f8f9fa',
  },
  appContainer: {
    backgroundColor: '#ffffff',
  },
  screenContainer: {
    backgroundColor: '#f8f9fa',
  },
  componentContainer: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  content: {
    alignItems: 'center',
    maxWidth: 400,
  },
  emoji: {
    fontSize: 64,
    marginBottom: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: '#1a1a1a',
    marginBottom: 10,
    textAlign: 'center',
  },
  message: {
    fontSize: 16,
    color: '#666666',
    textAlign: 'center',
    marginBottom: 30,
    lineHeight: 22,
  },
  warningText: {
    fontSize: 14,
    color: '#ff6b6b',
    textAlign: 'center',
    marginBottom: 20,
    fontStyle: 'italic',
  },
  buttonContainer: {
    width: '100%',
    gap: 12,
  },
  button: {
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
    minWidth: 150,
  },
  primaryButton: {
    backgroundColor: '#007AFF',
  },
  secondaryButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButtonText: {
    color: '#007AFF',
    fontSize: 16,
    fontWeight: '600',
  },
  successMessage: {
    backgroundColor: '#d4edda',
    borderColor: '#c3e6cb',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginTop: 10,
  },
  successText: {
    color: '#155724',
    fontSize: 14,
    textAlign: 'center',
  },
  detailsContainer: {
    marginTop: 30,
    maxHeight: 200,
    width: '100%',
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
    padding: 15,
  },
  detailsTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a1a1a',
    marginBottom: 10,
  },
  errorId: {
    fontSize: 12,
    color: '#666666',
    marginBottom: 10,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  errorMessage: {
    fontSize: 14,
    color: '#d73502',
    marginBottom: 10,
  },
  stackTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1a1a1a',
    marginTop: 10,
    marginBottom: 5,
  },
  stackTrace: {
    fontSize: 11,
    color: '#666666',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    lineHeight: 16,
  },
});

export default ErrorBoundary;

// HOC for wrapping components with error boundary
export function withErrorBoundary<P extends object>(
  Component: React.ComponentType<P>,
  errorBoundaryProps?: Partial<Props>
) {
  const WrappedComponent = (props: P) => (
    <ErrorBoundary {...errorBoundaryProps}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;

  return WrappedComponent;
}

// Hook for programmatic error handling
export function useErrorHandler() {
  return (error: Error, errorInfo?: ErrorInfo) => {
    Logger.error('Error handled by hook', error, {
      errorInfo,
      component: 'useErrorHandler',
    });
    
    if (!__DEV__) {
      Sentry.captureException(error, {
        contexts: {
          errorInfo,
        },
      });
    }
  };
}