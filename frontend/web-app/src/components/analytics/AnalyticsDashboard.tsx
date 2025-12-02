import React, { useState, useEffect, useMemo } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Button,
  Tabs,
  Tab,
  Paper,
  Chip,
  IconButton,
  Tooltip,
  LinearProgress,
  Alert,
  Skeleton,
  useTheme,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import PeopleIcon from '@mui/icons-material/People';
import TransactionIcon from '@mui/icons-material/ShoppingCart';
import WarningIcon from '@mui/icons-material/Warning';
import SpeedIcon from '@mui/icons-material/Speed';
import TimelineIcon from '@mui/icons-material/Timeline';
import PieChartIcon from '@mui/icons-material/PieChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import RefreshIcon from '@mui/icons-material/Refresh';
import DownloadIcon from '@mui/icons-material/Download';
import FilterIcon from '@mui/icons-material/FilterList';
import DateRangeIcon from '@mui/icons-material/DateRange';;
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as ChartTooltip,
  Legend,
  ResponsiveContainer,
  RadialBarChart,
  RadialBar,
  Treemap,
  Sankey,
} from 'recharts';
import { useDispatch, useSelector } from 'react-redux';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { 
  fetchAnalytics, 
  updateFilters,
  exportReport 
} from '../../store/slices/analyticsSlice';
import { formatCurrency, formatNumber, formatPercentage } from '../../utils/formatters';
import MetricCard from '../common/MetricCard';
import ChartCard from '../common/ChartCard';
import HeatMap from '../charts/HeatMap';
import FunnelChart from '../charts/FunnelChart';
import RealTimeMetrics from './RealTimeMetrics';
import PredictiveInsights from './PredictiveInsights';

interface AnalyticsDashboardProps {
  type?: 'executive' | 'operational' | 'financial' | 'user';
}

const COLORS = ['#8884d8', '#82ca9d', '#ffc658', '#ff7c7c', '#8dd1e1', '#d084d0'];

const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({ type = 'executive' }) => {
  const theme = useTheme();
  const dispatch = useDispatch();
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [timeRange, setTimeRange] = useState('7d');
  const [currency, setCurrency] = useState('USD');
  const [refreshing, setRefreshing] = useState(false);
  
  const { 
    metrics, 
    charts, 
    insights,
    predictions,
    loading, 
    error 
  } = useSelector((state: any) => state.analytics);

  useEffect(() => {
    loadAnalytics();
  }, [timeRange, currency, type]);

  const loadAnalytics = async () => {
    setRefreshing(true);
    await dispatch(fetchAnalytics({ 
      timeRange, 
      currency, 
      dashboardType: type 
    }));
    setRefreshing(false);
  };

  const handleExport = () => {
    dispatch(exportReport({
      format: 'pdf',
      timeRange,
      currency,
      dashboardType: type,
    }));
  };

  const renderExecutiveDashboard = () => (
    <>
      {/* Key Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Revenue"
            value={formatCurrency(metrics.totalRevenue, currency)}
            change={metrics.revenueChange}
            icon={<MoneyIcon />}
            trend={metrics.revenueChange > 0 ? 'up' : 'down'}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Active Users"
            value={formatNumber(metrics.activeUsers)}
            change={metrics.userGrowth}
            icon={<PeopleIcon />}
            trend={metrics.userGrowth > 0 ? 'up' : 'down'}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Transaction Volume"
            value={formatNumber(metrics.transactionVolume)}
            change={metrics.volumeChange}
            icon={<TransactionIcon />}
            trend={metrics.volumeChange > 0 ? 'up' : 'down'}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Success Rate"
            value={formatPercentage(metrics.successRate)}
            change={metrics.successRateChange}
            icon={<SpeedIcon />}
            trend={metrics.successRateChange > 0 ? 'up' : 'down'}
            loading={loading}
          />
        </Grid>
      </Grid>

      {/* Revenue Trends */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} lg={8}>
          <ChartCard title="Revenue Trends" loading={loading}>
            <ResponsiveContainer width="100%" height={400}>
              <AreaChart data={charts.revenueTrend}>
                <defs>
                  <linearGradient id="colorRevenue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={theme.palette.primary.main} stopOpacity={0.8}/>
                    <stop offset="95%" stopColor={theme.palette.primary.main} stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis />
                <ChartTooltip 
                  formatter={(value: any) => formatCurrency(value, currency)}
                />
                <Area
                  type="monotone"
                  dataKey="revenue"
                  stroke={theme.palette.primary.main}
                  fillOpacity={1}
                  fill="url(#colorRevenue)"
                />
                <Line
                  type="monotone"
                  dataKey="target"
                  stroke={theme.palette.warning.main}
                  strokeDasharray="5 5"
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={4}>
          <ChartCard title="Revenue by Category" loading={loading}>
            <ResponsiveContainer width="100%" height={400}>
              <PieChart>
                <Pie
                  data={charts.revenueByCategory}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                  outerRadius={120}
                  fill="#8884d8"
                  dataKey="value"
                >
                  {charts.revenueByCategory?.map((entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <ChartTooltip formatter={(value: any) => formatCurrency(value, currency)} />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>

      {/* Geographic Distribution */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12}>
          <ChartCard title="Geographic Distribution" loading={loading}>
            <HeatMap
              data={charts.geographicData}
              valueField="transactionVolume"
              tooltipContent={(data: any) => (
                <div>
                  <Typography variant="subtitle2">{data.country}</Typography>
                  <Typography variant="body2">
                    Volume: {formatNumber(data.transactionVolume)}
                  </Typography>
                  <Typography variant="body2">
                    Revenue: {formatCurrency(data.revenue, currency)}
                  </Typography>
                </div>
              )}
            />
          </ChartCard>
        </Grid>
      </Grid>

      {/* User Funnel */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} md={6}>
          <ChartCard title="User Acquisition Funnel" loading={loading}>
            <FunnelChart
              data={charts.userFunnel}
              height={300}
            />
          </ChartCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <ChartCard title="Top Merchants" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={charts.topMerchants} layout="horizontal">
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis type="number" />
                <YAxis dataKey="name" type="category" />
                <ChartTooltip formatter={(value: any) => formatCurrency(value, currency)} />
                <Bar dataKey="revenue" fill={theme.palette.primary.main} />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>

      {/* Insights */}
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Key Insights & Recommendations
              </Typography>
              <Grid container spacing={2}>
                {insights.map((insight: any, index: number) => (
                  <Grid item xs={12} md={6} key={index}>
                    <Alert 
                      severity={insight.type}
                      icon={insight.type === 'warning' ? <WarningIcon /> : <TrendingUpIcon />}
                    >
                      <Typography variant="subtitle2">{insight.title}</Typography>
                      <Typography variant="body2">{insight.description}</Typography>
                      {insight.action && (
                        <Button size="small" sx={{ mt: 1 }}>
                          {insight.action}
                        </Button>
                      )}
                    </Alert>
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );

  const renderOperationalDashboard = () => (
    <>
      {/* Real-time Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12}>
          <RealTimeMetrics />
        </Grid>
      </Grid>

      {/* System Performance */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} lg={6}>
          <ChartCard title="Transaction Processing Time" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={charts.processingTime}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <ChartTooltip />
                <Line type="monotone" dataKey="p50" stroke="#82ca9d" name="Median" />
                <Line type="monotone" dataKey="p95" stroke="#ffc658" name="95th Percentile" />
                <Line type="monotone" dataKey="p99" stroke="#ff7c7c" name="99th Percentile" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={6}>
          <ChartCard title="Error Rate by Service" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={charts.errorRates}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="service" />
                <YAxis />
                <ChartTooltip formatter={(value: any) => `${value}%`} />
                <Bar dataKey="errorRate" fill="#ff7c7c" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>

      {/* Queue Metrics */}
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <ChartCard title="Message Queue Performance" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={charts.queueMetrics}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <ChartTooltip />
                <Area type="monotone" dataKey="depth" stackId="1" stroke="#8884d8" fill="#8884d8" />
                <Area type="monotone" dataKey="processing" stackId="1" stroke="#82ca9d" fill="#82ca9d" />
                <Area type="monotone" dataKey="failed" stackId="1" stroke="#ff7c7c" fill="#ff7c7c" />
              </AreaChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>
    </>
  );

  const renderFinancialDashboard = () => (
    <>
      {/* Financial Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Gross Revenue"
            value={formatCurrency(metrics.grossRevenue, currency)}
            change={metrics.grossRevenueChange}
            icon={<MoneyIcon />}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Net Revenue"
            value={formatCurrency(metrics.netRevenue, currency)}
            change={metrics.netRevenueChange}
            icon={<MoneyIcon />}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Processing Fees"
            value={formatCurrency(metrics.processingFees, currency)}
            change={metrics.feesChange}
            icon={<MoneyIcon />}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Margin"
            value={formatPercentage(metrics.margin)}
            change={metrics.marginChange}
            icon={<TrendingUpIcon />}
            loading={loading}
          />
        </Grid>
      </Grid>

      {/* Cash Flow */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12}>
          <ChartCard title="Cash Flow Analysis" loading={loading}>
            <ResponsiveContainer width="100%" height={400}>
              <AreaChart data={charts.cashFlow}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis />
                <ChartTooltip formatter={(value: any) => formatCurrency(value, currency)} />
                <Area type="monotone" dataKey="inflow" stackId="1" stroke="#82ca9d" fill="#82ca9d" />
                <Area type="monotone" dataKey="outflow" stackId="1" stroke="#ff7c7c" fill="#ff7c7c" />
                <Line type="monotone" dataKey="net" stroke="#8884d8" strokeWidth={3} />
              </AreaChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>

      {/* Revenue Breakdown */}
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <ChartCard title="Revenue by Payment Method" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <Treemap
                data={charts.revenueByPaymentMethod}
                dataKey="value"
                aspectRatio={4/3}
                stroke="#fff"
                fill="#8884d8"
              >
                <ChartTooltip formatter={(value: any) => formatCurrency(value, currency)} />
              </Treemap>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <ChartCard title="Currency Distribution" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <RadialBarChart cx="50%" cy="50%" innerRadius="10%" outerRadius="80%" data={charts.currencyDistribution}>
                <RadialBar dataKey="value" cornerRadius={10} fill="#82ca9d" />
                <Legend />
                <ChartTooltip />
              </RadialBarChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>
    </>
  );

  const renderUserDashboard = () => (
    <>
      {/* User Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Users"
            value={formatNumber(metrics.totalUsers)}
            change={metrics.userGrowthRate}
            icon={<PeopleIcon />}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Daily Active Users"
            value={formatNumber(metrics.dailyActiveUsers)}
            change={metrics.dauChange}
            icon={<PeopleIcon />}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="User Retention"
            value={formatPercentage(metrics.retentionRate)}
            change={metrics.retentionChange}
            icon={<TrendingUpIcon />}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Avg. Transaction/User"
            value={formatCurrency(metrics.avgTransactionPerUser, currency)}
            change={metrics.avgTransactionChange}
            icon={<TransactionIcon />}
            loading={loading}
          />
        </Grid>
      </Grid>

      {/* User Behavior */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} lg={8}>
          <ChartCard title="User Activity Patterns" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={charts.userActivity}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="hour" />
                <YAxis />
                <ChartTooltip />
                <Line type="monotone" dataKey="weekday" stroke="#8884d8" name="Weekday" />
                <Line type="monotone" dataKey="weekend" stroke="#82ca9d" name="Weekend" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={4}>
          <ChartCard title="User Segments" loading={loading}>
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={charts.userSegments}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  fill="#8884d8"
                  dataKey="value"
                  label
                >
                  {charts.userSegments?.map((entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <ChartTooltip />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
        </Grid>
      </Grid>

      {/* Cohort Analysis */}
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <ChartCard title="Cohort Retention Analysis" loading={loading}>
            <Box sx={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ padding: 8, textAlign: 'left' }}>Cohort</th>
                    {[0, 1, 2, 3, 4, 5, 6].map(week => (
                      <th key={week} style={{ padding: 8, textAlign: 'center' }}>
                        Week {week}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {charts.cohortData?.map((cohort: any) => (
                    <tr key={cohort.name}>
                      <td style={{ padding: 8 }}>{cohort.name}</td>
                      {cohort.retention.map((value: number, index: number) => (
                        <td
                          key={index}
                          style={{
                            padding: 8,
                            textAlign: 'center',
                            backgroundColor: `rgba(130, 202, 157, ${value / 100})`,
                          }}
                        >
                          {value}%
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </Box>
          </ChartCard>
        </Grid>
      </Grid>
    </>
  );

  const dashboardContent = useMemo(() => {
    switch (type) {
      case 'operational':
        return renderOperationalDashboard();
      case 'financial':
        return renderFinancialDashboard();
      case 'user':
        return renderUserDashboard();
      default:
        return renderExecutiveDashboard();
    }
  }, [type, metrics, charts, insights, loading]);

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">
          {type.charAt(0).toUpperCase() + type.slice(1)} Analytics Dashboard
        </Typography>
        <Box display="flex" gap={2}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Time Range</InputLabel>
            <Select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              label="Time Range"
            >
              <MenuItem value="1d">Last 24 Hours</MenuItem>
              <MenuItem value="7d">Last 7 Days</MenuItem>
              <MenuItem value="30d">Last 30 Days</MenuItem>
              <MenuItem value="90d">Last 90 Days</MenuItem>
              <MenuItem value="1y">Last Year</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 100 }}>
            <InputLabel>Currency</InputLabel>
            <Select
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              label="Currency"
            >
              <MenuItem value="USD">USD</MenuItem>
              <MenuItem value="EUR">EUR</MenuItem>
              <MenuItem value="GBP">GBP</MenuItem>
            </Select>
          </FormControl>
          <Tooltip title="Refresh">
            <IconButton onClick={loadAnalytics} disabled={refreshing}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Export Report">
            <IconButton onClick={handleExport}>
              <DownloadIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* Loading Progress */}
      {refreshing && <LinearProgress sx={{ mb: 2 }} />}

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* Tabs for different views */}
      <Paper sx={{ mb: 3 }}>
        <Tabs value={selectedTab} onChange={(e, v) => setSelectedTab(v)}>
          <Tab label="Overview" />
          <Tab label="Trends" />
          <Tab label="Predictions" />
          <Tab label="Insights" />
        </Tabs>
      </Paper>

      {/* Dashboard Content */}
      {selectedTab === 0 && dashboardContent}
      {selectedTab === 1 && <Box>Trends Content</Box>}
      {selectedTab === 2 && <PredictiveInsights predictions={predictions} />}
      {selectedTab === 3 && <Box>Insights Content</Box>}
    </Box>
  );
};

export default AnalyticsDashboard;