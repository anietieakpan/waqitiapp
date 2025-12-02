/**
 * Wallet Slice - Production-ready wallet state management
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface WalletAccount {
  id: string;
  accountNumber: string;
  accountType: 'checking' | 'savings' | 'credit' | 'investment';
  balance: number;
  availableBalance: number;
  currency: string;
  isDefault: boolean;
  isActive: boolean;
  lastTransactionDate?: string;
  metadata: {
    bankName?: string;
    accountNickname?: string;
    routingNumber?: string;
    isLinked?: boolean;
  };
}

export interface Transaction {
  id: string;
  type: 'send' | 'receive' | 'deposit' | 'withdrawal' | 'payment' | 'refund';
  amount: number;
  currency: string;
  description: string;
  status: 'pending' | 'completed' | 'failed' | 'cancelled';
  timestamp: string;
  fromAccount?: string;
  toAccount?: string;
  fromUser?: {
    id: string;
    name: string;
    avatar?: string;
  };
  toUser?: {
    id: string;
    name: string;
    avatar?: string;
  };
  metadata: {
    category?: string;
    tags?: string[];
    location?: {
      latitude: number;
      longitude: number;
      address: string;
    };
    merchantInfo?: {
      name: string;
      category: string;
      logo?: string;
    };
  };
}

export interface WalletState {
  accounts: WalletAccount[];
  selectedAccountId: string | null;
  transactions: Transaction[];
  recentTransactions: Transaction[];
  pendingTransactions: Transaction[];
  totalBalance: number;
  availableBalance: number;
  isLoading: boolean;
  isRefreshing: boolean;
  error: string | null;
  lastUpdated: string | null;
  transactionFilters: {
    type: string | null;
    category: string | null;
    dateRange: {
      start: string | null;
      end: string | null;
    };
  };
}

const initialState: WalletState = {
  accounts: [],
  selectedAccountId: null,
  transactions: [],
  recentTransactions: [],
  pendingTransactions: [],
  totalBalance: 0,
  availableBalance: 0,
  isLoading: false,
  isRefreshing: false,
  error: null,
  lastUpdated: null,
  transactionFilters: {
    type: null,
    category: null,
    dateRange: {
      start: null,
      end: null,
    },
  },
};

// Async thunks
export const fetchWalletData = createAsyncThunk(
  'wallet/fetchData',
  async (_, { rejectWithValue }) => {
    try {
      const [accountsResponse, transactionsResponse] = await Promise.all([
        ApiService.get('/wallet/accounts'),
        ApiService.get('/wallet/transactions?limit=50'),
      ]);

      return {
        accounts: accountsResponse.data,
        transactions: transactionsResponse.data,
      };
    } catch (error: any) {
      logError('Failed to fetch wallet data', {
        feature: 'wallet_slice',
        action: 'fetch_wallet_data_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch wallet data');
    }
  }
);

export const refreshWalletData = createAsyncThunk(
  'wallet/refresh',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/wallet/refresh');
      return response.data;
    } catch (error: any) {
      logError('Failed to refresh wallet data', {
        feature: 'wallet_slice',
        action: 'refresh_wallet_data_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to refresh wallet data');
    }
  }
);

export const sendMoney = createAsyncThunk(
  'wallet/sendMoney',
  async (payload: {
    toUserId: string;
    amount: number;
    currency: string;
    description?: string;
    fromAccountId?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/wallet/send', payload);
      
      // Track transaction
      await ApiService.trackEvent('money_sent', {
        amount: payload.amount,
        currency: payload.currency,
        toUserId: payload.toUserId,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to send money', {
        feature: 'wallet_slice',
        action: 'send_money_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to send money');
    }
  }
);

export const requestMoney = createAsyncThunk(
  'wallet/requestMoney',
  async (payload: {
    fromUserId: string;
    amount: number;
    currency: string;
    description?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/wallet/request', payload);
      
      // Track transaction
      await ApiService.trackEvent('money_requested', {
        amount: payload.amount,
        currency: payload.currency,
        fromUserId: payload.fromUserId,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to request money', {
        feature: 'wallet_slice',
        action: 'request_money_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to request money');
    }
  }
);

export const addBankAccount = createAsyncThunk(
  'wallet/addBankAccount',
  async (payload: {
    bankName: string;
    accountNumber: string;
    routingNumber: string;
    accountType: 'checking' | 'savings';
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/wallet/accounts/bank', payload);
      
      // Track event
      await ApiService.trackEvent('bank_account_linked', {
        bankName: payload.bankName,
        accountType: payload.accountType,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to add bank account', {
        feature: 'wallet_slice',
        action: 'add_bank_account_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to add bank account');
    }
  }
);

export const fetchTransactionHistory = createAsyncThunk(
  'wallet/fetchTransactionHistory',
  async (params: {
    limit?: number;
    offset?: number;
    type?: string;
    category?: string;
    startDate?: string;
    endDate?: string;
  }, { rejectWithValue }) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.limit) queryParams.append('limit', params.limit.toString());
      if (params.offset) queryParams.append('offset', params.offset.toString());
      if (params.type) queryParams.append('type', params.type);
      if (params.category) queryParams.append('category', params.category);
      if (params.startDate) queryParams.append('startDate', params.startDate);
      if (params.endDate) queryParams.append('endDate', params.endDate);

      const response = await ApiService.get(`/wallet/transactions?${queryParams.toString()}`);
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch transaction history', {
        feature: 'wallet_slice',
        action: 'fetch_transaction_history_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch transaction history');
    }
  }
);

// Wallet slice
const walletSlice = createSlice({
  name: 'wallet',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setSelectedAccount: (state, action: PayloadAction<string>) => {
      state.selectedAccountId = action.payload;
    },
    setTransactionFilters: (state, action: PayloadAction<Partial<WalletState['transactionFilters']>>) => {
      state.transactionFilters = {
        ...state.transactionFilters,
        ...action.payload,
      };
    },
    clearTransactionFilters: (state) => {
      state.transactionFilters = {
        type: null,
        category: null,
        dateRange: {
          start: null,
          end: null,
        },
      };
    },
    addTransaction: (state, action: PayloadAction<Transaction>) => {
      state.transactions.unshift(action.payload);
      state.recentTransactions = state.transactions.slice(0, 10);
      
      // Update pending transactions
      if (action.payload.status === 'pending') {
        state.pendingTransactions.push(action.payload);
      }
    },
    updateTransaction: (state, action: PayloadAction<Transaction>) => {
      const index = state.transactions.findIndex(t => t.id === action.payload.id);
      if (index !== -1) {
        state.transactions[index] = action.payload;
      }
      
      // Update recent transactions
      state.recentTransactions = state.transactions.slice(0, 10);
      
      // Update pending transactions
      if (action.payload.status !== 'pending') {
        state.pendingTransactions = state.pendingTransactions.filter(t => t.id !== action.payload.id);
      }
    },
    updateAccountBalance: (state, action: PayloadAction<{ accountId: string; balance: number; availableBalance: number }>) => {
      const account = state.accounts.find(a => a.id === action.payload.accountId);
      if (account) {
        account.balance = action.payload.balance;
        account.availableBalance = action.payload.availableBalance;
        
        // Recalculate total balances
        state.totalBalance = state.accounts.reduce((sum, acc) => sum + acc.balance, 0);
        state.availableBalance = state.accounts.reduce((sum, acc) => sum + acc.availableBalance, 0);
      }
    },
    removeAccount: (state, action: PayloadAction<string>) => {
      state.accounts = state.accounts.filter(a => a.id !== action.payload);
      if (state.selectedAccountId === action.payload) {
        state.selectedAccountId = state.accounts.length > 0 ? state.accounts[0].id : null;
      }
    },
  },
  extraReducers: (builder) => {
    // Fetch wallet data
    builder
      .addCase(fetchWalletData.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchWalletData.fulfilled, (state, action) => {
        state.isLoading = false;
        state.accounts = action.payload.accounts;
        state.transactions = action.payload.transactions;
        state.recentTransactions = action.payload.transactions.slice(0, 10);
        state.pendingTransactions = action.payload.transactions.filter(t => t.status === 'pending');
        state.totalBalance = state.accounts.reduce((sum, acc) => sum + acc.balance, 0);
        state.availableBalance = state.accounts.reduce((sum, acc) => sum + acc.availableBalance, 0);
        state.lastUpdated = new Date().toISOString();
        
        // Set default account if none selected
        if (!state.selectedAccountId && state.accounts.length > 0) {
          const defaultAccount = state.accounts.find(a => a.isDefault) || state.accounts[0];
          state.selectedAccountId = defaultAccount.id;
        }
      })
      .addCase(fetchWalletData.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Refresh wallet data
    builder
      .addCase(refreshWalletData.pending, (state) => {
        state.isRefreshing = true;
        state.error = null;
      })
      .addCase(refreshWalletData.fulfilled, (state, action) => {
        state.isRefreshing = false;
        // Update with fresh data
        if (action.payload.accounts) {
          state.accounts = action.payload.accounts;
          state.totalBalance = state.accounts.reduce((sum, acc) => sum + acc.balance, 0);
          state.availableBalance = state.accounts.reduce((sum, acc) => sum + acc.availableBalance, 0);
        }
        if (action.payload.transactions) {
          state.transactions = action.payload.transactions;
          state.recentTransactions = action.payload.transactions.slice(0, 10);
          state.pendingTransactions = action.payload.transactions.filter(t => t.status === 'pending');
        }
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(refreshWalletData.rejected, (state, action) => {
        state.isRefreshing = false;
        state.error = action.payload as string;
      });

    // Send money
    builder
      .addCase(sendMoney.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(sendMoney.fulfilled, (state, action) => {
        state.isLoading = false;
        const transaction = action.payload;
        state.transactions.unshift(transaction);
        state.recentTransactions = state.transactions.slice(0, 10);
        
        if (transaction.status === 'pending') {
          state.pendingTransactions.push(transaction);
        }
        
        // Update account balance if provided
        if (transaction.fromAccount) {
          const account = state.accounts.find(a => a.id === transaction.fromAccount);
          if (account) {
            account.balance -= transaction.amount;
            account.availableBalance -= transaction.amount;
            state.totalBalance = state.accounts.reduce((sum, acc) => sum + acc.balance, 0);
            state.availableBalance = state.accounts.reduce((sum, acc) => sum + acc.availableBalance, 0);
          }
        }
      })
      .addCase(sendMoney.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Request money
    builder
      .addCase(requestMoney.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(requestMoney.fulfilled, (state, action) => {
        state.isLoading = false;
        const transaction = action.payload;
        state.transactions.unshift(transaction);
        state.recentTransactions = state.transactions.slice(0, 10);
        
        if (transaction.status === 'pending') {
          state.pendingTransactions.push(transaction);
        }
      })
      .addCase(requestMoney.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Add bank account
    builder
      .addCase(addBankAccount.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(addBankAccount.fulfilled, (state, action) => {
        state.isLoading = false;
        state.accounts.push(action.payload);
        
        // Set as default if it's the first account
        if (state.accounts.length === 1) {
          state.selectedAccountId = action.payload.id;
        }
      })
      .addCase(addBankAccount.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Fetch transaction history
    builder
      .addCase(fetchTransactionHistory.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(fetchTransactionHistory.fulfilled, (state, action) => {
        state.isLoading = false;
        // Append to existing transactions if offset provided, otherwise replace
        state.transactions = action.payload;
      })
      .addCase(fetchTransactionHistory.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });
  },
});

export const {
  clearError,
  setSelectedAccount,
  setTransactionFilters,
  clearTransactionFilters,
  addTransaction,
  updateTransaction,
  updateAccountBalance,
  removeAccount,
} = walletSlice.actions;

export default walletSlice.reducer;