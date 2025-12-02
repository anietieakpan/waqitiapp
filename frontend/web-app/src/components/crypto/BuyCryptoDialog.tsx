import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  Box,
  Typography,
  Alert,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { useAppDispatch } from '@/hooks/redux';
import { buyCrypto } from '@/store/slices/cryptoSlice';
import { CryptoCurrency, CryptoPrice, BuyCryptoRequest } from '@/types/crypto';
import toast from 'react-hot-toast';

interface BuyCryptoDialogProps {
  open: boolean;
  onClose: () => void;
  prices: Record<CryptoCurrency, CryptoPrice>;
}

const BuyCryptoDialog: React.FC<BuyCryptoDialogProps> = ({ open, onClose, prices }) => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(false);

  const { control, handleSubmit, watch, reset } = useForm({
    defaultValues: {
      currency: CryptoCurrency.BTC,
      amount: 0,
      paymentMethodId: '',
    },
  });

  const selectedCurrency = watch('currency');
  const amount = watch('amount');
  const price = prices[selectedCurrency]?.priceUSD || 0;
  const totalUSD = amount * price;

  const onSubmit = async (data: any) => {
    try {
      setLoading(true);
      const request: BuyCryptoRequest = {
        currency: data.currency,
        amount: parseFloat(data.amount),
        paymentMethodId: data.paymentMethodId || 'default',
      };
      await dispatch(buyCrypto(request)).unwrap();
      toast.success('Crypto purchase successful!');
      reset();
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Failed to buy crypto');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Buy Cryptocurrency</DialogTitle>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogContent>
          <Controller
            name="currency"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                select
                label="Cryptocurrency"
                fullWidth
                margin="normal"
              >
                {Object.values(CryptoCurrency).map((currency) => (
                  <MenuItem key={currency} value={currency}>
                    {currency} - ${prices[currency]?.priceUSD.toFixed(2) || '0.00'}
                  </MenuItem>
                ))}
              </TextField>
            )}
          />

          <Controller
            name="amount"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={`Amount (${selectedCurrency})`}
                type="number"
                fullWidth
                margin="normal"
                inputProps={{ step: '0.00000001', min: '0' }}
              />
            )}
          />

          {totalUSD > 0 && (
            <Box sx={{ mt: 2, p: 2, bgcolor: 'background.paper', borderRadius: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Estimated Total
              </Typography>
              <Typography variant="h6">
                ${totalUSD.toFixed(2)} USD
              </Typography>
            </Box>
          )}

          <Alert severity="info" sx={{ mt: 2 }}>
            Funds will be debited from your primary payment method
          </Alert>
        </DialogContent>

        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={loading || amount <= 0}>
            {loading ? 'Processing...' : 'Buy Crypto'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default BuyCryptoDialog;
