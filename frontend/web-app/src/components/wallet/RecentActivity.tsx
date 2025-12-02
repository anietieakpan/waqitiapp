import React, { useState } from 'react';
import {
  Paper,
  Typography,
  Box,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Chip,
  Button,
  IconButton,
  Menu,
  MenuItem,
  Divider,
  Collapse,
  Skeleton,
  Badge,
  useTheme,
  alpha,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import SendIcon from '@mui/icons-material/Send';
import ReceiveIcon from '@mui/icons-material/CallReceived';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import ReceiptIcon from '@mui/icons-material/Receipt';
import UndoIcon from '@mui/icons-material/Undo';
import MonetizationIcon from '@mui/icons-material/MonetizationOn';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';
import FilterIcon from '@mui/icons-material/FilterList';;
import { format, isToday, isYesterday, differenceInDays } from 'date-fns';
import { Transaction, TransactionStatus, TransactionType } from '../../types/wallet';
import { formatCurrency } from '../../utils/formatters';

interface RecentActivityProps {
  transactions: Transaction[];
  showValues: boolean;
  onViewAll?: () => void;
  onTransactionClick?: (transaction: Transaction) => void;
  isLoading?: boolean;
  limit?: number;
}

const RecentActivity: React.FC<RecentActivityProps> = ({
  transactions = [],
  showValues,
  onViewAll,
  onTransactionClick,
  isLoading = false,
  limit = 10,
}) => {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(true);
  const [filterAnchor, setFilterAnchor] = useState<null | HTMLElement>(null);
  const [selectedFilter, setSelectedFilter] = useState<'ALL' | 'CREDIT' | 'DEBIT'>('ALL');

  const getTransactionIcon = (type: TransactionType) => {
    switch (type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
        return <CallReceived />;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
        return <Send />;
      case TransactionType.TRANSFER:
        return <SwapIcon />;
      case TransactionType.FEE:
        return <MonetizationIcon />;
      case TransactionType.REFUND:
        return <UndoIcon />;
      default:
        return <SwapIcon />;
    }
  };

  const getTransactionColor = (type: TransactionType) => {
    switch (type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
      case TransactionType.REFUND:
        return theme.palette.success.main;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
      case TransactionType.FEE:
        return theme.palette.error.main;
      case TransactionType.TRANSFER:
        return theme.palette.info.main;
      default:
        return theme.palette.text.primary;
    }
  };

  const getStatusColor = (status: TransactionStatus) => {
    switch (status) {
      case TransactionStatus.COMPLETED:
        return 'success';
      case TransactionStatus.PENDING:
      case TransactionStatus.PROCESSING:
        return 'warning';
      case TransactionStatus.FAILED:
      case TransactionStatus.CANCELLED:
        return 'error';
      default:
        return 'default';
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    if (isToday(date)) return 'Today';
    if (isYesterday(date)) return 'Yesterday';
    if (differenceInDays(new Date(), date) < 7) {
      return format(date, 'EEEE');
    }
    return format(date, 'MMM d');
  };

  const getTransactionDescription = (transaction: Transaction) => {
    if (transaction.description) return transaction.description;
    
    switch (transaction.type) {
      case TransactionType.CREDIT:
        return transaction.fromUser 
          ? `Received from ${transaction.fromUser.name}`
          : 'Money received';
      case TransactionType.DEBIT:
        return transaction.toUser 
          ? `Sent to ${transaction.toUser.name}`
          : 'Money sent';
      case TransactionType.DEPOSIT:
        return 'Deposit from bank account';
      case TransactionType.WITHDRAWAL:
        return 'Withdrawal to bank account';
      case TransactionType.TRANSFER:
        return 'Transfer between wallets';
      case TransactionType.FEE:
        return 'Transaction fee';
      case TransactionType.REFUND:
        return 'Refund processed';
      default:
        return 'Transaction';
    }
  };

  const filteredTransactions = transactions.filter(transaction => {
    if (selectedFilter === 'ALL') return true;
    return transaction.type === selectedFilter;
  }).slice(0, limit);

  const groupedTransactions = filteredTransactions.reduce((groups, transaction) => {
    const date = format(new Date(transaction.createdAt), 'yyyy-MM-dd');
    if (!groups[date]) groups[date] = [];
    groups[date].push(transaction);
    return groups;
  }, {} as Record<string, Transaction[]>);

  const handleFilterOpen = (event: React.MouseEvent<HTMLElement>) => {
    setFilterAnchor(event.currentTarget);
  };

  const handleFilterClose = () => {
    setFilterAnchor(null);
  };

  const handleFilterSelect = (filter: 'ALL' | 'CREDIT' | 'DEBIT') => {
    setSelectedFilter(filter);
    handleFilterClose();
  };

  if (isLoading) {
    return (
      <Paper sx={{ p: 0 }}>
        <Box sx={{ p: 2 }}>
          <Skeleton variant="text" width={200} height={32} />
        </Box>
        <List>
          {[...Array(5)].map((_, index) => (
            <ListItem key={index}>
              <ListItemIcon>
                <Skeleton variant="circular" width={40} height={40} />
              </ListItemIcon>
              <ListItemText
                primary={<Skeleton variant="text" width="60%" />}
                secondary={<Skeleton variant="text" width="40%" />}
              />
              <ListItemSecondaryAction>
                <Skeleton variant="text" width={80} />
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 0 }}>
      <Box
        sx={{
          p: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
        }}
        onClick={() => setExpanded(!expanded)}
      >
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          Recent Activity
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Badge badgeContent={transactions.length} color="primary" max={99}>
            <IconButton size="small" onClick={handleFilterOpen}>
              <FilterIcon />
            </IconButton>
          </Badge>
          <IconButton size="small">
            {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </IconButton>
        </Box>
      </Box>

      <Collapse in={expanded}>
        <Divider />
        
        {Object.keys(groupedTransactions).length === 0 ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <ReceiptIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
            <Typography variant="body1" color="text.secondary">
              No transactions found
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Your transaction history will appear here
            </Typography>
          </Box>
        ) : (
          <List sx={{ py: 0 }}>
            {Object.entries(groupedTransactions).map(([date, dayTransactions], groupIndex) => (
              <React.Fragment key={date}>
                {/* Date Header */}
                <ListItem sx={{ bgcolor: alpha(theme.palette.primary.main, 0.04) }}>
                  <Typography variant="subtitle2" color="primary" sx={{ fontWeight: 600 }}>
                    {formatDate(dayTransactions[0].createdAt)}
                  </Typography>
                </ListItem>
                
                {/* Transactions for this date */}
                {dayTransactions.map((transaction, index) => (
                  <React.Fragment key={transaction.id}>
                    <ListItem
                      button={!!onTransactionClick}
                      onClick={() => onTransactionClick?.(transaction)}
                      sx={{ py: 2 }}
                    >
                      <ListItemIcon>
                        <Avatar
                          sx={{
                            bgcolor: alpha(getTransactionColor(transaction.type), 0.1),
                            color: getTransactionColor(transaction.type),
                          }}
                        >
                          {getTransactionIcon(transaction.type)}
                        </Avatar>
                      </ListItemIcon>
                      
                      <ListItemText
                        primary={
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 500 }}>
                              {getTransactionDescription(transaction)}
                            </Typography>
                            <Chip
                              label={transaction.status}
                              size="small"
                              color={getStatusColor(transaction.status) as any}
                              variant="outlined"
                            />
                          </Box>
                        }
                        secondary={
                          <Box sx={{ mt: 0.5 }}>
                            <Typography variant="caption" color="text.secondary">
                              {format(new Date(transaction.createdAt), 'h:mm a')}
                              {transaction.reference && ` • Ref: ${transaction.reference}`}
                            </Typography>
                            {transaction.fees && transaction.fees > 0 && (
                              <Typography variant="caption" color="warning.main" sx={{ ml: 1 }}>
                                Fee: {formatCurrency(transaction.fees, transaction.currency)}
                              </Typography>
                            )}
                          </Box>
                        }
                      />
                      
                      <ListItemSecondaryAction>
                        <Box sx={{ textAlign: 'right' }}>
                          <Typography
                            variant="subtitle1"
                            sx={{
                              fontWeight: 600,
                              color: getTransactionColor(transaction.type),
                            }}
                          >
                            {transaction.type === TransactionType.DEBIT || 
                             transaction.type === TransactionType.WITHDRAWAL ||
                             transaction.type === TransactionType.FEE ? '-' : '+'}
                            {showValues 
                              ? formatCurrency(transaction.amount, transaction.currency)
                              : '••••••'
                            }
                          </Typography>
                          
                          {transaction.exchangeRate && transaction.exchangeRate !== 1 && (
                            <Typography variant="caption" color="text.secondary">
                              Rate: {transaction.exchangeRate.toFixed(4)}
                            </Typography>
                          )}
                        </Box>
                      </ListItemSecondaryAction>
                    </ListItem>
                    
                    {index < dayTransactions.length - 1 && <Divider variant="inset" component="li" />}
                  </React.Fragment>
                ))}
                
                {groupIndex < Object.keys(groupedTransactions).length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List>
        )}
        
        {transactions.length > limit && (
          <>
            <Divider />
            <Box sx={{ p: 2, textAlign: 'center' }}>
              <Button
                variant="outlined"
                onClick={onViewAll}
                startIcon={<ReceiptIcon />}
              >
                View All Transactions ({transactions.length})
              </Button>
            </Box>
          </>
        )}
      </Collapse>

      {/* Filter Menu */}
      <Menu
        anchorEl={filterAnchor}
        open={Boolean(filterAnchor)}
        onClose={handleFilterClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem onClick={() => handleFilterSelect('ALL')}>
          <ListItemIcon>
            <SwapIcon />
          </ListItemIcon>
          <ListItemText primary="All Transactions" />
          {selectedFilter === 'ALL' && (
            <Typography variant="body2" color="primary">✓</Typography>
          )}
        </MenuItem>
        <MenuItem onClick={() => handleFilterSelect('CREDIT')}>
          <ListItemIcon>
            <TrendingUpIcon sx={{ color: theme.palette.success.main }} />
          </ListItemIcon>
          <ListItemText primary="Money In" />
          {selectedFilter === 'CREDIT' && (
            <Typography variant="body2" color="primary">✓</Typography>
          )}
        </MenuItem>
        <MenuItem onClick={() => handleFilterSelect('DEBIT')}>
          <ListItemIcon>
            <TrendingDownIcon sx={{ color: theme.palette.error.main }} />
          </ListItemIcon>
          <ListItemText primary="Money Out" />
          {selectedFilter === 'DEBIT' && (
            <Typography variant="body2" color="primary">✓</Typography>
          )}
        </MenuItem>
      </Menu>
    </Paper>
  );
};

export default RecentActivity;