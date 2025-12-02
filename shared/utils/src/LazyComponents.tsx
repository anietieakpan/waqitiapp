/**
 * Lazy Loading and Code Splitting Utilities
 * Provides comprehensive code splitting and lazy loading for React components
 */

import React, { 
  lazy, 
  Suspense, 
  ComponentType, 
  ReactNode, 
  useState, 
  useEffect,
  useCallback,
  useMemo,
  memo
} from 'react';
import { 
  View, 
  Text, 
  ActivityIndicator, 
  StyleSheet, 
  Dimensions,
  TouchableOpacity 
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Logger } from '../../services/src/LoggingService';

// Types
export interface LazyComponentOptions {
  fallback?: ComponentType;
  errorBoundary?: boolean;
  preload?: boolean;
  retryCount?: number;
  retryDelay?: number;
  cacheStrategy?: 'memory' | 'storage' | 'none';
  timeout?: number;
  onLoadStart?: () => void;
  onLoadEnd?: (success: boolean, error?: Error) => void;
  onRetry?: (attempt: number) => void;
}

export interface LazyRouteOptions extends LazyComponentOptions {
  routeName: string;
  requireAuth?: boolean;
  requireNetwork?: boolean;
  preloadCondition?: () => boolean;
}

interface ChunkLoadState {
  loading: boolean;
  loaded: boolean;
  error: Error | null;
  retryCount: number;
}

interface LoadingProps {
  message?: string;
  showProgress?: boolean;
  progress?: number;
  size?: 'small' | 'large';
  color?: string;
}

interface ErrorProps {
  error: Error;
  retry: () => void;
  canRetry: boolean;
  retryCount: number;
  maxRetries: number;
}

// Default loading component
const DefaultLoadingComponent: React.FC<LoadingProps> = ({ 
  message = 'Loading...',
  showProgress = false,
  progress = 0,
  size = 'large',
  color = '#007AFF'
}) => (
  <View style={styles.loadingContainer}>
    <ActivityIndicator size={size} color={color} />
    <Text style={styles.loadingText}>{message}</Text>
    {showProgress && (
      <View style={styles.progressContainer}>
        <View style={[styles.progressBar, { width: `${progress}%` }]} />
      </View>
    )}
  </View>
);

// Default error component
const DefaultErrorComponent: React.FC<ErrorProps> = ({ 
  error, 
  retry, 
  canRetry, 
  retryCount, 
  maxRetries 
}) => (
  <View style={styles.errorContainer}>
    <Text style={styles.errorIcon}>⚠️</Text>
    <Text style={styles.errorTitle}>Failed to Load</Text>
    <Text style={styles.errorMessage}>{error.message}</Text>
    {canRetry && (
      <TouchableOpacity style={styles.retryButton} onPress={retry}>
        <Text style={styles.retryButtonText}>
          Retry ({retryCount}/{maxRetries})
        </Text>
      </TouchableOpacity>
    )}
    {__DEV__ && (
      <Text style={styles.errorStack}>{error.stack}</Text>
    )}
  </View>
);

// Enhanced lazy component wrapper with error handling and retry logic
export function createLazyComponent<P = {}>(
  importFunc: () => Promise<{ default: ComponentType<P> }>,
  options: LazyComponentOptions = {}
): ComponentType<P> {
  const {
    fallback: FallbackComponent = DefaultLoadingComponent,
    errorBoundary = true,
    preload = false,
    retryCount = 3,
    retryDelay = 1000,
    cacheStrategy = 'memory',
    timeout = 10000,
    onLoadStart,
    onLoadEnd,
    onRetry,
  } = options;

  // Cache for loaded components
  const componentCache = new Map<string, ComponentType<P>>();
  
  // Create a unique key for this component
  const componentKey = importFunc.toString();

  // Enhanced import function with retry logic
  const enhancedImportFunc = async (): Promise<{ default: ComponentType<P> }> => {
    // Check cache first
    if (cacheStrategy === 'memory' && componentCache.has(componentKey)) {
      const cachedComponent = componentCache.get(componentKey)!;
      return { default: cachedComponent };
    }

    // Check storage cache
    if (cacheStrategy === 'storage') {
      try {
        const cached = await AsyncStorage.getItem(`lazy_component_${componentKey}`);
        if (cached) {
          Logger.debug('Loading component from storage cache', { componentKey });
        }
      } catch (error) {
        Logger.warn('Failed to load component from storage cache', error);
      }
    }

    onLoadStart?.();
    const startTime = performance.now();

    let lastError: Error | null = null;
    let attempts = 0;

    while (attempts <= retryCount) {
      try {
        Logger.debug('Loading lazy component', { componentKey, attempt: attempts + 1 });

        // Create timeout promise
        const timeoutPromise = new Promise<never>((_, reject) => {
          setTimeout(() => reject(new Error('Component load timeout')), timeout);
        });

        // Race between import and timeout
        const componentModule = await Promise.race([
          importFunc(),
          timeoutPromise
        ]);

        const loadTime = performance.now() - startTime;
        Logger.info('Lazy component loaded successfully', { 
          componentKey, 
          loadTime: `${loadTime.toFixed(2)}ms`,
          attempts: attempts + 1
        });

        // Cache the component
        if (cacheStrategy === 'memory') {
          componentCache.set(componentKey, componentModule.default);
        }

        onLoadEnd?.(true);
        return componentModule;

      } catch (error) {
        attempts++;
        lastError = error as Error;
        
        Logger.warn('Lazy component load failed', error, { 
          componentKey, 
          attempt: attempts, 
          maxAttempts: retryCount + 1 
        });

        if (attempts <= retryCount) {
          onRetry?.(attempts);
          const delay = retryDelay * Math.pow(2, attempts - 1); // Exponential backoff
          await new Promise(resolve => setTimeout(resolve, delay));
        }
      }
    }

    const totalLoadTime = performance.now() - startTime;
    Logger.error('Lazy component failed to load after all retries', lastError, {
      componentKey,
      totalAttempts: attempts,
      totalLoadTime: `${totalLoadTime.toFixed(2)}ms`
    });

    onLoadEnd?.(false, lastError);
    throw lastError;
  };

  // Create the lazy component
  const LazyComponent = lazy(enhancedImportFunc);

  // Wrapper component with enhanced error handling
  const LazyWrapper: ComponentType<P> = memo((props: P) => {
    const [loadState, setLoadState] = useState<ChunkLoadState>({
      loading: false,
      loaded: false,
      error: null,
      retryCount: 0,
    });

    // Retry function
    const retry = useCallback(() => {
      setLoadState(prev => ({
        ...prev,
        loading: true,
        error: null,
        retryCount: prev.retryCount + 1,
      }));
    }, []);

    // Preload component if needed
    useEffect(() => {
      if (preload) {
        enhancedImportFunc().catch(error => {
          Logger.warn('Preload failed for lazy component', error, { componentKey });
        });
      }
    }, []);

    const fallbackComponent = useMemo(() => {
      if (loadState.error) {
        return (
          <DefaultErrorComponent
            error={loadState.error}
            retry={retry}
            canRetry={loadState.retryCount < retryCount}
            retryCount={loadState.retryCount}
            maxRetries={retryCount}
          />
        );
      }

      return <FallbackComponent />;
    }, [loadState.error, retry, loadState.retryCount]);

    if (errorBoundary) {
      return (
        <LazyErrorBoundary 
          fallback={fallbackComponent}
          onError={(error) => {
            setLoadState(prev => ({ ...prev, error }));
          }}
        >
          <Suspense fallback={fallbackComponent}>
            <LazyComponent {...props} />
          </Suspense>
        </LazyErrorBoundary>
      );
    }

    return (
      <Suspense fallback={fallbackComponent}>
        <LazyComponent {...props} />
      </Suspense>
    );
  });

  LazyWrapper.displayName = `LazyWrapper(${LazyComponent.displayName || 'Component'})`;

  // Attach preload method for manual preloading
  (LazyWrapper as any).preload = () => enhancedImportFunc();

  return LazyWrapper;
}

// Enhanced error boundary for lazy components
class LazyErrorBoundary extends React.Component<
  { 
    children: ReactNode; 
    fallback: ReactNode; 
    onError?: (error: Error) => void;
  },
  { hasError: boolean; error?: Error }
> {
  constructor(props: any) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    Logger.error('LazyErrorBoundary caught error', error, {
      componentStack: errorInfo.componentStack,
    });
    
    this.props.onError?.(error);
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback;
    }

    return this.props.children;
  }
}

// Lazy route component with authentication and network checks
export function createLazyRoute<P = {}>(
  importFunc: () => Promise<{ default: ComponentType<P> }>,
  options: LazyRouteOptions
): ComponentType<P> {
  const {
    routeName,
    requireAuth = false,
    requireNetwork = false,
    preloadCondition,
    ...lazyOptions
  } = options;

  const LazyRouteComponent = createLazyComponent(importFunc, {
    ...lazyOptions,
    onLoadStart: () => {
      Logger.info('Loading route', { routeName });
      lazyOptions.onLoadStart?.();
    },
    onLoadEnd: (success, error) => {
      if (success) {
        Logger.info('Route loaded successfully', { routeName });
      } else {
        Logger.error('Route failed to load', error, { routeName });
      }
      lazyOptions.onLoadEnd?.(success, error);
    },
  });

  // Route wrapper with auth and network checks
  const RouteWrapper: ComponentType<P> = memo((props: P) => {
    const [canRender, setCanRender] = useState(!requireAuth && !requireNetwork);

    useEffect(() => {
      const checkRequirements = async () => {
        let authCheck = true;
        let networkCheck = true;

        // Check authentication if required
        if (requireAuth) {
          try {
            const token = await AsyncStorage.getItem('authToken');
            authCheck = !!token;
          } catch {
            authCheck = false;
          }
        }

        // Check network if required
        if (requireNetwork) {
          // Implement network check here
          networkCheck = true; // Simplified for now
        }

        setCanRender(authCheck && networkCheck);

        if (!authCheck) {
          Logger.warn('Route access denied - authentication required', { routeName });
        }
        if (!networkCheck) {
          Logger.warn('Route access denied - network required', { routeName });
        }
      };

      checkRequirements();
    }, []);

    // Conditional preloading
    useEffect(() => {
      if (preloadCondition && preloadCondition()) {
        (LazyRouteComponent as any).preload?.();
      }
    }, []);

    if (!canRender) {
      return (
        <View style={styles.accessDeniedContainer}>
          <Text style={styles.accessDeniedText}>
            {!requireAuth ? 'Authentication required' : 'Network connection required'}
          </Text>
        </View>
      );
    }

    return <LazyRouteComponent {...props} />;
  });

  RouteWrapper.displayName = `LazyRoute(${routeName})`;

  return RouteWrapper;
}

// Bundle analyzer utility
export class BundleAnalyzer {
  private static chunkLoadTimes = new Map<string, number>();
  private static chunkSizes = new Map<string, number>();

  static recordChunkLoad(chunkName: string, loadTime: number, size?: number) {
    this.chunkLoadTimes.set(chunkName, loadTime);
    if (size) {
      this.chunkSizes.set(chunkName, size);
    }

    Logger.info('Chunk loaded', {
      chunkName,
      loadTime: `${loadTime.toFixed(2)}ms`,
      size: size ? `${(size / 1024).toFixed(2)}KB` : undefined,
    });
  }

  static getAnalytics() {
    const analytics = {
      totalChunks: this.chunkLoadTimes.size,
      averageLoadTime: 0,
      slowestChunk: { name: '', time: 0 },
      largestChunk: { name: '', size: 0 },
      totalSize: 0,
    };

    // Calculate average load time
    const totalTime = Array.from(this.chunkLoadTimes.values()).reduce((sum, time) => sum + time, 0);
    analytics.averageLoadTime = totalTime / this.chunkLoadTimes.size;

    // Find slowest chunk
    for (const [name, time] of this.chunkLoadTimes) {
      if (time > analytics.slowestChunk.time) {
        analytics.slowestChunk = { name, time };
      }
    }

    // Find largest chunk and calculate total size
    for (const [name, size] of this.chunkSizes) {
      analytics.totalSize += size;
      if (size > analytics.largestChunk.size) {
        analytics.largestChunk = { name, size };
      }
    }

    return analytics;
  }

  static logAnalytics() {
    const analytics = this.getAnalytics();
    Logger.info('Bundle Analytics', {
      ...analytics,
      averageLoadTime: `${analytics.averageLoadTime.toFixed(2)}ms`,
      slowestChunkTime: `${analytics.slowestChunk.time.toFixed(2)}ms`,
      largestChunkSize: `${(analytics.largestChunk.size / 1024).toFixed(2)}KB`,
      totalSize: `${(analytics.totalSize / 1024).toFixed(2)}KB`,
    });
  }
}

// Preloader utility for critical routes
export class RoutePreloader {
  private static preloadedRoutes = new Set<string>();
  private static preloadPromises = new Map<string, Promise<any>>();

  static preloadRoute(
    routeName: string,
    importFunc: () => Promise<{ default: ComponentType<any> }>
  ): Promise<void> {
    if (this.preloadedRoutes.has(routeName)) {
      return Promise.resolve();
    }

    if (this.preloadPromises.has(routeName)) {
      return this.preloadPromises.get(routeName)!;
    }

    const preloadPromise = importFunc()
      .then(() => {
        this.preloadedRoutes.add(routeName);
        Logger.info('Route preloaded successfully', { routeName });
      })
      .catch((error) => {
        Logger.warn('Route preload failed', error, { routeName });
        throw error;
      })
      .finally(() => {
        this.preloadPromises.delete(routeName);
      });

    this.preloadPromises.set(routeName, preloadPromise);
    return preloadPromise;
  }

  static preloadCriticalRoutes() {
    // Define critical routes that should be preloaded
    const criticalRoutes = [
      {
        name: 'Dashboard',
        import: () => import('../../screens/DashboardScreen'),
      },
      {
        name: 'SendMoney',
        import: () => import('../../screens/SendMoneyScreen'),
      },
      {
        name: 'TransactionHistory',
        import: () => import('../../screens/TransactionHistoryScreen'),
      },
    ];

    return Promise.allSettled(
      criticalRoutes.map(route => 
        this.preloadRoute(route.name, route.import as any)
      )
    );
  }
}

// Hook for monitoring chunk loading performance
export const useChunkLoadingMetrics = (chunkName: string) => {
  const [metrics, setMetrics] = useState({
    isLoading: false,
    loadTime: 0,
    error: null as Error | null,
  });

  const recordLoadStart = useCallback(() => {
    setMetrics(prev => ({ ...prev, isLoading: true }));
  }, []);

  const recordLoadEnd = useCallback((success: boolean, error?: Error) => {
    const loadTime = performance.now();
    BundleAnalyzer.recordChunkLoad(chunkName, loadTime);
    
    setMetrics({
      isLoading: false,
      loadTime,
      error: error || null,
    });
  }, [chunkName]);

  return { metrics, recordLoadStart, recordLoadEnd };
};

// Styles
const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F8F9FA',
    padding: 20,
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#6C757D',
    textAlign: 'center',
  },
  progressContainer: {
    width: 200,
    height: 4,
    backgroundColor: '#E9ECEF',
    borderRadius: 2,
    marginTop: 16,
    overflow: 'hidden',
  },
  progressBar: {
    height: '100%',
    backgroundColor: '#007AFF',
    borderRadius: 2,
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F8F9FA',
    padding: 20,
  },
  errorIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#DC3545',
    marginBottom: 8,
    textAlign: 'center',
  },
  errorMessage: {
    fontSize: 14,
    color: '#6C757D',
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 20,
  },
  retryButton: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    marginBottom: 16,
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  errorStack: {
    fontSize: 10,
    color: '#ADB5BD',
    textAlign: 'left',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    maxHeight: 100,
    overflow: 'hidden',
  },
  accessDeniedContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F8F9FA',
    padding: 20,
  },
  accessDeniedText: {
    fontSize: 16,
    color: '#DC3545',
    textAlign: 'center',
  },
});

export default {
  createLazyComponent,
  createLazyRoute,
  BundleAnalyzer,
  RoutePreloader,
  useChunkLoadingMetrics,
};