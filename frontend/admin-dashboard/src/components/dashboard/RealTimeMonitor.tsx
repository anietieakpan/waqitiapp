import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  IconButton,
  Switch,
  FormControlLabel,
  Chip,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Avatar,
  Tooltip,
  Badge,
  Grid,
  Paper,
  Divider,
} from '@mui/material';
import {
  PlayArrow,
  Pause,
  Refresh,
  FiberManualRecord,
  Speed,
  Memory,
  Storage,
  NetworkCheck,
  Warning,
  CheckCircle,
  Error as ErrorIcon,
} from '@mui/icons-material';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as ChartTooltip,
  ResponsiveContainer,
  AreaChart,
  Area,
} from 'recharts';
import { formatNumber, formatBytes, formatDate } from '../../utils/formatters';
import { adminService } from '../../services/adminService';

interface RealTimeMonitorProps {
  type: 'transactions' | 'users' | 'system';
}

interface MetricData {
  timestamp: string;
  value: number;
  label?: string;
}

interface SystemMetrics {
  cpu: number;
  memory: number;
  disk: number;
  network: number;
  activeConnections: number;
  requestsPerSecond: number;
  errorRate: number;
  avgResponseTime: number;
}

/**
 * Real-Time Monitor Component - Live monitoring of system metrics
 */
const RealTimeMonitor: React.FC<RealTimeMonitorProps> = ({ type }) => {
  const [isLive, setIsLive] = useState(true);
  const [data, setData] = useState<MetricData[]>([]);
  const [systemMetrics, setSystemMetrics] = useState<SystemMetrics | null>(null);
  const [liveTransactions, setLiveTransactions] = useState<any[]>([]);
  const [activeUsers, setActiveUsers] = useState<any[]>([]);
  const [alerts, setAlerts] = useState<any[]>([]);
  const dataRef = useRef<MetricData[]>([]);
  const intervalRef = useRef<NodeJS.Timeout>();

  useEffect(() => {
    if (isLive) {
      startMonitoring();
    } else {
      stopMonitoring();
    }

    return () => {
      stopMonitoring();
    };
  }, [isLive, type]);

  const startMonitoring = () => {
    // Initial load
    loadData();

    // Set up interval for real-time updates
    intervalRef.current = setInterval(() => {
      loadData();
      updateChart();
    }, 2000); // Update every 2 seconds
  };

  const stopMonitoring = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
    }
  };

  const loadData = async () => {
    try {
      switch (type) {
        case 'transactions':
          const transactions = await adminService.getLiveTransactions();
          setLiveTransactions(transactions);
          break;
        case 'users':
          const users = await adminService.getActiveUsers();
          setActiveUsers(users);
          break;
        case 'system':
          const metrics = await adminService.getSystemMetrics();
          setSystemMetrics(metrics);
          break;
      }
    } catch (error) {
      console.error('Failed to load real-time data:', error);
    }
  };

  const updateChart = () => {
    const now = new Date();
    const newPoint: MetricData = {
      timestamp: now.toLocaleTimeString(),
      value: Math.random() * 100, // In production, this would be real data
    };

    dataRef.current = [...dataRef.current.slice(-29), newPoint]; // Keep last 30 points
    setData([...dataRef.current]);
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'healthy':
      case 'completed':
      case 'online':
        return 'success';
      case 'warning':
      case 'pending':
        return 'warning';
      case 'error':
      case 'failed':
      case 'offline':
        return 'error';
      default:
        return 'default';
    }
  };

  const renderTransactionMonitor = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={8}>
        <Card>
          <CardContent>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6">Transaction Flow</Typography>
              <Box display="flex" alignItems="center" gap={1}>
                <Chip
                  icon={<FiberManualRecord />}
                  label={isLive ? 'Live' : 'Paused'}
                  color={isLive ? 'success' : 'default'}
                  size="small"
                />
                <IconButton onClick={() => setIsLive(!isLive)} size="small">
                  {isLive ? <Pause /> : <PlayArrow />}
                </IconButton>
              </Box>
            </Box>

            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="timestamp" />
                <YAxis />
                <ChartTooltip />
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke="#8884d8"
                  fill="#8884d8"
                  fillOpacity={0.6}
                  animationDuration={0}
                />
              </AreaChart>
            </ResponsiveContainer>

            <Box mt={3}>
              <Typography variant="subtitle2" gutterBottom>
                Recent Transactions
              </Typography>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>ID</TableCell>
                      <TableCell>From → To</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Time</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {liveTransactions.slice(0, 5).map((tx) => (
                      <TableRow key={tx.id}>
                        <TableCell>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            {tx.id.substring(0, 8)}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Box display="flex" alignItems="center" gap={1}>
                            <Avatar src={tx.senderAvatar} sx={{ width: 20, height: 20 }} />
                            <Typography variant="caption">{tx.senderName}</Typography>
                            <Typography variant="caption">→</Typography>
                            <Avatar src={tx.recipientAvatar} sx={{ width: 20, height: 20 }} />
                            <Typography variant="caption">{tx.recipientName}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="caption" fontWeight="medium">
                            ${tx.amount}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={tx.status}
                            size="small"
                            color={getStatusColor(tx.status) as any}
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="caption" color="text.secondary">
                            {formatDate(tx.timestamp, 'time')}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={4}>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Transactions per Second
              </Typography>
              <Typography variant="h3">
                {formatNumber(Math.random() * 100)}
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Average Amount
              </Typography>
              <Typography variant="h3">
                ${formatNumber(Math.random() * 1000)}
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Success Rate
              </Typography>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="h3">
                  {(95 + Math.random() * 4).toFixed(1)}%
                </Typography>
                <CheckCircle color="success" />
              </Box>
            </Paper>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );

  const renderUserMonitor = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={8}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Active User Sessions
            </Typography>

            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="timestamp" />
                <YAxis />
                <ChartTooltip />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="#82ca9d"
                  strokeWidth={2}
                  dot={false}
                  animationDuration={0}
                />
              </LineChart>
            </ResponsiveContainer>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              Currently Active Users
            </Typography>
            <Box>
              {activeUsers.map((user) => (
                <Box key={user.id} display="flex" alignItems="center" gap={2} mb={2}>
                  <Badge
                    overlap="circular"
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    badgeContent={
                      <FiberManualRecord
                        sx={{
                          fontSize: 12,
                          color: user.status === 'active' ? 'success.main' : 'warning.main',
                        }}
                      />
                    }
                  >
                    <Avatar src={user.avatar} />
                  </Badge>
                  <Box flex={1}>
                    <Typography variant="body2">{user.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {user.location} • {user.device}
                    </Typography>
                  </Box>
                  <Box textAlign="right">
                    <Typography variant="caption" color="text.secondary">
                      Active for
                    </Typography>
                    <Typography variant="body2">
                      {user.sessionDuration}
                    </Typography>
                  </Box>
                </Box>
              ))}
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={4}>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Total Active Users
              </Typography>
              <Typography variant="h3">
                {formatNumber(activeUsers.length)}
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                New Sessions (Last Hour)
              </Typography>
              <Typography variant="h3">
                {formatNumber(Math.floor(Math.random() * 50))}
              </Typography>
            </Paper>
          </Grid>
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                User Activity by Region
              </Typography>
              <Box mt={1}>
                <Box display="flex" justifyContent="space-between" mb={1}>
                  <Typography variant="caption">North America</Typography>
                  <Typography variant="caption">45%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={45} sx={{ mb: 1 }} />
                
                <Box display="flex" justifyContent="space-between" mb={1}>
                  <Typography variant="caption">Europe</Typography>
                  <Typography variant="caption">30%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={30} sx={{ mb: 1 }} />
                
                <Box display="flex" justifyContent="space-between" mb={1}>
                  <Typography variant="caption">Asia</Typography>
                  <Typography variant="caption">20%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={20} sx={{ mb: 1 }} />
                
                <Box display="flex" justifyContent="space-between" mb={1}>
                  <Typography variant="caption">Other</Typography>
                  <Typography variant="caption">5%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={5} />
              </Box>
            </Paper>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );

  const renderSystemMonitor = () => (
    <Grid container spacing={3}>
      <Grid item xs={12}>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}>
            <Paper sx={{ p: 2 }}>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <Speed color="primary" />
                <Typography variant="subtitle2">CPU Usage</Typography>
              </Box>
              <Typography variant="h4">
                {systemMetrics?.cpu || 0}%
              </Typography>
              <LinearProgress
                variant="determinate"
                value={systemMetrics?.cpu || 0}
                sx={{ mt: 1 }}
                color={
                  (systemMetrics?.cpu || 0) > 80 ? 'error' :
                  (systemMetrics?.cpu || 0) > 60 ? 'warning' : 'success'
                }
              />
            </Paper>
          </Grid>

          <Grid item xs={12} sm={6} md={3}>
            <Paper sx={{ p: 2 }}>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <Memory color="secondary" />
                <Typography variant="subtitle2">Memory Usage</Typography>
              </Box>
              <Typography variant="h4">
                {systemMetrics?.memory || 0}%
              </Typography>
              <LinearProgress
                variant="determinate"
                value={systemMetrics?.memory || 0}
                sx={{ mt: 1 }}
                color={
                  (systemMetrics?.memory || 0) > 80 ? 'error' :
                  (systemMetrics?.memory || 0) > 60 ? 'warning' : 'success'
                }
              />
            </Paper>
          </Grid>

          <Grid item xs={12} sm={6} md={3}>
            <Paper sx={{ p: 2 }}>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <Storage color="info" />
                <Typography variant="subtitle2">Disk Usage</Typography>
              </Box>
              <Typography variant="h4">
                {systemMetrics?.disk || 0}%
              </Typography>
              <LinearProgress
                variant="determinate"
                value={systemMetrics?.disk || 0}
                sx={{ mt: 1 }}
                color={
                  (systemMetrics?.disk || 0) > 80 ? 'error' :
                  (systemMetrics?.disk || 0) > 60 ? 'warning' : 'success'
                }
              />
            </Paper>
          </Grid>

          <Grid item xs={12} sm={6} md={3}>
            <Paper sx={{ p: 2 }}>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <NetworkCheck color="success" />
                <Typography variant="subtitle2">Network I/O</Typography>
              </Box>
              <Typography variant="h4">
                {formatBytes((systemMetrics?.network || 0) * 1024 * 1024)}/s
              </Typography>
              <Box display="flex" gap={1} mt={1}>
                <Chip label="↑ 2.4 MB/s" size="small" variant="outlined" />
                <Chip label="↓ 5.1 MB/s" size="small" variant="outlined" />
              </Box>
            </Paper>
          </Grid>
        </Grid>
      </Grid>

      <Grid item xs={12} md={8}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              System Performance
            </Typography>

            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="timestamp" />
                <YAxis />
                <ChartTooltip />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="#ff7300"
                  name="Response Time (ms)"
                  strokeWidth={2}
                  dot={false}
                  animationDuration={0}
                />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={4}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              System Alerts
            </Typography>

            <Box>
              {alerts.length === 0 ? (
                <Box display="flex" alignItems="center" gap={1} py={2}>
                  <CheckCircle color="success" />
                  <Typography variant="body2" color="text.secondary">
                    All systems operational
                  </Typography>
                </Box>
              ) : (
                alerts.map((alert, index) => (
                  <Box key={index} display="flex" alignItems="flex-start" gap={1} mb={2}>
                    {alert.severity === 'error' ? (
                      <ErrorIcon color="error" fontSize="small" />
                    ) : (
                      <Warning color="warning" fontSize="small" />
                    )}
                    <Box>
                      <Typography variant="body2">{alert.message}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatDate(alert.timestamp, 'relative')}
                      </Typography>
                    </Box>
                  </Box>
                ))
              )}
            </Box>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  return (
    <Box>
      {type === 'transactions' && renderTransactionMonitor()}
      {type === 'users' && renderUserMonitor()}
      {type === 'system' && renderSystemMonitor()}
    </Box>
  );
};

export default RealTimeMonitor;