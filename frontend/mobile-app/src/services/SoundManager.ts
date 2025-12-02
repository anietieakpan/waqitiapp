/**
 * SoundManager - Manages notification sounds and audio feedback
 * Provides centralized sound management for different types of notifications
 */

import { Audio, AVPlaybackStatus } from 'expo-av';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Sound categories and their corresponding files
interface SoundAssets {
  payment: string;
  request: string;
  message: string;
  security: string;
  promotion: string;
  error: string;
  success: string;
  warning: string;
  default: string;
}

interface SoundSettings {
  enabled: boolean;
  volume: number;
  paymentSounds: boolean;
  securitySounds: boolean;
  messageSounds: boolean;
  promotionalSounds: boolean;
}

class SoundManager {
  private static instance: SoundManager;
  private sounds: Map<string, Audio.Sound> = new Map();
  private soundAssets: SoundAssets;
  private settings: SoundSettings;
  private isInitialized = false;

  constructor() {
    this.soundAssets = {
      payment: require('../assets/sounds/payment_success.mp3'),
      request: require('../assets/sounds/money_request.mp3'),
      message: require('../assets/sounds/message_tone.mp3'),
      security: require('../assets/sounds/security_alert.mp3'),
      promotion: require('../assets/sounds/notification_soft.mp3'),
      error: require('../assets/sounds/error_tone.mp3'),
      success: require('../assets/sounds/success_chime.mp3'),
      warning: require('../assets/sounds/warning_tone.mp3'),
      default: require('../assets/sounds/default_notification.mp3'),
    };

    this.settings = {
      enabled: true,
      volume: 0.7,
      paymentSounds: true,
      securitySounds: true,
      messageSounds: true,
      promotionalSounds: false,
    };
  }

  static getInstance(): SoundManager {
    if (!SoundManager.instance) {
      SoundManager.instance = new SoundManager();
    }
    return SoundManager.instance;
  }

  /**
   * Initialize the sound manager
   */
  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Configure audio mode
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: false,
        interruptionModeIOS: Audio.INTERRUPTION_MODE_IOS_DO_NOT_MIX,
        playsInSilentModeIOS: true,
        shouldDuckAndroid: true,
        interruptionModeAndroid: Audio.INTERRUPTION_MODE_ANDROID_DO_NOT_MIX,
        playThroughEarpieceAndroid: false,
      });

      // Load settings from storage
      await this.loadSettings();

      // Preload critical sounds
      const criticalSounds = ['payment', 'security', 'error'];
      await Promise.all(
        criticalSounds.map(soundKey => this.preloadSound(soundKey))
      );

      this.isInitialized = true;
      console.log('SoundManager initialized successfully');
    } catch (error) {
      console.error('Failed to initialize SoundManager:', error);
    }
  }

  /**
   * Preload a sound file
   */
  private async preloadSound(soundKey: keyof SoundAssets): Promise<void> {
    try {
      if (this.sounds.has(soundKey)) {
        return; // Already loaded
      }

      const { sound } = await Audio.Sound.createAsync(
        this.soundAssets[soundKey],
        { shouldPlay: false, volume: this.settings.volume }
      );

      this.sounds.set(soundKey, sound);
      console.log(`Preloaded sound: ${soundKey}`);
    } catch (error) {
      console.error(`Failed to preload sound ${soundKey}:`, error);
    }
  }

  /**
   * Play notification sound based on type
   */
  async playNotificationSound(notificationType: string): Promise<void> {
    if (!this.settings.enabled) {
      return;
    }

    // Check if specific category is enabled
    if (!this.isSoundEnabledForType(notificationType)) {
      return;
    }

    const soundKey = this.getSoundKeyForType(notificationType);
    await this.playSound(soundKey);
  }

  /**
   * Play a specific sound
   */
  async playSound(soundKey: keyof SoundAssets): Promise<void> {
    try {
      if (!this.settings.enabled) {
        return;
      }

      let sound = this.sounds.get(soundKey);
      
      if (!sound) {
        // Load sound if not preloaded
        await this.preloadSound(soundKey);
        sound = this.sounds.get(soundKey);
      }

      if (!sound) {
        console.error(`Sound not found: ${soundKey}`);
        return;
      }

      // Check if sound is already playing
      const status = await sound.getStatusAsync();
      if (status.isLoaded && status.isPlaying) {
        // Stop current playback and restart
        await sound.stopAsync();
        await sound.setPositionAsync(0);
      }

      // Set volume and play
      await sound.setVolumeAsync(this.settings.volume);
      await sound.playAsync();

    } catch (error) {
      console.error(`Failed to play sound ${soundKey}:`, error);
    }
  }

  /**
   * Play payment success sound
   */
  async playPaymentSuccess(): Promise<void> {
    await this.playSound('payment');
  }

  /**
   * Play money request sound
   */
  async playMoneyRequest(): Promise<void> {
    await this.playSound('request');
  }

  /**
   * Play message sound
   */
  async playMessageSound(): Promise<void> {
    await this.playSound('message');
  }

  /**
   * Play security alert sound
   */
  async playSecurityAlert(): Promise<void> {
    await this.playSound('security');
  }

  /**
   * Play error sound
   */
  async playError(): Promise<void> {
    await this.playSound('error');
  }

  /**
   * Play success sound
   */
  async playSuccess(): Promise<void> {
    await this.playSound('success');
  }

  /**
   * Play warning sound
   */
  async playWarning(): Promise<void> {
    await this.playSound('warning');
  }

  /**
   * Play default notification sound
   */
  async playDefault(): Promise<void> {
    await this.playSound('default');
  }

  /**
   * Get sound key for notification type
   */
  private getSoundKeyForType(notificationType: string): keyof SoundAssets {
    const typeMap: Record<string, keyof SoundAssets> = {
      'payment': 'payment',
      'payment_received': 'payment',
      'payment_sent': 'payment',
      'payment_success': 'success',
      'payment_failed': 'error',
      'money_request': 'request',
      'request': 'request',
      'message': 'message',
      'chat_message': 'message',
      'security': 'security',
      'security_alert': 'security',
      'login_attempt': 'security',
      'account_locked': 'error',
      'promotion': 'promotion',
      'marketing': 'promotion',
      'error': 'error',
      'warning': 'warning',
      'info': 'default',
    };

    return typeMap[notificationType] || 'default';
  }

  /**
   * Check if sound is enabled for specific type
   */
  private isSoundEnabledForType(notificationType: string): boolean {
    switch (notificationType) {
      case 'payment':
      case 'payment_received':
      case 'payment_sent':
      case 'money_request':
        return this.settings.paymentSounds;
      
      case 'security':
      case 'security_alert':
      case 'login_attempt':
        return this.settings.securitySounds;
      
      case 'message':
      case 'chat_message':
        return this.settings.messageSounds;
      
      case 'promotion':
      case 'marketing':
        return this.settings.promotionalSounds;
      
      default:
        return true; // Default to enabled for other types
    }
  }

  /**
   * Stop all playing sounds
   */
  async stopAllSounds(): Promise<void> {
    try {
      const stopPromises = Array.from(this.sounds.values()).map(async (sound) => {
        try {
          const status = await sound.getStatusAsync();
          if (status.isLoaded && status.isPlaying) {
            await sound.stopAsync();
          }
        } catch (error) {
          console.error('Error stopping sound:', error);
        }
      });

      await Promise.all(stopPromises);
    } catch (error) {
      console.error('Failed to stop all sounds:', error);
    }
  }

  /**
   * Set volume for all sounds (0.0 to 1.0)
   */
  async setVolume(volume: number): Promise<void> {
    this.settings.volume = Math.max(0, Math.min(1, volume));
    
    // Update volume for all loaded sounds
    const volumePromises = Array.from(this.sounds.values()).map(async (sound) => {
      try {
        await sound.setVolumeAsync(this.settings.volume);
      } catch (error) {
        console.error('Error setting volume:', error);
      }
    });

    await Promise.all(volumePromises);
    await this.saveSettings();
  }

  /**
   * Enable/disable all sounds
   */
  async setSoundsEnabled(enabled: boolean): Promise<void> {
    this.settings.enabled = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable payment sounds
   */
  async setPaymentSoundsEnabled(enabled: boolean): Promise<void> {
    this.settings.paymentSounds = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable security sounds
   */
  async setSecuritySoundsEnabled(enabled: boolean): Promise<void> {
    this.settings.securitySounds = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable message sounds
   */
  async setMessageSoundsEnabled(enabled: boolean): Promise<void> {
    this.settings.messageSounds = enabled;
    await this.saveSettings();
  }

  /**
   * Enable/disable promotional sounds
   */
  async setPromotionalSoundsEnabled(enabled: boolean): Promise<void> {
    this.settings.promotionalSounds = enabled;
    await this.saveSettings();
  }

  /**
   * Get current settings
   */
  getSettings(): SoundSettings {
    return { ...this.settings };
  }

  /**
   * Update settings
   */
  async updateSettings(newSettings: Partial<SoundSettings>): Promise<void> {
    this.settings = { ...this.settings, ...newSettings };
    
    // Update volume for loaded sounds if volume changed
    if (newSettings.volume !== undefined) {
      await this.setVolume(newSettings.volume);
    } else {
      await this.saveSettings();
    }
  }

  /**
   * Load settings from storage
   */
  private async loadSettings(): Promise<void> {
    try {
      const settingsJson = await AsyncStorage.getItem('@sound_settings');
      if (settingsJson) {
        const savedSettings = JSON.parse(settingsJson);
        this.settings = { ...this.settings, ...savedSettings };
      }
    } catch (error) {
      console.error('Failed to load sound settings:', error);
    }
  }

  /**
   * Save settings to storage
   */
  private async saveSettings(): Promise<void> {
    try {
      await AsyncStorage.setItem('@sound_settings', JSON.stringify(this.settings));
    } catch (error) {
      console.error('Failed to save sound settings:', error);
    }
  }

  /**
   * Cleanup resources
   */
  async cleanup(): Promise<void> {
    try {
      await this.stopAllSounds();
      
      // Unload all sounds
      const unloadPromises = Array.from(this.sounds.values()).map(async (sound) => {
        try {
          await sound.unloadAsync();
        } catch (error) {
          console.error('Error unloading sound:', error);
        }
      });

      await Promise.all(unloadPromises);
      this.sounds.clear();
      this.isInitialized = false;
    } catch (error) {
      console.error('Failed to cleanup SoundManager:', error);
    }
  }

  /**
   * Test a sound (for settings screen)
   */
  async testSound(soundKey: keyof SoundAssets): Promise<void> {
    const originalEnabled = this.settings.enabled;
    this.settings.enabled = true; // Temporarily enable for testing
    await this.playSound(soundKey);
    this.settings.enabled = originalEnabled;
  }

  /**
   * Check if sounds are supported on the current platform
   */
  isSoundSupported(): boolean {
    return Platform.OS === 'ios' || Platform.OS === 'android';
  }

  /**
   * Get available sound categories
   */
  getSoundCategories(): Array<{ key: string; name: string; enabled: boolean }> {
    return [
      { key: 'paymentSounds', name: 'Payment Sounds', enabled: this.settings.paymentSounds },
      { key: 'securitySounds', name: 'Security Alerts', enabled: this.settings.securitySounds },
      { key: 'messageSounds', name: 'Message Sounds', enabled: this.settings.messageSounds },
      { key: 'promotionalSounds', name: 'Promotional Sounds', enabled: this.settings.promotionalSounds },
    ];
  }
}

export const SoundManager = SoundManager.getInstance();
export default SoundManager;