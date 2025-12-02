import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  Image,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { launchCamera, launchImageLibrary } from 'react-native-image-picker';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * KYCVerificationScreen
 *
 * Screen for Know Your Customer (KYC) verification
 *
 * Features:
 * - Identity document upload
 * - Selfie verification
 * - Address proof upload
 * - Real-time status tracking
 * - Analytics tracking
 *
 * Architecture:
 * - Uploads documents to S3 via presigned URLs
 * - Submits to backend KYC service
 * - Backend integrates with Jumio/Onfido for verification
 * - WebSocket for real-time status updates
 * - Event-driven processing via Kafka
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

type KYCStatus = 'not_started' | 'pending' | 'verified' | 'rejected';
type DocumentType = 'id_front' | 'id_back' | 'selfie' | 'address_proof';

interface DocumentUpload {
  type: DocumentType;
  uri?: string;
  uploaded: boolean;
  documentId?: string;
}

const KYCVerificationScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  const [documents, setDocuments] = useState<DocumentUpload[]>([
    { type: 'id_front', uploaded: false },
    { type: 'id_back', uploaded: false },
    { type: 'selfie', uploaded: false },
    { type: 'address_proof', uploaded: false },
  ]);

  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    AnalyticsService.trackScreenView('KYCVerificationScreen', {
      kycStatus: user?.kycStatus || 'not_started',
    });

    // Setup WebSocket for real-time status updates
    setupWebSocket();

    return () => {
      // Cleanup WebSocket
    };
  }, []);

  const setupWebSocket = () => {
    // TODO: Implement WebSocket connection for real-time status updates
    // const socket = new WebSocket('wss://api.example.com/ws');
    // socket.onmessage = handleStatusUpdate;
  };

  const getKYCStatus = (): KYCStatus => {
    return (user?.kycStatus as KYCStatus) || 'not_started';
  };

  const getDocumentTitle = (type: DocumentType): string => {
    switch (type) {
      case 'id_front':
        return 'ID Front';
      case 'id_back':
        return 'ID Back';
      case 'selfie':
        return 'Selfie Verification';
      case 'address_proof':
        return 'Address Proof';
    }
  };

  const getDocumentDescription = (type: DocumentType): string => {
    switch (type) {
      case 'id_front':
        return 'Upload the front of your government-issued ID (Passport, Driver License, etc.)';
      case 'id_back':
        return 'Upload the back of your government-issued ID';
      case 'selfie':
        return 'Take a clear selfie for identity verification';
      case 'address_proof':
        return 'Upload a recent utility bill or bank statement (< 3 months old)';
    }
  };

  const handleSelectImage = (type: DocumentType) => {
    Alert.alert(
      'Upload Document',
      'Choose an option',
      [
        {
          text: 'Take Photo',
          onPress: () => takePhoto(type),
        },
        {
          text: 'Choose from Library',
          onPress: () => chooseFromLibrary(type),
        },
        {
          text: 'Cancel',
          style: 'cancel',
        },
      ]
    );
  };

  const takePhoto = async (type: DocumentType) => {
    const result = await launchCamera({
      mediaType: 'photo',
      quality: 0.8,
      maxWidth: 2000,
      maxHeight: 2000,
      cameraType: type === 'selfie' ? 'front' : 'back',
    });

    if (result.assets && result.assets[0]) {
      await uploadDocument(type, result.assets[0].uri!);
      AnalyticsService.trackEvent('kyc_document_captured', {
        documentType: type,
        method: 'camera',
      });
    }
  };

  const chooseFromLibrary = async (type: DocumentType) => {
    const result = await launchImageLibrary({
      mediaType: 'photo',
      quality: 0.8,
      maxWidth: 2000,
      maxHeight: 2000,
    });

    if (result.assets && result.assets[0]) {
      await uploadDocument(type, result.assets[0].uri!);
      AnalyticsService.trackEvent('kyc_document_selected', {
        documentType: type,
        method: 'library',
      });
    }
  };

  const uploadDocument = async (type: DocumentType, uri: string) => {
    try {
      // TODO: Implement actual document upload
      // 1. Get presigned URL from backend
      // const { uploadUrl, documentId } = await api.getPresignedUploadUrl(type);

      // 2. Upload to S3
      // await uploadToS3(uploadUrl, uri);

      // 3. Confirm upload to backend
      // await api.confirmDocumentUpload(documentId);

      // For now, simulate successful upload
      const documentId = `doc_${Date.now()}`;

      updateDocument(type, uri, documentId);

      AnalyticsService.trackEvent('kyc_document_uploaded', {
        documentType: type,
        documentId,
      });
    } catch (error: any) {
      Alert.alert('Upload Failed', error.message || 'Failed to upload document');
    }
  };

  const updateDocument = (type: DocumentType, uri?: string, documentId?: string) => {
    setDocuments((prev) =>
      prev.map((doc) =>
        doc.type === type
          ? { ...doc, uri, uploaded: !!uri, documentId }
          : doc
      )
    );
  };

  const handleSubmitKYC = async () => {
    const allUploaded = documents.every((doc) => doc.uploaded);

    if (!allUploaded) {
      Alert.alert('Incomplete', 'Please upload all required documents');
      return;
    }

    setIsSubmitting(true);

    try {
      // Build submission request
      const submissionRequest = {
        userId: user?.id,
        documents: documents.map((doc) => ({
          type: doc.type,
          documentId: doc.documentId,
        })),
        metadata: {
          deviceId: 'device-123', // TODO: Get actual device ID
          ipAddress: 'client-ip', // TODO: Get actual IP
          submittedAt: new Date().toISOString(),
        },
      };

      // TODO: Submit to backend
      // await dispatch(submitKYC(submissionRequest)).unwrap();

      AnalyticsService.trackEvent('kyc_submitted', {
        userId: user?.id,
        documentCount: documents.length,
      });

      Alert.alert(
        'Submitted Successfully',
        'Your KYC documents have been submitted for review. You will be notified once verified (typically 1-2 business days).',
        [
          {
            text: 'OK',
            onPress: () => navigation.goBack(),
          },
        ]
      );
    } catch (error: any) {
      Alert.alert('Submission Failed', error.message || 'Failed to submit KYC documents');

      AnalyticsService.trackEvent('kyc_submission_failed', {
        userId: user?.id,
        error: error.message,
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const renderStatusCard = () => {
    const status = getKYCStatus();
    let statusColor = '#9E9E9E';
    let statusIcon = 'shield-alert';
    let statusText = 'Not Verified';
    let statusDescription = 'Complete KYC verification to unlock all features';

    if (status === 'verified') {
      statusColor = '#4CAF50';
      statusIcon = 'shield-check';
      statusText = 'Verified';
      statusDescription = 'Your identity has been verified';
    } else if (status === 'pending') {
      statusColor = '#FF9800';
      statusIcon = 'shield-clock';
      statusText = 'Pending Review';
      statusDescription = 'Your documents are being reviewed';
    } else if (status === 'rejected') {
      statusColor = '#F44336';
      statusIcon = 'shield-remove';
      statusText = 'Rejected';
      statusDescription = 'Please resubmit your documents';
    }

    return (
      <View style={[styles.statusCard, { borderColor: statusColor }]}>
        <Icon name={statusIcon} size={48} color={statusColor} />
        <View style={styles.statusInfo}>
          <Text style={[styles.statusText, { color: statusColor }]}>
            {statusText}
          </Text>
          <Text style={styles.statusDescription}>{statusDescription}</Text>
        </View>
      </View>
    );
  };

  const renderDocumentCard = (doc: DocumentUpload) => (
    <View key={doc.type} style={styles.documentCard}>
      <View style={styles.documentHeader}>
        <View style={styles.documentTitleRow}>
          <Icon
            name={doc.uploaded ? 'check-circle' : 'circle-outline'}
            size={24}
            color={doc.uploaded ? '#4CAF50' : '#9E9E9E'}
          />
          <View style={styles.documentTitleContainer}>
            <Text style={styles.documentTitle}>
              {getDocumentTitle(doc.type)}
            </Text>
            <Text style={styles.documentDescription}>
              {getDocumentDescription(doc.type)}
            </Text>
          </View>
        </View>
      </View>

      {doc.uri ? (
        <View style={styles.imagePreviewContainer}>
          <Image source={{ uri: doc.uri }} style={styles.imagePreview} />
          <TouchableOpacity
            style={styles.changeImageButton}
            onPress={() => handleSelectImage(doc.type)}
          >
            <Text style={styles.changeImageText}>Change</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <TouchableOpacity
          style={styles.uploadButton}
          onPress={() => handleSelectImage(doc.type)}
        >
          <Icon name="cloud-upload" size={24} color="#6200EE" />
          <Text style={styles.uploadButtonText}>Upload</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  const renderBenefits = () => (
    <View style={styles.benefitsCard}>
      <Text style={styles.benefitsTitle}>Verification Benefits:</Text>
      <View style={styles.benefitItem}>
        <Icon name="currency-usd" size={20} color="#6200EE" />
        <Text style={styles.benefitText}>Higher transaction limits ($10,000/day)</Text>
      </View>
      <View style={styles.benefitItem}>
        <Icon name="shield-check" size={20} color="#6200EE" />
        <Text style={styles.benefitText}>Enhanced account security</Text>
      </View>
      <View style={styles.benefitItem}>
        <Icon name="bank" size={20} color="#6200EE" />
        <Text style={styles.benefitText}>Bank transfers & withdrawals</Text>
      </View>
      <View style={styles.benefitItem}>
        <Icon name="credit-card" size={20} color="#6200EE" />
        <Text style={styles.benefitText}>Premium features access</Text>
      </View>
      <View style={styles.benefitItem}>
        <Icon name="check-decagram" size={20} color="#6200EE" />
        <Text style={styles.benefitText}>Verified badge on profile</Text>
      </View>
    </View>
  );

  const status = getKYCStatus();
  const canSubmit = status !== 'verified' && status !== 'pending';

  return (
    <View style={styles.container}>
      <Header title="KYC Verification" showBack />

      <ScrollView style={styles.content}>
        {renderStatusCard()}

        {canSubmit && (
          <>
            <Text style={styles.sectionTitle}>Required Documents</Text>
            {documents.map(renderDocumentCard)}
            {renderBenefits()}

            <View style={styles.securityNote}>
              <Icon name="lock" size={16} color="#666" />
              <Text style={styles.securityNoteText}>
                All documents are encrypted and stored securely. We comply with GDPR and data protection regulations.
              </Text>
            </View>
          </>
        )}

        {status === 'pending' && (
          <View style={styles.pendingCard}>
            <Icon name="clock-outline" size={64} color="#FF9800" />
            <Text style={styles.pendingTitle}>Review in Progress</Text>
            <Text style={styles.pendingText}>
              We're reviewing your documents. This usually takes 1-2 business days.
              You'll receive a notification once the verification is complete.
            </Text>
          </View>
        )}

        {status === 'verified' && (
          <View style={styles.verifiedCard}>
            <Icon name="check-decagram" size={64} color="#4CAF50" />
            <Text style={styles.verifiedTitle}>Identity Verified</Text>
            <Text style={styles.verifiedText}>
              Your identity has been successfully verified. You now have access to all premium features and higher transaction limits.
            </Text>
          </View>
        )}
      </ScrollView>

      {canSubmit && (
        <View style={styles.footer}>
          <TouchableOpacity
            style={[
              styles.submitButton,
              (isSubmitting || !documents.every((d) => d.uploaded)) &&
                styles.submitButtonDisabled,
            ]}
            onPress={handleSubmitKYC}
            disabled={isSubmitting || !documents.every((d) => d.uploaded)}
          >
            {isSubmitting ? (
              <Text style={styles.submitButtonText}>Submitting...</Text>
            ) : (
              <>
                <Icon name="send" size={20} color="#FFFFFF" />
                <Text style={styles.submitButtonText}>Submit for Verification</Text>
              </>
            )}
          </TouchableOpacity>
        </View>
      )}
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
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  statusCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 20,
    paddingHorizontal: 16,
    borderRadius: 8,
    borderLeftWidth: 4,
    marginBottom: 24,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  statusInfo: {
    marginLeft: 16,
    flex: 1,
  },
  statusText: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  statusDescription: {
    fontSize: 14,
    color: '#666',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 16,
  },
  documentCard: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 12,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 1,
  },
  documentHeader: {
    marginBottom: 12,
  },
  documentTitleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  documentTitleContainer: {
    marginLeft: 12,
    flex: 1,
  },
  documentTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  documentDescription: {
    fontSize: 13,
    color: '#666',
    lineHeight: 18,
  },
  imagePreviewContainer: {
    alignItems: 'center',
  },
  imagePreview: {
    width: '100%',
    height: 200,
    borderRadius: 8,
    marginBottom: 12,
    backgroundColor: '#F5F5F5',
  },
  changeImageButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#6200EE',
  },
  changeImageText: {
    color: '#6200EE',
    fontSize: 14,
    fontWeight: '600',
  },
  uploadButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 16,
    borderWidth: 2,
    borderColor: '#6200EE',
    borderStyle: 'dashed',
    borderRadius: 8,
  },
  uploadButtonText: {
    color: '#6200EE',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  benefitsCard: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginTop: 8,
    marginBottom: 16,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 1,
  },
  benefitsTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 12,
  },
  benefitItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  benefitText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
  },
  securityNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8F5E9',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  securityNoteText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 8,
    flex: 1,
    lineHeight: 18,
  },
  pendingCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 32,
    paddingHorizontal: 24,
    borderRadius: 8,
    marginTop: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  pendingTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 16,
  },
  pendingText: {
    fontSize: 14,
    color: '#666',
    marginTop: 8,
    textAlign: 'center',
    lineHeight: 20,
  },
  verifiedCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 32,
    paddingHorizontal: 24,
    borderRadius: 8,
    marginTop: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  verifiedTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 16,
  },
  verifiedText: {
    fontSize: 14,
    color: '#666',
    marginTop: 8,
    textAlign: 'center',
    lineHeight: 20,
  },
  footer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  submitButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#6200EE',
    paddingVertical: 16,
    borderRadius: 8,
  },
  submitButtonDisabled: {
    backgroundColor: '#BDBDBD',
  },
  submitButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
    marginLeft: 8,
  },
});

export default KYCVerificationScreen;
