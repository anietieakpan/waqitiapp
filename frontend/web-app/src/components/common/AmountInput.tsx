import React, { useState, useEffect } from 'react';
import {
  TextField,
  InputAdornment,
  Box,
  Typography,
  ToggleButton,
  ToggleButtonGroup,
  FormHelperText,
} from '@mui/material';
import { NumericFormat } from 'react-number-format';

interface AmountInputProps {
  value: number;
  onChange: (value: number) => void;
  currency?: string;
  label?: string;
  error?: boolean;
  helperText?: string;
  min?: number;
  max?: number;
  quickAmounts?: number[];
  showBalance?: boolean;
  balance?: number;
  disabled?: boolean;
  autoFocus?: boolean;
}

export const AmountInput: React.FC<AmountInputProps> = ({
  value,
  onChange,
  currency = 'USD',
  label = 'Amount',
  error = false,
  helperText,
  min = 0.01,
  max,
  quickAmounts = [10, 25, 50, 100],
  showBalance = false,
  balance = 0,
  disabled = false,
  autoFocus = false,
}) => {
  const [inputValue, setInputValue] = useState<string>('');
  const [isFocused, setIsFocused] = useState(false);

  useEffect(() => {
    if (!isFocused && value > 0) {
      setInputValue(value.toFixed(2));
    } else if (value === 0) {
      setInputValue('');
    }
  }, [value, isFocused]);

  const handleValueChange = (values: any) => {
    const numericValue = values.floatValue || 0;
    setInputValue(values.value);
    
    if (numericValue >= min && (!max || numericValue <= max)) {
      onChange(numericValue);
    }
  };

  const handleQuickAmount = (_: React.MouseEvent<HTMLElement>, selectedAmount: number | null) => {
    if (selectedAmount !== null) {
      onChange(selectedAmount);
      setInputValue(selectedAmount.toFixed(2));
    }
  };

  const getCurrencySymbol = (currency: string) => {
    const symbols: { [key: string]: string } = {
      USD: '$',
      EUR: '€',
      GBP: '£',
      JPY: '¥',
    };
    return symbols[currency] || currency;
  };

  const validateAmount = (amount: number) => {
    if (amount < min) return `Minimum amount is ${getCurrencySymbol(currency)}${min.toFixed(2)}`;
    if (max && amount > max) return `Maximum amount is ${getCurrencySymbol(currency)}${max.toFixed(2)}`;
    if (showBalance && amount > balance) return 'Insufficient balance';
    return '';
  };

  const validationError = value > 0 ? validateAmount(value) : '';
  const displayError = error || !!validationError;
  const displayHelperText = validationError || helperText;

  return (
    <Box>
      <NumericFormat
        value={inputValue}
        onValueChange={handleValueChange}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
        thousandSeparator=","
        decimalSeparator="."
        decimalScale={2}
        fixedDecimalScale={false}
        allowNegative={false}
        customInput={TextField}
        fullWidth
        label={label}
        error={displayError}
        disabled={disabled}
        autoFocus={autoFocus}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Typography variant="h6" color="text.primary">
                {getCurrencySymbol(currency)}
              </Typography>
            </InputAdornment>
          ),
          sx: {
            '& input': {
              fontSize: '1.5rem',
              textAlign: 'right',
            },
          },
        }}
        variant="outlined"
      />
      
      {displayHelperText && (
        <FormHelperText error={displayError}>{displayHelperText}</FormHelperText>
      )}

      {showBalance && (
        <Box sx={{ mt: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            Available balance
          </Typography>
          <Typography variant="body2" color="text.primary">
            {getCurrencySymbol(currency)}{balance.toFixed(2)}
          </Typography>
        </Box>
      )}

      {quickAmounts.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary" gutterBottom>
            Quick amounts
          </Typography>
          <ToggleButtonGroup
            value={quickAmounts.includes(value) ? value : null}
            exclusive
            onChange={handleQuickAmount}
            aria-label="quick amounts"
            size="small"
            fullWidth
            sx={{ mt: 1 }}
          >
            {quickAmounts.map((amount) => (
              <ToggleButton
                key={amount}
                value={amount}
                aria-label={`${amount} ${currency}`}
                disabled={disabled || (max && amount > max) || (showBalance && amount > balance)}
              >
                {getCurrencySymbol(currency)}{amount}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>
      )}
    </Box>
  );
};

export default AmountInput;