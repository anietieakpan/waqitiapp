import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  Stepper,
  Step,
  StepLabel,
  Card,
  CardContent,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  IconButton,
  CircularProgress,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Grid,
  Avatar,
  Divider,
  LinearProgress,
  useTheme,
  alpha,
  FormControlLabel,
  Switch,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import UsbIcon from '@mui/icons-material/UsbIcon';
import SecurityIcon from '@mui/icons-material/Security';
import CheckIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import LockIcon from '@mui/icons-material/Lock';
import ShieldIcon from '@mui/icons-material/Shield';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';
import VerifiedIcon from '@mui/icons-material/VerifiedUser';
import SettingsIcon from '@mui/icons-material/Settings';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import TimerIcon from '@mui/icons-material/Timer';
import CloudSyncIcon from '@mui/icons-material/CloudSync';;
import { useSecurityService } from '../../hooks/useSecurityService';
import { HardwareKeyInfo, DeviceType } from '../../types/security';
import QRCode from 'react-qr-code';

interface HardwareKeySetupProps {
  onSetupComplete?: (device: HardwareKeyInfo) => void;
  onCancel?: () => void;
  existingDevices?: HardwareKeyInfo[];
}

const setupSteps = [
  'Connect Device',
  'Verify Device',
  'Configure Security',
  'Test & Complete',
];

const supportedDevices = [
  {
    type: 'yubikey',
    name: 'YubiKey',
    icon: '🔑',
    description: 'Hardware security key with FIDO2/WebAuthn support',
    features: ['FIDO2', 'OTP', 'Smart Card', 'OpenPGP'],
  },
  {
    type: 'ledger',
    name: 'Ledger',
    icon: '💳',
    description: 'Hardware wallet with secure element',
    features: ['Secure Element', 'Crypto Signing', 'Multi-App'],
  },
  {
    type: 'trezor',
    name: 'Trezor',
    icon: '🔒',
    description: 'Open-source hardware wallet',
    features: ['Open Source', 'Crypto Signing', 'Password Manager'],
  },
  {
    type: 'titan',
    name: 'Titan Security Key',
    icon: '🛡️',
    description: 'Google\'s security key with built-in chip',
    features: ['FIDO2', 'NFC', 'USB-C/USB-A'],
  },
];

const HardwareKeySetup: React.FC<HardwareKeySetupProps> = ({
  onSetupComplete,
  onCancel,
  existingDevices = [],
}) => {
  const theme = useTheme();
  const {
    detectHardwareKeys,
    registerHardwareKey,
    testHardwareKey,
    configureKeySettings,
    performDeviceAttestation,
    isLoading,
    error,
  } = useSecurityService();

  const [activeStep, setActiveStep] = useState(0);
  const [detectedDevice, setDetectedDevice] = useState<any>(null);
  const [deviceChallenge, setDeviceChallenge] = useState<string>('');
  const [pinCode, setPinCode] = useState('');
  const [deviceName, setDeviceName] = useState('');
  const [securitySettings, setSecuritySettings] = useState({
    requirePin: true,
    requirePresence: true,
    enableBiometric: false,
    transactionLimit: 1000,
    dailyLimit: 5000,
  });
  const [attestationResult, setAttestationResult] = useState<any>(null);
  const [testResult, setTestResult] = useState<any>(null);
  const [showQRCode, setShowQRCode] = useState(false);
  const [isScanning, setIsScanning] = useState(false);

  useEffect(() => {
    if (activeStep === 0) {
      startDeviceDetection();
    }
  }, [activeStep]);

  const startDeviceDetection = async () => {
    setIsScanning(true);
    try {
      const devices = await detectHardwareKeys();
      if (devices.length > 0) {
        setDetectedDevice(devices[0]);
        generateChallenge();
      }
    } catch (error) {
      console.error('Device detection failed:', error);
    } finally {
      setIsScanning(false);
    }
  };

  const generateChallenge = () => {
    // Generate random challenge for device verification
    const challenge = Array.from(crypto.getRandomValues(new Uint8Array(32)))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');
    setDeviceChallenge(challenge);
  };

  const handleNext = () => {
    setActiveStep((prevStep) => prevStep + 1);
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleDeviceVerification = async () => {
    if (!detectedDevice) return;

    try {
      const attestation = await performDeviceAttestation(detectedDevice.id);
      setAttestationResult(attestation);
      
      if (attestation.isValid) {
        handleNext();
      }
    } catch (error) {
      console.error('Device verification failed:', error);
    }
  };

  const handleSecurityConfiguration = async () => {
    if (!detectedDevice) return;

    try {
      await configureKeySettings(detectedDevice.id, {
        name: deviceName || `${detectedDevice.type} Key`,
        ...securitySettings,
      });
      handleNext();
    } catch (error) {
      console.error('Security configuration failed:', error);
    }
  };

  const handleTestDevice = async () => {
    if (!detectedDevice) return;

    try {
      const result = await testHardwareKey(detectedDevice.id, {
        testType: 'signature',
        payload: 'test-transaction-data',
      });
      setTestResult(result);
    } catch (error) {
      console.error('Device test failed:', error);
    }
  };

  const handleComplete = async () => {
    if (!detectedDevice) return;

    try {
      const registeredDevice = await registerHardwareKey({
        deviceId: detectedDevice.id,
        deviceName: deviceName || `${detectedDevice.type} Key`,
        settings: securitySettings,
      });

      if (onSetupComplete) {
        onSetupComplete(registeredDevice);
      }
    } catch (error) {
      console.error('Device registration failed:', error);
    }
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Connect Your Hardware Key
            </Typography>

            <Alert severity="info" sx={{ mb: 3 }}>
              Insert your hardware security key into a USB port or tap it on your device's NFC reader
            </Alert>

            {isScanning && (
              <Box sx={{ textAlign: 'center', py: 4 }}>
                <CircularProgress size={60} />
                <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                  Scanning for hardware keys...
                </Typography>
              </Box>
            )}

            {detectedDevice ? (
              <Card sx={{ bgcolor: alpha(theme.palette.success.main, 0.05) }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    <Avatar sx={{ bgcolor: 'success.main', mr: 2 }}>
                      <UsbIcon />
                    </Avatar>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6">
                        {detectedDevice.name}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {detectedDevice.type} • Serial: {detectedDevice.serialNumber}
                      </Typography>
                    </Box>
                    <CheckIcon color="success" />
                  </Box>

                  <Box sx={{ mt: 2 }}>
                    <Chip size="small" label={`Firmware: ${detectedDevice.firmwareVersion}`} sx={{ mr: 1 }} />
                    <Chip size="small" label={detectedDevice.isSecureElement ? 'Secure Element' : 'Standard'} />
                  </Box>
                </CardContent>
              </Card>
            ) : (
              <Box>
                <Typography variant="subtitle1" gutterBottom sx={{ mt: 3 }}>
                  Supported Devices
                </Typography>
                <Grid container spacing={2}>
                  {supportedDevices.map((device) => (
                    <Grid item xs={12} sm={6} key={device.type}>
                      <Card sx={{ height: '100%' }}>
                        <CardContent>
                          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                            <Typography variant="h4" sx={{ mr: 2 }}>
                              {device.icon}
                            </Typography>
                            <Typography variant="h6">
                              {device.name}
                            </Typography>
                          </Box>
                          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                            {device.description}
                          </Typography>
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                            {device.features.map((feature) => (
                              <Chip key={feature} label={feature} size="small" variant="outlined" />
                            ))}
                          </Box>
                        </CardContent>
                      </Card>
                    </Grid>
                  ))}
                </Grid>
              </Box>
            )}

            {!isScanning && !detectedDevice && (
              <Box sx={{ mt: 3, textAlign: 'center' }}>
                <Button
                  variant="contained"
                  onClick={startDeviceDetection}
                  startIcon={<DeviceHubIcon />}
                >
                  Scan for Devices
                </Button>
              </Box>
            )}
          </Box>
        );

      case 1:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Verify Your Device
            </Typography>

            <Alert severity="warning" sx={{ mb: 3 }}>
              We need to verify your device is genuine and hasn't been tampered with
            </Alert>

            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  Device Challenge
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Touch your hardware key when it starts blinking to complete verification
                </Typography>

                <Box sx={{ 
                  p: 2, 
                  bgcolor: 'background.default', 
                  borderRadius: 1,
                  fontFamily: 'monospace',
                  fontSize: '0.875rem',
                  wordBreak: 'break-all',
                }}>
                  {deviceChallenge}
                </Box>

                {attestationResult && (
                  <Box sx={{ mt: 3 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                      {attestationResult.isValid ? (
                        <CheckIcon color="success" sx={{ mr: 1 }} />
                      ) : (
                        <WarningIcon color="error" sx={{ mr: 1 }} />
                      )}
                      <Typography variant="subtitle2">
                        {attestationResult.isValid ? 'Device Verified' : 'Verification Failed'}
                      </Typography>
                    </Box>

                    {attestationResult.certificateChain && (
                      <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                          <Typography variant="body2">
                            Certificate Chain ({attestationResult.certificateChain.length} certificates)
                          </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                          <List dense>
                            {attestationResult.certificateChain.map((cert: any, index: number) => (
                              <ListItem key={index}>
                                <ListItemIcon>
                                  <VerifiedIcon fontSize="small" />
                                </ListItemIcon>
                                <ListItemText
                                  primary={cert.subject}
                                  secondary={cert.issuer}
                                />
                              </ListItem>
                            ))}
                          </List>
                        </AccordionDetails>
                      </Accordion>
                    )}
                  </Box>
                )}

                {!attestationResult && (
                  <Box sx={{ mt: 3, textAlign: 'center' }}>
                    <Button
                      variant="contained"
                      onClick={handleDeviceVerification}
                      disabled={isLoading}
                      startIcon={isLoading ? <CircularProgress size={20} /> : <ShieldIcon />}
                    >
                      {isLoading ? 'Verifying...' : 'Verify Device'}
                    </Button>
                  </Box>
                )}
              </CardContent>
            </Card>

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <InfoIcon color="info" fontSize="small" />
              <Typography variant="caption" color="text.secondary">
                This verification ensures your device hasn't been tampered with and is genuine
              </Typography>
            </Box>
          </Box>
        );

      case 2:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Configure Security Settings
            </Typography>

            <Card sx={{ mb: 3 }}>
              <CardContent>
                <TextField
                  fullWidth
                  label="Device Name"
                  value={deviceName}
                  onChange={(e) => setDeviceName(e.target.value)}
                  placeholder={`My ${detectedDevice?.type || 'Security'} Key`}
                  sx={{ mb: 3 }}
                />

                <Typography variant="subtitle1" gutterBottom>
                  Security Options
                </Typography>

                <List>
                  <ListItem>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={securitySettings.requirePin}
                          onChange={(e) => setSecuritySettings({
                            ...securitySettings,
                            requirePin: e.target.checked,
                          })}
                        />
                      }
                      label="Require PIN for transactions"
                    />
                  </ListItem>

                  <ListItem>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={securitySettings.requirePresence}
                          onChange={(e) => setSecuritySettings({
                            ...securitySettings,
                            requirePresence: e.target.checked,
                          })}
                        />
                      }
                      label="Require physical touch confirmation"
                    />
                  </ListItem>

                  {detectedDevice?.supportsBiometric && (
                    <ListItem>
                      <FormControlLabel
                        control={
                          <Switch
                            checked={securitySettings.enableBiometric}
                            onChange={(e) => setSecuritySettings({
                              ...securitySettings,
                              enableBiometric: e.target.checked,
                            })}
                          />
                        }
                        label="Enable biometric authentication"
                      />
                    </ListItem>
                  )}
                </List>

                <Divider sx={{ my: 2 }} />

                <Typography variant="subtitle1" gutterBottom>
                  Transaction Limits
                </Typography>

                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      fullWidth
                      label="Per Transaction Limit"
                      type="number"
                      value={securitySettings.transactionLimit}
                      onChange={(e) => setSecuritySettings({
                        ...securitySettings,
                        transactionLimit: Number(e.target.value),
                      })}
                      InputProps={{
                        startAdornment: '$',
                      }}
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      fullWidth
                      label="Daily Limit"
                      type="number"
                      value={securitySettings.dailyLimit}
                      onChange={(e) => setSecuritySettings({
                        ...securitySettings,
                        dailyLimit: Number(e.target.value),
                      })}
                      InputProps={{
                        startAdornment: '$',
                      }}
                    />
                  </Grid>
                </Grid>

                {securitySettings.requirePin && (
                  <Box sx={{ mt: 3 }}>
                    <TextField
                      fullWidth
                      label="Set Device PIN"
                      type="password"
                      value={pinCode}
                      onChange={(e) => setPinCode(e.target.value)}
                      helperText="6-8 digit PIN for device authentication"
                      inputProps={{ maxLength: 8 }}
                    />
                  </Box>
                )}
              </CardContent>
            </Card>

            <Alert severity="info">
              These settings can be changed later in your security preferences
            </Alert>
          </Box>
        );

      case 3:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Test Your Device
            </Typography>

            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Typography variant="subtitle1" gutterBottom>
                  Device Summary
                </Typography>

                <List>
                  <ListItem>
                    <ListItemIcon>
                      <UsbIcon />
                    </ListItemIcon>
                    <ListItemText
                      primary="Device"
                      secondary={`${deviceName || detectedDevice?.name} (${detectedDevice?.type})`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <SecurityIcon />
                    </ListItemIcon>
                    <ListItemText
                      primary="Security Level"
                      secondary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <LinearProgress
                            variant="determinate"
                            value={90}
                            sx={{ width: 100, height: 6 }}
                          />
                          <Typography variant="caption">High</Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <SettingsIcon />
                    </ListItemIcon>
                    <ListItemText
                      primary="Configuration"
                      secondary={`PIN: ${securitySettings.requirePin ? 'Required' : 'Not required'} • Touch: ${securitySettings.requirePresence ? 'Required' : 'Not required'}`}
                    />
                  </ListItem>
                </List>

                <Divider sx={{ my: 2 }} />

                <Typography variant="subtitle1" gutterBottom>
                  Test Transaction Signing
                </Typography>

                {!testResult ? (
                  <Box sx={{ textAlign: 'center', py: 3 }}>
                    <Button
                      variant="contained"
                      onClick={handleTestDevice}
                      disabled={isLoading}
                      startIcon={isLoading ? <CircularProgress size={20} /> : <FingerprintIcon />}
                      size="large"
                    >
                      {isLoading ? 'Testing...' : 'Test Device'}
                    </Button>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                      Touch your device when it blinks to complete the test
                    </Typography>
                  </Box>
                ) : (
                  <Box>
                    <Alert 
                      severity={testResult.success ? 'success' : 'error'}
                      sx={{ mb: 2 }}
                    >
                      {testResult.success 
                        ? 'Device test completed successfully!' 
                        : 'Device test failed. Please try again.'}
                    </Alert>

                    {testResult.signature && (
                      <Box sx={{ 
                        p: 2, 
                        bgcolor: 'background.default', 
                        borderRadius: 1,
                        mb: 2,
                      }}>
                        <Typography variant="caption" color="text.secondary">
                          Test Signature:
                        </Typography>
                        <Typography variant="body2" sx={{ 
                          fontFamily: 'monospace',
                          wordBreak: 'break-all',
                        }}>
                          {testResult.signature.substring(0, 64)}...
                        </Typography>
                      </Box>
                    )}

                    <Button
                      fullWidth
                      variant="contained"
                      onClick={handleComplete}
                      startIcon={<CheckIcon />}
                      color="success"
                    >
                      Complete Setup
                    </Button>
                  </Box>
                )}
              </CardContent>
            </Card>

            <Alert severity="info">
              Your device is ready to use for secure transaction signing
            </Alert>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <Avatar sx={{ bgcolor: 'primary.main' }}>
          <UsbIcon />
        </Avatar>
        <Box>
          <Typography variant="h5">
            Hardware Key Setup
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Add a hardware security key for enhanced protection
          </Typography>
        </Box>
      </Box>

      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {setupSteps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      {renderStepContent(activeStep)}

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error.message}
        </Alert>
      )}

      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 3 }}>
        <Button onClick={onCancel} disabled={isLoading}>
          Cancel
        </Button>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {activeStep > 0 && (
            <Button onClick={handleBack} disabled={isLoading}>
              Back
            </Button>
          )}
          {activeStep === 0 && detectedDevice && (
            <Button onClick={handleNext} variant="contained">
              Continue
            </Button>
          )}
          {activeStep === 1 && attestationResult?.isValid && (
            <Button onClick={handleNext} variant="contained">
              Continue
            </Button>
          )}
          {activeStep === 2 && (
            <Button 
              onClick={handleSecurityConfiguration} 
              variant="contained"
              disabled={isLoading || (securitySettings.requirePin && !pinCode)}
            >
              Save Settings
            </Button>
          )}
        </Box>
      </Box>

      {/* Existing Devices */}
      {existingDevices.length > 0 && (
        <Box sx={{ mt: 4 }}>
          <Typography variant="h6" gutterBottom>
            Your Hardware Keys
          </Typography>
          <List>
            {existingDevices.map((device) => (
              <ListItem
                key={device.id}
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 2,
                  mb: 1,
                }}
              >
                <ListItemIcon>
                  <UsbIcon />
                </ListItemIcon>
                <ListItemText
                  primary={device.name}
                  secondary={`${device.type} • Last used: ${new Date(device.lastUsed).toLocaleDateString()}`}
                />
                <IconButton size="small">
                  <EditIcon />
                </IconButton>
                <IconButton size="small" color="error">
                  <DeleteIcon />
                </IconButton>
              </ListItem>
            ))}
          </List>
        </Box>
      )}

      {/* Mobile QR Code Dialog */}
      <Dialog open={showQRCode} onClose={() => setShowQRCode(false)}>
        <DialogTitle>Scan with Mobile Device</DialogTitle>
        <DialogContent>
          <Box sx={{ p: 2, textAlign: 'center' }}>
            <QRCode value={`waqiti://hardware-key-setup/${deviceChallenge}`} size={200} />
            <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
              Scan this code with your Waqiti mobile app to complete setup
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowQRCode(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default HardwareKeySetup;