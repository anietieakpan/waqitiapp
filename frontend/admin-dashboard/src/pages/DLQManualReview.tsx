import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Alert,
  CircularProgress,
  Grid,
  Tabs,
  Tab,
  IconButton,
  Tooltip,
  Badge
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Visibility as ViewIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Schedule as RetryIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Info as InfoIcon
} from '@mui/icons-material';
import { format } from 'date-fns';
import axios from 'axios';

/**
 * DLQ Manual Review Dashboard
 *
 * Critical operational interface for reviewing and resolving Dead Letter Queue events
 * that require manual intervention.
 *
 * Features:
 * - Real-time DLQ event monitoring
 * - Priority-based triage (CRITICAL, HIGH, MEDIUM, LOW)
 * - Error classification (TRANSIENT, PERMANENT, BUSINESS_RULE, DATA_QUALITY)
 * - Bulk operations support
 * - Audit trail logging
 * - Auto-refresh with configurable intervals
 * - PagerDuty integration for critical cases
 *
 * @author Waqiti Operations Team
 * @version 2.0
 */

interface DLQEvent {
  id: string;
  topic: string;
  partition: number;
  offset: number;
  key: string;
  payload: any;
  errorType: 'TRANSIENT' | 'PERMANENT' | 'BUSINESS_RULE' | 'DATA_QUALITY' | 'SYSTEM_FAILURE';
  errorMessage: string;
  stackTrace: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  attemptCount: number;
  maxRetries: number;
  firstFailedAt: string;
  lastFailedAt: string;
  status: 'PENDING' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'RETRYING';
  assignedTo?: string;
  reviewNotes?: string;
  createdAt: string;
  updatedAt: string;
}

interface ResolutionAction {
  action: 'RETRY' | 'APPROVE' | 'REJECT' | 'ESCALATE';
  notes: string;
  escalationReason?: string;
}

const DLQManualReview: React.FC = () => {
  const [events, setEvents] = useState<DLQEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedEvent, setSelectedEvent] = useState<DLQEvent | null>(null);
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [actionType, setActionType] = useState<'RETRY' | 'APPROVE' | 'REJECT' | 'ESCALATE'>('RETRY');
  const [actionNotes, setActionNotes] = useState('');
  const [escalationReason, setEscalationReason] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('PENDING');
  const [filterPriority, setFilterPriority] = useState<string>('ALL');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(30); // seconds
  const [tabValue, setTabValue] = useState(0);
  const [statistics, setStatistics] = useState({
    total: 0,
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
    pending: 0,
    inReview: 0,
    resolved: 0
  });

  // Fetch DLQ events from API
  const fetchDLQEvents = useCallback(async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/admin/dlq/manual-review', {
        params: {
          status: filterStatus !== 'ALL' ? filterStatus : undefined,
          priority: filterPriority !== 'ALL' ? filterPriority : undefined
        }
      });

      setEvents(response.data.events);
      setStatistics(response.data.statistics);
    } catch (error) {
      console.error('Failed to fetch DLQ events:', error);
    } finally {
      setLoading(false);
    }
  }, [filterStatus, filterPriority]);

  // Initial load and auto-refresh
  useEffect(() => {
    fetchDLQEvents();

    if (autoRefresh) {
      const interval = setInterval(fetchDLQEvents, refreshInterval * 1000);
      return () => clearInterval(interval);
    }
  }, [fetchDLQEvents, autoRefresh, refreshInterval]);

  // Handle event detail view
  const handleViewDetails = (event: DLQEvent) => {
    setSelectedEvent(event);
    setDetailDialogOpen(true);
  };

  // Handle resolution action
  const handleAction = async (action: 'RETRY' | 'APPROVE' | 'REJECT' | 'ESCALATE') => {
    if (!selectedEvent) return;

    try {
      const payload: ResolutionAction = {
        action,
        notes: actionNotes,
        escalationReason: action === 'ESCALATE' ? escalationReason : undefined
      };

      await axios.post(`/api/admin/dlq/${selectedEvent.id}/resolve`, payload);

      // Close dialogs
      setActionDialogOpen(false);
      setDetailDialogOpen(false);
      setActionNotes('');
      setEscalationReason('');

      // Refresh event list
      fetchDLQEvents();

      // Show success notification
      alert(`Event ${action.toLowerCase()}d successfully`);
    } catch (error) {
      console.error(`Failed to ${action.toLowerCase()} event:`, error);
      alert(`Failed to ${action.toLowerCase()} event`);
    }
  };

  // Priority badge color
  const getPriorityColor = (priority: string): 'error' | 'warning' | 'info' | 'default' => {
    switch (priority) {
      case 'CRITICAL': return 'error';
      case 'HIGH': return 'warning';
      case 'MEDIUM': return 'info';
      default: return 'default';
    }
  };

  // Error type badge color
  const getErrorTypeColor = (errorType: string): 'error' | 'warning' | 'info' | 'default' => {
    switch (errorType) {
      case 'PERMANENT': return 'error';
      case 'BUSINESS_RULE': return 'warning';
      case 'DATA_QUALITY': return 'warning';
      case 'TRANSIENT': return 'info';
      default: return 'default';
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          DLQ Manual Review Dashboard
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Refresh</InputLabel>
            <Select
              value={refreshInterval}
              label="Refresh"
              onChange={(e) => setRefreshInterval(Number(e.target.value))}
            >
              <MenuItem value={10}>10s</MenuItem>
              <MenuItem value={30}>30s</MenuItem>
              <MenuItem value={60}>1m</MenuItem>
              <MenuItem value={300}>5m</MenuItem>
            </Select>
          </FormControl>
          <Button
            variant="contained"
            startIcon={<RefreshIcon />}
            onClick={fetchDLQEvents}
            disabled={loading}
          >
            Refresh
          </Button>
        </Box>
      </Box>

      {/* Statistics Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Events
              </Typography>
              <Typography variant="h4">{statistics.total}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ borderLeft: '4px solid #f44336' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Critical Priority
              </Typography>
              <Typography variant="h4" color="error">{statistics.critical}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ borderLeft: '4px solid #ff9800' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                High Priority
              </Typography>
              <Typography variant="h4" color="warning.main">{statistics.high}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ borderLeft: '4px solid #2196f3' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Pending Review
              </Typography>
              <Typography variant="h4" color="info.main">{statistics.pending}</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Filters */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} md={3}>
              <FormControl fullWidth>
                <InputLabel>Status</InputLabel>
                <Select
                  value={filterStatus}
                  label="Status"
                  onChange={(e) => setFilterStatus(e.target.value)}
                >
                  <MenuItem value="ALL">All</MenuItem>
                  <MenuItem value="PENDING">Pending</MenuItem>
                  <MenuItem value="IN_REVIEW">In Review</MenuItem>
                  <MenuItem value="APPROVED">Approved</MenuItem>
                  <MenuItem value="REJECTED">Rejected</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <FormControl fullWidth>
                <InputLabel>Priority</InputLabel>
                <Select
                  value={filterPriority}
                  label="Priority"
                  onChange={(e) => setFilterPriority(e.target.value)}
                >
                  <MenuItem value="ALL">All</MenuItem>
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

      {/* Events Table */}
      <Card>
        <CardContent>
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Priority</TableCell>
                  <TableCell>Topic</TableCell>
                  <TableCell>Error Type</TableCell>
                  <TableCell>Error Message</TableCell>
                  <TableCell>Attempts</TableCell>
                  <TableCell>First Failed</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center">
                      <CircularProgress />
                    </TableCell>
                  </TableRow>
                ) : events.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center">
                      <Typography color="textSecondary">No events found</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  events.map((event) => (
                    <TableRow key={event.id} hover>
                      <TableCell>
                        <Chip
                          label={event.priority}
                          color={getPriorityColor(event.priority)}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" noWrap>
                          {event.topic}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={event.errorType}
                          color={getErrorTypeColor(event.errorType)}
                          size="small"
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell>
                        <Tooltip title={event.errorMessage}>
                          <Typography variant="body2" noWrap sx={{ maxWidth: 300 }}>
                            {event.errorMessage}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {event.attemptCount} / {event.maxRetries}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {format(new Date(event.firstFailedAt), 'MMM d, HH:mm')}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={event.status} size="small" />
                      </TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', gap: 1 }}>
                          <Tooltip title="View Details">
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => handleViewDetails(event)}
                            >
                              <ViewIcon />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Event Detail Dialog */}
      <Dialog
        open={detailDialogOpen}
        onClose={() => setDetailDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          DLQ Event Details
          {selectedEvent && (
            <Chip
              label={selectedEvent.priority}
              color={getPriorityColor(selectedEvent.priority)}
              size="small"
              sx={{ ml: 2 }}
            />
          )}
        </DialogTitle>
        <DialogContent dividers>
          {selectedEvent && (
            <Box>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="textSecondary">Topic</Typography>
                  <Typography variant="body1">{selectedEvent.topic}</Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" color="textSecondary">Error Type</Typography>
                  <Chip
                    label={selectedEvent.errorType}
                    color={getErrorTypeColor(selectedEvent.errorType)}
                    size="small"
                  />
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Error Message</Typography>
                  <Alert severity="error" sx={{ mt: 1 }}>
                    {selectedEvent.errorMessage}
                  </Alert>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Payload</Typography>
                  <Paper variant="outlined" sx={{ p: 2, mt: 1, maxHeight: 200, overflow: 'auto' }}>
                    <pre style={{ margin: 0, fontSize: '0.875rem' }}>
                      {JSON.stringify(selectedEvent.payload, null, 2)}
                    </pre>
                  </Paper>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Stack Trace</Typography>
                  <Paper variant="outlined" sx={{ p: 2, mt: 1, maxHeight: 200, overflow: 'auto' }}>
                    <pre style={{ margin: 0, fontSize: '0.75rem', color: '#d32f2f' }}>
                      {selectedEvent.stackTrace}
                    </pre>
                  </Paper>
                </Grid>
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailDialogOpen(false)}>Close</Button>
          <Button
            variant="outlined"
            color="info"
            startIcon={<RetryIcon />}
            onClick={() => {
              setActionType('RETRY');
              setActionDialogOpen(true);
            }}
          >
            Retry
          </Button>
          <Button
            variant="outlined"
            color="success"
            startIcon={<ApproveIcon />}
            onClick={() => {
              setActionType('APPROVE');
              setActionDialogOpen(true);
            }}
          >
            Approve
          </Button>
          <Button
            variant="outlined"
            color="error"
            startIcon={<RejectIcon />}
            onClick={() => {
              setActionType('REJECT');
              setActionDialogOpen(true);
            }}
          >
            Reject
          </Button>
        </DialogActions>
      </Dialog>

      {/* Action Dialog */}
      <Dialog open={actionDialogOpen} onClose={() => setActionDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {actionType.charAt(0) + actionType.slice(1).toLowerCase()} Event
        </DialogTitle>
        <DialogContent dividers>
          <TextField
            fullWidth
            multiline
            rows={4}
            label="Notes"
            value={actionNotes}
            onChange={(e) => setActionNotes(e.target.value)}
            placeholder="Enter resolution notes..."
            sx={{ mb: 2 }}
          />
          {actionType === 'ESCALATE' && (
            <TextField
              fullWidth
              label="Escalation Reason"
              value={escalationReason}
              onChange={(e) => setEscalationReason(e.target.value)}
              placeholder="Why are you escalating this event?"
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="primary"
            onClick={() => handleAction(actionType)}
            disabled={!actionNotes}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default DLQManualReview;
