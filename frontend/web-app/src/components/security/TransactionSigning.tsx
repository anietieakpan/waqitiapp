import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Button,
  Stepper,
  Step,
  StepLabel,
  CircularProgress,
  Alert,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  IconButton,
  Chip,
  TextField,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Divider,
  LinearProgress,
  Collapse,
  Avatar,
  useTheme,
  alpha,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import UsbIcon from '@mui/icons-material/UsbIcon';
import PhoneIcon from '@mui/icons-material/PhoneAndroid';
import GroupIcon from '@mui/icons-material/Group';
import CheckIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import LockIcon from '@mui/icons-material/Lock';
import KeyIcon from '@mui/icons-material/Key';
import VerifiedIcon from '@mui/icons-material/VerifiedUser';
import TouchIcon from '@mui/icons-material/TouchApp';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import TimerIcon from '@mui/icons-material/Timer';
import LocationIcon from '@mui/icons-material/LocationOn';;
import { useSecurityService } from '../../hooks/useSecurityService';
import { SigningMethod, BiometricType } from '../../types/security';
import { formatCurrency } from '../../utils/formatters';

interface TransactionSigningProps {
  open: boolean;
  onClose: () => void;
  transaction: {
    id: string;
    amount: number;
    currency: string;
    type: string;
    recipient?: {
      id: string;
      name: string;
      avatar?: string;
    };
    description?: string;
  };
  onSigningComplete: (signature: string) => void;
  onSigningError?: (error: Error) => void;
  requiredSigningMethod?: SigningMethod;
  requireMultiSignature?: boolean;
}

const signingSteps = ['Verify Details', 'Choose Method', 'Sign Transaction', 'Complete'];

const TransactionSigning: React.FC<TransactionSigningProps> = ({
  open,
  onClose,
  transaction,
  onSigningComplete,
  onSigningError,
  requiredSigningMethod,
  requireMultiSignature,
}) => {
  const theme = useTheme();
  const { 
    availableMethods,
    signTransaction,
    detectHardwareKeys,
    initiateBiometric,
    verifyPin,
    isLoading,
    error 
  } = useSecurityService();

  const [activeStep, setActiveStep] = useState(0);
  const [selectedMethod, setSelectedMethod] = useState<SigningMethod | null>(
    requiredSigningMethod || null
  );
  const [pin, setPin] = useState('');
  const [showPin, setShowPin] = useState(false);
  const [signingInProgress, setSigningInProgress] = useState(false);
  const [signature, setSignature] = useState<string | null>(null);
  const [verificationDetails, setVerificationDetails] = useState<any>(null);
  const [connectedDevices, setConnectedDevices] = useState<any[]>([]);
  const [biometricReady, setBiometricReady] = useState(false);
  const [multiSigProgress, setMultiSigProgress] = useState({
    required: 3,
    collected: 0,
    signers: [] as string[],
  });

  useEffect(() => {
    if (open && activeStep === 1) {
      // Detect available hardware keys
      detectHardwareKeys().then(devices => {
        setConnectedDevices(devices);
      });

      // Check biometric availability
      checkBiometricAvailability();
    }
  }, [open, activeStep]);

  const checkBiometricAvailability = async () => {
    try {
      const available = await initiateBiometric();
      setBiometricReady(available);
    } catch (error) {
      console.error('Biometric check failed:', error);
    }
  };

  const handleNext = () => {
    setActiveStep((prevStep) => prevStep + 1);
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleMethodSelect = (method: SigningMethod) => {
    setSelectedMethod(method);
    handleNext();
  };

  const handleSign = async () => {
    if (!selectedMethod) return;

    setSigningInProgress(true);

    try {
      const signatureResult = await signTransaction({
        transactionId: transaction.id,
        method: selectedMethod,
        pin: pin || undefined,
        transactionData: {
          amount: transaction.amount,
          currency: transaction.currency,
          type: transaction.type,
          recipientId: transaction.recipient?.id,
          recipientName: transaction.recipient?.name,
          description: transaction.description,
        },
      });

      setSignature(signatureResult.signature);
      setVerificationDetails(signatureResult.verificationDetails);
      handleNext();
      
      // Notify parent component
      onSigningComplete(signatureResult.signature);
    } catch (error: any) {
      setSigningInProgress(false);
      if (onSigningError) {
        onSigningError(error);
      }
    }
  };

  const getMethodIcon = (method: SigningMethod) => {
    switch (method) {
      case SigningMethod.SOFTWARE_KEY:
        return <KeyIcon />;
      case SigningMethod.HARDWARE_KEY:
        return <UsbIcon />;
      case SigningMethod.BIOMETRIC:
        return <FingerprintIcon />;
      case SigningMethod.MULTI_SIGNATURE:
        return <GroupIcon />;
      case SigningMethod.PUSH_NOTIFICATION:
        return <PhoneIcon />;
      default:
        return <SecurityIcon />;
    }
  };

  const getMethodSecurityLevel = (method: SigningMethod): number => {
    switch (method) {
      case SigningMethod.HARDWARE_KEY:
      case SigningMethod.MULTI_SIGNATURE:
        return 5;
      case SigningMethod.BIOMETRIC:
        return 4;
      case SigningMethod.SOFTWARE_KEY:
        return 3;
      case SigningMethod.PUSH_NOTIFICATION:
        return 2;
      default:
        return 1;
    }
  };

  const renderStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Verify Transaction Details
            </Typography>
            
            <Card sx={{ mb: 3, bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Amount
                  </Typography>
                  <Typography variant="h5" color="primary">
                    {formatCurrency(transaction.amount, transaction.currency)}
                  </Typography>
                </Box>

                {transaction.recipient && (
                  <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    <Avatar 
                      src={transaction.recipient.avatar} 
                      sx={{ mr: 2 }}
                    >
                      {transaction.recipient.name.charAt(0)}
                    </Avatar>
                    <Box>
                      <Typography variant="body1">
                        {transaction.recipient.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Recipient
                      </Typography>
                    </Box>
                  </Box>
                )}

                {transaction.description && (
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                      Description
                    </Typography>
                    <Typography variant="body2">
                      {transaction.description}
                    </Typography>
                  </Box>
                )}

                <Divider sx={{ my: 2 }} />

                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <InfoIcon color="info" sx={{ mr: 1 }} />
                  <Typography variant="caption" color="text.secondary">
                    Transaction ID: {transaction.id}
                  </Typography>
                </Box>
              </CardContent>
            </Card>

            <Alert severity="info" icon={<SecurityIcon />}>
              You are about to sign this transaction. This action cannot be undone.
            </Alert>
          </Box>
        );

      case 1:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Choose Signing Method
            </Typography>

            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Select how you want to sign this transaction
            </Typography>

            <List>
              {availableMethods.map((method) => (
                <ListItem
                  key={method}
                  button
                  onClick={() => handleMethodSelect(method)}
                  disabled={requiredSigningMethod && requiredSigningMethod !== method}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 2,
                    mb: 1,
                    '&:hover': {
                      bgcolor: alpha(theme.palette.primary.main, 0.05),
                      borderColor: 'primary.main',
                    },
                  }}
                >
                  <ListItemIcon>
                    {getMethodIcon(method)}
                  </ListItemIcon>
                  <ListItemText
                    primary={method}
                    secondary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
                        <LinearProgress
                          variant="determinate"
                          value={getMethodSecurityLevel(method) * 20}
                          sx={{ width: 60, height: 4 }}
                        />
                        <Typography variant="caption">
                          Security Level
                        </Typography>
                      </Box>
                    }
                  />
                  {method === SigningMethod.HARDWARE_KEY && connectedDevices.length > 0 && (
                    <Chip
                      size="small"
                      label={`${connectedDevices.length} device(s)`}
                      color="success"
                    />
                  )}
                  {method === SigningMethod.BIOMETRIC && biometricReady && (
                    <Chip
                      size="small"
                      label="Ready"
                      color="success"
                    />
                  )}
                </ListItem>
              ))}
            </List>

            {requireMultiSignature && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                This transaction requires multiple signatures for approval
              </Alert>
            )}
          </Box>
        );

      case 2:
        return (
          <Box>
            <Typography variant="h6" gutterBottom>
              Sign Transaction
            </Typography>

            {selectedMethod === SigningMethod.SOFTWARE_KEY && (
              <Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Enter your PIN to sign this transaction
                </Typography>

                <TextField
                  fullWidth
                  label="PIN"
                  type={showPin ? 'text' : 'password'}
                  value={pin}
                  onChange={(e) => setPin(e.target.value)}
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={() => setShowPin(!showPin)}
                          edge="end"
                        >
                          {showPin ? <VisibilityOffIcon /> : <VisibilityIcon />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                  sx={{ mb: 3 }}
                />

                <Button
                  fullWidth
                  variant="contained"
                  onClick={handleSign}
                  disabled={!pin || signingInProgress}
                  startIcon={signingInProgress ? <CircularProgress size={20} /> : <LockIcon />}
                >
                  {signingInProgress ? 'Signing...' : 'Sign Transaction'}
                </Button>
              </Box>
            )}

            {selectedMethod === SigningMethod.HARDWARE_KEY && (
              <Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Connect your hardware key and follow the device instructions
                </Typography>

                <Card sx={{ mb: 3, bgcolor: alpha(theme.palette.info.main, 0.05) }}>
                  <CardContent>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                      <UsbIcon sx={{ mr: 2, fontSize: 40 }} />
                      <Box>
                        <Typography variant="subtitle1">
                          YubiKey 5C NFC
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Connected and ready
                        </Typography>
                      </Box>
                    </Box>

                    <Alert severity="info" sx={{ mt: 2 }}>
                      Touch your hardware key when it starts blinking
                    </Alert>
                  </CardContent>
                </Card>

                {signingInProgress && (
                  <Box sx={{ textAlign: 'center', py: 3 }}>
                    <CircularProgress size={60} />
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                      Waiting for hardware key...
                    </Typography>
                  </Box>
                )}
              </Box>
            )}

            {selectedMethod === SigningMethod.BIOMETRIC && (
              <Box sx={{ textAlign: 'center', py: 3 }}>
                <FingerprintIcon sx={{ fontSize: 80, color: 'primary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Biometric Authentication Required
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Place your finger on the sensor or look at the camera
                </Typography>

                {signingInProgress ? (
                  <CircularProgress size={60} />
                ) : (
                  <Button
                    variant="contained"
                    onClick={handleSign}
                    startIcon={<TouchIcon />}
                    size="large"
                  >
                    Authenticate
                  </Button>
                )}
              </Box>
            )}

            {selectedMethod === SigningMethod.MULTI_SIGNATURE && (
              <Box>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Multiple signatures required for this transaction
                </Typography>

                <Card sx={{ mb: 3 }}>
                  <CardContent>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                      <Typography variant="subtitle2">
                        Signatures Required
                      </Typography>
                      <Chip
                        label={`${multiSigProgress.collected} / ${multiSigProgress.required}`}
                        color={multiSigProgress.collected >= multiSigProgress.required ? 'success' : 'default'}
                      />
                    </Box>

                    <LinearProgress
                      variant="determinate"
                      value={(multiSigProgress.collected / multiSigProgress.required) * 100}
                      sx={{ mb: 2 }}
                    />

                    <List dense>
                      {['You', 'John Doe (CFO)', 'Jane Smith (CEO)'].map((signer, index) => (
                        <ListItem key={index}>
                          <ListItemIcon>
                            {index === 0 || index < multiSigProgress.collected ? (
                              <CheckIcon color="success" />
                            ) : (
                              <TimerIcon color="action" />
                            )}
                          </ListItemIcon>
                          <ListItemText
                            primary={signer}
                            secondary={index === 0 ? 'Signed' : index < multiSigProgress.collected ? 'Signed' : 'Pending'}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </CardContent>
                </Card>

                <Button
                  fullWidth
                  variant="contained"
                  onClick={handleSign}
                  disabled={signingInProgress}
                  startIcon={signingInProgress ? <CircularProgress size={20} /> : <GroupIcon />}
                >
                  {signingInProgress ? 'Adding Signature...' : 'Add Your Signature'}
                </Button>
              </Box>
            )}
          </Box>
        );

      case 3:
        return (
          <Box sx={{ textAlign: 'center', py: 3 }}>
            <CheckIcon sx={{ fontSize: 80, color: 'success.main', mb: 2 }} />
            <Typography variant="h5" gutterBottom>
              Transaction Signed Successfully
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Your transaction has been securely signed and is being processed
            </Typography>

            <Card sx={{ bgcolor: alpha(theme.palette.success.main, 0.05), mb: 3 }}>
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  Signature Hash
                </Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                  {signature?.substring(0, 32)}...
                </Typography>
              </CardContent>
            </Card>

            {verificationDetails && (
              <Box sx={{ textAlign: 'left' }}>
                <Typography variant="subtitle2" gutterBottom>
                  Verification Details
                </Typography>
                <List dense>
                  <ListItem>
                    <ListItemIcon>
                      <VerifiedIcon color="success" />
                    </ListItemIcon>
                    <ListItemText
                      primary="Signing Method"
                      secondary={selectedMethod}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemIcon>
                      <TimerIcon />
                    </ListItemIcon>
                    <ListItemText
                      primary="Signed At"
                      secondary={new Date().toLocaleString()}
                    />
                  </ListItem>
                  {verificationDetails.deviceInfo && (
                    <ListItem>
                      <ListItemIcon>
                        <UsbIcon />
                      </ListItemIcon>
                      <ListItemText
                        primary="Device"
                        secondary={verificationDetails.deviceInfo}
                      />
                    </ListItem>
                  )}
                </List>
              </Box>
            )}
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: {
          borderRadius: 2,
        },
      }}
    >
      <DialogTitle>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <SecurityIcon />
          <Typography variant="h6">
            Secure Transaction Signing
          </Typography>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
          {signingSteps.map((label) => (
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
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={signingInProgress}>
          Cancel
        </Button>
        {activeStep > 0 && activeStep < 3 && (
          <Button onClick={handleBack} disabled={signingInProgress}>
            Back
          </Button>
        )}
        {activeStep === 0 && (
          <Button onClick={handleNext} variant="contained">
            Continue
          </Button>
        )}
        {activeStep === 3 && (
          <Button onClick={onClose} variant="contained">
            Done
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default TransactionSigning;