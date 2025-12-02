/**
 * Crypto Service
 * Handles all cryptocurrency-related API communications
 */
import { apiClient } from './apiClient';
import {
  CryptoWallet,
  CryptoCurrency,
  CryptoTransaction,
  CryptoBuyRequest,
  CryptoSellRequest,
  CryptoSendRequest,
  CryptoConvertRequest,
  CreateCryptoWalletRequest,
  GenerateReceiveAddressRequest,
  CryptoReceiveResponse,
  CryptoTransactionResponse,
  CryptoConversionResponse,
  CryptoWalletDetailsResponse,
  CryptoTransactionDetailsResponse,
} from '../types/crypto';

export interface CryptoTransactionFilters {
  currency?: CryptoCurrency;
  type?: string;
  page?: number;
  size?: number;
}

export interface CryptoTransactionsResponse {
  content: CryptoTransaction[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

class CryptoService {
  private readonly baseUrl = '/crypto';

  /**
   * Wallet Management
   */
  async createWallet(
    request: CreateCryptoWalletRequest,
    token: string
  ): Promise<CryptoWallet> {
    const response = await apiClient.post(
      `${this.baseUrl}/wallets`,
      request,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async getWallets(token: string): Promise<CryptoWallet[]> {
    const response = await apiClient.get(`${this.baseUrl}/wallets`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.data;
  }

  async getWalletDetails(
    walletId: string,
    token: string
  ): Promise<CryptoWalletDetailsResponse> {
    const response = await apiClient.get(
      `${this.baseUrl}/wallets/${walletId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async generateReceiveAddress(
    request: GenerateReceiveAddressRequest,
    token: string
  ): Promise<CryptoReceiveResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/wallets/receive-address`,
      request,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  /**
   * Trading Operations
   */
  async buyCryptocurrency(
    request: CryptoBuyRequest,
    token: string
  ): Promise<CryptoTransactionResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/transactions/buy`,
      request,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async sellCryptocurrency(
    request: CryptoSellRequest,
    token: string
  ): Promise<CryptoTransactionResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/transactions/sell`,
      request,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async sendCryptocurrency(
    request: CryptoSendRequest,
    token: string
  ): Promise<CryptoTransactionResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/transactions/send`,
      request,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async convertCryptocurrency(
    request: CryptoConvertRequest,
    token: string
  ): Promise<CryptoConversionResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/transactions/convert`,
      request,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  /**
   * Transaction Management
   */
  async getTransactions(
    filters: CryptoTransactionFilters,
    token: string
  ): Promise<CryptoTransactionsResponse> {
    const params = new URLSearchParams();
    
    if (filters.currency) params.append('currency', filters.currency);
    if (filters.type) params.append('type', filters.type);
    if (filters.page !== undefined) params.append('page', filters.page.toString());
    if (filters.size !== undefined) params.append('size', filters.size.toString());

    const response = await apiClient.get(
      `${this.baseUrl}/transactions?${params.toString()}`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async getTransactionDetails(
    transactionId: string,
    token: string
  ): Promise<CryptoTransactionDetailsResponse> {
    const response = await apiClient.get(
      `${this.baseUrl}/transactions/${transactionId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  /**
   * Fee Estimation
   */
  async estimateNetworkFee(request: {
    currency: CryptoCurrency;
    toAddress: string;
    amount: number;
    feeSpeed?: string;
  }): Promise<number> {
    const response = await apiClient.post(
      `${this.baseUrl}/transactions/estimate-fee`,
      request
    );
    return response.data.networkFee;
  }

  /**
   * Price Data
   */
  async getCryptoPrice(currency: CryptoCurrency): Promise<number> {
    const response = await apiClient.get(`${this.baseUrl}/prices/${currency}`);
    return response.data.price;
  }

  async getCryptoPrices(currencies: CryptoCurrency[]): Promise<Record<string, number>> {
    const response = await apiClient.post(`${this.baseUrl}/prices/batch`, {
      currencies,
    });
    return response.data;
  }

  async getPriceHistory(
    currency: CryptoCurrency,
    timeframe: string
  ): Promise<Array<{ timestamp: string; price: number }>> {
    const response = await apiClient.get(
      `${this.baseUrl}/prices/${currency}/history?timeframe=${timeframe}`
    );
    return response.data;
  }

  /**
   * Network Information
   */
  async getNetworkStatus(currency: CryptoCurrency): Promise<{
    isHealthy: boolean;
    currentBlockHeight: number;
    networkFees: {
      slow: number;
      standard: number;
      fast: number;
    };
    lastChecked: string;
  }> {
    const response = await apiClient.get(
      `${this.baseUrl}/network/${currency}/status`
    );
    return response.data;
  }

  async validateAddress(
    address: string,
    currency: CryptoCurrency
  ): Promise<boolean> {
    try {
      const response = await apiClient.post(
        `${this.baseUrl}/addresses/validate`,
        {
          address,
          currency,
        }
      );
      return response.data.isValid;
    } catch (error) {
      console.error('Address validation error:', error);
      return false;
    }
  }

  /**
   * Portfolio Analytics
   */
  async getPortfolioSummary(token: string): Promise<{
    totalValue: number;
    change24h: number;
    changePercent24h: number;
    allocation: Array<{
      currency: CryptoCurrency;
      percentage: number;
      value: number;
    }>;
  }> {
    const response = await apiClient.get(`${this.baseUrl}/portfolio/summary`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.data;
  }

  async getPortfolioHistory(
    token: string,
    timeframe: string
  ): Promise<Array<{ timestamp: string; value: number }>> {
    const response = await apiClient.get(
      `${this.baseUrl}/portfolio/history?timeframe=${timeframe}`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  /**
   * Market Data
   */
  async getMarketData(): Promise<Array<{
    currency: CryptoCurrency;
    name: string;
    symbol: string;
    price: number;
    change24h: number;
    changePercent24h: number;
    marketCap: number;
    volume24h: number;
  }>> {
    const response = await apiClient.get(`${this.baseUrl}/market/data`);
    return response.data;
  }

  async getTrendingCryptocurrencies(): Promise<Array<{
    currency: CryptoCurrency;
    name: string;
    symbol: string;
    price: number;
    changePercent24h: number;
  }>> {
    const response = await apiClient.get(`${this.baseUrl}/market/trending`);
    return response.data;
  }

  /**
   * Security Operations
   */
  async freezeWallet(walletId: string, token: string): Promise<void> {
    await apiClient.post(
      `${this.baseUrl}/wallets/${walletId}/freeze`,
      {},
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
  }

  async unfreezeWallet(walletId: string, token: string): Promise<void> {
    await apiClient.post(
      `${this.baseUrl}/wallets/${walletId}/unfreeze`,
      {},
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
  }

  async getSecurityAlerts(token: string): Promise<Array<{
    id: string;
    type: string;
    severity: string;
    message: string;
    timestamp: string;
    acknowledged: boolean;
  }>> {
    const response = await apiClient.get(`${this.baseUrl}/security/alerts`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.data;
  }

  /**
   * Staking Operations (for supported currencies)
   */
  async getStakingInfo(
    currency: CryptoCurrency,
    token: string
  ): Promise<{
    isSupported: boolean;
    currentAPY: number;
    minimumStake: number;
    lockupPeriod: number;
    stakedAmount: number;
    rewards: number;
  }> {
    const response = await apiClient.get(
      `${this.baseUrl}/staking/${currency}/info`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async stakeTokens(
    currency: CryptoCurrency,
    amount: number,
    token: string
  ): Promise<CryptoTransactionResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/staking/${currency}/stake`,
      { amount },
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }

  async unstakeTokens(
    currency: CryptoCurrency,
    amount: number,
    token: string
  ): Promise<CryptoTransactionResponse> {
    const response = await apiClient.post(
      `${this.baseUrl}/staking/${currency}/unstake`,
      { amount },
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    return response.data;
  }
}

export const cryptoService = new CryptoService();