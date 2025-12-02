/**
 * CheckDepositStatusScreen - Status tracking screen after check submission
 * Shows deposit progress, estimated availability, and detailed information
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  RefreshControl,
  Share,
  Linking,
} from 'react-native';
import {
  Surface,
  useTheme,
  Button,
  Chip,
  List,
  Divider,
  Portal,
  Modal,
  ActivityIndicator,
} from 'react-native-paper';
import { useNavigation, useRoute, useFocusEffect } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { format, addBusinessDays, parseISO } from 'date-fns';
import { useSelector } from 'react-redux';

import { RootState } from '../../store/store';
import { formatCurrency } from '../../utils/formatters';
import { showToast } from '../../utils/toast';
import CheckDepositService, { CheckDepositStatusDetails, CheckDepositStatus } from '../../services/banking/CheckDepositService';
import { AccountService } from '../../services/AccountService';
import { Logger } from '../../../../shared/services/src/LoggingService';

interface DepositStatus {
  id: string;
  status: 'submitted' | 'processing' | 'approved' | 'available' | 'rejected' | 'on_hold';
  amount: number;
  submittedAt: string;
  estimatedAvailability?: string;
  actualAvailability?: string;
  account: {
    id: string;
    name: string;
    type: string;
  };
  holdReason?: string;
  rejectionReason?: string;
  processingSteps: ProcessingStep[];
}

interface ProcessingStep {
  id: string;
  name: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  completedAt?: string;
  description: string;
  estimatedDuration?: string;
}

interface RouteParams {
  CheckDepositStatus: {
    depositId: string;
    amount?: string;
    account?: any;
    estimatedAvailability?: string;
  };
}

// Helper function to map API status to local status
const mapApiStatusToLocal = (apiStatus: CheckDepositStatus): DepositStatus['status'] => {
  switch (apiStatus) {
    case CheckDepositStatus.SUBMITTED:
      return 'submitted';
    case CheckDepositStatus.PROCESSING:
      return 'processing';
    case CheckDepositStatus.UNDER_REVIEW:
      return 'on_hold';
    case CheckDepositStatus.APPROVED:
      return 'approved';
    case CheckDepositStatus.DEPOSITED:
      return 'available';
    case CheckDepositStatus.REJECTED:
    case CheckDepositStatus.FAILED:
      return 'rejected';
    default:
      return 'processing';
  }
};

// Helper function to format step titles
const formatStepTitle = (step: string): string => {
  const stepTitles: Record<string, string> = {
    'submitted': 'Check Submitted',
    'image_validation': 'Image Validation',
    'amount_verification': 'Amount Verification',
    'fraud_check': 'Security Check',
    'bank_processing': 'Bank Processing',
    'funds_available': 'Funds Available',
  };
  
  return stepTitles[step] || step.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
};

/**
 * Check Deposit Status Screen Component
 */
const CheckDepositStatusScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const route = useRoute<any>();
  
  // Route params
  const { depositId, amount, account, estimatedAvailability } = route.params || {};
  
  // State
  const [depositStatus, setDepositStatus] = useState<DepositStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showHelpModal, setShowHelpModal] = useState(false);
  
  // Redux state
  const user = useSelector((state: RootState) => state.auth.user);
  
  // Mock data for demonstration
  const mockDepositStatus: DepositStatus = {
    id: depositId,
    status: 'processing',
    amount: parseFloat(amount || '0'),
    submittedAt: new Date().toISOString(),
    estimatedAvailability: estimatedAvailability || format(addBusinessDays(new Date(), 2), 'MMM dd, yyyy'),
    account: account || { id: '1', name: 'Primary Checking', type: 'checking' },
    processingSteps: [
      {
        id: '1',
        name: 'Image Processing',
        status: 'completed',
        completedAt: new Date().toISOString(),
        description: 'Check images processed and validated',
        estimatedDuration: '1-2 minutes',
      },
      {
        id: '2',
        name: 'MICR Validation',
        status: 'completed',
        completedAt: new Date(Date.now() - 300000).toISOString(), // 5 minutes ago
        description: 'Bank routing and account numbers verified',
        estimatedDuration: '5-10 minutes',
      },
      {
        id: '3',
        name: 'Fraud Detection',
        status: 'in_progress',
        description: 'Security and fraud analysis in progress',
        estimatedDuration: '10-30 minutes',
      },
      {
        id: '4',
        name: 'Bank Processing',
        status: 'pending',
        description: 'Processing with issuing bank',
        estimatedDuration: '1-2 business days',
      },
      {
        id: '5',
        name: 'Fund Availability',
        status: 'pending',
        description: 'Funds released to account',
        estimatedDuration: '1-3 business days',
      },
    ],
  };
  
  // Load deposit status
  const loadDepositStatus = useCallback(async (showLoader = true) => {
    if (showLoader) setLoading(true);
    setRefreshing(!showLoader);
    
    try {
      Logger.info('Loading deposit status', { depositId });
      
      // Call actual API to get deposit status
      const statusDetails = await CheckDepositService.getDepositStatus(depositId);
      
      // Fetch actual account details
      const accountDetails = await AccountService.getAccountByDepositId(depositId) || 
        await AccountService.getAccountDetails(statusDetails.depositAccountId);
      
      // Convert API response to local format
      const mappedStatus: DepositStatus = {
        id: statusDetails.depositId,
        status: mapApiStatusToLocal(statusDetails.status),
        amount: statusDetails.amount,
        account: {
          id: accountDetails?.id || statusDetails.depositAccountId,
          name: accountDetails?.name || 'Checking Account',
          number: accountDetails?.maskedNumber || '****0000',
        },
        submittedAt: statusDetails.submittedAt,
        estimatedAvailability: statusDetails.availableAt || 'Processing',
        confirmationNumber: statusDetails.confirmationNumber,
        memo: statusDetails.memo,
        steps: statusDetails.processingSteps.map(step => ({
          id: step.step,
          title: formatStepTitle(step.step),
          status: step.status,
          timestamp: step.timestamp,
          description: step.description,
        })),
        actions: statusDetails.supportActions.filter(action => action.available),
        rejectionReason: statusDetails.rejectionReason,
      };
      
      setDepositStatus(mappedStatus);
    } catch (error) {
      Logger.error('Failed to load deposit status', error, { depositId });
      showToast('Failed to load deposit status', 'error');
      
      // Fallback to mock data in case of error
      setDepositStatus(mockDepositStatus);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [depositId, mockDepositStatus]);
  
  // Load data on mount and focus
  useEffect(() => {
    loadDepositStatus();
  }, [loadDepositStatus]);
  
  useFocusEffect(
    useCallback(() => {
      // Refresh status when screen comes into focus
      const interval = setInterval(() => {
        loadDepositStatus(false);
      }, 30000); // Refresh every 30 seconds
      
      return () => clearInterval(interval);
    }, [loadDepositStatus])
  );
  
  // Handle refresh
  const handleRefresh = useCallback(() => {
    loadDepositStatus(false);
  }, [loadDepositStatus]);
  
  // Get status color and icon
  const getStatusDisplay = (status: DepositStatus['status']) => {
    switch (status) {
      case 'submitted':
        return { color: '#2196F3', icon: 'clock-outline', text: 'Submitted' };
      case 'processing':
        return { color: '#FF9800', icon: 'cog', text: 'Processing' };
      case 'approved':
        return { color: '#4CAF50', icon: 'check-circle', text: 'Approved' };
      case 'available':
        return { color: '#4CAF50', icon: 'check-circle', text: 'Available' };
      case 'rejected':
        return { color: '#F44336', icon: 'close-circle', text: 'Rejected' };
      case 'on_hold':
        return { color: '#FF5722', icon: 'pause-circle', text: 'On Hold' };
      default:
        return { color: '#9E9E9E', icon: 'help-circle', text: 'Unknown' };
    }
  };
  
  // Get step status color and icon
  const getStepDisplay = (status: ProcessingStep['status']) => {
    switch (status) {
      case 'completed':
        return { color: '#4CAF50', icon: 'check-circle' };
      case 'in_progress':
        return { color: '#FF9800', icon: 'cog' };
      case 'failed':
        return { color: '#F44336', icon: 'close-circle' };
      default:
        return { color: '#9E9E9E', icon: 'circle-outline' };
    }
  };
  
  // Calculate progress percentage
  const getProgressPercentage = () => {
    if (!depositStatus) return 0;
    
    const completedSteps = depositStatus.processingSteps.filter(
      step => step.status === 'completed'
    ).length;
    return Math.round((completedSteps / depositStatus.processingSteps.length) * 100);
  };
  
  // Handle share receipt
  const handleShareReceipt = useCallback(async () => {
    if (!depositStatus) return;
    
    try {
      const message = `Check Deposit Receipt\n\nAmount: ${formatCurrency(depositStatus.amount)}\nAccount: ${depositStatus.account.name}\nSubmitted: ${format(parseISO(depositStatus.submittedAt), 'MMM dd, yyyy h:mm a')}\nStatus: ${getStatusDisplay(depositStatus.status).text}\nDeposit ID: ${depositStatus.id}`;
      
      await Share.share({
        message,
        title: 'Check Deposit Receipt',
      });
    } catch (error) {
      Logger.error('Share error', error);
    }
  }, [depositStatus]);
  
  // Handle support contact
  const handleContactSupport = useCallback(() => {
    Alert.alert(
      'Contact Support',
      'How would you like to get help?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Call Support',
          onPress: () => Linking.openURL('tel:1-800-123-4567'),
        },
        {
          text: 'Live Chat',
          onPress: async () => {
            try {
              // Get support info from API
              const supportInfo = await CheckDepositService.getSupportInfo();
              
              if (supportInfo.chatAvailable) {
                // Navigate to chat support screen
                navigation.navigate('SupportChat', {
                  type: 'check_deposit',
                  depositId: depositStatus?.id,
                });
              } else {
                showToast('Live chat is currently unavailable. Please call support.', 'info');
              }
            } catch (error) {
              Logger.error('Failed to get support info', error);
              showToast('Unable to open chat support. Please try calling.', 'error');
            }
          },
        },
      ]
    );
  }, []);
  
  // Handle cancel deposit (if allowed)
  const handleCancelDeposit = useCallback(() => {
    if (!depositStatus) return;
    
    Alert.alert(
      'Cancel Deposit',
      'Are you sure you want to cancel this check deposit? This action cannot be undone.',
      [
        { text: 'No', style: 'cancel' },
        {
          text: 'Yes, Cancel',
          style: 'destructive',
          onPress: async () => {
            try {
              // Call API to cancel deposit
              await CheckDepositService.cancelDeposit(
                depositStatus.id,
                'User requested cancellation'
              );
              showToast('Deposit cancelled successfully', 'success');
              
              // Refresh status to show cancellation
              await loadDepositStatus();
              navigation.goBack();
            } catch (error) {
              showToast('Failed to cancel deposit', 'error');
            }
          },
        },
      ]
    );
  }, [depositStatus, navigation]);
  
  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
        <Text style={styles.loadingText}>Loading deposit status...</Text>
      </View>
    );
  }
  
  if (!depositStatus) {
    return (
      <View style={styles.errorContainer}>
        <Icon name="alert-circle" size={48} color={theme.colors.error} />
        <Text style={styles.errorTitle}>Deposit Not Found</Text>
        <Text style={styles.errorMessage}>
          We couldn't find information about this deposit.
        </Text>
        <Button
          mode="contained"
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          Go Back
        </Button>
      </View>
    );
  }
  
  const statusDisplay = getStatusDisplay(depositStatus.status);
  const progressPercentage = getProgressPercentage();
  
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
      }
    >
      {/* Header */}
      <Surface style={styles.headerSection}>
        <View style={styles.headerContent}>
          <View style={styles.statusIndicator}>
            <Icon
              name={statusDisplay.icon}
              size={32}
              color={statusDisplay.color}
            />
            <View style={styles.statusText}>
              <Text style={styles.statusTitle}>{statusDisplay.text}</Text>
              <Text style={styles.depositId}>ID: {depositStatus.id}</Text>
            </View>
          </View>
          
          <View style={styles.amountContainer}>
            <Text style={styles.amountLabel}>Deposit Amount</Text>
            <Text style={styles.amountValue}>
              {formatCurrency(depositStatus.amount)}
            </Text>
          </View>
        </View>
        
        <View style={styles.accountInfo}>
          <Icon name="bank" size={20} color={theme.colors.primary} />
          <Text style={styles.accountText}>
            {depositStatus.account.name} ({depositStatus.account.type})
          </Text>
        </View>
        
        <View style={styles.timeInfo}>
          <Text style={styles.timeLabel}>Submitted:</Text>
          <Text style={styles.timeValue}>
            {format(parseISO(depositStatus.submittedAt), 'MMM dd, yyyy h:mm a')}
          </Text>
        </View>
        
        {depositStatus.estimatedAvailability && (
          <View style={styles.availabilityInfo}>
            <Text style={styles.availabilityLabel}>Estimated Availability:</Text>
            <Text style={styles.availabilityValue}>
              {depositStatus.estimatedAvailability}
            </Text>
          </View>
        )}
      </Surface>
      
      {/* Progress Section */}
      <Surface style={styles.progressSection}>
        <View style={styles.progressHeader}>
          <Text style={styles.sectionTitle}>Processing Progress</Text>
          <Chip
            style={[styles.progressChip, { backgroundColor: statusDisplay.color }]}
            textStyle={styles.progressChipText}
          >
            {progressPercentage}%
          </Chip>
        </View>
        
        <View style={styles.progressBar}>
          <View
            style={[
              styles.progressFill,
              {
                width: `${progressPercentage}%`,
                backgroundColor: statusDisplay.color,
              },
            ]}
          />
        </View>
        
        <View style={styles.processingSteps}>
          {depositStatus.processingSteps.map((step, index) => {
            const stepDisplay = getStepDisplay(step.status);
            const isLast = index === depositStatus.processingSteps.length - 1;
            
            return (
              <View key={step.id} style={styles.stepContainer}>
                <View style={styles.stepIndicator}>
                  <View style={[styles.stepIcon, { backgroundColor: stepDisplay.color }]}>
                    <Icon name={stepDisplay.icon} size={16} color="white" />
                  </View>
                  {!isLast && <View style={styles.stepConnector} />}
                </View>
                
                <View style={styles.stepContent}>
                  <Text style={styles.stepName}>{step.name}</Text>
                  <Text style={styles.stepDescription}>{step.description}</Text>
                  {step.completedAt && (
                    <Text style={styles.stepTime}>
                      Completed: {format(parseISO(step.completedAt), 'h:mm a')}
                    </Text>
                  )}
                  {step.status === 'in_progress' && step.estimatedDuration && (
                    <Text style={styles.stepEstimate}>
                      ETA: {step.estimatedDuration}
                    </Text>
                  )}
                </View>
              </View>
            );
          })}
        </View>
      </Surface>
      
      {/* Additional Information */}
      {(depositStatus.holdReason || depositStatus.rejectionReason) && (
        <Surface style={styles.alertSection}>
          <View style={styles.alertHeader}>
            <Icon
              name={depositStatus.status === 'rejected' ? 'close-circle' : 'pause-circle'}
              size={24}
              color={depositStatus.status === 'rejected' ? '#F44336' : '#FF5722'}
            />
            <Text style={styles.alertTitle}>
              {depositStatus.status === 'rejected' ? 'Deposit Rejected' : 'Deposit On Hold'}
            </Text>
          </View>
          <Text style={styles.alertMessage}>
            {depositStatus.rejectionReason || depositStatus.holdReason}
          </Text>
        </Surface>
      )}
      
      {/* Actions */}
      <Surface style={styles.actionsSection}>
        <Text style={styles.sectionTitle}>Actions</Text>
        
        <List.Item
          title="View Details"
          description="See full deposit information"
          left={(props) => <List.Icon {...props} icon="file-document" />}
          right={(props) => <List.Icon {...props} icon="chevron-right" />}
          onPress={() => setShowDetailsModal(true)}
          style={styles.actionItem}
        />
        
        <Divider />
        
        <List.Item
          title="Share Receipt"
          description="Share deposit confirmation"
          left={(props) => <List.Icon {...props} icon="share" />}
          right={(props) => <List.Icon {...props} icon="chevron-right" />}
          onPress={handleShareReceipt}
          style={styles.actionItem}
        />
        
        <Divider />
        
        <List.Item
          title="Get Help"
          description="Contact customer support"
          left={(props) => <List.Icon {...props} icon="help-circle" />}
          right={(props) => <List.Icon {...props} icon="chevron-right" />}
          onPress={handleContactSupport}
          style={styles.actionItem}
        />
        
        {depositStatus.status === 'submitted' && (
          <>
            <Divider />
            <List.Item
              title="Cancel Deposit"
              description="Cancel this deposit request"
              left={(props) => <List.Icon {...props} icon="cancel" color="#F44336" />}
              right={(props) => <List.Icon {...props} icon="chevron-right" />}
              onPress={handleCancelDeposit}
              style={styles.actionItem}
              titleStyle={{ color: '#F44336' }}
            />
          </>
        )}
      </Surface>
      
      {/* Bottom Actions */}
      <View style={styles.bottomActions}>
        <Button
          mode="outlined"
          onPress={() => navigation.navigate('Dashboard')}
          style={styles.bottomButton}
        >
          Go to Dashboard
        </Button>
        <Button
          mode="contained"
          onPress={() => navigation.navigate('CheckCapture')}
          style={styles.bottomButton}
        >
          New Deposit
        </Button>
      </View>
      
      {/* Details Modal */}
      <Portal>
        <Modal
          visible={showDetailsModal}
          onDismiss={() => setShowDetailsModal(false)}
          contentContainerStyle={styles.detailsModal}
        >
          <ScrollView>
            <Text style={styles.modalTitle}>Deposit Details</Text>
            
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Deposit ID:</Text>
              <Text style={styles.detailValue}>{depositStatus.id}</Text>
            </View>
            
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Amount:</Text>
              <Text style={styles.detailValue}>
                {formatCurrency(depositStatus.amount)}
              </Text>
            </View>
            
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Account:</Text>
              <Text style={styles.detailValue}>
                {depositStatus.account.name}
              </Text>
            </View>
            
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Status:</Text>
              <Text style={styles.detailValue}>{statusDisplay.text}</Text>
            </View>
            
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Submitted:</Text>
              <Text style={styles.detailValue}>
                {format(parseISO(depositStatus.submittedAt), 'MMM dd, yyyy h:mm:ss a')}
              </Text>
            </View>
            
            <Button
              mode="contained"
              onPress={() => setShowDetailsModal(false)}
              style={styles.modalButton}
            >
              Close
            </Button>
          </ScrollView>
        </Modal>
      </Portal>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    padding: 16,
    paddingBottom: 100,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginTop: 16,
    marginBottom: 8,
    textAlign: 'center',
  },
  errorMessage: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
  },
  backButton: {
    borderRadius: 8,
  },
  headerSection: {
    padding: 20,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 2,
  },
  headerContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  statusIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusText: {
    marginLeft: 12,
  },
  statusTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  depositId: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  amountContainer: {
    alignItems: 'flex-end',
  },
  amountLabel: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  amountValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
  },
  accountInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  accountText: {
    fontSize: 16,
    marginLeft: 8,
    color: '#333',
  },
  timeInfo: {
    flexDirection: 'row',
    marginBottom: 4,
  },
  timeLabel: {
    fontSize: 14,
    color: '#666',
    marginRight: 8,
  },
  timeValue: {
    fontSize: 14,
    color: '#333',
  },
  availabilityInfo: {
    flexDirection: 'row',
  },
  availabilityLabel: {
    fontSize: 14,
    color: '#666',
    marginRight: 8,
  },
  availabilityValue: {
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  progressSection: {
    padding: 20,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 1,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  progressChip: {
    backgroundColor: '#4CAF50',
  },
  progressChipText: {
    color: 'white',
    fontWeight: 'bold',
  },
  progressBar: {
    height: 8,
    backgroundColor: '#E0E0E0',
    borderRadius: 4,
    marginBottom: 24,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },
  processingSteps: {
    // No additional styles needed
  },
  stepContainer: {
    flexDirection: 'row',
    marginBottom: 20,
  },
  stepIndicator: {
    alignItems: 'center',
    marginRight: 16,
  },
  stepIcon: {
    width: 32,
    height: 32,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepConnector: {
    width: 2,
    flex: 1,
    backgroundColor: '#E0E0E0',
    marginTop: 8,
  },
  stepContent: {
    flex: 1,
    paddingBottom: 8,
  },
  stepName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  stepDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  stepTime: {
    fontSize: 12,
    color: '#4CAF50',
    marginTop: 4,
  },
  stepEstimate: {
    fontSize: 12,
    color: '#FF9800',
    marginTop: 4,
  },
  alertSection: {
    padding: 20,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 1,
    backgroundColor: '#FFF3E0',
  },
  alertHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  alertTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
    color: '#333',
  },
  alertMessage: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  actionsSection: {
    borderRadius: 12,
    marginBottom: 16,
    elevation: 1,
    overflow: 'hidden',
  },
  actionItem: {
    paddingVertical: 4,
  },
  bottomActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
  },
  bottomButton: {
    flex: 1,
    borderRadius: 8,
  },
  detailsModal: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    padding: 20,
    maxHeight: '80%',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
    paddingVertical: 4,
  },
  detailLabel: {
    fontSize: 14,
    color: '#666',
    flex: 1,
  },
  detailValue: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
    flex: 2,
    textAlign: 'right',
  },
  modalButton: {
    marginTop: 16,
    borderRadius: 8,
  },
});

export default CheckDepositStatusScreen;