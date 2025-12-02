import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  IconButton,
  Avatar,
  Chip,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Paper,
  Alert,
  CircularProgress,
  Divider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Switch,
  FormControlLabel,
  InputAdornment,
  useTheme,
  alpha,
  Tooltip,
  Badge,
  Menu,
  ListItemIcon,
  Fab,
  Zoom,
  Tab,
  Tabs,
  Slider,
  FormGroup,
  Checkbox,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import DuplicateIcon from '@mui/icons-material/ContentCopy';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import MoreIcon from '@mui/icons-material/MoreVert';
import HomeIcon from '@mui/icons-material/Home';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import CarIcon from '@mui/icons-material/DirectionsCar';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import BusinessIcon from '@mui/icons-material/Business';
import EducationIcon from '@mui/icons-material/School';
import HealthIcon from '@mui/icons-material/LocalHospital';
import TravelIcon from '@mui/icons-material/Flight';
import SubscriptionIcon from '@mui/icons-material/Subscriptions';
import CategoryIcon from '@mui/icons-material/Category';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import ScheduleIcon from '@mui/icons-material/Schedule';
import GroupIcon from '@mui/icons-material/Group';
import PersonIcon from '@mui/icons-material/Person';
import SettingsIcon from '@mui/icons-material/Settings';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckIcon from '@mui/icons-material/CheckCircle';
import CloseIcon from '@mui/icons-material/Close';
import SearchIcon from '@mui/icons-material/Search';
import FilterIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import PaletteIcon from '@mui/icons-material/Palette';
import LabelIcon from '@mui/icons-material/Label';
import TimerIcon from '@mui/icons-material/Timer';
import RepeatIcon from '@mui/icons-material/Repeat';
import CalendarIcon from '@mui/icons-material/CalendarToday';;
import { format, parseISO } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';
import toast from 'react-hot-toast';

interface RequestTemplate {
  id: string;
  name: string;
  description?: string;
  category: string;
  icon: string;
  color: string;
  amount?: number;
  currency: string;
  frequency?: 'once' | 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'yearly';
  priority: 'low' | 'normal' | 'high' | 'urgent';
  tags: string[];
  fields: {
    recipientType?: 'individual' | 'business' | 'group';
    dueInDays?: number;
    expiresInDays?: number;
    reminderEnabled?: boolean;
    reminderDays?: number[];
    notes?: string;
    attachmentTypes?: string[];
    customFields?: Array<{
      name: string;
      type: 'text' | 'number' | 'date' | 'select';
      required: boolean;
      options?: string[];
    }>;
  };
  isDefault: boolean;
  isFavorite: boolean;
  isActive: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  usage: {
    count: number;
    lastUsed?: string;
    successRate: number;
    averagePaymentTime?: number; // in days
  };
  permissions: {
    isPublic: boolean;
    sharedWith?: string[];
    canEdit: string[];
  };
}

interface TemplateCategory {
  id: string;
  name: string;
  icon: React.ReactNode;
  color: string;
  description: string;
}

interface RequestTemplatesProps {
  onSelectTemplate?: (template: RequestTemplate) => void;
  mode?: 'manage' | 'select';
}

const RequestTemplates: React.FC<RequestTemplatesProps> = ({
  onSelectTemplate,
  mode = 'manage',
}) => {
  const theme = useTheme();
  
  const [templates, setTemplates] = useState<RequestTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<RequestTemplate | null>(null);
  const [selectedTab, setSelectedTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [createDialog, setCreateDialog] = useState(false);
  const [editDialog, setEditDialog] = useState(false);
  const [deleteDialog, setDeleteDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterCategory, setFilterCategory] = useState<string>('all');
  const [filterFavorites, setFilterFavorites] = useState(false);
  const [sortBy, setSortBy] = useState<'name' | 'usage' | 'updated' | 'created'>('usage');

  // Form state
  const [templateForm, setTemplateForm] = useState({
    name: '',
    description: '',
    category: 'general',
    icon: 'receipt',
    color: '#2196F3',
    amount: '',
    currency: 'USD',
    frequency: 'once' as const,
    priority: 'normal' as const,
    tags: [] as string[],
    recipientType: 'individual' as const,
    dueInDays: 7,
    expiresInDays: 30,
    reminderEnabled: true,
    reminderDays: [7, 3, 1],
    notes: '',
    isPublic: false,
  });

  const categories: TemplateCategory[] = [
    { id: 'housing', name: 'Housing', icon: <HomeIcon />, color: '#2196F3', description: 'Rent, utilities, maintenance' },
    { id: 'food', name: 'Food & Dining', icon: <RestaurantIcon />, color: '#4CAF50', description: 'Restaurants, groceries, delivery' },
    { id: 'transport', name: 'Transportation', icon: <CarIcon />, color: '#FF9800', description: 'Gas, parking, rideshare' },
    { id: 'shopping', name: 'Shopping', icon: <ShoppingIcon />, color: '#E91E63', description: 'Retail, online shopping' },
    { id: 'business', name: 'Business', icon: <BusinessIcon />, color: '#9C27B0', description: 'Invoices, services, freelance' },
    { id: 'education', name: 'Education', icon: <EducationIcon />, color: '#00BCD4', description: 'Tuition, courses, supplies' },
    { id: 'health', name: 'Healthcare', icon: <HealthIcon />, color: '#F44336', description: 'Medical, dental, pharmacy' },
    { id: 'travel', name: 'Travel', icon: <TravelIcon />, color: '#3F51B5', description: 'Flights, hotels, vacation' },
    { id: 'subscription', name: 'Subscriptions', icon: <SubscriptionIcon />, color: '#795548', description: 'Monthly services, memberships' },
    { id: 'general', name: 'General', icon: <CategoryIcon />, color: '#607D8B', description: 'Other expenses' },
  ];

  // Mock data
  const mockTemplates: RequestTemplate[] = [
    {
      id: '1',
      name: 'Monthly Rent',
      description: 'Standard monthly rent payment request',
      category: 'housing',
      icon: 'home',
      color: '#2196F3',
      amount: 1200,
      currency: 'USD',
      frequency: 'monthly',
      priority: 'high',
      tags: ['rent', 'monthly', 'housing'],
      fields: {
        recipientType: 'business',
        dueInDays: 5,
        expiresInDays: 10,
        reminderEnabled: true,
        reminderDays: [5, 3, 1],
        notes: 'Rent for apartment {{apartment_number}}',
      },
      isDefault: true,
      isFavorite: true,
      isActive: true,
      createdBy: 'system',
      createdAt: new Date(Date.now() - 86400000 * 90).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      usage: {
        count: 127,
        lastUsed: new Date(Date.now() - 86400000 * 2).toISOString(),
        successRate: 98.5,
        averagePaymentTime: 2.3,
      },
      permissions: {
        isPublic: true,
        canEdit: ['system'],
      },
    },
    {
      id: '2',
      name: 'Split Restaurant Bill',
      description: 'Equal split for dining expenses',
      category: 'food',
      icon: 'restaurant',
      color: '#4CAF50',
      currency: 'USD',
      frequency: 'once',
      priority: 'normal',
      tags: ['dining', 'split', 'group'],
      fields: {
        recipientType: 'group',
        dueInDays: 3,
        expiresInDays: 7,
        reminderEnabled: true,
        reminderDays: [3, 1],
        notes: 'Split bill for {{restaurant_name}} on {{date}}',
      },
      isDefault: true,
      isFavorite: false,
      isActive: true,
      createdBy: 'system',
      createdAt: new Date(Date.now() - 86400000 * 60).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
      usage: {
        count: 89,
        lastUsed: new Date(Date.now() - 86400000 * 5).toISOString(),
        successRate: 92.1,
        averagePaymentTime: 1.8,
      },
      permissions: {
        isPublic: true,
        canEdit: ['system'],
      },
    },
    {
      id: '3',
      name: 'Freelance Invoice',
      description: 'Professional service invoice template',
      category: 'business',
      icon: 'business',
      color: '#9C27B0',
      currency: 'USD',
      frequency: 'once',
      priority: 'normal',
      tags: ['invoice', 'business', 'freelance'],
      fields: {
        recipientType: 'business',
        dueInDays: 30,
        expiresInDays: 45,
        reminderEnabled: true,
        reminderDays: [14, 7, 3],
        notes: 'Invoice for {{project_name}} - {{invoice_number}}',
        attachmentTypes: ['pdf', 'doc'],
        customFields: [
          { name: 'Invoice Number', type: 'text', required: true },
          { name: 'Project Name', type: 'text', required: true },
          { name: 'Hours Worked', type: 'number', required: false },
          { name: 'Hourly Rate', type: 'number', required: false },
        ],
      },
      isDefault: false,
      isFavorite: true,
      isActive: true,
      createdBy: 'user123',
      createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      usage: {
        count: 45,
        lastUsed: new Date(Date.now() - 86400000 * 1).toISOString(),
        successRate: 94.8,
        averagePaymentTime: 12.5,
      },
      permissions: {
        isPublic: false,
        sharedWith: ['team123'],
        canEdit: ['user123'],
      },
    },
    {
      id: '4',
      name: 'Utility Bill Split',
      description: 'Split utility bills among roommates',
      category: 'housing',
      icon: 'home',
      color: '#2196F3',
      currency: 'USD',
      frequency: 'monthly',
      priority: 'normal',
      tags: ['utilities', 'split', 'monthly'],
      fields: {
        recipientType: 'group',
        dueInDays: 10,
        expiresInDays: 15,
        reminderEnabled: true,
        reminderDays: [7, 3],
        notes: '{{utility_type}} bill for {{month}}',
      },
      isDefault: false,
      isFavorite: false,
      isActive: true,
      createdBy: 'user456',
      createdAt: new Date(Date.now() - 86400000 * 45).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 15).toISOString(),
      usage: {
        count: 23,
        lastUsed: new Date(Date.now() - 86400000 * 15).toISOString(),
        successRate: 89.7,
        averagePaymentTime: 4.2,
      },
      permissions: {
        isPublic: true,
        canEdit: ['user456'],
      },
    },
  ];

  const iconMap: Record<string, React.ReactNode> = {
    home: <HomeIcon />,
    restaurant: <RestaurantIcon />,
    car: <CarIcon />,
    shopping: <ShoppingIcon />,
    business: <BusinessIcon />,
    education: <EducationIcon />,
    health: <HealthIcon />,
    travel: <TravelIcon />,
    subscription: <SubscriptionIcon />,
    category: <CategoryIcon />,
  };

  useEffect(() => {
    loadTemplates();
  }, []);

  const loadTemplates = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      setTemplates(mockTemplates);
    } catch (error) {
      toast.error('Failed to load templates');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateTemplate = async () => {
    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const newTemplate: RequestTemplate = {
        id: `template_${Date.now()}`,
        name: templateForm.name,
        description: templateForm.description,
        category: templateForm.category,
        icon: templateForm.icon,
        color: templateForm.color,
        amount: templateForm.amount ? parseFloat(templateForm.amount) : undefined,
        currency: templateForm.currency,
        frequency: templateForm.frequency,
        priority: templateForm.priority,
        tags: templateForm.tags,
        fields: {
          recipientType: templateForm.recipientType,
          dueInDays: templateForm.dueInDays,
          expiresInDays: templateForm.expiresInDays,
          reminderEnabled: templateForm.reminderEnabled,
          reminderDays: templateForm.reminderDays,
          notes: templateForm.notes,
        },
        isDefault: false,
        isFavorite: false,
        isActive: true,
        createdBy: 'current_user',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        usage: {
          count: 0,
          successRate: 0,
        },
        permissions: {
          isPublic: templateForm.isPublic,
          canEdit: ['current_user'],
        },
      };

      setTemplates([newTemplate, ...templates]);
      setCreateDialog(false);
      resetForm();
      toast.success('Template created successfully');
    } catch (error) {
      toast.error('Failed to create template');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateTemplate = async () => {
    if (!selectedTemplate) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const updatedTemplate = {
        ...selectedTemplate,
        ...templateForm,
        amount: templateForm.amount ? parseFloat(templateForm.amount) : undefined,
        updatedAt: new Date().toISOString(),
      };

      setTemplates(templates.map(t => 
        t.id === selectedTemplate.id ? updatedTemplate : t
      ));
      setEditDialog(false);
      setSelectedTemplate(null);
      toast.success('Template updated successfully');
    } catch (error) {
      toast.error('Failed to update template');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteTemplate = async () => {
    if (!selectedTemplate) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      setTemplates(templates.filter(t => t.id !== selectedTemplate.id));
      setDeleteDialog(false);
      setSelectedTemplate(null);
      toast.success('Template deleted successfully');
    } catch (error) {
      toast.error('Failed to delete template');
    } finally {
      setLoading(false);
    }
  };

  const handleDuplicateTemplate = async (template: RequestTemplate) => {
    try {
      const duplicatedTemplate = {
        ...template,
        id: `template_${Date.now()}`,
        name: `${template.name} (Copy)`,
        isDefault: false,
        isFavorite: false,
        createdBy: 'current_user',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        usage: {
          count: 0,
          successRate: 0,
        },
      };

      setTemplates([duplicatedTemplate, ...templates]);
      toast.success('Template duplicated successfully');
    } catch (error) {
      toast.error('Failed to duplicate template');
    }
  };

  const handleToggleFavorite = async (template: RequestTemplate) => {
    try {
      const updatedTemplate = {
        ...template,
        isFavorite: !template.isFavorite,
      };

      setTemplates(templates.map(t => 
        t.id === template.id ? updatedTemplate : t
      ));
    } catch (error) {
      toast.error('Failed to update favorite status');
    }
  };

  const resetForm = () => {
    setTemplateForm({
      name: '',
      description: '',
      category: 'general',
      icon: 'receipt',
      color: '#2196F3',
      amount: '',
      currency: 'USD',
      frequency: 'once',
      priority: 'normal',
      tags: [],
      recipientType: 'individual',
      dueInDays: 7,
      expiresInDays: 30,
      reminderEnabled: true,
      reminderDays: [7, 3, 1],
      notes: '',
      isPublic: false,
    });
  };

  const getFilteredTemplates = () => {
    let filtered = templates;

    // Search filter
    if (searchQuery) {
      filtered = filtered.filter(template =>
        template.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        template.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        template.tags.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()))
      );
    }

    // Category filter
    if (filterCategory !== 'all') {
      filtered = filtered.filter(template => template.category === filterCategory);
    }

    // Favorites filter
    if (filterFavorites) {
      filtered = filtered.filter(template => template.isFavorite);
    }

    // Tab filter
    switch (selectedTab) {
      case 1: // My Templates
        filtered = filtered.filter(template => template.createdBy === 'current_user' || template.createdBy === 'user123' || template.createdBy === 'user456');
        break;
      case 2: // Public Templates
        filtered = filtered.filter(template => template.permissions.isPublic);
        break;
      case 3: // Favorites
        filtered = filtered.filter(template => template.isFavorite);
        break;
    }

    return filtered;
  };

  const getSortedTemplates = (templates: RequestTemplate[]) => {
    return [...templates].sort((a, b) => {
      switch (sortBy) {
        case 'name':
          return a.name.localeCompare(b.name);
        case 'usage':
          return b.usage.count - a.usage.count;
        case 'updated':
          return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
        case 'created':
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        default:
          return 0;
      }
    });
  };

  const renderTemplateCard = (template: RequestTemplate) => {
    const category = categories.find(c => c.id === template.category);
    const canEdit = template.permissions.canEdit.includes('current_user') || template.permissions.canEdit.includes('user123');

    return (
      <Card
        key={template.id}
        sx={{
          cursor: mode === 'select' ? 'pointer' : 'default',
          transition: 'all 0.2s',
          '&:hover': {
            transform: 'translateY(-2px)',
            boxShadow: theme.shadows[4],
          },
        }}
        onClick={() => {
          if (mode === 'select' && onSelectTemplate) {
            onSelectTemplate(template);
          }
        }}
      >
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar
                sx={{
                  bgcolor: template.color,
                  color: 'white',
                  width: 48,
                  height: 48,
                }}
              >
                {iconMap[template.icon] || <CategoryIcon />}
              </Avatar>
              <Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {template.name}
                  </Typography>
                  {template.isDefault && (
                    <Chip label="Default" size="small" color="primary" />
                  )}
                  {template.permissions.isPublic && (
                    <Chip label="Public" size="small" variant="outlined" />
                  )}
                </Box>
                {template.description && (
                  <Typography variant="body2" color="text.secondary">
                    {template.description}
                  </Typography>
                )}
              </Box>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <IconButton
                size="small"
                onClick={(e) => {
                  e.stopPropagation();
                  handleToggleFavorite(template);
                }}
              >
                {template.isFavorite ? (
                  <StarIcon sx={{ color: 'warning.main' }} />
                ) : (
                  <StarBorderIcon />
                )}
              </IconButton>
              {mode === 'manage' && (
                <IconButton
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedTemplate(template);
                    setMenuAnchor(e.currentTarget);
                  }}
                >
                  <MoreIcon />
                </IconButton>
              )}
            </Box>
          </Box>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            {template.amount && (
              <Grid item xs={6}>
                <Typography variant="body2" color="text.secondary">
                  Default Amount
                </Typography>
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                  {formatCurrency(template.amount)}
                </Typography>
              </Grid>
            )}
            {template.frequency && template.frequency !== 'once' && (
              <Grid item xs={6}>
                <Typography variant="body2" color="text.secondary">
                  Frequency
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <RepeatIcon sx={{ fontSize: 16 }} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                    {template.frequency.charAt(0).toUpperCase() + template.frequency.slice(1)}
                  </Typography>
                </Box>
              </Grid>
            )}
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">
                Used
              </Typography>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                {template.usage.count} times
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">
                Success Rate
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                {template.usage.successRate >= 90 ? (
                  <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />
                ) : template.usage.successRate >= 75 ? (
                  <WarningIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                ) : (
                  <WarningIcon sx={{ fontSize: 16, color: 'error.main' }} />
                )}
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                  {template.usage.successRate}%
                </Typography>
              </Box>
            </Grid>
          </Grid>

          {template.tags.length > 0 && (
            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 2 }}>
              {template.tags.map((tag, index) => (
                <Chip
                  key={index}
                  label={tag}
                  size="small"
                  variant="outlined"
                  sx={{ height: 24 }}
                />
              ))}
            </Box>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="caption" color="text.secondary">
              {template.usage.lastUsed
                ? `Last used ${format(parseISO(template.usage.lastUsed), 'MMM d, yyyy')}`
                : 'Never used'}
            </Typography>
            {mode === 'select' && (
              <Button
                size="small"
                variant="contained"
                onClick={(e) => {
                  e.stopPropagation();
                  if (onSelectTemplate) {
                    onSelectTemplate(template);
                  }
                }}
              >
                Use Template
              </Button>
            )}
          </Box>
        </CardContent>
      </Card>
    );
  };

  if (loading && templates.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  const filteredTemplates = getFilteredTemplates();
  const sortedTemplates = getSortedTemplates(filteredTemplates);

  return (
    <Box>
      {mode === 'manage' && (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h4" sx={{ fontWeight: 600 }}>
            Request Templates
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialog(true)}
          >
            Create Template
          </Button>
        </Box>
      )}

      {/* Search and Filters */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search templates..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <FormControl fullWidth size="small">
              <InputLabel>Category</InputLabel>
              <Select
                value={filterCategory}
                onChange={(e) => setFilterCategory(e.target.value)}
                label="Category"
              >
                <MenuItem value="all">All Categories</MenuItem>
                {categories.map(category => (
                  <MenuItem key={category.id} value={category.id}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      {category.icon}
                      {category.name}
                    </Box>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel>Sort By</InputLabel>
              <Select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as any)}
                label="Sort By"
              >
                <MenuItem value="usage">Most Used</MenuItem>
                <MenuItem value="name">Name</MenuItem>
                <MenuItem value="updated">Recently Updated</MenuItem>
                <MenuItem value="created">Date Created</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={3}>
            <FormControlLabel
              control={
                <Switch
                  checked={filterFavorites}
                  onChange={(e) => setFilterFavorites(e.target.checked)}
                />
              }
              label="Favorites Only"
            />
          </Grid>
        </Grid>
      </Paper>

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="All Templates" />
          <Tab label="My Templates" />
          <Tab label="Public Templates" />
          <Tab label="Favorites" />
        </Tabs>
      </Paper>

      {/* Template Grid */}
      {sortedTemplates.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <CategoryIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No templates found
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {searchQuery || filterCategory !== 'all' || filterFavorites
              ? 'Try adjusting your filters'
              : 'Create your first template to save time on recurring requests'}
          </Typography>
          {mode === 'manage' && !searchQuery && filterCategory === 'all' && !filterFavorites && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateDialog(true)}
            >
              Create Template
            </Button>
          )}
        </Paper>
      ) : (
        <Grid container spacing={3}>
          {sortedTemplates.map((template) => (
            <Grid item xs={12} sm={6} md={4} key={template.id}>
              {renderTemplateCard(template)}
            </Grid>
          ))}
        </Grid>
      )}

      {/* Floating Action Button */}
      {mode === 'manage' && (
        <Zoom in={true}>
          <Fab
            color="primary"
            sx={{ position: 'fixed', bottom: 16, right: 16 }}
            onClick={() => setCreateDialog(true)}
          >
            <AddIcon />
          </Fab>
        </Zoom>
      )}

      {/* Create/Edit Dialog */}
      <Dialog
        open={createDialog || editDialog}
        onClose={() => {
          setCreateDialog(false);
          setEditDialog(false);
          resetForm();
        }}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          {createDialog ? 'Create Template' : 'Edit Template'}
        </DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Template Name"
                value={templateForm.name}
                onChange={(e) => setTemplateForm({ ...templateForm, name: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Description"
                multiline
                rows={2}
                value={templateForm.description}
                onChange={(e) => setTemplateForm({ ...templateForm, description: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Category</InputLabel>
                <Select
                  value={templateForm.category}
                  onChange={(e) => setTemplateForm({ ...templateForm, category: e.target.value })}
                  label="Category"
                >
                  {categories.map(category => (
                    <MenuItem key={category.id} value={category.id}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {category.icon}
                        {category.name}
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Frequency</InputLabel>
                <Select
                  value={templateForm.frequency}
                  onChange={(e) => setTemplateForm({ ...templateForm, frequency: e.target.value as any })}
                  label="Frequency"
                >
                  <MenuItem value="once">One-time</MenuItem>
                  <MenuItem value="daily">Daily</MenuItem>
                  <MenuItem value="weekly">Weekly</MenuItem>
                  <MenuItem value="biweekly">Bi-weekly</MenuItem>
                  <MenuItem value="monthly">Monthly</MenuItem>
                  <MenuItem value="quarterly">Quarterly</MenuItem>
                  <MenuItem value="yearly">Yearly</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Default Amount (Optional)"
                type="number"
                value={templateForm.amount}
                onChange={(e) => setTemplateForm({ ...templateForm, amount: e.target.value })}
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Priority</InputLabel>
                <Select
                  value={templateForm.priority}
                  onChange={(e) => setTemplateForm({ ...templateForm, priority: e.target.value as any })}
                  label="Priority"
                >
                  <MenuItem value="low">Low</MenuItem>
                  <MenuItem value="normal">Normal</MenuItem>
                  <MenuItem value="high">High</MenuItem>
                  <MenuItem value="urgent">Urgent</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Tags (comma separated)
              </Typography>
              <TextField
                fullWidth
                placeholder="e.g., rent, monthly, utilities"
                value={templateForm.tags.join(', ')}
                onChange={(e) => setTemplateForm({
                  ...templateForm,
                  tags: e.target.value.split(',').map(tag => tag.trim()).filter(Boolean),
                })}
              />
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={
                  <Switch
                    checked={templateForm.reminderEnabled}
                    onChange={(e) => setTemplateForm({
                      ...templateForm,
                      reminderEnabled: e.target.checked,
                    })}
                  />
                }
                label="Enable automatic reminders"
              />
            </Grid>
            {templateForm.reminderEnabled && (
              <Grid item xs={12}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Send reminders (days before due)
                </Typography>
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  {[30, 14, 7, 3, 1].map((day) => (
                    <Chip
                      key={day}
                      label={`${day} day${day > 1 ? 's' : ''}`}
                      onClick={() => {
                        const days = templateForm.reminderDays;
                        if (days.includes(day)) {
                          setTemplateForm({
                            ...templateForm,
                            reminderDays: days.filter(d => d !== day),
                          });
                        } else {
                          setTemplateForm({
                            ...templateForm,
                            reminderDays: [...days, day].sort((a, b) => b - a),
                          });
                        }
                      }}
                      color={templateForm.reminderDays.includes(day) ? 'primary' : 'default'}
                      variant={templateForm.reminderDays.includes(day) ? 'filled' : 'outlined'}
                    />
                  ))}
                </Box>
              </Grid>
            )}
            <Grid item xs={12}>
              <FormControlLabel
                control={
                  <Switch
                    checked={templateForm.isPublic}
                    onChange={(e) => setTemplateForm({
                      ...templateForm,
                      isPublic: e.target.checked,
                    })}
                  />
                }
                label="Make this template public"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setCreateDialog(false);
            setEditDialog(false);
            resetForm();
          }}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={createDialog ? handleCreateTemplate : handleUpdateTemplate}
            disabled={!templateForm.name || loading}
          >
            {createDialog ? 'Create' : 'Update'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialog} onClose={() => setDeleteDialog(false)}>
        <DialogTitle>Delete Template</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete "{selectedTemplate?.name}"? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDeleteTemplate}
            disabled={loading}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem
          onClick={() => {
            if (selectedTemplate) {
              setTemplateForm({
                name: selectedTemplate.name,
                description: selectedTemplate.description || '',
                category: selectedTemplate.category,
                icon: selectedTemplate.icon,
                color: selectedTemplate.color,
                amount: selectedTemplate.amount?.toString() || '',
                currency: selectedTemplate.currency,
                frequency: selectedTemplate.frequency || 'once',
                priority: selectedTemplate.priority,
                tags: selectedTemplate.tags,
                recipientType: selectedTemplate.fields.recipientType || 'individual',
                dueInDays: selectedTemplate.fields.dueInDays || 7,
                expiresInDays: selectedTemplate.fields.expiresInDays || 30,
                reminderEnabled: selectedTemplate.fields.reminderEnabled || false,
                reminderDays: selectedTemplate.fields.reminderDays || [7, 3, 1],
                notes: selectedTemplate.fields.notes || '',
                isPublic: selectedTemplate.permissions.isPublic,
              });
              setEditDialog(true);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><EditIcon /></ListItemIcon>
          Edit
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (selectedTemplate) {
              handleDuplicateTemplate(selectedTemplate);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><DuplicateIcon /></ListItemIcon>
          Duplicate
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            setDeleteDialog(true);
            setMenuAnchor(null);
          }}
          sx={{ color: 'error.main' }}
        >
          <ListItemIcon><DeleteIcon sx={{ color: 'error.main' }} /></ListItemIcon>
          Delete
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default RequestTemplates;