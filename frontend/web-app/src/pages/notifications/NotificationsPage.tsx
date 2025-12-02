import React, { useEffect, useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
  IconButton,
  Chip,
  Button,
  Tabs,
  Tab,
  Badge,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Menu,
  MenuItem,
  Switch,
  FormControlLabel,
  Divider,
  Avatar,
  Grid,
  Card,
  CardContent,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import PaymentIcon from '@mui/icons-material/Payment';
import SecurityIcon from '@mui/icons-material/Security';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DeleteIcon from '@mui/icons-material/Delete';
import MarkEmailReadIcon from '@mui/icons-material/MarkEmailRead';
import SettingsIcon from '@mui/icons-material/Settings';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import MonetizationOnIcon from '@mui/icons-material/MonetizationOn';
import ShieldIcon from '@mui/icons-material/Shield';;
import { useNotification } from '../../hooks/useNotification';
import { Notification, NotificationPriority, NotificationType } from '../../types/notification';
import { formatDate, formatTimeAgo } from '../../utils/formatters';

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
      {value === index && <Box sx={{ p: 0 }}>{children}</Box>}
    </div>
  );
}

const NotificationsPage: React.FC = () => {
  const {
    notifications,
    unreadCount,
    loading,
    preferences,
    fetchNotifications,
    markNotificationAsRead,
    markAllNotificationsAsRead,
    deleteNotificationById,
    fetchPreferences,
    updatePreferences,
    requestPushPermission,
    getUnreadNotifications,
    getNotificationsByType,
  } = useNotification();

  const [tabValue, setTabValue] = useState(0);
  const [selectedNotification, setSelectedNotification] = useState<Notification | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedNotificationId, setSelectedNotificationId] = useState<string | null>(null);

  useEffect(() => {
    fetchNotifications();
    fetchPreferences();
  }, [fetchNotifications, fetchPreferences]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleNotificationClick = (notification: Notification) => {
    if (!notification.read) {
      markNotificationAsRead(notification.id);
    }
    setSelectedNotification(notification);
    setDetailsOpen(true);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, notificationId: string) => {
    event.stopPropagation();
    setMenuAnchor(event.currentTarget);
    setSelectedNotificationId(notificationId);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
    setSelectedNotificationId(null);
  };

  const handleDelete = (notificationId: string) => {
    deleteNotificationById(notificationId);
    handleMenuClose();
  };

  const handleMarkAsRead = (notificationId: string) => {
    markNotificationAsRead(notificationId);
    handleMenuClose();
  };

  const getNotificationIcon = (type: NotificationType) => {
    switch (type) {
      case 'PAYMENT':
        return <MonetizationOn color="primary" />;
      case 'SECURITY':
        return <Shield color="error" />;
      case 'SYSTEM':
        return <Info color="info" />;
      case 'WALLET':
        return <AccountBalanceWallet color="success" />;
      default:
        return <Notifications />;
    }
  };

  const getPriorityColor = (priority: NotificationPriority) => {
    switch (priority) {
      case 'URGENT':
        return 'error';
      case 'HIGH':
        return 'warning';
      case 'MEDIUM':
        return 'info';
      case 'LOW':
        return 'default';
      default:
        return 'default';
    }
  };

  const getFilteredNotifications = () => {
    switch (tabValue) {
      case 0: // All
        return notifications;
      case 1: // Unread
        return getUnreadNotifications();
      case 2: // Payments
        return getNotificationsByType('PAYMENT');
      case 3: // Security
        return getNotificationsByType('SECURITY');
      default:
        return notifications;
    }
  };

  const notificationStats = {
    total: notifications.length,
    unread: unreadCount,
    payment: getNotificationsByType('PAYMENT').length,
    security: getNotificationsByType('SECURITY').length,
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" gutterBottom>
          Notifications
        </Typography>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button
            variant="outlined"
            startIcon={<Settings />}
            onClick={() => setSettingsOpen(true)}
          >
            Settings
          </Button>
          <Button
            variant="contained"
            startIcon={<MarkEmailRead />}
            onClick={markAllNotificationsAsRead}
            disabled={unreadCount === 0}
          >
            Mark All Read
          </Button>
        </Box>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Notifications
              </Typography>
              <Typography variant="h4">
                {notificationStats.total}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Unread
              </Typography>
              <Typography variant="h4" color="primary">
                {notificationStats.unread}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Payment Notifications
              </Typography>
              <Typography variant="h4" color="success.main">
                {notificationStats.payment}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Security Alerts
              </Typography>
              <Typography variant="h4" color="error.main">
                {notificationStats.security}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Paper sx={{ width: '100%' }}>
        {/* Tabs */}
        <Tabs value={tabValue} onChange={handleTabChange}>
          <Tab
            label={
              <Badge badgeContent={notificationStats.total} color="default">
                All
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={notificationStats.unread} color="error">
                Unread
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={notificationStats.payment} color="primary">
                Payments
              </Badge>
            }
          />
          <Tab
            label={
              <Badge badgeContent={notificationStats.security} color="error">
                Security
              </Badge>
            }
          />
        </Tabs>

        {/* Notification List */}
        <List>
          {getFilteredNotifications().length === 0 ? (
            <ListItem>
              <ListItemText
                primary="No notifications"
                secondary="You're all caught up!"
                sx={{ textAlign: 'center', py: 4 }}
              />
            </ListItem>
          ) : (
            getFilteredNotifications().map((notification, index) => (
              <React.Fragment key={notification.id}>
                <ListItem
                  button
                  onClick={() => handleNotificationClick(notification)}
                  sx={{
                    backgroundColor: notification.read ? 'inherit' : 'action.hover',
                    '&:hover': {
                      backgroundColor: 'action.selected',
                    },
                  }}
                >
                  <ListItemIcon>
                    <Avatar sx={{ bgcolor: 'background.paper' }}>
                      {getNotificationIcon(notification.type)}
                    </Avatar>
                  </ListItemIcon>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography
                          variant="subtitle1"
                          fontWeight={notification.read ? 'normal' : 'bold'}
                        >
                          {notification.title}
                        </Typography>
                        <Chip
                          label={notification.priority}
                          size="small"
                          color={getPriorityColor(notification.priority)}
                        />
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography variant="body2" color="text.secondary">
                          {notification.message}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatTimeAgo(notification.createdAt)}
                        </Typography>
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <IconButton
                      edge="end"
                      onClick={(e) => handleMenuOpen(e, notification.id)}
                    >
                      <MoreVert />
                    </IconButton>
                  </ListItemSecondaryAction>
                </ListItem>
                {index < getFilteredNotifications().length - 1 && <Divider />}
              </React.Fragment>
            ))
          )}
        </List>
      </Paper>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem
          onClick={() => selectedNotificationId && handleMarkAsRead(selectedNotificationId)}
        >
          <ListItemIcon>
            <MarkEmailRead fontSize="small" />
          </ListItemIcon>
          Mark as Read
        </MenuItem>
        <MenuItem
          onClick={() => selectedNotificationId && handleDelete(selectedNotificationId)}
        >
          <ListItemIcon>
            <Delete fontSize="small" />
          </ListItemIcon>
          Delete
        </MenuItem>
      </Menu>

      {/* Notification Details Dialog */}
      <Dialog
        open={detailsOpen}
        onClose={() => setDetailsOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          {selectedNotification && (
            <>
              {getNotificationIcon(selectedNotification.type)}
              {selectedNotification.title}
              <Chip
                label={selectedNotification.priority}
                size="small"
                color={getPriorityColor(selectedNotification.priority)}
              />
            </>
          )}
        </DialogTitle>
        <DialogContent>
          {selectedNotification && (
            <>
              <DialogContentText>
                {selectedNotification.message}
              </DialogContentText>
              <Box sx={{ mt: 2 }}>
                <Typography variant="caption" color="text.secondary">
                  Type: {selectedNotification.type}
                </Typography>
                <br />
                <Typography variant="caption" color="text.secondary">
                  Received: {formatDate(selectedNotification.createdAt)}
                </Typography>
                {selectedNotification.data && (
                  <>
                    <br />
                    <Typography variant="caption" color="text.secondary">
                      Additional Data: {JSON.stringify(selectedNotification.data)}
                    </Typography>
                  </>
                )}
              </Box>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Settings Dialog */}
      <Dialog
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Notification Settings</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 2 }}>
            <Typography variant="h6" gutterBottom>
              Push Notifications
            </Typography>
            <FormControlLabel
              control={
                <Switch
                  checked={preferences?.pushEnabled || false}
                  onChange={(e) =>
                    updatePreferences({ pushEnabled: e.target.checked })
                  }
                />
              }
              label="Enable push notifications"
            />
            
            <Typography variant="h6" gutterBottom sx={{ mt: 3 }}>
              Email Notifications
            </Typography>
            <FormControlLabel
              control={
                <Switch
                  checked={preferences?.emailEnabled || false}
                  onChange={(e) =>
                    updatePreferences({ emailEnabled: e.target.checked })
                  }
                />
              }
              label="Enable email notifications"
            />
            
            <Typography variant="h6" gutterBottom sx={{ mt: 3 }}>
              Notification Types
            </Typography>
            <FormControlLabel
              control={
                <Switch
                  checked={preferences?.paymentNotifications || false}
                  onChange={(e) =>
                    updatePreferences({ paymentNotifications: e.target.checked })
                  }
                />
              }
              label="Payment notifications"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={preferences?.securityNotifications || false}
                  onChange={(e) =>
                    updatePreferences({ securityNotifications: e.target.checked })
                  }
                />
              }
              label="Security notifications"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={preferences?.marketingNotifications || false}
                  onChange={(e) =>
                    updatePreferences({ marketingNotifications: e.target.checked })
                  }
                />
              }
              label="Marketing notifications"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSettingsOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              requestPushPermission();
              setSettingsOpen(false);
            }}
          >
            Save Settings
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default NotificationsPage;