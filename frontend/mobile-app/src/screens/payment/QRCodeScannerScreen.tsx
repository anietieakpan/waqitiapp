import React, { useState, useCallback } from 'react';
import {
  View,
  StyleSheet,
  Text,
  Alert,
  SafeAreaView,
  TouchableOpacity,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useDispatch } from 'react-redux';
import { Icon } from 'react-native-elements';
import { VisionCameraScanner } from '../../components/camera/VisionCameraScanner';
import { colors } from '../../theme';
import { validateQRCode, parseQRPaymentData } from '../../utils/qrCodeUtils';
import { initiateQRPayment } from '../../store/slices/paymentSlice';

export const QRCodeScannerScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const [isProcessing, setIsProcessing] = useState(false);

  const handleQRCodeScan = useCallback(async (data: string) => {
    if (isProcessing) return;

    try {
      setIsProcessing(true);

      // Validate QR code format
      if (!validateQRCode(data)) {
        Alert.alert('Invalid QR Code', 'This QR code is not a valid payment code.');
        setIsProcessing(false);
        return;
      }

      // Parse payment data from QR code
      const paymentData = parseQRPaymentData(data);
      
      if (!paymentData) {
        Alert.alert('Error', 'Could not read payment information from QR code.');
        setIsProcessing(false);
        return;
      }

      // Navigate to payment confirmation screen
      navigation.navigate('PaymentConfirmation', {
        recipient: paymentData.merchantName || paymentData.recipientId,
        recipientId: paymentData.recipientId,
        amount: paymentData.amount,
        currency: paymentData.currency || 'USD',
        reference: paymentData.reference,
        qrCodeData: data,
        paymentType: 'QR_CODE',
      });

    } catch (error) {
      console.error('QR scan error:', error);
      Alert.alert('Error', 'Failed to process QR code. Please try again.');
    } finally {
      setIsProcessing(false);
    }
  }, [isProcessing, navigation]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Icon name="arrow-back" type="material" size={24} color="white" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Scan QR Code</Text>
        <View style={styles.placeholder} />
      </View>

      <VisionCameraScanner
        onScan={handleQRCodeScan}
        mode="scanner"
        scanTypes={['qr-code']}
        showFrame={true}
      />

      <View style={styles.bottomSection}>
        <Text style={styles.instructionText}>
          Point your camera at a QR code to scan
        </Text>
        
        <TouchableOpacity
          style={styles.myCodeButton}
          onPress={() => navigation.navigate('MyQRCode')}
        >
          <Icon name="qr-code" type="material" size={20} color={colors.primary} />
          <Text style={styles.myCodeButtonText}>My QR Code</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 1,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: 'white',
  },
  placeholder: {
    width: 40,
  },
  bottomSection: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'white',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingTop: 24,
    paddingBottom: 34,
    paddingHorizontal: 20,
    alignItems: 'center',
  },
  instructionText: {
    fontSize: 16,
    color: colors.textSecondary,
    marginBottom: 20,
  },
  myCodeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.backgroundLight,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 25,
  },
  myCodeButtonText: {
    marginLeft: 8,
    fontSize: 16,
    color: colors.primary,
    fontWeight: '500',
  },
});