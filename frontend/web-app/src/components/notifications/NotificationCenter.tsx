import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  IconButton,
  Badge,
  Popover,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Typography,
  Button,
  Divider,
  Tab,
  Tabs,
  Chip,
  CircularProgress,
  Alert,
  useTheme,
  alpha,
  Paper,
  Tooltip,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import ClearIcon from '@mui/icons-material/Clear';
import DeleteIcon from '@mui/icons-material/Delete';
import SettingsIcon from '@mui/icons-material/Settings';
import RefreshIcon from '@mui/icons-material/Refresh';
import MarkAsUnreadIcon from '@mui/icons-material/MarkAsUnread';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';;
import { formatDistanceToNow, parseISO } from 'date-fns';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { notificationService } from '../../services/notificationService';
import { Notification } from '../../types/notification';

interface NotificationCenterProps {
  userId: string;
}

const NotificationCenter: React.FC<NotificationCenterProps> = ({ userId }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const wsRef = useRef<WebSocket | null>(null);
  
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [selectedTab, setSelectedTab] = useState(0);
  const [page, setPage] = useState(0);
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const open = Boolean(anchorEl);

  // Query for unread count
  const { data: unreadData, refetch: refetchUnreadCount } = useQuery(
    'unread-notifications-count',
    () => notificationService.getUnreadCount(),
    {
      refetchInterval: 30000, // Refetch every 30 seconds
    }
  );

  // Query for notifications
  const { data: notificationsData, isLoading, refetch } = useQuery(
    ['notifications', selectedTab, page],
    () => notificationService.getNotifications({
      page,
      limit: 20,
      unreadOnly: selectedTab === 1,
    }),
    {
      enabled: open,
      onSuccess: (data) => {
        if (page === 0) {
          setNotifications(data.content);
        } else {
          setNotifications(prev => [...prev, ...data.content]);
        }
      },
    }
  );

  // Mutations
  const markAsReadMutation = useMutation(
    (notificationId: string) => notificationService.markAsRead(notificationId),
    {
      onSuccess: () => {
        refetchUnreadCount();
        refetch();
        setNotifications(prev =>
          prev.map(n => n.id === arguments[0] ? { ...n, read: true } : n)
        );
      },
    }
  );

  const markAllAsReadMutation = useMutation(
    () => notificationService.markAllAsRead(),
    {
      onSuccess: () => {
        refetchUnreadCount();
        refetch();
        setNotifications(prev => prev.map(n => ({ ...n, read: true })));
        toast.success('All notifications marked as read');
      },
    }
  );

  const deleteNotificationMutation = useMutation(
    (notificationId: string) => notificationService.deleteNotification(notificationId),
    {
      onSuccess: () => {
        refetchUnreadCount();
        refetch();
        setNotifications(prev => prev.filter(n => n.id !== arguments[0]));
        toast.success('Notification deleted');
      },
    }
  );

  // WebSocket for real-time notifications
  useEffect(() => {
    if (userId) {
      wsRef.current = notificationService.connectToNotificationWebSocket(
        userId,
        (notification) => {
          // Add new notification to the list
          setNotifications(prev => [notification, ...prev]);
          refetchUnreadCount();
          
          // Show browser notification if permission is granted
          if (Notification.permission === 'granted') {
            const browserNotification = new Notification(notification.title, {
              body: notification.message,
              icon: '/favicon.ico',
              tag: notification.id,
            });
            
            browserNotification.onclick = () => {
              if (notification.actionUrl) {
                navigate(notification.actionUrl);
              }
              browserNotification.close();
            };
            
            setTimeout(() => browserNotification.close(), 5000);
          }
        }
      );
    }

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, [userId, navigate, refetchUnreadCount]);

  // Request notification permission on mount
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }, []);

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
    setPage(0);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setNotifications([]);
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setSelectedTab(newValue);
    setPage(0);
  };

  const handleNotificationClick = (notification: Notification) => {
    if (!notification.read) {
      markAsReadMutation.mutate(notification.id);
    }
    
    if (notification.actionUrl) {
      navigate(notification.actionUrl);
      handleClose();
    }
  };

  const handleMarkAsRead = (notificationId: string, event: React.MouseEvent) => {
    event.stopPropagation();
    markAsReadMutation.mutate(notificationId);
  };

  const handleDelete = (notificationId: string, event: React.MouseEvent) => {
    event.stopPropagation();
    deleteNotificationMutation.mutate(notificationId);
  };

  const handleLoadMore = () => {
    setPage(prev => prev + 1);
  };

  const getNotificationIcon = (type: string) => {
    const iconMap: Record<string, string> = {
      PAYMENT_RECEIVED: '💰',
      PAYMENT_SENT: '📤',
      PAYMENT_REQUEST: '💳',
      BILL_SPLIT: '🧾',
      FRIEND_REQUEST: '👥',
      SECURITY_ALERT: '🔒',
      SYSTEM: '⚙️',
    };
    return iconMap[type] || '📬';
  };

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'HIGH': return theme.palette.error.main;
      case 'MEDIUM': return theme.palette.warning.main;
      case 'LOW': return theme.palette.info.main;
      default: return theme.palette.text.secondary;
    }
  };

  const unreadCount = unreadData?.count || 0;
  const hasNotifications = notifications.length > 0;

  return (
    <>
      <Tooltip title="Notifications">
        <IconButton
          onClick={handleClick}
          color={unreadCount > 0 ? 'primary' : 'default'}
          aria-label="notifications"
        >
          <Badge badgeContent={unreadCount} color="error" max={99}>
            {unreadCount > 0 ? <NotificationsIcon /> : <NotificationsNoneIcon />}
          </Badge>
        </IconButton>
      </Tooltip>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        PaperProps={{
          sx: {
            width: 400,
            maxHeight: 600,
            mt: 1,
          },
        }}
      >
        <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="h6" fontWeight="bold">
              Notifications
            </Typography>
            <Box display="flex" gap={1}>
              <Tooltip title="Refresh">
                <IconButton size="small" onClick={() => refetch()}>
                  <RefreshIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Mark all as read">
                <IconButton
                  size="small"
                  onClick={() => markAllAsReadMutation.mutate()}
                  disabled={unreadCount === 0}
                >
                  <CheckCircleIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Settings">
                <IconButton
                  size="small"
                  onClick={() => {
                    navigate('/settings/notifications');
                    handleClose();
                  }}
                >
                  <SettingsIcon />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>

          <Tabs
            value={selectedTab}
            onChange={handleTabChange}
            variant="fullWidth"
            sx={{ mt: 1 }}
          >
            <Tab label="All" />
            <Tab label={`Unread (${unreadCount})`} />
          </Tabs>
        </Box>

        <Box sx={{ maxHeight: 400, overflow: 'auto' }}>
          {isLoading && page === 0 ? (
            <Box display="flex" justifyContent="center" p={3}>
              <CircularProgress size={24} />
            </Box>
          ) : !hasNotifications ? (
            <Box textAlign="center" py={4}>
              <NotificationsNoneIcon
                sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }}
              />
              <Typography variant="body2" color="text.secondary">
                {selectedTab === 1 ? 'No unread notifications' : 'No notifications yet'}
              </Typography>
            </Box>
          ) : (
            <List sx={{ p: 0 }}>
              {notifications.map((notification, index) => (
                <React.Fragment key={notification.id}>
                  <ListItem
                    button
                    onClick={() => handleNotificationClick(notification)}
                    sx={{
                      py: 1.5,
                      bgcolor: notification.read
                        ? 'transparent'
                        : alpha(theme.palette.primary.main, 0.04),
                      '&:hover': {
                        bgcolor: alpha(theme.palette.primary.main, 0.08),
                      },
                    }}
                  >
                    <ListItemAvatar>
                      <Avatar
                        sx={{
                          bgcolor: alpha(getPriorityColor(notification.priority || 'LOW'), 0.1),
                          color: getPriorityColor(notification.priority || 'LOW'),
                          fontSize: '1.2rem',
                        }}
                      >
                        {getNotificationIcon(notification.type)}
                      </Avatar>
                    </ListItemAvatar>
                    
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography
                            variant="subtitle2"
                            fontWeight={notification.read ? 'normal' : 'bold'}
                            sx={{ flex: 1 }}
                          >
                            {notification.title}
                          </Typography>
                          {!notification.read && (
                            <Box
                              sx={{
                                width: 8,
                                height: 8,
                                borderRadius: '50%',
                                bgcolor: 'primary.main',
                              }}
                            />
                          )}
                        </Box>
                      }
                      secondary={
                        <Box>
                          <Typography
                            variant="body2"
                            color="text.secondary"
                            sx={{
                              display: '-webkit-box',
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: 'vertical',
                              overflow: 'hidden',
                            }}
                          >
                            {notification.message}
                          </Typography>
                          <Box display="flex" alignItems="center" justifyContent="space-between" mt={0.5}>
                            <Typography variant="caption" color="text.secondary">
                              {formatDistanceToNow(parseISO(notification.createdAt), { addSuffix: true })}
                            </Typography>
                            {notification.priority === 'HIGH' && (
                              <Chip
                                label="High"
                                size="small"
                                color="error"
                                variant="outlined"
                                sx={{ height: 18, fontSize: '0.65rem' }}
                              />
                            )}
                          </Box>
                        </Box>
                      }
                    />

                    <ListItemSecondaryAction>
                      <Box display="flex" flexDirection="column" gap={0.5}>
                        {!notification.read && (
                          <Tooltip title="Mark as read">
                            <IconButton
                              edge="end"
                              size="small"
                              onClick={(e) => handleMarkAsRead(notification.id, e)}
                            >
                              <MarkAsUnreadIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title="Delete">
                          <IconButton
                            edge="end"
                            size="small"
                            onClick={(e) => handleDelete(notification.id, e)}
                          >
                            <DeleteIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </ListItemSecondaryAction>
                  </ListItem>
                  {index < notifications.length - 1 && <Divider />}
                </React.Fragment>
              ))}
            </List>
          )}
        </Box>

        {hasNotifications && notificationsData && notificationsData.totalPages > page + 1 && (
          <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
            <Button
              fullWidth
              variant="outlined"
              onClick={handleLoadMore}
              disabled={isLoading}
              startIcon={isLoading ? <CircularProgress size={16} /> : undefined}
            >
              {isLoading ? 'Loading...' : 'Load More'}
            </Button>
          </Box>
        )}

        {hasNotifications && (
          <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
            <Button
              fullWidth
              variant="text"
              onClick={() => {
                navigate('/notifications');
                handleClose();
              }}
            >
              View All Notifications
            </Button>
          </Box>
        )}
      </Popover>
    </>
  );
};

export default NotificationCenter;