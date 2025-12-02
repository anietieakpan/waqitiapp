import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormGroup,
  FormControlLabel,
  Checkbox,
  Typography,
  Box,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Chip,
  Divider,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import InfoIcon from '@mui/icons-material/Info';
import CheckIcon from '@mui/icons-material/CheckCircle';;
import { ExportFormat } from '../../types/gdpr';

interface DataExportDialogProps {
  open: boolean;
  onClose: () => void;
  onExport: (format: ExportFormat, categories: string[]) => Promise<void>;
  categories: Array<{ id: string; label: string; icon: React.ReactNode }>;
}

const formatInfo = {
  [ExportFormat.JSON]: {
    description: 'Machine-readable format, ideal for data portability',
    fileSize: 'Small',
    readability: 'Technical users',
  },
  [ExportFormat.CSV]: {
    description: 'Spreadsheet format, can be opened in Excel',
    fileSize: 'Medium',
    readability: 'All users',
  },
  [ExportFormat.PDF]: {
    description: 'Human-readable document with formatting',
    fileSize: 'Large',
    readability: 'All users',
  },
  [ExportFormat.EXCEL]: {
    description: 'Native Excel format with multiple sheets',
    fileSize: 'Medium',
    readability: 'All users',
  },
};

const DataExportDialog: React.FC<DataExportDialogProps> = ({
  open,
  onClose,
  onExport,
  categories,
}) => {
  const [format, setFormat] = useState<ExportFormat>(ExportFormat.JSON);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  const handleCategoryToggle = (categoryId: string) => {
    setSelectedCategories(prev =>
      prev.includes(categoryId)
        ? prev.filter(id => id !== categoryId)
        : [...prev, categoryId]
    );
  };

  const handleSelectAll = (checked: boolean) => {
    setSelectAll(checked);
    if (checked) {
      setSelectedCategories(categories.map(cat => cat.id));
    } else {
      setSelectedCategories([]);
    }
  };

  const handleExport = async () => {
    if (selectedCategories.length === 0) {
      return;
    }

    setIsExporting(true);
    try {
      await onExport(format, selectedCategories);
      onClose();
    } catch (error) {
      console.error('Export failed:', error);
    } finally {
      setIsExporting(false);
    }
  };

  const formatDetails = formatInfo[format];

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Export Your Data</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 3 }}>
          Your data export will be prepared and you'll receive an email with a 
          secure download link within 24 hours.
        </Alert>

        <Box sx={{ mb: 3 }}>
          <FormControl fullWidth>
            <InputLabel>Export Format</InputLabel>
            <Select
              value={format}
              onChange={(e) => setFormat(e.target.value as ExportFormat)}
              label="Export Format"
            >
              {Object.values(ExportFormat).map((fmt) => (
                <MenuItem key={fmt} value={fmt}>
                  {fmt}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {formatDetails && (
            <Box sx={{ mt: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
              <Typography variant="body2" color="text.secondary">
                {formatDetails.description}
              </Typography>
              <Box sx={{ mt: 1, display: 'flex', gap: 2 }}>
                <Chip 
                  size="small" 
                  label={`Size: ${formatDetails.fileSize}`}
                  variant="outlined"
                />
                <Chip 
                  size="small" 
                  label={`For: ${formatDetails.readability}`}
                  variant="outlined"
                />
              </Box>
            </Box>
          )}
        </Box>

        <Divider sx={{ my: 2 }} />

        <Typography variant="subtitle1" gutterBottom>
          Select Data Categories
        </Typography>

        <FormGroup>
          <FormControlLabel
            control={
              <Checkbox
                checked={selectAll}
                onChange={(e) => handleSelectAll(e.target.checked)}
                indeterminate={
                  selectedCategories.length > 0 && 
                  selectedCategories.length < categories.length
                }
              />
            }
            label={<strong>Select All</strong>}
          />
        </FormGroup>

        <List sx={{ mt: 1 }}>
          {categories.map((category) => (
            <ListItem
              key={category.id}
              dense
              button
              onClick={() => handleCategoryToggle(category.id)}
              sx={{
                borderRadius: 1,
                mb: 0.5,
                bgcolor: selectedCategories.includes(category.id) 
                  ? 'action.selected' 
                  : 'transparent',
              }}
            >
              <ListItemIcon>
                <Checkbox
                  edge="start"
                  checked={selectedCategories.includes(category.id)}
                  tabIndex={-1}
                  disableRipple
                />
              </ListItemIcon>
              <ListItemIcon sx={{ minWidth: 40 }}>
                {category.icon}
              </ListItemIcon>
              <ListItemText primary={category.label} />
            </ListItem>
          ))}
        </List>

        <Alert severity="warning" sx={{ mt: 2 }}>
          <Typography variant="body2">
            Some data may be retained for legal compliance even after export. 
            Financial records are kept for 7 years as required by law.
          </Typography>
        </Alert>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={isExporting}>
          Cancel
        </Button>
        <Button
          onClick={handleExport}
          variant="contained"
          startIcon={<DownloadIcon />}
          disabled={selectedCategories.length === 0 || isExporting}
        >
          {isExporting ? 'Preparing Export...' : 'Export Data'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DataExportDialog;