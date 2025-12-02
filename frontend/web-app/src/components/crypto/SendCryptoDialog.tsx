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
import { sendCrypto } from '@/store/slices/cryptoSlice';
import { CryptoWallet, SendCryptoRequest } from '@/types/crypto';
import toast from 'react-hot-toast';

interface SendCryptoDialogProps {
  open: boolean;
  onClose: () => void;
  wallets: CryptoWallet[];
}

const SendCryptoDialog: React.FC<SendCryptoDialogProps> = ({ open, onClose, wallets }) => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(false);

  const { control, handleSubmit, watch, reset } = useForm({
    defaultValues: {
      currency: wallets[0]?.currency || '',
      amount: 0,
      toAddress: '',
    },
  });

  const selectedCurrency = watch('currency');
  const selectedWallet = wallets.find((w) => w.currency === selectedCurrency);

  const onSubmit = async (data: any) => {
    try {
      setLoading(true);
      const request: SendCryptoRequest = {
        currency: data.currency,
        amount: parseFloat(data.amount),
        toAddress: data.toAddress,
      };
      await dispatch(sendCrypto(request)).unwrap();
      toast.success('Crypto sent successfully!');
      reset();
      onClose();
    } catch (err: any) {
      toast.error(err.message || 'Failed to send crypto');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Send Cryptocurrency</DialogTitle>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogContent>
          <Controller
            name="currency"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                select
                label="From Wallet"
                fullWidth
                margin="normal"
              >
                {wallets.map((wallet) => (
                  <MenuItem key={wallet.id} value={wallet.currency}>
                    {wallet.currency} - {wallet.balance.toFixed(8)}
                  </MenuItem>
                ))}
              </TextField>
            )}
          />

          <Controller
            name="toAddress"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Recipient Address"
                fullWidth
                margin="normal"
                placeholder="0x..."
              />
            )}
          />

          <Controller
            name="amount"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Amount"
                type="number"
                fullWidth
                margin="normal"
                inputProps={{ step: '0.00000001', min: '0', max: selectedWallet?.balance }}
                helperText={`Available: ${selectedWallet?.balance.toFixed(8) || 0} ${selectedCurrency}`}
              />
            )}
          />

          <Alert severity="warning" sx={{ mt: 2 }}>
            Double-check the recipient address. Crypto transactions cannot be reversed.
          </Alert>
        </DialogContent>

        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? 'Sending...' : 'Send Crypto'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default SendCryptoDialog;
