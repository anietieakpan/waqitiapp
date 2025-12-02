export enum NotificationType {
  TRANSACTION = 'TRANSACTION',
  SECURITY = 'SECURITY',
  ACCOUNT = 'ACCOUNT',
  PAYMENT_REQUEST = 'PAYMENT_REQUEST',
  SYSTEM = 'SYSTEM',
  PROMOTION = 'PROMOTION',
  KYC = 'KYC',
  COMPLIANCE = 'COMPLIANCE',
}

export enum NotificationPriority {
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
}

export enum NotificationChannel {
  IN_APP = 'IN_APP',
  EMAIL = 'EMAIL',
  SMS = 'SMS',
  PUSH = 'PUSH',
}

export interface Notification {
  id: string;
  userId: string;
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  message: string;
  read: boolean;
  readAt?: string;
  data?: Record<string, any>;
  actionUrl?: string;
  actionText?: string;
  channels: NotificationChannel[];
  createdAt: string;
  expiresAt?: string;
}

export interface NotificationPreferences {
  userId: string;
  email: {
    enabled: boolean;
    transactions: boolean;
    security: boolean;
    marketing: boolean;
    paymentRequests: boolean;
    accountUpdates: boolean;
  };
  sms: {
    enabled: boolean;
    transactions: boolean;
    security: boolean;
    paymentRequests: boolean;
    accountUpdates: boolean;
  };
  push: {
    enabled: boolean;
    transactions: boolean;
    security: boolean;
    marketing: boolean;
    paymentRequests: boolean;
    accountUpdates: boolean;
  };
  inApp: {
    enabled: boolean;
    transactions: boolean;
    security: boolean;
    marketing: boolean;
    paymentRequests: boolean;
    accountUpdates: boolean;
    systemNotifications: boolean;
  };
  quietHours: {
    enabled: boolean;
    startTime: string; // HH:mm format
    endTime: string; // HH:mm format
    timezone: string;
  };
}

export interface NotificationStats {
  totalCount: number;
  unreadCount: number;
  byType: {
    type: NotificationType;
    count: number;
    unread: number;
  }[];
  lastRead?: string;
}