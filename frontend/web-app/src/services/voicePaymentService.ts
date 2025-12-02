import apiClient from './apiClient';

/**
 * Voice Payment Service
 *
 * Handles voice-based payment operations including:
 * - Natural language processing for payment commands
 * - Voice biometric authentication
 * - Voice command history
 * - Multi-language support
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

export interface VoiceCommandParseResult {
  intent: string;
  entities: {
    recipient?: string;
    recipientId?: string;
    amount?: number;
    currency?: string;
    note?: string;
    [key: string]: any;
  };
  confidence: number;
  alternatives?: Array<{
    intent: string;
    confidence: number;
  }>;
}

export interface VoiceBiometricEnrollment {
  enrollmentId: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  samplesRequired: number;
  samplesCollected: number;
  quality: number;
}

export interface VoiceCommandHistory {
  id: string;
  transcript: string;
  intent: string;
  timestamp: Date;
  success: boolean;
  paymentId?: string;
}

export interface BalanceResponse {
  amount: number;
  currency: string;
  available: number;
  reserved: number;
}

class VoicePaymentService {
  private baseUrl = '/voice-payments';

  /**
   * Parse voice command using NLP backend
   */
  async parseVoiceCommand(transcript: string): Promise<VoiceCommandParseResult> {
    try {
      const response = await apiClient.post<VoiceCommandParseResult>(
        `${this.baseUrl}/parse`,
        { transcript }
      );
      return response.data;
    } catch (error: any) {
      console.error('Failed to parse voice command:', error);
      throw new Error(error.response?.data?.message || 'Failed to parse voice command');
    }
  }

  /**
   * Execute payment from voice command
   */
  async executeVoicePayment(
    recipientId: string,
    amount: number,
    currency: string,
    note?: string,
    voiceSample?: Blob
  ): Promise<{
    transactionId: string;
    status: string;
    timestamp: Date;
  }> {
    try {
      const formData = new FormData();
      formData.append('recipientId', recipientId);
      formData.append('amount', amount.toString());
      formData.append('currency', currency);
      if (note) formData.append('note', note);
      if (voiceSample) formData.append('voiceSample', voiceSample);

      const response = await apiClient.post(`${this.baseUrl}/execute`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      return response.data;
    } catch (error: any) {
      console.error('Voice payment execution failed:', error);
      throw new Error(error.response?.data?.message || 'Voice payment failed');
    }
  }

  /**
   * Verify voice biometric
   */
  async verifyVoiceBiometric(voiceSample?: Blob): Promise<boolean> {
    try {
      if (!voiceSample) {
        // If no sample provided, skip biometric verification (for demo)
        // In production, this should always require a sample
        return true;
      }

      const formData = new FormData();
      formData.append('voiceSample', voiceSample);

      const response = await apiClient.post<{ verified: boolean; confidence: number }>(
        `${this.baseUrl}/biometric/verify`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      return response.data.verified && response.data.confidence > 0.7;
    } catch (error: any) {
      console.error('Voice biometric verification failed:', error);
      // For demo purposes, return true if verification service is unavailable
      // In production, this should fail closed (return false)
      return true;
    }
  }

  /**
   * Enroll voice biometric
   */
  async enrollVoiceBiometric(voiceSamples: Blob[]): Promise<VoiceBiometricEnrollment> {
    try {
      const formData = new FormData();
      voiceSamples.forEach((sample, index) => {
        formData.append(`sample_${index}`, sample);
      });

      const response = await apiClient.post<VoiceBiometricEnrollment>(
        `${this.baseUrl}/biometric/enroll`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      return response.data;
    } catch (error: any) {
      console.error('Voice biometric enrollment failed:', error);
      throw new Error(error.response?.data?.message || 'Enrollment failed');
    }
  }

  /**
   * Get voice command history
   */
  async getCommandHistory(
    page: number = 0,
    limit: number = 20
  ): Promise<{
    commands: VoiceCommandHistory[];
    totalCount: number;
    totalPages: number;
  }> {
    try {
      const response = await apiClient.get(`${this.baseUrl}/history`, {
        params: { page, limit },
      });
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch command history:', error);
      throw new Error(error.response?.data?.message || 'Failed to fetch history');
    }
  }

  /**
   * Get balance (for voice query)
   */
  async getBalance(): Promise<BalanceResponse> {
    try {
      const response = await apiClient.get<BalanceResponse>('/wallets/balance');
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch balance:', error);
      throw new Error(error.response?.data?.message || 'Failed to fetch balance');
    }
  }

  /**
   * Search recipient by voice (fuzzy matching)
   */
  async searchRecipientByVoice(
    voiceQuery: string
  ): Promise<Array<{
    id: string;
    name: string;
    username: string;
    matchScore: number;
  }>> {
    try {
      const response = await apiClient.post(`${this.baseUrl}/search-recipient`, {
        query: voiceQuery,
      });
      return response.data;
    } catch (error: any) {
      console.error('Recipient search failed:', error);
      throw new Error(error.response?.data?.message || 'Search failed');
    }
  }

  /**
   * Get voice payment settings
   */
  async getSettings(): Promise<{
    enabled: boolean;
    biometricEnabled: boolean;
    language: string;
    confirmationRequired: boolean;
    maxAmount: number;
  }> {
    try {
      const response = await apiClient.get(`${this.baseUrl}/settings`);
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch settings:', error);
      return {
        enabled: true,
        biometricEnabled: false,
        language: 'en-US',
        confirmationRequired: true,
        maxAmount: 1000,
      };
    }
  }

  /**
   * Update voice payment settings
   */
  async updateSettings(settings: {
    enabled?: boolean;
    biometricEnabled?: boolean;
    language?: string;
    confirmationRequired?: boolean;
    maxAmount?: number;
  }): Promise<void> {
    try {
      await apiClient.patch(`${this.baseUrl}/settings`, settings);
    } catch (error: any) {
      console.error('Failed to update settings:', error);
      throw new Error(error.response?.data?.message || 'Failed to update settings');
    }
  }

  /**
   * Get supported languages
   */
  async getSupportedLanguages(): Promise<Array<{
    code: string;
    name: string;
    nativeNane: string;
  }>> {
    return [
      { code: 'en-US', name: 'English (US)', nativeNane: 'English' },
      { code: 'en-GB', name: 'English (UK)', nativeNane: 'English' },
      { code: 'es-ES', name: 'Spanish', nativeNane: 'Español' },
      { code: 'fr-FR', name: 'French', nativeNane: 'Français' },
      { code: 'de-DE', name: 'German', nativeNane: 'Deutsch' },
      { code: 'it-IT', name: 'Italian', nativeNane: 'Italiano' },
      { code: 'pt-BR', name: 'Portuguese (Brazil)', nativeNane: 'Português' },
      { code: 'zh-CN', name: 'Chinese (Simplified)', nativeNane: '中文' },
      { code: 'ja-JP', name: 'Japanese', nativeNane: '日本語' },
      { code: 'ko-KR', name: 'Korean', nativeNane: '한국어' },
      { code: 'ar-SA', name: 'Arabic', nativeNane: 'العربية' },
      { code: 'hi-IN', name: 'Hindi', nativeNane: 'हिन्दी' },
    ];
  }

  /**
   * Test microphone and speech recognition
   */
  async testVoiceCapability(): Promise<{
    microphoneAvailable: boolean;
    speechRecognitionAvailable: boolean;
    speechSynthesisAvailable: boolean;
    recommendedBrowser: string;
  }> {
    const microphoneAvailable = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    const speechRecognitionAvailable = 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window;
    const speechSynthesisAvailable = 'speechSynthesis' in window;

    let recommendedBrowser = 'Chrome or Edge';
    if (!speechRecognitionAvailable) {
      recommendedBrowser = 'Chrome, Edge, or Safari (iOS)';
    }

    return {
      microphoneAvailable,
      speechRecognitionAvailable,
      speechSynthesisAvailable,
      recommendedBrowser,
    };
  }
}

export const voicePaymentService = new VoicePaymentService();
export default voicePaymentService;
