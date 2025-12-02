import React, { useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  Surface,
  RadioButton,
  IconButton,
  Divider,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useForm, Controller } from 'react-hook-form';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Header from '../../components/common/Header';
import { formatCurrency } from '../../utils/formatters';

interface AddMoneyFormData {
  amount: string;
}

interface PaymentMethod {
  id: string;
  type: 'card' | 'bank';
  name: string;
  last4: string;
  icon: string;
  fee: number;
}

/**
 * Add Money Screen - Add funds to wallet
 */
const AddMoneyScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<string>('');
  const [quickAmounts] = useState([25, 50, 100, 200]);

  const paymentMethods: PaymentMethod[] = [
    {
      id: '1',
      type: 'card',
      name: 'Visa ending in 4242',
      last4: '4242',
      icon: 'credit-card',
      fee: 0,
    },
    {
      id: '2',
      type: 'bank',
      name: 'Chase Bank',
      last4: '8901',
      icon: 'bank',
      fee: 0,
    },
    {
      id: '3',
      type: 'card',
      name: 'Mastercard ending in 5678',
      last4: '5678',
      icon: 'credit-card',
      fee: 2.99,
    },
  ];

  const {
    control,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isValid },
  } = useForm<AddMoneyFormData>({
    mode: 'onChange',
    defaultValues: {
      amount: '',
    },
  });

  const watchedAmount = watch('amount');
  const selectedMethod = paymentMethods.find(method => method.id === selectedPaymentMethod);
  const fee = selectedMethod?.fee || 0;
  const total = (parseFloat(watchedAmount) || 0) + fee;

  const handleQuickAmount = (amount: number) => {
    setValue('amount', amount.toString());
  };

  const handleAddMoney = (data: AddMoneyFormData) => {
    if (!selectedPaymentMethod) {
      return;
    }

    const addMoneyData = {
      amount: parseFloat(data.amount),
      paymentMethod: selectedMethod,
      fee,
      total,
    };

    // Navigate to confirmation
    console.log('Add money:', addMoneyData);
    navigation.goBack();
  };

  const validateAmount = (amount: string) => {
    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      return 'Please enter a valid amount';
    }
    if (numAmount < 10) {
      return 'Minimum amount is $10';
    }
    if (numAmount > 5000) {
      return 'Maximum amount is $5,000';
    }
    return true;
  };

  const renderPaymentMethod = (method: PaymentMethod) => (
    <TouchableOpacity
      key={method.id}
      style={[
        styles.paymentMethodCard,
        selectedPaymentMethod === method.id && styles.selectedPaymentMethod,
      ]}
      onPress={() => setSelectedPaymentMethod(method.id)}
    >
      <RadioButton
        value={method.id}
        status={selectedPaymentMethod === method.id ? 'checked' : 'unchecked'}
        onPress={() => setSelectedPaymentMethod(method.id)}
      />
      
      <Icon
        name={method.icon}
        size={24}
        color={theme.colors.primary}
        style={styles.paymentMethodIcon}
      />
      
      <View style={styles.paymentMethodInfo}>
        <Text style={styles.paymentMethodName}>{method.name}</Text>
        <Text style={styles.paymentMethodFee}>
          {method.fee > 0 ? `Fee: ${formatCurrency(method.fee, 'USD')}` : 'No fee'}
        </Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <SafeAreaView style={styles.container}>
        <Header
          title="Add Money"
          leftAction={
            <IconButton
              icon="arrow-left"
              size={24}
              onPress={() => navigation.goBack()}
            />
          }
        />

        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Amount Input */}
          <Surface style={styles.amountCard} elevation={2}>
            <Text style={styles.cardTitle}>How much would you like to add?</Text>
            
            <Controller
              control={control}
              name="amount"
              rules={{
                required: 'Amount is required',
                validate: validateAmount,
              }}
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  label="Enter amount"
                  value={value}
                  onBlur={onBlur}
                  onChangeText={onChange}
                  mode="outlined"
                  keyboardType="decimal-pad"
                  style={styles.amountInput}
                  contentStyle={styles.amountInputContent}
                  left={<TextInput.Icon icon="currency-usd" />}
                  error={!!errors.amount}
                />
              )}
            />
            
            {errors.amount && (
              <Text style={styles.errorText}>{errors.amount.message}</Text>
            )}

            {/* Quick Amount Buttons */}
            <View style={styles.quickAmountsContainer}>
              <Text style={styles.quickAmountsTitle}>Quick amounts:</Text>
              <View style={styles.quickAmounts}>
                {quickAmounts.map((amount) => (
                  <TouchableOpacity
                    key={amount}
                    style={styles.quickAmountButton}
                    onPress={() => handleQuickAmount(amount)}
                  >
                    <Text style={styles.quickAmountText}>
                      ${amount}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </Surface>

          {/* Payment Methods */}
          <Surface style={styles.paymentMethodsCard} elevation={2}>
            <Text style={styles.cardTitle}>Choose payment method</Text>
            
            <View style={styles.paymentMethodsList}>
              {paymentMethods.map(renderPaymentMethod)}
            </View>

            <TouchableOpacity
              style={styles.addPaymentMethodButton}
              onPress={() => navigation.navigate('LinkedAccounts' as never)}
            >
              <Icon name="plus" size={20} color={theme.colors.primary} />
              <Text style={styles.addPaymentMethodText}>
                Add payment method
              </Text>
            </TouchableOpacity>
          </Surface>

          {/* Summary */}
          {watchedAmount && selectedMethod && (
            <Surface style={styles.summaryCard} elevation={2}>
              <Text style={styles.cardTitle}>Summary</Text>
              
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>Amount to add:</Text>
                <Text style={styles.summaryValue}>
                  {formatCurrency(parseFloat(watchedAmount), 'USD')}
                </Text>
              </View>
              
              {fee > 0 && (
                <View style={styles.summaryRow}>
                  <Text style={styles.summaryLabel}>Processing fee:</Text>
                  <Text style={styles.summaryValue}>
                    {formatCurrency(fee, 'USD')}
                  </Text>
                </View>
              )}
              
              <Divider style={styles.summaryDivider} />
              
              <View style={styles.summaryRow}>
                <Text style={styles.totalLabel}>Total charge:</Text>
                <Text style={styles.totalValue}>
                  {formatCurrency(total, 'USD')}
                </Text>
              </View>
              
              <View style={styles.receiveContainer}>
                <Icon name="wallet" size={20} color="#4CAF50" />
                <Text style={styles.receiveText}>
                  You'll receive {formatCurrency(parseFloat(watchedAmount), 'USD')} in your wallet
                </Text>
              </View>
            </Surface>
          )}

          {/* Security Notice */}
          <Surface style={styles.securityNotice} elevation={1}>
            <Icon name="shield-check" size={24} color="#4CAF50" />
            <View style={styles.securityNoticeText}>
              <Text style={styles.securityTitle}>Secure Transaction</Text>
              <Text style={styles.securitySubtitle}>
                Your payment information is encrypted and secure
              </Text>
            </View>
          </Surface>

          {/* Terms */}
          <View style={styles.termsContainer}>
            <Text style={styles.termsText}>
              By adding money, you agree to our{' '}
              <Text style={styles.termsLink}>Terms of Service</Text>
              {' '}and{' '}
              <Text style={styles.termsLink}>Privacy Policy</Text>
            </Text>
          </View>
        </ScrollView>

        {/* Add Money Button */}
        <View style={styles.bottomContainer}>
          <Button
            mode="contained"
            onPress={handleSubmit(handleAddMoney)}
            disabled={!isValid || !selectedPaymentMethod}
            style={styles.addMoneyButton}
            contentStyle={styles.buttonContent}
          >
            {total > 0 ? `Add ${formatCurrency(total, 'USD')}` : 'Add Money'}
          </Button>
        </View>
      </SafeAreaView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 100,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 16,
  },
  amountCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  amountInput: {
    backgroundColor: 'white',
    marginBottom: 8,
  },
  amountInputContent: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  errorText: {
    color: '#d32f2f',
    fontSize: 12,
    marginTop: 4,
  },
  quickAmountsContainer: {
    marginTop: 16,
  },
  quickAmountsTitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 12,
  },
  quickAmounts: {
    flexDirection: 'row',
    gap: 12,
  },
  quickAmountButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: '#f0f0f0',
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  quickAmountText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  paymentMethodsCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  paymentMethodsList: {
    gap: 12,
  },
  paymentMethodCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: 'transparent',
    backgroundColor: '#f8f8f8',
  },
  selectedPaymentMethod: {
    borderColor: '#2196F3',
    backgroundColor: '#f0f8ff',
  },
  paymentMethodIcon: {
    marginLeft: 8,
  },
  paymentMethodInfo: {
    flex: 1,
    marginLeft: 12,
  },
  paymentMethodName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  paymentMethodFee: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  addPaymentMethodButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    marginTop: 8,
  },
  addPaymentMethodText: {
    marginLeft: 8,
    fontSize: 14,
    color: '#2196F3',
    fontWeight: '500',
  },
  summaryCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  summaryLabel: {
    fontSize: 14,
    color: '#666',
  },
  summaryValue: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
  summaryDivider: {
    marginVertical: 12,
  },
  totalLabel: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
  },
  totalValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
  },
  receiveContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
    padding: 12,
    backgroundColor: '#f8fff8',
    borderRadius: 8,
  },
  receiveText: {
    marginLeft: 8,
    fontSize: 14,
    color: '#4CAF50',
    fontWeight: '500',
  },
  securityNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#f8fff8',
    borderColor: '#4CAF50',
    borderWidth: 1,
    marginBottom: 16,
  },
  securityNoticeText: {
    marginLeft: 12,
    flex: 1,
  },
  securityTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4CAF50',
  },
  securitySubtitle: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  termsContainer: {
    paddingHorizontal: 8,
  },
  termsText: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
    lineHeight: 18,
  },
  termsLink: {
    color: '#2196F3',
    textDecorationLine: 'underline',
  },
  bottomContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
    backgroundColor: 'white',
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
  },
  addMoneyButton: {
    marginBottom: 0,
  },
  buttonContent: {
    height: 52,
  },
});

export default AddMoneyScreen;