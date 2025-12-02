import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Button,
  Avatar,
  Divider,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Tab,
  Tabs,
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
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  IconButton,
  Tooltip,
  CircularProgress,
  Snackbar
} from '@mui/material';
import {
  Person as PersonIcon,
  Email as EmailIcon,
  Phone as PhoneIcon,
  LocationOn as LocationIcon,
  CalendarToday as CalendarIcon,
  Security as SecurityIcon,
  AccountBalance as WalletIcon,
  Payment as PaymentIcon,
  Block as BlockIcon,
  CheckCircle as VerifiedIcon,
  Warning as WarningIcon,
  Edit as EditIcon,
  Refresh as RefreshIcon,
  VpnKey as KeyIcon,
  Flag as FlagIcon
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import axios from 'axios';

interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  country: string;
  dateOfBirth: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'BLOCKED' | 'PENDING_VERIFICATION';
  kycStatus: 'NOT_STARTED' | 'PENDING' | 'VERIFIED' | 'REJECTED';
  kycLevel: number;
  accountType: 'INDIVIDUAL' | 'BUSINESS' | 'PREMIUM';
  createdAt: string;
  lastLoginAt: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  twoFactorEnabled: boolean;
  riskScore: number;
  suspiciousActivityCount: number;
  totalTransactionVolume: number;
  totalTransactionCount: number;
}

interface Wallet {
  id: string;
  currency: string;
  balance: number;
  availableBalance: number;
  frozenBalance: number;
  status: string;
}

interface Transaction {
  id: string;
  type: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
  description: string;
}

interface ActivityLog {
  id: string;
  action: string;
  ipAddress: string;
  userAgent: string;
  timestamp: string;
  location: string;
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

const UserDetailView: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [activityLogs, setActivityLogs] = useState<ActivityLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [tabValue, setTabValue] = useState(0);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [actionType, setActionType] = useState<'suspend' | 'block' | 'unblock' | 'verify'>('suspend');
  const [actionReason, setActionReason] = useState('');
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  useEffect(() => {
    loadUserData();
  }, [userId]);

  const loadUserData = async () => {
    setLoading(true);
    try {
      const [userRes, walletsRes, transactionsRes, logsRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/users/${userId}`),
        axios.get(`${API_BASE_URL}/admin/users/${userId}/wallets`),
        axios.get(`${API_BASE_URL}/admin/users/${userId}/transactions?limit=10`),
        axios.get(`${API_BASE_URL}/admin/users/${userId}/activity-logs?limit=20`)
      ]);

      setUser(userRes.data);
      setWallets(walletsRes.data);
      setTransactions(transactionsRes.data);
      setActivityLogs(logsRes.data);
    } catch (error) {
      console.error('Failed to load user data:', error);
      setSnackbar({ open: true, message: 'Failed to load user data', severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleAction = async () => {
    if (!actionReason.trim()) {
      setSnackbar({ open: true, message: 'Please provide a reason', severity: 'error' });
      return;
    }

    try {
      await axios.post(`${API_BASE_URL}/admin/users/${userId}/actions/${actionType}`, {
        reason: actionReason,
        actionBy: 'current-admin'
      });

      setSnackbar({ open: true, message: `User ${actionType} successfully`, severity: 'success' });
      setActionDialogOpen(false);
      setActionReason('');
      loadUserData();
    } catch (error) {
      setSnackbar({ open: true, message: `Failed to ${actionType} user`, severity: 'error' });
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'SUSPENDED': return 'warning';
      case 'BLOCKED': return 'error';
      case 'PENDING_VERIFICATION': return 'info';
      default: return 'default';
    }
  };

  const getKycStatusColor = (status: string) => {
    switch (status) {
      case 'VERIFIED': return 'success';
      case 'PENDING': return 'warning';
      case 'REJECTED': return 'error';
      case 'NOT_STARTED': return 'default';
      default: return 'default';
    }
  };

  const getRiskScoreColor = (score: number) => {
    if (score >= 80) return 'error';
    if (score >= 50) return 'warning';
    return 'success';
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!user) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">User not found</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Avatar sx={{ width: 64, height: 64, bgcolor: 'primary.main' }}>
            {user.firstName[0]}{user.lastName[0]}
          </Avatar>
          <Box>
            <Typography variant="h4">
              {user.firstName} {user.lastName}
            </Typography>
            <Typography variant="body2" color="textSecondary">
              User ID: {user.id}
            </Typography>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadUserData}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<EditIcon />}
            onClick={() => navigate(`/admin/users/${userId}/edit`)}
          >
            Edit
          </Button>
        </Box>
      </Box>

      {/* Status Overview */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Account Status
              </Typography>
              <Chip
                label={user.status}
                color={getStatusColor(user.status) as any}
                size="medium"
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                KYC Status
              </Typography>
              <Chip
                label={user.kycStatus}
                color={getKycStatusColor(user.kycStatus) as any}
                size="medium"
                icon={user.kycStatus === 'VERIFIED' ? <VerifiedIcon /> : undefined}
              />
              <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                Level {user.kycLevel}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Risk Score
              </Typography>
              <Typography variant="h4" color={`${getRiskScoreColor(user.riskScore)}.main`}>
                {user.riskScore}/100
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Account Type
              </Typography>
              <Chip label={user.accountType} color="primary" size="medium" />
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Risk Alerts */}
      {user.suspiciousActivityCount > 0 && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          <strong>{user.suspiciousActivityCount} suspicious activities detected</strong> - Review required
        </Alert>
      )}

      {/* User Information Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Personal Information
          </Typography>
          <Divider sx={{ mb: 2 }} />
          <Grid container spacing={3}>
            <Grid item xs={12} md={6}>
              <List>
                <ListItem>
                  <ListItemIcon><EmailIcon /></ListItemIcon>
                  <ListItemText
                    primary="Email"
                    secondary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {user.email}
                        {user.emailVerified && <VerifiedIcon color="success" fontSize="small" />}
                      </Box>
                    }
                  />
                </ListItem>
                <ListItem>
                  <ListItemIcon><PhoneIcon /></ListItemIcon>
                  <ListItemText
                    primary="Phone"
                    secondary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {user.phone}
                        {user.phoneVerified && <VerifiedIcon color="success" fontSize="small" />}
                      </Box>
                    }
                  />
                </ListItem>
                <ListItem>
                  <ListItemIcon><LocationIcon /></ListItemIcon>
                  <ListItemText primary="Country" secondary={user.country} />
                </ListItem>
              </List>
            </Grid>
            <Grid item xs={12} md={6}>
              <List>
                <ListItem>
                  <ListItemIcon><CalendarIcon /></ListItemIcon>
                  <ListItemText
                    primary="Date of Birth"
                    secondary={format(new Date(user.dateOfBirth), 'PPP')}
                  />
                </ListItem>
                <ListItem>
                  <ListItemIcon><SecurityIcon /></ListItemIcon>
                  <ListItemText
                    primary="2FA Status"
                    secondary={user.twoFactorEnabled ? '✓ Enabled' : '✗ Disabled'}
                  />
                </ListItem>
                <ListItem>
                  <ListItemIcon><CalendarIcon /></ListItemIcon>
                  <ListItemText
                    primary="Member Since"
                    secondary={format(new Date(user.createdAt), 'PPP')}
                  />
                </ListItem>
              </List>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Tabs */}
      <Card>
        <Tabs value={tabValue} onChange={(e, newValue) => setTabValue(newValue)}>
          <Tab label="Wallets" />
          <Tab label="Recent Transactions" />
          <Tab label="Activity Log" />
          <Tab label="Actions" />
        </Tabs>

        {/* Wallets Tab */}
        <TabPanel value={tabValue} index={0}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Currency</TableCell>
                  <TableCell align="right">Balance</TableCell>
                  <TableCell align="right">Available</TableCell>
                  <TableCell align="right">Frozen</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {wallets.map((wallet) => (
                  <TableRow key={wallet.id}>
                    <TableCell>{wallet.currency}</TableCell>
                    <TableCell align="right">{wallet.balance.toLocaleString()}</TableCell>
                    <TableCell align="right">{wallet.availableBalance.toLocaleString()}</TableCell>
                    <TableCell align="right">
                      {wallet.frozenBalance > 0 && (
                        <Typography color="error">{wallet.frozenBalance.toLocaleString()}</Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip label={wallet.status} size="small" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </TabPanel>

        {/* Transactions Tab */}
        <TabPanel value={tabValue} index={1}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Type</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Date</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {transactions.map((tx) => (
                  <TableRow key={tx.id}>
                    <TableCell>{tx.type}</TableCell>
                    <TableCell>{tx.description}</TableCell>
                    <TableCell align="right">
                      {tx.currency} {tx.amount.toLocaleString()}
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

        {/* Activity Log Tab */}
        <TabPanel value={tabValue} index={2}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Action</TableCell>
                  <TableCell>IP Address</TableCell>
                  <TableCell>Location</TableCell>
                  <TableCell>Timestamp</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {activityLogs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell>{log.action}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{log.ipAddress}</TableCell>
                    <TableCell>{log.location}</TableCell>
                    <TableCell>{format(new Date(log.timestamp), 'PPp')}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </TabPanel>

        {/* Actions Tab */}
        <TabPanel value={tabValue} index={3}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Account Actions</Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
                    {user.status === 'ACTIVE' && (
                      <>
                        <Button
                          variant="outlined"
                          color="warning"
                          startIcon={<WarningIcon />}
                          onClick={() => { setActionType('suspend'); setActionDialogOpen(true); }}
                        >
                          Suspend Account
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          startIcon={<BlockIcon />}
                          onClick={() => { setActionType('block'); setActionDialogOpen(true); }}
                        >
                          Block Account
                        </Button>
                      </>
                    )}
                    {(user.status === 'SUSPENDED' || user.status === 'BLOCKED') && (
                      <Button
                        variant="outlined"
                        color="success"
                        startIcon={<VerifiedIcon />}
                        onClick={() => { setActionType('unblock'); setActionDialogOpen(true); }}
                      >
                        Activate Account
                      </Button>
                    )}
                    {user.kycStatus !== 'VERIFIED' && (
                      <Button
                        variant="outlined"
                        color="primary"
                        startIcon={<VerifiedIcon />}
                        onClick={() => { setActionType('verify'); setActionDialogOpen(true); }}
                      >
                        Verify KYC
                      </Button>
                    )}
                  </Box>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Security Actions</Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
                    <Button variant="outlined" startIcon={<KeyIcon />}>
                      Reset Password
                    </Button>
                    <Button variant="outlined" startIcon={<SecurityIcon />}>
                      Reset 2FA
                    </Button>
                    <Button variant="outlined" color="error" startIcon={<FlagIcon />}>
                      Flag for Review
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
          {actionType === 'suspend' && 'Suspend Account'}
          {actionType === 'block' && 'Block Account'}
          {actionType === 'unblock' && 'Activate Account'}
          {actionType === 'verify' && 'Verify KYC'}
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            rows={4}
            label="Reason"
            value={actionReason}
            onChange={(e) => setActionReason(e.target.value)}
            placeholder="Provide reason for this action..."
            sx={{ mt: 2 }}
            required
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleAction}
            variant="contained"
            color={actionType === 'block' ? 'error' : 'primary'}
            disabled={!actionReason.trim()}
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

export default UserDetailView;
