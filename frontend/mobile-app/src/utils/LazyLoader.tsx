/**
 * LazyLoader - Advanced component lazy loading with optimization and error handling
 * Provides intelligent code splitting, preloading, and bundle optimization
 */

import React, { 
  ComponentType, 
  lazy, 
  Suspense, 
  useEffect, 
  useState, 
  useCallback,
  createContext,
  useContext,
  useMemo,
} from 'react';
import { View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import PerformanceMonitor from './PerformanceMonitor';

// Lazy loading configuration
interface LazyLoadConfig {
  preload?: boolean;
  priority?: 'low' | 'medium' | 'high';
  timeout?: number;
  retryCount?: number;
  fallback?: ComponentType<any>;
  chunkName?: string;
  cacheStrategy?: 'memory' | 'storage' | 'none';
}

// Bundle chunk information
interface ChunkInfo {
  name: string;
  size: number;
  loaded: boolean;
  loadTime: number;
  error?: string;
  dependencies: string[];
}

// Lazy loading context
interface LazyLoadContext {
  loadedChunks: Map<string, ChunkInfo>;
  preloadQueue: string[];
  isPreloading: boolean;
  bundleStats: {
    totalSize: number;
    loadedSize: number;
    chunksLoaded: number;
    chunksTotal: number;
  };
}

const LazyLoadContext = createContext<LazyLoadContext | null>(null);

// Default loading component
const DefaultLoadingComponent: React.FC<{ message?: string }> = ({ 
  message = 'Loading...' 
}) => (
  <View style={styles.loadingContainer}>
    <ActivityIndicator size="large" color="#007AFF" />
    <Text style={styles.loadingText}>{message}</Text>
  </View>
);

// Default error component
const DefaultErrorComponent: React.FC<{ 
  error: Error;
  retry: () => void;
  componentName?: string;
}> = ({ error, retry, componentName }) => (
  <View style={styles.errorContainer}>
    <Text style={styles.errorTitle}>Failed to load {componentName || 'component'}</Text>
    <Text style={styles.errorMessage}>{error.message}</Text>
    <Text style={styles.retryButton} onPress={retry}>
      Tap to retry
    </Text>
  </View>
);

class LazyLoaderManager {
  private static instance: LazyLoaderManager;
  private loadedChunks: Map<string, ChunkInfo> = new Map();
  private preloadQueue: string[] = [];
  private isPreloading: boolean = false;
  private componentCache: Map<string, ComponentType<any>> = new Map();
  private loadingPromises: Map<string, Promise<any>> = new Map();
  private networkObserver?: (() => void);

  private constructor() {
    this.setupNetworkOptimization();
    this.loadBundleStats();
  }

  static getInstance(): LazyLoaderManager {
    if (!LazyLoaderManager.instance) {
      LazyLoaderManager.instance = new LazyLoaderManager();
    }
    return LazyLoaderManager.instance;
  }

  /**
   * Setup network-aware optimization
   */
  private setupNetworkOptimization(): void {
    // Monitor network conditions for intelligent preloading
    import('@react-native-community/netinfo').then(NetInfo => {
      this.networkObserver = NetInfo.default.addEventListener(state => {
        if (state.isConnected && state.type === 'wifi') {
          // Good network - preload high priority components
          this.startPreloading('high');
        } else if (state.isConnected && state.type === 'cellular') {
          // Cellular network - be more conservative
          this.startPreloading('medium');
        } else {
          // No network - stop preloading
          this.stopPreloading();
        }
      });
    }).catch(() => {
      // NetInfo not available - continue without network optimization
    });
  }

  /**
   * Load bundle statistics
   */
  private async loadBundleStats(): Promise<void> {
    try {
      const stats = await AsyncStorage.getItem('@bundle_stats');
      if (stats) {
        const parsedStats = JSON.parse(stats);
        // Initialize loaded chunks from previous session
        Object.entries(parsedStats.chunks || {}).forEach(([name, info]: [string, any]) => {
          this.loadedChunks.set(name, {
            ...info,
            loaded: false, // Reset loaded state
          });
        });
      }
    } catch (error) {
      console.warn('Failed to load bundle stats:', error);
    }
  }

  /**
   * Save bundle statistics
   */
  private async saveBundleStats(): Promise<void> {
    try {
      const chunks: Record<string, ChunkInfo> = {};
      this.loadedChunks.forEach((info, name) => {
        chunks[name] = info;
      });
      
      await AsyncStorage.setItem('@bundle_stats', JSON.stringify({
        chunks,
        lastUpdated: Date.now(),
        version: '1.0',
      }));
    } catch (error) {
      console.warn('Failed to save bundle stats:', error);
    }
  }

  /**
   * Create lazy component with advanced options
   */
  createLazyComponent<T extends ComponentType<any>>(
    importFunction: () => Promise<{ default: T }>,
    config: LazyLoadConfig = {}
  ): ComponentType<React.ComponentProps<T>> {
    const {
      priority = 'medium',
      timeout = 10000,
      retryCount = 3,
      fallback,
      chunkName = `chunk_${Math.random().toString(36).substr(2, 9)}`,
      cacheStrategy = 'memory',
    } = config;

    // Check if component is already cached
    if (cacheStrategy === 'memory' && this.componentCache.has(chunkName)) {
      return this.componentCache.get(chunkName)!;
    }

    const LazyComponent = lazy(() => {
      const startTime = performance.now();
      
      // Track loading performance
      const loadingPromise = this.loadWithRetry(
        importFunction,
        retryCount,
        timeout,
        chunkName
      );

      return loadingPromise.then((module) => {
        const loadTime = performance.now() - startTime;
        
        // Update chunk info
        const chunkInfo: ChunkInfo = {
          name: chunkName,
          size: this.estimateChunkSize(module),
          loaded: true,
          loadTime,
          dependencies: this.extractDependencies(module),
        };
        
        this.loadedChunks.set(chunkName, chunkInfo);
        
        // Record performance metric
        PerformanceMonitor.recordMetric({
          name: `chunk_loaded_${chunkName}`,
          value: loadTime,
          timestamp: Date.now(),
          category: 'bundle',
          metadata: {
            chunkName,
            size: chunkInfo.size,
            priority,
          },
        });

        // Cache if enabled
        if (cacheStrategy === 'memory') {
          this.componentCache.set(chunkName, module.default);
        }

        this.saveBundleStats();
        
        return module;
      }).catch((error) => {
        // Update chunk info with error
        this.loadedChunks.set(chunkName, {
          name: chunkName,
          size: 0,
          loaded: false,
          loadTime: performance.now() - startTime,
          error: error.message,
          dependencies: [],
        });

        throw error;
      });
    });

    // Create wrapped component with error boundary
    const WrappedComponent: ComponentType<React.ComponentProps<T>> = (props) => {
      const [error, setError] = useState<Error | null>(null);
      const [retryKey, setRetryKey] = useState(0);

      const handleRetry = useCallback(() => {
        setError(null);
        setRetryKey(prev => prev + 1);
      }, []);

      if (error) {
        const ErrorComponent = fallback || DefaultErrorComponent;
        return (
          <ErrorComponent
            error={error}
            retry={handleRetry}
            componentName={chunkName}
          />
        );
      }

      return (
        <ErrorBoundary
          onError={setError}
          fallback={fallback}
          componentName={chunkName}
        >
          <Suspense
            fallback={
              <DefaultLoadingComponent
                message={`Loading ${chunkName}...`}
              />
            }
          >
            <LazyComponent key={retryKey} {...props} />
          </Suspense>
        </ErrorBoundary>
      );
    };

    // Add preload method
    (WrappedComponent as any).preload = () => this.preloadComponent(chunkName, importFunction);
    
    // Add to preload queue if configured
    if (config.preload) {
      this.addToPreloadQueue(chunkName, priority);
    }

    return WrappedComponent;
  }

  /**
   * Load with retry logic
   */
  private async loadWithRetry<T>(
    importFunction: () => Promise<T>,
    maxRetries: number,
    timeout: number,
    chunkName: string
  ): Promise<T> {
    // Check if already loading
    if (this.loadingPromises.has(chunkName)) {
      return this.loadingPromises.get(chunkName)!;
    }

    const loadPromise = this.attemptLoad(importFunction, maxRetries, timeout);
    this.loadingPromises.set(chunkName, loadPromise);

    try {
      const result = await loadPromise;
      this.loadingPromises.delete(chunkName);
      return result;
    } catch (error) {
      this.loadingPromises.delete(chunkName);
      throw error;
    }
  }

  /**
   * Attempt to load with timeout and retries
   */
  private async attemptLoad<T>(
    importFunction: () => Promise<T>,
    maxRetries: number,
    timeout: number
  ): Promise<T> {
    let lastError: Error = new Error('Unknown error');

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await Promise.race([
          importFunction(),
          new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error('Loading timeout')), timeout)
          ),
        ]);
      } catch (error: any) {
        lastError = error;
        
        if (attempt < maxRetries) {
          // Exponential backoff
          const delay = Math.min(1000 * Math.pow(2, attempt), 10000);
          await new Promise(resolve => setTimeout(resolve, delay));
        }
      }
    }

    throw lastError;
  }

  /**
   * Estimate chunk size (rough approximation)
   */
  private estimateChunkSize(module: any): number {
    try {
      const moduleString = JSON.stringify(module);
      return moduleString.length * 2; // Rough byte estimate
    } catch {
      return 0; // Fallback for circular references
    }
  }

  /**
   * Extract dependencies from module (simplified)
   */
  private extractDependencies(module: any): string[] {
    // This is a simplified implementation
    // In a real scenario, you'd parse the module dependencies
    return [];
  }

  /**
   * Add component to preload queue
   */
  private addToPreloadQueue(chunkName: string, priority: string): void {
    if (!this.preloadQueue.includes(chunkName)) {
      if (priority === 'high') {
        this.preloadQueue.unshift(chunkName);
      } else {
        this.preloadQueue.push(chunkName);
      }
    }
  }

  /**
   * Preload a specific component
   */
  async preloadComponent(
    chunkName: string,
    importFunction: () => Promise<any>
  ): Promise<void> {
    if (this.loadedChunks.get(chunkName)?.loaded) {
      return; // Already loaded
    }

    try {
      await importFunction();
    } catch (error) {
      console.warn(`Failed to preload ${chunkName}:`, error);
    }
  }

  /**
   * Start preloading components
   */
  private async startPreloading(minPriority: string): Promise<void> {
    if (this.isPreloading) return;
    
    this.isPreloading = true;
    
    const priorityOrder = { high: 3, medium: 2, low: 1 };
    const minPriorityValue = priorityOrder[minPriority as keyof typeof priorityOrder] || 2;

    while (this.preloadQueue.length > 0 && this.isPreloading) {
      const chunkName = this.preloadQueue.shift();
      if (!chunkName) break;

      const chunkInfo = this.loadedChunks.get(chunkName);
      if (chunkInfo?.loaded) continue;

      try {
        // This is a simplified version - in practice, you'd need to map
        // chunk names back to their import functions
        console.log(`Preloading ${chunkName}...`);
        
        // Simulate preloading delay
        await new Promise(resolve => setTimeout(resolve, 100));
      } catch (error) {
        console.warn(`Failed to preload ${chunkName}:`, error);
      }
    }

    this.isPreloading = false;
  }

  /**
   * Stop preloading
   */
  private stopPreloading(): void {
    this.isPreloading = false;
  }

  /**
   * Get bundle statistics
   */
  getBundleStats() {
    const chunks = Array.from(this.loadedChunks.values());
    const loadedChunks = chunks.filter(c => c.loaded);
    
    return {
      totalSize: chunks.reduce((sum, chunk) => sum + chunk.size, 0),
      loadedSize: loadedChunks.reduce((sum, chunk) => sum + chunk.size, 0),
      chunksLoaded: loadedChunks.length,
      chunksTotal: chunks.length,
      avgLoadTime: loadedChunks.length > 0 
        ? loadedChunks.reduce((sum, chunk) => sum + chunk.loadTime, 0) / loadedChunks.length
        : 0,
      errors: chunks.filter(c => c.error).map(c => ({ name: c.name, error: c.error })),
    };
  }

  /**
   * Cleanup resources
   */
  cleanup(): void {
    if (this.networkObserver) {
      this.networkObserver();
      this.networkObserver = undefined;
    }
    
    this.componentCache.clear();
    this.loadingPromises.clear();
    this.stopPreloading();
  }
}

// Error boundary for lazy components
class ErrorBoundary extends React.Component<{
  children: React.ReactNode;
  onError?: (error: Error) => void;
  fallback?: ComponentType<any>;
  componentName?: string;
}, { hasError: boolean; error?: Error }> {
  constructor(props: any) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: any) {
    console.error('Lazy component error:', error, errorInfo);
    
    // Record error metric
    PerformanceMonitor.recordMetric({
      name: 'lazy_component_error',
      value: 1,
      timestamp: Date.now(),
      category: 'bundle',
      metadata: {
        componentName: this.props.componentName,
        error: error.message,
        stack: error.stack,
      },
    });

    if (this.props.onError) {
      this.props.onError(error);
    }
  }

  render() {
    if (this.state.hasError) {
      const FallbackComponent = this.props.fallback || DefaultErrorComponent;
      return (
        <FallbackComponent
          error={this.state.error!}
          retry={() => this.setState({ hasError: false, error: undefined })}
          componentName={this.props.componentName}
        />
      );
    }

    return this.props.children;
  }
}

// Provider for lazy loading context
export const LazyLoadProvider: React.FC<{ children: React.ReactNode }> = ({ 
  children 
}) => {
  const manager = LazyLoaderManager.getInstance();
  
  const contextValue = useMemo<LazyLoadContext>(() => ({
    loadedChunks: manager['loadedChunks'],
    preloadQueue: manager['preloadQueue'],
    isPreloading: manager['isPreloading'],
    bundleStats: manager.getBundleStats(),
  }), [manager]);

  return (
    <LazyLoadContext.Provider value={contextValue}>
      {children}
    </LazyLoadContext.Provider>
  );
};

// Hook to use lazy loading context
export const useLazyLoad = () => {
  const context = useContext(LazyLoadContext);
  if (!context) {
    throw new Error('useLazyLoad must be used within LazyLoadProvider');
  }
  return context;
};

// Hook for component preloading
export const usePreload = () => {
  const manager = LazyLoaderManager.getInstance();
  
  const preloadComponents = useCallback(async (
    components: Array<{
      chunkName: string;
      importFunction: () => Promise<any>;
      priority?: 'low' | 'medium' | 'high';
    }>
  ) => {
    const preloadPromises = components.map(({ chunkName, importFunction }) =>
      manager.preloadComponent(chunkName, importFunction)
    );
    
    await Promise.allSettled(preloadPromises);
  }, [manager]);

  return { preloadComponents };
};

// Main lazy loader utility
export const LazyLoader = {
  /**
   * Create a lazy component
   */
  create<T extends ComponentType<any>>(
    importFunction: () => Promise<{ default: T }>,
    config?: LazyLoadConfig
  ) {
    return LazyLoaderManager.getInstance().createLazyComponent(importFunction, config);
  },

  /**
   * Preload components for route
   */
  async preloadRoute(routeName: string, components: string[]) {
    console.log(`Preloading components for route: ${routeName}`, components);
    // Implementation would depend on your routing structure
  },

  /**
   * Get bundle statistics
   */
  getStats() {
    return LazyLoaderManager.getInstance().getBundleStats();
  },

  /**
   * Cleanup resources
   */
  cleanup() {
    LazyLoaderManager.getInstance().cleanup();
  },
};

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  loadingText: {
    marginTop: 10,
    fontSize: 16,
    color: '#666',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f8f8f8',
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#d32f2f',
    marginBottom: 10,
    textAlign: 'center',
  },
  errorMessage: {
    fontSize: 14,
    color: '#666',
    marginBottom: 20,
    textAlign: 'center',
  },
  retryButton: {
    fontSize: 16,
    color: '#007AFF',
    padding: 10,
    textDecorationLine: 'underline',
  },
});

export default LazyLoader;