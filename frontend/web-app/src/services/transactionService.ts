import apiClient from '../api/client';
import {
  TransactionFilter,
  TransactionSummary,
  TransactionDetails,
  DisputeRequest,
} from '../types/transaction';
import { Transaction } from '../types/wallet';

class TransactionService {
  private baseUrl = '/api/v1/transactions';

  // Get transactions with filters
  async getTransactions(filter: TransactionFilter) {
    return apiClient.get<{
      content: Transaction[];
      totalPages: number;
      totalElements: number;
      number: number;
    }>(this.baseUrl, { params: filter });
  }

  // Get transaction details
  async getTransactionDetails(transactionId: string) {
    return apiClient.get<TransactionDetails>(`${this.baseUrl}/${transactionId}`);
  }

  // Get transaction summary/analytics
  async getTransactionSummary(params: {
    startDate?: string;
    endDate?: string;
    walletId?: string;
  }) {
    return apiClient.get<TransactionSummary>(`${this.baseUrl}/summary`, { params });
  }

  // Search transactions
  async searchTransactions(searchTerm: string, filter?: TransactionFilter) {
    return apiClient.get<Transaction[]>(`${this.baseUrl}/search`, {
      params: { q: searchTerm, ...filter },
    });
  }

  // Export transactions
  async exportTransactions(format: 'csv' | 'pdf', filter: TransactionFilter) {
    return apiClient.get(`${this.baseUrl}/export`, {
      params: { format, ...filter },
      responseType: 'blob',
    });
  }

  // Download receipt
  async downloadReceipt(transactionId: string) {
    return apiClient.get(`${this.baseUrl}/${transactionId}/receipt`, {
      responseType: 'blob',
    });
  }

  // Email receipt
  async emailReceipt(transactionId: string, email?: string) {
    return apiClient.post(`${this.baseUrl}/${transactionId}/receipt/email`, { email });
  }

  // Dispute transaction
  async disputeTransaction(transactionId: string, data: DisputeRequest) {
    return apiClient.post(`${this.baseUrl}/${transactionId}/dispute`, data);
  }

  // Get dispute details
  async getDisputeDetails(transactionId: string) {
    return apiClient.get(`${this.baseUrl}/${transactionId}/dispute`);
  }

  // Cancel dispute
  async cancelDispute(transactionId: string) {
    return apiClient.post(`${this.baseUrl}/${transactionId}/dispute/cancel`);
  }

  // Get transaction timeline/events
  async getTransactionTimeline(transactionId: string) {
    return apiClient.get(`${this.baseUrl}/${transactionId}/timeline`);
  }

  // Retry failed transaction
  async retryTransaction(transactionId: string) {
    return apiClient.post(`${this.baseUrl}/${transactionId}/retry`);
  }

  // Cancel pending transaction
  async cancelTransaction(transactionId: string, reason?: string) {
    return apiClient.post(`${this.baseUrl}/${transactionId}/cancel`, { reason });
  }

  // Get transaction statistics
  async getTransactionStats(params: {
    period: 'daily' | 'weekly' | 'monthly' | 'yearly';
    startDate?: string;
    endDate?: string;
  }) {
    return apiClient.get(`${this.baseUrl}/stats`, { params });
  }

  // Get spending insights
  async getSpendingInsights(params: {
    period: 'week' | 'month' | 'quarter' | 'year';
    walletId?: string;
  }) {
    return apiClient.get(`${this.baseUrl}/insights/spending`, { params });
  }

  // Get merchant analytics
  async getMerchantAnalytics(params: {
    startDate?: string;
    endDate?: string;
    limit?: number;
  }) {
    return apiClient.get(`${this.baseUrl}/analytics/merchants`, { params });
  }

  // Bulk operations
  async bulkExportTransactions(transactionIds: string[], format: 'csv' | 'pdf') {
    return apiClient.post(
      `${this.baseUrl}/bulk/export`,
      { transactionIds, format },
      { responseType: 'blob' }
    );
  }

  // Get recurring transactions
  async getRecurringTransactions() {
    return apiClient.get(`${this.baseUrl}/recurring`);
  }

  // Transaction categories
  async getTransactionCategories() {
    return apiClient.get(`${this.baseUrl}/categories`);
  }

  async categorizeTransaction(transactionId: string, categoryId: string) {
    return apiClient.put(`${this.baseUrl}/${transactionId}/category`, { categoryId });
  }
}

export const transactionService = new TransactionService();