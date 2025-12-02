import NfcManager, {NfcTech, Ndef, NfcAdapter} from 'react-native-nfc-manager';
import AsyncStorage from '@react-native-async-storage/async-storage';
import CryptoJS from 'crypto-js';
import { Alert, Platform, Vibration } from 'react-native';

export interface NFCPaymentData {
  merchantId: string;
  amount?: number;
  currency: string;
  description?: string;
  orderId?: string;
  paymentId: string;
  timestamp: number;
  signature: string;
}

export interface NFCUserData {
  userId: string;
  publicKey: string;
  displayName: string;
  avatar?: string;
  timestamp: number;
  signature: string;
}

export interface NFCTransferData {
  senderId: string;
  amount: number;
  currency: string;
  message?: string;
  transferId: string;
  timestamp: number;
  signature: string;
}

class NFCPaymentService {
  private isInitialized = false;
  private privateKey: string | null = null;

  async initialize(): Promise<boolean> {
    try {
      // Initialize NFC Manager
      const supported = await NfcManager.isSupported();
      if (!supported) {
        console.warn('NFC not supported on this device');
        return false;
      }

      await NfcManager.start();
      this.isInitialized = true;

      // Generate or retrieve encryption keys
      await this.initializeEncryption();

      console.log('NFC Payment Service initialized successfully');
      return true;
    } catch (error) {
      console.error('Failed to initialize NFC Payment Service:', error);
      return false;
    }
  }

  private async initializeEncryption(): Promise<void> {
    try {
      // Try to get existing private key
      this.privateKey = await AsyncStorage.getItem('nfc_private_key');
      
      if (!this.privateKey) {
        // Generate new key pair
        this.privateKey = CryptoJS.lib.WordArray.random(256/8).toString();
        await AsyncStorage.setItem('nfc_private_key', this.privateKey);
      }
    } catch (error) {
      console.error('Failed to initialize encryption:', error);
      throw error;
    }
  }

  private generateSignature(data: any): string {
    const dataString = JSON.stringify(data);
    const signature = CryptoJS.HmacSHA256(dataString, this.privateKey!).toString();
    return signature;
  }

  private verifySignature(data: any, signature: string): boolean {
    const expectedSignature = this.generateSignature(data);
    return expectedSignature === signature;
  }

  // Merchant NFC Payment Reception
  async enableMerchantPaymentMode(merchantId: string, amount?: number, orderId?: string): Promise<void> {
    if (!this.isInitialized) {
      throw new Error('NFC Service not initialized');
    }

    try {
      await this.stopCurrentNFCOperation();

      // Create payment data
      const paymentData: NFCPaymentData = {
        merchantId,
        amount,
        currency: 'USD',
        description: 'NFC Payment',
        orderId,
        paymentId: this.generatePaymentId(),
        timestamp: Date.now(),
        signature: ''
      };

      // Generate signature
      paymentData.signature = this.generateSignature({
        merchantId: paymentData.merchantId,
        amount: paymentData.amount,
        paymentId: paymentData.paymentId,
        timestamp: paymentData.timestamp
      });

      // Prepare NDEF message
      const ndefMessage = this.createNDEFMessage('MERCHANT_PAYMENT', paymentData);

      // Enable NFC reading for customer payments
      await NfcManager.registerTagEvent();
      
      // Also enable writing mode for customers to tap
      await NfcManager.setEventListener(NfcAdapter.STATE_ON, this.handleNFCStateChange);

      console.log('Merchant payment mode enabled', paymentData);
    } catch (error) {
      console.error('Failed to enable merchant payment mode:', error);
      throw error;
    }
  }

  // Customer NFC Payment
  async initiateCustomerPayment(paymentData: NFCPaymentData): Promise<void> {
    if (!this.isInitialized) {
      throw new Error('NFC Service not initialized');
    }

    try {
      await this.stopCurrentNFCOperation();

      // Verify payment data signature
      if (!this.verifySignature({
        merchantId: paymentData.merchantId,
        amount: paymentData.amount,
        paymentId: paymentData.paymentId,
        timestamp: paymentData.timestamp
      }, paymentData.signature)) {
        throw new Error('Invalid payment data signature');
      }

      // Show payment confirmation
      const confirmed = await this.showPaymentConfirmation(paymentData);
      if (!confirmed) {
        return;
      }

      // Process payment through backend
      const result = await this.processNFCPayment(paymentData);
      
      if (result.success) {
        // Create success response
        const response = {
          status: 'SUCCESS',
          transactionId: result.transactionId,
          timestamp: Date.now(),
          signature: this.generateSignature({
            status: 'SUCCESS',
            transactionId: result.transactionId,
            paymentId: paymentData.paymentId
          })
        };

        // Write response back to merchant device
        await this.writeNFCResponse(response);
        
        // Provide haptic feedback
        Vibration.vibrate([100, 200, 100]);
        
        Alert.alert(
          'Payment Successful',
          `$${paymentData.amount} paid to ${paymentData.merchantId}`,
          [{ text: 'OK' }]
        );
      }
    } catch (error) {
      console.error('Customer payment failed:', error);
      await this.writeNFCResponse({
        status: 'FAILED',
        error: error.message,
        timestamp: Date.now()
      });
      
      Alert.alert('Payment Failed', error.message);
    }
  }

  // Peer-to-Peer NFC Transfer
  async enableP2PMode(userId: string, displayName: string): Promise<void> {
    if (!this.isInitialized) {
      throw new Error('NFC Service not initialized');
    }

    try {
      await this.stopCurrentNFCOperation();

      const userData: NFCUserData = {
        userId,
        publicKey: await this.getPublicKey(),
        displayName,
        timestamp: Date.now(),
        signature: ''
      };

      userData.signature = this.generateSignature({
        userId: userData.userId,
        publicKey: userData.publicKey,
        timestamp: userData.timestamp
      });

      const ndefMessage = this.createNDEFMessage('P2P_USER', userData);

      // Enable both reading and writing
      await NfcManager.registerTagEvent();
      await NfcManager.setEventListener(NfcAdapter.STATE_ON, this.handleP2PNFCEvent);

      console.log('P2P NFC mode enabled for user:', userId);
    } catch (error) {
      console.error('Failed to enable P2P mode:', error);
      throw error;
    }
  }

  async sendP2PTransfer(recipientData: NFCUserData, amount: number, message?: string): Promise<void> {
    try {
      // Verify recipient data
      if (!this.verifySignature({
        userId: recipientData.userId,
        publicKey: recipientData.publicKey,
        timestamp: recipientData.timestamp
      }, recipientData.signature)) {
        throw new Error('Invalid recipient data');
      }

      const transferData: NFCTransferData = {
        senderId: await this.getCurrentUserId(),
        amount,
        currency: 'USD',
        message,
        transferId: this.generateTransferId(),
        timestamp: Date.now(),
        signature: ''
      };

      transferData.signature = this.generateSignature({
        senderId: transferData.senderId,
        amount: transferData.amount,
        transferId: transferData.transferId,
        timestamp: transferData.timestamp
      });

      // Process transfer
      const result = await this.processP2PTransfer(transferData, recipientData.userId);
      
      if (result.success) {
        // Write success response
        await this.writeNFCResponse({
          status: 'SUCCESS',
          transactionId: result.transactionId,
          amount: transferData.amount,
          message: transferData.message
        });

        Vibration.vibrate([100, 200, 100]);
        Alert.alert(
          'Transfer Successful',
          `$${amount} sent to ${recipientData.displayName}`,
          [{ text: 'OK' }]
        );
      }
    } catch (error) {
      console.error('P2P transfer failed:', error);
      Alert.alert('Transfer Failed', error.message);
    }
  }

  // Quick Contact Exchange
  async enableContactExchangeMode(userProfile: any): Promise<void> {
    if (!this.isInitialized) {
      throw new Error('NFC Service not initialized');
    }

    try {
      await this.stopCurrentNFCOperation();

      const contactData = {
        type: 'CONTACT_EXCHANGE',
        userId: userProfile.userId,
        displayName: userProfile.displayName,
        avatar: userProfile.avatar,
        publicKey: await this.getPublicKey(),
        timestamp: Date.now(),
        signature: ''
      };

      contactData.signature = this.generateSignature({
        userId: contactData.userId,
        displayName: contactData.displayName,
        timestamp: contactData.timestamp
      });

      const ndefMessage = this.createNDEFMessage('CONTACT_EXCHANGE', contactData);

      await NfcManager.registerTagEvent();
      await NfcManager.setEventListener(NfcAdapter.STATE_ON, (event) => {
        this.handleContactExchange(event, contactData);
      });

      console.log('Contact exchange mode enabled');
    } catch (error) {
      console.error('Failed to enable contact exchange mode:', error);
      throw error;
    }
  }

  // NFC Event Handlers
  private handleNFCStateChange = (state: any) => {
    console.log('NFC State changed:', state);
  };

  private handleP2PNFCEvent = async (event: any) => {
    try {
      if (event.ndefMessage && event.ndefMessage.length > 0) {
        const record = event.ndefMessage[0];
        const payload = Ndef.text.decodePayload(record.payload);
        const data = JSON.parse(payload);

        if (data.type === 'P2P_USER') {
          // Handle incoming user data for P2P transfer
          await this.showP2PTransferDialog(data);
        }
      }
    } catch (error) {
      console.error('Error handling P2P NFC event:', error);
    }
  };

  private handleContactExchange = async (event: any, myContactData: any) => {
    try {
      if (event.ndefMessage && event.ndefMessage.length > 0) {
        const record = event.ndefMessage[0];
        const payload = Ndef.text.decodePayload(record.payload);
        const theirContactData = JSON.parse(payload);

        if (theirContactData.type === 'CONTACT_EXCHANGE') {
          // Verify signature
          if (this.verifySignature({
            userId: theirContactData.userId,
            displayName: theirContactData.displayName,
            timestamp: theirContactData.timestamp
          }, theirContactData.signature)) {
            
            // Add contact
            await this.addContact(theirContactData);
            
            // Write back our contact data
            await this.writeNFCResponse(myContactData);
            
            Alert.alert(
              'Contact Added',
              `${theirContactData.displayName} has been added to your contacts`,
              [{ text: 'OK' }]
            );
          }
        }
      }
    } catch (error) {
      console.error('Error handling contact exchange:', error);
    }
  };

  // Utility Methods
  private createNDEFMessage(type: string, data: any): any {
    const payload = JSON.stringify({ type, ...data });
    return [Ndef.textRecord(payload)];
  }

  private async writeNFCResponse(response: any): Promise<void> {
    try {
      const message = this.createNDEFMessage('RESPONSE', response);
      await NfcManager.writeNdefMessage(message);
    } catch (error) {
      console.error('Failed to write NFC response:', error);
    }
  }

  private async showPaymentConfirmation(paymentData: NFCPaymentData): Promise<boolean> {
    return new Promise((resolve) => {
      Alert.alert(
        'Confirm Payment',
        `Pay $${paymentData.amount} to ${paymentData.merchantId}?`,
        [
          { text: 'Cancel', onPress: () => resolve(false) },
          { text: 'Pay', onPress: () => resolve(true) }
        ]
      );
    });
  }

  private async showP2PTransferDialog(userData: NFCUserData): Promise<void> {
    // This would show a dialog to enter amount and send money
    console.log('Show P2P transfer dialog for:', userData.displayName);
  }

  private async processNFCPayment(paymentData: NFCPaymentData): Promise<any> {
    // Integration with payment service
    const response = await fetch('/api/v1/payments/nfc/merchant/payment', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        paymentId: paymentData.paymentId,
        merchantId: paymentData.merchantId,
        customerId: await this.getCurrentUserId(),
        amount: paymentData.amount,
        currency: paymentData.currency,
        description: paymentData.description,
        orderId: paymentData.orderId,
        timestamp: new Date(paymentData.timestamp).toISOString(),
        signature: paymentData.signature,
        deviceId: await this.getDeviceId(),
        nfcSessionId: await this.getCurrentSessionId(),
        nfcProtocolVersion: '1.0',
        deviceFingerprint: await this.getDeviceFingerprint(),
        latitude: await this.getLatitude(),
        longitude: await this.getLongitude(),
        locationAccuracy: await this.getLocationAccuracy()
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Payment processing failed');
    }
    
    return response.json();
  }

  private async processP2PTransfer(transferData: NFCTransferData, recipientId: string): Promise<any> {
    // Integration with NFC P2P transfer service
    const response = await fetch('/api/v1/payments/nfc/p2p/transfer', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        transferId: transferData.transferId,
        senderId: transferData.senderId,
        recipientId: recipientId,
        amount: transferData.amount,
        currency: transferData.currency,
        message: transferData.message,
        timestamp: new Date(transferData.timestamp).toISOString(),
        signature: transferData.signature,
        senderDeviceId: await this.getDeviceId(),
        recipientDeviceId: await this.getRecipientDeviceId(),
        nfcSessionId: await this.getCurrentSessionId(),
        nfcProtocolVersion: '1.0',
        senderDeviceFingerprint: await this.getDeviceFingerprint(),
        senderLatitude: await this.getLatitude(),
        senderLongitude: await this.getLongitude(),
        locationAccuracy: await this.getLocationAccuracy(),
        transferType: 'INSTANT'
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Transfer processing failed');
    }
    
    return response.json();
  }

  private async addContact(contactData: any): Promise<void> {
    // Integration with NFC contact exchange service
    const response = await fetch('/api/v1/payments/nfc/contact/exchange', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        userId: await this.getCurrentUserId(),
        displayName: await this.getCurrentUserName(),
        contactUserId: contactData.userId,
        contactDisplayName: contactData.displayName,
        avatarUrl: await this.getCurrentUserAvatar(),
        contactAvatarUrl: contactData.avatar,
        publicKey: await this.getPublicKey(),
        contactPublicKey: contactData.publicKey,
        timestamp: new Date().toISOString(),
        signature: contactData.signature,
        contactSignature: contactData.contactSignature,
        deviceId: await this.getDeviceId(),
        contactDeviceId: contactData.deviceId,
        nfcSessionId: await this.getCurrentSessionId(),
        nfcProtocolVersion: '1.0',
        sharePhoneNumber: true,
        shareEmail: true,
        allowPaymentRequests: true,
        allowDirectPayments: true,
        latitude: await this.getLatitude(),
        longitude: await this.getLongitude(),
        locationAccuracy: await this.getLocationAccuracy()
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Contact exchange failed');
    }
  }

  private generatePaymentId(): string {
    return `NFC_PAY_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private generateTransferId(): string {
    return `NFC_TXF_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private async getPublicKey(): Promise<string> {
    // Generate public key from private key
    return CryptoJS.SHA256(this.privateKey!).toString();
  }

  private async getCurrentUserId(): Promise<string> {
    const userId = await AsyncStorage.getItem('user_id');
    if (!userId) throw new Error('User not logged in');
    return userId;
  }

  async stopCurrentNFCOperation(): Promise<void> {
    try {
      await NfcManager.unregisterTagEvent();
      await NfcManager.cancelTechnologyRequest();
    } catch (error) {
      // Ignore errors when stopping
    }
  }

  async cleanup(): Promise<void> {
    try {
      await this.stopCurrentNFCOperation();
      await NfcManager.stop();
      this.isInitialized = false;
    } catch (error) {
      console.error('Error during NFC cleanup:', error);
    }
  }

  // Security Methods
  async isNFCSecure(): Promise<boolean> {
    try {
      // Check if device has secure element
      const hasSecureElement = await NfcManager.hasSecureElement();
      return hasSecureElement;
    } catch (error) {
      return false;
    }
  }

  async enableSecureMode(): Promise<boolean> {
    try {
      // Enable hardware-backed security if available
      if (Platform.OS === 'android') {
        return await NfcManager.enableReaderMode();
      }
      return true;
    } catch (error) {
      console.error('Failed to enable secure mode:', error);
      return false;
    }
  }

  // Session Management
  async initializeMerchantSession(merchantId: string, amount?: number, orderId?: string): Promise<any> {
    const response = await fetch('/api/v1/payments/nfc/merchant/session', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        merchantId,
        amount,
        currency: 'USD',
        orderId,
        deviceId: await this.getDeviceId(),
        nfcProtocolVersion: '1.0',
        sessionTimeoutMinutes: 10,
        latitude: await this.getLatitude(),
        longitude: await this.getLongitude(),
        locationAccuracy: await this.getLocationAccuracy(),
        securityLevel: 'HIGH',
        requireBiometric: true,
        requirePin: false
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Session initialization failed');
    }
    
    const sessionData = await response.json();
    await AsyncStorage.setItem('nfc_session_id', sessionData.sessionId);
    await AsyncStorage.setItem('nfc_session_token', sessionData.sessionToken);
    
    return sessionData;
  }

  async initializeP2PSession(userId: string, displayName: string): Promise<any> {
    const response = await fetch('/api/v1/payments/nfc/p2p/session', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        userId,
        displayName,
        avatarUrl: await this.getCurrentUserAvatar(),
        deviceId: await this.getDeviceId(),
        nfcProtocolVersion: '1.0',
        sessionTimeoutMinutes: 15,
        maxTransferAmount: 5000.00,
        maxTransferCount: 5,
        latitude: await this.getLatitude(),
        longitude: await this.getLongitude(),
        locationAccuracy: await this.getLocationAccuracy(),
        securityLevel: 'HIGH',
        requireBiometric: true,
        allowContactSharing: true,
        autoAcceptFromContacts: false
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'P2P session initialization failed');
    }
    
    const sessionData = await response.json();
    await AsyncStorage.setItem('nfc_session_id', sessionData.sessionId);
    await AsyncStorage.setItem('nfc_session_token', sessionData.sessionToken);
    
    return sessionData;
  }

  async validateNFCSignature(data: string, signature: string, publicKey: string, dataType: string): Promise<any> {
    const response = await fetch('/api/v1/payments/nfc/validate/signature', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        data,
        signature,
        publicKey,
        dataType,
        algorithm: 'ECDSA',
        timestamp: Date.now(),
        deviceId: await this.getDeviceId(),
        userId: await this.getCurrentUserId()
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Signature validation failed');
    }
    
    return response.json();
  }

  async getTransactionStatus(transactionId: string): Promise<any> {
    const response = await fetch(`/api/v1/payments/nfc/transaction/${transactionId}/status`, {
      headers: { 
        'Authorization': `Bearer ${await this.getAuthToken()}`
      }
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to get transaction status');
    }
    
    return response.json();
  }

  async getPaymentHistory(page: number = 0, size: number = 20, transactionType?: string): Promise<any> {
    const userId = await this.getCurrentUserId();
    const params = new URLSearchParams({
      userId,
      page: page.toString(),
      size: size.toString()
    });
    
    if (transactionType) {
      params.append('transactionType', transactionType);
    }
    
    const response = await fetch(`/api/v1/payments/nfc/history?${params}`, {
      headers: { 
        'Authorization': `Bearer ${await this.getAuthToken()}`
      }
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to get payment history');
    }
    
    return response.json();
  }

  async cancelTransaction(transactionId: string, reason: string): Promise<any> {
    const response = await fetch(`/api/v1/payments/nfc/transaction/${transactionId}/cancel`, {
      method: 'PATCH',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${await this.getAuthToken()}`
      },
      body: JSON.stringify({
        reason,
        cancelledBy: await this.getCurrentUserId(),
        timestamp: new Date().toISOString()
      })
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to cancel transaction');
    }
    
    return response.json();
  }

  async getDeviceCapabilities(): Promise<any> {
    const deviceId = await this.getDeviceId();
    const platform = Platform.OS;
    
    const response = await fetch(`/api/v1/payments/nfc/device/capabilities?deviceId=${deviceId}&platform=${platform}`, {
      headers: { 
        'Authorization': `Bearer ${await this.getAuthToken()}`
      }
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to get device capabilities');
    }
    
    return response.json();
  }

  // Helper methods for device and user information
  private async getAuthToken(): Promise<string> {
    const token = await AsyncStorage.getItem('auth_token');
    if (!token) throw new Error('Authentication token not found');
    return token;
  }

  private async getDeviceId(): Promise<string> {
    let deviceId = await AsyncStorage.getItem('device_id');
    if (!deviceId) {
      deviceId = `${Platform.OS}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
      await AsyncStorage.setItem('device_id', deviceId);
    }
    return deviceId;
  }

  private async getCurrentSessionId(): Promise<string | null> {
    return await AsyncStorage.getItem('nfc_session_id');
  }

  private async getCurrentUserName(): Promise<string> {
    const userName = await AsyncStorage.getItem('user_display_name');
    return userName || 'Unknown User';
  }

  private async getCurrentUserAvatar(): Promise<string> {
    const avatar = await AsyncStorage.getItem('user_avatar_url');
    return avatar || '';
  }

  private async getDeviceFingerprint(): Promise<string> {
    // Generate device fingerprint based on device characteristics
    const deviceInfo = {
      platform: Platform.OS,
      version: Platform.Version,
      timestamp: Date.now()
    };
    return CryptoJS.SHA256(JSON.stringify(deviceInfo)).toString();
  }

  private async getRecipientDeviceId(): Promise<string> {
    // This would be obtained from the NFC data exchange
    return await AsyncStorage.getItem('recipient_device_id') || '';
  }

  private async getLatitude(): Promise<number | undefined> {
    // Implementation would get current location
    // For now, return undefined to indicate location not available
    return undefined;
  }

  private async getLongitude(): Promise<number | undefined> {
    // Implementation would get current location
    return undefined;
  }

  private async getLocationAccuracy(): Promise<string | undefined> {
    // Implementation would get location accuracy
    return undefined;
  }

  // Enhanced NFC Hardware Integration
  async enableHCE(): Promise<boolean> {
    try {
      if (Platform.OS === 'android') {
        // Enable Host Card Emulation
        const hceEnabled = await NfcManager.enableHCE();
        return hceEnabled;
      }
      return false;
    } catch (error) {
      console.error('Failed to enable HCE:', error);
      return false;
    }
  }

  async enableReaderMode(protocols: string[] = ['ISO14443A', 'ISO14443B', 'ISO15693']): Promise<boolean> {
    try {
      if (Platform.OS === 'android') {
        const readerEnabled = await NfcManager.enableReaderMode({
          protocols,
          isReaderModeEnabled: true
        });
        return readerEnabled;
      }
      return false;
    } catch (error) {
      console.error('Failed to enable reader mode:', error);
      return false;
    }
  }

  async checkSecureElement(): Promise<boolean> {
    try {
      const hasSecureElement = await NfcManager.hasSecureElement();
      const isSecureElementEnabled = await NfcManager.isSecureElementEnabled();
      return hasSecureElement && isSecureElementEnabled;
    } catch (error) {
      console.error('Failed to check secure element:', error);
      return false;
    }
  }

  // iOS Core NFC Integration
  async enableIOSNFC(): Promise<boolean> {
    try {
      if (Platform.OS === 'ios') {
        // iOS-specific NFC initialization
        const supported = await NfcManager.isSupported();
        if (supported) {
          await NfcManager.start();
          return true;
        }
      }
      return false;
    } catch (error) {
      console.error('Failed to enable iOS NFC:', error);
      return false;
    }
  }

  // Advanced Security Features
  async enableBiometricProtection(): Promise<boolean> {
    try {
      // Implementation would integrate with biometric authentication
      // For now, return true to indicate feature is available
      return true;
    } catch (error) {
      console.error('Failed to enable biometric protection:', error);
      return false;
    }
  }

  async validateDeviceIntegrity(): Promise<boolean> {
    try {
      // Check if device is rooted/jailbroken
      // Check if app is running in secure environment
      // For now, return true
      return true;
    } catch (error) {
      console.error('Failed to validate device integrity:', error);
      return false;
    }
  }
}

export default new NFCPaymentService();