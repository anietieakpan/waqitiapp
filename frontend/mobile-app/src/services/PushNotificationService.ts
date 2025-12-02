import messaging, { FirebaseMessagingTypes } from '@react-native-firebase/messaging';
import notifee, { AndroidImportance, AndroidStyle, EventType } from '@notifee/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import { store } from '../store';
import { addNotification, updateNotificationStatus } from '../store/slices/notificationSlice';
import { NavigationService } from './NavigationService';
import { ApiService } from './ApiService';
import { SoundManager } from './SoundManager';
import { HapticService } from './HapticService';

interface NotificationPayload {
  id: string;
  type: 'payment' | 'request' | 'message' | 'security' | 'promotion';
  title: string;
  body: string;
  data?: {
    transactionId?: string;
    conversationId?: string;
    amount?: string;
    currency?: string;
    senderId?: string;
    senderName?: string;
    imageUrl?: string;
    deepLink?: string;
  };
  priority: 'high' | 'normal' | 'low';
  badge?: number;
}

class PushNotificationService {
  private static instance: PushNotificationService;
  private isInitialized = false;
  private fcmToken: string | null = null;
  private notificationChannels = {
    payment: 'payment-channel',
    request: 'request-channel',
    message: 'message-channel',
    security: 'security-channel',
    promotion: 'promotion-channel',
  };

  static getInstance(): PushNotificationService {
    if (!PushNotificationService.instance) {
      PushNotificationService.instance = new PushNotificationService();
    }
    return PushNotificationService.instance;
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Request permissions
      const authStatus = await this.requestPermissions();
      if (authStatus === messaging.AuthorizationStatus.AUTHORIZED) {
        // Get FCM token
        await this.registerForPushNotifications();

        // Create notification channels for Android
        if (Platform.OS === 'android') {
          await this.createNotificationChannels();
        }

        // Set up message handlers
        this.setupMessageHandlers();

        // Set up Notifee handlers
        this.setupNotifeeHandlers();

        // Handle initial notification if app was opened from notification
        await this.handleInitialNotification();

        this.isInitialized = true;
        console.log('Push notifications initialized successfully');
      } else {
        console.log('Push notification permission denied');
      }
    } catch (error) {
      console.error('Failed to initialize push notifications:', error);
    }
  }

  private async requestPermissions(): Promise<FirebaseMessagingTypes.AuthorizationStatus> {
    const authStatus = await messaging().requestPermission({
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
      criticalAlert: true,
      announcement: true,
    });

    return authStatus;
  }

  private async registerForPushNotifications(): Promise<void> {
    try {
      // Get FCM token
      this.fcmToken = await messaging().getToken();
      
      // Store token locally
      await AsyncStorage.setItem('fcmToken', this.fcmToken);
      
      // Register token with backend
      await ApiService.registerDeviceToken({
        token: this.fcmToken,
        platform: Platform.OS,
        deviceId: await this.getDeviceId(),
      });

      // Listen for token refresh
      messaging().onTokenRefresh(async (token) => {
        this.fcmToken = token;
        await AsyncStorage.setItem('fcmToken', token);
        await ApiService.updateDeviceToken({
          token,
          platform: Platform.OS,
          deviceId: await this.getDeviceId(),
        });
      });
    } catch (error) {
      console.error('Failed to register for push notifications:', error);
    }
  }

  private async createNotificationChannels(): Promise<void> {
    // Payment channel - highest priority
    await notifee.createChannel({
      id: this.notificationChannels.payment,
      name: 'Payment Notifications',
      description: 'Notifications for payments and transactions',
      importance: AndroidImportance.HIGH,
      sound: 'payment_sound',
      vibration: true,
      lights: true,
      lightColor: '#4CAF50',
    });

    // Request channel
    await notifee.createChannel({
      id: this.notificationChannels.request,
      name: 'Money Requests',
      description: 'Notifications for money requests',
      importance: AndroidImportance.HIGH,
      sound: 'request_sound',
      vibration: true,
    });

    // Message channel
    await notifee.createChannel({
      id: this.notificationChannels.message,
      name: 'Messages',
      description: 'Chat and message notifications',
      importance: AndroidImportance.DEFAULT,
      sound: 'message_sound',
    });

    // Security channel - critical
    await notifee.createChannel({
      id: this.notificationChannels.security,
      name: 'Security Alerts',
      description: 'Important security notifications',
      importance: AndroidImportance.HIGH,
      sound: 'security_alert',
      vibration: true,
      lights: true,
      lightColor: '#F44336',
      bypassDnd: true,
    });

    // Promotion channel - low priority
    await notifee.createChannel({
      id: this.notificationChannels.promotion,
      name: 'Promotions',
      description: 'Promotional offers and updates',
      importance: AndroidImportance.LOW,
    });
  }

  private setupMessageHandlers(): void {
    // Handle foreground messages
    messaging().onMessage(async (remoteMessage) => {
      console.log('Foreground notification received:', remoteMessage);
      await this.handleNotification(remoteMessage, 'foreground');
    });

    // Handle background messages
    messaging().setBackgroundMessageHandler(async (remoteMessage) => {
      console.log('Background notification received:', remoteMessage);
      await this.handleNotification(remoteMessage, 'background');
    });

    // Handle notification opened app from background
    messaging().onNotificationOpenedApp(async (remoteMessage) => {
      console.log('Notification opened app:', remoteMessage);
      await this.handleNotificationPress(remoteMessage.data);
    });
  }

  private setupNotifeeHandlers(): void {
    notifee.onForegroundEvent(async ({ type, detail }) => {
      switch (type) {
        case EventType.PRESS:
          await this.handleNotificationPress(detail.notification?.data);
          break;
        
        case EventType.ACTION_PRESS:
          await this.handleNotificationAction(detail.pressAction?.id, detail.notification);
          break;
        
        case EventType.DISMISSED:
          console.log('Notification dismissed:', detail.notification?.id);
          break;
      }
    });

    notifee.onBackgroundEvent(async ({ type, detail }) => {
      if (type === EventType.PRESS) {
        await this.handleNotificationPress(detail.notification?.data);
      }
    });
  }

  private async handleInitialNotification(): Promise<void> {
    // Check if app was opened from a notification
    const initialNotification = await messaging().getInitialNotification();
    if (initialNotification) {
      await this.handleNotificationPress(initialNotification.data);
    }
  }

  private async handleNotification(
    remoteMessage: FirebaseMessagingTypes.RemoteMessage,
    state: 'foreground' | 'background'
  ): Promise<void> {
    try {
      const payload = this.parseNotificationPayload(remoteMessage);
      
      // Store in Redux
      store.dispatch(addNotification({
        ...payload,
        receivedAt: new Date().toISOString(),
        read: false,
      }));

      // Play sound and haptic feedback
      if (payload.priority === 'high') {
        SoundManager.playNotificationSound(payload.type);
        HapticService.notification(payload.type);
      }

      // Display local notification if in foreground
      if (state === 'foreground') {
        await this.displayLocalNotification(payload);
      }

      // Update badge count
      await this.updateBadgeCount(payload.badge);

    } catch (error) {
      console.error('Failed to handle notification:', error);
    }
  }

  private parseNotificationPayload(
    remoteMessage: FirebaseMessagingTypes.RemoteMessage
  ): NotificationPayload {
    const { notification, data } = remoteMessage;
    
    return {
      id: remoteMessage.messageId || Date.now().toString(),
      type: (data?.type as NotificationPayload['type']) || 'payment',
      title: notification?.title || 'Waqiti',
      body: notification?.body || '',
      data: {
        transactionId: data?.transactionId,
        conversationId: data?.conversationId,
        amount: data?.amount,
        currency: data?.currency,
        senderId: data?.senderId,
        senderName: data?.senderName,
        imageUrl: notification?.android?.imageUrl || notification?.ios?.imageUrl,
        deepLink: data?.deepLink,
      },
      priority: (data?.priority as NotificationPayload['priority']) || 'normal',
      badge: notification?.badge ? parseInt(notification.badge as string, 10) : undefined,
    };
  }

  private async displayLocalNotification(payload: NotificationPayload): Promise<void> {
    const channelId = this.notificationChannels[payload.type];
    
    const notificationConfig: any = {
      id: payload.id,
      title: payload.title,
      body: payload.body,
      data: payload.data,
      android: {
        channelId,
        smallIcon: 'ic_notification',
        largeIcon: payload.data?.imageUrl,
        pressAction: {
          id: 'default',
          launchActivity: 'default',
        },
        importance: this.getAndroidImportance(payload.priority),
        showTimestamp: true,
        timestamp: Date.now(),
      },
      ios: {
        sound: `${payload.type}_sound.wav`,
        critical: payload.priority === 'high',
        attachments: payload.data?.imageUrl ? [{
          url: payload.data.imageUrl,
        }] : [],
      },
    };

    // Add actions based on notification type
    if (payload.type === 'payment') {
      notificationConfig.android.actions = [
        {
          title: 'View',
          pressAction: { id: 'view_transaction' },
        },
        {
          title: 'Reply',
          pressAction: { id: 'quick_reply' },
          input: true,
        },
      ];
    } else if (payload.type === 'request') {
      notificationConfig.android.actions = [
        {
          title: 'Accept',
          pressAction: { id: 'accept_request' },
        },
        {
          title: 'Decline',
          pressAction: { id: 'decline_request' },
        },
      ];
    }

    // Add big picture style for Android if image is available
    if (payload.data?.imageUrl && Platform.OS === 'android') {
      notificationConfig.android.style = {
        type: AndroidStyle.BIGPICTURE,
        picture: payload.data.imageUrl,
      };
    }

    await notifee.displayNotification(notificationConfig);
  }

  private async handleNotificationPress(data?: any): Promise<void> {
    if (!data) return;

    try {
      // Handle deep linking
      if (data.deepLink) {
        NavigationService.navigate(data.deepLink);
        return;
      }

      // Handle specific notification types
      switch (data.type) {
        case 'payment':
          if (data.transactionId) {
            NavigationService.navigate('TransactionDetails', {
              transactionId: data.transactionId,
            });
          }
          break;
        
        case 'request':
          if (data.transactionId) {
            NavigationService.navigate('RequestDetails', {
              requestId: data.transactionId,
            });
          }
          break;
        
        case 'message':
          if (data.conversationId) {
            NavigationService.navigate('Chat', {
              conversationId: data.conversationId,
            });
          }
          break;
        
        case 'security':
          NavigationService.navigate('Security');
          break;
        
        default:
          NavigationService.navigate('Notifications');
      }
    } catch (error) {
      console.error('Failed to handle notification press:', error);
    }
  }

  private async handleNotificationAction(
    actionId?: string,
    notification?: any
  ): Promise<void> {
    if (!actionId || !notification) return;

    try {
      switch (actionId) {
        case 'view_transaction':
          await this.handleNotificationPress(notification.data);
          break;
        
        case 'quick_reply':
          // Handle quick reply
          const input = notification.input;
          if (input && notification.data?.conversationId) {
            await ApiService.sendMessage({
              conversationId: notification.data.conversationId,
              message: input,
            });
          }
          break;
        
        case 'accept_request':
          if (notification.data?.transactionId) {
            await ApiService.acceptMoneyRequest(notification.data.transactionId);
          }
          break;
        
        case 'decline_request':
          if (notification.data?.transactionId) {
            await ApiService.declineMoneyRequest(notification.data.transactionId);
          }
          break;
      }
    } catch (error) {
      console.error('Failed to handle notification action:', error);
    }
  }

  private async updateBadgeCount(badge?: number): Promise<void> {
    if (badge !== undefined) {
      await notifee.setBadgeCount(badge);
    } else {
      // Get unread count from store
      const state = store.getState();
      const unreadCount = state.notifications.unreadCount || 0;
      await notifee.setBadgeCount(unreadCount);
    }
  }

  private getAndroidImportance(priority: string): AndroidImportance {
    switch (priority) {
      case 'high':
        return AndroidImportance.HIGH;
      case 'low':
        return AndroidImportance.LOW;
      default:
        return AndroidImportance.DEFAULT;
    }
  }

  private async getDeviceId(): Promise<string> {
    let deviceId = await AsyncStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = `${Platform.OS}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      await AsyncStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  }

  async cancelNotification(notificationId: string): Promise<void> {
    await notifee.cancelNotification(notificationId);
  }

  async cancelAllNotifications(): Promise<void> {
    await notifee.cancelAllNotifications();
  }

  async getDeliveredNotifications(): Promise<any[]> {
    return await notifee.getDisplayedNotifications();
  }

  async scheduleNotification(
    notification: NotificationPayload,
    date: Date
  ): Promise<string> {
    const trigger = {
      type: notifee.TriggerType.TIMESTAMP,
      timestamp: date.getTime(),
    };

    const notificationId = await notifee.createTriggerNotification(
      {
        id: notification.id,
        title: notification.title,
        body: notification.body,
        data: notification.data,
        android: {
          channelId: this.notificationChannels[notification.type],
        },
      },
      trigger
    );

    return notificationId;
  }

  getFCMToken(): string | null {
    return this.fcmToken;
  }
}

export default PushNotificationService.getInstance();