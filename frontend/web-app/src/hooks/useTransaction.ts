import { useState, useCallback } from 'react';
import { useAppDispatch, useAppSelector } from './redux';
import { transactionService } from '../services/transactionService';
import { 
  setTransactions, 
  addTransaction, 
  updateTransaction, 
  setTransactionFilters,
  setTransactionsPagination 
} from '../store/slices/transactionSlice';
import { Transaction, TransactionFilters, CreateTransactionRequest } from '../types/transaction';
import { toast } from 'react-hot-toast';

export const useTransaction = () => {
  const dispatch = useAppDispatch();
  const { 
    transactions, 
    currentTransaction, 
    loading, 
    error, 
    filters, 
    pagination 
  } = useAppSelector(state => state.transactions);
  
  const [localLoading, setLocalLoading] = useState(false);

  const fetchTransactions = useCallback(async (page = 1, limit = 20) => {
    try {
      setLocalLoading(true);
      const result = await transactionService.getTransactions(filters, page, limit);
      dispatch(setTransactions(result.transactions));
      dispatch(setTransactionsPagination({
        currentPage: page,
        totalPages: result.totalPages,
        totalItems: result.totalItems,
        itemsPerPage: limit
      }));
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch transactions');
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch, filters]);

  const createTransaction = useCallback(async (transactionData: CreateTransactionRequest) => {
    try {
      setLocalLoading(true);
      const newTransaction = await transactionService.createTransaction(transactionData);
      dispatch(addTransaction(newTransaction));
      toast.success('Transaction created successfully');
      return newTransaction;
    } catch (error: any) {
      toast.error(error.message || 'Failed to create transaction');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const getTransactionById = useCallback(async (id: string) => {
    try {
      setLocalLoading(true);
      const transaction = await transactionService.getTransactionById(id);
      return transaction;
    } catch (error: any) {
      toast.error(error.message || 'Failed to fetch transaction');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, []);

  const cancelTransaction = useCallback(async (id: string) => {
    try {
      setLocalLoading(true);
      const updatedTransaction = await transactionService.cancelTransaction(id);
      dispatch(updateTransaction(updatedTransaction));
      toast.success('Transaction cancelled successfully');
      return updatedTransaction;
    } catch (error: any) {
      toast.error(error.message || 'Failed to cancel transaction');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const refundTransaction = useCallback(async (id: string, reason?: string) => {
    try {
      setLocalLoading(true);
      const refundedTransaction = await transactionService.refundTransaction(id, reason);
      dispatch(updateTransaction(refundedTransaction));
      toast.success('Transaction refunded successfully');
      return refundedTransaction;
    } catch (error: any) {
      toast.error(error.message || 'Failed to refund transaction');
      throw error;
    } finally {
      setLocalLoading(false);
    }
  }, [dispatch]);

  const updateFilters = useCallback((newFilters: Partial<TransactionFilters>) => {
    dispatch(setTransactionFilters(newFilters));
  }, [dispatch]);

  const exportTransactions = useCallback(async (format: 'csv' | 'pdf' = 'csv') => {
    try {
      setLocalLoading(true);
      const blob = await transactionService.exportTransactions(filters, format);
      
      // Create download link
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `transactions-${new Date().toISOString().split('T')[0]}.${format}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      
      toast.success('Transactions exported successfully');
    } catch (error: any) {
      toast.error(error.message || 'Failed to export transactions');
    } finally {
      setLocalLoading(false);
    }
  }, [filters]);

  return {
    // State
    transactions,
    currentTransaction,
    loading: loading || localLoading,
    error,
    filters,
    pagination,
    
    // Actions
    fetchTransactions,
    createTransaction,
    getTransactionById,
    cancelTransaction,
    refundTransaction,
    updateFilters,
    exportTransactions
  };
};