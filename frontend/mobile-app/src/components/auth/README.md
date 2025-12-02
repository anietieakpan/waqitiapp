# Biometric Authentication Components

A comprehensive suite of React Native components for biometric authentication integration. These components provide a complete biometric authentication solution that seamlessly integrates with the existing `useBiometric` hook and `BiometricContext`.

## Components Overview

### 🔐 BiometricLoginComponent
Full-featured biometric authentication component with setup prompts, error handling, and user guidance.

**Features:**
- Login, unlock, and setup modes
- Automatic biometric type detection
- Setup prompts and recommendations
- Comprehensive error handling
- Fallback authentication support
- Debug mode for development

### 🔘 BiometricLoginButton
Lightweight biometric authentication button for easy integration into existing login screens.

**Features:**
- Multiple variants (primary, secondary, minimal)
- Auto-hide when biometric not available
- Built-in loading states
- Customizable styling

### ⚡ QuickUnlockComponent
Specialized component for app resumption and quick unlock scenarios.

**Features:**
- Auto-trigger on app foreground
- Recent authentication checking
- Timeout management
- Hardware back button handling
- Modal-based UI

### ❌ BiometricErrorHandler
Comprehensive error handling with user guidance and recovery suggestions.

**Features:**
- Contextual error messages
- Troubleshooting tips
- Recovery actions
- Security risk warnings
- Debug information

### 🔄 BiometricFallbackManager
Complete fallback authentication flow supporting multiple authentication methods.

**Features:**
- PIN, Password, and MFA support
- Attempt limiting and lockout
- Method switching
- Biometric retry option
- Visual feedback

### 🎨 BiometricTypeIcon
Dynamic icon component with animations and state management.

**Features:**
- Support for all biometric types
- State-based animations
- Accessibility support
- Size and color customization
- Specialized icon variants

## Installation & Setup

1. Ensure your app has the biometric service infrastructure set up:
   - `BiometricContext` is configured
   - `useBiometric` hook is available
   - Biometric services are initialized

2. Import the components you need:

```tsx
import {
  BiometricLoginComponent,
  BiometricLoginButton,
  QuickUnlockComponent,
  BiometricErrorHandler,
  BiometricFallbackManager,
  BiometricTypeIcon,
} from './components/auth';
```

## Usage Examples

### Basic Login Integration

```tsx
import React from 'react';
import { View, StyleSheet } from 'react-native';
import { BiometricLoginButton } from './components/auth';

const LoginScreen = () => {
  const handleSuccess = () => {
    console.log('Authentication successful');
    // Navigate to main app
  };

  const handleFallback = () => {
    console.log('Show traditional login');
    // Show PIN/password form
  };

  return (
    <View style={styles.container}>
      {/* Your existing login form */}
      
      <BiometricLoginButton
        userId="user123"
        onSuccess={handleSuccess}
        onFallback={handleFallback}
        variant="primary"
        style={styles.biometricButton}
      />
      
      {/* Alternative login methods */}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: 20,
  },
  biometricButton: {
    marginVertical: 16,
  },
});
```

### Comprehensive Login Screen

```tsx
import React, { useState } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { 
  BiometricLoginComponent,
  BiometricErrorHandler,
  BiometricAuthError 
} from './components/auth';

const CompleteLoginScreen = () => {
  const [showError, setShowError] = useState(false);
  const [error, setError] = useState<BiometricAuthError | null>(null);
  const [authMethod, setAuthMethod] = useState<string | null>(null);

  const handleSuccess = (result: { method: string; timestamp: number }) => {
    setAuthMethod(result.method);
    console.log('Authentication successful:', result);
    // Navigate to main app
  };

  const handleError = (errorMessage: string) => {
    setError(BiometricAuthError.AUTHENTICATION_FAILED);
    setShowError(true);
  };

  const handleFallback = () => {
    // Show traditional login form
    setAuthMethod('fallback');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Welcome Back</Text>
      <Text style={styles.subtitle}>Sign in to continue</Text>

      <BiometricLoginComponent
        userId="user123"
        onSuccess={handleSuccess}
        onFallback={handleFallback}
        onError={handleError}
        mode="login"
        showSetupPrompt={true}
        customPromptTitle="Sign in securely"
        style={styles.biometricLogin}
      />

      <BiometricErrorHandler
        visible={showError}
        error={error}
        onDismiss={() => setShowError(false)}
        onRetry={() => {
          setShowError(false);
          setError(null);
        }}
        onFallback={handleFallback}
        userId="user123"
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: 20,
    backgroundColor: '#F8F9FA',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 8,
    color: '#1C1C1E',
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 32,
    color: '#666666',
  },
  biometricLogin: {
    marginVertical: 20,
  },
});
```

### App Unlock Integration

```tsx
import React, { useState, useEffect } from 'react';
import { AppState, AppStateStatus } from 'react-native';
import { QuickUnlockComponent } from './components/auth';

const AppContainer = ({ children }: { children: React.ReactNode }) => {
  const [showUnlock, setShowUnlock] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [lastActiveTime, setLastActiveTime] = useState(Date.now());

  const UNLOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes

  useEffect(() => {
    const handleAppStateChange = (nextAppState: AppStateStatus) => {
      if (nextAppState === 'active') {
        const now = Date.now();
        const timeSinceBackground = now - lastActiveTime;
        
        if (timeSinceBackground > UNLOCK_TIMEOUT && isAuthenticated) {
          setShowUnlock(true);
          setIsAuthenticated(false);
        }
      } else if (nextAppState === 'background') {
        setLastActiveTime(Date.now());
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => subscription?.remove();
  }, [lastActiveTime, isAuthenticated]);

  const handleUnlockSuccess = () => {
    setIsAuthenticated(true);
    setShowUnlock(false);
    setLastActiveTime(Date.now());
  };

  const handleUnlockFallback = () => {
    setShowUnlock(false);
    // Show full login screen or PIN entry
  };

  const handleUnlockCancel = () => {
    setShowUnlock(false);
    // Optionally exit app or show limited functionality
  };

  return (
    <>
      {children}
      
      <QuickUnlockComponent
        userId="user123"
        visible={showUnlock}
        onSuccess={handleUnlockSuccess}
        onFallback={handleUnlockFallback}
        onCancel={handleUnlockCancel}
        autoTrigger={true}
        timeoutMs={30000}
        allowCancel={true}
        title="Welcome Back"
        subtitle="Use biometric authentication to unlock"
      />
    </>
  );
};
```

### Biometric Setup Flow

```tsx
import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert } from 'react-native';
import { 
  BiometricLoginComponent, 
  BiometricTypeIcon,
  BiometricOptions 
} from './components/auth';
import { useBiometric } from '../hooks/useBiometric';

const BiometricSetupScreen = () => {
  const { 
    capabilities, 
    shouldSetup, 
    getSetupRecommendation 
  } = useBiometric();
  
  const [setupComplete, setSetupComplete] = useState(false);

  const handleSetupSuccess = () => {
    setSetupComplete(true);
    Alert.alert(
      'Setup Complete!',
      'Biometric authentication has been enabled for your account.',
      [{ text: 'Continue', onPress: () => navigateToNextScreen() }]
    );
  };

  const handleSkip = () => {
    Alert.alert(
      'Skip Setup?',
      'You can enable biometric authentication later in settings.',
      [
        { text: 'Skip', onPress: () => navigateToNextScreen() },
        { text: 'Set Up Now', style: 'cancel' },
      ]
    );
  };

  const navigateToNextScreen = () => {
    // Navigate to main app or next onboarding step
  };

  if (!shouldSetup) {
    return null;
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <BiometricTypeIcon
          biometryType={capabilities?.biometryType || null}
          size="xlarge"
          state="idle"
          animated={true}
          showLabel={false}
          style={styles.mainIcon}
        />
        
        <Text style={styles.title}>Secure Your Account</Text>
        <Text style={styles.description}>
          {getSetupRecommendation()}
        </Text>
      </View>

      <View style={styles.content}>
        <BiometricLoginComponent
          userId="user123"
          onSuccess={handleSetupSuccess}
          onFallback={handleSkip}
          mode="setup"
          showSetupPrompt={false}
          customPromptTitle="Set up biometric authentication"
          style={styles.setupComponent}
        />
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          Your biometric data is stored securely on your device and never shared.
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    padding: 20,
  },
  header: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  mainIcon: {
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 16,
    color: '#1C1C1E',
  },
  description: {
    fontSize: 16,
    textAlign: 'center',
    lineHeight: 24,
    color: '#666666',
    paddingHorizontal: 20,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
  },
  setupComponent: {
    marginVertical: 20,
  },
  footer: {
    paddingVertical: 20,
  },
  footerText: {
    fontSize: 12,
    textAlign: 'center',
    color: '#999999',
    lineHeight: 18,
  },
});
```

### Advanced Fallback Management

```tsx
import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { 
  BiometricLoginButton,
  BiometricFallbackManager,
  AuthenticationMethod 
} from './components/auth';

const AdvancedAuthScreen = () => {
  const [showFallback, setShowFallback] = useState(false);
  const [fallbackReason, setFallbackReason] = useState('');

  const handleBiometricSuccess = () => {
    console.log('Biometric authentication successful');
    // Navigate to main app
  };

  const handleBiometricError = (error: string) => {
    setFallbackReason(`Biometric authentication failed: ${error}`);
    setShowFallback(true);
  };

  const handleFallbackSuccess = (method: AuthenticationMethod) => {
    console.log('Fallback authentication successful:', method);
    setShowFallback(false);
    // Navigate to main app
  };

  const handleFallbackError = (error: string) => {
    console.error('Fallback authentication error:', error);
    // Handle fallback errors
  };

  return (
    <View style={styles.container}>
      <BiometricLoginButton
        userId="user123"
        onSuccess={handleBiometricSuccess}
        onError={handleBiometricError}
        onFallback={() => {
          setFallbackReason('User chose alternative authentication');
          setShowFallback(true);
        }}
        variant="primary"
        style={styles.biometricButton}
      />

      <BiometricFallbackManager
        userId="user123"
        visible={showFallback}
        onSuccess={handleFallbackSuccess}
        onError={handleFallbackError}
        onCancel={() => setShowFallback(false)}
        fallbackOptions={{
          allowPin: true,
          allowPassword: true,
          allowMFA: true,
          maxFallbackAttempts: 3,
          lockoutDuration: 300000, // 5 minutes
        }}
        primaryMethod={AuthenticationMethod.PIN}
        reason={fallbackReason}
        title="Alternative Authentication"
        allowBiometricRetry={true}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: 20,
  },
  biometricButton: {
    marginVertical: 20,
  },
});
```

## Component Props Reference

### BiometricLoginComponent

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `userId` | `string` | - | User identifier for authentication |
| `onSuccess` | `function` | - | Called when authentication succeeds |
| `onFallback` | `function` | - | Called when fallback is needed |
| `onError` | `function` | - | Called when errors occur |
| `mode` | `'login' \| 'unlock' \| 'setup'` | `'login'` | Component mode |
| `showSetupPrompt` | `boolean` | `true` | Show setup prompts |
| `customPromptTitle` | `string` | - | Custom prompt title |
| `disabled` | `boolean` | `false` | Disable the component |
| `autoTrigger` | `boolean` | `false` | Auto-trigger authentication |
| `fallbackMethod` | `AuthenticationMethod` | `PIN` | Fallback method |
| `style` | `ViewStyle` | - | Custom styling |
| `compact` | `boolean` | `false` | Use compact mode |

### BiometricLoginButton

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `userId` | `string` | - | User identifier for authentication |
| `onSuccess` | `function` | - | Called when authentication succeeds |
| `onFallback` | `function` | - | Called when fallback is needed |
| `onError` | `function` | - | Called when errors occur |
| `style` | `ViewStyle` | - | Custom button styling |
| `textStyle` | `TextStyle` | - | Custom text styling |
| `disabled` | `boolean` | `false` | Disable the button |
| `variant` | `'primary' \| 'secondary' \| 'minimal'` | `'primary'` | Button variant |
| `showIcon` | `boolean` | `true` | Show biometric icon |
| `customText` | `string` | - | Custom button text |

### QuickUnlockComponent

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `userId` | `string` | - | User identifier for authentication |
| `visible` | `boolean` | - | Component visibility |
| `onSuccess` | `function` | - | Called when authentication succeeds |
| `onFallback` | `function` | - | Called when fallback is needed |
| `onCancel` | `function` | - | Called when user cancels |
| `autoTrigger` | `boolean` | `true` | Auto-trigger authentication |
| `fallbackMethod` | `AuthenticationMethod` | `PIN` | Fallback method |
| `timeoutMs` | `number` | `30000` | Timeout in milliseconds |
| `allowCancel` | `boolean` | `true` | Allow user to cancel |
| `title` | `string` | `'Unlock App'` | Modal title |
| `subtitle` | `string` | - | Modal subtitle |

## Best Practices

### Security Considerations

1. **User ID Validation**: Always validate user IDs before authentication
2. **Session Management**: Implement proper session timeouts
3. **Error Logging**: Log authentication events for security monitoring
4. **Fallback Security**: Ensure fallback methods are equally secure

### User Experience

1. **Clear Messaging**: Provide clear instructions and error messages
2. **Progressive Enhancement**: Gracefully handle devices without biometric support
3. **Accessibility**: Ensure components work with screen readers
4. **Visual Feedback**: Use animations and states to guide users

### Performance

1. **Lazy Loading**: Load components only when needed
2. **Memory Management**: Properly cleanup event listeners and timers
3. **Background Handling**: Handle app state changes appropriately

### Testing

1. **Device Testing**: Test on various devices with different biometric types
2. **Error Scenarios**: Test all error conditions and recovery flows
3. **Accessibility Testing**: Verify screen reader compatibility
4. **Performance Testing**: Monitor component performance impact

## Troubleshooting

### Common Issues

1. **Biometric Not Available**: Component doesn't show
   - Verify device has biometric hardware
   - Check if biometric is enrolled in device settings
   - Ensure app has biometric permissions

2. **Authentication Fails**: Biometric authentication consistently fails
   - Check device security settings
   - Verify biometric enrollment quality
   - Test with different fingers/face positions

3. **Fallback Not Working**: Fallback authentication fails
   - Verify fallback options configuration
   - Check network connectivity for server-side validation
   - Ensure proper error handling implementation

### Debug Mode

Enable debug mode in development to see detailed component state:

```tsx
// Components automatically show debug information in __DEV__ mode
// Look for "Show Details" toggle in development builds
```

## Migration Guide

If upgrading from a previous authentication system:

1. **Wrap Components**: Ensure components are within `BiometricProvider`
2. **Update Props**: Review and update component props
3. **Error Handling**: Update error handling to use new error types
4. **Styling**: Update custom styles to match new component structure

## Support

For issues or questions:

1. Check the troubleshooting section above
2. Review component prop types and requirements
3. Test on physical devices with biometric capabilities
4. Contact the development team for technical support

---

**Version**: 1.0.0  
**Last Updated**: 2025-07-12  
**Compatibility**: React Native 0.70+