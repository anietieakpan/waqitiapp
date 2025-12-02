import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  Animated,
  Platform
} from 'react-native';
import NfcManager, { NfcTech, Ndef } from 'react-native-nfc-manager';

const NFCPaymentScreen = ({ navigation, route }) => {
  const { amount } = route.params || {};
  const [isNfcSupported, setIsNfcSupported] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [pulseAnim] = useState(new Animated.Value(1));

  useEffect(() => {
    checkNfcSupport();
    return () => {
      NfcManager.cancelTechnologyRequest();
    };
  }, []);

  useEffect(() => {
    if (isScanning) {
      startPulseAnimation();
    }
  }, [isScanning]);

  const checkNfcSupport = async () => {
    try {
      const supported = await NfcManager.isSupported();
      setIsNfcSupported(supported);

      if (!supported) {
        Alert.alert(
          'NFC Not Supported',
          'Your device does not support NFC payments',
          [{ text: 'OK', onPress: () => navigation.goBack() }]
        );
      }
    } catch (error) {
      console.error('NFC check failed:', error);
      setIsNfcSupported(false);
    }
  };

  const startPulseAnimation = () => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1.2,
          duration: 1000,
          useNativeDriver: true
        }),
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true
        })
      ])
    ).start();
  };

  const startNfcScan = async () => {
    try {
      setIsScanning(true);

      // Request NFC technology
      await NfcManager.requestTechnology(NfcTech.Ndef);

      const tag = await NfcManager.getTag();

      if (tag && tag.ndefMessage) {
        const ndefRecords = tag.ndefMessage;

        // Parse NDEF message
        for (const record of ndefRecords) {
          if (record.tnf === Ndef.TNF_WELL_KNOWN) {
            const payload = Ndef.text.decodePayload(record.payload);

            try {
              const paymentData = JSON.parse(payload);

              if (paymentData.recipientId) {
                // Navigate to payment confirmation
                await NfcManager.cancelTechnologyRequest();
                setIsScanning(false);

                navigation.navigate('PaymentConfirmation', {
                  recipientId: paymentData.recipientId,
                  recipientName: paymentData.recipientName,
                  amount: amount || paymentData.amount,
                  note: 'NFC Payment',
                  paymentMethod: 'NFC'
                });
                return;
              }
            } catch (parseError) {
              console.error('Failed to parse NFC data:', parseError);
            }
          }
        }

        Alert.alert('Invalid NFC Tag', 'This tag is not a valid payment tag');
      }

      await NfcManager.cancelTechnologyRequest();
      setIsScanning(false);
    } catch (error) {
      console.error('NFC scan error:', error);
      await NfcManager.cancelTechnologyRequest();
      setIsScanning(false);

      if (error.toString().includes('cancelled')) {
        // User cancelled, do nothing
      } else {
        Alert.alert('Error', 'Failed to read NFC tag. Please try again.');
      }
    }
  };

  const stopNfcScan = async () => {
    try {
      await NfcManager.cancelTechnologyRequest();
      setIsScanning(false);
    } catch (error) {
      console.error('Failed to cancel NFC:', error);
    }
  };

  if (!isNfcSupported) {
    return (
      <View style={styles.container}>
        <Text style={styles.errorText}>NFC is not supported on this device</Text>
        <TouchableOpacity style={styles.button} onPress={() => navigation.goBack()}>
          <Text style={styles.buttonText}>Go Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>NFC Payment</Text>

      {amount && (
        <View style={styles.amountContainer}>
          <Text style={styles.amountLabel}>Amount</Text>
          <Text style={styles.amountText}>${parseFloat(amount).toFixed(2)}</Text>
        </View>
      )}

      <Animated.View
        style={[
          styles.nfcIcon,
          {
            transform: [{ scale: pulseAnim }]
          }
        ]}
      >
        <Text style={styles.nfcIconText}>📱</Text>
      </Animated.View>

      <Text style={styles.instructions}>
        {isScanning
          ? 'Hold your device near the payment terminal'
          : 'Tap the button below to start NFC payment'}
      </Text>

      {isScanning ? (
        <>
          <ActivityIndicator size="large" color="#007AFF" style={styles.loader} />
          <TouchableOpacity style={styles.cancelButton} onPress={stopNfcScan}>
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>
        </>
      ) : (
        <TouchableOpacity style={styles.button} onPress={startNfcScan}>
          <Text style={styles.buttonText}>Start NFC Payment</Text>
        </TouchableOpacity>
      )}

      <View style={styles.infoContainer}>
        <Text style={styles.infoTitle}>How it works:</Text>
        <Text style={styles.infoText}>
          1. Tap "Start NFC Payment"{'\n'}
          2. Hold your device close to the payment terminal{'\n'}
          3. Wait for confirmation{'\n'}
          4. Complete the transaction
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    backgroundColor: '#FFF',
    alignItems: 'center',
    justifyContent: 'center'
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 24
  },
  amountContainer: {
    backgroundColor: '#F0F0F0',
    padding: 20,
    borderRadius: 12,
    marginBottom: 32,
    width: '100%',
    alignItems: 'center'
  },
  amountLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8
  },
  amountText: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#007AFF'
  },
  nfcIcon: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#E3F2FD',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 32
  },
  nfcIconText: {
    fontSize: 64
  },
  instructions: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 32,
    paddingHorizontal: 24
  },
  loader: {
    marginBottom: 24
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    width: '100%',
    alignItems: 'center'
  },
  buttonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600'
  },
  cancelButton: {
    backgroundColor: '#FF3B30',
    padding: 16,
    borderRadius: 8,
    width: '100%',
    alignItems: 'center'
  },
  cancelButtonText: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: '600'
  },
  infoContainer: {
    marginTop: 48,
    padding: 20,
    backgroundColor: '#F9F9F9',
    borderRadius: 12,
    width: '100%'
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 24
  },
  errorText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24
  }
});

export default NFCPaymentScreen;
