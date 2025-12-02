/**
 * Optimized React Components with React.memo and performance improvements
 * These components are designed for maximum performance in production
 */

import React, { memo, useCallback, useMemo, forwardRef, useState, useEffect } from 'react';
import { 
  View, 
  Text, 
  TouchableOpacity, 
  FlatList, 
  StyleSheet, 
  Image, 
  ActivityIndicator,
  VirtualizedList,
  Dimensions,
  Platform
} from 'react-native';

// Types
interface Transaction {
  id: string;
  type: 'send' | 'receive' | 'payment' | 'refund';
  amount: number;
  currency: string;
  recipient?: string;
  sender?: string;
  date: Date;
  status: 'pending' | 'completed' | 'failed';
  description?: string;
  category?: string;
  avatar?: string;
  metadata?: Record<string, unknown>;
}

interface OptimizedTransactionItemProps {
  transaction: Transaction;
  onPress: (id: string) => void;
  onLongPress?: (id: string) => void;
  style?: any;
  testID?: string;
}

interface OptimizedTransactionListProps {
  transactions: Transaction[];
  onTransactionPress: (id: string) => void;
  onRefresh?: () => Promise<void>;
  onLoadMore?: () => Promise<void>;
  isLoading?: boolean;
  isRefreshing?: boolean;
  hasMore?: boolean;
  itemHeight?: number;
  windowSize?: number;
  initialNumToRender?: number;
  maxToRenderPerBatch?: number;
  updateCellsBatchingPeriod?: number;
  removeClippedSubviews?: boolean;
  getItemLayout?: (data: Transaction[] | null, index: number) => { length: number; offset: number; index: number };
}

interface OptimizedUserAvatarProps {
  userId?: string;
  name?: string;
  avatar?: string;
  size?: number;
  backgroundColor?: string;
  textColor?: string;
  onPress?: (userId: string) => void;
  showBadge?: boolean;
  badgeCount?: number;
  isOnline?: boolean;
  style?: any;
}

interface OptimizedAmountDisplayProps {
  amount: number;
  currency: string;
  type?: 'send' | 'receive' | 'neutral';
  showSign?: boolean;
  style?: any;
  precision?: number;
}

// Optimized Transaction Item Component
export const OptimizedTransactionItem = memo<OptimizedTransactionItemProps>(
  ({ transaction, onPress, onLongPress, style, testID }) => {
    // Memoize press handlers to prevent unnecessary re-renders
    const handlePress = useCallback(() => {
      onPress(transaction.id);
    }, [onPress, transaction.id]);

    const handleLongPress = useCallback(() => {
      onLongPress?.(transaction.id);
    }, [onLongPress, transaction.id]);

    // Memoize computed values
    const isOutgoing = useMemo(() => 
      transaction.type === 'send' || transaction.type === 'payment', 
      [transaction.type]
    );

    const statusColor = useMemo(() => {
      switch (transaction.status) {
        case 'completed': return '#4CAF50';
        case 'pending': return '#FF9800';
        case 'failed': return '#F44336';
        default: return '#757575';
      }
    }, [transaction.status]);

    const formattedAmount = useMemo(() => {
      const sign = isOutgoing ? '-' : '+';
      return `${sign}$${Math.abs(transaction.amount).toFixed(2)}`;
    }, [transaction.amount, isOutgoing]);

    const formattedDate = useMemo(() => {
      const now = new Date();
      const transactionDate = new Date(transaction.date);
      const diffInHours = (now.getTime() - transactionDate.getTime()) / (1000 * 60 * 60);
      
      if (diffInHours < 24) {
        return transactionDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      } else if (diffInHours < 168) { // 7 days
        return transactionDate.toLocaleDateString([], { weekday: 'short' });
      } else {
        return transactionDate.toLocaleDateString([], { month: 'short', day: 'numeric' });
      }
    }, [transaction.date]);

    const recipientName = useMemo(() => 
      transaction.recipient || transaction.sender || 'Unknown', 
      [transaction.recipient, transaction.sender]
    );

    return (
      <TouchableOpacity
        style={[styles.transactionItem, style]}
        onPress={handlePress}
        onLongPress={handleLongPress}
        testID={testID}
        activeOpacity={0.7}
      >
        <OptimizedUserAvatar
          name={recipientName}
          avatar={transaction.avatar}
          size={40}
          userId={transaction.id}
        />
        
        <View style={styles.transactionContent}>
          <View style={styles.transactionHeader}>
            <Text style={styles.recipientName} numberOfLines={1}>
              {recipientName}
            </Text>
            <Text style={[styles.amount, { color: isOutgoing ? '#F44336' : '#4CAF50' }]}>
              {formattedAmount}
            </Text>
          </View>
          
          <View style={styles.transactionDetails}>
            <Text style={styles.description} numberOfLines={1}>
              {transaction.description || transaction.type}
            </Text>
            <View style={styles.statusContainer}>
              <View style={[styles.statusDot, { backgroundColor: statusColor }]} />
              <Text style={styles.date}>{formattedDate}</Text>
            </View>
          </View>
        </View>
      </TouchableOpacity>
    );
  },
  // Custom comparison function for better performance
  (prevProps, nextProps) => {
    return (
      prevProps.transaction.id === nextProps.transaction.id &&
      prevProps.transaction.status === nextProps.transaction.status &&
      prevProps.transaction.amount === nextProps.transaction.amount &&
      prevProps.onPress === nextProps.onPress &&
      prevProps.onLongPress === nextProps.onLongPress
    );
  }
);

// Optimized User Avatar Component
export const OptimizedUserAvatar = memo<OptimizedUserAvatarProps>(
  ({ 
    userId, 
    name = '', 
    avatar, 
    size = 40, 
    backgroundColor = '#E0E0E0', 
    textColor = '#424242',
    onPress,
    showBadge = false,
    badgeCount = 0,
    isOnline = false,
    style 
  }) => {
    const initials = useMemo(() => {
      if (!name) return '?';
      return name
        .split(' ')
        .slice(0, 2)
        .map(word => word.charAt(0).toUpperCase())
        .join('');
    }, [name]);

    const handlePress = useCallback(() => {
      if (onPress && userId) {
        onPress(userId);
      }
    }, [onPress, userId]);

    const avatarStyle = useMemo(() => ({
      width: size,
      height: size,
      borderRadius: size / 2,
      backgroundColor,
      ...style
    }), [size, backgroundColor, style]);

    const textStyle = useMemo(() => ({
      fontSize: size * 0.4,
      color: textColor,
    }), [size, textColor]);

    return (
      <TouchableOpacity
        style={avatarStyle}
        onPress={handlePress}
        disabled={!onPress}
        activeOpacity={0.8}
      >
        {avatar ? (
          <Image
            source={{ uri: avatar }}
            style={avatarStyle}
            defaultSource={require('../../../assets/default-avatar.png')}
          />
        ) : (
          <View style={[avatarStyle, styles.avatarPlaceholder]}>
            <Text style={[styles.avatarText, textStyle]}>
              {initials}
            </Text>
          </View>
        )}
        
        {isOnline && (
          <View style={[styles.onlineIndicator, { 
            width: size * 0.3, 
            height: size * 0.3,
            bottom: size * 0.05,
            right: size * 0.05
          }]} />
        )}
        
        {showBadge && badgeCount > 0 && (
          <View style={[styles.badge, { 
            top: -size * 0.1, 
            right: -size * 0.1,
            minWidth: size * 0.5,
            height: size * 0.5
          }]}>
            <Text style={[styles.badgeText, { fontSize: size * 0.25 }]}>
              {badgeCount > 99 ? '99+' : badgeCount}
            </Text>
          </View>
        )}
      </TouchableOpacity>
    );
  }
);

// Optimized Amount Display Component
export const OptimizedAmountDisplay = memo<OptimizedAmountDisplayProps>(
  ({ amount, currency, type = 'neutral', showSign = true, style, precision = 2 }) => {
    const formattedAmount = useMemo(() => {
      const formatter = new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currency,
        minimumFractionDigits: precision,
        maximumFractionDigits: precision,
      });
      
      const absAmount = Math.abs(amount);
      let formatted = formatter.format(absAmount);
      
      if (showSign) {
        if (type === 'send' && amount > 0) {
          formatted = `-${formatted}`;
        } else if (type === 'receive' && amount > 0) {
          formatted = `+${formatted}`;
        }
      }
      
      return formatted;
    }, [amount, currency, type, showSign, precision]);

    const textStyle = useMemo(() => {
      let color = '#424242';
      
      if (type === 'send') {
        color = '#F44336';
      } else if (type === 'receive') {
        color = '#4CAF50';
      }
      
      return [styles.amountText, { color }, style];
    }, [type, style]);

    return (
      <Text style={textStyle}>
        {formattedAmount}
      </Text>
    );
  }
);

// Optimized Transaction List Component with Virtualization
export const OptimizedTransactionList = memo<OptimizedTransactionListProps>(
  ({ 
    transactions,
    onTransactionPress,
    onRefresh,
    onLoadMore,
    isLoading = false,
    isRefreshing = false,
    hasMore = false,
    itemHeight = 80,
    windowSize = 10,
    initialNumToRender = 10,
    maxToRenderPerBatch = 10,
    updateCellsBatchingPeriod = 50,
    removeClippedSubviews = Platform.OS === 'android',
    getItemLayout
  }) => {
    // Memoize the key extractor to prevent unnecessary re-renders
    const keyExtractor = useCallback((item: Transaction) => item.id, []);

    // Memoize the render item function
    const renderItem = useCallback(({ item }: { item: Transaction }) => (
      <OptimizedTransactionItem
        transaction={item}
        onPress={onTransactionPress}
        testID={`transaction-item-${item.id}`}
      />
    ), [onTransactionPress]);

    // Memoize the item separator component
    const ItemSeparatorComponent = useCallback(() => (
      <View style={styles.separator} />
    ), []);

    // Memoize the empty component
    const ListEmptyComponent = useCallback(() => (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>No transactions found</Text>
      </View>
    ), []);

    // Memoize the footer component
    const ListFooterComponent = useCallback(() => {
      if (!hasMore && !isLoading) return null;
      
      return (
        <View style={styles.footerContainer}>
          {isLoading && <ActivityIndicator size="small" color="#2196F3" />}
        </View>
      );
    }, [hasMore, isLoading]);

    // Memoize the default getItemLayout if not provided
    const memoizedGetItemLayout = useMemo(() => {
      if (getItemLayout) return getItemLayout;
      
      return (data: Transaction[] | null, index: number) => ({
        length: itemHeight,
        offset: itemHeight * index,
        index,
      });
    }, [getItemLayout, itemHeight]);

    const handleEndReached = useCallback(() => {
      if (hasMore && !isLoading && onLoadMore) {
        onLoadMore();
      }
    }, [hasMore, isLoading, onLoadMore]);

    return (
      <FlatList
        data={transactions}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ItemSeparatorComponent={ItemSeparatorComponent}
        ListEmptyComponent={ListEmptyComponent}
        ListFooterComponent={ListFooterComponent}
        getItemLayout={memoizedGetItemLayout}
        onEndReached={handleEndReached}
        onEndReachedThreshold={0.1}
        onRefresh={onRefresh}
        refreshing={isRefreshing}
        windowSize={windowSize}
        initialNumToRender={initialNumToRender}
        maxToRenderPerBatch={maxToRenderPerBatch}
        updateCellsBatchingPeriod={updateCellsBatchingPeriod}
        removeClippedSubviews={removeClippedSubviews}
        showsVerticalScrollIndicator={false}
        style={styles.list}
      />
    );
  }
);

// Optimized Search/Filter Component
interface OptimizedSearchFilterProps {
  onSearch: (query: string) => void;
  onFilter: (filters: any) => void;
  placeholder?: string;
  debounceMs?: number;
  showFilters?: boolean;
}

export const OptimizedSearchFilter = memo<OptimizedSearchFilterProps>(
  ({ onSearch, onFilter, placeholder = 'Search transactions...', debounceMs = 300, showFilters = true }) => {
    const [query, setQuery] = useState('');
    const [debouncedQuery, setDebouncedQuery] = useState('');

    // Debounce search query
    useEffect(() => {
      const timer = setTimeout(() => {
        setDebouncedQuery(query);
      }, debounceMs);

      return () => clearTimeout(timer);
    }, [query, debounceMs]);

    // Trigger search when debounced query changes
    useEffect(() => {
      onSearch(debouncedQuery);
    }, [debouncedQuery, onSearch]);

    const handleTextChange = useCallback((text: string) => {
      setQuery(text);
    }, []);

    return (
      <View style={styles.searchContainer}>
        <View style={styles.searchInputContainer}>
          <Text>🔍</Text>
          <Text
            style={styles.searchInput}
            onChangeText={handleTextChange}
            placeholder={placeholder}
            value={query}
          />
        </View>
        
        {showFilters && (
          <TouchableOpacity
            style={styles.filterButton}
            onPress={() => {/* Implement filter modal */}}
          >
            <Text>⚙️</Text>
          </TouchableOpacity>
        )}
      </View>
    );
  }
);

// Performance monitoring hook
export const usePerformanceMonitoring = (componentName: string) => {
  useEffect(() => {
    const startTime = performance.now();
    
    return () => {
      const endTime = performance.now();
      const renderTime = endTime - startTime;
      
      if (renderTime > 16.67) { // More than 1 frame at 60fps
        console.warn(`${componentName} render time: ${renderTime.toFixed(2)}ms`);
      }
    };
  });
};

// Styles
const styles = StyleSheet.create({
  transactionItem: {
    flexDirection: 'row',
    padding: 16,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
  },
  transactionContent: {
    flex: 1,
    marginLeft: 12,
  },
  transactionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  recipientName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    flex: 1,
  },
  amount: {
    fontSize: 16,
    fontWeight: '600',
  },
  transactionDetails: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  description: {
    fontSize: 14,
    color: '#757575',
    flex: 1,
  },
  statusContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 4,
  },
  date: {
    fontSize: 12,
    color: '#9E9E9E',
  },
  avatarPlaceholder: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    fontWeight: '600',
  },
  onlineIndicator: {
    position: 'absolute',
    backgroundColor: '#4CAF50',
    borderRadius: 50,
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  badge: {
    position: 'absolute',
    backgroundColor: '#F44336',
    borderRadius: 50,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  badgeText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  amountText: {
    fontSize: 16,
    fontWeight: '600',
  },
  list: {
    backgroundColor: '#F5F5F5',
  },
  separator: {
    height: 1,
    backgroundColor: '#E0E0E0',
    marginLeft: 68,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyText: {
    fontSize: 16,
    color: '#9E9E9E',
  },
  footerContainer: {
    paddingVertical: 16,
    alignItems: 'center',
  },
  searchContainer: {
    flexDirection: 'row',
    padding: 16,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
  },
  searchInputContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  searchInput: {
    flex: 1,
    marginLeft: 8,
    fontSize: 16,
    color: '#212121',
  },
  filterButton: {
    marginLeft: 12,
    padding: 8,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
  },
});

// Set display names for better debugging
OptimizedTransactionItem.displayName = 'OptimizedTransactionItem';
OptimizedUserAvatar.displayName = 'OptimizedUserAvatar';
OptimizedAmountDisplay.displayName = 'OptimizedAmountDisplay';
OptimizedTransactionList.displayName = 'OptimizedTransactionList';
OptimizedSearchFilter.displayName = 'OptimizedSearchFilter';

export default {
  OptimizedTransactionItem,
  OptimizedUserAvatar,
  OptimizedAmountDisplay,
  OptimizedTransactionList,
  OptimizedSearchFilter,
  usePerformanceMonitoring,
};