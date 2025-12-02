import React, { useState, useMemo } from 'react';
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
  Paper,
  Chip,
  IconButton,
  Tooltip,
  useTheme,
  alpha,
  ButtonGroup,
  Button,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import ReceiptIcon from '@mui/icons-material/Receipt';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import PieChartIcon from '@mui/icons-material/PieChart';
import ChartIcon from '@mui/icons-material/ShowChart';
import DownloadIcon from '@mui/icons-material/FileDownload';
import RefreshIcon from '@mui/icons-material/Refresh';;
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  Legend,
} from 'recharts';
import { format, subDays, startOfDay, endOfDay } from 'date-fns';
import { useQuery } from 'react-query';
import { formatCurrency } from '../../utils/formatters';

// Mock data interfaces
interface TransactionMetrics {
  totalVolume: number;
  totalCount: number;
  averageAmount: number;
  volumeChange: number;
  countChange: number;
  topCategories: Array<{
    category: string;
    amount: number;
    count: number;
    percentage: number;
  }>;
}

interface TimeSeriesData {
  date: string;
  volume: number;
  count: number;
  incoming: number;
  outgoing: number;
}

interface CategoryBreakdown {
  category: string;
  amount: number;
  count: number;
  color: string;
}

interface PaymentMethodStats {
  method: string;
  volume: number;
  count: number;
  avgAmount: number;
}

const TransactionAnalyticsDashboard: React.FC = () => {
  const theme = useTheme();
  const [timeRange, setTimeRange] = useState('30d');
  const [chartType, setChartType] = useState<'line' | 'area' | 'bar'>('area');

  // Mock API calls - replace with actual service calls
  const { data: metrics } = useQuery(
    ['transaction-metrics', timeRange],
    async (): Promise<TransactionMetrics> => {
      // Mock data
      return {
        totalVolume: 125750.50,
        totalCount: 342,
        averageAmount: 367.84,
        volumeChange: 12.5,
        countChange: -3.2,
        topCategories: [
          { category: 'Food & Dining', amount: 35240.20, count: 89, percentage: 28 },
          { category: 'Shopping', amount: 28450.75, count: 67, percentage: 23 },
          { category: 'Transportation', amount: 18750.30, count: 45, percentage: 15 },
          { category: 'Entertainment', amount: 15320.80, count: 38, percentage: 12 },
          { category: 'Bills & Utilities', amount: 12890.45, count: 28, percentage: 10 },
        ],
      };
    }
  );

  const { data: timeSeriesData } = useQuery(
    ['transaction-timeseries', timeRange],
    async (): Promise<TimeSeriesData[]> => {
      // Generate mock time series data
      const days = timeRange === '7d' ? 7 : timeRange === '30d' ? 30 : 90;
      const data: TimeSeriesData[] = [];
      
      for (let i = days - 1; i >= 0; i--) {
        const date = subDays(new Date(), i);
        data.push({
          date: format(date, 'MMM dd'),
          volume: Math.random() * 5000 + 1000,
          count: Math.floor(Math.random() * 20) + 5,
          incoming: Math.random() * 3000 + 500,
          outgoing: Math.random() * 2000 + 500,
        });
      }
      
      return data;
    }
  );

  const { data: categoryData } = useQuery(
    ['transaction-categories', timeRange],
    async (): Promise<CategoryBreakdown[]> => {
      return [
        { category: 'Food & Dining', amount: 35240.20, count: 89, color: '#FF6B6B' },
        { category: 'Shopping', amount: 28450.75, count: 67, color: '#4ECDC4' },
        { category: 'Transportation', amount: 18750.30, count: 45, color: '#45B7D1' },
        { category: 'Entertainment', amount: 15320.80, count: 38, color: '#96CEB4' },
        { category: 'Bills & Utilities', amount: 12890.45, count: 28, color: '#FFEAA7' },
        { category: 'Other', amount: 15097.00, count: 75, color: '#DDA0DD' },
      ];
    }
  );

  const { data: paymentMethodData } = useQuery(
    ['payment-methods', timeRange],
    async (): Promise<PaymentMethodStats[]> => {
      return [
        { method: 'Bank Transfer', volume: 85420.30, count: 156, avgAmount: 547.57 },
        { method: 'Credit Card', volume: 32150.75, count: 128, avgAmount: 251.18 },
        { method: 'Debit Card', volume: 8179.45, count: 58, avgAmount: 141.02 },
      ];
    }
  );

  const StatCard: React.FC<{
    title: string;
    value: string | number;
    change?: number;
    icon: React.ReactNode;
    color: string;
  }> = ({ title, value, change, icon, color }) => (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box>
            <Typography color="text.secondary" gutterBottom variant="body2">
              {title}
            </Typography>
            <Typography variant="h4" component="div" fontWeight="bold">
              {typeof value === 'number' ? formatCurrency(value) : value}
            </Typography>
            {change !== undefined && (
              <Box display="flex" alignItems="center" mt={1}>
                {change >= 0 ? (
                  <TrendingUpIcon sx={{ color: 'success.main', fontSize: 20, mr: 0.5 }} />
                ) : (
                  <TrendingDownIcon sx={{ color: 'error.main', fontSize: 20, mr: 0.5 }} />
                )}
                <Typography
                  variant="body2"
                  color={change >= 0 ? 'success.main' : 'error.main'}
                  fontWeight="medium"
                >
                  {Math.abs(change).toFixed(1)}%
                </Typography>
              </Box>
            )}
          </Box>
          <Box
            sx={{
              p: 2,
              borderRadius: 2,
              bgcolor: alpha(color, 0.1),
              color: color,
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <Paper sx={{ p: 2, border: 1, borderColor: 'divider' }}>
          <Typography variant="subtitle2" gutterBottom>
            {label}
          </Typography>
          {payload.map((entry: any, index: number) => (
            <Typography
              key={index}
              variant="body2"
              sx={{ color: entry.color }}
            >
              {entry.name}: {formatCurrency(entry.value)}
            </Typography>
          ))}
        </Paper>
      );
    }
    return null;
  };

  const renderChart = () => {
    if (!timeSeriesData) return null;

    const commonProps = {
      data: timeSeriesData,
      margin: { top: 5, right: 30, left: 20, bottom: 5 },
    };

    switch (chartType) {
      case 'line':
        return (
          <LineChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" />
            <YAxis />
            <RechartsTooltip content={<CustomTooltip />} />
            <Line
              type="monotone"
              dataKey="volume"
              stroke={theme.palette.primary.main}
              strokeWidth={3}
              dot={{ fill: theme.palette.primary.main, strokeWidth: 2, r: 4 }}
            />
          </LineChart>
        );
      case 'area':
        return (
          <AreaChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" />
            <YAxis />
            <RechartsTooltip content={<CustomTooltip />} />
            <Area
              type="monotone"
              dataKey="volume"
              stroke={theme.palette.primary.main}
              fill={alpha(theme.palette.primary.main, 0.3)}
            />
          </AreaChart>
        );
      case 'bar':
        return (
          <BarChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" />
            <YAxis />
            <RechartsTooltip content={<CustomTooltip />} />
            <Bar dataKey="volume" fill={theme.palette.primary.main} />
          </BarChart>
        );
      default:
        return null;
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" fontWeight="bold">
          Transaction Analytics
        </Typography>
        <Box display="flex" gap={2} alignItems="center">
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Time Range</InputLabel>
            <Select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              label="Time Range"
            >
              <MenuItem value="7d">Last 7 days</MenuItem>
              <MenuItem value="30d">Last 30 days</MenuItem>
              <MenuItem value="90d">Last 90 days</MenuItem>
            </Select>
          </FormControl>
          <Tooltip title="Export Data">
            <IconButton>
              <DownloadIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title="Refresh">
            <IconButton>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* Key Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Total Volume"
            value={metrics?.totalVolume || 0}
            change={metrics?.volumeChange}
            icon={<MoneyIcon />}
            color={theme.palette.success.main}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Total Transactions"
            value={metrics?.totalCount || 0}
            change={metrics?.countChange}
            icon={<SwapIcon />}
            color={theme.palette.info.main}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Average Amount"
            value={metrics?.averageAmount || 0}
            icon={<ReceiptIcon />}
            color={theme.palette.warning.main}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Active Categories"
            value={metrics?.topCategories.length || 0}
            icon={<PieChartIcon />}
            color={theme.palette.secondary.main}
          />
        </Grid>
      </Grid>

      {/* Charts Row */}
      <Grid container spacing={3} mb={3}>
        {/* Volume Over Time */}
        <Grid item xs={12} lg={8}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6" fontWeight="bold">
                  Transaction Volume Over Time
                </Typography>
                <ButtonGroup size="small">
                  <Button
                    variant={chartType === 'area' ? 'contained' : 'outlined'}
                    onClick={() => setChartType('area')}
                  >
                    Area
                  </Button>
                  <Button
                    variant={chartType === 'line' ? 'contained' : 'outlined'}
                    onClick={() => setChartType('line')}
                  >
                    Line
                  </Button>
                  <Button
                    variant={chartType === 'bar' ? 'contained' : 'outlined'}
                    onClick={() => setChartType('bar')}
                  >
                    Bar
                  </Button>
                </ButtonGroup>
              </Box>
              <Box height={300}>
                <ResponsiveContainer width="100%" height="100%">
                  {renderChart()}
                </ResponsiveContainer>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Category Breakdown */}
        <Grid item xs={12} lg={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" fontWeight="bold" gutterBottom>
                Category Breakdown
              </Typography>
              <Box height={250}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={categoryData}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={100}
                      dataKey="amount"
                      nameKey="category"
                    >
                      {categoryData?.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <RechartsTooltip
                      formatter={(value: number) => formatCurrency(value)}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </Box>
              <Box mt={1}>
                {categoryData?.slice(0, 3).map((category) => (
                  <Box key={category.category} display="flex" alignItems="center" mb={1}>
                    <Box
                      sx={{
                        width: 12,
                        height: 12,
                        bgcolor: category.color,
                        borderRadius: 1,
                        mr: 1,
                      }}
                    />
                    <Typography variant="body2" sx={{ flex: 1 }}>
                      {category.category}
                    </Typography>
                    <Typography variant="body2" fontWeight="medium">
                      {formatCurrency(category.amount)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Detailed Analytics */}
      <Grid container spacing={3}>
        {/* Top Categories */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight="bold" gutterBottom>
                Top Categories
              </Typography>
              {metrics?.topCategories.map((category, index) => (
                <Box key={category.category} mb={2}>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="body2" fontWeight="medium">
                      {category.category}
                    </Typography>
                    <Typography variant="body2">
                      {formatCurrency(category.amount)}
                    </Typography>
                  </Box>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Box
                      sx={{
                        flex: 1,
                        height: 6,
                        bgcolor: 'grey.200',
                        borderRadius: 3,
                        overflow: 'hidden',
                      }}
                    >
                      <Box
                        sx={{
                          width: `${category.percentage}%`,
                          height: '100%',
                          bgcolor: theme.palette.primary.main,
                        }}
                      />
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {category.percentage}%
                    </Typography>
                  </Box>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* Payment Methods */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight="bold" gutterBottom>
                Payment Methods
              </Typography>
              {paymentMethodData?.map((method) => (
                <Box
                  key={method.method}
                  display="flex"
                  alignItems="center"
                  justifyContent="space-between"
                  py={2}
                  borderBottom={1}
                  borderColor="divider"
                >
                  <Box display="flex" alignItems="center" gap={2}>
                    <Box
                      sx={{
                        p: 1,
                        borderRadius: 1,
                        bgcolor: alpha(theme.palette.primary.main, 0.1),
                      }}
                    >
                      {method.method === 'Bank Transfer' ? (
                        <BankIcon sx={{ color: 'primary.main' }} />
                      ) : (
                        <CardIcon sx={{ color: 'primary.main' }} />
                      )}
                    </Box>
                    <Box>
                      <Typography variant="body2" fontWeight="medium">
                        {method.method}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {method.count} transactions
                      </Typography>
                    </Box>
                  </Box>
                  <Box textAlign="right">
                    <Typography variant="body2" fontWeight="medium">
                      {formatCurrency(method.volume)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Avg: {formatCurrency(method.avgAmount)}
                    </Typography>
                  </Box>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default TransactionAnalyticsDashboard;