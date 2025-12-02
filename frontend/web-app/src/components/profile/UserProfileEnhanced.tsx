import React, { useState, useCallback } from 'react';
import {
  Box,
  Typography,
  TextField,
  Button,
  Grid,
  Card,
  CardContent,
  Avatar,
  IconButton,
  Alert,
  Divider,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  CircularProgress,
  InputAdornment,
  Tab,
  Tabs,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Switch,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  LinearProgress,
  Badge,
  Paper,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Tooltip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import VerifiedIcon from '@mui/icons-material/Verified';
import WarningIcon from '@mui/icons-material/Warning';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import BadgeIcon from '@mui/icons-material/Badge';
import LanguageIcon from '@mui/icons-material/Language';
import PaletteIcon from '@mui/icons-material/Palette';
import NotificationsIcon from '@mui/icons-material/Notifications';
import SecurityIcon from '@mui/icons-material/Security';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import GoogleIcon from '@mui/icons-material/Google';
import FacebookIcon from '@mui/icons-material/Facebook';
import AppleIcon from '@mui/icons-material/Apple';
import GitHubIcon from '@mui/icons-material/GitHub';
import LinkedInIcon from '@mui/icons-material/LinkedIn';
import TwitterIcon from '@mui/icons-material/Twitter';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DownloadIcon from '@mui/icons-material/Download';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import InfoIcon from '@mui/icons-material/Info';
import UploadIcon from '@mui/icons-material/Upload';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import StarIcon from '@mui/icons-material/Star';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import TimelineIcon from '@mui/icons-material/Timeline';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { format } from 'date-fns';
import { useAuth } from '@/contexts/AuthContext';
import toast from 'react-hot-toast';

const profileSchema = yup.object().shape({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phoneNumber: yup.string().required('Phone number is required'),
  dateOfBirth: yup.string().required('Date of birth is required'),
  occupation: yup.string().required('Occupation is required'),
  address: yup.object().shape({
    street: yup.string().required('Street address is required'),
    city: yup.string().required('City is required'),
    state: yup.string().required('State/Province is required'),
    postalCode: yup.string().required('Postal code is required'),
    country: yup.string().required('Country is required'),
  }),
  preferences: yup.object().shape({
    language: yup.string().required('Language is required'),
    currency: yup.string().required('Currency is required'),
    timezone: yup.string().required('Timezone is required'),
    theme: yup.string().required('Theme is required'),
  }),
});

interface ProfileForm {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  dateOfBirth: string;
  occupation: string;
  address: {
    street: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
  };
  preferences: {
    language: string;
    currency: string;
    timezone: string;
    theme: string;
  };
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
      id={`profile-tabpanel-${index}`}
      aria-labelledby={`profile-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const UserProfileEnhanced: React.FC = () => {
  const { user, updateProfile } = useAuth();
  const [activeTab, setActiveTab] = useState(0);
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [showAvatarDialog, setShowAvatarDialog] = useState(false);
  const [showKYCDialog, setShowKYCDialog] = useState(false);
  const [showDataExportDialog, setShowDataExportDialog] = useState(false);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [kycStep, setKycStep] = useState(0);

  // Mock data
  const [profileData, setProfileData] = useState({
    accountLevel: 'Gold',
    memberSince: '2023-01-15',
    totalTransactions: 1247,
    totalVolume: 156780.50,
    favoritePaymentMethod: 'Credit Card',
    averageTransactionSize: 125.80,
    kycDocuments: [
      { id: '1', type: 'Government ID', status: 'verified', uploadDate: '2023-01-20' },
      { id: '2', type: 'Proof of Address', status: 'verified', uploadDate: '2023-01-21' },
      { id: '3', type: 'Income Statement', status: 'pending', uploadDate: '2023-12-01' },
    ],
    linkedAccounts: [
      { id: '1', provider: 'Google', email: 'john@gmail.com', connected: true, lastUsed: '2024-01-01' },
      { id: '2', provider: 'Facebook', email: 'john@facebook.com', connected: false, lastUsed: null },
      { id: '3', provider: 'Apple', email: 'john@icloud.com', connected: true, lastUsed: '2023-12-15' },
    ],
    activityLog: [
      { id: '1', action: 'Profile Updated', timestamp: new Date(), details: 'Phone number changed' },
      { id: '2', action: 'Security Settings', timestamp: new Date(Date.now() - 86400000), details: '2FA enabled' },
      { id: '3', action: 'Payment Method', timestamp: new Date(Date.now() - 172800000), details: 'New card added' },
    ],
    preferences: {
      emailNotifications: true,
      smsNotifications: true,
      pushNotifications: true,
      marketingEmails: false,
      transactionAlerts: true,
      securityAlerts: true,
      monthlyStatements: true,
    },
  });

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileForm>({
    resolver: yupResolver(profileSchema),
    defaultValues: {
      firstName: user?.firstName || 'John',
      lastName: user?.lastName || 'Doe',
      email: user?.email || 'john.doe@example.com',
      phoneNumber: user?.phoneNumber || '+1 (555) 123-4567',
      dateOfBirth: '1990-05-15',
      occupation: 'Software Engineer',
      address: {
        street: '123 Main Street',
        city: 'New York',
        state: 'New York',
        postalCode: '10001',
        country: 'United States',
      },
      preferences: {
        language: 'en',
        currency: 'USD',
        timezone: 'America/New_York',
        theme: 'system',
      },
    },
  });

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleSaveProfile = async (data: ProfileForm) => {
    setLoading(true);
    try {
      await updateProfile({
        firstName: data.firstName,
        lastName: data.lastName,
        phoneNumber: data.phoneNumber,
      });
      toast.success('Profile updated successfully!');
      setIsEditing(false);
    } catch (error: any) {
      toast.error(error.message || 'Failed to update profile');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEdit = () => {
    reset();
    setIsEditing(false);
  };

  const handleAvatarChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        toast.error('Image size should be less than 5MB');
        return;
      }
      
      setAvatarFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatarPreview(reader.result as string);
      };
      reader.readAsDataURL(file);
      setShowAvatarDialog(true);
    }
  };

  const handleUploadAvatar = async () => {
    if (!avatarFile) return;
    
    setLoading(true);
    try {
      // In a real app, upload to server
      toast.success('Profile photo updated successfully!');
      setShowAvatarDialog(false);
    } catch (error) {
      toast.error('Failed to upload profile photo');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleAccount = (accountId: string) => {
    setProfileData(prev => ({
      ...prev,
      linkedAccounts: prev.linkedAccounts.map(acc => 
        acc.id === accountId 
          ? { ...acc, connected: !acc.connected, lastUsed: acc.connected ? null : new Date().toISOString() }
          : acc
      )
    }));
    toast.success('Account linking updated');
  };

  const handlePreferenceChange = (key: keyof typeof profileData.preferences) => {
    setProfileData(prev => ({
      ...prev,
      preferences: {
        ...prev.preferences,
        [key]: !prev.preferences[key],
      },
    }));
    toast.success('Preference updated');
  };

  const handleExportData = async () => {
    setLoading(true);
    try {
      // Simulate data export
      await new Promise(resolve => setTimeout(resolve, 2000));
      const dataBlob = new Blob([JSON.stringify(profileData, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'profile-data.json';
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Profile data exported successfully');
      setShowDataExportDialog(false);
    } catch (error) {
      toast.error('Failed to export data');
    } finally {
      setLoading(false);
    }
  };

  const getKycStatusColor = (status: string) => {
    switch (status) {
      case 'verified': return 'success';
      case 'pending': return 'warning';
      case 'rejected': return 'error';
      default: return 'default';
    }
  };

  const getAccountIcon = (provider: string) => {
    const icons: { [key: string]: React.ReactNode } = {
      Google: <Google />,
      Facebook: <Facebook />,
      Apple: <Apple />,
      GitHub: <GitHub />,
      LinkedIn: <LinkedIn />,
      Twitter: <Twitter />,
    };
    return icons[provider] || <AccountBalance />;
  };

  const getProfileCompleteness = () => {
    const fields = [
      user?.firstName, user?.lastName, user?.email, user?.phoneNumber,
      user?.avatar, user?.kycStatus === 'approved', profileData.linkedAccounts.some(acc => acc.connected)
    ];
    const completed = fields.filter(Boolean).length;
    return Math.round((completed / fields.length) * 100);
  };

  const renderPersonalInfoTab = () => (
    <Box>
      {/* Profile Completeness */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Typography variant="h6">Profile Completeness</Typography>
            <Typography variant="h6" color="primary">
              {getProfileCompleteness()}%
            </Typography>
          </Box>
          <LinearProgress 
            variant="determinate" 
            value={getProfileCompleteness()} 
            sx={{ height: 8, borderRadius: 4 }}
          />
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Complete your profile to unlock all features
          </Typography>
        </CardContent>
      </Card>

      {/* Profile Photo Section */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>Profile Photo</Typography>
          <Box display="flex" alignItems="center" gap={3}>
            <Box position="relative">
              <Avatar
                sx={{ width: 120, height: 120 }}
                src={user?.avatar}
              >
                <Typography variant="h3">
                  {user?.firstName?.charAt(0)}{user?.lastName?.charAt(0)}
                </Typography>
              </Avatar>
              <input
                accept="image/*"
                id="avatar-upload"
                type="file"
                hidden
                onChange={handleAvatarChange}
              />
              <label htmlFor="avatar-upload">
                <IconButton
                  component="span"
                  sx={{
                    position: 'absolute',
                    bottom: 0,
                    right: 0,
                    bgcolor: 'primary.main',
                    color: 'white',
                    '&:hover': { bgcolor: 'primary.dark' },
                  }}
                >
                  <CameraAlt />
                </IconButton>
              </label>
            </Box>
            <Box>
              <Typography variant="h5" gutterBottom>
                {user?.firstName} {user?.lastName}
              </Typography>
              <Typography variant="body1" color="text.secondary" paragraph>
                {user?.email}
              </Typography>
              <Box display="flex" gap={1} flexWrap="wrap">
                {user?.verified && (
                  <Chip
                    icon={<Verified />}
                    label="Email Verified"
                    color="primary"
                    size="small"
                  />
                )}
                <Chip
                  label={profileData.accountLevel}
                  color="secondary"
                  size="small"
                />
                <Chip
                  label={`KYC: ${user?.kycStatus?.toUpperCase()}`}
                  color={getKycStatusColor(user?.kycStatus || 'not_started') as any}
                  size="small"
                />
              </Box>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Personal Information Form */}
      <Card>
        <CardContent>
          <Box display="flex" justifyContent="between" alignItems="center" mb={3}>
            <Typography variant="h6">Personal Information</Typography>
            {!isEditing ? (
              <Button
                startIcon={<Edit />}
                onClick={() => setIsEditing(true)}
                variant="outlined"
              >
                Edit Profile
              </Button>
            ) : (
              <Box display="flex" gap={1}>
                <Button
                  startIcon={<Cancel />}
                  onClick={handleCancelEdit}
                  disabled={loading}
                  color="inherit"
                >
                  Cancel
                </Button>
                <Button
                  variant="contained"
                  startIcon={loading ? <CircularProgress size={20} /> : <Save />}
                  onClick={handleSubmit(handleSaveProfile)}
                  disabled={loading || !isDirty}
                >
                  Save Changes
                </Button>
              </Box>
            )}
          </Box>

          <form onSubmit={handleSubmit(handleSaveProfile)}>
            <Grid container spacing={3}>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="firstName"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="First Name"
                      disabled={!isEditing}
                      error={!!errors.firstName}
                      helperText={errors.firstName?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Person />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="lastName"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Last Name"
                      disabled={!isEditing}
                      error={!!errors.lastName}
                      helperText={errors.lastName?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Person />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="email"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Email Address"
                      disabled
                      error={!!errors.email}
                      helperText={errors.email?.message || "Email cannot be changed"}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Email />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="phoneNumber"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Phone Number"
                      disabled={!isEditing}
                      error={!!errors.phoneNumber}
                      helperText={errors.phoneNumber?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Phone />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="dateOfBirth"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Date of Birth"
                      type="date"
                      disabled={!isEditing}
                      error={!!errors.dateOfBirth}
                      helperText={errors.dateOfBirth?.message}
                      InputLabelProps={{ shrink: true }}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <CalendarToday />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="occupation"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Occupation"
                      disabled={!isEditing}
                      error={!!errors.occupation}
                      helperText={errors.occupation?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <BadgeIcon />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
            </Grid>

            <Divider sx={{ my: 3 }} />

            <Typography variant="h6" gutterBottom>
              Address Information
            </Typography>

            <Grid container spacing={3}>
              <Grid item xs={12}>
                <Controller
                  name="address.street"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Street Address"
                      disabled={!isEditing}
                      error={!!errors.address?.street}
                      helperText={errors.address?.street?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <LocationOn />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="address.city"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="City"
                      disabled={!isEditing}
                      error={!!errors.address?.city}
                      helperText={errors.address?.city?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="address.state"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="State/Province"
                      disabled={!isEditing}
                      error={!!errors.address?.state}
                      helperText={errors.address?.state?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="address.postalCode"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Postal Code"
                      disabled={!isEditing}
                      error={!!errors.address?.postalCode}
                      helperText={errors.address?.postalCode?.message}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller
                  name="address.country"
                  control={control}
                  render={({ field }) => (
                    <FormControl fullWidth disabled={!isEditing}>
                      <InputLabel>Country</InputLabel>
                      <Select {...field} label="Country">
                        <MenuItem value="United States">United States</MenuItem>
                        <MenuItem value="Canada">Canada</MenuItem>
                        <MenuItem value="United Kingdom">United Kingdom</MenuItem>
                        <MenuItem value="Germany">Germany</MenuItem>
                        <MenuItem value="France">France</MenuItem>
                        <MenuItem value="Australia">Australia</MenuItem>
                      </Select>
                    </FormControl>
                  )}
                />
              </Grid>
            </Grid>
          </form>
        </CardContent>
      </Card>
    </Box>
  );

  const renderPreferencesTab = () => (
    <Box>
      {/* App Preferences */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>Application Preferences</Typography>
          <Grid container spacing={3}>
            <Grid item xs={12} sm={6}>
              <Controller
                name="preferences.language"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth>
                    <InputLabel>Language</InputLabel>
                    <Select
                      {...field}
                      label="Language"
                      startAdornment={
                        <InputAdornment position="start">
                          <Language />
                        </InputAdornment>
                      }
                    >
                      <MenuItem value="en">English</MenuItem>
                      <MenuItem value="es">Español</MenuItem>
                      <MenuItem value="fr">Français</MenuItem>
                      <MenuItem value="de">Deutsch</MenuItem>
                      <MenuItem value="it">Italiano</MenuItem>
                      <MenuItem value="pt">Português</MenuItem>
                    </Select>
                  </FormControl>
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="preferences.currency"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth>
                    <InputLabel>Default Currency</InputLabel>
                    <Select
                      {...field}
                      label="Default Currency"
                      startAdornment={
                        <InputAdornment position="start">
                          <AttachMoney />
                        </InputAdornment>
                      }
                    >
                      <MenuItem value="USD">USD - US Dollar</MenuItem>
                      <MenuItem value="EUR">EUR - Euro</MenuItem>
                      <MenuItem value="GBP">GBP - British Pound</MenuItem>
                      <MenuItem value="CAD">CAD - Canadian Dollar</MenuItem>
                      <MenuItem value="AUD">AUD - Australian Dollar</MenuItem>
                      <MenuItem value="JPY">JPY - Japanese Yen</MenuItem>
                    </Select>
                  </FormControl>
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="preferences.timezone"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth>
                    <InputLabel>Timezone</InputLabel>
                    <Select {...field} label="Timezone">
                      <MenuItem value="America/New_York">Eastern Time (ET)</MenuItem>
                      <MenuItem value="America/Chicago">Central Time (CT)</MenuItem>
                      <MenuItem value="America/Denver">Mountain Time (MT)</MenuItem>
                      <MenuItem value="America/Los_Angeles">Pacific Time (PT)</MenuItem>
                      <MenuItem value="Europe/London">London (GMT)</MenuItem>
                      <MenuItem value="Europe/Paris">Paris (CET)</MenuItem>
                    </Select>
                  </FormControl>
                )}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller
                name="preferences.theme"
                control={control}
                render={({ field }) => (
                  <FormControl fullWidth>
                    <InputLabel>Theme</InputLabel>
                    <Select
                      {...field}
                      label="Theme"
                      startAdornment={
                        <InputAdornment position="start">
                          <Palette />
                        </InputAdornment>
                      }
                    >
                      <MenuItem value="light">Light</MenuItem>
                      <MenuItem value="dark">Dark</MenuItem>
                      <MenuItem value="system">System Default</MenuItem>
                    </Select>
                  </FormControl>
                )}
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Notification Preferences */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Notification Preferences</Typography>
          <List>
            <ListItem>
              <ListItemIcon>
                <Email />
              </ListItemIcon>
              <ListItemText
                primary="Email Notifications"
                secondary="Receive notifications via email"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.emailNotifications}
                  onChange={() => handlePreferenceChange('emailNotifications')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Phone />
              </ListItemIcon>
              <ListItemText
                primary="SMS Notifications"
                secondary="Receive notifications via text message"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.smsNotifications}
                  onChange={() => handlePreferenceChange('smsNotifications')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Notifications />
              </ListItemIcon>
              <ListItemText
                primary="Push Notifications"
                secondary="Receive push notifications in browser/app"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.pushNotifications}
                  onChange={() => handlePreferenceChange('pushNotifications')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <Divider />
            <ListItem>
              <ListItemIcon>
                <SwapHoriz />
              </ListItemIcon>
              <ListItemText
                primary="Transaction Alerts"
                secondary="Get notified of all account transactions"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.transactionAlerts}
                  onChange={() => handlePreferenceChange('transactionAlerts')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Security />
              </ListItemIcon>
              <ListItemText
                primary="Security Alerts"
                secondary="Get notified of security-related events"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.securityAlerts}
                  onChange={() => handlePreferenceChange('securityAlerts')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Timeline />
              </ListItemIcon>
              <ListItemText
                primary="Monthly Statements"
                secondary="Receive monthly account statements"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.monthlyStatements}
                  onChange={() => handlePreferenceChange('monthlyStatements')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <TrendingUp />
              </ListItemIcon>
              <ListItemText
                primary="Marketing Communications"
                secondary="Receive promotional offers and updates"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={profileData.preferences.marketingEmails}
                  onChange={() => handlePreferenceChange('marketingEmails')}
                />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>
    </Box>
  );

  const renderKYCTab = () => (
    <Box>
      {/* KYC Status Overview */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="between" mb={2}>
            <Box>
              <Typography variant="h6" gutterBottom>
                Identity Verification Status
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Complete your identity verification to access all features
              </Typography>
            </Box>
            <Chip
              label={`KYC: ${user?.kycStatus?.toUpperCase()}`}
              color={getKycStatusColor(user?.kycStatus || 'not_started') as any}
              size="medium"
            />
          </Box>
          
          {user?.kycStatus === 'not_started' && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              <Typography variant="body2">
                Your identity verification is not yet complete. Start the process to unlock higher transaction limits.
              </Typography>
              <Button 
                size="small" 
                variant="contained" 
                sx={{ mt: 1 }}
                onClick={() => setShowKYCDialog(true)}
              >
                Start Verification
              </Button>
            </Alert>
          )}
        </CardContent>
      </Card>

      {/* KYC Documents */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>Required Documents</Typography>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Document Type</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Upload Date</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {profileData.kycDocuments.map((doc) => (
                  <TableRow key={doc.id}>
                    <TableCell>
                      <Box display="flex" alignItems="center">
                        <BadgeIcon sx={{ mr: 1 }} />
                        {doc.type}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={doc.status}
                        color={getKycStatusColor(doc.status) as any}
                        size="small"
                        icon={doc.status === 'verified' ? <CheckCircle /> : doc.status === 'pending' ? <Info /> : <ErrorOutline />}
                      />
                    </TableCell>
                    <TableCell>
                      {doc.uploadDate ? format(new Date(doc.uploadDate), 'MMM dd, yyyy') : '-'}
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        startIcon={doc.status === 'verified' ? <Visibility /> : <Upload />}
                        disabled={doc.status === 'verified'}
                      >
                        {doc.status === 'verified' ? 'View' : 'Upload'}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Account Limits */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Account Limits</Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Box>
                <Typography variant="body2" color="text.secondary">Daily Transaction Limit</Typography>
                <Typography variant="h6">
                  ${user?.kycStatus === 'approved' ? '50,000' : '1,000'}
                </Typography>
                <LinearProgress 
                  variant="determinate" 
                  value={25} 
                  sx={{ mt: 1, height: 6, borderRadius: 3 }}
                />
                <Typography variant="caption" color="text.secondary">
                  $250 used today
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Box>
                <Typography variant="body2" color="text.secondary">Monthly Transaction Limit</Typography>
                <Typography variant="h6">
                  ${user?.kycStatus === 'approved' ? '500,000' : '10,000'}
                </Typography>
                <LinearProgress 
                  variant="determinate" 
                  value={60} 
                  sx={{ mt: 1, height: 6, borderRadius: 3 }}
                />
                <Typography variant="caption" color="text.secondary">
                  $6,000 used this month
                </Typography>
              </Box>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Box>
  );

  const renderLinkedAccountsTab = () => (
    <Box>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>Connected Accounts</Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Link your social media and other accounts for easy sign-in and enhanced security
          </Typography>
          
          <List>
            {profileData.linkedAccounts.map((account) => (
              <ListItem key={account.id} divider>
                <ListItemIcon>
                  {getAccountIcon(account.provider)}
                </ListItemIcon>
                <ListItemText
                  primary={account.provider}
                  secondary={
                    account.connected 
                      ? `Connected as ${account.email} • Last used: ${account.lastUsed ? format(new Date(account.lastUsed), 'MMM dd, yyyy') : 'Never'}`
                      : 'Not connected'
                  }
                />
                <ListItemSecondaryAction>
                  <Button
                    variant={account.connected ? 'outlined' : 'contained'}
                    color={account.connected ? 'error' : 'primary'}
                    size="small"
                    onClick={() => handleToggleAccount(account.id)}
                  >
                    {account.connected ? 'Disconnect' : 'Connect'}
                  </Button>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Account Security Benefits</Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}>
              <Box textAlign="center" p={2}>
                <Security sx={{ fontSize: 40, color: 'primary.main', mb: 1 }} />
                <Typography variant="subtitle1" gutterBottom>Enhanced Security</Typography>
                <Typography variant="body2" color="text.secondary">
                  Multi-account authentication provides additional security layers
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={12} sm={4}>
              <Box textAlign="center" p={2}>
                <Verified sx={{ fontSize: 40, color: 'success.main', mb: 1 }} />
                <Typography variant="subtitle1" gutterBottom>Verified Identity</Typography>
                <Typography variant="body2" color="text.secondary">
                  Linked accounts help verify your identity across platforms
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={12} sm={4}>
              <Box textAlign="center" p={2}>
                <AccountBalanceWallet sx={{ fontSize: 40, color: 'info.main', mb: 1 }} />
                <Typography variant="subtitle1" gutterBottom>Easy Recovery</Typography>
                <Typography variant="body2" color="text.secondary">
                  Recover your account easily using linked social accounts
                </Typography>
              </Box>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </Box>
  );

  const renderActivityTab = () => (
    <Box>
      {/* Account Statistics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <SwapHoriz sx={{ fontSize: 40, color: 'primary.main', mb: 1 }} />
              <Typography variant="h4">{profileData.totalTransactions.toLocaleString()}</Typography>
              <Typography variant="body2" color="text.secondary">Total Transactions</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <AttachMoney sx={{ fontSize: 40, color: 'success.main', mb: 1 }} />
              <Typography variant="h4">${profileData.totalVolume.toLocaleString()}</Typography>
              <Typography variant="body2" color="text.secondary">Total Volume</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Timeline sx={{ fontSize: 40, color: 'info.main', mb: 1 }} />
              <Typography variant="h4">${profileData.averageTransactionSize}</Typography>
              <Typography variant="body2" color="text.secondary">Avg Transaction</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <CalendarToday sx={{ fontSize: 40, color: 'warning.main', mb: 1 }} />
              <Typography variant="h4">
                {Math.floor((Date.now() - new Date(profileData.memberSince).getTime()) / (1000 * 60 * 60 * 24))}
              </Typography>
              <Typography variant="body2" color="text.secondary">Days as Member</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Recent Activity */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>Recent Activity</Typography>
          <List>
            {profileData.activityLog.map((activity) => (
              <ListItem key={activity.id}>
                <ListItemIcon>
                  <Badge color="primary" variant="dot">
                    <CheckCircle color="success" />
                  </Badge>
                </ListItemIcon>
                <ListItemText
                  primary={activity.action}
                  secondary={
                    <>
                      <Typography component="span" variant="body2" color="text.primary">
                        {activity.details}
                      </Typography>
                      {' — ' + format(activity.timestamp, 'MMM dd, yyyy HH:mm')}
                    </>
                  }
                />
              </ListItem>
            ))}
          </List>
          <Button variant="outlined" fullWidth sx={{ mt: 2 }}>
            View All Activity
          </Button>
        </CardContent>
      </Card>

      {/* Data Export */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Data Management</Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Download your account data or delete your account
          </Typography>
          <Box display="flex" gap={2}>
            <Button
              startIcon={<Download />}
              variant="outlined"
              onClick={() => setShowDataExportDialog(true)}
            >
              Export Data
            </Button>
            <Button
              startIcon={<Delete />}
              variant="outlined"
              color="error"
            >
              Delete Account
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        My Profile
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        Manage your personal information, preferences, and account settings
      </Typography>

      <Paper sx={{ width: '100%' }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Personal Info" icon={<Person />} />
          <Tab label="Preferences" icon={<Palette />} />
          <Tab label="Identity Verification" icon={<Verified />} />
          <Tab label="Linked Accounts" icon={<AccountBalance />} />
          <Tab label="Activity & Data" icon={<Timeline />} />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          {renderPersonalInfoTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          {renderPreferencesTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          {renderKYCTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={3}>
          {renderLinkedAccountsTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={4}>
          {renderActivityTab()}
        </TabPanel>
      </Paper>

      {/* Avatar Upload Dialog */}
      <Dialog
        open={showAvatarDialog}
        onClose={() => setShowAvatarDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Update Profile Photo</DialogTitle>
        <DialogContent>
          <Box textAlign="center" py={3}>
            {avatarPreview && (
              <Avatar
                src={avatarPreview}
                sx={{ width: 200, height: 200, mx: 'auto', mb: 2 }}
              />
            )}
            <Typography variant="body2" color="text.secondary">
              Choose a photo that clearly shows your face. This helps others recognize you.
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowAvatarDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleUploadAvatar}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : <Upload />}
          >
            Upload Photo
          </Button>
        </DialogActions>
      </Dialog>

      {/* KYC Dialog */}
      <Dialog
        open={showKYCDialog}
        onClose={() => setShowKYCDialog(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Identity Verification</DialogTitle>
        <DialogContent>
          <Stepper activeStep={kycStep} orientation="vertical">
            <Step>
              <StepLabel>Personal Information</StepLabel>
              <StepContent>
                <Typography variant="body2" paragraph>
                  Provide your basic personal information as it appears on your government ID.
                </Typography>
                <Button variant="contained" onClick={() => setKycStep(1)}>
                  Continue
                </Button>
              </StepContent>
            </Step>
            <Step>
              <StepLabel>Upload Documents</StepLabel>
              <StepContent>
                <Typography variant="body2" paragraph>
                  Upload a clear photo of your government-issued ID and proof of address.
                </Typography>
                <Button variant="contained" onClick={() => setKycStep(2)}>
                  Continue
                </Button>
              </StepContent>
            </Step>
            <Step>
              <StepLabel>Verification Complete</StepLabel>
              <StepContent>
                <Typography variant="body2" paragraph>
                  Your documents are being reviewed. This process typically takes 1-2 business days.
                </Typography>
                <Button variant="contained" onClick={() => setShowKYCDialog(false)}>
                  Finish
                </Button>
              </StepContent>
            </Step>
          </Stepper>
        </DialogContent>
      </Dialog>

      {/* Data Export Dialog */}
      <Dialog
        open={showDataExportDialog}
        onClose={() => setShowDataExportDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Export Account Data</DialogTitle>
        <DialogContent>
          <Typography variant="body2" paragraph>
            Download a copy of your account data including profile information, transaction history, and settings.
          </Typography>
          <Alert severity="info" sx={{ mb: 2 }}>
            The exported file will contain personal information. Please store it securely.
          </Alert>
          <Typography variant="body2" color="text.secondary">
            File format: JSON • Estimated size: ~2MB
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDataExportDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleExportData}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : <Download />}
          >
            {loading ? 'Preparing...' : 'Download Data'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UserProfileEnhanced;