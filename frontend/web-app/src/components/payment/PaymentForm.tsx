import React, { useState, useCallback, useEffect } from 'react';
import {
  Box,
  TextField,
  Button,
  Typography,
  Paper,
  Autocomplete,
  InputAdornment,
  Alert,
  CircularProgress,
  Divider,
  Grid,
} from '@mui/material';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { debounce } from 'lodash';
import { paymentService } from '../../services/paymentService';
import { SendMoneyRequest } from '../../types/payment';

interface PaymentFormProps {
  onSuccess: (result: any) => void;
  onCancel: () => void;
}

interface Recipient {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
}

const validationSchema = yup.object({
  recipient: yup.object().nullable().required('Recipient is required'),
  amount: yup
    .number()
    .required('Amount is required')
    .min(0.01, 'Amount must be greater than 0')
    .max(10000, 'Amount cannot exceed $10,000'),
  note: yup.string().max(200, 'Note cannot exceed 200 characters'),
});

const PaymentForm: React.FC<PaymentFormProps> = ({ onSuccess, onCancel }) => {
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [recipientSearch, setRecipientSearch] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [feeInfo, setFeeInfo] = useState<{
    fee: number;
    total: number;
    exchangeRate?: number;
  } | null>(null);
  const [isFeeLoading, setIsFeeLoading] = useState(false);
  const [feeError, setFeeError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const formik = useFormik({
    initialValues: {
      recipient: null as Recipient | null,
      amount: '',
      currency: 'USD',
      note: '',
    },
    validationSchema,
    onSubmit: async (values) => {
      if (!values.recipient) return;
      
      setIsSubmitting(true);
      setSubmitError(null);

      try {
        const request: SendMoneyRequest = {
          recipientId: values.recipient.id,
          amount: parseFloat(values.amount),
          currency: values.currency,
          note: values.note || undefined,
        };

        const result = await paymentService.sendMoney(request);
        onSuccess(result);
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : 'Payment failed';
        setSubmitError(errorMessage);
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  // Debounced search function
  const debouncedSearch = useCallback(
    debounce(async (searchQuery: string) => {
      if (!searchQuery || searchQuery.length < 2) {
        setRecipients([]);
        return;
      }

      setIsSearching(true);
      try {
        const results = await paymentService.searchUsers(searchQuery);
        setRecipients(results);
      } catch (error) {
        console.error('Search failed:', error);
        setRecipients([]);
      } finally {
        setIsSearching(false);
      }
    }, 300),
    []
  );

  // Effect to search for recipients
  useEffect(() => {
    debouncedSearch(recipientSearch);
  }, [recipientSearch, debouncedSearch]);

  // Effect to calculate fee when amount changes
  useEffect(() => {
    const calculateFee = async () => {
      const amount = parseFloat(formik.values.amount);
      if (!amount || amount <= 0) {
        setFeeInfo(null);
        return;
      }

      setIsFeeLoading(true);
      setFeeError(null);

      try {
        const fee = await paymentService.calculateFee({
          amount,
          currency: formik.values.currency,
        });
        setFeeInfo(fee);
      } catch (error) {
        setFeeError('Unable to calculate fee');
        setFeeInfo(null);
      } finally {
        setIsFeeLoading(false);
      }
    };

    const debounced = debounce(calculateFee, 500);
    debounced();

    return () => debounced.cancel();
  }, [formik.values.amount, formik.values.currency]);

  return (
    <Paper elevation={2} sx={{ p: 3, maxWidth: 600, mx: 'auto' }}>
      <Typography variant="h5" component="h2" gutterBottom>
        Send Money
      </Typography>
      
      <Box component="form" onSubmit={formik.handleSubmit} noValidate>
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <Autocomplete
              options={recipients}
              getOptionLabel={(option) => 
                `${option.firstName} ${option.lastName} (${option.email})`
              }
              value={formik.values.recipient}
              onChange={(_, newValue) => {
                formik.setFieldValue('recipient', newValue);
              }}
              inputValue={recipientSearch}
              onInputChange={(_, newInputValue) => {
                setRecipientSearch(newInputValue);
              }}
              loading={isSearching}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Recipient"
                  placeholder="Search by name or email"
                  error={formik.touched.recipient && Boolean(formik.errors.recipient)}
                  helperText={formik.touched.recipient && formik.errors.recipient}
                  InputProps={{
                    ...params.InputProps,
                    endAdornment: (
                      <>
                        {isSearching ? <CircularProgress color="inherit" size={20} /> : null}
                        {params.InputProps.endAdornment}
                      </>
                    ),
                  }}
                />
              )}
              renderOption={(props, option) => (
                <Box component="li" {...props}>
                  <Box>
                    <Typography variant="body1">
                      {option.firstName} {option.lastName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {option.email}
                    </Typography>
                  </Box>
                </Box>
              )}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              name="amount"
              label="Amount"
              type="number"
              value={formik.values.amount}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.amount && Boolean(formik.errors.amount)}
              helperText={formik.touched.amount && formik.errors.amount}
              InputProps={{
                startAdornment: <InputAdornment position="start">$</InputAdornment>,
              }}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              name="currency"
              label="Currency"
              select
              SelectProps={{ native: true }}
              value={formik.values.currency}
              onChange={formik.handleChange}
            >
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
              <option value="CAD">CAD</option>
            </TextField>
          </Grid>

          <Grid item xs={12}>
            <TextField
              fullWidth
              name="note"
              label="Note (optional)"
              multiline
              rows={2}
              value={formik.values.note}
              onChange={formik.handleChange}
              onBlur={formik.handleBlur}
              error={formik.touched.note && Boolean(formik.errors.note)}
              helperText={formik.touched.note && formik.errors.note}
              placeholder="Add a note for this payment"
            />
          </Grid>

          {/* Fee Information */}
          {(feeInfo || isFeeLoading || feeError) && (
            <Grid item xs={12}>
              <Paper variant="outlined" sx={{ p: 2, bgcolor: 'grey.50' }}>
                <Typography variant="subtitle2" gutterBottom>
                  Payment Summary
                </Typography>
                {isFeeLoading ? (
                  <Box display="flex" alignItems="center" gap={1}>
                    <CircularProgress size={16} />
                    <Typography variant="body2">Calculating fee...</Typography>
                  </Box>
                ) : feeError ? (
                  <Typography variant="body2" color="error">
                    {feeError}
                  </Typography>
                ) : feeInfo ? (
                  <Box>
                    <Box display="flex" justifyContent="space-between">
                      <Typography variant="body2">Amount:</Typography>
                      <Typography variant="body2">
                        ${parseFloat(formik.values.amount || '0').toFixed(2)}
                      </Typography>
                    </Box>
                    <Box display="flex" justifyContent="space-between">
                      <Typography variant="body2">Fee:</Typography>
                      <Typography variant="body2">${feeInfo.fee.toFixed(2)}</Typography>
                    </Box>
                    <Divider sx={{ my: 1 }} />
                    <Box display="flex" justifyContent="space-between">
                      <Typography variant="subtitle2">Total:</Typography>
                      <Typography variant="subtitle2" fontWeight="bold">
                        ${feeInfo.total.toFixed(2)}
                      </Typography>
                    </Box>
                  </Box>
                ) : null}
              </Paper>
            </Grid>
          )}

          {/* Error Display */}
          {submitError && (
            <Grid item xs={12}>
              <Alert severity="error">{submitError}</Alert>
            </Grid>
          )}

          {/* Action Buttons */}
          <Grid item xs={12}>
            <Box display="flex" gap={2} justifyContent="flex-end">
              <Button
                variant="outlined"
                onClick={onCancel}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="contained"
                disabled={
                  isSubmitting || 
                  !formik.values.recipient || 
                  !formik.values.amount ||
                  Boolean(formik.errors.amount) ||
                  Boolean(formik.errors.recipient)
                }
                startIcon={isSubmitting ? <CircularProgress size={20} /> : null}
              >
                {isSubmitting ? 'Sending...' : 'Send Payment'}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Box>
    </Paper>
  );
};

export default PaymentForm;