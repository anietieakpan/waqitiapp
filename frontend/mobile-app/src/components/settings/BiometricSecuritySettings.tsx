/**
 * BiometricSecuritySettings - Security settings component for biometric authentication
 * Provides comprehensive biometric security management including setup, configuration,
 * device trust management, and security assessment display.
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Alert,
  Platform,
  RefreshControl,
  Linking,
} from 'react-native';
import {
  Card,
  List,
  Switch,
  Text,
  Button,
  Divider,
  ActivityIndicator,
  Surface,
  IconButton,
  Menu,
  Chip,
  ProgressBar,
  Portal,
  Dialog,
  TextInput,
  HelperText,
} from 'react-native-paper';
import { useBiometric } from '../../hooks/useBiometric';
import { useSecurity } from '../../contexts/SecurityContext';
import { useAuth } from '../../contexts/AuthContext';
import { useTheme } from 'react-native-paper';
import {
  BiometricStatus,
  SecurityLevel,
  AuthenticationMethod,
  BiometricSettings,
  SecurityAssessment,
} from '../../services/biometric/types';
import BiometricTypeIcon from '../auth/BiometricTypeIcon';
import BiometricProviderManager from '../../services/biometric/BiometricProviderService';

interface BiometricSecuritySettingsProps {
  onNavigateToSetup?: () => void;
  onNavigateToDeviceManagement?: () => void;
  style?: any;
}

export const BiometricSecuritySettings: React.FC<BiometricSecuritySettingsProps> = ({
  onNavigateToSetup,
  onNavigateToDeviceManagement,
  style,
}) => {
  const theme = useTheme();
  const { user } = useAuth();
  const {
    isPinSetup,
    isMfaEnabled,
    securityLevel,
    deviceTrusted,
    checkDeviceSecurity,
    updateSecuritySettings,
    getSecuritySettings,
    verifyPin,
  } = useSecurity();

  const {
    isAvailable,
    isSetup,
    status,
    capabilities,
    canAuthenticate,
    securityAssessment,
    settings: biometricSettings,
    loading,
    error,
    setupWithPrompt,
    disableBiometric,
    updateSettings,
    performSecurityAssessment,
    getStatusMessage,
    clearError,
  } = useBiometric();

  const [refreshing, setRefreshing] = useState(false);
  const [menuVisible, setMenuVisible] = useState(false);
  const [dialogVisible, setDialogVisible] = useState(false);
  const [pinDialogVisible, setPinDialogVisible] = useState(false);
  const [currentPin, setCurrentPin] = useState('');
  const [pinError, setPinError] = useState('');
  const [localSettings, setLocalSettings] = useState<BiometricSettings | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [availableProviders, setAvailableProviders] = useState<string[]>([]);

  // Initialize local settings
  useEffect(() => {
    if (biometricSettings) {
      setLocalSettings(biometricSettings);
    }
  }, [biometricSettings]);

  // Load available providers
  useEffect(() => {
    loadAvailableProviders();
  }, []);

  const loadAvailableProviders = async () => {
    try {
      const providers = await BiometricProviderManager.getAllAvailableProviders();
      setAvailableProviders(providers.map(p => p.name));
    } catch (error) {
      console.error('Failed to load biometric providers:', error);
    }
  };

  // Get biometric type name
  const biometricTypeName = useMemo(() => {
    if (!capabilities?.biometryType) return 'Biometric';
    
    switch (capabilities.biometryType) {
      case 'TouchID':
        return 'Touch ID';
      case 'FaceID':
        return 'Face ID';
      case 'Biometrics':
        return 'Fingerprint';
      default:
        return 'Biometric Authentication';
    }
  }, [capabilities]);

  // Get security level color
  const getSecurityLevelColor = (level: SecurityLevel) => {
    switch (level) {
      case SecurityLevel.HIGH:
      case SecurityLevel.CRITICAL:
        return theme.colors.success;
      case SecurityLevel.MEDIUM:
        return theme.colors.warning;
      case SecurityLevel.LOW:
        return theme.colors.error;
      default:
        return theme.colors.outline;
    }
  };

  // Get security assessment score color
  const getScoreColor = (score: number) => {
    if (score >= 80) return theme.colors.success;
    if (score >= 60) return theme.colors.warning;
    return theme.colors.error;
  };

  // Handle refresh
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await Promise.all([
        checkDeviceSecurity(),
        performSecurityAssessment(),
      ]);
    } catch (error) {
      console.error('Refresh failed:', error);
    } finally {
      setRefreshing(false);
    }
  }, [checkDeviceSecurity, performSecurityAssessment]);

  // Handle biometric toggle
  const handleBiometricToggle = useCallback(async (value: boolean) => {
    if (!localSettings) return;

    if (value) {
      // Enable biometric
      if (!isSetup) {
        Alert.alert(
          'Setup Required',
          `Would you like to set up ${biometricTypeName} for secure authentication?`,
          [
            { text: 'Cancel', style: 'cancel' },
            { 
              text: 'Set Up', 
              onPress: () => {
                if (onNavigateToSetup) {
                  onNavigateToSetup();
                } else {
                  handleSetupBiometric();
                }
              }
            },
          ]
        );
        return;
      }

      setLocalSettings({ ...localSettings, enabled: true });
      await updateSettings({ enabled: true });
    } else {
      // Disable biometric - require PIN verification
      if (isPinSetup) {
        setPinDialogVisible(true);
      } else {
        Alert.alert(
          'Disable Biometric',
          `Are you sure you want to disable ${biometricTypeName}?`,
          [
            { text: 'Cancel', style: 'cancel' },
            { 
              text: 'Disable', 
              style: 'destructive',
              onPress: async () => {
                setLocalSettings({ ...localSettings, enabled: false });
                await disableBiometric();
              }
            },
          ]
        );
      }
    }
  }, [localSettings, isSetup, isPinSetup, biometricTypeName, onNavigateToSetup, updateSettings, disableBiometric]);

  // Handle biometric setup
  const handleSetupBiometric = useCallback(async () => {
    try {
      setIsProcessing(true);
      clearError();

      const userId = user?.id || 'current-user';
      const success = await setupWithPrompt(
        userId,
        `Set up ${biometricTypeName} for secure authentication`
      );

      if (success) {
        Alert.alert(
          'Setup Complete',
          `${biometricTypeName} has been successfully set up for your account.`,
          [{ text: 'OK' }]
        );
      }
    } catch (error: any) {
      Alert.alert(
        'Setup Failed',
        error.message || 'Failed to set up biometric authentication',
        [{ text: 'OK' }]
      );
    } finally {
      setIsProcessing(false);
    }
  }, [biometricTypeName, setupWithPrompt, clearError]);

  // Handle PIN verification for disabling biometric
  const handlePinVerification = useCallback(async () => {
    if (currentPin.length < 4) {
      setPinError('PIN must be at least 4 digits');
      return;
    }

    try {
      setIsProcessing(true);
      
      // Verify PIN with security context
      const isValidPin = await verifyPin(currentPin);
      
      if (!isValidPin) {
        setPinError('Invalid PIN');
        return;
      }
      
      // If PIN is correct, disable biometric
      if (localSettings) {
        setLocalSettings({ ...localSettings, enabled: false });
        await disableBiometric();
      }
      
      setPinDialogVisible(false);
      setCurrentPin('');
      setPinError('');
    } catch (error: any) {
      setPinError('PIN verification failed. Please try again.');
      console.error('PIN verification error:', error);
    } finally {
      setIsProcessing(false);
    }
  }, [currentPin, localSettings, disableBiometric]);

  // Handle settings update
  const handleSettingUpdate = useCallback(async (key: keyof BiometricSettings, value: any) => {
    if (!localSettings) return;

    const updatedSettings = { ...localSettings, [key]: value };
    setLocalSettings(updatedSettings);

    try {
      await updateSettings({ [key]: value });
      await updateSecuritySettings({ [key]: value });
    } catch (error) {
      console.error('Failed to update setting:', error);
      // Revert on error
      setLocalSettings(localSettings);
    }
  }, [localSettings, updateSettings, updateSecuritySettings]);

  // Handle re-configure biometric
  const handleReconfigure = useCallback(() => {
    Alert.alert(
      'Re-configure Biometric',
      'This will remove your current biometric setup and allow you to set it up again. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Continue',
          style: 'destructive',
          onPress: async () => {
            try {
              await disableBiometric();
              handleSetupBiometric();
            } catch (error) {
              console.error('Failed to reconfigure:', error);
            }
          },
        },
      ]
    );
  }, [disableBiometric, handleSetupBiometric]);

  // Get provider icon
  const getProviderIcon = (provider: string): string => {
    switch (provider) {
      case 'ReactNativeBiometrics':
        return 'fingerprint';
      case 'TouchID':
        return 'fingerprint';
      case 'FingerprintScanner':
        return 'fingerprint';
      case 'VoiceBiometric':
        return 'microphone';
      default:
        return 'shield-check';
    }
  };

  // Get provider display name
  const getProviderDisplayName = (provider: string): string => {
    switch (provider) {
      case 'ReactNativeBiometrics':
        return 'Biometric Auth';
      case 'TouchID':
        return 'Touch ID';
      case 'FingerprintScanner':
        return 'Fingerprint';
      case 'VoiceBiometric':
        return 'Voice ID';
      default:
        return provider;
    }
  };

  // Render security assessment
  const renderSecurityAssessment = () => {
    if (!securityAssessment) return null;

    const { deviceTrustScore, riskLevel, threats, recommendations } = securityAssessment;

    return (
      <Card style={styles.assessmentCard}>
        <Card.Title 
          title="Security Assessment"
          subtitle={`Last checked: ${new Date(securityAssessment.timestamp).toLocaleString()}`}
          right={(props) => (
            <IconButton
              {...props}
              icon="refresh"
              onPress={performSecurityAssessment}
              disabled={loading}
            />
          )}
        />
        <Card.Content>
          {/* Trust Score */}
          <View style={styles.scoreContainer}>
            <Text variant="labelMedium">Device Trust Score</Text>
            <View style={styles.scoreRow}>
              <Text 
                variant="headlineMedium" 
                style={[styles.scoreText, { color: getScoreColor(deviceTrustScore) }]}
              >
                {deviceTrustScore}
              </Text>
              <Text variant="labelSmall" style={styles.scoreMax}>/100</Text>
            </View>
            <ProgressBar 
              progress={deviceTrustScore / 100} 
              color={getScoreColor(deviceTrustScore)}
              style={styles.progressBar}
            />
          </View>

          {/* Risk Level */}
          <View style={styles.riskContainer}>
            <Text variant="labelMedium">Risk Level</Text>
            <Chip 
              mode="flat"
              style={[styles.riskChip, { backgroundColor: getSecurityLevelColor(riskLevel) }]}
              textStyle={{ color: theme.colors.onPrimary }}
            >
              {riskLevel}
            </Chip>
          </View>

          {/* Threats */}
          {threats.length > 0 && (
            <View style={styles.threatsContainer}>
              <Text variant="labelMedium" style={styles.sectionTitle}>
                Security Threats Detected
              </Text>
              {threats.map((threat, index) => (
                <View key={index} style={styles.threatItem}>
                  <IconButton 
                    icon="alert-circle" 
                    size={16} 
                    iconColor={theme.colors.error}
                    style={styles.threatIcon}
                  />
                  <Text variant="bodySmall" style={styles.threatText}>{threat}</Text>
                </View>
              ))}
            </View>
          )}

          {/* Recommendations */}
          {recommendations.length > 0 && (
            <View style={styles.recommendationsContainer}>
              <Text variant="labelMedium" style={styles.sectionTitle}>
                Recommendations
              </Text>
              {recommendations.map((recommendation, index) => (
                <View key={index} style={styles.recommendationItem}>
                  <IconButton 
                    icon="lightbulb-outline" 
                    size={16} 
                    iconColor={theme.colors.primary}
                    style={styles.recommendationIcon}
                  />
                  <Text variant="bodySmall" style={styles.recommendationText}>
                    {recommendation}
                  </Text>
                </View>
              ))}
            </View>
          )}
        </Card.Content>
      </Card>
    );
  };

  if (loading && !localSettings) {
    return (
      <View style={[styles.container, styles.centerContent]}>
        <ActivityIndicator size="large" />
        <Text variant="bodyMedium" style={styles.loadingText}>
          Loading security settings...
        </Text>
      </View>
    );
  }

  return (
    <ScrollView 
      style={[styles.container, style]}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
      }
    >
      {/* Biometric Authentication Section */}
      <Card style={styles.sectionCard}>
        <Card.Title 
          title="Biometric Authentication"
          subtitle={isAvailable ? biometricTypeName : 'Not available on this device'}
          left={(props) => (
            <BiometricTypeIcon 
              {...props}
              type={capabilities?.biometryType || 'unknown'}
              size={40}
            />
          )}
        />
        <Card.Content>
          {isAvailable ? (
            <>
              <List.Item
                title="Enable Biometric Authentication"
                description={`Use ${biometricTypeName} for quick and secure access`}
                right={() => (
                  <Switch
                    value={localSettings?.enabled || false}
                    onValueChange={handleBiometricToggle}
                    disabled={!canAuthenticate && !isSetup}
                  />
                )}
              />

              {isSetup && localSettings?.enabled && (
                <>
                  <Divider style={styles.divider} />
                  
                  <List.Item
                    title="Require for Transactions"
                    description="Confirm payments with biometric"
                    right={() => (
                      <Switch
                        value={localSettings?.requireBiometricConfirmation || false}
                        onValueChange={(value) => 
                          handleSettingUpdate('requireBiometricConfirmation', value)
                        }
                      />
                    )}
                  />

                  <List.Item
                    title="Auto-lock"
                    description={`Lock app after ${localSettings?.autoLockDuration || 5} minutes`}
                    right={() => (
                      <Switch
                        value={localSettings?.autoLockEnabled || false}
                        onValueChange={(value) => 
                          handleSettingUpdate('autoLockEnabled', value)
                        }
                      />
                    )}
                  />

                  <Button
                    mode="outlined"
                    onPress={handleReconfigure}
                    style={styles.reconfigureButton}
                    icon="cog"
                  >
                    Re-configure {biometricTypeName}
                  </Button>
                </>
              )}

              {error && (
                <HelperText type="error" visible={true} style={styles.errorText}>
                  {error}
                </HelperText>
              )}
            </>
          ) : (
            <Text variant="bodyMedium" style={styles.unavailableText}>
              Biometric authentication is not available on this device.
              Please use PIN or password for security.
            </Text>
          )}
        </Card.Content>
      </Card>

      {/* Security Status */}
      <Card style={styles.sectionCard}>
        <Card.Title 
          title="Security Status"
          subtitle="Current security configuration"
          right={(props) => (
            <Menu
              visible={menuVisible}
              onDismiss={() => setMenuVisible(false)}
              anchor={
                <IconButton
                  {...props}
                  icon="dots-vertical"
                  onPress={() => setMenuVisible(true)}
                />
              }
            >
              <Menu.Item 
                onPress={() => {
                  setMenuVisible(false);
                  handleRefresh();
                }} 
                title="Refresh Status" 
                leadingIcon="refresh"
              />
              <Menu.Item 
                onPress={() => {
                  setMenuVisible(false);
                  Linking.openSettings();
                }} 
                title="Device Settings" 
                leadingIcon="cellphone-cog"
              />
            </Menu>
          )}
        />
        <Card.Content>
          <List.Item
            title="Security Level"
            description="Overall account security"
            right={() => (
              <Chip 
                mode="flat"
                style={{ backgroundColor: getSecurityLevelColor(securityLevel as any) }}
                textStyle={{ color: theme.colors.onPrimary }}
              >
                {securityLevel.toUpperCase()}
              </Chip>
            )}
          />

          <List.Item
            title="PIN Protection"
            description={isPinSetup ? 'PIN is set up' : 'No PIN configured'}
            left={(props) => (
              <List.Icon 
                {...props} 
                icon={isPinSetup ? 'lock' : 'lock-open-variant'} 
                color={isPinSetup ? theme.colors.success : theme.colors.error}
              />
            )}
          />

          <List.Item
            title="Two-Factor Authentication"
            description={isMfaEnabled ? 'Enabled' : 'Not enabled'}
            left={(props) => (
              <List.Icon 
                {...props} 
                icon="shield-check" 
                color={isMfaEnabled ? theme.colors.success : theme.colors.outline}
              />
            )}
          />

          <List.Item
            title="Device Trust"
            description={deviceTrusted ? 'This device is trusted' : 'Device not verified'}
            left={(props) => (
              <List.Icon 
                {...props} 
                icon={deviceTrusted ? 'cellphone-check' : 'cellphone-remove'} 
                color={deviceTrusted ? theme.colors.success : theme.colors.warning}
              />
            )}
            onPress={onNavigateToDeviceManagement}
          />
        </Card.Content>
      </Card>

      {/* Security Assessment */}
      {renderSecurityAssessment()}

      {/* Available Biometric Providers */}
      {availableProviders.length > 0 && (
        <Card style={styles.sectionCard}>
          <Card.Title 
            title="Biometric Providers"
            subtitle="Available authentication methods"
          />
          <Card.Content>
            <View style={styles.providersContainer}>
              {availableProviders.map((provider, index) => (
                <Chip
                  key={provider}
                  mode="outlined"
                  style={styles.providerChip}
                  icon={getProviderIcon(provider)}
                >
                  {getProviderDisplayName(provider)}
                </Chip>
              ))}
            </View>
            <HelperText type="info" visible={true} style={styles.providerHelperText}>
              Your device supports {availableProviders.length} biometric authentication method{availableProviders.length > 1 ? 's' : ''}
            </HelperText>
          </Card.Content>
        </Card>
      )}

      {/* Advanced Settings */}
      {localSettings && (
        <Card style={styles.sectionCard}>
          <Card.Title 
            title="Advanced Settings"
            subtitle="Additional security options"
          />
          <Card.Content>
            <List.Item
              title="Allow Screenshots"
              description="Permit screenshots in sensitive screens"
              right={() => (
                <Switch
                  value={localSettings.allowScreenshots || false}
                  onValueChange={(value) => 
                    handleSettingUpdate('allowScreenshots', value)
                  }
                />
              )}
            />

            <List.Item
              title="Log Security Events"
              description="Track authentication attempts"
              right={() => (
                <Switch
                  value={localSettings.logBiometricEvents || false}
                  onValueChange={(value) => 
                    handleSettingUpdate('logBiometricEvents', value)
                  }
                />
              )}
            />

            <List.Item
              title="Re-authentication Interval"
              description={`Require re-auth every ${localSettings.reauthenticationInterval / 60000} minutes`}
              onPress={() => setDialogVisible(true)}
            />
          </Card.Content>
        </Card>
      )}

      {/* PIN Verification Dialog */}
      <Portal>
        <Dialog 
          visible={pinDialogVisible} 
          onDismiss={() => {
            setPinDialogVisible(false);
            setCurrentPin('');
            setPinError('');
          }}
        >
          <Dialog.Title>Verify PIN</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium" style={styles.dialogText}>
              Please enter your PIN to disable biometric authentication
            </Text>
            <TextInput
              label="PIN"
              value={currentPin}
              onChangeText={setCurrentPin}
              secureTextEntry
              keyboardType="numeric"
              maxLength={8}
              error={!!pinError}
              style={styles.pinInput}
            />
            <HelperText type="error" visible={!!pinError}>
              {pinError}
            </HelperText>
          </Dialog.Content>
          <Dialog.Actions>
            <Button 
              onPress={() => {
                setPinDialogVisible(false);
                setCurrentPin('');
                setPinError('');
              }}
            >
              Cancel
            </Button>
            <Button 
              onPress={handlePinVerification}
              loading={isProcessing}
              disabled={currentPin.length < 4}
            >
              Verify
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      {/* Re-authentication Interval Dialog */}
      <Portal>
        <Dialog 
          visible={dialogVisible} 
          onDismiss={() => setDialogVisible(false)}
        >
          <Dialog.Title>Re-authentication Interval</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium" style={styles.dialogText}>
              Choose how often to require re-authentication
            </Text>
            {[5, 15, 30, 60].map((minutes) => (
              <List.Item
                key={minutes}
                title={`${minutes} minutes`}
                onPress={() => {
                  handleSettingUpdate('reauthenticationInterval', minutes * 60000);
                  setDialogVisible(false);
                }}
                left={(props) => 
                  localSettings?.reauthenticationInterval === minutes * 60000 ? (
                    <List.Icon {...props} icon="check" color={theme.colors.primary} />
                  ) : null
                }
              />
            ))}
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDialogVisible(false)}>Cancel</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  centerContent: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    color: '#666',
  },
  sectionCard: {
    margin: 16,
    marginBottom: 8,
  },
  assessmentCard: {
    margin: 16,
    marginBottom: 8,
    backgroundColor: '#f8f9fa',
  },
  divider: {
    marginVertical: 8,
  },
  reconfigureButton: {
    marginTop: 16,
    marginHorizontal: 16,
  },
  unavailableText: {
    color: '#666',
    textAlign: 'center',
    paddingVertical: 16,
  },
  errorText: {
    marginTop: 8,
  },
  scoreContainer: {
    marginBottom: 16,
  },
  scoreRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginTop: 8,
  },
  scoreText: {
    fontWeight: 'bold',
  },
  scoreMax: {
    marginLeft: 4,
    color: '#999',
  },
  progressBar: {
    marginTop: 8,
    height: 8,
    borderRadius: 4,
  },
  riskContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  riskChip: {
    paddingHorizontal: 8,
  },
  sectionTitle: {
    marginTop: 16,
    marginBottom: 8,
    fontWeight: 'bold',
  },
  threatsContainer: {
    marginBottom: 16,
  },
  threatItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 4,
  },
  threatIcon: {
    margin: 0,
    marginRight: 8,
  },
  threatText: {
    flex: 1,
    color: '#d32f2f',
  },
  recommendationsContainer: {
    marginBottom: 8,
  },
  recommendationItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 4,
  },
  recommendationIcon: {
    margin: 0,
    marginRight: 8,
  },
  recommendationText: {
    flex: 1,
    color: '#1976d2',
  },
  dialogText: {
    marginBottom: 16,
  },
  pinInput: {
    marginTop: 8,
  },
  providersContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 8,
  },
  providerChip: {
    marginRight: 8,
    marginBottom: 8,
  },
  providerHelperText: {
    marginTop: 8,
  },
});

export default BiometricSecuritySettings;