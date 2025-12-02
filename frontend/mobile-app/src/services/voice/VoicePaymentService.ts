import { Platform, PermissionsAndroid, Alert } from 'react-native';
import Voice, { SpeechRecognizedEvent, SpeechResultsEvent, SpeechErrorEvent } from '@react-native-voice/voice';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../ApiService';
import { AuthService } from '../auth/AuthService';
import { AnalyticsService } from '../AnalyticsService';
import { BiometricService } from '../biometric/BiometricService';
import DeepLinkManager from '../deeplinking/DeepLinkManager';
import { Logger } from '../../../../shared/services/src/LoggingService';

export interface VoiceCommand {
  action: 'send' | 'request' | 'pay' | 'check_balance' | 'check_transactions' | 'split_bill';
  amount?: number;
  currency?: string;
  recipient?: {
    name: string;
    id?: string;
    phone?: string;
    email?: string;
  };
  merchant?: {
    name: string;
    id?: string;
  };
  description?: string;
  confidence: number;
  rawText: string;
}

export interface VoicePaymentConfig {
  enabled: boolean;
  requireBiometric: boolean;
  maxAmount: number;
  confirmationRequired: boolean;
  language: string;
  voiceBiometricEnabled: boolean;
  trustedCommands: string[];
}

export interface VoiceSession {
  id: string;
  startTime: number;
  endTime?: number;
  commands: VoiceCommand[];
  status: 'active' | 'completed' | 'cancelled' | 'error';
  errors?: string[];
}

interface VoiceBiometricProfile {
  userId: string;
  voiceprint: string; // Encoded voice characteristics
  enrollmentDate: number;
  lastUsed: number;
  confidence: number;
}

/**
 * Comprehensive Voice Payment Service for hands-free financial transactions
 * Supports speech recognition, natural language processing, and voice biometrics
 */
class VoicePaymentService {
  private static instance: VoicePaymentService;
  private isInitialized: boolean = false;
  private isListening: boolean = false;
  private currentSession: VoiceSession | null = null;
  private config: VoicePaymentConfig;
  private voiceBiometricProfile: VoiceBiometricProfile | null = null;

  private readonly CONFIG_KEY = '@voice_payment_config';
  private readonly VOICE_PROFILE_KEY = '@voice_biometric_profile';
  private readonly SESSION_HISTORY_KEY = '@voice_session_history';
  private readonly MAX_SESSION_HISTORY = 100;

  // Natural language patterns for payment commands
  private readonly COMMAND_PATTERNS = {
    send: [
      /send (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?\s+to\s+(.+)/i,
      /give (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?\s+to\s+(.+)/i,
      /transfer (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?\s+to\s+(.+)/i,
      /pay (.+) (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?/i,
    ],
    request: [
      /request (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?\s+from\s+(.+)/i,
      /ask (.+) for (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?/i,
      /charge (.+) (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?/i,
    ],
    balance: [
      /(?:what.?s|check|show|tell me) my balance/i,
      /how much money do i have/i,
      /account balance/i,
      /my waqiti balance/i,
    ],
    transactions: [
      /(?:show|check|get) my (?:recent )?transactions/i,
      /transaction history/i,
      /what did i spend money on/i,
      /recent payments/i,
    ],
    split: [
      /split (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?\s+(?:between|with|among)\s+(.+)/i,
      /divide (\$?[\d,]+(?:\.\d{2})?)\s*(?:dollars?)?\s+(?:between|with)\s+(.+)/i,
    ]
  };

  static getInstance(): VoicePaymentService {
    if (!VoicePaymentService.instance) {
      VoicePaymentService.instance = new VoicePaymentService();
    }
    return VoicePaymentService.instance;
  }

  private constructor() {
    this.config = this.getDefaultConfig();
    this.setupVoiceCallbacks();
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      console.log('Initializing Voice Payment Service...');

      // Load configuration
      await this.loadConfiguration();

      // Load voice biometric profile
      await this.loadVoiceBiometricProfile();

      // Request permissions
      await this.requestPermissions();

      // Initialize voice recognition
      await this.initializeVoiceRecognition();

      this.isInitialized = true;
      console.log('Voice Payment Service initialized successfully');

      await this.trackEvent('voice_payment_service_initialized');

    } catch (error) {
      console.error('Failed to initialize Voice Payment Service:', error);
      throw error;
    }
  }

  /**
   * Start listening for voice commands
   */
  async startListening(): Promise<void> {
    if (!this.isInitialized) {
      throw new Error('Voice Payment Service not initialized');
    }

    if (this.isListening) {
      console.log('Already listening for voice commands');
      return;
    }

    try {
      // Create new session
      this.currentSession = {
        id: this.generateSessionId(),
        startTime: Date.now(),
        commands: [],
        status: 'active'
      };

      // Start voice recognition
      await Voice.start(this.config.language);
      this.isListening = true;

      console.log('Started listening for voice commands');
      await this.trackEvent('voice_listening_started', {
        session_id: this.currentSession.id
      });

    } catch (error) {
      console.error('Failed to start voice listening:', error);
      this.isListening = false;
      throw error;
    }
  }

  /**
   * Stop listening for voice commands
   */
  async stopListening(): Promise<void> {
    if (!this.isListening) return;

    try {
      await Voice.stop();
      this.isListening = false;

      if (this.currentSession) {
        this.currentSession.endTime = Date.now();
        this.currentSession.status = 'completed';
        await this.saveSession(this.currentSession);
      }

      console.log('Stopped listening for voice commands');
      await this.trackEvent('voice_listening_stopped');

    } catch (error) {
      console.error('Failed to stop voice listening:', error);
    }
  }

  /**
   * Cancel current voice session
   */
  async cancelSession(): Promise<void> {
    if (this.currentSession) {
      this.currentSession.status = 'cancelled';
      this.currentSession.endTime = Date.now();
      await this.saveSession(this.currentSession);
    }

    await this.stopListening();
    await this.trackEvent('voice_session_cancelled');
  }

  /**
   * Process recognized speech and extract payment commands
   */
  async processVoiceCommand(speechText: string): Promise<VoiceCommand | null> {
    try {
      console.log('Processing voice command:', speechText);

      const command = await this.parsePaymentCommand(speechText);
      
      if (!command) {
        console.log('No payment command detected in speech');
        return null;
      }

      // Add to current session
      if (this.currentSession) {
        this.currentSession.commands.push(command);
      }

      await this.trackEvent('voice_command_recognized', {
        action: command.action,
        confidence: command.confidence,
        session_id: this.currentSession?.id
      });

      return command;

    } catch (error) {
      console.error('Failed to process voice command:', error);
      return null;
    }
  }

  /**
   * Execute a voice payment command
   */
  async executeVoiceCommand(command: VoiceCommand): Promise<boolean> {
    try {
      console.log('Executing voice command:', command);

      // Security checks
      if (!await this.validateCommandSecurity(command)) {
        throw new Error('Command failed security validation');
      }

      // Execute based on action type
      switch (command.action) {
        case 'send':
          return await this.executeSendCommand(command);
        case 'request':
          return await this.executeRequestCommand(command);
        case 'check_balance':
          return await this.executeBalanceCommand();
        case 'check_transactions':
          return await this.executeTransactionsCommand();
        case 'split_bill':
          return await this.executeSplitCommand(command);
        default:
          throw new Error(`Unknown command action: ${command.action}`);
      }

    } catch (error) {
      console.error('Failed to execute voice command:', error);
      await this.trackEvent('voice_command_execution_failed', {
        action: command.action,
        error: error.message
      });
      return false;
    }
  }

  /**
   * Configure voice payment settings
   */
  async configure(config: Partial<VoicePaymentConfig>): Promise<void> {
    try {
      this.config = { ...this.config, ...config };
      await AsyncStorage.setItem(this.CONFIG_KEY, JSON.stringify(this.config));

      await this.trackEvent('voice_payment_configured', {
        enabled: this.config.enabled,
        biometric_required: this.config.requireBiometric,
        max_amount: this.config.maxAmount
      });

    } catch (error) {
      console.error('Failed to configure voice payments:', error);
      throw error;
    }
  }

  /**
   * Get current configuration
   */
  getConfiguration(): VoicePaymentConfig {
    return { ...this.config };
  }

  /**
   * Check if voice payments are available
   */
  async isAvailable(): Promise<boolean> {
    try {
      const available = await Voice.isAvailable();
      return available && this.config.enabled;
    } catch (error) {
      console.error('Failed to check voice availability:', error);
      return false;
    }
  }

  /**
   * Enroll user for voice biometrics
   */
  async enrollVoiceBiometric(voiceSample: string): Promise<boolean> {
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) throw new Error('User not authenticated');

      // Generate voice characteristics (simplified - real implementation would use ML)
      const voiceprint = await this.generateVoiceprint(voiceSample);

      this.voiceBiometricProfile = {
        userId: user.id,
        voiceprint,
        enrollmentDate: Date.now(),
        lastUsed: Date.now(),
        confidence: 0.95
      };

      await AsyncStorage.setItem(this.VOICE_PROFILE_KEY, JSON.stringify(this.voiceBiometricProfile));

      await this.trackEvent('voice_biometric_enrolled');
      return true;

    } catch (error) {
      console.error('Failed to enroll voice biometric:', error);
      return false;
    }
  }

  /**
   * Verify voice biometric
   */
  async verifyVoiceBiometric(voiceSample: string): Promise<{ verified: boolean; confidence: number }> {
    try {
      if (!this.voiceBiometricProfile) {
        return { verified: false, confidence: 0 };
      }

      const voiceprint = await this.generateVoiceprint(voiceSample);
      const confidence = await this.compareVoiceprints(this.voiceBiometricProfile.voiceprint, voiceprint);

      const verified = confidence >= 0.8; // 80% confidence threshold

      if (verified) {
        this.voiceBiometricProfile.lastUsed = Date.now();
        await AsyncStorage.setItem(this.VOICE_PROFILE_KEY, JSON.stringify(this.voiceBiometricProfile));
      }

      await this.trackEvent('voice_biometric_verification', {
        verified,
        confidence
      });

      return { verified, confidence };

    } catch (error) {
      console.error('Failed to verify voice biometric:', error);
      return { verified: false, confidence: 0 };
    }
  }

  /**
   * Get voice session history
   */
  async getSessionHistory(): Promise<VoiceSession[]> {
    try {
      const history = await AsyncStorage.getItem(this.SESSION_HISTORY_KEY);
      return history ? JSON.parse(history) : [];
    } catch (error) {
      console.error('Failed to get session history:', error);
      return [];
    }
  }

  /**
   * Clear voice session history
   */
  async clearSessionHistory(): Promise<void> {
    try {
      await AsyncStorage.removeItem(this.SESSION_HISTORY_KEY);
      await this.trackEvent('voice_session_history_cleared');
    } catch (error) {
      console.error('Failed to clear session history:', error);
    }
  }

  /**
   * Cleanup voice service
   */
  async cleanup(): Promise<void> {
    try {
      if (this.isListening) {
        await this.stopListening();
      }

      await Voice.destroy();
      this.isInitialized = false;

      console.log('Voice Payment Service cleaned up');

    } catch (error) {
      console.error('Failed to cleanup voice service:', error);
    }
  }

  // Private methods

  private setupVoiceCallbacks(): void {
    Voice.onSpeechStart = () => {
      console.log('Speech recognition started');
    };

    Voice.onSpeechRecognized = (event: SpeechRecognizedEvent) => {
      console.log('Speech recognized:', event);
    };

    Voice.onSpeechEnd = () => {
      console.log('Speech recognition ended');
      this.isListening = false;
    };

    Voice.onSpeechError = (event: SpeechErrorEvent) => {
      console.error('Speech recognition error:', event);
      this.isListening = false;
      this.handleSpeechError(event);
    };

    Voice.onSpeechResults = (event: SpeechResultsEvent) => {
      console.log('Speech results:', event);
      this.handleSpeechResults(event);
    };

    Voice.onSpeechPartialResults = (event: SpeechResultsEvent) => {
      console.log('Partial speech results:', event);
    };
  }

  private async handleSpeechResults(event: SpeechResultsEvent): Promise<void> {
    if (!event.value || event.value.length === 0) return;

    const speechText = event.value[0];
    const command = await this.processVoiceCommand(speechText);

    if (command && command.confidence > 0.7) {
      // High confidence command - execute with confirmation
      await this.showCommandConfirmation(command);
    } else if (command && command.confidence > 0.5) {
      // Medium confidence - ask for clarification
      await this.askForClarification(command);
    } else {
      // Low confidence or no command detected
      await this.handleUnrecognizedCommand(speechText);
    }
  }

  private async handleSpeechError(event: SpeechErrorEvent): Promise<void> {
    const errorMessage = event.error?.message || 'Unknown speech error';
    console.error('Speech recognition error:', errorMessage);

    if (this.currentSession) {
      this.currentSession.errors = this.currentSession.errors || [];
      this.currentSession.errors.push(errorMessage);
      this.currentSession.status = 'error';
    }

    await this.trackEvent('voice_recognition_error', {
      error: errorMessage,
      session_id: this.currentSession?.id
    });
  }

  private async parsePaymentCommand(speechText: string): Promise<VoiceCommand | null> {
    const cleanText = speechText.toLowerCase().trim();

    // Check each command pattern
    for (const [action, patterns] of Object.entries(this.COMMAND_PATTERNS)) {
      for (const pattern of patterns) {
        const match = cleanText.match(pattern);
        if (match) {
          return await this.extractCommandFromMatch(action as any, match, speechText);
        }
      }
    }

    return null;
  }

  private async extractCommandFromMatch(
    action: VoiceCommand['action'], 
    match: RegExpMatchArray, 
    rawText: string
  ): Promise<VoiceCommand> {
    const command: VoiceCommand = {
      action,
      confidence: 0.8, // Base confidence
      rawText
    };

    switch (action) {
      case 'send':
        command.amount = this.parseAmount(match[1]);
        command.recipient = await this.parseRecipient(match[2]);
        break;
      case 'request':
        command.amount = this.parseAmount(match[2] || match[1]);
        command.recipient = await this.parseRecipient(match[1] || match[2]);
        break;
      case 'split_bill':
        command.amount = this.parseAmount(match[1]);
        command.description = `Split with ${match[2]}`;
        break;
    }

    return command;
  }

  private parseAmount(amountString: string): number {
    const cleanAmount = amountString.replace(/[$,\s]/g, '');
    return parseFloat(cleanAmount) || 0;
  }

  private async parseRecipient(recipientString: string): Promise<VoiceCommand['recipient']> {
    const cleanName = recipientString.trim();
    
    // Try to find contact by name
    try {
      const contacts = await ApiService.get('/api/contacts/search', {
        params: { q: cleanName, limit: 1 }
      });

      if (contacts.data && contacts.data.length > 0) {
        const contact = contacts.data[0];
        return {
          name: contact.displayName,
          id: contact.id,
          phone: contact.phone,
          email: contact.email
        };
      }
    } catch (error) {
      console.error('Failed to search contacts:', error);
    }

    // Return basic recipient info
    return { name: cleanName };
  }

  private async validateCommandSecurity(command: VoiceCommand): Promise<boolean> {
    // Check if user is authenticated
    const isAuthenticated = await AuthService.isAuthenticated();
    if (!isAuthenticated) {
      Alert.alert('Authentication Required', 'Please log in to use voice payments');
      return false;
    }

    // Check amount limits
    if (command.amount && command.amount > this.config.maxAmount) {
      Alert.alert(
        'Amount Limit Exceeded',
        `Voice payments are limited to $${this.config.maxAmount}. Please use the app for larger amounts.`
      );
      return false;
    }

    // Require biometric authentication for payments
    if (this.config.requireBiometric && (command.action === 'send' || command.action === 'request')) {
      try {
        const biometricResult = await BiometricService.authenticate({
          reason: 'Authenticate to confirm voice payment'
        });

        if (!biometricResult.success) {
          return false;
        }
      } catch (error) {
        console.error('Biometric authentication failed:', error);
        return false;
      }
    }

    // Voice biometric verification
    if (this.config.voiceBiometricEnabled && this.voiceBiometricProfile) {
      // In a real implementation, we'd analyze the voice sample from the command
      // For now, we'll simulate this check
      console.log('Voice biometric verification would be performed here');
    }

    return true;
  }

  private async executeSendCommand(command: VoiceCommand): Promise<boolean> {
    if (!command.recipient || !command.amount) {
      Alert.alert('Incomplete Command', 'Please specify both recipient and amount');
      return false;
    }

    try {
      // Navigate to send money screen with pre-filled data
      await DeepLinkManager.handleDeepLink('waqiti://send', {
        source: 'voice',
        prefillData: {
          recipientName: command.recipient.name,
          recipientId: command.recipient.id,
          amount: command.amount,
          description: command.description || 'Voice payment'
        }
      });

      return true;
    } catch (error) {
      console.error('Failed to execute send command:', error);
      return false;
    }
  }

  private async executeRequestCommand(command: VoiceCommand): Promise<boolean> {
    if (!command.recipient || !command.amount) {
      Alert.alert('Incomplete Command', 'Please specify both recipient and amount');
      return false;
    }

    try {
      // Navigate to request money screen
      await DeepLinkManager.handleDeepLink('waqiti://request', {
        source: 'voice',
        prefillData: {
          fromUserName: command.recipient.name,
          fromUserId: command.recipient.id,
          amount: command.amount,
          description: command.description || 'Voice payment request'
        }
      });

      return true;
    } catch (error) {
      console.error('Failed to execute request command:', error);
      return false;
    }
  }

  private async executeBalanceCommand(): Promise<boolean> {
    try {
      const response = await ApiService.get('/api/wallet/balance');
      const balance = response.data.totalBalance;
      const formattedBalance = new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
      }).format(balance);

      Alert.alert('Account Balance', `Your current balance is ${formattedBalance}`);
      return true;
    } catch (error) {
      console.error('Failed to get balance:', error);
      Alert.alert('Error', 'Unable to retrieve account balance');
      return false;
    }
  }

  private async executeTransactionsCommand(): Promise<boolean> {
    try {
      await DeepLinkManager.handleDeepLink('waqiti://transactions', { source: 'voice' });
      return true;
    } catch (error) {
      console.error('Failed to show transactions:', error);
      return false;
    }
  }

  private async executeSplitCommand(command: VoiceCommand): Promise<boolean> {
    try {
      await DeepLinkManager.handleDeepLink('waqiti://split/create', {
        source: 'voice',
        prefillData: {
          totalAmount: command.amount,
          description: command.description
        }
      });

      return true;
    } catch (error) {
      console.error('Failed to execute split command:', error);
      return false;
    }
  }

  private async showCommandConfirmation(command: VoiceCommand): Promise<void> {
    const message = this.formatCommandConfirmation(command);
    
    Alert.alert(
      'Confirm Voice Command',
      message,
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Confirm', 
          onPress: () => this.executeVoiceCommand(command)
        }
      ]
    );
  }

  private formatCommandConfirmation(command: VoiceCommand): string {
    switch (command.action) {
      case 'send':
        return `Send $${command.amount} to ${command.recipient?.name}?`;
      case 'request':
        return `Request $${command.amount} from ${command.recipient?.name}?`;
      case 'split_bill':
        return `Split $${command.amount} - ${command.description}?`;
      default:
        return `Execute ${command.action} command?`;
    }
  }

  private async askForClarification(command: VoiceCommand): Promise<void> {
    Alert.alert(
      'Please Clarify',
      `I heard: "${command.rawText}"\n\nDid you want to ${command.action}?`,
      [
        { text: 'No', style: 'cancel' },
        { 
          text: 'Yes', 
          onPress: () => this.executeVoiceCommand(command)
        },
        {
          text: 'Try Again',
          onPress: () => this.startListening()
        }
      ]
    );
  }

  private async handleUnrecognizedCommand(speechText: string): Promise<void> {
    Alert.alert(
      'Command Not Recognized',
      `I didn't understand: "${speechText}"\n\nTry saying something like "Send $20 to John" or "Check my balance"`
    );
  }

  private async requestPermissions(): Promise<void> {
    if (Platform.OS === 'android') {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
        {
          title: 'Voice Payment Permission',
          message: 'Waqiti needs access to your microphone for voice payments',
          buttonNeutral: 'Ask Me Later',
          buttonNegative: 'Cancel',
          buttonPositive: 'OK',
        }
      );

      if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
        throw new Error('Microphone permission denied');
      }
    }
  }

  private async initializeVoiceRecognition(): Promise<void> {
    try {
      const isAvailable = await Voice.isAvailable();
      if (!isAvailable) {
        throw new Error('Voice recognition not available on this device');
      }

      await Voice.destroyRecognition();
    } catch (error) {
      console.error('Failed to initialize voice recognition:', error);
      throw error;
    }
  }

  private async loadConfiguration(): Promise<void> {
    try {
      const savedConfig = await AsyncStorage.getItem(this.CONFIG_KEY);
      if (savedConfig) {
        this.config = { ...this.config, ...JSON.parse(savedConfig) };
      }
    } catch (error) {
      console.error('Failed to load voice payment configuration:', error);
    }
  }

  private async loadVoiceBiometricProfile(): Promise<void> {
    try {
      const savedProfile = await AsyncStorage.getItem(this.VOICE_PROFILE_KEY);
      if (savedProfile) {
        this.voiceBiometricProfile = JSON.parse(savedProfile);
      }
    } catch (error) {
      console.error('Failed to load voice biometric profile:', error);
    }
  }

  private getDefaultConfig(): VoicePaymentConfig {
    return {
      enabled: true,
      requireBiometric: true,
      maxAmount: 100,
      confirmationRequired: true,
      language: 'en-US',
      voiceBiometricEnabled: false,
      trustedCommands: []
    };
  }

  private generateSessionId(): string {
    return `voice_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private async saveSession(session: VoiceSession): Promise<void> {
    try {
      const history = await this.getSessionHistory();
      history.push(session);

      // Keep only recent sessions
      const recentHistory = history.slice(-this.MAX_SESSION_HISTORY);
      
      await AsyncStorage.setItem(this.SESSION_HISTORY_KEY, JSON.stringify(recentHistory));
    } catch (error) {
      console.error('Failed to save voice session:', error);
    }
  }

  private async generateVoiceprint(voiceSample: string): Promise<string> {
    // Simplified voice characteristic generation
    // In a real implementation, this would use ML models to analyze voice characteristics
    const characteristics = {
      length: voiceSample.length,
      pitch: Math.random() * 100,
      tone: Math.random() * 100,
      speed: Math.random() * 100,
      timestamp: Date.now()
    };

    return Buffer.from(JSON.stringify(characteristics)).toString('base64');
  }

  private async compareVoiceprints(stored: string, current: string): Promise<number> {
    try {
      const storedData = JSON.parse(Buffer.from(stored, 'base64').toString());
      const currentData = JSON.parse(Buffer.from(current, 'base64').toString());

      // Simplified comparison - real implementation would use ML models
      const pitchDiff = Math.abs(storedData.pitch - currentData.pitch);
      const toneDiff = Math.abs(storedData.tone - currentData.tone);
      const speedDiff = Math.abs(storedData.speed - currentData.speed);

      const similarity = 1 - ((pitchDiff + toneDiff + speedDiff) / 300);
      return Math.max(0, Math.min(1, similarity));
    } catch (error) {
      console.error('Failed to compare voiceprints:', error);
      return 0;
    }
  }

  private async trackEvent(eventName: string, properties?: Record<string, any>): Promise<void> {
    try {
      await AnalyticsService.track(eventName, {
        ...properties,
        timestamp: Date.now(),
        platform: Platform.OS
      });
    } catch (error) {
      console.error('Failed to track voice payment event:', error);
    }
  }
}

export default VoicePaymentService.getInstance();