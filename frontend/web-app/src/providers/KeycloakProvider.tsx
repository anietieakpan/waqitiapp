import React, { createContext, useContext, useEffect, useState } from 'react';
import { ReactKeycloakProvider } from '@react-keycloak/web';
import keycloak, { keycloakInitOptions, getUserProfile, hasRole, refreshToken } from '../config/keycloak';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

// Types
interface UserProfile {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
  emailVerified: boolean;
  roles: string[];
}

interface KeycloakContextType {
  isAuthenticated: boolean;
  isInitialized: boolean;
  user: UserProfile | null;
  token: string | undefined;
  login: () => void;
  logout: () => void;
  register: () => void;
  hasRole: (role: string) => boolean;
  refreshToken: (minValidity?: number) => Promise<boolean>;
}

// Context
const KeycloakContext = createContext<KeycloakContextType | undefined>(undefined);

// Hook to use Keycloak context
export const useKeycloak = () => {
  const context = useContext(KeycloakContext);
  if (!context) {
    throw new Error('useKeycloak must be used within KeycloakProvider');
  }
  return context;
};

// Provider Props
interface KeycloakProviderProps {
  children: React.ReactNode;
  loadingComponent?: React.ReactNode;
  onTokenExpired?: () => void;
  onAuthSuccess?: (token: string) => void;
  onAuthError?: (error: any) => void;
  onAuthRefreshSuccess?: (token: string) => void;
  onAuthRefreshError?: () => void;
}

// Loading component
const DefaultLoadingComponent = () => (
  <div style={{
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100vh',
    fontSize: '1.2rem',
    color: '#666'
  }}>
    Initializing authentication...
  </div>
);

// Keycloak Provider Component
export const KeycloakProvider: React.FC<KeycloakProviderProps> = ({
  children,
  loadingComponent = <DefaultLoadingComponent />,
  onTokenExpired,
  onAuthSuccess,
  onAuthError,
  onAuthRefreshSuccess,
  onAuthRefreshError,
}) => {
  const [isInitialized, setIsInitialized] = useState(false);
  const [user, setUser] = useState<UserProfile | null>(null);
  const navigate = useNavigate();

  // Event handlers
  const handleTokenExpired = () => {
    console.warn('Token expired');
    if (onTokenExpired) {
      onTokenExpired();
    } else {
      // Default behavior: try to refresh
      refreshToken().then(refreshed => {
        if (!refreshed) {
          keycloak.login();
        }
      });
    }
  };

  const handleAuthSuccess = () => {
    const token = keycloak.token;
    if (token) {
      // Set default Authorization header for all axios requests
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      
      // Update user profile
      const profile = getUserProfile();
      setUser(profile);
      
      if (onAuthSuccess) {
        onAuthSuccess(token);
      }
    }
  };

  const handleAuthError = (error: any) => {
    console.error('Authentication error:', error);
    if (onAuthError) {
      onAuthError(error);
    }
  };

  const handleAuthRefreshSuccess = () => {
    const token = keycloak.token;
    if (token) {
      // Update Authorization header
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      
      if (onAuthRefreshSuccess) {
        onAuthRefreshSuccess(token);
      }
    }
  };

  const handleAuthRefreshError = () => {
    console.error('Token refresh failed');
    if (onAuthRefreshError) {
      onAuthRefreshError();
    } else {
      // Default behavior: redirect to login
      keycloak.login();
    }
  };

  // Context value
  const contextValue: KeycloakContextType = {
    isAuthenticated: !!keycloak.authenticated,
    isInitialized,
    user,
    token: keycloak.token,
    login: () => keycloak.login(),
    logout: () => keycloak.logout(),
    register: () => keycloak.register(),
    hasRole,
    refreshToken,
  };

  return (
    <ReactKeycloakProvider
      authClient={keycloak}
      initOptions={keycloakInitOptions}
      onEvent={(event, error) => {
        console.log('Keycloak event:', event, error);
        
        switch (event) {
          case 'onReady':
            setIsInitialized(true);
            if (keycloak.authenticated) {
              handleAuthSuccess();
            }
            break;
          case 'onAuthSuccess':
            handleAuthSuccess();
            break;
          case 'onAuthError':
            handleAuthError(error);
            break;
          case 'onAuthRefreshSuccess':
            handleAuthRefreshSuccess();
            break;
          case 'onAuthRefreshError':
            handleAuthRefreshError();
            break;
          case 'onTokenExpired':
            handleTokenExpired();
            break;
          case 'onAuthLogout':
            setUser(null);
            delete axios.defaults.headers.common['Authorization'];
            navigate('/');
            break;
        }
      }}
      LoadingComponent={loadingComponent}
    >
      <KeycloakContext.Provider value={contextValue}>
        {children}
      </KeycloakContext.Provider>
    </ReactKeycloakProvider>
  );
};

// Protected Route Component
interface ProtectedRouteProps {
  children: React.ReactNode;
  roles?: string[];
  fallback?: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  roles = [],
  fallback = <div>Access Denied</div>,
}) => {
  const { isAuthenticated, hasRole } = useKeycloak();

  if (!isAuthenticated) {
    keycloak.login();
    return null;
  }

  if (roles.length > 0) {
    const hasRequiredRole = roles.some(role => hasRole(role));
    if (!hasRequiredRole) {
      return <>{fallback}</>;
    }
  }

  return <>{children}</>;
};

export default KeycloakProvider;