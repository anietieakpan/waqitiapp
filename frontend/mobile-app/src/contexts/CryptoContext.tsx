/**
 * Crypto Context
 * Provides cryptocurrency wallet and transaction functionality throughout the app
 */
import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { Alert } from 'react-native';
import { useAuth } from './AuthContext';
import { cryptoService } from '../services/cryptoService';
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
} from '../types/crypto';

interface CryptoContextType {
  // Wallet management
  wallets: CryptoWallet[];
  isLoading: boolean;
  error: string | null;
  totalPortfolioValue: number;
  portfolioChange24h: number;
  
  // Wallet operations
  createWallet: (currency: CryptoCurrency) => Promise<CryptoWallet>;
  refreshWallets: () => Promise<void>;
  getWalletDetails: (walletId: string) => Promise<CryptoWallet>;
  generateReceiveAddress: (request: GenerateReceiveAddressRequest) => Promise<CryptoReceiveResponse>;
  
  // Trading operations
  buyCryptocurrency: (request: CryptoBuyRequest) => Promise<CryptoTransactionResponse>;
  sellCryptocurrency: (request: CryptoSellRequest) => Promise<CryptoTransactionResponse>;
  sendCryptocurrency: (request: CryptoSendRequest) => Promise<CryptoTransactionResponse>;
  convertCryptocurrency: (request: CryptoConvertRequest) => Promise<CryptoConversionResponse>;
  
  // Transaction management
  transactions: CryptoTransaction[];
  getTransactions: (currency?: CryptoCurrency, type?: string) => Promise<void>;
  getTransactionDetails: (transactionId: string) => Promise<CryptoTransaction>;
  
  // Pricing and fees
  getCryptoPrice: (currency: CryptoCurrency) => Promise<number>;
  estimateNetworkFee: (request: any) => Promise<number>;
  estimateTradingFee: (amount: number) => Promise<number>;
  
  // Utilities
  validateAddress: (address: string, currency: CryptoCurrency) => Promise<boolean>;
  getNetworkStatus: (currency: CryptoCurrency) => Promise<any>;
}

const CryptoContext = createContext<CryptoContextType | undefined>(undefined);

interface CryptoProviderProps {
  children: ReactNode;
}

export const CryptoProvider: React.FC<CryptoProviderProps> = ({ children }) => {
  const { user, token } = useAuth();
  const [wallets, setWallets] = useState<CryptoWallet[]>([]);
  const [transactions, setTransactions] = useState<CryptoTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Initialize crypto data when user logs in
  useEffect(() => {
    if (user && token) {
      initializeCryptoData();
    }
  }, [user, token]);

  const initializeCryptoData = async () => {
    try {
      await refreshWallets();
    } catch (error) {
      console.error('Failed to initialize crypto data:', error);
    }
  };

  const createWallet = async (currency: CryptoCurrency): Promise<CryptoWallet> => {
    if (!user || !token) throw new Error('User not authenticated');

    setIsLoading(true);
    setError(null);

    try {
      const request: CreateCryptoWalletRequest = { currency };
      const wallet = await cryptoService.createWallet(request, token);
      
      // Add to local wallets list
      setWallets(prev => [...prev, wallet]);
      
      return wallet;
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to create wallet';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const refreshWallets = async (): Promise<void> => {
    if (!user || !token) return;

    setIsLoading(true);
    setError(null);

    try {
      const walletsData = await cryptoService.getWallets(token);
      setWallets(walletsData);
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load wallets';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const getWalletDetails = async (walletId: string): Promise<CryptoWallet> => {
    if (!user || !token) throw new Error('User not authenticated');

    try {
      return await cryptoService.getWalletDetails(walletId, token);
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load wallet details';
      throw new Error(errorMessage);
    }
  };

  const generateReceiveAddress = async (
    request: GenerateReceiveAddressRequest
  ): Promise<CryptoReceiveResponse> => {
    if (!user || !token) throw new Error('User not authenticated');

    try {
      return await cryptoService.generateReceiveAddress(request, token);
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to generate address';
      throw new Error(errorMessage);
    }
  };

  const buyCryptocurrency = async (
    request: CryptoBuyRequest
  ): Promise<CryptoTransactionResponse> => {
    if (!user || !token) throw new Error('User not authenticated');

    setIsLoading(true);
    try {
      const result = await cryptoService.buyCryptocurrency(request, token);
      
      // Refresh wallets to update balances
      await refreshWallets();
      
      return result;
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to buy cryptocurrency';
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const sellCryptocurrency = async (
    request: CryptoSellRequest
  ): Promise<CryptoTransactionResponse> => {
    if (!user || !token) throw new Error('User not authenticated');

    setIsLoading(true);
    try {
      const result = await cryptoService.sellCryptocurrency(request, token);
      
      // Refresh wallets to update balances
      await refreshWallets();
      
      return result;
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to sell cryptocurrency';
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const sendCryptocurrency = async (
    request: CryptoSendRequest
  ): Promise<CryptoTransactionResponse> => {
    if (!user || !token) throw new Error('User not authenticated');

    setIsLoading(true);
    try {
      const result = await cryptoService.sendCryptocurrency(request, token);
      
      // Refresh wallets to update balances
      await refreshWallets();
      
      return result;
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to send cryptocurrency';
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const convertCryptocurrency = async (
    request: CryptoConvertRequest
  ): Promise<CryptoConversionResponse> => {
    if (!user || !token) throw new Error('User not authenticated');

    setIsLoading(true);
    try {
      const result = await cryptoService.convertCryptocurrency(request, token);
      
      // Refresh wallets to update balances
      await refreshWallets();
      
      return result;
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to convert cryptocurrency';
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const getTransactions = async (
    currency?: CryptoCurrency,
    type?: string
  ): Promise<void> => {
    if (!user || !token) return;

    try {
      const transactionsData = await cryptoService.getTransactions(
        { currency, type },
        token
      );
      setTransactions(transactionsData.content || []);
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load transactions';
      throw new Error(errorMessage);
    }
  };

  const getTransactionDetails = async (transactionId: string): Promise<CryptoTransaction> => {
    if (!user || !token) throw new Error('User not authenticated');

    try {
      return await cryptoService.getTransactionDetails(transactionId, token);
    } catch (error: any) {
      const errorMessage = error.response?.data?.message || 'Failed to load transaction details';
      throw new Error(errorMessage);
    }
  };

  const getCryptoPrice = async (currency: CryptoCurrency): Promise<number> => {
    try {
      return await cryptoService.getCryptoPrice(currency);
    } catch (error) {
      console.error('Failed to get crypto price:', error);
      // Return fallback prices
      const fallbackPrices = {
        [CryptoCurrency.BITCOIN]: 45000,
        [CryptoCurrency.ETHEREUM]: 3000,
        [CryptoCurrency.LITECOIN]: 150,
        [CryptoCurrency.USDC]: 1,
        [CryptoCurrency.USDT]: 1,
      };
      return fallbackPrices[currency] || 0;
    }
  };

  const estimateNetworkFee = async (request: any): Promise<number> => {
    try {
      return await cryptoService.estimateNetworkFee(request);
    } catch (error) {
      console.error('Failed to estimate network fee:', error);
      // Return fallback fees
      const fallbackFees = {
        [CryptoCurrency.BITCOIN]: 0.0002,
        [CryptoCurrency.ETHEREUM]: 0.005,
        [CryptoCurrency.LITECOIN]: 0.001,
        [CryptoCurrency.USDC]: 0.005,
        [CryptoCurrency.USDT]: 0.005,
      };
      return fallbackFees[request.currency] || 0.001;
    }
  };

  const estimateTradingFee = async (amount: number): Promise<number> => {
    // 1.5% trading fee
    return amount * 0.015;
  };

  const validateAddress = async (
    address: string,
    currency: CryptoCurrency
  ): Promise<boolean> => {
    try {
      return await cryptoService.validateAddress(address, currency);
    } catch (error) {
      console.error('Address validation failed:', error);
      return false;
    }
  };

  const getNetworkStatus = async (currency: CryptoCurrency): Promise<any> => {
    try {
      return await cryptoService.getNetworkStatus(currency);
    } catch (error) {
      console.error('Failed to get network status:', error);
      return { isHealthy: false, error: 'Network status unavailable' };
    }
  };

  // Calculate portfolio values
  const totalPortfolioValue = wallets.reduce(
    (total, wallet) => total + wallet.usdValue,
    0
  );

  // Mock 24h change calculation
  const portfolioChange24h = Math.random() * 10 - 5; // Placeholder

  const value: CryptoContextType = {
    // Wallet management
    wallets,
    isLoading,
    error,
    totalPortfolioValue,
    portfolioChange24h,
    
    // Wallet operations
    createWallet,
    refreshWallets,
    getWalletDetails,
    generateReceiveAddress,
    
    // Trading operations
    buyCryptocurrency,
    sellCryptocurrency,
    sendCryptocurrency,
    convertCryptocurrency,
    
    // Transaction management
    transactions,
    getTransactions,
    getTransactionDetails,
    
    // Pricing and fees
    getCryptoPrice,
    estimateNetworkFee,
    estimateTradingFee,
    
    // Utilities
    validateAddress,
    getNetworkStatus,
  };

  return <CryptoContext.Provider value={value}>{children}</CryptoContext.Provider>;
};

export const useCrypto = (): CryptoContextType => {
  const context = useContext(CryptoContext);
  if (context === undefined) {
    throw new Error('useCrypto must be used within a CryptoProvider');
  }
  return context;
};