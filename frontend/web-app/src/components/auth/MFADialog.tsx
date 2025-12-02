import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Typography,
  Box,
  Alert,
  CircularProgress,
  Tabs,
  Tab,
  IconButton,
  InputAdornment,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import EmailIcon from '@mui/icons-material/Email';
import KeyIcon from '@mui/icons-material/Key';;
import { useAuth } from '@/contexts/AuthContext';
import QRCode from 'qrcode';
import toast from 'react-hot-toast';

interface MFADialogProps {
  open: boolean;
  onClose: () => void;
  mode: 'setup' | 'verify';
  onSuccess?: () => void;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <div role="tabpanel" hidden={value !== index}>
    {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
  </div>
);

const MFADialog: React.FC<MFADialogProps> = ({ 
  open, 
  onClose, 
  mode = 'verify',
  onSuccess 
}) => {
  const { setupMfa, verifyMfa, user } = useAuth();
  const [activeTab, setActiveTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // Setup state
  const [qrCodeUrl, setQrCodeUrl] = useState<string>('');
  const [secret, setSecret] = useState<string>('');
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const [setupStep, setSetupStep] = useState<'method' | 'configure' | 'verify' | 'backup'>('method');
  
  // Verification state
  const [verificationCode, setVerificationCode] = useState('');
  const [copiedSecret, setCopiedSecret] = useState(false);
  const [copiedBackupCode, setCopiedBackupCode] = useState<number | null>(null);

  const mfaMethods = [
    { id: 'totp', label: 'Authenticator App', icon: <Smartphone />, description: 'Use Google Authenticator or similar' },
    { id: 'sms', label: 'SMS', icon: <Smartphone />, description: 'Receive codes via text message' },
    { id: 'email', label: 'Email', icon: <Email />, description: 'Receive codes via email' },
  ];

  useEffect(() => {
    if (!open) {
      // Reset state when dialog closes
      setActiveTab(0);
      setError(null);
      setVerificationCode('');
      setSetupStep('method');
      setQrCodeUrl('');
      setSecret('');
      setBackupCodes([]);
    }
  }, [open]);

  const handleSetupMfa = async (method: 'totp' | 'sms' | 'email') => {
    setLoading(true);
    setError(null);

    try {
      const response = await setupMfa({ method });
      
      if (method === 'totp' && response.qrCode) {
        // Generate QR code for TOTP
        const qrDataUrl = await QRCode.toDataURL(response.qrCode);
        setQrCodeUrl(qrDataUrl);
        setSecret(response.secret || '');
      }
      
      if (response.backupCodes) {
        setBackupCodes(response.backupCodes);
      }
      
      setSetupStep('configure');
    } catch (err: any) {
      setError(err.message || 'Failed to setup MFA');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyMfa = async () => {
    if (!verificationCode || verificationCode.length !== 6) {
      setError('Please enter a 6-digit code');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await verifyMfa(verificationCode);
      
      if (mode === 'setup') {
        setSetupStep('backup');
      } else {
        toast.success('MFA verification successful!');
        onSuccess?.();
        onClose();
      }
    } catch (err: any) {
      setError(err.message || 'Invalid verification code');
    } finally {
      setLoading(false);
    }
  };

  const handleCopySecret = () => {
    navigator.clipboard.writeText(secret);
    setCopiedSecret(true);
    setTimeout(() => setCopiedSecret(false), 2000);
    toast.success('Secret key copied to clipboard');
  };

  const handleCopyBackupCode = (code: string, index: number) => {
    navigator.clipboard.writeText(code);
    setCopiedBackupCode(index);
    setTimeout(() => setCopiedBackupCode(null), 2000);
    toast.success('Backup code copied to clipboard');
  };

  const handleDownloadBackupCodes = () => {
    const content = `Waqiti 2FA Backup Codes\n\nIMPORTANT: Keep these codes safe. Each code can only be used once.\n\n${backupCodes.join('\n')}\n\nGenerated: ${new Date().toISOString()}`;
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'waqiti-2fa-backup-codes.txt';
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Backup codes downloaded');
  };

  const renderSetupContent = () => {
    switch (setupStep) {
      case 'method':
        return (
          <Box>
            <Typography variant="body1" paragraph>
              Choose your preferred two-factor authentication method:
            </Typography>
            {mfaMethods.map((method, index) => (
              <Button
                key={method.id}
                fullWidth
                variant="outlined"
                size="large"
                startIcon={method.icon}
                onClick={() => handleSetupMfa(method.id as any)}
                disabled={loading}
                sx={{ mb: 2, justifyContent: 'flex-start', textAlign: 'left' }}
              >
                <Box>
                  <Typography variant="subtitle2">{method.label}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {method.description}
                  </Typography>
                </Box>
              </Button>
            ))}
          </Box>
        );

      case 'configure':
        return (
          <Box>
            <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
              <Tab label="QR Code" />
              <Tab label="Manual Entry" />
            </Tabs>

            <TabPanel value={activeTab} index={0}>
              <Box textAlign="center">
                <Typography variant="body2" paragraph>
                  Scan this QR code with your authenticator app:
                </Typography>
                {qrCodeUrl && (
                  <img 
                    src={qrCodeUrl} 
                    alt="2FA QR Code" 
                    style={{ maxWidth: '200px', margin: '0 auto' }}
                  />
                )}
                <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
                  Can't scan? Use manual entry instead.
                </Typography>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={1}>
              <Typography variant="body2" paragraph>
                Enter this key in your authenticator app:
              </Typography>
              <Box
                sx={{
                  p: 2,
                  bgcolor: 'grey.100',
                  borderRadius: 1,
                  fontFamily: 'monospace',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
                  {secret}
                </Typography>
                <IconButton onClick={handleCopySecret} size="small">
                  {copiedSecret ? <Check color="success" /> : <ContentCopy />}
                </IconButton>
              </Box>
              <Alert severity="info" sx={{ mt: 2 }}>
                <Typography variant="caption">
                  Account: {user?.email}
                  <br />
                  Issuer: Waqiti
                </Typography>
              </Alert>
            </TabPanel>

            <Box mt={3}>
              <Typography variant="body2" paragraph>
                Enter the 6-digit code from your authenticator app:
              </Typography>
              <TextField
                fullWidth
                label="Verification Code"
                value={verificationCode}
                onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                error={!!error}
                helperText={error}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Key />
                    </InputAdornment>
                  ),
                }}
                inputProps={{
                  maxLength: 6,
                  style: { textAlign: 'center', letterSpacing: '0.5em', fontSize: '1.5rem' }
                }}
              />
            </Box>
          </Box>
        );

      case 'backup':
        return (
          <Box>
            <Alert severity="success" sx={{ mb: 2 }}>
              Two-factor authentication has been successfully enabled!
            </Alert>
            
            <Typography variant="h6" gutterBottom>
              Save Your Backup Codes
            </Typography>
            <Typography variant="body2" paragraph color="text.secondary">
              Store these backup codes in a safe place. You can use them to access your account if you lose your authenticator device.
            </Typography>
            
            <Box sx={{ bgcolor: 'grey.50', p: 2, borderRadius: 1 }}>
              {backupCodes.map((code, index) => (
                <Box
                  key={index}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    mb: 1,
                    p: 1,
                    bgcolor: 'white',
                    borderRadius: 1,
                    fontFamily: 'monospace',
                  }}
                >
                  <Typography variant="body2">{code}</Typography>
                  <IconButton 
                    size="small" 
                    onClick={() => handleCopyBackupCode(code, index)}
                  >
                    {copiedBackupCode === index ? <Check color="success" fontSize="small" /> : <ContentCopy fontSize="small" />}
                  </IconButton>
                </Box>
              ))}
            </Box>
            
            <Button
              fullWidth
              variant="outlined"
              startIcon={<ContentCopy />}
              onClick={handleDownloadBackupCodes}
              sx={{ mt: 2 }}
            >
              Download Backup Codes
            </Button>
            
            <Alert severity="warning" sx={{ mt: 2 }}>
              Each backup code can only be used once. When you use a backup code, it will be invalidated.
            </Alert>
          </Box>
        );
    }
  };

  const renderVerifyContent = () => (
    <Box>
      <Typography variant="body1" paragraph>
        Enter the 6-digit code from your authenticator app:
      </Typography>
      <TextField
        fullWidth
        label="Verification Code"
        value={verificationCode}
        onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        placeholder="000000"
        error={!!error}
        helperText={error}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Key />
            </InputAdornment>
          ),
        }}
        inputProps={{
          maxLength: 6,
          style: { textAlign: 'center', letterSpacing: '0.5em', fontSize: '1.5rem' }
        }}
        autoFocus
      />
      
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
        Lost your authenticator device? Use a backup code or contact support.
      </Typography>
    </Box>
  );

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      disableEscapeKeyDown={mode === 'verify'}
    >
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center">
            <Security sx={{ mr: 1 }} />
            <Typography variant="h6">
              {mode === 'setup' ? 'Setup Two-Factor Authentication' : 'Two-Factor Authentication'}
            </Typography>
          </Box>
          {mode === 'setup' && (
            <IconButton onClick={onClose} size="small">
              <Close />
            </IconButton>
          )}
        </Box>
      </DialogTitle>
      
      <DialogContent>
        {mode === 'setup' ? renderSetupContent() : renderVerifyContent()}
      </DialogContent>
      
      <DialogActions>
        {mode === 'setup' && setupStep !== 'backup' && (
          <Button onClick={onClose} disabled={loading}>
            Cancel
          </Button>
        )}
        
        {((mode === 'setup' && setupStep === 'configure') || mode === 'verify') && (
          <Button
            variant="contained"
            onClick={handleVerifyMfa}
            disabled={loading || verificationCode.length !== 6}
            startIcon={loading ? <CircularProgress size={20} /> : null}
          >
            Verify
          </Button>
        )}
        
        {mode === 'setup' && setupStep === 'backup' && (
          <Button
            variant="contained"
            onClick={() => {
              onSuccess?.();
              onClose();
            }}
          >
            Done
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default MFADialog;