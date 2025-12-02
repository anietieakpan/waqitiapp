/**
 * Secure Authentication Service
 *
 * Manages authentication using httpOnly cookies instead of localStorage.
 * This prevents XSS attacks from stealing authentication tokens.
 *
 * Migration from localStorage:
 * - BEFORE: localStorage.setItem('token', jwt) - vulnerable to XSS
 * - AFTER: Server sets httpOnly cookie - JavaScript cannot access
 *
 * Security Benefits:
 * - XSS Protection: JavaScript cannot read httpOnly cookies
 * - CSRF Protection: SameSite=Strict prevents cross-site requests
 * - Automatic: Browsers automatically send cookies with requests
 *
 * @author Waqiti Platform Team - Frontend Security
 * @version 1.0.0
 * @since 2025-10-26
 */

import axios from '../utils/axios';

interface LoginCredentials {
  username: string;
  password: string;
}

interface LoginResponse {
  success: boolean;
  message: string;
  user: {
    id: string;
    username: string;
    email: string;
  };
}

interface AuthStatus {
  authenticated: boolean;
  user?: {
    id: string;
    username: string;
  };
}

class SecureAuthService {
  /**
   * Login user and set authentication cookies
   *
   * IMPORTANT: This method does NOT return tokens.
   * Tokens are set in httpOnly cookies by the server.
   *
   * @param credentials User credentials
   * @returns Login response with user info (NO tokens)
   */
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    try {
      const response = await axios.post<LoginResponse>('/auth/login', credentials);

      // No need to store tokens - they're in httpOnly cookies now
      // axios automatically sends these cookies with subsequent requests

      console.log('Login successful - authentication cookies set by server');

      return response.data;
    } catch (error: any) {
      console.error('Login failed:', error.response?.data || error.message);
      throw error;
    }
  }

  /**
   * Logout user and clear authentication cookies
   *
   * Server will clear the httpOnly cookies.
   */
  async logout(): Promise<void> {
    try {
      await axios.post('/auth/logout');

      // Clean up any local state (but NOT tokens - they were never stored locally)
      this.clearLocalUserData();

      console.log('Logout successful - authentication cookies cleared');
    } catch (error: any) {
      console.error('Logout failed:', error.response?.data || error.message);

      // Even if server logout fails, clear local data
      this.clearLocalUserData();

      throw error;
    }
  }

  /**
   * Refresh access token
   *
   * Uses refresh token from httpOnly cookie.
   * Server will set new access token cookie.
   *
   * @returns Promise that resolves when token is refreshed
   */
  async refreshToken(): Promise<void> {
    try {
      await axios.post('/auth/refresh');

      console.log('Access token refreshed successfully');
    } catch (error: any) {
      console.error('Token refresh failed:', error.response?.data || error.message);

      // If refresh fails, user needs to login again
      throw error;
    }
  }

  /**
   * Check if user is authenticated
   *
   * Queries backend to verify authentication status.
   * Cannot check locally because tokens are in httpOnly cookies.
   *
   * @returns Authentication status
   */
  async checkAuthStatus(): Promise<AuthStatus> {
    try {
      const response = await axios.get<AuthStatus>('/auth/status');
      return response.data;
    } catch (error: any) {
      console.error('Auth status check failed:', error.response?.data || error.message);

      // If status check fails, assume not authenticated
      return { authenticated: false };
    }
  }

  /**
   * Check if user is authenticated (cached check)
   *
   * Uses localStorage for caching authentication state (NOT tokens).
   * This is safe because we're only storing a boolean, not sensitive data.
   *
   * @returns Cached authentication status
   */
  isAuthenticatedCached(): boolean {
    // Safe to use localStorage for non-sensitive data
    return localStorage.getItem('isAuthenticated') === 'true';
  }

  /**
   * Set authentication status in cache
   *
   * @param isAuthenticated Authentication status
   */
  setAuthenticationCache(isAuthenticated: boolean): void {
    if (isAuthenticated) {
      localStorage.setItem('isAuthenticated', 'true');
    } else {
      localStorage.removeItem('isAuthenticated');
    }
  }

  /**
   * Clear local user data (NOT tokens - those are in httpOnly cookies)
   *
   * Clears any locally cached user preferences or non-sensitive data.
   */
  private clearLocalUserData(): void {
    // Clear cached authentication status
    localStorage.removeItem('isAuthenticated');

    // Clear any other local user data (preferences, etc.)
    // But DO NOT try to clear tokens - they were never stored locally
    localStorage.removeItem('userPreferences');
    localStorage.removeItem('theme');
    // Add other non-sensitive items as needed
  }

  /**
   * Initialize CSRF protection
   *
   * Makes a request to trigger CSRF token generation.
   * Should be called on app initialization.
   */
  async initializeCsrf(): Promise<void> {
    try {
      await axios.get('/auth/csrf');
      console.log('CSRF protection initialized');
    } catch (error: any) {
      console.error('CSRF initialization failed:', error.response?.data || error.message);
      // Don't throw - CSRF initialization failure shouldn't block app
    }
  }
}

export default new SecureAuthService();
