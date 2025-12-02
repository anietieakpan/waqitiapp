import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  RadioGroup,
  FormControlLabel,
  Radio,
  Paper,
  Button,
  Chip,
  Stack,
  Alert,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  IconButton,
  Skeleton,
} from '@mui/material';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import WalletIcon from '@mui/icons-material/AccountBalanceWallet';
import CryptoIcon from '@mui/icons-material/Currency';
import AddIcon from '@mui/icons-material/Add';
import CheckIcon from '@mui/icons-material/Check';
import StarIcon from '@mui/icons-material/Star';
import CloseIcon from '@mui/icons-material/Close';
import SecurityIcon from '@mui/icons-material/Security';;
import { PaymentMethod } from '../../types/payment';
import { paymentService } from '../../services/paymentService';
import { CreatePaymentMethodForm } from '../payment/CreatePaymentMethodForm';

interface PaymentMethodSelectorProps {
  value: string | null;
  onChange: (methodId: string) => void;
  showAddNew?: boolean;
  onAddNew?: () => void;
  error?: boolean;
  helperText?: string;
  disabled?: boolean;
  methodTypes?: PaymentMethod['type'][];
}

export const PaymentMethodSelector: React.FC<PaymentMethodSelectorProps> = ({
  value,
  onChange,
  showAddNew = true,
  onAddNew,
  error = false,
  helperText,
  disabled = false,
  methodTypes,
}) => {
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [addingMethod, setAddingMethod] = useState(false);

  useEffect(() => {
    loadPaymentMethods();
  }, []);

  const loadPaymentMethods = async () => {
    setLoading(true);
    try {
      const allMethods = await paymentService.getPaymentMethods();
      const filteredMethods = methodTypes
        ? allMethods.filter(m => methodTypes.includes(m.type))
        : allMethods;
      setMethods(filteredMethods);
      
      // Auto-select default method if none selected
      if (!value && filteredMethods.length > 0) {
        const defaultMethod = filteredMethods.find(m => m.isDefault);
        if (defaultMethod) {
          onChange(defaultMethod.id);
        }
      }
    } catch (error) {
      console.error('Error loading payment methods:', error);
    } finally {
      setLoading(false);
    }
  };

  const getMethodIcon = (type: PaymentMethod['type']) => {
    switch (type) {
      case 'BANK_ACCOUNT':
        return <BankIcon />;
      case 'CREDIT_CARD':
      case 'DEBIT_CARD':
        return <CardIcon />;
      case 'DIGITAL_WALLET':
        return <WalletIcon />;
      case 'CRYPTOCURRENCY':
        return <CryptoIcon />;
      default:
        return <WalletIcon />;
    }
  };

  const getMethodTypeLabel = (type: PaymentMethod['type']) => {
    switch (type) {
      case 'BANK_ACCOUNT':
        return 'Bank Account';
      case 'CREDIT_CARD':
        return 'Credit Card';
      case 'DEBIT_CARD':
        return 'Debit Card';
      case 'DIGITAL_WALLET':
        return 'Digital Wallet';
      case 'CRYPTOCURRENCY':
        return 'Crypto Wallet';
      default:
        return type;
    }
  };

  const handleAddMethod = () => {
    if (onAddNew) {
      onAddNew();
    } else {
      setShowAddDialog(true);
    }
  };

  const handleMethodAdded = async (newMethod: PaymentMethod) => {
    setMethods([...methods, newMethod]);
    onChange(newMethod.id);
    setShowAddDialog(false);
    setAddingMethod(false);
  };

  if (loading) {
    return (
      <Box>
        <Skeleton variant="rounded" height={80} sx={{ mb: 1 }} />
        <Skeleton variant="rounded" height={80} sx={{ mb: 1 }} />
        <Skeleton variant="rounded" height={80} />
      </Box>
    );
  }

  return (
    <Box>
      {error && helperText && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {helperText}
        </Alert>
      )}

      {methods.length === 0 ? (
        <Paper sx={{ p: 3, textAlign: 'center' }}>
          <WalletIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" gutterBottom>
            No payment methods added
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Add a payment method to start sending and receiving money
          </Typography>
          {showAddNew && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleAddMethod}
              disabled={disabled}
            >
              Add Payment Method
            </Button>
          )}
        </Paper>
      ) : (
        <>
          <RadioGroup value={value} onChange={(e) => onChange(e.target.value)}>
            <Stack spacing={1}>
              {methods.map((method) => (
                <Paper
                  key={method.id}
                  sx={{
                    p: 2,
                    cursor: disabled ? 'default' : 'pointer',
                    border: 2,
                    borderColor: value === method.id ? 'primary.main' : 'transparent',
                    bgcolor: value === method.id ? 'action.selected' : 'background.paper',
                    transition: 'all 0.2s',
                    '&:hover': {
                      borderColor: disabled ? 'transparent' : 'primary.light',
                    },
                  }}
                  onClick={() => !disabled && onChange(method.id)}
                >
                  <FormControlLabel
                    value={method.id}
                    control={<Radio disabled={disabled} />}
                    label={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: 1 }}>
                        <Box sx={{ color: 'primary.main' }}>
                          {getMethodIcon(method.type)}
                        </Box>
                        <Box sx={{ flex: 1 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="subtitle1">
                              {method.displayName || method.maskedDetails}
                            </Typography>
                            {method.isDefault && (
                              <Chip
                                label="Default"
                                size="small"
                                icon={<StarIcon />}
                                color="primary"
                                variant="outlined"
                              />
                            )}
                            {method.verificationStatus === 'VERIFIED' && (
                              <CheckIcon fontSize="small" color="success" />
                            )}
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            {getMethodTypeLabel(method.type)}
                            {method.provider && ` • ${method.provider}`}
                          </Typography>
                        </Box>
                        {method.verificationStatus === 'PENDING' && (
                          <Chip
                            label="Verify"
                            size="small"
                            icon={<SecurityIcon />}
                            color="warning"
                            variant="outlined"
                          />
                        )}
                      </Box>
                    }
                    sx={{ m: 0, width: '100%' }}
                  />
                </Paper>
              ))}
            </Stack>
          </RadioGroup>

          {showAddNew && (
            <Button
              fullWidth
              variant="outlined"
              startIcon={<AddIcon />}
              onClick={handleAddMethod}
              disabled={disabled}
              sx={{ mt: 2 }}
            >
              Add New Payment Method
            </Button>
          )}
        </>
      )}

      <Dialog
        open={showAddDialog}
        onClose={() => !addingMethod && setShowAddDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="h6">Add Payment Method</Typography>
            <IconButton
              onClick={() => setShowAddDialog(false)}
              disabled={addingMethod}
              size="small"
            >
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <CreatePaymentMethodForm
            onSuccess={handleMethodAdded}
            onCancel={() => setShowAddDialog(false)}
            allowedTypes={methodTypes}
            onLoading={setAddingMethod}
          />
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default PaymentMethodSelector;