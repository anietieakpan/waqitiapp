import { apiClient } from './apiClient';

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

export interface TransactionsResponse {
  transactions: Transaction[];
  total: number;
  page: number;
  pageSize: number;
}

export interface Transaction {
  id: string;
  timestamp: string;
  type: 'CREDIT' | 'DEBIT' | 'TRANSFER' | 'PAYMENT' | 'REFUND';
  status: 'COMPLETED' | 'PENDING' | 'FAILED' | 'BLOCKED';
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

export const transactionService = {
  getTransactions: async (filters: TransactionFilters): Promise<TransactionsResponse> => {
    const response = await apiClient.get('/api/admin/transactions', { params: filters });
    return response.data;
  },

  getAnalytics: async (): Promise<TransactionAnalytics> => {
    const response = await apiClient.get('/api/admin/transactions/analytics');
    return response.data;
  },

  getTransactionById: async (transactionId: string): Promise<Transaction> => {
    const response = await apiClient.get(`/api/admin/transactions/${transactionId}`);
    return response.data;
  },

  approveTransaction: async (transactionId: string) => {
    const response = await apiClient.post(`/api/admin/transactions/${transactionId}/approve`);
    return response.data;
  },

  blockTransaction: async (transactionId: string) => {
    const response = await apiClient.post(`/api/admin/transactions/${transactionId}/block`);
    return response.data;
  },

  flagTransaction: async (transactionId: string, reason: string) => {
    const response = await apiClient.post(`/api/admin/transactions/${transactionId}/flag`, { reason });
    return response.data;
  },

  unflagTransaction: async (transactionId: string) => {
    const response = await apiClient.delete(`/api/admin/transactions/${transactionId}/flag`);
    return response.data;
  },

  reverseTransaction: async (transactionId: string, reason: string) => {
    const response = await apiClient.post(`/api/admin/transactions/${transactionId}/reverse`, { reason });
    return response.data;
  },

  getTransactionHistory: async (transactionId: string) => {
    const response = await apiClient.get(`/api/admin/transactions/${transactionId}/history`);
    return response.data;
  },

  getTransactionFraudAnalysis: async (transactionId: string) => {
    const response = await apiClient.get(`/api/admin/transactions/${transactionId}/fraud-analysis`);
    return response.data;
  },

  exportTransactions: async (filters?: any) => {
    const response = await apiClient.get('/api/admin/transactions/export', {
      params: filters,
      responseType: 'blob',
    });
    return response.data;
  },

  bulkAction: async (transactionIds: string[], action: string, data?: any) => {
    const response = await apiClient.post('/api/admin/transactions/bulk-action', {
      transactionIds,
      action,
      data,
    });
    return response.data;
  },

  getTransactionTrends: async (timeRange: string) => {
    const response = await apiClient.get('/api/admin/transactions/trends', {
      params: { timeRange },
    });
    return response.data;
  },

  getFailureReasons: async () => {
    const response = await apiClient.get('/api/admin/transactions/failure-reasons');
    return response.data;
  },

  getTransactionsByUser: async (userId: string, limit: number = 50) => {
    const response = await apiClient.get(`/api/admin/transactions/user/${userId}`, {
      params: { limit },
    });
    return response.data;
  },

  getHighValueTransactions: async (threshold: number = 10000) => {
    const response = await apiClient.get('/api/admin/transactions/high-value', {
      params: { threshold },
    });
    return response.data;
  },

  getSuspiciousTransactions: async () => {
    const response = await apiClient.get('/api/admin/transactions/suspicious');
    return response.data;
  },

  getTransactionMetrics: async (timeRange: string) => {
    const response = await apiClient.get('/api/admin/transactions/metrics', {
      params: { timeRange },
    });
    return response.data;
  },

  getPaymentMethodBreakdown: async () => {
    const response = await apiClient.get('/api/admin/transactions/payment-methods');
    return response.data;
  },

  getGeographicBreakdown: async () => {
    const response = await apiClient.get('/api/admin/transactions/geographic');
    return response.data;
  },

  getTransactionLimits: async () => {
    const response = await apiClient.get('/api/admin/transactions/limits');
    return response.data;
  },

  updateTransactionLimits: async (limits: any) => {
    const response = await apiClient.put('/api/admin/transactions/limits', limits);
    return response.data;
  },
};