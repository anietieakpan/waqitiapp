import React, { Component, ErrorInfo, ReactNode } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Alert,
  Collapse,
  IconButton,
  Divider,
  Chip,
} from '@mui/material';
import ErrorIcon from '@mui/icons-material/Error';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import BugReportIcon from '@mui/icons-material/BugReport';
import HomeIcon from '@mui/icons-material/Home';
import CopyIcon from '@mui/icons-material/ContentCopy';;

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  isolate?: boolean; // If true, only this component fails, not the whole app
  resetOnPropsChange?: boolean;
  resetKeys?: Array<string | number>;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorId: string;
  showDetails: boolean;
}

class ErrorBoundary extends Component<Props, State> {
  private resetTimeoutId: number | null = null;

  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: '',
      showDetails: false,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return {
      hasError: true,
      error,
      errorId: `error_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({
      errorInfo,
    });

    // Log error to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error('ErrorBoundary caught an error:', error, errorInfo);
    }

    // Call custom error handler
    this.props.onError?.(error, errorInfo);

    // Report error to monitoring service
    this.reportError(error, errorInfo);
  }

  componentDidUpdate(prevProps: Props) {
    const { resetKeys, resetOnPropsChange } = this.props;
    const { hasError } = this.state;

    if (hasError && prevProps.resetKeys !== resetKeys) {
      if (resetKeys) {
        const hasResetKeyChanged = resetKeys.some(
          (resetKey, idx) => prevProps.resetKeys?.[idx] !== resetKey
        );
        if (hasResetKeyChanged) {
          this.resetErrorBoundary();
        }
      }
    }

    if (hasError && resetOnPropsChange && prevProps.children !== this.props.children) {
      this.resetErrorBoundary();
    }
  }

  componentWillUnmount() {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
    }
  }

  resetErrorBoundary = () => {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
    }

    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: '',
      showDetails: false,
    });
  };

  handleRetry = () => {
    this.resetErrorBoundary();
  };

  handleReload = () => {
    window.location.reload();
  };

  handleGoHome = () => {
    window.location.href = '/';
  };

  toggleDetails = () => {
    this.setState(prev => ({ showDetails: !prev.showDetails }));
  };

  copyErrorDetails = async () => {
    const { error, errorInfo, errorId } = this.state;
    const errorDetails = {
      errorId,
      message: error?.message,
      stack: error?.stack,
      componentStack: errorInfo?.componentStack,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
    };

    try {
      await navigator.clipboard.writeText(JSON.stringify(errorDetails, null, 2));
      // You could show a toast here
    } catch (err) {
      console.error('Failed to copy error details:', err);
    }
  };

  reportError = async (error: Error, errorInfo: ErrorInfo) => {
    try {
      // Report to your error monitoring service (e.g., Sentry, LogRocket, etc.)
      const errorReport = {
        message: error.message,
        stack: error.stack,
        componentStack: errorInfo.componentStack,
        errorId: this.state.errorId,
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent,
        url: window.location.href,
        userId: localStorage.getItem('userId'), // If available
      };

      // Example API call to your error reporting service
      // await fetch('/api/v1/errors/report', {
      //   method: 'POST',
      //   headers: { 'Content-Type': 'application/json' },
      //   body: JSON.stringify(errorReport),
      // });

      console.log('Error reported:', errorReport);
    } catch (reportingError) {
      console.error('Failed to report error:', reportingError);
    }
  };

  getErrorSeverity = (error: Error): 'low' | 'medium' | 'high' | 'critical' => {
    const message = error.message.toLowerCase();
    
    if (message.includes('network') || message.includes('fetch')) {
      return 'medium';
    }
    
    if (message.includes('permission') || message.includes('unauthorized')) {
      return 'high';
    }
    
    if (message.includes('payment') || message.includes('transaction')) {
      return 'critical';
    }
    
    return 'low';
  };

  getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical': return 'error';
      case 'high': return 'warning';
      case 'medium': return 'info';
      case 'low': return 'success';
      default: return 'error';
    }
  };

  render() {
    const { hasError, error, errorInfo, errorId, showDetails } = this.state;
    const { children, fallback, isolate } = this.props;

    if (hasError && error) {
      if (fallback) {
        return fallback;
      }

      const severity = this.getErrorSeverity(error);
      const isProduction = process.env.NODE_ENV === 'production';

      return (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: isolate ? 200 : '50vh',
            p: 3,
          }}
        >
          <Paper
            elevation={3}
            sx={{
              p: 4,
              maxWidth: 600,
              width: '100%',
              textAlign: 'center',
            }}
          >
            {/* Error Icon and Title */}
            <Box mb={3}>
              <ErrorIcon
                sx={{
                  fontSize: 64,
                  color: 'error.main',
                  mb: 2,
                }}
              />
              <Typography variant="h4" gutterBottom fontWeight="bold">
                {isolate ? 'Component Error' : 'Oops! Something went wrong'}
              </Typography>
              <Typography variant="body1" color="text.secondary" gutterBottom>
                {isolate 
                  ? 'This component encountered an error, but the rest of the app should work normally.'
                  : 'We apologize for the inconvenience. Our team has been notified and is working on a fix.'
                }
              </Typography>
            </Box>

            {/* Error Severity Badge */}
            <Box mb={3}>
              <Chip
                label={`${severity.toUpperCase()} SEVERITY`}
                color={this.getSeverityColor(severity) as any}
                variant="outlined"
                icon={<BugReportIcon />}
              />
            </Box>

            {/* Error ID */}
            <Alert severity="info" sx={{ mb: 3, textAlign: 'left' }}>
              <Typography variant="body2">
                <strong>Error ID:</strong> {errorId}
              </Typography>
              <Typography variant="body2" sx={{ mt: 1 }}>
                Please reference this ID when contacting support.
              </Typography>
            </Alert>

            {/* Action Buttons */}
            <Box mb={3} display="flex" gap={2} justifyContent="center" flexWrap="wrap">
              <Button
                variant="contained"
                startIcon={<RefreshIcon />}
                onClick={this.handleRetry}
                size="large"
              >
                Try Again
              </Button>
              
              {!isolate && (
                <>
                  <Button
                    variant="outlined"
                    startIcon={<HomeIcon />}
                    onClick={this.handleGoHome}
                    size="large"
                  >
                    Go Home
                  </Button>
                  
                  <Button
                    variant="outlined"
                    startIcon={<RefreshIcon />}
                    onClick={this.handleReload}
                    size="large"
                  >
                    Reload Page
                  </Button>
                </>
              )}
            </Box>

            {/* Error Details (Development/Debug) */}
            {(!isProduction || process.env.REACT_APP_DEBUG === 'true') && (
              <Box>
                <Divider sx={{ mb: 2 }} />
                
                <Box display="flex" alignItems="center" justifyContent="center" gap={1} mb={2}>
                  <Button
                    variant="text"
                    size="small"
                    onClick={this.toggleDetails}
                    endIcon={showDetails ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                  >
                    {showDetails ? 'Hide' : 'Show'} Error Details
                  </Button>
                  
                  <IconButton size="small" onClick={this.copyErrorDetails} title="Copy error details">
                    <CopyIcon />
                  </IconButton>
                </Box>

                <Collapse in={showDetails}>
                  <Box textAlign="left">
                    {error.message && (
                      <Alert severity="error" sx={{ mb: 2 }}>
                        <Typography variant="body2" component="pre" sx={{ whiteSpace: 'pre-wrap' }}>
                          <strong>Error Message:</strong><br />
                          {error.message}
                        </Typography>
                      </Alert>
                    )}

                    {error.stack && (
                      <Alert severity="warning" sx={{ mb: 2 }}>
                        <Typography variant="body2" component="pre" sx={{ 
                          whiteSpace: 'pre-wrap',
                          fontSize: '0.75rem',
                          fontFamily: 'monospace',
                          maxHeight: 200,
                          overflow: 'auto',
                        }}>
                          <strong>Stack Trace:</strong><br />
                          {error.stack}
                        </Typography>
                      </Alert>
                    )}

                    {errorInfo?.componentStack && (
                      <Alert severity="info" sx={{ mb: 2 }}>
                        <Typography variant="body2" component="pre" sx={{ 
                          whiteSpace: 'pre-wrap',
                          fontSize: '0.75rem',
                          fontFamily: 'monospace',
                          maxHeight: 200,
                          overflow: 'auto',
                        }}>
                          <strong>Component Stack:</strong><br />
                          {errorInfo.componentStack}
                        </Typography>
                      </Alert>
                    )}
                  </Box>
                </Collapse>
              </Box>
            )}

            {/* Contact Support Link */}
            <Box mt={3}>
              <Typography variant="body2" color="text.secondary">
                Need help? {' '}
                <Button
                  variant="text"
                  size="small"
                  onClick={() => window.open('/support', '_blank')}
                >
                  Contact Support
                </Button>
              </Typography>
            </Box>
          </Paper>
        </Box>
      );
    }

    return children;
  }
}

// HOC for functional components
export const withErrorBoundary = <P extends object>(
  Component: React.ComponentType<P>,
  errorBoundaryProps?: Omit<Props, 'children'>
) => {
  const WrappedComponent = (props: P) => (
    <ErrorBoundary {...errorBoundaryProps}>
      <Component {...props} />
    </ErrorBoundary>
  );

  WrappedComponent.displayName = `withErrorBoundary(${Component.displayName || Component.name})`;
  return WrappedComponent;
};

// Hook for error reporting in functional components
export const useErrorHandler = () => {
  const handleError = React.useCallback((error: Error, errorInfo?: any) => {
    // Report error
    console.error('Manual error report:', error, errorInfo);
    
    // You could integrate with your error reporting service here
    // Example: Sentry.captureException(error, { extra: errorInfo });
  }, []);

  return handleError;
};

export default ErrorBoundary;