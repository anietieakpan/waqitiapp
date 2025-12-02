export type TransactionStatus = 'COMPLETED' | 'PENDING' | 'FAILED' | 'BLOCKED';

export type TransactionType = 'CREDIT' | 'DEBIT' | 'TRANSFER' | 'PAYMENT' | 'REFUND';

export interface Transaction {
  id: string;
  timestamp: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: number;
  currency: string;
  senderId: string;
  senderName: string;
  recipientId: string;
  recipientName: string;
  description: string;
  reference: string;
  fees: number;
  exchangeRate?: number;
  riskScore: number;
  flags: string[];
  paymentMethod: string;
  location: string;
  ipAddress: string;
  deviceFingerprint: string;
  metadata: Record<string, any>;
}

export interface TransactionFilters {
  search: string;
  status: string;
  type: string;
  dateFrom: Date | null;
  dateTo: Date | null;
  amountMin: string;
  amountMax: string;
  flagged: boolean;
}

export interface TransactionAnalytics {
  totalVolume: number;
  totalCount: number;
  averageAmount: number;
  pendingCount: number;
  flaggedCount: number;
  successRate: number;
  volumeChange: number;
  countChange: number;
}

export interface TransactionTrend {
  date: string;
  volume: number;
  count: number;
  avgAmount: number;
}

export interface PaymentMethodBreakdown {
  method: string;
  count: number;
  volume: number;
  percentage: number;
}

export interface GeographicBreakdown {
  country: string;
  count: number;
  volume: number;
  percentage: number;
}

export interface TransactionLimits {
  daily: {
    amount: number;
    count: number;
  };
  monthly: {
    amount: number;
    count: number;
  };
  perTransaction: {
    min: number;
    max: number;
  };
}

export interface FraudAnalysis {
  riskScore: number;
  riskFactors: Array<{
    factor: string;
    weight: number;
    description: string;
  }>;
  deviceFingerprint: {
    deviceId: string;
    browser: string;
    os: string;
    ipAddress: string;
    location: string;
  };
  behaviorAnalysis: {
    velocityScore: number;
    patternScore: number;
    locationScore: number;
    deviceScore: number;
  };
  recommendations: string[];
}

export interface TransactionHistory {
  id: string;
  timestamp: string;
  action: string;
  actor: string;
  description: string;
  oldValue?: any;
  newValue?: any;
}