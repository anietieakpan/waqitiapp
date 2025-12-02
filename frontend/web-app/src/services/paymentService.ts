import axios from '../utils/axios';
import { 
  SendMoneyRequest, 
  PaymentResponse, 
  RequestMoneyRequest,
  Transaction,
  PaymentRequest,
  PaymentMethod,
  PaymentLimit,
  QRCodeData
} from '@/types/payment';
import { Contact, ContactRequest, ContactSearchResult } from '@/types/contact';

class PaymentService {
  private axiosInstance = axios;

  // Payment operations
  async sendMoney(request: SendMoneyRequest): Promise<PaymentResponse> {
    const response = await this.axiosInstance.post('/payments/send', request);
    return response.data;
  }

  async requestMoney(request: RequestMoneyRequest): Promise<any> {
    const response = await this.axiosInstance.post('/payments/request', request);
    return response.data;
  }

  async payRequest(requestId: string): Promise<PaymentResponse> {
    const response = await this.axiosInstance.post(`/payments/requests/${requestId}/pay`);
    return response.data;
  }

  async declineRequest(requestId: string): Promise<void> {
    await this.axiosInstance.post(`/payments/requests/${requestId}/decline`);
  }

  async cancelRequest(requestId: string): Promise<void> {
    await this.axiosInstance.delete(`/payments/requests/${requestId}`);
  }

  async cancelMoneyRequest(requestId: string): Promise<void> {
    await this.axiosInstance.delete(`/payments/requests/${requestId}`);
  }

  async getMoneyRequests(): Promise<any[]> {
    const response = await this.axiosInstance.get('/payments/requests/all');
    return response.data;
  }

  // Transaction history
  async getTransactions(params: {
    page?: number;
    limit?: number;
    search?: string;
    status?: string;
    type?: string;
    dateFrom?: string;
    dateTo?: string;
  }): Promise<{
    transactions: Transaction[];
    totalCount: number;
    totalPages: number;
  }> {
    const response = await this.axiosInstance.get('/payments/transactions', { params });
    return response.data;
  }

  async getTransactionDetails(transactionId: string): Promise<Transaction> {
    const response = await this.axiosInstance.get(`/payments/transactions/${transactionId}`);
    return response.data;
  }

  async downloadReceipt(transactionId: string): Promise<Blob> {
    const response = await this.axiosInstance.get(
      `/payments/transactions/${transactionId}/receipt`,
      { responseType: 'blob' }
    );
    return response.data;
  }

  // Payment requests
  async getPaymentRequests(type: 'sent' | 'received'): Promise<PaymentRequest[]> {
    const response = await this.axiosInstance.get('/payments/requests', {
      params: { type },
    });
    return response.data;
  }

  async getPaymentRequestDetails(requestId: string): Promise<PaymentRequest> {
    const response = await this.axiosInstance.get(`/payments/requests/${requestId}`);
    return response.data;
  }

  // Payment methods
  async getPaymentMethods(): Promise<PaymentMethod[]> {
    const response = await this.axiosInstance.get('/payments/methods');
    return response.data;
  }

  async addPaymentMethod(method: {
    type: string;
    details: any;
  }): Promise<PaymentMethod> {
    const response = await this.axiosInstance.post('/payments/methods', method);
    return response.data;
  }

  async updatePaymentMethod(
    methodId: string,
    updates: Partial<PaymentMethod>
  ): Promise<PaymentMethod> {
    const response = await this.axiosInstance.patch(
      `/payments/methods/${methodId}`,
      updates
    );
    return response.data;
  }

  async deletePaymentMethod(methodId: string): Promise<void> {
    await this.axiosInstance.delete(`/payments/methods/${methodId}`);
  }

  async setDefaultPaymentMethod(methodId: string): Promise<void> {
    await this.axiosInstance.post(`/payments/methods/${methodId}/default`);
  }

  // Payment limits
  async getPaymentLimits(): Promise<PaymentLimit[]> {
    const response = await this.axiosInstance.get('/payments/limits');
    return response.data;
  }

  async updatePaymentLimit(
    limitType: string,
    newLimit: number
  ): Promise<PaymentLimit> {
    const response = await this.axiosInstance.post('/payments/limits', {
      type: limitType,
      limit: newLimit,
    });
    return response.data;
  }

  // QR codes
  async generateQRCode(data: {
    type: 'payment_request' | 'user_profile';
    amount?: number;
    currency?: string;
    note?: string;
    expiresAt?: string;
  }): Promise<string> {
    const response = await this.axiosInstance.post('/payments/qr/generate', data);
    return response.data.qrCodeUrl;
  }

  async generateQrCode(data: {
    type: 'static' | 'dynamic';
    amount?: number;
    note?: string;
    expiresIn?: number;
  }): Promise<{
    code: string;
    qrData: string;
    type: 'static' | 'dynamic';
    amount?: number;
    note?: string;
    expiresAt: string;
  }> {
    const response = await this.axiosInstance.post('/payments/qr/generate', data);
    return response.data;
  }

  async scanQrCode(qrData: string): Promise<{
    userId: string;
    username: string;
    amount?: number;
    note?: string;
    type: 'static' | 'dynamic';
  }> {
    const response = await this.axiosInstance.post('/payments/qr/scan', { qrData });
    return response.data;
  }

  async createQrPayment(data: {
    qrCode: string;
    amount: number;
    pin: string;
    note?: string;
  }): Promise<Transaction> {
    const response = await this.axiosInstance.post('/payments/qr/pay', data);
    return response.data;
  }

  async processQRCode(qrData: string): Promise<QRCodeData> {
    const response = await this.axiosInstance.post('/payments/qr/process', {
      qrData,
    });
    return response.data;
  }

  // Contacts
  async getContacts(): Promise<Contact[]> {
    const response = await this.axiosInstance.get('/contacts');
    return response.data;
  }

  async searchUsers(query: string): Promise<any[]> {
    const response = await this.axiosInstance.get('/users/search', {
      params: { q: query }
    });
    return response.data;
  }

  async getFrequentRecipients(): Promise<any[]> {
    const response = await this.axiosInstance.get('/users/frequent-recipients');
    return response.data;
  }

  async searchContacts(searchTerm: string): Promise<Contact[]> {
    const response = await this.axiosInstance.get('/contacts/search', {
      params: { q: searchTerm },
    });
    return response.data;
  }

  async addContact(contact: ContactRequest): Promise<Contact> {
    const response = await this.axiosInstance.post('/contacts', contact);
    return response.data;
  }

  async updateContact(
    contactId: string,
    updates: Partial<ContactRequest>
  ): Promise<Contact> {
    const response = await this.axiosInstance.patch(`/contacts/${contactId}`, updates);
    return response.data;
  }

  async deleteContact(contactId: string): Promise<void> {
    await this.axiosInstance.delete(`/contacts/${contactId}`);
  }

  async toggleFavorite(contactId: string, isFavorite: boolean): Promise<void> {
    await this.axiosInstance.post(`/contacts/${contactId}/favorite`, {
      isFavorite,
    });
  }

  async getContactTransactionHistory(contactId: string): Promise<Transaction[]> {
    const response = await this.axiosInstance.get(
      `/contacts/${contactId}/transactions`
    );
    return response.data;
  }

  // Fees and rates
  async calculateFee(params: {
    amount: number;
    currency: string;
    paymentMethod?: string;
    recipientCountry?: string;
  }): Promise<{
    fee: number;
    total: number;
    exchangeRate?: number;
  }> {
    const response = await this.axiosInstance.post('/payments/calculate-fee', params);
    return response.data;
  }

  async getExchangeRates(baseCurrency: string): Promise<Record<string, number>> {
    const response = await this.axiosInstance.get('/payments/exchange-rates', {
      params: { base: baseCurrency },
    });
    return response.data;
  }

  // Recurring payments
  async getRecurringPayments(): Promise<any[]> {
    const response = await this.axiosInstance.get('/payments/recurring');
    return response.data;
  }

  async createRecurringPayment(params: {
    recipientId: string;
    amount: number;
    currency: string;
    frequency: 'daily' | 'weekly' | 'monthly' | 'yearly';
    startDate: string;
    endDate?: string;
    note?: string;
  }): Promise<any> {
    const response = await this.axiosInstance.post('/payments/recurring', params);
    return response.data;
  }

  async cancelRecurringPayment(recurringPaymentId: string): Promise<void> {
    await this.axiosInstance.delete(`/payments/recurring/${recurringPaymentId}`);
  }

  async generateRequestLink(requestId: string): Promise<string> {
    const response = await this.axiosInstance.post(`/payments/requests/${requestId}/link`);
    return response.data.link;
  }

  // Statistics
  async getPaymentStatistics(params: {
    period: 'day' | 'week' | 'month' | 'year';
    startDate?: string;
    endDate?: string;
  }): Promise<{
    totalSent: number;
    totalReceived: number;
    totalFees: number;
    transactionCount: number;
    averageTransactionSize: number;
  }> {
    const response = await this.axiosInstance.get('/payments/statistics', { params });
    return response.data;
  }
}

export const paymentService = new PaymentService();