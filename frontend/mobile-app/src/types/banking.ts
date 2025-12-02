/**
 * Banking Types
 * Type definitions for banking-related functionality
 */

// Check deposit types
export interface CheckDepositRequest {
  sessionId: string;
  frontImage: string;
  backImage: string;
  amount: number;
  description?: string;
  accountId: string;
  userId: string;
  timestamp: string;
}

export interface CheckDepositResponse {
  success: boolean;
  depositId: string;
  status: CheckDepositStatus;
  estimatedAvailability?: string;
  message?: string;
}

export enum CheckDepositStatus {
  SUBMITTED = 'submitted',
  PROCESSING = 'processing',
  APPROVED = 'approved',
  AVAILABLE = 'available',
  REJECTED = 'rejected',
  ON_HOLD = 'on_hold',
}

export interface CheckProcessingStep {
  id: string;
  name: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  completedAt?: string;
  description: string;
  estimatedDuration?: string;
}

// Check validation types
export interface CheckValidationRules {
  minAmount: number;
  maxAmount: number;
  dailyLimit: number;
  monthlyLimit: number;
  requiresAuth: boolean;
  authThreshold: number;
}

export interface CheckImageQuality {
  brightness: number; // 0-100
  contrast: number; // 0-100
  sharpness: number; // 0-100
  overall: 'poor' | 'fair' | 'good' | 'excellent';
}

export interface CheckImageMetadata {
  width: number;
  height: number;
  fileSize: number;
  format: string;
  quality: CheckImageQuality;
  timestamp: string;
  location?: {
    latitude: number;
    longitude: number;
  };
}

// Account types for deposits
export interface DepositAccount {
  id: string;
  name: string;
  type: 'checking' | 'savings' | 'money_market';
  accountNumber: string;
  routingNumber: string;
  balance: number;
  currency: string;
  isActive: boolean;
  allowsCheckDeposits: boolean;
}

// Authentication types for check deposits
export interface CheckDepositAuthConfig {
  requireBiometric: boolean;
  requirePin: boolean;
  authThreshold: number; // Amount above which auth is required
  maxRetries: number;
  lockoutDuration: number; // Minutes
}

// Fraud detection types
export interface FraudCheckResult {
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  score: number; // 0-100
  flags: FraudFlag[];
  requiresManualReview: boolean;
  blockedReasons?: string[];
}

export interface FraudFlag {
  type: 'duplicate' | 'suspicious_amount' | 'invalid_micr' | 'image_quality' | 'velocity';
  severity: 'low' | 'medium' | 'high';
  description: string;
}

// Check image capture types
export interface CaptureSessionData {
  sessionId: string;
  frontImage?: CapturedImage;
  backImage?: CapturedImage;
  currentSide: 'front' | 'back';
  startedAt: string;
  lastModified: string;
}

export interface CapturedImage {
  uri: string;
  width: number;
  height: number;
  fileSize: number;
  quality: CheckImageQuality;
  metadata: CheckImageMetadata;
  isOptimized: boolean;
  thumbnailUri?: string;
}

// Error types
export interface CheckDepositError {
  code: string;
  message: string;
  details?: Record<string, any>;
  timestamp: string;
  recoverable: boolean;
}

// API response types
export interface CheckDepositApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: CheckDepositError;
  timestamp: string;
  requestId: string;
}

// Settings types
export interface CheckDepositSettings {
  autoCapture: boolean;
  imageQuality: 'standard' | 'high' | 'maximum';
  compressionLevel: number; // 0-100
  enableImageEnhancement: boolean;
  saveOriginalImages: boolean;
  enableFraudDetection: boolean;
  authSettings: CheckDepositAuthConfig;
}

// History types
export interface CheckDepositHistoryItem {
  id: string;
  amount: number;
  status: CheckDepositStatus;
  submittedAt: string;
  completedAt?: string;
  account: DepositAccount;
  thumbnailUri?: string;
  errorMessage?: string;
}

export interface CheckDepositHistory {
  items: CheckDepositHistoryItem[];
  totalCount: number;
  hasMore: boolean;
  lastUpdated: string;
}

// Limits and restrictions
export interface CheckDepositLimits {
  perCheck: {
    min: number;
    max: number;
  };
  daily: {
    amount: number;
    count: number;
  };
  monthly: {
    amount: number;
    count: number;
  };
  remaining: {
    dailyAmount: number;
    dailyCount: number;
    monthlyAmount: number;
    monthlyCount: number;
  };
}

// Navigation parameter types
export interface CheckDepositNavigationParams {
  CheckCapture: {
    resumeSession?: CaptureSessionData;
  };
  CheckPreview: {
    imageUri: string;
    side: 'front' | 'back';
    session: CaptureSessionData;
  };
  CheckDepositStatus: {
    depositId: string;
    amount?: string;
    account?: DepositAccount;
    estimatedAvailability?: string;
  };
}

// Event types for analytics
export interface CheckDepositEvent {
  type: 'capture_started' | 'capture_completed' | 'preview_viewed' | 'deposit_submitted' | 'deposit_completed' | 'error_occurred';
  sessionId: string;
  timestamp: string;
  metadata?: Record<string, any>;
}

export default {
  CheckDepositStatus,
  // Export all types for easy access
};