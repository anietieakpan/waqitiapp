import React, { useState, useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  Avatar,
  Chip,
  IconButton,
  Tooltip,
  Collapse,
  Button,
  useTheme,
  alpha,
  Divider,
  Badge,
} from '@mui/material';
import {
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent,
} from '@mui/lab';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import BusinessIcon from '@mui/icons-material/Business';
import PersonIcon from '@mui/icons-material/Person';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ScheduleIcon from '@mui/icons-material/Schedule';
import FlagIcon from '@mui/icons-material/Flag';
import OfferIcon from '@mui/icons-material/LocalOffer';
import ReceiptIcon from '@mui/icons-material/Receipt';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import FoodIcon from '@mui/icons-material/Restaurant';
import TransportIcon from '@mui/icons-material/DirectionsCar';
import HomeIcon from '@mui/icons-material/Home';
import HealthIcon from '@mui/icons-material/LocalHospital';
import EducationIcon from '@mui/icons-material/School';
import TravelIcon from '@mui/icons-material/Flight';
import CategoryIcon from '@mui/icons-material/Category';;
import { format, isToday, isYesterday, isSameDay, startOfDay } from 'date-fns';
import { Transaction, TransactionType, TransactionStatus } from '../../types/wallet';
import { formatCurrency, formatTime, formatRelativeDate } from '../../utils/formatters';

interface TransactionTimelineProps {
  transactions: Transaction[];
  onTransactionClick?: (transaction: Transaction) => void;
  showBalances?: boolean;
  compact?: boolean;
}

interface GroupedTransactions {
  date: Date;
  dateLabel: string;
  transactions: Transaction[];
  totalIn: number;
  totalOut: number;
  netAmount: number;
}

const TransactionTimeline: React.FC<TransactionTimelineProps> = ({
  transactions,
  onTransactionClick,
  showBalances = true,
  compact = false,
}) => {
  const theme = useTheme();
  const [expandedDates, setExpandedDates] = useState<string[]>([]);
  const [expandedTransactions, setExpandedTransactions] = useState<string[]>([]);

  // Group transactions by date
  const groupedTransactions = useMemo(() => {
    const groups: Record<string, GroupedTransactions> = {};

    transactions.forEach(transaction => {
      const date = startOfDay(new Date(transaction.createdAt));
      const dateKey = date.toISOString();

      if (!groups[dateKey]) {
        groups[dateKey] = {
          date,
          dateLabel: getDateLabel(date),
          transactions: [],
          totalIn: 0,
          totalOut: 0,
          netAmount: 0,
        };
      }

      groups[dateKey].transactions.push(transaction);

      if (transaction.type === TransactionType.CREDIT || transaction.type === TransactionType.DEPOSIT) {
        groups[dateKey].totalIn += transaction.amount;
      } else if (transaction.type === TransactionType.DEBIT || transaction.type === TransactionType.WITHDRAWAL) {
        groups[dateKey].totalOut += transaction.amount;
      }
    });

    // Calculate net amounts and sort
    return Object.values(groups)
      .map(group => ({
        ...group,
        netAmount: group.totalIn - group.totalOut,
        transactions: group.transactions.sort((a, b) => 
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        ),
      }))
      .sort((a, b) => b.date.getTime() - a.date.getTime());
  }, [transactions]);

  const getDateLabel = (date: Date): string => {
    if (isToday(date)) return 'Today';
    if (isYesterday(date)) return 'Yesterday';
    return format(date, 'EEEE, MMMM d, yyyy');
  };

  const getTransactionIcon = (transaction: Transaction) => {
    // Category-based icons
    if (transaction.category) {
      switch (transaction.category.toLowerCase()) {
        case 'food & dining':
          return <FoodIcon />;
        case 'shopping':
          return <ShoppingIcon />;
        case 'transportation':
          return <TransportIcon />;
        case 'bills & utilities':
          return <HomeIcon />;
        case 'healthcare':
          return <HealthIcon />;
        case 'education':
          return <EducationIcon />;
        case 'travel':
          return <TravelIcon />;
      }
    }

    // Type-based icons
    switch (transaction.type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
        return <TrendingUpIcon />;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
        return <TrendingDownIcon />;
      case TransactionType.TRANSFER:
        return <SwapIcon />;
      default:
        return <MoneyIcon />;
    }
  };

  const getTransactionColor = (transaction: Transaction) => {
    if (transaction.status === TransactionStatus.FAILED) return theme.palette.error.main;
    if (transaction.status === TransactionStatus.PENDING) return theme.palette.warning.main;
    
    switch (transaction.type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
        return theme.palette.success.main;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
        return theme.palette.error.main;
      default:
        return theme.palette.primary.main;
    }
  };

  const getStatusIcon = (status: TransactionStatus) => {
    switch (status) {
      case TransactionStatus.COMPLETED:
        return <CheckCircleIcon sx={{ fontSize: 16 }} />;
      case TransactionStatus.PENDING:
        return <ScheduleIcon sx={{ fontSize: 16 }} />;
      case TransactionStatus.FAILED:
        return <ErrorIcon sx={{ fontSize: 16 }} />;
      default:
        return null;
    }
  };

  const toggleDateExpansion = (dateKey: string) => {
    setExpandedDates(prev =>
      prev.includes(dateKey)
        ? prev.filter(d => d !== dateKey)
        : [...prev, dateKey]
    );
  };

  const toggleTransactionExpansion = (transactionId: string) => {
    setExpandedTransactions(prev =>
      prev.includes(transactionId)
        ? prev.filter(id => id !== transactionId)
        : [...prev, transactionId]
    );
  };

  const renderDateHeader = (group: GroupedTransactions) => (
    <Paper
      sx={{
        p: 2,
        mb: 2,
        bgcolor: alpha(theme.palette.primary.main, 0.05),
        cursor: 'pointer',
        '&:hover': {
          bgcolor: alpha(theme.palette.primary.main, 0.08),
        },
      }}
      onClick={() => toggleDateExpansion(group.date.toISOString())}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Avatar
            sx={{
              bgcolor: alpha(theme.palette.primary.main, 0.1),
              color: theme.palette.primary.main,
            }}
          >
            <CalendarIcon />
          </Avatar>
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
              {group.dateLabel}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {group.transactions.length} transaction{group.transactions.length !== 1 ? 's' : ''}
            </Typography>
          </Box>
        </Box>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box sx={{ textAlign: 'right' }}>
            {group.totalIn > 0 && (
              <Typography variant="body2" sx={{ color: theme.palette.success.main }}>
                +{formatCurrency(group.totalIn)}
              </Typography>
            )}
            {group.totalOut > 0 && (
              <Typography variant="body2" sx={{ color: theme.palette.error.main }}>
                -{formatCurrency(group.totalOut)}
              </Typography>
            )}
            <Typography
              variant="caption"
              sx={{
                color: group.netAmount >= 0 ? theme.palette.success.main : theme.palette.error.main,
                fontWeight: 600,
              }}
            >
              Net: {formatCurrency(Math.abs(group.netAmount))}
            </Typography>
          </Box>
          <IconButton size="small">
            {expandedDates.includes(group.date.toISOString()) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </IconButton>
        </Box>
      </Box>
    </Paper>
  );

  const renderTransaction = (transaction: Transaction, isLast: boolean) => {
    const isExpanded = expandedTransactions.includes(transaction.id);
    const color = getTransactionColor(transaction);

    return (
      <TimelineItem key={transaction.id}>
        <TimelineOppositeContent
          sx={{ 
            py: 1.5, 
            px: 2,
            flex: compact ? 0.3 : 0.4,
          }}
        >
          <Typography variant="caption" color="text.secondary">
            {formatTime(transaction.createdAt)}
          </Typography>
          {showBalances && transaction.balanceAfter && (
            <Typography variant="body2" sx={{ fontWeight: 500 }}>
              Balance: {formatCurrency(transaction.balanceAfter)}
            </Typography>
          )}
        </TimelineOppositeContent>
        
        <TimelineSeparator>
          <TimelineConnector sx={{ bgcolor: alpha(color, 0.2) }} />
          <TimelineDot
            sx={{
              bgcolor: alpha(color, 0.1),
              borderColor: color,
              borderWidth: 2,
              borderStyle: 'solid',
              p: 1,
            }}
          >
            <Box sx={{ color }}>
              {getTransactionIcon(transaction)}
            </Box>
          </TimelineDot>
          {!isLast && <TimelineConnector sx={{ bgcolor: alpha(color, 0.2) }} />}
        </TimelineSeparator>
        
        <TimelineContent sx={{ py: 1.5, px: 2 }}>
          <Paper
            sx={{
              p: 2,
              cursor: 'pointer',
              transition: 'all 0.2s',
              '&:hover': {
                bgcolor: alpha(theme.palette.primary.main, 0.05),
                transform: 'translateX(4px)',
              },
            }}
            onClick={() => onTransactionClick?.(transaction)}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <Box sx={{ flex: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    {transaction.description || transaction.merchantName || 'Transaction'}
                  </Typography>
                  {getStatusIcon(transaction.status)}
                  {transaction.tags?.map(tag => (
                    <Chip
                      key={tag}
                      label={tag}
                      size="small"
                      variant="outlined"
                      sx={{ height: 20 }}
                    />
                  ))}
                </Box>
                
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  {transaction.merchantName && (
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <BusinessIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      <Typography variant="caption" color="text.secondary">
                        {transaction.merchantName}
                      </Typography>
                    </Box>
                  )}
                  
                  {transaction.category && (
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <CategoryIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      <Typography variant="caption" color="text.secondary">
                        {transaction.category}
                      </Typography>
                    </Box>
                  )}
                  
                  {transaction.paymentMethodType && (
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      {transaction.paymentMethodType.includes('CARD') ? (
                        <CardIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      ) : (
                        <BankIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      )}
                      <Typography variant="caption" color="text.secondary">
                        {transaction.paymentMethodLast4 && `•••• ${transaction.paymentMethodLast4}`}
                      </Typography>
                    </Box>
                  )}
                </Box>
                
                {!compact && (
                  <IconButton
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      toggleTransactionExpansion(transaction.id);
                    }}
                    sx={{ mt: 1 }}
                  >
                    {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                  </IconButton>
                )}
              </Box>
              
              <Box sx={{ textAlign: 'right' }}>
                <Typography
                  variant="h6"
                  sx={{
                    fontWeight: 600,
                    color,
                  }}
                >
                  {transaction.type === TransactionType.CREDIT || transaction.type === TransactionType.DEPOSIT
                    ? '+'
                    : '-'}
                  {formatCurrency(transaction.amount)}
                </Typography>
                {transaction.fee && transaction.fee > 0 && (
                  <Typography variant="caption" color="text.secondary">
                    Fee: {formatCurrency(transaction.fee)}
                  </Typography>
                )}
              </Box>
            </Box>
            
            <Collapse in={isExpanded}>
              <Divider sx={{ my: 2 }} />
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {transaction.note && (
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      Note
                    </Typography>
                    <Typography variant="body2">{transaction.note}</Typography>
                  </Box>
                )}
                
                {transaction.reference && (
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      Reference
                    </Typography>
                    <Typography variant="body2">{transaction.reference}</Typography>
                  </Box>
                )}
                
                <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                  <Button size="small" startIcon={<ReceiptIcon />}>
                    Receipt
                  </Button>
                  {transaction.status === TransactionStatus.COMPLETED && (
                    <Button size="small" startIcon={<FlagIcon />}>
                      Report
                    </Button>
                  )}
                </Box>
              </Box>
            </Collapse>
          </Paper>
        </TimelineContent>
      </TimelineItem>
    );
  };

  const renderCompactView = () => (
    <Box>
      {groupedTransactions.map(group => (
        <Box key={group.date.toISOString()} sx={{ mb: 3 }}>
          {renderDateHeader(group)}
          <Collapse in={expandedDates.includes(group.date.toISOString())}>
            <Box sx={{ ml: 2 }}>
              {group.transactions.map((transaction, index) => (
                <Paper
                  key={transaction.id}
                  sx={{
                    p: 2,
                    mb: 1,
                    cursor: 'pointer',
                    '&:hover': {
                      bgcolor: alpha(theme.palette.primary.main, 0.05),
                    },
                  }}
                  onClick={() => onTransactionClick?.(transaction)}
                >
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <Avatar
                        sx={{
                          width: 36,
                          height: 36,
                          bgcolor: alpha(getTransactionColor(transaction), 0.1),
                          color: getTransactionColor(transaction),
                        }}
                      >
                        {getTransactionIcon(transaction)}
                      </Avatar>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {transaction.description || transaction.merchantName || 'Transaction'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatTime(transaction.createdAt)}
                          {transaction.category && ` • ${transaction.category}`}
                        </Typography>
                      </Box>
                    </Box>
                    
                    <Box sx={{ textAlign: 'right' }}>
                      <Typography
                        variant="body1"
                        sx={{
                          fontWeight: 600,
                          color: getTransactionColor(transaction),
                        }}
                      >
                        {transaction.type === TransactionType.CREDIT || transaction.type === TransactionType.DEPOSIT
                          ? '+'
                          : '-'}
                        {formatCurrency(transaction.amount)}
                      </Typography>
                      {showBalances && transaction.balanceAfter && (
                        <Typography variant="caption" color="text.secondary">
                          Balance: {formatCurrency(transaction.balanceAfter)}
                        </Typography>
                      )}
                    </Box>
                  </Box>
                </Paper>
              ))}
            </Box>
          </Collapse>
        </Box>
      ))}
    </Box>
  );

  if (compact) {
    return renderCompactView();
  }

  return (
    <Box>
      {groupedTransactions.map(group => (
        <Box key={group.date.toISOString()} sx={{ mb: 4 }}>
          {renderDateHeader(group)}
          <Collapse in={expandedDates.includes(group.date.toISOString())}>
            <Timeline position="right">
              {group.transactions.map((transaction, index) =>
                renderTransaction(transaction, index === group.transactions.length - 1)
              )}
            </Timeline>
          </Collapse>
        </Box>
      ))}
    </Box>
  );
};

export default TransactionTimeline;