/**
 * =====================================================================
 * Secure Token Storage Utility
 * =====================================================================
 * SECURITY: Replaces localStorage token storage with HttpOnly cookies
 *
 * VULNERABILITY FIXED: XSS Token Theft (CWE-79, CWE-522)
 * - localStorage is accessible to JavaScript, including XSS attacks
 * - HttpOnly cookies cannot be accessed by JavaScript
 * - Secure flag ensures transmission only over HTTPS
 * - SameSite=Strict prevents CSRF attacks
 *
 * COMPLIANCE:
 * - PCI-DSS Requirement 6.5.7 (XSS prevention)
 * - OWASP A03:2021 Injection
 * - OWASP A07:2021 Identification and Authentication Failures
 *
 * MIGRATION GUIDE:
 * 1. Backend must set tokens in HttpOnly cookies
 * 2. Replace all localStorage.setItem('token') with this utility
 * 3. Remove all localStorage.getItem('token') calls
 * 4. Tokens automatically sent with requests via cookie
 * =====================================================================
 */

/**
 * Token storage configuration
 */
export interface SecureStorageConfig {
  accessTokenKey?: string;
  refreshTokenKey?: string;
  cookieDomain?: string;
  cookiePath?: string;
  secureCookie?: boolean; // Force HTTPS-only
  sameSite?: 'Strict' | 'Lax' | 'None';
}

const DEFAULT_CONFIG: Required<SecureStorageConfig> = {
  accessTokenKey: '__waqiti_access',
  refreshTokenKey: '__waqiti_refresh',
  cookieDomain: window.location.hostname,
  cookiePath: '/',
  secureCookie: window.location.protocol === 'https:',
  sameSite: 'Strict',
};

/**
 * Secure storage manager for authentication tokens
 * Uses HttpOnly cookies instead of localStorage to prevent XSS token theft
 */
export class SecureStorage {
  private config: Required<SecureStorageConfig>;

  constructor(config: SecureStorageConfig = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };

    // Validate secure cookie usage in production
    if (import.meta.env.PROD && !this.config.secureCookie) {
      console.warn(
        'SECURITY WARNING: Secure cookies disabled in production. ' +
        'Tokens may be transmitted over insecure connections.'
      );
    }
  }

  /**
   * MIGRATION HELPER: Clear legacy localStorage tokens
   * Call this once during app initialization to clean up old insecure storage
   */
  public migrateLegacyStorage(): void {
    const legacyKeys = [
      'accessToken',
      'refreshToken',
      'token',
      'authToken',
      'adminToken',
      'jwt',
      'bearer',
    ];

    legacyKeys.forEach(key => {
      if (localStorage.getItem(key)) {
        console.warn(
          `[Security Migration] Removing insecure token from localStorage: ${key}`
        );
        localStorage.removeItem(key);
      }
    });

    // Also check sessionStorage
    legacyKeys.forEach(key => {
      if (sessionStorage.getItem(key)) {
        console.warn(
          `[Security Migration] Removing insecure token from sessionStorage: ${key}`
        );
        sessionStorage.removeItem(key);
      }
    });
  }

  /**
   * Check if user is authenticated
   * Note: With HttpOnly cookies, we can't read the token value
   * We rely on the cookie being present and the server validating it
   */
  public isAuthenticated(): boolean {
    // Check if auth cookie exists (we can see non-HttpOnly metadata)
    return this.hasCookie(this.config.accessTokenKey);
  }

  /**
   * Check if a cookie exists
   * Note: This only works for non-HttpOnly cookies or checking cookie presence via document.cookie
   * For HttpOnly cookies, we must rely on server-side validation
   */
  private hasCookie(name: string): boolean {
    const cookies = document.cookie.split(';');
    return cookies.some(cookie => {
      const [key] = cookie.trim().split('=');
      return key === name;
    });
  }

  /**
   * Store user metadata (non-sensitive) in sessionStorage
   * This is safe because it doesn't contain authentication credentials
   */
  public setUserMetadata(user: any): void {
    if (!user) {
      sessionStorage.removeItem('user_metadata');
      return;
    }

    // Only store non-sensitive user information
    const safeUserData = {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      verified: user.verified,
      mfaEnabled: user.mfaEnabled,
      // DO NOT store: password, tokens, social security numbers, etc.
    };

    sessionStorage.setItem('user_metadata', JSON.stringify(safeUserData));
  }

  /**
   * Retrieve user metadata from sessionStorage
   */
  public getUserMetadata(): any | null {
    const metadata = sessionStorage.getItem('user_metadata');
    return metadata ? JSON.parse(metadata) : null;
  }

  /**
   * Clear all authentication data
   * Note: HttpOnly cookies must be cleared by the server via logout endpoint
   * This method only clears client-side metadata
   */
  public clearAuth(): void {
    // Clear user metadata
    sessionStorage.removeItem('user_metadata');

    // Clear any non-HttpOnly cookies (shouldn't have auth tokens, but for safety)
    this.clearNonHttpOnlyCookie(this.config.accessTokenKey);
    this.clearNonHttpOnlyCookie(this.config.refreshTokenKey);

    // Migrate any legacy storage
    this.migrateLegacyStorage();
  }

  /**
   * Clear a non-HttpOnly cookie
   * Note: HttpOnly cookies CANNOT be deleted by JavaScript (security feature)
   * They must be cleared by the server setting an expired cookie
   */
  private clearNonHttpOnlyCookie(name: string): void {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=${this.config.cookiePath}; domain=${this.config.cookieDomain}; SameSite=${this.config.sameSite};`;
  }

  /**
   * Get CSRF token from cookie (if using CSRF protection)
   * CSRF tokens are typically stored in non-HttpOnly cookies so JavaScript can read them
   */
  public getCsrfToken(): string | null {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
  }
}

/**
 * Global secure storage instance
 */
export const secureStorage = new SecureStorage();

/**
 * Initialize secure storage and migrate from legacy localStorage
 * Call this once during app initialization
 */
export function initializeSecureStorage(): void {
  console.log('[SecureStorage] Initializing secure token storage...');

  // Migrate legacy tokens
  secureStorage.migrateLegacyStorage();

  // Check for legacy tokens and warn developers
  if (
    localStorage.getItem('accessToken') ||
    localStorage.getItem('refreshToken') ||
    localStorage.getItem('token')
  ) {
    console.error(
      '[SecureStorage] CRITICAL: Legacy tokens found in localStorage! ' +
      'These tokens are vulnerable to XSS attacks. ' +
      'Please ensure all token storage uses HttpOnly cookies.'
    );
  }

  console.log('[SecureStorage] Initialization complete');
}

/**
 * Utility: Detect if code is attempting to store tokens in localStorage
 * Install this as a development-time safety check
 */
export function installLocalStorageGuard(): void {
  if (import.meta.env.DEV) {
    const originalSetItem = localStorage.setItem.bind(localStorage);
    const dangerousKeys = ['token', 'accessToken', 'refreshToken', 'jwt', 'bearer', 'authToken'];

    localStorage.setItem = function(key: string, value: string) {
      if (dangerousKeys.some(k => key.toLowerCase().includes(k))) {
        console.error(
          `[SecureStorage Guard] BLOCKED: Attempted to store token "${key}" in localStorage. ` +
          `This is a security vulnerability. Use HttpOnly cookies instead.`
        );
        throw new Error(`Storing authentication tokens in localStorage is forbidden. Use secure cookies.`);
      }
      return originalSetItem(key, value);
    };

    console.log('[SecureStorage Guard] Development mode: localStorage token guard installed');
  }
}
