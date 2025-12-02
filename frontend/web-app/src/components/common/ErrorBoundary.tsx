import React, { Component, ErrorInfo, ReactNode } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Container,
  Alert,
  AlertTitle,
  Stack,
} from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import HomeIcon from '@mui/icons-material/Home';
import BugReportIcon from '@mui/icons-material/BugReport';;

interface Props {
  children: ReactNode;
  fallbackComponent?: React.ComponentType<ErrorFallbackProps>;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  isolate?: boolean;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

export interface ErrorFallbackProps {
  error: Error | null;
  errorInfo: ErrorInfo | null;
  resetError: () => void;
}

class ErrorBoundary extends Component<Props, State> {
  private resetTimeoutId: number | null = null;

  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
      errorInfo: null,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({
      error,
      errorInfo,
    });

    // Log error to monitoring service
    this.logErrorToService(error, errorInfo);

    // Call custom error handler if provided
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
  }

  componentWillUnmount() {
    if (this.resetTimeoutId) {
      window.clearTimeout(this.resetTimeoutId);
    }
  }

  private logErrorToService = (error: Error, errorInfo: ErrorInfo) => {
    // In production, integrate with error monitoring service like Sentry
    if (process.env.NODE_ENV === 'production') {
      try {
        // Example: Sentry.captureException(error, { contexts: { errorInfo } });
        console.error('Error logged to monitoring service:', {
          error: error.message,
          stack: error.stack,
          componentStack: errorInfo.componentStack,
          timestamp: new Date().toISOString(),
        });
      } catch (loggingError) {
        console.error('Failed to log error:', loggingError);
      }
    } else {
      console.error('ErrorBoundary caught an error:', error);
      console.error('Error info:', errorInfo);
    }
  };

  private resetError = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  private handleRetry = () => {
    this.resetError();
    // Force re-render after a short delay to ensure clean state
    this.resetTimeoutId = window.setTimeout(() => {
      window.location.reload();
    }, 100);
  };

  private handleGoHome = () => {
    this.resetError();
    window.location.href = '/dashboard';
  };

  private handleReportBug = () => {
    const { error, errorInfo } = this.state;
    const errorDetails = {
      message: error?.message,
      stack: error?.stack,
      componentStack: errorInfo?.componentStack,
      userAgent: navigator.userAgent,
      timestamp: new Date().toISOString(),
      url: window.location.href,
    };

    // Create bug report email or redirect to support
    const subject = encodeURIComponent('Bug Report - Application Error');
    const body = encodeURIComponent(
      `An error occurred in the application:\n\n` +
      `Error: ${errorDetails.message}\n` +
      `URL: ${errorDetails.url}\n` +
      `Timestamp: ${errorDetails.timestamp}\n\n` +
      `Technical Details:\n${errorDetails.stack}\n\n` +
      `Component Stack:\n${errorDetails.componentStack}`
    );
    
    window.open(`mailto:support@example.com?subject=${subject}&body=${body}`);
  };

  render() {
    if (this.state.hasError) {
      const { fallbackComponent: FallbackComponent } = this.props;
      
      if (FallbackComponent) {
        return (
          <FallbackComponent
            error={this.state.error}
            errorInfo={this.state.errorInfo}
            resetError={this.resetError}
          />
        );
      }

      return (
        <Container maxWidth="md" sx={{ py: 4 }}>
          <Paper elevation={3} sx={{ p: 4, textAlign: 'center' }}>
            <ErrorOutline sx={{ fontSize: 64, color: 'error.main', mb: 2 }} />
            
            <Typography variant="h4" gutterBottom color="error">
              Oops! Something went wrong
            </Typography>
            
            <Typography variant="body1" color="text.secondary" paragraph>
              We're sorry, but an unexpected error occurred. Our team has been notified
              and is working to fix the issue.
            </Typography>

            {process.env.NODE_ENV === 'development' && this.state.error && (
              <Alert severity="error" sx={{ mt: 3, textAlign: 'left' }}>
                <AlertTitle>Error Details (Development Mode)</AlertTitle>
                <Typography variant="body2" component="pre" sx={{ 
                  whiteSpace: 'pre-wrap',
                  fontSize: '0.8rem',
                  maxHeight: 200,
                  overflow: 'auto',
                  mt: 1,
                }}>
                  {this.state.error.message}
                  {'\n\n'}
                  {this.state.error.stack}
                </Typography>
              </Alert>
            )}

            <Stack 
              direction={{ xs: 'column', sm: 'row' }} 
              spacing={2} 
              justifyContent="center"
              sx={{ mt: 4 }}
            >
              <Button
                variant="contained"
                startIcon={<Refresh />}
                onClick={this.handleRetry}
                size="large"
              >
                Try Again
              </Button>
              
              <Button
                variant="outlined"
                startIcon={<Home />}
                onClick={this.handleGoHome}
                size="large"
              >
                Go to Dashboard
              </Button>
              
              <Button
                variant="text"
                startIcon={<BugReport />}
                onClick={this.handleReportBug}
                size="large"
              >
                Report Bug
              </Button>
            </Stack>

            <Typography variant="caption" color="text.disabled" sx={{ mt: 4, display: 'block' }}>
              Error ID: {this.state.error?.message?.slice(0, 8) || 'unknown'}
              {' • '}
              {new Date().toLocaleString()}
            </Typography>
          </Paper>
        </Container>
      );
    }

    return this.props.children;
  }
}

// Higher-order component for easy wrapping
export const withErrorBoundary = <P extends object>(
  WrappedComponent: React.ComponentType<P>,
  errorBoundaryProps?: Omit<Props, 'children'>
) => {
  const WithErrorBoundaryComponent = (props: P) => (
    <ErrorBoundary {...errorBoundaryProps}>
      <WrappedComponent {...props} />
    </ErrorBoundary>
  );

  WithErrorBoundaryComponent.displayName = 
    `withErrorBoundary(${WrappedComponent.displayName || WrappedComponent.name})`;

  return WithErrorBoundaryComponent;
};

// Specialized error boundary for async operations
export const AsyncErrorBoundary: React.FC<{
  children: ReactNode;
  fallback?: ReactNode;
}> = ({ children, fallback }) => (
  <ErrorBoundary
    fallbackComponent={fallback ? 
      () => <>{fallback}</> : 
      ({ resetError }) => (
        <Alert 
          severity="error" 
          action={
            <Button color="inherit" size="small" onClick={resetError}>
              RETRY
            </Button>
          }
        >
          Failed to load content. Please try again.
        </Alert>
      )
    }
  >
    {children}
  </ErrorBoundary>
);

export default ErrorBoundary;