import React, { useEffect, useState } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Animated,
  Dimensions,
  Alert,
  ActionSheetIOS,
  Platform,
} from 'react-native';
import {
  Text,
  Button,
  useTheme,
  Surface,
  Avatar,
  IconButton,
  Menu,
  MenuItem,
  ActivityIndicator,
  Snackbar,
} from 'react-native-paper';
import { useNavigation, useRoute } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import LottieView from 'lottie-react-native';
import { formatCurrency } from '../../utils/formatters';
import { formatDate } from '../../utils/dateUtils';
import HapticService from '../../services/HapticService';
import TransactionService, { ReceiptGenerationOptions, ShareOptions } from '../../services/TransactionService';

const { width: screenWidth } = Dimensions.get('window');

interface PaymentSuccessData {
  paymentId: string;
  paymentData: {
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
    total?: number;
  };
}

/**
 * Payment Success Screen - Success confirmation and next actions
 */
const PaymentSuccessScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute();
  const { paymentId, paymentData } = route.params as PaymentSuccessData;
  
  const [animationScale] = useState(new Animated.Value(0));
  const [confettiVisible, setConfettiVisible] = useState(true);
  const [receiptMenuVisible, setReceiptMenuVisible] = useState(false);
  const [isGeneratingReceipt, setIsGeneratingReceipt] = useState(false);
  const [snackbarVisible, setSnackbarVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [hasReceipt, setHasReceipt] = useState(false);

  useEffect(() => {
    // Success haptic feedback
    HapticService.success();
    
    // Animation
    Animated.spring(animationScale, {
      toValue: 1,
      tension: 50,
      friction: 7,
      useNativeDriver: true,
    }).start();

    // Hide confetti after animation
    const timer = setTimeout(() => {
      setConfettiVisible(false);
    }, 3000);

    // Check if receipt already exists
    checkReceiptAvailability();

    return () => clearTimeout(timer);
  }, []);

  const checkReceiptAvailability = async () => {
    try {
      const receiptExists = await TransactionService.hasReceipt(paymentId);
      setHasReceipt(receiptExists);
    } catch (error) {
      console.error('Error checking receipt availability:', error);
    }
  };

  const handleDone = () => {
    navigation.navigate('Dashboard' as never);
  };

  const handleShareReceipt = () => {
    if (Platform.OS === 'ios') {
      showIOSReceiptOptions();
    } else {
      setReceiptMenuVisible(true);
    }
  };

  const showIOSReceiptOptions = () => {
    const options = [
      'Download Standard Receipt',
      'Download Detailed Receipt',
      'Generate Proof of Payment',
      'Share via Email',
      'Save to Files',
      'Cancel'
    ];

    ActionSheetIOS.showActionSheetWithOptions(
      {
        options,
        cancelButtonIndex: options.length - 1,
        title: 'Receipt Options',
        message: 'Choose how you would like to receive your receipt'
      },
      (buttonIndex) => {
        switch (buttonIndex) {
          case 0:
            downloadReceipt('STANDARD');
            break;
          case 1:
            downloadReceipt('DETAILED');
            break;
          case 2:
            downloadProofOfPayment();
            break;
          case 3:
            shareViaEmail();
            break;
          case 4:
            saveToDevice();
            break;
        }
      }
    );
  };

  const downloadReceipt = async (format: ReceiptGenerationOptions['format'] = 'STANDARD') => {
    try {
      setIsGeneratingReceipt(true);
      setReceiptMenuVisible(false);

      const options: ReceiptGenerationOptions = {
        format,
        includeDetailedFees: format === 'DETAILED',
        includeTimeline: format === 'DETAILED',
        includeQrCode: true,
        includeWatermark: true,
      };

      await TransactionService.shareReceipt(paymentId, { saveToDevice: true }, options);
      
      setSnackbarMessage('Receipt downloaded successfully!');
      setSnackbarVisible(true);
      setHasReceipt(true);
      
      HapticService.success();
    } catch (error) {
      console.error('Error downloading receipt:', error);
      Alert.alert('Error', 'Failed to download receipt. Please try again.');
      HapticService.error();
    } finally {
      setIsGeneratingReceipt(false);
    }
  };

  const downloadProofOfPayment = async () => {
    try {
      setIsGeneratingReceipt(true);
      setReceiptMenuVisible(false);

      const proofBlob = await TransactionService.generateProofOfPayment(paymentId);
      
      // Save as file and share
      const shareOptions: ShareOptions = { saveToDevice: true };
      const receiptOptions: ReceiptGenerationOptions = { format: 'PROOF_OF_PAYMENT' };
      
      await TransactionService.shareReceipt(paymentId, shareOptions, receiptOptions);
      
      setSnackbarMessage('Proof of payment generated successfully!');
      setSnackbarVisible(true);
      setHasReceipt(true);
      
      HapticService.success();
    } catch (error) {
      console.error('Error generating proof of payment:', error);
      Alert.alert('Error', 'Failed to generate proof of payment. Please try again.');
      HapticService.error();
    } finally {
      setIsGeneratingReceipt(false);
    }
  };

  const shareViaEmail = () => {
    Alert.prompt(
      'Email Receipt',
      'Enter email address to send the receipt:',
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Send',
          onPress: async (email) => {
            if (email && isValidEmail(email)) {
              await sendEmailReceipt(email);
            } else {
              Alert.alert('Invalid Email', 'Please enter a valid email address.');
            }
          },
        },
      ],
      'plain-text',
      paymentData.recipient.email || ''
    );
  };

  const sendEmailReceipt = async (email: string) => {
    try {
      setIsGeneratingReceipt(true);
      setReceiptMenuVisible(false);

      const success = await TransactionService.emailReceipt(paymentId, email);
      
      if (success) {
        setSnackbarMessage(`Receipt sent to ${email}!`);
        setSnackbarVisible(true);
        HapticService.success();
      } else {
        throw new Error('Email sending failed');
      }
    } catch (error) {
      console.error('Error sending email receipt:', error);
      Alert.alert('Error', 'Failed to send receipt via email. Please try again.');
      HapticService.error();
    } finally {
      setIsGeneratingReceipt(false);
    }
  };

  const saveToDevice = async () => {
    try {
      setIsGeneratingReceipt(true);
      setReceiptMenuVisible(false);

      const shareOptions: ShareOptions = { saveToDevice: true };
      await TransactionService.shareReceipt(paymentId, shareOptions);
      
      setSnackbarMessage('Receipt saved to device!');
      setSnackbarVisible(true);
      setHasReceipt(true);
      
      HapticService.success();
    } catch (error) {
      console.error('Error saving receipt:', error);
      Alert.alert('Error', 'Failed to save receipt to device. Please try again.');
      HapticService.error();
    } finally {
      setIsGeneratingReceipt(false);
    }
  };

  const isValidEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const handleViewTransaction = () => {
    navigation.navigate('TransactionDetails', {
      transactionId: paymentId,
    } as never);
  };

  const handleSendAnother = () => {
    navigation.navigate('SendMoney', {
      recipientId: paymentData.recipient.id,
    } as never);
  };

  const isRequestPayment = paymentData.type === 'request';
  const displayAmount = paymentData.total || paymentData.amount;

  return (
    <View style={styles.container}>
      <LinearGradient
        colors={['#4CAF50', '#45a049']}
        style={styles.gradient}
      >
        <SafeAreaView style={styles.safeArea}>
          {/* Confetti Animation */}
          {confettiVisible && (
            <View style={styles.confettiContainer}>
              <LottieView
                source={require('../../assets/animations/success-checkmark.json')}
                autoPlay
                loop={false}
                style={styles.confettiAnimation}
              />
            </View>
          )}

          <View style={styles.content}>
            {/* Success Icon */}
            <Animated.View
              style={[
                styles.successIconContainer,
                {
                  transform: [{ scale: animationScale }],
                },
              ]}
            >
              <Surface style={styles.successIcon} elevation={8}>
                <Icon name=\"check\" size={80} color=\"#4CAF50\" />
              </Surface>
            </Animated.View>

            {/* Success Message */}
            <View style={styles.messageContainer}>
              <Text style={styles.successTitle}>
                {isRequestPayment ? 'Request Sent!' : 'Payment Sent!'}
              </Text>
              <Text style={styles.successSubtitle}>
                {isRequestPayment 
                  ? `Your payment request has been sent to ${paymentData.recipient.name}`
                  : `Your payment has been successfully sent to ${paymentData.recipient.name}`
                }
              </Text>
            </View>

            {/* Payment Summary */}
            <Surface style={styles.summaryCard} elevation={4}>
              <View style={styles.summaryHeader}>
                <View style={styles.recipientInfo}>
                  <Avatar.Text
                    size={56}
                    label={paymentData.recipient.name.split(' ').map(n => n[0]).join('')}
                    style={styles.recipientAvatar}
                  />
                  <View style={styles.recipientDetails}>
                    <Text style={styles.recipientName}>
                      {paymentData.recipient.name}
                    </Text>
                    <Text style={styles.recipientContact}>
                      {paymentData.recipient.phoneNumber || paymentData.recipient.email}
                    </Text>
                  </View>
                </View>
              </View>

              <View style={styles.amountContainer}>
                <Text style={styles.amountLabel}>
                  {isRequestPayment ? 'Requested Amount' : 'Amount Sent'}
                </Text>
                <Text style={styles.amountValue}>
                  {formatCurrency(displayAmount, paymentData.currency)}
                </Text>
              </View>

              {paymentData.note && (
                <View style={styles.noteContainer}>
                  <Text style={styles.noteLabel}>Note</Text>
                  <Text style={styles.noteText}>{paymentData.note}</Text>
                </View>
              )}

              <View style={styles.transactionInfo}>
                <View style={styles.transactionRow}>
                  <Text style={styles.transactionLabel}>Transaction ID</Text>
                  <Text style={styles.transactionValue}>{paymentId}</Text>
                </View>
                <View style={styles.transactionRow}>
                  <Text style={styles.transactionLabel}>Date</Text>
                  <Text style={styles.transactionValue}>
                    {formatDate(new Date())}
                  </Text>
                </View>
              </View>
            </Surface>

            {/* Action Buttons */}
            <View style={styles.actionButtons}>
              <Button
                mode=\"contained\"
                onPress={handleDone}
                style={styles.doneButton}
                contentStyle={styles.buttonContent}
                buttonColor=\"white\"
                textColor={theme.colors.primary}
              >
                Done
              </Button>

              <View style={styles.secondaryActions}>
                <Menu
                  visible={receiptMenuVisible}
                  onDismiss={() => setReceiptMenuVisible(false)}
                  anchor={
                    <Button
                      mode=\"outlined\"
                      onPress={handleShareReceipt}
                      style={styles.secondaryButton}
                      contentStyle={styles.secondaryButtonContent}
                      textColor=\"white\"
                      icon={isGeneratingReceipt ? undefined : hasReceipt ? \"receipt\" : \"download\"}
                      loading={isGeneratingReceipt}
                      disabled={isGeneratingReceipt}
                    >
                      {isGeneratingReceipt ? 'Generating...' : hasReceipt ? 'Receipt' : 'Get Receipt'}
                    </Button>
                  }
                >
                  <MenuItem 
                    onPress={() => downloadReceipt('STANDARD')} 
                    title=\"Standard Receipt\" 
                    leadingIcon=\"receipt\"
                  />
                  <MenuItem 
                    onPress={() => downloadReceipt('DETAILED')} 
                    title=\"Detailed Receipt\" 
                    leadingIcon=\"receipt-text\"
                  />
                  <MenuItem 
                    onPress={downloadProofOfPayment} 
                    title=\"Proof of Payment\" 
                    leadingIcon=\"certificate\"
                  />
                  <MenuItem 
                    onPress={shareViaEmail} 
                    title=\"Email Receipt\" 
                    leadingIcon=\"email\"
                  />
                  <MenuItem 
                    onPress={saveToDevice} 
                    title=\"Save to Device\" 
                    leadingIcon=\"content-save\"
                  />
                </Menu>

                <Button
                  mode=\"outlined\"
                  onPress={handleViewTransaction}
                  style={styles.secondaryButton}
                  contentStyle={styles.secondaryButtonContent}
                  textColor=\"white\"
                  icon=\"eye\"
                >
                  View Details
                </Button>

                {!isRequestPayment && (
                  <Button
                    mode=\"outlined\"
                    onPress={handleSendAnother}
                    style={styles.secondaryButton}
                    contentStyle={styles.secondaryButtonContent}
                    textColor=\"white\"
                    icon=\"send\"
                  >
                    Send Again
                  </Button>
                )}
              </View>
            </View>

            {/* Success Tips */}
            <Surface style={styles.tipsCard} elevation={2}>
              <View style={styles.tipsHeader}>
                <Icon name=\"lightbulb\" size={20} color={theme.colors.primary} />
                <Text style={styles.tipsTitle}>Did you know?</Text>
              </View>
              <Text style={styles.tipsText}>
                {isRequestPayment 
                  ? 'You can track your payment request status in the Activity tab.'
                  : 'You can set up recurring payments to send money automatically.'
                }
              </Text>
            </Surface>
          </View>
        </SafeAreaView>
      </LinearGradient>
      
      <Snackbar
        visible={snackbarVisible}
        onDismiss={() => setSnackbarVisible(false)}
        duration={3000}
        action={{
          label: 'Dismiss',
          onPress: () => setSnackbarVisible(false),
        }}
        style={{ marginBottom: 50 }}
      >
        {snackbarMessage}
      </Snackbar>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  gradient: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  confettiContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 1,
    pointerEvents: 'none',
  },
  confettiAnimation: {
    width: screenWidth,
    height: '100%',
  },
  content: {
    flex: 1,
    padding: 24,
    paddingTop: 60,
  },
  successIconContainer: {
    alignItems: 'center',
    marginBottom: 32,
  },
  successIcon: {
    width: 140,
    height: 140,
    borderRadius: 70,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'white',
  },
  messageContainer: {
    alignItems: 'center',
    marginBottom: 32,
  },
  successTitle: {
    fontSize: 32,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 12,
  },
  successSubtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.9)',
    textAlign: 'center',
    lineHeight: 24,
  },
  summaryCard: {
    borderRadius: 16,
    padding: 20,
    marginBottom: 24,
    backgroundColor: 'white',
  },
  summaryHeader: {
    marginBottom: 20,
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
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  recipientContact: {
    fontSize: 14,
    color: '#666',
  },
  amountContainer: {
    alignItems: 'center',
    paddingVertical: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
    marginBottom: 16,
  },
  amountLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8,
  },
  amountValue: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#4CAF50',
  },
  noteContainer: {
    marginBottom: 16,
  },
  noteLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  noteText: {
    fontSize: 16,
    color: '#333',
    fontStyle: 'italic',
  },
  transactionInfo: {
    gap: 8,
  },
  transactionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  transactionLabel: {
    fontSize: 14,
    color: '#666',
  },
  transactionValue: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
  actionButtons: {
    gap: 16,
  },
  doneButton: {
    elevation: 4,
  },
  buttonContent: {
    height: 52,
  },
  secondaryActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  secondaryButton: {
    flex: 1,
    borderColor: 'rgba(255, 255, 255, 0.5)',
  },
  secondaryButtonContent: {
    height: 44,
  },
  tipsCard: {
    borderRadius: 12,
    padding: 16,
    marginTop: 16,
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
  },
  tipsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  tipsTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginLeft: 8,
  },
  tipsText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
});

export default PaymentSuccessScreen;