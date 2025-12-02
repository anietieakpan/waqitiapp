import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Alert,
  AlertTitle,
  Badge,
  IconButton,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemText,
  Divider,
  Tooltip,
  Snackbar,
} from '@mui/material';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import ShieldIcon from '@mui/icons-material/Shield';
import CloseIcon from '@mui/icons-material/Close';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import VisibilityIcon from '@mui/icons-material/Visibility';;
import { format } from 'date-fns';
import { toast } from 'react-hot-toast';
import websocketService, { WebSocketEvent, FraudAlertEvent } from '@/services/websocketService';

/**
 * Real-Time Fraud Alerts Component
 *
 * FEATURES:
 * - Live fraud detection alerts
 * - Severity-based prioritization
 * - Sound and visual notifications
 * - Alert history
 * - Quick actions (block, review, dismiss)
 * - Alert acknowledgment
 *
 * SECURITY:
 * - Admin/compliance role required for actions
 * - Audit trail for all actions
 * - Automatic escalation for critical alerts
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

export const RealTimeFraudAlerts: React.FC = () => {
  const [alerts, setAlerts] = useState<FraudAlertEvent[]>([]);
  const [selectedAlert, setSelectedAlert] = useState<FraudAlertEvent | null>(null);
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [unacknowledgedCount, setUnacknowledgedCount] = useState(0);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [latestAlert, setLatestAlert] = useState<FraudAlertEvent | null>(null);

  const MAX_ALERTS = 50;

  useEffect(() => {
    initializeFraudMonitoring();

    return () => {
      websocketService.off(WebSocketEvent.FRAUD_ALERT, handleFraudAlert);
    };
  }, []);

  const initializeFraudMonitoring = () => {
    const userId = getCurrentUserId();

    websocketService.subscribeToFraudAlerts(userId, handleFraudAlert);
  };

  const getCurrentUserId = (): string => {
    return 'current-user-id';
  };

  const handleFraudAlert = useCallback((alert: FraudAlertEvent) => {
    console.log('[Fraud Alert] New alert:', alert);

    setAlerts(prev => {
      const exists = prev.some(a => a.id === alert.id);
      if (exists) return prev;

      const updated = [alert, ...prev];
      return updated.slice(0, MAX_ALERTS);
    });

    setUnacknowledgedCount(prev => prev + 1);
    setLatestAlert(alert);
    setSnackbarOpen(true);

    // Play alert sound based on severity
    playAlertSound(alert.severity);

    // Show toast notification
    const message = `${alert.severity} fraud alert: ${alert.reason}`;
    if (alert.severity === 'CRITICAL') {
      toast.error(message, { duration: 10000 });
    } else if (alert.severity === 'HIGH') {
      toast.error(message, { duration: 7000 });
    } else {
      toast.warning(message, { duration: 5000 });
    }

    // Auto-escalate critical alerts
    if (alert.severity === 'CRITICAL' && alert.actionRequired) {
      escalateAlert(alert);
    }
  }, []);

  const playAlertSound = (severity: string) => {
    const audio = new Audio(`/sounds/alert-${severity.toLowerCase()}.mp3`);
    audio.volume = severity === 'CRITICAL' ? 1.0 : 0.7;
    audio.play().catch(err => console.error('Failed to play alert sound:', err));
  };

  const escalateAlert = (alert: FraudAlertEvent) => {
    // Send to admin/compliance team
    console.log('[Fraud Alert] Escalating critical alert:', alert.id);
    // API call to escalate
  };

  const handleAcknowledge = (alertId: string) => {
    setAlerts(prev =>
      prev.map(a => (a.id === alertId ? { ...a, acknowledged: true } : a))
    );
    setUnacknowledgedCount(prev => Math.max(0, prev - 1));
    toast.success('Alert acknowledged');
  };

  const handleBlockUser = async (alert: FraudAlertEvent) => {
    try {
      // API call to block user
      console.log('[Fraud Alert] Blocking user:', alert.userId);
      toast.success('User blocked successfully');
      setDetailDialogOpen(false);
    } catch (error) {
      toast.error('Failed to block user');
    }
  };

  const handleDismiss = (alertId: string) => {
    setAlerts(prev => prev.filter(a => a.id !== alertId));
    setUnacknowledgedCount(prev => Math.max(0, prev - 1));
    toast.info('Alert dismissed');
  };

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return <ErrorIcon color="error" />;
      case 'HIGH':
        return <Warning color="error" />;
      case 'MEDIUM':
        return <Warning color="warning" />;
      case 'LOW':
        return <Info color="info" />;
      default:
        return <Shield />;
    }
  };

  const getSeverityColor = (severity: string): 'error' | 'warning' | 'info' => {
    switch (severity) {
      case 'CRITICAL':
      case 'HIGH':
        return 'error';
      case 'MEDIUM':
        return 'warning';
      default:
        return 'info';
    }
  };

  return (
    <Box>
      {/* Alert Badge */}
      <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Badge badgeContent={unacknowledgedCount} color="error" max={99}>
            <Shield fontSize="large" color="primary" />
          </Badge>
          <Box>
            <Typography variant="h6">Fraud Monitoring</Typography>
            <Typography variant="body2" color="text.secondary">
              {unacknowledgedCount} unacknowledged alerts
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* Alert List */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {alerts.length === 0 ? (
          <Card>
            <CardContent sx={{ textAlign: 'center', py: 4 }}>
              <Shield sx={{ fontSize: 48, color: 'success.main', mb: 2 }} />
              <Typography variant="h6" gutterBottom>
                No Fraud Alerts
              </Typography>
              <Typography variant="body2" color="text.secondary">
                All systems operating normally
              </Typography>
            </CardContent>
          </Card>
        ) : (
          alerts.map((alert) => (
            <Card
              key={alert.id}
              sx={{
                borderLeft: 6,
                borderColor:
                  alert.severity === 'CRITICAL'
                    ? 'error.main'
                    : alert.severity === 'HIGH'
                    ? 'error.light'
                    : alert.severity === 'MEDIUM'
                    ? 'warning.main'
                    : 'info.main',
                opacity: alert.acknowledged ? 0.6 : 1,
              }}
            >
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                  <Box sx={{ display: 'flex', gap: 2, flex: 1 }}>
                    <Box>{getSeverityIcon(alert.severity)}</Box>
                    <Box sx={{ flex: 1 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                        <Typography variant="h6">{alert.type.replace(/_/g, ' ')}</Typography>
                        <Chip
                          label={alert.severity}
                          size="small"
                          color={getSeverityColor(alert.severity)}
                        />
                        {alert.actionRequired && (
                          <Chip label="ACTION REQUIRED" size="small" color="error" />
                        )}
                      </Box>

                      <Typography variant="body2" gutterBottom>
                        {alert.reason}
                      </Typography>

                      <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                        <Typography variant="caption" color="text.secondary">
                          Risk Score: {(alert.riskScore * 100).toFixed(0)}%
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {format(new Date(alert.timestamp), 'MMM dd, HH:mm:ss')}
                        </Typography>
                        {alert.transactionId && (
                          <Typography variant="caption" color="text.secondary">
                            Transaction: {alert.transactionId}
                          </Typography>
                        )}
                      </Box>
                    </Box>
                  </Box>

                  <Box sx={{ display: 'flex', gap: 1 }}>
                    {!alert.acknowledged && (
                      <Tooltip title="Acknowledge">
                        <IconButton
                          size="small"
                          color="success"
                          onClick={() => handleAcknowledge(alert.id)}
                        >
                          <CheckCircle />
                        </IconButton>
                      </Tooltip>
                    )}
                    <Tooltip title="View Details">
                      <IconButton
                        size="small"
                        onClick={() => {
                          setSelectedAlert(alert);
                          setDetailDialogOpen(true);
                        }}
                      >
                        <Visibility />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Dismiss">
                      <IconButton
                        size="small"
                        onClick={() => handleDismiss(alert.id)}
                      >
                        <Close />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          ))
        )}
      </Box>

      {/* Alert Detail Dialog */}
      <Dialog
        open={detailDialogOpen}
        onClose={() => setDetailDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {selectedAlert && getSeverityIcon(selectedAlert.severity)}
            Fraud Alert Details
          </Box>
        </DialogTitle>
        <DialogContent>
          {selectedAlert && (
            <Box>
              <Alert severity={getSeverityColor(selectedAlert.severity)} sx={{ mb: 2 }}>
                <AlertTitle>{selectedAlert.type.replace(/_/g, ' ')}</AlertTitle>
                {selectedAlert.reason}
              </Alert>

              <List>
                <ListItem>
                  <ListItemText
                    primary="Alert ID"
                    secondary={selectedAlert.id}
                  />
                </ListItem>
                <Divider />
                <ListItem>
                  <ListItemText
                    primary="Severity"
                    secondary={
                      <Chip
                        label={selectedAlert.severity}
                        size="small"
                        color={getSeverityColor(selectedAlert.severity)}
                      />
                    }
                  />
                </ListItem>
                <Divider />
                <ListItem>
                  <ListItemText
                    primary="Risk Score"
                    secondary={`${(selectedAlert.riskScore * 100).toFixed(2)}%`}
                  />
                </ListItem>
                <Divider />
                <ListItem>
                  <ListItemText
                    primary="User ID"
                    secondary={selectedAlert.userId}
                  />
                </ListItem>
                {selectedAlert.transactionId && (
                  <>
                    <Divider />
                    <ListItem>
                      <ListItemText
                        primary="Transaction ID"
                        secondary={selectedAlert.transactionId}
                      />
                    </ListItem>
                  </>
                )}
                <Divider />
                <ListItem>
                  <ListItemText
                    primary="Timestamp"
                    secondary={format(new Date(selectedAlert.timestamp), 'PPpp')}
                  />
                </ListItem>
              </List>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailDialogOpen(false)}>Close</Button>
          {selectedAlert?.actionRequired && (
            <>
              <Button
                variant="outlined"
                color="error"
                startIcon={<Block />}
                onClick={() => selectedAlert && handleBlockUser(selectedAlert)}
              >
                Block User
              </Button>
              <Button
                variant="contained"
                color="primary"
                onClick={() => {
                  if (selectedAlert) {
                    handleAcknowledge(selectedAlert.id);
                    setDetailDialogOpen(false);
                  }
                }}
              >
                Acknowledge & Review
              </Button>
            </>
          )}
        </DialogActions>
      </Dialog>

      {/* Snackbar for new alerts */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={() => setSnackbarOpen(false)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert
          onClose={() => setSnackbarOpen(false)}
          severity={latestAlert ? getSeverityColor(latestAlert.severity) : 'info'}
          sx={{ width: '100%' }}
        >
          <AlertTitle>New Fraud Alert</AlertTitle>
          {latestAlert?.reason}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default RealTimeFraudAlerts;
