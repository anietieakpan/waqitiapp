import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  CardActions,
  Typography,
  Button,
  IconButton,
  Paper,
  Avatar,
  Chip,
  Menu,
  MenuItem,
  Tooltip,
  Alert,
  Skeleton,
  useTheme,
  alpha,
  LinearProgress,
  Badge,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
  Collapse,
  useMediaQuery,
} from '@mui/material';
import WalletIcon from '@mui/icons-material/AccountBalanceWallet';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import SecurityIcon from '@mui/icons-material/Security';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import SettingsIcon from '@mui/icons-material/Settings';
import SpeedIcon from '@mui/icons-material/Speed';
import SendIcon from '@mui/icons-material/Send';
import ReceiveIcon from '@mui/icons-material/CallReceived';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import AddIcon from '@mui/icons-material/Add';
import QrCodeIcon from '@mui/icons-material/QrCode';
import ReceiptIcon from '@mui/icons-material/Receipt';
import HistoryIcon from '@mui/icons-material/History';
import TimelineIcon from '@mui/icons-material/Timeline';
import AutoGraphIcon from '@mui/icons-material/AutoGraph';
import ShieldIcon from '@mui/icons-material/Shield';
import TollIcon from '@mui/icons-material/Toll';;
import { format, parseISO, differenceInDays, startOfMonth, endOfMonth } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../hooks/redux';
import { formatCurrency, formatPercentage, formatNumber } from '../../utils/formatters';
import { calculatePercentageChange } from '../../utils/calculations';
import WalletBalanceCard from './WalletBalanceCard';
import CurrencyBreakdown from './CurrencyBreakdown';
import RecentActivity from './RecentActivity';
import SpendingInsights from './SpendingInsights';
import SecurityStatus from './SecurityStatus';
import QuickTransfer from './QuickTransfer';
import GoalProgress from './GoalProgress';
import { EnhancedWalletBalance, Currency, TransactionType, SecuritySettings, Goal, Contact } from '../../types/wallet';
import { 
  loadBalances, 
  loadTransactions, 
  loadLimits, 
  loadSecuritySettings, 
  loadGoals, 
  loadContacts 
} from '../../store/slices/walletSlice';

interface WalletDashboardProps {
  onRefresh?: () => void;
}

const WalletDashboard: React.FC<WalletDashboardProps> = ({ onRefresh }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  
  // Redux state
  const { user } = useAppSelector((state) => state.auth);
  const { 
    balances, 
    totalBalance, 
    pendingBalance,
    transactions,
    limits,
    securitySettings,
    goals,
    contacts,
    isLoading,
    error 
  } = useAppSelector((state) => state.wallet);
  
  // Local state
  const [showBalances, setShowBalances] = useState(() => {
    return localStorage.getItem('showWalletBalances') !== 'false';
  });
  const [selectedCurrency, setSelectedCurrency] = useState<Currency>('USD');
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [showInsights, setShowInsights] = useState(false);
  const [speedDialOpen, setSpeedDialOpen] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  
  // Analytics state
  const [monthlyChange, setMonthlyChange] = useState(0);
  const [weeklyChange, setWeeklyChange] = useState(0);
  const [spendingRate, setSpendingRate] = useState(0);
  
  useEffect(() => {
    loadWalletData();
    calculateAnalytics();
  }, []);

  const loadWalletData = async () => {
    try {
      setRefreshing(true);
      // Dispatch actions to load wallet data
      await Promise.all([
        dispatch(loadBalances()),
        dispatch(loadTransactions()),
        dispatch(loadLimits()),
        dispatch(loadSecuritySettings()),
        dispatch(loadGoals()),
        dispatch(loadContacts()),
      ]);
      onRefresh?.();
    } catch (error) {
      console.error('Failed to load wallet data:', error);
    } finally {
      setRefreshing(false);
    }
  };

  const calculateAnalytics = () => {
    if (!transactions || transactions.length === 0) return;
    
    // Calculate monthly change
    const currentMonth = startOfMonth(new Date());
    const lastMonth = startOfMonth(new Date(currentMonth.getTime() - 30 * 24 * 60 * 60 * 1000));
    
    const currentMonthTotal = transactions
      .filter(tx => new Date(tx.createdAt) >= currentMonth)
      .reduce((sum, tx) => sum + (tx.type === TransactionType.CREDIT ? tx.amount : -tx.amount), 0);
    
    const lastMonthTotal = transactions
      .filter(tx => new Date(tx.createdAt) >= lastMonth && new Date(tx.createdAt) < currentMonth)
      .reduce((sum, tx) => sum + (tx.type === TransactionType.CREDIT ? tx.amount : -tx.amount), 0);
    
    setMonthlyChange(calculatePercentageChange(lastMonthTotal, currentMonthTotal));
    
    // Calculate weekly change and spending rate
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const weeklySpending = transactions
      .filter(tx => new Date(tx.createdAt) >= weekAgo && tx.type === TransactionType.DEBIT)
      .reduce((sum, tx) => sum + tx.amount, 0);
    
    setSpendingRate(weeklySpending / 7);
  };

  const handleToggleBalanceVisibility = () => {
    setShowBalances(!showBalances);
    localStorage.setItem('showWalletBalances', String(!showBalances));
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setMenuAnchor(event.currentTarget);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const getWalletStatus = () => {
    if (!securitySettings) return { level: 'basic', color: 'warning' };
    
    const score = 
      (securitySettings.mfaEnabled ? 30 : 0) +
      (securitySettings.biometricEnabled ? 20 : 0) +
      (securitySettings.transactionNotifications ? 15 : 0) +
      (securitySettings.loginNotifications ? 15 : 0) +
      (securitySettings.withdrawalLimits ? 20 : 0);
    
    if (score >= 80) return { level: 'excellent', color: 'success' };
    if (score >= 60) return { level: 'good', color: 'info' };
    if (score >= 40) return { level: 'fair', color: 'warning' };
    return { level: 'poor', color: 'error' };
  };

  const walletStatus = getWalletStatus();

  const speedDialActions = [
    { icon: <SendIcon />, name: 'Send Money', action: () => navigate('/send') },
    { icon: <ReceiveIcon />, name: 'Receive', action: () => navigate('/receive') },
    { icon: <SwapIcon />, name: 'Exchange', action: () => navigate('/exchange') },
    { icon: <AddIcon />, name: 'Add Funds', action: () => navigate('/add-funds') },
    { icon: <QrCodeIcon />, name: 'Scan QR', action: () => navigate('/scan') },
    { icon: <HistoryIcon />, name: 'History', action: () => navigate('/transactions') },
  ];

  const renderHeader = () => (
    <Box sx={{ mb: 3 }}>
      <Grid container alignItems="center" justifyContent="space-between">
        <Grid item>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 1 }}>
            My Wallet
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chip
              icon={<ShieldIcon />}
              label={`Security: ${walletStatus.level}`}
              color={walletStatus.color as any}
              size="small"
            />
            <Chip
              icon={<CheckCircleIcon />}
              label="Verified"
              color="success"
              size="small"
              variant="outlined"
            />
            {limits?.dailyTransferLimit && (
              <Chip
                icon={<TollIcon />}
                label={`Daily: ${formatCurrency(limits.dailyTransferLimit - limits.currentDailyUsage)} left`}
                size="small"
                variant="outlined"
              />
            )}
          </Box>
        </Grid>
        <Grid item>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Tooltip title="Toggle balance visibility">
              <IconButton onClick={handleToggleBalanceVisibility}>
                {showBalances ? <VisibilityIcon /> : <VisibilityOffIcon />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Refresh">
              <IconButton onClick={loadWalletData} disabled={refreshing}>
                <AutoGraphIcon className={refreshing ? 'rotating' : ''} />
              </IconButton>
            </Tooltip>
            <IconButton onClick={handleMenuOpen}>
              <MoreVertIcon />
            </IconButton>
          </Box>
        </Grid>
      </Grid>
      
      {refreshing && <LinearProgress sx={{ mt: 2 }} />}
    </Box>
  );

  const renderTotalBalance = () => {
    const availableBalance = balances.reduce((sum, balance) => sum + balance.available, 0);
    const pendingTotal = balances.reduce((sum, balance) => sum + balance.pending, 0);

    return (
      <Card
        sx={{
          background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.primary.dark} 100%)`,
          color: 'white',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <CardContent sx={{ pb: 2 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Box>
              <Typography variant="h6" sx={{ opacity: 0.9, mb: 1 }}>
                Total Balance
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 700, mb: 2 }}>
                {showBalances ? formatCurrency(totalBalance) : '••••••'}
              </Typography>
              
              <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                <Box>
                  <Typography variant="caption" sx={{ opacity: 0.8 }}>
                    Available
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 500 }}>
                    {showBalances ? formatCurrency(availableBalance) : '••••'}
                  </Typography>
                </Box>
                {pendingTotal > 0 && (
                  <Box>
                    <Typography variant="caption" sx={{ opacity: 0.8 }}>
                      Pending
                    </Typography>
                    <Typography variant="body1" sx={{ fontWeight: 500, color: theme.palette.warning.light }}>
                      {showBalances ? formatCurrency(pendingTotal) : '••••'}
                    </Typography>
                  </Box>
                )}
              </Box>
            </Box>
            
            <Avatar
              sx={{
                bgcolor: alpha(theme.palette.common.white, 0.2),
                width: 80,
                height: 80,
              }}
            >
              <WalletIcon sx={{ fontSize: 48 }} />
            </Avatar>
          </Box>
          
          <Box sx={{ display: 'flex', gap: 2, mt: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              {monthlyChange >= 0 ? (
                <TrendingUpIcon sx={{ color: theme.palette.success.light }} />
              ) : (
                <TrendingDownIcon sx={{ color: theme.palette.error.light }} />
              )}
              <Typography variant="body2">
                {formatPercentage(Math.abs(monthlyChange))} this month
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <SpeedIcon sx={{ fontSize: 20 }} />
              <Typography variant="body2">
                {formatCurrency(spendingRate)}/day avg
              </Typography>
            </Box>
          </Box>
        </CardContent>
        
        <CardActions sx={{ px: 2, pb: 2 }}>
          <Button
            fullWidth
            variant="contained"
            sx={{
              bgcolor: alpha(theme.palette.common.white, 0.2),
              '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.3) },
            }}
            startIcon={<TimelineIcon />}
            onClick={() => setShowInsights(!showInsights)}
          >
            View Insights
          </Button>
        </CardActions>
      </Card>
    );
  };

  const renderQuickActions = () => (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        Quick Actions
      </Typography>
      <Grid container spacing={2}>
        <Grid item xs={6} sm={3}>
          <Button
            fullWidth
            variant="outlined"
            sx={{ py: 2, flexDirection: 'column', gap: 1 }}
            onClick={() => navigate('/send')}
          >
            <SendIcon />
            <Typography variant="caption">Send</Typography>
          </Button>
        </Grid>
        <Grid item xs={6} sm={3}>
          <Button
            fullWidth
            variant="outlined"
            sx={{ py: 2, flexDirection: 'column', gap: 1 }}
            onClick={() => navigate('/receive')}
          >
            <ReceiveIcon />
            <Typography variant="caption">Receive</Typography>
          </Button>
        </Grid>
        <Grid item xs={6} sm={3}>
          <Button
            fullWidth
            variant="outlined"
            sx={{ py: 2, flexDirection: 'column', gap: 1 }}
            onClick={() => navigate('/add-funds')}
          >
            <AddIcon />
            <Typography variant="caption">Add Funds</Typography>
          </Button>
        </Grid>
        <Grid item xs={6} sm={3}>
          <Button
            fullWidth
            variant="outlined"
            sx={{ py: 2, flexDirection: 'column', gap: 1 }}
            onClick={() => navigate('/bills')}
          >
            <ReceiptIcon />
            <Typography variant="caption">Pay Bills</Typography>
          </Button>
        </Grid>
      </Grid>
    </Paper>
  );

  if (isLoading && !refreshing) {
    return (
      <Box>
        <Skeleton variant="text" width={200} height={40} sx={{ mb: 2 }} />
        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 2 }} />
          </Grid>
          <Grid item xs={12} md={4}>
            <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 2 }} />
          </Grid>
        </Grid>
      </Box>
    );
  }

  if (error) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={loadWalletData}>
            Retry
          </Button>
        }
      >
        {error}
      </Alert>
    );
  }

  return (
    <Box>
      {renderHeader()}
      
      <Grid container spacing={3}>
        <Grid item xs={12} lg={8}>
          <Grid container spacing={3}>
            <Grid item xs={12}>
              {renderTotalBalance()}
            </Grid>
            
            <Grid item xs={12}>
              <Collapse in={showInsights} timeout="auto">
                <SpendingInsights transactions={transactions} />
              </Collapse>
            </Grid>
            
            <Grid item xs={12}>
              <CurrencyBreakdown
                balances={balances}
                showValues={showBalances}
                onCurrencySelect={setSelectedCurrency}
              />
            </Grid>
            
            <Grid item xs={12}>
              {renderQuickActions()}
            </Grid>
            
            <Grid item xs={12}>
              <RecentActivity
                transactions={transactions}
                showValues={showBalances}
                onViewAll={() => navigate('/transactions')}
              />
            </Grid>
          </Grid>
        </Grid>
        
        <Grid item xs={12} lg={4}>
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <SecurityStatus
                settings={securitySettings}
                onManage={() => navigate('/settings/security')}
              />
            </Grid>
            
            <Grid item xs={12}>
              <QuickTransfer
                contacts={contacts}
                onTransfer={(recipient, amount) => {
                  navigate('/send', { state: { recipient, amount } });
                }}
              />
            </Grid>
            
            <Grid item xs={12}>
              <GoalProgress
                goals={goals}
                onManage={() => navigate('/goals')}
              />
            </Grid>
            
            <Grid item xs={12}>
              <Paper sx={{ p: 2 }}>
                <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
                  Payment Methods
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  <Button
                    fullWidth
                    variant="outlined"
                    onClick={() => navigate('/payment-methods')}
                  >
                    Manage Cards & Banks
                  </Button>
                </Box>
              </Paper>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      
      {/* Speed Dial for mobile */}
      {isMobile && (
        <SpeedDial
          ariaLabel="Wallet actions"
          sx={{ position: 'fixed', bottom: 16, right: 16 }}
          icon={<SpeedDialIcon />}
          open={speedDialOpen}
          onOpen={() => setSpeedDialOpen(true)}
          onClose={() => setSpeedDialOpen(false)}
        >
          {speedDialActions.map((action) => (
            <SpeedDialAction
              key={action.name}
              icon={action.icon}
              tooltipTitle={action.name}
              onClick={() => {
                action.action();
                setSpeedDialOpen(false);
              }}
            />
          ))}
        </SpeedDial>
      )}
      
      {/* Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => { navigate('/wallet/settings'); handleMenuClose(); }}>
          <SettingsIcon sx={{ mr: 1 }} /> Wallet Settings
        </MenuItem>
        <MenuItem onClick={() => { navigate('/wallet/statements'); handleMenuClose(); }}>
          <ReceiptIcon sx={{ mr: 1 }} /> Statements
        </MenuItem>
        <MenuItem onClick={() => { navigate('/wallet/export'); handleMenuClose(); }}>
          <TimelineIcon sx={{ mr: 1 }} /> Export Data
        </MenuItem>
      </Menu>
      
      <style jsx>{`
        @keyframes rotate {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .rotating {
          animation: rotate 1s linear infinite;
        }
      `}</style>
    </Box>
  );
};

export default WalletDashboard;