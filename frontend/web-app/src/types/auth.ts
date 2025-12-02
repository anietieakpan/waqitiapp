export interface RegisterRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  email: string;
  phoneNumber: string;
  address: {
    street: string;
    city: string;
    postalCode: string;
    country: string;
  };
  password: string;
}

export interface RegisterResponse {
  success: boolean;
  message: string;
  userId?: string;
  verificationRequired?: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
  rememberMe?: boolean;
  mfaCode?: string;
}

export interface LoginResponse {
  success: boolean;
  accessToken: string;
  refreshToken: string;
  user: User;
  requiresMfa?: boolean;
  mfaMethod?: 'totp' | 'sms' | 'email';
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  avatar?: string;
  verified: boolean;
  kycStatus: 'pending' | 'approved' | 'rejected' | 'not_started';
  mfaEnabled: boolean;
  createdAt: string;
  lastLoginAt?: string;
}

export interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  loading: boolean;
  error: string | null;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetResponse {
  success: boolean;
  message: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface MfaSetupRequest {
  method: 'totp' | 'sms' | 'email';
}

export interface MfaSetupResponse {
  success: boolean;
  qrCode?: string;
  backupCodes?: string[];
  secret?: string;
}