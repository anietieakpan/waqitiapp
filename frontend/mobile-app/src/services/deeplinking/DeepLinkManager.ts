import { NavigationContainerRef } from '@react-navigation/native';
import { DeepLinkRouter, DeepLinkContext, DeepLinkResult } from './DeepLinkRouter';
import DeepLinkingService from '../DeepLinkingService';
import { AuthService } from '../auth/AuthService';
import { AnalyticsService } from '../AnalyticsService';

export interface DeepLinkManagerConfig {
  enableLegacyMode: boolean;
  preferNewRouter: boolean;
  trackingEnabled: boolean;
}

/**
 * Enhanced Deep Link Manager that combines the legacy DeepLinkingService
 * with the new DeepLinkRouter for improved functionality and backward compatibility
 */
export class DeepLinkManager {
  private static instance: DeepLinkManager;
  private router: DeepLinkRouter;
  private config: DeepLinkManagerConfig;
  private isInitialized: boolean = false;

  private constructor(config: Partial<DeepLinkManagerConfig> = {}) {
    this.config = {
      enableLegacyMode: true,
      preferNewRouter: true,
      trackingEnabled: true,
      ...config
    };
    this.router = new DeepLinkRouter();
  }

  static getInstance(config?: Partial<DeepLinkManagerConfig>): DeepLinkManager {
    if (!DeepLinkManager.instance) {
      DeepLinkManager.instance = new DeepLinkManager(config);
    }
    return DeepLinkManager.instance;
  }

  async initialize(navigationRef: NavigationContainerRef<any>): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Initialize new router
      this.router.setNavigationRef(navigationRef);

      // Initialize legacy service if enabled
      if (this.config.enableLegacyMode) {
        await DeepLinkingService.initialize();
      }

      this.isInitialized = true;
      console.log('DeepLinkManager initialized successfully');
    } catch (error) {
      console.error('Failed to initialize DeepLinkManager:', error);
      throw error;
    }
  }

  /**
   * Handle a deep link URL using the enhanced router or legacy service
   */
  async handleDeepLink(url: string, context: Partial<DeepLinkContext> = {}): Promise<DeepLinkResult> {
    if (!this.isInitialized) {
      throw new Error('DeepLinkManager not initialized');
    }

    if (this.config.trackingEnabled) {
      await this.trackDeepLinkAttempt(url, context);
    }

    try {
      if (this.config.preferNewRouter) {
        // Try new router first
        const result = await this.router.route(url, context);
        
        if (result.success) {
          return result;
        }

        // Fallback to legacy service if enabled and new router failed
        if (this.config.enableLegacyMode) {
          console.log('New router failed, falling back to legacy service');
          await this.handleWithLegacyService(url);
          return { success: true, route: 'Legacy' };
        }

        return result;
      } else {
        // Use legacy service first
        if (this.config.enableLegacyMode) {
          await this.handleWithLegacyService(url);
          return { success: true, route: 'Legacy' };
        }

        // Fallback to new router
        return await this.router.route(url, context);
      }
    } catch (error) {
      console.error('Deep link handling failed:', error);
      
      if (this.config.trackingEnabled) {
        await this.trackDeepLinkError(url, error);
      }

      return {
        success: false,
        errorCode: 'HANDLING_ERROR',
        errorMessage: error.message
      };
    }
  }

  /**
   * Generate a deep link URL using the new router
   */
  generateDeepLink(pattern: string, params: Record<string, any> = {}, options: {
    source?: string;
    campaign?: string;
    utmParams?: Record<string, string>;
  } = {}): string {
    return this.router.generateURL(pattern, params, options);
  }

  /**
   * Create a payment link using the legacy service
   */
  async createPaymentLink(params: {
    amount: number;
    currency: string;
    description?: string;
    recipientId?: string;
    merchantId?: string;
  }): Promise<string> {
    if (!this.config.enableLegacyMode) {
      throw new Error('Legacy mode disabled');
    }
    return DeepLinkingService.createPaymentLink(params);
  }

  /**
   * Create a referral link using the legacy service
   */
  async createReferralLink(userId: string): Promise<string> {
    if (!this.config.enableLegacyMode) {
      throw new Error('Legacy mode disabled');
    }
    return DeepLinkingService.createReferralLink(userId);
  }

  /**
   * Create a split bill link using the legacy service
   */
  async createSplitBillLink(splitData: {
    splitId: string;
    totalAmount: number;
    currency: string;
    participants: number;
  }): Promise<string> {
    if (!this.config.enableLegacyMode) {
      throw new Error('Legacy mode disabled');
    }
    return DeepLinkingService.createSplitBillLink(splitData);
  }

  /**
   * Test if a URL would match any route
   */
  testURL(url: string): { matches: boolean; route?: any; params?: Record<string, any> } {
    return this.router.testURL(url);
  }

  /**
   * Get all registered routes (for debugging)
   */
  getRoutes(): Array<{ pattern: string; route: any }> {
    return this.router.getRoutes();
  }

  /**
   * Get deep link history from legacy service
   */
  getHistory(): Array<{ url: string; timestamp: number }> {
    if (!this.config.enableLegacyMode) {
      return [];
    }
    return DeepLinkingService.getHistory();
  }

  /**
   * Handle pending deep links from legacy service
   */
  async handlePendingDeepLinks(): Promise<void> {
    if (this.config.enableLegacyMode) {
      await DeepLinkingService.handlePendingDeepLink();
    }
  }

  /**
   * Register a custom route with the new router
   */
  registerRoute(route: any): void {
    this.router.registerRoute(route);
  }

  /**
   * Update configuration
   */
  updateConfig(newConfig: Partial<DeepLinkManagerConfig>): void {
    this.config = { ...this.config, ...newConfig };
  }

  /**
   * Destroy the manager and clean up resources
   */
  destroy(): void {
    if (this.config.enableLegacyMode) {
      DeepLinkingService.destroy();
    }
    this.isInitialized = false;
  }

  // Private methods

  private async handleWithLegacyService(url: string): Promise<void> {
    // Parse the URL and convert to legacy format
    const urlObj = new URL(url);
    await DeepLinkingService.initialize();
    
    // The legacy service will handle the URL through its event system
    DeepLinkingService.emit('url', { url });
  }

  private async trackDeepLinkAttempt(url: string, context: Partial<DeepLinkContext>): Promise<void> {
    try {
      const user = await AuthService.getCurrentUser();
      
      await AnalyticsService.track('deep_link_attempt', {
        url,
        source: context.source || 'unknown',
        campaign: context.campaign,
        referrer: context.referrer,
        user_id: user?.id,
        timestamp: new Date().toISOString()
      });
    } catch (error) {
      console.error('Failed to track deep link attempt:', error);
    }
  }

  private async trackDeepLinkError(url: string, error: any): Promise<void> {
    try {
      const user = await AuthService.getCurrentUser();
      
      await AnalyticsService.track('deep_link_error', {
        url,
        error_message: error.message,
        error_code: error.code || 'UNKNOWN',
        user_id: user?.id,
        timestamp: new Date().toISOString()
      });
    } catch (trackingError) {
      console.error('Failed to track deep link error:', trackingError);
    }
  }
}

// Export singleton instance
export default DeepLinkManager.getInstance();