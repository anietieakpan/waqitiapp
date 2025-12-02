/**
 * =====================================================================
 * Secure API Client for Admin Dashboard
 * =====================================================================
 * SECURITY FIX: Uses HttpOnly cookies instead of localStorage for tokens
 * - Prevents XSS token theft (CWE-79, CWE-522)
 * - PCI-DSS Requirement 6.5.7 compliance
 * - OWASP A03:2021 Injection prevention
 * =====================================================================
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';

// Create axios instance with default configuration
const apiClient: AxiosInstance = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || 'https://api.example.com',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // CRITICAL: Send HttpOnly cookies with every request
});

// SECURITY: Remove legacy tokens from localStorage
const migrateLegacyTokens = () => {
  const legacyKeys = ['adminToken', 'token', 'accessToken', 'refreshToken'];
  legacyKeys.forEach(key => {
    if (localStorage.getItem(key)) {
      console.warn(`[Security Migration] Removing insecure ${key} from localStorage`);
      localStorage.removeItem(key);
    }
  });
};

// Run migration on module load
migrateLegacyTokens();

// Request interceptor - no longer needs to add Authorization header
// Tokens are automatically sent via HttpOnly cookies
apiClient.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // Optional: Add CSRF token if using CSRF protection
    const csrfToken = document.cookie
      .split('; ')
      .find(row => row.startsWith('XSRF-TOKEN='))
      ?.split('=')[1];

    if (csrfToken) {
      config.headers = {
        ...config.headers,
        'X-XSRF-TOKEN': decodeURIComponent(csrfToken),
      };
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle errors
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    return response;
  },
  (error) => {
    // Handle 401 unauthorized errors
    if (error.response?.status === 401) {
      // HttpOnly cookies will be cleared by server on logout
      // Clear any session storage
      sessionStorage.clear();
      window.location.href = '/login';
    }

    // Handle 403 forbidden errors
    if (error.response?.status === 403) {
      // Show permission denied message
      console.error('Permission denied');
    }

    // Handle network errors
    if (!error.response) {
      console.error('Network error');
    }

    return Promise.reject(error);
  }
);

export { apiClient };