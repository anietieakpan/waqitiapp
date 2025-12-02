/**
 * Tax Documents Screen
 * Allows users to generate, view, and share tax documents for their investment and crypto transactions
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  ScrollView,
  Text,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  StyleSheet,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { Picker } from '@react-native-picker/picker';
import { useFocusEffect } from '@react-navigation/native';
import TaxDocumentService, {
  TaxDocument,
  TaxDocumentType,
  TaxYear,
} from '../../services/tax/TaxDocumentService';
import { theme } from '../../theme';
import { format } from 'date-fns';
import FileViewer from 'react-native-file-viewer';

interface TaxDocumentsScreenProps {
  navigation: any;
}

interface DocumentTypeOption {
  type: TaxDocumentType;
  label: string;
  description: string;
  icon: string;
}

const DOCUMENT_TYPES: DocumentTypeOption[] = [
  {
    type: TaxDocumentType.FORM_1099_B,
    label: 'Form 1099-B',
    description: 'Proceeds from cryptocurrency and investment sales',
    icon: 'description',
  },
  {
    type: TaxDocumentType.FORM_1099_MISC,
    label: 'Form 1099-MISC',
    description: 'Miscellaneous income including rewards and bonuses',
    icon: 'monetization-on',
  },
  {
    type: TaxDocumentType.FORM_1099_INT,
    label: 'Form 1099-INT',
    description: 'Interest income from savings and investments',
    icon: 'account-balance',
  },
  {
    type: TaxDocumentType.CAPITAL_GAINS_REPORT,
    label: 'Capital Gains Report',
    description: 'Detailed breakdown of gains and losses',
    icon: 'trending-up',
  },
  {
    type: TaxDocumentType.CRYPTO_GAINS_LOSSES,
    label: 'Crypto Gains/Losses',
    description: 'Cryptocurrency-specific tax report',
    icon: 'currency-bitcoin',
  },
  {
    type: TaxDocumentType.TRANSACTION_HISTORY,
    label: 'Transaction History',
    description: 'Complete record of all taxable transactions',
    icon: 'history',
  },
];

export const TaxDocumentsScreen: React.FC<TaxDocumentsScreenProps> = ({ navigation }) => {
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear() - 1);
  const [selectedTypes, setSelectedTypes] = useState<TaxDocumentType[]>([
    TaxDocumentType.FORM_1099_B,
    TaxDocumentType.CAPITAL_GAINS_REPORT,
  ]);
  const [documents, setDocuments] = useState<TaxDocument[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  // Load existing documents on mount and focus
  useFocusEffect(
    useCallback(() => {
      loadDocuments();
    }, [selectedYear])
  );

  const loadDocuments = async () => {
    try {
      setIsLoading(true);
      const docs = await TaxDocumentService.getDocumentsForYear(selectedYear);
      setDocuments(docs);
    } catch (error) {
      console.error('Failed to load documents:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGenerateDocuments = async () => {
    if (selectedTypes.length === 0) {
      Alert.alert('Select Documents', 'Please select at least one document type to generate.');
      return;
    }

    Alert.alert(
      'Generate Tax Documents',
      `Generate ${selectedTypes.length} document(s) for tax year ${selectedYear}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Generate',
          onPress: async () => {
            try {
              setIsGenerating(true);
              
              const generatedDocs = await TaxDocumentService.generateTaxDocuments(
                selectedYear,
                selectedTypes
              );

              Alert.alert(
                'Success',
                `Generated ${generatedDocs.length} tax document(s) successfully!`,
                [{ text: 'OK', onPress: () => loadDocuments() }]
              );
            } catch (error) {
              Alert.alert(
                'Error',
                'Failed to generate tax documents. Please try again.',
                [{ text: 'OK' }]
              );
              console.error('Failed to generate documents:', error);
            } finally {
              setIsGenerating(false);
            }
          },
        },
      ]
    );
  };

  const handleViewDocument = async (document: TaxDocument) => {
    try {
      await FileViewer.open(document.filePath, {
        showOpenWithDialog: true,
        displayName: `${document.type}_${document.taxYear}`,
      });
    } catch (error) {
      console.error('Failed to open document:', error);
      Alert.alert('Error', 'Unable to open document. Please try sharing it instead.');
    }
  };

  const handleShareDocument = async (document: TaxDocument) => {
    try {
      await TaxDocumentService.shareDocument(document.id);
    } catch (error) {
      Alert.alert('Error', 'Failed to share document. Please try again.');
    }
  };

  const handleDeleteDocument = async (document: TaxDocument) => {
    Alert.alert(
      'Delete Document',
      `Are you sure you want to delete this ${document.type} document?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              await TaxDocumentService.deleteDocument(document.id);
              await loadDocuments();
            } catch (error) {
              Alert.alert('Error', 'Failed to delete document.');
            }
          },
        },
      ]
    );
  };

  const toggleDocumentType = (type: TaxDocumentType) => {
    setSelectedTypes(prev => {
      if (prev.includes(type)) {
        return prev.filter(t => t !== type);
      }
      return [...prev, type];
    });
  };

  const getAvailableYears = (): number[] => {
    const currentYear = new Date().getFullYear();
    const years: number[] = [];
    for (let year = currentYear - 1; year >= currentYear - 5; year--) {
      years.push(year);
    }
    return years;
  };

  const renderDocumentTypeSelector = () => (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Select Document Types</Text>
      <View style={styles.documentTypesGrid}>
        {DOCUMENT_TYPES.map(docType => {
          const isSelected = selectedTypes.includes(docType.type);
          return (
            <TouchableOpacity
              key={docType.type}
              style={[styles.documentTypeCard, isSelected && styles.documentTypeCardSelected]}
              onPress={() => toggleDocumentType(docType.type)}
              activeOpacity={0.7}
            >
              <View style={styles.documentTypeHeader}>
                <MaterialIcons
                  name={docType.icon as any}
                  size={24}
                  color={isSelected ? theme.colors.primary : theme.colors.textLight}
                />
                {isSelected && (
                  <Ionicons
                    name="checkmark-circle"
                    size={20}
                    color={theme.colors.primary}
                    style={styles.checkIcon}
                  />
                )}
              </View>
              <Text style={[styles.documentTypeLabel, isSelected && styles.documentTypeLabelSelected]}>
                {docType.label}
              </Text>
              <Text style={styles.documentTypeDescription}>{docType.description}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );

  const renderExistingDocuments = () => {
    if (documents.length === 0) {
      return (
        <View style={styles.emptyState}>
          <MaterialIcons name="folder-open" size={64} color={theme.colors.textLight} />
          <Text style={styles.emptyStateTitle}>No Documents</Text>
          <Text style={styles.emptyStateText}>
            No tax documents generated for {selectedYear} yet
          </Text>
        </View>
      );
    }

    return (
      <View style={styles.documentsList}>
        {documents.map(doc => (
          <View key={doc.id} style={styles.documentCard}>
            <View style={styles.documentInfo}>
              <Text style={styles.documentTitle}>{doc.type}</Text>
              <Text style={styles.documentDate}>
                Generated on {format(new Date(doc.generatedAt), 'MMM dd, yyyy')}
              </Text>
              <Text style={styles.documentSize}>
                {(doc.fileSize / 1024).toFixed(1)} KB
              </Text>
            </View>
            <View style={styles.documentActions}>
              <TouchableOpacity
                style={styles.actionButton}
                onPress={() => handleViewDocument(doc)}
              >
                <Ionicons name="eye" size={20} color={theme.colors.primary} />
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.actionButton}
                onPress={() => handleShareDocument(doc)}
              >
                <Ionicons name="share" size={20} color={theme.colors.primary} />
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.actionButton}
                onPress={() => handleDeleteDocument(doc)}
              >
                <Ionicons name="trash" size={20} color={theme.colors.error} />
              </TouchableOpacity>
            </View>
          </View>
        ))}
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Text style={styles.title}>Tax Documents</Text>
          <Text style={styles.subtitle}>
            Generate tax documents for your cryptocurrency and investment transactions
          </Text>
        </View>

        <View style={styles.yearSelector}>
          <Text style={styles.yearSelectorLabel}>Tax Year</Text>
          <View style={styles.pickerContainer}>
            <Picker
              selectedValue={selectedYear}
              onValueChange={setSelectedYear}
              style={styles.picker}
            >
              {getAvailableYears().map(year => (
                <Picker.Item key={year} label={year.toString()} value={year} />
              ))}
            </Picker>
          </View>
        </View>

        {renderDocumentTypeSelector()}

        <TouchableOpacity
          style={[styles.generateButton, isGenerating && styles.generateButtonDisabled]}
          onPress={handleGenerateDocuments}
          disabled={isGenerating || selectedTypes.length === 0}
          activeOpacity={0.8}
        >
          {isGenerating ? (
            <ActivityIndicator color={theme.colors.white} />
          ) : (
            <>
              <MaterialIcons name="description" size={24} color={theme.colors.white} />
              <Text style={styles.generateButtonText}>Generate Documents</Text>
            </>
          )}
        </TouchableOpacity>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Generated Documents</Text>
          {isLoading ? (
            <ActivityIndicator size="large" color={theme.colors.primary} style={styles.loader} />
          ) : (
            renderExistingDocuments()
          )}
        </View>

        <View style={styles.disclaimer}>
          <Ionicons name="information-circle" size={20} color={theme.colors.textLight} />
          <Text style={styles.disclaimerText}>
            These documents are for informational purposes only. Please consult with a qualified
            tax professional for tax advice specific to your situation.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  scrollView: {
    flex: 1,
  },
  header: {
    padding: 20,
    paddingBottom: 10,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: theme.colors.text,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: theme.colors.textLight,
    lineHeight: 22,
  },
  yearSelector: {
    paddingHorizontal: 20,
    marginBottom: 20,
  },
  yearSelectorLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 8,
  },
  pickerContainer: {
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: theme.colors.border,
    overflow: 'hidden',
  },
  picker: {
    height: 50,
  },
  section: {
    paddingHorizontal: 20,
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 16,
  },
  documentTypesGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -8,
  },
  documentTypeCard: {
    width: '47%',
    margin: '1.5%',
    padding: 16,
    backgroundColor: theme.colors.surface,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  documentTypeCardSelected: {
    borderColor: theme.colors.primary,
    backgroundColor: theme.colors.primaryLight + '10',
  },
  documentTypeHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  checkIcon: {
    marginLeft: 'auto',
  },
  documentTypeLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 4,
  },
  documentTypeLabelSelected: {
    color: theme.colors.primary,
  },
  documentTypeDescription: {
    fontSize: 12,
    color: theme.colors.textLight,
    lineHeight: 16,
  },
  generateButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.primary,
    marginHorizontal: 20,
    marginBottom: 32,
    paddingVertical: 16,
    borderRadius: 12,
    gap: 8,
  },
  generateButtonDisabled: {
    opacity: 0.6,
  },
  generateButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.white,
  },
  documentsList: {
    gap: 12,
  },
  documentCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: theme.colors.surface,
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  documentInfo: {
    flex: 1,
  },
  documentTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 4,
  },
  documentDate: {
    fontSize: 14,
    color: theme.colors.textLight,
    marginBottom: 2,
  },
  documentSize: {
    fontSize: 12,
    color: theme.colors.textLight,
  },
  documentActions: {
    flexDirection: 'row',
    gap: 12,
  },
  actionButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: theme.colors.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 40,
  },
  emptyStateTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: theme.colors.text,
    marginTop: 16,
    marginBottom: 8,
  },
  emptyStateText: {
    fontSize: 14,
    color: theme.colors.textLight,
    textAlign: 'center',
  },
  loader: {
    marginVertical: 40,
  },
  disclaimer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: theme.colors.warningLight + '20',
    marginHorizontal: 20,
    marginBottom: 20,
    padding: 16,
    borderRadius: 12,
    gap: 12,
  },
  disclaimerText: {
    flex: 1,
    fontSize: 13,
    color: theme.colors.textLight,
    lineHeight: 18,
  },
});