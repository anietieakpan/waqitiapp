import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { analyticsApi } from '../../api/analytics';

// Types
export interface AnalyticsState {
  metrics: MetricsData;
  charts: ChartsData;
  insights: Insight[];
  predictions: PredictionData | null;
  realTimeMetrics: RealTimeMetrics | null;
  filters: AnalyticsFilters;
  loading: boolean;
  error: string | null;
  lastUpdated: string | null;
  subscriptions: WebSocketSubscription[];
}

export interface MetricsData {
  totalRevenue: number;
  revenueChange: number;
  activeUsers: number;
  userGrowth: number;
  transactionVolume: number;
  volumeChange: number;
  successRate: number;
  successRateChange: number;
  grossRevenue: number;
  grossRevenueChange: number;
  netRevenue: number;
  netRevenueChange: number;
  processingFees: number;
  feesChange: number;
  margin: number;
  marginChange: number;
  totalUsers: number;
  userGrowthRate: number;
  dailyActiveUsers: number;
  dauChange: number;
  retentionRate: number;
  retentionChange: number;
  avgTransactionPerUser: number;
  avgTransactionChange: number;
}

export interface ChartsData {
  revenueTrend: TimeSeriesData[];
  revenueByCategory: CategoryData[];
  geographicData: GeographicData[];
  userFunnel: FunnelData[];
  topMerchants: MerchantData[];
  processingTime: PerformanceData[];
  errorRates: ErrorData[];
  queueMetrics: QueueData[];
  cashFlow: CashFlowData[];
  revenueByPaymentMethod: TreemapData[];
  currencyDistribution: RadialData[];
  userActivity: ActivityData[];
  userSegments: SegmentData[];
  cohortData: CohortData[];
}

export interface TimeSeriesData {
  date: string;
  revenue: number;
  target?: number;
  timestamp: number;
}

export interface CategoryData {
  name: string;
  value: number;
  percentage: number;
}

export interface GeographicData {
  country: string;
  transactionVolume: number;
  revenue: number;
  lat: number;
  lng: number;
}

export interface FunnelData {
  stage: string;
  value: number;
  conversion: number;
}

export interface MerchantData {
  name: string;
  revenue: number;
  transactions: number;
  id: string;
}

export interface PerformanceData {
  time: string;
  p50: number;
  p95: number;
  p99: number;
}

export interface ErrorData {
  service: string;
  errorRate: number;
  errors: number;
}

export interface QueueData {
  time: string;
  depth: number;
  processing: number;
  failed: number;
}

export interface CashFlowData {
  date: string;
  inflow: number;
  outflow: number;
  net: number;
}

export interface TreemapData {
  name: string;
  value: number;
  children?: TreemapData[];
}

export interface RadialData {
  name: string;
  value: number;
  fill: string;
}

export interface ActivityData {
  hour: number;
  weekday: number;
  weekend: number;
}

export interface SegmentData {
  segment: string;
  value: number;
  color: string;
}

export interface CohortData {
  name: string;
  retention: number[];
}

export interface Insight {
  id: string;
  type: 'success' | 'warning' | 'error' | 'info';
  title: string;
  description: string;
  action?: string;
  priority: 'low' | 'medium' | 'high';
  timestamp: string;
}

export interface PredictionData {
  shortTerm: ShortTermPredictions;
  longTerm: LongTermPredictions;
  confidence: number;
  factors: string[];
}

export interface ShortTermPredictions {
  nextHour: PredictionPoint;
  next24Hours: PredictionPoint;
  nextWeek: PredictionPoint;
}

export interface LongTermPredictions {
  nextMonth: PredictionPoint;
  nextQuarter: PredictionPoint;
  nextYear: PredictionPoint;
}

export interface PredictionPoint {
  revenue: number;
  volume: number;
  users: number;
  confidence: number;
}

export interface RealTimeMetrics {
  timestamp: string;
  transactionsPerSecond: number;
  currentUsers: number;
  systemHealth: SystemHealth;
  alerts: Alert[];
}

export interface SystemHealth {
  cpu: number;
  memory: number;
  responseTime: number;
  errorRate: number;
  status: 'healthy' | 'warning' | 'critical';
}

export interface Alert {
  id: string;
  type: 'info' | 'warning' | 'error';
  message: string;
  timestamp: string;
  acknowledged: boolean;
}

export interface AnalyticsFilters {
  timeRange: string;
  currency: string;
  dashboardType: 'executive' | 'operational' | 'financial' | 'user';
  country?: string;
  merchant?: string;
  paymentMethod?: string;
}

export interface WebSocketSubscription {
  id: string;
  type: 'metrics' | 'alerts' | 'predictions';
  active: boolean;
}

// Async Thunks
export const fetchAnalytics = createAsyncThunk(
  'analytics/fetchAnalytics',
  async (params: Partial<AnalyticsFilters>, { rejectWithValue }) => {
    try {
      const response = await analyticsApi.getAnalytics(params);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch analytics');
    }
  }
);

export const fetchRealTimeMetrics = createAsyncThunk(
  'analytics/fetchRealTimeMetrics',
  async (_, { rejectWithValue }) => {
    try {
      const response = await analyticsApi.getRealTimeMetrics();
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch real-time metrics');
    }
  }
);

export const fetchPredictions = createAsyncThunk(
  'analytics/fetchPredictions',
  async (params: { timeframe: string; models: string[] }, { rejectWithValue }) => {
    try {
      const response = await analyticsApi.getPredictions(params);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to fetch predictions');
    }
  }
);

export const exportReport = createAsyncThunk(
  'analytics/exportReport',
  async (params: {
    format: 'pdf' | 'excel' | 'csv';
    timeRange: string;
    currency: string;
    dashboardType: string;
  }, { rejectWithValue }) => {
    try {
      const response = await analyticsApi.exportReport(params);
      
      // Handle file download
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `analytics-report-${Date.now()}.${params.format}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      
      return { success: true, format: params.format };
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Failed to export report');
    }
  }
);

export const subscribeToRealTime = createAsyncThunk(
  'analytics/subscribeToRealTime',
  async (type: 'metrics' | 'alerts' | 'predictions', { dispatch, rejectWithValue }) => {
    try {
      const subscription = await analyticsApi.subscribeToRealTime(type);
      
      subscription.onMessage((data) => {
        dispatch(updateRealTimeData({ type, data }));
      });
      
      subscription.onError((error) => {
        dispatch(handleWebSocketError({ type, error: error.message }));
      });
      
      return {
        id: subscription.id,
        type,
        active: true
      };
    } catch (error: any) {
      return rejectWithValue(error.message || 'Failed to subscribe to real-time updates');
    }
  }
);

// Initial State
const initialState: AnalyticsState = {
  metrics: {
    totalRevenue: 0,
    revenueChange: 0,
    activeUsers: 0,
    userGrowth: 0,
    transactionVolume: 0,
    volumeChange: 0,
    successRate: 0,
    successRateChange: 0,
    grossRevenue: 0,
    grossRevenueChange: 0,
    netRevenue: 0,
    netRevenueChange: 0,
    processingFees: 0,
    feesChange: 0,
    margin: 0,
    marginChange: 0,
    totalUsers: 0,
    userGrowthRate: 0,
    dailyActiveUsers: 0,
    dauChange: 0,
    retentionRate: 0,
    retentionChange: 0,
    avgTransactionPerUser: 0,
    avgTransactionChange: 0,
  },
  charts: {
    revenueTrend: [],
    revenueByCategory: [],
    geographicData: [],
    userFunnel: [],
    topMerchants: [],
    processingTime: [],
    errorRates: [],
    queueMetrics: [],
    cashFlow: [],
    revenueByPaymentMethod: [],
    currencyDistribution: [],
    userActivity: [],
    userSegments: [],
    cohortData: [],
  },
  insights: [],
  predictions: null,
  realTimeMetrics: null,
  filters: {
    timeRange: '7d',
    currency: 'USD',
    dashboardType: 'executive',
  },
  loading: false,
  error: null,
  lastUpdated: null,
  subscriptions: [],
};

// Slice
const analyticsSlice = createSlice({
  name: 'analytics',
  initialState,
  reducers: {
    updateFilters: (state, action: PayloadAction<Partial<AnalyticsFilters>>) => {
      state.filters = { ...state.filters, ...action.payload };
    },
    
    clearError: (state) => {
      state.error = null;
    },
    
    updateRealTimeData: (state, action: PayloadAction<{ type: string; data: any }>) => {
      const { type, data } = action.payload;
      
      switch (type) {
        case 'metrics':
          state.realTimeMetrics = data;
          break;
        case 'alerts':
          if (state.realTimeMetrics) {
            state.realTimeMetrics.alerts = data;
          }
          break;
        case 'predictions':
          state.predictions = data;
          break;
      }
      
      state.lastUpdated = new Date().toISOString();
    },
    
    handleWebSocketError: (state, action: PayloadAction<{ type: string; error: string }>) => {
      const { type } = action.payload;
      state.subscriptions = state.subscriptions.map(sub =>
        sub.type === type ? { ...sub, active: false } : sub
      );
    },
    
    acknowledgeAlert: (state, action: PayloadAction<string>) => {
      if (state.realTimeMetrics) {
        state.realTimeMetrics.alerts = state.realTimeMetrics.alerts.map(alert =>
          alert.id === action.payload ? { ...alert, acknowledged: true } : alert
        );
      }
    },
    
    addInsight: (state, action: PayloadAction<Insight>) => {
      state.insights.unshift(action.payload);
      // Keep only the last 20 insights
      if (state.insights.length > 20) {
        state.insights = state.insights.slice(0, 20);
      }
    },
    
    removeInsight: (state, action: PayloadAction<string>) => {
      state.insights = state.insights.filter(insight => insight.id !== action.payload);
    },
    
    unsubscribeFromRealTime: (state, action: PayloadAction<string>) => {
      state.subscriptions = state.subscriptions.filter(sub => sub.id !== action.payload);
    },
    
    resetAnalytics: () => initialState,
  },
  
  extraReducers: (builder) => {
    // Fetch Analytics
    builder
      .addCase(fetchAnalytics.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchAnalytics.fulfilled, (state, action) => {
        state.loading = false;
        state.metrics = action.payload.metrics;
        state.charts = action.payload.charts;
        state.insights = action.payload.insights;
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchAnalytics.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
    
    // Fetch Real-Time Metrics
    builder
      .addCase(fetchRealTimeMetrics.pending, (state) => {
        // Don't set loading for real-time updates
      })
      .addCase(fetchRealTimeMetrics.fulfilled, (state, action) => {
        state.realTimeMetrics = action.payload;
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchRealTimeMetrics.rejected, (state, action) => {
        state.error = action.payload as string;
      });
    
    // Fetch Predictions
    builder
      .addCase(fetchPredictions.pending, (state) => {
        // Don't set loading for predictions
      })
      .addCase(fetchPredictions.fulfilled, (state, action) => {
        state.predictions = action.payload;
        state.lastUpdated = new Date().toISOString();
      })
      .addCase(fetchPredictions.rejected, (state, action) => {
        state.error = action.payload as string;
      });
    
    // Export Report
    builder
      .addCase(exportReport.pending, (state) => {
        // Show loading indicator for export
        state.loading = true;
      })
      .addCase(exportReport.fulfilled, (state, action) => {
        state.loading = false;
        // Add success insight
        state.insights.unshift({
          id: `export-${Date.now()}`,
          type: 'success',
          title: 'Report Exported',
          description: `${action.payload.format.toUpperCase()} report downloaded successfully`,
          priority: 'low',
          timestamp: new Date().toISOString(),
        });
      })
      .addCase(exportReport.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
    
    // Subscribe to Real-Time
    builder
      .addCase(subscribeToRealTime.fulfilled, (state, action) => {
        state.subscriptions.push(action.payload);
      })
      .addCase(subscribeToRealTime.rejected, (state, action) => {
        state.error = action.payload as string;
      });
  },
});

// Actions
export const {
  updateFilters,
  clearError,
  updateRealTimeData,
  handleWebSocketError,
  acknowledgeAlert,
  addInsight,
  removeInsight,
  unsubscribeFromRealTime,
  resetAnalytics,
} = analyticsSlice.actions;

// Selectors
export const selectAnalytics = (state: { analytics: AnalyticsState }) => state.analytics;
export const selectMetrics = (state: { analytics: AnalyticsState }) => state.analytics.metrics;
export const selectCharts = (state: { analytics: AnalyticsState }) => state.analytics.charts;
export const selectInsights = (state: { analytics: AnalyticsState }) => state.analytics.insights;
export const selectPredictions = (state: { analytics: AnalyticsState }) => state.analytics.predictions;
export const selectRealTimeMetrics = (state: { analytics: AnalyticsState }) => state.analytics.realTimeMetrics;
export const selectFilters = (state: { analytics: AnalyticsState }) => state.analytics.filters;
export const selectLoading = (state: { analytics: AnalyticsState }) => state.analytics.loading;
export const selectError = (state: { analytics: AnalyticsState }) => state.analytics.error;
export const selectLastUpdated = (state: { analytics: AnalyticsState }) => state.analytics.lastUpdated;
export const selectSubscriptions = (state: { analytics: AnalyticsState }) => state.analytics.subscriptions;

// Active alerts selector
export const selectActiveAlerts = (state: { analytics: AnalyticsState }) => 
  state.analytics.realTimeMetrics?.alerts.filter(alert => !alert.acknowledged) || [];

// High priority insights selector
export const selectHighPriorityInsights = (state: { analytics: AnalyticsState }) =>
  state.analytics.insights.filter(insight => insight.priority === 'high');

// System health status selector
export const selectSystemHealthStatus = (state: { analytics: AnalyticsState }) =>
  state.analytics.realTimeMetrics?.systemHealth.status || 'unknown';

export default analyticsSlice.reducer;