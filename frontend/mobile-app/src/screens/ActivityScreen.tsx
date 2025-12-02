/**
 * ActivityScreen - Transaction Activity Feed
 * Displays user's transaction history with filtering, search, and analytics
 *
 * @author Waqiti Mobile Team
 * @version 1.0.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import { format, isToday, isYesterday, startOfMonth, endOfMonth } from 'date-fns';

// Redux
import { RootState } from '../store';
import { fetchTransactions, setFilter } from '../store/slices/transactionsSlice';

// Services
import { TransactionService } from '../services/TransactionService';
import { AnalyticsService } from '../services/AnalyticsService';

// Types
interface Transaction {
  id: string;
  type: 'sent' | 'received' | 'payment' | 'refund';
  amount: number;
  currency: string;
  description: string;
  recipient?: {
    id: string;
    name: string;
    avatar?: string;
  };
  sender?: {
    id: string;
    name: string;
    avatar?: string;
  };
  status: 'completed' | 'pending' | 'failed';
  timestamp: string;
  category?: string;
}

type FilterType = 'all' | 'sent' | 'received' | 'pending';
type PeriodType = 'week' | 'month' | 'year' | 'all';

const ActivityScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();

  // Redux state
  const { transactions, loading, hasMore } = useSelector(
    (state: RootState) => state.transactions
  );
  const { user } = useSelector((state: RootState) => state.auth);

  // Local state
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<FilterType>('all');
  const [period, setPeriod] = useState<PeriodType>('month');
  const [refreshing, setRefreshing] = useState(false);
  const [showFilters, setShowFilters] = useState(false);

  // Fetch transactions on mount
  useEffect(() => {
    loadTransactions();
    trackScreenView();
  }, [filterType, period]);

  /**
   * Load transactions from backend
   */
  const loadTransactions = async () => {
    try {
      // @ts-ignore
      await dispatch(fetchTransactions({ filter: filterType, period })).unwrap();
    } catch (error) {
      console.error('Failed to load transactions:', error);
    }
  };

  /**
   * Handle pull-to-refresh
   */
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadTransactions();
    setRefreshing(false);
  }, [filterType, period]);

  /**
   * Track screen view for analytics
   */
  const trackScreenView = () => {
    AnalyticsService.trackScreenView('Activity', {
      filter: filterType,
      period,
    });
  };

  /**
   * Navigate to transaction details
   */
  const handleTransactionPress = (transaction: Transaction) => {
    AnalyticsService.trackEvent('transaction_details_viewed', {
      transactionId: transaction.id,
      type: transaction.type,
      amount: transaction.amount,
    });

    navigation.navigate('TransactionDetails', { transactionId: transaction.id });
  };

  /**
   * Filter transactions by search query
   */
  const filteredTransactions = transactions.filter((transaction: Transaction) => {
    if (!searchQuery) return true;

    const query = searchQuery.toLowerCase();
    const matchesDescription = transaction.description?.toLowerCase().includes(query);
    const matchesRecipient = transaction.recipient?.name?.toLowerCase().includes(query);
    const matchesSender = transaction.sender?.name?.toLowerCase().includes(query);
    const matchesAmount = transaction.amount.toString().includes(query);

    return matchesDescription || matchesRecipient || matchesSender || matchesAmount;
  });

  /**
   * Group transactions by date
   */
  const groupedTransactions = groupTransactionsByDate(filteredTransactions);

  /**
   * Calculate period totals
   */
  const periodTotals = calculatePeriodTotals(filteredTransactions);

  /**
   * Render header with search and filters
   */
  const renderHeader = () => (
    <View style={styles.header}>
      {/* Title */}
      <Text style={styles.title}>Activity</Text>

      {/* Period Summary */}
      <View style={styles.summaryCard}>
        <View style={styles.summaryRow}>
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Received</Text>
            <Text style={[styles.summaryValue, styles.positive]}>
              +${periodTotals.received.toFixed(2)}
            </Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryLabel}>Sent</Text>
            <Text style={[styles.summaryValue, styles.negative]}>
              -${periodTotals.sent.toFixed(2)}
            </Text>
          </View>
        </View>
        <View style={styles.summaryFooter}>
          <Text style={styles.summaryNet}>
            Net: ${(periodTotals.received - periodTotals.sent).toFixed(2)}
          </Text>
        </View>
      </View>

      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <Icon name="magnify" size={20} color="#666" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search transactions..."
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholderTextColor="#999"
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity onPress={() => setSearchQuery('')}>
            <Icon name="close-circle" size={20} color="#666" />
          </TouchableOpacity>
        )}
      </View>

      {/* Filters */}
      <View style={styles.filtersContainer}>
        <TouchableOpacity
          style={styles.filterToggle}
          onPress={() => setShowFilters(!showFilters)}
        >
          <Icon name="filter-variant" size={20} color="#007AFF" />
          <Text style={styles.filterToggleText}>Filters</Text>
        </TouchableOpacity>

        {showFilters && (
          <View style={styles.filtersPanel}>
            {/* Type Filters */}
            <View style={styles.filterGroup}>
              <Text style={styles.filterGroupLabel}>Type</Text>
              <View style={styles.filterChips}>
                {(['all', 'sent', 'received', 'pending'] as FilterType[]).map((type) => (
                  <TouchableOpacity
                    key={type}
                    style={[
                      styles.filterChip,
                      filterType === type && styles.filterChipActive,
                    ]}
                    onPress={() => setFilterType(type)}
                  >
                    <Text
                      style={[
                        styles.filterChipText,
                        filterType === type && styles.filterChipTextActive,
                      ]}
                    >
                      {type.charAt(0).toUpperCase() + type.slice(1)}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Period Filters */}
            <View style={styles.filterGroup}>
              <Text style={styles.filterGroupLabel}>Period</Text>
              <View style={styles.filterChips}>
                {(['week', 'month', 'year', 'all'] as PeriodType[]).map((p) => (
                  <TouchableOpacity
                    key={p}
                    style={[
                      styles.filterChip,
                      period === p && styles.filterChipActive,
                    ]}
                    onPress={() => setPeriod(p)}
                  >
                    <Text
                      style={[
                        styles.filterChipText,
                        period === p && styles.filterChipTextActive,
                      ]}
                    >
                      {p.charAt(0).toUpperCase() + p.slice(1)}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>
        )}
      </View>
    </View>
  );

  /**
   * Render section header (date)
   */
  const renderSectionHeader = ({ section }: any) => (
    <View style={styles.sectionHeader}>
      <Text style={styles.sectionHeaderText}>{section.title}</Text>
    </View>
  );

  /**
   * Render transaction item
   */
  const renderTransaction = ({ item }: { item: Transaction }) => {
    const isOutgoing = item.type === 'sent' || item.type === 'payment';
    const otherParty = isOutgoing ? item.recipient : item.sender;
    const statusIcon =
      item.status === 'completed'
        ? 'check-circle'
        : item.status === 'pending'
        ? 'clock-outline'
        : 'alert-circle';
    const statusColor =
      item.status === 'completed'
        ? '#4CAF50'
        : item.status === 'pending'
        ? '#FFA726'
        : '#F44336';

    return (
      <TouchableOpacity
        style={styles.transactionItem}
        onPress={() => handleTransactionPress(item)}
        activeOpacity={0.7}
      >
        {/* Avatar/Icon */}
        <View style={[styles.transactionIcon, { backgroundColor: isOutgoing ? '#FFEBEE' : '#E8F5E9' }]}>
          <Icon
            name={isOutgoing ? 'arrow-up' : 'arrow-down'}
            size={24}
            color={isOutgoing ? '#F44336' : '#4CAF50'}
          />
        </View>

        {/* Details */}
        <View style={styles.transactionDetails}>
          <Text style={styles.transactionTitle}>
            {otherParty?.name || item.description || 'Transaction'}
          </Text>
          <Text style={styles.transactionSubtitle}>
            {item.description || item.category || format(new Date(item.timestamp), 'h:mm a')}
          </Text>
        </View>

        {/* Amount & Status */}
        <View style={styles.transactionRight}>
          <Text
            style={[
              styles.transactionAmount,
              isOutgoing ? styles.amountNegative : styles.amountPositive,
            ]}
          >
            {isOutgoing ? '-' : '+'}${item.amount.toFixed(2)}
          </Text>
          <View style={styles.statusContainer}>
            <Icon name={statusIcon} size={14} color={statusColor} />
            <Text style={[styles.statusText, { color: statusColor }]}>
              {item.status}
            </Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  /**
   * Render empty state
   */
  const renderEmpty = () => (
    <View style={styles.emptyState}>
      <Icon name="receipt-text-outline" size={80} color="#CCC" />
      <Text style={styles.emptyStateTitle}>No transactions yet</Text>
      <Text style={styles.emptyStateText}>
        Your transaction history will appear here
      </Text>
    </View>
  );

  if (loading && !refreshing) {
    return (
      <SafeAreaView style={styles.container}>
        {renderHeader()}
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={filteredTransactions}
        renderItem={renderTransaction}
        keyExtractor={(item) => item.id}
        ListHeaderComponent={renderHeader}
        ListEmptyComponent={renderEmpty}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
      />
    </SafeAreaView>
  );
};

/**
 * Group transactions by date (Today, Yesterday, etc.)
 */
function groupTransactionsByDate(transactions: Transaction[]) {
  // Implementation would group by date
  return transactions;
}

/**
 * Calculate period totals
 */
function calculatePeriodTotals(transactions: Transaction[]) {
  let received = 0;
  let sent = 0;

  transactions.forEach((t) => {
    if (t.status === 'completed') {
      if (t.type === 'received') {
        received += t.amount;
      } else if (t.type === 'sent' || t.type === 'payment') {
        sent += t.amount;
      }
    }
  });

  return { received, sent };
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    backgroundColor: '#FFF',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#000',
    marginBottom: 16,
  },
  summaryCard: {
    backgroundColor: '#F8F8F8',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 12,
  },
  summaryItem: {
    flex: 1,
    alignItems: 'center',
  },
  summaryLabel: {
    fontSize: 13,
    color: '#666',
    marginBottom: 4,
  },
  summaryValue: {
    fontSize: 20,
    fontWeight: '600',
  },
  positive: {
    color: '#4CAF50',
  },
  negative: {
    color: '#F44336',
  },
  summaryDivider: {
    width: 1,
    backgroundColor: '#E0E0E0',
    marginHorizontal: 16,
  },
  summaryFooter: {
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
    alignItems: 'center',
  },
  summaryNet: {
    fontSize: 15,
    fontWeight: '500',
    color: '#333',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F0F0F0',
    borderRadius: 10,
    paddingHorizontal: 12,
    marginBottom: 12,
    height: 44,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
    color: '#000',
  },
  filtersContainer: {
    marginBottom: 8,
  },
  filterToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
  },
  filterToggleText: {
    fontSize: 16,
    color: '#007AFF',
    marginLeft: 6,
    fontWeight: '500',
  },
  filtersPanel: {
    marginTop: 12,
  },
  filterGroup: {
    marginBottom: 12,
  },
  filterGroupLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#666',
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  filterChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#F0F0F0',
    borderWidth: 1,
    borderColor: '#E0E0E0',
  },
  filterChipActive: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  filterChipText: {
    fontSize: 14,
    color: '#666',
    fontWeight: '500',
  },
  filterChipTextActive: {
    color: '#FFF',
  },
  listContent: {
    flexGrow: 1,
  },
  sectionHeader: {
    backgroundColor: '#F5F5F5',
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  sectionHeaderText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#666',
    textTransform: 'uppercase',
  },
  transactionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F0F0',
  },
  transactionIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  transactionDetails: {
    flex: 1,
  },
  transactionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 4,
  },
  transactionSubtitle: {
    fontSize: 13,
    color: '#666',
  },
  transactionRight: {
    alignItems: 'flex-end',
  },
  transactionAmount: {
    fontSize: 17,
    fontWeight: '600',
    marginBottom: 4,
  },
  amountPositive: {
    color: '#4CAF50',
  },
  amountNegative: {
    color: '#F44336',
  },
  statusContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '500',
    textTransform: 'capitalize',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyStateTitle: {
    fontSize: 20,
    fontWeight: '600',
    color: '#333',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyStateText: {
    fontSize: 15,
    color: '#666',
    textAlign: 'center',
  },
});

export default ActivityScreen;
