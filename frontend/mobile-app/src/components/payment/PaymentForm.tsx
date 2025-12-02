import React, { useState, useEffect } from 'react';
import { View, Text, TextInput, TouchableOpacity, Alert, StyleSheet } from 'react-native';
import { Picker } from '@react-native-picker/picker';
import { paymentService } from '../../services/PaymentService';
import { validationService } from '../../services/ValidationService';
import { usePerformanceMonitor } from '../../hooks/usePerformanceMonitor';

interface PaymentFormProps {
  onPaymentComplete?: (result: PaymentResult) => void;
  onPaymentCancel?: () => void;
  recipientId?: string;
  initialAmount?: string;
  initialCurrency?: string;
}

interface PaymentResult {
  success: boolean;
  transactionId?: string;
  errorMessage?: string;
}

export const PaymentForm: React.FC<PaymentFormProps> = ({
  onPaymentComplete,
  onPaymentCancel,
  recipientId,
  initialAmount = '',
  initialCurrency = 'USD'
}) => {
  const performanceMonitor = usePerformanceMonitor('PaymentForm');
  
  const [amount, setAmount] = useState(initialAmount);
  const [currency, setCurrency] = useState(initialCurrency);
  const [description, setDescription] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('WALLET');
  const [isProcessing, setIsProcessing] = useState(false);
  
  const [errors, setErrors] = useState({
    amount: '',
    description: '',
    general: ''
  });

  const currencies = ['USD', 'EUR', 'GBP', 'CAD', 'AUD'];
  const paymentMethods = [
    { value: 'WALLET', label: 'Waqiti Wallet' },
    { value: 'APPLE_PAY', label: 'Apple Pay' },
    { value: 'GOOGLE_PAY', label: 'Google Pay' },
    { value: 'BANK_TRANSFER', label: 'Bank Transfer' }
  ];

  useEffect(() => {
    performanceMonitor.startTimer('form_load');
    return () => performanceMonitor.endTimer('form_load');
  }, []);

  const validateForm = (): boolean => {
    const newErrors = { amount: '', description: '', general: '' };
    let isValid = true;

    // Validate amount
    if (!amount || isNaN(parseFloat(amount))) {
      newErrors.amount = 'Please enter a valid amount';
      isValid = false;
    } else if (parseFloat(amount) <= 0) {
      newErrors.amount = 'Amount must be greater than 0';
      isValid = false;
    } else if (parseFloat(amount) > 10000) {
      newErrors.amount = 'Amount cannot exceed $10,000';
      isValid = false;
    }

    // Validate description
    if (description.length > 200) {
      newErrors.description = 'Description cannot exceed 200 characters';
      isValid = false;
    }

    // Validate amount format
    if (amount && !validationService.isValidAmount(amount)) {
      newErrors.amount = 'Invalid amount format';
      isValid = false;
    }

    setErrors(newErrors);
    return isValid;
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    if (!recipientId) {
      Alert.alert('Error', 'Recipient not selected');
      return;
    }

    setIsProcessing(true);
    performanceMonitor.startTimer('payment_processing');

    try {
      const paymentRequest = {
        recipientId,
        amount: parseFloat(amount),
        currency,
        description: description.trim(),
        paymentMethod,
        metadata: {
          source: 'mobile_app',
          timestamp: new Date().toISOString()
        }
      };

      const result = await paymentService.initiatePayment(paymentRequest);

      if (result.success) {
        performanceMonitor.recordEvent('payment_success');
        Alert.alert(
          'Payment Successful', 
          `Transaction ID: ${result.transactionId}`,
          [
            {
              text: 'OK',
              onPress: () => onPaymentComplete?.(result)
            }
          ]
        );
      } else {
        throw new Error(result.errorMessage || 'Payment failed');
      }

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Payment failed';
      performanceMonitor.recordError('payment_failure', errorMessage);
      
      setErrors(prev => ({ ...prev, general: errorMessage }));
      
      Alert.alert('Payment Failed', errorMessage);
      onPaymentComplete?.({ success: false, errorMessage });
    } finally {
      setIsProcessing(false);
      performanceMonitor.endTimer('payment_processing');
    }
  };

  const handleCancel = () => {
    performanceMonitor.recordEvent('payment_cancelled');
    onPaymentCancel?.();
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Send Payment</Text>
      
      {errors.general ? (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>{errors.general}</Text>
        </View>
      ) : null}

      <View style={styles.formGroup}>
        <Text style={styles.label}>Amount</Text>
        <TextInput
          style={[styles.input, errors.amount ? styles.inputError : null]}
          value={amount}
          onChangeText={setAmount}
          placeholder="0.00"
          keyboardType="decimal-pad"
          editable={!isProcessing}
          testID="payment-amount-input"
        />
        {errors.amount ? (
          <Text style={styles.fieldError}>{errors.amount}</Text>
        ) : null}
      </View>

      <View style={styles.formGroup}>
        <Text style={styles.label}>Currency</Text>
        <Picker
          selectedValue={currency}
          onValueChange={setCurrency}
          style={styles.picker}
          enabled={!isProcessing}
          testID="payment-currency-picker"
        >
          {currencies.map(curr => (
            <Picker.Item key={curr} label={curr} value={curr} />
          ))}
        </Picker>
      </View>

      <View style={styles.formGroup}>
        <Text style={styles.label}>Payment Method</Text>
        <Picker
          selectedValue={paymentMethod}
          onValueChange={setPaymentMethod}
          style={styles.picker}
          enabled={!isProcessing}
          testID="payment-method-picker"
        >
          {paymentMethods.map(method => (
            <Picker.Item key={method.value} label={method.label} value={method.value} />
          ))}
        </Picker>
      </View>

      <View style={styles.formGroup}>
        <Text style={styles.label}>Description (Optional)</Text>
        <TextInput
          style={[styles.input, styles.textArea, errors.description ? styles.inputError : null]}
          value={description}
          onChangeText={setDescription}
          placeholder="What's this payment for?"
          multiline
          numberOfLines={3}
          maxLength={200}
          editable={!isProcessing}
          testID="payment-description-input"
        />
        <Text style={styles.characterCount}>{description.length}/200</Text>
        {errors.description ? (
          <Text style={styles.fieldError}>{errors.description}</Text>
        ) : null}
      </View>

      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={[styles.button, styles.cancelButton]}
          onPress={handleCancel}
          disabled={isProcessing}
          testID="payment-cancel-button"
        >
          <Text style={styles.cancelButtonText}>Cancel</Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.button, styles.submitButton, isProcessing ? styles.buttonDisabled : null]}
          onPress={handleSubmit}
          disabled={isProcessing}
          testID="payment-submit-button"
        >
          <Text style={styles.submitButtonText}>
            {isProcessing ? 'Processing...' : `Send ${currency} ${amount || '0.00'}`}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 20,
    backgroundColor: '#fff',
    borderRadius: 12,
    margin: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
    color: '#1a202c',
  },
  formGroup: {
    marginBottom: 20,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    color: '#2d3748',
  },
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    backgroundColor: '#f7fafc',
  },
  inputError: {
    borderColor: '#e53e3e',
    backgroundColor: '#fed7d7',
  },
  textArea: {
    height: 80,
    textAlignVertical: 'top',
  },
  picker: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    backgroundColor: '#f7fafc',
  },
  characterCount: {
    fontSize: 12,
    color: '#718096',
    textAlign: 'right',
    marginTop: 4,
  },
  errorContainer: {
    backgroundColor: '#fed7d7',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
    borderLeft: 4,
    borderLeftColor: '#e53e3e',
  },
  errorText: {
    color: '#c53030',
    fontSize: 14,
    fontWeight: '500',
  },
  fieldError: {
    color: '#e53e3e',
    fontSize: 12,
    marginTop: 4,
  },
  buttonContainer: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
  },
  button: {
    flex: 1,
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  cancelButton: {
    backgroundColor: '#f7fafc',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  cancelButtonText: {
    color: '#4a5568',
    fontSize: 16,
    fontWeight: '600',
  },
  submitButton: {
    backgroundColor: '#3182ce',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
});