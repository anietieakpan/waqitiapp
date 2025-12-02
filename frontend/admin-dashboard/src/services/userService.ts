import { apiClient } from './apiClient';

export interface UserFilters {
  search: string;
  status: string;
  kycStatus: string;
  role: string;
  dateFrom: Date | null;
  dateTo: Date | null;
  country: string;
}

export interface UserStatistics {
  totalUsers: number;
  activeUsers: number;
  userGrowth: number;
  kycPending: number;
  suspendedUsers: number;
  newUsersToday: number;
}

export interface UsersResponse {
  users: User[];
  total: number;
  page: number;
  pageSize: number;
}

export interface User {
  id: string;
  userId: string;
  name: string;
  email: string;
  phoneNumber?: string;
  avatarUrl?: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING' | 'INACTIVE';
  kycStatus: 'VERIFIED' | 'PENDING' | 'REJECTED' | 'NOT_STARTED';
  role: 'USER' | 'MERCHANT' | 'ADMIN' | 'SUPPORT';
  balance: number;
  transactionCount: number;
  joinDate: string;
  lastActive?: string;
  country: string;
  flags: string[];
  riskScore: number;
  mfaEnabled: boolean;
  emailVerified: boolean;
  phoneVerified: boolean;
}

export interface UserActivity {
  id: string;
  userId: string;
  type: string;
  description: string;
  timestamp: string;
  ipAddress: string;
  userAgent: string;
  location: string;
  riskScore: number;
}

export const userService = {
  getUsers: async (filters: UserFilters): Promise<UsersResponse> => {
    const response = await apiClient.get('/api/admin/users', { params: filters });
    return response.data;
  },

  getUserStatistics: async (): Promise<UserStatistics> => {
    const response = await apiClient.get('/api/admin/users/statistics');
    return response.data;
  },

  getUserById: async (userId: string): Promise<User> => {
    const response = await apiClient.get(`/api/admin/users/${userId}`);
    return response.data;
  },

  createUser: async (userData: any): Promise<User> => {
    const response = await apiClient.post('/api/admin/users', userData);
    return response.data;
  },

  updateUser: async (userId: string, userData: any): Promise<User> => {
    const response = await apiClient.put(`/api/admin/users/${userId}`, userData);
    return response.data;
  },

  suspendUser: async (userId: string, reason: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/suspend`, { reason });
    return response.data;
  },

  unsuspendUser: async (userId: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/unsuspend`);
    return response.data;
  },

  deleteUser: async (userId: string) => {
    const response = await apiClient.delete(`/api/admin/users/${userId}`);
    return response.data;
  },

  verifyKyc: async (userId: string, verified: boolean = true, notes?: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/kyc/verify`, {
      verified,
      notes,
    });
    return response.data;
  },

  resetPassword: async (userId: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/reset-password`);
    return response.data;
  },

  enableMfa: async (userId: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/mfa/enable`);
    return response.data;
  },

  disableMfa: async (userId: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/mfa/disable`);
    return response.data;
  },

  getUserActivity: async (userId: string, limit: number = 50): Promise<UserActivity[]> => {
    const response = await apiClient.get(`/api/admin/users/${userId}/activity`, {
      params: { limit },
    });
    return response.data;
  },

  getUserTransactions: async (userId: string, limit: number = 50) => {
    const response = await apiClient.get(`/api/admin/users/${userId}/transactions`, {
      params: { limit },
    });
    return response.data;
  },

  updateUserRole: async (userId: string, role: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/role`, { role });
    return response.data;
  },

  flagUser: async (userId: string, flag: string, reason: string) => {
    const response = await apiClient.post(`/api/admin/users/${userId}/flag`, { flag, reason });
    return response.data;
  },

  unflagUser: async (userId: string, flag: string) => {
    const response = await apiClient.delete(`/api/admin/users/${userId}/flag/${flag}`);
    return response.data;
  },

  exportUsers: async (filters?: any) => {
    const response = await apiClient.get('/api/admin/users/export', {
      params: filters,
      responseType: 'blob',
    });
    return response.data;
  },

  bulkAction: async (userIds: string[], action: string, data?: any) => {
    const response = await apiClient.post('/api/admin/users/bulk-action', {
      userIds,
      action,
      data,
    });
    return response.data;
  },

  getUserRoles: async () => {
    const response = await apiClient.get('/api/admin/users/roles');
    return response.data;
  },

  createRole: async (roleData: any) => {
    const response = await apiClient.post('/api/admin/users/roles', roleData);
    return response.data;
  },

  updateRole: async (roleId: string, roleData: any) => {
    const response = await apiClient.put(`/api/admin/users/roles/${roleId}`, roleData);
    return response.data;
  },

  deleteRole: async (roleId: string) => {
    const response = await apiClient.delete(`/api/admin/users/roles/${roleId}`);
    return response.data;
  },

  getUserPermissions: async (userId: string) => {
    const response = await apiClient.get(`/api/admin/users/${userId}/permissions`);
    return response.data;
  },

  updateUserPermissions: async (userId: string, permissions: string[]) => {
    const response = await apiClient.put(`/api/admin/users/${userId}/permissions`, { permissions });
    return response.data;
  },
};