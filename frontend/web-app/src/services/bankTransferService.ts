import { apiClient } from './apiClient';

// Types for Bank Transfer API
export interface BankAccount {
  id: string;
  accountName: string;
  accountNumber: string;
  bankName: string;
  routingNumber: string;
  accountType: 'CHECKING' | 'SAVINGS';
  isVerified: boolean;
  isInstantEligible: boolean;
  balance?: number;
  lastFourDigits: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface InstantTransferRequest {
  amount: number;
  recipientAccountId: string;
  memo?: string;
  transferType: 'INSTANT' | 'STANDARD';
}

export interface TransferResponse {
  transferId: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  amount: number;
  fee: number;
  totalAmount: number;
  recipientAccount: BankAccount;
  estimatedArrival: string;
  createdAt: string;
}

export interface TransferFeeCalculation {
  amount: number;
  transferType: 'INSTANT' | 'STANDARD';
  fee: number;
  feePercentage: number;
  totalAmount: number;
}

export interface TransferLimits {
  dailyLimit: number;
  weeklyLimit: number;
  monthlyLimit: number;
  singleTransferLimit: number;
  remainingDaily: number;
  remainingWeekly: number;
  remainingMonthly: number;
}

export interface PlaidLinkToken {
  linkToken: string;
  expiration: string;
}

export interface BankVerificationRequest {
  accountId: string;
  microDepositAmounts: number[];
}

class BankTransferService {
  private baseUrl = '/api/v1/bank-transfers';

  // Bank Account Management
  async getBankAccounts(): Promise<BankAccount[]> {
    const response = await apiClient.get(`${this.baseUrl}/accounts`);
    return response.data;
  }

  async getBankAccount(accountId: string): Promise<BankAccount> {
    const response = await apiClient.get(`${this.baseUrl}/accounts/${accountId}`);
    return response.data;
  }

  async addBankAccount(plaidPublicToken: string, accountId: string): Promise<BankAccount> {
    const response = await apiClient.post(`${this.baseUrl}/accounts`, {
      plaidPublicToken,
      accountId,
    });
    return response.data;
  }

  async removeBankAccount(accountId: string): Promise<void> {
    await apiClient.delete(`${this.baseUrl}/accounts/${accountId}`);
  }

  async setDefaultBankAccount(accountId: string): Promise<void> {
    await apiClient.patch(`${this.baseUrl}/accounts/${accountId}/default`);
  }

  // Account Verification
  async initiateMicroDepositVerification(accountId: string): Promise<void> {
    await apiClient.post(`${this.baseUrl}/accounts/${accountId}/verify/micro-deposits`);
  }

  async verifyMicroDeposits(request: BankVerificationRequest): Promise<void> {
    await apiClient.post(`${this.baseUrl}/accounts/${request.accountId}/verify/micro-deposits/confirm`, {
      amounts: request.microDepositAmounts,
    });
  }

  // Plaid Integration
  async createPlaidLinkToken(): Promise<PlaidLinkToken> {
    const response = await apiClient.post(`${this.baseUrl}/plaid/link-token`);
    return response.data;
  }

  // Transfer Operations
  async initiateTransfer(request: InstantTransferRequest): Promise<TransferResponse> {
    const response = await apiClient.post(`${this.baseUrl}/transfers`, request);
    return response.data;
  }

  async getTransfer(transferId: string): Promise<TransferResponse> {
    const response = await apiClient.get(`${this.baseUrl}/transfers/${transferId}`);
    return response.data;
  }

  async getTransferHistory(
    page = 0,
    size = 20,
    status?: string,
    startDate?: string,
    endDate?: string
  ): Promise<{
    transfers: TransferResponse[];
    totalElements: number;
    totalPages: number;
    currentPage: number;
  }> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    if (status) params.append('status', status);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);

    const response = await apiClient.get(`${this.baseUrl}/transfers?${params}`);
    return response.data;
  }

  async cancelTransfer(transferId: string): Promise<void> {
    await apiClient.post(`${this.baseUrl}/transfers/${transferId}/cancel`);
  }

  // Fee Calculation
  async calculateTransferFee(amount: number, transferType: 'INSTANT' | 'STANDARD'): Promise<TransferFeeCalculation> {
    const response = await apiClient.post(`${this.baseUrl}/fees/calculate`, {
      amount,
      transferType,
    });
    return response.data;
  }

  // Transfer Limits
  async getTransferLimits(): Promise<TransferLimits> {
    const response = await apiClient.get(`${this.baseUrl}/limits`);
    return response.data;
  }

  // Account Balance
  async getAccountBalance(accountId: string): Promise<{ balance: number; availableBalance: number }> {
    const response = await apiClient.get(`${this.baseUrl}/accounts/${accountId}/balance`);
    return response.data;
  }

  async refreshAccountBalance(accountId: string): Promise<{ balance: number; availableBalance: number }> {
    const response = await apiClient.post(`${this.baseUrl}/accounts/${accountId}/refresh-balance`);
    return response.data;
  }

  // Recurring Transfers
  async createRecurringTransfer(request: {
    amount: number;
    recipientAccountId: string;
    frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
    startDate: string;
    endDate?: string;
    memo?: string;
  }): Promise<{ recurringTransferId: string }> {
    const response = await apiClient.post(`${this.baseUrl}/recurring-transfers`, request);
    return response.data;
  }

  async getRecurringTransfers(): Promise<Array<{
    id: string;
    amount: number;
    recipientAccount: BankAccount;
    frequency: string;
    startDate: string;
    endDate?: string;
    isActive: boolean;
    nextTransferDate: string;
  }>> {
    const response = await apiClient.get(`${this.baseUrl}/recurring-transfers`);
    return response.data;
  }

  async cancelRecurringTransfer(recurringTransferId: string): Promise<void> {
    await apiClient.delete(`${this.baseUrl}/recurring-transfers/${recurringTransferId}`);
  }

  // Transfer Templates
  async saveTransferTemplate(request: {
    name: string;
    amount: number;
    recipientAccountId: string;
    memo?: string;
    transferType: 'INSTANT' | 'STANDARD';
  }): Promise<{ templateId: string }> {
    const response = await apiClient.post(`${this.baseUrl}/templates`, request);
    return response.data;
  }

  async getTransferTemplates(): Promise<Array<{
    id: string;
    name: string;
    amount: number;
    recipientAccount: BankAccount;
    memo?: string;
    transferType: string;
    createdAt: string;
  }>> {
    const response = await apiClient.get(`${this.baseUrl}/templates`);
    return response.data;
  }

  async deleteTransferTemplate(templateId: string): Promise<void> {
    await apiClient.delete(`${this.baseUrl}/templates/${templateId}`);
  }

  // Notifications and Preferences
  async updateNotificationPreferences(preferences: {
    emailNotifications: boolean;
    smsNotifications: boolean;
    pushNotifications: boolean;
    transferCompletionNotifications: boolean;
    lowBalanceAlerts: boolean;
    suspiciousActivityAlerts: boolean;
  }): Promise<void> {
    await apiClient.put(`${this.baseUrl}/notifications/preferences`, preferences);
  }

  async getNotificationPreferences(): Promise<{
    emailNotifications: boolean;
    smsNotifications: boolean;
    pushNotifications: boolean;
    transferCompletionNotifications: boolean;
    lowBalanceAlerts: boolean;
    suspiciousActivityAlerts: boolean;
  }> {
    const response = await apiClient.get(`${this.baseUrl}/notifications/preferences`);
    return response.data;
  }

  // Utility methods
  formatAccountNumber(accountNumber: string): string {
    if (accountNumber.length <= 4) return accountNumber;
    return `****${accountNumber.slice(-4)}`;
  }

  formatBankName(bankName: string): string {
    // Capitalize first letter of each word
    return bankName
      .toLowerCase()
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  getTransferStatusColor(status: string): 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'PROCESSING':
        return 'info';
      case 'PENDING':
        return 'warning';
      case 'FAILED':
      case 'CANCELLED':
        return 'error';
      default:
        return 'default';
    }
  }

  getTransferStatusIcon(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return '✅';
      case 'PROCESSING':
        return '⏳';
      case 'PENDING':
        return '⏰';
      case 'FAILED':
        return '❌';
      case 'CANCELLED':
        return '🚫';
      default:
        return '❓';
    }
  }

  calculateEstimatedArrival(transferType: 'INSTANT' | 'STANDARD'): string {
    const now = new Date();
    
    if (transferType === 'INSTANT') {
      // Add 5 minutes for instant transfer
      now.setMinutes(now.getMinutes() + 5);
      return now.toISOString();
    } else {
      // Add 1-3 business days for standard transfer
      // For simplicity, add 2 days
      now.setDate(now.getDate() + 2);
      return now.toISOString();
    }
  }

  isBusinessDay(date: Date): boolean {
    const dayOfWeek = date.getDay();
    return dayOfWeek >= 1 && dayOfWeek <= 5; // Monday = 1, Friday = 5
  }

  getNextBusinessDay(date: Date): Date {
    const nextDay = new Date(date);
    nextDay.setDate(nextDay.getDate() + 1);
    
    while (!this.isBusinessDay(nextDay)) {
      nextDay.setDate(nextDay.getDate() + 1);
    }
    
    return nextDay;
  }
}

export const bankTransferService = new BankTransferService();
export default bankTransferService;