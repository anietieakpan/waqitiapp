import { onCLS, onINP, onFCP, onLCP, onTTFB, type Metric } from 'web-vitals';

interface PerformanceMetrics {
  // Web Vitals
  cls?: number;
  fid?: number;
  fcp?: number;
  lcp?: number;
  ttfb?: number;
  
  // Custom metrics
  renderTime?: number;
  apiResponseTime?: number;
  bundleSize?: number;
  memoryUsage?: number;
  
  // User experience
  timeToInteractive?: number;
  firstInputDelay?: number;
  cumulativeLayoutShift?: number;
}

interface PerformanceEvent {
  type: 'page_load' | 'navigation' | 'user_action' | 'api_call' | 'error';
  name: string;
  duration?: number;
  metadata?: Record<string, any>;
  timestamp: number;
  sessionId: string;
  userId?: string;
  url: string;
  userAgent: string;
}

class PerformanceMonitor {
  private metrics: PerformanceMetrics = {};
  private events: PerformanceEvent[] = [];
  private sessionId: string;
  private userId?: string;
  private analyticsEndpoint: string;
  private bufferSize: number = 50;
  private flushInterval: number = 30000; // 30 seconds
  private observer?: PerformanceObserver;
  private timers: Map<string, number> = new Map();

  constructor() {
    this.sessionId = this.generateSessionId();
    this.analyticsEndpoint = import.meta.env.VITE_ANALYTICS_ENDPOINT || '/api/analytics/performance';
    
    this.initializeWebVitals();
    this.initializePerformanceObserver();
    this.initializeNavigationTiming();
    this.initializeMemoryMonitoring();
    this.setupAutoFlush();
    this.setupBeforeUnload();
  }

  private generateSessionId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  private initializeWebVitals(): void {
    onCLS((metric: Metric) => this.recordWebVital('cls', metric));
    onINP((metric: Metric) => this.recordWebVital('fid', metric)); // INP replaces FID
    onFCP((metric: Metric) => this.recordWebVital('fcp', metric));
    onLCP((metric: Metric) => this.recordWebVital('lcp', metric));
    onTTFB((metric: Metric) => this.recordWebVital('ttfb', metric));
  }

  private recordWebVital(name: string, metric: Metric): void {
    this.metrics[name as keyof PerformanceMetrics] = metric.value;
    
    this.recordEvent({
      type: 'page_load',
      name: `web_vital_${name}`,
      duration: metric.value,
      metadata: {
        id: metric.id,
        navigationType: metric.navigationType,
        rating: this.getMetricRating(name, metric.value),
      },
    });
  }

  private getMetricRating(metric: string, value: number): 'good' | 'needs-improvement' | 'poor' {
    const thresholds = {
      cls: { good: 0.1, poor: 0.25 },
      fid: { good: 100, poor: 300 },
      fcp: { good: 1800, poor: 3000 },
      lcp: { good: 2500, poor: 4000 },
      ttfb: { good: 800, poor: 1800 },
    };

    const threshold = thresholds[metric as keyof typeof thresholds];
    if (!threshold) return 'good';

    if (value <= threshold.good) return 'good';
    if (value <= threshold.poor) return 'needs-improvement';
    return 'poor';
  }

  private initializePerformanceObserver(): void {
    if ('PerformanceObserver' in window) {
      this.observer = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.processPerformanceEntry(entry);
        }
      });

      // Observe various performance entry types
      try {
        this.observer.observe({ type: 'navigation', buffered: true });
        this.observer.observe({ type: 'resource', buffered: true });
        this.observer.observe({ type: 'paint', buffered: true });
        this.observer.observe({ type: 'layout-shift', buffered: true });
        this.observer.observe({ type: 'first-input', buffered: true });
      } catch (error) {
        console.warn('Some performance observers not supported:', error);
      }
    }
  }

  private processPerformanceEntry(entry: PerformanceEntry): void {
    switch (entry.entryType) {
      case 'navigation':
        this.processNavigationEntry(entry as PerformanceNavigationTiming);
        break;
      case 'resource':
        this.processResourceEntry(entry as PerformanceResourceTiming);
        break;
      case 'paint':
        this.processPaintEntry(entry);
        break;
      case 'layout-shift':
        this.processLayoutShiftEntry(entry as any);
        break;
      case 'first-input':
        this.processFirstInputEntry(entry as any);
        break;
    }
  }

  private processNavigationEntry(entry: PerformanceNavigationTiming): void {
    const metrics = {
      domContentLoaded: entry.domContentLoadedEventEnd - entry.domContentLoadedEventStart,
      domComplete: entry.domComplete - entry.fetchStart,
      loadComplete: entry.loadEventEnd - entry.fetchStart,
      dnsLookup: entry.domainLookupEnd - entry.domainLookupStart,
      tcpConnect: entry.connectEnd - entry.connectStart,
      requestTime: entry.responseStart - entry.requestStart,
      responseTime: entry.responseEnd - entry.responseStart,
      domProcessing: entry.domInteractive - entry.responseEnd,
    };

    this.recordEvent({
      type: 'page_load',
      name: 'navigation_timing',
      duration: metrics.loadComplete,
      metadata: metrics,
    });
  }

  private processResourceEntry(entry: PerformanceResourceTiming): void {
    // Track slow resources
    if (entry.duration > 1000) {
      this.recordEvent({
        type: 'page_load',
        name: 'slow_resource',
        duration: entry.duration,
        metadata: {
          url: entry.name,
          initiatorType: entry.initiatorType,
          size: entry.transferSize,
        },
      });
    }
  }

  private processPaintEntry(entry: PerformanceEntry): void {
    this.recordEvent({
      type: 'page_load',
      name: entry.name,
      duration: entry.startTime,
      metadata: {
        entryType: entry.entryType,
      },
    });
  }

  private processLayoutShiftEntry(entry: any): void {
    if (entry.hadRecentInput) return; // Ignore shifts from user input

    this.recordEvent({
      type: 'page_load',
      name: 'layout_shift',
      duration: entry.value,
      metadata: {
        sources: entry.sources?.map((source: any) => ({
          node: source.node?.tagName || 'unknown',
          currentRect: source.currentRect,
          previousRect: source.previousRect,
        })),
      },
    });
  }

  private processFirstInputEntry(entry: any): void {
    this.recordEvent({
      type: 'user_action',
      name: 'first_input',
      duration: entry.processingStart - entry.startTime,
      metadata: {
        inputType: entry.name,
      },
    });
  }

  private initializeNavigationTiming(): void {
    window.addEventListener('load', () => {
      // Calculate Time to Interactive
      if ('performance' in window && 'timing' in performance) {
        const timing = performance.timing;
        const tti = timing.domInteractive - timing.navigationStart;
        this.metrics.timeToInteractive = tti;
      }
    });
  }

  private initializeMemoryMonitoring(): void {
    if ('memory' in performance) {
      const memoryInfo = (performance as any).memory;
      this.metrics.memoryUsage = memoryInfo.usedJSHeapSize;
      
      // Monitor memory usage periodically
      setInterval(() => {
        const currentMemory = (performance as any).memory.usedJSHeapSize;
        if (currentMemory > this.metrics.memoryUsage! * 1.5) {
          this.recordEvent({
            type: 'error',
            name: 'memory_spike',
            metadata: {
              current: currentMemory,
              previous: this.metrics.memoryUsage,
              increase: ((currentMemory - this.metrics.memoryUsage!) / this.metrics.memoryUsage!) * 100,
            },
          });
        }
        this.metrics.memoryUsage = currentMemory;
      }, 30000);
    }
  }

  private setupAutoFlush(): void {
    setInterval(() => {
      this.flush();
    }, this.flushInterval);
  }

  private setupBeforeUnload(): void {
    window.addEventListener('beforeunload', () => {
      this.flush(true);
    });

    // Use Page Visibility API to flush when page becomes hidden
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        this.flush(true);
      }
    });
  }

  // Public API methods

  public setUserId(userId: string): void {
    this.userId = userId;
  }

  public startTimer(name: string): void {
    this.timers.set(name, performance.now());
  }

  public endTimer(name: string): number {
    const startTime = this.timers.get(name);
    if (!startTime) {
      console.warn(`Timer "${name}" was not started`);
      return 0;
    }

    const duration = performance.now() - startTime;
    this.timers.delete(name);

    this.recordEvent({
      type: 'user_action',
      name: `timer_${name}`,
      duration,
    });

    return duration;
  }

  public recordEvent(event: Partial<PerformanceEvent>): void {
    const fullEvent: PerformanceEvent = {
      type: event.type || 'user_action',
      name: event.name || 'unknown',
      duration: event.duration,
      metadata: event.metadata,
      timestamp: Date.now(),
      sessionId: this.sessionId,
      userId: this.userId,
      url: window.location.href,
      userAgent: navigator.userAgent,
    };

    this.events.push(fullEvent);

    // Auto-flush if buffer is full
    if (this.events.length >= this.bufferSize) {
      this.flush();
    }
  }

  public recordPageView(pageName: string, metadata?: Record<string, any>): void {
    this.recordEvent({
      type: 'navigation',
      name: 'page_view',
      metadata: {
        pageName,
        ...metadata,
      },
    });
  }

  public recordUserAction(actionName: string, duration?: number, metadata?: Record<string, any>): void {
    this.recordEvent({
      type: 'user_action',
      name: actionName,
      duration,
      metadata,
    });
  }

  public recordAPICall(url: string, method: string, duration: number, status: number): void {
    this.recordEvent({
      type: 'api_call',
      name: 'api_request',
      duration,
      metadata: {
        url,
        method,
        status,
        isError: status >= 400,
      },
    });
  }

  public recordError(errorName: string, errorMessage: string, metadata?: Record<string, any>): void {
    this.recordEvent({
      type: 'error',
      name: errorName,
      metadata: {
        message: errorMessage,
        stack: new Error().stack,
        ...metadata,
      },
    });
  }

  public getMetrics(): PerformanceMetrics {
    return { ...this.metrics };
  }

  public getEvents(): PerformanceEvent[] {
    return [...this.events];
  }

  private async flush(sync: boolean = false): Promise<void> {
    if (this.events.length === 0) return;

    const payload = {
      sessionId: this.sessionId,
      userId: this.userId,
      metrics: this.metrics,
      events: this.events,
      timestamp: Date.now(),
      url: window.location.href,
    };

    // Clear events buffer
    this.events = [];

    try {
      if (sync && 'sendBeacon' in navigator) {
        // Use sendBeacon for synchronous requests (like page unload)
        navigator.sendBeacon(this.analyticsEndpoint, JSON.stringify(payload));
      } else {
        // Use fetch for regular requests
        await fetch(this.analyticsEndpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        }).catch((error) => {
          console.error('Failed to send performance data:', error);
          // Re-add events to buffer on failure
          this.events.unshift(...payload.events);
        });
      }
    } catch (error) {
      console.error('Failed to send performance data:', error);
      // Re-add events to buffer on failure
      this.events.unshift(...payload.events);
    }
  }

  public destroy(): void {
    this.observer?.disconnect();
    this.flush(true);
  }
}

// Create and export singleton instance
export const performanceMonitor = new PerformanceMonitor();

// React Hook for easy integration
export function usePerformanceMonitor() {
  return {
    startTimer: performanceMonitor.startTimer.bind(performanceMonitor),
    endTimer: performanceMonitor.endTimer.bind(performanceMonitor),
    recordEvent: performanceMonitor.recordEvent.bind(performanceMonitor),
    recordPageView: performanceMonitor.recordPageView.bind(performanceMonitor),
    recordUserAction: performanceMonitor.recordUserAction.bind(performanceMonitor),
    recordAPICall: performanceMonitor.recordAPICall.bind(performanceMonitor),
    recordError: performanceMonitor.recordError.bind(performanceMonitor),
    getMetrics: performanceMonitor.getMetrics.bind(performanceMonitor),
  };
}

// Automatic error tracking
window.addEventListener('error', (event) => {
  performanceMonitor.recordError('javascript_error', event.message, {
    filename: event.filename,
    lineno: event.lineno,
    colno: event.colno,
    stack: event.error?.stack,
  });
});

window.addEventListener('unhandledrejection', (event) => {
  performanceMonitor.recordError('unhandled_promise_rejection', event.reason?.toString(), {
    promise: event.promise,
  });
});

export default performanceMonitor;