/**
 * Investment Slice - Investment portfolio and trading management
 */

import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiService } from '../../services/ApiService';
import { logError, logInfo } from '../../utils/Logger';

// Types
export interface Stock {
  symbol: string;
  name: string;
  exchange: string;
  currentPrice: number;
  previousClose: number;
  change: number;
  changePercent: number;
  dayHigh: number;
  dayLow: number;
  volume: number;
  marketCap: number;
  peRatio?: number;
  dividendYield?: number;
  fiftyTwoWeekHigh: number;
  fiftyTwoWeekLow: number;
  sector?: string;
  industry?: string;
  isWatchlisted: boolean;
}

export interface Holding {
  id: string;
  symbol: string;
  name: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  totalValue: number;
  totalCost: number;
  unrealizedGainLoss: number;
  unrealizedGainLossPercent: number;
  dayChange: number;
  dayChangePercent: number;
  lastUpdated: string;
}

export interface InvestmentOrder {
  id: string;
  symbol: string;
  type: 'market' | 'limit' | 'stop' | 'stop_limit';
  side: 'buy' | 'sell';
  quantity: number;
  price?: number; // For limit orders
  stopPrice?: number; // For stop orders
  timeInForce: 'day' | 'gtc' | 'ioc' | 'fok'; // Good Till Cancelled, Immediate or Cancel, Fill or Kill
  status: 'pending' | 'partial' | 'filled' | 'cancelled' | 'expired' | 'rejected';
  filledQuantity: number;
  remainingQuantity: number;
  averageFillPrice?: number;
  totalValue: number;
  commission: number;
  createdAt: string;
  updatedAt: string;
  expiresAt?: string;
  rejectionReason?: string;
}

export interface Portfolio {
  totalValue: number;
  totalCost: number;
  dayChange: number;
  dayChangePercent: number;
  totalGainLoss: number;
  totalGainLossPercent: number;
  cashBalance: number;
  buyingPower: number;
  marginUsed: number;
  marginAvailable: number;
  holdings: Holding[];
  diversification: {
    sectors: Array<{
      name: string;
      value: number;
      percentage: number;
    }>;
    assetTypes: Array<{
      type: 'stocks' | 'etfs' | 'options' | 'crypto' | 'cash';
      value: number;
      percentage: number;
    }>;
  };
}

export interface Watchlist {
  id: string;
  name: string;
  symbols: string[];
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MarketNews {
  id: string;
  headline: string;
  summary: string;
  url: string;
  source: string;
  publishedAt: string;
  relatedSymbols: string[];
  sentiment: 'positive' | 'negative' | 'neutral';
  imageUrl?: string;
}

export interface AutoInvestRule {
  id: string;
  name: string;
  isActive: boolean;
  frequency: 'weekly' | 'biweekly' | 'monthly';
  amount: number;
  symbols: Array<{
    symbol: string;
    percentage: number;
  }>;
  nextExecutionDate: string;
  lastExecutionDate?: string;
  totalInvested: number;
  createdAt: string;
}

export interface PriceAlert {
  id: string;
  symbol: string;
  condition: 'above' | 'below' | 'percent_change';
  targetValue: number;
  currentValue: number;
  isActive: boolean;
  isTriggered: boolean;
  triggeredAt?: string;
  createdAt: string;
}

export interface ResearchReport {
  id: string;
  symbol: string;
  analyst: string;
  firm: string;
  recommendation: 'strong_buy' | 'buy' | 'hold' | 'sell' | 'strong_sell';
  targetPrice: number;
  currentPrice: number;
  upside: number;
  summary: string;
  publishedAt: string;
  pdfUrl?: string;
}

export interface InvestmentState {
  // Market data
  stocks: Stock[];
  marketIndices: Array<{
    symbol: string;
    name: string;
    value: number;
    change: number;
    changePercent: number;
  }>;
  
  // Portfolio
  portfolio: Portfolio | null;
  portfolioHistory: Array<{
    date: string;
    value: number;
    dayChange: number;
  }>;
  
  // Trading
  orders: InvestmentOrder[];
  trades: Array<{
    id: string;
    symbol: string;
    side: 'buy' | 'sell';
    quantity: number;
    price: number;
    value: number;
    commission: number;
    executedAt: string;
  }>;
  
  // Watchlists
  watchlists: Watchlist[];
  activeWatchlistId: string | null;
  
  // Research
  news: MarketNews[];
  researchReports: ResearchReport[];
  
  // Auto-investing
  autoInvestRules: AutoInvestRule[];
  
  // Alerts
  priceAlerts: PriceAlert[];
  
  // Current trading
  currentTrade: {
    symbol: string;
    side: 'buy' | 'sell';
    quantity: number;
    orderType: 'market' | 'limit';
    limitPrice?: number;
    estimatedValue: number;
    estimatedCommission: number;
    status: 'idle' | 'calculating' | 'ready' | 'submitting';
  } | null;
  
  // Settings
  settings: {
    defaultOrderType: 'market' | 'limit';
    autoConfirmTrades: boolean;
    enablePushNotifications: boolean;
    riskTolerance: 'conservative' | 'moderate' | 'aggressive';
    investmentGoal: 'growth' | 'income' | 'balanced';
    timeHorizon: 'short' | 'medium' | 'long';
  };
  
  // UI state
  selectedStock: string | null;
  selectedTimeframe: '1D' | '1W' | '1M' | '3M' | '1Y' | '5Y';
  isLoading: boolean;
  isTrading: boolean;
  error: string | null;
  lastUpdated: string | null;
}

// Storage keys
const STORAGE_KEYS = {
  WATCHLISTS: '@waqiti_investment_watchlists',
  SETTINGS: '@waqiti_investment_settings',
  PORTFOLIO_CACHE: '@waqiti_investment_portfolio',
};

// Initial state
const initialState: InvestmentState = {
  stocks: [],
  marketIndices: [],
  portfolio: null,
  portfolioHistory: [],
  orders: [],
  trades: [],
  watchlists: [],
  activeWatchlistId: null,
  news: [],
  researchReports: [],
  autoInvestRules: [],
  priceAlerts: [],
  currentTrade: null,
  settings: {
    defaultOrderType: 'market',
    autoConfirmTrades: false,
    enablePushNotifications: true,
    riskTolerance: 'moderate',
    investmentGoal: 'growth',
    timeHorizon: 'long',
  },
  selectedStock: null,
  selectedTimeframe: '1D',
  isLoading: false,
  isTrading: false,
  error: null,
  lastUpdated: null,
};

// Async thunks
export const fetchPortfolio = createAsyncThunk(
  'investment/fetchPortfolio',
  async (_, { rejectWithValue }) => {
    try {
      const response = await ApiService.get('/investment/portfolio');
      
      // Cache portfolio data
      await AsyncStorage.setItem(STORAGE_KEYS.PORTFOLIO_CACHE, JSON.stringify(response.data));
      
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch investment portfolio', {
        feature: 'investment_slice',
        action: 'fetch_portfolio_failed'
      }, error);
      
      // Try to load from cache
      try {
        const cached = await AsyncStorage.getItem(STORAGE_KEYS.PORTFOLIO_CACHE);
        if (cached) {
          return { ...JSON.parse(cached), fromCache: true };
        }
      } catch (cacheError) {
        logError('Failed to load cached portfolio', { feature: 'investment_slice' }, cacheError as Error);
      }
      
      return rejectWithValue(error.message || 'Failed to fetch investment portfolio');
    }
  }
);

export const fetchStocks = createAsyncThunk(
  'investment/fetchStocks',
  async (symbols?: string[], { rejectWithValue }) => {
    try {
      const params = symbols ? { symbols: symbols.join(',') } : {};
      const response = await ApiService.get('/investment/stocks', { params });
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch stocks', {
        feature: 'investment_slice',
        action: 'fetch_stocks_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch stocks');
    }
  }
);

export const placeOrder = createAsyncThunk(
  'investment/placeOrder',
  async (orderData: {
    symbol: string;
    side: 'buy' | 'sell';
    quantity: number;
    type: 'market' | 'limit' | 'stop' | 'stop_limit';
    price?: number;
    stopPrice?: number;
    timeInForce?: 'day' | 'gtc';
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/investment/orders', orderData);
      
      // Track event
      await ApiService.trackEvent('investment_order_placed', {
        symbol: orderData.symbol,
        side: orderData.side,
        quantity: orderData.quantity,
        type: orderData.type,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to place order', {
        feature: 'investment_slice',
        action: 'place_order_failed'
      }, error);
      
      // Track failed order
      await ApiService.trackEvent('investment_order_failed', {
        symbol: orderData.symbol,
        side: orderData.side,
        error: error.message,
        timestamp: new Date().toISOString(),
      });
      
      return rejectWithValue(error.message || 'Failed to place order');
    }
  }
);

export const cancelOrder = createAsyncThunk(
  'investment/cancelOrder',
  async (orderId: string, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/investment/orders/${orderId}`);
      
      // Track event
      await ApiService.trackEvent('investment_order_cancelled', {
        orderId,
        timestamp: new Date().toISOString(),
      });
      
      return orderId;
    } catch (error: any) {
      logError('Failed to cancel order', {
        feature: 'investment_slice',
        action: 'cancel_order_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to cancel order');
    }
  }
);

export const createWatchlist = createAsyncThunk(
  'investment/createWatchlist',
  async (data: { name: string; symbols?: string[] }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/investment/watchlists', data);
      
      // Track event
      await ApiService.trackEvent('investment_watchlist_created', {
        name: data.name,
        symbolCount: data.symbols?.length || 0,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create watchlist', {
        feature: 'investment_slice',
        action: 'create_watchlist_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create watchlist');
    }
  }
);

export const addToWatchlist = createAsyncThunk(
  'investment/addToWatchlist',
  async (data: { watchlistId: string; symbol: string }, { rejectWithValue }) => {
    try {
      await ApiService.post(`/investment/watchlists/${data.watchlistId}/symbols`, {
        symbol: data.symbol,
      });
      
      // Track event
      await ApiService.trackEvent('investment_symbol_watchlisted', {
        symbol: data.symbol,
        watchlistId: data.watchlistId,
        timestamp: new Date().toISOString(),
      });
      
      return data;
    } catch (error: any) {
      logError('Failed to add to watchlist', {
        feature: 'investment_slice',
        action: 'add_to_watchlist_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to add to watchlist');
    }
  }
);

export const removeFromWatchlist = createAsyncThunk(
  'investment/removeFromWatchlist',
  async (data: { watchlistId: string; symbol: string }, { rejectWithValue }) => {
    try {
      await ApiService.delete(`/investment/watchlists/${data.watchlistId}/symbols/${data.symbol}`);
      
      // Track event
      await ApiService.trackEvent('investment_symbol_unwatchlisted', {
        symbol: data.symbol,
        watchlistId: data.watchlistId,
        timestamp: new Date().toISOString(),
      });
      
      return data;
    } catch (error: any) {
      logError('Failed to remove from watchlist', {
        feature: 'investment_slice',
        action: 'remove_from_watchlist_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to remove from watchlist');
    }
  }
);

export const createAutoInvestRule = createAsyncThunk(
  'investment/createAutoInvestRule',
  async (ruleData: {
    name: string;
    frequency: 'weekly' | 'biweekly' | 'monthly';
    amount: number;
    allocations: Array<{ symbol: string; percentage: number }>;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/investment/auto-invest', ruleData);
      
      // Track event
      await ApiService.trackEvent('investment_auto_invest_created', {
        frequency: ruleData.frequency,
        amount: ruleData.amount,
        symbolCount: ruleData.allocations.length,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create auto-invest rule', {
        feature: 'investment_slice',
        action: 'create_auto_invest_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create auto-invest rule');
    }
  }
);

export const createPriceAlert = createAsyncThunk(
  'investment/createPriceAlert',
  async (alertData: {
    symbol: string;
    condition: 'above' | 'below' | 'percent_change';
    targetValue: number;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/investment/alerts', alertData);
      
      // Track event
      await ApiService.trackEvent('investment_alert_created', {
        symbol: alertData.symbol,
        condition: alertData.condition,
        targetValue: alertData.targetValue,
        timestamp: new Date().toISOString(),
      });
      
      return response.data;
    } catch (error: any) {
      logError('Failed to create price alert', {
        feature: 'investment_slice',
        action: 'create_alert_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to create price alert');
    }
  }
);

export const fetchMarketNews = createAsyncThunk(
  'investment/fetchNews',
  async (symbol?: string, { rejectWithValue }) => {
    try {
      const params = symbol ? { symbol } : {};
      const response = await ApiService.get('/investment/news', { params });
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch market news', {
        feature: 'investment_slice',
        action: 'fetch_news_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch market news');
    }
  }
);

export const fetchResearchReports = createAsyncThunk(
  'investment/fetchResearch',
  async (symbol: string, { rejectWithValue }) => {
    try {
      const response = await ApiService.get(`/investment/research/${symbol}`);
      return response.data;
    } catch (error: any) {
      logError('Failed to fetch research reports', {
        feature: 'investment_slice',
        action: 'fetch_research_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to fetch research reports');
    }
  }
);

export const getOrderQuote = createAsyncThunk(
  'investment/getQuote',
  async (data: {
    symbol: string;
    side: 'buy' | 'sell';
    quantity: number;
    type: 'market' | 'limit';
    price?: number;
  }, { rejectWithValue }) => {
    try {
      const response = await ApiService.post('/investment/quote', data);
      return { ...response.data, ...data };
    } catch (error: any) {
      logError('Failed to get order quote', {
        feature: 'investment_slice',
        action: 'get_quote_failed'
      }, error);
      return rejectWithValue(error.message || 'Failed to get order quote');
    }
  }
);

// Investment slice
const investmentSlice = createSlice({
  name: 'investment',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setSelectedStock: (state, action: PayloadAction<string | null>) => {
      state.selectedStock = action.payload;
    },
    setSelectedTimeframe: (state, action: PayloadAction<'1D' | '1W' | '1M' | '3M' | '1Y' | '5Y'>) => {
      state.selectedTimeframe = action.payload;
    },
    setActiveWatchlist: (state, action: PayloadAction<string | null>) => {
      state.activeWatchlistId = action.payload;
    },
    updateSettings: (state, action: PayloadAction<Partial<InvestmentState['settings']>>) => {
      state.settings = { ...state.settings, ...action.payload };
      AsyncStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(state.settings));
    },
    startTradeFlow: (state, action: PayloadAction<{
      symbol: string;
      side: 'buy' | 'sell';
    }>) => {
      state.currentTrade = {
        ...action.payload,
        quantity: 0,
        orderType: state.settings.defaultOrderType,
        estimatedValue: 0,
        estimatedCommission: 0,
        status: 'idle',
      };
    },
    updateTradeQuantity: (state, action: PayloadAction<number>) => {
      if (state.currentTrade) {
        state.currentTrade.quantity = action.payload;
        state.currentTrade.status = 'calculating';
      }
    },
    updateTradeOrderType: (state, action: PayloadAction<'market' | 'limit'>) => {
      if (state.currentTrade) {
        state.currentTrade.orderType = action.payload;
        if (action.payload === 'market') {
          state.currentTrade.limitPrice = undefined;
        }
      }
    },
    updateTradeLimitPrice: (state, action: PayloadAction<number>) => {
      if (state.currentTrade) {
        state.currentTrade.limitPrice = action.payload;
      }
    },
    updateTradeEstimate: (state, action: PayloadAction<{
      estimatedValue: number;
      estimatedCommission: number;
    }>) => {
      if (state.currentTrade) {
        state.currentTrade.estimatedValue = action.payload.estimatedValue;
        state.currentTrade.estimatedCommission = action.payload.estimatedCommission;
        state.currentTrade.status = 'ready';
      }
    },
    clearTradeFlow: (state) => {
      state.currentTrade = null;
    },
    updateRealTimePrices: (state, action: PayloadAction<Record<string, {
      price: number;
      change: number;
      changePercent: number;
    }>>) => {
      // Update stock prices
      state.stocks.forEach(stock => {
        if (action.payload[stock.symbol]) {
          const priceData = action.payload[stock.symbol];
          stock.currentPrice = priceData.price;
          stock.change = priceData.change;
          stock.changePercent = priceData.changePercent;
        }
      });

      // Update portfolio holdings
      if (state.portfolio) {
        state.portfolio.holdings.forEach(holding => {
          if (action.payload[holding.symbol]) {
            const priceData = action.payload[holding.symbol];
            holding.currentPrice = priceData.price;
            holding.totalValue = holding.quantity * priceData.price;
            holding.unrealizedGainLoss = holding.totalValue - holding.totalCost;
            holding.unrealizedGainLossPercent = (holding.unrealizedGainLoss / holding.totalCost) * 100;
            holding.dayChange = priceData.change * holding.quantity;
            holding.dayChangePercent = priceData.changePercent;
          }
        });

        // Recalculate portfolio totals
        const totalValue = state.portfolio.holdings.reduce((sum, holding) => sum + holding.totalValue, 0);
        const totalCost = state.portfolio.holdings.reduce((sum, holding) => sum + holding.totalCost, 0);
        const dayChange = state.portfolio.holdings.reduce((sum, holding) => sum + holding.dayChange, 0);
        
        state.portfolio.totalValue = totalValue + state.portfolio.cashBalance;
        state.portfolio.totalGainLoss = totalValue - totalCost;
        state.portfolio.totalGainLossPercent = totalCost > 0 ? (state.portfolio.totalGainLoss / totalCost) * 100 : 0;
        state.portfolio.dayChange = dayChange;
        state.portfolio.dayChangePercent = totalValue > 0 ? (dayChange / totalValue) * 100 : 0;
      }

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
    toggleAutoInvestRule: (state, action: PayloadAction<string>) => {
      const rule = state.autoInvestRules.find(r => r.id === action.payload);
      if (rule) {
        rule.isActive = !rule.isActive;
      }
    },
  },
  extraReducers: (builder) => {
    // Fetch portfolio
    builder
      .addCase(fetchPortfolio.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchPortfolio.fulfilled, (state, action) => {
        state.isLoading = false;
        state.portfolio = action.payload.portfolio;
        state.portfolioHistory = action.payload.history || [];
      })
      .addCase(fetchPortfolio.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload as string;
      });

    // Fetch stocks
    builder
      .addCase(fetchStocks.fulfilled, (state, action) => {
        state.stocks = action.payload.stocks;
        state.marketIndices = action.payload.indices || [];
        state.lastUpdated = new Date().toISOString();
      });

    // Place order
    builder
      .addCase(placeOrder.pending, (state) => {
        state.isTrading = true;
        state.error = null;
        if (state.currentTrade) {
          state.currentTrade.status = 'submitting';
        }
      })
      .addCase(placeOrder.fulfilled, (state, action) => {
        state.isTrading = false;
        state.orders.unshift(action.payload);
        state.currentTrade = null;
      })
      .addCase(placeOrder.rejected, (state, action) => {
        state.isTrading = false;
        state.error = action.payload as string;
        if (state.currentTrade) {
          state.currentTrade.status = 'ready';
        }
      });

    // Cancel order
    builder
      .addCase(cancelOrder.fulfilled, (state, action) => {
        const order = state.orders.find(o => o.id === action.payload);
        if (order) {
          order.status = 'cancelled';
        }
      });

    // Create watchlist
    builder
      .addCase(createWatchlist.fulfilled, (state, action) => {
        state.watchlists.push(action.payload);
        if (state.watchlists.length === 1) {
          state.activeWatchlistId = action.payload.id;
        }
      });

    // Add to watchlist
    builder
      .addCase(addToWatchlist.fulfilled, (state, action) => {
        const watchlist = state.watchlists.find(w => w.id === action.payload.watchlistId);
        if (watchlist && !watchlist.symbols.includes(action.payload.symbol)) {
          watchlist.symbols.push(action.payload.symbol);
        }
        
        const stock = state.stocks.find(s => s.symbol === action.payload.symbol);
        if (stock) {
          stock.isWatchlisted = true;
        }
      });

    // Remove from watchlist
    builder
      .addCase(removeFromWatchlist.fulfilled, (state, action) => {
        const watchlist = state.watchlists.find(w => w.id === action.payload.watchlistId);
        if (watchlist) {
          watchlist.symbols = watchlist.symbols.filter(s => s !== action.payload.symbol);
        }
        
        const stock = state.stocks.find(s => s.symbol === action.payload.symbol);
        if (stock) {
          stock.isWatchlisted = false;
        }
      });

    // Create auto-invest rule
    builder
      .addCase(createAutoInvestRule.fulfilled, (state, action) => {
        state.autoInvestRules.push(action.payload);
      });

    // Create price alert
    builder
      .addCase(createPriceAlert.fulfilled, (state, action) => {
        state.priceAlerts.push(action.payload);
      });

    // Fetch market news
    builder
      .addCase(fetchMarketNews.fulfilled, (state, action) => {
        state.news = action.payload.articles;
      });

    // Fetch research reports
    builder
      .addCase(fetchResearchReports.fulfilled, (state, action) => {
        state.researchReports = action.payload.reports;
      });

    // Get order quote
    builder
      .addCase(getOrderQuote.fulfilled, (state, action) => {
        if (state.currentTrade) {
          state.currentTrade.estimatedValue = action.payload.estimatedValue;
          state.currentTrade.estimatedCommission = action.payload.commission;
          state.currentTrade.status = 'ready';
        }
      });
  },
});

export const {
  clearError,
  setSelectedStock,
  setSelectedTimeframe,
  setActiveWatchlist,
  updateSettings,
  startTradeFlow,
  updateTradeQuantity,
  updateTradeOrderType,
  updateTradeLimitPrice,
  updateTradeEstimate,
  clearTradeFlow,
  updateRealTimePrices,
  deletePriceAlert,
  toggleAlertActive,
  toggleAutoInvestRule,
} = investmentSlice.actions;

export default investmentSlice.reducer;