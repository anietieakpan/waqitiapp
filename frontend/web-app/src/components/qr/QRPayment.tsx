import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  Card,
  CardContent,
  Avatar,
  Chip,
  Grid,
  Stepper,
  Step,
  StepLabel,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  InputAdornment,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
  CircularProgress,
  Collapse,
  Fab,
  Zoom,
  Fade,
  useTheme,
  alpha,
  LinearProgress,
  Switch,
  FormControlLabel,
  Slider,
  Badge,
} from '@mui/material';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import SendIcon from '@mui/icons-material/Send';
import CheckIcon from '@mui/icons-material/Check';
import ErrorIcon from '@mui/icons-material/Error';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import StoreIcon from '@mui/icons-material/Store';
import PersonIcon from '@mui/icons-material/Person';
import EditIcon from '@mui/icons-material/Edit';
import BackIcon from '@mui/icons-material/ArrowBack';
import ForwardIcon from '@mui/icons-material/ArrowForward';
import ReceiptIcon from '@mui/icons-material/Receipt';
import ShareIcon from '@mui/icons-material/Share';
import DownloadIcon from '@mui/icons-material/Download';
import CopyIcon from '@mui/icons-material/ContentCopy';
import VerifiedIcon from '@mui/icons-material/Verified';
import SecurityIcon from '@mui/icons-material/Security';
import SpeedIcon from '@mui/icons-material/Speed';
import LocationIcon from '@mui/icons-material/LocationOn';
import ScheduleIcon from '@mui/icons-material/Schedule';
import OfferIcon from '@mui/icons-material/LocalOffer';
import RefreshIcon from '@mui/icons-material/Refresh';
import CardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';
import WalletIcon from '@mui/icons-material/Wallet';
import InfoIcon from '@mui/icons-material/Info';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TimerIcon from '@mui/icons-material/Timer';
import PrintIcon from '@mui/icons-material/Print';;
import { QRCodeSVG } from 'qrcode.react';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../hooks/redux';
import { formatCurrency } from '../../utils/formatters';
import { PaymentMethod } from '../../types/wallet';
import toast from 'react-hot-toast';
import CountdownTimer from '../common/CountdownTimer';

interface QRPaymentProps {
  recipientType?: 'user' | 'merchant';
  recipientData?: {
    id: string;
    name: string;
    avatar?: string;
    verified?: boolean;
    location?: string;
    category?: string;
  };
  initialAmount?: number;
  description?: string;
  onComplete?: (transactionId: string) => void;
  onCancel?: () => void;
}

const QRPayment: React.FC<QRPaymentProps> = ({
  recipientType = 'user',
  recipientData,
  initialAmount,
  description: initialDescription,
  onComplete,
  onCancel,
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  
  const [activeStep, setActiveStep] = useState(0);
  const [amount, setAmount] = useState(initialAmount?.toString() || '');
  const [description, setDescription] = useState(initialDescription || '');
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<PaymentMethod | null>(null);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [transactionId, setTransactionId] = useState<string | null>(null);
  const [receiptDialogOpen, setReceiptDialogOpen] = useState(false);
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [qrExpiry, setQrExpiry] = useState<Date | null>(null);
  const [tipAmount, setTipAmount] = useState(0);
  const [includeTip, setIncludeTip] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [dynamicQR, setDynamicQR] = useState(true);
  const [confirmationCode, setConfirmationCode] = useState('');
  
  const steps = recipientType === 'merchant' 
    ? ['QR Code', 'Amount', 'Payment', 'Complete']
    : ['Details', 'Payment Method', 'Confirm', 'Complete'];

  // Mock payment methods
  const paymentMethods: PaymentMethod[] = [
    {
      id: '1',
      type: 'WALLET',
      name: 'Waqiti Balance',
      last4: '',
      balance: 1250.50,
      isDefault: true,
      icon: <WalletIcon />,
    },
    {
      id: '2',
      type: 'DEBIT_CARD',
      name: 'Debit Card',
      last4: '1234',
      icon: <CardIcon />,
    },
    {
      id: '3',
      type: 'BANK_ACCOUNT',
      name: 'Bank Account',
      last4: '5678',
      icon: <BankIcon />,
    },
  ];

  useEffect(() => {
    // Set default payment method
    const defaultMethod = paymentMethods.find(m => m.isDefault) || paymentMethods[0];
    setSelectedPaymentMethod(defaultMethod);
  }, []);

  useEffect(() => {
    if (recipientType === 'merchant' && activeStep === 0) {
      generateMerchantQR();
    }
  }, [recipientType, activeStep]);

  const generateMerchantQR = async () => {
    setRefreshing(true);
    try {
      // Simulate API call to generate merchant QR
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      const qrData = {
        type: 'merchant_payment',
        merchantId: recipientData?.id,
        merchantName: recipientData?.name,
        timestamp: new Date().toISOString(),
        sessionId: Date.now().toString(),
        expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(), // 5 minutes
      };
      
      setQrCode(JSON.stringify(qrData));
      setQrExpiry(new Date(Date.now() + 5 * 60 * 1000));
    } catch (error) {
      setError('Failed to generate QR code');
    } finally {
      setRefreshing(false);
    }
  };

  const handleNext = () => {
    if (activeStep === steps.length - 1) {
      if (onComplete && transactionId) {
        onComplete(transactionId);
      } else {
        navigate('/transactions');
      }
    } else {
      if (activeStep === 0 && recipientType === 'user' && !amount) {
        setError('Please enter an amount');
        return;
      }
      setActiveStep(prev => prev + 1);
    }
  };

  const handleBack = () => {
    setActiveStep(prev => prev - 1);
  };

  const handlePayment = async () => {
    setProcessing(true);
    setError(null);
    
    try {
      // Simulate payment processing
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // Generate transaction ID
      const txId = `TXN${Date.now()}`;
      setTransactionId(txId);
      
      // Move to complete step
      setActiveStep(steps.length - 1);
      
      toast.success('Payment successful!');
    } catch (error) {
      setError('Payment failed. Please try again.');
      toast.error('Payment failed');
    } finally {
      setProcessing(false);
    }
  };

  const calculateTotal = () => {
    const baseAmount = parseFloat(amount) || 0;
    const tip = includeTip ? (baseAmount * tipAmount / 100) : 0;
    return baseAmount + tip;
  };

  const handleDownloadReceipt = () => {
    // In a real app, generate and download PDF receipt
    toast.success('Receipt downloaded');
  };

  const handleShareReceipt = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: 'Payment Receipt',
          text: `Payment of ${formatCurrency(calculateTotal())} to ${recipientData?.name}`,
          url: `https://waqiti.com/receipt/${transactionId}`,
        });
      } catch (error) {
        console.error('Error sharing:', error);
      }
    }
  };

  const renderMerchantQRStep = () => (
    <Box>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
            <Avatar
              sx={{
                width: 60,
                height: 60,
                bgcolor: alpha(theme.palette.primary.main, 0.1),
              }}
            >
              <StoreIcon />
            </Avatar>
            <Box sx={{ flex: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="h6">{recipientData?.name}</Typography>
                {recipientData?.verified && (
                  <VerifiedIcon sx={{ fontSize: 20, color: theme.palette.primary.main }} />
                )}
              </Box>
              {recipientData?.category && (
                <Chip label={recipientData.category} size="small" />
              )}
              {recipientData?.location && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
                  <LocationIcon sx={{ fontSize: 16 }} />
                  <Typography variant="caption" color="text.secondary">
                    {recipientData.location}
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>
          
          <Divider sx={{ my: 2 }} />
          
          <Box sx={{ textAlign: 'center' }}>
            {qrCode && !refreshing ? (
              <>
                <Box
                  sx={{
                    p: 2,
                    bgcolor: 'white',
                    borderRadius: 2,
                    display: 'inline-block',
                    mb: 2,
                  }}
                >
                  <QRCodeSVG
                    value={qrCode}
                    size={200}
                    level="H"
                    includeMargin
                  />
                </Box>
                
                {qrExpiry && (
                  <Box sx={{ mb: 2 }}>
                    <CountdownTimer
                      endTime={qrExpiry}
                      onExpire={() => {
                        setQrCode(null);
                        generateMerchantQR();
                      }}
                    />
                  </Box>
                )}
                
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Show this QR code to the merchant to proceed with payment
                </Typography>
                
                <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                  <Button
                    variant="outlined"
                    startIcon={<RefreshIcon />}
                    onClick={generateMerchantQR}
                    disabled={refreshing}
                  >
                    Refresh QR
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<CopyIcon />}
                    onClick={() => {
                      navigator.clipboard.writeText(qrCode);
                      toast.success('QR data copied');
                    }}
                  >
                    Copy
                  </Button>
                </Box>
              </>
            ) : (
              <CircularProgress size={60} />
            )}
          </Box>
        </CardContent>
      </Card>
      
      <Alert severity="info" icon={<InfoIcon />}>
        <Typography variant="body2">
          This QR code is valid for 5 minutes and can only be used once
        </Typography>
      </Alert>
      
      <FormControlLabel
        control={
          <Switch
            checked={dynamicQR}
            onChange={(e) => setDynamicQR(e.target.checked)}
          />
        }
        label="Dynamic QR (recommended for security)"
        sx={{ mt: 2 }}
      />
    </Box>
  );

  const renderAmountStep = () => (
    <Box>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Enter Amount
          </Typography>
          
          <TextField
            fullWidth
            label="Amount"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            error={!!error}
            helperText={error}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <MoneyIcon />
                </InputAdornment>
              ),
              sx: { fontSize: 32, fontWeight: 600 },
            }}
            sx={{ mb: 3 }}
          />
          
          <TextField
            fullWidth
            label="Description (Optional)"
            multiline
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            sx={{ mb: 3 }}
          />
          
          {recipientType === 'merchant' && (
            <Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={includeTip}
                    onChange={(e) => setIncludeTip(e.target.checked)}
                  />
                }
                label="Add tip"
                sx={{ mb: 2 }}
              />
              
              <Collapse in={includeTip}>
                <Box sx={{ px: 2 }}>
                  <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
                    {[10, 15, 20, 25].map((tip) => (
                      <Chip
                        key={tip}
                        label={`${tip}%`}
                        onClick={() => setTipAmount(tip)}
                        color={tipAmount === tip ? 'primary' : 'default'}
                        variant={tipAmount === tip ? 'filled' : 'outlined'}
                      />
                    ))}
                  </Box>
                  <Slider
                    value={tipAmount}
                    onChange={(_, value) => setTipAmount(value as number)}
                    min={0}
                    max={30}
                    marks
                    valueLabelDisplay="auto"
                    valueLabelFormat={(value) => `${value}%`}
                  />
                  <Typography variant="body2" color="text.secondary">
                    Tip amount: {formatCurrency((parseFloat(amount) || 0) * tipAmount / 100)}
                  </Typography>
                </Box>
              </Collapse>
            </Box>
          )}
        </CardContent>
      </Card>
      
      <Paper sx={{ p: 2, bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="body2">Subtotal</Typography>
          <Typography variant="body2">
            {formatCurrency(parseFloat(amount) || 0)}
          </Typography>
        </Box>
        {includeTip && (
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
            <Typography variant="body2">Tip ({tipAmount}%)</Typography>
            <Typography variant="body2">
              {formatCurrency((parseFloat(amount) || 0) * tipAmount / 100)}
            </Typography>
          </Box>
        )}
        <Divider sx={{ my: 1 }} />
        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Typography variant="h6">Total</Typography>
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            {formatCurrency(calculateTotal())}
          </Typography>
        </Box>
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
          <Card
            key={method.id}
            sx={{
              mb: 2,
              cursor: 'pointer',
              border: selectedPaymentMethod?.id === method.id
                ? `2px solid ${theme.palette.primary.main}`
                : '1px solid transparent',
              transition: 'all 0.2s',
              '&:hover': {
                borderColor: theme.palette.primary.light,
              },
            }}
            onClick={() => setSelectedPaymentMethod(method)}
          >
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar
                  sx={{
                    bgcolor: alpha(theme.palette.primary.main, 0.1),
                    color: theme.palette.primary.main,
                  }}
                >
                  {method.icon}
                </Avatar>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                    {method.name}
                  </Typography>
                  {method.last4 && (
                    <Typography variant="body2" color="text.secondary">
                      •••• {method.last4}
                    </Typography>
                  )}
                  {method.type === 'WALLET' && method.balance !== undefined && (
                    <Typography variant="body2" color="text.secondary">
                      Balance: {formatCurrency(method.balance)}
                    </Typography>
                  )}
                </Box>
                {method.isDefault && (
                  <Chip label="Default" size="small" color="primary" />
                )}
              </Box>
            </CardContent>
          </Card>
        ))}
      </List>
      
      <Button
        fullWidth
        variant="outlined"
        startIcon={<CardIcon />}
      >
        Add Payment Method
      </Button>
    </Box>
  );

  const renderConfirmStep = () => (
    <Box>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Review Payment
          </Typography>
          
          <Box sx={{ mb: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
              <Avatar
                sx={{
                  width: 48,
                  height: 48,
                  bgcolor: alpha(theme.palette.primary.main, 0.1),
                }}
              >
                {recipientType === 'merchant' ? <StoreIcon /> : <PersonIcon />}
              </Avatar>
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                  {recipientData?.name}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {recipientType === 'merchant' ? 'Merchant' : 'Personal'}
                </Typography>
              </Box>
            </Box>
            
            <Divider sx={{ my: 2 }} />
            
            <List sx={{ py: 0 }}>
              <ListItem sx={{ px: 0 }}>
                <ListItemIcon>
                  <MoneyIcon />
                </ListItemIcon>
                <ListItemText
                  primary="Amount"
                  secondary={formatCurrency(calculateTotal())}
                />
              </ListItem>
              
              {description && (
                <ListItem sx={{ px: 0 }}>
                  <ListItemIcon>
                    <EditIcon />
                  </ListItemIcon>
                  <ListItemText
                    primary="Description"
                    secondary={description}
                  />
                </ListItem>
              )}
              
              {selectedPaymentMethod && (
                <ListItem sx={{ px: 0 }}>
                  <ListItemIcon>
                    {selectedPaymentMethod.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary="Payment Method"
                    secondary={
                      <>
                        {selectedPaymentMethod.name}
                        {selectedPaymentMethod.last4 && ` •••• ${selectedPaymentMethod.last4}`}
                      </>
                    }
                  />
                </ListItem>
              )}
            </List>
          </Box>
          
          <Alert severity="info" icon={<SecurityIcon />}>
            <Typography variant="body2">
              Your payment is secured with end-to-end encryption
            </Typography>
          </Alert>
        </CardContent>
      </Card>
      
      {recipientType === 'merchant' && (
        <TextField
          fullWidth
          label="Confirmation Code (Optional)"
          value={confirmationCode}
          onChange={(e) => setConfirmationCode(e.target.value)}
          helperText="Enter the code provided by the merchant if required"
          sx={{ mb: 2 }}
        />
      )}
    </Box>
  );

  const renderCompleteStep = () => (
    <Box sx={{ textAlign: 'center' }}>
      <Zoom in timeout={500}>
        <Avatar
          sx={{
            width: 100,
            height: 100,
            bgcolor: theme.palette.success.main,
            mx: 'auto',
            mb: 3,
          }}
        >
          <CheckCircleIcon sx={{ fontSize: 60 }} />
        </Avatar>
      </Zoom>
      
      <Typography variant="h4" gutterBottom>
        Payment Successful!
      </Typography>
      
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        {formatCurrency(calculateTotal())} sent to {recipientData?.name}
      </Typography>
      
      <Card sx={{ mb: 3, bgcolor: alpha(theme.palette.success.main, 0.05) }}>
        <CardContent>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            Transaction ID
          </Typography>
          <Typography variant="h6" sx={{ fontFamily: 'monospace', mb: 2 }}>
            {transactionId}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {format(new Date(), 'MMMM d, yyyy h:mm a')}
          </Typography>
        </CardContent>
      </Card>
      
      <Grid container spacing={2}>
        <Grid item xs={6}>
          <Button
            fullWidth
            variant="outlined"
            startIcon={<ReceiptIcon />}
            onClick={() => setReceiptDialogOpen(true)}
          >
            View Receipt
          </Button>
        </Grid>
        <Grid item xs={6}>
          <Button
            fullWidth
            variant="outlined"
            startIcon={<ShareIcon />}
            onClick={handleShareReceipt}
          >
            Share
          </Button>
        </Grid>
      </Grid>
    </Box>
  );

  const renderReceipt = () => (
    <Dialog
      open={receiptDialogOpen}
      onClose={() => setReceiptDialogOpen(false)}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">Receipt</Typography>
          <IconButton onClick={() => setReceiptDialogOpen(false)}>
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>
      <DialogContent>
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Waqiti
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Digital Payment Receipt
          </Typography>
        </Box>
        
        <Divider sx={{ my: 2 }} />
        
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Transaction ID
          </Typography>
          <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
            {transactionId}
          </Typography>
        </Box>
        
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Date & Time
          </Typography>
          <Typography variant="body1">
            {format(new Date(), 'MMMM d, yyyy h:mm a')}
          </Typography>
        </Box>
        
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Paid To
          </Typography>
          <Typography variant="body1">
            {recipientData?.name}
          </Typography>
        </Box>
        
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Amount
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {formatCurrency(calculateTotal())}
          </Typography>
        </Box>
        
        {description && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle2" color="text.secondary">
              Description
            </Typography>
            <Typography variant="body1">
              {description}
            </Typography>
          </Box>
        )}
        
        <Divider sx={{ my: 2 }} />
        
        <Box sx={{ textAlign: 'center' }}>
          <QRCodeSVG
            value={`https://waqiti.com/receipt/${transactionId}`}
            size={100}
          />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Scan to verify
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button startIcon={<PrintIcon />}>
          Print
        </Button>
        <Button startIcon={<DownloadIcon />} onClick={handleDownloadReceipt}>
          Download
        </Button>
        <Button variant="contained" onClick={() => setReceiptDialogOpen(false)}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );

  const getStepContent = () => {
    if (recipientType === 'merchant') {
      switch (activeStep) {
        case 0:
          return renderMerchantQRStep();
        case 1:
          return renderAmountStep();
        case 2:
          return renderConfirmStep();
        case 3:
          return renderCompleteStep();
        default:
          return null;
      }
    } else {
      switch (activeStep) {
        case 0:
          return renderAmountStep();
        case 1:
          return renderPaymentMethodStep();
        case 2:
          return renderConfirmStep();
        case 3:
          return renderCompleteStep();
        default:
          return null;
      }
    }
  };

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto' }}>
      <Box sx={{ mb: 3 }}>
        <Stepper activeStep={activeStep}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      </Box>
      
      <Fade in timeout={300}>
        <Box>{getStepContent()}</Box>
      </Fade>
      
      <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
        {activeStep > 0 && activeStep < steps.length - 1 && (
          <Button
            variant="outlined"
            onClick={handleBack}
            startIcon={<BackIcon />}
          >
            Back
          </Button>
        )}
        
        <Box sx={{ flex: 1 }} />
        
        {activeStep === steps.length - 1 ? (
          <Button
            variant="contained"
            onClick={handleNext}
            fullWidth
          >
            Done
          </Button>
        ) : activeStep === steps.length - 2 ? (
          <Button
            variant="contained"
            onClick={handlePayment}
            disabled={processing}
            startIcon={processing ? <CircularProgress size={20} /> : <SendIcon />}
            fullWidth
          >
            {processing ? 'Processing...' : `Pay ${formatCurrency(calculateTotal())}`}
          </Button>
        ) : (
          <Button
            variant="contained"
            onClick={handleNext}
            endIcon={<ForwardIcon />}
            disabled={activeStep === 0 && recipientType === 'merchant' && !qrCode}
          >
            Next
          </Button>
        )}
      </Box>
      
      {renderReceipt()}
    </Box>
  );
};

export default QRPayment;