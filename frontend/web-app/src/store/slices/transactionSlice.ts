import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { transactionService } from '../../services/transactionService';
import { Transaction, TransactionFilter, TransactionStats } from '../../types/transaction';

interface TransactionState {
  transactions: Transaction[];
  recentTransactions: Transaction[];
  selectedTransaction: Transaction | null;
  filters: TransactionFilter;
  stats: TransactionStats | null;
  totalCount: number;
  currentPage: number;
  pageSize: number;
  loading: boolean;
  statsLoading: boolean;
  error: string | null;
}

const initialState: TransactionState = {
  transactions: [],
  recentTransactions: [],
  selectedTransaction: null,
  filters: {
    startDate: null,
    endDate: null,
    type: 'all',
    status: 'all',
    minAmount: null,
    maxAmount: null,
    searchQuery: '',
  },
  stats: null,
  totalCount: 0,
  currentPage: 1,
  pageSize: 20,
  loading: false,
  statsLoading: false,
  error: null,
};

// Async thunks
export const fetchTransactions = createAsyncThunk(
  'transaction/fetchTransactions',
  async ({ page, filters }: { page?: number; filters?: TransactionFilter }) => {
    const response = await transactionService.getTransactions({
      page: page || 1,
      pageSize: 20,
      ...filters,
    });
    return response;
  }
);

export const fetchRecentTransactions = createAsyncThunk(
  'transaction/fetchRecentTransactions',
  async () => {
    const response = await transactionService.getRecentTransactions();
    return response;
  }
);

export const fetchTransactionById = createAsyncThunk(
  'transaction/fetchTransactionById',
  async (transactionId: string) => {
    const response = await transactionService.getTransactionById(transactionId);
    return response;
  }
);

export const fetchTransactionStats = createAsyncThunk(
  'transaction/fetchTransactionStats',
  async (period: 'week' | 'month' | 'year' = 'month') => {
    const response = await transactionService.getTransactionStats(period);
    return response;
  }
);

export const exportTransactions = createAsyncThunk(
  'transaction/exportTransactions',
  async ({ format, filters }: { format: 'csv' | 'pdf'; filters?: TransactionFilter }) => {
    const response = await transactionService.exportTransactions(format, filters);
    return response;
  }
);

export const searchTransactions = createAsyncThunk(
  'transaction/searchTransactions',
  async (query: string) => {
    const response = await transactionService.searchTransactions(query);
    return response;
  }
);

const transactionSlice = createSlice({
  name: 'transaction',
  initialState,
  reducers: {
    setFilters: (state, action: PayloadAction<Partial<TransactionFilter>>) => {
      state.filters = { ...state.filters, ...action.payload };
      state.currentPage = 1; // Reset to first page when filters change
    },
    clearFilters: (state) => {
      state.filters = initialState.filters;
      state.currentPage = 1;
    },
    setCurrentPage: (state, action: PayloadAction<number>) => {
      state.currentPage = action.payload;
    },
    setSelectedTransaction: (state, action: PayloadAction<Transaction | null>) => {
      state.selectedTransaction = action.payload;
    },
    updateTransaction: (state, action: PayloadAction<Transaction>) => {
      const index = state.transactions.findIndex(t => t.id === action.payload.id);
      if (index !== -1) {
        state.transactions[index] = action.payload;
      }
      const recentIndex = state.recentTransactions.findIndex(t => t.id === action.payload.id);
      if (recentIndex !== -1) {
        state.recentTransactions[recentIndex] = action.payload;
      }
      if (state.selectedTransaction?.id === action.payload.id) {
        state.selectedTransaction = action.payload;
      }
    },
    addTransaction: (state, action: PayloadAction<Transaction>) => {
      state.transactions.unshift(action.payload);
      state.recentTransactions.unshift(action.payload);
      if (state.recentTransactions.length > 10) {
        state.recentTransactions.pop();
      }
      state.totalCount += 1;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Fetch transactions
      .addCase(fetchTransactions.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchTransactions.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions = action.payload.transactions;
        state.totalCount = action.payload.totalCount;
        state.currentPage = action.payload.currentPage;
      })
      .addCase(fetchTransactions.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch transactions';
      })
      // Fetch recent transactions
      .addCase(fetchRecentTransactions.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchRecentTransactions.fulfilled, (state, action) => {
        state.loading = false;
        state.recentTransactions = action.payload;
      })
      .addCase(fetchRecentTransactions.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch recent transactions';
      })
      // Fetch transaction by ID
      .addCase(fetchTransactionById.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchTransactionById.fulfilled, (state, action) => {
        state.loading = false;
        state.selectedTransaction = action.payload;
      })
      .addCase(fetchTransactionById.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch transaction details';
      })
      // Fetch transaction stats
      .addCase(fetchTransactionStats.pending, (state) => {
        state.statsLoading = true;
        state.error = null;
      })
      .addCase(fetchTransactionStats.fulfilled, (state, action) => {
        state.statsLoading = false;
        state.stats = action.payload;
      })
      .addCase(fetchTransactionStats.rejected, (state, action) => {
        state.statsLoading = false;
        state.error = action.error.message || 'Failed to fetch transaction stats';
      })
      // Search transactions
      .addCase(searchTransactions.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(searchTransactions.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions = action.payload;
        state.totalCount = action.payload.length;
      })
      .addCase(searchTransactions.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to search transactions';
      });
  },
});

export const {
  setFilters,
  clearFilters,
  setCurrentPage,
  setSelectedTransaction,
  updateTransaction,
  addTransaction,
  clearError,
} = transactionSlice.actions;

export default transactionSlice.reducer;