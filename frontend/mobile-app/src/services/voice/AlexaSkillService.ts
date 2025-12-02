import { Platform } from 'react-native';
import { AnalyticsService } from '../AnalyticsService';
import { AuthService } from '../auth/AuthService';
import { ApiService } from '../ApiService';
import VoicePaymentService from './VoicePaymentService';

export interface AlexaRequest {
  type: 'LaunchRequest' | 'IntentRequest' | 'SessionEndedRequest';
  requestId: string;
  timestamp: string;
  locale: string;
  intent?: {
    name: string;
    confirmationStatus: string;
    slots?: Record<string, AlexaSlot>;
  };
  session: {
    sessionId: string;
    user: {
      userId: string;
      accessToken?: string;
    };
    new: boolean;
  };
}

export interface AlexaSlot {
  name: string;
  value: string;
  confirmationStatus: string;
  resolutions?: {
    resolutionsPerAuthority: Array<{
      authority: string;
      status: {
        code: string;
      };
      values: Array<{
        value: {
          name: string;
          id: string;
        };
      }>;
    }>;
  };
}

export interface AlexaResponse {
  version: string;
  response: {
    outputSpeech: {
      type: 'PlainText' | 'SSML';
      text?: string;
      ssml?: string;
    };
    card?: {
      type: 'Simple' | 'Standard' | 'LinkAccount';
      title?: string;
      content?: string;
      text?: string;
      image?: {
        smallImageUrl: string;
        largeImageUrl: string;
      };
    };
    reprompt?: {
      outputSpeech: {
        type: 'PlainText' | 'SSML';
        text?: string;
        ssml?: string;
      };
    };
    shouldEndSession: boolean;
    directives?: any[];
  };
  sessionAttributes?: Record<string, any>;
}

/**
 * Alexa Skill Service for smart speaker voice-activated payments
 * Handles Alexa Skill requests, intent processing, and smart home integration
 */
class AlexaSkillService {
  private static instance: AlexaSkillService;
  private isInitialized: boolean = false;
  private intentHandlers: Map<string, (request: AlexaRequest) => Promise<AlexaResponse>> = new Map();
  private sessionAttributes: Map<string, Record<string, any>> = new Map();

  static getInstance(): AlexaSkillService {
    if (!AlexaSkillService.instance) {
      AlexaSkillService.instance = new AlexaSkillService();
    }
    return AlexaSkillService.instance;
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      console.log('Initializing Alexa Skill Service...');

      // Register intent handlers
      this.registerIntentHandlers();

      this.isInitialized = true;
      console.log('Alexa Skill Service initialized successfully');

      await this.trackEvent('alexa_skill_service_initialized');

    } catch (error) {
      console.error('Failed to initialize Alexa Skill Service:', error);
      throw error;
    }
  }

  /**
   * Handle incoming Alexa skill request
   */
  async handleSkillRequest(request: AlexaRequest): Promise<AlexaResponse> {
    try {
      console.log('Handling Alexa skill request:', request.type);

      await this.trackEvent('alexa_skill_request_received', {
        type: request.type,
        intent: request.intent?.name,
        session_id: request.session.sessionId
      });

      // Handle different request types
      switch (request.type) {
        case 'LaunchRequest':
          return await this.handleLaunchRequest(request);
        case 'IntentRequest':
          return await this.handleIntentRequest(request);
        case 'SessionEndedRequest':
          return await this.handleSessionEndedRequest(request);
        default:
          return this.createErrorResponse('I didn\'t understand that request type.');
      }

    } catch (error) {
      console.error('Failed to handle Alexa skill request:', error);
      
      await this.trackEvent('alexa_skill_request_failed', {
        type: request.type,
        error: error.message,
        session_id: request.session.sessionId
      });

      return this.createErrorResponse('Sorry, I encountered an error processing your request.');
    }
  }

  /**
   * Verify request signature and timestamp (for security)
   */
  verifyRequest(headers: Record<string, string>, body: string): boolean {
    // In a real implementation, this would verify the Alexa request signature
    // using the certificate chain and timestamp validation
    const timestamp = headers['x-amz-request-timestamp'];
    const signature = headers['signature'];
    const certUrl = headers['signaturecertchainurl'];

    // Simplified verification - real implementation would be more complex
    return !!(timestamp && signature && certUrl);
  }

  /**
   * Register custom intent handler
   */
  registerIntentHandler(
    intentName: string,
    handler: (request: AlexaRequest) => Promise<AlexaResponse>
  ): void {
    this.intentHandlers.set(intentName, handler);
    console.log(`Registered Alexa intent handler for: ${intentName}`);
  }

  // Private methods

  private async handleLaunchRequest(request: AlexaRequest): Promise<AlexaResponse> {
    const welcomeMessage = 'Welcome to Waqiti! I can help you send money, check your balance, or manage your payments. What would you like to do?';
    
    this.setSessionAttribute(request.session.sessionId, 'launched', true);

    return {
      version: '1.0',
      response: {
        outputSpeech: {
          type: 'PlainText',
          text: welcomeMessage
        },
        card: {
          type: 'Simple',
          title: 'Welcome to Waqiti',
          content: 'You can say things like:\n• Send $20 to John\n• Check my balance\n• What are my recent transactions?\n• Pay my electric bill'
        },
        reprompt: {
          outputSpeech: {
            type: 'PlainText',
            text: 'You can send money, check your balance, or ask about recent transactions. What would you like to do?'
          }
        },
        shouldEndSession: false
      },
      sessionAttributes: this.getSessionAttributes(request.session.sessionId)
    };
  }

  private async handleIntentRequest(request: AlexaRequest): Promise<AlexaResponse> {
    const intentName = request.intent?.name;
    if (!intentName) {
      return this.createErrorResponse('I didn\'t receive a valid intent.');
    }

    const handler = this.intentHandlers.get(intentName);
    if (!handler) {
      return this.createErrorResponse(`I don't know how to handle the ${intentName} intent.`);
    }

    return await handler(request);
  }

  private async handleSessionEndedRequest(request: AlexaRequest): Promise<AlexaResponse> {
    // Clean up session data
    this.sessionAttributes.delete(request.session.sessionId);

    return {
      version: '1.0',
      response: {
        outputSpeech: {
          type: 'PlainText',
          text: ''
        },
        shouldEndSession: true
      }
    };
  }

  private registerIntentHandlers(): void {
    // Send money intent
    this.registerIntentHandler('SendMoneyIntent', async (request) => {
      const slots = request.intent?.slots;
      const amount = slots?.amount?.value;
      const recipient = slots?.recipient?.value;

      if (!amount || !recipient) {
        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: 'I need both an amount and recipient to send money. For example, say "Send twenty dollars to John"'
            },
            reprompt: {
              outputSpeech: {
                type: 'PlainText',
                text: 'How much would you like to send and to whom?'
              }
            },
            shouldEndSession: false
          }
        };
      }

      // Check authentication
      const isAuthenticated = await this.verifyUserAuthentication(request);
      if (!isAuthenticated) {
        return this.createAuthenticationResponse();
      }

      // Validate amount
      const numericAmount = this.parseAmount(amount);
      if (numericAmount <= 0 || numericAmount > 500) {
        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: 'I can only process payments between $1 and $500 through Alexa. Please use the Waqiti app for larger amounts.'
            },
            shouldEndSession: false
          }
        };
      }

      // Store transaction details for confirmation
      this.setSessionAttribute(request.session.sessionId, 'pendingTransaction', {
        type: 'send',
        amount: numericAmount,
        recipient,
        timestamp: Date.now()
      });

      return {
        version: '1.0',
        response: {
          outputSpeech: {
            type: 'PlainText',
            text: `I'll send $${numericAmount} to ${recipient}. Say "confirm" to proceed or "cancel" to stop.`
          },
          card: {
            type: 'Simple',
            title: 'Confirm Payment',
            content: `Send $${numericAmount} to ${recipient}\n\nSay "confirm" to proceed`
          },
          reprompt: {
            outputSpeech: {
              type: 'PlainText',
              text: 'Say "confirm" to send the money or "cancel" to stop.'
            }
          },
          shouldEndSession: false
        },
        sessionAttributes: this.getSessionAttributes(request.session.sessionId)
      };
    });

    // Request money intent
    this.registerIntentHandler('RequestMoneyIntent', async (request) => {
      const slots = request.intent?.slots;
      const amount = slots?.amount?.value;
      const recipient = slots?.recipient?.value;

      if (!amount || !recipient) {
        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: 'I need both an amount and person to request money from. Try saying "Request thirty dollars from Sarah"'
            },
            shouldEndSession: false
          }
        };
      }

      const isAuthenticated = await this.verifyUserAuthentication(request);
      if (!isAuthenticated) {
        return this.createAuthenticationResponse();
      }

      const numericAmount = this.parseAmount(amount);
      
      this.setSessionAttribute(request.session.sessionId, 'pendingTransaction', {
        type: 'request',
        amount: numericAmount,
        recipient,
        timestamp: Date.now()
      });

      return {
        version: '1.0',
        response: {
          outputSpeech: {
            type: 'PlainText',
            text: `I'll request $${numericAmount} from ${recipient}. Say "confirm" to send the request.`
          },
          shouldEndSession: false
        },
        sessionAttributes: this.getSessionAttributes(request.session.sessionId)
      };
    });

    // Check balance intent
    this.registerIntentHandler('CheckBalanceIntent', async (request) => {
      const isAuthenticated = await this.verifyUserAuthentication(request);
      if (!isAuthenticated) {
        return this.createAuthenticationResponse();
      }

      try {
        const balanceResponse = await ApiService.get('/api/wallet/balance', {
          headers: {
            'Authorization': `Bearer ${request.session.user.accessToken}`
          }
        });

        const balance = balanceResponse.data.totalBalance;
        const formattedBalance = new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD'
        }).format(balance);

        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: `Your current Waqiti balance is ${formattedBalance}.`
            },
            card: {
              type: 'Simple',
              title: 'Account Balance',
              content: `Current balance: ${formattedBalance}`
            },
            shouldEndSession: true
          }
        };

      } catch (error) {
        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: 'I\'m having trouble accessing your balance right now. Please try again later or check the Waqiti app.'
            },
            shouldEndSession: true
          }
        };
      }
    });

    // Recent transactions intent
    this.registerIntentHandler('RecentTransactionsIntent', async (request) => {
      const isAuthenticated = await this.verifyUserAuthentication(request);
      if (!isAuthenticated) {
        return this.createAuthenticationResponse();
      }

      try {
        const transactionsResponse = await ApiService.get('/api/transactions/recent?limit=3', {
          headers: {
            'Authorization': `Bearer ${request.session.user.accessToken}`
          }
        });

        const transactions = transactionsResponse.data.transactions;
        
        if (!transactions || transactions.length === 0) {
          return {
            version: '1.0',
            response: {
              outputSpeech: {
                type: 'PlainText',
                text: 'You don\'t have any recent transactions.'
              },
              shouldEndSession: true
            }
          };
        }

        const transactionSummary = transactions.slice(0, 3).map((tx: any) => {
          const amount = Math.abs(tx.amount);
          const type = tx.amount > 0 ? 'received' : 'sent';
          const description = tx.description || tx.merchantName || 'a transaction';
          return `${type} $${amount} for ${description}`;
        }).join(', ');

        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: `Your recent transactions: ${transactionSummary}.`
            },
            card: {
              type: 'Simple',
              title: 'Recent Transactions',
              content: transactions.map((tx: any, index: number) => 
                `${index + 1}. ${tx.amount > 0 ? '+' : ''}$${tx.amount} - ${tx.description || tx.merchantName}`
              ).join('\n')
            },
            shouldEndSession: true
          }
        };

      } catch (error) {
        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: 'I\'m having trouble accessing your transactions right now. Please check the Waqiti app.'
            },
            shouldEndSession: true
          }
        };
      }
    });

    // Confirm intent
    this.registerIntentHandler('AMAZON.YesIntent', async (request) => {
      const pendingTransaction = this.getSessionAttribute(request.session.sessionId, 'pendingTransaction');
      
      if (!pendingTransaction) {
        return {
          version: '1.0',
          response: {
            outputSpeech: {
              type: 'PlainText',
              text: 'I don\'t have any pending transactions to confirm. What would you like to do?'
            },
            shouldEndSession: false
          }
        };
      }

      try {
        // Execute the transaction through the voice service
        const voiceService = VoicePaymentService;
        const command = {
          action: pendingTransaction.type as any,
          amount: pendingTransaction.amount,
          recipient: { name: pendingTransaction.recipient },
          confidence: 1.0,
          rawText: `Alexa ${pendingTransaction.type} command`
        };

        const success = await voiceService.executeVoiceCommand(command);

        if (success) {
          return {
            version: '1.0',
            response: {
              outputSpeech: {
                type: 'PlainText',
                text: `Your ${pendingTransaction.type} request has been processed. Please check the Waqiti app to complete the transaction.`
              },
              shouldEndSession: true
            }
          };
        } else {
          return {
            version: '1.0',
            response: {
              outputSpeech: {
                type: 'PlainText',
                text: 'I had trouble processing your request. Please try again using the Waqiti app.'
              },
              shouldEndSession: true
            }
          };
        }

      } catch (error) {
        return this.createErrorResponse('Sorry, I couldn\'t process your transaction right now.');
      } finally {
        // Clear pending transaction
        this.setSessionAttribute(request.session.sessionId, 'pendingTransaction', null);
      }
    });

    // Cancel intent
    this.registerIntentHandler('AMAZON.NoIntent', async (request) => {
      this.setSessionAttribute(request.session.sessionId, 'pendingTransaction', null);
      
      return {
        version: '1.0',
        response: {
          outputSpeech: {
            type: 'PlainText',
            text: 'Okay, I\'ve cancelled that transaction. What else can I help you with?'
          },
          shouldEndSession: false
        }
      };
    });

    // Help intent
    this.registerIntentHandler('AMAZON.HelpIntent', async (request) => {
      return {
        version: '1.0',
        response: {
          outputSpeech: {
            type: 'PlainText',
            text: 'I can help you with Waqiti payments. You can say "Send twenty dollars to John", "Check my balance", "What are my recent transactions", or "Request money from Sarah". What would you like to do?'
          },
          card: {
            type: 'Simple',
            title: 'Waqiti Voice Commands',
            content: 'Try saying:\n• Send $X to [name]\n• Check my balance\n• Recent transactions\n• Request $X from [name]\n• Cancel'
          },
          reprompt: {
            outputSpeech: {
              type: 'PlainText',
              text: 'What would you like to do with Waqiti?'
            }
          },
          shouldEndSession: false
        }
      };
    });

    // Stop/Cancel intents
    this.registerIntentHandler('AMAZON.StopIntent', async (request) => {
      return {
        version: '1.0',
        response: {
          outputSpeech: {
            type: 'PlainText',
            text: 'Goodbye! Thanks for using Waqiti.'
          },
          shouldEndSession: true
        }
      };
    });

    this.registerIntentHandler('AMAZON.CancelIntent', async (request) => {
      return {
        version: '1.0',
        response: {
          outputSpeech: {
            type: 'PlainText',
            text: 'Cancelled. Is there anything else I can help you with?'
          },
          shouldEndSession: false
        }
      };
    });
  }

  private async verifyUserAuthentication(request: AlexaRequest): Promise<boolean> {
    // In a real implementation, this would verify the access token
    // and ensure the user is properly authenticated with Waqiti
    return !!(request.session.user.accessToken);
  }

  private createAuthenticationResponse(): AlexaResponse {
    return {
      version: '1.0',
      response: {
        outputSpeech: {
          type: 'PlainText',
          text: 'You need to link your Waqiti account to use this feature. Please check the Alexa app to link your account.'
        },
        card: {
          type: 'LinkAccount'
        },
        shouldEndSession: true
      }
    };
  }

  private createErrorResponse(message: string): AlexaResponse {
    return {
      version: '1.0',
      response: {
        outputSpeech: {
          type: 'PlainText',
          text: message
        },
        shouldEndSession: true
      }
    };
  }

  private parseAmount(amountString: string): number {
    // Handle various amount formats: "twenty", "20", "twenty dollars", etc.
    const cleanAmount = amountString.toLowerCase()
      .replace(/dollars?|bucks?|\$/g, '')
      .trim();

    // Simple number words mapping (real implementation would be more comprehensive)
    const numberWords: Record<string, number> = {
      'zero': 0, 'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5,
      'six': 6, 'seven': 7, 'eight': 8, 'nine': 9, 'ten': 10,
      'eleven': 11, 'twelve': 12, 'thirteen': 13, 'fourteen': 14, 'fifteen': 15,
      'sixteen': 16, 'seventeen': 17, 'eighteen': 18, 'nineteen': 19, 'twenty': 20,
      'thirty': 30, 'forty': 40, 'fifty': 50, 'sixty': 60, 'seventy': 70,
      'eighty': 80, 'ninety': 90, 'hundred': 100
    };

    if (numberWords[cleanAmount] !== undefined) {
      return numberWords[cleanAmount];
    }

    const numericValue = parseFloat(cleanAmount);
    return isNaN(numericValue) ? 0 : numericValue;
  }

  private setSessionAttribute(sessionId: string, key: string, value: any): void {
    if (!this.sessionAttributes.has(sessionId)) {
      this.sessionAttributes.set(sessionId, {});
    }
    const attributes = this.sessionAttributes.get(sessionId)!;
    attributes[key] = value;
  }

  private getSessionAttribute(sessionId: string, key: string): any {
    const attributes = this.sessionAttributes.get(sessionId);
    return attributes?.[key];
  }

  private getSessionAttributes(sessionId: string): Record<string, any> {
    return this.sessionAttributes.get(sessionId) || {};
  }

  private async trackEvent(eventName: string, properties?: Record<string, any>): Promise<void> {
    try {
      await AnalyticsService.track(eventName, {
        ...properties,
        platform: 'alexa',
        integration: 'alexa_skill',
        timestamp: Date.now()
      });
    } catch (error) {
      console.error('Failed to track Alexa skill event:', error);
    }
  }
}

export default AlexaSkillService.getInstance();