/**
 * Performance Monitor - Comprehensive performance tracking and optimization utilities
 * Monitors app performance, memory usage, render times, and provides optimization insights
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { InteractionManager, DeviceEventEmitter } from 'react-native';
import * as Sentry from '@sentry/react-native';

export interface PerformanceMetric {
  name: string;
  value: number;
  timestamp: number;
  metadata?: Record<string, any>;
  category: 'render' | 'network' | 'memory' | 'interaction' | 'bundle' | 'custom';
}

export interface RenderMetrics {
  componentName: string;
  renderTime: number;
  mountTime: number;
  updateCount: number;
  propsChangeCount: number;
  rerenderReasons: string[];
}

export interface MemoryMetrics {
  jsHeapSizeUsed: number;
  jsHeapSizeTotal: number;
  jsHeapSizeLimit: number;
  nativeHeapSize?: number;
  timestamp: number;
}

export interface NetworkMetrics {
  url: string;
  method: string;
  duration: number;
  responseSize: number;
  statusCode: number;
  timestamp: number;
  error?: string;
}

export interface BundleMetrics {
  totalSize: number;
  chunkSizes: Record<string, number>;
  loadTime: number;
  compressionRatio: number;
}

export interface PerformanceReport {
  period: {
    start: number;
    end: number;
  };
  metrics: {
    render: RenderMetrics[];
    memory: MemoryMetrics[];
    network: NetworkMetrics[];
    interactions: PerformanceMetric[];
  };
  summary: {
    averageRenderTime: number;
    slowestComponents: string[];
    memoryLeaks: string[];
    networkBottlenecks: string[];
    suggestions: string[];
  };
}

class PerformanceMonitor {
  private static instance: PerformanceMonitor;
  private metrics: PerformanceMetric[] = [];
  private renderMetrics: Map<string, RenderMetrics> = new Map();
  private memorySnapshots: MemoryMetrics[] = [];
  private networkRequests: NetworkMetrics[] = [];
  private isMonitoring: boolean = false;
  private memoryInterval?: NodeJS.Timeout;
  private maxMetrics: number = 1000;
  private performanceObserver?: PerformanceObserver;
  
  // Performance thresholds
  private thresholds = {
    renderTime: 16, // 60fps target
    memoryGrowth: 50 * 1024 * 1024, // 50MB
    networkTimeout: 10000, // 10 seconds
    interactionDelay: 100, // 100ms
  };

  private constructor() {
    this.setupPerformanceObserver();
    this.setupMemoryMonitoring();
  }

  static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor();
    }
    return PerformanceMonitor.instance;
  }

  /**
   * Start performance monitoring
   */
  startMonitoring(): void {
    if (this.isMonitoring) return;

    this.isMonitoring = true;
    this.startMemoryMonitoring();
    this.setupNetworkInterception();
    console.log('Performance monitoring started');
  }

  /**
   * Stop performance monitoring
   */
  stopMonitoring(): void {
    this.isMonitoring = false;
    
    if (this.memoryInterval) {
      clearInterval(this.memoryInterval);
      this.memoryInterval = undefined;
    }

    if (this.performanceObserver) {
      this.performanceObserver.disconnect();
    }

    console.log('Performance monitoring stopped');
  }

  /**
   * Setup Performance Observer for Web APIs
   */
  private setupPerformanceObserver(): void {
    if (typeof PerformanceObserver !== 'undefined') {
      this.performanceObserver = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        entries.forEach((entry) => {
          this.recordMetric({
            name: entry.name,
            value: entry.duration || entry.startTime,
            timestamp: Date.now(),
            category: 'custom',
            metadata: {
              entryType: entry.entryType,
              startTime: entry.startTime,
            },
          });
        });
      });

      try {
        this.performanceObserver.observe({ 
          entryTypes: ['measure', 'navigation', 'resource'] 
        });
      } catch (error) {
        console.warn('Performance Observer not fully supported:', error);
      }
    }
  }

  /**
   * Setup memory monitoring
   */
  private setupMemoryMonitoring(): void {
    if (typeof performance !== 'undefined' && performance.memory) {
      this.memoryInterval = setInterval(() => {
        this.captureMemorySnapshot();
      }, 5000); // Every 5 seconds
    }
  }

  /**
   * Start memory monitoring
   */
  private startMemoryMonitoring(): void {
    if (!this.isMonitoring) return;

    const captureMemory = () => {
      this.captureMemorySnapshot();
      if (this.isMonitoring) {
        setTimeout(captureMemory, 5000);
      }
    };

    captureMemory();
  }

  /**
   * Capture memory snapshot
   */
  private captureMemorySnapshot(): void {
    let memoryInfo: MemoryMetrics;

    if (typeof performance !== 'undefined' && performance.memory) {
      // Web environment
      memoryInfo = {
        jsHeapSizeUsed: performance.memory.usedJSHeapSize,
        jsHeapSizeTotal: performance.memory.totalJSHeapSize,
        jsHeapSizeLimit: performance.memory.jsHeapSizeLimit,
        timestamp: Date.now(),
      };
    } else {
      // React Native environment - estimate memory usage
      memoryInfo = {
        jsHeapSizeUsed: this.estimateMemoryUsage(),
        jsHeapSizeTotal: this.estimateMemoryUsage() * 1.5,
        jsHeapSizeLimit: 100 * 1024 * 1024, // Estimated 100MB limit
        timestamp: Date.now(),
      };
    }

    this.memorySnapshots.push(memoryInfo);
    
    // Keep only last 100 snapshots
    if (this.memorySnapshots.length > 100) {
      this.memorySnapshots.shift();
    }

    // Check for memory leaks
    this.detectMemoryLeaks();
  }

  /**
   * Estimate memory usage in React Native
   */
  private estimateMemoryUsage(): number {
    // Rough estimation based on app state
    const baseMemory = 20 * 1024 * 1024; // 20MB base
    const metricsMemory = this.metrics.length * 1024; // 1KB per metric
    const componentsMemory = this.renderMetrics.size * 10 * 1024; // 10KB per component
    
    return baseMemory + metricsMemory + componentsMemory;
  }

  /**
   * Detect memory leaks
   */
  private detectMemoryLeaks(): void {
    if (this.memorySnapshots.length < 10) return;

    const recent = this.memorySnapshots.slice(-10);
    const first = recent[0];
    const last = recent[recent.length - 1];
    
    const growth = last.jsHeapSizeUsed - first.jsHeapSizeUsed;
    const timeSpan = last.timestamp - first.timestamp;
    
    if (growth > this.thresholds.memoryGrowth) {
      const growthRate = growth / (timeSpan / 1000); // bytes per second
      
      this.recordMetric({
        name: 'memory_leak_detected',
        value: growth,
        timestamp: Date.now(),
        category: 'memory',
        metadata: {
          growthRate,
          timeSpan,
          severity: growth > this.thresholds.memoryGrowth * 2 ? 'high' : 'medium',
        },
      });

      // Report to Sentry
      Sentry.addBreadcrumb({
        message: 'Memory leak detected',
        level: 'warning',
        data: { growth, growthRate },
      });
    }
  }

  /**
   * Setup network request interception
   */
  private setupNetworkInterception(): void {
    const originalFetch = global.fetch;
    
    global.fetch = async (input: RequestInfo, init?: RequestInit) => {
      const startTime = Date.now();
      const url = typeof input === 'string' ? input : input.url;
      const method = init?.method || 'GET';

      try {
        const response = await originalFetch(input, init);
        const endTime = Date.now();
        
        this.recordNetworkMetric({
          url,
          method,
          duration: endTime - startTime,
          responseSize: parseInt(response.headers.get('content-length') || '0'),
          statusCode: response.status,
          timestamp: startTime,
        });

        return response;
      } catch (error: any) {
        const endTime = Date.now();
        
        this.recordNetworkMetric({
          url,
          method,
          duration: endTime - startTime,
          responseSize: 0,
          statusCode: 0,
          timestamp: startTime,
          error: error.message,
        });

        throw error;
      }
    };
  }

  /**
   * Record a performance metric
   */
  recordMetric(metric: PerformanceMetric): void {
    this.metrics.push(metric);
    
    // Keep metrics within limit
    if (this.metrics.length > this.maxMetrics) {
      this.metrics.shift();
    }

    // Emit event for real-time monitoring
    DeviceEventEmitter.emit('performanceMetric', metric);

    // Check thresholds
    this.checkThresholds(metric);
  }

  /**
   * Record render metrics for a component
   */
  recordRenderMetric(componentName: string, renderTime: number, metadata?: any): void {
    const existing = this.renderMetrics.get(componentName) || {
      componentName,
      renderTime: 0,
      mountTime: 0,
      updateCount: 0,
      propsChangeCount: 0,
      rerenderReasons: [],
    };

    existing.renderTime = renderTime;
    existing.updateCount++;
    
    if (metadata?.propsChanged) {
      existing.propsChangeCount++;
    }
    
    if (metadata?.reason) {
      existing.rerenderReasons.push(metadata.reason);
    }

    this.renderMetrics.set(componentName, existing);

    // Record as general metric
    this.recordMetric({
      name: `render_${componentName}`,
      value: renderTime,
      timestamp: Date.now(),
      category: 'render',
      metadata,
    });
  }

  /**
   * Record network metric
   */
  private recordNetworkMetric(metric: NetworkMetrics): void {
    this.networkRequests.push(metric);
    
    // Keep only last 500 requests
    if (this.networkRequests.length > 500) {
      this.networkRequests.shift();
    }

    // Record as general metric
    this.recordMetric({
      name: 'network_request',
      value: metric.duration,
      timestamp: metric.timestamp,
      category: 'network',
      metadata: {
        url: metric.url,
        method: metric.method,
        statusCode: metric.statusCode,
        error: metric.error,
      },
    });
  }

  /**
   * Check performance thresholds
   */
  private checkThresholds(metric: PerformanceMetric): void {
    switch (metric.category) {
      case 'render':
        if (metric.value > this.thresholds.renderTime) {
          this.reportPerformanceIssue('slow_render', metric);
        }
        break;
        
      case 'network':
        if (metric.value > this.thresholds.networkTimeout) {
          this.reportPerformanceIssue('slow_network', metric);
        }
        break;
        
      case 'interaction':
        if (metric.value > this.thresholds.interactionDelay) {
          this.reportPerformanceIssue('slow_interaction', metric);
        }
        break;
    }
  }

  /**
   * Report performance issue
   */
  private reportPerformanceIssue(type: string, metric: PerformanceMetric): void {
    const issue = {
      type,
      metric: metric.name,
      value: metric.value,
      timestamp: metric.timestamp,
      metadata: metric.metadata,
    };

    // Log to console in development
    if (__DEV__) {
      console.warn('Performance Issue:', issue);
    }

    // Send to Sentry
    Sentry.addBreadcrumb({
      message: `Performance issue: ${type}`,
      level: 'warning',
      data: issue,
    });

    // Emit event
    DeviceEventEmitter.emit('performanceIssue', issue);
  }

  /**
   * Measure function execution time
   */
  measureFunction<T>(name: string, fn: () => T): T {
    const startTime = performance.now();
    
    try {
      const result = fn();
      
      if (result instanceof Promise) {
        return result.finally(() => {
          const endTime = performance.now();
          this.recordMetric({
            name,
            value: endTime - startTime,
            timestamp: Date.now(),
            category: 'custom',
            metadata: { async: true },
          });
        }) as T;
      } else {
        const endTime = performance.now();
        this.recordMetric({
          name,
          value: endTime - startTime,
          timestamp: Date.now(),
          category: 'custom',
        });
        return result;
      }
    } catch (error) {
      const endTime = performance.now();
      this.recordMetric({
        name,
        value: endTime - startTime,
        timestamp: Date.now(),
        category: 'custom',
        metadata: { error: true },
      });
      throw error;
    }
  }

  /**
   * Measure async function execution time
   */
  async measureAsync<T>(name: string, fn: () => Promise<T>): Promise<T> {
    const startTime = performance.now();
    
    try {
      const result = await fn();
      const endTime = performance.now();
      
      this.recordMetric({
        name,
        value: endTime - startTime,
        timestamp: Date.now(),
        category: 'custom',
        metadata: { async: true },
      });
      
      return result;
    } catch (error) {
      const endTime = performance.now();
      this.recordMetric({
        name,
        value: endTime - startTime,
        timestamp: Date.now(),
        category: 'custom',
        metadata: { async: true, error: true },
      });
      throw error;
    }
  }

  /**
   * Start interaction measurement
   */
  startInteraction(name: string): () => void {
    const startTime = performance.now();
    
    return () => {
      const endTime = performance.now();
      this.recordMetric({
        name: `interaction_${name}`,
        value: endTime - startTime,
        timestamp: Date.now(),
        category: 'interaction',
      });
    };
  }

  /**
   * Mark app startup complete
   */
  markAppStartupComplete(): void {
    InteractionManager.runAfterInteractions(() => {
      this.recordMetric({
        name: 'app_startup_complete',
        value: performance.now(),
        timestamp: Date.now(),
        category: 'custom',
        metadata: { milestone: true },
      });
    });
  }

  /**
   * Generate performance report
   */
  async generateReport(periodHours: number = 24): Promise<PerformanceReport> {
    const now = Date.now();
    const startTime = now - (periodHours * 60 * 60 * 1000);
    
    // Filter metrics by time period
    const periodMetrics = this.metrics.filter(
      m => m.timestamp >= startTime && m.timestamp <= now
    );
    
    const renderMetrics = Array.from(this.renderMetrics.values());
    const memoryMetrics = this.memorySnapshots.filter(
      m => m.timestamp >= startTime && m.timestamp <= now
    );
    const networkMetrics = this.networkRequests.filter(
      m => m.timestamp >= startTime && m.timestamp <= now
    );
    
    const interactions = periodMetrics.filter(m => m.category === 'interaction');
    
    // Calculate averages and identify issues
    const renderTimes = renderMetrics.map(r => r.renderTime);
    const averageRenderTime = renderTimes.reduce((a, b) => a + b, 0) / renderTimes.length || 0;
    
    const slowestComponents = renderMetrics
      .filter(r => r.renderTime > this.thresholds.renderTime)
      .sort((a, b) => b.renderTime - a.renderTime)
      .slice(0, 5)
      .map(r => r.componentName);
    
    const memoryLeaks = this.detectMemoryLeaksInPeriod(memoryMetrics);
    const networkBottlenecks = this.identifyNetworkBottlenecks(networkMetrics);
    const suggestions = this.generateOptimizationSuggestions(
      renderMetrics, 
      memoryMetrics, 
      networkMetrics
    );

    const report: PerformanceReport = {
      period: { start: startTime, end: now },
      metrics: {
        render: renderMetrics,
        memory: memoryMetrics,
        network: networkMetrics,
        interactions,
      },
      summary: {
        averageRenderTime,
        slowestComponents,
        memoryLeaks,
        networkBottlenecks,
        suggestions,
      },
    };

    // Store report
    await this.storeReport(report);
    
    return report;
  }

  /**
   * Detect memory leaks in a period
   */
  private detectMemoryLeaksInPeriod(snapshots: MemoryMetrics[]): string[] {
    if (snapshots.length < 5) return [];

    const leaks: string[] = [];
    const growthThreshold = 10 * 1024 * 1024; // 10MB

    for (let i = 4; i < snapshots.length; i++) {
      const window = snapshots.slice(i - 4, i + 1);
      const growth = window[4].jsHeapSizeUsed - window[0].jsHeapSizeUsed;
      
      if (growth > growthThreshold) {
        leaks.push(`Memory leak detected at ${new Date(window[4].timestamp).toISOString()}`);
      }
    }

    return leaks;
  }

  /**
   * Identify network bottlenecks
   */
  private identifyNetworkBottlenecks(requests: NetworkMetrics[]): string[] {
    const bottlenecks: string[] = [];
    const slowRequests = requests.filter(r => r.duration > this.thresholds.networkTimeout);
    
    // Group by URL
    const urlGroups = slowRequests.reduce((groups, request) => {
      const url = new URL(request.url).pathname;
      groups[url] = (groups[url] || 0) + 1;
      return groups;
    }, {} as Record<string, number>);

    Object.entries(urlGroups)
      .filter(([_, count]) => count > 2)
      .forEach(([url, count]) => {
        bottlenecks.push(`Slow endpoint: ${url} (${count} slow requests)`);
      });

    return bottlenecks;
  }

  /**
   * Generate optimization suggestions
   */
  private generateOptimizationSuggestions(
    renderMetrics: RenderMetrics[],
    memoryMetrics: MemoryMetrics[],
    networkMetrics: NetworkMetrics[]
  ): string[] {
    const suggestions: string[] = [];

    // Render optimizations
    const slowComponents = renderMetrics.filter(r => r.renderTime > this.thresholds.renderTime);
    if (slowComponents.length > 0) {
      suggestions.push(`Optimize ${slowComponents.length} slow-rendering components using React.memo or useMemo`);
    }

    const frequentRerenders = renderMetrics.filter(r => r.updateCount > 50);
    if (frequentRerenders.length > 0) {
      suggestions.push(`Reduce re-renders for ${frequentRerenders.length} frequently updating components`);
    }

    // Memory optimizations
    if (memoryMetrics.length > 0) {
      const latestMemory = memoryMetrics[memoryMetrics.length - 1];
      const memoryUsage = latestMemory.jsHeapSizeUsed / (1024 * 1024);
      
      if (memoryUsage > 50) {
        suggestions.push('High memory usage detected - consider implementing component recycling');
      }
    }

    // Network optimizations
    const slowRequests = networkMetrics.filter(r => r.duration > 5000);
    if (slowRequests.length > 0) {
      suggestions.push('Implement request caching and optimize API response sizes');
    }

    const failedRequests = networkMetrics.filter(r => r.error);
    if (failedRequests.length > 0) {
      suggestions.push('Implement retry logic and offline handling for network requests');
    }

    return suggestions;
  }

  /**
   * Store performance report
   */
  private async storeReport(report: PerformanceReport): Promise<void> {
    try {
      const reportKey = `@performance_report_${report.period.start}`;
      await AsyncStorage.setItem(reportKey, JSON.stringify(report));
      
      // Keep only last 10 reports
      const keys = await AsyncStorage.getAllKeys();
      const reportKeys = keys.filter(key => key.startsWith('@performance_report_'));
      
      if (reportKeys.length > 10) {
        const sortedKeys = reportKeys.sort();
        const keysToRemove = sortedKeys.slice(0, -10);
        await AsyncStorage.multiRemove(keysToRemove);
      }
    } catch (error) {
      console.error('Failed to store performance report:', error);
    }
  }

  /**
   * Get stored reports
   */
  async getStoredReports(): Promise<PerformanceReport[]> {
    try {
      const keys = await AsyncStorage.getAllKeys();
      const reportKeys = keys.filter(key => key.startsWith('@performance_report_'));
      const reports: PerformanceReport[] = [];
      
      for (const key of reportKeys) {
        const reportData = await AsyncStorage.getItem(key);
        if (reportData) {
          reports.push(JSON.parse(reportData));
        }
      }
      
      return reports.sort((a, b) => b.period.start - a.period.start);
    } catch (error) {
      console.error('Failed to get stored reports:', error);
      return [];
    }
  }

  /**
   * Clear all metrics and reports
   */
  async clearData(): Promise<void> {
    this.metrics = [];
    this.renderMetrics.clear();
    this.memorySnapshots = [];
    this.networkRequests = [];
    
    try {
      const keys = await AsyncStorage.getAllKeys();
      const reportKeys = keys.filter(key => key.startsWith('@performance_report_'));
      await AsyncStorage.multiRemove(reportKeys);
    } catch (error) {
      console.error('Failed to clear performance data:', error);
    }
  }

  /**
   * Get current performance stats
   */
  getCurrentStats(): {
    metricsCount: number;
    componentsTracked: number;
    memorySnapshots: number;
    networkRequests: number;
    isMonitoring: boolean;
  } {
    return {
      metricsCount: this.metrics.length,
      componentsTracked: this.renderMetrics.size,
      memorySnapshots: this.memorySnapshots.length,
      networkRequests: this.networkRequests.length,
      isMonitoring: this.isMonitoring,
    };
  }
}

export default PerformanceMonitor.getInstance();