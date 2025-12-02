/**
 * Account Service
 * Handles all account-related operations including fetching account details,
 * managing linked accounts, and account preferences
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { Logger } from '../../../shared/services/src/LoggingService';
import { BankAccount, LinkedAccount, Money } from '../../../shared/types/src';

export interface AccountDetails {
  id: string;
  name: string;
  number: string;
  maskedNumber: string;
  type: 'checking' | 'savings' | 'credit' | 'investment';
  balance: Money;
  availableBalance: Money;
  institution: {
    id: string;
    name: string;
    logo?: string;
  };
  routingNumber: string;
  isDefault: boolean;
  isVerified: boolean;
  capabilities: string[];
  limits: {
    daily: Money;
    monthly: Money;
    transaction: Money;
  };
  metadata: {
    lastSynced: Date;
    syncStatus: 'success' | 'pending' | 'failed';
    lastActivity: Date;
  };
}

export interface AccountSummary {
  id: string;
  name: string;
  maskedNumber: string;
  type: string;
  balance?: Money;
  institutionName: string;
  isDefault: boolean;
}

class AccountServiceClass {
  private baseUrl: string;
  private cachedAccounts: Map<string, AccountDetails> = new Map();
  private cacheExpiry: number = 5 * 60 * 1000; // 5 minutes
  private lastCacheTime: Map<string, number> = new Map();

  constructor() {
    this.baseUrl = process.env.REACT_APP_API_URL || 'https://api.example.com';
  }

  /**
   * Get detailed information about a specific account
   */
  async getAccountDetails(accountId: string): Promise<AccountDetails> {
    try {
      // Check cache first
      if (this.isCacheValid(accountId)) {
        const cached = this.cachedAccounts.get(accountId);
        if (cached) {
          Logger.debug('Returning cached account details', { accountId });
          return cached;
        }
      }

      Logger.info('Fetching account details', { accountId });

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/accounts/${accountId}`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch account details: ${response.statusText}`);
      }

      const data = await response.json();
      
      // Map API response to AccountDetails
      const accountDetails: AccountDetails = {
        id: data.id,
        name: data.accountName || `${data.type} Account`,
        number: data.accountNumber,
        maskedNumber: this.maskAccountNumber(data.accountNumber),
        type: data.accountType,
        balance: {
          amount: data.balance,
          currency: data.currency || 'USD',
          displayAmount: this.formatMoney(data.balance, data.currency || 'USD'),
        },
        availableBalance: {
          amount: data.availableBalance,
          currency: data.currency || 'USD',
          displayAmount: this.formatMoney(data.availableBalance, data.currency || 'USD'),
        },
        institution: {
          id: data.institutionId,
          name: data.institutionName,
          logo: data.institutionLogo,
        },
        routingNumber: data.routingNumber,
        isDefault: data.isDefault || false,
        isVerified: data.isVerified || false,
        capabilities: data.capabilities || ['deposit', 'withdrawal', 'transfer'],
        limits: {
          daily: {
            amount: data.limits?.daily || 10000,
            currency: data.currency || 'USD',
            displayAmount: this.formatMoney(data.limits?.daily || 10000, data.currency || 'USD'),
          },
          monthly: {
            amount: data.limits?.monthly || 50000,
            currency: data.currency || 'USD',
            displayAmount: this.formatMoney(data.limits?.monthly || 50000, data.currency || 'USD'),
          },
          transaction: {
            amount: data.limits?.transaction || 5000,
            currency: data.currency || 'USD',
            displayAmount: this.formatMoney(data.limits?.transaction || 5000, data.currency || 'USD'),
          },
        },
        metadata: {
          lastSynced: new Date(data.lastSynced || Date.now()),
          syncStatus: data.syncStatus || 'success',
          lastActivity: new Date(data.lastActivity || Date.now()),
        },
      };

      // Update cache
      this.cachedAccounts.set(accountId, accountDetails);
      this.lastCacheTime.set(accountId, Date.now());

      Logger.info('Account details fetched successfully', { accountId });
      return accountDetails;

    } catch (error) {
      Logger.error('Failed to fetch account details', error, { accountId });
      
      // Try to return cached data if available
      const cached = this.cachedAccounts.get(accountId);
      if (cached) {
        Logger.warn('Returning stale cached data due to error', { accountId });
        return cached;
      }

      // Return default data as fallback
      return this.getDefaultAccountDetails(accountId);
    }
  }

  /**
   * Get a summary of all user's accounts
   */
  async getAllAccounts(): Promise<AccountSummary[]> {
    try {
      Logger.info('Fetching all accounts');

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/accounts`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch accounts: ${response.statusText}`);
      }

      const data = await response.json();
      
      const accounts: AccountSummary[] = data.accounts.map((account: any) => ({
        id: account.id,
        name: account.accountName || `${account.type} Account`,
        maskedNumber: this.maskAccountNumber(account.accountNumber),
        type: account.accountType,
        balance: account.balance ? {
          amount: account.balance,
          currency: account.currency || 'USD',
          displayAmount: this.formatMoney(account.balance, account.currency || 'USD'),
        } : undefined,
        institutionName: account.institutionName,
        isDefault: account.isDefault || false,
      }));

      Logger.info('Accounts fetched successfully', { count: accounts.length });
      return accounts;

    } catch (error) {
      Logger.error('Failed to fetch accounts', error);
      return [];
    }
  }

  /**
   * Get account by deposit ID
   */
  async getAccountByDepositId(depositId: string): Promise<AccountDetails | null> {
    try {
      Logger.info('Fetching account for deposit', { depositId });

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/deposits/${depositId}/account`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        if (response.status === 404) {
          Logger.warn('No account found for deposit', { depositId });
          return null;
        }
        throw new Error(`Failed to fetch account: ${response.statusText}`);
      }

      const data = await response.json();
      return await this.getAccountDetails(data.accountId);

    } catch (error) {
      Logger.error('Failed to fetch account for deposit', error, { depositId });
      return null;
    }
  }

  /**
   * Link a new bank account
   */
  async linkAccount(accountData: {
    routingNumber: string;
    accountNumber: string;
    accountType: 'checking' | 'savings';
    accountName?: string;
  }): Promise<AccountDetails> {
    try {
      Logger.info('Linking new account', { type: accountData.accountType });

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/accounts/link`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(accountData),
      });

      if (!response.ok) {
        throw new Error(`Failed to link account: ${response.statusText}`);
      }

      const data = await response.json();
      
      Logger.info('Account linked successfully', { accountId: data.accountId });
      return await this.getAccountDetails(data.accountId);

    } catch (error) {
      Logger.error('Failed to link account', error);
      throw error;
    }
  }

  /**
   * Unlink an account
   */
  async unlinkAccount(accountId: string): Promise<boolean> {
    try {
      Logger.info('Unlinking account', { accountId });

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/accounts/${accountId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to unlink account: ${response.statusText}`);
      }

      // Clear from cache
      this.cachedAccounts.delete(accountId);
      this.lastCacheTime.delete(accountId);

      Logger.info('Account unlinked successfully', { accountId });
      return true;

    } catch (error) {
      Logger.error('Failed to unlink account', error, { accountId });
      return false;
    }
  }

  /**
   * Set an account as default
   */
  async setDefaultAccount(accountId: string): Promise<boolean> {
    try {
      Logger.info('Setting default account', { accountId });

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/accounts/${accountId}/default`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error(`Failed to set default account: ${response.statusText}`);
      }

      // Clear cache to force refresh
      this.clearCache();

      Logger.info('Default account set successfully', { accountId });
      return true;

    } catch (error) {
      Logger.error('Failed to set default account', error, { accountId });
      return false;
    }
  }

  /**
   * Verify an account with micro-deposits
   */
  async verifyAccount(accountId: string, amounts: number[]): Promise<boolean> {
    try {
      Logger.info('Verifying account with micro-deposits', { accountId });

      const token = await this.getAuthToken();
      const response = await fetch(`${this.baseUrl}/api/v1/accounts/${accountId}/verify`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ amounts }),
      });

      if (!response.ok) {
        throw new Error(`Failed to verify account: ${response.statusText}`);
      }

      // Clear cache to force refresh
      this.cachedAccounts.delete(accountId);
      this.lastCacheTime.delete(accountId);

      Logger.info('Account verified successfully', { accountId });
      return true;

    } catch (error) {
      Logger.error('Failed to verify account', error, { accountId });
      return false;
    }
  }

  // Private helper methods

  private async getAuthToken(): Promise<string> {
    const token = await AsyncStorage.getItem('authToken');
    if (!token) {
      throw new Error('No authentication token available');
    }
    return token;
  }

  private maskAccountNumber(accountNumber: string): string {
    if (!accountNumber || accountNumber.length < 4) {
      return '****';
    }
    return `****${accountNumber.slice(-4)}`;
  }

  private formatMoney(amount: number, currency: string): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  private isCacheValid(accountId: string): boolean {
    const cacheTime = this.lastCacheTime.get(accountId);
    if (!cacheTime) return false;
    return (Date.now() - cacheTime) < this.cacheExpiry;
  }

  private clearCache(): void {
    this.cachedAccounts.clear();
    this.lastCacheTime.clear();
  }

  private getDefaultAccountDetails(accountId: string): AccountDetails {
    return {
      id: accountId,
      name: 'Account',
      number: '0000',
      maskedNumber: '****0000',
      type: 'checking',
      balance: {
        amount: 0,
        currency: 'USD',
        displayAmount: '$0.00',
      },
      availableBalance: {
        amount: 0,
        currency: 'USD',
        displayAmount: '$0.00',
      },
      institution: {
        id: 'unknown',
        name: 'Bank',
      },
      routingNumber: '000000000',
      isDefault: false,
      isVerified: false,
      capabilities: ['deposit', 'withdrawal'],
      limits: {
        daily: { amount: 10000, currency: 'USD', displayAmount: '$10,000.00' },
        monthly: { amount: 50000, currency: 'USD', displayAmount: '$50,000.00' },
        transaction: { amount: 5000, currency: 'USD', displayAmount: '$5,000.00' },
      },
      metadata: {
        lastSynced: new Date(),
        syncStatus: 'pending',
        lastActivity: new Date(),
      },
    };
  }
}

// Export singleton instance
export const AccountService = new AccountServiceClass();

// Export convenience methods
export const getAccountDetails = (accountId: string) => 
  AccountService.getAccountDetails(accountId);

export const getAllAccounts = () => 
  AccountService.getAllAccounts();

export const getAccountByDepositId = (depositId: string) => 
  AccountService.getAccountByDepositId(depositId);

export const linkAccount = (accountData: Parameters<typeof AccountService.linkAccount>[0]) => 
  AccountService.linkAccount(accountData);

export const unlinkAccount = (accountId: string) => 
  AccountService.unlinkAccount(accountId);

export const setDefaultAccount = (accountId: string) => 
  AccountService.setDefaultAccount(accountId);

export const verifyAccount = (accountId: string, amounts: number[]) => 
  AccountService.verifyAccount(accountId, amounts);

export default AccountService;