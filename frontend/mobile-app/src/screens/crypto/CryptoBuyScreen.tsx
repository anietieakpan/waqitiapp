/**
 * Crypto Buy Screen
 * Interface for purchasing cryptocurrency with fiat currency
 */
import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  Alert,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useAuth } from '../../contexts/AuthContext';
import { useCrypto } from '../../contexts/CryptoContext';
import { usePayment } from '../../contexts/PaymentContext';
import { CryptoCurrencySelector } from '../../components/crypto/CryptoCurrencySelector';
import { PaymentMethodSelector } from '../../components/payment/PaymentMethodSelector';
import { PriceDisplay } from '../../components/crypto/PriceDisplay';
import { TransactionPreview } from '../../components/crypto/TransactionPreview';
import { LoadingButton } from '../../components/common/LoadingButton';
import { theme } from '../../theme';
import { CryptoCurrency, CryptoBuyRequest } from '../../types/crypto';
import { PaymentMethod } from '../../types/payment';

interface CryptoBuyScreenProps {
  navigation: any;
}

export const CryptoBuyScreen: React.FC<CryptoBuyScreenProps> = ({ navigation }) => {
  const { user } = useAuth();
  const { 
    buyCryptocurrency, 
    getCryptoPrice, 
    estimateTradingFee,
    isLoading: isCryptoLoading 
  } = useCrypto();
  const { paymentMethods, userBalance } = usePayment();

  const [selectedCurrency, setSelectedCurrency] = useState<CryptoCurrency>(CryptoCurrency.BITCOIN);
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<PaymentMethod | null>(null);
  const [usdAmount, setUsdAmount] = useState('');
  const [cryptoAmount, setCryptoAmount] = useState('');
  const [currentPrice, setCurrentPrice] = useState(0);
  const [tradingFee, setTradingFee] = useState(0);
  const [isCalculating, setIsCalculating] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [inputMode, setInputMode] = useState<'usd' | 'crypto'>('usd');

  useEffect(() => {
    loadCryptoPrice();
  }, [selectedCurrency]);

  useEffect(() => {
    if (usdAmount && !isCalculating) {
      calculateAmounts();
    }
  }, [usdAmount, currentPrice]);

  useEffect(() => {
    if (cryptoAmount && inputMode === 'crypto' && !isCalculating) {
      calculateUsdAmount();
    }
  }, [cryptoAmount, currentPrice]);

  const loadCryptoPrice = async () => {
    try {
      const price = await getCryptoPrice(selectedCurrency);
      setCurrentPrice(price);
    } catch (error) {
      console.error('Failed to load crypto price:', error);
      Alert.alert('Error', 'Failed to load current price. Please try again.');
    }
  };

  const calculateAmounts = async () => {
    if (!usdAmount || parseFloat(usdAmount) <= 0) {
      setCryptoAmount('');
      setTradingFee(0);
      return;
    }

    setIsCalculating(true);
    try {
      const usdValue = parseFloat(usdAmount);
      const fee = await estimateTradingFee(usdValue);
      const cryptoValue = usdValue / currentPrice;
      
      setCryptoAmount(cryptoValue.toFixed(8));
      setTradingFee(fee);
    } catch (error) {
      console.error('Failed to calculate amounts:', error);
    } finally {
      setIsCalculating(false);
    }
  };

  const calculateUsdAmount = async () => {
    if (!cryptoAmount || parseFloat(cryptoAmount) <= 0) {
      setUsdAmount('');
      setTradingFee(0);
      return;
    }

    setIsCalculating(true);
    try {
      const cryptoValue = parseFloat(cryptoAmount);
      const usdValue = cryptoValue * currentPrice;
      const fee = await estimateTradingFee(usdValue);
      
      setUsdAmount(usdValue.toFixed(2));
      setTradingFee(fee);
    } catch (error) {
      console.error('Failed to calculate USD amount:', error);
    } finally {
      setIsCalculating(false);
    }
  };

  const handleUsdAmountChange = (value: string) => {
    setInputMode('usd');
    setUsdAmount(value);
  };

  const handleCryptoAmountChange = (value: string) => {
    setInputMode('crypto');
    setCryptoAmount(value);
  };

  const handlePresetAmount = (amount: number) => {
    setInputMode('usd');
    setUsdAmount(amount.toString());
  };

  const validateTransaction = (): string | null => {
    if (!selectedPaymentMethod) {
      return 'Please select a payment method';
    }

    if (!usdAmount || parseFloat(usdAmount) <= 0) {
      return 'Please enter a valid amount';
    }

    const totalCost = parseFloat(usdAmount) + tradingFee;
    if (totalCost > userBalance) {
      return 'Insufficient balance';
    }

    const minPurchase = 1;
    if (parseFloat(usdAmount) < minPurchase) {
      return `Minimum purchase amount is $${minPurchase}`;
    }

    const maxPurchase = 10000;
    if (parseFloat(usdAmount) > maxPurchase) {
      return `Maximum purchase amount is $${maxPurchase}`;
    }

    return null;
  };

  const handlePreview = () => {
    const validationError = validateTransaction();
    if (validationError) {
      Alert.alert('Invalid Transaction', validationError);
      return;
    }
    setShowPreview(true);
  };

  const handleConfirmPurchase = async () => {
    try {
      const request: CryptoBuyRequest = {
        currency: selectedCurrency,
        usdAmount: parseFloat(usdAmount),
        paymentMethod: selectedPaymentMethod!,
      };

      await buyCryptocurrency(request);
      
      Alert.alert(
        'Purchase Successful',
        `You have successfully purchased ${cryptoAmount} ${selectedCurrency}`,
        [
          {
            text: 'View Wallet',
            onPress: () => navigation.navigate('CryptoWallet'),
          },
          {
            text: 'Buy More',
            onPress: () => {
              setShowPreview(false);
              setUsdAmount('');
              setCryptoAmount('');
            },
          },
        ]
      );
    } catch (error) {
      console.error('Failed to purchase cryptocurrency:', error);
      Alert.alert('Purchase Failed', 'Failed to complete purchase. Please try again.');
    }
  };

  const renderAmountInput = () => (
    <View style={styles.amountSection}>
      <Text style={styles.sectionTitle}>Amount</Text>
      
      <View style={styles.inputContainer}>
        <View style={styles.usdInputContainer}>
          <Text style={styles.currencySymbol}>$</Text>
          <TextInput
            style={styles.amountInput}
            value={usdAmount}
            onChangeText={handleUsdAmountChange}
            placeholder="0.00"
            placeholderTextColor={theme.colors.textLight}
            keyboardType="decimal-pad"
            returnKeyType="done"
          />
          <Text style={styles.currencyLabel}>USD</Text>
        </View>

        <TouchableOpacity
          style={styles.swapButton}
          onPress={() => {
            setInputMode(inputMode === 'usd' ? 'crypto' : 'usd');
          }}
        >
          <Ionicons name="swap-vertical" size={20} color={theme.colors.primary} />
        </TouchableOpacity>

        <View style={styles.cryptoInputContainer}>
          <TextInput
            style={styles.amountInput}
            value={cryptoAmount}
            onChangeText={handleCryptoAmountChange}
            placeholder="0.00000000"
            placeholderTextColor={theme.colors.textLight}
            keyboardType="decimal-pad"
            returnKeyType="done"
          />
          <Text style={styles.currencyLabel}>{selectedCurrency}</Text>
        </View>
      </View>

      <View style={styles.presetAmounts}>
        {[25, 50, 100, 250, 500].map((amount) => (
          <TouchableOpacity
            key={amount}
            style={styles.presetButton}
            onPress={() => handlePresetAmount(amount)}
          >
            <Text style={styles.presetButtonText}>${amount}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );

  const renderTransactionSummary = () => (
    <View style={styles.summarySection}>
      <Text style={styles.sectionTitle}>Transaction Summary</Text>
      
      <View style={styles.summaryRow}>
        <Text style={styles.summaryLabel}>Purchase Amount</Text>
        <Text style={styles.summaryValue}>${parseFloat(usdAmount || '0').toFixed(2)}</Text>
      </View>
      
      <View style={styles.summaryRow}>
        <Text style={styles.summaryLabel}>Trading Fee (1.5%)</Text>
        <Text style={styles.summaryValue}>${tradingFee.toFixed(2)}</Text>
      </View>
      
      <View style={[styles.summaryRow, styles.summaryTotal]}>
        <Text style={styles.summaryTotalLabel}>Total Cost</Text>
        <Text style={styles.summaryTotalValue}>
          ${(parseFloat(usdAmount || '0') + tradingFee).toFixed(2)}
        </Text>
      </View>
      
      <View style={styles.summaryRow}>
        <Text style={styles.summaryLabel}>You'll Receive</Text>
        <Text style={styles.summaryValue}>
          {parseFloat(cryptoAmount || '0').toFixed(8)} {selectedCurrency}
        </Text>
      </View>
    </View>
  );

  if (showPreview) {
    return (
      <TransactionPreview
        type="buy"
        currency={selectedCurrency}
        amount={parseFloat(cryptoAmount)}
        usdValue={parseFloat(usdAmount)}
        fee={tradingFee}
        paymentMethod={selectedPaymentMethod!}
        onConfirm={handleConfirmPurchase}
        onCancel={() => setShowPreview(false)}
        isLoading={isCryptoLoading}
      />
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => navigation.goBack()}
          >
            <Ionicons name="arrow-back" size={24} color={theme.colors.text} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Buy Crypto</Text>
          <View style={styles.headerRight} />
        </View>

        <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
          <CryptoCurrencySelector
            selectedCurrency={selectedCurrency}
            onCurrencySelect={setSelectedCurrency}
          />

          <PriceDisplay
            currency={selectedCurrency}
            price={currentPrice}
            isLoading={isCalculating}
          />

          {renderAmountInput()}

          <PaymentMethodSelector
            paymentMethods={paymentMethods}
            selectedMethod={selectedPaymentMethod}
            onMethodSelect={setSelectedPaymentMethod}
          />

          {usdAmount && currentPrice > 0 && renderTransactionSummary()}
        </ScrollView>

        <View style={styles.footer}>
          <LoadingButton
            title="Preview Purchase"
            onPress={handlePreview}
            loading={isCryptoLoading || isCalculating}
            disabled={!usdAmount || !selectedPaymentMethod || parseFloat(usdAmount || '0') <= 0}
            style={styles.previewButton}
          />
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  backButton: {
    padding: 8,
    marginLeft: -8,
  },
  headerTitle: {
    flex: 1,
    fontSize: 18,
    fontWeight: 'bold',
    color: theme.colors.text,
    textAlign: 'center',
  },
  headerRight: {
    width: 40,
  },
  content: {
    flex: 1,
    padding: 20,
  },
  amountSection: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 12,
  },
  inputContainer: {
    alignItems: 'center',
    gap: 12,
  },
  usdInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    width: '100%',
  },
  cryptoInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    width: '100%',
  },
  currencySymbol: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.text,
    marginRight: 8,
  },
  amountInput: {
    flex: 1,
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.text,
    textAlign: 'right',
  },
  currencyLabel: {
    fontSize: 14,
    fontWeight: '500',
    color: theme.colors.textLight,
    marginLeft: 8,
  },
  swapButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: theme.colors.surface,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 2,
    shadowColor: theme.colors.black,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  presetAmounts: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 16,
    gap: 8,
  },
  presetButton: {
    flex: 1,
    backgroundColor: theme.colors.surface,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  presetButtonText: {
    fontSize: 12,
    fontWeight: '500',
    color: theme.colors.primary,
  },
  summarySection: {
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    padding: 16,
    marginTop: 16,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  summaryLabel: {
    fontSize: 14,
    color: theme.colors.textLight,
  },
  summaryValue: {
    fontSize: 14,
    fontWeight: '500',
    color: theme.colors.text,
  },
  summaryTotal: {
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
    marginTop: 8,
    paddingTop: 12,
  },
  summaryTotalLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
  },
  summaryTotalValue: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
  },
  footer: {
    padding: 20,
    paddingBottom: 34,
  },
  previewButton: {
    backgroundColor: theme.colors.primary,
    borderRadius: 12,
    paddingVertical: 16,
  },
});