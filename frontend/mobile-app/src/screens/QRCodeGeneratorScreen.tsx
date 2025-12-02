import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Share,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import QRCode from 'react-native-qrcode-svg';
import ViewShot from 'react-native-view-shot';
import * as Sharing from 'expo-sharing';
import * as FileSystem from 'expo-file-system';
import { RootState } from '../store';
import Header from '../components/Header';
import AmountInput from '../components/AmountInput';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * QRCodeGeneratorScreen
 *
 * Screen for generating QR codes for payment collection
 *
 * Features:
 * - Dynamic QR code generation
 * - Amount specification (optional)
 * - QR code sharing
 * - QR code saving
 * - Business branding
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const QRCodeGeneratorScreen: React.FC = () => {
  const navigation = useNavigation();
  const { user } = useSelector((state: RootState) => state.auth);

  const viewShotRef = useRef<any>(null);

  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [qrData, setQrData] = useState('');
  const [qrSize, setQrSize] = useState<'small' | 'medium' | 'large'>('medium');

  useEffect(() => {
    AnalyticsService.trackScreenView('QRCodeGeneratorScreen');
    generateQRData();
  }, [amount, description]);

  const generateQRData = () => {
    // Generate payment request data
    const paymentData = {
      version: '1.0',
      type: 'payment_request',
      recipientId: user?.id,
      recipientName: user?.fullName || user?.businessName,
      amount: amount ? parseFloat(amount) : undefined,
      description: description || undefined,
      currency: 'USD',
      timestamp: new Date().toISOString(),
    };

    // Convert to JSON string for QR code
    const dataString = JSON.stringify(paymentData);
    setQrData(dataString);
  };

  const getQRSize = (): number => {
    switch (qrSize) {
      case 'small':
        return 200;
      case 'medium':
        return 280;
      case 'large':
        return 360;
      default:
        return 280;
    }
  };

  const handleShareQR = async () => {
    try {
      // Capture QR code as image
      const uri = await viewShotRef.current.capture();

      AnalyticsService.trackEvent('qr_code_shared', {
        userId: user?.id,
        hasAmount: !!amount,
      });

      // Share the image
      await Sharing.shareAsync(uri, {
        mimeType: 'image/png',
        dialogTitle: 'Share Payment QR Code',
      });
    } catch (error) {
      Alert.alert('Error', 'Failed to share QR code');
    }
  };

  const handleSaveQR = async () => {
    try {
      // Capture QR code as image
      const uri = await viewShotRef.current.capture();

      // Save to device
      const fileName = `waqiti_qr_${Date.now()}.png`;
      const fileUri = `${FileSystem.documentDirectory}${fileName}`;

      await FileSystem.copyAsync({
        from: uri,
        to: fileUri,
      });

      AnalyticsService.trackEvent('qr_code_saved', {
        userId: user?.id,
        hasAmount: !!amount,
      });

      Alert.alert('Success', 'QR code saved to your device');
    } catch (error) {
      Alert.alert('Error', 'Failed to save QR code');
    }
  };

  const handleCopyLink = async () => {
    try {
      // Generate payment link
      const paymentLink = `https://waqiti.com/pay/${user?.id}${
        amount ? `?amount=${amount}` : ''
      }${description ? `&description=${encodeURIComponent(description)}` : ''}`;

      // TODO: Copy to clipboard
      // await Clipboard.setStringAsync(paymentLink);

      AnalyticsService.trackEvent('payment_link_copied', {
        userId: user?.id,
        hasAmount: !!amount,
      });

      Alert.alert('Copied', 'Payment link copied to clipboard');
    } catch (error) {
      Alert.alert('Error', 'Failed to copy link');
    }
  };

  const renderQRCode = () => (
    <ViewShot ref={viewShotRef} options={{ format: 'png', quality: 1.0 }}>
      <View style={styles.qrContainer}>
        <View style={styles.qrHeader}>
          <Icon name="qrcode" size={32} color="#6200EE" />
          <Text style={styles.qrTitle}>Scan to Pay</Text>
        </View>

        <View style={styles.qrCodeWrapper}>
          {qrData ? (
            <QRCode
              value={qrData}
              size={getQRSize()}
              color="#212121"
              backgroundColor="#FFFFFF"
              logo={require('../assets/logo.png')}
              logoSize={40}
              logoBackgroundColor="#FFFFFF"
            />
          ) : (
            <View style={styles.qrPlaceholder}>
              <Icon name="qrcode" size={100} color="#E0E0E0" />
            </View>
          )}
        </View>

        {user?.businessName && (
          <View style={styles.businessInfo}>
            <Text style={styles.businessName}>{user.businessName}</Text>
            {user.businessCategory && (
              <Text style={styles.businessCategory}>{user.businessCategory}</Text>
            )}
          </View>
        )}

        {amount && (
          <View style={styles.amountDisplay}>
            <Text style={styles.amountLabel}>Amount:</Text>
            <Text style={styles.amountValue}>
              ${parseFloat(amount).toFixed(2)}
            </Text>
          </View>
        )}

        {description && (
          <View style={styles.descriptionDisplay}>
            <Text style={styles.descriptionText}>{description}</Text>
          </View>
        )}

        <View style={styles.qrFooter}>
          <Icon name="shield-check" size={16} color="#4CAF50" />
          <Text style={styles.qrFooterText}>Secured by Waqiti</Text>
        </View>
      </View>
    </ViewShot>
  );

  return (
    <View style={styles.container}>
      <Header title="QR Code Generator" showBack />

      <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Payment Details</Text>

          <AmountInput
            value={amount}
            onChangeAmount={setAmount}
            label="Amount (Optional)"
            placeholder="0.00"
            showQuickAmounts={false}
          />

          <View style={styles.inputContainer}>
            <Text style={styles.inputLabel}>Description (Optional)</Text>
            <View style={styles.descriptionInput}>
              <Icon name="message-text" size={20} color="#666" />
              <Text
                style={styles.descriptionPlaceholder}
                onPress={() => {
                  // TODO: Show description input modal
                }}
              >
                {description || 'Add description...'}
              </Text>
            </View>
          </View>
        </View>

        {renderQRCode()}

        <View style={styles.sizeSelector}>
          <Text style={styles.sizeSelectorLabel}>QR Code Size:</Text>
          <View style={styles.sizeButtons}>
            {(['small', 'medium', 'large'] as const).map((size) => (
              <TouchableOpacity
                key={size}
                style={[
                  styles.sizeButton,
                  qrSize === size && styles.sizeButtonActive,
                ]}
                onPress={() => setQrSize(size)}
              >
                <Text
                  style={[
                    styles.sizeButtonText,
                    qrSize === size && styles.sizeButtonTextActive,
                  ]}
                >
                  {size.charAt(0).toUpperCase() + size.slice(1)}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.actionsContainer}>
          <TouchableOpacity style={styles.actionButton} onPress={handleShareQR}>
            <Icon name="share-variant" size={24} color="#6200EE" />
            <Text style={styles.actionButtonText}>Share QR</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.actionButton} onPress={handleSaveQR}>
            <Icon name="download" size={24} color="#6200EE" />
            <Text style={styles.actionButtonText}>Save QR</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.actionButton} onPress={handleCopyLink}>
            <Icon name="link" size={24} color="#6200EE" />
            <Text style={styles.actionButtonText}>Copy Link</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.infoCard}>
          <Icon name="information" size={20} color="#6200EE" />
          <Text style={styles.infoText}>
            Customers can scan this QR code with the Waqiti app or any QR scanner to send you payment instantly.
          </Text>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  content: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 24,
  },
  section: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 16,
  },
  inputContainer: {
    marginTop: 16,
  },
  inputLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 8,
  },
  descriptionInput: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#E0E0E0',
  },
  descriptionPlaceholder: {
    fontSize: 16,
    color: '#999',
    marginLeft: 12,
    flex: 1,
  },
  qrContainer: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  qrHeader: {
    alignItems: 'center',
    marginBottom: 24,
  },
  qrTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 8,
  },
  qrCodeWrapper: {
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    marginBottom: 16,
  },
  qrPlaceholder: {
    width: 280,
    height: 280,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
  },
  businessInfo: {
    alignItems: 'center',
    marginBottom: 16,
  },
  businessName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  businessCategory: {
    fontSize: 14,
    color: '#666',
  },
  amountDisplay: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  amountLabel: {
    fontSize: 16,
    color: '#666',
    marginRight: 8,
  },
  amountValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#4CAF50',
  },
  descriptionDisplay: {
    backgroundColor: '#F5F5F5',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  descriptionText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
  qrFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  qrFooterText: {
    fontSize: 12,
    color: '#999',
    marginLeft: 6,
  },
  sizeSelector: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 8,
  },
  sizeSelectorLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 12,
  },
  sizeButtons: {
    flexDirection: 'row',
  },
  sizeButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 8,
    backgroundColor: '#F5F5F5',
    alignItems: 'center',
    marginHorizontal: 4,
  },
  sizeButtonActive: {
    backgroundColor: '#6200EE',
  },
  sizeButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  sizeButtonTextActive: {
    color: '#FFFFFF',
  },
  actionsContainer: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    marginBottom: 16,
  },
  actionButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 16,
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    marginHorizontal: 4,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 1,
  },
  actionButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6200EE',
    marginTop: 8,
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8EAF6',
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    borderRadius: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
});

export default QRCodeGeneratorScreen;
