/**
 * Biometric Authentication Components
 * 
 * A comprehensive suite of React Native components for biometric authentication
 * integration. These components work seamlessly with the existing useBiometric hook
 * and BiometricContext to provide a complete biometric authentication solution.
 * 
 * @author Waqiti Development Team
 * @version 1.0.0
 */

// Main Components
export { BiometricLoginComponent } from './BiometricLoginComponent';
export type { BiometricLoginComponentProps } from './BiometricLoginComponent';

export { BiometricLoginButton } from './BiometricLoginButton';
export type { BiometricLoginButtonProps } from './BiometricLoginButton';

export { QuickUnlockComponent } from './QuickUnlockComponent';
export type { QuickUnlockComponentProps } from './QuickUnlockComponent';

// Error Handling
export { BiometricErrorHandler } from './BiometricErrorHandler';
export type { BiometricErrorHandlerProps } from './BiometricErrorHandler';

// Fallback Management
export { BiometricFallbackManager } from './BiometricFallbackManager';
export type { BiometricFallbackManagerProps } from './BiometricFallbackManager';

// Icon and UI Components
export { 
  BiometricTypeIcon,
  FingerprintIcon,
  FaceIDIcon,
  BiometricsIcon,
  FallbackIcon,
  BiometricOptions,
} from './BiometricTypeIcon';
export type { 
  BiometricTypeIconProps,
  BiometricOptionsProps,
  IconState,
  IconSize,
} from './BiometricTypeIcon';

// Re-export types from biometric services for convenience
export type {
  BiometricStatus,
  BiometricAuthError,
  BiometricCapabilities,
  BiometricPromptConfig,
  BiometricAuthResult,
  AuthenticationMethod,
  SecurityLevel,
  BiometricSettings,
  BiometricFallbackOptions,
} from '../../services/biometric/types';

/**
 * Component Usage Examples and Integration Guide
 * 
 * This module provides comprehensive biometric authentication components that can be
 * easily integrated into your React Native application. Below are usage examples
 * for different scenarios.
 */

/**
 * Basic Integration Example:
 * 
 * ```tsx
 * import React from 'react';
 * import { View } from 'react-native';
 * import { BiometricLoginButton } from './components/auth';
 * 
 * const LoginScreen = () => {
 *   const handleBiometricSuccess = () => {
 *     // Handle successful authentication
 *     console.log('Biometric authentication successful');
 *   };
 * 
 *   const handleFallback = () => {
 *     // Handle fallback to PIN/password
 *     console.log('Fallback to traditional authentication');
 *   };
 * 
 *   return (
 *     <View>
 *       <BiometricLoginButton
 *         userId="user123"
 *         onSuccess={handleBiometricSuccess}
 *         onFallback={handleFallback}
 *         variant="primary"
 *         showIcon={true}
 *       />
 *     </View>
 *   );
 * };
 * ```
 */

/**
 * Comprehensive Login Screen Integration:
 * 
 * ```tsx
 * import React, { useState } from 'react';
 * import { View, StyleSheet } from 'react-native';
 * import { 
 *   BiometricLoginComponent,
 *   BiometricErrorHandler,
 *   BiometricAuthError 
 * } from './components/auth';
 * 
 * const LoginScreen = () => {
 *   const [showError, setShowError] = useState(false);
 *   const [error, setError] = useState<BiometricAuthError | null>(null);
 * 
 *   const handleSuccess = (result: { method: string; timestamp: number }) => {
 *     console.log('Authentication successful:', result);
 *     // Navigate to main app
 *   };
 * 
 *   const handleError = (errorMessage: string) => {
 *     console.error('Authentication error:', errorMessage);
 *     setError(BiometricAuthError.AUTHENTICATION_FAILED);
 *     setShowError(true);
 *   };
 * 
 *   const handleFallback = () => {
 *     // Show traditional login form
 *     console.log('Showing traditional login');
 *   };
 * 
 *   return (
 *     <View style={styles.container}>
 *       <BiometricLoginComponent
 *         userId="user123"
 *         onSuccess={handleSuccess}
 *         onFallback={handleFallback}
 *         onError={handleError}
 *         mode="login"
 *         showSetupPrompt={true}
 *         style={styles.biometricLogin}
 *       />
 * 
 *       <BiometricErrorHandler
 *         visible={showError}
 *         error={error}
 *         onDismiss={() => setShowError(false)}
 *         onRetry={() => {
 *           setShowError(false);
 *           setError(null);
 *         }}
 *         onFallback={handleFallback}
 *       />
 *     </View>
 *   );
 * };
 * 
 * const styles = StyleSheet.create({
 *   container: {
 *     flex: 1,
 *     justifyContent: 'center',
 *     padding: 20,
 *   },
 *   biometricLogin: {
 *     marginVertical: 20,
 *   },
 * });
 * ```
 */

/**
 * App Unlock/Resume Integration:
 * 
 * ```tsx
 * import React, { useState, useEffect } from 'react';
 * import { AppState } from 'react-native';
 * import { QuickUnlockComponent } from './components/auth';
 * 
 * const AppContainer = () => {
 *   const [showUnlock, setShowUnlock] = useState(false);
 *   const [isAuthenticated, setIsAuthenticated] = useState(false);
 * 
 *   useEffect(() => {
 *     const handleAppStateChange = (nextAppState: string) => {
 *       if (nextAppState === 'active' && !isAuthenticated) {
 *         setShowUnlock(true);
 *       }
 *     };
 * 
 *     const subscription = AppState.addEventListener('change', handleAppStateChange);
 *     return () => subscription?.remove();
 *   }, [isAuthenticated]);
 * 
 *   const handleUnlockSuccess = () => {
 *     setIsAuthenticated(true);
 *     setShowUnlock(false);
 *   };
 * 
 *   const handleUnlockFallback = () => {
 *     // Show PIN/password screen
 *     setShowUnlock(false);
 *   };
 * 
 *   return (
 *     <>
 *       {/* Your app content *\/}
 *       
 *       <QuickUnlockComponent
 *         userId="user123"
 *         visible={showUnlock}
 *         onSuccess={handleUnlockSuccess}
 *         onFallback={handleUnlockFallback}
 *         autoTrigger={true}
 *         title="Welcome Back"
 *         subtitle="Unlock to continue using the app"
 *       />
 *     </>
 *   );
 * };
 * ```
 */

/**
 * Biometric Setup Integration:
 * 
 * ```tsx
 * import React, { useState } from 'react';
 * import { View, Text } from 'react-native';
 * import { BiometricLoginComponent, BiometricTypeIcon } from './components/auth';
 * import { useBiometric } from '../hooks/useBiometric';
 * 
 * const BiometricSetupScreen = () => {
 *   const { capabilities, shouldSetup } = useBiometric();
 * 
 *   const handleSetupSuccess = () => {
 *     console.log('Biometric setup successful');
 *     // Navigate to next screen or show success message
 *   };
 * 
 *   const handleSkip = () => {
 *     console.log('User skipped biometric setup');
 *     // Continue without biometric setup
 *   };
 * 
 *   if (!shouldSetup) {
 *     return null; // Don't show if not needed
 *   }
 * 
 *   return (
 *     <View style={{ flex: 1, justifyContent: 'center', padding: 20 }}>
 *       <BiometricTypeIcon
 *         biometryType={capabilities?.biometryType || null}
 *         size="xlarge"
 *         state="idle"
 *         animated={true}
 *         showLabel={true}
 *         style={{ marginBottom: 32 }}
 *       />
 * 
 *       <Text style={{ fontSize: 24, fontWeight: 'bold', textAlign: 'center', marginBottom: 16 }}>
 *         Secure Your Account
 *       </Text>
 * 
 *       <BiometricLoginComponent
 *         userId="user123"
 *         onSuccess={handleSetupSuccess}
 *         onFallback={handleSkip}
 *         mode="setup"
 *         customPromptTitle="Set up biometric authentication"
 *       />
 *     </View>
 *   );
 * };
 * ```
 */

/**
 * Advanced Fallback Management:
 * 
 * ```tsx
 * import React, { useState } from 'react';
 * import { 
 *   BiometricLoginButton,
 *   BiometricFallbackManager,
 *   AuthenticationMethod 
 * } from './components/auth';
 * 
 * const AdvancedLoginScreen = () => {
 *   const [showFallback, setShowFallback] = useState(false);
 * 
 *   const handleBiometricSuccess = () => {
 *     console.log('Biometric authentication successful');
 *   };
 * 
 *   const handleFallbackSuccess = (method: AuthenticationMethod) => {
 *     console.log('Fallback authentication successful:', method);
 *     setShowFallback(false);
 *   };
 * 
 *   return (
 *     <>
 *       <BiometricLoginButton
 *         userId="user123"
 *         onSuccess={handleBiometricSuccess}
 *         onFallback={() => setShowFallback(true)}
 *         variant="primary"
 *       />
 * 
 *       <BiometricFallbackManager
 *         userId="user123"
 *         visible={showFallback}
 *         onSuccess={handleFallbackSuccess}
 *         onCancel={() => setShowFallback(false)}
 *         fallbackOptions={{
 *           allowPin: true,
 *           allowPassword: true,
 *           allowMFA: true,
 *           maxFallbackAttempts: 3,
 *           lockoutDuration: 300000,
 *         }}
 *         primaryMethod={AuthenticationMethod.PIN}
 *         allowBiometricRetry={true}
 *       />
 *     </>
 *   );
 * };
 * ```
 */

/**
 * Component Features Summary:
 * 
 * 1. BiometricLoginComponent:
 *    - Full-featured biometric authentication with setup prompts
 *    - Supports login, unlock, and setup modes
 *    - Comprehensive error handling and user guidance
 *    - Customizable UI and prompts
 * 
 * 2. BiometricLoginButton:
 *    - Lightweight button for simple biometric authentication
 *    - Multiple variants (primary, secondary, minimal)
 *    - Easy integration into existing login forms
 * 
 * 3. QuickUnlockComponent:
 *    - Specialized for app resumption/unlock scenarios
 *    - Auto-trigger capability
 *    - Timeout management
 *    - Background/foreground state handling
 * 
 * 4. BiometricErrorHandler:
 *    - Comprehensive error handling and user guidance
 *    - Contextual help and troubleshooting tips
 *    - Recovery action suggestions
 *    - Support contact integration
 * 
 * 5. BiometricFallbackManager:
 *    - Complete fallback authentication flow
 *    - Multiple authentication methods (PIN, Password, MFA)
 *    - Attempt limiting and lockout management
 *    - Seamless method switching
 * 
 * 6. BiometricTypeIcon:
 *    - Dynamic icons for different biometric types
 *    - State animations and visual feedback
 *    - Accessibility support
 *    - Size and color customization
 * 
 * All components are designed to work together seamlessly and integrate
 * with the existing biometric authentication infrastructure in the app.
 */