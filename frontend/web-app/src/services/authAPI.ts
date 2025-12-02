import axios from 'axios';
import { apiClient } from './apiClient';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
}

export interface MFAVerifyRequest {
  tempToken: string;
  code: string;
}

export const authAPI = {
  login: async (credentials: LoginRequest) => {
    return apiClient.post('/api/v1/auth/login', credentials);
  },

  register: async (userData: RegisterRequest) => {
    return apiClient.post('/api/v1/users/register', userData);
  },

  verifyMFA: async (data: MFAVerifyRequest) => {
    return apiClient.post('/api/v1/auth/verify-mfa', data);
  },

  refreshToken: async (refreshToken: string) => {
    return apiClient.post('/api/v1/auth/refresh', { refreshToken });
  },

  logout: async (refreshToken: string) => {
    return apiClient.post('/api/v1/auth/logout', { refreshToken });
  },

  verifyEmail: async (token: string) => {
    return apiClient.get(`/api/v1/users/verify/${token}`);
  },

  forgotPassword: async (email: string) => {
    return apiClient.post('/api/v1/auth/forgot-password', { email });
  },

  resetPassword: async (token: string, newPassword: string) => {
    return apiClient.post('/api/v1/auth/reset-password', { token, newPassword });
  },

  setupMFA: async (method: 'TOTP' | 'SMS' | 'EMAIL') => {
    return apiClient.post('/api/v1/mfa/setup', { method });
  },

  getCurrentUser: async () => {
    return apiClient.get('/api/v1/users/me');
  },
};