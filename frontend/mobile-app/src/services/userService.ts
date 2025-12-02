import { apiClient } from './apiClient';
import { User, UserProfile, UserSettings } from '../types';

export const userService = {
  async searchUsers(query: string): Promise<User[]> {
    try {
      const response = await apiClient.get('/users/search', {
        params: { 
          q: query,
          limit: 20,
        },
      });
      return response.data;
    } catch (error) {
      console.error('Failed to search users:', error);
      throw error;
    }
  },

  async getUserProfile(userId: string): Promise<UserProfile> {
    try {
      const response = await apiClient.get(`/users/${userId}/profile`);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch user profile:', error);
      throw error;
    }
  },

  async getCurrentUser(): Promise<User> {
    try {
      const response = await apiClient.get('/users/me');
      return response.data;
    } catch (error) {
      console.error('Failed to fetch current user:', error);
      throw error;
    }
  },

  async updateProfile(updates: Partial<UserProfile>): Promise<UserProfile> {
    try {
      const response = await apiClient.patch('/users/me/profile', updates);
      return response.data;
    } catch (error) {
      console.error('Failed to update profile:', error);
      throw error;
    }
  },

  async uploadAvatar(file: any): Promise<{ avatarUrl: string }> {
    try {
      const formData = new FormData();
      formData.append('avatar', {
        uri: file.uri,
        type: file.type || 'image/jpeg',
        name: file.name || 'avatar.jpg',
      } as any);

      const response = await apiClient.post('/users/me/avatar', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      return response.data;
    } catch (error) {
      console.error('Failed to upload avatar:', error);
      throw error;
    }
  },

  async updateSettings(settings: Partial<UserSettings>): Promise<UserSettings> {
    try {
      const response = await apiClient.patch('/users/me/settings', settings);
      return response.data;
    } catch (error) {
      console.error('Failed to update settings:', error);
      throw error;
    }
  },

  async checkUsername(username: string): Promise<{ available: boolean }> {
    try {
      const response = await apiClient.get('/users/check-username', {
        params: { username },
      });
      return response.data;
    } catch (error) {
      console.error('Failed to check username:', error);
      throw error;
    }
  },

  async getUserByUsername(username: string): Promise<User> {
    try {
      const response = await apiClient.get(`/users/username/${username}`);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch user by username:', error);
      throw error;
    }
  },

  async getUserByQRCode(qrCode: string): Promise<User> {
    try {
      const response = await apiClient.get('/users/qr', {
        params: { code: qrCode },
      });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch user by QR code:', error);
      throw error;
    }
  },

  async generateQRCode(): Promise<{ qrCode: string; qrCodeUrl: string }> {
    try {
      const response = await apiClient.post('/users/me/qr-code');
      return response.data;
    } catch (error) {
      console.error('Failed to generate QR code:', error);
      throw error;
    }
  },

  async reportUser(userId: string, reason: string, details?: string): Promise<void> {
    try {
      await apiClient.post(`/users/${userId}/report`, {
        reason,
        details,
      });
    } catch (error) {
      console.error('Failed to report user:', error);
      throw error;
    }
  },

  async getMutualContacts(userId: string): Promise<User[]> {
    try {
      const response = await apiClient.get(`/users/${userId}/mutual-contacts`);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch mutual contacts:', error);
      throw error;
    }
  },

  async inviteUser(phoneNumber: string): Promise<void> {
    try {
      await apiClient.post('/users/invite', { phoneNumber });
    } catch (error) {
      console.error('Failed to invite user:', error);
      throw error;
    }
  },
};