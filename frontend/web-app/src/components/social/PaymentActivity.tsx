import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Avatar,
  AvatarGroup,
  Chip,
  IconButton,
  Button,
  Card,
  CardContent,
  useTheme,
  alpha,
  Tooltip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  Divider,
  List,
  ListItem,
  ListItemAvatar,
  ListItemSecondaryAction,
  Badge,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import FireIcon from '@mui/icons-material/LocalFireDepartment';
import TrophyIcon from '@mui/icons-material/EmojiEvents';
import GroupIcon from '@mui/icons-material/Group';
import PublicIcon from '@mui/icons-material/Public';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import StarIcon from '@mui/icons-material/Star';
import MoreIcon from '@mui/icons-material/MoreVert';
import ShareIcon from '@mui/icons-material/Share';
import ViewIcon from '@mui/icons-material/Visibility';
import FlagIcon from '@mui/icons-material/Flag';
import ArrowUpIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownIcon from '@mui/icons-material/ArrowDownward';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import FoodIcon from '@mui/icons-material/Restaurant';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import TransportIcon from '@mui/icons-material/DirectionsCar';
import HomeIcon from '@mui/icons-material/Home';
import EntertainmentIcon from '@mui/icons-material/SportsEsports';
import HealthIcon from '@mui/icons-material/LocalHospital';
import EducationIcon from '@mui/icons-material/School';
import TravelIcon from '@mui/icons-material/Flight';
import OtherIcon from '@mui/icons-material/Category';
import CheckIcon from '@mui/icons-material/CheckCircle';
import PendingIcon from '@mui/icons-material/Schedule';
import CancelIcon from '@mui/icons-material/Cancel';;
import { format, parseISO, startOfWeek, endOfWeek, isWithinInterval } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { formatCurrency } from '../../utils/formatters';

interface PaymentActivityProps {
  userId?: string;
  view?: 'trending' | 'friends' | 'leaderboard';
}

interface ActivityItem {
  id: string;
  type: 'payment' | 'request' | 'split';
  amount: number;
  currency: string;
  category: string;
  from: {
    id: string;
    name: string;
    avatar?: string;
    verified?: boolean;
  };
  to: {
    id: string;
    name: string;
    avatar?: string;
    verified?: boolean;
  };
  participants?: Array<{
    id: string;
    name: string;
    avatar?: string;
  }>;
  description?: string;
  timestamp: string;
  likes: number;
  isLiked?: boolean;
  visibility: 'public' | 'friends' | 'private';
  status: 'completed' | 'pending' | 'failed';
}

interface LeaderboardEntry {
  rank: number;
  user: {
    id: string;
    name: string;
    avatar?: string;
    verified?: boolean;
  };
  stats: {
    sent: number;
    received: number;
    transactions: number;
    streak: number;
  };
  change: 'up' | 'down' | 'same';
}

interface TrendingCategory {
  name: string;
  icon: React.ReactNode;
  amount: number;
  transactions: number;
  trend: number;
}

const PaymentActivity: React.FC<PaymentActivityProps> = ({ userId, view = 'trending' }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  
  const [selectedView, setSelectedView] = useState(view);
  const [selectedPeriod, setSelectedPeriod] = useState<'day' | 'week' | 'month'>('week');
  const [detailsDialog, setDetailsDialog] = useState<ActivityItem | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedItem, setSelectedItem] = useState<ActivityItem | null>(null);

  // Mock data
  const trendingActivities: ActivityItem[] = [
    {
      id: '1',
      type: 'payment',
      amount: 125.50,
      currency: 'USD',
      category: 'Food & Dining',
      from: {
        id: '1',
        name: 'John Doe',
        avatar: 'https://i.pravatar.cc/150?img=1',
        verified: true,
      },
      to: {
        id: '2',
        name: 'Pizza Palace',
        avatar: 'https://i.pravatar.cc/150?img=10',
      },
      description: '🍕 Friday night pizza party!',
      timestamp: new Date().toISOString(),
      likes: 342,
      isLiked: false,
      visibility: 'public',
      status: 'completed',
    },
    {
      id: '2',
      type: 'split',
      amount: 450.00,
      currency: 'USD',
      category: 'Travel',
      from: {
        id: '3',
        name: 'Sarah Wilson',
        avatar: 'https://i.pravatar.cc/150?img=2',
      },
      to: {
        id: '4',
        name: 'Weekend Trip',
      },
      participants: [
        { id: '3', name: 'Sarah Wilson', avatar: 'https://i.pravatar.cc/150?img=2' },
        { id: '5', name: 'Mike Johnson', avatar: 'https://i.pravatar.cc/150?img=3' },
        { id: '6', name: 'Emily Davis', avatar: 'https://i.pravatar.cc/150?img=4' },
        { id: '7', name: 'Tom Brown', avatar: 'https://i.pravatar.cc/150?img=5' },
      ],
      description: '✈️ Miami Beach weekend getaway',
      timestamp: new Date(Date.now() - 3600000).toISOString(),
      likes: 567,
      isLiked: true,
      visibility: 'public',
      status: 'completed',
    },
  ];

  const leaderboard: LeaderboardEntry[] = [
    {
      rank: 1,
      user: {
        id: '1',
        name: 'Alex Thompson',
        avatar: 'https://i.pravatar.cc/150?img=1',
        verified: true,
      },
      stats: {
        sent: 5420.50,
        received: 3210.25,
        transactions: 156,
        streak: 21,
      },
      change: 'same',
    },
    {
      rank: 2,
      user: {
        id: '2',
        name: 'Maria Garcia',
        avatar: 'https://i.pravatar.cc/150?img=2',
        verified: true,
      },
      stats: {
        sent: 4890.75,
        received: 2980.50,
        transactions: 143,
        streak: 18,
      },
      change: 'up',
    },
    {
      rank: 3,
      user: {
        id: '3',
        name: 'James Chen',
        avatar: 'https://i.pravatar.cc/150?img=3',
      },
      stats: {
        sent: 4560.00,
        received: 3450.00,
        transactions: 132,
        streak: 15,
      },
      change: 'down',
    },
  ];

  const trendingCategories: TrendingCategory[] = [
    {
      name: 'Food & Dining',
      icon: <FoodIcon />,
      amount: 12450.50,
      transactions: 342,
      trend: 15.2,
    },
    {
      name: 'Shopping',
      icon: <ShoppingIcon />,
      amount: 9870.25,
      transactions: 256,
      trend: -5.8,
    },
    {
      name: 'Transportation',
      icon: <TransportIcon />,
      amount: 6540.00,
      transactions: 189,
      trend: 8.4,
    },
    {
      name: 'Entertainment',
      icon: <EntertainmentIcon />,
      amount: 5320.75,
      transactions: 145,
      trend: 22.1,
    },
  ];

  const getCategoryIcon = (category: string) => {
    const icons: Record<string, React.ReactNode> = {
      'Food & Dining': <FoodIcon />,
      'Shopping': <ShoppingIcon />,
      'Transportation': <TransportIcon />,
      'Bills & Utilities': <HomeIcon />,
      'Entertainment': <EntertainmentIcon />,
      'Healthcare': <HealthIcon />,
      'Education': <EducationIcon />,
      'Travel': <TravelIcon />,
      'Other': <OtherIcon />,
    };
    return icons[category] || <OtherIcon />;
  };

  const getStatusIcon = (status: ActivityItem['status']) => {
    switch (status) {
      case 'completed':
        return <CheckIcon color="success" fontSize="small" />;
      case 'pending':
        return <PendingIcon color="warning" fontSize="small" />;
      case 'failed':
        return <CancelIcon color="error" fontSize="small" />;
    }
  };

  const getRankIcon = (rank: number) => {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return null;
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, item: ActivityItem) => {
    setMenuAnchor(event.currentTarget);
    setSelectedItem(item);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
    setSelectedItem(null);
  };

  const handleViewDetails = () => {
    if (selectedItem) {
      setDetailsDialog(selectedItem);
    }
    handleMenuClose();
  };

  const renderTrendingView = () => (
    <Box>
      {/* Trending Categories */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Trending Categories
        </Typography>
        <Grid container spacing={2}>
          {trendingCategories.map((category) => (
            <Grid item xs={12} sm={6} md={3} key={category.name}>
              <Card
                sx={{
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: theme.shadows[4],
                  },
                }}
              >
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    <Avatar
                      sx={{
                        bgcolor: alpha(theme.palette.primary.main, 0.1),
                        color: theme.palette.primary.main,
                      }}
                    >
                      {category.icon}
                    </Avatar>
                    <Box sx={{ ml: 'auto' }}>
                      {category.trend > 0 ? (
                        <Chip
                          icon={<TrendingUpIcon />}
                          label={`+${category.trend}%`}
                          size="small"
                          color="success"
                        />
                      ) : (
                        <Chip
                          icon={<TrendingDownIcon />}
                          label={`${category.trend}%`}
                          size="small"
                          color="error"
                        />
                      )}
                    </Box>
                  </Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    {category.name}
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {formatCurrency(category.amount)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {category.transactions} transactions
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Paper>

      {/* Trending Activities */}
      <Paper sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <FireIcon sx={{ color: theme.palette.error.main, mr: 1 }} />
          <Typography variant="h6">Trending Now</Typography>
        </Box>
        <List>
          {trendingActivities.map((activity, index) => (
            <React.Fragment key={activity.id}>
              {index > 0 && <Divider />}
              <ListItem sx={{ px: 0 }}>
                <ListItemAvatar>
                  {activity.type === 'split' && activity.participants ? (
                    <AvatarGroup max={3}>
                      {activity.participants.map((p) => (
                        <Avatar key={p.id} src={p.avatar} sx={{ width: 32, height: 32 }}>
                          {p.name[0]}
                        </Avatar>
                      ))}
                    </AvatarGroup>
                  ) : (
                    <Avatar src={activity.from.avatar}>
                      {activity.from.name[0]}
                    </Avatar>
                  )}
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="subtitle2">
                        {activity.from.name}
                      </Typography>
                      {activity.type === 'split' ? (
                        <>
                          <Typography variant="body2" color="text.secondary">
                            split
                          </Typography>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                            {formatCurrency(activity.amount)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            with {activity.participants!.length - 1} others
                          </Typography>
                        </>
                      ) : (
                        <>
                          <Typography variant="body2" color="text.secondary">
                            {activity.type === 'payment' ? 'paid' : 'requested'}
                          </Typography>
                          <Typography variant="subtitle2">
                            {activity.to.name}
                          </Typography>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                            {formatCurrency(activity.amount)}
                          </Typography>
                        </>
                      )}
                    </Box>
                  }
                  secondary={
                    <Box>
                      {activity.description && (
                        <Typography variant="body2" sx={{ my: 0.5 }}>
                          {activity.description}
                        </Typography>
                      )}
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Chip
                          icon={getCategoryIcon(activity.category)}
                          label={activity.category}
                          size="small"
                          variant="outlined"
                        />
                        <Typography variant="caption" color="text.secondary">
                          {format(parseISO(activity.timestamp), 'h:mm a')}
                        </Typography>
                        {getStatusIcon(activity.status)}
                      </Box>
                    </Box>
                  }
                />
                <ListItemSecondaryAction>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip
                      icon={<FireIcon />}
                      label={activity.likes}
                      size="small"
                      color={activity.isLiked ? 'error' : 'default'}
                      onClick={() => {/* Handle like */}}
                    />
                    <IconButton size="small" onClick={(e) => handleMenuOpen(e, activity)}>
                      <MoreIcon />
                    </IconButton>
                  </Box>
                </ListItemSecondaryAction>
              </ListItem>
            </React.Fragment>
          ))}
        </List>
      </Paper>
    </Box>
  );

  const renderLeaderboardView = () => (
    <Box>
      {/* Period Selector */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">Leaderboard</Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            {(['day', 'week', 'month'] as const).map((period) => (
              <Chip
                key={period}
                label={period.charAt(0).toUpperCase() + period.slice(1)}
                onClick={() => setSelectedPeriod(period)}
                color={selectedPeriod === period ? 'primary' : 'default'}
                variant={selectedPeriod === period ? 'filled' : 'outlined'}
              />
            ))}
          </Box>
        </Box>
      </Paper>

      {/* Top 3 */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {leaderboard.slice(0, 3).map((entry) => (
          <Grid item xs={12} md={4} key={entry.user.id}>
            <Card
              sx={{
                textAlign: 'center',
                background: entry.rank === 1
                  ? `linear-gradient(135deg, ${alpha(theme.palette.warning.main, 0.1)} 0%, ${alpha(theme.palette.warning.light, 0.05)} 100%)`
                  : undefined,
              }}
            >
              <CardContent>
                <Typography variant="h2" sx={{ mb: 2 }}>
                  {getRankIcon(entry.rank)}
                </Typography>
                <Avatar
                  src={entry.user.avatar}
                  sx={{
                    width: 80,
                    height: 80,
                    mx: 'auto',
                    mb: 2,
                    border: entry.rank === 1 ? `3px solid ${theme.palette.warning.main}` : undefined,
                  }}
                >
                  {entry.user.name[0]}
                </Avatar>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {entry.user.name}
                </Typography>
                <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2, mt: 2 }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {formatCurrency(entry.stats.sent + entry.stats.received)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Total Volume
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {entry.stats.streak}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Day Streak 🔥
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Full Leaderboard */}
      <Paper>
        <List>
          {leaderboard.map((entry, index) => (
            <React.Fragment key={entry.user.id}>
              {index > 0 && <Divider />}
              <ListItem>
                <Box sx={{ display: 'flex', alignItems: 'center', mr: 2 }}>
                  <Typography variant="h6" sx={{ minWidth: 30, fontWeight: 700 }}>
                    {entry.rank}
                  </Typography>
                  {entry.change === 'up' && <ArrowUpIcon color="success" fontSize="small" />}
                  {entry.change === 'down' && <ArrowDownIcon color="error" fontSize="small" />}
                </Box>
                <ListItemAvatar>
                  <Badge
                    overlap="circular"
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    badgeContent={
                      entry.user.verified && (
                        <Box
                          sx={{
                            width: 20,
                            height: 20,
                            borderRadius: '50%',
                            bgcolor: 'background.paper',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <CheckIcon sx={{ fontSize: 14, color: theme.palette.primary.main }} />
                        </Box>
                      )
                    }
                  >
                    <Avatar src={entry.user.avatar}>
                      {entry.user.name[0]}
                    </Avatar>
                  </Badge>
                </ListItemAvatar>
                <ListItemText
                  primary={entry.user.name}
                  secondary={
                    <Box sx={{ display: 'flex', gap: 3 }}>
                      <Typography variant="caption">
                        Sent: {formatCurrency(entry.stats.sent)}
                      </Typography>
                      <Typography variant="caption">
                        Received: {formatCurrency(entry.stats.received)}
                      </Typography>
                      <Typography variant="caption">
                        {entry.stats.transactions} transactions
                      </Typography>
                    </Box>
                  }
                />
                <ListItemSecondaryAction>
                  {entry.stats.streak > 0 && (
                    <Chip
                      icon={<FireIcon />}
                      label={`${entry.stats.streak} days`}
                      size="small"
                      color="error"
                    />
                  )}
                </ListItemSecondaryAction>
              </ListItem>
            </React.Fragment>
          ))}
        </List>
      </Paper>
    </Box>
  );

  return (
    <Box>
      {/* View Selector */}
      <Paper sx={{ p: 1, mb: 3 }}>
        <Grid container spacing={1}>
          <Grid item xs={4}>
            <Button
              fullWidth
              variant={selectedView === 'trending' ? 'contained' : 'text'}
              startIcon={<FireIcon />}
              onClick={() => setSelectedView('trending')}
            >
              Trending
            </Button>
          </Grid>
          <Grid item xs={4}>
            <Button
              fullWidth
              variant={selectedView === 'friends' ? 'contained' : 'text'}
              startIcon={<GroupIcon />}
              onClick={() => setSelectedView('friends')}
            >
              Friends
            </Button>
          </Grid>
          <Grid item xs={4}>
            <Button
              fullWidth
              variant={selectedView === 'leaderboard' ? 'contained' : 'text'}
              startIcon={<TrophyIcon />}
              onClick={() => setSelectedView('leaderboard')}
            >
              Leaderboard
            </Button>
          </Grid>
        </Grid>
      </Paper>

      {/* Content */}
      {selectedView === 'trending' && renderTrendingView()}
      {selectedView === 'leaderboard' && renderLeaderboardView()}
      {selectedView === 'friends' && (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <GroupIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary">
            Friends activity coming soon
          </Typography>
        </Box>
      )}

      {/* Activity Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleViewDetails}>
          <ListItemIcon>
            <ViewIcon />
          </ListItemIcon>
          <ListItemText primary="View Details" />
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <ShareIcon />
          </ListItemIcon>
          <ListItemText primary="Share" />
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <FlagIcon />
          </ListItemIcon>
          <ListItemText primary="Report" />
        </MenuItem>
      </Menu>

      {/* Details Dialog */}
      <Dialog
        open={Boolean(detailsDialog)}
        onClose={() => setDetailsDialog(null)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Transaction Details</DialogTitle>
        <DialogContent>
          {detailsDialog && (
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
                <Avatar src={detailsDialog.from.avatar}>
                  {detailsDialog.from.name[0]}
                </Avatar>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                    {detailsDialog.from.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {format(parseISO(detailsDialog.timestamp), 'MMMM d, yyyy h:mm a')}
                  </Typography>
                </Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {formatCurrency(detailsDialog.amount)}
                </Typography>
              </Box>

              {detailsDialog.description && (
                <Paper sx={{ p: 2, mb: 2, bgcolor: 'background.default' }}>
                  <Typography variant="body1">{detailsDialog.description}</Typography>
                </Paper>
              )}

              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    Category
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {getCategoryIcon(detailsDialog.category)}
                    <Typography variant="body2">{detailsDialog.category}</Typography>
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="text.secondary">
                    Status
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {getStatusIcon(detailsDialog.status)}
                    <Typography variant="body2">
                      {detailsDialog.status.charAt(0).toUpperCase() + detailsDialog.status.slice(1)}
                    </Typography>
                  </Box>
                </Grid>
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsDialog(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PaymentActivity;