import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  IconButton,
  Badge,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tabs,
  Tab,
  Menu,
  MenuItem,
  Divider,
  Alert,
  Switch,
  FormControlLabel,
  TextField,
  InputAdornment,
  Tooltip,
  Paper,
  Grid,
  LinearProgress,
  Skeleton,
  Checkbox,
  FormGroup,
  Collapse,
  Fade,
  Slide,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import NotificationsOffIcon from '@mui/icons-material/NotificationsOff';
import CircleIcon from '@mui/icons-material/Circle';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import PaymentIcon from '@mui/icons-material/Payment';
import SecurityIcon from '@mui/icons-material/Security';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import GavelIcon from '@mui/icons-material/Gavel';
import CampaignIcon from '@mui/icons-material/Campaign';
import SettingsIcon from '@mui/icons-material/Settings';
import DeleteIcon from '@mui/icons-material/Delete';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import CloseIcon from '@mui/icons-material/Close';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VolumeOffIcon from '@mui/icons-material/VolumeOff';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import ArchiveIcon from '@mui/icons-material/Archive';
import UnarchiveIcon from '@mui/icons-material/Unarchive';
import MarkIcon from '@mui/icons-material/Mark';
import RefreshIcon from '@mui/icons-material/Refresh';;
import { format, formatDistanceToNow, isToday, isYesterday, startOfDay } from 'date-fns';
import { useAuth } from '@/contexts/AuthContext';
import toast from 'react-hot-toast';

interface Notification {
  id: string;
  type: 'transaction' | 'security' | 'social' | 'system' | 'promotion' | 'alert';
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
  important: boolean;
  starred: boolean;
  archived: boolean;
  actionUrl?: string;
  metadata?: {
    amount?: number;
    currency?: string;
    transactionId?: string;
    userId?: string;
    userName?: string;
    severity?: 'low' | 'medium' | 'high' | 'critical';
  };
  actions?: {
    label: string;
    action: string;
    variant?: 'text' | 'outlined' | 'contained';
    color?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info';
  }[];
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`notification-tabpanel-${index}`}
      aria-labelledby={`notification-tab-${index}`}
      {...other}
    >
      {value === index && <Box>{children}</Box>}
    </div>
  );
}

const NotificationCenterEnhanced: React.FC = () => {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState(0);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [filteredNotifications, setFilteredNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<'newest' | 'oldest' | 'important' | 'unread'>('newest');
  const [filterType, setFilterType] = useState<string>('all');
  const [showSettings, setShowSettings] = useState(false);
  const [selectedNotifications, setSelectedNotifications] = useState<string[]>([]);
  const [menuAnchorEl, setMenuAnchorEl] = useState<null | HTMLElement>(null);
  const [bulkActionAnchorEl, setBulkActionAnchorEl] = useState<null | HTMLElement>(null);
  const [expandedNotifications, setExpandedNotifications] = useState<Set<string>>(new Set());
  const [realTimeEnabled, setRealTimeEnabled] = useState(true);
  
  // Notification preferences
  const [preferences, setPreferences] = useState({
    enablePush: true,
    enableEmail: true,
    enableSms: false,
    enableSound: true,
    quietHours: {
      enabled: true,
      start: '22:00',
      end: '08:00',
    },
    categories: {
      transaction: true,
      security: true,
      social: true,
      system: true,
      promotion: false,
      alert: true,
    },
  });

  // Mock notification data
  const mockNotifications: Notification[] = [
    {
      id: '1',
      type: 'transaction',
      title: 'Payment Received',
      message: 'You received $250.00 from John Smith for dinner split',
      timestamp: new Date(),
      read: false,
      important: false,
      starred: false,
      archived: false,
      metadata: {
        amount: 250,
        currency: 'USD',
        transactionId: 'TX123456',
        userName: 'John Smith',
      },
      actions: [
        { label: 'View Transaction', action: 'view_transaction', variant: 'outlined', color: 'primary' },
        { label: 'Send Thank You', action: 'send_thanks', variant: 'text', color: 'secondary' },
      ],
    },
    {
      id: '2',
      type: 'security',
      title: 'New Login Detected',
      message: 'Someone signed into your account from Chrome on Windows in New York, US',
      timestamp: new Date(Date.now() - 3600000),
      read: false,
      important: true,
      starred: false,
      archived: false,
      metadata: { severity: 'medium' },
      actions: [
        { label: 'Secure Account', action: 'secure_account', variant: 'contained', color: 'error' },
        { label: 'This Was Me', action: 'confirm_login', variant: 'outlined', color: 'success' },
      ],
    },
    {
      id: '3',
      type: 'social',
      title: 'Friend Request',
      message: 'Sarah Johnson wants to connect with you on Waqiti',
      timestamp: new Date(Date.now() - 7200000),
      read: true,
      important: false,
      starred: true,
      archived: false,
      metadata: { userName: 'Sarah Johnson', userId: 'user123' },
      actions: [
        { label: 'Accept', action: 'accept_friend', variant: 'contained', color: 'primary' },
        { label: 'Decline', action: 'decline_friend', variant: 'text', color: 'secondary' },
      ],
    },
    {
      id: '4',
      type: 'system',
      title: 'System Maintenance',
      message: 'Scheduled maintenance will occur tonight from 2:00 AM to 4:00 AM EST',
      timestamp: new Date(Date.now() - 86400000),
      read: true,
      important: false,
      starred: false,
      archived: false,
      metadata: { severity: 'low' },
    },
    {
      id: '5',
      type: 'promotion',
      title: 'Limited Time Offer',
      message: 'Earn 5% cashback on all transactions this week! Terms apply.',
      timestamp: new Date(Date.now() - 172800000),
      read: false,
      important: false,
      starred: false,
      archived: false,
      actions: [
        { label: 'Learn More', action: 'view_offer', variant: 'contained', color: 'success' },
        { label: 'Not Interested', action: 'dismiss_offer', variant: 'text', color: 'secondary' },
      ],
    },
    {
      id: '6',
      type: 'alert',
      title: 'Account Limit Reached',
      message: 'You have reached 90% of your monthly transaction limit ($4,500 of $5,000)',
      timestamp: new Date(Date.now() - 259200000),
      read: true,
      important: true,
      starred: false,
      archived: false,
      metadata: { severity: 'high' },
      actions: [
        { label: 'Increase Limit', action: 'increase_limit', variant: 'contained', color: 'warning' },
        { label: 'View Usage', action: 'view_usage', variant: 'outlined', color: 'primary' },
      ],
    },
  ];

  // Initialize notifications
  useEffect(() => {
    setLoading(true);
    // Simulate API call
    setTimeout(() => {
      setNotifications(mockNotifications);
      setLoading(false);
    }, 1000);
  }, []);

  // Real-time notifications simulation
  useEffect(() => {
    if (!realTimeEnabled) return;

    const interval = setInterval(() => {
      // Simulate receiving a new notification
      if (Math.random() < 0.1) { // 10% chance every 10 seconds
        const newNotification: Notification = {
          id: Date.now().toString(),
          type: 'transaction',
          title: 'Payment Sent',
          message: `You sent $${(Math.random() * 100 + 10).toFixed(2)} to a friend`,
          timestamp: new Date(),
          read: false,
          important: false,
          starred: false,
          archived: false,
        };
        
        setNotifications(prev => [newNotification, ...prev]);
        
        if (preferences.enableSound) {
          // Play notification sound (in a real app)
          console.log('🔔 Notification sound would play here');
        }
        
        if (preferences.enablePush) {
          toast.success('New notification received!');
        }
      }
    }, 10000);

    return () => clearInterval(interval);
  }, [realTimeEnabled, preferences.enableSound, preferences.enablePush]);

  // Filter and search notifications
  useEffect(() => {
    let filtered = notifications;

    // Apply type filter
    if (filterType !== 'all') {
      filtered = filtered.filter(notification => {
        switch (filterType) {
          case 'unread':
            return !notification.read;
          case 'starred':
            return notification.starred;
          case 'important':
            return notification.important;
          default:
            return notification.type === filterType;
        }
      });
    }

    // Apply search filter
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(notification =>
        notification.title.toLowerCase().includes(query) ||
        notification.message.toLowerCase().includes(query)
      );
    }

    // Apply sorting
    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'oldest':
          return a.timestamp.getTime() - b.timestamp.getTime();
        case 'important':
          return Number(b.important) - Number(a.important);
        case 'unread':
          return Number(!a.read) - Number(!b.read);
        default: // newest
          return b.timestamp.getTime() - a.timestamp.getTime();
      }
    });

    setFilteredNotifications(filtered);
  }, [notifications, filterType, searchQuery, sortBy]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleMarkAsRead = (notificationId: string) => {
    setNotifications(prev =>
      prev.map(notification =>
        notification.id === notificationId
          ? { ...notification, read: true }
          : notification
      )
    );
  };

  const handleToggleStar = (notificationId: string) => {
    setNotifications(prev =>
      prev.map(notification =>
        notification.id === notificationId
          ? { ...notification, starred: !notification.starred }
          : notification
      )
    );
  };

  const handleArchive = (notificationId: string) => {
    setNotifications(prev =>
      prev.map(notification =>
        notification.id === notificationId
          ? { ...notification, archived: true }
          : notification
      )
    );
    toast.success('Notification archived');
  };

  const handleDelete = (notificationId: string) => {
    setNotifications(prev => prev.filter(n => n.id !== notificationId));
    toast.success('Notification deleted');
  };

  const handleBulkAction = (action: string) => {
    const selectedIds = selectedNotifications;
    
    switch (action) {
      case 'mark_read':
        setNotifications(prev =>
          prev.map(notification =>
            selectedIds.includes(notification.id)
              ? { ...notification, read: true }
              : notification
          )
        );
        break;
      case 'mark_unread':
        setNotifications(prev =>
          prev.map(notification =>
            selectedIds.includes(notification.id)
              ? { ...notification, read: false }
              : notification
          )
        );
        break;
      case 'archive':
        setNotifications(prev =>
          prev.map(notification =>
            selectedIds.includes(notification.id)
              ? { ...notification, archived: true }
              : notification
          )
        );
        break;
      case 'delete':
        setNotifications(prev => prev.filter(n => !selectedIds.includes(n.id)));
        break;
    }
    
    setSelectedNotifications([]);
    setBulkActionAnchorEl(null);
    toast.success(`${selectedIds.length} notifications updated`);
  };

  const handleMarkAllAsRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    toast.success('All notifications marked as read');
  };

  const handleNotificationAction = (notificationId: string, action: string) => {
    console.log(`Executing action ${action} for notification ${notificationId}`);
    
    switch (action) {
      case 'view_transaction':
        // Navigate to transaction details
        break;
      case 'secure_account':
        // Navigate to security settings
        break;
      case 'accept_friend':
        // Accept friend request
        handleDelete(notificationId);
        toast.success('Friend request accepted');
        break;
      case 'decline_friend':
        // Decline friend request
        handleDelete(notificationId);
        toast.info('Friend request declined');
        break;
      default:
        toast.info(`Action ${action} executed`);
    }
  };

  const handleToggleExpanded = (notificationId: string) => {
    setExpandedNotifications(prev => {
      const newSet = new Set(prev);
      if (newSet.has(notificationId)) {
        newSet.delete(notificationId);
      } else {
        newSet.add(notificationId);
      }
      return newSet;
    });
  };

  const getNotificationIcon = (type: string) => {
    const icons: { [key: string]: React.ReactNode } = {
      transaction: <Payment />,
      security: <Security color="error" />,
      social: <PersonAdd color="primary" />,
      system: <Settings color="info" />,
      promotion: <Campaign color="success" />,
      alert: <Warning color="warning" />,
    };
    return icons[type] || <Notifications />;
  };

  const getNotificationColor = (notification: Notification) => {
    if (!notification.read) return 'rgba(25, 118, 210, 0.04)';
    if (notification.important) return 'rgba(211, 47, 47, 0.04)';
    return 'transparent';
  };

  const formatNotificationTime = (timestamp: Date) => {
    if (isToday(timestamp)) {
      return format(timestamp, 'HH:mm');
    } else if (isYesterday(timestamp)) {
      return 'Yesterday';
    } else {
      return format(timestamp, 'MMM dd');
    }
  };

  const getUnreadCount = () => notifications.filter(n => !n.read).length;
  const getImportantCount = () => notifications.filter(n => n.important && !n.read).length;

  const renderNotificationItem = (notification: Notification) => {
    const isExpanded = expandedNotifications.has(notification.id);
    const isSelected = selectedNotifications.includes(notification.id);

    return (
      <Card
        key={notification.id}
        sx={{
          mb: 1,
          bgcolor: getNotificationColor(notification),
          border: isSelected ? '2px solid primary.main' : '1px solid',
          borderColor: isSelected ? 'primary.main' : 'divider',
        }}
      >
        <CardContent sx={{ pb: '16px !important' }}>
          <Box display="flex" alignItems="flex-start">
            {/* Selection Checkbox */}
            <Checkbox
              size="small"
              checked={isSelected}
              onChange={(e) => {
                if (e.target.checked) {
                  setSelectedNotifications(prev => [...prev, notification.id]);
                } else {
                  setSelectedNotifications(prev => prev.filter(id => id !== notification.id));
                }
              }}
              sx={{ mt: -1, mr: 1 }}
            />

            {/* Notification Icon */}
            <Box sx={{ mr: 2, mt: 0.5 }}>
              {getNotificationIcon(notification.type)}
            </Box>

            {/* Content */}
            <Box flex={1} minWidth={0}>
              <Box display="flex" alignItems="flex-start" justifyContent="between" mb={1}>
                <Box flex={1} minWidth={0}>
                  <Box display="flex" alignItems="center" gap={1} mb={0.5}>
                    <Typography
                      variant="subtitle2"
                      sx={{
                        fontWeight: notification.read ? 'normal' : 'bold',
                        color: notification.read ? 'text.secondary' : 'text.primary',
                      }}
                    >
                      {notification.title}
                    </Typography>
                    {notification.important && (
                      <Chip label="Important" size="small" color="error" />
                    )}
                    {!notification.read && (
                      <Circle sx={{ fontSize: 8, color: 'primary.main' }} />
                    )}
                  </Box>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{
                      display: '-webkit-box',
                      WebkitLineClamp: isExpanded ? 'none' : 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      mb: 1,
                    }}
                  >
                    {notification.message}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatDistanceToNow(notification.timestamp, { addSuffix: true })}
                  </Typography>
                </Box>

                {/* Action Menu */}
                <Box display="flex" alignItems="center" gap={0.5}>
                  <Typography variant="caption" color="text.secondary">
                    {formatNotificationTime(notification.timestamp)}
                  </Typography>
                  <Tooltip title={notification.starred ? 'Unstar' : 'Star'}>
                    <IconButton
                      size="small"
                      onClick={() => handleToggleStar(notification.id)}
                    >
                      {notification.starred ? <Star color="warning" /> : <StarBorder />}
                    </IconButton>
                  </Tooltip>
                  <IconButton
                    size="small"
                    onClick={(e) => setMenuAnchorEl(e.currentTarget)}
                  >
                    <MoreVert />
                  </IconButton>
                </Box>
              </Box>

              {/* Notification Actions */}
              {notification.actions && notification.actions.length > 0 && (
                <Collapse in={isExpanded}>
                  <Box display="flex" gap={1} flexWrap="wrap" mt={2}>
                    {notification.actions.map((action, index) => (
                      <Button
                        key={index}
                        size="small"
                        variant={action.variant || 'text'}
                        color={action.color || 'primary'}
                        onClick={() => handleNotificationAction(notification.id, action.action)}
                      >
                        {action.label}
                      </Button>
                    ))}
                  </Box>
                </Collapse>
              )}

              {/* Expand/Collapse Button */}
              {(notification.message.length > 100 || (notification.actions && notification.actions.length > 0)) && (
                <Button
                  size="small"
                  startIcon={isExpanded ? <ExpandLess /> : <ExpandMore />}
                  onClick={() => handleToggleExpanded(notification.id)}
                  sx={{ mt: 1, p: 0, minWidth: 'auto' }}
                >
                  {isExpanded ? 'Show less' : 'Show more'}
                </Button>
              )}
            </Box>
          </Box>
        </CardContent>
      </Card>
    );
  };

  return (
    <Box>
      {/* Header */}
      <Box display="flex" alignItems="center" justifyContent="between" mb={3}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Notifications
          </Typography>
          <Box display="flex" alignItems="center" gap={2}>
            <Chip
              icon={<Badge badgeContent={getUnreadCount()} color="error"><Notifications /></Badge>}
              label={`${getUnreadCount()} unread`}
              variant="outlined"
            />
            {getImportantCount() > 0 && (
              <Chip
                icon={<Warning />}
                label={`${getImportantCount()} important`}
                color="error"
                size="small"
              />
            )}
          </Box>
        </Box>
        <Box display="flex" gap={1}>
          <IconButton onClick={() => setRealTimeEnabled(!realTimeEnabled)}>
            {realTimeEnabled ? <VolumeUp color="primary" /> : <VolumeOff />}
          </IconButton>
          <Button
            startIcon={<Settings />}
            onClick={() => setShowSettings(true)}
            variant="outlined"
          >
            Settings
          </Button>
          <Button
            startIcon={<MarkIcon />}
            onClick={handleMarkAllAsRead}
            variant="contained"
          >
            Mark All Read
          </Button>
        </Box>
      </Box>

      {/* Search and Filters */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} sm={6} md={4}>
              <TextField
                fullWidth
                size="small"
                placeholder="Search notifications..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search />
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>
            <Grid item xs={6} sm={3} md={2}>
              <TextField
                fullWidth
                select
                size="small"
                label="Filter"
                value={filterType}
                onChange={(e) => setFilterType(e.target.value)}
                SelectProps={{ native: true }}
              >
                <option value="all">All</option>
                <option value="unread">Unread</option>
                <option value="starred">Starred</option>
                <option value="important">Important</option>
                <option value="transaction">Transactions</option>
                <option value="security">Security</option>
                <option value="social">Social</option>
                <option value="system">System</option>
                <option value="promotion">Promotions</option>
                <option value="alert">Alerts</option>
              </TextField>
            </Grid>
            <Grid item xs={6} sm={3} md={2}>
              <TextField
                fullWidth
                select
                size="small"
                label="Sort by"
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as any)}
                SelectProps={{ native: true }}
              >
                <option value="newest">Newest</option>
                <option value="oldest">Oldest</option>
                <option value="important">Important</option>
                <option value="unread">Unread</option>
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              {selectedNotifications.length > 0 && (
                <Box display="flex" alignItems="center" gap={1}>
                  <Typography variant="body2">
                    {selectedNotifications.length} selected
                  </Typography>
                  <Button
                    size="small"
                    startIcon={<MoreVert />}
                    onClick={(e) => setBulkActionAnchorEl(e.currentTarget)}
                  >
                    Actions
                  </Button>
                  <Button
                    size="small"
                    onClick={() => setSelectedNotifications([])}
                  >
                    Clear
                  </Button>
                </Box>
              )}
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Notifications List */}
      <Box>
        {loading ? (
          <Box>
            {[...Array(5)].map((_, index) => (
              <Card key={index} sx={{ mb: 1 }}>
                <CardContent>
                  <Box display="flex" alignItems="center" gap={2}>
                    <Skeleton variant="circular" width={40} height={40} />
                    <Box flex={1}>
                      <Skeleton variant="text" width="60%" />
                      <Skeleton variant="text" width="40%" />
                      <Skeleton variant="text" width="20%" />
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            ))}
          </Box>
        ) : filteredNotifications.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Notifications sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              {searchQuery || filterType !== 'all' ? 'No notifications found' : 'No notifications yet'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {searchQuery || filterType !== 'all'
                ? 'Try adjusting your search or filters'
                : "You'll see notifications here when you have them"}
            </Typography>
          </Paper>
        ) : (
          <Box>
            {filteredNotifications.map(renderNotificationItem)}
          </Box>
        )}
      </Box>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchorEl}
        open={Boolean(menuAnchorEl)}
        onClose={() => setMenuAnchorEl(null)}
      >
        <MenuItem onClick={() => { /* Mark as read/unread logic */ setMenuAnchorEl(null); }}>
          <ListItemIcon><CheckCircle /></ListItemIcon>
          Mark as Read
        </MenuItem>
        <MenuItem onClick={() => { /* Star/unstar logic */ setMenuAnchorEl(null); }}>
          <ListItemIcon><Star /></ListItemIcon>
          Star
        </MenuItem>
        <MenuItem onClick={() => { /* Archive logic */ setMenuAnchorEl(null); }}>
          <ListItemIcon><Archive /></ListItemIcon>
          Archive
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => { /* Delete logic */ setMenuAnchorEl(null); }}>
          <ListItemIcon><Delete /></ListItemIcon>
          Delete
        </MenuItem>
      </Menu>

      {/* Bulk Actions Menu */}
      <Menu
        anchorEl={bulkActionAnchorEl}
        open={Boolean(bulkActionAnchorEl)}
        onClose={() => setBulkActionAnchorEl(null)}
      >
        <MenuItem onClick={() => handleBulkAction('mark_read')}>
          <ListItemIcon><CheckCircle /></ListItemIcon>
          Mark as Read
        </MenuItem>
        <MenuItem onClick={() => handleBulkAction('mark_unread')}>
          <ListItemIcon><Circle /></ListItemIcon>
          Mark as Unread
        </MenuItem>
        <MenuItem onClick={() => handleBulkAction('archive')}>
          <ListItemIcon><Archive /></ListItemIcon>
          Archive
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => handleBulkAction('delete')}>
          <ListItemIcon><Delete /></ListItemIcon>
          Delete
        </MenuItem>
      </Menu>

      {/* Settings Dialog */}
      <Dialog
        open={showSettings}
        onClose={() => setShowSettings(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          Notification Settings
          <IconButton
            onClick={() => setShowSettings(false)}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Typography variant="h6" gutterBottom>
            Delivery Methods
          </Typography>
          <FormGroup>
            <FormControlLabel
              control={
                <Switch
                  checked={preferences.enablePush}
                  onChange={(e) => setPreferences(prev => ({ ...prev, enablePush: e.target.checked }))}
                />
              }
              label="Browser push notifications"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={preferences.enableEmail}
                  onChange={(e) => setPreferences(prev => ({ ...prev, enableEmail: e.target.checked }))}
                />
              }
              label="Email notifications"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={preferences.enableSms}
                  onChange={(e) => setPreferences(prev => ({ ...prev, enableSms: e.target.checked }))}
                />
              }
              label="SMS notifications"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={preferences.enableSound}
                  onChange={(e) => setPreferences(prev => ({ ...prev, enableSound: e.target.checked }))}
                />
              }
              label="Sound alerts"
            />
          </FormGroup>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" gutterBottom>
            Notification Categories
          </Typography>
          <FormGroup>
            {Object.entries(preferences.categories).map(([category, enabled]) => (
              <FormControlLabel
                key={category}
                control={
                  <Switch
                    checked={enabled}
                    onChange={(e) => setPreferences(prev => ({
                      ...prev,
                      categories: { ...prev.categories, [category]: e.target.checked }
                    }))}
                  />
                }
                label={category.charAt(0).toUpperCase() + category.slice(1)}
              />
            ))}
          </FormGroup>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" gutterBottom>
            Quiet Hours
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={preferences.quietHours.enabled}
                onChange={(e) => setPreferences(prev => ({
                  ...prev,
                  quietHours: { ...prev.quietHours, enabled: e.target.checked }
                }))}
              />
            }
            label="Enable quiet hours"
          />
          {preferences.quietHours.enabled && (
            <Box display="flex" gap={2} mt={2}>
              <TextField
                label="Start time"
                type="time"
                value={preferences.quietHours.start}
                onChange={(e) => setPreferences(prev => ({
                  ...prev,
                  quietHours: { ...prev.quietHours, start: e.target.value }
                }))}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="End time"
                type="time"
                value={preferences.quietHours.end}
                onChange={(e) => setPreferences(prev => ({
                  ...prev,
                  quietHours: { ...prev.quietHours, end: e.target.value }
                }))}
                InputLabelProps={{ shrink: true }}
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowSettings(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => {
            setShowSettings(false);
            toast.success('Settings saved');
          }}>
            Save Settings
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default NotificationCenterEnhanced;