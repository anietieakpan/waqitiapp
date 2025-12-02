import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import investmentService, {
  InvestmentAccount,
  Portfolio,
  InvestmentHolding,
  InvestmentOrder,
  WatchlistItem,
  AutoInvestPlan,
  MarketData,
  CreateOrderRequest
} from '../../services/investmentService';

interface InvestmentState {
  account: InvestmentAccount | null;
  portfolio: Portfolio | null;
  holdings: InvestmentHolding[];
  orders: InvestmentOrder[];
  activeOrders: InvestmentOrder[];
  watchlist: WatchlistItem[];
  autoInvestPlans: AutoInvestPlan[];
  marketData: { [symbol: string]: MarketData };
  marketMovers: {
    gainers: MarketData[];
    losers: MarketData[];
    active: MarketData[];
  };
  searchResults: MarketData[];
  loading: {
    account: boolean;
    portfolio: boolean;
    orders: boolean;
    trading: boolean;
    marketData: boolean;
  };
  error: string | null;
  tradingStatus: {
    placing: boolean;
    cancelling: boolean;
    lastOrderId?: string;
  };
}

const initialState: InvestmentState = {
  account: null,
  portfolio: null,
  holdings: [],
  orders: [],
  activeOrders: [],
  watchlist: [],
  autoInvestPlans: [],
  marketData: {},
  marketMovers: {
    gainers: [],
    losers: [],
    active: []
  },
  searchResults: [],
  loading: {
    account: false,
    portfolio: false,
    orders: false,
    trading: false,
    marketData: false
  },
  error: null,
  tradingStatus: {
    placing: false,
    cancelling: false
  }
};

// Async thunks
export const fetchAccount = createAsyncThunk(
  'investment/fetchAccount',
  async (accountId: string) => {
    return await investmentService.getAccount(accountId);
  }
);

export const fetchAccountByCustomer = createAsyncThunk(
  'investment/fetchAccountByCustomer',
  async (customerId: string) => {
    return await investmentService.getAccountByCustomer(customerId);
  }
);

export const createAccount = createAsyncThunk(
  'investment/createAccount',
  async (request: any) => {
    return await investmentService.createAccount(request);
  }
);

export const fundAccount = createAsyncThunk(
  'investment/fundAccount',
  async ({ accountId, amount }: { accountId: string; amount: number }) => {
    return await investmentService.fundAccount(accountId, amount);
  }
);

export const withdrawFunds = createAsyncThunk(
  'investment/withdrawFunds',
  async ({ accountId, amount }: { accountId: string; amount: number }) => {
    return await investmentService.withdrawFunds(accountId, amount);
  }
);

export const fetchPortfolio = createAsyncThunk(
  'investment/fetchPortfolio',
  async (accountId: string) => {
    return await investmentService.getPortfolio(accountId);
  }
);

export const fetchHoldings = createAsyncThunk(
  'investment/fetchHoldings',
  async (accountId: string) => {
    return await investmentService.getHoldings(accountId);
  }
);

export const placeOrder = createAsyncThunk(
  'investment/placeOrder',
  async (request: CreateOrderRequest) => {
    return await investmentService.createOrder(request);
  }
);

export const fetchOrders = createAsyncThunk(
  'investment/fetchOrders',
  async ({ accountId, status }: { accountId: string; status?: string }) => {
    return await investmentService.getOrders(accountId, status);
  }
);

export const cancelOrder = createAsyncThunk(
  'investment/cancelOrder',
  async (orderId: string) => {
    await investmentService.cancelOrder(orderId);
    return orderId;
  }
);

export const fetchWatchlist = createAsyncThunk(
  'investment/fetchWatchlist',
  async () => {
    return await investmentService.getWatchlist();
  }
);

export const addToWatchlist = createAsyncThunk(
  'investment/addToWatchlist',
  async ({ symbol, notes }: { symbol: string; notes?: string }) => {
    return await investmentService.addToWatchlist(symbol, notes);
  }
);

export const removeFromWatchlist = createAsyncThunk(
  'investment/removeFromWatchlist',
  async (symbol: string) => {
    await investmentService.removeFromWatchlist(symbol);
    return symbol;
  }
);

export const updateWatchlistItem = createAsyncThunk(
  'investment/updateWatchlistItem',
  async ({ symbol, updates }: { symbol: string; updates: Partial<WatchlistItem> }) => {
    return await investmentService.updateWatchlistItem(symbol, updates);
  }
);

export const fetchAutoInvestPlans = createAsyncThunk(
  'investment/fetchAutoInvestPlans',
  async (accountId: string) => {
    return await investmentService.getAutoInvestPlans(accountId);
  }
);

export const createAutoInvestPlan = createAsyncThunk(
  'investment/createAutoInvestPlan',
  async ({ accountId, plan }: { accountId: string; plan: Partial<AutoInvestPlan> }) => {
    return await investmentService.createAutoInvestPlan(accountId, plan);
  }
);

export const searchSecurities = createAsyncThunk(
  'investment/searchSecurities',
  async (query: string) => {
    return await investmentService.searchSecurities(query);
  }
);

export const getQuote = createAsyncThunk(
  'investment/getQuote',
  async (symbol: string) => {
    return await investmentService.getQuote(symbol);
  }
);

export const fetchMarketMovers = createAsyncThunk(
  'investment/fetchMarketMovers',
  async (type: 'gainers' | 'losers' | 'active') => {
    const data = await investmentService.getMarketMovers(type);
    return { type, data };
  }
);

const investmentSlice = createSlice({
  name: 'investment',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updateMarketData: (state, action: PayloadAction<MarketData>) => {
      state.marketData[action.payload.symbol] = action.payload;
    },
    clearSearchResults: (state) => {
      state.searchResults = [];
    }
  },
  extraReducers: (builder) => {
    // Account
    builder
      .addCase(fetchAccount.pending, (state) => {
        state.loading.account = true;
      })
      .addCase(fetchAccount.fulfilled, (state, action) => {
        state.loading.account = false;
        state.account = action.payload;
      })
      .addCase(fetchAccount.rejected, (state, action) => {
        state.loading.account = false;
        state.error = action.error.message || 'Failed to fetch account';
      });

    builder
      .addCase(fetchAccountByCustomer.fulfilled, (state, action) => {
        state.account = action.payload;
      });

    builder
      .addCase(createAccount.fulfilled, (state, action) => {
        state.account = action.payload;
      });

    // Portfolio
    builder
      .addCase(fetchPortfolio.pending, (state) => {
        state.loading.portfolio = true;
      })
      .addCase(fetchPortfolio.fulfilled, (state, action) => {
        state.loading.portfolio = false;
        state.portfolio = action.payload;
      })
      .addCase(fetchPortfolio.rejected, (state, action) => {
        state.loading.portfolio = false;
        state.error = action.error.message || 'Failed to fetch portfolio';
      });

    // Holdings
    builder
      .addCase(fetchHoldings.fulfilled, (state, action) => {
        state.holdings = action.payload;
      });

    // Orders
    builder
      .addCase(placeOrder.pending, (state) => {
        state.tradingStatus.placing = true;
        state.error = null;
      })
      .addCase(placeOrder.fulfilled, (state, action) => {
        state.tradingStatus.placing = false;
        state.tradingStatus.lastOrderId = action.payload.id;
        state.orders.unshift(action.payload);
        if (['NEW', 'PENDING_SUBMIT', 'ACCEPTED', 'PARTIALLY_FILLED'].includes(action.payload.status)) {
          state.activeOrders.unshift(action.payload);
        }
      })
      .addCase(placeOrder.rejected, (state, action) => {
        state.tradingStatus.placing = false;
        state.error = action.error.message || 'Failed to place order';
      });

    builder
      .addCase(fetchOrders.pending, (state) => {
        state.loading.orders = true;
      })
      .addCase(fetchOrders.fulfilled, (state, action) => {
        state.loading.orders = false;
        state.orders = action.payload;
        state.activeOrders = action.payload.filter(order => 
          ['NEW', 'PENDING_SUBMIT', 'ACCEPTED', 'PARTIALLY_FILLED'].includes(order.status)
        );
      });

    builder
      .addCase(cancelOrder.pending, (state) => {
        state.tradingStatus.cancelling = true;
      })
      .addCase(cancelOrder.fulfilled, (state, action) => {
        state.tradingStatus.cancelling = false;
        const orderId = action.payload;
        state.orders = state.orders.map(order => 
          order.id === orderId ? { ...order, status: 'CANCELLED' as const } : order
        );
        state.activeOrders = state.activeOrders.filter(order => order.id !== orderId);
      });

    // Watchlist
    builder
      .addCase(fetchWatchlist.fulfilled, (state, action) => {
        state.watchlist = action.payload;
      });

    builder
      .addCase(addToWatchlist.fulfilled, (state, action) => {
        state.watchlist.push(action.payload);
      });

    builder
      .addCase(removeFromWatchlist.fulfilled, (state, action) => {
        state.watchlist = state.watchlist.filter(item => item.symbol !== action.payload);
      });

    builder
      .addCase(updateWatchlistItem.fulfilled, (state, action) => {
        const index = state.watchlist.findIndex(item => item.symbol === action.payload.symbol);
        if (index !== -1) {
          state.watchlist[index] = action.payload;
        }
      });

    // Auto-invest
    builder
      .addCase(fetchAutoInvestPlans.fulfilled, (state, action) => {
        state.autoInvestPlans = action.payload;
      });

    builder
      .addCase(createAutoInvestPlan.fulfilled, (state, action) => {
        state.autoInvestPlans.push(action.payload);
      });

    // Market data
    builder
      .addCase(searchSecurities.pending, (state) => {
        state.loading.marketData = true;
      })
      .addCase(searchSecurities.fulfilled, (state, action) => {
        state.loading.marketData = false;
        state.searchResults = action.payload;
      });

    builder
      .addCase(getQuote.fulfilled, (state, action) => {
        state.marketData[action.payload.symbol] = action.payload;
      });

    builder
      .addCase(fetchMarketMovers.fulfilled, (state, action) => {
        state.marketMovers[action.payload.type] = action.payload.data;
      });
  }
});

export const { clearError, updateMarketData, clearSearchResults } = investmentSlice.actions;
export default investmentSlice.reducer;