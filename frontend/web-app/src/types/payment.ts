export interface SendMoneyRequest {
  recipientId?: string;
  recipientEmail: string;
  amount: number;
  currency: string;
  note?: string;
  paymentMethodId?: string;
}

export interface PaymentResponse {
  success: boolean;
  transactionId: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  recipient: string;
  fee: number;
  estimatedDelivery?: string;
  message?: string;
}

export interface RequestMoneyRequest {
  fromUserId?: string;
  fromEmail: string;
  amount: number;
  currency: string;
  note?: string;
  expiresAt?: string;
}

export interface PaymentRequest {
  id: string;
  fromUser: {
    id: string;
    name: string;
    email: string;
    avatar?: string;
  };
  toUser: {
    id: string;
    name: string;
    email: string;
    avatar?: string;
  };
  amount: number;
  currency: string;
  note?: string;
  status: 'pending' | 'paid' | 'declined' | 'expired';
  createdAt: string;
  expiresAt?: string;
  paidAt?: string;
}

export interface Transaction {
  id: string;
  type: 'send' | 'receive' | 'request' | 'deposit' | 'withdrawal';
  status: PaymentStatus;
  amount: number;
  currency: string;
  description: string;
  counterparty: {
    name: string;
    email: string;
    avatar?: string;
  };
  fee: number;
  createdAt: string;
  completedAt?: string;
  failureReason?: string;
  metadata?: Record<string, any>;
}

export enum PaymentStatus {
  PENDING = 'pending',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
  EXPIRED = 'expired',
}

export interface PaymentMethod {
  id: string;
  type: 'bank_account' | 'debit_card' | 'credit_card' | 'wallet';
  name: string;
  last4?: string;
  bankName?: string;
  cardBrand?: string;
  isDefault: boolean;
  verified: boolean;
  expiresAt?: string;
}

export interface PaymentLimit {
  type: 'daily' | 'weekly' | 'monthly' | 'per_transaction';
  current: number;
  limit: number;
  currency: string;
  resetDate?: string;
}

export interface QRCodeData {
  type: 'payment_request' | 'user_profile';
  userId?: string;
  amount?: number;
  currency?: string;
  note?: string;
  expiresAt?: string;
}