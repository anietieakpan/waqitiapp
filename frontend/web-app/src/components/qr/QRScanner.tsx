import React, { useState, useRef, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  Fab,
  Chip,
  Card,
  CardContent,
  Avatar,
  TextField,
  InputAdornment,
  Grid,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  useTheme,
  alpha,
  Fade,
  Zoom,
  Backdrop,
} from '@mui/material';
import QrScannerIcon from '@mui/icons-material/QrCodeScanner';
import CloseIcon from '@mui/icons-material/Close';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import FlashOffIcon from '@mui/icons-material/FlashOff';
import CameraIcon from '@mui/icons-material/CameraAlt';
import FlipCameraIcon from '@mui/icons-material/FlipCameraIos';
import GalleryIcon from '@mui/icons-material/PhotoLibrary';
import HistoryIcon from '@mui/icons-material/History';
import PersonIcon from '@mui/icons-material/Person';
import StoreIcon from '@mui/icons-material/Store';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CheckIcon from '@mui/icons-material/Check';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import SendIcon from '@mui/icons-material/Send';
import BackIcon from '@mui/icons-material/ArrowBack';
import CopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import VerifiedIcon from '@mui/icons-material/Verified';;
import { QrReader } from 'react-qr-reader';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../hooks/redux';
import { formatCurrency } from '../../utils/formatters';
import toast from 'react-hot-toast';

interface QRScannerProps {
  onScan?: (data: QRData) => void;
  onClose?: () => void;
  mode?: 'scan' | 'generate' | 'both';
}

interface QRData {
  type: 'user' | 'payment' | 'merchant' | 'request';
  data: {
    userId?: string;
    userName?: string;
    amount?: number;
    currency?: string;
    description?: string;
    merchantId?: string;
    merchantName?: string;
    requestId?: string;
    timestamp?: string;
  };
}

interface ScanHistory {
  id: string;
  type: QRData['type'];
  data: QRData['data'];
  timestamp: string;
}

const QRScanner: React.FC<QRScannerProps> = ({
  onScan,
  onClose,
  mode = 'both',
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  
  const [scanning, setScanning] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [flashOn, setFlashOn] = useState(false);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('environment');
  const [scanResult, setScanResult] = useState<QRData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [scanHistory, setScanHistory] = useState<ScanHistory[]>([]);
  const [selectedTab, setSelectedTab] = useState<'scan' | 'generate'>(mode === 'generate' ? 'generate' : 'scan');
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Mock scan history
  const mockHistory: ScanHistory[] = [
    {
      id: '1',
      type: 'user',
      data: {
        userId: 'user1',
        userName: 'John Doe',
      },
      timestamp: new Date(Date.now() - 3600000).toISOString(),
    },
    {
      id: '2',
      type: 'merchant',
      data: {
        merchantId: 'merchant1',
        merchantName: 'Coffee Shop',
        amount: 5.50,
        currency: 'USD',
      },
      timestamp: new Date(Date.now() - 86400000).toISOString(),
    },
  ];

  useEffect(() => {
    // Load scan history from local storage
    const savedHistory = localStorage.getItem('qrScanHistory');
    if (savedHistory) {
      setScanHistory(JSON.parse(savedHistory));
    } else {
      setScanHistory(mockHistory);
    }
  }, []);

  const handleScan = (result: any) => {
    if (result) {
      try {
        const data = JSON.parse(result);
        if (data && data.type) {
          setScanResult(data);
          setScanning(false);
          
          // Add to history
          const historyItem: ScanHistory = {
            id: Date.now().toString(),
            type: data.type,
            data: data.data,
            timestamp: new Date().toISOString(),
          };
          const newHistory = [historyItem, ...scanHistory].slice(0, 10);
          setScanHistory(newHistory);
          localStorage.setItem('qrScanHistory', JSON.stringify(newHistory));
          
          if (onScan) {
            onScan(data);
          }
        }
      } catch (err) {
        setError('Invalid QR code format');
      }
    }
  };

  const handleError = (err: any) => {
    setError('Failed to access camera. Please check permissions.');
    console.error(err);
  };

  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      // In a real app, would process the image to extract QR code
      toast.success('QR code uploaded successfully');
    }
  };

  const handleProcessPayment = async () => {
    if (!scanResult) return;
    
    setProcessing(true);
    try {
      // Simulate processing
      await new Promise(resolve => setTimeout(resolve, 1500));
      
      switch (scanResult.type) {
        case 'user':
          navigate('/send', { state: { recipient: scanResult.data } });
          break;
        case 'payment':
          navigate('/pay', { state: { payment: scanResult.data } });
          break;
        case 'merchant':
          navigate('/pay-merchant', { state: { merchant: scanResult.data } });
          break;
        case 'request':
          navigate('/pay-request', { state: { request: scanResult.data } });
          break;
      }
      
      onClose?.();
    } catch (error) {
      setError('Failed to process payment');
    } finally {
      setProcessing(false);
    }
  };

  const generateQRData = (): string => {
    const qrData: QRData = {
      type: amount ? 'payment' : 'user',
      data: {
        userId: user?.id,
        userName: `${user?.firstName} ${user?.lastName}`,
        amount: amount ? parseFloat(amount) : undefined,
        currency: 'USD',
        description: description || undefined,
        timestamp: new Date().toISOString(),
      },
    };
    return JSON.stringify(qrData);
  };

  const handleCopyQR = () => {
    navigator.clipboard.writeText(generateQRData());
    toast.success('QR data copied to clipboard');
  };

  const handleShareQR = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: 'Waqiti Payment QR',
          text: `Pay ${user?.firstName} ${user?.lastName} on Waqiti`,
          url: `https://waqiti.com/pay/${user?.id}`,
        });
      } catch (error) {
        console.error('Error sharing:', error);
      }
    }
  };

  const renderScanner = () => (
    <Box sx={{ position: 'relative', height: '100%' }}>
      {scanning ? (
        <>
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              bgcolor: 'black',
            }}
          >
            <QrReader
              onResult={handleScan}
              onError={handleError}
              constraints={{
                facingMode,
              }}
              style={{ width: '100%', height: '100%' }}
            />
          </Box>
          
          {/* Scanner Overlay */}
          <Box
            sx={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              width: 250,
              height: 250,
              border: `3px solid ${theme.palette.primary.main}`,
              borderRadius: 2,
              '&::before': {
                content: '""',
                position: 'absolute',
                top: -3,
                left: -3,
                width: 30,
                height: 30,
                borderTop: `3px solid ${theme.palette.primary.main}`,
                borderLeft: `3px solid ${theme.palette.primary.main}`,
              },
              '&::after': {
                content: '""',
                position: 'absolute',
                bottom: -3,
                right: -3,
                width: 30,
                height: 30,
                borderBottom: `3px solid ${theme.palette.primary.main}`,
                borderRight: `3px solid ${theme.palette.primary.main}`,
              },
            }}
          >
            <Box
              sx={{
                position: 'absolute',
                top: -3,
                right: -3,
                width: 30,
                height: 30,
                borderTop: `3px solid ${theme.palette.primary.main}`,
                borderRight: `3px solid ${theme.palette.primary.main}`,
              }}
            />
            <Box
              sx={{
                position: 'absolute',
                bottom: -3,
                left: -3,
                width: 30,
                height: 30,
                borderBottom: `3px solid ${theme.palette.primary.main}`,
                borderLeft: `3px solid ${theme.palette.primary.main}`,
              }}
            />
          </Box>
          
          {/* Controls */}
          <Box
            sx={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              p: 3,
              background: `linear-gradient(to top, ${alpha(theme.palette.background.default, 0.9)} 0%, transparent 100%)`,
            }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2 }}>
              <IconButton
                sx={{
                  bgcolor: alpha(theme.palette.background.paper, 0.8),
                  backdropFilter: 'blur(10px)',
                }}
                onClick={() => setFlashOn(!flashOn)}
              >
                {flashOn ? <FlashOnIcon /> : <FlashOffIcon />}
              </IconButton>
              <IconButton
                sx={{
                  bgcolor: alpha(theme.palette.background.paper, 0.8),
                  backdropFilter: 'blur(10px)',
                }}
                onClick={() => setFacingMode(facingMode === 'user' ? 'environment' : 'user')}
              >
                <FlipCameraIcon />
              </IconButton>
              <IconButton
                sx={{
                  bgcolor: alpha(theme.palette.background.paper, 0.8),
                  backdropFilter: 'blur(10px)',
                }}
                onClick={() => fileInputRef.current?.click()}
              >
                <GalleryIcon />
              </IconButton>
            </Box>
            <Button
              fullWidth
              variant="text"
              sx={{ mt: 2, color: 'white' }}
              onClick={() => setScanning(false)}
            >
              Cancel Scan
            </Button>
          </Box>
        </>
      ) : (
        <Box sx={{ p: 3, textAlign: 'center' }}>
          <QrScannerIcon sx={{ fontSize: 120, color: theme.palette.primary.main, mb: 3 }} />
          <Typography variant="h5" gutterBottom>
            Scan QR Code
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
            Scan a QR code to send money, pay a merchant, or add a friend
          </Typography>
          
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <Button
                fullWidth
                variant="contained"
                size="large"
                startIcon={<CameraIcon />}
                onClick={() => setScanning(true)}
              >
                Start Scanning
              </Button>
            </Grid>
            <Grid item xs={6}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<GalleryIcon />}
                onClick={() => fileInputRef.current?.click()}
              >
                Upload Image
              </Button>
            </Grid>
            <Grid item xs={6}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<HistoryIcon />}
                onClick={() => setShowHistory(true)}
              >
                History
              </Button>
            </Grid>
          </Grid>
          
          {error && (
            <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}
        </Box>
      )}
      
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileUpload}
      />
    </Box>
  );

  const renderGenerator = () => (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        Generate QR Code
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Create a QR code to receive payments
      </Typography>
      
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            label="Amount (Optional)"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <MoneyIcon />
                </InputAdornment>
              ),
            }}
            helperText="Leave empty to let the payer choose the amount"
          />
          
          <TextField
            fullWidth
            label="Description (Optional)"
            multiline
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            sx={{ mt: 2 }}
            helperText="Add a note about what this payment is for"
          />
          
          <Box sx={{ mt: 3 }}>
            <Typography variant="subtitle2" gutterBottom>
              Share Options
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="outlined"
                startIcon={<CopyIcon />}
                onClick={handleCopyQR}
              >
                Copy
              </Button>
              <Button
                variant="outlined"
                startIcon={<ShareIcon />}
                onClick={handleShareQR}
              >
                Share
              </Button>
            </Box>
          </Box>
        </Grid>
        
        <Grid item xs={12} md={6}>
          <Paper
            sx={{
              p: 3,
              textAlign: 'center',
              bgcolor: 'background.default',
            }}
          >
            <Box
              sx={{
                width: 200,
                height: 200,
                mx: 'auto',
                mb: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: `1px dashed ${theme.palette.divider}`,
                borderRadius: 1,
              }}
            >
              <QrCodeIcon sx={{ fontSize: 100, color: theme.palette.action.disabled }} />
            </Box>
            <Typography variant="caption" color="text.secondary">
              QR code will appear here
            </Typography>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );

  const renderScanResult = () => {
    if (!scanResult) return null;
    
    const getResultIcon = () => {
      switch (scanResult.type) {
        case 'user':
          return <PersonIcon sx={{ fontSize: 48 }} />;
        case 'merchant':
          return <StoreIcon sx={{ fontSize: 48 }} />;
        case 'payment':
        case 'request':
          return <MoneyIcon sx={{ fontSize: 48 }} />;
      }
    };
    
    const getResultTitle = () => {
      switch (scanResult.type) {
        case 'user':
          return 'User Profile';
        case 'merchant':
          return 'Merchant Payment';
        case 'payment':
          return 'Payment Request';
        case 'request':
          return 'Money Request';
      }
    };
    
    return (
      <Fade in={Boolean(scanResult)}>
        <Box sx={{ p: 3 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Avatar
              sx={{
                width: 80,
                height: 80,
                mx: 'auto',
                mb: 2,
                bgcolor: alpha(theme.palette.primary.main, 0.1),
                color: theme.palette.primary.main,
              }}
            >
              {getResultIcon()}
            </Avatar>
            <Typography variant="h5" gutterBottom>
              {getResultTitle()}
            </Typography>
          </Box>
          
          <Card sx={{ mb: 3 }}>
            <CardContent>
              {scanResult.type === 'user' && (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                    <Avatar sx={{ width: 56, height: 56 }}>
                      {scanResult.data.userName?.[0]}
                    </Avatar>
                    <Box>
                      <Typography variant="h6">
                        {scanResult.data.userName}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        User ID: {scanResult.data.userId}
                      </Typography>
                    </Box>
                  </Box>
                  <Button
                    fullWidth
                    variant="contained"
                    startIcon={<SendIcon />}
                    onClick={handleProcessPayment}
                    disabled={processing}
                  >
                    {processing ? <CircularProgress size={24} /> : 'Send Money'}
                  </Button>
                </>
              )}
              
              {(scanResult.type === 'merchant' || scanResult.type === 'payment') && (
                <>
                  <Typography variant="h6" gutterBottom>
                    {scanResult.data.merchantName || 'Payment Request'}
                  </Typography>
                  {scanResult.data.amount && (
                    <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
                      {formatCurrency(scanResult.data.amount, scanResult.data.currency)}
                    </Typography>
                  )}
                  {scanResult.data.description && (
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                      {scanResult.data.description}
                    </Typography>
                  )}
                  <Button
                    fullWidth
                    variant="contained"
                    startIcon={<SendIcon />}
                    onClick={handleProcessPayment}
                    disabled={processing}
                  >
                    {processing ? <CircularProgress size={24} /> : 'Pay Now'}
                  </Button>
                </>
              )}
            </CardContent>
          </Card>
          
          <Button
            fullWidth
            variant="outlined"
            onClick={() => {
              setScanResult(null);
              setScanning(true);
            }}
          >
            Scan Another Code
          </Button>
        </Box>
      </Fade>
    );
  };

  const renderHistory = () => (
    <Dialog
      open={showHistory}
      onClose={() => setShowHistory(false)}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">Scan History</Typography>
          <IconButton onClick={() => setShowHistory(false)}>
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>
      <DialogContent>
        {scanHistory.length > 0 ? (
          <List>
            {scanHistory.map((item, index) => (
              <React.Fragment key={item.id}>
                {index > 0 && <Divider />}
                <ListItem>
                  <ListItemAvatar>
                    <Avatar sx={{ bgcolor: alpha(theme.palette.primary.main, 0.1) }}>
                      {item.type === 'user' ? <PersonIcon /> : 
                       item.type === 'merchant' ? <StoreIcon /> : <MoneyIcon />}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      item.type === 'user' ? item.data.userName :
                      item.type === 'merchant' ? item.data.merchantName :
                      'Payment Request'
                    }
                    secondary={
                      <Box>
                        {item.data.amount && (
                          <Typography variant="body2">
                            {formatCurrency(item.data.amount, item.data.currency)}
                          </Typography>
                        )}
                        <Typography variant="caption" color="text.secondary">
                          {format(new Date(item.timestamp), 'MMM d, yyyy h:mm a')}
                        </Typography>
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Button
                      size="small"
                      onClick={() => {
                        setScanResult({
                          type: item.type,
                          data: item.data,
                        });
                        setShowHistory(false);
                      }}
                    >
                      Use
                    </Button>
                  </ListItemSecondaryAction>
                </ListItem>
              </React.Fragment>
            ))}
          </List>
        ) : (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <HistoryIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No scan history
            </Typography>
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );

  return (
    <Box sx={{ height: '100%' }}>
      {mode === 'both' && (
        <Paper sx={{ p: 1, mb: 2 }}>
          <Grid container spacing={1}>
            <Grid item xs={6}>
              <Button
                fullWidth
                variant={selectedTab === 'scan' ? 'contained' : 'outlined'}
                startIcon={<QrScannerIcon />}
                onClick={() => setSelectedTab('scan')}
              >
                Scan
              </Button>
            </Grid>
            <Grid item xs={6}>
              <Button
                fullWidth
                variant={selectedTab === 'generate' ? 'contained' : 'outlined'}
                startIcon={<QrCodeIcon />}
                onClick={() => setSelectedTab('generate')}
              >
                Generate
              </Button>
            </Grid>
          </Grid>
        </Paper>
      )}
      
      {scanResult ? (
        renderScanResult()
      ) : (
        <>
          {(mode === 'scan' || (mode === 'both' && selectedTab === 'scan')) && renderScanner()}
          {(mode === 'generate' || (mode === 'both' && selectedTab === 'generate')) && renderGenerator()}
        </>
      )}
      
      {renderHistory()}
    </Box>
  );
};

export default QRScanner;