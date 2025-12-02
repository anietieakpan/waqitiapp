import axios from 'axios';
import { getAuthToken } from '../utils/auth';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
const INVESTMENT_SERVICE_URL = `${API_BASE_URL}/api/investment`;

export interface InvestmentAccount {
  id: string;
  customerId: string;
  accountNumber: string;
  walletAccountId: string;
  cashBalance: number;
  investedAmount: number;
  totalValue: number;
  dayChange: number;
  dayChangePercent: number;
  totalReturn: number;
  totalReturnPercent: number;
  realizedGains: number;
  unrealizedGains: number;
  dividendEarnings: number;
  status: 'PENDING_ACTIVATION' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | 'RESTRICTED' | 'UNDER_REVIEW';
  kycVerified: boolean;
  patternDayTrader: boolean;
  dayTrades: number;
  riskProfile?: string;
  investmentGoals?: string;
  brokerageProvider: string;
  createdAt: string;
  updatedAt: string;
  numberOfPositions?: number;
  portfolioDiversification?: number;
}

export interface Portfolio {
  id: string;
  investmentAccountId: string;
  totalValue: number;
  totalCost: number;
  totalReturn: number;
  totalReturnPercent: number;
  dayChange: number;
  dayChangePercent: number;
  realizedGains: number;
  unrealizedGains: number;
  dividendEarnings: number;
  numberOfPositions: number;
  cashPercentage: number;
  equityPercentage: number;
  etfPercentage: number;
  cryptoPercentage: number;
  diversificationScore: number;
  riskScore: number;
  topPerformer?: string;
  worstPerformer?: string;
  topHoldings?: HoldingSummary[];
  assetAllocation?: AssetAllocation[];
}

export interface HoldingSummary {
  symbol: string;
  name: string;
  quantity: number;
  marketValue: number;
  portfolioPercentage: number;
  dayChange: number;
  dayChangePercent: number;
  totalReturn: number;
  totalReturnPercent: number;
}

export interface AssetAllocation {
  assetType: string;
  percentage: number;
  value: number;
  count: number;
}

export interface InvestmentHolding {
  id: string;
  symbol: string;
  instrumentType: 'STOCK' | 'ETF' | 'CRYPTO';
  name: string;
  quantity: number;
  averageCost: number;
  totalCost: number;
  currentPrice: number;
  marketValue: number;
  dayChange: number;
  dayChangePercent: number;
  totalReturn: number;
  totalReturnPercent: number;
  realizedGains: number;
  unrealizedGains: number;
  dividendEarnings: number;
  portfolioPercentage: number;
  previousClose?: number;
  dayLow?: number;
  dayHigh?: number;
  fiftyTwoWeekLow?: number;
  fiftyTwoWeekHigh?: number;
  volume?: number;
  marketCap?: number;
  peRatio?: number;
  dividendYield?: number;
  beta?: number;
}

export interface InvestmentOrder {
  id: string;
  orderNumber: string;
  symbol: string;
  instrumentType: string;
  instrumentName?: string;
  side: 'BUY' | 'SELL';
  orderType: 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT';
  timeInForce: 'DAY' | 'GTC' | 'GTD' | 'IOC' | 'FOK';
  quantity: number;
  limitPrice?: number;
  stopPrice?: number;
  executedQuantity: number;
  executedPrice?: number;
  averagePrice?: number;
  orderAmount: number;
  commission: number;
  fees: number;
  totalCost?: number;
  status: 'NEW' | 'PENDING_SUBMIT' | 'ACCEPTED' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED';
  statusDisplay?: string;
  rejectReason?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  filledAt?: string;
}

export interface CreateInvestmentAccountRequest {
  customerId: string;
  walletAccountId: string;
  riskProfile?: string;
  investmentGoals?: string;
  brokerageProvider: string;
  initialDeposit?: number;
}

export interface CreateOrderRequest {
  accountId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  orderType: 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT';
  quantity: number;
  limitPrice?: number;
  stopPrice?: number;
  timeInForce: 'DAY' | 'GTC' | 'GTD' | 'IOC' | 'FOK';
  notes?: string;
}

export interface WatchlistItem {
  id: string;
  symbol: string;
  instrumentType: string;
  name: string;
  currentPrice: number;
  dayChange: number;
  dayChangePercent: number;
  marketCap?: number;
  peRatio?: number;
  dividendYield?: number;
  targetPrice?: number;
  notes?: string;
  alertsEnabled: boolean;
  priceAlertAbove?: number;
  priceAlertBelow?: number;
}

export interface AutoInvestPlan {
  id: string;
  planName: string;
  amount: number;
  frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY';
  startDate: string;
  endDate?: string;
  status: 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'COMPLETED';
  allocations: AutoInvestAllocation[];
  totalInvested: number;
  executionCount: number;
  nextExecutionDate?: string;
}

export interface AutoInvestAllocation {
  symbol: string;
  name: string;
  percentage: number;
}

export interface MarketData {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  marketCap?: number;
  peRatio?: number;
  dividendYield?: number;
  dayLow: number;
  dayHigh: number;
  fiftyTwoWeekLow: number;
  fiftyTwoWeekHigh: number;
}

class InvestmentService {
  private getHeaders() {
    const token = getAuthToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    };
  }

  // Account Management
  async createAccount(request: CreateInvestmentAccountRequest): Promise<InvestmentAccount> {
    const response = await axios.post(
      `${INVESTMENT_SERVICE_URL}/accounts`,
      request,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getAccount(accountId: string): Promise<InvestmentAccount> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getAccountByCustomer(customerId: string): Promise<InvestmentAccount> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/customer/${customerId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async fundAccount(accountId: string, amount: number): Promise<any> {
    const response = await axios.post(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/fund`,
      { accountId, amount },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async withdrawFunds(accountId: string, amount: number): Promise<any> {
    const response = await axios.post(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/withdraw`,
      null,
      { 
        headers: this.getHeaders(),
        params: { amount }
      }
    );
    return response.data;
  }

  // Portfolio Management
  async getPortfolio(accountId: string): Promise<Portfolio> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/portfolios/${accountId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getHoldings(accountId: string): Promise<InvestmentHolding[]> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/holdings`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getHolding(accountId: string, symbol: string): Promise<InvestmentHolding> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/holdings/${symbol}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Trading
  async createOrder(request: CreateOrderRequest): Promise<InvestmentOrder> {
    const response = await axios.post(
      `${INVESTMENT_SERVICE_URL}/orders`,
      request,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getOrders(accountId: string, status?: string): Promise<InvestmentOrder[]> {
    const params = status ? { status } : {};
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/orders`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async getOrder(orderId: string): Promise<InvestmentOrder> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/orders/${orderId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async cancelOrder(orderId: string): Promise<void> {
    await axios.delete(
      `${INVESTMENT_SERVICE_URL}/orders/${orderId}`,
      { headers: this.getHeaders() }
    );
  }

  // Watchlist
  async getWatchlist(): Promise<WatchlistItem[]> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/watchlist`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async addToWatchlist(symbol: string, notes?: string): Promise<WatchlistItem> {
    const response = await axios.post(
      `${INVESTMENT_SERVICE_URL}/watchlist`,
      { symbol, notes },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async removeFromWatchlist(symbol: string): Promise<void> {
    await axios.delete(
      `${INVESTMENT_SERVICE_URL}/watchlist/${symbol}`,
      { headers: this.getHeaders() }
    );
  }

  async updateWatchlistItem(symbol: string, updates: Partial<WatchlistItem>): Promise<WatchlistItem> {
    const response = await axios.put(
      `${INVESTMENT_SERVICE_URL}/watchlist/${symbol}`,
      updates,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Auto-Invest
  async getAutoInvestPlans(accountId: string): Promise<AutoInvestPlan[]> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/auto-invest`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async createAutoInvestPlan(accountId: string, plan: Partial<AutoInvestPlan>): Promise<AutoInvestPlan> {
    const response = await axios.post(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/auto-invest`,
      plan,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async updateAutoInvestPlan(planId: string, updates: Partial<AutoInvestPlan>): Promise<AutoInvestPlan> {
    const response = await axios.put(
      `${INVESTMENT_SERVICE_URL}/auto-invest/${planId}`,
      updates,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async pauseAutoInvestPlan(planId: string): Promise<void> {
    await axios.put(
      `${INVESTMENT_SERVICE_URL}/auto-invest/${planId}/pause`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async resumeAutoInvestPlan(planId: string): Promise<void> {
    await axios.put(
      `${INVESTMENT_SERVICE_URL}/auto-invest/${planId}/resume`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async cancelAutoInvestPlan(planId: string): Promise<void> {
    await axios.delete(
      `${INVESTMENT_SERVICE_URL}/auto-invest/${planId}`,
      { headers: this.getHeaders() }
    );
  }

  // Market Data
  async searchSecurities(query: string): Promise<MarketData[]> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/market/search`,
      { 
        headers: this.getHeaders(),
        params: { q: query }
      }
    );
    return response.data;
  }

  async getQuote(symbol: string): Promise<MarketData> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/market/quote/${symbol}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getMarketMovers(type: 'gainers' | 'losers' | 'active'): Promise<MarketData[]> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/market/movers/${type}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Performance
  async getAccountPerformance(accountId: string, period: string = '1M'): Promise<any> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/accounts/${accountId}/performance`,
      { 
        headers: this.getHeaders(),
        params: { period }
      }
    );
    return response.data;
  }

  async getPortfolioAnalytics(accountId: string): Promise<any> {
    const response = await axios.get(
      `${INVESTMENT_SERVICE_URL}/portfolios/${accountId}/analytics`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }
}

export default new InvestmentService();