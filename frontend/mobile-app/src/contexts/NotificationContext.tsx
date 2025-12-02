/**
 * NotificationContext - Mobile notification context
 * Provides notification state and methods for the mobile app
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Notification action
interface NotificationAction {
  id: string;
  label: string;
  actionType: 'primary' | 'secondary' | 'destructive';
  handler?: () => void;
}

// Notification category
export type NotificationCategory = 
  | 'transaction'
  | 'security'
  | 'marketing'
  | 'system'
  | 'social'
  | 'reminder'
  | 'achievement';

// Rich notification content
interface RichContent {
  imageUrl?: string;
  thumbnailUrl?: string;
  largeIcon?: string;
  color?: string;
  progressBar?: {
    value: number;
    max: number;
    indeterminate?: boolean;
  };
  buttons?: NotificationAction[];
  expandedView?: {
    bigText?: string;
    bigPicture?: string;
    inboxStyle?: string[];
  };
}

// Notification types
interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
  category: NotificationCategory;
  timestamp: number;
  read: boolean;
  actionUrl?: string;
  metadata?: Record<string, any>;
  priority: 'low' | 'normal' | 'high' | 'urgent';
  persistent?: boolean;
  richContent?: RichContent;
  groupKey?: string;
  tag?: string;
  channelId?: string;
  sound?: string;
  vibrationPattern?: number[];
  ledColor?: string;
  badgeNumber?: number;
  autoCancel?: boolean;
  deliveryTime?: number; // Scheduled delivery time
  expirationTime?: number; // When notification expires
}

// Notification channel settings
interface NotificationChannelSettings {
  enabled: boolean;
  sound: boolean;
  vibration: boolean;
  priority: 'low' | 'normal' | 'high' | 'urgent';
  showBadge: boolean;
  ledColor?: string;
}

// Notification settings
interface NotificationSettings {
  pushEnabled: boolean;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  transactionAlerts: boolean;
  securityAlerts: boolean;
  marketingOffers: boolean;
  quietHoursEnabled: boolean;
  quietHoursStart: string; // HH:MM format
  quietHoursEnd: string; // HH:MM format
  
  // Channel-specific settings
  channels: {
    transaction: NotificationChannelSettings;
    security: NotificationChannelSettings;
    marketing: NotificationChannelSettings;
    system: NotificationChannelSettings;
    social: NotificationChannelSettings;
    reminder: NotificationChannelSettings;
    achievement: NotificationChannelSettings;
  };
  
  // Rich notification preferences
  showImages: boolean;
  showActions: boolean;
  groupNotifications: boolean;
  notificationTone: string;
  ledColorEnabled: boolean;
  headsUpDisplay: boolean;
  lockScreenVisibility: 'public' | 'private' | 'secret';
}

// Notification state
interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  settings: NotificationSettings | null;
  loading: boolean;
  error: string | null;
}

// NotificationContext interface
interface NotificationContextType extends NotificationState {
  // Basic notification operations
  addNotification: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  markAsRead: (notificationId: string) => void;
  markAllAsRead: () => void;
  removeNotification: (notificationId: string) => void;
  clearAllNotifications: () => void;
  
  // Rich notification operations
  scheduleNotification: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>, deliveryTime: Date) => Promise<void>;
  cancelScheduledNotification: (notificationId: string) => Promise<void>;
  updateNotification: (notificationId: string, updates: Partial<Notification>) => void;
  groupNotifications: (groupKey: string) => Notification[];
  getNotificationsByCategory: (category: NotificationCategory) => Notification[];
  clearNotificationGroup: (groupKey: string) => void;
  
  // Settings management
  updateSettings: (settings: Partial<NotificationSettings>) => Promise<void>;
  updateChannelSettings: (channel: NotificationCategory, settings: Partial<NotificationChannelSettings>) => Promise<void>;
  getSettings: () => Promise<NotificationSettings>;
  
  // Display methods
  showToast: (message: string, type?: 'info' | 'success' | 'warning' | 'error', duration?: number) => void;
  showRichNotification: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => Promise<void>;
  showInAppNotification: (notification: Notification) => void;
  
  // Badge management
  updateBadgeCount: (count?: number) => void;
  getBadgeCount: () => number;
  
  // Error handling
  clearError: () => void;
}

// Default channel settings
const defaultChannelSettings: NotificationChannelSettings = {
  enabled: true,
  sound: true,
  vibration: true,
  priority: 'normal',
  showBadge: true,
  ledColor: '#007AFF',
};

// Default settings
const defaultSettings: NotificationSettings = {
  pushEnabled: true,
  soundEnabled: true,
  vibrationEnabled: true,
  transactionAlerts: true,
  securityAlerts: true,
  marketingOffers: false,
  quietHoursEnabled: false,
  quietHoursStart: '22:00',
  quietHoursEnd: '08:00',
  
  // Channel-specific settings
  channels: {
    transaction: { ...defaultChannelSettings, priority: 'high' },
    security: { ...defaultChannelSettings, priority: 'urgent', ledColor: '#FF0000' },
    marketing: { ...defaultChannelSettings, priority: 'low', enabled: false },
    system: { ...defaultChannelSettings, priority: 'high' },
    social: { ...defaultChannelSettings, priority: 'normal' },
    reminder: { ...defaultChannelSettings, priority: 'high' },
    achievement: { ...defaultChannelSettings, priority: 'normal', ledColor: '#00FF00' },
  },
  
  // Rich notification preferences
  showImages: true,
  showActions: true,
  groupNotifications: true,
  notificationTone: 'default',
  ledColorEnabled: true,
  headsUpDisplay: true,
  lockScreenVisibility: 'private',
};

// Default state
const defaultState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  settings: null,
  loading: true,
  error: null,
};

// Create context
const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

// Custom hook
export const useNotification = () => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotification must be used within a NotificationProvider');
  }
  return context;
};

// NotificationProvider props
interface NotificationProviderProps {
  children: ReactNode;
}

// Storage keys
const STORAGE_KEYS = {
  NOTIFICATIONS: '@waqiti_notifications',
  NOTIFICATION_SETTINGS: '@waqiti_notification_settings',
};

// NotificationProvider component
export const NotificationProvider: React.FC<NotificationProviderProps> = ({ children }) => {
  const [state, setState] = useState<NotificationState>(defaultState);

  // Initialize notification state
  useEffect(() => {
    initializeNotifications();
  }, []);

  /**
   * Initialize notifications from storage
   */
  const initializeNotifications = async () => {
    try {
      const [notificationsJson, settingsJson] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEYS.NOTIFICATIONS),
        AsyncStorage.getItem(STORAGE_KEYS.NOTIFICATION_SETTINGS),
      ]);

      const notifications: Notification[] = notificationsJson ? JSON.parse(notificationsJson) : [];
      const settings: NotificationSettings = settingsJson 
        ? { ...defaultSettings, ...JSON.parse(settingsJson) }
        : defaultSettings;

      const unreadCount = notifications.filter(n => !n.read).length;

      setState({
        notifications,
        unreadCount,
        settings,
        loading: false,
        error: null,
      });
    } catch (error) {
      console.error('Failed to initialize notifications:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        error: 'Failed to load notifications',
      }));
    }
  };

  /**
   * Save notifications to storage
   */
  const saveNotifications = async (notifications: Notification[]) => {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.NOTIFICATIONS, JSON.stringify(notifications));
    } catch (error) {
      console.error('Failed to save notifications:', error);
    }
  };

  /**
   * Generate unique ID for notification
   */
  const generateId = (): string => {
    return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  };

  /**
   * Add new notification
   */
  const addNotification = (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => {
    const newNotification: Notification = {
      category: 'system',
      priority: 'normal',
      autoCancel: true,
      ...notification,
      id: generateId(),
      timestamp: Date.now(),
      read: false,
    };

    // Check if notification should be displayed based on settings
    if (!shouldDisplayNotification(newNotification)) {
      return;
    }

    setState(prev => {
      let updatedNotifications = [...prev.notifications];
      
      // Handle notification grouping
      if (state.settings?.groupNotifications && newNotification.groupKey) {
        updatedNotifications = groupNotificationsByKey(updatedNotifications, newNotification);
      } else {
        updatedNotifications = [newNotification, ...updatedNotifications];
      }
      
      // Remove expired notifications
      updatedNotifications = removeExpiredNotifications(updatedNotifications);
      
      const unreadCount = updatedNotifications.filter(n => !n.read).length;
      
      // Save to storage
      saveNotifications(updatedNotifications);
      
      // Update badge count
      updateBadgeCount(unreadCount);
      
      return {
        ...prev,
        notifications: updatedNotifications,
        unreadCount,
      };
    });
    
    // Show system notification if app is in background
    if (state.settings?.pushEnabled) {
      showSystemNotification(newNotification);
    }
  };

  /**
   * Mark notification as read
   */
  const markAsRead = (notificationId: string) => {
    setState(prev => {
      const updatedNotifications = prev.notifications.map(notification =>
        notification.id === notificationId
          ? { ...notification, read: true }
          : notification
      );
      
      const unreadCount = updatedNotifications.filter(n => !n.read).length;
      
      // Save to storage
      saveNotifications(updatedNotifications);
      
      return {
        ...prev,
        notifications: updatedNotifications,
        unreadCount,
      };
    });
  };

  /**
   * Mark all notifications as read
   */
  const markAllAsRead = () => {
    setState(prev => {
      const updatedNotifications = prev.notifications.map(notification => ({
        ...notification,
        read: true,
      }));
      
      // Save to storage
      saveNotifications(updatedNotifications);
      
      return {
        ...prev,
        notifications: updatedNotifications,
        unreadCount: 0,
      };
    });
  };

  /**
   * Remove notification
   */
  const removeNotification = (notificationId: string) => {
    setState(prev => {
      const updatedNotifications = prev.notifications.filter(
        notification => notification.id !== notificationId
      );
      
      const unreadCount = updatedNotifications.filter(n => !n.read).length;
      
      // Save to storage
      saveNotifications(updatedNotifications);
      
      return {
        ...prev,
        notifications: updatedNotifications,
        unreadCount,
      };
    });
  };

  /**
   * Clear all notifications
   */
  const clearAllNotifications = () => {
    setState(prev => ({
      ...prev,
      notifications: [],
      unreadCount: 0,
    }));
    
    saveNotifications([]);
  };

  /**
   * Update notification settings
   */
  const updateSettings = async (settingsUpdate: Partial<NotificationSettings>): Promise<void> => {
    try {
      const currentSettings = state.settings || defaultSettings;
      const updatedSettings = { ...currentSettings, ...settingsUpdate };
      
      await AsyncStorage.setItem(
        STORAGE_KEYS.NOTIFICATION_SETTINGS,
        JSON.stringify(updatedSettings)
      );
      
      setState(prev => ({
        ...prev,
        settings: updatedSettings,
      }));
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        error: error.message || 'Failed to update notification settings',
      }));
      throw error;
    }
  };

  /**
   * Get notification settings
   */
  const getSettings = async (): Promise<NotificationSettings> => {
    try {
      const settingsJson = await AsyncStorage.getItem(STORAGE_KEYS.NOTIFICATION_SETTINGS);
      if (settingsJson) {
        return { ...defaultSettings, ...JSON.parse(settingsJson) };
      }
      return defaultSettings;
    } catch (error) {
      console.error('Failed to get notification settings:', error);
      return defaultSettings;
    }
  };

  /**
   * Check if notification should be displayed based on settings
   */
  const shouldDisplayNotification = (notification: Notification): boolean => {
    if (!state.settings) return true;
    
    const settings = state.settings;
    const channelSettings = settings.channels[notification.category];
    
    // Check if channel is enabled
    if (!channelSettings?.enabled) return false;
    
    // Check quiet hours
    if (settings.quietHoursEnabled && isInQuietHours(settings.quietHoursStart, settings.quietHoursEnd)) {
      // Only allow urgent notifications during quiet hours
      return notification.priority === 'urgent';
    }
    
    // Check category-specific settings
    switch (notification.category) {
      case 'transaction':
        return settings.transactionAlerts;
      case 'security':
        return settings.securityAlerts;
      case 'marketing':
        return settings.marketingOffers;
      default:
        return true;
    }
  };

  /**
   * Check if current time is within quiet hours
   */
  const isInQuietHours = (startTime: string, endTime: string): boolean => {
    const now = new Date();
    const [startHour, startMinute] = startTime.split(':').map(Number);
    const [endHour, endMinute] = endTime.split(':').map(Number);
    
    const currentMinutes = now.getHours() * 60 + now.getMinutes();
    const startMinutes = startHour * 60 + startMinute;
    const endMinutes = endHour * 60 + endMinute;
    
    if (startMinutes < endMinutes) {
      return currentMinutes >= startMinutes && currentMinutes < endMinutes;
    } else {
      // Quiet hours span midnight
      return currentMinutes >= startMinutes || currentMinutes < endMinutes;
    }
  };

  /**
   * Group notifications by key
   */
  const groupNotificationsByKey = (notifications: Notification[], newNotification: Notification): Notification[] => {
    const grouped = [...notifications];
    const existingGroupIndex = grouped.findIndex(n => n.groupKey === newNotification.groupKey && n.id !== newNotification.id);
    
    if (existingGroupIndex >= 0) {
      // Create a summary notification
      const group = notifications.filter(n => n.groupKey === newNotification.groupKey);
      const summaryNotification: Notification = {
        ...newNotification,
        id: `${newNotification.groupKey}_summary`,
        title: `${group.length + 1} new notifications`,
        message: `You have ${group.length + 1} new ${newNotification.category} notifications`,
        richContent: {
          ...newNotification.richContent,
          expandedView: {
            inboxStyle: [...group.map(n => n.title), newNotification.title],
          },
        },
      };
      
      // Remove individual notifications and add summary
      return [
        summaryNotification,
        ...grouped.filter(n => n.groupKey !== newNotification.groupKey),
      ];
    }
    
    return [newNotification, ...grouped];
  };

  /**
   * Remove expired notifications
   */
  const removeExpiredNotifications = (notifications: Notification[]): Notification[] => {
    const now = Date.now();
    return notifications.filter(n => !n.expirationTime || n.expirationTime > now);
  };

  /**
   * Show system notification
   */
  const showSystemNotification = async (notification: Notification) => {
    // This would integrate with react-native-push-notification or similar
    // Implementation depends on the notification library used
    console.log('Show system notification:', notification);
  };

  /**
   * Schedule notification
   */
  const scheduleNotification = async (
    notification: Omit<Notification, 'id' | 'timestamp' | 'read'>,
    deliveryTime: Date
  ): Promise<void> => {
    const scheduledNotification: Notification = {
      category: 'system',
      priority: 'normal',
      autoCancel: true,
      ...notification,
      id: generateId(),
      timestamp: Date.now(),
      deliveryTime: deliveryTime.getTime(),
      read: false,
    };
    
    // Store scheduled notifications separately
    const scheduledKey = `@waqiti_scheduled_${scheduledNotification.id}`;
    await AsyncStorage.setItem(scheduledKey, JSON.stringify(scheduledNotification));
    
    // Schedule with native notification system
    console.log('Schedule notification for:', deliveryTime);
  };

  /**
   * Cancel scheduled notification
   */
  const cancelScheduledNotification = async (notificationId: string): Promise<void> => {
    const scheduledKey = `@waqiti_scheduled_${notificationId}`;
    await AsyncStorage.removeItem(scheduledKey);
    
    // Cancel with native notification system
    console.log('Cancel scheduled notification:', notificationId);
  };

  /**
   * Update notification
   */
  const updateNotification = (notificationId: string, updates: Partial<Notification>) => {
    setState(prev => {
      const updatedNotifications = prev.notifications.map(notification =>
        notification.id === notificationId
          ? { ...notification, ...updates }
          : notification
      );
      
      const unreadCount = updatedNotifications.filter(n => !n.read).length;
      
      // Save to storage
      saveNotifications(updatedNotifications);
      
      return {
        ...prev,
        notifications: updatedNotifications,
        unreadCount,
      };
    });
  };

  /**
   * Get notifications by group
   */
  const groupNotifications = (groupKey: string): Notification[] => {
    return state.notifications.filter(n => n.groupKey === groupKey);
  };

  /**
   * Get notifications by category
   */
  const getNotificationsByCategory = (category: NotificationCategory): Notification[] => {
    return state.notifications.filter(n => n.category === category);
  };

  /**
   * Clear notification group
   */
  const clearNotificationGroup = (groupKey: string) => {
    setState(prev => {
      const updatedNotifications = prev.notifications.filter(n => n.groupKey !== groupKey);
      const unreadCount = updatedNotifications.filter(n => !n.read).length;
      
      // Save to storage
      saveNotifications(updatedNotifications);
      
      return {
        ...prev,
        notifications: updatedNotifications,
        unreadCount,
      };
    });
  };

  /**
   * Update channel settings
   */
  const updateChannelSettings = async (
    channel: NotificationCategory,
    settings: Partial<NotificationChannelSettings>
  ): Promise<void> => {
    try {
      const currentSettings = state.settings || defaultSettings;
      const updatedSettings = {
        ...currentSettings,
        channels: {
          ...currentSettings.channels,
          [channel]: {
            ...currentSettings.channels[channel],
            ...settings,
          },
        },
      };
      
      await AsyncStorage.setItem(
        STORAGE_KEYS.NOTIFICATION_SETTINGS,
        JSON.stringify(updatedSettings)
      );
      
      setState(prev => ({
        ...prev,
        settings: updatedSettings,
      }));
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        error: error.message || 'Failed to update channel settings',
      }));
      throw error;
    }
  };

  /**
   * Show toast notification (temporary notification)
   */
  const showToast = (message: string, type: 'info' | 'success' | 'warning' | 'error' = 'info', duration: number = 3000) => {
    // TODO: Implement toast notification system
    // This could use a library like react-native-toast-message
    console.log(`Toast [${type}]: ${message} (duration: ${duration}ms)`);
  };

  /**
   * Show rich notification
   */
  const showRichNotification = async (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>): Promise<void> => {
    const richNotification: Notification = {
      category: 'system',
      priority: 'normal',
      autoCancel: true,
      ...notification,
      id: generateId(),
      timestamp: Date.now(),
      read: false,
    };
    
    // Add to notification list
    addNotification(richNotification);
    
    // Show as system notification with rich content
    if (state.settings?.pushEnabled && state.settings?.showImages && richNotification.richContent) {
      // Implementation would use native notification system
      console.log('Show rich notification:', richNotification);
    }
  };

  /**
   * Show in-app notification
   */
  const showInAppNotification = (notification: Notification) => {
    // This would trigger an in-app notification UI component
    console.log('Show in-app notification:', notification);
  };

  /**
   * Update badge count
   */
  const updateBadgeCount = (count?: number) => {
    const badgeCount = count ?? state.unreadCount;
    // Implementation would use react-native-push-notification or similar
    console.log('Update badge count:', badgeCount);
  };

  /**
   * Get badge count
   */
  const getBadgeCount = (): number => {
    return state.unreadCount;
  };

  /**
   * Clear error
   */
  const clearError = () => {
    setState(prev => ({ ...prev, error: null }));
  };

  const contextValue: NotificationContextType = {
    ...state,
    // Basic operations
    addNotification,
    markAsRead,
    markAllAsRead,
    removeNotification,
    clearAllNotifications,
    
    // Rich notification operations
    scheduleNotification,
    cancelScheduledNotification,
    updateNotification,
    groupNotifications,
    getNotificationsByCategory,
    clearNotificationGroup,
    
    // Settings management
    updateSettings,
    updateChannelSettings,
    getSettings,
    
    // Display methods
    showToast,
    showRichNotification,
    showInAppNotification,
    
    // Badge management
    updateBadgeCount,
    getBadgeCount,
    
    // Error handling
    clearError,
  };

  return (
    <NotificationContext.Provider value={contextValue}>
      {children}
    </NotificationContext.Provider>
  );
};

export default NotificationProvider;