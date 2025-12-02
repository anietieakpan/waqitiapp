import React from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Linking,
} from 'react-native';
import {
  Text,
  useTheme,
  Surface,
  IconButton,
  Divider,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import DeviceInfo from 'react-native-device-info';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Header from '../components/common/Header';

interface InfoItem {
  label: string;
  value: string;
}

interface LinkItem {
  title: string;
  icon: string;
  url: string;
}

/**
 * About Screen - App information, version, and links
 */
const AboutScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const appVersion = DeviceInfo.getVersion();
  const buildNumber = DeviceInfo.getBuildNumber();
  const appName = DeviceInfo.getApplicationName();

  const appInfo: InfoItem[] = [
    { label: 'Version', value: appVersion },
    { label: 'Build', value: buildNumber },
    { label: 'Platform', value: 'React Native' },
  ];

  const links: LinkItem[] = [
    {
      title: 'Privacy Policy',
      icon: 'shield-account',
      url: 'https://waqiti.com/privacy',
    },
    {
      title: 'Terms of Service',
      icon: 'file-document',
      url: 'https://waqiti.com/terms',
    },
    {
      title: 'Support Center',
      icon: 'help-circle',
      url: 'https://help.example.com',
    },
    {
      title: 'Community',
      icon: 'forum',
      url: 'https://community.example.com',
    },
    {
      title: 'Website',
      icon: 'web',
      url: 'https://waqiti.com',
    },
  ];

  const developers = [
    { name: 'Waqiti Development Team', role: 'Core Development' },
    { name: 'Security Partners', role: 'Security & Compliance' },
    { name: 'UX/UI Team', role: 'Design & Experience' },
  ];

  const handleLinkPress = async (url: string) => {
    try {
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
      }
    } catch (error) {
      console.error('Failed to open URL:', error);
    }
  };

  const renderInfoItem = (item: InfoItem, index: number) => (
    <View key={index} style={styles.infoItem}>
      <Text style={styles.infoLabel}>{item.label}:</Text>
      <Text style={styles.infoValue}>{item.value}</Text>
    </View>
  );

  const renderLinkItem = (link: LinkItem) => (
    <TouchableOpacity
      key={link.title}
      style={styles.linkItem}
      onPress={() => handleLinkPress(link.url)}
    >
      <Icon name={link.icon} size={24} color={theme.colors.primary} />
      <View style={styles.linkContent}>
        <Text style={styles.linkTitle}>{link.title}</Text>
        <Text style={styles.linkUrl}>{link.url}</Text>
      </View>
      <Icon name="chevron-right" size={20} color="#666" />
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      <Header
        title="About Waqiti"
        leftAction={
          <IconButton
            icon="arrow-left"
            size={24}
            onPress={() => navigation.goBack()}
          />
        }
      />

      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* App Logo and Info */}
        <Surface style={styles.logoCard} elevation={2}>
          <View style={styles.logoContainer}>
            <Surface style={styles.logoIcon} elevation={4}>
              <Icon name="wallet" size={60} color={theme.colors.primary} />
            </Surface>
            <Text style={styles.appTitle}>{appName || 'Waqiti'}</Text>
            <Text style={styles.appTagline}>
              Your Smart Payment Companion
            </Text>
          </View>

          <View style={styles.appInfoGrid}>
            {appInfo.map(renderInfoItem)}
          </View>
        </Surface>

        {/* Description */}
        <Surface style={styles.descriptionCard} elevation={2}>
          <Text style={styles.sectionTitle}>About This App</Text>
          <Text style={styles.description}>
            Waqiti is a modern peer-to-peer payment application that makes sending, 
            receiving, and managing money simple and secure. With advanced security 
            features, social payment options, and seamless integration with your 
            financial ecosystem, Waqiti puts the power of digital payments in your hands.
          </Text>
        </Surface>

        {/* Features */}
        <Surface style={styles.featuresCard} elevation={2}>
          <Text style={styles.sectionTitle}>Key Features</Text>
          <View style={styles.featuresList}>
            <View style={styles.featureItem}>
              <Icon name="send" size={20} color="#4CAF50" />
              <Text style={styles.featureText}>Instant money transfers</Text>
            </View>
            <View style={styles.featureItem}>
              <Icon name="shield-check" size={20} color="#4CAF50" />
              <Text style={styles.featureText}>Bank-level security</Text>
            </View>
            <View style={styles.featureItem}>
              <Icon name="account-group" size={20} color="#4CAF50" />
              <Text style={styles.featureText}>Social payment features</Text>
            </View>
            <View style={styles.featureItem}>
              <Icon name="qrcode-scan" size={20} color="#4CAF50" />
              <Text style={styles.featureText}>QR code payments</Text>
            </View>
            <View style={styles.featureItem}>
              <Icon name="chart-line" size={20} color="#4CAF50" />
              <Text style={styles.featureText}>Expense tracking</Text>
            </View>
            <View style={styles.featureItem}>
              <Icon name="contactless-payment" size={20} color="#4CAF50" />
              <Text style={styles.featureText}>Contactless payments</Text>
            </View>
          </View>
        </Surface>

        {/* Links */}
        <Surface style={styles.linksCard} elevation={2}>
          <Text style={styles.sectionTitle}>Helpful Links</Text>
          <View style={styles.linksList}>
            {links.map(renderLinkItem)}
          </View>
        </Surface>

        {/* Development Team */}
        <Surface style={styles.teamCard} elevation={2}>
          <Text style={styles.sectionTitle}>Development Team</Text>
          <View style={styles.teamList}>
            {developers.map((dev, index) => (
              <View key={index} style={styles.teamMember}>
                <Text style={styles.memberName}>{dev.name}</Text>
                <Text style={styles.memberRole}>{dev.role}</Text>
              </View>
            ))}
          </View>
        </Surface>

        {/* Legal & Acknowledgments */}
        <Surface style={styles.legalCard} elevation={1}>
          <Text style={styles.sectionTitle}>Legal & Acknowledgments</Text>
          <Text style={styles.legalText}>
            © 2024 Waqiti. All rights reserved.
          </Text>
          <Text style={styles.legalText}>
            This app uses open-source libraries and third-party services. 
            We appreciate the contributions of the open-source community.
          </Text>
          
          <Divider style={styles.divider} />
          
          <View style={styles.acknowledgmentsList}>
            <Text style={styles.acknowledgmentTitle}>Special Thanks To:</Text>
            <Text style={styles.acknowledgmentItem}>• React Native Community</Text>
            <Text style={styles.acknowledgmentItem}>• Material Design Team</Text>
            <Text style={styles.acknowledgmentItem}>• Financial Security Partners</Text>
            <Text style={styles.acknowledgmentItem}>• Beta Testing Community</Text>
          </View>
        </Surface>

        {/* Contact Info */}
        <Surface style={styles.contactCard} elevation={2}>
          <View style={styles.contactHeader}>
            <Icon name="email" size={24} color={theme.colors.primary} />
            <Text style={styles.sectionTitle}>Get in Touch</Text>
          </View>
          <Text style={styles.contactText}>
            Questions, feedback, or need support?
          </Text>
          <TouchableOpacity
            style={styles.contactButton}
            onPress={() => handleLinkPress('mailto:support@example.com')}
          >
            <Text style={styles.contactButtonText}>support@example.com</Text>
          </TouchableOpacity>
        </Surface>

        {/* Build Info */}
        <View style={styles.buildInfo}>
          <Text style={styles.buildInfoText}>
            Built with ❤️ for secure financial transactions
          </Text>
          <Text style={styles.buildInfoText}>
            Version {appVersion} ({buildNumber})
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 32,
  },
  logoCard: {
    borderRadius: 16,
    padding: 24,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  logoContainer: {
    alignItems: 'center',
    marginBottom: 24,
  },
  logoIcon: {
    width: 100,
    height: 100,
    borderRadius: 50,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f0f8ff',
    marginBottom: 16,
  },
  appTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  appTagline: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
  },
  appInfoGrid: {
    gap: 12,
  },
  infoItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 4,
  },
  infoLabel: {
    fontSize: 14,
    color: '#666',
  },
  infoValue: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
  descriptionCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  description: {
    fontSize: 16,
    color: '#666',
    lineHeight: 24,
  },
  featuresCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  featuresList: {
    gap: 12,
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  featureText: {
    marginLeft: 12,
    fontSize: 16,
    color: '#333',
  },
  linksCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  linksList: {
    gap: 4,
  },
  linkItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 4,
  },
  linkContent: {
    flex: 1,
    marginLeft: 12,
  },
  linkTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  linkUrl: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  teamCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  teamList: {
    gap: 12,
  },
  teamMember: {
    paddingVertical: 8,
  },
  memberName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  memberRole: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  legalCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: '#f8f8f8',
  },
  legalText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 8,
  },
  divider: {
    marginVertical: 16,
  },
  acknowledgmentsList: {
    gap: 8,
  },
  acknowledgmentTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 8,
  },
  acknowledgmentItem: {
    fontSize: 14,
    color: '#666',
  },
  contactCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  contactHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  contactText: {
    fontSize: 16,
    color: '#666',
    marginBottom: 16,
  },
  contactButton: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#e3f2fd',
    borderRadius: 8,
    alignItems: 'center',
  },
  contactButtonText: {
    fontSize: 16,
    color: '#2196F3',
    fontWeight: '500',
  },
  buildInfo: {
    alignItems: 'center',
    marginTop: 16,
    gap: 4,
  },
  buildInfoText: {
    fontSize: 12,
    color: '#999',
    textAlign: 'center',
  },
});

export default AboutScreen;