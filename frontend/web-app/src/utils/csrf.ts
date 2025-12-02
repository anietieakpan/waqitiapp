/**
 * CSRF Token Utility
 *
 * Provides Cross-Site Request Forgery (CSRF) protection for the Waqiti web application.
 * Implements the Double Submit Cookie pattern with XOR token masking.
 *
 * Security Flow:
 * 1. Backend generates CSRF token and sets it in XSRF-TOKEN cookie
 * 2. Frontend reads token from cookie using this utility
 * 3. Frontend sends token in X-XSRF-TOKEN header for state-changing requests
 * 4. Backend validates token matches cookie value
 *
 * @author Waqiti Platform Team - Frontend Security
 * @version 1.0.0
 * @since 2025-10-26
 */

/**
 * Extract CSRF token from browser cookies
 *
 * Reads the XSRF-TOKEN cookie set by the backend and returns its value.
 * This token must be sent back in the X-XSRF-TOKEN header for POST/PUT/DELETE requests.
 *
 * @returns CSRF token string or null if not found
 */
export const getCsrfToken = (): string | null => {
  // Spring Security sets the token in 'XSRF-TOKEN' cookie by default
  const cookieName = 'XSRF-TOKEN';
  
  // Parse all cookies from document.cookie
  const cookies = document.cookie.split(';');
  
  for (const cookie of cookies) {
    const trimmedCookie = cookie.trim();
    
    // Check if this cookie is the CSRF token
    if (trimmedCookie.startsWith(`${cookieName}=`)) {
      // Extract token value (everything after "XSRF-TOKEN=")
      const token = trimmedCookie.substring(cookieName.length + 1);
      
      // Decode URI component in case token contains special characters
      return decodeURIComponent(token);
    }
  }
  
  // Token not found - might be first request or CSRF disabled
  return null;
};

/**
 * Check if CSRF token exists in cookies
 *
 * Useful for determining if user session is initialized and CSRF protection is active.
 *
 * @returns true if CSRF token cookie exists
 */
export const hasCsrfToken = (): boolean => {
  return getCsrfToken() !== null;
};

/**
 * Wait for CSRF token to be available
 *
 * Some requests need to wait for initial CSRF token generation.
 * This utility polls for the token with exponential backoff.
 *
 * @param maxAttempts Maximum number of polling attempts (default: 10)
 * @param initialDelay Initial delay in milliseconds (default: 100ms)
 * @returns Promise that resolves to token or rejects if not found
 */
export const waitForCsrfToken = async (
  maxAttempts: number = 10,
  initialDelay: number = 100
): Promise<string> => {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const token = getCsrfToken();
    
    if (token) {
      return token;
    }
    
    // Exponential backoff: 100ms, 200ms, 400ms, 800ms, etc.
    const delay = initialDelay * Math.pow(2, attempt);
    await new Promise(resolve => setTimeout(resolve, delay));
  }
  
  throw new Error('CSRF token not available after maximum attempts');
};

/**
 * Manually trigger CSRF token generation
 *
 * Makes a GET request to trigger CSRF token generation on the backend.
 * Useful for initializing CSRF protection before first state-changing request.
 *
 * @param baseURL Backend API base URL
 * @returns Promise that resolves when token is generated
 */
export const initializeCsrfToken = async (baseURL: string = '/api'): Promise<void> => {
  try {
    // Make a simple GET request to trigger CSRF token generation
    // Most backends generate CSRF tokens on first request
    await fetch(`${baseURL}/v1/auth/csrf`, {
      method: 'GET',
      credentials: 'include', // Important: send cookies
    });
    
    // Wait for token to appear in cookies
    await waitForCsrfToken();
  } catch (error) {
    console.error('Failed to initialize CSRF token:', error);
    throw error;
  }
};

/**
 * Create headers object with CSRF token
 *
 * Convenience method to create HTTP headers object with CSRF token included.
 * Use this when making manual fetch requests.
 *
 * @param additionalHeaders Additional headers to include
 * @returns Headers object with CSRF token
 */
export const createCsrfHeaders = (
  additionalHeaders: Record<string, string> = {}
): Record<string, string> => {
  const token = getCsrfToken();
  
  const headers: Record<string, string> = {
    ...additionalHeaders,
  };
  
  if (token) {
    headers['X-XSRF-TOKEN'] = token;
  }
  
  return headers;
};

/**
 * Check if request method requires CSRF protection
 *
 * GET, HEAD, OPTIONS, and TRACE are considered safe methods
 * and don't require CSRF tokens. All state-changing methods do.
 *
 * @param method HTTP method
 * @returns true if method requires CSRF token
 */
export const requiresCsrfToken = (method: string): boolean => {
  const safeMethods = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];
  return !safeMethods.includes(method.toUpperCase());
};

/**
 * Validate CSRF token format
 *
 * Basic validation to ensure token looks valid before sending.
 * Spring Security tokens are typically 36+ characters (UUID-based).
 *
 * @param token CSRF token to validate
 * @returns true if token format appears valid
 */
export const isValidCsrfToken = (token: string | null): boolean => {
  if (!token) return false;
  
  // Token should be at least 20 characters (conservative check)
  if (token.length < 20) return false;
  
  // Token should only contain alphanumeric characters, hyphens, and underscores
  const validTokenPattern = /^[a-zA-Z0-9\-_=]+$/;
  return validTokenPattern.test(token);
};

export default {
  getCsrfToken,
  hasCsrfToken,
  waitForCsrfToken,
  initializeCsrfToken,
  createCsrfHeaders,
  requiresCsrfToken,
  isValidCsrfToken,
};
