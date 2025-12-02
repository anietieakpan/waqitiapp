export interface User {
  id: string;
  username: string;
  email: string;
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
  displayName?: string;
  avatarUrl?: string;
  dateOfBirth?: string;
  
  // Verification status
  emailVerified: boolean;
  phoneVerified: boolean;
  identityVerified: boolean;
  
  // KYC status
  kycStatus: 'not_started' | 'pending' | 'approved' | 'rejected';
  kycSubmittedAt?: string;
  kycApprovedAt?: string;
  
  // Security
  mfaEnabled: boolean;
  mfaMethod?: 'totp' | 'sms' | 'email';
  
  // Account status
  status: 'active' | 'suspended' | 'deactivated' | 'pending';
  accountLevel: 'basic' | 'verified' | 'premium' | 'business';
  
  // Profile information
  bio?: string;
  location?: string;
  website?: string;
  
  // Privacy settings
  profileVisibility: 'public' | 'friends' | 'private';
  transactionVisibility: 'public' | 'friends' | 'private';
  
  // Roles and permissions
  roles: string[];
  permissions: string[];
  
  // Timestamps
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string;
  
  // Statistics
  totalTransactions?: number;
  totalSent?: number;
  totalReceived?: number;
  
  // Limits
  dailyTransactionLimit: number;
  monthlyTransactionLimit: number;
  singleTransactionLimit: number;
  
  // Preferences
  currency: string;
  timezone: string;
  language: string;
  
  // Social
  friendsCount?: number;
  followersCount?: number;
  followingCount?: number;
  
  // Business account specific
  businessName?: string;
  businessType?: string;
  businessRegistrationNumber?: string;
  businessAddress?: Address;
  
  // Compliance
  riskLevel: 'low' | 'medium' | 'high';
  sourceOfFunds?: string;
  occupation?: string;
  
  // Notifications
  notificationPreferences: {
    email: boolean;
    push: boolean;
    sms: boolean;
    marketing: boolean;
  };
}

export interface Address {
  street: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
}

export interface UserProfile extends User {
  // Extended profile information
  recentActivity: UserActivity[];
  mutualFriends?: User[];
  connectionStatus?: 'none' | 'pending' | 'connected' | 'blocked';
}

export interface UserActivity {
  id: string;
  type: 'payment' | 'login' | 'profile_update' | 'verification' | 'security';
  description: string;
  timestamp: string;
  metadata?: Record<string, unknown>;
}

export interface UserSearchResult {
  users: User[];
  totalCount: number;
  hasMore: boolean;
  nextCursor?: string;
}

export interface UserConnection {
  id: string;
  userId: string;
  connectedUserId: string;
  status: 'pending' | 'accepted' | 'blocked';
  createdAt: string;
  updatedAt: string;
}

export interface UserSession {
  id: string;
  userId: string;
  deviceInfo: {
    userAgent: string;
    ipAddress: string;
    location?: string;
    device: string;
    os: string;
    browser: string;
  };
  isActive: boolean;
  createdAt: string;
  lastActivityAt: string;
  expiresAt: string;
}

export interface SecurityEvent {
  id: string;
  userId: string;
  eventType: 'login' | 'logout' | 'password_change' | 'mfa_setup' | 'suspicious_activity';
  details: string;
  ipAddress: string;
  userAgent: string;
  location?: string;
  timestamp: string;
  severity: 'low' | 'medium' | 'high';
}