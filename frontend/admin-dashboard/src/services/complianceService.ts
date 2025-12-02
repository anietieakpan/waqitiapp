import { apiClient } from './apiClient';

export interface ComplianceOverview {
  kycVerified: number;
  kycPending: number;
  sarsFiled: number;
  sarsPending: number;
  ctrsFiled: number;
  upcomingDeadlines: Array<{
    id: string;
    title: string;
    dueDate: string;
    daysRemaining: number;
  }>;
  recentActivities: Array<{
    id: string;
    date: string;
    type: string;
    description: string;
    status: string;
    actionBy: string;
  }>;
}

export interface ComplianceScore {
  overall: number;
  kyc: number;
  aml: number;
  reporting: number;
  policies: number;
  training: number;
}

export interface ComplianceReport {
  id: string;
  type: 'SAR' | 'CTR' | 'AUDIT' | 'RISK';
  status: 'DRAFT' | 'PENDING' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  submittedAt?: string;
  description: string;
  attachments: string[];
}

export const complianceService = {
  getComplianceOverview: async (): Promise<ComplianceOverview> => {
    const response = await apiClient.get('/api/admin/compliance/overview');
    return response.data;
  },

  getPendingItems: async () => {
    const response = await apiClient.get('/api/admin/compliance/pending');
    return response.data;
  },

  getComplianceScore: async (): Promise<ComplianceScore> => {
    const response = await apiClient.get('/api/admin/compliance/score');
    return response.data;
  },

  submitReport: async (data: any) => {
    const response = await apiClient.post('/api/admin/compliance/reports', data);
    return response.data;
  },

  getReports: async (filters?: any) => {
    const response = await apiClient.get('/api/admin/compliance/reports', { params: filters });
    return response.data;
  },

  getKycApplications: async (status?: string) => {
    const response = await apiClient.get('/api/admin/compliance/kyc', { params: { status } });
    return response.data;
  },

  verifyKyc: async (userId: string, verified: boolean, notes?: string) => {
    const response = await apiClient.post(`/api/admin/compliance/kyc/${userId}/verify`, {
      verified,
      notes,
    });
    return response.data;
  },

  getAmlAlerts: async () => {
    const response = await apiClient.get('/api/admin/compliance/aml/alerts');
    return response.data;
  },

  fileSar: async (data: any) => {
    const response = await apiClient.post('/api/admin/compliance/sar', data);
    return response.data;
  },

  fileCtr: async (data: any) => {
    const response = await apiClient.post('/api/admin/compliance/ctr', data);
    return response.data;
  },

  getRegulatoryUpdates: async () => {
    const response = await apiClient.get('/api/admin/compliance/regulatory-updates');
    return response.data;
  },

  getTrainingStatus: async () => {
    const response = await apiClient.get('/api/admin/compliance/training');
    return response.data;
  },

  assignTraining: async (userId: string, courseId: string) => {
    const response = await apiClient.post('/api/admin/compliance/training/assign', {
      userId,
      courseId,
    });
    return response.data;
  },

  getRiskAssessments: async () => {
    const response = await apiClient.get('/api/admin/compliance/risk-assessments');
    return response.data;
  },

  createRiskAssessment: async (data: any) => {
    const response = await apiClient.post('/api/admin/compliance/risk-assessments', data);
    return response.data;
  },

  downloadReport: async (reportId: string) => {
    const response = await apiClient.get(`/api/admin/compliance/reports/${reportId}/download`, {
      responseType: 'blob',
    });
    return response.data;
  },
};