import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { Box, Menu, MenuItem, Dialog } from '@mui/material';
import { useInfiniteQuery, useQuery } from 'react-query';
import { toast } from 'react-toastify';
import { debounce } from 'lodash';
import { useAppSelector } from '../../hooks/redux';
import { transactionService, TransactionResponse } from '../../services/transactionService';
import TransactionHistoryHeader, { TransactionFilters } from './TransactionHistoryHeader';
import VirtualizedTransactionList from './VirtualizedTransactionList';
import TransactionDetails from './TransactionDetails';
import { Transaction } from './TransactionItem';

interface TransactionHistoryOptimizedProps {
  userId?: string;
}

const TransactionHistoryOptimized: React.FC<TransactionHistoryOptimizedProps> = ({ 
  userId 
}) => {
  const { user } = useAppSelector((state) => state.auth);
  
  const [filters, setFilters] = useState<TransactionFilters>({
    search: '',
    status: '',
    type: '',
    dateFrom: null,
    dateTo: null,
    amountMin: '',
    amountMax: ''
  });
  
  const [menuAnchorEl, setMenuAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [detailsDialogOpen, setDetailsDialogOpen] = useState(false);

  // Debounced filters for API calls
  const debouncedFilters = useMemo(() => {
    const debouncedSetFilters = debounce((newFilters: TransactionFilters) => {
      return newFilters;
    }, 300);
    
    return debouncedSetFilters(filters);
  }, [filters]);

  // Query key that includes all filter parameters
  const queryKey = useMemo(() => [
    'transactions', 
    userId || user?.id,
    debouncedFilters.search,
    debouncedFilters.status,
    debouncedFilters.type,
    debouncedFilters.dateFrom?.toISOString(),
    debouncedFilters.dateTo?.toISOString(),
    debouncedFilters.amountMin,
    debouncedFilters.amountMax
  ], [
    userId, 
    user?.id, 
    debouncedFilters.search,
    debouncedFilters.status,
    debouncedFilters.type,
    debouncedFilters.dateFrom,
    debouncedFilters.dateTo,
    debouncedFilters.amountMin,
    debouncedFilters.amountMax
  ]);

  // Infinite query for transaction data
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isLoading,
    isFetchingNextPage,
    refetch
  } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam = 0 }) => transactionService.getTransactions({
      userId: userId || user?.id,
      page: pageParam,
      limit: 50,
      search: debouncedFilters.search || undefined,
      status: debouncedFilters.status || undefined,
      type: debouncedFilters.type || undefined,
      dateFrom: debouncedFilters.dateFrom || undefined,
      dateTo: debouncedFilters.dateTo || undefined,
      amountMin: debouncedFilters.amountMin ? Number(debouncedFilters.amountMin) : undefined,
      amountMax: debouncedFilters.amountMax ? Number(debouncedFilters.amountMax) : undefined,
    }),
    getNextPageParam: (lastPage: TransactionResponse) => 
      lastPage.hasMore ? lastPage.page + 1 : undefined,
    staleTime: 1 * 60 * 1000, // 1 minute
    cacheTime: 5 * 60 * 1000, // 5 minutes
    refetchOnWindowFocus: false,
    keepPreviousData: true,
    enabled: !!(userId || user?.id),
  });

  // Transaction statistics query
  const { data: transactionStats } = useQuery({
    queryKey: ['transactionStats', userId || user?.id],
    queryFn: () => transactionService.getTransactionStats(userId || user?.id!),
    enabled: !!(userId || user?.id),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  // Flatten pages into single array
  const allTransactions = useMemo(() => 
    data?.pages.flatMap(page => page.transactions) ?? [],
    [data]
  );

  const totalCount = useMemo(() => 
    data?.pages[0]?.totalCount ?? 0,
    [data]
  );

  // Event handlers
  const handleFiltersChange = useCallback((newFilters: TransactionFilters) => {
    setFilters(newFilters);
  }, []);

  const handleTransactionClick = useCallback((transaction: Transaction) => {
    setSelectedTransaction(transaction);
    setDetailsDialogOpen(true);
  }, []);

  const handleTransactionMenu = useCallback((transaction: Transaction, anchorEl: HTMLElement) => {
    setSelectedTransaction(transaction);
    setMenuAnchorEl(anchorEl);
  }, []);

  const handleMenuClose = useCallback(() => {
    setMenuAnchorEl(null);
    setSelectedTransaction(null);
  }, []);

  const handleExport = useCallback(async () => {
    try {
      const exportData = await transactionService.exportTransactions({
        userId: userId || user?.id!,
        format: 'csv',
        filters: debouncedFilters
      });
      
      // Create and trigger download
      const blob = new Blob([exportData], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `transactions_${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      
      toast.success('Transactions exported successfully');
    } catch (error) {
      toast.error('Failed to export transactions');
    }
  }, [userId, user?.id, debouncedFilters]);

  const loadMore = useCallback(async () => {
    if (hasNextPage && !isFetchingNextPage) {
      await fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const handleRefresh = useCallback(() => {
    refetch();
  }, [refetch]);

  // Menu actions
  const menuActions = [
    {
      label: 'View Details',
      action: () => {
        setDetailsDialogOpen(true);
        handleMenuClose();
      }
    },
    {
      label: 'Download Receipt',
      action: () => {
        // Implement receipt download
        toast.info('Receipt download feature coming soon');
        handleMenuClose();
      }
    },
    {
      label: 'Copy Transaction ID',
      action: () => {
        if (selectedTransaction) {
          navigator.clipboard.writeText(selectedTransaction.id);
          toast.success('Transaction ID copied to clipboard');
        }
        handleMenuClose();
      }
    },
    {
      label: 'Report Issue',
      action: () => {
        // Implement issue reporting
        toast.info('Issue reporting feature coming soon');
        handleMenuClose();
      }
    }
  ];

  return (
    <Box>
      <TransactionHistoryHeader
        filters={filters}
        onFiltersChange={handleFiltersChange}
        onExport={handleExport}
        totalCount={totalCount}
        isLoading={isLoading}
      />

      <VirtualizedTransactionList
        transactions={allTransactions}
        hasNextPage={hasNextPage || false}
        isLoading={isFetchingNextPage}
        loadMore={loadMore}
        onTransactionClick={handleTransactionClick}
        onTransactionMenu={handleTransactionMenu}
        height={700}
        itemHeight={80}
      />

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchorEl}
        open={Boolean(menuAnchorEl)}
        onClose={handleMenuClose}
      >
        {menuActions.map((action, index) => (
          <MenuItem key={index} onClick={action.action}>
            {action.label}
          </MenuItem>
        ))}
      </Menu>

      {/* Transaction Details Dialog */}
      {selectedTransaction && (
        <TransactionDetails
          transaction={selectedTransaction}
          open={detailsDialogOpen}
          onClose={() => {
            setDetailsDialogOpen(false);
            setSelectedTransaction(null);
          }}
        />
      )}
    </Box>
  );
};

export default TransactionHistoryOptimized;