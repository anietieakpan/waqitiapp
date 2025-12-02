import axios, { AxiosResponse, AxiosRequestConfig, AxiosError } from 'axios';

/**
 * SECURE API CLIENT
 *
 * ✅ COOKIE-BASED AUTHENTICATION (No localStorage tokens)
 * - HttpOnly cookies protect against XSS attacks
 * - Tokens automatically sent via withCredentials
 *
 * ✅ AUTOMATIC TOKEN REFRESH
 * - Intercepts 401 errors
 * - Refreshes token via HttpOnly cookie
 * - Retries failed request with new token
 */

const API_GATEWAY_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const apiClient = axios.create({
  baseURL: API_GATEWAY_URL,
  withCredentials: true, // CRITICAL: Send HttpOnly cookies
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * REQUEST INTERCEPTOR
 * NO LONGER adds Authorization header from localStorage
 * Cookies are sent automatically
 */
apiClient.interceptors.request.use(
  (config) => {
    // SECURITY: No token handling in JavaScript
    // Access token cookie sent automatically by browser
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * RESPONSE INTERCEPTOR
 * Handles 401 with automatic token refresh
 */
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Call refresh endpoint - refresh token sent via HttpOnly cookie
        await axios.post(
          `${API_GATEWAY_URL}/api/v1/auth/refresh`,
          {},
          { withCredentials: true }
        );

        // SECURITY: No token storage in JavaScript
        // New access token cookie set by backend

        // Retry original request
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh failed - redirect to login
        console.error('Token refresh failed:', refreshError);
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

interface ApiResponse<T> {
  data: T;
  message?: string;
  status: number;
}

class ApiClient {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<{ data: T }> {
    const response: AxiosResponse<ApiResponse<T>> = await apiClient.get(url, config);
    return { data: response.data.data || response.data as any };
  }

  async post<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<{ data: T }> {
    const response: AxiosResponse<ApiResponse<T>> = await apiClient.post(url, data, config);
    return { data: response.data.data || response.data as any };
  }

  async put<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<{ data: T }> {
    const response: AxiosResponse<ApiResponse<T>> = await apiClient.put(url, data, config);
    return { data: response.data.data || response.data as any };
  }

  async patch<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<{ data: T }> {
    const response: AxiosResponse<ApiResponse<T>> = await apiClient.patch(url, data, config);
    return { data: response.data.data || response.data as any };
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<{ data: T }> {
    const response: AxiosResponse<ApiResponse<T>> = await apiClient.delete(url, config);
    return { data: response.data.data || response.data as any };
  }
}

export default new ApiClient();
