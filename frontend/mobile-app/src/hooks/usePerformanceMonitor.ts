/**
 * usePerformanceMonitor Hook - React hook for component performance tracking
 */

import { useEffect, useRef, useCallback, useMemo } from 'react';
import { DeviceEventEmitter } from 'react-native';
import PerformanceMonitor, { PerformanceMetric } from '../utils/PerformanceMonitor';

interface UsePerformanceMonitorOptions {
  componentName: string;
  trackRenders?: boolean;
  trackInteractions?: boolean;
  trackMemory?: boolean;
  onMetric?: (metric: PerformanceMetric) => void;
}

interface PerformanceHookResult {
  measureInteraction: (name: string) => () => void;
  measureFunction: <T>(name: string, fn: () => T) => T;
  measureAsync: <T>(name: string, fn: () => Promise<T>) => Promise<T>;
  recordCustomMetric: (name: string, value: number, metadata?: Record<string, any>) => void;
  getCurrentStats: () => any;
}

export function usePerformanceMonitor(
  options: UsePerformanceMonitorOptions
): PerformanceHookResult {
  const {
    componentName,
    trackRenders = true,
    trackInteractions = false,
    trackMemory = false,
    onMetric,
  } = options;

  const renderCount = useRef(0);
  const mountTime = useRef(performance.now());
  const lastRenderTime = useRef(performance.now());
  const propsRef = useRef<any>(null);

  // Track component renders
  useEffect(() => {
    if (!trackRenders) return;

    const renderTime = performance.now() - lastRenderTime.current;
    renderCount.current++;

    // Skip first render (mount)
    if (renderCount.current > 1) {
      PerformanceMonitor.recordRenderMetric(componentName, renderTime, {
        renderCount: renderCount.current,
        timeSinceMount: performance.now() - mountTime.current,
      });
    }

    lastRenderTime.current = performance.now();
  });

  // Track mount and unmount
  useEffect(() => {
    mountTime.current = performance.now();

    return () => {
      const totalMountTime = performance.now() - mountTime.current;
      PerformanceMonitor.recordRenderMetric(componentName, totalMountTime, {
        event: 'unmount',
        totalMountTime,
        totalRenders: renderCount.current,
      });
    };
  }, [componentName]);

  // Listen for performance metrics
  useEffect(() => {
    if (!onMetric) return;

    const listener = (metric: PerformanceMetric) => {
      if (metric.name.includes(componentName) || metric.metadata?.component === componentName) {
        onMetric(metric);
      }
    };

    DeviceEventEmitter.addListener('performanceMetric', listener);
    
    return () => {
      DeviceEventEmitter.removeListener('performanceMetric', listener);
    };
  }, [onMetric, componentName]);

  // Memory tracking
  useEffect(() => {
    if (!trackMemory) return;

    const interval = setInterval(() => {
      PerformanceMonitor.recordMetric({
        name: `memory_${componentName}`,
        value: PerformanceMonitor.getCurrentStats().metricsCount,
        timestamp: Date.now(),
        category: 'memory',
        metadata: { component: componentName },
      });
    }, 10000); // Every 10 seconds

    return () => clearInterval(interval);
  }, [trackMemory, componentName]);

  const measureInteraction = useCallback((name: string) => {
    return PerformanceMonitor.startInteraction(`${componentName}_${name}`);
  }, [componentName]);

  const measureFunction = useCallback(<T>(name: string, fn: () => T): T => {
    return PerformanceMonitor.measureFunction(`${componentName}_${name}`, fn);
  }, [componentName]);

  const measureAsync = useCallback(async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
    return PerformanceMonitor.measureAsync(`${componentName}_${name}`, fn);
  }, [componentName]);

  const recordCustomMetric = useCallback((
    name: string,
    value: number,
    metadata?: Record<string, any>
  ) => {
    PerformanceMonitor.recordMetric({
      name: `${componentName}_${name}`,
      value,
      timestamp: Date.now(),
      category: 'custom',
      metadata: { component: componentName, ...metadata },
    });
  }, [componentName]);

  const getCurrentStats = useCallback(() => {
    return PerformanceMonitor.getCurrentStats();
  }, []);

  const result = useMemo(() => ({
    measureInteraction,
    measureFunction,
    measureAsync,
    recordCustomMetric,
    getCurrentStats,
  }), [
    measureInteraction,
    measureFunction,
    measureAsync,
    recordCustomMetric,
    getCurrentStats,
  ]);

  return result;
}

/**
 * Higher-Order Component for automatic performance tracking
 */
export function withPerformanceMonitor<P extends object>(
  WrappedComponent: React.ComponentType<P>,
  componentName?: string
) {
  const displayName = componentName || WrappedComponent.displayName || WrappedComponent.name || 'Component';

  const PerformanceTrackedComponent: React.FC<P> = (props) => {
    const performance = usePerformanceMonitor({
      componentName: displayName,
      trackRenders: true,
      trackInteractions: true,
    });

    // Add performance methods to props if component expects them
    const enhancedProps = {
      ...props,
      ...(typeof props === 'object' && props && 'performanceMonitor' in props ? { performanceMonitor: performance } : {}),
    } as P;

    return <WrappedComponent {...enhancedProps} />;
  };

  PerformanceTrackedComponent.displayName = `withPerformanceMonitor(${displayName})`;
  
  return PerformanceTrackedComponent;
}

/**
 * Hook for tracking specific user interactions
 */
export function useInteractionTracker() {
  const measureTap = useCallback((elementName: string) => {
    const endMeasurement = PerformanceMonitor.startInteraction(`tap_${elementName}`);
    
    return () => {
      endMeasurement();
      PerformanceMonitor.recordMetric({
        name: 'user_interaction',
        value: 1,
        timestamp: Date.now(),
        category: 'interaction',
        metadata: { type: 'tap', element: elementName },
      });
    };
  }, []);

  const measureScroll = useCallback((scrollName: string, startY: number) => {
    const startTime = performance.now();
    
    return (endY: number) => {
      const duration = performance.now() - startTime;
      const distance = Math.abs(endY - startY);
      
      PerformanceMonitor.recordMetric({
        name: `scroll_${scrollName}`,
        value: duration,
        timestamp: Date.now(),
        category: 'interaction',
        metadata: { 
          type: 'scroll', 
          distance, 
          speed: distance / duration,
          element: scrollName,
        },
      });
    };
  }, []);

  const measureNavigation = useCallback((fromScreen: string, toScreen: string) => {
    const startTime = performance.now();
    
    return () => {
      const duration = performance.now() - startTime;
      
      PerformanceMonitor.recordMetric({
        name: 'navigation_transition',
        value: duration,
        timestamp: Date.now(),
        category: 'interaction',
        metadata: { 
          type: 'navigation',
          from: fromScreen,
          to: toScreen,
        },
      });
    };
  }, []);

  return {
    measureTap,
    measureScroll,
    measureNavigation,
  };
}

/**
 * Hook for tracking form performance
 */
export function useFormPerformance(formName: string) {
  const startTime = useRef<number>(0);
  const fieldInteractions = useRef<Record<string, number>>({});

  const startForm = useCallback(() => {
    startTime.current = performance.now();
    fieldInteractions.current = {};
  }, []);

  const trackFieldInteraction = useCallback((fieldName: string) => {
    fieldInteractions.current[fieldName] = (fieldInteractions.current[fieldName] || 0) + 1;
    
    PerformanceMonitor.recordMetric({
      name: `form_field_interaction`,
      value: fieldInteractions.current[fieldName],
      timestamp: Date.now(),
      category: 'interaction',
      metadata: {
        form: formName,
        field: fieldName,
        totalInteractions: fieldInteractions.current[fieldName],
      },
    });
  }, [formName]);

  const submitForm = useCallback((success: boolean, validationErrors?: string[]) => {
    const completionTime = performance.now() - startTime.current;
    const totalInteractions = Object.values(fieldInteractions.current).reduce((a, b) => a + b, 0);
    
    PerformanceMonitor.recordMetric({
      name: `form_submission`,
      value: completionTime,
      timestamp: Date.now(),
      category: 'interaction',
      metadata: {
        form: formName,
        success,
        completionTime,
        totalInteractions,
        validationErrors: validationErrors?.length || 0,
        fieldsInteracted: Object.keys(fieldInteractions.current).length,
      },
    });
  }, [formName]);

  return {
    startForm,
    trackFieldInteraction,
    submitForm,
  };
}

/**
 * Hook for network request performance tracking
 */
export function useNetworkPerformance() {
  const trackRequest = useCallback(<T>(
    requestName: string,
    requestPromise: Promise<T>
  ): Promise<T> => {
    const startTime = performance.now();
    
    return requestPromise
      .then((result) => {
        const duration = performance.now() - startTime;
        
        PerformanceMonitor.recordMetric({
          name: `network_${requestName}`,
          value: duration,
          timestamp: Date.now(),
          category: 'network',
          metadata: {
            success: true,
            requestName,
          },
        });
        
        return result;
      })
      .catch((error) => {
        const duration = performance.now() - startTime;
        
        PerformanceMonitor.recordMetric({
          name: `network_${requestName}`,
          value: duration,
          timestamp: Date.now(),
          category: 'network',
          metadata: {
            success: false,
            requestName,
            error: error.message,
          },
        });
        
        throw error;
      });
  }, []);

  return { trackRequest };
}