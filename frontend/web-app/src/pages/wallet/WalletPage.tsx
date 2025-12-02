import React, { useState, useEffect } from 'react';
import {
  Container,
  Grid,
  Paper,
  Typography,
  Box,
  Button,
  Tab,
  Tabs,
  Card,
  CardContent,
  IconButton,
  Menu,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  FormControl,
  InputLabel,
  Alert,
  CircularProgress,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
  Chip,
  Divider,
} from '@mui/material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import HistoryIcon from '@mui/icons-material/History';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import SettingsIcon from '@mui/icons-material/Settings';
import LockIcon from '@mui/icons-material/Lock';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';;
import { useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { format } from 'date-fns';
import { walletService } from '@/services/walletService';
import { RootState } from '@/store/store';
import WalletCard from '@/components/wallet/WalletCard';
import TransactionChart from '@/components/charts/TransactionChart';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`wallet-tabpanel-${index}`}
      aria-labelledby={`wallet-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

const WalletPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);
  const [tabValue, setTabValue] = useState(0);
  const [wallets, setWallets] = useState<any[]>([]);
  const [selectedWallet, setSelectedWallet] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [createWalletDialog, setCreateWalletDialog] = useState(false);
  const [addMoneyDialog, setAddMoneyDialog] = useState(false);
  const [withdrawDialog, setWithdrawDialog] = useState(false);
  const [showBalance, setShowBalance] = useState(true);
  const [transactions, setTransactions] = useState<any[]>([]);
  const [limits, setLimits] = useState<any>(null);

  // Form states
  const [walletForm, setWalletForm] = useState({
    name: '',
    currency: 'USD',
    type: 'personal',
  });

  const [moneyForm, setMoneyForm] = useState({
    amount: '',
    paymentMethod: '',
    note: '',
  });

  useEffect(() => {
    fetchWallets();
  }, []);

  useEffect(() => {
    if (selectedWallet) {
      fetchTransactions();
      fetchLimits();
    }
  }, [selectedWallet]);

  const fetchWallets = async () => {
    try {
      setLoading(true);
      const response = await walletService.getWallets();
      setWallets(response);
      if (response.length > 0) {
        const primary = response.find((w: any) => w.isPrimary) || response[0];
        setSelectedWallet(primary);
      }
    } catch (error) {
      console.error('Failed to fetch wallets:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchTransactions = async () => {
    if (!selectedWallet) return;
    try {
      const response = await walletService.getTransactions(selectedWallet.id, {
        limit: 10,
      });
      setTransactions(response);
    } catch (error) {
      console.error('Failed to fetch transactions:', error);
    }
  };

  const fetchLimits = async () => {
    if (!selectedWallet) return;
    try {
      const response = await walletService.getWalletLimits(selectedWallet.id);
      setLimits(response);
    } catch (error) {
      console.error('Failed to fetch limits:', error);
    }
  };

  const handleCreateWallet = async () => {
    try {
      const response = await walletService.createWallet(walletForm);
      setWallets([...wallets, response]);
      setCreateWalletDialog(false);
      setWalletForm({ name: '', currency: 'USD', type: 'personal' });
    } catch (error) {
      console.error('Failed to create wallet:', error);
    }
  };

  const handleAddMoney = async () => {
    try {
      await walletService.addMoney(selectedWallet.id, moneyForm);
      fetchWallets();
      fetchTransactions();
      setAddMoneyDialog(false);
      setMoneyForm({ amount: '', paymentMethod: '', note: '' });
    } catch (error) {
      console.error('Failed to add money:', error);
    }
  };

  const handleWithdraw = async () => {
    try {
      await walletService.withdrawMoney(selectedWallet.id, moneyForm);
      fetchWallets();
      fetchTransactions();
      setWithdrawDialog(false);
      setMoneyForm({ amount: '', paymentMethod: '', note: '' });
    } catch (error) {
      console.error('Failed to withdraw money:', error);
    }
  };

  const handleSetPrimary = async (walletId: string) => {
    try {
      await walletService.setPrimaryWallet(walletId);
      fetchWallets();
    } catch (error) {
      console.error('Failed to set primary wallet:', error);
    }
  };

  const handleExportTransactions = async (format: 'csv' | 'pdf') => {
    try {
      const blob = await walletService.exportTransactions(selectedWallet.id, format);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `transactions.${format}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to export transactions:', error);
    }
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="80vh">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Grid container spacing={3}>
        {/* Header */}
        <Grid item xs={12}>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="h4" gutterBottom>
              My Wallets
            </Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateWalletDialog(true)}
            >
              Create Wallet
            </Button>
          </Box>
        </Grid>

        {/* Wallet Cards */}
        <Grid item xs={12}>
          <Grid container spacing={2}>
            {wallets.map((wallet) => (
              <Grid item xs={12} md={4} key={wallet.id}>
                <WalletCard
                  wallet={wallet}
                  selected={selectedWallet?.id === wallet.id}
                  onSelect={() => setSelectedWallet(wallet)}
                  onSetPrimary={() => handleSetPrimary(wallet.id)}
                  showBalance={showBalance}
                />
              </Grid>
            ))}
          </Grid>
        </Grid>

        {/* Balance Visibility Toggle */}
        <Grid item xs={12}>
          <Box display="flex" alignItems="center" gap={1}>
            <IconButton onClick={() => setShowBalance(!showBalance)}>
              {showBalance ? <VisibilityOff /> : <Visibility />}
            </IconButton>
            <Typography variant="body2" color="text.secondary">
              {showBalance ? 'Hide' : 'Show'} balances
            </Typography>
          </Box>
        </Grid>

        {/* Tabs */}
        <Grid item xs={12}>
          <Paper sx={{ width: '100%' }}>
            <Tabs
              value={tabValue}
              onChange={(e, newValue) => setTabValue(newValue)}
              aria-label="wallet tabs"
            >
              <Tab label="Overview" />
              <Tab label="Transactions" />
              <Tab label="Analytics" />
              <Tab label="Limits & Settings" />
            </Tabs>

            <TabPanel value={tabValue} index={0}>
              {/* Quick Actions */}
              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
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
                            startIcon={<Upload />}
                            onClick={() => setAddMoneyDialog(true)}
                          >
                            Add Money
                          </Button>
                        </Grid>
                        <Grid item xs={6}>
                          <Button
                            fullWidth
                            variant="outlined"
                            startIcon={<Download />}
                            onClick={() => setWithdrawDialog(true)}
                          >
                            Withdraw
                          </Button>
                        </Grid>
                        <Grid item xs={6}>
                          <Button
                            fullWidth
                            variant="outlined"
                            startIcon={<SwapHoriz />}
                            onClick={() => navigate('/payment')}
                          >
                            Transfer
                          </Button>
                        </Grid>
                        <Grid item xs={6}>
                          <Button
                            fullWidth
                            variant="outlined"
                            startIcon={<History />}
                            onClick={() => setTabValue(1)}
                          >
                            History
                          </Button>
                        </Grid>
                      </Grid>
                    </CardContent>
                  </Card>
                </Grid>

                <Grid item xs={12} md={6}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        Recent Activity
                      </Typography>
                      <List>
                        {transactions.slice(0, 3).map((tx) => (
                          <ListItem key={tx.id}>
                            <ListItemIcon>
                              {tx.type === 'credit' ? (
                                <TrendingUp color="success" />
                              ) : (
                                <TrendingUp color="error" sx={{ transform: 'rotate(180deg)' }} />
                              )}
                            </ListItemIcon>
                            <ListItemText
                              primary={tx.description}
                              secondary={format(new Date(tx.createdAt), 'MMM dd, yyyy')}
                            />
                            <ListItemSecondaryAction>
                              <Typography
                                variant="body2"
                                color={tx.type === 'credit' ? 'success.main' : 'error.main'}
                              >
                                {tx.type === 'credit' ? '+' : '-'}${tx.amount}
                              </Typography>
                            </ListItemSecondaryAction>
                          </ListItem>
                        ))}
                      </List>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </TabPanel>

            <TabPanel value={tabValue} index={1}>
              {/* Transactions */}
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">Transaction History</Typography>
                <Box>
                  <Button onClick={() => handleExportTransactions('csv')}>Export CSV</Button>
                  <Button onClick={() => handleExportTransactions('pdf')}>Export PDF</Button>
                </Box>
              </Box>
              <List>
                {transactions.map((tx) => (
                  <React.Fragment key={tx.id}>
                    <ListItem>
                      <ListItemIcon>
                        {tx.type === 'credit' ? (
                          <TrendingUp color="success" />
                        ) : (
                          <TrendingUp color="error" sx={{ transform: 'rotate(180deg)' }} />
                        )}
                      </ListItemIcon>
                      <ListItemText
                        primary={tx.description}
                        secondary={
                          <Box>
                            <Typography variant="caption" display="block">
                              {format(new Date(tx.createdAt), 'MMM dd, yyyy hh:mm a')}
                            </Typography>
                            <Typography variant="caption" display="block">
                              Reference: {tx.reference}
                            </Typography>
                          </Box>
                        }
                      />
                      <ListItemSecondaryAction>
                        <Box textAlign="right">
                          <Typography
                            variant="body1"
                            color={tx.type === 'credit' ? 'success.main' : 'error.main'}
                          >
                            {tx.type === 'credit' ? '+' : '-'}${tx.amount}
                          </Typography>
                          <Chip
                            label={tx.status}
                            size="small"
                            color={tx.status === 'completed' ? 'success' : 'default'}
                          />
                        </Box>
                      </ListItemSecondaryAction>
                    </ListItem>
                    <Divider />
                  </React.Fragment>
                ))}
              </List>
            </TabPanel>

            <TabPanel value={tabValue} index={2}>
              {/* Analytics */}
              <TransactionChart />
            </TabPanel>

            <TabPanel value={tabValue} index={3}>
              {/* Limits & Settings */}
              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        Transaction Limits
                      </Typography>
                      {limits && (
                        <List>
                          <ListItem>
                            <ListItemText
                              primary="Daily Limit"
                              secondary={`$${limits.daily.used} / $${limits.daily.limit}`}
                            />
                          </ListItem>
                          <ListItem>
                            <ListItemText
                              primary="Monthly Limit"
                              secondary={`$${limits.monthly.used} / $${limits.monthly.limit}`}
                            />
                          </ListItem>
                          <ListItem>
                            <ListItemText
                              primary="Per Transaction"
                              secondary={`Max: $${limits.perTransaction}`}
                            />
                          </ListItem>
                        </List>
                      )}
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom>
                        Wallet Settings
                      </Typography>
                      <List>
                        <ListItem>
                          <ListItemText primary="Notifications" secondary="Enabled for all transactions" />
                          <ListItemSecondaryAction>
                            <IconButton edge="end">
                              <Settings />
                            </IconButton>
                          </ListItemSecondaryAction>
                        </ListItem>
                        <ListItem>
                          <ListItemText primary="Security" secondary="2FA enabled" />
                          <ListItemSecondaryAction>
                            <IconButton edge="end">
                              <Lock />
                            </IconButton>
                          </ListItemSecondaryAction>
                        </ListItem>
                      </List>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </TabPanel>
          </Paper>
        </Grid>
      </Grid>

      {/* Create Wallet Dialog */}
      <Dialog open={createWalletDialog} onClose={() => setCreateWalletDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create New Wallet</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Wallet Name"
                value={walletForm.name}
                onChange={(e) => setWalletForm({ ...walletForm, name: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Currency</InputLabel>
                <Select
                  value={walletForm.currency}
                  onChange={(e) => setWalletForm({ ...walletForm, currency: e.target.value })}
                >
                  <MenuItem value="USD">USD - US Dollar</MenuItem>
                  <MenuItem value="EUR">EUR - Euro</MenuItem>
                  <MenuItem value="GBP">GBP - British Pound</MenuItem>
                  <MenuItem value="NGN">NGN - Nigerian Naira</MenuItem>
                  <MenuItem value="KES">KES - Kenyan Shilling</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Wallet Type</InputLabel>
                <Select
                  value={walletForm.type}
                  onChange={(e) => setWalletForm({ ...walletForm, type: e.target.value })}
                >
                  <MenuItem value="personal">Personal</MenuItem>
                  <MenuItem value="business">Business</MenuItem>
                  <MenuItem value="savings">Savings</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateWalletDialog(false)}>Cancel</Button>
          <Button onClick={handleCreateWallet} variant="contained" disabled={!walletForm.name}>
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {/* Add Money Dialog */}
      <Dialog open={addMoneyDialog} onClose={() => setAddMoneyDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Money to Wallet</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Amount"
                type="number"
                value={moneyForm.amount}
                onChange={(e) => setMoneyForm({ ...moneyForm, amount: e.target.value })}
                InputProps={{
                  startAdornment: '$',
                }}
              />
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Payment Method</InputLabel>
                <Select
                  value={moneyForm.paymentMethod}
                  onChange={(e) => setMoneyForm({ ...moneyForm, paymentMethod: e.target.value })}
                >
                  <MenuItem value="bank_transfer">Bank Transfer</MenuItem>
                  <MenuItem value="debit_card">Debit Card</MenuItem>
                  <MenuItem value="credit_card">Credit Card</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Note (Optional)"
                multiline
                rows={2}
                value={moneyForm.note}
                onChange={(e) => setMoneyForm({ ...moneyForm, note: e.target.value })}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddMoneyDialog(false)}>Cancel</Button>
          <Button
            onClick={handleAddMoney}
            variant="contained"
            disabled={!moneyForm.amount || !moneyForm.paymentMethod}
          >
            Add Money
          </Button>
        </DialogActions>
      </Dialog>

      {/* Withdraw Dialog */}
      <Dialog open={withdrawDialog} onClose={() => setWithdrawDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Withdraw Money</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Amount"
                type="number"
                value={moneyForm.amount}
                onChange={(e) => setMoneyForm({ ...moneyForm, amount: e.target.value })}
                InputProps={{
                  startAdornment: '$',
                }}
              />
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Withdraw To</InputLabel>
                <Select
                  value={moneyForm.paymentMethod}
                  onChange={(e) => setMoneyForm({ ...moneyForm, paymentMethod: e.target.value })}
                >
                  <MenuItem value="bank_account">Bank Account</MenuItem>
                  <MenuItem value="debit_card">Debit Card</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Note (Optional)"
                multiline
                rows={2}
                value={moneyForm.note}
                onChange={(e) => setMoneyForm({ ...moneyForm, note: e.target.value })}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setWithdrawDialog(false)}>Cancel</Button>
          <Button
            onClick={handleWithdraw}
            variant="contained"
            disabled={!moneyForm.amount || !moneyForm.paymentMethod}
          >
            Withdraw
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default WalletPage;