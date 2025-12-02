import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import axios from 'axios';
import {
  User,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  AuthState,
  PasswordResetRequest,
  PasswordResetResponse,
  ChangePasswordRequest,
  VerifyEmailRequest,
  MfaSetupRequest,
  MfaSetupResponse,
} from '@/types/auth';
import { secureStorage, initializeSecureStorage, installLocalStorageGuard } from '@/utils/secureStorage';

const API_GATEWAY_URL = process.env.REACT_APP_API_GATEWAY_URL || 'http://localhost:8080/api/v1';

// Configure axios to include credentials (cookies) with every request
axios.defaults.withCredentials = true;

interface AuthContextType extends AuthState {
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<RegisterResponse>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<void>;
  resetPassword: (data: PasswordResetRequest) => Promise<PasswordResetResponse>;
  changePassword: (data: ChangePasswordRequest) => Promise<void>;
  verifyEmail: (data: VerifyEmailRequest) => Promise<void>;
  setupMfa: (data: MfaSetupRequest) => Promise<MfaSetupResponse>;
  verifyMfa: (code: string) => Promise<void>;
  updateProfile: (updates: Partial<User>) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    user: null,
    accessToken: null,
    refreshToken: null,
    loading: true,
    error: null,
  });

  // Initialize secure storage and auth state
  useEffect(() => {
    const initializeAuth = async () => {
      try {
        // Initialize secure storage (migrates legacy localStorage tokens)
        initializeSecureStorage();

        // Install localStorage guard in development
        if (process.env.NODE_ENV === 'development') {
          installLocalStorageGuard();
        }

        // Check if user is authenticated (via HttpOnly cookie)
        const isAuthenticated = secureStorage.isAuthenticated();
        const userMetadata = secureStorage.getUserMetadata();

        if (isAuthenticated && userMetadata) {
          setState({
            isAuthenticated: true,
            user: userMetadata,
            accessToken: null, // Stored in HttpOnly cookie, not accessible to JS
            refreshToken: null, // Stored in HttpOnly cookie, not accessible to JS
            loading: false,
            error: null,
          });

          // Verify token is still valid with server
          await verifyToken();
        } else {
          setState(prev => ({ ...prev, loading: false }));
        }
      } catch (error) {
        console.error('Failed to initialize auth:', error);
        // Clear invalid auth data
        secureStorage.clearAuth();
        setState(prev => ({ ...prev, loading: false }));
      }
    };

    initializeAuth();
  }, []);

  const verifyToken = async () => {
    try {
      // Token automatically sent via HttpOnly cookie
      const response = await axios.get(`${API_GATEWAY_URL}/auth/verify`);
      return response.data;
    } catch (error) {
      throw error;
    }
  };

  const login = async (credentials: LoginRequest) => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      // Server will set HttpOnly cookies with accessToken and refreshToken
      const response = await axios.post<LoginResponse>(
        `${API_GATEWAY_URL}/auth/login`,
        credentials
      );

      if (response.data.requiresMfa) {
        // Store partial auth state for MFA
        setState(prev => ({
          ...prev,
          loading: false,
          error: null,
          user: response.data.user,
        }));
        throw new Error('MFA_REQUIRED');
      }

      const { user } = response.data;

      // Store only non-sensitive user metadata in sessionStorage
      secureStorage.setUserMetadata(user);

      setState({
        isAuthenticated: true,
        user,
        accessToken: null, // Tokens stored in HttpOnly cookies by server
        refreshToken: null, // Tokens stored in HttpOnly cookies by server
        loading: false,
        error: null,
      });
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.response?.data?.message || error.message,
      }));
      throw error;
    }
  };

  const register = async (data: RegisterRequest): Promise<RegisterResponse> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      const response = await axios.post<RegisterResponse>(
        `${API_GATEWAY_URL}/users/register`,
        data
      );

      setState(prev => ({ ...prev, loading: false }));
      return response.data;
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.response?.data?.message || error.message,
      }));
      throw error;
    }
  };

  const logout = async () => {
    try {
      // Server will clear HttpOnly cookies
      await axios.post(`${API_GATEWAY_URL}/auth/logout`);
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      // Clear secure storage (sessionStorage metadata)
      secureStorage.clearAuth();

      // Reset state
      setState({
        isAuthenticated: false,
        user: null,
        accessToken: null,
        refreshToken: null,
        loading: false,
        error: null,
      });
    }
  };

  const refreshToken = async () => {
    try {
      // Server will read refreshToken from HttpOnly cookie and set new cookies
      await axios.post(`${API_GATEWAY_URL}/auth/refresh`);

      // No need to update state - tokens are in HttpOnly cookies
      // User metadata remains unchanged
    } catch (error) {
      // If refresh fails, logout user
      await logout();
      throw error;
    }
  };

  const resetPassword = async (
    data: PasswordResetRequest
  ): Promise<PasswordResetResponse> => {
    try {
      const response = await axios.post<PasswordResetResponse>(
        `${API_GATEWAY_URL}/users/password/reset`,
        data
      );
      return response.data;
    } catch (error: any) {
      throw error.response?.data || error;
    }
  };

  const changePassword = async (data: ChangePasswordRequest) => {
    try {
      // Token automatically sent via HttpOnly cookie
      await axios.post(`${API_GATEWAY_URL}/users/password/change`, data);
    } catch (error: any) {
      throw error.response?.data || error;
    }
  };

  const verifyEmail = async (data: VerifyEmailRequest) => {
    try {
      await axios.post(`${API_GATEWAY_URL}/users/verify`, data);

      // Update user verified status
      if (state.user) {
        const updatedUser = { ...state.user, verified: true };
        secureStorage.setUserMetadata(updatedUser);
        setState(prev => ({ ...prev, user: updatedUser }));
      }
    } catch (error: any) {
      throw error.response?.data || error;
    }
  };

  const setupMfa = async (data: MfaSetupRequest): Promise<MfaSetupResponse> => {
    try {
      // Token automatically sent via HttpOnly cookie
      const response = await axios.post<MfaSetupResponse>(
        `${API_GATEWAY_URL}/security/mfa/setup`,
        data
      );
      return response.data;
    } catch (error: any) {
      throw error.response?.data || error;
    }
  };

  const verifyMfa = async (code: string) => {
    try {
      // Server will set HttpOnly cookies on successful MFA verification
      const response = await axios.post<LoginResponse>(
        `${API_GATEWAY_URL}/security/mfa/verify`,
        { code, userId: state.user?.id }
      );

      const { user } = response.data;

      // Store only user metadata
      secureStorage.setUserMetadata(user);

      setState({
        isAuthenticated: true,
        user,
        accessToken: null, // Tokens in HttpOnly cookies
        refreshToken: null, // Tokens in HttpOnly cookies
        loading: false,
        error: null,
      });
    } catch (error: any) {
      throw error.response?.data || error;
    }
  };

  const updateProfile = async (updates: Partial<User>) => {
    try {
      // Token automatically sent via HttpOnly cookie
      const response = await axios.patch<User>(
        `${API_GATEWAY_URL}/users/profile`,
        updates
      );

      const updatedUser = response.data;
      secureStorage.setUserMetadata(updatedUser);
      setState(prev => ({ ...prev, user: updatedUser }));
    } catch (error: any) {
      throw error.response?.data || error;
    }
  };

  // Set up axios interceptor for automatic token refresh
  useEffect(() => {
    const interceptor = axios.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;

        if (
          error.response?.status === 401 &&
          !originalRequest._retry &&
          secureStorage.isAuthenticated()
        ) {
          originalRequest._retry = true;

          try {
            await refreshToken();
            // Retry original request (token automatically included via cookie)
            return axios(originalRequest);
          } catch (refreshError) {
            // Refresh failed, redirect to login
            await logout();
            window.location.href = '/login';
          }
        }

        return Promise.reject(error);
      }
    );

    return () => {
      axios.interceptors.response.eject(interceptor);
    };
  }, []);

  const contextValue: AuthContextType = {
    ...state,
    login,
    register,
    logout,
    refreshToken,
    resetPassword,
    changePassword,
    verifyEmail,
    setupMfa,
    verifyMfa,
    updateProfile,
  };

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};