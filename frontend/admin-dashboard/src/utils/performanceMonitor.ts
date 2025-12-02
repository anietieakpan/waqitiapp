import { getCLS, getFID, getFCP, getLCP, getTTFB, Metric } from 'web-vitals';

interface AdminPerformanceMetrics {
  // Web Vitals
  cls?: number;
  fid?: number;
  fcp?: number;
  lcp?: number;
  ttfb?: number;
  
  // Admin-specific metrics
  dashboardLoadTime?: number;
  chartRenderTime?: number;
  dataTableRenderTime?: number;
  realTimeDataLatency?: number;
  reportGenerationTime?: number;
  
  // System performance
  memoryUsage?: number;
  cpuUsage?: number;
  networkLatency?: number;
}

interface AdminPerformanceEvent {
  type: 'dashboard_load' | 'chart_render' | 'data_fetch' | 'user_action' | 'system_event' | 'error';
  name: string;
  duration?: number;
  metadata?: Record<string, any>;
  timestamp: number;
  sessionId: string;
  adminId?: string;
  url: string;
  userAgent: string;
  severity?: 'low' | 'medium' | 'high' | 'critical';
}

interface RealTimeMetrics {
  activeUsers: number;
  systemLoad: number;
  responseTime: number;
  errorRate: number;
  throughput: number;
}

class AdminPerformanceMonitor {
  private metrics: AdminPerformanceMetrics = {};
  private events: AdminPerformanceEvent[] = [];
  private realTimeMetrics: RealTimeMetrics = {
    activeUsers: 0,
    systemLoad: 0,
    responseTime: 0,
    errorRate: 0,
    throughput: 0,
  };
  
  private sessionId: string;
  private adminId?: string;
  private analyticsEndpoint: string;
  private bufferSize: number = 100;
  private flushInterval: number = 15000; // 15 seconds for admin
  private observer?: PerformanceObserver;
  private timers: Map<string, number> = new Map();
  private realTimeSocket?: WebSocket;
  private performanceAlerts: Set<string> = new Set();

  constructor() {
    this.sessionId = this.generateSessionId();
    this.analyticsEndpoint = process.env.REACT_APP_ADMIN_ANALYTICS_ENDPOINT || '/api/admin/analytics/performance';
    
    this.initializeWebVitals();
    this.initializePerformanceObserver();
    this.initializeAdminSpecificMonitoring();
    this.initializeRealTimeConnection();
    this.initializeSystemMonitoring();
    this.setupAutoFlush();
    this.setupBeforeUnload();
    this.initializePerformanceAlerts();
  }

  private generateSessionId(): string {
    return `admin-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  private initializeWebVitals(): void {
    getCLS((metric) => this.recordWebVital('cls', metric));
    getFID((metric) => this.recordWebVital('fid', metric));
    getFCP((metric) => this.recordWebVital('fcp', metric));
    getLCP((metric) => this.recordWebVital('lcp', metric));
    getTTFB((metric) => this.recordWebVital('ttfb', metric));
  }

  private recordWebVital(name: string, metric: Metric): void {
    this.metrics[name as keyof AdminPerformanceMetrics] = metric.value;
    
    this.recordEvent({
      type: 'dashboard_load',
      name: `web_vital_${name}`,
      duration: metric.value,
      severity: this.getMetricSeverity(name, metric.value),
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

  private getMetricSeverity(metric: string, value: number): 'low' | 'medium' | 'high' | 'critical' {
    const rating = this.getMetricRating(metric, value);
    switch (rating) {
      case 'good': return 'low';
      case 'needs-improvement': return 'medium';
      case 'poor': return 'high';
      default: return 'low';
    }
  }

  private initializePerformanceObserver(): void {
    if ('PerformanceObserver' in window) {
      this.observer = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.processPerformanceEntry(entry);
        }
      });

      try {
        this.observer.observe({ type: 'navigation', buffered: true });
        this.observer.observe({ type: 'resource', buffered: true });
        this.observer.observe({ type: 'paint', buffered: true });
        this.observer.observe({ type: 'layout-shift', buffered: true });
        this.observer.observe({ type: 'first-input', buffered: true });
        this.observer.observe({ type: 'largest-contentful-paint', buffered: true });
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
    }
  }

  private processNavigationEntry(entry: PerformanceNavigationTiming): void {
    const dashboardLoadTime = entry.loadEventEnd - entry.navigationStart;
    this.metrics.dashboardLoadTime = dashboardLoadTime;

    this.recordEvent({
      type: 'dashboard_load',
      name: 'dashboard_navigation',
      duration: dashboardLoadTime,
      severity: dashboardLoadTime > 3000 ? 'high' : dashboardLoadTime > 1500 ? 'medium' : 'low',
      metadata: {
        domContentLoaded: entry.domContentLoadedEventEnd - entry.domContentLoadedEventStart,
        dnsLookup: entry.domainLookupEnd - entry.domainLookupStart,
        tcpConnect: entry.connectEnd - entry.connectStart,
        serverResponse: entry.responseEnd - entry.requestStart,
      },
    });
  }

  private processResourceEntry(entry: PerformanceResourceTiming): void {
    // Track chart libraries and large assets
    if (entry.name.includes('chart') || entry.name.includes('d3') || entry.transferSize > 100000) {
      this.recordEvent({
        type: 'dashboard_load',
        name: 'large_resource_load',
        duration: entry.duration,
        severity: entry.duration > 2000 ? 'high' : 'medium',
        metadata: {
          url: entry.name,
          size: entry.transferSize,
          type: entry.initiatorType,
        },
      });
    }
  }

  private processPaintEntry(entry: PerformanceEntry): void {
    this.recordEvent({
      type: 'dashboard_load',
      name: entry.name,
      duration: entry.startTime,
      severity: 'low',
    });
  }

  private initializeAdminSpecificMonitoring(): void {
    // Monitor chart rendering performance
    this.monitorChartPerformance();
    
    // Monitor data table performance
    this.monitorDataTablePerformance();
    
    // Monitor real-time data updates
    this.monitorRealTimeData();
  }

  private monitorChartPerformance(): void {
    // Hook into common chart libraries
    const originalD3Select = window.d3?.select;
    if (originalD3Select) {
      window.d3.select = function(...args: any[]) {
        const startTime = performance.now();
        const result = originalD3Select.apply(this, args);
        const duration = performance.now() - startTime;
        
        if (duration > 100) { // Only log slow chart operations
          performanceMonitor.recordEvent({
            type: 'chart_render',
            name: 'd3_select',
            duration,
            severity: duration > 500 ? 'high' : 'medium',
          });
        }
        
        return result;
      };
    }
  }

  private monitorDataTablePerformance(): void {
    // Monitor DOM mutations for large table updates
    if ('MutationObserver' in window) {
      const observer = new MutationObserver((mutations) => {
        const largeMutations = mutations.filter(m => 
          m.type === 'childList' && m.addedNodes.length > 10
        );
        
        if (largeMutations.length > 0) {
          this.recordEvent({
            type: 'data_fetch',
            name: 'large_table_update',
            metadata: {
              mutationCount: largeMutations.length,
              nodesAdded: largeMutations.reduce((sum, m) => sum + m.addedNodes.length, 0),
            },
            severity: 'medium',
          });
        }
      });

      observer.observe(document.body, {
        childList: true,
        subtree: true,
      });
    }
  }

  private monitorRealTimeData(): void {
    let lastUpdateTime = Date.now();
    
    // Monitor real-time data update frequency
    const originalSetInterval = window.setInterval;
    window.setInterval = function(callback: Function, delay: number, ...args: any[]) {
      const wrappedCallback = function() {
        const now = Date.now();
        const timeSinceLastUpdate = now - lastUpdateTime;
        
        if (timeSinceLastUpdate > delay * 2) {
          performanceMonitor.recordEvent({
            type: 'system_event',
            name: 'realtime_data_lag',
            duration: timeSinceLastUpdate,
            severity: timeSinceLastUpdate > delay * 3 ? 'high' : 'medium',
            metadata: { expectedDelay: delay, actualDelay: timeSinceLastUpdate },
          });
        }
        
        lastUpdateTime = now;
        return callback.apply(this, args);
      };
      
      return originalSetInterval.call(this, wrappedCallback, delay);
    };
  }

  private initializeRealTimeConnection(): void {
    // Connect to admin analytics WebSocket for real-time metrics
    const wsUrl = process.env.REACT_APP_ADMIN_WS_ENDPOINT || 'ws://localhost:8080/admin/analytics';
    
    try {
      this.realTimeSocket = new WebSocket(wsUrl);
      
      this.realTimeSocket.onopen = () => {
        this.recordEvent({
          type: 'system_event',
          name: 'websocket_connected',
          severity: 'low',
        });
      };

      this.realTimeSocket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.updateRealTimeMetrics(data);
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      };

      this.realTimeSocket.onerror = (error) => {
        this.recordEvent({
          type: 'error',
          name: 'websocket_error',
          severity: 'high',
          metadata: { error: error.toString() },
        });
      };

      this.realTimeSocket.onclose = () => {
        this.recordEvent({
          type: 'system_event',
          name: 'websocket_disconnected',
          severity: 'medium',
        });
        
        // Attempt to reconnect after 5 seconds
        setTimeout(() => this.initializeRealTimeConnection(), 5000);
      };
    } catch (error) {
      console.error('Failed to initialize WebSocket connection:', error);
    }
  }

  private updateRealTimeMetrics(data: any): void {
    this.realTimeMetrics = { ...this.realTimeMetrics, ...data };
    
    // Check for performance alerts
    if (data.responseTime > 2000) {
      this.recordEvent({
        type: 'system_event',
        name: 'high_response_time',
        duration: data.responseTime,
        severity: 'high',
      });
    }

    if (data.errorRate > 0.05) { // 5% error rate
      this.recordEvent({
        type: 'system_event',
        name: 'high_error_rate',
        metadata: { errorRate: data.errorRate },
        severity: 'critical',
      });
    }
  }

  private initializeSystemMonitoring(): void {
    // Monitor system resources
    if ('memory' in performance) {
      setInterval(() => {
        const memInfo = (performance as any).memory;
        const memoryUsage = memInfo.usedJSHeapSize / memInfo.jsHeapSizeLimit;
        
        this.metrics.memoryUsage = memInfo.usedJSHeapSize;
        
        if (memoryUsage > 0.8) {
          this.recordEvent({
            type: 'system_event',
            name: 'high_memory_usage',
            metadata: {
              usage: memoryUsage,
              used: memInfo.usedJSHeapSize,
              limit: memInfo.jsHeapSizeLimit,
            },
            severity: memoryUsage > 0.9 ? 'critical' : 'high',
          });
        }
      }, 30000);
    }

    // Monitor network performance
    if ('connection' in navigator) {
      const connection = (navigator as any).connection;
      
      this.recordEvent({
        type: 'system_event',
        name: 'network_info',
        metadata: {
          effectiveType: connection.effectiveType,
          downlink: connection.downlink,
          rtt: connection.rtt,
        },
        severity: 'low',
      });

      connection.addEventListener('change', () => {
        this.recordEvent({
          type: 'system_event',
          name: 'network_change',
          metadata: {
            effectiveType: connection.effectiveType,
            downlink: connection.downlink,
            rtt: connection.rtt,
          },
          severity: 'medium',
        });
      });
    }
  }

  private initializePerformanceAlerts(): void {
    // Set up performance thresholds and alerts
    const checkPerformanceThresholds = () => {
      const currentMetrics = this.getMetrics();
      
      // Check dashboard load time
      if (currentMetrics.dashboardLoadTime && currentMetrics.dashboardLoadTime > 5000) {
        this.triggerPerformanceAlert('slow_dashboard_load', {
          loadTime: currentMetrics.dashboardLoadTime,
          threshold: 5000,
        });
      }

      // Check memory usage
      if (currentMetrics.memoryUsage && 'memory' in performance) {
        const memInfo = (performance as any).memory;
        const memoryUsage = currentMetrics.memoryUsage / memInfo.jsHeapSizeLimit;
        
        if (memoryUsage > 0.85) {
          this.triggerPerformanceAlert('high_memory_usage', {
            usage: memoryUsage,
            threshold: 0.85,
          });
        }
      }
    };

    setInterval(checkPerformanceThresholds, 60000); // Check every minute
  }

  private triggerPerformanceAlert(alertType: string, metadata: any): void {
    if (this.performanceAlerts.has(alertType)) {
      return; // Already triggered, avoid spam
    }

    this.performanceAlerts.add(alertType);
    
    this.recordEvent({
      type: 'system_event',
      name: `performance_alert_${alertType}`,
      metadata,
      severity: 'critical',
    });

    // Clear alert after 5 minutes
    setTimeout(() => {
      this.performanceAlerts.delete(alertType);
    }, 300000);
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

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        this.flush(true);
      }
    });
  }

  // Public API methods
  public setAdminId(adminId: string): void {
    this.adminId = adminId;
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

    const severity = duration > 1000 ? 'high' : duration > 500 ? 'medium' : 'low';

    this.recordEvent({
      type: 'user_action',
      name: `timer_${name}`,
      duration,
      severity,
    });

    return duration;
  }

  public recordEvent(event: Partial<AdminPerformanceEvent>): void {
    const fullEvent: AdminPerformanceEvent = {
      type: event.type || 'user_action',
      name: event.name || 'unknown',
      duration: event.duration,
      metadata: event.metadata,
      timestamp: Date.now(),
      sessionId: this.sessionId,
      adminId: this.adminId,
      url: window.location.href,
      userAgent: navigator.userAgent,
      severity: event.severity || 'low',
    };

    this.events.push(fullEvent);

    // Auto-flush if buffer is full
    if (this.events.length >= this.bufferSize) {
      this.flush();
    }
  }

  public recordChartRender(chartType: string, duration: number, dataPoints: number): void {
    this.recordEvent({
      type: 'chart_render',
      name: `chart_render_${chartType}`,
      duration,
      severity: duration > 1000 ? 'high' : 'low',
      metadata: {
        chartType,
        dataPoints,
        renderRate: dataPoints / duration,
      },
    });
  }

  public recordReportGeneration(reportType: string, duration: number, recordCount: number): void {
    this.recordEvent({
      type: 'data_fetch',
      name: 'report_generation',
      duration,
      severity: duration > 10000 ? 'critical' : duration > 5000 ? 'high' : 'medium',
      metadata: {
        reportType,
        recordCount,
        processingRate: recordCount / duration,
      },
    });
  }

  public recordAdminAction(actionName: string, duration?: number, metadata?: Record<string, any>): void {
    this.recordEvent({
      type: 'user_action',
      name: actionName,
      duration,
      metadata,
      severity: duration && duration > 2000 ? 'high' : 'low',
    });
  }

  public getRealTimeMetrics(): RealTimeMetrics {
    return { ...this.realTimeMetrics };
  }

  public getMetrics(): AdminPerformanceMetrics {
    return { ...this.metrics };
  }

  public getEvents(): AdminPerformanceEvent[] {
    return [...this.events];
  }

  private async flush(sync: boolean = false): Promise<void> {
    if (this.events.length === 0) return;

    const payload = {
      sessionId: this.sessionId,
      adminId: this.adminId,
      metrics: this.metrics,
      events: this.events,
      realTimeMetrics: this.realTimeMetrics,
      timestamp: Date.now(),
      url: window.location.href,
    };

    this.events = [];

    try {
      if (sync && 'sendBeacon' in navigator) {
        navigator.sendBeacon(this.analyticsEndpoint, JSON.stringify(payload));
      } else {
        await fetch(this.analyticsEndpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        }).catch((error) => {
          console.error('Failed to send admin performance data:', error);
        });
      }
    } catch (error) {
      console.error('Failed to send admin performance data:', error);
    }
  }

  public destroy(): void {
    this.observer?.disconnect();
    this.realTimeSocket?.close();
    this.flush(true);
  }
}

// Create and export singleton instance
export const performanceMonitor = new AdminPerformanceMonitor();

// React Hook for admin components
export function useAdminPerformanceMonitor() {
  return {
    startTimer: performanceMonitor.startTimer.bind(performanceMonitor),
    endTimer: performanceMonitor.endTimer.bind(performanceMonitor),
    recordEvent: performanceMonitor.recordEvent.bind(performanceMonitor),
    recordChartRender: performanceMonitor.recordChartRender.bind(performanceMonitor),
    recordReportGeneration: performanceMonitor.recordReportGeneration.bind(performanceMonitor),
    recordAdminAction: performanceMonitor.recordAdminAction.bind(performanceMonitor),
    getRealTimeMetrics: performanceMonitor.getRealTimeMetrics.bind(performanceMonitor),
    getMetrics: performanceMonitor.getMetrics.bind(performanceMonitor),
  };
}

export default performanceMonitor;