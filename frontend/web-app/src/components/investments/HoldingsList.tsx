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
  Chip,
  Box,
  Button,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { formatCurrency, formatPercent } from '@/utils/formatters';

interface Holding {
  id: string;
  symbol: string;
  name: string;
  quantity: number;
  currentPrice: number;
  totalValue: number;
  gain: number;
  gainPercent: number;
  assetType?: string;
}

interface HoldingsListProps {
  holdings: Holding[] | null;
}

const HoldingsList: React.FC<HoldingsListProps> = ({ holdings }) => {
  if (!holdings || holdings.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <Typography color="text.secondary">No holdings yet</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Start investing to see your holdings here
        </Typography>
      </Paper>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Asset</TableCell>
            <TableCell align="right">Quantity</TableCell>
            <TableCell align="right">Price</TableCell>
            <TableCell align="right">Total Value</TableCell>
            <TableCell align="right">Gain/Loss</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {holdings.map((holding) => {
            const isPositive = holding.gain >= 0;
            return (
              <TableRow key={holding.id} hover>
                <TableCell>
                  <Box>
                    <Typography variant="body1" fontWeight="bold">
                      {holding.symbol}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {holding.name}
                    </Typography>
                    {holding.assetType && (
                      <Chip label={holding.assetType} size="small" sx={{ mt: 0.5 }} />
                    )}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Typography>{holding.quantity.toFixed(4)}</Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography>{formatCurrency(holding.currentPrice)}</Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography fontWeight="bold">{formatCurrency(holding.totalValue)}</Typography>
                </TableCell>
                <TableCell align="right">
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                    {isPositive ? (
                      <TrendingUpIcon fontSize="small" color="success" />
                    ) : (
                      <TrendingDownIcon fontSize="small" color="error" />
                    )}
                    <Box>
                      <Typography color={isPositive ? 'success.main' : 'error.main'}>
                        {isPositive ? '+' : ''}
                        {formatCurrency(holding.gain)}
                      </Typography>
                      <Typography variant="caption" color={isPositive ? 'success.main' : 'error.main'}>
                        {isPositive ? '+' : ''}
                        {formatPercent(holding.gainPercent)}
                      </Typography>
                    </Box>
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" variant="outlined" color="primary">
                    Trade
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default HoldingsList;
