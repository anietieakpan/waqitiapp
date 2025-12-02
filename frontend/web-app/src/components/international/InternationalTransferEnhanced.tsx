import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Grid,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Avatar,
  Divider,
  InputAdornment,
  IconButton,
  Tooltip,
  LinearProgress,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Tabs,
  Tab,
  Badge,
  useTheme,
  alpha,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import GlobalIcon from '@mui/icons-material/Public';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SecurityIcon from '@mui/icons-material/Security';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import RefreshIcon from '@mui/icons-material/Refresh';
import CalculateIcon from '@mui/icons-material/Calculate';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import FlagIcon from '@mui/icons-material/Flag';
import LocationIcon from '@mui/icons-material/LocationOn';
import PhoneIcon from '@mui/icons-material/Phone';
import EmailIcon from '@mui/icons-material/Email';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import ReceiptIcon from '@mui/icons-material/Receipt';
import TimelineIcon from '@mui/icons-material/Timeline';
import TrackIcon from '@mui/icons-material/Track';
import HistoryIcon from '@mui/icons-material/History';
import CompareIcon from '@mui/icons-material/Compare';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import StarIcon from '@mui/icons-material/Star';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import CopyIcon from '@mui/icons-material/FileCopy';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { format, addMinutes, differenceInMinutes } from 'date-fns';
import toast from 'react-hot-toast';

import { formatCurrency, formatDate, formatTimeAgo } from '@/utils/formatters';
import { useAppSelector, useAppDispatch } from '@/hooks/redux';

interface Country {
  code: string;
  name: string;
  currency: string;
  currencySymbol: string;
  flag: string;
  supportedMethods: DeliveryMethod[];
  timeZone: string;
  processingTime: {
    min: number;
    max: number;
    unit: 'minutes' | 'hours' | 'days';
  };
  restrictions?: {
    maxAmount?: number;
    minAmount?: number;
    requiresKyc: boolean;
    blockedReasons?: string[];
  };
}

interface ExchangeRate {
  from: string;
  to: string;
  rate: number;
  spread: number;
  fee: number;
  lastUpdated: string;
  trend: 'UP' | 'DOWN' | 'STABLE';
  marketRate: number;
  waqitiRate: number;
}

interface DeliveryMethod {
  id: string;
  type: 'BANK_DEPOSIT' | 'CASH_PICKUP' | 'MOBILE_WALLET' | 'CARD_DEPOSIT' | 'HOME_DELIVERY';
  name: string;
  description: string;
  processingTime: {
    min: number;
    max: number;
    unit: 'minutes' | 'hours' | 'days';
  };
  fee: {
    type: 'FIXED' | 'PERCENTAGE';
    amount: number;
    currency: string;
  };
  requirements: string[];
  availability: {
    countries: string[];
    schedule?: {
      timezone: string;
      hours: { open: string; close: string };
      days: string[];
    };
  };
}

interface Recipient {
  id: string;
  firstName: string;
  lastName: string;
  email?: string;
  phone: string;
  address: {
    street: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
  };
  bankDetails?: {
    accountNumber: string;
    bankName: string;
    bankCode: string;
    swiftCode?: string;
    iban?: string;
    routingNumber?: string;
  };
  mobileWallet?: {
    provider: string;
    accountNumber: string;
  };
  relationship: string;
  dateOfBirth?: string;
  nationality?: string;
  occupation?: string;
  verified: boolean;
  favorite: boolean;
  lastUsed?: string;
}

interface TransferRequest {
  recipientId: string;
  recipientCountry: string;
  sendAmount: number;
  sendCurrency: string;
  receiveAmount: number;
  receiveCurrency: string;
  deliveryMethod: string;
  purpose: string;
  sourceOfFunds: string;
  message?: string;
  scheduledDate?: Date;
  recurring?: {
    enabled: boolean;
    frequency: 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
    endDate?: Date;
    maxTransfers?: number;
  };
}

interface TransferQuote {
  id: string;
  sendAmount: number;
  sendCurrency: string;
  receiveAmount: number;
  receiveCurrency: string;
  exchangeRate: number;
  fees: {
    transferFee: number;
    exchangeFee: number;
    deliveryFee: number;
    total: number;
    currency: string;
  };
  totalCost: number;
  deliveryTime: {
    min: number;
    max: number;
    unit: string;
  };
  expiresAt: string;
  route: {
    from: string;
    to: string;
    via?: string[];
  };
}

const transferSchema = yup.object().shape({
  recipientCountry: yup.string().required('Please select recipient country'),
  sendAmount: yup
    .number()
    .required('Amount is required')
    .positive('Amount must be positive')
    .max(50000, 'Maximum transfer amount is $50,000'),
  sendCurrency: yup.string().required('Send currency is required'),
  deliveryMethod: yup.string().required('Please select delivery method'),
  purpose: yup.string().required('Transfer purpose is required'),
  sourceOfFunds: yup.string().required('Source of funds is required'),
});

const countries: Country[] = [
  {
    code: 'GB',
    name: 'United Kingdom',
    currency: 'GBP',
    currencySymbol: '£',
    flag: '🇬🇧',
    supportedMethods: ['BANK_DEPOSIT', 'CARD_DEPOSIT'],
    timeZone: 'Europe/London',
    processingTime: { min: 15, max: 30, unit: 'minutes' },
    restrictions: {
      maxAmount: 50000,
      minAmount: 1,
      requiresKyc: true,
    },
  },
  {
    code: 'IN',
    name: 'India',
    currency: 'INR',
    currencySymbol: '₹',
    flag: '🇮🇳',
    supportedMethods: ['BANK_DEPOSIT', 'MOBILE_WALLET', 'CASH_PICKUP'],
    timeZone: 'Asia/Kolkata',
    processingTime: { min: 1, max: 4, unit: 'hours' },
    restrictions: {
      maxAmount: 25000,
      minAmount: 1,
      requiresKyc: true,
    },
  },
  {
    code: 'PH',
    name: 'Philippines',
    currency: 'PHP',
    currencySymbol: '₱',
    flag: '🇵🇭',
    supportedMethods: ['BANK_DEPOSIT', 'CASH_PICKUP', 'HOME_DELIVERY'],
    timeZone: 'Asia/Manila',
    processingTime: { min: 30, max: 2, unit: 'hours' },
    restrictions: {
      maxAmount: 10000,
      minAmount: 1,
      requiresKyc: true,
    },
  },
  {
    code: 'MX',
    name: 'Mexico',
    currency: 'MXN',
    currencySymbol: '$',
    flag: '🇲🇽',
    supportedMethods: ['BANK_DEPOSIT', 'CASH_PICKUP', 'CARD_DEPOSIT'],
    timeZone: 'America/Mexico_City',
    processingTime: { min: 15, max: 1, unit: 'hours' },
    restrictions: {
      maxAmount: 30000,
      minAmount: 1,
      requiresKyc: true,
    },
  },
];

const deliveryMethods: { [key: string]: DeliveryMethod } = {
  BANK_DEPOSIT: {
    id: 'BANK_DEPOSIT',
    type: 'BANK_DEPOSIT',
    name: 'Bank Deposit',
    description: 'Direct deposit to recipient\'s bank account',
    processingTime: { min: 15, max: 60, unit: 'minutes' },
    fee: { type: 'FIXED', amount: 2.99, currency: 'USD' },
    requirements: ['Bank account details', 'Recipient verification'],
    availability: { countries: ['GB', 'IN', 'PH', 'MX'] },
  },
  CASH_PICKUP: {
    id: 'CASH_PICKUP',
    type: 'CASH_PICKUP',
    name: 'Cash Pickup',
    description: 'Recipient collects cash from pickup location',
    processingTime: { min: 30, max: 2, unit: 'hours' },
    fee: { type: 'PERCENTAGE', amount: 1.5, currency: 'USD' },
    requirements: ['Valid ID', 'Transfer reference number'],
    availability: { countries: ['IN', 'PH', 'MX'] },
  },
  MOBILE_WALLET: {
    id: 'MOBILE_WALLET',
    type: 'MOBILE_WALLET',
    name: 'Mobile Wallet',
    description: 'Transfer to mobile wallet account',
    processingTime: { min: 5, max: 15, unit: 'minutes' },
    fee: { type: 'FIXED', amount: 1.99, currency: 'USD' },
    requirements: ['Mobile wallet account'],
    availability: { countries: ['IN', 'PH'] },
  },
};

const transferPurposes = [
  'Family Support',
  'Education',
  'Medical Expenses',
  'Business Payment',
  'Investment',
  'Gift',
  'Travel',
  'Property Purchase',
  'Other',
];

const sourceOfFunds = [
  'Salary/Wages',
  'Business Income',
  'Investment Returns',
  'Savings',
  'Gift/Inheritance',
  'Loan',
  'Other',
];

const steps = [
  { key: 'recipient', label: 'Select Recipient', icon: <PersonIcon /> },
  { key: 'amount', label: 'Amount & Currency', icon: <MoneyIcon /> },
  { key: 'delivery', label: 'Delivery Method', icon: <SendIcon /> },
  { key: 'details', label: 'Transfer Details', icon: <InfoIcon /> },
  { key: 'review', label: 'Review & Confirm', icon: <CheckCircleIcon /> },
  { key: 'complete', label: 'Transfer Complete', icon: <CheckCircleIcon /> },
];

interface InternationalTransferEnhancedProps {
  onComplete?: (transferId: string) => void;
  onCancel?: () => void;
  prefilledRecipient?: Recipient;
}

const InternationalTransferEnhanced: React.FC<InternationalTransferEnhancedProps> = ({
  onComplete,
  onCancel,
  prefilledRecipient,
}) => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { walletBalance } = useAppSelector((state) => state.wallet);
  
  const [activeStep, setActiveStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selectedRecipient, setSelectedRecipient] = useState<Recipient | null>(prefilledRecipient || null);
  const [exchangeRates, setExchangeRates] = useState<{ [key: string]: ExchangeRate }>({});
  const [currentQuote, setCurrentQuote] = useState<TransferQuote | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [showNewRecipientDialog, setShowNewRecipientDialog] = useState(false);
  const [showRateAlert, setShowRateAlert] = useState(false);
  const [rateAlertTarget, setRateAlertTarget] = useState<number | null>(null);
  const [transferHistory, setTransferHistory] = useState<any[]>([]);
  const [showCompareRates, setShowCompareRates] = useState(false);
  const [savedQuotes, setSavedQuotes] = useState<TransferQuote[]>([]);

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<TransferRequest>({
    resolver: yupResolver(transferSchema),
    defaultValues: {
      sendAmount: 1000,
      sendCurrency: 'USD',
      receiveAmount: 0,
      receiveCurrency: 'GBP',
      recipientCountry: 'GB',
      deliveryMethod: 'BANK_DEPOSIT',
      purpose: 'Family Support',
      sourceOfFunds: 'Salary/Wages',
    },
  });

  const watchedValues = watch();

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    if (watchedValues.sendAmount && watchedValues.sendCurrency && watchedValues.receiveCurrency) {
      debouncedGetQuote();
    }
  }, [watchedValues.sendAmount, watchedValues.sendCurrency, watchedValues.receiveCurrency, watchedValues.deliveryMethod]);

  const loadInitialData = async () => {
    try {
      // Load recipients and exchange rates
      const [recipientsData, ratesData, historyData] = await Promise.all([
        loadRecipients(),
        loadExchangeRates(),
        loadTransferHistory(),
      ]);
      
      setRecipients(recipientsData);
      setExchangeRates(ratesData);
      setTransferHistory(historyData);
    } catch (error) {
      console.error('Failed to load initial data:', error);
    }
  };

  const loadRecipients = async (): Promise<Recipient[]> => {
    // Mock data - in real app, fetch from API
    return [
      {
        id: '1',
        firstName: 'John',
        lastName: 'Smith',
        email: 'john.smith@example.com',
        phone: '+44 20 7946 0958',
        address: {
          street: '123 Oxford Street',
          city: 'London',
          state: 'England',
          postalCode: 'W1D 2HX',
          country: 'GB',
        },
        bankDetails: {
          accountNumber: '12345678',
          bankName: 'Barclays Bank',
          bankCode: 'BARCGB22',
          swiftCode: 'BARCGB22XXX',
        },
        relationship: 'Family',
        verified: true,
        favorite: true,
        lastUsed: new Date(Date.now() - 86400000).toISOString(),
      },
      {
        id: '2',
        firstName: 'Maria',
        lastName: 'Rodriguez',
        email: 'maria.rodriguez@example.com',
        phone: '+52 55 1234 5678',
        address: {
          street: 'Av. Insurgentes Sur 123',
          city: 'Mexico City',
          state: 'CDMX',
          postalCode: '06700',
          country: 'MX',
        },
        relationship: 'Friend',
        verified: true,
        favorite: false,
      },
    ];
  };

  const loadExchangeRates = async () => {
    // Mock exchange rates - in real app, fetch from API
    return {
      'USD-GBP': {
        from: 'USD',
        to: 'GBP',
        rate: 0.79,
        spread: 0.02,
        fee: 0.005,
        lastUpdated: new Date().toISOString(),
        trend: 'UP' as const,
        marketRate: 0.795,
        waqitiRate: 0.79,
      },
      'USD-INR': {
        from: 'USD',
        to: 'INR',
        rate: 83.12,
        spread: 0.15,
        fee: 0.008,
        lastUpdated: new Date().toISOString(),
        trend: 'STABLE' as const,
        marketRate: 83.25,
        waqitiRate: 83.12,
      },
      'USD-PHP': {
        from: 'USD',
        to: 'PHP',
        rate: 55.85,
        spread: 0.25,
        fee: 0.01,
        lastUpdated: new Date().toISOString(),
        trend: 'DOWN' as const,
        marketRate: 56.10,
        waqitiRate: 55.85,
      },
      'USD-MXN': {
        from: 'USD',
        to: 'MXN',
        rate: 17.23,
        spread: 0.12,
        fee: 0.007,
        lastUpdated: new Date().toISOString(),
        trend: 'UP' as const,
        marketRate: 17.35,
        waqitiRate: 17.23,
      },
    };
  };

  const loadTransferHistory = async () => {
    // Mock transfer history
    return [
      {
        id: 'TXN001',
        recipient: 'John Smith',
        country: 'GB',
        amount: 1000,
        currency: 'USD',
        receivedAmount: 790,
        receivedCurrency: 'GBP',
        date: new Date(Date.now() - 172800000).toISOString(),
        status: 'COMPLETED',
      },
      {
        id: 'TXN002',
        recipient: 'Maria Rodriguez',
        country: 'MX',
        amount: 500,
        currency: 'USD',
        receivedAmount: 8615,
        receivedCurrency: 'MXN',
        date: new Date(Date.now() - 604800000).toISOString(),
        status: 'COMPLETED',
      },
    ];
  };

  const debouncedGetQuote = useCallback(
    debounce(async () => {
      if (!watchedValues.sendAmount || !watchedValues.deliveryMethod) return;
      
      setQuoteLoading(true);
      try {
        const quote = await getTransferQuote({
          sendAmount: watchedValues.sendAmount,
          sendCurrency: watchedValues.sendCurrency,
          receiveCurrency: watchedValues.receiveCurrency,
          deliveryMethod: watchedValues.deliveryMethod,
          recipientCountry: watchedValues.recipientCountry,
        });
        
        setCurrentQuote(quote);
        setValue('receiveAmount', quote.receiveAmount);
      } catch (error) {
        console.error('Failed to get quote:', error);
        toast.error('Failed to get exchange rate');
      } finally {
        setQuoteLoading(false);
      }
    }, 500),
    [watchedValues.sendAmount, watchedValues.sendCurrency, watchedValues.receiveCurrency, watchedValues.deliveryMethod]
  );

  const getTransferQuote = async (params: any): Promise<TransferQuote> => {
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    const rateKey = `${params.sendCurrency}-${params.receiveCurrency}`;
    const exchangeRate = exchangeRates[rateKey];
    
    if (!exchangeRate) {
      throw new Error('Exchange rate not available');
    }
    
    const deliveryMethodInfo = deliveryMethods[params.deliveryMethod];
    const transferFee = 5.99;
    const exchangeFee = params.sendAmount * exchangeRate.fee;
    const deliveryFee = deliveryMethodInfo.fee.type === 'FIXED' 
      ? deliveryMethodInfo.fee.amount 
      : params.sendAmount * (deliveryMethodInfo.fee.amount / 100);
    
    const totalFees = transferFee + exchangeFee + deliveryFee;
    const receiveAmount = (params.sendAmount - exchangeFee) * exchangeRate.rate;
    
    return {
      id: `QUOTE_${Date.now()}`,
      sendAmount: params.sendAmount,
      sendCurrency: params.sendCurrency,
      receiveAmount: Math.round(receiveAmount * 100) / 100,
      receiveCurrency: params.receiveCurrency,
      exchangeRate: exchangeRate.rate,
      fees: {
        transferFee,
        exchangeFee,
        deliveryFee,
        total: totalFees,
        currency: params.sendCurrency,
      },
      totalCost: params.sendAmount + totalFees,
      deliveryTime: deliveryMethodInfo.processingTime,
      expiresAt: addMinutes(new Date(), 15).toISOString(),
      route: {
        from: params.sendCurrency,
        to: params.receiveCurrency,
        via: params.sendCurrency !== 'USD' ? ['USD'] : undefined,
      },
    };
  };

  const handleNext = () => {
    if (activeStep === steps.length - 2) {
      handleSubmitTransfer();
    } else {
      setActiveStep(prev => prev + 1);
    }
  };

  const handleBack = () => {
    setActiveStep(prev => prev - 1);
  };

  const handleSubmitTransfer = async () => {
    if (!currentQuote) {
      toast.error('Please get a current quote before proceeding');
      return;
    }
    
    const now = new Date();
    const expiresAt = new Date(currentQuote.expiresAt);
    
    if (now > expiresAt) {
      toast.error('Quote has expired. Please get a new quote.');
      return;
    }
    
    setLoading(true);
    try {
      const transferData = {
        ...getValues(),
        recipientId: selectedRecipient?.id,
        quoteId: currentQuote.id,
      };
      
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 3000));
      
      setActiveStep(steps.length - 1);
      toast.success('Transfer initiated successfully!');
      onComplete?.('TXN_' + Date.now());
    } catch (error: any) {
      toast.error(error.message || 'Failed to process transfer');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveQuote = () => {
    if (currentQuote) {
      setSavedQuotes(prev => [...prev, currentQuote]);
      toast.success('Quote saved for comparison');
    }
  };

  const handleSetRateAlert = (targetRate: number) => {
    setRateAlertTarget(targetRate);
    setShowRateAlert(true);
  };

  const renderRecipientStep = () => {
    const favoriteRecipients = recipients.filter(r => r.favorite);
    const recentRecipients = recipients
      .filter(r => r.lastUsed && !r.favorite)
      .sort((a, b) => new Date(b.lastUsed!).getTime() - new Date(a.lastUsed!).getTime());
    const otherRecipients = recipients.filter(r => !r.favorite && !r.lastUsed);

    return (
      <Box>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
          <Typography variant="h6">
            Select Recipient
          </Typography>
          <Button
            startIcon={<AddIcon />}
            onClick={() => setShowNewRecipientDialog(true)}
          >
            New Recipient
          </Button>
        </Box>

        {/* Favorite Recipients */}
        {favoriteRecipients.length > 0 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                <StarIcon sx={{ mr: 1, color: 'gold' }} />
                Favorites
              </Typography>
              <Grid container spacing={2}>
                {favoriteRecipients.map(recipient => (
                  <Grid item xs={12} sm={6} md={4} key={recipient.id}>
                    <Card 
                      variant="outlined"
                      sx={{ 
                        cursor: 'pointer',
                        border: selectedRecipient?.id === recipient.id ? 2 : 1,
                        borderColor: selectedRecipient?.id === recipient.id 
                          ? 'primary.main' 
                          : 'divider'
                      }}
                      onClick={() => {
                        setSelectedRecipient(recipient);
                        setValue('recipientCountry', recipient.address.country);
                        const country = countries.find(c => c.code === recipient.address.country);
                        if (country) {
                          setValue('receiveCurrency', country.currency);
                        }
                      }}
                    >
                      <CardContent>
                        <Box display="flex" alignItems="center" mb={1}>
                          <Avatar sx={{ mr: 2 }}>
                            {recipient.firstName.charAt(0)}
                          </Avatar>
                          <Box>
                            <Typography variant="subtitle2">
                              {recipient.firstName} {recipient.lastName}
                            </Typography>
                            <Box display="flex" alignItems="center">
                              <Typography variant="caption" sx={{ mr: 1 }}>
                                {countries.find(c => c.code === recipient.address.country)?.flag}
                              </Typography>
                              <Typography variant="caption">
                                {countries.find(c => c.code === recipient.address.country)?.name}
                              </Typography>
                            </Box>
                          </Box>
                        </Box>
                        
                        <Box display="flex" alignItems="center" justifyContent="space-between">
                          <Chip 
                            label={recipient.relationship} 
                            size="small" 
                            variant="outlined"
                          />
                          {recipient.verified && (
                            <CheckCircleIcon color="success" fontSize="small" />
                          )}
                        </Box>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        )}

        {/* Recent Recipients */}
        {recentRecipients.length > 0 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                <HistoryIcon sx={{ mr: 1 }} />
                Recent
              </Typography>
              <List>
                {recentRecipients.map(recipient => (
                  <ListItem 
                    key={recipient.id}
                    button
                    selected={selectedRecipient?.id === recipient.id}
                    onClick={() => {
                      setSelectedRecipient(recipient);
                      setValue('recipientCountry', recipient.address.country);
                    }}
                  >
                    <ListItemAvatar>
                      <Avatar>
                        {recipient.firstName.charAt(0)}
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={`${recipient.firstName} ${recipient.lastName}`}
                      secondary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="caption">
                            {countries.find(c => c.code === recipient.address.country)?.flag}
                            {' '}
                            {countries.find(c => c.code === recipient.address.country)?.name}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            • Last used: {formatTimeAgo(recipient.lastUsed!)}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        )}

        {/* All Recipients */}
        {otherRecipients.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="subtitle1" gutterBottom>
                All Recipients
              </Typography>
              <List>
                {otherRecipients.map(recipient => (
                  <ListItem 
                    key={recipient.id}
                    button
                    selected={selectedRecipient?.id === recipient.id}
                    onClick={() => {
                      setSelectedRecipient(recipient);
                      setValue('recipientCountry', recipient.address.country);
                    }}
                  >
                    <ListItemAvatar>
                      <Avatar>
                        {recipient.firstName.charAt(0)}
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={`${recipient.firstName} ${recipient.lastName}`}
                      secondary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="caption">
                            {countries.find(c => c.code === recipient.address.country)?.flag}
                            {' '}
                            {countries.find(c => c.code === recipient.address.country)?.name}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        )}
      </Box>
    );
  };

  const renderAmountStep = () => {
    const selectedCountry = countries.find(c => c.code === watchedValues.recipientCountry);
    const rateKey = `${watchedValues.sendCurrency}-${watchedValues.receiveCurrency}`;
    const currentRate = exchangeRates[rateKey];

    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          Amount & Exchange Rate
        </Typography>

        <Grid container spacing={3}>
          {/* Send Amount */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  You Send
                </Typography>
                
                <Grid container spacing={2}>
                  <Grid item xs={8}>
                    <Controller
                      name="sendAmount"
                      control={control}
                      render={({ field }) => (
                        <TextField
                          {...field}
                          fullWidth
                          type="number"
                          label="Amount"
                          error={!!errors.sendAmount}
                          helperText={errors.sendAmount?.message}
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
                      name="sendCurrency"
                      control={control}
                      render={({ field }) => (
                        <FormControl fullWidth>
                          <InputLabel>Currency</InputLabel>
                          <Select {...field} label="Currency">
                            <MenuItem value="USD">USD</MenuItem>
                            <MenuItem value="EUR">EUR</MenuItem>
                            <MenuItem value="GBP">GBP</MenuItem>
                          </Select>
                        </FormControl>
                      )}
                    />
                  </Grid>
                </Grid>

                {/* Available Balance */}
                {walletBalance && (
                  <Alert severity="info" sx={{ mt: 2 }}>
                    Available Balance: {formatCurrency(walletBalance.currentBalance)} USD
                  </Alert>
                )}
              </CardContent>
            </Card>
          </Grid>

          {/* Receive Amount */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  Recipient Gets
                </Typography>
                
                <Grid container spacing={2}>
                  <Grid item xs={8}>
                    <TextField
                      fullWidth
                      type="number"
                      label="Amount"
                      value={watchedValues.receiveAmount || ''}
                      InputProps={{
                        readOnly: true,
                        startAdornment: (
                          <InputAdornment position="start">
                            {selectedCountry?.currencySymbol}
                          </InputAdornment>
                        ),
                        endAdornment: quoteLoading && (
                          <InputAdornment position="end">
                            <CircularProgress size={20} />
                          </InputAdornment>
                        ),
                      }}
                    />
                  </Grid>
                  <Grid item xs={4}>
                    <Controller
                      name="receiveCurrency"
                      control={control}
                      render={({ field }) => (
                        <FormControl fullWidth>
                          <InputLabel>Currency</InputLabel>
                          <Select {...field} label="Currency">
                            {countries.map(country => (
                              <MenuItem key={country.code} value={country.currency}>
                                {country.flag} {country.currency}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      )}
                    />
                  </Grid>
                </Grid>

                {/* Processing Time */}
                {selectedCountry && (
                  <Alert severity="success" sx={{ mt: 2 }}>
                    <Box display="flex" alignItems="center">
                      <ScheduleIcon sx={{ mr: 1 }} />
                      Delivered in {selectedCountry.processingTime.min}-{selectedCountry.processingTime.max} {selectedCountry.processingTime.unit}
                    </Box>
                  </Alert>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Exchange Rate Information */}
        {currentRate && (
          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="subtitle1">
                  Exchange Rate Information
                </Typography>
                <Box display="flex" gap={1}>
                  <Button
                    size="small"
                    startIcon={<CompareIcon />}
                    onClick={() => setShowCompareRates(true)}
                  >
                    Compare Rates
                  </Button>
                  <Button
                    size="small"
                    startIcon={<StarBorderIcon />}
                    onClick={handleSaveQuote}
                  >
                    Save Quote
                  </Button>
                </Box>
              </Box>
              
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <Box textAlign="center">
                    <Typography variant="h4" color="primary">
                      {currentRate.rate.toFixed(4)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      1 {currentRate.from} = {currentRate.rate.toFixed(4)} {currentRate.to}
                    </Typography>
                    <Box display="flex" alignItems="center" justifyContent="center" mt={1}>
                      {currentRate.trend === 'UP' ? (
                        <TrendingUpIcon color="success" fontSize="small" />
                      ) : currentRate.trend === 'DOWN' ? (
                        <TrendingDownIcon color="error" fontSize="small" />
                      ) : (
                        <SwapIcon color="action" fontSize="small" />
                      )}
                      <Typography variant="caption" sx={{ ml: 0.5 }}>
                        {currentRate.trend}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>
                
                <Grid item xs={12} sm={4}>
                  <Box textAlign="center">
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      Market Rate
                    </Typography>
                    <Typography variant="h6">
                      {currentRate.marketRate.toFixed(4)}
                    </Typography>
                    <Typography variant="caption" color="success.main">
                      Waqiti rate is {((currentRate.marketRate - currentRate.rate) / currentRate.marketRate * 100).toFixed(2)}% better
                    </Typography>
                  </Box>
                </Grid>
                
                <Grid item xs={12} sm={4}>
                  <Box textAlign="center">
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      Last Updated
                    </Typography>
                    <Typography variant="body2">
                      {formatTimeAgo(currentRate.lastUpdated)}
                    </Typography>
                    <Button
                      size="small"
                      startIcon={<RefreshIcon />}
                      onClick={() => {
                        // Refresh rate
                        debouncedGetQuote();
                      }}
                    >
                      Refresh
                    </Button>
                  </Box>
                </Grid>
              </Grid>

              {/* Rate Alert */}
              <Box mt={2}>
                <Button
                  size="small"
                  startIcon={<InfoIcon />}
                  onClick={() => handleSetRateAlert(currentRate.rate * 1.02)}
                >
                  Set Rate Alert
                </Button>
                <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                  Get notified when the rate improves
                </Typography>
              </Box>
            </CardContent>
          </Card>
        )}
      </Box>
    );
  };

  const renderDeliveryStep = () => {
    const selectedCountry = countries.find(c => c.code === watchedValues.recipientCountry);
    const availableMethods = selectedCountry?.supportedMethods.map(id => deliveryMethods[id]) || [];

    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          Choose Delivery Method
        </Typography>

        <Grid container spacing={3}>
          {availableMethods.map(method => {
            const isSelected = watchedValues.deliveryMethod === method.id;
            const feeText = method.fee.type === 'FIXED' 
              ? formatCurrency(method.fee.amount)
              : `${method.fee.amount}% of send amount`;
            
            return (
              <Grid item xs={12} sm={6} key={method.id}>
                <Card 
                  variant="outlined"
                  sx={{
                    cursor: 'pointer',
                    border: isSelected ? 2 : 1,
                    borderColor: isSelected ? 'primary.main' : 'divider',
                    '&:hover': {
                      borderColor: 'primary.main',
                    },
                  }}
                  onClick={() => setValue('deliveryMethod', method.id)}
                >
                  <CardContent>
                    <Box display="flex" alignItems="center" mb={2}>
                      {method.type === 'BANK_DEPOSIT' && <BankIcon sx={{ mr: 2 }} />}
                      {method.type === 'CASH_PICKUP' && <MoneyIcon sx={{ mr: 2 }} />}
                      {method.type === 'MOBILE_WALLET' && <PhoneIcon sx={{ mr: 2 }} />}
                      {method.type === 'CARD_DEPOSIT' && <CardIcon sx={{ mr: 2 }} />}
                      {method.type === 'HOME_DELIVERY' && <LocationIcon sx={{ mr: 2 }} />}
                      
                      <Box>
                        <Typography variant="h6">
                          {method.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {method.description}
                        </Typography>
                      </Box>
                    </Box>
                    
                    <Box display="flex" justify="space-between" alignItems="center" mb={2}>
                      <Box>
                        <Typography variant="body2" color="text.secondary">
                          Processing Time
                        </Typography>
                        <Typography variant="body1">
                          {method.processingTime.min}-{method.processingTime.max} {method.processingTime.unit}
                        </Typography>
                      </Box>
                      <Box textAlign="right">
                        <Typography variant="body2" color="text.secondary">
                          Fee
                        </Typography>
                        <Typography variant="body1">
                          {feeText}
                        </Typography>
                      </Box>
                    </Box>
                    
                    <Divider sx={{ mb: 2 }} />
                    
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      Requirements:
                    </Typography>
                    <Box display="flex" flexWrap="wrap" gap={0.5}>
                      {method.requirements.map((req, index) => (
                        <Chip key={index} label={req} size="small" variant="outlined" />
                      ))}
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      </Box>
    );
  };

  const renderDetailsStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Transfer Details
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} sm={6}>
          <Controller
            name="purpose"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth>
                <InputLabel>Purpose of Transfer</InputLabel>
                <Select {...field} label="Purpose of Transfer">
                  {transferPurposes.map(purpose => (
                    <MenuItem key={purpose} value={purpose}>
                      {purpose}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}
          />
        </Grid>
        
        <Grid item xs={12} sm={6}>
          <Controller
            name="sourceOfFunds"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth>
                <InputLabel>Source of Funds</InputLabel>
                <Select {...field} label="Source of Funds">
                  {sourceOfFunds.map(source => (
                    <MenuItem key={source} value={source}>
                      {source}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}
          />
        </Grid>
        
        <Grid item xs={12}>
          <Controller
            name="message"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                fullWidth
                multiline
                rows={3}
                label="Message to Recipient (Optional)"
                placeholder="Add a personal message..."
              />
            )}
          />
        </Grid>
      </Grid>
    </Box>
  );

  const renderReviewStep = () => {
    if (!currentQuote) {
      return (
        <Alert severity="error">
          No quote available. Please go back and get a current quote.
        </Alert>
      );
    }

    const expiresIn = differenceInMinutes(new Date(currentQuote.expiresAt), new Date());
    
    return (
      <Box>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
          <Typography variant="h6">
            Review Transfer
          </Typography>
          <Chip 
            label={`Quote expires in ${expiresIn} minutes`}
            color={expiresIn <= 5 ? 'error' : 'warning'}
            icon={<ScheduleIcon />}
          />
        </Box>

        <Grid container spacing={3}>
          {/* Transfer Summary */}
          <Grid item xs={12} md={8}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Transfer Summary
                </Typography>
                
                <List>
                  <ListItem>
                    <ListItemText
                      primary="Recipient"
                      secondary={
                        selectedRecipient && (
                          <Box display="flex" alignItems="center" gap={1}>
                            <Avatar sx={{ width: 24, height: 24 }}>
                              {selectedRecipient.firstName.charAt(0)}
                            </Avatar>
                            <Typography variant="body2">
                              {selectedRecipient.firstName} {selectedRecipient.lastName}
                            </Typography>
                            <Typography variant="caption">
                              • {selectedRecipient.address.city}, {countries.find(c => c.code === selectedRecipient.address.country)?.name}
                            </Typography>
                          </Box>
                        )
                      }
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="You Send"
                      secondary={
                        <Typography variant="h6" color="primary">
                          {formatCurrency(currentQuote.sendAmount)} {currentQuote.sendCurrency}
                        </Typography>
                      }
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Recipient Gets"
                      secondary={
                        <Typography variant="h6" color="success.main">
                          {formatCurrency(currentQuote.receiveAmount)} {currentQuote.receiveCurrency}
                        </Typography>
                      }
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Exchange Rate"
                      secondary={`1 ${currentQuote.sendCurrency} = ${currentQuote.exchangeRate.toFixed(4)} ${currentQuote.receiveCurrency}`}
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Delivery Method"
                      secondary={deliveryMethods[watchedValues.deliveryMethod].name}
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Purpose"
                      secondary={watchedValues.purpose}
                    />
                  </ListItem>
                </List>
              </CardContent>
            </Card>
          </Grid>

          {/* Cost Breakdown */}
          <Grid item xs={12} md={4}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Cost Breakdown
                </Typography>
                
                <List dense>
                  <ListItem>
                    <ListItemText
                      primary="Transfer Amount"
                      secondary={formatCurrency(currentQuote.sendAmount)}
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Transfer Fee"
                      secondary={formatCurrency(currentQuote.fees.transferFee)}
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Exchange Fee"
                      secondary={formatCurrency(currentQuote.fees.exchangeFee)}
                    />
                  </ListItem>
                  
                  <ListItem>
                    <ListItemText
                      primary="Delivery Fee"
                      secondary={formatCurrency(currentQuote.fees.deliveryFee)}
                    />
                  </ListItem>
                  
                  <Divider />
                  
                  <ListItem>
                    <ListItemText
                      primary={<Typography variant="h6">Total Cost</Typography>}
                      secondary={
                        <Typography variant="h6" color="primary">
                          {formatCurrency(currentQuote.totalCost)} {currentQuote.sendCurrency}
                        </Typography>
                      }
                    />
                  </ListItem>
                </List>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Important Information */}
        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
              <InfoIcon sx={{ mr: 1 }} />
              Important Information
            </Typography>
            
            <List dense>
              <ListItem>
                <ListItemText
                  primary="Delivery Time"
                  secondary={`Your transfer will be delivered in ${currentQuote.deliveryTime.min}-${currentQuote.deliveryTime.max} ${currentQuote.deliveryTime.unit}`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemText
                  primary="Quote Validity"
                  secondary="This quote is valid for 15 minutes from generation"
                />
              </ListItem>
              
              <ListItem>
                <ListItemText
                  primary="Cancellation Policy"
                  secondary="Transfers can be cancelled within 30 minutes of initiation for a full refund"
                />
              </ListItem>
            </List>
          </CardContent>
        </Card>
      </Box>
    );
  };

  const renderCompleteStep = () => (
    <Box textAlign="center" py={4}>
      <CheckCircleIcon 
        sx={{ 
          fontSize: 80, 
          color: 'success.main', 
          mb: 2 
        }} 
      />
      
      <Typography variant="h5" gutterBottom>
        Transfer Initiated Successfully!
      </Typography>
      
      <Typography variant="body1" color="text.secondary" paragraph>
        Your international transfer has been successfully initiated. 
        The recipient will receive the funds within the estimated delivery time.
      </Typography>

      <Card sx={{ maxWidth: 400, mx: 'auto', mt: 3 }}>
        <CardContent>
          <Typography variant="subtitle2" gutterBottom>
            Transfer Reference
          </Typography>
          <Box display="flex" alignItems="center" justifyContent="center" gap={1}>
            <Typography variant="h6" sx={{ fontFamily: 'monospace' }}>
              TXN-{Date.now().toString().slice(-8)}
            </Typography>
            <IconButton size="small" onClick={() => {
              navigator.clipboard.writeText(`TXN-${Date.now().toString().slice(-8)}`);
              toast.success('Reference copied!');
            }}>
              <CopyIcon fontSize="small" />
            </IconButton>
          </Box>
        </CardContent>
      </Card>

      <Box mt={4} display="flex" justifyContent="center" gap={2}>
        <Button
          variant="outlined"
          startIcon={<TrackIcon />}
        >
          Track Transfer
        </Button>
        <Button
          variant="outlined"
          startIcon={<DownloadIcon />}
        >
          Download Receipt
        </Button>
        <Button
          variant="contained"
          onClick={() => {
            setActiveStep(0);
            setSelectedRecipient(null);
            setCurrentQuote(null);
            // Reset form
          }}
        >
          Send Another
        </Button>
      </Box>
    </Box>
  );

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0: return renderRecipientStep();
      case 1: return renderAmountStep();
      case 2: return renderDeliveryStep();
      case 3: return renderDetailsStep();
      case 4: return renderReviewStep();
      case 5: return renderCompleteStep();
      default: return null;
    }
  };

  const canProceed = () => {
    switch (activeStep) {
      case 0: return selectedRecipient !== null;
      case 1: return watchedValues.sendAmount > 0 && currentQuote !== null;
      case 2: return watchedValues.deliveryMethod !== '';
      case 3: return watchedValues.purpose && watchedValues.sourceOfFunds;
      case 4: return currentQuote && new Date() < new Date(currentQuote.expiresAt);
      default: return false;
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5">
          International Transfer
        </Typography>
        <Box display="flex" gap={1}>
          <Button
            startIcon={<HistoryIcon />}
            onClick={() => {/* Show transfer history */}}
          >
            History
          </Button>
          <Button
            startIcon={<CompareIcon />}
            onClick={() => setShowCompareRates(true)}
          >
            Compare Rates
          </Button>
          <IconButton onClick={onCancel}>
            <CloseIcon />
          </IconButton>
        </Box>
      </Box>

      {/* Stepper */}
      <Box mb={4}>
        <Stepper activeStep={activeStep} alternativeLabel>
          {steps.map((step, index) => (
            <Step key={step.key}>
              <StepLabel icon={step.icon}>
                {step.label}
              </StepLabel>
            </Step>
          ))}
        </Stepper>
      </Box>

      {/* Progress */}
      {(loading || quoteLoading) && <LinearProgress sx={{ mb: 2 }} />}

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
            {activeStep === steps.length - 2 ? 'Send Transfer' : 'Next'}
          </Button>
        </Box>
      )}
    </Box>
  );
};

// Debounce utility function
function debounce<T extends (...args: any[]) => any>(
  func: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timeoutId: NodeJS.Timeout;
  return (...args: Parameters<T>) => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => func(...args), delay);
  };
}

export default InternationalTransferEnhanced;