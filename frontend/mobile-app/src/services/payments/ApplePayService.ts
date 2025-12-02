/**
 * Apple Pay Integration Service
 * Provides deep integration with Apple Pay for seamless iOS payments
 */

import { Platform, NativeModules } from 'react-native';
import { ApiService } from '../ApiService';
import { useBiometric } from '../../hooks/useBiometric';
import DeviceInfo from 'react-native-device-info';
import AppConfigService from '../AppConfigService';

// Apple Pay availability and capability types
export interface ApplePayCapabilities {
  isAvailable: boolean;
  supportedNetworks: ApplePayNetwork[];
  supportedCapabilities: ApplePayCapability[];
  canMakePayments: boolean;
  canMakePaymentsUsingNetworks: boolean;
  canSetupCards: boolean;
}

// Apple Pay network types
export enum ApplePayNetwork {
  VISA = 'visa',
  MASTERCARD = 'masterCard',
  AMEX = 'amex',
  DISCOVER = 'discover',
  MAESTRO = 'maestro',
  JCB = 'jcb',
  UNION_PAY = 'chinaUnionPay'
}

// Apple Pay capability types
export enum ApplePayCapability {
  SUPPORTS_3DS = 'supports3DS',
  SUPPORTS_EMV = 'supportsEMV',
  SUPPORTS_CREDIT = 'supportsCredit',
  SUPPORTS_DEBIT = 'supportsDebit'
}

// Payment request structure
export interface ApplePayRequest {
  merchantIdentifier: string;
  supportedNetworks: ApplePayNetwork[];
  merchantCapabilities: ApplePayCapability[];
  countryCode: string;
  currencyCode: string;
  paymentItems: ApplePayItem[];
  shippingItems?: ApplePayShippingItem[];
  requiredBillingContactFields?: ApplePayContactField[];
  requiredShippingContactFields?: ApplePayContactField[];
  shippingType?: ApplePayShippingType;
  billingContact?: ApplePayContact;
  shippingContact?: ApplePayContact;
}

export interface ApplePayItem {
  label: string;
  amount: string;
  type?: 'final' | 'pending';
}

export interface ApplePayShippingItem extends ApplePayItem {
  identifier: string;
  detail?: string;
}

export enum ApplePayContactField {
  POSTAL_ADDRESS = 'postalAddress',
  PHONE = 'phoneNumber',
  EMAIL = 'emailAddress',
  NAME = 'name'
}

export enum ApplePayShippingType {
  SHIPPING = 'shipping',
  DELIVERY = 'delivery',
  STORE_PICKUP = 'storePickup',
  SERVICE_PICKUP = 'servicePickup'
}

export interface ApplePayContact {
  name?: {
    givenName?: string;
    familyName?: string;
    nickname?: string;
    namePrefix?: string;
    nameSuffix?: string;
  };
  postalAddress?: {
    street?: string;
    city?: string;
    state?: string;
    postalCode?: string;
    country?: string;
    countryCode?: string;
  };
  phoneNumber?: string;
  emailAddress?: string;
}

// Payment response structure
export interface ApplePayResponse {
  paymentData: {
    version: string;
    data: string;
    signature: string;
    header: {
      ephemeralPublicKey: string;
      publicKeyHash: string;
      transactionId: string;
    };
  };
  paymentMethod: {
    displayName: string;
    network: string;
    type: 'debit' | 'credit' | 'prepaid' | 'store';
    paymentPass?: {
      primaryAccountIdentifier: string;
      primaryAccountNumberSuffix: string;
      deviceAccountIdentifier: string;
      deviceAccountNumberSuffix: string;
    };
  };
  transactionIdentifier: string;
  billingContact?: ApplePayContact;
  shippingContact?: ApplePayContact;
}

// Transaction record for Waqiti integration
export interface WaqitiApplePayTransaction {
  id: string;
  applePayTransactionId: string;
  amount: number;
  currency: string;
  merchantIdentifier: string;
  status: 'pending' | 'completed' | 'failed' | 'cancelled';
  paymentMethod: {
    network: string;
    type: string;
    displayName: string;
    last4: string;
  };
  timestamp: string;
  billingContact?: ApplePayContact;
  shippingContact?: ApplePayContact;
  metadata?: Record<string, any>;
}

/**
 * Apple Pay Integration Service for Waqiti
 */
class ApplePayService {
  private static instance: ApplePayService;
  private isInitialized: boolean = false;
  private capabilities: ApplePayCapabilities | null = null;
  private merchantId: string = '';

  private constructor() {}

  public static getInstance(): ApplePayService {
    if (!ApplePayService.instance) {
      ApplePayService.instance = new ApplePayService();
    }
    return ApplePayService.instance;
  }

  /**
   * Initialize Apple Pay service
   */
  async initialize(): Promise<void> {
    if (Platform.OS !== 'ios' || this.isInitialized) {
      return;
    }

    try {
      console.log('Initializing Apple Pay Service...');

      // Get merchant ID from app configuration
      const config = await AppConfigService.getConfig();
      this.merchantId = config.features.applePayMerchantId || 'merchant.com.waqiti.mobile';

      // Check Apple Pay availability and capabilities
      await this.checkCapabilities();

      this.isInitialized = true;
      console.log('Apple Pay Service initialized successfully');

      // Track initialization
      await this.trackEvent('apple_pay_initialized', {
        isAvailable: this.capabilities?.isAvailable,
        supportedNetworks: this.capabilities?.supportedNetworks?.length || 0,
      });

    } catch (error) {
      console.error('Failed to initialize Apple Pay Service:', error);
      throw error;
    }
  }

  /**
   * Check Apple Pay capabilities on the device
   */
  async checkCapabilities(): Promise<ApplePayCapabilities> {
    if (Platform.OS !== 'ios') {
      this.capabilities = {
        isAvailable: false,
        supportedNetworks: [],
        supportedCapabilities: [],
        canMakePayments: false,
        canMakePaymentsUsingNetworks: false,
        canSetupCards: false,
      };
      return this.capabilities;
    }

    try {
      // Use native module to check Apple Pay capabilities
      const { ApplePay } = NativeModules;
      
      if (!ApplePay) {
        throw new Error('Apple Pay native module not available');
      }

      const capabilities = await ApplePay.checkCapabilities();
      
      this.capabilities = {
        isAvailable: capabilities.isAvailable || false,
        supportedNetworks: capabilities.supportedNetworks || Object.values(ApplePayNetwork),
        supportedCapabilities: capabilities.supportedCapabilities || Object.values(ApplePayCapability),
        canMakePayments: capabilities.canMakePayments || false,
        canMakePaymentsUsingNetworks: capabilities.canMakePaymentsUsingNetworks || false,
        canSetupCards: capabilities.canSetupCards || false,
      };

      return this.capabilities;

    } catch (error) {
      console.error('Failed to check Apple Pay capabilities:', error);
      
      // Fallback capabilities
      this.capabilities = {
        isAvailable: false,
        supportedNetworks: [],
        supportedCapabilities: [],
        canMakePayments: false,
        canMakePaymentsUsingNetworks: false,
        canSetupCards: false,
      };
      
      return this.capabilities;
    }
  }

  /**
   * Check if Apple Pay is available for use
   */
  isAvailable(): boolean {
    return Platform.OS === 'ios' && this.capabilities?.isAvailable === true;
  }

  /**
   * Present Apple Pay payment sheet
   */
  async presentPaymentSheet(request: ApplePayRequest): Promise<ApplePayResponse> {
    if (!this.isAvailable()) {
      throw new Error('Apple Pay is not available on this device');
    }

    try {
      const { ApplePay } = NativeModules;
      
      if (!ApplePay) {
        throw new Error('Apple Pay native module not available');
      }

      console.log('Presenting Apple Pay payment sheet...');

      // Validate request
      this.validatePaymentRequest(request);

      // Present the payment sheet
      const response = await ApplePay.presentPaymentSheet(request);

      // Track successful presentation
      await this.trackEvent('apple_pay_sheet_presented', {
        merchantId: request.merchantIdentifier,
        amount: request.paymentItems.reduce((sum, item) => sum + parseFloat(item.amount), 0),
        currency: request.currencyCode,
      });

      return response;

    } catch (error) {
      console.error('Failed to present Apple Pay payment sheet:', error);
      
      // Track error
      await this.trackEvent('apple_pay_sheet_error', {
        error: error.message,
        merchantId: request.merchantIdentifier,
      });
      
      throw error;
    }
  }

  /**
   * Process Apple Pay payment through Waqiti backend
   */
  async processPayment(
    applePayResponse: ApplePayResponse,
    paymentDetails: {
      amount: number;
      currency: string;
      description?: string;
      recipientId?: string;
      metadata?: Record<string, any>;
    }
  ): Promise<WaqitiApplePayTransaction> {
    try {
      console.log('Processing Apple Pay payment through Waqiti...');

      // Prepare payment request for Waqiti backend
      const paymentRequest = {
        provider: 'apple_pay',
        amount: paymentDetails.amount,
        currency: paymentDetails.currency,
        description: paymentDetails.description,
        recipientId: paymentDetails.recipientId,
        paymentData: {
          paymentToken: applePayResponse.paymentData,
          paymentMethod: applePayResponse.paymentMethod,
          transactionIdentifier: applePayResponse.transactionIdentifier,
          billingContact: applePayResponse.billingContact,
          shippingContact: applePayResponse.shippingContact,
        },
        deviceInfo: {
          deviceId: await DeviceInfo.getUniqueId(),
          platform: Platform.OS,
          appVersion: await DeviceInfo.getVersion(),
        },
        metadata: paymentDetails.metadata,
      };

      // Send to Waqiti backend for processing
      const response = await ApiService.post('/payments/apple-pay/process', paymentRequest);

      if (!response.success) {
        throw new Error(response.message || 'Payment processing failed');
      }

      const transaction: WaqitiApplePayTransaction = {
        id: response.data.transactionId,
        applePayTransactionId: applePayResponse.transactionIdentifier,
        amount: paymentDetails.amount,
        currency: paymentDetails.currency,
        merchantIdentifier: this.merchantId,
        status: response.data.status,
        paymentMethod: {
          network: applePayResponse.paymentMethod.network,
          type: applePayResponse.paymentMethod.type,
          displayName: applePayResponse.paymentMethod.displayName,
          last4: applePayResponse.paymentMethod.paymentPass?.primaryAccountNumberSuffix || '****',
        },
        timestamp: new Date().toISOString(),
        billingContact: applePayResponse.billingContact,
        shippingContact: applePayResponse.shippingContact,
        metadata: paymentDetails.metadata,
      };

      // Track successful payment
      await this.trackEvent('apple_pay_payment_processed', {
        transactionId: transaction.id,
        amount: transaction.amount,
        currency: transaction.currency,
        network: transaction.paymentMethod.network,
        status: transaction.status,
      });

      console.log('Apple Pay payment processed successfully:', transaction.id);
      return transaction;

    } catch (error) {
      console.error('Failed to process Apple Pay payment:', error);
      
      // Track error
      await this.trackEvent('apple_pay_payment_error', {
        error: error.message,
        amount: paymentDetails.amount,
        currency: paymentDetails.currency,
      });
      
      throw error;
    }
  }

  /**
   * Add cards to Apple Wallet for Waqiti payments
   */
  async addCardToWallet(cardDetails: {
    cardholderName: string;
    primaryAccountSuffix: string;
    localizedDescription: string;
    paymentNetwork: ApplePayNetwork;
  }): Promise<boolean> {
    if (!this.isAvailable() || !this.capabilities?.canSetupCards) {
      throw new Error('Card provisioning is not available');
    }

    try {
      const { ApplePay } = NativeModules;
      
      const result = await ApplePay.addCardToWallet({
        cardholderName: cardDetails.cardholderName,
        primaryAccountSuffix: cardDetails.primaryAccountSuffix,
        localizedDescription: cardDetails.localizedDescription,
        paymentNetwork: cardDetails.paymentNetwork,
        merchantIdentifier: this.merchantID,
      });

      await this.trackEvent('apple_pay_card_added', {
        network: cardDetails.paymentNetwork,
        success: result.success,
      });

      return result.success;

    } catch (error) {
      console.error('Failed to add card to Apple Wallet:', error);
      await this.trackEvent('apple_pay_card_add_error', {
        error: error.message,
        network: cardDetails.paymentNetwork,
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
  ): ApplePayRequest {
    return {
      merchantIdentifier: this.merchantId,
      supportedNetworks: [
        ApplePayNetwork.VISA,
        ApplePayNetwork.MASTERCARD,
        ApplePayNetwork.AMEX,
        ApplePayNetwork.DISCOVER,
      ],
      merchantCapabilities: [
        ApplePayCapability.SUPPORTS_3DS,
        ApplePayCapability.SUPPORTS_EMV,
        ApplePayCapability.SUPPORTS_CREDIT,
        ApplePayCapability.SUPPORTS_DEBIT,
      ],
      countryCode: 'US',
      currencyCode: currency,
      paymentItems: [
        {
          label: description,
          amount: amount.toFixed(2),
          type: 'final',
        },
        {
          label: 'Waqiti',
          amount: amount.toFixed(2),
          type: 'final',
        },
      ],
    };
  }

  /**
   * Get current Apple Pay capabilities
   */
  getCapabilities(): ApplePayCapabilities | null {
    return this.capabilities;
  }

  /**
   * Validate payment request
   */
  private validatePaymentRequest(request: ApplePayRequest): void {
    if (!request.merchantIdentifier) {
      throw new Error('Merchant identifier is required');
    }

    if (!request.supportedNetworks || request.supportedNetworks.length === 0) {
      throw new Error('At least one supported network is required');
    }

    if (!request.paymentItems || request.paymentItems.length === 0) {
      throw new Error('At least one payment item is required');
    }

    // Validate total amount
    const totalAmount = request.paymentItems.reduce((sum, item) => {
      const amount = parseFloat(item.amount);
      if (isNaN(amount) || amount < 0) {
        throw new Error(`Invalid payment item amount: ${item.amount}`);
      }
      return sum + amount;
    }, 0);

    if (totalAmount <= 0) {
      throw new Error('Total payment amount must be greater than zero');
    }
  }

  /**
   * Track analytics events
   */
  private async trackEvent(event: string, properties?: Record<string, any>): Promise<void> {
    try {
      await ApiService.trackEvent(`apple_pay_${event}`, {
        ...properties,
        platform: Platform.OS,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.warn('Failed to track Apple Pay event:', error);
    }
  }
}

export default ApplePayService.getInstance();