/**
 * Error Boundary Component
 * Catches and displays JavaScript errors anywhere in the child component tree
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { View, StyleSheet } from 'react-native';
import { Button, Text, Card } from 'react-native-paper';
import { logError } from '../../services/crashlyticsService';
import { trackError } from '../../services/analyticsService';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

class ErrorBoundary extends Component<Props, State> {
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
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    
    this.setState({
      error,
      errorInfo,
    });

    // Log error to crashlytics
    logError(error, {
      componentStack: errorInfo.componentStack,
      screen: 'ErrorBoundary',
      action: 'component_error',
    });

    // Track error in analytics
    trackError('react_error_boundary', error.message, 'ErrorBoundary');
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <View style={styles.container}>
          <Card style={styles.card}>
            <Card.Content>
              <Text variant="headlineSmall" style={styles.title}>
                Oops! Something went wrong
              </Text>
              <Text variant="bodyMedium" style={styles.description}>
                We're sorry for the inconvenience. The app has encountered an unexpected error.
              </Text>
              
              {__DEV__ && this.state.error && (
                <View style={styles.errorDetails}>
                  <Text variant="labelLarge" style={styles.errorTitle}>
                    Error Details:
                  </Text>
                  <Text variant="bodySmall" style={styles.errorText}>
                    {this.state.error.toString()}
                  </Text>
                  {this.state.errorInfo && (
                    <Text variant="bodySmall" style={styles.errorText}>
                      {this.state.errorInfo.componentStack}
                    </Text>
                  )}
                </View>
              )}
            </Card.Content>
            
            <Card.Actions>
              <Button mode="contained" onPress={this.handleReset}>
                Try Again
              </Button>
            </Card.Actions>
          </Card>
        </View>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  card: {
    width: '100%',
    maxWidth: 400,
  },
  title: {
    textAlign: 'center',
    marginBottom: 16,
  },
  description: {
    textAlign: 'center',
    marginBottom: 16,
  },
  errorDetails: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
  },
  errorTitle: {
    marginBottom: 8,
    fontWeight: 'bold',
  },
  errorText: {
    fontFamily: 'monospace',
    fontSize: 12,
  },
});

export default ErrorBoundary;