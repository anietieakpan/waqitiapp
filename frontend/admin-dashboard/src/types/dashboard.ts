export type TimeRange = '1h' | '24h' | '7d' | '30d';

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
  api: {
    requestsPerSecond: number;
    averageResponseTime: number;
    errorRate: number;
  };
  alerts: {
    critical: number;
    warning: number;
    info: number;
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
  metadata: Record<string, any>;
}

export interface PerformanceMetric {
  timestamp: string;
  metric: string;
  value: number;
  unit: string;
  threshold?: number;
  status: 'normal' | 'warning' | 'critical';
}

export interface BusinessMetrics {
  revenue: {
    total: number;
    change: number;
    target: number;
    currency: string;
  };
  transactions: {
    count: number;
    volume: number;
    averageValue: number;
    successRate: number;
  };
  users: {
    active: number;
    new: number;
    retention: number;
    churn: number;
  };
  fees: {
    collected: number;
    percentage: number;
    change: number;
  };
}

export interface RegionalData {
  region: string;
  country: string;
  users: number;
  transactions: number;
  volume: number;
  currency: string;
  growth: number;
  marketShare: number;
}

export interface ApiMetrics {
  endpoint: string;
  requestCount: number;
  averageResponseTime: number;
  errorRate: number;
  p95ResponseTime: number;
  p99ResponseTime: number;
  throughput: number;
  statusCodes: Record<string, number>;
}

export interface ErrorMetric {
  timestamp: string;
  service: string;
  errorType: string;
  count: number;
  message: string;
  stackTrace?: string;
  affectedUsers: number;
}

export interface UserActivityMetric {
  timestamp: string;
  activeUsers: number;
  newUsers: number;
  returningUsers: number;
  sessionDuration: number;
  pageViews: number;
  bounceRate: number;
}

export interface TransactionTrend {
  timestamp: string;
  volume: number;
  count: number;
  averageAmount: number;
  successRate: number;
  fraudRate: number;
}

export interface SystemResourceUsage {
  timestamp: string;
  cpu: number;
  memory: number;
  disk: number;
  network: {
    in: number;
    out: number;
  };
  connections: number;
  processes: number;
}

export interface ComplianceStatus {
  score: number;
  kycCompliance: number;
  amlCompliance: number;
  reportingCompliance: number;
  pendingReviews: number;
  overdueReports: number;
  lastAudit: string;
  nextAudit: string;
}

export interface FraudDetection {
  riskScore: number;
  activeAlerts: number;
  suspiciousTransactions: number;
  blockedTransactions: number;
  falsePositiveRate: number;
  detectionRate: number;
  modelAccuracy: number;
  lastModelUpdate: string;
}

export interface QuickAction {
  id: string;
  title: string;
  description: string;
  icon: string;
  action: string;
  priority: 'low' | 'medium' | 'high';
  permissions: string[];
  enabled: boolean;
}