/**
 * NotificationSlice - Redux slice for managing notification state in mobile app
 * Handles notifications, device tokens, preferences, and push notification state
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { ApiService } from '../../services/ApiService';
import { Platform } from 'react-native';

// Types
interface Notification {
  id: string;
  title: string;
  body: string;
  type: 'payment' | 'request' | 'message' | 'security' | 'promotion' | 'info' | 'warning' | 'error';
  category: string;
  data?: Record<string, any>;
  timestamp: string;
  read: boolean;
  actionUrl?: string;
  imageUrl?: string;
  priority: 'high' | 'normal' | 'low';
  expiresAt?: string;
}

interface DeviceToken {
  token: string;
  platform: 'ios' | 'android';
  deviceId: string;
  registeredAt: string;
  active: boolean;
}

interface NotificationPermissions {
  push: boolean;
  sound: boolean;
  badge: boolean;
  alert: boolean;
}

interface NotificationPreferences {
  pushEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  categories: {
    payments: boolean;
    security: boolean;
    marketing: boolean;
    social: boolean;
    system: boolean;
  };
  quietHours: {
    enabled: boolean;
    startTime: string;
    endTime: string;
  };
  sounds: {
    enabled: boolean;
    payments: boolean;
    security: boolean;
    messages: boolean;
  };
  vibration: {
    enabled: boolean;
    payments: boolean;
    security: boolean;
    messages: boolean;
  };
}

interface NotificationStats {
  totalNotifications: number;
  unreadCount: number;
  todayCount: number;
  weekCount: number;
  lastFetchTime?: string;
}

interface NotificationState {
  // Notifications
  notifications: Notification[];
  stats: NotificationStats;
  
  // Device and permissions
  deviceToken: DeviceToken | null;
  permissions: NotificationPermissions;
  
  // User preferences
  preferences: NotificationPreferences | null;
  
  // UI state
  loading: boolean;
  error: string | null;
  refreshing: boolean;
  
  // Pagination
  hasMore: boolean;
  currentPage: number;
  totalPages: number;
  
  // Last actions
  lastNotificationReceived?: Notification;
  lastNotificationOpened?: string;
}

const initialState: NotificationState = {
  notifications: [],
  stats: {
    totalNotifications: 0,
    unreadCount: 0,
    todayCount: 0,
    weekCount: 0,
  },
  deviceToken: null,
  permissions: {
    push: false,
    sound: false,
    badge: false,
    alert: false,
  },
  preferences: null,
  loading: false,
  error: null,
  refreshing: false,
  hasMore: true,
  currentPage: 0,
  totalPages: 0,
};

// ==================== ASYNC THUNKS ====================

/**
 * Register device token for push notifications
 */
export const registerPushToken = createAsyncThunk(
  'notification/registerPushToken',
  async (tokenData: {
    token: string;
    platform: string;
    deviceId: string;
    deviceModel?: string;
    osVersion?: string;
    appVersion?: string;
  }, { rejectWithValue }) => {
    try {
      await ApiService.registerDeviceToken({
        token: tokenData.token,
        platform: tokenData.platform as 'ios' | 'android',
        deviceId: tokenData.deviceId,
        deviceModel: tokenData.deviceModel,
        osVersion: tokenData.osVersion,
        appVersion: tokenData.appVersion,
      });

      return {
        token: tokenData.token,
        platform: tokenData.platform as 'ios' | 'android',
        deviceId: tokenData.deviceId,
        registeredAt: new Date().toISOString(),
        active: true,
      };
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to register push token');
    }
  }
);

/**
 * Fetch notifications with pagination
 */
export const fetchNotifications = createAsyncThunk(
  'notification/fetchNotifications',
  async (params: {
    page?: number;
    size?: number;
    unreadOnly?: boolean;
    refresh?: boolean;
  } = {}, { rejectWithValue, getState }) => {
    try {
      const { page = 0, size = 20, unreadOnly = false } = params;
      
      const response = unreadOnly 
        ? await ApiService.getUnreadNotifications({ page, size })
        : await ApiService.getNotifications({ page, size });

      const notifications = response.content || response.notifications || [];
      
      return {
        notifications: notifications.map((notification: any) => ({
          id: notification.id,
          title: notification.title,
          body: notification.message || notification.body,
          type: notification.type || 'info',
          category: notification.category || 'general',
          data: notification.data || {},
          timestamp: notification.createdAt || notification.timestamp,
          read: notification.read || false,
          actionUrl: notification.actionUrl,
          imageUrl: notification.imageUrl,
          priority: notification.priority || 'normal',
          expiresAt: notification.expiresAt,
        })),
        totalElements: response.totalElements || response.totalNotifications || 0,
        totalPages: response.totalPages || 0,
        currentPage: page,
        unreadCount: response.unreadCount || 0,
        hasMore: (page + 1) < (response.totalPages || 0),
        isRefresh: params.refresh || false,
      };
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to fetch notifications');
    }
  }
);

/**
 * Mark notification as read
 */
export const markNotificationAsRead = createAsyncThunk(
  'notification/markAsRead',
  async (notificationId: string, { rejectWithValue }) => {
    try {
      await ApiService.markNotificationAsRead(notificationId);
      return notificationId;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to mark notification as read');
    }
  }
);

/**
 * Mark all notifications as read
 */
export const markAllNotificationsAsRead = createAsyncThunk(
  'notification/markAllAsRead',
  async (_, { rejectWithValue }) => {
    try {
      await ApiService.markAllNotificationsAsRead();
      return true;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to mark all notifications as read');
    }
  }
);

/**
 * Delete notification
 */
export const deleteNotification = createAsyncThunk(
  'notification/delete',
  async (notificationId: string, { rejectWithValue }) => {
    try {
      await ApiService.deleteNotification(notificationId);
      return notificationId;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to delete notification');
    }
  }
);

/**
 * Fetch notification preferences
 */
export const fetchNotificationPreferences = createAsyncThunk(
  'notification/fetchPreferences',
  async (_, { rejectWithValue }) => {
    try {
      const preferences = await ApiService.getNotificationPreferences();
      return preferences;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to fetch notification preferences');
    }
  }
);

/**
 * Update notification preferences
 */
export const updateNotificationPreferences = createAsyncThunk(
  'notification/updatePreferences',
  async (preferences: Partial<NotificationPreferences>, { rejectWithValue }) => {
    try {
      const updatedPreferences = await ApiService.updateNotificationPreferences(preferences);
      return updatedPreferences;
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to update notification preferences');
    }
  }
);

/**
 * Refresh notification stats
 */
export const refreshNotificationStats = createAsyncThunk(
  'notification/refreshStats',
  async (_, { rejectWithValue }) => {
    try {
      const [unreadResponse, allResponse] = await Promise.all([
        ApiService.getUnreadNotificationCount(),
        ApiService.getNotifications({ page: 0, size: 1 }),
      ]);

      const now = new Date();
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      const weekAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);

      // For simplicity, we'll estimate today and week counts
      // In a real app, you'd have specific API endpoints for this
      const todayCount = 0; // Would be calculated server-side
      const weekCount = Math.min(unreadResponse.count, 10); // Estimate

      return {
        totalNotifications: allResponse.totalElements || 0,
        unreadCount: unreadResponse.count,
        todayCount,
        weekCount,
        lastFetchTime: new Date().toISOString(),
      };
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to refresh notification stats');
    }
  }
);

// ==================== NOTIFICATION SLICE ====================

const notificationSlice = createSlice({
  name: 'notification',
  initialState,
  reducers: {
    // Add new notification (from push notification)
    addNotification: (state, action: PayloadAction<Notification>) => {
      const notification = action.payload;
      
      // Avoid duplicates
      const existingIndex = state.notifications.findIndex(n => n.id === notification.id);
      if (existingIndex === -1) {
        state.notifications.unshift(notification);
        
        // Update stats
        state.stats.totalNotifications += 1;
        if (!notification.read) {
          state.stats.unreadCount += 1;
        }
        
        // Check if it's from today
        const today = new Date().toDateString();
        const notificationDate = new Date(notification.timestamp).toDateString();
        if (notificationDate === today) {
          state.stats.todayCount += 1;
        }
        
        // Store last received notification
        state.lastNotificationReceived = notification;
      }
    },

    // Update notification permissions
    updateNotificationPermissions: (state, action: PayloadAction<Partial<NotificationPermissions>>) => {
      state.permissions = { ...state.permissions, ...action.payload };
    },

    // Clear error
    clearError: (state) => {
      state.error = null;
    },

    // Set loading state
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },

    // Set refreshing state
    setRefreshing: (state, action: PayloadAction<boolean>) => {
      state.refreshing = action.payload;
    },

    // Mark notification as opened
    markNotificationAsOpened: (state, action: PayloadAction<string>) => {
      const notificationId = action.payload;
      const notification = state.notifications.find(n => n.id === notificationId);
      
      if (notification && !notification.read) {
        notification.read = true;
        state.stats.unreadCount = Math.max(0, state.stats.unreadCount - 1);
      }
      
      state.lastNotificationOpened = notificationId;
    },

    // Remove expired notifications
    removeExpiredNotifications: (state) => {
      const now = new Date().toISOString();
      const activeNotifications = state.notifications.filter(notification => 
        !notification.expiresAt || notification.expiresAt > now
      );
      
      const removedCount = state.notifications.length - activeNotifications.length;
      state.notifications = activeNotifications;
      state.stats.totalNotifications -= removedCount;
    },

    // Clear all notifications
    clearAllNotifications: (state) => {
      state.notifications = [];
      state.stats = {
        totalNotifications: 0,
        unreadCount: 0,
        todayCount: 0,
        weekCount: 0,
        lastFetchTime: state.stats.lastFetchTime,
      };
    },

    // Update notification in list
    updateNotification: (state, action: PayloadAction<{ id: string; updates: Partial<Notification> }>) => {
      const { id, updates } = action.payload;
      const notification = state.notifications.find(n => n.id === id);
      
      if (notification) {
        Object.assign(notification, updates);
        
        // Update unread count if read status changed
        if (updates.read !== undefined) {
          if (updates.read && !notification.read) {
            state.stats.unreadCount = Math.max(0, state.stats.unreadCount - 1);
          } else if (!updates.read && notification.read) {
            state.stats.unreadCount += 1;
          }
        }
      }
    },

    // Reset notification state
    resetNotificationState: (state) => {
      Object.assign(state, initialState);
    },
  },

  extraReducers: (builder) => {
    // Register push token
    builder
      .addCase(registerPushToken.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(registerPushToken.fulfilled, (state, action) => {
        state.loading = false;
        state.deviceToken = action.payload;
        state.permissions.push = true;
      })
      .addCase(registerPushToken.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });

    // Fetch notifications
    builder
      .addCase(fetchNotifications.pending, (state, action) => {
        if (action.meta.arg.refresh) {
          state.refreshing = true;
        } else {
          state.loading = true;
        }
        state.error = null;
      })
      .addCase(fetchNotifications.fulfilled, (state, action) => {
        state.loading = false;
        state.refreshing = false;
        
        const { notifications, totalElements, totalPages, currentPage, unreadCount, hasMore, isRefresh } = action.payload;
        
        if (isRefresh || currentPage === 0) {
          // Replace notifications for refresh or first page
          state.notifications = notifications;
        } else {
          // Append notifications for pagination
          state.notifications.push(...notifications);
        }
        
        state.stats.totalNotifications = totalElements;
        state.stats.unreadCount = unreadCount;
        state.stats.lastFetchTime = new Date().toISOString();
        state.hasMore = hasMore;
        state.currentPage = currentPage;
        state.totalPages = totalPages;
      })
      .addCase(fetchNotifications.rejected, (state, action) => {
        state.loading = false;
        state.refreshing = false;
        state.error = action.payload as string;
      });

    // Mark as read
    builder
      .addCase(markNotificationAsRead.fulfilled, (state, action) => {
        const notificationId = action.payload;
        const notification = state.notifications.find(n => n.id === notificationId);
        
        if (notification && !notification.read) {
          notification.read = true;
          state.stats.unreadCount = Math.max(0, state.stats.unreadCount - 1);
        }
      });

    // Mark all as read
    builder
      .addCase(markAllNotificationsAsRead.fulfilled, (state) => {
        state.notifications.forEach(notification => {
          notification.read = true;
        });
        state.stats.unreadCount = 0;
      });

    // Delete notification
    builder
      .addCase(deleteNotification.fulfilled, (state, action) => {
        const notificationId = action.payload;
        const index = state.notifications.findIndex(n => n.id === notificationId);
        
        if (index !== -1) {
          const notification = state.notifications[index];
          if (!notification.read) {
            state.stats.unreadCount = Math.max(0, state.stats.unreadCount - 1);
          }
          state.notifications.splice(index, 1);
          state.stats.totalNotifications -= 1;
        }
      });

    // Fetch preferences
    builder
      .addCase(fetchNotificationPreferences.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchNotificationPreferences.fulfilled, (state, action) => {
        state.loading = false;
        state.preferences = action.payload;
      })
      .addCase(fetchNotificationPreferences.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });

    // Update preferences
    builder
      .addCase(updateNotificationPreferences.pending, (state) => {
        state.loading = true;
      })
      .addCase(updateNotificationPreferences.fulfilled, (state, action) => {
        state.loading = false;
        state.preferences = action.payload;
      })
      .addCase(updateNotificationPreferences.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });

    // Refresh stats
    builder
      .addCase(refreshNotificationStats.fulfilled, (state, action) => {
        state.stats = { ...state.stats, ...action.payload };
      });
  },
});

// Export actions
export const {
  addNotification,
  updateNotificationPermissions,
  clearError,
  setLoading,
  setRefreshing,
  markNotificationAsOpened,
  removeExpiredNotifications,
  clearAllNotifications,
  updateNotification,
  resetNotificationState,
} = notificationSlice.actions;

// Export selectors
export const selectNotifications = (state: { notification: NotificationState }) => state.notification.notifications;
export const selectUnreadNotifications = (state: { notification: NotificationState }) => 
  state.notification.notifications.filter(n => !n.read);
export const selectNotificationStats = (state: { notification: NotificationState }) => state.notification.stats;
export const selectNotificationPreferences = (state: { notification: NotificationState }) => state.notification.preferences;
export const selectDeviceToken = (state: { notification: NotificationState }) => state.notification.deviceToken;
export const selectNotificationPermissions = (state: { notification: NotificationState }) => state.notification.permissions;
export const selectNotificationLoading = (state: { notification: NotificationState }) => state.notification.loading;
export const selectNotificationError = (state: { notification: NotificationState }) => state.notification.error;

export default notificationSlice.reducer;