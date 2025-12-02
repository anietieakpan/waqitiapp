import { apiClient } from './apiClient';

export interface DashboardStats {
  totalUsers: number;
  userGrowth: number;
  transactionVolume: number;
  volumeChange: number;
  activeWallets: number;
  walletGrowth: number;
  systemHealth: {
    score: number;
    status: string;
    issues: string[];
  };
  geographicData: Array<{
    country: string;
    users: number;
    transactions: number;
    volume: number;
  }>;
}

export interface RealtimeMetrics {
  transactions: Array<{
    timestamp: string;
    count: number;
    volume: number;
    avgAmount: number;
  }>;
  system: {
    cpu: number;
    memory: number;
    disk: number;
    network: number;
  };
}

export interface SystemAlert {
  id: string;
  timestamp: string;
  severity: 'info' | 'warning' | 'error' | 'critical';
  title: string;
  message: string;
  source: string;
  acknowledged: boolean;
  resolved: boolean;
}

export type TimeRange = '1h' | '24h' | '7d' | '30d';

export const dashboardService = {
  getDashboardStats: async (timeRange: TimeRange): Promise<DashboardStats> => {
    const response = await apiClient.get('/api/admin/dashboard/stats', {
      params: { timeRange },
    });
    return response.data;
  },

  getRealtimeMetrics: async (): Promise<RealtimeMetrics> => {
    const response = await apiClient.get('/api/admin/dashboard/realtime');
    return response.data;
  },

  getSystemAlerts: async (): Promise<SystemAlert[]> => {
    const response = await apiClient.get('/api/admin/dashboard/alerts');
    return response.data;
  },

  acknowledgeAlert: async (alertId: string) => {
    const response = await apiClient.post(`/api/admin/dashboard/alerts/${alertId}/acknowledge`);
    return response.data;
  },

  resolveAlert: async (alertId: string) => {
    const response = await apiClient.post(`/api/admin/dashboard/alerts/${alertId}/resolve`);
    return response.data;
  },

  getQuickActions: async () => {
    const response = await apiClient.get('/api/admin/dashboard/quick-actions');
    return response.data;
  },

  getComplianceStatus: async () => {
    const response = await apiClient.get('/api/admin/dashboard/compliance-status');
    return response.data;
  },

  getFraudDetection: async () => {
    const response = await apiClient.get('/api/admin/dashboard/fraud-detection');
    return response.data;
  },

  getRecentTransactions: async (limit: number = 10) => {
    const response = await apiClient.get('/api/admin/dashboard/recent-transactions', {
      params: { limit },
    });
    return response.data;
  },

  getUserActivity: async (timeRange: TimeRange) => {
    const response = await apiClient.get('/api/admin/dashboard/user-activity', {
      params: { timeRange },
    });
    return response.data;
  },

  getRevenueAnalytics: async (timeRange: TimeRange) => {
    const response = await apiClient.get('/api/admin/dashboard/revenue', {
      params: { timeRange },
    });
    return response.data;
  },

  getPerformanceMetrics: async () => {
    const response = await apiClient.get('/api/admin/dashboard/performance');
    return response.data;
  },

  getSystemResourceUsage: async () => {
    const response = await apiClient.get('/api/admin/dashboard/system-resources');
    return response.data;
  },

  getApiMetrics: async () => {
    const response = await apiClient.get('/api/admin/dashboard/api-metrics');
    return response.data;
  },

  getErrorRates: async () => {
    const response = await apiClient.get('/api/admin/dashboard/error-rates');
    return response.data;
  },

  getActiveUsers: async (timeRange: TimeRange) => {
    const response = await apiClient.get('/api/admin/dashboard/active-users', {
      params: { timeRange },
    });
    return response.data;
  },

  getTransactionTrends: async (timeRange: TimeRange) => {
    const response = await apiClient.get('/api/admin/dashboard/transaction-trends', {
      params: { timeRange },
    });
    return response.data;
  },

  getRegionalData: async () => {
    const response = await apiClient.get('/api/admin/dashboard/regional-data');
    return response.data;
  },

  getBusinessMetrics: async () => {
    const response = await apiClient.get('/api/admin/dashboard/business-metrics');
    return response.data;
  },

  downloadReport: async (type: string, timeRange: TimeRange) => {
    const response = await apiClient.get('/api/admin/dashboard/reports/download', {
      params: { type, timeRange },
      responseType: 'blob',
    });
    return response.data;
  },
};