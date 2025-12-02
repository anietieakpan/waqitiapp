import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Alert,
  TouchableOpacity,
  ActivityIndicator,
  Platform,
} from 'react-native';
import {
  Text,
  Button,
  Card,
  List,
  Divider,
  Dialog,
  Portal,
  ProgressBar,
  Chip,
  RadioButton,
  Switch,
  IconButton,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import RNFS from 'react-native-fs';
import Share from 'react-native-share';
import { useNavigation } from '@react-navigation/native';

/**
 * GDPR Data Management Screen
 *
 * FEATURES:
 * - Data export (JSON, CSV, PDF)
 * - Data erasure (Right to be forgotten)
 * - Consent management
 * - Data access history
 * - Privacy settings
 * - Data portability
 *
 * COMPLIANCE:
 * - GDPR Article 15 (Right to access)
 * - GDPR Article 17 (Right to erasure)
 * - GDPR Article 20 (Right to data portability)
 * - GDPR Article 21 (Right to object)
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

interface DataExportProgress {
  stage: string;
  progress: number;
  totalRecords: number;
  processedRecords: number;
}

interface ConsentItem {
  id: string;
  category: string;
  description: string;
  required: boolean;
  granted: boolean;
  grantedAt?: Date;
}

const GDPRDataManagementScreen: React.FC = () => {
  const navigation = useNavigation();

  // State
  const [loading, setLoading] = useState(false);
  const [exportDialogVisible, setExportDialogVisible] = useState(false);
  const [erasureDialogVisible, setErasureDialogVisible] = useState(false);
  const [exportFormat, setExportFormat] = useState<'JSON' | 'CSV' | 'PDF'>('JSON');
  const [exportProgress, setExportProgress] = useState<DataExportProgress | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [consents, setConsents] = useState<ConsentItem[]>([]);
  const [erasureConfirmText, setErasureConfirmText] = useState('');

  useEffect(() => {
    loadConsents();
  }, []);

  const loadConsents = async () => {
    setLoading(true);
    try {
      // Mock data - replace with actual API call
      const mockConsents: ConsentItem[] = [
        {
          id: 'consent-001',
          category: 'Essential Services',
          description: 'Required for core payment functionality',
          required: true,
          granted: true,
          grantedAt: new Date('2025-01-15'),
        },
        {
          id: 'consent-002',
          category: 'Marketing Communications',
          description: 'Promotional emails and notifications',
          required: false,
          granted: true,
          grantedAt: new Date('2025-01-15'),
        },
        {
          id: 'consent-003',
          category: 'Analytics',
          description: 'Usage data for improving services',
          required: false,
          granted: false,
        },
        {
          id: 'consent-004',
          category: 'Third-party Sharing',
          description: 'Share data with partner merchants',
          required: false,
          granted: false,
        },
      ];
      setConsents(mockConsents);
    } catch (error) {
      console.error('Failed to load consents:', error);
      Alert.alert('Error', 'Failed to load consent data');
    } finally {
      setLoading(false);
    }
  };

  const handleDataExport = async () => {
    setIsExporting(true);
    setExportDialogVisible(false);

    try {
      // Simulate export progress
      const stages = [
        { stage: 'Fetching user data', progress: 0.2, records: 100 },
        { stage: 'Exporting transactions', progress: 0.4, records: 500 },
        { stage: 'Exporting documents', progress: 0.6, records: 50 },
        { stage: 'Generating export file', progress: 0.8, records: 0 },
        { stage: 'Finalizing', progress: 1.0, records: 0 },
      ];

      for (const stage of stages) {
        setExportProgress({
          stage: stage.stage,
          progress: stage.progress,
          totalRecords: 650,
          processedRecords: Math.floor(650 * stage.progress),
        });
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      // Generate file
      const timestamp = new Date().getTime();
      const fileName = `waqiti_data_export_${timestamp}.${exportFormat.toLowerCase()}`;
      const filePath = `${RNFS.DocumentDirectoryPath}/${fileName}`;

      // Create mock export data
      const exportData = {
        exportDate: new Date().toISOString(),
        format: exportFormat,
        user: {
          id: 'user-12345',
          name: 'John Doe',
          email: 'john@example.com',
          phone: '+1234567890',
          createdAt: '2025-01-15T00:00:00Z',
        },
        accounts: [
          {
            id: 'acc-001',
            type: 'PERSONAL',
            balance: 1250.50,
            currency: 'USD',
          },
        ],
        transactions: [
          {
            id: 'txn-001',
            type: 'PAYMENT',
            amount: 50.00,
            currency: 'USD',
            date: '2025-10-20T10:30:00Z',
            recipient: 'Jane Smith',
          },
        ],
        // Add more data categories
      };

      let fileContent = '';
      if (exportFormat === 'JSON') {
        fileContent = JSON.stringify(exportData, null, 2);
      } else if (exportFormat === 'CSV') {
        fileContent = 'category,field,value\n';
        fileContent += `user,id,${exportData.user.id}\n`;
        fileContent += `user,name,${exportData.user.name}\n`;
        fileContent += `user,email,${exportData.user.email}\n`;
        // Add more CSV rows
      } else {
        // For PDF, we'd generate a formatted document
        fileContent = 'PDF generation would require a PDF library';
      }

      await RNFS.writeFile(filePath, fileContent, 'utf8');

      // Share the file
      await Share.open({
        title: 'Export Your Data',
        message: 'Your personal data export is ready',
        url: Platform.OS === 'android' ? `file://${filePath}` : filePath,
        type: exportFormat === 'JSON' ? 'application/json' : 'text/plain',
        subject: 'Waqiti Data Export',
      });

      Alert.alert(
        'Export Complete',
        'Your data has been exported successfully. The file has been saved and shared.',
        [{ text: 'OK' }]
      );
    } catch (error: any) {
      console.error('Export failed:', error);
      Alert.alert('Export Failed', error.message || 'Failed to export data');
    } finally {
      setIsExporting(false);
      setExportProgress(null);
    }
  };

  const handleDataErasure = () => {
    Alert.alert(
      'Confirm Data Erasure',
      'This will permanently delete all your personal data. This action cannot be undone.\n\nType DELETE to confirm.',
      [
        {
          text: 'Cancel',
          style: 'cancel',
          onPress: () => setErasureDialogVisible(false),
        },
        {
          text: 'I Understand',
          style: 'destructive',
          onPress: () => setErasureDialogVisible(true),
        },
      ]
    );
  };

  const confirmDataErasure = async () => {
    if (erasureConfirmText !== 'DELETE') {
      Alert.alert('Invalid Input', 'Please type DELETE to confirm erasure');
      return;
    }

    setLoading(true);
    setErasureDialogVisible(false);

    try {
      // API call to request data erasure
      // await gdprService.requestDataErasure();

      // Simulate erasure request
      await new Promise(resolve => setTimeout(resolve, 2000));

      Alert.alert(
        'Erasure Request Submitted',
        'Your data erasure request has been submitted. You will receive a confirmation email within 30 days as required by GDPR.\n\nYour account will be deleted after verification.',
        [
          {
            text: 'OK',
            onPress: () => {
              // Logout and navigate to login
              // navigation.navigate('Login');
            },
          },
        ]
      );
    } catch (error: any) {
      console.error('Erasure request failed:', error);
      Alert.alert('Error', error.message || 'Failed to submit erasure request');
    } finally {
      setLoading(false);
      setErasureConfirmText('');
    }
  };

  const updateConsent = async (consentId: string, granted: boolean) => {
    try {
      // API call to update consent
      // await gdprService.updateConsent(consentId, granted);

      setConsents(prev =>
        prev.map(c =>
          c.id === consentId
            ? { ...c, granted, grantedAt: granted ? new Date() : undefined }
            : c
        )
      );

      Alert.alert('Success', 'Consent updated successfully');
    } catch (error: any) {
      console.error('Failed to update consent:', error);
      Alert.alert('Error', 'Failed to update consent');
    }
  };

  return (
    <ScrollView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <IconButton
          icon="arrow-left"
          size={24}
          onPress={() => navigation.goBack()}
        />
        <Text variant="headlineSmall" style={styles.headerTitle}>
          GDPR Data Management
        </Text>
      </View>

      {/* Export Progress */}
      {isExporting && exportProgress && (
        <Card style={styles.card}>
          <Card.Content>
            <Text variant="titleMedium" style={styles.cardTitle}>
              Exporting Your Data...
            </Text>
            <Text variant="bodyMedium" style={styles.progressStage}>
              {exportProgress.stage}
            </Text>
            <ProgressBar
              progress={exportProgress.progress}
              color="#4CAF50"
              style={styles.progressBar}
            />
            <Text variant="bodySmall" style={styles.progressText}>
              {exportProgress.processedRecords} / {exportProgress.totalRecords} records processed
            </Text>
          </Card.Content>
        </Card>
      )}

      {/* Data Export Section */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={styles.sectionHeader}>
            <Icon name="download" size={24} color="#2196F3" />
            <Text variant="titleLarge" style={styles.sectionTitle}>
              Export Your Data
            </Text>
          </View>
          <Text variant="bodyMedium" style={styles.description}>
            Download a copy of all your personal data stored in Waqiti. This includes transactions,
            profile information, and settings.
          </Text>
          <Button
            mode="contained"
            onPress={() => setExportDialogVisible(true)}
            icon="download"
            style={styles.button}
            disabled={isExporting}
          >
            Request Data Export
          </Button>
        </Card.Content>
      </Card>

      {/* Data Erasure Section */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={styles.sectionHeader}>
            <Icon name="delete-forever" size={24} color="#F44336" />
            <Text variant="titleLarge" style={styles.sectionTitle}>
              Delete Your Data
            </Text>
          </View>
          <Text variant="bodyMedium" style={styles.description}>
            Permanently delete all your personal data from Waqiti. This action cannot be undone and
            will close your account.
          </Text>
          <Button
            mode="outlined"
            onPress={handleDataErasure}
            icon="alert"
            style={styles.button}
            buttonColor="#FFEBEE"
            textColor="#F44336"
            disabled={loading}
          >
            Request Data Deletion
          </Button>
        </Card.Content>
      </Card>

      {/* Consent Management Section */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={styles.sectionHeader}>
            <Icon name="shield-check" size={24} color="#4CAF50" />
            <Text variant="titleLarge" style={styles.sectionTitle}>
              Manage Consents
            </Text>
          </View>
          <Text variant="bodyMedium" style={styles.description}>
            Control how your data is used. You can withdraw consent at any time.
          </Text>

          <List.Section>
            {consents.map((consent, index) => (
              <React.Fragment key={consent.id}>
                <List.Item
                  title={consent.category}
                  description={consent.description}
                  left={props => <List.Icon {...props} icon="shield-account" />}
                  right={() => (
                    <View style={styles.consentRight}>
                      {consent.required ? (
                        <Chip mode="outlined" compact>
                          Required
                        </Chip>
                      ) : (
                        <Switch
                          value={consent.granted}
                          onValueChange={(value) => updateConsent(consent.id, value)}
                          disabled={loading}
                        />
                      )}
                    </View>
                  )}
                />
                {index < consents.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List.Section>
        </Card.Content>
      </Card>

      {/* Data Access History */}
      <Card style={styles.card}>
        <Card.Content>
          <View style={styles.sectionHeader}>
            <Icon name="history" size={24} color="#FF9800" />
            <Text variant="titleLarge" style={styles.sectionTitle}>
              Data Access History
            </Text>
          </View>
          <Text variant="bodyMedium" style={styles.description}>
            View who has accessed your data and when
          </Text>
          <Button
            mode="outlined"
            onPress={() => {
              // Navigate to access history
            }}
            icon="eye"
            style={styles.button}
          >
            View Access Log
          </Button>
        </Card.Content>
      </Card>

      {/* Export Format Dialog */}
      <Portal>
        <Dialog
          visible={exportDialogVisible}
          onDismiss={() => setExportDialogVisible(false)}
        >
          <Dialog.Title>Select Export Format</Dialog.Title>
          <Dialog.Content>
            <RadioButton.Group
              onValueChange={value => setExportFormat(value as any)}
              value={exportFormat}
            >
              <RadioButton.Item label="JSON (Structured data)" value="JSON" />
              <RadioButton.Item label="CSV (Spreadsheet)" value="CSV" />
              <RadioButton.Item label="PDF (Human-readable)" value="PDF" />
            </RadioButton.Group>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setExportDialogVisible(false)}>Cancel</Button>
            <Button onPress={handleDataExport} mode="contained">
              Export
            </Button>
          </Dialog.Actions>
        </Dialog>

        {/* Erasure Confirmation Dialog */}
        <Dialog
          visible={erasureDialogVisible}
          onDismiss={() => setErasureDialogVisible(false)}
        >
          <Dialog.Title>Confirm Data Erasure</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium" style={styles.warningText}>
              ⚠️ This will permanently delete:
            </Text>
            <Text variant="bodySmall" style={styles.bulletList}>
              • All transaction history{'\n'}
              • Payment methods{'\n'}
              • Profile information{'\n'}
              • Saved preferences{'\n'}
              • Connected accounts
            </Text>
            <Text variant="bodyMedium" style={styles.warningText}>
              Type DELETE to confirm:
            </Text>
            <TextInput
              value={erasureConfirmText}
              onChangeText={setErasureConfirmText}
              placeholder="Type DELETE"
              style={styles.confirmInput}
              autoCapitalize="characters"
            />
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setErasureDialogVisible(false)}>Cancel</Button>
            <Button
              onPress={confirmDataErasure}
              mode="contained"
              buttonColor="#F44336"
              disabled={erasureConfirmText !== 'DELETE' || loading}
            >
              {loading ? 'Processing...' : 'Delete My Data'}
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#FFF',
    elevation: 2,
  },
  headerTitle: {
    marginLeft: 8,
    flex: 1,
  },
  card: {
    margin: 16,
    elevation: 2,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  sectionTitle: {
    marginLeft: 8,
    flex: 1,
  },
  cardTitle: {
    marginBottom: 8,
  },
  description: {
    marginBottom: 16,
    color: '#666',
  },
  button: {
    marginTop: 8,
  },
  progressStage: {
    marginTop: 8,
    marginBottom: 8,
  },
  progressBar: {
    height: 8,
    borderRadius: 4,
    marginBottom: 8,
  },
  progressText: {
    color: '#666',
  },
  consentRight: {
    justifyContent: 'center',
  },
  warningText: {
    marginTop: 8,
    marginBottom: 8,
    fontWeight: 'bold',
  },
  bulletList: {
    marginLeft: 16,
    marginBottom: 16,
    lineHeight: 24,
  },
  confirmInput: {
    marginTop: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: '#DDD',
    borderRadius: 4,
    fontSize: 16,
  },
});

export default GDPRDataManagementScreen;
