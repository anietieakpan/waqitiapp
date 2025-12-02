import React, { useState } from 'react';
import {
  Card,
  CardContent,
  CardActions,
  Typography,
  Box,
  Avatar,
  IconButton,
  Chip,
  Button,
  Menu,
  MenuItem,
  Tooltip,
  LinearProgress,
  alpha,
  useTheme,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import LockIcon from '@mui/icons-material/Lock';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import SendIcon from '@mui/icons-material/Send';
import ReceiveIcon from '@mui/icons-material/CallReceived';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import EuroIcon from '@mui/icons-material/Euro';
import PoundIcon from '@mui/icons-material/CurrencyPound';
import YenIcon from '@mui/icons-material/CurrencyYen';
import BitcoinIcon from '@mui/icons-material/CurrencyBitcoin';;
import { formatCurrency, formatPercentage } from '../../utils/formatters';
import { Currency, WalletBalance } from '../../types/wallet';

interface WalletBalanceCardProps {
  balance: WalletBalance;
  showAmount: boolean;
  isMain?: boolean;
  onChange?: number;
  onSend?: () => void;
  onReceive?: () => void;
  onExchange?: () => void;
  onToggleVisibility?: () => void;
  onFreeze?: () => void;
  onSettings?: () => void;
}

const WalletBalanceCard: React.FC<WalletBalanceCardProps> = ({
  balance,
  showAmount,
  isMain = false,
  onChange = 0,
  onSend,
  onReceive,
  onExchange,
  onToggleVisibility,
  onFreeze,
  onSettings,
}) => {
  const theme = useTheme();
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  const getCurrencyIcon = (currency: Currency) => {
    const iconProps = { sx: { fontSize: isMain ? 40 : 24 } };
    switch (currency) {
      case 'USD': return <AttachMoneyIcon {...iconProps} />;
      case 'EUR': return <EuroIcon {...iconProps} />;
      case 'GBP': return <PoundIcon {...iconProps} />;
      case 'JPY': return <YenIcon {...iconProps} />;
      case 'BTC': return <BitcoinIcon {...iconProps} />;
      default: return <AttachMoneyIcon {...iconProps} />;
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

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setMenuAnchor(event.currentTarget);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const utilization = balance.limits?.dailyLimit 
    ? (balance.limits.dailyUsed / balance.limits.dailyLimit) * 100 
    : 0;

  return (
    <Card
      sx={{
        position: 'relative',
        background: isMain 
          ? `linear-gradient(135deg, ${getCurrencyColor(balance.currency)} 0%, ${alpha(getCurrencyColor(balance.currency), 0.8)} 100%)`
          : 'background.paper',
        color: isMain ? 'white' : 'text.primary',
        ...(balance.isFrozen && {
          opacity: 0.7,
          borderLeft: `4px solid ${theme.palette.warning.main}`,
        }),
      }}
    >
      <CardContent sx={{ pb: isMain ? 2 : 1 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Box sx={{ flex: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <Typography 
                variant={isMain ? "h6" : "subtitle2"} 
                sx={{ opacity: isMain ? 0.9 : 1 }}
              >
                {balance.currency} Wallet
              </Typography>
              {balance.isFrozen && (
                <Chip
                  icon={<LockIcon />}
                  label="Frozen"
                  size="small"
                  color="warning"
                  sx={{ 
                    ...(isMain && { 
                      bgcolor: alpha(theme.palette.common.white, 0.2),
                      color: 'white' 
                    })
                  }}
                />
              )}
            </Box>

            <Typography 
              variant={isMain ? "h3" : "h5"} 
              sx={{ 
                fontWeight: 700, 
                mb: isMain ? 2 : 1,
                fontSize: isMain ? '2.5rem' : '1.5rem',
              }}
            >
              {showAmount ? formatCurrency(balance.amount, balance.currency) : '••••••'}
            </Typography>

            {(balance.available !== balance.amount || balance.pending > 0) && (
              <Box sx={{ mb: 2 }}>
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <Box>
                    <Typography 
                      variant="caption" 
                      sx={{ opacity: isMain ? 0.8 : 0.7, display: 'block' }}
                    >
                      Available
                    </Typography>
                    <Typography variant={isMain ? "body1" : "body2"} sx={{ fontWeight: 500 }}>
                      {showAmount ? formatCurrency(balance.available, balance.currency) : '••••'}
                    </Typography>
                  </Box>
                  {balance.pending > 0 && (
                    <Box>
                      <Typography 
                        variant="caption" 
                        sx={{ opacity: isMain ? 0.8 : 0.7, display: 'block' }}
                      >
                        Pending
                      </Typography>
                      <Typography 
                        variant={isMain ? "body1" : "body2"} 
                        sx={{ 
                          fontWeight: 500,
                          color: isMain ? theme.palette.warning.light : theme.palette.warning.main,
                        }}
                      >
                        {showAmount ? formatCurrency(balance.pending, balance.currency) : '••••'}
                      </Typography>
                    </Box>
                  )}
                </Box>
              </Box>
            )}

            {/* Daily spending limit progress */}
            {balance.limits?.dailyLimit && (
              <Box sx={{ mb: 2 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="caption" sx={{ opacity: isMain ? 0.8 : 0.7 }}>
                    Daily Spending
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: isMain ? 0.8 : 0.7 }}>
                    {formatCurrency(balance.limits.dailyUsed, balance.currency)} / {formatCurrency(balance.limits.dailyLimit, balance.currency)}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(utilization, 100)}
                  sx={{
                    height: 4,
                    borderRadius: 2,
                    backgroundColor: alpha(isMain ? theme.palette.common.white : theme.palette.grey[300], 0.3),
                    '& .MuiLinearProgress-bar': {
                      backgroundColor: utilization > 80 
                        ? theme.palette.warning.main 
                        : isMain 
                          ? theme.palette.common.white 
                          : theme.palette.primary.main,
                    },
                  }}
                />
              </Box>
            )}

            {/* Performance indicator */}
            {onChange !== 0 && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
                {onChange > 0 ? (
                  <TrendingUpIcon 
                    sx={{ 
                      fontSize: 16, 
                      color: isMain ? theme.palette.success.light : theme.palette.success.main 
                    }} 
                  />
                ) : (
                  <TrendingDownIcon 
                    sx={{ 
                      fontSize: 16, 
                      color: isMain ? theme.palette.error.light : theme.palette.error.main 
                    }} 
                  />
                )}
                <Typography 
                  variant="caption" 
                  sx={{ 
                    color: onChange > 0 
                      ? (isMain ? theme.palette.success.light : theme.palette.success.main)
                      : (isMain ? theme.palette.error.light : theme.palette.error.main)
                  }}
                >
                  {formatPercentage(Math.abs(onChange))} {onChange > 0 ? 'up' : 'down'} this week
                </Typography>
              </Box>
            )}
          </Box>

          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1 }}>
            <Avatar
              sx={{
                bgcolor: isMain 
                  ? alpha(theme.palette.common.white, 0.2) 
                  : alpha(getCurrencyColor(balance.currency), 0.1),
                width: isMain ? 72 : 48,
                height: isMain ? 72 : 48,
                color: isMain ? 'inherit' : getCurrencyColor(balance.currency),
              }}
            >
              {getCurrencyIcon(balance.currency)}
            </Avatar>
            
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              <Tooltip title="Toggle visibility">
                <IconButton
                  size="small"
                  onClick={onToggleVisibility}
                  sx={{ 
                    color: isMain ? 'inherit' : 'text.secondary',
                    bgcolor: isMain ? alpha(theme.palette.common.white, 0.1) : 'transparent',
                  }}
                >
                  {showAmount ? <VisibilityIcon /> : <VisibilityOffIcon />}
                </IconButton>
              </Tooltip>
              
              <IconButton
                size="small"
                onClick={handleMenuOpen}
                sx={{ 
                  color: isMain ? 'inherit' : 'text.secondary',
                  bgcolor: isMain ? alpha(theme.palette.common.white, 0.1) : 'transparent',
                }}
              >
                <MoreVertIcon />
              </IconButton>
            </Box>
          </Box>
        </Box>
      </CardContent>

      {(onSend || onReceive || onExchange) && (
        <CardActions sx={{ px: 2, pb: 2, pt: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, width: '100%' }}>
            {onSend && (
              <Button
                size="small"
                variant={isMain ? "contained" : "outlined"}
                startIcon={<SendIcon />}
                onClick={onSend}
                disabled={balance.isFrozen}
                sx={{
                  flex: 1,
                  ...(isMain && {
                    bgcolor: alpha(theme.palette.common.white, 0.2),
                    '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.3) },
                  }),
                }}
              >
                Send
              </Button>
            )}
            
            {onReceive && (
              <Button
                size="small"
                variant={isMain ? "contained" : "outlined"}
                startIcon={<ReceiveIcon />}
                onClick={onReceive}
                sx={{
                  flex: 1,
                  ...(isMain && {
                    bgcolor: alpha(theme.palette.common.white, 0.2),
                    '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.3) },
                  }),
                }}
              >
                Receive
              </Button>
            )}
            
            {onExchange && balance.currency !== 'USD' && (
              <Button
                size="small"
                variant={isMain ? "contained" : "outlined"}
                startIcon={<SwapIcon />}
                onClick={onExchange}
                disabled={balance.isFrozen}
                sx={{
                  flex: 1,
                  ...(isMain && {
                    bgcolor: alpha(theme.palette.common.white, 0.2),
                    '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.3) },
                  }),
                }}
              >
                Exchange
              </Button>
            )}
          </Box>
        </CardActions>
      )}

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem onClick={() => { onSettings?.(); handleMenuClose(); }}>
          Settings
        </MenuItem>
        <MenuItem onClick={() => { onFreeze?.(); handleMenuClose(); }}>
          {balance.isFrozen ? 'Unfreeze' : 'Freeze'} Wallet
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          View Statements
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          Export Transactions
        </MenuItem>
      </Menu>
    </Card>
  );
};

export default WalletBalanceCard;