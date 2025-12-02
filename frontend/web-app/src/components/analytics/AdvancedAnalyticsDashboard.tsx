import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Tab,
  Tabs,
  IconButton,
  Button,
  Chip,
  LinearProgress,
  Alert,
  Tooltip,
  useTheme,
  alpha,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import DownloadIcon from '@mui/icons-material/Download';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import WarningIcon from '@mui/icons-material/Warning';
import SecurityIcon from '@mui/icons-material/Security';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import PieChartIcon from '@mui/icons-material/PieChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import TimelineIcon from '@mui/icons-material/Timeline';;
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
  Tooltip as RechartsTooltip,
  Legend,
  ResponsiveContainer,
  ComposedChart,
  Scatter,
  ScatterChart,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
} from 'recharts';
import { format, parseISO, subDays, startOfMonth, endOfMonth } from 'date-fns';

interface AnalyticsData {
  transactionSummary: {
    totalTransactions: number;
    totalSpent: number;
    totalReceived: number;
    netCashFlow: number;
    averageTransactionAmount: number;
    largestTransaction: number;
    smallestTransaction: number;
    uniqueMerchants: number;
    uniqueCategories: number;
  };
  spendingAnalysis: {
    totalSpent: number;
    categoryBreakdown: Array<{
      category: string;
      amount: number;
      transactionCount: number;
      percentage: number;
    }>;
    dailySpending: Array<{
      date: string;
      amount: number;
    }>;
    hourlySpending: Array<{
      hour: number;
      amount: number;
    }>;
    spendingTrend: 'INCREASING' | 'DECREASING' | 'STABLE';
    spendingVelocity: number;
    spendingConsistency: number;
  };
  incomeAnalysis: {
    totalIncome: number;
    incomeSources: Array<{
      source: string;
      amount: number;
      frequency: number;
    }>;
    dailyIncome: Array<{
      date: string;
      amount: number;
    }>;
    incomeStability: 'STABLE' | 'VOLATILE' | 'GROWING' | 'DECLINING';
    incomeGrowthRate: number;
  };
  cashFlowAnalysis: {
    netCashFlow: number;
    cashFlowData: Array<{
      date: string;
      income: number;
      expenses: number;
      netFlow: number;
    }>;
    cashFlowTrend: 'IMPROVING' | 'DECLINING' | 'STABLE' | 'VOLATILE';
    cashFlowVolatility: number;
    forecast: Array<{
      date: string;
      predictedFlow: number;
      confidence: number;
    }>;
  };
  behavioralPatterns: {
    spendingPattern: {
      impulseBuying: number;
      planningHorizon: string;
      riskTolerance: string;
    };
    timingPattern: {
      peakSpendingDays: string[];
      peakSpendingHours: number[];
      seasonalTrends: Array<{
        period: string;
        multiplier: number;
      }>;
    };
    fraudRiskAssessment: {
      overallRiskScore: number;
      riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
      anomalies: Array<{
        type: string;
        description: string;
        severity: string;
        date: string;
      }>;
    };
  };
  financialHealthScore: {
    overallScore: number;
    healthLevel: string;
    spendingBehaviorScore: { score: number; factors: string[] };
    savingsRateScore: { score: number; factors: string[] };
    debtToIncomeScore: { score: number; factors: string[] };
    improvements: Array<{
      area: string;
      suggestion: string;
      impact: string;
    }>;
  };
  predictiveInsights: {
    spendingForecast: {
      predictions: Array<{
        date: string;
        predictedAmount: number;
        confidence: number;
      }>;
      trend: 'INCREASING' | 'DECREASING' | 'STABLE';
    };
    categoryTrends: Array<{
      category: string;
      trend: string;
      predictedChange: number;
      expectedSpending: number;
    }>;
    recommendations: Array<{
      type: string;
      title: string;
      description: string;
      priority: 'HIGH' | 'MEDIUM' | 'LOW';
      estimatedSavings: number;
    }>;
  };
}

const AdvancedAnalyticsDashboard: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [analyticsData, setAnalyticsData] = useState<AnalyticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [dateRange, setDateRange] = useState('30d');

  useEffect(() => {
    loadAnalyticsData();
  }, [dateRange]);

  const loadAnalyticsData = async () => {
    setLoading(true);
    try {
      const endDate = new Date();
      const startDate = subDays(endDate, dateRange === '30d' ? 30 : dateRange === '90d' ? 90 : 7);
      
      const response = await fetch('/api/analytics/comprehensive', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          startDate: startDate.toISOString(),
          endDate: endDate.toISOString(),
        }),
      });
      
      const data = await response.json();
      setAnalyticsData(data);
    } catch (error) {
      console.error('Failed to load analytics data:', error);
    } finally {
      setLoading(false);
    }
  };

  const exportData = () => {
    if (!analyticsData) return;
    
    const dataToExport = {
      exportDate: new Date().toISOString(),
      dateRange,
      ...analyticsData,
    };
    
    const blob = new Blob([JSON.stringify(dataToExport, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `waqiti-analytics-${format(new Date(), 'yyyy-MM-dd')}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const renderTransactionSummary = () => {
    if (!analyticsData) return null;
    const { transactionSummary } = analyticsData;

    return (
      <Grid container spacing={3}>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Total Transactions
              </Typography>
              <Typography variant="h4" color="primary">
                {transactionSummary.totalTransactions.toLocaleString()}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                {transactionSummary.uniqueMerchants} unique merchants
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Total Spent
              </Typography>
              <Typography variant="h4" color="error">
                ${transactionSummary.totalSpent.toLocaleString()}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Avg: ${transactionSummary.averageTransactionAmount.toFixed(2)}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Total Received
              </Typography>
              <Typography variant="h4" color="success.main">
                ${transactionSummary.totalReceived.toLocaleString()}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                {transactionSummary.uniqueCategories} categories
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                Net Cash Flow
                {transactionSummary.netCashFlow > 0 ? (
                  <TrendingUpIcon color="success" sx={{ ml: 1 }} />
                ) : (
                  <TrendingDownIcon color="error" sx={{ ml: 1 }} />
                )}
              </Typography>
              <Typography 
                variant="h4" 
                color={transactionSummary.netCashFlow > 0 ? 'success.main' : 'error.main'}
              >
                ${Math.abs(transactionSummary.netCashFlow).toLocaleString()}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Range: ${transactionSummary.smallestTransaction} - ${transactionSummary.largestTransaction}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderSpendingAnalysis = () => {
    if (!analyticsData) return null;
    const { spendingAnalysis } = analyticsData;

    const categoryColors = [
      '#8884d8', '#82ca9d', '#ffc658', '#ff7300', '#00ff00',
      '#ff0000', '#00ffff', '#ff00ff', '#ffff00', '#0000ff'
    ];

    return (
      <Grid container spacing={3}>
        {/* Category Breakdown Pie Chart */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Spending by Category
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={spendingAnalysis.categoryBreakdown}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ category, percentage }) => `${category} (${percentage.toFixed(1)}%)`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="amount"
                  >
                    {spendingAnalysis.categoryBreakdown.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={categoryColors[index % categoryColors.length]} />
                    ))}
                  </Pie>
                  <RechartsTooltip formatter={(value: number) => [`$${value.toLocaleString()}`, 'Amount']} />
                </PieChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Daily Spending Trend */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Daily Spending Trend
              </Typography>
              <Chip 
                label={spendingAnalysis.spendingTrend} 
                color={
                  spendingAnalysis.spendingTrend === 'INCREASING' ? 'error' :
                  spendingAnalysis.spendingTrend === 'DECREASING' ? 'success' : 'default'
                }
                sx={{ mb: 2 }}
              />
              <ResponsiveContainer width="100%" height={250}>
                <AreaChart data={spendingAnalysis.dailySpending}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis 
                    dataKey="date" 
                    tickFormatter={(date) => format(parseISO(date), 'MM/dd')}
                  />
                  <YAxis tickFormatter={(value) => `$${value}`} />
                  <RechartsTooltip 
                    labelFormatter={(date) => format(parseISO(date), 'MMM dd, yyyy')}
                    formatter={(value: number) => [`$${value.toLocaleString()}`, 'Amount']}
                  />
                  <Area 
                    type="monotone" 
                    dataKey="amount" 
                    stroke="#8884d8" 
                    fill={alpha('#8884d8', 0.3)} 
                  />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Hourly Spending Pattern */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Spending by Hour of Day
              </Typography>
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={spendingAnalysis.hourlySpending}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="hour" />
                  <YAxis tickFormatter={(value) => `$${value}`} />
                  <RechartsTooltip formatter={(value: number) => [`$${value.toLocaleString()}`, 'Amount']} />
                  <Bar dataKey="amount" fill="#82ca9d" />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Spending Metrics */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Spending Metrics
              </Typography>
              <Box sx={{ mt: 2 }}>
                <Typography variant="body2" gutterBottom>
                  Spending Velocity: ${spendingAnalysis.spendingVelocity.toFixed(2)}/day
                </Typography>
                <LinearProgress 
                  variant="determinate" 
                  value={Math.min(spendingAnalysis.spendingVelocity / 100 * 100, 100)} 
                  sx={{ mb: 2 }}
                />
                
                <Typography variant="body2" gutterBottom>
                  Spending Consistency: {(spendingAnalysis.spendingConsistency * 100).toFixed(1)}%
                </Typography>
                <LinearProgress 
                  variant="determinate" 
                  value={spendingAnalysis.spendingConsistency * 100} 
                  color="secondary"
                />
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderCashFlowAnalysis = () => {
    if (!analyticsData) return null;
    const { cashFlowAnalysis } = analyticsData;

    return (
      <Grid container spacing={3}>
        {/* Cash Flow Chart */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Cash Flow Analysis
              </Typography>
              <Chip 
                label={cashFlowAnalysis.cashFlowTrend} 
                color={
                  cashFlowAnalysis.cashFlowTrend === 'IMPROVING' ? 'success' :
                  cashFlowAnalysis.cashFlowTrend === 'DECLINING' ? 'error' : 'default'
                }
                sx={{ mb: 2 }}
              />
              <ResponsiveContainer width="100%" height={400}>
                <ComposedChart data={cashFlowAnalysis.cashFlowData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis 
                    dataKey="date" 
                    tickFormatter={(date) => format(parseISO(date), 'MM/dd')}
                  />
                  <YAxis tickFormatter={(value) => `$${value}`} />
                  <RechartsTooltip 
                    labelFormatter={(date) => format(parseISO(date), 'MMM dd, yyyy')}
                    formatter={(value: number, name: string) => [`$${value.toLocaleString()}`, name]}
                  />
                  <Legend />
                  <Bar dataKey="income" fill="#82ca9d" name="Income" />
                  <Bar dataKey="expenses" fill="#ff7300" name="Expenses" />
                  <Line 
                    type="monotone" 
                    dataKey="netFlow" 
                    stroke="#8884d8" 
                    strokeWidth={3}
                    name="Net Flow"
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Cash Flow Forecast */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Cash Flow Forecast (30 days)
              </Typography>
              <ResponsiveContainer width="100%" height={250}>
                <AreaChart data={cashFlowAnalysis.forecast}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis 
                    dataKey="date" 
                    tickFormatter={(date) => format(parseISO(date), 'MM/dd')}
                  />
                  <YAxis tickFormatter={(value) => `$${value}`} />
                  <RechartsTooltip 
                    labelFormatter={(date) => format(parseISO(date), 'MMM dd, yyyy')}
                    formatter={(value: number) => [`$${value.toLocaleString()}`, 'Predicted Flow']}
                  />
                  <Area 
                    type="monotone" 
                    dataKey="predictedFlow" 
                    stroke="#8884d8" 
                    fill={alpha('#8884d8', 0.3)} 
                  />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Cash Flow Summary */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Cash Flow Summary
              </Typography>
              <Box sx={{ mt: 2 }}>
                <Typography variant="h4" color="primary" gutterBottom>
                  ${Math.abs(cashFlowAnalysis.netCashFlow).toLocaleString()}
                </Typography>
                <Typography 
                  variant="body1" 
                  color={cashFlowAnalysis.netCashFlow > 0 ? 'success.main' : 'error.main'}
                  gutterBottom
                >
                  {cashFlowAnalysis.netCashFlow > 0 ? 'Positive' : 'Negative'} Net Cash Flow
                </Typography>
                
                <Typography variant="body2" gutterBottom>
                  Volatility: {(cashFlowAnalysis.cashFlowVolatility * 100).toFixed(1)}%
                </Typography>
                <LinearProgress 
                  variant="determinate" 
                  value={Math.min(cashFlowAnalysis.cashFlowVolatility * 100, 100)} 
                  color={cashFlowAnalysis.cashFlowVolatility > 0.3 ? 'error' : 'success'}
                />
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderBehavioralInsights = () => {
    if (!analyticsData) return null;
    const { behavioralPatterns, financialHealthScore } = analyticsData;

    const riskData = [
      { subject: 'Fraud Risk', score: 100 - behavioralPatterns.fraudRiskAssessment.overallRiskScore },
      { subject: 'Spending Health', score: financialHealthScore.spendingBehaviorScore.score },
      { subject: 'Savings Rate', score: financialHealthScore.savingsRateScore.score },
      { subject: 'Debt Ratio', score: financialHealthScore.debtToIncomeScore.score },
      { subject: 'Pattern Stability', score: 85 }, // Mock data
      { subject: 'Budget Adherence', score: 78 }, // Mock data
    ];

    return (
      <Grid container spacing={3}>
        {/* Financial Health Score */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                <AnalyticsIcon sx={{ mr: 1 }} />
                Financial Health Score
              </Typography>
              <Box textAlign="center" sx={{ mb: 2 }}>
                <Typography variant="h2" color="primary">
                  {financialHealthScore.overallScore.toFixed(0)}
                </Typography>
                <Chip 
                  label={financialHealthScore.healthLevel} 
                  color={
                    financialHealthScore.healthLevel === 'EXCELLENT' ? 'success' :
                    financialHealthScore.healthLevel === 'GOOD' ? 'primary' :
                    financialHealthScore.healthLevel === 'FAIR' ? 'warning' : 'error'
                  }
                />
              </Box>
              
              <ResponsiveContainer width="100%" height={200}>
                <RadarChart data={riskData}>
                  <PolarGrid />
                  <PolarAngleAxis dataKey="subject" />
                  <PolarRadiusAxis angle={90} domain={[0, 100]} />
                  <Radar 
                    name="Score" 
                    dataKey="score" 
                    stroke="#8884d8" 
                    fill="#8884d8" 
                    fillOpacity={0.3} 
                  />
                </RadarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Fraud Risk Assessment */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                <SecurityIcon sx={{ mr: 1 }} />
                Security Assessment
              </Typography>
              
              <Alert 
                severity={
                  behavioralPatterns.fraudRiskAssessment.riskLevel === 'LOW' ? 'success' :
                  behavioralPatterns.fraudRiskAssessment.riskLevel === 'MEDIUM' ? 'warning' : 'error'
                }
                sx={{ mb: 2 }}
              >
                Risk Level: {behavioralPatterns.fraudRiskAssessment.riskLevel}
              </Alert>

              <Typography variant="body2" gutterBottom>
                Risk Score: {behavioralPatterns.fraudRiskAssessment.overallRiskScore.toFixed(1)}/100
              </Typography>
              <LinearProgress 
                variant="determinate" 
                value={behavioralPatterns.fraudRiskAssessment.overallRiskScore} 
                color={
                  behavioralPatterns.fraudRiskAssessment.riskLevel === 'LOW' ? 'success' :
                  behavioralPatterns.fraudRiskAssessment.riskLevel === 'MEDIUM' ? 'warning' : 'error'
                }
                sx={{ mb: 2 }}
              />

              <Typography variant="body2" gutterBottom>
                Recent Anomalies: {behavioralPatterns.fraudRiskAssessment.anomalies.length}
              </Typography>
              {behavioralPatterns.fraudRiskAssessment.anomalies.slice(0, 3).map((anomaly, index) => (
                <Chip 
                  key={index}
                  label={anomaly.type}
                  size="small"
                  variant="outlined"
                  sx={{ mr: 1, mb: 1 }}
                />
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* Behavioral Patterns */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Spending Patterns
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" gutterBottom>
                    Impulse Buying Score
                  </Typography>
                  <Typography variant="h5" color="primary">
                    {(behavioralPatterns.spendingPattern.impulseBuying * 100).toFixed(0)}%
                  </Typography>
                  <LinearProgress 
                    variant="determinate" 
                    value={behavioralPatterns.spendingPattern.impulseBuying * 100} 
                    color={behavioralPatterns.spendingPattern.impulseBuying > 0.7 ? 'error' : 'success'}
                  />
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" gutterBottom>
                    Planning Horizon
                  </Typography>
                  <Chip 
                    label={behavioralPatterns.spendingPattern.planningHorizon} 
                    color="primary" 
                  />
                </Grid>
                
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" gutterBottom>
                    Risk Tolerance
                  </Typography>
                  <Chip 
                    label={behavioralPatterns.spendingPattern.riskTolerance}
                    color={
                      behavioralPatterns.spendingPattern.riskTolerance === 'HIGH' ? 'error' :
                      behavioralPatterns.spendingPattern.riskTolerance === 'MEDIUM' ? 'warning' : 'success'
                    }
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderPredictiveInsights = () => {
    if (!analyticsData) return null;
    const { predictiveInsights } = analyticsData;

    return (
      <Grid container spacing={3}>
        {/* Spending Forecast */}
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                30-Day Spending Forecast
              </Typography>
              <Chip 
                label={`Trend: ${predictiveInsights.spendingForecast.trend}`}
                color={
                  predictiveInsights.spendingForecast.trend === 'INCREASING' ? 'error' :
                  predictiveInsights.spendingForecast.trend === 'DECREASING' ? 'success' : 'default'
                }
                sx={{ mb: 2 }}
              />
              <ResponsiveContainer width="100%" height={300}>
                <AreaChart data={predictiveInsights.spendingForecast.predictions}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis 
                    dataKey="date" 
                    tickFormatter={(date) => format(parseISO(date), 'MM/dd')}
                  />
                  <YAxis tickFormatter={(value) => `$${value}`} />
                  <RechartsTooltip 
                    labelFormatter={(date) => format(parseISO(date), 'MMM dd, yyyy')}
                    formatter={(value: number, name: string) => [`$${value.toLocaleString()}`, name]}
                  />
                  <Area 
                    type="monotone" 
                    dataKey="predictedAmount" 
                    stroke="#8884d8" 
                    fill={alpha('#8884d8', 0.3)}
                    name="Predicted Spending" 
                  />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Category Trends */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Category Trends
              </Typography>
              {predictiveInsights.categoryTrends.slice(0, 5).map((trend, index) => (
                <Box key={index} sx={{ mb: 2 }}>
                  <Typography variant="body2" gutterBottom>
                    {trend.category}
                  </Typography>
                  <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Chip 
                      size="small"
                      label={trend.trend}
                      color={
                        trend.trend === 'INCREASING' ? 'error' :
                        trend.trend === 'DECREASING' ? 'success' : 'default'
                      }
                    />
                    <Typography variant="body2">
                      ${trend.expectedSpending.toLocaleString()}
                    </Typography>
                  </Box>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* Recommendations */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Personalized Recommendations
              </Typography>
              <Grid container spacing={2}>
                {predictiveInsights.recommendations.slice(0, 6).map((rec, index) => (
                  <Grid item xs={12} md={6} key={index}>
                    <Card variant="outlined">
                      <CardContent>
                        <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                          <Chip 
                            label={rec.type} 
                            size="small" 
                            color="primary" 
                          />
                          <Chip 
                            label={rec.priority} 
                            size="small"
                            color={
                              rec.priority === 'HIGH' ? 'error' :
                              rec.priority === 'MEDIUM' ? 'warning' : 'default'
                            }
                          />
                        </Box>
                        <Typography variant="subtitle2" gutterBottom>
                          {rec.title}
                        </Typography>
                        <Typography variant="body2" color="textSecondary" gutterBottom>
                          {rec.description}
                        </Typography>
                        {rec.estimatedSavings > 0 && (
                          <Typography variant="body2" color="success.main">
                            Potential savings: ${rec.estimatedSavings.toLocaleString()}
                          </Typography>
                        )}
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const tabs = [
    { label: 'Overview', icon: <AnalyticsIcon /> },
    { label: 'Spending', icon: <PieChartIcon /> },
    { label: 'Cash Flow', icon: <TimelineIcon /> },
    { label: 'Behavioral', icon: <BarChartIcon /> },
    { label: 'Predictive', icon: <TrendingUpIcon /> },
  ];

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <LinearProgress />
        <Typography sx={{ mt: 2 }}>Loading analytics data...</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Advanced Analytics Dashboard
        </Typography>
        <Box display="flex" gap={1}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadAnalyticsData}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={exportData}
          >
            Export
          </Button>
        </Box>
      </Box>

      {/* Date Range Selector */}
      <Box mb={3}>
        <Tabs value={dateRange} onChange={(e, newValue) => setDateRange(newValue)}>
          <Tab label="7 Days" value="7d" />
          <Tab label="30 Days" value="30d" />
          <Tab label="90 Days" value="90d" />
        </Tabs>
      </Box>

      {/* Main Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={activeTab} onChange={(e, newValue) => setActiveTab(newValue)}>
          {tabs.map((tab, index) => (
            <Tab 
              key={index}
              icon={tab.icon} 
              label={tab.label} 
              iconPosition="start"
            />
          ))}
        </Tabs>
      </Box>

      {/* Tab Content */}
      <Box sx={{ mt: 3 }}>
        {activeTab === 0 && renderTransactionSummary()}
        {activeTab === 1 && renderSpendingAnalysis()}
        {activeTab === 2 && renderCashFlowAnalysis()}
        {activeTab === 3 && renderBehavioralInsights()}
        {activeTab === 4 && renderPredictiveInsights()}
      </Box>
    </Box>
  );
};

export default AdvancedAnalyticsDashboard;