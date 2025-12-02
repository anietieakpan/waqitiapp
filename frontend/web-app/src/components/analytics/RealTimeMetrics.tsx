import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Chip,
  Alert,
  IconButton,
  Tooltip,
  Switch,
  FormControlLabel,
  useTheme,
  Badge,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import PlayIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import SpeedIcon from '@mui/icons-material/Speed';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import TimelineIcon from '@mui/icons-material/Timeline';;
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as ChartTooltip,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import { formatCurrency, formatNumber, formatPercentage } from '../../utils/formatters';

interface RealTimeMetricsProps {
  autoRefresh?: boolean;
  refreshInterval?: number;
}

interface MetricValue {
  current: number;
  previous: number;
  trend: 'up' | 'down' | 'stable';
  change: number;
  timestamp: Date;
}

interface SystemHealth {
  cpu: number;
  memory: number;
  responseTime: number;
  errorRate: number;
  throughput: number;
  status: 'healthy' | 'warning' | 'critical';
}

interface AlertMessage {
  id: string;
  type: 'info' | 'warning' | 'error';
  message: string;
  timestamp: Date;
  acknowledged: boolean;
}

const RealTimeMetrics: React.FC<RealTimeMetricsProps> = ({
  autoRefresh = true,
  refreshInterval = 5000
}) => {
  const theme = useTheme();
  const intervalRef = useRef<NodeJS.Timeout>();
  
  const [isLive, setIsLive] = useState(autoRefresh);
  const [lastUpdate, setLastUpdate] = useState(new Date());
  const [alerts, setAlerts] = useState<AlertMessage[]>([]);
  
  // Real-time metrics state
  const [metrics, setMetrics] = useState({
    transactionsPerSecond: { current: 45.2, previous: 42.1, trend: 'up' as const, change: 7.4 },
    totalTransactions: { current: 28547, previous: 27892, trend: 'up' as const, change: 2.3 },
    revenue: { current: 2854750, previous: 2789200, trend: 'up' as const, change: 2.4 },
    successRate: { current: 0.9934, previous: 0.9928, trend: 'up' as const, change: 0.06 },
    averageAmount: { current: 125.50, previous: 128.20, trend: 'down' as const, change: -2.1 },
    activeUsers: { current: 1847, previous: 1792, trend: 'up' as const, change: 3.1 },
    errorRate: { current: 0.0066, previous: 0.0072, trend: 'down' as const, change: -8.3 },
    queueDepth: { current: 23, previous: 28, trend: 'down' as const, change: -17.9 }
  });

  const [systemHealth, setSystemHealth] = useState<SystemHealth>({
    cpu: 67.5,
    memory: 72.3,
    responseTime: 245,
    errorRate: 0.66,
    throughput: 1247,
    status: 'healthy'
  });

  const [timeSeriesData, setTimeSeriesData] = useState(() => {
    const now = Date.now();
    return Array.from({ length: 60 }, (_, i) => ({
      time: new Date(now - (59 - i) * 1000).toLocaleTimeString(),
      tps: Math.random() * 10 + 40,
      errorRate: Math.random() * 2 + 0.5,
      responseTime: Math.random() * 100 + 200,
      revenue: Math.random() * 50000 + 100000
    }));
  });

  useEffect(() => {
    if (isLive) {
      intervalRef.current = setInterval(() => {
        updateMetrics();
        updateTimeSeriesData();
        checkForAlerts();
        setLastUpdate(new Date());
      }, refreshInterval);
    } else {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [isLive, refreshInterval]);

  const updateMetrics = () => {
    setMetrics(prev => {
      const newMetrics = { ...prev };
      
      // Simulate metric updates with realistic variations
      Object.keys(newMetrics).forEach(key => {
        const metric = newMetrics[key as keyof typeof newMetrics];
        const variation = (Math.random() - 0.5) * 0.1; // ±5% variation
        const newValue = metric.current * (1 + variation);
        
        newMetrics[key as keyof typeof newMetrics] = {
          current: newValue,
          previous: metric.current,
          trend: newValue > metric.current ? 'up' : newValue < metric.current ? 'down' : 'stable',
          change: ((newValue - metric.current) / metric.current) * 100
        };
      });
      
      return newMetrics;
    });

    setSystemHealth(prev => ({
      ...prev,
      cpu: Math.max(30, Math.min(90, prev.cpu + (Math.random() - 0.5) * 5)),
      memory: Math.max(40, Math.min(85, prev.memory + (Math.random() - 0.5) * 3)),
      responseTime: Math.max(150, Math.min(500, prev.responseTime + (Math.random() - 0.5) * 20)),
      errorRate: Math.max(0.1, Math.min(5, prev.errorRate + (Math.random() - 0.5) * 0.3)),
      throughput: Math.max(800, Math.min(1500, prev.throughput + (Math.random() - 0.5) * 50))
    }));
  };

  const updateTimeSeriesData = () => {
    setTimeSeriesData(prev => {
      const newData = [...prev.slice(1)];
      newData.push({
        time: new Date().toLocaleTimeString(),
        tps: metrics.transactionsPerSecond.current,
        errorRate: metrics.errorRate.current * 100,
        responseTime: systemHealth.responseTime,
        revenue: metrics.revenue.current / 100 // Scale for display
      });
      return newData;
    });
  };

  const checkForAlerts = () => {
    const newAlerts: AlertMessage[] = [];

    // Check thresholds
    if (systemHealth.cpu > 80) {
      newAlerts.push({
        id: 'cpu-high',
        type: 'warning',
        message: `High CPU usage: ${systemHealth.cpu.toFixed(1)}%`,
        timestamp: new Date(),
        acknowledged: false
      });
    }

    if (metrics.errorRate.current > 0.01) {
      newAlerts.push({
        id: 'error-rate-high',
        type: 'error',
        message: `Error rate elevated: ${formatPercentage(metrics.errorRate.current)}`,
        timestamp: new Date(),
        acknowledged: false
      });
    }

    if (systemHealth.responseTime > 400) {
      newAlerts.push({
        id: 'latency-high',
        type: 'warning',
        message: `High response time: ${systemHealth.responseTime}ms`,
        timestamp: new Date(),
        acknowledged: false
      });
    }

    if (newAlerts.length > 0) {
      setAlerts(prev => [...prev.slice(-4), ...newAlerts].slice(-5)); // Keep last 5 alerts
    }
  };

  const toggleLiveMode = () => {
    setIsLive(!isLive);
  };

  const manualRefresh = () => {
    updateMetrics();
    updateTimeSeriesData();
    checkForAlerts();
    setLastUpdate(new Date());
  };

  const acknowledgeAlert = (alertId: string) => {
    setAlerts(prev => prev.map(alert => 
      alert.id === alertId ? { ...alert, acknowledged: true } : alert
    ));
  };

  const getHealthColor = (value: number, thresholds: { good: number; warning: number }) => {
    if (value <= thresholds.good) return 'success';
    if (value <= thresholds.warning) return 'warning';
    return 'error';
  };

  const MetricCard = ({ 
    title, 
    value, 
    format, 
    metric, 
    icon, 
    thresholds 
  }: { 
    title: string; 
    value: string; 
    format: 'number' | 'currency' | 'percentage'; 
    metric: MetricValue; 
    icon: React.ReactNode;
    thresholds?: { good: number; warning: number };
  }) => (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
          <Box display="flex" alignItems="center">
            {icon}
            <Typography variant="subtitle2" sx={{ ml: 1 }}>
              {title}
            </Typography>
          </Box>
          {metric.trend === 'up' ? (
            <TrendingUpIcon color="success" fontSize="small" />
          ) : metric.trend === 'down' ? (
            <TrendingDownIcon color="error" fontSize="small" />
          ) : (
            <TimelineIcon color="disabled" fontSize="small" />
          )}
        </Box>
        
        <Typography variant="h4" component="div" gutterBottom>
          {value}
        </Typography>
        
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Chip
            label={`${metric.change > 0 ? '+' : ''}${metric.change.toFixed(1)}%`}
            size="small"
            color={metric.trend === 'up' ? 'success' : metric.trend === 'down' ? 'error' : 'default'}
            variant="outlined"
          />
          {thresholds && (
            <Chip
              size="small"
              color={getHealthColor(metric.current, thresholds)}
              label={
                metric.current <= thresholds.good ? 'Good' :
                metric.current <= thresholds.warning ? 'Warning' : 'Critical'
              }
            />
          )}
        </Box>
      </CardContent>
    </Card>
  );

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">Real-time System Metrics</Typography>
        <Box display="flex" alignItems="center" gap={2}>
          <Typography variant="body2" color="textSecondary">
            Last updated: {lastUpdate.toLocaleTimeString()}
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={isLive}
                onChange={toggleLiveMode}
                color="primary"
              />
            }
            label="Live"
          />
          <Tooltip title="Refresh Now">
            <IconButton onClick={manualRefresh} size="small">
              <RefreshIcon />
            </IconButton>
          </Tooltip>
          {isLive ? (
            <Chip
              icon={<PlayIcon />}
              label="LIVE"
              color="success"
              size="small"
              variant="filled"
            />
          ) : (
            <Chip
              icon={<PauseIcon />}
              label="PAUSED"
              color="default"
              size="small"
              variant="outlined"
            />
          )}
        </Box>
      </Box>

      {/* Alerts */}
      {alerts.filter(a => !a.acknowledged).length > 0 && (
        <Box mb={3}>
          {alerts.filter(a => !a.acknowledged).map(alert => (
            <Alert
              key={alert.id}
              severity={alert.type}
              onClose={() => acknowledgeAlert(alert.id)}
              sx={{ mb: 1 }}
            >
              {alert.message}
            </Alert>
          ))}
        </Box>
      )}

      {/* Key Metrics */}
      <Grid container spacing={2} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Transactions/sec"
            value={metrics.transactionsPerSecond.current.toFixed(1)}
            format="number"
            metric={metrics.transactionsPerSecond}
            icon={<SpeedIcon color="primary" />}
            thresholds={{ good: 50, warning: 80 }}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Success Rate"
            value={formatPercentage(metrics.successRate.current)}
            format="percentage"
            metric={metrics.successRate}
            icon={<CheckCircleIcon color="success" />}
            thresholds={{ good: 0.995, warning: 0.99 }}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Error Rate"
            value={formatPercentage(metrics.errorRate.current)}
            format="percentage"
            metric={metrics.errorRate}
            icon={<ErrorIcon color="error" />}
            thresholds={{ good: 0.005, warning: 0.01 }}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Active Users"
            value={formatNumber(metrics.activeUsers.current)}
            format="number"
            metric={metrics.activeUsers}
            icon={<TrendingUpIcon color="info" />}
          />
        </Grid>
      </Grid>

      {/* System Health */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>System Health</Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Box mb={2}>
                    <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                      <Typography variant="body2">CPU Usage</Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {systemHealth.cpu.toFixed(1)}%
                      </Typography>
                    </Box>
                    <Box
                      height={8}
                      bgcolor="grey.200"
                      borderRadius={1}
                      position="relative"
                      overflow="hidden"
                    >
                      <Box
                        width={`${systemHealth.cpu}%`}
                        height="100%"
                        bgcolor={
                          systemHealth.cpu > 80 ? 'error.main' :
                          systemHealth.cpu > 60 ? 'warning.main' : 'success.main'
                        }
                      />
                    </Box>
                  </Box>
                  
                  <Box mb={2}>
                    <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                      <Typography variant="body2">Memory Usage</Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {systemHealth.memory.toFixed(1)}%
                      </Typography>
                    </Box>
                    <Box
                      height={8}
                      bgcolor="grey.200"
                      borderRadius={1}
                      position="relative"
                      overflow="hidden"
                    >
                      <Box
                        width={`${systemHealth.memory}%`}
                        height="100%"
                        bgcolor={
                          systemHealth.memory > 85 ? 'error.main' :
                          systemHealth.memory > 70 ? 'warning.main' : 'success.main'
                        }
                      />
                    </Box>
                  </Box>
                </Grid>
                
                <Grid item xs={6}>
                  <Box mb={2}>
                    <Typography variant="body2" color="textSecondary">Response Time</Typography>
                    <Typography variant="h6">{systemHealth.responseTime}ms</Typography>
                  </Box>
                  
                  <Box mb={2}>
                    <Typography variant="body2" color="textSecondary">Throughput</Typography>
                    <Typography variant="h6">{formatNumber(systemHealth.throughput)}/min</Typography>
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Live Transaction Stream</Typography>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={timeSeriesData.slice(-20)}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" hide />
                  <YAxis hide />
                  <ChartTooltip
                    labelFormatter={(label) => `Time: ${label}`}
                    formatter={(value, name) => [
                      name === 'tps' ? `${Number(value).toFixed(1)}/s` :
                      name === 'errorRate' ? `${Number(value).toFixed(2)}%` :
                      name === 'responseTime' ? `${Number(value).toFixed(0)}ms` :
                      formatNumber(value),
                      name === 'tps' ? 'TPS' :
                      name === 'errorRate' ? 'Error Rate' :
                      name === 'responseTime' ? 'Response Time' : 'Revenue'
                    ]}
                  />
                  <Line
                    type="monotone"
                    dataKey="tps"
                    stroke="#8884d8"
                    strokeWidth={2}
                    dot={false}
                    connectNulls
                  />
                  <ReferenceLine y={50} stroke="#ff0000" strokeDasharray="3 3" />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Detailed Metrics */}
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Transactions"
            value={formatNumber(metrics.totalTransactions.current)}
            format="number"
            metric={metrics.totalTransactions}
            icon={<TimelineIcon color="primary" />}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Revenue Today"
            value={formatCurrency(metrics.revenue.current)}
            format="currency"
            metric={metrics.revenue}
            icon={<TrendingUpIcon color="success" />}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Average Amount"
            value={formatCurrency(metrics.averageAmount.current)}
            format="currency"
            metric={metrics.averageAmount}
            icon={<SpeedIcon color="info" />}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Queue Depth"
            value={formatNumber(metrics.queueDepth.current)}
            format="number"
            metric={metrics.queueDepth}
            icon={<WarningIcon color="warning" />}
            thresholds={{ good: 50, warning: 100 }}
          />
        </Grid>
      </Grid>
    </Box>
  );
};

export default RealTimeMetrics;