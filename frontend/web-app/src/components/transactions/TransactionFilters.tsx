import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Chip,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Slider,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Grid,
  IconButton,
  Badge,
  Tooltip,
  Autocomplete,
  ToggleButton,
  ToggleButtonGroup,
  useTheme,
  alpha,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ClearIcon from '@mui/icons-material/Clear';
import FilterIcon from '@mui/icons-material/FilterList';
import DateRangeIcon from '@mui/icons-material/DateRange';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CategoryIcon from '@mui/icons-material/Category';
import LabelIcon from '@mui/icons-material/Label';
import BusinessIcon from '@mui/icons-material/Business';
import CardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';
import OfferIcon from '@mui/icons-material/LocalOffer';
import SearchIcon from '@mui/icons-material/Search';
import SaveIcon from '@mui/icons-material/SaveAlt';
import RestoreIcon from '@mui/icons-material/Restore';
import BookmarkIcon from '@mui/icons-material/Bookmark';;
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { format, subDays, startOfMonth, endOfMonth, startOfQuarter, endOfQuarter, startOfYear, endOfYear } from 'date-fns';
import { Transaction, TransactionType, TransactionStatus, PaymentMethodType } from '../../types/wallet';

interface TransactionFiltersProps {
  onApplyFilters: (filters: TransactionFilters) => void;
  onReset: () => void;
  onSavePreset?: (preset: FilterPreset) => void;
  savedPresets?: FilterPreset[];
  activeFilterCount?: number;
}

export interface TransactionFilters {
  search?: string;
  types?: TransactionType[];
  statuses?: TransactionStatus[];
  dateRange?: {
    start: Date | null;
    end: Date | null;
  };
  amountRange?: {
    min: number;
    max: number;
  };
  categories?: string[];
  tags?: string[];
  merchants?: string[];
  paymentMethods?: PaymentMethodType[];
  currency?: string;
}

interface FilterPreset {
  id: string;
  name: string;
  filters: TransactionFilters;
  createdAt: Date;
}

const TransactionFilters: React.FC<TransactionFiltersProps> = ({
  onApplyFilters,
  onReset,
  onSavePreset,
  savedPresets = [],
  activeFilterCount = 0,
}) => {
  const theme = useTheme();
  const [filters, setFilters] = useState<TransactionFilters>({});
  const [expandedSections, setExpandedSections] = useState<string[]>(['basic']);
  const [presetName, setPresetName] = useState('');
  const [showSavePreset, setShowSavePreset] = useState(false);

  // Mock data - in real app, these would come from API
  const categories = [
    'Food & Dining',
    'Shopping',
    'Transportation',
    'Entertainment',
    'Bills & Utilities',
    'Healthcare',
    'Education',
    'Travel',
    'Other',
  ];

  const merchants = [
    'Amazon',
    'Walmart',
    'Starbucks',
    'Uber',
    'Netflix',
    'Spotify',
    'Target',
    'Whole Foods',
  ];

  const tags = [
    'business',
    'personal',
    'recurring',
    'subscription',
    'reimbursable',
    'tax-deductible',
  ];

  const datePresets = [
    { label: 'Today', getValue: () => ({ start: new Date(), end: new Date() }) },
    { label: 'Last 7 days', getValue: () => ({ start: subDays(new Date(), 7), end: new Date() }) },
    { label: 'Last 30 days', getValue: () => ({ start: subDays(new Date(), 30), end: new Date() }) },
    { label: 'This month', getValue: () => ({ start: startOfMonth(new Date()), end: endOfMonth(new Date()) }) },
    { label: 'This quarter', getValue: () => ({ start: startOfQuarter(new Date()), end: endOfQuarter(new Date()) }) },
    { label: 'This year', getValue: () => ({ start: startOfYear(new Date()), end: endOfYear(new Date()) }) },
  ];

  const handleSectionToggle = (section: string) => {
    setExpandedSections(prev =>
      prev.includes(section)
        ? prev.filter(s => s !== section)
        : [...prev, section]
    );
  };

  const handleFilterChange = (key: keyof TransactionFilters, value: any) => {
    setFilters(prev => ({
      ...prev,
      [key]: value,
    }));
  };

  const handleApplyFilters = () => {
    onApplyFilters(filters);
  };

  const handleReset = () => {
    setFilters({});
    onReset();
  };

  const handleSavePreset = () => {
    if (presetName && onSavePreset) {
      onSavePreset({
        id: Date.now().toString(),
        name: presetName,
        filters,
        createdAt: new Date(),
      });
      setPresetName('');
      setShowSavePreset(false);
    }
  };

  const handleApplyPreset = (preset: FilterPreset) => {
    setFilters(preset.filters);
    onApplyFilters(preset.filters);
  };

  const renderBasicFilters = () => (
    <AccordionDetails>
      <Grid container spacing={2}>
        <Grid item xs={12}>
          <TextField
            fullWidth
            placeholder="Search transactions..."
            value={filters.search || ''}
            onChange={(e) => handleFilterChange('search', e.target.value)}
            InputProps={{
              startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
            }}
          />
        </Grid>

        <Grid item xs={12} sm={6}>
          <FormControl fullWidth>
            <InputLabel>Transaction Types</InputLabel>
            <Select
              multiple
              value={filters.types || []}
              onChange={(e) => handleFilterChange('types', e.target.value)}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selected.map((value) => (
                    <Chip key={value} label={value} size="small" />
                  ))}
                </Box>
              )}
            >
              {Object.values(TransactionType).map((type) => (
                <MenuItem key={type} value={type}>
                  {type.replace(/_/g, ' ')}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>

        <Grid item xs={12} sm={6}>
          <FormControl fullWidth>
            <InputLabel>Status</InputLabel>
            <Select
              multiple
              value={filters.statuses || []}
              onChange={(e) => handleFilterChange('statuses', e.target.value)}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selected.map((value) => (
                    <Chip key={value} label={value} size="small" />
                  ))}
                </Box>
              )}
            >
              {Object.values(TransactionStatus).map((status) => (
                <MenuItem key={status} value={status}>
                  {status}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
      </Grid>
    </AccordionDetails>
  );

  const renderDateFilters = () => (
    <AccordionDetails>
      <LocalizationProvider dateAdapter={AdapterDateFns}>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
              {datePresets.map((preset) => (
                <Chip
                  key={preset.label}
                  label={preset.label}
                  onClick={() => handleFilterChange('dateRange', preset.getValue())}
                  variant="outlined"
                  size="small"
                />
              ))}
            </Box>
          </Grid>

          <Grid item xs={12} sm={6}>
            <DatePicker
              label="Start Date"
              value={filters.dateRange?.start || null}
              onChange={(date) => handleFilterChange('dateRange', {
                ...filters.dateRange,
                start: date,
              })}
              renderInput={(params) => <TextField {...params} fullWidth />}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <DatePicker
              label="End Date"
              value={filters.dateRange?.end || null}
              onChange={(date) => handleFilterChange('dateRange', {
                ...filters.dateRange,
                end: date,
              })}
              renderInput={(params) => <TextField {...params} fullWidth />}
            />
          </Grid>
        </Grid>
      </LocalizationProvider>
    </AccordionDetails>
  );

  const renderAmountFilters = () => (
    <AccordionDetails>
      <Grid container spacing={2}>
        <Grid item xs={12}>
          <Typography gutterBottom>Amount Range</Typography>
          <Box sx={{ px: 2 }}>
            <Slider
              value={[
                filters.amountRange?.min || 0,
                filters.amountRange?.max || 10000,
              ]}
              onChange={(_, value) => {
                const [min, max] = value as number[];
                handleFilterChange('amountRange', { min, max });
              }}
              valueLabelDisplay="auto"
              min={0}
              max={10000}
              step={100}
              marks={[
                { value: 0, label: '$0' },
                { value: 2500, label: '$2.5k' },
                { value: 5000, label: '$5k' },
                { value: 7500, label: '$7.5k' },
                { value: 10000, label: '$10k' },
              ]}
            />
          </Box>
        </Grid>

        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            label="Min Amount"
            type="number"
            value={filters.amountRange?.min || ''}
            onChange={(e) => handleFilterChange('amountRange', {
              ...filters.amountRange,
              min: Number(e.target.value),
            })}
          />
        </Grid>

        <Grid item xs={12} sm={6}>
          <TextField
            fullWidth
            label="Max Amount"
            type="number"
            value={filters.amountRange?.max || ''}
            onChange={(e) => handleFilterChange('amountRange', {
              ...filters.amountRange,
              max: Number(e.target.value),
            })}
          />
        </Grid>

        <Grid item xs={12}>
          <FormControl fullWidth>
            <InputLabel>Currency</InputLabel>
            <Select
              value={filters.currency || 'USD'}
              onChange={(e) => handleFilterChange('currency', e.target.value)}
            >
              <MenuItem value="USD">USD - US Dollar</MenuItem>
              <MenuItem value="EUR">EUR - Euro</MenuItem>
              <MenuItem value="GBP">GBP - British Pound</MenuItem>
              <MenuItem value="JPY">JPY - Japanese Yen</MenuItem>
              <MenuItem value="CAD">CAD - Canadian Dollar</MenuItem>
            </Select>
          </FormControl>
        </Grid>
      </Grid>
    </AccordionDetails>
  );

  const renderCategoryFilters = () => (
    <AccordionDetails>
      <Grid container spacing={2}>
        <Grid item xs={12}>
          <Autocomplete
            multiple
            options={categories}
            value={filters.categories || []}
            onChange={(_, value) => handleFilterChange('categories', value)}
            renderInput={(params) => (
              <TextField {...params} label="Categories" placeholder="Select categories" />
            )}
            renderTags={(value, getTagProps) =>
              value.map((option, index) => (
                <Chip
                  variant="outlined"
                  label={option}
                  size="small"
                  {...getTagProps({ index })}
                />
              ))
            }
          />
        </Grid>

        <Grid item xs={12}>
          <Autocomplete
            multiple
            options={tags}
            value={filters.tags || []}
            onChange={(_, value) => handleFilterChange('tags', value)}
            renderInput={(params) => (
              <TextField {...params} label="Tags" placeholder="Select tags" />
            )}
            renderTags={(value, getTagProps) =>
              value.map((option, index) => (
                <Chip
                  variant="outlined"
                  label={option}
                  size="small"
                  icon={<LabelIcon />}
                  {...getTagProps({ index })}
                />
              ))
            }
          />
        </Grid>

        <Grid item xs={12}>
          <Autocomplete
            multiple
            options={merchants}
            value={filters.merchants || []}
            onChange={(_, value) => handleFilterChange('merchants', value)}
            renderInput={(params) => (
              <TextField {...params} label="Merchants" placeholder="Select merchants" />
            )}
            renderTags={(value, getTagProps) =>
              value.map((option, index) => (
                <Chip
                  variant="outlined"
                  label={option}
                  size="small"
                  icon={<BusinessIcon />}
                  {...getTagProps({ index })}
                />
              ))
            }
          />
        </Grid>
      </Grid>
    </AccordionDetails>
  );

  const renderPaymentMethodFilters = () => (
    <AccordionDetails>
      <FormControl fullWidth>
        <InputLabel>Payment Methods</InputLabel>
        <Select
          multiple
          value={filters.paymentMethods || []}
          onChange={(e) => handleFilterChange('paymentMethods', e.target.value)}
          renderValue={(selected) => (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
              {selected.map((value) => (
                <Chip
                  key={value}
                  label={value.replace(/_/g, ' ')}
                  size="small"
                  icon={value.includes('CARD') ? <CardIcon /> : <BankIcon />}
                />
              ))}
            </Box>
          )}
        >
          {Object.values(PaymentMethodType).map((method) => (
            <MenuItem key={method} value={method}>
              {method.replace(/_/g, ' ')}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
    </AccordionDetails>
  );

  const renderPresets = () => (
    <Box sx={{ mb: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
          Filter Presets
        </Typography>
        <Button
          size="small"
          startIcon={<SaveIcon />}
          onClick={() => setShowSavePreset(!showSavePreset)}
        >
          Save Current
        </Button>
      </Box>

      {showSavePreset && (
        <Box sx={{ mb: 2, p: 2, bgcolor: alpha(theme.palette.primary.main, 0.05), borderRadius: 1 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Preset name..."
            value={presetName}
            onChange={(e) => setPresetName(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSavePreset()}
            InputProps={{
              endAdornment: (
                <Button size="small" onClick={handleSavePreset} disabled={!presetName}>
                  Save
                </Button>
              ),
            }}
          />
        </Box>
      )}

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
        {savedPresets.map((preset) => (
          <Chip
            key={preset.id}
            label={preset.name}
            onClick={() => handleApplyPreset(preset)}
            onDelete={() => {/* Handle delete */}}
            icon={<BookmarkIcon />}
            variant="outlined"
          />
        ))}
      </Box>
    </Box>
  );

  return (
    <Paper sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <FilterIcon />
          <Typography variant="h6">Filters</Typography>
          {activeFilterCount > 0 && (
            <Badge badgeContent={activeFilterCount} color="primary" />
          )}
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            size="small"
            onClick={handleReset}
            startIcon={<RestoreIcon />}
          >
            Reset
          </Button>
          <Button
            size="small"
            variant="contained"
            onClick={handleApplyFilters}
          >
            Apply
          </Button>
        </Box>
      </Box>

      {savedPresets.length > 0 && renderPresets()}

      <Accordion
        expanded={expandedSections.includes('basic')}
        onChange={() => handleSectionToggle('basic')}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography>Basic Filters</Typography>
        </AccordionSummary>
        {renderBasicFilters()}
      </Accordion>

      <Accordion
        expanded={expandedSections.includes('date')}
        onChange={() => handleSectionToggle('date')}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <DateRangeIcon />
            <Typography>Date Range</Typography>
          </Box>
        </AccordionSummary>
        {renderDateFilters()}
      </Accordion>

      <Accordion
        expanded={expandedSections.includes('amount')}
        onChange={() => handleSectionToggle('amount')}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <MoneyIcon />
            <Typography>Amount</Typography>
          </Box>
        </AccordionSummary>
        {renderAmountFilters()}
      </Accordion>

      <Accordion
        expanded={expandedSections.includes('category')}
        onChange={() => handleSectionToggle('category')}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CategoryIcon />
            <Typography>Categories & Tags</Typography>
          </Box>
        </AccordionSummary>
        {renderCategoryFilters()}
      </Accordion>

      <Accordion
        expanded={expandedSections.includes('payment')}
        onChange={() => handleSectionToggle('payment')}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CardIcon />
            <Typography>Payment Methods</Typography>
          </Box>
        </AccordionSummary>
        {renderPaymentMethodFilters()}
      </Accordion>
    </Paper>
  );
};

export default TransactionFilters;