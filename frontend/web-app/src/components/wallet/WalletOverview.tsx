import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  Avatar,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  LinearProgress,
  Divider,
  Stack,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
} from '@mui/material';
import WalletIcon from '@mui/icons-material/AccountBalanceWallet';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import ReceiptIcon from '@mui/icons-material/Receipt';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SecurityIcon from '@mui/icons-material/Security';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';;
import { useDispatch, useSelector } from 'react-redux';
import { RootState } from '../../store/store';
import { fetchWallets, fetchWalletBalance } from '../../store/slices/walletSlice';
import { formatCurrency } from '../../utils/formatters';
import { WalletBalance } from '../../types/wallet';

interface WalletOverviewProps {
  onAddMoney: () => void;
  onWithdraw: () => void;
  onTransfer: () => void;
  onSendMoney: () => void;
}

export const WalletOverview: React.FC<WalletOverviewProps> = ({
  onAddMoney,
  onWithdraw,
  onTransfer,
  onSendMoney,
}) => {
  const dispatch = useDispatch();
  const { activeWallet, balance, loading, recentTransactions } = useSelector(
    (state: RootState) => state.wallet
  );
  
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [balanceVisible, setBalanceVisible] = useState(true);

  useEffect(() => {
    dispatch(fetchWallets() as any);
  }, [dispatch]);

  useEffect(() => {
    if (activeWallet) {
      dispatch(fetchWalletBalance(activeWallet.id) as any);
    }
  }, [dispatch, activeWallet]);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const getBalanceDisplay = (amount: number) => {
    return balanceVisible ? formatCurrency(amount) : '••••••';
  };

  const getSpendingProgress = () => {
    if (!balance || !activeWallet) return 0;
    const spent = (balance.totalBalance || 0) - (balance.availableBalance || 0);
    const limit = activeWallet.dailyLimit || 1000;
    return Math.min((spent / limit) * 100, 100);
  };

  const quickActions = [
    {
      label: 'Add Money',
      icon: <AddIcon />,
      onClick: onAddMoney,
      color: 'primary' as const,
    },
    {
      label: 'Send Money',
      icon: <ArrowUpwardIcon />,
      onClick: onSendMoney,
      color: 'secondary' as const,
    },
    {
      label: 'Withdraw',
      icon: <ArrowDownwardIcon />,
      onClick: onWithdraw,
      color: 'info' as const,
    },
    {
      label: 'Transfer',
      icon: <AttachMoneyIcon />,
      onClick: onTransfer,
      color: 'success' as const,
    },
  ];

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <LinearProgress />
      </Box>
    );
  }

  if (!activeWallet) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <WalletIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" gutterBottom>
          No Active Wallet
        </Typography>
        <Typography variant="body2" color="text.secondary" paragraph>
          Create a wallet to start managing your money
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />}>
          Create Wallet
        </Button>
      </Paper>
    );
  }

  return (
    <Box>
      {/* Main Balance Card */}
      <Card sx={{ mb: 3, background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
        <CardContent sx={{ color: 'white' }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="caption" sx={{ opacity: 0.8 }}>
                Available Balance
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                <Typography variant="h4" sx={{ mr: 1 }}>
                  {getBalanceDisplay(balance?.availableBalance || 0)}
                </Typography>
                <IconButton
                  size="small"
                  onClick={() => setBalanceVisible(!balanceVisible)}
                  sx={{ color: 'white' }}
                >
                  {balanceVisible ? <VisibilityIcon /> : <VisibilityOffIcon />}
                </IconButton>
              </Box>
              <Typography variant="caption" sx={{ opacity: 0.8 }}>
                {activeWallet.currency}
              </Typography>
            </Box>
            <Box sx={{ textAlign: 'right' }}>
              <IconButton
                size="small"
                onClick={handleMenuOpen}
                sx={{ color: 'white' }}
              >
                <MoreVertIcon />
              </IconButton>
              <Typography variant="caption" sx={{ display: 'block', opacity: 0.8 }}>
                {activeWallet.name || 'Primary Wallet'}
              </Typography>
              <Chip
                label={activeWallet.status}
                size="small"
                sx={{
                  backgroundColor: 'rgba(255, 255, 255, 0.2)',
                  color: 'white',
                  fontWeight: 'bold',
                }}
              />
            </Box>
          </Box>

          {/* Balance Breakdown */}
          <Box sx={{ mt: 3 }}>
            <Grid container spacing={2}>
              <Grid item xs={4}>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                  Total Balance
                </Typography>
                <Typography variant="h6">
                  {getBalanceDisplay(balance?.totalBalance || 0)}
                </Typography>
              </Grid>
              <Grid item xs={4}>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                  Pending
                </Typography>
                <Typography variant="h6">
                  {getBalanceDisplay(balance?.pendingBalance || 0)}
                </Typography>
              </Grid>
              <Grid item xs={4}>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                  Frozen
                </Typography>
                <Typography variant="h6">
                  {getBalanceDisplay(balance?.frozenBalance || 0)}
                </Typography>
              </Grid>
            </Grid>
          </Box>

          {/* Daily Spending Progress */}
          <Box sx={{ mt: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
              <Typography variant="caption" sx={{ opacity: 0.8 }}>
                Daily Spending
              </Typography>
              <Typography variant="caption" sx={{ opacity: 0.8 }}>
                {Math.round(getSpendingProgress())}%
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={getSpendingProgress()}
              sx={{
                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                '& .MuiLinearProgress-bar': {
                  backgroundColor: 'white',
                },
              }}
            />
          </Box>
        </CardContent>
      </Card>

      {/* Quick Actions */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {quickActions.map((action) => (
          <Grid item xs={3} key={action.label}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={action.icon}
              onClick={action.onClick}
              sx={{
                py: 1.5,
                flexDirection: 'column',
                '& .MuiButton-startIcon': {
                  margin: 0,
                  mb: 0.5,
                },
              }}
            >
              <Typography variant="caption">{action.label}</Typography>
            </Button>
          </Grid>
        ))}
      </Grid>

      {/* Recent Activity */}
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">Recent Activity</Typography>
            <Button size="small" color="primary">
              View All
            </Button>
          </Box>
          
          {recentTransactions && recentTransactions.length > 0 ? (
            <List>
              {recentTransactions.slice(0, 5).map((transaction, index) => (
                <React.Fragment key={transaction.id}>
                  <ListItem>
                    <ListItemIcon>
                      <Avatar sx={{ bgcolor: getTransactionColor(transaction.type) }}>
                        {getTransactionIcon(transaction.type)}
                      </Avatar>
                    </ListItemIcon>
                    <ListItemText
                      primary={transaction.description || getTransactionDescription(transaction.type)}
                      secondary={new Date(transaction.createdAt).toLocaleDateString()}
                    />
                    <ListItemSecondaryAction>
                      <Typography
                        variant="body2"
                        color={transaction.type === 'CREDIT' ? 'success.main' : 'error.main'}
                      >
                        {transaction.type === 'CREDIT' ? '+' : '-'}
                        {formatCurrency(transaction.amount)}
                      </Typography>
                    </ListItemSecondaryAction>
                  </ListItem>
                  {index < recentTransactions.length - 1 && <Divider />}
                </React.Fragment>
              ))}
            </List>
          ) : (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <ReceiptIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
              <Typography variant="body2" color="text.secondary">
                No recent transactions
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Wallet Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>
          <SecurityIcon sx={{ mr: 1 }} />
          Security Settings
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <ScheduleIcon sx={{ mr: 1 }} />
          Auto-Reload
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <ReceiptIcon sx={{ mr: 1 }} />
          Transaction History
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleMenuClose} sx={{ color: 'error.main' }}>
          Freeze Wallet
        </MenuItem>
      </Menu>
    </Box>
  );
};

// Helper functions
const getTransactionColor = (type: string) => {
  switch (type) {
    case 'CREDIT':
      return 'success.main';
    case 'DEBIT':
      return 'error.main';
    default:
      return 'primary.main';
  }
};

const getTransactionIcon = (type: string) => {
  switch (type) {
    case 'CREDIT':
      return <TrendingUpIcon />;
    case 'DEBIT':
      return <TrendingDownIcon />;
    default:
      return <AttachMoneyIcon />;
  }
};

const getTransactionDescription = (type: string) => {
  switch (type) {
    case 'CREDIT':
      return 'Money received';
    case 'DEBIT':
      return 'Money sent';
    default:
      return 'Transaction';
  }
};

export default WalletOverview;