import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  Alert,
  StyleSheet
} from 'react-native';
import { walletService } from '../../services/WalletService';
import { usePerformanceMonitor } from '../../hooks/usePerformanceMonitor';

interface WalletBalance {
  currency: string;
  balance: number;
  availableBalance: number;
  pendingBalance: number;
  lastUpdated: string;
}

interface WalletBalanceProps {
  userId: string;
  onBalancePress?: (currency: string, balance: WalletBalance) => void;
  onAddFunds?: () => void;
  onSendMoney?: () => void;
  showActions?: boolean;
}

export const WalletBalance: React.FC<WalletBalanceProps> = ({
  userId,
  onBalancePress,
  onAddFunds,
  onSendMoney,
  showActions = true
}) => {
  const performanceMonitor = usePerformanceMonitor('WalletBalance');
  
  const [balances, setBalances] = useState<WalletBalance[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isBalanceVisible, setIsBalanceVisible] = useState(true);
  const [primaryCurrency, setPrimaryCurrency] = useState('USD');

  useEffect(() => {
    loadWalletBalances();
  }, [userId]);

  const loadWalletBalances = useCallback(async () => {
    if (!userId) return;

    performanceMonitor.startTimer('balance_load');

    try {
      const response = await walletService.getWalletBalances(userId);
      
      if (response.success) {
        const walletBalances = response.data || [];
        setBalances(walletBalances);
        
        // Set primary currency to the one with highest balance
        if (walletBalances.length > 0) {
          const primary = walletBalances.reduce((prev, current) => 
            current.balance > prev.balance ? current : prev
          );
          setPrimaryCurrency(primary.currency);
        }
        
        performanceMonitor.recordEvent('balance_loaded', {
          count: walletBalances.length,
          currencies: walletBalances.map(b => b.currency)
        });
      } else {
        throw new Error(response.errorMessage || 'Failed to load wallet balances');
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to load balances';
      performanceMonitor.recordError('balance_load_error', errorMessage);
      
      Alert.alert('Error', errorMessage);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
      performanceMonitor.endTimer('balance_load');
    }
  }, [userId, performanceMonitor]);

  const handleRefresh = useCallback(() => {
    setIsRefreshing(true);
    loadWalletBalances();
  }, [loadWalletBalances]);

  const toggleBalanceVisibility = () => {
    setIsBalanceVisible(prev => !prev);
    performanceMonitor.recordEvent('balance_visibility_toggled', {
      visible: !isBalanceVisible
    });
  };

  const formatCurrency = (amount: number, currency: string): string => {
    if (!isBalanceVisible) {
      return '••••••';
    }
    
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  };

  const getTotalBalance = (): string => {
    if (!isBalanceVisible || balances.length === 0) {
      return isBalanceVisible ? '$0.00' : '••••••';
    }

    // Convert all balances to USD for total (simplified conversion)
    const total = balances.reduce((sum, balance) => {
      // In a real app, you'd convert using real exchange rates
      const conversionRate = balance.currency === 'USD' ? 1 :
                           balance.currency === 'EUR' ? 1.1 :
                           balance.currency === 'GBP' ? 1.3 :
                           balance.currency === 'CAD' ? 0.75 : 1;
      return sum + (balance.balance * conversionRate);
    }, 0);

    return formatCurrency(total, 'USD');
  };

  const getPrimaryBalance = (): WalletBalance | null => {
    return balances.find(b => b.currency === primaryCurrency) || 
           (balances.length > 0 ? balances[0] : null);
  };

  const handleBalancePress = (balance: WalletBalance) => {
    performanceMonitor.recordEvent('balance_pressed', { currency: balance.currency });
    onBalancePress?.(balance.currency, balance);
  };

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#3182ce" />
        <Text style={styles.loadingText}>Loading wallet...</Text>
      </View>
    );
  }

  const primaryBalance = getPrimaryBalance();

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl
          refreshing={isRefreshing}
          onRefresh={handleRefresh}
          colors={['#3182ce']}
          tintColor="#3182ce"
        />
      }
      testID="wallet-balance-container"
    >
      {/* Main Balance Card */}
      <View style={styles.mainBalanceCard}>
        <View style={styles.balanceHeader}>
          <Text style={styles.totalBalanceLabel}>Total Balance</Text>
          <TouchableOpacity
            onPress={toggleBalanceVisibility}
            style={styles.visibilityButton}
            testID="toggle-balance-visibility"
          >
            <Text style={styles.visibilityIcon}>
              {isBalanceVisible ? '👁️' : '🙈'}
            </Text>
          </TouchableOpacity>
        </View>
        
        <Text style={styles.totalBalance}>{getTotalBalance()}</Text>
        
        {primaryBalance && (
          <View style={styles.primaryBalanceContainer}>
            <Text style={styles.primaryBalanceLabel}>
              {primaryBalance.currency} Balance
            </Text>
            <Text style={styles.primaryBalance}>
              {formatCurrency(primaryBalance.balance, primaryBalance.currency)}
            </Text>
            {primaryBalance.pendingBalance > 0 && (
              <Text style={styles.pendingBalance}>
                Pending: {formatCurrency(primaryBalance.pendingBalance, primaryBalance.currency)}
              </Text>
            )}
          </View>
        )}
      </View>

      {/* Action Buttons */}
      {showActions && (
        <View style={styles.actionButtons}>
          <TouchableOpacity
            style={styles.actionButton}
            onPress={onAddFunds}
            testID="add-funds-button"
          >
            <Text style={styles.actionIcon}>💳</Text>
            <Text style={styles.actionText}>Add Funds</Text>
          </TouchableOpacity>
          
          <TouchableOpacity
            style={styles.actionButton}
            onPress={onSendMoney}
            testID="send-money-button"
          >
            <Text style={styles.actionIcon}>📤</Text>
            <Text style={styles.actionText}>Send Money</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Currency Breakdown */}
      {balances.length > 1 && (
        <View style={styles.currencyBreakdown}>
          <Text style={styles.sectionTitle}>Currency Balances</Text>
          {balances.map((balance, index) => (
            <TouchableOpacity
              key={balance.currency}
              style={[
                styles.currencyItem,
                index === balances.length - 1 ? styles.lastCurrencyItem : null
              ]}
              onPress={() => handleBalancePress(balance)}
              testID={`currency-balance-${balance.currency}`}
            >
              <View style={styles.currencyInfo}>
                <Text style={styles.currencyCode}>{balance.currency}</Text>
                <Text style={styles.currencyName}>
                  {getCurrencyName(balance.currency)}
                </Text>
              </View>
              
              <View style={styles.balanceInfo}>
                <Text style={styles.currencyBalance}>
                  {formatCurrency(balance.balance, balance.currency)}
                </Text>
                {balance.pendingBalance > 0 && (
                  <Text style={styles.currencyPending}>
                    +{formatCurrency(balance.pendingBalance, balance.currency)} pending
                  </Text>
                )}
              </View>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {/* Last Updated */}
      {balances.length > 0 && (
        <View style={styles.lastUpdated}>
          <Text style={styles.lastUpdatedText}>
            Last updated: {formatLastUpdated(balances[0].lastUpdated)}
          </Text>
        </View>
      )}
    </ScrollView>
  );
};

const getCurrencyName = (currency: string): string => {
  const names: { [key: string]: string } = {
    USD: 'US Dollar',
    EUR: 'Euro',
    GBP: 'British Pound',
    CAD: 'Canadian Dollar',
    AUD: 'Australian Dollar',
    JPY: 'Japanese Yen',
    SGD: 'Singapore Dollar'
  };
  return names[currency] || currency;
};

const formatLastUpdated = (timestamp: string): string => {
  const date = new Date(timestamp);
  const now = new Date();
  const diffInMinutes = (now.getTime() - date.getTime()) / (1000 * 60);

  if (diffInMinutes < 1) {
    return 'Just now';
  } else if (diffInMinutes < 60) {
    return `${Math.floor(diffInMinutes)} minutes ago`;
  } else {
    return date.toLocaleTimeString('en-US', { 
      hour: 'numeric', 
      minute: '2-digit',
      hour12: true 
    });
  }
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#6b7280',
  },
  mainBalanceCard: {
    margin: 16,
    padding: 24,
    backgroundColor: '#3182ce',
    borderRadius: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
  balanceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  totalBalanceLabel: {
    fontSize: 16,
    color: '#bfdbfe',
    fontWeight: '500',
  },
  visibilityButton: {
    padding: 8,
  },
  visibilityIcon: {
    fontSize: 20,
  },
  totalBalance: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 16,
  },
  primaryBalanceContainer: {
    borderTopWidth: 1,
    borderTopColor: '#60a5fa',
    paddingTop: 16,
  },
  primaryBalanceLabel: {
    fontSize: 14,
    color: '#bfdbfe',
    marginBottom: 4,
  },
  primaryBalance: {
    fontSize: 20,
    fontWeight: '600',
    color: '#ffffff',
  },
  pendingBalance: {
    fontSize: 14,
    color: '#fbbf24',
    marginTop: 4,
  },
  actionButtons: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginBottom: 24,
    gap: 12,
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#ffffff',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  actionIcon: {
    fontSize: 24,
    marginBottom: 8,
  },
  actionText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
  },
  currencyBreakdown: {
    margin: 16,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a202c',
    padding: 16,
    paddingBottom: 8,
  },
  currencyItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  lastCurrencyItem: {
    borderBottomWidth: 0,
  },
  currencyInfo: {
    flex: 1,
  },
  currencyCode: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
  },
  currencyName: {
    fontSize: 14,
    color: '#6b7280',
    marginTop: 2,
  },
  balanceInfo: {
    alignItems: 'flex-end',
  },
  currencyBalance: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a202c',
  },
  currencyPending: {
    fontSize: 12,
    color: '#f59e0b',
    marginTop: 2,
  },
  lastUpdated: {
    alignItems: 'center',
    padding: 16,
  },
  lastUpdatedText: {
    fontSize: 12,
    color: '#9ca3af',
  },
});