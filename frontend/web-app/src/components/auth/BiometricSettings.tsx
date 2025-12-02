import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Button,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  CircularProgress,
  Tooltip,
  Divider,
} from '@mui/material';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import LaptopIcon from '@mui/icons-material/Laptop';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import KeyIcon from '@mui/icons-material/Key';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import AddIcon from '@mui/icons-material/Add';
import SecurityIcon from '@mui/icons-material/Security';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';;
import { format } from 'date-fns';
import { toast } from 'react-hot-toast';
import biometricAuthService, { BiometricDevice } from '@/services/biometricAuthService';
import BiometricSetup from './BiometricSetup';

/**
 * Biometric Settings Component
 *
 * FEATURES:
 * - List registered devices
 * - Add new device
 * - Remove device
 * - Rename device
 * - View device details
 * - Last used tracking
 * - Device type icons
 *
 * SECURITY:
 * - Require authentication for removal
 * - Show last used timestamps
 * - Credential ID display
 * - Device verification status
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

export const BiometricSettings: React.FC = () => {
  const [devices, setDevices] = useState<BiometricDevice[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDevice, setSelectedDevice] = useState<BiometricDevice | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [addDeviceDialogOpen, setAddDeviceDialogOpen] = useState(false);
  const [newDeviceName, setNewDeviceName] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [renaming, setRenaming] = useState(false);

  useEffect(() => {
    loadDevices();
  }, []);

  const loadDevices = async () => {
    setLoading(true);
    try {
      const deviceList = await biometricAuthService.getDevices();
      setDevices(deviceList);
    } catch (error) {
      console.error('Failed to load devices:', error);
      toast.error('Failed to load biometric devices');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteDevice = async () => {
    if (!selectedDevice) return;

    setDeleting(true);
    try {
      await biometricAuthService.removeDevice(selectedDevice.credentialId);
      await loadDevices();
      setDeleteDialogOpen(false);
      setSelectedDevice(null);
      toast.success('Device removed successfully');
    } catch (error) {
      console.error('Failed to delete device:', error);
      toast.error('Failed to remove device');
    } finally {
      setDeleting(false);
    }
  };

  const handleRenameDevice = async () => {
    if (!selectedDevice || !newDeviceName.trim()) return;

    setRenaming(true);
    try {
      await biometricAuthService.updateDeviceName(
        selectedDevice.credentialId,
        newDeviceName
      );
      await loadDevices();
      setRenameDialogOpen(false);
      setSelectedDevice(null);
      setNewDeviceName('');
    } catch (error) {
      console.error('Failed to rename device:', error);
      toast.error('Failed to rename device');
    } finally {
      setRenaming(false);
    }
  };

  const handleAddDevice = () => {
    setAddDeviceDialogOpen(true);
  };

  const handleAddDeviceComplete = async () => {
    setAddDeviceDialogOpen(false);
    await loadDevices();
  };

  const getDeviceIcon = (device: BiometricDevice) => {
    switch (device.deviceType) {
      case 'PLATFORM':
        const platform = navigator.platform;
        if (platform.includes('Mac') || platform.includes('iPhone') || platform.includes('iPad')) {
          return <Smartphone color="primary" />;
        }
        return <Laptop color="primary" />;
      case 'SECURITY_KEY':
        return <Key color="primary" />;
      default:
        return <Fingerprint color="primary" />;
    }
  };

  const getAuthenticatorTypeLabel = (type: string) => {
    switch (type) {
      case 'FINGERPRINT':
        return 'Fingerprint';
      case 'FACE':
        return 'Face Recognition';
      case 'PIN':
        return 'PIN';
      default:
        return 'Biometric';
    }
  };

  const getDeviceTypeChip = (type: string) => {
    switch (type) {
      case 'PLATFORM':
        return <Chip label="This Device" size="small" color="success" />;
      case 'CROSS_PLATFORM':
        return <Chip label="Cross-Platform" size="small" color="info" />;
      case 'SECURITY_KEY':
        return <Chip label="Security Key" size="small" color="warning" />;
      default:
        return <Chip label="Unknown" size="small" />;
    }
  };

  return (
    <Box>
      <Card>
        <CardContent>
          {/* Header */}
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
            <Box>
              <Typography variant="h6" gutterBottom>
                Biometric Authentication
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Manage devices that can authenticate with biometrics
              </Typography>
            </Box>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={handleAddDevice}
            >
              Add Device
            </Button>
          </Box>

          {/* Info Alert */}
          <Alert severity="info" icon={<Security />} sx={{ mb: 3 }}>
            Biometric data never leaves your device. We use WebAuthn for secure,
            passwordless authentication.
          </Alert>

          {/* Device List */}
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress />
            </Box>
          ) : devices.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Fingerprint sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
              <Typography variant="body1" color="text.secondary" gutterBottom>
                No biometric devices registered
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Add a device to enable fast and secure biometric login
              </Typography>
              <Button
                variant="outlined"
                startIcon={<Add />}
                onClick={handleAddDevice}
              >
                Add Your First Device
              </Button>
            </Box>
          ) : (
            <List>
              {devices.map((device, index) => (
                <React.Fragment key={device.credentialId}>
                  <ListItem
                    sx={{
                      borderRadius: 1,
                      '&:hover': {
                        bgcolor: 'action.hover',
                      },
                    }}
                  >
                    <ListItemIcon>
                      {getDeviceIcon(device)}
                    </ListItemIcon>
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body1" fontWeight="medium">
                            {device.deviceName}
                          </Typography>
                          {getDeviceTypeChip(device.deviceType)}
                          {device.userVerified && (
                            <Tooltip title="User verification enabled">
                              <CheckCircle color="success" fontSize="small" />
                            </Tooltip>
                          )}
                        </Box>
                      }
                      secondary={
                        <React.Fragment>
                          <Typography variant="body2" color="text.secondary" component="span">
                            {getAuthenticatorTypeLabel(device.authenticatorType)}
                          </Typography>
                          <br />
                          <Typography variant="caption" color="text.secondary">
                            Added: {format(new Date(device.createdAt), 'MMM dd, yyyy')}
                          </Typography>
                          {device.lastUsedAt && (
                            <>
                              {' • '}
                              <Typography variant="caption" color="text.secondary">
                                Last used: {biometricAuthService.formatLastUsed(device.lastUsedAt)}
                              </Typography>
                            </>
                          )}
                          <br />
                          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                            ID: {device.credentialId.substring(0, 20)}...
                          </Typography>
                        </React.Fragment>
                      }
                    />
                    <ListItemSecondaryAction>
                      <Tooltip title="Rename device">
                        <IconButton
                          edge="end"
                          onClick={() => {
                            setSelectedDevice(device);
                            setNewDeviceName(device.deviceName);
                            setRenameDialogOpen(true);
                          }}
                        >
                          <Edit />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Remove device">
                        <IconButton
                          edge="end"
                          onClick={() => {
                            setSelectedDevice(device);
                            setDeleteDialogOpen(true);
                          }}
                          sx={{ ml: 1 }}
                        >
                          <Delete />
                        </IconButton>
                      </Tooltip>
                    </ListItemSecondaryAction>
                  </ListItem>
                  {index < devices.length - 1 && <Divider variant="inset" component="li" />}
                </React.Fragment>
              ))}
            </List>
          )}

          {/* Security Note */}
          {devices.length > 0 && (
            <Box sx={{ mt: 3, p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
              <Typography variant="caption" color="text.secondary">
                <strong>Security Tip:</strong> If you lose access to a device, remove it
                immediately to prevent unauthorized access. You can always add it back later.
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => !deleting && setDeleteDialogOpen(false)}
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Warning color="warning" />
            Remove Biometric Device
          </Box>
        </DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            You will no longer be able to use this device for biometric authentication.
          </Alert>
          {selectedDevice && (
            <Box>
              <Typography variant="body2" gutterBottom>
                Are you sure you want to remove:
              </Typography>
              <Typography variant="body1" fontWeight="bold" gutterBottom>
                {selectedDevice.deviceName}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {getAuthenticatorTypeLabel(selectedDevice.authenticatorType)} •{' '}
                Added {format(new Date(selectedDevice.createdAt), 'MMM dd, yyyy')}
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)} disabled={deleting}>
            Cancel
          </Button>
          <Button
            onClick={handleDeleteDevice}
            color="error"
            variant="contained"
            disabled={deleting}
            startIcon={deleting ? <CircularProgress size={20} /> : <Delete />}
          >
            {deleting ? 'Removing...' : 'Remove Device'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Rename Dialog */}
      <Dialog
        open={renameDialogOpen}
        onClose={() => !renaming && setRenameDialogOpen(false)}
      >
        <DialogTitle>Rename Device</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Device Name"
            value={newDeviceName}
            onChange={(e) => setNewDeviceName(e.target.value)}
            disabled={renaming}
            autoFocus
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRenameDialogOpen(false)} disabled={renaming}>
            Cancel
          </Button>
          <Button
            onClick={handleRenameDevice}
            variant="contained"
            disabled={!newDeviceName.trim() || renaming}
            startIcon={renaming ? <CircularProgress size={20} /> : <Edit />}
          >
            {renaming ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Add Device Dialog */}
      <Dialog
        open={addDeviceDialogOpen}
        onClose={() => setAddDeviceDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <BiometricSetup
          onComplete={handleAddDeviceComplete}
          onCancel={() => setAddDeviceDialogOpen(false)}
        />
      </Dialog>
    </Box>
  );
};

export default BiometricSettings;
