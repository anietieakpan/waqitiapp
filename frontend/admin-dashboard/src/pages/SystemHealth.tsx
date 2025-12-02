import React, { useState, useEffect } from 'react';
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
  LinearProgress,
  CircularProgress,
  Stack,
  Tooltip,
  Badge,
  Divider,
  useTheme,
  alpha,
} from '@mui/material';
import {
  Speed,
  Memory,
  Storage,
  NetworkCheck,
  Cloud,
  Dns,
  DataUsage,
  Error,
  CheckCircle,
  Warning,
  Refresh,
  Timeline,
  MoreVert,
  ArrowUpward,
  ArrowDownward,
  Hub,
  Api,
  Database,
  Queue,
  Cached,
  Timer,
  TrendingUp,
  Visibility,
  RestartAlt,
  Stop,
  PlayArrow,
  Settings,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format, formatDistanceToNow } from 'date-fns';
import { Line, Doughnut } from 'react-chartjs-2';

import { systemHealthService } from '../services/systemHealthService';
import ServiceStatus from '../components/systemHealth/ServiceStatus';
import ResourceMonitor from '../components/systemHealth/ResourceMonitor';
import NetworkMonitor from '../components/systemHealth/NetworkMonitor';
import DatabaseHealth from '../components/systemHealth/DatabaseHealth';
import CachePerformance from '../components/systemHealth/CachePerformance';
import QueueMetrics from '../components/systemHealth/QueueMetrics';
import ApiMetrics from '../components/systemHealth/ApiMetrics';
import InfrastructureMap from '../components/systemHealth/InfrastructureMap';
import { useNotification } from '../hooks/useNotification';
import { useSocket } from '../hooks/useSocket';

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

const SystemHealth: React.FC = () => {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const { showNotification } = useNotification();
  const socket = useSocket();
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [selectedService, setSelectedService] = useState<any>(null);
  const [actionDialogOpen, setActionDialogOpen] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);

  // Fetch system overview
  const { data: systemOverview, isLoading: overviewLoading } = useQuery({
    queryKey: ['system-overview'],
    queryFn: () => systemHealthService.getSystemOverview(),
    refetchInterval: autoRefresh ? 30000 : false, // Refresh every 30 seconds
  });

  // Fetch service statuses
  const { data: serviceStatuses, isLoading: servicesLoading } = useQuery({
    queryKey: ['service-statuses'],
    queryFn: () => systemHealthService.getServiceStatuses(),
    refetchInterval: autoRefresh ? 20000 : false, // Refresh every 20 seconds
  });

  // Fetch infrastructure metrics
  const { data: infrastructureMetrics } = useQuery({
    queryKey: ['infrastructure-metrics'],
    queryFn: () => systemHealthService.getInfrastructureMetrics(),
    refetchInterval: autoRefresh ? 60000 : false, // Refresh every minute
  });

  // Restart service mutation
  const restartServiceMutation = useMutation({
    mutationFn: (serviceId: string) => systemHealthService.restartService(serviceId),
    onSuccess: () => {
      queryClient.invalidateQueries(['service-statuses']);
      showNotification('Service restart initiated', 'info');
      setActionDialogOpen(false);
    },
  });

  // Scale service mutation
  const scaleServiceMutation = useMutation({
    mutationFn: ({ serviceId, replicas }: { serviceId: string; replicas: number }) =>
      systemHealthService.scaleService(serviceId, replicas),
    onSuccess: () => {
      queryClient.invalidateQueries(['service-statuses']);
      showNotification('Service scaling initiated', 'success');
    },
  });

  useEffect(() => {
    if (!socket) return;

    // Subscribe to real-time health updates
    socket.on('health:service-status', (data) => {
      queryClient.setQueryData(['service-statuses'], (old: any) => {
        if (!old) return old;
        return old.map((service: any) =>
          service.id === data.serviceId
            ? { ...service, ...data }
            : service
        );
      });
    });

    socket.on('health:alert', (alert) => {
      showNotification(alert.message, alert.severity);
    });

    return () => {
      socket.off('health:service-status');
      socket.off('health:alert');
    };
  }, [socket, queryClient, showNotification]);

  const getHealthColor = (health: string) => {
    switch (health) {
      case 'HEALTHY':
        return 'success';
      case 'DEGRADED':
        return 'warning';
      case 'UNHEALTHY':
        return 'error';
      default:
        return 'default';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'HEALTHY':
        return <CheckCircle color="success" />;
      case 'DEGRADED':
        return <Warning color="warning" />;
      case 'UNHEALTHY':
        return <Error color="error" />;
      default:
        return <Help color="disabled" />;
    }
  };

  const criticalServices = serviceStatuses?.filter(s => s.health === 'UNHEALTHY').length || 0;
  const degradedServices = serviceStatuses?.filter(s => s.health === 'DEGRADED').length || 0;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4" fontWeight="bold">
          System Health & Monitoring
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant={autoRefresh ? 'contained' : 'outlined'}
            startIcon={autoRefresh ? <Cached /> : <Refresh />}
            onClick={() => setAutoRefresh(!autoRefresh)}
          >
            {autoRefresh ? 'Auto-refresh ON' : 'Auto-refresh OFF'}
          </Button>
          <Button
            variant="outlined"
            startIcon={<Settings />}
          >
            Configure Alerts
          </Button>
          <Button
            variant="contained"
            startIcon={<Timeline />}
          >
            Health Report
          </Button>
        </Stack>
      </Box>

      {/* System Alert */}
      {(criticalServices > 0 || degradedServices > 0) && (
        <Alert 
          severity={criticalServices > 0 ? 'error' : 'warning'}
          sx={{ mb: 3 }}
          action={
            <Button color="inherit" size="small">
              View Details
            </Button>
          }
        >
          <AlertTitle>System Health Issues Detected</AlertTitle>
          {criticalServices > 0 && `${criticalServices} services are unhealthy. `}
          {degradedServices > 0 && `${degradedServices} services are degraded.`}
        </Alert>
      )}

      {/* Health Overview Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Speed sx={{ fontSize: 40, color: theme.palette.primary.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    System Health
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {systemOverview?.overallHealth || 0}%
                  </Typography>
                </Box>
              </Box>
              <LinearProgress
                variant="determinate"
                value={systemOverview?.overallHealth || 0}
                color={systemOverview?.overallHealth >= 90 ? 'success' : systemOverview?.overallHealth >= 70 ? 'warning' : 'error'}
                sx={{ height: 8, borderRadius: 4 }}
              />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Memory sx={{ fontSize: 40, color: theme.palette.info.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    CPU Usage
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {systemOverview?.cpuUsage || 0}%
                  </Typography>
                </Box>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                {systemOverview?.cpuTrend === 'up' ? (
                  <ArrowUpward color="error" fontSize="small" />
                ) : (
                  <ArrowDownward color="success" fontSize="small" />
                )}
                <Typography variant="body2" color="text.secondary" sx={{ ml: 0.5 }}>
                  {Math.abs(systemOverview?.cpuChange || 0)}% from last hour
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Storage sx={{ fontSize: 40, color: theme.palette.warning.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Memory Usage
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {systemOverview?.memoryUsage || 0}%
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {systemOverview?.memoryAvailable || 0} GB available
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Cloud sx={{ fontSize: 40, color: theme.palette.success.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Active Services
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {serviceStatuses?.filter(s => s.health === 'HEALTHY').length || 0}/{serviceStatuses?.length || 0}
                  </Typography>
                </Box>
              </Box>
              <Stack direction="row" spacing={1}>
                {criticalServices > 0 && (
                  <Chip label={`${criticalServices} Down`} size="small" color="error" />
                )}
                {degradedServices > 0 && (
                  <Chip label={`${degradedServices} Degraded`} size="small" color="warning" />
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Quick Infrastructure Stats */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Stack direction="row" spacing={3} alignItems="center" flexWrap="wrap">
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <NetworkCheck sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              Network Latency: <strong>{systemOverview?.networkLatency || 0}ms</strong>
            </Typography>
          </Box>
          <Divider orientation="vertical" flexItem />
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <DataUsage sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              Bandwidth: <strong>{systemOverview?.bandwidthUsage || 0} Mbps</strong>
            </Typography>
          </Box>
          <Divider orientation="vertical" flexItem />
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Database sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              DB Connections: <strong>{systemOverview?.dbConnections || 0}/{systemOverview?.dbMaxConnections || 100}</strong>
            </Typography>
          </Box>
          <Divider orientation="vertical" flexItem />
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Queue sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              Queue Depth: <strong>{systemOverview?.queueDepth || 0}</strong>
            </Typography>
          </Box>
          <Divider orientation="vertical" flexItem />
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Timer sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              Uptime: <strong>{systemOverview?.uptime || '0d 0h'}</strong>
            </Typography>
          </Box>
        </Stack>
      </Paper>

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
          <Tab label="Overview" icon={<Dashboard />} />
          <Tab 
            label="Services" 
            icon={
              <Badge badgeContent={criticalServices} color="error">
                <Cloud />
              </Badge>
            }
          />
          <Tab label="Resources" icon={<Memory />} />
          <Tab label="Network" icon={<NetworkCheck />} />
          <Tab label="Database" icon={<Database />} />
          <Tab label="Cache" icon={<Cached />} />
          <Tab label="Message Queue" icon={<Queue />} />
          <Tab label="API Performance" icon={<Api />} />
          <Tab label="Infrastructure" icon={<Hub />} />
        </Tabs>
      </Paper>

      {/* Tab Panels */}
      <TabPanel value={selectedTab} index={0}>
        {/* System Overview Dashboard */}
        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 3, height: 400 }}>
              <Typography variant="h6" gutterBottom>
                System Performance Trends
              </Typography>
              <Box sx={{ height: 320 }}>
                {/* Performance trends chart would go here */}
              </Box>
            </Paper>
          </Grid>
          
          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 3, height: 400 }}>
              <Typography variant="h6" gutterBottom>
                Resource Distribution
              </Typography>
              <Box sx={{ height: 320, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {/* Resource distribution doughnut chart would go here */}
              </Box>
            </Paper>
          </Grid>

          <Grid item xs={12}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                Service Health Matrix
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Service</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>CPU</TableCell>
                      <TableCell>Memory</TableCell>
                      <TableCell>Response Time</TableCell>
                      <TableCell>Error Rate</TableCell>
                      <TableCell>Uptime</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {serviceStatuses?.slice(0, 10).map((service: any) => (
                      <TableRow key={service.id}>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            {getStatusIcon(service.health)}
                            <Typography variant="body2" sx={{ ml: 1 }}>
                              {service.name}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={service.status}
                            size="small"
                            color={getHealthColor(service.health)}
                          />
                        </TableCell>
                        <TableCell>{service.cpu}%</TableCell>
                        <TableCell>{service.memory}%</TableCell>
                        <TableCell>{service.responseTime}ms</TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            color={service.errorRate > 5 ? 'error' : 'text.primary'}
                          >
                            {service.errorRate}%
                          </Typography>
                        </TableCell>
                        <TableCell>{service.uptime}</TableCell>
                        <TableCell align="right">
                          <IconButton
                            size="small"
                            onClick={() => {
                              setSelectedService(service);
                              setActionDialogOpen(true);
                            }}
                          >
                            <MoreVert />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          </Grid>
        </Grid>
      </TabPanel>

      <TabPanel value={selectedTab} index={1}>
        <ServiceStatus services={serviceStatuses} />
      </TabPanel>

      <TabPanel value={selectedTab} index={2}>
        <ResourceMonitor />
      </TabPanel>

      <TabPanel value={selectedTab} index={3}>
        <NetworkMonitor />
      </TabPanel>

      <TabPanel value={selectedTab} index={4}>
        <DatabaseHealth />
      </TabPanel>

      <TabPanel value={selectedTab} index={5}>
        <CachePerformance />
      </TabPanel>

      <TabPanel value={selectedTab} index={6}>
        <QueueMetrics />
      </TabPanel>

      <TabPanel value={selectedTab} index={7}>
        <ApiMetrics />
      </TabPanel>

      <TabPanel value={selectedTab} index={8}>
        <InfrastructureMap />
      </TabPanel>

      {/* Service Action Dialog */}
      <Dialog
        open={actionDialogOpen}
        onClose={() => setActionDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Service Actions - {selectedService?.name}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<RestartAlt />}
              onClick={() => restartServiceMutation.mutate(selectedService?.id)}
              disabled={restartServiceMutation.isLoading}
            >
              Restart Service
            </Button>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<Stop />}
              color="warning"
            >
              Stop Service
            </Button>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<PlayArrow />}
              color="success"
              disabled={selectedService?.status === 'RUNNING'}
            >
              Start Service
            </Button>
            <Divider />
            <Typography variant="subtitle2">Scale Service</Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                type="number"
                label="Replicas"
                defaultValue={selectedService?.replicas || 1}
                size="small"
                fullWidth
              />
              <Button variant="contained">Apply</Button>
            </Stack>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<Visibility />}
            >
              View Logs
            </Button>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Add missing imports
import { Help, Dashboard } from '@mui/icons-material';

export default SystemHealth;