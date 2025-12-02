import apiClient from '../api/client';
import {
  Wallet,
  WalletBalance,
  Transaction,
  WalletLimit,
  CreateWalletRequest,
  AddMoneyRequest,
  WithdrawMoneyRequest,
  TransferMoneyRequest,
  PaymentMethod,
  BankAccount,
  EnhancedWalletBalance,
  SecuritySettings,
  Goal,
  Contact,
} from '../types/wallet';

class WalletService {
  private baseUrl = '/api/v1/wallets';

  // Wallet management
  async getWallets() {
    return apiClient.get<Wallet[]>(this.baseUrl);
  }

  async getWallet(walletId: string) {
    return apiClient.get<Wallet>(`${this.baseUrl}/${walletId}`);
  }

  async createWallet(data: CreateWalletRequest) {
    return apiClient.post<Wallet>(this.baseUrl, data);
  }

  async updateWallet(walletId: string, data: Partial<Wallet>) {
    return apiClient.put<Wallet>(`${this.baseUrl}/${walletId}`, data);
  }

  async setPrimaryWallet(walletId: string) {
    return apiClient.put<Wallet>(`${this.baseUrl}/${walletId}/set-primary`);
  }

  async closeWallet(walletId: string, reason?: string) {
    return apiClient.post<void>(`${this.baseUrl}/${walletId}/close`, { reason });
  }

  // Balance operations
  async getWalletBalance(walletId: string) {
    return apiClient.get<WalletBalance>(`${this.baseUrl}/${walletId}/balance`);
  }

  async getBalanceHistory(walletId: string, params?: { startDate?: string; endDate?: string }) {
    return apiClient.get<WalletBalance[]>(`${this.baseUrl}/${walletId}/balance/history`, { params });
  }

  // Transaction operations
  async getTransactions(walletId: string, params?: any) {
    return apiClient.get<Transaction[]>(`${this.baseUrl}/${walletId}/transactions`, { params });
  }

  async addMoney(walletId: string, data: AddMoneyRequest) {
    return apiClient.post<Transaction>(`${this.baseUrl}/${walletId}/add-money`, data);
  }

  async withdrawMoney(walletId: string, data: WithdrawMoneyRequest) {
    return apiClient.post<Transaction>(`${this.baseUrl}/${walletId}/withdraw`, data);
  }

  async transferMoney(data: TransferMoneyRequest) {
    return apiClient.post<Transaction>(`${this.baseUrl}/transfer`, data);
  }

  // Limits
  async getWalletLimits(walletId: string) {
    return apiClient.get<WalletLimit>(`${this.baseUrl}/${walletId}/limits`);
  }

  async updateWalletLimits(walletId: string, limits: Partial<WalletLimit>) {
    return apiClient.put<WalletLimit>(`${this.baseUrl}/${walletId}/limits`, limits);
  }

  // Payment methods
  async getPaymentMethods() {
    return apiClient.get<PaymentMethod[]>('/api/v1/payment-methods');
  }

  async addPaymentMethod(data: any) {
    return apiClient.post<PaymentMethod>('/api/v1/payment-methods', data);
  }

  async removePaymentMethod(methodId: string) {
    return apiClient.delete(`/api/v1/payment-methods/${methodId}`);
  }

  async setDefaultPaymentMethod(methodId: string) {
    return apiClient.put<PaymentMethod>(`/api/v1/payment-methods/${methodId}/set-default`);
  }

  // Bank accounts
  async getBankAccounts() {
    return apiClient.get<BankAccount[]>('/api/v1/bank-accounts');
  }

  async addBankAccount(data: any) {
    return apiClient.post<BankAccount>('/api/v1/bank-accounts', data);
  }

  async verifyBankAccount(accountId: string, data: any) {
    return apiClient.post<BankAccount>(`/api/v1/bank-accounts/${accountId}/verify`, data);
  }

  async removeBankAccount(accountId: string) {
    return apiClient.delete(`/api/v1/bank-accounts/${accountId}`);
  }

  async setDefaultBankAccount(accountId: string) {
    return apiClient.put<BankAccount>(`/api/v1/bank-accounts/${accountId}/set-default`);
  }

  // Search users for transfers
  async searchUsers(query: string) {
    return apiClient.get('/api/v1/users/search', {
      params: { q: query },
    });
  }

  // QR code operations
  async generateQRCode(walletId: string) {
    return apiClient.get(`${this.baseUrl}/${walletId}/qr-code`, {
      responseType: 'blob',
    });
  }

  async scanQRCode(data: { qrData: string }) {
    return apiClient.post(`${this.baseUrl}/scan-qr`, data);
  }

  // Export operations
  async exportTransactions(walletId: string, format: 'csv' | 'pdf', params?: any) {
    return apiClient.get(`${this.baseUrl}/${walletId}/transactions/export`, {
      params: { format, ...params },
      responseType: 'blob',
    });
  }

  async exportStatement(walletId: string, params: { startDate: string; endDate: string }) {
    return apiClient.get(`${this.baseUrl}/${walletId}/statement`, {
      params,
      responseType: 'blob',
    });
  }

  // Dashboard specific methods
  async getMultiCurrencyBalances() {
    return apiClient.get<EnhancedWalletBalance[]>(`${this.baseUrl}/balances`);
  }

  async getSecuritySettings() {
    return apiClient.get<SecuritySettings>('/api/v1/security/settings');
  }

  async updateSecuritySettings(settings: Partial<SecuritySettings>) {
    return apiClient.put<SecuritySettings>('/api/v1/security/settings', settings);
  }

  async getGoals() {
    return apiClient.get<Goal[]>('/api/v1/goals');
  }

  async createGoal(goal: Partial<Goal>) {
    return apiClient.post<Goal>('/api/v1/goals', goal);
  }

  async updateGoal(goalId: string, goal: Partial<Goal>) {
    return apiClient.put<Goal>(`/api/v1/goals/${goalId}`, goal);
  }

  async deleteGoal(goalId: string) {
    return apiClient.delete(`/api/v1/goals/${goalId}`);
  }

  async getContacts() {
    return apiClient.get<Contact[]>('/api/v1/contacts');
  }

  async addContact(contact: Partial<Contact>) {
    return apiClient.post<Contact>('/api/v1/contacts', contact);
  }

  async updateContact(contactId: string, contact: Partial<Contact>) {
    return apiClient.put<Contact>(`/api/v1/contacts/${contactId}`, contact);
  }

  async deleteContact(contactId: string) {
    return apiClient.delete(`/api/v1/contacts/${contactId}`);
  }

  async getAllTransactions(params?: any) {
    return apiClient.get<Transaction[]>(`${this.baseUrl}/transactions/all`, { params });
  }
}

export const walletService = new WalletService();