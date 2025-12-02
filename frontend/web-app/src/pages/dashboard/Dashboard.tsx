import React, { useEffect, useState } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  IconButton,
  Skeleton,
  useTheme,
  useMediaQuery,
  Button,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
} from '@mui/material';
import WalletIcon from '@mui/icons-material/AccountBalance';
import SendIcon from '@mui/icons-material/Send';
import RequestIcon from '@mui/icons-material/RequestPage';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import RefreshIcon from '@mui/icons-material/Refresh';
import NotificationsIcon from '@mui/icons-material/Notifications';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import AddIcon from '@mui/icons-material/Add';;
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../hooks/redux';
import { fetchWalletBalance } from '../../store/slices/walletSlice';
import { fetchRecentTransactions } from '../../store/slices/transactionSlice';
import { fetchNotifications } from '../../store/slices/notificationSlice';
import { formatCurrency, formatDate } from '../../utils/formatters';
import QuickActions from '../../components/QuickActions';
import TransactionChart from '../../components/charts/TransactionChart';
import WalletCard from '../../components/wallet/WalletCard';

const Dashboard: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  
  const { user } = useAppSelector((state) => state.auth);
  const { balance, loading: walletLoading } = useAppSelector((state) => state.wallet);
  const { recentTransactions, loading: transactionLoading } = useAppSelector(
    (state) => state.transaction
  );
  const { unreadCount, notifications } = useAppSelector((state) => state.notification);
  
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    // Fetch dashboard data on mount
    dispatch(fetchWalletBalance());
    dispatch(fetchRecentTransactions({ limit: 5 }));
    dispatch(fetchNotifications({ limit: 3 }));
  }, [dispatch]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await Promise.all([
      dispatch(fetchWalletBalance()),
      dispatch(fetchRecentTransactions({ limit: 5 })),
      dispatch(fetchNotifications({ limit: 3 })),
    ]);
    setRefreshing(false);
  };

  const getTransactionIcon = (type: string) => {
    switch (type) {
      case 'CREDIT':
      case 'DEPOSIT':
        return <TrendingDownIcon color="success" />;
      case 'DEBIT':
      case 'WITHDRAWAL':
        return <TrendingUpIcon color="error" />;
      default:
        return <WalletIcon />;
    }
  };

  const getTransactionColor = (type: string) => {
    switch (type) {
      case 'CREDIT':
      case 'DEPOSIT':
        return theme.palette.success.main;
      case 'DEBIT':
      case 'WITHDRAWAL':
        return theme.palette.error.main;
      default:
        return theme.palette.text.primary;
    }
  };

  return (
    <Box sx={{ flexGrow: 1, p: { xs: 2, sm: 3 } }}>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Welcome back, {user?.firstName}!
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {formatDate(new Date())}
          </Typography>
        </Box>
        <Box>
          <IconButton 
            onClick={() => navigate('/notifications')} 
            sx={{ position: 'relative' }}
          >
            <NotificationsIcon />
            {unreadCount > 0 && (
              <Box
                sx={{
                  position: 'absolute',
                  top: 0,
                  right: 0,
                  width: 20,
                  height: 20,
                  borderRadius: '50%',
                  backgroundColor: 'error.main',
                  color: 'white',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 12,
                }}
              >
                {unreadCount}
              </Box>
            )}
          </IconButton>
          <IconButton onClick={handleRefresh} disabled={refreshing}>
            <RefreshIcon />
          </IconButton>
        </Box>
      </Box>

      <Grid container spacing={3}>
        {/* Wallet Balance Card */}
        <Grid item xs={12} md={8}>
          <WalletCard
            balance={balance}
            loading={walletLoading}
            onSend={() => navigate('/payments/send')}
            onRequest={() => navigate('/payments/request')}
            onAddMoney={() => navigate('/wallet/add-money')}
          />
        </Grid>

        {/* Quick Stats */}
        <Grid item xs={12} md={4}>
          <Grid container spacing={2}>
            <Grid item xs={6} md={12}>
              <Card>
                <CardContent>
                  <Typography color="text.secondary" gutterBottom>
                    This Month
                  </Typography>
                  <Typography variant="h5">
                    {formatCurrency(balance?.monthlySpent || 0)}
                  </Typography>
                  <Typography variant="body2" color="error">
                    Spent
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={6} md={12}>
              <Card>
                <CardContent>
                  <Typography color="text.secondary" gutterBottom>
                    This Month
                  </Typography>
                  <Typography variant="h5">
                    {formatCurrency(balance?.monthlyReceived || 0)}
                  </Typography>
                  <Typography variant="body2" color="success.main">
                    Received
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Grid>

        {/* Quick Actions */}
        <Grid item xs={12}>
          <QuickActions />
        </Grid>

        {/* Recent Transactions */}
        <Grid item xs={12} lg={7}>
          <Paper sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="h6">Recent Transactions</Typography>
              <Button size="small" onClick={() => navigate('/transactions')}>
                View All
              </Button>
            </Box>
            
            {transactionLoading ? (
              <Box>
                {[...Array(3)].map((_, index) => (
                  <Skeleton key={index} variant="rectangular" height={60} sx={{ mb: 1 }} />
                ))}
              </Box>
            ) : recentTransactions?.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 4 }}>
                <Typography color="text.secondary">No transactions yet</Typography>
                <Button
                  startIcon={<SendIcon />}
                  onClick={() => navigate('/payments/send')}
                  sx={{ mt: 2 }}
                >
                  Make your first payment
                </Button>
              </Box>
            ) : (
              <List>
                {recentTransactions?.map((transaction) => (
                  <ListItem
                    key={transaction.id}
                    button
                    onClick={() => navigate(`/transactions/${transaction.id}`)}
                  >
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: 'background.default' }}>
                        {getTransactionIcon(transaction.type)}
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={transaction.description}
                      secondary={formatDate(transaction.createdAt)}
                    />
                    <ListItemSecondaryAction>
                      <Typography
                        variant="body1"
                        sx={{
                          color: getTransactionColor(transaction.type),
                          fontWeight: 'medium',
                        }}
                      >
                        {transaction.type === 'CREDIT' ? '+' : '-'}
                        {formatCurrency(transaction.amount)}
                      </Typography>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            )}
          </Paper>
        </Grid>

        {/* Transaction Chart */}
        <Grid item xs={12} lg={5}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              Transaction Overview
            </Typography>
            <TransactionChart />
          </Paper>
        </Grid>

        {/* Recent Notifications */}
        <Grid item xs={12}>
          <Paper sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="h6">Recent Notifications</Typography>
              <Button size="small" onClick={() => navigate('/notifications')}>
                View All
              </Button>
            </Box>
            
            {notifications?.length === 0 ? (
              <Typography color="text.secondary" align="center" sx={{ py: 2 }}>
                No new notifications
              </Typography>
            ) : (
              <List>
                {notifications?.slice(0, 3).map((notification) => (
                  <ListItem key={notification.id}>
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: 'primary.main' }}>
                        <NotificationsIcon />
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={notification.title}
                      secondary={notification.message}
                    />
                    <ListItemSecondaryAction>
                      <Chip
                        label={notification.type}
                        size="small"
                        color={notification.read ? 'default' : 'primary'}
                      />
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default Dashboard;