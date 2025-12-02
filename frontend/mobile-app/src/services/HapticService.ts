/**
 * HapticService - Manages haptic feedback for notifications and user interactions
 * Provides centralized haptic feedback management for different types of events
 */

import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface HapticSettings {
  enabled: boolean;
  paymentFeedback: boolean;
  securityFeedback: boolean;
  messageFeedback: boolean;
  buttonFeedback: boolean;
  notificationFeedback: boolean;
  intensity: 'light' | 'medium' | 'heavy';
}

class HapticService {
  private static instance: HapticService;
  private settings: HapticSettings;
  private isInitialized = false;

  constructor() {
    this.settings = {
      enabled: true,
      paymentFeedback: true,
      securityFeedback: true,
      messageFeedback: true,
      buttonFeedback: true,
      notificationFeedback: true,
      intensity: 'medium',
    };
  }

  static getInstance(): HapticService {
    if (!HapticService.instance) {
      HapticService.instance = new HapticService();
    }
    return HapticService.instance;
  }

  /**
   * Initialize the haptic service
   */
  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Load settings from storage
      await this.loadSettings();
      
      this.isInitialized = true;
      console.log('HapticService initialized successfully');
    } catch (error) {
      console.error('Failed to initialize HapticService:', error);
    }
  }

  /**
   * Check if haptic feedback is supported
   */
  isSupported(): boolean {
    return Platform.OS === 'ios' || Platform.OS === 'android';
  }

  // ==================== NOTIFICATION HAPTICS ====================

  /**
   * Notification haptic feedback based on type
   */
  async notification(notificationType?: string): Promise<void> {
    if (!this.shouldProvideHaptic('notificationFeedback')) return;

    try {
      switch (notificationType) {
        case 'payment':
        case 'payment_received':
        case 'payment_success':
          await this.success();
          break;
        
        case 'payment_failed':
        case 'transaction_failed':
        case 'error':
          await this.error();
          break;
        
        case 'security':
        case 'security_alert':
        case 'login_attempt':
        case 'account_locked':
          await this.warning();
          break;
        
        case 'money_request':
        case 'request':
        case 'message':
        case 'chat_message':
          await this.light();
          break;
        
        default:
          await this.light();
      }
    } catch (error) {
      console.error('Notification haptic feedback error:', error);
    }
  }

  // ==================== BASIC HAPTIC TYPES ====================

  /**
   * Light haptic feedback - for subtle interactions
   */
  async light(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    } catch (error) {
      console.error('Light haptic feedback error:', error);
    }
  }

  /**
   * Medium haptic feedback - for standard interactions
   */
  async medium(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    } catch (error) {
      console.error('Medium haptic feedback error:', error);
    }
  }

  /**
   * Heavy haptic feedback - for important interactions
   */
  async heavy(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    } catch (error) {
      console.error('Heavy haptic feedback error:', error);
    }
  }

  // ==================== NOTIFICATION FEEDBACK TYPES ====================

  /**
   * Success notification feedback
   */
  async success(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (error) {
      console.error('Success haptic feedback error:', error);
    }
  }

  /**
   * Warning notification feedback
   */
  async warning(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    } catch (error) {
      console.error('Warning haptic feedback error:', error);
    }
  }

  /**
   * Error notification feedback
   */
  async error(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    } catch (error) {
      console.error('Error haptic feedback error:', error);
    }
  }

  // ==================== SELECTION FEEDBACK ====================

  /**
   * Selection changed feedback - for pickers and toggles
   */
  async selectionChanged(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await Haptics.selectionAsync();
    } catch (error) {
      console.error('Selection haptic feedback error:', error);
    }
  }

  // ==================== CONTEXT-SPECIFIC HAPTICS ====================

  /**
   * Payment-related haptic feedback
   */
  async payment(type: 'success' | 'failed' | 'processing' = 'success'): Promise<void> {
    if (!this.shouldProvideHaptic('paymentFeedback')) return;

    try {
      switch (type) {
        case 'success':
          await this.success();
          break;
        case 'failed':
          await this.error();
          break;
        case 'processing':
          await this.light();
          break;
      }
    } catch (error) {
      console.error('Payment haptic feedback error:', error);
    }
  }

  /**
   * Security-related haptic feedback
   */
  async security(type: 'alert' | 'success' | 'error' = 'alert'): Promise<void> {
    if (!this.shouldProvideHaptic('securityFeedback')) return;

    try {
      switch (type) {
        case 'alert':
          await this.warning();
          break;
        case 'success':
          await this.success();
          break;
        case 'error':
          await this.error();
          break;
      }
    } catch (error) {
      console.error('Security haptic feedback error:', error);
    }
  }

  /**
   * Message-related haptic feedback
   */
  async message(): Promise<void> {
    if (!this.shouldProvideHaptic('messageFeedback')) return;

    try {
      await this.light();
    } catch (error) {
      console.error('Message haptic feedback error:', error);
    }
  }

  /**
   * Button press haptic feedback
   */
  async buttonPress(importance: 'low' | 'medium' | 'high' = 'medium'): Promise<void> {
    if (!this.shouldProvideHaptic('buttonFeedback')) return;

    try {
      switch (importance) {
        case 'low':
          await this.light();
          break;
        case 'medium':
          await this.medium();
          break;
        case 'high':
          await this.heavy();
          break;
      }
    } catch (error) {
      console.error('Button haptic feedback error:', error);
    }
  }

  // ==================== CUSTOM PATTERNS ====================

  /**
   * Double tap haptic pattern
   */
  async doubleTap(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await this.light();
      setTimeout(async () => {
        await this.light();
      }, 100);
    } catch (error) {
      console.error('Double tap haptic feedback error:', error);
    }
  }

  /**
   * Triple tap haptic pattern
   */
  async tripleTap(): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      await this.light();
      setTimeout(async () => {
        await this.light();
        setTimeout(async () => {
          await this.light();
        }, 100);
      }, 100);
    } catch (error) {
      console.error('Triple tap haptic feedback error:', error);
    }
  }

  /**
   * Pulse haptic pattern
   */
  async pulse(count: number = 3, interval: number = 200): Promise<void> {
    if (!this.settings.enabled) return;

    try {
      for (let i = 0; i < count; i++) {
        await this.getIntensityFeedback();
        if (i < count - 1) {
          await new Promise(resolve => setTimeout(resolve, interval));
        }
      }
    } catch (error) {
      console.error('Pulse haptic feedback error:', error);
    }
  }

  // ==================== INTENSITY-BASED FEEDBACK ====================

  /**
   * Get haptic feedback based on current intensity setting
   */
  private async getIntensityFeedback(): Promise<void> {
    switch (this.settings.intensity) {
      case 'light':
        await this.light();
        break;
      case 'medium':
        await this.medium();
        break;
      case 'heavy':
        await this.heavy();
        break;
    }
  }

  // ==================== SETTINGS MANAGEMENT ====================

  /**
   * Check if haptic feedback should be provided for a specific category
   */
  private shouldProvideHaptic(category: keyof HapticSettings): boolean {
    return this.settings.enabled && this.settings[category] === true;
  }

  /**
   * Enable/disable all haptic feedback
   */
  async setEnabled(enabled: boolean): Promise<void> {
    this.settings.enabled = enabled;
    await this.saveSettings();
  }

  /**
   * Set haptic intensity
   */
  async setIntensity(intensity: 'light' | 'medium' | 'heavy'): Promise<void> {
    this.settings.intensity = intensity;
    await this.saveSettings();
  }

  /**
   * Enable/disable payment haptic feedback
   */
  async setPaymentFeedbackEnabled(enabled: boolean): Promise<void> {
    this.settings.paymentFeedback = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable security haptic feedback
   */
  async setSecurityFeedbackEnabled(enabled: boolean): Promise<void> {
    this.settings.securityFeedback = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable message haptic feedback
   */
  async setMessageFeedbackEnabled(enabled: boolean): Promise<void> {
    this.settings.messageFeedback = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable button haptic feedback
   */
  async setButtonFeedbackEnabled(enabled: boolean): Promise<void> {
    this.settings.buttonFeedback = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable notification haptic feedback
   */
  async setNotificationFeedbackEnabled(enabled: boolean): Promise<void> {
    this.settings.notificationFeedback = enabled;
    await this.saveSettings();
  }

  /**
   * Get current settings
   */
  getSettings(): HapticSettings {
    return { ...this.settings };
  }

  /**
   * Update settings
   */
  async updateSettings(newSettings: Partial<HapticSettings>): Promise<void> {
    this.settings = { ...this.settings, ...newSettings };
    await this.saveSettings();
  }

  /**
   * Load settings from storage
   */
  private async loadSettings(): Promise<void> {
    try {
      const settingsJson = await AsyncStorage.getItem('@haptic_settings');
      if (settingsJson) {
        const savedSettings = JSON.parse(settingsJson);
        this.settings = { ...this.settings, ...savedSettings };
      }
    } catch (error) {
      console.error('Failed to load haptic settings:', error);
    }
  }

  /**
   * Save settings to storage
   */
  private async saveSettings(): Promise<void> {
    try {
      await AsyncStorage.setItem('@haptic_settings', JSON.stringify(this.settings));
    } catch (error) {
      console.error('Failed to save haptic settings:', error);
    }
  }

  /**
   * Test haptic feedback (for settings screen)
   */
  async testHaptic(type: 'light' | 'medium' | 'heavy' | 'success' | 'warning' | 'error'): Promise<void> {
    const originalEnabled = this.settings.enabled;
    this.settings.enabled = true; // Temporarily enable for testing

    try {
      switch (type) {
        case 'light':
          await this.light();
          break;
        case 'medium':
          await this.medium();
          break;
        case 'heavy':
          await this.heavy();
          break;
        case 'success':
          await this.success();
          break;
        case 'warning':
          await this.warning();
          break;
        case 'error':
          await this.error();
          break;
      }
    } finally {
      this.settings.enabled = originalEnabled;
    }
  }

  /**
   * Get available haptic categories
   */
  getHapticCategories(): Array<{ key: string; name: string; enabled: boolean }> {
    return [
      { key: 'paymentFeedback', name: 'Payment Feedback', enabled: this.settings.paymentFeedback },
      { key: 'securityFeedback', name: 'Security Feedback', enabled: this.settings.securityFeedback },
      { key: 'messageFeedback', name: 'Message Feedback', enabled: this.settings.messageFeedback },
      { key: 'buttonFeedback', name: 'Button Feedback', enabled: this.settings.buttonFeedback },
      { key: 'notificationFeedback', name: 'Notification Feedback', enabled: this.settings.notificationFeedback },
    ];
  }

  /**
   * Reset settings to defaults
   */
  async resetToDefaults(): Promise<void> {
    this.settings = {
      enabled: true,
      paymentFeedback: true,
      securityFeedback: true,
      messageFeedback: true,
      buttonFeedback: true,
      notificationFeedback: true,
      intensity: 'medium',
    };
    await this.saveSettings();
  }
}

export const HapticService = HapticService.getInstance();
export default HapticService;