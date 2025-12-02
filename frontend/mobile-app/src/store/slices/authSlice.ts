/**
 * Auth Slice - Production-ready Redux Toolkit implementation
 * Replaces mock authentication state management
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { ApiService } from '../../services/ApiService';
import AsyncStorage from '@react-native-async-storage/async-storage';
import DeviceInfo from 'react-native-device-info';
import { Platform } from 'react-native';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  userType: 'personal' | 'business';
  verified: boolean;
  mfaEnabled: boolean;
  kycLevel: 'basic' | 'enhanced' | 'premium';
  createdAt: string;
  updatedAt: string;
  avatar?: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  address?: {
    street: string;
    city: string;
    state: string;
    zipCode: string;
    country: string;
  };
}

export interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isInitializing: boolean;
  error: string | null;
  isOnboardingComplete: boolean;
  lastLoginTime: string | null;
  deviceId: string | null;
  biometricEnabled: boolean;
  pinEnabled: boolean;
  sessionExpiry: string | null;
}

export interface LoginCredentials {
  email: string;
  password: string;
  rememberMe?: boolean;
  deviceId?: string;
  mfaCode?: string;
}

export interface RegisterData {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  userType: 'personal' | 'business';
  phoneNumber?: string;
  acceptTerms: boolean;
  acceptPrivacy: boolean;
}

// Storage keys
const STORAGE_KEYS = {
  ACCESS_TOKEN: '@waqiti_access_token',
  REFRESH_TOKEN: '@waqiti_refresh_token',
  USER: '@waqiti_user',
  ONBOARDING_COMPLETE: '@waqiti_onboarding_complete',
  DEVICE_ID: '@waqiti_device_id',
  BIOMETRIC_ENABLED: '@waqiti_biometric_enabled',
  PIN_ENABLED: '@waqiti_pin_enabled',
  LAST_LOGIN: '@waqiti_last_login',
};

// Initial state
const initialState: AuthState = {
  user: null,
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  isLoading: false,
  isInitializing: true,
  error: null,
  isOnboardingComplete: false,
  lastLoginTime: null,
  deviceId: null,
  biometricEnabled: false,
  pinEnabled: false,
  sessionExpiry: null,
};

// Async thunks
export const initializeAuth = createAsyncThunk(
  'auth/initialize',
  async (_, { rejectWithValue }) => {
    try {
      const [
        accessToken,
        refreshToken,
        userJson,
        onboardingComplete,
        deviceId,
        biometricEnabled,
        pinEnabled,
        lastLoginTime,
      ] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN),
        AsyncStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN),
        AsyncStorage.getItem(STORAGE_KEYS.USER),
        AsyncStorage.getItem(STORAGE_KEYS.ONBOARDING_COMPLETE),
        AsyncStorage.getItem(STORAGE_KEYS.DEVICE_ID),
        AsyncStorage.getItem(STORAGE_KEYS.BIOMETRIC_ENABLED),
        AsyncStorage.getItem(STORAGE_KEYS.PIN_ENABLED),
        AsyncStorage.getItem(STORAGE_KEYS.LAST_LOGIN),
      ]);

      let user: User | null = null;
      if (userJson) {
        try {
          user = JSON.parse(userJson);
        } catch (error) {
          logError('Failed to parse stored user data', {
            feature: 'auth_slice',
            action: 'parse_user_data_failed'
          }, error as Error);
        }
      }

      // Check if tokens are still valid
      if (accessToken && refreshToken && user) {
        try {
          // Validate token with backend
          await ApiService.validateToken();
          
          return {
            user,
            accessToken,
            refreshToken,
            isOnboardingComplete: onboardingComplete === 'true',
            deviceId,
            biometricEnabled: biometricEnabled === 'true',
            pinEnabled: pinEnabled === 'true',
            lastLoginTime,
            isAuthenticated: true,
          };
        } catch (error) {
          // Token invalid, clear storage
          await Promise.all([
            AsyncStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN),
            AsyncStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN),
            AsyncStorage.removeItem(STORAGE_KEYS.USER),
          ]);
          
          return {
            user: null,
            accessToken: null,
            refreshToken: null,
            isOnboardingComplete: onboardingComplete === 'true',
            deviceId,
            biometricEnabled: biometricEnabled === 'true',
            pinEnabled: pinEnabled === 'true',
            lastLoginTime: null,
            isAuthenticated: false,
          };
        }
      }

      return {
        user: null,
        accessToken: null,
        refreshToken: null,
        isOnboardingComplete: onboardingComplete === 'true',
        deviceId,
        biometricEnabled: biometricEnabled === 'true',
        pinEnabled: pinEnabled === 'true',
        lastLoginTime: null,
        isAuthenticated: false,
      };
    } catch (error) {
      logError('Auth initialization failed', {
        feature: 'auth_slice',
        action: 'initialize_failed'
      }, error as Error);
      return rejectWithValue('Failed to initialize authentication');
    }
  }
);

export const loginUser = createAsyncThunk(
  'auth/login',
  async (credentials: LoginCredentials, { rejectWithValue }) => {
    try {
      // Get device information
      const deviceId = credentials.deviceId || await DeviceInfo.getUniqueId();
      const deviceModel = await DeviceInfo.getModel();
      const osVersion = await DeviceInfo.getSystemVersion();
      const appVersion = await DeviceInfo.getVersion();

      const loginPayload = {
        ...credentials,
        deviceId,
        deviceInfo: {
          model: deviceModel,
          osVersion,
          appVersion,
          platform: Platform.OS,
        },
      };

      const response = await ApiService.login(loginPayload);

      if (!response.success) {
        throw new Error(response.message || 'Login failed');
      }

      // Store tokens and user data
      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, response.accessToken),
        AsyncStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, response.refreshToken),
        AsyncStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(response.user)),
        AsyncStorage.setItem(STORAGE_KEYS.DEVICE_ID, deviceId),
        AsyncStorage.setItem(STORAGE_KEYS.LAST_LOGIN, new Date().toISOString()),
      ]);

      // Set API service token
      ApiService.setAuthToken(response.accessToken);

      // Track login event
      await ApiService.trackEvent('user_login', {
        userType: response.user.userType,
        mfaEnabled: response.user.mfaEnabled,
        deviceId,
        timestamp: new Date().toISOString(),
      });

      return {
        user: response.user,
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        deviceId,
        requiresMfa: response.requiresMfa || false,
      };
    } catch (error: any) {
      logError('Login failed', {
        feature: 'auth_slice',
        action: 'login_failed'
      }, error);

      // Track failed login
      await ApiService.trackEvent('user_login_failed', {
        email: credentials.email,
        error: error.message,
        timestamp: new Date().toISOString(),
      });

      return rejectWithValue(error.message || 'Login failed');
    }
  }
);

export const registerUser = createAsyncThunk(
  'auth/register',
  async (userData: RegisterData, { rejectWithValue }) => {
    try {
      const deviceId = await DeviceInfo.getUniqueId();
      const deviceModel = await DeviceInfo.getModel();
      const osVersion = await DeviceInfo.getSystemVersion();
      const appVersion = await DeviceInfo.getVersion();

      const registrationPayload = {
        ...userData,
        deviceId,
        deviceInfo: {
          model: deviceModel,
          osVersion,
          appVersion,
          platform: Platform.OS,
        },
      };

      const response = await ApiService.register(registrationPayload);

      if (!response.success) {
        throw new Error(response.message || 'Registration failed');
      }

      // If auto-login after registration
      if (response.accessToken) {
        await Promise.all([
          AsyncStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, response.accessToken),
          AsyncStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, response.refreshToken),
          AsyncStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(response.user)),
          AsyncStorage.setItem(STORAGE_KEYS.DEVICE_ID, deviceId),
          AsyncStorage.setItem(STORAGE_KEYS.LAST_LOGIN, new Date().toISOString()),
        ]);

        ApiService.setAuthToken(response.accessToken);

        // Track registration
        await ApiService.trackEvent('user_registered', {
          userType: response.user.userType,
          deviceId,
          timestamp: new Date().toISOString(),
        });

        return {
          user: response.user,
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          deviceId,
          requiresVerification: !response.user.verified,
        };
      }

      return {
        user: response.user,
        requiresVerification: true,
        message: response.message,
      };
    } catch (error: any) {
      logError('Registration failed', {
        feature: 'auth_slice',
        action: 'register_failed'
      }, error);

      await ApiService.trackEvent('user_registration_failed', {
        email: userData.email,
        error: error.message,
        timestamp: new Date().toISOString(),
      });

      return rejectWithValue(error.message || 'Registration failed');
    }
  }
);

export const logoutUser = createAsyncThunk(
  'auth/logout',
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as { auth: AuthState };
      const { user } = state.auth;

      // Track logout event
      if (user?.id) {
        await ApiService.trackEvent('user_logout', {
          userId: user.id,
          userType: user.userType,
          timestamp: new Date().toISOString(),
        });
      }

      // Call API logout
      try {
        await ApiService.logout();
      } catch (apiError) {
        logError('API logout failed, continuing with local logout', {
          feature: 'auth_slice',
          action: 'api_logout_failed'
        }, apiError as Error);
      }

      // Clear storage
      await Promise.all([
        AsyncStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN),
        AsyncStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN),
        AsyncStorage.removeItem(STORAGE_KEYS.USER),
        AsyncStorage.removeItem(STORAGE_KEYS.LAST_LOGIN),
      ]);

      // Clear API service token
      ApiService.clearAuthToken();

      return true;
    } catch (error) {
      logError('Logout failed', {
        feature: 'auth_slice',
        action: 'logout_failed'
      }, error as Error);
      return rejectWithValue('Logout failed');
    }
  }
);

export const refreshAccessToken = createAsyncThunk(
  'auth/refreshToken',
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as { auth: AuthState };
      const { refreshToken } = state.auth;

      if (!refreshToken) {
        throw new Error('No refresh token available');
      }

      const response = await ApiService.refreshToken();

      if (!response.accessToken) {
        throw new Error('Invalid refresh response');
      }

      // Store new tokens
      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, response.accessToken),
        AsyncStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, response.refreshToken),
      ]);

      ApiService.setAuthToken(response.accessToken);

      logInfo('Access token refreshed successfully', {
        feature: 'auth_slice',
        action: 'token_refreshed'
      });

      return {
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
      };
    } catch (error: any) {
      logError('Token refresh failed', {
        feature: 'auth_slice',
        action: 'token_refresh_failed'
      }, error);
      return rejectWithValue(error.message || 'Token refresh failed');
    }
  }
);

// Auth slice
const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updateUser: (state, action: PayloadAction<Partial<User>>) => {
      if (state.user) {
        state.user = { ...state.user, ...action.payload };
        AsyncStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(state.user));
      }
    },
    completeOnboarding: (state) => {
      state.isOnboardingComplete = true;
      AsyncStorage.setItem(STORAGE_KEYS.ONBOARDING_COMPLETE, 'true');
    },
    enableBiometric: (state) => {
      state.biometricEnabled = true;
      AsyncStorage.setItem(STORAGE_KEYS.BIOMETRIC_ENABLED, 'true');
    },
    disableBiometric: (state) => {
      state.biometricEnabled = false;
      AsyncStorage.setItem(STORAGE_KEYS.BIOMETRIC_ENABLED, 'false');
    },
    enablePin: (state) => {
      state.pinEnabled = true;
      AsyncStorage.setItem(STORAGE_KEYS.PIN_ENABLED, 'true');
    },
    disablePin: (state) => {
      state.pinEnabled = false;
      AsyncStorage.setItem(STORAGE_KEYS.PIN_ENABLED, 'false');
    },
    setSessionExpiry: (state, action: PayloadAction<string>) => {
      state.sessionExpiry = action.payload;
    },
    clearSession: (state) => {
      state.user = null;
      state.accessToken = null;
      state.refreshToken = null;
      state.isAuthenticated = false;
      state.sessionExpiry = null;
    },
  },
  extraReducers: (builder) => {
    // Initialize auth
    builder
      .addCase(initializeAuth.pending, (state) => {
        state.isInitializing = true;
        state.error = null;
      })
      .addCase(initializeAuth.fulfilled, (state, action) => {
        state.isInitializing = false;
        state.user = action.payload.user;
        state.accessToken = action.payload.accessToken;
        state.refreshToken = action.payload.refreshToken;
        state.isAuthenticated = action.payload.isAuthenticated;
        state.isOnboardingComplete = action.payload.isOnboardingComplete;
        state.deviceId = action.payload.deviceId;
        state.biometricEnabled = action.payload.biometricEnabled;
        state.pinEnabled = action.payload.pinEnabled;
        state.lastLoginTime = action.payload.lastLoginTime;
      })
      .addCase(initializeAuth.rejected, (state, action) => {
        state.isInitializing = false;
        state.error = action.payload as string;
      });

    // Login
    builder
      .addCase(loginUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.isLoading = false;
        state.user = action.payload.user;
        state.accessToken = action.payload.accessToken;
        state.refreshToken = action.payload.refreshToken;
        state.isAuthenticated = true;
        state.deviceId = action.payload.deviceId;
        state.lastLoginTime = new Date().toISOString();
        state.error = null;
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
        state.isAuthenticated = false;
      });

    // Register
    builder
      .addCase(registerUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(registerUser.fulfilled, (state, action) => {
        state.isLoading = false;
        if (action.payload.accessToken) {
          state.user = action.payload.user;
          state.accessToken = action.payload.accessToken;
          state.refreshToken = action.payload.refreshToken;
          state.isAuthenticated = true;
          state.deviceId = action.payload.deviceId;
          state.lastLoginTime = new Date().toISOString();
        }
        state.error = null;
      })
      .addCase(registerUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Logout
    builder
      .addCase(logoutUser.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(logoutUser.fulfilled, (state) => {
        state.isLoading = false;
        state.user = null;
        state.accessToken = null;
        state.refreshToken = null;
        state.isAuthenticated = false;
        state.lastLoginTime = null;
        state.sessionExpiry = null;
        state.error = null;
      })
      .addCase(logoutUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
        // Force logout even if API call failed
        state.user = null;
        state.accessToken = null;
        state.refreshToken = null;
        state.isAuthenticated = false;
      });

    // Refresh token
    builder
      .addCase(refreshAccessToken.fulfilled, (state, action) => {
        state.accessToken = action.payload.accessToken;
        state.refreshToken = action.payload.refreshToken;
      })
      .addCase(refreshAccessToken.rejected, (state) => {
        // Token refresh failed, force logout
        state.user = null;
        state.accessToken = null;
        state.refreshToken = null;
        state.isAuthenticated = false;
        state.sessionExpiry = null;
      });
  },
});

export const {
  clearError,
  updateUser,
  completeOnboarding,
  enableBiometric,
  disableBiometric,
  enablePin,
  disablePin,
  setSessionExpiry,
  clearSession,
} = authSlice.actions;

export default authSlice.reducer;