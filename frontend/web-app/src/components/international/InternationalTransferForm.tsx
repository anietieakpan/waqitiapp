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
  Grid,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  InputAdornment,
  Alert,
  Chip,
  Divider,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Tooltip,
  FormHelperText,
  useTheme,
  alpha,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import PersonIcon from '@mui/icons-material/Person';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import SpeedIcon from '@mui/icons-material/Speed';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import TimerIcon from '@mui/icons-material/Timer';
import SecurityIcon from '@mui/icons-material/Security';
import ReceiptIcon from '@mui/icons-material/Receipt';
import FlagIcon from '@mui/icons-material/Flag';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';;
import { format } from 'date-fns';

interface TransferQuote {
  quoteId: string;
  sourceAmount: number;
  sourceCurrency: string;
  targetAmount: number;
  targetCurrency: string;
  exchangeRate: number;
  transferFee: number;
  conversionFee: number;
  totalFees: number;
  transferSpeed: 'EXPRESS' | 'STANDARD' | 'ECONOMY';
  estimatedDelivery: string;
  expiresAt: string;
}

interface Recipient {
  id: string;
  fullName: string;
  country: string;
  accountNumber: string;
  bankName: string;
  relationship: string;
  verificationStatus: 'VERIFIED' | 'PENDING';
}

interface SupportedCountry {
  code: string;
  name: string;
  currency: string;
  deliveryMethods: string[];
  regulatoryInfo?: string;
}

const InternationalTransferForm: React.FC = () => {
  const theme = useTheme();
  const [activeStep, setActiveStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [quote, setQuote] = useState<TransferQuote | null>(null);
  const [showQuoteDetails, setShowQuoteDetails] = useState(false);
  const [showRecipientDialog, setShowRecipientDialog] = useState(false);
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [supportedCountries, setSupportedCountries] = useState<SupportedCountry[]>([]);
  const [exchangeRate, setExchangeRate] = useState<number | null>(null);
  
  // Form state
  const [formData, setFormData] = useState({
    sourceAmount: '',
    sourceCurrency: 'USD',
    targetAmount: '',
    targetCurrency: 'EUR',
    sourceCountry: 'US',
    targetCountry: 'DE',
    purpose: 'PERSONAL',
    purposeDetails: '',
    transferSpeed: 'STANDARD' as 'EXPRESS' | 'STANDARD' | 'ECONOMY',
    recipientId: '',
    recipient: null as Recipient | null,
    paymentMethod: 'BANK_ACCOUNT',
    deliveryMethod: 'BANK_DEPOSIT',
  });

  const steps = ['Amount & Currency', 'Recipient', 'Transfer Details', 'Review & Confirm'];

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    if (formData.sourceCurrency !== formData.targetCurrency) {
      fetchExchangeRate();
    }
  }, [formData.sourceCurrency, formData.targetCurrency]);

  const loadInitialData = async () => {
    try {
      // Load supported countries
      const countriesResponse = await fetch('/api/international/countries');
      const countries = await countriesResponse.json();
      setSupportedCountries(countries);

      // Load saved recipients
      const recipientsResponse = await fetch('/api/international/recipients');
      const recipientsData = await recipientsResponse.json();
      setRecipients(recipientsData);
    } catch (error) {
      console.error('Failed to load initial data:', error);
    }
  };

  const fetchExchangeRate = async () => {
    try {
      const response = await fetch(
        `/api/international/exchange-rate?source=${formData.sourceCurrency}&target=${formData.targetCurrency}`
      );
      const data = await response.json();
      setExchangeRate(data.rate);
      
      // Update target amount if source amount is set
      if (formData.sourceAmount) {
        const targetAmount = (parseFloat(formData.sourceAmount) * data.rate).toFixed(2);
        setFormData(prev => ({ ...prev, targetAmount }));
      }
    } catch (error) {
      console.error('Failed to fetch exchange rate:', error);
    }
  };

  const createQuote = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/international/quotes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sourceAmount: formData.sourceAmount ? parseFloat(formData.sourceAmount) : null,
          sourceCurrency: formData.sourceCurrency,
          targetAmount: formData.targetAmount ? parseFloat(formData.targetAmount) : null,
          targetCurrency: formData.targetCurrency,
          sourceCountry: formData.sourceCountry,
          targetCountry: formData.targetCountry,
          purpose: formData.purpose,
          transferSpeed: formData.transferSpeed,
          paymentMethod: formData.paymentMethod,
          deliveryMethod: formData.deliveryMethod,
        }),
      });

      const quoteData = await response.json();
      setQuote(quoteData);
      setShowQuoteDetails(true);
    } catch (error) {
      console.error('Failed to create quote:', error);
    } finally {
      setLoading(false);
    }
  };

  const initiateTransfer = async () => {
    if (!quote || !formData.recipient) return;

    setLoading(true);
    try {
      const response = await fetch('/api/international/transfers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          quoteId: quote.quoteId,
          recipient: {
            recipientId: formData.recipientId,
            ...formData.recipient,
          },
          transferReference: `Transfer to ${formData.recipient.fullName}`,
          additionalInfo: {
            purpose: formData.purpose,
            purposeDetails: formData.purposeDetails,
          },
        }),
      });

      const transfer = await response.json();
      
      if (transfer.status === 'INITIATED' || transfer.status === 'PROCESSING') {
        // Navigate to transfer tracking page
        window.location.href = `/international/transfers/${transfer.transferId}`;
      }
    } catch (error) {
      console.error('Failed to initiate transfer:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleNext = async () => {
    if (activeStep === 0) {
      // Validate amount and create quote
      if (!formData.sourceAmount && !formData.targetAmount) {
        return;
      }
      await createQuote();
    }
    
    if (activeStep === steps.length - 1) {
      // Final step - initiate transfer
      await initiateTransfer();
    } else {
      setActiveStep(prev => prev + 1);
    }
  };

  const handleBack = () => {
    setActiveStep(prev => prev - 1);
  };

  const renderAmountStep = () => (
    <Box>
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle2" gutterBottom>
                You Send
              </Typography>
              <FormControl fullWidth sx={{ mb: 2 }}>
                <TextField
                  value={formData.sourceAmount}
                  onChange={(e) => {
                    const value = e.target.value;
                    setFormData(prev => ({ 
                      ...prev, 
                      sourceAmount: value,
                      targetAmount: exchangeRate ? 
                        (parseFloat(value || '0') * exchangeRate).toFixed(2) : ''
                    }));
                  }}
                  placeholder="0.00"
                  type="number"
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <Select
                          value={formData.sourceCurrency}
                          onChange={(e) => setFormData(prev => ({ 
                            ...prev, 
                            sourceCurrency: e.target.value 
                          }))}
                          variant="standard"
                          disableUnderline
                        >
                          <MenuItem value="USD">USD</MenuItem>
                          <MenuItem value="EUR">EUR</MenuItem>
                          <MenuItem value="GBP">GBP</MenuItem>
                          <MenuItem value="JPY">JPY</MenuItem>
                        </Select>
                      </InputAdornment>
                    ),
                  }}
                />
              </FormControl>
              <FormControl fullWidth size="small">
                <InputLabel>From Country</InputLabel>
                <Select
                  value={formData.sourceCountry}
                  onChange={(e) => setFormData(prev => ({ 
                    ...prev, 
                    sourceCountry: e.target.value 
                  }))}
                  label="From Country"
                >
                  {supportedCountries.map(country => (
                    <MenuItem key={country.code} value={country.code}>
                      <Box display="flex" alignItems="center">
                        <FlagIcon sx={{ mr: 1, fontSize: 16 }} />
                        {country.name}
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle2" gutterBottom>
                Recipient Gets
              </Typography>
              <FormControl fullWidth sx={{ mb: 2 }}>
                <TextField
                  value={formData.targetAmount}
                  onChange={(e) => {
                    const value = e.target.value;
                    setFormData(prev => ({ 
                      ...prev, 
                      targetAmount: value,
                      sourceAmount: exchangeRate ? 
                        (parseFloat(value || '0') / exchangeRate).toFixed(2) : ''
                    }));
                  }}
                  placeholder="0.00"
                  type="number"
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <Select
                          value={formData.targetCurrency}
                          onChange={(e) => setFormData(prev => ({ 
                            ...prev, 
                            targetCurrency: e.target.value 
                          }))}
                          variant="standard"
                          disableUnderline
                        >
                          <MenuItem value="EUR">EUR</MenuItem>
                          <MenuItem value="GBP">GBP</MenuItem>
                          <MenuItem value="JPY">JPY</MenuItem>
                          <MenuItem value="USD">USD</MenuItem>
                        </Select>
                      </InputAdornment>
                    ),
                  }}
                />
              </FormControl>
              <FormControl fullWidth size="small">
                <InputLabel>To Country</InputLabel>
                <Select
                  value={formData.targetCountry}
                  onChange={(e) => setFormData(prev => ({ 
                    ...prev, 
                    targetCountry: e.target.value 
                  }))}
                  label="To Country"
                >
                  {supportedCountries.map(country => (
                    <MenuItem key={country.code} value={country.code}>
                      <Box display="flex" alignItems="center">
                        <FlagIcon sx={{ mr: 1, fontSize: 16 }} />
                        {country.name}
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </CardContent>
          </Card>
        </Grid>

        {/* Exchange Rate Display */}
        {exchangeRate && (
          <Grid item xs={12}>
            <Card sx={{ bgcolor: alpha(theme.palette.info.main, 0.1) }}>
              <CardContent>
                <Box display="flex" alignItems="center" justifyContent="space-between">
                  <Box display="flex" alignItems="center">
                    <TrendingUpIcon sx={{ mr: 1 }} />
                    <Typography variant="body1">
                      Exchange Rate: 1 {formData.sourceCurrency} = {exchangeRate.toFixed(4)} {formData.targetCurrency}
                    </Typography>
                  </Box>
                  <Chip 
                    label="Live Rate" 
                    color="info" 
                    size="small"
                  />
                </Box>
              </CardContent>
            </Card>
          </Grid>
        )}

        {/* Transfer Speed Selection */}
        <Grid item xs={12}>
          <Typography variant="h6" gutterBottom>
            Transfer Speed
          </Typography>
          <Grid container spacing={2}>
            {[
              { value: 'EXPRESS', label: 'Express', time: '30 minutes', icon: <SpeedIcon /> },
              { value: 'STANDARD', label: 'Standard', time: '1-2 days', icon: <TimerIcon /> },
              { value: 'ECONOMY', label: 'Economy', time: '3-5 days', icon: <AccountBalanceIcon /> },
            ].map((speed) => (
              <Grid item xs={12} sm={4} key={speed.value}>
                <Card 
                  variant={formData.transferSpeed === speed.value ? 'elevation' : 'outlined'}
                  sx={{ 
                    cursor: 'pointer',
                    borderColor: formData.transferSpeed === speed.value ? 
                      theme.palette.primary.main : undefined,
                    borderWidth: formData.transferSpeed === speed.value ? 2 : 1,
                  }}
                  onClick={() => setFormData(prev => ({ 
                    ...prev, 
                    transferSpeed: speed.value as any 
                  }))}
                >
                  <CardContent>
                    <Box display="flex" alignItems="center" mb={1}>
                      {speed.icon}
                      <Typography variant="h6" sx={{ ml: 1 }}>
                        {speed.label}
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="textSecondary">
                      Delivery: {speed.time}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Grid>
      </Grid>
    </Box>
  );

  const renderRecipientStep = () => (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">
          Select Recipient
        </Typography>
        <Button
          variant="outlined"
          startIcon={<AddIcon />}
          onClick={() => setShowRecipientDialog(true)}
        >
          Add New Recipient
        </Button>
      </Box>

      <Grid container spacing={2}>
        {recipients.map((recipient) => (
          <Grid item xs={12} sm={6} key={recipient.id}>
            <Card 
              variant={formData.recipientId === recipient.id ? 'elevation' : 'outlined'}
              sx={{ 
                cursor: 'pointer',
                borderColor: formData.recipientId === recipient.id ? 
                  theme.palette.primary.main : undefined,
                borderWidth: formData.recipientId === recipient.id ? 2 : 1,
              }}
              onClick={() => setFormData(prev => ({ 
                ...prev, 
                recipientId: recipient.id,
                recipient: recipient
              }))}
            >
              <CardContent>
                <Box display="flex" alignItems="flex-start" justifyContent="space-between">
                  <Box>
                    <Typography variant="h6">
                      {recipient.fullName}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      {recipient.bankName}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      Account: ***{recipient.accountNumber.slice(-4)}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      {recipient.country}
                    </Typography>
                  </Box>
                  <Box textAlign="right">
                    <Chip 
                      label={recipient.verificationStatus}
                      color={recipient.verificationStatus === 'VERIFIED' ? 'success' : 'warning'}
                      size="small"
                      icon={recipient.verificationStatus === 'VERIFIED' ? 
                        <CheckCircleIcon /> : <WarningIcon />}
                    />
                    <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                      {recipient.relationship}
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {formData.recipient && (
        <Alert severity="info" sx={{ mt: 3 }}>
          Selected recipient: {formData.recipient.fullName} in {formData.recipient.country}
        </Alert>
      )}
    </Box>
  );

  const renderDetailsStep = () => (
    <Box>
      <Grid container spacing={3}>
        <Grid item xs={12}>
          <FormControl fullWidth>
            <InputLabel>Purpose of Transfer</InputLabel>
            <Select
              value={formData.purpose}
              onChange={(e) => setFormData(prev => ({ ...prev, purpose: e.target.value }))}
              label="Purpose of Transfer"
            >
              <MenuItem value="PERSONAL">Personal/Family Support</MenuItem>
              <MenuItem value="BUSINESS">Business Payment</MenuItem>
              <MenuItem value="EDUCATION">Education Expenses</MenuItem>
              <MenuItem value="MEDICAL">Medical Expenses</MenuItem>
              <MenuItem value="PROPERTY">Property Purchase</MenuItem>
              <MenuItem value="INVESTMENT">Investment</MenuItem>
              <MenuItem value="OTHER">Other</MenuItem>
            </Select>
            <FormHelperText>Required for regulatory compliance</FormHelperText>
          </FormControl>
        </Grid>

        <Grid item xs={12}>
          <TextField
            fullWidth
            label="Purpose Details"
            multiline
            rows={3}
            value={formData.purposeDetails}
            onChange={(e) => setFormData(prev => ({ ...prev, purposeDetails: e.target.value }))}
            placeholder="Please provide additional details about this transfer..."
            helperText="Optional: Additional information helps ensure smooth processing"
          />
        </Grid>

        <Grid item xs={12}>
          <Alert severity="info">
            <Typography variant="subtitle2" gutterBottom>
              Regulatory Information
            </Typography>
            <Typography variant="body2">
              • All transfers are subject to regulatory review
              <br />
              • Additional documentation may be required for amounts over $10,000
              <br />
              • Business transfers require invoice or contract documentation
            </Typography>
          </Alert>
        </Grid>
      </Grid>
    </Box>
  );

  const renderReviewStep = () => {
    if (!quote || !formData.recipient) return null;

    return (
      <Box>
        <Grid container spacing={3}>
          {/* Transfer Summary */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Transfer Summary
                </Typography>
                <List>
                  <ListItem>
                    <ListItemText 
                      primary="You Send"
                      secondary={`${formData.sourceCurrency} ${parseFloat(formData.sourceAmount).toFixed(2)}`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText 
                      primary="Exchange Rate"
                      secondary={`1 ${quote.sourceCurrency} = ${quote.exchangeRate} ${quote.targetCurrency}`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText 
                      primary="Transfer Fee"
                      secondary={`${quote.sourceCurrency} ${quote.transferFee.toFixed(2)}`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText 
                      primary="Total Cost"
                      secondary={`${quote.sourceCurrency} ${(quote.sourceAmount + quote.totalFees).toFixed(2)}`}
                    />
                  </ListItem>
                  <Divider />
                  <ListItem>
                    <ListItemText 
                      primary="Recipient Gets"
                      secondary={
                        <Typography variant="h6" color="primary">
                          {quote.targetCurrency} {quote.targetAmount.toFixed(2)}
                        </Typography>
                      }
                    />
                  </ListItem>
                </List>
              </CardContent>
            </Card>
          </Grid>

          {/* Recipient Details */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Recipient Details
                </Typography>
                <List>
                  <ListItem>
                    <ListItemIcon>
                      <PersonIcon />
                    </ListItemIcon>
                    <ListItemText 
                      primary="Name"
                      secondary={formData.recipient.fullName}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <AccountBalanceIcon />
                    </ListItemIcon>
                    <ListItemText 
                      primary="Bank"
                      secondary={formData.recipient.bankName}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <FlagIcon />
                    </ListItemIcon>
                    <ListItemText 
                      primary="Country"
                      secondary={formData.recipient.country}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <TimerIcon />
                    </ListItemIcon>
                    <ListItemText 
                      primary="Estimated Delivery"
                      secondary={format(new Date(quote.estimatedDelivery), 'PPP')}
                    />
                  </ListItem>
                </List>
              </CardContent>
            </Card>
          </Grid>

          {/* Security Notice */}
          <Grid item xs={12}>
            <Alert severity="success" icon={<SecurityIcon />}>
              <Typography variant="subtitle2" gutterBottom>
                Secure Transfer
              </Typography>
              <Typography variant="body2">
                • Your transfer is protected by bank-level encryption
                <br />
                • Regulated and licensed in all operating countries
                <br />
                • Real-time tracking available after confirmation
              </Typography>
            </Alert>
          </Grid>
        </Grid>
      </Box>
    );
  };

  const getStepContent = (step: number) => {
    switch (step) {
      case 0:
        return renderAmountStep();
      case 1:
        return renderRecipientStep();
      case 2:
        return renderDetailsStep();
      case 3:
        return renderReviewStep();
      default:
        return 'Unknown step';
    }
  };

  return (
    <Box>
      <Card>
        <CardContent>
          <Typography variant="h5" gutterBottom>
            International Transfer
          </Typography>
          
          <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
            {steps.map((label) => (
              <Step key={label}>
                <StepLabel>{label}</StepLabel>
              </Step>
            ))}
          </Stepper>

          <Box sx={{ mt: 3, mb: 3 }}>
            {getStepContent(activeStep)}
          </Box>

          <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
            <Button
              disabled={activeStep === 0}
              onClick={handleBack}
            >
              Back
            </Button>
            <Button
              variant="contained"
              onClick={handleNext}
              disabled={loading}
              startIcon={loading ? <CircularProgress size={20} /> : 
                        activeStep === steps.length - 1 ? <SendIcon /> : null}
            >
              {activeStep === steps.length - 1 ? 'Confirm Transfer' : 'Next'}
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Quote Details Dialog */}
      <Dialog
        open={showQuoteDetails}
        onClose={() => setShowQuoteDetails(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Transfer Quote</DialogTitle>
        <DialogContent>
          {quote && (
            <Box>
              <Alert severity="info" sx={{ mb: 2 }}>
                This quote is valid until {format(new Date(quote.expiresAt), 'PPp')}
              </Alert>
              <Typography variant="body1" gutterBottom>
                Transfer Amount: {quote.sourceCurrency} {quote.sourceAmount.toFixed(2)}
              </Typography>
              <Typography variant="body1" gutterBottom>
                Transfer Fee: {quote.sourceCurrency} {quote.transferFee.toFixed(2)}
              </Typography>
              <Typography variant="body1" gutterBottom>
                Total: {quote.sourceCurrency} {(quote.sourceAmount + quote.totalFees).toFixed(2)}
              </Typography>
              <Typography variant="body1" gutterBottom>
                Recipient Receives: {quote.targetCurrency} {quote.targetAmount.toFixed(2)}
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowQuoteDetails(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default InternationalTransferForm;