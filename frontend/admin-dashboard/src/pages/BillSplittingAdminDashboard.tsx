import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Button,
  IconButton,
  TextField,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Tab,
  Tabs,
  LinearProgress,
  Tooltip,
  Avatar,
  AvatarGroup,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Divider,
} from '@mui/material';
import {
  Visibility,
  Block,
  CheckCircle,
  Error,
  Group,
  Pending,
  TrendingUp,
  TrendingDown,
  Refresh,
  FilterList,
  Download,
  Cancel as CancelIcon,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { format } from 'date-fns';

/**
 * Bill Splitting Admin Dashboard
 *
 * FEATURES:
 * - Monitor all bill splitting activities
 * - Review disputed bills
 * - Manage group expenses
 * - Resolve payment conflicts
 * - Analytics and reporting
 * - Fraud detection for split bills
 *
 * ADMIN ACTIONS:
 * - Review and approve/reject bills
 * - Resolve disputes
 * - Block fraudulent groups
 * - Refund participants
 * - View detailed bill breakdowns
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

interface BillSplit {
  id: string;
  billName: string;
  totalAmount: number;
  currency: string;
  createdBy: {
    id: string;
    name: string;
    email: string;
  };
  participants: Array<{
    id: string;
    name: string;
    amount: number;
    status: 'PENDING' | 'PAID' | 'DECLINED' | 'OVERDUE';
    paidAt?: Date;
  }>;
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'DISPUTED';
  category: string;
  createdAt: Date;
  completedAt?: Date;
  disputeReason?: string;
  riskScore?: number;
}

interface BillSplitMetrics {
  totalBills: number;
  activeBills: number;
  completedBills: number;
  disputedBills: number;
  totalVolume: number;
  averageBillAmount: number;
  completionRate: number;
  disputeRate: number;
}

const BillSplittingAdminDashboard: React.FC = () => {
  const [tabValue, setTabValue] = useState(0);
  const [bills, setBills] = useState<BillSplit[]>([]);
  const [metrics, setMetrics] = useState<BillSplitMetrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [selectedBill, setSelectedBill] = useState<BillSplit | null>(null);
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [actionType, setActionType] = useState<'RESOLVE' | 'BLOCK' | 'REFUND' | null>(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [dateFrom, setDateFrom] = useState<Date | null>(null);
  const [dateTo, setDateTo] = useState<Date | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadData();
  }, [tabValue, statusFilter, dateFrom, dateTo]);

  const loadData = async () => {
    setLoading(true);
    try {
      // Mock data - replace with actual API calls
      const mockBills: BillSplit[] = [
        {
          id: 'BILL-001',
          billName: 'Team Lunch at Restaurant X',
          totalAmount: 250.00,
          currency: 'USD',
          createdBy: {
            id: 'user-001',
            name: 'John Doe',
            email: 'john@example.com',
          },
          participants: [
            {
              id: 'user-001',
              name: 'John Doe',
              amount: 50.00,
              status: 'PAID',
              paidAt: new Date('2025-10-20'),
            },
            {
              id: 'user-002',
              name: 'Jane Smith',
              amount: 50.00,
              status: 'PAID',
              paidAt: new Date('2025-10-20'),
            },
            {
              id: 'user-003',
              name: 'Bob Johnson',
              amount: 50.00,
              status: 'PENDING',
            },
            {
              id: 'user-004',
              name: 'Alice Williams',
              amount: 50.00,
              status: 'DECLINED',
            },
            {
              id: 'user-005',
              name: 'Charlie Brown',
              amount: 50.00,
              status: 'OVERDUE',
            },
          ],
          status: 'DISPUTED',
          category: 'Food & Dining',
          createdAt: new Date('2025-10-20'),
          disputeReason: 'Alice claims she was not present at the lunch',
          riskScore: 0.35,
        },
        {
          id: 'BILL-002',
          billName: 'Monthly Rent',
          totalAmount: 2000.00,
          currency: 'USD',
          createdBy: {
            id: 'user-006',
            name: 'David Lee',
            email: 'david@example.com',
          },
          participants: [
            {
              id: 'user-006',
              name: 'David Lee',
              amount: 1000.00,
              status: 'PAID',
              paidAt: new Date('2025-10-15'),
            },
            {
              id: 'user-007',
              name: 'Emma Wilson',
              amount: 1000.00,
              status: 'PAID',
              paidAt: new Date('2025-10-16'),
            },
          ],
          status: 'COMPLETED',
          category: 'Housing',
          createdAt: new Date('2025-10-15'),
          completedAt: new Date('2025-10-16'),
          riskScore: 0.05,
        },
      ];

      setBills(mockBills);

      const mockMetrics: BillSplitMetrics = {
        totalBills: 1543,
        activeBills: 387,
        completedBills: 1089,
        disputedBills: 67,
        totalVolume: 456780.50,
        averageBillAmount: 296.02,
        completionRate: 70.6,
        disputeRate: 4.3,
      };

      setMetrics(mockMetrics);
    } catch (error) {
      console.error('Failed to load bill splitting data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleViewDetails = (bill: BillSplit) => {
    setSelectedBill(bill);
    setDetailDialogOpen(true);
  };

  const handleAction = (bill: BillSplit, type: 'RESOLVE' | 'BLOCK' | 'REFUND') => {
    setSelectedBill(bill);
    setActionType(type);
    setActionDialogOpen(true);
  };

  const executeAction = async () => {
    if (!selectedBill || !actionType) return;

    setLoading(true);
    try {
      // API call to execute action
      console.log(`Executing ${actionType} on bill ${selectedBill.id}`);

      // Close dialog and refresh
      setActionDialogOpen(false);
      await loadData();
    } catch (error) {
      console.error('Action failed:', error);
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string): 'success' | 'error' | 'warning' | 'info' | 'default' => {
    switch (status) {
      case 'PAID':
      case 'COMPLETED':
        return 'success';
      case 'DECLINED':
      case 'CANCELLED':
        return 'error';
      case 'PENDING':
        return 'warning';
      case 'OVERDUE':
        return 'error';
      case 'DISPUTED':
        return 'warning';
      case 'ACTIVE':
        return 'info';
      default:
        return 'default';
    }
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Box sx={{ p: 3 }}>
        {/* Header */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="h4" gutterBottom>
            Bill Splitting Administration
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Monitor and manage all bill splitting activities across the platform
          </Typography>
        </Box>

        {/* Metrics Cards */}
        {metrics && (
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item xs={12} sm={6} md={3}>
              <Card>
                <CardContent>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Total Bills
                  </Typography>
                  <Typography variant="h4">{metrics.totalBills.toLocaleString()}</Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                    <TrendingUp color="success" fontSize="small" />
                    <Typography variant="body2" color="success.main" sx={{ ml: 0.5 }}>
                      +12.3% this month
                    </Typography>
                  </Box>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card>
                <CardContent>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Active Bills
                  </Typography>
                  <Typography variant="h4">{metrics.activeBills.toLocaleString()}</Typography>
                  <Chip
                    label={`${metrics.completionRate.toFixed(1)}% completion rate`}
                    size="small"
                    color="success"
                    sx={{ mt: 1 }}
                  />
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card>
                <CardContent>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Disputed Bills
                  </Typography>
                  <Typography variant="h4" color="warning.main">
                    {metrics.disputedBills}
                  </Typography>
                  <Chip
                    label={`${metrics.disputeRate.toFixed(1)}% dispute rate`}
                    size="small"
                    color="warning"
                    sx={{ mt: 1 }}
                  />
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card>
                <CardContent>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Total Volume
                  </Typography>
                  <Typography variant="h4">
                    ${metrics.totalVolume.toLocaleString()}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    Avg: ${metrics.averageBillAmount.toFixed(2)}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        )}

        {/* Filters */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} sm={6} md={3}>
                <TextField
                  fullWidth
                  label="Search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Bill name, user..."
                  size="small"
                />
              </Grid>
              <Grid item xs={12} sm={6} md={2}>
                <TextField
                  fullWidth
                  select
                  label="Status"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  size="small"
                >
                  <MenuItem value="ALL">All</MenuItem>
                  <MenuItem value="ACTIVE">Active</MenuItem>
                  <MenuItem value="COMPLETED">Completed</MenuItem>
                  <MenuItem value="DISPUTED">Disputed</MenuItem>
                  <MenuItem value="CANCELLED">Cancelled</MenuItem>
                </TextField>
              </Grid>
              <Grid item xs={12} sm={6} md={2}>
                <DatePicker
                  label="From Date"
                  value={dateFrom}
                  onChange={(date) => setDateFrom(date)}
                  slotProps={{ textField: { size: 'small', fullWidth: true } }}
                />
              </Grid>
              <Grid item xs={12} sm={6} md={2}>
                <DatePicker
                  label="To Date"
                  value={dateTo}
                  onChange={(date) => setDateTo(date)}
                  slotProps={{ textField: { size: 'small', fullWidth: true } }}
                />
              </Grid>
              <Grid item xs={12} sm={6} md={1}>
                <Button
                  fullWidth
                  variant="outlined"
                  startIcon={<Refresh />}
                  onClick={loadData}
                  disabled={loading}
                >
                  Refresh
                </Button>
              </Grid>
              <Grid item xs={12} sm={6} md={2}>
                <Button
                  fullWidth
                  variant="outlined"
                  startIcon={<Download />}
                  disabled={loading}
                >
                  Export
                </Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* Tabs */}
        <Card>
          <Tabs
            value={tabValue}
            onChange={(_, newValue) => setTabValue(newValue)}
            sx={{ borderBottom: 1, borderColor: 'divider' }}
          >
            <Tab label="All Bills" />
            <Tab label="Disputed" icon={<Chip label={metrics?.disputedBills || 0} size="small" color="warning" />} iconPosition="end" />
            <Tab label="Active" />
            <Tab label="Completed" />
          </Tabs>

          {loading && <LinearProgress />}

          <CardContent>
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Bill ID</TableCell>
                    <TableCell>Bill Name</TableCell>
                    <TableCell>Created By</TableCell>
                    <TableCell>Participants</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Risk Score</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell align="center">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {bills.map((bill) => (
                    <TableRow key={bill.id} hover>
                      <TableCell>
                        <Typography variant="body2" fontWeight="bold">
                          {bill.id}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{bill.billName}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{bill.createdBy.name}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {bill.createdBy.email}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <AvatarGroup max={4}>
                          {bill.participants.map((p) => (
                            <Tooltip key={p.id} title={`${p.name} - ${p.status}`}>
                              <Avatar sx={{ width: 32, height: 32 }}>
                                {p.name.charAt(0)}
                              </Avatar>
                            </Tooltip>
                          ))}
                        </AvatarGroup>
                        <Typography variant="caption" color="text.secondary">
                          {bill.participants.length} people
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2" fontWeight="bold">
                          ${bill.totalAmount.toFixed(2)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {bill.currency}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={bill.category} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={bill.status}
                          size="small"
                          color={getStatusColor(bill.status)}
                        />
                      </TableCell>
                      <TableCell>
                        {bill.riskScore !== undefined && (
                          <Chip
                            label={`${(bill.riskScore * 100).toFixed(0)}%`}
                            size="small"
                            color={bill.riskScore > 0.7 ? 'error' : bill.riskScore > 0.4 ? 'warning' : 'success'}
                          />
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption">
                          {format(bill.createdAt, 'MMM dd, yyyy')}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <IconButton size="small" onClick={() => handleViewDetails(bill)}>
                          <Visibility />
                        </IconButton>
                        {bill.status === 'DISPUTED' && (
                          <>
                            <IconButton
                              size="small"
                              color="success"
                              onClick={() => handleAction(bill, 'RESOLVE')}
                            >
                              <CheckCircle />
                            </IconButton>
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => handleAction(bill, 'BLOCK')}
                            >
                              <Block />
                            </IconButton>
                          </>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>

        {/* Detail Dialog */}
        <Dialog
          open={detailDialogOpen}
          onClose={() => setDetailDialogOpen(false)}
          maxWidth="md"
          fullWidth
        >
          <DialogTitle>Bill Details: {selectedBill?.billName}</DialogTitle>
          <DialogContent>
            {selectedBill && (
              <Box>
                <Grid container spacing={2} sx={{ mb: 3 }}>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Bill ID
                    </Typography>
                    <Typography variant="body1">{selectedBill.id}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Total Amount
                    </Typography>
                    <Typography variant="h6">
                      ${selectedBill.totalAmount.toFixed(2)} {selectedBill.currency}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Status
                    </Typography>
                    <Chip
                      label={selectedBill.status}
                      size="small"
                      color={getStatusColor(selectedBill.status)}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="body2" color="text.secondary">
                      Category
                    </Typography>
                    <Typography variant="body1">{selectedBill.category}</Typography>
                  </Grid>
                </Grid>

                {selectedBill.disputeReason && (
                  <Alert severity="warning" sx={{ mb: 2 }}>
                    <Typography variant="body2" fontWeight="bold">
                      Dispute Reason:
                    </Typography>
                    <Typography variant="body2">{selectedBill.disputeReason}</Typography>
                  </Alert>
                )}

                <Typography variant="h6" gutterBottom sx={{ mt: 2 }}>
                  Participants ({selectedBill.participants.length})
                </Typography>
                <List>
                  {selectedBill.participants.map((participant) => (
                    <React.Fragment key={participant.id}>
                      <ListItem>
                        <ListItemAvatar>
                          <Avatar>{participant.name.charAt(0)}</Avatar>
                        </ListItemAvatar>
                        <ListItemText
                          primary={participant.name}
                          secondary={`Amount: $${participant.amount.toFixed(2)} | ${participant.status}`}
                        />
                        <Chip
                          label={participant.status}
                          size="small"
                          color={getStatusColor(participant.status)}
                        />
                      </ListItem>
                      <Divider />
                    </React.Fragment>
                  ))}
                </List>
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDetailDialogOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>

        {/* Action Dialog */}
        <Dialog
          open={actionDialogOpen}
          onClose={() => setActionDialogOpen(false)}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle>
            {actionType === 'RESOLVE' && 'Resolve Dispute'}
            {actionType === 'BLOCK' && 'Block Group'}
            {actionType === 'REFUND' && 'Process Refund'}
          </DialogTitle>
          <DialogContent>
            <Alert severity="warning" sx={{ mb: 2 }}>
              This action cannot be undone. Please confirm.
            </Alert>
            <Typography variant="body2">
              Bill: {selectedBill?.billName} ({selectedBill?.id})
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setActionDialogOpen(false)} startIcon={<CancelIcon />}>
              Cancel
            </Button>
            <Button
              onClick={executeAction}
              variant="contained"
              color={actionType === 'BLOCK' ? 'error' : 'primary'}
              disabled={loading}
            >
              Confirm
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </LocalizationProvider>
  );
};

export default BillSplittingAdminDashboard;
