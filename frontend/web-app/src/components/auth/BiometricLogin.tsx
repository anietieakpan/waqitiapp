import React, { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Typography,
  CircularProgress,
  Alert,
  Divider,
  IconButton,
  Tooltip,
  Fade,
} from '@mui/material';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import PasswordIcon from '@mui/icons-material/Password';
import SecurityIcon from '@mui/icons-material/Security';
import InfoIcon from '@mui/icons-material/Info';
import ErrorIcon from '@mui/icons-material/Error';;
import { toast } from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import biometricAuthService from '@/services/biometricAuthService';

/**
 * Biometric Login Component
 *
 * FEATURES:
 * - One-click biometric login
 * - Automatic biometric prompt
 * - Fallback to password
 * - Error handling
 * - Loading states
 * - Success navigation
 *
 * SECURITY:
 * - WebAuthn challenge-response
 * - User verification required
 * - No password transmission
 * - Device-bound credentials
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

interface BiometricLoginProps {
  onSuccess?: () => void;
  onFallback?: () => void;
  autoPrompt?: boolean;
}

export const BiometricLogin: React.FC<BiometricLoginProps> = ({
  onSuccess,
  onFallback,
  autoPrompt = false,
}) => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [available, setAvailable] = useState(false);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [authenticatorName, setAuthenticatorName] = useState('Biometric');

  useEffect(() => {
    checkAvailability();
  }, []);

  useEffect(() => {
    if (available && autoPrompt && !loading) {
      // Auto-prompt after a brief delay
      const timer = setTimeout(() => {
        handleBiometricLogin();
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [available, autoPrompt]);

  const checkAvailability = async () => {
    setChecking(true);
    try {
      const capability = await biometricAuthService.checkCapability();
      setAvailable(capability.available);

      if (capability.available) {
        setAuthenticatorName(detectAuthenticatorName());
      }
    } catch (err) {
      console.error('Failed to check biometric availability:', err);
      setAvailable(false);
    } finally {
      setChecking(false);
    }
  };

  const detectAuthenticatorName = (): string => {
    const platform = navigator.platform;
    const userAgent = navigator.userAgent;

    if (platform.includes('Mac')) return 'Touch ID';
    if (platform.includes('Win')) return 'Windows Hello';
    if (userAgent.includes('iPhone') || userAgent.includes('iPad')) return 'Face ID';
    if (userAgent.includes('Android')) return 'Fingerprint';

    return 'Biometric';
  };

  const handleBiometricLogin = async () => {
    setLoading(true);
    setError(null);

    try {
      const result = await biometricAuthService.authenticate(undefined, true);

      if (result.authenticated) {
        toast.success('Login successful!');

        // Navigate to dashboard or call onSuccess callback
        if (onSuccess) {
          onSuccess();
        } else {
          navigate('/dashboard');
        }
      } else {
        throw new Error('Authentication failed');
      }
    } catch (err: any) {
      console.error('Biometric login failed:', err);

      // Handle specific errors
      if (err.name === 'NotAllowedError') {
        setError('Authentication was cancelled');
      } else if (err.name === 'SecurityError') {
        setError('Authentication failed due to security error');
      } else if (err.name === 'AbortError') {
        setError('Authentication timed out');
      } else if (err.message?.includes('not available')) {
        setError('Biometric authentication is not available');
        setAvailable(false);
      } else {
        setError(err.message || 'Authentication failed');
      }

      toast.error(err.message || 'Biometric login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleFallback = () => {
    if (onFallback) {
      onFallback();
    }
  };

  if (checking) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <CircularProgress />
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          Checking biometric availability...
        </Typography>
      </Box>
    );
  }

  if (!available) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Alert severity="info" icon={<Info />}>
            Biometric authentication is not available on this device.
            <br />
            Please use password login or try on a different device.
          </Alert>
          {onFallback && (
            <Box sx={{ mt: 2, textAlign: 'center' }}>
              <Button
                variant="contained"
                startIcon={<Password />}
                onClick={handleFallback}
              >
                Use Password Login
              </Button>
            </Box>
          )}
        </CardContent>
      </Card>
    );
  }

  return (
    <Fade in timeout={500}>
      <Card elevation={3}>
        <CardContent>
          {/* Header */}
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box
              sx={{
                display: 'inline-flex',
                p: 2,
                borderRadius: '50%',
                bgcolor: 'primary.light',
                mb: 2,
              }}
            >
              <Fingerprint sx={{ fontSize: 48, color: 'primary.main' }} />
            </Box>
            <Typography variant="h5" gutterBottom>
              Sign in with {authenticatorName}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Use your device's biometric authentication for secure access
            </Typography>
          </Box>

          {/* Error Alert */}
          {error && (
            <Alert
              severity="error"
              icon={<ErrorIcon />}
              onClose={() => setError(null)}
              sx={{ mb: 2 }}
            >
              {error}
            </Alert>
          )}

          {/* Biometric Login Button */}
          <Box sx={{ textAlign: 'center', mb: 2 }}>
            <Button
              variant="contained"
              size="large"
              fullWidth
              onClick={handleBiometricLogin}
              disabled={loading}
              startIcon={loading ? <CircularProgress size={20} /> : <Fingerprint />}
              sx={{
                py: 1.5,
                fontSize: '1.1rem',
                background: loading
                  ? undefined
                  : 'linear-gradient(45deg, #2196F3 30%, #21CBF3 90%)',
                boxShadow: loading ? undefined : '0 3px 5px 2px rgba(33, 203, 243, .3)',
              }}
            >
              {loading ? 'Authenticating...' : `Sign in with ${authenticatorName}`}
            </Button>
          </Box>

          {/* Info */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              p: 2,
              bgcolor: 'grey.50',
              borderRadius: 1,
              mb: 2,
            }}
          >
            <Security color="primary" fontSize="small" />
            <Typography variant="caption" color="text.secondary">
              Your biometric data never leaves your device. We use public key cryptography
              for secure authentication.
            </Typography>
          </Box>

          {/* Fallback Option */}
          {onFallback && (
            <>
              <Divider sx={{ my: 2 }}>
                <Typography variant="caption" color="text.secondary">
                  OR
                </Typography>
              </Divider>

              <Box sx={{ textAlign: 'center' }}>
                <Button
                  variant="outlined"
                  startIcon={<Password />}
                  onClick={handleFallback}
                  disabled={loading}
                  fullWidth
                >
                  Sign in with Password
                </Button>
              </Box>
            </>
          )}

          {/* Help Text */}
          <Box sx={{ mt: 3, textAlign: 'center' }}>
            <Typography variant="caption" color="text.secondary">
              Having trouble?{' '}
              <Tooltip title="Make sure your device supports biometric authentication and you have it enabled in your device settings.">
                <IconButton size="small">
                  <Info fontSize="small" />
                </IconButton>
              </Tooltip>
            </Typography>
          </Box>
        </CardContent>
      </Card>
    </Fade>
  );
};

export default BiometricLogin;
