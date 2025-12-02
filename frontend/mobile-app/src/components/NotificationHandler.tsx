import React, { useEffect, useRef } from 'react';
import { Platform, Alert, AppState, AppStateStatus } from 'react-native';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import { useNavigation } from '@react-navigation/native';

import { useAppDispatch, useAppSelector } from '../store/hooks';
import { 
  registerPushToken, 
  updateNotificationPermissions,
  markNotificationAsRead,
  addNotification
} from '../store/slices/notificationSlice';
import { showToast } from '../utils/toast';
import { hapticFeedback } from '../utils/haptics';

// Configure notification behavior
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

interface NotificationHandlerProps {
  children: React.ReactNode;
}

export const NotificationHandler: React.FC<NotificationHandlerProps> = ({ children }) => {
  const navigation = useNavigation();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { permissions } = useAppSelector((state) => state.notification);

  const notificationListener = useRef<any>();
  const responseListener = useRef<any>();
  const appState = useRef(AppState.currentState);

  useEffect(() => {
    if (user) {
      registerForPushNotificationsAsync();
    }

    // Listen for incoming notifications
    notificationListener.current = Notifications.addNotificationReceivedListener(handleNotificationReceived);

    // Listen for notification responses (when user taps notification)
    responseListener.current = Notifications.addNotificationResponseReceivedListener(handleNotificationResponse);

    // Listen for app state changes
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      Notifications.removeNotificationSubscription(notificationListener.current);
      Notifications.removeNotificationSubscription(responseListener.current);
      subscription?.remove();
    };
  }, [user]);

  const registerForPushNotificationsAsync = async () => {
    try {
      if (!Device.isDevice) {
        console.log('Push notifications only work on physical devices');
        return;
      }

      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;

      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }

      if (finalStatus !== 'granted') {
        Alert.alert(
          'Push Notifications',
          'Push notifications are required to receive payment alerts and important updates. You can enable them in Settings.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Settings', onPress: () => Notifications.openSettingsAsync() },
          ]
        );
        return;
      }

      // Update permissions in store
      dispatch(updateNotificationPermissions({
        push: finalStatus === 'granted',
        sound: true,
        badge: true,
      }));

      // Get the push token
      const token = (await Notifications.getExpoPushTokenAsync()).data;
      
      // Register token with backend
      dispatch(registerPushToken({
        token,
        platform: Platform.OS,
        deviceId: await getDeviceId(),
      }));

      // Configure notification categories
      await configureNotificationCategories();

    } catch (error) {
      console.error('Error registering for push notifications:', error);
      showToast('Failed to register for push notifications');
    }
  };

  const getDeviceId = async (): Promise<string> => {
    try {
      // In a real app, you'd use a library like react-native-device-info
      return Device.modelName || 'unknown-device';
    } catch (error) {
      return 'unknown-device';
    }
  };

  const configureNotificationCategories = async () => {
    try {
      await Notifications.setNotificationCategoryAsync('payment_received', [
        {
          identifier: 'view_transaction',
          buttonTitle: 'View',
          options: { foreground: true },
        },
        {
          identifier: 'thank_sender',
          buttonTitle: 'Thank',
          options: { foreground: false },
        },
      ]);

      await Notifications.setNotificationCategoryAsync('payment_request', [
        {
          identifier: 'pay_now',
          buttonTitle: 'Pay Now',
          options: { foreground: true },
        },
        {
          identifier: 'decline_request',
          buttonTitle: 'Decline',
          options: { foreground: false },
        },
      ]);

      await Notifications.setNotificationCategoryAsync('security_alert', [
        {
          identifier: 'secure_account',
          buttonTitle: 'Secure Account',
          options: { foreground: true },
        },
      ]);

    } catch (error) {
      console.error('Error configuring notification categories:', error);
    }
  };

  const handleNotificationReceived = (notification: Notifications.Notification) => {
    console.log('Notification received:', notification);
    
    const { title, body, data } = notification.request.content;
    
    // Add notification to local store
    dispatch(addNotification({
      id: notification.request.identifier,
      title: title || '',
      body: body || '',
      data: data || {},
      timestamp: Date.now(),
      read: false,
    }));

    // Handle different notification types
    handleNotificationByType(notification);
    
    // Provide haptic feedback
    hapticFeedback.notification();
  };

  const handleNotificationResponse = (response: Notifications.NotificationResponse) => {
    console.log('Notification response:', response);
    
    const { notification, actionIdentifier } = response;
    const { data } = notification.request.content;

    // Mark notification as read
    dispatch(markNotificationAsRead(notification.request.identifier));

    // Handle action buttons
    if (actionIdentifier && actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) {
      handleNotificationAction(actionIdentifier, data);
      return;
    }

    // Handle default tap action
    handleNotificationNavigation(data);
  };

  const handleNotificationByType = (notification: Notifications.Notification) => {
    const { data } = notification.request.content;
    const type = data?.type;

    switch (type) {
      case 'payment_received':
        hapticFeedback.success();
        break;
      case 'payment_request':
      case 'payment_reminder':
        hapticFeedback.light();
        break;
      case 'security_alert':
      case 'login_attempt':
        hapticFeedback.warning();
        break;
      case 'transaction_failed':
      case 'account_locked':
        hapticFeedback.error();
        break;
      default:
        hapticFeedback.light();
    }
  };

  const handleNotificationAction = (actionIdentifier: string, data: any) => {
    switch (actionIdentifier) {
      case 'view_transaction':
        if (data?.transactionId) {
          navigation.navigate('TransactionDetails', { id: data.transactionId });
        }
        break;

      case 'thank_sender':
        if (data?.senderId) {
          // Send a thank you message
          showToast('Thank you message sent!');
        }
        break;

      case 'pay_now':
        if (data?.requestId) {
          navigation.navigate('PayRequest', { requestId: data.requestId });
        }
        break;

      case 'decline_request':
        if (data?.requestId) {
          // Handle request decline
          showToast('Payment request declined');
        }
        break;

      case 'secure_account':
        navigation.navigate('SecuritySettings');
        break;

      default:
        console.log('Unknown notification action:', actionIdentifier);
    }
  };

  const handleNotificationNavigation = (data: any) => {
    const type = data?.type;

    switch (type) {
      case 'payment_received':
      case 'payment_sent':
        if (data?.transactionId) {
          navigation.navigate('TransactionDetails', { id: data.transactionId });
        } else {
          navigation.navigate('Transactions');
        }
        break;

      case 'payment_request':
        if (data?.requestId) {
          navigation.navigate('PaymentRequests');
        }
        break;

      case 'contact_request':
        navigation.navigate('Contacts');
        break;

      case 'security_alert':
      case 'login_attempt':
        navigation.navigate('SecuritySettings');
        break;

      case 'account_verification':
        navigation.navigate('VerifyAccount');
        break;

      case 'card_transaction':
        navigation.navigate('CardTransactions');
        break;

      case 'promotion':
      case 'announcement':
        if (data?.url) {
          // Open external URL or specific screen
          navigation.navigate('WebView', { url: data.url });
        } else {
          navigation.navigate('Notifications');
        }
        break;

      default:
        navigation.navigate('Notifications');
    }
  };

  const handleAppStateChange = (nextAppState: AppStateStatus) => {
    if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
      // App has come to the foreground
      console.log('App has come to the foreground');
      
      // Clear notification badge
      Notifications.setBadgeCountAsync(0);
      
      // Refresh notification permissions
      checkNotificationPermissions();
    }
    
    appState.current = nextAppState;
  };

  const checkNotificationPermissions = async () => {
    try {
      const { status } = await Notifications.getPermissionsAsync();
      
      dispatch(updateNotificationPermissions({
        push: status === 'granted',
        sound: true,
        badge: true,
      }));

    } catch (error) {
      console.error('Error checking notification permissions:', error);
    }
  };

  // Utility functions for sending local notifications
  const scheduleLocalNotification = async (
    title: string, 
    body: string, 
    data: any = {}, 
    seconds: number = 0
  ) => {
    try {
      const identifier = await Notifications.scheduleNotificationAsync({
        content: {
          title,
          body,
          data,
          sound: true,
          badge: 1,
        },
        trigger: seconds > 0 ? { seconds } : null,
      });
      
      return identifier;
    } catch (error) {
      console.error('Error scheduling local notification:', error);
      return null;
    }
  };

  const cancelScheduledNotification = async (identifier: string) => {
    try {
      await Notifications.cancelScheduledNotificationAsync(identifier);
    } catch (error) {
      console.error('Error canceling scheduled notification:', error);
    }
  };

  const cancelAllScheduledNotifications = async () => {
    try {
      await Notifications.cancelAllScheduledNotificationsAsync();
    } catch (error) {
      console.error('Error canceling all scheduled notifications:', error);
    }
  };

  // Expose utility functions through context if needed
  return (
    <>
      {children}
    </>
  );
};

// Hook for accessing notification utilities
export const useNotifications = () => {
  const scheduleLocalNotification = async (
    title: string, 
    body: string, 
    data: any = {}, 
    seconds: number = 0
  ) => {
    try {
      const identifier = await Notifications.scheduleNotificationAsync({
        content: {
          title,
          body,
          data,
          sound: true,
          badge: 1,
        },
        trigger: seconds > 0 ? { seconds } : null,
      });
      
      return identifier;
    } catch (error) {
      console.error('Error scheduling local notification:', error);
      return null;
    }
  };

  const clearBadge = () => {
    Notifications.setBadgeCountAsync(0);
  };

  const requestPermissions = async () => {
    try {
      const { status } = await Notifications.requestPermissionsAsync();
      return status === 'granted';
    } catch (error) {
      console.error('Error requesting notification permissions:', error);
      return false;
    }
  };

  return {
    scheduleLocalNotification,
    clearBadge,
    requestPermissions,
  };
};

export default NotificationHandler;