import React, { useState, useEffect } from 'react';
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
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  CircularProgress,
  Divider,
  Paper,
  IconButton,
  FormControlLabel,
  Switch,
  Tooltip,
  Badge,
  ToggleButton,
  ToggleButtonGroup,
  Collapse,
  FormHelperText,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import CheckIcon from '@mui/icons-material/Check';
import WarningIcon from '@mui/icons-material/Warning';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CloseIcon from '@mui/icons-material/Close';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import RepeatIcon from '@mui/icons-material/Repeat';
import PeopleIcon from '@mui/icons-material/People';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import FacebookIcon from '@mui/icons-material/Facebook';
import TwitterIcon from '@mui/icons-material/Twitter';
import ScheduleIcon from '@mui/icons-material/Schedule';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import GroupIcon from '@mui/icons-material/Group';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';
import QRCode from 'qrcode.react';
import { motion, AnimatePresence } from 'framer-motion';

import { paymentService } from '@/services/paymentService';
import { RequestMoneyRequest } from '@/types/payment';
import { Contact } from '@/types/contact';
import { formatCurrency, formatDate } from '@/utils/formatters';

const schema = yup.object().shape({
  recipients: yup.array()
    .min(1, 'At least one recipient is required')
    .required('Recipients are required'),
  amount: yup.number()
    .required('Amount is required')
    .positive('Amount must be positive')
    .max(10000, 'Maximum amount is $10,000'),
  currency: yup.string().required('Currency is required'),
  note: yup.string()
    .required('Please add a description')
    .max(500, 'Note cannot exceed 500 characters'),
  dueDate: yup.date()
    .min(new Date(), 'Due date cannot be in the past')
    .nullable(),
  isRecurring: yup.boolean(),
  recurringFrequency: yup.string().when('isRecurring', {
    is: true,
    then: yup.string().required('Please select recurring frequency'),
  }),
  splitType: yup.string().oneOf(['equal', 'custom', 'percentage']),
});

interface Recipient {
  id: string;
  name: string;
  email: string;
  phone?: string;
  avatar?: string;
  splitAmount?: number;
  splitPercentage?: number;
}

interface FormData {
  recipients: Recipient[];
  amount: number;
  currency: string;
  note: string;
  dueDate: Date | null;
  isRecurring: boolean;
  recurringFrequency: string;
  splitType: 'equal' | 'custom' | 'percentage';
  includeMessage: boolean;
  customMessage: string;
  notificationChannels: string[];
}

const steps = ['Select Recipients', 'Enter Details', 'Review & Send'];

const RequestMoneyForm: React.FC = () => {
  const [activeStep, setActiveStep] = useState(0);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [recipientSearchTerm, setRecipientSearchTerm] = useState('');
  const [requestUrl, setRequestUrl] = useState('');

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
      fromUser: '',
      amount: 0,
      currency: 'USD',
      note: '',
      expiresAt: '',
    },
  });

  const watchedValues = watch();

  // Fetch contacts for recipient selection
  const { data: contacts, isLoading: contactsLoading } = useQuery(
    ['contacts', recipientSearchTerm],
    () => paymentService.searchContacts(recipientSearchTerm),
    { enabled: recipientSearchTerm.length > 2 }
  );

  // Request money mutation
  const requestMoneyMutation = useMutation<any, Error, RequestMoneyRequest>(
    (data) => paymentService.requestMoney(data),
    {
      onSuccess: (response) => {
        setRequestUrl(response.requestUrl);
        setActiveStep(3);
        toast.success('Payment request created successfully!');
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to create payment request');
      },
    }
  );

  const handleNext = () => {
    if (activeStep === 2) {
      handleCreateRequest();
    } else {
      setActiveStep((prev) => prev + 1);
    }
  };

  const handleBack = () => {
    setActiveStep((prev) => prev - 1);
  };

  const handleCreateRequest = async () => {
    const formData = getValues();
    const requestData: RequestMoneyRequest = {
      fromUserId: selectedContact?.id,
      fromEmail: selectedContact?.email || formData.fromUser,
      amount: formData.amount,
      currency: formData.currency,
      note: formData.note,
      expiresAt: formData.expiresAt || undefined,
    };

    requestMoneyMutation.mutate(requestData);
  };

  const handleContactSelect = (contact: Contact) => {
    setSelectedContact(contact);
    setValue('fromUser', contact.email);
    setRecipientSearchTerm('');
    setActiveStep(1);
  };

  const handleCopyLink = () => {
    navigator.clipboard.writeText(requestUrl);
    toast.success('Request link copied to clipboard!');
  };

  const handleShareRequest = () => {
    if (navigator.share) {
      navigator.share({
        title: 'Payment Request',
        text: `I've requested $${watchedValues.amount} from you via Waqiti`,
        url: requestUrl,
      });
    } else {
      handleCopyLink();
    }
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Who should send you money?
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
              name="fromUser"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label="Payer Email or Phone"
                  error={!!errors.fromUser}
                  helperText={errors.fromUser?.message}
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

            {/* Search Results */}
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

            {/* Recent Contacts */}
            <Card>
              <CardContent>
                <Typography variant="subtitle2" gutterBottom>
                  Recent Contacts
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
              How much are you requesting?
            </Typography>

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

            <Controller
              name="expiresAt"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label="Expires At (optional)"
                  type="datetime-local"
                  InputLabelProps={{
                    shrink: true,
                  }}
                  sx={{ mt: 3 }}
                />
              )}
            />

            <Alert severity="info" sx={{ mt: 3 }}>
              <Typography variant="body2">
                The recipient will receive a link to pay this request. If no expiration is set, 
                the request will expire after 7 days.
              </Typography>
            </Alert>
          </Box>
        );

      case 2:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Review Payment Request
            </Typography>

            <Card>
              <CardContent>
                <Grid container spacing={2}>
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Requesting from:
                    </Typography>
                    <Box display="flex" alignItems="center" mt={1}>
                      <Avatar sx={{ mr: 2 }}>
                        {selectedContact?.name?.charAt(0) || 'U'}
                      </Avatar>
                      <Box>
                        <Typography variant="body1">
                          {selectedContact?.name || 'Unknown User'}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {watchedValues.fromUser}
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
                      Expires:
                    </Typography>
                    <Typography variant="h6">
                      {watchedValues.expiresAt 
                        ? new Date(watchedValues.expiresAt).toLocaleDateString()
                        : '7 days'
                      }
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

            <Alert severity="info" sx={{ mt: 2 }}>
              <Typography variant="body2">
                Once created, a shareable link will be generated that you can send to the recipient.
              </Typography>
            </Alert>
          </Box>
        );

      case 3:
        return (
          <Box textAlign="center">
            <Check color="success" sx={{ fontSize: 64, mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Payment Request Created!
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Your payment request has been created successfully. Share the link below with{' '}
              {selectedContact?.name || watchedValues.fromUser}.
            </Typography>

            <Paper sx={{ p: 2, mt: 3, bgcolor: 'grey.50' }}>
              <Typography variant="subtitle2" gutterBottom>
                Request Link:
              </Typography>
              <Typography 
                variant="body2" 
                sx={{ 
                  fontFamily: 'monospace',
                  wordBreak: 'break-all',
                  mb: 2
                }}
              >
                {requestUrl || `https://waqiti.com/pay/request/${Date.now()}`}
              </Typography>
              <Box display="flex" gap={2} justifyContent="center">
                <Button
                  variant="outlined"
                  startIcon={<ContentCopy />}
                  onClick={handleCopyLink}
                  size="small"
                >
                  Copy Link
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<Share />}
                  onClick={handleShareRequest}
                  size="small"
                >
                  Share
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<QrCode />}
                  size="small"
                >
                  QR Code
                </Button>
              </Box>
            </Paper>

            <Card sx={{ mt: 3 }}>
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary">
                  Request Details
                </Typography>
                <Box display="flex" justifyContent="space-between" mt={1}>
                  <Typography variant="body2">Amount:</Typography>
                  <Typography variant="body2" fontWeight="bold">
                    ${watchedValues.amount?.toFixed(2)} {watchedValues.currency}
                  </Typography>
                </Box>
                <Box display="flex" justifyContent="space-between" mt={0.5}>
                  <Typography variant="body2">Expires:</Typography>
                  <Typography variant="body2">
                    {watchedValues.expiresAt 
                      ? new Date(watchedValues.expiresAt).toLocaleDateString()
                      : '7 days from now'
                    }
                  </Typography>
                </Box>
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
              (activeStep === 0 && !watchedValues.fromUser) ||
              (activeStep === 1 && (!watchedValues.amount || watchedValues.amount <= 0)) ||
              requestMoneyMutation.isLoading
            }
            endIcon={requestMoneyMutation.isLoading ? <CircularProgress size={20} /> : <RequestQuote />}
          >
            {activeStep === 2 ? 'Create Request' : 'Next'}
          </Button>
        </Box>
      )}

      {activeStep === 3 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <Button
            variant="contained"
            onClick={() => {
              setActiveStep(0);
              setValue('fromUser', '');
              setValue('amount', 0);
              setValue('note', '');
              setValue('expiresAt', '');
              setSelectedContact(null);
              setRequestUrl('');
            }}
          >
            Create Another Request
          </Button>
        </Box>
      )}
    </Box>
  );
};

export default RequestMoneyForm;