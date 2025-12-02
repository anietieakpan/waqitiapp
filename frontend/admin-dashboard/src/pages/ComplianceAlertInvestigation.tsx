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
  Divider,
  List,
  ListItem,
  ListItemText,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Snackbar
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Visibility as ViewIcon,
  CheckCircle as ApproveIcon,
  Block as BlockIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  FilterList as FilterIcon,
  Assignment as AssignIcon,
  Flag as FlagIcon,
  ExpandMore as ExpandMoreIcon,
  AccountBalance as BankIcon,
  CreditCard as CardIcon,
  Person as PersonIcon,
  AttachMoney as MoneyIcon,
  Public as GlobalIcon
} from '@mui/icons-material';
import { format } from 'date-fns';
import axios from 'axios';

// Types
interface ComplianceAlert {
  id: string;
  alertType: 'OFAC' | 'AML' | 'KYC' | 'SANCTIONS' | 'PEP' | 'HIGH_RISK_COUNTRY';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'INVESTIGATING' | 'APPROVED' | 'BLOCKED' | 'ESCALATED';
  userId?: string;
  userName?: string;
  transactionId?: string;
  transactionAmount?: number;
  currency?: string;
  matchScore?: number;
  matchedEntity?: string;
  matchedList?: string;
  country?: string;
  riskScore?: number;
  description: string;
  findings: string;
  createdAt: string;
  updatedAt: string;
  assignedTo?: string;
  investigationNotes?: string;
  decision?: string;
  decidedBy?: string;
  decidedAt?: string;
}

interface DashboardStats {
  totalAlerts: number;
  pendingAlerts: number;
  investigatingAlerts: number;
  criticalAlerts: number;
  blockedToday: number;
  approvedToday: number;
  avgInvestigationTime: number;
  ofacMatches: number;
  amlHighRisk: number;
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

const ComplianceAlertInvestigation: React.FC = () => {
  // State
  const [alerts, setAlerts] = useState<ComplianceAlert[]>([]);
  const [stats, setStats] = useState<DashboardStats>({
    totalAlerts: 0,
    pendingAlerts: 0,
    investigatingAlerts: 0,
    criticalAlerts: 0,
    blockedToday: 0,
    approvedToday: 0,
    avgInvestigationTime: 0,
    ofacMatches: 0,
    amlHighRisk: 0
  });
  const [loading, setLoading] = useState(false);
  const [selectedAlert, setSelectedAlert] = useState<ComplianceAlert | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [decisionDialogOpen, setDecisionDialogOpen] = useState(false);
  const [investigationNotes, setInvestigationNotes] = useState('');
  const [decision, setDecision] = useState('');
  const [filterType, setFilterType] = useState<string>('ALL');
  const [filterSeverity, setFilterSeverity] = useState<string>('ALL');
  const [tabValue, setTabValue] = useState(0);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  // API Configuration
  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  // Load data
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [alertsResponse, statsResponse] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/compliance/alerts`, {
          params: {
            type: filterType !== 'ALL' ? filterType : undefined,
            severity: filterSeverity !== 'ALL' ? filterSeverity : undefined
          }
        }),
        axios.get(`${API_BASE_URL}/admin/compliance/stats`)
      ]);

      setAlerts(alertsResponse.data);
      setStats(statsResponse.data);
    } catch (error) {
      console.error('Failed to load compliance alerts:', error);
      setSnackbar({ open: true, message: 'Failed to load data', severity: 'error' });
    } finally {
      setLoading(false);
    }
  }, [API_BASE_URL, filterType, filterSeverity]);

  useEffect(() => {
    loadData();
    // Auto-refresh every 30 seconds
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, [loadData]);

  // Handlers
  const handleViewDetails = (alert: ComplianceAlert) => {
    setSelectedAlert(alert);
    setDetailsOpen(true);
  };

  const handleAssignToMe = async (alertId: string) => {
    try {
      await axios.post(`${API_BASE_URL}/admin/compliance/alerts/${alertId}/assign`, {
        assignedTo: 'current-user'
      });
      setSnackbar({ open: true, message: 'Alert assigned successfully', severity: 'success' });
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to assign alert', severity: 'error' });
    }
  };

  const handleStartInvestigation = async (alertId: string) => {
    try {
      await axios.post(`${API_BASE_URL}/admin/compliance/alerts/${alertId}/start-investigation`);
      setSnackbar({ open: true, message: 'Investigation started', severity: 'success' });
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to start investigation', severity: 'error' });
    }
  };

  const handleMakeDecision = (alert: ComplianceAlert) => {
    setSelectedAlert(alert);
    setInvestigationNotes('');
    setDecision('APPROVE');
    setDecisionDialogOpen(true);
  };

  const handleDecisionSubmit = async () => {
    if (!selectedAlert || !investigationNotes.trim()) {
      setSnackbar({ open: true, message: 'Please provide investigation notes', severity: 'error' });
      return;
    }

    try {
      await axios.post(`${API_BASE_URL}/admin/compliance/alerts/${selectedAlert.id}/decide`, {
        decision,
        investigationNotes,
        decidedBy: 'current-user'
      });

      setSnackbar({ open: true, message: 'Decision recorded successfully', severity: 'success' });
      setDecisionDialogOpen(false);
      setSelectedAlert(null);
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to record decision', severity: 'error' });
    }
  };

  const handleEscalate = async (alertId: string) => {
    const notes = window.prompt('Enter escalation reason:');
    if (!notes) return;

    try {
      await axios.post(`${API_BASE_URL}/admin/compliance/alerts/${alertId}/escalate`, {
        escalationNotes: notes,
        escalatedBy: 'current-user'
      });

      setSnackbar({ open: true, message: 'Alert escalated successfully', severity: 'success' });
      loadData();
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to escalate alert', severity: 'error' });
    }
  };

  // Helper functions
  const getSeverityColor = (severity: string) => {
    switch (severity) {
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
      case 'INVESTIGATING': return 'info';
      case 'APPROVED': return 'success';
      case 'BLOCKED': return 'error';
      case 'ESCALATED': return 'secondary';
      default: return 'default';
    }
  };

  const getAlertTypeIcon = (type: string) => {
    switch (type) {
      case 'OFAC': return <FlagIcon color="error" />;
      case 'SANCTIONS': return <FlagIcon color="error" />;
      case 'AML': return <MoneyIcon color="warning" />;
      case 'PEP': return <PersonIcon color="warning" />;
      case 'HIGH_RISK_COUNTRY': return <GlobalIcon color="warning" />;
      case 'KYC': return <CardIcon color="info" />;
      default: return <WarningIcon />;
    }
  };

  const filteredAlerts = alerts.filter(a => {
    if (tabValue === 0) return a.status === 'PENDING';
    if (tabValue === 1) return a.status === 'INVESTIGATING';
    if (tabValue === 2) return a.severity === 'CRITICAL';
    if (tabValue === 3) return a.alertType === 'OFAC' || a.alertType === 'SANCTIONS';
    if (tabValue === 4) return a.status === 'APPROVED' || a.status === 'BLOCKED';
    return true;
  });

  return (
    <Box sx={{ padding: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          Compliance Alert Investigation
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
            <Chip icon={<RefreshIcon />} label="Auto-refresh: ON" color="primary" variant="outlined" />
          </Tooltip>
        </Box>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Alerts
              </Typography>
              <Typography variant="h4">{stats.totalAlerts}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Pending Review
              </Typography>
              <Typography variant="h4" color="warning.main">{stats.pendingAlerts}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Investigating
              </Typography>
              <Typography variant="h4" color="info.main">{stats.investigatingAlerts}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: 'error.light' }}>
            <CardContent>
              <Typography color="error.contrastText" gutterBottom>
                CRITICAL
              </Typography>
              <Typography variant="h4" color="error.contrastText">{stats.criticalAlerts}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                OFAC Matches
              </Typography>
              <Typography variant="h4" color="error.main">{stats.ofacMatches}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                AML High Risk
              </Typography>
              <Typography variant="h4" color="warning.main">{stats.amlHighRisk}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Blocked Today
              </Typography>
              <Typography variant="h4" color="error.main">{stats.blockedToday}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Approved Today
              </Typography>
              <Typography variant="h4" color="success.main">{stats.approvedToday}</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Critical Alerts Warning */}
      {stats.criticalAlerts > 0 && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <strong>{stats.criticalAlerts} CRITICAL compliance alerts require immediate investigation!</strong>
          {stats.ofacMatches > 0 && (
            <Typography variant="body2" sx={{ mt: 1 }}>
              ⚠️ {stats.ofacMatches} OFAC/Sanctions matches detected - regulatory reporting required
            </Typography>
          )}
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
                <InputLabel>Alert Type</InputLabel>
                <Select
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                  label="Alert Type"
                >
                  <MenuItem value="ALL">All Types</MenuItem>
                  <MenuItem value="OFAC">OFAC</MenuItem>
                  <MenuItem value="SANCTIONS">Sanctions</MenuItem>
                  <MenuItem value="AML">AML</MenuItem>
                  <MenuItem value="PEP">PEP</MenuItem>
                  <MenuItem value="HIGH_RISK_COUNTRY">High Risk Country</MenuItem>
                  <MenuItem value="KYC">KYC</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Severity</InputLabel>
                <Select
                  value={filterSeverity}
                  onChange={(e) => setFilterSeverity(e.target.value)}
                  label="Severity"
                >
                  <MenuItem value="ALL">All Severities</MenuItem>
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
              <Badge badgeContent={stats.pendingAlerts} color="warning">
                Pending
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={stats.investigatingAlerts} color="info">
                Investigating
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={stats.criticalAlerts} color="error">
                Critical
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={stats.ofacMatches} color="error">
                OFAC/Sanctions
              </Badge>
            }
          />
          <Tab label="Resolved" />
          <Tab label="All" />
        </Tabs>

        {loading && <LinearProgress />}

        {/* Alerts Table */}
        {[0, 1, 2, 3, 4, 5].map((index) => (
          <TabPanel value={tabValue} index={index} key={index}>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Severity</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>User/Entity</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Match Score</TableCell>
                    <TableCell>Risk Score</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredAlerts.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center">
                        <Typography variant="body2" color="textSecondary">
                          No alerts found
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredAlerts.map((alert) => (
                      <TableRow key={alert.id} hover>
                        <TableCell>
                          <Chip
                            label={alert.severity}
                            color={getSeverityColor(alert.severity) as any}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            {getAlertTypeIcon(alert.alertType)}
                            <Typography variant="body2">{alert.alertType}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" fontWeight="bold">
                            {alert.userName || 'N/A'}
                          </Typography>
                          {alert.userId && (
                            <Typography variant="caption" color="textSecondary" sx={{ fontFamily: 'monospace' }}>
                              {alert.userId.substring(0, 8)}...
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          <Tooltip title={alert.description}>
                            <Typography variant="body2" noWrap sx={{ maxWidth: 250 }}>
                              {alert.description}
                            </Typography>
                          </Tooltip>
                          {alert.matchedEntity && (
                            <Typography variant="caption" color="error.main">
                              Match: {alert.matchedEntity}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          {alert.matchScore !== undefined && (
                            <Chip
                              label={`${alert.matchScore}%`}
                              color={alert.matchScore >= 90 ? 'error' : alert.matchScore >= 70 ? 'warning' : 'default'}
                              size="small"
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          {alert.riskScore !== undefined && (
                            <Chip
                              label={alert.riskScore}
                              color={alert.riskScore >= 80 ? 'error' : alert.riskScore >= 50 ? 'warning' : 'success'}
                              size="small"
                            />
                          )}
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={alert.status}
                            color={getStatusColor(alert.status) as any}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          {format(new Date(alert.createdAt), 'MMM dd, HH:mm')}
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 1 }}>
                            <Tooltip title="View Details">
                              <IconButton
                                size="small"
                                onClick={() => handleViewDetails(alert)}
                              >
                                <ViewIcon />
                              </IconButton>
                            </Tooltip>
                            {alert.status === 'PENDING' && (
                              <>
                                <Tooltip title="Assign to Me">
                                  <IconButton
                                    size="small"
                                    color="primary"
                                    onClick={() => handleAssignToMe(alert.id)}
                                  >
                                    <AssignIcon />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="Start Investigation">
                                  <IconButton
                                    size="small"
                                    color="info"
                                    onClick={() => handleStartInvestigation(alert.id)}
                                  >
                                    <WarningIcon />
                                  </IconButton>
                                </Tooltip>
                              </>
                            )}
                            {alert.status === 'INVESTIGATING' && (
                              <>
                                <Tooltip title="Make Decision">
                                  <IconButton
                                    size="small"
                                    color="success"
                                    onClick={() => handleMakeDecision(alert)}
                                  >
                                    <ApproveIcon />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="Escalate">
                                  <IconButton
                                    size="small"
                                    color="warning"
                                    onClick={() => handleEscalate(alert.id)}
                                  >
                                    <FlagIcon />
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
          Compliance Alert Details - {selectedAlert?.alertType}
        </DialogTitle>
        <DialogContent>
          {selectedAlert && (
            <Box sx={{ mt: 2 }}>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="textSecondary">Severity</Typography>
                  <Chip label={selectedAlert.severity} color={getSeverityColor(selectedAlert.severity) as any} />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="textSecondary">Status</Typography>
                  <Chip label={selectedAlert.status} color={getStatusColor(selectedAlert.status) as any} />
                </Grid>
                <Grid item xs={12}>
                  <Divider />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="textSecondary">User Name</Typography>
                  <Typography variant="body2">{selectedAlert.userName || 'N/A'}</Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="textSecondary">User ID</Typography>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                    {selectedAlert.userId || 'N/A'}
                  </Typography>
                </Grid>
                {selectedAlert.transactionId && (
                  <>
                    <Grid item xs={6}>
                      <Typography variant="subtitle2" color="textSecondary">Transaction ID</Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                        {selectedAlert.transactionId}
                      </Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="subtitle2" color="textSecondary">Amount</Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {selectedAlert.currency} {selectedAlert.transactionAmount?.toLocaleString()}
                      </Typography>
                    </Grid>
                  </>
                )}
                <Grid item xs={12}>
                  <Divider />
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Description</Typography>
                  <Alert severity="warning" sx={{ mt: 1 }}>
                    {selectedAlert.description}
                  </Alert>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="textSecondary">Findings</Typography>
                  <Paper sx={{ p: 2, bgcolor: 'grey.100' }}>
                    <Typography variant="body2">{selectedAlert.findings}</Typography>
                  </Paper>
                </Grid>
                {selectedAlert.matchedEntity && (
                  <>
                    <Grid item xs={6}>
                      <Typography variant="subtitle2" color="textSecondary">Matched Entity</Typography>
                      <Alert severity="error" sx={{ mt: 1 }}>
                        {selectedAlert.matchedEntity}
                      </Alert>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="subtitle2" color="textSecondary">Matched List</Typography>
                      <Typography variant="body2">{selectedAlert.matchedList}</Typography>
                    </Grid>
                  </>
                )}
                {selectedAlert.matchScore !== undefined && (
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="textSecondary">Match Score</Typography>
                    <Typography variant="h5" color={selectedAlert.matchScore >= 90 ? 'error.main' : 'warning.main'}>
                      {selectedAlert.matchScore}%
                    </Typography>
                  </Grid>
                )}
                {selectedAlert.riskScore !== undefined && (
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="textSecondary">Risk Score</Typography>
                    <Typography variant="h5" color={selectedAlert.riskScore >= 80 ? 'error.main' : 'warning.main'}>
                      {selectedAlert.riskScore}/100
                    </Typography>
                  </Grid>
                )}
                {selectedAlert.investigationNotes && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="textSecondary">Investigation Notes</Typography>
                    <Typography variant="body2">{selectedAlert.investigationNotes}</Typography>
                  </Grid>
                )}
                {selectedAlert.decision && (
                  <Grid item xs={12}>
                    <Typography variant="subtitle2" color="textSecondary">Decision</Typography>
                    <Chip
                      label={selectedAlert.decision}
                      color={selectedAlert.decision === 'APPROVE' ? 'success' : 'error'}
                    />
                    <Typography variant="caption" color="textSecondary" sx={{ ml: 2 }}>
                      by {selectedAlert.decidedBy} at {selectedAlert.decidedAt && format(new Date(selectedAlert.decidedAt), 'PPpp')}
                    </Typography>
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

      {/* Decision Dialog */}
      <Dialog
        open={decisionDialogOpen}
        onClose={() => setDecisionDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Make Compliance Decision</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel>Decision</InputLabel>
              <Select
                value={decision}
                onChange={(e) => setDecision(e.target.value)}
                label="Decision"
              >
                <MenuItem value="APPROVE">Approve - Clear for processing</MenuItem>
                <MenuItem value="BLOCK">Block - Compliance violation</MenuItem>
                <MenuItem value="ESCALATE">Escalate - Requires senior review</MenuItem>
                <MenuItem value="REQUEST_MORE_INFO">Request More Information</MenuItem>
              </Select>
            </FormControl>
            <TextField
              fullWidth
              multiline
              rows={6}
              label="Investigation Notes"
              value={investigationNotes}
              onChange={(e) => setInvestigationNotes(e.target.value)}
              placeholder="Document your investigation findings, evidence reviewed, and rationale for decision..."
              required
              helperText="Required for compliance audit trail"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecisionDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleDecisionSubmit}
            variant="contained"
            color={decision === 'BLOCK' ? 'error' : 'success'}
            disabled={!investigationNotes.trim()}
          >
            Submit Decision
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

export default ComplianceAlertInvestigation;
