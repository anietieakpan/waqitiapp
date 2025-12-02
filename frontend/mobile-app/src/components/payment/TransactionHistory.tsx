import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  StyleSheet,
  Alert
} from 'react-native';
import { paymentService } from '../../services/PaymentService';
import { usePerformanceMonitor } from '../../hooks/usePerformanceMonitor';

interface Transaction {
  id: string;
  type: 'SENT' | 'RECEIVED' | 'DEPOSIT' | 'WITHDRAWAL';
  amount: number;
  currency: string;
  description: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  timestamp: string;
  recipientName?: string;
  senderName?: string;
  transactionHash?: string;
}

interface TransactionHistoryProps {
  userId: string;
  onTransactionPress?: (transaction: Transaction) => void;
  limit?: number;
}

export const TransactionHistory: React.FC<TransactionHistoryProps> = ({
  userId,
  onTransactionPress,
  limit = 20
}) => {
  const performanceMonitor = usePerformanceMonitor('TransactionHistory');
  
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);

  useEffect(() => {
    loadTransactions(true);
  }, [userId]);

  const loadTransactions = useCallback(async (isInitial: boolean = false) => {
    if (!isInitial && (!hasMore || isLoading)) return;

    const currentPage = isInitial ? 0 : page;
    
    if (isInitial) {
      setIsLoading(true);
      performanceMonitor.startTimer('initial_load');
    }

    try {
      const response = await paymentService.getTransactionHistory({
        userId,
        page: currentPage,
        size: limit,
        sortBy: 'timestamp',
        sortDirection: 'DESC'
      });

      if (response.success) {
        const newTransactions = response.data || [];
        
        if (isInitial) {
          setTransactions(newTransactions);
          performanceMonitor.endTimer('initial_load');
        } else {
          setTransactions(prev => [...prev, ...newTransactions]);
        }

        setHasMore(newTransactions.length === limit);
        setPage(currentPage + 1);
        
        performanceMonitor.recordEvent('transactions_loaded', {
          count: newTransactions.length,
          page: currentPage
        });
      } else {
        throw new Error(response.errorMessage || 'Failed to load transactions');
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to load transactions';
      performanceMonitor.recordError('transaction_load_error', errorMessage);
      
      if (isInitial) {
        Alert.alert('Error', errorMessage);
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [userId, page, limit, hasMore, isLoading, performanceMonitor]);

  const handleRefresh = useCallback(() => {
    setIsRefreshing(true);
    setPage(0);
    setHasMore(true);
    loadTransactions(true);
  }, [loadTransactions]);

  const handleLoadMore = useCallback(() => {
    if (!isLoading && hasMore) {
      loadTransactions(false);
    }
  }, [isLoading, hasMore, loadTransactions]);

  const formatAmount = (amount: number, currency: string): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 2,
    }).format(amount);
  };

  const formatDate = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffInHours = (now.getTime() - date.getTime()) / (1000 * 60 * 60);

    if (diffInHours < 24) {
      return date.toLocaleTimeString('en-US', { 
        hour: 'numeric', 
        minute: '2-digit',
        hour12: true 
      });
    } else if (diffInHours < 168) { // 1 week
      return date.toLocaleDateString('en-US', { 
        weekday: 'short',
        month: 'short',
        day: 'numeric'
      });
    } else {
      return date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    }
  };

  const getTransactionIcon = (type: Transaction['type']): string => {
    switch (type) {
      case 'SENT': return '↗️';
      case 'RECEIVED': return '↙️';
      case 'DEPOSIT': return '⬇️';
      case 'WITHDRAWAL': return '⬆️';
      default: return '💰';
    }
  };

  const getStatusColor = (status: Transaction['status']): string => {
    switch (status) {
      case 'COMPLETED': return '#22c55e';
      case 'PENDING': return '#f59e0b';
      case 'FAILED': return '#ef4444';
      case 'CANCELLED': return '#6b7280';
      default: return '#6b7280';
    }
  };

  const renderTransaction = ({ item }: { item: Transaction }) => {
    const isOutgoing = item.type === 'SENT' || item.type === 'WITHDRAWAL';
    const displayName = isOutgoing ? item.recipientName : item.senderName;
    
    return (
      <TouchableOpacity
        style={styles.transactionItem}
        onPress={() => onTransactionPress?.(item)}
        testID={`transaction-${item.id}`}
      >
        <View style={styles.transactionIcon}>
          <Text style={styles.iconText}>{getTransactionIcon(item.type)}</Text>
        </View>
        
        <View style={styles.transactionContent}>
          <View style={styles.transactionHeader}>
            <Text style={styles.transactionDescription} numberOfLines={1}>
              {item.description || displayName || 'Transaction'}
            </Text>
            <Text style={[
              styles.transactionAmount,
              { color: isOutgoing ? '#ef4444' : '#22c55e' }
            ]}>
              {isOutgoing ? '-' : '+'}{formatAmount(item.amount, item.currency)}
            </Text>
          </View>
          
          <View style={styles.transactionFooter}>
            <Text style={styles.transactionDate}>{formatDate(item.timestamp)}</Text>
            <View style={[
              styles.statusBadge,
              { backgroundColor: `${getStatusColor(item.status)}20` }
            ]}>
              <Text style={[
                styles.statusText,
                { color: getStatusColor(item.status) }
              ]}>
                {item.status}
              </Text>
            </View>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  const renderFooter = () => {
    if (!hasMore) {
      return (
        <View style={styles.footer}>
          <Text style={styles.footerText}>No more transactions</Text>
        </View>
      );
    }
    
    if (isLoading && !isRefreshing) {
      return (
        <View style={styles.footer}>
          <ActivityIndicator size="small" color="#3182ce" />
        </View>
      );
    }
    
    return null;
  };

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyIcon}>📊</Text>
      <Text style={styles.emptyTitle}>No Transactions Yet</Text>
      <Text style={styles.emptyDescription}>
        Your transaction history will appear here once you start making payments.
      </Text>
    </View>
  );

  if (isLoading && transactions.length === 0) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#3182ce" />
        <Text style={styles.loadingText}>Loading transactions...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={transactions}
        renderItem={renderTransaction}
        keyExtractor={(item) => item.id}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={handleRefresh}
            colors={['#3182ce']}
            tintColor="#3182ce"
          />
        }
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.3}
        ListEmptyComponent={renderEmpty}
        ListFooterComponent={renderFooter}
        showsVerticalScrollIndicator={false}
        testID="transaction-history-list"
      />
    </View>
  );
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
  transactionItem: {
    flexDirection: 'row',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
    alignItems: 'center',
  },
  transactionIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#f1f5f9',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  iconText: {
    fontSize: 20,
  },
  transactionContent: {
    flex: 1,
  },
  transactionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  transactionDescription: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
    flex: 1,
    marginRight: 12,
  },
  transactionAmount: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  transactionFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  transactionDate: {
    fontSize: 14,
    color: '#6b7280',
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'capitalize',
  },
  footer: {
    padding: 20,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 14,
    color: '#6b7280',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a202c',
    marginBottom: 8,
  },
  emptyDescription: {
    fontSize: 16,
    color: '#6b7280',
    textAlign: 'center',
    lineHeight: 24,
  },
});