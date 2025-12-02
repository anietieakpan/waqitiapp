import React, { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Typography,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  TextField,
  Alert,
  CircularProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import SecurityIcon from '@mui/icons-material/Security';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import LaptopIcon from '@mui/icons-material/Laptop';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import KeyIcon from '@mui/icons-material/Key';;
import { toast } from 'react-hot-toast';
import biometricAuthService, { BiometricCapability } from '@/services/biometricAuthService';

/**
 * Biometric Setup Component
 *
 * FEATURES:
 * - Step-by-step enrollment wizard
 * - Capability detection
 * - Device naming
 * - Browser compatibility check
 * - Enrollment verification
 * - Fallback guidance
 *
 * FLOW:
 * 1. Check capability
 * 2. Show device info
 * 3. Name device
 * 4. Enroll biometric
 * 5. Verify enrollment
 * 6. Success confirmation
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

interface BiometricSetupProps {
  onComplete?: () => void;
  onCancel?: () => void;
}

export const BiometricSetup: React.FC<BiometricSetupProps> = ({
  onComplete,
  onCancel,
}) => {
  const [activeStep, setActiveStep] = useState(0);
  const [capability, setCapability] = useState<BiometricCapability | null>(null);
  const [deviceName, setDeviceName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enrolled, setEnrolled] = useState(false);
  const [credentialId, setCredentialId] = useState<string | null>(null);
  const [showUnsupportedDialog, setShowUnsupportedDialog] = useState(false);

  const steps = [
    'Check Compatibility',
    'Device Information',
    'Enroll Biometric',
    'Verification',
    'Complete',
  ];

  useEffect(() => {
    checkCapability();
  }, []);

  const checkCapability = async () => {
    setLoading(true);
    try {
      const cap = await biometricAuthService.checkCapability();
      setCapability(cap);

      if (!cap.available) {
        setError(cap.errorMessage || 'Biometric authentication is not available');
        setShowUnsupportedDialog(true);
      } else {
        setActiveStep(1);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to check biometric capability');
    } finally {
      setLoading(false);
    }
  };

  const handleEnroll = async () => {
    if (!deviceName.trim()) {
      toast.error('Please enter a device name');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await biometricAuthService.enrollDevice(deviceName);

      if (response.success) {
        setCredentialId(response.credentialId);
        setEnrolled(true);
        setActiveStep(3);
        toast.success('Biometric enrolled successfully!');
      }
    } catch (err: any) {
      setError(err.message || 'Failed to enroll biometric');
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async () => {
    setLoading(true);
    setError(null);

    try {
      // Test authentication with the newly enrolled credential
      const result = await biometricAuthService.authenticate();

      if (result.authenticated) {
        setActiveStep(4);
        toast.success('Verification successful!');
      } else {
        throw new Error('Verification failed');
      }
    } catch (err: any) {
      setError('Verification failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleComplete = () => {
    if (onComplete) {
      onComplete();
    }
  };

  const handleCancel = () => {
    if (onCancel) {
      onCancel();
    }
  };

  const getAuthenticatorIcon = () => {
    if (!capability) return <Fingerprint />;

    if (capability.platformAuthenticator) {
      const platform = navigator.platform;
      if (platform.includes('Mac') || platform.includes('iPhone') || platform.includes('iPad')) {
        return <Fingerprint sx={{ fontSize: 48 }} />;
      } else if (platform.includes('Win')) {
        return <Laptop sx={{ fontSize: 48 }} />;
      }
    }

    return <Security sx={{ fontSize: 48 }} />;
  };

  const getAuthenticatorName = (): string => {
    if (!capability) return 'Unknown';

    const platform = navigator.platform;
    if (platform.includes('Mac')) return 'Touch ID';
    if (platform.includes('Win')) return 'Windows Hello';
    if (platform.includes('iPhone') || platform.includes('iPad')) return 'Face ID / Touch ID';
    if (platform.includes('Android')) return 'Fingerprint / Face Unlock';

    return 'Platform Authenticator';
  };

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Card>
        <CardContent>
          {/* Header */}
          <Box sx={{ mb: 4, textAlign: 'center' }}>
            <Box sx={{ mb: 2 }}>{getAuthenticatorIcon()}</Box>
            <Typography variant="h5" gutterBottom>
              Set Up Biometric Authentication
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Add {getAuthenticatorName()} for faster and more secure login
            </Typography>
          </Box>

          {/* Stepper */}
          <Stepper activeStep={activeStep} orientation="vertical">
            {/* Step 1: Check Compatibility */}
            <Step>
              <StepLabel>Check Compatibility</StepLabel>
              <StepContent>
                {loading ? (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 2 }}>
                    <CircularProgress size={24} />
                    <Typography>Checking device compatibility...</Typography>
                  </Box>
                ) : capability?.available ? (
                  <Box>
                    <Alert severity="success" sx={{ mb: 2 }}>
                      Your device supports biometric authentication!
                    </Alert>
                    <List dense>
                      <ListItem>
                        <ListItemIcon>
                          <CheckCircle color="success" />
                        </ListItemIcon>
                        <ListItemText
                          primary="Platform Authenticator"
                          secondary={getAuthenticatorName()}
                        />
                      </ListItem>
                      <ListItem>
                        <ListItemIcon>
                          <CheckCircle color="success" />
                        </ListItemIcon>
                        <ListItemText
                          primary="Browser"
                          secondary={capability.browserName}
                        />
                      </ListItem>
                    </List>
                  </Box>
                ) : (
                  <Alert severity="error">
                    {error || 'Biometric authentication is not available on this device'}
                  </Alert>
                )}
              </StepContent>
            </Step>

            {/* Step 2: Device Information */}
            <Step>
              <StepLabel>Device Information</StepLabel>
              <StepContent>
                <Typography variant="body2" gutterBottom>
                  Give this device a name so you can identify it later
                </Typography>
                <TextField
                  fullWidth
                  label="Device Name"
                  value={deviceName}
                  onChange={(e) => setDeviceName(e.target.value)}
                  placeholder={`My ${navigator.platform.includes('Mac') ? 'MacBook' : 'Device'}`}
                  sx={{ mt: 2, mb: 2 }}
                  disabled={loading}
                  autoFocus
                />
                <Box sx={{ mb: 2 }}>
                  <Button
                    variant="contained"
                    onClick={() => setActiveStep(2)}
                    disabled={!deviceName.trim() || loading}
                  >
                    Continue
                  </Button>
                  <Button onClick={handleCancel} sx={{ ml: 1 }}>
                    Cancel
                  </Button>
                </Box>
              </StepContent>
            </Step>

            {/* Step 3: Enroll Biometric */}
            <Step>
              <StepLabel>Enroll Biometric</StepLabel>
              <StepContent>
                <Alert severity="info" icon={<Info />} sx={{ mb: 2 }}>
                  You'll be prompted to authenticate with {getAuthenticatorName()}. Follow your
                  device's instructions.
                </Alert>

                {error && (
                  <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                  </Alert>
                )}

                <List dense sx={{ mb: 2 }}>
                  <ListItem>
                    <ListItemIcon>
                      <Security />
                    </ListItemIcon>
                    <ListItemText
                      primary="Secure Storage"
                      secondary="Your biometric data stays on your device"
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <Key />
                    </ListItemIcon>
                    <ListItemText
                      primary="Public Key Cryptography"
                      secondary="Uses WebAuthn standard for authentication"
                    />
                  </ListItem>
                </List>

                <Box sx={{ mb: 2 }}>
                  <Button
                    variant="contained"
                    onClick={handleEnroll}
                    disabled={loading || enrolled}
                    startIcon={loading ? <CircularProgress size={20} /> : <Fingerprint />}
                  >
                    {loading ? 'Enrolling...' : enrolled ? 'Enrolled' : 'Enroll Now'}
                  </Button>
                  <Button onClick={() => setActiveStep(1)} sx={{ ml: 1 }} disabled={loading}>
                    Back
                  </Button>
                </Box>
              </StepContent>
            </Step>

            {/* Step 4: Verification */}
            <Step>
              <StepLabel>Verification</StepLabel>
              <StepContent>
                <Typography variant="body2" gutterBottom>
                  Let's verify your biometric authentication works correctly
                </Typography>

                {error && (
                  <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                  </Alert>
                )}

                <Box sx={{ mb: 2 }}>
                  <Button
                    variant="contained"
                    onClick={handleVerify}
                    disabled={loading}
                    startIcon={loading ? <CircularProgress size={20} /> : <Security />}
                  >
                    {loading ? 'Verifying...' : 'Verify Now'}
                  </Button>
                  <Button onClick={() => setActiveStep(2)} sx={{ ml: 1 }} disabled={loading}>
                    Skip
                  </Button>
                </Box>
              </StepContent>
            </Step>

            {/* Step 5: Complete */}
            <Step>
              <StepLabel>Complete</StepLabel>
              <StepContent>
                <Alert severity="success" icon={<CheckCircle />} sx={{ mb: 2 }}>
                  Biometric authentication has been set up successfully!
                </Alert>

                <Box sx={{ bgcolor: 'grey.50', p: 2, borderRadius: 1, mb: 2 }}>
                  <Typography variant="body2" fontWeight="bold" gutterBottom>
                    Device: {deviceName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Credential ID: {credentialId?.substring(0, 20)}...
                  </Typography>
                </Box>

                <Typography variant="body2" gutterBottom>
                  You can now use {getAuthenticatorName()} to sign in quickly and securely.
                </Typography>

                <Box sx={{ mt: 2 }}>
                  <Button variant="contained" onClick={handleComplete}>
                    Done
                  </Button>
                </Box>
              </StepContent>
            </Step>
          </Stepper>
        </CardContent>
      </Card>

      {/* Unsupported Dialog */}
      <Dialog open={showUnsupportedDialog} onClose={() => setShowUnsupportedDialog(false)}>
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <ErrorIcon color="error" />
            Biometric Not Supported
          </Box>
        </DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            Biometric authentication is not available on this device or browser.
          </Alert>

          <Typography variant="body2" gutterBottom>
            <strong>Requirements:</strong>
          </Typography>
          <List dense>
            <ListItem>
              <ListItemText
                primary="Supported Browser"
                secondary="Chrome 67+, Edge 18+, Firefox 60+, Safari 14+"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Biometric Hardware"
                secondary="Touch ID, Windows Hello, Face ID, or fingerprint scanner"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Secure Context"
                secondary="HTTPS connection required"
              />
            </ListItem>
          </List>

          <Typography variant="body2" sx={{ mt: 2 }}>
            You can continue using password authentication or try on a different device.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowUnsupportedDialog(false)}>Close</Button>
          <Button variant="contained" onClick={handleCancel}>
            Use Password Login
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default BiometricSetup;
