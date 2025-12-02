/**
 * Google Pay Integration Service
 * Provides deep integration with Google Pay for seamless Android payments
 */

import { Platform, NativeModules } from 'react-native';
import { ApiService } from '../ApiService';
import DeviceInfo from 'react-native-device-info';
import AppConfigService from '../AppConfigService';

// Google Pay availability and readiness types
export interface GooglePayCapabilities {
  isAvailable: boolean;
  canUseGooglePay: boolean;
  existingPaymentMethodRequired: boolean;
  supportedCardNetworks: GooglePayCardNetwork[];
  supportedAuthMethods: GooglePayAuthMethod[];
}

// Google Pay card networks
export enum GooglePayCardNetwork {
  AMEX = 'AMEX',
  DISCOVER = 'DISCOVER',
  INTERAC = 'INTERAC',
  JCB = 'JCB',
  MASTERCARD = 'MASTERCARD',
  VISA = 'VISA'
}

// Google Pay authentication methods
export enum GooglePayAuthMethod {
  PAN_ONLY = 'PAN_ONLY',
  CRYPTOGRAM_3DS = 'CRYPTOGRAM_3DS'
}

// Google Pay environment
export enum GooglePayEnvironment {
  TEST = 'TEST',
  PRODUCTION = 'PRODUCTION'
}

// Payment request structure
export interface GooglePayRequest {
  environment: GooglePayEnvironment;
  merchantInfo: {
    merchantId?: string;
    merchantName: string;
  };
  allowedPaymentMethods: GooglePayPaymentMethod[];
  transactionInfo: {
    totalPriceStatus: 'FINAL' | 'ESTIMATED';
    totalPrice: string;
    currencyCode: string;
    countryCode?: string;
    displayItems?: GooglePayDisplayItem[];
  };
  shippingAddressRequired?: boolean;
  shippingAddressParameters?: {
    allowedCountryCodes?: string[];
    phoneNumberRequired?: boolean;
  };
  emailRequired?: boolean;
  billingAddressRequired?: boolean;
  billingAddressParameters?: {
    format?: 'MIN' | 'FULL';
    phoneNumberRequired?: boolean;
  };
}

export interface GooglePayPaymentMethod {
  type: 'CARD' | 'PAYPAL';
  parameters: {
    allowedAuthMethods: GooglePayAuthMethod[];
    allowedCardNetworks: GooglePayCardNetwork[];
    allowPrepaidCards?: boolean;
    allowCreditCards?: boolean;
    assuranceDetailsRequired?: boolean;
    billingAddressRequired?: boolean;
    billingAddressParameters?: {
      format?: 'MIN' | 'FULL';
      phoneNumberRequired?: boolean;
    };
  };
  tokenizationSpecification: {
    type: 'PAYMENT_GATEWAY' | 'DIRECT';
    parameters: {
      gateway?: string;
      gatewayMerchantId?: string;
      publicKey?: string;
      protocolVersion?: string;
      signature?: string;
    };
  };
}

export interface GooglePayDisplayItem {
  label: string;
  type: 'LINE_ITEM' | 'SUBTOTAL' | 'TAX' | 'DISCOUNT';
  price: string;
  status?: 'FINAL' | 'PENDING';
}

// Payment response structure
export interface GooglePayResponse {
  apiVersion: number;
  apiVersionMinor: number;
  paymentMethodData: {
    type: string;
    description: string;
    info: {
      cardNetwork: string;
      cardDetails: string;
      assuranceDetails?: {
        cardHolderAuthenticated: boolean;
        accountVerified: boolean;
      };
      billingAddress?: GooglePayAddress;
    };
    tokenizationData: {
      type: string;
      token: string;
    };
  };
  shippingAddress?: GooglePayAddress;
  email?: string;
}

export interface GooglePayAddress {
  name?: string;
  address1?: string;
  address2?: string;
  address3?: string;
  locality?: string;
  administrativeArea?: string;
  countryCode?: string;
  postalCode?: string;
  phoneNumber?: string;
}

// Transaction record for Waqiti integration
export interface WaqitiGooglePayTransaction {
  id: string;
  googlePayTransactionId: string;
  amount: number;
  currency: string;
  merchantId: string;
  status: 'pending' | 'completed' | 'failed' | 'cancelled';
  paymentMethod: {
    network: string;
    cardDetails: string;
    description: string;
  };
  timestamp: string;
  billingAddress?: GooglePayAddress;
  shippingAddress?: GooglePayAddress;
  email?: string;
  metadata?: Record<string, any>;
}

/**
 * Google Pay Integration Service for Waqiti
 */
class GooglePayService {
  private static instance: GooglePayService;
  private isInitialized: boolean = false;
  private capabilities: GooglePayCapabilities | null = null;
  private merchantId: string = '';
  private environment: GooglePayEnvironment = GooglePayEnvironment.TEST;

  private constructor() {}

  public static getInstance(): GooglePayService {
    if (!GooglePayService.instance) {
      GooglePayService.instance = new GooglePayService();
    }
    return GooglePayService.instance;
  }

  /**
   * Initialize Google Pay service
   */
  async initialize(): Promise<void> {
    if (Platform.OS !== 'android' || this.isInitialized) {
      return;
    }

    try {
      console.log('Initializing Google Pay Service...');

      // Get configuration from app config
      const config = await AppConfigService.getConfig();
      this.merchantId = config.features.googlePayMerchantId || 'waqiti-payments';
      this.environment = config.environment === 'production' 
        ? GooglePayEnvironment.PRODUCTION 
        : GooglePayEnvironment.TEST;

      // Check Google Pay availability and capabilities
      await this.checkCapabilities();

      this.isInitialized = true;
      console.log('Google Pay Service initialized successfully');

      // Track initialization
      await this.trackEvent('google_pay_initialized', {
        isAvailable: this.capabilities?.isAvailable,
        environment: this.environment,
        supportedNetworks: this.capabilities?.supportedCardNetworks?.length || 0,
      });

    } catch (error) {
      console.error('Failed to initialize Google Pay Service:', error);
      throw error;
    }
  }

  /**
   * Check Google Pay capabilities on the device
   */
  async checkCapabilities(): Promise<GooglePayCapabilities> {
    if (Platform.OS !== 'android') {
      this.capabilities = {
        isAvailable: false,
        canUseGooglePay: false,
        existingPaymentMethodRequired: false,
        supportedCardNetworks: [],
        supportedAuthMethods: [],
      };
      return this.capabilities;
    }

    try {
      // Use native module to check Google Pay capabilities
      const { GooglePay } = NativeModules;
      
      if (!GooglePay) {
        throw new Error('Google Pay native module not available');
      }

      const readinessRequest = {
        apiVersion: 2,
        apiVersionMinor: 0,
        allowedPaymentMethods: [this.createBaseCardPaymentMethod()],
        existingPaymentMethodRequired: false,
      };

      const capabilities = await GooglePay.isReadyToPay(readinessRequest);
      
      this.capabilities = {
        isAvailable: capabilities.result || false,
        canUseGooglePay: capabilities.result || false,
        existingPaymentMethodRequired: capabilities.existingPaymentMethodRequired || false,
        supportedCardNetworks: Object.values(GooglePayCardNetwork),
        supportedAuthMethods: Object.values(GooglePayAuthMethod),
      };

      return this.capabilities;

    } catch (error) {
      console.error('Failed to check Google Pay capabilities:', error);
      
      // Fallback capabilities
      this.capabilities = {
        isAvailable: false,
        canUseGooglePay: false,
        existingPaymentMethodRequired: false,
        supportedCardNetworks: [],
        supportedAuthMethods: [],
      };
      
      return this.capabilities;
    }
  }

  /**
   * Check if Google Pay is available for use
   */
  isAvailable(): boolean {
    return Platform.OS === 'android' && this.capabilities?.isAvailable === true;
  }

  /**
   * Request payment via Google Pay
   */
  async requestPayment(request: GooglePayRequest): Promise<GooglePayResponse> {
    if (!this.isAvailable()) {
      throw new Error('Google Pay is not available on this device');
    }

    try {
      const { GooglePay } = NativeModules;
      
      if (!GooglePay) {
        throw new Error('Google Pay native module not available');
      }

      console.log('Requesting Google Pay payment...');

      // Validate request
      this.validatePaymentRequest(request);

      // Create full payment data request
      const paymentDataRequest = {
        apiVersion: 2,
        apiVersionMinor: 0,
        ...request,
      };

      // Request payment
      const response = await GooglePay.loadPaymentData(paymentDataRequest);

      // Track successful request
      await this.trackEvent('google_pay_payment_requested', {
        merchantId: request.merchantInfo.merchantId,
        amount: parseFloat(request.transactionInfo.totalPrice),
        currency: request.transactionInfo.currencyCode,
      });

      return response;

    } catch (error) {
      console.error('Failed to request Google Pay payment:', error);
      
      // Track error
      await this.trackEvent('google_pay_payment_error', {
        error: error.message,
        merchantId: request.merchantInfo.merchantId,
      });
      
      throw error;
    }
  }

  /**
   * Process Google Pay payment through Waqiti backend
   */
  async processPayment(
    googlePayResponse: GooglePayResponse,
    paymentDetails: {
      amount: number;
      currency: string;
      description?: string;
      recipientId?: string;
      metadata?: Record<string, any>;
    }
  ): Promise<WaqitiGooglePayTransaction> {
    try {
      console.log('Processing Google Pay payment through Waqiti...');

      // Prepare payment request for Waqiti backend
      const paymentRequest = {
        provider: 'google_pay',
        amount: paymentDetails.amount,
        currency: paymentDetails.currency,
        description: paymentDetails.description,
        recipientId: paymentDetails.recipientId,
        paymentData: {
          apiVersion: googlePayResponse.apiVersion,
          apiVersionMinor: googlePayResponse.apiVersionMinor,
          paymentMethodData: googlePayResponse.paymentMethodData,
          shippingAddress: googlePayResponse.shippingAddress,
          email: googlePayResponse.email,
        },
        deviceInfo: {
          deviceId: await DeviceInfo.getUniqueId(),
          platform: Platform.OS,
          appVersion: await DeviceInfo.getVersion(),
        },
        metadata: paymentDetails.metadata,
      };

      // Send to Waqiti backend for processing
      const response = await ApiService.post('/payments/google-pay/process', paymentRequest);

      if (!response.success) {
        throw new Error(response.message || 'Payment processing failed');
      }

      const transaction: WaqitiGooglePayTransaction = {
        id: response.data.transactionId,
        googlePayTransactionId: response.data.googlePayTransactionId || `gp_${Date.now()}`,
        amount: paymentDetails.amount,
        currency: paymentDetails.currency,
        merchantId: this.merchantId,
        status: response.data.status,
        paymentMethod: {
          network: googlePayResponse.paymentMethodData.info.cardNetwork,
          cardDetails: googlePayResponse.paymentMethodData.info.cardDetails,
          description: googlePayResponse.paymentMethodData.description,
        },
        timestamp: new Date().toISOString(),
        billingAddress: googlePayResponse.paymentMethodData.info.billingAddress,
        shippingAddress: googlePayResponse.shippingAddress,
        email: googlePayResponse.email,
        metadata: paymentDetails.metadata,
      };

      // Track successful payment
      await this.trackEvent('google_pay_payment_processed', {
        transactionId: transaction.id,
        amount: transaction.amount,
        currency: transaction.currency,
        network: transaction.paymentMethod.network,
        status: transaction.status,
      });

      console.log('Google Pay payment processed successfully:', transaction.id);
      return transaction;

    } catch (error) {
      console.error('Failed to process Google Pay payment:', error);
      
      // Track error
      await this.trackEvent('google_pay_payment_processing_error', {
        error: error.message,
        amount: paymentDetails.amount,
        currency: paymentDetails.currency,
      });
      
      throw error;
    }
  }

  /**
   * Create quick payment request for common Waqiti transactions
   */
  createWaqitiPaymentRequest(
    amount: number,
    currency: string = 'USD',
    description: string = 'Waqiti Payment'
  ): GooglePayRequest {
    return {
      environment: this.environment,
      merchantInfo: {
        merchantId: this.merchantId,
        merchantName: 'Waqiti',
      },
      allowedPaymentMethods: [this.createWaqitiCardPaymentMethod()],
      transactionInfo: {
        totalPriceStatus: 'FINAL',
        totalPrice: amount.toFixed(2),
        currencyCode: currency,
        countryCode: 'US',
        displayItems: [
          {
            label: description,
            type: 'LINE_ITEM',
            price: amount.toFixed(2),
            status: 'FINAL',
          },
        ],
      },
      emailRequired: false,
      shippingAddressRequired: false,
      billingAddressRequired: true,
      billingAddressParameters: {
        format: 'MIN',
        phoneNumberRequired: false,
      },
    };
  }

  /**
   * Create base card payment method for readiness check
   */
  private createBaseCardPaymentMethod(): GooglePayPaymentMethod {
    return {
      type: 'CARD',
      parameters: {
        allowedAuthMethods: [GooglePayAuthMethod.PAN_ONLY, GooglePayAuthMethod.CRYPTOGRAM_3DS],
        allowedCardNetworks: [
          GooglePayCardNetwork.AMEX,
          GooglePayCardNetwork.DISCOVER,
          GooglePayCardNetwork.MASTERCARD,
          GooglePayCardNetwork.VISA,
        ],
        allowPrepaidCards: true,
        allowCreditCards: true,
      },
      tokenizationSpecification: {
        type: 'PAYMENT_GATEWAY',
        parameters: {
          gateway: 'waqiti',
          gatewayMerchantId: this.merchantId,
        },
      },
    };
  }

  /**
   * Create Waqiti-specific card payment method
   */
  private createWaqitiCardPaymentMethod(): GooglePayPaymentMethod {
    return {
      type: 'CARD',
      parameters: {
        allowedAuthMethods: [GooglePayAuthMethod.PAN_ONLY, GooglePayAuthMethod.CRYPTOGRAM_3DS],
        allowedCardNetworks: [
          GooglePayCardNetwork.AMEX,
          GooglePayCardNetwork.DISCOVER,
          GooglePayCardNetwork.MASTERCARD,
          GooglePayCardNetwork.VISA,
        ],
        allowPrepaidCards: true,
        allowCreditCards: true,
        assuranceDetailsRequired: true,
        billingAddressRequired: true,
        billingAddressParameters: {
          format: 'MIN',
          phoneNumberRequired: false,
        },
      },
      tokenizationSpecification: {
        type: 'PAYMENT_GATEWAY',
        parameters: {
          gateway: 'waqiti',
          gatewayMerchantId: this.merchantId,
          protocolVersion: 'ECv2',
        },
      },
    };
  }

  /**
   * Get current Google Pay capabilities
   */
  getCapabilities(): GooglePayCapabilities | null {
    return this.capabilities;
  }

  /**
   * Validate payment request
   */
  private validatePaymentRequest(request: GooglePayRequest): void {
    if (!request.merchantInfo?.merchantName) {
      throw new Error('Merchant name is required');
    }

    if (!request.allowedPaymentMethods || request.allowedPaymentMethods.length === 0) {
      throw new Error('At least one payment method is required');
    }

    if (!request.transactionInfo) {
      throw new Error('Transaction info is required');
    }

    const amount = parseFloat(request.transactionInfo.totalPrice);
    if (isNaN(amount) || amount <= 0) {
      throw new Error('Valid total price is required');
    }

    if (!request.transactionInfo.currencyCode) {
      throw new Error('Currency code is required');
    }
  }

  /**
   * Track analytics events
   */
  private async trackEvent(event: string, properties?: Record<string, any>): Promise<void> {
    try {
      await ApiService.trackEvent(`google_pay_${event}`, {
        ...properties,
        platform: Platform.OS,
        environment: this.environment,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.warn('Failed to track Google Pay event:', error);
    }
  }
}

export default GooglePayService.getInstance();