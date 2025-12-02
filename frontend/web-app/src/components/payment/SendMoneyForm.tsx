import React, { useState } from 'react';
import { useMutation, useQuery } from 'react-query';
import {
  Box,
  Grid,
  TextField,
  Button,
  Typography,
  Card,
  CardContent,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stepper,
  Step,
  StepLabel,
  CircularProgress,
  Divider,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import PersonIcon from '@mui/icons-material/Person';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import SendIcon from '@mui/icons-material/Send';
import CheckIcon from '@mui/icons-material/Check';
import WarningIcon from '@mui/icons-material/Warning';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';

import { paymentService } from '@/services/paymentService';
import { walletService } from '@/services/walletService';
import { SendMoneyRequest, PaymentResponse } from '@/types/payment';
import { Contact } from '@/types/contact';

const schema = yup.object().shape({
  recipient: yup.string().required('Recipient is required'),
  amount: yup.number()
    .required('Amount is required')
    .positive('Amount must be positive')
    .max(10000, 'Maximum amount is $10,000'),
  currency: yup.string().required('Currency is required'),
  note: yup.string().max(200, 'Note cannot exceed 200 characters'),
});

interface FormData {
  recipient: string;
  amount: number;
  currency: string;
  note: string;
}

const steps = ['Recipient', 'Amount', 'Confirm', 'Complete'];

const SendMoneyForm: React.FC = () => {
  const [activeStep, setActiveStep] = useState(0);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [recipientSearchTerm, setRecipientSearchTerm] = useState('');
  const [confirmationDialog, setConfirmationDialog] = useState(false);

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    getValues,
    formState: { errors, isValid },
  } = useForm<FormData>({
    resolver: yupResolver(schema),
    mode: 'onChange',
    defaultValues: {
      recipient: '',
      amount: 0,
      currency: 'USD',
      note: '',
    },
  });

  const watchedValues = watch();

  // Fetch user's wallet balance
  const { data: walletBalance, isLoading: walletLoading } = useQuery(
    'walletBalance',
    () => walletService.getBalance(),
    { refetchInterval: 30000 }
  );

  // Fetch contacts for recipient selection
  const { data: contacts, isLoading: contactsLoading } = useQuery(
    ['contacts', recipientSearchTerm],
    () => paymentService.searchContacts(recipientSearchTerm),
    { enabled: recipientSearchTerm.length > 2 }
  );

  // Send money mutation
  const sendMoneyMutation = useMutation<PaymentResponse, Error, SendMoneyRequest>(
    (data) => paymentService.sendMoney(data),
    {
      onSuccess: (response) => {
        setActiveStep(3);
        toast.success('Money sent successfully!');
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to send money');
      },
    }
  );

  const handleNext = () => {
    if (activeStep === 2) {
      setConfirmationDialog(true);
    } else {
      setActiveStep((prev) => prev + 1);
    }
  };

  const handleBack = () => {
    setActiveStep((prev) => prev - 1);
  };

  const handleSendMoney = async () => {
    const formData = getValues();
    const sendData: SendMoneyRequest = {
      recipientId: selectedContact?.id || formData.recipient,
      amount: formData.amount,
      currency: formData.currency,
      note: formData.note,
      recipientEmail: selectedContact?.email || formData.recipient,
    };

    setConfirmationDialog(false);
    sendMoneyMutation.mutate(sendData);
  };

  const handleContactSelect = (contact: Contact) => {
    setSelectedContact(contact);
    setValue('recipient', contact.email);
    setRecipientSearchTerm('');
    setActiveStep(1);
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Who are you sending money to?
            </Typography>
            
            <TextField
              fullWidth
              label="Search by email, phone, or name"
              value={recipientSearchTerm}
              onChange={(e) => setRecipientSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
              }}
              sx={{ mb: 3 }}
            />

            <Controller
              name="recipient"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label="Recipient Email or Phone"
                  error={!!errors.recipient}
                  helperText={errors.recipient?.message}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <Person />
                      </InputAdornment>
                    ),
                  }}
                  sx={{ mb: 3 }}
                />
              )}
            />

            {/* Recent Contacts */}
            {contacts && contacts.length > 0 && (
              <Card sx={{ mb: 3 }}>
                <CardContent>
                  <Typography variant="subtitle2" gutterBottom>
                    Search Results
                  </Typography>
                  <List>
                    {contacts.map((contact) => (
                      <ListItemButton
                        key={contact.id}
                        onClick={() => handleContactSelect(contact)}
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
            )}

            {/* Favorite Contacts */}
            <Card>
              <CardContent>
                <Typography variant="subtitle2" gutterBottom>
                  Favorite Contacts
                </Typography>
                <List>
                  <ListItemButton onClick={() => handleContactSelect({
                    id: '1',
                    name: 'John Doe',
                    email: 'john@example.com',
                    phone: '+1234567890'
                  })}>
                    <ListItemAvatar>
                      <Avatar>J</Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary="John Doe"
                      secondary="john@example.com"
                    />
                  </ListItemButton>
                  <ListItemButton onClick={() => handleContactSelect({
                    id: '2',
                    name: 'Jane Smith',
                    email: 'jane@example.com',
                    phone: '+1234567891'
                  })}>
                    <ListItemAvatar>
                      <Avatar>J</Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary="Jane Smith"
                      secondary="jane@example.com"
                    />
                  </ListItemButton>
                </List>
              </CardContent>
            </Card>
          </Box>
        );

      case 1:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              How much are you sending?
            </Typography>

            {/* Wallet Balance */}
            {walletBalance && (
              <Card sx={{ mb: 3, bgcolor: 'primary.50' }}>
                <CardContent>
                  <Box display="flex" alignItems="center">
                    <AccountBalanceWallet color="primary" sx={{ mr: 1 }} />
                    <Typography variant="subtitle2">
                      Available Balance: ${walletBalance.balance.toFixed(2)}
                    </Typography>
                  </Box>
                </CardContent>
              </Card>
            )}

            <Grid container spacing={3}>
              <Grid item xs={12} sm={8}>
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
                            <AttachMoney />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={4}>
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
                      </Select>
                    </FormControl>
                  )}
                />
              </Grid>
            </Grid>

            {/* Quick Amount Buttons */}
            <Box sx={{ mt: 3 }}>
              <Typography variant="subtitle2" gutterBottom>
                Quick amounts:
              </Typography>
              <Box display="flex" gap={1} flexWrap="wrap">
                {[10, 25, 50, 100, 250, 500].map((amount) => (
                  <Chip
                    key={amount}
                    label={`$${amount}`}
                    onClick={() => setValue('amount', amount)}
                    clickable
                    variant={watchedValues.amount === amount ? 'filled' : 'outlined'}
                    color="primary"
                  />
                ))}
              </Box>
            </Box>

            <Controller
              name="note"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label="Note (optional)"
                  multiline
                  rows={3}
                  error={!!errors.note}
                  helperText={errors.note?.message}
                  sx={{ mt: 3 }}
                />
              )}
            />

            {/* Fee Information */}
            <Alert severity="info" sx={{ mt: 3 }}>
              <Typography variant="body2">
                <strong>Transaction Fee:</strong> $0.00 (Free for first 3 transactions this month)
              </Typography>
            </Alert>
          </Box>
        );

      case 2:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Confirm Transaction
            </Typography>

            <Card>
              <CardContent>
                <Grid container spacing={2}>
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Sending to:
                    </Typography>
                    <Box display="flex" alignItems="center" mt={1}>
                      <Avatar sx={{ mr: 2 }}>
                        {selectedContact?.name?.charAt(0) || 'U'}
                      </Avatar>
                      <Box>
                        <Typography variant="body1">
                          {selectedContact?.name || 'Unknown Recipient'}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {watchedValues.recipient}
                        </Typography>
                      </Box>
                    </Box>
                  </Grid>

                  <Grid item xs={12}>
                    <Divider />
                  </Grid>

                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Amount:
                    </Typography>
                    <Typography variant="h6">
                      ${watchedValues.amount?.toFixed(2)} {watchedValues.currency}
                    </Typography>
                  </Grid>

                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Fee:
                    </Typography>
                    <Typography variant="h6" color="success.main">
                      $0.00
                    </Typography>
                  </Grid>

                  <Grid item xs={12}>
                    <Divider />
                  </Grid>

                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total:
                    </Typography>
                    <Typography variant="h5" color="primary">
                      ${watchedValues.amount?.toFixed(2)} {watchedValues.currency}
                    </Typography>
                  </Grid>

                  {watchedValues.note && (
                    <>
                      <Grid item xs={12}>
                        <Divider />
                      </Grid>
                      <Grid item xs={12}>
                        <Typography variant="subtitle2" color="text.secondary">
                          Note:
                        </Typography>
                        <Typography variant="body2">
                          {watchedValues.note}
                        </Typography>
                      </Grid>
                    </>
                  )}
                </Grid>
              </CardContent>
            </Card>

            {/* Warnings */}
            {walletBalance && watchedValues.amount > walletBalance.balance && (
              <Alert severity="error" sx={{ mt: 2 }}>
                Insufficient balance. You need ${(watchedValues.amount - walletBalance.balance).toFixed(2)} more.
              </Alert>
            )}
          </Box>
        );

      case 3:
        return (
          <Box textAlign="center">
            <Check color="success" sx={{ fontSize: 64, mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Money Sent Successfully!
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Your payment has been sent to {selectedContact?.name || watchedValues.recipient}
            </Typography>
            <Card sx={{ mt: 3 }}>
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary">
                  Transaction ID
                </Typography>
                <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                  TXN-{Date.now()}
                </Typography>
              </CardContent>
            </Card>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label, index) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      {renderStepContent(activeStep)}

      {activeStep < 3 && (
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
            disabled={
              (activeStep === 0 && !watchedValues.recipient) ||
              (activeStep === 1 && (!watchedValues.amount || watchedValues.amount <= 0)) ||
              sendMoneyMutation.isLoading
            }
            endIcon={sendMoneyMutation.isLoading ? <CircularProgress size={20} /> : <Send />}
          >
            {activeStep === 2 ? 'Send Money' : 'Next'}
          </Button>
        </Box>
      )}

      {activeStep === 3 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <Button
            variant="contained"
            onClick={() => {
              setActiveStep(0);
              setValue('recipient', '');
              setValue('amount', 0);
              setValue('note', '');
              setSelectedContact(null);
            }}
          >
            Send Another Payment
          </Button>
        </Box>
      )}

      {/* Confirmation Dialog */}
      <Dialog open={confirmationDialog} onClose={() => setConfirmationDialog(false)}>
        <DialogTitle>
          <Box display="flex" alignItems="center">
            <Warning color="warning" sx={{ mr: 1 }} />
            Confirm Payment
          </Box>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body1" paragraph>
            Are you sure you want to send <strong>${watchedValues.amount?.toFixed(2)} {watchedValues.currency}</strong> to{' '}
            <strong>{selectedContact?.name || watchedValues.recipient}</strong>?
          </Typography>
          <Typography variant="body2" color="text.secondary">
            This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmationDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSendMoney}
            disabled={sendMoneyMutation.isLoading}
            endIcon={sendMoneyMutation.isLoading ? <CircularProgress size={20} /> : null}
          >
            Confirm & Send
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SendMoneyForm;