import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Share,
  Platform,
} from 'react-native';
import { Picker } from '@react-native-picker/picker';
import DocumentPicker from 'react-native-document-picker';
import RNFetchBlob from 'rn-fetch-blob';
import { taxService } from '../../services/TaxService';
import { usePerformanceMonitor } from '../../hooks/usePerformanceMonitor';

interface TaxReport {
  id: string;
  taxYear: number;
  reportType: string;
  status: 'GENERATING' | 'COMPLETED' | 'ERROR';
  forms: TaxForm[];
  summary: TaxSummary;
  generatedAt: string;
  downloadUrl?: string;
}

interface TaxForm {
  formType: string;
  formName: string;
  status: 'COMPLETE' | 'INCOMPLETE' | 'NOT_APPLICABLE';
  requiredData: string[];
  missingData: string[];
  estimatedRefund?: number;
  estimatedTax?: number;
}

interface TaxSummary {
  totalIncome: number;
  totalDeductions: number;
  capitalGains: number;
  capitalLosses: number;
  cryptoGains: number;
  dividendIncome: number;
  interestIncome: number;
  estimatedTax: number;
  estimatedRefund: number;
}

interface TaxDocument {
  id: string;
  type: string;
  name: string;
  uploadedAt: string;
  status: 'PROCESSING' | 'VERIFIED' | 'REJECTED';
}

export const TaxReportingScreen: React.FC = () => {
  const performanceMonitor = usePerformanceMonitor('TaxReporting');
  
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear() - 1);
  const [taxReport, setTaxReport] = useState<TaxReport | null>(null);
  const [documents, setDocuments] = useState<TaxDocument[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);

  const availableYears = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - 1 - i);

  useEffect(() => {
    loadTaxData();
    loadTaxDocuments();
  }, [selectedYear]);

  const loadTaxData = async () => {
    setIsLoading(true);
    try {
      performanceMonitor.startTimer('tax_data_load');
      
      const response = await taxService.getTaxReport(selectedYear);
      
      if (response.success) {
        setTaxReport(response.data);
        performanceMonitor.recordEvent('tax_data_loaded');
      } else if (response.errorCode !== 'REPORT_NOT_FOUND') {
        throw new Error(response.errorMessage || 'Failed to load tax data');
      }
    } catch (error) {
      performanceMonitor.recordError('tax_data_error', error.toString());
      Alert.alert('Error', 'Failed to load tax data');
    } finally {
      setIsLoading(false);
      performanceMonitor.endTimer('tax_data_load');
    }
  };

  const loadTaxDocuments = async () => {
    try {
      const response = await taxService.getTaxDocuments(selectedYear);
      
      if (response.success) {
        setDocuments(response.data || []);
      }
    } catch (error) {
      console.error('Failed to load tax documents:', error);
    }
  };

  const generateTaxReport = async () => {
    setIsGenerating(true);
    try {
      performanceMonitor.startTimer('tax_report_generation');
      
      const response = await taxService.generateTaxReport({
        taxYear: selectedYear,
        includeTransactions: true,
        includeCrypto: true,
        includeInvestments: true,
        includeInternational: true,
      });

      if (response.success) {
        setTaxReport(response.data);
        performanceMonitor.recordEvent('tax_report_generated');
        Alert.alert('Success', 'Tax report generated successfully!');
      } else {
        throw new Error(response.errorMessage || 'Failed to generate tax report');
      }
    } catch (error) {
      performanceMonitor.recordError('tax_generation_error', error.toString());
      Alert.alert('Error', 'Failed to generate tax report');
    } finally {
      setIsGenerating(false);
      performanceMonitor.endTimer('tax_report_generation');
    }
  };

  const uploadTaxDocument = async () => {
    try {
      const result = await DocumentPicker.pick({
        type: [DocumentPicker.types.pdf, DocumentPicker.types.images],
        allowMultiSelection: false,
      });

      const file = result[0];
      
      const response = await taxService.uploadTaxDocument({
        taxYear: selectedYear,
        file: {
          uri: file.uri,
          type: file.type,
          name: file.name,
        },
      });

      if (response.success) {
        Alert.alert('Success', 'Document uploaded successfully!');
        loadTaxDocuments();
      } else {
        throw new Error(response.errorMessage || 'Failed to upload document');
      }
    } catch (error) {
      if (DocumentPicker.isCancel(error)) {
        return;
      }
      Alert.alert('Error', 'Failed to upload document');
    }
  };

  const downloadTaxReport = async () => {
    if (!taxReport?.downloadUrl) {
      Alert.alert('Error', 'No report available for download');
      return;
    }

    try {
      const { config, fs } = RNFetchBlob;
      const downloads = fs.dirs.DownloadDir;
      const fileName = `WaqitiTaxReport_${selectedYear}.pdf`;

      const configOptions = Platform.select({
        ios: {
          fileCache: true,
          path: `${downloads}/${fileName}`,
          notification: true,
        },
        android: {
          fileCache: true,
          path: `${downloads}/${fileName}`,
          addAndroidDownloads: {
            useDownloadManager: true,
            notification: true,
            path: `${downloads}/${fileName}`,
            description: `Tax Report for ${selectedYear}`,
          },
        },
      });

      const response = await config(configOptions).fetch('GET', taxReport.downloadUrl);

      Alert.alert('Success', `Tax report downloaded to ${fileName}`);
      
      if (Platform.OS === 'ios') {
        Share.share({
          url: response.path(),
          title: `Tax Report ${selectedYear}`,
        });
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to download tax report');
    }
  };

  const shareTaxReport = async () => {
    if (!taxReport?.downloadUrl) {
      Alert.alert('Error', 'No report available to share');
      return;
    }

    try {
      await Share.share({
        message: `My ${selectedYear} Tax Report from Waqiti`,
        url: taxReport.downloadUrl,
        title: `Tax Report ${selectedYear}`,
      });
    } catch (error) {
      console.error('Error sharing report:', error);
    }
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
    }).format(amount);
  };

  const renderTaxSummary = () => {
    if (!taxReport?.summary) return null;

    const { summary } = taxReport;

    return (
      <View style={styles.summaryContainer}>
        <Text style={styles.sectionTitle}>Tax Summary {selectedYear}</Text>
        
        <View style={styles.summaryGrid}>
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Total Income</Text>
            <Text style={[styles.summaryValue, { color: '#22c55e' }]}>
              {formatCurrency(summary.totalIncome)}
            </Text>
          </View>
          
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Total Deductions</Text>
            <Text style={styles.summaryValue}>
              {formatCurrency(summary.totalDeductions)}
            </Text>
          </View>
          
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Capital Gains</Text>
            <Text style={[styles.summaryValue, { color: summary.capitalGains >= 0 ? '#22c55e' : '#ef4444' }]}>
              {formatCurrency(summary.capitalGains)}
            </Text>
          </View>
          
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Crypto Gains</Text>
            <Text style={[styles.summaryValue, { color: summary.cryptoGains >= 0 ? '#22c55e' : '#ef4444' }]}>
              {formatCurrency(summary.cryptoGains)}
            </Text>
          </View>
          
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Dividend Income</Text>
            <Text style={[styles.summaryValue, { color: '#22c55e' }]}>
              {formatCurrency(summary.dividendIncome)}
            </Text>
          </View>
          
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Interest Income</Text>
            <Text style={[styles.summaryValue, { color: '#22c55e' }]}>
              {formatCurrency(summary.interestIncome)}
            </Text>
          </View>
        </View>

        {/* Tax Estimate */}
        <View style={styles.taxEstimateContainer}>
          <View style={[
            styles.taxEstimate,
            { backgroundColor: summary.estimatedRefund > 0 ? '#dcfce7' : '#fee2e2' }
          ]}>
            <Text style={styles.taxEstimateLabel}>
              {summary.estimatedRefund > 0 ? 'Estimated Refund' : 'Estimated Tax Owed'}
            </Text>
            <Text style={[
              styles.taxEstimateAmount,
              { color: summary.estimatedRefund > 0 ? '#16a34a' : '#dc2626' }
            ]}>
              {formatCurrency(Math.abs(summary.estimatedRefund || summary.estimatedTax))}
            </Text>
          </View>
        </View>
      </View>
    );
  };

  const renderTaxForms = () => {
    if (!taxReport?.forms) return null;

    return (
      <View style={styles.formsContainer}>
        <Text style={styles.sectionTitle}>Tax Forms</Text>
        
        {taxReport.forms.map((form, index) => (
          <View key={index} style={styles.formItem}>
            <View style={styles.formHeader}>
              <Text style={styles.formType}>{form.formType}</Text>
              <View style={[
                styles.formStatus,
                { backgroundColor: getFormStatusColor(form.status) }
              ]}>
                <Text style={styles.formStatusText}>{form.status}</Text>
              </View>
            </View>
            
            <Text style={styles.formName}>{form.formName}</Text>
            
            {form.missingData.length > 0 && (
              <View style={styles.missingDataContainer}>
                <Text style={styles.missingDataTitle}>Missing Data:</Text>
                {form.missingData.map((item, idx) => (
                  <Text key={idx} style={styles.missingDataItem}>• {item}</Text>
                ))}
              </View>
            )}

            {form.estimatedRefund !== undefined && (
              <Text style={[styles.formEstimate, { color: '#22c55e' }]}>
                Estimated Refund: {formatCurrency(form.estimatedRefund)}
              </Text>
            )}

            {form.estimatedTax !== undefined && (
              <Text style={[styles.formEstimate, { color: '#ef4444' }]}>
                Estimated Tax: {formatCurrency(form.estimatedTax)}
              </Text>
            )}
          </View>
        ))}
      </View>
    );
  };

  const renderDocuments = () => (
    <View style={styles.documentsContainer}>
      <View style={styles.documentsHeader}>
        <Text style={styles.sectionTitle}>Tax Documents</Text>
        <TouchableOpacity
          style={styles.uploadButton}
          onPress={uploadTaxDocument}
        >
          <Text style={styles.uploadButtonText}>+ Upload</Text>
        </TouchableOpacity>
      </View>
      
      {documents.length === 0 ? (
        <View style={styles.emptyDocuments}>
          <Text style={styles.emptyDocumentsText}>
            No documents uploaded yet. Upload W-2s, 1099s, and other tax documents.
          </Text>
        </View>
      ) : (
        documents.map((doc, index) => (
          <View key={doc.id} style={styles.documentItem}>
            <View style={styles.documentInfo}>
              <Text style={styles.documentName}>{doc.name}</Text>
              <Text style={styles.documentType}>{doc.type}</Text>
              <Text style={styles.documentDate}>
                Uploaded {new Date(doc.uploadedAt).toLocaleDateString()}
              </Text>
            </View>
            <View style={[
              styles.documentStatus,
              { backgroundColor: getDocumentStatusColor(doc.status) }
            ]}>
              <Text style={styles.documentStatusText}>{doc.status}</Text>
            </View>
          </View>
        ))
      )}
    </View>
  );

  const getFormStatusColor = (status: string): string => {
    switch (status) {
      case 'COMPLETE': return '#dcfce7';
      case 'INCOMPLETE': return '#fef3c7';
      case 'NOT_APPLICABLE': return '#f1f5f9';
      default: return '#f1f5f9';
    }
  };

  const getDocumentStatusColor = (status: string): string => {
    switch (status) {
      case 'VERIFIED': return '#dcfce7';
      case 'PROCESSING': return '#fef3c7';
      case 'REJECTED': return '#fee2e2';
      default: return '#f1f5f9';
    }
  };

  return (
    <ScrollView style={styles.container}>
      {/* Year Selector */}
      <View style={styles.yearSelector}>
        <Text style={styles.yearLabel}>Tax Year</Text>
        <Picker
          selectedValue={selectedYear}
          onValueChange={setSelectedYear}
          style={styles.yearPicker}
        >
          {availableYears.map(year => (
            <Picker.Item key={year} label={year.toString()} value={year} />
          ))}
        </Picker>
      </View>

      {/* Action Buttons */}
      <View style={styles.actionButtons}>
        <TouchableOpacity
          style={[styles.actionButton, styles.generateButton]}
          onPress={generateTaxReport}
          disabled={isGenerating}
        >
          <Text style={styles.actionButtonText}>
            {isGenerating ? 'Generating...' : 'Generate Report'}
          </Text>
        </TouchableOpacity>

        {taxReport && (
          <>
            <TouchableOpacity
              style={[styles.actionButton, styles.downloadButton]}
              onPress={downloadTaxReport}
            >
              <Text style={styles.actionButtonText}>Download PDF</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionButton, styles.shareButton]}
              onPress={shareTaxReport}
            >
              <Text style={styles.actionButtonText}>Share</Text>
            </TouchableOpacity>
          </>
        )}
      </View>

      {/* Content */}
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <Text>Loading tax data...</Text>
        </View>
      ) : taxReport ? (
        <>
          {renderTaxSummary()}
          {renderTaxForms()}
          {renderDocuments()}
        </>
      ) : (
        <View style={styles.emptyState}>
          <Text style={styles.emptyStateTitle}>No Tax Report Generated</Text>
          <Text style={styles.emptyStateDescription}>
            Generate your {selectedYear} tax report to view summary and forms.
          </Text>
        </View>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  yearSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    marginHorizontal: 16,
    marginTop: 16,
    padding: 16,
    borderRadius: 12,
  },
  yearLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
    marginRight: 16,
  },
  yearPicker: {
    flex: 1,
    height: 40,
  },
  actionButtons: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 16,
    gap: 8,
  },
  actionButton: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  generateButton: {
    backgroundColor: '#3182ce',
  },
  downloadButton: {
    backgroundColor: '#059669',
  },
  shareButton: {
    backgroundColor: '#7c3aed',
  },
  actionButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
  loadingContainer: {
    padding: 40,
    alignItems: 'center',
  },
  emptyState: {
    padding: 40,
    alignItems: 'center',
  },
  emptyStateTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a202c',
    marginBottom: 8,
  },
  emptyStateDescription: {
    fontSize: 16,
    color: '#6b7280',
    textAlign: 'center',
    lineHeight: 24,
  },
  summaryContainer: {
    backgroundColor: '#ffffff',
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 12,
    padding: 16,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a202c',
    marginBottom: 16,
  },
  summaryGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  summaryItem: {
    width: '48%',
    padding: 12,
    backgroundColor: '#f8fafc',
    borderRadius: 8,
  },
  summaryLabel: {
    fontSize: 14,
    color: '#6b7280',
    marginBottom: 4,
  },
  summaryValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a202c',
  },
  taxEstimateContainer: {
    marginTop: 16,
  },
  taxEstimate: {
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  taxEstimateLabel: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  taxEstimateAmount: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  formsContainer: {
    backgroundColor: '#ffffff',
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 12,
    padding: 16,
  },
  formItem: {
    padding: 16,
    backgroundColor: '#f8fafc',
    borderRadius: 8,
    marginBottom: 12,
  },
  formHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  formType: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1a202c',
  },
  formStatus: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  formStatusText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#374151',
  },
  formName: {
    fontSize: 14,
    color: '#6b7280',
    marginBottom: 8,
  },
  missingDataContainer: {
    marginTop: 8,
  },
  missingDataTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#dc2626',
    marginBottom: 4,
  },
  missingDataItem: {
    fontSize: 12,
    color: '#dc2626',
    marginLeft: 8,
  },
  formEstimate: {
    fontSize: 14,
    fontWeight: '600',
    marginTop: 8,
  },
  documentsContainer: {
    backgroundColor: '#ffffff',
    marginHorizontal: 16,
    marginBottom: 16,
    borderRadius: 12,
    padding: 16,
  },
  documentsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  uploadButton: {
    backgroundColor: '#3182ce',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 6,
  },
  uploadButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  emptyDocuments: {
    padding: 20,
    alignItems: 'center',
  },
  emptyDocumentsText: {
    fontSize: 14,
    color: '#6b7280',
    textAlign: 'center',
    lineHeight: 20,
  },
  documentItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#f8fafc',
    borderRadius: 8,
    marginBottom: 8,
  },
  documentInfo: {
    flex: 1,
  },
  documentName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
    marginBottom: 2,
  },
  documentType: {
    fontSize: 12,
    color: '#6b7280',
    marginBottom: 2,
  },
  documentDate: {
    fontSize: 11,
    color: '#9ca3af',
  },
  documentStatus: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  documentStatusText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#374151',
  },
});