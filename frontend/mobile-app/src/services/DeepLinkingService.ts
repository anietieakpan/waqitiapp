import { Linking, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import dynamicLinks from '@react-native-firebase/dynamic-links';
import branch from 'react-native-branch';
import { NavigationService } from './NavigationService';
import { store } from '../store';
import { ApiService } from './ApiService';
import { EventEmitter } from 'eventemitter3';

interface DeepLinkConfig {
  scheme: string;
  universalLinkDomain: string;
  androidPackageName: string;
  iosBundleId: string;
  fallbackUrl: string;
}

interface DeepLinkData {
  type: 'payment' | 'request' | 'user' | 'merchant' | 'promotion' | 'referral' | 'split';
  params: {
    id?: string;
    amount?: string;
    currency?: string;
    description?: string;
    userId?: string;
    merchantId?: string;
    promotionId?: string;
    referralCode?: string;
    splitId?: string;
    [key: string]: any;
  };
  metadata?: {
    source?: string;
    campaign?: string;
    medium?: string;
    timestamp?: string;
  };
}

interface DynamicLinkOptions {
  title: string;
  description?: string;
  imageUrl?: string;
  socialTitle?: string;
  socialDescription?: string;
  socialImageUrl?: string;
}

class DeepLinkingService extends EventEmitter {
  private static instance: DeepLinkingService;
  private isInitialized: boolean = false;
  private pendingDeepLink: string | null = null;
  private deepLinkHistory: Array<{ url: string; timestamp: number }> = [];
  private branchUnsubscribe: (() => void) | null = null;
  
  private config: DeepLinkConfig = {
    scheme: 'waqiti',
    universalLinkDomain: 'waqiti.app.link',
    androidPackageName: 'com.waqiti.app',
    iosBundleId: 'com.waqiti.app',
    fallbackUrl: 'https://waqiti.com/download',
  };

  private readonly DEEPLINK_HISTORY_KEY = '@deeplink_history';
  private readonly MAX_HISTORY_SIZE = 50;

  static getInstance(): DeepLinkingService {
    if (!DeepLinkingService.instance) {
      DeepLinkingService.instance = new DeepLinkingService();
    }
    return DeepLinkingService.instance;
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Set up URL scheme handler
      this.setupURLHandler();
      
      // Set up Firebase Dynamic Links
      this.setupDynamicLinks();
      
      // Set up Branch.io
      await this.setupBranch();
      
      // Handle initial URL
      await this.handleInitialURL();
      
      // Load history
      await this.loadHistory();
      
      this.isInitialized = true;
      this.emit('initialized');
    } catch (error) {
      console.error('Failed to initialize deep linking:', error);
      this.emit('error', error);
    }
  }

  private setupURLHandler(): void {
    // Handle deep links when app is already open
    Linking.addEventListener('url', this.handleDeepLink.bind(this));
  }

  private setupDynamicLinks(): void {
    // Handle dynamic links when app is in foreground
    dynamicLinks().onLink(this.handleDynamicLink.bind(this));
  }

  private async setupBranch(): Promise<void> {
    // Subscribe to Branch deep link events
    this.branchUnsubscribe = branch.subscribe(({ error, params, uri }) => {
      if (error) {
        console.error('Branch error:', error);
        return;
      }

      if (params && !params['+clicked_branch_link']) {
        // Not a Branch link
        return;
      }

      this.handleBranchLink(params);
    });
  }

  private async handleInitialURL(): Promise<void> {
    try {
      // Check for initial URL (app opened from link)
      const url = await Linking.getInitialURL();
      if (url) {
        this.pendingDeepLink = url;
        this.emit('pendingDeepLink', url);
      }

      // Check for Firebase Dynamic Link
      const dynamicLink = await dynamicLinks().getInitialLink();
      if (dynamicLink) {
        this.handleDynamicLink(dynamicLink);
      }
    } catch (error) {
      console.error('Failed to handle initial URL:', error);
    }
  }

  private handleDeepLink({ url }: { url: string }): void {
    console.log('Deep link received:', url);
    
    try {
      const data = this.parseDeepLink(url);
      if (data) {
        this.processDeepLink(data);
        this.addToHistory(url);
      }
    } catch (error) {
      console.error('Failed to handle deep link:', error);
      this.emit('error', error);
    }
  }

  private handleDynamicLink(link: any): void {
    console.log('Dynamic link received:', link.url);
    
    try {
      const data = this.parseDeepLink(link.url);
      if (data) {
        // Add Firebase Analytics data
        if (link.utmParameters) {
          data.metadata = {
            ...data.metadata,
            source: link.utmParameters.utm_source,
            campaign: link.utmParameters.utm_campaign,
            medium: link.utmParameters.utm_medium,
          };
        }
        
        this.processDeepLink(data);
        this.addToHistory(link.url);
      }
    } catch (error) {
      console.error('Failed to handle dynamic link:', error);
      this.emit('error', error);
    }
  }

  private handleBranchLink(params: any): void {
    console.log('Branch link received:', params);
    
    try {
      const data: DeepLinkData = {
        type: params.type || 'payment',
        params: {
          id: params.id,
          amount: params.amount,
          currency: params.currency,
          description: params.description,
          userId: params.user_id,
          merchantId: params.merchant_id,
          promotionId: params.promotion_id,
          referralCode: params.referral_code,
          splitId: params.split_id,
          ...params,
        },
        metadata: {
          source: params['~channel'],
          campaign: params['~campaign'],
          medium: params['~feature'],
          timestamp: new Date().toISOString(),
        },
      };
      
      this.processDeepLink(data);
    } catch (error) {
      console.error('Failed to handle Branch link:', error);
      this.emit('error', error);
    }
  }

  private parseDeepLink(url: string): DeepLinkData | null {
    try {
      const uri = new URL(url);
      
      // Check if it's our scheme
      if (!uri.protocol.startsWith(this.config.scheme) && 
          !uri.hostname.includes(this.config.universalLinkDomain)) {
        return null;
      }
      
      // Parse path and query params
      const pathParts = uri.pathname.split('/').filter(Boolean);
      const type = pathParts[0] as DeepLinkData['type'];
      const params: any = {};
      
      // Extract ID from path
      if (pathParts.length > 1) {
        params.id = pathParts[1];
      }
      
      // Extract query parameters
      uri.searchParams.forEach((value, key) => {
        params[key] = value;
      });
      
      return {
        type,
        params,
        metadata: {
          timestamp: new Date().toISOString(),
        },
      };
    } catch (error) {
      console.error('Failed to parse deep link:', error);
      return null;
    }
  }

  private async processDeepLink(data: DeepLinkData): Promise<void> {
    // Wait for navigation to be ready
    await NavigationService.isReadyPromise;
    
    // Check if user is authenticated
    const state = store.getState();
    const isAuthenticated = state.auth.isAuthenticated;
    
    if (!isAuthenticated) {
      // Store deep link for after authentication
      this.pendingDeepLink = JSON.stringify(data);
      NavigationService.navigate('Login', { returnTo: 'deeplink' });
      return;
    }
    
    // Process based on type
    switch (data.type) {
      case 'payment':
        if (data.params.merchantId) {
          NavigationService.navigate('Payment', {
            merchantId: data.params.merchantId,
            amount: data.params.amount ? parseFloat(data.params.amount) : undefined,
            currency: data.params.currency,
            description: data.params.description,
          });
        } else if (data.params.userId) {
          NavigationService.navigate('SendMoney', {
            recipientId: data.params.userId,
            amount: data.params.amount ? parseFloat(data.params.amount) : undefined,
            currency: data.params.currency,
            description: data.params.description,
          });
        }
        break;
        
      case 'request':
        if (data.params.id) {
          NavigationService.navigate('RequestDetails', {
            requestId: data.params.id,
          });
        } else {
          NavigationService.navigate('RequestMoney', {
            fromUserId: data.params.userId,
            amount: data.params.amount ? parseFloat(data.params.amount) : undefined,
            currency: data.params.currency,
            description: data.params.description,
          });
        }
        break;
        
      case 'user':
        NavigationService.navigate('UserProfile', {
          userId: data.params.id || data.params.userId,
        });
        break;
        
      case 'merchant':
        NavigationService.navigate('MerchantDetails', {
          merchantId: data.params.id || data.params.merchantId,
        });
        break;
        
      case 'promotion':
        NavigationService.navigate('PromotionDetails', {
          promotionId: data.params.id || data.params.promotionId,
        });
        break;
        
      case 'referral':
        NavigationService.navigate('Referral', {
          referralCode: data.params.referralCode,
        });
        break;
        
      case 'split':
        NavigationService.navigate('BillSplit', {
          splitId: data.params.id || data.params.splitId,
        });
        break;
        
      default:
        console.warn('Unknown deep link type:', data.type);
        NavigationService.navigate('Home');
    }
    
    // Track deep link usage
    await this.trackDeepLink(data);
    
    // Emit event
    this.emit('deepLinkHandled', data);
  }

  async createPaymentLink(params: {
    amount: number;
    currency: string;
    description?: string;
    recipientId?: string;
    merchantId?: string;
  }): Promise<string> {
    try {
      const linkData = {
        type: 'payment',
        ...params,
      };
      
      // Create dynamic link
      const link = await dynamicLinks().buildShortLink({
        link: `${this.config.scheme}://payment?${this.buildQueryString(params)}`,
        domainUriPrefix: `https://${this.config.universalLinkDomain}`,
        android: {
          packageName: this.config.androidPackageName,
          fallbackUrl: this.config.fallbackUrl,
        },
        ios: {
          bundleId: this.config.iosBundleId,
          fallbackUrl: this.config.fallbackUrl,
        },
        analytics: {
          source: 'app',
          medium: 'payment_link',
          campaign: 'user_generated',
        },
      });
      
      // Save link metadata
      await ApiService.savePaymentLink({
        shortLink: link,
        data: linkData,
      });
      
      return link;
    } catch (error) {
      console.error('Failed to create payment link:', error);
      throw error;
    }
  }

  async createReferralLink(userId: string): Promise<string> {
    try {
      // Create Branch link for referrals
      const branchUniversalObject = await branch.createBranchUniversalObject(
        `referral/${userId}`,
        {
          title: 'Join Waqiti',
          contentDescription: 'Join me on Waqiti and get $10 bonus!',
          contentImageUrl: 'https://waqiti.com/images/referral.png',
          contentMetadata: {
            customMetadata: {
              type: 'referral',
              referrer_id: userId,
            },
          },
        }
      );
      
      const linkProperties = {
        feature: 'referral',
        channel: 'app',
        campaign: 'user_referral',
        stage: 'new_user',
        tags: ['referral', userId],
      };
      
      const controlParams = {
        $desktop_url: `${this.config.fallbackUrl}?referral=${userId}`,
        $ios_url: `${this.config.scheme}://referral?code=${userId}`,
        $android_url: `${this.config.scheme}://referral?code=${userId}`,
      };
      
      const { url } = await branchUniversalObject.generateShortUrl(
        linkProperties,
        controlParams
      );
      
      return url;
    } catch (error) {
      console.error('Failed to create referral link:', error);
      throw error;
    }
  }

  async createSplitBillLink(splitData: {
    splitId: string;
    totalAmount: number;
    currency: string;
    participants: number;
  }): Promise<string> {
    try {
      const link = await dynamicLinks().buildShortLink({
        link: `${this.config.scheme}://split/${splitData.splitId}`,
        domainUriPrefix: `https://${this.config.universalLinkDomain}`,
        android: {
          packageName: this.config.androidPackageName,
        },
        ios: {
          bundleId: this.config.iosBundleId,
        },
        social: {
          title: 'Split Bill',
          descriptionText: `Split ${splitData.currency} ${splitData.totalAmount} between ${splitData.participants} people`,
          imageUrl: 'https://waqiti.com/images/split-bill.png',
        },
      });
      
      return link;
    } catch (error) {
      console.error('Failed to create split bill link:', error);
      throw error;
    }
  }

  async handlePendingDeepLink(): Promise<void> {
    if (this.pendingDeepLink) {
      try {
        // Check if it's already parsed data
        if (this.pendingDeepLink.startsWith('{')) {
          const data = JSON.parse(this.pendingDeepLink);
          await this.processDeepLink(data);
        } else {
          this.handleDeepLink({ url: this.pendingDeepLink });
        }
      } catch (error) {
        console.error('Failed to handle pending deep link:', error);
      } finally {
        this.pendingDeepLink = null;
      }
    }
  }

  private buildQueryString(params: any): string {
    return Object.keys(params)
      .filter(key => params[key] !== undefined)
      .map(key => `${key}=${encodeURIComponent(params[key])}`)
      .join('&');
  }

  private async trackDeepLink(data: DeepLinkData): Promise<void> {
    try {
      await ApiService.trackDeepLink({
        type: data.type,
        params: data.params,
        metadata: data.metadata,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error('Failed to track deep link:', error);
    }
  }

  private async addToHistory(url: string): Promise<void> {
    this.deepLinkHistory.push({
      url,
      timestamp: Date.now(),
    });
    
    // Keep only recent history
    if (this.deepLinkHistory.length > this.MAX_HISTORY_SIZE) {
      this.deepLinkHistory = this.deepLinkHistory.slice(-this.MAX_HISTORY_SIZE);
    }
    
    // Save to storage
    try {
      await AsyncStorage.setItem(
        this.DEEPLINK_HISTORY_KEY,
        JSON.stringify(this.deepLinkHistory)
      );
    } catch (error) {
      console.error('Failed to save deep link history:', error);
    }
  }

  private async loadHistory(): Promise<void> {
    try {
      const history = await AsyncStorage.getItem(this.DEEPLINK_HISTORY_KEY);
      if (history) {
        this.deepLinkHistory = JSON.parse(history);
      }
    } catch (error) {
      console.error('Failed to load deep link history:', error);
    }
  }

  getHistory(): Array<{ url: string; timestamp: number }> {
    return [...this.deepLinkHistory];
  }

  destroy(): void {
    Linking.removeEventListener('url', this.handleDeepLink);
    
    if (this.branchUnsubscribe) {
      this.branchUnsubscribe();
    }
    
    this.removeAllListeners();
  }
}

export default DeepLinkingService.getInstance();