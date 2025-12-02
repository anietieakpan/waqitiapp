import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { cryptoService } from '@/services/cryptoService';
import {
  CryptoWallet,
  CryptoPrice,
  CryptoTransaction,
  CryptoState,
  BuyCryptoRequest,
  SendCryptoRequest,
  SwapCryptoRequest,
  CryptoCurrency,
} from '@/types/crypto';

const initialState: CryptoState = {
  wallets: [],
  prices: {} as Record<CryptoCurrency, CryptoPrice>,
  transactions: [],
  selectedWallet: null,
  loading: false,
  error: null,
};

// Async thunks
export const fetchWallets = createAsyncThunk('crypto/fetchWallets', async () => {
  return await cryptoService.getWallets();
});

export const createWallet = createAsyncThunk(
  'crypto/createWallet',
  async ({ currency, network }: { currency: CryptoCurrency; network?: string }) => {
    return await cryptoService.createWallet(currency, network);
  }
);

export const fetchPrices = createAsyncThunk(
  'crypto/fetchPrices',
  async (currencies?: CryptoCurrency[]) => {
    return await cryptoService.getPrices(currencies);
  }
);

export const buyCrypto = createAsyncThunk(
  'crypto/buy',
  async (request: BuyCryptoRequest) => {
    return await cryptoService.buyCrypto(request);
  }
);

export const sendCrypto = createAsyncThunk(
  'crypto/send',
  async (request: SendCryptoRequest) => {
    return await cryptoService.sendCrypto(request);
  }
);

export const swapCrypto = createAsyncThunk(
  'crypto/swap',
  async (request: SwapCryptoRequest) => {
    return await cryptoService.swapCrypto(request);
  }
);

export const fetchTransactions = createAsyncThunk(
  'crypto/fetchTransactions',
  async (currency?: CryptoCurrency) => {
    return await cryptoService.getTransactions(currency);
  }
);

const cryptoSlice = createSlice({
  name: 'crypto',
  initialState,
  reducers: {
    setSelectedWallet: (state, action: PayloadAction<CryptoWallet | null>) => {
      state.selectedWallet = action.payload;
    },
    clearError: (state) => {
      state.error = null;
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
      })
      .addCase(fetchWallets.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch wallets';
      });

    // Create wallet
    builder
      .addCase(createWallet.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(createWallet.fulfilled, (state, action) => {
        state.loading = false;
        state.wallets.push(action.payload);
      })
      .addCase(createWallet.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to create wallet';
      });

    // Fetch prices
    builder
      .addCase(fetchPrices.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchPrices.fulfilled, (state, action) => {
        state.loading = false;
        state.prices = action.payload as Record<CryptoCurrency, CryptoPrice>;
      })
      .addCase(fetchPrices.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch prices';
      });

    // Buy crypto
    builder
      .addCase(buyCrypto.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(buyCrypto.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions.unshift(action.payload);
      })
      .addCase(buyCrypto.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to buy crypto';
      });

    // Send crypto
    builder
      .addCase(sendCrypto.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(sendCrypto.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions.unshift(action.payload);
      })
      .addCase(sendCrypto.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to send crypto';
      });

    // Swap crypto
    builder
      .addCase(swapCrypto.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(swapCrypto.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions.unshift(action.payload);
      })
      .addCase(swapCrypto.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to swap crypto';
      });

    // Fetch transactions
    builder
      .addCase(fetchTransactions.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchTransactions.fulfilled, (state, action) => {
        state.loading = false;
        state.transactions = action.payload;
      })
      .addCase(fetchTransactions.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch transactions';
      });
  },
});

export const { setSelectedWallet, clearError } = cryptoSlice.actions;
export default cryptoSlice.reducer;
