import axios from 'axios';
import { User } from '../types/user';

const API_GATEWAY_URL = process.env.REACT_APP_API_GATEWAY_URL || 'http://localhost:8080/api/v1';

class UserService {
  private axiosInstance = axios.create({
    baseURL: API_GATEWAY_URL,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  constructor() {
    // ✅ SECURITY FIX: Configure axios to use credentials (HttpOnly cookies)
    // Tokens are automatically sent via HttpOnly cookies - no localStorage needed
    this.axiosInstance.defaults.withCredentials = true;

    // Add response interceptor for error handling
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          try {
            // ✅ SECURITY: Token refresh via HttpOnly cookies
            // Server reads refreshToken from HttpOnly cookie and sets new cookies
            await this.refreshToken();

            // Retry original request (tokens automatically included via cookies)
            return this.axiosInstance.request(originalRequest);
          } catch (refreshError) {
            // Refresh failed, redirect to login
            // Dispatch event for auth system to handle
            window.dispatchEvent(new CustomEvent('auth:error', {
              detail: { reason: 'token_refresh_failed' }
            }));
            window.location.href = '/login';
          }
        }
        return Promise.reject(error);
      }
    );
  }

  private async refreshToken() {
    // ✅ SECURITY FIX: Token refresh via HttpOnly cookies
    // Server reads refreshToken from HttpOnly cookie (automatically sent)
    // Server responds by setting new HttpOnly cookies
    // NO localStorage access needed - prevents XSS token theft (CWE-522, CWE-79)
    //
    // ❌ REMOVED: localStorage.getItem('refreshToken') - VULNERABLE TO XSS
    // ❌ REMOVED: localStorage.setItem('accessToken', ...) - VULNERABLE TO XSS
    // ❌ REMOVED: localStorage.setItem('refreshToken', ...) - VULNERABLE TO XSS

    await axios.post(
      `${API_GATEWAY_URL}/auth/refresh`,
      {},  // Empty body - refreshToken sent via HttpOnly cookie
      {
        withCredentials: true,  // Include HttpOnly cookies in request
      }
    );

    // Server automatically sets new HttpOnly cookies in response
    // No client-side token storage needed
  }

  // User profile operations
  async getCurrentUser(): Promise<User> {
    const response = await this.axiosInstance.get('/users/me');
    return response.data;
  }

  async updateProfile(updates: Partial<User>): Promise<User> {
    const response = await this.axiosInstance.patch('/users/me', updates);
    return response.data;
  }

  async updatePreferences(preferences: any): Promise<any> {
    const response = await this.axiosInstance.patch('/users/me/preferences', preferences);
    return response.data;
  }

  async uploadAvatar(file: File): Promise<string> {
    const formData = new FormData();
    formData.append('avatar', file);

    const response = await this.axiosInstance.post('/users/me/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data.avatarUrl;
  }

  async deleteAvatar(): Promise<void> {
    await this.axiosInstance.delete('/users/me/avatar');
  }

  // KYC operations
  async uploadKycDocument(document: { type: string; file: File }): Promise<any> {
    const formData = new FormData();
    formData.append('type', document.type);
    formData.append('document', document.file);

    const response = await this.axiosInstance.post('/users/me/kyc/documents', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  async getKycDocuments(): Promise<any[]> {
    const response = await this.axiosInstance.get('/users/me/kyc/documents');
    return response.data;
  }

  async submitKycApplication(): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/kyc/submit');
    return response.data;
  }

  async getKycStatus(): Promise<any> {
    const response = await this.axiosInstance.get('/users/me/kyc/status');
    return response.data;
  }

  // Verification operations
  async verifyEmail(token: string): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/verify-email', { token });
    return response.data;
  }

  async resendEmailVerification(): Promise<void> {
    await this.axiosInstance.post('/users/me/verify-email/resend');
  }

  async verifyPhone(code: string): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/verify-phone', { code });
    return response.data;
  }

  async sendPhoneVerification(): Promise<void> {
    await this.axiosInstance.post('/users/me/verify-phone/send');
  }

  // MFA operations
  async setupMfa(params: { method: string }): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/mfa/setup', params);
    return response.data;
  }

  async verifyMfa(code: string): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/mfa/verify', { code });
    return response.data;
  }

  async disableMfa(): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/mfa/disable');
    return response.data;
  }

  async getMfaBackupCodes(): Promise<string[]> {
    const response = await this.axiosInstance.get('/users/me/mfa/backup-codes');
    return response.data;
  }

  async regenerateMfaBackupCodes(): Promise<string[]> {
    const response = await this.axiosInstance.post('/users/me/mfa/backup-codes/regenerate');
    return response.data;
  }

  // Security operations
  async changePassword(params: { currentPassword: string; newPassword: string }): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/change-password', params);
    return response.data;
  }

  async getSecurityLog(): Promise<any[]> {
    const response = await this.axiosInstance.get('/users/me/security-log');
    return response.data;
  }

  async getSessions(): Promise<any[]> {
    const response = await this.axiosInstance.get('/users/me/sessions');
    return response.data;
  }

  async revokeSession(sessionId: string): Promise<void> {
    await this.axiosInstance.delete(`/users/me/sessions/${sessionId}`);
  }

  async revokeAllSessions(): Promise<void> {
    await this.axiosInstance.delete('/users/me/sessions');
  }

  // Account operations
  async deleteAccount(password: string): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/delete-account', { password });
    return response.data;
  }

  async deactivateAccount(): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/deactivate');
    return response.data;
  }

  async reactivateAccount(): Promise<any> {
    const response = await this.axiosInstance.post('/users/me/reactivate');
    return response.data;
  }

  // Privacy operations
  async exportData(): Promise<Blob> {
    const response = await this.axiosInstance.get('/users/me/export-data', {
      responseType: 'blob',
    });
    return response.data;
  }

  async getPrivacySettings(): Promise<any> {
    const response = await this.axiosInstance.get('/users/me/privacy-settings');
    return response.data;
  }

  async updatePrivacySettings(settings: any): Promise<any> {
    const response = await this.axiosInstance.patch('/users/me/privacy-settings', settings);
    return response.data;
  }

  // Notification operations
  async getNotificationSettings(): Promise<any> {
    const response = await this.axiosInstance.get('/users/me/notification-settings');
    return response.data;
  }

  async updateNotificationSettings(settings: any): Promise<any> {
    const response = await this.axiosInstance.patch('/users/me/notification-settings', settings);
    return response.data;
  }

  // Limits operations
  async getTransactionLimits(): Promise<any> {
    const response = await this.axiosInstance.get('/users/me/transaction-limits');
    return response.data;
  }

  async requestLimitIncrease(params: {
    limitType: string;
    requestedAmount: number;
    reason: string;
    documents?: File[];
  }): Promise<any> {
    const formData = new FormData();
    formData.append('limitType', params.limitType);
    formData.append('requestedAmount', params.requestedAmount.toString());
    formData.append('reason', params.reason);
    
    if (params.documents) {
      params.documents.forEach((doc, index) => {
        formData.append(`documents[${index}]`, doc);
      });
    }

    const response = await this.axiosInstance.post('/users/me/limit-increase-request', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  // User search and social operations
  async searchUsers(query: string): Promise<User[]> {
    const response = await this.axiosInstance.get('/users/search', {
      params: { q: query },
    });
    return response.data;
  }

  async getUserProfile(userId: string): Promise<User> {
    const response = await this.axiosInstance.get(`/users/${userId}`);
    return response.data;
  }

  async reportUser(userId: string, reason: string, details: string): Promise<void> {
    await this.axiosInstance.post('/users/report', {
      userId,
      reason,
      details,
    });
  }

  async blockUser(userId: string): Promise<void> {
    await this.axiosInstance.post(`/users/${userId}/block`);
  }

  async unblockUser(userId: string): Promise<void> {
    await this.axiosInstance.post(`/users/${userId}/unblock`);
  }

  async getBlockedUsers(): Promise<User[]> {
    const response = await this.axiosInstance.get('/users/me/blocked');
    return response.data;
  }
}

export const userService = new UserService();