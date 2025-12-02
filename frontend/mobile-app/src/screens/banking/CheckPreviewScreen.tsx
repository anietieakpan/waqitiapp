/**
 * CheckPreviewScreen - Preview and confirmation screen for captured check images
 * Shows captured images with editing options and submission flow
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  ScrollView,
  Dimensions,
  Platform,
  KeyboardAvoidingView,
  Image,
  ActivityIndicator,
} from 'react-native';
import {
  Surface,
  useTheme,
  TextInput,
  Button,
  Chip,
  Portal,
  Modal,
  HelperText,
  List,
} from 'react-native-paper';
import { useNavigation, useRoute } from '@react-navigation/native';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import FastImage from 'react-native-fast-image';
import { useSelector } from 'react-redux';

import { RootState } from '../../store/store';
import { useBiometric } from '../../hooks/useBiometric';
import { formatCurrency } from '../../utils/formatters';
import { showToast } from '../../utils/toast';
import CheckDepositService, { CheckDepositRequest } from '../../services/banking/CheckDepositService';
import DeviceInfo from 'react-native-device-info';

interface CheckDepositForm {
  amount: string;
  description: string;
  accountId: string;
}

interface CaptureSession {
  frontImage?: string;
  backImage?: string;
  currentSide: 'front' | 'back';
  sessionId: string;
}

interface RouteParams {
  CheckPreview: {
    imageUri: string;
    side: 'front' | 'back';
    session: CaptureSession;
  };
}

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

const schema = yup.object({
  amount: yup
    .string()
    .required('Please enter the check amount')
    .test('positive', 'Amount must be greater than 0', (value) => {
      const num = parseFloat(value || '0');
      return num > 0;
    })
    .test('max', 'Amount cannot exceed $10,000', (value) => {
      const num = parseFloat(value || '0');
      return num <= 10000;
    }),
  description: yup.string().max(200, 'Description is too long'),
  accountId: yup.string().required('Please select a deposit account'),
}).required();

/**
 * Check Preview Screen Component
 */
const CheckPreviewScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute<any>();
  const { canAuthenticate, authenticateWithFallback } = useBiometric();
  
  // Route params
  const { imageUri, side, session } = route.params || {};
  
  // State
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showImageModal, setShowImageModal] = useState(false);
  const [selectedImageUri, setSelectedImageUri] = useState<string>('');
  const [showAccountModal, setShowAccountModal] = useState(false);
  const [accounts] = useState([
    { id: '1', name: 'Primary Checking', balance: 2543.89, type: 'checking' },
    { id: '2', name: 'Savings Account', balance: 15234.56, type: 'savings' },
  ]);
  
  // Redux state
  const user = useSelector((state: RootState) => state.auth.user);
  const wallet = useSelector((state: RootState) => state.wallet);
  
  // Form setup
  const {
    control,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<CheckDepositForm>({
    resolver: yupResolver(schema),
    defaultValues: {
      amount: '',
      description: '',
      accountId: accounts[0]?.id || '',
    },
  });
  
  const watchedAccountId = watch('accountId');
  const watchedAmount = watch('amount');
  
  // Get selected account
  const selectedAccount = accounts.find(acc => acc.id === watchedAccountId);
  
  // Handle retake photo
  const handleRetake = useCallback(() => {
    navigation.navigate('CheckCapture', {
      resumeSession: {
        ...session,
        [side + 'Image']: undefined, // Clear the current side image
        currentSide: side,
      },
    } as never);
  }, [navigation, session, side]);
  
  // Handle capture other side
  const handleCaptureOtherSide = useCallback(() => {
    const otherSide = side === 'front' ? 'back' : 'front';
    navigation.navigate('CheckCapture', {
      resumeSession: {
        ...session,
        currentSide: otherSide,
      },
    } as never);
  }, [navigation, session, side]);
  
  // Show image in modal
  const showImageFullScreen = useCallback((uri: string) => {
    setSelectedImageUri(uri);
    setShowImageModal(true);
  }, []);
  
  // Handle account selection
  const handleAccountSelect = useCallback((accountId: string) => {
    setValue('accountId', accountId);
    setShowAccountModal(false);
  }, [setValue]);
  
  // Estimate processing time
  const getProcessingTime = useCallback(() => {
    const amount = parseFloat(watchedAmount || '0');
    if (amount <= 200) return '1-2 business days';
    if (amount <= 1000) return '2-3 business days';
    return '3-5 business days';
  }, [watchedAmount]);
  
  // Calculate fees
  const calculateFees = useCallback(() => {
    const amount = parseFloat(watchedAmount || '0');
    if (amount === 0) return { fee: 0, total: amount };
    
    // No fee for check deposits under $5000
    const fee = amount > 5000 ? 2.50 : 0;
    return { fee, total: amount - fee };
  }, [watchedAmount]);
  
  // Submit check deposit
  const onSubmit = useCallback(async (data: CheckDepositForm) => {
    if (!session.frontImage || !session.backImage) {
      Alert.alert(
        'Incomplete Capture',
        'Please capture both sides of the check before submitting.',
        [{ text: 'OK' }]
      );
      return;
    }
    
    try {
      setIsSubmitting(true);
      
      // Biometric authentication for deposits over $100
      const amount = parseFloat(data.amount);
      if (amount > 100 && canAuthenticate) {
        const authSuccess = await authenticateWithFallback(
          user?.id || '',
          'PIN'
        );
        
        if (!authSuccess) {
          showToast('Authentication required to complete deposit', 'error');
          return;
        }
      }
      
      // Prepare deposit data
      const depositData = {
        sessionId: session.sessionId,
        frontImage: session.frontImage,
        backImage: session.backImage,
        amount: parseFloat(data.amount),
        description: data.description,
        accountId: data.accountId,
        userId: user?.id,
        timestamp: new Date().toISOString(),
      };
      
      // Get device information
      const deviceId = await DeviceInfo.getUniqueId();
      
      // Prepare check deposit request
      const depositRequest: CheckDepositRequest = {
        frontImagePath: session.frontImage || '',
        backImagePath: session.backImage || '',
        amount: parseFloat(data.amount),
        depositAccountId: data.accountId,
        memo: data.description,
        deviceInfo: {
          deviceId,
          timestamp: new Date().toISOString(),
        },
      };
      
      console.log('Submitting check deposit via API...');
      
      // Submit check deposit via API
      const response = await CheckDepositService.submitCheckDeposit(depositRequest);
      
      console.log('Check deposit submitted successfully:', response.depositId);
      showToast('Check deposit submitted successfully!', 'success');
      
      // Navigate to status screen
      navigation.navigate('CheckDepositStatus', {
        depositId: response.depositId,
        amount: data.amount,
        account: selectedAccount,
        estimatedAvailability: getProcessingTime(),
      } as never);
      
    } catch (error) {
      console.error('Deposit submission error:', error);
      showToast('Failed to submit check deposit. Please try again.', 'error');
    } finally {
      setIsSubmitting(false);
    }
  }, [
    session,
    canAuthenticate,
    authenticateWithFallback,
    user?.id,
    selectedAccount,
    getProcessingTime,
    navigation,
  ]);
  
  // Check if ready to submit
  const canSubmit = session.frontImage && session.backImage && watchedAmount && selectedAccount;
  
  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Icon name="arrow-left" size={24} color={theme.colors.onSurface} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Review Check</Text>
        <View style={styles.headerRight} />
      </View>
      
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Check Images */}
        <Surface style={styles.imagesSection}>
          <Text style={styles.sectionTitle}>Check Images</Text>
          
          <View style={styles.imagesContainer}>
            {/* Front Image */}
            <View style={styles.imageContainer}>
              <Text style={styles.imageLabel}>Front</Text>
              {session.frontImage ? (
                <TouchableOpacity
                  onPress={() => showImageFullScreen(session.frontImage!)}
                >
                  <FastImage
                    source={{ uri: session.frontImage }}
                    style={styles.checkImage}
                    resizeMode={FastImage.resizeMode.cover}
                  />
                </TouchableOpacity>
              ) : (
                <TouchableOpacity
                  style={styles.placeholderImage}
                  onPress={side === 'front' ? handleRetake : handleCaptureOtherSide}
                >
                  <Icon name="camera" size={32} color={theme.colors.outline} />
                  <Text style={styles.placeholderText}>
                    {side === 'front' ? 'Retake' : 'Capture Front'}
                  </Text>
                </TouchableOpacity>
              )}
              {session.frontImage && (
                <TouchableOpacity
                  style={styles.retakeButton}
                  onPress={side === 'front' ? handleRetake : handleCaptureOtherSide}
                >
                  <Icon name="camera-retake" size={16} color={theme.colors.primary} />
                  <Text style={styles.retakeButtonText}>
                    {side === 'front' ? 'Retake' : 'Capture'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>
            
            {/* Back Image */}
            <View style={styles.imageContainer}>
              <Text style={styles.imageLabel}>Back</Text>
              {session.backImage ? (
                <TouchableOpacity
                  onPress={() => showImageFullScreen(session.backImage!)}
                >
                  <FastImage
                    source={{ uri: session.backImage }}
                    style={styles.checkImage}
                    resizeMode={FastImage.resizeMode.cover}
                  />
                </TouchableOpacity>
              ) : (
                <TouchableOpacity
                  style={styles.placeholderImage}
                  onPress={side === 'back' ? handleRetake : handleCaptureOtherSide}
                >
                  <Icon name="camera" size={32} color={theme.colors.outline} />
                  <Text style={styles.placeholderText}>
                    {side === 'back' ? 'Retake' : 'Capture Back'}
                  </Text>
                </TouchableOpacity>
              )}
              {session.backImage && (
                <TouchableOpacity
                  style={styles.retakeButton}
                  onPress={side === 'back' ? handleRetake : handleCaptureOtherSide}
                >
                  <Icon name="camera-retake" size={16} color={theme.colors.primary} />
                  <Text style={styles.retakeButtonText}>
                    {side === 'back' ? 'Retake' : 'Capture'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        </Surface>
        
        {/* Deposit Details */}
        <Surface style={styles.detailsSection}>
          <Text style={styles.sectionTitle}>Deposit Details</Text>
          
          {/* Amount Input */}
          <Controller
            control={control}
            name="amount"
            render={({ field: { onChange, value } }) => (
              <View style={styles.inputContainer}>
                <TextInput
                  mode="outlined"
                  label="Check Amount"
                  value={value}
                  onChangeText={onChange}
                  error={!!errors.amount}
                  keyboardType="numeric"
                  left={<TextInput.Icon icon="currency-usd" />}
                  placeholder="0.00"
                  style={styles.textInput}
                />
              </View>
            )}
          />
          <HelperText type="error" visible={!!errors.amount}>
            {errors.amount?.message}
          </HelperText>
          
          {/* Account Selection */}
          <TouchableOpacity
            style={styles.accountSelector}
            onPress={() => setShowAccountModal(true)}
          >
            <View style={styles.accountSelectorContent}>
              <Icon name="bank" size={24} color={theme.colors.primary} />
              <View style={styles.accountInfo}>
                <Text style={styles.accountName}>
                  {selectedAccount?.name || 'Select Account'}
                </Text>
                {selectedAccount && (
                  <Text style={styles.accountBalance}>
                    Balance: {formatCurrency(selectedAccount.balance)}
                  </Text>
                )}
              </View>
              <Icon name="chevron-right" size={24} color={theme.colors.outline} />
            </View>
          </TouchableOpacity>
          
          {/* Description */}
          <Controller
            control={control}
            name="description"
            render={({ field: { onChange, value } }) => (
              <TextInput
                mode="outlined"
                label="Description (Optional)"
                value={value}
                onChangeText={onChange}
                multiline
                numberOfLines={2}
                maxLength={200}
                style={styles.textInput}
              />
            )}
          />
        </Surface>
        
        {/* Processing Info */}
        {watchedAmount && (
          <Surface style={styles.processingSection}>
            <Text style={styles.sectionTitle}>Processing Information</Text>
            
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Estimated availability:</Text>
              <Text style={styles.infoValue}>{getProcessingTime()}</Text>
            </View>
            
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Processing fee:</Text>
              <Text style={styles.infoValue}>
                {formatCurrency(calculateFees().fee)}
              </Text>
            </View>
            
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Amount to be deposited:</Text>
              <Text style={[styles.infoValue, styles.totalAmount]}>
                {formatCurrency(calculateFees().total)}
              </Text>
            </View>
            
            <View style={styles.noteContainer}>
              <Icon name="information" size={16} color={theme.colors.primary} />
              <Text style={styles.noteText}>
                Funds may be held for verification. Mobile deposits are subject to daily limits.
              </Text>
            </View>
          </Surface>
        )}
      </ScrollView>
      
      {/* Submit Button */}
      <Surface style={styles.submitSection}>
        <Button
          mode="contained"
          onPress={handleSubmit(onSubmit)}
          disabled={!canSubmit || isSubmitting}
          loading={isSubmitting}
          style={styles.submitButton}
        >
          {isSubmitting ? 'Processing...' : 'Submit Deposit'}
        </Button>
      </Surface>
      
      {/* Image Modal */}
      <Portal>
        <Modal
          visible={showImageModal}
          onDismiss={() => setShowImageModal(false)}
          contentContainerStyle={styles.imageModal}
        >
          <TouchableOpacity
            style={styles.imageModalClose}
            onPress={() => setShowImageModal(false)}
          >
            <Icon name="close" size={24} color="white" />
          </TouchableOpacity>
          {selectedImageUri && (
            <Image
              source={{ uri: selectedImageUri }}
              style={styles.fullScreenImage}
              resizeMode="contain"
            />
          )}
        </Modal>
      </Portal>
      
      {/* Account Selection Modal */}
      <Portal>
        <Modal
          visible={showAccountModal}
          onDismiss={() => setShowAccountModal(false)}
          contentContainerStyle={styles.accountModal}
        >
          <Text style={styles.modalTitle}>Select Deposit Account</Text>
          {accounts.map((account) => (
            <List.Item
              key={account.id}
              title={account.name}
              description={`${account.type} • ${formatCurrency(account.balance)}`}
              left={(props) => <List.Icon {...props} icon="bank" />}
              right={(props) => 
                watchedAccountId === account.id ? (
                  <List.Icon {...props} icon="check" />
                ) : null
              }
              onPress={() => handleAccountSelect(account.id)}
              style={[
                styles.accountItem,
                watchedAccountId === account.id && styles.selectedAccountItem
              ]}
            />
          ))}
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
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'white',
    elevation: 2,
  },
  backButton: {
    padding: 8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  headerRight: {
    width: 40,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 100,
  },
  imagesSection: {
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 1,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 16,
    color: '#333',
  },
  imagesContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  imageContainer: {
    flex: 1,
  },
  imageLabel: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 8,
    textAlign: 'center',
    color: '#666',
  },
  checkImage: {
    width: '100%',
    height: 120,
    borderRadius: 8,
    backgroundColor: '#f0f0f0',
  },
  placeholderImage: {
    width: '100%',
    height: 120,
    borderRadius: 8,
    backgroundColor: '#f0f0f0',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#ddd',
    borderStyle: 'dashed',
  },
  placeholderText: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  retakeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
    padding: 8,
  },
  retakeButtonText: {
    fontSize: 12,
    color: '#007AFF',
    marginLeft: 4,
  },
  detailsSection: {
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 1,
  },
  inputContainer: {
    marginBottom: 8,
  },
  textInput: {
    backgroundColor: 'white',
  },
  accountSelector: {
    marginVertical: 8,
  },
  accountSelectorContent: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    backgroundColor: 'white',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  accountInfo: {
    flex: 1,
    marginLeft: 12,
  },
  accountName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
  },
  accountBalance: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  processingSection: {
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 1,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  infoLabel: {
    fontSize: 14,
    color: '#666',
  },
  infoValue: {
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  totalAmount: {
    fontSize: 16,
    fontWeight: '600',
    color: '#007AFF',
  },
  noteContainer: {
    flexDirection: 'row',
    marginTop: 12,
    padding: 12,
    backgroundColor: '#f0f8ff',
    borderRadius: 8,
  },
  noteText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 8,
    flex: 1,
    lineHeight: 18,
  },
  submitSection: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
    backgroundColor: 'white',
    elevation: 8,
  },
  submitButton: {
    borderRadius: 8,
    paddingVertical: 4,
  },
  imageModal: {
    flex: 1,
    backgroundColor: 'black',
    justifyContent: 'center',
    alignItems: 'center',
  },
  imageModalClose: {
    position: 'absolute',
    top: 50,
    right: 20,
    zIndex: 10,
    padding: 8,
  },
  fullScreenImage: {
    width: screenWidth,
    height: screenHeight,
  },
  accountModal: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    padding: 16,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 16,
    textAlign: 'center',
  },
  accountItem: {
    borderRadius: 8,
    marginVertical: 2,
  },
  selectedAccountItem: {
    backgroundColor: '#e3f2fd',
  },
});

export default CheckPreviewScreen;