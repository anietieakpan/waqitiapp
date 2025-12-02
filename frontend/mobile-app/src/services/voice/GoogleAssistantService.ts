import { Platform, Linking } from 'react-native';
import { AnalyticsService } from '../AnalyticsService';
import { AuthService } from '../auth/AuthService';
import VoicePaymentService from './VoicePaymentService';
import DeepLinkManager from '../deeplinking/DeepLinkManager';

export interface AssistantAction {
  intent: string;
  parameters: Record<string, any>;
  query: string;
  confidence: number;
}

export interface AssistantResponse {
  fulfillmentText: string;
  fulfillmentMessages?: any[];
  outputContexts?: any[];
  followupEventInput?: any;
}

/**
 * Google Assistant Integration Service for Android voice-activated payments
 * Handles Actions on Google, Intent processing, and conversational AI
 */
class GoogleAssistantService {
  private static instance: GoogleAssistantService;
  private isInitialized: boolean = false;
  private actionHandlers: Map<string, (action: AssistantAction) => Promise<AssistantResponse>> = new Map();

  static getInstance(): GoogleAssistantService {
    if (!GoogleAssistantService.instance) {
      GoogleAssistantService.instance = new GoogleAssistantService();
    }
    return GoogleAssistantService.instance;
  }

  async initialize(): Promise<void> {
    if (Platform.OS !== 'android' || this.isInitialized) return;

    try {
      console.log('Initializing Google Assistant Service...');

      // Register action handlers
      this.registerActionHandlers();

      // Setup deep link listener for Assistant actions
      this.setupAssistantDeepLinkHandling();

      this.isInitialized = true;
      console.log('Google Assistant Service initialized successfully');

      await this.trackEvent('google_assistant_service_initialized');

    } catch (error) {
      console.error('Failed to initialize Google Assistant Service:', error);
      throw error;
    }
  }

  /**
   * Handle Google Assistant action
   */
  async handleAssistantAction(action: AssistantAction): Promise<AssistantResponse> {
    try {
      console.log('Handling Google Assistant action:', action);

      await this.trackEvent('google_assistant_action_received', {
        intent: action.intent,
        confidence: action.confidence
      });

      // Get handler for the intent
      const handler = this.actionHandlers.get(action.intent);
      if (!handler) {
        return this.createErrorResponse(`I don't know how to handle the intent: ${action.intent}`);
      }

      // Execute the handler
      const response = await handler(action);

      await this.trackEvent('google_assistant_action_handled', {
        intent: action.intent,
        success: true
      });

      return response;

    } catch (error) {
      console.error('Failed to handle Google Assistant action:', error);
      
      await this.trackEvent('google_assistant_action_failed', {
        intent: action.intent,
        error: error.message
      });

      return this.createErrorResponse('Sorry, I encountered an error processing your request.');
    }
  }

  /**
   * Process incoming deep link from Google Assistant
   */
  async processAssistantDeepLink(url: string): Promise<void> {
    try {
      const urlObj = new URL(url);
      const intent = urlObj.searchParams.get('intent');
      const parameters = this.parseUrlParameters(urlObj.searchParams);

      if (intent) {
        const action: AssistantAction = {
          intent,
          parameters,
          query: urlObj.searchParams.get('query') || '',
          confidence: parseFloat(urlObj.searchParams.get('confidence') || '1.0')
        };

        await this.handleAssistantAction(action);
      }

    } catch (error) {
      console.error('Failed to process Assistant deep link:', error);
    }
  }

  /**
   * Register a custom action handler
   */
  registerActionHandler(
    intent: string,
    handler: (action: AssistantAction) => Promise<AssistantResponse>
  ): void {
    this.actionHandlers.set(intent, handler);
    console.log(`Registered Google Assistant action handler for: ${intent}`);
  }

  /**
   * Create suggestion chips for Assistant responses
   */
  createSuggestionChips(suggestions: string[]): any[] {
    return suggestions.map(suggestion => ({
      platform: 'ACTIONS_ON_GOOGLE',
      suggestions: {
        suggestions: suggestions.map(text => ({ title: text }))
      }
    }));
  }

  // Private methods

  private registerActionHandlers(): void {
    // Send money intent
    this.registerActionHandler('waqiti.send.money', async (action) => {
      const { amount, recipient, currency = 'USD' } = action.parameters;

      if (!amount || !recipient) {
        return {
          fulfillmentText: 'I need both an amount and recipient to send money. For example, say "Send $20 to John"',
          fulfillmentMessages: this.createSuggestionChips(['Send $20 to John', 'Cancel'])
        };
      }

      // Check authentication
      const isAuthenticated = await AuthService.isAuthenticated();
      if (!isAuthenticated) {
        await DeepLinkManager.handleDeepLink('waqiti://login', { source: 'google_assistant' });
        return {
          fulfillmentText: 'Please open the Waqiti app and log in first, then try again.'
        };
      }

      // Navigate to send money flow
      await DeepLinkManager.handleDeepLink('waqiti://send', {
        source: 'google_assistant',
        prefillData: {
          recipientName: recipient,
          amount: parseFloat(amount),
          currency,
          description: 'Google Assistant payment'
        }
      });

      return {
        fulfillmentText: `Opening Waqiti to send $${amount} to ${recipient}. Please confirm the payment in the app.`,
        fulfillmentMessages: this.createSuggestionChips(['Check my balance', 'Cancel'])
      };
    });

    // Request money intent
    this.registerActionHandler('waqiti.request.money', async (action) => {
      const { amount, recipient, currency = 'USD' } = action.parameters;

      if (!amount || !recipient) {
        return {
          fulfillmentText: 'I need both an amount and person to request money from. For example, say "Request $30 from Sarah"',
          fulfillmentMessages: this.createSuggestionChips(['Request $30 from Sarah', 'Cancel'])
        };
      }

      await DeepLinkManager.handleDeepLink('waqiti://request', {
        source: 'google_assistant',
        prefillData: {
          fromUserName: recipient,
          amount: parseFloat(amount),
          currency,
          description: 'Google Assistant request'
        }
      });

      return {
        fulfillmentText: `Opening Waqiti to request $${amount} from ${recipient}. Please review and send the request in the app.`,
        fulfillmentMessages: this.createSuggestionChips(['Check my balance', 'Recent transactions'])
      };
    });

    // Check balance intent
    this.registerActionHandler('waqiti.check.balance', async (action) => {
      const isAuthenticated = await AuthService.isAuthenticated();
      if (!isAuthenticated) {
        await DeepLinkManager.handleDeepLink('waqiti://login', { source: 'google_assistant' });
        return {
          fulfillmentText: 'Please open the Waqiti app and log in to check your balance.'
        };
      }

      try {
        // Execute balance check through voice service
        const voiceService = VoicePaymentService;
        const success = await voiceService.executeVoiceCommand({
          action: 'check_balance',
          confidence: 1.0,
          rawText: 'Check balance from Google Assistant'
        });

        if (success) {
          return {
            fulfillmentText: 'I\'ve retrieved your balance. Check the Waqiti app for details.',
            fulfillmentMessages: this.createSuggestionChips(['Send money', 'Recent transactions'])
          };
        } else {
          return {
            fulfillmentText: 'I couldn\'t retrieve your balance right now. Please open the Waqiti app to check manually.',
            fulfillmentMessages: this.createSuggestionChips(['Open Waqiti'])
          };
        }
      } catch (error) {
        return this.createErrorResponse('Sorry, I couldn\'t check your balance right now.');
      }
    });

    // Recent transactions intent
    this.registerActionHandler('waqiti.recent.transactions', async (action) => {
      await DeepLinkManager.handleDeepLink('waqiti://transactions', { source: 'google_assistant' });

      return {
        fulfillmentText: 'Opening your recent transactions in Waqiti.',
        fulfillmentMessages: this.createSuggestionChips(['Send money', 'Check balance'])
      };
    });

    // Split bill intent
    this.registerActionHandler('waqiti.split.bill', async (action) => {
      const { amount, people, description } = action.parameters;

      if (!amount) {
        return {
          fulfillmentText: 'How much is the total bill you want to split?',
          fulfillmentMessages: this.createSuggestionChips(['Split $50', 'Split $100'])
        };
      }

      await DeepLinkManager.handleDeepLink('waqiti://split/create', {
        source: 'google_assistant',
        prefillData: {
          totalAmount: parseFloat(amount),
          description: description || `Split bill - ${people || 'Google Assistant'}`
        }
      });

      return {
        fulfillmentText: `Opening Waqiti to split a $${amount} bill. You can add participants in the app.`,
        fulfillmentMessages: this.createSuggestionChips(['Check balance', 'Recent transactions'])
      };
    });

    // Pay bill intent
    this.registerActionHandler('waqiti.pay.bill', async (action) => {
      const { merchant, amount, billType } = action.parameters;

      if (!merchant && !billType) {
        return {
          fulfillmentText: 'Which bill would you like to pay? For example, say "Pay my electric bill" or "Pay Starbucks"',
          fulfillmentMessages: this.createSuggestionChips(['Pay electric bill', 'Pay Netflix'])
        };
      }

      const merchantName = merchant || `${billType} bill`;

      await DeepLinkManager.handleDeepLink('waqiti://pay', {
        source: 'google_assistant',
        prefillData: {
          merchantName,
          amount: amount ? parseFloat(amount) : undefined,
          description: `Bill payment - ${merchantName}`
        }
      });

      return {
        fulfillmentText: `Opening Waqiti to pay your ${merchantName}${amount ? ` for $${amount}` : ''}. Please complete the payment in the app.`,
        fulfillmentMessages: this.createSuggestionChips(['Check balance', 'Recent transactions'])
      };
    });

    // Voice payment intent
    this.registerActionHandler('waqiti.voice.payment', async (action) => {
      try {
        const voiceService = VoicePaymentService;
        await voiceService.startListening();

        return {
          fulfillmentText: 'Voice payment mode activated. Tell me what you\'d like to do with your money.',
          fulfillmentMessages: this.createSuggestionChips(['Send $20 to John', 'Check my balance', 'Cancel'])
        };
      } catch (error) {
        return this.createErrorResponse('Sorry, I couldn\'t start voice payment mode. Please open the Waqiti app.');
      }
    });

    // Help intent
    this.registerActionHandler('waqiti.help', async (action) => {
      return {
        fulfillmentText: 'I can help you with Waqiti payments! You can say things like:\n\n' +
          '• "Send $20 to John"\n' +
          '• "Request $30 from Sarah"\n' +
          '• "Check my balance"\n' +
          '• "Show recent transactions"\n' +
          '• "Split a $50 bill"\n' +
          '• "Pay my electric bill"',
        fulfillmentMessages: this.createSuggestionChips([
          'Send money',
          'Check balance',
          'Recent transactions',
          'Split bill'
        ])
      };
    });

    // Default fallback intent
    this.registerActionHandler('Default Fallback Intent', async (action) => {
      return {
        fulfillmentText: 'I didn\'t understand that. Try saying something like "Send $20 to John" or "Check my balance". Say "Help" for more options.',
        fulfillmentMessages: this.createSuggestionChips(['Help', 'Send money', 'Check balance'])
      };
    });
  }

  private setupAssistantDeepLinkHandling(): void {
    // Listen for deep links from Google Assistant
    Linking.addEventListener('url', ({ url }) => {
      if (url.includes('google_assistant') || url.includes('assistant_action')) {
        this.processAssistantDeepLink(url);
      }
    });

    // Handle initial URL if app was opened from Assistant
    Linking.getInitialURL().then(url => {
      if (url && (url.includes('google_assistant') || url.includes('assistant_action'))) {
        this.processAssistantDeepLink(url);
      }
    });
  }

  private parseUrlParameters(searchParams: URLSearchParams): Record<string, any> {
    const parameters: Record<string, any> = {};
    
    searchParams.forEach((value, key) => {
      if (key !== 'intent' && key !== 'query' && key !== 'confidence') {
        // Try to parse as number if possible
        const numValue = parseFloat(value);
        parameters[key] = isNaN(numValue) ? value : numValue;
      }
    });

    return parameters;
  }

  private createErrorResponse(message: string): AssistantResponse {
    return {
      fulfillmentText: message,
      fulfillmentMessages: this.createSuggestionChips(['Help', 'Try again'])
    };
  }

  private async trackEvent(eventName: string, properties?: Record<string, any>): Promise<void> {
    try {
      await AnalyticsService.track(eventName, {
        ...properties,
        platform: 'android',
        integration: 'google_assistant',
        timestamp: Date.now()
      });
    } catch (error) {
      console.error('Failed to track Google Assistant event:', error);
    }
  }
}

export default GoogleAssistantService.getInstance();