import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Switch,
  TouchableOpacity,
  Alert,
  Platform
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { SafeAreaView } from 'react-native-safe-area-context';
import VoicePaymentService from '../../services/voice/VoicePaymentService';
import SiriIntegrationService from '../../services/voice/SiriIntegrationService';
import GoogleAssistantService from '../../services/voice/GoogleAssistantService';
import AlexaSkillService from '../../services/voice/AlexaSkillService';

interface VoiceSettings {
  enabled: boolean;
  biometricAuth: boolean;
  confirmationRequired: boolean;
  maxAmount: number;
  allowedActions: {
    send: boolean;
    request: boolean;
    checkBalance: boolean;
    splitBill: boolean;
  };
  siriEnabled: boolean;
  googleAssistantEnabled: boolean;
  alexaEnabled: boolean;
}

const VoiceSettingsScreen: React.FC = () => {
  const [settings, setSettings] = useState<VoiceSettings>({
    enabled: false,
    biometricAuth: true,
    confirmationRequired: true,
    maxAmount: 100,
    allowedActions: {
      send: true,
      request: true,
      checkBalance: true,
      splitBill: true
    },
    siriEnabled: Platform.OS === 'ios',
    googleAssistantEnabled: Platform.OS === 'android',
    alexaEnabled: false
  });
  
  const [isLoading, setIsLoading] = useState(true);
  const [isVoiceAvailable, setIsVoiceAvailable] = useState(false);

  useEffect(() => {
    loadSettings();
    checkVoiceAvailability();
  }, []);

  const loadSettings = async () => {
    try {
      const savedSettings = await VoicePaymentService.getVoiceSettings();
      if (savedSettings) {
        setSettings(savedSettings);
      }
    } catch (error) {
      console.error('Failed to load voice settings:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const checkVoiceAvailability = async () => {
    try {
      const available = await VoicePaymentService.isAvailable();
      setIsVoiceAvailable(available);
    } catch (error) {
      console.error('Failed to check voice availability:', error);
      setIsVoiceAvailable(false);
    }
  };

  const saveSettings = async (newSettings: VoiceSettings) => {
    try {
      await VoicePaymentService.updateVoiceSettings(newSettings);
      setSettings(newSettings);
    } catch (error) {
      console.error('Failed to save voice settings:', error);
      Alert.alert('Error', 'Failed to save settings. Please try again.');
    }
  };

  const handleToggleVoice = async (enabled: boolean) => {
    if (enabled && !isVoiceAvailable) {
      Alert.alert(
        'Voice Not Available',
        'Voice payments are not available on this device. Please check your microphone permissions.',
        [{ text: 'OK' }]
      );
      return;
    }

    if (enabled) {
      Alert.alert(
        'Enable Voice Payments?',
        'This will allow you to make payments using voice commands. Make sure you\'re in a secure environment when using this feature.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Enable',
            onPress: async () => {
              try {
                await VoicePaymentService.enableVoicePayments();
                await saveSettings({ ...settings, enabled: true });
              } catch (error) {
                Alert.alert('Error', 'Failed to enable voice payments. Please try again.');
              }
            }
          }
        ]
      );
    } else {
      await saveSettings({ ...settings, enabled: false });
    }
  };

  const handleMaxAmountChange = (amount: number) => {
    if (amount < 1 || amount > 1000) {
      Alert.alert('Invalid Amount', 'Maximum amount must be between $1 and $1000');
      return;
    }
    saveSettings({ ...settings, maxAmount: amount });
  };

  const handleSiriSetup = async () => {
    if (Platform.OS !== 'ios') return;

    try {
      await SiriIntegrationService.initialize();
      Alert.alert(
        'Siri Setup Complete',
        'You can now use Siri shortcuts for Waqiti payments. Try saying "Hey Siri, send money with Waqiti"'
      );
    } catch (error) {
      Alert.alert('Error', 'Failed to setup Siri integration. Please try again.');
    }
  };

  const handleGoogleAssistantSetup = async () => {
    if (Platform.OS !== 'android') return;

    try {
      await GoogleAssistantService.initialize();
      Alert.alert(
        'Google Assistant Setup Complete',
        'You can now use Google Assistant for Waqiti payments. Try saying "Hey Google, talk to Waqiti"'
      );
    } catch (error) {
      Alert.alert('Error', 'Failed to setup Google Assistant integration. Please try again.');
    }
  };

  const handleAlexaSetup = async () => {
    Alert.alert(
      'Alexa Skill Setup',
      'To use Alexa with Waqiti:\n\n1. Open the Alexa app\n2. Search for "Waqiti" skill\n3. Enable the skill\n4. Link your Waqiti account\n\nThen you can say "Alexa, ask Waqiti to check my balance"',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Learn More',
          onPress: () => {
            // In a real app, this would open the Alexa app or a web page
            console.log('Opening Alexa skill setup guide');
          }
        }
      ]
    );
  };

  const renderSettingItem = (
    title: string,
    subtitle: string,
    value: boolean,
    onToggle: (value: boolean) => void,
    disabled: boolean = false
  ) => (
    <View style={styles.settingItem}>
      <View style={styles.settingInfo}>
        <Text style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingSubtitle}>{subtitle}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onToggle}
        disabled={disabled}
        trackColor={{ false: '#E0E0E0', true: '#667eea' }}
        thumbColor={value ? '#FFFFFF' : '#F4F3F4'}
      />
    </View>
  );

  const renderActionToggle = (
    action: keyof VoiceSettings['allowedActions'],
    title: string,
    icon: string
  ) => (
    <View style={styles.actionItem}>
      <View style={styles.actionInfo}>
        <Icon name={icon} size={24} color="#667eea" style={styles.actionIcon} />
        <Text style={styles.actionTitle}>{title}</Text>
      </View>
      <Switch
        value={settings.allowedActions[action]}
        onValueChange={(value) => {
          saveSettings({
            ...settings,
            allowedActions: { ...settings.allowedActions, [action]: value }
          });
        }}
        disabled={!settings.enabled}
        trackColor={{ false: '#E0E0E0', true: '#667eea' }}
        thumbColor={settings.allowedActions[action] ? '#FFFFFF' : '#F4F3F4'}
      />
    </View>
  );

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <Text style={styles.loadingText}>Loading voice settings...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Icon name="mic" size={32} color="#667eea" />
          <Text style={styles.headerTitle}>Voice Payments</Text>
          <Text style={styles.headerSubtitle}>
            Configure voice-activated payment settings
          </Text>
        </View>

        {!isVoiceAvailable && (
          <View style={styles.warningCard}>
            <Icon name="warning" size={24} color="#FF9800" />
            <Text style={styles.warningText}>
              Voice payments are not available on this device. Please check your microphone permissions.
            </Text>
          </View>
        )}

        {/* Main Voice Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>General Settings</Text>
          
          {renderSettingItem(
            'Enable Voice Payments',
            'Allow payment transactions using voice commands',
            settings.enabled,
            handleToggleVoice,
            !isVoiceAvailable
          )}

          {renderSettingItem(
            'Biometric Authentication',
            'Require biometric verification for voice payments',
            settings.biometricAuth,
            (value) => saveSettings({ ...settings, biometricAuth: value }),
            !settings.enabled
          )}

          {renderSettingItem(
            'Confirmation Required',
            'Always ask for confirmation before executing payments',
            settings.confirmationRequired,
            (value) => saveSettings({ ...settings, confirmationRequired: value }),
            !settings.enabled
          )}
        </View>

        {/* Transaction Limits */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Transaction Limits</Text>
          <View style={styles.limitCard}>
            <View style={styles.limitInfo}>
              <Text style={styles.limitTitle}>Maximum Amount</Text>
              <Text style={styles.limitSubtitle}>
                Maximum amount allowed for voice payments
              </Text>
            </View>
            <View style={styles.limitControls}>
              <TouchableOpacity
                style={styles.limitButton}
                onPress={() => handleMaxAmountChange(Math.max(1, settings.maxAmount - 10))}
                disabled={!settings.enabled}
              >
                <Icon name="remove" size={20} color={settings.enabled ? "#667eea" : "#CCC"} />
              </TouchableOpacity>
              <Text style={styles.limitAmount}>${settings.maxAmount}</Text>
              <TouchableOpacity
                style={styles.limitButton}
                onPress={() => handleMaxAmountChange(Math.min(1000, settings.maxAmount + 10))}
                disabled={!settings.enabled}
              >
                <Icon name="add" size={20} color={settings.enabled ? "#667eea" : "#CCC"} />
              </TouchableOpacity>
            </View>
          </View>
        </View>

        {/* Allowed Actions */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Allowed Actions</Text>
          {renderActionToggle('send', 'Send Money', 'arrow-upward')}
          {renderActionToggle('request', 'Request Money', 'arrow-downward')}
          {renderActionToggle('checkBalance', 'Check Balance', 'account-balance')}
          {renderActionToggle('splitBill', 'Split Bills', 'group')}
        </View>

        {/* Voice Assistant Integration */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Voice Assistant Integration</Text>
          
          {Platform.OS === 'ios' && (
            <TouchableOpacity
              style={[styles.integrationCard, !settings.enabled && styles.disabledCard]}
              onPress={handleSiriSetup}
              disabled={!settings.enabled}
            >
              <View style={styles.integrationInfo}>
                <Icon name="mic" size={24} color="#667eea" />
                <View style={styles.integrationText}>
                  <Text style={styles.integrationTitle}>Siri Shortcuts</Text>
                  <Text style={styles.integrationSubtitle}>
                    "Hey Siri, send money with Waqiti"
                  </Text>
                </View>
              </View>
              <Icon name="arrow-forward" size={20} color="#CCC" />
            </TouchableOpacity>
          )}

          {Platform.OS === 'android' && (
            <TouchableOpacity
              style={[styles.integrationCard, !settings.enabled && styles.disabledCard]}
              onPress={handleGoogleAssistantSetup}
              disabled={!settings.enabled}
            >
              <View style={styles.integrationInfo}>
                <Icon name="mic" size={24} color="#667eea" />
                <View style={styles.integrationText}>
                  <Text style={styles.integrationTitle}>Google Assistant</Text>
                  <Text style={styles.integrationSubtitle}>
                    "Hey Google, talk to Waqiti"
                  </Text>
                </View>
              </View>
              <Icon name="arrow-forward" size={20} color="#CCC" />
            </TouchableOpacity>
          )}

          <TouchableOpacity
            style={[styles.integrationCard, !settings.enabled && styles.disabledCard]}
            onPress={handleAlexaSetup}
            disabled={!settings.enabled}
          >
            <View style={styles.integrationInfo}>
              <Icon name="speaker" size={24} color="#667eea" />
              <View style={styles.integrationText}>
                <Text style={styles.integrationTitle}>Amazon Alexa</Text>
                <Text style={styles.integrationSubtitle}>
                  "Alexa, ask Waqiti to check my balance"
                </Text>
              </View>
            </View>
            <Icon name="arrow-forward" size={20} color="#CCC" />
          </TouchableOpacity>
        </View>

        {/* Security Notice */}
        <View style={styles.securityNotice}>
          <Icon name="security" size={20} color="#666" />
          <Text style={styles.securityText}>
            Voice payments are secured with biometric authentication and confirmation requirements. 
            Only use in secure, private environments.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  scrollView: {
    flex: 1,
    padding: 16,
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
  header: {
    alignItems: 'center',
    paddingVertical: 24,
    marginBottom: 16,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginTop: 8,
    marginBottom: 4,
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
  warningCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF8E1',
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#FF9800',
  },
  warningText: {
    flex: 1,
    fontSize: 14,
    color: '#E65100',
    marginLeft: 12,
    lineHeight: 20,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 16,
  },
  settingItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  settingInfo: {
    flex: 1,
  },
  settingTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 2,
  },
  settingSubtitle: {
    fontSize: 13,
    color: '#666',
    lineHeight: 18,
  },
  limitCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  limitInfo: {
    flex: 1,
  },
  limitTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 2,
  },
  limitSubtitle: {
    fontSize: 13,
    color: '#666',
  },
  limitControls: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  limitButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#F5F5F5',
    alignItems: 'center',
    justifyContent: 'center',
  },
  limitAmount: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginHorizontal: 16,
    minWidth: 60,
    textAlign: 'center',
  },
  actionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  actionInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  actionIcon: {
    marginRight: 12,
  },
  actionTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  integrationCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  disabledCard: {
    opacity: 0.5,
  },
  integrationInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  integrationText: {
    marginLeft: 12,
  },
  integrationTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 2,
  },
  integrationSubtitle: {
    fontSize: 13,
    color: '#666',
    fontStyle: 'italic',
  },
  securityNotice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8F4FD',
    padding: 16,
    borderRadius: 12,
    marginTop: 8,
    marginBottom: 24,
  },
  securityText: {
    flex: 1,
    fontSize: 13,
    color: '#1976D2',
    lineHeight: 18,
    marginLeft: 8,
  },
});

export default VoiceSettingsScreen;