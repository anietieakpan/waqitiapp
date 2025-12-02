import React, { memo, useCallback, useMemo } from 'react';
import { FixedSizeList as List } from 'react-window';
import InfiniteLoader from 'react-window-infinite-loader';
import {
  Box,
  List as MuiList,
  CircularProgress,
  Typography,
  Paper,
} from '@mui/material';
import { Transaction } from './TransactionItem';
import TransactionItem from './TransactionItem';

interface VirtualizedTransactionListProps {
  transactions: Transaction[];
  hasNextPage: boolean;
  isLoading: boolean;
  loadMore: () => Promise<void>;
  onTransactionClick?: (transaction: Transaction) => void;
  onTransactionMenu?: (transaction: Transaction, anchorEl: HTMLElement) => void;
  height?: number;
  itemHeight?: number;
}

const VirtualizedTransactionList = memo<VirtualizedTransactionListProps>(({
  transactions,
  hasNextPage,
  isLoading,
  loadMore,
  onTransactionClick,
  onTransactionMenu,
  height = 600,
  itemHeight = 80
}) => {
  const itemCount = hasNextPage ? transactions.length + 1 : transactions.length;

  const isItemLoaded = useCallback((index: number) => {
    return !!transactions[index];
  }, [transactions]);

  const loadMoreItems = useCallback(async () => {
    if (!isLoading && hasNextPage) {
      await loadMore();
    }
  }, [isLoading, hasNextPage, loadMore]);

  const itemData = useMemo(() => ({
    transactions,
    onTransactionClick,
    onTransactionMenu,
    isLoading
  }), [transactions, onTransactionClick, onTransactionMenu, isLoading]);

  if (transactions.length === 0 && !isLoading) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No transactions found
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Try adjusting your filters or check back later
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ height, overflow: 'hidden' }}>
      <InfiniteLoader
        isItemLoaded={isItemLoaded}
        itemCount={itemCount}
        loadMoreItems={loadMoreItems}
        threshold={5} // Load more when 5 items from the end
      >
        {({ onItemsRendered, ref }) => (
          <List
            ref={ref}
            height={height}
            itemCount={itemCount}
            itemSize={itemHeight}
            onItemsRendered={onItemsRendered}
            itemData={itemData}
            overscanCount={10} // Pre-render 10 items above and below
          >
            {VirtualizedTransactionItem}
          </List>
        )}
      </InfiniteLoader>
    </Paper>
  );
});

// Virtualized item renderer
const VirtualizedTransactionItem = memo<any>(({ index, style, data }) => {
  const { transactions, onTransactionClick, onTransactionMenu, isLoading } = data;
  const transaction = transactions[index];

  // Loading item
  if (!transaction) {
    return (
      <div style={style}>
        <Box 
          sx={{ 
            display: 'flex', 
            justifyContent: 'center', 
            alignItems: 'center',
            height: '100%',
            borderBottom: 1,
            borderColor: 'divider'
          }}
        >
          <CircularProgress size={24} />
        </Box>
      </div>
    );
  }

  return (
    <div style={style}>
      <MuiList disablePadding>
        <TransactionItem
          transaction={transaction}
          onClick={onTransactionClick}
          onMenuClick={onTransactionMenu}
        />
      </MuiList>
    </div>
  );
});

VirtualizedTransactionItem.displayName = 'VirtualizedTransactionItem';
VirtualizedTransactionList.displayName = 'VirtualizedTransactionList';

export default VirtualizedTransactionList;