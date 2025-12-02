/**
 * Crypto Send Screen
 * Interface for sending cryptocurrency to external addresses
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
import { CryptoCurrencySelector } from '../../components/crypto/CryptoCurrencySelector';
import { WalletBalance } from '../../components/crypto/WalletBalance';
import { AddressInput } from '../../components/crypto/AddressInput';
import { NetworkFeeSelector } from '../../components/crypto/NetworkFeeSelector';
import { TransactionPreview } from '../../components/crypto/TransactionPreview';
import { LoadingButton } from '../../components/common/LoadingButton';
import { theme } from '../../theme';
import { CryptoCurrency, CryptoSendRequest, FeeSpeed } from '../../types/crypto';

interface CryptoSendScreenProps {
  navigation: any;
  route: any;
}

export const CryptoSendScreen: React.FC<CryptoSendScreenProps> = ({ navigation, route }) => {
  const { user } = useAuth();
  const {
    wallets,
    sendCryptocurrency,
    estimateNetworkFee,
    validateAddress,
    isLoading: isCryptoLoading,
  } = useCrypto();

  // Pre-fill from QR scan or route params
  const initialAddress = route.params?.address || '';
  const initialAmount = route.params?.amount || '';
  const initialCurrency = route.params?.currency || CryptoCurrency.BITCOIN;

  const [selectedCurrency, setSelectedCurrency] = useState<CryptoCurrency>(initialCurrency);
  const [toAddress, setToAddress] = useState(initialAddress);
  const [amount, setAmount] = useState(initialAmount);
  const [memo, setMemo] = useState('');
  const [feeSpeed, setFeeSpeed] = useState<FeeSpeed>(FeeSpeed.STANDARD);
  const [networkFee, setNetworkFee] = useState(0);
  const [isValidatingAddress, setIsValidatingAddress] = useState(false);
  const [isValidAddress, setIsValidAddress] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [showMaxAmount, setShowMaxAmount] = useState(false);

  const currentWallet = wallets.find(w => w.currency === selectedCurrency);

  useEffect(() => {
    if (toAddress && selectedCurrency) {
      validateAddressFormat();
    }
  }, [toAddress, selectedCurrency]);

  useEffect(() => {
    if (amount && isValidAddress) {
      estimateFee();
    }
  }, [amount, feeSpeed, isValidAddress]);

  const validateAddressFormat = async () => {
    if (!toAddress.trim()) {
      setIsValidAddress(false);
      return;
    }

    setIsValidatingAddress(true);
    try {
      const isValid = await validateAddress(toAddress, selectedCurrency);
      setIsValidAddress(isValid);
    } catch (error) {
      console.error('Address validation failed:', error);
      setIsValidAddress(false);
    } finally {
      setIsValidatingAddress(false);
    }
  };

  const estimateFee = async () => {
    if (!amount || !toAddress || !currentWallet) return;

    try {
      const fee = await estimateNetworkFee({
        currency: selectedCurrency,
        toAddress,
        amount: parseFloat(amount),
        feeSpeed,
      });
      setNetworkFee(fee);
    } catch (error) {
      console.error('Failed to estimate network fee:', error);
      setNetworkFee(0);
    }
  };

  const handleMaxAmount = () => {
    if (!currentWallet) return;
    
    // Set max amount minus estimated fee
    const maxSendable = Math.max(0, currentWallet.availableBalance - networkFee);
    setAmount(maxSendable.toFixed(8));
    setShowMaxAmount(false);
  };

  const handleQRScan = () => {
    navigation.navigate('QRScanner', {
      type: 'crypto',
      onScan: (data: any) => {
        if (data.address) setToAddress(data.address);
        if (data.amount) setAmount(data.amount);
        if (data.currency) setSelectedCurrency(data.currency);
        if (data.memo) setMemo(data.memo);
      },
    });
  };

  const validateTransaction = (): string | null => {
    if (!currentWallet) {
      return 'No wallet found for selected currency';
    }

    if (!toAddress.trim()) {
      return 'Please enter a destination address';
    }

    if (!isValidAddress) {
      return 'Invalid destination address';
    }

    if (!amount || parseFloat(amount) <= 0) {
      return 'Please enter a valid amount';
    }

    const sendAmount = parseFloat(amount);
    const totalCost = sendAmount + networkFee;

    if (totalCost > currentWallet.availableBalance) {
      return 'Insufficient balance (including network fee)';
    }

    const minSend = 0.00001; // Dust threshold
    if (sendAmount < minSend) {
      return `Minimum send amount is ${minSend} ${selectedCurrency}`;
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

  const handleConfirmSend = async () => {
    try {
      const request: CryptoSendRequest = {
        currency: selectedCurrency,
        toAddress,
        amount: parseFloat(amount),
        memo: memo.trim() || undefined,
        feeSpeed,
      };

      const result = await sendCryptocurrency(request);
      
      Alert.alert(
        'Transaction Submitted',
        `Your ${selectedCurrency} transaction has been submitted to the network. Transaction ID: ${result.transactionId}`,
        [
          {
            text: 'View Transaction',
            onPress: () => navigation.navigate('CryptoTransactionDetails', {
              transactionId: result.transactionId,
            }),
          },
          {
            text: 'Done',
            onPress: () => navigation.navigate('CryptoWallet'),
          },
        ]
      );
    } catch (error) {
      console.error('Failed to send cryptocurrency:', error);
      Alert.alert('Transaction Failed', 'Failed to send transaction. Please try again.');
    }
  };

  const renderAmountInput = () => (
    <View style={styles.amountSection}>
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Amount</Text>
        {currentWallet && (
          <TouchableOpacity
            style={styles.maxButton}
            onPress={handleMaxAmount}
          >
            <Text style={styles.maxButtonText}>MAX</Text>
          </TouchableOpacity>
        )}
      </View>
      
      <View style={styles.amountInputContainer}>
        <TextInput
          style={styles.amountInput}
          value={amount}
          onChangeText={setAmount}
          placeholder="0.00000000"
          placeholderTextColor={theme.colors.textLight}
          keyboardType="decimal-pad"
          returnKeyType="done"
        />
        <Text style={styles.currencyLabel}>{selectedCurrency}</Text>
      </View>

      {currentWallet && amount && (
        <Text style={styles.usdValue}>
          ≈ ${(parseFloat(amount) * currentWallet.currentPrice).toFixed(2)} USD
        </Text>
      )}
    </View>
  );

  const renderMemoInput = () => (
    <View style={styles.memoSection}>
      <Text style={styles.sectionTitle}>Memo (Optional)</Text>
      <TextInput
        style={styles.memoInput}
        value={memo}
        onChangeText={setMemo}
        placeholder="Add a note for this transaction"
        placeholderTextColor={theme.colors.textLight}
        multiline
        numberOfLines={3}
        maxLength={100}
      />
      <Text style={styles.charCount}>{memo.length}/100</Text>
    </View>
  );

  const renderTransactionSummary = () => (
    <View style={styles.summarySection}>
      <Text style={styles.sectionTitle}>Transaction Summary</Text>
      
      <View style={styles.summaryRow}>
        <Text style={styles.summaryLabel}>Send Amount</Text>
        <Text style={styles.summaryValue}>
          {parseFloat(amount || '0').toFixed(8)} {selectedCurrency}
        </Text>
      </View>
      
      <View style={styles.summaryRow}>
        <Text style={styles.summaryLabel}>Network Fee</Text>
        <Text style={styles.summaryValue}>
          {networkFee.toFixed(8)} {selectedCurrency}
        </Text>
      </View>
      
      <View style={[styles.summaryRow, styles.summaryTotal]}>
        <Text style={styles.summaryTotalLabel}>Total Cost</Text>
        <Text style={styles.summaryTotalValue}>
          {(parseFloat(amount || '0') + networkFee).toFixed(8)} {selectedCurrency}
        </Text>
      </View>
    </View>
  );

  if (showPreview) {
    return (
      <TransactionPreview
        type="send"
        currency={selectedCurrency}
        amount={parseFloat(amount)}
        toAddress={toAddress}
        networkFee={networkFee}
        memo={memo}
        feeSpeed={feeSpeed}
        onConfirm={handleConfirmSend}
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
          <Text style={styles.headerTitle}>Send Crypto</Text>
          <TouchableOpacity
            style={styles.scanButton}
            onPress={handleQRScan}
          >
            <MaterialIcons name="qr-code-scanner" size={24} color={theme.colors.primary} />
          </TouchableOpacity>
        </View>

        <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
          <CryptoCurrencySelector
            selectedCurrency={selectedCurrency}
            onCurrencySelect={setSelectedCurrency}
            wallets={wallets}
          />

          {currentWallet && (
            <WalletBalance
              wallet={currentWallet}
              showActions={false}
            />
          )}

          <AddressInput
            address={toAddress}
            onAddressChange={setToAddress}
            currency={selectedCurrency}
            isValidating={isValidatingAddress}
            isValid={isValidAddress}
            onScan={handleQRScan}
          />

          {renderAmountInput()}

          <NetworkFeeSelector
            feeSpeed={feeSpeed}
            onFeeSpeedChange={setFeeSpeed}
            networkFee={networkFee}
            currency={selectedCurrency}
          />

          {renderMemoInput()}

          {amount && isValidAddress && renderTransactionSummary()}
        </ScrollView>

        <View style={styles.footer}>
          <LoadingButton
            title="Preview Transaction"
            onPress={handlePreview}
            loading={isCryptoLoading || isValidatingAddress}
            disabled={!amount || !toAddress || !isValidAddress || parseFloat(amount || '0') <= 0}
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
  scanButton: {
    padding: 8,
    marginRight: -8,
  },
  content: {
    flex: 1,
    padding: 20,
  },
  amountSection: {
    marginBottom: 24,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
  },
  maxButton: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 6,
  },
  maxButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: theme.colors.white,
  },
  amountInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
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
  usdValue: {
    fontSize: 14,
    color: theme.colors.textLight,
    textAlign: 'center',
    marginTop: 8,
  },
  memoSection: {
    marginBottom: 24,
  },
  memoInput: {
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    color: theme.colors.text,
    textAlignVertical: 'top',
    minHeight: 80,
  },
  charCount: {
    fontSize: 12,
    color: theme.colors.textLight,
    textAlign: 'right',
    marginTop: 4,
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