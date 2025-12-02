import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Dimensions,
  ActivityIndicator
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import LinearGradient from 'react-native-linear-gradient';
import WidgetService, { WidgetData, WidgetConfig } from '../../services/widgets/WidgetService';

interface WidgetPreviewProps {
  widgetType: 'balance' | 'quick_actions' | 'recent_transactions';
  size: 'small' | 'medium' | 'large';
  onTap?: () => void;
  onQuickAction?: (actionId: string) => void;
}

const { width: screenWidth } = Dimensions.get('window');

const WidgetPreview: React.FC<WidgetPreviewProps> = ({
  widgetType,
  size,
  onTap,
  onQuickAction
}) => {
  const [widgetData, setWidgetData] = useState<WidgetData | null>(null);
  const [config, setConfig] = useState<WidgetConfig | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadWidgetData();
  }, [widgetType]);

  const loadWidgetData = async () => {
    try {
      setLoading(true);
      const [data, configurations] = await Promise.all([
        WidgetService.getWidgetData(),
        WidgetService.getConfiguration()
      ]);
      
      setWidgetData(data);
      setConfig(configurations[widgetType]);
    } catch (error) {
      console.error('Failed to load widget data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getWidgetDimensions = () => {
    const padding = 32; // Total horizontal padding
    const availableWidth = screenWidth - padding;
    
    switch (size) {
      case 'small':
        return { width: availableWidth * 0.45, height: 120 };
      case 'medium':
        return { width: availableWidth, height: 150 };
      case 'large':
        return { width: availableWidth, height: 220 };
      default:
        return { width: availableWidth, height: 150 };
    }
  };

  const formatTimeAgo = (timestamp: number) => {
    const now = Date.now();
    const diff = now - timestamp;
    
    if (diff < 60000) return 'now';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h`;
    return `${Math.floor(diff / 86400000)}d`;
  };

  const getTransactionColor = (type: string) => {
    switch (type) {
      case 'sent': return '#FF5252';
      case 'received': return '#4CAF50';
      case 'pending': return '#FF9800';
      default: return '#757575';
    }
  };

  const getIconName = (iconName: string) => {
    const iconMap: Record<string, string> = {
      'arrow-up-circle': 'arrow-upward',
      'arrow-down-circle': 'arrow-downward',
      'qr-code-scanner': 'qr-code-scanner',
      'credit-card': 'credit-card',
      'history': 'history',
      'bitcoin': 'currency-bitcoin',
      'group': 'group',
      'add-circle': 'add-circle'
    };
    return iconMap[iconName] || 'help';
  };

  const renderBalanceWidget = () => {
    const dimensions = getWidgetDimensions();
    
    return (
      <LinearGradient
        colors={['#667eea', '#764ba2']}
        style={[styles.widgetContainer, dimensions]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <TouchableOpacity style={styles.widgetContent} onPress={onTap}>
          {/* Header */}
          <View style={styles.widgetHeader}>
            <View style={styles.headerLeft}>
              <Icon name="account-balance-wallet" size={20} color="#FFFFFF" />
              <Text style={styles.widgetTitle}>Waqiti</Text>
            </View>
            <Text style={styles.lastUpdated}>
              {widgetData ? formatTimeAgo(widgetData.lastUpdated) : 'now'}
            </Text>
          </View>

          {/* Balance */}
          <View style={styles.balanceSection}>
            <Text style={styles.balanceLabel}>Total Balance</Text>
            <Text style={styles.balanceAmount}>
              {widgetData?.balance || '$1,234.56'}
            </Text>
          </View>

          {/* Recent Transaction (medium/large only) */}
          {size !== 'small' && widgetData?.recentTransaction && (
            <>
              <View style={styles.divider} />
              <View style={styles.transactionSection}>
                <View style={styles.transactionInfo}>
                  <Text style={styles.transactionLabel}>Recent</Text>
                  <Text style={styles.transactionDescription}>
                    {widgetData.recentTransaction.description}
                  </Text>
                </View>
                <View style={styles.transactionAmount}>
                  <Text style={[
                    styles.transactionAmountText,
                    { color: getTransactionColor(widgetData.recentTransaction.type) }
                  ]}>
                    {widgetData.recentTransaction.amount}
                  </Text>
                  <Text style={styles.transactionTime}>
                    {formatTimeAgo(widgetData.recentTransaction.timestamp)}
                  </Text>
                </View>
              </View>
            </>
          )}

          {/* Quick Actions (large only) */}
          {size === 'large' && widgetData?.quickActions && (
            <>
              <View style={styles.divider} />
              <View style={styles.quickActionsGrid}>
                {widgetData.quickActions.slice(0, 4).map((action) => (
                  <TouchableOpacity
                    key={action.id}
                    style={styles.quickActionButton}
                    onPress={() => onQuickAction?.(action.id)}
                  >
                    <Icon 
                      name={getIconName(action.icon)} 
                      size={16} 
                      color="#FFFFFF" 
                    />
                    <Text style={styles.quickActionText}>{action.title}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </>
          )}
        </TouchableOpacity>
      </LinearGradient>
    );
  };

  const renderQuickActionsWidget = () => {
    const dimensions = getWidgetDimensions();
    
    return (
      <LinearGradient
        colors={['#4facfe', '#00f2fe']}
        style={[styles.widgetContainer, dimensions]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <TouchableOpacity style={styles.widgetContent} onPress={onTap}>
          {/* Header */}
          <View style={styles.widgetHeader}>
            <View style={styles.headerLeft}>
              <Icon name="flash-on" size={20} color="#FFFFFF" />
              <Text style={styles.widgetTitle}>Quick Actions</Text>
            </View>
          </View>

          {/* Actions Grid */}
          <View style={styles.quickActionsGrid}>
            {widgetData?.quickActions?.slice(0, 4).map((action) => (
              <TouchableOpacity
                key={action.id}
                style={styles.quickActionButton}
                onPress={() => onQuickAction?.(action.id)}
              >
                <Icon 
                  name={getIconName(action.icon)} 
                  size={20} 
                  color="#FFFFFF" 
                />
                <Text style={styles.quickActionText}>{action.title}</Text>
              </TouchableOpacity>
            )) || (
              // Placeholder actions
              <>
                <TouchableOpacity style={styles.quickActionButton}>
                  <Icon name="arrow-upward" size={20} color="#FFFFFF" />
                  <Text style={styles.quickActionText}>Send</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.quickActionButton}>
                  <Icon name="arrow-downward" size={20} color="#FFFFFF" />
                  <Text style={styles.quickActionText}>Request</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.quickActionButton}>
                  <Icon name="qr-code-scanner" size={20} color="#FFFFFF" />
                  <Text style={styles.quickActionText}>Scan</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.quickActionButton}>
                  <Icon name="history" size={20} color="#FFFFFF" />
                  <Text style={styles.quickActionText}>History</Text>
                </TouchableOpacity>
              </>
            )}
          </View>
        </TouchableOpacity>
      </LinearGradient>
    );
  };

  const renderTransactionsWidget = () => {
    const dimensions = getWidgetDimensions();
    
    return (
      <LinearGradient
        colors={['#a8edea', '#fed6e3']}
        style={[styles.widgetContainer, dimensions]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <TouchableOpacity style={styles.widgetContent} onPress={onTap}>
          {/* Header with Balance */}
          <View style={styles.transactionWidgetHeader}>
            <Text style={styles.transactionWidgetBalance}>
              {widgetData?.balance || '$1,234.56'}
            </Text>
            <Text style={styles.lastUpdated}>
              {widgetData ? formatTimeAgo(widgetData.lastUpdated) : 'now'}
            </Text>
          </View>

          {/* Recent Transaction */}
          {widgetData?.recentTransaction && (
            <View style={styles.fullTransactionSection}>
              <View style={styles.transactionRow}>
                <View style={styles.transactionDetails}>
                  <Text style={styles.transactionDescriptionLarge}>
                    {widgetData.recentTransaction.description}
                  </Text>
                  <Text style={styles.transactionTimeLarge}>
                    {formatTimeAgo(widgetData.recentTransaction.timestamp)}
                  </Text>
                </View>
                <Text style={[
                  styles.transactionAmountLarge,
                  { color: getTransactionColor(widgetData.recentTransaction.type) }
                ]}>
                  {widgetData.recentTransaction.amount}
                </Text>
              </View>
            </View>
          )}
        </TouchableOpacity>
      </LinearGradient>
    );
  };

  const renderWidget = () => {
    switch (widgetType) {
      case 'balance':
        return renderBalanceWidget();
      case 'quick_actions':
        return renderQuickActionsWidget();
      case 'recent_transactions':
        return renderTransactionsWidget();
      default:
        return renderBalanceWidget();
    }
  };

  if (loading) {
    return (
      <View style={[styles.loadingContainer, getWidgetDimensions()]}>
        <ActivityIndicator size="small" color="#007AFF" />
      </View>
    );
  }

  return renderWidget();
};

const styles = StyleSheet.create({
  widgetContainer: {
    borderRadius: 16,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 4,
    },
    shadowOpacity: 0.3,
    shadowRadius: 4.65,
    elevation: 8,
  },
  widgetContent: {
    flex: 1,
  },
  loadingContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    borderRadius: 16,
  },
  widgetHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  widgetTitle: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  lastUpdated: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 12,
  },
  balanceSection: {
    marginBottom: 8,
  },
  balanceLabel: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 12,
    marginBottom: 4,
  },
  balanceAmount: {
    color: '#FFFFFF',
    fontSize: 24,
    fontWeight: 'bold',
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    marginVertical: 12,
  },
  transactionSection: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  transactionInfo: {
    flex: 1,
  },
  transactionLabel: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 11,
    marginBottom: 2,
  },
  transactionDescription: {
    color: '#FFFFFF',
    fontSize: 13,
  },
  transactionAmount: {
    alignItems: 'flex-end',
  },
  transactionAmountText: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 2,
  },
  transactionTime: {
    color: 'rgba(255, 255, 255, 0.6)',
    fontSize: 11,
  },
  quickActionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 8,
  },
  quickActionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    minWidth: '48%',
  },
  quickActionText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '500',
    marginLeft: 6,
  },
  transactionWidgetHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  transactionWidgetBalance: {
    color: '#1F2937',
    fontSize: 20,
    fontWeight: 'bold',
  },
  fullTransactionSection: {
    flex: 1,
  },
  transactionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    padding: 12,
    borderRadius: 8,
  },
  transactionDetails: {
    flex: 1,
  },
  transactionDescriptionLarge: {
    color: '#1F2937',
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 2,
  },
  transactionTimeLarge: {
    color: '#6B7280',
    fontSize: 12,
  },
  transactionAmountLarge: {
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default WidgetPreview;