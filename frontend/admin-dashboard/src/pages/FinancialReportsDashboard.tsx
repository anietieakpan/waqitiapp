import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  Chip
} from '@mui/material';
import {
  TrendingUp as TrendingUpIcon,
  TrendingDown as TrendingDownIcon,
  Download as DownloadIcon,
  AttachMoney as MoneyIcon,
  Receipt as ReceiptIcon,
  AccountBalance as BalanceIcon,
  ShowChart as ChartIcon
} from '@mui/icons-material';
import { format } from 'date-fns';
import {
  LineChart,
  Line,
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
  AreaChart,
  Area
} from 'recharts';
import axios from 'axios';

interface FinancialMetrics {
  totalRevenue: number;
  totalTransactions: number;
  avgTransactionValue: number;
  successRate: number;
  totalFees: number;
  netRevenue: number;
  growthRate: number;
  activeUsers: number;
}

interface RevenueData {
  date: string;
  revenue: number;
  transactions: number;
  fees: number;
}

interface PaymentMethodData {
  method: string;
  volume: number;
  percentage: number;
}

interface TopMerchant {
  id: string;
  name: string;
  revenue: number;
  transactions: number;
  avgTicket: number;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => {
  return (
    <div hidden={value !== index} style={{ padding: '24px 0' }}>
      {value === index && children}
    </div>
  );
};

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884D8'];

const FinancialReportsDashboard: React.FC = () => {
  const [metrics, setMetrics] = useState<FinancialMetrics>({
    totalRevenue: 0,
    totalTransactions: 0,
    avgTransactionValue: 0,
    successRate: 0,
    totalFees: 0,
    netRevenue: 0,
    growthRate: 0,
    activeUsers: 0
  });
  const [revenueData, setRevenueData] = useState<RevenueData[]>([]);
  const [paymentMethodData, setPaymentMethodData] = useState<PaymentMethodData[]>([]);
  const [topMerchants, setTopMerchants] = useState<TopMerchant[]>([]);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState<string>('30d');
  const [tabValue, setTabValue] = useState(0);

  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  useEffect(() => {
    loadFinancialData();
  }, [period]);

  const loadFinancialData = async () => {
    setLoading(true);
    try {
      const [metricsRes, revenueRes, methodsRes, merchantsRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/reports/financial/metrics`, { params: { period } }),
        axios.get(`${API_BASE_URL}/admin/reports/financial/revenue-trend`, { params: { period } }),
        axios.get(`${API_BASE_URL}/admin/reports/financial/payment-methods`, { params: { period } }),
        axios.get(`${API_BASE_URL}/admin/reports/financial/top-merchants`, { params: { period, limit: 10 } })
      ]);

      setMetrics(metricsRes.data);
      setRevenueData(revenueRes.data);
      setPaymentMethodData(methodsRes.data);
      setTopMerchants(merchantsRes.data);
    } catch (error) {
      console.error('Failed to load financial data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async (reportType: string) => {
    try {
      const response = await axios.get(`${API_BASE_URL}/admin/reports/financial/export/${reportType}`, {
        params: { period },
        responseType: 'blob'
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${reportType}_${format(new Date(), 'yyyy-MM-dd')}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error('Failed to export report:', error);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          Financial Reports Dashboard
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Period</InputLabel>
            <Select value={period} onChange={(e) => setPeriod(e.target.value)} label="Period">
              <MenuItem value="7d">Last 7 Days</MenuItem>
              <MenuItem value="30d">Last 30 Days</MenuItem>
              <MenuItem value="90d">Last 90 Days</MenuItem>
              <MenuItem value="1y">Last Year</MenuItem>
              <MenuItem value="ytd">Year to Date</MenuItem>
            </Select>
          </FormControl>
          <Button variant="outlined" startIcon={<DownloadIcon />} onClick={() => handleExport('executive-summary')}>
            Export Report
          </Button>
        </Box>
      </Box>

      {/* Key Metrics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Total Revenue
                  </Typography>
                  <Typography variant="h4">${metrics.totalRevenue.toLocaleString()}</Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                    {metrics.growthRate >= 0 ? (
                      <TrendingUpIcon color="success" fontSize="small" />
                    ) : (
                      <TrendingDownIcon color="error" fontSize="small" />
                    )}
                    <Typography
                      variant="body2"
                      color={metrics.growthRate >= 0 ? 'success.main' : 'error.main'}
                      sx={{ ml: 0.5 }}
                    >
                      {Math.abs(metrics.growthRate).toFixed(1)}%
                    </Typography>
                  </Box>
                </Box>
                <MoneyIcon sx={{ fontSize: 40, color: 'primary.main', opacity: 0.3 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Total Transactions
                  </Typography>
                  <Typography variant="h4">{metrics.totalTransactions.toLocaleString()}</Typography>
                  <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                    {metrics.successRate.toFixed(1)}% success rate
                  </Typography>
                </Box>
                <ReceiptIcon sx={{ fontSize: 40, color: 'success.main', opacity: 0.3 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Avg Transaction
                  </Typography>
                  <Typography variant="h4">${metrics.avgTransactionValue.toLocaleString()}</Typography>
                  <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                    Per transaction
                  </Typography>
                </Box>
                <ChartIcon sx={{ fontSize: 40, color: 'info.main', opacity: 0.3 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Box>
                  <Typography color="textSecondary" gutterBottom>
                    Net Revenue
                  </Typography>
                  <Typography variant="h4">${metrics.netRevenue.toLocaleString()}</Typography>
                  <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                    After ${metrics.totalFees.toLocaleString()} fees
                  </Typography>
                </Box>
                <BalanceIcon sx={{ fontSize: 40, color: 'warning.main', opacity: 0.3 }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Revenue Trend Chart */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Revenue Trend
          </Typography>
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={revenueData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <RechartsTooltip />
              <Legend />
              <Area type="monotone" dataKey="revenue" stackId="1" stroke="#8884d8" fill="#8884d8" name="Revenue" />
              <Area type="monotone" dataKey="fees" stackId="1" stroke="#82ca9d" fill="#82ca9d" name="Fees" />
            </AreaChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      {/* Tabs for Detailed Reports */}
      <Card>
        <Tabs value={tabValue} onChange={(e, newValue) => setTabValue(newValue)}>
          <Tab label="Payment Methods" />
          <Tab label="Top Merchants" />
          <Tab label="Transaction Volume" />
        </Tabs>

        {/* Payment Methods Tab */}
        <TabPanel value={tabValue} index={0}>
          <Grid container spacing={3}>
            <Grid item xs={12} md={6}>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={paymentMethodData}
                    dataKey="volume"
                    nameKey="method"
                    cx="50%"
                    cy="50%"
                    outerRadius={100}
                    label={(entry) => `${entry.method}: ${entry.percentage}%`}
                  >
                    {paymentMethodData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <RechartsTooltip />
                </PieChart>
              </ResponsiveContainer>
            </Grid>
            <Grid item xs={12} md={6}>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Payment Method</TableCell>
                      <TableCell align="right">Volume</TableCell>
                      <TableCell align="right">Percentage</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {paymentMethodData.map((method) => (
                      <TableRow key={method.method}>
                        <TableCell>{method.method}</TableCell>
                        <TableCell align="right">${method.volume.toLocaleString()}</TableCell>
                        <TableCell align="right">
                          <Chip label={`${method.percentage}%`} size="small" />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Grid>
          </Grid>
        </TabPanel>

        {/* Top Merchants Tab */}
        <TabPanel value={tabValue} index={1}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Rank</TableCell>
                  <TableCell>Merchant Name</TableCell>
                  <TableCell align="right">Revenue</TableCell>
                  <TableCell align="right">Transactions</TableCell>
                  <TableCell align="right">Avg Ticket</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {topMerchants.map((merchant, index) => (
                  <TableRow key={merchant.id}>
                    <TableCell>#{index + 1}</TableCell>
                    <TableCell>{merchant.name}</TableCell>
                    <TableCell align="right">
                      <Typography fontWeight="bold">${merchant.revenue.toLocaleString()}</Typography>
                    </TableCell>
                    <TableCell align="right">{merchant.transactions.toLocaleString()}</TableCell>
                    <TableCell align="right">${merchant.avgTicket.toLocaleString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </TabPanel>

        {/* Transaction Volume Tab */}
        <TabPanel value={tabValue} index={2}>
          <ResponsiveContainer width="100%" height={400}>
            <BarChart data={revenueData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <RechartsTooltip />
              <Legend />
              <Bar dataKey="transactions" fill="#8884d8" name="Transactions" />
            </BarChart>
          </ResponsiveContainer>
        </TabPanel>
      </Card>
    </Box>
  );
};

export default FinancialReportsDashboard;
