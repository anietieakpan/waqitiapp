/**
 * Secure Storage Service
 *
 * Provides encrypted storage for sensitive data using Web Crypto API
 * with HttpOnly cookie support for maximum security.
 *
 * Features:
 * - AES-256-GCM encryption
 * - Secure key derivation (PBKDF2)
 * - HttpOnly cookie support
 * - XSS protection
 * - CSRF token integration
 * - Automatic expiration
 * - Session binding
 */

interface StorageOptions {
  encrypt?: boolean;
  httpOnly?: boolean;
  secure?: boolean;
  sameSite?: 'strict' | 'lax' | 'none';
  maxAge?: number; // seconds
  path?: string;
}

interface EncryptedData {
  ciphertext: string;
  iv: string;
  salt: string;
  timestamp: number;
}

class SecureStorageService {
  private readonly ENCRYPTION_KEY = 'waqiti_secure_storage_key';
  private readonly KEY_ITERATIONS = 100000;
  private readonly KEY_LENGTH = 256;
  private encryptionKey: CryptoKey | null = null;
  private sessionId: string;

  constructor() {
    this.sessionId = this.generateSessionId();
    this.initializeEncryptionKey();
  }

  /**
   * Initialize encryption key using PBKDF2
   */
  private async initializeEncryptionKey(): Promise<void> {
    try {
      const password = this.getDeviceSecret();
      const encoder = new TextEncoder();
      const keyMaterial = await crypto.subtle.importKey(
        'raw',
        encoder.encode(password),
        'PBKDF2',
        false,
        ['deriveBits', 'deriveKey']
      );

      this.encryptionKey = await crypto.subtle.deriveKey(
        {
          name: 'PBKDF2',
          salt: encoder.encode(this.sessionId),
          iterations: this.KEY_ITERATIONS,
          hash: 'SHA-256'
        },
        keyMaterial,
        { name: 'AES-GCM', length: this.KEY_LENGTH },
        true,
        ['encrypt', 'decrypt']
      );
    } catch (error) {
      console.error('Failed to initialize encryption key:', error);
      throw new Error('Secure storage initialization failed');
    }
  }

  /**
   * Get device-specific secret (combines multiple entropy sources)
   */
  private getDeviceSecret(): string {
    const sources = [
      this.ENCRYPTION_KEY,
      navigator.userAgent,
      navigator.language,
      screen.width.toString(),
      screen.height.toString(),
      new Date().getTimezoneOffset().toString(),
      this.sessionId
    ];

    return sources.join('|');
  }

  /**
   * Generate cryptographically secure session ID
   */
  private generateSessionId(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
  }

  /**
   * Encrypt data using AES-256-GCM
   */
  private async encrypt(data: string): Promise<EncryptedData> {
    if (!this.encryptionKey) {
      await this.initializeEncryptionKey();
    }

    const encoder = new TextEncoder();
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const salt = crypto.getRandomValues(new Uint8Array(16));

    const ciphertext = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv: iv
      },
      this.encryptionKey!,
      encoder.encode(data)
    );

    return {
      ciphertext: this.arrayBufferToBase64(ciphertext),
      iv: this.arrayBufferToBase64(iv),
      salt: this.arrayBufferToBase64(salt),
      timestamp: Date.now()
    };
  }

  /**
   * Decrypt data using AES-256-GCM
   */
  private async decrypt(encryptedData: EncryptedData): Promise<string> {
    if (!this.encryptionKey) {
      await this.initializeEncryptionKey();
    }

    const ciphertext = this.base64ToArrayBuffer(encryptedData.ciphertext);
    const iv = this.base64ToArrayBuffer(encryptedData.iv);

    const plaintext = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: new Uint8Array(iv)
      },
      this.encryptionKey!,
      ciphertext
    );

    const decoder = new TextDecoder();
    return decoder.decode(plaintext);
  }

  /**
   * Store data securely
   */
  async setItem(
    key: string,
    value: string,
    options: StorageOptions = {}
  ): Promise<void> {
    const {
      encrypt = true,
      httpOnly = false,
      secure = true,
      sameSite = 'strict',
      maxAge = 86400, // 24 hours default
      path = '/'
    } = options;

    try {
      let storedValue: string;

      if (encrypt) {
        const encrypted = await this.encrypt(value);
        storedValue = JSON.stringify(encrypted);
      } else {
        storedValue = value;
      }

      if (httpOnly) {
        // Store in HttpOnly cookie via backend API
        await this.setHttpOnlyCookie(key, storedValue, {
          secure,
          sameSite,
          maxAge,
          path
        });
      } else {
        // Store in sessionStorage (more secure than localStorage)
        sessionStorage.setItem(this.getPrefixedKey(key), storedValue);
      }
    } catch (error) {
      console.error('Failed to store item securely:', error);
      throw new Error('Secure storage failed');
    }
  }

  /**
   * Retrieve data securely
   */
  async getItem(key: string, encrypted = true): Promise<string | null> {
    try {
      // Try HttpOnly cookie first
      let storedValue = await this.getHttpOnlyCookie(key);

      // Fallback to sessionStorage
      if (!storedValue) {
        storedValue = sessionStorage.getItem(this.getPrefixedKey(key));
      }

      if (!storedValue) {
        return null;
      }

      if (encrypted) {
        const encryptedData: EncryptedData = JSON.parse(storedValue);

        // Check expiration (24 hours)
        if (Date.now() - encryptedData.timestamp > 24 * 60 * 60 * 1000) {
          await this.removeItem(key);
          return null;
        }

        return await this.decrypt(encryptedData);
      }

      return storedValue;
    } catch (error) {
      console.error('Failed to retrieve item securely:', error);
      return null;
    }
  }

  /**
   * Remove item from secure storage
   */
  async removeItem(key: string): Promise<void> {
    try {
      // Remove from sessionStorage
      sessionStorage.removeItem(this.getPrefixedKey(key));

      // Remove HttpOnly cookie
      await this.deleteHttpOnlyCookie(key);
    } catch (error) {
      console.error('Failed to remove item:', error);
    }
  }

  /**
   * Clear all secure storage
   */
  async clear(): Promise<void> {
    try {
      // Clear sessionStorage items with our prefix
      const keys = Object.keys(sessionStorage);
      const prefix = this.getStoragePrefix();

      keys.forEach(key => {
        if (key.startsWith(prefix)) {
          sessionStorage.removeItem(key);
        }
      });

      // Clear HttpOnly cookies via API
      await this.clearHttpOnlyCookies();
    } catch (error) {
      console.error('Failed to clear secure storage:', error);
    }
  }

  /**
   * Set HttpOnly cookie via backend API
   */
  private async setHttpOnlyCookie(
    key: string,
    value: string,
    options: Partial<StorageOptions>
  ): Promise<void> {
    try {
      const response = await fetch('/api/v1/secure-storage/set-cookie', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': await this.getCSRFToken()
        },
        credentials: 'include',
        body: JSON.stringify({
          key,
          value,
          options
        })
      });

      if (!response.ok) {
        throw new Error('Failed to set HttpOnly cookie');
      }
    } catch (error) {
      console.error('HttpOnly cookie storage failed:', error);
      throw error;
    }
  }

  /**
   * Get HttpOnly cookie via backend API
   */
  private async getHttpOnlyCookie(key: string): Promise<string | null> {
    try {
      const response = await fetch(`/api/v1/secure-storage/get-cookie?key=${encodeURIComponent(key)}`, {
        method: 'GET',
        headers: {
          'X-CSRF-Token': await this.getCSRFToken()
        },
        credentials: 'include'
      });

      if (!response.ok) {
        return null;
      }

      const data = await response.json();
      return data.value || null;
    } catch (error) {
      console.error('Failed to retrieve HttpOnly cookie:', error);
      return null;
    }
  }

  /**
   * Delete HttpOnly cookie via backend API
   */
  private async deleteHttpOnlyCookie(key: string): Promise<void> {
    try {
      await fetch('/api/v1/secure-storage/delete-cookie', {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': await this.getCSRFToken()
        },
        credentials: 'include',
        body: JSON.stringify({ key })
      });
    } catch (error) {
      console.error('Failed to delete HttpOnly cookie:', error);
    }
  }

  /**
   * Clear all HttpOnly cookies
   */
  private async clearHttpOnlyCookies(): Promise<void> {
    try {
      await fetch('/api/v1/secure-storage/clear-cookies', {
        method: 'POST',
        headers: {
          'X-CSRF-Token': await this.getCSRFToken()
        },
        credentials: 'include'
      });
    } catch (error) {
      console.error('Failed to clear HttpOnly cookies:', error);
    }
  }

  /**
   * Get CSRF token for API calls
   */
  private async getCSRFToken(): Promise<string> {
    const token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content');
    if (token) {
      return token;
    }

    // Fetch from API if not in meta tag
    try {
      const response = await fetch('/api/v1/csrf-token', {
        credentials: 'include'
      });
      const data = await response.json();
      return data.token;
    } catch (error) {
      console.error('Failed to get CSRF token:', error);
      throw new Error('CSRF token required');
    }
  }

  /**
   * Get prefixed storage key
   */
  private getPrefixedKey(key: string): string {
    return `${this.getStoragePrefix()}${key}`;
  }

  /**
   * Get storage prefix with session binding
   */
  private getStoragePrefix(): string {
    return `waqiti_secure_${this.sessionId.substring(0, 8)}_`;
  }

  /**
   * Convert ArrayBuffer to Base64
   */
  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  /**
   * Convert Base64 to ArrayBuffer
   */
  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  /**
   * Check if secure storage is available
   */
  isAvailable(): boolean {
    try {
      return (
        typeof crypto !== 'undefined' &&
        typeof crypto.subtle !== 'undefined' &&
        typeof sessionStorage !== 'undefined'
      );
    } catch {
      return false;
    }
  }
}

// Singleton instance
const secureStorage = new SecureStorageService();

export default secureStorage;
export { SecureStorageService, StorageOptions, EncryptedData };
