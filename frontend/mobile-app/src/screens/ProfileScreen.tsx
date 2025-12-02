import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Image,
  Alert,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import { logout, fetchUserProfile } from '../store/slices/authSlice';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * ProfileScreen - User Profile and Settings
 *
 * Features:
 * - User profile information display
 * - Account statistics (balance, transactions, contacts)
 * - Quick access to settings
 * - Profile editing
 * - Logout functionality
 * - KYC verification status
 * - Redux integration for user state
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const ProfileScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();

  // Redux state
  const { user, loading } = useSelector((state: RootState) => state.auth);
  const { balance } = useSelector((state: RootState) => state.wallet);
  const { transactions } = useSelector((state: RootState) => state.transactions);

  // Local state
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    AnalyticsService.trackScreenView('ProfileScreen');
  }, []);

  // Calculate user statistics
  const stats = {
    totalTransactions: transactions?.length || 0,
    monthlyTransactions: transactions?.filter((t: any) => {
      const transactionDate = new Date(t.createdAt);
      const now = new Date();
      return (
        transactionDate.getMonth() === now.getMonth() &&
        transactionDate.getFullYear() === now.getFullYear()
      );
    }).length || 0,
    accountAge: user?.createdAt
      ? Math.floor((Date.now() - new Date(user.createdAt).getTime()) / (1000 * 60 * 60 * 24))
      : 0,
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    AnalyticsService.trackEvent('profile_refreshed', {
      userId: user?.id,
    });
    try {
      await dispatch(fetchUserProfile()).unwrap();
    } catch (error) {
      Alert.alert('Error', 'Failed to refresh profile');
    } finally {
      setRefreshing(false);
    }
  };

  const handleEditProfile = () => {
    AnalyticsService.trackEvent('edit_profile_clicked', {
      userId: user?.id,
    });
    navigation.navigate('EditProfile' as never);
  };

  const handleLogout = () => {
    Alert.alert(
      'Logout',
      'Are you sure you want to logout?',
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Logout',
          onPress: async () => {
            AnalyticsService.trackEvent('user_logged_out', {
              userId: user?.id,
            });
            await dispatch(logout());
            navigation.navigate('Login' as never);
          },
          style: 'destructive',
        },
      ],
      { cancelable: true }
    );
  };

  const handleNavigateToSettings = (settingsType: string, screen: string) => {
    AnalyticsService.trackEvent('settings_navigation', {
      userId: user?.id,
      settingsType,
      screen,
    });
    navigation.navigate(screen as never);
  };

  const handleKYCVerification = () => {
    AnalyticsService.trackEvent('kyc_verification_clicked', {
      userId: user?.id,
      kycStatus: user?.kycStatus || 'not_started',
    });
    navigation.navigate('KYCVerification' as never);
  };

  const getKYCStatusColor = () => {
    switch (user?.kycStatus) {
      case 'verified':
        return '#4CAF50';
      case 'pending':
        return '#FF9800';
      case 'rejected':
        return '#F44336';
      default:
        return '#9E9E9E';
    }
  };

  const getKYCStatusText = () => {
    switch (user?.kycStatus) {
      case 'verified':
        return 'Verified';
      case 'pending':
        return 'Pending Review';
      case 'rejected':
        return 'Rejected';
      default:
        return 'Not Verified';
    }
  };

  const formatBalance = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const renderProfileHeader = () => (
    <View style={styles.profileHeader}>
      <View style={styles.avatarContainer}>
        {user?.profilePicture ? (
          <Image source={{ uri: user.profilePicture }} style={styles.avatar} />
        ) : (
          <View style={[styles.avatar, styles.avatarPlaceholder]}>
            <Icon name="account" size={60} color="#FFFFFF" />
          </View>
        )}
        <TouchableOpacity style={styles.editAvatarButton} onPress={handleEditProfile}>
          <Icon name="camera" size={20} color="#FFFFFF" />
        </TouchableOpacity>
      </View>

      <Text style={styles.userName}>{user?.fullName || 'Unknown User'}</Text>
      <Text style={styles.userEmail}>{user?.email}</Text>
      <Text style={styles.userPhone}>{user?.phoneNumber}</Text>

      <TouchableOpacity
        style={[styles.kycBadge, { backgroundColor: getKYCStatusColor() }]}
        onPress={handleKYCVerification}
      >
        <Icon
          name={user?.kycStatus === 'verified' ? 'shield-check' : 'shield-alert'}
          size={16}
          color="#FFFFFF"
        />
        <Text style={styles.kycBadgeText}>{getKYCStatusText()}</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.editProfileButton} onPress={handleEditProfile}>
        <Icon name="pencil" size={18} color="#6200EE" />
        <Text style={styles.editProfileText}>Edit Profile</Text>
      </TouchableOpacity>
    </View>
  );

  const renderStatistics = () => (
    <View style={styles.statisticsContainer}>
      <View style={styles.statCard}>
        <Icon name="wallet" size={32} color="#6200EE" />
        <Text style={styles.statValue}>{formatBalance(balance || 0)}</Text>
        <Text style={styles.statLabel}>Balance</Text>
      </View>

      <View style={styles.statCard}>
        <Icon name="swap-horizontal" size={32} color="#6200EE" />
        <Text style={styles.statValue}>{stats.totalTransactions}</Text>
        <Text style={styles.statLabel}>Transactions</Text>
      </View>

      <View style={styles.statCard}>
        <Icon name="calendar-month" size={32} color="#6200EE" />
        <Text style={styles.statValue}>{stats.monthlyTransactions}</Text>
        <Text style={styles.statLabel}>This Month</Text>
      </View>

      <View style={styles.statCard}>
        <Icon name="account-clock" size={32} color="#6200EE" />
        <Text style={styles.statValue}>{stats.accountAge}d</Text>
        <Text style={styles.statLabel}>Member Since</Text>
      </View>
    </View>
  );

  const renderSettingsSection = (title: string, items: Array<{
    icon: string;
    label: string;
    screen: string;
    badge?: string;
  }>) => (
    <View style={styles.settingsSection}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {items.map((item, index) => (
        <TouchableOpacity
          key={index}
          style={styles.settingsItem}
          onPress={() => handleNavigateToSettings(title.toLowerCase(), item.screen)}
        >
          <View style={styles.settingsItemLeft}>
            <Icon name={item.icon} size={24} color="#6200EE" />
            <Text style={styles.settingsItemLabel}>{item.label}</Text>
          </View>
          <View style={styles.settingsItemRight}>
            {item.badge && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{item.badge}</Text>
              </View>
            )}
            <Icon name="chevron-right" size={24} color="#9E9E9E" />
          </View>
        </TouchableOpacity>
      ))}
    </View>
  );

  if (loading && !user) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#6200EE" />
        <Text style={styles.loadingText}>Loading profile...</Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={handleRefresh}
          tintColor="#6200EE"
        />
      }
    >
      {renderProfileHeader()}
      {renderStatistics()}

      {renderSettingsSection('Payment', [
        { icon: 'credit-card', label: 'Payment Methods', screen: 'PaymentMethods' },
        { icon: 'cog', label: 'Payment Settings', screen: 'PaymentSettings' },
        { icon: 'history', label: 'Transaction History', screen: 'TransactionHistory' },
        { icon: 'calendar-clock', label: 'Scheduled Payments', screen: 'ScheduledPayments' },
      ])}

      {renderSettingsSection('Security', [
        { icon: 'lock', label: 'Change Password', screen: 'ChangePassword' },
        { icon: 'lock-reset', label: 'Change PIN', screen: 'ChangePin' },
        { icon: 'fingerprint', label: 'Biometric Settings', screen: 'BiometricSettings' },
        { icon: 'devices', label: 'Device Management', screen: 'DeviceManagement' },
        { icon: 'shield-check', label: 'KYC Verification', screen: 'KYCVerification',
          badge: user?.kycStatus === 'verified' ? undefined : 'Action Required' },
      ])}

      {renderSettingsSection('Account', [
        { icon: 'shield-account', label: 'Privacy Settings', screen: 'PrivacySettings' },
        { icon: 'bell', label: 'Notifications', screen: 'NotificationSettings' },
        { icon: 'translate', label: 'Language & Region', screen: 'LanguageSettings' },
        { icon: 'palette', label: 'Appearance', screen: 'AppearanceSettings' },
      ])}

      {renderSettingsSection('Business', [
        { icon: 'briefcase', label: 'Business Dashboard', screen: 'BusinessDashboard' },
        { icon: 'receipt', label: 'Invoices', screen: 'Invoices' },
        { icon: 'qrcode', label: 'QR Code Generator', screen: 'QRCodeGenerator' },
        { icon: 'chart-bar', label: 'Business Analytics', screen: 'BusinessAnalytics' },
      ])}

      {renderSettingsSection('Rewards', [
        { icon: 'gift', label: 'Rewards Program', screen: 'Rewards' },
        { icon: 'account-multiple', label: 'Referral Program', screen: 'Referral' },
        { icon: 'leaf', label: 'Carbon Footprint', screen: 'CarbonFootprint' },
      ])}

      {renderSettingsSection('Support', [
        { icon: 'help-circle', label: 'Help & Support', screen: 'HelpSupport' },
        { icon: 'information', label: 'About Waqiti', screen: 'About' },
        { icon: 'file-document', label: 'Terms & Privacy', screen: 'TermsPrivacy' },
      ])}

      <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
        <Icon name="logout" size={24} color="#F44336" />
        <Text style={styles.logoutText}>Logout</Text>
      </TouchableOpacity>

      <View style={styles.versionContainer}>
        <Text style={styles.versionText}>Waqiti v1.0.0</Text>
        <Text style={styles.versionSubtext}>Build 2025.10.23</Text>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  profileHeader: {
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    paddingVertical: 32,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  avatarContainer: {
    position: 'relative',
    marginBottom: 16,
  },
  avatar: {
    width: 120,
    height: 120,
    borderRadius: 60,
  },
  avatarPlaceholder: {
    backgroundColor: '#6200EE',
    justifyContent: 'center',
    alignItems: 'center',
  },
  editAvatarButton: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    backgroundColor: '#6200EE',
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 3,
    borderColor: '#FFFFFF',
  },
  userName: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  userEmail: {
    fontSize: 16,
    color: '#666',
    marginBottom: 2,
  },
  userPhone: {
    fontSize: 14,
    color: '#999',
    marginBottom: 12,
  },
  kycBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    marginBottom: 16,
  },
  kycBadgeText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: 'bold',
    marginLeft: 4,
  },
  editProfileButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#6200EE',
  },
  editProfileText: {
    color: '#6200EE',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  statisticsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 8,
    marginTop: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  statCard: {
    width: '50%',
    alignItems: 'center',
    paddingVertical: 16,
  },
  statValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 8,
  },
  statLabel: {
    fontSize: 12,
    color: '#999',
    marginTop: 4,
  },
  settingsSection: {
    backgroundColor: '#FFFFFF',
    marginTop: 8,
    paddingVertical: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#999',
    paddingHorizontal: 16,
    paddingVertical: 8,
    textTransform: 'uppercase',
  },
  settingsItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  settingsItemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  settingsItemLabel: {
    fontSize: 16,
    color: '#212121',
    marginLeft: 16,
  },
  settingsItemRight: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  badge: {
    backgroundColor: '#FF9800',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    marginRight: 8,
  },
  badgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: 'bold',
  },
  logoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
    marginTop: 16,
    marginHorizontal: 16,
    paddingVertical: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#F44336',
  },
  logoutText: {
    color: '#F44336',
    fontSize: 16,
    fontWeight: 'bold',
    marginLeft: 8,
  },
  versionContainer: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  versionText: {
    fontSize: 14,
    color: '#999',
  },
  versionSubtext: {
    fontSize: 12,
    color: '#BBB',
    marginTop: 4,
  },
});

export default ProfileScreen;
