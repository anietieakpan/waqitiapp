import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Button,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Tooltip,
  CircularProgress,
  Snackbar,
  LinearProgress
} from '@mui/material';
import {
  AccountBalance as WalletIcon,
  TrendingUp as TrendingUpIcon,
  TrendingDown as TrendingDownIcon,
  Refresh as RefreshIcon,
  Block as FreezeIcon,
  CheckCircle as UnfreezeIcon,
  Edit as EditIcon,
  History as HistoryIcon,
  AttachMoney as MoneyIcon,
  Lock as LockIcon,
  LockOpen as UnlockIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  ContentCopy as CopyIcon
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
  AreaChart,
  Area
} from 'recharts';
import axios from 'axios';

interface Wallet {
  id: string;
  userId: string;
  userName: string;
  currency: string;
  balance: number;
  availableBalance: number;
  frozenBalance: number;
  pendingBalance: number;
  status: 'ACTIVE' | 'FROZEN' | 'SUSPENDED' | 'CLOSED';
  createdAt: string;
  updatedAt: string;
  lastTransactionAt?: string;
  dailyLimit: number;
  monthlyLimit: number;
  dailySpent: number;
  monthlySpent: number;
  tier: number;
}

interface Transaction {
  id: string;
  type: 'CREDIT' | 'DEBIT' | 'TRANSFER_IN' | 'TRANSFER_OUT';
  amount: number;
  balance: number;
  status: string;
  reference: string;
  description: string;
  createdAt: string;
}

interface BalanceHistory {
  date: string;
  balance: number;
  credits: number;
  debits: number;
}

interface WalletStats {
  totalTransactions: number;
  totalCredits: number;
  totalDebits: number;
  avgTransactionSize: number;
  largestTransaction: number;
  transactionCount30Days: number;
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

const WalletDetailView: React.FC = () => {
  const { walletId } = useParams<{ walletId: string }>();
  const navigate = useNavigate();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [balanceHistory, setBalanceHistory] = useState<BalanceHistory[]>([]);
  const [stats, setStats] = useState<WalletStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [tabValue, setTabValue] = useState(0);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [actionType, setActionType] = useState<'freeze' | 'unfreeze' | 'adjust' | 'setLimit'>('freeze');
  const [actionReason, setActionReason] = useState('');
  const [adjustAmount, setAdjustAmount] = useState<number>(0);
  const [limitAmount, setLimitAmount] = useState<number>(0);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  useEffect(() => {
    loadWalletData();
  }, [walletId]);

  const loadWalletData = async () => {
    setLoading(true);
    try {
      const [walletRes, transactionsRes, historyRes, statsRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/wallets/${walletId}`),
        axios.get(`${API_BASE_URL}/admin/wallets/${walletId}/transactions?limit=50`),
        axios.get(`${API_BASE_URL}/admin/wallets/${walletId}/balance-history?days=30`),
        axios.get(`${API_BASE_URL}/admin/wallets/${walletId}/stats`)
      ]);

      setWallet(walletRes.data);
      setTransactions(transactionsRes.data);
      setBalanceHistory(historyRes.data);
      setStats(statsRes.data);
      setLimitAmount(walletRes.data.dailyLimit);
    } catch (error) {
      console.error('Failed to load wallet data:', error);
      setSnackbar({ open: true, message: 'Failed to load wallet data', severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleAction = async () => {
    if (!actionReason.trim() && actionType !== 'adjust') {
      setSnackbar({ open: true, message: 'Please provide a reason', severity: 'error' });
      return;
    }

    try {
      let endpoint = '';
      let payload: any = { actionBy: 'current-admin' };

      switch (actionType) {
        case 'freeze':
          endpoint = `${API_BASE_URL}/admin/wallets/${walletId}/freeze`;
          payload.reason = actionReason;
          break;
        case 'unfreeze':
          endpoint = `${API_BASE_URL}/admin/wallets/${walletId}/unfreeze`;
          payload.reason = actionReason;
          break;
        case 'adjust':
          endpoint = `${API_BASE_URL}/admin/wallets/${walletId}/adjust-balance`;
          payload.amount = adjustAmount;
          payload.reason = actionReason;
          break;
        case 'setLimit':
          endpoint = `${API_BASE_URL}/admin/wallets/${walletId}/set-limit`;
          payload.dailyLimit = limitAmount;
          payload.reason = actionReason;
          break;
      }

      await axios.post(endpoint, payload);

      setSnackbar({ open: true, message: `Wallet ${actionType} successful`, severity: 'success' });
      setActionDialogOpen(false);
      setActionReason('');
      loadWalletData();
    } catch (error) {
      setSnackbar({ open: true, message: `Failed to ${actionType} wallet`, severity: 'error' });
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setSnackbar({ open: true, message: 'Copied to clipboard', severity: 'success' });
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'FROZEN': return 'error';
      case 'SUSPENDED': return 'warning';
      case 'CLOSED': return 'default';
      default: return 'default';
    }
  };

  const getTransactionTypeColor = (type: string) => {
    switch (type) {
      case 'CREDIT':
      case 'TRANSFER_IN':
        return 'success';
      case 'DEBIT':
      case 'TRANSFER_OUT':
        return 'error';
      default:
        return 'default';
    }
  };

  const calculateLimitUtilization = (spent: number, limit: number) => {
    return limit > 0 ? (spent / limit) * 100 : 0;
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!wallet) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Wallet not found</Alert>
      </Box>
    );
  }

  const dailyUtilization = calculateLimitUtilization(wallet.dailySpent, wallet.dailyLimit);
  const monthlyUtilization = calculateLimitUtilization(wallet.monthlySpent, wallet.monthlyLimit);

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Wallet Details - {wallet.currency}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography variant="body2" color="textSecondary" sx={{ fontFamily: 'monospace' }}>
              {wallet.id}
            </Typography>
            <IconButton size="small" onClick={() => copyToClipboard(wallet.id)}>
              <CopyIcon fontSize="small" />
            </IconButton>
            <Chip label={`Tier ${wallet.tier}`} size="small" color="primary" />
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadWalletData}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<EditIcon />}
            onClick={() => navigate(`/admin/users/${wallet.userId}`)}
          >
            View User
          </Button>
        </Box>
      </Box>

      {/* Balance Overview */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Balance
              </Typography>
              <Typography variant="h4">
                {wallet.currency} {wallet.balance.toLocaleString()}
              </Typography>
              <Chip
                label={wallet.status}
                color={getStatusColor(wallet.status) as any}
                size="small"
                sx={{ mt: 1 }}
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Available Balance
              </Typography>
              <Typography variant="h4" color="success.main">
                {wallet.currency} {wallet.availableBalance.toLocaleString()}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Can be withdrawn
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Frozen Balance
              </Typography>
              <Typography variant="h4" color="error.main">
                {wallet.currency} {wallet.frozenBalance.toLocaleString()}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                {wallet.frozenBalance > 0 ? 'Funds on hold' : 'No holds'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Pending Balance
              </Typography>
              <Typography variant="h4" color="warning.main">
                {wallet.currency} {wallet.pendingBalance.toLocaleString()}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Processing
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Limit Utilization */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Daily Limit Utilization
              </Typography>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" color="textSecondary">
                  {wallet.currency} {wallet.dailySpent.toLocaleString()} / {wallet.dailyLimit.toLocaleString()}
                </Typography>
                <Typography variant="body2" fontWeight="bold">
                  {dailyUtilization.toFixed(1)}%
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={Math.min(dailyUtilization, 100)}
                color={dailyUtilization >= 90 ? 'error' : dailyUtilization >= 70 ? 'warning' : 'success'}
                sx={{ height: 10, borderRadius: 5 }}
              />
              {dailyUtilization >= 90 && (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  Daily limit nearly exceeded
                </Alert>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Monthly Limit Utilization
              </Typography>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" color="textSecondary">
                  {wallet.currency} {wallet.monthlySpent.toLocaleString()} / {wallet.monthlyLimit.toLocaleString()}
                </Typography>
                <Typography variant="body2" fontWeight="bold">
                  {monthlyUtilization.toFixed(1)}%
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={Math.min(monthlyUtilization, 100)}
                color={monthlyUtilization >= 90 ? 'error' : monthlyUtilization >= 70 ? 'warning' : 'success'}
                sx={{ height: 10, borderRadius: 5 }}
              />
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Wallet Information */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Wallet Information
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <List dense>
                <ListItem>
                  <ListItemText primary="User" secondary={wallet.userName} />
                </ListItem>
                <ListItem>
                  <ListItemText primary="Currency" secondary={wallet.currency} />
                </ListItem>
                <ListItem>
                  <ListItemText primary="Created" secondary={format(new Date(wallet.createdAt), 'PPpp')} />
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="Last Transaction"
                    secondary={wallet.lastTransactionAt ? format(new Date(wallet.lastTransactionAt), 'PPpp') : 'No transactions'}
                  />
                </ListItem>
              </List>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Statistics (All Time)
              </Typography>
              <Divider sx={{ mb: 2 }} />
              {stats && (
                <List dense>
                  <ListItem>
                    <ListItemText primary="Total Transactions" secondary={stats.totalTransactions.toLocaleString()} />
                  </ListItem>
                  <ListItem>
                    <ListItemText
                      primary="Total Credits"
                      secondary={`${wallet.currency} ${stats.totalCredits.toLocaleString()}`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText
                      primary="Total Debits"
                      secondary={`${wallet.currency} ${stats.totalDebits.toLocaleString()}`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText
                      primary="Avg Transaction Size"
                      secondary={`${wallet.currency} ${stats.avgTransactionSize.toLocaleString()}`}
                    />
                  </ListItem>
                </List>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Balance Chart */}
      {balanceHistory.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Balance History (30 Days)
            </Typography>
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={balanceHistory}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis />
                <RechartsTooltip />
                <Area type="monotone" dataKey="balance" stroke="#1976d2" fill="#1976d2" fillOpacity={0.3} />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      )}

      {/* Tabs */}
      <Card>
        <Tabs value={tabValue} onChange={(e, newValue) => setTabValue(newValue)}>
          <Tab label="Recent Transactions" />
          <Tab label="Actions" />
        </Tabs>

        {/* Transactions Tab */}
        <TabPanel value={tabValue} index={0}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Type</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell align="right">Balance After</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Date</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {transactions.map((tx) => (
                  <TableRow key={tx.id}>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {(tx.type === 'CREDIT' || tx.type === 'TRANSFER_IN') ? (
                          <TrendingUpIcon color="success" />
                        ) : (
                          <TrendingDownIcon color="error" />
                        )}
                        <Chip
                          label={tx.type}
                          size="small"
                          color={getTransactionTypeColor(tx.type) as any}
                        />
                      </Box>
                    </TableCell>
                    <TableCell>
                      {tx.description}
                      <Typography variant="caption" display="block" color="textSecondary">
                        Ref: {tx.reference}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography
                        color={(tx.type === 'CREDIT' || tx.type === 'TRANSFER_IN') ? 'success.main' : 'error.main'}
                        fontWeight="bold"
                      >
                        {(tx.type === 'CREDIT' || tx.type === 'TRANSFER_IN') ? '+' : '-'}
                        {wallet.currency} {tx.amount.toLocaleString()}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      {wallet.currency} {tx.balance.toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Chip label={tx.status} size="small" />
                    </TableCell>
                    <TableCell>{format(new Date(tx.createdAt), 'PPp')}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </TabPanel>

        {/* Actions Tab */}
        <TabPanel value={tabValue} index={1}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Wallet Status</Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
                    {wallet.status === 'ACTIVE' && (
                      <Button
                        variant="outlined"
                        color="error"
                        startIcon={<FreezeIcon />}
                        onClick={() => { setActionType('freeze'); setActionDialogOpen(true); }}
                      >
                        Freeze Wallet
                      </Button>
                    )}
                    {wallet.status === 'FROZEN' && (
                      <Button
                        variant="outlined"
                        color="success"
                        startIcon={<UnfreezeIcon />}
                        onClick={() => { setActionType('unfreeze'); setActionDialogOpen(true); }}
                      >
                        Unfreeze Wallet
                      </Button>
                    )}
                  </Box>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Adjustments</Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
                    <Button
                      variant="outlined"
                      startIcon={<MoneyIcon />}
                      onClick={() => { setActionType('adjust'); setAdjustAmount(0); setActionDialogOpen(true); }}
                    >
                      Adjust Balance
                    </Button>
                    <Button
                      variant="outlined"
                      startIcon={<LockIcon />}
                      onClick={() => { setActionType('setLimit'); setActionDialogOpen(true); }}
                    >
                      Set Daily Limit
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>
      </Card>

      {/* Action Dialog */}
      <Dialog open={actionDialogOpen} onClose={() => setActionDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {actionType === 'freeze' && 'Freeze Wallet'}
          {actionType === 'unfreeze' && 'Unfreeze Wallet'}
          {actionType === 'adjust' && 'Adjust Balance'}
          {actionType === 'setLimit' && 'Set Daily Limit'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            {actionType === 'adjust' && (
              <TextField
                fullWidth
                type="number"
                label="Adjustment Amount"
                value={adjustAmount}
                onChange={(e) => setAdjustAmount(parseFloat(e.target.value))}
                helperText="Positive for credit, negative for debit"
                sx={{ mb: 2 }}
              />
            )}
            {actionType === 'setLimit' && (
              <TextField
                fullWidth
                type="number"
                label="Daily Limit"
                value={limitAmount}
                onChange={(e) => setLimitAmount(parseFloat(e.target.value))}
                InputProps={{
                  startAdornment: <Typography sx={{ mr: 1 }}>{wallet.currency}</Typography>
                }}
                helperText={`Current: ${wallet.currency} ${wallet.dailyLimit.toLocaleString()}`}
                sx={{ mb: 2 }}
              />
            )}
            <TextField
              fullWidth
              multiline
              rows={4}
              label="Reason"
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder="Provide reason for this action..."
              required
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleAction}
            variant="contained"
            color={actionType === 'freeze' ? 'error' : 'primary'}
            disabled={!actionReason.trim() && actionType !== 'adjust'}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      {/* Snackbar */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default WalletDetailView;
