/**
 * API Request/Response Interceptor
 * Provides comprehensive error handling, retry logic, and monitoring
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';
import { Logger } from './LoggingService';
import * as Sentry from '@sentry/react-native';

export interface RequestConfig extends RequestInit {
  url: string;
  timeout?: number;
  retryCount?: number;
  retryDelay?: number;
  skipAuth?: boolean;
  correlationId?: string;
  metadata?: Record<string, unknown>;
}

export interface InterceptorConfig {
  baseURL: string;
  timeout: number;
  retryCount: number;
  retryDelay: number;
  enableLogging: boolean;
  enableMetrics: boolean;
  enableCaching: boolean;
  enableOfflineQueue: boolean;
  onRequestError?: (error: ApiError) => void;
  onResponseError?: (error: ApiError) => void;
  onNetworkError?: (error: NetworkError) => void;
}

export interface ApiError extends Error {
  code: string;
  status?: number;
  response?: Response;
  request?: RequestConfig;
  correlationId: string;
  timestamp: Date;
  retryCount: number;
  networkError?: boolean;
}

export interface NetworkError extends ApiError {
  networkType?: string;
  isConnected: boolean;
  isInternetReachable: boolean;
}

export interface ApiResponse<T = any> {
  data: T;
  status: number;
  headers: Headers;
  correlationId: string;
  duration: number;
}

interface QueuedRequest {
  id: string;
  config: RequestConfig;
  timestamp: Date;
  retryCount: number;
}

class ApiInterceptor {
  private static instance: ApiInterceptor;
  private config: InterceptorConfig;
  private requestQueue: QueuedRequest[] = [];
  private isOnline: boolean = true;
  private authToken: string | null = null;
  private refreshToken: string | null = null;
  private isRefreshingToken: boolean = false;
  private refreshPromise: Promise<string> | null = null;
  private requestMetrics: Map<string, number> = new Map();
  private cache: Map<string, { data: any; timestamp: number }> = new Map();
  private cacheExpiry: number = 5 * 60 * 1000; // 5 minutes
  private circuitBreaker: Map<string, CircuitBreakerState> = new Map();

  private readonly DEFAULT_CONFIG: InterceptorConfig = {
    baseURL: process.env.REACT_APP_API_URL || 'https://api.waqiti.com',
    timeout: 30000,
    retryCount: 3,
    retryDelay: 1000,
    enableLogging: true,
    enableMetrics: true,
    enableCaching: true,
    enableOfflineQueue: true,
  };

  private constructor() {
    this.config = this.DEFAULT_CONFIG;
    this.setupNetworkListener();
    this.loadAuthTokens();
  }

  public static getInstance(): ApiInterceptor {
    if (!ApiInterceptor.instance) {
      ApiInterceptor.instance = new ApiInterceptor();
    }
    return ApiInterceptor.instance;
  }

  public configure(config: Partial<InterceptorConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Main request method with interceptors
   */
  public async request<T = any>(config: RequestConfig): Promise<ApiResponse<T>> {
    const startTime = performance.now();
    const correlationId = config.correlationId || this.generateCorrelationId();
    
    // Check circuit breaker
    const endpoint = new URL(config.url, this.config.baseURL).pathname;
    if (this.isCircuitOpen(endpoint)) {
      throw this.createApiError('Circuit breaker is open', 'CIRCUIT_OPEN', 503, config, correlationId);
    }

    // Check cache for GET requests
    if (config.method === 'GET' && this.config.enableCaching) {
      const cached = this.getFromCache(config.url);
      if (cached) {
        Logger.debug('Cache hit', { url: config.url, correlationId });
        return {
          data: cached,
          status: 200,
          headers: new Headers(),
          correlationId,
          duration: performance.now() - startTime,
        };
      }
    }

    // Check network status
    if (!this.isOnline && this.config.enableOfflineQueue) {
      if (this.isIdempotent(config.method)) {
        return this.queueRequest(config);
      } else {
        throw this.createNetworkError(config, correlationId);
      }
    }

    try {
      // Prepare request
      const preparedConfig = await this.prepareRequest(config, correlationId);
      
      // Execute request with timeout
      const response = await this.executeWithTimeout(preparedConfig, correlationId);
      
      // Process response
      const processedResponse = await this.processResponse<T>(response, preparedConfig, correlationId, startTime);
      
      // Update circuit breaker
      this.recordSuccess(endpoint);
      
      // Cache successful GET requests
      if (config.method === 'GET' && this.config.enableCaching) {
        this.saveToCache(config.url, processedResponse.data);
      }
      
      return processedResponse;
      
    } catch (error) {
      // Handle error with retry logic
      return this.handleRequestError<T>(error as Error, config, correlationId, startTime);
    }
  }

  /**
   * Prepare request with auth and headers
   */
  private async prepareRequest(config: RequestConfig, correlationId: string): Promise<RequestConfig> {
    const url = new URL(config.url, this.config.baseURL).toString();
    
    // Add auth token if needed
    const headers = new Headers(config.headers);
    if (!config.skipAuth && this.authToken) {
      headers.set('Authorization', `Bearer ${this.authToken}`);
    }
    
    // Add standard headers
    headers.set('X-Correlation-Id', correlationId);
    headers.set('X-Client-Version', process.env.REACT_APP_VERSION || '1.0.0');
    headers.set('X-Platform', 'mobile');
    
    if (!headers.has('Content-Type') && config.body) {
      headers.set('Content-Type', 'application/json');
    }

    // Log request
    if (this.config.enableLogging) {
      Logger.debug('API Request', {
        method: config.method || 'GET',
        url,
        correlationId,
        headers: this.sanitizeHeaders(headers),
      });
    }

    return {
      ...config,
      url,
      headers,
    };
  }

  /**
   * Execute request with timeout
   */
  private async executeWithTimeout(config: RequestConfig, correlationId: string): Promise<Response> {
    const timeout = config.timeout || this.config.timeout;
    const controller = new AbortController();
    
    const timeoutId = setTimeout(() => {
      controller.abort();
    }, timeout);

    try {
      const response = await fetch(config.url, {
        ...config,
        signal: controller.signal,
      });
      
      clearTimeout(timeoutId);
      return response;
      
    } catch (error) {
      clearTimeout(timeoutId);
      
      if ((error as Error).name === 'AbortError') {
        throw this.createApiError('Request timeout', 'TIMEOUT', 408, config, correlationId);
      }
      throw error;
    }
  }

  /**
   * Process response with error handling
   */
  private async processResponse<T>(
    response: Response,
    config: RequestConfig,
    correlationId: string,
    startTime: number
  ): Promise<ApiResponse<T>> {
    const duration = performance.now() - startTime;
    
    // Log response
    if (this.config.enableLogging) {
      Logger.debug('API Response', {
        method: config.method || 'GET',
        url: config.url,
        status: response.status,
        duration: `${duration.toFixed(2)}ms`,
        correlationId,
      });
    }

    // Record metrics
    if (this.config.enableMetrics) {
      this.recordMetrics(config.url, duration, response.status);
    }

    // Handle auth errors
    if (response.status === 401 && !config.skipAuth) {
      return this.handleAuthError(config, correlationId, startTime);
    }

    // Handle error responses
    if (!response.ok) {
      const errorData = await this.parseErrorResponse(response);
      const error = this.createApiError(
        errorData.message || response.statusText,
        errorData.code || 'API_ERROR',
        response.status,
        config,
        correlationId
      );
      
      if (this.config.onResponseError) {
        this.config.onResponseError(error);
      }
      
      throw error;
    }

    // Parse successful response
    const data = await this.parseResponse<T>(response);
    
    return {
      data,
      status: response.status,
      headers: response.headers,
      correlationId,
      duration,
    };
  }

  /**
   * Handle request errors with retry logic
   */
  private async handleRequestError<T>(
    error: Error,
    config: RequestConfig,
    correlationId: string,
    startTime: number,
    retryCount: number = 0
  ): Promise<ApiResponse<T>> {
    const endpoint = new URL(config.url, this.config.baseURL).pathname;
    
    // Record failure for circuit breaker
    this.recordFailure(endpoint);
    
    // Log error
    Logger.error('API Request Failed', error, {
      url: config.url,
      correlationId,
      retryCount,
    });

    // Check if retryable
    if (this.isRetryable(error, config, retryCount)) {
      const delay = this.getRetryDelay(retryCount);
      Logger.info('Retrying request', {
        url: config.url,
        retryCount: retryCount + 1,
        delay: `${delay}ms`,
        correlationId,
      });
      
      await this.sleep(delay);
      
      return this.request<T>({
        ...config,
        retryCount: retryCount + 1,
      });
    }

    // Send to Sentry in production
    if (!__DEV__) {
      Sentry.captureException(error, {
        contexts: {
          api: {
            url: config.url,
            method: config.method,
            correlationId,
            retryCount,
          },
        },
      });
    }

    // Call error handler
    if (this.config.onRequestError) {
      this.config.onRequestError(error as ApiError);
    }

    throw error;
  }

  /**
   * Handle 401 authentication errors
   */
  private async handleAuthError<T>(
    config: RequestConfig,
    correlationId: string,
    startTime: number
  ): Promise<ApiResponse<T>> {
    if (this.isRefreshingToken) {
      // Wait for token refresh to complete
      await this.refreshPromise;
      return this.request<T>(config);
    }

    if (!this.refreshToken) {
      throw this.createApiError('Authentication required', 'AUTH_REQUIRED', 401, config, correlationId);
    }

    try {
      this.isRefreshingToken = true;
      this.refreshPromise = this.refreshAuthToken();
      
      const newToken = await this.refreshPromise;
      this.authToken = newToken;
      await AsyncStorage.setItem('authToken', newToken);
      
      // Retry original request
      return this.request<T>(config);
      
    } catch (refreshError) {
      // Refresh failed, clear tokens
      this.clearAuthTokens();
      throw this.createApiError('Authentication failed', 'AUTH_FAILED', 401, config, correlationId);
      
    } finally {
      this.isRefreshingToken = false;
      this.refreshPromise = null;
    }
  }

  /**
   * Refresh authentication token
   */
  private async refreshAuthToken(): Promise<string> {
    const response = await fetch(`${this.config.baseURL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        refreshToken: this.refreshToken,
      }),
    });

    if (!response.ok) {
      throw new Error('Failed to refresh token');
    }

    const data = await response.json();
    return data.accessToken;
  }

  /**
   * Queue request for offline execution
   */
  private async queueRequest<T>(config: RequestConfig): Promise<ApiResponse<T>> {
    const queuedRequest: QueuedRequest = {
      id: this.generateCorrelationId(),
      config,
      timestamp: new Date(),
      retryCount: 0,
    };

    this.requestQueue.push(queuedRequest);
    await this.saveQueueToStorage();

    Logger.info('Request queued for offline execution', {
      id: queuedRequest.id,
      url: config.url,
      method: config.method,
    });

    // Return optimistic response
    return {
      data: { queued: true, id: queuedRequest.id } as any,
      status: 202,
      headers: new Headers(),
      correlationId: queuedRequest.id,
      duration: 0,
    };
  }

  /**
   * Process queued requests when online
   */
  private async processQueuedRequests(): Promise<void> {
    if (this.requestQueue.length === 0) return;

    Logger.info('Processing queued requests', { count: this.requestQueue.length });

    const queue = [...this.requestQueue];
    this.requestQueue = [];

    for (const queuedRequest of queue) {
      try {
        await this.request(queuedRequest.config);
        Logger.info('Queued request processed', { id: queuedRequest.id });
      } catch (error) {
        Logger.error('Failed to process queued request', error, { id: queuedRequest.id });
        
        // Re-queue if still offline or retryable
        if (!this.isOnline || queuedRequest.retryCount < this.config.retryCount) {
          queuedRequest.retryCount++;
          this.requestQueue.push(queuedRequest);
        }
      }
    }

    await this.saveQueueToStorage();
  }

  // Circuit Breaker Implementation
  
  private isCircuitOpen(endpoint: string): boolean {
    const state = this.circuitBreaker.get(endpoint);
    if (!state) return false;
    
    if (state.state === 'OPEN') {
      // Check if cooldown period has passed
      if (Date.now() - state.lastFailure > state.cooldownPeriod) {
        state.state = 'HALF_OPEN';
        return false;
      }
      return true;
    }
    
    return false;
  }

  private recordSuccess(endpoint: string): void {
    const state = this.circuitBreaker.get(endpoint);
    if (!state) {
      this.circuitBreaker.set(endpoint, {
        state: 'CLOSED',
        failures: 0,
        lastFailure: 0,
        cooldownPeriod: 60000, // 1 minute
      });
      return;
    }
    
    if (state.state === 'HALF_OPEN') {
      state.state = 'CLOSED';
      state.failures = 0;
    }
  }

  private recordFailure(endpoint: string): void {
    let state = this.circuitBreaker.get(endpoint);
    if (!state) {
      state = {
        state: 'CLOSED',
        failures: 0,
        lastFailure: 0,
        cooldownPeriod: 60000,
      };
      this.circuitBreaker.set(endpoint, state);
    }
    
    state.failures++;
    state.lastFailure = Date.now();
    
    if (state.failures >= 5) {
      state.state = 'OPEN';
      Logger.warn('Circuit breaker opened', { endpoint, failures: state.failures });
    }
  }

  // Helper methods

  private setupNetworkListener(): void {
    NetInfo.addEventListener((state) => {
      const wasOffline = !this.isOnline;
      this.isOnline = state.isConnected || false;
      
      if (wasOffline && this.isOnline) {
        Logger.info('Network connection restored');
        this.processQueuedRequests();
      } else if (!this.isOnline) {
        Logger.warn('Network connection lost');
      }
    });
  }

  private async loadAuthTokens(): Promise<void> {
    try {
      const [authToken, refreshToken] = await Promise.all([
        AsyncStorage.getItem('authToken'),
        AsyncStorage.getItem('refreshToken'),
      ]);
      
      this.authToken = authToken;
      this.refreshToken = refreshToken;
    } catch (error) {
      Logger.error('Failed to load auth tokens', error);
    }
  }

  private async clearAuthTokens(): Promise<void> {
    this.authToken = null;
    this.refreshToken = null;
    await Promise.all([
      AsyncStorage.removeItem('authToken'),
      AsyncStorage.removeItem('refreshToken'),
    ]);
  }

  private async saveQueueToStorage(): Promise<void> {
    try {
      await AsyncStorage.setItem('requestQueue', JSON.stringify(this.requestQueue));
    } catch (error) {
      Logger.error('Failed to save request queue', error);
    }
  }

  private async loadQueueFromStorage(): Promise<void> {
    try {
      const queue = await AsyncStorage.getItem('requestQueue');
      if (queue) {
        this.requestQueue = JSON.parse(queue);
      }
    } catch (error) {
      Logger.error('Failed to load request queue', error);
    }
  }

  private getFromCache(key: string): any | null {
    const cached = this.cache.get(key);
    if (!cached) return null;
    
    if (Date.now() - cached.timestamp > this.cacheExpiry) {
      this.cache.delete(key);
      return null;
    }
    
    return cached.data;
  }

  private saveToCache(key: string, data: any): void {
    this.cache.set(key, {
      data,
      timestamp: Date.now(),
    });
    
    // Limit cache size
    if (this.cache.size > 100) {
      const firstKey = this.cache.keys().next().value;
      this.cache.delete(firstKey);
    }
  }

  private recordMetrics(url: string, duration: number, status: number): void {
    const endpoint = new URL(url, this.config.baseURL).pathname;
    const key = `${endpoint}_${status}`;
    
    const count = this.requestMetrics.get(key) || 0;
    this.requestMetrics.set(key, count + 1);
    
    // Log slow requests
    if (duration > 3000) {
      Logger.warn('Slow API request', {
        url,
        duration: `${duration.toFixed(2)}ms`,
        status,
      });
    }
  }

  private isRetryable(error: Error, config: RequestConfig, retryCount: number): boolean {
    const maxRetries = config.retryCount ?? this.config.retryCount;
    
    if (retryCount >= maxRetries) return false;
    
    // Don't retry non-idempotent methods unless explicitly configured
    if (!this.isIdempotent(config.method) && !config.retryCount) return false;
    
    const apiError = error as ApiError;
    
    // Retry on network errors
    if (apiError.networkError) return true;
    
    // Retry on specific status codes
    const retryableStatuses = [408, 429, 500, 502, 503, 504];
    if (apiError.status && retryableStatuses.includes(apiError.status)) return true;
    
    return false;
  }

  private isIdempotent(method?: string): boolean {
    const idempotentMethods = ['GET', 'HEAD', 'OPTIONS', 'PUT', 'DELETE'];
    return idempotentMethods.includes(method?.toUpperCase() || 'GET');
  }

  private getRetryDelay(retryCount: number): number {
    // Exponential backoff with jitter
    const baseDelay = this.config.retryDelay;
    const delay = baseDelay * Math.pow(2, retryCount);
    const jitter = Math.random() * 1000;
    return Math.min(delay + jitter, 30000); // Max 30 seconds
  }

  private async parseResponse<T>(response: Response): Promise<T> {
    const contentType = response.headers.get('content-type');
    
    if (contentType?.includes('application/json')) {
      return response.json();
    } else if (contentType?.includes('text/')) {
      return response.text() as any;
    } else {
      return response.blob() as any;
    }
  }

  private async parseErrorResponse(response: Response): Promise<any> {
    try {
      const contentType = response.headers.get('content-type');
      if (contentType?.includes('application/json')) {
        return await response.json();
      }
      return { message: await response.text() };
    } catch {
      return { message: response.statusText };
    }
  }

  private createApiError(
    message: string,
    code: string,
    status: number,
    request: RequestConfig,
    correlationId: string
  ): ApiError {
    const error = new Error(message) as ApiError;
    error.code = code;
    error.status = status;
    error.request = request;
    error.correlationId = correlationId;
    error.timestamp = new Date();
    error.retryCount = request.retryCount || 0;
    return error;
  }

  private createNetworkError(config: RequestConfig, correlationId: string): NetworkError {
    const error = new Error('No network connection') as NetworkError;
    error.code = 'NETWORK_ERROR';
    error.networkError = true;
    error.request = config;
    error.correlationId = correlationId;
    error.timestamp = new Date();
    error.retryCount = config.retryCount || 0;
    error.isConnected = false;
    error.isInternetReachable = false;
    
    if (this.config.onNetworkError) {
      this.config.onNetworkError(error);
    }
    
    return error;
  }

  private sanitizeHeaders(headers: Headers): Record<string, string> {
    const sanitized: Record<string, string> = {};
    headers.forEach((value, key) => {
      if (key.toLowerCase() === 'authorization') {
        sanitized[key] = '[REDACTED]';
      } else {
        sanitized[key] = value;
      }
    });
    return sanitized;
  }

  private generateCorrelationId(): string {
    return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

interface CircuitBreakerState {
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  failures: number;
  lastFailure: number;
  cooldownPeriod: number;
}

// Export singleton instance
export const apiInterceptor = ApiInterceptor.getInstance();

// Export convenience methods
export const api = {
  get: <T = any>(url: string, config?: Partial<RequestConfig>) =>
    apiInterceptor.request<T>({ ...config, url, method: 'GET' }),
  
  post: <T = any>(url: string, data?: any, config?: Partial<RequestConfig>) =>
    apiInterceptor.request<T>({ ...config, url, method: 'POST', body: JSON.stringify(data) }),
  
  put: <T = any>(url: string, data?: any, config?: Partial<RequestConfig>) =>
    apiInterceptor.request<T>({ ...config, url, method: 'PUT', body: JSON.stringify(data) }),
  
  patch: <T = any>(url: string, data?: any, config?: Partial<RequestConfig>) =>
    apiInterceptor.request<T>({ ...config, url, method: 'PATCH', body: JSON.stringify(data) }),
  
  delete: <T = any>(url: string, config?: Partial<RequestConfig>) =>
    apiInterceptor.request<T>({ ...config, url, method: 'DELETE' }),
};

export default apiInterceptor;