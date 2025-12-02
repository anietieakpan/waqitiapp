import { Platform, NativeModules, NativeEventEmitter } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../ApiService';
import { AuthService } from '../auth/AuthService';
import { AnalyticsService } from '../AnalyticsService';
import DeviceInfo from 'react-native-device-info';

export interface WidgetData {
  balance: string;
  recentTransaction?: {
    description: string;
    amount: string;
    type: 'sent' | 'received' | 'pending';
    timestamp: number;
  };
  quickActions: Array<{
    id: string;
    title: string;
    icon: string;
    deeplink: string;
  }>;
  lastUpdated: number;
}

export interface WidgetConfig {
  type: 'balance' | 'quick_actions' | 'recent_transactions' | 'crypto_prices';
  size: 'small' | 'medium' | 'large';
  updateInterval: number; // in minutes
  enabled: boolean;
  customization?: {
    showBalance: boolean;
    showRecentTransaction: boolean;
    quickActionIds: string[];
    theme: 'light' | 'dark' | 'auto';
  };
}

interface WidgetError {
  code: string;
  message: string;
  timestamp: number;
}

/**
 * Comprehensive Widget Service for iOS and Android Home Screen Widgets
 * Provides data updates, configuration management, and deep link handling
 */
class WidgetService {
  private static instance: WidgetService;
  private widgetNativeModule: any;
  private eventEmitter: NativeEventEmitter | null = null;
  private updateTimer: NodeJS.Timeout | null = null;
  private isInitialized: boolean = false;
  
  private readonly WIDGET_DATA_KEY = '@widget_data';
  private readonly WIDGET_CONFIG_KEY = '@widget_config';
  private readonly WIDGET_ERROR_KEY = '@widget_errors';
  private readonly DEFAULT_UPDATE_INTERVAL = 15; // minutes
  private readonly MAX_RETRIES = 3;

  static getInstance(): WidgetService {
    if (!WidgetService.instance) {
      WidgetService.instance = new WidgetService();
    }
    return WidgetService.instance;
  }

  private constructor() {
    this.initializeNativeModule();
  }

  private initializeNativeModule(): void {
    try {
      if (Platform.OS === 'ios') {
        this.widgetNativeModule = NativeModules.WaqitiWidgetModule;
      } else if (Platform.OS === 'android') {
        this.widgetNativeModule = NativeModules.WaqitiWidgetModule;
      }

      if (this.widgetNativeModule) {
        this.eventEmitter = new NativeEventEmitter(this.widgetNativeModule);
        this.setupEventListeners();
      }
    } catch (error) {
      console.error('Failed to initialize widget native module:', error);
    }
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      console.log('Initializing Widget Service...');
      
      // Load saved configuration
      await this.loadConfiguration();
      
      // Setup periodic updates
      await this.setupPeriodicUpdates();
      
      // Initial data fetch
      await this.updateAllWidgets();
      
      this.isInitialized = true;
      console.log('Widget Service initialized successfully');
      
      await this.trackEvent('widget_service_initialized');
    } catch (error) {
      console.error('Failed to initialize Widget Service:', error);
      await this.logError('INITIALIZATION_FAILED', error.message);
      throw error;
    }
  }

  /**
   * Update all widgets with fresh data
   */
  async updateAllWidgets(): Promise<void> {
    try {
      console.log('Updating all widgets...');
      
      const isAuthenticated = await AuthService.isAuthenticated();
      if (!isAuthenticated) {
        await this.updateWidgetsWithPlaceholder();
        return;
      }

      const widgetData = await this.fetchWidgetData();
      await this.saveWidgetData(widgetData);
      
      if (this.widgetNativeModule) {
        await this.widgetNativeModule.updateWidgets(widgetData);
      }
      
      await this.trackEvent('widgets_updated', {
        data_size: JSON.stringify(widgetData).length,
        timestamp: Date.now()
      });
      
    } catch (error) {
      console.error('Failed to update widgets:', error);
      await this.logError('UPDATE_FAILED', error.message);
      
      // Try to update with cached data
      await this.updateWidgetsWithCachedData();
    }
  }

  /**
   * Configure widget settings
   */
  async configureWidget(type: string, config: WidgetConfig): Promise<void> {
    try {
      const currentConfig = await this.getConfiguration();
      currentConfig[type] = config;
      
      await AsyncStorage.setItem(this.WIDGET_CONFIG_KEY, JSON.stringify(currentConfig));
      
      if (this.widgetNativeModule) {
        await this.widgetNativeModule.configureWidget(type, config);
      }
      
      // Update widgets with new configuration
      await this.updateAllWidgets();
      
      await this.trackEvent('widget_configured', {
        type,
        size: config.size,
        enabled: config.enabled
      });
      
    } catch (error) {
      console.error('Failed to configure widget:', error);
      await this.logError('CONFIGURATION_FAILED', error.message);
    }
  }

  /**
   * Handle widget tap/click events
   */
  async handleWidgetTap(widgetType: string, actionId?: string): Promise<void> {
    try {
      await this.trackEvent('widget_tapped', {
        widget_type: widgetType,
        action_id: actionId,
        timestamp: Date.now()
      });

      if (actionId) {
        // Handle specific action
        await this.executeQuickAction(actionId);
      } else {
        // Default widget tap - open main app
        await this.openMainApp();
      }
      
    } catch (error) {
      console.error('Failed to handle widget tap:', error);
      await this.logError('TAP_HANDLING_FAILED', error.message);
    }
  }

  /**
   * Get widget data for display
   */
  async getWidgetData(): Promise<WidgetData | null> {
    try {
      const savedData = await AsyncStorage.getItem(this.WIDGET_DATA_KEY);
      return savedData ? JSON.parse(savedData) : null;
    } catch (error) {
      console.error('Failed to get widget data:', error);
      return null;
    }
  }

  /**
   * Get widget configuration
   */
  async getConfiguration(): Promise<Record<string, WidgetConfig>> {
    try {
      const savedConfig = await AsyncStorage.getItem(this.WIDGET_CONFIG_KEY);
      return savedConfig ? JSON.parse(savedConfig) : this.getDefaultConfiguration();
    } catch (error) {
      console.error('Failed to get widget configuration:', error);
      return this.getDefaultConfiguration();
    }
  }

  /**
   * Enable/disable specific widget
   */
  async setWidgetEnabled(type: string, enabled: boolean): Promise<void> {
    try {
      const config = await this.getConfiguration();
      if (config[type]) {
        config[type].enabled = enabled;
        await AsyncStorage.setItem(this.WIDGET_CONFIG_KEY, JSON.stringify(config));
        
        if (this.widgetNativeModule) {
          await this.widgetNativeModule.setWidgetEnabled(type, enabled);
        }
        
        await this.trackEvent('widget_toggled', { type, enabled });
      }
    } catch (error) {
      console.error('Failed to set widget enabled state:', error);
      await this.logError('TOGGLE_FAILED', error.message);
    }
  }

  /**
   * Force refresh widget data
   */
  async refreshWidgets(): Promise<void> {
    await this.updateAllWidgets();
  }

  /**
   * Get widget errors for debugging
   */
  async getErrors(): Promise<WidgetError[]> {
    try {
      const savedErrors = await AsyncStorage.getItem(this.WIDGET_ERROR_KEY);
      return savedErrors ? JSON.parse(savedErrors) : [];
    } catch (error) {
      console.error('Failed to get widget errors:', error);
      return [];
    }
  }

  /**
   * Clear widget errors
   */
  async clearErrors(): Promise<void> {
    try {
      await AsyncStorage.removeItem(this.WIDGET_ERROR_KEY);
    } catch (error) {
      console.error('Failed to clear widget errors:', error);
    }
  }

  /**
   * Cleanup widget service
   */
  async cleanup(): Promise<void> {
    try {
      if (this.updateTimer) {
        clearInterval(this.updateTimer);
        this.updateTimer = null;
      }
      
      if (this.eventEmitter) {
        this.eventEmitter.removeAllListeners();
      }
      
      this.isInitialized = false;
      console.log('Widget Service cleaned up');
    } catch (error) {
      console.error('Failed to cleanup Widget Service:', error);
    }
  }

  // Private methods

  private setupEventListeners(): void {
    if (!this.eventEmitter) return;

    this.eventEmitter.addListener('WidgetTapped', (event) => {
      this.handleWidgetTap(event.widgetType, event.actionId);
    });

    this.eventEmitter.addListener('WidgetDataRequested', () => {
      this.updateAllWidgets();
    });

    this.eventEmitter.addListener('WidgetConfigurationChanged', (config) => {
      this.configureWidget(config.type, config.settings);
    });
  }

  private async setupPeriodicUpdates(): Promise<void> {
    if (this.updateTimer) {
      clearInterval(this.updateTimer);
    }

    const config = await this.getConfiguration();
    const updateInterval = config.balance?.updateInterval || this.DEFAULT_UPDATE_INTERVAL;
    
    this.updateTimer = setInterval(async () => {
      await this.updateAllWidgets();
    }, updateInterval * 60 * 1000); // Convert minutes to milliseconds
  }

  private async fetchWidgetData(): Promise<WidgetData> {
    const user = await AuthService.getCurrentUser();
    if (!user) {
      throw new Error('User not authenticated');
    }

    // Fetch data in parallel
    const [balanceData, transactionData, actionsData] = await Promise.all([
      this.fetchBalanceData(),
      this.fetchRecentTransaction(),
      this.fetchQuickActions()
    ]);

    return {
      balance: balanceData.balance,
      recentTransaction: transactionData,
      quickActions: actionsData,
      lastUpdated: Date.now()
    };
  }

  private async fetchBalanceData(): Promise<{ balance: string }> {
    try {
      const response = await ApiService.get('/api/wallet/balance');
      return {
        balance: this.formatCurrency(response.data.totalBalance, response.data.currency)
      };
    } catch (error) {
      console.error('Failed to fetch balance data:', error);
      return { balance: 'N/A' };
    }
  }

  private async fetchRecentTransaction(): Promise<WidgetData['recentTransaction'] | undefined> {
    try {
      const response = await ApiService.get('/api/transactions/recent?limit=1');
      const transaction = response.data.transactions?.[0];
      
      if (!transaction) return undefined;

      return {
        description: transaction.description || transaction.merchantName || 'Transaction',
        amount: this.formatCurrency(transaction.amount, transaction.currency),
        type: transaction.type,
        timestamp: new Date(transaction.createdAt).getTime()
      };
    } catch (error) {
      console.error('Failed to fetch recent transaction:', error);
      return undefined;
    }
  }

  private async fetchQuickActions(): Promise<WidgetData['quickActions']> {
    const config = await this.getConfiguration();
    const actionIds = config.balance?.customization?.quickActionIds || 
      ['send_money', 'request_money', 'scan_qr', 'recent_transactions'];

    const actions = actionIds.map(id => ({
      id,
      title: this.getActionTitle(id),
      icon: this.getActionIcon(id),
      deeplink: this.getActionDeeplink(id)
    }));

    return actions.slice(0, 4); // Limit to 4 actions
  }

  private async updateWidgetsWithPlaceholder(): Promise<void> {
    const placeholderData: WidgetData = {
      balance: 'Login Required',
      quickActions: [
        { id: 'login', title: 'Login', icon: 'login', deeplink: 'waqiti://login' }
      ],
      lastUpdated: Date.now()
    };

    await this.saveWidgetData(placeholderData);
    
    if (this.widgetNativeModule) {
      await this.widgetNativeModule.updateWidgets(placeholderData);
    }
  }

  private async updateWidgetsWithCachedData(): Promise<void> {
    try {
      const cachedData = await this.getWidgetData();
      if (cachedData && this.widgetNativeModule) {
        await this.widgetNativeModule.updateWidgets(cachedData);
      }
    } catch (error) {
      console.error('Failed to update widgets with cached data:', error);
    }
  }

  private async saveWidgetData(data: WidgetData): Promise<void> {
    try {
      await AsyncStorage.setItem(this.WIDGET_DATA_KEY, JSON.stringify(data));
    } catch (error) {
      console.error('Failed to save widget data:', error);
    }
  }

  private async loadConfiguration(): Promise<void> {
    try {
      const savedConfig = await AsyncStorage.getItem(this.WIDGET_CONFIG_KEY);
      if (!savedConfig) {
        // Initialize with default configuration
        const defaultConfig = this.getDefaultConfiguration();
        await AsyncStorage.setItem(this.WIDGET_CONFIG_KEY, JSON.stringify(defaultConfig));
      }
    } catch (error) {
      console.error('Failed to load widget configuration:', error);
    }
  }

  private getDefaultConfiguration(): Record<string, WidgetConfig> {
    return {
      balance: {
        type: 'balance',
        size: 'medium',
        updateInterval: 15,
        enabled: true,
        customization: {
          showBalance: true,
          showRecentTransaction: true,
          quickActionIds: ['send_money', 'request_money', 'scan_qr'],
          theme: 'auto'
        }
      },
      quick_actions: {
        type: 'quick_actions',
        size: 'small',
        updateInterval: 60,
        enabled: true,
        customization: {
          showBalance: false,
          showRecentTransaction: false,
          quickActionIds: ['send_money', 'request_money', 'scan_qr', 'pay_merchant'],
          theme: 'auto'
        }
      }
    };
  }

  private formatCurrency(amount: number, currency: string = 'USD'): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2
    }).format(amount);
  }

  private getActionTitle(id: string): string {
    const titles: Record<string, string> = {
      send_money: 'Send',
      request_money: 'Request',
      scan_qr: 'Scan QR',
      pay_merchant: 'Pay',
      recent_transactions: 'History',
      crypto_wallet: 'Crypto',
      split_bill: 'Split',
      top_up: 'Top Up'
    };
    return titles[id] || 'Action';
  }

  private getActionIcon(id: string): string {
    const icons: Record<string, string> = {
      send_money: 'arrow-up-circle',
      request_money: 'arrow-down-circle',
      scan_qr: 'qr-code-scanner',
      pay_merchant: 'credit-card',
      recent_transactions: 'history',
      crypto_wallet: 'bitcoin',
      split_bill: 'group',
      top_up: 'add-circle'
    };
    return icons[id] || 'help-circle';
  }

  private getActionDeeplink(id: string): string {
    const deeplinks: Record<string, string> = {
      send_money: 'waqiti://send',
      request_money: 'waqiti://request',
      scan_qr: 'waqiti://scan',
      pay_merchant: 'waqiti://pay',
      recent_transactions: 'waqiti://transactions',
      crypto_wallet: 'waqiti://crypto',
      split_bill: 'waqiti://split/create',
      top_up: 'waqiti://topup'
    };
    return deeplinks[id] || 'waqiti://home';
  }

  private async executeQuickAction(actionId: string): Promise<void> {
    const deeplink = this.getActionDeeplink(actionId);
    
    // Import DeepLinkManager dynamically to avoid circular dependencies
    const { default: DeepLinkManager } = await import('../deeplinking');
    await DeepLinkManager.handleDeepLink(deeplink, { source: 'widget' });
  }

  private async openMainApp(): Promise<void> {
    const { default: DeepLinkManager } = await import('../deeplinking');
    await DeepLinkManager.handleDeepLink('waqiti://home', { source: 'widget' });
  }

  private async trackEvent(eventName: string, properties?: Record<string, any>): Promise<void> {
    try {
      const deviceInfo = {
        platform: Platform.OS,
        version: Platform.Version,
        app_version: await DeviceInfo.getVersion(),
        build_number: await DeviceInfo.getBuildNumber()
      };

      await AnalyticsService.track(eventName, {
        ...properties,
        ...deviceInfo,
        timestamp: Date.now()
      });
    } catch (error) {
      console.error('Failed to track widget event:', error);
    }
  }

  private async logError(code: string, message: string): Promise<void> {
    try {
      const error: WidgetError = {
        code,
        message,
        timestamp: Date.now()
      };

      const errors = await this.getErrors();
      errors.push(error);
      
      // Keep only last 50 errors
      const recentErrors = errors.slice(-50);
      
      await AsyncStorage.setItem(this.WIDGET_ERROR_KEY, JSON.stringify(recentErrors));
      
      await this.trackEvent('widget_error', {
        error_code: code,
        error_message: message
      });
    } catch (err) {
      console.error('Failed to log widget error:', err);
    }
  }
}

export default WidgetService.getInstance();