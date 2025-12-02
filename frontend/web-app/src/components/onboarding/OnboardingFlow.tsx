import React, { useState } from 'react';
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Button,
  Typography,
  Card,
  CardContent,
  TextField,
  FormControlLabel,
  Checkbox,
  Alert,
  CircularProgress,
  Avatar,
  Chip,
  Grid,
  Paper,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import VerifiedIcon from '@mui/icons-material/Verified';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import SecurityIcon from '@mui/icons-material/Security';
import CelebrationIcon from '@mui/icons-material/Celebration';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import ShieldIcon from '@mui/icons-material/Shield';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import InfoIcon from '@mui/icons-material/Info';;
import { useDispatch } from 'react-redux';
import { updateUserProfile, verifyEmail, verifyPhone, setupMfa } from '../../store/slices/userSlice';
import { addPaymentMethod } from '../../store/slices/paymentSlice';
import { CreatePaymentMethodForm } from '../payment/CreatePaymentMethodForm';
import { MFADialog } from '../auth/MFADialog';

interface OnboardingFlowProps {
  onComplete: () => void;
}

interface OnboardingState {
  profile: {
    firstName: string;
    lastName: string;
    dateOfBirth: string;
    phoneNumber: string;
    address: {
      street: string;
      city: string;
      state: string;
      zipCode: string;
    };
  };
  verificationData: {
    emailToken: string;
    phoneCode: string;
  };
  mfaMethod: string;
  paymentMethod: any;
  agreesToTerms: boolean;
  agreesToPrivacy: boolean;
  optInMarketing: boolean;
}

const steps = [
  {
    label: 'Personal Information',
    description: 'Tell us about yourself',
    icon: <PersonAddIcon />,
  },
  {
    label: 'Verify Identity',
    description: 'Verify your email and phone',
    icon: <VerifiedIcon />,
  },
  {
    label: 'Add Payment Method',
    description: 'Add your first payment method',
    icon: <AccountBalanceIcon />,
  },
  {
    label: 'Enable Security',
    description: 'Set up two-factor authentication',
    icon: <SecurityIcon />,
  },
  {
    label: 'Welcome to Waqiti',
    description: 'Your account is ready!',
    icon: <CelebrationIcon />,
  },
];

export const OnboardingFlow: React.FC<OnboardingFlowProps> = ({ onComplete }) => {
  const dispatch = useDispatch();
  const [activeStep, setActiveStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const [showMfaDialog, setShowMfaDialog] = useState(false);

  const [formData, setFormData] = useState<OnboardingState>({
    profile: {
      firstName: '',
      lastName: '',
      dateOfBirth: '',
      phoneNumber: '',
      address: {
        street: '',
        city: '',
        state: '',
        zipCode: '',
      },
    },
    verificationData: {
      emailToken: '',
      phoneCode: '',
    },
    mfaMethod: 'totp',
    paymentMethod: null,
    agreesToTerms: false,
    agreesToPrivacy: false,
    optInMarketing: false,
  });

  const handleNext = async () => {
    setError('');
    setLoading(true);

    try {
      switch (activeStep) {
        case 0:
          await handleProfileSubmit();
          break;
        case 1:
          await handleVerificationSubmit();
          break;
        case 2:
          await handlePaymentMethodSubmit();
          break;
        case 3:
          await handleSecuritySubmit();
          break;
        case 4:
          onComplete();
          return;
      }
      
      setActiveStep(prev => prev + 1);
    } catch (err: any) {
      setError(err.message || 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    setActiveStep(prev => prev - 1);
  };

  const handleProfileSubmit = async () => {
    // Validate required fields
    if (!formData.profile.firstName || !formData.profile.lastName) {
      throw new Error('Please fill in all required fields');
    }

    if (!formData.agreesToTerms || !formData.agreesToPrivacy) {
      throw new Error('Please agree to the terms and privacy policy');
    }

    // Update user profile
    await dispatch(updateUserProfile({
      firstName: formData.profile.firstName,
      lastName: formData.profile.lastName,
      dateOfBirth: formData.profile.dateOfBirth,
      phoneNumber: formData.profile.phoneNumber,
      address: formData.profile.address,
    }) as any);
  };

  const handleVerificationSubmit = async () => {
    // Verify email
    if (formData.verificationData.emailToken) {
      await dispatch(verifyEmail(formData.verificationData.emailToken) as any);
    }

    // Verify phone
    if (formData.verificationData.phoneCode) {
      await dispatch(verifyPhone(formData.verificationData.phoneCode) as any);
    }
  };

  const handlePaymentMethodSubmit = async () => {
    if (!formData.paymentMethod) {
      throw new Error('Please add a payment method');
    }

    await dispatch(addPaymentMethod(formData.paymentMethod) as any);
  };

  const handleSecuritySubmit = async () => {
    if (formData.mfaMethod) {
      await dispatch(setupMfa({ method: formData.mfaMethod }) as any);
    }
  };

  const updateFormData = (path: string, value: any) => {
    setFormData(prev => {
      const newData = { ...prev };
      const keys = path.split('.');
      let current = newData;
      
      for (let i = 0; i < keys.length - 1; i++) {
        current = current[keys[i] as keyof typeof current] as any;
      }
      
      current[keys[keys.length - 1] as keyof typeof current] = value;
      return newData;
    });
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return renderProfileStep();
      case 1:
        return renderVerificationStep();
      case 2:
        return renderPaymentMethodStep();
      case 3:
        return renderSecurityStep();
      case 4:
        return renderWelcomeStep();
      default:
        return null;
    }
  };

  const renderProfileStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Let's get to know you better
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        This information helps us keep your account secure and comply with regulations.
      </Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            label="First Name"
            value={formData.profile.firstName}
            onChange={(e) => updateFormData('profile.firstName', e.target.value)}
            required
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            label="Last Name"
            value={formData.profile.lastName}
            onChange={(e) => updateFormData('profile.lastName', e.target.value)}
            required
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            label="Date of Birth"
            type="date"
            value={formData.profile.dateOfBirth}
            onChange={(e) => updateFormData('profile.dateOfBirth', e.target.value)}
            InputLabelProps={{ shrink: true }}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            label="Phone Number"
            value={formData.profile.phoneNumber}
            onChange={(e) => updateFormData('profile.phoneNumber', e.target.value)}
          />
        </Grid>
        <Grid item xs={12}>
          <TextField
            fullWidth
            label="Street Address"
            value={formData.profile.address.street}
            onChange={(e) => updateFormData('profile.address.street', e.target.value)}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <TextField
            fullWidth
            label="City"
            value={formData.profile.address.city}
            onChange={(e) => updateFormData('profile.address.city', e.target.value)}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <TextField
            fullWidth
            label="State"
            value={formData.profile.address.state}
            onChange={(e) => updateFormData('profile.address.state', e.target.value)}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <TextField
            fullWidth
            label="ZIP Code"
            value={formData.profile.address.zipCode}
            onChange={(e) => updateFormData('profile.address.zipCode', e.target.value)}
          />
        </Grid>
      </Grid>

      <Box sx={{ mt: 3 }}>
        <FormControlLabel
          control={
            <Checkbox
              checked={formData.agreesToTerms}
              onChange={(e) => updateFormData('agreesToTerms', e.target.checked)}
            />
          }
          label={
            <Typography variant="body2">
              I agree to the <a href="/terms">Terms of Service</a>
            </Typography>
          }
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={formData.agreesToPrivacy}
              onChange={(e) => updateFormData('agreesToPrivacy', e.target.checked)}
            />
          }
          label={
            <Typography variant="body2">
              I agree to the <a href="/privacy">Privacy Policy</a>
            </Typography>
          }
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={formData.optInMarketing}
              onChange={(e) => updateFormData('optInMarketing', e.target.checked)}
            />
          }
          label={
            <Typography variant="body2">
              I'd like to receive marketing communications
            </Typography>
          }
        />
      </Box>
    </Box>
  );

  const renderVerificationStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Let's verify your identity
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        We'll send verification codes to your email and phone number.
      </Typography>

      <List>
        <ListItem>
          <ListItemIcon>
            <EmailIcon color="primary" />
          </ListItemIcon>
          <ListItemText
            primary="Email Verification"
            secondary="Check your email for a verification code"
          />
          <Chip label="Pending" color="warning" />
        </ListItem>
        <Divider />
        <ListItem>
          <ListItemIcon>
            <PhoneIcon color="primary" />
          </ListItemIcon>
          <ListItemText
            primary="Phone Verification"
            secondary="We'll send an SMS to your phone"
          />
          <Chip label="Pending" color="warning" />
        </ListItem>
      </List>

      <Box sx={{ mt: 3 }}>
        <TextField
          fullWidth
          label="Email Verification Code"
          value={formData.verificationData.emailToken}
          onChange={(e) => updateFormData('verificationData.emailToken', e.target.value)}
          margin="normal"
          helperText="Enter the 6-digit code from your email"
        />
        <TextField
          fullWidth
          label="Phone Verification Code"
          value={formData.verificationData.phoneCode}
          onChange={(e) => updateFormData('verificationData.phoneCode', e.target.value)}
          margin="normal"
          helperText="Enter the 6-digit code from your phone"
        />
      </Box>
    </Box>
  );

  const renderPaymentMethodStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Add your first payment method
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Add a payment method to start sending and receiving money.
      </Typography>

      <CreatePaymentMethodForm
        onSuccess={(method) => updateFormData('paymentMethod', method)}
        onCancel={() => {}}
        onLoading={setLoading}
      />
    </Box>
  );

  const renderSecurityStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Keep your account secure
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Two-factor authentication adds an extra layer of security to your account.
      </Typography>

      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          We recommend enabling 2FA for maximum security. You can always change this later in settings.
        </Typography>
      </Alert>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <ShieldIcon color="primary" sx={{ mr: 1 }} />
          <Typography variant="subtitle1">Two-Factor Authentication</Typography>
        </Box>
        <List>
          <ListItem>
            <ListItemIcon>
              <CheckCircleIcon color="success" />
            </ListItemIcon>
            <ListItemText
              primary="Authenticator App"
              secondary="Use Google Authenticator or similar app"
            />
          </ListItem>
          <ListItem>
            <ListItemIcon>
              <InfoIcon color="info" />
            </ListItemIcon>
            <ListItemText
              primary="SMS Backup"
              secondary="Receive codes via text message"
            />
          </ListItem>
        </List>
      </Paper>

      <Button
        fullWidth
        variant="contained"
        onClick={() => setShowMfaDialog(true)}
        startIcon={<SecurityIcon />}
      >
        Set Up Two-Factor Authentication
      </Button>
    </Box>
  );

  const renderWelcomeStep = () => (
    <Box textAlign="center">
      <Avatar sx={{ width: 80, height: 80, bgcolor: 'primary.main', mx: 'auto', mb: 2 }}>
        <CelebrationIcon sx={{ fontSize: 40 }} />
      </Avatar>
      <Typography variant="h4" gutterBottom>
        Welcome to Waqiti!
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        Your account is now set up and ready to use. You can start sending and receiving money right away.
      </Typography>

      <Grid container spacing={2} sx={{ mt: 3 }}>
        <Grid item xs={12} sm={4}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <CreditCardIcon color="primary" sx={{ fontSize: 40, mb: 1 }} />
            <Typography variant="h6">Send Money</Typography>
            <Typography variant="body2" color="text.secondary">
              Send money to friends and family instantly
            </Typography>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <AccountBalanceIcon color="primary" sx={{ fontSize: 40, mb: 1 }} />
            <Typography variant="h6">Manage Wallet</Typography>
            <Typography variant="body2" color="text.secondary">
              Add money and manage your balance
            </Typography>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <SecurityIcon color="primary" sx={{ fontSize: 40, mb: 1 }} />
            <Typography variant="h6">Stay Secure</Typography>
            <Typography variant="body2" color="text.secondary">
              Your account is protected with bank-level security
            </Typography>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Stepper activeStep={activeStep} orientation="vertical">
        {steps.map((step, index) => (
          <Step key={step.label}>
            <StepLabel
              icon={
                <Avatar sx={{ bgcolor: activeStep >= index ? 'primary.main' : 'grey.400' }}>
                  {step.icon}
                </Avatar>
              }
            >
              <Typography variant="h6">{step.label}</Typography>
              <Typography variant="body2" color="text.secondary">
                {step.description}
              </Typography>
            </StepLabel>
            <StepContent>
              <Card sx={{ mb: 2 }}>
                <CardContent>
                  {error && (
                    <Alert severity="error" sx={{ mb: 2 }}>
                      {error}
                    </Alert>
                  )}
                  {renderStepContent(index)}
                </CardContent>
              </Card>
              <Box sx={{ mb: 2 }}>
                <Button
                  variant="contained"
                  onClick={handleNext}
                  disabled={loading}
                  sx={{ mt: 1, mr: 1 }}
                >
                  {loading ? (
                    <CircularProgress size={20} />
                  ) : index === steps.length - 1 ? (
                    'Get Started'
                  ) : (
                    'Continue'
                  )}
                </Button>
                <Button
                  disabled={index === 0 || loading}
                  onClick={handleBack}
                  sx={{ mt: 1, mr: 1 }}
                >
                  Back
                </Button>
              </Box>
            </StepContent>
          </Step>
        ))}
      </Stepper>

      <MFADialog
        open={showMfaDialog}
        onClose={() => setShowMfaDialog(false)}
        onSuccess={() => {
          setShowMfaDialog(false);
          updateFormData('mfaMethod', 'totp');
        }}
        username={formData.profile.firstName}
        requireSetup={true}
      />
    </Box>
  );
};

export default OnboardingFlow;