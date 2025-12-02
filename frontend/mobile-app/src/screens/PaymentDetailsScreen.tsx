import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useSelector } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import Header from '../components/Header';
import AmountInput from '../components/AmountInput';
import PaymentMethodSelector from '../components/PaymentMethodSelector';
import usePayment from '../hooks/usePayment';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * PaymentDetailsScreen
 *
 * Screen for entering payment details (amount, description, payment method)
 *
 * Features:
 * - Amount input with validation
 * - Payment description
 * - Payment method selection
 * - Balance check
 * - Payment processing
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const PaymentDetailsScreen: React.FC = () => {
  const navigation = useNavigation();
  const route = useRoute();
  const { contact, mode = 'send' } = (route.params || {}) as any;

  const { balance } = useSelector((state: RootState) => state.wallet);
  const { paymentMethods } = useSelector((state: RootState) => state.paymentMethods || { paymentMethods: [] });

  const {
    initiatePayment,
    validatePayment,
    isProcessing,
    error: paymentError,
    clearError,
  } = usePayment();

  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<any>(null);
  const [showPaymentMethods, setShowPaymentMethods] = useState(false);

  useEffect(() => {
    AnalyticsService.trackScreenView('PaymentDetailsScreen', {
      mode,
      hasContact: !!contact,
    });

    // Select default payment method
    const defaultMethod = paymentMethods.find((pm: any) => pm.isDefault);
    if (defaultMethod) {
      setSelectedPaymentMethod(defaultMethod);
    }
  }, []);

  const formatBalance = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const handleAmountChange = (value: string) => {
    setAmount(value);
    clearError();
  };

  const handleSendPayment = async () => {
    if (!contact) {
      Alert.alert('Error', 'No recipient selected');
      return;
    }

    const numAmount = parseFloat(amount);
    if (!numAmount || numAmount <= 0) {
      Alert.alert('Error', 'Please enter a valid amount');
      return;
    }

    // Validate payment
    const validation = validatePayment({
      recipientId: contact.waqitiUserId || contact.id,
      amount: numAmount,
      description,
      paymentMethodId: selectedPaymentMethod?.id,
    });

    if (!validation.isValid) {
      Alert.alert('Validation Error', validation.errors.join('\n'));
      return;
    }

    // Show confirmation
    Alert.alert(
      'Confirm Payment',
      `Send ${formatBalance(numAmount)} to ${contact.name}?${
        description ? `\n\nDescription: ${description}` : ''
      }`,
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Confirm',
          onPress: async () => {
            const result = await initiatePayment({
              recipientId: contact.waqitiUserId || contact.id,
              amount: numAmount,
              description,
              paymentMethodId: selectedPaymentMethod?.id,
            });

            if (result.success) {
              navigation.navigate('PaymentSuccess' as never, {
                transactionId: result.transactionId,
                amount: numAmount,
                recipient: contact.name,
              } as never);
            }
          },
        },
      ]
    );
  };

  const renderRecipient = () => (
    <View style={styles.recipientContainer}>
      <Text style={styles.sectionLabel}>Sending to</Text>
      <View style={styles.recipientCard}>
        <View style={styles.recipientInfo}>
          <Icon name="account-circle" size={48} color="#6200EE" />
          <View style={styles.recipientDetails}>
            <Text style={styles.recipientName}>{contact?.name}</Text>
            <Text style={styles.recipientContact}>
              {contact?.email || contact?.phoneNumber}
            </Text>
          </View>
        </View>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.changeButton}
        >
          <Text style={styles.changeButtonText}>Change</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderBalance = () => (
    <View style={styles.balanceContainer}>
      <Icon name="wallet" size={20} color="#666" />
      <Text style={styles.balanceLabel}>Available Balance:</Text>
      <Text style={styles.balanceAmount}>{formatBalance(balance || 0)}</Text>
    </View>
  );

  const renderPaymentMethod = () => (
    <View style={styles.paymentMethodContainer}>
      <Text style={styles.sectionLabel}>Payment Method</Text>
      {selectedPaymentMethod ? (
        <TouchableOpacity
          style={styles.selectedMethodCard}
          onPress={() => setShowPaymentMethods(!showPaymentMethods)}
        >
          <View style={styles.methodInfo}>
            <Icon name="credit-card" size={24} color="#6200EE" />
            <Text style={styles.methodName}>{selectedPaymentMethod.name}</Text>
          </View>
          <Icon name="chevron-down" size={24} color="#666" />
        </TouchableOpacity>
      ) : (
        <TouchableOpacity
          style={styles.addMethodButton}
          onPress={() => setShowPaymentMethods(!showPaymentMethods)}
        >
          <Icon name="plus-circle" size={24} color="#6200EE" />
          <Text style={styles.addMethodText}>Select Payment Method</Text>
        </TouchableOpacity>
      )}

      {showPaymentMethods && (
        <View style={styles.methodsListContainer}>
          <PaymentMethodSelector
            paymentMethods={paymentMethods}
            selectedPaymentMethodId={selectedPaymentMethod?.id}
            onSelectPaymentMethod={(method) => {
              setSelectedPaymentMethod(method);
              setShowPaymentMethods(false);
            }}
            showAddButton={false}
          />
        </View>
      )}
    </View>
  );

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <Header title="Payment Details" showBack />

      <ScrollView style={styles.content} keyboardShouldPersistTaps="handled">
        {renderRecipient()}
        {renderBalance()}

        <View style={styles.amountSection}>
          <AmountInput
            value={amount}
            onChangeAmount={handleAmountChange}
            label="Amount"
            placeholder="0.00"
            minAmount={0.01}
            maxAmount={balance || 10000}
            showQuickAmounts
            quickAmounts={[10, 25, 50, 100, 250, 500]}
          />
        </View>

        <View style={styles.descriptionSection}>
          <Text style={styles.sectionLabel}>Description (Optional)</Text>
          <View style={styles.descriptionInput}>
            <Icon name="message-text" size={20} color="#666" />
            <Text
              style={styles.descriptionPlaceholder}
              onPress={() => {
                // TODO: Open description input modal
              }}
            >
              {description || 'Add a note...'}
            </Text>
          </View>
        </View>

        {renderPaymentMethod()}

        {paymentError && (
          <View style={styles.errorContainer}>
            <Icon name="alert-circle" size={20} color="#F44336" />
            <Text style={styles.errorText}>{paymentError}</Text>
          </View>
        )}
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={[
            styles.sendButton,
            (isProcessing || !amount) && styles.sendButtonDisabled,
          ]}
          onPress={handleSendPayment}
          disabled={isProcessing || !amount}
        >
          {isProcessing ? (
            <Text style={styles.sendButtonText}>Processing...</Text>
          ) : (
            <>
              <Icon name="send" size={20} color="#FFFFFF" />
              <Text style={styles.sendButtonText}>
                Send {amount ? formatBalance(parseFloat(amount)) : 'Payment'}
              </Text>
            </>
          )}
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  content: {
    flex: 1,
  },
  recipientContainer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  sectionLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginBottom: 12,
  },
  recipientCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  recipientInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  recipientDetails: {
    marginLeft: 12,
    flex: 1,
  },
  recipientName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
  },
  recipientContact: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  changeButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#6200EE',
  },
  changeButtonText: {
    color: '#6200EE',
    fontSize: 14,
    fontWeight: '600',
  },
  balanceContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#E8F5E9',
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  balanceLabel: {
    fontSize: 14,
    color: '#666',
    marginLeft: 8,
  },
  balanceAmount: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#4CAF50',
    marginLeft: 'auto',
  },
  amountSection: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  descriptionSection: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  descriptionInput: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
  },
  descriptionPlaceholder: {
    fontSize: 16,
    color: '#999',
    marginLeft: 12,
  },
  paymentMethodContainer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 8,
  },
  selectedMethodCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
  },
  methodInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  methodName: {
    fontSize: 16,
    color: '#212121',
    marginLeft: 12,
  },
  addMethodButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderWidth: 2,
    borderColor: '#6200EE',
    borderStyle: 'dashed',
    borderRadius: 8,
  },
  addMethodText: {
    fontSize: 16,
    color: '#6200EE',
    fontWeight: '600',
    marginLeft: 8,
  },
  methodsListContainer: {
    marginTop: 12,
    maxHeight: 300,
  },
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFEBEE',
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 8,
  },
  errorText: {
    fontSize: 14,
    color: '#F44336',
    marginLeft: 8,
    flex: 1,
  },
  footer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  sendButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#6200EE',
    paddingVertical: 16,
    borderRadius: 8,
  },
  sendButtonDisabled: {
    backgroundColor: '#BDBDBD',
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
    marginLeft: 8,
  },
});

export default PaymentDetailsScreen;
