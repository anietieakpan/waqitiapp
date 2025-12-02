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
  Link,
} from '@mui/material';
import { CryptoTransaction } from '@/types/crypto';
import { formatCurrency, formatDate } from '@/utils/formatters';

interface CryptoTransactionsProps {
  transactions: CryptoTransaction[];
}

const getTypeColor = (type: string) => {
  switch (type) {
    case 'BUY':
      return 'success';
    case 'SELL':
      return 'error';
    case 'SEND':
      return 'warning';
    case 'RECEIVE':
      return 'info';
    case 'SWAP':
      return 'primary';
    default:
      return 'default';
  }
};

const CryptoTransactions: React.FC<CryptoTransactionsProps> = ({ transactions }) => {
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
            <TableCell>Currency</TableCell>
            <TableCell align="right">Amount</TableCell>
            <TableCell align="right">Value (USD)</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Tx Hash</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {transactions.map((tx) => (
            <TableRow key={tx.id} hover>
              <TableCell>
                <Typography variant="body2">{formatDate(tx.timestamp)}</Typography>
              </TableCell>
              <TableCell>
                <Chip label={tx.type} color={getTypeColor(tx.type)} size="small" />
              </TableCell>
              <TableCell>
                <Typography fontWeight="bold">{tx.currency}</Typography>
              </TableCell>
              <TableCell align="right">
                <Typography>
                  {tx.type === 'SEND' || tx.type === 'SELL' ? '-' : '+'}
                  {tx.amount.toFixed(8)}
                </Typography>
              </TableCell>
              <TableCell align="right">
                <Typography fontWeight="bold">{formatCurrency(tx.amountUSD)}</Typography>
              </TableCell>
              <TableCell>
                <Chip
                  label={tx.status}
                  color={
                    tx.status === 'CONFIRMED'
                      ? 'success'
                      : tx.status === 'PENDING'
                      ? 'warning'
                      : 'error'
                  }
                  size="small"
                />
                {tx.confirmations && (
                  <Typography variant="caption" display="block" color="text.secondary">
                    {tx.confirmations} confirmations
                  </Typography>
                )}
              </TableCell>
              <TableCell>
                {tx.txHash ? (
                  <Link
                    href={`https://etherscan.io/tx/${tx.txHash}`}
                    target="_blank"
                    rel="noopener"
                    variant="caption"
                  >
                    {tx.txHash.substring(0, 10)}...
                  </Link>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    N/A
                  </Typography>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default CryptoTransactions;
