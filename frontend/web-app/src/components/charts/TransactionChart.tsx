import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  ToggleButton,
  ToggleButtonGroup,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  useTheme,
  CircularProgress,
  Grid,
  Chip,
  Button,
  Alert,
  IconButton,
  Tooltip,
} from '@mui/material';
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
} from 'recharts';
import { format } from 'date-fns';
import { formatCurrency } from '../../utils/helpers';
import analyticsService, { TransactionAnalytics, PeriodType as ServicePeriodType, ChartDataPoint } from '../../services/analyticsService';
import { useAppSelector } from '../../hooks/redux';
import toast from 'react-hot-toast';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExportIcon from '@mui/icons-material/GetApp';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';;

interface TransactionChartProps {
  walletId?: string;
}

type ChartType = 'line' | 'bar' | 'area' | 'pie';
type PeriodType = '7d' | '30d' | '90d' | '1y';

const TransactionChart: React.FC<TransactionChartProps> = ({ walletId }) => {
  const theme = useTheme();
  const [chartType, setChartType] = useState<ChartType>('line');
  const [period, setPeriod] = useState<PeriodType>('30d');
  const [loading, setLoading] = useState(true);
  const [analytics, setAnalytics] = useState<TransactionAnalytics | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAppSelector((state) => state.auth);

  useEffect(() => {
    fetchChartData();
  }, [walletId, period]);

  const fetchChartData = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const analyticsData = await analyticsService.getTransactionAnalytics(
        period as ServicePeriodType,
        walletId || user?.defaultWalletId
      );
      
      setAnalytics(analyticsData);
    } catch (error: any) {
      console.error('Failed to fetch chart data:', error);
      setError(error.message || 'Failed to load chart data');
      toast.error('Failed to load chart data');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    await fetchChartData();
  };

  const handleExport = async () => {
    try {
      setLoading(true);
      const blob = await analyticsService.exportAnalytics(
        period as ServicePeriodType,
        'pdf',
        walletId || user?.defaultWalletId,
        true
      );
      
      // Create download link
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `transaction-analytics-${period}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      
      toast.success('Analytics exported successfully');
    } catch (error: any) {
      console.error('Export failed:', error);
      toast.error('Failed to export analytics');
    } finally {
      setLoading(false);
    }
  };

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <Card sx={{ p: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            {label}
          </Typography>
          {payload.map((entry: any, index: number) => (
            <Typography
              key={index}
              variant="body2"
              sx={{ color: entry.color, display: 'flex', alignItems: 'center', gap: 1 }}
            >
              <Box
                sx={{
                  width: 12,
                  height: 12,
                  backgroundColor: entry.color,
                  borderRadius: '50%',
                }}
              />
              {entry.name}: {formatCurrency(entry.value)}
              {entry.dataKey === 'net' && (
                <Chip
                  size="small"
                  icon={entry.value >= 0 ? <TrendingUp /> : <TrendingDown />}
                  label={entry.value >= 0 ? 'Profit' : 'Loss'}
                  color={entry.value >= 0 ? 'success' : 'error'}
                  variant="outlined"
                  sx={{ ml: 1, height: 20 }}
                />
              )}
            </Typography>
          ))}
        </Card>
      );
    }
    return null;
  };

  const renderChart = () => {
    if (loading) {
      return (
        <Box display="flex" justifyContent="center" alignItems="center" height={400}>
          <CircularProgress size={50} />
        </Box>
      );
    }

    if (error) {
      return (
        <Box display="flex" justifyContent="center" alignItems="center" height={400}>
          <Alert 
            severity="error" 
            action={
              <Button color="inherit" size="small" onClick={handleRefresh}>
                Retry
              </Button>
            }
          >
            {error}
          </Alert>
        </Box>
      );
    }

    if (!analytics?.chartData?.length) {
      return (
        <Box display="flex" justifyContent="center" alignItems="center" height={400}>
          <Typography color="textSecondary">
            No transaction data available for the selected period
          </Typography>
        </Box>
      );
    }

    switch (chartType) {
      case 'line':
        return (
          <ResponsiveContainer width="100%" height={400}>
            <LineChart data={analytics.chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
              <XAxis dataKey="date" stroke={theme.palette.text.secondary} />
              <YAxis stroke={theme.palette.text.secondary} />
              <ChartTooltip content={<CustomTooltip />} />
              <Legend />
              <Line
                type="monotone"
                dataKey="income"
                stroke={theme.palette.success.main}
                strokeWidth={3}
                dot={{ r: 5, fill: theme.palette.success.main }}
                activeDot={{ r: 7, fill: theme.palette.success.dark }}
                name="Income"
              />
              <Line
                type="monotone"
                dataKey="expense"
                stroke={theme.palette.error.main}
                strokeWidth={3}
                dot={{ r: 5, fill: theme.palette.error.main }}
                activeDot={{ r: 7, fill: theme.palette.error.dark }}
                name="Expenses"
              />
              <Line
                type="monotone"
                dataKey="net"
                stroke={theme.palette.primary.main}
                strokeWidth={2}
                strokeDasharray="5 5"
                dot={{ r: 4, fill: theme.palette.primary.main }}
                name="Net Flow"
              />
            </LineChart>
          </ResponsiveContainer>
        );
      
      case 'area':
        return (
          <ResponsiveContainer width="100%" height={400}>
            <AreaChart data={analytics.chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
              <XAxis dataKey="date" stroke={theme.palette.text.secondary} />
              <YAxis stroke={theme.palette.text.secondary} />
              <ChartTooltip content={<CustomTooltip />} />
              <Legend />
              <Area
                type="monotone"
                dataKey="income"
                stackId="1"
                stroke={theme.palette.success.main}
                fill={theme.palette.success.light}
                name="Income"
              />
              <Area
                type="monotone"
                dataKey="expense"
                stackId="2"
                stroke={theme.palette.error.main}
                fill={theme.palette.error.light}
                name="Expenses"
              />
            </AreaChart>
          </ResponsiveContainer>
        );
      
      case 'bar':
        return (
          <ResponsiveContainer width="100%" height={400}>
            <BarChart data={analytics.chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
              <XAxis dataKey="date" stroke={theme.palette.text.secondary} />
              <YAxis stroke={theme.palette.text.secondary} />
              <ChartTooltip content={<CustomTooltip />} />
              <Legend />
              <Bar dataKey="income" fill={theme.palette.success.main} name="Income" />
              <Bar dataKey="expense" fill={theme.palette.error.main} name="Expenses" />
            </BarChart>
          </ResponsiveContainer>
        );
      
      case 'pie':
        return (
          <ResponsiveContainer width="100%" height={400}>
            <PieChart>
              <Pie
                data={analytics.categories}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percentage }) => `${name} ${percentage?.toFixed(1)}%`}
                outerRadius={140}
                fill="#8884d8"
                dataKey="value"
              >
                {analytics.categories.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} />
                ))}
              </Pie>
              <ChartTooltip 
                formatter={(value: number, name: string) => [
                  formatCurrency(value), 
                  name
                ]} 
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        );
      
      default:
        return null;
    }
  };

  const summary = analytics?.summary;

  return (
    <Box>
      <Grid container spacing={3}>
        {/* Enhanced Summary Cards */}
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={2}>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Total Income
                  </Typography>
                  <Typography variant="h5" color="success.main" fontWeight="bold">
                    {formatCurrency(summary?.totalIncome || 0)}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    Avg: {formatCurrency(summary?.avgDailyIncome || 0)}/day
                  </Typography>
                </Box>
                <TrendingUp sx={{ fontSize: 40, color: theme.palette.success.main, opacity: 0.3 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={2}>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Total Expenses
                  </Typography>
                  <Typography variant="h5" color="error.main" fontWeight="bold">
                    {formatCurrency(summary?.totalExpense || 0)}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    Avg: {formatCurrency(summary?.avgDailyExpense || 0)}/day
                  </Typography>
                </Box>
                <TrendingDown sx={{ fontSize: 40, color: theme.palette.error.main, opacity: 0.3 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={2}>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Net Flow
                  </Typography>
                  <Typography
                    variant="h5"
                    color={summary?.netFlow && summary.netFlow >= 0 ? 'success.main' : 'error.main'}
                    fontWeight="bold"
                  >
                    {summary?.netFlow && summary.netFlow >= 0 ? '+' : ''}{formatCurrency(summary?.netFlow || 0)}
                  </Typography>
                  <Chip
                    size="small"
                    label={summary?.netFlow && summary.netFlow >= 0 ? 'Surplus' : 'Deficit'}
                    color={summary?.netFlow && summary.netFlow >= 0 ? 'success' : 'error'}
                    variant="outlined"
                  />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} sm={6} md={3}>
          <Card elevation={2}>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Savings Rate
                  </Typography>
                  <Typography variant="h5" color="primary.main" fontWeight="bold">
                    {summary?.totalIncome && summary.totalIncome > 0
                      ? Math.round((summary.netFlow / summary.totalIncome) * 100)
                      : 0}%
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    of total income
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Additional Metrics */}
        <Grid item xs={12} sm={6} md={4}>
          <Card elevation={1}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Total Transactions
              </Typography>
              <Typography variant="h6" fontWeight="bold">
                {summary?.transactionCount || 0}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <Card elevation={1}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Average Transaction
              </Typography>
              <Typography variant="h6" fontWeight="bold">
                {formatCurrency(summary?.averageTransactionSize || 0)}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <Card elevation={1}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Largest Transaction
              </Typography>
              <Typography variant="h6" fontWeight="bold" color="primary.main">
                {formatCurrency(summary?.largestTransaction || 0)}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ mt: 3 }} elevation={3}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={3} flexWrap="wrap" gap={2}>
            <Typography variant="h5" fontWeight="bold">
              Transaction Analytics
            </Typography>
            
            <Box display="flex" gap={2} alignItems="center" flexWrap="wrap">
              <FormControl size="small" sx={{ minWidth: 140 }}>
                <InputLabel>Time Period</InputLabel>
                <Select
                  value={period}
                  label="Time Period"
                  onChange={(e) => setPeriod(e.target.value as PeriodType)}
                >
                  <MenuItem value="7d">Last 7 days</MenuItem>
                  <MenuItem value="30d">Last 30 days</MenuItem>
                  <MenuItem value="90d">Last 90 days</MenuItem>
                  <MenuItem value="1y">Last year</MenuItem>
                </Select>
              </FormControl>
              
              <ToggleButtonGroup
                value={chartType}
                exclusive
                onChange={(e, value) => value && setChartType(value)}
                size="small"
              >
                <ToggleButton value="line">
                  <Tooltip title="Line Chart">
                    <span>Line</span>
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="area">
                  <Tooltip title="Area Chart">
                    <span>Area</span>
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="bar">
                  <Tooltip title="Bar Chart">
                    <span>Bar</span>
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="pie">
                  <Tooltip title="Category Breakdown">
                    <span>Categories</span>
                  </Tooltip>
                </ToggleButton>
              </ToggleButtonGroup>

              <Tooltip title="Refresh Data">
                <IconButton onClick={handleRefresh} disabled={loading}>
                  <RefreshIcon />
                </IconButton>
              </Tooltip>

              <Tooltip title="Export Analytics">
                <IconButton onClick={handleExport} disabled={loading}>
                  <ExportIcon />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
          
          {renderChart()}

          {/* Insights */}
          {analytics?.trends && (
            <Box mt={3}>
              <Typography variant="h6" gutterBottom>
                Insights & Trends
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <Alert 
                    severity={analytics.trends.incomeGrowth >= 0 ? 'success' : 'warning'}
                    variant="outlined"
                  >
                    <Typography variant="body2">
                      <strong>Income Trend:</strong> {analytics.trends.incomeGrowth >= 0 ? '↗️' : '↘️'} 
                      {Math.abs(analytics.trends.incomeGrowth).toFixed(1)}% vs previous period
                    </Typography>
                  </Alert>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Alert 
                    severity={analytics.trends.expenseGrowth <= 0 ? 'success' : 'warning'}
                    variant="outlined"
                  >
                    <Typography variant="body2">
                      <strong>Expense Trend:</strong> {analytics.trends.expenseGrowth >= 0 ? '↗️' : '↘️'} 
                      {Math.abs(analytics.trends.expenseGrowth).toFixed(1)}% vs previous period
                    </Typography>
                  </Alert>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Alert severity="info" variant="outlined">
                    <Typography variant="body2">
                      <strong>Savings Rate:</strong> {analytics.trends.savingsRate.toFixed(1)}% of income
                    </Typography>
                  </Alert>
                </Grid>
              </Grid>
            </Box>
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

export default TransactionChart;