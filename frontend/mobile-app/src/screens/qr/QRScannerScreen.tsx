import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  Linking
} from 'react-native';
import { RNCamera } from 'react-native-camera';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';

const QRScannerScreen = ({ navigation }) => {
  const [hasPermission, setHasPermission] = useState(false);
  const [scanning, setScanning] = useState(true);
  const [processing, setProcessing] = useState(false);

  useEffect(() => {
    requestCameraPermission();
  }, []);

  const requestCameraPermission = async () => {
    try {
      const permission = Platform.OS === 'ios'
        ? PERMISSIONS.IOS.CAMERA
        : PERMISSIONS.ANDROID.CAMERA;

      const result = await check(permission);

      if (result === RESULTS.GRANTED) {
        setHasPermission(true);
      } else {
        const requestResult = await request(permission);
        setHasPermission(requestResult === RESULTS.GRANTED);
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to request camera permission');
    }
  };

  const handleBarCodeRead = async ({ data }: { data: string }) => {
    if (!scanning || processing) return;

    setScanning(false);
    setProcessing(true);

    try {
      // Parse QR code data
      const paymentData = JSON.parse(data);

      if (!paymentData.recipientId || !paymentData.amount) {
        Alert.alert('Invalid QR Code', 'This QR code is not a valid payment request');
        resetScanner();
        return;
      }

      // Navigate to payment confirmation screen
      navigation.navigate('PaymentConfirmation', {
        recipientId: paymentData.recipientId,
        recipientName: paymentData.recipientName,
        amount: paymentData.amount,
        note: paymentData.note
      });
    } catch (error) {
      // If not JSON, might be a payment link
      if (data.startsWith('waqiti://pay') || data.startsWith('https://waqiti.com/pay')) {
        try {
          await Linking.openURL(data);
        } catch (linkError) {
          Alert.alert('Error', 'Failed to open payment link');
        }
      } else {
        Alert.alert('Invalid QR Code', 'This QR code is not recognized');
      }
      resetScanner();
    }
  };

  const resetScanner = () => {
    setTimeout(() => {
      setProcessing(false);
      setScanning(true);
    }, 2000);
  };

  if (!hasPermission) {
    return (
      <View style={styles.container}>
        <Text style={styles.permissionText}>
          Camera permission is required to scan QR codes
        </Text>
        <TouchableOpacity style={styles.button} onPress={requestCameraPermission}>
          <Text style={styles.buttonText}>Grant Permission</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <RNCamera
        style={styles.camera}
        type={RNCamera.Constants.Type.back}
        onBarCodeRead={handleBarCodeRead}
        barCodeTypes={[RNCamera.Constants.BarCodeType.qr]}
        captureAudio={false}
      >
        <View style={styles.overlay}>
          <View style={styles.topOverlay} />
          <View style={styles.middleRow}>
            <View style={styles.sideOverlay} />
            <View style={styles.scanFrame}>
              <View style={[styles.corner, styles.topLeftCorner]} />
              <View style={[styles.corner, styles.topRightCorner]} />
              <View style={[styles.corner, styles.bottomLeftCorner]} />
              <View style={[styles.corner, styles.bottomRightCorner]} />
            </View>
            <View style={styles.sideOverlay} />
          </View>
          <View style={styles.bottomOverlay}>
            <Text style={styles.instructionText}>
              {processing ? 'Processing...' : 'Align QR code within the frame'}
            </Text>
            {processing && <ActivityIndicator color="#FFF" style={styles.loader} />}
          </View>
        </View>
      </RNCamera>

      <TouchableOpacity
        style={styles.closeButton}
        onPress={() => navigation.goBack()}
      >
        <Text style={styles.closeButtonText}>✕</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center'
  },
  camera: {
    flex: 1,
    width: '100%'
  },
  overlay: {
    flex: 1
  },
  topOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)'
  },
  middleRow: {
    flexDirection: 'row',
    height: 300
  },
  sideOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)'
  },
  scanFrame: {
    width: 300,
    height: 300,
    position: 'relative'
  },
  bottomOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 24
  },
  corner: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderColor: '#FFF',
    borderWidth: 4
  },
  topLeftCorner: {
    top: 0,
    left: 0,
    borderRightWidth: 0,
    borderBottomWidth: 0
  },
  topRightCorner: {
    top: 0,
    right: 0,
    borderLeftWidth: 0,
    borderBottomWidth: 0
  },
  bottomLeftCorner: {
    bottom: 0,
    left: 0,
    borderRightWidth: 0,
    borderTopWidth: 0
  },
  bottomRightCorner: {
    bottom: 0,
    right: 0,
    borderLeftWidth: 0,
    borderTopWidth: 0
  },
  instructionText: {
    color: '#FFF',
    fontSize: 16,
    textAlign: 'center',
    paddingHorizontal: 32
  },
  loader: {
    marginTop: 16
  },
  permissionText: {
    color: '#FFF',
    fontSize: 16,
    textAlign: 'center',
    paddingHorizontal: 32,
    marginBottom: 24
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    paddingHorizontal: 32
  },
  buttonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600'
  },
  closeButton: {
    position: 'absolute',
    top: 50,
    right: 20,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center'
  },
  closeButtonText: {
    color: '#FFF',
    fontSize: 24,
    fontWeight: '300'
  }
});

export default QRScannerScreen;
