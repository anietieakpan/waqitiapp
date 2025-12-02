/**
 * Validation Service - Comprehensive form validation and security checks
 * Provides field validation, security scanning, and data sanitization
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Keychain from 'react-native-keychain';
import CryptoJS from 'crypto-js';
import { Alert } from 'react-native';

// Validation rule types
export enum ValidationType {
  REQUIRED = 'required',
  EMAIL = 'email',
  PHONE = 'phone',
  PASSWORD = 'password',
  PIN = 'pin',
  AMOUNT = 'amount',
  ACCOUNT_NUMBER = 'account_number',
  ROUTING_NUMBER = 'routing_number',
  CARD_NUMBER = 'card_number',
  CVV = 'cvv',
  DATE = 'date',
  NAME = 'name',
  ADDRESS = 'address',
  SSN = 'ssn',
  TAX_ID = 'tax_id',
  CUSTOM = 'custom',
}

// Security check types
export enum SecurityCheckType {
  SQL_INJECTION = 'sql_injection',
  XSS = 'xss',
  COMMAND_INJECTION = 'command_injection',
  PATH_TRAVERSAL = 'path_traversal',
  SENSITIVE_DATA = 'sensitive_data',
  PROFANITY = 'profanity',
  RATE_LIMIT = 'rate_limit',
  DUPLICATE = 'duplicate',
  BLACKLIST = 'blacklist',
  FRAUD = 'fraud',
}

// Validation result
export interface ValidationResult {
  isValid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
  sanitizedValue?: any;
  metadata?: Record<string, any>;
}

export interface ValidationError {
  field: string;
  type: ValidationType | SecurityCheckType;
  message: string;
  code: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
}

export interface ValidationWarning {
  field: string;
  type: string;
  message: string;
  suggestion?: string;
}

// Validation rule
export interface ValidationRule {
  type: ValidationType;
  required?: boolean;
  min?: number;
  max?: number;
  pattern?: RegExp;
  custom?: (value: any) => boolean | string;
  message?: string;
  sanitize?: boolean;
  trim?: boolean;
  lowercase?: boolean;
  uppercase?: boolean;
}

// Form validation schema
export interface ValidationSchema {
  [field: string]: ValidationRule | ValidationRule[];
}

// Security configuration
export interface SecurityConfig {
  enableSQLInjectionCheck?: boolean;
  enableXSSCheck?: boolean;
  enableCommandInjectionCheck?: boolean;
  enablePathTraversalCheck?: boolean;
  enableSensitiveDataCheck?: boolean;
  enableProfanityCheck?: boolean;
  enableRateLimitCheck?: boolean;
  enableDuplicateCheck?: boolean;
  enableBlacklistCheck?: boolean;
  enableFraudCheck?: boolean;
  maxAttempts?: number;
  blockDuration?: number; // in minutes
}

class ValidationService {
  private static instance: ValidationService;
  private attemptTracker: Map<string, { count: number; lastAttempt: Date }> = new Map();
  private blockedUsers: Map<string, Date> = new Map();
  private securityConfig: SecurityConfig;
  
  // Common patterns
  private patterns = {
    email: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
    phone: /^(\+\d{1,3}[- ]?)?\d{10}$/,
    strongPassword: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/,
    mediumPassword: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[A-Za-z\d@$!%*?&]{8,}$/,
    pin: /^\d{4,6}$/,
    amount: /^\d+(\.\d{1,2})?$/,
    accountNumber: /^\d{8,17}$/,
    routingNumber: /^\d{9}$/,
    cardNumber: /^\d{13,19}$/,
    cvv: /^\d{3,4}$/,
    date: /^\d{4}-\d{2}-\d{2}$/,
    name: /^[a-zA-Z\s'-]{2,50}$/,
    alphanumeric: /^[a-zA-Z0-9]+$/,
    ssn: /^\d{3}-?\d{2}-?\d{4}$/,
    taxId: /^\d{2}-?\d{7}$/,
  };

  // SQL injection patterns
  private sqlInjectionPatterns = [
    /(\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|CREATE|ALTER|EXEC|EXECUTE|SCRIPT|JAVASCRIPT)\b)/gi,
    /(--|;|'|"|\/\*|\*\/|xp_|sp_|<script|<\/script|javascript:|onerror=|onload=)/gi,
    /(\bOR\b\s*\d+\s*=\s*\d+)|(\bAND\b\s*\d+\s*=\s*\d+)/gi,
    /(\bWHERE\b.*\b1\s*=\s*1)|(\bWHERE\b.*\b'1'\s*=\s*'1')/gi,
  ];

  // XSS patterns
  private xssPatterns = [
    /<script[^>]*>.*?<\/script>/gi,
    /<iframe[^>]*>.*?<\/iframe>/gi,
    /javascript:/gi,
    /on\w+\s*=/gi,
    /<img[^>]*onerror\s*=/gi,
    /<svg[^>]*onload\s*=/gi,
  ];

  // Command injection patterns
  private commandInjectionPatterns = [
    /[;&|`$(){}[\]<>]/g,
    /\b(cat|ls|rm|mv|cp|chmod|chown|sudo|su|wget|curl|nc|bash|sh|python|perl|ruby|php)\b/gi,
  ];

  // Path traversal patterns
  private pathTraversalPatterns = [
    /\.\.\//g,
    /\.\.\\/, 
    /%2e%2e%2f/gi,
    /%252e%252e%252f/gi,
    /\.\./g,
  ];

  // Sensitive data patterns
  private sensitiveDataPatterns = {
    creditCard: /\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b/g,
    ssn: /\b\d{3}-?\d{2}-?\d{4}\b/g,
    apiKey: /\b[A-Za-z0-9]{32,}\b/g,
    password: /password\s*[:=]\s*['"]?[^'"\s]+/gi,
  };

  // Profanity list (simplified)
  private profanityList = [
    'badword1', 'badword2', // Add actual profanity words
  ];

  private constructor() {
    this.securityConfig = {
      enableSQLInjectionCheck: true,
      enableXSSCheck: true,
      enableCommandInjectionCheck: true,
      enablePathTraversalCheck: true,
      enableSensitiveDataCheck: true,
      enableProfanityCheck: true,
      enableRateLimitCheck: true,
      enableDuplicateCheck: true,
      enableBlacklistCheck: true,
      enableFraudCheck: true,
      maxAttempts: 5,
      blockDuration: 30, // 30 minutes
    };
  }

  static getInstance(): ValidationService {
    if (!ValidationService.instance) {
      ValidationService.instance = new ValidationService();
    }
    return ValidationService.instance;
  }

  /**
   * Configure security settings
   */
  configureSecuritySettings(config: Partial<SecurityConfig>): void {
    this.securityConfig = { ...this.securityConfig, ...config };
  }

  /**
   * Validate form data against schema
   */
  async validateForm(
    data: Record<string, any>,
    schema: ValidationSchema,
    userId?: string
  ): Promise<ValidationResult> {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];
    const sanitizedData: Record<string, any> = {};

    // Check rate limiting
    if (this.securityConfig.enableRateLimitCheck && userId) {
      const rateLimitError = await this.checkRateLimit(userId);
      if (rateLimitError) {
        errors.push(rateLimitError);
        return { isValid: false, errors, warnings };
      }
    }

    // Validate each field
    for (const [field, rules] of Object.entries(schema)) {
      const value = data[field];
      const rulesArray = Array.isArray(rules) ? rules : [rules];
      
      let sanitizedValue = value;

      for (const rule of rulesArray) {
        // Apply sanitization
        sanitizedValue = this.sanitizeValue(sanitizedValue, rule);

        // Validate field
        const fieldResult = await this.validateField(field, sanitizedValue, rule);
        
        if (fieldResult.errors.length > 0) {
          errors.push(...fieldResult.errors);
        }
        if (fieldResult.warnings.length > 0) {
          warnings.push(...fieldResult.warnings);
        }
        
        if (fieldResult.sanitizedValue !== undefined) {
          sanitizedValue = fieldResult.sanitizedValue;
        }
      }

      // Security checks
      const securityResult = await this.performSecurityChecks(field, sanitizedValue);
      if (securityResult.errors.length > 0) {
        errors.push(...securityResult.errors);
      }
      if (securityResult.warnings.length > 0) {
        warnings.push(...securityResult.warnings);
      }

      sanitizedData[field] = sanitizedValue;
    }

    // Track attempt if validation failed
    if (errors.length > 0 && userId) {
      this.trackFailedAttempt(userId);
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
      sanitizedValue: sanitizedData,
    };
  }

  /**
   * Validate single field
   */
  async validateField(
    field: string,
    value: any,
    rule: ValidationRule
  ): Promise<ValidationResult> {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];
    let sanitizedValue = value;

    // Required check
    if (rule.required && !value) {
      errors.push({
        field,
        type: ValidationType.REQUIRED,
        message: rule.message || `${field} is required`,
        code: 'FIELD_REQUIRED',
        severity: 'medium',
      });
      return { isValid: false, errors, warnings };
    }

    // Skip further validation if not required and empty
    if (!rule.required && !value) {
      return { isValid: true, errors, warnings };
    }

    // Type-specific validation
    switch (rule.type) {
      case ValidationType.EMAIL:
        if (!this.patterns.email.test(value)) {
          errors.push({
            field,
            type: ValidationType.EMAIL,
            message: rule.message || 'Invalid email address',
            code: 'INVALID_EMAIL',
            severity: 'low',
          });
        }
        break;

      case ValidationType.PHONE:
        const cleanPhone = value.replace(/\D/g, '');
        if (!this.patterns.phone.test(cleanPhone)) {
          errors.push({
            field,
            type: ValidationType.PHONE,
            message: rule.message || 'Invalid phone number',
            code: 'INVALID_PHONE',
            severity: 'low',
          });
        }
        sanitizedValue = cleanPhone;
        break;

      case ValidationType.PASSWORD:
        const passwordStrength = this.checkPasswordStrength(value);
        if (passwordStrength.score < 3) {
          errors.push({
            field,
            type: ValidationType.PASSWORD,
            message: rule.message || passwordStrength.feedback,
            code: 'WEAK_PASSWORD',
            severity: passwordStrength.score < 2 ? 'high' : 'medium',
          });
        }
        if (passwordStrength.suggestions.length > 0) {
          warnings.push({
            field,
            type: 'password_suggestion',
            message: 'Password can be stronger',
            suggestion: passwordStrength.suggestions.join(', '),
          });
        }
        break;

      case ValidationType.PIN:
        if (!this.patterns.pin.test(value)) {
          errors.push({
            field,
            type: ValidationType.PIN,
            message: rule.message || 'PIN must be 4-6 digits',
            code: 'INVALID_PIN',
            severity: 'medium',
          });
        }
        // Check for common PINs
        if (this.isCommonPIN(value)) {
          errors.push({
            field,
            type: ValidationType.PIN,
            message: 'This PIN is too common. Please choose a different one.',
            code: 'COMMON_PIN',
            severity: 'high',
          });
        }
        break;

      case ValidationType.AMOUNT:
        const amount = parseFloat(value);
        if (isNaN(amount) || amount <= 0) {
          errors.push({
            field,
            type: ValidationType.AMOUNT,
            message: rule.message || 'Invalid amount',
            code: 'INVALID_AMOUNT',
            severity: 'medium',
          });
        }
        if (rule.min && amount < rule.min) {
          errors.push({
            field,
            type: ValidationType.AMOUNT,
            message: `Amount must be at least ${rule.min}`,
            code: 'AMOUNT_TOO_LOW',
            severity: 'medium',
          });
        }
        if (rule.max && amount > rule.max) {
          errors.push({
            field,
            type: ValidationType.AMOUNT,
            message: `Amount cannot exceed ${rule.max}`,
            code: 'AMOUNT_TOO_HIGH',
            severity: 'medium',
          });
        }
        sanitizedValue = amount.toFixed(2);
        break;

      case ValidationType.CARD_NUMBER:
        const cleanCard = value.replace(/\s/g, '');
        if (!this.patterns.cardNumber.test(cleanCard)) {
          errors.push({
            field,
            type: ValidationType.CARD_NUMBER,
            message: rule.message || 'Invalid card number',
            code: 'INVALID_CARD_NUMBER',
            severity: 'high',
          });
        }
        if (!this.validateLuhn(cleanCard)) {
          errors.push({
            field,
            type: ValidationType.CARD_NUMBER,
            message: 'Invalid card number',
            code: 'FAILED_LUHN_CHECK',
            severity: 'high',
          });
        }
        // Mask card number for storage
        sanitizedValue = this.maskCardNumber(cleanCard);
        break;

      case ValidationType.CVV:
        if (!this.patterns.cvv.test(value)) {
          errors.push({
            field,
            type: ValidationType.CVV,
            message: rule.message || 'Invalid CVV',
            code: 'INVALID_CVV',
            severity: 'high',
          });
        }
        // Never store CVV
        sanitizedValue = null;
        break;

      case ValidationType.SSN:
        const cleanSSN = value.replace(/-/g, '');
        if (!this.patterns.ssn.test(value)) {
          errors.push({
            field,
            type: ValidationType.SSN,
            message: rule.message || 'Invalid SSN format',
            code: 'INVALID_SSN',
            severity: 'critical',
          });
        }
        // Encrypt SSN
        sanitizedValue = await this.encryptSensitiveData(cleanSSN);
        break;

      case ValidationType.NAME:
        if (!this.patterns.name.test(value)) {
          errors.push({
            field,
            type: ValidationType.NAME,
            message: rule.message || 'Invalid name format',
            code: 'INVALID_NAME',
            severity: 'low',
          });
        }
        break;

      case ValidationType.CUSTOM:
        if (rule.custom) {
          const result = rule.custom(value);
          if (typeof result === 'string') {
            errors.push({
              field,
              type: ValidationType.CUSTOM,
              message: result,
              code: 'CUSTOM_VALIDATION_FAILED',
              severity: 'medium',
            });
          } else if (!result) {
            errors.push({
              field,
              type: ValidationType.CUSTOM,
              message: rule.message || 'Validation failed',
              code: 'CUSTOM_VALIDATION_FAILED',
              severity: 'medium',
            });
          }
        }
        break;
    }

    // Length validation
    if (rule.min && value.length < rule.min) {
      errors.push({
        field,
        type: ValidationType.CUSTOM,
        message: `Must be at least ${rule.min} characters`,
        code: 'TOO_SHORT',
        severity: 'low',
      });
    }
    if (rule.max && value.length > rule.max) {
      errors.push({
        field,
        type: ValidationType.CUSTOM,
        message: `Must not exceed ${rule.max} characters`,
        code: 'TOO_LONG',
        severity: 'low',
      });
    }

    // Pattern validation
    if (rule.pattern && !rule.pattern.test(value)) {
      errors.push({
        field,
        type: ValidationType.CUSTOM,
        message: rule.message || 'Invalid format',
        code: 'PATTERN_MISMATCH',
        severity: 'medium',
      });
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
      sanitizedValue,
    };
  }

  /**
   * Perform security checks on field value
   */
  async performSecurityChecks(
    field: string,
    value: any
  ): Promise<ValidationResult> {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];

    if (!value || typeof value !== 'string') {
      return { isValid: true, errors, warnings };
    }

    // SQL Injection check
    if (this.securityConfig.enableSQLInjectionCheck) {
      for (const pattern of this.sqlInjectionPatterns) {
        if (pattern.test(value)) {
          errors.push({
            field,
            type: SecurityCheckType.SQL_INJECTION,
            message: 'Potential security threat detected',
            code: 'SQL_INJECTION_DETECTED',
            severity: 'critical',
          });
          break;
        }
      }
    }

    // XSS check
    if (this.securityConfig.enableXSSCheck) {
      for (const pattern of this.xssPatterns) {
        if (pattern.test(value)) {
          errors.push({
            field,
            type: SecurityCheckType.XSS,
            message: 'Invalid characters detected',
            code: 'XSS_DETECTED',
            severity: 'high',
          });
          break;
        }
      }
    }

    // Command injection check
    if (this.securityConfig.enableCommandInjectionCheck) {
      for (const pattern of this.commandInjectionPatterns) {
        if (pattern.test(value)) {
          warnings.push({
            field,
            type: 'command_injection',
            message: 'Potentially unsafe characters detected',
            suggestion: 'Remove special characters',
          });
          break;
        }
      }
    }

    // Path traversal check
    if (this.securityConfig.enablePathTraversalCheck) {
      for (const pattern of this.pathTraversalPatterns) {
        if (pattern.test(value)) {
          errors.push({
            field,
            type: SecurityCheckType.PATH_TRAVERSAL,
            message: 'Invalid path characters',
            code: 'PATH_TRAVERSAL_DETECTED',
            severity: 'high',
          });
          break;
        }
      }
    }

    // Sensitive data check
    if (this.securityConfig.enableSensitiveDataCheck) {
      for (const [dataType, pattern] of Object.entries(this.sensitiveDataPatterns)) {
        if (pattern.test(value) && field !== dataType) {
          warnings.push({
            field,
            type: 'sensitive_data',
            message: `Potential ${dataType} detected in ${field}`,
            suggestion: 'Avoid entering sensitive information in this field',
          });
        }
      }
    }

    // Profanity check
    if (this.securityConfig.enableProfanityCheck) {
      const lowerValue = value.toLowerCase();
      for (const word of this.profanityList) {
        if (lowerValue.includes(word)) {
          errors.push({
            field,
            type: SecurityCheckType.PROFANITY,
            message: 'Inappropriate content detected',
            code: 'PROFANITY_DETECTED',
            severity: 'medium',
          });
          break;
        }
      }
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Sanitize value based on rule
   */
  private sanitizeValue(value: any, rule: ValidationRule): any {
    if (!value || typeof value !== 'string') return value;

    let sanitized = value;

    if (rule.trim) {
      sanitized = sanitized.trim();
    }

    if (rule.lowercase) {
      sanitized = sanitized.toLowerCase();
    }

    if (rule.uppercase) {
      sanitized = sanitized.toUpperCase();
    }

    if (rule.sanitize) {
      // Remove potential XSS
      sanitized = sanitized.replace(/<[^>]*>/g, '');
      // Remove SQL injection attempts
      sanitized = sanitized.replace(/['";\\]/g, '');
      // Remove control characters
      sanitized = sanitized.replace(/[\x00-\x1F\x7F]/g, '');
    }

    return sanitized;
  }

  /**
   * Check password strength
   */
  private checkPasswordStrength(password: string): {
    score: number;
    feedback: string;
    suggestions: string[];
  } {
    let score = 0;
    const suggestions: string[] = [];

    // Length check
    if (password.length >= 8) score++;
    if (password.length >= 12) score++;
    if (password.length < 8) {
      suggestions.push('Use at least 8 characters');
    }

    // Complexity checks
    if (/[a-z]/.test(password)) score++;
    else suggestions.push('Add lowercase letters');

    if (/[A-Z]/.test(password)) score++;
    else suggestions.push('Add uppercase letters');

    if (/\d/.test(password)) score++;
    else suggestions.push('Add numbers');

    if (/[@$!%*?&]/.test(password)) score++;
    else suggestions.push('Add special characters');

    // Common password check
    if (this.isCommonPassword(password)) {
      score = Math.max(0, score - 2);
      suggestions.push('Avoid common passwords');
    }

    // Sequential characters check
    if (this.hasSequentialCharacters(password)) {
      score = Math.max(0, score - 1);
      suggestions.push('Avoid sequential characters');
    }

    const feedback = 
      score < 2 ? 'Very weak password' :
      score < 3 ? 'Weak password' :
      score < 4 ? 'Fair password' :
      score < 5 ? 'Good password' :
      'Strong password';

    return { score, feedback, suggestions };
  }

  /**
   * Check if password is common
   */
  private isCommonPassword(password: string): boolean {
    const commonPasswords = [
      'password', '123456', '12345678', 'qwerty', 'abc123',
      'password123', 'admin', 'letmein', 'welcome', 'monkey',
      '1234567890', 'password1', '123456789', 'welcome123',
    ];
    
    return commonPasswords.includes(password.toLowerCase());
  }

  /**
   * Check if PIN is common
   */
  private isCommonPIN(pin: string): boolean {
    const commonPINs = [
      '0000', '1111', '1234', '4321', '1212',
      '0000', '2222', '3333', '4444', '5555',
      '6666', '7777', '8888', '9999', '1122',
    ];
    
    return commonPINs.includes(pin);
  }

  /**
   * Check for sequential characters
   */
  private hasSequentialCharacters(value: string): boolean {
    const sequences = [
      'abc', 'bcd', 'cde', 'def', 'efg', 'fgh', 'ghi', 'hij',
      'ijk', 'jkl', 'klm', 'lmn', 'mno', 'nop', 'opq', 'pqr',
      'qrs', 'rst', 'stu', 'tuv', 'uvw', 'vwx', 'wxy', 'xyz',
      '012', '123', '234', '345', '456', '567', '678', '789',
    ];
    
    const lowerValue = value.toLowerCase();
    return sequences.some(seq => lowerValue.includes(seq));
  }

  /**
   * Validate Luhn algorithm (credit card)
   */
  private validateLuhn(value: string): boolean {
    let sum = 0;
    let isEven = false;
    
    for (let i = value.length - 1; i >= 0; i--) {
      let digit = parseInt(value[i], 10);
      
      if (isEven) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      
      sum += digit;
      isEven = !isEven;
    }
    
    return sum % 10 === 0;
  }

  /**
   * Mask card number
   */
  private maskCardNumber(cardNumber: string): string {
    if (cardNumber.length < 8) return cardNumber;
    
    const first4 = cardNumber.slice(0, 4);
    const last4 = cardNumber.slice(-4);
    const masked = '*'.repeat(cardNumber.length - 8);
    
    return `${first4}${masked}${last4}`;
  }

  /**
   * Encrypt sensitive data
   */
  private async encryptSensitiveData(data: string): Promise<string> {
    try {
      // Get or generate encryption key
      const credentials = await Keychain.getInternetCredentials('waqiti_encryption');
      let key = credentials ? credentials.password : '';
      
      if (!key) {
        key = CryptoJS.lib.WordArray.random(256/8).toString();
        await Keychain.setInternetCredentials(
          'waqiti_encryption',
          'key',
          key
        );
      }
      
      // Encrypt data
      const encrypted = CryptoJS.AES.encrypt(data, key).toString();
      return encrypted;
    } catch (error) {
      console.error('Encryption failed:', error);
      throw new Error('Failed to secure sensitive data');
    }
  }

  /**
   * Decrypt sensitive data
   */
  async decryptSensitiveData(encryptedData: string): Promise<string> {
    try {
      const credentials = await Keychain.getInternetCredentials('waqiti_encryption');
      if (!credentials) {
        throw new Error('Decryption key not found');
      }
      
      const decrypted = CryptoJS.AES.decrypt(encryptedData, credentials.password);
      return decrypted.toString(CryptoJS.enc.Utf8);
    } catch (error) {
      console.error('Decryption failed:', error);
      throw new Error('Failed to decrypt data');
    }
  }

  /**
   * Check rate limiting
   */
  private async checkRateLimit(userId: string): Promise<ValidationError | null> {
    // Check if user is blocked
    const blockedUntil = this.blockedUsers.get(userId);
    if (blockedUntil && blockedUntil > new Date()) {
      const minutesRemaining = Math.ceil((blockedUntil.getTime() - Date.now()) / 60000);
      return {
        field: 'general',
        type: SecurityCheckType.RATE_LIMIT,
        message: `Too many attempts. Please try again in ${minutesRemaining} minutes.`,
        code: 'RATE_LIMIT_EXCEEDED',
        severity: 'high',
      };
    }

    // Check attempt count
    const attempts = this.attemptTracker.get(userId);
    if (attempts && attempts.count >= this.securityConfig.maxAttempts!) {
      const timeSinceLastAttempt = Date.now() - attempts.lastAttempt.getTime();
      const blockDurationMs = this.securityConfig.blockDuration! * 60 * 1000;
      
      if (timeSinceLastAttempt < blockDurationMs) {
        // Block user
        const blockUntil = new Date(Date.now() + blockDurationMs);
        this.blockedUsers.set(userId, blockUntil);
        
        return {
          field: 'general',
          type: SecurityCheckType.RATE_LIMIT,
          message: `Account temporarily locked due to multiple failed attempts.`,
          code: 'ACCOUNT_LOCKED',
          severity: 'critical',
        };
      } else {
        // Reset attempts
        this.attemptTracker.delete(userId);
      }
    }

    return null;
  }

  /**
   * Track failed attempt
   */
  private trackFailedAttempt(userId: string): void {
    const attempts = this.attemptTracker.get(userId) || { count: 0, lastAttempt: new Date() };
    attempts.count++;
    attempts.lastAttempt = new Date();
    this.attemptTracker.set(userId, attempts);
  }

  /**
   * Reset user attempts
   */
  resetUserAttempts(userId: string): void {
    this.attemptTracker.delete(userId);
    this.blockedUsers.delete(userId);
  }

  /**
   * Check for duplicate submission
   */
  async checkDuplicateSubmission(
    data: Record<string, any>,
    type: string,
    timeWindowMs: number = 5000
  ): Promise<boolean> {
    const hash = CryptoJS.MD5(JSON.stringify({ data, type })).toString();
    const key = `@duplicate_check_${hash}`;
    
    try {
      const lastSubmission = await AsyncStorage.getItem(key);
      if (lastSubmission) {
        const lastTime = parseInt(lastSubmission, 10);
        if (Date.now() - lastTime < timeWindowMs) {
          return true; // Duplicate detected
        }
      }
      
      await AsyncStorage.setItem(key, Date.now().toString());
      
      // Clean old entries
      setTimeout(() => {
        AsyncStorage.removeItem(key);
      }, timeWindowMs);
      
      return false;
    } catch (error) {
      console.error('Duplicate check failed:', error);
      return false;
    }
  }

  /**
   * Validate file upload
   */
  validateFileUpload(
    file: { name: string; size: number; type: string },
    config: {
      maxSize?: number; // in bytes
      allowedTypes?: string[];
      allowedExtensions?: string[];
    }
  ): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];

    // Check file size
    if (config.maxSize && file.size > config.maxSize) {
      errors.push({
        field: 'file',
        type: ValidationType.CUSTOM,
        message: `File size exceeds ${config.maxSize / 1024 / 1024}MB limit`,
        code: 'FILE_TOO_LARGE',
        severity: 'medium',
      });
    }

    // Check file type
    if (config.allowedTypes && !config.allowedTypes.includes(file.type)) {
      errors.push({
        field: 'file',
        type: ValidationType.CUSTOM,
        message: 'File type not allowed',
        code: 'INVALID_FILE_TYPE',
        severity: 'medium',
      });
    }

    // Check file extension
    if (config.allowedExtensions) {
      const extension = file.name.split('.').pop()?.toLowerCase();
      if (!extension || !config.allowedExtensions.includes(extension)) {
        errors.push({
          field: 'file',
          type: ValidationType.CUSTOM,
          message: 'File extension not allowed',
          code: 'INVALID_FILE_EXTENSION',
          severity: 'medium',
        });
      }
    }

    // Check for potentially dangerous extensions
    const dangerousExtensions = ['exe', 'bat', 'cmd', 'sh', 'ps1', 'vbs', 'js'];
    const fileExtension = file.name.split('.').pop()?.toLowerCase();
    if (fileExtension && dangerousExtensions.includes(fileExtension)) {
      errors.push({
        field: 'file',
        type: SecurityCheckType.BLACKLIST,
        message: 'Potentially dangerous file type',
        code: 'DANGEROUS_FILE',
        severity: 'critical',
      });
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Create validation schema for common forms
   */
  static createSchema = {
    login: (): ValidationSchema => ({
      email: {
        type: ValidationType.EMAIL,
        required: true,
        lowercase: true,
        trim: true,
      },
      password: {
        type: ValidationType.PASSWORD,
        required: true,
      },
    }),

    registration: (): ValidationSchema => ({
      firstName: {
        type: ValidationType.NAME,
        required: true,
        trim: true,
      },
      lastName: {
        type: ValidationType.NAME,
        required: true,
        trim: true,
      },
      email: {
        type: ValidationType.EMAIL,
        required: true,
        lowercase: true,
        trim: true,
      },
      phone: {
        type: ValidationType.PHONE,
        required: true,
      },
      password: {
        type: ValidationType.PASSWORD,
        required: true,
      },
      confirmPassword: {
        type: ValidationType.CUSTOM,
        required: true,
        custom: (value, data) => value === data.password || 'Passwords do not match',
      },
    }),

    payment: (): ValidationSchema => ({
      amount: {
        type: ValidationType.AMOUNT,
        required: true,
        min: 0.01,
        max: 10000,
      },
      recipientId: {
        type: ValidationType.CUSTOM,
        required: true,
        pattern: /^[a-zA-Z0-9-]+$/,
      },
      description: {
        type: ValidationType.CUSTOM,
        required: false,
        max: 200,
        sanitize: true,
      },
    }),

    bankAccount: (): ValidationSchema => ({
      accountNumber: {
        type: ValidationType.ACCOUNT_NUMBER,
        required: true,
      },
      routingNumber: {
        type: ValidationType.ROUTING_NUMBER,
        required: true,
      },
      accountHolderName: {
        type: ValidationType.NAME,
        required: true,
        trim: true,
      },
    }),

    creditCard: (): ValidationSchema => ({
      cardNumber: {
        type: ValidationType.CARD_NUMBER,
        required: true,
      },
      cardholderName: {
        type: ValidationType.NAME,
        required: true,
        trim: true,
      },
      expiryMonth: {
        type: ValidationType.CUSTOM,
        required: true,
        pattern: /^(0[1-9]|1[0-2])$/,
      },
      expiryYear: {
        type: ValidationType.CUSTOM,
        required: true,
        pattern: /^20\d{2}$/,
      },
      cvv: {
        type: ValidationType.CVV,
        required: true,
      },
    }),

    address: (): ValidationSchema => ({
      street: {
        type: ValidationType.ADDRESS,
        required: true,
        trim: true,
        sanitize: true,
      },
      city: {
        type: ValidationType.NAME,
        required: true,
        trim: true,
      },
      state: {
        type: ValidationType.CUSTOM,
        required: true,
        pattern: /^[A-Z]{2}$/,
        uppercase: true,
      },
      zipCode: {
        type: ValidationType.CUSTOM,
        required: true,
        pattern: /^\d{5}(-\d{4})?$/,
      },
      country: {
        type: ValidationType.CUSTOM,
        required: true,
        pattern: /^[A-Z]{2}$/,
        uppercase: true,
      },
    }),
  };
}

export default ValidationService.getInstance();