import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { RouteProp } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';

import { useAppDispatch, useAppSelector } from '../store/hooks';
import { sendMoney, resetTransactionState } from '../store/slices/transactionSlice';
import { getContacts } from '../store/slices/contactSlice';
import { RootStackParamList } from '../navigation/types';
import { COLORS, FONTS, SIZES } from '../constants/theme';
import { ContactItem } from '../components/ContactItem';
import { AmountInput } from '../components/AmountInput';
import { PaymentMethodSelector } from '../components/PaymentMethodSelector';
import { hapticFeedback } from '../utils/haptics';
import { showToast } from '../utils/toast';
import { formatCurrency } from '../utils/currency';

type SendMoneyScreenNavigationProp = StackNavigationProp<RootStackParamList, 'SendMoney'>;
type SendMoneyScreenRouteProp = RouteProp<RootStackParamList, 'SendMoney'>;

interface Props {
  navigation: SendMoneyScreenNavigationProp;
  route: SendMoneyScreenRouteProp;
}

interface SendMoneyStep {
  id: number;
  title: string;
  completed: boolean;
}

export const SendMoneyScreen: React.FC<Props> = ({ navigation, route }) => {
  const dispatch = useAppDispatch();
  const { loading, error, lastTransaction } = useAppSelector((state) => state.transaction);
  const { contacts, loading: contactsLoading } = useAppSelector((state) => state.contact);
  const { user } = useAppSelector((state) => state.auth);

  const [currentStep, setCurrentStep] = useState(1);
  const [selectedContact, setSelectedContact] = useState<any>(null);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<any>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const steps: SendMoneyStep[] = [
    { id: 1, title: 'Select Recipient', completed: false },
    { id: 2, title: 'Enter Amount', completed: false },
    { id: 3, title: 'Review & Send', completed: false },
  ];

  useEffect(() => {
    dispatch(getContacts());
    
    return () => {
      dispatch(resetTransactionState());
    };
  }, [dispatch]);

  useEffect(() => {
    if (lastTransaction && !loading && !error) {
      hapticFeedback.success();
      showToast('Money sent successfully!');
      navigation.navigate('TransactionSuccess', { transaction: lastTransaction });
    }
  }, [lastTransaction, loading, error, navigation]);

  useEffect(() => {
    if (error) {
      hapticFeedback.error();
      showToast(error);
    }
  }, [error]);

  const filteredContacts = contacts.filter(contact =>
    contact.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    contact.username?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleContactSelect = (contact: any) => {
    setSelectedContact(contact);
    setCurrentStep(2);
    hapticFeedback.light();
  };

  const handleAmountSubmit = () => {
    if (!amount || parseFloat(amount) <= 0) {
      showToast('Please enter a valid amount');
      return;
    }

    if (parseFloat(amount) > user?.balance) {
      showToast('Insufficient balance');
      return;
    }

    setCurrentStep(3);
    hapticFeedback.light();
  };

  const handleSendMoney = () => {
    if (!selectedContact || !amount) {
      showToast('Please complete all required fields');
      return;
    }

    const transactionData = {
      recipientId: selectedContact.id,
      amount: parseFloat(amount),
      note: note.trim(),
      paymentMethodId: selectedPaymentMethod?.id,
    };

    dispatch(sendMoney(transactionData));
    hapticFeedback.light();
  };

  const renderStepIndicator = () => (
    <View style={styles.stepIndicator}>
      {steps.map((step, index) => (
        <View key={step.id} style={styles.stepContainer}>
          <View style={[
            styles.stepCircle,
            currentStep >= step.id && styles.stepCircleActive,
            currentStep > step.id && styles.stepCircleCompleted,
          ]}>
            {currentStep > step.id ? (
              <Ionicons name="checkmark" size={16} color={COLORS.white} />
            ) : (
              <Text style={[
                styles.stepNumber,
                currentStep >= step.id && styles.stepNumberActive,
              ]}>
                {step.id}
              </Text>
            )}
          </View>
          <Text style={[
            styles.stepTitle,
            currentStep >= step.id && styles.stepTitleActive,
          ]}>
            {step.title}
          </Text>
          {index < steps.length - 1 && (
            <View style={[
              styles.stepConnector,
              currentStep > step.id && styles.stepConnectorActive,
            ]} />
          )}
        </View>
      ))}
    </View>
  );

  const renderSelectRecipient = () => (
    <View style={styles.stepContent}>
      <Text style={styles.stepHeader}>Who are you sending money to?</Text>
      
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={20} color={COLORS.gray} style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search contacts..."
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholderTextColor={COLORS.gray}
        />
      </View>

      <ScrollView style={styles.contactsList} showsVerticalScrollIndicator={false}>
        {contactsLoading ? (
          <ActivityIndicator size="large" color={COLORS.primary} style={styles.loader} />
        ) : filteredContacts.length > 0 ? (
          filteredContacts.map((contact) => (
            <ContactItem
              key={contact.id}
              contact={contact}
              onPress={() => handleContactSelect(contact)}
              selected={selectedContact?.id === contact.id}
            />
          ))
        ) : (
          <View style={styles.emptyState}>
            <Ionicons name="people-outline" size={48} color={COLORS.gray} />
            <Text style={styles.emptyStateText}>
              {searchQuery ? 'No contacts found' : 'No contacts available'}
            </Text>
          </View>
        )}
      </ScrollView>

      <TouchableOpacity
        style={styles.addContactButton}
        onPress={() => navigation.navigate('AddContact')}
      >
        <Ionicons name="person-add" size={20} color={COLORS.primary} />
        <Text style={styles.addContactText}>Add New Contact</Text>
      </TouchableOpacity>
    </View>
  );

  const renderEnterAmount = () => (
    <View style={styles.stepContent}>
      <Text style={styles.stepHeader}>How much are you sending?</Text>
      
      {selectedContact && (
        <View style={styles.selectedContactCard}>
          <View style={styles.contactAvatar}>
            <Text style={styles.contactInitials}>
              {selectedContact.name.charAt(0).toUpperCase()}
            </Text>
          </View>
          <View style={styles.contactInfo}>
            <Text style={styles.contactName}>{selectedContact.name}</Text>
            <Text style={styles.contactUsername}>@{selectedContact.username}</Text>
          </View>
        </View>
      )}

      <AmountInput
        value={amount}
        onChangeText={setAmount}
        maxAmount={user?.balance || 0}
        style={styles.amountInput}
      />

      <View style={styles.balanceInfo}>
        <Text style={styles.balanceLabel}>Available Balance</Text>
        <Text style={styles.balanceAmount}>
          {formatCurrency(user?.balance || 0)}
        </Text>
      </View>

      <View style={styles.noteContainer}>
        <Text style={styles.noteLabel}>Add a note (optional)</Text>
        <TextInput
          style={styles.noteInput}
          placeholder="What's this for?"
          value={note}
          onChangeText={setNote}
          multiline
          maxLength={200}
          placeholderTextColor={COLORS.gray}
        />
      </View>

      <TouchableOpacity
        style={[styles.continueButton, !amount && styles.continueButtonDisabled]}
        onPress={handleAmountSubmit}
        disabled={!amount}
      >
        <Text style={styles.continueButtonText}>Continue</Text>
      </TouchableOpacity>
    </View>
  );

  const renderReviewAndSend = () => (
    <View style={styles.stepContent}>
      <Text style={styles.stepHeader}>Review your transaction</Text>
      
      <View style={styles.reviewCard}>
        <View style={styles.reviewSection}>
          <Text style={styles.reviewLabel}>Sending to</Text>
          <View style={styles.reviewContactRow}>
            <View style={styles.contactAvatar}>
              <Text style={styles.contactInitials}>
                {selectedContact?.name.charAt(0).toUpperCase()}
              </Text>
            </View>
            <View>
              <Text style={styles.reviewContactName}>{selectedContact?.name}</Text>
              <Text style={styles.reviewContactUsername}>@{selectedContact?.username}</Text>
            </View>
          </View>
        </View>

        <View style={styles.reviewDivider} />

        <View style={styles.reviewSection}>
          <Text style={styles.reviewLabel}>Amount</Text>
          <Text style={styles.reviewAmount}>{formatCurrency(parseFloat(amount || '0'))}</Text>
        </View>

        {note && (
          <>
            <View style={styles.reviewDivider} />
            <View style={styles.reviewSection}>
              <Text style={styles.reviewLabel}>Note</Text>
              <Text style={styles.reviewNote}>{note}</Text>
            </View>
          </>
        )}

        <View style={styles.reviewDivider} />

        <View style={styles.reviewSection}>
          <Text style={styles.reviewLabel}>Fee</Text>
          <Text style={styles.reviewFee}>Free</Text>
        </View>

        <View style={styles.reviewDivider} />

        <View style={styles.reviewSection}>
          <Text style={styles.reviewLabel}>Total</Text>
          <Text style={styles.reviewTotal}>{formatCurrency(parseFloat(amount || '0'))}</Text>
        </View>
      </View>

      <PaymentMethodSelector
        selectedMethod={selectedPaymentMethod}
        onMethodSelect={setSelectedPaymentMethod}
        style={styles.paymentMethodSelector}
      />

      <TouchableOpacity
        style={[styles.sendButton, loading && styles.sendButtonDisabled]}
        onPress={handleSendMoney}
        disabled={loading}
      >
        <LinearGradient
          colors={[COLORS.primary, COLORS.secondary]}
          style={styles.sendButtonGradient}
        >
          {loading ? (
            <ActivityIndicator size="small" color={COLORS.white} />
          ) : (
            <Text style={styles.sendButtonText}>Send Money</Text>
          )}
        </LinearGradient>
      </TouchableOpacity>
    </View>
  );

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Ionicons name="arrow-back" size={24} color={COLORS.text} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Send Money</Text>
        <View style={styles.headerRight} />
      </View>

      {renderStepIndicator()}

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {currentStep === 1 && renderSelectRecipient()}
        {currentStep === 2 && renderEnterAmount()}
        {currentStep === 3 && renderReviewAndSend()}
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: SIZES.padding,
    paddingTop: SIZES.statusBarHeight,
    paddingBottom: SIZES.base,
    backgroundColor: COLORS.white,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.lightGray,
  },
  backButton: {
    padding: SIZES.base,
  },
  headerTitle: {
    fontSize: SIZES.h3,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
  },
  headerRight: {
    width: 40,
  },
  stepIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: SIZES.padding,
    backgroundColor: COLORS.white,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.lightGray,
  },
  stepContainer: {
    alignItems: 'center',
    position: 'relative',
  },
  stepCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: COLORS.lightGray,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: SIZES.base / 2,
  },
  stepCircleActive: {
    backgroundColor: COLORS.primary,
  },
  stepCircleCompleted: {
    backgroundColor: COLORS.success,
  },
  stepNumber: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.medium,
    color: COLORS.gray,
  },
  stepNumberActive: {
    color: COLORS.white,
  },
  stepTitle: {
    fontSize: SIZES.caption,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    textAlign: 'center',
  },
  stepTitleActive: {
    color: COLORS.text,
    fontFamily: FONTS.medium,
  },
  stepConnector: {
    position: 'absolute',
    top: 16,
    left: 40,
    width: 40,
    height: 2,
    backgroundColor: COLORS.lightGray,
  },
  stepConnectorActive: {
    backgroundColor: COLORS.success,
  },
  content: {
    flex: 1,
  },
  stepContent: {
    flex: 1,
    paddingHorizontal: SIZES.padding,
    paddingVertical: SIZES.padding,
  },
  stepHeader: {
    fontSize: SIZES.h3,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
    textAlign: 'center',
    marginBottom: SIZES.padding,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.lightGray,
    borderRadius: SIZES.radius,
    paddingHorizontal: SIZES.base,
    marginBottom: SIZES.padding,
  },
  searchIcon: {
    marginRight: SIZES.base,
  },
  searchInput: {
    flex: 1,
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.text,
    paddingVertical: SIZES.base,
  },
  contactsList: {
    flex: 1,
  },
  loader: {
    marginTop: SIZES.padding * 2,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: SIZES.padding * 2,
  },
  emptyStateText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginTop: SIZES.base,
  },
  addContactButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: SIZES.base,
    borderTopWidth: 1,
    borderTopColor: COLORS.lightGray,
    marginTop: SIZES.padding,
  },
  addContactText: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.medium,
    color: COLORS.primary,
    marginLeft: SIZES.base / 2,
  },
  selectedContactCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: COLORS.white,
    borderRadius: SIZES.radius,
    padding: SIZES.padding,
    marginBottom: SIZES.padding,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
  },
  contactAvatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: SIZES.base,
  },
  contactInitials: {
    fontSize: SIZES.h4,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
  },
  contactInfo: {
    flex: 1,
  },
  contactName: {
    fontSize: SIZES.body2,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
  },
  contactUsername: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
  },
  amountInput: {
    marginBottom: SIZES.padding,
  },
  balanceInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: SIZES.padding,
  },
  balanceLabel: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
  },
  balanceAmount: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
  },
  noteContainer: {
    marginBottom: SIZES.padding,
  },
  noteLabel: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.medium,
    color: COLORS.text,
    marginBottom: SIZES.base / 2,
  },
  noteInput: {
    backgroundColor: COLORS.white,
    borderRadius: SIZES.radius,
    padding: SIZES.base,
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.text,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
    minHeight: 80,
    textAlignVertical: 'top',
  },
  continueButton: {
    backgroundColor: COLORS.primary,
    borderRadius: SIZES.radius,
    paddingVertical: SIZES.base,
    alignItems: 'center',
    justifyContent: 'center',
  },
  continueButtonDisabled: {
    backgroundColor: COLORS.gray,
  },
  continueButtonText: {
    fontSize: SIZES.body2,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
  },
  reviewCard: {
    backgroundColor: COLORS.white,
    borderRadius: SIZES.radius,
    padding: SIZES.padding,
    marginBottom: SIZES.padding,
    borderWidth: 1,
    borderColor: COLORS.lightGray,
  },
  reviewSection: {
    paddingVertical: SIZES.base,
  },
  reviewLabel: {
    fontSize: SIZES.body4,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
    marginBottom: SIZES.base / 2,
  },
  reviewContactRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  reviewContactName: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.semiBold,
    color: COLORS.text,
  },
  reviewContactUsername: {
    fontSize: SIZES.caption,
    fontFamily: FONTS.regular,
    color: COLORS.gray,
  },
  reviewAmount: {
    fontSize: SIZES.h2,
    fontFamily: FONTS.bold,
    color: COLORS.text,
  },
  reviewNote: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.regular,
    color: COLORS.text,
  },
  reviewFee: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.medium,
    color: COLORS.success,
  },
  reviewTotal: {
    fontSize: SIZES.h3,
    fontFamily: FONTS.bold,
    color: COLORS.text,
  },
  reviewDivider: {
    height: 1,
    backgroundColor: COLORS.lightGray,
    marginVertical: SIZES.base,
  },
  paymentMethodSelector: {
    marginBottom: SIZES.padding,
  },
  sendButton: {
    borderRadius: SIZES.radius,
    overflow: 'hidden',
  },
  sendButtonDisabled: {
    opacity: 0.6,
  },
  sendButtonGradient: {
    paddingVertical: SIZES.base,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonText: {
    fontSize: SIZES.body2,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
  },
});

export default SendMoneyScreen;