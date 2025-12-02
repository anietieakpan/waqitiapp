/**
 * High-Performance Virtualized Lists for Large Datasets
 * Implements windowing/virtualization to handle thousands of items efficiently
 */

import React, { 
  memo, 
  useCallback, 
  useMemo, 
  useRef, 
  useEffect, 
  useState,
  forwardRef,
  useImperativeHandle
} from 'react';
import {
  View,
  ScrollView,
  Dimensions,
  StyleSheet,
  Text,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  Platform,
  LayoutChangeEvent,
  NativeScrollEvent,
  NativeSyntheticEvent,
} from 'react-native';
import { Logger } from '../../services/src/LoggingService';

// Types
export interface VirtualizedListItem {
  id: string;
  height?: number;
  data: any;
}

export interface VirtualizedListProps<T extends VirtualizedListItem> {
  data: T[];
  renderItem: (item: T, index: number) => React.ReactNode;
  itemHeight?: number | ((item: T, index: number) => number);
  estimatedItemHeight?: number;
  windowSize?: number;
  overscan?: number;
  onEndReached?: () => Promise<void>;
  onEndReachedThreshold?: number;
  onRefresh?: () => Promise<void>;
  refreshing?: boolean;
  loading?: boolean;
  ListEmptyComponent?: React.ComponentType;
  ListHeaderComponent?: React.ComponentType;
  ListFooterComponent?: React.ComponentType;
  keyExtractor?: (item: T, index: number) => string;
  onScroll?: (event: NativeSyntheticEvent<NativeScrollEvent>) => void;
  scrollEventThrottle?: number;
  maintainVisibleContentPosition?: {
    minIndexForVisible: number;
    autoscrollToTopThreshold?: number;
  };
  onItemsRendered?: (startIndex: number, endIndex: number, totalItems: number) => void;
  testID?: string;
}

export interface VirtualizedListRef {
  scrollToIndex: (index: number, animated?: boolean) => void;
  scrollToOffset: (offset: number, animated?: boolean) => void;
  scrollToTop: (animated?: boolean) => void;
  scrollToEnd: (animated?: boolean) => void;
  getScrollOffset: () => number;
  getVisibleRange: () => { start: number; end: number };
}

interface VirtualizedItem {
  index: number;
  top: number;
  height: number;
  bottom: number;
  isVisible: boolean;
}

// Constants
const SCREEN_HEIGHT = Dimensions.get('window').height;
const DEFAULT_ITEM_HEIGHT = 60;
const DEFAULT_WINDOW_SIZE = Math.ceil(SCREEN_HEIGHT / DEFAULT_ITEM_HEIGHT) * 2;
const DEFAULT_OVERSCAN = 5;

// Main Virtualized List Component
export const VirtualizedList = memo(
  forwardRef<VirtualizedListRef, VirtualizedListProps<VirtualizedListItem>>(
    (
      {
        data,
        renderItem,
        itemHeight = DEFAULT_ITEM_HEIGHT,
        estimatedItemHeight = DEFAULT_ITEM_HEIGHT,
        windowSize = DEFAULT_WINDOW_SIZE,
        overscan = DEFAULT_OVERSCAN,
        onEndReached,
        onEndReachedThreshold = 0.2,
        onRefresh,
        refreshing = false,
        loading = false,
        ListEmptyComponent,
        ListHeaderComponent,
        ListFooterComponent,
        keyExtractor = (item, index) => item.id || String(index),
        onScroll,
        scrollEventThrottle = 16,
        maintainVisibleContentPosition,
        onItemsRendered,
        testID,
      },
      ref
    ) => {
      // State
      const [scrollOffset, setScrollOffset] = useState(0);
      const [containerHeight, setContainerHeight] = useState(SCREEN_HEIGHT);
      const [isScrolling, setIsScrolling] = useState(false);
      const [measuredHeights, setMeasuredHeights] = useState<Map<number, number>>(new Map());

      // Refs
      const scrollViewRef = useRef<ScrollView>(null);
      const scrollTimeoutRef = useRef<NodeJS.Timeout>();
      const itemRefs = useRef<Map<number, View>>(new Map());
      const lastScrollOffset = useRef(0);

      // Memoized calculations
      const getItemHeight = useCallback(
        (item: VirtualizedListItem, index: number): number => {
          // Check if we have a measured height
          const measured = measuredHeights.get(index);
          if (measured) return measured;

          // Use provided height function or default
          if (typeof itemHeight === 'function') {
            return itemHeight(item, index);
          }
          return typeof itemHeight === 'number' ? itemHeight : estimatedItemHeight;
        },
        [itemHeight, estimatedItemHeight, measuredHeights]
      );

      // Calculate virtual items with positions
      const virtualItems = useMemo(() => {
        const items: VirtualizedItem[] = [];
        let top = 0;

        for (let i = 0; i < data.length; i++) {
          const height = getItemHeight(data[i], i);
          const bottom = top + height;
          
          items.push({
            index: i,
            top,
            height,
            bottom,
            isVisible: false, // Will be calculated later
          });
          
          top = bottom;
        }

        return items;
      }, [data, getItemHeight]);

      // Calculate total height
      const totalHeight = useMemo(() => {
        return virtualItems.length > 0 
          ? virtualItems[virtualItems.length - 1].bottom 
          : 0;
      }, [virtualItems]);

      // Calculate visible range
      const visibleRange = useMemo(() => {
        const start = Math.max(0, scrollOffset);
        const end = start + containerHeight;

        let startIndex = 0;
        let endIndex = virtualItems.length - 1;

        // Binary search for start index
        let left = 0;
        let right = virtualItems.length - 1;
        while (left <= right) {
          const mid = Math.floor((left + right) / 2);
          if (virtualItems[mid].bottom <= start) {
            left = mid + 1;
          } else {
            right = mid - 1;
            startIndex = mid;
          }
        }

        // Binary search for end index
        left = startIndex;
        right = virtualItems.length - 1;
        while (left <= right) {
          const mid = Math.floor((left + right) / 2);
          if (virtualItems[mid].top < end) {
            left = mid + 1;
            endIndex = mid;
          } else {
            right = mid - 1;
          }
        }

        return {
          start: Math.max(0, startIndex - overscan),
          end: Math.min(virtualItems.length - 1, endIndex + overscan),
        };
      }, [scrollOffset, containerHeight, virtualItems, overscan]);

      // Get visible items
      const visibleItems = useMemo(() => {
        return virtualItems.slice(visibleRange.start, visibleRange.end + 1).map(item => ({
          ...item,
          isVisible: true,
        }));
      }, [virtualItems, visibleRange]);

      // Imperative handle for ref
      useImperativeHandle(ref, () => ({
        scrollToIndex: (index: number, animated = true) => {
          const item = virtualItems[index];
          if (item && scrollViewRef.current) {
            scrollViewRef.current.scrollTo({
              y: item.top,
              animated,
            });
          }
        },
        scrollToOffset: (offset: number, animated = true) => {
          if (scrollViewRef.current) {
            scrollViewRef.current.scrollTo({
              y: offset,
              animated,
            });
          }
        },
        scrollToTop: (animated = true) => {
          if (scrollViewRef.current) {
            scrollViewRef.current.scrollTo({
              y: 0,
              animated,
            });
          }
        },
        scrollToEnd: (animated = true) => {
          if (scrollViewRef.current) {
            scrollViewRef.current.scrollToEnd({ animated });
          }
        },
        getScrollOffset: () => scrollOffset,
        getVisibleRange: () => visibleRange,
      }));

      // Handle scroll
      const handleScroll = useCallback(
        (event: NativeSyntheticEvent<NativeScrollEvent>) => {
          const newOffset = event.nativeEvent.contentOffset.y;
          setScrollOffset(newOffset);
          setIsScrolling(true);

          // Clear existing timeout
          if (scrollTimeoutRef.current) {
            clearTimeout(scrollTimeoutRef.current);
          }

          // Set scrolling to false after scroll ends
          scrollTimeoutRef.current = setTimeout(() => {
            setIsScrolling(false);
          }, 150);

          // Handle end reached
          if (onEndReached && !loading) {
            const { contentSize, contentOffset, layoutMeasurement } = event.nativeEvent;
            const threshold = onEndReachedThreshold * contentSize.height;
            const isNearEnd = 
              contentOffset.y + layoutMeasurement.height >= contentSize.height - threshold;
            
            if (isNearEnd && newOffset > lastScrollOffset.current) {
              onEndReached().catch(error => {
                Logger.error('VirtualizedList onEndReached error', error);
              });
            }
          }

          lastScrollOffset.current = newOffset;
          onScroll?.(event);
        },
        [onEndReached, onEndReachedThreshold, loading, onScroll]
      );

      // Handle item layout measurement
      const handleItemLayout = useCallback((index: number, height: number) => {
        setMeasuredHeights(prev => {
          const newMap = new Map(prev);
          if (newMap.get(index) !== height) {
            newMap.set(index, height);
            return newMap;
          }
          return prev;
        });
      }, []);

      // Handle container layout
      const handleContainerLayout = useCallback((event: LayoutChangeEvent) => {
        const { height } = event.nativeEvent.layout;
        setContainerHeight(height);
      }, []);

      // Effect to notify about rendered items
      useEffect(() => {
        onItemsRendered?.(visibleRange.start, visibleRange.end, data.length);
      }, [visibleRange.start, visibleRange.end, data.length, onItemsRendered]);

      // Render visible items
      const renderVisibleItems = useCallback(() => {
        if (data.length === 0) {
          return ListEmptyComponent ? <ListEmptyComponent /> : (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>No items to display</Text>
            </View>
          );
        }

        return visibleItems.map(virtualItem => {
          const item = data[virtualItem.index];
          return (
            <VirtualizedItemWrapper
              key={keyExtractor(item, virtualItem.index)}
              item={item}
              index={virtualItem.index}
              top={virtualItem.top}
              height={virtualItem.height}
              onLayout={handleItemLayout}
              renderItem={renderItem}
            />
          );
        });
      }, [data, visibleItems, keyExtractor, renderItem, handleItemLayout, ListEmptyComponent]);

      // Performance monitoring
      useEffect(() => {
        const startTime = performance.now();
        
        return () => {
          const renderTime = performance.now() - startTime;
          if (renderTime > 16.67) { // More than 1 frame at 60fps
            Logger.warn('VirtualizedList slow render', {
              renderTime: `${renderTime.toFixed(2)}ms`,
              itemCount: data.length,
              visibleCount: visibleItems.length,
            });
          }
        };
      }, [data.length, visibleItems.length]);

      return (
        <View style={styles.container} onLayout={handleContainerLayout} testID={testID}>
          <ScrollView
            ref={scrollViewRef}
            style={styles.scrollView}
            onScroll={handleScroll}
            scrollEventThrottle={scrollEventThrottle}
            removeClippedSubviews={Platform.OS === 'android'}
            showsVerticalScrollIndicator={true}
            refreshControl={
              onRefresh ? (
                <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
              ) : undefined
            }
            maintainVisibleContentPosition={maintainVisibleContentPosition}
          >
            {ListHeaderComponent && <ListHeaderComponent />}
            
            {/* Virtual spacer for items before visible range */}
            {visibleRange.start > 0 && (
              <View style={{ height: virtualItems[visibleRange.start]?.top || 0 }} />
            )}
            
            {/* Visible items */}
            {renderVisibleItems()}
            
            {/* Virtual spacer for items after visible range */}
            {visibleRange.end < virtualItems.length - 1 && (
              <View 
                style={{ 
                  height: totalHeight - (virtualItems[visibleRange.end]?.bottom || 0) 
                }} 
              />
            )}
            
            {/* Footer */}
            {ListFooterComponent && <ListFooterComponent />}
            
            {/* Loading indicator */}
            {loading && (
              <View style={styles.loadingContainer}>
                <ActivityIndicator size="small" color="#007AFF" />
              </View>
            )}
          </ScrollView>
        </View>
      );
    }
  ),
  (prevProps, nextProps) => {
    // Custom comparison for performance optimization
    return (
      prevProps.data === nextProps.data &&
      prevProps.refreshing === nextProps.refreshing &&
      prevProps.loading === nextProps.loading &&
      prevProps.itemHeight === nextProps.itemHeight &&
      prevProps.renderItem === nextProps.renderItem
    );
  }
);

// Individual item wrapper component
interface VirtualizedItemWrapperProps {
  item: VirtualizedListItem;
  index: number;
  top: number;
  height: number;
  onLayout: (index: number, height: number) => void;
  renderItem: (item: VirtualizedListItem, index: number) => React.ReactNode;
}

const VirtualizedItemWrapper = memo<VirtualizedItemWrapperProps>(
  ({ item, index, top, height, onLayout, renderItem }) => {
    const handleLayout = useCallback((event: LayoutChangeEvent) => {
      const measuredHeight = event.nativeEvent.layout.height;
      if (measuredHeight !== height) {
        onLayout(index, measuredHeight);
      }
    }, [index, height, onLayout]);

    return (
      <View 
        style={[
          styles.itemContainer,
          { 
            position: 'absolute',
            top,
            left: 0,
            right: 0,
            minHeight: height,
          }
        ]}
        onLayout={handleLayout}
      >
        {renderItem(item, index)}
      </View>
    );
  }
);

// Specialized Transaction List with Virtualization
export interface VirtualizedTransactionListProps {
  transactions: any[];
  onTransactionPress: (id: string) => void;
  onRefresh?: () => Promise<void>;
  onLoadMore?: () => Promise<void>;
  refreshing?: boolean;
  loading?: boolean;
  estimatedItemHeight?: number;
}

export const VirtualizedTransactionList = memo<VirtualizedTransactionListProps>(
  ({ 
    transactions, 
    onTransactionPress, 
    onRefresh, 
    onLoadMore, 
    refreshing = false, 
    loading = false,
    estimatedItemHeight = 80 
  }) => {
    // Convert transactions to virtualized items
    const virtualizedData = useMemo(() => 
      transactions.map((transaction, index) => ({
        id: transaction.id,
        data: transaction,
        height: estimatedItemHeight,
      })),
      [transactions, estimatedItemHeight]
    );

    // Render transaction item
    const renderTransactionItem = useCallback(
      (virtualItem: VirtualizedListItem, index: number) => {
        const transaction = virtualItem.data;
        return (
          <TouchableOpacity
            style={styles.transactionItem}
            onPress={() => onTransactionPress(transaction.id)}
            testID={`transaction-${transaction.id}`}
          >
            <View style={styles.transactionContent}>
              <Text style={styles.transactionTitle} numberOfLines={1}>
                {transaction.description || 'Transaction'}
              </Text>
              <Text style={styles.transactionAmount}>
                ${Math.abs(transaction.amount).toFixed(2)}
              </Text>
            </View>
            <Text style={styles.transactionDate}>
              {new Date(transaction.date).toLocaleDateString()}
            </Text>
          </TouchableOpacity>
        );
      },
      [onTransactionPress]
    );

    // Empty component
    const EmptyComponent = useCallback(() => (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>No transactions found</Text>
      </View>
    ), []);

    return (
      <VirtualizedList
        data={virtualizedData}
        renderItem={renderTransactionItem}
        estimatedItemHeight={estimatedItemHeight}
        onRefresh={onRefresh}
        onEndReached={onLoadMore}
        refreshing={refreshing}
        loading={loading}
        ListEmptyComponent={EmptyComponent}
        keyExtractor={(item) => item.id}
        testID="virtualized-transaction-list"
      />
    );
  }
);

// Hook for virtualization performance monitoring
export const useVirtualizationMetrics = (listName: string) => {
  const metricsRef = useRef({
    renderCount: 0,
    totalRenderTime: 0,
    maxRenderTime: 0,
    itemsRendered: 0,
  });

  const recordRender = useCallback((startTime: number, itemCount: number) => {
    const renderTime = performance.now() - startTime;
    metricsRef.current.renderCount++;
    metricsRef.current.totalRenderTime += renderTime;
    metricsRef.current.maxRenderTime = Math.max(metricsRef.current.maxRenderTime, renderTime);
    metricsRef.current.itemsRendered = itemCount;

    // Log performance issues
    if (renderTime > 16.67) {
      Logger.warn(`${listName} slow render`, {
        renderTime: `${renderTime.toFixed(2)}ms`,
        itemCount,
        averageRenderTime: `${(metricsRef.current.totalRenderTime / metricsRef.current.renderCount).toFixed(2)}ms`,
      });
    }
  }, [listName]);

  const getMetrics = useCallback(() => ({
    ...metricsRef.current,
    averageRenderTime: metricsRef.current.totalRenderTime / metricsRef.current.renderCount,
  }), []);

  return { recordRender, getMetrics };
};

// Styles
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  scrollView: {
    flex: 1,
  },
  itemContainer: {
    backgroundColor: 'transparent',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyText: {
    fontSize: 16,
    color: '#999999',
    textAlign: 'center',
  },
  loadingContainer: {
    paddingVertical: 20,
    alignItems: 'center',
  },
  transactionItem: {
    flexDirection: 'row',
    padding: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E0E0E0',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
  },
  transactionContent: {
    flex: 1,
  },
  transactionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 4,
  },
  transactionAmount: {
    fontSize: 14,
    color: '#4CAF50',
    fontWeight: '500',
  },
  transactionDate: {
    fontSize: 12,
    color: '#757575',
  },
});

// Set display names
VirtualizedList.displayName = 'VirtualizedList';
VirtualizedItemWrapper.displayName = 'VirtualizedItemWrapper';
VirtualizedTransactionList.displayName = 'VirtualizedTransactionList';

export default {
  VirtualizedList,
  VirtualizedTransactionList,
  useVirtualizationMetrics,
};