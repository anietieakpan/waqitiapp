import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  LinearProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  TextField,
  FormControlLabel,
  Switch,
  Alert,
  IconButton,
  Tooltip,
  Divider,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
} from '@mui/material';
import {
  Download,
  CloudDownload,
  Delete,
  Refresh,
  Schedule,
  Storage,
  Security,
  Description,
  TableChart,
  PictureAsPdf,
  GetApp,
  Cancel,
  CheckCircle,
  Error,
  Warning,
  Settings,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { exportService } from '../../services/exportService';

interface ExportJob {
  id: string;
  name: string;
  type: 'FULL_BACKUP' | 'USER_DATA' | 'TRANSACTION_DATA' | 'COMPLIANCE_DATA' | 'CUSTOM';
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  format: 'CSV' | 'JSON' | 'XML' | 'PDF' | 'BACKUP';
  fileSize?: number;
  progress: number;
  createdAt: string;
  completedAt?: string;
  downloadUrl?: string;
  expiresAt?: string;
  errorMessage?: string;
  recordCount?: number;
  compression: boolean;
  encryption: boolean;
  includeMetadata: boolean;
  filters?: ExportFilter[];
}

interface ExportFilter {
  field: string;
  operator: string;
  value: any;
}

interface ExportTemplate {
  id: string;
  name: string;
  description: string;
  type: string;
  format: string;
  filters: ExportFilter[];
  schedule?: ScheduleConfig;
  retention: number; // days
  compression: boolean;
  encryption: boolean;
}

interface ScheduleConfig {
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY';
  dayOfWeek?: number;
  dayOfMonth?: number;
  time: string;
  timezone: string;
}

export const DataExportManager: React.FC = () => {
  const [exportJobs, setExportJobs] = useState<ExportJob[]>([]);
  const [exportTemplates, setExportTemplates] = useState<ExportTemplate[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [selectedJob, setSelectedJob] = useState<ExportJob | null>(null);
  
  const [newExport, setNewExport] = useState({
    name: '',
    type: 'USER_DATA' as const,
    format: 'CSV' as const,
    startDate: null as Date | null,
    endDate: null as Date | null,
    compression: true,
    encryption: true,
    includeMetadata: false,
    filters: [] as ExportFilter[],
  });

  const exportTypes = [
    { value: 'FULL_BACKUP', label: 'Full System Backup', description: 'Complete system data backup' },
    { value: 'USER_DATA', label: 'User Data', description: 'User profiles and settings' },
    { value: 'TRANSACTION_DATA', label: 'Transaction Data', description: 'Payment and transaction records' },
    { value: 'COMPLIANCE_DATA', label: 'Compliance Data', description: 'KYC, AML, and audit data' },
    { value: 'CUSTOM', label: 'Custom Export', description: 'Custom data selection' },
  ];

  const formatOptions = [
    { value: 'CSV', label: 'CSV', description: 'Comma-separated values' },
    { value: 'JSON', label: 'JSON', description: 'JavaScript Object Notation' },
    { value: 'XML', label: 'XML', description: 'Extensible Markup Language' },
    { value: 'PDF', label: 'PDF', description: 'Portable Document Format' },
    { value: 'BACKUP', label: 'Binary Backup', description: 'Compressed binary format' },
  ];

  useEffect(() => {
    loadExportJobs();
    loadExportTemplates();
    
    // Set up polling for active jobs
    const interval = setInterval(() => {
      loadExportJobs();
    }, 5000);
    
    return () => clearInterval(interval);
  }, []);

  const loadExportJobs = async () => {
    try {
      const response = await exportService.getExportJobs();
      if (response.success) {
        setExportJobs(response.data);
      }
    } catch (error) {
      console.error('Failed to load export jobs:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const loadExportTemplates = async () => {
    try {
      const response = await exportService.getExportTemplates();
      if (response.success) {
        setExportTemplates(response.data);
      }
    } catch (error) {
      console.error('Failed to load export templates:', error);
    }
  };

  const createExportJob = async () => {
    try {
      const response = await exportService.createExportJob({
        name: newExport.name,
        type: newExport.type,
        format: newExport.format,
        dateRange: newExport.startDate && newExport.endDate ? {
          startDate: newExport.startDate,
          endDate: newExport.endDate,
        } : undefined,
        options: {
          compression: newExport.compression,
          encryption: newExport.encryption,
          includeMetadata: newExport.includeMetadata,
        },
        filters: newExport.filters,
      });

      if (response.success) {
        setCreateDialogOpen(false);
        loadExportJobs();
        // Reset form
        setNewExport({
          name: '',
          type: 'USER_DATA',
          format: 'CSV',
          startDate: null,
          endDate: null,
          compression: true,
          encryption: true,
          includeMetadata: false,
          filters: [],
        });
      }
    } catch (error) {
      console.error('Failed to create export job:', error);
    }
  };

  const cancelExportJob = async (jobId: string) => {
    try {
      const response = await exportService.cancelExportJob(jobId);
      if (response.success) {
        loadExportJobs();
      }
    } catch (error) {
      console.error('Failed to cancel export job:', error);
    }
  };

  const deleteExportJob = async (jobId: string) => {
    try {
      const response = await exportService.deleteExportJob(jobId);
      if (response.success) {
        loadExportJobs();
      }
    } catch (error) {
      console.error('Failed to delete export job:', error);
    }
  };

  const downloadExport = async (job: ExportJob) => {
    if (!job.downloadUrl) return;

    try {
      const response = await exportService.downloadExport(job.id);
      
      // Create download link
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${job.name}.${job.format.toLowerCase()}`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to download export:', error);
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle color="success" />;
      case 'FAILED':
        return <Error color="error" />;
      case 'CANCELLED':
        return <Cancel color="disabled" />;
      case 'IN_PROGRESS':
        return <Download color="primary" />;
      default:
        return <Schedule color="info" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'FAILED':
        return 'error';
      case 'CANCELLED':
        return 'default';
      case 'IN_PROGRESS':
        return 'primary';
      default:
        return 'info';
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const formatDuration = (startDate: string, endDate?: string): string => {
    const start = new Date(startDate);
    const end = endDate ? new Date(endDate) : new Date();
    const duration = end.getTime() - start.getTime();
    
    const hours = Math.floor(duration / (1000 * 60 * 60));
    const minutes = Math.floor((duration % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((duration % (1000 * 60)) / 1000);
    
    if (hours > 0) {
      return `${hours}h ${minutes}m ${seconds}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    } else {
      return `${seconds}s`;
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Data Export Manager</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={loadExportJobs}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<Settings />}
            onClick={() => setTemplateDialogOpen(true)}
          >
            Templates
          </Button>
          <Button
            variant="contained"
            startIcon={<Download />}
            onClick={() => setCreateDialogOpen(true)}
          >
            New Export
          </Button>
        </Box>
      </Box>

      {/* Export Jobs */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Export Jobs
          </Typography>
          
          {isLoading ? (
            <LinearProgress />
          ) : (
            <TableContainer component={Paper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Format</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Progress</TableCell>
                    <TableCell>Size</TableCell>
                    <TableCell>Duration</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {exportJobs.map((job) => (
                    <TableRow key={job.id}>
                      <TableCell>
                        <Typography variant="body2" fontWeight="medium">
                          {job.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Created: {new Date(job.createdAt).toLocaleDateString()}
                        </Typography>
                      </TableCell>
                      
                      <TableCell>
                        <Chip
                          label={job.type.replace('_', ' ')}
                          size="small"
                          variant="outlined"
                        />
                      </TableCell>
                      
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Chip label={job.format} size="small" />
                          {job.compression && <Storage fontSize="small" />}
                          {job.encryption && <Security fontSize="small" />}
                        </Box>
                      </TableCell>
                      
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          {getStatusIcon(job.status)}
                          <Chip
                            label={job.status.replace('_', ' ')}
                            size="small"
                            color={getStatusColor(job.status) as any}
                          />
                        </Box>
                      </TableCell>
                      
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Box sx={{ width: 100 }}>
                            <LinearProgress
                              variant="determinate"
                              value={job.progress}
                            />
                          </Box>
                          <Typography variant="caption">
                            {job.progress}%
                          </Typography>
                        </Box>
                      </TableCell>
                      
                      <TableCell>
                        {job.fileSize ? formatFileSize(job.fileSize) : '-'}
                        {job.recordCount && (
                          <Typography variant="caption" display="block" color="text.secondary">
                            {job.recordCount.toLocaleString()} records
                          </Typography>
                        )}
                      </TableCell>
                      
                      <TableCell>
                        <Typography variant="body2">
                          {formatDuration(job.createdAt, job.completedAt)}
                        </Typography>
                        {job.expiresAt && (
                          <Typography variant="caption" color="warning.main" display="block">
                            Expires: {new Date(job.expiresAt).toLocaleDateString()}
                          </Typography>
                        )}
                      </TableCell>
                      
                      <TableCell>
                        <Box sx={{ display: 'flex', gap: 0.5 }}>
                          {job.status === 'COMPLETED' && job.downloadUrl && (
                            <Tooltip title="Download">
                              <IconButton
                                size="small"
                                onClick={() => downloadExport(job)}
                              >
                                <CloudDownload />
                              </IconButton>
                            </Tooltip>
                          )}
                          
                          {job.status === 'IN_PROGRESS' && (
                            <Tooltip title="Cancel">
                              <IconButton
                                size="small"
                                color="warning"
                                onClick={() => cancelExportJob(job.id)}
                              >
                                <Cancel />
                              </IconButton>
                            </Tooltip>
                          )}
                          
                          {['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status) && (
                            <Tooltip title="Delete">
                              <IconButton
                                size="small"
                                color="error"
                                onClick={() => deleteExportJob(job.id)}
                              >
                                <Delete />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                  
                  {exportJobs.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={8} align="center">
                        <Typography color="text.secondary">
                          No export jobs found
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      {/* Export Statistics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Typography variant="h4" color="primary">
                {exportJobs.filter(j => j.status === 'COMPLETED').length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Completed Exports
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Typography variant="h4" color="info.main">
                {exportJobs.filter(j => j.status === 'IN_PROGRESS').length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                In Progress
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Typography variant="h4" color="success.main">
                {formatFileSize(
                  exportJobs
                    .filter(j => j.fileSize)
                    .reduce((sum, j) => sum + (j.fileSize || 0), 0)
                )}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total Data Exported
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Typography variant="h4" color="error.main">
                {exportJobs.filter(j => j.status === 'FAILED').length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Failed Exports
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Create Export Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Create New Export</DialogTitle>
        <DialogContent>
          <Grid container spacing={3} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Export Name"
                value={newExport.name}
                onChange={(e) => setNewExport(prev => ({ ...prev, name: e.target.value }))}
                placeholder="e.g., Monthly User Data Export"
              />
            </Grid>
            
            <Grid item xs={12} md={6}>
              <FormControl fullWidth>
                <InputLabel>Export Type</InputLabel>
                <Select
                  value={newExport.type}
                  onChange={(e) => setNewExport(prev => ({ ...prev, type: e.target.value as any }))}
                >
                  {exportTypes.map((type) => (
                    <MenuItem key={type.value} value={type.value}>
                      <Box>
                        <Typography variant="body1">{type.label}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {type.description}
                        </Typography>
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            
            <Grid item xs={12} md={6}>
              <FormControl fullWidth>
                <InputLabel>Format</InputLabel>
                <Select
                  value={newExport.format}
                  onChange={(e) => setNewExport(prev => ({ ...prev, format: e.target.value as any }))}
                >
                  {formatOptions.map((format) => (
                    <MenuItem key={format.value} value={format.value}>
                      <Box>
                        <Typography variant="body1">{format.label}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {format.description}
                        </Typography>
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            
            <Grid item xs={12} md={6}>
              <DatePicker
                label="Start Date"
                value={newExport.startDate}
                onChange={(date) => setNewExport(prev => ({ ...prev, startDate: date }))}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Grid>
            
            <Grid item xs={12} md={6}>
              <DatePicker
                label="End Date"
                value={newExport.endDate}
                onChange={(date) => setNewExport(prev => ({ ...prev, endDate: date }))}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Grid>
            
            <Grid item xs={12}>
              <Typography variant="subtitle2" gutterBottom>
                Export Options
              </Typography>
              
              <FormControlLabel
                control={
                  <Switch
                    checked={newExport.compression}
                    onChange={(e) => setNewExport(prev => ({ ...prev, compression: e.target.checked }))}
                  />
                }
                label="Enable Compression"
              />
              
              <FormControlLabel
                control={
                  <Switch
                    checked={newExport.encryption}
                    onChange={(e) => setNewExport(prev => ({ ...prev, encryption: e.target.checked }))}
                  />
                }
                label="Enable Encryption"
              />
              
              <FormControlLabel
                control={
                  <Switch
                    checked={newExport.includeMetadata}
                    onChange={(e) => setNewExport(prev => ({ ...prev, includeMetadata: e.target.checked }))}
                  />
                }
                label="Include Metadata"
              />
            </Grid>
          </Grid>

          <Alert severity="info" sx={{ mt: 3 }}>
            <Typography variant="body2">
              Large exports may take several hours to complete. You'll receive an email notification when ready.
            </Typography>
          </Alert>
        </DialogContent>
        
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={createExportJob}
            disabled={!newExport.name || !newExport.type}
          >
            Create Export
          </Button>
        </DialogActions>
      </Dialog>

      {/* Export Templates Dialog */}
      <Dialog open={templateDialogOpen} onClose={() => setTemplateDialogOpen(false)} maxWidth="lg" fullWidth>
        <DialogTitle>Export Templates</DialogTitle>
        <DialogContent>
          <List>
            {exportTemplates.map((template) => (
              <ListItem key={template.id}>
                <ListItemIcon>
                  <Description />
                </ListItemIcon>
                <ListItemText
                  primary={template.name}
                  secondary={
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        {template.description}
                      </Typography>
                      <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                        <Chip label={template.type} size="small" />
                        <Chip label={template.format} size="small" />
                        {template.schedule && <Chip label="Scheduled" size="small" color="primary" />}
                      </Box>
                    </Box>
                  }
                />
                <ListItemSecondaryAction>
                  <Button size="small" variant="outlined">
                    Use Template
                  </Button>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTemplateDialogOpen(false)}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};