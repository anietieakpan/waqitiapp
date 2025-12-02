import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  IconButton,
  Paper,
  Chip,
  TextField,
  InputAdornment,
  Menu,
  MenuItem,
  Divider,
  Alert,
  Skeleton,
  useTheme,
  alpha,
  LinearProgress,
  Badge,
  Tooltip,
  Collapse,
  ToggleButton,
  ToggleButtonGroup,
  useMediaQuery,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  SelectChangeEvent,
  Stack,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Checkbox,
  FormControlLabel,
  Autocomplete,
  Tab,
  Tabs,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FilterIcon from '@mui/icons-material/FilterList';
import DownloadIcon from '@mui/icons-material/Download';
import CalendarIcon from '@mui/icons-material/CalendarToday';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SendIcon from '@mui/icons-material/Send';
import ReceiveIcon from '@mui/icons-material/CallReceived';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import ReceiptIcon from '@mui/icons-material/Receipt';
import RefreshIcon from '@mui/icons-material/Refresh';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import CloseIcon from '@mui/icons-material/Close';
import GetAppIcon from '@mui/icons-material/GetApp';
import PrintIcon from '@mui/icons-material/Print';
import ShareIcon from '@mui/icons-material/Share';
import FlagIcon from '@mui/icons-material/Flag';
import HelpIcon from '@mui/icons-material/Help';
import CardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';
import AtmIcon from '@mui/icons-material/LocalAtm';
import ScheduleIcon from '@mui/icons-material/Schedule';
import TimelineIcon from '@mui/icons-material/Timeline';
import CategoryIcon from '@mui/icons-material/Category';
import LabelIcon from '@mui/icons-material/Label';
import LocationIcon from '@mui/icons-material/LocationOn';
import BusinessIcon from '@mui/icons-material/Business';
import PersonIcon from '@mui/icons-material/Person';
import BarChartIcon from '@mui/icons-material/BarChart';
import PieChartIcon from '@mui/icons-material/PieChart';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import ListIcon from '@mui/icons-material/ViewList';
import GridIcon from '@mui/icons-material/ViewModule';
import CompactIcon from '@mui/icons-material/ViewAgenda';
import UndoIcon from '@mui/icons-material/Undo';
import ExchangeIcon from '@mui/icons-material/CurrencyExchange';
import TollIcon from '@mui/icons-material/Toll';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import GasIcon from '@mui/icons-material/LocalGasStation';
import EntertainmentIcon from '@mui/icons-material/Movie';
import HealthIcon from '@mui/icons-material/LocalHospital';
import EducationIcon from '@mui/icons-material/School';
import HomeIcon from '@mui/icons-material/Home';
import TransportIcon from '@mui/icons-material/DirectionsCar';
import CopyIcon from '@mui/icons-material/ContentCopy';
import QrCodeIcon from '@mui/icons-material/QrCode';;
import { format, parseISO, startOfMonth, endOfMonth, startOfWeek, endOfWeek, isWithinInterval, sub, isSameDay } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../hooks/redux';
import { formatCurrency, formatDate, formatTime, formatTransactionId, formatRelativeDate } from '../../utils/formatters';
import { calculatePercentageChange, calculateAverage } from '../../utils/calculations';
import TransactionDetails from './TransactionDetails';
import TransactionFilters from './TransactionFilters';
import TransactionStats from './TransactionStats';
import TransactionExport from './TransactionExport';
import TransactionTimeline from './TransactionTimeline';
import TransactionReceipt from './TransactionReceipt';
import TransactionInsights from './TransactionInsights';
import BulkActions from './BulkActions';
import { Transaction, TransactionType, TransactionStatus, Currency } from '../../types/wallet';

interface TransactionHistoryProps {
  embedded?: boolean;
  limit?: number;
  onTransactionSelect?: (transaction: Transaction) => void;
}

type ViewMode = 'list' | 'timeline' | 'grid' | 'compact' | 'analytics';
type SortField = 'date' | 'amount' | 'type' | 'status';
type SortOrder = 'asc' | 'desc';

const TransactionHistory: React.FC<TransactionHistoryProps> = ({
  embedded = false,
  limit,
  onTransactionSelect,
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const isTablet = useMediaQuery(theme.breakpoints.down('md'));
  
  // Redux state
  const { transactions, isLoading, error, hasMore } = useAppSelector((state) => state.wallet);
  const { user } = useAppSelector((state) => state.auth);
  
  // View state
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [activeTab, setActiveTab] = useState(0);
  const [showFilters, setShowFilters] = useState(false);
  const [showStats, setShowStats] = useState(!embedded);
  const [showInsights, setShowInsights] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  
  // Selection state
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [selectedTransactions, setSelectedTransactions] = useState<string[]>([]);
  const [showBulkActions, setShowBulkActions] = useState(false);
  
  // Filter state
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState<'today' | 'week' | 'month' | 'quarter' | 'year' | 'all' | 'custom'>('month');
  const [customDateRange, setCustomDateRange] = useState<{ start: Date | null; end: Date | null }>({ start: null, end: null });
  const [typeFilter, setTypeFilter] = useState<TransactionType | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<TransactionStatus | 'ALL'>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [currencyFilter, setCurrencyFilter] = useState<Currency | 'ALL'>('ALL');
  const [amountRange, setAmountRange] = useState<{ min: number; max: number }>({ min: 0, max: 100000 });
  const [merchantFilter, setMerchantFilter] = useState<string>('ALL');
  const [tagFilter, setTagFilter] = useState<string[]>([]);
  
  // Sort state
  const [sortField, setSortField] = useState<SortField>('date');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
  
  // Pagination state
  const [page, setPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(20);
  
  // UI state
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [speedDialOpen, setSpeedDialOpen] = useState(false);
  
  useEffect(() => {
    loadTransactions();
  }, [dateRange, customDateRange, page, sortField, sortOrder]);

  useEffect(() => {
    if (selectedTransactions.length > 0) {
      setShowBulkActions(true);
    } else {
      setShowBulkActions(false);
    }
  }, [selectedTransactions]);

  const loadTransactions = async () => {
    try {
      setRefreshing(true);
      const dateFilters = getDateRangeFilters();
      
      await dispatch({
        type: 'wallet/loadTransactions',
        payload: {
          ...dateFilters,
          page,
          limit: itemsPerPage,
          sort: `${sortField},${sortOrder}`,
        },
      });
    } catch (error) {
      console.error('Failed to load transactions:', error);
    } finally {
      setRefreshing(false);
    }
  };

  const getDateRangeFilters = () => {
    const now = new Date();
    let start: Date;
    let end: Date = now;

    if (dateRange === 'custom' && customDateRange.start && customDateRange.end) {
      return { startDate: customDateRange.start, endDate: customDateRange.end };
    }

    switch (dateRange) {
      case 'today':
        start = startOfWeek(now);
        end = endOfWeek(now);
        break;
      case 'week':
        start = startOfWeek(now);
        end = endOfWeek(now);
        break;
      case 'month':
        start = startOfMonth(now);
        end = endOfMonth(now);
        break;
      case 'quarter':
        start = startOfMonth(sub(now, { months: 2 }));
        break;
      case 'year':
        start = sub(now, { years: 1 });
        break;
      case 'all':
      default:
        return {};
    }

    return { startDate: start, endDate: end };
  };

  // Filter and sort transactions
  const processedTransactions = useMemo(() => {
    let filtered = [...transactions];

    // Apply search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(tx => 
        tx.description.toLowerCase().includes(query) ||
        tx.reference?.toLowerCase().includes(query) ||
        tx.toUserName?.toLowerCase().includes(query) ||
        tx.fromUserName?.toLowerCase().includes(query) ||
        tx.merchantName?.toLowerCase().includes(query) ||
        formatCurrency(tx.amount, tx.currency).toLowerCase().includes(query)
      );
    }

    // Apply type filter
    if (typeFilter !== 'ALL') {
      filtered = filtered.filter(tx => tx.type === typeFilter);
    }

    // Apply status filter
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter(tx => tx.status === statusFilter);
    }

    // Apply category filter
    if (categoryFilter !== 'ALL') {
      filtered = filtered.filter(tx => tx.category === categoryFilter);
    }

    // Apply currency filter
    if (currencyFilter !== 'ALL') {
      filtered = filtered.filter(tx => tx.currency === currencyFilter);
    }

    // Apply merchant filter
    if (merchantFilter !== 'ALL') {
      filtered = filtered.filter(tx => tx.merchantName === merchantFilter);
    }

    // Apply tag filter
    if (tagFilter.length > 0) {
      filtered = filtered.filter(tx => 
        tx.tags?.some(tag => tagFilter.includes(tag))
      );
    }

    // Apply amount range filter
    filtered = filtered.filter(tx => 
      tx.amount >= amountRange.min && tx.amount <= amountRange.max
    );

    // Sort transactions
    filtered.sort((a, b) => {
      let comparison = 0;
      
      switch (sortField) {
        case 'date':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          break;
        case 'amount':
          comparison = a.amount - b.amount;
          break;
        case 'type':
          comparison = a.type.localeCompare(b.type);
          break;
        case 'status':
          comparison = a.status.localeCompare(b.status);
          break;
      }
      
      return sortOrder === 'asc' ? comparison : -comparison;
    });

    // Apply limit if specified
    if (limit && embedded) {
      filtered = filtered.slice(0, limit);
    }

    return filtered;
  }, [transactions, searchQuery, typeFilter, statusFilter, categoryFilter, currencyFilter, merchantFilter, tagFilter, amountRange, sortField, sortOrder, limit, embedded]);

  // Calculate statistics
  const statistics = useMemo(() => {
    const stats = {
      totalIncome: 0,
      totalExpenses: 0,
      netAmount: 0,
      transactionCount: processedTransactions.length,
      averageTransaction: 0,
      largestTransaction: 0,
      smallestTransaction: Infinity,
      categoryCounts: {} as Record<string, number>,
      typeCounts: {} as Record<string, number>,
      dailyVolumes: {} as Record<string, { count: number; amount: number }>,
      merchantFrequency: {} as Record<string, number>,
      currencyBreakdown: {} as Record<string, number>,
    };

    processedTransactions.forEach(tx => {
      // Income vs Expenses
      if (tx.type === TransactionType.CREDIT || tx.type === TransactionType.DEPOSIT) {
        stats.totalIncome += tx.amount;
      } else {
        stats.totalExpenses += tx.amount;
      }

      // Min/Max
      stats.largestTransaction = Math.max(stats.largestTransaction, tx.amount);
      stats.smallestTransaction = Math.min(stats.smallestTransaction, tx.amount);
      
      // Category counts
      const category = tx.category || 'Other';
      stats.categoryCounts[category] = (stats.categoryCounts[category] || 0) + 1;
      
      // Type counts
      stats.typeCounts[tx.type] = (stats.typeCounts[tx.type] || 0) + 1;
      
      // Daily volumes
      const date = format(parseISO(tx.createdAt), 'yyyy-MM-dd');
      if (!stats.dailyVolumes[date]) {
        stats.dailyVolumes[date] = { count: 0, amount: 0 };
      }
      stats.dailyVolumes[date].count++;
      stats.dailyVolumes[date].amount += tx.amount;
      
      // Merchant frequency
      if (tx.merchantName) {
        stats.merchantFrequency[tx.merchantName] = (stats.merchantFrequency[tx.merchantName] || 0) + 1;
      }
      
      // Currency breakdown
      stats.currencyBreakdown[tx.currency] = (stats.currencyBreakdown[tx.currency] || 0) + tx.amount;
    });

    stats.netAmount = stats.totalIncome - stats.totalExpenses;
    stats.averageTransaction = stats.transactionCount > 0 
      ? (stats.totalIncome + stats.totalExpenses) / stats.transactionCount 
      : 0;

    if (stats.smallestTransaction === Infinity) {
      stats.smallestTransaction = 0;
    }

    return stats;
  }, [processedTransactions]);

  const handleTransactionClick = (transaction: Transaction) => {
    if (onTransactionSelect) {
      onTransactionSelect(transaction);
    } else {
      setSelectedTransaction(transaction);
    }
  };

  const handleSelectTransaction = (transactionId: string) => {
    setSelectedTransactions(prev => {
      if (prev.includes(transactionId)) {
        return prev.filter(id => id !== transactionId);
      }
      return [...prev, transactionId];
    });
  };

  const handleSelectAll = () => {
    if (selectedTransactions.length === processedTransactions.length) {
      setSelectedTransactions([]);
    } else {
      setSelectedTransactions(processedTransactions.map(tx => tx.id));
    }
  };

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('desc');
    }
  };

  const handleBulkAction = async (action: string, data?: any) => {
    const selectedTxs = processedTransactions.filter(tx => selectedTransactions.includes(tx.id));
    
    switch (action) {
      case 'export':
        // Handle export
        break;
      case 'print':
        // Handle print
        break;
      case 'categorize':
        // Handle categorization
        break;
      case 'tag':
        // Handle tagging
        break;
      case 'report':
        // Handle reporting
        break;
    }
    
    setSelectedTransactions([]);
  };

  const getTransactionIcon = (transaction: Transaction) => {
    // Category-based icons
    if (transaction.category) {
      switch (transaction.category.toLowerCase()) {
        case 'food & dining':
          return <RestaurantIcon />;
        case 'shopping':
          return <ShoppingIcon />;
        case 'transportation':
          return <TransportIcon />;
        case 'entertainment':
          return <EntertainmentIcon />;
        case 'health & fitness':
          return <HealthIcon />;
        case 'education':
          return <EducationIcon />;
        case 'home':
          return <HomeIcon />;
        case 'gas & fuel':
          return <GasIcon />;
        case 'bills & utilities':
          return <TollIcon />;
      }
    }
    
    // Type-based icons
    switch (transaction.type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
        return <ReceiveIcon />;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
        return <SendIcon />;
      case TransactionType.TRANSFER:
        return <SwapIcon />;
      case TransactionType.FEE:
        return <TollIcon />;
      case TransactionType.REFUND:
        return <UndoIcon />;
      default:
        return <SwapIcon />;
    }
  };

  const getTransactionColor = (transaction: Transaction) => {
    switch (transaction.type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
      case TransactionType.REFUND:
        return theme.palette.success.main;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
      case TransactionType.FEE:
        return theme.palette.error.main;
      case TransactionType.TRANSFER:
        return theme.palette.info.main;
      default:
        return theme.palette.text.primary;
    }
  };

  const getStatusIcon = (status: TransactionStatus) => {
    switch (status) {
      case TransactionStatus.COMPLETED:
        return <CheckCircleIcon sx={{ fontSize: 16, color: theme.palette.success.main }} />;
      case TransactionStatus.PENDING:
      case TransactionStatus.PROCESSING:
        return <ScheduleIcon sx={{ fontSize: 16, color: theme.palette.warning.main }} />;
      case TransactionStatus.FAILED:
      case TransactionStatus.CANCELLED:
        return <ErrorIcon sx={{ fontSize: 16, color: theme.palette.error.main }} />;
      case TransactionStatus.REVERSED:
        return <UndoIcon sx={{ fontSize: 16, color: theme.palette.info.main }} />;
      default:
        return <InfoIcon sx={{ fontSize: 16 }} />;
    }
  };

  const renderHeader = () => (
    <Box sx={{ mb: 3 }}>
      <Grid container alignItems="center" justifyContent="space-between">
        <Grid item>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 1 }}>
            {embedded ? 'Recent Transactions' : 'Transaction History'}
          </Typography>
          {!embedded && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Chip
                icon={<ReceiptIcon />}
                label={`${statistics.transactionCount} transactions`}
                size="small"
              />
              <Chip
                icon={statistics.netAmount >= 0 ? <TrendingUpIcon /> : <TrendingDownIcon />}
                label={`Net: ${formatCurrency(statistics.netAmount)}`}
                color={statistics.netAmount >= 0 ? 'success' : 'error'}
                size="small"
                variant="outlined"
              />
              <Chip
                icon={<TimelineIcon />}
                label={`Avg: ${formatCurrency(statistics.averageTransaction)}`}
                size="small"
                variant="outlined"
              />
            </Box>
          )}
        </Grid>
        
        {!embedded && (
          <Grid item>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              <ToggleButtonGroup
                value={viewMode}
                exclusive
                onChange={(e, value) => value && setViewMode(value)}
                size="small"
              >
                <ToggleButton value="list">
                  <Tooltip title="List View">
                    <ListIcon />
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="timeline">
                  <Tooltip title="Timeline View">
                    <TimelineIcon />
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="grid">
                  <Tooltip title="Grid View">
                    <GridIcon />
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="compact">
                  <Tooltip title="Compact View">
                    <CompactIcon />
                  </Tooltip>
                </ToggleButton>
                <ToggleButton value="analytics">
                  <Tooltip title="Analytics View">
                    <BarChartIcon />
                  </Tooltip>
                </ToggleButton>
              </ToggleButtonGroup>
              
              <IconButton onClick={() => setShowInsights(!showInsights)}>
                <Badge badgeContent="AI" color="info" invisible={!showInsights}>
                  <ShowChartIcon />
                </Badge>
              </IconButton>
              
              <IconButton onClick={() => setShowFilters(!showFilters)}>
                <Badge badgeContent={
                  [typeFilter !== 'ALL', statusFilter !== 'ALL', categoryFilter !== 'ALL', currencyFilter !== 'ALL', merchantFilter !== 'ALL', tagFilter.length > 0].filter(Boolean).length
                } color="primary">
                  <FilterIcon />
                </Badge>
              </IconButton>
              
              <IconButton onClick={loadTransactions} disabled={refreshing}>
                <RefreshIcon className={refreshing ? 'rotating' : ''} />
              </IconButton>
              
              <IconButton onClick={(e) => setMenuAnchor(e.currentTarget)}>
                <MoreVertIcon />
              </IconButton>
            </Box>
          </Grid>
        )}
      </Grid>
      
      {refreshing && <LinearProgress sx={{ mt: 2 }} />}
    </Box>
  );

  const renderSearchAndFilters = () => (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Grid container spacing={2} alignItems="center">
        <Grid item xs={12} md={embedded ? 12 : 6}>
          <TextField
            fullWidth
            size="small"
            placeholder="Search transactions..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
          />
        </Grid>
        
        {!embedded && (
          <Grid item xs={12} md={6}>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel>Period</InputLabel>
                <Select
                  value={dateRange}
                  onChange={(e: SelectChangeEvent) => setDateRange(e.target.value as any)}
                  label="Period"
                >
                  <MenuItem value="today">Today</MenuItem>
                  <MenuItem value="week">This Week</MenuItem>
                  <MenuItem value="month">This Month</MenuItem>
                  <MenuItem value="quarter">Last 3 Months</MenuItem>
                  <MenuItem value="year">This Year</MenuItem>
                  <MenuItem value="all">All Time</MenuItem>
                  <MenuItem value="custom">Custom Range</MenuItem>
                </Select>
              </FormControl>
              
              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel>Type</InputLabel>
                <Select
                  value={typeFilter}
                  onChange={(e: SelectChangeEvent) => setTypeFilter(e.target.value as any)}
                  label="Type"
                >
                  <MenuItem value="ALL">All Types</MenuItem>
                  <MenuItem value={TransactionType.CREDIT}>Income</MenuItem>
                  <MenuItem value={TransactionType.DEBIT}>Expenses</MenuItem>
                  <MenuItem value={TransactionType.TRANSFER}>Transfers</MenuItem>
                  <MenuItem value={TransactionType.DEPOSIT}>Deposits</MenuItem>
                  <MenuItem value={TransactionType.WITHDRAWAL}>Withdrawals</MenuItem>
                </Select>
              </FormControl>
              
              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel>Status</InputLabel>
                <Select
                  value={statusFilter}
                  onChange={(e: SelectChangeEvent) => setStatusFilter(e.target.value as any)}
                  label="Status"
                >
                  <MenuItem value="ALL">All Status</MenuItem>
                  <MenuItem value={TransactionStatus.COMPLETED}>Completed</MenuItem>
                  <MenuItem value={TransactionStatus.PENDING}>Pending</MenuItem>
                  <MenuItem value={TransactionStatus.FAILED}>Failed</MenuItem>
                </Select>
              </FormControl>
            </Box>
          </Grid>
        )}
      </Grid>
      
      {!embedded && (
        <Collapse in={showFilters}>
          <Divider sx={{ my: 2 }} />
          <TransactionFilters
            filters={{
              category: categoryFilter,
              currency: currencyFilter,
              merchant: merchantFilter,
              tags: tagFilter,
              amountRange,
              dateRange: customDateRange,
            }}
            categories={Object.keys(statistics.categoryCounts)}
            merchants={Object.keys(statistics.merchantFrequency)}
            onFiltersChange={(newFilters) => {
              setCategoryFilter(newFilters.category || 'ALL');
              setCurrencyFilter(newFilters.currency || 'ALL');
              setMerchantFilter(newFilters.merchant || 'ALL');
              setTagFilter(newFilters.tags || []);
              setAmountRange(newFilters.amountRange || { min: 0, max: 100000 });
              setCustomDateRange(newFilters.dateRange || { start: null, end: null });
            }}
            onReset={() => {
              setCategoryFilter('ALL');
              setCurrencyFilter('ALL');
              setMerchantFilter('ALL');
              setTagFilter([]);
              setAmountRange({ min: 0, max: 100000 });
              setCustomDateRange({ start: null, end: null });
            }}
          />
        </Collapse>
      )}
    </Paper>
  );

  const renderListView = () => (
    <TableContainer component={Paper}>
      <Table size={isMobile ? 'small' : 'medium'}>
        <TableHead>
          <TableRow>
            {!embedded && (
              <TableCell padding="checkbox">
                <Checkbox
                  indeterminate={selectedTransactions.length > 0 && selectedTransactions.length < processedTransactions.length}
                  checked={processedTransactions.length > 0 && selectedTransactions.length === processedTransactions.length}
                  onChange={handleSelectAll}
                />
              </TableCell>
            )}
            <TableCell>
              <TableSortLabel
                active={sortField === 'date'}
                direction={sortField === 'date' ? sortOrder : 'asc'}
                onClick={() => handleSort('date')}
              >
                Transaction
              </TableSortLabel>
            </TableCell>
            {!isMobile && (
              <>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'type'}
                    direction={sortField === 'type' ? sortOrder : 'asc'}
                    onClick={() => handleSort('type')}
                  >
                    Type
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'status'}
                    direction={sortField === 'status' ? sortOrder : 'asc'}
                    onClick={() => handleSort('status')}
                  >
                    Status
                  </TableSortLabel>
                </TableCell>
              </>
            )}
            <TableCell align="right">
              <TableSortLabel
                active={sortField === 'amount'}
                direction={sortField === 'amount' ? sortOrder : 'asc'}
                onClick={() => handleSort('amount')}
              >
                Amount
              </TableSortLabel>
            </TableCell>
            {!embedded && <TableCell align="right">Actions</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {processedTransactions.map((transaction) => (
            <TableRow
              key={transaction.id}
              hover
              onClick={() => handleTransactionClick(transaction)}
              sx={{ cursor: 'pointer' }}
            >
              {!embedded && (
                <TableCell padding="checkbox" onClick={(e) => e.stopPropagation()}>
                  <Checkbox
                    checked={selectedTransactions.includes(transaction.id)}
                    onChange={() => handleSelectTransaction(transaction.id)}
                  />
                </TableCell>
              )}
              <TableCell>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Avatar
                    sx={{
                      bgcolor: alpha(getTransactionColor(transaction), 0.1),
                      color: getTransactionColor(transaction),
                      width: 40,
                      height: 40,
                    }}
                  >
                    {getTransactionIcon(transaction)}
                  </Avatar>
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 500 }}>
                      {transaction.description}
                    </Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">
                        {formatRelativeDate(new Date(transaction.createdAt))}
                      </Typography>
                      {transaction.location && (
                        <>
                          <Typography variant="caption" color="text.secondary">•</Typography>
                          <LocationIcon sx={{ fontSize: 12 }} />
                          <Typography variant="caption" color="text.secondary">
                            {transaction.location}
                          </Typography>
                        </>
                      )}
                    </Box>
                  </Box>
                </Box>
              </TableCell>
              {!isMobile && (
                <>
                  <TableCell>
                    <Chip
                      label={transaction.type.replace(/_/g, ' ')}
                      size="small"
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      {getStatusIcon(transaction.status)}
                      <Typography variant="caption">
                        {transaction.status}
                      </Typography>
                    </Box>
                  </TableCell>
                </>
              )}
              <TableCell align="right">
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    color: getTransactionColor(transaction),
                  }}
                >
                  {transaction.type === TransactionType.DEBIT || 
                   transaction.type === TransactionType.WITHDRAWAL ||
                   transaction.type === TransactionType.FEE ? '-' : '+'}
                  {formatCurrency(transaction.amount, transaction.currency)}
                </Typography>
                {transaction.fee && transaction.fee > 0 && (
                  <Typography variant="caption" color="text.secondary">
                    Fee: {formatCurrency(transaction.fee, transaction.currency)}
                  </Typography>
                )}
              </TableCell>
              {!embedded && (
                <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                  <IconButton size="small">
                    <MoreVertIcon />
                  </IconButton>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );

  const renderTimelineView = () => (
    <TransactionTimeline
      transactions={processedTransactions}
      onTransactionClick={handleTransactionClick}
    />
  );

  const renderGridView = () => (
    <Grid container spacing={2}>
      {processedTransactions.map((transaction) => (
        <Grid item xs={12} sm={6} md={4} key={transaction.id}>
          <Card
            sx={{
              cursor: 'pointer',
              height: '100%',
              '&:hover': {
                boxShadow: theme.shadows[4],
              },
            }}
            onClick={() => handleTransactionClick(transaction)}
          >
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Avatar
                  sx={{
                    bgcolor: alpha(getTransactionColor(transaction), 0.1),
                    color: getTransactionColor(transaction),
                  }}
                >
                  {getTransactionIcon(transaction)}
                </Avatar>
                {getStatusIcon(transaction.status)}
              </Box>
              
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>
                {formatCurrency(transaction.amount, transaction.currency)}
              </Typography>
              
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {transaction.description}
              </Typography>
              
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {transaction.tags?.map((tag) => (
                  <Chip
                    key={tag}
                    label={tag}
                    size="small"
                    variant="outlined"
                  />
                ))}
              </Box>
              
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                {formatDate(transaction.createdAt, 'medium')}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );

  const renderCompactView = () => (
    <List sx={{ py: 0 }}>
      {processedTransactions.map((transaction, index) => (
        <React.Fragment key={transaction.id}>
          <ListItem
            button
            onClick={() => handleTransactionClick(transaction)}
            sx={{
              py: 1.5,
              '&:hover': {
                bgcolor: alpha(theme.palette.primary.main, 0.05),
              },
            }}
          >
            <ListItemIcon>
              <Avatar
                sx={{
                  bgcolor: alpha(getTransactionColor(transaction), 0.1),
                  color: getTransactionColor(transaction),
                  width: 32,
                  height: 32,
                }}
              >
                {React.cloneElement(getTransactionIcon(transaction), { sx: { fontSize: 18 } })}
              </Avatar>
            </ListItemIcon>
            
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {transaction.description}
                  </Typography>
                  {getStatusIcon(transaction.status)}
                </Box>
              }
              secondary={
                <Typography variant="caption" color="text.secondary">
                  {formatRelativeDate(new Date(transaction.createdAt))}
                  {transaction.category && ` • ${transaction.category}`}
                </Typography>
              }
            />
            
            <ListItemSecondaryAction>
              <Typography
                variant="body2"
                sx={{
                  fontWeight: 600,
                  color: getTransactionColor(transaction),
                }}
              >
                {transaction.type === TransactionType.DEBIT || 
                 transaction.type === TransactionType.WITHDRAWAL ||
                 transaction.type === TransactionType.FEE ? '-' : '+'}
                {formatCurrency(transaction.amount, transaction.currency)}
              </Typography>
            </ListItemSecondaryAction>
          </ListItem>
          {index < processedTransactions.length - 1 && <Divider />}
        </React.Fragment>
      ))}
    </List>
  );

  const renderAnalyticsView = () => (
    <Grid container spacing={3}>
      <Grid item xs={12}>
        <TransactionStats
          statistics={statistics}
          transactions={processedTransactions}
        />
      </Grid>
      {showInsights && (
        <Grid item xs={12}>
          <TransactionInsights
            transactions={processedTransactions}
            statistics={statistics}
          />
        </Grid>
      )}
    </Grid>
  );

  const renderContent = () => {
    switch (viewMode) {
      case 'list':
        return renderListView();
      case 'timeline':
        return renderTimelineView();
      case 'grid':
        return renderGridView();
      case 'compact':
        return renderCompactView();
      case 'analytics':
        return renderAnalyticsView();
      default:
        return renderListView();
    }
  };

  if (isLoading && !refreshing) {
    return (
      <Box>
        <Skeleton variant="text" width={200} height={40} sx={{ mb: 2 }} />
        {[...Array(5)].map((_, index) => (
          <Skeleton key={index} variant="rectangular" height={80} sx={{ mb: 1, borderRadius: 1 }} />
        ))}
      </Box>
    );
  }

  if (error) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={loadTransactions}>
            Retry
          </Button>
        }
      >
        {error}
      </Alert>
    );
  }

  return (
    <Box>
      {!embedded && renderHeader()}
      
      {renderSearchAndFilters()}
      
      {processedTransactions.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <ReceiptIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" sx={{ mb: 1 }}>
            No transactions found
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Try adjusting your filters or search criteria
          </Typography>
        </Paper>
      ) : (
        <>
          {renderContent()}
          
          {!embedded && viewMode !== 'analytics' && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
              <Pagination
                count={Math.ceil(statistics.transactionCount / itemsPerPage)}
                page={page}
                onChange={(e, value) => setPage(value)}
                color="primary"
              />
            </Box>
          )}
        </>
      )}
      
      {/* Bulk Actions */}
      <BulkActions
        open={showBulkActions}
        selectedCount={selectedTransactions.length}
        onAction={handleBulkAction}
        onClose={() => setSelectedTransactions([])}
      />
      
      {/* Transaction Details Dialog */}
      <TransactionDetails
        transaction={selectedTransaction}
        open={Boolean(selectedTransaction)}
        onClose={() => setSelectedTransaction(null)}
        onPrint={() => {
          // Handle print
        }}
        onDispute={() => {
          // Handle dispute
        }}
        onRefund={() => {
          // Handle refund
        }}
      />
      
      {/* Speed Dial for mobile */}
      {isMobile && !embedded && (
        <SpeedDial
          ariaLabel="Transaction actions"
          sx={{ position: 'fixed', bottom: 16, right: 16 }}
          icon={<SpeedDialIcon />}
          open={speedDialOpen}
          onOpen={() => setSpeedDialOpen(true)}
          onClose={() => setSpeedDialOpen(false)}
        >
          <SpeedDialAction
            icon={<FilterIcon />}
            tooltipTitle="Filters"
            onClick={() => setShowFilters(!showFilters)}
          />
          <SpeedDialAction
            icon={<DownloadIcon />}
            tooltipTitle="Export"
            onClick={() => handleBulkAction('export')}
          />
          <SpeedDialAction
            icon={<BarChartIcon />}
            tooltipTitle="Analytics"
            onClick={() => setViewMode('analytics')}
          />
        </SpeedDial>
      )}
      
      {/* Action Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem onClick={() => { handleBulkAction('export'); setMenuAnchor(null); }}>
          <ListItemIcon>
            <DownloadIcon />
          </ListItemIcon>
          <ListItemText primary="Export All" />
        </MenuItem>
        <MenuItem onClick={() => { window.print(); setMenuAnchor(null); }}>
          <ListItemIcon>
            <PrintIcon />
          </ListItemIcon>
          <ListItemText primary="Print" />
        </MenuItem>
        <MenuItem onClick={() => { navigate('/transactions/statements'); setMenuAnchor(null); }}>
          <ListItemIcon>
            <ReceiptIcon />
          </ListItemIcon>
          <ListItemText primary="Generate Statement" />
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => { navigate('/support'); setMenuAnchor(null); }}>
          <ListItemIcon>
            <HelpIcon />
          </ListItemIcon>
          <ListItemText primary="Get Help" />
        </MenuItem>
      </Menu>
      
      <style jsx>{`
        @keyframes rotate {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .rotating {
          animation: rotate 1s linear infinite;
        }
      `}</style>
    </Box>
  );
};

export default TransactionHistory;