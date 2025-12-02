import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { paymentService } from '../../services/paymentService';
import { PaymentMethod, Payment, PaymentRequest, Transaction } from '../../types/payment';

interface PaymentState {
  paymentMethods: PaymentMethod[];
  paymentMethodsLoading: boolean;
  paymentRequestsLoading: boolean;
  transactionsLoading: boolean;
  sendPaymentLoading: boolean;
  
  sentPaymentRequests: PaymentRequest[];
  receivedPaymentRequests: PaymentRequest[];
  moneyRequests: any[]; // All money requests
  
  transactions: Transaction[];
  transactionsPagination: {
    currentPage: number;
    totalPages: number;
    totalCount: number;
  };
  
  selectedPaymentMethod: string | null;
  
  // Recent activities
  recentTransactions: Transaction[];
  
  // Search and recipients
  searchResults: any[];
  frequentRecipients: any[];
  
  // QR Code
  qrCode: any | null;
  
  // UI state
  showPaymentMethodDialog: boolean;
  showSendMoneyDialog: boolean;
  showRequestMoneyDialog: boolean;
  
  error: string | null;
  loading: boolean;
}

const initialState: PaymentState = {
  paymentMethods: [],
  paymentMethodsLoading: false,
  paymentRequestsLoading: false,
  transactionsLoading: false,
  sendPaymentLoading: false,
  
  sentPaymentRequests: [],
  receivedPaymentRequests: [],
  moneyRequests: [],
  
  transactions: [],
  transactionsPagination: {
    currentPage: 0,
    totalPages: 0,
    totalCount: 0,
  },
  
  selectedPaymentMethod: null,
  recentTransactions: [],
  
  searchResults: [],
  frequentRecipients: [],
  qrCode: null,
  
  showPaymentMethodDialog: false,
  showSendMoneyDialog: false,
  showRequestMoneyDialog: false,
  
  error: null,
  loading: false,
};

// Async thunks
export const fetchPaymentMethods = createAsyncThunk(
  'payment/fetchPaymentMethods',
  async (_, { rejectWithValue }) => {
    try {
      return await paymentService.getPaymentMethods();
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const addPaymentMethod = createAsyncThunk(
  'payment/addPaymentMethod',
  async (method: any, { rejectWithValue }) => {
    try {
      return await paymentService.addPaymentMethod(method);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const deletePaymentMethod = createAsyncThunk(
  'payment/deletePaymentMethod',
  async (methodId: string, { rejectWithValue }) => {
    try {
      await paymentService.deletePaymentMethod(methodId);
      return methodId;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const setDefaultPaymentMethod = createAsyncThunk(
  'payment/setDefaultPaymentMethod',
  async (methodId: string, { rejectWithValue }) => {
    try {
      await paymentService.setDefaultPaymentMethod(methodId);
      return methodId;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const sendMoney = createAsyncThunk(
  'payment/sendMoney',
  async (request: any, { rejectWithValue }) => {
    try {
      return await paymentService.sendMoney(request);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const requestMoney = createAsyncThunk(
  'payment/requestMoney',
  async (request: any, { rejectWithValue }) => {
    try {
      return await paymentService.requestMoney(request);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const fetchTransactions = createAsyncThunk(
  'payment/fetchTransactions',
  async (params: any, { rejectWithValue }) => {
    try {
      return await paymentService.getTransactions(params);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const fetchPaymentRequests = createAsyncThunk(
  'payment/fetchPaymentRequests',
  async (type: 'sent' | 'received', { rejectWithValue }) => {
    try {
      return await paymentService.getPaymentRequests(type);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const acceptPaymentRequest = createAsyncThunk(
  'payment/acceptPaymentRequest',
  async (requestId: string, { rejectWithValue }) => {
    try {
      return await paymentService.payRequest(requestId);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const declinePaymentRequest = createAsyncThunk(
  'payment/declinePaymentRequest',
  async (requestId: string, { rejectWithValue }) => {
    try {
      await paymentService.declineRequest(requestId);
      return requestId;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const searchUsers = createAsyncThunk(
  'payment/searchUsers',
  async (query: string, { rejectWithValue }) => {
    try {
      return await paymentService.searchUsers(query);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const getFrequentRecipients = createAsyncThunk(
  'payment/getFrequentRecipients',
  async (_, { rejectWithValue }) => {
    try {
      return await paymentService.getFrequentRecipients();
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const getMoneyRequests = createAsyncThunk(
  'payment/getMoneyRequests',
  async (_, { rejectWithValue }) => {
    try {
      return await paymentService.getMoneyRequests();
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const cancelMoneyRequest = createAsyncThunk(
  'payment/cancelMoneyRequest',
  async (requestId: string, { rejectWithValue }) => {
    try {
      await paymentService.cancelMoneyRequest(requestId);
      return requestId;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const generateQrCode = createAsyncThunk(
  'payment/generateQrCode',
  async (data: {
    type: 'static' | 'dynamic';
    amount?: number;
    note?: string;
    expiresIn?: number;
  }, { rejectWithValue }) => {
    try {
      return await paymentService.generateQrCode(data);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const scanQrCode = createAsyncThunk(
  'payment/scanQrCode',
  async (qrData: string, { rejectWithValue }) => {
    try {
      return await paymentService.scanQrCode(qrData);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const createQrPayment = createAsyncThunk(
  'payment/createQrPayment',
  async (data: {
    qrCode: string;
    amount: number;
    pin: string;
    note?: string;
  }, { rejectWithValue }) => {
    try {
      return await paymentService.createQrPayment(data);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

export const generateRequestLink = createAsyncThunk(
  'payment/generateRequestLink',
  async (requestId: string, { rejectWithValue }) => {
    try {
      return await paymentService.generateRequestLink(requestId);
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

const paymentSlice = createSlice({
  name: 'payment',
  initialState,
  reducers: {
    setSelectedPaymentMethod: (state, action: PayloadAction<string | null>) => {
      state.selectedPaymentMethod = action.payload;
    },
    
    setShowPaymentMethodDialog: (state, action: PayloadAction<boolean>) => {
      state.showPaymentMethodDialog = action.payload;
    },
    
    setShowSendMoneyDialog: (state, action: PayloadAction<boolean>) => {
      state.showSendMoneyDialog = action.payload;
    },
    
    setShowRequestMoneyDialog: (state, action: PayloadAction<boolean>) => {
      state.showRequestMoneyDialog = action.payload;
    },
    
    clearError: (state) => {
      state.error = null;
    },
    
    updatePaymentMethod: (state, action: PayloadAction<PaymentMethod>) => {
      const index = state.paymentMethods.findIndex(m => m.id === action.payload.id);
      if (index !== -1) {
        state.paymentMethods[index] = action.payload;
      }
    },
    
    addTransaction: (state, action: PayloadAction<Transaction>) => {
      state.transactions.unshift(action.payload);
      state.recentTransactions = state.transactions.slice(0, 10);
    },
    
    updateTransaction: (state, action: PayloadAction<Transaction>) => {
      const index = state.transactions.findIndex(t => t.id === action.payload.id);
      if (index !== -1) {
        state.transactions[index] = action.payload;
      }
    },
  },
  
  extraReducers: (builder) => {
    builder
      // Fetch payment methods
      .addCase(fetchPaymentMethods.pending, (state) => {
        state.paymentMethodsLoading = true;
        state.error = null;
      })
      .addCase(fetchPaymentMethods.fulfilled, (state, action) => {
        state.paymentMethodsLoading = false;
        state.paymentMethods = action.payload;
        
        // Auto-select default method if none selected
        if (!state.selectedPaymentMethod && action.payload.length > 0) {
          const defaultMethod = action.payload.find(m => m.isDefault);
          state.selectedPaymentMethod = defaultMethod?.id || action.payload[0].id;
        }
      })
      .addCase(fetchPaymentMethods.rejected, (state, action) => {
        state.paymentMethodsLoading = false;
        state.error = action.payload as string;
      })
      
      // Add payment method
      .addCase(addPaymentMethod.fulfilled, (state, action) => {
        state.paymentMethods.push(action.payload);
        if (action.payload.isDefault) {
          state.selectedPaymentMethod = action.payload.id;
        }
      })
      .addCase(addPaymentMethod.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Delete payment method
      .addCase(deletePaymentMethod.fulfilled, (state, action) => {
        state.paymentMethods = state.paymentMethods.filter(m => m.id !== action.payload);
        if (state.selectedPaymentMethod === action.payload) {
          state.selectedPaymentMethod = state.paymentMethods[0]?.id || null;
        }
      })
      .addCase(deletePaymentMethod.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Set default payment method
      .addCase(setDefaultPaymentMethod.fulfilled, (state, action) => {
        state.paymentMethods = state.paymentMethods.map(m => ({
          ...m,
          isDefault: m.id === action.payload,
        }));
        state.selectedPaymentMethod = action.payload;
      })
      
      // Send money
      .addCase(sendMoney.pending, (state) => {
        state.sendPaymentLoading = true;
        state.error = null;
      })
      .addCase(sendMoney.fulfilled, (state, action) => {
        state.sendPaymentLoading = false;
        state.transactions.unshift(action.payload);
        state.recentTransactions = state.transactions.slice(0, 10);
      })
      .addCase(sendMoney.rejected, (state, action) => {
        state.sendPaymentLoading = false;
        state.error = action.payload as string;
      })
      
      // Request money
      .addCase(requestMoney.fulfilled, (state, action) => {
        state.sentPaymentRequests.unshift(action.payload);
      })
      .addCase(requestMoney.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Fetch transactions
      .addCase(fetchTransactions.pending, (state) => {
        state.transactionsLoading = true;
        state.error = null;
      })
      .addCase(fetchTransactions.fulfilled, (state, action) => {
        state.transactionsLoading = false;
        state.transactions = action.payload.transactions;
        state.transactionsPagination = {
          currentPage: action.payload.currentPage || 0,
          totalPages: action.payload.totalPages || 0,
          totalCount: action.payload.totalCount || 0,
        };
        state.recentTransactions = action.payload.transactions.slice(0, 10);
      })
      .addCase(fetchTransactions.rejected, (state, action) => {
        state.transactionsLoading = false;
        state.error = action.payload as string;
      })
      
      // Fetch payment requests
      .addCase(fetchPaymentRequests.pending, (state) => {
        state.paymentRequestsLoading = true;
      })
      .addCase(fetchPaymentRequests.fulfilled, (state, action) => {
        state.paymentRequestsLoading = false;
        // Note: You would need to pass the type as part of the action to determine which array to update
        state.receivedPaymentRequests = action.payload;
      })
      .addCase(fetchPaymentRequests.rejected, (state, action) => {
        state.paymentRequestsLoading = false;
        state.error = action.payload as string;
      })
      
      // Accept payment request
      .addCase(acceptPaymentRequest.fulfilled, (state, action) => {
        state.receivedPaymentRequests = state.receivedPaymentRequests.filter(
          r => r.id !== action.meta.arg
        );
        state.transactions.unshift(action.payload);
        state.recentTransactions = state.transactions.slice(0, 10);
      })
      
      // Decline payment request
      .addCase(declinePaymentRequest.fulfilled, (state, action) => {
        state.receivedPaymentRequests = state.receivedPaymentRequests.filter(
          r => r.id !== action.payload
        );
      })
      
      // Search users
      .addCase(searchUsers.pending, (state) => {
        state.loading = true;
      })
      .addCase(searchUsers.fulfilled, (state, action) => {
        state.loading = false;
        state.searchResults = action.payload;
      })
      .addCase(searchUsers.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // Get frequent recipients
      .addCase(getFrequentRecipients.pending, (state) => {
        state.loading = true;
      })
      .addCase(getFrequentRecipients.fulfilled, (state, action) => {
        state.loading = false;
        state.frequentRecipients = action.payload;
      })
      .addCase(getFrequentRecipients.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // Get money requests
      .addCase(getMoneyRequests.pending, (state) => {
        state.loading = true;
      })
      .addCase(getMoneyRequests.fulfilled, (state, action) => {
        state.loading = false;
        state.moneyRequests = action.payload;
      })
      .addCase(getMoneyRequests.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // Cancel money request
      .addCase(cancelMoneyRequest.fulfilled, (state, action) => {
        state.moneyRequests = state.moneyRequests.filter(
          r => r.id !== action.payload
        );
      })
      .addCase(cancelMoneyRequest.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Generate QR code
      .addCase(generateQrCode.pending, (state) => {
        state.loading = true;
      })
      .addCase(generateQrCode.fulfilled, (state, action) => {
        state.loading = false;
        state.qrCode = action.payload;
      })
      .addCase(generateQrCode.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // Scan QR code
      .addCase(scanQrCode.fulfilled, (state, action) => {
        // QR code scan results are typically handled in components
        // This could update searchResults or navigate to payment
      })
      .addCase(scanQrCode.rejected, (state, action) => {
        state.error = action.payload as string;
      })
      
      // Create QR payment
      .addCase(createQrPayment.pending, (state) => {
        state.sendPaymentLoading = true;
      })
      .addCase(createQrPayment.fulfilled, (state, action) => {
        state.sendPaymentLoading = false;
        // Add the new transaction
        state.transactions.unshift(action.payload);
        state.recentTransactions = state.transactions.slice(0, 10);
      })
      .addCase(createQrPayment.rejected, (state, action) => {
        state.sendPaymentLoading = false;
        state.error = action.payload as string;
      })
      
      // Generate request link
      .addCase(generateRequestLink.fulfilled, (state, action) => {
        // Update the money request with the generated link
        const index = state.moneyRequests.findIndex(r => r.id === action.meta.arg);
        if (index !== -1) {
          state.moneyRequests[index] = {
            ...state.moneyRequests[index],
            paymentLink: action.payload
          };
        }
      })
      .addCase(generateRequestLink.rejected, (state, action) => {
        state.error = action.payload as string;
      });
  },
});

export const {
  setSelectedPaymentMethod,
  setShowPaymentMethodDialog,
  setShowSendMoneyDialog,
  setShowRequestMoneyDialog,
  clearError,
  updatePaymentMethod,
  addTransaction,
  updateTransaction,
} = paymentSlice.actions;

export default paymentSlice.reducer;