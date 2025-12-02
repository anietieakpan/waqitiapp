/**
 * Payments Slice - Payment processing and transaction state management
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface PaymentMethod {
  id: string;
  type: 'card' | 'bank_account' | 'wallet' | 'crypto';
  last4: string;
  brand?: string; // For cards: visa, mastercard, etc.
  bankName?: string; // For bank accounts
  walletType?: string; // For digital wallets
  isDefault: boolean;
  isVerified: boolean;
  expiryMonth?: number;
  expiryYear?: number;
  createdAt: string;
  metadata?: Record<string, any>;
}

export interface Transaction {
  id: string;
  type: 'payment' | 'transfer' | 'deposit' | 'withdrawal' | 'refund';
  status: 'pending' | 'processing' | 'completed' | 'failed' | 'cancelled';
  amount: number;
  currency: string;
  fromAccount?: string;
  toAccount?: string;
  recipientName?: string;
  recipientEmail?: string;
  recipientPhone?: string;
  description?: string;
  reference: string;
  fee: number;
  exchangeRate?: number;
  category?: string;
  tags?: string[];
  metadata?: Record<string, any>;
  createdAt: string;
  completedAt?: string;
  failureReason?: string;
}

export interface PaymentRequest {
  id: string;
  requesterId: string;
  requesterName: string;
  amount: number;
  currency: string;
  reason: string;
  status: 'pending' | 'accepted' | 'rejected' | 'expired';
  dueDate?: string;
  createdAt: string;
  respondedAt?: string;
  attachments?: string[];
}

export interface ScheduledPayment {
  id: string;
  paymentMethodId: string;
  recipientId: string;
  recipientName: string;
  amount: number;
  currency: string;
  frequency: 'once' | 'daily' | 'weekly' | 'monthly' | 'yearly';
  nextPaymentDate: string;
  endDate?: string;
  isActive: boolean;
  description?: string;
  createdAt: string;
  lastProcessedAt?: string;
  failureCount: number;
}

export interface PaymentLimit {
  type: 'daily' | 'weekly' | 'monthly' | 'per_transaction';
  limit: number;
  used: number;
  remaining: number;
  resetsAt: string;
}

export interface QRPayment {
  id: string;
  qrCode: string;
  amount?: number;
  currency: string;
  description?: string;
  expiresAt: string;
  status: 'active' | 'used' | 'expired';
  createdAt: string;
}

export interface PaymentsState {
  // Payment Methods
  paymentMethods: PaymentMethod[];
  defaultPaymentMethodId: string | null;
  
  // Transactions
  transactions: Transaction[];
  transactionFilters: {
    type?: string;
    status?: string;
    dateFrom?: string;
    dateTo?: string;
    minAmount?: number;
    maxAmount?: number;
  };
  
  // Payment Requests
  incomingRequests: PaymentRequest[];
  outgoingRequests: PaymentRequest[];
  
  // Scheduled Payments
  scheduledPayments: ScheduledPayment[];
  
  // Payment Limits
  paymentLimits: PaymentLimit[];
  
  // QR Payments
  activeQRPayment: QRPayment | null;
  qrPaymentHistory: QRPayment[];
  
  // Transaction in Progress
  currentTransaction: {
    type: string;
    amount: number;
    currency: string;
    recipient?: string;
    paymentMethodId?: string;
    status: 'idle' | 'processing' | 'confirming' | 'completed' | 'failed';
    error?: string;
  } | null;
  
  // Statistics
  statistics: {
    totalSpent: number;
    totalReceived: number;
    pendingAmount: number;
    lastTransactionDate?: string;
    favoriteRecipients: Array<{
      id: string;
      name: string;
      count: number;
    }>;
  };
  
  // UI State
  isLoading: boolean;
  isProcessingPayment: boolean;
  error: string | null;
  successMessage: string | null;
}

// Initial state
const initialState: PaymentsState = {
  paymentMethods: [],
  defaultPaymentMethodId: null,
  transactions: [],
  transactionFilters: {},
  incomingRequests: [],
  outgoingRequests: [],
  scheduledPayments: [],
  paymentLimits: [],
  activeQRPayment: null,
  qrPaymentHistory: [],
  currentTransaction: null,
  statistics: {
    totalSpent: 0,
    totalReceived: 0,
    pendingAmount: 0,
    favoriteRecipients: [],
  },
  isLoading: false,
  isProcessingPayment: false,
  error: null,
  successMessage: null,
};

// Async thunks
export const fetchPaymentMethods = createAsyncThunk(
  'payments/fetchMethods',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/payment-methods');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch payment methods', {
        feature: 'payments_slice',
        action: 'fetch_payment_methods_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch payment methods');
    }
  }
);

export const addPaymentMethod = createAsyncThunk(
  'payments/addMethod',
  async (methodData: {
    type: 'card' | 'bank_account';
    token?: string;
    cardNumber?: string;
    expiryMonth?: number;
    expiryYear?: number;
    cvv?: string;
    accountNumber?: string;
    routingNumber?: string;
    accountHolderName?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/payment-methods', methodData);
      
      // Track event
      await ApiService.trackEvent('payment_method_added', {
        type: methodData.type,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to add payment method', {
        feature: 'payments_slice',
        action: 'add_payment_method_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to add payment method');
    }
  }
);

export const removePaymentMethod = createAsyncThunk(
  'payments/removeMethod',
  async (methodId: string, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/payment-methods/${methodId}`);
      
      // Track event
      await ApiService.trackEvent('payment_method_removed', {
        methodId,
        timestamp: new Date().toISOString(),
      });
      
      return methodId;
    } catch (error: any) {
      logError('Failed to remove payment method', {
        feature: 'payments_slice',
        action: 'remove_payment_method_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to remove payment method');
    }
  }
);

export const setDefaultPaymentMethod = createAsyncThunk(
  'payments/setDefault',
  async (methodId: string, { rejectWithValue }) => {
    try {
      await ApiService.put(`/payment-methods/${methodId}/default`);
      return methodId;
    } catch (error: any) {
      logError('Failed to set default payment method', {
        feature: 'payments_slice',
        action: 'set_default_method_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to set default payment method');
    }
  }
);

export const fetchTransactions = createAsyncThunk(
  'payments/fetchTransactions',
  async (filters?: {
    page?: number;
    limit?: number;
    type?: string;
    status?: string;
    dateFrom?: string;
    dateTo?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/transactions', { params: filters });
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch transactions', {
        feature: 'payments_slice',
        action: 'fetch_transactions_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch transactions');
    }
  }
);

export const initiatePayment = createAsyncThunk(
  'payments/initiate',
  async (paymentData: {
    amount: number;
    currency: string;
    recipientId?: string;
    recipientEmail?: string;
    recipientPhone?: string;
    paymentMethodId: string;
    description?: string;
    category?: string;
    scheduledDate?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/payments/initiate', paymentData);
      
      // Track event
      await ApiService.trackEvent('payment_initiated', {
        amount: paymentData.amount,
        currency: paymentData.currency,
        category: paymentData.category,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to initiate payment', {
        feature: 'payments_slice',
        action: 'initiate_payment_failed'
      }, error);
      
      // Track failed payment
      await ApiService.trackEvent('payment_failed', {
        amount: paymentData.amount,
        currency: paymentData.currency,
        error: error.message,
        timestamp: new Date().toISOString(),
      });
      
      return rejectWithValue(error.message || 'Failed to initiate payment');
    }
  }
);

export const confirmPayment = createAsyncThunk(
  'payments/confirm',
  async (data: {
    transactionId: string;
    verificationCode?: string;
    pin?: string;
    biometricSignature?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/payments/confirm', data);
      
      // Track event
      await ApiService.trackEvent('payment_confirmed', {
        transactionId: data.transactionId,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to confirm payment', {
        feature: 'payments_slice',
        action: 'confirm_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to confirm payment');
    }
  }
);

export const cancelPayment = createAsyncThunk(
  'payments/cancel',
  async (transactionId: string, { rejectWithValue }) => {
    try {
      await ApiService.post(`/payments/${transactionId}/cancel`);
      
      // Track event
      await ApiService.trackEvent('payment_cancelled', {
        transactionId,
        timestamp: new Date().toISOString(),
      });
      
      return transactionId;
    } catch (error: any) {
      logError('Failed to cancel payment', {
        feature: 'payments_slice',
        action: 'cancel_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to cancel payment');
    }
  }
);

export const createPaymentRequest = createAsyncThunk(
  'payments/createRequest',
  async (requestData: {
    amount: number;
    currency: string;
    recipientIds: string[];
    reason: string;
    dueDate?: string;
    attachments?: string[];
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/payment-requests', requestData);
      
      // Track event
      await ApiService.trackEvent('payment_request_created', {
        amount: requestData.amount,
        recipientCount: requestData.recipientIds.length,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create payment request', {
        feature: 'payments_slice',
        action: 'create_payment_request_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create payment request');
    }
  }
);

export const respondToPaymentRequest = createAsyncThunk(
  'payments/respondToRequest',
  async (data: {
    requestId: string;
    action: 'accept' | 'reject';
    paymentMethodId?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post(`/payment-requests/${data.requestId}/respond`, {
        action: data.action,
        paymentMethodId: data.paymentMethodId,
      });
      
      // Track event
      await ApiService.trackEvent('payment_request_responded', {
        requestId: data.requestId,
        action: data.action,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to respond to payment request', {
        feature: 'payments_slice',
        action: 'respond_to_request_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to respond to payment request');
    }
  }
);

export const createScheduledPayment = createAsyncThunk(
  'payments/createScheduled',
  async (scheduleData: {
    paymentMethodId: string;
    recipientId: string;
    amount: number;
    currency: string;
    frequency: 'once' | 'daily' | 'weekly' | 'monthly' | 'yearly';
    startDate: string;
    endDate?: string;
    description?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/scheduled-payments', scheduleData);
      
      // Track event
      await ApiService.trackEvent('scheduled_payment_created', {
        frequency: scheduleData.frequency,
        amount: scheduleData.amount,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create scheduled payment', {
        feature: 'payments_slice',
        action: 'create_scheduled_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create scheduled payment');
    }
  }
);

export const cancelScheduledPayment = createAsyncThunk(
  'payments/cancelScheduled',
  async (scheduledPaymentId: string, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/scheduled-payments/${scheduledPaymentId}`);
      
      // Track event
      await ApiService.trackEvent('scheduled_payment_cancelled', {
        scheduledPaymentId,
        timestamp: new Date().toISOString(),
      });
      
      return scheduledPaymentId;
    } catch (error: any) {
      logError('Failed to cancel scheduled payment', {
        feature: 'payments_slice',
        action: 'cancel_scheduled_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to cancel scheduled payment');
    }
  }
);

export const generateQRPayment = createAsyncThunk(
  'payments/generateQR',
  async (data: {
    amount?: number;
    currency: string;
    description?: string;
    expiryMinutes?: number;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/payments/qr/generate', data);
      
      // Track event
      await ApiService.trackEvent('qr_payment_generated', {
        hasAmount: !!data.amount,
        currency: data.currency,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to generate QR payment', {
        feature: 'payments_slice',
        action: 'generate_qr_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to generate QR payment');
    }
  }
);

export const scanQRPayment = createAsyncThunk(
  'payments/scanQR',
  async (qrData: string, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/payments/qr/scan', { qrData });
      
      // Track event
      await ApiService.trackEvent('qr_payment_scanned', {
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to scan QR payment', {
        feature: 'payments_slice',
        action: 'scan_qr_payment_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to scan QR payment');
    }
  }
);

export const fetchPaymentLimits = createAsyncThunk(
  'payments/fetchLimits',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/payment-limits');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch payment limits', {
        feature: 'payments_slice',
        action: 'fetch_payment_limits_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch payment limits');
    }
  }
);

// Payments slice
const paymentsSlice = createSlice({
  name: 'payments',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    clearSuccessMessage: (state) => {
      state.successMessage = null;
    },
    setTransactionFilters: (state, action: PayloadAction<PaymentsState['transactionFilters']>) => {
      state.transactionFilters = action.payload;
    },
    clearTransactionFilters: (state) => {
      state.transactionFilters = {};
    },
    startPaymentFlow: (state, action: PayloadAction<{
      type: string;
      amount: number;
      currency: string;
      recipient?: string;
    }>) => {
      state.currentTransaction = {
        ...action.payload,
        status: 'idle',
      };
    },
    updatePaymentFlowStatus: (state, action: PayloadAction<'processing' | 'confirming' | 'completed' | 'failed'>) => {
      if (state.currentTransaction) {
        state.currentTransaction.status = action.payload;
      }
    },
    clearPaymentFlow: (state) => {
      state.currentTransaction = null;
    },
    updateStatistics: (state, action: PayloadAction<Partial<PaymentsState['statistics']>>) => {
      state.statistics = { ...state.statistics, ...action.payload };
    },
  },
  extraReducers: (builder) => {
    // Fetch payment methods
    builder
      .addCase(fetchPaymentMethods.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchPaymentMethods.fulfilled, (state, action) => {
        state.isLoading = false;
        state.paymentMethods = action.payload.methods;
        state.defaultPaymentMethodId = action.payload.defaultMethodId;
      })
      .addCase(fetchPaymentMethods.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Add payment method
    builder
      .addCase(addPaymentMethod.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(addPaymentMethod.fulfilled, (state, action) => {
        state.isLoading = false;
        state.paymentMethods.push(action.payload);
        state.successMessage = 'Payment method added successfully';
      })
      .addCase(addPaymentMethod.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Remove payment method
    builder
      .addCase(removePaymentMethod.fulfilled, (state, action) => {
        state.paymentMethods = state.paymentMethods.filter(method => method.id !== action.payload);
        state.successMessage = 'Payment method removed successfully';
      });

    // Set default payment method
    builder
      .addCase(setDefaultPaymentMethod.fulfilled, (state, action) => {
        state.paymentMethods.forEach(method => {
          method.isDefault = method.id === action.payload;
        });
        state.defaultPaymentMethodId = action.payload;
      });

    // Fetch transactions
    builder
      .addCase(fetchTransactions.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchTransactions.fulfilled, (state, action) => {
        state.isLoading = false;
        state.transactions = action.payload.transactions;
        state.statistics = action.payload.statistics || state.statistics;
      })
      .addCase(fetchTransactions.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Initiate payment
    builder
      .addCase(initiatePayment.pending, (state) => {
        state.isProcessingPayment = true;
        state.error = null;
        if (state.currentTransaction) {
          state.currentTransaction.status = 'processing';
        }
      })
      .addCase(initiatePayment.fulfilled, (state, action) => {
        state.isProcessingPayment = false;
        state.transactions.unshift(action.payload);
        if (state.currentTransaction) {
          state.currentTransaction.status = 'confirming';
        }
      })
      .addCase(initiatePayment.rejected, (state, action) => {
        state.isProcessingPayment = false;
        state.error = action.payload as string;
        if (state.currentTransaction) {
          state.currentTransaction.status = 'failed';
          state.currentTransaction.error = action.payload as string;
        }
      });

    // Confirm payment
    builder
      .addCase(confirmPayment.pending, (state) => {
        state.isProcessingPayment = true;
      })
      .addCase(confirmPayment.fulfilled, (state, action) => {
        state.isProcessingPayment = false;
        const transaction = state.transactions.find(t => t.id === action.payload.id);
        if (transaction) {
          Object.assign(transaction, action.payload);
        }
        if (state.currentTransaction) {
          state.currentTransaction.status = 'completed';
        }
        state.successMessage = 'Payment completed successfully';
      })
      .addCase(confirmPayment.rejected, (state, action) => {
        state.isProcessingPayment = false;
        state.error = action.payload as string;
        if (state.currentTransaction) {
          state.currentTransaction.status = 'failed';
          state.currentTransaction.error = action.payload as string;
        }
      });

    // Create payment request
    builder
      .addCase(createPaymentRequest.fulfilled, (state, action) => {
        state.outgoingRequests.unshift(action.payload);
        state.successMessage = 'Payment request sent successfully';
      });

    // Respond to payment request
    builder
      .addCase(respondToPaymentRequest.fulfilled, (state, action) => {
        const request = state.incomingRequests.find(r => r.id === action.payload.id);
        if (request) {
          request.status = action.payload.status;
          request.respondedAt = action.payload.respondedAt;
        }
        state.successMessage = `Payment request ${action.payload.status}`;
      });

    // Create scheduled payment
    builder
      .addCase(createScheduledPayment.fulfilled, (state, action) => {
        state.scheduledPayments.push(action.payload);
        state.successMessage = 'Scheduled payment created successfully';
      });

    // Cancel scheduled payment
    builder
      .addCase(cancelScheduledPayment.fulfilled, (state, action) => {
        state.scheduledPayments = state.scheduledPayments.filter(sp => sp.id !== action.payload);
        state.successMessage = 'Scheduled payment cancelled';
      });

    // Generate QR payment
    builder
      .addCase(generateQRPayment.fulfilled, (state, action) => {
        state.activeQRPayment = action.payload;
        state.qrPaymentHistory.unshift(action.payload);
      });

    // Fetch payment limits
    builder
      .addCase(fetchPaymentLimits.fulfilled, (state, action) => {
        state.paymentLimits = action.payload;
      });
  },
});

export const {
  clearError,
  clearSuccessMessage,
  setTransactionFilters,
  clearTransactionFilters,
  startPaymentFlow,
  updatePaymentFlowStatus,
  clearPaymentFlow,
  updateStatistics,
} = paymentsSlice.actions;

export default paymentsSlice.reducer;