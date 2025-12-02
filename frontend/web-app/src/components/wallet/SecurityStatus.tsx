import React, { useState } from 'react';
import {
  Paper,
  Typography,
  Box,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Chip,
  Button,
  IconButton,
  Switch,
  LinearProgress,
  Alert,
  Collapse,
  useTheme,
  alpha,
  Badge,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import ShieldIcon from '@mui/icons-material/Shield';
import LockIcon from '@mui/icons-material/Lock';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import SmsIcon from '@mui/icons-material/Sms';
import NotificationsIcon from '@mui/icons-material/Notifications';
import DevicesIcon from '@mui/icons-material/DevicesOther';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import SettingsIcon from '@mui/icons-material/Settings';
import PhoneIcon from '@mui/icons-material/Phone';
import EmailIcon from '@mui/icons-material/Email';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import AccountBoxIcon from '@mui/icons-material/AccountBox';;
import { format } from 'date-fns';
import { SecuritySettings } from '../../types/wallet';

interface SecurityStatusProps {
  settings?: SecuritySettings;
  onManage?: () => void;
  onToggleSetting?: (setting: keyof SecuritySettings, value: boolean) => void;
}

const SecurityStatus: React.FC<SecurityStatusProps> = ({
  settings,
  onManage,
  onToggleSetting,
}) => {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(false);
  const [showDevices, setShowDevices] = useState(false);

  if (!settings) {
    return (
      <Paper sx={{ p: 2 }}>
        <Alert severity="warning">
          Security settings not available
        </Alert>
      </Paper>
    );
  }

  const calculateSecurityScore = () => {
    const scores = {
      mfaEnabled: settings.mfaEnabled ? 25 : 0,
      biometricEnabled: settings.biometricEnabled ? 20 : 0,
      transactionNotifications: settings.transactionNotifications ? 15 : 0,
      loginNotifications: settings.loginNotifications ? 15 : 0,
      withdrawalLimits: settings.withdrawalLimits ? 15 : 0,
      suspiciousActivityAlerts: settings.suspiciousActivityAlerts ? 10 : 0,
    };
    
    return Object.values(scores).reduce((sum, score) => sum + score, 0);
  };

  const securityScore = calculateSecurityScore();
  const maxScore = 100;

  const getScoreLevel = (score: number) => {
    if (score >= 80) return { level: 'Excellent', color: 'success' };
    if (score >= 60) return { level: 'Good', color: 'info' };
    if (score >= 40) return { level: 'Fair', color: 'warning' };
    return { level: 'Needs Improvement', color: 'error' };
  };

  const scoreLevel = getScoreLevel(securityScore);

  const securityFeatures = [
    {
      key: 'mfaEnabled' as keyof SecuritySettings,
      title: 'Two-Factor Authentication',
      description: 'Add an extra layer of security to your account',
      icon: <SmsIcon />,
      enabled: settings.mfaEnabled,
      critical: true,
    },
    {
      key: 'biometricEnabled' as keyof SecuritySettings,
      title: 'Biometric Authentication',
      description: 'Use fingerprint or face recognition',
      icon: <FingerprintIcon />,
      enabled: settings.biometricEnabled,
      critical: true,
    },
    {
      key: 'transactionNotifications' as keyof SecuritySettings,
      title: 'Transaction Alerts',
      description: 'Get notified of all transactions',
      icon: <NotificationsIcon />,
      enabled: settings.transactionNotifications,
      critical: false,
    },
    {
      key: 'loginNotifications' as keyof SecuritySettings,
      title: 'Login Alerts',
      description: 'Get notified of new device logins',
      icon: <PhoneIcon />,
      enabled: settings.loginNotifications,
      critical: false,
    },
    {
      key: 'withdrawalLimits' as keyof SecuritySettings,
      title: 'Withdrawal Limits',
      description: 'Set daily and monthly withdrawal limits',
      icon: <LockIcon />,
      enabled: settings.withdrawalLimits,
      critical: false,
    },
    {
      key: 'suspiciousActivityAlerts' as keyof SecuritySettings,
      title: 'Suspicious Activity Monitoring',
      description: 'AI-powered fraud detection alerts',
      icon: <WarningIcon />,
      enabled: settings.suspiciousActivityAlerts,
      critical: false,
    },
  ];

  const enabledFeatures = securityFeatures.filter(f => f.enabled).length;
  const criticalFeatures = securityFeatures.filter(f => f.critical);
  const enabledCritical = criticalFeatures.filter(f => f.enabled).length;

  const renderSecurityScore = () => (
    <Box sx={{ mb: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          Security Score
        </Typography>
        <Chip
          label={scoreLevel.level}
          color={scoreLevel.color as any}
          icon={scoreLevel.color === 'success' ? <CheckCircleIcon /> : 
                scoreLevel.color === 'error' ? <ErrorIcon /> : <InfoIcon />}
        />
      </Box>
      
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Avatar
          sx={{
            bgcolor: alpha(theme.palette[scoreLevel.color as keyof typeof theme.palette].main, 0.1),
            color: theme.palette[scoreLevel.color as keyof typeof theme.palette].main,
            width: 60,
            height: 60,
          }}
        >
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            {securityScore}
          </Typography>
        </Avatar>
        
        <Box sx={{ flex: 1 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
            {enabledFeatures} of {securityFeatures.length} security features enabled
          </Typography>
          <LinearProgress
            variant="determinate"
            value={(securityScore / maxScore) * 100}
            sx={{
              height: 8,
              borderRadius: 4,
              backgroundColor: alpha(theme.palette.grey[300], 0.3),
              '& .MuiLinearProgress-bar': {
                backgroundColor: theme.palette[scoreLevel.color as keyof typeof theme.palette].main,
                borderRadius: 4,
              },
            }}
          />
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
            Target: 80+ for excellent security
          </Typography>
        </Box>
      </Box>

      {enabledCritical < criticalFeatures.length && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="body2">
            {criticalFeatures.length - enabledCritical} critical security feature(s) disabled.
            Enable them to improve your security score.
          </Typography>
        </Alert>
      )}
    </Box>
  );

  const renderSecurityFeatures = () => (
    <List sx={{ py: 0 }}>
      {securityFeatures.map((feature, index) => (
        <ListItem key={feature.key} sx={{ px: 0 }}>
          <ListItemIcon>
            <Avatar
              sx={{
                bgcolor: feature.enabled 
                  ? alpha(theme.palette.success.main, 0.1)
                  : alpha(theme.palette.grey[400], 0.1),
                color: feature.enabled 
                  ? theme.palette.success.main 
                  : theme.palette.grey[400],
              }}
            >
              {feature.icon}
            </Avatar>
          </ListItemIcon>
          
          <ListItemText
            primary={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="subtitle2">
                  {feature.title}
                </Typography>
                {feature.critical && (
                  <Chip label="Critical" size="small" color="error" variant="outlined" />
                )}
              </Box>
            }
            secondary={feature.description}
          />
          
          <ListItemSecondaryAction>
            <Switch
              checked={feature.enabled}
              onChange={(e) => onToggleSetting?.(feature.key, e.target.checked)}
              color="primary"
            />
          </ListItemSecondaryAction>
        </ListItem>
      ))}
    </List>
  );

  const renderTrustedDevices = () => (
    <Collapse in={showDevices}>
      <Box sx={{ mt: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
          Trusted Devices
        </Typography>
        
        {settings.trustedDevices.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No trusted devices configured
          </Typography>
        ) : (
          <List dense>
            {settings.trustedDevices.map((device) => (
              <ListItem key={device.id} sx={{ px: 0 }}>
                <ListItemIcon>
                  <DevicesIcon color={device.trusted ? 'primary' : 'disabled'} />
                </ListItemIcon>
                <ListItemText
                  primary={device.name}
                  secondary={`${device.type} • Last used: ${format(new Date(device.lastUsed), 'MMM d, yyyy')}`}
                />
                <ListItemSecondaryAction>
                  <Chip 
                    label={device.trusted ? 'Trusted' : 'Untrusted'} 
                    size="small"
                    color={device.trusted ? 'success' : 'default'}
                    variant="outlined"
                  />
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        )}
      </Box>
    </Collapse>
  );

  return (
    <Paper sx={{ p: 0 }}>
      <Box
        sx={{
          p: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
        }}
        onClick={() => setExpanded(!expanded)}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <ShieldIcon />
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Security
          </Typography>
          <Badge badgeContent={securityScore} color="primary" max={99}>
            <SecurityIcon />
          </Badge>
        </Box>
        <IconButton size="small">
          {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
        </IconButton>
      </Box>

      <Collapse in={expanded}>
        <Box sx={{ px: 2, pb: 2 }}>
          {renderSecurityScore()}
          {renderSecurityFeatures()}
          
          <Box sx={{ mt: 2 }}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<DevicesIcon />}
              onClick={() => setShowDevices(!showDevices)}
              sx={{ mb: 1 }}
            >
              {showDevices ? 'Hide' : 'Show'} Trusted Devices ({settings.trustedDevices.length})
            </Button>
            
            {renderTrustedDevices()}
            
            <Button
              fullWidth
              variant="contained"
              startIcon={<SettingsIcon />}
              onClick={onManage}
              sx={{ mt: 2 }}
            >
              Manage Security Settings
            </Button>
          </Box>
          
          <Box sx={{ mt: 2, p: 2, bgcolor: alpha(theme.palette.info.main, 0.05), borderRadius: 1 }}>
            <Typography variant="caption" color="text.secondary">
              Last security review: {format(new Date(settings.lastSecurityReview), 'MMM d, yyyy')}
            </Typography>
            <br />
            <Typography variant="caption" color="text.secondary">
              Device trust level: {settings.deviceTrustLevel}
            </Typography>
          </Box>
        </Box>
      </Collapse>
    </Paper>
  );
};

export default SecurityStatus;