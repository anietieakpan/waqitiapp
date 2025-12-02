import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Alert,
  Switch,
  TouchableOpacity,
  Modal,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import NFCPaymentService from '../../services/nfc/NFCPaymentService';
import { useNavigation } from '@react-navigation/native';

interface NFCSettings {
  enabled: boolean;
  secureElementEnabled: boolean;
  biometricRequired: boolean;
  pinRequired: boolean;
  maxPaymentAmount: number;
  maxP2PAmount: number;
  autoAcceptFromContacts: boolean;
  allowContactSharing: boolean;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  showTutorials: boolean;
}

interface DeviceCapabilities {
  nfcSupported: boolean;
  nfcEnabled: boolean;
  secureElementAvailable: boolean;
  hceSupported: boolean;
  readerModeSupported: boolean;
  biometricAvailable: boolean;
  hardwareBackedKeystore: boolean;
}

const NFCSettingsScreen: React.FC = () => {
  const navigation = useNavigation();
  const [settings, setSettings] = useState<NFCSettings>({
    enabled: false,
    secureElementEnabled: false,
    biometricRequired: true,
    pinRequired: false,
    maxPaymentAmount: 1000,
    maxP2PAmount: 500,
    autoAcceptFromContacts: false,
    allowContactSharing: true,
    soundEnabled: true,
    vibrationEnabled: true,
    showTutorials: true,
  });
  
  const [capabilities, setCapabilities] = useState<DeviceCapabilities>({
    nfcSupported: false,
    nfcEnabled: false,
    secureElementAvailable: false,
    hceSupported: false,
    readerModeSupported: false,
    biometricAvailable: false,
    hardwareBackedKeystore: false,
  });
  
  const [loading, setLoading] = useState(true);
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [limitType, setLimitType] = useState<'payment' | 'p2p'>('payment');

  useEffect(() => {
    loadSettings();
    checkDeviceCapabilities();
  }, []);

  const loadSettings = async () => {
    try {
      // Load settings from storage or API
      // For now, using default values
      setLoading(false);
    } catch (error) {
      console.error('Error loading NFC settings:', error);
      setLoading(false);
    }
  };

  const checkDeviceCapabilities = async () => {
    try {
      const deviceCapabilities = await NFCPaymentService.getDeviceCapabilities();
      setCapabilities({
        nfcSupported: deviceCapabilities.nfcSupported || false,
        nfcEnabled: deviceCapabilities.nfcEnabled || false,
        secureElementAvailable: deviceCapabilities.secureElementAvailable || false,
        hceSupported: deviceCapabilities.hceSupported || false,
        readerModeSupported: deviceCapabilities.readerModeSupported || false,
        biometricAvailable: deviceCapabilities.biometricAvailable || false,
        hardwareBackedKeystore: deviceCapabilities.hardwareBackedKeystore || false,
      });
      
      setSettings(prev => ({
        ...prev,
        enabled: deviceCapabilities.nfcEnabled || false,
        secureElementEnabled: deviceCapabilities.secureElementAvailable || false,
      }));
    } catch (error) {
      console.error('Error checking device capabilities:', error);
    }
  };

  const updateSetting = async <K extends keyof NFCSettings>(
    key: K,
    value: NFCSettings[K]
  ) => {
    try {
      const newSettings = { ...settings, [key]: value };
      setSettings(newSettings);
      
      // Save to storage or API
      // await saveSettings(newSettings);
      
      // Handle specific setting changes
      switch (key) {
        case 'enabled':
          if (value && !capabilities.nfcEnabled) {
            Alert.alert(
              'NFC Disabled',
              'NFC is disabled on your device. Please enable NFC in your device settings.',
              [
                { text: 'Cancel' },
                { text: 'Open Settings', onPress: () => openNFCSettings() }
              ]
            );
            setSettings(prev => ({ ...prev, enabled: false }));
            return;
          }
          break;
          
        case 'secureElementEnabled':
          if (value && !capabilities.secureElementAvailable) {
            Alert.alert(
              'Secure Element Unavailable',
              'Your device does not support secure element functionality.',
              [{ text: 'OK' }]
            );
            setSettings(prev => ({ ...prev, secureElementEnabled: false }));
            return;
          }
          break;
          
        case 'biometricRequired':
          if (value && !capabilities.biometricAvailable) {
            Alert.alert(
              'Biometric Authentication Unavailable',
              'Your device does not support biometric authentication.',
              [{ text: 'OK' }]
            );
            setSettings(prev => ({ ...prev, biometricRequired: false }));
            return;
          }
          break;
      }
      
      // Show success feedback
      if (['biometricRequired', 'pinRequired', 'secureElementEnabled'].includes(key)) {
        Alert.alert('Settings Updated', 'Your NFC security settings have been updated.');
      }
      
    } catch (error) {
      console.error('Error updating setting:', error);
      Alert.alert('Error', 'Failed to update setting. Please try again.');
    }
  };

  const openNFCSettings = () => {
    // Open device NFC settings
    if (Platform.OS === 'android') {
      // Android-specific code to open NFC settings
      Alert.alert('Open Settings', 'Please go to Settings > Connected devices > NFC to enable NFC.');
    } else {
      Alert.alert('NFC Settings', 'Please check your device settings for NFC options.');
    }
  };

  const showLimitDialog = (type: 'payment' | 'p2p') => {
    setLimitType(type);
    setShowLimitModal(true);
  };

  const testNFCFunctionality = async () => {
    try {
      Alert.alert('Testing NFC', 'Checking NFC functionality...');
      
      const isSecure = await NFCPaymentService.isNFCSecure();
      const canEnable = await NFCPaymentService.enableSecureMode();
      
      Alert.alert(
        'NFC Test Results',
        `NFC Secure: ${isSecure ? 'Yes' : 'No'}\nSecure Mode: ${canEnable ? 'Available' : 'Unavailable'}`,
        [{ text: 'OK' }]
      );
    } catch (error) {
      Alert.alert('Test Failed', 'Unable to test NFC functionality.');
    }
  };

  const viewNFCHistory = () => {
    navigation.navigate('NFCHistory');
  };

  const viewTrustedDevices = () => {
    navigation.navigate('TrustedDevices');
  };

  const showNFCTutorial = () => {
    navigation.navigate('NFCTutorial');
  };

  const resetNFCSettings = () => {
    Alert.alert(
      'Reset NFC Settings',
      'This will reset all NFC settings to default values. Are you sure?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset',
          style: 'destructive',
          onPress: () => {
            setSettings({
              enabled: false,
              secureElementEnabled: false,
              biometricRequired: true,
              pinRequired: false,
              maxPaymentAmount: 1000,
              maxP2PAmount: 500,
              autoAcceptFromContacts: false,
              allowContactSharing: true,
              soundEnabled: true,
              vibrationEnabled: true,
              showTutorials: true,
            });
            Alert.alert('Settings Reset', 'NFC settings have been reset to default values.');
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
    icon: string,
    disabled: boolean = false
  ) => (
    <View style={[styles.settingItem, disabled && styles.settingItemDisabled]}>
      <View style={styles.settingIcon}>
        <Icon name={icon} size={24} color={disabled ? '#999' : '#007AFF'} />
      </View>
      <View style={styles.settingContent}>
        <Text style={[styles.settingTitle, disabled && styles.disabledText]}>{title}</Text>
        <Text style={[styles.settingSubtitle, disabled && styles.disabledText]}>{subtitle}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onToggle}
        disabled={disabled}
        trackColor={{ false: '#767577', true: '#007AFF' }}
        thumbColor={value ? '#FFF' : '#f4f3f4'}
      />
    </View>
  );

  const renderActionItem = (
    title: string,
    subtitle: string,
    onPress: () => void,
    icon: string,
    color: string = '#007AFF'
  ) => (
    <TouchableOpacity style={styles.actionItem} onPress={onPress}>
      <View style={styles.settingIcon}>
        <Icon name={icon} size={24} color={color} />
      </View>
      <View style={styles.settingContent}>
        <Text style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingSubtitle}>{subtitle}</Text>
      </View>
      <Icon name="chevron-right" size={24} color="#C7C7CC" />
    </TouchableOpacity>
  );

  if (loading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <Text>Loading NFC settings...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Icon name="arrow-left" size={24} color="#000" />
        </TouchableOpacity>
        <Text style={styles.title}>NFC Settings</Text>
        <View style={styles.placeholder} />
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Device Status */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Device Status</Text>
          <View style={styles.statusContainer}>
            <View style={styles.statusItem}>
              <Text style={styles.statusLabel}>NFC Supported</Text>
              <View style={[styles.statusIndicator, 
                capabilities.nfcSupported ? styles.statusGreen : styles.statusRed]}>
                <Text style={styles.statusText}>
                  {capabilities.nfcSupported ? 'Yes' : 'No'}
                </Text>
              </View>
            </View>
            <View style={styles.statusItem}>
              <Text style={styles.statusLabel}>NFC Enabled</Text>
              <View style={[styles.statusIndicator, 
                capabilities.nfcEnabled ? styles.statusGreen : styles.statusRed]}>
                <Text style={styles.statusText}>
                  {capabilities.nfcEnabled ? 'Yes' : 'No'}
                </Text>
              </View>
            </View>
            <View style={styles.statusItem}>
              <Text style={styles.statusLabel}>Secure Element</Text>
              <View style={[styles.statusIndicator, 
                capabilities.secureElementAvailable ? styles.statusGreen : styles.statusGray]}>
                <Text style={styles.statusText}>
                  {capabilities.secureElementAvailable ? 'Available' : 'N/A'}
                </Text>
              </View>
            </View>
          </View>
        </View>

        {/* NFC Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>NFC Payment Settings</Text>
          
          {renderSettingItem(
            'Enable NFC Payments',
            'Allow payments using NFC technology',
            settings.enabled,
            (value) => updateSetting('enabled', value),
            'nfc',
            !capabilities.nfcSupported
          )}
          
          {renderSettingItem(
            'Secure Element',
            'Use hardware security for enhanced protection',
            settings.secureElementEnabled,
            (value) => updateSetting('secureElementEnabled', value),
            'shield-check',
            !capabilities.secureElementAvailable || !settings.enabled
          )}
        </View>

        {/* Security Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Security</Text>
          
          {renderSettingItem(
            'Require Biometric',
            'Use fingerprint or face recognition for payments',
            settings.biometricRequired,
            (value) => updateSetting('biometricRequired', value),
            'fingerprint',
            !capabilities.biometricAvailable || !settings.enabled
          )}
          
          {renderSettingItem(
            'Require PIN',
            'Require PIN entry for NFC payments',
            settings.pinRequired,
            (value) => updateSetting('pinRequired', value),
            'numeric',
            !settings.enabled
          )}
        </View>

        {/* Payment Limits */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Payment Limits</Text>
          
          <TouchableOpacity 
            style={styles.limitItem} 
            onPress={() => showLimitDialog('payment')}
            disabled={!settings.enabled}
          >
            <View style={styles.settingIcon}>
              <Icon name="credit-card" size={24} color={settings.enabled ? '#007AFF' : '#999'} />
            </View>
            <View style={styles.settingContent}>
              <Text style={[styles.settingTitle, !settings.enabled && styles.disabledText]}>
                Max Payment Amount
              </Text>
              <Text style={[styles.settingSubtitle, !settings.enabled && styles.disabledText]}>
                ${settings.maxPaymentAmount.toFixed(2)} per transaction
              </Text>
            </View>
            <Icon name="chevron-right" size={24} color="#C7C7CC" />
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={styles.limitItem} 
            onPress={() => showLimitDialog('p2p')}
            disabled={!settings.enabled}
          >
            <View style={styles.settingIcon}>
              <Icon name="account-multiple" size={24} color={settings.enabled ? '#007AFF' : '#999'} />
            </View>
            <View style={styles.settingContent}>
              <Text style={[styles.settingTitle, !settings.enabled && styles.disabledText]}>
                Max P2P Transfer
              </Text>
              <Text style={[styles.settingSubtitle, !settings.enabled && styles.disabledText]}>
                ${settings.maxP2PAmount.toFixed(2)} per transfer
              </Text>
            </View>
            <Icon name="chevron-right" size={24} color="#C7C7CC" />
          </TouchableOpacity>
        </View>

        {/* Contact & Social */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Contact & Social</Text>
          
          {renderSettingItem(
            'Auto-accept from Contacts',
            'Automatically accept payments from contacts',
            settings.autoAcceptFromContacts,
            (value) => updateSetting('autoAcceptFromContacts', value),
            'account-check',
            !settings.enabled
          )}
          
          {renderSettingItem(
            'Allow Contact Sharing',
            'Share contact info during NFC exchanges',
            settings.allowContactSharing,
            (value) => updateSetting('allowContactSharing', value),
            'contacts',
            !settings.enabled
          )}
        </View>

        {/* User Experience */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>User Experience</Text>
          
          {renderSettingItem(
            'Sound Effects',
            'Play sounds for NFC interactions',
            settings.soundEnabled,
            (value) => updateSetting('soundEnabled', value),
            'volume-high'
          )}
          
          {renderSettingItem(
            'Vibration Feedback',
            'Vibrate device for NFC events',
            settings.vibrationEnabled,
            (value) => updateSetting('vibrationEnabled', value),
            'vibrate'
          )}
          
          {renderSettingItem(
            'Show Tutorials',
            'Display helpful tips and tutorials',
            settings.showTutorials,
            (value) => updateSetting('showTutorials', value),
            'help-circle'
          )}
        </View>

        {/* Actions */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Actions</Text>
          
          {renderActionItem(
            'Test NFC Functionality',
            'Check if NFC is working properly',
            testNFCFunctionality,
            'test-tube'
          )}
          
          {renderActionItem(
            'View Transaction History',
            'See all NFC payment transactions',
            viewNFCHistory,
            'history'
          )}
          
          {renderActionItem(
            'Trusted Devices',
            'Manage devices authorized for NFC',
            viewTrustedDevices,
            'devices'
          )}
          
          {renderActionItem(
            'NFC Tutorial',
            'Learn how to use NFC payments',
            showNFCTutorial,
            'school'
          )}
          
          {renderActionItem(
            'Reset Settings',
            'Reset all NFC settings to defaults',
            resetNFCSettings,
            'restore',
            '#FF3B30'
          )}
        </View>

        <View style={styles.bottomSpacing} />
      </ScrollView>

      {/* Limit Setting Modal */}
      <Modal
        visible={showLimitModal}
        transparent={true}
        animationType="slide"
        onRequestClose={() => setShowLimitModal(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>
              Set {limitType === 'payment' ? 'Payment' : 'P2P Transfer'} Limit
            </Text>
            <Text style={styles.modalSubtitle}>
              Maximum amount per {limitType === 'payment' ? 'payment' : 'transfer'}
            </Text>
            
            {/* Limit options */}
            <View style={styles.limitOptions}>
              {[100, 250, 500, 1000, 2500, 5000].map((amount) => (
                <TouchableOpacity
                  key={amount}
                  style={[
                    styles.limitOption,
                    (limitType === 'payment' ? settings.maxPaymentAmount : settings.maxP2PAmount) === amount && 
                    styles.limitOptionSelected
                  ]}
                  onPress={() => {
                    updateSetting(
                      limitType === 'payment' ? 'maxPaymentAmount' : 'maxP2PAmount',
                      amount
                    );
                    setShowLimitModal(false);
                  }}
                >
                  <Text style={[
                    styles.limitOptionText,
                    (limitType === 'payment' ? settings.maxPaymentAmount : settings.maxP2PAmount) === amount && 
                    styles.limitOptionTextSelected
                  ]}>
                    ${amount}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            
            <TouchableOpacity
              style={styles.modalCloseButton}
              onPress={() => setShowLimitModal(false)}
            >
              <Text style={styles.modalCloseText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E7',
    backgroundColor: '#FFF',
  },
  backButton: {
    padding: 8,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
  },
  placeholder: {
    width: 40,
  },
  scrollView: {
    flex: 1,
  },
  section: {
    backgroundColor: '#FFF',
    marginTop: 20,
    paddingVertical: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    paddingHorizontal: 16,
    paddingVertical: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  statusContainer: {
    paddingHorizontal: 16,
  },
  statusItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  statusLabel: {
    fontSize: 16,
    color: '#000',
  },
  statusIndicator: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusGreen: {
    backgroundColor: '#34C759',
  },
  statusRed: {
    backgroundColor: '#FF3B30',
  },
  statusGray: {
    backgroundColor: '#8E8E93',
  },
  statusText: {
    color: '#FFF',
    fontSize: 12,
    fontWeight: '600',
  },
  settingItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0',
  },
  settingItemDisabled: {
    opacity: 0.5,
  },
  settingIcon: {
    marginRight: 12,
    width: 24,
    alignItems: 'center',
  },
  settingContent: {
    flex: 1,
  },
  settingTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#000',
    marginBottom: 2,
  },
  settingSubtitle: {
    fontSize: 14,
    color: '#666',
  },
  disabledText: {
    color: '#999',
  },
  actionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0',
  },
  limitItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0',
  },
  bottomSpacing: {
    height: 50,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: '#FFF',
    borderRadius: 12,
    padding: 20,
    margin: 20,
    minWidth: 300,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 8,
  },
  modalSubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 20,
  },
  limitOptions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  limitOption: {
    width: '30%',
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#E5E5E7',
    alignItems: 'center',
    marginBottom: 10,
  },
  limitOptionSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#007AFF',
  },
  limitOptionText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#000',
  },
  limitOptionTextSelected: {
    color: '#FFF',
  },
  modalCloseButton: {
    padding: 12,
    alignItems: 'center',
  },
  modalCloseText: {
    fontSize: 16,
    color: '#007AFF',
    fontWeight: '500',
  },
});

export default NFCSettingsScreen;