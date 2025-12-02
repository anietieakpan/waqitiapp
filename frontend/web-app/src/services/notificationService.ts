import apiClient from '../api/client';
import { Notification, NotificationPreferences, NotificationStats } from '../types/notification';

class NotificationService {
  private baseUrl = '/api/v1/notifications';

  // Get notifications
  async getNotifications(params?: {
    page?: number;
    limit?: number;
    unreadOnly?: boolean;
    type?: string;
  }) {
    return apiClient.get<{
      content: Notification[];
      totalPages: number;
      totalElements: number;
    }>(this.baseUrl, { params });
  }

  // Get notification by ID
  async getNotification(notificationId: string) {
    return apiClient.get<Notification>(`${this.baseUrl}/${notificationId}`);
  }

  // Get unread count
  async getUnreadCount() {
    return apiClient.get<{ count: number }>(`${this.baseUrl}/unread/count`);
  }

  // Mark notification as read
  async markAsRead(notificationId: string) {
    return apiClient.put(`${this.baseUrl}/${notificationId}/read`);
  }

  // Mark all notifications as read
  async markAllAsRead() {
    return apiClient.put(`${this.baseUrl}/read-all`);
  }

  // Delete notification
  async deleteNotification(notificationId: string) {
    return apiClient.delete(`${this.baseUrl}/${notificationId}`);
  }

  // Delete all read notifications
  async deleteAllRead() {
    return apiClient.delete(`${this.baseUrl}/read`);
  }

  // Get notification preferences
  async getPreferences() {
    return apiClient.get<NotificationPreferences>(`${this.baseUrl}/preferences`);
  }

  // Update notification preferences
  async updatePreferences(preferences: Partial<NotificationPreferences>) {
    return apiClient.put<NotificationPreferences>(`${this.baseUrl}/preferences`, preferences);
  }

  // Get notification statistics
  async getNotificationStats() {
    return apiClient.get<NotificationStats>(`${this.baseUrl}/stats`);
  }

  // Subscribe to push notifications
  async subscribeToPushNotifications(subscription: PushSubscription) {
    return apiClient.post(`${this.baseUrl}/push/subscribe`, subscription);
  }

  // Unsubscribe from push notifications
  async unsubscribeFromPushNotifications() {
    return apiClient.post(`${this.baseUrl}/push/unsubscribe`);
  }

  // Test notification (for debugging)
  async sendTestNotification(type: string) {
    return apiClient.post(`${this.baseUrl}/test`, { type });
  }

  // WebSocket connection for real-time notifications
  connectToNotificationWebSocket(userId: string, onMessage: (notification: Notification) => void) {
    const wsUrl = process.env.REACT_APP_WS_URL || 'ws://localhost:8080';
    const ws = new WebSocket(`${wsUrl}/notifications?userId=${userId}`);

    ws.onmessage = (event) => {
      try {
        const notification = JSON.parse(event.data);
        onMessage(notification);
      } catch (error) {
        console.error('Failed to parse notification:', error);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    ws.onclose = () => {
      console.log('WebSocket connection closed');
      // Implement reconnection logic here
      setTimeout(() => {
        this.connectToNotificationWebSocket(userId, onMessage);
      }, 5000); // Reconnect after 5 seconds
    };

    return ws;
  }

  // Get notification templates (for admin)
  async getNotificationTemplates() {
    return apiClient.get(`${this.baseUrl}/templates`);
  }

  // Update notification template (for admin)
  async updateNotificationTemplate(templateId: string, template: any) {
    return apiClient.put(`${this.baseUrl}/templates/${templateId}`, template);
  }
}

export const notificationService = new NotificationService();