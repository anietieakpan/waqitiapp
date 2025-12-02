import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  CardActions,
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
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Avatar,
  LinearProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Tabs,
  Tab,
  Badge,
  Tooltip,
  Menu,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  Slider,
  useTheme,
  alpha,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
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
import DeviceUnknownIcon from '@mui/icons-material/DeviceUnknown';
import ShieldIcon from '@mui/icons-material/Shield';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import FaceIcon from '@mui/icons-material/Face';
import KeyIcon from '@mui/icons-material/Key';
import QrCodeIcon from '@mui/icons-material/QrCode';
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import BackupIcon from '@mui/icons-material/Backup';
import VpnLockIcon from '@mui/icons-material/VpnLock';
import GpsIcon from '@mui/icons-material/Gps';
import NotificationsIcon from '@mui/icons-material/Notifications';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroid';
import ComputerIcon from '@mui/icons-material/Computer';
import TabletIcon from '@mui/icons-material/Tablet';
import WatchIcon from '@mui/icons-material/Watch';
import RouterIcon from '@mui/icons-material/Router';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import BlockIcon from '@mui/icons-material/Block';
import ReportIcon from '@mui/icons-material/Report';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import InfoIcon from '@mui/icons-material/Info';
import ErrorIcon from '@mui/icons-material/Error';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import RadioButtonCheckedIcon from '@mui/icons-material/RadioButtonChecked';
import CloseIcon from '@mui/icons-material/Close';;
import { format, parseISO, differenceInMinutes, differenceInDays } from 'date-fns';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import toast from 'react-hot-toast';

import { useAppSelector, useAppDispatch } from '@/hooks/redux';
import { formatDate, formatTimeAgo } from '@/utils/formatters';

type SecurityLevel = 'BASIC' | 'STANDARD' | 'HIGH' | 'MAXIMUM';
type BiometricType = 'FINGERPRINT' | 'FACE' | 'VOICE' | 'IRIS';
type DeviceType = 'MOBILE' | 'DESKTOP' | 'TABLET' | 'SMARTWATCH' | 'OTHER';
type ThreatLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

interface SecuritySettings {
  twoFactorEnabled: boolean;
  biometricEnabled: boolean;
  hardwareKeyEnabled: boolean;
  loginAlerts: boolean;
  suspiciousActivityAlerts: boolean;
  locationBasedSecurity: boolean;
  deviceTrustEnabled: boolean;
  sessionTimeout: number; // minutes
  maxActiveSessions: number;
  requirePasswordForTransactions: boolean;
  allowRememberDevice: boolean;
  securityLevel: SecurityLevel;
  allowedCountries: string[];
  blockedIpRanges: string[];
  enabledBiometrics: BiometricType[];
  autoLockEnabled: boolean;
  autoLockDelay: number; // minutes
  privacyMode: boolean;
  encryptLocalData: boolean;
  wipePendingEnabled: boolean;
  emergencyContacts: string[];
}

interface TrustedDevice {
  id: string;
  name: string;
  type: DeviceType;
  os: string;
  browser?: string;
  lastUsed: string;
  location: string;
  fingerprint: string;
  trusted: boolean;
  riskScore: number;
  ip: string;
  userAgent: string;
}

interface SecurityThreat {
  id: string;
  type: string;
  level: ThreatLevel;
  description: string;
  detectedAt: string;
  location?: string;
  device?: string;
  ip?: string;
  status: 'ACTIVE' | 'RESOLVED' | 'INVESTIGATING';
  actions: string[];
}

interface BackupCode {
  code: string;
  used: boolean;
  usedAt?: string;
}

const passwordSchema = yup.object().shape({
  currentPassword: yup.string().required('Current password is required'),
  newPassword: yup
    .string()
    .required('New password is required')
    .min(12, 'Password must be at least 12 characters')
    .matches(
      /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/,
      'Password must contain uppercase, lowercase, number and special character'
    ),
  confirmPassword: yup
    .string()
    .required('Please confirm your password')
    .oneOf([yup.ref('newPassword')], 'Passwords must match'),
});

const SecuritySettingsEnhanced: React.FC = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  
  const [activeTab, setActiveTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [show2FASetup, setShow2FASetup] = useState(false);
  const [showBiometricSetup, setShowBiometricSetup] = useState(false);
  const [showHardwareKeySetup, setShowHardwareKeySetup] = useState(false);
  const [showBackupCodes, setShowBackupCodes] = useState(false);
  const [showThreatDetails, setShowThreatDetails] = useState<SecurityThreat | null>(null);
  const [selectedDevice, setSelectedDevice] = useState<TrustedDevice | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [showPassword, setShowPassword] = useState({
    current: false,
    new: false,
    confirm: false,
  });
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [backupCodes, setBackupCodes] = useState<BackupCode[]>([]);
  const [securityScore, setSecurityScore] = useState(0);
  
  // Mock security settings
  const [settings, setSettings] = useState<SecuritySettings>({
    twoFactorEnabled: false,
    biometricEnabled: false,
    hardwareKeyEnabled: false,
    loginAlerts: true,
    suspiciousActivityAlerts: true,
    locationBasedSecurity: false,
    deviceTrustEnabled: true,
    sessionTimeout: 30,
    maxActiveSessions: 5,
    requirePasswordForTransactions: true,
    allowRememberDevice: true,
    securityLevel: 'STANDARD',
    allowedCountries: ['US', 'CA', 'UK'],
    blockedIpRanges: [],
    enabledBiometrics: [],
    autoLockEnabled: false,
    autoLockDelay: 5,
    privacyMode: false,
    encryptLocalData: true,
    wipePendingEnabled: false,
    emergencyContacts: [],
  });

  // Mock data
  const trustedDevices: TrustedDevice[] = [
    {
      id: '1',
      name: 'iPhone 15 Pro',
      type: 'MOBILE',
      os: 'iOS 17.2',
      lastUsed: new Date().toISOString(),
      location: 'New York, US',
      fingerprint: 'a1b2c3d4e5f6',
      trusted: true,
      riskScore: 15,
      ip: '192.168.1.100',
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X)',
    },
    {
      id: '2',
      name: 'MacBook Pro',
      type: 'DESKTOP',
      os: 'macOS Sonoma',
      browser: 'Chrome 120.0',
      lastUsed: new Date(Date.now() - 3600000).toISOString(),
      location: 'New York, US',
      fingerprint: 'f6e5d4c3b2a1',
      trusted: true,
      riskScore: 8,
      ip: '192.168.1.101',
      userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
    },
  ];

  const securityThreats: SecurityThreat[] = [
    {
      id: '1',
      type: 'Suspicious Login Attempt',
      level: 'HIGH',
      description: 'Login attempt from unrecognized location (Moscow, Russia)',
      detectedAt: new Date(Date.now() - 7200000).toISOString(),
      location: 'Moscow, Russia',
      device: 'Unknown Device',
      ip: '192.0.2.1',
      status: 'RESOLVED',
      actions: ['Blocked IP', 'Sent Alert', 'Required Additional Verification'],
    },
    {
      id: '2',
      type: 'Multiple Failed Login Attempts',
      level: 'MEDIUM',
      description: '5 failed login attempts in the last hour',
      detectedAt: new Date(Date.now() - 1800000).toISOString(),
      ip: '192.0.2.2',
      status: 'ACTIVE',
      actions: ['Rate Limited', 'Monitoring'],
    },
  ];

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(passwordSchema),
    defaultValues: {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    },
  });

  useEffect(() => {
    calculateSecurityScore();
  }, [settings]);

  const calculateSecurityScore = () => {
    let score = 0;
    
    // Base security measures
    if (settings.twoFactorEnabled) score += 25;
    if (settings.biometricEnabled) score += 20;
    if (settings.hardwareKeyEnabled) score += 25;
    if (settings.encryptLocalData) score += 10;
    if (settings.locationBasedSecurity) score += 10;
    if (settings.deviceTrustEnabled) score += 5;
    if (settings.autoLockEnabled) score += 5;
    
    setSecurityScore(Math.min(score, 100));
  };

  const getSecurityLevel = () => {
    if (securityScore >= 90) return { level: 'MAXIMUM', color: 'success' };
    if (securityScore >= 70) return { level: 'HIGH', color: 'info' };
    if (securityScore >= 50) return { level: 'STANDARD', color: 'warning' };
    return { level: 'BASIC', color: 'error' };
  };

  const handleSettingChange = (setting: keyof SecuritySettings, value: any) => {
    setSettings(prev => ({ ...prev, [setting]: value }));
    toast.success('Security settings updated');
  };

  const handleEnable2FA = async () => {
    setLoading(true);
    try {
      // Generate QR code and backup codes
      const qrCodeData = await generateQRCode();
      const codes = await generateBackupCodes();
      
      setQrCode(qrCodeData);
      setBackupCodes(codes);
      setShow2FASetup(true);
    } catch (error) {
      toast.error('Failed to setup 2FA');
    } finally {
      setLoading(false);
    }
  };

  const generateQRCode = async (): Promise<string> => {
    // In a real app, this would call the backend to generate a QR code
    return 'otpauth://totp/Waqiti:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Waqiti';
  };

  const generateBackupCodes = async (): Promise<BackupCode[]> => {
    // Generate 10 backup codes
    return Array.from({ length: 10 }, (_, i) => ({
      code: Math.random().toString(36).substring(2, 10).toUpperCase(),
      used: false,
    }));
  };

  const handleSetupBiometrics = async (type: BiometricType) => {
    setLoading(true);
    try {
      // In a real app, this would use WebAuthn API
      if (navigator.credentials && (navigator.credentials as any).create) {
        // Mock biometric enrollment
        await new Promise(resolve => setTimeout(resolve, 2000));
        
        setSettings(prev => ({
          ...prev,
          biometricEnabled: true,
          enabledBiometrics: [...prev.enabledBiometrics, type],
        }));
        
        toast.success(`${type.toLowerCase()} authentication enabled`);
      } else {
        toast.error('Biometric authentication not supported on this device');
      }
    } catch (error) {
      toast.error('Failed to setup biometric authentication');
    } finally {
      setLoading(false);
    }
  };

  const handleSetupHardwareKey = async () => {
    setLoading(true);
    try {
      if (navigator.credentials && (navigator.credentials as any).create) {
        // Mock hardware key registration
        await new Promise(resolve => setTimeout(resolve, 3000));
        
        setSettings(prev => ({ ...prev, hardwareKeyEnabled: true }));
        toast.success('Hardware security key registered successfully');
      } else {
        toast.error('Hardware keys not supported on this device');
      }
    } catch (error) {
      toast.error('Failed to register hardware key');
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveDevice = (deviceId: string) => {
    // In a real app, call API to remove device
    toast.success('Device removed from trusted list');
  };

  const handleBlockIP = (ip: string) => {
    setSettings(prev => ({
      ...prev,
      blockedIpRanges: [...prev.blockedIpRanges, ip],
    }));
    toast.success('IP address blocked');
  };

  const renderSecurityOverview = () => {
    const securityLevel = getSecurityLevel();
    
    return (
      <Box>
        {/* Security Score Card */}
        <Card sx={{ mb: 3, bgcolor: alpha(theme.palette[securityLevel.color].main, 0.1) }}>
          <CardContent>
            <Grid container alignItems="center" spacing={3}>
              <Grid item xs={12} md={8}>
                <Box display="flex" alignItems="center" mb={2}>
                  <Shield 
                    sx={{ 
                      fontSize: 48, 
                      mr: 2, 
                      color: `${securityLevel.color}.main` 
                    }} 
                  />
                  <Box>
                    <Typography variant="h5" gutterBottom>
                      Security Score: {securityScore}/100
                    </Typography>
                    <Chip 
                      label={securityLevel.level}
                      color={securityLevel.color as any}
                      size="large"
                    />
                  </Box>
                </Box>
                
                <LinearProgress 
                  variant="determinate" 
                  value={securityScore} 
                  color={securityLevel.color as any}
                  sx={{ height: 8, borderRadius: 4 }}
                />
                
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Your account security is {securityLevel.level.toLowerCase()}. 
                  {securityScore < 70 && 'Consider enabling additional security features.'}
                </Typography>
              </Grid>
              
              <Grid item xs={12} md={4}>
                <Box textAlign="center">
                  <Typography variant="h6" gutterBottom>
                    Active Protections
                  </Typography>
                  <Box display="flex" flexDirection="column" gap={1}>
                    {settings.twoFactorEnabled && (
                      <Chip icon={<Smartphone />} label="2FA Enabled" color="success" size="small" />
                    )}
                    {settings.biometricEnabled && (
                      <Chip icon={<Fingerprint />} label="Biometrics" color="success" size="small" />
                    )}
                    {settings.hardwareKeyEnabled && (
                      <Chip icon={<VpnKey />} label="Hardware Key" color="success" size="small" />
                    )}
                    {settings.encryptLocalData && (
                      <Chip icon={<VpnLock />} label="Encryption" color="info" size="small" />
                    )}
                  </Box>
                </Box>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* Quick Actions */}
        <Grid container spacing={3} mb={3}>
          <Grid item xs={12} sm={6} md={3}>
            <Card sx={{ height: '100%', cursor: 'pointer' }} onClick={() => setShow2FASetup(true)}>
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <Smartphone sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Two-Factor Auth
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {settings.twoFactorEnabled ? 'Manage 2FA' : 'Enable 2FA'}
                </Typography>
                {settings.twoFactorEnabled && (
                  <CheckCircle color="success" sx={{ mt: 1 }} />
                )}
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} sm={6} md={3}>
            <Card sx={{ height: '100%', cursor: 'pointer' }} onClick={() => setShowBiometricSetup(true)}>
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <Fingerprint sx={{ fontSize: 48, color: 'secondary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Biometrics
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {settings.biometricEnabled ? 'Manage' : 'Setup'}
                </Typography>
                {settings.biometricEnabled && (
                  <CheckCircle color="success" sx={{ mt: 1 }} />
                )}
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} sm={6} md={3}>
            <Card sx={{ height: '100%', cursor: 'pointer' }} onClick={() => setShowHardwareKeySetup(true)}>
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <VpnKey sx={{ fontSize: 48, color: 'info.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Hardware Keys
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {settings.hardwareKeyEnabled ? 'Manage' : 'Add Key'}
                </Typography>
                {settings.hardwareKeyEnabled && (
                  <CheckCircle color="success" sx={{ mt: 1 }} />
                )}
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} sm={6} md={3}>
            <Card sx={{ height: '100%', cursor: 'pointer' }} onClick={() => setShowPasswordDialog(true)}>
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <Lock sx={{ fontSize: 48, color: 'warning.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Password
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Change Password
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Last changed: 30 days ago
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Security Recommendations */}
        {securityScore < 80 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                <Warning color="warning" sx={{ mr: 1 }} />
                Security Recommendations
              </Typography>
              
              <List>
                {!settings.twoFactorEnabled && (
                  <ListItem>
                    <ListItemIcon>
                      <Smartphone color="primary" />
                    </ListItemIcon>
                    <ListItemText
                      primary="Enable Two-Factor Authentication"
                      secondary="Add an extra layer of security with 2FA (+25 points)"
                    />
                    <ListItemSecondaryAction>
                      <Button size="small" onClick={() => setShow2FASetup(true)}>
                        Enable
                      </Button>
                    </ListItemSecondaryAction>
                  </ListItem>
                )}
                
                {!settings.biometricEnabled && (
                  <ListItem>
                    <ListItemIcon>
                      <Fingerprint color="secondary" />
                    </ListItemIcon>
                    <ListItemText
                      primary="Setup Biometric Authentication"
                      secondary="Use fingerprint or face recognition for secure access (+20 points)"
                    />
                    <ListItemSecondaryAction>
                      <Button size="small" onClick={() => setShowBiometricSetup(true)}>
                        Setup
                      </Button>
                    </ListItemSecondaryAction>
                  </ListItem>
                )}
                
                {!settings.hardwareKeyEnabled && (
                  <ListItem>
                    <ListItemIcon>
                      <VpnKey color="info" />
                    </ListItemIcon>
                    <ListItemText
                      primary="Register Hardware Security Key"
                      secondary="Use a physical security key for maximum protection (+25 points)"
                    />
                    <ListItemSecondaryAction>
                      <Button size="small" onClick={() => setShowHardwareKeySetup(true)}>
                        Add Key
                      </Button>
                    </ListItemSecondaryAction>
                  </ListItem>
                )}
              </List>
            </CardContent>
          </Card>
        )}

        {/* Recent Security Activity */}
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Recent Security Activity
            </Typography>
            
            <List>
              <ListItem>
                <ListItemIcon>
                  <CheckCircle color="success" />
                </ListItemIcon>
                <ListItemText
                  primary="Successful login from trusted device"
                  secondary={`${formatTimeAgo(new Date())} • New York, US`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <Shield color="info" />
                </ListItemIcon>
                <ListItemText
                  primary="2FA backup codes regenerated"
                  secondary={`${formatTimeAgo(new Date(Date.now() - 86400000))} • Security Settings`}
                />
              </ListItem>
              
              <ListItem>
                <ListItemIcon>
                  <Warning color="warning" />
                </ListItemIcon>
                <ListItemText
                  primary="Login attempt blocked from unknown location"
                  secondary={`${formatTimeAgo(new Date(Date.now() - 172800000))} • Moscow, Russia`}
                />
              </ListItem>
            </List>
          </CardContent>
        </Card>
      </Box>
    );
  };

  const renderAuthenticationTab = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Authentication Methods
      </Typography>
      
      {/* Password */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="subtitle1" gutterBottom>
                Password
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Last changed: {format(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), 'MMMM dd, yyyy')}
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<Lock />}
              onClick={() => setShowPasswordDialog(true)}
            >
              Change
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Two-Factor Authentication */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Box>
              <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                Two-Factor Authentication
                {settings.twoFactorEnabled && (
                  <Chip label="Active" color="success" size="small" sx={{ ml: 1 }} />
                )}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Secure your account with an additional verification step
              </Typography>
            </Box>
            <Button
              variant={settings.twoFactorEnabled ? "outlined" : "contained"}
              startIcon={<Smartphone />}
              onClick={handleEnable2FA}
              disabled={loading}
            >
              {settings.twoFactorEnabled ? 'Manage' : 'Enable'}
            </Button>
          </Box>
          
          {settings.twoFactorEnabled && (
            <Box>
              <Typography variant="body2" gutterBottom>
                Active Methods:
              </Typography>
              <Box display="flex" gap={1} mb={2}>
                <Chip icon={<Smartphone />} label="Authenticator App" size="small" />
                <Chip icon={<Sms />} label="SMS Backup" size="small" variant="outlined" />
              </Box>
              
              <Box display="flex" gap={1}>
                <Button size="small" startIcon={<Backup />} onClick={() => setShowBackupCodes(true)}>
                  View Backup Codes
                </Button>
                <Button size="small" startIcon={<Download />}>
                  Download Codes
                </Button>
              </Box>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Biometric Authentication */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Box>
              <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                Biometric Authentication
                {settings.biometricEnabled && (
                  <Chip label="Active" color="success" size="small" sx={{ ml: 1 }} />
                )}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Use your biometrics for quick and secure authentication
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<Fingerprint />}
              onClick={() => setShowBiometricSetup(true)}
            >
              {settings.biometricEnabled ? 'Manage' : 'Setup'}
            </Button>
          </Box>
          
          {settings.biometricEnabled && (
            <Box>
              <Typography variant="body2" gutterBottom>
                Enabled Methods:
              </Typography>
              <Box display="flex" gap={1}>
                {settings.enabledBiometrics.map((type) => (
                  <Chip
                    key={type}
                    icon={
                      type === 'FINGERPRINT' ? <Fingerprint /> :
                      type === 'FACE' ? <Face /> : <Key />
                    }
                    label={type.toLowerCase()}
                    size="small"
                  />
                ))}
              </Box>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Hardware Security Keys */}
      <Card>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Box>
              <Typography variant="subtitle1" gutterBottom display="flex" alignItems="center">
                Hardware Security Keys
                {settings.hardwareKeyEnabled && (
                  <Chip label="Active" color="success" size="small" sx={{ ml: 1 }} />
                )}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Physical security keys provide the highest level of protection
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<VpnKey />}
              onClick={() => setShowHardwareKeySetup(true)}
              disabled={loading}
            >
              {settings.hardwareKeyEnabled ? 'Manage' : 'Add Key'}
            </Button>
          </Box>
          
          {settings.hardwareKeyEnabled && (
            <Alert severity="success">
              Hardware security key is registered and active. Use it for secure authentication.
            </Alert>
          )}
        </CardContent>
      </Card>
    </Box>
  );

  const renderDevicesTab = () => (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">
          Trusted Devices ({trustedDevices.length})
        </Typography>
        <Button
          startIcon={<Refresh />}
          onClick={() => toast.success('Device list refreshed')}
        >
          Refresh
        </Button>
      </Box>
      
      <Grid container spacing={3}>
        {trustedDevices.map((device) => {
          const getDeviceIcon = () => {
            switch (device.type) {
              case 'MOBILE': return <PhoneAndroid />;
              case 'TABLET': return <Tablet />;
              case 'DESKTOP': return <Computer />;
              case 'SMARTWATCH': return <Watch />;
              default: return <DeviceUnknown />;
            }
          };

          const getRiskColor = () => {
            if (device.riskScore < 20) return 'success';
            if (device.riskScore < 50) return 'warning';
            return 'error';
          };

          return (
            <Grid item xs={12} md={6} key={device.id}>
              <Card>
                <CardContent>
                  <Box display="flex" alignItems="center" mb={2}>
                    <Avatar sx={{ bgcolor: 'primary.main', mr: 2 }}>
                      {getDeviceIcon()}
                    </Avatar>
                    <Box flex={1}>
                      <Typography variant="subtitle1">
                        {device.name}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {device.os} {device.browser && `• ${device.browser}`}
                      </Typography>
                    </Box>
                    <IconButton onClick={(e) => {
                      setSelectedDevice(device);
                      setMenuAnchor(e.currentTarget);
                    }}>
                      <MoreVertIcon />
                    </IconButton>
                  </Box>
                  
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <LocationOn fontSize="small" />
                    <Typography variant="body2">
                      {device.location}
                    </Typography>
                    <Chip 
                      label={`Risk: ${device.riskScore}%`} 
                      color={getRiskColor()}
                      size="small"
                    />
                  </Box>
                  
                  <Box display="flex" alignItems="center" gap={1} mb={2}>
                    <AccessTime fontSize="small" />
                    <Typography variant="body2" color="text.secondary">
                      Last used: {formatTimeAgo(device.lastUsed)}
                    </Typography>
                  </Box>
                  
                  <Box display="flex" justifyContent="space-between" alignItems="center">
                    <Chip
                      label={device.trusted ? 'Trusted' : 'Untrusted'}
                      color={device.trusted ? 'success' : 'default'}
                      size="small"
                    />
                    <Typography variant="caption" color="text.secondary">
                      {device.ip}
                    </Typography>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          );
        })}
      </Grid>
    </Box>
  );

  const renderThreatMonitoringTab = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Security Monitoring
      </Typography>
      
      {/* Active Threats */}
      {securityThreats.filter(t => t.status === 'ACTIVE').length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="subtitle1" gutterBottom color="error">
              Active Security Threats ({securityThreats.filter(t => t.status === 'ACTIVE').length})
            </Typography>
            
            <List>
              {securityThreats.filter(t => t.status === 'ACTIVE').map((threat) => (
                <ListItem key={threat.id} button onClick={() => setShowThreatDetails(threat)}>
                  <ListItemIcon>
                    <Warning color={threat.level === 'HIGH' ? 'error' : 'warning'} />
                  </ListItemIcon>
                  <ListItemText
                    primary={threat.type}
                    secondary={
                      <Box>
                        <Typography variant="body2" color="text.secondary">
                          {threat.description}
                        </Typography>
                        <Typography variant="caption">
                          {formatTimeAgo(threat.detectedAt)}
                        </Typography>
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Chip
                      label={threat.level}
                      color={threat.level === 'HIGH' ? 'error' : 'warning'}
                      size="small"
                    />
                  </ListItemSecondaryAction>
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      )}
      
      {/* Security History */}
      <Card>
        <CardContent>
          <Typography variant="subtitle1" gutterBottom>
            Security Event History
          </Typography>
          
          <List>
            {securityThreats.map((threat) => (
              <ListItem key={threat.id} button onClick={() => setShowThreatDetails(threat)}>
                <ListItemIcon>
                  {threat.status === 'RESOLVED' ? (
                    <CheckCircle color="success" />
                  ) : threat.status === 'INVESTIGATING' ? (
                    <Info color="info" />
                  ) : (
                    <Warning color={threat.level === 'HIGH' ? 'error' : 'warning'} />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={threat.type}
                  secondary={
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        {threat.description}
                      </Typography>
                      <Typography variant="caption">
                        {formatDate(threat.detectedAt)} • {threat.location || threat.ip}
                      </Typography>
                    </Box>
                  }
                />
                <ListItemSecondaryAction>
                  <Box display="flex" alignItems="center" gap={1}>
                    <Chip
                      label={threat.status}
                      color={
                        threat.status === 'RESOLVED' ? 'success' :
                        threat.status === 'INVESTIGATING' ? 'info' : 'error'
                      }
                      size="small"
                    />
                    <Chip
                      label={threat.level}
                      color={threat.level === 'HIGH' ? 'error' : 'warning'}
                      size="small"
                      variant="outlined"
                    />
                  </Box>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>
    </Box>
  );

  const renderAdvancedTab = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Advanced Security Settings
      </Typography>
      
      {/* Session Management */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="subtitle1" gutterBottom>
            Session Management
          </Typography>
          
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" gutterBottom>
                Session Timeout (minutes)
              </Typography>
              <Slider
                value={settings.sessionTimeout}
                onChange={(_, value) => handleSettingChange('sessionTimeout', value)}
                min={5}
                max={120}
                step={5}
                valueLabelDisplay="auto"
                marks={[
                  { value: 5, label: '5m' },
                  { value: 30, label: '30m' },
                  { value: 60, label: '1h' },
                  { value: 120, label: '2h' },
                ]}
              />
            </Grid>
            
            <Grid item xs={12} sm={6}>
              <Typography variant="body2" gutterBottom>
                Maximum Active Sessions
              </Typography>
              <Slider
                value={settings.maxActiveSessions}
                onChange={(_, value) => handleSettingChange('maxActiveSessions', value)}
                min={1}
                max={10}
                step={1}
                valueLabelDisplay="auto"
                marks={[
                  { value: 1, label: '1' },
                  { value: 5, label: '5' },
                  { value: 10, label: '10' },
                ]}
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Privacy & Encryption */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="subtitle1" gutterBottom>
            Privacy & Encryption
          </Typography>
          
          <List>
            <ListItem>
              <ListItemIcon>
                <VpnLock />
              </ListItemIcon>
              <ListItemText
                primary="Encrypt Local Data"
                secondary="Encrypt sensitive data stored locally on your devices"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.encryptLocalData}
                  onChange={(e) => handleSettingChange('encryptLocalData', e.target.checked)}
                />
              </ListItemSecondaryAction>
            </ListItem>
            
            <ListItem>
              <ListItemIcon>
                <Shield />
              </ListItemIcon>
              <ListItemText
                primary="Privacy Mode"
                secondary="Hide sensitive information in screenshots and notifications"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.privacyMode}
                  onChange={(e) => handleSettingChange('privacyMode', e.target.checked)}
                />
              </ListItemSecondaryAction>
            </ListItem>
            
            <ListItem>
              <ListItemIcon>
                <Delete />
              </ListItemIcon>
              <ListItemText
                primary="Remote Wipe"
                secondary="Enable remote device wipe in case of theft or loss"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.wipePendingEnabled}
                  onChange={(e) => handleSettingChange('wipePendingEnabled', e.target.checked)}
                  color="error"
                />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Geographic Restrictions */}
      <Card>
        <CardContent>
          <Typography variant="subtitle1" gutterBottom>
            Geographic Access Controls
          </Typography>
          
          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Allowed Countries</InputLabel>
            <Select
              multiple
              value={settings.allowedCountries}
              onChange={(e) => handleSettingChange('allowedCountries', e.target.value)}
              label="Allowed Countries"
            >
              <MenuItem value="US">United States</MenuItem>
              <MenuItem value="CA">Canada</MenuItem>
              <MenuItem value="UK">United Kingdom</MenuItem>
              <MenuItem value="DE">Germany</MenuItem>
              <MenuItem value="FR">France</MenuItem>
              <MenuItem value="JP">Japan</MenuItem>
              <MenuItem value="AU">Australia</MenuItem>
            </Select>
          </FormControl>
          
          <FormControlLabel
            control={
              <Switch
                checked={settings.locationBasedSecurity}
                onChange={(e) => handleSettingChange('locationBasedSecurity', e.target.checked)}
              />
            }
            label="Enable location-based security alerts"
          />
        </CardContent>
      </Card>
    </Box>
  );

  const tabs = [
    { label: 'Overview', icon: <Shield /> },
    { label: 'Authentication', icon: <Lock /> },
    { label: 'Devices', icon: <Devices />, badge: trustedDevices.length },
    { label: 'Threats', icon: <Warning />, badge: securityThreats.filter(t => t.status === 'ACTIVE').length },
    { label: 'Advanced', icon: <Settings /> },
  ];

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5">
          Security Settings
        </Typography>
        <Button
          startIcon={<Download />}
          onClick={() => toast.success('Security report downloaded')}
        >
          Export Report
        </Button>
      </Box>
      
      <Typography variant="body2" color="text.secondary" paragraph>
        Manage your account security, authentication methods, and privacy settings
      </Typography>

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={(e, newValue) => setActiveTab(newValue)}>
          {tabs.map((tab, index) => (
            <Tab
              key={index}
              icon={
                tab.badge ? (
                  <Badge badgeContent={tab.badge} color="error">
                    {tab.icon}
                  </Badge>
                ) : (
                  tab.icon
                )
              }
              label={tab.label}
              iconPosition="start"
            />
          ))}
        </Tabs>
      </Box>

      {/* Tab Content */}
      {activeTab === 0 && renderSecurityOverview()}
      {activeTab === 1 && renderAuthenticationTab()}
      {activeTab === 2 && renderDevicesTab()}
      {activeTab === 3 && renderThreatMonitoringTab()}
      {activeTab === 4 && renderAdvancedTab()}

      {/* Device Actions Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor && selectedDevice)}
        onClose={() => {
          setMenuAnchor(null);
          setSelectedDevice(null);
        }}
      >
        <MenuItem onClick={() => {
          toast.success('Device details viewed');
          setMenuAnchor(null);
        }}>
          <Info sx={{ mr: 1 }} /> View Details
        </MenuItem>
        <MenuItem onClick={() => {
          if (selectedDevice?.trusted) {
            toast.success('Device removed from trusted list');
          } else {
            toast.success('Device added to trusted list');
          }
          setMenuAnchor(null);
        }}>
          {selectedDevice?.trusted ? (
            <><Block sx={{ mr: 1 }} /> Remove Trust</>
          ) : (
            <><Check sx={{ mr: 1 }} /> Mark as Trusted</>
          )}
        </MenuItem>
        <MenuItem onClick={() => {
          handleRemoveDevice(selectedDevice?.id || '');
          setMenuAnchor(null);
        }}>
          <Delete sx={{ mr: 1 }} /> Remove Device
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => {
          handleBlockIP(selectedDevice?.ip || '');
          setMenuAnchor(null);
        }}>
          <Block sx={{ mr: 1 }} /> Block IP Address
        </MenuItem>
      </Menu>

      {/* Threat Details Dialog */}
      <Dialog
        open={Boolean(showThreatDetails)}
        onClose={() => setShowThreatDetails(null)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Security Threat Details
          <IconButton
            onClick={() => setShowThreatDetails(null)}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {showThreatDetails && (
            <Box>
              <Grid container spacing={2} mb={3}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" gutterBottom>
                    Threat Type
                  </Typography>
                  <Typography variant="body1">
                    {showThreatDetails.type}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" gutterBottom>
                    Severity Level
                  </Typography>
                  <Chip
                    label={showThreatDetails.level}
                    color={showThreatDetails.level === 'HIGH' ? 'error' : 'warning'}
                  />
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" gutterBottom>
                    Description
                  </Typography>
                  <Typography variant="body1">
                    {showThreatDetails.description}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" gutterBottom>
                    Detected At
                  </Typography>
                  <Typography variant="body2">
                    {formatDate(showThreatDetails.detectedAt)}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle2" gutterBottom>
                    Status
                  </Typography>
                  <Chip
                    label={showThreatDetails.status}
                    color={
                      showThreatDetails.status === 'RESOLVED' ? 'success' :
                      showThreatDetails.status === 'INVESTIGATING' ? 'info' : 'error'
                    }
                  />
                </Grid>
              </Grid>
              
              <Typography variant="subtitle2" gutterBottom>
                Actions Taken
              </Typography>
              <List dense>
                {showThreatDetails.actions.map((action, index) => (
                  <ListItem key={index}>
                    <ListItemIcon>
                      <CheckCircle color="success" fontSize="small" />
                    </ListItemIcon>
                    <ListItemText primary={action} />
                  </ListItem>
                ))}
              </List>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowThreatDetails(null)}>Close</Button>
          {showThreatDetails?.status === 'ACTIVE' && (
            <Button variant="contained" color="error">
              Take Action
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SecuritySettingsEnhanced;