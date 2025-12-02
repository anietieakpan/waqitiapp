import DOMPurify from 'dompurify';

/**
 * Sanitizes HTML content to prevent XSS attacks
 * @param dirty - The HTML string to sanitize
 * @param options - Optional DOMPurify configuration
 * @returns Sanitized HTML string
 */
export const sanitizeHTML = (dirty: string, options?: DOMPurify.Config): string => {
  // Configure DOMPurify with secure defaults
  const defaultConfig: DOMPurify.Config = {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'b', 'i', 'em', 'u', 'a', 
      'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'span', 'div', 'img', 'hr', 'sub', 'sup'
    ],
    ALLOWED_ATTR: [
      'href', 'title', 'target', 'rel', 'class', 'id',
      'src', 'alt', 'width', 'height', 'style'
    ],
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i,
    ALLOW_DATA_ATTR: false,
    ALLOW_UNKNOWN_PROTOCOLS: false,
    SAFE_FOR_TEMPLATES: true,
    WHOLE_DOCUMENT: false,
    RETURN_DOM: false,
    RETURN_DOM_FRAGMENT: false,
    FORCE_BODY: false,
    SANITIZE_DOM: true,
    IN_PLACE: false,
    ...options
  };

  // Add hook to set target="_blank" and rel="noopener noreferrer" for external links
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A') {
      const href = node.getAttribute('href');
      if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
        node.setAttribute('target', '_blank');
        node.setAttribute('rel', 'noopener noreferrer');
      }
    }
  });

  return DOMPurify.sanitize(dirty, defaultConfig as any) as unknown as string;
};

/**
 * Sanitizes HTML for displaying article content
 * Allows more formatting tags suitable for articles
 */
export const sanitizeArticleHTML = (dirty: string): string => {
  return sanitizeHTML(dirty, {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'b', 'i', 'em', 'u', 'a', 
      'ul', 'ol', 'li', 'blockquote', 'code', 'pre',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'span', 'div', 'img', 'hr', 'sub', 'sup',
      'figure', 'figcaption', 'mark', 'kbd', 'abbr'
    ],
    ALLOWED_ATTR: [
      'href', 'title', 'target', 'rel', 'class', 'id',
      'src', 'alt', 'width', 'height', 'style',
      'data-language', 'data-theme'
    ],
    ALLOW_DATA_ATTR: true,
    ADD_TAGS: ['iframe'], // Allow embedded videos
    ADD_ATTR: ['allowfullscreen', 'frameborder', 'scrolling'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i
  });
};

/**
 * Sanitizes HTML for displaying in limited contexts (like category icons)
 * Only allows basic inline elements and SVG
 */
export const sanitizeIconHTML = (dirty: string): string => {
  return sanitizeHTML(dirty, {
    ALLOWED_TAGS: [
      'svg', 'path', 'g', 'circle', 'rect', 'line', 'polyline', 'polygon',
      'span', 'i', 'b', 'strong', 'em'
    ],
    ALLOWED_ATTR: [
      'class', 'id', 'viewBox', 'xmlns', 'd', 'fill', 'stroke',
      'stroke-width', 'stroke-linecap', 'stroke-linejoin',
      'width', 'height', 'x', 'y', 'rx', 'ry', 'cx', 'cy', 'r',
      'points', 'transform', 'style'
    ],
    ALLOW_DATA_ATTR: false,
    KEEP_CONTENT: true,
    USE_PROFILES: { svg: true, svgFilters: true }
  });
};

/**
 * Strips all HTML tags and returns plain text
 * Useful for displaying HTML content in contexts where HTML is not allowed
 */
export const stripHTML = (html: string): string => {
  const clean = DOMPurify.sanitize(html, { 
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
    KEEP_CONTENT: true
  });
  return clean;
};

/**
 * Validates if a string contains potentially dangerous HTML
 * @param html - The HTML string to check
 * @returns true if the HTML is safe, false if it contains dangerous content
 */
export const isHTMLSafe = (html: string): boolean => {
  const cleaned = sanitizeHTML(html);
  return cleaned === html;
};

/**
 * Configuration for trusted content sources
 * Only content from these sources should bypass sanitization
 */
export const TRUSTED_SOURCES = {
  SYSTEM: 'system',
  ADMIN: 'admin',
  VERIFIED_CONTENT: 'verified'
} as const;

export type TrustedSource = typeof TRUSTED_SOURCES[keyof typeof TRUSTED_SOURCES];

/**
 * Sanitizes content based on its source
 * System and admin content may have less restrictive sanitization
 */
export const sanitizeBySource = (
  content: string, 
  source?: TrustedSource
): string => {
  switch (source) {
    case TRUSTED_SOURCES.SYSTEM:
    case TRUSTED_SOURCES.ADMIN:
      // Still sanitize but allow more tags for trusted sources
      return sanitizeArticleHTML(content);
    case TRUSTED_SOURCES.VERIFIED_CONTENT:
      return sanitizeHTML(content);
    default:
      // Unknown sources get strict sanitization
      return sanitizeHTML(content);
  }
};

// Clean up hooks when module is unloaded
if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', () => {
    DOMPurify.removeAllHooks();
  });
}