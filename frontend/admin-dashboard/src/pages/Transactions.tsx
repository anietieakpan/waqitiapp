import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  IconButton,
  Chip,
  Grid,
  Card,
  CardContent,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  Stack,
  Tooltip,
  Alert,
  LinearProgress,
  Tab,
  Tabs,
  Badge,
} from '@mui/material';
import {
  DataGrid,
  GridColDef,
  GridToolbar,
  GridValueGetterParams,
  GridRenderCellParams,
  GridSelectionModel,
} from '@mui/x-data-grid';
import {
  Search,
  FilterList,
  Download,
  Block,
  CheckCircle,
  Warning,
  Info,
  AttachMoney,
  TrendingUp,
  Timeline,
  Assessment,
  Flag,
  Visibility,
  Refresh,
  PauseCircle,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';

import { transactionService } from '../services/transactionService';
import TransactionDetails from '../components/transactions/TransactionDetails';
import TransactionFilters from '../components/transactions/TransactionFilters';
import TransactionAnalytics from '../components/transactions/TransactionAnalytics';
import FraudAnalysis from '../components/transactions/FraudAnalysis';
import BulkActions from '../components/transactions/BulkActions';
import ExportDialog from '../components/common/ExportDialog';
import { useNotification } from '../hooks/useNotification';
import { Transaction, TransactionStatus, TransactionType } from '../types/transaction';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const Transactions: React.FC = () => {
  const queryClient = useQueryClient();
  const { showNotification } = useNotification();
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [selectedTransactions, setSelectedTransactions] = useState<GridSelectionModel>([]);
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [filters, setFilters] = useState({
    search: '',
    status: 'all',
    type: 'all',
    dateFrom: null as Date | null,
    dateTo: null as Date | null,
    amountMin: '',
    amountMax: '',
    flagged: false,
  });

  // Fetch transactions
  const { data: transactionsData, isLoading, refetch } = useQuery({
    queryKey: ['transactions', filters],
    queryFn: () => transactionService.getTransactions(filters),
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  // Fetch analytics
  const { data: analyticsData } = useQuery({
    queryKey: ['transaction-analytics'],
    queryFn: () => transactionService.getAnalytics(),
  });

  // Flag transaction mutation
  const flagMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      transactionService.flagTransaction(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries(['transactions']);
      showNotification('Transaction flagged successfully', 'success');
    },
  });

  // Block transaction mutation
  const blockMutation = useMutation({
    mutationFn: (id: string) => transactionService.blockTransaction(id),
    onSuccess: () => {
      queryClient.invalidateQueries(['transactions']);
      showNotification('Transaction blocked successfully', 'warning');
    },
  });

  // Approve transaction mutation
  const approveMutation = useMutation({
    mutationFn: (id: string) => transactionService.approveTransaction(id),
    onSuccess: () => {
      queryClient.invalidateQueries(['transactions']);
      showNotification('Transaction approved successfully', 'success');
    },
  });

  const columns: GridColDef[] = [
    {
      field: 'id',
      headerName: 'Transaction ID',
      width: 150,
      renderCell: (params: GridRenderCellParams) => (
        <Tooltip title={params.value}>
          <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
            {params.value.substring(0, 8)}...
          </Typography>
        </Tooltip>
      ),
    },
    {
      field: 'timestamp',
      headerName: 'Date & Time',
      width: 180,
      valueGetter: (params: GridValueGetterParams) =>
        format(new Date(params.value), 'yyyy-MM-dd HH:mm:ss'),
    },
    {
      field: 'type',
      headerName: 'Type',
      width: 120,
      renderCell: (params: GridRenderCellParams) => (
        <Chip
          label={params.value}
          size="small"
          color={params.value === 'CREDIT' ? 'success' : 'default'}
        />
      ),
    },
    {
      field: 'amount',
      headerName: 'Amount',
      width: 150,
      renderCell: (params: GridRenderCellParams) => (
        <Typography
          variant="body2"
          sx={{
            fontWeight: 'bold',
            color: params.row.type === 'CREDIT' ? 'success.main' : 'text.primary',
          }}
        >
          ${params.value.toLocaleString()}
        </Typography>
      ),
    },
    {
      field: 'currency',
      headerName: 'Currency',
      width: 80,
    },
    {
      field: 'sender',
      headerName: 'Sender',
      width: 180,
      valueGetter: (params: GridValueGetterParams) => params.row.senderName || params.row.senderId,
    },
    {
      field: 'recipient',
      headerName: 'Recipient',
      width: 180,
      valueGetter: (params: GridValueGetterParams) => params.row.recipientName || params.row.recipientId,
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 130,
      renderCell: (params: GridRenderCellParams) => {
        const getStatusColor = (status: TransactionStatus) => {
          switch (status) {
            case 'COMPLETED':
              return 'success';
            case 'PENDING':
              return 'warning';
            case 'FAILED':
              return 'error';
            case 'BLOCKED':
              return 'error';
            default:
              return 'default';
          }
        };

        const getStatusIcon = (status: TransactionStatus) => {
          switch (status) {
            case 'COMPLETED':
              return <CheckCircle fontSize="small" />;
            case 'PENDING':
              return <PauseCircle fontSize="small" />;
            case 'FAILED':
            case 'BLOCKED':
              return <Block fontSize="small" />;
            default:
              return null;
          }
        };

        return (
          <Chip
            icon={getStatusIcon(params.value)}
            label={params.value}
            size="small"
            color={getStatusColor(params.value)}
          />
        );
      },
    },
    {
      field: 'riskScore',
      headerName: 'Risk',
      width: 100,
      renderCell: (params: GridRenderCellParams) => {
        const score = params.value || 0;
        const getColor = () => {
          if (score > 70) return 'error';
          if (score > 40) return 'warning';
          return 'success';
        };

        return (
          <Chip
            label={`${score}%`}
            size="small"
            color={getColor()}
            variant="outlined"
          />
        );
      },
    },
    {
      field: 'flags',
      headerName: 'Flags',
      width: 100,
      renderCell: (params: GridRenderCellParams) => {
        const flags = params.value || [];
        if (flags.length === 0) return null;
        
        return (
          <Badge badgeContent={flags.length} color="error">
            <Flag color="error" fontSize="small" />
          </Badge>
        );
      },
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 150,
      sortable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Stack direction="row" spacing={1}>
          <Tooltip title="View Details">
            <IconButton
              size="small"
              onClick={() => handleViewDetails(params.row)}
            >
              <Visibility fontSize="small" />
            </IconButton>
          </Tooltip>
          {params.row.status === 'PENDING' && (
            <>
              <Tooltip title="Approve">
                <IconButton
                  size="small"
                  color="success"
                  onClick={() => handleApprove(params.row.id)}
                >
                  <CheckCircle fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="Block">
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => handleBlock(params.row.id)}
                >
                  <Block fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}
          <Tooltip title="Flag">
            <IconButton
              size="small"
              color="warning"
              onClick={() => handleFlag(params.row.id)}
            >
              <Flag fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      ),
    },
  ];

  const handleViewDetails = (transaction: Transaction) => {
    setSelectedTransaction(transaction);
    setDetailsOpen(true);
  };

  const handleApprove = async (id: string) => {
    await approveMutation.mutateAsync(id);
  };

  const handleBlock = async (id: string) => {
    await blockMutation.mutateAsync(id);
  };

  const handleFlag = async (id: string) => {
    // In a real app, this would open a dialog to get the reason
    await flagMutation.mutateAsync({ id, reason: 'Suspicious activity' });
  };

  const handleExport = () => {
    setExportOpen(true);
  };

  const pendingCount = transactionsData?.transactions.filter(t => t.status === 'PENDING').length || 0;
  const flaggedCount = transactionsData?.transactions.filter(t => t.flags && t.flags.length > 0).length || 0;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4" fontWeight="bold">
          Transaction Management
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => refetch()}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<Download />}
            onClick={handleExport}
          >
            Export
          </Button>
        </Stack>
      </Box>

      {/* Analytics Summary */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Total Volume
              </Typography>
              <Typography variant="h4" component="div">
                ${analyticsData?.totalVolume.toLocaleString() || 0}
              </Typography>
              <Typography variant="body2" color="success.main">
                <TrendingUp fontSize="small" /> +12.5% from last period
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Total Transactions
              </Typography>
              <Typography variant="h4" component="div">
                {analyticsData?.totalCount.toLocaleString() || 0}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Avg: ${analyticsData?.averageAmount.toFixed(2) || 0}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Pending Review
              </Typography>
              <Typography variant="h4" component="div" color="warning.main">
                {pendingCount}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Requires attention
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Flagged
              </Typography>
              <Typography variant="h4" component="div" color="error.main">
                {flaggedCount}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                High risk transactions
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          indicatorColor="primary"
          textColor="primary"
        >
          <Tab 
            label="All Transactions" 
            icon={<Badge badgeContent={transactionsData?.total || 0} color="primary" max={9999} />}
          />
          <Tab 
            label="Pending Review" 
            icon={<Badge badgeContent={pendingCount} color="warning" />}
          />
          <Tab 
            label="Flagged" 
            icon={<Badge badgeContent={flaggedCount} color="error" />}
          />
          <Tab label="Analytics" icon={<Assessment />} />
          <Tab label="Fraud Analysis" icon={<Warning />} />
        </Tabs>
      </Paper>

      {/* Tab Panels */}
      <TabPanel value={selectedTab} index={0}>
        <Paper sx={{ p: 3 }}>
          {/* Filters */}
          <TransactionFilters filters={filters} onFiltersChange={setFilters} />
          
          {/* Data Grid */}
          <Box sx={{ height: 600, width: '100%', mt: 3 }}>
            {isLoading && <LinearProgress />}
            <DataGrid
              rows={transactionsData?.transactions || []}
              columns={columns}
              pageSize={10}
              rowsPerPageOptions={[10, 25, 50, 100]}
              checkboxSelection
              disableSelectionOnClick
              selectionModel={selectedTransactions}
              onSelectionModelChange={setSelectedTransactions}
              components={{
                Toolbar: GridToolbar,
              }}
              loading={isLoading}
            />
          </Box>

          {/* Bulk Actions */}
          {selectedTransactions.length > 0 && (
            <BulkActions
              selectedIds={selectedTransactions as string[]}
              onComplete={() => {
                setSelectedTransactions([]);
                refetch();
              }}
            />
          )}
        </Paper>
      </TabPanel>

      <TabPanel value={selectedTab} index={1}>
        <Paper sx={{ p: 3 }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            These transactions require manual review based on risk assessment or compliance rules.
          </Alert>
          <Box sx={{ height: 600, width: '100%' }}>
            <DataGrid
              rows={transactionsData?.transactions.filter(t => t.status === 'PENDING') || []}
              columns={columns}
              pageSize={10}
              rowsPerPageOptions={[10, 25, 50]}
              checkboxSelection
              disableSelectionOnClick
            />
          </Box>
        </Paper>
      </TabPanel>

      <TabPanel value={selectedTab} index={2}>
        <Paper sx={{ p: 3 }}>
          <Alert severity="warning" sx={{ mb: 2 }}>
            These transactions have been flagged for suspicious activity or policy violations.
          </Alert>
          <Box sx={{ height: 600, width: '100%' }}>
            <DataGrid
              rows={transactionsData?.transactions.filter(t => t.flags && t.flags.length > 0) || []}
              columns={columns}
              pageSize={10}
              rowsPerPageOptions={[10, 25, 50]}
              disableSelectionOnClick
            />
          </Box>
        </Paper>
      </TabPanel>

      <TabPanel value={selectedTab} index={3}>
        <TransactionAnalytics />
      </TabPanel>

      <TabPanel value={selectedTab} index={4}>
        <FraudAnalysis />
      </TabPanel>

      {/* Transaction Details Dialog */}
      {selectedTransaction && (
        <TransactionDetails
          transaction={selectedTransaction}
          open={detailsOpen}
          onClose={() => {
            setDetailsOpen(false);
            setSelectedTransaction(null);
          }}
          onUpdate={() => refetch()}
        />
      )}

      {/* Export Dialog */}
      <ExportDialog
        open={exportOpen}
        onClose={() => setExportOpen(false)}
        data={transactionsData?.transactions || []}
        filename="transactions"
        title="Export Transactions"
      />
    </Box>
  );
};

export default Transactions;