import apiClient from '../api/client';
import {
  SendMoneyRequest,
  PaymentResponse,
  RequestMoneyRequest,
  PaymentRequest,
  Transaction,
  PaymentStatus,
  PaymentMethod,
  PaymentLimit,
  QRCodeData,
} from '../types/payment';

interface PaymentSearchParams {
  status?: PaymentStatus;
  fromDate?: string;
  toDate?: string;
  minAmount?: number;
  maxAmount?: number;
  currency?: string;
  counterpartyId?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

interface BulkPaymentRequest {
  payments: Array<{
    recipientEmail: string;
    amount: number;
    currency: string;
    note?: string;
  }>;
  paymentMethodId?: string;
  scheduledDate?: string;
}

interface RecurringPaymentRequest {
  recipientEmail: string;
  amount: number;
  currency: string;
  frequency: 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'yearly';
  startDate: string;
  endDate?: string;
  paymentMethodId?: string;
  note?: string;
}

interface SplitPaymentRequest {
  groupName: string;
  totalAmount: number;
  currency: string;
  splitType: 'equal' | 'custom' | 'percentage';
  participants: Array<{
    email: string;
    amount?: number;
    percentage?: number;
  }>;
  note?: string;
  dueDate?: string;
}

interface InternationalTransferRequest {
  recipientDetails: {
    name: string;
    email?: string;
    accountNumber: string;
    swiftCode?: string;
    iban?: string;
    address: {
      street: string;
      city: string;
      state?: string;
      country: string;
      postalCode: string;
    };
  };
  amount: number;
  sourceCurrency: string;
  targetCurrency: string;
  purpose: string;
  reference?: string;
  paymentMethodId?: string;
}

interface PaymentFeeCalculation {
  amount: number;
  currency: string;
  paymentType: 'domestic' | 'international' | 'instant';
  paymentMethodType?: 'bank_account' | 'card' | 'wallet';
}

interface PaymentFeeResponse {
  baseFee: number;
  percentageFee: number;
  totalFee: number;
  finalAmount: number;
  currency: string;
  exchangeRate?: number;
  estimatedDelivery?: string;
}

interface PaymentAnalytics {
  period: 'daily' | 'weekly' | 'monthly' | 'yearly';
  startDate: string;
  endDate: string;
  metrics: Array<{
    date: string;
    sent: number;
    received: number;
    totalVolume: number;
    averageTransaction: number;
    transactionCount: number;
  }>;
  totals: {
    totalSent: number;
    totalReceived: number;
    netFlow: number;
    transactionCount: number;
  };
}

class PaymentServiceClient {
  private baseUrl = '/api/v1/payments';

  // Send money operations
  async sendMoney(data: SendMoneyRequest): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/send`, data);
  }

  async sendMoneyInstant(data: SendMoneyRequest): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/send/instant`, data);
  }

  async sendMoneyScheduled(data: SendMoneyRequest & { scheduledDate: string }): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/send/scheduled`, data);
  }

  async sendBulkPayments(data: BulkPaymentRequest): Promise<PaymentResponse[]> {
    return apiClient.post<PaymentResponse[]>(`${this.baseUrl}/send/bulk`, data);
  }

  // Request money operations
  async requestMoney(data: RequestMoneyRequest): Promise<PaymentRequest> {
    return apiClient.post<PaymentRequest>(`${this.baseUrl}/request`, data);
  }

  async getPaymentRequests(params?: { status?: string; type?: 'sent' | 'received' }): Promise<PaymentRequest[]> {
    return apiClient.get<PaymentRequest[]>(`${this.baseUrl}/requests`, { params });
  }

  async getPaymentRequest(requestId: string): Promise<PaymentRequest> {
    return apiClient.get<PaymentRequest>(`${this.baseUrl}/requests/${requestId}`);
  }

  async cancelPaymentRequest(requestId: string, reason?: string): Promise<void> {
    return apiClient.post<void>(`${this.baseUrl}/requests/${requestId}/cancel`, { reason });
  }

  async payPaymentRequest(requestId: string, paymentMethodId?: string): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/requests/${requestId}/pay`, { paymentMethodId });
  }

  async declinePaymentRequest(requestId: string, reason?: string): Promise<void> {
    return apiClient.post<void>(`${this.baseUrl}/requests/${requestId}/decline`, { reason });
  }

  // Transaction operations
  async getTransactions(params?: PaymentSearchParams): Promise<Transaction[]> {
    return apiClient.get<Transaction[]>(`${this.baseUrl}/transactions`, { params });
  }

  async getTransaction(transactionId: string): Promise<Transaction> {
    return apiClient.get<Transaction>(`${this.baseUrl}/transactions/${transactionId}`);
  }

  async cancelTransaction(transactionId: string, reason?: string): Promise<void> {
    return apiClient.post<void>(`${this.baseUrl}/transactions/${transactionId}/cancel`, { reason });
  }

  async retryTransaction(transactionId: string): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/transactions/${transactionId}/retry`);
  }

  async getTransactionReceipt(transactionId: string): Promise<Blob> {
    return apiClient.get(`${this.baseUrl}/transactions/${transactionId}/receipt`, {
      responseType: 'blob',
    });
  }

  // Recurring payments
  async createRecurringPayment(data: RecurringPaymentRequest): Promise<{ id: string; status: string }> {
    return apiClient.post(`${this.baseUrl}/recurring`, data);
  }

  async getRecurringPayments(): Promise<any[]> {
    return apiClient.get(`${this.baseUrl}/recurring`);
  }

  async getRecurringPayment(recurringId: string): Promise<any> {
    return apiClient.get(`${this.baseUrl}/recurring/${recurringId}`);
  }

  async updateRecurringPayment(recurringId: string, data: Partial<RecurringPaymentRequest>): Promise<any> {
    return apiClient.put(`${this.baseUrl}/recurring/${recurringId}`, data);
  }

  async pauseRecurringPayment(recurringId: string): Promise<void> {
    return apiClient.post(`${this.baseUrl}/recurring/${recurringId}/pause`);
  }

  async resumeRecurringPayment(recurringId: string): Promise<void> {
    return apiClient.post(`${this.baseUrl}/recurring/${recurringId}/resume`);
  }

  async cancelRecurringPayment(recurringId: string, reason?: string): Promise<void> {
    return apiClient.post(`${this.baseUrl}/recurring/${recurringId}/cancel`, { reason });
  }

  // Split payments / Group payments
  async createSplitPayment(data: SplitPaymentRequest): Promise<{ id: string; status: string }> {
    return apiClient.post(`${this.baseUrl}/split`, data);
  }

  async getSplitPayments(params?: { status?: string; role?: 'organizer' | 'participant' }): Promise<any[]> {
    return apiClient.get(`${this.baseUrl}/split`, { params });
  }

  async getSplitPayment(splitId: string): Promise<any> {
    return apiClient.get(`${this.baseUrl}/split/${splitId}`);
  }

  async updateSplitPayment(splitId: string, data: Partial<SplitPaymentRequest>): Promise<any> {
    return apiClient.put(`${this.baseUrl}/split/${splitId}`, data);
  }

  async settleSplitPayment(splitId: string): Promise<PaymentResponse> {
    return apiClient.post(`${this.baseUrl}/split/${splitId}/settle`);
  }

  async remindSplitParticipants(splitId: string, participantIds?: string[]): Promise<void> {
    return apiClient.post(`${this.baseUrl}/split/${splitId}/remind`, { participantIds });
  }

  // International transfers
  async createInternationalTransfer(data: InternationalTransferRequest): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/international`, data);
  }

  async getExchangeRate(sourceCurrency: string, targetCurrency: string, amount?: number): Promise<{
    rate: number;
    inverseRate: number;
    timestamp: string;
    amount?: number;
    convertedAmount?: number;
  }> {
    return apiClient.get(`${this.baseUrl}/international/exchange-rate`, {
      params: { sourceCurrency, targetCurrency, amount },
    });
  }

  async getSupportedCountries(): Promise<Array<{
    code: string;
    name: string;
    currency: string;
    supportedServices: string[];
  }>> {
    return apiClient.get(`${this.baseUrl}/international/countries`);
  }

  // Payment methods
  async getPaymentMethods(): Promise<PaymentMethod[]> {
    return apiClient.get<PaymentMethod[]>('/api/v1/payment-methods');
  }

  async addPaymentMethod(data: any): Promise<PaymentMethod> {
    return apiClient.post<PaymentMethod>('/api/v1/payment-methods', data);
  }

  async updatePaymentMethod(methodId: string, data: any): Promise<PaymentMethod> {
    return apiClient.put<PaymentMethod>(`/api/v1/payment-methods/${methodId}`, data);
  }

  async removePaymentMethod(methodId: string): Promise<void> {
    return apiClient.delete(`/api/v1/payment-methods/${methodId}`);
  }

  async setDefaultPaymentMethod(methodId: string): Promise<PaymentMethod> {
    return apiClient.put<PaymentMethod>(`/api/v1/payment-methods/${methodId}/set-default`);
  }

  async verifyPaymentMethod(methodId: string, verificationData: any): Promise<PaymentMethod> {
    return apiClient.post<PaymentMethod>(`/api/v1/payment-methods/${methodId}/verify`, verificationData);
  }

  // Payment limits
  async getPaymentLimits(): Promise<PaymentLimit[]> {
    return apiClient.get<PaymentLimit[]>(`${this.baseUrl}/limits`);
  }

  async requestLimitIncrease(data: {
    limitType: string;
    requestedLimit: number;
    reason: string;
    documents?: string[];
  }): Promise<{ requestId: string; status: string }> {
    return apiClient.post(`${this.baseUrl}/limits/increase-request`, data);
  }

  // Fee calculation
  async calculateFees(data: PaymentFeeCalculation): Promise<PaymentFeeResponse> {
    return apiClient.post<PaymentFeeResponse>(`${this.baseUrl}/fees/calculate`, data);
  }

  // QR code operations
  async generatePaymentQR(data: {
    amount?: number;
    currency?: string;
    note?: string;
    expiresIn?: number;
  }): Promise<{ qrCode: string; qrData: QRCodeData }> {
    return apiClient.post(`${this.baseUrl}/qr/generate`, data);
  }

  async scanPaymentQR(qrData: string): Promise<QRCodeData> {
    return apiClient.post<QRCodeData>(`${this.baseUrl}/qr/scan`, { qrData });
  }

  async payWithQR(qrData: string, paymentMethodId?: string): Promise<PaymentResponse> {
    return apiClient.post<PaymentResponse>(`${this.baseUrl}/qr/pay`, { qrData, paymentMethodId });
  }

  // Analytics and reporting
  async getPaymentAnalytics(period: 'daily' | 'weekly' | 'monthly' | 'yearly', params?: {
    startDate?: string;
    endDate?: string;
  }): Promise<PaymentAnalytics> {
    return apiClient.get<PaymentAnalytics>(`${this.baseUrl}/analytics`, { params: { period, ...params } });
  }

  async exportTransactions(format: 'csv' | 'pdf' | 'xlsx', params?: PaymentSearchParams): Promise<Blob> {
    return apiClient.get(`${this.baseUrl}/transactions/export`, {
      params: { format, ...params },
      responseType: 'blob',
    });
  }

  async getStatement(params: {
    startDate: string;
    endDate: string;
    format?: 'pdf' | 'html';
  }): Promise<Blob> {
    return apiClient.get(`${this.baseUrl}/statement`, {
      params,
      responseType: 'blob',
    });
  }

  // Contact and beneficiary management
  async getContacts(params?: { search?: string; favorite?: boolean }): Promise<Array<{
    id: string;
    name: string;
    email: string;
    avatar?: string;
    isFavorite: boolean;
    lastTransactionDate?: string;
  }>> {
    return apiClient.get(`${this.baseUrl}/contacts`, { params });
  }

  async addContact(data: {
    name: string;
    email: string;
    nickname?: string;
  }): Promise<{ id: string }> {
    return apiClient.post(`${this.baseUrl}/contacts`, data);
  }

  async updateContact(contactId: string, data: any): Promise<void> {
    return apiClient.put(`${this.baseUrl}/contacts/${contactId}`, data);
  }

  async removeContact(contactId: string): Promise<void> {
    return apiClient.delete(`${this.baseUrl}/contacts/${contactId}`);
  }

  async toggleFavoriteContact(contactId: string): Promise<void> {
    return apiClient.post(`${this.baseUrl}/contacts/${contactId}/toggle-favorite`);
  }

  // Notifications preferences
  async getPaymentNotificationPreferences(): Promise<{
    emailNotifications: boolean;
    smsNotifications: boolean;
    pushNotifications: boolean;
    notificationTypes: {
      paymentSent: boolean;
      paymentReceived: boolean;
      paymentRequest: boolean;
      paymentFailed: boolean;
      recurringPayment: boolean;
      lowBalance: boolean;
    };
  }> {
    return apiClient.get(`${this.baseUrl}/notifications/preferences`);
  }

  async updatePaymentNotificationPreferences(preferences: any): Promise<void> {
    return apiClient.put(`${this.baseUrl}/notifications/preferences`, preferences);
  }

  // Dispute and support
  async createDispute(transactionId: string, data: {
    reason: string;
    description: string;
    evidence?: string[];
  }): Promise<{ disputeId: string; status: string }> {
    return apiClient.post(`${this.baseUrl}/transactions/${transactionId}/dispute`, data);
  }

  async getDisputes(params?: { status?: string }): Promise<any[]> {
    return apiClient.get(`${this.baseUrl}/disputes`, { params });
  }

  async getDispute(disputeId: string): Promise<any> {
    return apiClient.get(`${this.baseUrl}/disputes/${disputeId}`);
  }

  async updateDispute(disputeId: string, data: {
    message?: string;
    evidence?: string[];
  }): Promise<void> {
    return apiClient.put(`${this.baseUrl}/disputes/${disputeId}`, data);
  }

  async cancelDispute(disputeId: string, reason?: string): Promise<void> {
    return apiClient.post(`${this.baseUrl}/disputes/${disputeId}/cancel`, { reason });
  }
}

export const paymentServiceClient = new PaymentServiceClient();