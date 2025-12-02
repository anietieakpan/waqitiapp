import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  Slide,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  useTheme,
  alpha,
} from '@mui/material';
import InstallIcon from '@mui/icons-material/GetApp';
import CloseIcon from '@mui/icons-material/Close';
import PhoneIcon from '@mui/icons-material/PhoneIphone';
import ComputerIcon from '@mui/icons-material/Computer';
import TabletIcon from '@mui/icons-material/Tablet';
import ShareIcon from '@mui/icons-material/Share';
import AddIcon from '@mui/icons-material/Add';
import MoreIcon from '@mui/icons-material/MoreVert';;
import { pwaService } from '../../utils/pwa';

interface PWAInstallBannerProps {
  position?: 'top' | 'bottom';
  persistent?: boolean;
  showAfterDelay?: number;
}

const PWAInstallBanner: React.FC<PWAInstallBannerProps> = ({
  position = 'bottom',
  persistent = false,
  showAfterDelay = 5000,
}) => {
  const theme = useTheme();
  const [showBanner, setShowBanner] = useState(false);
  const [showInstructions, setShowInstructions] = useState(false);
  const [isInstallable, setIsInstallable] = useState(false);
  const [isInstalled, setIsInstalled] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    // Check if already dismissed
    const isDismissed = localStorage.getItem('pwa-install-dismissed') === 'true';
    if (isDismissed && !persistent) {
      return;
    }

    // Check initial state
    setIsInstalled(pwaService.isAppInstalled());
    setIsInstallable(pwaService.canInstall());

    // Listen for PWA events
    const handleInstallable = (installable: boolean) => {
      setIsInstallable(installable);
      if (installable && !isDismissed) {
        setTimeout(() => setShowBanner(true), showAfterDelay);
      }
    };

    const handleInstalled = () => {
      setIsInstalled(true);
      setShowBanner(false);
      localStorage.removeItem('pwa-install-dismissed');
    };

    pwaService.on('installable', handleInstallable);
    pwaService.on('installed', handleInstalled);

    // Show banner after delay if installable
    if (pwaService.canInstall() && !isDismissed) {
      setTimeout(() => setShowBanner(true), showAfterDelay);
    }

    return () => {
      pwaService.off('installable', handleInstallable);
      pwaService.off('installed', handleInstalled);
    };
  }, [showAfterDelay, persistent]);

  const handleInstall = async () => {
    try {
      const result = await pwaService.showInstallPrompt();
      
      if (result === 'accepted') {
        setShowBanner(false);
      } else if (result === 'dismissed') {
        handleDismiss();
      } else {
        // Show manual instructions
        setShowInstructions(true);
      }
    } catch (error) {
      console.error('Error installing PWA:', error);
      setShowInstructions(true);
    }
  };

  const handleDismiss = () => {
    setShowBanner(false);
    setDismissed(true);
    if (!persistent) {
      localStorage.setItem('pwa-install-dismissed', 'true');
    }
  };

  const handleShowInstructions = () => {
    setShowInstructions(true);
  };

  const instructions = pwaService.getInstallInstructions();

  if (isInstalled || (!isInstallable && !showInstructions)) {
    return null;
  }

  const InstallInstructionsDialog = () => (
    <Dialog
      open={showInstructions}
      onClose={() => setShowInstructions(false)}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <InstallIcon color="primary" />
          <Typography variant="h6">Install Waqiti App</Typography>
        </Box>
      </DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          <Typography variant="body2">
            Install our app for the best experience with offline access, 
            push notifications, and faster loading.
          </Typography>
        </Alert>

        <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>
          Instructions for {instructions.platform}:
        </Typography>

        <List>
          {instructions.instructions.map((step, index) => (
            <ListItem key={index} sx={{ py: 0.5 }}>
              <ListItemIcon sx={{ minWidth: 32 }}>
                <Typography
                  variant="body2"
                  sx={{
                    width: 20,
                    height: 20,
                    borderRadius: '50%',
                    bgcolor: 'primary.main',
                    color: 'white',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.75rem',
                  }}
                >
                  {index + 1}
                </Typography>
              </ListItemIcon>
              <ListItemText
                primary={step}
                primaryTypographyProps={{ variant: 'body2' }}
              />
            </ListItem>
          ))}
        </List>

        {/* Visual guide based on platform */}
        <Box mt={2} p={2} bgcolor="grey.50" borderRadius={1}>
          <Typography variant="caption" color="text.secondary">
            {instructions.platform === 'iOS Safari' && (
              <Box display="flex" alignItems="center" gap={1}>
                <ShareIcon sx={{ fontSize: 16 }} />
                Look for the Share button
                <AddIcon sx={{ fontSize: 16 }} />
                then "Add to Home Screen"
              </Box>
            )}
            {instructions.platform === 'Android Chrome' && (
              <Box display="flex" alignItems="center" gap={1}>
                <MoreIcon sx={{ fontSize: 16 }} />
                Look for the menu (⋮) or install icon in the address bar
              </Box>
            )}
            {instructions.platform === 'Desktop' && (
              <Box display="flex" alignItems="center" gap={1}>
                <InstallIcon sx={{ fontSize: 16 }} />
                Look for the install icon in your browser's address bar
              </Box>
            )}
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setShowInstructions(false)}>
          Close
        </Button>
        <Button
          variant="contained"
          onClick={() => {
            setShowInstructions(false);
            handleDismiss();
          }}
        >
          Got it
        </Button>
      </DialogActions>
    </Dialog>
  );

  return (
    <>
      <Slide direction={position === 'bottom' ? 'up' : 'down'} in={showBanner}>
        <Paper
          elevation={8}
          sx={{
            position: 'fixed',
            [position]: 0,
            left: 0,
            right: 0,
            zIndex: 1300,
            bgcolor: alpha(theme.palette.primary.main, 0.95),
            color: 'white',
            backdropFilter: 'blur(10px)',
          }}
        >
          <Box p={2}>
            <Box display="flex" alignItems="center" justifyContent="space-between">
              <Box display="flex" alignItems="center" gap={2}>
                <Box
                  sx={{
                    p: 1,
                    borderRadius: 1,
                    bgcolor: alpha(theme.palette.common.white, 0.2),
                  }}
                >
                  <InstallIcon />
                </Box>
                <Box>
                  <Typography variant="subtitle1" fontWeight="bold">
                    Install Waqiti App
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.9 }}>
                    Get the full experience with offline access and notifications
                  </Typography>
                </Box>
              </Box>

              <Box display="flex" alignItems="center" gap={1}>
                <Button
                  variant="contained"
                  onClick={handleInstall}
                  sx={{
                    bgcolor: 'white',
                    color: 'primary.main',
                    '&:hover': {
                      bgcolor: alpha(theme.palette.common.white, 0.9),
                    },
                  }}
                  startIcon={<InstallIcon />}
                >
                  Install
                </Button>
                
                <Button
                  variant="text"
                  onClick={handleShowInstructions}
                  sx={{
                    color: 'white',
                    opacity: 0.9,
                    '&:hover': {
                      opacity: 1,
                      bgcolor: alpha(theme.palette.common.white, 0.1),
                    },
                  }}
                >
                  How?
                </Button>

                {!persistent && (
                  <IconButton
                    onClick={handleDismiss}
                    sx={{
                      color: 'white',
                      opacity: 0.7,
                      '&:hover': {
                        opacity: 1,
                        bgcolor: alpha(theme.palette.common.white, 0.1),
                      },
                    }}
                  >
                    <CloseIcon />
                  </IconButton>
                )}
              </Box>
            </Box>
          </Box>
        </Paper>
      </Slide>

      <InstallInstructionsDialog />
    </>
  );
};

export default PWAInstallBanner;