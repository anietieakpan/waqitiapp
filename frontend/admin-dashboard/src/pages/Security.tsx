import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
  Tabs,
  Tab,
  Chip,
  Alert,
  AlertTitle,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Badge,
  LinearProgress,
  Stack,
  Tooltip,
  Avatar,
  AvatarGroup,
  Divider,
  Switch,
  FormControlLabel,
  useTheme,
} from '@mui/material';
import {
  Security as SecurityIcon,
  VpnKey,
  LockOpen,
  Block,
  Warning,
  CheckCircle,
  Error,
  Shield,
  Fingerprint,
  Key,
  PhonelinkLock,
  AdminPanelSettings,
  VerifiedUser,
  BugReport,
  NetworkCheck,
  Assessment,
  Timeline,
  Visibility,
  Edit,
  Delete,
  Add,
  Refresh,
  Download,
  Search,
  FilterList,
  PersonAdd,
  Group,
  AccessTime,
  LocationOn,
  DevicesOther,
} from '@mui/icons-material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format, formatDistanceToNow } from 'date-fns';

import { securityService } from '../services/securityService';
import ThreatMonitor from '../components/security/ThreatMonitor';
import AccessControlPanel from '../components/security/AccessControlPanel';
import SecurityIncidents from '../components/security/SecurityIncidents';
import VulnerabilityScanner from '../components/security/VulnerabilityScanner';
import AuditLog from '../components/security/AuditLog';
import EncryptionStatus from '../components/security/EncryptionStatus';
import MFAManagement from '../components/security/MFAManagement';
import SecurityPolicies from '../components/security/SecurityPolicies';
import { useNotification } from '../hooks/useNotification';

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

const Security: React.FC = () => {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const { showNotification } = useNotification();
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [incidentDialogOpen, setIncidentDialogOpen] = useState(false);
  const [selectedIncident, setSelectedIncident] = useState<any>(null);
  const [filterDialogOpen, setFilterDialogOpen] = useState(false);

  // Fetch security overview
  const { data: securityOverview, isLoading: overviewLoading } = useQuery({
    queryKey: ['security-overview'],
    queryFn: () => securityService.getSecurityOverview(),
    refetchInterval: 60000, // Refresh every minute
  });

  // Fetch active threats
  const { data: activeThreats, isLoading: threatsLoading } = useQuery({
    queryKey: ['active-threats'],
    queryFn: () => securityService.getActiveThreats(),
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  // Fetch security score
  const { data: securityScore } = useQuery({
    queryKey: ['security-score'],
    queryFn: () => securityService.getSecurityScore(),
  });

  // Block IP mutation
  const blockIpMutation = useMutation({
    mutationFn: (ip: string) => securityService.blockIp(ip),
    onSuccess: () => {
      queryClient.invalidateQueries(['security-overview']);
      showNotification('IP address blocked successfully', 'success');
    },
  });

  // Resolve incident mutation
  const resolveIncidentMutation = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: string }) =>
      securityService.resolveIncident(id, resolution),
    onSuccess: () => {
      queryClient.invalidateQueries(['security-incidents']);
      showNotification('Incident resolved successfully', 'success');
      setIncidentDialogOpen(false);
    },
  });

  const getSecurityColor = (score: number) => {
    if (score >= 90) return 'success';
    if (score >= 70) return 'warning';
    return 'error';
  };

  const getThreatLevelColor = (level: string) => {
    switch (level) {
      case 'CRITICAL':
        return 'error';
      case 'HIGH':
        return 'error';
      case 'MEDIUM':
        return 'warning';
      case 'LOW':
        return 'info';
      default:
        return 'default';
    }
  };

  const criticalThreats = activeThreats?.filter(t => t.severity === 'CRITICAL').length || 0;
  const highThreats = activeThreats?.filter(t => t.severity === 'HIGH').length || 0;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4" fontWeight="bold">
          Security Management
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => queryClient.invalidateQueries(['security-overview'])}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<Download />}
          >
            Export Report
          </Button>
          <Button
            variant="contained"
            color="error"
            startIcon={<Shield />}
          >
            Emergency Lockdown
          </Button>
        </Stack>
      </Box>

      {/* Security Alert */}
      {(criticalThreats > 0 || highThreats > 0) && (
        <Alert 
          severity="error" 
          sx={{ mb: 3 }}
          action={
            <Button color="inherit" size="small">
              View All Threats
            </Button>
          }
        >
          <AlertTitle>Active Security Threats Detected</AlertTitle>
          {criticalThreats > 0 && `${criticalThreats} critical threats require immediate attention. `}
          {highThreats > 0 && `${highThreats} high-severity threats detected.`}
        </Alert>
      )}

      {/* Security Metrics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Shield sx={{ fontSize: 40, color: theme.palette.primary.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Security Score
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {securityScore?.overall || 0}%
                  </Typography>
                </Box>
              </Box>
              <Chip
                label={securityScore?.overall >= 90 ? 'Excellent' : securityScore?.overall >= 70 ? 'Good' : 'Poor'}
                color={getSecurityColor(securityScore?.overall || 0)}
                size="small"
              />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: criticalThreats > 0 ? alpha(theme.palette.error.main, 0.1) : 'background.paper' }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Warning sx={{ fontSize: 40, color: theme.palette.error.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Active Threats
                  </Typography>
                  <Typography variant="h4" fontWeight="bold" color="error">
                    {activeThreats?.length || 0}
                  </Typography>
                </Box>
              </Box>
              <Stack direction="row" spacing={1}>
                <Chip label={`${criticalThreats} Critical`} size="small" color="error" />
                <Chip label={`${highThreats} High`} size="small" color="warning" />
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Block sx={{ fontSize: 40, color: theme.palette.warning.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Blocked IPs
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {securityOverview?.blockedIps || 0}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Last 24 hours
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <VerifiedUser sx={{ fontSize: 40, color: theme.palette.success.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    MFA Adoption
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {securityOverview?.mfaAdoption || 0}%
                  </Typography>
                </Box>
              </Box>
              <LinearProgress 
                variant="determinate" 
                value={securityOverview?.mfaAdoption || 0} 
                color="success"
                sx={{ height: 6, borderRadius: 3 }}
              />
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Quick Stats */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12}>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" spacing={3} alignItems="center" flexWrap="wrap">
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <BugReport sx={{ mr: 1, color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                  Failed Login Attempts: <strong>{securityOverview?.failedLogins || 0}</strong>
                </Typography>
              </Box>
              <Divider orientation="vertical" flexItem />
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <NetworkCheck sx={{ mr: 1, color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                  Suspicious Activities: <strong>{securityOverview?.suspiciousActivities || 0}</strong>
                </Typography>
              </Box>
              <Divider orientation="vertical" flexItem />
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <Key sx={{ mr: 1, color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                  API Keys Active: <strong>{securityOverview?.activeApiKeys || 0}</strong>
                </Typography>
              </Box>
              <Divider orientation="vertical" flexItem />
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <AccessTime sx={{ mr: 1, color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                  Last Security Scan: <strong>{securityOverview?.lastScan ? formatDistanceToNow(new Date(securityOverview.lastScan), { addSuffix: true }) : 'Never'}</strong>
                </Typography>
              </Box>
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      {/* Main Content Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          indicatorColor="primary"
          textColor="primary"
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Overview" icon={<Assessment />} />
          <Tab 
            label="Threat Monitor" 
            icon={
              <Badge badgeContent={activeThreats?.length || 0} color="error">
                <Warning />
              </Badge>
            }
          />
          <Tab label="Access Control" icon={<AdminPanelSettings />} />
          <Tab 
            label="Security Incidents" 
            icon={
              <Badge badgeContent={securityOverview?.openIncidents || 0} color="warning">
                <BugReport />
              </Badge>
            }
          />
          <Tab label="Vulnerability Scan" icon={<NetworkCheck />} />
          <Tab label="Audit Logs" icon={<Timeline />} />
          <Tab label="Encryption" icon={<LockOpen />} />
          <Tab label="MFA Settings" icon={<Fingerprint />} />
          <Tab label="Security Policies" icon={<SecurityIcon />} />
        </Tabs>
      </Paper>

      {/* Tab Panels */}
      <TabPanel value={selectedTab} index={0}>
        {/* Security Overview Dashboard */}
        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 3, height: 400 }}>
              <Typography variant="h6" gutterBottom>
                Security Events Timeline
              </Typography>
              <Box sx={{ height: 320 }}>
                {/* Security events timeline chart would go here */}
              </Box>
            </Paper>
          </Grid>
          
          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 3, height: 400 }}>
              <Typography variant="h6" gutterBottom>
                Recent Security Alerts
              </Typography>
              <List sx={{ maxHeight: 320, overflow: 'auto' }}>
                {securityOverview?.recentAlerts?.map((alert: any, index: number) => (
                  <ListItem key={index}>
                    <ListItemIcon>
                      <Warning color={getThreatLevelColor(alert.severity)} />
                    </ListItemIcon>
                    <ListItemText
                      primary={alert.message}
                      secondary={format(new Date(alert.timestamp), 'MMM dd, yyyy HH:mm')}
                    />
                  </ListItem>
                ))}
              </List>
            </Paper>
          </Grid>

          <Grid item xs={12} md={6}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                Login Activity by Location
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Location</TableCell>
                      <TableCell align="right">Successful</TableCell>
                      <TableCell align="right">Failed</TableCell>
                      <TableCell align="right">Blocked</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {securityOverview?.loginByLocation?.map((location: any) => (
                      <TableRow key={location.country}>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <LocationOn fontSize="small" sx={{ mr: 1 }} />
                            {location.country}
                          </Box>
                        </TableCell>
                        <TableCell align="right">{location.successful}</TableCell>
                        <TableCell align="right">{location.failed}</TableCell>
                        <TableCell align="right">{location.blocked}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          </Grid>

          <Grid item xs={12} md={6}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                Device Access Patterns
              </Typography>
              <List>
                {securityOverview?.deviceAccess?.map((device: any, index: number) => (
                  <ListItem key={index}>
                    <ListItemIcon>
                      <DevicesOther />
                    </ListItemIcon>
                    <ListItemText
                      primary={device.type}
                      secondary={`${device.count} devices, ${device.suspiciousCount} suspicious`}
                    />
                    <ListItemSecondaryAction>
                      <Chip
                        label={`${device.percentage}%`}
                        size="small"
                        color={device.suspiciousCount > 0 ? 'warning' : 'default'}
                      />
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            </Paper>
          </Grid>
        </Grid>
      </TabPanel>

      <TabPanel value={selectedTab} index={1}>
        <ThreatMonitor threats={activeThreats} />
      </TabPanel>

      <TabPanel value={selectedTab} index={2}>
        <AccessControlPanel />
      </TabPanel>

      <TabPanel value={selectedTab} index={3}>
        <SecurityIncidents />
      </TabPanel>

      <TabPanel value={selectedTab} index={4}>
        <VulnerabilityScanner />
      </TabPanel>

      <TabPanel value={selectedTab} index={5}>
        <AuditLog />
      </TabPanel>

      <TabPanel value={selectedTab} index={6}>
        <EncryptionStatus />
      </TabPanel>

      <TabPanel value={selectedTab} index={7}>
        <MFAManagement />
      </TabPanel>

      <TabPanel value={selectedTab} index={8}>
        <SecurityPolicies />
      </TabPanel>

      {/* Incident Resolution Dialog */}
      <Dialog
        open={incidentDialogOpen}
        onClose={() => setIncidentDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Resolve Security Incident</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Incident ID"
                value={selectedIncident?.id || ''}
                disabled
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Description"
                value={selectedIncident?.description || ''}
                disabled
                multiline
                rows={2}
              />
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Resolution Type</InputLabel>
                <Select value="" label="Resolution Type">
                  <MenuItem value="RESOLVED">Resolved - Threat Mitigated</MenuItem>
                  <MenuItem value="FALSE_POSITIVE">False Positive</MenuItem>
                  <MenuItem value="ACCEPTED_RISK">Accepted Risk</MenuItem>
                  <MenuItem value="ESCALATED">Escalated to Higher Authority</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={4}
                label="Resolution Details"
                placeholder="Describe the actions taken to resolve this incident..."
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIncidentDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => {}}>
            Resolve Incident
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Add missing imports
import { alpha } from '@mui/material/styles';

export default Security;