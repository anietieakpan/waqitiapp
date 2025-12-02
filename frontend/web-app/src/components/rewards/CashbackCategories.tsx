import React, { useState, useMemo } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  CardActions,
  Button,
  IconButton,
  Chip,
  Avatar,
  TextField,
  InputAdornment,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
  LinearProgress,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  ToggleButton,
  ToggleButtonGroup,
  Badge,
  Tooltip,
  Switch,
  FormControlLabel,
  Slider,
  useTheme,
  alpha,
} from '@mui/material';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import LocalGroceryStoreIcon from '@mui/icons-material/LocalGroceryStore';
import LocalGasStationIcon from '@mui/icons-material/LocalGasStation';
import FlightTakeoffIcon from '@mui/icons-material/FlightTakeoff';
import HotelIcon from '@mui/icons-material/Hotel';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import MovieIcon from '@mui/icons-material/Movie';
import FitnessCenterIcon from '@mui/icons-material/FitnessCenter';
import SchoolIcon from '@mui/icons-material/School';
import LocalPharmacyIcon from '@mui/icons-material/LocalPharmacy';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import SubscriptionsIcon from '@mui/icons-material/Subscriptions';
import ChildCareIcon from '@mui/icons-material/ChildCare';
import PetsIcon from '@mui/icons-material/Pets';
import HomeIcon from '@mui/icons-material/Home';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import StarIcon from '@mui/icons-material/Star';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import ScheduleIcon from '@mui/icons-material/Schedule';
import BoltIcon from '@mui/icons-material/Bolt';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import MapIcon from '@mui/icons-material/Map';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CreditCardIcon from '@mui/icons-material/CreditCard';;
import { format, addDays, isWithinInterval, startOfMonth, endOfMonth } from 'date-fns';

interface CashbackCategory {
  id: string;
  name: string;
  icon: React.ReactNode;
  baseRate: number;
  bonusRate?: number;
  merchants: CashbackMerchant[];
  totalSpent: number;
  totalEarned: number;
  isRotating?: boolean;
  rotationEndDate?: string;
  color: string;
}

interface CashbackMerchant {
  id: string;
  name: string;
  logo?: string;
  rate: number;
  bonusRate?: number;
  location?: string;
  distance?: number;
  isOnline: boolean;
  isFavorite: boolean;
  specialOffer?: {
    description: string;
    validUntil: string;
  };
  restrictions?: string[];
  activated: boolean;
}

interface RotatingCategory {
  quarter: string;
  categories: string[];
  startDate: string;
  endDate: string;
  maxEarnings: number;
}

interface SpendingInsight {
  category: string;
  averageMonthly: number;
  trend: 'up' | 'down' | 'stable';
  percentOfTotal: number;
  projectedEarnings: number;
}

const mockCategories: CashbackCategory[] = [
  {
    id: '1',
    name: 'Dining',
    icon: <Restaurant />,
    baseRate: 3,
    bonusRate: 5,
    merchants: [
      {
        id: 'm1',
        name: 'Chipotle',
        rate: 3,
        bonusRate: 5,
        location: '2.3 mi',
        distance: 2.3,
        isOnline: true,
        isFavorite: true,
        activated: true,
        specialOffer: {
          description: 'Extra 2% on orders over $30',
          validUntil: '2024-01-31',
        },
      },
      {
        id: 'm2',
        name: 'Starbucks',
        rate: 3,
        location: '0.5 mi',
        distance: 0.5,
        isOnline: true,
        isFavorite: false,
        activated: true,
      },
    ],
    totalSpent: 450.25,
    totalEarned: 22.51,
    isRotating: true,
    rotationEndDate: '2024-03-31',
    color: '#FF6B6B',
  },
  {
    id: '2',
    name: 'Groceries',
    icon: <LocalGroceryStore />,
    baseRate: 2,
    merchants: [
      {
        id: 'm3',
        name: 'Whole Foods',
        rate: 2,
        bonusRate: 4,
        location: '1.8 mi',
        distance: 1.8,
        isOnline: true,
        isFavorite: true,
        activated: true,
        restrictions: ['Excludes alcohol', 'Excludes gift cards'],
      },
      {
        id: 'm4',
        name: 'Trader Joe\'s',
        rate: 2,
        location: '3.2 mi',
        distance: 3.2,
        isOnline: false,
        isFavorite: false,
        activated: false,
      },
    ],
    totalSpent: 823.45,
    totalEarned: 16.47,
    color: '#4ECDC4',
  },
  {
    id: '3',
    name: 'Gas',
    icon: <LocalGasStation />,
    baseRate: 2,
    merchants: [
      {
        id: 'm5',
        name: 'Shell',
        rate: 2,
        bonusRate: 3,
        location: '0.8 mi',
        distance: 0.8,
        isOnline: false,
        isFavorite: false,
        activated: true,
        specialOffer: {
          description: 'Double points on premium gas',
          validUntil: '2024-02-15',
        },
      },
    ],
    totalSpent: 245.00,
    totalEarned: 7.35,
    color: '#FFE66D',
  },
  {
    id: '4',
    name: 'Travel',
    icon: <FlightTakeoff />,
    baseRate: 5,
    merchants: [
      {
        id: 'm6',
        name: 'United Airlines',
        rate: 5,
        isOnline: true,
        isFavorite: false,
        activated: true,
      },
      {
        id: 'm7',
        name: 'Marriott',
        rate: 5,
        bonusRate: 8,
        isOnline: true,
        isFavorite: true,
        activated: true,
        specialOffer: {
          description: 'Triple points on weekend stays',
          validUntil: '2024-04-30',
        },
      },
    ],
    totalSpent: 1250.00,
    totalEarned: 62.50,
    color: '#6C5CE7',
  },
];

const mockRotatingCategories: RotatingCategory[] = [
  {
    quarter: 'Q1 2024',
    categories: ['Dining', 'Entertainment'],
    startDate: '2024-01-01',
    endDate: '2024-03-31',
    maxEarnings: 75,
  },
  {
    quarter: 'Q2 2024',
    categories: ['Gas', 'Streaming'],
    startDate: '2024-04-01',
    endDate: '2024-06-30',
    maxEarnings: 75,
  },
];

const mockSpendingInsights: SpendingInsight[] = [
  {
    category: 'Dining',
    averageMonthly: 450,
    trend: 'up',
    percentOfTotal: 28,
    projectedEarnings: 22.50,
  },
  {
    category: 'Groceries',
    averageMonthly: 800,
    trend: 'stable',
    percentOfTotal: 35,
    projectedEarnings: 16.00,
  },
  {
    category: 'Gas',
    averageMonthly: 250,
    trend: 'down',
    percentOfTotal: 12,
    projectedEarnings: 5.00,
  },
];

const CashbackCategories: React.FC = () => {
  const theme = useTheme();
  const [selectedCategory, setSelectedCategory] = useState<CashbackCategory | null>(null);
  const [showMerchantDialog, setShowMerchantDialog] = useState(false);
  const [selectedMerchant, setSelectedMerchant] = useState<CashbackMerchant | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterMode, setFilterMode] = useState<'all' | 'nearby' | 'online'>('all');
  const [sortBy, setSortBy] = useState<'rate' | 'distance' | 'name'>('rate');
  const [showOnlyActivated, setShowOnlyActivated] = useState(false);
  const [maxDistance, setMaxDistance] = useState(10);

  const currentQuarter = mockRotatingCategories.find(q => 
    isWithinInterval(new Date(), {
      start: new Date(q.startDate),
      end: new Date(q.endDate),
    })
  );

  const filteredMerchants = useMemo(() => {
    if (!selectedCategory) return [];

    let merchants = [...selectedCategory.merchants];

    // Apply search filter
    if (searchTerm) {
      merchants = merchants.filter(m => 
        m.name.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    // Apply mode filter
    if (filterMode === 'nearby') {
      merchants = merchants.filter(m => m.distance && m.distance <= maxDistance);
    } else if (filterMode === 'online') {
      merchants = merchants.filter(m => m.isOnline);
    }

    // Apply activation filter
    if (showOnlyActivated) {
      merchants = merchants.filter(m => m.activated);
    }

    // Sort
    merchants.sort((a, b) => {
      switch (sortBy) {
        case 'rate':
          return (b.bonusRate || b.rate) - (a.bonusRate || a.rate);
        case 'distance':
          return (a.distance || 999) - (b.distance || 999);
        case 'name':
          return a.name.localeCompare(b.name);
        default:
          return 0;
      }
    });

    return merchants;
  }, [selectedCategory, searchTerm, filterMode, sortBy, showOnlyActivated, maxDistance]);

  const totalCashbackEarned = mockCategories.reduce((sum, cat) => sum + cat.totalEarned, 0);
  const totalSpent = mockCategories.reduce((sum, cat) => sum + cat.totalSpent, 0);
  const averageRate = totalSpent > 0 ? (totalCashbackEarned / totalSpent) * 100 : 0;

  const renderCategoryCard = (category: CashbackCategory) => {
    const effectiveRate = category.bonusRate || category.baseRate;
    const activatedCount = category.merchants.filter(m => m.activated).length;

    return (
      <Card
        key={category.id}
        sx={{
          cursor: 'pointer',
          transition: 'all 0.3s',
          '&:hover': {
            transform: 'translateY(-4px)',
            boxShadow: theme.shadows[8],
          },
        }}
        onClick={() => {
          setSelectedCategory(category);
          setShowMerchantDialog(true);
        }}
      >
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
            <Box display="flex" alignItems="center" gap={2}>
              <Avatar
                sx={{
                  bgcolor: alpha(category.color, 0.2),
                  color: category.color,
                  width: 56,
                  height: 56,
                }}
              >
                {category.icon}
              </Avatar>
              <Box>
                <Typography variant="h6" fontWeight="bold">
                  {category.name}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {category.merchants.length} merchants
                </Typography>
              </Box>
            </Box>
            {category.isRotating && (
              <Chip
                label="Rotating"
                size="small"
                color="error"
                icon={<AutoAwesome />}
              />
            )}
          </Box>

          <Box display="flex" alignItems="baseline" gap={1} mb={2}>
            <Typography variant="h4" fontWeight="bold" color="primary">
              {effectiveRate}%
            </Typography>
            <Typography variant="body2" color="text.secondary">
              cashback
            </Typography>
            {category.bonusRate && (
              <Chip
                label={`+${category.bonusRate - category.baseRate}% bonus`}
                size="small"
                color="success"
              />
            )}
          </Box>

          <Grid container spacing={1}>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary">
                This Month
              </Typography>
              <Typography variant="body2" fontWeight="bold">
                ${category.totalSpent.toFixed(2)}
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary">
                Earned
              </Typography>
              <Typography variant="body2" fontWeight="bold" color="success.main">
                +${category.totalEarned.toFixed(2)}
              </Typography>
            </Grid>
          </Grid>

          <Divider sx={{ my: 2 }} />

          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Stack direction="row" spacing={0.5}>
              {category.merchants.slice(0, 3).map((merchant) => (
                <Tooltip key={merchant.id} title={merchant.name}>
                  <Avatar sx={{ width: 24, height: 24, fontSize: 10 }}>
                    {merchant.name.charAt(0)}
                  </Avatar>
                </Tooltip>
              ))}
              {category.merchants.length > 3 && (
                <Avatar sx={{ width: 24, height: 24, fontSize: 10, bgcolor: 'grey.300' }}>
                  +{category.merchants.length - 3}
                </Avatar>
              )}
            </Stack>
            <Typography variant="caption" color="text.secondary">
              {activatedCount}/{category.merchants.length} active
            </Typography>
          </Box>

          {category.rotationEndDate && (
            <Alert severity="warning" sx={{ mt: 2, py: 0.5 }}>
              <Typography variant="caption">
                Bonus ends {format(new Date(category.rotationEndDate), 'MMM dd')}
              </Typography>
            </Alert>
          )}
        </CardContent>
      </Card>
    );
  };

  const renderMerchantItem = (merchant: CashbackMerchant) => (
    <Card key={merchant.id} variant="outlined" sx={{ mb: 2 }}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Box display="flex" gap={2} flex={1}>
            <Avatar sx={{ bgcolor: theme.palette.grey[200], width: 48, height: 48 }}>
              {merchant.name.charAt(0)}
            </Avatar>
            <Box flex={1}>
              <Box display="flex" alignItems="center" gap={1} mb={0.5}>
                <Typography variant="subtitle1" fontWeight="bold">
                  {merchant.name}
                </Typography>
                {merchant.isFavorite && <Star color="warning" fontSize="small" />}
                {merchant.activated && <CheckCircle color="success" fontSize="small" />}
              </Box>

              <Stack direction="row" spacing={1} mb={1}>
                <Chip
                  label={`${merchant.bonusRate || merchant.rate}% cashback`}
                  size="small"
                  color="primary"
                />
                {merchant.location && (
                  <Chip
                    label={merchant.location}
                    size="small"
                    icon={<LocationOn />}
                    variant="outlined"
                  />
                )}
                {merchant.isOnline && (
                  <Chip
                    label="Online"
                    size="small"
                    variant="outlined"
                  />
                )}
              </Stack>

              {merchant.specialOffer && (
                <Alert severity="success" sx={{ py: 0.5, mb: 1 }}>
                  <Typography variant="caption">
                    {merchant.specialOffer.description} - Ends {format(new Date(merchant.specialOffer.validUntil), 'MMM dd')}
                  </Typography>
                </Alert>
              )}

              {merchant.restrictions && merchant.restrictions.length > 0 && (
                <Box display="flex" alignItems="center" gap={0.5}>
                  <Info fontSize="small" color="action" />
                  <Typography variant="caption" color="text.secondary">
                    {merchant.restrictions.join(', ')}
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>

          <Stack direction="row" spacing={1}>
            <IconButton
              onClick={(e) => {
                e.stopPropagation();
                // Toggle favorite
              }}
              size="small"
            >
              {merchant.isFavorite ? <Favorite color="error" /> : <FavoriteBorder />}
            </IconButton>
            <Switch
              checked={merchant.activated}
              onChange={(e) => {
                e.stopPropagation();
                // Toggle activation
              }}
              size="small"
            />
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Cashback Categories
        </Typography>
        <Stack direction="row" spacing={2}>
          <Chip
            label={`${averageRate.toFixed(1)}% avg rate`}
            icon={<TrendingUp />}
            color="success"
          />
          <Button variant="outlined" startIcon={<Map />}>
            Nearby Offers
          </Button>
        </Stack>
      </Box>

      {/* Summary Stats */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Current Quarter Bonuses
              </Typography>
              {currentQuarter && (
                <Box>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                    <Box>
                      <Typography variant="subtitle1" fontWeight="bold">
                        {currentQuarter.quarter} Rotating Categories
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Earn 5% cashback on select categories
                      </Typography>
                    </Box>
                    <Chip
                      label={`Max $${currentQuarter.maxEarnings} cashback`}
                      color="primary"
                    />
                  </Box>
                  <Stack direction="row" spacing={2}>
                    {currentQuarter.categories.map((cat) => (
                      <Chip
                        key={cat}
                        label={cat}
                        icon={<Bolt />}
                        sx={{
                          bgcolor: alpha(theme.palette.warning.main, 0.1),
                          color: theme.palette.warning.dark,
                        }}
                      />
                    ))}
                  </Stack>
                  <LinearProgress
                    variant="determinate"
                    value={75}
                    sx={{
                      mt: 2,
                      height: 8,
                      borderRadius: 4,
                      backgroundColor: alpha(theme.palette.primary.main, 0.2),
                      '& .MuiLinearProgress-bar': {
                        borderRadius: 4,
                      },
                    }}
                  />
                  <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                    $56.25 of $75 max bonus earned
                  </Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                This Month's Summary
              </Typography>
              <Stack spacing={2}>
                <Box display="flex" justifyContent="space-between">
                  <Typography variant="body2" color="text.secondary">
                    Total Spent
                  </Typography>
                  <Typography variant="body2" fontWeight="bold">
                    ${totalSpent.toFixed(2)}
                  </Typography>
                </Box>
                <Box display="flex" justifyContent="space-between">
                  <Typography variant="body2" color="text.secondary">
                    Cashback Earned
                  </Typography>
                  <Typography variant="body2" fontWeight="bold" color="success.main">
                    +${totalCashbackEarned.toFixed(2)}
                  </Typography>
                </Box>
                <Divider />
                <Box display="flex" justifyContent="space-between">
                  <Typography variant="body2" color="text.secondary">
                    Effective Rate
                  </Typography>
                  <Typography variant="body2" fontWeight="bold" color="primary">
                    {averageRate.toFixed(2)}%
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Categories Grid */}
      <Typography variant="h6" gutterBottom>
        All Categories
      </Typography>
      <Grid container spacing={3} mb={3}>
        {mockCategories.map((category) => (
          <Grid item xs={12} sm={6} md={4} key={category.id}>
            {renderCategoryCard(category)}
          </Grid>
        ))}
      </Grid>

      {/* Spending Insights */}
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          Spending Insights
        </Typography>
        <Grid container spacing={3}>
          {mockSpendingInsights.map((insight) => (
            <Grid item xs={12} md={4} key={insight.category}>
              <Box
                sx={{
                  p: 2,
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 2,
                }}
              >
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                  <Typography variant="subtitle1" fontWeight="bold">
                    {insight.category}
                  </Typography>
                  {insight.trend === 'up' && <TrendingUp color="success" />}
                  {insight.trend === 'down' && <TrendingDown color="error" />}
                  {insight.trend === 'stable' && <TrendingFlat color="action" />}
                </Box>
                <Stack spacing={1}>
                  <Box display="flex" justifyContent="space-between">
                    <Typography variant="body2" color="text.secondary">
                      Monthly Avg
                    </Typography>
                    <Typography variant="body2">
                      ${insight.averageMonthly}
                    </Typography>
                  </Box>
                  <Box display="flex" justifyContent="space-between">
                    <Typography variant="body2" color="text.secondary">
                      % of Total
                    </Typography>
                    <Typography variant="body2">
                      {insight.percentOfTotal}%
                    </Typography>
                  </Box>
                  <Box display="flex" justifyContent="space-between">
                    <Typography variant="body2" color="text.secondary">
                      Est. Earnings
                    </Typography>
                    <Typography variant="body2" color="success.main">
                      +${insight.projectedEarnings.toFixed(2)}
                    </Typography>
                  </Box>
                </Stack>
              </Box>
            </Grid>
          ))}
        </Grid>
      </Paper>

      {/* Merchant Dialog */}
      <Dialog
        open={showMerchantDialog}
        onClose={() => setShowMerchantDialog(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Box display="flex" alignItems="center" gap={2}>
              {selectedCategory && (
                <>
                  <Avatar sx={{ bgcolor: alpha(selectedCategory.color, 0.2), color: selectedCategory.color }}>
                    {selectedCategory.icon}
                  </Avatar>
                  <Typography variant="h6">{selectedCategory.name} Merchants</Typography>
                </>
              )}
            </Box>
            <IconButton onClick={() => setShowMerchantDialog(false)}>
              <Close />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <Box mb={3}>
            <TextField
              fullWidth
              placeholder="Search merchants..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
              }}
              sx={{ mb: 2 }}
            />

            <Stack direction="row" spacing={2} alignItems="center">
              <ToggleButtonGroup
                value={filterMode}
                exclusive
                onChange={(_, value) => value && setFilterMode(value)}
                size="small"
              >
                <ToggleButton value="all">All</ToggleButton>
                <ToggleButton value="nearby">Nearby</ToggleButton>
                <ToggleButton value="online">Online</ToggleButton>
              </ToggleButtonGroup>

              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel>Sort by</InputLabel>
                <Select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as any)}
                  label="Sort by"
                >
                  <MenuItem value="rate">Cashback Rate</MenuItem>
                  <MenuItem value="distance">Distance</MenuItem>
                  <MenuItem value="name">Name</MenuItem>
                </Select>
              </FormControl>

              <FormControlLabel
                control={
                  <Switch
                    checked={showOnlyActivated}
                    onChange={(e) => setShowOnlyActivated(e.target.checked)}
                    size="small"
                  />
                }
                label="Active only"
              />
            </Stack>

            {filterMode === 'nearby' && (
              <Box mt={2}>
                <Typography variant="body2" gutterBottom>
                  Max distance: {maxDistance} miles
                </Typography>
                <Slider
                  value={maxDistance}
                  onChange={(_, value) => setMaxDistance(value as number)}
                  min={1}
                  max={50}
                  valueLabelDisplay="auto"
                />
              </Box>
            )}
          </Box>

          <Box sx={{ maxHeight: 400, overflow: 'auto' }}>
            {filteredMerchants.map(renderMerchantItem)}
            
            {filteredMerchants.length === 0 && (
              <Box textAlign="center" py={4}>
                <LocalOffer sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
                <Typography variant="body1" color="text.secondary">
                  No merchants found
                </Typography>
              </Box>
            )}
          </Box>

          <Alert severity="info" sx={{ mt: 3 }}>
            <Typography variant="body2">
              Activate merchants to start earning cashback. Use your linked cards at activated merchants to automatically earn rewards.
            </Typography>
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowMerchantDialog(false)}>
            Close
          </Button>
          <Button variant="contained" startIcon={<NotificationsActive />}>
            Set Alerts
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Add missing Close icon
const Close: React.FC = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
    <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
  </svg>
);

const TrendingDown: React.FC = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
    <path d="M16 18l2.29-2.29-4.88-4.88-4 4L2 7.41 3.41 6l6 6 4-4 6.3 6.29L22 12v6z" />
  </svg>
);

const TrendingFlat: React.FC = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
    <path d="M22 12l-4-4v3H3v2h15v3z" />
  </svg>
);

export default CashbackCategories;