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
  IconButton,
} from '@mui/material';
import SwapVertIcon from '@mui/icons-material/SwapVert';
import { useForm, Controller } from 'react-hook-form';
import { useAppDispatch } from '@/hooks/redux';
import { swapCrypto } from '@/store/slices/cryptoSlice';
import { CryptoWallet, CryptoPrice, CryptoCurrency, SwapCryptoRequest } from '@/types/crypto';
import toast from 'react-hot-toast';

interface SwapCryptoDialogProps {
  open: boolean;
  onClose: () => void;
  wallets: CryptoWallet[];
  prices: Record<CryptoCurrency, CryptoPrice>;
}

const SwapCryptoDialog: React.FC<SwapCryptoDialogProps> = ({ open, onClose, wallets, prices }) => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(false);

  const { control, handleSubmit, watch, setValue, reset } = useForm({
    defaultValues: {
      fromCurrency: wallets[0]?.currency || CryptoCurrency.BTC,
      toCurrency: CryptoCurrency.ETH,
      amount: 0,
    },
  });

  const fromCurrency = watch('fromCurrency');
  const toCurrency = watch('toCurrency');
  const amount = watch('amount');

  const fromPrice = prices[fromCurrency]?.priceUSD || 0;
  const toPrice = prices[toCurrency]?.priceUSD || 0;
  const estimatedReceive = (amount * fromPrice) / toPrice;

  const handleSwapCurrencies = () => {
    const temp = fromCurrency;
    setValue('fromCurrency', toCurrency);
    setValue('toCurrency', temp);
  };

  const onSubmit = async (data: any) => {
    try {
      setLoading(true);
      const request: SwapCryptoRequest = {
        fromCurrency: data.fromCurrency,
        toCurrency: data.toCurrency,
        amount: parseFloat(data.amount),
      };
      await dispatch(swapCrypto(request)).unwrap();
      toast.success('Crypto swap successful!');
      reset();
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Failed to swap crypto');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Swap Cryptocurrency</DialogTitle>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Controller
              name="fromCurrency"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  select
                  label="From"
                  fullWidth
                  margin="normal"
                >
                  {wallets.map((wallet) => (
                    <MenuItem key={wallet.id} value={wallet.currency}>
                      {wallet.currency}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />

            <IconButton onClick={handleSwapCurrencies} sx={{ mt: 1 }}>
              <SwapVertIcon />
            </IconButton>

            <Controller
              name="toCurrency"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  select
                  label="To"
                  fullWidth
                  margin="normal"
                >
                  {Object.values(CryptoCurrency).map((currency) => (
                    <MenuItem key={currency} value={currency}>
                      {currency}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
          </Box>

          <Controller
            name="amount"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label={`Amount (${fromCurrency})`}
                type="number"
                fullWidth
                margin="normal"
                inputProps={{ step: '0.00000001', min: '0' }}
              />
            )}
          />

          {estimatedReceive > 0 && (
            <Box sx={{ mt: 2, p: 2, bgcolor: 'background.paper', borderRadius: 1 }}>
              <Typography variant="body2" color="text.secondary">
                You will receive approximately
              </Typography>
              <Typography variant="h6">
                {estimatedReceive.toFixed(8)} {toCurrency}
              </Typography>
            </Box>
          )}
        </DialogContent>

        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={loading || amount <= 0}>
            {loading ? 'Swapping...' : 'Swap Crypto'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default SwapCryptoDialog;
