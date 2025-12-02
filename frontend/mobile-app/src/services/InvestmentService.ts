import { ApiService } from './ApiService';

export interface InvestmentPortfolio {
  totalValue: number;
  totalGain: number;
  totalGainPercent: number;
  assets: PortfolioAsset[];
  allocation: AssetAllocation[];
  performance: PerformanceData[];
}

export interface PortfolioAsset {
  symbol: string;
  name: string;
  quantity: number;
  currentPrice: number;
  marketValue: number;
  gain: number;
  gainPercent: number;
  assetType: 'STOCK' | 'CRYPTO' | 'BOND' | 'ETF' | 'MUTUAL_FUND';
}

export interface AssetAllocation {
  category: string;
  value: number;
  percentage: number;
  color: string;
}

export interface PerformanceData {
  date: string;
  value: number;
}

export interface PortfolioRequest {
  period?: string;
  includePerformance?: boolean;
  includeAllocation?: boolean;
}

export interface InvestmentOrder {
  orderType: 'BUY' | 'SELL';
  symbol: string;
  quantity: number;
  orderMethod: 'MARKET' | 'LIMIT' | 'STOP_LOSS';
  limitPrice?: number;
  stopPrice?: number;
  timeInForce: 'DAY' | 'GTC' | 'IOC' | 'FOK';
}

export interface OrderResponse {
  orderId: string;
  status: 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED';
  fillPrice?: number;
  fillQuantity?: number;
  executedAt?: string;
}

export interface MarketData {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  high52Week: number;
  low52Week: number;
  marketCap?: number;
  peRatio?: number;
  dividendYield?: number;
}

export interface WatchlistItem {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  alertPrice?: number;
  alertType?: 'ABOVE' | 'BELOW';
}

export interface ResearchReport {
  symbol: string;
  rating: 'STRONG_BUY' | 'BUY' | 'HOLD' | 'SELL' | 'STRONG_SELL';
  targetPrice: number;
  summary: string;
  keyMetrics: KeyMetric[];
  analyst: string;
  publishedAt: string;
}

export interface KeyMetric {
  name: string;
  value: string;
  description: string;
}

class InvestmentService {
  private apiService = new ApiService();

  async getPortfolioSummary(request: PortfolioRequest): Promise<{
    success: boolean;
    data?: InvestmentPortfolio;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/portfolio', {
        params: request,
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getAssetDetail(symbol: string): Promise<{
    success: boolean;
    data?: MarketData;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/investments/assets/${symbol}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async placeOrder(order: InvestmentOrder): Promise<{
    success: boolean;
    data?: OrderResponse;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.post('/api/v1/investments/orders', order);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getOrderHistory(limit: number = 50): Promise<{
    success: boolean;
    data?: OrderResponse[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/orders/history', {
        params: { limit },
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getWatchlist(): Promise<{
    success: boolean;
    data?: WatchlistItem[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/watchlist');

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async addToWatchlist(symbol: string, alertPrice?: number, alertType?: 'ABOVE' | 'BELOW'): Promise<{
    success: boolean;
    errorMessage?: string;
  }> {
    try {
      await this.apiService.post('/api/v1/investments/watchlist', {
        symbol,
        alertPrice,
        alertType,
      });

      return { success: true };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async removeFromWatchlist(symbol: string): Promise<{
    success: boolean;
    errorMessage?: string;
  }> {
    try {
      await this.apiService.delete(`/api/v1/investments/watchlist/${symbol}`);

      return { success: true };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async searchAssets(query: string, type?: string): Promise<{
    success: boolean;
    data?: MarketData[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/search', {
        params: { q: query, type },
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getResearchReport(symbol: string): Promise<{
    success: boolean;
    data?: ResearchReport;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/investments/research/${symbol}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getMarketNews(limit: number = 20): Promise<{
    success: boolean;
    data?: NewsItem[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/news', {
        params: { limit },
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getRebalancingRecommendations(): Promise<{
    success: boolean;
    data?: RebalancingRecommendation[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/rebalancing');

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async executeRebalancing(recommendations: RebalancingAction[]): Promise<{
    success: boolean;
    data?: OrderResponse[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.post('/api/v1/investments/rebalancing/execute', {
        actions: recommendations,
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getTaxLossHarvestingOpportunities(): Promise<{
    success: boolean;
    data?: TaxLossOpportunity[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/tax-loss-harvesting');

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getRiskAssessment(): Promise<{
    success: boolean;
    data?: RiskAssessment;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/investments/risk-assessment');

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }
}

// Supporting interfaces
export interface NewsItem {
  id: string;
  title: string;
  summary: string;
  url: string;
  publishedAt: string;
  source: string;
  relatedSymbols: string[];
}

export interface RebalancingRecommendation {
  reason: string;
  recommendations: RebalancingAction[];
  expectedBenefit: string;
  riskImpact: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface RebalancingAction {
  action: 'BUY' | 'SELL';
  symbol: string;
  quantity: number;
  reason: string;
}

export interface TaxLossOpportunity {
  symbol: string;
  currentValue: number;
  purchaseValue: number;
  loss: number;
  potentialTaxSavings: number;
  recommendation: string;
}

export interface RiskAssessment {
  overallRisk: 'CONSERVATIVE' | 'MODERATE' | 'AGGRESSIVE';
  riskScore: number;
  factors: RiskFactor[];
  recommendations: string[];
}

export interface RiskFactor {
  factor: string;
  impact: 'LOW' | 'MEDIUM' | 'HIGH';
  description: string;
}

export const investmentService = new InvestmentService();