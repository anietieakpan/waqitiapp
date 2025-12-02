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
  Button,
  useTheme,
  Surface,
  TextInput,
  Divider,
  Avatar,
  IconButton,
} from 'react-native-paper';
import { useNavigation, useRoute } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useSelector } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../../store/store';
import { formatCurrency } from '../../utils/formatters';
import { useBiometric } from '../../hooks/useBiometric';
import HapticService from '../../services/HapticService';
import Header from '../../components/common/Header';

interface PaymentData {
  type: 'send' | 'request';
  recipient: {
    id: string;
    name: string;
    avatar?: string;
    phoneNumber?: string;
    email?: string;
  };
  amount: number;
  currency: string;
  note?: string;
  paymentMethod?: {
    id: string;
    type: 'card' | 'bank' | 'wallet';
    last4: string;
    name: string;
  };
  fee?: number;
  total?: number;
}

/**
 * Payment Confirmation Screen - Final confirmation before processing payment
 */
const PaymentConfirmationScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute();
  const { paymentData } = route.params as { paymentData: PaymentData };
  
  const wallet = useSelector((state: RootState) => state.wallet);
  const user = useSelector((state: RootState) => state.auth.user);
  const { authenticateWithBiometrics, isBiometricSupported } = useBiometric();
  
  const [note, setNote] = useState(paymentData.note || '');
  const [isProcessing, setIsProcessing] = useState(false);
  const [showPinInput, setShowPinInput] = useState(false);
  const [pin, setPin] = useState('');

  const fee = paymentData.fee || 0;
  const total = paymentData.amount + fee;

  const handleConfirmPayment = async () => {
    try {
      setIsProcessing(true);
      
      // Try biometric authentication first
      if (isBiometricSupported) {
        const biometricResult = await authenticateWithBiometrics();
        if (!biometricResult.success) {
          setShowPinInput(true);
          setIsProcessing(false);
          return;
        }
      } else {
        setShowPinInput(true);
        setIsProcessing(false);
        return;
      }

      // Process payment
      await processPayment();
    } catch (error) {
      console.error('Payment confirmation error:', error);
      HapticService.error();
      setIsProcessing(false);
    }
  };

  const handlePinSubmit = async () => {
    if (pin.length !== 4) {
      HapticService.error();
      return;
    }

    try {
      setIsProcessing(true);
      await processPayment();
    } catch (error) {
      console.error('PIN verification error:', error);
      HapticService.error();
      setIsProcessing(false);
    }
  };

  const processPayment = async () => {
    // Simulate payment processing
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    HapticService.success();
    
    // Navigate to success screen
    navigation.navigate('PaymentSuccess', {
      paymentId: 'pay_' + Date.now(),
      paymentData: {
        ...paymentData,
        note,
        total,
      },
    } as never);
  };

  const handleBack = () => {
    if (showPinInput) {
      setShowPinInput(false);
      setPin('');
      return;
    }
    navigation.goBack();
  };

  const renderPaymentMethod = () => {
    if (!paymentData.paymentMethod) {
      return (
        <View style={styles.paymentMethodContainer}>
          <Icon name=\"wallet\" size={24} color={theme.colors.primary} />
          <View style={styles.paymentMethodInfo}>
            <Text style={styles.paymentMethodName}>Waqiti Wallet</Text>
            <Text style={styles.paymentMethodBalance}>
              Balance: {formatCurrency(wallet.balance, wallet.currency)}
            </Text>
          </View>
        </View>
      );
    }

    const { paymentMethod } = paymentData;
    const iconName = paymentMethod.type === 'card' ? 'credit-card' : 'bank';
    
    return (
      <View style={styles.paymentMethodContainer}>
        <Icon name={iconName} size={24} color={theme.colors.primary} />
        <View style={styles.paymentMethodInfo}>
          <Text style={styles.paymentMethodName}>{paymentMethod.name}</Text>
          <Text style={styles.paymentMethodBalance}>••••{paymentMethod.last4}</Text>
        </View>
        <TouchableOpacity onPress={() => {/* Handle change payment method */}}>
          <Text style={styles.changeText}>Change</Text>
        </TouchableOpacity>
      </View>
    );
  };

  if (showPinInput) {
    return (
      <SafeAreaView style={styles.container}>
        <Header
          title=\"Enter PIN\"
          leftAction={
            <IconButton
              icon=\"arrow-left\"
              size={24}
              onPress={handleBack}
            />
          }
        />
        
        <View style={styles.pinContainer}>
          <Text style={styles.pinTitle}>Enter your 4-digit PIN</Text>
          <Text style={styles.pinSubtitle}>
            Confirm your payment of {formatCurrency(total, paymentData.currency)}
          </Text>
          
          <Surface style={styles.pinInputContainer} elevation={2}>
            <TextInput
              value={pin}
              onChangeText={setPin}
              mode=\"outlined\"
              keyboardType=\"numeric\"
              secureTextEntry
              maxLength={4}
              style={styles.pinInput}
              autoFocus
            />
          </Surface>
          
          <Button
            mode=\"contained\"
            onPress={handlePinSubmit}
            loading={isProcessing}
            disabled={pin.length !== 4 || isProcessing}
            style={styles.pinSubmitButton}
            contentStyle={styles.buttonContent}
          >
            Confirm Payment
          </Button>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <SafeAreaView style={styles.container}>
        <Header
          title=\"Confirm Payment\"
          leftAction={
            <IconButton
              icon=\"arrow-left\"
              size={24}
              onPress={handleBack}
            />
          }
        />

        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Recipient Information */}
          <Surface style={styles.recipientCard} elevation={2}>
            <View style={styles.recipientHeader}>
              <Text style={styles.sectionTitle}>
                {paymentData.type === 'send' ? 'Sending to' : 'Requesting from'}
              </Text>
            </View>
            
            <View style={styles.recipientInfo}>
              <Avatar.Text
                size={64}
                label={paymentData.recipient.name.split(' ').map(n => n[0]).join('')}
                style={styles.recipientAvatar}
              />
              <View style={styles.recipientDetails}>
                <Text style={styles.recipientName}>{paymentData.recipient.name}</Text>
                <Text style={styles.recipientContact}>
                  {paymentData.recipient.phoneNumber || paymentData.recipient.email}
                </Text>
              </View>
            </View>
          </Surface>

          {/* Amount Information */}
          <Surface style={styles.amountCard} elevation={2}>
            <View style={styles.amountHeader}>
              <Text style={styles.sectionTitle}>Payment Details</Text>
            </View>
            
            <View style={styles.amountBreakdown}>
              <View style={styles.amountRow}>
                <Text style={styles.amountLabel}>Amount</Text>
                <Text style={styles.amountValue}>
                  {formatCurrency(paymentData.amount, paymentData.currency)}
                </Text>
              </View>
              
              {fee > 0 && (
                <View style={styles.amountRow}>
                  <Text style={styles.amountLabel}>Fee</Text>
                  <Text style={styles.amountValue}>
                    {formatCurrency(fee, paymentData.currency)}
                  </Text>
                </View>
              )}
              
              <Divider style={styles.divider} />
              
              <View style={styles.amountRow}>
                <Text style={styles.totalLabel}>Total</Text>
                <Text style={styles.totalValue}>
                  {formatCurrency(total, paymentData.currency)}
                </Text>
              </View>
            </View>
          </Surface>

          {/* Payment Method */}
          <Surface style={styles.paymentMethodCard} elevation={2}>
            <View style={styles.paymentMethodHeader}>
              <Text style={styles.sectionTitle}>Payment Method</Text>
            </View>
            {renderPaymentMethod()}
          </Surface>

          {/* Note */}
          <Surface style={styles.noteCard} elevation={2}>
            <View style={styles.noteHeader}>
              <Text style={styles.sectionTitle}>Add a note (optional)</Text>
            </View>
            
            <TextInput
              value={note}
              onChangeText={setNote}
              mode=\"outlined\"
              placeholder=\"What's this for?\"
              multiline
              numberOfLines={3}
              maxLength={200}
              style={styles.noteInput}
            />
          </Surface>

          {/* Security Notice */}
          <Surface style={styles.securityNotice} elevation={1}>
            <Icon name=\"shield-check\" size={24} color=\"#4CAF50\" />
            <View style={styles.securityNoticeText}>
              <Text style={styles.securityTitle}>Secure Payment</Text>
              <Text style={styles.securitySubtitle}>
                Your payment is protected with bank-level encryption
              </Text>
            </View>
          </Surface>
        </ScrollView>

        {/* Confirm Button */}
        <View style={styles.bottomContainer}>
          <Button
            mode=\"contained\"
            onPress={handleConfirmPayment}
            loading={isProcessing}
            disabled={isProcessing}
            style={styles.confirmButton}
            contentStyle={styles.buttonContent}
          >
            {paymentData.type === 'send' ? `Send ${formatCurrency(total, paymentData.currency)}` : `Request ${formatCurrency(paymentData.amount, paymentData.currency)}`}
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
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  recipientCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  recipientHeader: {
    marginBottom: 16,
  },
  recipientInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  recipientAvatar: {
    marginRight: 16,
  },
  recipientDetails: {
    flex: 1,
  },
  recipientName: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  recipientContact: {
    fontSize: 14,
    color: '#666',
  },
  amountCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  amountHeader: {
    marginBottom: 16,
  },
  amountBreakdown: {
    gap: 12,
  },
  amountRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  amountLabel: {
    fontSize: 16,
    color: '#333',
  },
  amountValue: {
    fontSize: 16,
    color: '#333',
    fontWeight: '500',
  },
  divider: {
    marginVertical: 8,
  },
  totalLabel: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  totalValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  paymentMethodCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  paymentMethodHeader: {
    marginBottom: 16,
  },
  paymentMethodContainer: {
    flexDirection: 'row',
    alignItems: 'center',
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
  paymentMethodBalance: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  changeText: {
    color: '#2196F3',
    fontSize: 14,
    fontWeight: '500',
  },
  noteCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    backgroundColor: 'white',
  },
  noteHeader: {
    marginBottom: 16,
  },
  noteInput: {
    backgroundColor: 'white',
  },
  securityNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#f8fff8',
    borderColor: '#4CAF50',
    borderWidth: 1,
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
  confirmButton: {
    marginBottom: 0,
  },
  buttonContent: {
    height: 52,
  },
  pinContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  pinTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
    textAlign: 'center',
  },
  pinSubtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 32,
  },
  pinInputContainer: {
    width: '100%',
    maxWidth: 200,
    marginBottom: 32,
    borderRadius: 12,
    backgroundColor: 'white',
  },
  pinInput: {
    backgroundColor: 'white',
    textAlign: 'center',
    fontSize: 24,
    letterSpacing: 8,
  },
  pinSubmitButton: {
    width: '100%',
    maxWidth: 300,
  },
});

export default PaymentConfirmationScreen;