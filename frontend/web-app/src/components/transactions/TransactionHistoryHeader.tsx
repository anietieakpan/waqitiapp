import React, { memo, useState } from 'react';
import {
  Box,
  Typography,
  Button,
  IconButton,
  TextField,
  Chip,
  Menu,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  InputAdornment,
  Collapse,
} from '@mui/material';
import FilterIcon from '@mui/icons-material/FilterList';
import SearchIcon from '@mui/icons-material/Search';
import DownloadIcon from '@mui/icons-material/Download';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ClearIcon from '@mui/icons-material/Clear';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';

export interface TransactionFilters {
  search: string;
  status: string;
  type: string;
  dateFrom: Date | null;
  dateTo: Date | null;
  amountMin: string;
  amountMax: string;
}

interface TransactionHistoryHeaderProps {
  filters: TransactionFilters;
  onFiltersChange: (filters: TransactionFilters) => void;
  onExport: () => void;
  totalCount: number;
  isLoading: boolean;
}

const TransactionHistoryHeader = memo<TransactionHistoryHeaderProps>(({
  filters,
  onFiltersChange,
  onExport,
  totalCount,
  isLoading
}) => {
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [exportMenuAnchor, setExportMenuAnchor] = useState<null | HTMLElement>(null);

  const handleFilterChange = (field: keyof TransactionFilters, value: any) => {
    onFiltersChange({
      ...filters,
      [field]: value
    });
  };

  const clearFilters = () => {
    onFiltersChange({
      search: '',
      status: '',
      type: '',
      dateFrom: null,
      dateTo: null,
      amountMin: '',
      amountMax: ''
    });
  };

  const hasActiveFilters = Object.values(filters).some(value => 
    value !== '' && value !== null
  );

  const transactionTypes = [
    { value: '', label: 'All Types' },
    { value: 'PAYMENT', label: 'Payment' },
    { value: 'TRANSFER', label: 'Transfer' },
    { value: 'DEPOSIT', label: 'Deposit' },
    { value: 'WITHDRAWAL', label: 'Withdrawal' },
    { value: 'REFUND', label: 'Refund' },
  ];

  const transactionStatuses = [
    { value: '', label: 'All Statuses' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'PENDING', label: 'Pending' },
    { value: 'FAILED', label: 'Failed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];

  return (
    <Box sx={{ mb: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Box>
          <Typography variant="h5" component="h1" fontWeight="bold">
            Transaction History
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {isLoading ? 'Loading...' : `${totalCount} transactions`}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<FilterIcon />}
            endIcon={filtersExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            onClick={() => setFiltersExpanded(!filtersExpanded)}
            color={hasActiveFilters ? 'primary' : 'inherit'}
          >
            Filters
            {hasActiveFilters && (
              <Chip 
                size="small" 
                label="•" 
                sx={{ ml: 1, minWidth: 'auto', height: 16 }} 
              />
            )}
          </Button>

          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={(e) => setExportMenuAnchor(e.currentTarget)}
            disabled={totalCount === 0}
          >
            Export
          </Button>
        </Box>
      </Box>

      {/* Search Bar */}
      <Box sx={{ mb: 2 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Search transactions by amount, description, or transaction ID..."
          value={filters.search}
          onChange={(e) => handleFilterChange('search', e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
            endAdornment: filters.search && (
              <InputAdornment position="end">
                <IconButton
                  size="small"
                  onClick={() => handleFilterChange('search', '')}
                >
                  <ClearIcon />
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {/* Advanced Filters */}
      <Collapse in={filtersExpanded}>
        <Box sx={{ 
          display: 'grid', 
          gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 1fr' },
          gap: 2,
          p: 2,
          bgcolor: 'background.paper',
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
          mb: 2
        }}>
          {/* Status Filter */}
          <FormControl size="small" fullWidth>
            <InputLabel>Status</InputLabel>
            <Select
              value={filters.status}
              label="Status"
              onChange={(e) => handleFilterChange('status', e.target.value)}
            >
              {transactionStatuses.map(status => (
                <MenuItem key={status.value} value={status.value}>
                  {status.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Type Filter */}
          <FormControl size="small" fullWidth>
            <InputLabel>Type</InputLabel>
            <Select
              value={filters.type}
              label="Type"
              onChange={(e) => handleFilterChange('type', e.target.value)}
            >
              {transactionTypes.map(type => (
                <MenuItem key={type.value} value={type.value}>
                  {type.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Amount Range */}
          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              size="small"
              label="Min Amount"
              type="number"
              value={filters.amountMin}
              onChange={(e) => handleFilterChange('amountMin', e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">$</InputAdornment>,
              }}
            />
            <TextField
              size="small"
              label="Max Amount"
              type="number"
              value={filters.amountMax}
              onChange={(e) => handleFilterChange('amountMax', e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">$</InputAdornment>,
              }}
            />
          </Box>

          {/* Date Range */}
          <DatePicker
            label="From Date"
            value={filters.dateFrom}
            onChange={(date) => handleFilterChange('dateFrom', date)}
            slotProps={{ textField: { size: 'small', fullWidth: true } }}
          />

          <DatePicker
            label="To Date"
            value={filters.dateTo}
            onChange={(date) => handleFilterChange('dateTo', date)}
            slotProps={{ textField: { size: 'small', fullWidth: true } }}
          />

          {/* Clear Filters */}
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Button
              variant="text"
              onClick={clearFilters}
              disabled={!hasActiveFilters}
              startIcon={<ClearIcon />}
            >
              Clear Filters
            </Button>
          </Box>
        </Box>
      </Collapse>

      {/* Export Menu */}
      <Menu
        anchorEl={exportMenuAnchor}
        open={Boolean(exportMenuAnchor)}
        onClose={() => setExportMenuAnchor(null)}
      >
        <MenuItem onClick={() => {
          onExport();
          setExportMenuAnchor(null);
        }}>
          Export as CSV
        </MenuItem>
        <MenuItem onClick={() => setExportMenuAnchor(null)}>
          Export as PDF
        </MenuItem>
        <MenuItem onClick={() => setExportMenuAnchor(null)}>
          Export as Excel
        </MenuItem>
      </Menu>
    </Box>
  );
});

TransactionHistoryHeader.displayName = 'TransactionHistoryHeader';

export default TransactionHistoryHeader;