/**
 * NavigationService - Centralized navigation handling for notifications and deep links
 * Provides navigation utilities that can be used outside of React components
 */

import { createNavigationContainerRef, NavigationAction, StackActions } from '@react-navigation/native';
import { Linking } from 'react-native';

export type RootStackParamList = {
  // Auth Stack
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  VerifyOTP: { phoneNumber: string };
  
  // Main App Stack
  MainTabs: undefined;
  Dashboard: undefined;
  Notifications: undefined;
  Profile: undefined;
  Settings: undefined;
  
  // Transaction Stack
  SendMoney: { recipientId?: string; amount?: string };
  RequestMoney: { recipientId?: string; amount?: string };
  TransactionDetails: { transactionId: string; id?: string };
  TransactionHistory: undefined;
  PayRequest: { requestId: string };
  RequestDetails: { requestId: string };
  
  // Security Stack
  Security: undefined;
  SecuritySettings: undefined;
  VerifyAccount: undefined;
  TwoFactorAuth: undefined;
  BiometricSettings: undefined;
  
  // Card Stack
  Cards: undefined;
  CardDetails: { cardId: string };
  CardTransactions: { cardId?: string };
  AddCard: undefined;
  
  // Chat Stack
  Chat: { conversationId: string };
  ChatList: undefined;
  
  // Contacts Stack
  Contacts: undefined;
  ContactDetails: { contactId: string };
  AddContact: undefined;
  
  // Account Stack
  Account: undefined;
  AccountSettings: undefined;
  PersonalInfo: undefined;
  KYCVerification: undefined;
  
  // Merchant Stack
  MerchantDashboard: undefined;
  
  // Support Stack
  Support: undefined;
  HelpCenter: undefined;
  ContactSupport: undefined;
  
  // Utility Stack
  WebView: { url: string; title?: string };
  QRCodeScanner: undefined;
  LocationPicker: undefined;
  
  // Notification specific screens
  NotificationSettings: undefined;
  PaymentRequests: undefined;
};

export const navigationRef = createNavigationContainerRef<RootStackParamList>();

class NavigationService {
  private static instance: NavigationService;
  private isReady = false;
  private pendingActions: (() => void)[] = [];

  static getInstance(): NavigationService {
    if (!NavigationService.instance) {
      NavigationService.instance = new NavigationService();
    }
    return NavigationService.instance;
  }

  /**
   * Set navigation as ready and execute any pending actions
   */
  setReady(): void {
    this.isReady = true;
    
    // Execute any pending navigation actions
    while (this.pendingActions.length > 0) {
      const action = this.pendingActions.shift();
      if (action) {
        try {
          action();
        } catch (error) {
          console.error('Error executing pending navigation action:', error);
        }
      }
    }
  }

  /**
   * Check if navigation is ready
   */
  getIsReady(): boolean {
    return this.isReady && navigationRef.isReady();
  }

  /**
   * Execute navigation action when ready
   */
  private executeWhenReady(action: () => void): void {
    if (this.getIsReady()) {
      try {
        action();
      } catch (error) {
        console.error('Navigation error:', error);
      }
    } else {
      // Queue the action to execute when navigation is ready
      this.pendingActions.push(action);
    }
  }

  /**
   * Navigate to a screen
   */
  navigate<RouteName extends keyof RootStackParamList>(
    routeName: RouteName,
    params?: RootStackParamList[RouteName]
  ): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady()) {
        navigationRef.navigate(routeName as never, params as never);
      }
    });
  }

  /**
   * Push a new screen onto the stack
   */
  push<RouteName extends keyof RootStackParamList>(
    routeName: RouteName,
    params?: RootStackParamList[RouteName]
  ): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady()) {
        navigationRef.dispatch(StackActions.push(routeName as string, params));
      }
    });
  }

  /**
   * Replace current screen
   */
  replace<RouteName extends keyof RootStackParamList>(
    routeName: RouteName,
    params?: RootStackParamList[RouteName]
  ): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady()) {
        navigationRef.dispatch(StackActions.replace(routeName as string, params));
      }
    });
  }

  /**
   * Go back to previous screen
   */
  goBack(): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady() && navigationRef.canGoBack()) {
        navigationRef.goBack();
      }
    });
  }

  /**
   * Pop to top of stack
   */
  popToTop(): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady()) {
        navigationRef.dispatch(StackActions.popToTop());
      }
    });
  }

  /**
   * Reset navigation stack
   */
  reset(state: Parameters<typeof navigationRef.reset>[0]): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady()) {
        navigationRef.reset(state);
      }
    });
  }

  /**
   * Get current route name
   */
  getCurrentRoute(): string | undefined {
    if (this.getIsReady()) {
      return navigationRef.getCurrentRoute()?.name;
    }
    return undefined;
  }

  /**
   * Get current route params
   */
  getCurrentRouteParams(): any {
    if (this.getIsReady()) {
      return navigationRef.getCurrentRoute()?.params;
    }
    return undefined;
  }

  /**
   * Handle deep link navigation
   */
  handleDeepLink(url: string): boolean {
    try {
      const parsedUrl = this.parseDeepLink(url);
      if (parsedUrl) {
        this.navigate(parsedUrl.screen as keyof RootStackParamList, parsedUrl.params);
        return true;
      }
    } catch (error) {
      console.error('Error handling deep link:', error);
    }
    return false;
  }

  /**
   * Parse deep link URL
   */
  private parseDeepLink(url: string): { screen: string; params?: any } | null {
    try {
      const urlObj = new URL(url);
      const pathSegments = urlObj.pathname.split('/').filter(Boolean);
      
      if (pathSegments.length === 0) {
        return { screen: 'Dashboard' };
      }

      const screen = pathSegments[0];
      const params: any = {};

      // Parse query parameters
      urlObj.searchParams.forEach((value, key) => {
        params[key] = value;
      });

      // Parse path parameters
      if (pathSegments.length > 1) {
        switch (screen) {
          case 'transaction':
            params.transactionId = pathSegments[1];
            return { screen: 'TransactionDetails', params };
            
          case 'request':
            params.requestId = pathSegments[1];
            return { screen: 'RequestDetails', params };
            
          case 'chat':
            params.conversationId = pathSegments[1];
            return { screen: 'Chat', params };
            
          case 'card':
            params.cardId = pathSegments[1];
            return { screen: 'CardDetails', params };
            
          case 'contact':
            params.contactId = pathSegments[1];
            return { screen: 'ContactDetails', params };
        }
      }

      // Map screen names
      const screenMap: Record<string, string> = {
        'dashboard': 'Dashboard',
        'notifications': 'Notifications',
        'profile': 'Profile',
        'settings': 'Settings',
        'send': 'SendMoney',
        'request': 'RequestMoney',
        'transactions': 'TransactionHistory',
        'cards': 'Cards',
        'contacts': 'Contacts',
        'security': 'Security',
        'support': 'Support',
        'chat': 'ChatList',
        'kyc': 'KYCVerification',
      };

      const mappedScreen = screenMap[screen.toLowerCase()] || screen;
      return { screen: mappedScreen, params: Object.keys(params).length > 0 ? params : undefined };
      
    } catch (error) {
      console.error('Error parsing deep link:', error);
      return null;
    }
  }

  /**
   * Handle notification-specific navigation
   */
  handleNotificationNavigation(notificationType: string, data: any): void {
    switch (notificationType) {
      case 'payment_received':
      case 'payment_sent':
        if (data?.transactionId) {
          this.navigate('TransactionDetails', { transactionId: data.transactionId });
        } else {
          this.navigate('TransactionHistory');
        }
        break;

      case 'payment_request':
        if (data?.requestId) {
          this.navigate('RequestDetails', { requestId: data.requestId });
        } else {
          this.navigate('PaymentRequests');
        }
        break;

      case 'money_request_received':
        if (data?.requestId) {
          this.navigate('PayRequest', { requestId: data.requestId });
        }
        break;

      case 'security_alert':
      case 'login_attempt':
      case 'suspicious_activity':
        this.navigate('Security');
        break;

      case 'account_verification':
      case 'kyc_update':
        this.navigate('KYCVerification');
        break;

      case 'card_transaction':
      case 'card_blocked':
        if (data?.cardId) {
          this.navigate('CardDetails', { cardId: data.cardId });
        } else {
          this.navigate('Cards');
        }
        break;

      case 'message':
      case 'chat_message':
        if (data?.conversationId) {
          this.navigate('Chat', { conversationId: data.conversationId });
        } else {
          this.navigate('ChatList');
        }
        break;

      case 'contact_request':
      case 'friend_request':
        this.navigate('Contacts');
        break;

      case 'promotion':
      case 'marketing':
        if (data?.url) {
          this.navigate('WebView', { url: data.url, title: data?.title });
        } else {
          this.navigate('Notifications');
        }
        break;

      case 'system_maintenance':
      case 'service_update':
        this.navigate('Notifications');
        break;

      default:
        this.navigate('Notifications');
    }
  }

  /**
   * Handle notification actions (quick actions from notification)
   */
  async handleNotificationAction(actionId: string, notificationData: any): Promise<void> {
    switch (actionId) {
      case 'accept_request':
        if (notificationData?.requestId) {
          this.navigate('PayRequest', { requestId: notificationData.requestId });
        }
        break;

      case 'decline_request':
        // Handle decline action - could show a confirmation screen
        this.navigate('PaymentRequests');
        break;

      case 'view_transaction':
        if (notificationData?.transactionId) {
          this.navigate('TransactionDetails', { transactionId: notificationData.transactionId });
        }
        break;

      case 'reply_message':
        if (notificationData?.conversationId) {
          this.navigate('Chat', { conversationId: notificationData.conversationId });
        }
        break;

      case 'secure_account':
        this.navigate('Security');
        break;

      case 'verify_account':
        this.navigate('KYCVerification');
        break;

      default:
        console.log('Unknown notification action:', actionId);
        this.navigate('Notifications');
    }
  }

  /**
   * Open external URL
   */
  async openExternalURL(url: string): Promise<boolean> {
    try {
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
        return true;
      } else {
        console.log("Don't know how to open URI: " + url);
        return false;
      }
    } catch (error) {
      console.error('Error opening external URL:', error);
      return false;
    }
  }

  /**
   * Navigate to app settings
   */
  async openAppSettings(): Promise<void> {
    try {
      await Linking.openSettings();
    } catch (error) {
      console.error('Error opening app settings:', error);
    }
  }

  /**
   * Navigate with animation type
   */
  navigateWithAnimation<RouteName extends keyof RootStackParamList>(
    routeName: RouteName,
    params?: RootStackParamList[RouteName],
    animationType: 'slide' | 'fade' | 'modal' = 'slide'
  ): void {
    // The animation type can be handled by the navigator configuration
    // This is a placeholder for custom animation handling
    this.navigate(routeName, params);
  }

  /**
   * Navigate and clear stack
   */
  navigateAndReset<RouteName extends keyof RootStackParamList>(
    routeName: RouteName,
    params?: RootStackParamList[RouteName]
  ): void {
    this.executeWhenReady(() => {
      if (navigationRef.isReady()) {
        this.reset({
          index: 0,
          routes: [{ name: routeName as string, params }],
        });
      }
    });
  }

  /**
   * Check if can go back
   */
  canGoBack(): boolean {
    return this.getIsReady() && navigationRef.canGoBack();
  }

  /**
   * Get navigation state
   */
  getState(): any {
    if (this.getIsReady()) {
      return navigationRef.getState();
    }
    return null;
  }
}

export const NavigationService = NavigationService.getInstance();
export default NavigationService;