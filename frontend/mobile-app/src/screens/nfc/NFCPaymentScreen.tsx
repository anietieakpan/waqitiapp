import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  Alert,
  TouchableOpacity,
  Vibration,
  Image,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import NFCPaymentService from '../../services/nfc/NFCPaymentService';
import { useNavigation, useRoute } from '@react-navigation/native';

interface RouteParams {
  mode: 'merchant' | 'customer' | 'p2p' | 'contact';
  merchantId?: string;
  amount?: number;
  orderId?: string;
  userProfile?: any;
}

const NFCPaymentScreen: React.FC = () => {
  const navigation = useNavigation();
  const route = useRoute();
  const { mode, merchantId, amount, orderId, userProfile } = route.params as RouteParams;

  const [isNFCEnabled, setIsNFCEnabled] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState<'waiting' | 'detected' | 'processing' | 'success' | 'error'>('waiting');
  const [statusMessage, setStatusMessage] = useState('');

  // Animations
  const pulseAnimation = useRef(new Animated.Value(1)).current;
  const waveAnimation = useRef(new Animated.Value(0)).current;
  const successAnimation = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    initializeNFC();
    startAnimations();

    return () => {
      cleanup();
    };
  }, []);

  useEffect(() => {
    if (isNFCEnabled) {
      startNFCMode();
    }
  }, [isNFCEnabled, mode]);

  const initializeNFC = async () => {
    try {
      const initialized = await NFCPaymentService.initialize();
      setIsNFCEnabled(initialized);
      
      if (!initialized) {
        setStatus('error');
        setStatusMessage('NFC not available on this device');
        Alert.alert(
          'NFC Unavailable',
          'This feature requires NFC capability. Please use an NFC-enabled device.',
          [{ text: 'OK', onPress: () => navigation.goBack() }]
        );
      }
    } catch (error) {
      console.error('NFC initialization failed:', error);
      setStatus('error');
      setStatusMessage('Failed to initialize NFC');
    }
  };

  const startNFCMode = async () => {
    try {
      setStatus('waiting');
      
      switch (mode) {
        case 'merchant':
          await startMerchantMode();
          break;
        case 'customer':
          await startCustomerMode();
          break;
        case 'p2p':
          await startP2PMode();
          break;
        case 'contact':
          await startContactExchangeMode();
          break;
      }
    } catch (error) {
      console.error('Failed to start NFC mode:', error);
      setStatus('error');
      setStatusMessage('Failed to start NFC mode');
    }
  };

  const startMerchantMode = async () => {
    if (!merchantId) {
      throw new Error('Merchant ID required for merchant mode');
    }

    setStatusMessage('Ready to accept payments\nHave customers tap their phone');
    await NFCPaymentService.enableMerchantPaymentMode(merchantId, amount, orderId);
  };

  const startCustomerMode = async () => {
    setStatusMessage('Tap your phone on the merchant\'s NFC reader');
    // Customer mode is handled when merchant NFC is detected
  };

  const startP2PMode = async () => {
    if (!userProfile) {
      throw new Error('User profile required for P2P mode');
    }

    setStatusMessage('Ready for peer-to-peer transfer\nTap phones together');
    await NFCPaymentService.enableP2PMode(userProfile.userId, userProfile.displayName);
  };

  const startContactExchangeMode = async () => {
    if (!userProfile) {
      throw new Error('User profile required for contact exchange');
    }

    setStatusMessage('Ready to exchange contact info\nTap phones together');
    await NFCPaymentService.enableContactExchangeMode(userProfile);
  };

  const startAnimations = () => {
    // Pulse animation for NFC icon
    const pulseLoop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnimation, {
          toValue: 1.2,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnimation, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    );

    // Wave animation for detection
    const waveLoop = Animated.loop(
      Animated.timing(waveAnimation, {
        toValue: 1,
        duration: 2000,
        useNativeDriver: true,
      })
    );

    pulseLoop.start();
    waveLoop.start();
  };

  const handleNFCDetected = async (nfcData?: any) => {
    setStatus('detected');
    setIsProcessing(true);
    Vibration.vibrate([100, 200, 100]);
    
    // Stop pulse animation and start processing
    pulseAnimation.stopAnimation();
    
    try {
      setStatusMessage('Processing NFC data...');
      
      // Process the NFC data based on mode
      switch (mode) {
        case 'merchant':
          await handleMerchantPayment(nfcData);
          break;
        case 'customer':
          await handleCustomerPayment(nfcData);
          break;
        case 'p2p':
          await handleP2PTransfer(nfcData);
          break;
        case 'contact':
          await handleContactExchange(nfcData);
          break;
      }
      
    } catch (error) {
      console.error('Error processing NFC data:', error);
      handlePaymentError(error.message);
    }
    
    Animated.timing(successAnimation, {
      toValue: 1,
      duration: 500,
      useNativeDriver: true,
    }).start();
  };

  const handlePaymentSuccess = (result: any) => {
    setStatus('success');
    setIsProcessing(false);
    setStatusMessage('Payment successful!');
    
    Vibration.vibrate([200, 100, 200]);
    
    setTimeout(() => {
      navigation.navigate('PaymentSuccess', { result });
    }, 2000);
  };

  const handlePaymentError = (error: string) => {
    setStatus('error');
    setIsProcessing(false);
    setStatusMessage(`Payment failed: ${error}`);
    
    Vibration.vibrate([500]);
  };

  const cleanup = async () => {
    try {
      await NFCPaymentService.stopCurrentNFCOperation();
    } catch (error) {
      console.error('Cleanup error:', error);
    }
  };

  // NFC Event Handlers
  const handleMerchantPayment = async (nfcData: any) => {
    try {
      setStatusMessage('Processing merchant payment...');
      
      // Validate NFC data
      if (!nfcData || !nfcData.paymentId) {
        throw new Error('Invalid payment data received');
      }

      // Process the payment
      const result = await NFCPaymentService.processNFCPayment(nfcData);
      
      if (result.isSuccessful()) {
        handlePaymentSuccess(result);
      } else {
        throw new Error(result.errorMessage || 'Payment processing failed');
      }
      
    } catch (error) {
      console.error('Merchant payment error:', error);
      throw error;
    }
  };

  const handleCustomerPayment = async (nfcData: any) => {
    try {
      setStatusMessage('Initiating customer payment...');
      
      // Validate merchant NFC data
      if (!nfcData || !nfcData.merchantId || !nfcData.amount) {
        throw new Error('Invalid merchant payment data');
      }

      // Show payment confirmation
      const confirmed = await showPaymentConfirmation(nfcData);
      if (!confirmed) {
        setStatus('waiting');
        setIsProcessing(false);
        setStatusMessage('Payment cancelled by user');
        return;
      }

      // Process customer payment
      const result = await NFCPaymentService.initiateCustomerPayment(nfcData);
      
      if (result.isSuccessful()) {
        handlePaymentSuccess(result);
      } else {
        throw new Error(result.errorMessage || 'Payment failed');
      }
      
    } catch (error) {
      console.error('Customer payment error:', error);
      throw error;
    }
  };

  const handleP2PTransfer = async (nfcData: any) => {
    try {
      setStatusMessage('Processing peer-to-peer transfer...');
      
      // Validate P2P data
      if (!nfcData || !nfcData.userId || !nfcData.displayName) {
        throw new Error('Invalid P2P user data');
      }

      // Show transfer dialog
      const transferDetails = await showP2PTransferDialog(nfcData);
      if (!transferDetails) {
        setStatus('waiting');
        setIsProcessing(false);
        setStatusMessage('Transfer cancelled by user');
        return;
      }

      // Process P2P transfer
      const result = await NFCPaymentService.sendP2PTransfer(
        nfcData, 
        transferDetails.amount, 
        transferDetails.message
      );
      
      if (result.isSuccessful()) {
        handlePaymentSuccess(result);
      } else {
        throw new Error(result.errorMessage || 'Transfer failed');
      }
      
    } catch (error) {
      console.error('P2P transfer error:', error);
      throw error;
    }
  };

  const handleContactExchange = async (nfcData: any) => {
    try {
      setStatusMessage('Exchanging contact information...');
      
      // Validate contact data
      if (!nfcData || !nfcData.userId || !nfcData.displayName) {
        throw new Error('Invalid contact data');
      }

      // Show contact exchange confirmation
      const confirmed = await showContactExchangeConfirmation(nfcData);
      if (!confirmed) {
        setStatus('waiting');
        setIsProcessing(false);
        setStatusMessage('Contact exchange cancelled');
        return;
      }

      // Process contact exchange
      const result = await NFCPaymentService.processContactExchange({
        userId: userProfile?.userId,
        displayName: userProfile?.displayName,
        contactUserId: nfcData.userId,
        contactDisplayName: nfcData.displayName,
        avatarUrl: userProfile?.avatar,
        contactAvatarUrl: nfcData.avatar,
        publicKey: await NFCPaymentService.getPublicKey(),
        contactPublicKey: nfcData.publicKey,
        timestamp: new Date().toISOString(),
        signature: nfcData.signature,
        contactSignature: nfcData.contactSignature,
        deviceId: await NFCPaymentService.getDeviceId(),
        contactDeviceId: nfcData.deviceId,
        sharePhoneNumber: true,
        shareEmail: true,
        allowPaymentRequests: true,
        allowDirectPayments: true
      });
      
      if (result.isSuccessful()) {
        handleContactExchangeSuccess(result);
      } else {
        throw new Error(result.errorMessage || 'Contact exchange failed');
      }
      
    } catch (error) {
      console.error('Contact exchange error:', error);
      throw error;
    }
  };

  const handleContactExchangeSuccess = (result: any) => {
    setStatus('success');
    setIsProcessing(false);
    setStatusMessage('Contact added successfully!');
    
    Vibration.vibrate([200, 100, 200]);
    
    setTimeout(() => {
      navigation.navigate('ContactExchangeSuccess', { result });
    }, 2000);
  };

  // Dialog and Confirmation Methods
  const showPaymentConfirmation = (paymentData: any): Promise<boolean> => {
    return new Promise((resolve) => {
      Alert.alert(
        'Confirm Payment',
        `Pay $${paymentData.amount?.toFixed(2) || '0.00'} to ${paymentData.merchantId}?
        
Description: ${paymentData.description || 'NFC Payment'}
Order ID: ${paymentData.orderId || 'N/A'}`,
        [
          { 
            text: 'Cancel', 
            style: 'cancel',
            onPress: () => resolve(false) 
          },
          { 
            text: 'Pay Now', 
            style: 'default',
            onPress: () => resolve(true) 
          }
        ],
        { cancelable: false }
      );
    });
  };

  const showP2PTransferDialog = (userData: any): Promise<any> => {
    return new Promise((resolve) => {
      // In a real implementation, this would show a custom modal
      // For now, we'll use a simple prompt
      Alert.prompt(
        'Send Money',
        `Send money to ${userData.displayName}?
        
Enter amount:`,
        [
          { 
            text: 'Cancel', 
            style: 'cancel',
            onPress: () => resolve(null) 
          },
          { 
            text: 'Send', 
            style: 'default',
            onPress: (amount) => {
              const numAmount = parseFloat(amount || '0');
              if (numAmount > 0) {
                resolve({ amount: numAmount, message: '' });
              } else {
                Alert.alert('Invalid Amount', 'Please enter a valid amount');
                resolve(null);
              }
            }
          }
        ],
        'plain-text',
        '',
        'numeric'
      );
    });
  };

  const showContactExchangeConfirmation = (contactData: any): Promise<boolean> => {
    return new Promise((resolve) => {
      Alert.alert(
        'Exchange Contacts',
        `Add ${contactData.displayName} to your contacts?
        
This will share your contact information with them as well.`,
        [
          { 
            text: 'Cancel', 
            style: 'cancel',
            onPress: () => resolve(false) 
          },
          { 
            text: 'Add Contact', 
            style: 'default',
            onPress: () => resolve(true) 
          }
        ],
        { cancelable: false }
      );
    });
  };

  // Enhanced NFC Detection
  const setupNFCListeners = () => {
    try {
      // Set up NFC tag detection listener
      NFCPaymentService.setTagDetectionListener((tag) => {
        console.log('NFC tag detected:', tag);
        handleNFCDetected(tag);
      });

      // Set up NFC error listener
      NFCPaymentService.setErrorListener((error) => {
        console.error('NFC error:', error);
        handlePaymentError(error.message);
      });

      // Set up NFC state change listener
      NFCPaymentService.setStateChangeListener((state) => {
        console.log('NFC state changed:', state);
        if (state === 'disabled') {
          setStatus('error');
          setStatusMessage('NFC is disabled. Please enable NFC in settings.');
        }
      });

    } catch (error) {
      console.error('Error setting up NFC listeners:', error);
    }
  };

  // Transaction Status Monitoring
  const monitorTransactionStatus = async (transactionId: string) => {
    let attempts = 0;
    const maxAttempts = 30; // 30 seconds with 1-second intervals
    
    const checkStatus = async () => {
      try {
        const status = await NFCPaymentService.getTransactionStatus(transactionId);
        
        if (status.isFinalState()) {
          if (status.isSuccessful()) {
            handlePaymentSuccess(status);
          } else {
            handlePaymentError(status.errorMessage || 'Transaction failed');
          }
          return;
        }
        
        // Update progress
        if (status.completionPercentage) {
          setStatusMessage(`Processing... ${status.completionPercentage}%`);
        }
        
        attempts++;
        if (attempts < maxAttempts) {
          setTimeout(checkStatus, 1000);
        } else {
          handlePaymentError('Transaction timeout');
        }
        
      } catch (error) {
        console.error('Error checking transaction status:', error);
        handlePaymentError('Failed to check transaction status');
      }
    };
    
    checkStatus();
  };

  // Device Capability Check
  const checkDeviceCapabilities = async () => {
    try {
      const capabilities = await NFCPaymentService.getDeviceCapabilities();
      
      if (!capabilities.nfcSupported) {
        setStatus('error');
        setStatusMessage('NFC not supported on this device');
        return false;
      }
      
      if (!capabilities.nfcEnabled) {
        setStatus('error');
        setStatusMessage('NFC is disabled. Please enable NFC in settings.');
        return false;
      }
      
      if (capabilities.secureElementAvailable) {
        console.log('Secure element available - enhanced security enabled');
      }
      
      return true;
      
    } catch (error) {
      console.error('Error checking device capabilities:', error);
      return false;
    }
  };

  const getModeTitle = () => {
    switch (mode) {
      case 'merchant':
        return 'Accept NFC Payment';
      case 'customer':
        return 'NFC Payment';
      case 'p2p':
        return 'Peer-to-Peer Transfer';
      case 'contact':
        return 'Exchange Contacts';
      default:
        return 'NFC Payment';
    }
  };

  const getModeIcon = () => {
    switch (mode) {
      case 'merchant':
        return 'store';
      case 'customer':
        return 'contactless-payment';
      case 'p2p':
        return 'account-multiple';
      case 'contact':
        return 'contacts';
      default:
        return 'nfc';
    }
  };

  const getStatusColor = () => {
    switch (status) {
      case 'waiting':
        return '#007AFF';
      case 'detected':
        return '#FF9500';
      case 'processing':
        return '#FF9500';
      case 'success':
        return '#34C759';
      case 'error':
        return '#FF3B30';
      default:
        return '#007AFF';
    }
  };

  const waveInterpolation = waveAnimation.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 1.5],
  });

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Icon name="arrow-left" size={24} color="#000" />
        </TouchableOpacity>
        <Text style={styles.title}>{getModeTitle()}</Text>
        <View style={styles.placeholder} />
      </View>

      {/* Main NFC Area */}
      <View style={styles.nfcContainer}>
        {/* NFC Icon with animations */}
        <View style={styles.nfcIconContainer}>
          {/* Wave effects */}
          {status === 'waiting' && (
            <>
              <Animated.View
                style={[
                  styles.wave,
                  {
                    transform: [{ scale: waveInterpolation }],
                    opacity: waveAnimation.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0.3, 0],
                    }),
                  },
                ]}
              />
              <Animated.View
                style={[
                  styles.wave,
                  styles.wave2,
                  {
                    transform: [{ scale: waveInterpolation }],
                    opacity: waveAnimation.interpolate({
                      inputRange: [0, 0.5, 1],
                      outputRange: [0, 0.3, 0],
                    }),
                  },
                ]}
              />
            </>
          )}

          {/* Main NFC Icon */}
          <Animated.View
            style={[
              styles.nfcIcon,
              {
                transform: [{ scale: pulseAnimation }],
                backgroundColor: getStatusColor(),
              },
            ]}
          >
            <Icon name={getModeIcon()} size={60} color="#FFF" />
          </Animated.View>

          {/* Success checkmark */}
          {status === 'success' && (
            <Animated.View
              style={[
                styles.successOverlay,
                {
                  opacity: successAnimation,
                  transform: [
                    {
                      scale: successAnimation.interpolate({
                        inputRange: [0, 1],
                        outputRange: [0.5, 1],
                      }),
                    },
                  ],
                },
              ]}
            >
              <Icon name="check-circle" size={80} color="#34C759" />
            </Animated.View>
          )}
        </View>

        {/* Status Message */}
        <Text style={[styles.statusMessage, { color: getStatusColor() }]}>
          {statusMessage}
        </Text>

        {/* Amount Display (for merchant/customer modes) */}
        {amount && (mode === 'merchant' || mode === 'customer') && (
          <View style={styles.amountContainer}>
            <Text style={styles.amountLabel}>Amount</Text>
            <Text style={styles.amountValue}>${amount.toFixed(2)}</Text>
          </View>
        )}

        {/* Processing Indicator */}
        {isProcessing && (
          <View style={styles.processingContainer}>
            <Icon name="loading" size={24} color={getStatusColor()} />
            <Text style={styles.processingText}>Processing...</Text>
          </View>
        )}
      </View>

      {/* Instructions */}
      <View style={styles.instructionsContainer}>
        <Text style={styles.instructionsTitle}>Instructions</Text>
        {mode === 'merchant' && (
          <View style={styles.instruction}>
            <Icon name="numeric-1-circle" size={20} color="#666" />
            <Text style={styles.instructionText}>Keep your phone steady</Text>
          </View>
        )}
        {mode === 'customer' && (
          <View style={styles.instruction}>
            <Icon name="numeric-1-circle" size={20} color="#666" />
            <Text style={styles.instructionText}>Hold your phone near the NFC reader</Text>
          </View>
        )}
        {(mode === 'p2p' || mode === 'contact') && (
          <View style={styles.instruction}>
            <Icon name="numeric-1-circle" size={20} color="#666" />
            <Text style={styles.instructionText}>Tap the back of your phones together</Text>
          </View>
        )}
        <View style={styles.instruction}>
          <Icon name="numeric-2-circle" size={20} color="#666" />
          <Text style={styles.instructionText}>Wait for confirmation vibration</Text>
        </View>
        <View style={styles.instruction}>
          <Icon name="numeric-3-circle" size={20} color="#666" />
          <Text style={styles.instructionText}>Transaction will complete automatically</Text>
        </View>
      </View>

      {/* Security Info */}
      <View style={styles.securityContainer}>
        <Icon name="shield-check" size={16} color="#34C759" />
        <Text style={styles.securityText}>
          Secure NFC with end-to-end encryption
        </Text>
      </View>

      {/* Cancel Button */}
      <TouchableOpacity style={styles.cancelButton} onPress={() => navigation.goBack()}>
        <Text style={styles.cancelButtonText}>Cancel</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E7',
    backgroundColor: '#FFF',
  },
  backButton: {
    padding: 8,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
  },
  placeholder: {
    width: 40,
  },
  nfcContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  nfcIconContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 30,
  },
  wave: {
    position: 'absolute',
    width: 200,
    height: 200,
    borderRadius: 100,
    borderWidth: 2,
    borderColor: '#007AFF',
  },
  wave2: {
    width: 240,
    height: 240,
    borderRadius: 120,
  },
  nfcIcon: {
    width: 120,
    height: 120,
    borderRadius: 60,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  successOverlay: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusMessage: {
    fontSize: 18,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 24,
  },
  amountContainer: {
    alignItems: 'center',
    marginBottom: 20,
  },
  amountLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  amountValue: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#000',
  },
  processingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 20,
  },
  processingText: {
    marginLeft: 8,
    fontSize: 16,
    color: '#666',
  },
  instructionsContainer: {
    backgroundColor: '#FFF',
    margin: 16,
    padding: 16,
    borderRadius: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  instructionsTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
    color: '#000',
  },
  instruction: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  instructionText: {
    marginLeft: 8,
    fontSize: 14,
    color: '#666',
    flex: 1,
  },
  securityContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 16,
    marginBottom: 16,
  },
  securityText: {
    marginLeft: 6,
    fontSize: 12,
    color: '#666',
  },
  cancelButton: {
    backgroundColor: '#FF3B30',
    marginHorizontal: 16,
    marginBottom: 16,
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default NFCPaymentScreen;