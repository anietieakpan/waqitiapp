/**
 * AuthContext - Mobile authentication context
 * Provides authentication state and methods for the mobile app
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import { ApiService } from '../services/ApiService';
import DeviceInfo from 'react-native-device-info';
import { logError, logInfo, logWarn, logDebug } from '../utils/Logger';

// Auth state interface
interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  loading: boolean;
  error: string | null;
  isOnboardingComplete: boolean;
}

// User interface
interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  userType: 'personal' | 'business';
  verified: boolean;
  mfaEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

// Login request interface
interface LoginRequest {
  email: string;
  password: string;
  deviceId?: string;
  rememberMe?: boolean;
}

// Login response interface
interface LoginResponse {
  success: boolean;
  accessToken: string;
  refreshToken: string;
  user: User;
  requiresMfa?: boolean;
  message?: string;
}

// Register request interface
interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  userType: 'personal' | 'business';
  deviceId?: string;
}

// AuthContext interface
interface AuthContextType extends AuthState {
  login: (credentials: LoginRequest) => Promise<LoginResponse>;
  register: (userData: RegisterRequest) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  refreshAccessToken: () => Promise<void>;
  updateUser: (user: User) => void;
  clearError: () => void;
  completeOnboarding: () => void;
  verifyEmail: (token: string) => Promise<void>;
  requestPasswordReset: (email: string) => Promise<void>;
  resetPassword: (token: string, newPassword: string) => Promise<void>;
}

// Default state
const defaultState: AuthState = {
  isAuthenticated: false,
  user: null,
  accessToken: null,
  refreshToken: null,
  loading: true,
  error: null,
  isOnboardingComplete: false,
};

// Create context
const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Custom hook
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

// AuthProvider props
interface AuthProviderProps {
  children: ReactNode;
}

// Storage keys
const STORAGE_KEYS = {
  ACCESS_TOKEN: '@waqiti_access_token',
  REFRESH_TOKEN: '@waqiti_refresh_token',
  USER: '@waqiti_user',
  ONBOARDING_COMPLETE: '@waqiti_onboarding_complete',
};

// AuthProvider component
export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [state, setState] = useState<AuthState>(defaultState);

  // Initialize auth state from AsyncStorage
  useEffect(() => {
    initializeAuth();
  }, []);

  /**
   * Initialize authentication state from storage
   */
  const initializeAuth = async () => {
    try {
      const [
        accessToken,
        refreshToken,
        userJson,
        onboardingComplete
      ] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN),
        AsyncStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN),
        AsyncStorage.getItem(STORAGE_KEYS.USER),
        AsyncStorage.getItem(STORAGE_KEYS.ONBOARDING_COMPLETE),
      ]);

      if (accessToken && refreshToken && userJson) {
        const user = JSON.parse(userJson);
        setState({
          isAuthenticated: true,
          user,
          accessToken,
          refreshToken,
          loading: false,
          error: null,
          isOnboardingComplete: onboardingComplete === 'true',
        });
      } else {
        setState(prev => ({
          ...prev,
          loading: false,
          isOnboardingComplete: onboardingComplete === 'true',
        }));
      }
    } catch (error) {
      logError('Failed to initialize auth state', {
        feature: 'authentication',
        action: 'init_auth_state_failed'
      }, error as Error);
      setState(prev => ({ ...prev, loading: false }));
    }
  };

  /**
   * Login user
   */
  const login = async (credentials: LoginRequest): Promise<LoginResponse> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      // Get device information for enhanced security
      const deviceId = credentials.deviceId || await DeviceInfo.getUniqueId();
      const deviceModel = await DeviceInfo.getModel();
      const osVersion = await DeviceInfo.getSystemVersion();
      const appVersion = await DeviceInfo.getVersion();

      // Prepare login payload with device information
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

      // Make API call using ApiService
      const loginResponse: LoginResponse = await ApiService.login(loginPayload);

      if (!loginResponse.success) {
        throw new Error(loginResponse.message || 'Login failed');
      }

      // Store tokens and user data with proper key names
      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, loginResponse.accessToken),
        AsyncStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, loginResponse.refreshToken),
        AsyncStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(loginResponse.user)),
        // Also store in ApiService format for interceptors
        ApiService.setAuthToken(loginResponse.accessToken),
        AsyncStorage.setItem('refreshToken', loginResponse.refreshToken),
        AsyncStorage.setItem('deviceId', deviceId),
      ]);

      setState({
        isAuthenticated: true,
        user: loginResponse.user,
        accessToken: loginResponse.accessToken,
        refreshToken: loginResponse.refreshToken,
        loading: false,
        error: null,
        isOnboardingComplete: state.isOnboardingComplete,
      });

      // Track successful login event
      try {
        await ApiService.trackEvent('user_login', {
          userType: loginResponse.user.userType,
          mfaEnabled: loginResponse.user.mfaEnabled,
          deviceId,
        });
      } catch (trackingError) {
        console.warn('Failed to track login event:', trackingError);
      }

      return loginResponse;
    } catch (error: any) {
      const errorMessage = error.message || 'Login failed';
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
      
      // Track failed login attempt
      try {
        await ApiService.trackEvent('user_login_failed', {
          email: credentials.email,
          error: errorMessage,
        });
      } catch (trackingError) {
        console.warn('Failed to track failed login event:', trackingError);
      }
      
      throw error;
    }
  };

  /**
   * Logout user
   */
  const logout = async (): Promise<void> => {
    try {
      // Track logout event before clearing tokens
      if (state.user?.id) {
        try {
          await ApiService.trackEvent('user_logout', {
            userId: state.user.id,
            userType: state.user.userType,
          });
        } catch (trackingError) {
          console.warn('Failed to track logout event:', trackingError);
        }
      }

      // Call API logout endpoint to invalidate tokens on server
      try {
        await ApiService.logout();
      } catch (apiError) {
        console.warn('API logout failed, continuing with local logout:', apiError);
      }

      // Clear all auth-related storage
      await Promise.all([
        AsyncStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN),
        AsyncStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN),
        AsyncStorage.removeItem(STORAGE_KEYS.USER),
        // Clear ApiService tokens too
        ApiService.clearAuthToken(),
        AsyncStorage.removeItem('refreshToken'),
        // Keep deviceId for future logins
      ]);

      // Reset state
      setState({
        isAuthenticated: false,
        user: null,
        accessToken: null,
        refreshToken: null,
        loading: false,
        error: null,
        isOnboardingComplete: state.isOnboardingComplete,
      });
    } catch (error) {
      console.error('Logout error:', error);
      // Even if there's an error, reset the state to ensure user is logged out locally
      setState({
        isAuthenticated: false,
        user: null,
        accessToken: null,
        refreshToken: null,
        loading: false,
        error: null,
        isOnboardingComplete: state.isOnboardingComplete,
      });
    }
  };

  /**
   * Refresh access token
   */
  const refreshAccessToken = async (): Promise<void> => {
    try {
      if (!state.refreshToken) {
        throw new Error('No refresh token available');
      }

      console.log('Refreshing access token...');

      // Call API to refresh tokens
      const refreshResponse = await ApiService.refreshToken();

      if (!refreshResponse.accessToken) {
        throw new Error('Invalid refresh response');
      }

      // Store new tokens
      await Promise.all([
        AsyncStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, refreshResponse.accessToken),
        AsyncStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshResponse.refreshToken),
        // Update ApiService token too
        ApiService.setAuthToken(refreshResponse.accessToken),
        AsyncStorage.setItem('refreshToken', refreshResponse.refreshToken),
      ]);

      // Update state with new tokens
      setState(prev => ({
        ...prev,
        accessToken: refreshResponse.accessToken,
        refreshToken: refreshResponse.refreshToken,
      }));

      console.log('Access token refreshed successfully');

      // Track token refresh event
      try {
        await ApiService.trackEvent('token_refreshed', {
          userId: state.user?.id,
        });
      } catch (trackingError) {
        console.warn('Failed to track token refresh event:', trackingError);
      }

    } catch (error) {
      console.error('Token refresh failed:', error);
      
      // Track failed token refresh
      try {
        await ApiService.trackEvent('token_refresh_failed', {
          userId: state.user?.id,
          error: error.message,
        });
      } catch (trackingError) {
        console.warn('Failed to track token refresh failure:', trackingError);
      }

      // If refresh fails, logout user to force re-authentication
      await logout();
      throw error;
    }
  };

  /**
   * Update user data
   */
  const updateUser = (user: User): void => {
    setState(prev => ({ ...prev, user }));
    AsyncStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
  };

  /**
   * Clear error
   */
  const clearError = (): void => {
    setState(prev => ({ ...prev, error: null }));
  };

  /**
   * Complete onboarding
   */
  const completeOnboarding = (): void => {
    setState(prev => ({ ...prev, isOnboardingComplete: true }));
    AsyncStorage.setItem(STORAGE_KEYS.ONBOARDING_COMPLETE, 'true');
  };

  /**
   * Register new user
   */
  const register = async (userData: RegisterRequest): Promise<LoginResponse> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      // Get device information
      const deviceId = userData.deviceId || await DeviceInfo.getUniqueId();
      const deviceModel = await DeviceInfo.getModel();
      const osVersion = await DeviceInfo.getSystemVersion();
      const appVersion = await DeviceInfo.getVersion();

      // Prepare registration payload
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

      // Call API to register user
      const registerResponse: LoginResponse = await ApiService.register(registrationPayload);

      if (!registerResponse.success) {
        throw new Error(registerResponse.message || 'Registration failed');
      }

      // If registration is successful and user is automatically logged in
      if (registerResponse.accessToken) {
        // Store tokens and user data
        await Promise.all([
          AsyncStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, registerResponse.accessToken),
          AsyncStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, registerResponse.refreshToken),
          AsyncStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(registerResponse.user)),
          // Also store in ApiService format
          ApiService.setAuthToken(registerResponse.accessToken),
          AsyncStorage.setItem('refreshToken', registerResponse.refreshToken),
          AsyncStorage.setItem('deviceId', deviceId),
        ]);

        setState({
          isAuthenticated: true,
          user: registerResponse.user,
          accessToken: registerResponse.accessToken,
          refreshToken: registerResponse.refreshToken,
          loading: false,
          error: null,
          isOnboardingComplete: false, // New users need onboarding
        });

        // Track successful registration
        try {
          await ApiService.trackEvent('user_registered', {
            userType: registerResponse.user.userType,
            deviceId,
          });
        } catch (trackingError) {
          console.warn('Failed to track registration event:', trackingError);
        }
      } else {
        // Registration successful but requires email verification
        setState(prev => ({ ...prev, loading: false }));
      }

      return registerResponse;
    } catch (error: any) {
      const errorMessage = error.message || 'Registration failed';
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));

      // Track failed registration
      try {
        await ApiService.trackEvent('user_registration_failed', {
          email: userData.email,
          error: errorMessage,
        });
      } catch (trackingError) {
        console.warn('Failed to track registration failure:', trackingError);
      }

      throw error;
    }
  };

  /**
   * Verify email address
   */
  const verifyEmail = async (token: string): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      await ApiService.verifyEmail(token);

      setState(prev => ({ ...prev, loading: false }));

      // Track email verification
      try {
        await ApiService.trackEvent('email_verified', {
          userId: state.user?.id,
        });
      } catch (trackingError) {
        console.warn('Failed to track email verification:', trackingError);
      }

    } catch (error: any) {
      const errorMessage = error.message || 'Email verification failed';
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
      throw error;
    }
  };

  /**
   * Request password reset
   */
  const requestPasswordReset = async (email: string): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      await ApiService.requestPasswordReset(email);

      setState(prev => ({ ...prev, loading: false }));

      // Track password reset request
      try {
        await ApiService.trackEvent('password_reset_requested', {
          email,
        });
      } catch (trackingError) {
        console.warn('Failed to track password reset request:', trackingError);
      }

    } catch (error: any) {
      const errorMessage = error.message || 'Password reset request failed';
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
      throw error;
    }
  };

  /**
   * Reset password with token
   */
  const resetPassword = async (token: string, newPassword: string): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      await ApiService.resetPassword(token, newPassword);

      setState(prev => ({ ...prev, loading: false }));

      // Track password reset completion
      try {
        await ApiService.trackEvent('password_reset_completed', {
          userId: state.user?.id,
          timestamp: new Date().toISOString(),
          success: true,
          // Note: Removed sensitive token from tracking for security compliance
        });
      } catch (trackingError) {
        logWarn('Failed to track password reset completion', {
          feature: 'authentication',
          action: 'track_password_reset_completion_failed'
        }, trackingError);
      }

    } catch (error: any) {
      const errorMessage = error.message || 'Password reset failed';
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
      }));
      throw error;
    }
  };

  const contextValue: AuthContextType = {
    ...state,
    login,
    register,
    logout,
    refreshAccessToken,
    updateUser,
    clearError,
    completeOnboarding,
    verifyEmail,
    requestPasswordReset,
    resetPassword,
  };

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export default AuthProvider;