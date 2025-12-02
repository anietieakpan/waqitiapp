import React, { useEffect } from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createDrawerNavigator } from '@react-navigation/drawer';
import { useSelector } from 'react-redux';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useTheme } from 'react-native-paper';

import { RootState } from '../store/store';
import { useAuth } from '../contexts/AuthContext';
import { useBiometric } from '../hooks/useBiometric';

// Auth Screens
import OnboardingScreen from '../screens/auth/OnboardingScreen';
import LoginScreen from '../screens/auth/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';
import ForgotPasswordScreen from '../screens/auth/ForgotPasswordScreen';
import ResetPasswordScreen from '../screens/auth/ResetPasswordScreen';
import VerifyEmailScreen from '../screens/auth/VerifyEmailScreen';
import SetupPinScreen from '../screens/auth/SetupPinScreen';
import BiometricSetupScreen from '../screens/auth/BiometricSetupScreen';
import MFASetupScreen from '../screens/auth/MFASetupScreen';
import MFAVerificationScreen from '../screens/auth/MFAVerificationScreen';

// Main Screens
import DashboardScreen from '../screens/main/DashboardScreen';
import WalletScreen from '../screens/wallet/WalletScreen';
import PaymentScreen from '../screens/payment/PaymentScreen';
import ActivityScreen from '../screens/activity/ActivityScreen';
import ProfileScreen from '../screens/profile/ProfileScreen';

// Payment Screens
import SendMoneyScreen from '../screens/payment/SendMoneyScreen';
import RequestMoneyScreen from '../screens/payment/RequestMoneyScreen';
import ScanQRScreen from '../screens/payment/ScanQRScreen';
import PaymentDetailsScreen from '../screens/payment/PaymentDetailsScreen';
import ContactSelectionScreen from '../screens/payment/ContactSelectionScreen';
import PaymentConfirmationScreen from '../screens/payment/PaymentConfirmationScreen';
import PaymentSuccessScreen from '../screens/payment/PaymentSuccessScreen';
import SplitBillScreen from '../screens/payment/SplitBillScreen';
import ScheduledPaymentScreen from '../screens/payment/ScheduledPaymentScreen';
import NearbyPaymentScreen from '../screens/payment/NearbyPaymentScreen';
import NFCPaymentScreen from '../screens/payment/NFCPaymentScreen';

// Social Payment Screens
import PaymentFeedScreen from '../screens/social/PaymentFeedScreen';
import PaymentCommentsScreen from '../screens/social/PaymentCommentsScreen';
import PublicFeedScreen from '../screens/social/PublicFeedScreen';
import TrendingPaymentsScreen from '../screens/social/TrendingPaymentsScreen';

// Wallet Screens
import AddMoneyScreen from '../screens/wallet/AddMoneyScreen';
import WithdrawMoneyScreen from '../screens/wallet/WithdrawMoneyScreen';
import LinkedAccountsScreen from '../screens/wallet/LinkedAccountsScreen';
import AddBankAccountScreen from '../screens/wallet/AddBankAccountScreen';
import AddCardScreen from '../screens/wallet/AddCardScreen';
import TransactionHistoryScreen from '../screens/wallet/TransactionHistoryScreen';
import TransactionDetailsScreen from '../screens/wallet/TransactionDetailsScreen';

// Profile & Settings Screens
import EditProfileScreen from '../screens/profile/EditProfileScreen';
import SecuritySettingsScreen from '../screens/settings/SecuritySettingsScreen';
import PrivacySettingsScreen from '../screens/settings/PrivacySettingsScreen';
import NotificationSettingsScreen from '../screens/settings/NotificationSettingsScreen';
import PaymentSettingsScreen from '../screens/settings/PaymentSettingsScreen';
import KYCVerificationScreen from '../screens/settings/KYCVerificationScreen';
import ChangePasswordScreen from '../screens/settings/ChangePasswordScreen';
import ChangePinScreen from '../screens/settings/ChangePinScreen';
import DeviceManagementScreen from '../screens/settings/DeviceManagementScreen';
import HelpSupportScreen from '../screens/settings/HelpSupportScreen';
import AboutScreen from '../screens/settings/AboutScreen';

// Business Account Screens
import BusinessDashboardScreen from '../screens/business/BusinessDashboardScreen';
import BusinessPaymentsScreen from '../screens/business/BusinessPaymentsScreen';
import InvoicesScreen from '../screens/business/InvoicesScreen';
import BusinessAnalyticsScreen from '../screens/business/BusinessAnalyticsScreen';
import QRCodeGeneratorScreen from '../screens/business/QRCodeGeneratorScreen';

// Additional Screens
import NotificationsScreen from '../screens/NotificationsScreen';
import SearchScreen from '../screens/SearchScreen';
import ContactsScreen from '../screens/contacts/ContactsScreen';
import UserSearchScreen from '../screens/contacts/UserSearchScreen';
import ContactDetailsScreen from '../screens/contacts/ContactDetailsScreen';
import RewardsScreen from '../screens/RewardsScreen';
import ReferralScreen from '../screens/ReferralScreen';

// Custom Drawer Content
import CustomDrawerContent from '../components/navigation/CustomDrawerContent';

// Types
import { Contact } from '../types/contact';

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
  Onboarding: undefined;
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  ResetPassword: { token: string };
  VerifyEmail: { email: string };
  SetupPin: undefined;
  BiometricSetup: undefined;
  MFASetup: undefined;
  MFAVerification: { method: 'sms' | 'email' | 'totp' };
  Dashboard: undefined;
  Wallet: undefined;
  Payment: undefined;
  Activity: undefined;
  Profile: undefined;
  SendMoney: { recipientId?: string };
  RequestMoney: { senderId?: string };
  ScanQR: undefined;
  PaymentDetails: { paymentId: string };
  ContactSelection: { action: 'send' | 'request' };
  PaymentConfirmation: { paymentData: any };
  PaymentSuccess: { paymentId: string };
  SplitBill: undefined;
  ScheduledPayment: undefined;
  NearbyPayment: undefined;
  NFCPayment: undefined;
  PaymentFeed: undefined;
  PaymentComments: { paymentId: string };
  PublicFeed: undefined;
  TrendingPayments: undefined;
  AddMoney: undefined;
  WithdrawMoney: undefined;
  LinkedAccounts: undefined;
  AddBankAccount: undefined;
  AddCard: undefined;
  TransactionHistory: undefined;
  TransactionDetails: { transactionId: string };
  EditProfile: undefined;
  SecuritySettings: undefined;
  PrivacySettings: undefined;
  NotificationSettings: undefined;
  PaymentSettings: undefined;
  KYCVerification: undefined;
  ChangePassword: undefined;
  ChangePin: undefined;
  DeviceManagement: undefined;
  HelpSupport: undefined;
  About: undefined;
  BusinessDashboard: undefined;
  BusinessPayments: undefined;
  Invoices: undefined;
  BusinessAnalytics: undefined;
  QRCodeGenerator: undefined;
  Notifications: undefined;
  Search: undefined;
  Contacts: undefined;
  UserSearch: undefined;
  ContactDetails: { contact: Contact };
  Rewards: undefined;
  Referral: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator();
const Drawer = createDrawerNavigator();

/**
 * Auth Stack Navigator
 */
const AuthNavigator = () => {
  const theme = useTheme();
  
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
      }}
    >
      <Stack.Screen name="Onboarding" component={OnboardingScreen} />
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Register" component={RegisterScreen} />
      <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
      <Stack.Screen name="SetupPin" component={SetupPinScreen} />
      <Stack.Screen name="BiometricSetup" component={BiometricSetupScreen} />
      <Stack.Screen name="MFASetup" component={MFASetupScreen} />
      <Stack.Screen name="MFAVerification" component={MFAVerificationScreen} />
    </Stack.Navigator>
  );
};

/**
 * Main Tab Navigator
 */
const TabNavigator = () => {
  const theme = useTheme();
  const userType = useSelector((state: RootState) => state.auth.user?.userType);
  
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ focused, color, size }) => {
          let iconName: string;
          
          switch (route.name) {
            case 'Dashboard':
              iconName = focused ? 'home' : 'home-outline';
              break;
            case 'Wallet':
              iconName = focused ? 'wallet' : 'wallet-outline';
              break;
            case 'Payment':
              iconName = 'cash-fast';
              break;
            case 'Activity':
              iconName = focused ? 'history' : 'clock-outline';
              break;
            case 'Profile':
              iconName = focused ? 'account' : 'account-outline';
              break;
            default:
              iconName = 'circle';
          }
          
          return <Icon name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.onSurfaceVariant,
        tabBarStyle: {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.outline,
          paddingBottom: 5,
          height: 60,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: '500',
        },
        headerShown: false,
      })}
    >
      <Tab.Screen 
        name="Dashboard" 
        component={userType === 'business' ? BusinessDashboardScreen : DashboardScreen} 
      />
      <Tab.Screen name="Wallet" component={WalletScreen} />
      <Tab.Screen 
        name="Payment" 
        component={PaymentScreen}
        options={{
          tabBarLabel: 'Pay',
          tabBarIcon: ({ color, size }) => (
            <Icon name="cash-fast" size={size + 4} color={color} />
          ),
        }}
      />
      <Tab.Screen name="Activity" component={ActivityScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
};

/**
 * Drawer Navigator
 */
const DrawerNavigator = () => {
  const theme = useTheme();
  
  return (
    <Drawer.Navigator
      drawerContent={(props) => <CustomDrawerContent {...props} />}
      screenOptions={{
        headerShown: false,
        drawerActiveTintColor: theme.colors.primary,
        drawerInactiveTintColor: theme.colors.onSurfaceVariant,
        drawerStyle: {
          backgroundColor: theme.colors.surface,
          width: 280,
        },
      }}
    >
      <Drawer.Screen 
        name="Home" 
        component={TabNavigator}
        options={{
          drawerIcon: ({ color, size }) => (
            <Icon name="home" size={size} color={color} />
          ),
        }}
      />
      <Drawer.Screen 
        name="PaymentFeed" 
        component={PaymentFeedScreen}
        options={{
          drawerLabel: 'Payment Feed',
          drawerIcon: ({ color, size }) => (
            <Icon name="rss" size={size} color={color} />
          ),
        }}
      />
      <Drawer.Screen 
        name="Notifications" 
        component={NotificationsScreen}
        options={{
          drawerIcon: ({ color, size }) => (
            <Icon name="bell" size={size} color={color} />
          ),
        }}
      />
      <Drawer.Screen 
        name="Contacts" 
        component={ContactsScreen}
        options={{
          drawerIcon: ({ color, size }) => (
            <Icon name="account-group" size={size} color={color} />
          ),
        }}
      />
      <Drawer.Screen 
        name="Rewards" 
        component={RewardsScreen}
        options={{
          drawerIcon: ({ color, size }) => (
            <Icon name="trophy" size={size} color={color} />
          ),
        }}
      />
      <Drawer.Screen 
        name="HelpSupport" 
        component={HelpSupportScreen}
        options={{
          drawerLabel: 'Help & Support',
          drawerIcon: ({ color, size }) => (
            <Icon name="help-circle" size={size} color={color} />
          ),
        }}
      />
    </Drawer.Navigator>
  );
};

/**
 * Main Stack Navigator
 */
const MainNavigator = () => {
  const theme = useTheme();
  
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
      }}
    >
      <Stack.Screen name="DrawerMain" component={DrawerNavigator} />
      
      {/* Payment Screens */}
      <Stack.Screen name="SendMoney" component={SendMoneyScreen} />
      <Stack.Screen name="RequestMoney" component={RequestMoneyScreen} />
      <Stack.Screen name="ScanQR" component={ScanQRScreen} />
      <Stack.Screen name="ContactSelection" component={ContactSelectionScreen} />
      <Stack.Screen name="PaymentConfirmation" component={PaymentConfirmationScreen} />
      <Stack.Screen name="PaymentSuccess" component={PaymentSuccessScreen} />
      <Stack.Screen name="PaymentDetails" component={PaymentDetailsScreen} />
      <Stack.Screen name="SplitBill" component={SplitBillScreen} />
      <Stack.Screen name="ScheduledPayment" component={ScheduledPaymentScreen} />
      <Stack.Screen name="NearbyPayment" component={NearbyPaymentScreen} />
      <Stack.Screen name="NFCPayment" component={NFCPaymentScreen} />
      
      {/* Social Payment Screens */}
      <Stack.Screen name="PaymentComments" component={PaymentCommentsScreen} />
      <Stack.Screen name="PublicFeed" component={PublicFeedScreen} />
      <Stack.Screen name="TrendingPayments" component={TrendingPaymentsScreen} />
      
      {/* Wallet Screens */}
      <Stack.Screen name="AddMoney" component={AddMoneyScreen} />
      <Stack.Screen name="WithdrawMoney" component={WithdrawMoneyScreen} />
      <Stack.Screen name="LinkedAccounts" component={LinkedAccountsScreen} />
      <Stack.Screen name="AddBankAccount" component={AddBankAccountScreen} />
      <Stack.Screen name="AddCard" component={AddCardScreen} />
      <Stack.Screen name="TransactionHistory" component={TransactionHistoryScreen} />
      <Stack.Screen name="TransactionDetails" component={TransactionDetailsScreen} />
      
      {/* Profile & Settings Screens */}
      <Stack.Screen name="EditProfile" component={EditProfileScreen} />
      <Stack.Screen name="SecuritySettings" component={SecuritySettingsScreen} />
      <Stack.Screen name="PrivacySettings" component={PrivacySettingsScreen} />
      <Stack.Screen name="NotificationSettings" component={NotificationSettingsScreen} />
      <Stack.Screen name="PaymentSettings" component={PaymentSettingsScreen} />
      <Stack.Screen name="KYCVerification" component={KYCVerificationScreen} />
      <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} />
      <Stack.Screen name="ChangePin" component={ChangePinScreen} />
      <Stack.Screen name="DeviceManagement" component={DeviceManagementScreen} />
      <Stack.Screen name="About" component={AboutScreen} />
      
      {/* Business Screens */}
      <Stack.Screen name="BusinessPayments" component={BusinessPaymentsScreen} />
      <Stack.Screen name="Invoices" component={InvoicesScreen} />
      <Stack.Screen name="BusinessAnalytics" component={BusinessAnalyticsScreen} />
      <Stack.Screen name="QRCodeGenerator" component={QRCodeGeneratorScreen} />
      
      {/* Other Screens */}
      <Stack.Screen name="Search" component={SearchScreen} />
      <Stack.Screen name="UserSearch" component={UserSearchScreen} />
      <Stack.Screen name="ContactDetails" component={ContactDetailsScreen} />
      <Stack.Screen name="Referral" component={ReferralScreen} />
    </Stack.Navigator>
  );
};

/**
 * Root Navigator
 */
const RootNavigator = () => {
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated);
  const isOnboardingComplete = useSelector((state: RootState) => state.auth.isOnboardingComplete);
  const { checkBiometricAuth } = useBiometric();
  const navigation = useNavigation();

  useEffect(() => {
    if (isAuthenticated) {
      checkBiometricAuth();
    }
  }, [isAuthenticated]);

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {!isAuthenticated ? (
        <Stack.Screen name="Auth" component={AuthNavigator} />
      ) : (
        <Stack.Screen name="Main" component={MainNavigator} />
      )}
    </Stack.Navigator>
  );
};

export default RootNavigator;