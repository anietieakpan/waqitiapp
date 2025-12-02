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
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { notificationService } from '../../services/notificationService';
import { requestNotificationPermissions, openAppSettings } from '../../utils/permissions';

interface NotificationSettings {
  pushNotifications: boolean;
  emailNotifications: boolean;
  smsNotifications: boolean;
  paymentReceived: boolean;
  paymentSent: boolean;
  paymentRequests: boolean;
  securityAlerts: boolean;
  promotionalOffers: boolean;
  weeklyStatements: boolean;
  lowBalance: boolean;
  largeTransactions: boolean;
  loginAlerts: boolean;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  doNotDisturbEnabled: boolean;
  doNotDisturbStart: string;
  doNotDisturbEnd: string;
  frequency: 'immediate' | 'hourly' | 'daily';
}

const NotificationSettingsScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [settings, setSettings] = useState<NotificationSettings>({
    pushNotifications: true,
    emailNotifications: true,
    smsNotifications: false,
    paymentReceived: true,
    paymentSent: true,
    paymentRequests: true,
    securityAlerts: true,
    promotionalOffers: false,
    weeklyStatements: true,
    lowBalance: true,
    largeTransactions: true,
    loginAlerts: true,
    soundEnabled: true,
    vibrationEnabled: true,
    doNotDisturbEnabled: false,
    doNotDisturbStart: '22:00',
    doNotDisturbEnd: '08:00',
    frequency: 'immediate',
  });

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      setLoading(true);
      const response = await notificationService.getNotificationSettings();
      setSettings(response.settings);
    } catch (error) {
      console.error('Failed to load notification settings:', error);
      Alert.alert('Error', 'Failed to load notification settings');
    } finally {
      setLoading(false);
    }
  };

  const updateSetting = async (key: keyof NotificationSettings, value: any) => {
    // Handle push notification permission request
    if (key === 'pushNotifications' && value === true) {
      const hasPermission = await requestNotificationPermissions();
      if (!hasPermission) {
        Alert.alert(
          'Permission Required',
          'Please enable notifications in your device settings to receive push notifications.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Open Settings', onPress: openAppSettings },
          ]
        );
        return;
      }
    }

    try {
      const updatedSettings = { ...settings, [key]: value };
      setSettings(updatedSettings);
      await notificationService.updateNotificationSettings({ [key]: value });
    } catch (error) {
      console.error('Failed to update setting:', error);
      // Revert the change
      setSettings(settings);
      Alert.alert('Error', 'Failed to update notification settings');
    }
  };

  const testNotification = async () => {
    try {
      await notificationService.sendTestNotification();
      Alert.alert('Test Notification', 'A test notification has been sent!');
    } catch (error) {
      console.error('Failed to send test notification:', error);
      Alert.alert('Error', 'Failed to send test notification');
    }
  };

  const renderSettingItem = (
    title: string,
    subtitle: string,
    value: boolean,
    onValueChange: (value: boolean) => void,
    icon: string,
    disabled: boolean = false
  ) => (
    <View style={[styles.settingItem, { backgroundColor: theme.colors.surface }]}>
      <View style={styles.settingContent}>
        <View style={[styles.settingIcon, { backgroundColor: theme.colors.primary + '20' }]}>
          <Icon name={icon} size={24} color={theme.colors.primary} />
        </View>
        <View style={styles.settingText}>
          <Text style={[styles.settingTitle, { color: theme.colors.text }]}>
            {title}
          </Text>
          <Text style={[styles.settingSubtitle, { color: theme.colors.textSecondary }]}>
            {subtitle}
          </Text>
        </View>
      </View>
      <Switch
        value={value}
        onValueChange={onValueChange}
        trackColor={{
          false: theme.colors.border,
          true: theme.colors.primary + '40',
        }}
        thumbColor={value ? theme.colors.primary : '#FFFFFF'}
        disabled={disabled}
      />
    </View>
  );

  const renderSectionHeader = (title: string, icon: string) => (
    <View style={styles.sectionHeader}>
      <View style={styles.sectionHeaderContent}>
        <Icon name={icon} size={20} color={theme.colors.primary} />
        <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
          {title}
        </Text>
      </View>
    </View>
  );

  const renderFrequencySelector = () => (
    <View style={[styles.settingItem, { backgroundColor: theme.colors.surface }]}>
      <View style={styles.settingContent}>
        <View style={[styles.settingIcon, { backgroundColor: theme.colors.primary + '20' }]}>
          <Icon name="schedule" size={24} color={theme.colors.primary} />
        </View>
        <View style={styles.settingText}>
          <Text style={[styles.settingTitle, { color: theme.colors.text }]}>
            Notification Frequency
          </Text>
          <Text style={[styles.settingSubtitle, { color: theme.colors.textSecondary }]}>
            How often to receive notifications
          </Text>
        </View>
      </View>
      <View style={styles.frequencyOptions}>
        {[
          { key: 'immediate', label: 'Immediate' },
          { key: 'hourly', label: 'Hourly' },
          { key: 'daily', label: 'Daily' },
        ].map(option => (
          <TouchableOpacity
            key={option.key}
            style={[
              styles.frequencyOption,
              {
                backgroundColor: settings.frequency === option.key
                  ? theme.colors.primary
                  : theme.colors.border,
              },
            ]}
            onPress={() => updateSetting('frequency', option.key)}
          >
            <Text
              style={[
                styles.frequencyOptionText,
                {
                  color: settings.frequency === option.key
                    ? '#FFFFFF'
                    : theme.colors.text,
                },
              ]}
            >
              {option.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );

  const renderDoNotDisturbSettings = () => (
    <View style={[styles.settingItem, { backgroundColor: theme.colors.surface }]}>
      <View style={styles.settingContent}>
        <View style={[styles.settingIcon, { backgroundColor: theme.colors.primary + '20' }]}>
          <Icon name="do-not-disturb" size={24} color={theme.colors.primary} />
        </View>
        <View style={styles.settingText}>
          <Text style={[styles.settingTitle, { color: theme.colors.text }]}>
            Do Not Disturb
          </Text>
          <Text style={[styles.settingSubtitle, { color: theme.colors.textSecondary }]}>
            Quiet hours: {settings.doNotDisturbStart} - {settings.doNotDisturbEnd}
          </Text>
        </View>
      </View>
      <Switch
        value={settings.doNotDisturbEnabled}
        onValueChange={(value) => updateSetting('doNotDisturbEnabled', value)}
        trackColor={{
          false: theme.colors.border,
          true: theme.colors.primary + '40',
        }}
        thumbColor={settings.doNotDisturbEnabled ? theme.colors.primary : '#FFFFFF'}
      />
    </View>
  );

  if (loading) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <View style={styles.loadingContainer}>
          <Text style={[styles.loadingText, { color: theme.colors.text }]}>
            Loading settings...
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Icon name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <Text style={[styles.headerTitle, { color: theme.colors.text }]}>
          Notification Settings
        </Text>
        
        <TouchableOpacity
          onPress={testNotification}
          style={styles.testButton}
        >
          <Text style={[styles.testButtonText, { color: theme.colors.primary }]}>
            Test
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {/* Delivery Methods */}
        {renderSectionHeader('Delivery Methods', 'send')}
        
        {renderSettingItem(
          'Push Notifications',
          'Receive notifications on this device',
          settings.pushNotifications,
          (value) => updateSetting('pushNotifications', value),
          'notifications'
        )}
        
        {renderSettingItem(
          'Email Notifications',
          'Receive notifications via email',
          settings.emailNotifications,
          (value) => updateSetting('emailNotifications', value),
          'email'
        )}
        
        {renderSettingItem(
          'SMS Notifications',
          'Receive notifications via text message',
          settings.smsNotifications,
          (value) => updateSetting('smsNotifications', value),
          'sms'
        )}

        {/* Payment Notifications */}
        {renderSectionHeader('Payment Notifications', 'payment')}
        
        {renderSettingItem(
          'Payment Received',
          'When you receive money',
          settings.paymentReceived,
          (value) => updateSetting('paymentReceived', value),
          'arrow-downward'
        )}
        
        {renderSettingItem(
          'Payment Sent',
          'When you send money',
          settings.paymentSent,
          (value) => updateSetting('paymentSent', value),
          'arrow-upward'
        )}
        
        {renderSettingItem(
          'Payment Requests',
          'When someone requests money from you',
          settings.paymentRequests,
          (value) => updateSetting('paymentRequests', value),
          'request-page'
        )}

        {/* Security Notifications */}
        {renderSectionHeader('Security & Account', 'security')}
        
        {renderSettingItem(
          'Security Alerts',
          'Login attempts and security events',
          settings.securityAlerts,
          (value) => updateSetting('securityAlerts', value),
          'security',
          true // Always enabled for security
        )}
        
        {renderSettingItem(
          'Login Alerts',
          'New device or location logins',
          settings.loginAlerts,
          (value) => updateSetting('loginAlerts', value),
          'login',
          true // Always enabled for security
        )}
        
        {renderSettingItem(
          'Large Transactions',
          'Transactions above your set limit',
          settings.largeTransactions,
          (value) => updateSetting('largeTransactions', value),
          'trending-up'
        )}
        
        {renderSettingItem(
          'Low Balance',
          'When your balance falls below $50',
          settings.lowBalance,
          (value) => updateSetting('lowBalance', value),
          'trending-down'
        )}

        {/* Marketing & Updates */}
        {renderSectionHeader('Marketing & Updates', 'campaign')}
        
        {renderSettingItem(
          'Promotional Offers',
          'Special offers and promotions',
          settings.promotionalOffers,
          (value) => updateSetting('promotionalOffers', value),
          'local-offer'
        )}
        
        {renderSettingItem(
          'Weekly Statements',
          'Weekly activity summaries',
          settings.weeklyStatements,
          (value) => updateSetting('weeklyStatements', value),
          'assessment'
        )}

        {/* Notification Settings */}
        {renderSectionHeader('Notification Behavior', 'tune')}
        
        {Platform.OS === 'ios' && renderSettingItem(
          'Sound',
          'Play sound for notifications',
          settings.soundEnabled,
          (value) => updateSetting('soundEnabled', value),
          'volume-up'
        )}
        
        {Platform.OS === 'android' && renderSettingItem(
          'Vibration',
          'Vibrate for notifications',
          settings.vibrationEnabled,
          (value) => updateSetting('vibrationEnabled', value),
          'vibration'
        )}

        {renderFrequencySelector()}
        {renderDoNotDisturbSettings()}

        <View style={styles.footer}>
          <Text style={[styles.footerText, { color: theme.colors.textSecondary }]}>
            Security notifications cannot be disabled for your protection.
            You can manage notification permissions in your device settings.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    fontSize: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 16,
  },
  testButton: {
    padding: 8,
  },
  testButtonText: {
    fontSize: 16,
    fontWeight: '500',
  },
  content: {
    flex: 1,
    paddingHorizontal: 16,
  },
  sectionHeader: {
    marginTop: 24,
    marginBottom: 12,
  },
  sectionHeaderContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginLeft: 8,
  },
  settingItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  settingContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  settingIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  settingText: {
    flex: 1,
  },
  settingTitle: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 2,
  },
  settingSubtitle: {
    fontSize: 14,
    lineHeight: 18,
  },
  frequencyOptions: {
    flexDirection: 'row',
    gap: 8,
  },
  frequencyOption: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  frequencyOptionText: {
    fontSize: 12,
    fontWeight: '500',
  },
  footer: {
    marginTop: 32,
    marginBottom: 32,
    paddingHorizontal: 16,
  },
  footerText: {
    fontSize: 12,
    lineHeight: 18,
    textAlign: 'center',
  },
});

export default NotificationSettingsScreen;