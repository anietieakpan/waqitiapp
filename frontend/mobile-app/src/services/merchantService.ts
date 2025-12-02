import { apiClient } from './apiClient';

export interface MerchantProfile {
  id: string;
  userId: string;
  businessName: string;
  businessType: string;
  businessCategory: string;
  businessDescription: string;
  businessEmail: string;
  businessPhone: string;
  businessWebsite?: string;
  businessAddress: {
    street: string;
    city: string;
    state: string;
    zipCode: string;
    country: string;
  };
  taxId: string;
  status: 'pending_verification' | 'verified' | 'suspended' | 'active';
  verificationLevel: number;
  createdAt: string;
  updatedAt: string;
}

export interface MerchantRegistration {
  businessName: string;
  businessType: string;
  businessCategory: string;
  businessDescription: string;
  businessEmail: string;
  businessPhone: string;
  businessWebsite?: string;
  businessAddress: {
    street: string;
    city: string;
    state: string;
    zipCode: string;
    country: string;
  };
  taxId: string;
  ownerFirstName: string;
  ownerLastName: string;
  ownerEmail: string;
  ownerPhone: string;
  ownerDateOfBirth: string;
  ownerSSN: string;
}

export interface MerchantBalance {
  merchantId: string;
  totalBalance: number;
  availableBalance: number;
  pendingBalance: number;
  currency: string;
  lastUpdated: string;
}

export interface MerchantTransaction {
  id: string;
  merchantId: string;
  customerId: string;
  customerName: string;
  amount: number;
  currency: string;
  status: 'pending' | 'completed' | 'failed' | 'refunded';
  paymentMethod: string;
  description?: string;
  metadata?: any;
  createdAt: string;
  completedAt?: string;
  refundedAt?: string;
}

export interface MerchantQRCode {
  id: string;
  merchantId: string;
  name: string;
  description?: string;
  amount?: number;
  currency?: string;
  qrCodeData: string;
  qrCodeUrl: string;
  isActive: boolean;
  usageCount: number;
  createdAt: string;
}

export interface MerchantPayout {
  id: string;
  merchantId: string;
  amount: number;
  currency: string;
  paymentMethodId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  requestedAt: string;
  processedAt?: string;
  failureReason?: string;
}

export interface MerchantAnalytics {
  merchantId: string;
  dateRange: {
    startDate: string;
    endDate: string;
  };
  totalSales: number;
  totalTransactions: number;
  averageTransactionValue: number;
  topCustomers: Array<{
    customerId: string;
    customerName: string;
    totalSpent: number;
    transactionCount: number;
  }>;
  salesByDay: Array<{
    date: string;
    sales: number;
    transactions: number;
  }>;
  salesByCategory: Array<{
    category: string;
    sales: number;
    percentage: number;
  }>;
  paymentMethods: Array<{
    method: string;
    count: number;
    percentage: number;
  }>;
}

export interface PaymentMethod {
  id: string;
  merchantId: string;
  type: 'bank_account' | 'debit_card' | 'paypal';
  accountNumber: string;
  routingNumber?: string;
  bankName?: string;
  accountHolderName: string;
  isDefault: boolean;
  isActive: boolean;
  createdAt: string;
}

class MerchantService {
  // Registration and Profile Management
  async registerMerchant(registration: MerchantRegistration): Promise<{ merchant: MerchantProfile }> {
    const response = await apiClient.post('/merchants/register', registration);
    return response.data;
  }

  async getMerchantProfile(merchantId: string): Promise<{ merchant: MerchantProfile }> {
    const response = await apiClient.get(`/merchants/${merchantId}`);
    return response.data;
  }

  async updateMerchantProfile(
    merchantId: string,
    updates: Partial<MerchantProfile>
  ): Promise<{ merchant: MerchantProfile }> {
    const response = await apiClient.put(`/merchants/${merchantId}`, updates);
    return response.data;
  }

  // Verification
  async submitVerificationDocuments(
    merchantId: string,
    documents: {
      businessLicense?: File;
      taxDocument?: File;
      bankStatement?: File;
      ownerIdDocument?: File;
    }
  ): Promise<{ verificationId: string }> {
    const formData = new FormData();
    
    Object.entries(documents).forEach(([key, file]) => {
      if (file) {
        formData.append(key, file);
      }
    });

    const response = await apiClient.post(
      `/merchants/${merchantId}/verification`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );
    return response.data;
  }

  async getVerificationStatus(merchantId: string): Promise<{
    status: string;
    requiredDocuments: string[];
    submittedDocuments: string[];
    verificationLevel: number;
  }> {
    const response = await apiClient.get(`/merchants/${merchantId}/verification-status`);
    return response.data;
  }

  // Balance and Transactions
  async getBalance(merchantId: string): Promise<MerchantBalance> {
    const response = await apiClient.get(`/merchants/${merchantId}/balance`);
    return response.data;
  }

  async getTransactions(
    merchantId: string,
    params?: {
      page?: number;
      limit?: number;
      startDate?: string;
      endDate?: string;
      status?: string;
      customerId?: string;
    }
  ): Promise<{
    transactions: MerchantTransaction[];
    pagination: {
      page: number;
      limit: number;
      total: number;
      totalPages: number;
    };
  }> {
    const response = await apiClient.get(`/merchants/${merchantId}/transactions`, { params });
    return response.data;
  }

  async getTransaction(
    merchantId: string,
    transactionId: string
  ): Promise<{ transaction: MerchantTransaction }> {
    const response = await apiClient.get(`/merchants/${merchantId}/transactions/${transactionId}`);
    return response.data;
  }

  // QR Code Management
  async generateQRCode(
    merchantId: string,
    qrCodeData: {
      name: string;
      description?: string;
      amount?: number;
      currency?: string;
    }
  ): Promise<{ qrCode: MerchantQRCode }> {
    const response = await apiClient.post(`/merchants/${merchantId}/qr-codes`, qrCodeData);
    return response.data;
  }

  async getQRCodes(merchantId: string): Promise<{ qrCodes: MerchantQRCode[] }> {
    const response = await apiClient.get(`/merchants/${merchantId}/qr-codes`);
    return response.data;
  }

  async updateQRCode(
    merchantId: string,
    qrCodeId: string,
    updates: Partial<MerchantQRCode>
  ): Promise<{ qrCode: MerchantQRCode }> {
    const response = await apiClient.put(`/merchants/${merchantId}/qr-codes/${qrCodeId}`, updates);
    return response.data;
  }

  async deleteQRCode(merchantId: string, qrCodeId: string): Promise<void> {
    await apiClient.delete(`/merchants/${merchantId}/qr-codes/${qrCodeId}`);
  }

  // Payouts
  async requestPayout(
    merchantId: string,
    payoutData: {
      amount: number;
      currency: string;
      paymentMethodId: string;
    }
  ): Promise<{ payout: MerchantPayout }> {
    const response = await apiClient.post(`/merchants/${merchantId}/payouts`, payoutData);
    return response.data;
  }

  async getPayouts(merchantId: string): Promise<{ payouts: MerchantPayout[] }> {
    const response = await apiClient.get(`/merchants/${merchantId}/payouts`);
    return response.data;
  }

  async getPayoutDetails(
    merchantId: string,
    payoutId: string
  ): Promise<{ payout: MerchantPayout }> {
    const response = await apiClient.get(`/merchants/${merchantId}/payouts/${payoutId}`);
    return response.data;
  }

  // Analytics
  async getAnalytics(
    merchantId: string,
    params: {
      startDate: string;
      endDate: string;
      granularity?: 'daily' | 'weekly' | 'monthly';
    }
  ): Promise<MerchantAnalytics> {
    const response = await apiClient.get(`/merchants/${merchantId}/analytics`, { params });
    return response.data;
  }

  async getDashboardData(merchantId: string): Promise<{
    balance: {
      total: number;
      available: number;
      pending: number;
    };
    todayStats: {
      sales: number;
      transactions: number;
      customers: number;
    };
    weeklyStats: {
      sales: number[];
      transactions: number[];
      labels: string[];
    };
    topProducts: Array<{
      id: string;
      name: string;
      sales: number;
      quantity: number;
    }>;
    recentTransactions: Array<{
      id: string;
      customerName: string;
      amount: number;
      status: string;
      timestamp: string;
    }>;
  }> {
    const response = await apiClient.get(`/merchants/${merchantId}/dashboard`);
    return response.data;
  }

  // Payment Methods
  async addPaymentMethod(
    merchantId: string,
    paymentMethod: {
      type: string;
      accountNumber: string;
      routingNumber?: string;
      bankName?: string;
      accountHolderName: string;
      isDefault?: boolean;
    }
  ): Promise<{ paymentMethod: PaymentMethod }> {
    const response = await apiClient.post(`/merchants/${merchantId}/payment-methods`, paymentMethod);
    return response.data;
  }

  async getPaymentMethods(merchantId: string): Promise<{ paymentMethods: PaymentMethod[] }> {
    const response = await apiClient.get(`/merchants/${merchantId}/payment-methods`);
    return response.data;
  }

  async updatePaymentMethod(
    merchantId: string,
    paymentMethodId: string,
    updates: Partial<PaymentMethod>
  ): Promise<{ paymentMethod: PaymentMethod }> {
    const response = await apiClient.put(
      `/merchants/${merchantId}/payment-methods/${paymentMethodId}`,
      updates
    );
    return response.data;
  }

  async deletePaymentMethod(merchantId: string, paymentMethodId: string): Promise<void> {
    await apiClient.delete(`/merchants/${merchantId}/payment-methods/${paymentMethodId}`);
  }

  // Refunds
  async processRefund(
    merchantId: string,
    refundData: {
      transactionId: string;
      amount: number;
      reason: string;
    }
  ): Promise<{ refund: any }> {
    const response = await apiClient.post(`/merchants/${merchantId}/refunds`, refundData);
    return response.data;
  }

  async getRefunds(merchantId: string): Promise<{ refunds: any[] }> {
    const response = await apiClient.get(`/merchants/${merchantId}/refunds`);
    return response.data;
  }

  // Disputes
  async createDispute(
    merchantId: string,
    disputeData: {
      transactionId: string;
      reason: string;
      description: string;
      evidence?: File[];
    }
  ): Promise<{ dispute: any }> {
    const formData = new FormData();
    formData.append('transactionId', disputeData.transactionId);
    formData.append('reason', disputeData.reason);
    formData.append('description', disputeData.description);

    if (disputeData.evidence) {
      disputeData.evidence.forEach((file, index) => {
        formData.append(`evidence[${index}]`, file);
      });
    }

    const response = await apiClient.post(`/merchants/${merchantId}/disputes`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  }

  async getDisputes(merchantId: string): Promise<{ disputes: any[] }> {
    const response = await apiClient.get(`/merchants/${merchantId}/disputes`);
    return response.data;
  }

  // Settings
  async updateSettings(
    merchantId: string,
    settings: {
      autoPayoutEnabled?: boolean;
      autoPayoutThreshold?: number;
      notificationSettings?: {
        emailNotifications?: boolean;
        smsNotifications?: boolean;
        pushNotifications?: boolean;
      };
      businessHours?: {
        monday?: { open: string; close: string; isOpen: boolean };
        tuesday?: { open: string; close: string; isOpen: boolean };
        wednesday?: { open: string; close: string; isOpen: boolean };
        thursday?: { open: string; close: string; isOpen: boolean };
        friday?: { open: string; close: string; isOpen: boolean };
        saturday?: { open: string; close: string; isOpen: boolean };
        sunday?: { open: string; close: string; isOpen: boolean };
      };
    }
  ): Promise<{ settings: any }> {
    const response = await apiClient.put(`/merchants/${merchantId}/settings`, settings);
    return response.data;
  }

  async getSettings(merchantId: string): Promise<{ settings: any }> {
    const response = await apiClient.get(`/merchants/${merchantId}/settings`);
    return response.data;
  }

  // Webhooks
  async addWebhook(
    merchantId: string,
    webhook: {
      url: string;
      events: string[];
      description?: string;
    }
  ): Promise<{ webhook: any }> {
    const response = await apiClient.post(`/merchants/${merchantId}/webhooks`, webhook);
    return response.data;
  }

  async getWebhooks(merchantId: string): Promise<{ webhooks: any[] }> {
    const response = await apiClient.get(`/merchants/${merchantId}/webhooks`);
    return response.data;
  }

  async deleteWebhook(merchantId: string, webhookId: string): Promise<void> {
    await apiClient.delete(`/merchants/${merchantId}/webhooks/${webhookId}`);
  }

  // Search and Discovery
  async searchMerchants(params: {
    query?: string;
    category?: string;
    location?: string;
    latitude?: number;
    longitude?: number;
    radius?: number;
    page?: number;
    limit?: number;
  }): Promise<{
    merchants: Array<{
      id: string;
      businessName: string;
      category: string;
      description: string;
      address: any;
      rating: number;
      reviewCount: number;
      distance?: number;
    }>;
    pagination: any;
  }> {
    const response = await apiClient.get('/merchants/search', { params });
    return response.data;
  }

  // Customer Management
  async getCustomers(merchantId: string): Promise<{
    customers: Array<{
      id: string;
      name: string;
      email: string;
      phone: string;
      totalSpent: number;
      transactionCount: number;
      lastTransaction: string;
    }>;
  }> {
    const response = await apiClient.get(`/merchants/${merchantId}/customers`);
    return response.data;
  }

  async getCustomerDetails(
    merchantId: string,
    customerId: string
  ): Promise<{
    customer: any;
    transactions: MerchantTransaction[];
    analytics: any;
  }> {
    const response = await apiClient.get(`/merchants/${merchantId}/customers/${customerId}`);
    return response.data;
  }

  // Reports
  async generateReport(
    merchantId: string,
    reportType: 'sales' | 'transactions' | 'customers' | 'tax',
    params: {
      startDate: string;
      endDate: string;
      format: 'pdf' | 'csv' | 'excel';
    }
  ): Promise<{ reportUrl: string }> {
    const response = await apiClient.post(`/merchants/${merchantId}/reports/${reportType}`, params);
    return response.data;
  }

  // Utility Methods
  async validateBusinessInformation(businessInfo: any): Promise<{ isValid: boolean; errors: string[] }> {
    const response = await apiClient.post('/merchants/validate-business', businessInfo);
    return response.data;
  }

  async getBusinessCategories(): Promise<{ categories: string[] }> {
    const response = await apiClient.get('/merchants/categories');
    return response.data;
  }

  async getSupportedCountries(): Promise<{ countries: Array<{ code: string; name: string }> }> {
    const response = await apiClient.get('/merchants/supported-countries');
    return response.data;
  }
}

export const merchantService = new MerchantService();
export default merchantService;