/**
 * Biometric Authentication Types and Interfaces
 * Provides type definitions for biometric authentication functionality
 */

import { BiometryType } from 'react-native-biometrics';

// Enums for various biometric authentication states and types
export enum BiometricStatus {
  NOT_AVAILABLE = 'NOT_AVAILABLE',
  NOT_ENROLLED = 'NOT_ENROLLED',
  AVAILABLE = 'AVAILABLE',
  DISABLED = 'DISABLED',
  TEMPORARILY_LOCKED = 'TEMPORARILY_LOCKED',
  PERMANENTLY_LOCKED = 'PERMANENTLY_LOCKED',
  UNKNOWN = 'UNKNOWN'
}

export enum BiometricAuthError {
  HARDWARE_NOT_AVAILABLE = 'HARDWARE_NOT_AVAILABLE',
  NOT_ENROLLED = 'NOT_ENROLLED',
  USER_CANCELLED = 'USER_CANCELLED',
  AUTHENTICATION_FAILED = 'AUTHENTICATION_FAILED',
  TEMPORARILY_LOCKED = 'TEMPORARILY_LOCKED',
  PERMANENTLY_LOCKED = 'PERMANENTLY_LOCKED',
  SYSTEM_ERROR = 'SYSTEM_ERROR',
  NETWORK_ERROR = 'NETWORK_ERROR',
  TOKEN_EXPIRED = 'TOKEN_EXPIRED',
  DEVICE_NOT_TRUSTED = 'DEVICE_NOT_TRUSTED',
  UNKNOWN_ERROR = 'UNKNOWN_ERROR'
}

export enum SecurityLevel {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL'
}

export enum AuthenticationMethod {
  FINGERPRINT = 'FINGERPRINT',
  FACE_ID = 'FACE_ID',
  IRIS = 'IRIS',
  VOICE = 'VOICE',
  PIN = 'PIN',
  PASSWORD = 'PASSWORD',
  MFA = 'MFA'
}

// Core interfaces for biometric functionality
export interface BiometricCapabilities {
  isAvailable: boolean;
  biometryType: BiometryType | null;
  supportedTypes: BiometryType[];
  hasHardware: boolean;
  isEnrolled: boolean;
  securityLevel: SecurityLevel;
  canStoreKeys: boolean;
  supportsStrongBox: boolean;
}

export interface BiometricPromptConfig {
  title: string;
  subtitle?: string;
  description?: string;
  fallbackTitle?: string;
  negativeButtonText?: string;
  allowDeviceCredentials?: boolean;
  requireConfirmation?: boolean;
  deviceCredentialTitle?: string;
  deviceCredentialSubtitle?: string;
  deviceCredentialDescription?: string;
}

export interface BiometricAuthResult {
  success: boolean;
  signature?: string;
  publicKey?: string;
  error?: BiometricAuthError;
  errorMessage?: string;
  biometryType?: BiometryType;
  timestamp?: number;
  deviceFingerprint?: string;
}

export interface BiometricToken {
  token: string;
  signature: string;
  publicKey: string;
  expiresAt: number;
  issuedAt: number;
  deviceFingerprint: string;
  userId: string;
  biometryType: BiometryType;
  securityLevel: SecurityLevel;
}

export interface StoredBiometricData {
  userId: string;
  publicKey: string;
  keyAlias: string;
  biometryType: BiometryType;
  enrolledAt: number;
  lastUsedAt?: number;
  deviceFingerprint: string;
  isActive: boolean;
  failureCount: number;
  securityLevel: SecurityLevel;
}

export interface DeviceFingerprint {
  deviceId: string;
  platform: string;
  osVersion: string;
  appVersion: string;
  manufacturer: string;
  model: string;
  buildNumber: string;
  bundleId: string;
  carrier?: string;
  timeZone: string;
  locale: string;
  screenDimensions: {
    width: number;
    height: number;
    scale: number;
  };
  hardwareFeatures: string[];
  securityFeatures: string[];
  installationId: string;
  firstInstallTime: number;
  lastUpdateTime: number;
  isRooted: boolean;
  isEmulator: boolean;
  hash: string;
}

export interface SecurityAssessment {
  deviceTrustScore: number;
  riskLevel: SecurityLevel;
  threats: string[];
  recommendations: string[];
  biometricIntegrity: boolean;
  deviceIntegrity: boolean;
  appIntegrity: boolean;
  networkSecurity: boolean;
  timestamp: number;
}

export interface BiometricRegistrationRequest {
  userId: string;
  publicKey: string;
  signature: string;
  biometryType: BiometryType;
  deviceFingerprint: DeviceFingerprint;
  securityAssessment: SecurityAssessment;
}

export interface BiometricAuthenticationRequest {
  userId: string;
  challenge: string;
  signature: string;
  biometryType: BiometryType;
  deviceFingerprint: DeviceFingerprint;
  timestamp: number;
}

export interface BiometricVerificationResponse {
  verified: boolean;
  token?: string;
  expiresIn?: number;
  securityLevel: SecurityLevel;
  riskAssessment?: {
    score: number;
    factors: string[];
    recommendation: 'APPROVE' | 'REVIEW' | 'DENY';
  };
  deviceTrusted: boolean;
  message?: string;
}

export interface BiometricFallbackOptions {
  allowPin: boolean;
  allowPassword: boolean;
  allowMFA: boolean;
  maxFallbackAttempts: number;
  lockoutDuration: number;
  emergencyContact?: string;
}

export interface BiometricSettings {
  enabled: boolean;
  biometryType: BiometryType | null;
  fallbackOptions: BiometricFallbackOptions;
  securityLevel: SecurityLevel;
  autoLockEnabled: boolean;
  autoLockDuration: number;
  requireReauthentication: boolean;
  reauthenticationInterval: number;
  allowScreenshots: boolean;
  logBiometricEvents: boolean;
}

export interface BiometricEvent {
  eventId: string;
  userId: string;
  eventType: 'REGISTRATION' | 'AUTHENTICATION' | 'FAILURE' | 'DISABLED' | 'ENABLED';
  biometryType?: BiometryType;
  success: boolean;
  errorCode?: string;
  deviceFingerprint: string;
  timestamp: number;
  ipAddress?: string;
  userAgent?: string;
  location?: {
    latitude: number;
    longitude: number;
    accuracy: number;
  };
  additionalData?: Record<string, any>;
}

// Service interface definitions
export interface IBiometricService {
  checkAvailability(): Promise<BiometricCapabilities>;
  generateKeyPair(keyAlias: string): Promise<{ publicKey: string; keyAlias: string }>;
  authenticate(promptConfig: BiometricPromptConfig, challenge: string): Promise<BiometricAuthResult>;
  deleteKey(keyAlias: string): Promise<boolean>;
  isKeyExists(keyAlias: string): Promise<boolean>;
}

export interface ISecureStorageService {
  storeBiometricData(data: StoredBiometricData): Promise<void>;
  getBiometricData(userId: string): Promise<StoredBiometricData | null>;
  removeBiometricData(userId: string): Promise<void>;
  storeToken(token: BiometricToken): Promise<void>;
  getToken(userId: string): Promise<BiometricToken | null>;
  removeToken(userId: string): Promise<void>;
  clearAllData(): Promise<void>;
}

export interface IDeviceFingerprintService {
  generateFingerprint(): Promise<DeviceFingerprint>;
  validateFingerprint(stored: DeviceFingerprint): Promise<boolean>;
  assessSecurity(): Promise<SecurityAssessment>;
  checkDeviceIntegrity(): Promise<boolean>;
}

export interface ISecurityServiceClient {
  registerBiometric(request: BiometricRegistrationRequest): Promise<{ success: boolean; keyId: string }>;
  authenticateBiometric(request: BiometricAuthenticationRequest): Promise<BiometricVerificationResponse>;
  verifyDevice(deviceFingerprint: DeviceFingerprint): Promise<{ trusted: boolean; score: number }>;
  reportSecurityEvent(event: BiometricEvent): Promise<void>;
  getSecuritySettings(userId: string): Promise<BiometricSettings>;
  updateSecuritySettings(userId: string, settings: Partial<BiometricSettings>): Promise<void>;
}

export interface IBiometricAuthService {
  initialize(): Promise<void>;
  setupBiometric(userId: string, promptConfig?: BiometricPromptConfig): Promise<BiometricAuthResult>;
  authenticate(userId: string, promptConfig?: BiometricPromptConfig): Promise<BiometricAuthResult>;
  disableBiometric(userId: string): Promise<boolean>;
  isSetup(userId: string): Promise<boolean>;
  getCapabilities(): Promise<BiometricCapabilities>;
  getSettings(userId: string): Promise<BiometricSettings>;
  updateSettings(userId: string, settings: Partial<BiometricSettings>): Promise<void>;
  handleFallback(userId: string, method: AuthenticationMethod): Promise<BiometricAuthResult>;
  checkDeviceSecurity(): Promise<SecurityAssessment>;
  logout(userId: string): Promise<void>;
}

// Configuration interfaces
export interface BiometricConfig {
  keyAlias: string;
  storageKey: string;
  challengeLength: number;
  tokenExpirationTime: number;
  maxFailureAttempts: number;
  lockoutDuration: number;
  securityLevel: SecurityLevel;
  enableLogging: boolean;
  enableAnalytics: boolean;
  apiEndpoints: {
    register: string;
    authenticate: string;
    verify: string;
    settings: string;
    events: string;
  };
  timeouts: {
    authentication: number;
    network: number;
    keyGeneration: number;
  };
  retry: {
    maxAttempts: number;
    backoffMs: number;
  };
}

// Error handling types
export interface BiometricError extends Error {
  code: BiometricAuthError;
  message: string;
  details?: any;
  recoverable: boolean;
  fallbackAvailable: boolean;
}

// Event types for analytics and monitoring
export type BiometricEventData = {
  eventType: string;
  success: boolean;
  duration: number;
  biometryType?: BiometryType;
  errorCode?: string;
  deviceFingerprint: string;
  securityLevel: SecurityLevel;
  metadata?: Record<string, any>;
};

// Utility types
export type BiometricPromise<T> = Promise<T>;
export type BiometricCallback<T> = (result: T) => void;
export type BiometricErrorHandler = (error: BiometricError) => void;