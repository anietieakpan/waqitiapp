/**
 * Analytics Service - Real API integration for transaction analytics
 * Replaces mock data with actual backend integration
 */

import { apiClient } from './apiClient';

export interface TransactionAnalytics {
  chartData: ChartDataPoint[];
  summary: TransactionSummary;
  categories: CategoryBreakdown[];
  trends: TrendAnalysis;
}

export interface ChartDataPoint {
  date: string;
  income: number;
  expense: number;
  net: number;
  transactionCount: number;
}

export interface TransactionSummary {
  totalIncome: number;
  totalExpense: number;
  netFlow: number;
  avgDailyIncome: number;
  avgDailyExpense: number;
  transactionCount: number;
  largestTransaction: number;
  averageTransactionSize: number;
}

export interface CategoryBreakdown {
  name: string;
  value: number;
  percentage: number;
  color: string;
  transactionCount: number;
}

export interface TrendAnalysis {
  incomeGrowth: number; // percentage
  expenseGrowth: number; // percentage
  savingsRate: number; // percentage
  prediction: {
    nextMonthIncome: number;
    nextMonthExpense: number;
    confidence: number; // 0-1
  };
}

export type PeriodType = '7d' | '30d' | '90d' | '1y';
export type ChartType = 'line' | 'bar' | 'area' | 'pie';

class AnalyticsService {
  /**
   * Get transaction analytics for charts and summaries
   */
  async getTransactionAnalytics(
    period: PeriodType,
    walletId?: string,
    categoryFilter?: string[]
  ): Promise<TransactionAnalytics> {
    try {
      const params = {
        period,
        walletId,
        categories: categoryFilter?.join(','),
        includeCategories: true,
        includeTrends: true,
      };

      const response = await apiClient.get<TransactionAnalytics>('/analytics/transactions', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch transaction analytics:', error);
      throw new Error(error.response?.data?.message || 'Failed to load analytics data');
    }
  }

  /**
   * Get spending by category breakdown
   */
  async getCategoryBreakdown(
    period: PeriodType,
    walletId?: string,
    type: 'expense' | 'income' | 'both' = 'expense'
  ): Promise<CategoryBreakdown[]> {
    try {
      const params = {
        period,
        walletId,
        type,
      };

      const response = await apiClient.get<{ categories: CategoryBreakdown[] }>(
        '/analytics/categories',
        { params }
      );
      
      return response.data.categories;
    } catch (error: any) {
      console.error('Failed to fetch category breakdown:', error);
      throw new Error(error.response?.data?.message || 'Failed to load category data');
    }
  }

  /**
   * Get transaction trends and predictions
   */
  async getTransactionTrends(
    period: PeriodType,
    walletId?: string
  ): Promise<TrendAnalysis> {
    try {
      const params = {
        period,
        walletId,
      };

      const response = await apiClient.get<TrendAnalysis>('/analytics/trends', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch transaction trends:', error);
      throw new Error(error.response?.data?.message || 'Failed to load trend data');
    }
  }

  /**
   * Get real-time metrics dashboard data
   */
  async getDashboardMetrics(walletId?: string): Promise<{
    todayIncome: number;
    todayExpense: number;
    weeklyAverage: number;
    monthlyBudget: {
      spent: number;
      limit: number;
      remaining: number;
    };
    topCategories: CategoryBreakdown[];
    recentTransactions: any[];
  }> {
    try {
      const params = { walletId };

      const response = await apiClient.get('/analytics/dashboard', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch dashboard metrics:', error);
      throw new Error(error.response?.data?.message || 'Failed to load dashboard data');
    }
  }

  /**
   * Get comparative analytics (vs previous period)
   */
  async getComparativeAnalytics(
    period: PeriodType,
    walletId?: string
  ): Promise<{
    current: TransactionSummary;
    previous: TransactionSummary;
    changes: {
      incomeChange: number;
      expenseChange: number;
      netFlowChange: number;
      transactionCountChange: number;
    };
  }> {
    try {
      const params = {
        period,
        walletId,
        comparative: true,
      };

      const response = await apiClient.get('/analytics/comparative', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch comparative analytics:', error);
      throw new Error(error.response?.data?.message || 'Failed to load comparative data');
    }
  }

  /**
   * Get budget tracking and alerts
   */
  async getBudgetAnalytics(walletId?: string): Promise<{
    categories: Array<{
      name: string;
      budgeted: number;
      spent: number;
      remaining: number;
      percentage: number;
      status: 'on-track' | 'warning' | 'over-budget';
      daysRemaining: number;
    }>;
    overall: {
      totalBudget: number;
      totalSpent: number;
      remainingBudget: number;
      projectedSpending: number;
      recommendedDailySpend: number;
    };
    alerts: Array<{
      type: 'warning' | 'danger' | 'info';
      category: string;
      message: string;
      recommendation: string;
    }>;
  }> {
    try {
      const params = { walletId };

      const response = await apiClient.get('/analytics/budget', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch budget analytics:', error);
      throw new Error(error.response?.data?.message || 'Failed to load budget data');
    }
  }

  /**
   * Export analytics data
   */
  async exportAnalytics(
    period: PeriodType,
    format: 'csv' | 'pdf' | 'excel',
    walletId?: string,
    includeCharts: boolean = true
  ): Promise<Blob> {
    try {
      const params = {
        period,
        format,
        walletId,
        includeCharts,
      };

      const response = await apiClient.get('/analytics/export', { 
        params,
        responseType: 'blob',
      });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to export analytics:', error);
      throw new Error(error.response?.data?.message || 'Failed to export analytics');
    }
  }

  /**
   * Get merchant analytics (spending by merchant)
   */
  async getMerchantAnalytics(
    period: PeriodType,
    walletId?: string,
    limit: number = 20
  ): Promise<Array<{
    merchantId: string;
    merchantName: string;
    totalSpent: number;
    transactionCount: number;
    averageAmount: number;
    category: string;
    lastTransaction: string;
    trend: 'increasing' | 'decreasing' | 'stable';
  }>> {
    try {
      const params = {
        period,
        walletId,
        limit,
      };

      const response = await apiClient.get('/analytics/merchants', { params });
      
      return response.data.merchants;
    } catch (error: any) {
      console.error('Failed to fetch merchant analytics:', error);
      throw new Error(error.response?.data?.message || 'Failed to load merchant data');
    }
  }

  /**
   * Get cash flow analysis
   */
  async getCashFlowAnalysis(
    period: PeriodType,
    walletId?: string
  ): Promise<{
    dailyCashFlow: Array<{
      date: string;
      inflow: number;
      outflow: number;
      balance: number;
      runningBalance: number;
    }>;
    predictions: {
      nextWeekBalance: number;
      nextMonthBalance: number;
      lowBalanceDate?: string;
      recommendedTopUp?: number;
    };
    patterns: {
      paydayPattern: {
        detected: boolean;
        frequency: 'weekly' | 'biweekly' | 'monthly';
        nextPayday?: string;
        averageAmount?: number;
      };
      recurringExpenses: Array<{
        description: string;
        amount: number;
        frequency: string;
        nextDue: string;
        confidence: number;
      }>;
    };
  }> {
    try {
      const params = {
        period,
        walletId,
      };

      const response = await apiClient.get('/analytics/cashflow', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch cash flow analysis:', error);
      throw new Error(error.response?.data?.message || 'Failed to load cash flow data');
    }
  }

  /**
   * Get personalized insights and recommendations
   */
  async getPersonalizedInsights(walletId?: string): Promise<{
    insights: Array<{
      id: string;
      type: 'spending' | 'saving' | 'budgeting' | 'optimization';
      title: string;
      description: string;
      impact: 'high' | 'medium' | 'low';
      action: string;
      potentialSavings?: number;
      confidence: number;
    }>;
    achievements: Array<{
      id: string;
      title: string;
      description: string;
      icon: string;
      unlockedAt: string;
      progress?: number;
    }>;
    goals: Array<{
      id: string;
      title: string;
      targetAmount: number;
      currentAmount: number;
      deadline: string;
      status: 'on-track' | 'behind' | 'ahead';
      recommendation: string;
    }>;
  }> {
    try {
      const params = { walletId };

      const response = await apiClient.get('/analytics/insights', { params });
      
      return response.data;
    } catch (error: any) {
      console.error('Failed to fetch personalized insights:', error);
      throw new Error(error.response?.data?.message || 'Failed to load insights');
    }
  }
}

export default new AnalyticsService();