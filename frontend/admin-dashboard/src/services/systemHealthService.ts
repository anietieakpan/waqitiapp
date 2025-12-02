import { apiClient } from './apiClient';

export interface SystemOverview {
  overallHealth: number;
  cpuUsage: number;
  cpuTrend: 'up' | 'down' | 'stable';
  cpuChange: number;
  memoryUsage: number;
  memoryAvailable: number;
  networkLatency: number;
  bandwidthUsage: number;
  dbConnections: number;
  dbMaxConnections: number;
  queueDepth: number;
  uptime: string;
}

export interface ServiceStatus {
  id: string;
  name: string;
  health: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY';
  status: 'RUNNING' | 'STOPPED' | 'RESTARTING';
  cpu: number;
  memory: number;
  responseTime: number;
  errorRate: number;
  uptime: string;
  replicas: number;
  version: string;
  lastHealthCheck: string;
}

export interface InfrastructureMetrics {
  clusters: Array<{
    name: string;
    region: string;
    nodes: number;
    health: string;
    workloads: number;
  }>;
  databases: Array<{
    name: string;
    type: string;
    size: number;
    connections: number;
    performance: number;
  }>;
  storage: Array<{
    name: string;
    type: string;
    used: number;
    total: number;
    iops: number;
  }>;
}

export const systemHealthService = {
  getSystemOverview: async (): Promise<SystemOverview> => {
    const response = await apiClient.get('/api/admin/system/overview');
    return response.data;
  },

  getServiceStatuses: async (): Promise<ServiceStatus[]> => {
    const response = await apiClient.get('/api/admin/system/services');
    return response.data;
  },

  getInfrastructureMetrics: async (): Promise<InfrastructureMetrics> => {
    const response = await apiClient.get('/api/admin/system/infrastructure');
    return response.data;
  },

  restartService: async (serviceId: string) => {
    const response = await apiClient.post(`/api/admin/system/services/${serviceId}/restart`);
    return response.data;
  },

  stopService: async (serviceId: string) => {
    const response = await apiClient.post(`/api/admin/system/services/${serviceId}/stop`);
    return response.data;
  },

  startService: async (serviceId: string) => {
    const response = await apiClient.post(`/api/admin/system/services/${serviceId}/start`);
    return response.data;
  },

  scaleService: async (serviceId: string, replicas: number) => {
    const response = await apiClient.post(`/api/admin/system/services/${serviceId}/scale`, { replicas });
    return response.data;
  },

  getServiceLogs: async (serviceId: string, lines: number = 100) => {
    const response = await apiClient.get(`/api/admin/system/services/${serviceId}/logs`, {
      params: { lines },
    });
    return response.data;
  },

  getResourceMetrics: async (timeRange: string) => {
    const response = await apiClient.get('/api/admin/system/metrics/resources', {
      params: { timeRange },
    });
    return response.data;
  },

  getNetworkMetrics: async () => {
    const response = await apiClient.get('/api/admin/system/metrics/network');
    return response.data;
  },

  getDatabaseHealth: async () => {
    const response = await apiClient.get('/api/admin/system/database/health');
    return response.data;
  },

  getCachePerformance: async () => {
    const response = await apiClient.get('/api/admin/system/cache/performance');
    return response.data;
  },

  getQueueMetrics: async () => {
    const response = await apiClient.get('/api/admin/system/queue/metrics');
    return response.data;
  },

  getApiMetrics: async () => {
    const response = await apiClient.get('/api/admin/system/api/metrics');
    return response.data;
  },

  runHealthCheck: async (serviceId?: string) => {
    const url = serviceId
      ? `/api/admin/system/health-check/${serviceId}`
      : '/api/admin/system/health-check';
    const response = await apiClient.post(url);
    return response.data;
  },

  getAlerts: async (severity?: string) => {
    const response = await apiClient.get('/api/admin/system/alerts', {
      params: { severity },
    });
    return response.data;
  },

  acknowledgeAlert: async (alertId: string) => {
    const response = await apiClient.post(`/api/admin/system/alerts/${alertId}/acknowledge`);
    return response.data;
  },

  getMaintenanceWindows: async () => {
    const response = await apiClient.get('/api/admin/system/maintenance');
    return response.data;
  },

  scheduleMaintenanceWindow: async (data: any) => {
    const response = await apiClient.post('/api/admin/system/maintenance', data);
    return response.data;
  },
};