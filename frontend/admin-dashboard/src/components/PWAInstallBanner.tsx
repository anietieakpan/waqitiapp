import React, { useState } from 'react';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Card,
  CardContent,
  Collapse,
  IconButton,
  Snackbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Close,
  GetApp,
  PhoneAndroid,
  Update,
  Notifications,
  NotificationsOff,
} from '@mui/icons-material';
import { usePWA } from '../hooks/usePWA';

interface PWAInstallBannerProps {
  position?: 'top' | 'bottom';
  showUpdatePrompt?: boolean;
  showNotificationPrompt?: boolean;
}

const PWAInstallBanner: React.FC<PWAInstallBannerProps> = ({
  position = 'bottom',
  showUpdatePrompt = true,
  showNotificationPrompt = true,
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const {
    isInstallable,
    isInstalled,
    isUpdateAvailable,
    isOffline,
    installApp,
    updateApp,
    registerForNotifications,
    unregisterNotifications,
  } = usePWA();

  const [dismissed, setDismissed] = useState(() => {
    return localStorage.getItem('pwa-install-dismissed') === 'true';
  });
  
  const [updateDismissed, setUpdateDismissed] = useState(false);
  const [notificationPromptDismissed, setNotificationPromptDismissed] = useState(() => {
    return localStorage.getItem('notification-prompt-dismissed') === 'true';
  });
  
  const [installing, setInstalling] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [notificationStatus, setNotificationStatus] = useState<'idle' | 'granting' | 'granted' | 'denied'>('idle');

  const handleInstall = async () => {
    setInstalling(true);
    const success = await installApp();
    setInstalling(false);
    
    if (success) {
      setDismissed(true);
      localStorage.setItem('pwa-install-dismissed', 'true');
    }
  };

  const handleUpdate = async () => {
    setUpdating(true);
    await updateApp();
    setUpdating(false);
    setUpdateDismissed(true);
  };

  const handleDismiss = () => {
    setDismissed(true);
    localStorage.setItem('pwa-install-dismissed', 'true');
  };

  const handleNotificationToggle = async () => {
    if (notificationStatus === 'granted') {
      setNotificationStatus('idle');
      await unregisterNotifications();
    } else {
      setNotificationStatus('granting');
      const success = await registerForNotifications();
      setNotificationStatus(success ? 'granted' : 'denied');
      
      if (!success) {
        setTimeout(() => setNotificationStatus('idle'), 3000);
      }
    }
  };

  const handleNotificationPromptDismiss = () => {
    setNotificationPromptDismissed(true);
    localStorage.setItem('notification-prompt-dismissed', 'true');
  };

  // Don't show if already installed or dismissed
  const showInstallBanner = isInstallable && !isInstalled && !dismissed;
  const showUpdateBanner = isUpdateAvailable && showUpdatePrompt && !updateDismissed;
  const showNotificationBanner = isInstalled && 
    showNotificationPrompt && 
    !notificationPromptDismissed && 
    notificationStatus === 'idle' &&
    'Notification' in window;

  if (!showInstallBanner && !showUpdateBanner && !showNotificationBanner) {
    return null;
  }

  return (
    <>
      {/* Install Banner */}
      <Collapse in={showInstallBanner}>
        <Box
          sx={{
            position: 'fixed',
            [position]: 16,
            left: 16,
            right: 16,
            zIndex: theme.zIndex.snackbar,
            maxWidth: 400,
            mx: 'auto',
          }}
        >
          <Card elevation={8}>
            <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
              <Box display="flex" alignItems="center" gap={2}>
                <PhoneAndroid color="primary" />
                <Box flex={1}>
                  <Typography variant="subtitle2" fontWeight="bold">
                    Install Waqiti Admin
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Get the full app experience with offline access and notifications
                  </Typography>
                </Box>
                <IconButton size="small" onClick={handleDismiss}>
                  <Close fontSize="small" />
                </IconButton>
              </Box>
              
              <Box display="flex" gap={1} mt={2}>
                <Button
                  variant="contained"
                  startIcon={<GetApp />}
                  onClick={handleInstall}
                  disabled={installing}
                  size="small"
                  fullWidth={isMobile}
                >
                  {installing ? 'Installing...' : 'Install'}
                </Button>
                <Button
                  variant="text"
                  onClick={handleDismiss}
                  size="small"
                  sx={{ display: { xs: 'none', sm: 'inline-flex' } }}
                >
                  Not now
                </Button>
              </Box>
            </CardContent>
          </Card>
        </Box>
      </Collapse>

      {/* Update Snackbar */}
      <Snackbar
        open={showUpdateBanner}
        onClose={() => setUpdateDismissed(true)}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert
          severity="info"
          action={
            <Box display="flex" gap={1}>
              <Button
                color="inherit"
                size="small"
                startIcon={<Update />}
                onClick={handleUpdate}
                disabled={updating}
              >
                {updating ? 'Updating...' : 'Update'}
              </Button>
              <IconButton
                size="small"
                color="inherit"
                onClick={() => setUpdateDismissed(true)}
              >
                <Close fontSize="small" />
              </IconButton>
            </Box>
          }
        >
          <AlertTitle>App Update Available</AlertTitle>
          A new version of the admin dashboard is ready to install.
        </Alert>
      </Snackbar>

      {/* Notification Permission Banner */}
      <Collapse in={showNotificationBanner}>
        <Box
          sx={{
            position: 'fixed',
            top: 16,
            left: 16,
            right: 16,
            zIndex: theme.zIndex.snackbar,
            maxWidth: 400,
            mx: 'auto',
          }}
        >
          <Alert
            severity="info"
            action={
              <Box display="flex" gap={1}>
                <Button
                  color="inherit"
                  size="small"
                  startIcon={
                    notificationStatus === 'granted' ? <NotificationsOff /> : <Notifications />
                  }
                  onClick={handleNotificationToggle}
                  disabled={notificationStatus === 'granting'}
                >
                  {notificationStatus === 'granting' ? 'Enabling...' :
                   notificationStatus === 'granted' ? 'Disable' : 'Enable'}
                </Button>
                <IconButton
                  size="small"
                  color="inherit"
                  onClick={handleNotificationPromptDismiss}
                >
                  <Close fontSize="small" />
                </IconButton>
              </Box>
            }
          >
            <AlertTitle>Enable Notifications</AlertTitle>
            Stay updated with important admin alerts and system notifications.
          </Alert>
        </Box>
      </Collapse>

      {/* Offline Status */}
      <Snackbar
        open={isOffline}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        sx={{ mb: showInstallBanner ? 10 : 0 }}
      >
        <Alert severity="warning">
          You're offline. Some features may be limited.
        </Alert>
      </Snackbar>

      {/* Notification Status Feedback */}
      <Snackbar
        open={notificationStatus === 'denied'}
        autoHideDuration={6000}
        onClose={() => setNotificationStatus('idle')}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="error">
          Notifications blocked. Enable them in your browser settings to receive admin alerts.
        </Alert>
      </Snackbar>

      <Snackbar
        open={notificationStatus === 'granted'}
        autoHideDuration={4000}
        onClose={() => setNotificationStatus('idle')}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="success">
          Notifications enabled! You'll receive important admin alerts.
        </Alert>
      </Snackbar>
    </>
  );
};

export default PWAInstallBanner;