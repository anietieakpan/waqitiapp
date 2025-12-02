import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useMutation } from 'react-query';
import {
  Box,
  Container,
  Paper,
  TextField,
  Button,
  Typography,
  Stepper,
  Step,
  StepLabel,
  FormControlLabel,
  Checkbox,
  Alert,
  CircularProgress,
  InputAdornment,
  IconButton,
  Grid,
} from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import LockIcon from '@mui/icons-material/Lock';
import CheckIcon from '@mui/icons-material/Check';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';

import { authAPI } from '@/services/authAPI';
import { RegisterRequest, RegisterResponse } from '@/types/auth';

const steps = ['Personal Info', 'Contact Details', 'Security', 'Verification'];

const personalInfoSchema = yup.object().shape({
  firstName: yup.string().required('First name is required').min(2, 'Minimum 2 characters'),
  lastName: yup.string().required('Last name is required').min(2, 'Minimum 2 characters'),
  dateOfBirth: yup.date().required('Date of birth is required').max(new Date(Date.now() - 18 * 365 * 24 * 60 * 60 * 1000), 'Must be at least 18 years old'),
});

const contactDetailsSchema = yup.object().shape({
  email: yup.string().required('Email is required').email('Invalid email format'),
  phoneNumber: yup.string().required('Phone number is required').matches(/^\+?[1-9]\d{1,14}$/, 'Invalid phone number'),
  address: yup.object().shape({
    street: yup.string().required('Street address is required'),
    city: yup.string().required('City is required'),
    postalCode: yup.string().required('Postal code is required'),
    country: yup.string().required('Country is required'),
  }),
});

const securitySchema = yup.object().shape({
  password: yup.string()
    .required('Password is required')
    .min(8, 'Password must be at least 8 characters')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/, 
      'Password must contain uppercase, lowercase, number and special character'),
  confirmPassword: yup.string()
    .required('Please confirm your password')
    .oneOf([yup.ref('password')], 'Passwords must match'),
  acceptTerms: yup.boolean().oneOf([true], 'You must accept the terms and conditions'),
  acceptPrivacy: yup.boolean().oneOf([true], 'You must accept the privacy policy'),
});

interface FormData {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  email: string;
  phoneNumber: string;
  address: {
    street: string;
    city: string;
    postalCode: string;
    country: string;
  };
  password: string;
  confirmPassword: string;
  acceptTerms: boolean;
  acceptPrivacy: boolean;
}

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeStep, setActiveStep] = useState(0);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    control,
    handleSubmit,
    trigger,
    getValues,
    formState: { errors, isValid },
  } = useForm<FormData>({
    resolver: yupResolver(
      activeStep === 0 ? personalInfoSchema :
      activeStep === 1 ? contactDetailsSchema :
      securitySchema
    ),
    mode: 'onChange',
    defaultValues: {
      firstName: '',
      lastName: '',
      dateOfBirth: '',
      email: '',
      phoneNumber: '',
      address: {
        street: '',
        city: '',
        postalCode: '',
        country: '',
      },
      password: '',
      confirmPassword: '',
      acceptTerms: false,
      acceptPrivacy: false,
    },
  });

  const registerMutation = useMutation<RegisterResponse, Error, RegisterRequest>(
    (data) => authAPI.register(data),
    {
      onSuccess: (response) => {
        toast.success('Registration successful! Please check your email for verification.');
        navigate('/login', { state: { email: getValues('email') } });
      },
      onError: (error: any) => {
        toast.error(error.message || 'Registration failed');
      },
    }
  );

  const handleNext = async () => {
    const isStepValid = await trigger();
    if (isStepValid) {
      if (activeStep === steps.length - 1) {
        handleRegistration();
      } else {
        setActiveStep((prevStep) => prevStep + 1);
      }
    }
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleRegistration = async () => {
    const formData = getValues();
    const registerData: RegisterRequest = {
      firstName: formData.firstName,
      lastName: formData.lastName,
      dateOfBirth: formData.dateOfBirth,
      email: formData.email,
      phoneNumber: formData.phoneNumber,
      address: formData.address,
      password: formData.password,
    };

    registerMutation.mutate(registerData);
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Grid container spacing={3}>
            <Grid item xs={12} sm={6}>
              <Controller
                name="firstName"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="First Name"
                    error={!!errors.firstName}
                    helperText={errors.firstName?.message}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Person color="action" />
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="lastName"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Last Name"
                    error={!!errors.lastName}
                    helperText={errors.lastName?.message}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Person color="action" />
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="dateOfBirth"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Date of Birth"
                    type="date"
                    InputLabelProps={{ shrink: true }}
                    error={!!errors.dateOfBirth}
                    helperText={errors.dateOfBirth?.message}
                  />
                )}
              />
            </Grid>
          </Grid>
        );

      case 1:
        return (
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Email Address"
                    type="email"
                    error={!!errors.email}
                    helperText={errors.email?.message}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Email color="action" />
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="phoneNumber"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Phone Number"
                    error={!!errors.phoneNumber}
                    helperText={errors.phoneNumber?.message}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Phone color="action" />
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="address.street"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Street Address"
                    error={!!errors.address?.street}
                    helperText={errors.address?.street?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="address.city"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="City"
                    error={!!errors.address?.city}
                    helperText={errors.address?.city?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="address.postalCode"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Postal Code"
                    error={!!errors.address?.postalCode}
                    helperText={errors.address?.postalCode?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="address.country"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Country"
                    error={!!errors.address?.country}
                    helperText={errors.address?.country?.message}
                  />
                )}
              />
            </Grid>
          </Grid>
        );

      case 2:
        return (
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <Controller
                name="password"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Password"
                    type={showPassword ? 'text' : 'password'}
                    error={!!errors.password}
                    helperText={errors.password?.message}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Lock color="action" />
                        </InputAdornment>
                      ),
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            onClick={() => setShowPassword(!showPassword)}
                            edge="end"
                          >
                            {showPassword ? <VisibilityOff /> : <Visibility />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="confirmPassword"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    fullWidth
                    label="Confirm Password"
                    type={showConfirmPassword ? 'text' : 'password'}
                    error={!!errors.confirmPassword}
                    helperText={errors.confirmPassword?.message}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Lock color="action" />
                        </InputAdornment>
                      ),
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                            edge="end"
                          >
                            {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="acceptTerms"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={<Checkbox {...field} checked={field.value} />}
                    label={
                      <Typography variant="body2">
                        I accept the{' '}
                        <Link to="/terms" target="_blank" rel="noopener">
                          Terms and Conditions
                        </Link>
                      </Typography>
                    }
                  />
                )}
              />
              {errors.acceptTerms && (
                <Typography color="error" variant="caption">
                  {errors.acceptTerms.message}
                </Typography>
              )}
            </Grid>
            <Grid item xs={12}>
              <Controller
                name="acceptPrivacy"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={<Checkbox {...field} checked={field.value} />}
                    label={
                      <Typography variant="body2">
                        I accept the{' '}
                        <Link to="/privacy" target="_blank" rel="noopener">
                          Privacy Policy
                        </Link>
                      </Typography>
                    }
                  />
                )}
              />
              {errors.acceptPrivacy && (
                <Typography color="error" variant="caption">
                  {errors.acceptPrivacy.message}
                </Typography>
              )}
            </Grid>
          </Grid>
        );

      case 3:
        return (
          <Box textAlign="center">
            <Check color="success" sx={{ fontSize: 64, mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Ready to Create Account
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Please review your information and click "Create Account" to complete registration.
            </Typography>
            <Alert severity="info" sx={{ mt: 2 }}>
              You will receive an email verification link after registration.
            </Alert>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Container maxWidth="md">
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          py: 4,
        }}
      >
        <Paper elevation={3} sx={{ p: 4, width: '100%', maxWidth: 600 }}>
          <Typography variant="h4" align="center" gutterBottom>
            Create Account
          </Typography>
          <Typography variant="body2" align="center" color="text.secondary" paragraph>
            Join Waqiti and start sending money securely
          </Typography>

          <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
            {steps.map((label) => (
              <Step key={label}>
                <StepLabel>{label}</StepLabel>
              </Step>
            ))}
          </Stepper>

          <Box component="form" onSubmit={handleSubmit(handleRegistration)}>
            {renderStepContent(activeStep)}

            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
              <Button
                disabled={activeStep === 0}
                onClick={handleBack}
                sx={{ mr: 1 }}
              >
                Back
              </Button>
              <Button
                variant="contained"
                onClick={handleNext}
                disabled={registerMutation.isLoading}
                endIcon={
                  registerMutation.isLoading ? (
                    <CircularProgress size={20} />
                  ) : null
                }
              >
                {activeStep === steps.length - 1 ? 'Create Account' : 'Next'}
              </Button>
            </Box>
          </Box>

          <Box sx={{ mt: 3, textAlign: 'center' }}>
            <Typography variant="body2">
              Already have an account?{' '}
              <Link to="/login">
                Sign in
              </Link>
            </Typography>
          </Box>
        </Paper>
      </Box>
    </Container>
  );
};

export default RegisterPage;