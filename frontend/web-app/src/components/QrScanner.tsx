import React, { useEffect, useRef, useState } from 'react';
import {
  Box,
  Button,
  Typography,
  Alert,
  Paper,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  CircularProgress,
  Chip,
} from '@mui/material';
import CameraIcon from '@mui/icons-material/CameraAlt';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import FlashOffIcon from '@mui/icons-material/FlashOff';
import CameraSwitchIcon from '@mui/icons-material/CameraswapOutlined';
import PhotoLibraryIcon from '@mui/icons-material/PhotoLibrary';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';;
import { QrReader } from 'react-qr-reader';
import { motion, AnimatePresence } from 'framer-motion';

interface QrScannerProps {
  onScan: (data: string) => void;
  onError?: (error: Error) => void;
}

const QrScanner: React.FC<QrScannerProps> = ({ onScan, onError }) => {
  const [error, setError] = useState<string>('');
  const [scanning, setScanning] = useState(true);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('environment');
  const [torchEnabled, setTorchEnabled] = useState(false);
  const [showManualInput, setShowManualInput] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [lastScanned, setLastScanned] = useState<string>('');
  const [scanSuccess, setScanSuccess] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleScan = (result: any) => {
    if (result?.text && result.text !== lastScanned) {
      setLastScanned(result.text);
      setScanSuccess(true);
      setScanning(false);
      
      // Vibrate if available
      if (navigator.vibrate) {
        navigator.vibrate(200);
      }

      // Show success animation
      setTimeout(() => {
        onScan(result.text);
      }, 500);
    }
  };

  const handleError = (err: any) => {
    console.error('QR Scanner error:', err);
    setError(err?.message || 'Failed to access camera');
    if (onError) {
      onError(err);
    }
  };

  const toggleCamera = () => {
    setFacingMode((prev) => (prev === 'user' ? 'environment' : 'user'));
  };

  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        canvas.width = img.width;
        canvas.height = img.height;
        ctx.drawImage(img, 0, 0);

        // Here you would typically use a QR code detection library
        // For now, we'll show the manual input dialog
        setShowManualInput(true);
      };
      img.src = e.target?.result as string;
    };
    reader.readAsDataURL(file);
  };

  const handleManualSubmit = () => {
    if (manualCode.trim()) {
      onScan(manualCode.trim());
      setShowManualInput(false);
    }
  };

  const renderScanner = () => (
    <Box sx={{ position: 'relative', width: '100%', height: '100%' }}>
      {scanning && !error ? (
        <>
          <QrReader
            onResult={handleScan}
            constraints={{
              facingMode,
            }}
            containerStyle={{
              width: '100%',
              height: '100%',
            }}
            videoStyle={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
            }}
          />

          {/* Scanning overlay */}
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              pointerEvents: 'none',
            }}
          >
            <Box
              sx={{
                width: 250,
                height: 250,
                position: 'relative',
              }}
            >
              {/* Corner markers */}
              {[
                { top: 0, left: 0 },
                { top: 0, right: 0 },
                { bottom: 0, left: 0 },
                { bottom: 0, right: 0 },
              ].map((position, index) => (
                <Box
                  key={index}
                  sx={{
                    position: 'absolute',
                    width: 40,
                    height: 40,
                    borderColor: 'primary.main',
                    borderStyle: 'solid',
                    borderWidth: 0,
                    ...position,
                    ...(position.top !== undefined && {
                      borderTopWidth: 3,
                    }),
                    ...(position.bottom !== undefined && {
                      borderBottomWidth: 3,
                    }),
                    ...(position.left !== undefined && {
                      borderLeftWidth: 3,
                    }),
                    ...(position.right !== undefined && {
                      borderRightWidth: 3,
                    }),
                  }}
                />
              ))}

              {/* Scanning line animation */}
              <motion.div
                style={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  height: 2,
                  backgroundColor: '#2196f3',
                  boxShadow: '0 0 10px #2196f3',
                }}
                animate={{
                  top: ['0%', '100%', '0%'],
                }}
                transition={{
                  duration: 2,
                  repeat: Infinity,
                  ease: 'linear',
                }}
              />
            </Box>
          </Box>

          {/* Instructions */}
          <Box
            sx={{
              position: 'absolute',
              bottom: 120,
              left: 0,
              right: 0,
              textAlign: 'center',
              px: 3,
            }}
          >
            <Typography
              variant="body1"
              sx={{
                color: 'white',
                textShadow: '0 1px 3px rgba(0,0,0,0.5)',
              }}
            >
              Align QR code within the frame
            </Typography>
          </Box>
        </>
      ) : null}

      {/* Success animation */}
      <AnimatePresence>
        {scanSuccess && (
          <motion.div
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
            }}
          >
            <Paper
              sx={{
                p: 3,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                bgcolor: 'background.paper',
              }}
            >
              <CheckCircleIcon sx={{ fontSize: 64, color: 'success.main', mb: 2 }} />
              <Typography variant="h6">QR Code Scanned!</Typography>
            </Paper>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Controls */}
      <Box
        sx={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          p: 2,
          display: 'flex',
          justifyContent: 'center',
          gap: 2,
          bgcolor: 'rgba(0,0,0,0.5)',
        }}
      >
        <IconButton
          color="primary"
          onClick={toggleCamera}
          sx={{
            bgcolor: 'background.paper',
            '&:hover': { bgcolor: 'background.paper' },
          }}
        >
          <CameraSwitchIcon />
        </IconButton>

        <IconButton
          color="primary"
          onClick={() => fileInputRef.current?.click()}
          sx={{
            bgcolor: 'background.paper',
            '&:hover': { bgcolor: 'background.paper' },
          }}
        >
          <PhotoLibraryIcon />
        </IconButton>

        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleFileUpload}
        />
      </Box>

      {/* Error state */}
      {error && (
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'background.default',
            p: 3,
          }}
        >
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
          <Button
            variant="contained"
            startIcon={<CameraIcon />}
            onClick={() => {
              setError('');
              setScanning(true);
            }}
          >
            Try Again
          </Button>
          <Button
            variant="text"
            sx={{ mt: 2 }}
            onClick={() => setShowManualInput(true)}
          >
            Enter Code Manually
          </Button>
        </Box>
      )}
    </Box>
  );

  return (
    <>
      {renderScanner()}

      {/* Manual input dialog */}
      <Dialog open={showManualInput} onClose={() => setShowManualInput(false)}>
        <DialogTitle>Enter QR Code Manually</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" paragraph>
            If you're having trouble scanning, you can enter the code manually
          </Typography>
          <TextField
            fullWidth
            label="QR Code"
            value={manualCode}
            onChange={(e) => setManualCode(e.target.value)}
            placeholder="Enter or paste the code here"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowManualInput(false)}>Cancel</Button>
          <Button
            onClick={handleManualSubmit}
            variant="contained"
            disabled={!manualCode.trim()}
          >
            Submit
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default QrScanner;