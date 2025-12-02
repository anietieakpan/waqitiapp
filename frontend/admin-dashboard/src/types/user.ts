export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING' | 'INACTIVE';

export type KYCStatus = 'VERIFIED' | 'PENDING' | 'REJECTED' | 'NOT_STARTED';

export type UserRole = 'USER' | 'MERCHANT' | 'ADMIN' | 'SUPPORT';

export interface User {
  id: string;
  userId: string;
  name: string;
  email: string;
  phoneNumber?: string;
  avatarUrl?: string;
  status: UserStatus;
  kycStatus: KYCStatus;
  role: UserRole;
  balance: number;
  transactionCount: number;
  joinDate: string;
  lastActive?: string;
  country: string;
  flags: string[];
  riskScore: number;
  mfaEnabled: boolean;
  emailVerified: boolean;
  phoneVerified: boolean;
  metadata: Record<string, any>;
}

export interface UserFilters {
  search: string;
  status: string;
  kycStatus: string;
  role: string;
  dateFrom: Date | null;
  dateTo: Date | null;
  country: string;
}

export interface UserStatistics {
  totalUsers: number;
  activeUsers: number;
  userGrowth: number;
  kycPending: number;
  suspendedUsers: number;
  newUsersToday: number;
  mfaAdoptionRate: number;
  verificationRate: number;
}

export interface UserActivity {
  id: string;
  userId: string;
  type: string;
  description: string;
  timestamp: string;
  ipAddress: string;
  userAgent: string;
  location: string;
  riskScore: number;
  metadata: Record<string, any>;
}

export interface UserProfile {
  personalInfo: {
    firstName: string;
    lastName: string;
    dateOfBirth: string;
    nationality: string;
    address: {
      street: string;
      city: string;
      state: string;
      country: string;
      zipCode: string;
    };
  };
  contactInfo: {
    email: string;
    phoneNumber: string;
    alternateEmail?: string;
  };
  identification: {
    type: string;
    number: string;
    expiryDate: string;
    issuingCountry: string;
  };
  financialInfo: {
    occupation: string;
    sourceOfFunds: string;
    expectedTransactionVolume: string;
    bankAccount?: {
      accountNumber: string;
      routingNumber: string;
      bankName: string;
    };
  };
}

export interface KYCDocument {
  id: string;
  type: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED';
  uploadDate: string;
  reviewDate?: string;
  reviewNotes?: string;
  documentUrl: string;
  metadata: Record<string, any>;
}

export interface UserPermission {
  id: string;
  name: string;
  description: string;
  category: string;
  granted: boolean;
}

export interface UserRole {
  id: string;
  name: string;
  description: string;
  permissions: string[];
  userCount: number;
  isSystem: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserSession {
  id: string;
  userId: string;
  ipAddress: string;
  userAgent: string;
  location: string;
  loginTime: string;
  lastActivity: string;
  isActive: boolean;
  deviceInfo: {
    type: string;
    os: string;
    browser: string;
  };
}

export interface UserFlag {
  id: string;
  type: string;
  reason: string;
  createdAt: string;
  createdBy: string;
  isActive: boolean;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
}

export interface UserSecurityEvent {
  id: string;
  userId: string;
  type: string;
  description: string;
  timestamp: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  ipAddress: string;
  userAgent: string;
  location: string;
  resolved: boolean;
  metadata: Record<string, any>;
}