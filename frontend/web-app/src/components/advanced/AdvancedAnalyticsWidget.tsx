import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  useTheme,
  useMediaQuery,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  FormControl,
  Select,
  SelectChangeEvent,
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
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DownloadIcon from '@mui/icons-material/Download';
import ShareIcon from '@mui/icons-material/Share';
import FullscreenIcon from '@mui/icons-material/Fullscreen';;
import { analyticsService } from '../../services/analyticsService';

interface AnalyticsData {
  spendingTrends: SpendingTrend[];
  categoryBreakdown: CategoryData[];
  incomeVsExpenses: IncomeExpenseData[];
  savingsRate: number;
  budgetPerformance: BudgetData[];
  merchantAnalysis: MerchantData[];
  timeAnalysis: TimeData[];
  predictions: PredictionData[];
}

interface SpendingTrend {
  month: string;
  spending: number;
  income: number;
  savings: number;
  trend: 'up' | 'down' | 'stable';
}

interface CategoryData {
  category: string;
  amount: number;
  percentage: number;
  color: string;
  trend: number;
}

interface IncomeExpenseData {
  period: string;
  income: number;
  expenses: number;
  netSavings: number;
}

interface BudgetData {
  category: string;
  budgeted: number;
  actual: number;
  variance: number;
  status: 'under' | 'over' | 'on-track';
}

interface MerchantData {
  merchant: string;
  totalSpent: number;
  transactions: number;
  avgTransaction: number;
  category: string;
}

interface TimeData {
  hour: number;
  dayOfWeek: string;
  transactionCount: number;
  avgAmount: number;
}

interface PredictionData {
  metric: string;
  predicted: number;
  confidence: number;
  timeframe: string;
}

export const AdvancedAnalyticsWidget: React.FC = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const isTablet = useMediaQuery(theme.breakpoints.down('md'));
  
  const [analyticsData, setAnalyticsData] = useState<AnalyticsData | null>(null);
  const [selectedTimeframe, setSelectedTimeframe] = useState('3M');
  const [selectedView, setSelectedView] = useState('overview');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [isLoading, setIsLoading] = useState(true);

  const timeframes = [
    { value: '1M', label: '1 Month' },
    { value: '3M', label: '3 Months' },
    { value: '6M', label: '6 Months' },
    { value: '1Y', label: '1 Year' },
    { value: 'ALL', label: 'All Time' },
  ];

  const views = [
    { value: 'overview', label: 'Overview' },
    { value: 'spending', label: 'Spending Analysis' },
    { value: 'budget', label: 'Budget Performance' },
    { value: 'merchants', label: 'Merchant Analysis' },
    { value: 'predictions', label: 'Predictions' },
  ];

  useEffect(() => {
    loadAnalyticsData();
  }, [selectedTimeframe]);

  const loadAnalyticsData = async () => {
    try {
      setIsLoading(true);
      const response = await analyticsService.getAdvancedAnalytics({
        timeframe: selectedTimeframe,
        includePredict: true,
        includeMerchantAnalysis: true,
        includeTimeAnalysis: true,
      });

      if (response.success) {
        setAnalyticsData(response.data);
      }
    } catch (error) {
      console.error('Failed to load analytics:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleTimeframeChange = (event: SelectChangeEvent) => {
    setSelectedTimeframe(event.target.value);
  };

  const handleViewChange = (event: SelectChangeEvent) => {
    setSelectedView(event.target.value);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const formatPercent = (percent: number): string => {
    return `${percent >= 0 ? '+' : ''}${percent.toFixed(1)}%`;
  };

  const renderOverview = () => (
    <Grid container spacing={isMobile ? 2 : 3}>
      {/* Key Metrics */}
      <Grid item xs={12}>
        <Grid container spacing={2}>
          <Grid item xs={6} sm={3}>
            <Card sx={{ height: '100%', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
              <CardContent sx={{ color: 'white', textAlign: 'center' }}>
                <Typography variant="h6" component="div">
                  Savings Rate
                </Typography>
                <Typography variant="h4" component="div">
                  {analyticsData?.savingsRate.toFixed(1)}%
                </Typography>
                <Chip
                  icon={<TrendingUp />}
                  label="+2.3%"
                  size="small"
                  sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white', mt: 1 }}
                />
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={6} sm={3}>
            <Card sx={{ height: '100%', background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' }}>
              <CardContent sx={{ color: 'white', textAlign: 'center' }}>
                <Typography variant="h6" component="div">
                  Monthly Spending
                </Typography>
                <Typography variant="h4" component="div">
                  {formatCurrency(3420)}
                </Typography>
                <Chip
                  icon={<TrendingDown />}
                  label="-5.2%"
                  size="small"
                  sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white', mt: 1 }}
                />
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={6} sm={3}>
            <Card sx={{ height: '100%', background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' }}>
              <CardContent sx={{ color: 'white', textAlign: 'center' }}>
                <Typography variant="h6" component="div">
                  Budget Adherence
                </Typography>
                <Typography variant="h4" component="div">
                  87%
                </Typography>
                <Chip
                  icon={<TrendingUp />}
                  label="+12%"
                  size="small"
                  sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white', mt: 1 }}
                />
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={6} sm={3}>
            <Card sx={{ height: '100%', background: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)' }}>
              <CardContent sx={{ color: 'white', textAlign: 'center' }}>
                <Typography variant="h6" component="div">
                  Investment Growth
                </Typography>
                <Typography variant="h4" component="div">
                  {formatCurrency(8750)}
                </Typography>
                <Chip
                  icon={<TrendingUp />}
                  label="+18.3%"
                  size="small"
                  sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white', mt: 1 }}
                />
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Grid>

      {/* Spending Trends Chart */}
      <Grid item xs={12} md={8}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Income vs Expenses Trend
            </Typography>
            <Box sx={{ height: isMobile ? 250 : 350 }}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={analyticsData?.spendingTrends || []}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="month" />
                  <YAxis tickFormatter={(value) => formatCurrency(value)} />
                  <Tooltip formatter={(value) => formatCurrency(value as number)} />
                  <Legend />
                  <Area
                    type="monotone"
                    dataKey="income"
                    stackId="1"
                    stroke="#8884d8"
                    fill="#8884d8"
                    fillOpacity={0.6}
                  />
                  <Area
                    type="monotone"
                    dataKey="spending"
                    stackId="2"
                    stroke="#82ca9d"
                    fill="#82ca9d"
                    fillOpacity={0.6}
                  />
                  <Area
                    type="monotone"
                    dataKey="savings"
                    stackId="3"
                    stroke="#ffc658"
                    fill="#ffc658"
                    fillOpacity={0.6}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      {/* Category Breakdown */}
      <Grid item xs={12} md={4}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Spending by Category
            </Typography>
            <Box sx={{ height: isMobile ? 250 : 350 }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={analyticsData?.categoryBreakdown || []}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ category, percentage }) => 
                      isMobile ? '' : `${category}: ${percentage}%`
                    }
                    outerRadius={isMobile ? 80 : 100}
                    fill="#8884d8"
                    dataKey="amount"
                  >
                    {analyticsData?.categoryBreakdown.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value) => formatCurrency(value as number)} />
                  {!isMobile && <Legend />}
                </PieChart>
              </ResponsiveContainer>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      {/* Top Categories with Trends */}
      <Grid item xs={12}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Category Performance
            </Typography>
            <Grid container spacing={2}>
              {analyticsData?.categoryBreakdown.slice(0, 4).map((category, index) => (
                <Grid item xs={6} md={3} key={index}>
                  <Box sx={{ p: 2, backgroundColor: '#f8f9fa', borderRadius: 2 }}>
                    <Typography variant="subtitle1" fontWeight="bold">
                      {category.category}
                    </Typography>
                    <Typography variant="h6" color="primary">
                      {formatCurrency(category.amount)}
                    </Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                      {category.trend >= 0 ? (
                        <TrendingUp color="success" fontSize="small" />
                      ) : (
                        <TrendingDown color="error" fontSize="small" />
                      )}
                      <Typography
                        variant="body2"
                        color={category.trend >= 0 ? 'success.main' : 'error.main'}
                        sx={{ ml: 0.5 }}
                      >
                        {formatPercent(category.trend)}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>
              ))}
            </Grid>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderBudgetPerformance = () => (
    <Grid container spacing={3}>
      <Grid item xs={12}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Budget vs Actual Spending
            </Typography>
            <Box sx={{ height: isMobile ? 300 : 400 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={analyticsData?.budgetPerformance || []}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="category" />
                  <YAxis tickFormatter={(value) => formatCurrency(value)} />
                  <Tooltip formatter={(value) => formatCurrency(value as number)} />
                  <Legend />
                  <Bar dataKey="budgeted" fill="#8884d8" name="Budgeted" />
                  <Bar dataKey="actual" fill="#82ca9d" name="Actual" />
                </BarChart>
              </ResponsiveContainer>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      {/* Budget Status Cards */}
      <Grid item xs={12}>
        <Grid container spacing={2}>
          {analyticsData?.budgetPerformance.map((budget, index) => (
            <Grid item xs={12} sm={6} md={4} key={index}>
              <Card
                sx={{
                  borderLeft: `4px solid ${
                    budget.status === 'under' ? '#4caf50' :
                    budget.status === 'over' ? '#f44336' : '#ff9800'
                  }`
                }}
              >
                <CardContent>
                  <Typography variant="subtitle1" fontWeight="bold">
                    {budget.category}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Budget: {formatCurrency(budget.budgeted)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Actual: {formatCurrency(budget.actual)}
                  </Typography>
                  <Typography
                    variant="body2"
                    color={
                      budget.status === 'under' ? 'success.main' :
                      budget.status === 'over' ? 'error.main' : 'warning.main'
                    }
                    sx={{ mt: 1, fontWeight: 'bold' }}
                  >
                    {budget.variance >= 0 ? 'Under' : 'Over'} by {formatCurrency(Math.abs(budget.variance))}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Grid>
    </Grid>
  );

  const renderPredictions = () => (
    <Grid container spacing={3}>
      <Grid item xs={12}>
        <Typography variant="h6" gutterBottom>
          AI-Powered Financial Predictions
        </Typography>
      </Grid>

      {analyticsData?.predictions.map((prediction, index) => (
        <Grid item xs={12} sm={6} md={4} key={index}>
          <Card>
            <CardContent>
              <Typography variant="h6" color="primary" gutterBottom>
                {prediction.metric}
              </Typography>
              <Typography variant="h4" component="div">
                {typeof prediction.predicted === 'number' ? 
                  formatCurrency(prediction.predicted) : 
                  prediction.predicted
                }
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {prediction.timeframe}
              </Typography>
              <Box sx={{ mt: 2 }}>
                <Typography variant="body2" component="div">
                  Confidence: {prediction.confidence}%
                </Typography>
                <Box
                  sx={{
                    width: '100%',
                    height: 8,
                    backgroundColor: '#e0e0e0',
                    borderRadius: 4,
                    overflow: 'hidden',
                    mt: 1,
                  }}
                >
                  <Box
                    sx={{
                      width: `${prediction.confidence}%`,
                      height: '100%',
                      backgroundColor: prediction.confidence >= 80 ? '#4caf50' : 
                                      prediction.confidence >= 60 ? '#ff9800' : '#f44336',
                      transition: 'width 0.3s ease',
                    }}
                  />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      ))}

      {/* Predictive Insights */}
      <Grid item xs={12}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Predictive Insights
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <Box sx={{ p: 2, backgroundColor: '#e3f2fd', borderRadius: 2 }}>
                  <Typography variant="subtitle1" fontWeight="bold" color="primary">
                    💡 Spending Pattern Alert
                  </Typography>
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    Based on your transaction history, you typically spend 23% more in the 
                    last week of the month. Consider adjusting your budget allocation.
                  </Typography>
                </Box>
              </Grid>
              <Grid item xs={12} md={6}>
                <Box sx={{ p: 2, backgroundColor: '#e8f5e8', borderRadius: 2 }}>
                  <Typography variant="subtitle1" fontWeight="bold" color="success.main">
                    🎯 Savings Opportunity
                  </Typography>
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    You could save an additional $340/month by reducing dining out by 2 times 
                    per week and switching to a high-yield savings account.
                  </Typography>
                </Box>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  if (isLoading) {
    return (
      <Card>
        <CardContent>
          <Typography>Loading advanced analytics...</Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Box>
      {/* Header Controls */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          mb: 3,
          flexDirection: isMobile ? 'column' : 'row',
          gap: isMobile ? 2 : 0,
        }}
      >
        <Typography variant="h4" component="h1">
          Advanced Analytics
        </Typography>
        
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <Select value={selectedTimeframe} onChange={handleTimeframeChange}>
              {timeframes.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ minWidth: 120 }}>
            <Select value={selectedView} onChange={handleViewChange}>
              {views.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <IconButton onClick={handleMenuOpen}>
            <MoreVert />
          </IconButton>
        </Box>
      </Box>

      {/* Menu */}
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleMenuClose}>
        <MenuItem onClick={handleMenuClose}>
          <Download fontSize="small" sx={{ mr: 1 }} />
          Export Data
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <Share fontSize="small" sx={{ mr: 1 }} />
          Share Report
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <Fullscreen fontSize="small" sx={{ mr: 1 }} />
          Full Screen
        </MenuItem>
      </Menu>

      {/* Content */}
      {selectedView === 'overview' && renderOverview()}
      {selectedView === 'budget' && renderBudgetPerformance()}
      {selectedView === 'predictions' && renderPredictions()}
    </Box>
  );
};