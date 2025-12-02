import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { walletService } from '../../services/walletService';
import { 
  Wallet, 
  WalletBalance, 
  Transaction, 
  WalletLimit, 
  WalletType,
  EnhancedWalletBalance,
  SecuritySettings,
  Goal,
  Contact,
  Currency
} from '../../types/wallet';
import { showNotification } from './notificationSlice';

interface WalletState {
  wallets: Wallet[];
  activeWallet: Wallet | null;
  balance: number; // Simple balance for payment components
  balanceDetails: WalletBalance | null; // Detailed balance object
  balances: EnhancedWalletBalance[]; // Multi-currency balances for dashboard
  totalBalance: number; // Total balance across all currencies
  pendingBalance: number; // Total pending balance
  transactions: Transaction[];
  limits: WalletLimit | null;
  securitySettings: SecuritySettings | null;
  goals: Goal[];
  contacts: Contact[];
  loading: boolean;
  isLoading: boolean; // Alias for dashboard compatibility
  error: string | null;
  transactionLoading: boolean;
  createWalletLoading: boolean;
}

const initialState: WalletState = {
  wallets: [],
  activeWallet: null,
  balance: 0,
  balanceDetails: null,
  balances: [],
  totalBalance: 0,
  pendingBalance: 0,
  transactions: [],
  limits: null,
  securitySettings: null,
  goals: [],
  contacts: [],
  loading: false,
  isLoading: false,
  error: null,
  transactionLoading: false,
  createWalletLoading: false,
};

// Async thunks
export const fetchWallets = createAsyncThunk(
  'wallet/fetchWallets',
  async (_, { dispatch, rejectWithValue }) => {
    try {
      const response = await walletService.getWallets();
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to fetch wallets',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch wallets');
    }
  }
);

export const fetchWalletBalance = createAsyncThunk(
  'wallet/fetchBalance',
  async (walletId?: string, { getState, dispatch, rejectWithValue }) => {
    try {
      const state = getState() as any;
      const id = walletId || state.wallet.activeWallet?.id;
      if (!id) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.getWalletBalance(id);
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to fetch balance',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch balance');
    }
  }
);

// Simple balance fetch for payment components
export const getWalletBalance = createAsyncThunk(
  'wallet/getBalance',
  async (_, { getState, dispatch, rejectWithValue }) => {
    try {
      const state = getState() as any;
      const walletId = state.wallet.activeWallet?.id;
      if (!walletId) {
        // If no active wallet, try to fetch wallets first
        await dispatch(fetchWallets()).unwrap();
        const newState = getState() as any;
        const newWalletId = newState.wallet.activeWallet?.id;
        if (!newWalletId) {
          throw new Error('No wallet available');
        }
        const response = await walletService.getWalletBalance(newWalletId);
        return response.data;
      }
      const response = await walletService.getWalletBalance(walletId);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch balance');
    }
  }
);

export const createWallet = createAsyncThunk(
  'wallet/create',
  async (data: { type: WalletType; currency: string; name?: string }, { dispatch, rejectWithValue }) => {
    try {
      const response = await walletService.createWallet(data);
      dispatch(
        showNotification({
          message: 'Wallet created successfully',
          type: 'success',
        })
      );
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to create wallet',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to create wallet');
    }
  }
);

export const fetchWalletTransactions = createAsyncThunk(
  'wallet/fetchTransactions',
  async (
    params: {
      walletId?: string;
      page?: number;
      limit?: number;
      startDate?: string;
      endDate?: string;
      type?: string;
    },
    { getState, dispatch, rejectWithValue }
  ) => {
    try {
      const state = getState() as any;
      const walletId = params.walletId || state.wallet.activeWallet?.id;
      if (!walletId) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.getTransactions(walletId, params);
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to fetch transactions',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch transactions');
    }
  }
);

export const addMoney = createAsyncThunk(
  'wallet/addMoney',
  async (
    data: {
      walletId?: string;
      amount: number;
      paymentMethodId: string;
      description?: string;
    },
    { getState, dispatch, rejectWithValue }
  ) => {
    try {
      const state = getState() as any;
      const walletId = data.walletId || state.wallet.activeWallet?.id;
      if (!walletId) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.addMoney(walletId, data);
      dispatch(
        showNotification({
          message: 'Money added successfully',
          type: 'success',
        })
      );
      // Refresh balance after adding money
      dispatch(fetchWalletBalance(walletId));
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to add money',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to add money');
    }
  }
);

export const withdrawMoney = createAsyncThunk(
  'wallet/withdrawMoney',
  async (
    data: {
      walletId?: string;
      amount: number;
      bankAccountId: string;
      description?: string;
    },
    { getState, dispatch, rejectWithValue }
  ) => {
    try {
      const state = getState() as any;
      const walletId = data.walletId || state.wallet.activeWallet?.id;
      if (!walletId) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.withdrawMoney(walletId, data);
      dispatch(
        showNotification({
          message: 'Withdrawal initiated successfully',
          type: 'success',
        })
      );
      // Refresh balance after withdrawal
      dispatch(fetchWalletBalance(walletId));
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to withdraw money',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to withdraw money');
    }
  }
);

export const transferMoney = createAsyncThunk(
  'wallet/transfer',
  async (
    data: {
      fromWalletId?: string;
      toWalletId: string;
      amount: number;
      description?: string;
      reference?: string;
    },
    { getState, dispatch, rejectWithValue }
  ) => {
    try {
      const state = getState() as any;
      const fromWalletId = data.fromWalletId || state.wallet.activeWallet?.id;
      if (!fromWalletId) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.transferMoney({
        ...data,
        fromWalletId,
      });
      dispatch(
        showNotification({
          message: 'Transfer completed successfully',
          type: 'success',
        })
      );
      // Refresh balance after transfer
      dispatch(fetchWalletBalance(fromWalletId));
      return response.data;
    } catch (error: any) {
      dispatch(
        showNotification({
          message: error.response?.data?.message || 'Failed to transfer money',
          type: 'error',
        })
      );
      return rejectWithValue(error.response?.data?.message || 'Failed to transfer money');
    }
  }
);

export const fetchWalletLimits = createAsyncThunk(
  'wallet/fetchLimits',
  async (walletId?: string, { getState, dispatch, rejectWithValue }) => {
    try {
      const state = getState() as any;
      const id = walletId || state.wallet.activeWallet?.id;
      if (!id) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.getWalletLimits(id);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch limits');
    }
  }
);

// Load multi-currency balances for dashboard
export const loadBalances = createAsyncThunk(
  'wallet/loadBalances',
  async (_, { dispatch, rejectWithValue }) => {
    try {
      const response = await walletService.getMultiCurrencyBalances();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to load balances');
    }
  }
);

// Load security settings
export const loadSecuritySettings = createAsyncThunk(
  'wallet/loadSecuritySettings',
  async (_, { rejectWithValue }) => {
    try {
      const response = await walletService.getSecuritySettings();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to load security settings');
    }
  }
);

// Load goals
export const loadGoals = createAsyncThunk(
  'wallet/loadGoals',
  async (_, { rejectWithValue }) => {
    try {
      const response = await walletService.getGoals();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to load goals');
    }
  }
);

// Load contacts
export const loadContacts = createAsyncThunk(
  'wallet/loadContacts',
  async (_, { rejectWithValue }) => {
    try {
      const response = await walletService.getContacts();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to load contacts');
    }
  }
);

// Load all transactions
export const loadTransactions = createAsyncThunk(
  'wallet/loadTransactions',
  async (_, { rejectWithValue }) => {
    try {
      const response = await walletService.getAllTransactions();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to load transactions');
    }
  }
);

// Load wallet limits (alias for dashboard)
export const loadLimits = createAsyncThunk(
  'wallet/loadLimits',
  async (_, { getState, dispatch, rejectWithValue }) => {
    try {
      const state = getState() as any;
      const walletId = state.wallet.activeWallet?.id;
      if (!walletId) {
        throw new Error('No wallet selected');
      }
      const response = await walletService.getWalletLimits(walletId);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to load limits');
    }
  }
);

// Wallet slice
const walletSlice = createSlice({
  name: 'wallet',
  initialState,
  reducers: {
    setActiveWallet: (state, action: PayloadAction<Wallet>) => {
      state.activeWallet = action.payload;
    },
    clearWalletError: (state) => {
      state.error = null;
    },
    clearTransactions: (state) => {
      state.transactions = [];
    },
  },
  extraReducers: (builder) => {
    // Fetch wallets
    builder
      .addCase(fetchWallets.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchWallets.fulfilled, (state, action) => {
        state.loading = false;
        state.wallets = action.payload;
        // Set first wallet as active if none selected
        if (!state.activeWallet && action.payload.length > 0) {
          state.activeWallet = action.payload[0];
        }
      })
      .addCase(fetchWallets.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });

    // Fetch balance
    builder
      .addCase(fetchWalletBalance.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchWalletBalance.fulfilled, (state, action) => {
        state.loading = false;
        state.balanceDetails = action.payload;
        // Update simple balance from details
        state.balance = action.payload?.available || 0;
      })
      .addCase(fetchWalletBalance.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });

    // Create wallet
    builder
      .addCase(createWallet.pending, (state) => {
        state.createWalletLoading = true;
        state.error = null;
      })
      .addCase(createWallet.fulfilled, (state, action) => {
        state.createWalletLoading = false;
        state.wallets.push(action.payload);
        state.activeWallet = action.payload;
      })
      .addCase(createWallet.rejected, (state, action) => {
        state.createWalletLoading = false;
        state.error = action.payload as string;
      });

    // Fetch transactions
    builder
      .addCase(fetchWalletTransactions.pending, (state) => {
        state.transactionLoading = true;
        state.error = null;
      })
      .addCase(fetchWalletTransactions.fulfilled, (state, action) => {
        state.transactionLoading = false;
        state.transactions = action.payload.content || action.payload;
      })
      .addCase(fetchWalletTransactions.rejected, (state, action) => {
        state.transactionLoading = false;
        state.error = action.payload as string;
      });

    // Add money
    builder
      .addCase(addMoney.pending, (state) => {
        state.transactionLoading = true;
        state.error = null;
      })
      .addCase(addMoney.fulfilled, (state) => {
        state.transactionLoading = false;
      })
      .addCase(addMoney.rejected, (state, action) => {
        state.transactionLoading = false;
        state.error = action.payload as string;
      });

    // Withdraw money
    builder
      .addCase(withdrawMoney.pending, (state) => {
        state.transactionLoading = true;
        state.error = null;
      })
      .addCase(withdrawMoney.fulfilled, (state) => {
        state.transactionLoading = false;
      })
      .addCase(withdrawMoney.rejected, (state, action) => {
        state.transactionLoading = false;
        state.error = action.payload as string;
      });

    // Transfer money
    builder
      .addCase(transferMoney.pending, (state) => {
        state.transactionLoading = true;
        state.error = null;
      })
      .addCase(transferMoney.fulfilled, (state) => {
        state.transactionLoading = false;
      })
      .addCase(transferMoney.rejected, (state, action) => {
        state.transactionLoading = false;
        state.error = action.payload as string;
      });

    // Fetch limits
    builder
      .addCase(fetchWalletLimits.fulfilled, (state, action) => {
        state.limits = action.payload;
      });

    // Get wallet balance (simple)
    builder
      .addCase(getWalletBalance.pending, (state) => {
        state.loading = true;
      })
      .addCase(getWalletBalance.fulfilled, (state, action) => {
        state.loading = false;
        state.balanceDetails = action.payload;
        // Update simple balance from details
        state.balance = action.payload?.available || 0;
      })
      .addCase(getWalletBalance.rejected, (state) => {
        state.loading = false;
      });

    // Load multi-currency balances
    builder
      .addCase(loadBalances.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(loadBalances.fulfilled, (state, action) => {
        state.isLoading = false;
        state.balances = action.payload;
        // Calculate total balance across all currencies
        state.totalBalance = action.payload.reduce((sum: number, bal: EnhancedWalletBalance) => sum + bal.amount, 0);
        state.pendingBalance = action.payload.reduce((sum: number, bal: EnhancedWalletBalance) => sum + bal.pending, 0);
      })
      .addCase(loadBalances.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Load security settings
    builder
      .addCase(loadSecuritySettings.fulfilled, (state, action) => {
        state.securitySettings = action.payload;
      });

    // Load goals
    builder
      .addCase(loadGoals.fulfilled, (state, action) => {
        state.goals = action.payload;
      });

    // Load contacts
    builder
      .addCase(loadContacts.fulfilled, (state, action) => {
        state.contacts = action.payload;
      });

    // Load transactions
    builder
      .addCase(loadTransactions.pending, (state) => {
        state.transactionLoading = true;
      })
      .addCase(loadTransactions.fulfilled, (state, action) => {
        state.transactionLoading = false;
        state.transactions = action.payload;
      })
      .addCase(loadTransactions.rejected, (state, action) => {
        state.transactionLoading = false;
        state.error = action.payload as string;
      });

    // Load limits
    builder
      .addCase(loadLimits.fulfilled, (state, action) => {
        state.limits = action.payload;
      });
  },
});

export const { setActiveWallet, clearWalletError, clearTransactions } = walletSlice.actions;

export default walletSlice.reducer;