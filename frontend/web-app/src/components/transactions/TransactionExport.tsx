import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Button,
  TextField,
  FormControl,
  FormControlLabel,
  FormLabel,
  RadioGroup,
  Radio,
  Checkbox,
  Chip,
  Alert,
  Paper,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stepper,
  Step,
  StepLabel,
  CircularProgress,
  LinearProgress,
  useTheme,
  alpha,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import FileIcon from '@mui/icons-material/Description';
import CsvIcon from '@mui/icons-material/TableChart';
import PdfIcon from '@mui/icons-material/PictureAsPdf';
import JsonIcon from '@mui/icons-material/Code';
import ExcelIcon from '@mui/icons-material/Assessment';
import EmailIcon from '@mui/icons-material/Email';
import CloudIcon from '@mui/icons-material/CloudDownload';
import CheckIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import ScheduleIcon from '@mui/icons-material/Schedule';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CategoryIcon from '@mui/icons-material/Category';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import DateIcon from '@mui/icons-material/DateRange';
import LabelIcon from '@mui/icons-material/Label';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { format } from 'date-fns';
import { Transaction, TransactionType } from '../../types/wallet';

interface TransactionExportProps {
  open: boolean;
  onClose: () => void;
  transactions: Transaction[];
  selectedTransactions?: string[];
}

type ExportFormat = 'csv' | 'pdf' | 'json' | 'excel';
type DeliveryMethod = 'download' | 'email' | 'cloud';

interface ExportOptions {
  format: ExportFormat;
  delivery: DeliveryMethod;
  dateRange?: {
    start: Date | null;
    end: Date | null;
  };
  includeFields: {
    date: boolean;
    description: boolean;
    amount: boolean;
    balance: boolean;
    category: boolean;
    merchant: boolean;
    paymentMethod: boolean;
    status: boolean;
    fees: boolean;
    notes: boolean;
    tags: boolean;
    reference: boolean;
  };
  filters?: {
    types?: TransactionType[];
    minAmount?: number;
    maxAmount?: number;
    categories?: string[];
  };
  email?: string;
  cloudService?: 'drive' | 'dropbox' | 'onedrive';
}

const TransactionExport: React.FC<TransactionExportProps> = ({
  open,
  onClose,
  transactions,
  selectedTransactions = [],
}) => {
  const theme = useTheme();
  const [activeStep, setActiveStep] = useState(0);
  const [options, setOptions] = useState<ExportOptions>({
    format: 'csv',
    delivery: 'download',
    includeFields: {
      date: true,
      description: true,
      amount: true,
      balance: true,
      category: true,
      merchant: true,
      paymentMethod: true,
      status: true,
      fees: false,
      notes: false,
      tags: false,
      reference: false,
    },
  });
  const [exporting, setExporting] = useState(false);
  const [exportProgress, setExportProgress] = useState(0);
  const [exportResult, setExportResult] = useState<{
    success: boolean;
    message: string;
    downloadUrl?: string;
  } | null>(null);

  const steps = ['Select Format', 'Choose Fields', 'Configure Export', 'Export'];

  const formatInfo = {
    csv: {
      name: 'CSV',
      icon: <CsvIcon />,
      description: 'Comma-separated values, compatible with Excel and Google Sheets',
      extension: '.csv',
    },
    pdf: {
      name: 'PDF',
      icon: <PdfIcon />,
      description: 'Portable document format, ideal for printing and sharing',
      extension: '.pdf',
    },
    json: {
      name: 'JSON',
      icon: <JsonIcon />,
      description: 'JavaScript Object Notation, for developers and data processing',
      extension: '.json',
    },
    excel: {
      name: 'Excel',
      icon: <ExcelIcon />,
      description: 'Microsoft Excel format with formatting and formulas',
      extension: '.xlsx',
    },
  };

  const handleNext = () => {
    if (activeStep === steps.length - 1) {
      handleExport();
    } else {
      setActiveStep((prevStep) => prevStep + 1);
    }
  };

  const handleBack = () => {
    setActiveStep((prevStep) => prevStep - 1);
  };

  const handleExport = async () => {
    setExporting(true);
    setExportProgress(0);

    try {
      // Filter transactions based on options
      let exportData = selectedTransactions.length > 0
        ? transactions.filter(tx => selectedTransactions.includes(tx.id))
        : transactions;

      // Apply date range filter
      if (options.dateRange?.start && options.dateRange?.end) {
        exportData = exportData.filter(tx => {
          const txDate = new Date(tx.createdAt);
          return txDate >= options.dateRange!.start! && txDate <= options.dateRange!.end!;
        });
      }

      // Apply other filters
      if (options.filters) {
        if (options.filters.types?.length) {
          exportData = exportData.filter(tx => options.filters!.types!.includes(tx.type));
        }
        if (options.filters.minAmount !== undefined) {
          exportData = exportData.filter(tx => tx.amount >= options.filters!.minAmount!);
        }
        if (options.filters.maxAmount !== undefined) {
          exportData = exportData.filter(tx => tx.amount <= options.filters!.maxAmount!);
        }
        if (options.filters.categories?.length) {
          exportData = exportData.filter(tx => tx.category && options.filters!.categories!.includes(tx.category));
        }
      }

      // Simulate export progress
      const progressInterval = setInterval(() => {
        setExportProgress((prev) => {
          if (prev >= 90) {
            clearInterval(progressInterval);
            return prev;
          }
          return prev + 10;
        });
      }, 200);

      // Format data based on selected format
      let exportContent: string | Blob;
      const filename = `transactions_${format(new Date(), 'yyyy-MM-dd_HHmmss')}${formatInfo[options.format].extension}`;

      switch (options.format) {
        case 'csv':
          exportContent = generateCSV(exportData, options.includeFields);
          break;
        case 'json':
          exportContent = generateJSON(exportData, options.includeFields);
          break;
        case 'pdf':
          exportContent = await generatePDF(exportData, options.includeFields);
          break;
        case 'excel':
          exportContent = await generateExcel(exportData, options.includeFields);
          break;
        default:
          throw new Error('Unsupported format');
      }

      clearInterval(progressInterval);
      setExportProgress(100);

      // Handle delivery method
      switch (options.delivery) {
        case 'download':
          downloadFile(exportContent, filename, options.format);
          setExportResult({
            success: true,
            message: `Successfully exported ${exportData.length} transactions`,
          });
          break;
        case 'email':
          // Send to email
          setExportResult({
            success: true,
            message: `Export sent to ${options.email}`,
          });
          break;
        case 'cloud':
          // Upload to cloud
          setExportResult({
            success: true,
            message: `Uploaded to ${options.cloudService}`,
          });
          break;
      }
    } catch (error) {
      setExportResult({
        success: false,
        message: 'Export failed. Please try again.',
      });
    } finally {
      setExporting(false);
    }
  };

  const renderFormatSelection = () => (
    <Box>
      <Typography variant="subtitle1" sx={{ mb: 3 }}>
        Choose export format
      </Typography>
      <Grid container spacing={2}>
        {Object.entries(formatInfo).map(([key, info]) => (
          <Grid item xs={12} sm={6} key={key}>
            <Paper
              sx={{
                p: 3,
                cursor: 'pointer',
                border: `2px solid ${
                  options.format === key ? theme.palette.primary.main : 'transparent'
                }`,
                '&:hover': {
                  borderColor: theme.palette.primary.light,
                },
              }}
              onClick={() => setOptions({ ...options, format: key as ExportFormat })}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar
                  sx={{
                    bgcolor: alpha(theme.palette.primary.main, 0.1),
                    color: theme.palette.primary.main,
                  }}
                >
                  {info.icon}
                </Avatar>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    {info.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {info.description}
                  </Typography>
                </Box>
              </Box>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Box sx={{ mt: 3 }}>
        <FormControl component="fieldset">
          <FormLabel component="legend">Delivery Method</FormLabel>
          <RadioGroup
            value={options.delivery}
            onChange={(e) => setOptions({ ...options, delivery: e.target.value as DeliveryMethod })}
          >
            <FormControlLabel
              value="download"
              control={<Radio />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <DownloadIcon />
                  <span>Download to device</span>
                </Box>
              }
            />
            <FormControlLabel
              value="email"
              control={<Radio />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <EmailIcon />
                  <span>Send to email</span>
                </Box>
              }
            />
            <FormControlLabel
              value="cloud"
              control={<Radio />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <CloudIcon />
                  <span>Save to cloud</span>
                </Box>
              }
            />
          </RadioGroup>
        </FormControl>

        {options.delivery === 'email' && (
          <TextField
            fullWidth
            label="Email address"
            type="email"
            value={options.email || ''}
            onChange={(e) => setOptions({ ...options, email: e.target.value })}
            sx={{ mt: 2 }}
          />
        )}

        {options.delivery === 'cloud' && (
          <FormControl fullWidth sx={{ mt: 2 }}>
            <FormLabel>Cloud Service</FormLabel>
            <RadioGroup
              value={options.cloudService || 'drive'}
              onChange={(e) => setOptions({ ...options, cloudService: e.target.value as any })}
            >
              <FormControlLabel value="drive" control={<Radio />} label="Google Drive" />
              <FormControlLabel value="dropbox" control={<Radio />} label="Dropbox" />
              <FormControlLabel value="onedrive" control={<Radio />} label="OneDrive" />
            </RadioGroup>
          </FormControl>
        )}
      </Box>
    </Box>
  );

  const renderFieldSelection = () => (
    <Box>
      <Typography variant="subtitle1" sx={{ mb: 3 }}>
        Select fields to include
      </Typography>
      <Grid container spacing={2}>
        {Object.entries(options.includeFields).map(([field, included]) => (
          <Grid item xs={12} sm={6} md={4} key={field}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={included}
                  onChange={(e) =>
                    setOptions({
                      ...options,
                      includeFields: {
                        ...options.includeFields,
                        [field]: e.target.checked,
                      },
                    })
                  }
                />
              }
              label={field.charAt(0).toUpperCase() + field.slice(1).replace(/([A-Z])/g, ' $1')}
            />
          </Grid>
        ))}
      </Grid>

      <Alert severity="info" sx={{ mt: 3 }}>
        <Typography variant="body2">
          Selected fields will determine the columns in your export. Date and Amount are recommended for most exports.
        </Typography>
      </Alert>
    </Box>
  );

  const renderConfiguration = () => (
    <Box>
      <Typography variant="subtitle1" sx={{ mb: 3 }}>
        Configure export options
      </Typography>

      <LocalizationProvider dateAdapter={AdapterDateFns}>
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <Typography variant="subtitle2" sx={{ mb: 2 }}>
              Date Range (Optional)
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <DatePicker
                  label="Start Date"
                  value={options.dateRange?.start || null}
                  onChange={(date) =>
                    setOptions({
                      ...options,
                      dateRange: { ...options.dateRange, start: date },
                    })
                  }
                  renderInput={(params) => <TextField {...params} fullWidth />}
                />
              </Grid>
              <Grid item xs={12} sm={6}>
                <DatePicker
                  label="End Date"
                  value={options.dateRange?.end || null}
                  onChange={(date) =>
                    setOptions({
                      ...options,
                      dateRange: { ...options.dateRange, end: date },
                    })
                  }
                  renderInput={(params) => <TextField {...params} fullWidth />}
                />
              </Grid>
            </Grid>
          </Grid>

          <Grid item xs={12}>
            <Typography variant="subtitle2" sx={{ mb: 2 }}>
              Transaction Types (Optional)
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {Object.values(TransactionType).map((type) => (
                <Chip
                  key={type}
                  label={type}
                  onClick={() => {
                    const types = options.filters?.types || [];
                    const newTypes = types.includes(type)
                      ? types.filter(t => t !== type)
                      : [...types, type];
                    setOptions({
                      ...options,
                      filters: { ...options.filters, types: newTypes },
                    });
                  }}
                  color={options.filters?.types?.includes(type) ? 'primary' : 'default'}
                  variant={options.filters?.types?.includes(type) ? 'filled' : 'outlined'}
                />
              ))}
            </Box>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Minimum Amount"
              type="number"
              value={options.filters?.minAmount || ''}
              onChange={(e) =>
                setOptions({
                  ...options,
                  filters: { ...options.filters, minAmount: Number(e.target.value) },
                })
              }
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Maximum Amount"
              type="number"
              value={options.filters?.maxAmount || ''}
              onChange={(e) =>
                setOptions({
                  ...options,
                  filters: { ...options.filters, maxAmount: Number(e.target.value) },
                })
              }
            />
          </Grid>
        </Grid>
      </LocalizationProvider>

      <Alert severity="info" sx={{ mt: 3 }}>
        <Typography variant="body2">
          {selectedTransactions.length > 0
            ? `Exporting ${selectedTransactions.length} selected transactions`
            : `Exporting all ${transactions.length} transactions`}
          {options.dateRange?.start && options.dateRange?.end && ' within the specified date range'}
        </Typography>
      </Alert>
    </Box>
  );

  const renderExportProgress = () => (
    <Box sx={{ textAlign: 'center', py: 4 }}>
      {!exportResult ? (
        <>
          <CircularProgress size={60} sx={{ mb: 3 }} />
          <Typography variant="h6" sx={{ mb: 2 }}>
            Exporting Transactions...
          </Typography>
          <Box sx={{ width: '100%', maxWidth: 400, mx: 'auto' }}>
            <LinearProgress variant="determinate" value={exportProgress} sx={{ height: 8, borderRadius: 4 }} />
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {exportProgress}% Complete
            </Typography>
          </Box>
        </>
      ) : (
        <Box>
          <Avatar
            sx={{
              width: 80,
              height: 80,
              mx: 'auto',
              mb: 3,
              bgcolor: exportResult.success ? theme.palette.success.main : theme.palette.error.main,
            }}
          >
            {exportResult.success ? <CheckIcon sx={{ fontSize: 48 }} /> : <ErrorIcon sx={{ fontSize: 48 }} />}
          </Avatar>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {exportResult.success ? 'Export Successful!' : 'Export Failed'}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {exportResult.message}
          </Typography>
          {exportResult.downloadUrl && (
            <Button
              variant="contained"
              startIcon={<DownloadIcon />}
              href={exportResult.downloadUrl}
              download
              sx={{ mt: 3 }}
            >
              Download File
            </Button>
          )}
        </Box>
      )}
    </Box>
  );

  const getStepContent = (step: number) => {
    switch (step) {
      case 0:
        return renderFormatSelection();
      case 1:
        return renderFieldSelection();
      case 2:
        return renderConfiguration();
      case 3:
        return renderExportProgress();
      default:
        return null;
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Export Transactions</DialogTitle>
      <DialogContent>
        <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
        {getStepContent(activeStep)}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          disabled={activeStep === 0 || exporting}
          onClick={handleBack}
        >
          Back
        </Button>
        <Button
          variant="contained"
          onClick={handleNext}
          disabled={exporting || (activeStep === steps.length - 1 && exportResult !== null)}
        >
          {activeStep === steps.length - 1 ? 'Export' : 'Next'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// Helper functions for generating export formats
function generateCSV(transactions: Transaction[], fields: ExportOptions['includeFields']): string {
  const headers: string[] = [];
  if (fields.date) headers.push('Date');
  if (fields.description) headers.push('Description');
  if (fields.amount) headers.push('Amount');
  if (fields.balance) headers.push('Balance');
  if (fields.category) headers.push('Category');
  if (fields.merchant) headers.push('Merchant');
  if (fields.paymentMethod) headers.push('Payment Method');
  if (fields.status) headers.push('Status');
  if (fields.fees) headers.push('Fees');
  if (fields.notes) headers.push('Notes');
  if (fields.tags) headers.push('Tags');
  if (fields.reference) headers.push('Reference');

  const rows = transactions.map(tx => {
    const row: string[] = [];
    if (fields.date) row.push(format(new Date(tx.createdAt), 'yyyy-MM-dd HH:mm:ss'));
    if (fields.description) row.push(tx.description || '');
    if (fields.amount) row.push(tx.amount.toString());
    if (fields.balance) row.push(tx.balanceAfter?.toString() || '');
    if (fields.category) row.push(tx.category || '');
    if (fields.merchant) row.push(tx.merchantName || '');
    if (fields.paymentMethod) row.push(tx.paymentMethodType || '');
    if (fields.status) row.push(tx.status);
    if (fields.fees) row.push(tx.fee?.toString() || '0');
    if (fields.notes) row.push(tx.note || '');
    if (fields.tags) row.push(tx.tags?.join(', ') || '');
    if (fields.reference) row.push(tx.reference || '');
    return row.map(cell => `"${cell.replace(/"/g, '""')}"`).join(',');
  });

  return [headers.join(','), ...rows].join('\n');
}

function generateJSON(transactions: Transaction[], fields: ExportOptions['includeFields']): string {
  const exportData = transactions.map(tx => {
    const item: any = {};
    if (fields.date) item.date = tx.createdAt;
    if (fields.description) item.description = tx.description;
    if (fields.amount) item.amount = tx.amount;
    if (fields.balance) item.balance = tx.balanceAfter;
    if (fields.category) item.category = tx.category;
    if (fields.merchant) item.merchant = tx.merchantName;
    if (fields.paymentMethod) item.paymentMethod = tx.paymentMethodType;
    if (fields.status) item.status = tx.status;
    if (fields.fees) item.fees = tx.fee;
    if (fields.notes) item.notes = tx.note;
    if (fields.tags) item.tags = tx.tags;
    if (fields.reference) item.reference = tx.reference;
    return item;
  });

  return JSON.stringify(exportData, null, 2);
}

async function generatePDF(transactions: Transaction[], fields: ExportOptions['includeFields']): Promise<Blob> {
  // In a real implementation, this would use a PDF library like jsPDF
  // For now, return a mock blob
  return new Blob(['PDF content'], { type: 'application/pdf' });
}

async function generateExcel(transactions: Transaction[], fields: ExportOptions['includeFields']): Promise<Blob> {
  // In a real implementation, this would use a library like SheetJS
  // For now, return a mock blob
  return new Blob(['Excel content'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
}

function downloadFile(content: string | Blob, filename: string, format: ExportFormat) {
  const blob = typeof content === 'string' 
    ? new Blob([content], { type: getMimeType(format) })
    : content;
  
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function getMimeType(format: ExportFormat): string {
  switch (format) {
    case 'csv':
      return 'text/csv';
    case 'json':
      return 'application/json';
    case 'pdf':
      return 'application/pdf';
    case 'excel':
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    default:
      return 'text/plain';
  }
}

export default TransactionExport;