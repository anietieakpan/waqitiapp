import React, { useEffect, useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Chip,
  IconButton,
  TextField,
  MenuItem,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tabs,
  Tab,
  Grid,
  Card,
  CardContent,
  InputAdornment,
  Tooltip,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import GetAppIcon from '@mui/icons-material/GetApp';
import VisibilityIcon from '@mui/icons-material/Visibility';
import ReceiptIcon from '@mui/icons-material/Receipt';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { fetchTransactions } from '../../store/slices/paymentSlice';
import { Transaction } from '../../types/payment';
import { formatCurrency, formatDate } from '../../utils/formatters';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`transaction-tabpanel-${index}`}
      aria-labelledby={`transaction-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

const TransactionsPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const { transactions, transactionsLoading } = useAppSelector((state) => state.payment);
  const { wallets } = useAppSelector((state) => state.wallet);
  
  const [tabValue, setTabValue] = useState(0);
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions, filters]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
    
    // Update filters based on tab
    switch (newValue) {
      case 0: // All
        updateFilters({ type: undefined });
        break;
      case 1: // Sent
        updateFilters({ type: 'TRANSFER' });
        break;
      case 2: // Received
        updateFilters({ type: 'DEPOSIT' });
        break;
      case 3: // Pending
        updateFilters({ status: 'PENDING' });
        break;
    }
  };

  const handlePageChange = (event: unknown, newPage: number) => {
    fetchTransactions(newPage + 1, pagination.itemsPerPage);
  };

  const handleRowsPerPageChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newLimit = parseInt(event.target.value, 10);
    fetchTransactions(1, newLimit);
  };

  const handleViewDetails = (transaction: Transaction) => {
    setSelectedTransaction(transaction);
    setDetailsOpen(true);
  };

  const getStatusColor = (status: TransactionStatus) => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'FAILED':
        return 'error';
      case 'CANCELLED':
        return 'default';
      default:
        return 'default';
    }
  };

  const getTypeIcon = (type: TransactionType) => {
    switch (type) {
      case 'DEPOSIT':
        return <TrendingUp color="success" />;
      case 'WITHDRAWAL':
        return <TrendingDown color="error" />;
      case 'TRANSFER':
        return <SwapHoriz color="primary" />;
      default:
        return <SwapHoriz />;
    }
  };

  const filteredTransactions = transactions.filter(transaction => {
    if (tabValue === 0) return true;
    if (tabValue === 1) return transaction.type === 'TRANSFER';
    if (tabValue === 2) return transaction.type === 'DEPOSIT';
    if (tabValue === 3) return transaction.status === 'PENDING';
    return true;
  });

  const stats = {
    total: transactions.length,
    completed: transactions.filter(t => t.status === 'COMPLETED').length,
    pending: transactions.filter(t => t.status === 'PENDING').length,
    failed: transactions.filter(t => t.status === 'FAILED').length,
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Transactions
      </Typography>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Transactions
              </Typography>
              <Typography variant="h4">
                {stats.total}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Completed
              </Typography>
              <Typography variant="h4" color="success.main">
                {stats.completed}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Pending
              </Typography>
              <Typography variant="h4" color="warning.main">
                {stats.pending}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Failed
              </Typography>
              <Typography variant="h4" color="error.main">
                {stats.failed}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Paper sx={{ width: '100%', mb: 2 }}>
        {/* Search and Filters */}
        <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                placeholder="Search transactions..."
                value={filters.search || ''}
                onChange={(e) => updateFilters({ search: e.target.value })}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search />
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                <Button
                  startIcon={<FilterList />}
                  onClick={() => setFiltersOpen(true)}
                >
                  Filters
                </Button>
                <Button
                  startIcon={<GetApp />}
                  onClick={() => exportTransactions('csv')}
                >
                  Export
                </Button>
              </Box>
            </Grid>
          </Grid>
        </Box>

        {/* Tabs */}
        <Tabs value={tabValue} onChange={handleTabChange}>
          <Tab label="All" />
          <Tab label="Sent" />
          <Tab label="Received" />
          <Tab label="Pending" />
        </Tabs>

        {/* Transaction Table */}
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Type</TableCell>
                <TableCell>Reference</TableCell>
                <TableCell>Amount</TableCell>
                <TableCell>From/To</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Date</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredTransactions.map((transaction) => (
                <TableRow key={transaction.id} hover>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      {getTypeIcon(transaction.type)}
                      {transaction.type}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {transaction.reference}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography
                      color={transaction.type === 'DEPOSIT' ? 'success.main' : 'text.primary'}
                      fontWeight={transaction.type === 'DEPOSIT' ? 'bold' : 'normal'}
                    >
                      {transaction.type === 'DEPOSIT' ? '+' : '-'}
                      {formatCurrency(transaction.amount, transaction.currency)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {transaction.fromAccount || transaction.toAccount}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={transaction.status}
                      color={getStatusColor(transaction.status)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {formatDate(transaction.createdAt)}
                  </TableCell>
                  <TableCell>
                    <Tooltip title="View Details">
                      <IconButton
                        size="small"
                        onClick={() => handleViewDetails(transaction)}
                      >
                        <Visibility />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Receipt">
                      <IconButton size="small">
                        <Receipt />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>

        {/* Pagination */}
        <TablePagination
          rowsPerPageOptions={[10, 25, 50]}
          component="div"
          count={pagination.totalItems}
          rowsPerPage={pagination.itemsPerPage}
          page={pagination.currentPage - 1}
          onPageChange={handlePageChange}
          onRowsPerPageChange={handleRowsPerPageChange}
        />
      </Paper>

      {/* Transaction Details Dialog */}
      <Dialog
        open={detailsOpen}
        onClose={() => setDetailsOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Transaction Details
        </DialogTitle>
        <DialogContent>
          {selectedTransaction && (
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Reference
                </Typography>
                <Typography variant="body1" fontFamily="monospace">
                  {selectedTransaction.reference}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Status
                </Typography>
                <Chip
                  label={selectedTransaction.status}
                  color={getStatusColor(selectedTransaction.status)}
                  size="small"
                />
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Amount
                </Typography>
                <Typography variant="h6">
                  {formatCurrency(selectedTransaction.amount, selectedTransaction.currency)}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Fee
                </Typography>
                <Typography variant="body1">
                  {formatCurrency(selectedTransaction.fee || 0, selectedTransaction.currency)}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  From
                </Typography>
                <Typography variant="body1">
                  {selectedTransaction.fromAccount}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  To
                </Typography>
                <Typography variant="body1">
                  {selectedTransaction.toAccount}
                </Typography>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="subtitle2" color="textSecondary">
                  Description
                </Typography>
                <Typography variant="body1">
                  {selectedTransaction.description || 'No description'}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Created
                </Typography>
                <Typography variant="body1">
                  {formatDate(selectedTransaction.createdAt)}
                </Typography>
              </Grid>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Updated
                </Typography>
                <Typography variant="body1">
                  {formatDate(selectedTransaction.updatedAt)}
                </Typography>
              </Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsOpen(false)}>Close</Button>
          <Button variant="contained" startIcon={<Receipt />}>
            Download Receipt
          </Button>
        </DialogActions>
      </Dialog>

      {/* Filters Dialog */}
      <Dialog
        open={filtersOpen}
        onClose={() => setFiltersOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Filter Transactions</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={6}>
              <TextField
                select
                fullWidth
                label="Status"
                value={filters.status || ''}
                onChange={(e) => updateFilters({ status: e.target.value as TransactionStatus })}
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="COMPLETED">Completed</MenuItem>
                <MenuItem value="FAILED">Failed</MenuItem>
                <MenuItem value="CANCELLED">Cancelled</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField
                select
                fullWidth
                label="Type"
                value={filters.type || ''}
                onChange={(e) => updateFilters({ type: e.target.value as TransactionType })}
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="DEPOSIT">Deposit</MenuItem>
                <MenuItem value="WITHDRAWAL">Withdrawal</MenuItem>
                <MenuItem value="TRANSFER">Transfer</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <DatePicker
                label="From Date"
                value={filters.fromDate || null}
                onChange={(date) => updateFilters({ fromDate: date })}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Grid>
            <Grid item xs={6}>
              <DatePicker
                label="To Date"
                value={filters.toDate || null}
                onChange={(date) => updateFilters({ toDate: date })}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                select
                fullWidth
                label="Wallet"
                value={filters.walletId || ''}
                onChange={(e) => updateFilters({ walletId: e.target.value })}
              >
                <MenuItem value="">All Wallets</MenuItem>
                {wallets.map((wallet) => (
                  <MenuItem key={wallet.id} value={wallet.id}>
                    {wallet.name} ({wallet.currency})
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFiltersOpen(false)}>Cancel</Button>
          <Button
            onClick={() => {
              updateFilters({});
              setFiltersOpen(false);
            }}
          >
            Clear Filters
          </Button>
          <Button
            variant="contained"
            onClick={() => setFiltersOpen(false)}
          >
            Apply
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TransactionsPage;