import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
  SectionList,
  ActivityIndicator,
} from 'react-native';
import {
  Text,
  Searchbar,
  Chip,
  Surface,
  IconButton,
  useTheme,
  List,
  Divider,
  FAB,
  Portal,
  Modal,
  Button,
  RadioButton,
  Checkbox,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { useSelector } from 'react-redux';
import { useInfiniteQuery, useQuery } from 'react-query';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import DateTimePicker from '@react-native-community/datetimepicker';
import Animated, { 
  FadeInDown,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  interpolate,
} from 'react-native-reanimated';

import { RootState } from '../../store/store';
import { transactionService } from '../../services/transactionService';
import { formatCurrency, formatDate, formatDateSection } from '../../utils/formatters';
import Header from '../../components/common/Header';
import TransactionItem from '../../components/wallet/TransactionItem';
import EmptyState from '../../components/common/EmptyState';
import { Transaction, TransactionFilter, TransactionType } from '../../types/transaction';

interface TransactionSection {
  title: string;
  data: Transaction[];
}

/**
 * Transaction History Screen - Comprehensive transaction list with filtering
 */
const TransactionHistoryScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [searchQuery, setSearchQuery] = useState('');
  const [showFilterModal, setShowFilterModal] = useState(false);
  const [showDatePicker, setShowDatePicker] = useState<'start' | 'end' | null>(null);
  const [filters, setFilters] = useState<TransactionFilter>({
    types: [],
    status: 'all',
    dateRange: 'all',
    startDate: null,
    endDate: null,
    minAmount: null,
    maxAmount: null,
  });
  const [tempFilters, setTempFilters] = useState<TransactionFilter>(filters);
  
  const filterAnimation = useSharedValue(0);
  const listRef = useRef<SectionList>(null);
  
  // Redux state
  const { currency } = useSelector((state: RootState) => state.wallet);
  
  // Fetch transactions with infinite scroll
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    refetch,
    isRefetching,
  } = useInfiniteQuery(
    ['transactions', filters, searchQuery],
    ({ pageParam = 0 }) => 
      transactionService.getTransactions({
        page: pageParam,
        limit: 20,
        filters,
        search: searchQuery,
      }),
    {
      getNextPageParam: (lastPage, pages) => {
        if (lastPage.hasMore) {
          return pages.length;
        }
        return undefined;
      },
    }
  );
  
  // Fetch transaction summary
  const { data: summary } = useQuery(
    ['transactionSummary', filters],
    () => transactionService.getTransactionSummary(filters),
    {
      enabled: !isLoading,
    }
  );
  
  // Group transactions by date
  const sections = React.useMemo(() => {
    if (!data?.pages) return [];
    
    const allTransactions = data.pages.flatMap(page => page.transactions);
    const grouped: { [key: string]: Transaction[] } = {};
    
    allTransactions.forEach(transaction => {
      const dateKey = formatDateSection(transaction.createdAt);
      if (!grouped[dateKey]) {
        grouped[dateKey] = [];
      }
      grouped[dateKey].push(transaction);
    });
    
    return Object.entries(grouped).map(([title, transactions]) => ({
      title,
      data: transactions,
    }));
  }, [data]);
  
  const handleLoadMore = () => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  };
  
  const handleRefresh = useCallback(() => {
    refetch();
  }, [refetch]);
  
  const handleSearch = useCallback((query: string) => {
    setSearchQuery(query);
  }, []);
  
  const handleFilterChange = (key: keyof TransactionFilter, value: any) => {
    setTempFilters(prev => ({ ...prev, [key]: value }));
  };
  
  const handleTypeToggle = (type: TransactionType) => {
    setTempFilters(prev => ({
      ...prev,
      types: prev.types.includes(type)
        ? prev.types.filter(t => t !== type)
        : [...prev.types, type],
    }));
  };
  
  const applyFilters = () => {
    setFilters(tempFilters);
    setShowFilterModal(false);
    filterAnimation.value = withTiming(1, { duration: 300 });
  };
  
  const resetFilters = () => {
    const defaultFilters: TransactionFilter = {
      types: [],
      status: 'all',
      dateRange: 'all',
      startDate: null,
      endDate: null,
      minAmount: null,
      maxAmount: null,
    };
    setTempFilters(defaultFilters);
    setFilters(defaultFilters);
    filterAnimation.value = withTiming(0, { duration: 300 });
  };
  
  const handleExport = async () => {
    try {
      const result = await transactionService.exportTransactions(filters);
      // Handle file download/share
      console.log('Export completed:', result);
    } catch (error) {
      console.error('Export failed:', error);
    }
  };
  
  const activeFilterCount = React.useMemo(() => {
    let count = 0;
    if (filters.types.length > 0) count++;
    if (filters.status !== 'all') count++;
    if (filters.dateRange !== 'all') count++;
    if (filters.minAmount || filters.maxAmount) count++;
    return count;
  }, [filters]);
  
  const filterBadgeStyle = useAnimatedStyle(() => {
    const scale = interpolate(
      filterAnimation.value,
      [0, 1],
      [1, 1.2]
    );
    return {
      transform: [{ scale }],
    };
  });
  
  const renderSectionHeader = ({ section }: { section: TransactionSection }) => (
    <Surface style={styles.sectionHeader} elevation={1}>
      <Text style={styles.sectionTitle}>{section.title}</Text>
      <Text style={styles.sectionAmount}>
        {formatCurrency(
          section.data.reduce((sum, t) => sum + Math.abs(t.amount), 0),
          currency
        )}
      </Text>
    </Surface>
  );
  
  const renderTransaction = ({ item }: { item: Transaction }) => (
    <TransactionItem
      transaction={item}
      onPress={() => navigation.navigate('TransactionDetails', {
        transactionId: item.id
      } as never)}
    />
  );
  
  const renderEmpty = () => (
    <EmptyState
      icon="history"
      title="No transactions found"
      description={searchQuery || activeFilterCount > 0
        ? "Try adjusting your search or filters"
        : "Your transaction history will appear here"
      }
      action={
        (searchQuery || activeFilterCount > 0) ? {
          label: "Clear filters",
          onPress: () => {
            setSearchQuery('');
            resetFilters();
          }
        } : undefined
      }
    />
  );
  
  const renderFooter = () => {
    if (!isFetchingNextPage) return null;
    return (
      <View style={styles.footerLoader}>
        <ActivityIndicator size="small" color={theme.colors.primary} />
      </View>
    );
  };
  
  return (
    <View style={styles.container}>
      <Header
        title="Transaction History"
        subtitle={summary ? `${summary.totalCount} transactions` : undefined}
        rightAction={
          <View style={styles.headerActions}>
            <IconButton
              icon="download"
              size={24}
              onPress={handleExport}
            />
            <Animated.View style={filterBadgeStyle}>
              <IconButton
                icon="filter"
                size={24}
                onPress={() => setShowFilterModal(true)}
                style={activeFilterCount > 0 ? styles.activeFilter : undefined}
              />
              {activeFilterCount > 0 && (
                <View style={styles.filterBadge}>
                  <Text style={styles.filterBadgeText}>{activeFilterCount}</Text>
                </View>
              )}
            </Animated.View>
          </View>
        }
      />
      
      {/* Search Bar */}
      <Surface style={styles.searchContainer} elevation={1}>
        <Searchbar
          placeholder="Search transactions..."
          value={searchQuery}
          onChangeText={handleSearch}
          style={styles.searchBar}
          icon="magnify"
          clearIcon="close"
        />
      </Surface>
      
      {/* Summary Stats */}
      {summary && (
        <Animated.View entering={FadeInDown}>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.summaryContainer}
          >
            <Surface style={styles.summaryCard}>
              <Icon name="arrow-down" size={20} color="#4CAF50" />
              <Text style={styles.summaryLabel}>Income</Text>
              <Text style={styles.summaryValue}>
                {formatCurrency(summary.totalIncome, currency)}
              </Text>
            </Surface>
            
            <Surface style={styles.summaryCard}>
              <Icon name="arrow-up" size={20} color="#FF5252" />
              <Text style={styles.summaryLabel}>Expenses</Text>
              <Text style={styles.summaryValue}>
                {formatCurrency(Math.abs(summary.totalExpenses), currency)}
              </Text>
            </Surface>
            
            <Surface style={styles.summaryCard}>
              <Icon name="wallet" size={20} color="#2196F3" />
              <Text style={styles.summaryLabel}>Net</Text>
              <Text style={styles.summaryValue}>
                {formatCurrency(summary.netAmount, currency)}
              </Text>
            </Surface>
            
            <Surface style={styles.summaryCard}>
              <Icon name="sigma" size={20} color="#FF9800" />
              <Text style={styles.summaryLabel}>Average</Text>
              <Text style={styles.summaryValue}>
                {formatCurrency(summary.averageAmount, currency)}
              </Text>
            </Surface>
          </ScrollView>
        </Animated.View>
      )}
      
      {/* Transaction List */}
      <SectionList
        ref={listRef}
        sections={sections}
        keyExtractor={(item) => item.id}
        renderSectionHeader={renderSectionHeader}
        renderItem={renderTransaction}
        ItemSeparatorComponent={() => <Divider />}
        ListEmptyComponent={renderEmpty}
        ListFooterComponent={renderFooter}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={handleRefresh}
          />
        }
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.5}
        contentContainerStyle={styles.listContent}
        stickySectionHeadersEnabled
      />
      
      {/* Filter Modal */}
      <Portal>
        <Modal
          visible={showFilterModal}
          onDismiss={() => setShowFilterModal(false)}
          contentContainerStyle={styles.filterModal}
        >
          <ScrollView showsVerticalScrollIndicator={false}>
            <Text style={styles.filterTitle}>Filter Transactions</Text>
            
            {/* Transaction Types */}
            <View style={styles.filterSection}>
              <Text style={styles.filterSectionTitle}>Transaction Type</Text>
              <View style={styles.typeGrid}>
                {['PAYMENT', 'REQUEST', 'TRANSFER', 'DEPOSIT', 'WITHDRAWAL'].map((type) => (
                  <TouchableOpacity
                    key={type}
                    style={[
                      styles.typeChip,
                      tempFilters.types.includes(type as TransactionType) && styles.typeChipSelected
                    ]}
                    onPress={() => handleTypeToggle(type as TransactionType)}
                  >
                    <Icon
                      name={getTransactionIcon(type as TransactionType)}
                      size={20}
                      color={tempFilters.types.includes(type as TransactionType) 
                        ? 'white' 
                        : theme.colors.onSurface
                      }
                    />
                    <Text style={[
                      styles.typeChipText,
                      tempFilters.types.includes(type as TransactionType) && styles.typeChipTextSelected
                    ]}>
                      {type.charAt(0) + type.slice(1).toLowerCase()}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
            
            {/* Status Filter */}
            <View style={styles.filterSection}>
              <Text style={styles.filterSectionTitle}>Status</Text>
              <RadioButton.Group
                onValueChange={(value) => handleFilterChange('status', value)}
                value={tempFilters.status}
              >
                <TouchableOpacity
                  style={styles.radioOption}
                  onPress={() => handleFilterChange('status', 'all')}
                >
                  <RadioButton value="all" />
                  <Text style={styles.radioLabel}>All</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.radioOption}
                  onPress={() => handleFilterChange('status', 'completed')}
                >
                  <RadioButton value="completed" />
                  <Text style={styles.radioLabel}>Completed</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.radioOption}
                  onPress={() => handleFilterChange('status', 'pending')}
                >
                  <RadioButton value="pending" />
                  <Text style={styles.radioLabel}>Pending</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.radioOption}
                  onPress={() => handleFilterChange('status', 'failed')}
                >
                  <RadioButton value="failed" />
                  <Text style={styles.radioLabel}>Failed</Text>
                </TouchableOpacity>
              </RadioButton.Group>
            </View>
            
            {/* Date Range */}
            <View style={styles.filterSection}>
              <Text style={styles.filterSectionTitle}>Date Range</Text>
              <View style={styles.dateRangeGrid}>
                {[
                  { value: 'all', label: 'All Time' },
                  { value: 'today', label: 'Today' },
                  { value: 'week', label: 'This Week' },
                  { value: 'month', label: 'This Month' },
                  { value: 'year', label: 'This Year' },
                  { value: 'custom', label: 'Custom' },
                ].map((option) => (
                  <Chip
                    key={option.value}
                    mode={tempFilters.dateRange === option.value ? 'flat' : 'outlined'}
                    selected={tempFilters.dateRange === option.value}
                    onPress={() => handleFilterChange('dateRange', option.value)}
                    style={styles.dateRangeChip}
                  >
                    {option.label}
                  </Chip>
                ))}
              </View>
              
              {tempFilters.dateRange === 'custom' && (
                <View style={styles.customDateContainer}>
                  <TouchableOpacity
                    style={styles.dateButton}
                    onPress={() => setShowDatePicker('start')}
                  >
                    <Icon name="calendar" size={20} color={theme.colors.onSurface} />
                    <Text style={styles.dateButtonText}>
                      {tempFilters.startDate 
                        ? formatDate(tempFilters.startDate)
                        : 'Start Date'
                      }
                    </Text>
                  </TouchableOpacity>
                  
                  <TouchableOpacity
                    style={styles.dateButton}
                    onPress={() => setShowDatePicker('end')}
                  >
                    <Icon name="calendar" size={20} color={theme.colors.onSurface} />
                    <Text style={styles.dateButtonText}>
                      {tempFilters.endDate 
                        ? formatDate(tempFilters.endDate)
                        : 'End Date'
                      }
                    </Text>
                  </TouchableOpacity>
                </View>
              )}
            </View>
            
            {/* Filter Actions */}
            <View style={styles.filterActions}>
              <Button
                mode="outlined"
                onPress={() => {
                  setTempFilters(filters);
                  setShowFilterModal(false);
                }}
                style={styles.filterButton}
              >
                Cancel
              </Button>
              <Button
                mode="text"
                onPress={() => setTempFilters({
                  types: [],
                  status: 'all',
                  dateRange: 'all',
                  startDate: null,
                  endDate: null,
                  minAmount: null,
                  maxAmount: null,
                })}
                style={styles.filterButton}
              >
                Reset
              </Button>
              <Button
                mode="contained"
                onPress={applyFilters}
                style={styles.filterButton}
              >
                Apply
              </Button>
            </View>
          </ScrollView>
        </Modal>
      </Portal>
      
      {/* Date Picker */}
      {showDatePicker && (
        <DateTimePicker
          value={
            showDatePicker === 'start' 
              ? tempFilters.startDate || new Date()
              : tempFilters.endDate || new Date()
          }
          mode="date"
          onChange={(event, date) => {
            setShowDatePicker(null);
            if (date) {
              handleFilterChange(
                showDatePicker === 'start' ? 'startDate' : 'endDate',
                date
              );
            }
          }}
        />
      )}
      
      {/* Scroll to Top FAB */}
      <FAB
        icon="arrow-up"
        style={styles.scrollToTopFab}
        onPress={() => listRef.current?.scrollToLocation({
          sectionIndex: 0,
          itemIndex: 0,
          animated: true,
        })}
        visible={sections.length > 0}
        small
      />
    </View>
  );
};

const getTransactionIcon = (type: TransactionType): string => {
  switch (type) {
    case 'PAYMENT':
      return 'send';
    case 'REQUEST':
      return 'call-received';
    case 'TRANSFER':
      return 'swap-horizontal';
    case 'DEPOSIT':
      return 'bank-plus';
    case 'WITHDRAWAL':
      return 'bank-minus';
    default:
      return 'cash';
  }
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  activeFilter: {
    backgroundColor: '#E3F2FD',
    borderRadius: 20,
  },
  filterBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: '#2196F3',
    borderRadius: 10,
    width: 20,
    height: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  filterBadgeText: {
    color: 'white',
    fontSize: 12,
    fontWeight: 'bold',
  },
  searchContainer: {
    margin: 16,
    marginBottom: 8,
    borderRadius: 8,
    elevation: 2,
  },
  searchBar: {
    elevation: 0,
    backgroundColor: 'transparent',
  },
  summaryContainer: {
    paddingHorizontal: 16,
    paddingBottom: 8,
    gap: 12,
  },
  summaryCard: {
    padding: 12,
    borderRadius: 12,
    minWidth: 100,
    alignItems: 'center',
    elevation: 1,
  },
  summaryLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
  summaryValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginTop: 2,
  },
  listContent: {
    flexGrow: 1,
    paddingBottom: 80,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#f5f5f5',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  sectionAmount: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  footerLoader: {
    paddingVertical: 20,
    alignItems: 'center',
  },
  filterModal: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    padding: 20,
    maxHeight: '80%',
  },
  filterTitle: {
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 20,
  },
  filterSection: {
    marginBottom: 24,
  },
  filterSectionTitle: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 12,
    color: '#333',
  },
  typeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  typeChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#f0f0f0',
    gap: 6,
  },
  typeChipSelected: {
    backgroundColor: '#2196F3',
  },
  typeChipText: {
    fontSize: 14,
    color: '#333',
  },
  typeChipTextSelected: {
    color: 'white',
  },
  radioOption: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
  },
  radioLabel: {
    fontSize: 16,
    marginLeft: 8,
  },
  dateRangeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  dateRangeChip: {
    marginBottom: 8,
  },
  customDateContainer: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 12,
  },
  dateButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    borderRadius: 8,
    backgroundColor: '#f0f0f0',
  },
  dateButtonText: {
    fontSize: 14,
    color: '#333',
  },
  filterActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 24,
  },
  filterButton: {
    minWidth: 80,
  },
  scrollToTopFab: {
    position: 'absolute',
    margin: 16,
    right: 0,
    bottom: 16,
  },
});

export default TransactionHistoryScreen;