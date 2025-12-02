import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Alert,
  Stepper,
  Step,
  StepLabel,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
  useTheme,
  alpha,
} from '@mui/material';
import BankIcon from '@mui/icons-material/AccountBalance';
import InstantIcon from '@mui/icons-material/Speed';
import SecurityIcon from '@mui/icons-material/Security';
import CheckIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import CloseIcon from '@mui/icons-material/Close';
import RefreshIcon from '@mui/icons-material/Refresh';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { useMutation, useQuery } from 'react-query';
import { toast } from 'react-toastify';
import { formatCurrency } from '../../utils/formatters';

// Form validation schema
const transferSchema = yup.object().shape({
  amount: yup
    .number()
    .required('Amount is required')
    .min(1, 'Amount must be at least $1')
    .max(25000, 'Amount cannot exceed $25,000 for instant transfers'),
  recipientAccountId: yup
    .string()
    .required('Please select a recipient bank account'),
  memo: yup
    .string()
    .max(100, 'Memo cannot exceed 100 characters'),
});

interface TransferFormData {
  amount: number;
  recipientAccountId: string;
  memo?: string;
}

interface BankAccount {
  id: string;
  accountName: string;
  accountNumber: string;
  bankName: string;
  routingNumber: string;
  accountType: 'CHECKING' | 'SAVINGS';
  isVerified: boolean;
  isInstantEligible: boolean;
  balance?: number;
}

interface InstantTransferFee {
  amount: number;
  percentage: number;
  minimumFee: number;
  maximumFee: number;
}

interface InstantBankTransferProps {
  onTransferComplete?: (transferId: string) => void;
  preselectedAmount?: number;
  preselectedAccount?: string;
}

const InstantBankTransfer: React.FC<InstantBankTransferProps> = ({
  onTransferComplete,
  preselectedAmount,
  preselectedAccount,
}) => {
  const theme = useTheme();
  const [activeStep, setActiveStep] = useState(0);
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState<BankAccount | null>(null);
  const [calculatedFee, setCalculatedFee] = useState<number>(0);

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isValid },
  } = useForm<TransferFormData>({
    resolver: yupResolver(transferSchema),
    defaultValues: {
      amount: preselectedAmount || 0,
      recipientAccountId: preselectedAccount || '',
      memo: '',
    },
  });

  const watchedAmount = watch('amount');
  const watchedAccountId = watch('recipientAccountId');

  // Fetch user's bank accounts
  const { data: bankAccounts = [], isLoading: loadingAccounts } = useQuery(
    'user-bank-accounts',
    async () => {
      // This would call your actual API
      return [
        {
          id: '1',
          accountName: 'My Checking Account',
          accountNumber: '****1234',
          bankName: 'Chase Bank',
          routingNumber: '021000021',
          accountType: 'CHECKING' as const,
          isVerified: true,
          isInstantEligible: true,
          balance: 5420.50,
        },
        {
          id: '2',
          accountName: 'Savings Account',
          accountNumber: '****5678',
          bankName: 'Wells Fargo',
          routingNumber: '121042882',
          accountType: 'SAVINGS' as const,
          isVerified: true,
          isInstantEligible: false,
          balance: 12350.75,
        },
      ];
    }
  );

  // Fetch instant transfer fees
  const { data: feeStructure } = useQuery(
    'instant-transfer-fees',
    async (): Promise<InstantTransferFee> => {
      return {
        amount: 0,
        percentage: 1.5,
        minimumFee: 0.25,
        maximumFee: 15.00,
      };
    }
  );

  // Transfer mutation
  const transferMutation = useMutation(
    async (data: TransferFormData) => {
      // This would call your actual transfer API
      await new Promise(resolve => setTimeout(resolve, 2000)); // Simulate API call
      return {
        transferId: 'txn_' + Date.now(),
        status: 'completed',
      };
    },
    {
      onSuccess: (result) => {
        toast.success('Transfer completed successfully!');
        setActiveStep(2);
        onTransferComplete?.(result.transferId);
      },
      onError: () => {
        toast.error('Transfer failed. Please try again.');
      },
    }
  );

  // Calculate fees when amount changes
  useEffect(() => {
    if (watchedAmount > 0 && feeStructure) {
      const percentageFee = (watchedAmount * feeStructure.percentage) / 100;
      const fee = Math.min(
        Math.max(percentageFee, feeStructure.minimumFee),
        feeStructure.maximumFee
      );
      setCalculatedFee(fee);
    } else {
      setCalculatedFee(0);
    }
  }, [watchedAmount, feeStructure]);

  // Update selected account when selection changes
  useEffect(() => {
    const account = bankAccounts.find(acc => acc.id === watchedAccountId);
    setSelectedAccount(account || null);
  }, [watchedAccountId, bankAccounts]);

  const steps = ['Enter Details', 'Confirm Transfer', 'Transfer Complete'];

  const onSubmit = (data: TransferFormData) => {
    setConfirmDialogOpen(true);
  };

  const handleConfirmTransfer = () => {
    const formData = {
      amount: watchedAmount,
      recipientAccountId: watchedAccountId,
      memo: watch('memo'),
    };
    transferMutation.mutate(formData);
    setConfirmDialogOpen(false);
    setActiveStep(1);
  };

  const getStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            {/* Amount Input */}
            <Controller
              name="amount"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label="Transfer Amount"
                  type="number"
                  fullWidth
                  variant="outlined"
                  error={!!errors.amount}
                  helperText={errors.amount?.message}
                  InputProps={{
                    startAdornment: <Typography sx={{ mr: 1 }}>$</Typography>,
                  }}
                  sx={{ mb: 3 }}
                />
              )}
            />

            {/* Bank Account Selection */}
            <Typography variant="h6" gutterBottom>
              Select Bank Account
            </Typography>
            <Controller
              name="recipientAccountId"
              control={control}
              render={({ field }) => (
                <Box>
                  {loadingAccounts ? (
                    <CircularProgress />
                  ) : (
                    bankAccounts.map((account) => (
                      <Card
                        key={account.id}
                        sx={{
                          mb: 2,
                          cursor: 'pointer',
                          border: field.value === account.id ? 2 : 1,
                          borderColor: field.value === account.id 
                            ? 'primary.main' 
                            : 'divider',
                          '&:hover': {
                            borderColor: 'primary.main',
                            backgroundColor: alpha(theme.palette.primary.main, 0.04),
                          },
                        }}
                        onClick={() => field.onChange(account.id)}
                      >
                        <CardContent>
                          <Box display="flex" justifyContent="space-between" alignItems="center">
                            <Box display="flex" alignItems="center" gap={2}>
                              <BankIcon />
                              <Box>
                                <Typography variant="subtitle1" fontWeight="bold">
                                  {account.accountName}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {account.bankName} • {account.accountNumber}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {account.accountType} • Balance: {formatCurrency(account.balance || 0)}
                                </Typography>
                              </Box>
                            </Box>
                            <Box display="flex" flexDirection="column" alignItems="end" gap={1}>
                              {account.isVerified && (
                                <Chip
                                  icon={<CheckIcon />}
                                  label="Verified"
                                  size="small"
                                  color="success"
                                />
                              )}
                              {account.isInstantEligible ? (
                                <Chip
                                  icon={<InstantIcon />}
                                  label="Instant"
                                  size="small"
                                  color="primary"
                                />
                              ) : (
                                <Chip
                                  label="1-3 Business Days"
                                  size="small"
                                  variant="outlined"
                                />
                              )}
                            </Box>
                          </Box>
                        </CardContent>
                      </Card>
                    ))
                  )}
                </Box>
              )}
            />
            {errors.recipientAccountId && (
              <Typography color="error" variant="body2" sx={{ mt: 1 }}>
                {errors.recipientAccountId.message}
              </Typography>
            )}

            {/* Memo */}
            <Controller
              name="memo"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  label="Memo (Optional)"
                  fullWidth
                  variant="outlined"
                  error={!!errors.memo}
                  helperText={errors.memo?.message}
                  sx={{ mt: 3 }}
                />
              )}
            />

            {/* Fee Information */}
            {watchedAmount > 0 && selectedAccount?.isInstantEligible && (
              <Alert severity="info" sx={{ mt: 3 }}>
                <Typography variant="body2">
                  <strong>Instant Transfer Fee:</strong> {formatCurrency(calculatedFee)}
                </Typography>
                <Typography variant="body2">
                  Total Amount: {formatCurrency(watchedAmount + calculatedFee)}
                </Typography>
              </Alert>
            )}

            {selectedAccount && !selectedAccount.isInstantEligible && (
              <Alert severity="warning" sx={{ mt: 3 }}>
                This account doesn't support instant transfers. 
                Your transfer will take 1-3 business days to complete.
              </Alert>
            )}
          </Box>
        );

      case 1:
        return (
          <Box display="flex" justifyContent="center" alignItems="center" py={4}>
            <CircularProgress size={60} />
            <Typography variant="h6" sx={{ ml: 2 }}>
              Processing your transfer...
            </Typography>
          </Box>
        );

      case 2:
        return (
          <Box textAlign="center" py={4}>
            <CheckIcon sx={{ fontSize: 80, color: 'success.main', mb: 2 }} />
            <Typography variant="h5" gutterBottom>
              Transfer Completed!
            </Typography>
            <Typography variant="body1" color="text.secondary" gutterBottom>
              Your transfer of {formatCurrency(watchedAmount)} has been processed successfully.
            </Typography>
            {selectedAccount?.isInstantEligible && (
              <Typography variant="body2" color="success.main">
                Funds should be available in your account within minutes.
              </Typography>
            )}
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Paper elevation={3} sx={{ p: 4, maxWidth: 600, mx: 'auto' }}>
      <Typography variant="h4" gutterBottom textAlign="center">
        Instant Bank Transfer
      </Typography>

      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <form onSubmit={handleSubmit(onSubmit)}>
        {getStepContent(activeStep)}

        {activeStep === 0 && (
          <Box display="flex" justifyContent="center" mt={4}>
            <Button
              type="submit"
              variant="contained"
              size="large"
              disabled={!isValid || !watchedAmount}
              endIcon={<ArrowForwardIcon />}
            >
              Continue
            </Button>
          </Box>
        )}

        {activeStep === 2 && (
          <Box display="flex" justifyContent="center" mt={4}>
            <Button
              variant="outlined"
              onClick={() => window.location.reload()}
            >
              Start New Transfer
            </Button>
          </Box>
        )}
      </form>

      {/* Confirmation Dialog */}
      <Dialog
        open={confirmDialogOpen}
        onClose={() => setConfirmDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">Confirm Transfer</Typography>
            <IconButton onClick={() => setConfirmDialogOpen(false)}>
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <List>
            <ListItem>
              <ListItemIcon>
                <BankIcon />
              </ListItemIcon>
              <ListItemText
                primary="Transfer Amount"
                secondary={formatCurrency(watchedAmount)}
              />
            </ListItem>
            {selectedAccount?.isInstantEligible && calculatedFee > 0 && (
              <ListItem>
                <ListItemIcon>
                  <InstantIcon />
                </ListItemIcon>
                <ListItemText
                  primary="Instant Transfer Fee"
                  secondary={formatCurrency(calculatedFee)}
                />
              </ListItem>
            )}
            <ListItem>
              <ListItemIcon>
                <InfoIcon />
              </ListItemIcon>
              <ListItemText
                primary="Total Amount"
                secondary={formatCurrency(watchedAmount + calculatedFee)}
              />
            </ListItem>
            <Divider />
            <ListItem>
              <ListItemIcon>
                <BankIcon />
              </ListItemIcon>
              <ListItemText
                primary="Destination Account"
                secondary={`${selectedAccount?.accountName} • ${selectedAccount?.bankName}`}
              />
            </ListItem>
            {watch('memo') && (
              <ListItem>
                <ListItemIcon>
                  <InfoIcon />
                </ListItemIcon>
                <ListItemText
                  primary="Memo"
                  secondary={watch('memo')}
                />
              </ListItem>
            )}
          </List>

          <Alert severity="info" sx={{ mt: 2 }}>
            <Typography variant="body2">
              {selectedAccount?.isInstantEligible
                ? 'This transfer will be processed instantly and funds should be available within minutes.'
                : 'This transfer will take 1-3 business days to complete.'}
            </Typography>
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            onClick={handleConfirmTransfer}
            variant="contained"
            disabled={transferMutation.isLoading}
            startIcon={transferMutation.isLoading ? <CircularProgress size={16} /> : <SecurityIcon />}
          >
            {transferMutation.isLoading ? 'Processing...' : 'Confirm Transfer'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Features Section */}
      {activeStep === 0 && (
        <Box mt={4}>
          <Typography variant="h6" gutterBottom>
            Why Choose Instant Transfers?
          </Typography>
          <List>
            <ListItem>
              <ListItemIcon>
                <InstantIcon color="primary" />
              </ListItemIcon>
              <ListItemText
                primary="Lightning Fast"
                secondary="Funds available within minutes"
              />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <SecurityIcon color="primary" />
              </ListItemIcon>
              <ListItemText
                primary="Bank-Level Security"
                secondary="256-bit encryption and fraud protection"
              />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <CheckIcon color="primary" />
              </ListItemIcon>
              <ListItemText
                primary="24/7 Availability"
                secondary="Transfer money anytime, anywhere"
              />
            </ListItem>
          </List>
        </Box>
      )}
    </Paper>
  );
};

export default InstantBankTransfer;