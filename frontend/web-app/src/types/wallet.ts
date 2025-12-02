export enum WalletType {
  PERSONAL = 'PERSONAL',
  BUSINESS = 'BUSINESS',
  SAVINGS = 'SAVINGS',
}

export enum WalletStatus {
  ACTIVE = 'ACTIVE',
  SUSPENDED = 'SUSPENDED',
  CLOSED = 'CLOSED',
  FROZEN = 'FROZEN',
}

export enum TransactionType {
  CREDIT = 'CREDIT',
  DEBIT = 'DEBIT',
  TRANSFER = 'TRANSFER',
  DEPOSIT = 'DEPOSIT',
  WITHDRAWAL = 'WITHDRAWAL',
  FEE = 'FEE',
  REFUND = 'REFUND',
}

export enum TransactionStatus {
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
  REVERSED = 'REVERSED',
}

export interface Wallet {
  id: string;
  userId: string;
  type: WalletType;
  currency: string;
  name?: string;
  status: WalletStatus;
  isPrimary: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WalletBalance {
  walletId: string;
  currency: string;
  currentBalance: number;
  availableBalance: number;
  pendingBalance: number;
  frozenBalance: number;
  monthlySpent: number;
  monthlyReceived: number;
  lastUpdated: string;
}

export interface Transaction {
  id: string;
  walletId: string;
  type: TransactionType;
  amount: number;
  currency: string;
  status: TransactionStatus;
  description: string;
  reference: string;
  fromWallet?: string;
  toWallet?: string;
  fromUser?: {
    id: string;
    name: string;
    email: string;
    phoneNumber: string;
  };
  toUser?: {
    id: string;
    name: string;
    email: string;
    phoneNumber: string;
  };
  metadata?: Record<string, any>;
  fees?: number;
  exchangeRate?: number;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

export interface WalletLimit {
  walletId: string;
  dailyTransferLimit: number;
  monthlyTransferLimit: number;
  singleTransactionLimit: number;
  dailyWithdrawalLimit: number;
  monthlyWithdrawalLimit: number;
  currentDailyUsage: number;
  currentMonthlyUsage: number;
  currentDailyWithdrawal: number;
  currentMonthlyWithdrawal: number;
}

export interface CreateWalletRequest {
  type: WalletType;
  currency: string;
  name?: string;
}

export interface AddMoneyRequest {
  amount: number;
  paymentMethodId: string;
  description?: string;
}

export interface WithdrawMoneyRequest {
  amount: number;
  bankAccountId: string;
  description?: string;
}

export interface TransferMoneyRequest {
  fromWalletId: string;
  toWalletId?: string;
  toUserId?: string;
  toEmail?: string;
  toPhoneNumber?: string;
  amount: number;
  currency: string;
  description?: string;
  reference?: string;
}

export interface PaymentMethod {
  id: string;
  type: 'CARD' | 'BANK_ACCOUNT' | 'MOBILE_MONEY';
  lastFour: string;
  brand?: string;
  bankName?: string;
  isDefault: boolean;
  expiryMonth?: number;
  expiryYear?: number;
}

export interface BankAccount {
  id: string;
  accountNumber: string;
  accountName: string;
  bankName: string;
  bankCode: string;
  isVerified: boolean;
  isDefault: boolean;
}

export type Currency = 'USD' | 'EUR' | 'GBP' | 'JPY' | 'BTC' | 'ETH' | 'USDC';

export interface SecuritySettings {
  mfaEnabled: boolean;
  biometricEnabled: boolean;
  transactionNotifications: boolean;
  loginNotifications: boolean;
  withdrawalLimits: boolean;
  suspiciousActivityAlerts: boolean;
  deviceTrustLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  lastSecurityReview: string;
  trustedDevices: Array<{
    id: string;
    name: string;
    type: string;
    lastUsed: string;
    trusted: boolean;
  }>;
}

export interface Goal {
  id: string;
  name: string;
  description?: string;
  targetAmount: number;
  currentAmount: number;
  currency: string;
  targetDate: string;
  category: string;
  isActive: boolean;
  autoSaveEnabled: boolean;
  autoSaveAmount?: number;
  autoSaveFrequency?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  createdAt: string;
  updatedAt: string;
}

export interface Contact {
  id: string;
  userId: string;
  name: string;
  username: string;
  email?: string;
  phone?: string;
  avatar?: string;
  isFavorite: boolean;
  lastPaymentDate?: string;
  totalTransactions: number;
  totalAmountSent: number;
  totalAmountReceived: number;
  createdAt: string;
}

export interface SpendingInsight {
  category: string;
  amount: number;
  currency: string;
  transactionCount: number;
  averageAmount: number;
  percentage: number;
  change: number;
  period: 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';
}

export interface WalletAnalytics {
  totalBalance: number;
  totalIncome: number;
  totalExpenses: number;
  netIncome: number;
  monthlySpending: number[];
  spendingByCategory: Record<string, number>;
  topMerchants: Array<{
    name: string;
    amount: number;
    transactionCount: number;
  }>;
  monthlyTrends: Array<{
    month: string;
    income: number;
    expenses: number;
    balance: number;
  }>;
  savingsRate: number;
  averageDailySpending: number;
  largestExpense: Transaction;
  mostFrequentCategory: string;
}

export interface EnhancedWalletBalance extends WalletBalance {
  amount: number;
  available: number;
  pending: number;
  frozen: number;
  reserved: number;
  change24h?: number;
  change7d?: number;
  change30d?: number;
  limits?: WalletLimit;
  isFrozen: boolean;
  isDefault: boolean;
  interestRate?: number;
  stakingRewards?: number;
  lastUpdated: string;
}