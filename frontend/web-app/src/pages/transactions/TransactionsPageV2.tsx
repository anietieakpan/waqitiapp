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
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import GetAppIcon from '@mui/icons-material/GetApp';
import VisibilityIcon from '@mui/icons-material/Visibility';
import ReceiptIcon from '@mui/icons-material/Receipt';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { fetchTransactions } from '../../store/slices/paymentSlice';
import { formatCurrency, formatDate } from '../../utils/helpers';

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
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const TransactionsPageV2: React.FC = () => {
  const dispatch = useAppDispatch();
  const { transactions, transactionsLoading } = useAppSelector((state) => state.payment);
  const { activeWallet } = useAppSelector((state) => state.wallet);
  const { user } = useAppSelector((state) => state.auth);

  const [tabValue, setTabValue] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTransaction, setSelectedTransaction] = useState<any>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [filters, setFilters] = useState({
    type: '',
    status: '',
    startDate: null as Date | null,
    endDate: null as Date | null,
  });

  useEffect(() => {
    dispatch(fetchTransactions({
      page: page + 1,
      limit: rowsPerPage,
      search: searchQuery,
      ...filters,
    }));
  }, [dispatch, page, rowsPerPage, searchQuery, filters]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
    setPage(0);
    
    // Update filters based on tab
    switch (newValue) {
      case 0: // All
        setFilters({ ...filters, type: '', status: '' });
        break;
      case 1: // Sent
        setFilters({ ...filters, type: 'send', status: '' });
        break;
      case 2: // Received
        setFilters({ ...filters, type: 'receive', status: '' });
        break;
      case 3: // Pending
        setFilters({ ...filters, type: '', status: 'pending' });
        break;
    }
  };

  const handlePageChange = (event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleViewDetails = (transaction: any) => {
    setSelectedTransaction(transaction);
    setDetailsOpen(true);
  };

  const getStatusColor = (status: string) => {
    switch (status?.toLowerCase()) {
      case 'completed':
        return 'success';
      case 'pending':
        return 'warning';
      case 'failed':
        return 'error';
      case 'cancelled':
        return 'default';
      default:
        return 'default';
    }
  };

  const getTransactionIcon = (transaction: any) => {
    // Determine if money is coming in or going out
    if (transaction.toUser?.id === user?.id) {
      return <ArrowDownward color="success" />;
    } else {
      return <ArrowUpward color="error" />;
    }
  };

  const stats = {
    totalSent: transactions.filter(t => t.fromUser?.id === user?.id).reduce((sum, t) => sum + t.amount, 0),
    totalReceived: transactions.filter(t => t.toUser?.id === user?.id).reduce((sum, t) => sum + t.amount, 0),
    pending: transactions.filter(t => t.status === 'pending').length,
    completed: transactions.filter(t => t.status === 'completed').length,
  };

  const exportTransactions = (format: 'csv' | 'pdf') => {
    // Implementation for export
    console.log(`Exporting as ${format}`);
  };

  // Mobile view for transactions
  const renderMobileTransaction = (transaction: any) => {
    const isReceived = transaction.toUser?.id === user?.id;
    const otherUser = isReceived ? transaction.fromUser : transaction.toUser;
    
    return (
      <ListItem
        key={transaction.id}
        button
        onClick={() => handleViewDetails(transaction)}
        sx={{ px: 0 }}
      >
        <ListItemAvatar>
          <Avatar src={otherUser?.avatar}>
            {otherUser?.firstName?.[0] || otherUser?.username?.[0] || '?'}
          </Avatar>
        </ListItemAvatar>
        <ListItemText
          primary={
            <Box display="flex" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle2">
                {otherUser?.firstName && otherUser?.lastName
                  ? `${otherUser.firstName} ${otherUser.lastName}`
                  : otherUser?.username || 'Unknown User'}
              </Typography>
              <Typography
                variant="subtitle2"
                fontWeight="bold"
                color={isReceived ? 'success.main' : 'text.primary'}
              >
                {isReceived ? '+' : '-'}{formatCurrency(transaction.amount)}
              </Typography>
            </Box>
          }
          secondary={
            <Box display="flex" justifyContent="space-between" alignItems="center">
              <Typography variant="caption" color="text.secondary">
                {transaction.description || 'Payment'}
              </Typography>
              <Chip
                label={transaction.status}
                size="small"
                color={getStatusColor(transaction.status) as any}
              />
            </Box>
          }
        />
      </ListItem>
    );
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Transactions
      </Typography>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={1}>
                <ArrowUpward color="error" />
                <Box>
                  <Typography color="textSecondary" variant="body2">
                    Total Sent
                  </Typography>
                  <Typography variant="h6">
                    {formatCurrency(stats.totalSent)}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={1}>
                <ArrowDownward color="success" />
                <Box>
                  <Typography color="textSecondary" variant="body2">
                    Total Received
                  </Typography>
                  <Typography variant="h6" color="success.main">
                    {formatCurrency(stats.totalReceived)}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Completed
              </Typography>
              <Typography variant="h4" color="success.main">
                {stats.completed}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Pending
              </Typography>
              <Typography variant="h4" color="warning.main">
                {stats.pending}
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
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
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

        {/* Transaction List - Mobile */}
        <Box sx={{ display: { xs: 'block', md: 'none' } }}>
          <List>
            {transactions.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((transaction, index) => (
              <React.Fragment key={transaction.id}>
                {renderMobileTransaction(transaction)}
                {index < transactions.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List>
        </Box>

        {/* Transaction Table - Desktop */}
        <Box sx={{ display: { xs: 'none', md: 'block' } }}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell>From/To</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {transactions.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((transaction) => {
                  const isReceived = transaction.toUser?.id === user?.id;
                  const otherUser = isReceived ? transaction.fromUser : transaction.toUser;
                  
                  return (
                    <TableRow key={transaction.id} hover>
                      <TableCell>
                        {formatDate(transaction.createdAt)}
                      </TableCell>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={1}>
                          {getTransactionIcon(transaction)}
                          <Typography variant="body2">
                            {transaction.description || 'Payment'}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={1}>
                          <Avatar src={otherUser?.avatar} sx={{ width: 32, height: 32 }}>
                            {otherUser?.firstName?.[0] || otherUser?.username?.[0] || '?'}
                          </Avatar>
                          <Box>
                            <Typography variant="body2">
                              {otherUser?.firstName && otherUser?.lastName
                                ? `${otherUser.firstName} ${otherUser.lastName}`
                                : otherUser?.username || 'Unknown User'}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              @{otherUser?.username || 'unknown'}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell align="right">
                        <Typography
                          color={isReceived ? 'success.main' : 'text.primary'}
                          fontWeight={isReceived ? 'bold' : 'normal'}
                        >
                          {isReceived ? '+' : '-'}{formatCurrency(transaction.amount)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={transaction.status}
                          color={getStatusColor(transaction.status) as any}
                          size="small"
                        />
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
                        <Tooltip title="Download Receipt">
                          <IconButton size="small">
                            <Receipt />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>

        {/* Pagination */}
        <TablePagination
          rowsPerPageOptions={[10, 25, 50]}
          component="div"
          count={transactions.length}
          rowsPerPage={rowsPerPage}
          page={page}
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
              <Grid item xs={12}>
                <Typography variant="h4" align="center" gutterBottom>
                  {formatCurrency(selectedTransaction.amount)}
                </Typography>
              </Grid>
              
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Status
                </Typography>
                <Chip
                  label={selectedTransaction.status}
                  color={getStatusColor(selectedTransaction.status) as any}
                />
              </Grid>
              
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Date
                </Typography>
                <Typography variant="body1">
                  {formatDate(selectedTransaction.createdAt, 'long')}
                </Typography>
              </Grid>
              
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  From
                </Typography>
                <Box display="flex" alignItems="center" gap={1}>
                  <Avatar src={selectedTransaction.fromUser?.avatar} sx={{ width: 32, height: 32 }}>
                    {selectedTransaction.fromUser?.firstName?.[0] || '?'}
                  </Avatar>
                  <Typography variant="body1">
                    {selectedTransaction.fromUser?.firstName} {selectedTransaction.fromUser?.lastName}
                  </Typography>
                </Box>
              </Grid>
              
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  To
                </Typography>
                <Box display="flex" alignItems="center" gap={1}>
                  <Avatar src={selectedTransaction.toUser?.avatar} sx={{ width: 32, height: 32 }}>
                    {selectedTransaction.toUser?.firstName?.[0] || '?'}
                  </Avatar>
                  <Typography variant="body1">
                    {selectedTransaction.toUser?.firstName} {selectedTransaction.toUser?.lastName}
                  </Typography>
                </Box>
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
                  Transaction ID
                </Typography>
                <Typography variant="body2" fontFamily="monospace">
                  {selectedTransaction.id}
                </Typography>
              </Grid>
              
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="textSecondary">
                  Fee
                </Typography>
                <Typography variant="body1">
                  {formatCurrency(selectedTransaction.fee || 0)}
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
            <Grid item xs={12}>
              <TextField
                select
                fullWidth
                label="Type"
                value={filters.type}
                onChange={(e) => setFilters({ ...filters, type: e.target.value })}
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="send">Sent</MenuItem>
                <MenuItem value="receive">Received</MenuItem>
                <MenuItem value="request">Request</MenuItem>
              </TextField>
            </Grid>
            
            <Grid item xs={12}>
              <TextField
                select
                fullWidth
                label="Status"
                value={filters.status}
                onChange={(e) => setFilters({ ...filters, status: e.target.value })}
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="pending">Pending</MenuItem>
                <MenuItem value="completed">Completed</MenuItem>
                <MenuItem value="failed">Failed</MenuItem>
                <MenuItem value="cancelled">Cancelled</MenuItem>
              </TextField>
            </Grid>
            
            <Grid item xs={6}>
              <DatePicker
                label="From Date"
                value={filters.startDate}
                onChange={(date) => setFilters({ ...filters, startDate: date })}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Grid>
            
            <Grid item xs={6}>
              <DatePicker
                label="To Date"
                value={filters.endDate}
                onChange={(date) => setFilters({ ...filters, endDate: date })}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFiltersOpen(false)}>Cancel</Button>
          <Button
            onClick={() => {
              setFilters({
                type: '',
                status: '',
                startDate: null,
                endDate: null,
              });
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

export default TransactionsPageV2;