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
  Collapse,
  Divider,
  LinearProgress,
  useTheme,
  alpha,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import AddIcon from '@mui/icons-material/Add';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import EuroIcon from '@mui/icons-material/Euro';
import PoundIcon from '@mui/icons-material/CurrencyPound';
import YenIcon from '@mui/icons-material/CurrencyYen';
import BitcoinIcon from '@mui/icons-material/CurrencyBitcoin';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import WalletIcon from '@mui/icons-material/Wallet';;
import { formatCurrency, formatPercentage } from '../../utils/formatters';
import { Currency, WalletBalance } from '../../types/wallet';

interface CurrencyBreakdownProps {
  balances: WalletBalance[];
  showValues: boolean;
  onCurrencySelect?: (currency: Currency) => void;
  onAddCurrency?: () => void;
  onExchange?: (from: Currency, to: Currency) => void;
}

const CurrencyBreakdown: React.FC<CurrencyBreakdownProps> = ({
  balances,
  showValues,
  onCurrencySelect,
  onAddCurrency,
  onExchange,
}) => {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(true);
  const [selectedCurrency, setSelectedCurrency] = useState<Currency | null>(null);

  const getCurrencyIcon = (currency: Currency) => {
    switch (currency) {
      case 'USD': return <AttachMoneyIcon />;
      case 'EUR': return <EuroIcon />;
      case 'GBP': return <PoundIcon />;
      case 'JPY': return <YenIcon />;
      case 'BTC': return <BitcoinIcon />;
      default: return <AttachMoneyIcon />;
    }
  };

  const getCurrencyColor = (currency: Currency) => {
    switch (currency) {
      case 'USD': return theme.palette.success.main;
      case 'EUR': return theme.palette.info.main;
      case 'GBP': return theme.palette.warning.main;
      case 'JPY': return theme.palette.error.main;
      case 'BTC': return '#f7931a';
      default: return theme.palette.primary.main;
    }
  };

  const getTotalValue = () => {
    return balances.reduce((total, balance) => {
      // Convert to USD for total calculation (simplified - would use real exchange rates)
      const rate = getExchangeRate(balance.currency);
      return total + (balance.amount * rate);
    }, 0);
  };

  const getExchangeRate = (currency: Currency) => {
    // Simplified exchange rates - would be fetched from API
    switch (currency) {
      case 'USD': return 1;
      case 'EUR': return 1.1;
      case 'GBP': return 1.25;
      case 'JPY': return 0.007;
      case 'BTC': return 45000;
      default: return 1;
    }
  };

  const getPercentageOfTotal = (balance: WalletBalance) => {
    const total = getTotalValue();
    if (total === 0) return 0;
    const rate = getExchangeRate(balance.currency);
    return ((balance.amount * rate) / total) * 100;
  };

  const handleCurrencyClick = (currency: Currency) => {
    setSelectedCurrency(selectedCurrency === currency ? null : currency);
    onCurrencySelect?.(currency);
  };

  const sortedBalances = [...balances].sort((a, b) => {
    const aValue = a.amount * getExchangeRate(a.currency);
    const bValue = b.amount * getExchangeRate(b.currency);
    return bValue - aValue;
  });

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
          Currency Breakdown
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Chip
            label={`${balances.length} currencies`}
            size="small"
            variant="outlined"
          />
          <IconButton size="small">
            {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </IconButton>
        </Box>
      </Box>

      <Collapse in={expanded}>
        <Divider />
        <List sx={{ py: 0 }}>
          {sortedBalances.map((balance, index) => {
            const percentage = getPercentageOfTotal(balance);
            const isSelected = selectedCurrency === balance.currency;
            
            return (
              <React.Fragment key={balance.currency}>
                <ListItem
                  button
                  onClick={() => handleCurrencyClick(balance.currency)}
                  sx={{
                    py: 2,
                    ...(isSelected && {
                      bgcolor: alpha(getCurrencyColor(balance.currency), 0.08),
                    }),
                  }}
                >
                  <ListItemIcon>
                    <Avatar
                      sx={{
                        bgcolor: alpha(getCurrencyColor(balance.currency), 0.1),
                        color: getCurrencyColor(balance.currency),
                        width: 48,
                        height: 48,
                      }}
                    >
                      {getCurrencyIcon(balance.currency)}
                    </Avatar>
                  </ListItemIcon>
                  
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {balance.currency}
                        </Typography>
                        {balance.isFrozen && (
                          <Chip label="Frozen" size="small" color="warning" />
                        )}
                        {balance.isDefault && (
                          <Chip label="Primary" size="small" color="primary" />
                        )}
                      </Box>
                    }
                    secondary={
                      <Box sx={{ mt: 1 }}>
                        <Typography variant="body2" color="text.secondary">
                          {showValues ? formatCurrency(balance.amount, balance.currency) : '••••••'}
                          {balance.pending > 0 && (
                            <Typography
                              component="span"
                              variant="caption"
                              sx={{ ml: 1, color: theme.palette.warning.main }}
                            >
                              (+{formatCurrency(balance.pending, balance.currency)} pending)
                            </Typography>
                          )}
                        </Typography>
                        
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
                          <LinearProgress
                            variant="determinate"
                            value={percentage}
                            sx={{
                              flex: 1,
                              height: 4,
                              borderRadius: 2,
                              backgroundColor: alpha(theme.palette.grey[300], 0.3),
                              '& .MuiLinearProgress-bar': {
                                backgroundColor: getCurrencyColor(balance.currency),
                              },
                            }}
                          />
                          <Typography variant="caption" color="text.secondary">
                            {formatPercentage(percentage)}
                          </Typography>
                        </Box>
                      </Box>
                    }
                  />
                  
                  <ListItemSecondaryAction>
                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 1 }}>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {showValues ? `$${(balance.amount * getExchangeRate(balance.currency)).toFixed(2)}` : '••••'}
                      </Typography>
                      
                      {balance.change24h !== undefined && (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                          {balance.change24h >= 0 ? (
                            <TrendingUpIcon sx={{ fontSize: 16, color: theme.palette.success.main }} />
                          ) : (
                            <TrendingDownIcon sx={{ fontSize: 16, color: theme.palette.error.main }} />
                          )}
                          <Typography
                            variant="caption"
                            sx={{
                              color: balance.change24h >= 0 
                                ? theme.palette.success.main 
                                : theme.palette.error.main,
                              fontWeight: 500,
                            }}
                          >
                            {formatPercentage(Math.abs(balance.change24h))}
                          </Typography>
                        </Box>
                      )}
                    </Box>
                  </ListItemSecondaryAction>
                </ListItem>

                {/* Expanded details */}
                <Collapse in={isSelected}>
                  <Box sx={{ pl: 9, pr: 2, pb: 2 }}>
                    <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<SwapIcon />}
                        onClick={() => onExchange?.(balance.currency, 'USD')}
                        disabled={balance.isFrozen}
                      >
                        Exchange
                      </Button>
                      <Button
                        size="small"
                        variant="outlined"
                        onClick={() => onCurrencySelect?.(balance.currency)}
                      >
                        View Details
                      </Button>
                    </Box>
                    
                    {/* Additional currency details */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Typography variant="caption" color="text.secondary">
                          Available Balance
                        </Typography>
                        <Typography variant="caption">
                          {showValues ? formatCurrency(balance.available, balance.currency) : '••••'}
                        </Typography>
                      </Box>
                      
                      {balance.pending > 0 && (
                        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Typography variant="caption" color="text.secondary">
                            Pending
                          </Typography>
                          <Typography variant="caption" color="warning.main">
                            {showValues ? formatCurrency(balance.pending, balance.currency) : '••••'}
                          </Typography>
                        </Box>
                      )}
                      
                      {balance.limits?.dailyLimit && (
                        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Typography variant="caption" color="text.secondary">
                            Daily Limit Remaining
                          </Typography>
                          <Typography variant="caption">
                            {showValues 
                              ? formatCurrency(balance.limits.dailyLimit - balance.limits.dailyUsed, balance.currency)
                              : '••••'
                            }
                          </Typography>
                        </Box>
                      )}
                      
                      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Typography variant="caption" color="text.secondary">
                          Last Updated
                        </Typography>
                        <Typography variant="caption">
                          {new Date(balance.lastUpdated).toLocaleTimeString()}
                        </Typography>
                      </Box>
                    </Box>
                  </Box>
                </Collapse>

                {index < sortedBalances.length - 1 && <Divider variant="inset" component="li" />}
              </React.Fragment>
            );
          })}
        </List>

        <Divider />
        <Box sx={{ p: 2 }}>
          <Button
            fullWidth
            variant="outlined"
            startIcon={<AddIcon />}
            onClick={onAddCurrency}
            sx={{ mb: 1 }}
          >
            Add New Currency
          </Button>
          
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center' }}>
            Total Portfolio Value: {showValues ? `$${getTotalValue().toFixed(2)}` : '••••••'}
          </Typography>
        </Box>
      </Collapse>
    </Paper>
  );
};

export default CurrencyBreakdown;