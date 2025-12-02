/**
 * Comprehensive input validation and sanitization utilities
 * Provides protection against injection attacks, XSS, and malformed data
 */

// Common validation patterns
export const VALIDATION_PATTERNS = {
  EMAIL: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
  PHONE: /^\+?[1-9]\d{1,14}$/,
  PASSWORD: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/,
  TRANSACTION_ID: /^[a-zA-Z0-9_-]{8,32}$/,
  AMOUNT: /^\d+(\.\d{1,2})?$/,
  CURRENCY_CODE: /^[A-Z]{3}$/,
  USERNAME: /^[a-zA-Z0-9_-]{3,20}$/,
  ALPHANUMERIC: /^[a-zA-Z0-9]+$/,
  NUMERIC: /^\d+$/,
  DECIMAL: /^\d*\.?\d+$/,
  HEX_COLOR: /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/,
  URL: /^https?:\/\/[-\w.]+(?:\:[0-9]+)?(?:\/[^\s]*)?$/,
  IP_ADDRESS: /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,
  SAFE_STRING: /^[a-zA-Z0-9\s\-_.,'()]+$/,
};

// Dangerous patterns that should be blocked
export const DANGEROUS_PATTERNS = [
  // Script injection patterns
  /<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi,
  /javascript:/gi,
  /on\w+\s*=/gi,
  /expression\s*\(/gi,
  /vbscript:/gi,
  /data:text\/html/gi,
  
  // SQL injection patterns
  /(\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION)\b)|(-{2})|(\/{2})/gi,
  /(\b(OR|AND)\b\s+\w+\s*=\s*\w+)|(\w+\s*=\s*\w+\s+(OR|AND))/gi,
  
  // Command injection patterns
  /[;&|`$(){}[\]]/g,
  /\.\.\//g,
  
  // Path traversal
  /\.\.[\\/]/g,
  
  // LDAP injection
  /[()\\*]/g,
];

export interface ValidationResult {
  isValid: boolean;
  errors: string[];
  sanitizedValue?: any;
}

export class InputValidator {
  /**
   * Validate email address
   */
  static validateEmail(email: string): ValidationResult {
    const errors: string[] = [];
    
    if (!email || typeof email !== 'string') {
      errors.push('Email is required');
      return { isValid: false, errors };
    }
    
    if (email.length > 254) {
      errors.push('Email address is too long');
    }
    
    if (!VALIDATION_PATTERNS.EMAIL.test(email)) {
      errors.push('Invalid email format');
    }
    
    // Check for dangerous patterns
    if (this.containsDangerousPatterns(email)) {
      errors.push('Email contains invalid characters');
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      sanitizedValue: email.toLowerCase().trim(),
    };
  }

  /**
   * Validate password strength
   */
  static validatePassword(password: string): ValidationResult {
    const errors: string[] = [];
    
    if (!password || typeof password !== 'string') {
      errors.push('Password is required');
      return { isValid: false, errors };
    }
    
    if (password.length < 8) {
      errors.push('Password must be at least 8 characters long');
    }
    
    if (password.length > 128) {
      errors.push('Password is too long');
    }
    
    if (!/(?=.*[a-z])/.test(password)) {
      errors.push('Password must contain at least one lowercase letter');
    }
    
    if (!/(?=.*[A-Z])/.test(password)) {
      errors.push('Password must contain at least one uppercase letter');
    }
    
    if (!/(?=.*\d)/.test(password)) {
      errors.push('Password must contain at least one number');
    }
    
    if (!/(?=.*[@$!%*?&])/.test(password)) {
      errors.push('Password must contain at least one special character');
    }
    
    // Check for common weak passwords
    const weakPasswords = [
      'password', 'password123', '12345678', 'qwerty', 'abc123',
      'admin', 'letmein', 'welcome', 'monkey', '123456789'
    ];
    
    if (weakPasswords.some(weak => password.toLowerCase().includes(weak))) {
      errors.push('Password is too common');
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      sanitizedValue: password, // Don't sanitize passwords
    };
  }

  /**
   * Validate transaction amount
   */
  static validateAmount(amount: string | number): ValidationResult {
    const errors: string[] = [];
    
    if (amount === null || amount === undefined || amount === '') {
      errors.push('Amount is required');
      return { isValid: false, errors };
    }
    
    const numAmount = typeof amount === 'string' ? parseFloat(amount) : amount;
    
    if (isNaN(numAmount)) {
      errors.push('Amount must be a valid number');
      return { isValid: false, errors };
    }
    
    if (numAmount <= 0) {
      errors.push('Amount must be greater than 0');
    }
    
    if (numAmount > 1000000) { // $1M limit
      errors.push('Amount exceeds maximum limit');
    }
    
    // Check for decimal places (max 2)
    const decimalPlaces = (numAmount.toString().split('.')[1] || '').length;
    if (decimalPlaces > 2) {
      errors.push('Amount can have maximum 2 decimal places');
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      sanitizedValue: Math.round(numAmount * 100) / 100, // Round to 2 decimal places
    };
  }

  /**
   * Validate phone number
   */
  static validatePhoneNumber(phone: string): ValidationResult {
    const errors: string[] = [];
    
    if (!phone || typeof phone !== 'string') {
      errors.push('Phone number is required');
      return { isValid: false, errors };
    }
    
    // Remove all non-digit characters except +
    const cleanPhone = phone.replace(/[^\d+]/g, '');
    
    if (!VALIDATION_PATTERNS.PHONE.test(cleanPhone)) {
      errors.push('Invalid phone number format');
    }
    
    if (cleanPhone.length > 15) {
      errors.push('Phone number is too long');
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      sanitizedValue: cleanPhone,
    };
  }

  /**
   * Validate and sanitize general text input
   */
  static validateText(
    text: string,
    options: {
      required?: boolean;
      minLength?: number;
      maxLength?: number;
      allowSpecialChars?: boolean;
      pattern?: RegExp;
    } = {}
  ): ValidationResult {
    const {
      required = false,
      minLength = 0,
      maxLength = 1000,
      allowSpecialChars = false,
      pattern,
    } = options;
    
    const errors: string[] = [];
    
    if (required && (!text || typeof text !== 'string')) {
      errors.push('This field is required');
      return { isValid: false, errors };
    }
    
    if (!text) {
      return { isValid: true, errors: [], sanitizedValue: '' };
    }
    
    if (typeof text !== 'string') {
      errors.push('Input must be a string');
      return { isValid: false, errors };
    }
    
    if (text.length < minLength) {
      errors.push(`Minimum length is ${minLength} characters`);
    }
    
    if (text.length > maxLength) {
      errors.push(`Maximum length is ${maxLength} characters`);
    }
    
    // Check for dangerous patterns
    if (this.containsDangerousPatterns(text)) {
      errors.push('Input contains invalid characters');
    }
    
    // Check pattern if provided
    if (pattern && !pattern.test(text)) {
      errors.push('Input format is invalid');
    }
    
    // Check for special characters if not allowed
    if (!allowSpecialChars && !VALIDATION_PATTERNS.SAFE_STRING.test(text)) {
      errors.push('Special characters are not allowed');
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      sanitizedValue: this.sanitizeText(text),
    };
  }

  /**
   * Validate JSON input
   */
  static validateJSON(jsonString: string): ValidationResult {
    const errors: string[] = [];
    
    if (!jsonString || typeof jsonString !== 'string') {
      errors.push('JSON string is required');
      return { isValid: false, errors };
    }
    
    try {
      const parsed = JSON.parse(jsonString);
      
      // Check for dangerous patterns in the JSON
      if (this.containsDangerousPatternsInObject(parsed)) {
        errors.push('JSON contains invalid data');
      }
      
      return {
        isValid: errors.length === 0,
        errors,
        sanitizedValue: parsed,
      };
    } catch (error) {
      errors.push('Invalid JSON format');
      return { isValid: false, errors };
    }
  }

  /**
   * Validate file upload
   */
  static validateFile(
    file: File,
    options: {
      maxSize?: number; // in bytes
      allowedTypes?: string[];
      allowedExtensions?: string[];
    } = {}
  ): ValidationResult {
    const {
      maxSize = 5 * 1024 * 1024, // 5MB default
      allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'application/pdf'],
      allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.pdf'],
    } = options;
    
    const errors: string[] = [];
    
    if (!file) {
      errors.push('File is required');
      return { isValid: false, errors };
    }
    
    if (file.size > maxSize) {
      errors.push(`File size exceeds ${Math.round(maxSize / 1024 / 1024)}MB limit`);
    }
    
    if (!allowedTypes.includes(file.type)) {
      errors.push('File type is not allowed');
    }
    
    const fileExtension = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!allowedExtensions.includes(fileExtension)) {
      errors.push('File extension is not allowed');
    }
    
    // Check for dangerous file names
    if (this.containsDangerousPatterns(file.name)) {
      errors.push('File name contains invalid characters');
    }
    
    return {
      isValid: errors.length === 0,
      errors,
      sanitizedValue: file,
    };
  }

  /**
   * Check if text contains dangerous patterns
   */
  private static containsDangerousPatterns(text: string): boolean {
    return DANGEROUS_PATTERNS.some(pattern => pattern.test(text));
  }

  /**
   * Check if object contains dangerous patterns (recursive)
   */
  private static containsDangerousPatternsInObject(obj: any): boolean {
    if (typeof obj === 'string') {
      return this.containsDangerousPatterns(obj);
    }
    
    if (Array.isArray(obj)) {
      return obj.some(item => this.containsDangerousPatternsInObject(item));
    }
    
    if (obj && typeof obj === 'object') {
      return Object.values(obj).some(value => 
        this.containsDangerousPatternsInObject(value)
      );
    }
    
    return false;
  }

  /**
   * Sanitize text input
   */
  private static sanitizeText(text: string): string {
    return text
      .replace(/[<>]/g, '') // Remove angle brackets
      .replace(/javascript:/gi, '') // Remove javascript: protocol
      .replace(/on\w+\s*=/gi, '') // Remove event handlers
      .trim();
  }
}

/**
 * Rate limiting for input validation
 */
export class ValidationRateLimiter {
  private static attempts = new Map<string, { count: number; resetTime: number }>();
  
  static isAllowed(identifier: string, maxAttempts = 10, windowMs = 60000): boolean {
    const now = Date.now();
    const attempt = this.attempts.get(identifier);
    
    if (!attempt || now > attempt.resetTime) {
      this.attempts.set(identifier, { count: 1, resetTime: now + windowMs });
      return true;
    }
    
    if (attempt.count >= maxAttempts) {
      return false;
    }
    
    attempt.count++;
    return true;
  }
  
  static reset(identifier: string): void {
    this.attempts.delete(identifier);
  }
}

/**
 * Secure form validation decorator
 */
export function validateForm<T>(
  formData: Record<string, any>,
  validators: Record<keyof T, (value: any) => ValidationResult>
): { isValid: boolean; errors: Record<string, string[]>; sanitizedData: Partial<T> } {
  const errors: Record<string, string[]> = {};
  const sanitizedData: Partial<T> = {};
  let isValid = true;
  
  for (const [field, validator] of Object.entries(validators)) {
    const value = formData[field];
    const result = validator(value);
    
    if (!result.isValid) {
      errors[field] = result.errors;
      isValid = false;
    } else if (result.sanitizedValue !== undefined) {
      (sanitizedData as any)[field] = result.sanitizedValue;
    }
  }
  
  return { isValid, errors, sanitizedData };
}