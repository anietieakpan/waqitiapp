import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  CardActions,
  Button,
  IconButton,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormControlLabel,
  Switch,
  Divider,
  Alert,
  LinearProgress,
  Menu,
  Tabs,
  Tab,
  Tooltip,
  Badge,
  Stack,
  InputAdornment,
  Collapse,
  useTheme,
  alpha,
} from '@mui/material';
import LoopIcon from '@mui/icons-material/Loop';
import ScheduleIcon from '@mui/icons-material/Schedule';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import AddIcon from '@mui/icons-material/Add';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import StopIcon from '@mui/icons-material/Stop';
import HistoryIcon from '@mui/icons-material/History';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import PersonIcon from '@mui/icons-material/Person';
import NotificationsIcon from '@mui/icons-material/Notifications';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import SettingsIcon from '@mui/icons-material/Settings';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import TimerIcon from '@mui/icons-material/Timer';
import EventRepeatIcon from '@mui/icons-material/EventRepeat';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';;
import { format, addDays, addWeeks, addMonths, differenceInDays } from 'date-fns';

interface RecurringRequest {
  id: string;
  title: string;
  recipient: {
    id: string;
    name: string;
    email: string;
    avatar?: string;
  };
  amount: number;
  currency: string;
  frequency: 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'yearly';
  interval: number;
  startDate: string;
  endDate?: string;
  nextDueDate: string;
  status: 'active' | 'paused' | 'completed' | 'cancelled';
  totalPaid: number;
  totalRequests: number;
  successfulRequests: number;
  category?: string;
  note?: string;
  autoRemind: boolean;
  reminderDays: number;
  createdAt: string;
}

interface RecurringHistory {
  id: string;
  requestId: string;
  date: string;
  amount: number;
  status: 'sent' | 'paid' | 'failed' | 'cancelled';
  paidAt?: string;
  note?: string;
}

const mockRecurringRequests: RecurringRequest[] = [
  {
    id: 'rec_001',
    title: 'Monthly Rent',
    recipient: {
      id: 'user_001',
      name: 'John Landlord',
      email: 'john@property.com',
    },
    amount: 1500,
    currency: 'USD',
    frequency: 'monthly',
    interval: 1,
    startDate: '2024-01-01',
    nextDueDate: '2024-02-01',
    status: 'active',
    totalPaid: 1500,
    totalRequests: 1,
    successfulRequests: 1,
    category: 'Housing',
    autoRemind: true,
    reminderDays: 3,
    createdAt: '2024-01-01',
  },
  {
    id: 'rec_002',
    title: 'Weekly Cleaning Service',
    recipient: {
      id: 'user_002',
      name: 'Clean Pro Services',
      email: 'billing@cleanpro.com',
    },
    amount: 120,
    currency: 'USD',
    frequency: 'weekly',
    interval: 1,
    startDate: '2024-01-08',
    nextDueDate: '2024-01-22',
    status: 'active',
    totalPaid: 240,
    totalRequests: 2,
    successfulRequests: 2,
    category: 'Services',
    autoRemind: true,
    reminderDays: 1,
    createdAt: '2024-01-08',
  },
  {
    id: 'rec_003',
    title: 'Gym Membership',
    recipient: {
      id: 'user_003',
      name: 'FitLife Gym',
      email: 'membership@fitlife.com',
    },
    amount: 50,
    currency: 'USD',
    frequency: 'monthly',
    interval: 1,
    startDate: '2023-10-01',
    nextDueDate: '2024-02-01',
    status: 'paused',
    totalPaid: 200,
    totalRequests: 4,
    successfulRequests: 4,
    category: 'Health',
    autoRemind: false,
    reminderDays: 2,
    createdAt: '2023-10-01',
  },
];

const mockHistory: RecurringHistory[] = [
  {
    id: 'hist_001',
    requestId: 'rec_001',
    date: '2024-01-01',
    amount: 1500,
    status: 'paid',
    paidAt: '2024-01-01T10:30:00Z',
  },
  {
    id: 'hist_002',
    requestId: 'rec_002',
    date: '2024-01-15',
    amount: 120,
    status: 'paid',
    paidAt: '2024-01-15T14:20:00Z',
  },
  {
    id: 'hist_003',
    requestId: 'rec_002',
    date: '2024-01-08',
    amount: 120,
    status: 'paid',
    paidAt: '2024-01-08T09:15:00Z',
  },
];

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

const RecurringRequests: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedRequest, setSelectedRequest] = useState<RecurringRequest | null>(null);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [showEditDialog, setShowEditDialog] = useState(false);
  const [showHistoryDialog, setShowHistoryDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [expandedCard, setExpandedCard] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<'all' | 'active' | 'paused' | 'completed'>('all');

  const [newRequest, setNewRequest] = useState({
    title: '',
    recipientEmail: '',
    amount: 0,
    currency: 'USD',
    frequency: 'monthly' as RecurringRequest['frequency'],
    interval: 1,
    startDate: format(new Date(), 'yyyy-MM-dd'),
    endDate: '',
    category: '',
    note: '',
    autoRemind: true,
    reminderDays: 3,
  });

  const frequencyOptions = {
    daily: 'Daily',
    weekly: 'Weekly',
    biweekly: 'Bi-weekly',
    monthly: 'Monthly',
    quarterly: 'Quarterly',
    yearly: 'Yearly',
  };

  const categoryOptions = [
    'Housing',
    'Services',
    'Health',
    'Education',
    'Entertainment',
    'Business',
    'Personal',
    'Other',
  ];

  const getNextDueDate = (frequency: string, interval: number, lastDate: Date): Date => {
    switch (frequency) {
      case 'daily':
        return addDays(lastDate, interval);
      case 'weekly':
        return addWeeks(lastDate, interval);
      case 'biweekly':
        return addWeeks(lastDate, interval * 2);
      case 'monthly':
        return addMonths(lastDate, interval);
      case 'quarterly':
        return addMonths(lastDate, interval * 3);
      case 'yearly':
        return addMonths(lastDate, interval * 12);
      default:
        return lastDate;
    }
  };

  const getDaysUntilDue = (dueDate: string): number => {
    return differenceInDays(new Date(dueDate), new Date());
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'active':
        return 'success';
      case 'paused':
        return 'warning';
      case 'completed':
        return 'info';
      case 'cancelled':
        return 'error';
      default:
        return 'default';
    }
  };

  const getSuccessRate = (request: RecurringRequest): number => {
    if (request.totalRequests === 0) return 0;
    return (request.successfulRequests / request.totalRequests) * 100;
  };

  const filteredRequests = mockRecurringRequests.filter(request => 
    filterStatus === 'all' || request.status === filterStatus
  );

  const handleCreateRequest = () => {
    // Handle creating new recurring request
    setShowCreateDialog(false);
    setNewRequest({
      title: '',
      recipientEmail: '',
      amount: 0,
      currency: 'USD',
      frequency: 'monthly',
      interval: 1,
      startDate: format(new Date(), 'yyyy-MM-dd'),
      endDate: '',
      category: '',
      note: '',
      autoRemind: true,
      reminderDays: 3,
    });
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, request: RecurringRequest) => {
    setMenuAnchor(event.currentTarget);
    setSelectedRequest(request);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const renderRequestCard = (request: RecurringRequest) => {
    const daysUntilDue = getDaysUntilDue(request.nextDueDate);
    const successRate = getSuccessRate(request);
    const isExpanded = expandedCard === request.id;

    return (
      <Card key={request.id} sx={{ mb: 2 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="flex-start">
            <Box flex={1}>
              <Box display="flex" alignItems="center" gap={2} mb={1}>
                <Typography variant="h6">{request.title}</Typography>
                <Chip 
                  label={request.status} 
                  color={getStatusColor(request.status) as any}
                  size="small"
                />
                {request.category && (
                  <Chip label={request.category} size="small" variant="outlined" />
                )}
              </Box>

              <Box display="flex" alignItems="center" gap={2} mb={2}>
                <Avatar sx={{ width: 32, height: 32 }}>
                  {request.recipient.name.charAt(0)}
                </Avatar>
                <Box>
                  <Typography variant="body2">{request.recipient.name}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {request.recipient.email}
                  </Typography>
                </Box>
              </Box>

              <Grid container spacing={2}>
                <Grid item xs={6} sm={3}>
                  <Typography variant="caption" color="text.secondary">Amount</Typography>
                  <Typography variant="body1" fontWeight="bold">
                    ${request.amount} {request.currency}
                  </Typography>
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Typography variant="caption" color="text.secondary">Frequency</Typography>
                  <Typography variant="body1">
                    {frequencyOptions[request.frequency]}
                  </Typography>
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Typography variant="caption" color="text.secondary">Next Due</Typography>
                  <Typography variant="body1">
                    {format(new Date(request.nextDueDate), 'MMM dd, yyyy')}
                  </Typography>
                  {daysUntilDue <= 3 && daysUntilDue >= 0 && (
                    <Chip 
                      label={`${daysUntilDue} days`} 
                      color="warning" 
                      size="small" 
                      sx={{ mt: 0.5 }}
                    />
                  )}
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Typography variant="caption" color="text.secondary">Success Rate</Typography>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Typography variant="body1">{successRate.toFixed(0)}%</Typography>
                    <LinearProgress
                      variant="determinate"
                      value={successRate}
                      sx={{ 
                        width: 60, 
                        height: 6, 
                        borderRadius: 3,
                        backgroundColor: alpha(theme.palette.success.main, 0.2),
                        '& .MuiLinearProgress-bar': {
                          borderRadius: 3,
                          backgroundColor: theme.palette.success.main,
                        }
                      }}
                    />
                  </Box>
                </Grid>
              </Grid>

              <Collapse in={isExpanded}>
                <Box mt={3} pt={3} borderTop={1} borderColor="divider">
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <Stack spacing={1}>
                        <Box display="flex" justifyContent="space-between">
                          <Typography variant="body2" color="text.secondary">Total Paid:</Typography>
                          <Typography variant="body2" fontWeight="bold">
                            ${request.totalPaid.toLocaleString()}
                          </Typography>
                        </Box>
                        <Box display="flex" justifyContent="space-between">
                          <Typography variant="body2" color="text.secondary">Total Requests:</Typography>
                          <Typography variant="body2">{request.totalRequests}</Typography>
                        </Box>
                        <Box display="flex" justifyContent="space-between">
                          <Typography variant="body2" color="text.secondary">Started:</Typography>
                          <Typography variant="body2">
                            {format(new Date(request.startDate), 'MMM dd, yyyy')}
                          </Typography>
                        </Box>
                      </Stack>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Stack spacing={1}>
                        <Box display="flex" alignItems="center" gap={1}>
                          <NotificationsActive fontSize="small" color={request.autoRemind ? 'primary' : 'disabled'} />
                          <Typography variant="body2">
                            {request.autoRemind 
                              ? `Reminder ${request.reminderDays} days before` 
                              : 'Reminders disabled'}
                          </Typography>
                        </Box>
                        {request.endDate && (
                          <Box display="flex" justifyContent="space-between">
                            <Typography variant="body2" color="text.secondary">Ends:</Typography>
                            <Typography variant="body2">
                              {format(new Date(request.endDate), 'MMM dd, yyyy')}
                            </Typography>
                          </Box>
                        )}
                        {request.note && (
                          <Typography variant="body2" color="text.secondary">
                            Note: {request.note}
                          </Typography>
                        )}
                      </Stack>
                    </Grid>
                  </Grid>
                </Box>
              </Collapse>
            </Box>

            <Box display="flex" flexDirection="column" alignItems="flex-end" gap={1}>
              <IconButton onClick={(e) => handleMenuOpen(e, request)} size="small">
                <MoreVert />
              </IconButton>
              <IconButton 
                onClick={() => setExpandedCard(isExpanded ? null : request.id)}
                size="small"
              >
                {isExpanded ? <ExpandLess /> : <ExpandMore />}
              </IconButton>
            </Box>
          </Box>
        </CardContent>

        {request.status === 'active' && (
          <CardActions sx={{ px: 2, pb: 2 }}>
            <Button
              variant="contained"
              size="small"
              startIcon={<AttachMoney />}
              onClick={() => {
                setSelectedRequest(request);
                // Handle send request
              }}
            >
              Send Request
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<Pause />}
              onClick={() => {
                // Handle pause
              }}
            >
              Pause
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<History />}
              onClick={() => {
                setSelectedRequest(request);
                setShowHistoryDialog(true);
              }}
            >
              History
            </Button>
          </CardActions>
        )}

        {request.status === 'paused' && (
          <CardActions sx={{ px: 2, pb: 2 }}>
            <Button
              variant="contained"
              size="small"
              startIcon={<PlayArrow />}
              color="success"
              onClick={() => {
                // Handle resume
              }}
            >
              Resume
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<History />}
              onClick={() => {
                setSelectedRequest(request);
                setShowHistoryDialog(true);
              }}
            >
              History
            </Button>
          </CardActions>
        )}
      </Card>
    );
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Recurring Requests
        </Typography>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => setShowCreateDialog(true)}
        >
          New Recurring Request
        </Button>
      </Box>

      {/* Summary Cards */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Box
                  sx={{
                    p: 1,
                    borderRadius: 2,
                    backgroundColor: alpha(theme.palette.primary.main, 0.1),
                    color: theme.palette.primary.main,
                  }}
                >
                  <Loop />
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Active Recurring
                  </Typography>
                  <Typography variant="h6" fontWeight="bold">
                    {filteredRequests.filter(r => r.status === 'active').length}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Box
                  sx={{
                    p: 1,
                    borderRadius: 2,
                    backgroundColor: alpha(theme.palette.success.main, 0.1),
                    color: theme.palette.success.main,
                  }}
                >
                  <AttachMoney />
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Monthly Total
                  </Typography>
                  <Typography variant="h6" fontWeight="bold">
                    ${filteredRequests
                      .filter(r => r.status === 'active')
                      .reduce((sum, r) => {
                        if (r.frequency === 'monthly') return sum + r.amount;
                        if (r.frequency === 'weekly') return sum + (r.amount * 4);
                        if (r.frequency === 'biweekly') return sum + (r.amount * 2);
                        return sum;
                      }, 0)
                      .toLocaleString()}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Box
                  sx={{
                    p: 1,
                    borderRadius: 2,
                    backgroundColor: alpha(theme.palette.warning.main, 0.1),
                    color: theme.palette.warning.main,
                  }}
                >
                  <Schedule />
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Due This Week
                  </Typography>
                  <Typography variant="h6" fontWeight="bold">
                    {filteredRequests.filter(r => 
                      r.status === 'active' && getDaysUntilDue(r.nextDueDate) <= 7
                    ).length}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Box
                  sx={{
                    p: 1,
                    borderRadius: 2,
                    backgroundColor: alpha(theme.palette.info.main, 0.1),
                    color: theme.palette.info.main,
                  }}
                >
                  <TrendingUp />
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Success Rate
                  </Typography>
                  <Typography variant="h6" fontWeight="bold">
                    {(
                      filteredRequests.reduce((sum, r) => sum + getSuccessRate(r), 0) / 
                      filteredRequests.length || 0
                    ).toFixed(0)}%
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab label="All Recurring" />
          <Tab label="Upcoming" />
          <Tab label="Templates" />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          <Box p={2}>
            <FormControl size="small" sx={{ mb: 2 }}>
              <InputLabel>Status</InputLabel>
              <Select
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value as any)}
                label="Status"
              >
                <MenuItem value="all">All</MenuItem>
                <MenuItem value="active">Active</MenuItem>
                <MenuItem value="paused">Paused</MenuItem>
                <MenuItem value="completed">Completed</MenuItem>
              </Select>
            </FormControl>

            {filteredRequests.length === 0 ? (
              <Box textAlign="center" py={8}>
                <Loop sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                <Typography variant="h6" color="text.secondary" gutterBottom>
                  No recurring requests
                </Typography>
                <Typography variant="body2" color="text.secondary" mb={3}>
                  Set up recurring payment requests to automate regular payments
                </Typography>
                <Button
                  variant="contained"
                  startIcon={<Add />}
                  onClick={() => setShowCreateDialog(true)}
                >
                  Create First Request
                </Button>
              </Box>
            ) : (
              filteredRequests.map(renderRequestCard)
            )}
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          <Box p={2}>
            <Typography variant="h6" gutterBottom>
              Upcoming Requests
            </Typography>
            <List>
              {filteredRequests
                .filter(r => r.status === 'active')
                .sort((a, b) => new Date(a.nextDueDate).getTime() - new Date(b.nextDueDate).getTime())
                .slice(0, 5)
                .map((request) => {
                  const daysUntil = getDaysUntilDue(request.nextDueDate);
                  return (
                    <ListItem key={request.id} divider>
                      <ListItemAvatar>
                        <Avatar>{request.recipient.name.charAt(0)}</Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={request.title}
                        secondary={`To ${request.recipient.name} • $${request.amount}`}
                      />
                      <Box textAlign="right">
                        <Typography variant="body2">
                          {format(new Date(request.nextDueDate), 'MMM dd')}
                        </Typography>
                        <Chip
                          label={`${daysUntil} days`}
                          size="small"
                          color={daysUntil <= 3 ? 'warning' : 'default'}
                        />
                      </Box>
                    </ListItem>
                  );
                })}
            </List>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          <Box p={2}>
            <Typography variant="h6" gutterBottom>
              Suggested Templates
            </Typography>
            <Grid container spacing={2}>
              {[
                { title: 'Monthly Rent', icon: <CalendarMonth />, amount: 1500, frequency: 'monthly' },
                { title: 'Weekly Services', icon: <Loop />, amount: 100, frequency: 'weekly' },
                { title: 'Quarterly Subscription', icon: <AutoAwesome />, amount: 299, frequency: 'quarterly' },
              ].map((template, index) => (
                <Grid item xs={12} md={4} key={index}>
                  <Card variant="outlined">
                    <CardContent>
                      <Box display="flex" alignItems="center" gap={2} mb={2}>
                        {template.icon}
                        <Typography variant="subtitle1" fontWeight="medium">
                          {template.title}
                        </Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary" paragraph>
                        ${template.amount} {template.frequency}
                      </Typography>
                      <Button
                        variant="outlined"
                        size="small"
                        fullWidth
                        onClick={() => {
                          setNewRequest({
                            ...newRequest,
                            title: template.title,
                            amount: template.amount,
                            frequency: template.frequency as any,
                          });
                          setShowCreateDialog(true);
                        }}
                      >
                        Use Template
                      </Button>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Box>
        </TabPanel>
      </Paper>

      {/* Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => {
          setShowEditDialog(true);
          handleMenuClose();
        }}>
          <Edit sx={{ mr: 1 }} /> Edit
        </MenuItem>
        <MenuItem onClick={() => {
          // Handle duplicate
          handleMenuClose();
        }}>
          <ContentCopy sx={{ mr: 1 }} /> Duplicate
        </MenuItem>
        <MenuItem onClick={() => {
          // Handle share
          handleMenuClose();
        }}>
          <Share sx={{ mr: 1 }} /> Share
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => {
          // Handle delete
          handleMenuClose();
        }} sx={{ color: 'error.main' }}>
          <Delete sx={{ mr: 1 }} /> Delete
        </MenuItem>
      </Menu>

      {/* Create Dialog */}
      <Dialog
        open={showCreateDialog}
        onClose={() => setShowCreateDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create Recurring Request</DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={2}>
            <TextField
              label="Title"
              fullWidth
              value={newRequest.title}
              onChange={(e) => setNewRequest({ ...newRequest, title: e.target.value })}
            />
            
            <TextField
              label="Recipient Email"
              fullWidth
              value={newRequest.recipientEmail}
              onChange={(e) => setNewRequest({ ...newRequest, recipientEmail: e.target.value })}
            />

            <Grid container spacing={2}>
              <Grid item xs={8}>
                <TextField
                  label="Amount"
                  type="number"
                  fullWidth
                  value={newRequest.amount}
                  onChange={(e) => setNewRequest({ ...newRequest, amount: Number(e.target.value) })}
                  InputProps={{
                    startAdornment: <InputAdornment position="start">$</InputAdornment>,
                  }}
                />
              </Grid>
              <Grid item xs={4}>
                <FormControl fullWidth>
                  <InputLabel>Currency</InputLabel>
                  <Select
                    value={newRequest.currency}
                    onChange={(e) => setNewRequest({ ...newRequest, currency: e.target.value })}
                    label="Currency"
                  >
                    <MenuItem value="USD">USD</MenuItem>
                    <MenuItem value="EUR">EUR</MenuItem>
                    <MenuItem value="GBP">GBP</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
            </Grid>

            <Grid container spacing={2}>
              <Grid item xs={8}>
                <FormControl fullWidth>
                  <InputLabel>Frequency</InputLabel>
                  <Select
                    value={newRequest.frequency}
                    onChange={(e) => setNewRequest({ ...newRequest, frequency: e.target.value as any })}
                    label="Frequency"
                  >
                    {Object.entries(frequencyOptions).map(([value, label]) => (
                      <MenuItem key={value} value={value}>{label}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={4}>
                <TextField
                  label="Interval"
                  type="number"
                  fullWidth
                  value={newRequest.interval}
                  onChange={(e) => setNewRequest({ ...newRequest, interval: Number(e.target.value) })}
                  inputProps={{ min: 1 }}
                />
              </Grid>
            </Grid>

            <Grid container spacing={2}>
              <Grid item xs={6}>
                <TextField
                  label="Start Date"
                  type="date"
                  fullWidth
                  value={newRequest.startDate}
                  onChange={(e) => setNewRequest({ ...newRequest, startDate: e.target.value })}
                  InputLabelProps={{ shrink: true }}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  label="End Date (Optional)"
                  type="date"
                  fullWidth
                  value={newRequest.endDate}
                  onChange={(e) => setNewRequest({ ...newRequest, endDate: e.target.value })}
                  InputLabelProps={{ shrink: true }}
                />
              </Grid>
            </Grid>

            <FormControl fullWidth>
              <InputLabel>Category</InputLabel>
              <Select
                value={newRequest.category}
                onChange={(e) => setNewRequest({ ...newRequest, category: e.target.value })}
                label="Category"
              >
                {categoryOptions.map((cat) => (
                  <MenuItem key={cat} value={cat}>{cat}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              label="Note (Optional)"
              multiline
              rows={2}
              fullWidth
              value={newRequest.note}
              onChange={(e) => setNewRequest({ ...newRequest, note: e.target.value })}
            />

            <Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={newRequest.autoRemind}
                    onChange={(e) => setNewRequest({ ...newRequest, autoRemind: e.target.checked })}
                  />
                }
                label="Auto-remind recipient"
              />
              {newRequest.autoRemind && (
                <TextField
                  label="Remind days before"
                  type="number"
                  size="small"
                  value={newRequest.reminderDays}
                  onChange={(e) => setNewRequest({ ...newRequest, reminderDays: Number(e.target.value) })}
                  inputProps={{ min: 1, max: 30 }}
                  sx={{ ml: 3, width: 150 }}
                />
              )}
            </Box>

            <Alert severity="info">
              The recipient will receive a payment request on {frequencyOptions[newRequest.frequency].toLowerCase()} basis starting from {format(new Date(newRequest.startDate), 'MMM dd, yyyy')}.
            </Alert>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowCreateDialog(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreateRequest}>
            Create Request
          </Button>
        </DialogActions>
      </Dialog>

      {/* History Dialog */}
      <Dialog
        open={showHistoryDialog}
        onClose={() => setShowHistoryDialog(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            Request History
            {selectedRequest && (
              <Chip label={selectedRequest.title} />
            )}
          </Box>
        </DialogTitle>
        <DialogContent>
          <List>
            {mockHistory
              .filter(h => h.requestId === selectedRequest?.id)
              .map((history) => (
                <ListItem key={history.id} divider>
                  <ListItemText
                    primary={`$${history.amount}`}
                    secondary={format(new Date(history.date), 'MMM dd, yyyy')}
                  />
                  <Box textAlign="right">
                    <Chip
                      label={history.status}
                      color={history.status === 'paid' ? 'success' : 'error'}
                      size="small"
                    />
                    {history.paidAt && (
                      <Typography variant="caption" display="block" mt={0.5}>
                        Paid: {format(new Date(history.paidAt), 'MMM dd HH:mm')}
                      </Typography>
                    )}
                  </Box>
                </ListItem>
              ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowHistoryDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default RecurringRequests;