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
} from '@mui/material';
import { formatCurrency, formatDate } from '@/utils/formatters';

interface Transaction {
  id: string;
  type: 'BUY' | 'SELL';
  symbol: string;
  quantity: number;
  price: number;
  total: number;
  date: string;
  status: 'COMPLETED' | 'PENDING' | 'CANCELLED';
}

// Mock data for now - will be replaced with real data from Redux
const mockTransactions: Transaction[] = [
  {
    id: '1',
    type: 'BUY',
    symbol: 'AAPL',
    quantity: 10,
    price: 150.25,
    total: 1502.50,
    date: new Date().toISOString(),
    status: 'COMPLETED',
  },
];

const InvestmentTransactions: React.FC = () => {
  const transactions = mockTransactions;

  if (transactions.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <Typography color="text.secondary">No transactions yet</Typography>
      </Paper>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Date</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Asset</TableCell>
            <TableCell align="right">Quantity</TableCell>
            <TableCell align="right">Price</TableCell>
            <TableCell align="right">Total</TableCell>
            <TableCell>Status</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {transactions.map((transaction) => (
            <TableRow key={transaction.id} hover>
              <TableCell>
                <Typography variant="body2">{formatDate(transaction.date)}</Typography>
              </TableCell>
              <TableCell>
                <Chip
                  label={transaction.type}
                  color={transaction.type === 'BUY' ? 'success' : 'error'}
                  size="small"
                />
              </TableCell>
              <TableCell>
                <Typography fontWeight="bold">{transaction.symbol}</Typography>
              </TableCell>
              <TableCell align="right">
                <Typography>{transaction.quantity}</Typography>
              </TableCell>
              <TableCell align="right">
                <Typography>{formatCurrency(transaction.price)}</Typography>
              </TableCell>
              <TableCell align="right">
                <Typography fontWeight="bold">{formatCurrency(transaction.total)}</Typography>
              </TableCell>
              <TableCell>
                <Chip
                  label={transaction.status}
                  color={
                    transaction.status === 'COMPLETED'
                      ? 'success'
                      : transaction.status === 'PENDING'
                      ? 'warning'
                      : 'default'
                  }
                  size="small"
                />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default InvestmentTransactions;
