import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { getCsrfToken, requiresCsrfToken } from './csrf';

/**
 * SECURITY-HARDENED AXIOS INSTANCE
 *
 * ✅ COOKIE-BASED AUTHENTICATION (XSS Protection)
 * - Tokens stored in HttpOnly cookies (JavaScript cannot access)
 * - Protects against XSS token theft attacks
 * - Cookies automatically sent with every request
 *
 * ✅ CSRF PROTECTION
 * - CSRF tokens added to state-changing requests
 * - Double-submit cookie pattern
 *
 * MIGRATION FROM LOCALSTORAGE:
 * ❌ OLD (INSECURE): localStorage.getItem('accessToken')
 * ✅ NEW (SECURE): Cookies sent automatically via withCredentials
 *
 * @see Backend must set cookies: Set-Cookie: accessToken=xxx; HttpOnly; Secure; SameSite=Strict
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// Create axios instance with cookie-based authentication
const instance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000, // 30 second timeout
  withCredentials: true, // CRITICAL: Send cookies with every request
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

/**
 * REQUEST INTERCEPTOR
 * - Adds CSRF token for state-changing operations
 * - NO LONGER adds Authorization header (cookies handle auth)
 */
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add CSRF token for state-changing requests (POST, PUT, DELETE, PATCH)
    if (config.method && requiresCsrfToken(config.method)) {
      const csrfToken = getCsrfToken();
      if (csrfToken && config.headers) {
        config.headers['X-XSRF-TOKEN'] = csrfToken;
      } else {
        console.warn(
          `CSRF token not found for ${config.method?.toUpperCase()} ${config.url}. ` +
          'Ensure CSRF cookies are enabled on backend.'
        );
      }
    }

    // SECURITY: No longer adding Authorization header from localStorage
    // Access token cookie is sent automatically by browser
    // This prevents XSS attacks from stealing tokens

    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

/**
 * RESPONSE INTERCEPTOR
 * - Handles 401 Unauthorized with token refresh
 * - Token refresh via HttpOnly cookie (no localStorage)
 */
instance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Handle 401 Unauthorized - attempt token refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Call refresh endpoint
        // Backend reads refreshToken from HttpOnly cookie
        // Backend sets new accessToken cookie in response
        await axios.post(
          `${API_BASE_URL}/auth/refresh`,
          {}, // Empty body - refresh token sent via cookie
          {
            withCredentials: true, // Send refresh token cookie
          }
        );

        // SECURITY: No token handling in JavaScript
        // New access token cookie is automatically set by backend
        // Browser will include it in subsequent requests

        // Retry original request with new token (sent via cookie)
        return instance(originalRequest);
      } catch (refreshError) {
        // Refresh failed - redirect to login
        // Backend will clear cookies on logout endpoint
        console.error('Token refresh failed:', refreshError);

        // SECURITY: No localStorage.removeItem() needed
        // Tokens were never in localStorage

        // Redirect to login page
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default instance;
