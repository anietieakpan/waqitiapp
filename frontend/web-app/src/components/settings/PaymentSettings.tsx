import React, { useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Button,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  Divider,
  Switch,
  FormControlLabel,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  InputAdornment,
  Stepper,
  Step,
  StepLabel,
} from '@mui/material';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import SecurityIcon from '@mui/icons-material/Security';
import WarningIcon from '@mui/icons-material/Warning';
import CheckIcon from '@mui/icons-material/Check';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ReceiptIcon from '@mui/icons-material/Receipt';
import VerifiedIcon from '@mui/icons-material/Verified';
import BlockIcon from '@mui/icons-material/Block';
import StarIcon from '@mui/icons-material/Star';;
import { format } from 'date-fns';
import { useQuery } from 'react-query';
import { paymentService } from '@/services/paymentService';
import { PaymentMethod, PaymentLimit } from '@/types/payment';
import toast from 'react-hot-toast';

interface AddPaymentMethodForm {
  type: 'bank_account' | 'debit_card' | 'credit_card';
  accountNumber?: string;
  routingNumber?: string;
  cardNumber?: string;
  expiryDate?: string;
  cvv?: string;
  billingZip?: string;
  nickname?: string;
}

const PaymentSettings: React.FC = () => {
  const [showAddMethodDialog, setShowAddMethodDialog] = useState(false);
  const [editingMethod, setEditingMethod] = useState<PaymentMethod | null>(null);
  const [addMethodStep, setAddMethodStep] = useState(0);
  const [addMethodForm, setAddMethodForm] = useState<AddPaymentMethodForm>({
    type: 'bank_account',
  });
  const [loading, setLoading] = useState(false);
  const [showLimitDialog, setShowLimitDialog] = useState(false);
  const [selectedLimit, setSelectedLimit] = useState<PaymentLimit | null>(null);

  // Mock data for demonstration
  const mockPaymentMethods: PaymentMethod[] = [
    {
      id: '1',
      type: 'bank_account',
      name: 'Chase Checking ****1234',
      last4: '1234',
      bankName: 'Chase Bank',
      isDefault: true,
      verified: true,
    },
    {
      id: '2',
      type: 'debit_card',
      name: 'Visa Debit ****5678',
      last4: '5678',
      cardBrand: 'Visa',
      isDefault: false,
      verified: true,
      expiresAt: '2025-12-31',
    },
    {
      id: '3',
      type: 'credit_card',
      name: 'Mastercard ****9012',
      last4: '9012',
      cardBrand: 'Mastercard',
      isDefault: false,
      verified: true,
      expiresAt: '2026-08-31',
    },
  ];

  const mockPaymentLimits: PaymentLimit[] = [
    {
      type: 'daily',
      current: 1250.50,
      limit: 5000,
      currency: 'USD',
      resetDate: new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString(),
    },
    {
      type: 'weekly',
      current: 3500.00,
      limit: 15000,
      currency: 'USD',
      resetDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
    },
    {
      type: 'monthly',
      current: 8750.25,
      limit: 50000,
      currency: 'USD',
      resetDate: new Date(Date.now() + 15 * 24 * 60 * 60 * 1000).toISOString(),
    },
    {
      type: 'per_transaction',
      current: 0,
      limit: 10000,
      currency: 'USD',
    },
  ];

  const { data: paymentMethods = mockPaymentMethods, refetch: refetchMethods } = useQuery(
    'paymentMethods',
    () => paymentService.getPaymentMethods(),
    { initialData: mockPaymentMethods }
  );

  const { data: paymentLimits = mockPaymentLimits, refetch: refetchLimits } = useQuery(
    'paymentLimits',
    () => paymentService.getPaymentLimits(),
    { initialData: mockPaymentLimits }
  );

  const getMethodIcon = (type: string) => {
    switch (type) {
      case 'bank_account':
        return <AccountBalance />;
      case 'debit_card':
      case 'credit_card':
        return <CreditCard />;
      default:
        return <Receipt />;
    }
  };

  const handleAddPaymentMethod = async () => {
    setLoading(true);
    try {
      // In a real app, call API
      await new Promise(resolve => setTimeout(resolve, 2000));
      toast.success('Payment method added successfully!');
      setShowAddMethodDialog(false);
      setAddMethodStep(0);
      setAddMethodForm({ type: 'bank_account' });
      refetchMethods();
    } catch (error) {
      toast.error('Failed to add payment method');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteMethod = async (methodId: string) => {
    if (window.confirm('Are you sure you want to remove this payment method?')) {
      try {
        await paymentService.deletePaymentMethod(methodId);
        toast.success('Payment method removed');
        refetchMethods();
      } catch (error) {
        toast.error('Failed to remove payment method');
      }
    }
  };

  const handleSetDefault = async (methodId: string) => {
    try {
      await paymentService.setDefaultPaymentMethod(methodId);
      toast.success('Default payment method updated');
      refetchMethods();
    } catch (error) {
      toast.error('Failed to update default payment method');
    }
  };

  const handleRequestLimitIncrease = async () => {
    if (!selectedLimit) return;
    
    setLoading(true);
    try {
      // In a real app, call API
      await new Promise(resolve => setTimeout(resolve, 2000));
      toast.success('Limit increase request submitted');
      setShowLimitDialog(false);
    } catch (error) {
      toast.error('Failed to submit request');
    } finally {
      setLoading(false);
    }
  };

  const getLimitProgress = (limit: PaymentLimit) => {
    return (limit.current / limit.limit) * 100;
  };

  const getLimitColor = (progress: number) => {
    if (progress >= 90) return 'error';
    if (progress >= 75) return 'warning';
    return 'primary';
  };

  const renderAddMethodStep = () => {
    switch (addMethodStep) {
      case 0: // Choose type
        return (
          <Box>
            <Typography variant="body1" paragraph>
              Select a payment method type to add:
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Button
                  fullWidth
                  variant={addMethodForm.type === 'bank_account' ? 'contained' : 'outlined'}
                  size="large"
                  startIcon={<AccountBalance />}
                  onClick={() => setAddMethodForm({ ...addMethodForm, type: 'bank_account' })}
                  sx={{ justifyContent: 'flex-start', py: 2 }}
                >
                  <Box textAlign="left">
                    <Typography variant="subtitle1">Bank Account</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Connect your checking or savings account
                    </Typography>
                  </Box>
                </Button>
              </Grid>
              <Grid item xs={12}>
                <Button
                  fullWidth
                  variant={addMethodForm.type === 'debit_card' ? 'contained' : 'outlined'}
                  size="large"
                  startIcon={<CreditCard />}
                  onClick={() => setAddMethodForm({ ...addMethodForm, type: 'debit_card' })}
                  sx={{ justifyContent: 'flex-start', py: 2 }}
                >
                  <Box textAlign="left">
                    <Typography variant="subtitle1">Debit Card</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Add a debit card for instant transfers
                    </Typography>
                  </Box>
                </Button>
              </Grid>
              <Grid item xs={12}>
                <Button
                  fullWidth
                  variant={addMethodForm.type === 'credit_card' ? 'contained' : 'outlined'}
                  size="large"
                  startIcon={<CreditCard />}
                  onClick={() => setAddMethodForm({ ...addMethodForm, type: 'credit_card' })}
                  sx={{ justifyContent: 'flex-start', py: 2 }}
                >
                  <Box textAlign="left">
                    <Typography variant="subtitle1">Credit Card</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Use a credit card (fees may apply)
                    </Typography>
                  </Box>
                </Button>
              </Grid>
            </Grid>
          </Box>
        );

      case 1: // Enter details
        return (
          <Box>
            <Typography variant="body1" paragraph>
              Enter your {addMethodForm.type.replace('_', ' ')} details:
            </Typography>
            <Grid container spacing={2}>
              {addMethodForm.type === 'bank_account' ? (
                <>
                  <Grid item xs={12}>
                    <TextField
                      fullWidth
                      label="Account Number"
                      value={addMethodForm.accountNumber || ''}
                      onChange={(e) => setAddMethodForm({ ...addMethodForm, accountNumber: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField
                      fullWidth
                      label="Routing Number"
                      value={addMethodForm.routingNumber || ''}
                      onChange={(e) => setAddMethodForm({ ...addMethodForm, routingNumber: e.target.value })}
                    />
                  </Grid>
                </>
              ) : (
                <>
                  <Grid item xs={12}>
                    <TextField
                      fullWidth
                      label="Card Number"
                      value={addMethodForm.cardNumber || ''}
                      onChange={(e) => setAddMethodForm({ ...addMethodForm, cardNumber: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <TextField
                      fullWidth
                      label="Expiry Date"
                      placeholder="MM/YY"
                      value={addMethodForm.expiryDate || ''}
                      onChange={(e) => setAddMethodForm({ ...addMethodForm, expiryDate: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <TextField
                      fullWidth
                      label="CVV"
                      value={addMethodForm.cvv || ''}
                      onChange={(e) => setAddMethodForm({ ...addMethodForm, cvv: e.target.value })}
                    />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField
                      fullWidth
                      label="Billing ZIP Code"
                      value={addMethodForm.billingZip || ''}
                      onChange={(e) => setAddMethodForm({ ...addMethodForm, billingZip: e.target.value })}
                    />
                  </Grid>
                </>
              )}
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Nickname (optional)"
                  value={addMethodForm.nickname || ''}
                  onChange={(e) => setAddMethodForm({ ...addMethodForm, nickname: e.target.value })}
                  helperText="Give this payment method a nickname for easy identification"
                />
              </Grid>
            </Grid>
          </Box>
        );

      case 2: // Verify
        return (
          <Box textAlign="center">
            <Security sx={{ fontSize: 64, color: 'primary.main', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Verify Your Payment Method
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              We'll make two small deposits to your account within 1-2 business days. 
              Once you see them, enter the amounts here to verify your account.
            </Typography>
            <Alert severity="info" sx={{ mt: 2 }}>
              This verification process helps protect your account from unauthorized access.
            </Alert>
          </Box>
        );
    }
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Payment Settings
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Manage your payment methods and transaction limits
      </Typography>

      {/* Payment Methods */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">Payment Methods</Typography>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={() => setShowAddMethodDialog(true)}
            >
              Add Method
            </Button>
          </Box>

          <List>
            {paymentMethods.map((method, index) => (
              <React.Fragment key={method.id}>
                <ListItem>
                  <ListItemIcon>{getMethodIcon(method.type)}</ListItemIcon>
                  <ListItemText
                    primary={
                      <Box display="flex" alignItems="center" gap={1}>
                        {method.name}
                        {method.isDefault && (
                          <Chip label="Default" color="primary" size="small" />
                        )}
                        {method.verified ? (
                          <Verified color="success" fontSize="small" />
                        ) : (
                          <Warning color="warning" fontSize="small" />
                        )}
                      </Box>
                    }
                    secondary={
                      <Box>
                        {method.bankName && `${method.bankName} • `}
                        {method.cardBrand && `${method.cardBrand} • `}
                        {method.expiresAt && `Expires ${format(new Date(method.expiresAt), 'MM/yy')} • `}
                        Added {format(new Date(), 'MMM dd, yyyy')}
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    {!method.isDefault && (
                      <Button
                        size="small"
                        onClick={() => handleSetDefault(method.id)}
                        sx={{ mr: 1 }}
                      >
                        Set Default
                      </Button>
                    )}
                    <IconButton
                      edge="end"
                      onClick={() => handleDeleteMethod(method.id)}
                      disabled={method.isDefault}
                    >
                      <Delete />
                    </IconButton>
                  </ListItemSecondaryAction>
                </ListItem>
                {index < paymentMethods.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List>

          {paymentMethods.length === 0 && (
            <Box textAlign="center" py={4}>
              <Receipt sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
              <Typography variant="h6" color="text.secondary">
                No payment methods added
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Add a payment method to start sending and receiving money
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Transaction Limits */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Transaction Limits
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Your current transaction limits and usage
          </Typography>

          <Grid container spacing={3}>
            {paymentLimits.map((limit) => {
              const progress = getLimitProgress(limit);
              const color = getLimitColor(progress);
              
              return (
                <Grid item xs={12} sm={6} key={limit.type}>
                  <Card variant="outlined">
                    <CardContent>
                      <Typography variant="subtitle2" color="text.secondary">
                        {limit.type.replace('_', ' ').toUpperCase()}
                      </Typography>
                      <Typography variant="h6" gutterBottom>
                        ${limit.current.toFixed(2)} / ${limit.limit.toFixed(2)}
                      </Typography>
                      <Box sx={{ position: 'relative', pt: 1 }}>
                        <Box
                          sx={{
                            height: 8,
                            borderRadius: 1,
                            bgcolor: 'grey.200',
                            overflow: 'hidden',
                          }}
                        >
                          <Box
                            sx={{
                              height: '100%',
                              width: `${progress}%`,
                              bgcolor: `${color}.main`,
                              transition: 'width 0.3s ease',
                            }}
                          />
                        </Box>
                      </Box>
                      {limit.resetDate && (
                        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                          Resets {format(new Date(limit.resetDate), 'MMM dd, HH:mm')}
                        </Typography>
                      )}
                      <Button
                        size="small"
                        onClick={() => {
                          setSelectedLimit(limit);
                          setShowLimitDialog(true);
                        }}
                        sx={{ mt: 1 }}
                      >
                        Request Increase
                      </Button>
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        </CardContent>
      </Card>

      {/* Auto-payments */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Auto-payments & Subscriptions
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Manage recurring payments and subscriptions
          </Typography>
          
          <Alert severity="info" sx={{ mb: 2 }}>
            No active auto-payments. Set up recurring transfers or bill payments to save time.
          </Alert>
          
          <Button variant="outlined" startIcon={<Schedule />}>
            Set Up Auto-payment
          </Button>
        </CardContent>
      </Card>

      {/* Payment Preferences */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Payment Preferences
          </Typography>
          
          <List>
            <ListItem>
              <ListItemText
                primary="Instant Transfer"
                secondary="Pay a small fee for instant transfers (usually 1-3%)"
              />
              <ListItemSecondaryAction>
                <Switch defaultChecked />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemText
                primary="International Payments"
                secondary="Enable sending money internationally"
              />
              <ListItemSecondaryAction>
                <Switch />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Payment Requests Auto-accept"
                secondary="Automatically accept payment requests from trusted contacts"
              />
              <ListItemSecondaryAction>
                <Switch />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Add Payment Method Dialog */}
      <Dialog
        open={showAddMethodDialog}
        onClose={() => setShowAddMethodDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          Add Payment Method
        </DialogTitle>
        <DialogContent>
          <Stepper activeStep={addMethodStep} sx={{ mb: 3 }}>
            <Step>
              <StepLabel>Choose Type</StepLabel>
            </Step>
            <Step>
              <StepLabel>Enter Details</StepLabel>
            </Step>
            <Step>
              <StepLabel>Verify</StepLabel>
            </Step>
          </Stepper>
          
          {renderAddMethodStep()}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowAddMethodDialog(false)}>
            Cancel
          </Button>
          {addMethodStep > 0 && (
            <Button onClick={() => setAddMethodStep(prev => prev - 1)}>
              Back
            </Button>
          )}
          <Button
            variant="contained"
            onClick={() => {
              if (addMethodStep < 2) {
                setAddMethodStep(prev => prev + 1);
              } else {
                handleAddPaymentMethod();
              }
            }}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            {addMethodStep === 2 ? 'Complete Setup' : 'Next'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Limit Increase Dialog */}
      <Dialog
        open={showLimitDialog}
        onClose={() => setShowLimitDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          Request Limit Increase
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" paragraph>
            Request an increase to your {selectedLimit?.type.replace('_', ' ')} limit.
          </Typography>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Current Limit"
                value={`$${selectedLimit?.limit.toFixed(2) || 0}`}
                disabled
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Requested Limit"
                type="number"
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={3}
                label="Reason for Increase"
                placeholder="Please explain why you need a higher limit..."
              />
            </Grid>
          </Grid>
          <Alert severity="info" sx={{ mt: 2 }}>
            Limit increase requests are typically reviewed within 1-2 business days.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowLimitDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleRequestLimitIncrease}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            Submit Request
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PaymentSettings;