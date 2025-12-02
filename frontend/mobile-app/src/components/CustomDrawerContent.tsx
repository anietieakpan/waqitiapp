import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  Image,
  TouchableOpacity,
  ScrollView,
  Alert,
} from 'react-native';
import {
  DrawerContentScrollView,
  DrawerContentComponentProps,
} from '@react-navigation/drawer';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import { logout } from '../store/slices/authSlice';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * CustomDrawerContent Component
 *
 * Custom drawer navigation content with user profile, menu items, and settings
 *
 * Features:
 * - User profile display
 * - Navigation menu items
 * - Logout functionality
 * - Badge support
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface DrawerMenuItem {
  label: string;
  icon: string;
  screen: string;
  badge?: string | number;
  badgeColor?: string;
}

const CustomDrawerContent: React.FC<DrawerContentComponentProps> = (props) => {
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);
  const { balance } = useSelector((state: RootState) => state.wallet);
  const { unreadCount } = useSelector((state: RootState) => state.notifications || { unreadCount: 0 });

  const menuItems: DrawerMenuItem[] = [
    {
      label: 'Home',
      icon: 'home',
      screen: 'Home',
    },
    {
      label: 'Activity',
      icon: 'swap-horizontal',
      screen: 'Activity',
    },
    {
      label: 'Wallet',
      icon: 'wallet',
      screen: 'Wallet',
    },
    {
      label: 'Contacts',
      icon: 'account-multiple',
      screen: 'Contacts',
    },
    {
      label: 'Notifications',
      icon: 'bell',
      screen: 'Notifications',
      badge: unreadCount > 0 ? unreadCount : undefined,
      badgeColor: '#FF5252',
    },
    {
      label: 'Scheduled Payments',
      icon: 'calendar-clock',
      screen: 'ScheduledPayments',
    },
    {
      label: 'Business Dashboard',
      icon: 'briefcase',
      screen: 'BusinessDashboard',
    },
    {
      label: 'Rewards',
      icon: 'gift',
      screen: 'Rewards',
    },
    {
      label: 'Settings',
      icon: 'cog',
      screen: 'Settings',
    },
    {
      label: 'Help & Support',
      icon: 'help-circle',
      screen: 'HelpSupport',
    },
  ];

  const formatBalance = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const handleNavigate = (screen: string, label: string) => {
    AnalyticsService.trackEvent('drawer_navigation', {
      userId: user?.id,
      screen,
      label,
    });
    props.navigation.navigate(screen);
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
            AnalyticsService.trackEvent('user_logged_out_drawer', {
              userId: user?.id,
            });
            await dispatch(logout());
            props.navigation.navigate('Login' as never);
          },
          style: 'destructive',
        },
      ],
      { cancelable: true }
    );
  };

  const renderProfileSection = () => (
    <View style={styles.profileSection}>
      <TouchableOpacity
        style={styles.profileContainer}
        onPress={() => handleNavigate('Profile', 'Profile')}
      >
        {user?.profilePicture ? (
          <Image source={{ uri: user.profilePicture }} style={styles.avatar} />
        ) : (
          <View style={[styles.avatar, styles.avatarPlaceholder]}>
            <Icon name="account" size={40} color="#FFFFFF" />
          </View>
        )}

        <View style={styles.profileInfo}>
          <Text style={styles.userName} numberOfLines={1}>
            {user?.fullName || 'User'}
          </Text>
          <Text style={styles.userEmail} numberOfLines={1}>
            {user?.email}
          </Text>
        </View>

        <Icon name="chevron-right" size={24} color="#FFFFFF" />
      </TouchableOpacity>

      <View style={styles.balanceContainer}>
        <Icon name="wallet" size={20} color="#FFFFFF" />
        <Text style={styles.balanceLabel}>Balance</Text>
        <Text style={styles.balanceAmount}>{formatBalance(balance || 0)}</Text>
      </View>
    </View>
  );

  const renderMenuItem = (item: DrawerMenuItem, index: number) => {
    const isActive = props.state.routeNames[props.state.index] === item.screen;

    return (
      <TouchableOpacity
        key={index}
        style={[styles.menuItem, isActive && styles.menuItemActive]}
        onPress={() => handleNavigate(item.screen, item.label)}
      >
        <Icon
          name={item.icon}
          size={24}
          color={isActive ? '#6200EE' : '#666'}
        />
        <Text
          style={[
            styles.menuItemLabel,
            isActive && styles.menuItemLabelActive,
          ]}
        >
          {item.label}
        </Text>

        {item.badge && (
          <View
            style={[
              styles.badge,
              { backgroundColor: item.badgeColor || '#FF5252' },
            ]}
          >
            <Text style={styles.badgeText}>{item.badge}</Text>
          </View>
        )}
      </TouchableOpacity>
    );
  };

  return (
    <DrawerContentScrollView
      {...props}
      contentContainerStyle={styles.container}
    >
      {renderProfileSection()}

      <View style={styles.menuContainer}>
        {menuItems.map((item, index) => renderMenuItem(item, index))}
      </View>

      <View style={styles.footer}>
        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <Icon name="logout" size={20} color="#F44336" />
          <Text style={styles.logoutText}>Logout</Text>
        </TouchableOpacity>

        <Text style={styles.versionText}>Waqiti v1.0.0</Text>
      </View>
    </DrawerContentScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  profileSection: {
    backgroundColor: '#6200EE',
    paddingVertical: 24,
    paddingHorizontal: 16,
  },
  profileContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  avatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  avatarPlaceholder: {
    backgroundColor: '#7E3FF2',
    justifyContent: 'center',
    alignItems: 'center',
  },
  profileInfo: {
    flex: 1,
    marginLeft: 12,
  },
  userName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 4,
  },
  userEmail: {
    fontSize: 14,
    color: '#FFFFFF',
    opacity: 0.9,
  },
  balanceContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
  },
  balanceLabel: {
    fontSize: 12,
    color: '#FFFFFF',
    marginLeft: 6,
    opacity: 0.9,
  },
  balanceAmount: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginLeft: 'auto',
  },
  menuContainer: {
    flex: 1,
    paddingVertical: 8,
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    paddingHorizontal: 16,
    marginHorizontal: 8,
    borderRadius: 8,
  },
  menuItemActive: {
    backgroundColor: '#F3E5F5',
  },
  menuItemLabel: {
    fontSize: 16,
    color: '#666',
    marginLeft: 16,
    flex: 1,
  },
  menuItemLabelActive: {
    color: '#6200EE',
    fontWeight: '600',
  },
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 6,
  },
  badgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: 'bold',
  },
  footer: {
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  logoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 8,
    backgroundColor: '#FFEBEE',
    marginBottom: 12,
  },
  logoutText: {
    color: '#F44336',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  versionText: {
    fontSize: 12,
    color: '#999',
    textAlign: 'center',
  },
});

export default CustomDrawerContent;
