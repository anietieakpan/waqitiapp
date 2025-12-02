/**
 * Content Security Policy (CSP) directives for Waqiti Web App
 * Implements comprehensive security hardening against XSS, injection attacks, and data exfiltration
 */

export const CSP_DIRECTIVES = {
  'default-src': [
    "'self'",
  ],
  
  'script-src': [
    "'self'",
    "'unsafe-inline'", // Only for development - should be removed in production
    "'unsafe-eval'", // Only for development - should be removed in production
    // Production should use nonces or hashes instead
    'https://www.googletagmanager.com',
    'https://www.google-analytics.com',
    'https://connect.facebook.net',
    'https://js.stripe.com',
  ],
  
  'style-src': [
    "'self'",
    "'unsafe-inline'", // MUI requires inline styles
    'https://fonts.googleapis.com',
    'https://cdn.jsdelivr.net',
  ],
  
  'font-src': [
    "'self'",
    'https://fonts.gstatic.com',
    'data:', // For base64 encoded fonts
  ],
  
  'img-src': [
    "'self'",
    'data:', // For base64 images
    'blob:', // For dynamically generated images
    'https:', // Allow HTTPS images
    'https://www.gravatar.com', // User avatars
    'https://secure.gravatar.com',
    'https://avatars.githubusercontent.com',
  ],
  
  'media-src': [
    "'self'",
    'blob:',
    'data:',
  ],
  
  'object-src': [
    "'none'", // Prevent embedding of plugins
  ],
  
  'connect-src': [
    "'self'",
    'https:', // API calls
    'wss:', // WebSocket connections
    'https://api.waqiti.com',
    'https://analytics.waqiti.com',
    'https://sentry.io', // Error reporting
    'https://*.sentry.io',
  ],
  
  'frame-src': [
    "'none'", // Prevent framing by default
  ],
  
  'frame-ancestors': [
    "'none'", // Prevent being framed (clickjacking protection)
  ],
  
  'form-action': [
    "'self'",
    'https://api.waqiti.com',
  ],
  
  'base-uri': [
    "'self'", // Restrict base URI
  ],
  
  'manifest-src': [
    "'self'",
  ],
  
  'worker-src': [
    "'self'",
    'blob:', // For service workers
  ],
  
  'upgrade-insecure-requests': [], // Force HTTPS
  
  'block-all-mixed-content': [], // Block mixed content
};

export const generateCSPHeader = (): string => {
  return Object.entries(CSP_DIRECTIVES)
    .map(([directive, sources]) => {
      if (sources.length === 0) {
        return directive;
      }
      return `${directive} ${sources.join(' ')}`;
    })
    .join('; ');
};

// Security headers for enhanced protection
export const SECURITY_HEADERS = {
  // Prevent MIME type sniffing
  'X-Content-Type-Options': 'nosniff',
  
  // Enable XSS protection
  'X-XSS-Protection': '1; mode=block',
  
  // Prevent clickjacking
  'X-Frame-Options': 'DENY',
  
  // HSTS (HTTP Strict Transport Security)
  'Strict-Transport-Security': 'max-age=31536000; includeSubDomains; preload',
  
  // Referrer policy
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  
  // Permissions policy (formerly Feature Policy)
  'Permissions-Policy': [
    'geolocation=(self)',
    'camera=*',
    'microphone=*',
    'payment=(self)',
    'usb=()',
    'magnetometer=()',
    'gyroscope=()',
    'accelerometer=()',
  ].join(', '),
  
  // Cross-Origin policies
  'Cross-Origin-Embedder-Policy': 'require-corp',
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Resource-Policy': 'cross-origin',
};

// Generate nonce for CSP
export const generateNonce = (): string => {
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  return btoa(String.fromCharCode.apply(null, Array.from(array)));
};

// CSP reporting endpoint configuration
export const CSP_REPORT_CONFIG = {
  'report-uri': '/api/security/csp-report',
  'report-to': 'csp-endpoint',
};

// Report-To header for CSP reporting
export const REPORT_TO_HEADER = JSON.stringify({
  group: 'csp-endpoint',
  max_age: 10886400,
  endpoints: [
    {
      url: '/api/security/csp-report',
    },
  ],
});

/**
 * Apply security headers to the document
 * Note: These should ideally be set by the server, but can be enforced client-side as well
 */
export const applySecurityHeaders = (): void => {
  // Create meta tags for CSP if not set by server
  if (!document.querySelector('meta[http-equiv="Content-Security-Policy"]')) {
    const cspMeta = document.createElement('meta');
    cspMeta.setAttribute('http-equiv', 'Content-Security-Policy');
    cspMeta.setAttribute('content', generateCSPHeader());
    document.head.appendChild(cspMeta);
  }

  // Add X-Frame-Options meta tag
  if (!document.querySelector('meta[http-equiv="X-Frame-Options"]')) {
    const frameMeta = document.createElement('meta');
    frameMeta.setAttribute('http-equiv', 'X-Frame-Options');
    frameMeta.setAttribute('content', 'DENY');
    document.head.appendChild(frameMeta);
  }
};

/**
 * Validate external resources before loading
 */
export const validateExternalResource = (url: string): boolean => {
  try {
    const parsedUrl = new URL(url);
    
    // Check against allowed domains
    const allowedDomains = [
      'fonts.googleapis.com',
      'fonts.gstatic.com',
      'cdn.jsdelivr.net',
      'www.googletagmanager.com',
      'www.google-analytics.com',
      'js.stripe.com',
    ];
    
    return allowedDomains.some(domain => 
      parsedUrl.hostname === domain || parsedUrl.hostname.endsWith(`.${domain}`)
    );
  } catch {
    return false;
  }
};

/**
 * Sanitize HTML content to prevent XSS
 */
export const sanitizeHTML = (html: string): string => {
  const temp = document.createElement('div');
  temp.textContent = html;
  return temp.innerHTML;
};

/**
 * Secure localStorage wrapper with encryption
 */
export class SecureStorage {
  private static encryptionKey: string | null = null;

  static async generateKey(): Promise<void> {
    if (crypto.subtle) {
      const key = await crypto.subtle.generateKey(
        {
          name: 'AES-GCM',
          length: 256,
        },
        true,
        ['encrypt', 'decrypt']
      );
      
      const exportedKey = await crypto.subtle.exportKey('raw', key);
      this.encryptionKey = btoa(String.fromCharCode(...new Uint8Array(exportedKey)));
    }
  }

  static async encrypt(data: string): Promise<string> {
    if (!crypto.subtle || !this.encryptionKey) {
      // Fallback to base64 encoding (not secure, but better than nothing)
      return btoa(data);
    }

    const key = await crypto.subtle.importKey(
      'raw',
      Uint8Array.from(atob(this.encryptionKey), c => c.charCodeAt(0)),
      { name: 'AES-GCM' },
      false,
      ['encrypt']
    );

    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encodedData = new TextEncoder().encode(data);

    const encrypted = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      key,
      encodedData
    );

    const encryptedArray = new Uint8Array(encrypted);
    const combinedArray = new Uint8Array(iv.length + encryptedArray.length);
    combinedArray.set(iv);
    combinedArray.set(encryptedArray, iv.length);

    return btoa(String.fromCharCode(...combinedArray));
  }

  static async decrypt(encryptedData: string): Promise<string> {
    if (!crypto.subtle || !this.encryptionKey) {
      // Fallback to base64 decoding
      try {
        return atob(encryptedData);
      } catch {
        return encryptedData;
      }
    }

    const key = await crypto.subtle.importKey(
      'raw',
      Uint8Array.from(atob(this.encryptionKey), c => c.charCodeAt(0)),
      { name: 'AES-GCM' },
      false,
      ['decrypt']
    );

    const combinedArray = Uint8Array.from(atob(encryptedData), c => c.charCodeAt(0));
    const iv = combinedArray.slice(0, 12);
    const encryptedArray = combinedArray.slice(12);

    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      key,
      encryptedArray
    );

    return new TextDecoder().decode(decrypted);
  }

  static async setItem(key: string, value: string): Promise<void> {
    const encryptedValue = await this.encrypt(value);
    localStorage.setItem(`waqiti_${key}`, encryptedValue);
  }

  static async getItem(key: string): Promise<string | null> {
    const encryptedValue = localStorage.getItem(`waqiti_${key}`);
    if (!encryptedValue) return null;

    try {
      return await this.decrypt(encryptedValue);
    } catch {
      // If decryption fails, remove the item and return null
      localStorage.removeItem(`waqiti_${key}`);
      return null;
    }
  }

  static removeItem(key: string): void {
    localStorage.removeItem(`waqiti_${key}`);
  }

  static clear(): void {
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith('waqiti_')) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach(key => localStorage.removeItem(key));
  }
}

// Initialize secure storage
SecureStorage.generateKey().catch(console.error);