import React, { useState, useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  IconButton,
  Chip,
  LinearProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Avatar,
  AvatarGroup,
  Tooltip,
  Button,
  Stack,
  Divider,
  useTheme,
  alpha,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import PeopleIcon from '@mui/icons-material/People';
import ReceiptIcon from '@mui/icons-material/Receipt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ScheduleIcon from '@mui/icons-material/Schedule';
import DownloadIcon from '@mui/icons-material/Download';
import InfoIcon from '@mui/icons-material/Info';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import PieChartIcon from '@mui/icons-material/PieChart';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import TimelineIcon from '@mui/icons-material/Timeline';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import LoopIcon from '@mui/icons-material/Loop';
import PaymentIcon from '@mui/icons-material/Payment';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';;
import { format, subDays, startOfMonth, endOfMonth, eachDayOfInterval } from 'date-fns';

interface RequestAnalytics {
  totalRequests: number;
  totalAmount: number;
  averageAmount: number;
  successRate: number;
  averageResponseTime: number;
  topRequesters: Array<{
    id: string;
    name: string;
    avatar?: string;
    totalRequests: number;
    totalAmount: number;
  }>;
  requestsByStatus: {
    pending: number;
    paid: number;
    cancelled: number;
    expired: number;
  };
  requestsByType: {
    single: number;
    recurring: number;
    split: number;
    invoice: number;
  };
  dailyTrends: Array<{
    date: string;
    sent: number;
    received: number;
    amount: number;
  }>;
  monthlyComparison: {
    current: { requests: number; amount: number };
    previous: { requests: number; amount: number };
  };
}

const mockAnalytics: RequestAnalytics = {
  totalRequests: 248,
  totalAmount: 15420.50,
  averageAmount: 62.18,
  successRate: 82.5,
  averageResponseTime: 18.5,
  topRequesters: [
    { id: '1', name: 'John Doe', totalRequests: 45, totalAmount: 2850 },
    { id: '2', name: 'Jane Smith', totalRequests: 38, totalAmount: 2120 },
    { id: '3', name: 'Mike Johnson', totalRequests: 32, totalAmount: 1890 },
    { id: '4', name: 'Sarah Wilson', totalRequests: 28, totalAmount: 1560 },
    { id: '5', name: 'Tom Brown', totalRequests: 24, totalAmount: 1420 },
  ],
  requestsByStatus: {
    pending: 42,
    paid: 168,
    cancelled: 24,
    expired: 14,
  },
  requestsByType: {
    single: 156,
    recurring: 48,
    split: 32,
    invoice: 12,
  },
  dailyTrends: Array.from({ length: 30 }, (_, i) => ({
    date: format(subDays(new Date(), 29 - i), 'MMM dd'),
    sent: Math.floor(Math.random() * 15) + 5,
    received: Math.floor(Math.random() * 12) + 3,
    amount: Math.floor(Math.random() * 800) + 200,
  })),
  monthlyComparison: {
    current: { requests: 248, amount: 15420.50 },
    previous: { requests: 215, amount: 13280.75 },
  },
};

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <Box hidden={value !== index} pt={3}>
    {value === index && children}
  </Box>
);

const RequestAnalytics: React.FC = () => {
  const theme = useTheme();
  const [timeRange, setTimeRange] = useState('30d');
  const [activeTab, setActiveTab] = useState(0);
  const [viewType, setViewType] = useState<'sent' | 'received' | 'all'>('all');

  const analytics = mockAnalytics;

  const percentageChange = useMemo(() => {
    const current = analytics.monthlyComparison.current;
    const previous = analytics.monthlyComparison.previous;
    return {
      requests: ((current.requests - previous.requests) / previous.requests) * 100,
      amount: ((current.amount - previous.amount) / previous.amount) * 100,
    };
  }, [analytics]);

  const statusColors = {
    pending: theme.palette.warning.main,
    paid: theme.palette.success.main,
    cancelled: theme.palette.error.main,
    expired: theme.palette.grey[500],
  };

  const typeIcons = {
    single: <Payment />,
    recurring: <Loop />,
    split: <People />,
    invoice: <Receipt />,
  };

  const renderStatCard = (
    title: string,
    value: string | number,
    change?: number,
    icon?: React.ReactNode,
    color?: string
  ) => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {title}
            </Typography>
            <Typography variant="h5" fontWeight="bold">
              {value}
            </Typography>
            {change !== undefined && (
              <Box display="flex" alignItems="center" mt={1}>
                {change >= 0 ? (
                  <TrendingUp color="success" fontSize="small" />
                ) : (
                  <TrendingDown color="error" fontSize="small" />
                )}
                <Typography
                  variant="body2"
                  color={change >= 0 ? 'success.main' : 'error.main'}
                  ml={0.5}
                >
                  {Math.abs(change).toFixed(1)}%
                </Typography>
              </Box>
            )}
          </Box>
          {icon && (
            <Box
              sx={{
                p: 1,
                borderRadius: 2,
                backgroundColor: color ? alpha(color, 0.1) : alpha(theme.palette.primary.main, 0.1),
                color: color || theme.palette.primary.main,
              }}
            >
              {icon}
            </Box>
          )}
        </Box>
      </CardContent>
    </Card>
  );

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Request Analytics
        </Typography>
        <Stack direction="row" spacing={2}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>View</InputLabel>
            <Select
              value={viewType}
              onChange={(e) => setViewType(e.target.value as any)}
              label="View"
            >
              <MenuItem value="all">All</MenuItem>
              <MenuItem value="sent">Sent</MenuItem>
              <MenuItem value="received">Received</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Time Range</InputLabel>
            <Select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              label="Time Range"
            >
              <MenuItem value="7d">Last 7 days</MenuItem>
              <MenuItem value="30d">Last 30 days</MenuItem>
              <MenuItem value="90d">Last 90 days</MenuItem>
              <MenuItem value="1y">Last year</MenuItem>
            </Select>
          </FormControl>
          <Button
            variant="outlined"
            startIcon={<Download />}
            size="small"
          >
            Export
          </Button>
        </Stack>
      </Box>

      {/* Summary Stats */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          {renderStatCard(
            'Total Requests',
            analytics.totalRequests,
            percentageChange.requests,
            <RequestQuote />,
            theme.palette.primary.main
          )}
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          {renderStatCard(
            'Total Amount',
            `$${analytics.totalAmount.toLocaleString()}`,
            percentageChange.amount,
            <AttachMoney />,
            theme.palette.success.main
          )}
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          {renderStatCard(
            'Success Rate',
            `${analytics.successRate}%`,
            undefined,
            <CheckCircle />,
            theme.palette.info.main
          )}
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          {renderStatCard(
            'Avg Response Time',
            `${analytics.averageResponseTime}h`,
            undefined,
            <AccessTime />,
            theme.palette.warning.main
          )}
        </Grid>
      </Grid>

      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab label="Overview" icon={<ShowChart />} iconPosition="start" />
          <Tab label="Trends" icon={<Timeline />} iconPosition="start" />
          <Tab label="Top Users" icon={<People />} iconPosition="start" />
          <Tab label="Insights" icon={<PieChart />} iconPosition="start" />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          <Grid container spacing={3} p={3}>
            {/* Request Status Distribution */}
            <Grid item xs={12} md={6}>
              <Typography variant="h6" gutterBottom>
                Request Status Distribution
              </Typography>
              <Box>
                {Object.entries(analytics.requestsByStatus).map(([status, count]) => (
                  <Box key={status} mb={2}>
                    <Box display="flex" justifyContent="space-between" mb={1}>
                      <Typography variant="body2" textTransform="capitalize">
                        {status}
                      </Typography>
                      <Typography variant="body2" fontWeight="bold">
                        {count} ({((count / analytics.totalRequests) * 100).toFixed(1)}%)
                      </Typography>
                    </Box>
                    <LinearProgress
                      variant="determinate"
                      value={(count / analytics.totalRequests) * 100}
                      sx={{
                        height: 8,
                        borderRadius: 4,
                        backgroundColor: alpha(statusColors[status as keyof typeof statusColors], 0.2),
                        '& .MuiLinearProgress-bar': {
                          backgroundColor: statusColors[status as keyof typeof statusColors],
                          borderRadius: 4,
                        },
                      }}
                    />
                  </Box>
                ))}
              </Box>
            </Grid>

            {/* Request Type Distribution */}
            <Grid item xs={12} md={6}>
              <Typography variant="h6" gutterBottom>
                Request Type Distribution
              </Typography>
              <Grid container spacing={2}>
                {Object.entries(analytics.requestsByType).map(([type, count]) => (
                  <Grid item xs={6} key={type}>
                    <Card variant="outlined">
                      <CardContent>
                        <Box display="flex" alignItems="center" mb={1}>
                          {typeIcons[type as keyof typeof typeIcons]}
                          <Typography variant="subtitle2" ml={1} textTransform="capitalize">
                            {type}
                          </Typography>
                        </Box>
                        <Typography variant="h6" fontWeight="bold">
                          {count}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {((count / analytics.totalRequests) * 100).toFixed(1)}%
                        </Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </Grid>

            {/* Average Amount by Type */}
            <Grid item xs={12}>
              <Typography variant="h6" gutterBottom>
                Performance Metrics
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={4}>
                  <Card variant="outlined">
                    <CardContent>
                      <Box display="flex" alignItems="center" justifyContent="space-between">
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            Average Amount
                          </Typography>
                          <Typography variant="h6" fontWeight="bold">
                            ${analytics.averageAmount.toFixed(2)}
                          </Typography>
                        </Box>
                        <AttachMoney color="primary" />
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Card variant="outlined">
                    <CardContent>
                      <Box display="flex" alignItems="center" justifyContent="space-between">
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            Completion Rate
                          </Typography>
                          <Typography variant="h6" fontWeight="bold">
                            {((analytics.requestsByStatus.paid / analytics.totalRequests) * 100).toFixed(1)}%
                          </Typography>
                        </Box>
                        <CheckCircle color="success" />
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Card variant="outlined">
                    <CardContent>
                      <Box display="flex" alignItems="center" justifyContent="space-between">
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            Active Requests
                          </Typography>
                          <Typography variant="h6" fontWeight="bold">
                            {analytics.requestsByStatus.pending}
                          </Typography>
                        </Box>
                        <Schedule color="warning" />
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          <Box p={3}>
            <Typography variant="h6" gutterBottom>
              Daily Request Trends
            </Typography>
            {/* Simplified chart visualization */}
            <Box sx={{ height: 300, position: 'relative', mb: 4 }}>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'flex-end',
                  height: '100%',
                  gap: 0.5,
                }}
              >
                {analytics.dailyTrends.map((day, index) => (
                  <Tooltip
                    key={index}
                    title={
                      <Box>
                        <Typography variant="caption">{day.date}</Typography>
                        <Typography variant="caption" display="block">
                          Sent: {day.sent}
                        </Typography>
                        <Typography variant="caption" display="block">
                          Received: {day.received}
                        </Typography>
                        <Typography variant="caption" display="block">
                          Amount: ${day.amount}
                        </Typography>
                      </Box>
                    }
                  >
                    <Box
                      sx={{
                        flex: 1,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: 0.5,
                      }}
                    >
                      <Box
                        sx={{
                          width: '100%',
                          height: `${(day.sent / 20) * 100}%`,
                          backgroundColor: theme.palette.primary.main,
                          borderRadius: '4px 4px 0 0',
                          cursor: 'pointer',
                          transition: 'opacity 0.2s',
                          '&:hover': { opacity: 0.8 },
                        }}
                      />
                      <Box
                        sx={{
                          width: '100%',
                          height: `${(day.received / 20) * 100}%`,
                          backgroundColor: theme.palette.secondary.main,
                          borderRadius: '0 0 4px 4px',
                          cursor: 'pointer',
                          transition: 'opacity 0.2s',
                          '&:hover': { opacity: 0.8 },
                        }}
                      />
                    </Box>
                  </Tooltip>
                ))}
              </Box>
            </Box>

            <Box display="flex" justifyContent="center" gap={3} mb={4}>
              <Box display="flex" alignItems="center" gap={1}>
                <Box
                  sx={{
                    width: 16,
                    height: 16,
                    backgroundColor: theme.palette.primary.main,
                    borderRadius: 1,
                  }}
                />
                <Typography variant="body2">Sent Requests</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Box
                  sx={{
                    width: 16,
                    height: 16,
                    backgroundColor: theme.palette.secondary.main,
                    borderRadius: 1,
                  }}
                />
                <Typography variant="body2">Received Requests</Typography>
              </Box>
            </Box>

            <Divider sx={{ mb: 3 }} />

            <Typography variant="h6" gutterBottom>
              Monthly Comparison
            </Typography>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                      Request Volume
                    </Typography>
                    <Box display="flex" alignItems="baseline" gap={2}>
                      <Typography variant="h4" fontWeight="bold">
                        {analytics.monthlyComparison.current.requests}
                      </Typography>
                      <Box display="flex" alignItems="center">
                        {percentageChange.requests >= 0 ? (
                          <ArrowUpward color="success" fontSize="small" />
                        ) : (
                          <ArrowDownward color="error" fontSize="small" />
                        )}
                        <Typography
                          variant="body2"
                          color={percentageChange.requests >= 0 ? 'success.main' : 'error.main'}
                        >
                          {Math.abs(percentageChange.requests).toFixed(1)}%
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary" mt={1}>
                      vs. {analytics.monthlyComparison.previous.requests} last month
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={6}>
                <Card variant="outlined">
                  <CardContent>
                    <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                      Total Amount
                    </Typography>
                    <Box display="flex" alignItems="baseline" gap={2}>
                      <Typography variant="h4" fontWeight="bold">
                        ${analytics.monthlyComparison.current.amount.toLocaleString()}
                      </Typography>
                      <Box display="flex" alignItems="center">
                        {percentageChange.amount >= 0 ? (
                          <ArrowUpward color="success" fontSize="small" />
                        ) : (
                          <ArrowDownward color="error" fontSize="small" />
                        )}
                        <Typography
                          variant="body2"
                          color={percentageChange.amount >= 0 ? 'success.main' : 'error.main'}
                        >
                          {Math.abs(percentageChange.amount).toFixed(1)}%
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary" mt={1}>
                      vs. ${analytics.monthlyComparison.previous.amount.toLocaleString()} last month
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          <Box p={3}>
            <Typography variant="h6" gutterBottom>
              Top Requesters
            </Typography>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>User</TableCell>
                    <TableCell align="right">Requests</TableCell>
                    <TableCell align="right">Total Amount</TableCell>
                    <TableCell align="right">Average</TableCell>
                    <TableCell align="right">Success Rate</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {analytics.topRequesters.map((user) => (
                    <TableRow key={user.id}>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={2}>
                          <Avatar sx={{ width: 40, height: 40 }}>
                            {user.name.charAt(0)}
                          </Avatar>
                          <Typography variant="body2">{user.name}</Typography>
                        </Box>
                      </TableCell>
                      <TableCell align="right">{user.totalRequests}</TableCell>
                      <TableCell align="right">${user.totalAmount.toLocaleString()}</TableCell>
                      <TableCell align="right">
                        ${(user.totalAmount / user.totalRequests).toFixed(2)}
                      </TableCell>
                      <TableCell align="right">
                        <Chip
                          label={`${Math.floor(Math.random() * 20 + 75)}%`}
                          size="small"
                          color="success"
                          variant="outlined"
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <Box mt={4}>
              <Typography variant="h6" gutterBottom>
                Frequent Collaborators
              </Typography>
              <Grid container spacing={2}>
                {[
                  { users: ['John Doe', 'Jane Smith'], count: 24 },
                  { users: ['Mike Johnson', 'Sarah Wilson'], count: 18 },
                  { users: ['Tom Brown', 'Alex Davis'], count: 15 },
                ].map((collab, index) => (
                  <Grid item xs={12} md={4} key={index}>
                    <Card variant="outlined">
                      <CardContent>
                        <AvatarGroup max={2} sx={{ mb: 2 }}>
                          {collab.users.map((user) => (
                            <Avatar key={user} sx={{ width: 32, height: 32 }}>
                              {user.charAt(0)}
                            </Avatar>
                          ))}
                        </AvatarGroup>
                        <Typography variant="body2" gutterBottom>
                          {collab.users.join(' & ')}
                        </Typography>
                        <Typography variant="h6" fontWeight="bold">
                          {collab.count} requests
                        </Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </Box>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={3}>
          <Box p={3}>
            <Typography variant="h6" gutterBottom>
              Key Insights
            </Typography>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Box display="flex" alignItems="center" gap={2} mb={2}>
                      <Info color="primary" />
                      <Typography variant="subtitle1" fontWeight="medium">
                        Peak Request Times
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      Most requests are created between 12 PM - 2 PM and 6 PM - 8 PM
                    </Typography>
                    <Box display="flex" gap={1}>
                      <Chip label="Lunch Hours" size="small" />
                      <Chip label="After Work" size="small" />
                    </Box>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Box display="flex" alignItems="center" gap={2} mb={2}>
                      <TrendingUp color="success" />
                      <Typography variant="subtitle1" fontWeight="medium">
                        Growth Opportunity
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      Recurring requests show 40% higher completion rate than one-time requests
                    </Typography>
                    <Button variant="outlined" size="small">
                      Enable Recurring
                    </Button>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Box display="flex" alignItems="center" gap={2} mb={2}>
                      <Warning color="warning" />
                      <Typography variant="subtitle1" fontWeight="medium">
                        Attention Needed
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      14% of requests expire without action. Consider shorter expiration times
                    </Typography>
                    <Button variant="outlined" size="small" color="warning">
                      Adjust Settings
                    </Button>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Box display="flex" alignItems="center" gap={2} mb={2}>
                      <PersonAdd color="info" />
                      <Typography variant="subtitle1" fontWeight="medium">
                        User Behavior
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      Users who use templates complete 3x more requests per month
                    </Typography>
                    <Button variant="outlined" size="small" color="info">
                      View Templates
                    </Button>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Box mt={4}>
              <Typography variant="h6" gutterBottom>
                Recommendations
              </Typography>
              <Stack spacing={2}>
                <Alert severity="success" icon={<CheckCircle />}>
                  <Typography variant="body2">
                    Your request completion rate is above average. Keep up the good work!
                  </Typography>
                </Alert>
                <Alert severity="info" icon={<Info />}>
                  <Typography variant="body2">
                    Enable automated reminders to reduce request expiration by up to 25%
                  </Typography>
                </Alert>
                <Alert severity="warning" icon={<Schedule />}>
                  <Typography variant="body2">
                    Consider creating templates for your most frequent request types
                  </Typography>
                </Alert>
              </Stack>
            </Box>
          </Box>
        </TabPanel>
      </Paper>
    </Box>
  );
};

export default RequestAnalytics;