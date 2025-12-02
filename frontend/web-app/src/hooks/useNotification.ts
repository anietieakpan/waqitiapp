import { useState, useCallback, useEffect } from 'react';
import { useAppDispatch, useAppSelector } from './redux';
import { notificationService } from '../services/notificationService';
import { 
  addNotification, 
  markAsRead, 
  markAllAsRead, 
  deleteNotification,
  setNotifications
} from '../store/slices/notificationSlice';
import { Notification, NotificationPreferences } from '../types/notification';
import { toast } from 'react-hot-toast';

export const useNotification = () => {
  const dispatch = useAppDispatch();
  const { 
    notifications, 
    unreadCount, 
    loading 
  } = useAppSelector(state => state.notifications);
  
  const [localLoading, setLocalLoading] = useState(false);
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);

  const fetchNotifications = useCallback(async (page = 1, limit = 20) => {
    try {
      setLocalLoading(true);
      const result = await notificationService.getNotifications(page, limit);
      dispatch(setNotifications(result.notifications));
      return result;
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch notifications');
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const markNotificationAsRead = useCallback(async (notificationId: string) => {
    try {
      await notificationService.markAsRead(notificationId);
      dispatch(markAsRead(notificationId));
    } catch (error: any) {
      console.error('Failed to mark notification as read:', error);
    }
  }, [dispatch]);

  const markAllNotificationsAsRead = useCallback(async () => {
    try {
      setLocalLoading(true);
      await notificationService.markAllAsRead();
      dispatch(markAllAsRead());
      toast.success('All notifications marked as read');
    } catch (error: any) {
      toast.error(error.message || 'Failed to mark notifications as read');
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const deleteNotificationById = useCallback(async (notificationId: string) => {
    try {
      await notificationService.deleteNotification(notificationId);
      dispatch(deleteNotification(notificationId));
      toast.success('Notification deleted');
    } catch (error: any) {
      toast.error(error.message || 'Failed to delete notification');
    }
  }, [dispatch]);

  const fetchPreferences = useCallback(async () => {
    try {
      setLocalLoading(true);
      const prefs = await notificationService.getPreferences();
      setPreferences(prefs);
      return prefs;
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch notification preferences');
    } finally {
      setLocalLoading(false);
    }
  }, []);

  const updatePreferences = useCallback(async (newPreferences: Partial<NotificationPreferences>) => {
    try {
      setLocalLoading(true);
      const updatedPrefs = await notificationService.updatePreferences(newPreferences);
      setPreferences(updatedPrefs);
      toast.success('Notification preferences updated');
      return updatedPrefs;
    } catch (error: any) {
      toast.error(error.message || 'Failed to update notification preferences');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, []);

  const requestPushPermission = useCallback(async () => {
    try {
      if (!('Notification' in window)) {
        throw new Error('This browser does not support push notifications');
      }

      if (Notification.permission === 'granted') {
        return true;
      }

      if (Notification.permission === 'denied') {
        throw new Error('Push notifications are blocked. Please enable them in browser settings.');
      }

      const permission = await Notification.requestPermission();
      
      if (permission === 'granted') {
        // Register service worker and get push subscription
        await registerPushSubscription();
        toast.success('Push notifications enabled');
        return true;
      } else {
        throw new Error('Push notification permission denied');
      }
    } catch (error: any) {
      toast.error(error.message || 'Failed to enable push notifications');
      return false;
    }
  }, []);

  const registerPushSubscription = useCallback(async () => {
    try {
      if ('serviceWorker' in navigator && 'PushManager' in window) {
        const registration = await navigator.serviceWorker.ready;
        
        const subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: process.env.REACT_APP_VAPID_PUBLIC_KEY
        });

        // Send subscription to server
        await notificationService.registerPushSubscription(subscription);
      }
    } catch (error: any) {
      console.error('Failed to register push subscription:', error);
    }
  }, []);

  const sendTestNotification = useCallback(async () => {
    try {
      await notificationService.sendTestNotification();
      toast.success('Test notification sent');
    } catch (error: any) {
      toast.error(error.message || 'Failed to send test notification');
    }
  }, []);

  // Real-time notification handling
  useEffect(() => {
    const handleNewNotification = (event: CustomEvent<Notification>) => {
      const notification = event.detail;
      dispatch(addNotification(notification));
      
      // Show browser notification if permission granted
      if (Notification.permission === 'granted') {
        new Notification(notification.title, {
          body: notification.message,
          icon: '/favicon.ico',
          tag: notification.id
        });
      }
      
      // Show toast for important notifications
      if (notification.priority === 'HIGH' || notification.priority === 'URGENT') {
        toast.success(notification.title);
      }
    };

    window.addEventListener('newNotification', handleNewNotification as EventListener);
    
    return () => {
      window.removeEventListener('newNotification', handleNewNotification as EventListener);
    };
  }, [dispatch]);

  // WebSocket connection for real-time notifications
  useEffect(() => {
    const connectWebSocket = () => {
      const wsUrl = `${process.env.REACT_APP_WS_URL}/notifications`;
      const ws = new WebSocket(wsUrl);
      
      ws.onmessage = (event) => {
        try {
          const notification = JSON.parse(event.data);
          dispatch(addNotification(notification));
        } catch (error) {
          console.error('Failed to parse notification:', error);
        }
      };
      
      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
      };
      
      return ws;
    };

    let ws: WebSocket | null = null;
    
    if (process.env.REACT_APP_WS_URL) {
      ws = connectWebSocket();
    }
    
    return () => {
      if (ws) {
        ws.close();
      }
    };
  }, [dispatch]);

  // Utility functions
  const getUnreadNotifications = useCallback(() => {
    return notifications.filter(n => !n.read);
  }, [notifications]);

  const getNotificationsByType = useCallback((type: string) => {
    return notifications.filter(n => n.type === type);
  }, [notifications]);

  const getRecentNotifications = useCallback((hours = 24) => {
    const cutoff = new Date(Date.now() - hours * 60 * 60 * 1000);
    return notifications.filter(n => new Date(n.createdAt) > cutoff);
  }, [notifications]);

  return {
    // State
    notifications,
    unreadCount,
    loading: loading || localLoading,
    preferences,
    
    // Actions
    fetchNotifications,
    markNotificationAsRead,
    markAllNotificationsAsRead,
    deleteNotificationById,
    fetchPreferences,
    updatePreferences,
    requestPushPermission,
    sendTestNotification,
    
    // Utilities
    getUnreadNotifications,
    getNotificationsByType,
    getRecentNotifications
  };
};