import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

/**
 * AmountInput Component
 *
 * Reusable currency input component with formatting, validation, and quick amount buttons
 *
 * Features:
 * - Currency formatting (USD)
 * - Real-time validation
 * - Min/max amount support
 * - Quick amount buttons
 * - Error handling
 * - Accessibility support
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface AmountInputProps {
  value: string;
  onChangeAmount: (amount: string) => void;
  currency?: string;
  minAmount?: number;
  maxAmount?: number;
  showQuickAmounts?: boolean;
  quickAmounts?: number[];
  error?: string;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
}

const AmountInput: React.FC<AmountInputProps> = ({
  value,
  onChangeAmount,
  currency = 'USD',
  minAmount = 0.01,
  maxAmount = 10000,
  showQuickAmounts = true,
  quickAmounts = [10, 25, 50, 100],
  error,
  label = 'Amount',
  placeholder = '0.00',
  disabled = false,
}) => {
  const [focused, setFocused] = useState(false);
  const [formattedValue, setFormattedValue] = useState('');

  useEffect(() => {
    if (value) {
      const numValue = parseFloat(value);
      if (!isNaN(numValue)) {
        setFormattedValue(formatCurrency(numValue));
      }
    } else {
      setFormattedValue('');
    }
  }, [value]);

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  };

  const parseCurrencyInput = (text: string): string => {
    // Remove all non-digit and non-decimal characters
    const cleaned = text.replace(/[^\d.]/g, '');

    // Ensure only one decimal point
    const parts = cleaned.split('.');
    if (parts.length > 2) {
      return parts[0] + '.' + parts.slice(1).join('');
    }

    // Limit to 2 decimal places
    if (parts.length === 2 && parts[1].length > 2) {
      return parts[0] + '.' + parts[1].substring(0, 2);
    }

    return cleaned;
  };

  const validateAmount = (amount: string): string | undefined => {
    if (!amount) return undefined;

    const numValue = parseFloat(amount);

    if (isNaN(numValue)) {
      return 'Please enter a valid amount';
    }

    if (numValue < minAmount) {
      return `Minimum amount is ${formatCurrency(minAmount)}`;
    }

    if (numValue > maxAmount) {
      return `Maximum amount is ${formatCurrency(maxAmount)}`;
    }

    return undefined;
  };

  const handleTextChange = (text: string) => {
    const parsed = parseCurrencyInput(text);
    onChangeAmount(parsed);
  };

  const handleQuickAmount = (amount: number) => {
    onChangeAmount(amount.toString());
  };

  const handleClear = () => {
    onChangeAmount('');
  };

  const getCurrencySymbol = (): string => {
    switch (currency) {
      case 'USD':
        return '$';
      case 'EUR':
        return '€';
      case 'GBP':
        return '£';
      default:
        return currency;
    }
  };

  const validationError = error || validateAmount(value);

  return (
    <View style={styles.container}>
      {label && <Text style={styles.label}>{label}</Text>}

      <View
        style={[
          styles.inputContainer,
          focused && styles.inputContainerFocused,
          validationError && styles.inputContainerError,
          disabled && styles.inputContainerDisabled,
        ]}
      >
        <Text style={styles.currencySymbol}>{getCurrencySymbol()}</Text>

        <TextInput
          style={styles.input}
          value={value}
          onChangeText={handleTextChange}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          placeholder={placeholder}
          placeholderTextColor="#999"
          keyboardType="decimal-pad"
          editable={!disabled}
          accessibilityLabel={`${label} input`}
          accessibilityHint={`Enter amount between ${formatCurrency(minAmount)} and ${formatCurrency(maxAmount)}`}
        />

        {value && !disabled ? (
          <TouchableOpacity onPress={handleClear} style={styles.clearButton}>
            <Icon name="close-circle" size={20} color="#999" />
          </TouchableOpacity>
        ) : null}
      </View>

      {formattedValue && !validationError && (
        <Text style={styles.formattedAmount}>{formattedValue}</Text>
      )}

      {validationError && (
        <View style={styles.errorContainer}>
          <Icon name="alert-circle" size={16} color="#F44336" />
          <Text style={styles.errorText}>{validationError}</Text>
        </View>
      )}

      {showQuickAmounts && !disabled && (
        <View style={styles.quickAmountsContainer}>
          <Text style={styles.quickAmountsLabel}>Quick amounts:</Text>
          <View style={styles.quickAmountsButtons}>
            {quickAmounts.map((amount) => (
              <TouchableOpacity
                key={amount}
                style={[
                  styles.quickAmountButton,
                  parseFloat(value) === amount && styles.quickAmountButtonActive,
                ]}
                onPress={() => handleQuickAmount(amount)}
              >
                <Text
                  style={[
                    styles.quickAmountText,
                    parseFloat(value) === amount && styles.quickAmountTextActive,
                  ]}
                >
                  {getCurrencySymbol()}{amount}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      )}

      <View style={styles.limitsContainer}>
        <Text style={styles.limitsText}>
          Min: {formatCurrency(minAmount)} • Max: {formatCurrency(maxAmount)}
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginVertical: 8,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 8,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderWidth: 2,
    borderColor: '#E0E0E0',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  inputContainerFocused: {
    borderColor: '#6200EE',
  },
  inputContainerError: {
    borderColor: '#F44336',
  },
  inputContainerDisabled: {
    backgroundColor: '#F5F5F5',
    borderColor: '#E0E0E0',
  },
  currencySymbol: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#6200EE',
    marginRight: 8,
  },
  input: {
    flex: 1,
    fontSize: 24,
    fontWeight: 'bold',
    color: '#212121',
    padding: 0,
  },
  clearButton: {
    padding: 4,
  },
  formattedAmount: {
    fontSize: 14,
    color: '#666',
    marginTop: 8,
    textAlign: 'center',
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
  },
  errorText: {
    fontSize: 14,
    color: '#F44336',
    marginLeft: 4,
  },
  quickAmountsContainer: {
    marginTop: 16,
  },
  quickAmountsLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8,
  },
  quickAmountsButtons: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -4,
  },
  quickAmountButton: {
    backgroundColor: '#F5F5F5',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 8,
    marginHorizontal: 4,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#E0E0E0',
  },
  quickAmountButtonActive: {
    backgroundColor: '#6200EE',
    borderColor: '#6200EE',
  },
  quickAmountText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  quickAmountTextActive: {
    color: '#FFFFFF',
  },
  limitsContainer: {
    marginTop: 8,
    alignItems: 'center',
  },
  limitsText: {
    fontSize: 12,
    color: '#999',
  },
});

export default AmountInput;
