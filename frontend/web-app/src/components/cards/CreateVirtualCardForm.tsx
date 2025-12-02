import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Grid,
  Typography,
  Box,
  Alert,
  InputAdornment,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { CreateVirtualCardRequest } from '@/types/card';

interface CreateVirtualCardFormProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: CreateVirtualCardRequest) => Promise<void>;
  loading: boolean;
}

const schema = yup.object({
  cardholderName: yup.string().required('Cardholder name is required'),
  street: yup.string().required('Street address is required'),
  city: yup.string().required('City is required'),
  state: yup.string().required('State is required'),
  postalCode: yup.string().required('Postal code is required'),
  country: yup.string().required('Country is required'),
  dailySpendLimit: yup
    .number()
    .positive('Must be positive')
    .nullable()
    .transform((value, originalValue) => (originalValue === '' ? null : value)),
  monthlySpendLimit: yup
    .number()
    .positive('Must be positive')
    .nullable()
    .transform((value, originalValue) => (originalValue === '' ? null : value)),
}).required();

const CreateVirtualCardForm: React.FC<CreateVirtualCardFormProps> = ({
  open,
  onClose,
  onSubmit,
  loading,
}) => {
  const [error, setError] = useState<string | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateVirtualCardRequest & { street: string; city: string; state: string; postalCode: string; country: string }>({
    resolver: yupResolver(schema),
    defaultValues: {
      cardholderName: '',
      street: '',
      city: '',
      state: '',
      postalCode: '',
      country: 'US',
      dailySpendLimit: undefined,
      monthlySpendLimit: undefined,
    },
  });

  const handleFormSubmit = async (data: any) => {
    try {
      setError(null);
      const requestData: CreateVirtualCardRequest = {
        cardholderName: data.cardholderName,
        billingAddress: {
          street: data.street,
          city: data.city,
          state: data.state,
          postalCode: data.postalCode,
          country: data.country,
        },
        dailySpendLimit: data.dailySpendLimit,
        monthlySpendLimit: data.monthlySpendLimit,
      };
      await onSubmit(requestData);
      reset();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create card');
    }
  };

  const handleClose = () => {
    reset();
    setError(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Create Virtual Card</DialogTitle>
      <form onSubmit={handleSubmit(handleFormSubmit)}>
        <DialogContent>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
            Cardholder Information
          </Typography>

          <Controller
            name="cardholderName"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Cardholder Name"
                fullWidth
                margin="normal"
                error={!!errors.cardholderName}
                helperText={errors.cardholderName?.message}
              />
            )}
          />

          <Typography variant="subtitle2" gutterBottom sx={{ mt: 3 }}>
            Billing Address
          </Typography>

          <Controller
            name="street"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Street Address"
                fullWidth
                margin="normal"
                error={!!errors.street}
                helperText={errors.street?.message}
              />
            )}
          />

          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Controller
                name="city"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="City"
                    fullWidth
                    margin="normal"
                    error={!!errors.city}
                    helperText={errors.city?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="state"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="State"
                    fullWidth
                    margin="normal"
                    error={!!errors.state}
                    helperText={errors.state?.message}
                  />
                )}
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Controller
                name="postalCode"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Postal Code"
                    fullWidth
                    margin="normal"
                    error={!!errors.postalCode}
                    helperText={errors.postalCode?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="country"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Country"
                    fullWidth
                    margin="normal"
                    error={!!errors.country}
                    helperText={errors.country?.message}
                  />
                )}
              />
            </Grid>
          </Grid>

          <Typography variant="subtitle2" gutterBottom sx={{ mt: 3 }}>
            Spending Limits (Optional)
          </Typography>

          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Controller
                name="dailySpendLimit"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Daily Limit"
                    type="number"
                    fullWidth
                    margin="normal"
                    InputProps={{
                      startAdornment: <InputAdornment position="start">$</InputAdornment>,
                    }}
                    error={!!errors.dailySpendLimit}
                    helperText={errors.dailySpendLimit?.message}
                  />
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="monthlySpendLimit"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Monthly Limit"
                    type="number"
                    fullWidth
                    margin="normal"
                    InputProps={{
                      startAdornment: <InputAdornment position="start">$</InputAdornment>,
                    }}
                    error={!!errors.monthlySpendLimit}
                    helperText={errors.monthlySpendLimit?.message}
                  />
                )}
              />
            </Grid>
          </Grid>
        </DialogContent>

        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? 'Creating...' : 'Create Card'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreateVirtualCardForm;
