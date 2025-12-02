/**
 * BiometricContext - Provides biometric authentication state and methods
 * Integrates with BiometricAuthService for comprehensive biometric functionality
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { BiometricAuthService } from '../services/biometric/BiometricAuthService';
import {
  BiometricStatus,
  BiometricCapabilities,
  BiometricAuthResult,
  BiometricSettings,
  BiometricPromptConfig,
  SecurityAssessment,
  BiometricAuthError,
  AuthenticationMethod,
  SecurityLevel,
} from '../services/biometric/types';

// BiometricState interface
interface BiometricState {
  // Status and availability
  status: BiometricStatus;
  isAvailable: boolean;
  isSetup: boolean;
  isEnabled: boolean;
  
  // Capabilities
  capabilities: BiometricCapabilities | null;
  
  // Authentication state
  isAuthenticating: boolean;
  isAuthenticated: boolean;
  lastAuthenticationTime: number | null;
  
  // Settings
  settings: BiometricSettings | null;
  
  // Security
  securityAssessment: SecurityAssessment | null;
  
  // Error handling
  error: string | null;
  lastError: BiometricAuthError | null;
  
  // Loading states
  loading: boolean;
  initializing: boolean;
}

// BiometricContext interface
interface BiometricContextType extends BiometricState {
  // Core authentication methods
  setupBiometric: (userId: string, promptConfig?: BiometricPromptConfig) => Promise<BiometricAuthResult>;
  authenticate: (userId: string, promptConfig?: BiometricPromptConfig) => Promise<BiometricAuthResult>;
  disableBiometric: (userId: string) => Promise<boolean>;
  
  // Capability and status checks
  checkAvailability: () => Promise<void>;
  checkSetup: (userId: string) => Promise<void>;
  
  // Settings management
  getSettings: (userId: string) => Promise<void>;
  updateSettings: (userId: string, settings: Partial<BiometricSettings>) => Promise<void>;
  
  // Security assessments
  checkDeviceSecurity: () => Promise<void>;
  
  // Authentication verification
  checkBiometricAuth: (userId?: string) => Promise<void>;
  
  // Fallback methods
  handleFallback: (userId: string, method: AuthenticationMethod) => Promise<BiometricAuthResult>;
  
  // Session management
  logout: (userId: string) => Promise<void>;
  clearError: () => void;
  reset: () => void;
}

// Default state
const defaultState: BiometricState = {
  status: BiometricStatus.UNKNOWN,
  isAvailable: false,
  isSetup: false,
  isEnabled: false,
  capabilities: null,
  isAuthenticating: false,
  isAuthenticated: false,
  lastAuthenticationTime: null,
  settings: null,
  securityAssessment: null,
  error: null,
  lastError: null,
  loading: false,
  initializing: true,
};

// Create context
const BiometricContext = createContext<BiometricContextType | undefined>(undefined);

// Custom hook to use BiometricContext
export const useBiometric = () => {
  const context = useContext(BiometricContext);
  if (!context) {
    throw new Error('useBiometric must be used within a BiometricProvider');
  }
  return context;
};

// BiometricProvider props
interface BiometricProviderProps {
  children: ReactNode;
}

// BiometricProvider component
export const BiometricProvider: React.FC<BiometricProviderProps> = ({ children }) => {
  const [state, setState] = useState<BiometricState>(defaultState);
  const biometricService = BiometricAuthService.getInstance();

  // Initialize biometric service on mount
  useEffect(() => {
    initializeBiometricService();
  }, []);

  /**
   * Initialize the biometric service
   */
  const initializeBiometricService = async () => {
    try {
      setState(prev => ({ ...prev, initializing: true, error: null }));

      await biometricService.initialize();
      
      // Check initial capabilities
      const capabilities = await biometricService.getCapabilities();
      
      // Determine initial status
      let status = BiometricStatus.UNKNOWN;
      if (!capabilities.isAvailable) {
        status = BiometricStatus.NOT_AVAILABLE;
      } else if (!capabilities.isEnrolled) {
        status = BiometricStatus.NOT_ENROLLED;
      } else {
        status = BiometricStatus.AVAILABLE;
      }

      setState(prev => ({
        ...prev,
        initializing: false,
        isAvailable: capabilities.isAvailable,
        capabilities,
        status,
        error: null,
      }));

      console.log('BiometricProvider initialized successfully');
    } catch (error: any) {
      console.error('Failed to initialize BiometricProvider:', error);
      setState(prev => ({
        ...prev,
        initializing: false,
        error: error.message || 'Failed to initialize biometric authentication',
        status: BiometricStatus.UNKNOWN,
      }));
    }
  };

  /**
   * Setup biometric authentication for a user
   */
  const setupBiometric = async (
    userId: string,
    promptConfig?: BiometricPromptConfig
  ): Promise<BiometricAuthResult> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null, lastError: null }));

      const result = await biometricService.setupBiometric(userId, promptConfig);

      if (result.success) {
        setState(prev => ({
          ...prev,
          isSetup: true,
          isEnabled: true,
          status: BiometricStatus.AVAILABLE,
          loading: false,
          error: null,
        }));
      } else {
        setState(prev => ({
          ...prev,
          loading: false,
          error: result.errorMessage || 'Biometric setup failed',
          lastError: result.error || null,
        }));
      }

      return result;
    } catch (error: any) {
      console.error('Setup biometric failed:', error);
      const errorMessage = error.message || 'An unexpected error occurred during setup';
      
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
        lastError: BiometricAuthError.SYSTEM_ERROR,
      }));

      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage,
      };
    }
  };

  /**
   * Authenticate using biometrics
   */
  const authenticate = async (
    userId: string,
    promptConfig?: BiometricPromptConfig
  ): Promise<BiometricAuthResult> => {
    try {
      setState(prev => ({ 
        ...prev, 
        isAuthenticating: true, 
        error: null, 
        lastError: null 
      }));

      const result = await biometricService.authenticate(userId, promptConfig);

      if (result.success) {
        setState(prev => ({
          ...prev,
          isAuthenticating: false,
          isAuthenticated: true,
          lastAuthenticationTime: Date.now(),
          error: null,
        }));
      } else {
        setState(prev => ({
          ...prev,
          isAuthenticating: false,
          isAuthenticated: false,
          error: result.errorMessage || 'Authentication failed',
          lastError: result.error || null,
        }));

        // Handle specific error cases
        if (result.error === BiometricAuthError.TEMPORARILY_LOCKED) {
          setState(prev => ({ ...prev, status: BiometricStatus.TEMPORARILY_LOCKED }));
        } else if (result.error === BiometricAuthError.NOT_ENROLLED) {
          setState(prev => ({ ...prev, status: BiometricStatus.NOT_ENROLLED }));
        }
      }

      return result;
    } catch (error: any) {
      console.error('Biometric authentication failed:', error);
      const errorMessage = error.message || 'An unexpected error occurred during authentication';
      
      setState(prev => ({
        ...prev,
        isAuthenticating: false,
        isAuthenticated: false,
        error: errorMessage,
        lastError: BiometricAuthError.SYSTEM_ERROR,
      }));

      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage,
      };
    }
  };

  /**
   * Disable biometric authentication
   */
  const disableBiometric = async (userId: string): Promise<boolean> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      const result = await biometricService.disableBiometric(userId);

      if (result) {
        setState(prev => ({
          ...prev,
          isSetup: false,
          isEnabled: false,
          isAuthenticated: false,
          lastAuthenticationTime: null,
          settings: null,
          status: state.isAvailable ? BiometricStatus.AVAILABLE : BiometricStatus.NOT_AVAILABLE,
          loading: false,
          error: null,
        }));
      } else {
        setState(prev => ({
          ...prev,
          loading: false,
          error: 'Failed to disable biometric authentication',
        }));
      }

      return result;
    } catch (error: any) {
      console.error('Failed to disable biometric:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to disable biometric authentication',
      }));
      return false;
    }
  };

  /**
   * Check biometric availability
   */
  const checkAvailability = async (): Promise<void> => {
    try {
      const capabilities = await biometricService.getCapabilities();
      
      let status = BiometricStatus.UNKNOWN;
      if (!capabilities.isAvailable) {
        status = BiometricStatus.NOT_AVAILABLE;
      } else if (!capabilities.isEnrolled) {
        status = BiometricStatus.NOT_ENROLLED;
      } else {
        status = BiometricStatus.AVAILABLE;
      }

      setState(prev => ({
        ...prev,
        isAvailable: capabilities.isAvailable,
        capabilities,
        status,
        error: null,
      }));
    } catch (error: any) {
      console.error('Failed to check biometric availability:', error);
      setState(prev => ({
        ...prev,
        error: error.message || 'Failed to check biometric availability',
      }));
    }
  };

  /**
   * Check if biometric is setup for user
   */
  const checkSetup = async (userId: string): Promise<void> => {
    try {
      const isSetup = await biometricService.isSetup(userId);
      
      setState(prev => ({
        ...prev,
        isSetup,
        isEnabled: isSetup,
        error: null,
      }));
    } catch (error: any) {
      console.error('Failed to check biometric setup:', error);
      setState(prev => ({
        ...prev,
        error: error.message || 'Failed to check biometric setup',
      }));
    }
  };

  /**
   * Get biometric settings
   */
  const getSettings = async (userId: string): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true }));
      
      const settings = await biometricService.getSettings(userId);
      
      setState(prev => ({
        ...prev,
        settings,
        loading: false,
        error: null,
      }));
    } catch (error: any) {
      console.error('Failed to get biometric settings:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to get biometric settings',
      }));
    }
  };

  /**
   * Update biometric settings
   */
  const updateSettings = async (
    userId: string,
    settingsUpdate: Partial<BiometricSettings>
  ): Promise<void> => {
    try {
      setState(prev => ({ ...prev, loading: true }));
      
      await biometricService.updateSettings(userId, settingsUpdate);
      
      // Refresh settings
      await getSettings(userId);
    } catch (error: any) {
      console.error('Failed to update biometric settings:', error);
      setState(prev => ({
        ...prev,
        loading: false,
        error: error.message || 'Failed to update biometric settings',
      }));
    }
  };

  /**
   * Check device security
   */
  const checkDeviceSecurity = async (): Promise<void> => {
    try {
      const securityAssessment = await biometricService.checkDeviceSecurity();
      
      setState(prev => ({
        ...prev,
        securityAssessment,
        error: null,
      }));
    } catch (error: any) {
      console.error('Failed to check device security:', error);
      setState(prev => ({
        ...prev,
        error: error.message || 'Failed to check device security',
      }));
    }
  };

  /**
   * Check biometric authentication (used by RootNavigator)
   */
  const checkBiometricAuth = async (userId?: string): Promise<void> => {
    if (!userId || !state.isAvailable || !state.isSetup) {
      return;
    }

    try {
      // Check if recent authentication is still valid
      const now = Date.now();
      const recentAuthThreshold = 5 * 60 * 1000; // 5 minutes
      
      if (
        state.lastAuthenticationTime &&
        (now - state.lastAuthenticationTime) < recentAuthThreshold
      ) {
        setState(prev => ({ ...prev, isAuthenticated: true }));
        return;
      }

      // Perform biometric authentication with quick prompt
      const quickPromptConfig: BiometricPromptConfig = {
        title: 'Quick Authentication',
        subtitle: 'Authenticate to continue',
        description: 'Use biometrics to unlock the app',
        negativeButtonText: 'Cancel',
        allowDeviceCredentials: true,
      };

      await authenticate(userId, quickPromptConfig);
    } catch (error: any) {
      console.error('Quick biometric auth failed:', error);
      // Don't throw error for quick auth failures
    }
  };

  /**
   * Handle fallback authentication methods
   */
  const handleFallback = async (
    userId: string,
    method: AuthenticationMethod
  ): Promise<BiometricAuthResult> => {
    try {
      setState(prev => ({ ...prev, loading: true, error: null }));

      const result = await biometricService.handleFallback(userId, method);

      if (result.success) {
        setState(prev => ({
          ...prev,
          isAuthenticated: true,
          lastAuthenticationTime: Date.now(),
          loading: false,
          error: null,
        }));
      } else {
        setState(prev => ({
          ...prev,
          loading: false,
          error: result.errorMessage || 'Fallback authentication failed',
          lastError: result.error || null,
        }));
      }

      return result;
    } catch (error: any) {
      console.error('Fallback authentication failed:', error);
      const errorMessage = error.message || 'Fallback authentication failed';
      
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
        lastError: BiometricAuthError.SYSTEM_ERROR,
      }));

      return {
        success: false,
        error: BiometricAuthError.SYSTEM_ERROR,
        errorMessage,
      };
    }
  };

  /**
   * Logout and clear biometric session
   */
  const logout = async (userId: string): Promise<void> => {
    try {
      await biometricService.logout(userId);
      
      setState(prev => ({
        ...prev,
        isAuthenticated: false,
        lastAuthenticationTime: null,
        error: null,
        lastError: null,
      }));
    } catch (error: any) {
      console.error('Failed to logout biometric session:', error);
      // Still update state even if logout fails
      setState(prev => ({
        ...prev,
        isAuthenticated: false,
        lastAuthenticationTime: null,
      }));
    }
  };

  /**
   * Clear current error
   */
  const clearError = (): void => {
    setState(prev => ({ ...prev, error: null, lastError: null }));
  };

  /**
   * Reset biometric state
   */
  const reset = (): void => {
    setState({
      ...defaultState,
      initializing: false,
      capabilities: state.capabilities,
      isAvailable: state.isAvailable,
      status: state.status,
    });
  };

  // Context value
  const contextValue: BiometricContextType = {
    ...state,
    setupBiometric,
    authenticate,
    disableBiometric,
    checkAvailability,
    checkSetup,
    getSettings,
    updateSettings,
    checkDeviceSecurity,
    checkBiometricAuth,
    handleFallback,
    logout,
    clearError,
    reset,
  };

  return (
    <BiometricContext.Provider value={contextValue}>
      {children}
    </BiometricContext.Provider>
  );
};

export default BiometricProvider;