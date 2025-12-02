import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Button,
  IconButton,
  Skeleton,
  Chip,
  Menu,
  MenuItem,
  Divider,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import AddIcon from '@mui/icons-material/Add';
import RequestIcon from '@mui/icons-material/RequestPage';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import QrCodeIcon from '@mui/icons-material/QrCode';
import WalletIcon from '@mui/icons-material/AccountBalanceWallet';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';;
import { WalletBalance } from '../../types/wallet';
import { formatCurrency } from '../../utils/formatters';

interface WalletCardProps {
  balance: WalletBalance | null;
  loading: boolean;
  onSend: () => void;
  onRequest: () => void;
  onAddMoney: () => void;
  onShowQR?: () => void;
}

const WalletCard: React.FC<WalletCardProps> = ({
  balance,
  loading,
  onSend,
  onRequest,
  onAddMoney,
  onShowQR,
}) => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const getBalanceChange = () => {
    if (!balance) return null;
    const change = balance.monthlyReceived - balance.monthlySpent;
    const percentage = balance.monthlySpent > 0 
      ? Math.abs((change / balance.monthlySpent) * 100).toFixed(1)
      : '0';
    
    return {
      amount: change,
      percentage,
      isPositive: change >= 0,
    };
  };

  const balanceChange = getBalanceChange();

  return (
    <Card
      sx={{
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <CardContent sx={{ p: 3 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <WalletIcon />
            <Typography variant="h6">Main Wallet</Typography>
          </Box>
          <IconButton size="small" sx={{ color: 'white' }} onClick={handleMenuOpen}>
            <MoreVertIcon />
          </IconButton>
        </Box>

        {/* Balance */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="body2" sx={{ opacity: 0.9, mb: 1 }}>
            Available Balance
          </Typography>
          {loading ? (
            <Skeleton variant="text" width={200} height={48} sx={{ bgcolor: 'rgba(255,255,255,0.2)' }} />
          ) : (
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2 }}>
              <Typography variant="h3" component="div" fontWeight="bold">
                {formatCurrency(balance?.availableBalance || 0)}
              </Typography>
              {balanceChange && (
                <Chip
                  size="small"
                  icon={balanceChange.isPositive ? <TrendingUpIcon /> : <TrendingDownIcon />}
                  label={`${balanceChange.isPositive ? '+' : ''}${balanceChange.percentage}%`}
                  sx={{
                    bgcolor: balanceChange.isPositive ? 'success.main' : 'error.main',
                    color: 'white',
                  }}
                />
              )}
            </Box>
          )}
        </Box>

        {/* Sub-balances */}
        {balance && (
          <Box sx={{ display: 'flex', gap: 3, mb: 3 }}>
            <Box>
              <Typography variant="caption" sx={{ opacity: 0.8 }}>
                Pending
              </Typography>
              <Typography variant="body2" fontWeight="medium">
                {formatCurrency(balance.pendingBalance)}
              </Typography>
            </Box>
            {balance.frozenBalance > 0 && (
              <Box>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                  Frozen
                </Typography>
                <Typography variant="body2" fontWeight="medium">
                  {formatCurrency(balance.frozenBalance)}
                </Typography>
              </Box>
            )}
          </Box>
        )}

        <Divider sx={{ bgcolor: 'rgba(255,255,255,0.2)', my: 2 }} />

        {/* Actions */}
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button
            variant="contained"
            startIcon={<SendIcon />}
            onClick={onSend}
            sx={{
              flex: 1,
              bgcolor: 'rgba(255,255,255,0.2)',
              '&:hover': {
                bgcolor: 'rgba(255,255,255,0.3)',
              },
            }}
          >
            Send
          </Button>
          <Button
            variant="contained"
            startIcon={<RequestIcon />}
            onClick={onRequest}
            sx={{
              flex: 1,
              bgcolor: 'rgba(255,255,255,0.2)',
              '&:hover': {
                bgcolor: 'rgba(255,255,255,0.3)',
              },
            }}
          >
            Request
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={onAddMoney}
            sx={{
              flex: 1,
              bgcolor: 'rgba(255,255,255,0.2)',
              '&:hover': {
                bgcolor: 'rgba(255,255,255,0.3)',
              },
            }}
          >
            Add
          </Button>
        </Box>

        {/* Menu */}
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={handleMenuClose}
          anchorOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
        >
          <MenuItem onClick={() => { handleMenuClose(); onShowQR?.(); }}>
            <QrCodeIcon sx={{ mr: 1 }} /> Show QR Code
          </MenuItem>
          <MenuItem onClick={handleMenuClose}>View Details</MenuItem>
          <MenuItem onClick={handleMenuClose}>Transaction History</MenuItem>
          <MenuItem onClick={handleMenuClose}>Settings</MenuItem>
        </Menu>

        {/* Background decoration */}
        <Box
          sx={{
            position: 'absolute',
            bottom: -50,
            right: -50,
            width: 200,
            height: 200,
            borderRadius: '50%',
            bgcolor: 'rgba(255,255,255,0.1)',
          }}
        />
      </CardContent>
    </Card>
  );
};

export default WalletCard;