import React, { useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Switch,
  FormControlLabel,
  FormGroup,
  Divider,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Chip,
  Alert,
  TextField,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  RadioGroup,
  Radio,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroid';
import PaymentIcon from '@mui/icons-material/Payment';
import SecurityIcon from '@mui/icons-material/Security';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import CampaignIcon from '@mui/icons-material/Campaign';
import ScheduleIcon from '@mui/icons-material/Schedule';
import VolumeOffIcon from '@mui/icons-material/VolumeOff';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';;
import { TimePicker } from '@mui/x-date-pickers/TimePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import toast from 'react-hot-toast';

interface NotificationChannel {
  email: boolean;
  sms: boolean;
  push: boolean;
}

interface NotificationSettings {
  transactions: NotificationChannel & {
    minAmount: number;
  };
  security: NotificationChannel;
  marketing: NotificationChannel;
  accountUpdates: NotificationChannel;
  paymentReminders: NotificationChannel;
  priceAlerts: NotificationChannel & {
    enabled: boolean;
    thresholds: Array<{
      id: string;
      currency: string;
      direction: 'above' | 'below';
      value: number;
    }>;
  };
  dailySummary: {
    enabled: boolean;
    time: Date | null;
    channels: NotificationChannel;
  };
  quietHours: {
    enabled: boolean;
    startTime: Date | null;
    endTime: Date | null;
    allowUrgent: boolean;
  };
}

const NotificationSettings: React.FC = () => {
  const [settings, setSettings] = useState<NotificationSettings>({
    transactions: {
      email: true,
      sms: false,
      push: true,
      minAmount: 0,
    },
    security: {
      email: true,
      sms: true,
      push: true,
    },
    marketing: {
      email: false,
      sms: false,
      push: false,
    },
    accountUpdates: {
      email: true,
      sms: false,
      push: true,
    },
    paymentReminders: {
      email: true,
      sms: false,
      push: true,
    },
    priceAlerts: {
      email: true,
      sms: false,
      push: true,
      enabled: false,
      thresholds: [],
    },
    dailySummary: {
      enabled: true,
      time: new Date('2024-01-01T09:00:00'),
      channels: {
        email: true,
        sms: false,
        push: false,
      },
    },
    quietHours: {
      enabled: false,
      startTime: new Date('2024-01-01T22:00:00'),
      endTime: new Date('2024-01-01T07:00:00'),
      allowUrgent: true,
    },
  });

  const [showPriceAlertDialog, setShowPriceAlertDialog] = useState(false);
  const [editingPriceAlert, setEditingPriceAlert] = useState<any>(null);
  const [testChannel, setTestChannel] = useState<'email' | 'sms' | 'push'>('email');

  const notificationCategories = [
    {
      id: 'transactions',
      title: 'Transaction Notifications',
      description: 'Get notified about payments and transfers',
      icon: <Payment />,
    },
    {
      id: 'security',
      title: 'Security Alerts',
      description: 'Important security and login notifications',
      icon: <Security />,
    },
    {
      id: 'accountUpdates',
      title: 'Account Updates',
      description: 'Changes to your account and profile',
      icon: <AccountBalance />,
    },
    {
      id: 'paymentReminders',
      title: 'Payment Reminders',
      description: 'Reminders for pending payments and requests',
      icon: <Schedule />,
    },
    {
      id: 'marketing',
      title: 'Marketing & Promotions',
      description: 'News, offers, and promotional content',
      icon: <Campaign />,
    },
  ];

  const handleToggleChannel = (
    category: keyof NotificationSettings,
    channel: keyof NotificationChannel
  ) => {
    setSettings(prev => ({
      ...prev,
      [category]: {
        ...prev[category as keyof NotificationSettings],
        [channel]: !(prev[category as keyof NotificationSettings] as any)[channel],
      },
    }));
  };

  const handleToggleAll = (channel: keyof NotificationChannel, enabled: boolean) => {
    const newSettings = { ...settings };
    notificationCategories.forEach(category => {
      if (category.id in newSettings) {
        (newSettings as any)[category.id][channel] = enabled;
      }
    });
    setSettings(newSettings);
    toast.success(`All ${channel} notifications ${enabled ? 'enabled' : 'disabled'}`);
  };

  const handleSaveSettings = () => {
    // In a real app, save to API
    toast.success('Notification settings saved');
  };

  const handleTestNotification = () => {
    toast.success(`Test ${testChannel} notification sent!`);
  };

  const handleAddPriceAlert = (alert: any) => {
    setSettings(prev => ({
      ...prev,
      priceAlerts: {
        ...prev.priceAlerts,
        thresholds: [
          ...prev.priceAlerts.thresholds,
          { ...alert, id: Date.now().toString() },
        ],
      },
    }));
    setShowPriceAlertDialog(false);
    toast.success('Price alert added');
  };

  const handleDeletePriceAlert = (id: string) => {
    setSettings(prev => ({
      ...prev,
      priceAlerts: {
        ...prev.priceAlerts,
        thresholds: prev.priceAlerts.thresholds.filter(t => t.id !== id),
      },
    }));
    toast.success('Price alert removed');
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDateFns}>
      <Box>
        <Typography variant="h5" gutterBottom>
          Notification Settings
        </Typography>
        <Typography variant="body2" color="text.secondary" paragraph>
          Manage how and when you receive notifications
        </Typography>

        {/* Quick Actions */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Quick Actions
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={4}>
                <Button
                  fullWidth
                  variant="outlined"
                  onClick={() => handleToggleAll('email', false)}
                  startIcon={<VolumeOff />}
                >
                  Disable All Email
                </Button>
              </Grid>
              <Grid item xs={12} sm={4}>
                <Button
                  fullWidth
                  variant="outlined"
                  onClick={() => handleToggleAll('sms', false)}
                  startIcon={<VolumeOff />}
                >
                  Disable All SMS
                </Button>
              </Grid>
              <Grid item xs={12} sm={4}>
                <Button
                  fullWidth
                  variant="outlined"
                  onClick={() => handleToggleAll('push', false)}
                  startIcon={<VolumeOff />}
                >
                  Disable All Push
                </Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* Notification Categories */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Notification Preferences
            </Typography>
            <List>
              {notificationCategories.map((category, index) => {
                const categorySettings = settings[category.id as keyof NotificationSettings] as NotificationChannel;
                return (
                  <React.Fragment key={category.id}>
                    <ListItem>
                      <ListItemIcon>{category.icon}</ListItemIcon>
                      <ListItemText
                        primary={category.title}
                        secondary={category.description}
                      />
                      <Box display="flex" gap={1}>
                        <Chip
                          label="Email"
                          color={categorySettings.email ? 'primary' : 'default'}
                          onClick={() => handleToggleChannel(category.id as any, 'email')}
                          icon={<Email />}
                          clickable
                        />
                        <Chip
                          label="SMS"
                          color={categorySettings.sms ? 'primary' : 'default'}
                          onClick={() => handleToggleChannel(category.id as any, 'sms')}
                          icon={<Sms />}
                          clickable
                        />
                        <Chip
                          label="Push"
                          color={categorySettings.push ? 'primary' : 'default'}
                          onClick={() => handleToggleChannel(category.id as any, 'push')}
                          icon={<PhoneAndroid />}
                          clickable
                        />
                      </Box>
                    </ListItem>
                    {category.id === 'transactions' && (
                      <Box sx={{ pl: 9, pr: 2, pb: 2 }}>
                        <TextField
                          label="Minimum amount for notifications"
                          type="number"
                          value={settings.transactions.minAmount}
                          onChange={(e) => setSettings(prev => ({
                            ...prev,
                            transactions: {
                              ...prev.transactions,
                              minAmount: parseFloat(e.target.value) || 0,
                            },
                          }))}
                          InputProps={{
                            startAdornment: '$',
                          }}
                          size="small"
                          sx={{ width: 200 }}
                        />
                      </Box>
                    )}
                    {index < notificationCategories.length - 1 && <Divider />}
                  </React.Fragment>
                );
              })}
            </List>
          </CardContent>
        </Card>

        {/* Price Alerts */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
              <Box>
                <Typography variant="h6">Price Alerts</Typography>
                <Typography variant="body2" color="text.secondary">
                  Get notified when exchange rates meet your criteria
                </Typography>
              </Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.priceAlerts.enabled}
                    onChange={(e) => setSettings(prev => ({
                      ...prev,
                      priceAlerts: {
                        ...prev.priceAlerts,
                        enabled: e.target.checked,
                      },
                    }))}
                  />
                }
                label="Enable"
              />
            </Box>
            
            {settings.priceAlerts.enabled && (
              <>
                <List>
                  {settings.priceAlerts.thresholds.map((alert) => (
                    <ListItem key={alert.id}>
                      <ListItemIcon>
                        <TrendingUp />
                      </ListItemIcon>
                      <ListItemText
                        primary={`${alert.currency} ${alert.direction} ${alert.value}`}
                        secondary={`Alert when ${alert.currency} goes ${alert.direction} ${alert.value}`}
                      />
                      <ListItemSecondaryAction>
                        <IconButton
                          edge="end"
                          onClick={() => handleDeletePriceAlert(alert.id)}
                        >
                          <Delete />
                        </IconButton>
                      </ListItemSecondaryAction>
                    </ListItem>
                  ))}
                </List>
                <Button
                  startIcon={<Add />}
                  onClick={() => setShowPriceAlertDialog(true)}
                >
                  Add Price Alert
                </Button>
              </>
            )}
          </CardContent>
        </Card>

        {/* Daily Summary */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
              <Box>
                <Typography variant="h6">Daily Summary</Typography>
                <Typography variant="body2" color="text.secondary">
                  Receive a daily summary of your account activity
                </Typography>
              </Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.dailySummary.enabled}
                    onChange={(e) => setSettings(prev => ({
                      ...prev,
                      dailySummary: {
                        ...prev.dailySummary,
                        enabled: e.target.checked,
                      },
                    }))}
                  />
                }
                label="Enable"
              />
            </Box>
            
            {settings.dailySummary.enabled && (
              <Grid container spacing={2} alignItems="center">
                <Grid item xs={12} sm={6}>
                  <TimePicker
                    label="Delivery time"
                    value={settings.dailySummary.time}
                    onChange={(newTime) => setSettings(prev => ({
                      ...prev,
                      dailySummary: {
                        ...prev.dailySummary,
                        time: newTime,
                      },
                    }))}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Box display="flex" gap={1}>
                    <Chip
                      label="Email"
                      color={settings.dailySummary.channels.email ? 'primary' : 'default'}
                      onClick={() => setSettings(prev => ({
                        ...prev,
                        dailySummary: {
                          ...prev.dailySummary,
                          channels: {
                            ...prev.dailySummary.channels,
                            email: !prev.dailySummary.channels.email,
                          },
                        },
                      }))}
                      clickable
                    />
                    <Chip
                      label="Push"
                      color={settings.dailySummary.channels.push ? 'primary' : 'default'}
                      onClick={() => setSettings(prev => ({
                        ...prev,
                        dailySummary: {
                          ...prev.dailySummary,
                          channels: {
                            ...prev.dailySummary.channels,
                            push: !prev.dailySummary.channels.push,
                          },
                        },
                      }))}
                      clickable
                    />
                  </Box>
                </Grid>
              </Grid>
            )}
          </CardContent>
        </Card>

        {/* Quiet Hours */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
              <Box>
                <Typography variant="h6">Quiet Hours</Typography>
                <Typography variant="body2" color="text.secondary">
                  Pause non-urgent notifications during specific hours
                </Typography>
              </Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.quietHours.enabled}
                    onChange={(e) => setSettings(prev => ({
                      ...prev,
                      quietHours: {
                        ...prev.quietHours,
                        enabled: e.target.checked,
                      },
                    }))}
                  />
                }
                label="Enable"
              />
            </Box>
            
            {settings.quietHours.enabled && (
              <>
                <Grid container spacing={2} alignItems="center">
                  <Grid item xs={12} sm={6}>
                    <TimePicker
                      label="Start time"
                      value={settings.quietHours.startTime}
                      onChange={(newTime) => setSettings(prev => ({
                        ...prev,
                        quietHours: {
                          ...prev.quietHours,
                          startTime: newTime,
                        },
                      }))}
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TimePicker
                      label="End time"
                      value={settings.quietHours.endTime}
                      onChange={(newTime) => setSettings(prev => ({
                        ...prev,
                        quietHours: {
                          ...prev.quietHours,
                          endTime: newTime,
                        },
                      }))}
                    />
                  </Grid>
                </Grid>
                <FormControlLabel
                  control={
                    <Switch
                      checked={settings.quietHours.allowUrgent}
                      onChange={(e) => setSettings(prev => ({
                        ...prev,
                        quietHours: {
                          ...prev.quietHours,
                          allowUrgent: e.target.checked,
                        },
                      }))}
                    />
                  }
                  label="Allow urgent security notifications"
                  sx={{ mt: 2 }}
                />
              </>
            )}
          </CardContent>
        </Card>

        {/* Test Notifications */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Test Notifications
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Send a test notification to verify your settings
            </Typography>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} sm={6}>
                <FormControl fullWidth size="small">
                  <InputLabel>Channel</InputLabel>
                  <Select
                    value={testChannel}
                    onChange={(e) => setTestChannel(e.target.value as any)}
                    label="Channel"
                  >
                    <MenuItem value="email">Email</MenuItem>
                    <MenuItem value="sms">SMS</MenuItem>
                    <MenuItem value="push">Push Notification</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Button
                  variant="outlined"
                  onClick={handleTestNotification}
                  fullWidth
                >
                  Send Test Notification
                </Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* Save Button */}
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            variant="contained"
            onClick={handleSaveSettings}
            startIcon={<Check />}
            size="large"
          >
            Save Settings
          </Button>
        </Box>

        {/* Price Alert Dialog */}
        <Dialog
          open={showPriceAlertDialog}
          onClose={() => setShowPriceAlertDialog(false)}
          maxWidth="sm"
          fullWidth
        >
          <DialogTitle>Add Price Alert</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <FormControl fullWidth>
                  <InputLabel>Currency</InputLabel>
                  <Select
                    value={editingPriceAlert?.currency || 'EUR'}
                    label="Currency"
                  >
                    <MenuItem value="EUR">EUR</MenuItem>
                    <MenuItem value="GBP">GBP</MenuItem>
                    <MenuItem value="JPY">JPY</MenuItem>
                    <MenuItem value="CAD">CAD</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12}>
                <FormControl>
                  <RadioGroup
                    value={editingPriceAlert?.direction || 'above'}
                    row
                  >
                    <FormControlLabel value="above" control={<Radio />} label="Above" />
                    <FormControlLabel value="below" control={<Radio />} label="Below" />
                  </RadioGroup>
                </FormControl>
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Price"
                  type="number"
                  value={editingPriceAlert?.value || ''}
                  InputProps={{
                    startAdornment: '$',
                  }}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setShowPriceAlertDialog(false)}>
              Cancel
            </Button>
            <Button
              variant="contained"
              onClick={() => handleAddPriceAlert({
                currency: 'EUR',
                direction: 'above',
                value: 1.2,
              })}
            >
              Add Alert
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </LocalizationProvider>
  );
};

export default NotificationSettings;