import React, { useState } from 'react';
import { useQuery } from 'react-query';
import {
  Box,
  Typography,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Chip,
  IconButton,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button,
  Grid,
  Divider,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Paper,
  InputAdornment,
  Pagination,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import DownloadIcon from '@mui/icons-material/Download';
import ReceiptIcon from '@mui/icons-material/Receipt';
import SendIcon from '@mui/icons-material/Send';
import CallReceivedIcon from '@mui/icons-material/CallReceived';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import MoreIcon from '@mui/icons-material/More';
import CloseIcon from '@mui/icons-material/Close';
import DateRangeIcon from '@mui/icons-material/DateRange';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { format } from 'date-fns';

import { Transaction, PaymentStatus } from '@/types/payment';
import { paymentService } from '@/services/paymentService';
import toast from 'react-hot-toast';

const PaymentHistory: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<PaymentStatus | 'all'>('all');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [dateFrom, setDateFrom] = useState<Date | null>(null);
  const [dateTo, setDateTo] = useState<Date | null>(null);
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [showFilters, setShowFilters] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(10);

  // Mock data for demonstration
  const mockTransactions: Transaction[] = [
    {
      id: 'txn_001',
      type: 'send',
      status: PaymentStatus.COMPLETED,
      amount: 125.50,
      currency: 'USD',
      description: 'Coffee payment',
      counterparty: {
        name: 'John Doe',
        email: 'john@example.com',
        avatar: undefined,
      },
      fee: 0.00,
      createdAt: '2024-01-15T10:30:00Z',
      completedAt: '2024-01-15T10:30:15Z',
    },
    {
      id: 'txn_002',
      type: 'receive',
      status: PaymentStatus.COMPLETED,
      amount: 75.00,
      currency: 'USD',
      description: 'Lunch split',
      counterparty: {
        name: 'Jane Smith',
        email: 'jane@example.com',
        avatar: undefined,
      },
      fee: 0.00,
      createdAt: '2024-01-14T14:20:00Z',
      completedAt: '2024-01-14T14:20:10Z',
    },
    {
      id: 'txn_003',
      type: 'send',
      status: PaymentStatus.PENDING,
      amount: 200.00,
      currency: 'USD',
      description: 'Rent payment',
      counterparty: {
        name: 'Mike Johnson',
        email: 'mike@example.com',
        avatar: undefined,
      },
      fee: 2.50,
      createdAt: '2024-01-13T09:15:00Z',
    },
    {
      id: 'txn_004',
      type: 'request',
      status: PaymentStatus.EXPIRED,
      amount: 50.00,
      currency: 'USD',
      description: 'Dinner payment',
      counterparty: {
        name: 'Sarah Wilson',
        email: 'sarah@example.com',
        avatar: undefined,
      },
      fee: 0.00,
      createdAt: '2024-01-12T19:45:00Z',
      failureReason: 'Request expired after 7 days',
    },
    {
      id: 'txn_005',
      type: 'deposit',
      status: PaymentStatus.COMPLETED,
      amount: 500.00,
      currency: 'USD',
      description: 'Bank transfer',
      counterparty: {
        name: 'Chase Bank',
        email: 'noreply@chase.com',
        avatar: undefined,
      },
      fee: 0.00,
      createdAt: '2024-01-11T16:00:00Z',
      completedAt: '2024-01-11T16:02:30Z',
    },
  ];

  const { data: transactions, isLoading, error } = useQuery(
    ['transactions', currentPage, pageSize, searchTerm, statusFilter, typeFilter, dateFrom, dateTo],
    () => paymentService.getTransactions({
      page: currentPage,
      limit: pageSize,
      search: searchTerm,
      status: statusFilter === 'all' ? undefined : statusFilter,
      type: typeFilter === 'all' ? undefined : typeFilter,
      dateFrom: dateFrom?.toISOString(),
      dateTo: dateTo?.toISOString(),
    }),
    {
      keepPreviousData: true,
      // Use mock data for demonstration
      select: () => ({
        transactions: mockTransactions,
        totalCount: mockTransactions.length,
        totalPages: Math.ceil(mockTransactions.length / pageSize),
      }),
    }
  );

  const getStatusColor = (status: PaymentStatus) => {
    switch (status) {
      case PaymentStatus.COMPLETED:
        return 'success';
      case PaymentStatus.PENDING:
      case PaymentStatus.PROCESSING:
        return 'warning';
      case PaymentStatus.FAILED:
      case PaymentStatus.CANCELLED:
      case PaymentStatus.EXPIRED:
        return 'error';
      default:
        return 'default';
    }
  };

  const getTransactionIcon = (type: string) => {
    switch (type) {
      case 'send':
        return <Send />;
      case 'receive':
        return <CallReceived />;
      case 'request':
        return <RequestQuote />;
      case 'deposit':
      case 'withdrawal':
        return <AccountBalanceWallet />;
      default:
        return <Receipt />;
    }
  };

  const getAmountColor = (type: string) => {
    switch (type) {
      case 'send':
      case 'withdrawal':
        return 'error.main';
      case 'receive':
      case 'deposit':
        return 'success.main';
      default:
        return 'text.primary';
    }
  };

  const getAmountPrefix = (type: string) => {
    switch (type) {
      case 'send':
      case 'withdrawal':
        return '-';
      case 'receive':
      case 'deposit':
        return '+';
      default:
        return '';
    }
  };

  const filteredTransactions = transactions?.transactions?.filter(transaction => {
    const matchesSearch = searchTerm === '' || 
      transaction.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
      transaction.counterparty.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      transaction.counterparty.email.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus = statusFilter === 'all' || transaction.status === statusFilter;
    const matchesType = typeFilter === 'all' || transaction.type === typeFilter;
    
    return matchesSearch && matchesStatus && matchesType;
  }) || [];

  const handleExportTransactions = () => {
    // In a real app, this would generate and download a CSV/PDF
    toast.success('Transaction history exported successfully!');
  };

  const handleViewReceipt = (transaction: Transaction) => {
    setSelectedTransaction(transaction);
  };

  const clearFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
    setTypeFilter('all');
    setDateFrom(null);
    setDateTo(null);
  };

  const renderTransactionItem = (transaction: Transaction) => (
    <Card key={transaction.id} sx={{ mb: 2 }}>
      <CardContent>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" flex={1}>
            <Avatar sx={{ mr: 2, bgcolor: getStatusColor(transaction.status) + '.light' }}>
              {getTransactionIcon(transaction.type)}
            </Avatar>
            <Box flex={1}>
              <Typography variant="subtitle2" fontWeight="medium">
                {transaction.description}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {transaction.counterparty.name} • {format(new Date(transaction.createdAt), 'MMM dd, yyyy')}
              </Typography>
              <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                <Chip
                  label={transaction.status}
                  color={getStatusColor(transaction.status) as any}
                  size="small"
                />
                <Chip
                  label={transaction.type}
                  variant="outlined"
                  size="small"
                />
              </Box>
            </Box>
          </Box>
          <Box textAlign="right">
            <Typography
              variant="h6"
              color={getAmountColor(transaction.type)}
              fontWeight="medium"
            >
              {getAmountPrefix(transaction.type)}${transaction.amount.toFixed(2)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {transaction.currency}
            </Typography>
            {transaction.fee > 0 && (
              <Typography variant="caption" color="text.secondary">
                Fee: ${transaction.fee.toFixed(2)}
              </Typography>
            )}
          </Box>
          <IconButton onClick={() => handleViewReceipt(transaction)}>
            <More />
          </IconButton>
        </Box>
      </CardContent>
    </Card>
  );

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Box sx={{ p: 3 }}>
        <Typography variant="h5" gutterBottom>
          Transaction History
        </Typography>

        {/* Summary Cards */}
        <Grid container spacing={3} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box display="flex" alignItems="center">
                  <TrendingUp color="success" sx={{ mr: 1 }} />
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total Received
                    </Typography>
                    <Typography variant="h6" color="success.main">
                      $1,234.50
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box display="flex" alignItems="center">
                  <TrendingDown color="error" sx={{ mr: 1 }} />
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total Sent
                    </Typography>
                    <Typography variant="h6" color="error.main">
                      $856.25
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box display="flex" alignItems="center">
                  <Receipt color="primary" sx={{ mr: 1 }} />
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total Transactions
                    </Typography>
                    <Typography variant="h6">
                      {filteredTransactions.length}
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box display="flex" alignItems="center">
                  <AccountBalanceWallet color="info" sx={{ mr: 1 }} />
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total Fees
                    </Typography>
                    <Typography variant="h6">
                      $12.50
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Search and Filters */}
        <Paper sx={{ p: 2, mb: 3 }}>
          <Box display="flex" alignItems="center" gap={2} mb={2}>
            <TextField
              placeholder="Search transactions..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
              }}
              size="small"
              sx={{ flex: 1 }}
            />
            <Button
              variant="outlined"
              startIcon={<FilterList />}
              onClick={() => setShowFilters(!showFilters)}
            >
              Filters
            </Button>
            <Button
              variant="outlined"
              startIcon={<Download />}
              onClick={handleExportTransactions}
            >
              Export
            </Button>
          </Box>

          {showFilters && (
            <Box>
              <Divider sx={{ mb: 2 }} />
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Status</InputLabel>
                    <Select
                      value={statusFilter}
                      label="Status"
                      onChange={(e) => setStatusFilter(e.target.value as PaymentStatus | 'all')}
                    >
                      <MenuItem value="all">All Statuses</MenuItem>
                      <MenuItem value={PaymentStatus.COMPLETED}>Completed</MenuItem>
                      <MenuItem value={PaymentStatus.PENDING}>Pending</MenuItem>
                      <MenuItem value={PaymentStatus.PROCESSING}>Processing</MenuItem>
                      <MenuItem value={PaymentStatus.FAILED}>Failed</MenuItem>
                      <MenuItem value={PaymentStatus.CANCELLED}>Cancelled</MenuItem>
                      <MenuItem value={PaymentStatus.EXPIRED}>Expired</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Type</InputLabel>
                    <Select
                      value={typeFilter}
                      label="Type"
                      onChange={(e) => setTypeFilter(e.target.value)}
                    >
                      <MenuItem value="all">All Types</MenuItem>
                      <MenuItem value="send">Send</MenuItem>
                      <MenuItem value="receive">Receive</MenuItem>
                      <MenuItem value="request">Request</MenuItem>
                      <MenuItem value="deposit">Deposit</MenuItem>
                      <MenuItem value="withdrawal">Withdrawal</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <DatePicker
                    label="From Date"
                    value={dateFrom}
                    onChange={(date) => setDateFrom(date)}
                    slotProps={{ textField: { size: 'small', fullWidth: true } }}
                  />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <DatePicker
                    label="To Date"
                    value={dateTo}
                    onChange={(date) => setDateTo(date)}
                    slotProps={{ textField: { size: 'small', fullWidth: true } }}
                  />
                </Grid>
              </Grid>
              <Box display="flex" justifyContent="flex-end" mt={2}>
                <Button onClick={clearFilters} size="small">
                  Clear Filters
                </Button>
              </Box>
            </Box>
          )}
        </Paper>

        {/* Transaction List */}
        {isLoading ? (
          <Box textAlign="center" py={4}>
            <Typography>Loading transactions...</Typography>
          </Box>
        ) : error ? (
          <Alert severity="error" sx={{ mb: 3 }}>
            Failed to load transactions. Please try again.
          </Alert>
        ) : filteredTransactions.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Receipt sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No transactions found
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Try adjusting your search or filter criteria
            </Typography>
          </Paper>
        ) : (
          <Box>
            {filteredTransactions.map(renderTransactionItem)}
            
            {/* Pagination */}
            <Box display="flex" justifyContent="center" mt={3}>
              <Pagination
                count={transactions?.totalPages || 1}
                page={currentPage}
                onChange={(event, page) => setCurrentPage(page)}
                color="primary"
              />
            </Box>
          </Box>
        )}

        {/* Transaction Details Dialog */}
        <Dialog
          open={!!selectedTransaction}
          onClose={() => setSelectedTransaction(null)}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle>
            <Box display="flex" alignItems="center" justifyContent="space-between">
              <Typography variant="h6">Transaction Details</Typography>
              <IconButton onClick={() => setSelectedTransaction(null)}>
                <Close />
              </IconButton>
            </Box>
          </DialogTitle>
          <DialogContent>
            {selectedTransaction && (
              <Box>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Transaction ID
                    </Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {selectedTransaction.id}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Status
                    </Typography>
                    <Chip
                      label={selectedTransaction.status}
                      color={getStatusColor(selectedTransaction.status) as any}
                      size="small"
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Type
                    </Typography>
                    <Typography variant="body2" sx={{ textTransform: 'capitalize' }}>
                      {selectedTransaction.type}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Amount
                    </Typography>
                    <Typography variant="body2" fontWeight="medium">
                      ${selectedTransaction.amount.toFixed(2)} {selectedTransaction.currency}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Fee
                    </Typography>
                    <Typography variant="body2">
                      ${selectedTransaction.fee.toFixed(2)}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Created
                    </Typography>
                    <Typography variant="body2">
                      {format(new Date(selectedTransaction.createdAt), 'MMM dd, yyyy HH:mm')}
                    </Typography>
                  </Grid>
                  {selectedTransaction.completedAt && (
                    <Grid item xs={6}>
                      <Typography variant="subtitle2" color="text.secondary">
                        Completed
                      </Typography>
                      <Typography variant="body2">
                        {format(new Date(selectedTransaction.completedAt), 'MMM dd, yyyy HH:mm')}
                      </Typography>
                    </Grid>
                  )}
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Description
                    </Typography>
                    <Typography variant="body2">
                      {selectedTransaction.description}
                    </Typography>
                  </Grid>
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Counterparty
                    </Typography>
                    <Box display="flex" alignItems="center" mt={1}>
                      <Avatar sx={{ mr: 1, width: 32, height: 32 }}>
                        {selectedTransaction.counterparty.name.charAt(0)}
                      </Avatar>
                      <Box>
                        <Typography variant="body2" fontWeight="medium">
                          {selectedTransaction.counterparty.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {selectedTransaction.counterparty.email}
                        </Typography>
                      </Box>
                    </Box>
                  </Grid>
                  {selectedTransaction.failureReason && (
                    <Grid item xs={12}>
                      <Typography variant="subtitle2" color="text.secondary">
                        Failure Reason
                      </Typography>
                      <Typography variant="body2" color="error.main">
                        {selectedTransaction.failureReason}
                      </Typography>
                    </Grid>
                  )}
                </Grid>
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setSelectedTransaction(null)}>
              Close
            </Button>
            <Button variant="contained" startIcon={<Download />}>
              Download Receipt
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </LocalizationProvider>
  );
};

export default PaymentHistory;