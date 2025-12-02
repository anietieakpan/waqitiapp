import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Switch,
  Alert,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Slider from '@react-native-community/slider';
import * as LocalAuthentication from 'expo-local-authentication';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * BiometricSettingsScreen
 *
 * Configure biometric authentication preferences
 *
 * Features:
 * - Device capability detection
 * - Biometric authentication toggle
 * - Use case configuration
 * - Fallback settings
 * - Security information
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

type BiometricType = 'face_id' | 'touch_id' | 'fingerprint' | 'face_unlock' | 'none';

interface BiometricSettings {
  enabled: boolean;
  supportedType: BiometricType;
  isEnrolled: boolean;
  useCases: {
    appLogin: boolean;
    paymentConfirmation: boolean;
    paymentThreshold: number;
    settingsChanges: boolean;
    viewSensitiveInfo: boolean;
  };
  fallback: {
    maxAttempts: number;
    lockoutEnabled: boolean;
  };
  lastSuccessfulAuth?: string;
}

const BiometricSettingsScreen: React.FC = () => {
  const navigation = useNavigation();
  const { user } = useSelector((state: RootState) => state.auth);

  const [settings, setSettings] = useState<BiometricSettings>({
    enabled: false,
    supportedType: 'none',
    isEnrolled: false,
    useCases: {
      appLogin: true,
      paymentConfirmation: true,
      paymentThreshold: 100,
      settingsChanges: true,
      viewSensitiveInfo: true,
    },
    fallback: {
      maxAttempts: 3,
      lockoutEnabled: true,
    },
  });

  const [hasChanges, setHasChanges] = useState(false);
  const [saving, setSaving] = useState(false);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    AnalyticsService.trackScreenView('BiometricSettingsScreen');
    checkBiometricSupport();
  }, []);

  const checkBiometricSupport = async () => {
    try {
      // Check if hardware supports biometrics
      const hasHardware = await LocalAuthentication.hasHardwareAsync();

      if (!hasHardware) {
        setSettings((prev) => ({
          ...prev,
          supportedType: 'none',
          isEnrolled: false,
        }));
        setChecking(false);
        return;
      }

      // Check if biometrics are enrolled
      const isEnrolled = await LocalAuthentication.isEnrolledAsync();

      // Get supported authentication types
      const types = await LocalAuthentication.supportedAuthenticationTypesAsync();

      let biometricType: BiometricType = 'none';

      if (types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) {
        biometricType = Platform.OS === 'ios' ? 'face_id' : 'face_unlock';
      } else if (types.includes(LocalAuthentication.AuthenticationType.FINGERPRINT)) {
        biometricType = Platform.OS === 'ios' ? 'touch_id' : 'fingerprint';
      }

      setSettings((prev) => ({
        ...prev,
        supportedType: biometricType,
        isEnrolled,
      }));
    } catch (error) {
      console.error('Error checking biometric support:', error);
    } finally {
      setChecking(false);
    }
  };

  const getBiometricDisplayName = (): string => {
    switch (settings.supportedType) {
      case 'face_id':
        return 'Face ID';
      case 'touch_id':
        return 'Touch ID';
      case 'fingerprint':
        return 'Fingerprint';
      case 'face_unlock':
        return 'Face Unlock';
      default:
        return 'Biometric Authentication';
    }
  };

  const handleToggleBiometric = async (enabled: boolean) => {
    if (enabled && !settings.isEnrolled) {
      Alert.alert(
        'Biometric Not Set Up',
        `Please set up ${getBiometricDisplayName()} in your device settings first.`,
        [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Open Settings', onPress: () => {
            // TODO: Deep link to device settings
          }},
        ]
      );
      return;
    }

    if (enabled) {
      // Test biometric authentication before enabling
      try {
        const result = await LocalAuthentication.authenticateAsync({
          promptMessage: `Enable ${getBiometricDisplayName()}`,
          fallbackLabel: 'Use PIN',
        });

        if (result.success) {
          updateSetting('enabled', true);
          AnalyticsService.trackEvent('biometric_enabled', {
            type: settings.supportedType,
          });
        }
      } catch (error) {
        Alert.alert('Error', 'Failed to authenticate');
      }
    } else {
      // Require authentication to disable
      Alert.alert(
        'Disable Biometric',
        'Enter your PIN to disable biometric authentication',
        [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Continue', onPress: () => {
            // TODO: Show PIN entry
            updateSetting('enabled', false);
            AnalyticsService.trackEvent('biometric_disabled');
          }},
        ]
      );
    }
  };

  const updateSetting = (key: string, value: any) => {
    setSettings((prev) => {
      const keys = key.split('.');
      if (keys.length === 1) {
        return { ...prev, [key]: value };
      } else {
        return {
          ...prev,
          [keys[0]]: {
            ...(prev as any)[keys[0]],
            [keys[1]]: value,
          },
        };
      }
    });
    setHasChanges(true);
  };

  const handleSave = async () => {
    setSaving(true);

    try {
      // TODO: Save to API
      AnalyticsService.trackEvent('biometric_settings_updated');
      Alert.alert('Success', 'Settings saved successfully');
      setHasChanges(false);
    } catch (error) {
      Alert.alert('Error', 'Failed to save settings');
    } finally {
      setSaving(false);
    }
  };

  const renderDeviceStatus = () => (
    <View style={styles.section}>
      <View style={styles.statusCard}>
        <View style={[
          styles.statusIcon,
          { backgroundColor: settings.isEnrolled ? '#E8F5E9' : '#FFEBEE' }
        ]}>
          <Icon
            name={settings.isEnrolled ? 'check-circle' : 'alert-circle'}
            size={40}
            color={settings.isEnrolled ? '#4CAF50' : '#F44336'}
          />
        </View>
        <Text style={styles.statusTitle}>
          {settings.supportedType === 'none'
            ? 'Not Supported'
            : settings.isEnrolled
            ? `${getBiometricDisplayName()} Available`
            : `${getBiometricDisplayName()} Not Set Up`}
        </Text>
        <Text style={styles.statusDescription}>
          {settings.supportedType === 'none'
            ? 'Your device does not support biometric authentication'
            : settings.isEnrolled
            ? `Use ${getBiometricDisplayName()} to secure your account`
            : `Set up ${getBiometricDisplayName()} in device settings to use this feature`}
        </Text>
        {!settings.isEnrolled && settings.supportedType !== 'none' && (
          <TouchableOpacity style={styles.setupButton}>
            <Text style={styles.setupButtonText}>Open Device Settings</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );

  const renderMasterToggle = () => {
    if (settings.supportedType === 'none' || !settings.isEnrolled) {
      return null;
    }

    return (
      <View style={styles.section}>
        <View style={styles.settingRow}>
          <View style={styles.settingLeft}>
            <Icon name="fingerprint" size={24} color="#666" />
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>
                Enable {getBiometricDisplayName()}
              </Text>
              <Text style={styles.settingDescription}>
                Use {getBiometricDisplayName()} for authentication
              </Text>
            </View>
          </View>
          <Switch
            value={settings.enabled}
            onValueChange={handleToggleBiometric}
            trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
            thumbColor={settings.enabled ? '#6200EE' : '#FFFFFF'}
          />
        </View>
      </View>
    );
  };

  const renderUseCases = () => {
    if (!settings.enabled) return null;

    return (
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Use For</Text>

        <View style={styles.settingRow}>
          <View style={styles.settingLeft}>
            <Icon name="login" size={24} color="#666" />
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>App Login</Text>
              <Text style={styles.settingDescription}>
                Use instead of PIN/password
              </Text>
            </View>
          </View>
          <Switch
            value={settings.useCases.appLogin}
            onValueChange={(v) => updateSetting('useCases.appLogin', v)}
            trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
            thumbColor={settings.useCases.appLogin ? '#6200EE' : '#FFFFFF'}
          />
        </View>

        <View style={styles.settingRow}>
          <View style={styles.settingLeft}>
            <Icon name="cash" size={24} color="#666" />
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>Payment Confirmation</Text>
              <Text style={styles.settingDescription}>
                Confirm payments above ${settings.useCases.paymentThreshold}
              </Text>
            </View>
          </View>
          <Switch
            value={settings.useCases.paymentConfirmation}
            onValueChange={(v) => updateSetting('useCases.paymentConfirmation', v)}
            trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
            thumbColor={settings.useCases.paymentConfirmation ? '#6200EE' : '#FFFFFF'}
          />
        </View>

        {settings.useCases.paymentConfirmation && (
          <View style={styles.thresholdCard}>
            <View style={styles.thresholdHeader}>
              <Text style={styles.thresholdLabel}>Payment Threshold</Text>
              <Text style={styles.thresholdValue}>
                ${settings.useCases.paymentThreshold}
              </Text>
            </View>
            <Slider
              style={styles.slider}
              minimumValue={0}
              maximumValue={1000}
              step={10}
              value={settings.useCases.paymentThreshold}
              onValueChange={(v) => updateSetting('useCases.paymentThreshold', v)}
              minimumTrackTintColor="#6200EE"
              maximumTrackTintColor="#E0E0E0"
              thumbTintColor="#6200EE"
            />
          </View>
        )}

        <View style={styles.settingRow}>
          <View style={styles.settingLeft}>
            <Icon name="cog" size={24} color="#666" />
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>Settings Changes</Text>
              <Text style={styles.settingDescription}>
                Security-related settings
              </Text>
            </View>
          </View>
          <Switch
            value={settings.useCases.settingsChanges}
            onValueChange={(v) => updateSetting('useCases.settingsChanges', v)}
            trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
            thumbColor={settings.useCases.settingsChanges ? '#6200EE' : '#FFFFFF'}
          />
        </View>

        <View style={styles.settingRow}>
          <View style={styles.settingLeft}>
            <Icon name="eye-off" size={24} color="#666" />
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>View Sensitive Info</Text>
              <Text style={styles.settingDescription}>
                Card numbers, account details
              </Text>
            </View>
          </View>
          <Switch
            value={settings.useCases.viewSensitiveInfo}
            onValueChange={(v) => updateSetting('useCases.viewSensitiveInfo', v)}
            trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
            thumbColor={settings.useCases.viewSensitiveInfo ? '#6200EE' : '#FFFFFF'}
          />
        </View>
      </View>
    );
  };

  const renderFallbackSettings = () => {
    if (!settings.enabled) return null;

    return (
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Fallback & Security</Text>

        <View style={styles.infoCard}>
          <Icon name="information" size={20} color="#6200EE" />
          <Text style={styles.infoText}>
            You can always use your PIN if biometric authentication fails
          </Text>
        </View>

        <View style={styles.fallbackCard}>
          <Text style={styles.fallbackLabel}>Maximum Attempts</Text>
          <Text style={styles.fallbackDescription}>
            Number of failed biometric attempts before requiring PIN
          </Text>
          <View style={styles.attemptButtons}>
            {[3, 5, 10].map((num) => (
              <TouchableOpacity
                key={num}
                style={[
                  styles.attemptButton,
                  settings.fallback.maxAttempts === num && styles.attemptButtonActive,
                ]}
                onPress={() => updateSetting('fallback.maxAttempts', num)}
              >
                <Text
                  style={[
                    styles.attemptButtonText,
                    settings.fallback.maxAttempts === num && styles.attemptButtonTextActive,
                  ]}
                >
                  {num}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.settingRow}>
          <View style={styles.settingLeft}>
            <Icon name="lock" size={24} color="#666" />
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>Lockout After Failures</Text>
              <Text style={styles.settingDescription}>
                Temporarily disable after max attempts
              </Text>
            </View>
          </View>
          <Switch
            value={settings.fallback.lockoutEnabled}
            onValueChange={(v) => updateSetting('fallback.lockoutEnabled', v)}
            trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
            thumbColor={settings.fallback.lockoutEnabled ? '#6200EE' : '#FFFFFF'}
          />
        </View>
      </View>
    );
  };

  const renderSecurityInfo = () => (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Security Information</Text>

      <View style={styles.securityCard}>
        <Icon name="shield-check" size={24} color="#4CAF50" />
        <Text style={styles.securityText}>
          Your biometric data never leaves your device and is stored securely in the{' '}
          {Platform.OS === 'ios' ? 'Secure Enclave' : 'Trusted Execution Environment'}.
        </Text>
      </View>

      <View style={styles.tipsCard}>
        <Text style={styles.tipsTitle}>Best Practices</Text>
        <View style={styles.tipItem}>
          <Icon name="check" size={16} color="#4CAF50" />
          <Text style={styles.tipText}>Don't share device access with others</Text>
        </View>
        <View style={styles.tipItem}>
          <Icon name="check" size={16} color="#4CAF50" />
          <Text style={styles.tipText}>Keep your device OS updated</Text>
        </View>
        <View style={styles.tipItem}>
          <Icon name="check" size={16} color="#4CAF50" />
          <Text style={styles.tipText}>Report suspicious authentication prompts</Text>
        </View>
      </View>
    </View>
  );

  if (checking) {
    return (
      <View style={styles.container}>
        <Header title="Biometric Settings" showBack />
        <View style={styles.loadingContainer}>
          <Text style={styles.loadingText}>Checking device capabilities...</Text>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Header title="Biometric Settings" showBack />

      <ScrollView style={styles.content}>
        {renderDeviceStatus()}
        {renderMasterToggle()}
        {renderUseCases()}
        {renderFallbackSettings()}
        {renderSecurityInfo()}
      </ScrollView>

      {hasChanges && (
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.saveButton, saving && styles.saveButtonDisabled]}
            onPress={handleSave}
            disabled={saving}
          >
            <Text style={styles.saveButtonText}>
              {saving ? 'Saving...' : 'Save Changes'}
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  content: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    fontSize: 16,
    color: '#666',
  },
  section: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 20,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 16,
  },
  statusCard: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  statusIcon: {
    width: 80,
    height: 80,
    borderRadius: 40,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  statusTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 8,
  },
  statusDescription: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 16,
  },
  setupButton: {
    backgroundColor: '#6200EE',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
  },
  setupButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  settingLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  settingInfo: {
    marginLeft: 12,
    flex: 1,
  },
  settingLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
  },
  settingDescription: {
    fontSize: 13,
    color: '#666',
    marginTop: 2,
  },
  thresholdCard: {
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    padding: 16,
    marginTop: 8,
    marginBottom: 12,
  },
  thresholdHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  thresholdLabel: {
    fontSize: 14,
    color: '#666',
  },
  thresholdValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#6200EE',
  },
  slider: {
    width: '100%',
    height: 40,
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8EAF6',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
  fallbackCard: {
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
  },
  fallbackLabel: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  fallbackDescription: {
    fontSize: 13,
    color: '#666',
    marginBottom: 16,
  },
  attemptButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  attemptButton: {
    flex: 1,
    paddingVertical: 12,
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    marginHorizontal: 4,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#E0E0E0',
  },
  attemptButtonActive: {
    backgroundColor: '#6200EE',
    borderColor: '#6200EE',
  },
  attemptButtonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#666',
  },
  attemptButtonTextActive: {
    color: '#FFFFFF',
  },
  securityCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8F5E9',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  securityText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
  tipsCard: {
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    padding: 16,
  },
  tipsTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 12,
  },
  tipItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  tipText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 8,
  },
  footer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  saveButton: {
    backgroundColor: '#6200EE',
    paddingVertical: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  saveButtonDisabled: {
    backgroundColor: '#BDBDBD',
  },
  saveButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
});

export default BiometricSettingsScreen;
