import { useState, useCallback } from 'react';
import { useAppDispatch, useAppSelector } from './redux';
import { walletService } from '../services/walletService';
import { 
  setWallets, 
  setCurrentWallet, 
  updateWalletBalance, 
  addWallet 
} from '../store/slices/walletSlice';
import { Wallet, CreateWalletRequest, WalletTransaction } from '../types/wallet';
import { toast } from 'react-hot-toast';

export const useWallet = () => {
  const dispatch = useAppDispatch();
  const { 
    wallets, 
    currentWallet, 
    loading, 
    error 
  } = useAppSelector(state => state.wallet);
  
  const [localLoading, setLocalLoading] = useState(false);

  const fetchWallets = useCallback(async () => {
    try {
      setLocalLoading(true);
      const walletsData = await walletService.getWallets();
      dispatch(setWallets(walletsData));
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch wallets');
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const createWallet = useCallback(async (walletData: CreateWalletRequest) => {
    try {
      setLocalLoading(true);
      const newWallet = await walletService.createWallet(walletData);
      dispatch(addWallet(newWallet));
      toast.success('Wallet created successfully');
      return newWallet;
    } catch (error: any) {
      toast.error(error.message || 'Failed to create wallet');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const getWalletById = useCallback(async (id: string) => {
    try {
      setLocalLoading(true);
      const wallet = await walletService.getWalletById(id);
      return wallet;
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch wallet');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, []);

  const setActiveWallet = useCallback(async (id: string) => {
    try {
      const wallet = await walletService.getWalletById(id);
      dispatch(setCurrentWallet(wallet));
      toast.success(`Switched to ${wallet.name} wallet`);
    } catch (error: any) {
      toast.error(error.message || 'Failed to switch wallet');
    }
  }, [dispatch]);

  const updateBalance = useCallback(async (walletId: string) => {
    try {
      const balance = await walletService.getWalletBalance(walletId);
      dispatch(updateWalletBalance({ walletId, balance }));
    } catch (error: any) {
      console.error('Failed to update wallet balance:', error);
    }
  }, [dispatch]);

  const getWalletTransactions = useCallback(async (
    walletId: string, 
    page = 1, 
    limit = 20
  ): Promise<{ transactions: WalletTransaction[], totalPages: number }> => {
    try {
      setLocalLoading(true);
      const result = await walletService.getWalletTransactions(walletId, page, limit);
      return result;
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch wallet transactions');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, []);

  const freezeWallet = useCallback(async (id: string, reason?: string) => {
    try {
      setLocalLoading(true);
      await walletService.freezeWallet(id, reason);
      
      // Update wallet status in store
      const updatedWallet = { ...wallets.find(w => w.id === id)!, status: 'FROZEN' as const };
      dispatch(setWallets(wallets.map(w => w.id === id ? updatedWallet : w)));
      
      toast.success('Wallet frozen successfully');
    } catch (error: any) {
      toast.error(error.message || 'Failed to freeze wallet');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch, wallets]);

  const unfreezeWallet = useCallback(async (id: string) => {
    try {
      setLocalLoading(true);
      await walletService.unfreezeWallet(id);
      
      // Update wallet status in store
      const updatedWallet = { ...wallets.find(w => w.id === id)!, status: 'ACTIVE' as const };
      dispatch(setWallets(wallets.map(w => w.id === id ? updatedWallet : w)));
      
      toast.success('Wallet unfrozen successfully');
    } catch (error: any) {
      toast.error(error.message || 'Failed to unfreeze wallet');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch, wallets]);

  const validateWalletPin = useCallback(async (walletId: string, pin: string) => {
    try {
      const isValid = await walletService.validatePin(walletId, pin);
      return isValid;
    } catch (error: any) {
      toast.error(error.message || 'Failed to validate PIN');
      return false;
    }
  }, []);

  const changeWalletPin = useCallback(async (walletId: string, oldPin: string, newPin: string) => {
    try {
      setLocalLoading(true);
      await walletService.changePin(walletId, oldPin, newPin);
      toast.success('Wallet PIN changed successfully');
    } catch (error: any) {
      toast.error(error.message || 'Failed to change wallet PIN');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, []);

  const getWalletQRCode = useCallback(async (walletId: string) => {
    try {
      const qrCodeData = await walletService.getWalletQRCode(walletId);
      return qrCodeData;
    } catch (error: any) {
      toast.error(error.message || 'Failed to generate QR code');
      throw error;
    }
  }, []);

  const getPrimaryWallet = useCallback(() => {
    return wallets.find(wallet => wallet.isPrimary) || wallets[0] || null;
  }, [wallets]);

  const getWalletsByCurrency = useCallback((currency: string) => {
    return wallets.filter(wallet => wallet.currency === currency);
  }, [wallets]);

  const getTotalBalance = useCallback(() => {
    return wallets.reduce((total, wallet) => total + wallet.balance, 0);
  }, [wallets]);

  return {
    // State
    wallets,
    currentWallet,
    loading: loading || localLoading,
    error,
    
    // Actions
    fetchWallets,
    createWallet,
    getWalletById,
    setActiveWallet,
    updateBalance,
    getWalletTransactions,
    freezeWallet,
    unfreezeWallet,
    validateWalletPin,
    changeWalletPin,
    getWalletQRCode,
    
    // Computed values
    getPrimaryWallet,
    getWalletsByCurrency,
    getTotalBalance
  };
};