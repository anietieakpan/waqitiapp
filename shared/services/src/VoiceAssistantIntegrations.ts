/**
 * Voice Assistant Integrations Service
 * Comprehensive integration with Siri, Google Assistant, and Alexa
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform, NativeModules, AppState } from 'react-native';
import { Logger } from './LoggingService';

// Types
export interface VoiceCommand {
  id: string;
  phrase: string;
  action: string;
  parameters: Record<string, any>;
  assistant: 'siri' | 'google' | 'alexa';
  timestamp: Date;
  confidence: number;
  userId?: string;
  sessionId?: string;
}

export interface VoiceIntent {
  name: string;
  phrases: string[];
  parameters: VoiceIntentParameter[];
  handler: (parameters: Record<string, any>) => Promise<VoiceResponse>;
  requiresAuth?: boolean;
  requiresBiometric?: boolean;
  category: 'payment' | 'balance' | 'transaction' | 'general';
}

export interface VoiceIntentParameter {
  name: string;
  type: 'string' | 'number' | 'amount' | 'contact' | 'date';
  required: boolean;
  examples?: string[];
}

export interface VoiceResponse {
  success: boolean;
  text: string;
  data?: any;
  followUp?: VoiceIntent[];
  requiresConfirmation?: boolean;
  confirmationText?: string;
}

export interface VoiceAssistantConfig {
  enabled: boolean;
  assistants: {
    siri: SiriConfig;
    google: GoogleAssistantConfig;
    alexa: AlexaConfig;
  };
  security: {
    requireAuthentication: boolean;
    requireBiometric: boolean;
    maxAmount: number;
    sessionTimeout: number;
  };
  nlp: {
    confidenceThreshold: number;
    enableLearning: boolean;
    personalizedResponses: boolean;
  };
}

export interface SiriConfig {
  enabled: boolean;
  shortcuts: SiriShortcut[];
}

export interface SiriShortcut {
  identifier: string;
  phrase: string;
  title: string;
  subtitle?: string;
  userActivity: any;
  intent: string;
}

export interface GoogleAssistantConfig {
  enabled: boolean;
  projectId: string;
  actions: GoogleAction[];
}

export interface GoogleAction {
  intent: string;
  trainingPhrases: string[];
  parameters: any[];
  handler: string;
}

export interface AlexaConfig {
  enabled: boolean;
  skillId: string;
  invocationName: string;
  intents: AlexaIntent[];
}

export interface AlexaIntent {
  name: string;
  samples: string[];
  slots: AlexaSlot[];
}

export interface AlexaSlot {
  name: string;
  type: string;
  required: boolean;
}

class VoiceAssistantIntegrationsService {
  private static instance: VoiceAssistantIntegrationsService;
  private isInitialized = false;
  private config: VoiceAssistantConfig;
  private intents: Map<string, VoiceIntent> = new Map();
  private currentSession: string | null = null;
  private sessionStartTime: number = 0;

  private readonly DEFAULT_CONFIG: VoiceAssistantConfig = {
    enabled: true,
    assistants: {
      siri: {
        enabled: Platform.OS === 'ios',
        shortcuts: [],
      },
      google: {
        enabled: Platform.OS === 'android',
        projectId: '',
        actions: [],
      },
      alexa: {
        enabled: false,
        skillId: '',
        invocationName: 'waqiti',
        intents: [],
      },
    },
    security: {
      requireAuthentication: true,
      requireBiometric: true,
      maxAmount: 500,
      sessionTimeout: 300000, // 5 minutes
    },
    nlp: {
      confidenceThreshold: 0.7,
      enableLearning: true,
      personalizedResponses: true,
    },
  };

  private constructor() {
    this.config = this.DEFAULT_CONFIG;
  }

  public static getInstance(): VoiceAssistantIntegrationsService {
    if (!VoiceAssistantIntegrationsService.instance) {
      VoiceAssistantIntegrationsService.instance = new VoiceAssistantIntegrationsService();
    }
    return VoiceAssistantIntegrationsService.instance;
  }

  public async initialize(config?: Partial<VoiceAssistantConfig>): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Load saved configuration
      const savedConfig = await AsyncStorage.getItem('@voice_assistant_config');
      if (savedConfig) {
        this.config = { ...this.DEFAULT_CONFIG, ...JSON.parse(savedConfig), ...config };
      } else {
        this.config = { ...this.DEFAULT_CONFIG, ...config };
      }

      // Initialize platform-specific integrations
      if (this.config.assistants.siri.enabled && Platform.OS === 'ios') {
        await this.initializeSiri();
      }

      if (this.config.assistants.google.enabled && Platform.OS === 'android') {
        await this.initializeGoogleAssistant();
      }

      if (this.config.assistants.alexa.enabled) {
        await this.initializeAlexa();
      }

      // Register default intents
      this.registerDefaultIntents();

      // Setup app state listener
      AppState.addEventListener('change', this.handleAppStateChange);

      this.isInitialized = true;
      Logger.info('Voice Assistant Integrations initialized', {
        siri: this.config.assistants.siri.enabled,
        google: this.config.assistants.google.enabled,
        alexa: this.config.assistants.alexa.enabled,
      });

    } catch (error) {
      Logger.error('Failed to initialize Voice Assistant Integrations', error);
      throw error;
    }
  }

  // Siri Integration
  private async initializeSiri(): Promise<void> {
    try {
      const { SiriShortcuts } = NativeModules;
      if (!SiriShortcuts) {
        Logger.warn('Siri Shortcuts not available');
        return;
      }

      // Define Siri shortcuts
      const shortcuts: SiriShortcut[] = [
        {
          identifier: 'com.waqiti.send_money',
          phrase: 'Send money with Waqiti',
          title: 'Send Money',
          subtitle: 'Send money to a contact',
          userActivity: {
            activityType: 'com.waqiti.send_money',
            title: 'Send Money',
            userInfo: { action: 'send_money' },
          },
          intent: 'send_money',
        },
        {
          identifier: 'com.waqiti.check_balance',
          phrase: 'Check my Waqiti balance',
          title: 'Check Balance',
          subtitle: 'View your account balance',
          userActivity: {
            activityType: 'com.waqiti.check_balance',
            title: 'Check Balance',
            userInfo: { action: 'check_balance' },
          },
          intent: 'check_balance',
        },
        {
          identifier: 'com.waqiti.recent_transactions',
          phrase: 'Show my recent Waqiti transactions',
          title: 'Recent Transactions',
          subtitle: 'View recent payment history',
          userActivity: {
            activityType: 'com.waqiti.recent_transactions',
            title: 'Recent Transactions',
            userInfo: { action: 'recent_transactions' },
          },
          intent: 'recent_transactions',
        },
        {
          identifier: 'com.waqiti.request_money',
          phrase: 'Request money with Waqiti',
          title: 'Request Money',
          subtitle: 'Request payment from a contact',
          userActivity: {
            activityType: 'com.waqiti.request_money',
            title: 'Request Money',
            userInfo: { action: 'request_money' },
          },
          intent: 'request_money',
        },
      ];

      // Register shortcuts
      for (const shortcut of shortcuts) {
        await SiriShortcuts.addShortcut(shortcut);
      }

      this.config.assistants.siri.shortcuts = shortcuts;
      Logger.info('Siri shortcuts registered', { count: shortcuts.length });

    } catch (error) {
      Logger.error('Failed to initialize Siri integration', error);
    }
  }

  // Google Assistant Integration
  private async initializeGoogleAssistant(): Promise<void> {
    try {
      const { GoogleAssistant } = NativeModules;
      if (!GoogleAssistant) {
        Logger.warn('Google Assistant not available');
        return;
      }

      // Define Google Actions
      const actions: GoogleAction[] = [
        {
          intent: 'send_money',
          trainingPhrases: [
            'Send $amount to $contact',
            'Pay $contact $amount',
            'Transfer $amount to $contact',
            'Give $amount to $contact',
          ],
          parameters: [
            { name: 'amount', type: '@sys.unit-currency' },
            { name: 'contact', type: '@sys.person' },
          ],
          handler: 'send_money',
        },
        {
          intent: 'check_balance',
          trainingPhrases: [
            'What is my balance',
            'Check my account balance',
            'How much money do I have',
            'Show my Waqiti balance',
          ],
          parameters: [],
          handler: 'check_balance',
        },
        {
          intent: 'recent_transactions',
          trainingPhrases: [
            'Show my recent transactions',
            'What are my latest payments',
            'Recent transaction history',
            'What did I spend money on',
          ],
          parameters: [],
          handler: 'recent_transactions',
        },
      ];

      // Register actions with Google Assistant
      await GoogleAssistant.registerActions(actions);

      this.config.assistants.google.actions = actions;
      Logger.info('Google Assistant actions registered', { count: actions.length });

    } catch (error) {
      Logger.error('Failed to initialize Google Assistant integration', error);
    }
  }

  // Alexa Integration
  private async initializeAlexa(): Promise<void> {
    try {
      // Alexa integration would typically be server-side
      // This is a placeholder for client-side setup
      
      const intents: AlexaIntent[] = [
        {
          name: 'SendMoneyIntent',
          samples: [
            'send {amount} to {contact}',
            'pay {contact} {amount}',
            'transfer {amount} to {contact}',
          ],
          slots: [
            { name: 'amount', type: 'AMAZON.NUMBER', required: true },
            { name: 'contact', type: 'AMAZON.Person', required: true },
          ],
        },
        {
          name: 'CheckBalanceIntent',
          samples: [
            'what is my balance',
            'check my balance',
            'how much money do I have',
          ],
          slots: [],
        },
      ];

      this.config.assistants.alexa.intents = intents;
      Logger.info('Alexa intents configured', { count: intents.length });

    } catch (error) {
      Logger.error('Failed to initialize Alexa integration', error);
    }
  }

  // Register default voice intents
  private registerDefaultIntents(): void {
    // Send Money Intent
    this.registerIntent({
      name: 'send_money',
      phrases: [
        'Send {amount} to {contact}',
        'Pay {contact} {amount}',
        'Transfer {amount} to {contact}',
        'Give {amount} to {contact}',
      ],
      parameters: [
        { name: 'amount', type: 'amount', required: true },
        { name: 'contact', type: 'contact', required: true },
      ],
      handler: this.handleSendMoney,
      requiresAuth: true,
      requiresBiometric: true,
      category: 'payment',
    });

    // Check Balance Intent
    this.registerIntent({
      name: 'check_balance',
      phrases: [
        'What is my balance',
        'Check my balance',
        'How much money do I have',
        'Show my account balance',
      ],
      parameters: [],
      handler: this.handleCheckBalance,
      requiresAuth: true,
      category: 'balance',
    });

    // Recent Transactions Intent
    this.registerIntent({
      name: 'recent_transactions',
      phrases: [
        'Show my recent transactions',
        'What are my latest payments',
        'Recent transaction history',
        'What did I spend money on',
      ],
      parameters: [],
      handler: this.handleRecentTransactions,
      requiresAuth: true,
      category: 'transaction',
    });

    // Request Money Intent
    this.registerIntent({
      name: 'request_money',
      phrases: [
        'Request {amount} from {contact}',
        'Ask {contact} for {amount}',
        'Charge {contact} {amount}',
      ],
      parameters: [
        { name: 'amount', type: 'amount', required: true },
        { name: 'contact', type: 'contact', required: true },
      ],
      handler: this.handleRequestMoney,
      requiresAuth: true,
      category: 'payment',
    });

    Logger.info('Default voice intents registered', { count: this.intents.size });
  }

  // Intent registration
  public registerIntent(intent: VoiceIntent): void {
    this.intents.set(intent.name, intent);
    Logger.debug('Voice intent registered', { name: intent.name, category: intent.category });
  }

  // Process voice command
  public async processVoiceCommand(command: VoiceCommand): Promise<VoiceResponse> {
    try {
      Logger.info('Processing voice command', {
        action: command.action,
        assistant: command.assistant,
        confidence: command.confidence,
      });

      // Check if session is valid
      if (!this.isSessionValid()) {
        await this.startNewSession();
      }

      // Find matching intent
      const intent = this.intents.get(command.action);
      if (!intent) {
        return {
          success: false,
          text: "I don't understand that command. Please try again.",
        };
      }

      // Check authentication if required
      if (intent.requiresAuth && !(await this.checkAuthentication())) {
        return {
          success: false,
          text: 'Please authenticate to use this feature.',
        };
      }

      // Check biometric authentication if required
      if (intent.requiresBiometric && !(await this.checkBiometric())) {
        return {
          success: false,
          text: 'Biometric authentication required for this action.',
        };
      }

      // Process the command
      const response = await intent.handler(command.parameters);

      // Log the interaction
      await this.logVoiceInteraction(command, response);

      return response;

    } catch (error) {
      Logger.error('Failed to process voice command', error, {
        action: command.action,
        assistant: command.assistant,
      });

      return {
        success: false,
        text: 'Sorry, there was an error processing your request. Please try again.',
      };
    }
  }

  // Intent Handlers
  private handleSendMoney = async (parameters: Record<string, any>): Promise<VoiceResponse> => {
    try {
      const { amount, contact } = parameters;
      
      // Validate parameters
      if (!amount || !contact) {
        return {
          success: false,
          text: 'Please specify both the amount and the recipient.',
        };
      }

      // Check amount limit
      if (amount > this.config.security.maxAmount) {
        return {
          success: false,
          text: `The maximum amount for voice payments is $${this.config.security.maxAmount}.`,
        };
      }

      // Process the payment (this would call the actual payment service)
      Logger.info('Processing voice payment', { amount, contact });

      return {
        success: true,
        text: `Sending $${amount} to ${contact}. Please confirm this transaction.`,
        requiresConfirmation: true,
        confirmationText: `Are you sure you want to send $${amount} to ${contact}?`,
        data: { amount, contact, action: 'send_money' },
      };

    } catch (error) {
      Logger.error('Failed to process send money command', error);
      return {
        success: false,
        text: 'Failed to process payment. Please try again.',
      };
    }
  };

  private handleCheckBalance = async (): Promise<VoiceResponse> => {
    try {
      // This would call the actual balance service
      const balance = 1250.75; // Mock balance
      
      return {
        success: true,
        text: `Your current balance is $${balance.toFixed(2)}.`,
        data: { balance },
      };

    } catch (error) {
      Logger.error('Failed to check balance', error);
      return {
        success: false,
        text: 'Failed to retrieve your balance. Please try again.',
      };
    }
  };

  private handleRecentTransactions = async (): Promise<VoiceResponse> => {
    try {
      // This would call the actual transaction service
      const transactions = [
        { amount: 25.50, recipient: 'Coffee Shop', date: 'today' },
        { amount: 100.00, recipient: 'John Doe', date: 'yesterday' },
      ];
      
      const summary = transactions
        .map(t => `$${t.amount} to ${t.recipient} ${t.date}`)
        .join(', ');
      
      return {
        success: true,
        text: `Your recent transactions: ${summary}.`,
        data: { transactions },
      };

    } catch (error) {
      Logger.error('Failed to get recent transactions', error);
      return {
        success: false,
        text: 'Failed to retrieve your transactions. Please try again.',
      };
    }
  };

  private handleRequestMoney = async (parameters: Record<string, any>): Promise<VoiceResponse> => {
    try {
      const { amount, contact } = parameters;
      
      if (!amount || !contact) {
        return {
          success: false,
          text: 'Please specify both the amount and who to request money from.',
        };
      }

      Logger.info('Processing money request', { amount, contact });

      return {
        success: true,
        text: `Requesting $${amount} from ${contact}. They will receive a notification.`,
        data: { amount, contact, action: 'request_money' },
      };

    } catch (error) {
      Logger.error('Failed to process money request', error);
      return {
        success: false,
        text: 'Failed to send money request. Please try again.',
      };
    }
  };

  // Session management
  private startNewSession(): void {
    this.currentSession = `voice_session_${Date.now()}`;
    this.sessionStartTime = Date.now();
    Logger.info('Started new voice session', { sessionId: this.currentSession });
  }

  private isSessionValid(): boolean {
    if (!this.currentSession) return false;
    
    const sessionAge = Date.now() - this.sessionStartTime;
    return sessionAge < this.config.security.sessionTimeout;
  }

  // Security checks
  private async checkAuthentication(): Promise<boolean> {
    try {
      const token = await AsyncStorage.getItem('authToken');
      return !!token;
    } catch {
      return false;
    }
  }

  private async checkBiometric(): Promise<boolean> {
    try {
      // This would integrate with the biometric service
      return true; // Mock implementation
    } catch {
      return false;
    }
  }

  // Logging
  private async logVoiceInteraction(
    command: VoiceCommand,
    response: VoiceResponse
  ): Promise<void> {
    try {
      const interaction = {
        sessionId: this.currentSession,
        command,
        response,
        timestamp: new Date().toISOString(),
      };

      // Store in local history
      const history = await AsyncStorage.getItem('@voice_interaction_history') || '[]';
      const interactions = JSON.parse(history);
      interactions.push(interaction);
      
      // Keep only recent interactions
      if (interactions.length > 100) {
        interactions.splice(0, interactions.length - 100);
      }
      
      await AsyncStorage.setItem('@voice_interaction_history', JSON.stringify(interactions));

    } catch (error) {
      Logger.error('Failed to log voice interaction', error);
    }
  }

  // App state handling
  private handleAppStateChange = (nextAppState: string): void => {
    if (nextAppState === 'background' && this.currentSession) {
      Logger.info('App backgrounded, ending voice session');
      this.currentSession = null;
    }
  };

  // Configuration
  public async updateConfig(config: Partial<VoiceAssistantConfig>): Promise<void> {
    this.config = { ...this.config, ...config };
    await AsyncStorage.setItem('@voice_assistant_config', JSON.stringify(this.config));
    Logger.info('Voice assistant configuration updated');
  }

  public getConfig(): VoiceAssistantConfig {
    return { ...this.config };
  }

  // Analytics
  public async getUsageAnalytics(): Promise<any> {
    try {
      const history = await AsyncStorage.getItem('@voice_interaction_history') || '[]';
      const interactions = JSON.parse(history);
      
      const analytics = {
        totalInteractions: interactions.length,
        successRate: interactions.filter((i: any) => i.response.success).length / interactions.length,
        mostUsedIntent: this.getMostUsedIntent(interactions),
        avgSessionLength: this.getAverageSessionLength(interactions),
        assistantUsage: this.getAssistantUsageStats(interactions),
      };

      return analytics;
    } catch {
      return null;
    }
  }

  private getMostUsedIntent(interactions: any[]): string {
    const intentCounts: Record<string, number> = {};
    interactions.forEach(i => {
      const intent = i.command.action;
      intentCounts[intent] = (intentCounts[intent] || 0) + 1;
    });
    
    return Object.keys(intentCounts).reduce((a, b) => 
      intentCounts[a] > intentCounts[b] ? a : b, ''
    );
  }

  private getAverageSessionLength(interactions: any[]): number {
    // Simplified calculation
    return interactions.length > 0 ? 30000 : 0; // 30 seconds average
  }

  private getAssistantUsageStats(interactions: any[]): Record<string, number> {
    const stats: Record<string, number> = { siri: 0, google: 0, alexa: 0 };
    interactions.forEach(i => {
      const assistant = i.command.assistant;
      if (stats[assistant] !== undefined) {
        stats[assistant]++;
      }
    });
    return stats;
  }

  // Cleanup
  public async cleanup(): Promise<void> {
    this.currentSession = null;
    AppState.removeEventListener('change', this.handleAppStateChange);
    Logger.info('Voice assistant integrations cleaned up');
  }
}

// Export singleton instance
export const VoiceAssistantIntegrations = VoiceAssistantIntegrationsService.getInstance();

export default VoiceAssistantIntegrations;