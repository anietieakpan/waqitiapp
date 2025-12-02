import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  InputAdornment,
  Alert,
  Chip,
  IconButton,
  Tooltip,
  Paper,
  Divider,
  Avatar,
  Grid,
  CircularProgress,
  Fade,
  Zoom,
  ToggleButton,
  ToggleButtonGroup,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import DownloadIcon from '@mui/icons-material/Download';
import ShareIcon from '@mui/icons-material/Share';
import CopyIcon from '@mui/icons-material/ContentCopy';
import RefreshIcon from '@mui/icons-material/Refresh';
import CloseIcon from '@mui/icons-material/Close';
import CameraIcon from '@mui/icons-material/CameraAlt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SecurityIcon from '@mui/icons-material/Security';;
import { QRCodeSVG } from 'qrcode.react';
import QrScanner from './QrScanner';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { generateQrCode, scanQrCode, createQrPayment } from '../store/slices/paymentSlice';
import { formatCurrency, copyToClipboard } from '../utils/helpers';
import { motion, AnimatePresence } from 'framer-motion';

interface QrPaymentProps {
  open: boolean;
  onClose: () => void;
}

type QrMode = 'receive' | 'send';

interface QrData {
  code: string;
  amount?: number;
  note?: string;
  expiresAt: string;
  type: 'static' | 'dynamic';
  userId: string;
  username: string;
}

const QrPayment: React.FC<QrPaymentProps> = ({ open, onClose }) => {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { qrCode, loading } = useAppSelector((state) => state.payment);

  const [mode, setMode] = useState<QrMode>('receive');
  const [showScanner, setShowScanner] = useState(false);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [qrType, setQrType] = useState<'static' | 'dynamic'>('static');
  const [expiresIn, setExpiresIn] = useState('5'); // minutes
  const [scannedData, setScannedData] = useState<QrData | null>(null);
  const [paymentPin, setPaymentPin] = useState('');
  const [showPinDialog, setShowPinDialog] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [copied, setCopied] = useState(false);

  const qrRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open && mode === 'receive' && !qrCode) {
      generateNewQr();
    }
  }, [open, mode]);

  const generateNewQr = async () => {
    try {
      const data = {
        type: qrType,
        amount: qrType === 'dynamic' && amount ? parseFloat(amount) : undefined,
        note: note || undefined,
        expiresIn: qrType === 'dynamic' ? parseInt(expiresIn) * 60 : undefined, // Convert to seconds
      };

      await dispatch(generateQrCode(data)).unwrap();
      setSuccess('QR code generated successfully!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (error: any) {
      setError(error.message || 'Failed to generate QR code');
    }
  };

  const handleScanComplete = async (data: string) => {
    try {
      const parsed = JSON.parse(data) as QrData;
      setScannedData(parsed);
      setShowScanner(false);

      // If it's a dynamic QR with amount, show PIN dialog
      if (parsed.amount) {
        setShowPinDialog(true);
      } else {
        // For static QR, navigate to send money with recipient pre-filled
        // This would typically navigate to the send money page
        window.location.href = `/send?to=${parsed.userId}`;
      }
    } catch (error) {
      setError('Invalid QR code');
      setShowScanner(false);
    }
  };

  const handlePayment = async () => {
    if (!scannedData || !paymentPin) return;

    try {
      await dispatch(
        createQrPayment({
          qrCode: scannedData.code,
          amount: scannedData.amount || parseFloat(amount),
          pin: paymentPin,
          note: scannedData.note || note,
        })
      ).unwrap();

      setSuccess('Payment successful!');
      setShowPinDialog(false);
      setScannedData(null);
      setPaymentPin('');
      setTimeout(() => {
        setSuccess('');
        onClose();
      }, 2000);
    } catch (error: any) {
      setError(error.message || 'Payment failed');
    }
  };

  const downloadQr = () => {
    if (!qrRef.current) return;

    const svg = qrRef.current.querySelector('svg');
    if (!svg) return;

    const svgData = new XMLSerializer().serializeToString(svg);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    const img = new Image();

    img.onload = () => {
      canvas.width = img.width;
      canvas.height = img.height;
      ctx?.drawImage(img, 0, 0);
      const pngFile = canvas.toDataURL('image/png');
      
      const downloadLink = document.createElement('a');
      downloadLink.download = `waqiti-qr-${Date.now()}.png`;
      downloadLink.href = pngFile;
      downloadLink.click();
    };

    img.src = 'data:image/svg+xml;base64,' + btoa(svgData);
  };

  const shareQr = async () => {
    if (!qrCode) return;

    const shareData = {
      title: 'Waqiti Payment QR',
      text: `Pay me on Waqiti${amount ? ` - ${formatCurrency(parseFloat(amount))}` : ''}`,
      url: `https://waqiti.com/pay/${qrCode.code}`,
    };

    try {
      if (navigator.share) {
        await navigator.share(shareData);
      } else {
        copyToClipboard(shareData.url);
        setCopied(true);
        setTimeout(() => setCopied(false), 3000);
      }
    } catch (error) {
      console.error('Error sharing:', error);
    }
  };

  const renderReceiveMode = () => (
    <Box>
      <ToggleButtonGroup
        value={qrType}
        exclusive
        onChange={(_, value) => value && setQrType(value)}
        fullWidth
        sx={{ mb: 3 }}
      >
        <ToggleButton value="static">
          <Box textAlign="center">
            <Typography variant="body2">Static QR</Typography>
            <Typography variant="caption" color="text.secondary">
              Reusable code
            </Typography>
          </Box>
        </ToggleButton>
        <ToggleButton value="dynamic">
          <Box textAlign="center">
            <Typography variant="body2">Dynamic QR</Typography>
            <Typography variant="caption" color="text.secondary">
              Fixed amount
            </Typography>
          </Box>
        </ToggleButton>
      </ToggleButtonGroup>

      {qrType === 'dynamic' && (
        <>
          <TextField
            fullWidth
            label="Amount"
            value={amount}
            onChange={(e) => {
              const value = e.target.value.replace(/[^0-9.]/g, '');
              setAmount(value);
            }}
            InputProps={{
              startAdornment: <InputAdornment position="start">$</InputAdornment>,
            }}
            sx={{ mb: 2 }}
          />

          <TextField
            fullWidth
            label="Note (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            multiline
            rows={2}
            sx={{ mb: 2 }}
          />

          <FormControl fullWidth sx={{ mb: 3 }}>
            <InputLabel>Expires in</InputLabel>
            <Select
              value={expiresIn}
              onChange={(e) => setExpiresIn(e.target.value)}
              label="Expires in"
            >
              <MenuItem value="5">5 minutes</MenuItem>
              <MenuItem value="10">10 minutes</MenuItem>
              <MenuItem value="15">15 minutes</MenuItem>
              <MenuItem value="30">30 minutes</MenuItem>
              <MenuItem value="60">1 hour</MenuItem>
            </Select>
          </FormControl>
        </>
      )}

      <Button
        fullWidth
        variant="contained"
        onClick={generateNewQr}
        disabled={loading || (qrType === 'dynamic' && !amount)}
        startIcon={loading ? <CircularProgress size={20} /> : <RefreshIcon />}
        sx={{ mb: 3 }}
      >
        {qrCode ? 'Generate New QR' : 'Generate QR Code'}
      </Button>

      {qrCode && (
        <Zoom in={!!qrCode}>
          <Card variant="outlined">
            <CardContent>
              <Box textAlign="center">
                <Paper
                  ref={qrRef}
                  variant="outlined"
                  sx={{ p: 2, display: 'inline-block', mb: 2 }}
                >
                  <QRCodeSVG
                    value={JSON.stringify({
                      code: qrCode.code,
                      userId: user?.id,
                      username: user?.username,
                      amount: qrCode.amount,
                      note: qrCode.note,
                      type: qrCode.type,
                      expiresAt: qrCode.expiresAt,
                    })}
                    size={256}
                    level="H"
                    includeMargin
                  />
                </Paper>

                {qrCode.amount && (
                  <Typography variant="h5" gutterBottom>
                    {formatCurrency(qrCode.amount)}
                  </Typography>
                )}

                {qrCode.note && (
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {qrCode.note}
                  </Typography>
                )}

                {qrCode.type === 'dynamic' && (
                  <Chip
                    icon={<ScheduleIcon />}
                    label={`Expires ${new Date(qrCode.expiresAt).toLocaleString()}`}
                    size="small"
                    color="warning"
                    sx={{ mb: 2 }}
                  />
                )}

                <Grid container spacing={1}>
                  <Grid item xs={4}>
                    <Tooltip title="Download QR">
                      <IconButton onClick={downloadQr}>
                        <DownloadIcon />
                      </IconButton>
                    </Tooltip>
                  </Grid>
                  <Grid item xs={4}>
                    <Tooltip title="Share">
                      <IconButton onClick={shareQr}>
                        <ShareIcon />
                      </IconButton>
                    </Tooltip>
                  </Grid>
                  <Grid item xs={4}>
                    <Tooltip title={copied ? 'Copied!' : 'Copy link'}>
                      <IconButton
                        onClick={() => {
                          copyToClipboard(`https://waqiti.com/pay/${qrCode.code}`);
                          setCopied(true);
                          setTimeout(() => setCopied(false), 3000);
                        }}
                      >
                        {copied ? <CheckCircleIcon color="success" /> : <CopyIcon />}
                      </IconButton>
                    </Tooltip>
                  </Grid>
                </Grid>
              </Box>
            </CardContent>
          </Card>
        </Zoom>
      )}
    </Box>
  );

  const renderSendMode = () => (
    <Box>
      <Alert severity="info" sx={{ mb: 3 }}>
        Scan a QR code to send money instantly
      </Alert>

      {!showScanner && !scannedData && (
        <Button
          fullWidth
          variant="contained"
          size="large"
          startIcon={<CameraIcon />}
          onClick={() => setShowScanner(true)}
        >
          Scan QR Code
        </Button>
      )}

      {scannedData && !showScanner && (
        <Card variant="outlined">
          <CardContent>
            <Box display="flex" alignItems="center" gap={2} mb={2}>
              <Avatar>{scannedData.username[0].toUpperCase()}</Avatar>
              <Box flex={1}>
                <Typography variant="h6">@{scannedData.username}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {scannedData.type === 'dynamic' ? 'Payment Request' : 'Waqiti User'}
                </Typography>
              </Box>
            </Box>

            {scannedData.amount && (
              <>
                <Divider sx={{ my: 2 }} />
                <Typography variant="h4" align="center" gutterBottom>
                  {formatCurrency(scannedData.amount)}
                </Typography>
              </>
            )}

            {scannedData.note && (
              <Typography variant="body2" color="text.secondary" align="center" gutterBottom>
                {scannedData.note}
              </Typography>
            )}

            {!scannedData.amount && (
              <TextField
                fullWidth
                label="Amount to send"
                value={amount}
                onChange={(e) => {
                  const value = e.target.value.replace(/[^0-9.]/g, '');
                  setAmount(value);
                }}
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
                sx={{ my: 2 }}
              />
            )}

            <Box display="flex" gap={2}>
              <Button
                fullWidth
                variant="outlined"
                onClick={() => {
                  setScannedData(null);
                  setAmount('');
                }}
              >
                Cancel
              </Button>
              <Button
                fullWidth
                variant="contained"
                onClick={() => setShowPinDialog(true)}
                disabled={!scannedData.amount && !amount}
              >
                Pay Now
              </Button>
            </Box>
          </CardContent>
        </Card>
      )}
    </Box>
  );

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box display="flex" alignItems="center" gap={1}>
              <QrCodeIcon />
              <Typography variant="h6">QR Payment</Typography>
            </Box>
            <IconButton onClick={onClose} size="small">
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <ToggleButtonGroup
            value={mode}
            exclusive
            onChange={(_, value) => value && setMode(value)}
            fullWidth
            sx={{ mb: 3 }}
          >
            <ToggleButton value="receive">
              <Box>
                <Typography variant="body2">Receive</Typography>
              </Box>
            </ToggleButton>
            <ToggleButton value="send">
              <Box>
                <Typography variant="body2">Send</Typography>
              </Box>
            </ToggleButton>
          </ToggleButtonGroup>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>
              {error}
            </Alert>
          )}

          {success && (
            <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess('')}>
              {success}
            </Alert>
          )}

          <AnimatePresence mode="wait">
            <motion.div
              key={mode}
              initial={{ opacity: 0, x: mode === 'receive' ? -20 : 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: mode === 'receive' ? 20 : -20 }}
              transition={{ duration: 0.2 }}
            >
              {mode === 'receive' ? renderReceiveMode() : renderSendMode()}
            </motion.div>
          </AnimatePresence>
        </DialogContent>
      </Dialog>

      {/* QR Scanner Dialog */}
      <Dialog open={showScanner} onClose={() => setShowScanner(false)} fullScreen>
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="h6">Scan QR Code</Typography>
            <IconButton onClick={() => setShowScanner(false)}>
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <QrScanner onScan={handleScanComplete} />
        </DialogContent>
      </Dialog>

      {/* PIN Confirmation Dialog */}
      <Dialog open={showPinDialog} onClose={() => setShowPinDialog(false)}>
        <DialogTitle>
          <Box display="flex" alignItems="center" gap={1}>
            <SecurityIcon color="primary" />
            <Typography variant="h6">Confirm Payment</Typography>
          </Box>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" paragraph>
            Enter your 4-digit PIN to confirm this payment
          </Typography>

          {scannedData && (
            <Card variant="outlined" sx={{ mb: 2, bgcolor: 'background.default' }}>
              <CardContent sx={{ py: 1.5 }}>
                <Typography variant="h5" align="center">
                  {formatCurrency(scannedData.amount || parseFloat(amount))}
                </Typography>
                <Typography variant="body2" color="text.secondary" align="center">
                  to @{scannedData.username}
                </Typography>
              </CardContent>
            </Card>
          )}

          <TextField
            fullWidth
            type="password"
            label="Transaction PIN"
            value={paymentPin}
            onChange={(e) => {
              const value = e.target.value.replace(/[^0-9]/g, '').slice(0, 4);
              setPaymentPin(value);
            }}
            inputProps={{
              maxLength: 4,
              inputMode: 'numeric',
              pattern: '[0-9]*',
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowPinDialog(false)}>Cancel</Button>
          <Button
            onClick={handlePayment}
            variant="contained"
            disabled={paymentPin.length !== 4 || loading}
          >
            {loading ? <CircularProgress size={24} /> : 'Confirm'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default QrPayment;