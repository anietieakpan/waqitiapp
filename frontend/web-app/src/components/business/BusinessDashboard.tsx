import React, { useState, useEffect } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  Chip,
  IconButton,
  Avatar,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  LinearProgress,
  Alert,
  Tabs,
  Tab,
  useTheme,
  alpha,
} from '@mui/material';
import BusinessIcon from '@mui/icons-material/Business';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import ReceiptIcon from '@mui/icons-material/Receipt';
import PeopleIcon from '@mui/icons-material/People';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import WarningIcon from '@mui/icons-material/Warning';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import UploadIcon from '@mui/icons-material/Upload';
import SendIcon from '@mui/icons-material/Send';
import EditIcon from '@mui/icons-material/Edit';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import AssessmentIcon from '@mui/icons-material/Assessment';
import NotificationsIcon from '@mui/icons-material/Notifications';
import SecurityIcon from '@mui/icons-material/Security';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';;
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
import { format, subDays, parseISO } from 'date-fns';

interface BusinessAccount {
  id: string;
  businessName: string;
  businessType: string;
  tier: 'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE';
  status: 'ACTIVE' | 'PENDING' | 'SUSPENDED';
  verificationStatus: 'VERIFIED' | 'PENDING' | 'REJECTED';
  balance: number;
  monthlySpending: number;
  monthlyIncome: number;
  teamMemberCount: number;
  activeInvoiceCount: number;
  pendingExpenseCount: number;
}

interface BusinessTransaction {
  id: string;
  type: 'INCOME' | 'EXPENSE' | 'TRANSFER';
  category: string;
  amount: number;
  description: string;
  merchant: string;
  date: string;
  status: 'COMPLETED' | 'PENDING' | 'FAILED';
}

interface BusinessExpense {
  id: string;
  category: string;
  amount: number;
  description: string;
  submittedBy: string;
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  receiptUrl?: string;
  expenseDate: string;
}

interface BusinessInvoice {
  id: string;
  invoiceNumber: string;
  customerName: string;
  amount: number;
  dueDate: string;
  status: 'DRAFT' | 'SENT' | 'PAID' | 'OVERDUE';
  paymentStatus: 'UNPAID' | 'PARTIALLY_PAID' | 'PAID';
}

interface BusinessAnalytics {
  cashFlow: Array<{
    date: string;
    income: number;
    expenses: number;
    netFlow: number;
  }>;
  expensesByCategory: Array<{
    category: string;
    amount: number;
    percentage: number;
  }>;
  monthlyTrends: {
    revenue: number;
    expenses: number;
    profit: number;
    growth: number;
  };
}

const BusinessDashboard: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [businessAccount, setBusinessAccount] = useState<BusinessAccount | null>(null);
  const [recentTransactions, setRecentTransactions] = useState<BusinessTransaction[]>([]);
  const [pendingExpenses, setPendingExpenses] = useState<BusinessExpense[]>([]);
  const [recentInvoices, setRecentInvoices] = useState<BusinessInvoice[]>([]);
  const [analytics, setAnalytics] = useState<BusinessAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadBusinessData();
  }, []);

  const loadBusinessData = async () => {
    try {
      setLoading(true);
      
      // Load business account info
      const accountResponse = await fetch('/api/business/account');
      const account = await accountResponse.json();
      setBusinessAccount(account);

      // Load recent transactions
      const transactionsResponse = await fetch('/api/business/transactions?limit=10');
      const transactions = await transactionsResponse.json();
      setRecentTransactions(transactions.content || []);

      // Load pending expenses
      const expensesResponse = await fetch('/api/business/expenses?status=SUBMITTED&limit=5');
      const expenses = await expensesResponse.json();
      setPendingExpenses(expenses.content || []);

      // Load recent invoices
      const invoicesResponse = await fetch('/api/business/invoices?limit=5');
      const invoices = await invoicesResponse.json();
      setRecentInvoices(invoices.content || []);

      // Load analytics
      const analyticsResponse = await fetch('/api/business/analytics', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          startDate: subDays(new Date(), 30).toISOString(),
          endDate: new Date().toISOString(),
        }),
      });
      const analyticsData = await analyticsResponse.json();
      setAnalytics(analyticsData);

    } catch (error) {
      console.error('Failed to load business data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getTierColor = (tier: string) => {
    switch (tier) {
      case 'STARTER': return theme.palette.info.main;
      case 'PROFESSIONAL': return theme.palette.warning.main;
      case 'ENTERPRISE': return theme.palette.success.main;
      default: return theme.palette.grey[500];
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE': case 'VERIFIED': case 'COMPLETED': case 'PAID': case 'APPROVED':
        return theme.palette.success.main;
      case 'PENDING': case 'SUBMITTED': case 'SENT':
        return theme.palette.warning.main;
      case 'SUSPENDED': case 'REJECTED': case 'FAILED': case 'OVERDUE':
        return theme.palette.error.main;
      default:
        return theme.palette.grey[500];
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const renderOverviewCards = () => {
    if (!businessAccount) return null;

    return (
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="h6" color="textSecondary" gutterBottom>
                    Account Balance
                  </Typography>
                  <Typography variant="h4" component="div">
                    {formatCurrency(businessAccount.balance)}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.primary.main }}>
                  <AccountBalanceIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="h6" color="textSecondary" gutterBottom>
                    Monthly Income
                  </Typography>
                  <Typography variant="h4" component="div" color="success.main">
                    {formatCurrency(businessAccount.monthlyIncome)}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.success.main }}>
                  <TrendingUpIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="h6" color="textSecondary" gutterBottom>
                    Monthly Spending
                  </Typography>
                  <Typography variant="h4" component="div" color="error.main">
                    {formatCurrency(businessAccount.monthlySpending)}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.error.main }}>
                  <TrendingDownIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography variant="h6" color="textSecondary" gutterBottom>
                    Team Members
                  </Typography>
                  <Typography variant="h4" component="div">
                    {businessAccount.teamMemberCount}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.info.main }}>
                  <PeopleIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    );
  };

  const renderBusinessInfo = () => {
    if (!businessAccount) return null;

    return (
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Box display="flex" alignItems="center">
              <Avatar sx={{ width: 56, height: 56, mr: 2, bgcolor: theme.palette.primary.main }}>
                <BusinessIcon />
              </Avatar>
              <Box>
                <Typography variant="h5" component="h1">
                  {businessAccount.businessName}
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  {businessAccount.businessType}
                </Typography>
              </Box>
            </Box>
            <Box display="flex" alignItems="center" gap={1}>
              <Chip 
                label={businessAccount.tier} 
                color="primary"
                sx={{ bgcolor: getTierColor(businessAccount.tier) }}
              />
              <Chip 
                label={businessAccount.status}
                sx={{ bgcolor: alpha(getStatusColor(businessAccount.status), 0.1) }}
              />
              <Chip 
                label={businessAccount.verificationStatus}
                icon={businessAccount.verificationStatus === 'VERIFIED' ? <CheckCircleIcon /> : <ScheduleIcon />}
                sx={{ bgcolor: alpha(getStatusColor(businessAccount.verificationStatus), 0.1) }}
              />
            </Box>
          </Box>

          {businessAccount.verificationStatus !== 'VERIFIED' && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              Your business account verification is {businessAccount.verificationStatus.toLowerCase()}. 
              Complete verification to unlock all features.
            </Alert>
          )}
        </CardContent>
      </Card>
    );
  };

  const renderCashFlowChart = () => {
    if (!analytics?.cashFlow) return null;

    return (
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Cash Flow (Last 30 Days)
          </Typography>
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={analytics.cashFlow}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis 
                dataKey="date" 
                tickFormatter={(date) => format(parseISO(date), 'MM/dd')}
              />
              <YAxis tickFormatter={(value) => `$${value.toLocaleString()}`} />
              <Tooltip 
                labelFormatter={(date) => format(parseISO(date), 'MMM dd, yyyy')}
                formatter={(value: number, name: string) => [`$${value.toLocaleString()}`, name]}
              />
              <Legend />
              <Area 
                type="monotone" 
                dataKey="income" 
                stackId="1"
                stroke={theme.palette.success.main} 
                fill={alpha(theme.palette.success.main, 0.3)}
                name="Income"
              />
              <Area 
                type="monotone" 
                dataKey="expenses" 
                stackId="2"
                stroke={theme.palette.error.main} 
                fill={alpha(theme.palette.error.main, 0.3)}
                name="Expenses"
              />
              <Line 
                type="monotone" 
                dataKey="netFlow" 
                stroke={theme.palette.primary.main}
                strokeWidth={3}
                name="Net Flow"
              />
            </AreaChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    );
  };

  const renderExpenseBreakdown = () => {
    if (!analytics?.expensesByCategory) return null;

    const colors = [
      theme.palette.primary.main,
      theme.palette.secondary.main,
      theme.palette.error.main,
      theme.palette.warning.main,
      theme.palette.info.main,
      theme.palette.success.main,
    ];

    return (
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Expenses by Category
          </Typography>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={analytics.expensesByCategory}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ category, percentage }) => `${category} (${percentage.toFixed(1)}%)`}
                outerRadius={80}
                fill="#8884d8"
                dataKey="amount"
              >
                {analytics.expensesByCategory.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={colors[index % colors.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value: number) => [`$${value.toLocaleString()}`, 'Amount']} />
            </PieChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    );
  };

  const renderRecentTransactions = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            Recent Transactions
          </Typography>
          <Button
            variant="outlined"
            size="small"
            startIcon={<AddIcon />}
            href="/business/transactions/new"
          >
            New Transaction
          </Button>
        </Box>
        <List>
          {recentTransactions.slice(0, 5).map((transaction, index) => (
            <React.Fragment key={transaction.id}>
              <ListItem>
                <ListItemIcon>
                  <Avatar 
                    sx={{ 
                      width: 32, 
                      height: 32,
                      bgcolor: transaction.type === 'INCOME' ? 
                        theme.palette.success.main : 
                        theme.palette.error.main 
                    }}
                  >
                    {transaction.type === 'INCOME' ? <TrendingUpIcon /> : <TrendingDownIcon />}
                  </Avatar>
                </ListItemIcon>
                <ListItemText
                  primary={transaction.description}
                  secondary={`${transaction.merchant} • ${transaction.category}`}
                />
                <ListItemSecondaryAction>
                  <Box textAlign="right">
                    <Typography 
                      variant="body1" 
                      color={transaction.type === 'INCOME' ? 'success.main' : 'error.main'}
                    >
                      {transaction.type === 'INCOME' ? '+' : '-'}{formatCurrency(Math.abs(transaction.amount))}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      {format(parseISO(transaction.date), 'MMM dd')}
                    </Typography>
                  </Box>
                </ListItemSecondaryAction>
              </ListItem>
              {index < recentTransactions.length - 1 && <Divider />}
            </React.Fragment>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderPendingExpenses = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            Pending Expenses ({pendingExpenses.length})
          </Typography>
          <Button
            variant="outlined"
            size="small"
            startIcon={<UploadIcon />}
            href="/business/expenses/new"
          >
            Submit Expense
          </Button>
        </Box>
        <List>
          {pendingExpenses.map((expense, index) => (
            <React.Fragment key={expense.id}>
              <ListItem>
                <ListItemIcon>
                  <Avatar sx={{ width: 32, height: 32, bgcolor: theme.palette.warning.main }}>
                    <ReceiptIcon />
                  </Avatar>
                </ListItemIcon>
                <ListItemText
                  primary={expense.description}
                  secondary={`${expense.category} • ${expense.submittedBy}`}
                />
                <ListItemSecondaryAction>
                  <Box textAlign="right">
                    <Typography variant="body1">
                      {formatCurrency(expense.amount)}
                    </Typography>
                    <Chip 
                      label={expense.status}
                      size="small"
                      sx={{ bgcolor: alpha(getStatusColor(expense.status), 0.1) }}
                    />
                  </Box>
                </ListItemSecondaryAction>
              </ListItem>
              {index < pendingExpenses.length - 1 && <Divider />}
            </React.Fragment>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderRecentInvoices = () => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            Recent Invoices
          </Typography>
          <Button
            variant="outlined"
            size="small"
            startIcon={<AddIcon />}
            href="/business/invoices/new"
          >
            Create Invoice
          </Button>
        </Box>
        <List>
          {recentInvoices.map((invoice, index) => (
            <React.Fragment key={invoice.id}>
              <ListItem>
                <ListItemIcon>
                  <Avatar sx={{ width: 32, height: 32, bgcolor: theme.palette.primary.main }}>
                    <AttachMoneyIcon />
                  </Avatar>
                </ListItemIcon>
                <ListItemText
                  primary={`Invoice #${invoice.invoiceNumber}`}
                  secondary={invoice.customerName}
                />
                <ListItemSecondaryAction>
                  <Box textAlign="right">
                    <Typography variant="body1">
                      {formatCurrency(invoice.amount)}
                    </Typography>
                    <Chip 
                      label={invoice.paymentStatus}
                      size="small"
                      sx={{ bgcolor: alpha(getStatusColor(invoice.paymentStatus), 0.1) }}
                    />
                  </Box>
                </ListItemSecondaryAction>
              </ListItem>
              {index < recentInvoices.length - 1 && <Divider />}
            </React.Fragment>
          ))}
        </List>
      </CardContent>
    </Card>
  );

  const renderQuickActions = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Quick Actions
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={6}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<AddIcon />}
              href="/business/transactions/new"
            >
              Record Transaction
            </Button>
          </Grid>
          <Grid item xs={6}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<UploadIcon />}
              href="/business/expenses/new"
            >
              Submit Expense
            </Button>
          </Grid>
          <Grid item xs={6}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<SendIcon />}
              href="/business/invoices/new"
            >
              Create Invoice
            </Button>
          </Grid>
          <Grid item xs={6}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<PeopleIcon />}
              href="/business/team"
            >
              Manage Team
            </Button>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <LinearProgress />
        <Typography sx={{ mt: 2 }}>Loading business dashboard...</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Business Info Header */}
      {renderBusinessInfo()}

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={(e, newValue) => setActiveTab(newValue)}>
          <Tab icon={<AssessmentIcon />} label="Overview" iconPosition="start" />
          <Tab icon={<ReceiptIcon />} label="Expenses" iconPosition="start" />
          <Tab icon={<AttachMoneyIcon />} label="Invoices" iconPosition="start" />
          <Tab icon={<PeopleIcon />} label="Team" iconPosition="start" />
        </Tabs>
      </Box>

      {/* Tab Content */}
      {activeTab === 0 && (
        <Box>
          {/* Overview Cards */}
          {renderOverviewCards()}

          {/* Charts Row */}
          <Grid container spacing={3} sx={{ mt: 3 }}>
            <Grid item xs={12} md={8}>
              {renderCashFlowChart()}
            </Grid>
            <Grid item xs={12} md={4}>
              {renderExpenseBreakdown()}
            </Grid>
          </Grid>

          {/* Activities Row */}
          <Grid container spacing={3} sx={{ mt: 3 }}>
            <Grid item xs={12} md={4}>
              {renderRecentTransactions()}
            </Grid>
            <Grid item xs={12} md={4}>
              {renderPendingExpenses()}
            </Grid>
            <Grid item xs={12} md={4}>
              {renderRecentInvoices()}
            </Grid>
          </Grid>

          {/* Quick Actions */}
          <Grid container spacing={3} sx={{ mt: 3 }}>
            <Grid item xs={12} md={4}>
              {renderQuickActions()}
            </Grid>
          </Grid>
        </Box>
      )}

      {activeTab === 1 && (
        <Box>
          <Typography variant="h5">Expense Management</Typography>
          <Typography variant="body1" color="textSecondary">
            View and manage business expenses, receipts, and reimbursements.
          </Typography>
        </Box>
      )}

      {activeTab === 2 && (
        <Box>
          <Typography variant="h5">Invoice Management</Typography>
          <Typography variant="body1" color="textSecondary">
            Create, send, and track invoices and payments.
          </Typography>
        </Box>
      )}

      {activeTab === 3 && (
        <Box>
          <Typography variant="h5">Team Management</Typography>
          <Typography variant="body1" color="textSecondary">
            Manage team members, roles, and permissions.
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default BusinessDashboard;