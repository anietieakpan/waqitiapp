import React, { useState, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  AlertTitle,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Grid,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import DownloadIcon from '@mui/icons-material/Download';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import StopIcon from '@mui/icons-material/Stop';
import CheckIcon from '@mui/icons-material/Check';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityIcon from '@mui/icons-material/Visibility';;
import { useDropzone } from 'react-dropzone';
import { bulkTransactionService } from '../../services/bulkTransactionService';

interface BulkTransaction {
  id: string;
  recipientEmail: string;
  recipientName?: string;
  amount: number;
  currency: string;
  description: string;
  scheduledDate?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  errorMessage?: string;
  transactionId?: string;
}

interface BulkJob {
  id: string;
  name: string;
  status: 'DRAFT' | 'VALIDATING' | 'READY' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED';
  totalTransactions: number;
  completedTransactions: number;
  failedTransactions: number;
  totalAmount: number;
  createdAt: string;
  transactions: BulkTransaction[];
  validationErrors?: ValidationError[];
}

interface ValidationError {
  row: number;
  field: string;
  message: string;
  value?: string;
}

const steps = [
  'Upload File',
  'Validate Data',
  'Review & Configure',
  'Execute Transactions',
];

export const BulkTransactionProcessor: React.FC = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  
  const [activeStep, setActiveStep] = useState(0);
  const [bulkJob, setBulkJob] = useState<BulkJob | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [jobName, setJobName] = useState('');
  const [executionMode, setExecutionMode] = useState<'IMMEDIATE' | 'SCHEDULED'>('IMMEDIATE');
  const [scheduledDate, setScheduledDate] = useState('');
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [selectedTransaction, setSelectedTransaction] = useState<BulkTransaction | null>(null);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'text/csv': ['.csv'],
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
    },
    maxFiles: 1,
    onDrop: useCallback((acceptedFiles: File[]) => {
      if (acceptedFiles.length > 0) {
        setSelectedFile(acceptedFiles[0]);
        handleFileUpload(acceptedFiles[0]);
      }
    }, []),
  });

  const handleFileUpload = async (file: File) => {
    setIsProcessing(true);
    setUploadProgress(0);

    try {
      // Simulate upload progress
      const progressInterval = setInterval(() => {
        setUploadProgress(prev => {
          if (prev >= 90) {
            clearInterval(progressInterval);
            return 90;
          }
          return prev + 10;
        });
      }, 200);

      const response = await bulkTransactionService.uploadFile(file);
      
      clearInterval(progressInterval);
      setUploadProgress(100);

      if (response.success) {
        setBulkJob(response.data);
        setActiveStep(1);
        
        // Auto-validate after upload
        await validateTransactions(response.data.id);
      } else {
        throw new Error(response.errorMessage || 'Upload failed');
      }
    } catch (error) {
      console.error('Upload error:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  const validateTransactions = async (jobId: string) => {
    try {
      setIsProcessing(true);
      
      const response = await bulkTransactionService.validateJob(jobId);
      
      if (response.success) {
        setBulkJob(response.data);
        
        if (response.data.validationErrors && response.data.validationErrors.length === 0) {
          setActiveStep(2);
        }
      }
    } catch (error) {
      console.error('Validation error:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  const executeTransactions = async () => {
    if (!bulkJob) return;

    try {
      setIsProcessing(true);
      setActiveStep(3);

      const response = await bulkTransactionService.executeJob(bulkJob.id, {
        executionMode,
        scheduledDate: scheduledDate || undefined,
        jobName: jobName || undefined,
      });

      if (response.success) {
        setBulkJob(response.data);
        
        // Poll for updates during execution
        const pollInterval = setInterval(async () => {
          try {
            const statusResponse = await bulkTransactionService.getJobStatus(bulkJob.id);
            if (statusResponse.success) {
              setBulkJob(statusResponse.data);
              
              if (['COMPLETED', 'FAILED'].includes(statusResponse.data.status)) {
                clearInterval(pollInterval);
                setIsProcessing(false);
              }
            }
          } catch (error) {
            clearInterval(pollInterval);
            setIsProcessing(false);
          }
        }, 2000);
      }
    } catch (error) {
      console.error('Execution error:', error);
      setIsProcessing(false);
    }
  };

  const pauseJob = async () => {
    if (!bulkJob) return;
    
    try {
      const response = await bulkTransactionService.pauseJob(bulkJob.id);
      if (response.success) {
        setBulkJob(response.data);
      }
    } catch (error) {
      console.error('Pause error:', error);
    }
  };

  const resumeJob = async () => {
    if (!bulkJob) return;
    
    try {
      const response = await bulkTransactionService.resumeJob(bulkJob.id);
      if (response.success) {
        setBulkJob(response.data);
      }
    } catch (error) {
      console.error('Resume error:', error);
    }
  };

  const cancelJob = async () => {
    if (!bulkJob) return;
    
    try {
      const response = await bulkTransactionService.cancelJob(bulkJob.id);
      if (response.success) {
        setBulkJob(response.data);
        setIsProcessing(false);
      }
    } catch (error) {
      console.error('Cancel error:', error);
    }
  };

  const downloadTemplate = async () => {
    try {
      const response = await bulkTransactionService.downloadTemplate();
      
      // Create download link
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'bulk_transaction_template.csv');
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Template download error:', error);
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <Check color="success" />;
      case 'FAILED':
      case 'CANCELLED':
        return <Error color="error" />;
      case 'PROCESSING':
        return <LinearProgress />;
      default:
        return <Info color="info" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'FAILED':
      case 'CANCELLED':
        return 'error';
      case 'PROCESSING':
        return 'primary';
      default:
        return 'default';
    }
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const renderUploadStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Upload Transaction File
      </Typography>
      
      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Box
            {...getRootProps()}
            sx={{
              border: 2,
              borderColor: isDragActive ? 'primary.main' : 'grey.300',
              borderStyle: 'dashed',
              borderRadius: 2,
              p: 4,
              textAlign: 'center',
              cursor: 'pointer',
              backgroundColor: isDragActive ? 'action.hover' : 'background.paper',
              transition: 'all 0.2s ease',
            }}
          >
            <input {...getInputProps()} />
            <Upload sx={{ fontSize: 48, color: 'grey.400', mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              {isDragActive ? 'Drop file here' : 'Drag & drop file or click to select'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Supported formats: CSV, Excel (.xlsx)
            </Typography>
          </Box>

          {uploadProgress > 0 && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="body2" gutterBottom>
                Uploading: {uploadProgress}%
              </Typography>
              <LinearProgress variant="determinate" value={uploadProgress} />
            </Box>
          )}

          {selectedFile && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="subtitle1">Selected File:</Typography>
              <Typography variant="body2">{selectedFile.name}</Typography>
              <Typography variant="body2" color="text.secondary">
                Size: {(selectedFile.size / 1024).toFixed(2)} KB
              </Typography>
            </Box>
          )}
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Template & Guidelines
              </Typography>
              
              <Button
                variant="outlined"
                startIcon={<Download />}
                onClick={downloadTemplate}
                fullWidth
                sx={{ mb: 2 }}
              >
                Download Template
              </Button>

              <Alert severity="info" sx={{ mb: 2 }}>
                <AlertTitle>Required Fields</AlertTitle>
                <Typography variant="body2">
                  • Recipient Email<br />
                  • Amount<br />
                  • Currency<br />
                  • Description
                </Typography>
              </Alert>

              <Alert severity="warning">
                <Typography variant="body2">
                  Maximum 1,000 transactions per batch
                </Typography>
              </Alert>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );

  const renderValidationStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Data Validation Results
      </Typography>

      {bulkJob?.validationErrors && bulkJob.validationErrors.length > 0 ? (
        <Alert severity="error" sx={{ mb: 3 }}>
          <AlertTitle>Validation Errors Found</AlertTitle>
          Please fix the following errors before proceeding:
        </Alert>
      ) : (
        <Alert severity="success" sx={{ mb: 3 }}>
          <AlertTitle>Validation Successful</AlertTitle>
          All transactions are valid and ready for processing.
        </Alert>
      )}

      {bulkJob?.validationErrors && bulkJob.validationErrors.length > 0 && (
        <TableContainer component={Paper} sx={{ mb: 3 }}>
          <Table size={isMobile ? 'small' : 'medium'}>
            <TableHead>
              <TableRow>
                <TableCell>Row</TableCell>
                <TableCell>Field</TableCell>
                <TableCell>Error</TableCell>
                {!isMobile && <TableCell>Value</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {bulkJob.validationErrors.map((error, index) => (
                <TableRow key={index}>
                  <TableCell>{error.row}</TableCell>
                  <TableCell>{error.field}</TableCell>
                  <TableCell>{error.message}</TableCell>
                  {!isMobile && <TableCell>{error.value || '-'}</TableCell>}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {bulkJob && (
        <Grid container spacing={3}>
          <Grid item xs={6} sm={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Typography variant="h4" color="primary">
                  {bulkJob.totalTransactions}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Total Transactions
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={6} sm={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Typography variant="h4" color="success.main">
                  {formatCurrency(bulkJob.totalAmount)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Total Amount
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={6} sm={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Typography variant="h4" color="error.main">
                  {bulkJob.validationErrors?.length || 0}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Errors
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={6} sm={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Typography variant="h4" color="info.main">
                  {bulkJob.totalTransactions - (bulkJob.validationErrors?.length || 0)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Valid Transactions
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {bulkJob && (!bulkJob.validationErrors || bulkJob.validationErrors.length === 0) && (
        <Box sx={{ mt: 3, display: 'flex', justifyContent: 'center' }}>
          <Button
            variant="contained"
            size="large"
            onClick={() => setActiveStep(2)}
          >
            Proceed to Configuration
          </Button>
        </Box>
      )}
    </Box>
  );

  const renderConfigurationStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Configure Execution
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <TextField
            fullWidth
            label="Job Name"
            value={jobName}
            onChange={(e) => setJobName(e.target.value)}
            placeholder="Optional job name for tracking"
            sx={{ mb: 3 }}
          />

          <FormControl fullWidth sx={{ mb: 3 }}>
            <InputLabel>Execution Mode</InputLabel>
            <Select
              value={executionMode}
              onChange={(e) => setExecutionMode(e.target.value as 'IMMEDIATE' | 'SCHEDULED')}
            >
              <MenuItem value="IMMEDIATE">Execute Immediately</MenuItem>
              <MenuItem value="SCHEDULED">Schedule for Later</MenuItem>
            </Select>
          </FormControl>

          {executionMode === 'SCHEDULED' && (
            <TextField
              fullWidth
              type="datetime-local"
              label="Scheduled Date & Time"
              value={scheduledDate}
              onChange={(e) => setScheduledDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ mb: 3 }}
            />
          )}
        </Grid>

        <Grid item xs={12} md={6}>
          <Alert severity="info" sx={{ mb: 2 }}>
            <AlertTitle>Execution Summary</AlertTitle>
            <Typography variant="body2">
              • {bulkJob?.totalTransactions} transactions will be processed<br />
              • Total amount: {formatCurrency(bulkJob?.totalAmount || 0)}<br />
              • Mode: {executionMode === 'IMMEDIATE' ? 'Immediate' : 'Scheduled'}<br />
              {scheduledDate && `• Scheduled: ${new Date(scheduledDate).toLocaleString()}`}
            </Typography>
          </Alert>

          <Alert severity="warning">
            <Typography variant="body2">
              Once started, transactions cannot be modified. Failed transactions can be retried individually.
            </Typography>
          </Alert>
        </Grid>
      </Grid>

      <Box sx={{ mt: 4, display: 'flex', justifyContent: 'center', gap: 2 }}>
        <Button
          variant="outlined"
          onClick={() => setActiveStep(1)}
        >
          Back to Validation
        </Button>
        
        <Button
          variant="contained"
          size="large"
          onClick={executeTransactions}
          disabled={isProcessing}
        >
          {executionMode === 'IMMEDIATE' ? 'Start Processing' : 'Schedule Job'}
        </Button>
      </Box>
    </Box>
  );

  const renderExecutionStep = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Transaction Execution
      </Typography>

      {bulkJob && (
        <>
          {/* Progress Overview */}
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Grid container spacing={3} alignItems="center">
                <Grid item xs={12} sm={6}>
                  <Typography variant="subtitle1" gutterBottom>
                    Progress: {bulkJob.completedTransactions + bulkJob.failedTransactions} / {bulkJob.totalTransactions}
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={((bulkJob.completedTransactions + bulkJob.failedTransactions) / bulkJob.totalTransactions) * 100}
                    sx={{ height: 8, borderRadius: 4 }}
                  />
                </Grid>
                
                <Grid item xs={12} sm={6}>
                  <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center', flexWrap: 'wrap' }}>
                    {bulkJob.status === 'RUNNING' && (
                      <>
                        <Button
                          variant="outlined"
                          startIcon={<Pause />}
                          onClick={pauseJob}
                          size="small"
                        >
                          Pause
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          startIcon={<Stop />}
                          onClick={cancelJob}
                          size="small"
                        >
                          Cancel
                        </Button>
                      </>
                    )}
                    
                    {bulkJob.status === 'PAUSED' && (
                      <Button
                        variant="outlined"
                        color="primary"
                        startIcon={<PlayArrow />}
                        onClick={resumeJob}
                        size="small"
                      >
                        Resume
                      </Button>
                    )}
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* Status Cards */}
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid item xs={6} sm={3}>
              <Card>
                <CardContent sx={{ textAlign: 'center' }}>
                  <Typography variant="h4" color="success.main">
                    {bulkJob.completedTransactions}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Completed
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            
            <Grid item xs={6} sm={3}>
              <Card>
                <CardContent sx={{ textAlign: 'center' }}>
                  <Typography variant="h4" color="error.main">
                    {bulkJob.failedTransactions}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Failed
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            
            <Grid item xs={6} sm={3}>
              <Card>
                <CardContent sx={{ textAlign: 'center' }}>
                  <Typography variant="h4" color="info.main">
                    {bulkJob.totalTransactions - bulkJob.completedTransactions - bulkJob.failedTransactions}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Pending
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            
            <Grid item xs={6} sm={3}>
              <Card>
                <CardContent sx={{ textAlign: 'center' }}>
                  <Chip
                    icon={getStatusIcon(bulkJob.status)}
                    label={bulkJob.status}
                    color={getStatusColor(bulkJob.status) as any}
                  />
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    Job Status
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Transaction Details */}
          <TableContainer component={Paper}>
            <Table size={isMobile ? 'small' : 'medium'}>
              <TableHead>
                <TableRow>
                  <TableCell>Recipient</TableCell>
                  <TableCell>Amount</TableCell>
                  <TableCell>Status</TableCell>
                  {!isMobile && <TableCell>Actions</TableCell>}
                </TableRow>
              </TableHead>
              <TableBody>
                {bulkJob.transactions.slice(0, 10).map((transaction) => (
                  <TableRow key={transaction.id}>
                    <TableCell>
                      <Typography variant="body2">
                        {transaction.recipientName || transaction.recipientEmail}
                      </Typography>
                      {transaction.recipientName && (
                        <Typography variant="caption" color="text.secondary">
                          {transaction.recipientEmail}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      {formatCurrency(transaction.amount)} {transaction.currency}
                    </TableCell>
                    <TableCell>
                      <Chip
                        icon={getStatusIcon(transaction.status)}
                        label={transaction.status}
                        color={getStatusColor(transaction.status) as any}
                        size="small"
                      />
                    </TableCell>
                    {!isMobile && (
                      <TableCell>
                        <IconButton
                          size="small"
                          onClick={() => {
                            setSelectedTransaction(transaction);
                            setPreviewDialogOpen(true);
                          }}
                        >
                          <Visibility />
                        </IconButton>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Box>
  );

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Bulk Transaction Processor
      </Typography>

      <Stepper activeStep={activeStep} orientation={isMobile ? 'vertical' : 'horizontal'}>
        {steps.map((label, index) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
            {isMobile && (
              <StepContent>
                <Box sx={{ pb: 2 }}>
                  {index === 0 && renderUploadStep()}
                  {index === 1 && renderValidationStep()}
                  {index === 2 && renderConfigurationStep()}
                  {index === 3 && renderExecutionStep()}
                </Box>
              </StepContent>
            )}
          </Step>
        ))}
      </Stepper>

      {!isMobile && (
        <Box sx={{ mt: 4 }}>
          {activeStep === 0 && renderUploadStep()}
          {activeStep === 1 && renderValidationStep()}
          {activeStep === 2 && renderConfigurationStep()}
          {activeStep === 3 && renderExecutionStep()}
        </Box>
      )}

      {/* Transaction Detail Dialog */}
      <Dialog
        open={previewDialogOpen}
        onClose={() => setPreviewDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Transaction Details</DialogTitle>
        <DialogContent>
          {selectedTransaction && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Recipient: {selectedTransaction.recipientName || selectedTransaction.recipientEmail}
              </Typography>
              <Typography variant="body2" gutterBottom>
                Amount: {formatCurrency(selectedTransaction.amount)} {selectedTransaction.currency}
              </Typography>
              <Typography variant="body2" gutterBottom>
                Description: {selectedTransaction.description}
              </Typography>
              <Typography variant="body2" gutterBottom>
                Status: {selectedTransaction.status}
              </Typography>
              {selectedTransaction.errorMessage && (
                <Alert severity="error" sx={{ mt: 2 }}>
                  {selectedTransaction.errorMessage}
                </Alert>
              )}
              {selectedTransaction.transactionId && (
                <Typography variant="body2" sx={{ mt: 2 }}>
                  Transaction ID: {selectedTransaction.transactionId}
                </Typography>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};