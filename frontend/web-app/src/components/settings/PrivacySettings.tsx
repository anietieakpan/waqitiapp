import React, { useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Switch,
  FormControlLabel,
  Button,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormGroup,
  Chip,
  Grid,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import PrivacyTipIcon from '@mui/icons-material/PrivacyTip';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import PersonSearchIcon from '@mui/icons-material/PersonSearch';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import GroupIcon from '@mui/icons-material/Group';
import DownloadIcon from '@mui/icons-material/Download';
import DeleteIcon from '@mui/icons-material/Delete';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SecurityIcon from '@mui/icons-material/Security';
import WarningIcon from '@mui/icons-material/Warning';
import CheckIcon from '@mui/icons-material/Check';
import BlockIcon from '@mui/icons-material/Block';
import ShareIcon from '@mui/icons-material/Share';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import ContactsIcon from '@mui/icons-material/Contacts';
import StorageIcon from '@mui/icons-material/Storage';
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';;
import { format } from 'date-fns';
import toast from 'react-hot-toast';

interface PrivacySettings {
  profileVisibility: 'public' | 'friends' | 'private';
  showEmail: boolean;
  showPhone: boolean;
  showTransactionHistory: boolean;
  allowFriendRequests: boolean;
  allowMessageRequests: boolean;
  dataSharing: {
    analytics: boolean;
    marketing: boolean;
    thirdParty: boolean;
  };
  activityStatus: boolean;
  locationServices: boolean;
  photoTags: boolean;
  contactSync: boolean;
}

interface DataExport {
  id: string;
  requestedAt: string;
  status: 'pending' | 'ready' | 'expired';
  expiresAt?: string;
  size?: string;
}

interface BlockedUser {
  id: string;
  name: string;
  email: string;
  blockedAt: string;
}

const PrivacySettings: React.FC = () => {
  const [settings, setSettings] = useState<PrivacySettings>({
    profileVisibility: 'friends',
    showEmail: false,
    showPhone: false,
    showTransactionHistory: false,
    allowFriendRequests: true,
    allowMessageRequests: true,
    dataSharing: {
      analytics: true,
      marketing: false,
      thirdParty: false,
    },
    activityStatus: true,
    locationServices: false,
    photoTags: true,
    contactSync: false,
  });

  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [showDataRequestDialog, setShowDataRequestDialog] = useState(false);
  const [deleteConfirmation, setDeleteConfirmation] = useState('');
  const [loading, setLoading] = useState(false);

  // Mock data
  const dataExports: DataExport[] = [
    {
      id: '1',
      requestedAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      status: 'ready',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      size: '125 MB',
    },
    {
      id: '2',
      requestedAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
      status: 'expired',
    },
  ];

  const blockedUsers: BlockedUser[] = [
    {
      id: '1',
      name: 'Spam User',
      email: 'spam@example.com',
      blockedAt: new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString(),
    },
  ];

  const handleToggleSetting = (setting: keyof PrivacySettings, value?: any) => {
    if (typeof settings[setting] === 'boolean') {
      setSettings(prev => ({
        ...prev,
        [setting]: !prev[setting],
      }));
    } else if (value !== undefined) {
      setSettings(prev => ({
        ...prev,
        [setting]: value,
      }));
    }
    toast.success('Privacy settings updated');
  };

  const handleToggleDataSharing = (type: keyof typeof settings.dataSharing) => {
    setSettings(prev => ({
      ...prev,
      dataSharing: {
        ...prev.dataSharing,
        [type]: !prev.dataSharing[type],
      },
    }));
    toast.success('Data sharing preferences updated');
  };

  const handleRequestData = async () => {
    setLoading(true);
    try {
      // In a real app, call API
      await new Promise(resolve => setTimeout(resolve, 2000));
      toast.success('Data export requested. You\'ll receive an email when it\'s ready.');
      setShowDataRequestDialog(false);
    } catch (error) {
      toast.error('Failed to request data export');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAccount = async () => {
    if (deleteConfirmation !== 'DELETE') {
      toast.error('Please type DELETE to confirm');
      return;
    }

    setLoading(true);
    try {
      // In a real app, call API
      await new Promise(resolve => setTimeout(resolve, 2000));
      toast.success('Account deletion initiated. You\'ll receive a confirmation email.');
      setShowDeleteDialog(false);
    } catch (error) {
      toast.error('Failed to delete account');
    } finally {
      setLoading(false);
    }
  };

  const handleUnblockUser = (userId: string) => {
    toast.success('User unblocked');
  };

  const handleDownloadData = (exportId: string) => {
    toast.success('Download started');
  };

  const visibilityOptions = [
    { value: 'public', label: 'Public', description: 'Anyone can see your profile' },
    { value: 'friends', label: 'Friends Only', description: 'Only your contacts can see your profile' },
    { value: 'private', label: 'Private', description: 'No one can see your profile' },
  ];

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Privacy Settings
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Control your privacy and data sharing preferences
      </Typography>

      {/* Profile Visibility */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Profile Visibility
          </Typography>
          <Grid container spacing={2}>
            {visibilityOptions.map((option) => (
              <Grid item xs={12} sm={4} key={option.value}>
                <Button
                  fullWidth
                  variant={settings.profileVisibility === option.value ? 'contained' : 'outlined'}
                  onClick={() => handleToggleSetting('profileVisibility', option.value)}
                  sx={{ py: 2, textAlign: 'left' }}
                >
                  <Box>
                    <Typography variant="subtitle2">{option.label}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {option.description}
                    </Typography>
                  </Box>
                </Button>
              </Grid>
            ))}
          </Grid>

          <Divider sx={{ my: 3 }} />

          <Typography variant="subtitle1" gutterBottom>
            Profile Information
          </Typography>
          <FormGroup>
            <FormControlLabel
              control={
                <Switch
                  checked={settings.showEmail}
                  onChange={() => handleToggleSetting('showEmail')}
                />
              }
              label="Show email address on profile"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={settings.showPhone}
                  onChange={() => handleToggleSetting('showPhone')}
                />
              }
              label="Show phone number on profile"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={settings.showTransactionHistory}
                  onChange={() => handleToggleSetting('showTransactionHistory')}
                />
              }
              label="Show transaction history to friends"
            />
          </FormGroup>
        </CardContent>
      </Card>

      {/* Communication Preferences */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Communication Preferences
          </Typography>
          <List>
            <ListItem>
              <ListItemIcon>
                <PersonSearch />
              </ListItemIcon>
              <ListItemText
                primary="Friend Requests"
                secondary="Allow others to send you friend requests"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.allowFriendRequests}
                  onChange={() => handleToggleSetting('allowFriendRequests')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Group />
              </ListItemIcon>
              <ListItemText
                primary="Message Requests"
                secondary="Allow non-friends to send you messages"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.allowMessageRequests}
                  onChange={() => handleToggleSetting('allowMessageRequests')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Visibility />
              </ListItemIcon>
              <ListItemText
                primary="Activity Status"
                secondary="Show when you're active on Waqiti"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.activityStatus}
                  onChange={() => handleToggleSetting('activityStatus')}
                />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Data Sharing */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Data Sharing & Permissions
          </Typography>
          <Alert severity="info" sx={{ mb: 2 }}>
            We take your privacy seriously. Your financial data is never shared without your explicit consent.
          </Alert>
          <List>
            <ListItem>
              <ListItemIcon>
                <Analytics />
              </ListItemIcon>
              <ListItemText
                primary="Analytics"
                secondary="Help us improve by sharing anonymous usage data"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.dataSharing.analytics}
                  onChange={() => handleToggleDataSharing('analytics')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Share />
              </ListItemIcon>
              <ListItemText
                primary="Marketing Communications"
                secondary="Receive personalized offers and recommendations"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.dataSharing.marketing}
                  onChange={() => handleToggleDataSharing('marketing')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Group />
              </ListItemIcon>
              <ListItemText
                primary="Third-Party Services"
                secondary="Share data with integrated services you use"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.dataSharing.thirdParty}
                  onChange={() => handleToggleDataSharing('thirdParty')}
                />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* App Permissions */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            App Permissions
          </Typography>
          <List>
            <ListItem>
              <ListItemIcon>
                <LocationOn />
              </ListItemIcon>
              <ListItemText
                primary="Location Services"
                secondary="Use location for security and nearby features"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.locationServices}
                  onChange={() => handleToggleSetting('locationServices')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <PhotoCamera />
              </ListItemIcon>
              <ListItemText
                primary="Photo Tags"
                secondary="Allow friends to tag you in transaction photos"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.photoTags}
                  onChange={() => handleToggleSetting('photoTags')}
                />
              </ListItemSecondaryAction>
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Contacts />
              </ListItemIcon>
              <ListItemText
                primary="Contact Sync"
                secondary="Sync phone contacts to find friends on Waqiti"
              />
              <ListItemSecondaryAction>
                <Switch
                  checked={settings.contactSync}
                  onChange={() => handleToggleSetting('contactSync')}
                />
              </ListItemSecondaryAction>
            </ListItem>
          </List>
        </CardContent>
      </Card>

      {/* Blocked Users */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMore />}>
          <Typography variant="h6">
            Blocked Users ({blockedUsers.length})
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          {blockedUsers.length > 0 ? (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>User</TableCell>
                    <TableCell>Blocked Date</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {blockedUsers.map((user) => (
                    <TableRow key={user.id}>
                      <TableCell>
                        <Box>
                          <Typography variant="body2">{user.name}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {user.email}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        {format(new Date(user.blockedAt), 'MMM dd, yyyy')}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          onClick={() => handleUnblockUser(user.id)}
                        >
                          Unblock
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <Typography variant="body2" color="text.secondary">
              No blocked users
            </Typography>
          )}
        </AccordionDetails>
      </Accordion>

      {/* Data Management */}
      <Card sx={{ mt: 3, mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Data Management
          </Typography>
          
          <Box mb={3}>
            <Typography variant="subtitle1" gutterBottom>
              Download Your Data
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Request a copy of all your Waqiti data including transactions, profile information, and settings.
            </Typography>
            <Button
              variant="outlined"
              startIcon={<CloudDownload />}
              onClick={() => setShowDataRequestDialog(true)}
            >
              Request Data Export
            </Button>
          </Box>

          {dataExports.length > 0 && (
            <Box mb={3}>
              <Typography variant="subtitle2" gutterBottom>
                Export History
              </Typography>
              <List>
                {dataExports.map((export_) => (
                  <ListItem key={export_.id}>
                    <ListItemIcon>
                      <Storage />
                    </ListItemIcon>
                    <ListItemText
                      primary={`Requested ${format(new Date(export_.requestedAt), 'MMM dd, yyyy')}`}
                      secondary={
                        export_.status === 'ready' 
                          ? `Ready to download (${export_.size}) - Expires ${format(new Date(export_.expiresAt!), 'MMM dd')}`
                          : export_.status === 'pending'
                          ? 'Processing...'
                          : 'Expired'
                      }
                    />
                    {export_.status === 'ready' && (
                      <ListItemSecondaryAction>
                        <Button
                          size="small"
                          startIcon={<Download />}
                          onClick={() => handleDownloadData(export_.id)}
                        >
                          Download
                        </Button>
                      </ListItemSecondaryAction>
                    )}
                  </ListItem>
                ))}
              </List>
            </Box>
          )}

          <Divider sx={{ my: 3 }} />

          <Box>
            <Typography variant="subtitle1" gutterBottom color="error">
              Delete Account
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Permanently delete your Waqiti account and all associated data. This action cannot be undone.
            </Typography>
            <Button
              variant="outlined"
              color="error"
              startIcon={<Delete />}
              onClick={() => setShowDeleteDialog(true)}
            >
              Delete My Account
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Request Data Dialog */}
      <Dialog
        open={showDataRequestDialog}
        onClose={() => setShowDataRequestDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Request Data Export</DialogTitle>
        <DialogContent>
          <Typography variant="body2" paragraph>
            We'll prepare a download containing all your Waqiti data including:
          </Typography>
          <List dense>
            <ListItem>
              <ListItemIcon>
                <Check fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="Profile information" />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Check fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="Transaction history" />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Check fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="Contacts and messages" />
            </ListItem>
            <ListItem>
              <ListItemIcon>
                <Check fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="Settings and preferences" />
            </ListItem>
          </List>
          <Alert severity="info" sx={{ mt: 2 }}>
            Data export typically takes 24-48 hours. You'll receive an email when it's ready.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDataRequestDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleRequestData}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            Request Export
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Account Dialog */}
      <Dialog
        open={showDeleteDialog}
        onClose={() => setShowDeleteDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center" color="error.main">
            <Warning sx={{ mr: 1 }} />
            Delete Account
          </Box>
        </DialogTitle>
        <DialogContent>
          <Alert severity="error" sx={{ mb: 2 }}>
            This action is permanent and cannot be undone.
          </Alert>
          <Typography variant="body2" paragraph>
            Deleting your account will:
          </Typography>
          <List dense>
            <ListItem>
              <ListItemText primary="• Remove all your personal information" />
            </ListItem>
            <ListItem>
              <ListItemText primary="• Cancel all pending transactions" />
            </ListItem>
            <ListItem>
              <ListItemText primary="• Delete your transaction history" />
            </ListItem>
            <ListItem>
              <ListItemText primary="• Remove you from all contacts" />
            </ListItem>
          </List>
          <Typography variant="body2" paragraph sx={{ mt: 2 }}>
            To confirm, type <strong>DELETE</strong> below:
          </Typography>
          <TextField
            fullWidth
            value={deleteConfirmation}
            onChange={(e) => setDeleteConfirmation(e.target.value)}
            placeholder="Type DELETE to confirm"
            error={deleteConfirmation !== '' && deleteConfirmation !== 'DELETE'}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDeleteDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDeleteAccount}
            disabled={loading || deleteConfirmation !== 'DELETE'}
            startIcon={loading ? <CircularProgress size={20} /> : <Delete />}
          >
            Delete Account
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PrivacySettings;