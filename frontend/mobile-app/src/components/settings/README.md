# Settings Components

This directory contains reusable settings components for the Waqiti mobile application.

## BiometricSecuritySettings

A comprehensive security settings component that manages biometric authentication, device trust, and security assessments.

### Features

1. **Biometric Authentication Management**
   - Toggle biometric authentication on/off
   - Display current biometric type (TouchID/FaceID/Fingerprint)
   - Setup wizard for first-time configuration
   - Re-configure existing biometric settings

2. **Security Status Display**
   - Current security level (Low/Medium/High)
   - PIN protection status
   - Two-factor authentication status
   - Device trust status

3. **Security Assessment**
   - Device trust score (0-100)
   - Risk level assessment
   - Threat detection and display
   - Security recommendations

4. **Advanced Settings**
   - Require biometric for transactions
   - Auto-lock configuration
   - Screenshot permission control
   - Security event logging
   - Re-authentication intervals

5. **Device Trust Management**
   - View trusted device status
   - Navigate to device management
   - Security score visualization

### Usage

```tsx
import { BiometricSecuritySettings } from '@components/settings';

function SecurityScreen() {
  const navigation = useNavigation();

  return (
    <BiometricSecuritySettings
      onNavigateToSetup={() => navigation.navigate('BiometricSetup')}
      onNavigateToDeviceManagement={() => navigation.navigate('DeviceManagement')}
    />
  );
}
```

### Props

| Prop | Type | Description |
|------|------|-------------|
| `onNavigateToSetup` | `() => void` | Optional callback for navigating to biometric setup screen |
| `onNavigateToDeviceManagement` | `() => void` | Optional callback for navigating to device management |
| `style` | `ViewStyle` | Optional custom styles |

### Integration Requirements

The component relies on:

1. **Contexts**
   - `useBiometric` hook from biometric context
   - `useSecurity` hook from security context

2. **Services**
   - BiometricAuthService
   - SecurityService
   - DeviceFingerprintService

3. **Navigation**
   - Expects navigation handlers for setup and device management

### Security Features

- **PIN Verification**: Required when disabling biometric authentication
- **Device Trust**: Continuous assessment of device security
- **Threat Detection**: Real-time security threat monitoring
- **Audit Logging**: Optional security event tracking

### Accessibility

- Full screen reader support
- Clear labels for all interactive elements
- Status announcements for security changes
- Keyboard navigation support

### Styling

The component uses React Native Paper's Material Design 3 theme and adapts to light/dark modes automatically.

### Example Integration

```tsx
// In your navigation stack
<Stack.Screen 
  name="SecuritySettings" 
  component={SecuritySettingsScreen}
  options={{ title: 'Security Settings' }}
/>

// SecuritySettingsScreen.tsx
import React from 'react';
import { SafeAreaView } from 'react-native';
import { Appbar } from 'react-native-paper';
import { BiometricSecuritySettings } from '@components/settings';

export const SecuritySettingsScreen = () => {
  const navigation = useNavigation();

  return (
    <SafeAreaView style={{ flex: 1 }}>
      <Appbar.Header>
        <Appbar.BackAction onPress={() => navigation.goBack()} />
        <Appbar.Content title="Security Settings" />
      </Appbar.Header>
      
      <BiometricSecuritySettings
        onNavigateToSetup={() => navigation.navigate('BiometricSetup')}
        onNavigateToDeviceManagement={() => navigation.navigate('DeviceManagement')}
      />
    </SafeAreaView>
  );
};
```