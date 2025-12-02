import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  CardActions,
  Typography,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Grid,
  Avatar,
  Chip,
  IconButton,
  Tabs,
  Tab,
  Alert,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  ListItemSecondaryAction,
  Divider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Switch,
  FormControlLabel,
  Tooltip,
  Badge,
  Paper,
  Stepper,
  Step,
  StepLabel,
  CircularProgress,
  LinearProgress,
  Backdrop,
  InputAdornment,
  Slider,
  useTheme,
  alpha,
} from '@mui/material';
import QrCodeIcon from '@mui/icons-material/QrCode';
import QrScannerIcon from '@mui/icons-material/QrCodeScanner';
import ShareIcon from '@mui/icons-material/Share';
import DownloadIcon from '@mui/icons-material/Download';
import CloseIcon from '@mui/icons-material/Close';
import CameraIcon from '@mui/icons-material/CameraAlt';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import FlashOffIcon from '@mui/icons-material/FlashOff';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import FullscreenExitIcon from '@mui/icons-material/FullscreenExit';
import RefreshIcon from '@mui/icons-material/Refresh';
import SettingsIcon from '@mui/icons-material/Settings';
import HistoryIcon from '@mui/icons-material/History';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import PersonIcon from '@mui/icons-material/Person';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SecurityIcon from '@mui/icons-material/Security';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import CopyIcon from '@mui/icons-material/ContentCopy';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import SendIcon from '@mui/icons-material/Send';
import ReceiptIcon from '@mui/icons-material/Receipt';
import LocationIcon from '@mui/icons-material/LocationOn';
import TimerIcon from '@mui/icons-material/Timer';
import NotificationsIcon from '@mui/icons-material/Notifications';
import PublicIcon from '@mui/icons-material/Public';
import LockIcon from '@mui/icons-material/Lock';;
import { format, addMinutes, addHours, addDays } from 'date-fns';
import toast from 'react-hot-toast';
import QRCode from 'qrcode';

import { formatCurrency, formatDate, formatTimeAgo } from '@/utils/formatters';
import { useAppSelector } from '@/hooks/redux';

interface QRPaymentData {
  id: string;
  type: 'PAYMENT_REQUEST' | 'MERCHANT_PAYMENT' | 'PERSON_TO_PERSON' | 'DONATION' | 'INVOICE';
  version: string;
  amount?: number;
  currency: string;
  description?: string;
  merchant?: {
    id: string;
    name: string;
    location?: string;
    category?: string;
  };
  recipient?: {
    id: string;
    name: string;
    email?: string;
    avatar?: string;
  };
  expiry?: string;
  metadata?: {
    orderId?: string;
    invoiceNumber?: string;
    reference?: string;
    tags?: string[];
    location?: {
      lat: number;
      lng: number;
      address?: string;
    };
    restrictions?: {
      minAmount?: number;
      maxAmount?: number;
      allowPartialPayment?: boolean;
      requiresAuth?: boolean;
    };
  };
  security?: {
    encrypted: boolean;
    signature?: string;
    publicKey?: string;
  };
}

interface QRCodeTemplate {
  id: string;
  name: string;
  type: QRPaymentData['type'];
  amount?: number;
  currency: string;
  description: string;
  expiry?: 'NONE' | '15MIN' | '1HOUR' | '1DAY' | '1WEEK';
  favorite: boolean;
  usageCount: number;
  createdAt: string;
  lastUsed?: string;
}

interface QRScanResult {
  success: boolean;
  data?: QRPaymentData;
  error?: string;
  rawData?: string;
}

interface QRPaymentHistory {
  id: string;
  qrCodeId: string;
  type: 'GENERATED' | 'SCANNED' | 'PAYMENT_COMPLETED';
  amount?: number;
  currency?: string;
  counterparty?: {
    name: string;
    avatar?: string;
  };
  timestamp: string;
  status: 'SUCCESS' | 'PENDING' | 'FAILED';
  transactionId?: string;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`qr-tabpanel-${index}`}
      aria-labelledby={`qr-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 0 }}>{children}</Box>}
    </div>
  );
}

const QRPaymentSystemEnhanced: React.FC = () => {
  const theme = useTheme();
  const { user, walletBalance } = useAppSelector((state) => ({ 
    user: state.auth.user,
    walletBalance: state.wallet.walletBalance 
  }));
  
  const [activeTab, setActiveTab] = useState(0);
  const [showGenerateDialog, setShowGenerateDialog] = useState(false);
  const [showScanDialog, setShowScanDialog] = useState(false);
  const [showTemplatesDialog, setShowTemplatesDialog] = useState(false);
  const [showSettingsDialog, setShowSettingsDialog] = useState(false);
  const [showHistoryDialog, setShowHistoryDialog] = useState(false);
  
  const [generatedQR, setGeneratedQR] = useState<string | null>(null);
  const [qrPaymentData, setQrPaymentData] = useState<QRPaymentData | null>(null);
  const [isScanning, setIsScanning] = useState(false);
  const [scanResult, setScanResult] = useState<QRScanResult | null>(null);
  const [loading, setLoading] = useState(false);
  
  // QR Generation form state
  const [qrForm, setQrForm] = useState({
    type: 'PAYMENT_REQUEST' as QRPaymentData['type'],
    amount: '',
    currency: 'USD',
    description: '',
    expiry: '1HOUR' as const,
    requireAuth: false,
    allowPartialPayment: true,
    tags: [] as string[],
  });
  
  // QR Scanner settings
  const [scannerSettings, setScannerSettings] = useState({
    flashEnabled: false,
    soundEnabled: true,
    vibrateEnabled: true,
    autoFocus: true,
    continuous: false,
    preferredCamera: 'environment' as 'user' | 'environment',
  });
  
  const [templates, setTemplates] = useState<QRCodeTemplate[]>([]);
  const [qrHistory, setQrHistory] = useState<QRPaymentHistory[]>([]);
  
  // Refs
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const scanIntervalRef = useRef<NodeJS.Timeout | null>(null);
  
  useEffect(() => {
    loadTemplates();
    loadQRHistory();
    
    return () => {
      stopScanning();
    };
  }, []);

  const loadTemplates = async () => {
    // Mock data - in real app, fetch from API
    const mockTemplates: QRCodeTemplate[] = [
      {
        id: '1',
        name: 'Coffee Payment',
        type: 'MERCHANT_PAYMENT',
        amount: 5.99,
        currency: 'USD',
        description: 'Daily coffee payment',
        expiry: '15MIN',
        favorite: true,
        usageCount: 45,
        createdAt: new Date(Date.now() - 86400000 * 7).toISOString(),
        lastUsed: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        id: '2',
        name: 'Lunch Split',
        type: 'PERSON_TO_PERSON',
        currency: 'USD',
        description: 'Split lunch bill',
        expiry: '1HOUR',
        favorite: true,
        usageCount: 12,
        createdAt: new Date(Date.now() - 86400000 * 14).toISOString(),
        lastUsed: new Date(Date.now() - 86400000 * 2).toISOString(),
      },
    ];
    
    setTemplates(mockTemplates);
  };

  const loadQRHistory = async () => {
    // Mock data - in real app, fetch from API
    const mockHistory: QRPaymentHistory[] = [
      {
        id: '1',
        qrCodeId: 'QR001',
        type: 'PAYMENT_COMPLETED',
        amount: 25.50,
        currency: 'USD',
        counterparty: {
          name: 'Alice Johnson',
          avatar: '/avatars/alice.jpg',
        },
        timestamp: new Date(Date.now() - 3600000).toISOString(),
        status: 'SUCCESS',
        transactionId: 'TXN_12345',
      },
      {
        id: '2',
        qrCodeId: 'QR002',
        type: 'SCANNED',
        amount: 15.99,
        currency: 'USD',
        counterparty: {
          name: 'Bob\'s Coffee Shop',
        },
        timestamp: new Date(Date.now() - 7200000).toISOString(),
        status: 'PENDING',
      },
    ];
    
    setQrHistory(mockHistory);
  };

  const generateQRCode = async () => {
    setLoading(true);
    try {
      const expiryDate = getExpiryDate(qrForm.expiry);
      
      const paymentData: QRPaymentData = {
        id: `QR_${Date.now()}`,
        type: qrForm.type,
        version: '1.0',
        amount: qrForm.amount ? parseFloat(qrForm.amount) : undefined,
        currency: qrForm.currency,
        description: qrForm.description || undefined,
        recipient: {
          id: user?.id || 'unknown',
          name: user?.name || 'Unknown User',
          email: user?.email,
        },
        expiry: expiryDate?.toISOString(),
        metadata: {
          reference: `REF_${Date.now()}`,
          tags: qrForm.tags,
          restrictions: {
            allowPartialPayment: qrForm.allowPartialPayment,
            requiresAuth: qrForm.requireAuth,
          },
        },
        security: {
          encrypted: true,
          // In a real app, you would sign the data
        },
      };
      
      const qrDataString = JSON.stringify(paymentData);
      const qrCodeDataURL = await QRCode.toDataURL(qrDataString, {
        width: 400,
        margin: 2,
        color: {
          dark: theme.palette.text.primary,
          light: theme.palette.background.paper,
        },
      });
      
      setGeneratedQR(qrCodeDataURL);
      setQrPaymentData(paymentData);
      
      // Add to history
      const historyEntry: QRPaymentHistory = {
        id: `HIST_${Date.now()}`,
        qrCodeId: paymentData.id,
        type: 'GENERATED',
        amount: paymentData.amount,
        currency: paymentData.currency,
        timestamp: new Date().toISOString(),
        status: 'SUCCESS',
      };
      
      setQrHistory(prev => [historyEntry, ...prev]);
      
      toast.success('QR code generated successfully!');
    } catch (error) {
      console.error('Failed to generate QR code:', error);
      toast.error('Failed to generate QR code');
    } finally {
      setLoading(false);
    }
  };

  const getExpiryDate = (expiry: string): Date | null => {
    const now = new Date();
    switch (expiry) {
      case '15MIN': return addMinutes(now, 15);
      case '1HOUR': return addHours(now, 1);
      case '1DAY': return addDays(now, 1);
      case '1WEEK': return addDays(now, 7);
      case 'NONE': return null;
      default: return addHours(now, 1);
    }
  };

  const startScanning = async () => {
    try {
      const constraints = {
        video: {
          facingMode: scannerSettings.preferredCamera,
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
      };
      
      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      streamRef.current = stream;
      
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play();
      }
      
      setIsScanning(true);
      
      // Start scanning for QR codes
      scanIntervalRef.current = setInterval(() => {
        if (videoRef.current && canvasRef.current) {
          scanQRCode();
        }
      }, 500);
      
    } catch (error) {
      console.error('Failed to start camera:', error);
      toast.error('Failed to access camera');
    }
  };

  const stopScanning = () => {
    if (scanIntervalRef.current) {
      clearInterval(scanIntervalRef.current);
      scanIntervalRef.current = null;
    }
    
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    
    setIsScanning(false);
  };

  const scanQRCode = async () => {
    if (!videoRef.current || !canvasRef.current) return;
    
    const canvas = canvasRef.current;
    const video = videoRef.current;
    const context = canvas.getContext('2d');
    
    if (!context) return;
    
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    context.drawImage(video, 0, 0);
    
    // In a real app, you would use a QR code scanning library like jsQR
    // For now, we'll simulate QR code detection
    // const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
    // const code = jsQR(imageData.data, imageData.width, imageData.height);
    
    // Simulate QR code detection for demo
    if (Math.random() < 0.1) { // 10% chance of "detecting" a QR code
      const mockQRData: QRPaymentData = {
        id: 'MOCK_QR_001',
        type: 'MERCHANT_PAYMENT',
        version: '1.0',
        amount: 25.99,
        currency: 'USD',
        description: 'Coffee Shop Payment',
        merchant: {
          id: 'merchant_123',
          name: 'Bob\'s Coffee Shop',
          location: 'Downtown',
          category: 'Food & Beverage',
        },
        expiry: addHours(new Date(), 1).toISOString(),
      };
      
      handleQRCodeDetected(JSON.stringify(mockQRData));
    }
  };

  const handleQRCodeDetected = (data: string) => {
    try {
      const qrData: QRPaymentData = JSON.parse(data);
      
      // Validate QR code
      if (!qrData.id || !qrData.type || !qrData.version) {
        throw new Error('Invalid QR code format');
      }
      
      // Check if expired
      if (qrData.expiry && new Date(qrData.expiry) < new Date()) {
        throw new Error('QR code has expired');
      }
      
      setScanResult({ success: true, data: qrData, rawData: data });
      
      // Add to history
      const historyEntry: QRPaymentHistory = {
        id: `HIST_${Date.now()}`,
        qrCodeId: qrData.id,
        type: 'SCANNED',
        amount: qrData.amount,
        currency: qrData.currency,
        counterparty: qrData.merchant ? {
          name: qrData.merchant.name,
        } : qrData.recipient ? {
          name: qrData.recipient.name,
          avatar: qrData.recipient.avatar,
        } : undefined,
        timestamp: new Date().toISOString(),
        status: 'PENDING',
      };
      
      setQrHistory(prev => [historyEntry, ...prev]);
      
      stopScanning();
      
      // Play sound and vibrate if enabled
      if (scannerSettings.soundEnabled) {
        // Play scan sound
      }
      
      if (scannerSettings.vibrateEnabled && navigator.vibrate) {
        navigator.vibrate(200);
      }
      
      toast.success('QR code scanned successfully!');
      
    } catch (error) {
      console.error('Failed to parse QR code:', error);
      setScanResult({ 
        success: false, 
        error: error instanceof Error ? error.message : 'Invalid QR code',
        rawData: data,
      });
      toast.error('Invalid QR code');
    }
  };

  const handlePayment = async (qrData: QRPaymentData) => {
    setLoading(true);
    try {
      // Simulate payment processing
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // Update history
      const historyEntry: QRPaymentHistory = {
        id: `HIST_${Date.now()}`,
        qrCodeId: qrData.id,
        type: 'PAYMENT_COMPLETED',
        amount: qrData.amount,
        currency: qrData.currency,
        counterparty: qrData.merchant ? {
          name: qrData.merchant.name,
        } : qrData.recipient ? {
          name: qrData.recipient.name,
          avatar: qrData.recipient.avatar,
        } : undefined,
        timestamp: new Date().toISOString(),
        status: 'SUCCESS',
        transactionId: `TXN_${Date.now()}`,
      };
      
      setQrHistory(prev => [historyEntry, ...prev.map(h => 
        h.qrCodeId === qrData.id && h.type === 'SCANNED' ? 
          { ...h, status: 'SUCCESS' as const, transactionId: historyEntry.transactionId } : h
      )]);
      
      setScanResult(null);
      setShowScanDialog(false);
      
      toast.success('Payment completed successfully!');
    } catch (error) {
      console.error('Payment failed:', error);
      toast.error('Payment failed');
    } finally {
      setLoading(false);
    }
  };

  const shareQRCode = async () => {
    if (!generatedQR || !qrPaymentData) return;
    
    try {
      if (navigator.share && navigator.canShare) {
        // Convert data URL to blob
        const response = await fetch(generatedQR);
        const blob = await response.blob();
        const file = new File([blob], 'qr-code.png', { type: 'image/png' });
        
        const shareData = {
          title: 'Waqiti Payment QR Code',
          text: qrPaymentData.description || 'Payment QR Code',
          files: [file],
        };
        
        if (navigator.canShare(shareData)) {
          await navigator.share(shareData);
          return;
        }
      }
      
      // Fallback: copy link to clipboard
      const paymentLink = `https://waqiti.app/pay/${qrPaymentData.id}`;
      await navigator.clipboard.writeText(paymentLink);
      toast.success('Payment link copied to clipboard!');
      
    } catch (error) {
      console.error('Failed to share QR code:', error);
      toast.error('Failed to share QR code');
    }
  };

  const downloadQRCode = () => {
    if (!generatedQR) return;
    
    const link = document.createElement('a');
    link.download = `waqiti-qr-${Date.now()}.png`;
    link.href = generatedQR;
    link.click();
  };

  const useTemplate = (template: QRCodeTemplate) => {
    setQrForm({
      type: template.type,
      amount: template.amount?.toString() || '',
      currency: template.currency,
      description: template.description,
      expiry: template.expiry || '1HOUR',
      requireAuth: false,
      allowPartialPayment: true,
      tags: [],
    });
    
    // Update usage count
    setTemplates(prev => prev.map(t => 
      t.id === template.id ? 
        { ...t, usageCount: t.usageCount + 1, lastUsed: new Date().toISOString() } : 
        t
    ));
    
    setShowTemplatesDialog(false);
    setShowGenerateDialog(true);
  };

  const saveAsTemplate = () => {
    const template: QRCodeTemplate = {
      id: `TEMPLATE_${Date.now()}`,
      name: qrForm.description || 'Unnamed Template',
      type: qrForm.type,
      amount: qrForm.amount ? parseFloat(qrForm.amount) : undefined,
      currency: qrForm.currency,
      description: qrForm.description,
      expiry: qrForm.expiry,
      favorite: false,
      usageCount: 0,
      createdAt: new Date().toISOString(),
    };
    
    setTemplates(prev => [template, ...prev]);
    toast.success('Template saved successfully!');
  };

  const renderGenerateTab = () => (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">
          Generate QR Code
        </Typography>
        <Button
          startIcon={<ReceiptIcon />}
          onClick={() => setShowTemplatesDialog(true)}
        >
          Templates
        </Button>
      </Box>

      <Grid container spacing={3}>
        {/* Quick Generate Buttons */}
        <Grid item xs={12}>
          <Typography variant="subtitle2" gutterBottom>
            Quick Generate
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => {
                  setQrForm(prev => ({ ...prev, type: 'PAYMENT_REQUEST' }));
                  setShowGenerateDialog(true);
                }}
                sx={{ py: 2, flexDirection: 'column', gap: 1 }}
              >
                <PersonIcon />
                <Typography variant="caption">Request Money</Typography>
              </Button>
            </Grid>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => {
                  setQrForm(prev => ({ ...prev, type: 'MERCHANT_PAYMENT' }));
                  setShowGenerateDialog(true);
                }}
                sx={{ py: 2, flexDirection: 'column', gap: 1 }}
              >
                <ReceiptIcon />
                <Typography variant="caption">Merchant</Typography>
              </Button>
            </Grid>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => {
                  setQrForm(prev => ({ ...prev, type: 'PERSON_TO_PERSON' }));
                  setShowGenerateDialog(true);
                }}
                sx={{ py: 2, flexDirection: 'column', gap: 1 }}
              >
                <SendIcon />
                <Typography variant="caption">P2P Payment</Typography>
              </Button>
            </Grid>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => {
                  setQrForm(prev => ({ ...prev, type: 'DONATION' }));
                  setShowGenerateDialog(true);
                }}
                sx={{ py: 2, flexDirection: 'column', gap: 1 }}
              >
                <StarIcon />
                <Typography variant="caption">Donation</Typography>
              </Button>
            </Grid>
          </Grid>
        </Grid>

        {/* Recent Templates */}
        <Grid item xs={12}>
          <Typography variant="subtitle2" gutterBottom>
            Recent Templates
          </Typography>
          <List>
            {templates.slice(0, 3).map(template => (
              <ListItem 
                key={template.id} 
                button 
                onClick={() => useTemplate(template)}
              >
                <ListItemAvatar>
                  <Avatar>
                    <QrCodeIcon />
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={template.name}
                  secondary={
                    <Box>
                      <Typography variant="body2" component="span">
                        {template.type} • {template.currency}
                        {template.amount && ` • ${formatCurrency(template.amount)}`}
                      </Typography>
                      <br />
                      <Typography variant="caption" color="text.secondary">
                        Used {template.usageCount} times
                        {template.lastUsed && ` • Last: ${formatTimeAgo(template.lastUsed)}`}
                      </Typography>
                    </Box>
                  }
                />
                <ListItemSecondaryAction>
                  <IconButton onClick={(e) => {
                    e.stopPropagation();
                    setTemplates(prev => prev.map(t => 
                      t.id === template.id ? { ...t, favorite: !t.favorite } : t
                    ));
                  }}>
                    {template.favorite ? <StarIcon color="warning" /> : <StarBorderIcon />}
                  </IconButton>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </Grid>
      </Grid>
    </Box>
  );

  const renderScanTab = () => (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">
          Scan QR Code
        </Typography>
        <Button
          startIcon={<SettingsIcon />}
          onClick={() => setShowSettingsDialog(true)}
        >
          Scanner Settings
        </Button>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Card>
            <CardContent sx={{ textAlign: 'center', py: 4 }}>
              <QrScannerIcon sx={{ fontSize: 80, color: 'primary.main', mb: 2 }} />
              <Typography variant="h6" gutterBottom>
                Scan Payment QR Code
              </Typography>
              <Typography variant="body2" color="text.secondary" paragraph>
                Point your camera at a QR code to scan it for payment
              </Typography>
              <Button
                variant="contained"
                size="large"
                startIcon={<CameraIcon />}
                onClick={() => setShowScanDialog(true)}
              >
                Start Scanning
              </Button>
            </CardContent>
          </Card>
        </Grid>

        {/* Recent Scans */}
        <Grid item xs={12}>
          <Typography variant="subtitle2" gutterBottom>
            Recent Scans
          </Typography>
          <List>
            {qrHistory
              .filter(h => h.type === 'SCANNED' || h.type === 'PAYMENT_COMPLETED')
              .slice(0, 5)
              .map(scan => (
                <ListItem key={scan.id}>
                  <ListItemAvatar>
                    <Avatar>
                      {scan.counterparty?.avatar ? (
                        <img src={scan.counterparty.avatar} alt={scan.counterparty.name} />
                      ) : (
                        scan.counterparty?.name.charAt(0) || 'Q'
                      )}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={scan.counterparty?.name || 'Unknown'}
                    secondary={
                      <Box>
                        <Typography variant="body2" component="span">
                          {scan.amount && formatCurrency(scan.amount)} {scan.currency}
                        </Typography>
                        <br />
                        <Typography variant="caption" color="text.secondary">
                          {formatTimeAgo(scan.timestamp)}
                        </Typography>
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Chip
                      label={scan.status}
                      color={
                        scan.status === 'SUCCESS' ? 'success' :
                        scan.status === 'PENDING' ? 'warning' : 'error'
                      }
                      size="small"
                    />
                  </ListItemSecondaryAction>
                </ListItem>
              ))
            }
          </List>
        </Grid>
      </Grid>
    </Box>
  );

  const renderHistoryTab = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        QR Code History
      </Typography>

      <List>
        {qrHistory.map(item => {
          const getIcon = () => {
            switch (item.type) {
              case 'GENERATED': return <QrCodeIcon />;
              case 'SCANNED': return <QrScannerIcon />;
              case 'PAYMENT_COMPLETED': return <CheckCircleIcon />;
              default: return <InfoIcon />;
            }
          };

          const getTypeLabel = () => {
            switch (item.type) {
              case 'GENERATED': return 'Generated QR Code';
              case 'SCANNED': return 'Scanned QR Code';
              case 'PAYMENT_COMPLETED': return 'Payment Completed';
              default: return item.type;
            }
          };

          return (
            <ListItem key={item.id}>
              <ListItemAvatar>
                <Avatar sx={{ 
                  bgcolor: item.status === 'SUCCESS' ? 'success.main' :
                           item.status === 'PENDING' ? 'warning.main' : 'error.main'
                }}>
                  {getIcon()}
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={getTypeLabel()}
                secondary={
                  <Box>
                    {item.counterparty && (
                      <Typography variant="body2" component="span">
                        {item.counterparty.name}
                      </Typography>
                    )}
                    {item.amount && (
                      <>
                        <br />
                        <Typography variant="body2" component="span">
                          {formatCurrency(item.amount)} {item.currency}
                        </Typography>
                      </>
                    )}
                    <br />
                    <Typography variant="caption" color="text.secondary">
                      {formatTimeAgo(item.timestamp)}
                      {item.transactionId && ` • ${item.transactionId}`}
                    </Typography>
                  </Box>
                }
              />
              <ListItemSecondaryAction>
                <Chip
                  label={item.status}
                  color={
                    item.status === 'SUCCESS' ? 'success' :
                    item.status === 'PENDING' ? 'warning' : 'error'
                  }
                  size="small"
                />
              </ListItemSecondaryAction>
            </ListItem>
          );
        })}
      </List>

      {qrHistory.length === 0 && (
        <Box textAlign="center" py={4}>
          <HistoryIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary">
            No QR Activity Yet
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Generate or scan QR codes to see your history here
          </Typography>
        </Box>
      )}
    </Box>
  );

  return (
    <Box>
      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={(e, newValue) => setActiveTab(newValue)}>
          <Tab icon={<QrCodeIcon />} label="Generate" />
          <Tab icon={<QrScannerIcon />} label="Scan" />
          <Tab 
            icon={
              <Badge badgeContent={qrHistory.length} color="primary">
                <HistoryIcon />
              </Badge>
            } 
            label="History" 
          />
        </Tabs>
      </Box>

      {/* Tab Content */}
      <TabPanel value={activeTab} index={0}>
        {renderGenerateTab()}
      </TabPanel>
      <TabPanel value={activeTab} index={1}>
        {renderScanTab()}
      </TabPanel>
      <TabPanel value={activeTab} index={2}>
        {renderHistoryTab()}
      </TabPanel>

      {/* Generate QR Dialog */}
      <Dialog
        open={showGenerateDialog}
        onClose={() => setShowGenerateDialog(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Generate QR Code
          <IconButton
            onClick={() => setShowGenerateDialog(false)}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={3}>
            {/* Form */}
            <Grid item xs={12} md={6}>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <FormControl fullWidth>
                    <InputLabel>QR Code Type</InputLabel>
                    <Select
                      value={qrForm.type}
                      onChange={(e) => setQrForm(prev => ({ 
                        ...prev, 
                        type: e.target.value as QRPaymentData['type'] 
                      }))}
                      label="QR Code Type"
                    >
                      <MenuItem value="PAYMENT_REQUEST">Payment Request</MenuItem>
                      <MenuItem value="MERCHANT_PAYMENT">Merchant Payment</MenuItem>
                      <MenuItem value="PERSON_TO_PERSON">Person to Person</MenuItem>
                      <MenuItem value="DONATION">Donation</MenuItem>
                      <MenuItem value="INVOICE">Invoice</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                
                <Grid item xs={8}>
                  <TextField
                    fullWidth
                    label="Amount (Optional)"
                    type="number"
                    value={qrForm.amount}
                    onChange={(e) => setQrForm(prev => ({ ...prev, amount: e.target.value }))}
                    InputProps={{
                      startAdornment: <InputAdornment position="start">$</InputAdornment>,
                    }}
                  />
                </Grid>
                
                <Grid item xs={4}>
                  <FormControl fullWidth>
                    <InputLabel>Currency</InputLabel>
                    <Select
                      value={qrForm.currency}
                      onChange={(e) => setQrForm(prev => ({ ...prev, currency: e.target.value }))}
                      label="Currency"
                    >
                      <MenuItem value="USD">USD</MenuItem>
                      <MenuItem value="EUR">EUR</MenuItem>
                      <MenuItem value="GBP">GBP</MenuItem>
                      <MenuItem value="CAD">CAD</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Description"
                    multiline
                    rows={2}
                    value={qrForm.description}
                    onChange={(e) => setQrForm(prev => ({ ...prev, description: e.target.value }))}
                    placeholder="What is this payment for?"
                  />
                </Grid>
                
                <Grid item xs={12}>
                  <FormControl fullWidth>
                    <InputLabel>Expiry</InputLabel>
                    <Select
                      value={qrForm.expiry}
                      onChange={(e) => setQrForm(prev => ({ ...prev, expiry: e.target.value as any }))}
                      label="Expiry"
                    >
                      <MenuItem value="15MIN">15 Minutes</MenuItem>
                      <MenuItem value="1HOUR">1 Hour</MenuItem>
                      <MenuItem value="1DAY">1 Day</MenuItem>
                      <MenuItem value="1WEEK">1 Week</MenuItem>
                      <MenuItem value="NONE">Never</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                
                <Grid item xs={12}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={qrForm.allowPartialPayment}
                        onChange={(e) => setQrForm(prev => ({ 
                          ...prev, 
                          allowPartialPayment: e.target.checked 
                        }))}
                      />
                    }
                    label="Allow partial payments"
                  />
                </Grid>
                
                <Grid item xs={12}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={qrForm.requireAuth}
                        onChange={(e) => setQrForm(prev => ({ 
                          ...prev, 
                          requireAuth: e.target.checked 
                        }))}
                      />
                    }
                    label="Require authentication"
                  />
                </Grid>
              </Grid>
            </Grid>
            
            {/* Preview */}
            <Grid item xs={12} md={6}>
              <Box textAlign="center">
                <Typography variant="h6" gutterBottom>
                  Preview
                </Typography>
                
                {generatedQR ? (
                  <Box>
                    <Box
                      component="img"
                      src={generatedQR}
                      alt="Generated QR Code"
                      sx={{
                        width: 250,
                        height: 250,
                        border: 1,
                        borderColor: 'divider',
                        borderRadius: 2,
                        mb: 2,
                      }}
                    />
                    
                    {qrPaymentData && (
                      <Card variant="outlined" sx={{ textAlign: 'left', mb: 2 }}>
                        <CardContent>
                          <Typography variant="subtitle2" gutterBottom>
                            QR Code Details
                          </Typography>
                          <Typography variant="body2">
                            Type: {qrPaymentData.type}
                          </Typography>
                          {qrPaymentData.amount && (
                            <Typography variant="body2">
                              Amount: {formatCurrency(qrPaymentData.amount)} {qrPaymentData.currency}
                            </Typography>
                          )}
                          {qrPaymentData.description && (
                            <Typography variant="body2">
                              Description: {qrPaymentData.description}
                            </Typography>
                          )}
                          {qrPaymentData.expiry && (
                            <Typography variant="body2" color="warning.main">
                              Expires: {format(new Date(qrPaymentData.expiry), 'PPpp')}
                            </Typography>
                          )}
                        </CardContent>
                      </Card>
                    )}
                    
                    <Box display="flex" gap={1} justifyContent="center">
                      <Button
                        variant="outlined"
                        startIcon={<ShareIcon />}
                        onClick={shareQRCode}
                      >
                        Share
                      </Button>
                      <Button
                        variant="outlined"
                        startIcon={<DownloadIcon />}
                        onClick={downloadQRCode}
                      >
                        Download
                      </Button>
                    </Box>
                  </Box>
                ) : (
                  <Box
                    sx={{
                      width: 250,
                      height: 250,
                      border: 2,
                      borderStyle: 'dashed',
                      borderColor: 'divider',
                      borderRadius: 2,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      mx: 'auto',
                      mb: 2,
                    }}
                  >
                    <Typography color="text.secondary">
                      QR Code Preview
                    </Typography>
                  </Box>
                )}
              </Box>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowGenerateDialog(false)}>Cancel</Button>
          <Button onClick={saveAsTemplate}>Save Template</Button>
          <Button
            variant="contained"
            onClick={generateQRCode}
            disabled={loading}
            endIcon={loading ? <CircularProgress size={20} /> : <QrCodeIcon />}
          >
            Generate QR Code
          </Button>
        </DialogActions>
      </Dialog>

      {/* Scanner Dialog */}
      <Dialog
        open={showScanDialog}
        onClose={() => {
          setShowScanDialog(false);
          stopScanning();
          setScanResult(null);
        }}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: { bgcolor: 'black' }
        }}
      >
        <DialogContent sx={{ p: 0, position: 'relative' }}>
          {!scanResult ? (
            <Box position="relative">
              <video
                ref={videoRef}
                style={{
                  width: '100%',
                  height: '400px',
                  objectFit: 'cover',
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
                    width: 200,
                    height: 200,
                    border: 2,
                    borderColor: 'primary.main',
                    borderRadius: 2,
                    position: 'relative',
                    '&::before, &::after': {
                      content: '""',
                      position: 'absolute',
                      width: 20,
                      height: 20,
                      border: '3px solid',
                      borderColor: 'primary.main',
                    },
                    '&::before': {
                      top: -3,
                      left: -3,
                      borderRight: 'none',
                      borderBottom: 'none',
                    },
                    '&::after': {
                      bottom: -3,
                      right: -3,
                      borderLeft: 'none',
                      borderTop: 'none',
                    },
                  }}
                />
              </Box>
              
              {/* Controls */}
              <Box
                sx={{
                  position: 'absolute',
                  bottom: 16,
                  left: 16,
                  right: 16,
                  display: 'flex',
                  justifyContent: 'center',
                  gap: 2,
                }}
              >
                <IconButton
                  sx={{ bgcolor: 'rgba(0,0,0,0.5)', color: 'white' }}
                  onClick={() => {
                    setScannerSettings(prev => ({ 
                      ...prev, 
                      flashEnabled: !prev.flashEnabled 
                    }));
                    // In real app, toggle flash
                  }}
                >
                  {scannerSettings.flashEnabled ? <FlashOnIcon /> : <FlashOffIcon />}
                </IconButton>
                
                <IconButton
                  sx={{ bgcolor: 'rgba(0,0,0,0.5)', color: 'white' }}
                  onClick={() => {
                    stopScanning();
                    setShowScanDialog(false);
                  }}
                >
                  <CloseIcon />
                </IconButton>
              </Box>
              
              {!isScanning && (
                <Box
                  sx={{
                    position: 'absolute',
                    top: '50%',
                    left: '50%',
                    transform: 'translate(-50%, -50%)',
                  }}
                >
                  <Button
                    variant="contained"
                    startIcon={<CameraIcon />}
                    onClick={startScanning}
                  >
                    Start Camera
                  </Button>
                </Box>
              )}
              
              {isScanning && (
                <Box
                  sx={{
                    position: 'absolute',
                    top: 16,
                    left: 16,
                    right: 16,
                    textAlign: 'center',
                  }}
                >
                  <Typography variant="h6" sx={{ color: 'white', textShadow: '0 0 10px black' }}>
                    Scanning for QR Code...
                  </Typography>
                </Box>
              )}
            </Box>
          ) : (
            <Box p={3} bgcolor="background.paper">
              {scanResult.success ? (
                <Box>
                  <Box display="flex" alignItems="center" mb={2}>
                    <CheckCircleIcon color="success" sx={{ mr: 1 }} />
                    <Typography variant="h6">
                      QR Code Scanned Successfully
                    </Typography>
                  </Box>
                  
                  {scanResult.data && (
                    <Card variant="outlined" sx={{ mb: 3 }}>
                      <CardContent>
                        <Typography variant="subtitle1" gutterBottom>
                          Payment Details
                        </Typography>
                        
                        <Grid container spacing={2}>
                          <Grid item xs={6}>
                            <Typography variant="body2" color="text.secondary">
                              Type
                            </Typography>
                            <Typography variant="body1">
                              {scanResult.data.type}
                            </Typography>
                          </Grid>
                          
                          {scanResult.data.amount && (
                            <Grid item xs={6}>
                              <Typography variant="body2" color="text.secondary">
                                Amount
                              </Typography>
                              <Typography variant="h6" color="primary">
                                {formatCurrency(scanResult.data.amount)} {scanResult.data.currency}
                              </Typography>
                            </Grid>
                          )}
                          
                          {scanResult.data.description && (
                            <Grid item xs={12}>
                              <Typography variant="body2" color="text.secondary">
                                Description
                              </Typography>
                              <Typography variant="body1">
                                {scanResult.data.description}
                              </Typography>
                            </Grid>
                          )}
                          
                          {scanResult.data.merchant && (
                            <Grid item xs={12}>
                              <Typography variant="body2" color="text.secondary">
                                Merchant
                              </Typography>
                              <Box display="flex" alignItems="center" gap={1}>
                                <Typography variant="body1">
                                  {scanResult.data.merchant.name}
                                </Typography>
                                {scanResult.data.merchant.location && (
                                  <Chip 
                                    label={scanResult.data.merchant.location} 
                                    size="small" 
                                    variant="outlined" 
                                  />
                                )}
                              </Box>
                            </Grid>
                          )}
                          
                          {scanResult.data.expiry && (
                            <Grid item xs={12}>
                              <Alert severity="warning" size="small">
                                Expires: {format(new Date(scanResult.data.expiry), 'PPpp')}
                              </Alert>
                            </Grid>
                          )}
                        </Grid>
                      </CardContent>
                      
                      <CardActions>
                        <Button
                          variant="contained"
                          fullWidth
                          onClick={() => handlePayment(scanResult.data!)}
                          disabled={loading || (walletBalance && scanResult.data!.amount && walletBalance.currentBalance < scanResult.data!.amount)}
                          endIcon={loading ? <CircularProgress size={20} /> : <SendIcon />}
                        >
                          {loading ? 'Processing...' : 'Pay Now'}
                        </Button>
                      </CardActions>
                    </Card>
                  )}
                </Box>
              ) : (
                <Box>
                  <Box display="flex" alignItems="center" mb={2}>
                    <ErrorIcon color="error" sx={{ mr: 1 }} />
                    <Typography variant="h6">
                      Invalid QR Code
                    </Typography>
                  </Box>
                  
                  <Alert severity="error" sx={{ mb: 2 }}>
                    {scanResult.error}
                  </Alert>
                  
                  {scanResult.rawData && (
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="subtitle2" gutterBottom>
                          Raw Data
                        </Typography>
                        <Typography
                          variant="body2"
                          sx={{ 
                            fontFamily: 'monospace',
                            wordBreak: 'break-all',
                            bgcolor: 'grey.100',
                            p: 1,
                            borderRadius: 1,
                          }}
                        >
                          {scanResult.rawData}
                        </Typography>
                      </CardContent>
                    </Card>
                  )}
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        
        {scanResult && (
          <DialogActions>
            <Button onClick={() => {
              setScanResult(null);
              if (!scanResult.success) {
                startScanning();
              }
            }}>
              {scanResult.success ? 'Cancel' : 'Try Again'}
            </Button>
          </DialogActions>
        )}
      </Dialog>
    </Box>
  );
};

export default QRPaymentSystemEnhanced;