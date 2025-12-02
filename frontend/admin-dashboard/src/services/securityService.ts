import { apiClient } from './apiClient';

export interface SecurityOverview {
  blockedIps: number;
  failedLogins: number;
  suspiciousActivities: number;
  activeApiKeys: number;
  mfaAdoption: number;
  lastScan?: string;
  openIncidents: number;
  recentAlerts: Array<{
    id: string;
    timestamp: string;
    severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
    message: string;
    source: string;
  }>;
  loginByLocation: Array<{
    country: string;
    successful: number;
    failed: number;
    blocked: number;
  }>;
  deviceAccess: Array<{
    type: string;
    count: number;
    suspiciousCount: number;
    percentage: number;
  }>;
}

export interface SecurityScore {
  overall: number;
  authentication: number;
  authorization: number;
  encryption: number;
  monitoring: number;
  compliance: number;
}

export interface Threat {
  id: string;
  timestamp: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  type: string;
  source: string;
  target: string;
  description: string;
  status: 'ACTIVE' | 'MITIGATED' | 'INVESTIGATING';
  actions: string[];
}

export interface SecurityIncident {
  id: string;
  createdAt: string;
  type: string;
  severity: string;
  description: string;
  affectedUsers: number;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'CLOSED';
  assignedTo?: string;
  resolution?: string;
}

export const securityService = {
  getSecurityOverview: async (): Promise<SecurityOverview> => {
    const response = await apiClient.get('/api/admin/security/overview');
    return response.data;
  },

  getActiveThreats: async (): Promise<Threat[]> => {
    const response = await apiClient.get('/api/admin/security/threats/active');
    return response.data;
  },

  getSecurityScore: async (): Promise<SecurityScore> => {
    const response = await apiClient.get('/api/admin/security/score');
    return response.data;
  },

  blockIp: async (ip: string) => {
    const response = await apiClient.post('/api/admin/security/block-ip', { ip });
    return response.data;
  },

  unblockIp: async (ip: string) => {
    const response = await apiClient.post('/api/admin/security/unblock-ip', { ip });
    return response.data;
  },

  getBlockedIps: async () => {
    const response = await apiClient.get('/api/admin/security/blocked-ips');
    return response.data;
  },

  getSecurityIncidents: async (status?: string) => {
    const response = await apiClient.get('/api/admin/security/incidents', { params: { status } });
    return response.data;
  },

  resolveIncident: async (id: string, resolution: string) => {
    const response = await apiClient.post(`/api/admin/security/incidents/${id}/resolve`, { resolution });
    return response.data;
  },

  getAuditLogs: async (filters?: any) => {
    const response = await apiClient.get('/api/admin/security/audit-logs', { params: filters });
    return response.data;
  },

  getAccessControl: async () => {
    const response = await apiClient.get('/api/admin/security/access-control');
    return response.data;
  },

  updateAccessControl: async (userId: string, permissions: string[]) => {
    const response = await apiClient.put(`/api/admin/security/access-control/${userId}`, { permissions });
    return response.data;
  },

  getMfaStatus: async () => {
    const response = await apiClient.get('/api/admin/security/mfa/status');
    return response.data;
  },

  enforceMfa: async (userId: string) => {
    const response = await apiClient.post(`/api/admin/security/mfa/enforce/${userId}`);
    return response.data;
  },

  getEncryptionStatus: async () => {
    const response = await apiClient.get('/api/admin/security/encryption/status');
    return response.data;
  },

  rotateKeys: async (service: string) => {
    const response = await apiClient.post('/api/admin/security/encryption/rotate-keys', { service });
    return response.data;
  },

  runVulnerabilityScan: async () => {
    const response = await apiClient.post('/api/admin/security/vulnerability-scan/run');
    return response.data;
  },

  getVulnerabilities: async () => {
    const response = await apiClient.get('/api/admin/security/vulnerabilities');
    return response.data;
  },

  getSecurityPolicies: async () => {
    const response = await apiClient.get('/api/admin/security/policies');
    return response.data;
  },

  updateSecurityPolicy: async (policyId: string, data: any) => {
    const response = await apiClient.put(`/api/admin/security/policies/${policyId}`, data);
    return response.data;
  },

  emergencyLockdown: async (reason: string) => {
    const response = await apiClient.post('/api/admin/security/emergency-lockdown', { reason });
    return response.data;
  },
};