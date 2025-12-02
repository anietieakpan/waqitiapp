/**
 * Production-grade monitoring and analytics service
 */
import { apiClient } from './apiClient';

export interface PerformanceMetrics {
  pageLoadTime: number;
  domContentLoadedTime: number;
  firstContentfulPaint: number;
  largestContentfulPaint: number;
  cumulativeLayoutShift: number;
  firstInputDelay: number;
  navigationTiming: PerformanceTiming;
  resourceTiming: PerformanceResourceTiming[];
}

export interface UserInteractionEvent {
  type: 'click' | 'scroll' | 'input' | 'navigation' | 'error';
  element?: string;
  page: string;
  timestamp: number;
  userId?: string;
  sessionId: string;
  metadata?: Record<string, any>;
}

export interface ErrorEvent {
  message: string;
  stack?: string;
  filename?: string;
  lineno?: number;
  colno?: number;
  userId?: string;
  sessionId: string;
  page: string;
  timestamp: number;
  userAgent: string;
  url: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
}

export interface AnalyticsEvent {
  name: string;
  category: string;
  properties: Record<string, any>;
  userId?: string;
  sessionId: string;
  timestamp: number;
}

class MonitoringService {
  private sessionId: string;
  private userId?: string;
  private performanceObserver?: PerformanceObserver;
  private eventQueue: Array<UserInteractionEvent | ErrorEvent | AnalyticsEvent> = [];
  private flushInterval: number;
  private isEnabled: boolean;

  constructor() {
    this.sessionId = this.generateSessionId();
    this.isEnabled = process.env.NODE_ENV === 'production' || 
                     import.meta.env.VITE_ENABLE_MONITORING === 'true';
    this.flushInterval = window.setInterval(() => this.flushEvents(), 30000); // 30 seconds

    if (this.isEnabled) {
      this.initialize();
    }
  }

  private initialize(): void {
    this.setupPerformanceMonitoring();
    this.setupErrorTracking();
    this.setupUserInteractionTracking();
    this.setupPageVisibilityTracking();
  }

  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  public setUserId(userId: string): void {
    this.userId = userId;
  }

  private setupPerformanceMonitoring(): void {
    // Web Vitals monitoring
    if ('PerformanceObserver' in window) {
      this.performanceObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.handlePerformanceEntry(entry);
        }
      });

      try {
        this.performanceObserver.observe({ entryTypes: ['navigation', 'paint', 'measure'] });
        this.performanceObserver.observe({ entryTypes: ['largest-contentful-paint'], buffered: true });
        this.performanceObserver.observe({ entryTypes: ['first-input'], buffered: true });
        this.performanceObserver.observe({ entryTypes: ['layout-shift'], buffered: true });
      } catch (error) {
        console.warn('Performance Observer not fully supported:', error);
      }
    }

    // Report initial page load metrics
    window.addEventListener('load', () => {
      setTimeout(() => this.reportPageLoadMetrics(), 0);
    });
  }

  private handlePerformanceEntry(entry: PerformanceEntry): void {
    switch (entry.entryType) {
      case 'largest-contentful-paint':
        this.trackEvent('performance_metric', 'web_vitals', {
          metric: 'lcp',
          value: entry.startTime,
          rating: this.getLCPRating(entry.startTime),
        });
        break;
      
      case 'first-input':
        const fidEntry = entry as PerformanceEventTiming;
        this.trackEvent('performance_metric', 'web_vitals', {
          metric: 'fid',
          value: fidEntry.processingStart - fidEntry.startTime,
          rating: this.getFIDRating(fidEntry.processingStart - fidEntry.startTime),
        });
        break;
      
      case 'layout-shift':
        const clsEntry = entry as LayoutShift;
        if (!clsEntry.hadRecentInput) {
          this.trackEvent('performance_metric', 'web_vitals', {
            metric: 'cls',
            value: clsEntry.value,
            rating: this.getCLSRating(clsEntry.value),
          });
        }
        break;
    }
  }

  private getLCPRating(value: number): 'good' | 'needs-improvement' | 'poor' {
    return value <= 2500 ? 'good' : value <= 4000 ? 'needs-improvement' : 'poor';
  }

  private getFIDRating(value: number): 'good' | 'needs-improvement' | 'poor' {
    return value <= 100 ? 'good' : value <= 300 ? 'needs-improvement' : 'poor';
  }

  private getCLSRating(value: number): 'good' | 'needs-improvement' | 'poor' {
    return value <= 0.1 ? 'good' : value <= 0.25 ? 'needs-improvement' : 'poor';
  }

  private reportPageLoadMetrics(): void {
    if (!('performance' in window)) return;

    const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
    const paint = performance.getEntriesByType('paint');
    
    const metrics = {
      pageLoadTime: navigation.loadEventEnd - navigation.fetchStart,
      domContentLoadedTime: navigation.domContentLoadedEventEnd - navigation.fetchStart,
      firstContentfulPaint: paint.find(p => p.name === 'first-contentful-paint')?.startTime || 0,
      dnsTime: navigation.domainLookupEnd - navigation.domainLookupStart,
      tcpTime: navigation.connectEnd - navigation.connectStart,
      requestTime: navigation.responseEnd - navigation.requestStart,
      responseTime: navigation.responseEnd - navigation.responseStart,
      domProcessingTime: navigation.domContentLoadedEventStart - navigation.responseEnd,
      resourceCount: performance.getEntriesByType('resource').length,
    };

    this.trackEvent('page_performance', 'navigation', metrics);
  }

  private setupErrorTracking(): void {
    // Global error handler
    window.addEventListener('error', (event) => {
      this.trackError({
        message: event.message,
        stack: event.error?.stack,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
        userId: this.userId,
        sessionId: this.sessionId,
        page: window.location.pathname,
        timestamp: Date.now(),
        userAgent: navigator.userAgent,
        url: window.location.href,
        severity: 'high',
      });
    });

    // Unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
      this.trackError({
        message: event.reason?.message || 'Unhandled Promise Rejection',
        stack: event.reason?.stack,
        userId: this.userId,
        sessionId: this.sessionId,
        page: window.location.pathname,
        timestamp: Date.now(),
        userAgent: navigator.userAgent,
        url: window.location.href,
        severity: 'medium',
      });
    });

    // Resource loading errors
    window.addEventListener('error', (event) => {
      if (event.target !== window && event.target) {
        const target = event.target as HTMLElement;
        this.trackError({
          message: `Resource failed to load: ${target.tagName}`,
          filename: (target as any).src || (target as any).href,
          userId: this.userId,
          sessionId: this.sessionId,
          page: window.location.pathname,
          timestamp: Date.now(),
          userAgent: navigator.userAgent,
          url: window.location.href,
          severity: 'low',
        });
      }
    }, true);
  }

  private setupUserInteractionTracking(): void {
    // Click tracking
    document.addEventListener('click', (event) => {
      const target = event.target as HTMLElement;
      const element = this.getElementIdentifier(target);
      
      this.trackInteraction({
        type: 'click',
        element,
        page: window.location.pathname,
        timestamp: Date.now(),
        userId: this.userId,
        sessionId: this.sessionId,
        metadata: {
          x: event.clientX,
          y: event.clientY,
          button: event.button,
        },
      });
    });

    // Form submission tracking
    document.addEventListener('submit', (event) => {
      const form = event.target as HTMLFormElement;
      const formId = form.id || form.className || 'unknown';
      
      this.trackInteraction({
        type: 'input',
        element: `form[${formId}]`,
        page: window.location.pathname,
        timestamp: Date.now(),
        userId: this.userId,
        sessionId: this.sessionId,
        metadata: {
          action: form.action,
          method: form.method,
        },
      });
    });

    // Scroll tracking (throttled)
    let scrollTimer: number;
    document.addEventListener('scroll', () => {
      clearTimeout(scrollTimer);
      scrollTimer = window.setTimeout(() => {
        this.trackInteraction({
          type: 'scroll',
          page: window.location.pathname,
          timestamp: Date.now(),
          userId: this.userId,
          sessionId: this.sessionId,
          metadata: {
            scrollY: window.scrollY,
            scrollX: window.scrollX,
            scrollHeight: document.documentElement.scrollHeight,
            clientHeight: window.innerHeight,
          },
        });
      }, 1000);
    });

    // Page navigation tracking
    window.addEventListener('popstate', () => {
      this.trackInteraction({
        type: 'navigation',
        page: window.location.pathname,
        timestamp: Date.now(),
        userId: this.userId,
        sessionId: this.sessionId,
        metadata: {
          type: 'popstate',
          url: window.location.href,
        },
      });
    });
  }

  private setupPageVisibilityTracking(): void {
    let visibilityStart = Date.now();

    const handleVisibilityChange = () => {
      const now = Date.now();
      
      if (document.hidden) {
        // Page became hidden
        const visibleTime = now - visibilityStart;
        this.trackEvent('page_visibility', 'engagement', {
          action: 'hidden',
          visibleTime,
          page: window.location.pathname,
        });
      } else {
        // Page became visible
        visibilityStart = now;
        this.trackEvent('page_visibility', 'engagement', {
          action: 'visible',
          page: window.location.pathname,
        });
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Track session end
    window.addEventListener('beforeunload', () => {
      const sessionDuration = Date.now() - parseInt(this.sessionId.split('_')[1]);
      this.trackEvent('session', 'engagement', {
        action: 'end',
        duration: sessionDuration,
        page: window.location.pathname,
      });
      this.flushEvents(); // Send remaining events
    });
  }

  private getElementIdentifier(element: HTMLElement): string {
    if (element.id) return `#${element.id}`;
    if (element.className) return `.${element.className.split(' ')[0]}`;
    return element.tagName.toLowerCase();
  }

  public trackEvent(name: string, category: string, properties: Record<string, any> = {}): void {
    if (!this.isEnabled) return;

    const event: AnalyticsEvent = {
      name,
      category,
      properties: {
        ...properties,
        page: window.location.pathname,
        referrer: document.referrer,
        timestamp: Date.now(),
      },
      userId: this.userId,
      sessionId: this.sessionId,
      timestamp: Date.now(),
    };

    this.eventQueue.push(event);
    
    // Flush immediately for critical events
    if (['error', 'performance_critical'].includes(category)) {
      this.flushEvents();
    }
  }

  public trackInteraction(interaction: Omit<UserInteractionEvent, 'sessionId'>): void {
    if (!this.isEnabled) return;

    this.eventQueue.push({
      ...interaction,
      sessionId: this.sessionId,
    });
  }

  public trackError(error: ErrorEvent): void {
    if (!this.isEnabled) return;

    console.error('Application Error:', error);
    this.eventQueue.push(error);
    
    // Flush immediately for high/critical severity errors
    if (['high', 'critical'].includes(error.severity)) {
      this.flushEvents();
    }
  }

  private async flushEvents(): Promise<void> {
    if (this.eventQueue.length === 0) return;

    const events = [...this.eventQueue];
    this.eventQueue = [];

    try {
      await apiClient.post('/analytics/events', {
        events,
        sessionId: this.sessionId,
        userId: this.userId,
      });
    } catch (error) {
      console.warn('Failed to send analytics events:', error);
      // Re-queue events on failure (but limit to prevent infinite growth)
      if (this.eventQueue.length < 1000) {
        this.eventQueue.unshift(...events);
      }
    }
  }

  public async getAnalytics(timeRange: string = '24h'): Promise<{
    pageViews: number;
    uniqueUsers: number;
    averageSessionDuration: number;
    bounceRate: number;
    topPages: Array<{ page: string; views: number }>;
    errorRate: number;
    performanceScore: number;
  }> {
    try {
      const response = await apiClient.get(`/analytics/dashboard?timeRange=${timeRange}`);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch analytics:', error);
      throw error;
    }
  }

  public destroy(): void {
    if (this.performanceObserver) {
      this.performanceObserver.disconnect();
    }
    
    if (this.flushInterval) {
      clearInterval(this.flushInterval);
    }
    
    this.flushEvents(); // Send remaining events
  }
}

export default new MonitoringService();