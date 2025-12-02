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
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  InputAdornment,
  Chip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  IconButton,
  Divider,
  Paper,
  Grid,
  Tooltip,
  Collapse,
  Switch,
  FormControlLabel,
  Autocomplete,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import PersonIcon from '@mui/icons-material/Person';
import QrCodeIcon from '@mui/icons-material/QrCode';
import SearchIcon from '@mui/icons-material/Search';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ReceiptIcon from '@mui/icons-material/Receipt';
import SecurityIcon from '@mui/icons-material/Security';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import GroupIcon from '@mui/icons-material/Group';
import ContactsIcon from '@mui/icons-material/Contacts';
import HistoryIcon from '@mui/icons-material/History';
import FavoriteIcon from '@mui/icons-material/Favorite';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';
import AtmIcon from '@mui/icons-material/LocalAtm';
import SpeedIcon from '@mui/icons-material/Speed';;
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../hooks/redux';
import { sendPayment, calculateFees } from '../../store/slices/paymentSlice';
import { searchUsers, getRecentContacts } from '../../store/slices/userSlice';
import { PaymentMethod, TransactionSpeed } from '../../types/payment';
import { formatCurrency, formatPhoneNumber } from '../../utils/formatters';
import PaymentMethodSelector from './PaymentMethodSelector';
import TransactionPinDialog from './TransactionPinDialog';
import QRScanner from './QRScanner';

const steps = ['Select Recipient', 'Enter Amount', 'Review & Send'];

interface Recipient {
  id: string;
  name: string;
  username: string;
  email: string;
  phone: string;
  avatar: string;
  lastPaymentDate?: string;
  isFavorite?: boolean;
}

export default function SendMoney() {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { balance, defaultPaymentMethod } = useAppSelector((state) => state.wallet);
  const { isLoading, fees } = useAppSelector((state) => state.payment);

  const [activeStep, setActiveStep] = useState(0);
  const [recipient, setRecipient] = useState<Recipient | null>(null);
  const [recipientSearch, setRecipientSearch] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(defaultPaymentMethod);
  const [transactionSpeed, setTransactionSpeed] = useState<TransactionSpeed>('standard');
  const [isScheduled, setIsScheduled] = useState(false);
  const [scheduledDate, setScheduledDate] = useState('');
  const [searchResults, setSearchResults] = useState<Recipient[]>([]);
  const [recentContacts, setRecentContacts] = useState<Recipient[]>([]);
  const [favorites, setFavorites] = useState<Recipient[]>([]);
  const [showQRScanner, setShowQRScanner] = useState(false);
  const [showPinDialog, setShowPinDialog] = useState(false);
  const [paymentSuccess, setPaymentSuccess] = useState(false);
  const [paymentError, setPaymentError] = useState('');
  const [recipientType, setRecipientType] = useState<'contact' | 'phone' | 'email' | 'qr'>('contact');

  useEffect(() => {
    loadRecentContacts();
    loadFavorites();
  }, []);

  useEffect(() => {
    if (recipientSearch.length > 2 && recipientType === 'contact') {
      searchForUsers();
    }
  }, [recipientSearch]);

  useEffect(() => {
    if (amount && recipient) {
      dispatch(calculateFees({
        amount: parseFloat(amount),
        paymentMethod,
        transactionSpeed,
      }));
    }
  }, [amount, paymentMethod, transactionSpeed, recipient]);

  const loadRecentContacts = async () => {
    const contacts = await dispatch(getRecentContacts()).unwrap();
    setRecentContacts(contacts);
  };

  const loadFavorites = async () => {
    // Load favorite recipients
    const favs = recentContacts.filter(c => c.isFavorite);
    setFavorites(favs);
  };

  const searchForUsers = async () => {
    const results = await dispatch(searchUsers(recipientSearch)).unwrap();
    setSearchResults(results);
  };

  const handleRecipientSelect = (selectedRecipient: Recipient) => {
    setRecipient(selectedRecipient);
    setActiveStep(1);
  };

  const handleAmountChange = (value: string) => {
    // Only allow valid currency format
    const regex = /^\d*\.?\d{0,2}$/;
    if (regex.test(value) || value === '') {
      setAmount(value);
    }
  };

  const handleQRScan = (data: string) => {
    // Parse QR code data
    try {
      const qrData = JSON.parse(data);
      if (qrData.userId) {
        // Look up user by ID
        handleRecipientSelect(qrData);
      }
      if (qrData.amount) {
        setAmount(qrData.amount.toString());
      }
      setShowQRScanner(false);
    } catch (error) {
      setPaymentError('Invalid QR code');
    }
  };

  const handleNext = () => {
    if (activeStep === 0 && !recipient) {
      setPaymentError('Please select a recipient');
      return;
    }
    if (activeStep === 1) {
      if (!amount || parseFloat(amount) <= 0) {
        setPaymentError('Please enter a valid amount');
        return;
      }
      if (parseFloat(amount) > balance) {
        setPaymentError('Insufficient balance');
        return;
      }
    }
    setPaymentError('');
    setActiveStep((prev) => prev + 1);
  };

  const handleBack = () => {
    setActiveStep((prev) => prev - 1);
  };

  const handleSendPayment = async (pin: string) => {
    try {
      const paymentData = {
        recipientId: recipient!.id,
        amount: parseFloat(amount),
        currency: 'USD',
        note,
        paymentMethod,
        transactionSpeed,
        pin,
        scheduledDate: isScheduled ? scheduledDate : undefined,
      };

      await dispatch(sendPayment(paymentData)).unwrap();
      setPaymentSuccess(true);
      setShowPinDialog(false);
    } catch (error: any) {
      setPaymentError(error.message || 'Payment failed');
      setShowPinDialog(false);
    }
  };

  const handlePaymentComplete = () => {
    navigate('/transactions');
  };

  const renderRecipientStep = () => (
    <Box>
      <Box sx={{ mb: 3 }}>
        <ToggleButtonGroup
          value={recipientType}
          exclusive
          onChange={(e, value) => value && setRecipientType(value)}
          fullWidth
        >
          <ToggleButton value="contact">
            <ContactsIcon sx={{ mr: 1 }} /> Contacts
          </ToggleButton>
          <ToggleButton value="phone">
            <PersonIcon sx={{ mr: 1 }} /> Phone
          </ToggleButton>
          <ToggleButton value="email">
            <PersonIcon sx={{ mr: 1 }} /> Email
          </ToggleButton>
          <ToggleButton value="qr">
            <QrCodeIcon sx={{ mr: 1 }} /> QR Code
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {recipientType === 'contact' && (
        <>
          <TextField
            fullWidth
            label="Search contacts"
            value={recipientSearch}
            onChange={(e) => setRecipientSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            sx={{ mb: 3 }}
          />

          {favorites.length > 0 && (
            <Box sx={{ mb: 3 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Favorites
              </Typography>
              <Box sx={{ display: 'flex', gap: 2, overflowX: 'auto', pb: 1 }}>
                {favorites.map((fav) => (
                  <Paper
                    key={fav.id}
                    sx={{
                      p: 2,
                      minWidth: 120,
                      textAlign: 'center',
                      cursor: 'pointer',
                      '&:hover': { bgcolor: 'action.hover' },
                    }}
                    onClick={() => handleRecipientSelect(fav)}
                  >
                    <Avatar src={fav.avatar} sx={{ mx: 'auto', mb: 1 }}>
                      {fav.name.charAt(0)}
                    </Avatar>
                    <Typography variant="body2" noWrap>
                      {fav.name.split(' ')[0]}
                    </Typography>
                  </Paper>
                ))}
              </Box>
            </Box>
          )}

          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {recipientSearch ? 'Search Results' : 'Recent Contacts'}
          </Typography>
          <List>
            {(recipientSearch ? searchResults : recentContacts).map((contact) => (
              <ListItem
                key={contact.id}
                button
                onClick={() => handleRecipientSelect(contact)}
              >
                <ListItemAvatar>
                  <Avatar src={contact.avatar}>
                    {contact.name.charAt(0)}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={contact.name}
                  secondary={`@${contact.username}`}
                />
                {contact.lastPaymentDate && (
                  <ListItemSecondaryAction>
                    <Typography variant="caption" color="text.secondary">
                      Last: {new Date(contact.lastPaymentDate).toLocaleDateString()}
                    </Typography>
                  </ListItemSecondaryAction>
                )}
              </ListItem>
            ))}
          </List>
        </>
      )}

      {recipientType === 'phone' && (
        <TextField
          fullWidth
          label="Phone number"
          value={recipientSearch}
          onChange={(e) => setRecipientSearch(formatPhoneNumber(e.target.value))}
          placeholder="(555) 123-4567"
          helperText="Enter recipient's phone number"
        />
      )}

      {recipientType === 'email' && (
        <TextField
          fullWidth
          label="Email address"
          type="email"
          value={recipientSearch}
          onChange={(e) => setRecipientSearch(e.target.value)}
          placeholder="user@example.com"
          helperText="Enter recipient's email address"
        />
      )}

      {recipientType === 'qr' && (
        <Box sx={{ textAlign: 'center', py: 3 }}>
          <Button
            variant="contained"
            size="large"
            startIcon={<QrCodeIcon />}
            onClick={() => setShowQRScanner(true)}
          >
            Scan QR Code
          </Button>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Scan recipient's QR code to send money instantly
          </Typography>
        </Box>
      )}
    </Box>
  );

  const renderAmountStep = () => (
    <Box>
      <Box sx={{ mb: 4, textAlign: 'center' }}>
        <Avatar
          src={recipient?.avatar}
          sx={{ width: 80, height: 80, mx: 'auto', mb: 2 }}
        >
          {recipient?.name.charAt(0)}
        </Avatar>
        <Typography variant="h6">{recipient?.name}</Typography>
        <Typography variant="body2" color="text.secondary">
          @{recipient?.username}
        </Typography>
      </Box>

      <TextField
        fullWidth
        label="Amount"
        value={amount}
        onChange={(e) => handleAmountChange(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <MoneyIcon />
            </InputAdornment>
          ),
        }}
        inputProps={{
          inputMode: 'decimal',
          pattern: '[0-9]*',
          style: { fontSize: '2rem', textAlign: 'center' },
        }}
        sx={{ mb: 3 }}
      />

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          Quick amounts
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {[10, 25, 50, 100].map((quickAmount) => (
            <Chip
              key={quickAmount}
              label={`$${quickAmount}`}
              onClick={() => setAmount(quickAmount.toString())}
              variant={amount === quickAmount.toString() ? 'filled' : 'outlined'}
            />
          ))}
        </Box>
      </Box>

      <TextField
        fullWidth
        label="Note (optional)"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        multiline
        rows={2}
        sx={{ mb: 3 }}
      />

      <PaymentMethodSelector
        selectedMethod={paymentMethod}
        onSelect={setPaymentMethod}
        balance={balance}
      />

      <Box sx={{ mt: 3 }}>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          Transaction Speed
        </Typography>
        <ToggleButtonGroup
          value={transactionSpeed}
          exclusive
          onChange={(e, value) => value && setTransactionSpeed(value)}
          fullWidth
        >
          <ToggleButton value="standard">
            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="body2">Standard</Typography>
              <Typography variant="caption">1-3 days • Free</Typography>
            </Box>
          </ToggleButton>
          <ToggleButton value="instant">
            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="body2">Instant</Typography>
              <Typography variant="caption">Immediate • {formatCurrency(fees?.instantFee || 0.5)}</Typography>
            </Box>
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

      <FormControlLabel
        control={
          <Switch
            checked={isScheduled}
            onChange={(e) => setIsScheduled(e.target.checked)}
          />
        }
        label="Schedule payment"
        sx={{ mt: 2 }}
      />

      <Collapse in={isScheduled}>
        <TextField
          fullWidth
          label="Schedule date"
          type="datetime-local"
          value={scheduledDate}
          onChange={(e) => setScheduledDate(e.target.value)}
          InputLabelProps={{ shrink: true }}
          sx={{ mt: 2 }}
        />
      </Collapse>
    </Box>
  );

  const renderReviewStep = () => {
    const totalAmount = parseFloat(amount) + (fees?.totalFee || 0);

    return (
      <Box>
        <Paper sx={{ p: 3, mb: 3 }}>
          <Typography variant="h6" sx={{ mb: 3 }}>
            Payment Summary
          </Typography>

          <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
            <Avatar src={recipient?.avatar} sx={{ mr: 2 }}>
              {recipient?.name.charAt(0)}
            </Avatar>
            <Box>
              <Typography variant="subtitle1">{recipient?.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                @{recipient?.username}
              </Typography>
            </Box>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Box sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
              <Typography>Amount</Typography>
              <Typography>{formatCurrency(parseFloat(amount))}</Typography>
            </Box>
            {fees?.processingFee > 0 && (
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Processing fee
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {formatCurrency(fees.processingFee)}
                </Typography>
              </Box>
            )}
            {transactionSpeed === 'instant' && (
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Instant transfer fee
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {formatCurrency(fees?.instantFee || 0.5)}
                </Typography>
              </Box>
            )}
          </Box>

          <Divider sx={{ my: 2 }} />

          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="h6">Total</Typography>
            <Typography variant="h6">{formatCurrency(totalAmount)}</Typography>
          </Box>

          {note && (
            <Box sx={{ mb: 2 }}>
              <Typography variant="body2" color="text.secondary">
                Note: {note}
              </Typography>
            </Box>
          )}

          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <Typography variant="body2" sx={{ mr: 1 }}>
              Payment method:
            </Typography>
            {paymentMethod === 'balance' && <AtmIcon sx={{ mr: 1 }} />}
            {paymentMethod === 'card' && <CreditCardIcon sx={{ mr: 1 }} />}
            {paymentMethod === 'bank' && <BankIcon sx={{ mr: 1 }} />}
            <Typography variant="body2">
              {paymentMethod === 'balance' && 'Waqiti Balance'}
              {paymentMethod === 'card' && 'Debit Card ****4242'}
              {paymentMethod === 'bank' && 'Bank Account ****1234'}
            </Typography>
          </Box>

          {isScheduled && (
            <Alert severity="info" icon={<ScheduleIcon />} sx={{ mb: 2 }}>
              Scheduled for {new Date(scheduledDate).toLocaleString()}
            </Alert>
          )}
        </Paper>

        <Alert severity="info" sx={{ mb: 3 }}>
          <Typography variant="body2">
            By sending this payment, you agree to our terms of service and privacy policy.
            This transaction will be processed securely.
          </Typography>
        </Alert>
      </Box>
    );
  };

  if (paymentSuccess) {
    return (
      <Card>
        <CardContent sx={{ textAlign: 'center', py: 6 }}>
          <CheckCircleIcon sx={{ fontSize: 80, color: 'success.main', mb: 3 }} />
          <Typography variant="h4" sx={{ mb: 2 }}>
            Payment Sent!
          </Typography>
          <Typography variant="h6" sx={{ mb: 1 }}>
            {formatCurrency(parseFloat(amount))}
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            to {recipient?.name}
          </Typography>
          <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
            <Button
              variant="contained"
              onClick={handlePaymentComplete}
              startIcon={<ReceiptIcon />}
            >
              View Receipt
            </Button>
            <Button
              variant="outlined"
              onClick={() => navigate('/dashboard')}
            >
              Done
            </Button>
          </Box>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
            <IconButton onClick={() => navigate('/dashboard')} sx={{ mr: 2 }}>
              <ArrowBackIcon />
            </IconButton>
            <Typography variant="h5">Send Money</Typography>
          </Box>

          <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
            {steps.map((label) => (
              <Step key={label}>
                <StepLabel>{label}</StepLabel>
              </Step>
            ))}
          </Stepper>

          {paymentError && (
            <Alert severity="error" sx={{ mb: 3 }} onClose={() => setPaymentError('')}>
              {paymentError}
            </Alert>
          )}

          {activeStep === 0 && renderRecipientStep()}
          {activeStep === 1 && renderAmountStep()}
          {activeStep === 2 && renderReviewStep()}

          <Box sx={{ mt: 4, display: 'flex', justifyContent: 'space-between' }}>
            <Button
              disabled={activeStep === 0}
              onClick={handleBack}
            >
              Back
            </Button>
            {activeStep < steps.length - 1 ? (
              <Button
                variant="contained"
                onClick={handleNext}
                disabled={isLoading}
              >
                Next
              </Button>
            ) : (
              <Button
                variant="contained"
                onClick={() => setShowPinDialog(true)}
                disabled={isLoading}
                startIcon={isLoading ? <CircularProgress size={20} /> : <SendIcon />}
              >
                Send Payment
              </Button>
            )}
          </Box>
        </CardContent>
      </Card>

      <QRScanner
        open={showQRScanner}
        onClose={() => setShowQRScanner(false)}
        onScan={handleQRScan}
      />

      <TransactionPinDialog
        open={showPinDialog}
        onClose={() => setShowPinDialog(false)}
        onConfirm={handleSendPayment}
        amount={parseFloat(amount) + (fees?.totalFee || 0)}
        recipientName={recipient?.name || ''}
      />
    </>
  );
}