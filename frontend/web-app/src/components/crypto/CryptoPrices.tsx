import React from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Typography,
  Box,
  Avatar,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { CryptoPrice, CryptoCurrency } from '@/types/crypto';
import { formatCurrency } from '@/utils/formatters';

interface CryptoPricesProps {
  prices: Record<CryptoCurrency, CryptoPrice>;
}

const CryptoPrices: React.FC<CryptoPricesProps> = ({ prices }) => {
  const pricesArray = Object.values(prices);

  if (pricesArray.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <Typography color="text.secondary">Loading prices...</Typography>
      </Paper>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Asset</TableCell>
            <TableCell align="right">Price</TableCell>
            <TableCell align="right">24h Change</TableCell>
            <TableCell align="right">7d Change</TableCell>
            <TableCell align="right">Market Cap</TableCell>
            <TableCell align="right">24h Volume</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {pricesArray.map((price) => {
            const is24hPositive = price.change24h >= 0;
            const is7dPositive = price.change7d >= 0;

            return (
              <TableRow key={price.currency} hover>
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="body1" fontWeight="bold">
                      {price.currency}
                    </Typography>
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Typography fontWeight="bold">{formatCurrency(price.priceUSD)}</Typography>
                </TableCell>
                <TableCell align="right">
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                    {is24hPositive ? (
                      <TrendingUpIcon fontSize="small" color="success" />
                    ) : (
                      <TrendingDownIcon fontSize="small" color="error" />
                    )}
                    <Typography color={is24hPositive ? 'success.main' : 'error.main'}>
                      {is24hPositive ? '+' : ''}
                      {price.change24h.toFixed(2)}%
                    </Typography>
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Typography color={is7dPositive ? 'success.main' : 'error.main'}>
                    {is7dPositive ? '+' : ''}
                    {price.change7d.toFixed(2)}%
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography>${(price.marketCap / 1e9).toFixed(2)}B</Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography>${(price.volume24h / 1e6).toFixed(2)}M</Typography>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default CryptoPrices;
