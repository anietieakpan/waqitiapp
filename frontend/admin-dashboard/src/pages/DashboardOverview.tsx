import React, { useState, useEffect } from 'react';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  LinearProgress,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Alert,
} from '@mui/material';
import {
  TrendingUp,
  TrendingDown,
  People,
  AccountBalance,
  CreditCard,
  Security,
  MoreVert,
  Refresh,
} from '@mui/icons-material';
import { useQuery } from 'react-query';
import { format } from 'date-fns';

interface DashboardMetrics {
  totalUsers: number;
  activeUsers: number;
  totalTransactions: number;
  transactionVolume: number;
  revenue: number;
  failedTransactions: number;
  systemHealth: number;
  alerts: number;
}

interface TrendData {
  value: number;
  change: number;
  changeType: 'increase' | 'decrease';
}

const DashboardOverview: React.FC = () => {
  const [dateRange, setDateRange] = useState('7d');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const { data: metrics, isLoading, refetch } = useQuery<DashboardMetrics>(
    ['dashboard-metrics', dateRange],
    () => fetchDashboardMetrics(dateRange),
    { refetchInterval: 30000 }
  );

  const { data: trends } = useQuery<Record<string, TrendData>>(
    ['dashboard-trends', dateRange],
    () => fetchTrendData(dateRange)
  );

  const { data: alerts } = useQuery(
    'dashboard-alerts',
    () => fetchActiveAlerts(),
    { refetchInterval: 60000 }
  );

  const handleRefresh = () => {
    refetch();
  };

  const handleDateRangeChange = (range: string) => {
    setDateRange(range);
    setAnchorEl(null);
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <LinearProgress sx={{ width: '50%' }} />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" fontWeight="bold">
          Admin Dashboard
        </Typography>
        <Box display="flex" gap={2}>
          <Chip
            label={`Last ${dateRange}`}
            onClick={(e) => setAnchorEl(e.currentTarget)}
            variant="outlined"
          />
          <IconButton onClick={handleRefresh}>
            <Refresh />
          </IconButton>
        </Box>
      </Box>

      {/* Alerts */}
      {alerts && alerts.length > 0 && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          {alerts.length} active alert(s) require attention
        </Alert>
      )}

      {/* Key Metrics */}
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Users"
            value={metrics?.totalUsers || 0}
            trend={trends?.totalUsers}
            icon={<People />}
            color="primary"
            format="number"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Transaction Volume"
            value={metrics?.transactionVolume || 0}
            trend={trends?.transactionVolume}
            icon={<CreditCard />}
            color="success"
            format="currency"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Revenue"
            value={metrics?.revenue || 0}
            trend={trends?.revenue}
            icon={<TrendingUp />}
            color="info"
            format="currency"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="System Health"
            value={metrics?.systemHealth || 0}
            trend={trends?.systemHealth}
            icon={<Security />}
            color="warning"
            format="percentage"
          />
        </Grid>
      </Grid>

      {/* Secondary Metrics */}
      <Grid container spacing={3} sx={{ mt: 2 }}>
        <Grid item xs={12} sm={6} md={4}>
          <MetricCard
            title="Active Users"
            value={metrics?.activeUsers || 0}
            trend={trends?.activeUsers}
            icon={<People />}
            color="secondary"
            format="number"
            subtitle={`${((metrics?.activeUsers || 0) / (metrics?.totalUsers || 1) * 100).toFixed(1)}% of total`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <MetricCard
            title="Total Transactions"
            value={metrics?.totalTransactions || 0}
            trend={trends?.totalTransactions}
            icon={<AccountBalance />}
            color="primary"
            format="number"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <MetricCard
            title="Failed Transactions"
            value={metrics?.failedTransactions || 0}
            trend={trends?.failedTransactions}
            icon={<TrendingDown />}
            color="error"
            format="number"
            subtitle={`${((metrics?.failedTransactions || 0) / (metrics?.totalTransactions || 1) * 100).toFixed(2)}% failure rate`}
          />
        </Grid>
      </Grid>

      {/* Date Range Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        <MenuItem onClick={() => handleDateRangeChange('24h')}>Last 24 hours</MenuItem>
        <MenuItem onClick={() => handleDateRangeChange('7d')}>Last 7 days</MenuItem>
        <MenuItem onClick={() => handleDateRangeChange('30d')}>Last 30 days</MenuItem>
        <MenuItem onClick={() => handleDateRangeChange('90d')}>Last 90 days</MenuItem>
      </Menu>
    </Box>
  );
};

interface MetricCardProps {
  title: string;
  value: number;
  trend?: TrendData;
  icon: React.ReactNode;
  color: 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error';
  format: 'number' | 'currency' | 'percentage';
  subtitle?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  trend,
  icon,
  color,
  format,
  subtitle
}) => {
  const formatValue = (val: number, fmt: string) => {
    switch (fmt) {
      case 'currency':
        return new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD',
          notation: val > 1000000 ? 'compact' : 'standard'
        }).format(val);
      case 'percentage':
        return `${val.toFixed(1)}%`;
      case 'number':
        return new Intl.NumberFormat('en-US', {
          notation: val > 1000 ? 'compact' : 'standard'
        }).format(val);
      default:
        return val.toString();
    }
  };

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography color="textSecondary" gutterBottom variant="body2">
              {title}
            </Typography>
            <Typography variant="h4" component="div" fontWeight="bold">
              {formatValue(value, format)}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="textSecondary">
                {subtitle}
              </Typography>
            )}
            {trend && (
              <Box display="flex" alignItems="center" mt={1}>
                {trend.changeType === 'increase' ? (
                  <TrendingUp color="success" fontSize="small" />
                ) : (
                  <TrendingDown color="error" fontSize="small" />
                )}
                <Typography
                  variant="body2"
                  color={trend.changeType === 'increase' ? 'success.main' : 'error.main'}
                  sx={{ ml: 0.5 }}
                >
                  {trend.change > 0 ? '+' : ''}{trend.change.toFixed(1)}%
                </Typography>
              </Box>
            )}
          </Box>
          <Box
            sx={{
              p: 1,
              borderRadius: 1,
              backgroundColor: `${color}.light`,
              color: `${color}.main`
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};

// API Functions
const fetchDashboardMetrics = async (dateRange: string): Promise<DashboardMetrics> => {
  const response = await fetch(`/api/v1/admin/dashboard/metrics?range=${dateRange}`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
    }
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch dashboard metrics');
  }
  
  return response.json();
};

const fetchTrendData = async (dateRange: string): Promise<Record<string, TrendData>> => {
  const response = await fetch(`/api/v1/admin/dashboard/trends?range=${dateRange}`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
    }
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch trend data');
  }
  
  return response.json();
};

const fetchActiveAlerts = async () => {
  const response = await fetch('/api/v1/admin/alerts?status=active', {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
    }
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch alerts');
  }
  
  return response.json();
};

export default DashboardOverview;