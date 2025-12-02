import React, { useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Button,
  Grid,
  Switch,
  FormControlLabel,
  TextField,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Chip,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  InputAdornment,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import LockIcon from '@mui/icons-material/Lock';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import DevicesIcon from '@mui/icons-material/Devices';
import HistoryIcon from '@mui/icons-material/History';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import WarningIcon from '@mui/icons-material/Warning';
import CheckIcon from '@mui/icons-material/Check';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import DeviceUnknownIcon from '@mui/icons-material/DeviceUnknown';;
import { format } from 'date-fns';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { useAuth } from '@/contexts/AuthContext';
import MFADialog from '@/components/auth/MFADialog';
import toast from 'react-hot-toast';

const passwordSchema = yup.object().shape({
  currentPassword: yup.string().required('Current password is required'),
  newPassword: yup
    .string()
    .required('New password is required')
    .min(8, 'Password must be at least 8 characters')
    .matches(
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/,
      'Password must contain uppercase, lowercase, number and special character'
    ),
  confirmPassword: yup
    .string()
    .required('Please confirm your password')
    .oneOf([yup.ref('newPassword')], 'Passwords must match'),
});

interface PasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

interface ActiveSession {
  id: string;
  device: string;
  location: string;
  lastActive: string;
  current: boolean;
  browser?: string;
  ip?: string;
}

interface LoginHistory {
  id: string;
  timestamp: string;
  location: string;
  device: string;
  status: 'success' | 'failed';
  ip: string;
}

const SecuritySettings: React.FC = () => {
  const { user, changePassword } = useAuth();
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [showMfaDialog, setShowMfaDialog] = useState(false);
  const [showPassword, setShowPassword] = useState({
    current: false,
    new: false,
    confirm: false,
  });
  const [loading, setLoading] = useState(false);
  const [selectedSession, setSelectedSession] = useState<ActiveSession | null>(null);

  // Mock data
  const [securitySettings, setSecuritySettings] = useState({
    twoFactorEnabled: user?.mfaEnabled || false,
    loginAlerts: true,
    suspiciousActivityAlerts: true,
    biometricLogin: false,
    rememberDevice: true,
  });

  const activeSessions: ActiveSession[] = [
    {
      id: '1',
      device: 'Chrome on Windows',
      location: 'New York, US',
      lastActive: new Date().toISOString(),
      current: true,
      browser: 'Chrome 120.0',
      ip: '192.168.1.1',
    },
    {
      id: '2',
      device: 'Safari on iPhone',
      location: 'Brooklyn, US',
      lastActive: new Date(Date.now() - 3600000).toISOString(),
      current: false,
      browser: 'Safari 17.0',
      ip: '192.168.1.2',
    },
  ];

  const loginHistory: LoginHistory[] = [
    {
      id: '1',
      timestamp: new Date().toISOString(),
      location: 'New York, US',
      device: 'Chrome on Windows',
      status: 'success',
      ip: '192.168.1.1',
    },
    {
      id: '2',
      timestamp: new Date(Date.now() - 86400000).toISOString(),
      location: 'Unknown Location',
      device: 'Firefox on Linux',
      status: 'failed',
      ip: '10.0.0.1',
    },
  ];

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PasswordForm>({
    resolver: yupResolver(passwordSchema),
    defaultValues: {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    },
  });

  const handleChangePassword = async (data: PasswordForm) => {
    setLoading(true);
    try {
      await changePassword({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      toast.success('Password changed successfully!');
      setShowPasswordDialog(false);
      reset();
    } catch (error: any) {
      toast.error(error.message || 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSetting = (setting: keyof typeof securitySettings) => {
    setSecuritySettings(prev => ({
      ...prev,
      [setting]: !prev[setting],
    }));
    toast.success('Security settings updated');
  };

  const handleTerminateSession = (sessionId: string) => {
    // In a real app, call API to terminate session
    toast.success('Session terminated successfully');
  };

  const handleTerminateAllSessions = () => {
    // In a real app, call API to terminate all sessions
    toast.success('All other sessions terminated');
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Security Settings
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Manage your account security and authentication settings
      </Typography>

      {/* Security Score */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box>
              <Typography variant="h6" gutterBottom>
                Security Score
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Your account security level based on enabled features
              </Typography>
            </Box>
            <Box textAlign="center">
              <Box
                sx={{
                  width: 80,
                  height: 80,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: securitySettings.twoFactorEnabled ? 'success.light' : 'warning.light',
                  color: securitySettings.twoFactorEnabled ? 'success.main' : 'warning.main',
                }}
              >
                <Typography variant="h4" fontWeight="bold">
                  {securitySettings.twoFactorEnabled ? '85' : '60'}%
                </Typography>
              </Box>
              <Chip
                label={securitySettings.twoFactorEnabled ? 'Strong' : 'Medium'}
                color={securitySettings.twoFactorEnabled ? 'success' : 'warning'}
                size="small"
                sx={{ mt: 1 }}
              />
            </Box>
          </Box>
          
          {!securitySettings.twoFactorEnabled && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              Enable two-factor authentication to improve your security score
            </Alert>
          )}
        </CardContent>
      </Card>

      {/* Password Section */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Password
          </Typography>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box>
              <Typography variant="body2" color="text.secondary">
                Last changed: {format(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), 'MMMM dd, yyyy')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Use a strong, unique password to protect your account
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<Lock />}
              onClick={() => setShowPasswordDialog(true)}
            >
              Change Password
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Two-Factor Authentication */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box>
              <Typography variant="h6" gutterBottom>
                Two-Factor Authentication
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Add an extra layer of security to your account
              </Typography>
              {securitySettings.twoFactorEnabled && (
                <Chip
                  icon={<Check />}
                  label="Enabled with Authenticator App"
                  color="success"
                  size="small"
                  sx={{ mt: 1 }}
                />
              )}
            </Box>
            <Button
              variant={securitySettings.twoFactorEnabled ? 'outlined' : 'contained'}
              startIcon={<Smartphone />}
              onClick={() => setShowMfaDialog(true)}
            >
              {securitySettings.twoFactorEnabled ? 'Manage' : 'Enable'}
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Security Preferences */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Security Preferences
          </Typography>
          <List>
            <ListItem>
              <ListItemIcon>
                <Warning />
              </ListItemIcon>
              <ListItemText
                primary="Login Alerts"
                secondary="Get notified when someone logs into your account"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={securitySettings.loginAlerts}
                  onChange={() => handleToggleSetting('loginAlerts')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Security />
              </ListItemIcon>
              <ListItemText
                primary="Suspicious Activity Alerts"
                secondary="Get alerts about unusual account activity"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={securitySettings.suspiciousActivityAlerts}
                  onChange={() => handleToggleSetting('suspiciousActivityAlerts')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Smartphone />
              </ListItemIcon>
              <ListItemText
                primary="Biometric Login"
                secondary="Use fingerprint or face recognition to login"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={securitySettings.biometricLogin}
                  onChange={() => handleToggleSetting('biometricLogin')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Devices />
              </ListItemIcon>
              <ListItemText
                primary="Remember Trusted Devices"
                secondary="Don't require 2FA on recognized devices"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={securitySettings.rememberDevice}
                  onChange={() => handleToggleSetting('rememberDevice')}
                />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Active Sessions */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Typography variant="h6">Active Sessions</Typography>
            <Button
              size="small"
              color="error"
              onClick={handleTerminateAllSessions}
            >
              Terminate All Other Sessions
            </Button>
          </Box>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Device</TableCell>
                  <TableCell>Location</TableCell>
                  <TableCell>Last Active</TableCell>
                  <TableCell align="right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {activeSessions.map((session) => (
                  <TableRow key={session.id}>
                    <TableCell>
                      <Box display="flex" alignItems="center">
                        <DeviceUnknown sx={{ mr: 1 }} />
                        <Box>
                          <Typography variant="body2">
                            {session.device}
                          </Typography>
                          {session.current && (
                            <Chip label="Current" color="primary" size="small" />
                          )}
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Box display="flex" alignItems="center">
                        <LocationOn fontSize="small" sx={{ mr: 0.5 }} />
                        {session.location}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Box display="flex" alignItems="center">
                        <AccessTime fontSize="small" sx={{ mr: 0.5 }} />
                        {format(new Date(session.lastActive), 'MMM dd, HH:mm')}
                      </Box>
                    </TableCell>
                    <TableCell align="right">
                      {!session.current && (
                        <IconButton
                          size="small"
                          color="error"
                          onClick={() => handleTerminateSession(session.id)}
                        >
                          <Delete />
                        </IconButton>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Login History */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Recent Login Activity
          </Typography>
          <List>
            {loginHistory.slice(0, 5).map((login) => (
              <ListItem key={login.id}>
                <ListItemIcon>
                  {login.status === 'success' ? (
                    <Check color="success" />
                  ) : (
                    <Warning color="error" />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={`${login.device} - ${login.location}`}
                  secondary={format(new Date(login.timestamp), 'MMMM dd, yyyy HH:mm')}
                />
                <ListItemSecondaryAction>
                  <Chip
                    label={login.status}
                    color={login.status === 'success' ? 'success' : 'error'}
                    size="small"
                  />
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>

      {/* Change Password Dialog */}
      <Dialog
        open={showPasswordDialog}
        onClose={() => setShowPasswordDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Change Password</DialogTitle>
        <DialogContent>
          <form onSubmit={handleSubmit(handleChangePassword)}>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <Controller
                  name="currentPassword"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      type={showPassword.current ? 'text' : 'password'}
                      label="Current Password"
                      error={!!errors.currentPassword}
                      helperText={errors.currentPassword?.message}
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton
                              onClick={() => setShowPassword(prev => ({ ...prev, current: !prev.current }))}
                              edge="end"
                            >
                              {showPassword.current ? <VisibilityOff /> : <Visibility />}
                            </IconButton>
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12}>
                <Controller
                  name="newPassword"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      type={showPassword.new ? 'text' : 'password'}
                      label="New Password"
                      error={!!errors.newPassword}
                      helperText={errors.newPassword?.message}
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton
                              onClick={() => setShowPassword(prev => ({ ...prev, new: !prev.new }))}
                              edge="end"
                            >
                              {showPassword.new ? <VisibilityOff /> : <Visibility />}
                            </IconButton>
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12}>
                <Controller
                  name="confirmPassword"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      type={showPassword.confirm ? 'text' : 'password'}
                      label="Confirm Password"
                      error={!!errors.confirmPassword}
                      helperText={errors.confirmPassword?.message}
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton
                              onClick={() => setShowPassword(prev => ({ ...prev, confirm: !prev.confirm }))}
                              edge="end"
                            >
                              {showPassword.confirm ? <VisibilityOff /> : <Visibility />}
                            </IconButton>
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
            </Grid>
          </form>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowPasswordDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmit(handleChangePassword)}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            Change Password
          </Button>
        </DialogActions>
      </Dialog>

      {/* MFA Dialog */}
      <MFADialog
        open={showMfaDialog}
        onClose={() => setShowMfaDialog(false)}
        mode={securitySettings.twoFactorEnabled ? 'verify' : 'setup'}
        onSuccess={() => {
          setSecuritySettings(prev => ({ ...prev, twoFactorEnabled: true }));
          setShowMfaDialog(false);
        }}
      />
    </Box>
  );
};

export default SecuritySettings;