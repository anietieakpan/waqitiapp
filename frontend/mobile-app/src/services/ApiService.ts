/**
 * ApiService - Centralized API service for mobile app
 * Handles all API communications including notification-related endpoints
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import AppConfigService from './AppConfigService';

// API Types
interface DeviceTokenRequest {
  token: string;
  platform: 'ios' | 'android';
  deviceId: string;
  deviceModel?: string;
  osVersion?: string;
  appVersion?: string;
}

interface SendMessageRequest {
  conversationId: string;
  message: string;
  messageType?: 'text' | 'image' | 'file';
}

interface NotificationPreferencesRequest {
  pushEnabled?: boolean;
  emailEnabled?: boolean;
  smsEnabled?: boolean;
  categories?: {
    payments?: boolean;
    security?: boolean;
    marketing?: boolean;
    social?: boolean;
  };
  quietHours?: {
    enabled: boolean;
    startTime: string;
    endTime: string;
  };
}

interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
}

interface NotificationListResponse {
  content: any[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  unreadCount?: number;
}

class ApiService {
  private static instance: ApiService;
  private axios: AxiosInstance;
  private baseURL: string;

  constructor() {
    // Initialize with a default base URL, will be updated when AppConfigService is ready
    this.baseURL = this.getBaseURL();
    
    this.axios = axios.create({
      baseURL: this.baseURL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    });

    this.setupInterceptors();
    this.initializeWithAppConfig();
  }

  static getInstance(): ApiService {
    if (!ApiService.instance) {
      ApiService.instance = new ApiService();
    }
    return ApiService.instance;
  }

  /**
   * Initialize with AppConfigService
   */
  private async initializeWithAppConfig(): Promise<void> {
    try {
      // Update base URL from app config
      const apiBaseUrl = await AppConfigService.getApiBaseUrl();
      this.updateBaseURL(apiBaseUrl);
    } catch (error) {
      console.warn('Failed to initialize with AppConfigService:', error);
    }
  }

  private getBaseURL(): string {
    // You can configure different URLs for different environments
    const isDevelopment = __DEV__;
    
    if (isDevelopment) {
      // Development environment
      return Platform.OS === 'android' 
        ? 'http://10.0.2.2:8080/api/v1'  // Android emulator
        : 'http://localhost:8080/api/v1'; // iOS simulator
    } else {
      // Production environment
      return 'https://api.example.com/v1';
    }
  }

  private setupInterceptors(): void {
    // Request interceptor to add auth token and app headers
    this.axios.interceptors.request.use(
      async (config) => {
        try {
          const token = await AsyncStorage.getItem('authToken');
          if (token) {
            config.headers.Authorization = `Bearer ${token}`;
          }

          // Add app headers from AppConfigService
          try {
            const appHeaders = await AppConfigService.getAppHeaders();
            Object.assign(config.headers, appHeaders);
          } catch (error) {
            console.warn('Failed to add app headers:', error);
            // Fallback headers
            config.headers['X-App-Version'] = '1.0.0';
            config.headers['X-App-Platform'] = Platform.OS;
          }

          // Add device info to headers
          const deviceId = await AsyncStorage.getItem('deviceId');
          if (deviceId) {
            config.headers['X-Device-Id'] = deviceId;
          }
          
          console.log(`API Request: ${config.method?.toUpperCase()} ${config.url}`);
          return config;
        } catch (error) {
          console.error('Error in request interceptor:', error);
          return config;
        }
      },
      (error) => {
        console.error('Request interceptor error:', error);
        return Promise.reject(error);
      }
    );

    // Response interceptor for error handling
    this.axios.interceptors.response.use(
      (response) => {
        console.log(`API Response: ${response.status} ${response.config.method?.toUpperCase()} ${response.config.url}`);
        return response;
      },
      async (error) => {
        console.error('API Error:', error.response?.status, error.response?.data);
        
        // Handle 401 Unauthorized
        if (error.response?.status === 401) {
          await this.handleUnauthorized();
        }
        
        // Handle network errors
        if (!error.response) {
          console.error('Network error:', error.message);
        }
        
        return Promise.reject(error);
      }
    );
  }

  private async handleUnauthorized(): Promise<void> {
    try {
      // Clear auth token
      await AsyncStorage.removeItem('authToken');
      await AsyncStorage.removeItem('refreshToken');
      
      // Redirect to login (this would typically be handled by navigation service)
      console.log('User unauthorized, need to redirect to login');
    } catch (error) {
      console.error('Error handling unauthorized:', error);
    }
  }

  // Generic API methods
  private async request<T>(config: AxiosRequestConfig): Promise<T> {
    try {
      const response: AxiosResponse<T> = await this.axios(config);
      return response.data;
    } catch (error: any) {
      throw this.handleError(error);
    }
  }

  private handleError(error: any): Error {
    if (error.response) {
      // Server responded with error status
      const message = error.response.data?.message || 
                     error.response.data?.error || 
                     `Server error: ${error.response.status}`;
      return new Error(message);
    } else if (error.request) {
      // Network error
      return new Error('Network error: Please check your internet connection');
    } else {
      // Other error
      return new Error(error.message || 'An unexpected error occurred');
    }
  }

  // ==================== NOTIFICATION API METHODS ====================

  /**
   * Register device token for push notifications
   */
  async registerDeviceToken(request: DeviceTokenRequest): Promise<void> {
    return this.request({
      method: 'POST',
      url: '/notifications/device-tokens',
      data: request,
    });
  }

  /**
   * Update device token
   */
  async updateDeviceToken(request: DeviceTokenRequest): Promise<void> {
    return this.request({
      method: 'PUT',
      url: '/notifications/device-tokens',
      data: request,
    });
  }

  /**
   * Unregister device token
   */
  async unregisterDeviceToken(deviceId: string): Promise<void> {
    return this.request({
      method: 'DELETE',
      url: `/notifications/device-tokens/${deviceId}`,
    });
  }

  /**
   * Get user notifications with pagination
   */
  async getNotifications(params?: PaginationParams): Promise<NotificationListResponse> {
    return this.request({
      method: 'GET',
      url: '/notifications',
      params,
    });
  }

  /**
   * Get unread notifications
   */
  async getUnreadNotifications(params?: PaginationParams): Promise<NotificationListResponse> {
    return this.request({
      method: 'GET',
      url: '/notifications/unread',
      params,
    });
  }

  /**
   * Get unread notification count
   */
  async getUnreadNotificationCount(): Promise<{ count: number }> {
    return this.request({
      method: 'GET',
      url: '/notifications/unread/count',
    });
  }

  /**
   * Mark notification as read
   */
  async markNotificationAsRead(notificationId: string): Promise<void> {
    return this.request({
      method: 'PUT',
      url: `/notifications/${notificationId}/read`,
    });
  }

  /**
   * Mark all notifications as read
   */
  async markAllNotificationsAsRead(): Promise<void> {
    return this.request({
      method: 'PUT',
      url: '/notifications/read-all',
    });
  }

  /**
   * Delete notification
   */
  async deleteNotification(notificationId: string): Promise<void> {
    return this.request({
      method: 'DELETE',
      url: `/notifications/${notificationId}`,
    });
  }

  /**
   * Get notification preferences
   */
  async getNotificationPreferences(): Promise<NotificationPreferencesRequest> {
    return this.request({
      method: 'GET',
      url: '/notifications/preferences',
    });
  }

  /**
   * Update notification preferences
   */
  async updateNotificationPreferences(preferences: NotificationPreferencesRequest): Promise<NotificationPreferencesRequest> {
    return this.request({
      method: 'PUT',
      url: '/notifications/preferences',
      data: preferences,
    });
  }

  /**
   * Test push notification (for debugging)
   */
  async testPushNotification(message: string): Promise<void> {
    return this.request({
      method: 'POST',
      url: '/notifications/test',
      data: { message },
    });
  }

  // ==================== MESSAGING API METHODS ====================

  /**
   * Send message in conversation
   */
  async sendMessage(request: SendMessageRequest): Promise<any> {
    return this.request({
      method: 'POST',
      url: `/conversations/${request.conversationId}/messages`,
      data: {
        message: request.message,
        messageType: request.messageType || 'text',
      },
    });
  }

  /**
   * Get conversation messages
   */
  async getConversationMessages(conversationId: string, params?: PaginationParams): Promise<any> {
    return this.request({
      method: 'GET',
      url: `/conversations/${conversationId}/messages`,
      params,
    });
  }

  // ==================== PAYMENT API METHODS ====================

  /**
   * Accept money request
   */
  async acceptMoneyRequest(requestId: string): Promise<any> {
    return this.request({
      method: 'POST',
      url: `/payment-requests/${requestId}/accept`,
    });
  }

  /**
   * Decline money request
   */
  async declineMoneyRequest(requestId: string, reason?: string): Promise<any> {
    return this.request({
      method: 'POST',
      url: `/payment-requests/${requestId}/decline`,
      data: { reason },
    });
  }

  /**
   * Get payment request details
   */
  async getPaymentRequest(requestId: string): Promise<any> {
    return this.request({
      method: 'GET',
      url: `/payment-requests/${requestId}`,
    });
  }

  /**
   * Get transaction details
   */
  async getTransactionDetails(transactionId: string): Promise<any> {
    return this.request({
      method: 'GET',
      url: `/transactions/${transactionId}`,
    });
  }

  /**
   * Get user transactions
   */
  async getTransactions(params?: PaginationParams & { type?: string; status?: string }): Promise<any> {
    return this.request({
      method: 'GET',
      url: '/transactions',
      params,
    });
  }

  // ==================== USER API METHODS ====================

  /**
   * Get user profile
   */
  async getUserProfile(): Promise<any> {
    return this.request({
      method: 'GET',
      url: '/users/profile',
    });
  }

  /**
   * Update user profile
   */
  async updateUserProfile(data: any): Promise<any> {
    return this.request({
      method: 'PUT',
      url: '/users/profile',
      data,
    });
  }

  // ==================== SECURITY API METHODS ====================

  /**
   * Get security settings
   */
  async getSecuritySettings(): Promise<any> {
    return this.request({
      method: 'GET',
      url: '/users/security',
    });
  }

  /**
   * Update security settings
   */
  async updateSecuritySettings(data: any): Promise<any> {
    return this.request({
      method: 'PUT',
      url: '/users/security',
      data,
    });
  }

  /**
   * Report security incident
   */
  async reportSecurityIncident(data: { type: string; description: string }): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/security/incidents',
      data,
    });
  }

  // ==================== UTILITY METHODS ====================

  /**
   * Generic GET request
   */
  async get<T = any>(url: string, params?: any): Promise<T> {
    return this.request({
      method: 'GET',
      url,
      params,
    });
  }

  /**
   * Generic POST request
   */
  async post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.request({
      method: 'POST',
      url,
      data,
      ...config,
    });
  }

  /**
   * Generic PUT request
   */
  async put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.request({
      method: 'PUT',
      url,
      data,
      ...config,
    });
  }

  /**
   * Generic DELETE request
   */
  async delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request({
      method: 'DELETE',
      url,
      ...config,
    });
  }

  /**
   * Multipart form data POST request
   */
  async postMultipart<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
    return this.request({
      method: 'POST',
      url,
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      ...config,
    });
  }

  /**
   * Upload file
   */
  async uploadFile(file: FormData, endpoint: string = '/files/upload'): Promise<{ url: string; fileId: string }> {
    return this.request({
      method: 'POST',
      url: endpoint,
      data: file,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  /**
   * Get app configuration
   */
  async getAppConfig(): Promise<any> {
    return this.request({
      method: 'GET',
      url: '/config/app',
    });
  }

  /**
   * Health check
   */
  async healthCheck(): Promise<{ status: string; timestamp: string }> {
    return this.request({
      method: 'GET',
      url: '/health',
    });
  }

  // ==================== AUTH API METHODS ====================

  /**
   * Login user
   */
  async login(credentials: any): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/auth/login',
      data: credentials,
    });
  }

  /**
   * Register new user
   */
  async register(userData: any): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/auth/register',
      data: userData,
    });
  }

  /**
   * Verify email address
   */
  async verifyEmail(token: string): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/auth/verify-email',
      data: { token },
    });
  }

  /**
   * Request password reset
   */
  async requestPasswordReset(email: string): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/auth/forgot-password',
      data: { email },
    });
  }

  /**
   * Reset password with token
   */
  async resetPassword(token: string, newPassword: string): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/auth/reset-password',
      data: { token, newPassword },
    });
  }

  /**
   * Verify MFA code
   */
  async verifyMFA(code: string, sessionToken: string): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/auth/mfa/verify',
      data: { code, sessionToken },
    });
  }

  /**
   * Refresh auth token
   */
  async refreshToken(): Promise<{ accessToken: string; refreshToken: string }> {
    const refreshToken = await AsyncStorage.getItem('refreshToken');
    return this.request({
      method: 'POST',
      url: '/auth/refresh',
      data: { refreshToken },
    });
  }

  /**
   * Logout
   */
  async logout(): Promise<void> {
    try {
      await this.request({
        method: 'POST',
        url: '/auth/logout',
      });
    } finally {
      // Clear local storage even if API call fails
      await AsyncStorage.multiRemove(['authToken', 'refreshToken', 'deviceId']);
    }
  }

  // ==================== CONTACT API METHODS ====================

  /**
   * Get contacts
   */
  async getContacts(params?: PaginationParams): Promise<any> {
    return this.request({
      method: 'GET',
      url: '/contacts',
      params,
    });
  }

  /**
   * Add contact
   */
  async addContact(data: { phoneNumber?: string; email?: string; name: string }): Promise<any> {
    return this.request({
      method: 'POST',
      url: '/contacts',
      data,
    });
  }

  // ==================== ANALYTICS API METHODS ====================

  /**
   * Track event
   */
  async trackEvent(event: string, properties?: Record<string, any>): Promise<void> {
    return this.request({
      method: 'POST',
      url: '/analytics/events',
      data: {
        event,
        properties,
        platform: Platform.OS,
        timestamp: new Date().toISOString(),
      },
    });
  }

  /**
   * Update API base URL (for testing/environment switching)
   */
  updateBaseURL(newBaseURL: string): void {
    this.baseURL = newBaseURL;
    this.axios.defaults.baseURL = newBaseURL;
  }

  /**
   * Get current base URL
   */
  getApiBaseURL(): string {
    return this.baseURL;
  }

  /**
   * Set auth token
   */
  async setAuthToken(token: string): Promise<void> {
    await AsyncStorage.setItem('authToken', token);
  }

  /**
   * Clear auth token
   */
  async clearAuthToken(): Promise<void> {
    await AsyncStorage.removeItem('authToken');
  }
}

export const ApiService = ApiService.getInstance();
export default ApiService;