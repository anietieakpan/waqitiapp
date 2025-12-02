import React, { useCallback } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Chip,
  Avatar,
  Box,
  IconButton,
} from '@mui/material';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ReceiptIcon from '@mui/icons-material/Receipt';
import MoreVertIcon from '@mui/icons-material/MoreVert';;
import { format } from 'date-fns';
import VirtualizedList from './VirtualizedList';
import { Transaction } from '../../types/payment';

interface VirtualizedTransactionListProps {
  transactions: Transaction[];
  onTransactionClick?: (transaction: Transaction) => void;
  onLoadMore?: () => void;
  hasNextPage?: boolean;
  isLoadingMore?: boolean;
  loading?: boolean;
  error?: string;
  searchable?: boolean;
  height?: number;
}

const VirtualizedTransactionList: React.FC<VirtualizedTransactionListProps> = ({
  transactions,
  onTransactionClick,
  onLoadMore,
  hasNextPage = false,
  isLoadingMore = false,
  loading = false,
  error,
  searchable = true,
  height = 600,
}) => {
  const getTransactionIcon = (type: string) => {
    switch (type) {
      case 'send':
        return <ArrowUpward color="error" />;
      case 'receive':
        return <ArrowDownward color="success" />;
      default:
        return <Receipt color="primary" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'completed':
        return 'success';
      case 'pending':
        return 'warning';
      case 'failed':
        return 'error';
      case 'cancelled':
        return 'default';
      default:
        return 'default';
    }
  };

  const getAmountColor = (type: string) => {
    switch (type) {
      case 'send':
        return 'error.main';
      case 'receive':
        return 'success.main';
      default:
        return 'text.primary';
    }
  };

  const formatAmount = (amount: number, currency: string = 'USD') => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
    }).format(amount);
  };

  const renderTransaction = useCallback(
    (transaction: Transaction, index: number, style: React.CSSProperties) => {
      return (
        <div style={style}>
          <Card
            variant="outlined"
            sx={{
              m: 1,
              cursor: onTransactionClick ? 'pointer' : 'default',
              transition: 'all 0.2s ease',
              '&:hover': onTransactionClick ? {
                elevation: 2,
                transform: 'translateY(-2px)',
              } : {},
            }}
            onClick={() => onTransactionClick?.(transaction)}
          >
            <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Box display="flex" alignItems="center" gap={2}>
                {/* Transaction Icon */}
                <Avatar sx={{ bgcolor: 'transparent', border: 1, borderColor: 'divider' }}>
                  {getTransactionIcon(transaction.type)}
                </Avatar>

                {/* Transaction Details */}
                <Box flexGrow={1}>
                  <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Typography variant="subtitle1" fontWeight="medium">
                      {transaction.type === 'send' 
                        ? `To: ${transaction.recipient || 'Unknown'}`
                        : `From: ${transaction.sender || 'Unknown'}`
                      }
                    </Typography>
                    <Typography
                      variant="subtitle1"
                      fontWeight="bold"
                      color={getAmountColor(transaction.type)}
                    >
                      {transaction.type === 'send' ? '-' : '+'}
                      {formatAmount(transaction.amount, transaction.currency)}
                    </Typography>
                  </Box>

                  <Box display="flex" alignItems="center" justifyContent="space-between" mt={0.5}>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography variant="body2" color="text.secondary">
                        {format(new Date(transaction.createdAt), 'MMM dd, yyyy • hh:mm a')}
                      </Typography>
                      <Chip
                        label={transaction.status}
                        size="small"
                        color={getStatusColor(transaction.status) as any}
                        variant="outlined"
                        sx={{ height: 20, fontSize: '0.7rem' }}
                      />
                    </Box>
                    
                    {onTransactionClick && (
                      <IconButton size="small" onClick={(e) => e.stopPropagation()}>
                        <MoreVert />
                      </IconButton>
                    )}
                  </Box>

                  {transaction.description && (
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{
                        mt: 1,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {transaction.description}
                    </Typography>
                  )}

                  {transaction.fee && transaction.fee > 0 && (
                    <Typography variant="caption" color="text.disabled" sx={{ mt: 0.5, display: 'block' }}>
                      Fee: {formatAmount(transaction.fee, transaction.currency)}
                    </Typography>
                  )}
                </Box>
              </Box>
            </CardContent>
          </Card>
        </div>
      );
    },
    [onTransactionClick]
  );

  const searchKeys: (keyof Transaction)[] = [
    'recipient',
    'sender', 
    'description',
    'status',
    'type'
  ];

  const customFilterFn = useCallback((transaction: Transaction, searchTerm: string) => {
    const term = searchTerm.toLowerCase();
    
    // Search in basic fields
    const basicMatch = searchKeys.some(key => {
      const value = transaction[key];
      return value && String(value).toLowerCase().includes(term);
    });

    // Search in amount (formatted)
    const amountMatch = formatAmount(transaction.amount, transaction.currency)
      .toLowerCase()
      .includes(term);

    // Search in formatted date
    const dateMatch = format(new Date(transaction.createdAt), 'MMM dd, yyyy')
      .toLowerCase()
      .includes(term);

    return basicMatch || amountMatch || dateMatch;
  }, []);

  return (
    <VirtualizedList
      items={transactions}
      renderItem={renderTransaction}
      itemHeight={120}
      height={height}
      searchable={searchable}
      searchKeys={searchKeys}
      searchPlaceholder="Search transactions by recipient, amount, status..."
      filterFn={customFilterFn}
      onItemClick={onTransactionClick}
      loading={loading}
      error={error}
      emptyMessage="No transactions found"
      onLoadMore={onLoadMore}
      hasNextPage={hasNextPage}
      isLoadingMore={isLoadingMore}
      overscan={3}
      headerComponent={
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
          <Typography variant="h6" component="h2">
            Transaction History
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {transactions.length} transaction{transactions.length !== 1 ? 's' : ''}
          </Typography>
        </Box>
      }
    />
  );
};

export default VirtualizedTransactionList;