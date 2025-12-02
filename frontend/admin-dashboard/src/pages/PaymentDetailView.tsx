import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  Button,
  Divider,
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
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
  List,
  ListItem,
  ListItemText,
  IconButton,
  Tooltip,
  CircularProgress,
  Snackbar,
  Accordion,
  AccordionSummary,
  AccordionDetails
} from '@mui/material';
import {
  Payment as PaymentIcon,
  ArrowForward as ArrowForwardIcon,
  Refresh as RefreshIcon,
  Cancel as CancelIcon,
  Undo as RefundIcon,
  CheckCircle as ApproveIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  ContentCopy as CopyIcon,
  Receipt as ReceiptIcon,
  ExpandMore as ExpandMoreIcon,
  AccountBalance as BankIcon,
  CreditCard as CardIcon,
  Person as PersonIcon,
  Business as MerchantIcon
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import axios from 'axios';

interface Payment {
  id: string;
  userId: string;
  userName: string;
  merchantId?: string;
  merchantName?: string;
  amount: number;
  currency: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'REFUNDED' | 'PARTIALLY_REFUNDED';
  paymentMethod: 'CARD' | 'BANK_TRANSFER' | 'WALLET' | 'CRYPTO';
  paymentProvider: string;
  providerTransactionId?: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
  failureReason?: string;
  refundAmount?: number;
  refundedAt?: string;
  metadata: any;
  fraudScore?: number;
  fraudStatus?: string;
  ipAddress: string;
  deviceId: string;
  userAgent: string;
}

interface StatusHistory {
  status: string;
  timestamp: string;
  notes?: string;
  changedBy?: string;
}

interface RefundHistory {
  id: string;
  amount: number;
  reason: string;
  status: string;
  createdAt: string;
  processedBy: string;
}

interface FraudCheck {
  id: string;
  checkType: string;
  result: string;
  score: number;
  details: string;
  timestamp: string;
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

const PaymentDetailView: React.FC = () => {
  const { paymentId } = useParams<{ paymentId: string }>();
  const navigate = useNavigate();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [statusHistory, setStatusHistory] = useState<StatusHistory[]>([]);
  const [refundHistory, setRefundHistory] = useState<RefundHistory[]>([]);
  const [fraudChecks, setFraudChecks] = useState<FraudCheck[]>([]);
  const [loading, setLoading] = useState(true);
  const [tabValue, setTabValue] = useState(0);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [actionType, setActionType] = useState<'cancel' | 'refund' | 'approve'>('cancel');
  const [actionReason, setActionReason] = useState('');
  const [refundAmount, setRefundAmount] = useState<number>(0);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

  useEffect(() => {
    loadPaymentData();
  }, [paymentId]);

  const loadPaymentData = async () => {
    setLoading(true);
    try {
      const [paymentRes, historyRes, refundsRes, fraudRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/payments/${paymentId}`),
        axios.get(`${API_BASE_URL}/admin/payments/${paymentId}/status-history`),
        axios.get(`${API_BASE_URL}/admin/payments/${paymentId}/refunds`),
        axios.get(`${API_BASE_URL}/admin/payments/${paymentId}/fraud-checks`)
      ]);

      setPayment(paymentRes.data);
      setStatusHistory(historyRes.data);
      setRefundHistory(refundsRes.data);
      setFraudChecks(fraudRes.data);
      setRefundAmount(paymentRes.data.amount);
    } catch (error) {
      console.error('Failed to load payment data:', error);
      setSnackbar({ open: true, message: 'Failed to load payment data', severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleAction = async () => {
    if (!actionReason.trim()) {
      setSnackbar({ open: true, message: 'Please provide a reason', severity: 'error' });
      return;
    }

    try {
      const endpoint = actionType === 'refund'
        ? `${API_BASE_URL}/admin/payments/${paymentId}/refund`
        : `${API_BASE_URL}/admin/payments/${paymentId}/${actionType}`;

      const payload = actionType === 'refund'
        ? { amount: refundAmount, reason: actionReason, actionBy: 'current-admin' }
        : { reason: actionReason, actionBy: 'current-admin' };

      await axios.post(endpoint, payload);

      setSnackbar({ open: true, message: `Payment ${actionType} successful`, severity: 'success' });
      setActionDialogOpen(false);
      setActionReason('');
      loadPaymentData();
    } catch (error) {
      setSnackbar({ open: true, message: `Failed to ${actionType} payment`, severity: 'error' });
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setSnackbar({ open: true, message: 'Copied to clipboard', severity: 'success' });
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'success';
      case 'PENDING': return 'warning';
      case 'PROCESSING': return 'info';
      case 'FAILED': return 'error';
      case 'CANCELLED': return 'default';
      case 'REFUNDED': return 'secondary';
      case 'PARTIALLY_REFUNDED': return 'secondary';
      default: return 'default';
    }
  };

  const getPaymentMethodIcon = (method: string) => {
    switch (method) {
      case 'CARD': return <CardIcon />;
      case 'BANK_TRANSFER': return <BankIcon />;
      case 'WALLET': return <PaymentIcon />;
      default: return <PaymentIcon />;
    }
  };

  const getFraudScoreColor = (score?: number) => {
    if (!score) return 'success';
    if (score >= 80) return 'error';
    if (score >= 50) return 'warning';
    return 'success';
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '80vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!payment) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Payment not found</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Payment Details
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography variant="body2" color="textSecondary" sx={{ fontFamily: 'monospace' }}>
              {payment.id}
            </Typography>
            <IconButton size="small" onClick={() => copyToClipboard(payment.id)}>
              <CopyIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadPaymentData}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<ReceiptIcon />}
            onClick={() => window.open(`/admin/payments/${paymentId}/receipt`, '_blank')}
          >
            Receipt
          </Button>
        </Box>
      </Box>

      {/* Status and Amount Overview */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Status
              </Typography>
              <Chip
                label={payment.status}
                color={getStatusColor(payment.status) as any}
                size="medium"
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Amount
              </Typography>
              <Typography variant="h5">
                {payment.currency} {payment.amount.toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Payment Method
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                {getPaymentMethodIcon(payment.paymentMethod)}
                <Typography variant="body1">{payment.paymentMethod}</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ bgcolor: payment.fraudScore && payment.fraudScore >= 50 ? 'error.light' : 'inherit' }}>
            <CardContent>
              <Typography color={payment.fraudScore && payment.fraudScore >= 50 ? 'error.contrastText' : 'textSecondary'} gutterBottom>
                Fraud Score
              </Typography>
              <Typography variant="h5" color={payment.fraudScore && payment.fraudScore >= 50 ? 'error.contrastText' : `${getFraudScoreColor(payment.fraudScore)}.main`}>
                {payment.fraudScore || 0}/100
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Fraud Alert */}
      {payment.fraudScore && payment.fraudScore >= 80 && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <strong>HIGH FRAUD RISK DETECTED</strong> - This payment requires immediate review
        </Alert>
      )}

      {payment.failureReason && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <strong>Payment Failed:</strong> {payment.failureReason}
        </Alert>
      )}

      {/* Payment Information */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Transaction Details
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <TableContainer>
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell><strong>User</strong></TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <PersonIcon fontSize="small" />
                          {payment.userName}
                        </Box>
                      </TableCell>
                    </TableRow>
                    {payment.merchantName && (
                      <TableRow>
                        <TableCell><strong>Merchant</strong></TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <MerchantIcon fontSize="small" />
                            {payment.merchantName}
                          </Box>
                        </TableCell>
                      </TableRow>
                    )}
                    <TableRow>
                      <TableCell><strong>Description</strong></TableCell>
                      <TableCell>{payment.description}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Payment Provider</strong></TableCell>
                      <TableCell>{payment.paymentProvider}</TableCell>
                    </TableRow>
                    {payment.providerTransactionId && (
                      <TableRow>
                        <TableCell><strong>Provider TXN ID</strong></TableCell>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                          {payment.providerTransactionId}
                          <IconButton size="small" onClick={() => copyToClipboard(payment.providerTransactionId!)}>
                            <CopyIcon fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    )}
                    <TableRow>
                      <TableCell><strong>Created At</strong></TableCell>
                      <TableCell>{format(new Date(payment.createdAt), 'PPpp')}</TableCell>
                    </TableRow>
                    {payment.completedAt && (
                      <TableRow>
                        <TableCell><strong>Completed At</strong></TableCell>
                        <TableCell>{format(new Date(payment.completedAt), 'PPpp')}</TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Technical Details
              </Typography>
              <Divider sx={{ mb: 2 }} />
              <TableContainer>
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell><strong>IP Address</strong></TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{payment.ipAddress}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>Device ID</strong></TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{payment.deviceId}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell><strong>User Agent</strong></TableCell>
                      <TableCell sx={{ fontSize: '0.75rem', wordBreak: 'break-all' }}>{payment.userAgent}</TableCell>
                    </TableRow>
                    {payment.fraudStatus && (
                      <TableRow>
                        <TableCell><strong>Fraud Status</strong></TableCell>
                        <TableCell>
                          <Chip label={payment.fraudStatus} size="small" color={getFraudScoreColor(payment.fraudScore) as any} />
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tabs */}
      <Card>
        <Tabs value={tabValue} onChange={(e, newValue) => setTabValue(newValue)}>
          <Tab label="Status History" />
          <Tab label="Refund History" />
          <Tab label="Fraud Checks" />
          <Tab label="Metadata" />
          <Tab label="Actions" />
        </Tabs>

        {/* Status History Tab */}
        <TabPanel value={tabValue} index={0}>
          <Timeline>
            {statusHistory.map((history, index) => (
              <TimelineItem key={index}>
                <TimelineOppositeContent color="textSecondary">
                  {format(new Date(history.timestamp), 'PPp')}
                </TimelineOppositeContent>
                <TimelineSeparator>
                  <TimelineDot color={getStatusColor(history.status) as any} />
                  {index < statusHistory.length - 1 && <TimelineConnector />}
                </TimelineSeparator>
                <TimelineContent>
                  <Typography variant="h6">{history.status}</Typography>
                  {history.notes && <Typography variant="body2" color="textSecondary">{history.notes}</Typography>}
                  {history.changedBy && <Typography variant="caption" color="textSecondary">by {history.changedBy}</Typography>}
                </TimelineContent>
              </TimelineItem>
            ))}
          </Timeline>
        </TabPanel>

        {/* Refund History Tab */}
        <TabPanel value={tabValue} index={1}>
          {refundHistory.length === 0 ? (
            <Alert severity="info">No refunds processed for this payment</Alert>
          ) : (
            <TableContainer>
              <Table>
                <TableBody>
                  {refundHistory.map((refund) => (
                    <TableRow key={refund.id}>
                      <TableCell>
                        <Typography variant="body2" fontWeight="bold">
                          {payment.currency} {refund.amount.toLocaleString()}
                        </Typography>
                        <Typography variant="caption" color="textSecondary">
                          {format(new Date(refund.createdAt), 'PPp')}
                        </Typography>
                      </TableCell>
                      <TableCell>{refund.reason}</TableCell>
                      <TableCell>
                        <Chip label={refund.status} size="small" />
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption">by {refund.processedBy}</Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </TabPanel>

        {/* Fraud Checks Tab */}
        <TabPanel value={tabValue} index={2}>
          {fraudChecks.length === 0 ? (
            <Alert severity="info">No fraud checks performed</Alert>
          ) : (
            <List>
              {fraudChecks.map((check) => (
                <Accordion key={check.id}>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
                      <Typography>{check.checkType}</Typography>
                      <Chip
                        label={`Score: ${check.score}`}
                        size="small"
                        color={check.score >= 80 ? 'error' : check.score >= 50 ? 'warning' : 'success'}
                      />
                      <Chip label={check.result} size="small" />
                      <Typography variant="caption" sx={{ ml: 'auto' }}>
                        {format(new Date(check.timestamp), 'PPp')}
                      </Typography>
                    </Box>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Typography variant="body2">{check.details}</Typography>
                  </AccordionDetails>
                </Accordion>
              ))}
            </List>
          )}
        </TabPanel>

        {/* Metadata Tab */}
        <TabPanel value={tabValue} index={3}>
          <Paper sx={{ p: 2, bgcolor: 'grey.100' }}>
            <Typography variant="body2" component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
              {JSON.stringify(payment.metadata, null, 2)}
            </Typography>
          </Paper>
        </TabPanel>

        {/* Actions Tab */}
        <TabPanel value={tabValue} index={4}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Payment Actions</Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
                    {payment.status === 'PENDING' && (
                      <>
                        <Button
                          variant="outlined"
                          color="success"
                          startIcon={<ApproveIcon />}
                          onClick={() => { setActionType('approve'); setActionDialogOpen(true); }}
                        >
                          Approve Payment
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          startIcon={<CancelIcon />}
                          onClick={() => { setActionType('cancel'); setActionDialogOpen(true); }}
                        >
                          Cancel Payment
                        </Button>
                      </>
                    )}
                    {payment.status === 'COMPLETED' && (
                      <Button
                        variant="outlined"
                        color="warning"
                        startIcon={<RefundIcon />}
                        onClick={() => { setActionType('refund'); setActionDialogOpen(true); }}
                      >
                        Process Refund
                      </Button>
                    )}
                  </Box>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Investigation</Typography>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
                    <Button variant="outlined" startIcon={<WarningIcon />}>
                      Flag for Review
                    </Button>
                    <Button variant="outlined" startIcon={<InfoIcon />}>
                      Request Provider Details
                    </Button>
                    <Button variant="outlined" color="error" startIcon={<ErrorIcon />}>
                      Report Fraud
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>
      </Card>

      {/* Action Dialog */}
      <Dialog open={actionDialogOpen} onClose={() => setActionDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {actionType === 'cancel' && 'Cancel Payment'}
          {actionType === 'refund' && 'Process Refund'}
          {actionType === 'approve' && 'Approve Payment'}
        </DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            {actionType === 'refund' && (
              <TextField
                fullWidth
                type="number"
                label="Refund Amount"
                value={refundAmount}
                onChange={(e) => setRefundAmount(parseFloat(e.target.value))}
                InputProps={{
                  startAdornment: <Typography sx={{ mr: 1 }}>{payment.currency}</Typography>
                }}
                helperText={`Max: ${payment.currency} ${payment.amount.toLocaleString()}`}
                sx={{ mb: 2 }}
              />
            )}
            <TextField
              fullWidth
              multiline
              rows={4}
              label="Reason"
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder="Provide reason for this action..."
              required
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleAction}
            variant="contained"
            color={actionType === 'cancel' ? 'error' : 'primary'}
            disabled={!actionReason.trim() || (actionType === 'refund' && refundAmount <= 0)}
          >
            Confirm
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

export default PaymentDetailView;
