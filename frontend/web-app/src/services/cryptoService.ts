import axios from 'axios';
import {
  CryptoWallet,
  CryptoPrice,
  CryptoTransaction,
  BuyCryptoRequest,
  SendCryptoRequest,
  SwapCryptoRequest,
  CryptoCurrency,
} from '@/types/crypto';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8443';

export const cryptoService = {
  // Get all crypto wallets
  getWallets: async (): Promise<CryptoWallet[]> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/crypto/wallets`);
    return response.data.data || response.data;
  },

  // Get single wallet
  getWallet: async (currency: CryptoCurrency): Promise<CryptoWallet> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/crypto/wallets/${currency}`);
    return response.data.data || response.data;
  },

  // Create new wallet
  createWallet: async (currency: CryptoCurrency, network?: string): Promise<CryptoWallet> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/crypto/wallets`, {
      currency,
      network,
    });
    return response.data.data || response.data;
  },

  // Get crypto prices
  getPrices: async (currencies?: CryptoCurrency[]): Promise<Record<string, CryptoPrice>> => {
    const params = currencies ? { currencies: currencies.join(',') } : {};
    const response = await axios.get(`${API_BASE_URL}/api/v1/crypto/prices`, { params });
    return response.data.data || response.data;
  },

  // Buy crypto
  buyCrypto: async (request: BuyCryptoRequest): Promise<CryptoTransaction> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/crypto/buy`, request);
    return response.data.data || response.data;
  },

  // Send crypto
  sendCrypto: async (request: SendCryptoRequest): Promise<CryptoTransaction> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/crypto/send`, request);
    return response.data.data || response.data;
  },

  // Swap crypto
  swapCrypto: async (request: SwapCryptoRequest): Promise<CryptoTransaction> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/crypto/swap`, request);
    return response.data.data || response.data;
  },

  // Get transactions
  getTransactions: async (currency?: CryptoCurrency, limit = 50): Promise<CryptoTransaction[]> => {
    const params = { currency, limit };
    const response = await axios.get(`${API_BASE_URL}/api/v1/crypto/transactions`, { params });
    return response.data.data || response.data;
  },

  // Get transaction by hash
  getTransaction: async (txHash: string): Promise<CryptoTransaction> => {
    const response = await axios.get(`${API_BASE_URL}/api/v1/crypto/transactions/${txHash}`);
    return response.data.data || response.data;
  },

  // Estimate gas fee
  estimateGasFee: async (currency: CryptoCurrency, amount: number): Promise<number> => {
    const response = await axios.post(`${API_BASE_URL}/api/v1/crypto/estimate-fee`, {
      currency,
      amount,
    });
    return response.data.data || response.data;
  },
};
