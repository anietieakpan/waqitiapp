import { apiClient } from './apiClient';
import { Platform } from 'react-native';
import messaging from '@react-native-firebase/messaging';
import PushNotification from 'react-native-push-notification';

export interface Notification {
  id: string;
  userId: string;
  type: 'PAYMENT_RECEIVED' | 'PAYMENT_SENT' | 'PAYMENT_REQUEST' | 'SECURITY_ALERT' | 'SYSTEM' | 'PROMOTIONAL';
  title: string;
  message: string;
  data?: any;
  isRead: boolean;
  createdAt: string;
  actionUrl?: string;
}

export interface NotificationSettings {
  pushNotifications: boolean;
  emailNotifications: boolean;
  smsNotifications: boolean;
  paymentReceived: boolean;
  paymentSent: boolean;
  paymentRequests: boolean;
  securityAlerts: boolean;
  promotionalOffers: boolean;
  weeklyStatements: boolean;
  lowBalance: boolean;
  largeTransactions: boolean;
  loginAlerts: boolean;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  doNotDisturbEnabled: boolean;
  doNotDisturbStart: string;
  doNotDisturbEnd: string;
  frequency: 'immediate' | 'hourly' | 'daily';
}

class NotificationService {
  private fcmToken: string | null = null;

  constructor() {
    this.initializePushNotifications();
  }

  private async initializePushNotifications() {
    try {
      // Request permission
      const authStatus = await messaging().requestPermission();
      const enabled =
        authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
        authStatus === messaging.AuthorizationStatus.PROVISIONAL;

      if (enabled) {
        // Get FCM token
        this.fcmToken = await messaging().getToken();
        console.log('FCM Token:', this.fcmToken);

        // Register token with backend
        if (this.fcmToken) {
          await this.registerDeviceToken(this.fcmToken);
        }

        // Handle token refresh
        messaging().onTokenRefresh(async (token) => {
          this.fcmToken = token;
          await this.registerDeviceToken(token);
        });

        // Handle foreground messages
        messaging().onMessage(async (remoteMessage) => {
          this.handleForegroundMessage(remoteMessage);
        });

        // Handle background messages
        messaging().setBackgroundMessageHandler(async (remoteMessage) => {
          console.log('Message handled in the background!', remoteMessage);
        });
      }

      // Configure local notifications
      this.configurePushNotifications();
    } catch (error) {
      console.error('Failed to initialize push notifications:', error);
    }
  }

  private configurePushNotifications() {
    if (Platform.OS === 'android') {
      PushNotification.createChannel(
        {
          channelId: 'waqiti-default',
          channelName: 'Default',
          channelDescription: 'Default notification channel',
          playSound: true,
          soundName: 'default',
          importance: 4,
          vibrate: true,
        },
        (created) => console.log(`Channel created: ${created}`)
      );

      PushNotification.createChannel(
        {
          channelId: 'waqiti-payments',
          channelName: 'Payments',
          channelDescription: 'Payment related notifications',
          playSound: true,
          soundName: 'default',
          importance: 4,
          vibrate: true,
        },
        (created) => console.log(`Payments channel created: ${created}`)
      );

      PushNotification.createChannel(
        {
          channelId: 'waqiti-security',
          channelName: 'Security Alerts',
          channelDescription: 'Security and login alerts',
          playSound: true,
          soundName: 'default',
          importance: 5,
          vibrate: true,
        },
        (created) => console.log(`Security channel created: ${created}`)
      );
    }

    PushNotification.configure({
      onRegister: function (token) {
        console.log('TOKEN:', token);
      },
      onNotification: function (notification) {
        console.log('NOTIFICATION:', notification);
        if (notification.userInteraction) {
          // Handle notification tap
          notificationService.handleNotificationTap(notification);
        }
      },
      onAction: function (notification) {
        console.log('ACTION:', notification.action);
      },
      onRegistrationError: function (err) {
        console.error(err.message, err);
      },
      permissions: {
        alert: true,
        badge: true,
        sound: true,
      },
      popInitialNotification: true,
      requestPermissions: true,
    });
  }

  private async registerDeviceToken(token: string): Promise<void> {
    try {
      await apiClient.post('/notifications/register-device', {
        token,
        platform: Platform.OS,
        appVersion: '1.0.0', // Should come from app config
      });
    } catch (error) {
      console.error('Failed to register device token:', error);
    }
  }

  private handleForegroundMessage(remoteMessage: any) {
    const { notification, data } = remoteMessage;
    
    if (notification) {
      this.showLocalNotification({
        title: notification.title || 'Waqiti',
        message: notification.body || '',
        data: data || {},
        type: data?.type || 'SYSTEM',
      });
    }
  }

  private showLocalNotification(params: {
    title: string;
    message: string;
    data: any;
    type: string;
  }) {
    const channelId = this.getChannelIdForType(params.type);
    
    PushNotification.localNotification({
      title: params.title,
      message: params.message,
      playSound: true,
      soundName: 'default',
      channelId,
      userInfo: params.data,
      actions: this.getActionsForType(params.type),
    });
  }

  private getChannelIdForType(type: string): string {
    switch (type) {
      case 'PAYMENT_RECEIVED':
      case 'PAYMENT_SENT':
      case 'PAYMENT_REQUEST':
        return 'waqiti-payments';
      case 'SECURITY_ALERT':
        return 'waqiti-security';
      default:
        return 'waqiti-default';
    }
  }

  private getActionsForType(type: string): string[] {
    switch (type) {
      case 'PAYMENT_REQUEST':
        return ['Accept', 'Decline'];
      case 'PAYMENT_RECEIVED':
        return ['View'];
      default:
        return [];
    }
  }

  private handleNotificationTap(notification: any) {
    const { userInfo } = notification;
    
    // Navigate based on notification type
    switch (userInfo?.type) {
      case 'PAYMENT_RECEIVED':
      case 'PAYMENT_SENT':
        // Navigate to transaction details
        break;
      case 'PAYMENT_REQUEST':
        // Navigate to payment request
        break;
      case 'SECURITY_ALERT':
        // Navigate to security settings
        break;
      default:
        // Navigate to notifications screen
        break;
    }
  }

  // API Methods
  async getNotifications(): Promise<{ notifications: Notification[] }> {
    const response = await apiClient.get('/notifications');
    return response.data;
  }

  async markAsRead(notificationId: string): Promise<void> {
    await apiClient.patch(`/notifications/${notificationId}/read`);
  }

  async markAllAsRead(): Promise<void> {
    await apiClient.patch('/notifications/mark-all-read');
  }

  async deleteNotification(notificationId: string): Promise<void> {
    await apiClient.delete(`/notifications/${notificationId}`);
  }

  async getUnreadCount(): Promise<{ count: number }> {
    const response = await apiClient.get('/notifications/unread-count');
    return response.data;
  }

  async getNotificationSettings(): Promise<{ settings: NotificationSettings }> {
    const response = await apiClient.get('/notifications/settings');
    return response.data;
  }

  async updateNotificationSettings(
    settings: Partial<NotificationSettings>
  ): Promise<void> {
    await apiClient.patch('/notifications/settings', settings);
  }

  async sendTestNotification(): Promise<void> {
    await apiClient.post('/notifications/test');
  }

  async subscribeToTopic(topic: string): Promise<void> {
    try {
      await messaging().subscribeToTopic(topic);
      console.log(`Subscribed to topic: ${topic}`);
    } catch (error) {
      console.error(`Failed to subscribe to topic ${topic}:`, error);
    }
  }

  async unsubscribeFromTopic(topic: string): Promise<void> {
    try {
      await messaging().unsubscribeFromTopic(topic);
      console.log(`Unsubscribed from topic: ${topic}`);
    } catch (error) {
      console.error(`Failed to unsubscribe from topic ${topic}:`, error);
    }
  }

  async scheduleLocalNotification(params: {
    title: string;
    message: string;
    date: Date;
    data?: any;
  }): Promise<void> {
    PushNotification.localNotificationSchedule({
      title: params.title,
      message: params.message,
      date: params.date,
      userInfo: params.data || {},
      playSound: true,
      soundName: 'default',
    });
  }

  async cancelLocalNotification(notificationId: string): Promise<void> {
    PushNotification.cancelLocalNotifications({ id: notificationId });
  }

  async cancelAllLocalNotifications(): Promise<void> {
    PushNotification.cancelAllLocalNotifications();
  }

  async getBadgeCount(): Promise<number> {
    return new Promise((resolve) => {
      PushNotification.getApplicationIconBadgeNumber((count) => {
        resolve(count);
      });
    });
  }

  async setBadgeCount(count: number): Promise<void> {
    PushNotification.setApplicationIconBadgeNumber(count);
  }

  async clearBadge(): Promise<void> {
    PushNotification.setApplicationIconBadgeNumber(0);
  }

  // Utility methods
  async requestPermission(): Promise<boolean> {
    try {
      const authStatus = await messaging().requestPermission();
      return (
        authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
        authStatus === messaging.AuthorizationStatus.PROVISIONAL
      );
    } catch (error) {
      console.error('Failed to request notification permission:', error);
      return false;
    }
  }

  async checkPermission(): Promise<boolean> {
    try {
      const authStatus = await messaging().hasPermission();
      return (
        authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
        authStatus === messaging.AuthorizationStatus.PROVISIONAL
      );
    } catch (error) {
      console.error('Failed to check notification permission:', error);
      return false;
    }
  }

  getToken(): string | null {
    return this.fcmToken;
  }

  // Business logic helpers
  async setupPaymentNotifications(userId: string): Promise<void> {
    await this.subscribeToTopic(`user_${userId}_payments`);
    await this.subscribeToTopic(`user_${userId}_requests`);
  }

  async setupSecurityNotifications(userId: string): Promise<void> {
    await this.subscribeToTopic(`user_${userId}_security`);
  }

  async removeAllSubscriptions(userId: string): Promise<void> {
    await this.unsubscribeFromTopic(`user_${userId}_payments`);
    await this.unsubscribeFromTopic(`user_${userId}_requests`);
    await this.unsubscribeFromTopic(`user_${userId}_security`);
  }

  // Analytics
  async trackNotificationInteraction(
    notificationId: string,
    action: 'opened' | 'dismissed' | 'action_taken'
  ): Promise<void> {
    try {
      await apiClient.post('/notifications/analytics', {
        notificationId,
        action,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error('Failed to track notification interaction:', error);
    }
  }
}

export const notificationService = new NotificationService();

/**
 * Setup push notifications
 */
export const setupNotifications = async (): Promise<void> => {
  return notificationService.setupPaymentNotifications('');
};

/**
 * Request notification permissions
 */
export const requestNotificationPermissions = async (): Promise<boolean> => {
  return notificationService.requestPermission();
};

export default notificationService;