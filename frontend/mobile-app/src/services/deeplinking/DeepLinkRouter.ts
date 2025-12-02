import { NavigationContainerRef } from '@react-navigation/native';
import { Alert, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../ApiService';
import { AuthService } from '../auth/AuthService';
import { UserPreferencesService } from '../UserPreferencesService';
import { AnalyticsService } from '../AnalyticsService';

export interface DeepLinkRoute {
  pattern: string;
  handler: DeepLinkHandler;
  requiresAuth: boolean;
  permission?: string;
  fallback?: string;
  metadata?: {
    category: string;
    description: string;
    public: boolean;
  };
}

export interface DeepLinkHandler {
  (params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult>;
}

export interface DeepLinkContext {
  navigation: NavigationContainerRef<any>;
  user?: any;
  isAuthenticated: boolean;
  deviceInfo: any;
  source: 'app' | 'web' | 'sms' | 'email' | 'qr' | 'nfc' | 'social';
  campaign?: string;
  referrer?: string;
}

export interface DeepLinkResult {
  success: boolean;
  route?: string;
  params?: any;
  errorCode?: string;
  errorMessage?: string;
  requiresUserAction?: boolean;
  actionType?: 'confirm' | 'authenticate' | 'permissions' | 'update';
  actionData?: any;
}

/**
 * Advanced Deep Link Router with pattern matching and context-aware routing
 */
export class DeepLinkRouter {
  private routes: Map<string, DeepLinkRoute> = new Map();
  private defaultFallback: string = 'Home';
  private navigationRef: NavigationContainerRef<any> | null = null;
  private pendingRoutes: Array<{ url: string; context: Partial<DeepLinkContext> }> = [];

  constructor() {
    this.registerDefaultRoutes();
  }

  setNavigationRef(ref: NavigationContainerRef<any>): void {
    this.navigationRef = ref;
    
    // Process any pending routes
    if (this.pendingRoutes.length > 0) {
      this.pendingRoutes.forEach(({ url, context }) => {
        this.route(url, context);
      });
      this.pendingRoutes = [];
    }
  }

  /**
   * Register a new deep link route
   */
  registerRoute(route: DeepLinkRoute): void {
    this.routes.set(route.pattern, route);
  }

  /**
   * Route a deep link URL to the appropriate screen
   */
  async route(url: string, context: Partial<DeepLinkContext> = {}): Promise<DeepLinkResult> {
    if (!this.navigationRef) {
      // Store for later processing
      this.pendingRoutes.push({ url, context });
      return {
        success: false,
        errorCode: 'NAVIGATION_NOT_READY',
        errorMessage: 'Navigation not ready, route queued for processing'
      };
    }

    try {
      // Parse URL and extract components
      const urlInfo = this.parseURL(url);
      if (!urlInfo) {
        return this.handleError('INVALID_URL', 'Invalid deep link URL format');
      }

      // Find matching route
      const matchedRoute = this.findMatchingRoute(urlInfo.path);
      if (!matchedRoute) {
        return this.handleFallback('ROUTE_NOT_FOUND', urlInfo);
      }

      // Build full context
      const fullContext: DeepLinkContext = {
        navigation: this.navigationRef,
        isAuthenticated: await AuthService.isAuthenticated(),
        user: await AuthService.getCurrentUser(),
        deviceInfo: await this.getDeviceInfo(),
        source: context.source || 'app',
        campaign: context.campaign || urlInfo.queryParams.utm_campaign,
        referrer: context.referrer || urlInfo.queryParams.utm_source,
        ...context
      };

      // Check authentication requirements
      if (matchedRoute.route.requiresAuth && !fullContext.isAuthenticated) {
        return this.handleAuthRequired(url, fullContext);
      }

      // Check permissions
      if (matchedRoute.route.permission) {
        const hasPermission = await this.checkPermission(matchedRoute.route.permission, fullContext);
        if (!hasPermission) {
          return this.handlePermissionDenied(matchedRoute.route.permission, fullContext);
        }
      }

      // Execute route handler
      const result = await matchedRoute.route.handler(matchedRoute.params, fullContext);
      
      // Track successful routing
      await this.trackRouting(url, matchedRoute.route, result, fullContext);
      
      return result;

    } catch (error) {
      console.error('Deep link routing error:', error);
      return this.handleError('ROUTING_ERROR', error.message);
    }
  }

  /**
   * Generate a deep link URL for a specific route
   */
  generateURL(pattern: string, params: Record<string, any> = {}, options: {
    source?: string;
    campaign?: string;
    utmParams?: Record<string, string>;
  } = {}): string {
    let url = pattern;

    // Replace path parameters
    Object.keys(params).forEach(key => {
      url = url.replace(`:${key}`, encodeURIComponent(params[key]));
    });

    // Add query parameters
    const queryParams = new URLSearchParams();
    
    if (options.source) queryParams.set('utm_source', options.source);
    if (options.campaign) queryParams.set('utm_campaign', options.campaign);
    
    if (options.utmParams) {
      Object.entries(options.utmParams).forEach(([key, value]) => {
        queryParams.set(key, value);
      });
    }

    const queryString = queryParams.toString();
    if (queryString) {
      url += (url.includes('?') ? '&' : '?') + queryString;
    }

    return url;
  }

  /**
   * Get all registered routes (for debugging/documentation)
   */
  getRoutes(): Array<{ pattern: string; route: DeepLinkRoute }> {
    return Array.from(this.routes.entries()).map(([pattern, route]) => ({
      pattern,
      route
    }));
  }

  /**
   * Test if a URL would match any route
   */
  testURL(url: string): { matches: boolean; route?: DeepLinkRoute; params?: Record<string, any> } {
    const urlInfo = this.parseURL(url);
    if (!urlInfo) {
      return { matches: false };
    }

    const matchedRoute = this.findMatchingRoute(urlInfo.path);
    if (!matchedRoute) {
      return { matches: false };
    }

    return {
      matches: true,
      route: matchedRoute.route,
      params: matchedRoute.params
    };
  }

  private registerDefaultRoutes(): void {
    // Payment routes
    this.registerRoute({
      pattern: '/pay/:merchantId',
      requiresAuth: true,
      handler: this.handleMerchantPayment.bind(this),
      metadata: {
        category: 'payment',
        description: 'Pay a specific merchant',
        public: false
      }
    });

    this.registerRoute({
      pattern: '/send/:userId',
      requiresAuth: true,
      handler: this.handleSendMoney.bind(this),
      metadata: {
        category: 'payment',
        description: 'Send money to a user',
        public: false
      }
    });

    this.registerRoute({
      pattern: '/request/:requestId',
      requiresAuth: true,
      handler: this.handlePaymentRequest.bind(this),
      metadata: {
        category: 'payment',
        description: 'Handle payment request',
        public: false
      }
    });

    // Split payment routes
    this.registerRoute({
      pattern: '/split/:splitId',
      requiresAuth: true,
      handler: this.handleSplitBill.bind(this),
      metadata: {
        category: 'social',
        description: 'Join split bill',
        public: false
      }
    });

    this.registerRoute({
      pattern: '/split/create',
      requiresAuth: true,
      handler: this.handleCreateSplit.bind(this),
      metadata: {
        category: 'social',
        description: 'Create new split bill',
        public: false
      }
    });

    // User and social routes
    this.registerRoute({
      pattern: '/user/:userId',
      requiresAuth: false,
      handler: this.handleUserProfile.bind(this),
      metadata: {
        category: 'social',
        description: 'View user profile',
        public: true
      }
    });

    this.registerRoute({
      pattern: '/referral/:code',
      requiresAuth: false,
      handler: this.handleReferral.bind(this),
      metadata: {
        category: 'growth',
        description: 'Handle referral invite',
        public: true
      }
    });

    // Merchant and business routes
    this.registerRoute({
      pattern: '/merchant/:merchantId',
      requiresAuth: false,
      handler: this.handleMerchantDetails.bind(this),
      metadata: {
        category: 'business',
        description: 'View merchant details',
        public: true
      }
    });

    // Promotion and rewards routes
    this.registerRoute({
      pattern: '/promo/:promoId',
      requiresAuth: false,
      handler: this.handlePromotion.bind(this),
      metadata: {
        category: 'marketing',
        description: 'Handle promotion',
        public: true
      }
    });

    this.registerRoute({
      pattern: '/rewards/:category?',
      requiresAuth: true,
      handler: this.handleRewards.bind(this),
      metadata: {
        category: 'engagement',
        description: 'View rewards',
        public: false
      }
    });

    // Transaction and history routes
    this.registerRoute({
      pattern: '/transaction/:transactionId',
      requiresAuth: true,
      handler: this.handleTransactionDetails.bind(this),
      metadata: {
        category: 'account',
        description: 'View transaction details',
        public: false
      }
    });

    // Settings and account routes
    this.registerRoute({
      pattern: '/settings/:section?',
      requiresAuth: true,
      handler: this.handleSettings.bind(this),
      metadata: {
        category: 'account',
        description: 'App settings',
        public: false
      }
    });

    // Support and help routes
    this.registerRoute({
      pattern: '/help/:topic?',
      requiresAuth: false,
      handler: this.handleHelp.bind(this),
      metadata: {
        category: 'support',
        description: 'Help and support',
        public: true
      }
    });

    // QR code routes
    this.registerRoute({
      pattern: '/qr/:action/:data',
      requiresAuth: false,
      handler: this.handleQRCode.bind(this),
      metadata: {
        category: 'utility',
        description: 'QR code actions',
        public: true
      }
    });
  }

  // Route handlers
  private async handleMerchantPayment(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { merchantId } = params;
    
    try {
      // Validate merchant exists
      const merchant = await ApiService.getMerchant(merchantId);
      if (!merchant) {
        return {
          success: false,
          errorCode: 'MERCHANT_NOT_FOUND',
          errorMessage: 'Merchant not found'
        };
      }

      // Check if user has sufficient permissions/verification
      if (merchant.requiresVerification && !context.user?.isVerified) {
        return {
          success: false,
          requiresUserAction: true,
          actionType: 'authenticate',
          actionData: { reason: 'merchant_verification_required' }
        };
      }

      context.navigation.navigate('Payment', {
        merchantId,
        amount: params.amount ? parseFloat(params.amount) : undefined,
        currency: params.currency || 'USD',
        description: params.description,
        orderId: params.orderId,
        returnUrl: params.returnUrl
      });

      return { success: true, route: 'Payment', params };
    } catch (error) {
      return {
        success: false,
        errorCode: 'MERCHANT_PAYMENT_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handleSendMoney(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { userId } = params;

    try {
      // Validate recipient
      const recipient = await ApiService.getUser(userId);
      if (!recipient) {
        return {
          success: false,
          errorCode: 'USER_NOT_FOUND',
          errorMessage: 'Recipient not found'
        };
      }

      // Check daily limits
      const limits = await ApiService.getUserLimits(context.user.id);
      const amount = params.amount ? parseFloat(params.amount) : 0;
      
      if (amount > 0 && amount > limits.dailyRemaining) {
        return {
          success: false,
          requiresUserAction: true,
          actionType: 'confirm',
          actionData: { 
            reason: 'daily_limit_exceeded',
            limit: limits.dailyLimit,
            remaining: limits.dailyRemaining
          }
        };
      }

      context.navigation.navigate('SendMoney', {
        recipientId: userId,
        recipientName: recipient.displayName,
        amount: amount || undefined,
        currency: params.currency || 'USD',
        description: params.description,
        memo: params.memo
      });

      return { success: true, route: 'SendMoney', params };
    } catch (error) {
      return {
        success: false,
        errorCode: 'SEND_MONEY_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handlePaymentRequest(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { requestId } = params;

    try {
      // Fetch payment request details
      const paymentRequest = await ApiService.getPaymentRequest(requestId);
      if (!paymentRequest) {
        return {
          success: false,
          errorCode: 'REQUEST_NOT_FOUND',
          errorMessage: 'Payment request not found'
        };
      }

      // Check if request is still valid
      if (paymentRequest.status !== 'pending' || paymentRequest.expiresAt < new Date()) {
        return {
          success: false,
          errorCode: 'REQUEST_EXPIRED',
          errorMessage: 'Payment request has expired'
        };
      }

      // Check if user is the intended recipient
      if (paymentRequest.recipientId !== context.user.id) {
        return {
          success: false,
          errorCode: 'UNAUTHORIZED',
          errorMessage: 'You are not authorized to view this request'
        };
      }

      context.navigation.navigate('RequestDetails', {
        requestId,
        paymentRequest
      });

      return { success: true, route: 'RequestDetails', params: { requestId } };
    } catch (error) {
      return {
        success: false,
        errorCode: 'PAYMENT_REQUEST_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handleSplitBill(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { splitId } = params;

    try {
      const splitBill = await ApiService.getSplitBill(splitId);
      if (!splitBill) {
        return {
          success: false,
          errorCode: 'SPLIT_NOT_FOUND',
          errorMessage: 'Split bill not found'
        };
      }

      // Check if split is still active
      if (splitBill.status === 'completed' || splitBill.status === 'cancelled') {
        context.navigation.navigate('SplitBillHistory', { splitId });
        return { success: true, route: 'SplitBillHistory', params: { splitId } };
      }

      context.navigation.navigate('BillSplit', {
        splitId,
        splitBill
      });

      return { success: true, route: 'BillSplit', params: { splitId } };
    } catch (error) {
      return {
        success: false,
        errorCode: 'SPLIT_BILL_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handleCreateSplit(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    context.navigation.navigate('CreateSplit', {
      totalAmount: params.amount ? parseFloat(params.amount) : undefined,
      currency: params.currency || 'USD',
      description: params.description,
      participants: params.participants ? JSON.parse(params.participants) : undefined
    });

    return { success: true, route: 'CreateSplit', params };
  }

  private async handleUserProfile(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { userId } = params;

    try {
      const user = await ApiService.getUser(userId);
      if (!user) {
        return {
          success: false,
          errorCode: 'USER_NOT_FOUND',
          errorMessage: 'User not found'
        };
      }

      // Check privacy settings
      if (user.privacy?.profileVisibility === 'private' && 
          context.user?.id !== userId && 
          !user.friends?.includes(context.user?.id)) {
        return {
          success: false,
          errorCode: 'PROFILE_PRIVATE',
          errorMessage: 'This profile is private'
        };
      }

      context.navigation.navigate('UserProfile', { userId, user });
      return { success: true, route: 'UserProfile', params: { userId } };
    } catch (error) {
      return {
        success: false,
        errorCode: 'USER_PROFILE_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handleReferral(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { code } = params;

    try {
      // Validate referral code
      const referral = await ApiService.validateReferralCode(code);
      if (!referral.valid) {
        return {
          success: false,
          errorCode: 'INVALID_REFERRAL',
          errorMessage: 'Invalid or expired referral code'
        };
      }

      if (context.isAuthenticated) {
        // User is already signed up, just apply referral bonus
        await ApiService.applyReferralBonus(code, context.user.id);
        Alert.alert('Referral Applied', 'Referral bonus has been added to your account!');
        context.navigation.navigate('Home');
      } else {
        // New user, navigate to sign up with referral
        context.navigation.navigate('SignUp', { referralCode: code });
      }

      return { success: true, route: context.isAuthenticated ? 'Home' : 'SignUp', params: { referralCode: code } };
    } catch (error) {
      return {
        success: false,
        errorCode: 'REFERRAL_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handleMerchantDetails(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { merchantId } = params;

    context.navigation.navigate('MerchantDetails', { merchantId });
    return { success: true, route: 'MerchantDetails', params: { merchantId } };
  }

  private async handlePromotion(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { promoId } = params;

    try {
      const promotion = await ApiService.getPromotion(promoId);
      if (!promotion || !promotion.active) {
        return {
          success: false,
          errorCode: 'PROMOTION_INVALID',
          errorMessage: 'Promotion is no longer active'
        };
      }

      context.navigation.navigate('PromotionDetails', { promoId, promotion });
      return { success: true, route: 'PromotionDetails', params: { promoId } };
    } catch (error) {
      return {
        success: false,
        errorCode: 'PROMOTION_ERROR',
        errorMessage: error.message
      };
    }
  }

  private async handleRewards(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    context.navigation.navigate('Rewards', { 
      category: params.category,
      filter: params.filter 
    });
    return { success: true, route: 'Rewards', params };
  }

  private async handleTransactionDetails(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { transactionId } = params;

    context.navigation.navigate('TransactionDetails', { transactionId });
    return { success: true, route: 'TransactionDetails', params: { transactionId } };
  }

  private async handleSettings(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { section } = params;

    if (section) {
      context.navigation.navigate('Settings', { 
        screen: section,
        params: params 
      });
    } else {
      context.navigation.navigate('Settings');
    }

    return { success: true, route: 'Settings', params };
  }

  private async handleHelp(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    context.navigation.navigate('Help', { 
      topic: params.topic,
      search: params.search 
    });
    return { success: true, route: 'Help', params };
  }

  private async handleQRCode(params: Record<string, any>, context: DeepLinkContext): Promise<DeepLinkResult> {
    const { action, data } = params;

    try {
      const qrData = JSON.parse(decodeURIComponent(data));
      
      switch (action) {
        case 'pay':
          return this.handleMerchantPayment(qrData, context);
        case 'add-contact':
          context.navigation.navigate('AddContact', { contactData: qrData });
          break;
        case 'join-group':
          context.navigation.navigate('JoinGroup', { groupData: qrData });
          break;
        default:
          throw new Error('Unknown QR action: ' + action);
      }

      return { success: true, route: 'QRCode', params: { action, data: qrData } };
    } catch (error) {
      return {
        success: false,
        errorCode: 'QR_CODE_ERROR',
        errorMessage: error.message
      };
    }
  }

  // Helper methods
  private parseURL(url: string): { path: string; queryParams: Record<string, string> } | null {
    try {
      const urlObj = new URL(url);
      const queryParams: Record<string, string> = {};
      
      urlObj.searchParams.forEach((value, key) => {
        queryParams[key] = value;
      });

      return {
        path: urlObj.pathname,
        queryParams
      };
    } catch {
      // Try as a simple path
      const [path, queryString] = url.split('?');
      const queryParams: Record<string, string> = {};
      
      if (queryString) {
        queryString.split('&').forEach(param => {
          const [key, value] = param.split('=');
          if (key && value) {
            queryParams[decodeURIComponent(key)] = decodeURIComponent(value);
          }
        });
      }

      return { path, queryParams };
    }
  }

  private findMatchingRoute(path: string): { route: DeepLinkRoute; params: Record<string, any> } | null {
    for (const [pattern, route] of this.routes.entries()) {
      const params = this.matchPattern(pattern, path);
      if (params !== null) {
        return { route, params };
      }
    }
    return null;
  }

  private matchPattern(pattern: string, path: string): Record<string, any> | null {
    const patternParts = pattern.split('/').filter(Boolean);
    const pathParts = path.split('/').filter(Boolean);

    if (patternParts.length !== pathParts.length) {
      // Check for optional parameters
      const hasOptional = patternParts.some(part => part.endsWith('?'));
      if (!hasOptional || patternParts.length < pathParts.length) {
        return null;
      }
    }

    const params: Record<string, any> = {};

    for (let i = 0; i < patternParts.length; i++) {
      const patternPart = patternParts[i];
      const pathPart = pathParts[i];

      if (patternPart.startsWith(':')) {
        // Parameter
        const paramName = patternPart.slice(1).replace('?', '');
        if (pathPart !== undefined) {
          params[paramName] = decodeURIComponent(pathPart);
        } else if (!patternPart.endsWith('?')) {
          // Required parameter missing
          return null;
        }
      } else if (patternPart !== pathPart) {
        // Literal mismatch
        return null;
      }
    }

    return params;
  }

  private async handleAuthRequired(url: string, context: DeepLinkContext): Promise<DeepLinkResult> {
    // Store URL for after authentication
    await AsyncStorage.setItem('pending_deep_link', url);
    
    context.navigation.navigate('Login', { 
      returnTo: 'deeplink',
      message: 'Please log in to continue'
    });

    return {
      success: false,
      requiresUserAction: true,
      actionType: 'authenticate',
      route: 'Login'
    };
  }

  private async handlePermissionDenied(permission: string, context: DeepLinkContext): Promise<DeepLinkResult> {
    return {
      success: false,
      errorCode: 'PERMISSION_DENIED',
      errorMessage: `Permission required: ${permission}`,
      requiresUserAction: true,
      actionType: 'permissions',
      actionData: { permission }
    };
  }

  private async handleFallback(reason: string, urlInfo: any): Promise<DeepLinkResult> {
    if (this.navigationRef) {
      this.navigationRef.navigate(this.defaultFallback);
    }

    return {
      success: false,
      errorCode: reason,
      route: this.defaultFallback,
      errorMessage: `Unhandled deep link: ${urlInfo.path}`
    };
  }

  private handleError(code: string, message: string): DeepLinkResult {
    return {
      success: false,
      errorCode: code,
      errorMessage: message
    };
  }

  private async checkPermission(permission: string, context: DeepLinkContext): Promise<boolean> {
    // Implement permission checking logic
    const userPermissions = context.user?.permissions || [];
    return userPermissions.includes(permission);
  }

  private async getDeviceInfo(): Promise<any> {
    // Return device information
    return {
      platform: Platform.OS,
      version: Platform.Version,
      model: 'Unknown', // Could be enhanced with react-native-device-info
      appVersion: '1.0.0' // Could be from package.json or config
    };
  }

  private async trackRouting(url: string, route: DeepLinkRoute, result: DeepLinkResult, context: DeepLinkContext): Promise<void> {
    try {
      await AnalyticsService.track('deep_link_routed', {
        url,
        pattern: route.pattern,
        success: result.success,
        route: result.route,
        errorCode: result.errorCode,
        source: context.source,
        campaign: context.campaign,
        user_id: context.user?.id
      });
    } catch (error) {
      console.error('Failed to track deep link routing:', error);
    }
  }
}