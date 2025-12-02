import React, { useState, useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  Avatar,
  Chip,
  Button,
  IconButton,
  Tooltip,
  LinearProgress,
  useTheme,
  alpha,
  Divider,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import ReceiptIcon from '@mui/icons-material/Receipt';
import WalletIcon from '@mui/icons-material/AccountBalanceWallet';
import TimelineIcon from '@mui/icons-material/Timeline';
import DonutIcon from '@mui/icons-material/DonutLarge';
import BarChartIcon from '@mui/icons-material/BarChart';
import InfoIcon from '@mui/icons-material/Info';
import DownloadIcon from '@mui/icons-material/Download';
import CalendarIcon from '@mui/icons-material/CalendarMonth';
import CategoryIcon from '@mui/icons-material/Category';
import SpeedIcon from '@mui/icons-material/Speed';
import OfferIcon from '@mui/icons-material/LocalOffer';;
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip as ChartTooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Line, Bar, Doughnut } from 'react-chartjs-2';
import { format, startOfMonth, endOfMonth, eachDayOfInterval, parseISO } from 'date-fns';
import { Transaction, TransactionType, TransactionStatus } from '../../types/wallet';
import { formatCurrency, formatNumber, formatPercentage } from '../../utils/formatters';
import { calculateAverage, calculatePercentageChange, calculateSpendingVelocity } from '../../utils/calculations';

// Register ChartJS components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  ChartTooltip,
  Legend,
  Filler
);

interface TransactionStatsProps {
  transactions: Transaction[];
  period?: 'week' | 'month' | 'quarter' | 'year' | 'all';
  onPeriodChange?: (period: string) => void;
  onExport?: () => void;
}

interface StatCard {
  title: string;
  value: string;
  change?: number;
  icon: React.ReactNode;
  color: string;
  subtitle?: string;
}

const TransactionStats: React.FC<TransactionStatsProps> = ({
  transactions,
  period = 'month',
  onPeriodChange,
  onExport,
}) => {
  const theme = useTheme();
  const [selectedView, setSelectedView] = useState<'overview' | 'spending' | 'income'>('overview');

  // Calculate stats
  const stats = useMemo(() => {
    const now = new Date();
    const filteredTransactions = transactions.filter(tx => {
      const txDate = new Date(tx.createdAt);
      switch (period) {
        case 'week':
          return txDate >= new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        case 'month':
          return txDate >= startOfMonth(now);
        case 'quarter':
          return txDate >= new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1);
        case 'year':
          return txDate >= new Date(now.getFullYear(), 0, 1);
        default:
          return true;
      }
    });

    const totalSpent = filteredTransactions
      .filter(tx => tx.type === TransactionType.DEBIT || tx.type === TransactionType.WITHDRAWAL)
      .reduce((sum, tx) => sum + tx.amount, 0);

    const totalReceived = filteredTransactions
      .filter(tx => tx.type === TransactionType.CREDIT || tx.type === TransactionType.DEPOSIT)
      .reduce((sum, tx) => sum + tx.amount, 0);

    const totalFees = filteredTransactions
      .reduce((sum, tx) => sum + (tx.fee || 0), 0);

    const netFlow = totalReceived - totalSpent;

    const transactionCount = filteredTransactions.length;
    const averageTransaction = transactionCount > 0 ? (totalSpent + totalReceived) / transactionCount : 0;

    // Calculate category breakdown
    const categoryBreakdown = filteredTransactions
      .filter(tx => tx.type === TransactionType.DEBIT)
      .reduce((acc, tx) => {
        const category = tx.category || 'Other';
        acc[category] = (acc[category] || 0) + tx.amount;
        return acc;
      }, {} as Record<string, number>);

    // Calculate daily spending
    const dailySpending = calculateDailySpending(filteredTransactions);

    // Calculate merchant frequency
    const merchantFrequency = calculateMerchantFrequency(filteredTransactions);

    // Calculate previous period for comparison
    const previousPeriodTransactions = getPreviousPeriodTransactions(transactions, period);
    const previousTotalSpent = previousPeriodTransactions
      .filter(tx => tx.type === TransactionType.DEBIT || tx.type === TransactionType.WITHDRAWAL)
      .reduce((sum, tx) => sum + tx.amount, 0);

    const spendingChange = calculatePercentageChange(previousTotalSpent, totalSpent);

    return {
      totalSpent,
      totalReceived,
      totalFees,
      netFlow,
      transactionCount,
      averageTransaction,
      categoryBreakdown,
      dailySpending,
      merchantFrequency,
      spendingChange,
    };
  }, [transactions, period]);

  const statCards: StatCard[] = [
    {
      title: 'Total Spent',
      value: formatCurrency(stats.totalSpent),
      change: stats.spendingChange,
      icon: <ShoppingIcon />,
      color: theme.palette.error.main,
      subtitle: `${stats.transactionCount} transactions`,
    },
    {
      title: 'Total Received',
      value: formatCurrency(stats.totalReceived),
      icon: <WalletIcon />,
      color: theme.palette.success.main,
    },
    {
      title: 'Net Flow',
      value: formatCurrency(stats.netFlow),
      icon: stats.netFlow >= 0 ? <TrendingUpIcon /> : <TrendingDownIcon />,
      color: stats.netFlow >= 0 ? theme.palette.success.main : theme.palette.error.main,
    },
    {
      title: 'Avg Transaction',
      value: formatCurrency(stats.averageTransaction),
      icon: <ReceiptIcon />,
      color: theme.palette.info.main,
    },
    {
      title: 'Total Fees',
      value: formatCurrency(stats.totalFees),
      icon: <MoneyIcon />,
      color: theme.palette.warning.main,
    },
    {
      title: 'Daily Average',
      value: formatCurrency(stats.totalSpent / getDaysInPeriod(period)),
      icon: <SpeedIcon />,
      color: theme.palette.primary.main,
    },
  ];

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
      },
      tooltip: {
        backgroundColor: theme.palette.background.paper,
        titleColor: theme.palette.text.primary,
        bodyColor: theme.palette.text.secondary,
        borderColor: theme.palette.divider,
        borderWidth: 1,
      },
    },
    scales: {
      x: {
        grid: {
          display: false,
        },
      },
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value: any) => formatCurrency(value, { compact: true }),
        },
      },
    },
  };

  const renderStatCard = (stat: StatCard) => (
    <Grid item xs={12} sm={6} md={4} key={stat.title}>
      <Card
        sx={{
          height: '100%',
          background: `linear-gradient(135deg, ${alpha(stat.color, 0.1)} 0%, ${alpha(stat.color, 0.05)} 100%)`,
          border: `1px solid ${alpha(stat.color, 0.2)}`,
        }}
      >
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
            <Avatar
              sx={{
                bgcolor: alpha(stat.color, 0.2),
                color: stat.color,
                width: 48,
                height: 48,
              }}
            >
              {stat.icon}
            </Avatar>
            {stat.change !== undefined && (
              <Chip
                label={formatPercentage(Math.abs(stat.change), { showSign: true })}
                size="small"
                sx={{
                  bgcolor: stat.change >= 0 ? alpha(theme.palette.error.main, 0.1) : alpha(theme.palette.success.main, 0.1),
                  color: stat.change >= 0 ? theme.palette.error.main : theme.palette.success.main,
                }}
                icon={stat.change >= 0 ? <TrendingUpIcon /> : <TrendingDownIcon />}
              />
            )}
          </Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
            {stat.value}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {stat.title}
          </Typography>
          {stat.subtitle && (
            <Typography variant="caption" color="text.secondary">
              {stat.subtitle}
            </Typography>
          )}
        </CardContent>
      </Card>
    </Grid>
  );

  const renderSpendingChart = () => {
    const labels = Object.keys(stats.dailySpending);
    const data = Object.values(stats.dailySpending);

    const chartData = {
      labels,
      datasets: [
        {
          label: 'Daily Spending',
          data,
          borderColor: theme.palette.primary.main,
          backgroundColor: alpha(theme.palette.primary.main, 0.1),
          fill: true,
          tension: 0.4,
        },
      ],
    };

    return (
      <Paper sx={{ p: 3, height: 400 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
          <Typography variant="h6">Spending Trend</Typography>
          <IconButton size="small" onClick={onExport}>
            <DownloadIcon />
          </IconButton>
        </Box>
        <Box sx={{ height: 320 }}>
          <Line data={chartData} options={chartOptions} />
        </Box>
      </Paper>
    );
  };

  const renderCategoryChart = () => {
    const sortedCategories = Object.entries(stats.categoryBreakdown)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 6);

    const chartData = {
      labels: sortedCategories.map(([cat]) => cat),
      datasets: [
        {
          data: sortedCategories.map(([, amount]) => amount),
          backgroundColor: [
            theme.palette.primary.main,
            theme.palette.secondary.main,
            theme.palette.error.main,
            theme.palette.warning.main,
            theme.palette.info.main,
            theme.palette.success.main,
          ],
        },
      ],
    };

    return (
      <Paper sx={{ p: 3, height: 400 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Spending by Category
        </Typography>
        <Box sx={{ height: 300, position: 'relative' }}>
          <Doughnut
            data={chartData}
            options={{
              ...chartOptions,
              maintainAspectRatio: true,
              plugins: {
                ...chartOptions.plugins,
                legend: {
                  display: true,
                  position: 'right',
                },
              },
            }}
          />
        </Box>
      </Paper>
    );
  };

  const renderMerchantList = () => {
    const topMerchants = Object.entries(stats.merchantFrequency)
      .sort(([, a], [, b]) => b.totalSpent - a.totalSpent)
      .slice(0, 5);

    return (
      <Paper sx={{ p: 3, height: 400 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Top Merchants
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {topMerchants.map(([merchant, data], index) => (
            <Box key={merchant}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Avatar
                    sx={{
                      width: 32,
                      height: 32,
                      bgcolor: alpha(theme.palette.primary.main, 0.1),
                      color: theme.palette.primary.main,
                      fontSize: 14,
                    }}
                  >
                    {index + 1}
                  </Avatar>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {merchant}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {data.frequency} transactions
                    </Typography>
                  </Box>
                </Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {formatCurrency(data.totalSpent)}
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={(data.totalSpent / stats.totalSpent) * 100}
                sx={{ height: 4, borderRadius: 2 }}
              />
            </Box>
          ))}
        </Box>
      </Paper>
    );
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Transaction Analytics
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {period === 'all' ? 'All time' : `Last ${period}`}
          </Typography>
        </Box>
        
        <Box sx={{ display: 'flex', gap: 2 }}>
          <ToggleButtonGroup
            value={period}
            exclusive
            onChange={(_, value) => value && onPeriodChange?.(value)}
            size="small"
          >
            <ToggleButton value="week">Week</ToggleButton>
            <ToggleButton value="month">Month</ToggleButton>
            <ToggleButton value="quarter">Quarter</ToggleButton>
            <ToggleButton value="year">Year</ToggleButton>
            <ToggleButton value="all">All</ToggleButton>
          </ToggleButtonGroup>
        </Box>
      </Box>

      <Grid container spacing={3}>
        {statCards.map(renderStatCard)}

        <Grid item xs={12} lg={8}>
          {renderSpendingChart()}
        </Grid>

        <Grid item xs={12} lg={4}>
          {renderMerchantList()}
        </Grid>

        <Grid item xs={12} md={6}>
          {renderCategoryChart()}
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" sx={{ mb: 2 }}>
              Insights
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Alert severity="info" icon={<InfoIcon />}>
                Your spending is {stats.spendingChange > 0 ? 'up' : 'down'} {formatPercentage(Math.abs(stats.spendingChange))} compared to last {period}.
              </Alert>
              
              <Alert severity="success" icon={<CategoryIcon />}>
                Your top spending category is <strong>{Object.entries(stats.categoryBreakdown)[0]?.[0] || 'N/A'}</strong>, accounting for {formatPercentage((Object.entries(stats.categoryBreakdown)[0]?.[1] || 0) / stats.totalSpent * 100)} of total spending.
              </Alert>
              
              {stats.netFlow < 0 && (
                <Alert severity="warning" icon={<TrendingDownIcon />}>
                  You spent {formatCurrency(Math.abs(stats.netFlow))} more than you received this {period}.
                </Alert>
              )}
              
              <Alert severity="info" icon={<OfferIcon />}>
                You could save approximately {formatCurrency(stats.totalFees * 0.3)} by using fee-free payment methods.
              </Alert>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

// Helper functions
function calculateDailySpending(transactions: Transaction[]): Record<string, number> {
  const dailyTotals: Record<string, number> = {};
  
  transactions
    .filter(tx => tx.type === TransactionType.DEBIT || tx.type === TransactionType.WITHDRAWAL)
    .forEach(tx => {
      const date = format(new Date(tx.createdAt), 'MMM dd');
      dailyTotals[date] = (dailyTotals[date] || 0) + tx.amount;
    });

  return dailyTotals;
}

function calculateMerchantFrequency(transactions: Transaction[]): Record<string, { frequency: number; totalSpent: number }> {
  const merchantData: Record<string, { frequency: number; totalSpent: number }> = {};
  
  transactions
    .filter(tx => tx.merchantName && (tx.type === TransactionType.DEBIT || tx.type === TransactionType.WITHDRAWAL))
    .forEach(tx => {
      const merchant = tx.merchantName!;
      if (!merchantData[merchant]) {
        merchantData[merchant] = { frequency: 0, totalSpent: 0 };
      }
      merchantData[merchant].frequency += 1;
      merchantData[merchant].totalSpent += tx.amount;
    });

  return merchantData;
}

function getPreviousPeriodTransactions(transactions: Transaction[], period: string): Transaction[] {
  const now = new Date();
  let startDate: Date;
  let endDate: Date;

  switch (period) {
    case 'week':
      endDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      startDate = new Date(endDate.getTime() - 7 * 24 * 60 * 60 * 1000);
      break;
    case 'month':
      endDate = startOfMonth(now);
      startDate = new Date(endDate.getFullYear(), endDate.getMonth() - 1, 1);
      break;
    case 'quarter':
      const currentQuarter = Math.floor(now.getMonth() / 3);
      endDate = new Date(now.getFullYear(), currentQuarter * 3, 1);
      startDate = new Date(now.getFullYear(), (currentQuarter - 1) * 3, 1);
      break;
    case 'year':
      endDate = new Date(now.getFullYear(), 0, 1);
      startDate = new Date(now.getFullYear() - 1, 0, 1);
      break;
    default:
      return [];
  }

  return transactions.filter(tx => {
    const txDate = new Date(tx.createdAt);
    return txDate >= startDate && txDate < endDate;
  });
}

function getDaysInPeriod(period: string): number {
  switch (period) {
    case 'week':
      return 7;
    case 'month':
      return 30;
    case 'quarter':
      return 90;
    case 'year':
      return 365;
    default:
      return 1;
  }
}

export default TransactionStats;