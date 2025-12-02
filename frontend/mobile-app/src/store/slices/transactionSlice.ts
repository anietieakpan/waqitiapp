/**
 * Transaction Slice - Production-ready transaction state management
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface TransactionRequest {
  id: string;
  type: 'sent' | 'received';
  status: 'pending' | 'accepted' | 'declined' | 'expired';
  amount: number;
  currency: string;
  description?: string;
  fromUser: {
    id: string;
    name: string;
    avatar?: string;
  };
  toUser: {
    id: string;
    name: string;
    avatar?: string;
  };
  createdAt: string;
  expiresAt: string;
  metadata?: {
    location?: string;
    category?: string;
  };
}

export interface PaymentSchedule {
  id: string;
  name: string;
  amount: number;
  currency: string;
  frequency: 'daily' | 'weekly' | 'monthly' | 'yearly';
  toUser: {
    id: string;
    name: string;
    avatar?: string;
  };
  nextPayment: string;
  isActive: boolean;
  description?: string;
  createdAt: string;
}

export interface SplitPayment {
  id: string;
  title: string;
  totalAmount: number;
  currency: string;
  organizer: {
    id: string;
    name: string;
    avatar?: string;
  };
  participants: Array<{
    userId: string;
    name: string;
    avatar?: string;
    amount: number;
    status: 'pending' | 'paid' | 'declined';
  }>;
  status: 'active' | 'completed' | 'cancelled';
  createdAt: string;
  dueDate?: string;
}

export interface TransactionState {
  requests: TransactionRequest[];
  schedules: PaymentSchedule[];
  splitPayments: SplitPayment[];
  isLoading: boolean;
  error: string | null;
  lastUpdated: string | null;
  filters: {
    status: string | null;
    type: string | null;
    dateRange: {
      start: string | null;
      end: string | null;
    };
  };
}

const initialState: TransactionState = {
  requests: [],
  schedules: [],
  splitPayments: [],
  isLoading: false,
  error: null,
  lastUpdated: null,
  filters: {
    status: null,
    type: null,
    dateRange: {
      start: null,
      end: null,
    },
  },
};

// Async thunks
export const fetchTransactionRequests = createAsyncThunk(
  'transactions/fetchRequests',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/transactions/requests');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch transaction requests', {
        feature: 'transaction_slice',
        action: 'fetch_requests_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch transaction requests');
    }
  }
);

export const sendTransactionRequest = createAsyncThunk(
  'transactions/sendRequest',
  async (payload: {
    toUserId: string;
    amount: number;
    currency: string;
    description?: string;
    expiresIn?: number; // hours
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/transactions/requests', payload);
      
      // Track event
      await ApiService.trackEvent('payment_request_sent', {
        toUserId: payload.toUserId,
        amount: payload.amount,
        currency: payload.currency,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to send transaction request', {
        feature: 'transaction_slice',
        action: 'send_request_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to send transaction request');
    }
  }
);

export const respondToRequest = createAsyncThunk(
  'transactions/respondToRequest',
  async (payload: {
    requestId: string;
    action: 'accept' | 'decline';
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post(`/transactions/requests/${payload.requestId}/${payload.action}`);
      
      // Track event
      await ApiService.trackEvent('payment_request_responded', {
        requestId: payload.requestId,
        action: payload.action,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to respond to transaction request', {
        feature: 'transaction_slice',
        action: 'respond_to_request_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to respond to request');
    }
  }
);

export const createPaymentSchedule = createAsyncThunk(
  'transactions/createSchedule',
  async (payload: {
    name: string;
    toUserId: string;
    amount: number;
    currency: string;
    frequency: 'daily' | 'weekly' | 'monthly' | 'yearly';
    startDate: string;
    description?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/transactions/schedules', payload);
      
      // Track event
      await ApiService.trackEvent('payment_schedule_created', {
        toUserId: payload.toUserId,
        amount: payload.amount,
        frequency: payload.frequency,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to create payment schedule', {
        feature: 'transaction_slice',
        action: 'create_schedule_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create payment schedule');
    }
  }
);

export const fetchPaymentSchedules = createAsyncThunk(
  'transactions/fetchSchedules',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/transactions/schedules');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch payment schedules', {
        feature: 'transaction_slice',
        action: 'fetch_schedules_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch payment schedules');
    }
  }
);

export const updatePaymentSchedule = createAsyncThunk(
  'transactions/updateSchedule',
  async (payload: {
    scheduleId: string;
    updates: Partial<PaymentSchedule>;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.put(`/transactions/schedules/${payload.scheduleId}`, payload.updates);
      return response.data;
    } catch (error: any) {
      logError('Failed to update payment schedule', {
        feature: 'transaction_slice',
        action: 'update_schedule_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to update payment schedule');
    }
  }
);

export const deletePaymentSchedule = createAsyncThunk(
  'transactions/deleteSchedule',
  async (scheduleId: string, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/transactions/schedules/${scheduleId}`);
      return scheduleId;
    } catch (error: any) {
      logError('Failed to delete payment schedule', {
        feature: 'transaction_slice',
        action: 'delete_schedule_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to delete payment schedule');
    }
  }
);

export const createSplitPayment = createAsyncThunk(
  'transactions/createSplitPayment',
  async (payload: {
    title: string;
    totalAmount: number;
    currency: string;
    participants: Array<{
      userId: string;
      amount: number;
    }>;
    dueDate?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/transactions/split-payments', payload);
      
      // Track event
      await ApiService.trackEvent('split_payment_created', {
        totalAmount: payload.totalAmount,
        participantCount: payload.participants.length,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to create split payment', {
        feature: 'transaction_slice',
        action: 'create_split_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create split payment');
    }
  }
);

export const fetchSplitPayments = createAsyncThunk(
  'transactions/fetchSplitPayments',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/transactions/split-payments');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch split payments', {
        feature: 'transaction_slice',
        action: 'fetch_split_payments_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch split payments');
    }
  }
);

export const respondToSplitPayment = createAsyncThunk(
  'transactions/respondToSplitPayment',
  async (payload: {
    splitPaymentId: string;
    action: 'pay' | 'decline';
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post(`/transactions/split-payments/${payload.splitPaymentId}/${payload.action}`);
      
      // Track event
      await ApiService.trackEvent('split_payment_responded', {
        splitPaymentId: payload.splitPaymentId,
        action: payload.action,
        timestamp: new Date().toISOString(),
      });

      return response.data;
    } catch (error: any) {
      logError('Failed to respond to split payment', {
        feature: 'transaction_slice',
        action: 'respond_to_split_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to respond to split payment');
    }
  }
);

// Transaction slice
const transactionSlice = createSlice({
  name: 'transactions',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setFilters: (state, action: PayloadAction<Partial<TransactionState['filters']>>) => {
      state.filters = {
        ...state.filters,
        ...action.payload,
      };
    },
    clearFilters: (state) => {
      state.filters = {
        status: null,
        type: null,
        dateRange: {
          start: null,
          end: null,
        },
      };
    },
    addTransactionRequest: (state, action: PayloadAction<TransactionRequest>) => {
      state.requests.unshift(action.payload);
    },
    updateTransactionRequest: (state, action: PayloadAction<TransactionRequest>) => {
      const index = state.requests.findIndex(r => r.id === action.payload.id);
      if (index !== -1) {
        state.requests[index] = action.payload;
      }
    },
    removeTransactionRequest: (state, action: PayloadAction<string>) => {
      state.requests = state.requests.filter(r => r.id !== action.payload);
    },
    addPaymentSchedule: (state, action: PayloadAction<PaymentSchedule>) => {
      state.schedules.push(action.payload);
    },
    updatePaymentScheduleState: (state, action: PayloadAction<PaymentSchedule>) => {
      const index = state.schedules.findIndex(s => s.id === action.payload.id);
      if (index !== -1) {
        state.schedules[index] = action.payload;
      }
    },
    removePaymentScheduleState: (state, action: PayloadAction<string>) => {
      state.schedules = state.schedules.filter(s => s.id !== action.payload);
    },
    addSplitPayment: (state, action: PayloadAction<SplitPayment>) => {
      state.splitPayments.unshift(action.payload);
    },
    updateSplitPayment: (state, action: PayloadAction<SplitPayment>) => {
      const index = state.splitPayments.findIndex(sp => sp.id === action.payload.id);
      if (index !== -1) {
        state.splitPayments[index] = action.payload;
      }
    },
  },
  extraReducers: (builder) => {
    // Fetch transaction requests
    builder
      .addCase(fetchTransactionRequests.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchTransactionRequests.fulfilled, (state, action) => {
        state.isLoading = false;
        state.requests = action.payload;
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchTransactionRequests.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Send transaction request
    builder
      .addCase(sendTransactionRequest.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(sendTransactionRequest.fulfilled, (state, action) => {
        state.isLoading = false;
        state.requests.unshift(action.payload);
      })
      .addCase(sendTransactionRequest.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Respond to request
    builder
      .addCase(respondToRequest.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(respondToRequest.fulfilled, (state, action) => {
        state.isLoading = false;
        const index = state.requests.findIndex(r => r.id === action.payload.id);
        if (index !== -1) {
          state.requests[index] = action.payload;
        }
      })
      .addCase(respondToRequest.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Create payment schedule
    builder
      .addCase(createPaymentSchedule.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(createPaymentSchedule.fulfilled, (state, action) => {
        state.isLoading = false;
        state.schedules.push(action.payload);
      })
      .addCase(createPaymentSchedule.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Fetch payment schedules
    builder
      .addCase(fetchPaymentSchedules.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchPaymentSchedules.fulfilled, (state, action) => {
        state.isLoading = false;
        state.schedules = action.payload;
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchPaymentSchedules.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Update payment schedule
    builder
      .addCase(updatePaymentSchedule.fulfilled, (state, action) => {
        const index = state.schedules.findIndex(s => s.id === action.payload.id);
        if (index !== -1) {
          state.schedules[index] = action.payload;
        }
      });

    // Delete payment schedule
    builder
      .addCase(deletePaymentSchedule.fulfilled, (state, action) => {
        state.schedules = state.schedules.filter(s => s.id !== action.payload);
      });

    // Create split payment
    builder
      .addCase(createSplitPayment.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(createSplitPayment.fulfilled, (state, action) => {
        state.isLoading = false;
        state.splitPayments.unshift(action.payload);
      })
      .addCase(createSplitPayment.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Fetch split payments
    builder
      .addCase(fetchSplitPayments.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchSplitPayments.fulfilled, (state, action) => {
        state.isLoading = false;
        state.splitPayments = action.payload;
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchSplitPayments.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Respond to split payment
    builder
      .addCase(respondToSplitPayment.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(respondToSplitPayment.fulfilled, (state, action) => {
        state.isLoading = false;
        const index = state.splitPayments.findIndex(sp => sp.id === action.payload.id);
        if (index !== -1) {
          state.splitPayments[index] = action.payload;
        }
      })
      .addCase(respondToSplitPayment.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });
  },
});

export const {
  clearError,
  setFilters,
  clearFilters,
  addTransactionRequest,
  updateTransactionRequest,
  removeTransactionRequest,
  addPaymentSchedule,
  updatePaymentScheduleState,
  removePaymentScheduleState,
  addSplitPayment,
  updateSplitPayment,
} = transactionSlice.actions;

export default transactionSlice.reducer;