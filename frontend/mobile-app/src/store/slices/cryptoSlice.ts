/**
 * Crypto Slice - Cryptocurrency trading and wallet management
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface CryptoCurrency {
  symbol: string;
  name: string;
  icon: string;
  currentPrice: number;
  priceChange24h: number;
  priceChangePercentage24h: number;
  marketCap: number;
  volume24h: number;
  rank: number;
  isSupported: boolean;
  minTradeAmount: number;
  maxTradeAmount: number;
}

export interface CryptoWallet {
  id: string;
  currency: string;
  address: string;
  balance: number;
  balanceUSD: number;
  isActive: boolean;
  publicKey?: string;
  label?: string;
  network: string;
  createdAt: string;
}

export interface CryptoTransaction {
  id: string;
  type: 'buy' | 'sell' | 'send' | 'receive' | 'swap' | 'stake' | 'unstake';
  status: 'pending' | 'confirming' | 'completed' | 'failed' | 'cancelled';
  fromCurrency: string;
  toCurrency: string;
  fromAmount: number;
  toAmount: number;
  exchangeRate: number;
  fee: number;
  feeUSD: number;
  txHash?: string;
  blockHeight?: number;
  confirmations?: number;
  requiredConfirmations?: number;
  fromAddress?: string;
  toAddress?: string;
  createdAt: string;
  completedAt?: string;
  failureReason?: string;
}

export interface TradeOrder {
  id: string;
  type: 'market' | 'limit' | 'stop_loss' | 'take_profit';
  side: 'buy' | 'sell';
  status: 'pending' | 'partial' | 'filled' | 'cancelled' | 'expired';
  baseCurrency: string;
  quoteCurrency: string;
  amount: number;
  price?: number; // For limit orders
  stopPrice?: number; // For stop orders
  filledAmount: number;
  remainingAmount: number;
  averagePrice?: number;
  totalValue: number;
  fee: number;
  createdAt: string;
  expiresAt?: string;
  filledAt?: string;
}

export interface CryptoPortfolio {
  totalValue: number;
  totalValueUSD: number;
  dayChange: number;
  dayChangePercentage: number;
  holdings: Array<{
    currency: string;
    amount: number;
    valueUSD: number;
    percentage: number;
    avgBuyPrice: number;
    currentPrice: number;
    profitLoss: number;
    profitLossPercentage: number;
  }>;
}

export interface PriceAlert {
  id: string;
  currency: string;
  condition: 'above' | 'below';
  targetPrice: number;
  currentPrice: number;
  isActive: boolean;
  isTriggered: boolean;
  triggeredAt?: string;
  createdAt: string;
}

export interface StakingPosition {
  id: string;
  currency: string;
  amount: number;
  apy: number;
  duration: number; // days
  startDate: string;
  endDate: string;
  status: 'active' | 'pending' | 'completed' | 'cancelled';
  earnedRewards: number;
  estimatedRewards: number;
}

export interface CryptoNewsItem {
  id: string;
  title: string;
  summary: string;
  url: string;
  source: string;
  publishedAt: string;
  sentiment: 'positive' | 'negative' | 'neutral';
  relatedCurrencies: string[];
  imageUrl?: string;
}

export interface CryptoState {
  // Market data
  currencies: CryptoCurrency[];
  watchlist: string[];
  priceHistory: Record<string, Array<{ timestamp: number; price: number }>>;
  
  // Wallets
  wallets: CryptoWallet[];
  portfolio: CryptoPortfolio | null;
  
  // Trading
  trades: CryptoTransaction[];
  orders: TradeOrder[];
  tradingPairs: Array<{
    base: string;
    quote: string;
    minTradeAmount: number;
    maxTradeAmount: number;
    fee: number;
  }>;
  
  // Current trade
  currentTrade: {
    type: 'buy' | 'sell' | 'swap';
    fromCurrency: string;
    toCurrency: string;
    amount: number;
    estimatedResult: number;
    fee: number;
    status: 'idle' | 'calculating' | 'confirming' | 'processing';
  } | null;
  
  // Alerts
  priceAlerts: PriceAlert[];
  
  // Staking
  stakingPositions: StakingPosition[];
  stakingRewards: Array<{
    currency: string;
    amount: number;
    date: string;
    stakingPositionId: string;
  }>;
  
  // News
  news: CryptoNewsItem[];
  
  // Settings
  settings: {
    defaultCurrency: string;
    showPortfolioValue: boolean;
    priceChangeTimeframe: '1h' | '24h' | '7d';
    enablePushNotifications: boolean;
    enablePriceAlerts: boolean;
    riskLevel: 'conservative' | 'moderate' | 'aggressive';
  };
  
  // UI state
  selectedCurrency: string | null;
  selectedTimeframe: '1h' | '24h' | '7d' | '30d' | '1y';
  isLoading: boolean;
  isTrading: boolean;
  error: string | null;
  lastUpdated: string | null;
}

// Storage keys
const STORAGE_KEYS = {
  WATCHLIST: '@waqiti_crypto_watchlist',
  SETTINGS: '@waqiti_crypto_settings',
  PORTFOLIO_CACHE: '@waqiti_crypto_portfolio',
};

// Initial state
const initialState: CryptoState = {
  currencies: [],
  watchlist: ['BTC', 'ETH', 'ADA', 'DOT', 'LINK'],
  priceHistory: {},
  wallets: [],
  portfolio: null,
  trades: [],
  orders: [],
  tradingPairs: [],
  currentTrade: null,
  priceAlerts: [],
  stakingPositions: [],
  stakingRewards: [],
  news: [],
  settings: {
    defaultCurrency: 'USD',
    showPortfolioValue: true,
    priceChangeTimeframe: '24h',
    enablePushNotifications: true,
    enablePriceAlerts: true,
    riskLevel: 'moderate',
  },
  selectedCurrency: null,
  selectedTimeframe: '24h',
  isLoading: false,
  isTrading: false,
  error: null,
  lastUpdated: null,
};

// Async thunks
export const fetchCryptoCurrencies = createAsyncThunk(
  'crypto/fetchCurrencies',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/crypto/currencies');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch crypto currencies', {
        feature: 'crypto_slice',
        action: 'fetch_currencies_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch crypto currencies');
    }
  }
);

export const fetchCryptoWallets = createAsyncThunk(
  'crypto/fetchWallets',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/crypto/wallets');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch crypto wallets', {
        feature: 'crypto_slice',
        action: 'fetch_wallets_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch crypto wallets');
    }
  }
);

export const createCryptoWallet = createAsyncThunk(
  'crypto/createWallet',
  async (data: {
    currency: string;
    label?: string;
    network?: string;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/crypto/wallets', data);
      
      // Track event
      await ApiService.trackEvent('crypto_wallet_created', {
        currency: data.currency,
        network: data.network,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create crypto wallet', {
        feature: 'crypto_slice',
        action: 'create_wallet_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create crypto wallet');
    }
  }
);

export const fetchPortfolio = createAsyncThunk(
  'crypto/fetchPortfolio',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/crypto/portfolio');
      
      // Cache portfolio data
      await AsyncStorage.setItem(STORAGE_KEYS.PORTFOLIO_CACHE, JSON.stringify(response.data));
      
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch crypto portfolio', {
        feature: 'crypto_slice',
        action: 'fetch_portfolio_failed'
      }, error);
      
      // Try to load from cache
      try {
        const cached = await AsyncStorage.getItem(STORAGE_KEYS.PORTFOLIO_CACHE);
        if (cached) {
          return JSON.parse(cached);
        }
      } catch (cacheError) {
        logError('Failed to load cached portfolio', { feature: 'crypto_slice' }, cacheError as Error);
      }
      
      return rejectWithValue(error.message || 'Failed to fetch crypto portfolio');
    }
  }
);

export const executeTrade = createAsyncThunk(
  'crypto/executeTrade',
  async (tradeData: {
    type: 'buy' | 'sell' | 'swap';
    fromCurrency: string;
    toCurrency: string;
    amount: number;
    orderType?: 'market' | 'limit';
    limitPrice?: number;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/crypto/trade', tradeData);
      
      // Track event
      await ApiService.trackEvent('crypto_trade_executed', {
        type: tradeData.type,
        fromCurrency: tradeData.fromCurrency,
        toCurrency: tradeData.toCurrency,
        amount: tradeData.amount,
        orderType: tradeData.orderType,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to execute crypto trade', {
        feature: 'crypto_slice',
        action: 'execute_trade_failed'
      }, error);
      
      // Track failed trade
      await ApiService.trackEvent('crypto_trade_failed', {
        type: tradeData.type,
        fromCurrency: tradeData.fromCurrency,
        toCurrency: tradeData.toCurrency,
        error: error.message,
        timestamp: new Date().toISOString(),
      });
      
      return rejectWithValue(error.message || 'Failed to execute crypto trade');
    }
  }
);

export const getTradeQuote = createAsyncThunk(
  'crypto/getQuote',
  async (data: {
    fromCurrency: string;
    toCurrency: string;
    amount: number;
    type: 'buy' | 'sell' | 'swap';
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/crypto/quote', data);
      return { ...response.data, ...data };
    } catch (error: any) {
      logError('Failed to get trade quote', {
        feature: 'crypto_slice',
        action: 'get_quote_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to get trade quote');
    }
  }
);

export const sendCrypto = createAsyncThunk(
  'crypto/send',
  async (data: {
    currency: string;
    amount: number;
    toAddress: string;
    memo?: string;
    fee?: number;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/crypto/send', data);
      
      // Track event
      await ApiService.trackEvent('crypto_sent', {
        currency: data.currency,
        amount: data.amount,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to send crypto', {
        feature: 'crypto_slice',
        action: 'send_crypto_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to send crypto');
    }
  }
);

export const createPriceAlert = createAsyncThunk(
  'crypto/createAlert',
  async (alertData: {
    currency: string;
    condition: 'above' | 'below';
    targetPrice: number;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/crypto/alerts', alertData);
      
      // Track event
      await ApiService.trackEvent('crypto_alert_created', {
        currency: alertData.currency,
        condition: alertData.condition,
        targetPrice: alertData.targetPrice,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create price alert', {
        feature: 'crypto_slice',
        action: 'create_alert_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create price alert');
    }
  }
);

export const startStaking = createAsyncThunk(
  'crypto/startStaking',
  async (data: {
    currency: string;
    amount: number;
    duration: number; // days
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/crypto/staking', data);
      
      // Track event
      await ApiService.trackEvent('crypto_staking_started', {
        currency: data.currency,
        amount: data.amount,
        duration: data.duration,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to start staking', {
        feature: 'crypto_slice',
        action: 'start_staking_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to start staking');
    }
  }
);

export const fetchCryptoNews = createAsyncThunk(
  'crypto/fetchNews',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/crypto/news');
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch crypto news', {
        feature: 'crypto_slice',
        action: 'fetch_news_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch crypto news');
    }
  }
);

export const fetchPriceHistory = createAsyncThunk(
  'crypto/fetchPriceHistory',
  async (data: {
    currency: string;
    timeframe: '1h' | '24h' | '7d' | '30d' | '1y';
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.get(`/crypto/price-history/${data.currency}`, {
        params: { timeframe: data.timeframe },
      });
      return { currency: data.currency, history: response.data };
    } catch (error: any) {
      logError('Failed to fetch price history', {
        feature: 'crypto_slice',
        action: 'fetch_price_history_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch price history');
    }
  }
);

// Crypto slice
const cryptoSlice = createSlice({
  name: 'crypto',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setSelectedCurrency: (state, action: PayloadAction<string | null>) => {
      state.selectedCurrency = action.payload;
    },
    setSelectedTimeframe: (state, action: PayloadAction<'1h' | '24h' | '7d' | '30d' | '1y'>) => {
      state.selectedTimeframe = action.payload;
    },
    addToWatchlist: (state, action: PayloadAction<string>) => {
      if (!state.watchlist.includes(action.payload)) {
        state.watchlist.push(action.payload);
        AsyncStorage.setItem(STORAGE_KEYS.WATCHLIST, JSON.stringify(state.watchlist));
      }
    },
    removeFromWatchlist: (state, action: PayloadAction<string>) => {
      state.watchlist = state.watchlist.filter(symbol => symbol !== action.payload);
      AsyncStorage.setItem(STORAGE_KEYS.WATCHLIST, JSON.stringify(state.watchlist));
    },
    updateSettings: (state, action: PayloadAction<Partial<CryptoState['settings']>>) => {
      state.settings = { ...state.settings, ...action.payload };
      AsyncStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(state.settings));
    },
    startTradeFlow: (state, action: PayloadAction<{
      type: 'buy' | 'sell' | 'swap';
      fromCurrency: string;
      toCurrency: string;
    }>) => {
      state.currentTrade = {
        ...action.payload,
        amount: 0,
        estimatedResult: 0,
        fee: 0,
        status: 'idle',
      };
    },
    updateTradeAmount: (state, action: PayloadAction<number>) => {
      if (state.currentTrade) {
        state.currentTrade.amount = action.payload;
        state.currentTrade.status = 'calculating';
      }
    },
    updateTradeEstimate: (state, action: PayloadAction<{
      estimatedResult: number;
      fee: number;
    }>) => {
      if (state.currentTrade) {
        state.currentTrade.estimatedResult = action.payload.estimatedResult;
        state.currentTrade.fee = action.payload.fee;
        state.currentTrade.status = 'idle';
      }
    },
    clearTradeFlow: (state) => {
      state.currentTrade = null;
    },
    updateRealTimePrices: (state, action: PayloadAction<Record<string, number>>) => {
      state.currencies.forEach(currency => {
        if (action.payload[currency.symbol]) {
          const newPrice = action.payload[currency.symbol];
          const oldPrice = currency.currentPrice;
          currency.currentPrice = newPrice;
          currency.priceChange24h = newPrice - oldPrice;
          currency.priceChangePercentage24h = ((newPrice - oldPrice) / oldPrice) * 100;
        }
      });
      state.lastUpdated = new Date().toISOString();
    },
    deletePriceAlert: (state, action: PayloadAction<string>) => {
      state.priceAlerts = state.priceAlerts.filter(alert => alert.id !== action.payload);
    },
    toggleAlertActive: (state, action: PayloadAction<string>) => {
      const alert = state.priceAlerts.find(a => a.id === action.payload);
      if (alert) {
        alert.isActive = !alert.isActive;
      }
    },
  },
  extraReducers: (builder) => {
    // Fetch crypto currencies
    builder
      .addCase(fetchCryptoCurrencies.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchCryptoCurrencies.fulfilled, (state, action) => {
        state.isLoading = false;
        state.currencies = action.payload.currencies;
        state.tradingPairs = action.payload.tradingPairs || [];
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchCryptoCurrencies.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Fetch crypto wallets
    builder
      .addCase(fetchCryptoWallets.fulfilled, (state, action) => {
        state.wallets = action.payload.wallets;
      });

    // Create crypto wallet
    builder
      .addCase(createCryptoWallet.fulfilled, (state, action) => {
        state.wallets.push(action.payload);
      });

    // Fetch portfolio
    builder
      .addCase(fetchPortfolio.fulfilled, (state, action) => {
        state.portfolio = action.payload;
      });

    // Execute trade
    builder
      .addCase(executeTrade.pending, (state) => {
        state.isTrading = true;
        state.error = null;
        if (state.currentTrade) {
          state.currentTrade.status = 'processing';
        }
      })
      .addCase(executeTrade.fulfilled, (state, action) => {
        state.isTrading = false;
        state.trades.unshift(action.payload);
        state.currentTrade = null;
      })
      .addCase(executeTrade.rejected, (state, action) => {
        state.isTrading = false;
        state.error = action.payload as string;
        if (state.currentTrade) {
          state.currentTrade.status = 'idle';
        }
      });

    // Get trade quote
    builder
      .addCase(getTradeQuote.fulfilled, (state, action) => {
        if (state.currentTrade) {
          state.currentTrade.estimatedResult = action.payload.estimatedAmount;
          state.currentTrade.fee = action.payload.fee;
          state.currentTrade.status = 'idle';
        }
      });

    // Send crypto
    builder
      .addCase(sendCrypto.fulfilled, (state, action) => {
        state.trades.unshift(action.payload);
      });

    // Create price alert
    builder
      .addCase(createPriceAlert.fulfilled, (state, action) => {
        state.priceAlerts.push(action.payload);
      });

    // Start staking
    builder
      .addCase(startStaking.fulfilled, (state, action) => {
        state.stakingPositions.push(action.payload);
      });

    // Fetch crypto news
    builder
      .addCase(fetchCryptoNews.fulfilled, (state, action) => {
        state.news = action.payload.articles;
      });

    // Fetch price history
    builder
      .addCase(fetchPriceHistory.fulfilled, (state, action) => {
        state.priceHistory[action.payload.currency] = action.payload.history;
      });
  },
});

export const {
  clearError,
  setSelectedCurrency,
  setSelectedTimeframe,
  addToWatchlist,
  removeFromWatchlist,
  updateSettings,
  startTradeFlow,
  updateTradeAmount,
  updateTradeEstimate,
  clearTradeFlow,
  updateRealTimePrices,
  deletePriceAlert,
  toggleAlertActive,
} = cryptoSlice.actions;

export default cryptoSlice.reducer;