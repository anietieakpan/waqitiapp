import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  Animated,
  Dimensions,
  Alert,
  Platform,
  Image,
  ActivityIndicator,
} from 'react-native';
import { Linking } from 'react-native';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';
import Icon from 'react-native-vector-icons/MaterialIcons';
import LinearGradient from 'react-native-linear-gradient';
import { useNavigation } from '@react-navigation/native';
import { colors, fonts } from '../../theme';
import { HapticService } from '../../services/HapticService';
import { ApiService } from '../../services/ApiService';
import { validateQRCode } from '../../utils/qrValidator';
import { VisionCameraScanner } from './camera/VisionCameraScanner';

interface QRScannerProps {
  visible: boolean;
  onClose: () => void;
  onScanSuccess?: (data: any) => void;
  mode?: 'payment' | 'receive' | 'connect' | 'any';
  title?: string;
  subtitle?: string;
}

interface QRCodeData {
  type: 'payment' | 'user' | 'merchant' | 'request' | 'connect';
  version: string;
  data: {
    userId?: string;
    merchantId?: string;
    amount?: number;
    currency?: string;
    reference?: string;
    description?: string;
    requestId?: string;
    connectionToken?: string;
    timestamp?: string;
    signature?: string;
  };
}

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');
const SCAN_AREA_SIZE = screenWidth * 0.7;

const QRScanner: React.FC<QRScannerProps> = ({
  visible,
  onClose,
  onScanSuccess,
  mode = 'any',
  title = 'Scan QR Code',
  subtitle = 'Position QR code within the frame',
}) => {
  const navigation = useNavigation();
  const [hasPermission, setHasPermission] = useState(false);
  const [isScanning, setIsScanning] = useState(true);
  const [flashMode, setFlashMode] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [recentScans, setRecentScans] = useState<string[]>([]);
  
  const [showScanner, setShowScanner] = useState(false);
  const animatedValue = useRef(new Animated.Value(0)).current;
  const scanLineAnimation = useRef<Animated.Value>(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      checkCameraPermission();
      startAnimations();
    }
    
    return () => {
      stopAnimations();
    };
  }, [visible]);

  const checkCameraPermission = async () => {
    try {
      const permission = Platform.OS === 'ios' 
        ? PERMISSIONS.IOS.CAMERA 
        : PERMISSIONS.ANDROID.CAMERA;
      
      const result = await check(permission);
      
      if (result === RESULTS.GRANTED) {
        setHasPermission(true);
      } else if (result === RESULTS.DENIED) {
        const requestResult = await request(permission);
        setHasPermission(requestResult === RESULTS.GRANTED);
      } else {
        Alert.alert(
          'Camera Permission',
          'Camera permission is required to scan QR codes. Please enable it in settings.',
          [
            { text: 'Cancel', onPress: onClose },
            { text: 'Open Settings', onPress: openSettings },
          ]
        );
      }
    } catch (error) {
      console.error('Permission check failed:', error);
      setHasPermission(false);
    }
  };

  const openSettings = () => {
    if (Platform.OS === 'ios') {
      Linking.openURL('app-settings:');
    } else {
      Linking.openSettings();
    }
  };

  const startAnimations = () => {
    // Pulse animation for corners
    Animated.loop(
      Animated.sequence([
        Animated.timing(animatedValue, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(animatedValue, {
          toValue: 0,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    ).start();
    
    // Scan line animation
    Animated.loop(
      Animated.sequence([
        Animated.timing(scanLineAnimation, {
          toValue: 1,
          duration: 2000,
          useNativeDriver: true,
        }),
        Animated.timing(scanLineAnimation, {
          toValue: 0,
          duration: 0,
          useNativeDriver: true,
        }),
      ])
    ).start();
  };

  const stopAnimations = () => {
    animatedValue.stopAnimation();
    scanLineAnimation.stopAnimation();
  };

  const handleQRCodeScan = async (data: string) => {
    if (!isScanning || isProcessing) return;
    
    // Prevent duplicate scans
    if (recentScans.includes(data)) {
      return;
    }
    
    setIsProcessing(true);
    setIsScanning(false);
    HapticService.impact();
    
    try {
      // Validate QR code format
      const qrData = await validateAndParseQRCode(data);
      
      if (!qrData) {
        throw new Error('Invalid QR code format');
      }
      
      // Check if QR code type matches expected mode
      if (mode !== 'any' && !isValidMode(qrData.type, mode)) {
        throw new Error(`Expected ${mode} QR code, but scanned ${qrData.type}`);
      }
      
      // Process based on QR code type
      await processQRCode(qrData);
      
      // Add to recent scans
      setRecentScans(prev => [...prev.slice(-4), data]);
      
      // Success feedback
      HapticService.success();
      
      if (onScanSuccess) {
        onScanSuccess(qrData);
      }
      
    } catch (error: any) {
      console.error('QR scan error:', error);
      HapticService.error();
      
      Alert.alert(
        'Scan Error',
        error.message || 'Failed to process QR code',
        [
          {
            text: 'Try Again',
            onPress: () => {
              setIsScanning(true);
              setIsProcessing(false);
            },
          },
          {
            text: 'Cancel',
            onPress: onClose,
          },
        ]
      );
    }
    
    setIsProcessing(false);
  };

  const validateAndParseQRCode = async (data: string): Promise<QRCodeData | null> => {
    try {
      // First try to parse as Waqiti QR code
      if (data.startsWith('waqiti://')) {
        return parseWaqitiQRCode(data);
      }
      
      // Try to parse as JSON
      try {
        const jsonData = JSON.parse(data);
        if (jsonData.type && jsonData.data) {
          return jsonData as QRCodeData;
        }
      } catch {}
      
      // Try to parse as merchant/user ID
      if (data.match(/^(USR|MER|REQ)[A-Z0-9]{10,}$/)) {
        return parseIdQRCode(data);
      }
      
      return null;
    } catch (error) {
      console.error('QR parsing error:', error);
      return null;
    }
  };

  const parseWaqitiQRCode = (url: string): QRCodeData => {
    const uri = new URL(url);
    const type = uri.hostname as QRCodeData['type'];
    const params = Object.fromEntries(uri.searchParams);
    
    return {
      type,
      version: '1.0',
      data: {
        ...params,
        amount: params.amount ? parseFloat(params.amount) : undefined,
        timestamp: new Date().toISOString(),
      },
    };
  };

  const parseIdQRCode = (id: string): QRCodeData => {
    const prefix = id.substring(0, 3);
    const type = prefix === 'USR' ? 'user' : prefix === 'MER' ? 'merchant' : 'request';
    
    return {
      type,
      version: '1.0',
      data: {
        userId: type === 'user' ? id : undefined,
        merchantId: type === 'merchant' ? id : undefined,
        requestId: type === 'request' ? id : undefined,
        timestamp: new Date().toISOString(),
      },
    };
  };

  const isValidMode = (qrType: string, expectedMode: string): boolean => {
    const modeMap: Record<string, string[]> = {
      payment: ['merchant', 'user', 'payment'],
      receive: ['request'],
      connect: ['connect', 'user'],
    };
    
    return modeMap[expectedMode]?.includes(qrType) || false;
  };

  const processQRCode = async (qrData: QRCodeData) => {
    switch (qrData.type) {
      case 'payment':
      case 'merchant':
        // Navigate to payment screen
        navigation.navigate('Payment', {
          merchantId: qrData.data.merchantId,
          amount: qrData.data.amount,
          currency: qrData.data.currency,
          reference: qrData.data.reference,
          description: qrData.data.description,
        });
        onClose();
        break;
        
      case 'user':
        if (mode === 'payment') {
          // Navigate to send money screen
          navigation.navigate('SendMoney', {
            recipientId: qrData.data.userId,
          });
        } else if (mode === 'connect') {
          // Add as contact
          await ApiService.addContact(qrData.data.userId!);
          Alert.alert('Success', 'Contact added successfully');
        }
        onClose();
        break;
        
      case 'request':
        // Navigate to request details
        navigation.navigate('RequestDetails', {
          requestId: qrData.data.requestId,
        });
        onClose();
        break;
        
      case 'connect':
        // Process connection token
        await ApiService.processConnectionToken(qrData.data.connectionToken!);
        Alert.alert('Success', 'Device connected successfully');
        onClose();
        break;
        
      default:
        throw new Error('Unsupported QR code type');
    }
  };

  const toggleFlash = () => {
    setFlashMode(!flashMode);
    HapticService.selection();
  };

  const openGallery = async () => {
    // Implementation for selecting QR from gallery
    Alert.alert('Coming Soon', 'Gallery QR scanning will be available soon');
  };

  if (!visible) return null;

  return (
    <Modal
      animationType="slide"
      transparent={false}
      visible={visible}
      onRequestClose={onClose}
    >
      <View style={styles.container}>
        {hasPermission ? (
          <>
            {/* Header */}
            <View style={styles.header}>
              <TouchableOpacity style={styles.closeButton} onPress={onClose}>
                <Icon name="close" size={28} color="white" />
              </TouchableOpacity>
              <View style={styles.titleContainer}>
                <Text style={styles.title}>{title}</Text>
                <Text style={styles.subtitle}>{subtitle}</Text>
              </View>
            </View>

            {/* Camera Scanner */}
            <VisionCameraScanner
              onScan={handleQRCodeScan}
              mode="scanner"
              scanTypes={['qr-code']}
              showFrame={false}
            />

            {/* Bottom Controls */}
            <View style={styles.bottomContent}>
              <View style={styles.actionButtons}>
                <TouchableOpacity style={styles.actionButton} onPress={toggleFlash}>
                  <Icon 
                    name={flashMode ? 'flash-on' : 'flash-off'} 
                    size={24} 
                    color="white" 
                  />
                  <Text style={styles.actionButtonText}>Flash</Text>
                </TouchableOpacity>
                
                <TouchableOpacity style={styles.actionButton} onPress={openGallery}>
                  <Icon name="photo-library" size={24} color="white" />
                  <Text style={styles.actionButtonText}>Gallery</Text>
                </TouchableOpacity>
              </View>
              
              {isProcessing && (
                <View style={styles.processingContainer}>
                  <ActivityIndicator size="large" color={colors.primary} />
                  <Text style={styles.processingText}>Processing...</Text>
                </View>
              )}
            </View>
          </>
        ) : (
          <View style={styles.permissionContainer}>
            <Icon name="camera-alt" size={80} color={colors.text.secondary} />
            <Text style={styles.permissionText}>Camera permission required</Text>
            <TouchableOpacity style={styles.permissionButton} onPress={checkCameraPermission}>
              <Text style={styles.permissionButtonText}>Grant Permission</Text>
            </TouchableOpacity>
          </View>
        )}
        
        {/* Custom scan area overlay */}
        {hasPermission && (
          <View style={styles.scanAreaContainer}>
            <View style={styles.scanArea}>
              {/* Animated corners */}
              <Animated.View
                style={[
                  styles.corner,
                  styles.topLeft,
                  {
                    opacity: animatedValue.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0.5, 1],
                    }),
                  },
                ]}
              />
              <Animated.View
                style={[
                  styles.corner,
                  styles.topRight,
                  {
                    opacity: animatedValue.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0.5, 1],
                    }),
                  },
                ]}
              />
              <Animated.View
                style={[
                  styles.corner,
                  styles.bottomLeft,
                  {
                    opacity: animatedValue.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0.5, 1],
                    }),
                  },
                ]}
              />
              <Animated.View
                style={[
                  styles.corner,
                  styles.bottomRight,
                  {
                    opacity: animatedValue.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0.5, 1],
                    }),
                  },
                ]}
              />
              
              {/* Animated scan line */}
              <Animated.View
                style={[
                  styles.scanLine,
                  {
                    transform: [
                      {
                        translateY: scanLineAnimation.interpolate({
                          inputRange: [0, 1],
                          outputRange: [0, SCAN_AREA_SIZE - 4],
                        }),
                      },
                    ],
                  },
                ]}
              >
                <LinearGradient
                  colors={['transparent', colors.primary, 'transparent']}
                  style={styles.scanLineGradient}
                />
              </Animated.View>
            </View>
          </View>
        )}
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  header: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 1,
    paddingTop: Platform.OS === 'ios' ? 50 : 20,
    paddingHorizontal: 20,
  },
  closeButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  titleContainer: {
    alignItems: 'center',
    marginTop: 20,
  },
  title: {
    fontSize: 24,
    fontFamily: fonts.bold,
    color: 'white',
    marginBottom: 5,
  },
  subtitle: {
    fontSize: 16,
    fontFamily: fonts.regular,
    color: 'rgba(255, 255, 255, 0.8)',
  },
  bottomContent: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingBottom: 50,
  },
  actionButtons: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 40,
    marginBottom: 30,
  },
  actionButton: {
    alignItems: 'center',
    padding: 15,
    borderRadius: 50,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  actionButtonText: {
    color: 'white',
    fontSize: 12,
    fontFamily: fonts.regular,
    marginTop: 5,
  },
  scanAreaContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scanArea: {
    width: SCAN_AREA_SIZE,
    height: SCAN_AREA_SIZE,
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderColor: colors.primary,
  },
  topLeft: {
    top: 0,
    left: 0,
    borderTopWidth: 3,
    borderLeftWidth: 3,
  },
  topRight: {
    top: 0,
    right: 0,
    borderTopWidth: 3,
    borderRightWidth: 3,
  },
  bottomLeft: {
    bottom: 0,
    left: 0,
    borderBottomWidth: 3,
    borderLeftWidth: 3,
  },
  bottomRight: {
    bottom: 0,
    right: 0,
    borderBottomWidth: 3,
    borderRightWidth: 3,
  },
  scanLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 4,
  },
  scanLineGradient: {
    flex: 1,
  },
  permissionContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  permissionText: {
    fontSize: 18,
    fontFamily: fonts.medium,
    color: colors.text.secondary,
    marginTop: 20,
    marginBottom: 30,
    textAlign: 'center',
  },
  permissionButton: {
    backgroundColor: colors.primary,
    paddingHorizontal: 30,
    paddingVertical: 15,
    borderRadius: 10,
  },
  permissionButtonText: {
    color: 'white',
    fontSize: 16,
    fontFamily: fonts.semiBold,
  },
  processingContainer: {
    alignItems: 'center',
  },
  processingText: {
    color: 'white',
    fontSize: 16,
    fontFamily: fonts.medium,
    marginTop: 10,
  },
});

export default QRScanner;