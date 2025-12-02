import React, { memo, useMemo } from 'react';
import {
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Typography,
  Chip,
  Box,
  IconButton,
  Tooltip,
} from '@mui/material';
import ArrowUpIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownIcon from '@mui/icons-material/ArrowDownward';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import ReceiptIcon from '@mui/icons-material/Receipt';
import MoreIcon from '@mui/icons-material/MoreVert';;
import { format, parseISO } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';

export interface Transaction {
  id: string;
  type: string;
  status: string;
  amount: number;
  currency: string;
  description: string;
  fromAccount?: string;
  toAccount?: string;
  createdAt: string;
  fee?: number;
  reference?: string;
  merchantName?: string;
  category?: string;
}

interface TransactionItemProps {
  transaction: Transaction;
  onClick?: (transaction: Transaction) => void;
  onMenuClick?: (transaction: Transaction, anchorEl: HTMLElement) => void;
}

const TransactionItem = memo<TransactionItemProps>(({
  transaction,
  onClick,
  onMenuClick
}) => {
  const { statusColor, statusIcon, isDebit } = useMemo(() => {
    const isDebit = ['PAYMENT', 'WITHDRAWAL', 'TRANSFER_OUT'].includes(transaction.type);
    
    let statusColor: 'success' | 'warning' | 'error' | 'default' = 'default';
    let statusIcon = <SwapIcon />;

    switch (transaction.status) {
      case 'COMPLETED':
        statusColor = 'success';
        break;
      case 'PENDING':
        statusColor = 'warning';
        break;
      case 'FAILED':
        statusColor = 'error';
        break;
    }

    if (isDebit) {
      statusIcon = <ArrowUpIcon />;
    } else {
      statusIcon = <ArrowDownIcon />;
    }

    return { statusColor, statusIcon, isDebit };
  }, [transaction.type, transaction.status]);

  const formattedDate = useMemo(() => 
    format(parseISO(transaction.createdAt), 'MMM d, h:mm a'),
    [transaction.createdAt]
  );

  const formattedAmount = useMemo(() => {
    const prefix = isDebit ? '-' : '+';
    return `${prefix}${formatCurrency(transaction.amount, transaction.currency)}`;
  }, [transaction.amount, transaction.currency, isDebit]);

  const handleClick = () => {
    if (onClick) {
      onClick(transaction);
    }
  };

  const handleMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    if (onMenuClick) {
      onMenuClick(transaction, event.currentTarget);
    }
  };

  return (
    <ListItem
      button
      onClick={handleClick}
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
        '&:hover': {
          bgcolor: 'action.hover',
        },
      }}
      secondaryAction={
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography
            variant="subtitle1"
            fontWeight="bold"
            color={isDebit ? 'error.main' : 'success.main'}
          >
            {formattedAmount}
          </Typography>
          {transaction.fee && (
            <Tooltip title={`Fee: ${formatCurrency(transaction.fee, transaction.currency)}`}>
              <Typography variant="caption" color="text.secondary">
                +fee
              </Typography>
            </Tooltip>
          )}
          <IconButton
            size="small"
            onClick={handleMenuClick}
            sx={{ ml: 1 }}
          >
            <MoreIcon />
          </IconButton>
        </Box>
      }
    >
      <ListItemAvatar>
        <Avatar
          sx={{
            bgcolor: `${statusColor}.main`,
            color: 'white',
          }}
        >
          {statusIcon}
        </Avatar>
      </ListItemAvatar>

      <ListItemText
        primary={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
            <Typography variant="subtitle1" fontWeight="medium">
              {transaction.description || transaction.merchantName || 'Transaction'}
            </Typography>
            <Chip
              label={transaction.status}
              size="small"
              color={statusColor}
              variant="outlined"
            />
            {transaction.reference && (
              <Tooltip title={`Reference: ${transaction.reference}`}>
                <ReceiptIcon fontSize="small" color="action" />
              </Tooltip>
            )}
          </Box>
        }
        secondary={
          <Box>
            <Typography variant="body2" color="text.secondary">
              {transaction.type.replace('_', ' ').toLowerCase()}
              {transaction.toAccount && ` to ${transaction.toAccount}`}
              {transaction.fromAccount && ` from ${transaction.fromAccount}`}
            </Typography>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 0.5 }}>
              <Typography variant="caption" color="text.secondary">
                {formattedDate}
              </Typography>
              {transaction.category && (
                <Chip
                  label={transaction.category}
                  size="small"
                  variant="outlined"
                  sx={{ height: 20, fontSize: '0.7rem' }}
                />
              )}
            </Box>
          </Box>
        }
        sx={{ pr: 16 }} // Make space for the amount and menu button
      />
    </ListItem>
  );
});

TransactionItem.displayName = 'TransactionItem';

export default TransactionItem;