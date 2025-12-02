import React, { useState, useEffect } from 'react';
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  Button,
  Typography,
  Paper,
  TextField,
  Grid,
  Card,
  CardContent,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Chip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Divider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  InputAdornment,
  Tooltip,
  useTheme,
  alpha,
  Skeleton,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import PersonIcon from '@mui/icons-material/Person';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import PhoneIcon from '@mui/icons-material/Phone';
import QrCodeIcon from '@mui/icons-material/QrCode';
import SearchIcon from '@mui/icons-material/Search';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import InfoIcon from '@mui/icons-material/Info';
import SecurityIcon from '@mui/icons-material/Security';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ReceiptIcon from '@mui/icons-material/Receipt';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import HistoryIcon from '@mui/icons-material/History';
import ContactPhoneIcon from '@mui/icons-material/ContactPhone';
import EmailIcon from '@mui/icons-material/Email';;
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { useAppDispatch, useAppSelector } from '../../hooks/redux';
import { initiatePayment, confirmPayment, cancelPayment } from '../../store/slices/paymentSlice';
import { searchUsers } from '../../services/userService';
import { validatePayment } from '../../utils/validation';
import QRScanner from '../common/QRScanner';

interface PaymentRecipient {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  phone?: string;
  avatar?: string;
  isFavorite?: boolean;
  lastPayment?: string;
}

interface PaymentMethod {
  id: string;
  type: 'BANK_ACCOUNT' | 'CARD' | 'WALLET';
  name: string;
  lastFour: string;
  brand?: string;
  isDefault: boolean;
  icon: React.ReactElement;
}

const PaymentFlow: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { processing, currentPayment } = useAppSelector((state) => state.payment);
  
  const [activeStep, setActiveStep] = useState(0);
  const [showQRScanner, setShowQRScanner] = useState(false);
  const [showAddNote, setShowAddNote] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<PaymentRecipient[]>([]);
  const [searching, setSearching] = useState(false);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([]);
  const [recentRecipients, setRecentRecipients] = useState<PaymentRecipient[]>([]);
  const [favorites, setFavorites] = useState<PaymentRecipient[]>([]);
  
  // Payment data
  const [paymentData, setPaymentData] = useState({
    recipient: null as PaymentRecipient | null,
    amount: '',
    currency: 'USD',
    paymentMethod: null as PaymentMethod | null,
    note: '',
    isPrivate: false,
    scheduledDate: null as Date | null,
    splitBill: false,
    requestId: null as string | null,
  });
  
  const [errors, setErrors] = useState<Record<string, string>>({});
  
  const steps = ['Select Recipient', 'Enter Amount', 'Payment Method', 'Review & Send'];
  
  useEffect(() => {
    loadPaymentMethods();
    loadRecentRecipients();
    loadFavorites();
  }, []);
  
  useEffect(() => {
    if (searchQuery.length >= 2) {
      performSearch();
    } else {
      setSearchResults([]);
    }
  }, [searchQuery]);
  
  const loadPaymentMethods = async () => {
    try {
      const response = await fetch('/api/payment-methods');
      const methods = await response.json();
      
      const formattedMethods: PaymentMethod[] = methods.map((method: any) => ({
        id: method.id,
        type: method.type,
        name: method.nickname || `${method.brand} ****${method.lastFour}`,
        lastFour: method.lastFour,
        brand: method.brand,
        isDefault: method.isDefault,
        icon: getPaymentMethodIcon(method.type),
      }));
      
      setPaymentMethods(formattedMethods);
      
      // Set default payment method
      const defaultMethod = formattedMethods.find(m => m.isDefault);
      if (defaultMethod) {
        setPaymentData(prev => ({ ...prev, paymentMethod: defaultMethod }));
      }
    } catch (error) {
      console.error('Failed to load payment methods:', error);
    }
  };
  
  const loadRecentRecipients = async () => {
    try {
      const response = await fetch('/api/payments/recent-recipients');
      const recipients = await response.json();
      setRecentRecipients(recipients);
    } catch (error) {
      console.error('Failed to load recent recipients:', error);
    }
  };
  
  const loadFavorites = async () => {
    try {
      const response = await fetch('/api/users/favorites');
      const favs = await response.json();
      setFavorites(favs);
    } catch (error) {
      console.error('Failed to load favorites:', error);
    }
  };
  
  const performSearch = async () => {
    setSearching(true);
    try {
      const results = await searchUsers(searchQuery);
      setSearchResults(results.filter((u: any) => u.id !== user?.id));
    } catch (error) {
      console.error('Search failed:', error);
    } finally {
      setSearching(false);
    }
  };
  
  const handleNext = () => {
    const validationErrors = validateStep(activeStep);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }
    
    setErrors({});
    
    if (activeStep === steps.length - 1) {
      handleSendPayment();
    } else {
      setActiveStep((prevStep) => prevStep + 1);
    }
  };
  
  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };
  
  const validateStep = (step: number): Record<string, string> => {
    const errors: Record<string, string> = {};
    
    switch (step) {
      case 0: // Select Recipient
        if (!paymentData.recipient) {
          errors.recipient = 'Please select a recipient';
        }
        break;
        
      case 1: // Enter Amount
        if (!paymentData.amount || parseFloat(paymentData.amount) <= 0) {
          errors.amount = 'Please enter a valid amount';
        } else if (parseFloat(paymentData.amount) > 10000) {
          errors.amount = 'Amount exceeds maximum limit of $10,000';
        }
        break;
        
      case 2: // Payment Method
        if (!paymentData.paymentMethod) {
          errors.paymentMethod = 'Please select a payment method';
        }
        break;
    }
    
    return errors;
  };
  
  const handleSendPayment = async () => {
    try {
      const paymentRequest = {
        recipientId: paymentData.recipient!.id,
        amount: parseFloat(paymentData.amount),
        currency: paymentData.currency,
        paymentMethodId: paymentData.paymentMethod!.id,
        note: paymentData.note,
        isPrivate: paymentData.isPrivate,
        scheduledDate: paymentData.scheduledDate,
        requestId: paymentData.requestId,
      };
      
      const result = await dispatch(initiatePayment(paymentRequest)).unwrap();
      
      // Navigate to success page
      navigate(`/payment/success/${result.transactionId}`);
    } catch (error: any) {
      console.error('Payment failed:', error);
      // Handle error (show error dialog, etc.)
    }
  };
  
  const handleQRScan = (data: string) => {
    try {
      const qrData = JSON.parse(data);
      if (qrData.type === 'payment_request' && qrData.userId) {
        // Find user by ID and set as recipient
        // This would typically make an API call
        setShowQRScanner(false);
      }
    } catch (error) {
      console.error('Invalid QR code:', error);
    }
  };
  
  const toggleFavorite = async (recipient: PaymentRecipient) => {
    try {
      const method = recipient.isFavorite ? 'DELETE' : 'POST';
      await fetch(`/api/users/favorites/${recipient.id}`, { method });
      
      // Update local state
      if (recipient.isFavorite) {
        setFavorites(prev => prev.filter(f => f.id !== recipient.id));
      } else {
        setFavorites(prev => [...prev, { ...recipient, isFavorite: true }]);
      }
      
      // Update recipient
      recipient.isFavorite = !recipient.isFavorite;
    } catch (error) {
      console.error('Failed to update favorite:', error);
    }
  };
  
  const getPaymentMethodIcon = (type: string): React.ReactElement => {
    switch (type) {
      case 'BANK_ACCOUNT':
        return <AccountBalanceIcon />;
      case 'CARD':
        return <CreditCardIcon />;
      case 'WALLET':
        return <AccountBalanceIcon />;
      default:
        return <AttachMoneyIcon />;
    }
  };
  
  // Step renderers
  
  const renderRecipientStep = () => (
    <Box>
      {/* Search Bar */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs>
            <TextField
              fullWidth
              placeholder="Search by username, email, or phone..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
                endAdornment: searching && (
                  <InputAdornment position="end">
                    <CircularProgress size={20} />
                  </InputAdornment>
                ),
              }}
            />
          </Grid>
          <Grid item>
            <Button
              variant="outlined"
              startIcon={<QrCodeIcon />}
              onClick={() => setShowQRScanner(true)}
            >
              Scan QR
            </Button>
          </Grid>
        </Grid>
      </Paper>
      
      {/* Search Results */}
      {searchResults.length > 0 && (
        <Box mb={3}>
          <Typography variant="subtitle2" gutterBottom>
            Search Results
          </Typography>
          <List>
            {searchResults.map((recipient) => (
              <RecipientListItem
                key={recipient.id}
                recipient={recipient}
                selected={paymentData.recipient?.id === recipient.id}
                onSelect={() => setPaymentData({ ...paymentData, recipient })}
                onToggleFavorite={() => toggleFavorite(recipient)}
              />
            ))}
          </List>
        </Box>
      )}
      
      {/* Favorites */}
      {favorites.length > 0 && (
        <Box mb={3}>
          <Typography variant="subtitle2" gutterBottom>
            Favorites
          </Typography>
          <Grid container spacing={2}>
            {favorites.map((recipient) => (
              <Grid item xs={6} sm={4} md={3} key={recipient.id}>
                <Card
                  sx={{
                    cursor: 'pointer',
                    border: paymentData.recipient?.id === recipient.id ? 2 : 0,
                    borderColor: 'primary.main',
                  }}
                  onClick={() => setPaymentData({ ...paymentData, recipient })}
                >
                  <CardContent sx={{ textAlign: 'center' }}>
                    <Avatar sx={{ mx: 'auto', mb: 1 }}>
                      {recipient.avatar ? (
                        <img src={recipient.avatar} alt={recipient.displayName} />
                      ) : (
                        <PersonIcon />
                      )}
                    </Avatar>
                    <Typography variant="body2" noWrap>
                      {recipient.displayName}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      @{recipient.username}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}
      
      {/* Recent Recipients */}
      {recentRecipients.length > 0 && (
        <Box>
          <Typography variant="subtitle2" gutterBottom>
            Recent
          </Typography>
          <List>
            {recentRecipients.map((recipient) => (
              <RecipientListItem
                key={recipient.id}
                recipient={recipient}
                selected={paymentData.recipient?.id === recipient.id}
                onSelect={() => setPaymentData({ ...paymentData, recipient })}
                onToggleFavorite={() => toggleFavorite(recipient)}
              />
            ))}
          </List>
        </Box>
      )}
    </Box>
  );
  
  const renderAmountStep = () => (
    <Box>
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <Typography variant="h6" gutterBottom>
          Sending to {paymentData.recipient?.displayName}
        </Typography>
        
        <Box sx={{ my: 4 }}>
          <TextField
            autoFocus
            type="number"
            value={paymentData.amount}
            onChange={(e) => setPaymentData({ ...paymentData, amount: e.target.value })}
            error={!!errors.amount}
            helperText={errors.amount}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Typography variant="h4">$</Typography>
                </InputAdornment>
              ),
              sx: { fontSize: '3rem' },
            }}
            inputProps={{
              style: { textAlign: 'center', fontSize: '3rem' },
              step: '0.01',
              min: '0',
            }}
          />
        </Box>
        
        {/* Quick amounts */}
        <Grid container spacing={1} sx={{ mb: 3 }}>
          {[5, 10, 20, 50, 100].map((amount) => (
            <Grid item key={amount}>
              <Chip
                label={`$${amount}`}
                onClick={() => setPaymentData({ ...paymentData, amount: amount.toString() })}
                color={paymentData.amount === amount.toString() ? 'primary' : 'default'}
                variant={paymentData.amount === amount.toString() ? 'filled' : 'outlined'}
              />
            </Grid>
          ))}
        </Grid>
        
        {/* Add note */}
        <Button
          startIcon={<AddIcon />}
          onClick={() => setShowAddNote(true)}
          sx={{ mb: 2 }}
        >
          Add Note
        </Button>
        
        {paymentData.note && (
          <Alert severity="info" sx={{ mt: 2 }}>
            Note: {paymentData.note}
          </Alert>
        )}
        
        {/* Privacy toggle */}
        <FormControl component="fieldset" sx={{ mt: 2 }}>
          <Typography variant="body2" color="textSecondary">
            <IconButton
              size="small"
              onClick={() => setPaymentData({ ...paymentData, isPrivate: !paymentData.isPrivate })}
            >
              {paymentData.isPrivate ? <SecurityIcon /> : <PersonIcon />}
            </IconButton>
            {paymentData.isPrivate ? 'Private' : 'Public'} payment
          </Typography>
        </FormControl>
      </Paper>
    </Box>
  );
  
  const renderPaymentMethodStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Select Payment Method
      </Typography>
      
      <List>
        {paymentMethods.map((method) => (
          <ListItem
            key={method.id}
            button
            selected={paymentData.paymentMethod?.id === method.id}
            onClick={() => setPaymentData({ ...paymentData, paymentMethod: method })}
            sx={{
              border: 1,
              borderColor: paymentData.paymentMethod?.id === method.id 
                ? 'primary.main' 
                : 'divider',
              borderRadius: 1,
              mb: 1,
            }}
          >
            <ListItemAvatar>
              <Avatar sx={{ bgcolor: alpha(theme.palette.primary.main, 0.1) }}>
                {method.icon}
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary={method.name}
              secondary={`${method.type} • ****${method.lastFour}`}
            />
            {method.isDefault && (
              <Chip label="Default" size="small" color="primary" />
            )}
          </ListItem>
        ))}
      </List>
      
      <Button
        fullWidth
        variant="outlined"
        startIcon={<AddIcon />}
        sx={{ mt: 2 }}
        onClick={() => navigate('/payment-methods/add')}
      >
        Add Payment Method
      </Button>
      
      {errors.paymentMethod && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {errors.paymentMethod}
        </Alert>
      )}
    </Box>
  );
  
  const renderReviewStep = () => (
    <Box>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          Review Payment
        </Typography>
        
        <List>
          <ListItem>
            <ListItemAvatar>
              <Avatar>
                {paymentData.recipient?.avatar ? (
                  <img src={paymentData.recipient.avatar} alt="" />
                ) : (
                  <PersonIcon />
                )}
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary="Recipient"
              secondary={`${paymentData.recipient?.displayName} (@${paymentData.recipient?.username})`}
            />
          </ListItem>
          
          <Divider />
          
          <ListItem>
            <ListItemAvatar>
              <Avatar>
                <AttachMoneyIcon />
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary="Amount"
              secondary={`$${paymentData.amount} ${paymentData.currency}`}
            />
          </ListItem>
          
          <Divider />
          
          <ListItem>
            <ListItemAvatar>
              <Avatar>
                {paymentData.paymentMethod?.icon}
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary="Payment Method"
              secondary={paymentData.paymentMethod?.name}
            />
          </ListItem>
          
          {paymentData.note && (
            <>
              <Divider />
              <ListItem>
                <ListItemAvatar>
                  <Avatar>
                    <ReceiptIcon />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary="Note"
                  secondary={paymentData.note}
                />
              </ListItem>
            </>
          )}
        </List>
        
        <Alert severity="info" sx={{ mt: 2 }}>
          <Typography variant="body2">
            By sending this payment, you agree to our terms of service and privacy policy.
          </Typography>
        </Alert>
      </Paper>
    </Box>
  );
  
  const getStepContent = (step: number) => {
    switch (step) {
      case 0:
        return renderRecipientStep();
      case 1:
        return renderAmountStep();
      case 2:
        return renderPaymentMethodStep();
      case 3:
        return renderReviewStep();
      default:
        return 'Unknown step';
    }
  };
  
  return (
    <Box>
      <Paper sx={{ p: 3 }}>
        <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
        
        <Box sx={{ mt: 3, mb: 3, minHeight: 400 }}>
          {getStepContent(activeStep)}
        </Box>
        
        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Button
            disabled={activeStep === 0}
            onClick={handleBack}
          >
            Back
          </Button>
          <Button
            variant="contained"
            onClick={handleNext}
            disabled={processing}
            startIcon={processing ? <CircularProgress size={20} /> : 
                     activeStep === steps.length - 1 ? <SendIcon /> : null}
          >
            {activeStep === steps.length - 1 ? 'Send Payment' : 'Next'}
          </Button>
        </Box>
      </Paper>
      
      {/* QR Scanner Dialog */}
      <Dialog
        open={showQRScanner}
        onClose={() => setShowQRScanner(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Scan Payment QR Code</DialogTitle>
        <DialogContent>
          <QRScanner onScan={handleQRScan} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowQRScanner(false)}>Cancel</Button>
        </DialogActions>
      </Dialog>
      
      {/* Add Note Dialog */}
      <Dialog
        open={showAddNote}
        onClose={() => setShowAddNote(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Add Note</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            rows={3}
            value={paymentData.note}
            onChange={(e) => setPaymentData({ ...paymentData, note: e.target.value })}
            placeholder="What's this payment for?"
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowAddNote(false)}>Cancel</Button>
          <Button onClick={() => setShowAddNote(false)} variant="contained">
            Add
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Recipient List Item Component
const RecipientListItem: React.FC<{
  recipient: PaymentRecipient;
  selected: boolean;
  onSelect: () => void;
  onToggleFavorite: () => void;
}> = ({ recipient, selected, onSelect, onToggleFavorite }) => (
  <ListItem
    button
    selected={selected}
    onClick={onSelect}
    sx={{
      border: 1,
      borderColor: selected ? 'primary.main' : 'divider',
      borderRadius: 1,
      mb: 1,
    }}
  >
    <ListItemAvatar>
      <Avatar>
        {recipient.avatar ? (
          <img src={recipient.avatar} alt={recipient.displayName} />
        ) : (
          <PersonIcon />
        )}
      </Avatar>
    </ListItemAvatar>
    <ListItemText
      primary={recipient.displayName}
      secondary={
        <Box>
          <Typography variant="caption" display="block">
            @{recipient.username}
          </Typography>
          {recipient.lastPayment && (
            <Typography variant="caption" color="textSecondary">
              Last payment: {format(new Date(recipient.lastPayment), 'MMM d, yyyy')}
            </Typography>
          )}
        </Box>
      }
    />
    <ListItemSecondaryAction>
      <IconButton edge="end" onClick={onToggleFavorite}>
        {recipient.isFavorite ? (
          <FavoriteIcon color="error" />
        ) : (
          <FavoriteBorderIcon />
        )}
      </IconButton>
    </ListItemSecondaryAction>
  </ListItem>
);

export default PaymentFlow;