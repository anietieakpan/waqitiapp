import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Button,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Tooltip,
  Alert,
  LinearProgress,
  Tabs,
  Tab,
  Badge,
  CircularProgress,
  Snackbar
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Visibility as ViewIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  FilterList as FilterIcon,
  Assignment as AssignIcon,
  Schedule as ScheduleIcon,
  History as HistoryIcon
} from '@mui/icons-material';
import { format } from 'date-fns';
import axios from 'axios';

// Types
interface ManualReviewCase {
  id: string;
  eventId: string;
  originalTopic: string;
  errorMessage: string;
  stackTrace: string;
  eventPayload: any;
  errorType: 'TRANSIENT' | 'PERMANENT' | 'BUSINESS_RULE' | 'DATA_QUALITY' | 'SYSTEM_FAILURE';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED';
  retryCount: number;
  maxRetries: number;
  createdAt: string;
  updatedAt: string;
  assignedTo?: string;
  resolutionNotes?: string;
  resolutionAction?: string;
}

interface DashboardStats {
  totalCases: number;
  pendingCases: number;
  inReviewCases: number;
  resolvedToday: number;
  criticalCases: number;
  avgResolutionTime: number;
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

const ManualReviewDashboard: React.FC = () => {
  // State
  const [cases, setCases] = useState<ManualReviewCase[]>([]);
  const [stats, setStats] = useState<DashboardStats>({
    totalCases: 0,
    pendingCases: 0,
    inReviewCases: 0,
    resolvedToday: 0,
    criticalCases: 0,
    avgResolutionTime: 0
  });
  const [loading, setLoading] = useState(false);
  const [selectedCase, setSelectedCase] = useState<ManualReviewCase | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [resolveDialogOpen, setResolveDialogOpen] = useState(false);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [resolutionAction, setResolutionAction] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');
  const [filterPriority, setFilterPriority] = useState<string>('ALL');
  const [tabValue, setTabValue] = useState(0);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  // API Configuration
  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  // Load data
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [casesResponse, statsResponse] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/manual-review/cases`, {
          params: {
            status: filterStatus !== 'ALL' ? filterStatus : undefined,
            priority: filterPriority !== 'ALL' ? filterPriority : undefined
          }
        }),
        axios.get(`${API_BASE_URL}/admin/manual-review/stats`)
      ]);

      setCases(casesResponse.data);
      setStats(statsResponse.data);
    } catch (error) {
      console.error('Failed to load manual review data:', error);
      setSnackbar({ open: true, message: 'Failed to load data', severity: 'error' });
    } finally {
      setLoading(false);
    }
  }, [API_BASE_URL, filterStatus, filterPriority]);

  useEffect(() => {
    loadData();
    // Auto-refresh every 30 seconds
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, [loadData]);

  // Handlers
  const handleViewDetails = (caseItem: ManualReviewCase) => {
    setSelectedCase(caseItem);
    setDetailsOpen(true);
  };

  const handleAssignToMe = async (caseId: string) => {
    try {
      await axios.post(`${API_BASE_URL}/admin/manual-review/cases/${caseId}/assign`, {
        assignedTo: 'current-user' // In production, get from auth context
      });
      setSnackbar({ open: true, message: 'Case assigned successfully', severity: 'success' });
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to assign case', severity: 'error' });
    }
  };

  const handleResolve = (caseItem: ManualReviewCase) => {
    setSelectedCase(caseItem);
    setResolutionNotes('');
    setResolutionAction('REPROCESS');
    setResolveDialogOpen(true);
  };

  const handleResolveSubmit = async () => {
    if (!selectedCase || !resolutionNotes.trim()) {
      setSnackbar({ open: true, message: 'Please provide resolution notes', severity: 'error' });
      return;
    }

    try {
      await axios.post(`${API_BASE_URL}/admin/manual-review/cases/${selectedCase.id}/resolve`, {
        resolutionNotes,
        resolutionAction,
        resolvedBy: 'current-user'
      });

      setSnackbar({ open: true, message: 'Case resolved successfully', severity: 'success' });
      setResolveDialogOpen(false);
      setSelectedCase(null);
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to resolve case', severity: 'error' });
    }
  };

  const handleReject = async (caseItem: ManualReviewCase) => {
    const notes = window.prompt('Enter rejection reason:');
    if (!notes) return;

    try {
      await axios.post(`${API_BASE_URL}/admin/manual-review/cases/${caseItem.id}/reject`, {
        rejectionNotes: notes,
        rejectedBy: 'current-user'
      });

      setSnackbar({ open: true, message: 'Case rejected successfully', severity: 'success' });
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to reject case', severity: 'error' });
    }
  };

  const handleRetry = async (caseId: string) => {
    try {
      await axios.post(`${API_BASE_URL}/admin/manual-review/cases/${caseId}/retry`);
      setSnackbar({ open: true, message: 'Event requeued for retry', severity: 'success' });
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to retry event', severity: 'error' });
    }
  };

  // Helper functions
  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'CRITICAL': return 'error';
      case 'HIGH': return 'warning';
      case 'MEDIUM': return 'info';
      case 'LOW': return 'default';
      default: return 'default';
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'PENDING': return 'warning';
      case 'IN_REVIEW': return 'info';
      case 'RESOLVED': return 'success';
      case 'REJECTED': return 'error';
      default: return 'default';
    }
  };

  const getErrorTypeIcon = (errorType: string) => {
    switch (errorType) {
      case 'CRITICAL': return <ErrorIcon color="error" />;
      case 'BUSINESS_RULE': return <WarningIcon color="warning" />;
      default: return <WarningIcon />;
    }
  };

  const filteredCases = cases.filter(c => {
    if (tabValue === 0) return c.status === 'PENDING';
    if (tabValue === 1) return c.status === 'IN_REVIEW';
    if (tabValue === 2) return c.priority === 'CRITICAL';
    if (tabValue === 3) return c.status === 'RESOLVED';
    return true;
  });

  return (
    <Box sx={{ padding: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          Manual Review Dashboard - DLQ Operations
        </Typography>
        <Box>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadData}
            disabled={loading}
            sx={{ mr: 2 }}
          >
            Refresh
          </Button>
          <Tooltip title="Auto-refreshes every 30 seconds">
            <Chip icon={<ScheduleIcon />} label="Auto-refresh: ON" color="primary" variant="outlined" />
          </Tooltip>
        </Box>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Cases
              </Typography>
              <Typography variant="h4">{stats.totalCases}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Pending
              </Typography>
              <Typography variant="h4" color="warning.main">{stats.pendingCases}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                In Review
              </Typography>
              <Typography variant="h4" color="info.main">{stats.inReviewCases}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Resolved Today
              </Typography>
              <Typography variant="h4" color="success.main">{stats.resolvedToday}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card sx={{ bgcolor: 'error.light' }}>
            <CardContent>
              <Typography color="error.contrastText" gutterBottom>
                CRITICAL
              </Typography>
              <Typography variant="h4" color="error.contrastText">{stats.criticalCases}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Avg Resolution
              </Typography>
              <Typography variant="h5">{stats.avgResolutionTime}h</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Alert for critical cases */}
      {stats.criticalCases > 0 && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <strong>{stats.criticalCases} CRITICAL cases require immediate attention!</strong>
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
              <FormControl fullWidth size="small">
                <InputLabel>Status</InputLabel>
                <Select
                  value={filterStatus}
                  onChange={(e) => setFilterStatus(e.target.value)}
                  label="Status"
                >
                  <MenuItem value="ALL">All Statuses</MenuItem>
                  <MenuItem value="PENDING">Pending</MenuItem>
                  <MenuItem value="IN_REVIEW">In Review</MenuItem>
                  <MenuItem value="RESOLVED">Resolved</MenuItem>
                  <MenuItem value="REJECTED">Rejected</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Priority</InputLabel>
                <Select
                  value={filterPriority}
                  onChange={(e) => setFilterPriority(e.target.value)}
                  label="Priority"
                >
                  <MenuItem value="ALL">All Priorities</MenuItem>
                  <MenuItem value="CRITICAL">Critical</MenuItem>
                  <MenuItem value="HIGH">High</MenuItem>
                  <MenuItem value="MEDIUM">Medium</MenuItem>
                  <MenuItem value="LOW">Low</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Tabs */}
      <Card>
        <Tabs value={tabValue} onChange={(e, newValue) => setTabValue(newValue)}>
          <Tab
            label={
              <Badge badgeContent={stats.pendingCases} color="warning">
                Pending
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={stats.inReviewCases} color="info">
                In Review
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={stats.criticalCases} color="error">
                Critical
              </Badge>
            }
          />
          <Tab label="Resolved" />
          <Tab label="All" />
        </Tabs>

        {loading && <LinearProgress />}

        {/* Cases Table */}
        {[0, 1, 2, 3, 4].map((index) => (
          <TabPanel value={tabValue} index={index} key={index}>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Priority</TableCell>
                    <TableCell>Event ID</TableCell>
                    <TableCell>Topic</TableCell>
                    <TableCell>Error Type</TableCell>
                    <TableCell>Error Message</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Retries</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredCases.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center">
                        <Typography variant="body2" color="textSecondary">
                          No cases found
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredCases.map((caseItem) => (
                      <TableRow key={caseItem.id} hover>
                        <TableCell>
                          <Chip
                            label={caseItem.priority}
                            color={getPriorityColor(caseItem.priority) as any}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                            {caseItem.eventId.substring(0, 16)}...
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {caseItem.originalTopic}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={caseItem.errorType}
                            size="small"
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell>
                          <Tooltip title={caseItem.errorMessage}>
                            <Typography variant="body2" noWrap sx={{ maxWidth: 200 }}>
                              {caseItem.errorMessage}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={caseItem.status}
                            color={getStatusColor(caseItem.status) as any}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          {caseItem.retryCount} / {caseItem.maxRetries}
                        </TableCell>
                        <TableCell>
                          {format(new Date(caseItem.createdAt), 'MMM dd, HH:mm')}
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 1 }}>
                            <Tooltip title="View Details">
                              <IconButton
                                size="small"
                                onClick={() => handleViewDetails(caseItem)}
                              >
                                <ViewIcon />
                              </IconButton>
                            </Tooltip>
                            {caseItem.status === 'PENDING' && (
                              <>
                                <Tooltip title="Assign to Me">
                                  <IconButton
                                    size="small"
                                    color="primary"
                                    onClick={() => handleAssignToMe(caseItem.id)}
                                  >
                                    <AssignIcon />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="Retry">
                                  <IconButton
                                    size="small"
                                    color="info"
                                    onClick={() => handleRetry(caseItem.id)}
                                    disabled={caseItem.retryCount >= caseItem.maxRetries}
                                  >
                                    <RefreshIcon />
                                  </IconButton>
                                </Tooltip>
                              </>
                            )}
                            {caseItem.status === 'IN_REVIEW' && (
                              <>
                                <Tooltip title="Resolve">
                                  <IconButton
                                    size="small"
                                    color="success"
                                    onClick={() => handleResolve(caseItem)}
                                  >
                                    <ApproveIcon />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="Reject">
                                  <IconButton
                                    size="small"
                                    color="error"
                                    onClick={() => handleReject(caseItem)}
                                  >
                                    <RejectIcon />
                                  </IconButton>
                                </Tooltip>
                              </>
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
      <Dialog
        open={detailsOpen}
        onClose={() => setDetailsOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Case Details - {selectedCase?.eventId}
        </DialogTitle>
        <DialogContent>
          {selectedCase && (
            <Box sx={{ mt: 2 }}>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="textSecondary">Priority</Typography>
                  <Chip label={selectedCase.priority} color={getPriorityColor(selectedCase.priority) as any} />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="textSecondary">Status</Typography>
                  <Chip label={selectedCase.status} color={getStatusColor(selectedCase.status) as any} />
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Original Topic</Typography>
                  <Typography variant="body2">{selectedCase.originalTopic}</Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Error Type</Typography>
                  <Typography variant="body2">{selectedCase.errorType}</Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Error Message</Typography>
                  <Alert severity="error">
                    {selectedCase.errorMessage}
                  </Alert>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Stack Trace</Typography>
                  <Paper sx={{ p: 2, bgcolor: 'grey.100', maxHeight: 200, overflow: 'auto' }}>
                    <Typography variant="body2" component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                      {selectedCase.stackTrace}
                    </Typography>
                  </Paper>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Event Payload</Typography>
                  <Paper sx={{ p: 2, bgcolor: 'grey.100', maxHeight: 300, overflow: 'auto' }}>
                    <Typography variant="body2" component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                      {JSON.stringify(selectedCase.eventPayload, null, 2)}
                    </Typography>
                  </Paper>
                </Grid>
                {selectedCase.resolutionNotes && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="textSecondary">Resolution Notes</Typography>
                    <Typography variant="body2">{selectedCase.resolutionNotes}</Typography>
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
      <Dialog
        open={resolveDialogOpen}
        onClose={() => setResolveDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Resolve Case</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>Resolution Action</InputLabel>
              <Select
                value={resolutionAction}
                onChange={(e) => setResolutionAction(e.target.value)}
                label="Resolution Action"
              >
                <MenuItem value="REPROCESS">Reprocess Event</MenuItem>
                <MenuItem value="FIX_DATA">Fix Data & Reprocess</MenuItem>
                <MenuItem value="SKIP">Skip Event (Invalid)</MenuItem>
                <MenuItem value="ESCALATE">Escalate to Engineering</MenuItem>
              </Select>
            </FormControl>
            <TextField
              fullWidth
              multiline
              rows={4}
              label="Resolution Notes"
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              placeholder="Describe the resolution action taken..."
              required
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResolveDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleResolveSubmit}
            variant="contained"
            color="success"
            disabled={!resolutionNotes.trim()}
          >
            Resolve Case
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
  );
};

export default ManualReviewDashboard;
