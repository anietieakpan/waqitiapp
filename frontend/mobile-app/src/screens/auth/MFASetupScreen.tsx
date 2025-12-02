import React, { useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import {
  Text,
  Button,
  useTheme,
  Surface,
  IconButton,
  Divider,
  Switch,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import QRCode from 'react-native-qrcode-svg';
import Header from '../../components/common/Header';

interface MFAMethod {
  id: string;
  title: string;
  description: string;
  icon: string;
  enabled: boolean;
  isRecommended?: boolean;
}

/**
 * MFA Setup Screen - Multi-factor authentication setup
 */
const MFASetupScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [selectedMethod, setSelectedMethod] = useState<string | null>(null);
  const [methods, setMethods] = useState<MFAMethod[]>([
    {
      id: 'sms',
      title: 'SMS Authentication',
      description: 'Receive codes via text message',
      icon: 'message-text',
      enabled: false,
      isRecommended: true,
    },
    {
      id: 'email',
      title: 'Email Authentication',
      description: 'Receive codes via email',
      icon: 'email',
      enabled: false,
    },
    {
      id: 'totp',
      title: 'Authenticator App',
      description: 'Use Google Authenticator or similar apps',
      icon: 'shield-key',
      enabled: false,
      isRecommended: true,
    },
  ]);

  // TOTP secret from backend API (no hardcoded values)
  const [totpSecret, setTotpSecret] = useState<string>('');
  const [totpQRData, setTotpQRData] = useState<string>('');
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const handleMethodToggle = (methodId: string) => {
    setMethods(prev =>
      prev.map(method =>
        method.id === methodId
          ? { ...method, enabled: !method.enabled }
          : method
      )
    );
  };

  const handleSetupMethod = (methodId: string) => {
    setSelectedMethod(methodId);
    navigation.navigate('MFAVerification', { method: methodId } as never);
  };

  const handleSkip = () => {
    navigation.navigate('Main' as never);
  };

  const handleContinue = () => {
    const enabledMethods = methods.filter(method => method.enabled);
    if (enabledMethods.length > 0) {
      // Save MFA settings
      console.log('MFA methods enabled:', enabledMethods);
      navigation.navigate('Main' as never);
    }
  };

  const renderMethodCard = (method: MFAMethod) => (
    <Surface key={method.id} style={styles.methodCard} elevation={2}>
      <View style={styles.methodHeader}>
        <View style={styles.methodInfo}>
          <View style={styles.methodIconContainer}>
            <Icon name={method.icon} size={24} color={theme.colors.primary} />
            {method.isRecommended && (
              <View style={styles.recommendedBadge}>
                <Text style={styles.recommendedText}>Recommended</Text>
              </View>
            )}
          </View>
          <View style={styles.methodDetails}>
            <Text style={styles.methodTitle}>{method.title}</Text>
            <Text style={styles.methodDescription}>{method.description}</Text>
          </View>
        </View>
        
        <Switch
          value={method.enabled}
          onValueChange={() => handleMethodToggle(method.id)}
        />
      </View>

      {method.enabled && (
        <View style={styles.methodContent}>
          <Divider style={styles.methodDivider} />
          
          {method.id === 'totp' && (
            <View style={styles.totpSetup}>
              <Text style={styles.setupStepTitle}>
                1. Install an authenticator app
              </Text>
              <Text style={styles.setupStepDescription}>
                Download Google Authenticator, Authy, or any compatible TOTP app
              </Text>
              
              <Text style={styles.setupStepTitle}>
                2. Scan this QR code
              </Text>
              <View style={styles.qrContainer}>
                <QRCode
                  value={totpQRData}
                  size={150}
                  backgroundColor="white"
                  color="black"
                />
              </View>
              
              <Text style={styles.setupStepTitle}>
                3. Or enter this code manually
              </Text>
              <Surface style={styles.secretContainer} elevation={1}>
                <Text style={styles.secretText}>{totpSecret}</Text>
                <IconButton
                  icon="content-copy"
                  size={20}
                  onPress={() => {
                    // Copy to clipboard
                    console.log('Copy secret to clipboard');
                  }}
                />
              </Surface>
            </View>
          )}

          {method.id === 'sms' && (
            <View style={styles.smsSetup}>
              <Text style={styles.setupStepDescription}>
                We'll send a verification code to your registered phone number
              </Text>
              <Surface style={styles.phoneContainer} elevation={1}>
                <Icon name="phone" size={20} color={theme.colors.primary} />
                <Text style={styles.phoneText}>+1 (555) 123-4567</Text>
                <TouchableOpacity style={styles.changeButton}>
                  <Text style={styles.changeButtonText}>Change</Text>
                </TouchableOpacity>
              </Surface>
            </View>
          )}

          {method.id === 'email' && (
            <View style={styles.emailSetup}>
              <Text style={styles.setupStepDescription}>
                We'll send a verification code to your registered email
              </Text>
              <Surface style={styles.emailContainer} elevation={1}>
                <Icon name="email" size={20} color={theme.colors.primary} />
                <Text style={styles.emailText}>user@example.com</Text>
                <TouchableOpacity style={styles.changeButton}>
                  <Text style={styles.changeButtonText}>Change</Text>
                </TouchableOpacity>
              </Surface>
            </View>
          )}

          <Button
            mode="contained"
            onPress={() => handleSetupMethod(method.id)}
            style={styles.setupButton}
          >
            Setup {method.title}
          </Button>
        </View>
      )}
    </Surface>
  );

  const enabledCount = methods.filter(method => method.enabled).length;

  return (
    <View style={styles.container}>
      <LinearGradient
        colors={[theme.colors.primary, theme.colors.secondary]}
        style={styles.headerGradient}
      >
        <SafeAreaView>
          <Header
            title="Multi-Factor Authentication"
            leftAction={
              <IconButton
                icon="arrow-left"
                iconColor="white"
                size={24}
                onPress={() => navigation.goBack()}
              />
            }
          />
          
          <View style={styles.headerContent}>
            <Surface style={styles.iconContainer} elevation={4}>
              <Icon name="shield-lock" size={60} color={theme.colors.primary} />
            </Surface>
            <Text style={styles.title}>Secure Your Account</Text>
            <Text style={styles.subtitle}>
              Add an extra layer of security to protect your account and transactions
            </Text>
          </View>
        </SafeAreaView>
      </LinearGradient>

      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Security Benefits */}
        <Surface style={styles.benefitsCard} elevation={2}>
          <Text style={styles.benefitsTitle}>Why enable MFA?</Text>
          <View style={styles.benefitsList}>
            <View style={styles.benefitItem}>
              <Icon name="check-circle" size={20} color="#4CAF50" />
              <Text style={styles.benefitText}>
                Protect against unauthorized access
              </Text>
            </View>
            <View style={styles.benefitItem}>
              <Icon name="check-circle" size={20} color="#4CAF50" />
              <Text style={styles.benefitText}>
                Secure your transactions and sensitive data
              </Text>
            </View>
            <View style={styles.benefitItem}>
              <Icon name="check-circle" size={20} color="#4CAF50" />
              <Text style={styles.benefitText}>
                Comply with banking security standards
              </Text>
            </View>
          </View>
        </Surface>

        {/* MFA Methods */}
        <View style={styles.methodsSection}>
          <Text style={styles.sectionTitle}>Choose your methods</Text>
          <Text style={styles.sectionSubtitle}>
            Select one or more authentication methods
          </Text>
          
          <View style={styles.methodsList}>
            {methods.map(renderMethodCard)}
          </View>
        </View>

        {/* Recovery Options */}
        <Surface style={styles.recoveryCard} elevation={1}>
          <View style={styles.recoveryHeader}>
            <Icon name="backup-restore" size={24} color="#FF9800" />
            <Text style={styles.recoveryTitle}>Recovery Options</Text>
          </View>
          <Text style={styles.recoveryDescription}>
            Make sure you have backup recovery codes saved in a secure location. 
            These can be used if you lose access to your primary MFA method.
          </Text>
          <Button
            mode="outlined"
            onPress={() => {
              // Handle backup codes generation
              console.log('Generate backup codes');
            }}
            style={styles.backupButton}
          >
            Generate Backup Codes
          </Button>
        </Surface>
      </ScrollView>

      {/* Bottom Actions */}
      <View style={styles.bottomContainer}>
        <View style={styles.statusContainer}>
          <Text style={styles.statusText}>
            {enabledCount === 0 
              ? 'No methods enabled' 
              : `${enabledCount} method${enabledCount > 1 ? 's' : ''} enabled`
            }
          </Text>
        </View>
        
        <View style={styles.buttonContainer}>
          <Button
            mode="text"
            onPress={handleSkip}
            style={styles.skipButton}
          >
            Skip for now
          </Button>
          <Button
            mode="contained"
            onPress={handleContinue}
            disabled={enabledCount === 0}
            style={styles.continueButton}
          >
            Continue
          </Button>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  headerGradient: {
    paddingBottom: 20,
  },
  headerContent: {
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingTop: 20,
  },
  iconContainer: {
    width: 100,
    height: 100,
    borderRadius: 50,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'white',
    marginBottom: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.9)',
    textAlign: 'center',
    lineHeight: 24,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 120,
  },
  benefitsCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 24,
    backgroundColor: 'white',
  },
  benefitsTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 16,
  },
  benefitsList: {
    gap: 12,
  },
  benefitItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  benefitText: {
    marginLeft: 12,
    fontSize: 14,
    color: '#666',
    flex: 1,
  },
  methodsSection: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  sectionSubtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 16,
  },
  methodsList: {
    gap: 16,
  },
  methodCard: {
    borderRadius: 12,
    backgroundColor: 'white',
    overflow: 'hidden',
  },
  methodHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
  },
  methodInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  methodIconContainer: {
    marginRight: 12,
    position: 'relative',
  },
  recommendedBadge: {
    position: 'absolute',
    top: -8,
    right: -8,
    backgroundColor: '#4CAF50',
    borderRadius: 8,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  recommendedText: {
    fontSize: 10,
    color: 'white',
    fontWeight: '500',
  },
  methodDetails: {
    flex: 1,
  },
  methodTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 4,
  },
  methodDescription: {
    fontSize: 14,
    color: '#666',
  },
  methodContent: {
    paddingHorizontal: 16,
    paddingBottom: 16,
  },
  methodDivider: {
    marginBottom: 16,
  },
  setupStepTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
    marginTop: 16,
  },
  setupStepDescription: {
    fontSize: 14,
    color: '#666',
    marginBottom: 12,
    lineHeight: 20,
  },
  totpSetup: {
    alignItems: 'center',
  },
  qrContainer: {
    padding: 20,
    backgroundColor: 'white',
    borderRadius: 12,
    marginVertical: 16,
    elevation: 2,
  },
  secretContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    marginTop: 8,
    marginBottom: 16,
  },
  secretText: {
    flex: 1,
    fontFamily: 'monospace',
    fontSize: 16,
    color: '#333',
  },
  smsSetup: {
    marginBottom: 16,
  },
  phoneContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#f8f8f8',
    borderRadius: 8,
    marginTop: 8,
  },
  phoneText: {
    flex: 1,
    marginLeft: 8,
    fontSize: 14,
    color: '#333',
  },
  changeButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#e3f2fd',
  },
  changeButtonText: {
    fontSize: 12,
    color: '#2196F3',
    fontWeight: '500',
  },
  emailSetup: {
    marginBottom: 16,
  },
  emailContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#f8f8f8',
    borderRadius: 8,
    marginTop: 8,
  },
  emailText: {
    flex: 1,
    marginLeft: 8,
    fontSize: 14,
    color: '#333',
  },
  setupButton: {
    marginTop: 8,
  },
  recoveryCard: {
    borderRadius: 12,
    padding: 16,
    backgroundColor: '#fff8e1',
    borderColor: '#FFE082',
    borderWidth: 1,
  },
  recoveryHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  recoveryTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginLeft: 8,
  },
  recoveryDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 16,
  },
  backupButton: {
    borderColor: '#FF9800',
  },
  bottomContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'white',
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
  },
  statusContainer: {
    alignItems: 'center',
    marginBottom: 12,
  },
  statusText: {
    fontSize: 14,
    color: '#666',
  },
  buttonContainer: {
    flexDirection: 'row',
    gap: 12,
  },
  skipButton: {
    flex: 1,
  },
  continueButton: {
    flex: 1,
  },
});

export default MFASetupScreen;