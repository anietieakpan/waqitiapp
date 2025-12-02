import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
  IconButton,
  Tooltip,
  CircularProgress,
  Snackbar,
  LinearProgress,
  Checkbox
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  CheckCircle as MatchedIcon,
  Warning as UnmatchedIcon,
  Error as ErrorIcon,
  Download as DownloadIcon,
  Upload as UploadIcon,
  Visibility as ViewIcon,
  Link as LinkIcon,
  AutorenewIcon,
  FilterList as FilterIcon
} from '@mui/icons-material';
import { format } from 'date-fns';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import axios from 'axios';

interface ReconciliationItem {
  id: string;
  paymentId: string;
  providerTransactionId: string;
  provider: string;
  amount: number;
  currency: string;
  internalStatus: string;
  providerStatus: string;
  matchStatus: 'MATCHED' | 'UNMATCHED' | 'DISCREPANCY' | 'PENDING';
  discrepancyType?: string;
  discrepancyAmount?: number;
  transactionDate: string;
  reconciliationDate?: string;
  notes?: string;
}

interface ReconciliationStats {
  totalTransactions: number;
  matchedTransactions: number;
  unmatchedTransactions: number;
  discrepancies: number;
  totalAmount: number;
  matchedAmount: number;
  discrepancyAmount: number;
  matchRate: number;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => {
  return (
    <div hidden={value !== index} style={{ padding: '24px 0' }}>
      {value === index && children}
    </div>
  );
};

const PaymentReconciliationDashboard: React.FC = () => {
  const [items, setItems] = useState<ReconciliationItem[]>([]);
  const [stats, setStats] = useState<ReconciliationStats>({
    totalTransactions: 0,
    matchedTransactions: 0,
    unmatchedTransactions: 0,
    discrepancies: 0,
    totalAmount: 0,
    matchedAmount: 0,
    discrepancyAmount: 0,
    matchRate: 0
  });
  const [loading, setLoading] = useState(false);
  const [selectedItems, setSelectedItems] = useState<string[]>([]);
  const [selectedItem, setSelectedItem] = useState<ReconciliationItem | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [resolveDialogOpen, setResolveDialogOpen] = useState(false);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [filterProvider, setFilterProvider] = useState<string>('ALL');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [startDate, setStartDate] = useState<Date | null>(new Date(new Date().setDate(new Date().getDate() - 7)));
  const [endDate, setEndDate] = useState<Date | null>(new Date());
  const [tabValue, setTabValue] = useState(0);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  useEffect(() => {
    loadReconciliationData();
  }, [filterProvider, filterStatus, startDate, endDate]);

  const loadReconciliationData = async () => {
    setLoading(true);
    try {
      const [itemsRes, statsRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/reconciliation/items`, {
          params: {
            provider: filterProvider !== 'ALL' ? filterProvider : undefined,
            status: filterStatus !== 'ALL' ? filterStatus : undefined,
            startDate: startDate?.toISOString(),
            endDate: endDate?.toISOString()
          }
        }),
        axios.get(`${API_BASE_URL}/admin/reconciliation/stats`, {
          params: {
            startDate: startDate?.toISOString(),
            endDate: endDate?.toISOString()
          }
        })
      ]);

      setItems(itemsRes.data);
      setStats(statsRes.data);
    } catch (error) {
      console.error('Failed to load reconciliation data:', error);
      setSnackbar({ open: true, message: 'Failed to load data', severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleRunReconciliation = async () => {
    setLoading(true);
    try {
      await axios.post(`${API_BASE_URL}/admin/reconciliation/run`, {
        startDate: startDate?.toISOString(),
        endDate: endDate?.toISOString()
      });

      setSnackbar({ open: true, message: 'Reconciliation started successfully', severity: 'success' });
      setTimeout(() => loadReconciliationData(), 5000); // Refresh after 5 seconds
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to start reconciliation', severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleResolveDiscrepancy = async () => {
    if (!selectedItem || !resolutionNotes.trim()) {
      setSnackbar({ open: true, message: 'Please provide resolution notes', severity: 'error' });
      return;
    }

    try {
      await axios.post(`${API_BASE_URL}/admin/reconciliation/items/${selectedItem.id}/resolve`, {
        resolutionNotes,
        resolvedBy: 'current-admin'
      });

      setSnackbar({ open: true, message: 'Discrepancy resolved successfully', severity: 'success' });
      setResolveDialogOpen(false);
      setResolutionNotes('');
      loadReconciliationData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to resolve discrepancy', severity: 'error' });
    }
  };

  const handleExportReport = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/admin/reconciliation/export`, {
        params: {
          startDate: startDate?.toISOString(),
          endDate: endDate?.toISOString(),
          format: 'csv'
        },
        responseType: 'blob'
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `reconciliation_${format(new Date(), 'yyyy-MM-dd')}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();

      setSnackbar({ open: true, message: 'Report exported successfully', severity: 'success' });
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to export report', severity: 'error' });
    }
  };

  const handleBulkMatch = async () => {
    if (selectedItems.length === 0) {
      setSnackbar({ open: true, message: 'Please select items to match', severity: 'error' });
      return;
    }

    try {
      await axios.post(`${API_BASE_URL}/admin/reconciliation/bulk-match`, {
        itemIds: selectedItems
      });

      setSnackbar({ open: true, message: `${selectedItems.length} items matched successfully`, severity: 'success' });
      setSelectedItems([]);
      loadReconciliationData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to match items', severity: 'error' });
    }
  };

  const getMatchStatusColor = (status: string) => {
    switch (status) {
      case 'MATCHED': return 'success';
      case 'UNMATCHED': return 'warning';
      case 'DISCREPANCY': return 'error';
      case 'PENDING': return 'info';
      default: return 'default';
    }
  };

  const handleSelectItem = (itemId: string) => {
    setSelectedItems(prev =>
      prev.includes(itemId) ? prev.filter(id => id !== itemId) : [...prev, itemId]
    );
  };

  const filteredItems = items.filter(item => {
    if (tabValue === 0) return item.matchStatus === 'UNMATCHED';
    if (tabValue === 1) return item.matchStatus === 'DISCREPANCY';
    if (tabValue === 2) return item.matchStatus === 'MATCHED';
    return true;
  });

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Box sx={{ p: 3 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h4" component="h1">
            Payment Reconciliation Dashboard
          </Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              variant="outlined"
              startIcon={<UploadIcon />}
              onClick={() => {/* Import provider data */}}
            >
              Import
            </Button>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={handleExportReport}
            >
              Export
            </Button>
            <Button
              variant="contained"
              startIcon={<AutorenewIcon />}
              onClick={handleRunReconciliation}
              disabled={loading}
            >
              Run Reconciliation
            </Button>
          </Box>
        </Box>

        {/* Stats Cards */}
        <Grid container spacing={3} sx={{ mb: 3 }}>
          <Grid item xs={12} md={3}>
            <Card>
              <CardContent>
                <Typography color="textSecondary" gutterBottom>
                  Total Transactions
                </Typography>
                <Typography variant="h4">{stats.totalTransactions.toLocaleString()}</Typography>
                <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                  ${stats.totalAmount.toLocaleString()}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={3}>
            <Card>
              <CardContent>
                <Typography color="textSecondary" gutterBottom>
                  Matched
                </Typography>
                <Typography variant="h4" color="success.main">
                  {stats.matchedTransactions.toLocaleString()}
                </Typography>
                <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                  ${stats.matchedAmount.toLocaleString()}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={3}>
            <Card>
              <CardContent>
                <Typography color="textSecondary" gutterBottom>
                  Unmatched
                </Typography>
                <Typography variant="h4" color="warning.main">
                  {stats.unmatchedTransactions.toLocaleString()}
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={100 - stats.matchRate}
                  color="warning"
                  sx={{ mt: 1 }}
                />
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={3}>
            <Card sx={{ bgcolor: stats.discrepancies > 0 ? 'error.light' : 'inherit' }}>
              <CardContent>
                <Typography color={stats.discrepancies > 0 ? 'error.contrastText' : 'textSecondary'} gutterBottom>
                  Discrepancies
                </Typography>
                <Typography variant="h4" color={stats.discrepancies > 0 ? 'error.contrastText' : 'error.main'}>
                  {stats.discrepancies.toLocaleString()}
                </Typography>
                <Typography variant="body2" color={stats.discrepancies > 0 ? 'error.contrastText' : 'textSecondary'} sx={{ mt: 1 }}>
                  ${stats.discrepancyAmount.toLocaleString()}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Match Rate */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
              <Typography variant="h6">Overall Match Rate</Typography>
              <Typography variant="h6" color={stats.matchRate >= 95 ? 'success.main' : 'warning.main'}>
                {stats.matchRate.toFixed(1)}%
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={stats.matchRate}
              color={stats.matchRate >= 95 ? 'success' : 'warning'}
              sx={{ height: 10, borderRadius: 5 }}
            />
          </CardContent>
        </Card>

        {/* Alerts */}
        {stats.discrepancies > 0 && (
          <Alert severity="error" sx={{ mb: 3 }}>
            <strong>{stats.discrepancies} discrepancies detected</strong> - Immediate review required
          </Alert>
        )}

        {/* Filters */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Grid container spacing={2} alignItems="center">
              <Grid item>
                <FilterIcon />
              </Grid>
              <Grid item xs={12} sm={3}>
                <DatePicker
                  label="Start Date"
                  value={startDate}
                  onChange={(date) => setStartDate(date)}
                  slotProps={{ textField: { size: 'small', fullWidth: true } }}
                />
              </Grid>
              <Grid item xs={12} sm={3}>
                <DatePicker
                  label="End Date"
                  value={endDate}
                  onChange={(date) => setEndDate(date)}
                  slotProps={{ textField: { size: 'small', fullWidth: true } }}
                />
              </Grid>
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Provider</InputLabel>
                  <Select
                    value={filterProvider}
                    onChange={(e) => setFilterProvider(e.target.value)}
                    label="Provider"
                  >
                    <MenuItem value="ALL">All Providers</MenuItem>
                    <MenuItem value="STRIPE">Stripe</MenuItem>
                    <MenuItem value="PAYPAL">PayPal</MenuItem>
                    <MenuItem value="SQUARE">Square</MenuItem>
                    <MenuItem value="ADYEN">Adyen</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Status</InputLabel>
                  <Select
                    value={filterStatus}
                    onChange={(e) => setFilterStatus(e.target.value)}
                    label="Status"
                  >
                    <MenuItem value="ALL">All Statuses</MenuItem>
                    <MenuItem value="MATCHED">Matched</MenuItem>
                    <MenuItem value="UNMATCHED">Unmatched</MenuItem>
                    <MenuItem value="DISCREPANCY">Discrepancy</MenuItem>
                    <MenuItem value="PENDING">Pending</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} sm={2}>
                <Button
                  fullWidth
                  variant="outlined"
                  startIcon={<RefreshIcon />}
                  onClick={loadReconciliationData}
                  disabled={loading}
                >
                  Refresh
                </Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* Bulk Actions */}
        {selectedItems.length > 0 && (
          <Alert severity="info" sx={{ mb: 3 }}>
            {selectedItems.length} items selected
            <Button
              size="small"
              color="primary"
              onClick={handleBulkMatch}
              sx={{ ml: 2 }}
            >
              Match Selected
            </Button>
            <Button
              size="small"
              onClick={() => setSelectedItems([])}
              sx={{ ml: 1 }}
            >
              Clear Selection
            </Button>
          </Alert>
        )}

        {/* Tabs */}
        <Card>
          <Tabs value={tabValue} onChange={(e, newValue) => setTabValue(newValue)}>
            <Tab
              label={`Unmatched (${stats.unmatchedTransactions})`}
              icon={<UnmatchedIcon />}
              iconPosition="start"
            />
            <Tab
              label={`Discrepancies (${stats.discrepancies})`}
              icon={<ErrorIcon />}
              iconPosition="start"
            />
            <Tab
              label={`Matched (${stats.matchedTransactions})`}
              icon={<MatchedIcon />}
              iconPosition="start"
            />
            <Tab label="All" />
          </Tabs>

          {loading && <LinearProgress />}

          {/* Items Table */}
          {[0, 1, 2, 3].map((index) => (
            <TabPanel value={tabValue} index={index} key={index}>
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox">
                        <Checkbox
                          indeterminate={selectedItems.length > 0 && selectedItems.length < filteredItems.length}
                          checked={filteredItems.length > 0 && selectedItems.length === filteredItems.length}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedItems(filteredItems.map(item => item.id));
                            } else {
                              setSelectedItems([]);
                            }
                          }}
                        />
                      </TableCell>
                      <TableCell>Payment ID</TableCell>
                      <TableCell>Provider</TableCell>
                      <TableCell>Provider TXN ID</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Internal Status</TableCell>
                      <TableCell>Provider Status</TableCell>
                      <TableCell>Match Status</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell>Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredItems.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={10} align="center">
                          <Typography variant="body2" color="textSecondary">
                            No items found
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ) : (
                      filteredItems.map((item) => (
                        <TableRow key={item.id} hover>
                          <TableCell padding="checkbox">
                            <Checkbox
                              checked={selectedItems.includes(item.id)}
                              onChange={() => handleSelectItem(item.id)}
                            />
                          </TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                            {item.paymentId.substring(0, 12)}...
                          </TableCell>
                          <TableCell>
                            <Chip label={item.provider} size="small" />
                          </TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                            {item.providerTransactionId}
                          </TableCell>
                          <TableCell align="right">
                            <Typography fontWeight="bold">
                              {item.currency} {item.amount.toLocaleString()}
                            </Typography>
                            {item.discrepancyAmount && (
                              <Typography variant="caption" color="error">
                                Δ {item.currency} {item.discrepancyAmount.toLocaleString()}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell>
                            <Chip label={item.internalStatus} size="small" variant="outlined" />
                          </TableCell>
                          <TableCell>
                            <Chip label={item.providerStatus} size="small" variant="outlined" />
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={item.matchStatus}
                              color={getMatchStatusColor(item.matchStatus) as any}
                              size="small"
                            />
                            {item.discrepancyType && (
                              <Typography variant="caption" display="block" color="error">
                                {item.discrepancyType}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell>{format(new Date(item.transactionDate), 'MMM dd, yyyy')}</TableCell>
                          <TableCell>
                            <Box sx={{ display: 'flex', gap: 1 }}>
                              <Tooltip title="View Details">
                                <IconButton
                                  size="small"
                                  onClick={() => { setSelectedItem(item); setDetailsOpen(true); }}
                                >
                                  <ViewIcon />
                                </IconButton>
                              </Tooltip>
                              {item.matchStatus === 'DISCREPANCY' && (
                                <Tooltip title="Resolve">
                                  <IconButton
                                    size="small"
                                    color="primary"
                                    onClick={() => { setSelectedItem(item); setResolveDialogOpen(true); }}
                                  >
                                    <LinkIcon />
                                  </IconButton>
                                </Tooltip>
                              )}
                            </Box>
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </TabPanel>
          ))}
        </Card>

        {/* Details Dialog */}
        <Dialog open={detailsOpen} onClose={() => setDetailsOpen(false)} maxWidth="md" fullWidth>
          <DialogTitle>Reconciliation Details</DialogTitle>
          <DialogContent>
            {selectedItem && (
              <Box sx={{ mt: 2 }}>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="textSecondary">Payment ID</Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{selectedItem.paymentId}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="textSecondary">Provider Transaction ID</Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{selectedItem.providerTransactionId}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="textSecondary">Amount</Typography>
                    <Typography variant="body2">{selectedItem.currency} {selectedItem.amount.toLocaleString()}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="textSecondary">Match Status</Typography>
                    <Chip label={selectedItem.matchStatus} color={getMatchStatusColor(selectedItem.matchStatus) as any} size="small" />
                  </Grid>
                  {selectedItem.discrepancyType && (
                    <>
                      <Grid item xs={6}>
                        <Typography variant="subtitle2" color="textSecondary">Discrepancy Type</Typography>
                        <Typography variant="body2" color="error">{selectedItem.discrepancyType}</Typography>
                      </Grid>
                      <Grid item xs={6}>
                        <Typography variant="subtitle2" color="textSecondary">Discrepancy Amount</Typography>
                        <Typography variant="body2" color="error">
                          {selectedItem.currency} {selectedItem.discrepancyAmount?.toLocaleString()}
                        </Typography>
                      </Grid>
                    </>
                  )}
                  {selectedItem.notes && (
                    <Grid item xs={12}>
                      <Typography variant="subtitle2" color="textSecondary">Notes</Typography>
                      <Typography variant="body2">{selectedItem.notes}</Typography>
                    </Grid>
                  )}
                </Grid>
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDetailsOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>

        {/* Resolve Dialog */}
        <Dialog open={resolveDialogOpen} onClose={() => setResolveDialogOpen(false)} maxWidth="sm" fullWidth>
          <DialogTitle>Resolve Discrepancy</DialogTitle>
          <DialogContent>
            <TextField
              fullWidth
              multiline
              rows={4}
              label="Resolution Notes"
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              placeholder="Explain how this discrepancy was resolved..."
              sx={{ mt: 2 }}
              required
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setResolveDialogOpen(false)}>Cancel</Button>
            <Button
              onClick={handleResolveDiscrepancy}
              variant="contained"
              disabled={!resolutionNotes.trim()}
            >
              Resolve
            </Button>
          </DialogActions>
        </Dialog>

        {/* Snackbar */}
        <Snackbar
          open={snackbar.open}
          autoHideDuration={6000}
          onClose={() => setSnackbar({ ...snackbar, open: false })}
        >
          <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
            {snackbar.message}
          </Alert>
        </Snackbar>
      </Box>
    </LocalizationProvider>
  );
};

export default PaymentReconciliationDashboard;
