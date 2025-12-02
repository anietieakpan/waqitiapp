import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Stepper,
  Step,
  StepLabel,
  Avatar,
  Chip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  InputAdornment,
  IconButton,
  Grid,
  Autocomplete,
  FormControl,
  FormHelperText,
  Collapse,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemButton,
  Divider,
  Paper,
  Fade,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import PersonIcon from '@mui/icons-material/Person';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CheckIcon from '@mui/icons-material/Check';
import QrCodeIcon from '@mui/icons-material/QrCode';
import ContactsIcon from '@mui/icons-material/Contacts';
import SearchIcon from '@mui/icons-material/Search';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SecurityIcon from '@mui/icons-material/Security';
import ScheduleIcon from '@mui/icons-material/Schedule';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';;
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { sendMoney, searchUsers, getFrequentRecipients } from '../store/slices/paymentSlice';
import { getWalletBalance } from '../store/slices/walletSlice';
import { formatCurrency, debounce } from '../utils/helpers';
import QrScanner from '../components/QrScanner';

const steps = ['Select Recipient', 'Enter Amount', 'Review & Confirm'];

interface Recipient {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  avatar?: string;
  phoneNumber?: string;
  isVerified: boolean;
}

interface PaymentData {
  recipient?: Recipient;
  amount: string;
  note: string;
  scheduledDate?: Date;
  recurring?: 'none' | 'weekly' | 'monthly';
}

const SendMoney: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { balance, loading: walletLoading } = useAppSelector((state) => state.wallet);
  const { searchResults, frequentRecipients, loading: paymentLoading } = useAppSelector(
    (state) => state.payment
  );

  const [activeStep, setActiveStep] = useState(0);
  const [paymentData, setPaymentData] = useState<PaymentData>({
    amount: '',
    note: '',
    recurring: 'none',
  });
  const [searchQuery, setSearchQuery] = useState('');
  const [showQrScanner, setShowQrScanner] = useState(false);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [showScheduleOptions, setShowScheduleOptions] = useState(false);
  const [transactionPin, setTransactionPin] = useState('');
  const [showSuccess, setShowSuccess] = useState(false);

  useEffect(() => {
    dispatch(getWalletBalance());
    dispatch(getFrequentRecipients());
  }, [dispatch]);

  const handleSearch = debounce((query: string) => {
    if (query.length >= 3) {
      dispatch(searchUsers(query));
    }
  }, 300);

  const handleRecipientSelect = (recipient: Recipient) => {
    setPaymentData({ ...paymentData, recipient });
    setActiveStep(1);
    setErrors({});
  };

  const handleQrScan = (data: string) => {
    try {
      const qrData = JSON.parse(data);
      if (qrData.userId && qrData.username) {
        handleRecipientSelect({
          id: qrData.userId,
          username: qrData.username,
          email: qrData.email || '',
          firstName: qrData.firstName || '',
          lastName: qrData.lastName || '',
          isVerified: qrData.isVerified || false,
        });
        setShowQrScanner(false);
      }
    } catch (error) {
      setErrors({ qr: 'Invalid QR code' });
    }
  };

  const validateAmount = (): boolean => {
    const newErrors: Record<string, string> = {};
    const amount = parseFloat(paymentData.amount);

    if (!paymentData.amount || isNaN(amount) || amount <= 0) {
      newErrors.amount = 'Please enter a valid amount';
    } else if (amount > balance) {
      newErrors.amount = 'Insufficient balance';
    } else if (amount < 0.5) {
      newErrors.amount = 'Minimum amount is $0.50';
    } else if (amount > 10000) {
      newErrors.amount = 'Maximum amount is $10,000';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleAmountNext = () => {
    if (validateAmount()) {
      setActiveStep(2);
    }
  };

  const handleConfirmPayment = async () => {
    if (!transactionPin || transactionPin.length !== 4) {
      setErrors({ pin: 'Please enter your 4-digit PIN' });
      return;
    }

    setShowConfirmDialog(false);
    
    try {
      await dispatch(
        sendMoney({
          recipientId: paymentData.recipient!.id,
          amount: parseFloat(paymentData.amount),
          currency: 'USD',
          note: paymentData.note,
          pin: transactionPin,
          scheduledDate: paymentData.scheduledDate?.toISOString(),
          recurring: paymentData.recurring,
        })
      ).unwrap();

      setShowSuccess(true);
      setTimeout(() => {
        navigate('/wallet');
      }, 3000);
    } catch (error: any) {
      setErrors({ submit: error.message || 'Payment failed. Please try again.' });
    }
  };

  const renderRecipientStep = () => (
    <Box>
      <TextField
        fullWidth
        placeholder="Search by username, email, or phone number"
        value={searchQuery}
        onChange={(e) => {
          setSearchQuery(e.target.value);
          handleSearch(e.target.value);
        }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon />
            </InputAdornment>
          ),
          endAdornment: (
            <InputAdornment position="end">
              <IconButton onClick={() => setShowQrScanner(true)}>
                <QrCodeIcon />
              </IconButton>
            </InputAdornment>
          ),
        }}
        sx={{ mb: 3 }}
      />

      {frequentRecipients.length > 0 && !searchQuery && (
        <>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            Frequent Recipients
          </Typography>
          <List>
            {frequentRecipients.map((recipient) => (
              <ListItemButton
                key={recipient.id}
                onClick={() => handleRecipientSelect(recipient)}
                sx={{ borderRadius: 2, mb: 1 }}
              >
                <ListItemAvatar>
                  <Avatar src={recipient.avatar}>
                    {recipient.firstName?.[0] || recipient.username[0]}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={`${recipient.firstName} ${recipient.lastName}` || recipient.username}
                  secondary={`@${recipient.username}`}
                />
                {recipient.isVerified && (
                  <Chip size="small" label="Verified" color="primary" variant="outlined" />
                )}
              </ListItemButton>
            ))}
          </List>
          <Divider sx={{ my: 2 }} />
        </>
      )}

      {searchResults.length > 0 && searchQuery && (
        <>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            Search Results
          </Typography>
          <List>
            {searchResults.map((recipient) => (
              <ListItemButton
                key={recipient.id}
                onClick={() => handleRecipientSelect(recipient)}
                sx={{ borderRadius: 2, mb: 1 }}
              >
                <ListItemAvatar>
                  <Avatar src={recipient.avatar}>
                    {recipient.firstName?.[0] || recipient.username[0]}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={`${recipient.firstName} ${recipient.lastName}` || recipient.username}
                  secondary={`@${recipient.username} • ${recipient.email}`}
                />
                {recipient.isVerified && (
                  <Chip size="small" label="Verified" color="primary" variant="outlined" />
                )}
              </ListItemButton>
            ))}
          </List>
        </>
      )}

      {searchQuery.length >= 3 && searchResults.length === 0 && !paymentLoading && (
        <Box textAlign="center" py={4}>
          <Typography color="text.secondary">No users found</Typography>
        </Box>
      )}

      <Button
        fullWidth
        variant="outlined"
        startIcon={<ContactsIcon />}
        onClick={() => navigate('/contacts')}
        sx={{ mt: 2 }}
      >
        Import from Contacts
      </Button>
    </Box>
  );

  const renderAmountStep = () => (
    <Box>
      {paymentData.recipient && (
        <Card variant="outlined" sx={{ mb: 3, bgcolor: 'background.default' }}>
          <CardContent>
            <Box display="flex" alignItems="center" gap={2}>
              <Avatar src={paymentData.recipient.avatar}>
                {paymentData.recipient.firstName?.[0] || paymentData.recipient.username[0]}
              </Avatar>
              <Box flex={1}>
                <Typography variant="subtitle1">
                  {`${paymentData.recipient.firstName} ${paymentData.recipient.lastName}` ||
                    paymentData.recipient.username}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  @{paymentData.recipient.username}
                </Typography>
              </Box>
              {paymentData.recipient.isVerified && (
                <Chip size="small" label="Verified" color="primary" variant="outlined" />
              )}
            </Box>
          </CardContent>
        </Card>
      )}

      <TextField
        fullWidth
        label="Amount"
        value={paymentData.amount}
        onChange={(e) => {
          const value = e.target.value.replace(/[^0-9.]/g, '');
          setPaymentData({ ...paymentData, amount: value });
          setErrors({ ...errors, amount: '' });
        }}
        error={!!errors.amount}
        helperText={errors.amount}
        InputProps={{
          startAdornment: <InputAdornment position="start">$</InputAdornment>,
        }}
        inputProps={{
          inputMode: 'decimal',
          pattern: '[0-9]*',
        }}
        sx={{ mb: 2 }}
      />

      <Typography variant="body2" color="text.secondary" align="center" sx={{ mb: 3 }}
>
        Available balance: {formatCurrency(balance)}
      </Typography>

      <Grid container spacing={1} sx={{ mb: 3 }}>
        {[10, 25, 50, 100].map((amount) => (
          <Grid item xs={3} key={amount}>
            <Button
              fullWidth
              variant="outlined"
              size="small"
              onClick={() => setPaymentData({ ...paymentData, amount: amount.toString() })}
            >
              ${amount}
            </Button>
          </Grid>
        ))}
      </Grid>

      <TextField
        fullWidth
        label="Add a note (optional)"
        value={paymentData.note}
        onChange={(e) => setPaymentData({ ...paymentData, note: e.target.value })}
        multiline
        rows={2}
        sx={{ mb: 2 }}
      />

      <Button
        variant="text"
        startIcon={<ScheduleIcon />}
        onClick={() => setShowScheduleOptions(!showScheduleOptions)}
        sx={{ mb: 2 }}
      >
        Schedule or Repeat
      </Button>

      <Collapse in={showScheduleOptions}>
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
          <FormControl fullWidth sx={{ mb: 2 }}>
            <TextField
              type="datetime-local"
              label="Schedule for later"
              value={paymentData.scheduledDate?.toISOString().slice(0, 16) || ''}
              onChange={(e) =>
                setPaymentData({
                  ...paymentData,
                  scheduledDate: e.target.value ? new Date(e.target.value) : undefined,
                })
              }
              InputLabelProps={{ shrink: true }}
            />
          </FormControl>

          <FormControl fullWidth>
            <Autocomplete
              value={paymentData.recurring}
              onChange={(_, value) =>
                setPaymentData({ ...paymentData, recurring: value as any })
              }
              options={['none', 'weekly', 'monthly']}
              getOptionLabel={(option) => 
                option === 'none' ? 'One-time payment' : `Repeat ${option}`
              }
              renderInput={(params) => <TextField {...params} label="Recurring" />}
            />
          </FormControl>
        </Paper>
      </Collapse>

      <Box display="flex" gap={2}>
        <Button
          fullWidth
          variant="outlined"
          onClick={() => setActiveStep(0)}
        >
          Back
        </Button>
        <Button
          fullWidth
          variant="contained"
          onClick={handleAmountNext}
          disabled={!paymentData.amount}
        >
          Continue
        </Button>
      </Box>
    </Box>
  );

  const renderReviewStep = () => (
    <Box>
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          Please review the details before confirming your payment
        </Typography>
      </Alert>

      <Card variant="outlined" sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h4" align="center" gutterBottom>
            {formatCurrency(parseFloat(paymentData.amount))}
          </Typography>

          <Divider sx={{ my: 2 }} />

          <Box display="flex" alignItems="center" gap={2} mb={2}>
            <Avatar src={paymentData.recipient?.avatar}>
              {paymentData.recipient?.firstName?.[0] || paymentData.recipient?.username[0]}
            </Avatar>
            <Box flex={1}>
              <Typography variant="subtitle1">
                To: {`${paymentData.recipient?.firstName} ${paymentData.recipient?.lastName}` ||
                  paymentData.recipient?.username}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                @{paymentData.recipient?.username}
              </Typography>
            </Box>
          </Box>

          {paymentData.note && (
            <>
              <Typography variant="body2" color="text.secondary">
                Note
              </Typography>
              <Typography variant="body1" paragraph>
                {paymentData.note}
              </Typography>
            </>
          )}

          {paymentData.scheduledDate && (
            <>
              <Typography variant="body2" color="text.secondary">
                Scheduled for
              </Typography>
              <Typography variant="body1" paragraph>
                {new Date(paymentData.scheduledDate).toLocaleString()}
              </Typography>
            </>
          )}

          {paymentData.recurring !== 'none' && (
            <>
              <Typography variant="body2" color="text.secondary">
                Recurring
              </Typography>
              <Typography variant="body1" paragraph>
                {paymentData.recurring === 'weekly' ? 'Every week' : 'Every month'}
              </Typography>
            </>
          )}
        </CardContent>
      </Card>

      {!paymentData.recipient?.isVerified && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="body2">
            This recipient is not verified. Please ensure you're sending to the correct person.
          </Typography>
        </Alert>
      )}

      {errors.submit && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {errors.submit}
        </Alert>
      )}

      <Box display="flex" gap={2}>
        <Button
          fullWidth
          variant="outlined"
          onClick={() => setActiveStep(1)}
        >
          Back
        </Button>
        <Button
          fullWidth
          variant="contained"
          startIcon={<SendIcon />}
          onClick={() => setShowConfirmDialog(true)}
          disabled={paymentLoading}
        >
          Send Money
        </Button>
      </Box>
    </Box>
  );

  if (showSuccess) {
    return (
      <Box
        display="flex"
        flexDirection="column"
        alignItems="center"
        justifyContent="center"
        minHeight="60vh"
      >
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', duration: 0.5 }}
        >
          <Avatar sx={{ width: 80, height: 80, bgcolor: 'success.main', mb: 2 }}>
            <CheckIcon sx={{ fontSize: 48 }} />
          </Avatar>
        </motion.div>
        <Typography variant="h4" gutterBottom>
          Payment Sent!
        </Typography>
        <Typography variant="body1" color="text.secondary" align="center" sx={{ mb: 2 }}>
          {formatCurrency(parseFloat(paymentData.amount))} has been sent to{' '}
          {paymentData.recipient?.firstName || paymentData.recipient?.username}
        </Typography>
        <Button variant="contained" onClick={() => navigate('/wallet')}>
          Back to Wallet
        </Button>
      </Box>
    );
  }

  return (
    <Box maxWidth="md" mx="auto">
      <Box display="flex" alignItems="center" gap={2} mb={4}>
        <IconButton onClick={() => navigate('/wallet')}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" component="h1">
          Send Money
        </Typography>
      </Box>

      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <AnimatePresence mode="wait">
        <motion.div
          key={activeStep}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          transition={{ duration: 0.3 }}
        >
          <Card>
            <CardContent sx={{ p: 3 }}>
              {activeStep === 0 && renderRecipientStep()}
              {activeStep === 1 && renderAmountStep()}
              {activeStep === 2 && renderReviewStep()}
            </CardContent>
          </Card>
        </motion.div>
      </AnimatePresence>

      <Dialog open={showQrScanner} onClose={() => setShowQrScanner(false)} fullScreen>
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="h6">Scan QR Code</Typography>
            <IconButton onClick={() => setShowQrScanner(false)}>
              <ArrowBackIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <QrScanner onScan={handleQrScan} />
          {errors.qr && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {errors.qr}
            </Alert>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={showConfirmDialog} onClose={() => setShowConfirmDialog(false)}>
        <DialogTitle>
          <Box display="flex" alignItems="center" gap={1}>
            <SecurityIcon color="primary" />
            <Typography variant="h6">Confirm Payment</Typography>
          </Box>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" paragraph>
            Enter your 4-digit transaction PIN to confirm this payment
          </Typography>
          <TextField
            fullWidth
            type="password"
            label="Transaction PIN"
            value={transactionPin}
            onChange={(e) => {
              const value = e.target.value.replace(/[^0-9]/g, '').slice(0, 4);
              setTransactionPin(value);
              setErrors({ ...errors, pin: '' });
            }}
            error={!!errors.pin}
            helperText={errors.pin}
            inputProps={{
              maxLength: 4,
              inputMode: 'numeric',
              pattern: '[0-9]*',
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowConfirmDialog(false)}>Cancel</Button>
          <Button
            onClick={handleConfirmPayment}
            variant="contained"
            disabled={transactionPin.length !== 4 || paymentLoading}
          >
            {paymentLoading ? <CircularProgress size={24} /> : 'Confirm'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SendMoney;