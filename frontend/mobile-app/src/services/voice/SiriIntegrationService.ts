import { Platform } from 'react-native';
import { SiriShortcutsEvent, donateShortcut, suggestShortcuts, clearAllShortcuts } from 'react-native-siri-shortcut';
import VoicePaymentService from './VoicePaymentService';
import { AnalyticsService } from '../AnalyticsService';
import { AuthService } from '../auth/AuthService';
import DeepLinkManager from '../deeplinking/DeepLinkManager';

export interface SiriShortcut {
  activityType: string;
  title: string;
  userInfo: Record<string, any>;
  keywords: string[];
  persistentIdentifier: string;
  isEligibleForSearch: boolean;
  isEligibleForPrediction: boolean;
  suggestedInvocationPhrase: string;
  needsSave?: boolean;
}

/**
 * Siri Integration Service for iOS voice-activated payments
 * Handles Siri Shortcuts, Intent handling, and voice command processing
 */
class SiriIntegrationService {
  private static instance: SiriIntegrationService;
  private isInitialized: boolean = false;
  private shortcuts: Map<string, SiriShortcut> = new Map();

  static getInstance(): SiriIntegrationService {
    if (!SiriIntegrationService.instance) {
      SiriIntegrationService.instance = new SiriIntegrationService();
    }
    return SiriIntegrationService.instance;
  }

  async initialize(): Promise<void> {
    if (Platform.OS !== 'ios' || this.isInitialized) return;

    try {
      console.log('Initializing Siri Integration Service...');

      // Setup shortcut event listeners
      this.setupShortcutListeners();

      // Register default shortcuts
      await this.registerDefaultShortcuts();

      // Suggest shortcuts to user
      await this.suggestShortcutsToUser();

      this.isInitialized = true;
      console.log('Siri Integration Service initialized successfully');

      await this.trackEvent('siri_integration_initialized');

    } catch (error) {
      console.error('Failed to initialize Siri Integration Service:', error);
      throw error;
    }
  }

  /**
   * Register a new Siri shortcut
   */
  async registerShortcut(shortcut: SiriShortcut): Promise<void> {
    try {
      await donateShortcut(shortcut);
      this.shortcuts.set(shortcut.activityType, shortcut);

      await this.trackEvent('siri_shortcut_registered', {
        activity_type: shortcut.activityType,
        title: shortcut.title
      });

      console.log(`Registered Siri shortcut: ${shortcut.title}`);

    } catch (error) {
      console.error('Failed to register Siri shortcut:', error);
      throw error;
    }
  }

  /**
   * Handle Siri shortcut activation
   */
  async handleShortcutActivation(event: SiriShortcutsEvent): Promise<void> {
    try {
      console.log('Siri shortcut activated:', event);

      const { activityType, userInfo } = event;

      await this.trackEvent('siri_shortcut_activated', {
        activity_type: activityType,
        user_info: userInfo
      });

      // Route to appropriate handler based on activity type
      switch (activityType) {
        case 'com.waqiti.send_money':
          await this.handleSendMoneyShortcut(userInfo);
          break;
        case 'com.waqiti.request_money':
          await this.handleRequestMoneyShortcut(userInfo);
          break;
        case 'com.waqiti.check_balance':
          await this.handleCheckBalanceShortcut();
          break;
        case 'com.waqiti.recent_transactions':
          await this.handleRecentTransactionsShortcut();
          break;
        case 'com.waqiti.split_bill':
          await this.handleSplitBillShortcut(userInfo);
          break;
        case 'com.waqiti.voice_payment':
          await this.handleVoicePaymentShortcut();
          break;
        default:
          console.warn('Unknown Siri shortcut activity type:', activityType);
      }

    } catch (error) {
      console.error('Failed to handle Siri shortcut activation:', error);
      await this.trackEvent('siri_shortcut_activation_failed', {
        activity_type: event.activityType,
        error: error.message
      });
    }
  }

  /**
   * Create contextual shortcuts based on user activity
   */
  async createContextualShortcut(
    action: 'send' | 'request' | 'split',
    recipientName: string,
    amount?: number
  ): Promise<void> {
    try {
      const shortcut = this.buildContextualShortcut(action, recipientName, amount);
      await this.registerShortcut(shortcut);

      await this.trackEvent('siri_contextual_shortcut_created', {
        action,
        recipient_name: recipientName,
        amount
      });

    } catch (error) {
      console.error('Failed to create contextual shortcut:', error);
    }
  }

  /**
   * Suggest shortcuts to user after successful transactions
   */
  async suggestShortcutAfterTransaction(
    action: 'send' | 'request',
    recipientName: string,
    amount: number
  ): Promise<void> {
    try {
      const shortcut = this.buildTransactionShortcut(action, recipientName, amount);
      
      await suggestShortcuts([shortcut]);

      await this.trackEvent('siri_shortcut_suggested', {
        action,
        recipient_name: recipientName,
        amount
      });

    } catch (error) {
      console.error('Failed to suggest shortcut:', error);
    }
  }

  /**
   * Clear all registered shortcuts
   */
  async clearAllShortcuts(): Promise<void> {
    try {
      await clearAllShortcuts();
      this.shortcuts.clear();

      await this.trackEvent('siri_shortcuts_cleared');
      console.log('All Siri shortcuts cleared');

    } catch (error) {
      console.error('Failed to clear Siri shortcuts:', error);
    }
  }

  /**
   * Get all registered shortcuts
   */
  getRegisteredShortcuts(): SiriShortcut[] {
    return Array.from(this.shortcuts.values());
  }

  // Private methods

  private setupShortcutListeners(): void {
    // Note: In a real implementation, you'd use the actual react-native-siri-shortcut library
    // which provides event listeners for shortcut activations
    console.log('Setting up Siri shortcut event listeners');
  }

  private async registerDefaultShortcuts(): Promise<void> {
    const defaultShortcuts: SiriShortcut[] = [
      {
        activityType: 'com.waqiti.voice_payment',
        title: 'Start Voice Payment',
        userInfo: { action: 'voice_payment' },
        keywords: ['voice', 'payment', 'waqiti', 'money'],
        persistentIdentifier: 'voice_payment',
        isEligibleForSearch: true,
        isEligibleForPrediction: true,
        suggestedInvocationPhrase: 'Start voice payment'
      },
      {
        activityType: 'com.waqiti.check_balance',
        title: 'Check Balance',
        userInfo: { action: 'check_balance' },
        keywords: ['balance', 'money', 'account', 'waqiti'],
        persistentIdentifier: 'check_balance',
        isEligibleForSearch: true,
        isEligibleForPrediction: true,
        suggestedInvocationPhrase: 'Check my Waqiti balance'
      },
      {
        activityType: 'com.waqiti.recent_transactions',
        title: 'Recent Transactions',
        userInfo: { action: 'recent_transactions' },
        keywords: ['transactions', 'history', 'payments', 'waqiti'],
        persistentIdentifier: 'recent_transactions',
        isEligibleForSearch: true,
        isEligibleForPrediction: true,
        suggestedInvocationPhrase: 'Show my recent transactions'
      },
      {
        activityType: 'com.waqiti.send_money',
        title: 'Send Money',
        userInfo: { action: 'send_money' },
        keywords: ['send', 'money', 'transfer', 'pay', 'waqiti'],
        persistentIdentifier: 'send_money',
        isEligibleForSearch: true,
        isEligibleForPrediction: true,
        suggestedInvocationPhrase: 'Send money with Waqiti'
      },
      {
        activityType: 'com.waqiti.request_money',
        title: 'Request Money',
        userInfo: { action: 'request_money' },
        keywords: ['request', 'money', 'ask', 'charge', 'waqiti'],
        persistentIdentifier: 'request_money',
        isEligibleForSearch: true,
        isEligibleForPrediction: true,
        suggestedInvocationPhrase: 'Request money with Waqiti'
      }
    ];

    for (const shortcut of defaultShortcuts) {
      await this.registerShortcut(shortcut);
    }
  }

  private async suggestShortcutsToUser(): Promise<void> {
    try {
      const suggestedShortcuts = Array.from(this.shortcuts.values()).slice(0, 3);
      await suggestShortcuts(suggestedShortcuts);

      await this.trackEvent('siri_shortcuts_suggested_to_user', {
        count: suggestedShortcuts.length
      });

    } catch (error) {
      console.error('Failed to suggest shortcuts to user:', error);
    }
  }

  private buildContextualShortcut(
    action: 'send' | 'request' | 'split',
    recipientName: string,
    amount?: number
  ): SiriShortcut {
    const activityType = `com.waqiti.${action}_${recipientName.replace(/\s+/g, '_').toLowerCase()}`;
    const amountText = amount ? ` $${amount}` : '';
    
    return {
      activityType,
      title: `${this.capitalizeFirst(action)}${amountText} ${action === 'split' ? 'with' : action === 'send' ? 'to' : 'from'} ${recipientName}`,
      userInfo: {
        action,
        recipientName,
        amount
      },
      keywords: [action, 'money', recipientName.toLowerCase(), 'waqiti'],
      persistentIdentifier: activityType,
      isEligibleForSearch: true,
      isEligibleForPrediction: true,
      suggestedInvocationPhrase: `${this.capitalizeFirst(action)} money ${action === 'send' ? 'to' : 'from'} ${recipientName}`
    };
  }

  private buildTransactionShortcut(
    action: 'send' | 'request',
    recipientName: string,
    amount: number
  ): SiriShortcut {
    const activityType = `com.waqiti.${action}_${recipientName.replace(/\s+/g, '_').toLowerCase()}_${amount}`;
    
    return {
      activityType,
      title: `${this.capitalizeFirst(action)} $${amount} ${action === 'send' ? 'to' : 'from'} ${recipientName}`,
      userInfo: {
        action,
        recipientName,
        amount,
        predefined: true
      },
      keywords: [action, 'money', recipientName.toLowerCase(), amount.toString(), 'waqiti'],
      persistentIdentifier: activityType,
      isEligibleForSearch: true,
      isEligibleForPrediction: true,
      suggestedInvocationPhrase: `${this.capitalizeFirst(action)} $${amount} ${action === 'send' ? 'to' : 'from'} ${recipientName}`
    };
  }

  private async handleSendMoneyShortcut(userInfo: Record<string, any>): Promise<void> {
    const { recipientName, amount, predefined } = userInfo;

    if (predefined && recipientName && amount) {
      // Pre-filled send money flow
      await DeepLinkManager.handleDeepLink('waqiti://send', {
        source: 'siri',
        prefillData: {
          recipientName,
          amount,
          description: 'Siri shortcut payment'
        }
      });
    } else {
      // General send money flow
      await DeepLinkManager.handleDeepLink('waqiti://send', { source: 'siri' });
    }
  }

  private async handleRequestMoneyShortcut(userInfo: Record<string, any>): Promise<void> {
    const { recipientName, amount, predefined } = userInfo;

    if (predefined && recipientName && amount) {
      // Pre-filled request money flow
      await DeepLinkManager.handleDeepLink('waqiti://request', {
        source: 'siri',
        prefillData: {
          fromUserName: recipientName,
          amount,
          description: 'Siri shortcut request'
        }
      });
    } else {
      // General request money flow
      await DeepLinkManager.handleDeepLink('waqiti://request', { source: 'siri' });
    }
  }

  private async handleCheckBalanceShortcut(): Promise<void> {
    // Check if user is authenticated
    const isAuthenticated = await AuthService.isAuthenticated();
    if (!isAuthenticated) {
      await DeepLinkManager.handleDeepLink('waqiti://login', { source: 'siri' });
      return;
    }

    // Use voice service to announce balance
    try {
      const voiceService = VoicePaymentService;
      await voiceService.executeVoiceCommand({
        action: 'check_balance',
        confidence: 1.0,
        rawText: 'Check balance from Siri'
      });
    } catch (error) {
      // Fallback to opening balance screen
      await DeepLinkManager.handleDeepLink('waqiti://home', { source: 'siri' });
    }
  }

  private async handleRecentTransactionsShortcut(): Promise<void> {
    await DeepLinkManager.handleDeepLink('waqiti://transactions', { source: 'siri' });
  }

  private async handleSplitBillShortcut(userInfo: Record<string, any>): Promise<void> {
    const { amount, recipientName } = userInfo;

    await DeepLinkManager.handleDeepLink('waqiti://split/create', {
      source: 'siri',
      prefillData: {
        totalAmount: amount,
        description: recipientName ? `Split with ${recipientName}` : 'Siri split bill'
      }
    });
  }

  private async handleVoicePaymentShortcut(): Promise<void> {
    try {
      // Start voice payment session
      const voiceService = VoicePaymentService;
      await voiceService.startListening();

      // Provide voice feedback
      // In a real implementation, you might use text-to-speech
      console.log('Voice payment started via Siri shortcut');

    } catch (error) {
      console.error('Failed to start voice payment from Siri:', error);
      // Fallback to opening the app
      await DeepLinkManager.handleDeepLink('waqiti://home', { source: 'siri' });
    }
  }

  private capitalizeFirst(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  private async trackEvent(eventName: string, properties?: Record<string, any>): Promise<void> {
    try {
      await AnalyticsService.track(eventName, {
        ...properties,
        platform: 'ios',
        integration: 'siri',
        timestamp: Date.now()
      });
    } catch (error) {
      console.error('Failed to track Siri integration event:', error);
    }
  }
}

export default SiriIntegrationService.getInstance();