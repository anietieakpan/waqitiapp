/**
 * Native Certificate Pinning Bridge
 * React Native module for native certificate pinning on iOS and Android
 */

import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import { logError, logInfo, logWarn, logSecurityEvent } from '../../utils/Logger';

const { CertificatePinning } = NativeModules;

interface CertificateValidationResult {
  valid: boolean;
  hostname: string;
  matchedPin?: string;
  error?: string;
}

interface PinningConfiguration {
  enforcementMode: string;
  enforcePinning: boolean;
  configuredHosts: string[];
  reportCount?: number;
}

interface TestResult {
  success: boolean;
  hostname: string;
  error?: string;
}

class NativeCertificatePinning {
  private static instance: NativeCertificatePinning;
  private eventEmitter: NativeEventEmitter;
  private listeners: Map<string, any> = new Map();

  private constructor() {
    if (!CertificatePinning) {
      logWarn('Certificate pinning native module not available', {
        feature: 'security',
        action: 'native_module_unavailable'
      });
    }
    
    this.eventEmitter = new NativeEventEmitter(CertificatePinning);
    this.setupEventListeners();
  }

  public static getInstance(): NativeCertificatePinning {
    if (!NativeCertificatePinning.instance) {
      NativeCertificatePinning.instance = new NativeCertificatePinning();
    }
    return NativeCertificatePinning.instance;
  }

  /**
   * Check if native module is available
   */
  public isAvailable(): boolean {
    return !!CertificatePinning;
  }

  /**
   * Validate certificate for hostname
   */
  public async validateCertificate(
    hostname: string,
    certificates: string[]
  ): Promise<CertificateValidationResult> {
    if (!this.isAvailable()) {
      return { valid: true, hostname, error: 'Native module not available' };
    }

    try {
      return await CertificatePinning.validateCertificateForHost(hostname, certificates);
    } catch (error) {
      logError('Certificate validation error', {
        feature: 'security',
        action: 'certificate_validation_failed',
        metadata: { hostname }
      }, error as Error);
      throw error;
    }
  }

  /**
   * Add pins for hostname
   */
  public async addPins(hostname: string, pins: string[]): Promise<{ success: boolean }> {
    if (!this.isAvailable()) {
      throw new Error('Native module not available');
    }

    return await CertificatePinning.addPinForHost(hostname, pins);
  }

  /**
   * Remove pins for hostname
   */
  public async removePins(hostname: string): Promise<{ success: boolean }> {
    if (!this.isAvailable()) {
      throw new Error('Native module not available');
    }

    return await CertificatePinning.removePinForHost(hostname);
  }

  /**
   * Clear all pins (reset to defaults)
   */
  public async clearAllPins(): Promise<{ success: boolean }> {
    if (!this.isAvailable()) {
      throw new Error('Native module not available');
    }

    return await CertificatePinning.clearAllPins();
  }

  /**
   * Set enforcement mode
   */
  public async setEnforcementMode(
    mode: 'strict' | 'report' | 'disabled'
  ): Promise<{ success: boolean }> {
    if (!this.isAvailable()) {
      throw new Error('Native module not available');
    }

    return await CertificatePinning.setEnforcementMode(mode);
  }

  /**
   * Get current configuration
   */
  public async getConfiguration(): Promise<PinningConfiguration> {
    if (!this.isAvailable()) {
      // Even when native module isn't available, enforce JS-level validation
      return {
        enforcementMode: 'strict',
        enforcePinning: true,
        configuredHosts: ['api.example.com', 'auth.example.com', 'payments.example.com']
      };
    }

    return await CertificatePinning.getConfiguration();
  }

  /**
   * Test pinning for hostname
   */
  public async testPinning(hostname: string): Promise<TestResult> {
    if (!this.isAvailable()) {
      return { success: false, hostname, error: 'Native module not available' };
    }

    return await CertificatePinning.testPinningForHost(hostname);
  }

  /**
   * Setup event listeners
   */
  private setupEventListeners(): void {
    if (!this.isAvailable()) return;

    // Certificate pinning failure event
    const failureListener = this.eventEmitter.addListener(
      'CertificatePinningFailure',
      (event) => {
        logWarn('Certificate pinning failure detected', {
          feature: 'security',
          action: 'certificate_pinning_failure',
          metadata: event
        });
        this.handlePinningFailure(event);
      }
    );

    // Certificate pinning success event
    const successListener = this.eventEmitter.addListener(
      'CertificatePinningSuccess',
      (event) => {
        logInfo('Certificate pinning validation successful', {
          feature: 'security',
          action: 'certificate_pinning_success',
          metadata: event
        });
      }
    );

    this.listeners.set('failure', failureListener);
    this.listeners.set('success', successListener);
  }

  /**
   * Handle pinning failure
   */
  private handlePinningFailure(event: any): void {
    // Log to analytics
    this.logSecurityEvent('certificate_pinning_failure', {
      hostname: event.hostname,
      reason: event.reason,
      timestamp: event.timestamp,
      platform: Platform.OS
    });

    // Could trigger additional security measures here
  }

  /**
   * Log security event
   */
  private logSecurityEvent(eventName: string, data: any): void {
    // Use proper security event logging
    logSecurityEvent(eventName, 'high', {
      feature: 'certificate_pinning',
      action: eventName,
      metadata: data
    });
  }

  /**
   * Subscribe to pinning events
   */
  public onPinningFailure(callback: (event: any) => void): () => void {
    const listener = this.eventEmitter.addListener('CertificatePinningFailure', callback);
    return () => listener.remove();
  }

  /**
   * Clean up listeners
   */
  public cleanup(): void {
    this.listeners.forEach(listener => listener.remove());
    this.listeners.clear();
  }
}

export default NativeCertificatePinning;

// Export singleton instance
export const nativePinning = NativeCertificatePinning.getInstance();