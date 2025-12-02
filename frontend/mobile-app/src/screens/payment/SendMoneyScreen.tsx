import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  TouchableOpacity,
  Animated,
} from 'react-native';
import {
  Text,
  TextInput,
  Button,
  Avatar,
  Chip,
  Surface,
  useTheme,
  HelperText,
  IconButton,
  Portal,
  Modal,
  List,
  Divider,
  RadioButton,
} from 'react-native-paper';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useSelector } from 'react-redux';

import { RootState } from '../../store/store';
import Header from '../../components/common/Header';
import AmountInput from '../../components/payment/AmountInput';
import ContactSelector from '../../components/payment/ContactSelector';
import PaymentMethodSelector from '../../components/payment/PaymentMethodSelector';
import { formatCurrency } from '../../utils/formatters';
import { paymentService } from '../../services/paymentService';
import { usePayment } from '../../hooks/usePayment';
import { showToast } from '../../utils/toast';
import { PaymentVisibility } from '../../types/payment';

interface SendMoneyForm {
  recipientId: string;
  amount: string;
  description: string;
  paymentMethod: string;
  visibility: PaymentVisibility;
  emoji?: string;
  tags?: string[];
  instantTransfer: boolean;
}

const schema = yup.object({
  recipientId: yup.string().required('Please select a recipient'),
  amount: yup
    .string()
    .required('Please enter an amount')
    .test('positive', 'Amount must be greater than 0', (value) => {
      const num = parseFloat(value || '0');
      return num > 0;
    })
    .test('max', 'Amount exceeds your balance', function(value) {
      const num = parseFloat(value || '0');
      const balance = this.options.context?.balance || 0;
      return num <= balance;
    }),
  description: yup.string().max(200, 'Description is too long'),
  paymentMethod: yup.string().required('Please select a payment method'),
  visibility: yup.string().oneOf(['public', 'friends', 'private']).required(),
}).required();

type RouteParams = {
  SendMoney: {
    recipientId?: string;
  };
};

/**
 * Send Money Screen - Comprehensive payment sending interface
 */
const SendMoneyScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute<RouteProp<RouteParams, 'SendMoney'>>();
  const { sendPayment, loading: paymentLoading } = usePayment();
  
  const [step, setStep] = useState(1);
  const [selectedRecipient, setSelectedRecipient] = useState<any>(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [selectedEmoji, setSelectedEmoji] = useState('💸');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  
  const scrollViewRef = useRef<ScrollView>(null);
  const fadeAnim = useRef(new Animated.Value(1)).current;
  
  // Redux state
  const wallet = useSelector((state: RootState) => state.wallet);
  const user = useSelector((state: RootState) => state.auth.user);
  
  const {
    control,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<SendMoneyForm>({
    resolver: yupResolver(schema),
    context: { balance: wallet.balance },
    defaultValues: {
      recipientId: route.params?.recipientId || '',
      amount: '',
      description: '',
      paymentMethod: 'wallet',
      visibility: 'friends',
      instantTransfer: false,
    },
  });

  const watchedAmount = watch('amount');
  const watchedInstantTransfer = watch('instantTransfer');

  useEffect(() => {
    if (route.params?.recipientId) {
      // Load recipient details
      loadRecipient(route.params.recipientId);
    }
  }, [route.params?.recipientId]);

  const loadRecipient = async (recipientId: string) => {
    try {
      const recipient = await paymentService.getRecipientDetails(recipientId);
      setSelectedRecipient(recipient);
      setValue('recipientId', recipientId);
      setStep(2); // Move to amount step
    } catch (error) {
      showToast('Failed to load recipient details', 'error');
    }
  };

  const calculateFees = () => {
    const amount = parseFloat(watchedAmount || '0');
    if (amount === 0) return { fee: 0, total: 0 };
    
    const fee = watchedInstantTransfer ? amount * 0.015 : 0; // 1.5% for instant
    const total = amount + fee;
    
    return { fee, total };
  };

  const onSubmit = async (data: SendMoneyForm) => {
    try {
      const paymentData = {
        ...data,
        amount: parseFloat(data.amount),
        emoji: selectedEmoji,
        tags,
        senderId: user?.id,
      };
      
      const result = await sendPayment(paymentData);
      
      if (result.success) {
        navigation.navigate('PaymentSuccess', {
          paymentId: result.paymentId,
        } as never);
      }
    } catch (error) {
      showToast('Payment failed. Please try again.', 'error');
    }
  };

  const handleRecipientSelect = (recipient: any) => {
    setSelectedRecipient(recipient);
    setValue('recipientId', recipient.id);
    nextStep();
  };

  const nextStep = () => {
    Animated.sequence([
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 150,
        useNativeDriver: true,
      }),
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 150,
        useNativeDriver: true,
      }),
    ]).start();
    
    setStep(step + 1);
    scrollViewRef.current?.scrollTo({ x: 0, y: 0, animated: true });
  };

  const previousStep = () => {
    if (step > 1) {
      setStep(step - 1);
      scrollViewRef.current?.scrollTo({ x: 0, y: 0, animated: true });
    }
  };

  const addTag = () => {
    if (tagInput.trim() && tags.length < 5) {
      setTags([...tags, tagInput.trim()]);
      setTagInput('');
    }
  };

  const removeTag = (index: number) => {
    setTags(tags.filter((_, i) => i !== index));
  };

  const popularEmojis = ['💸', '🎉', '🍕', '☕', '🎬', '🛍️', '✈️', '🎂', '🏠', '💰'];

  const renderStep = () => {
    switch (step) {
      case 1:
        return (
          <Animated.View style={{ opacity: fadeAnim }}>
            <Text style={styles.stepTitle}>Who are you sending to?</Text>
            <ContactSelector
              onSelect={handleRecipientSelect}
              showRecent
              showQRScan
              showNearby
            />
          </Animated.View>
        );
        
      case 2:
        return (
          <Animated.View style={{ opacity: fadeAnim }}>
            <Text style={styles.stepTitle}>How much?</Text>
            
            {selectedRecipient && (
              <Surface style={styles.recipientCard}>
                <Avatar.Image
                  size={48}
                  source={{ uri: selectedRecipient.avatar }}
                  style={styles.recipientAvatar}
                />
                <View style={styles.recipientInfo}>
                  <Text style={styles.recipientName}>{selectedRecipient.name}</Text>
                  <Text style={styles.recipientHandle}>@{selectedRecipient.username}</Text>
                </View>
              </Surface>
            )}
            
            <Controller
              control={control}
              name="amount"
              render={({ field: { onChange, value } }) => (
                <AmountInput
                  value={value}
                  onChange={onChange}
                  currency={wallet.currency}
                  balance={wallet.balance}
                  error={errors.amount?.message}
                />
              )}
            />
            
            <View style={styles.instantTransferOption}>
              <Controller
                control={control}
                name="instantTransfer"
                render={({ field: { onChange, value } }) => (
                  <TouchableOpacity
                    style={styles.instantTransferToggle}
                    onPress={() => onChange(!value)}
                  >
                    <Icon
                      name={value ? 'lightning-bolt' : 'lightning-bolt-outline'}
                      size={24}
                      color={value ? theme.colors.primary : theme.colors.onSurfaceVariant}
                    />
                    <View style={styles.instantTransferText}>
                      <Text style={styles.instantTransferTitle}>
                        Instant Transfer
                      </Text>
                      <Text style={styles.instantTransferSubtitle}>
                        Arrives in seconds • 1.5% fee
                      </Text>
                    </View>
                    <RadioButton
                      value="instant"
                      status={value ? 'checked' : 'unchecked'}
                      onPress={() => onChange(!value)}
                    />
                  </TouchableOpacity>
                )}
              />
            </View>
            
            {watchedAmount && (
              <Surface style={styles.feeSummary}>
                <View style={styles.feeRow}>
                  <Text style={styles.feeLabel}>Amount</Text>
                  <Text style={styles.feeValue}>
                    {formatCurrency(parseFloat(watchedAmount))}
                  </Text>
                </View>
                {watchedInstantTransfer && (
                  <View style={styles.feeRow}>
                    <Text style={styles.feeLabel}>Instant fee</Text>
                    <Text style={styles.feeValue}>
                      {formatCurrency(calculateFees().fee)}
                    </Text>
                  </View>
                )}
                <Divider style={styles.feeDivider} />
                <View style={styles.feeRow}>
                  <Text style={styles.feeTotalLabel}>Total</Text>
                  <Text style={styles.feeTotalValue}>
                    {formatCurrency(calculateFees().total)}
                  </Text>
                </View>
              </Surface>
            )}
          </Animated.View>
        );
        
      case 3:
        return (
          <Animated.View style={{ opacity: fadeAnim }}>
            <Text style={styles.stepTitle}>Add details</Text>
            
            <Controller
              control={control}
              name="description"
              render={({ field: { onChange, value } }) => (
                <View style={styles.descriptionContainer}>
                  <TouchableOpacity
                    style={styles.emojiButton}
                    onPress={() => setShowEmojiPicker(true)}
                  >
                    <Text style={styles.selectedEmoji}>{selectedEmoji}</Text>
                  </TouchableOpacity>
                  <TextInput
                    mode="outlined"
                    label="What's this for?"
                    value={value}
                    onChangeText={onChange}
                    error={!!errors.description}
                    style={styles.descriptionInput}
                    multiline
                    numberOfLines={2}
                    maxLength={200}
                  />
                </View>
              )}
            />
            <HelperText type="error" visible={!!errors.description}>
              {errors.description?.message}
            </HelperText>
            
            <View style={styles.tagsSection}>
              <Text style={styles.tagsSectionTitle}>Tags (optional)</Text>
              <View style={styles.tagsContainer}>
                {tags.map((tag, index) => (
                  <Chip
                    key={index}
                    onClose={() => removeTag(index)}
                    style={styles.tag}
                  >
                    {tag}
                  </Chip>
                ))}
                {tags.length < 5 && (
                  <View style={styles.tagInputContainer}>
                    <TextInput
                      mode="outlined"
                      placeholder="Add tag"
                      value={tagInput}
                      onChangeText={setTagInput}
                      onSubmitEditing={addTag}
                      style={styles.tagInput}
                      dense
                    />
                  </View>
                )}
              </View>
            </View>
            
            <View style={styles.visibilitySection}>
              <Text style={styles.visibilitySectionTitle}>Who can see this?</Text>
              <Controller
                control={control}
                name="visibility"
                render={({ field: { onChange, value } }) => (
                  <RadioButton.Group onValueChange={onChange} value={value}>
                    <TouchableOpacity
                      style={styles.visibilityOption}
                      onPress={() => onChange('public')}
                    >
                      <Icon name="earth" size={24} color={theme.colors.onSurface} />
                      <View style={styles.visibilityTextContainer}>
                        <Text style={styles.visibilityTitle}>Public</Text>
                        <Text style={styles.visibilitySubtitle}>
                          Visible on the public feed
                        </Text>
                      </View>
                      <RadioButton value="public" />
                    </TouchableOpacity>
                    
                    <TouchableOpacity
                      style={styles.visibilityOption}
                      onPress={() => onChange('friends')}
                    >
                      <Icon name="account-group" size={24} color={theme.colors.onSurface} />
                      <View style={styles.visibilityTextContainer}>
                        <Text style={styles.visibilityTitle}>Friends</Text>
                        <Text style={styles.visibilitySubtitle}>
                          Only your friends can see
                        </Text>
                      </View>
                      <RadioButton value="friends" />
                    </TouchableOpacity>
                    
                    <TouchableOpacity
                      style={styles.visibilityOption}
                      onPress={() => onChange('private')}
                    >
                      <Icon name="lock" size={24} color={theme.colors.onSurface} />
                      <View style={styles.visibilityTextContainer}>
                        <Text style={styles.visibilityTitle}>Private</Text>
                        <Text style={styles.visibilitySubtitle}>
                          Only you and recipient can see
                        </Text>
                      </View>
                      <RadioButton value="private" />
                    </TouchableOpacity>
                  </RadioButton.Group>
                )}
              />
            </View>
            
            <Controller
              control={control}
              name="paymentMethod"
              render={({ field: { onChange, value } }) => (
                <PaymentMethodSelector
                  value={value}
                  onChange={onChange}
                  balance={wallet.balance}
                />
              )}
            />
          </Animated.View>
        );
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <Header
        title="Send Money"
        subtitle={`Step ${step} of 3`}
        onBack={step > 1 ? previousStep : undefined}
      />
      
      <ScrollView
        ref={scrollViewRef}
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {renderStep()}
      </ScrollView>
      
      <Surface style={styles.bottomActions}>
        <Button
          mode="contained"
          onPress={step < 3 ? nextStep : handleSubmit(onSubmit)}
          disabled={
            (step === 1 && !selectedRecipient) ||
            (step === 2 && (!watchedAmount || !!errors.amount)) ||
            paymentLoading
          }
          loading={paymentLoading}
          style={styles.continueButton}
        >
          {step < 3 ? 'Continue' : 'Send Money'}
        </Button>
      </Surface>
      
      {/* Emoji Picker Modal */}
      <Portal>
        <Modal
          visible={showEmojiPicker}
          onDismiss={() => setShowEmojiPicker(false)}
          contentContainerStyle={styles.emojiModal}
        >
          <Text style={styles.emojiModalTitle}>Choose an emoji</Text>
          <View style={styles.emojiGrid}>
            {popularEmojis.map((emoji) => (
              <TouchableOpacity
                key={emoji}
                style={styles.emojiOption}
                onPress={() => {
                  setSelectedEmoji(emoji);
                  setShowEmojiPicker(false);
                }}
              >
                <Text style={styles.emojiOptionText}>{emoji}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </Modal>
      </Portal>
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
  stepTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 24,
    color: '#333',
  },
  recipientCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    marginBottom: 24,
    elevation: 1,
  },
  recipientAvatar: {
    marginRight: 12,
  },
  recipientInfo: {
    flex: 1,
  },
  recipientName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  recipientHandle: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  instantTransferOption: {
    marginTop: 16,
  },
  instantTransferToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    backgroundColor: 'white',
    borderRadius: 12,
    elevation: 1,
  },
  instantTransferText: {
    flex: 1,
    marginLeft: 12,
  },
  instantTransferTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  instantTransferSubtitle: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  feeSummary: {
    marginTop: 24,
    padding: 16,
    borderRadius: 12,
    elevation: 1,
  },
  feeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  feeLabel: {
    fontSize: 14,
    color: '#666',
  },
  feeValue: {
    fontSize: 14,
    color: '#333',
  },
  feeDivider: {
    marginVertical: 8,
  },
  feeTotalLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  feeTotalValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  descriptionContainer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
  },
  emojiButton: {
    width: 56,
    height: 56,
    backgroundColor: 'white',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 1,
  },
  selectedEmoji: {
    fontSize: 32,
  },
  descriptionInput: {
    flex: 1,
  },
  tagsSection: {
    marginTop: 24,
  },
  tagsSectionTitle: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 12,
    color: '#333',
  },
  tagsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  tag: {
    backgroundColor: '#E3F2FD',
  },
  tagInputContainer: {
    width: 100,
  },
  tagInput: {
    height: 32,
  },
  visibilitySection: {
    marginTop: 24,
  },
  visibilitySectionTitle: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 12,
    color: '#333',
  },
  visibilityOption: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: 'white',
    borderRadius: 12,
    marginBottom: 8,
    elevation: 1,
  },
  visibilityTextContainer: {
    flex: 1,
    marginLeft: 12,
  },
  visibilityTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  visibilitySubtitle: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  bottomActions: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
    backgroundColor: 'white',
    elevation: 8,
  },
  continueButton: {
    borderRadius: 8,
  },
  emojiModal: {
    backgroundColor: 'white',
    padding: 20,
    margin: 20,
    borderRadius: 12,
  },
  emojiModalTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 16,
    textAlign: 'center',
  },
  emojiGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 12,
  },
  emojiOption: {
    width: 60,
    height: 60,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 12,
    backgroundColor: '#f5f5f5',
  },
  emojiOptionText: {
    fontSize: 32,
  },
});