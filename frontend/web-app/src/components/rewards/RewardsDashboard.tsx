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
  LinearProgress,
  Tabs,
  Tab,
  Stack,
  Divider,
  Badge,
  Alert,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  InputAdornment,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  CircularProgress,
  useTheme,
  alpha,
} from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import RedeemIcon from '@mui/icons-material/Redeem';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import FlightIcon from '@mui/icons-material/Flight';
import HotelIcon from '@mui/icons-material/Hotel';
import LocalGasStationIcon from '@mui/icons-material/LocalGasStation';
import MovieIcon from '@mui/icons-material/Movie';
import SportsEsportsIcon from '@mui/icons-material/SportsEsports';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TimerIcon from '@mui/icons-material/Timer';
import LockIcon from '@mui/icons-material/Lock';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import InfoIcon from '@mui/icons-material/Info';
import CelebrationIcon from '@mui/icons-material/Celebration';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import BoltIcon from '@mui/icons-material/Bolt';
import DiamondIcon from '@mui/icons-material/Diamond';
import WorkspacePremiumIcon from '@mui/icons-material/WorkspacePremium';
import MilitaryTechIcon from '@mui/icons-material/MilitaryTech';
import GradeIcon from '@mui/icons-material/Grade';
import MonetizationOnIcon from '@mui/icons-material/MonetizationOn';
import LoyaltyIcon from '@mui/icons-material/Loyalty';
import QrCode2Icon from '@mui/icons-material/QrCode2';
import ShareIcon from '@mui/icons-material/Share';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import FilterListIcon from '@mui/icons-material/FilterList';
import SearchIcon from '@mui/icons-material/Search';;
import { format, addDays, differenceInDays } from 'date-fns';

interface RewardStats {
  totalPoints: number;
  pendingPoints: number;
  lifetimeEarned: number;
  currentTier: 'Bronze' | 'Silver' | 'Gold' | 'Platinum' | 'Diamond';
  nextTierProgress: number;
  cashbackEarned: number;
  rewardsRedeemed: number;
  streakDays: number;
}

interface Reward {
  id: string;
  name: string;
  description: string;
  category: string;
  pointsCost: number;
  cashValue?: number;
  icon: React.ReactNode;
  availability: 'available' | 'locked' | 'out_of_stock';
  requiredTier?: string;
  expiresAt?: string;
  quantity?: number;
  featured?: boolean;
  discount?: number;
}

interface CashbackOffer {
  id: string;
  merchant: string;
  category: string;
  rate: number;
  bonusRate?: number;
  description: string;
  logo?: string;
  terms?: string;
  expiresAt?: string;
  activated: boolean;
  maxCashback?: number;
}

interface Transaction {
  id: string;
  type: 'earned' | 'redeemed' | 'expired';
  description: string;
  points: number;
  cashback?: number;
  date: string;
  merchant?: string;
  status: 'completed' | 'pending' | 'failed';
}

interface Challenge {
  id: string;
  title: string;
  description: string;
  reward: number;
  progress: number;
  target: number;
  category: string;
  difficulty: 'easy' | 'medium' | 'hard';
  expiresAt?: string;
  icon: React.ReactNode;
}

const mockStats: RewardStats = {
  totalPoints: 12450,
  pendingPoints: 850,
  lifetimeEarned: 45280,
  currentTier: 'Gold',
  nextTierProgress: 75,
  cashbackEarned: 234.50,
  rewardsRedeemed: 15,
  streakDays: 7,
};

const mockRewards: Reward[] = [
  {
    id: '1',
    name: '$10 Amazon Gift Card',
    description: 'Redeem for Amazon shopping credit',
    category: 'Gift Cards',
    pointsCost: 1000,
    cashValue: 10,
    icon: <CardGiftcard />,
    availability: 'available',
    featured: true,
  },
  {
    id: '2',
    name: '20% Off Next Purchase',
    description: 'Valid for any merchant in our network',
    category: 'Discounts',
    pointsCost: 500,
    icon: <LocalOffer />,
    availability: 'available',
    discount: 20,
  },
  {
    id: '3',
    name: 'Free Month Premium',
    description: 'Unlock all premium features for 30 days',
    category: 'Subscriptions',
    pointsCost: 2500,
    cashValue: 25,
    icon: <WorkspacePremium />,
    availability: 'available',
    requiredTier: 'Silver',
  },
  {
    id: '4',
    name: 'Exclusive Diamond Reward',
    description: 'Mystery reward for Diamond members',
    category: 'Exclusive',
    pointsCost: 10000,
    icon: <Diamond />,
    availability: 'locked',
    requiredTier: 'Diamond',
    featured: true,
  },
];

const mockCashbackOffers: CashbackOffer[] = [
  {
    id: '1',
    merchant: 'Whole Foods',
    category: 'Groceries',
    rate: 3,
    bonusRate: 5,
    description: 'Earn 3% cashback, 5% on weekends',
    activated: true,
    maxCashback: 50,
  },
  {
    id: '2',
    merchant: 'Shell',
    category: 'Gas',
    rate: 2,
    description: 'Save on every fill-up',
    activated: true,
  },
  {
    id: '3',
    merchant: 'Netflix',
    category: 'Entertainment',
    rate: 5,
    description: 'Streaming services cashback',
    activated: false,
    expiresAt: '2024-02-28',
  },
  {
    id: '4',
    merchant: 'Uber',
    category: 'Transportation',
    rate: 4,
    bonusRate: 8,
    description: '4% on rides, 8% on Uber Eats',
    activated: true,
  },
];

const mockTransactions: Transaction[] = [
  {
    id: '1',
    type: 'earned',
    description: 'Purchase at Whole Foods',
    points: 150,
    cashback: 4.50,
    date: '2024-01-20T14:30:00Z',
    merchant: 'Whole Foods',
    status: 'completed',
  },
  {
    id: '2',
    type: 'redeemed',
    description: '$10 Amazon Gift Card',
    points: -1000,
    date: '2024-01-19T10:15:00Z',
    status: 'completed',
  },
  {
    id: '3',
    type: 'earned',
    description: 'Weekly streak bonus',
    points: 500,
    date: '2024-01-18T00:00:00Z',
    status: 'completed',
  },
];

const mockChallenges: Challenge[] = [
  {
    id: '1',
    title: 'Weekend Warrior',
    description: 'Make 5 purchases this weekend',
    reward: 250,
    progress: 3,
    target: 5,
    category: 'Shopping',
    difficulty: 'easy',
    expiresAt: '2024-01-21',
    icon: <ShoppingCart />,
  },
  {
    id: '2',
    title: 'Foodie Challenge',
    description: 'Try 3 different restaurants',
    reward: 500,
    progress: 1,
    target: 3,
    category: 'Dining',
    difficulty: 'medium',
    icon: <Restaurant />,
  },
  {
    id: '3',
    title: 'Big Spender',
    description: 'Spend $500 in a single transaction',
    reward: 1000,
    progress: 0,
    target: 1,
    category: 'Premium',
    difficulty: 'hard',
    icon: <MonetizationOn />,
  },
];

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <Box hidden={value !== index} pt={3}>
    {value === index && children}
  </Box>
);

const RewardsDashboard: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedReward, setSelectedReward] = useState<Reward | null>(null);
  const [showRedeemDialog, setShowRedeemDialog] = useState(false);
  const [filterCategory, setFilterCategory] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState<'points' | 'value' | 'newest'>('points');

  const tierColors = {
    Bronze: '#CD7F32',
    Silver: '#C0C0C0',
    Gold: '#FFD700',
    Platinum: '#E5E4E2',
    Diamond: '#B9F2FF',
  };

  const tierIcons = {
    Bronze: <MilitaryTech />,
    Silver: <Grade />,
    Gold: <EmojiEvents />,
    Platinum: <WorkspacePremium />,
    Diamond: <Diamond />,
  };

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'Gift Cards':
        return <CardGiftcard />;
      case 'Travel':
        return <Flight />;
      case 'Dining':
        return <Restaurant />;
      case 'Entertainment':
        return <Movie />;
      case 'Shopping':
        return <ShoppingCart />;
      case 'Gas':
        return <LocalGasStation />;
      default:
        return <LocalOffer />;
    }
  };

  const filteredRewards = useMemo(() => {
    let filtered = mockRewards.filter(reward => {
      const matchesSearch = reward.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        reward.description.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = filterCategory === 'all' || reward.category === filterCategory;
      return matchesSearch && matchesCategory;
    });

    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'points':
          return a.pointsCost - b.pointsCost;
        case 'value':
          return (b.cashValue || 0) - (a.cashValue || 0);
        case 'newest':
          return 0; // Would sort by date in real implementation
        default:
          return 0;
      }
    });

    return filtered;
  }, [searchTerm, filterCategory, sortBy]);

  const getPointsValue = (points: number) => {
    return (points * 0.01).toFixed(2);
  };

  const handleRedeem = () => {
    // Handle redemption
    setShowRedeemDialog(false);
    setSelectedReward(null);
  };

  const renderTierProgress = () => {
    const tiers = ['Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond'];
    const currentIndex = tiers.indexOf(mockStats.currentTier);
    const nextTier = currentIndex < tiers.length - 1 ? tiers[currentIndex + 1] : null;

    return (
      <Card>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">Tier Status</Typography>
            <Chip
              label={mockStats.currentTier}
              icon={tierIcons[mockStats.currentTier]}
              sx={{
                backgroundColor: alpha(tierColors[mockStats.currentTier], 0.2),
                color: tierColors[mockStats.currentTier],
                fontWeight: 'bold',
              }}
            />
          </Box>

          {nextTier && (
            <>
              <Box display="flex" justifyContent="space-between" mb={1}>
                <Typography variant="body2" color="text.secondary">
                  Progress to {nextTier}
                </Typography>
                <Typography variant="body2" fontWeight="bold">
                  {mockStats.nextTierProgress}%
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={mockStats.nextTierProgress}
                sx={{
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: alpha(tierColors[nextTier], 0.2),
                  '& .MuiLinearProgress-bar': {
                    borderRadius: 4,
                    backgroundColor: tierColors[nextTier],
                  },
                }}
              />
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                {100 - mockStats.nextTierProgress}% more to unlock {nextTier} benefits
              </Typography>
            </>
          )}

          <Divider sx={{ my: 2 }} />

          <Stack spacing={1}>
            <Box display="flex" alignItems="center" gap={1}>
              <CheckCircle color="success" fontSize="small" />
              <Typography variant="body2">2x points on all purchases</Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1}>
              <CheckCircle color="success" fontSize="small" />
              <Typography variant="body2">Exclusive member offers</Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1}>
              <CheckCircle color="success" fontSize="small" />
              <Typography variant="body2">Priority customer support</Typography>
            </Box>
            {nextTier && (
              <Box display="flex" alignItems="center" gap={1}>
                <Lock color="disabled" fontSize="small" />
                <Typography variant="body2" color="text.secondary">
                  3x points on select categories ({nextTier})
                </Typography>
              </Box>
            )}
          </Stack>
        </CardContent>
      </Card>
    );
  };

  const renderRewardCard = (reward: Reward) => (
    <Card
      key={reward.id}
      sx={{
        height: '100%',
        opacity: reward.availability === 'locked' ? 0.7 : 1,
        position: 'relative',
      }}
    >
      {reward.featured && (
        <Chip
          label="Featured"
          size="small"
          color="error"
          sx={{
            position: 'absolute',
            top: 8,
            right: 8,
            zIndex: 1,
          }}
        />
      )}
      <CardContent>
        <Box display="flex" alignItems="center" gap={2} mb={2}>
          <Avatar
            sx={{
              bgcolor: theme.palette.primary.main,
              width: 48,
              height: 48,
            }}
          >
            {reward.icon}
          </Avatar>
          <Box flex={1}>
            <Typography variant="subtitle1" fontWeight="bold">
              {reward.name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {reward.category}
            </Typography>
          </Box>
        </Box>

        <Typography variant="body2" color="text.secondary" mb={2}>
          {reward.description}
        </Typography>

        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box>
            <Typography variant="h6" fontWeight="bold" color="primary">
              {reward.pointsCost.toLocaleString()} pts
            </Typography>
            {reward.cashValue && (
              <Typography variant="caption" color="text.secondary">
                ${reward.cashValue} value
              </Typography>
            )}
          </Box>
          {reward.discount && (
            <Chip
              label={`${reward.discount}% OFF`}
              color="success"
              size="small"
            />
          )}
        </Box>

        {reward.requiredTier && reward.availability === 'locked' && (
          <Alert severity="info" sx={{ mt: 2, py: 0.5 }}>
            <Typography variant="caption">
              Requires {reward.requiredTier} tier
            </Typography>
          </Alert>
        )}
      </CardContent>
      <CardActions>
        <Button
          fullWidth
          variant={reward.availability === 'available' ? 'contained' : 'outlined'}
          disabled={reward.availability !== 'available' || reward.pointsCost > mockStats.totalPoints}
          onClick={() => {
            setSelectedReward(reward);
            setShowRedeemDialog(true);
          }}
          startIcon={reward.availability === 'locked' ? <Lock /> : <Redeem />}
        >
          {reward.availability === 'locked' ? 'Locked' :
           reward.pointsCost > mockStats.totalPoints ? 'Insufficient Points' :
           'Redeem'}
        </Button>
      </CardActions>
    </Card>
  );

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Rewards & Cashback
        </Typography>
        <Stack direction="row" spacing={2}>
          <Chip
            icon={<Bolt />}
            label={`${mockStats.streakDays} day streak`}
            color="warning"
          />
          <Button variant="outlined" startIcon={<QrCode2 />}>
            Scan Receipt
          </Button>
        </Stack>
      </Box>

      {/* Stats Overview */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.primary.main }}>
                  <Star />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Available Points
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    {mockStats.totalPoints.toLocaleString()}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    ≈ ${getPointsValue(mockStats.totalPoints)}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.success.main }}>
                  <AttachMoney />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Cashback Earned
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    ${mockStats.cashbackEarned}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    This month
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.warning.main }}>
                  <Timer />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Pending Points
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    {mockStats.pendingPoints}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Processing
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.info.main }}>
                  <TrendingUp />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Lifetime Earned
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    {mockStats.lifetimeEarned.toLocaleString()}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {mockStats.rewardsRedeemed} redeemed
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={8}>
          <Paper>
            <Tabs
              value={activeTab}
              onChange={(_, value) => setActiveTab(value)}
              sx={{ borderBottom: 1, borderColor: 'divider' }}
            >
              <Tab label="Rewards Catalog" icon={<CardGiftcard />} iconPosition="start" />
              <Tab label="Cashback Offers" icon={<LocalOffer />} iconPosition="start" />
              <Tab label="Challenges" icon={<EmojiEvents />} iconPosition="start" />
              <Tab label="History" icon={<Timer />} iconPosition="start" />
            </Tabs>

            <TabPanel value={activeTab} index={0}>
              <Box p={2}>
                <Box display="flex" gap={2} mb={3}>
                  <TextField
                    placeholder="Search rewards..."
                    size="small"
                    fullWidth
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <Search />
                        </InputAdornment>
                      ),
                    }}
                  />
                  <FormControl size="small" sx={{ minWidth: 150 }}>
                    <InputLabel>Category</InputLabel>
                    <Select
                      value={filterCategory}
                      onChange={(e) => setFilterCategory(e.target.value)}
                      label="Category"
                    >
                      <MenuItem value="all">All Categories</MenuItem>
                      <MenuItem value="Gift Cards">Gift Cards</MenuItem>
                      <MenuItem value="Discounts">Discounts</MenuItem>
                      <MenuItem value="Subscriptions">Subscriptions</MenuItem>
                      <MenuItem value="Exclusive">Exclusive</MenuItem>
                    </Select>
                  </FormControl>
                  <FormControl size="small" sx={{ minWidth: 120 }}>
                    <InputLabel>Sort by</InputLabel>
                    <Select
                      value={sortBy}
                      onChange={(e) => setSortBy(e.target.value as any)}
                      label="Sort by"
                    >
                      <MenuItem value="points">Points</MenuItem>
                      <MenuItem value="value">Value</MenuItem>
                      <MenuItem value="newest">Newest</MenuItem>
                    </Select>
                  </FormControl>
                </Box>

                <Grid container spacing={3}>
                  {filteredRewards.map(reward => (
                    <Grid item xs={12} sm={6} md={4} key={reward.id}>
                      {renderRewardCard(reward)}
                    </Grid>
                  ))}
                </Grid>

                {filteredRewards.length === 0 && (
                  <Box textAlign="center" py={8}>
                    <CardGiftcard sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                    <Typography variant="h6" color="text.secondary">
                      No rewards found
                    </Typography>
                  </Box>
                )}
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={1}>
              <Box p={2}>
                <Alert severity="info" sx={{ mb: 3 }}>
                  <Typography variant="body2">
                    Activate cashback offers to earn on every purchase. Stack multiple offers for maximum savings!
                  </Typography>
                </Alert>

                <Grid container spacing={2}>
                  {mockCashbackOffers.map((offer) => (
                    <Grid item xs={12} md={6} key={offer.id}>
                      <Card variant={offer.activated ? 'elevation' : 'outlined'}>
                        <CardContent>
                          <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                            <Box display="flex" gap={2}>
                              <Avatar sx={{ bgcolor: theme.palette.grey[200] }}>
                                {getCategoryIcon(offer.category)}
                              </Avatar>
                              <Box>
                                <Typography variant="subtitle1" fontWeight="bold">
                                  {offer.merchant}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {offer.description}
                                </Typography>
                                <Stack direction="row" spacing={1} mt={1}>
                                  <Chip
                                    label={`${offer.rate}% base`}
                                    size="small"
                                    color="primary"
                                  />
                                  {offer.bonusRate && (
                                    <Chip
                                      label={`${offer.bonusRate}% bonus`}
                                      size="small"
                                      color="success"
                                    />
                                  )}
                                  {offer.maxCashback && (
                                    <Chip
                                      label={`Max $${offer.maxCashback}`}
                                      size="small"
                                      variant="outlined"
                                    />
                                  )}
                                </Stack>
                              </Box>
                            </Box>
                            <IconButton
                              onClick={() => {
                                // Toggle activation
                              }}
                              color={offer.activated ? 'success' : 'default'}
                            >
                              {offer.activated ? <CheckCircle /> : <Circle />}
                            </IconButton>
                          </Box>
                          {offer.expiresAt && (
                            <Alert severity="warning" sx={{ mt: 2, py: 0.5 }}>
                              <Typography variant="caption">
                                Expires {format(new Date(offer.expiresAt), 'MMM dd, yyyy')}
                              </Typography>
                            </Alert>
                          )}
                        </CardContent>
                      </Card>
                    </Grid>
                  ))}
                </Grid>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={2}>
              <Box p={2}>
                <Typography variant="h6" gutterBottom>
                  Active Challenges
                </Typography>
                <Stack spacing={2}>
                  {mockChallenges.map((challenge) => {
                    const progress = (challenge.progress / challenge.target) * 100;
                    const daysLeft = challenge.expiresAt ? 
                      differenceInDays(new Date(challenge.expiresAt), new Date()) : null;

                    return (
                      <Card key={challenge.id} variant="outlined">
                        <CardContent>
                          <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                            <Box display="flex" gap={2} flex={1}>
                              <Avatar sx={{ bgcolor: 
                                challenge.difficulty === 'easy' ? theme.palette.success.main :
                                challenge.difficulty === 'medium' ? theme.palette.warning.main :
                                theme.palette.error.main
                              }}>
                                {challenge.icon}
                              </Avatar>
                              <Box flex={1}>
                                <Box display="flex" alignItems="center" gap={1} mb={1}>
                                  <Typography variant="subtitle1" fontWeight="bold">
                                    {challenge.title}
                                  </Typography>
                                  <Chip
                                    label={challenge.difficulty}
                                    size="small"
                                    color={
                                      challenge.difficulty === 'easy' ? 'success' :
                                      challenge.difficulty === 'medium' ? 'warning' :
                                      'error'
                                    }
                                  />
                                  {daysLeft !== null && daysLeft <= 3 && (
                                    <Chip
                                      label={`${daysLeft}d left`}
                                      size="small"
                                      color="error"
                                      variant="outlined"
                                    />
                                  )}
                                </Box>
                                <Typography variant="body2" color="text.secondary" mb={2}>
                                  {challenge.description}
                                </Typography>
                                <Box>
                                  <Box display="flex" justifyContent="space-between" mb={1}>
                                    <Typography variant="body2">
                                      Progress: {challenge.progress}/{challenge.target}
                                    </Typography>
                                    <Typography variant="body2" fontWeight="bold">
                                      {progress.toFixed(0)}%
                                    </Typography>
                                  </Box>
                                  <LinearProgress
                                    variant="determinate"
                                    value={progress}
                                    sx={{
                                      height: 8,
                                      borderRadius: 4,
                                      backgroundColor: alpha(theme.palette.primary.main, 0.2),
                                      '& .MuiLinearProgress-bar': {
                                        borderRadius: 4,
                                      },
                                    }}
                                  />
                                </Box>
                              </Box>
                            </Box>
                            <Box textAlign="right" ml={2}>
                              <Typography variant="h6" color="primary" fontWeight="bold">
                                +{challenge.reward}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                points
                              </Typography>
                            </Box>
                          </Box>
                        </CardContent>
                      </Card>
                    );
                  })}
                </Stack>

                <Alert severity="success" sx={{ mt: 3 }} icon={<Celebration />}>
                  <Typography variant="body2">
                    Complete all challenges this week to earn a bonus 1,000 points!
                  </Typography>
                </Alert>
              </Box>
            </TabPanel>

            <TabPanel value={activeTab} index={3}>
              <Box p={2}>
                <Typography variant="h6" gutterBottom>
                  Points History
                </Typography>
                <List>
                  {mockTransactions.map((transaction) => (
                    <ListItem key={transaction.id} divider>
                      <ListItemAvatar>
                        <Avatar sx={{ bgcolor: 
                          transaction.type === 'earned' ? theme.palette.success.main :
                          transaction.type === 'redeemed' ? theme.palette.error.main :
                          theme.palette.grey[500]
                        }}>
                          {transaction.type === 'earned' ? <TrendingUp /> :
                           transaction.type === 'redeemed' ? <Redeem /> :
                           <Timer />}
                        </Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={
                          <Box display="flex" alignItems="center" gap={1}>
                            <Typography variant="body1">
                              {transaction.description}
                            </Typography>
                            <Chip
                              label={transaction.status}
                              size="small"
                              color={
                                transaction.status === 'completed' ? 'success' :
                                transaction.status === 'pending' ? 'warning' :
                                'error'
                              }
                            />
                          </Box>
                        }
                        secondary={
                          <Box>
                            <Typography variant="caption" color="text.secondary">
                              {format(new Date(transaction.date), 'MMM dd, yyyy HH:mm')}
                            </Typography>
                            {transaction.merchant && (
                              <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                                {transaction.merchant}
                              </Typography>
                            )}
                          </Box>
                        }
                      />
                      <ListItemSecondaryAction>
                        <Box textAlign="right">
                          <Typography
                            variant="body1"
                            fontWeight="bold"
                            color={transaction.points > 0 ? 'success.main' : 'error.main'}
                          >
                            {transaction.points > 0 ? '+' : ''}{transaction.points}
                          </Typography>
                          {transaction.cashback && (
                            <Typography variant="caption" color="success.main">
                              +${transaction.cashback}
                            </Typography>
                          )}
                        </Box>
                      </ListItemSecondaryAction>
                    </ListItem>
                  ))}
                </List>
              </Box>
            </TabPanel>
          </Paper>
        </Grid>

        <Grid item xs={12} lg={4}>
          <Stack spacing={3}>
            {renderTierProgress()}

            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Quick Actions
                </Typography>
                <Stack spacing={2}>
                  <Button
                    variant="outlined"
                    fullWidth
                    startIcon={<QrCode2 />}
                    sx={{ justifyContent: 'flex-start' }}
                  >
                    Scan Receipt for Points
                  </Button>
                  <Button
                    variant="outlined"
                    fullWidth
                    startIcon={<Share />}
                    sx={{ justifyContent: 'flex-start' }}
                  >
                    Refer a Friend (+500 pts)
                  </Button>
                  <Button
                    variant="outlined"
                    fullWidth
                    startIcon={<Loyalty />}
                    sx={{ justifyContent: 'flex-start' }}
                  >
                    Link Loyalty Cards
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                  <Typography variant="h6">
                    Bonus Opportunities
                  </Typography>
                  <AutoAwesome color="warning" />
                </Box>
                <Stack spacing={2}>
                  <Alert severity="info">
                    <Typography variant="body2">
                      <strong>Double Points Weekend!</strong> Earn 2x points on all purchases this weekend.
                    </Typography>
                  </Alert>
                  <Alert severity="success">
                    <Typography variant="body2">
                      <strong>Birthday Month:</strong> Get 3x points throughout your birthday month!
                    </Typography>
                  </Alert>
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>

      {/* Redeem Dialog */}
      <Dialog
        open={showRedeemDialog}
        onClose={() => setShowRedeemDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          Confirm Redemption
        </DialogTitle>
        <DialogContent>
          {selectedReward && (
            <Box>
              <Box display="flex" alignItems="center" gap={2} mb={3}>
                <Avatar sx={{ bgcolor: theme.palette.primary.main, width: 56, height: 56 }}>
                  {selectedReward.icon}
                </Avatar>
                <Box>
                  <Typography variant="h6">{selectedReward.name}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedReward.description}
                  </Typography>
                </Box>
              </Box>

              <Divider sx={{ my: 3 }} />

              <Stack spacing={2}>
                <Box display="flex" justifyContent="space-between">
                  <Typography>Points Cost:</Typography>
                  <Typography fontWeight="bold">
                    {selectedReward.pointsCost.toLocaleString()} pts
                  </Typography>
                </Box>
                <Box display="flex" justifyContent="space-between">
                  <Typography>Current Balance:</Typography>
                  <Typography>{mockStats.totalPoints.toLocaleString()} pts</Typography>
                </Box>
                <Box display="flex" justifyContent="space-between">
                  <Typography>After Redemption:</Typography>
                  <Typography fontWeight="bold" color="primary">
                    {(mockStats.totalPoints - selectedReward.pointsCost).toLocaleString()} pts
                  </Typography>
                </Box>
              </Stack>

              <Alert severity="info" sx={{ mt: 3 }}>
                <Typography variant="body2">
                  Reward will be delivered to your registered email within 24 hours.
                </Typography>
              </Alert>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowRedeemDialog(false)}>
            Cancel
          </Button>
          <Button variant="contained" onClick={handleRedeem} startIcon={<Redeem />}>
            Confirm Redemption
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Fix the Circle icon import
const Circle: React.FC = () => (
  <Box
    sx={{
      width: 24,
      height: 24,
      borderRadius: '50%',
      border: '2px solid',
      borderColor: 'action.disabled',
    }}
  />
);

export default RewardsDashboard;