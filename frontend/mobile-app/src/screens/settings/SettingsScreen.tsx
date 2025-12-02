import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Switch,
  Alert
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface Settings {
  notificationsEnabled: boolean;
  biometricEnabled: boolean;
  twoFactorEnabled: boolean;
  darkMode: boolean;
  currency: string;
  language: string;
}

const SettingsScreen = ({ navigation }) => {
  const [settings, setSettings] = useState<Settings>({
    notificationsEnabled: true,
    biometricEnabled: false,
    twoFactorEnabled: false,
    darkMode: false,
    currency: 'USD',
    language: 'English'
  });

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const storedSettings = await AsyncStorage.getItem('userSettings');
      if (storedSettings) {
        setSettings(JSON.parse(storedSettings));
      }
    } catch (error) {
      console.error('Failed to load settings:', error);
    }
  };

  const saveSettings = async (newSettings: Settings) => {
    try {
      await AsyncStorage.setItem('userSettings', JSON.stringify(newSettings));
      setSettings(newSettings);
    } catch (error) {
      Alert.alert('Error', 'Failed to save settings');
    }
  };

  const toggleSetting = (key: keyof Settings) => {
    const newSettings = {
      ...settings,
      [key]: !settings[key]
    };
    saveSettings(newSettings);
  };

  const handleLogout = () => {
    Alert.alert(
      'Logout',
      'Are you sure you want to logout?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Logout',
          style: 'destructive',
          onPress: async () => {
            await AsyncStorage.clear();
            navigation.reset({
              index: 0,
              routes: [{ name: 'Login' }]
            });
          }
        }
      ]
    );
  };

  const renderSettingRow = (
    label: string,
    value: boolean,
    onToggle: () => void,
    subtitle?: string
  ) => (
    <View style={styles.settingRow}>
      <View style={styles.settingInfo}>
        <Text style={styles.settingLabel}>{label}</Text>
        {subtitle && <Text style={styles.settingSubtitle}>{subtitle}</Text>}
      </View>
      <Switch
        value={value}
        onValueChange={onToggle}
        trackColor={{ false: '#DDD', true: '#007AFF' }}
      />
    </View>
  );

  const renderNavigationRow = (label: string, onPress: () => void, danger?: boolean) => (
    <TouchableOpacity style={styles.settingRow} onPress={onPress}>
      <Text style={[styles.settingLabel, danger && styles.dangerText]}>{label}</Text>
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
  );

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Settings</Text>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Notifications</Text>
        {renderSettingRow(
          'Push Notifications',
          settings.notificationsEnabled,
          () => toggleSetting('notificationsEnabled'),
          'Receive payment and activity alerts'
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Security</Text>
        {renderSettingRow(
          'Biometric Authentication',
          settings.biometricEnabled,
          () => toggleSetting('biometricEnabled'),
          'Use Face ID or Touch ID'
        )}
        {renderSettingRow(
          'Two-Factor Authentication',
          settings.twoFactorEnabled,
          () => toggleSetting('twoFactorEnabled'),
          'Extra security for your account'
        )}
        {renderNavigationRow('Change PIN', () => navigation.navigate('ChangePin'))}
        {renderNavigationRow('Change Password', () => navigation.navigate('ChangePassword'))}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Preferences</Text>
        {renderSettingRow(
          'Dark Mode',
          settings.darkMode,
          () => toggleSetting('darkMode'),
          'Use dark theme'
        )}
        {renderNavigationRow('Currency Settings', () => navigation.navigate('CurrencySettings'))}
        {renderNavigationRow('Language', () => navigation.navigate('LanguageSettings'))}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Account</Text>
        {renderNavigationRow('Profile Information', () => navigation.navigate('Profile'))}
        {renderNavigationRow('KYC Verification', () => navigation.navigate('KYC'))}
        {renderNavigationRow('Linked Accounts', () => navigation.navigate('LinkedAccounts'))}
        {renderNavigationRow('Transaction Limits', () => navigation.navigate('TransactionLimits'))}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Legal</Text>
        {renderNavigationRow('Terms of Service', () => navigation.navigate('Terms'))}
        {renderNavigationRow('Privacy Policy', () => navigation.navigate('Privacy'))}
        {renderNavigationRow('GDPR Data Request', () => navigation.navigate('GDPRRequest'))}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Support</Text>
        {renderNavigationRow('Help Center', () => navigation.navigate('Help'))}
        {renderNavigationRow('Contact Support', () => navigation.navigate('Support'))}
        {renderNavigationRow('Report a Problem', () => navigation.navigate('ReportProblem'))}
      </View>

      <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
        <Text style={styles.logoutText}>Logout</Text>
      </TouchableOpacity>

      <Text style={styles.version}>Version 1.0.0</Text>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#FFF'
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 24
  },
  section: {
    marginBottom: 24
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    textTransform: 'uppercase',
    marginBottom: 12
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0'
  },
  settingInfo: {
    flex: 1
  },
  settingLabel: {
    fontSize: 16,
    fontWeight: '500'
  },
  settingSubtitle: {
    fontSize: 12,
    color: '#999',
    marginTop: 4
  },
  chevron: {
    fontSize: 24,
    color: '#CCC'
  },
  dangerText: {
    color: '#FF3B30'
  },
  logoutButton: {
    backgroundColor: '#FF3B30',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 24
  },
  logoutText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600'
  },
  version: {
    textAlign: 'center',
    color: '#999',
    fontSize: 12,
    marginBottom: 32
  }
});

export default SettingsScreen;
