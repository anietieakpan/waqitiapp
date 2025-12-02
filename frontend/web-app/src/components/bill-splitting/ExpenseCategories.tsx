import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Switch,
  FormControlLabel,
  Alert,
  CircularProgress,
  Paper,
  Divider,
  Chip,
  Avatar,
  Tooltip,
  Menu,
  MenuItem,
  useTheme,
  alpha,
  LinearProgress,
  Fab,
  Zoom,
  Badge,
  InputAdornment,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import MoreIcon from '@mui/icons-material/MoreVert';
import CategoryIcon from '@mui/icons-material/Category';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import DiningIcon from '@mui/icons-material/RestaurantMenu';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import TransportIcon from '@mui/icons-material/DirectionsCar';
import UtilitiesIcon from '@mui/icons-material/Home';
import GasIcon from '@mui/icons-material/LocalGasStation';
import TravelIcon from '@mui/icons-material/Flight';
import EntertainmentIcon from '@mui/icons-material/SportsEsports';
import HealthIcon from '@mui/icons-material/LocalHospital';
import EducationIcon from '@mui/icons-material/School';
import FitnessIcon from '@mui/icons-material/FitnessCenter';
import PetsIcon from '@mui/icons-material/Pets';
import BusinessIcon from '@mui/icons-material/Business';
import MoneyIcon from '@mui/icons-material/MonetizationOn';
import SettingsIcon from '@mui/icons-material/Settings';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import FilterIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import SearchIcon from '@mui/icons-material/Search';
import PaletteIcon from '@mui/icons-material/Palette';
import SaveIcon from '@mui/icons-material/Save';
import CloseIcon from '@mui/icons-material/Close';
import WarningIcon from '@mui/icons-material/Warning';
import CheckIcon from '@mui/icons-material/CheckCircle';
import InfoIcon from '@mui/icons-material/Info';;
import { format, subDays, subMonths, parseISO } from 'date-fns';
import { formatCurrency, formatPercentage } from '../../utils/formatters';
import toast from 'react-hot-toast';

interface ExpenseCategory {
  id: string;
  name: string;
  description?: string;
  icon: string;
  color: string;
  isActive: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
  usage: {
    totalExpenses: number;
    totalAmount: number;
    lastUsed?: string;
    frequency: number;
    trend: number; // percentage change from last period
  };
  budget?: {
    monthly: number;
    warning: number; // percentage threshold
    spent: number;
    remaining: number;
  };
  subcategories?: ExpenseSubcategory[];
}

interface ExpenseSubcategory {
  id: string;
  name: string;
  parentId: string;
  color?: string;
  isActive: boolean;
  usage: {
    totalExpenses: number;
    totalAmount: number;
  };
}

interface ExpenseCategoriesProps {
  groupId?: string;
  onCategorySelect?: (category: ExpenseCategory) => void;
  mode?: 'manage' | 'select';
}

const ExpenseCategories: React.FC<ExpenseCategoriesProps> = ({
  groupId,
  onCategorySelect,
  mode = 'manage',
}) => {
  const theme = useTheme();
  
  const [categories, setCategories] = useState<ExpenseCategory[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<ExpenseCategory | null>(null);
  const [loading, setLoading] = useState(false);
  const [createDialog, setCreateDialog] = useState(false);
  const [editDialog, setEditDialog] = useState(false);
  const [deleteDialog, setDeleteDialog] = useState(false);
  const [budgetDialog, setBudgetDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterActive, setFilterActive] = useState<boolean | null>(null);
  const [sortBy, setSortBy] = useState<'name' | 'usage' | 'amount' | 'trend'>('usage');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  // Form states
  const [categoryForm, setCategoryForm] = useState({
    name: '',
    description: '',
    icon: 'CategoryIcon',
    color: '#2196F3',
    isActive: true,
  });

  const [budgetForm, setBudgetForm] = useState({
    monthly: '',
    warning: '80',
  });

  // Predefined icon options
  const iconOptions = [
    { value: 'DiningIcon', label: 'Dining', component: DiningIcon },
    { value: 'ShoppingIcon', label: 'Shopping', component: ShoppingIcon },
    { value: 'TransportIcon', label: 'Transport', component: TransportIcon },
    { value: 'UtilitiesIcon', label: 'Utilities', component: UtilitiesIcon },
    { value: 'GasIcon', label: 'Gas', component: GasIcon },
    { value: 'TravelIcon', label: 'Travel', component: TravelIcon },
    { value: 'EntertainmentIcon', label: 'Entertainment', component: EntertainmentIcon },
    { value: 'HealthIcon', label: 'Health', component: HealthIcon },
    { value: 'EducationIcon', label: 'Education', component: EducationIcon },
    { value: 'FitnessIcon', label: 'Fitness', component: FitnessIcon },
    { value: 'PetsIcon', label: 'Pets', component: PetsIcon },
    { value: 'BusinessIcon', label: 'Business', component: BusinessIcon },
    { value: 'CategoryIcon', label: 'General', component: CategoryIcon },
  ];

  // Color palette
  const colorOptions = [
    '#F44336', '#E91E63', '#9C27B0', '#673AB7',
    '#3F51B5', '#2196F3', '#03A9F4', '#00BCD4',
    '#009688', '#4CAF50', '#8BC34A', '#CDDC39',
    '#FFEB3B', '#FFC107', '#FF9800', '#FF5722',
    '#795548', '#9E9E9E', '#607D8B', '#000000',
  ];

  // Mock data
  const mockCategories: ExpenseCategory[] = [
    {
      id: '1',
      name: 'Food & Dining',
      description: 'Restaurants, groceries, takeout',
      icon: 'DiningIcon',
      color: '#4CAF50',
      isActive: true,
      isDefault: true,
      createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
      usage: {
        totalExpenses: 45,
        totalAmount: 1256.78,
        lastUsed: new Date(Date.now() - 86400000).toISOString(),
        frequency: 0.35,
        trend: 12.5,
      },
      budget: {
        monthly: 800,
        warning: 80,
        spent: 650.25,
        remaining: 149.75,
      },
      subcategories: [
        {
          id: 'sub1',
          name: 'Restaurants',
          parentId: '1',
          isActive: true,
          usage: { totalExpenses: 25, totalAmount: 756.50 },
        },
        {
          id: 'sub2',
          name: 'Groceries',
          parentId: '1',
          isActive: true,
          usage: { totalExpenses: 20, totalAmount: 500.28 },
        },
      ],
    },
    {
      id: '2',
      name: 'Transportation',
      description: 'Gas, public transport, rideshare',
      icon: 'TransportIcon',
      color: '#2196F3',
      isActive: true,
      isDefault: true,
      createdAt: new Date(Date.now() - 86400000 * 25).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      usage: {
        totalExpenses: 22,
        totalAmount: 456.30,
        lastUsed: new Date(Date.now() - 86400000 * 2).toISOString(),
        frequency: 0.17,
        trend: -8.2,
      },
      budget: {
        monthly: 300,
        warning: 75,
        spent: 245.80,
        remaining: 54.20,
      },
    },
    {
      id: '3',
      name: 'Utilities',
      description: 'Electricity, water, internet, phone',
      icon: 'UtilitiesIcon',
      color: '#FF9800',
      isActive: true,
      isDefault: true,
      createdAt: new Date(Date.now() - 86400000 * 20).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 3).toISOString(),
      usage: {
        totalExpenses: 8,
        totalAmount: 384.50,
        lastUsed: new Date(Date.now() - 86400000 * 5).toISOString(),
        frequency: 0.06,
        trend: 3.1,
      },
    },
    {
      id: '4',
      name: 'Entertainment',
      description: 'Movies, games, subscriptions',
      icon: 'EntertainmentIcon',
      color: '#E91E63',
      isActive: true,
      isDefault: false,
      createdAt: new Date(Date.now() - 86400000 * 15).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      usage: {
        totalExpenses: 15,
        totalAmount: 234.60,
        lastUsed: new Date(Date.now() - 86400000 * 1).toISOString(),
        frequency: 0.12,
        trend: 25.7,
      },
    },
    {
      id: '5',
      name: 'Health & Fitness',
      description: 'Medical expenses, gym, supplements',
      icon: 'HealthIcon',
      color: '#9C27B0',
      isActive: false,
      isDefault: false,
      createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 8).toISOString(),
      usage: {
        totalExpenses: 3,
        totalAmount: 125.00,
        lastUsed: new Date(Date.now() - 86400000 * 8).toISOString(),
        frequency: 0.02,
        trend: -15.3,
      },
    },
  ];

  useEffect(() => {
    loadCategories();
  }, [groupId]);

  const loadCategories = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      setCategories(mockCategories);
    } catch (error) {
      toast.error('Failed to load categories');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateCategory = async () => {
    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const newCategory: ExpenseCategory = {
        id: `cat_${Date.now()}`,
        ...categoryForm,
        isDefault: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        usage: {
          totalExpenses: 0,
          totalAmount: 0,
          frequency: 0,
          trend: 0,
        },
      };

      setCategories([...categories, newCategory]);
      setCreateDialog(false);
      setCategoryForm({
        name: '',
        description: '',
        icon: 'CategoryIcon',
        color: '#2196F3',
        isActive: true,
      });
      toast.success('Category created successfully');
    } catch (error) {
      toast.error('Failed to create category');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateCategory = async () => {
    if (!selectedCategory) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const updatedCategory = {
        ...selectedCategory,
        ...categoryForm,
        updatedAt: new Date().toISOString(),
      };

      setCategories(categories.map(cat => 
        cat.id === selectedCategory.id ? updatedCategory : cat
      ));
      setEditDialog(false);
      setSelectedCategory(null);
      toast.success('Category updated successfully');
    } catch (error) {
      toast.error('Failed to update category');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteCategory = async () => {
    if (!selectedCategory) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      setCategories(categories.filter(cat => cat.id !== selectedCategory.id));
      setDeleteDialog(false);
      setSelectedCategory(null);
      toast.success('Category deleted successfully');
    } catch (error) {
      toast.error('Failed to delete category');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateBudget = async () => {
    if (!selectedCategory) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const monthlyBudget = parseFloat(budgetForm.monthly);
      const warningThreshold = parseFloat(budgetForm.warning);
      const spent = selectedCategory.budget?.spent || 0;

      const updatedCategory = {
        ...selectedCategory,
        budget: {
          monthly: monthlyBudget,
          warning: warningThreshold,
          spent,
          remaining: monthlyBudget - spent,
        },
        updatedAt: new Date().toISOString(),
      };

      setCategories(categories.map(cat => 
        cat.id === selectedCategory.id ? updatedCategory : cat
      ));
      setBudgetDialog(false);
      setSelectedCategory(null);
      setBudgetForm({ monthly: '', warning: '80' });
      toast.success('Budget updated successfully');
    } catch (error) {
      toast.error('Failed to update budget');
    } finally {
      setLoading(false);
    }
  };

  const toggleCategoryStatus = async (category: ExpenseCategory) => {
    try {
      const updatedCategory = {
        ...category,
        isActive: !category.isActive,
        updatedAt: new Date().toISOString(),
      };

      setCategories(categories.map(cat => 
        cat.id === category.id ? updatedCategory : cat
      ));
      toast.success(`Category ${updatedCategory.isActive ? 'activated' : 'deactivated'}`);
    } catch (error) {
      toast.error('Failed to update category status');
    }
  };

  const getIconComponent = (iconName: string) => {
    const iconOption = iconOptions.find(opt => opt.value === iconName);
    return iconOption ? iconOption.component : CategoryIcon;
  };

  const getFilteredCategories = () => {
    return categories.filter(category => {
      const matchesSearch = category.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           category.description?.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesFilter = filterActive === null || category.isActive === filterActive;
      
      return matchesSearch && matchesFilter;
    });
  };

  const getSortedCategories = (filteredCategories: ExpenseCategory[]) => {
    return [...filteredCategories].sort((a, b) => {
      switch (sortBy) {
        case 'name':
          return a.name.localeCompare(b.name);
        case 'usage':
          return b.usage.totalExpenses - a.usage.totalExpenses;
        case 'amount':
          return b.usage.totalAmount - a.usage.totalAmount;
        case 'trend':
          return b.usage.trend - a.usage.trend;
        default:
          return 0;
      }
    });
  };

  const getBudgetStatus = (category: ExpenseCategory) => {
    if (!category.budget) return null;
    
    const percentage = (category.budget.spent / category.budget.monthly) * 100;
    
    if (percentage >= 100) {
      return { status: 'over', color: 'error', icon: WarningIcon };
    } else if (percentage >= category.budget.warning) {
      return { status: 'warning', color: 'warning', icon: WarningIcon };
    } else {
      return { status: 'good', color: 'success', icon: CheckIcon };
    }
  };

  const renderCategoryCard = (category: ExpenseCategory) => {
    const IconComponent = getIconComponent(category.icon);
    const budgetStatus = getBudgetStatus(category);
    const budgetPercentage = category.budget 
      ? Math.min((category.budget.spent / category.budget.monthly) * 100, 100)
      : 0;

    return (
      <Card
        key={category.id}
        sx={{
          cursor: mode === 'select' ? 'pointer' : 'default',
          transition: 'all 0.2s',
          opacity: category.isActive ? 1 : 0.6,
          '&:hover': {
            transform: mode === 'select' ? 'translateY(-2px)' : 'none',
            boxShadow: theme.shadows[4],
          },
        }}
        onClick={() => {
          if (mode === 'select' && onCategorySelect) {
            onCategorySelect(category);
          }
        }}
      >
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar
                sx={{
                  bgcolor: category.color,
                  color: 'white',
                  width: 48,
                  height: 48,
                }}
              >
                <IconComponent />
              </Avatar>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {category.name}
                </Typography>
                {category.description && (
                  <Typography variant="body2" color="text.secondary">
                    {category.description}
                  </Typography>
                )}
              </Box>
            </Box>
            
            {mode === 'manage' && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {category.isDefault && (
                  <Chip label="Default" size="small" color="primary" />
                )}
                <IconButton
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedCategory(category);
                    setMenuAnchor(e.currentTarget);
                  }}
                >
                  <MoreIcon />
                </IconButton>
              </Box>
            )}
          </Box>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">
                Expenses
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                {category.usage.totalExpenses}
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">
                Amount
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                {formatCurrency(category.usage.totalAmount)}
              </Typography>
            </Grid>
          </Grid>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Trend:
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              {category.usage.trend > 0 ? (
                <TrendingUpIcon sx={{ fontSize: 16, color: 'success.main' }} />
              ) : (
                <TrendingDownIcon sx={{ fontSize: 16, color: 'error.main' }} />
              )}
              <Typography
                variant="body2"
                sx={{
                  color: category.usage.trend > 0 ? 'success.main' : 'error.main',
                  fontWeight: 600,
                }}
              >
                {Math.abs(category.usage.trend).toFixed(1)}%
              </Typography>
            </Box>
          </Box>

          {category.budget && (
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Budget
                </Typography>
                {budgetStatus && (
                  <Chip
                    size="small"
                    label={budgetStatus.status}
                    color={budgetStatus.color}
                    icon={<budgetStatus.icon />}
                  />
                )}
              </Box>
              <LinearProgress
                variant="determinate"
                value={budgetPercentage}
                sx={{
                  height: 8,
                  borderRadius: 4,
                  bgcolor: alpha(category.color, 0.1),
                  '& .MuiLinearProgress-bar': {
                    bgcolor: budgetPercentage >= 100 ? theme.palette.error.main : category.color,
                  },
                }}
              />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {formatCurrency(category.budget.spent)} spent
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatCurrency(category.budget.monthly)} budget
                </Typography>
              </Box>
            </Box>
          )}

          {category.usage.lastUsed && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              Last used: {format(parseISO(category.usage.lastUsed), 'MMM d, yyyy')}
            </Typography>
          )}
        </CardContent>
      </Card>
    );
  };

  const renderCategoryList = (category: ExpenseCategory) => {
    const IconComponent = getIconComponent(category.icon);
    const budgetStatus = getBudgetStatus(category);

    return (
      <ListItem
        key={category.id}
        sx={{
          cursor: mode === 'select' ? 'pointer' : 'default',
          opacity: category.isActive ? 1 : 0.6,
        }}
        onClick={() => {
          if (mode === 'select' && onCategorySelect) {
            onCategorySelect(category);
          }
        }}
      >
        <ListItemIcon>
          <Avatar
            sx={{
              bgcolor: category.color,
              color: 'white',
              width: 40,
              height: 40,
            }}
          >
            <IconComponent />
          </Avatar>
        </ListItemIcon>
        <ListItemText
          primary={
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                {category.name}
              </Typography>
              {category.isDefault && (
                <Chip label="Default" size="small" color="primary" />
              )}
              {budgetStatus && (
                <Chip
                  size="small"
                  label={budgetStatus.status}
                  color={budgetStatus.color}
                  icon={<budgetStatus.icon />}
                />
              )}
            </Box>
          }
          secondary={
            <Box>
              {category.description && (
                <Typography variant="body2" color="text.secondary">
                  {category.description}
                </Typography>
              )}
              <Box sx={{ display: 'flex', gap: 3, mt: 1 }}>
                <Typography variant="caption">
                  {category.usage.totalExpenses} expenses
                </Typography>
                <Typography variant="caption">
                  {formatCurrency(category.usage.totalAmount)}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{
                    color: category.usage.trend > 0 ? 'success.main' : 'error.main',
                  }}
                >
                  {category.usage.trend > 0 ? '+' : ''}{category.usage.trend.toFixed(1)}%
                </Typography>
              </Box>
            </Box>
          }
        />
        {mode === 'manage' && (
          <ListItemSecondaryAction>
            <IconButton
              onClick={(e) => {
                e.stopPropagation();
                setSelectedCategory(category);
                setMenuAnchor(e.currentTarget);
              }}
            >
              <MoreIcon />
            </IconButton>
          </ListItemSecondaryAction>
        )}
      </ListItem>
    );
  };

  if (loading && categories.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  const filteredCategories = getFilteredCategories();
  const sortedCategories = getSortedCategories(filteredCategories);

  return (
    <Box>
      {mode === 'manage' && (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h4" sx={{ fontWeight: 600 }}>
            Expense Categories
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialog(true)}
          >
            Create Category
          </Button>
        </Box>
      )}

      {/* Search and Filter Controls */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search categories..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
              }}
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              select
              label="Status"
              value={filterActive === null ? 'all' : filterActive ? 'active' : 'inactive'}
              onChange={(e) => {
                const value = e.target.value;
                setFilterActive(value === 'all' ? null : value === 'active');
              }}
              SelectProps={{ native: true }}
            >
              <option value="all">All</option>
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              select
              label="Sort by"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              SelectProps={{ native: true }}
            >
              <option value="usage">Usage</option>
              <option value="name">Name</option>
              <option value="amount">Amount</option>
              <option value="trend">Trend</option>
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
              <Tooltip title="Grid View">
                <IconButton
                  onClick={() => setViewMode('grid')}
                  color={viewMode === 'grid' ? 'primary' : 'default'}
                >
                  <CategoryIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="List View">
                <IconButton
                  onClick={() => setViewMode('list')}
                  color={viewMode === 'list' ? 'primary' : 'default'}
                >
                  <FilterIcon />
                </IconButton>
              </Tooltip>
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {/* Categories Display */}
      {sortedCategories.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <CategoryIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            {searchQuery || filterActive !== null ? 'No categories found' : 'No categories yet'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {searchQuery || filterActive !== null
              ? 'Try adjusting your search or filter criteria'
              : 'Create your first expense category to get started'
            }
          </Typography>
          {!searchQuery && filterActive === null && mode === 'manage' && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateDialog(true)}
            >
              Create Category
            </Button>
          )}
        </Paper>
      ) : viewMode === 'grid' ? (
        <Grid container spacing={3}>
          {sortedCategories.map((category) => (
            <Grid item xs={12} sm={6} md={4} key={category.id}>
              {renderCategoryCard(category)}
            </Grid>
          ))}
        </Grid>
      ) : (
        <Paper>
          <List>
            {sortedCategories.map((category, index) => (
              <React.Fragment key={category.id}>
                {index > 0 && <Divider />}
                {renderCategoryList(category)}
              </React.Fragment>
            ))}
          </List>
        </Paper>
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

      {/* Create Category Dialog */}
      <Dialog open={createDialog} onClose={() => setCreateDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle>Create New Category</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Category Name"
            value={categoryForm.name}
            onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })}
            margin="normal"
            required
          />
          <TextField
            fullWidth
            label="Description"
            value={categoryForm.description}
            onChange={(e) => setCategoryForm({ ...categoryForm, description: e.target.value })}
            margin="normal"
            multiline
            rows={2}
          />

          {/* Icon Selection */}
          <Typography variant="subtitle2" sx={{ mt: 3, mb: 2 }}>
            Choose Icon
          </Typography>
          <Grid container spacing={1}>
            {iconOptions.map((icon) => {
              const IconComponent = icon.component;
              return (
                <Grid item key={icon.value}>
                  <IconButton
                    onClick={() => setCategoryForm({ ...categoryForm, icon: icon.value })}
                    sx={{
                      border: categoryForm.icon === icon.value ? 2 : 1,
                      borderColor: categoryForm.icon === icon.value ? 'primary.main' : 'divider',
                      bgcolor: categoryForm.icon === icon.value ? alpha(theme.palette.primary.main, 0.1) : 'transparent',
                    }}
                  >
                    <IconComponent />
                  </IconButton>
                </Grid>
              );
            })}
          </Grid>

          {/* Color Selection */}
          <Typography variant="subtitle2" sx={{ mt: 3, mb: 2 }}>
            Choose Color
          </Typography>
          <Grid container spacing={1}>
            {colorOptions.map((color) => (
              <Grid item key={color}>
                <IconButton
                  onClick={() => setCategoryForm({ ...categoryForm, color })}
                  sx={{
                    width: 40,
                    height: 40,
                    bgcolor: color,
                    border: categoryForm.color === color ? 3 : 1,
                    borderColor: categoryForm.color === color ? 'primary.main' : 'divider',
                    '&:hover': { bgcolor: color },
                  }}
                >
                  {categoryForm.color === color && <CheckIcon sx={{ color: 'white' }} />}
                </IconButton>
              </Grid>
            ))}
          </Grid>

          <FormControlLabel
            control={
              <Switch
                checked={categoryForm.isActive}
                onChange={(e) => setCategoryForm({ ...categoryForm, isActive: e.target.checked })}
              />
            }
            label="Active"
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreateCategory}
            disabled={!categoryForm.name || loading}
          >
            Create Category
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Category Dialog */}
      <Dialog open={editDialog} onClose={() => setEditDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle>Edit Category</DialogTitle>
        <DialogContent>
          {/* Same form as create dialog */}
          <TextField
            fullWidth
            label="Category Name"
            value={categoryForm.name}
            onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })}
            margin="normal"
            required
          />
          {/* ... rest of the form fields */}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleUpdateCategory}
            disabled={!categoryForm.name || loading}
          >
            Update Category
          </Button>
        </DialogActions>
      </Dialog>

      {/* Budget Dialog */}
      <Dialog open={budgetDialog} onClose={() => setBudgetDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Set Budget</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Monthly Budget"
            type="number"
            value={budgetForm.monthly}
            onChange={(e) => setBudgetForm({ ...budgetForm, monthly: e.target.value })}
            margin="normal"
            InputProps={{
              startAdornment: <InputAdornment position="start">$</InputAdornment>,
            }}
            required
          />
          <TextField
            fullWidth
            label="Warning Threshold"
            type="number"
            value={budgetForm.warning}
            onChange={(e) => setBudgetForm({ ...budgetForm, warning: e.target.value })}
            margin="normal"
            InputProps={{
              endAdornment: <InputAdornment position="end">%</InputAdornment>,
            }}
            helperText="Get notified when spending reaches this percentage"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBudgetDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleUpdateBudget}
            disabled={!budgetForm.monthly || loading}
          >
            Set Budget
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialog} onClose={() => setDeleteDialog(false)}>
        <DialogTitle>Delete Category</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete "{selectedCategory?.name}"? This action cannot be undone.
            {selectedCategory?.usage.totalExpenses > 0 && (
              <Alert severity="warning" sx={{ mt: 2 }}>
                This category has {selectedCategory.usage.totalExpenses} expenses associated with it.
              </Alert>
            )}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDeleteCategory}
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
            if (selectedCategory) {
              setCategoryForm({
                name: selectedCategory.name,
                description: selectedCategory.description || '',
                icon: selectedCategory.icon,
                color: selectedCategory.color,
                isActive: selectedCategory.isActive,
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
            if (selectedCategory) {
              setBudgetForm({
                monthly: selectedCategory.budget?.monthly.toString() || '',
                warning: selectedCategory.budget?.warning.toString() || '80',
              });
              setBudgetDialog(true);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><MoneyIcon /></ListItemIcon>
          Set Budget
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (selectedCategory) {
              toggleCategoryStatus(selectedCategory);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon>
            {selectedCategory?.isActive ? <VisibilityOffIcon /> : <VisibilityIcon />}
          </ListItemIcon>
          {selectedCategory?.isActive ? 'Deactivate' : 'Activate'}
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            setDeleteDialog(true);
            setMenuAnchor(null);
          }}
          disabled={selectedCategory?.isDefault}
        >
          <ListItemIcon><DeleteIcon /></ListItemIcon>
          Delete
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default ExpenseCategories;