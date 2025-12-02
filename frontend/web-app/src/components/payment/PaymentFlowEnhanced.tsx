import React, { useState, useCallback, useEffect } from 'react';
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Button,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  CardActions,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Avatar,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Divider,
  LinearProgress,
  Tabs,
  Tab,
  InputAdornment,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  ListItemButton,
  CircularProgress,
  Tooltip,
  Badge,
  Skeleton,
  useTheme,
  alpha,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import PersonIcon from '@mui/icons-material/Person';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import QrCodeIcon from '@mui/icons-material/QrCode';
import ScheduleIcon from '@mui/icons-material/Schedule';
import RepeatIcon from '@mui/icons-material/Repeat';
import GroupIcon from '@mui/icons-material/Group';
import ReceiptIcon from '@mui/icons-material/Receipt';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import WalletIcon from '@mui/icons-material/Wallet';
import SecurityIcon from '@mui/icons-material/Security';
import CheckIcon from '@mui/icons-material/Check';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import CloseIcon from '@mui/icons-material/Close';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import CameraIcon from '@mui/icons-material/Camera';
import ContactsIcon from '@mui/icons-material/Contacts';
import SearchIcon from '@mui/icons-material/Search';
import StarIcon from '@mui/icons-material/Star';
import HistoryIcon from '@mui/icons-material/History';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { format, addDays, addWeeks, addMonths } from 'date-fns';
import toast from 'react-hot-toast';

import { useAppSelector, useAppDispatch } from '@/hooks/redux';
import { paymentService } from '@/services/paymentService';
import { walletService } from '@/services/walletService';
import { formatCurrency, formatDate } from '@/utils/formatters';
import { Contact } from '@/types/contact';
import { PaymentMethod, WalletBalance } from '@/types/wallet';
import { SendMoneyRequest, RequestMoneyRequest, GroupPaymentRequest } from '@/types/payment';

type PaymentType = 'send' | 'request' | 'group' | 'international' | 'scheduled';
type PaymentMethod = 'wallet' | 'card' | 'bank' | 'crypto';

interface EnhancedPaymentFormData {
  type: PaymentType;
  recipients: Contact[];
  amount: number;
  currency: string;
  description: string;
  category: string;
  paymentMethod: PaymentMethod;
  scheduledDate?: Date;
  recurringType?: 'none' | 'daily' | 'weekly' | 'monthly';
  splitType?: 'equal' | 'percentage' | 'amount';
  splitDetails?: { [contactId: string]: number };
  priority: 'low' | 'normal' | 'high';
  private: boolean;
  requestExpiry?: Date;
  notes: string;
  attachments?: File[];
}

const validationSchema = yup.object().shape({
  recipients: yup.array().min(1, 'At least one recipient is required'),
  amount: yup.number().positive('Amount must be positive').required('Amount is required'),
  currency: yup.string().required('Currency is required'),
  description: yup.string().required('Description is required'),
  category: yup.string().required('Category is required'),
  paymentMethod: yup.string().required('Payment method is required'),
});

const steps = [
  { key: 'type', label: 'Payment Type', icon: <SendIcon /> },
  { key: 'recipients', label: 'Recipients', icon: <PersonIcon /> },
  { key: 'amount', label: 'Amount & Details', icon: <MoneyIcon /> },
  { key: 'method', label: 'Payment Method', icon: <CardIcon /> },
  { key: 'schedule', label: 'Timing & Options', icon: <ScheduleIcon /> },
  { key: 'review', label: 'Review & Confirm', icon: <CheckIcon /> },
  { key: 'complete', label: 'Complete', icon: <CheckIcon /> },
];

const paymentTypes = [
  { 
    key: 'send', 
    title: 'Send Money', 
    description: 'Transfer money to contacts instantly',
    icon: <SendIcon />,
    color: 'primary',
  },
  { 
    key: 'request', 
    title: 'Request Money', 
    description: 'Request payment from contacts',
    icon: <ReceiptIcon />,
    color: 'secondary',
  },
  { 
    key: 'group', 
    title: 'Split Bill', 
    description: 'Split expenses among multiple people',
    icon: <GroupIcon />,
    color: 'success',
  },
  { 
    key: 'international', 
    title: 'International Transfer', 
    description: 'Send money across borders',
    icon: <TrendingUpIcon />,
    color: 'info',
  },
  { 
    key: 'scheduled', 
    title: 'Schedule Payment', 
    description: 'Set up future or recurring payments',
    icon: <ScheduleIcon />,
    color: 'warning',
  },
];

const paymentCategories = [
  'Food & Dining',
  'Transportation',
  'Shopping',
  'Entertainment',
  'Bills & Utilities',
  'Health & Medical',
  'Travel',
  'Education',
  'Personal Care',
  'Gifts & Donations',
  'Business',
  'Investment',
  'Other',
];

interface PaymentFlowEnhancedProps {
  initialType?: PaymentType;
  initialRecipients?: Contact[];
  initialAmount?: number;
  onComplete?: (paymentId: string) => void;
  onCancel?: () => void;
}

const PaymentFlowEnhanced: React.FC<PaymentFlowEnhancedProps> = ({
  initialType = 'send',
  initialRecipients = [],
  initialAmount,
  onComplete,
  onCancel,
}) => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { walletBalance } = useAppSelector((state) => state.wallet);
  const { contacts: allContacts } = useAppSelector((state) => state.user);
  
  const [activeStep, setActiveStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedContacts, setSelectedContacts] = useState<Contact[]>(initialRecipients);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([]);
  const [exchangeRates, setExchangeRates] = useState<{ [key: string]: number }>({});
  const [recentContacts, setRecentContacts] = useState<Contact[]>([]);
  const [favoriteContacts, setFavoriteContacts] = useState<Contact[]>([]);
  const [qrScannerOpen, setQrScannerOpen] = useState(false);
  const [previewMode, setPreviewMode] = useState(false);
  
  const {
    control,
    handleSubmit,
    watch,
    setValue,
    getValues,
    reset,
    formState: { errors, isValid },
  } = useForm<EnhancedPaymentFormData>({
    resolver: yupResolver(validationSchema),
    defaultValues: {
      type: initialType,
      recipients: initialRecipients,
      amount: initialAmount || 0,
      currency: 'USD',
      description: '',
      category: 'Other',
      paymentMethod: 'wallet',
      splitType: 'equal',
      priority: 'normal',
      private: false,
      recurringType: 'none',
      notes: '',
    },
  });

  const watchedValues = watch();

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    if (watchedValues.type === 'international') {
      loadExchangeRates();
    }
  }, [watchedValues.currency, watchedValues.type]);

  const loadInitialData = async () => {
    try {
      const [methods, recent, favorites] = await Promise.all([
        walletService.getPaymentMethods(),
        paymentService.getRecentContacts(),
        paymentService.getFavoriteContacts(),
      ]);
      
      setPaymentMethods(methods);
      setRecentContacts(recent);
      setFavoriteContacts(favorites);
    } catch (error) {
      console.error('Failed to load initial data:', error);
    }
  };

  const loadExchangeRates = async () => {
    try {
      const rates = await paymentService.getExchangeRates(watchedValues.currency);
      setExchangeRates(rates);
    } catch (error) {
      console.error('Failed to load exchange rates:', error);
    }
  };

  const handleNext = () => {
    if (activeStep === steps.length - 2) {
      handleSubmitPayment();
    } else {
      setActiveStep(prev => prev + 1);
    }
  };

  const handleBack = () => {
    setActiveStep(prev => prev - 1);
  };

  const handleContactSelect = (contact: Contact) => {
    const isSelected = selectedContacts.some(c => c.id === contact.id);
    if (isSelected) {
      setSelectedContacts(prev => prev.filter(c => c.id !== contact.id));
    } else {
      setSelectedContacts(prev => [...prev, contact]);
    }
    setValue('recipients', isSelected 
      ? selectedContacts.filter(c => c.id !== contact.id)
      : [...selectedContacts, contact]
    );
  };

  const handleQRScan = (data: string) => {
    try {
      const qrData = JSON.parse(data);
      if (qrData.type === 'payment_request') {
        // Handle QR payment request
        setValue('recipients', [{ id: qrData.userId, name: qrData.name, email: qrData.email }]);
        setValue('amount', qrData.amount || 0);
        setValue('description', qrData.description || '');
        setQrScannerOpen(false);
        setActiveStep(3); // Skip to amount step
      }
    } catch (error) {
      toast.error('Invalid QR code');
    }
  };

  const calculateSplitAmounts = () => {
    const { amount, splitType, splitDetails } = watchedValues;
    const recipientCount = selectedContacts.length;
    
    if (splitType === 'equal') {
      return selectedContacts.reduce((acc, contact) => {
        acc[contact.id] = amount / recipientCount;
        return acc;
      }, {} as { [key: string]: number });
    }
    
    return splitDetails || {};
  };

  const getTotalAmount = () => {
    const { amount, type } = watchedValues;
    const fees = calculateFees();
    const exchangeRate = exchangeRates[watchedValues.currency] || 1;
    
    return (amount * exchangeRate) + fees;
  };

  const calculateFees = () => {
    const { type, paymentMethod, amount } = watchedValues;
    let feePercent = 0;
    let fixedFee = 0;

    switch (type) {
      case 'international':
        feePercent = 0.025; // 2.5%
        fixedFee = 5;
        break;
      case 'send':
        if (paymentMethod === 'card') {
          feePercent = 0.029; // 2.9%
        }
        break;
      default:
        break;
    }

    return (amount * feePercent) + fixedFee;
  };

  const handleSubmitPayment = async () => {
    setLoading(true);
    try {
      const formData = getValues();
      let response;

      switch (formData.type) {
        case 'send':
          response = await paymentService.sendMoney({
            recipientId: formData.recipients[0].id,
            amount: formData.amount,
            currency: formData.currency,
            description: formData.description,
            category: formData.category,
            paymentMethodId: formData.paymentMethod,
          } as SendMoneyRequest);
          break;

        case 'request':
          response = await paymentService.requestMoney({
            recipientId: formData.recipients[0].id,
            amount: formData.amount,
            currency: formData.currency,
            description: formData.description,
            expiryDate: formData.requestExpiry,
          } as RequestMoneyRequest);
          break;

        case 'group':
          const splitAmounts = calculateSplitAmounts();
          response = await paymentService.createGroupPayment({
            recipients: formData.recipients.map(r => ({
              contactId: r.id,
              amount: splitAmounts[r.id],
            })),
            totalAmount: formData.amount,
            currency: formData.currency,
            description: formData.description,
            splitType: formData.splitType,
          } as GroupPaymentRequest);
          break;

        case 'international':
          response = await paymentService.sendInternationalTransfer({
            recipientId: formData.recipients[0].id,
            amount: formData.amount,
            currency: formData.currency,
            description: formData.description,
            paymentMethodId: formData.paymentMethod,
          });
          break;

        case 'scheduled':
          response = await paymentService.schedulePayment({
            recipientId: formData.recipients[0].id,
            amount: formData.amount,
            currency: formData.currency,
            description: formData.description,
            scheduledDate: formData.scheduledDate,
            recurringType: formData.recurringType,
            paymentMethodId: formData.paymentMethod,
          });
          break;

        default:
          throw new Error('Invalid payment type');
      }

      setActiveStep(steps.length - 1);
      toast.success('Payment completed successfully!');
      onComplete?.(response.id);
    } catch (error: any) {
      toast.error(error.message || 'Payment failed');
    } finally {
      setLoading(false);
    }
  };

  const renderPaymentTypeStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Choose Payment Type
      </Typography>
      <Grid container spacing={2}>
        {paymentTypes.map((type) => (
          <Grid item xs={12} sm={6} md={4} key={type.key}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                border: watchedValues.type === type.key ? 2 : 1,
                borderColor: watchedValues.type === type.key 
                  ? `${type.color}.main` 
                  : 'divider',
                '&:hover': {
                  elevation: 4,
                  borderColor: `${type.color}.main`,
                },
              }}
              onClick={() => setValue('type', type.key as PaymentType)}
            >
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <Avatar 
                  sx={{ 
                    bgcolor: `${type.color}.main`, 
                    mx: 'auto', 
                    mb: 2,
                    width: 56,
                    height: 56,
                  }}
                >
                  {type.icon}
                </Avatar>
                <Typography variant="h6" gutterBottom>
                  {type.title}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {type.description}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );

  const renderRecipientsStep = () => {
    const filteredContacts = allContacts.filter(contact =>
      contact.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      contact.email?.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
      <Box>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
          <Typography variant="h6">
            Select {watchedValues.type === 'group' ? 'Recipients' : 'Recipient'}
          </Typography>
          <Box display="flex" gap={1}>
            <Button
              startIcon={<QrCodeIcon />}
              onClick={() => setQrScannerOpen(true)}
            >
              Scan QR
            </Button>
            <Button
              startIcon={<ContactsIcon />}
              onClick={() => {/* Import contacts */}}
            >
              Import
            </Button>
          </Box>
        </Box>

        {/* Search Bar */}
        <TextField
          fullWidth
          placeholder="Search contacts by name or email..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
          sx={{ mb: 3 }}
        />

        {/* Selected Recipients */}
        {selectedContacts.length > 0 && (
          <Box mb={3}>
            <Typography variant="subtitle2" gutterBottom>
              Selected ({selectedContacts.length})
            </Typography>
            <Box display="flex" gap={1} flexWrap="wrap">
              {selectedContacts.map((contact) => (
                <Chip
                  key={contact.id}
                  avatar={<Avatar>{contact.name.charAt(0)}</Avatar>}
                  label={contact.name}
                  onDelete={() => handleContactSelect(contact)}
                  color="primary"
                />
              ))}
            </Box>
          </Box>
        )}

        {/* Contact Sections */}
        <Grid container spacing={3}>
          {/* Favorites */}
          {favoriteContacts.length > 0 && (
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                    <StarIcon sx={{ mr: 1, color: 'gold' }} />
                    Favorites
                  </Typography>
                  <List dense>
                    {favoriteContacts.slice(0, 5).map((contact) => (
                      <ListItemButton
                        key={contact.id}
                        onClick={() => handleContactSelect(contact)}
                        selected={selectedContacts.some(c => c.id === contact.id)}
                      >
                        <ListItemAvatar>
                          <Avatar>{contact.name.charAt(0)}</Avatar>
                        </ListItemAvatar>
                        <ListItemText
                          primary={contact.name}
                          secondary={contact.email}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                </CardContent>
              </Card>
            </Grid>
          )}

          {/* Recent */}
          {recentContacts.length > 0 && (
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                    <HistoryIcon sx={{ mr: 1 }} />
                    Recent
                  </Typography>
                  <List dense>
                    {recentContacts.slice(0, 5).map((contact) => (
                      <ListItemButton
                        key={contact.id}
                        onClick={() => handleContactSelect(contact)}
                        selected={selectedContacts.some(c => c.id === contact.id)}
                      >
                        <ListItemAvatar>
                          <Avatar>{contact.name.charAt(0)}</Avatar>
                        </ListItemAvatar>
                        <ListItemText
                          primary={contact.name}
                          secondary={`Last: ${formatDate(contact.lastPaymentDate || new Date())}`}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                </CardContent>
              </Card>
            </Grid>
          )}

          {/* All Contacts */}
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  All Contacts ({filteredContacts.length})
                </Typography>
                <List dense sx={{ maxHeight: 300, overflow: 'auto' }}>
                  {filteredContacts.map((contact) => (
                    <ListItemButton
                      key={contact.id}
                      onClick={() => handleContactSelect(contact)}
                      selected={selectedContacts.some(c => c.id === contact.id)}
                    >
                      <ListItemAvatar>
                        <Avatar>{contact.name.charAt(0)}</Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={contact.name}
                        secondary={contact.email}
                      />
                    </ListItemButton>
                  ))}
                </List>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Box>
    );
  };

  const renderAmountStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Amount & Details
      </Typography>
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" gutterBottom>
                Payment Amount
              </Typography>
              
              {/* Amount Input */}
              <Grid container spacing={2}>
                <Grid item xs={8}>
                  <Controller
                    name="amount"
                    control={control}
                    render={({ field }) => (
                      <TextField
                        {...field}
                        fullWidth
                        label="Amount"
                        type="number"
                        error={!!errors.amount}
                        helperText={errors.amount?.message}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <MoneyIcon />
                            </InputAdornment>
                          ),
                        }}
                      />
                    )}
                  />
                </Grid>
                <Grid item xs={4}>
                  <Controller
                    name="currency"
                    control={control}
                    render={({ field }) => (
                      <FormControl fullWidth>
                        <InputLabel>Currency</InputLabel>
                        <Select {...field} label="Currency">
                          <MenuItem value="USD">USD</MenuItem>
                          <MenuItem value="EUR">EUR</MenuItem>
                          <MenuItem value="GBP">GBP</MenuItem>
                          <MenuItem value="CAD">CAD</MenuItem>
                          <MenuItem value="JPY">JPY</MenuItem>
                        </Select>
                      </FormControl>
                    )}
                  />
                </Grid>
              </Grid>

              {/* Quick Amounts */}
              <Box mt={2}>
                <Typography variant="body2" gutterBottom>
                  Quick amounts:
                </Typography>
                <Box display="flex" gap={1} flexWrap="wrap">
                  {[10, 25, 50, 100, 250, 500, 1000].map((amount) => (
                    <Chip
                      key={amount}
                      label={formatCurrency(amount)}
                      onClick={() => setValue('amount', amount)}
                      clickable
                      variant={watchedValues.amount === amount ? 'filled' : 'outlined'}
                      color="primary"
                      size="small"
                    />
                  ))}
                </Box>
              </Box>

              {/* Exchange Rate Info */}
              {watchedValues.type === 'international' && exchangeRates[watchedValues.currency] && (
                <Alert severity="info" sx={{ mt: 2 }}>
                  Exchange Rate: 1 USD = {exchangeRates[watchedValues.currency]} {watchedValues.currency}
                </Alert>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" gutterBottom>
                Description & Category
              </Typography>
              
              <Controller
                name="description"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="What's this for?"
                    multiline
                    rows={3}
                    error={!!errors.description}
                    helperText={errors.description?.message}
                    sx={{ mb: 2 }}
                  />
                )}
              />

              <Controller
                name="category"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth sx={{ mb: 2 }}>
                    <InputLabel>Category</InputLabel>
                    <Select {...field} label="Category">
                      {paymentCategories.map((category) => (
                        <MenuItem key={category} value={category}>
                          {category}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                )}
              />

              <Controller
                name="notes"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Additional Notes (Optional)"
                    multiline
                    rows={2}
                  />
                )}
              />
            </CardContent>
          </Card>
        </Grid>

        {/* Split Details for Group Payments */}
        {watchedValues.type === 'group' && selectedContacts.length > 0 && (
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  Split Details
                </Typography>
                
                <Box mb={2}>
                  <Controller
                    name="splitType"
                    control={control}
                    render={({ field }) => (
                      <FormControl>
                        <InputLabel>Split Type</InputLabel>
                        <Select {...field} label="Split Type">
                          <MenuItem value="equal">Equal Split</MenuItem>
                          <MenuItem value="percentage">By Percentage</MenuItem>
                          <MenuItem value="amount">By Amount</MenuItem>
                        </Select>
                      </FormControl>
                    )}
                  />
                </Box>

                <Grid container spacing={2}>
                  {selectedContacts.map((contact, index) => {
                    const splitAmounts = calculateSplitAmounts();
                    const contactAmount = splitAmounts[contact.id] || 0;
                    
                    return (
                      <Grid item xs={12} sm={6} md={4} key={contact.id}>
                        <Card variant="outlined">
                          <CardContent>
                            <Box display="flex" alignItems="center" mb={1}>
                              <Avatar sx={{ mr: 1, width: 32, height: 32 }}>
                                {contact.name.charAt(0)}
                              </Avatar>
                              <Typography variant="body2">
                                {contact.name}
                              </Typography>
                            </Box>
                            <Typography variant="h6" color="primary">
                              {formatCurrency(contactAmount)}
                            </Typography>
                            {watchedValues.splitType === 'percentage' && (
                              <Typography variant="caption" color="text.secondary">
                                {((contactAmount / watchedValues.amount) * 100).toFixed(1)}%
                              </Typography>
                            )}
                          </CardContent>
                        </Card>
                      </Grid>
                    );
                  })}
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        )}

        {/* Fee Information */}
        <Grid item xs={12}>
          <Alert 
            severity="info" 
            sx={{ 
              bgcolor: alpha(theme.palette.info.main, 0.1),
              border: `1px solid ${alpha(theme.palette.info.main, 0.3)}`,
            }}
          >
            <Typography variant="body2">
              <strong>Transaction Summary:</strong><br />
              Amount: {formatCurrency(watchedValues.amount)} {watchedValues.currency}<br />
              Fees: {formatCurrency(calculateFees())}<br />
              <strong>Total: {formatCurrency(getTotalAmount())}</strong>
            </Typography>
          </Alert>
        </Grid>
      </Grid>
    </Box>
  );

  const renderPaymentMethodStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Payment Method
      </Typography>
      
      <Grid container spacing={3}>
        {/* Wallet Balance */}
        <Grid item xs={12}>
          <Card 
            sx={{ 
              bgcolor: alpha(theme.palette.primary.main, 0.1),
              border: `2px solid ${alpha(theme.palette.primary.main, 0.3)}`,
            }}
          >
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box display="flex" alignItems="center">
                  <WalletIcon sx={{ mr: 2, color: 'primary.main' }} />
                  <Box>
                    <Typography variant="subtitle1">
                      Waqiti Wallet
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Available: {formatCurrency(walletBalance?.currentBalance || 0)}
                    </Typography>
                  </Box>
                </Box>
                <Box textAlign="right">
                  <Typography variant="body2" color="success.main">
                    Free transfers
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Instant
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Payment Methods */}
        {paymentMethods.map((method, index) => (
          <Grid item xs={12} sm={6} key={method.id}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                border: watchedValues.paymentMethod === method.id ? 2 : 1,
                borderColor: watchedValues.paymentMethod === method.id 
                  ? 'primary.main' 
                  : 'divider',
                '&:hover': {
                  elevation: 4,
                  borderColor: 'primary.main',
                },
              }}
              onClick={() => setValue('paymentMethod', method.id)}
            >
              <CardContent>
                <Box display="flex" alignItems="center" justifyContent="space-between">
                  <Box display="flex" alignItems="center">
                    {method.type === 'CARD' ? (
                      <CardIcon sx={{ mr: 2 }} />
                    ) : (
                      <BankIcon sx={{ mr: 2 }} />
                    )}
                    <Box>
                      <Typography variant="subtitle2">
                        {method.type === 'CARD' 
                          ? `•••• ${method.lastFour}` 
                          : method.bankName
                        }
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {method.brand || method.accountName}
                      </Typography>
                    </Box>
                  </Box>
                  {method.isDefault && (
                    <Chip label="Default" size="small" color="primary" />
                  )}
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Fee breakdown */}
      {calculateFees() > 0 && (
        <Alert severity="warning" sx={{ mt: 2 }}>
          <Typography variant="body2">
            This payment method includes additional fees: {formatCurrency(calculateFees())}
          </Typography>
        </Alert>
      )}
    </Box>
  );

  const renderScheduleStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Timing & Options
      </Typography>
      
      <Grid container spacing={3}>
        {/* Scheduling Options */}
        {(watchedValues.type === 'send' || watchedValues.type === 'scheduled') && (
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  Schedule Payment
                </Typography>
                
                <Controller
                  name="scheduledDate"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Send Date"
                      type="datetime-local"
                      InputLabelProps={{ shrink: true }}
                      sx={{ mb: 2 }}
                      value={field.value ? format(new Date(field.value), "yyyy-MM-dd'T'HH:mm") : ''}
                      onChange={(e) => field.onChange(new Date(e.target.value))}
                    />
                  )}
                />

                <Controller
                  name="recurringType"
                  control={control}
                  render={({ field }) => (
                    <FormControl fullWidth>
                      <InputLabel>Recurring</InputLabel>
                      <Select {...field} label="Recurring">
                        <MenuItem value="none">One-time</MenuItem>
                        <MenuItem value="daily">Daily</MenuItem>
                        <MenuItem value="weekly">Weekly</MenuItem>
                        <MenuItem value="monthly">Monthly</MenuItem>
                      </Select>
                    </FormControl>
                  )}
                />
              </CardContent>
            </Card>
          </Grid>
        )}

        {/* Priority & Privacy */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" gutterBottom>
                Priority & Privacy
              </Typography>
              
              <Controller
                name="priority"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth sx={{ mb: 2 }}>
                    <InputLabel>Priority</InputLabel>
                    <Select {...field} label="Priority">
                      <MenuItem value="low">Low - Standard processing</MenuItem>
                      <MenuItem value="normal">Normal - Within 24 hours</MenuItem>
                      <MenuItem value="high">High - Immediate</MenuItem>
                    </Select>
                  </FormControl>
                )}
              />

              <Controller
                name="private"
                control={control}
                render={({ field }) => (
                  <Box display="flex" alignItems="center">
                    <Switch {...field} checked={field.value} />
                    <Typography variant="body2" sx={{ ml: 1 }}>
                      Private payment (hide from activity feed)
                    </Typography>
                  </Box>
                )}
              />
            </CardContent>
          </Card>
        </Grid>

        {/* Request-specific options */}
        {watchedValues.type === 'request' && (
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  Request Options
                </Typography>
                
                <Controller
                  name="requestExpiry"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Expires On"
                      type="date"
                      InputLabelProps={{ shrink: true }}
                      sx={{ mb: 2 }}
                      value={field.value ? format(new Date(field.value), 'yyyy-MM-dd') : ''}
                      onChange={(e) => field.onChange(new Date(e.target.value))}
                    />
                  )}
                />

                <Typography variant="body2" color="text.secondary">
                  Recipients will be notified via email and push notification.
                  They can accept or decline your request.
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        )}
      </Grid>
    </Box>
  );

  const renderReviewStep = () => {
    const totalAmount = getTotalAmount();
    const fees = calculateFees();
    
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          Review & Confirm
        </Typography>
        
        <Grid container spacing={3}>
          {/* Payment Summary */}
          <Grid item xs={12} md={8}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Payment Summary
                </Typography>
                
                <Box mb={3}>
                  <Grid container spacing={2}>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">
                        Type
                      </Typography>
                      <Typography variant="body1">
                        {paymentTypes.find(t => t.key === watchedValues.type)?.title}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">
                        Recipients
                      </Typography>
                      <Typography variant="body1">
                        {selectedContacts.length === 1 
                          ? selectedContacts[0].name
                          : `${selectedContacts.length} people`
                        }
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">
                        Amount
                      </Typography>
                      <Typography variant="h6" color="primary">
                        {formatCurrency(watchedValues.amount)} {watchedValues.currency}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">
                        Description
                      </Typography>
                      <Typography variant="body1">
                        {watchedValues.description}
                      </Typography>
                    </Grid>
                  </Grid>
                </Box>

                <Divider />
                
                <Box mt={2}>
                  <Grid container spacing={1}>
                    <Grid item xs={6}>
                      <Typography variant="body2">
                        Subtotal:
                      </Typography>
                    </Grid>
                    <Grid item xs={6} textAlign="right">
                      <Typography variant="body2">
                        {formatCurrency(watchedValues.amount)}
                      </Typography>
                    </Grid>
                    
                    {fees > 0 && (
                      <>
                        <Grid item xs={6}>
                          <Typography variant="body2">
                            Fees:
                          </Typography>
                        </Grid>
                        <Grid item xs={6} textAlign="right">
                          <Typography variant="body2">
                            {formatCurrency(fees)}
                          </Typography>
                        </Grid>
                      </>
                    )}
                    
                    <Grid item xs={12}>
                      <Divider sx={{ my: 1 }} />
                    </Grid>
                    
                    <Grid item xs={6}>
                      <Typography variant="h6">
                        Total:
                      </Typography>
                    </Grid>
                    <Grid item xs={6} textAlign="right">
                      <Typography variant="h6" color="primary">
                        {formatCurrency(totalAmount)}
                      </Typography>
                    </Grid>
                  </Grid>
                </Box>
              </CardContent>
            </Card>
          </Grid>

          {/* Recipients Detail */}
          <Grid item xs={12} md={4}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  {watchedValues.type === 'group' ? 'Split Details' : 'Recipient'}
                </Typography>
                
                {watchedValues.type === 'group' ? (
                  <List dense>
                    {selectedContacts.map((contact) => {
                      const splitAmounts = calculateSplitAmounts();
                      const contactAmount = splitAmounts[contact.id] || 0;
                      
                      return (
                        <ListItem key={contact.id}>
                          <ListItemAvatar>
                            <Avatar sx={{ width: 32, height: 32 }}>
                              {contact.name.charAt(0)}
                            </Avatar>
                          </ListItemAvatar>
                          <ListItemText
                            primary={contact.name}
                            secondary={formatCurrency(contactAmount)}
                          />
                        </ListItem>
                      );
                    })}
                  </List>
                ) : (
                  selectedContacts.length > 0 && (
                    <Box display="flex" alignItems="center">
                      <Avatar sx={{ mr: 2, width: 48, height: 48 }}>
                        {selectedContacts[0].name.charAt(0)}
                      </Avatar>
                      <Box>
                        <Typography variant="subtitle1">
                          {selectedContacts[0].name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {selectedContacts[0].email}
                        </Typography>
                      </Box>
                    </Box>
                  )
                )}
              </CardContent>
            </Card>
          </Grid>

          {/* Security Notice */}
          <Grid item xs={12}>
            <Alert severity="info" icon={<SecurityIcon />}>
              <Typography variant="body2">
                Your payment is secured with bank-level encryption. 
                {watchedValues.type === 'send' ? 'This transaction cannot be reversed once confirmed.' : ''}
                {watchedValues.type === 'request' ? 'The recipient can accept or decline your request.' : ''}
              </Typography>
            </Alert>
          </Grid>
        </Grid>
      </Box>
    );
  };

  const renderCompleteStep = () => (
    <Box textAlign="center" py={4}>
      <Avatar 
        sx={{ 
          bgcolor: 'success.main', 
          width: 80, 
          height: 80, 
          mx: 'auto', 
          mb: 2,
        }}
      >
        <CheckIcon sx={{ fontSize: 48 }} />
      </Avatar>
      
      <Typography variant="h5" gutterBottom>
        {watchedValues.type === 'request' ? 'Request Sent!' : 'Payment Complete!'}
      </Typography>
      
      <Typography variant="body1" color="text.secondary" paragraph>
        {watchedValues.type === 'request' 
          ? `Your payment request has been sent to ${selectedContacts[0]?.name}. They will be notified via email and push notification.`
          : `Your ${watchedValues.type} payment of ${formatCurrency(watchedValues.amount)} has been processed successfully.`
        }
      </Typography>

      <Box mt={4} display="flex" justifyContent="center" gap={2}>
        <Button
          variant="outlined"
          onClick={() => {
            reset();
            setActiveStep(0);
            setSelectedContacts([]);
          }}
        >
          Send Another
        </Button>
        <Button
          variant="contained"
          onClick={() => onComplete?.('payment-id')}
        >
          Done
        </Button>
      </Box>
    </Box>
  );

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0: return renderPaymentTypeStep();
      case 1: return renderRecipientsStep();
      case 2: return renderAmountStep();
      case 3: return renderPaymentMethodStep();
      case 4: return renderScheduleStep();
      case 5: return renderReviewStep();
      case 6: return renderCompleteStep();
      default: return null;
    }
  };

  const canProceed = () => {
    switch (activeStep) {
      case 0: return watchedValues.type;
      case 1: return selectedContacts.length > 0;
      case 2: return watchedValues.amount > 0 && watchedValues.description;
      case 3: return watchedValues.paymentMethod;
      case 4: return true;
      case 5: return isValid;
      default: return false;
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5">
          {watchedValues.type ? paymentTypes.find(t => t.key === watchedValues.type)?.title : 'New Payment'}
        </Typography>
        <IconButton onClick={onCancel}>
          <CloseIcon />
        </IconButton>
      </Box>

      {/* Stepper */}
      <Box mb={4}>
        <Stepper activeStep={activeStep} alternativeLabel>
          {steps.map((step, index) => (
            <Step key={step.key}>
              <StepLabel 
                icon={step.icon}
                sx={{
                  '& .MuiStepLabel-iconContainer': {
                    '& .MuiSvgIcon-root': {
                      fontSize: 24,
                    },
                  },
                }}
              >
                {step.label}
              </StepLabel>
            </Step>
          ))}
        </Stepper>
      </Box>

      {/* Progress */}
      {loading && <LinearProgress sx={{ mb: 2 }} />}

      {/* Content */}
      <Box mb={4}>
        {renderStepContent(activeStep)}
      </Box>

      {/* Actions */}
      {activeStep < steps.length - 1 && (
        <Box display="flex" justifyContent="space-between">
          <Button
            disabled={activeStep === 0}
            onClick={handleBack}
          >
            Back
          </Button>
          <Button
            variant="contained"
            onClick={handleNext}
            disabled={!canProceed() || loading}
            endIcon={loading ? <CircularProgress size={20} /> : null}
          >
            {activeStep === steps.length - 2 ? 'Confirm & Send' : 'Next'}
          </Button>
        </Box>
      )}

      {/* QR Scanner Dialog */}
      <Dialog
        open={qrScannerOpen}
        onClose={() => setQrScannerOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Scan QR Code</DialogTitle>
        <DialogContent>
          {/* QR Scanner would be implemented here */}
          <Box 
            sx={{ 
              height: 300, 
              bgcolor: 'grey.100', 
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center',
              borderRadius: 1,
            }}
          >
            <Typography color="text.secondary">
              QR Scanner Component
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQrScannerOpen(false)}>Cancel</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PaymentFlowEnhanced;