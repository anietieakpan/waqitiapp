import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Animated,
  Share,
  ScrollView,
  Dimensions,
  Platform,
  PermissionsAndroid,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import QRCode from 'react-native-qrcode-svg';
import { ActivityIndicator } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { userService } from '../../services/userService';
import { paymentService } from '../../services/paymentService';
import { contactService } from '../../services/contactService';
import UserAvatar from '../../components/UserAvatar';
import { formatCurrency } from '../../utils/formatters';
import Haptics from 'react-native-haptic-feedback';
import ViewShot from 'react-native-view-shot';
import CameraRoll from '@react-native-community/cameraroll';
import { VisionCameraScanner } from '../../components/camera/VisionCameraScanner';

const { width: screenWidth } = Dimensions.get('window');
const QR_SIZE = screenWidth * 0.7;

interface QRPaymentData {
  type: 'user' | 'payment_request' | 'merchant';
  userId?: string;
  requestId?: string;
  merchantId?: string;
  amount?: number;
  currency?: string;
  description?: string;
  expiresAt?: string;
}

const QRCodeScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<'scan' | 'receive'>('scan');
  const [scannerActive, setScannerActive] = useState(true);
  const [qrData, setQrData] = useState<string>('');
  const [requestAmount, setRequestAmount] = useState('');
  const [requestNote, setRequestNote] = useState('');
  const [scannedUser, setScannedUser] = useState<any>(null);
  const [processing, setProcessing] = useState(false);
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [showAmountModal, setShowAmountModal] = useState(false);
  const qrRef = useRef<ViewShot>(null);
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.8)).current;

  useEffect(() => {
    checkCameraPermission();
    generateQRCode();
    
    // Animate QR code appearance
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        friction: 5,
        useNativeDriver: true,
      }),
    ]).start();
  }, []);

  const checkCameraPermission = async () => {
    if (Platform.OS === 'android') {
      const result = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.CAMERA,
        {
          title: 'Camera Permission',
          message: 'Waqiti needs access to your camera to scan QR codes',
          buttonNeutral: 'Ask Me Later',
          buttonNegative: 'Cancel',
          buttonPositive: 'OK',
        }
      );
      setHasPermission(result === PermissionsAndroid.RESULTS.GRANTED);
    } else {
      setHasPermission(true);
    }
  };

  const generateQRCode = async () => {
    try {
      const { qrCode } = await userService.generateQRCode();
      
      // Generate payment request QR if amount is specified
      if (requestAmount) {
        const paymentData: QRPaymentData = {
          type: 'payment_request',
          userId: user?.id,
          amount: parseFloat(requestAmount),
          currency: 'USD',
          description: requestNote,
          expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(), // 5 minutes
        };
        setQrData(JSON.stringify(paymentData));
      } else {
        // Simple user QR code
        const userData: QRPaymentData = {
          type: 'user',
          userId: user?.id,
        };
        setQrData(JSON.stringify(userData));
      }
    } catch (error) {
      console.error('Failed to generate QR code:', error);
    }
  };

  const handleQRCodeScanned = async (data: string) => {
    if (!scannerActive || processing) return;

    setProcessing(true);
    setScannerActive(false);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);

    try {
      const qrPaymentData: QRPaymentData = JSON.parse(data);
      
      switch (qrPaymentData.type) {
        case 'user':
          await handleUserQRCode(qrPaymentData);
          break;
        case 'payment_request':
          await handlePaymentRequestQRCode(qrPaymentData);
          break;
        case 'merchant':
          await handleMerchantQRCode(qrPaymentData);
          break;
        default:
          throw new Error('Invalid QR code format');
      }
    } catch (error) {
      console.error('Failed to process QR code:', error);
      Alert.alert(
        'Invalid QR Code',
        'This QR code is not valid for Waqiti payments.',
        [
          {
            text: 'OK',
            onPress: () => {
              setScannerActive(true);
              setProcessing(false);
            },
          },
        ]
      );
    }
  };

  const handleUserQRCode = async (data: QRPaymentData) => {
    try {
      const user = await userService.getUserProfile(data.userId!);
      setScannedUser(user);
      
      // Show user profile with payment options
      Alert.alert(
        'User Found',
        `${user.firstName} ${user.lastName} (@${user.username})`,
        [
          {
            text: 'Send Money',
            onPress: () => {
              navigation.navigate('SendMoney', { recipient: user });
              setProcessing(false);
            },
          },
          {
            text: 'Add Contact',
            onPress: async () => {
              await contactService.addContact(user.id);
              Alert.alert('Success', 'Contact added successfully');
              setScannerActive(true);
              setProcessing(false);
            },
          },
          {
            text: 'Cancel',
            style: 'cancel',
            onPress: () => {
              setScannerActive(true);
              setProcessing(false);
            },
          },
        ]
      );
    } catch (error) {
      console.error('Failed to fetch user:', error);
      Alert.alert('Error', 'Failed to find user');
      setScannerActive(true);
      setProcessing(false);
    }
  };

  const handlePaymentRequestQRCode = async (data: QRPaymentData) => {
    try {
      // Check if request is expired
      if (data.expiresAt && new Date(data.expiresAt) < new Date()) {
        Alert.alert('Expired', 'This payment request has expired.');
        setScannerActive(true);
        setProcessing(false);
        return;
      }

      const user = await userService.getUserProfile(data.userId!);
      
      Alert.alert(
        'Payment Request',
        `${user.firstName} ${user.lastName} is requesting ${formatCurrency(data.amount || 0)}`,
        [
          {
            text: 'Pay Now',
            onPress: () => {
              navigation.navigate('SendMoney', {
                recipient: user,
                amount: data.amount,
                note: data.description,
              });
              setProcessing(false);
            },
          },
          {
            text: 'Cancel',
            style: 'cancel',
            onPress: () => {
              setScannerActive(true);
              setProcessing(false);
            },
          },
        ]
      );
    } catch (error) {
      console.error('Failed to process payment request:', error);
      Alert.alert('Error', 'Failed to process payment request');
      setScannerActive(true);
      setProcessing(false);
    }
  };

  const handleMerchantQRCode = async (data: QRPaymentData) => {
    try {
      navigation.navigate('MerchantPayment', {
        merchantId: data.merchantId,
        amount: data.amount,
        description: data.description,
      });
      setProcessing(false);
    } catch (error) {
      console.error('Failed to process merchant QR:', error);
      Alert.alert('Error', 'Failed to process merchant payment');
      setScannerActive(true);
      setProcessing(false);
    }
  };

  const handleShareQR = async () => {
    try {
      await Share.share({
        message: `Pay me on Waqiti! Scan my QR code or use my username @${user?.username}`,
        title: 'Share QR Code',
      });
    } catch (error) {
      console.error('Error sharing:', error);
    }
  };

  const handleSaveQR = async () => {
    try {
      if (qrRef.current) {
        const uri = await qrRef.current.capture();
        const result = await CameraRoll.save(uri, { type: 'photo' });
        Alert.alert('Success', 'QR code saved to camera roll');
      }
    } catch (error) {
      console.error('Error saving QR:', error);
      Alert.alert('Error', 'Failed to save QR code');
    }
  };

  const handleTabChange = (tab: 'scan' | 'receive') => {
    setActiveTab(tab);
    if (tab === 'scan') {
      setScannerActive(true);
    } else {
      setScannerActive(false);
      generateQRCode();
    }
  };

  const renderScanner = () => {
    return (
      <View style={styles.scannerContainer}>
        <VisionCameraScanner
          onScan={handleQRCodeScanned}
          mode="scanner"
          scanTypes={['qr-code']}
          showFrame={true}
        />
        
        {scannedUser && (
          <Animated.View
            style={[
              styles.scannedUserCard,
              {
                backgroundColor: theme.colors.surface,
                opacity: fadeAnim,
                transform: [{ scale: scaleAnim }],
              },
            ]}
          >
            <UserAvatar user={scannedUser} size={60} />
            <View style={styles.scannedUserInfo}>
              <Text style={[styles.scannedUserName, { color: theme.colors.text }]}>
                {scannedUser.firstName} {scannedUser.lastName}
              </Text>
              <Text style={[styles.scannedUserUsername, { color: theme.colors.textSecondary }]}>
                @{scannedUser.username}
              </Text>
            </View>
          </Animated.View>
        )}

        {processing && (
          <View style={styles.processingOverlay}>
            <ActivityIndicator size="large" color="#FFFFFF" />
            <Text style={styles.scannerText}>Processing...</Text>
          </View>
        )}
      </View>
    );
  };

  const renderReceive = () => {
    return (
      <ScrollView style={styles.receiveContainer} showsVerticalScrollIndicator={false}>
        <Animated.View
          style={[
            styles.qrContainer,
            {
              opacity: fadeAnim,
              transform: [{ scale: scaleAnim }],
            },
          ]}
        >
          <ViewShot ref={qrRef} options={{ format: 'png', quality: 1 }}>
            <View style={[styles.qrCard, { backgroundColor: '#FFFFFF' }]}>
              <View style={styles.qrHeader}>
                <UserAvatar user={user} size={50} />
                <View style={styles.qrUserInfo}>
                  <Text style={styles.qrUserName}>
                    {user?.firstName} {user?.lastName}
                  </Text>
                  <Text style={styles.qrUsername}>@{user?.username}</Text>
                </View>
              </View>
              
              <QRCode
                value={qrData}
                size={QR_SIZE}
                backgroundColor="#FFFFFF"
                color="#000000"
                logo={require('../../assets/logo.png')}
                logoSize={50}
                logoBackgroundColor="#FFFFFF"
                logoMargin={2}
                logoBorderRadius={25}
              />
              
              {requestAmount && (
                <View style={styles.qrAmount}>
                  <Text style={styles.qrAmountLabel}>Requesting</Text>
                  <Text style={styles.qrAmountValue}>{formatCurrency(parseFloat(requestAmount))}</Text>
                </View>
              )}
              
              <Text style={styles.qrFooter}>Scan with Waqiti app to pay</Text>
            </View>
          </ViewShot>
        </Animated.View>
        
        <View style={styles.qrActions}>
          <TouchableOpacity
            style={[styles.qrActionButton, { backgroundColor: theme.colors.surface }]}
            onPress={handleShareQR}
          >
            <Icon name="share" size={24} color={theme.colors.primary} />
            <Text style={[styles.qrActionText, { color: theme.colors.text }]}>Share</Text>
          </TouchableOpacity>
          
          <TouchableOpacity
            style={[styles.qrActionButton, { backgroundColor: theme.colors.surface }]}
            onPress={handleSaveQR}
          >
            <Icon name="save-alt" size={24} color={theme.colors.primary} />
            <Text style={[styles.qrActionText, { color: theme.colors.text }]}>Save</Text>
          </TouchableOpacity>
          
          <TouchableOpacity
            style={[styles.qrActionButton, { backgroundColor: theme.colors.surface }]}
            onPress={() => setShowAmountModal(true)}
          >
            <Icon name="attach-money" size={24} color={theme.colors.primary} />
            <Text style={[styles.qrActionText, { color: theme.colors.text }]}>Amount</Text>
          </TouchableOpacity>
        </View>
        
        <View style={styles.instructionsContainer}>
          <Text style={[styles.instructionsTitle, { color: theme.colors.text }]}>
            How to receive payments
          </Text>
          <View style={styles.instruction}>
            <Icon name="looks-one" size={24} color={theme.colors.primary} />
            <Text style={[styles.instructionText, { color: theme.colors.textSecondary }]}>
              Show this QR code to the sender
            </Text>
          </View>
          <View style={styles.instruction}>
            <Icon name="looks-two" size={24} color={theme.colors.primary} />
            <Text style={[styles.instructionText, { color: theme.colors.textSecondary }]}>
              They scan it with their Waqiti app
            </Text>
          </View>
          <View style={styles.instruction}>
            <Icon name="looks-3" size={24} color={theme.colors.primary} />
            <Text style={[styles.instructionText, { color: theme.colors.textSecondary }]}>
              Money is instantly transferred to your wallet
            </Text>
          </View>
        </View>
      </ScrollView>
    );
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Icon name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <Text style={[styles.title, { color: theme.colors.text }]}>QR Code</Text>
        
        <TouchableOpacity
          onPress={() => navigation.navigate('QRHistory')}
          style={styles.historyButton}
        >
          <Icon name="history" size={24} color={theme.colors.text} />
        </TouchableOpacity>
      </View>

      <View style={[styles.tabContainer, { backgroundColor: theme.colors.surface }]}>
        <TouchableOpacity
          style={[
            styles.tab,
            activeTab === 'scan' && { backgroundColor: theme.colors.primary },
          ]}
          onPress={() => handleTabChange('scan')}
        >
          <Icon
            name="qr-code-scanner"
            size={20}
            color={activeTab === 'scan' ? '#FFFFFF' : theme.colors.text}
          />
          <Text
            style={[
              styles.tabText,
              { color: activeTab === 'scan' ? '#FFFFFF' : theme.colors.text },
            ]}
          >
            Scan
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[
            styles.tab,
            activeTab === 'receive' && { backgroundColor: theme.colors.primary },
          ]}
          onPress={() => handleTabChange('receive')}
        >
          <Icon
            name="qr-code"
            size={20}
            color={activeTab === 'receive' ? '#FFFFFF' : theme.colors.text}
          />
          <Text
            style={[
              styles.tabText,
              { color: activeTab === 'receive' ? '#FFFFFF' : theme.colors.text },
            ]}
          >
            Receive
          </Text>
        </TouchableOpacity>
      </View>

      {activeTab === 'scan' ? renderScanner() : renderReceive()}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
  },
  historyButton: {
    padding: 8,
  },
  tabContainer: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 12,
    padding: 4,
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 8,
    gap: 8,
  },
  tabText: {
    fontSize: 16,
    fontWeight: '600',
  },
  scannerContainer: {
    flex: 1,
  },
  scannerText: {
    color: '#FFFFFF',
    fontSize: 16,
    textAlign: 'center',
    marginTop: 10,
  },
  processingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scannedUserCard: {
    position: 'absolute',
    bottom: 32,
    left: 16,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  scannedUserInfo: {
    marginLeft: 12,
    flex: 1,
  },
  scannedUserName: {
    fontSize: 18,
    fontWeight: '600',
  },
  scannedUserUsername: {
    fontSize: 14,
    marginTop: 2,
  },
  receiveContainer: {
    flex: 1,
  },
  qrContainer: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  qrCard: {
    padding: 24,
    borderRadius: 16,
    alignItems: 'center',
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  qrHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 24,
  },
  qrUserInfo: {
    marginLeft: 12,
  },
  qrUserName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000000',
  },
  qrUsername: {
    fontSize: 14,
    color: '#666666',
  },
  qrAmount: {
    marginTop: 24,
    alignItems: 'center',
  },
  qrAmountLabel: {
    fontSize: 14,
    color: '#666666',
  },
  qrAmountValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#000000',
    marginTop: 4,
  },
  qrFooter: {
    marginTop: 16,
    fontSize: 12,
    color: '#666666',
  },
  qrActions: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 24,
    paddingHorizontal: 16,
    gap: 16,
  },
  qrActionButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 16,
    borderRadius: 12,
    gap: 8,
  },
  qrActionText: {
    fontSize: 14,
    fontWeight: '500',
  },
  instructionsContainer: {
    paddingHorizontal: 16,
    paddingTop: 32,
  },
  instructionsTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 16,
  },
  instruction: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    gap: 12,
  },
  instructionText: {
    flex: 1,
    fontSize: 14,
    lineHeight: 20,
  },
});

export default QRCodeScreen;