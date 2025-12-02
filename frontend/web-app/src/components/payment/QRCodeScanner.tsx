import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  Typography,
  Button,
  Alert,
  Card,
  CardContent,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  Chip,
  IconButton,
  Switch,
  FormControlLabel,
  Divider,
} from '@mui/material';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import FlashOffIcon from '@mui/icons-material/FlashOff';
import CameraFrontIcon from '@mui/icons-material/CameraFront';
import CameraRearIcon from '@mui/icons-material/CameraRear';
import CloseIcon from '@mui/icons-material/Close';
import PersonIcon from '@mui/icons-material/Person';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import CheckIcon from '@mui/icons-material/Check';
import ErrorIcon from '@mui/icons-material/Error';;
import { QRCodeData } from '@/types/payment';
import { Contact } from '@/types/contact';
import toast from 'react-hot-toast';

const QRCodeScanner: React.FC = () => {
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  
  const [isScanning, setIsScanning] = useState(false);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [flashEnabled, setFlashEnabled] = useState(false);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('environment');
  const [scannedData, setScannedData] = useState<QRCodeData | null>(null);
  const [showResult, setShowResult] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [permissionDenied, setPermissionDenied] = useState(false);

  useEffect(() => {
    return () => {
      if (stream) {
        stream.getTracks().forEach(track => track.stop());
      }
    };
  }, [stream]);

  const startScanning = async () => {
    try {
      setError(null);
      setPermissionDenied(false);
      
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: facingMode,
          width: { ideal: 1280 },
          height: { ideal: 720 },
        }
      });

      setStream(mediaStream);
      
      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
        videoRef.current.play();
      }
      
      setIsScanning(true);
      
      // Start QR code scanning interval
      const scanInterval = setInterval(() => {
        captureAndScan();
      }, 1000);

      // Store interval for cleanup
      (mediaStream as any).scanInterval = scanInterval;
      
    } catch (err: any) {
      console.error('Error starting camera:', err);
      if (err.name === 'NotAllowedError') {
        setPermissionDenied(true);
        setError('Camera permission denied. Please enable camera access to scan QR codes.');
      } else if (err.name === 'NotFoundError') {
        setError('No camera found on this device.');
      } else {
        setError('Failed to start camera. Please check your device settings.');
      }
    }
  };

  const stopScanning = () => {
    if (stream) {
      // Clear scanning interval
      if ((stream as any).scanInterval) {
        clearInterval((stream as any).scanInterval);
      }
      
      stream.getTracks().forEach(track => track.stop());
      setStream(null);
    }
    setIsScanning(false);
  };

  const captureAndScan = () => {
    if (!videoRef.current || !canvasRef.current) return;

    const video = videoRef.current;
    const canvas = canvasRef.current;
    const context = canvas.getContext('2d');

    if (!context) return;

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    context.drawImage(video, 0, 0);

    // In a real implementation, you would use a QR code library here
    // For demo purposes, we'll simulate QR code detection
    simulateQRCodeDetection();
  };

  const simulateQRCodeDetection = () => {
    // Simulate finding a QR code after some scanning time
    const random = Math.random();
    if (random > 0.95) { // 5% chance per scan
      const mockQRData: QRCodeData = {
        type: 'payment_request',
        userId: 'user123',
        amount: 25.50,
        currency: 'USD',
        note: 'Coffee payment',
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      };
      handleQRCodeDetected(mockQRData);
    }
  };

  const handleQRCodeDetected = (data: QRCodeData) => {
    setScannedData(data);
    setShowResult(true);
    stopScanning();
    toast.success('QR code detected!');
  };

  const toggleFlash = async () => {
    if (stream) {
      const track = stream.getVideoTracks()[0];
      const capabilities = track.getCapabilities();
      
      if (capabilities.torch) {
        try {
          await track.applyConstraints({
            advanced: [{ torch: !flashEnabled } as any]
          });
          setFlashEnabled(!flashEnabled);
        } catch (err) {
          toast.error('Flash not supported on this device');
        }
      } else {
        toast.error('Flash not supported on this device');
      }
    }
  };

  const switchCamera = () => {
    const newFacingMode = facingMode === 'user' ? 'environment' : 'user';
    setFacingMode(newFacingMode);
    
    if (isScanning) {
      stopScanning();
      // Restart with new facing mode
      setTimeout(() => {
        startScanning();
      }, 100);
    }
  };

  const handleProcessPayment = () => {
    if (scannedData?.type === 'payment_request') {
      navigate('/payment/send', { 
        state: { 
          prefilledData: {
            recipientId: scannedData.userId,
            amount: scannedData.amount,
            currency: scannedData.currency,
            note: scannedData.note,
          }
        }
      });
    } else if (scannedData?.type === 'user_profile') {
      navigate('/payment/send', { 
        state: { 
          prefilledData: {
            recipientId: scannedData.userId,
          }
        }
      });
    }
  };

  const renderScannedResult = () => {
    if (!scannedData) return null;

    if (scannedData.type === 'payment_request') {
      return (
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" mb={2}>
              <AttachMoney color="primary" sx={{ mr: 1 }} />
              <Typography variant="h6">
                Payment Request
              </Typography>
            </Box>
            
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Amount:
                </Typography>
                <Typography variant="h6">
                  ${scannedData.amount?.toFixed(2)} {scannedData.currency}
                </Typography>
              </Grid>
              
              <Grid item xs={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Expires:
                </Typography>
                <Typography variant="body2">
                  {scannedData.expiresAt 
                    ? new Date(scannedData.expiresAt).toLocaleDateString()
                    : 'No expiration'
                  }
                </Typography>
              </Grid>
              
              {scannedData.note && (
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Note:
                  </Typography>
                  <Typography variant="body2">
                    {scannedData.note}
                  </Typography>
                </Grid>
              )}
            </Grid>
          </CardContent>
        </Card>
      );
    } else if (scannedData.type === 'user_profile') {
      return (
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" mb={2}>
              <Person color="primary" sx={{ mr: 1 }} />
              <Typography variant="h6">
                User Profile
              </Typography>
            </Box>
            
            <Typography variant="body2" color="text.secondary">
              Send money to this user
            </Typography>
          </CardContent>
        </Card>
      );
    }

    return null;
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        QR Code Scanner
      </Typography>
      
      <Typography variant="body2" color="text.secondary" paragraph>
        Scan QR codes to quickly send money or connect with other users.
      </Typography>

      {error && (
        <Alert 
          severity="error" 
          sx={{ mb: 3 }}
          action={
            permissionDenied && (
              <Button color="inherit" size="small" onClick={() => setError(null)}>
                Try Again
              </Button>
            )
          }
        >
          {error}
        </Alert>
      )}

      <Paper sx={{ p: 3, mb: 3 }}>
        {!isScanning && !error && (
          <Box textAlign="center">
            <QrCode sx={{ fontSize: 80, color: 'primary.main', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Ready to Scan
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Click the button below to start scanning QR codes
            </Typography>
            <Button
              variant="contained"
              startIcon={<CameraAlt />}
              onClick={startScanning}
              size="large"
            >
              Start Scanning
            </Button>
          </Box>
        )}

        {isScanning && (
          <Box>
            <Box position="relative" mb={2}>
              <video
                ref={videoRef}
                style={{
                  width: '100%',
                  maxWidth: '400px',
                  height: 'auto',
                  borderRadius: '8px',
                }}
                autoPlay
                playsInline
                muted
              />
              <canvas
                ref={canvasRef}
                style={{ display: 'none' }}
              />
              
              {/* Scanner overlay */}
              <Box
                position="absolute"
                top="50%"
                left="50%"
                sx={{
                  transform: 'translate(-50%, -50%)',
                  width: '200px',
                  height: '200px',
                  border: '2px solid white',
                  borderRadius: '8px',
                  backgroundColor: 'rgba(0,0,0,0.1)',
                }}
              />
            </Box>

            <Box display="flex" justifyContent="center" gap={2} mb={2}>
              <IconButton onClick={toggleFlash} color="primary">
                {flashEnabled ? <FlashOff /> : <FlashOn />}
              </IconButton>
              <IconButton onClick={switchCamera} color="primary">
                {facingMode === 'user' ? <CameraRear /> : <CameraFront />}
              </IconButton>
            </Box>

            <Box textAlign="center">
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Position the QR code within the frame
              </Typography>
              <Button
                variant="outlined"
                onClick={stopScanning}
                startIcon={<Close />}
              >
                Stop Scanning
              </Button>
            </Box>
          </Box>
        )}
      </Paper>

      {/* Feature Information */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            What can you scan?
          </Typography>
          <Box display="flex" flexWrap="wrap" gap={1}>
            <Chip
              icon={<AttachMoney />}
              label="Payment Requests"
              color="primary"
              variant="outlined"
            />
            <Chip
              icon={<Person />}
              label="User Profiles"
              color="secondary"
              variant="outlined"
            />
            <Chip
              icon={<QrCode />}
              label="Waqiti QR Codes"
              color="info"
              variant="outlined"
            />
          </Box>
        </CardContent>
      </Card>

      {/* Settings */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Scanner Settings
          </Typography>
          <FormControlLabel
            control={<Switch />}
            label="Auto-process payments"
            sx={{ mb: 1 }}
          />
          <FormControlLabel
            control={<Switch />}
            label="Save scanned codes"
            sx={{ mb: 1 }}
          />
          <FormControlLabel
            control={<Switch />}
            label="Sound alerts"
          />
        </CardContent>
      </Card>

      {/* Result Dialog */}
      <Dialog 
        open={showResult} 
        onClose={() => setShowResult(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center">
            <Check color="success" sx={{ mr: 1 }} />
            QR Code Scanned
          </Box>
        </DialogTitle>
        <DialogContent>
          {renderScannedResult()}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowResult(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleProcessPayment}
            disabled={!scannedData}
          >
            {scannedData?.type === 'payment_request' ? 'Pay Now' : 'Send Money'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default QRCodeScanner;