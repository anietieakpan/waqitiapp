/**
 * Settings Slice - User preferences and app configuration
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface NotificationSettings {
  pushEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  transactionAlerts: boolean;
  securityAlerts: boolean;
  marketingMessages: boolean;
  promotionalOffers: boolean;
  paymentRequests: boolean;
  friendActivity: boolean;
  weeklyDigest: boolean;
  quietHours: {
    enabled: boolean;
    start: string; // 24h format: "22:00"
    end: string;   // 24h format: "08:00"
  };
}

export interface SecuritySettings {
  twoFactorEnabled: boolean;
  biometricEnabled: boolean;
  pinEnabled: boolean;
  deviceLockRequired: boolean;
  sessionTimeout: number; // minutes
  transactionPinRequired: boolean;
  highValueTransactionLimit: number;
  allowInternationalTransactions: boolean;
  trustedDevices: string[];
  loginNotifications: boolean;
}

export interface PrivacySettings {
  profileVisibility: 'public' | 'friends' | 'private';
  shareTransactionHistory: boolean;
  allowDataSharing: boolean;
  allowAnalytics: boolean;
  allowLocationTracking: boolean;
  allowMarketingCommunications: boolean;
  dataRetentionPeriod: number; // days
}

export interface AppSettings {
  theme: 'light' | 'dark' | 'system';
  language: string;
  currency: string;
  timezone: string;
  dateFormat: string;
  numberFormat: string;
  autoLockTimeout: number; // seconds
  hapticFeedback: boolean;
  soundEffects: boolean;
  animations: boolean;
  reducedMotion: boolean;
  highContrast: boolean;
  fontSize: 'small' | 'medium' | 'large' | 'extra-large';
}

export interface SettingsState {
  notifications: NotificationSettings;
  security: SecuritySettings;
  privacy: PrivacySettings;
  app: AppSettings;
  isLoading: boolean;
  error: string | null;
  hasUnsavedChanges: boolean;
  lastSynced: string | null;
}

// Storage keys
const STORAGE_KEYS = {
  NOTIFICATIONS: '@waqiti_notification_settings',
  SECURITY: '@waqiti_security_settings',
  PRIVACY: '@waqiti_privacy_settings',
  APP: '@waqiti_app_settings',
  LAST_SYNCED: '@waqiti_settings_last_synced',
};

// Initial state
const initialState: SettingsState = {
  notifications: {
    pushEnabled: true,
    emailEnabled: true,
    smsEnabled: false,
    transactionAlerts: true,
    securityAlerts: true,
    marketingMessages: false,
    promotionalOffers: false,
    paymentRequests: true,
    friendActivity: true,
    weeklyDigest: true,
    quietHours: {
      enabled: false,
      start: '22:00',
      end: '08:00',
    },
  },
  security: {
    twoFactorEnabled: false,
    biometricEnabled: false,
    pinEnabled: false,
    deviceLockRequired: true,
    sessionTimeout: 30,
    transactionPinRequired: true,
    highValueTransactionLimit: 1000,
    allowInternationalTransactions: false,
    trustedDevices: [],
    loginNotifications: true,
  },
  privacy: {
    profileVisibility: 'friends',
    shareTransactionHistory: false,
    allowDataSharing: true,
    allowAnalytics: true,
    allowLocationTracking: false,
    allowMarketingCommunications: false,
    dataRetentionPeriod: 365,
  },
  app: {
    theme: 'system',
    language: 'en',
    currency: 'USD',
    timezone: 'America/New_York',
    dateFormat: 'MM/DD/YYYY',
    numberFormat: 'en-US',
    autoLockTimeout: 300, // 5 minutes
    hapticFeedback: true,
    soundEffects: true,
    animations: true,
    reducedMotion: false,
    highContrast: false,
    fontSize: 'medium',
  },
  isLoading: false,
  error: null,
  hasUnsavedChanges: false,
  lastSynced: null,
};

// Async thunks
export const loadSettings = createAsyncThunk(
  'settings/load',
  async (_, { rejectWithValue }) => {
    try {
      const [
        notificationsJson,
        securityJson,
        privacyJson,
        appJson,
        lastSynced,
      ] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEYS.NOTIFICATIONS),
        AsyncStorage.getItem(STORAGE_KEYS.SECURITY),
        AsyncStorage.getItem(STORAGE_KEYS.PRIVACY),
        AsyncStorage.getItem(STORAGE_KEYS.APP),
        AsyncStorage.getItem(STORAGE_KEYS.LAST_SYNCED),
      ]);

      const settings: Partial<SettingsState> = {
        lastSynced,
      };

      if (notificationsJson) {
        settings.notifications = { ...initialState.notifications, ...JSON.parse(notificationsJson) };
      }
      if (securityJson) {
        settings.security = { ...initialState.security, ...JSON.parse(securityJson) };
      }
      if (privacyJson) {
        settings.privacy = { ...initialState.privacy, ...JSON.parse(privacyJson) };
      }
      if (appJson) {
        settings.app = { ...initialState.app, ...JSON.parse(appJson) };
      }

      return settings;
    } catch (error: any) {
      logError('Failed to load settings', {
        feature: 'settings_slice',
        action: 'load_settings_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to load settings');
    }
  }
);

export const saveSettings = createAsyncThunk(
  'settings/save',
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as { settings: SettingsState };
      const { notifications, security, privacy, app } = state.settings;

      // Save to local storage
      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.NOTIFICATIONS, JSON.stringify(notifications)),
        AsyncStorage.setItem(STORAGE_KEYS.SECURITY, JSON.stringify(security)),
        AsyncStorage.setItem(STORAGE_KEYS.PRIVACY, JSON.stringify(privacy)),
        AsyncStorage.setItem(STORAGE_KEYS.APP, JSON.stringify(app)),
        AsyncStorage.setItem(STORAGE_KEYS.LAST_SYNCED, new Date().toISOString()),
      ]);

      // Sync with server
      await ApiService.post('/user/settings', {
        notifications,
        security,
        privacy,
        app,
      });

      // Track settings change
      await ApiService.trackEvent('settings_updated', {
        timestamp: new Date().toISOString(),
      });

      return new Date().toISOString();
    } catch (error: any) {
      logError('Failed to save settings', {
        feature: 'settings_slice',
        action: 'save_settings_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to save settings');
    }
  }
);

export const syncSettings = createAsyncThunk(
  'settings/sync',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/user/settings');
      const serverSettings = response.data;

      // Save synced settings to local storage
      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.NOTIFICATIONS, JSON.stringify(serverSettings.notifications)),
        AsyncStorage.setItem(STORAGE_KEYS.SECURITY, JSON.stringify(serverSettings.security)),
        AsyncStorage.setItem(STORAGE_KEYS.PRIVACY, JSON.stringify(serverSettings.privacy)),
        AsyncStorage.setItem(STORAGE_KEYS.APP, JSON.stringify(serverSettings.app)),
        AsyncStorage.setItem(STORAGE_KEYS.LAST_SYNCED, new Date().toISOString()),
      ]);

      return {
        ...serverSettings,
        lastSynced: new Date().toISOString(),
      };
    } catch (error: any) {
      logError('Failed to sync settings', {
        feature: 'settings_slice',
        action: 'sync_settings_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to sync settings');
    }
  }
);

export const resetSettings = createAsyncThunk(
  'settings/reset',
  async (category: 'all' | 'notifications' | 'security' | 'privacy' | 'app', { rejectWithValue }) => {
    try {
      const resetData: Partial<SettingsState> = {};

      switch (category) {
        case 'all':
          resetData.notifications = initialState.notifications;
          resetData.security = initialState.security;
          resetData.privacy = initialState.privacy;
          resetData.app = initialState.app;
          break;
        case 'notifications':
          resetData.notifications = initialState.notifications;
          break;
        case 'security':
          resetData.security = initialState.security;
          break;
        case 'privacy':
          resetData.privacy = initialState.privacy;
          break;
        case 'app':
          resetData.app = initialState.app;
          break;
      }

      // Track reset event
      await ApiService.trackEvent('settings_reset', {
        category,
        timestamp: new Date().toISOString(),
      });

      return resetData;
    } catch (error: any) {
      logError('Failed to reset settings', {
        feature: 'settings_slice',
        action: 'reset_settings_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to reset settings');
    }
  }
);

// Settings slice
const settingsSlice = createSlice({
  name: 'settings',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updateNotificationSettings: (state, action: PayloadAction<Partial<NotificationSettings>>) => {
      state.notifications = { ...state.notifications, ...action.payload };
      state.hasUnsavedChanges = true;
    },
    updateSecuritySettings: (state, action: PayloadAction<Partial<SecuritySettings>>) => {
      state.security = { ...state.security, ...action.payload };
      state.hasUnsavedChanges = true;
    },
    updatePrivacySettings: (state, action: PayloadAction<Partial<PrivacySettings>>) => {
      state.privacy = { ...state.privacy, ...action.payload };
      state.hasUnsavedChanges = true;
    },
    updateAppSettings: (state, action: PayloadAction<Partial<AppSettings>>) => {
      state.app = { ...state.app, ...action.payload };
      state.hasUnsavedChanges = true;
    },
    addTrustedDevice: (state, action: PayloadAction<string>) => {
      if (!state.security.trustedDevices.includes(action.payload)) {
        state.security.trustedDevices.push(action.payload);
        state.hasUnsavedChanges = true;
      }
    },
    removeTrustedDevice: (state, action: PayloadAction<string>) => {
      state.security.trustedDevices = state.security.trustedDevices.filter(
        device => device !== action.payload
      );
      state.hasUnsavedChanges = true;
    },
    markChangesSaved: (state) => {
      state.hasUnsavedChanges = false;
    },
    setTheme: (state, action: PayloadAction<'light' | 'dark' | 'system'>) => {
      state.app.theme = action.payload;
      state.hasUnsavedChanges = true;
    },
    setLanguage: (state, action: PayloadAction<string>) => {
      state.app.language = action.payload;
      state.hasUnsavedChanges = true;
    },
    setCurrency: (state, action: PayloadAction<string>) => {
      state.app.currency = action.payload;
      state.hasUnsavedChanges = true;
    },
    toggleBiometric: (state, action: PayloadAction<boolean>) => {
      state.security.biometricEnabled = action.payload;
      state.hasUnsavedChanges = true;
    },
    togglePin: (state, action: PayloadAction<boolean>) => {
      state.security.pinEnabled = action.payload;
      state.hasUnsavedChanges = true;
    },
    toggleTwoFactor: (state, action: PayloadAction<boolean>) => {
      state.security.twoFactorEnabled = action.payload;
      state.hasUnsavedChanges = true;
    },
    updateQuietHours: (state, action: PayloadAction<NotificationSettings['quietHours']>) => {
      state.notifications.quietHours = action.payload;
      state.hasUnsavedChanges = true;
    },
  },
  extraReducers: (builder) => {
    // Load settings
    builder
      .addCase(loadSettings.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(loadSettings.fulfilled, (state, action) => {
        state.isLoading = false;
        if (action.payload.notifications) {
          state.notifications = action.payload.notifications;
        }
        if (action.payload.security) {
          state.security = action.payload.security;
        }
        if (action.payload.privacy) {
          state.privacy = action.payload.privacy;
        }
        if (action.payload.app) {
          state.app = action.payload.app;
        }
        state.lastSynced = action.payload.lastSynced;
        state.hasUnsavedChanges = false;
      })
      .addCase(loadSettings.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Save settings
    builder
      .addCase(saveSettings.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(saveSettings.fulfilled, (state, action) => {
        state.isLoading = false;
        state.hasUnsavedChanges = false;
        state.lastSynced = action.payload;
      })
      .addCase(saveSettings.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Sync settings
    builder
      .addCase(syncSettings.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(syncSettings.fulfilled, (state, action) => {
        state.isLoading = false;
        state.notifications = action.payload.notifications;
        state.security = action.payload.security;
        state.privacy = action.payload.privacy;
        state.app = action.payload.app;
        state.lastSynced = action.payload.lastSynced;
        state.hasUnsavedChanges = false;
      })
      .addCase(syncSettings.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Reset settings
    builder
      .addCase(resetSettings.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(resetSettings.fulfilled, (state, action) => {
        state.isLoading = false;
        if (action.payload.notifications) {
          state.notifications = action.payload.notifications;
        }
        if (action.payload.security) {
          state.security = action.payload.security;
        }
        if (action.payload.privacy) {
          state.privacy = action.payload.privacy;
        }
        if (action.payload.app) {
          state.app = action.payload.app;
        }
        state.hasUnsavedChanges = true;
      })
      .addCase(resetSettings.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });
  },
});

export const {
  clearError,
  updateNotificationSettings,
  updateSecuritySettings,
  updatePrivacySettings,
  updateAppSettings,
  addTrustedDevice,
  removeTrustedDevice,
  markChangesSaved,
  setTheme,
  setLanguage,
  setCurrency,
  toggleBiometric,
  togglePin,
  toggleTwoFactor,
  updateQuietHours,
} = settingsSlice.actions;

export default settingsSlice.reducer;