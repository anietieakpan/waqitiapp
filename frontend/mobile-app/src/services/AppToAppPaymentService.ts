import { NativeModules, NativeEventEmitter, Platform, Linking } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { store } from '../store';
import { ApiService } from './ApiService';
import { BiometricService } from './BiometricService';
import { EventEmitter } from 'eventemitter3';
import uuid from 'react-native-uuid';

interface PaymentApp {
  id: string;
  name: string;
  scheme: string;
  packageName?: string; // Android
  bundleId?: string; // iOS
  icon?: string;
  supportsReceive: boolean;
  supportsSend: boolean;
  installed?: boolean;
}

interface AppPaymentRequest {
  id: string;
  amount: number;
  currency: string;
  recipientId?: string;
  recipientPhone?: string;
  recipientEmail?: string;
  description?: string;
  returnUrl: string;
  callbackUrl?: string;
  metadata?: Record<string, any>;
}

interface AppPaymentResponse {
  requestId: string;
  status: 'success' | 'failed' | 'cancelled';
  transactionId?: string;
  error?: {
    code: string;
    message: string;
  };
  timestamp: string;
}

interface UniversalLinkPayment {
  appId: string;
  action: 'pay' | 'request';
  amount: string;
  currency: string;
  recipient?: string;
  description?: string;
  returnApp?: string;
}

// Native module for app detection and communication
const { AppToAppPaymentModule } = NativeModules;
const appToAppEventEmitter = new NativeEventEmitter(AppToAppPaymentModule);

class AppToAppPaymentService extends EventEmitter {
  private static instance: AppToAppPaymentService;
  private supportedApps: Map<string, PaymentApp> = new Map();
  private pendingRequests: Map<string, AppPaymentRequest> = new Map();
  private installedApps: Set<string> = new Set();
  private isInitialized: boolean = false;
  
  private readonly SUPPORTED_APPS: PaymentApp[] = [
    {
      id: 'venmo',
      name: 'Venmo',
      scheme: 'venmo://',
      packageName: 'com.venmo',
      bundleId: 'net.venmo',
      supportsReceive: true,
      supportsSend: true,
    },
    {
      id: 'cashapp',
      name: 'Cash App',
      scheme: 'cashapp://',
      packageName: 'com.squareup.cash',
      bundleId: 'com.squareup.cash',
      supportsReceive: true,
      supportsSend: true,
    },
    {
      id: 'paypal',
      name: 'PayPal',
      scheme: 'paypal://',
      packageName: 'com.paypal.android.p2pmobile',
      bundleId: 'com.paypal.mobile',
      supportsReceive: true,
      supportsSend: true,
    },
    {
      id: 'zelle',
      name: 'Zelle',
      scheme: 'zelle://',
      packageName: 'com.zellepay.zelle',
      bundleId: 'com.zellepay.zelle',
      supportsReceive: true,
      supportsSend: false,
    },
    {
      id: 'googlepay',
      name: 'Google Pay',
      scheme: 'gpay://',
      packageName: 'com.google.android.apps.nbu.paisa.user',
      bundleId: 'com.google.paisa',
      supportsReceive: true,
      supportsSend: true,
    },
    {
      id: 'applepay',
      name: 'Apple Pay',
      scheme: 'applepay://',
      bundleId: 'com.apple.PassbookUIService',
      supportsReceive: false,
      supportsSend: true,
    },
  ];

  private readonly REQUEST_TIMEOUT = 300000; // 5 minutes
  private readonly PENDING_REQUESTS_KEY = '@app_payment_requests';

  static getInstance(): AppToAppPaymentService {
    if (!AppToAppPaymentService.instance) {
      AppToAppPaymentService.instance = new AppToAppPaymentService();
    }
    return AppToAppPaymentService.instance;
  }

  constructor() {
    super();
    this.setupSupportedApps();
  }

  private setupSupportedApps(): void {
    this.SUPPORTED_APPS.forEach(app => {
      this.supportedApps.set(app.id, app);
    });
  }

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      // Detect installed payment apps
      await this.detectInstalledApps();
      
      // Load pending requests
      await this.loadPendingRequests();
      
      // Set up native event listeners
      this.setupNativeListeners();
      
      // Set up URL handler for returns
      this.setupReturnHandler();
      
      // Clean up expired requests
      await this.cleanupExpiredRequests();
      
      this.isInitialized = true;
      this.emit('initialized');
    } catch (error) {
      console.error('Failed to initialize app-to-app payments:', error);
      this.emit('error', error);
    }
  }

  private async detectInstalledApps(): Promise<void> {
    const detectedApps: string[] = [];
    
    for (const app of this.supportedApps.values()) {
      const isInstalled = await this.isAppInstalled(app);
      if (isInstalled) {
        app.installed = true;
        this.installedApps.add(app.id);
        detectedApps.push(app.id);
      }
    }
    
    this.emit('appsDetected', detectedApps);
  }

  private async isAppInstalled(app: PaymentApp): Promise<boolean> {
    try {
      if (Platform.OS === 'ios') {
        // Check URL scheme
        return await Linking.canOpenURL(app.scheme);
      } else {
        // Use native module to check package
        return await AppToAppPaymentModule.isAppInstalled(app.packageName);
      }
    } catch (error) {
      console.error(`Failed to check if ${app.name} is installed:`, error);
      return false;
    }
  }

  private setupNativeListeners(): void {
    // Listen for payment responses from other apps
    appToAppEventEmitter.addListener(
      'onPaymentResponse',
      this.handlePaymentResponse.bind(this)
    );
    
    // Listen for app installation changes
    appToAppEventEmitter.addListener(
      'onAppInstallationChanged',
      this.handleAppInstallationChange.bind(this)
    );
  }

  private setupReturnHandler(): void {
    Linking.addEventListener('url', this.handleReturnUrl.bind(this));
  }

  private async handleReturnUrl({ url }: { url: string }): Promise<void> {
    try {
      // Parse return URL
      const uri = new URL(url);
      const requestId = uri.searchParams.get('request_id');
      const status = uri.searchParams.get('status') as AppPaymentResponse['status'];
      const transactionId = uri.searchParams.get('transaction_id');
      const errorCode = uri.searchParams.get('error_code');
      const errorMessage = uri.searchParams.get('error_message');
      
      if (requestId && status) {
        const response: AppPaymentResponse = {
          requestId,
          status,
          transactionId,
          error: errorCode ? {
            code: errorCode,
            message: errorMessage || 'Unknown error',
          } : undefined,
          timestamp: new Date().toISOString(),
        };
        
        await this.processPaymentResponse(response);
      }
    } catch (error) {
      console.error('Failed to handle return URL:', error);
    }
  }

  async sendPayment(params: {
    appId: string;
    amount: number;
    currency: string;
    recipientId?: string;
    recipientPhone?: string;
    recipientEmail?: string;
    description?: string;
    metadata?: Record<string, any>;
  }): Promise<AppPaymentResponse> {
    const app = this.supportedApps.get(params.appId);
    if (!app || !app.installed || !app.supportsSend) {
      throw new Error(`App ${params.appId} is not available for sending payments`);
    }
    
    // Create payment request
    const request: AppPaymentRequest = {
      id: uuid.v4() as string,
      amount: params.amount,
      currency: params.currency,
      recipientId: params.recipientId,
      recipientPhone: params.recipientPhone,
      recipientEmail: params.recipientEmail,
      description: params.description,
      returnUrl: `waqiti://payment-return`,
      metadata: {
        ...params.metadata,
        appId: params.appId,
        timestamp: new Date().toISOString(),
      },
    };
    
    // Store pending request
    this.pendingRequests.set(request.id, request);
    await this.savePendingRequests();
    
    // Require biometric authentication
    const authResult = await BiometricService.authenticate({
      reason: `Confirm ${params.currency} ${params.amount} payment via ${app.name}`,
    });
    
    if (!authResult.success) {
      throw new Error('Authentication required');
    }
    
    // Launch payment app
    const launched = await this.launchPaymentApp(app, request);
    if (!launched) {
      throw new Error(`Failed to launch ${app.name}`);
    }
    
    // Wait for response (with timeout)
    return await this.waitForPaymentResponse(request.id);
  }

  async requestPayment(params: {
    appId: string;
    amount: number;
    currency: string;
    payerId?: string;
    payerPhone?: string;
    payerEmail?: string;
    description?: string;
    metadata?: Record<string, any>;
  }): Promise<AppPaymentResponse> {
    const app = this.supportedApps.get(params.appId);
    if (!app || !app.installed || !app.supportsReceive) {
      throw new Error(`App ${params.appId} is not available for payment requests`);
    }
    
    // Create payment request
    const request: AppPaymentRequest = {
      id: uuid.v4() as string,
      amount: params.amount,
      currency: params.currency,
      recipientId: params.payerId,
      recipientPhone: params.payerPhone,
      recipientEmail: params.payerEmail,
      description: params.description,
      returnUrl: `waqiti://request-return`,
      metadata: {
        ...params.metadata,
        appId: params.appId,
        type: 'request',
        timestamp: new Date().toISOString(),
      },
    };
    
    // Store pending request
    this.pendingRequests.set(request.id, request);
    await this.savePendingRequests();
    
    // Launch payment app for request
    const launched = await this.launchPaymentRequest(app, request);
    if (!launched) {
      throw new Error(`Failed to launch ${app.name}`);
    }
    
    // Return immediately for requests (async processing)
    return {
      requestId: request.id,
      status: 'success',
      timestamp: new Date().toISOString(),
    };
  }

  private async launchPaymentApp(
    app: PaymentApp,
    request: AppPaymentRequest
  ): Promise<boolean> {
    try {
      let url: string;
      
      switch (app.id) {
        case 'venmo':
          url = this.buildVenmoUrl(request);
          break;
        case 'cashapp':
          url = this.buildCashAppUrl(request);
          break;
        case 'paypal':
          url = this.buildPayPalUrl(request);
          break;
        case 'zelle':
          url = this.buildZelleUrl(request);
          break;
        case 'googlepay':
          url = await this.buildGooglePayUrl(request);
          break;
        case 'applepay':
          url = await this.buildApplePayUrl(request);
          break;
        default:
          throw new Error(`Unsupported app: ${app.id}`);
      }
      
      return await Linking.openURL(url);
    } catch (error) {
      console.error(`Failed to launch ${app.name}:`, error);
      return false;
    }
  }

  private async launchPaymentRequest(
    app: PaymentApp,
    request: AppPaymentRequest
  ): Promise<boolean> {
    // Similar to launchPaymentApp but for requests
    return this.launchPaymentApp(app, request);
  }

  private buildVenmoUrl(request: AppPaymentRequest): string {
    const params = new URLSearchParams({
      txn: 'pay',
      amount: request.amount.toString(),
      note: request.description || '',
      app_id: 'waqiti',
      callback_url: request.returnUrl,
    });
    
    if (request.recipientPhone) {
      params.append('recipients', request.recipientPhone);
    }
    
    return `venmo://paycharge?${params.toString()}`;
  }

  private buildCashAppUrl(request: AppPaymentRequest): string {
    const params = new URLSearchParams({
      amount: request.amount.toString(),
      currency: request.currency,
      note: request.description || '',
      return_url: request.returnUrl,
    });
    
    let recipientId = '';
    if (request.recipientPhone) {
      recipientId = request.recipientPhone;
    } else if (request.recipientId) {
      recipientId = `$${request.recipientId}`;
    }
    
    return `cashapp://cash.app/${recipientId}?${params.toString()}`;
  }

  private buildPayPalUrl(request: AppPaymentRequest): string {
    const params = new URLSearchParams({
      amount: request.amount.toString(),
      currency_code: request.currency,
      memo: request.description || '',
      return_url: request.returnUrl,
      request_id: request.id,
    });
    
    return `paypal://paypal.com/send?${params.toString()}`;
  }

  private buildZelleUrl(request: AppPaymentRequest): string {
    // Zelle uses email or phone
    const recipient = request.recipientEmail || request.recipientPhone || '';
    return `zelle://send?recipient=${recipient}&amount=${request.amount}`;
  }

  private async buildGooglePayUrl(request: AppPaymentRequest): Promise<string> {
    // Google Pay requires a more complex integration
    if (Platform.OS === 'android') {
      const paymentData = await AppToAppPaymentModule.createGooglePayRequest({
        amount: request.amount,
        currency: request.currency,
        merchantName: 'Waqiti',
        requestId: request.id,
      });
      
      return `gpay://pay?data=${encodeURIComponent(JSON.stringify(paymentData))}`;
    }
    
    throw new Error('Google Pay is not supported on this platform');
  }

  private async buildApplePayUrl(request: AppPaymentRequest): Promise<string> {
    // Apple Pay requires PassKit integration
    if (Platform.OS === 'ios') {
      const paymentRequest = await AppToAppPaymentModule.createApplePayRequest({
        amount: request.amount,
        currency: request.currency,
        merchantId: 'merchant.com.waqiti',
        requestId: request.id,
      });
      
      return `applepay://pay?request=${encodeURIComponent(JSON.stringify(paymentRequest))}`;
    }
    
    throw new Error('Apple Pay is not supported on this platform');
  }

  private async waitForPaymentResponse(
    requestId: string,
    timeout: number = this.REQUEST_TIMEOUT
  ): Promise<AppPaymentResponse> {
    return new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        this.pendingRequests.delete(requestId);
        reject(new Error('Payment request timed out'));
      }, timeout);
      
      const checkResponse = (response: AppPaymentResponse) => {
        if (response.requestId === requestId) {
          clearTimeout(timeoutId);
          this.removeListener('paymentResponse', checkResponse);
          resolve(response);
        }
      };
      
      this.on('paymentResponse', checkResponse);
    });
  }

  private async handlePaymentResponse(data: any): Promise<void> {
    try {
      const response: AppPaymentResponse = {
        requestId: data.requestId,
        status: data.status,
        transactionId: data.transactionId,
        error: data.error,
        timestamp: new Date().toISOString(),
      };
      
      await this.processPaymentResponse(response);
    } catch (error) {
      console.error('Failed to handle payment response:', error);
    }
  }

  private async processPaymentResponse(response: AppPaymentResponse): Promise<void> {
    const request = this.pendingRequests.get(response.requestId);
    if (!request) {
      console.warn('Received response for unknown request:', response.requestId);
      return;
    }
    
    // Remove from pending
    this.pendingRequests.delete(response.requestId);
    await this.savePendingRequests();
    
    // Process based on status
    if (response.status === 'success' && response.transactionId) {
      // Verify with backend
      try {
        await ApiService.verifyExternalPayment({
          externalTransactionId: response.transactionId,
          amount: request.amount,
          currency: request.currency,
          provider: request.metadata?.appId,
          requestId: request.id,
        });
        
        // Create internal transaction record
        await ApiService.createPayment({
          amount: request.amount,
          currency: request.currency,
          recipientId: request.recipientId,
          recipientPhone: request.recipientPhone,
          recipientEmail: request.recipientEmail,
          description: request.description,
          externalPaymentId: response.transactionId,
          paymentMethod: 'external_app',
          metadata: {
            ...request.metadata,
            externalApp: request.metadata?.appId,
            externalTransactionId: response.transactionId,
          },
        });
      } catch (error) {
        console.error('Failed to verify external payment:', error);
        response.status = 'failed';
        response.error = {
          code: 'VERIFICATION_FAILED',
          message: 'Failed to verify payment with Waqiti',
        };
      }
    }
    
    // Emit response
    this.emit('paymentResponse', response);
  }

  private handleAppInstallationChange(data: {
    appId: string;
    installed: boolean;
  }): void {
    const app = this.supportedApps.get(data.appId);
    if (app) {
      app.installed = data.installed;
      
      if (data.installed) {
        this.installedApps.add(data.appId);
      } else {
        this.installedApps.delete(data.appId);
      }
      
      this.emit('appInstallationChanged', data);
    }
  }

  async getInstalledPaymentApps(): Promise<PaymentApp[]> {
    await this.detectInstalledApps();
    
    return Array.from(this.supportedApps.values())
      .filter(app => app.installed);
  }

  async getSupportedPaymentApps(): Promise<PaymentApp[]> {
    return Array.from(this.supportedApps.values());
  }

  async openAppStore(appId: string): Promise<void> {
    const app = this.supportedApps.get(appId);
    if (!app) {
      throw new Error(`Unknown app: ${appId}`);
    }
    
    let storeUrl: string;
    
    if (Platform.OS === 'ios' && app.bundleId) {
      // App Store URL
      storeUrl = `https://apps.apple.com/app/${app.bundleId}`;
    } else if (Platform.OS === 'android' && app.packageName) {
      // Play Store URL
      storeUrl = `market://details?id=${app.packageName}`;
    } else {
      throw new Error(`No store URL for ${app.name}`);
    }
    
    await Linking.openURL(storeUrl);
  }

  private async loadPendingRequests(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(this.PENDING_REQUESTS_KEY);
      if (stored) {
        const requests = JSON.parse(stored) as AppPaymentRequest[];
        requests.forEach(request => {
          this.pendingRequests.set(request.id, request);
        });
      }
    } catch (error) {
      console.error('Failed to load pending requests:', error);
    }
  }

  private async savePendingRequests(): Promise<void> {
    try {
      const requests = Array.from(this.pendingRequests.values());
      await AsyncStorage.setItem(
        this.PENDING_REQUESTS_KEY,
        JSON.stringify(requests)
      );
    } catch (error) {
      console.error('Failed to save pending requests:', error);
    }
  }

  private async cleanupExpiredRequests(): Promise<void> {
    const now = Date.now();
    const expired: string[] = [];
    
    for (const [id, request] of this.pendingRequests) {
      const createdAt = new Date(request.metadata?.timestamp || 0).getTime();
      if (now - createdAt > this.REQUEST_TIMEOUT) {
        expired.push(id);
      }
    }
    
    expired.forEach(id => this.pendingRequests.delete(id));
    
    if (expired.length > 0) {
      await this.savePendingRequests();
    }
  }

  destroy(): void {
    Linking.removeEventListener('url', this.handleReturnUrl);
    appToAppEventEmitter.removeAllListeners();
    this.removeAllListeners();
  }
}

export default AppToAppPaymentService.getInstance();