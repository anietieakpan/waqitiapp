/**
 * Navigation types for React Navigation
 */

import { NavigationProp, RouteProp } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';

// Define all screens and their params
export type RootStackParamList = {
  // Auth Stack
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  VerifyOTP: { phone?: string; email?: string };
  
  // Main Tab Navigator
  MainTabs: undefined;
  
  // Home Stack
  Home: undefined;
  Notifications: undefined;
  Profile: undefined;
  Settings: undefined;
  
  // Payment Stack
  SendMoney: { recipientId?: string };
  RequestMoney: { senderId?: string };
  PaymentDetails: { paymentId: string };
  PaymentHistory: undefined;
  SplitBill: { participants?: string[] };
  QRScan: undefined;
  QRCode: { amount?: number };
  
  // Wallet Stack
  Wallet: undefined;
  AddMoney: undefined;
  Withdraw: undefined;
  Cards: undefined;
  BankAccounts: undefined;
  
  // Social Stack
  SocialFeed: undefined;
  UserProfile: { userId: string };
  TransactionDetails: { transactionId: string };
  
  // Tax Stack
  TaxDocuments: undefined;
  TaxFiling: { year?: number };
  TaxReports: undefined;
  
  // Investment Stack
  Investments: undefined;
  StockTrading: { symbol?: string };
  CryptoTrading: { currency?: string };
  Portfolio: undefined;
  
  // Business Stack
  BusinessDashboard: undefined;
  Invoices: undefined;
  Merchants: undefined;
  POSPayment: { merchantId: string };
  
  // Settings Stack
  Security: undefined;
  Privacy: undefined;
  NotificationSettings: undefined;
  Language: undefined;
  Help: undefined;
  About: undefined;
};

// Define Tab Navigator params
export type MainTabParamList = {
  HomeTab: undefined;
  WalletTab: undefined;
  PayTab: undefined;
  SocialTab: undefined;
  ProfileTab: undefined;
};

// Navigation prop types for each screen
export type HomeScreenNavigationProp = StackNavigationProp<RootStackParamList, 'Home'>;
export type SendMoneyNavigationProp = StackNavigationProp<RootStackParamList, 'SendMoney'>;
export type WalletNavigationProp = StackNavigationProp<RootStackParamList, 'Wallet'>;
export type TaxDocumentsNavigationProp = StackNavigationProp<RootStackParamList, 'TaxDocuments'>;
export type ProfileNavigationProp = StackNavigationProp<RootStackParamList, 'Profile'>;

// Route prop types for screens that receive params
export type SendMoneyRouteProp = RouteProp<RootStackParamList, 'SendMoney'>;
export type PaymentDetailsRouteProp = RouteProp<RootStackParamList, 'PaymentDetails'>;
export type UserProfileRouteProp = RouteProp<RootStackParamList, 'UserProfile'>;

// Combined props for screens
export interface SendMoneyScreenProps {
  navigation: SendMoneyNavigationProp;
  route: SendMoneyRouteProp;
}

export interface TaxDocumentsScreenProps {
  navigation: TaxDocumentsNavigationProp;
  route: RouteProp<RootStackParamList, 'TaxDocuments'>;
}

// Generic navigation prop
export type AppNavigationProp = NavigationProp<RootStackParamList>;