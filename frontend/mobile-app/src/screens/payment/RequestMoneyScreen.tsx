import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  TouchableOpacity,
  Share,
  Alert,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  Surface,
  Avatar,
  Chip,
  Divider,
  HelperText,
  IconButton,
  List,
  Portal,
  Modal,
  Checkbox,
  FAB,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { useDispatch, useSelector } from 'react-redux';
import { Formik } from 'formik';
import * as Yup from 'yup';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useMutation, useQuery } from 'react-query';
import QRCode from 'react-native-qrcode-svg';
import Clipboard from '@react-native-clipboard/clipboard';
import Animated, { FadeInDown } from 'react-native-reanimated';

import { RootState, AppDispatch } from '../../store/store';
import { paymentService } from '../../services/paymentService';
import { contactService } from '../../services/contactService';
import { formatCurrency } from '../../utils/formatters';
import Header from '../../components/common/Header';
import AmountInput from '../../components/payment/AmountInput';
import ContactSelector from '../../components/payment/ContactSelector';
import { Contact, PaymentRequest } from '../../types/payment';
import { showToast } from '../../utils/toast';

interface RequestMoneyFormValues {
  amount: string;
  note: string;
  recipients: Contact[];
  splitEvenly: boolean;
  requestType: 'single' | 'group' | 'link';
  expirationDays: number;
}

const validationSchema = Yup.object().shape({
  amount: Yup.string()
    .required('Amount is required')
    .test('valid-amount', 'Invalid amount', (value) => {
      if (!value) return false;
      const num = parseFloat(value);
      return !isNaN(num) && num > 0 && num <= 10000;
    }),
  note: Yup.string()
    .required('Please add a note')
    .max(500, 'Note is too long'),
  recipients: Yup.array()
    .when('requestType', {
      is: (type: string) => type === 'single' || type === 'group',
      then: Yup.array().min(1, 'Please select at least one recipient'),
    }),
  expirationDays: Yup.number().min(1).max(30),
});

/**
 * Request Money Screen - Comprehensive payment request interface
 */
const RequestMoneyScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const dispatch = useDispatch<AppDispatch>();
  
  const [showContactModal, setShowContactModal] = useState(false);
  const [showQRModal, setShowQRModal] = useState(false);
  const [generatedLink, setGeneratedLink] = useState('');
  const [qrValue, setQrValue] = useState('');
  
  // Redux state
  const { user } = useSelector((state: RootState) => state.auth);
  const { currency } = useSelector((state: RootState) => state.wallet);
  
  // Fetch recent contacts
  const { data: recentContacts } = useQuery(
    'recentContacts',
    contactService.getRecentContacts
  );
  
  // Create payment request mutation
  const createRequestMutation = useMutation(
    (request: PaymentRequest) => paymentService.createPaymentRequest(request),
    {
      onSuccess: (response) => {
        if (response.type === 'link') {
          setGeneratedLink(response.paymentLink);
          setQrValue(response.qrCode);
          showToast('Payment link created!', 'success');
        } else {
          navigation.navigate('RequestSuccess', {
            requestId: response.id,
            recipients: response.recipients,
          } as never);
        }
      },
      onError: (error: any) => {
        Alert.alert(
          'Request Failed',
          error.response?.data?.message || 'Failed to create payment request.',
          [{ text: 'OK' }]
        );
      },
    }
  );
  
  const handleSubmit = async (values: RequestMoneyFormValues) => {
    const request: PaymentRequest = {
      amount: parseFloat(values.amount),
      currency: currency,
      note: values.note,
      type: values.requestType,
      recipients: values.recipients.map(r => r.id),
      splitEvenly: values.splitEvenly,
      expirationDate: new Date(
        Date.now() + values.expirationDays * 24 * 60 * 60 * 1000
      ).toISOString(),
      requesterId: user?.id,
    };
    
    await createRequestMutation.mutateAsync(request);
  };
  
  const handleAddRecipient = (contact: Contact, values: any, setFieldValue: any) => {
    if (!values.recipients.find((r: Contact) => r.id === contact.id)) {
      setFieldValue('recipients', [...values.recipients, contact]);
    }
    setShowContactModal(false);
  };
  
  const handleRemoveRecipient = (contactId: string, values: any, setFieldValue: any) => {
    setFieldValue(
      'recipients',
      values.recipients.filter((r: Contact) => r.id !== contactId)
    );
  };
  
  const handleShareLink = async () => {
    if (generatedLink) {
      try {
        await Share.share({
          message: `You can pay me using this link: ${generatedLink}`,
          title: 'Payment Request',
        });
      } catch (error) {
        console.error('Error sharing:', error);
      }
    }
  };
  
  const handleCopyLink = () => {
    if (generatedLink) {
      Clipboard.setString(generatedLink);
      showToast('Link copied to clipboard!', 'success');
    }
  };
  
  const calculateSplitAmount = (totalAmount: number, recipientCount: number) => {
    if (recipientCount === 0) return 0;
    return totalAmount / (recipientCount + 1); // +1 for the requester
  };
  
  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <Header
        title="Request Money"
        subtitle="Ask for payments from friends"
        onBack={() => navigation.goBack()}
      />
      
      <Formik
        initialValues={{
          amount: '',
          note: '',
          recipients: [],
          splitEvenly: false,
          requestType: 'single' as const,
          expirationDays: 7,
        }}
        validationSchema={validationSchema}
        onSubmit={handleSubmit}
      >
        {({
          values,
          errors,
          touched,
          handleChange,
          handleBlur,
          handleSubmit,
          setFieldValue,
          isValid,
        }) => (
          <>
            <ScrollView
              style={styles.scrollView}
              showsVerticalScrollIndicator={false}
              keyboardShouldPersistTaps="handled"
            >
              {/* Request Type Selection */}
              <Animated.View
                entering={FadeInDown.delay(100)}
                style={styles.section}
              >
                <Text style={styles.sectionTitle}>Request Type</Text>
                <View style={styles.requestTypeContainer}>
                  <Chip
                    mode={values.requestType === 'single' ? 'flat' : 'outlined'}
                    selected={values.requestType === 'single'}
                    onPress={() => setFieldValue('requestType', 'single')}
                    style={styles.requestTypeChip}
                    icon="account"
                  >
                    Single
                  </Chip>
                  <Chip
                    mode={values.requestType === 'group' ? 'flat' : 'outlined'}
                    selected={values.requestType === 'group'}
                    onPress={() => setFieldValue('requestType', 'group')}
                    style={styles.requestTypeChip}
                    icon="account-group"
                  >
                    Group
                  </Chip>
                  <Chip
                    mode={values.requestType === 'link' ? 'flat' : 'outlined'}
                    selected={values.requestType === 'link'}
                    onPress={() => setFieldValue('requestType', 'link')}
                    style={styles.requestTypeChip}
                    icon="link"
                  >
                    Link
                  </Chip>
                </View>
              </Animated.View>
              
              {/* Recipients (for single and group requests) */}
              {values.requestType !== 'link' && (
                <Animated.View
                  entering={FadeInDown.delay(200)}
                  style={styles.section}
                >
                  <View style={styles.sectionHeader}>
                    <Text style={styles.sectionTitle}>From</Text>
                    <IconButton
                      icon="plus"
                      size={20}
                      onPress={() => setShowContactModal(true)}
                    />
                  </View>
                  
                  {values.recipients.length === 0 ? (
                    <TouchableOpacity
                      style={styles.addRecipientButton}
                      onPress={() => setShowContactModal(true)}
                    >
                      <Icon name="account-plus" size={24} color={theme.colors.primary} />
                      <Text style={styles.addRecipientText}>Add recipients</Text>
                    </TouchableOpacity>
                  ) : (
                    <Surface style={styles.recipientsList}>
                      {values.recipients.map((recipient) => (
                        <View key={recipient.id} style={styles.recipientItem}>
                          <Avatar.Image
                            size={40}
                            source={{ uri: recipient.avatar }}
                            style={styles.recipientAvatar}
                          />
                          <View style={styles.recipientInfo}>
                            <Text style={styles.recipientName}>
                              {recipient.displayName}
                            </Text>
                            <Text style={styles.recipientUsername}>
                              @{recipient.username}
                            </Text>
                          </View>
                          {values.splitEvenly && values.recipients.length > 0 && (
                            <Text style={styles.splitAmount}>
                              {formatCurrency(
                                calculateSplitAmount(
                                  parseFloat(values.amount || '0'),
                                  values.recipients.length
                                ),
                                currency
                              )}
                            </Text>
                          )}
                          <IconButton
                            icon="close"
                            size={20}
                            onPress={() => handleRemoveRecipient(recipient.id, values, setFieldValue)}
                          />
                        </View>
                      ))}
                    </Surface>
                  )}
                  
                  {touched.recipients && errors.recipients && (
                    <HelperText type="error" visible>
                      {errors.recipients}
                    </HelperText>
                  )}
                  
                  {/* Split evenly option for group requests */}
                  {values.requestType === 'group' && values.recipients.length > 0 && (
                    <TouchableOpacity
                      style={styles.splitEvenlyOption}
                      onPress={() => setFieldValue('splitEvenly', !values.splitEvenly)}
                    >
                      <Checkbox
                        status={values.splitEvenly ? 'checked' : 'unchecked'}
                        onPress={() => setFieldValue('splitEvenly', !values.splitEvenly)}
                      />
                      <View style={styles.splitEvenlyText}>
                        <Text style={styles.splitEvenlyTitle}>Split evenly</Text>
                        <Text style={styles.splitEvenlySubtitle}>
                          Divide the total amount equally among everyone
                        </Text>
                      </View>
                    </TouchableOpacity>
                  )}
                </Animated.View>
              )}
              
              {/* Amount Input */}
              <Animated.View
                entering={FadeInDown.delay(300)}
                style={styles.section}
              >
                <Text style={styles.sectionTitle}>
                  {values.splitEvenly ? 'Total Amount' : 'Amount'}
                </Text>
                <AmountInput
                  value={values.amount}
                  onChangeText={handleChange('amount')}
                  onBlur={handleBlur('amount')}
                  currency={currency}
                  error={touched.amount && errors.amount}
                  helperText={errors.amount}
                />
                
                {values.splitEvenly && values.recipients.length > 0 && values.amount && (
                  <Surface style={styles.splitBreakdown}>
                    <Text style={styles.splitBreakdownTitle}>Split breakdown</Text>
                    <Divider style={styles.splitDivider} />
                    <View style={styles.splitRow}>
                      <Text style={styles.splitLabel}>Per person</Text>
                      <Text style={styles.splitValue}>
                        {formatCurrency(
                          calculateSplitAmount(
                            parseFloat(values.amount),
                            values.recipients.length
                          ),
                          currency
                        )}
                      </Text>
                    </View>
                    <View style={styles.splitRow}>
                      <Text style={styles.splitLabel}>Number of people</Text>
                      <Text style={styles.splitValue}>
                        {values.recipients.length + 1} (including you)
                      </Text>
                    </View>
                  </Surface>
                )}
              </Animated.View>
              
              {/* Note */}
              <Animated.View
                entering={FadeInDown.delay(400)}
                style={styles.section}
              >
                <Text style={styles.sectionTitle}>What's it for?</Text>
                <TextInput
                  mode="outlined"
                  placeholder="e.g., Dinner last night, Concert tickets"
                  value={values.note}
                  onChangeText={handleChange('note')}
                  onBlur={handleBlur('note')}
                  error={touched.note && !!errors.note}
                  multiline
                  numberOfLines={3}
                  maxLength={500}
                  style={styles.noteInput}
                  right={
                    <TextInput.Affix
                      text={`${values.note.length}/500`}
                      textStyle={styles.charCount}
                    />
                  }
                />
                {touched.note && errors.note && (
                  <HelperText type="error" visible>
                    {errors.note}
                  </HelperText>
                )}
              </Animated.View>
              
              {/* Expiration */}
              <Animated.View
                entering={FadeInDown.delay(500)}
                style={styles.section}
              >
                <Text style={styles.sectionTitle}>Request expires in</Text>
                <View style={styles.expirationOptions}>
                  {[3, 7, 14, 30].map((days) => (
                    <Chip
                      key={days}
                      mode={values.expirationDays === days ? 'flat' : 'outlined'}
                      selected={values.expirationDays === days}
                      onPress={() => setFieldValue('expirationDays', days)}
                      style={styles.expirationChip}
                    >
                      {days} days
                    </Chip>
                  ))}
                </View>
              </Animated.View>
              
              {/* Generated Link (for link requests) */}
              {values.requestType === 'link' && generatedLink && (
                <Animated.View
                  entering={FadeInDown.delay(600)}
                  style={styles.section}
                >
                  <Surface style={styles.linkContainer}>
                    <Text style={styles.linkTitle}>Payment Link Created!</Text>
                    <Text style={styles.linkUrl} numberOfLines={2}>
                      {generatedLink}
                    </Text>
                    <View style={styles.linkActions}>
                      <Button
                        mode="outlined"
                        onPress={handleCopyLink}
                        icon="content-copy"
                        style={styles.linkButton}
                      >
                        Copy
                      </Button>
                      <Button
                        mode="outlined"
                        onPress={handleShareLink}
                        icon="share"
                        style={styles.linkButton}
                      >
                        Share
                      </Button>
                      <Button
                        mode="outlined"
                        onPress={() => setShowQRModal(true)}
                        icon="qrcode"
                        style={styles.linkButton}
                      >
                        QR Code
                      </Button>
                    </View>
                  </Surface>
                </Animated.View>
              )}
              
              <View style={styles.bottomSpacer} />
            </ScrollView>
            
            {/* Submit Button */}
            {(!generatedLink || values.requestType !== 'link') && (
              <Surface style={styles.bottomBar} elevation={4}>
                <Button
                  mode="contained"
                  onPress={handleSubmit}
                  disabled={!isValid || createRequestMutation.isLoading}
                  loading={createRequestMutation.isLoading}
                  style={styles.requestButton}
                  contentStyle={styles.requestButtonContent}
                  icon="send"
                >
                  {values.requestType === 'link' ? 'Generate Link' : 'Send Request'}
                </Button>
              </Surface>
            )}
            
            {/* Contact Selection Modal */}
            <Portal>
              <Modal
                visible={showContactModal}
                onDismiss={() => setShowContactModal(false)}
                contentContainerStyle={styles.modalContent}
              >
                <ContactSelector
                  onSelectContact={(contact) => handleAddRecipient(contact, values, setFieldValue)}
                  onClose={() => setShowContactModal(false)}
                  recentContacts={recentContacts || []}
                  selectedContacts={values.recipients}
                  multiSelect={values.requestType === 'group'}
                />
              </Modal>
            </Portal>
            
            {/* QR Code Modal */}
            <Portal>
              <Modal
                visible={showQRModal}
                onDismiss={() => setShowQRModal(false)}
                contentContainerStyle={styles.qrModal}
              >
                <Text style={styles.qrTitle}>Scan to Pay</Text>
                <View style={styles.qrContainer}>
                  <QRCode
                    value={qrValue}
                    size={250}
                    color={theme.colors.primary}
                    backgroundColor="white"
                  />
                </View>
                <Text style={styles.qrAmount}>
                  {formatCurrency(parseFloat(values.amount || '0'), currency)}
                </Text>
                <Text style={styles.qrNote}>{values.note}</Text>
                <Button
                  mode="contained"
                  onPress={() => setShowQRModal(false)}
                  style={styles.qrCloseButton}
                >
                  Close
                </Button>
              </Modal>
            </Portal>
          </>
        )}
      </Formik>
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
  section: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginBottom: 8,
  },
  requestTypeContainer: {
    flexDirection: 'row',
    gap: 8,
  },
  requestTypeChip: {
    flex: 1,
  },
  addRecipientButton: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
    borderColor: '#e0e0e0',
    borderStyle: 'dashed',
  },
  addRecipientText: {
    fontSize: 16,
    color: '#666',
  },
  recipientsList: {
    backgroundColor: 'white',
    borderRadius: 12,
    overflow: 'hidden',
  },
  recipientItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  recipientAvatar: {
    marginRight: 12,
  },
  recipientInfo: {
    flex: 1,
  },
  recipientName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  recipientUsername: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  splitAmount: {
    fontSize: 14,
    fontWeight: '600',
    color: '#2196F3',
    marginRight: 8,
  },
  splitEvenlyOption: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
    backgroundColor: 'white',
    padding: 12,
    borderRadius: 12,
  },
  splitEvenlyText: {
    flex: 1,
  },
  splitEvenlyTitle: {
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  splitEvenlySubtitle: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  splitBreakdown: {
    marginTop: 12,
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#E3F2FD',
  },
  splitBreakdownTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1976D2',
    marginBottom: 8,
  },
  splitDivider: {
    marginBottom: 8,
    backgroundColor: '#90CAF9',
  },
  splitRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  splitLabel: {
    fontSize: 14,
    color: '#1976D2',
  },
  splitValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1976D2',
  },
  noteInput: {
    backgroundColor: 'white',
  },
  charCount: {
    fontSize: 12,
    color: '#666',
  },
  expirationOptions: {
    flexDirection: 'row',
    gap: 8,
  },
  expirationChip: {
    flex: 1,
  },
  linkContainer: {
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  linkTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#4CAF50',
    marginBottom: 12,
  },
  linkUrl: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 16,
    paddingHorizontal: 16,
  },
  linkActions: {
    flexDirection: 'row',
    gap: 8,
  },
  linkButton: {
    flex: 1,
  },
  bottomSpacer: {
    height: 100,
  },
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
    backgroundColor: 'white',
  },
  requestButton: {
    borderRadius: 12,
  },
  requestButtonContent: {
    paddingVertical: 8,
  },
  modalContent: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    maxHeight: '80%',
  },
  qrModal: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
  },
  qrTitle: {
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 20,
  },
  qrContainer: {
    padding: 20,
    backgroundColor: 'white',
    borderRadius: 12,
    elevation: 2,
  },
  qrAmount: {
    fontSize: 24,
    fontWeight: 'bold',
    marginTop: 20,
    color: '#333',
  },
  qrNote: {
    fontSize: 16,
    color: '#666',
    marginTop: 8,
    textAlign: 'center',
    paddingHorizontal: 20,
  },
  qrCloseButton: {
    marginTop: 24,
    minWidth: 120,
  },
});

export default RequestMoneyScreen;