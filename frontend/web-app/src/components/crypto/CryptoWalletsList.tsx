import React from 'react';
import { Grid, Card, CardContent, Typography, Box, Avatar } from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { CryptoWallet, CryptoPrice, CryptoCurrency } from '@/types/crypto';
import { formatCurrency } from '@/utils/formatters';

interface CryptoWalletsListProps {
  wallets: CryptoWallet[];
  prices: Record<CryptoCurrency, CryptoPrice>;
}

const getCryptoIcon = (currency: string) => {
  const icons: Record<string, string> = {
    BTC: '₿',
    ETH: 'Ξ',
    USDT: '₮',
    USDC: '$',
    BNB: 'BNB',
    SOL: 'SOL',
    ADA: '₳',
    MATIC: 'MATIC',
  };
  return icons[currency] || currency;
};

const getCryptoColor = (currency: string) => {
  const colors: Record<string, string> = {
    BTC: '#F7931A',
    ETH: '#627EEA',
    USDT: '#26A17B',
    USDC: '#2775CA',
    BNB: '#F3BA2F',
    SOL: '#00FFA3',
    ADA: '#0033AD',
    MATIC: '#8247E5',
  };
  return colors[currency] || '#757575';
};

const CryptoWalletsList: React.FC<CryptoWalletsListProps> = ({ wallets, prices }) => {
  if (wallets.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <Typography variant="h6" color="text.secondary">
          No crypto wallets yet
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Buy or receive crypto to get started
        </Typography>
      </Box>
    );
  }

  return (
    <Grid container spacing={2}>
      {wallets.map((wallet) => {
        const price = prices[wallet.currency];
        const change24h = price?.change24h || 0;
        const isPositive = change24h >= 0;

        return (
          <Grid item xs={12} sm={6} md={4} key={wallet.id}>
            <Card sx={{ cursor: 'pointer', '&:hover': { boxShadow: 4 } }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Avatar
                    sx={{
                      bgcolor: getCryptoColor(wallet.currency),
                      mr: 2,
                      width: 48,
                      height: 48,
                    }}
                  >
                    {getCryptoIcon(wallet.currency)}
                  </Avatar>
                  <Box>
                    <Typography variant="h6">{wallet.currency}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {wallet.network}
                    </Typography>
                  </Box>
                </Box>

                <Typography variant="h5" gutterBottom>
                  {wallet.balance.toFixed(8)}
                </Typography>

                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body1" color="text.secondary">
                    {formatCurrency(wallet.balanceUSD)}
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    {isPositive ? (
                      <TrendingUpIcon fontSize="small" color="success" />
                    ) : (
                      <TrendingDownIcon fontSize="small" color="error" />
                    )}
                    <Typography
                      variant="caption"
                      color={isPositive ? 'success.main' : 'error.main'}
                    >
                      {isPositive ? '+' : ''}
                      {change24h.toFixed(2)}%
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        );
      })}
    </Grid>
  );
};

export default CryptoWalletsList;
