import React, { useEffect, useState } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  IconButton,
  Menu,
  MenuItem,
  Chip,
  Alert,
  AlertTitle,
  LinearProgress,
  useTheme,
  alpha,
} from '@mui/material';
import {
  TrendingUp,
  TrendingDown,
  MoreVert,
  AccountBalance,
  People,
  SwapHoriz,
  Warning,
  CheckCircle,
  Error,
  Timer,
  Speed,
  Memory,
  Storage,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../store';
import {
  fetchDashboardStats,
  fetchRealtimeMetrics,
  fetchSystemAlerts,
  setTimeRange,
} from '../store/slices/dashboardSlice';
import { useSocket } from '../hooks/useSocket';

// Components
import MetricCard from '../components/dashboard/MetricCard';
import TransactionChart from '../components/dashboard/TransactionChart';
import UserActivityChart from '../components/dashboard/UserActivityChart';
import RevenueChart from '../components/dashboard/RevenueChart';
import GeographicMap from '../components/dashboard/GeographicMap';
import SystemHealthMonitor from '../components/dashboard/SystemHealthMonitor';
import RecentTransactions from '../components/dashboard/RecentTransactions';
import AlertsPanel from '../components/dashboard/AlertsPanel';
import QuickActions from '../components/dashboard/QuickActions';
import ComplianceStatus from '../components/dashboard/ComplianceStatus';
import FraudDetectionPanel from '../components/dashboard/FraudDetectionPanel';

const Dashboard: React.FC = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const socket = useSocket();
  
  const {
    stats,
    realtimeMetrics,
    alerts,
    isLoading,
    selectedTimeRange,
    autoRefresh,
  } = useAppSelector((state) => state.dashboard);

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  useEffect(() => {
    // Initial data fetch
    dispatch(fetchDashboardStats(selectedTimeRange));
    dispatch(fetchRealtimeMetrics());
    dispatch(fetchSystemAlerts());

    // Set up auto-refresh
    const interval = autoRefresh
      ? setInterval(() => {
          dispatch(fetchDashboardStats(selectedTimeRange));
          dispatch(fetchRealtimeMetrics());
        }, 30000) // 30 seconds
      : null;

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [dispatch, selectedTimeRange, autoRefresh]);

  useEffect(() => {
    if (!socket) return;

    // Subscribe to real-time updates
    socket.on('metrics:update', (data) => {
      dispatch(updateRealtimeMetrics(data));
    });

    socket.on('alert:new', (alert) => {
      dispatch(addAlert(alert));
    });

    socket.on('transaction:high-value', (transaction) => {
      // Handle high-value transaction alerts
      logHighValueTransaction(transaction, { 
        feature: 'admin_dashboard', 
        action: 'high_value_transaction_received' 
      });
    });

    return () => {
      socket.off('metrics:update');
      socket.off('alert:new');
      socket.off('transaction:high-value');
    };
  }, [socket, dispatch]);

  const handleTimeRangeChange = (range: string) => {
    dispatch(setTimeRange(range as any));
    setAnchorEl(null);
  };

  const criticalAlerts = alerts.filter(alert => alert.severity === 'critical');
  const hasIssues = criticalAlerts.length > 0 || (stats?.systemHealth.score || 100) < 90;

  return (
    <Box sx={{ flexGrow: 1 }}>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4" fontWeight="bold">
          System Dashboard
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <Chip
            label={`Last updated: ${new Date().toLocaleTimeString()}`}
            size="small"
            icon={<Timer />}
          />
          <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
            <MoreVert />
          </IconButton>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={() => setAnchorEl(null)}
          >
            <MenuItem onClick={() => handleTimeRangeChange('1h')}>Last Hour</MenuItem>
            <MenuItem onClick={() => handleTimeRangeChange('24h')}>Last 24 Hours</MenuItem>
            <MenuItem onClick={() => handleTimeRangeChange('7d')}>Last 7 Days</MenuItem>
            <MenuItem onClick={() => handleTimeRangeChange('30d')}>Last 30 Days</MenuItem>
          </Menu>
        </Box>
      </Box>

      {/* System Status Alert */}
      {hasIssues && (
        <Alert 
          severity="warning" 
          sx={{ mb: 3 }}
          action={
            <Chip
              label={`${criticalAlerts.length} Critical`}
              color="error"
              size="small"
            />
          }
        >
          <AlertTitle>System Requires Attention</AlertTitle>
          {criticalAlerts.length > 0 && `${criticalAlerts.length} critical alerts require immediate attention. `}
          {(stats?.systemHealth.score || 100) < 90 && 'System health is below optimal levels.'}
        </Alert>
      )}

      {/* Loading State */}
      {isLoading && <LinearProgress sx={{ mb: 2 }} />}

      {/* Key Metrics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Users"
            value={stats?.totalUsers || 0}
            change={stats?.userGrowth || 0}
            icon={<People />}
            color="primary"
            format="number"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Transaction Volume"
            value={stats?.transactionVolume || 0}
            change={stats?.volumeChange || 0}
            icon={<SwapHoriz />}
            color="success"
            format="currency"
            prefix="$"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Active Wallets"
            value={stats?.activeWallets || 0}
            change={stats?.walletGrowth || 0}
            icon={<AccountBalance />}
            color="info"
            format="number"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="System Health"
            value={stats?.systemHealth.score || 100}
            icon={<Speed />}
            color={stats?.systemHealth.score < 90 ? 'error' : 'success'}
            format="percentage"
            suffix="%"
          />
        </Grid>
      </Grid>

      {/* Real-time Metrics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              Transaction Activity
            </Typography>
            <TransactionChart data={realtimeMetrics?.transactions} />
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              System Resources
            </Typography>
            <SystemHealthMonitor 
              cpu={realtimeMetrics?.system.cpu || 0}
              memory={realtimeMetrics?.system.memory || 0}
              disk={realtimeMetrics?.system.disk || 0}
              network={realtimeMetrics?.system.network || 0}
            />
          </Paper>
        </Grid>
      </Grid>

      {/* Analytics Grid */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" gutterBottom>
              User Activity Trends
            </Typography>
            <UserActivityChart />
          </Paper>
        </Grid>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" gutterBottom>
              Revenue Analytics
            </Typography>
            <RevenueChart />
          </Paper>
        </Grid>
      </Grid>

      {/* Geographic Distribution */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12}>
          <Paper sx={{ p: 3, height: 500 }}>
            <Typography variant="h6" gutterBottom>
              Geographic Distribution
            </Typography>
            <GeographicMap data={stats?.geographicData} />
          </Paper>
        </Grid>
      </Grid>

      {/* Bottom Grid */}
      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" gutterBottom>
              Compliance Status
            </Typography>
            <ComplianceStatus />
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" gutterBottom>
              Fraud Detection
            </Typography>
            <FraudDetectionPanel />
          </Paper>
        </Grid>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" gutterBottom>
              System Alerts
            </Typography>
            <AlertsPanel alerts={alerts} />
          </Paper>
        </Grid>
      </Grid>

      {/* Recent Transactions */}
      <Box sx={{ mt: 3 }}>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Recent High-Value Transactions
          </Typography>
          <RecentTransactions />
        </Paper>
      </Box>

      {/* Quick Actions */}
      <Box sx={{ mt: 3 }}>
        <QuickActions />
      </Box>
    </Box>
  );
};

export default Dashboard;