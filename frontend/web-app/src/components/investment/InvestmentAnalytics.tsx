import React, { useState, useMemo } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  Button,
  IconButton,
  Chip,
  Tabs,
  Tab,
  Stack,
  LinearProgress,
  Alert,
  Divider,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Avatar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tooltip,
  Badge,
  useTheme,
  alpha,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import PieChartIcon from '@mui/icons-material/PieChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import TimelineIcon from '@mui/icons-material/Timeline';
import AssessmentIcon from '@mui/icons-material/Assessment';
import SpeedIcon from '@mui/icons-material/Speed';
import SecurityIcon from '@mui/icons-material/Security';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import StarIcon from '@mui/icons-material/Star';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import AcUnitIcon from '@mui/icons-material/AcUnit';
import BoltIcon from '@mui/icons-material/Bolt';
import DiamondIcon from '@mui/icons-material/Diamond';
import SavingsIcon from '@mui/icons-material/Savings';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import CurrencyExchangeIcon from '@mui/icons-material/CurrencyExchange';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import DownloadIcon from '@mui/icons-material/Download';
import ShareIcon from '@mui/icons-material/Share';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import TrendingFlatIcon from '@mui/icons-material/TrendingFlat';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import ScheduleIcon from '@mui/icons-material/Schedule';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';;
import { format, subMonths, startOfMonth, endOfMonth } from 'date-fns';

interface PortfolioMetrics {
  totalValue: number;
  totalCost: number;
  totalReturn: number;
  totalReturnPercent: number;
  monthlyReturn: number;
  yearlyReturn: number;
  sharpeRatio: number;
  volatility: number;
  beta: number;
  alpha: number;
  maxDrawdown: number;
  winRate: number;
}

interface AssetAllocation {
  category: string;
  value: number;
  percentage: number;
  target: number;
  difference: number;
  icon: React.ReactNode;
  color: string;
}

interface PerformanceHistory {
  date: string;
  portfolioValue: number;
  dailyReturn: number;
  benchmark: number;
}

interface RiskMetrics {
  portfolioRisk: 'Low' | 'Moderate' | 'High';
  diversificationScore: number;
  concentrationRisk: string[];
  volatilityTrend: 'Increasing' | 'Stable' | 'Decreasing';
  correlationMatrix: { [key: string]: number };
}

interface InvestmentGoal {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  monthlyContribution: number;
  onTrack: boolean;
}

const mockMetrics: PortfolioMetrics = {
  totalValue: 85420.50,
  totalCost: 72000.00,
  totalReturn: 13420.50,
  totalReturnPercent: 18.64,
  monthlyReturn: 2.8,
  yearlyReturn: 22.5,
  sharpeRatio: 1.85,
  volatility: 15.2,
  beta: 1.12,
  alpha: 3.2,
  maxDrawdown: -12.5,
  winRate: 68.5,
};

const mockAllocation: AssetAllocation[] = [
  {
    category: 'Stocks',
    value: 47893.25,
    percentage: 56.1,
    target: 60,
    difference: -3.9,
    icon: <ShowChart />,
    color: '#4CAF50',
  },
  {
    category: 'Crypto',
    value: 21355.13,
    percentage: 25.0,
    target: 20,
    difference: 5.0,
    icon: <Diamond />,
    color: '#FF9800',
  },
  {
    category: 'Bonds',
    value: 8542.05,
    percentage: 10.0,
    target: 15,
    difference: -5.0,
    icon: <AccountBalance />,
    color: '#2196F3',
  },
  {
    category: 'Cash',
    value: 7630.07,
    percentage: 8.9,
    target: 5,
    difference: 3.9,
    icon: <AttachMoney />,
    color: '#9C27B0',
  },
];

const mockPerformanceHistory: PerformanceHistory[] = Array.from({ length: 30 }, (_, i) => ({
  date: format(subMonths(new Date(), 29 - i), 'MMM yyyy'),
  portfolioValue: 72000 + (13420.50 / 30) * i + Math.random() * 2000 - 1000,
  dailyReturn: Math.random() * 4 - 2,
  benchmark: 72000 + (11000 / 30) * i + Math.random() * 1500 - 750,
}));

const mockRiskMetrics: RiskMetrics = {
  portfolioRisk: 'Moderate',
  diversificationScore: 7.5,
  concentrationRisk: ['AAPL (15%)', 'BTC (18%)'],
  volatilityTrend: 'Stable',
  correlationMatrix: {
    'Stocks-Crypto': 0.45,
    'Stocks-Bonds': -0.25,
    'Crypto-Bonds': -0.15,
  },
};

const mockGoals: InvestmentGoal[] = [
  {
    id: '1',
    name: 'Retirement Fund',
    targetAmount: 1000000,
    currentAmount: 85420.50,
    targetDate: '2050-01-01',
    monthlyContribution: 2000,
    onTrack: true,
  },
  {
    id: '2',
    name: 'House Down Payment',
    targetAmount: 100000,
    currentAmount: 45000,
    targetDate: '2026-06-01',
    monthlyContribution: 1500,
    onTrack: false,
  },
  {
    id: '3',
    name: 'Emergency Fund',
    targetAmount: 30000,
    currentAmount: 28500,
    targetDate: '2024-12-31',
    monthlyContribution: 500,
    onTrack: true,
  },
];

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <Box hidden={value !== index} pt={3}>
    {value === index && children}
  </Box>
);

const InvestmentAnalytics: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [timeRange, setTimeRange] = useState('1Y');
  const [comparisonMode, setComparisonMode] = useState('benchmark');

  const getRiskColor = (risk: string) => {
    switch (risk) {
      case 'Low':
        return theme.palette.success.main;
      case 'Moderate':
        return theme.palette.warning.main;
      case 'High':
        return theme.palette.error.main;
      default:
        return theme.palette.grey[500];
    }
  };

  const getPerformanceIcon = (value: number) => {
    if (value > 20) return <LocalFireDepartment color="error" />;
    if (value > 10) return <Bolt color="warning" />;
    if (value > 0) return <TrendingUp color="success" />;
    if (value > -10) return <TrendingDown color="error" />;
    return <AcUnit color="info" />;
  };

  const renderMetricCard = (
    title: string,
    value: string | number,
    subtitle?: string,
    icon?: React.ReactNode,
    color?: string,
    trend?: number
  ) => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {title}
            </Typography>
            <Typography variant="h5" fontWeight="bold">
              {value}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="text.secondary" mt={0.5}>
                {subtitle}
              </Typography>
            )}
            {trend !== undefined && (
              <Box display="flex" alignItems="center" mt={1}>
                {trend > 0 ? (
                  <ArrowUpward color="success" fontSize="small" />
                ) : trend < 0 ? (
                  <ArrowDownward color="error" fontSize="small" />
                ) : (
                  <TrendingFlat color="action" fontSize="small" />
                )}
                <Typography
                  variant="body2"
                  color={trend > 0 ? 'success.main' : trend < 0 ? 'error.main' : 'text.secondary'}
                  ml={0.5}
                >
                  {Math.abs(trend).toFixed(1)}%
                </Typography>
              </Box>
            )}
          </Box>
          {icon && (
            <Box
              sx={{
                p: 1,
                borderRadius: 2,
                backgroundColor: color ? alpha(color, 0.1) : alpha(theme.palette.primary.main, 0.1),
                color: color || theme.palette.primary.main,
              }}
            >
              {icon}
            </Box>
          )}
        </Box>
      </CardContent>
    </Card>
  );

  const renderAllocationChart = () => {
    const maxPercentage = Math.max(...mockAllocation.map(a => a.percentage));

    return (
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Asset Allocation
          </Typography>
          <Stack spacing={2}>
            {mockAllocation.map((asset) => (
              <Box key={asset.category}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                  <Box display="flex" alignItems="center" gap={1}>
                    {asset.icon}
                    <Typography variant="body2">{asset.category}</Typography>
                  </Box>
                  <Box textAlign="right">
                    <Typography variant="body2" fontWeight="bold">
                      ${(asset.value / 1000).toFixed(1)}k ({asset.percentage}%)
                    </Typography>
                    <Typography
                      variant="caption"
                      color={asset.difference > 0 ? 'warning.main' : 'success.main'}
                    >
                      Target: {asset.target}% ({asset.difference > 0 ? '+' : ''}{asset.difference}%)
                    </Typography>
                  </Box>
                </Box>
                <Box position="relative">
                  <LinearProgress
                    variant="determinate"
                    value={(asset.percentage / maxPercentage) * 100}
                    sx={{
                      height: 10,
                      borderRadius: 5,
                      backgroundColor: alpha(asset.color, 0.2),
                      '& .MuiLinearProgress-bar': {
                        borderRadius: 5,
                        backgroundColor: asset.color,
                      },
                    }}
                  />
                  <Box
                    sx={{
                      position: 'absolute',
                      left: `${asset.target}%`,
                      top: -2,
                      bottom: -2,
                      width: 2,
                      backgroundColor: theme.palette.text.primary,
                    }}
                  />
                </Box>
              </Box>
            ))}
          </Stack>

          <Divider sx={{ my: 3 }} />

          <Alert severity="info" icon={<Info />}>
            <Typography variant="body2">
              Your portfolio is slightly overweight in crypto. Consider rebalancing to match target allocation.
            </Typography>
          </Alert>
        </CardContent>
      </Card>
    );
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Investment Analytics
        </Typography>
        <Stack direction="row" spacing={2}>
          <FormControl size="small" sx={{ minWidth: 100 }}>
            <Select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
            >
              <MenuItem value="1M">1 Month</MenuItem>
              <MenuItem value="3M">3 Months</MenuItem>
              <MenuItem value="6M">6 Months</MenuItem>
              <MenuItem value="1Y">1 Year</MenuItem>
              <MenuItem value="ALL">All Time</MenuItem>
            </Select>
          </FormControl>
          <Button variant="outlined" startIcon={<Download />}>
            Export Report
          </Button>
        </Stack>
      </Box>

      {/* Key Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          {renderMetricCard(
            'Total Portfolio Value',
            `$${mockMetrics.totalValue.toLocaleString()}`,
            'All assets combined',
            <AccountBalance />,
            theme.palette.primary.main,
            mockMetrics.monthlyReturn
          )}
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          {renderMetricCard(
            'Total Return',
            `$${mockMetrics.totalReturn.toLocaleString()}`,
            `${mockMetrics.totalReturnPercent.toFixed(2)}% gain`,
            getPerformanceIcon(mockMetrics.totalReturnPercent),
            theme.palette.success.main
          )}
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          {renderMetricCard(
            'Yearly Return',
            `${mockMetrics.yearlyReturn}%`,
            'Annualized',
            <Timeline />,
            theme.palette.info.main
          )}
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          {renderMetricCard(
            'Win Rate',
            `${mockMetrics.winRate}%`,
            'Profitable trades',
            <EmojiEvents />,
            theme.palette.warning.main
          )}
        </Grid>
      </Grid>

      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} lg={8}>
          <Paper>
            <Tabs
              value={activeTab}
              onChange={(_, value) => setActiveTab(value)}
              sx={{ borderBottom: 1, borderColor: 'divider' }}
            >
              <Tab label="Performance" icon={<ShowChart />} iconPosition="start" />
              <Tab label="Risk Analysis" icon={<Security />} iconPosition="start" />
              <Tab label="Goals" icon={<Savings />} iconPosition="start" />
              <Tab label="Insights" icon={<Analytics />} iconPosition="start" />
            </Tabs>

            <TabPanel value={activeTab} index={0}>
              <Box p={3}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
                  <Typography variant="h6">Portfolio Performance</Typography>
                  <FormControl size="small" sx={{ minWidth: 120 }}>
                    <Select
                      value={comparisonMode}
                      onChange={(e) => setComparisonMode(e.target.value)}
                    >
                      <MenuItem value="benchmark">vs S&P 500</MenuItem>
                      <MenuItem value="inflation">vs Inflation</MenuItem>
                      <MenuItem value="savings">vs Savings</MenuItem>
                    </Select>
                  </FormControl>
                </Box>

                {/* Performance Chart (Simplified) */}
                <Box sx={{ height: 300, position: 'relative', mb: 4 }}>
                  <Box
                    sx={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      bottom: 0,
                      display: 'flex',
                      alignItems: 'flex-end',
                      gap: 0.5,
                    }}
                  >
                    {mockPerformanceHistory.slice(-12).map((data, index) => (
                      <Tooltip
                        key={index}
                        title={
                          <Box>
                            <Typography variant="caption">{data.date}</Typography>
                            <Typography variant="caption" display="block">
                              Portfolio: ${data.portfolioValue.toLocaleString()}
                            </Typography>
                            <Typography variant="caption" display="block">
                              Return: {data.dailyReturn > 0 ? '+' : ''}{data.dailyReturn.toFixed(2)}%
                            </Typography>
                          </Box>
                        }
                      >
                        <Box
                          sx={{
                            flex: 1,
                            height: `${(data.portfolioValue / mockMetrics.totalValue) * 100}%`,
                            backgroundColor: data.dailyReturn > 0 ? theme.palette.success.main : theme.palette.error.main,
                            borderRadius: '4px 4px 0 0',
                            cursor: 'pointer',
                            transition: 'opacity 0.2s',
                            '&:hover': { opacity: 0.8 },
                          }}
                        />
                      </Tooltip>
                    ))}
                  </Box>
                </Box>

                <Grid container spacing={3}>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="body2" color="text.secondary">
                        Sharpe Ratio
                      </Typography>
                      <Typography variant="h6">
                        {mockMetrics.sharpeRatio}
                      </Typography>
                      <Chip
                        label="Excellent"
                        color="success"
                        size="small"
                      />
                    </Box>
                  </Grid>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="body2" color="text.secondary">
                        Volatility
                      </Typography>
                      <Typography variant="h6">
                        {mockMetrics.volatility}%
                      </Typography>
                      <Chip
                        label="Moderate"
                        color="warning"
                        size="small"
                      />
                    </Box>
                  </Grid>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="body2" color="text.secondary">
                        Beta
                      </Typography>
                      <Typography variant="h6">
                        {mockMetrics.beta}
                      </Typography>
                      <Chip
                        label="Market+"
                        color="info"
                        size="small"
                      />
                    </Box>
                  </Grid>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="body2" color="text.secondary">
                        Max Drawdown
                      </Typography>
                      <Typography variant="h6" color="error.main">
                        {mockMetrics.maxDrawdown}%
                      </Typography>
                      <Chip
                        label="Acceptable"
                        color="default"
                        size="small"
                      />
                    </Box>
                  </Grid>
                </Grid>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={1}>
              <Box p={3}>
                <Typography variant="h6" gutterBottom>
                  Risk Analysis
                </Typography>

                <Grid container spacing={3}>
                  <Grid item xs={12} md={6}>
                    <Card variant="outlined">
                      <CardContent>
                        <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                          <Typography variant="subtitle1">Portfolio Risk Level</Typography>
                          <Chip
                            label={mockRiskMetrics.portfolioRisk}
                            color="warning"
                            icon={<Security />}
                          />
                        </Box>
                        <LinearProgress
                          variant="determinate"
                          value={60}
                          sx={{
                            height: 10,
                            borderRadius: 5,
                            backgroundColor: alpha(theme.palette.warning.main, 0.2),
                            '& .MuiLinearProgress-bar': {
                              borderRadius: 5,
                              backgroundColor: theme.palette.warning.main,
                            },
                          }}
                        />
                        <Box display="flex" justifyContent="space-between" mt={1}>
                          <Typography variant="caption">Low</Typography>
                          <Typography variant="caption">High</Typography>
                        </Box>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6}>
                    <Card variant="outlined">
                      <CardContent>
                        <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                          <Typography variant="subtitle1">Diversification Score</Typography>
                          <Typography variant="h6" color="success.main">
                            {mockRiskMetrics.diversificationScore}/10
                          </Typography>
                        </Box>
                        <LinearProgress
                          variant="determinate"
                          value={mockRiskMetrics.diversificationScore * 10}
                          sx={{
                            height: 10,
                            borderRadius: 5,
                            backgroundColor: alpha(theme.palette.success.main, 0.2),
                            '& .MuiLinearProgress-bar': {
                              borderRadius: 5,
                              backgroundColor: theme.palette.success.main,
                            },
                          }}
                        />
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12}>
                    <Alert severity="warning" icon={<Warning />}>
                      <Typography variant="body2" fontWeight="bold" gutterBottom>
                        Concentration Risk Detected
                      </Typography>
                      <Typography variant="body2">
                        The following positions represent a large portion of your portfolio:
                      </Typography>
                      <Stack direction="row" spacing={1} mt={1}>
                        {mockRiskMetrics.concentrationRisk.map((risk) => (
                          <Chip key={risk} label={risk} size="small" />
                        ))}
                      </Stack>
                    </Alert>
                  </Grid>

                  <Grid item xs={12}>
                    <Typography variant="subtitle1" gutterBottom>
                      Asset Correlation Matrix
                    </Typography>
                    <TableContainer>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Asset Pair</TableCell>
                            <TableCell align="right">Correlation</TableCell>
                            <TableCell align="right">Risk Impact</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {Object.entries(mockRiskMetrics.correlationMatrix).map(([pair, correlation]) => (
                            <TableRow key={pair}>
                              <TableCell>{pair}</TableCell>
                              <TableCell align="right">
                                <Typography
                                  variant="body2"
                                  color={correlation > 0.5 ? 'error.main' : correlation < -0.3 ? 'success.main' : 'text.primary'}
                                >
                                  {correlation.toFixed(2)}
                                </Typography>
                              </TableCell>
                              <TableCell align="right">
                                <Chip
                                  label={
                                    correlation > 0.5 ? 'High' :
                                    correlation < -0.3 ? 'Negative' :
                                    'Moderate'
                                  }
                                  size="small"
                                  color={
                                    correlation > 0.5 ? 'error' :
                                    correlation < -0.3 ? 'success' :
                                    'default'
                                  }
                                />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </Grid>
                </Grid>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={2}>
              <Box p={3}>
                <Typography variant="h6" gutterBottom>
                  Investment Goals
                </Typography>
                <Stack spacing={3}>
                  {mockGoals.map((goal) => {
                    const progress = (goal.currentAmount / goal.targetAmount) * 100;
                    const monthsRemaining = Math.floor(
                      (new Date(goal.targetDate).getTime() - new Date().getTime()) / (1000 * 60 * 60 * 24 * 30)
                    );

                    return (
                      <Card key={goal.id} variant="outlined">
                        <CardContent>
                          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                            <Box>
                              <Typography variant="subtitle1" fontWeight="bold">
                                {goal.name}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                Target: ${goal.targetAmount.toLocaleString()} by {format(new Date(goal.targetDate), 'MMM yyyy')}
                              </Typography>
                            </Box>
                            <Chip
                              label={goal.onTrack ? 'On Track' : 'Behind'}
                              color={goal.onTrack ? 'success' : 'warning'}
                              size="small"
                            />
                          </Box>

                          <Box mb={2}>
                            <Box display="flex" justifyContent="space-between" mb={1}>
                              <Typography variant="body2">
                                ${goal.currentAmount.toLocaleString()} of ${goal.targetAmount.toLocaleString()}
                              </Typography>
                              <Typography variant="body2" fontWeight="bold">
                                {progress.toFixed(1)}%
                              </Typography>
                            </Box>
                            <LinearProgress
                              variant="determinate"
                              value={progress}
                              sx={{
                                height: 8,
                                borderRadius: 4,
                                backgroundColor: alpha(theme.palette.primary.main, 0.2),
                                '& .MuiLinearProgress-bar': {
                                  borderRadius: 4,
                                  backgroundColor: goal.onTrack ? theme.palette.success.main : theme.palette.warning.main,
                                },
                              }}
                            />
                          </Box>

                          <Grid container spacing={2}>
                            <Grid item xs={4}>
                              <Typography variant="caption" color="text.secondary">
                                Monthly Contribution
                              </Typography>
                              <Typography variant="body2" fontWeight="bold">
                                ${goal.monthlyContribution}
                              </Typography>
                            </Grid>
                            <Grid item xs={4}>
                              <Typography variant="caption" color="text.secondary">
                                Months Remaining
                              </Typography>
                              <Typography variant="body2" fontWeight="bold">
                                {monthsRemaining}
                              </Typography>
                            </Grid>
                            <Grid item xs={4}>
                              <Typography variant="caption" color="text.secondary">
                                Required Return
                              </Typography>
                              <Typography variant="body2" fontWeight="bold">
                                {((goal.targetAmount - goal.currentAmount) / goal.currentAmount * 100 / (monthsRemaining / 12)).toFixed(1)}%/yr
                              </Typography>
                            </Grid>
                          </Grid>
                        </CardContent>
                      </Card>
                    );
                  })}
                </Stack>

                <Box mt={3}>
                  <Button variant="contained" fullWidth startIcon={<Add />}>
                    Add New Goal
                  </Button>
                </Box>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={3}>
              <Box p={3}>
                <Typography variant="h6" gutterBottom>
                  Investment Insights
                </Typography>
                <Stack spacing={3}>
                  <Alert severity="success" icon={<CheckCircle />}>
                    <Typography variant="body2" fontWeight="bold">
                      Strong Performance
                    </Typography>
                    <Typography variant="body2">
                      Your portfolio has outperformed the S&P 500 by 4.2% this year. Keep up the good work!
                    </Typography>
                  </Alert>

                  <Alert severity="info" icon={<Info />}>
                    <Typography variant="body2" fontWeight="bold">
                      Rebalancing Opportunity
                    </Typography>
                    <Typography variant="body2">
                      Your crypto allocation has grown to 25% (target: 20%). Consider taking some profits to rebalance.
                    </Typography>
                  </Alert>

                  <Alert severity="warning" icon={<Warning />}>
                    <Typography variant="body2" fontWeight="bold">
                      Tax Consideration
                    </Typography>
                    <Typography variant="body2">
                      You have $3,500 in unrealized short-term gains. Consider holding for long-term capital gains treatment.
                    </Typography>
                  </Alert>

                  <Card variant="outlined">
                    <CardContent>
                      <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
                        Performance Attribution
                      </Typography>
                      <List dense>
                        <ListItem>
                          <ListItemAvatar>
                            <Avatar sx={{ bgcolor: theme.palette.success.main, width: 32, height: 32 }}>
                              <TrendingUp fontSize="small" />
                            </Avatar>
                          </ListItemAvatar>
                          <ListItemText
                            primary="Tech Stocks"
                            secondary="+$5,420 (40% of gains)"
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemAvatar>
                            <Avatar sx={{ bgcolor: theme.palette.warning.main, width: 32, height: 32 }}>
                              <Diamond fontSize="small" />
                            </Avatar>
                          </ListItemAvatar>
                          <ListItemText
                            primary="Crypto Assets"
                            secondary="+$4,850 (36% of gains)"
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemAvatar>
                            <Avatar sx={{ bgcolor: theme.palette.info.main, width: 32, height: 32 }}>
                              <AccountBalance fontSize="small" />
                            </Avatar>
                          </ListItemAvatar>
                          <ListItemText
                            primary="Bonds & Fixed Income"
                            secondary="+$850 (6% of gains)"
                          />
                        </ListItem>
                      </List>
                    </CardContent>
                  </Card>
                </Stack>
              </Box>
            </TabPanel>
          </Paper>
        </Grid>

        <Grid item xs={12} lg={4}>
          {renderAllocationChart()}
        </Grid>
      </Grid>
    </Box>
  );
};

export default InvestmentAnalytics;