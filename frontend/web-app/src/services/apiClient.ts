/**
 * Centralized API Client with comprehensive error handling and interceptors
 */
import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { toast } from 'react-hot-toast';

// API configuration
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const API_TIMEOUT = 30000; // 30 seconds

// Response interfaces
interface ErrorResponse {
  message: string;
  code?: string;
  details?: any;
  timestamp?: string;
}

interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

// Custom error class
export class ApiError extends Error {
  public status: number;
  public code?: string;
  public details?: any;

  constructor(message: string, status: number, code?: string, details?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

class ApiClient {
  private instance: AxiosInstance;
  private isRefreshing = false;
  private refreshPromise: Promise<void> | null = null;

  constructor() {
    this.instance = axios.create({
      baseURL: API_BASE_URL,
      timeout: API_TIMEOUT,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      // ✅ SECURITY FIX: Enable credentials to send HttpOnly cookies
      withCredentials: true,
    });

    this.setupInterceptors();
  }

  private setupInterceptors(): void {
    // Request interceptor
    this.instance.interceptors.request.use(
      (config) => {
        // ✅ SECURITY FIX: Token automatically sent via HttpOnly cookie
        // No need to manually add Authorization header
        // Browser automatically includes cookies with withCredentials: true
        //
        // ❌ REMOVED: const token = this.getStoredToken() - NO LONGER NEEDED
        // ❌ REMOVED: config.headers.Authorization = `Bearer ${token}` - COOKIES USED INSTEAD

        // Add request ID for tracking
        config.headers['X-Request-ID'] = this.generateRequestId();

        // Add client information
        config.headers['X-Client-Version'] = import.meta.env.VITE_APP_VERSION || '1.0.0';
        config.headers['X-Client-Type'] = 'web';

        // Log request in development
        if (import.meta.env.DEV) {
          console.log(`[API Request] ${config.method?.toUpperCase()} ${config.url}`, {
            headers: config.headers,
            data: config.data,
            params: config.params,
          });
        }

        return config;
      },
      (error) => {
        console.error('[API Request Error]', error);
        return Promise.reject(error);
      }
    );

    // Response interceptor
    this.instance.interceptors.response.use(
      (response) => {
        // Log response in development
        if (import.meta.env.DEV) {
          console.log(`[API Response] ${response.status} ${response.config.url}`, response.data);
        }

        return response;
      },
      async (error: AxiosError) => {
        const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

        // Handle network errors
        if (!error.response) {
          console.error('[API Network Error]', error);
          toast.error('Network error. Please check your connection.');
          return Promise.reject(new ApiError('Network error', 0, 'NETWORK_ERROR'));
        }

        const { status, data } = error.response;
        const errorData = data as ErrorResponse;

        // Handle token refresh for 401 errors
        if (status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          try {
            // ✅ SECURITY FIX: Token refresh via HttpOnly cookies
            await this.refreshToken();

            // Retry original request (new token automatically sent via HttpOnly cookie)
            // No need to manually set Authorization header
            return this.instance.request(originalRequest);
          } catch (refreshError) {
            // Refresh failed, redirect to login
            this.handleAuthError();
            return Promise.reject(refreshError);
          }
        }

        // Handle different error types
        const apiError = this.handleErrorResponse(status, errorData);
        
        // Log error in development
        if (import.meta.env.DEV) {
          console.error(`[API Error] ${status} ${error.config?.url}`, {
            error: apiError,
            response: error.response,
          });
        }

        return Promise.reject(apiError);
      }
    );
  }

  private handleErrorResponse(status: number, errorData: ErrorResponse): ApiError {
    const message = errorData.message || this.getDefaultErrorMessage(status);
    const code = errorData.code;
    const details = errorData.details;

    // Show user-friendly toast notifications
    switch (status) {
      case 400:
        toast.error(message || 'Invalid request. Please check your input.');
        break;
      case 401:
        // Don't show toast for auth errors as they're handled by auth flow
        break;
      case 403:
        toast.error('You don\'t have permission to perform this action.');
        break;
      case 404:
        toast.error('The requested resource was not found.');
        break;
      case 409:
        toast.error(message || 'Conflict. The resource already exists or is in use.');
        break;
      case 422:
        toast.error(message || 'Validation failed. Please check your input.');
        break;
      case 429:
        toast.error('Too many requests. Please wait a moment and try again.');
        break;
      case 500:
        toast.error('Server error. Our team has been notified.');
        break;
      case 503:
        toast.error('Service temporarily unavailable. Please try again later.');
        break;
      default:
        toast.error(message || 'An unexpected error occurred.');
    }

    return new ApiError(message, status, code, details);
  }

  private getDefaultErrorMessage(status: number): string {
    const messages: Record<number, string> = {
      400: 'Bad Request',
      401: 'Unauthorized',
      403: 'Forbidden',
      404: 'Not Found',
      409: 'Conflict',
      422: 'Unprocessable Entity',
      429: 'Too Many Requests',
      500: 'Internal Server Error',
      502: 'Bad Gateway',
      503: 'Service Unavailable',
      504: 'Gateway Timeout',
    };

    return messages[status] || 'Unknown Error';
  }

  private async refreshToken(): Promise<void> {
    if (this.isRefreshing && this.refreshPromise) {
      return this.refreshPromise;
    }

    this.isRefreshing = true;
    this.refreshPromise = this.performTokenRefresh();

    try {
      await this.refreshPromise;
    } finally {
      this.isRefreshing = false;
      this.refreshPromise = null;
    }
  }

  private async performTokenRefresh(): Promise<void> {
    // ✅ SECURITY FIX: Token refresh via HttpOnly cookies
    // Server reads refreshToken from HttpOnly cookie (automatically sent)
    // Server responds by setting new HttpOnly cookies
    // NO localStorage access needed - prevents XSS token theft (CWE-522, CWE-79)
    //
    // ❌ REMOVED: localStorage.getItem('refreshToken') - VULNERABLE TO XSS
    // ❌ REMOVED: localStorage.setItem('authToken', ...) - VULNERABLE TO XSS
    // ❌ REMOVED: localStorage.setItem('refreshToken', ...) - VULNERABLE TO XSS

    try {
      await axios.post(
        `${API_BASE_URL}/auth/refresh`,
        {},  // Empty body - refreshToken sent via HttpOnly cookie
        {
          withCredentials: true,  // Include HttpOnly cookies in request
        }
      );

      // Server automatically sets new HttpOnly cookies in response
      // No client-side token storage or return value needed
    } catch (error) {
      // Refresh failed, dispatch auth error event
      window.dispatchEvent(new CustomEvent('auth:error', {
        detail: { reason: 'token_refresh_failed' }
      }));

      throw new ApiError('Token refresh failed', 401, 'REFRESH_FAILED');
    }
  }

  private handleAuthError(): void {
    // ✅ SECURITY FIX: No local token storage to clear
    // HttpOnly cookies are cleared by server via logout endpoint
    // Client just needs to redirect and dispatch event

    // Dispatch custom event for auth error
    window.dispatchEvent(new CustomEvent('auth:error', {
      detail: { reason: 'authentication_failed' }
    }));

    // Redirect to login if not already there
    if (!window.location.pathname.includes('/login')) {
      window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
    }
  }

  // ✅ SECURITY FIX: Token utility methods removed
  // HttpOnly cookies are managed by the browser automatically
  //
  // ❌ REMOVED: private getStoredToken() - NO LONGER NEEDED
  // ❌ REMOVED: private clearStoredTokens() - COOKIES CLEARED BY SERVER

  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // HTTP Methods
  public async get<T>(url: string, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> {
    return this.instance.get<T>(url, config);
  }

  public async post<T>(
    url: string,
    data?: any,
    config?: AxiosRequestConfig
  ): Promise<AxiosResponse<T>> {
    return this.instance.post<T>(url, data, config);
  }

  public async put<T>(
    url: string,
    data?: any,
    config?: AxiosRequestConfig
  ): Promise<AxiosResponse<T>> {
    return this.instance.put<T>(url, data, config);
  }

  public async patch<T>(
    url: string,
    data?: any,
    config?: AxiosRequestConfig
  ): Promise<AxiosResponse<T>> {
    return this.instance.patch<T>(url, data, config);
  }

  public async delete<T>(url: string, config?: AxiosRequestConfig): Promise<AxiosResponse<T>> {
    return this.instance.delete<T>(url, config);
  }

  // ✅ SECURITY FIX: Auth utility methods updated for HttpOnly cookies

  /**
   * @deprecated Tokens are now stored in HttpOnly cookies by the backend
   * This method is kept for backward compatibility but does nothing
   */
  public setAuthToken(token: string): void {
    console.warn(
      '[SECURITY] setAuthToken() is deprecated. Tokens are now stored in HttpOnly cookies by the server. ' +
      'This prevents XSS token theft. If you see this warning, update your code to rely on cookie-based auth.'
    );
    // Do nothing - tokens managed by HttpOnly cookies
  }

  /**
   * Clear authentication by calling server logout endpoint
   * Server will clear HttpOnly cookies
   */
  public async clearAuth(): Promise<void> {
    try {
      // Call server logout to clear HttpOnly cookies
      await this.post('/auth/logout', {});
    } catch (error) {
      console.error('Logout API call failed:', error);
    }

    // Dispatch auth cleared event
    window.dispatchEvent(new CustomEvent('auth:cleared'));
  }

  /**
   * @deprecated Tokens are stored in HttpOnly cookies and not accessible to JavaScript
   * This method always returns null for security reasons
   */
  public getAuthToken(): string | null {
    console.warn(
      '[SECURITY] getAuthToken() is deprecated and always returns null. ' +
      'Tokens are stored in HttpOnly cookies and are not accessible to JavaScript for security. ' +
      'This prevents XSS token theft.'
    );
    return null;  // Tokens in HttpOnly cookies are not accessible to JS
  }

  // File upload with progress
  public async uploadFile<T>(
    url: string,
    file: File,
    onProgress?: (progress: number) => void,
    additionalData?: Record<string, any>
  ): Promise<AxiosResponse<T>> {
    const formData = new FormData();
    formData.append('file', file);
    
    if (additionalData) {
      Object.entries(additionalData).forEach(([key, value]) => {
        formData.append(key, value);
      });
    }

    return this.instance.post<T>(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(progress);
        }
      },
    });
  }

  // Health check
  public async healthCheck(): Promise<boolean> {
    try {
      await this.get('/health');
      return true;
    } catch {
      return false;
    }
  }
}

export const apiClient = new ApiClient();
export default apiClient;