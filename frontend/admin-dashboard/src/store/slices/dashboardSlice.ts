import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { dashboardService } from '../../services/dashboardService';
import { 
  DashboardStats, 
  RealtimeMetrics, 
  SystemAlert,
  TimeRange 
} from '../../types/dashboard';

interface DashboardState {
  stats: DashboardStats | null;
  realtimeMetrics: RealtimeMetrics | null;
  alerts: SystemAlert[];
  isLoading: boolean;
  error: string | null;
  selectedTimeRange: TimeRange;
  autoRefresh: boolean;
  refreshInterval: number; // in seconds
}

const initialState: DashboardState = {
  stats: null,
  realtimeMetrics: null,
  alerts: [],
  isLoading: false,
  error: null,
  selectedTimeRange: '24h',
  autoRefresh: true,
  refreshInterval: 30,
};

export const fetchDashboardStats = createAsyncThunk(
  'dashboard/fetchStats',
  async (timeRange: TimeRange) => {
    return await dashboardService.getDashboardStats(timeRange);
  }
);

export const fetchRealtimeMetrics = createAsyncThunk(
  'dashboard/fetchRealtimeMetrics',
  async () => {
    return await dashboardService.getRealtimeMetrics();
  }
);

export const fetchSystemAlerts = createAsyncThunk(
  'dashboard/fetchAlerts',
  async () => {
    return await dashboardService.getSystemAlerts();
  }
);

export const acknowledgeAlert = createAsyncThunk(
  'dashboard/acknowledgeAlert',
  async (alertId: string) => {
    await dashboardService.acknowledgeAlert(alertId);
    return alertId;
  }
);

const dashboardSlice = createSlice({
  name: 'dashboard',
  initialState,
  reducers: {
    setTimeRange: (state, action: PayloadAction<TimeRange>) => {
      state.selectedTimeRange = action.payload;
    },
    toggleAutoRefresh: (state) => {
      state.autoRefresh = !state.autoRefresh;
    },
    setRefreshInterval: (state, action: PayloadAction<number>) => {
      state.refreshInterval = action.payload;
    },
    updateRealtimeMetrics: (state, action: PayloadAction<RealtimeMetrics>) => {
      state.realtimeMetrics = action.payload;
    },
    addAlert: (state, action: PayloadAction<SystemAlert>) => {
      state.alerts.unshift(action.payload);
    },
    removeAlert: (state, action: PayloadAction<string>) => {
      state.alerts = state.alerts.filter(alert => alert.id !== action.payload);
    },
  },
  extraReducers: (builder) => {
    // Fetch Dashboard Stats
    builder
      .addCase(fetchDashboardStats.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchDashboardStats.fulfilled, (state, action) => {
        state.isLoading = false;
        state.stats = action.payload;
      })
      .addCase(fetchDashboardStats.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.error.message || 'Failed to fetch dashboard stats';
      });

    // Fetch Realtime Metrics
    builder
      .addCase(fetchRealtimeMetrics.fulfilled, (state, action) => {
        state.realtimeMetrics = action.payload;
      });

    // Fetch System Alerts
    builder
      .addCase(fetchSystemAlerts.fulfilled, (state, action) => {
        state.alerts = action.payload;
      });

    // Acknowledge Alert
    builder
      .addCase(acknowledgeAlert.fulfilled, (state, action) => {
        state.alerts = state.alerts.map(alert =>
          alert.id === action.payload
            ? { ...alert, acknowledged: true }
            : alert
        );
      });
  },
});

export const {
  setTimeRange,
  toggleAutoRefresh,
  setRefreshInterval,
  updateRealtimeMetrics,
  addAlert,
  removeAlert,
} = dashboardSlice.actions;

export default dashboardSlice.reducer;