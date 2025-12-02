import React, { useState, useRef, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  CircularProgress,
  Alert,
  IconButton,
  Paper,
  List,
  ListItem,
  ListItemText,
  TextField,
  Chip,
  Divider,
} from '@mui/material';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import CloseIcon from '@mui/icons-material/Close';
import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';
import ReceiptIcon from '@mui/icons-material/Receipt';;
import { useDropzone } from 'react-dropzone';
import { formatCurrency } from '../../utils/formatters';
import OCRService, { OCRResult, ReceiptData as OCRReceiptData, ImageQualityCheck } from '../../services/OCRService';

interface ReceiptItem {
  name: string;
  quantity: number;
  price: number;
  total: number;
}

interface ReceiptData {
  merchantName?: string;
  merchantAddress?: string;
  date?: Date;
  items: ReceiptItem[];
  subtotal?: number;
  tax?: number;
  tip?: number;
  total?: number;
  confidence?: number;
  currency?: string;
  paymentMethod?: string;
}

interface ReceiptScannerProps {
  open: boolean;
  onClose: () => void;
  onScan: (data: ReceiptData) => void;
}

/**
 * Receipt Scanner Component - OCR scanning for receipts
 */
const ReceiptScanner: React.FC<ReceiptScannerProps> = ({ open, onClose, onScan }) => {
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receiptData, setReceiptData] = useState<ReceiptData | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [qualityCheck, setQualityCheck] = useState<ImageQualityCheck | null>(null);
  const [processingTime, setProcessingTime] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'image/*': ['.jpeg', '.jpg', '.png', '.gif', '.bmp', '.webp']
    },
    multiple: false,
    onDrop: handleFileDrop,
  });

  async function handleFileDrop(acceptedFiles: File[]) {
    if (acceptedFiles.length === 0) return;

    const file = acceptedFiles[0];
    const reader = new FileReader();
    
    reader.onload = (e) => {
      setImagePreview(e.target?.result as string);
    };
    
    reader.readAsDataURL(file);
    await scanReceipt(file);
  }

  const handleCameraCapture = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      setImagePreview(e.target?.result as string);
    };
    reader.readAsDataURL(file);
    
    await scanReceipt(file);
  };

  const scanReceipt = async (file: File) => {
    setScanning(true);
    setError(null);
    setQualityCheck(null);
    
    try {
      // First, check image quality
      const qualityResult = await OCRService.checkImageQuality(file);
      setQualityCheck(qualityResult);
      
      if (!qualityResult.acceptable && qualityResult.score < 0.5) {
        setError(`Image quality issues detected: ${qualityResult.issues.join(', ')}. ${qualityResult.suggestions[0] || 'Please try again with a better image.'}`);
        return;
      }
      
      // Process the receipt with OCR
      const ocrResult: OCRResult = await OCRService.processReceiptImage(file, {
        documentType: 'receipt',
        enhanceImage: true,
        extractStructuredData: true,
        validateData: true,
      });
      
      setProcessingTime(ocrResult.processingTimeMs);
      
      if (!ocrResult.success || ocrResult.confidence < 0.3) {
        setError('Failed to extract receipt data. The image may be unclear or not a valid receipt.');
        return;
      }
      
      const ocrData = ocrResult.extractedData as OCRReceiptData;
      
      // Convert OCR data to component format
      const receiptData: ReceiptData = {
        merchantName: ocrData.merchantName,
        merchantAddress: ocrData.merchantAddress,
        date: ocrData.date ? new Date(ocrData.date) : undefined,
        items: ocrData.items.map(item => ({
          name: item.name,
          quantity: item.quantity,
          price: item.unitPrice,
          total: item.totalPrice,
        })),
        subtotal: ocrData.subtotal,
        tax: ocrData.tax,
        tip: ocrData.tip,
        total: ocrData.total,
        confidence: ocrResult.confidence,
        currency: ocrData.currency,
        paymentMethod: ocrData.paymentMethod,
      };
      
      setReceiptData(receiptData);
      
      // If confidence is low, suggest edit mode
      if (ocrResult.confidence < 0.7) {
        setEditMode(true);
      }
      
    } catch (err: any) {
      console.error('OCR processing failed:', err);
      setError(err.message || 'Failed to scan receipt. Please try again.');
    } finally {
      setScanning(false);
    }
  };

  const handleEditItem = (index: number, field: keyof ReceiptItem, value: string | number) => {
    if (!receiptData) return;
    
    const newItems = [...receiptData.items];
    newItems[index] = {
      ...newItems[index],
      [field]: field === 'name' ? value : parseFloat(value as string) || 0,
    };
    
    // Recalculate totals
    newItems[index].total = newItems[index].quantity * newItems[index].price;
    
    const subtotal = newItems.reduce((sum, item) => sum + item.total, 0);
    const tax = receiptData.tax || 0;
    const tip = receiptData.tip || 0;
    const total = subtotal + tax + tip;
    
    setReceiptData({
      ...receiptData,
      items: newItems,
      subtotal,
      total,
    });
  };

  const handleEditTotal = (field: 'tax' | 'tip', value: string) => {
    if (!receiptData) return;
    
    const numValue = parseFloat(value) || 0;
    const subtotal = receiptData.subtotal || 0;
    const tax = field === 'tax' ? numValue : receiptData.tax || 0;
    const tip = field === 'tip' ? numValue : receiptData.tip || 0;
    const total = subtotal + tax + tip;
    
    setReceiptData({
      ...receiptData,
      [field]: numValue,
      total,
    });
  };

  const handleConfirm = () => {
    if (receiptData) {
      onScan(receiptData);
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Typography variant="h6">Scan Receipt</Typography>
          <IconButton onClick={onClose} size="small">
            <Close />
          </IconButton>
        </Box>
      </DialogTitle>
      
      <DialogContent>
        {!imagePreview && !scanning && (
          <Box>
            <Paper
              {...getRootProps()}
              sx={{
                p: 4,
                border: '2px dashed',
                borderColor: isDragActive ? 'primary.main' : 'divider',
                bgcolor: isDragActive ? 'action.hover' : 'background.paper',
                cursor: 'pointer',
                textAlign: 'center',
                mb: 2,
              }}
            >
              <input {...getInputProps()} />
              <CloudUpload sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
              <Typography variant="h6" gutterBottom>
                Drop receipt image here
              </Typography>
              <Typography variant="body2" color="text.secondary">
                or click to select from your device
              </Typography>
            </Paper>
            
            <Button
              variant="outlined"
              startIcon={<CameraAlt />}
              onClick={handleCameraCapture}
              fullWidth
            >
              Take Photo
            </Button>
            
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              capture="environment"
              onChange={handleFileChange}
              style={{ display: 'none' }}
            />
          </Box>
        )}
        
        {scanning && (
          <Box display="flex" flexDirection="column" alignItems="center" py={4}>
            <CircularProgress size={60} sx={{ mb: 3 }} />
            <Typography variant="h6" gutterBottom>
              Scanning Receipt...
            </Typography>
            <Typography variant="body2" color="text.secondary">
              This may take a few seconds
            </Typography>
          </Box>
        )}
        
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        
        {qualityCheck && !qualityCheck.acceptable && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Image Quality Issues:
              </Typography>
              {qualityCheck.issues.map((issue, index) => (
                <Typography key={index} variant="body2">
                  • {issue}
                </Typography>
              ))}
              {qualityCheck.suggestions.length > 0 && (
                <Box mt={1}>
                  <Typography variant="caption" fontWeight="bold">
                    Suggestions:
                  </Typography>
                  {qualityCheck.suggestions.map((suggestion, index) => (
                    <Typography key={index} variant="caption" display="block">
                      • {suggestion}
                    </Typography>
                  ))}
                </Box>
              )}
            </Box>
          </Alert>
        )}
        
        {receiptData && !scanning && (
          <Box>
            {imagePreview && (
              <Box mb={2} sx={{ maxHeight: 200, overflow: 'hidden' }}>
                <img
                  src={imagePreview}
                  alt="Receipt"
                  style={{ width: '100%', height: 'auto' }}
                />
              </Box>
            )}
            
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6">Scanned Items</Typography>
              <Box display="flex" alignItems="center" gap={1}>
                {processingTime && (
                  <Chip
                    label={`${processingTime}ms`}
                    size="small"
                    color="info"
                    variant="outlined"
                  />
                )}
                {receiptData.confidence && (
                  <Chip
                    label={`${Math.round(receiptData.confidence * 100)}% confidence`}
                    size="small"
                    color={receiptData.confidence > 0.8 ? 'success' : receiptData.confidence > 0.6 ? 'warning' : 'error'}
                    sx={{ mr: 1 }}
                  />
                )}
                <IconButton
                  onClick={() => setEditMode(!editMode)}
                  color={editMode ? 'primary' : 'default'}
                >
                  {editMode ? <Check /> : <Edit />}
                </IconButton>
              </Box>
            </Box>
            
            {receiptData.merchantName && (
              <Box mb={2}>
                <Typography variant="subtitle2" color="text.secondary">
                  Merchant
                </Typography>
                <Typography variant="body1">
                  {receiptData.merchantName}
                </Typography>
                {receiptData.merchantAddress && (
                  <Typography variant="body2" color="text.secondary">
                    {receiptData.merchantAddress}
                  </Typography>
                )}
                {receiptData.paymentMethod && (
                  <Typography variant="caption" color="text.secondary">
                    Payment: {receiptData.paymentMethod}
                  </Typography>
                )}
              </Box>
            )}
            
            <List dense>
              {receiptData.items.map((item, index) => (
                <ListItem key={index}>
                  {editMode ? (
                    <Box display="flex" gap={1} width="100%">
                      <TextField
                        size="small"
                        value={item.name}
                        onChange={(e) => handleEditItem(index, 'name', e.target.value)}
                        sx={{ flex: 2 }}
                      />
                      <TextField
                        size="small"
                        type="number"
                        value={item.quantity}
                        onChange={(e) => handleEditItem(index, 'quantity', e.target.value)}
                        sx={{ width: 60 }}
                      />
                      <TextField
                        size="small"
                        type="number"
                        value={item.price}
                        onChange={(e) => handleEditItem(index, 'price', e.target.value)}
                        sx={{ width: 80 }}
                        InputProps={{ startAdornment: '$' }}
                      />
                    </Box>
                  ) : (
                    <ListItemText
                      primary={`${item.quantity}x ${item.name}`}
                      secondary={`$${item.price.toFixed(2)} each`}
                      secondaryTypographyProps={{ component: 'div' }}
                    />
                  )}
                  <Typography variant="body2" sx={{ minWidth: 70, textAlign: 'right' }}>
                    {formatCurrency(item.total)}
                  </Typography>
                </ListItem>
              ))}
            </List>
            
            <Divider sx={{ my: 2 }} />
            
            <Box>
              <Box display="flex" justifyContent="space-between" mb={1}>
                <Typography>Subtotal</Typography>
                <Typography>{formatCurrency(receiptData.subtotal || 0)}</Typography>
              </Box>
              
              <Box display="flex" justifyContent="space-between" mb={1}>
                <Typography>Tax</Typography>
                {editMode ? (
                  <TextField
                    size="small"
                    type="number"
                    value={receiptData.tax || 0}
                    onChange={(e) => handleEditTotal('tax', e.target.value)}
                    sx={{ width: 100 }}
                    InputProps={{ startAdornment: '$' }}
                  />
                ) : (
                  <Typography>{formatCurrency(receiptData.tax || 0)}</Typography>
                )}
              </Box>
              
              <Box display="flex" justifyContent="space-between" mb={1}>
                <Typography>Tip</Typography>
                {editMode ? (
                  <TextField
                    size="small"
                    type="number"
                    value={receiptData.tip || 0}
                    onChange={(e) => handleEditTotal('tip', e.target.value)}
                    sx={{ width: 100 }}
                    InputProps={{ startAdornment: '$' }}
                  />
                ) : (
                  <Typography>{formatCurrency(receiptData.tip || 0)}</Typography>
                )}
              </Box>
              
              <Divider sx={{ my: 1 }} />
              
              <Box display="flex" justifyContent="space-between">
                <Typography variant="h6">Total</Typography>
                <Typography variant="h6">
                  {formatCurrency(receiptData.total || 0)}
                </Typography>
              </Box>
            </Box>
          </Box>
        )}
      </DialogContent>
      
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleConfirm}
          disabled={!receiptData || scanning}
        >
          Use This Receipt
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ReceiptScanner;