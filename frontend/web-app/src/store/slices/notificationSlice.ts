import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { notificationService } from '../../services/notificationService';
import { Notification, NotificationPreferences, NotificationType } from '../../types/notification';

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  preferences: NotificationPreferences | null;
  loading: boolean;
  error: string | null;
  snackbar: {
    open: boolean;
    message: string;
    type: 'success' | 'error' | 'warning' | 'info';
  };
}

const initialState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  preferences: null,
  loading: false,
  error: null,
  snackbar: {
    open: false,
    message: '',
    type: 'info',
  },
};

// Async thunks
export const fetchNotifications = createAsyncThunk(
  'notification/fetchNotifications',
  async (params: { page?: number; limit?: number; unreadOnly?: boolean }, { rejectWithValue }) => {
    try {
      const response = await notificationService.getNotifications(params);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch notifications');
    }
  }
);

export const fetchUnreadCount = createAsyncThunk(
  'notification/fetchUnreadCount',
  async (_, { rejectWithValue }) => {
    try {
      const response = await notificationService.getUnreadCount();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch unread count');
    }
  }
);

export const markAsRead = createAsyncThunk(
  'notification/markAsRead',
  async (notificationId: string, { dispatch, rejectWithValue }) => {
    try {
      await notificationService.markAsRead(notificationId);
      // Refresh unread count
      dispatch(fetchUnreadCount());
      return notificationId;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to mark as read');
    }
  }
);

export const markAllAsRead = createAsyncThunk(
  'notification/markAllAsRead',
  async (_, { dispatch, rejectWithValue }) => {
    try {
      await notificationService.markAllAsRead();
      // Refresh notifications and count
      dispatch(fetchNotifications({ page: 0, limit: 10 }));
      dispatch(fetchUnreadCount());
      return true;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to mark all as read');
    }
  }
);

export const deleteNotification = createAsyncThunk(
  'notification/delete',
  async (notificationId: string, { dispatch, rejectWithValue }) => {
    try {
      await notificationService.deleteNotification(notificationId);
      dispatch(
        showNotification({
          message: 'Notification deleted',
          type: 'success',
        })
      );
      return notificationId;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to delete notification');
    }
  }
);

export const fetchNotificationPreferences = createAsyncThunk(
  'notification/fetchPreferences',
  async (_, { rejectWithValue }) => {
    try {
      const response = await notificationService.getPreferences();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch preferences');
    }
  }
);

export const updateNotificationPreferences = createAsyncThunk(
  'notification/updatePreferences',
  async (preferences: Partial<NotificationPreferences>, { dispatch, rejectWithValue }) => {
    try {
      const response = await notificationService.updatePreferences(preferences);
      dispatch(
        showNotification({
          message: 'Preferences updated successfully',
          type: 'success',
        })
      );
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to update preferences',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to update preferences');
    }
  }
);

// Notification slice
const notificationSlice = createSlice({
  name: 'notification',
  initialState,
  reducers: {
    showNotification: (
      state,
      action: PayloadAction<{
        message: string;
        type: 'success' | 'error' | 'warning' | 'info';
      }>
    ) => {
      state.snackbar = {
        open: true,
        message: action.payload.message,
        type: action.payload.type,
      };
    },
    hideNotification: (state) => {
      state.snackbar.open = false;
    },
    addNotification: (state, action: PayloadAction<Notification>) => {
      state.notifications.unshift(action.payload);
      if (!action.payload.read) {
        state.unreadCount += 1;
      }
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    // Fetch notifications
    builder
      .addCase(fetchNotifications.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchNotifications.fulfilled, (state, action) => {
        state.loading = false;
        state.notifications = action.payload.content || action.payload;
      })
      .addCase(fetchNotifications.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });

    // Fetch unread count
    builder
      .addCase(fetchUnreadCount.fulfilled, (state, action) => {
        state.unreadCount = action.payload.count || action.payload;
      });

    // Mark as read
    builder
      .addCase(markAsRead.fulfilled, (state, action) => {
        const notification = state.notifications.find((n) => n.id === action.payload);
        if (notification && !notification.read) {
          notification.read = true;
          state.unreadCount = Math.max(0, state.unreadCount - 1);
        }
      });

    // Mark all as read
    builder
      .addCase(markAllAsRead.fulfilled, (state) => {
        state.notifications.forEach((n) => {
          n.read = true;
        });
        state.unreadCount = 0;
      });

    // Delete notification
    builder
      .addCase(deleteNotification.fulfilled, (state, action) => {
        const index = state.notifications.findIndex((n) => n.id === action.payload);
        if (index !== -1) {
          const notification = state.notifications[index];
          if (!notification.read) {
            state.unreadCount = Math.max(0, state.unreadCount - 1);
          }
          state.notifications.splice(index, 1);
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
      .addCase(fetchNotificationPreferences.rejected, (state) => {
        state.loading = false;
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
  },
});

export const { showNotification, hideNotification, addNotification, clearError } =
  notificationSlice.actions;
export default notificationSlice.reducer;