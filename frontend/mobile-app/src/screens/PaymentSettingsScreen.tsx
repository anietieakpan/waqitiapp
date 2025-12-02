import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Switch,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Slider from '@react-native-community/slider';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * PaymentSettingsScreen
 *
 * Configure payment preferences and limits
 *
 * Features:
 * - Default payment method selection
 * - Transaction limits configuration
 * - Payment confirmation settings
 * - Currency preferences
 * - Auto-accept settings
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface PaymentSettings {
  defaultPaymentMethod: string;
  dailyLimit: number;
  weeklyLimit: number;
  monthlyLimit: number;
  perTransactionLimit: number;
  autoAcceptRequests: boolean;
  confirmationThreshold: number;
  confirmationMethod: 'pin' | 'biometric' | 'both';
  defaultCurrency: string;
  enableAutoReceipts: boolean;
  showTransactionFees: boolean;
}

const PaymentSettingsScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  // Mock payment methods - TODO: Replace with Redux
  const paymentMethods = [
    { id: 'wallet', name: 'Waqiti Wallet', type: 'wallet', balance: 1250.50 },
    { id: 'card-123', name: 'Visa •••• 4242', type: 'card' },
    { id: 'bank-456', name: 'Chase Checking', type: 'bank' },
  ];

  // Settings state
  const [settings, setSettings] = useState<PaymentSettings>({
    defaultPaymentMethod: 'wallet',
    dailyLimit: 5000,
    weeklyLimit: 15000,
    monthlyLimit: 50000,
    perTransactionLimit: 10000,
    autoAcceptRequests: false,
    confirmationThreshold: 100,
    confirmationMethod: 'both',
    defaultCurrency: 'USD',
    enableAutoReceipts: true,
    showTransactionFees: true,
  });

  const [hasChanges, setHasChanges] = useState(false);
  const [saving, setSaving] = useState(false);

  // Current usage (mock data)
  const currentUsage = {
    daily: 1250,
    weekly: 4800,
    monthly: 18500,
  };

  useEffect(() => {
    AnalyticsService.trackScreenView('PaymentSettingsScreen');
    loadSettings();
  }, []);

  const loadSettings = async () => {
    // TODO: Load from API/Redux
  };

  const updateSetting = <K extends keyof PaymentSettings>(
    key: K,
    value: PaymentSettings[K]
  ) => {
    setSettings((prev) => ({ ...prev, [key]: value }));
    setHasChanges(true);
  };

  const handleSave = async () => {
    setSaving(true);

    try {
      // TODO: Save to API
      // await dispatch(updatePaymentSettings(settings)).unwrap();

      AnalyticsService.trackEvent('payment_settings_updated', {
        userId: user?.id,
        changes: Object.keys(settings),
      });

      Alert.alert('Success', 'Payment settings updated successfully');
      setHasChanges(false);
    } catch (error: any) {
      Alert.alert('Error', 'Failed to update settings');
    } finally {
      setSaving(false);
    }
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const getUsagePercentage = (current: number, limit: number): number => {
    return Math.min((current / limit) * 100, 100);
  };

  const getUsageColor = (percentage: number): string => {
    if (percentage >= 90) return '#F44336';
    if (percentage >= 70) return '#FF9800';
    return '#4CAF50';
  };

  const renderDefaultPaymentMethod = () => (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Default Payment Method</Text>
      <Text style={styles.sectionDescription}>
        This will be used for sending payments
      </Text>

      {paymentMethods.map((method) => (
        <TouchableOpacity
          key={method.id}
          style={[
            styles.paymentMethodCard,
            settings.defaultPaymentMethod === method.id &&
              styles.paymentMethodCardActive,
          ]}
          onPress={() => updateSetting('defaultPaymentMethod', method.id)}
        >
          <View style={styles.paymentMethodLeft}>
            <Icon
              name={
                method.type === 'wallet'
                  ? 'wallet'
                  : method.type === 'card'
                  ? 'credit-card'
                  : 'bank'
              }
              size={24}
              color="#6200EE"
            />
            <View style={styles.paymentMethodInfo}>
              <Text style={styles.paymentMethodName}>{method.name}</Text>
              {method.type === 'wallet' && (
                <Text style={styles.paymentMethodBalance}>
                  Balance: {formatCurrency(method.balance)}
                </Text>
              )}
            </View>
          </View>
          {settings.defaultPaymentMethod === method.id && (
            <Icon name="check-circle" size={24} color="#6200EE" />
          )}
        </TouchableOpacity>
      ))}
    </View>
  );

  const renderTransactionLimits = () => (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Transaction Limits</Text>
      <Text style={styles.sectionDescription}>
        Manage your spending limits for security
      </Text>

      {/* Daily Limit */}
      <View style={styles.limitCard}>
        <View style={styles.limitHeader}>
          <Text style={styles.limitLabel}>Daily Limit</Text>
          <Text style={styles.limitValue}>
            {formatCurrency(settings.dailyLimit)}
          </Text>
        </View>
        <Slider
          style={styles.slider}
          minimumValue={100}
          maximumValue={10000}
          step={100}
          value={settings.dailyLimit}
          onValueChange={(value) => updateSetting('dailyLimit', value)}
          minimumTrackTintColor="#6200EE"
          maximumTrackTintColor="#E0E0E0"
          thumbTintColor="#6200EE"
        />
        <View style={styles.usageBar}>
          <View style={styles.usageInfo}>
            <Text style={styles.usageLabel}>Today's Usage</Text>
            <Text style={styles.usageValue}>
              {formatCurrency(currentUsage.daily)}
            </Text>
          </View>
          <View style={styles.usageProgress}>
            <View
              style={[
                styles.usageProgressFill,
                {
                  width: `${getUsagePercentage(
                    currentUsage.daily,
                    settings.dailyLimit
                  )}%`,
                  backgroundColor: getUsageColor(
                    getUsagePercentage(currentUsage.daily, settings.dailyLimit)
                  ),
                },
              ]}
            />
          </View>
        </View>
      </View>

      {/* Weekly Limit */}
      <View style={styles.limitCard}>
        <View style={styles.limitHeader}>
          <Text style={styles.limitLabel}>Weekly Limit</Text>
          <Text style={styles.limitValue}>
            {formatCurrency(settings.weeklyLimit)}
          </Text>
        </View>
        <Slider
          style={styles.slider}
          minimumValue={1000}
          maximumValue={30000}
          step={500}
          value={settings.weeklyLimit}
          onValueChange={(value) => updateSetting('weeklyLimit', value)}
          minimumTrackTintColor="#6200EE"
          maximumTrackTintColor="#E0E0E0"
          thumbTintColor="#6200EE"
        />
        <View style={styles.usageBar}>
          <View style={styles.usageInfo}>
            <Text style={styles.usageLabel}>This Week's Usage</Text>
            <Text style={styles.usageValue}>
              {formatCurrency(currentUsage.weekly)}
            </Text>
          </View>
          <View style={styles.usageProgress}>
            <View
              style={[
                styles.usageProgressFill,
                {
                  width: `${getUsagePercentage(
                    currentUsage.weekly,
                    settings.weeklyLimit
                  )}%`,
                  backgroundColor: getUsageColor(
                    getUsagePercentage(currentUsage.weekly, settings.weeklyLimit)
                  ),
                },
              ]}
            />
          </View>
        </View>
      </View>

      {/* Monthly Limit */}
      <View style={styles.limitCard}>
        <View style={styles.limitHeader}>
          <Text style={styles.limitLabel}>Monthly Limit</Text>
          <Text style={styles.limitValue}>
            {formatCurrency(settings.monthlyLimit)}
          </Text>
        </View>
        <Slider
          style={styles.slider}
          minimumValue={5000}
          maximumValue={100000}
          step={1000}
          value={settings.monthlyLimit}
          onValueChange={(value) => updateSetting('monthlyLimit', value)}
          minimumTrackTintColor="#6200EE"
          maximumTrackTintColor="#E0E0E0"
          thumbTintColor="#6200EE"
        />
        <View style={styles.usageBar}>
          <View style={styles.usageInfo}>
            <Text style={styles.usageLabel}>This Month's Usage</Text>
            <Text style={styles.usageValue}>
              {formatCurrency(currentUsage.monthly)}
            </Text>
          </View>
          <View style={styles.usageProgress}>
            <View
              style={[
                styles.usageProgressFill,
                {
                  width: `${getUsagePercentage(
                    currentUsage.monthly,
                    settings.monthlyLimit
                  )}%`,
                  backgroundColor: getUsageColor(
                    getUsagePercentage(currentUsage.monthly, settings.monthlyLimit)
                  ),
                },
              ]}
            />
          </View>
        </View>
      </View>
    </View>
  );

  const renderPaymentPreferences = () => (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Payment Preferences</Text>

      <View style={styles.settingRow}>
        <View style={styles.settingLeft}>
          <Icon name="check-circle" size={24} color="#666" />
          <View style={styles.settingInfo}>
            <Text style={styles.settingLabel}>Auto-Accept Requests</Text>
            <Text style={styles.settingDescription}>
              Automatically accept payment requests
            </Text>
          </View>
        </View>
        <Switch
          value={settings.autoAcceptRequests}
          onValueChange={(value) => updateSetting('autoAcceptRequests', value)}
          trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
          thumbColor={settings.autoAcceptRequests ? '#6200EE' : '#FFFFFF'}
        />
      </View>

      <View style={styles.settingRow}>
        <View style={styles.settingLeft}>
          <Icon name="receipt" size={24} color="#666" />
          <View style={styles.settingInfo}>
            <Text style={styles.settingLabel}>Auto-Send Receipts</Text>
            <Text style={styles.settingDescription}>
              Automatically send receipts after payment
            </Text>
          </View>
        </View>
        <Switch
          value={settings.enableAutoReceipts}
          onValueChange={(value) => updateSetting('enableAutoReceipts', value)}
          trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
          thumbColor={settings.enableAutoReceipts ? '#6200EE' : '#FFFFFF'}
        />
      </View>

      <View style={styles.settingRow}>
        <View style={styles.settingLeft}>
          <Icon name="cash" size={24} color="#666" />
          <View style={styles.settingInfo}>
            <Text style={styles.settingLabel}>Show Transaction Fees</Text>
            <Text style={styles.settingDescription}>
              Display fees before confirming payment
            </Text>
          </View>
        </View>
        <Switch
          value={settings.showTransactionFees}
          onValueChange={(value) => updateSetting('showTransactionFees', value)}
          trackColor={{ false: '#E0E0E0', true: '#B388FF' }}
          thumbColor={settings.showTransactionFees ? '#6200EE' : '#FFFFFF'}
        />
      </View>
    </View>
  );

  const renderConfirmationSettings = () => (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Payment Confirmation</Text>

      <View style={styles.limitCard}>
        <View style={styles.limitHeader}>
          <Text style={styles.limitLabel}>Require Confirmation Above</Text>
          <Text style={styles.limitValue}>
            {formatCurrency(settings.confirmationThreshold)}
          </Text>
        </View>
        <Slider
          style={styles.slider}
          minimumValue={0}
          maximumValue={1000}
          step={10}
          value={settings.confirmationThreshold}
          onValueChange={(value) => updateSetting('confirmationThreshold', value)}
          minimumTrackTintColor="#6200EE"
          maximumTrackTintColor="#E0E0E0"
          thumbTintColor="#6200EE"
        />
        <Text style={styles.sliderHint}>
          Payments above this amount require confirmation
        </Text>
      </View>

      <Text style={styles.subSectionTitle}>Confirmation Method</Text>
      {['pin', 'biometric', 'both'].map((method) => (
        <TouchableOpacity
          key={method}
          style={[
            styles.radioOption,
            settings.confirmationMethod === method && styles.radioOptionActive,
          ]}
          onPress={() => updateSetting('confirmationMethod', method as any)}
        >
          <Icon
            name={
              settings.confirmationMethod === method
                ? 'radiobox-marked'
                : 'radiobox-blank'
            }
            size={24}
            color={settings.confirmationMethod === method ? '#6200EE' : '#999'}
          />
          <Text style={styles.radioLabel}>
            {method === 'pin'
              ? 'PIN Only'
              : method === 'biometric'
              ? 'Biometric Only'
              : 'PIN or Biometric'}
          </Text>
        </TouchableOpacity>
      ))}
    </View>
  );

  return (
    <View style={styles.container}>
      <Header title="Payment Settings" showBack />

      <ScrollView style={styles.content}>
        {renderDefaultPaymentMethod()}
        {renderTransactionLimits()}
        {renderPaymentPreferences()}
        {renderConfirmationSettings()}
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
    marginBottom: 8,
  },
  sectionDescription: {
    fontSize: 14,
    color: '#666',
    marginBottom: 16,
  },
  subSectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginTop: 16,
    marginBottom: 12,
  },
  paymentMethodCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  paymentMethodCardActive: {
    backgroundColor: '#F3E5F5',
    borderColor: '#6200EE',
  },
  paymentMethodLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  paymentMethodInfo: {
    marginLeft: 12,
  },
  paymentMethodName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
  },
  paymentMethodBalance: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  limitCard: {
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
  },
  limitHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  limitLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
  },
  limitValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#6200EE',
  },
  slider: {
    width: '100%',
    height: 40,
  },
  sliderHint: {
    fontSize: 12,
    color: '#999',
    marginTop: 8,
  },
  usageBar: {
    marginTop: 12,
  },
  usageInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  usageLabel: {
    fontSize: 13,
    color: '#666',
  },
  usageValue: {
    fontSize: 13,
    fontWeight: '600',
    color: '#212121',
  },
  usageProgress: {
    height: 6,
    backgroundColor: '#E0E0E0',
    borderRadius: 3,
    overflow: 'hidden',
  },
  usageProgressFill: {
    height: '100%',
    borderRadius: 3,
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
  radioOption: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    marginBottom: 8,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  radioOptionActive: {
    backgroundColor: '#F3E5F5',
    borderColor: '#6200EE',
  },
  radioLabel: {
    fontSize: 16,
    color: '#212121',
    marginLeft: 12,
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

export default PaymentSettingsScreen;
