import React, { useState, useEffect } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  LinearProgress,
  IconButton,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Paper,
  Tab,
  Tabs,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  InputAdornment,
  Slider,
  Switch,
  FormControlLabel,
  Alert,
  Tooltip,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
  useTheme,
  alpha,
  Skeleton,
  Badge,
  Menu,
  Divider,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SavingsIcon from '@mui/icons-material/Savings';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import TimelineIcon from '@mui/icons-material/Timeline';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import NotificationsIcon from '@mui/icons-material/Notifications';
import ShareIcon from '@mui/icons-material/Share';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import PauseIcon from '@mui/icons-material/Pause';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import CategoryIcon from '@mui/icons-material/Category';
import SchoolIcon from '@mui/icons-material/School';
import HomeIcon from '@mui/icons-material/Home';
import CarIcon from '@mui/icons-material/DirectionsCar';
import BeachIcon from '@mui/icons-material/Beach';
import BusinessIcon from '@mui/icons-material/Business';
import HealthIcon from '@mui/icons-material/LocalHospital';
import GiftIcon from '@mui/icons-material/CardGiftcard';
import VacationIcon from '@mui/icons-material/FlightTakeoff';
import WeddingIcon from '@mui/icons-material/Favorite';
import GadgetIcon from '@mui/icons-material/Devices';
import WalletIcon from '@mui/icons-material/AccountBalanceWallet';
import AutoGraphIcon from '@mui/icons-material/AutoGraph';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import MoreVertIcon from '@mui/icons-material/MoreVert';;
import { format, formatDistanceToNow, addMonths } from 'date-fns';
import { Line, Bar, Doughnut } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip as ChartTooltip,
  Legend,
  Filler,
} from 'chart.js';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  ChartTooltip,
  Legend,
  Filler
);

interface SavingsGoal {
  id: string;
  name: string;
  description?: string;
  category: string;
  targetAmount: number;
  currentAmount: number;
  currency: string;
  targetDate?: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'ACTIVE' | 'COMPLETED' | 'PAUSED' | 'ABANDONED';
  progressPercentage: number;
  imageUrl?: string;
  icon?: string;
  color?: string;
  autoSaveEnabled: boolean;
  flexibleTarget: boolean;
  allowWithdrawals: boolean;
  requiredMonthlySaving?: number;
  averageMonthlyContribution?: number;
  projectedCompletionDate?: string;
  currentStreak: number;
  totalContributions: number;
  lastContributionAt?: string;
  interestRate?: number;
  interestEarned: number;
  createdAt: string;
}

interface SavingsAnalytics {
  totalSaved: number;
  totalTarget: number;
  overallProgress: number;
  activeGoalsCount: number;
  completedGoalsCount: number;
  monthlyContributions: number;
  averageContribution: number;
  savingsRate: number;
  contributionTrends: Array<{
    month: string;
    amount: number;
  }>;
  goalProgress: Array<{
    goalId: string;
    name: string;
    progress: number;
  }>;
}

interface AutoSaveRule {
  id: string;
  goalId: string;
  ruleType: 'FIXED_AMOUNT' | 'PERCENTAGE_OF_INCOME' | 'ROUND_UP' | 'SPARE_CHANGE';
  amount?: number;
  percentage?: number;
  frequency?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  isActive: boolean;
  lastExecutedAt?: string;
  totalSaved: number;
}

const categoryIcons: Record<string, React.ReactElement> = {
  EMERGENCY_FUND: <AccountBalanceIcon />,
  VACATION: <VacationIcon />,
  HOME: <HomeIcon />,
  CAR: <CarIcon />,
  EDUCATION: <SchoolIcon />,
  RETIREMENT: <AccountBalanceIcon />,
  WEDDING: <WeddingIcon />,
  BUSINESS: <BusinessIcon />,
  GADGET: <GadgetIcon />,
  HEALTH: <HealthIcon />,
  GIFT: <GiftIcon />,
  OTHER: <CategoryIcon />,
};

const priorityColors = {
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'error',
  CRITICAL: 'error',
};

const SavingsGoalsDashboard: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [goals, setGoals] = useState<SavingsGoal[]>([]);
  const [analytics, setAnalytics] = useState<SavingsAnalytics | null>(null);
  const [autoSaveRules, setAutoSaveRules] = useState<AutoSaveRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedGoal, setSelectedGoal] = useState<SavingsGoal | null>(null);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [showContributeDialog, setShowContributeDialog] = useState(false);
  const [showAutoSaveDialog, setShowAutoSaveDialog] = useState(false);
  const [goalMenuAnchor, setGoalMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedGoalForMenu, setSelectedGoalForMenu] = useState<SavingsGoal | null>(null);

  // Form state
  const [contributeAmount, setContributeAmount] = useState('');
  const [withdrawAmount, setWithdrawAmount] = useState('');
  const [newGoalData, setNewGoalData] = useState({
    name: '',
    description: '',
    category: 'OTHER',
    targetAmount: '',
    targetDate: '',
    priority: 'MEDIUM',
    autoSaveEnabled: false,
    flexibleTarget: false,
    allowWithdrawals: true,
  });

  useEffect(() => {
    loadGoals();
    loadAnalytics();
    loadAutoSaveRules();
  }, []);

  const loadGoals = async () => {
    try {
      const response = await fetch('/api/savings/goals');
      const data = await response.json();
      setGoals(data.content || []);
    } catch (error) {
      console.error('Failed to load goals:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadAnalytics = async () => {
    try {
      const response = await fetch('/api/savings/analytics');
      const data = await response.json();
      setAnalytics(data);
    } catch (error) {
      console.error('Failed to load analytics:', error);
    }
  };

  const loadAutoSaveRules = async () => {
    try {
      const response = await fetch('/api/savings/auto-save-rules');
      const data = await response.json();
      setAutoSaveRules(data);
    } catch (error) {
      console.error('Failed to load auto-save rules:', error);
    }
  };

  const createGoal = async () => {
    try {
      const response = await fetch('/api/savings/goals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...newGoalData,
          targetAmount: parseFloat(newGoalData.targetAmount),
        }),
      });
      
      if (response.ok) {
        setShowCreateDialog(false);
        loadGoals();
        loadAnalytics();
      }
    } catch (error) {
      console.error('Failed to create goal:', error);
    }
  };

  const contributeToGoal = async (goalId: string) => {
    try {
      const response = await fetch(`/api/savings/goals/${goalId}/contribute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: parseFloat(contributeAmount),
          type: 'MANUAL',
          paymentMethod: 'BANK_ACCOUNT',
        }),
      });
      
      if (response.ok) {
        setShowContributeDialog(false);
        setContributeAmount('');
        loadGoals();
        loadAnalytics();
      }
    } catch (error) {
      console.error('Failed to contribute:', error);
    }
  };

  const handleGoalMenuClick = (event: React.MouseEvent<HTMLElement>, goal: SavingsGoal) => {
    setGoalMenuAnchor(event.currentTarget);
    setSelectedGoalForMenu(goal);
  };

  const handleGoalMenuClose = () => {
    setGoalMenuAnchor(null);
    setSelectedGoalForMenu(null);
  };

  const pauseGoal = async (goalId: string) => {
    try {
      await fetch(`/api/savings/goals/${goalId}/pause`, { method: 'POST' });
      loadGoals();
    } catch (error) {
      console.error('Failed to pause goal:', error);
    }
  };

  const resumeGoal = async (goalId: string) => {
    try {
      await fetch(`/api/savings/goals/${goalId}/resume`, { method: 'POST' });
      loadGoals();
    } catch (error) {
      console.error('Failed to resume goal:', error);
    }
  };

  const deleteGoal = async (goalId: string) => {
    if (window.confirm('Are you sure you want to delete this goal?')) {
      try {
        await fetch(`/api/savings/goals/${goalId}`, { method: 'DELETE' });
        loadGoals();
        loadAnalytics();
      } catch (error) {
        console.error('Failed to delete goal:', error);
      }
    }
  };

  const renderGoalCard = (goal: SavingsGoal) => (
    <Card 
      key={goal.id}
      sx={{ 
        height: '100%',
        position: 'relative',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        '&:hover': {
          transform: 'translateY(-4px)',
          boxShadow: theme.shadows[8],
        },
        ...(goal.status === 'COMPLETED' && {
          bgcolor: alpha(theme.palette.success.main, 0.05),
          borderColor: theme.palette.success.main,
        }),
        ...(goal.status === 'PAUSED' && {
          opacity: 0.7,
        }),
      }}
      onClick={() => setSelectedGoal(goal)}
    >
      <CardContent>
        {/* Header */}
        <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
          <Box display="flex" alignItems="center">
            <Avatar
              sx={{ 
                bgcolor: goal.color || theme.palette.primary.main,
                width: 48,
                height: 48,
                mr: 2,
              }}
            >
              {categoryIcons[goal.category] || <SavingsIcon />}
            </Avatar>
            <Box>
              <Typography variant="h6" gutterBottom>
                {goal.name}
              </Typography>
              <Box display="flex" gap={1}>
                <Chip 
                  label={goal.status} 
                  size="small"
                  color={goal.status === 'COMPLETED' ? 'success' : 'default'}
                />
                <Chip 
                  label={goal.priority} 
                  size="small"
                  color={priorityColors[goal.priority] as any}
                />
              </Box>
            </Box>
          </Box>
          <IconButton
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              handleGoalMenuClick(e, goal);
            }}
          >
            <MoreVertIcon />
          </IconButton>
        </Box>

        {/* Progress */}
        <Box mb={2}>
          <Box display="flex" justifyContent="space-between" mb={1}>
            <Typography variant="body2" color="textSecondary">
              Progress
            </Typography>
            <Typography variant="body2" fontWeight="bold">
              {goal.progressPercentage.toFixed(1)}%
            </Typography>
          </Box>
          <LinearProgress 
            variant="determinate" 
            value={goal.progressPercentage}
            sx={{ 
              height: 8,
              borderRadius: 4,
              bgcolor: alpha(theme.palette.primary.main, 0.1),
              '& .MuiLinearProgress-bar': {
                borderRadius: 4,
                background: goal.status === 'COMPLETED' 
                  ? theme.palette.success.main
                  : `linear-gradient(90deg, ${theme.palette.primary.main} 0%, ${theme.palette.primary.dark} 100%)`,
              },
            }}
          />
          <Box display="flex" justifyContent="space-between" mt={1}>
            <Typography variant="caption" color="textSecondary">
              ${goal.currentAmount.toFixed(2)}
            </Typography>
            <Typography variant="caption" color="textSecondary">
              ${goal.targetAmount.toFixed(2)}
            </Typography>
          </Box>
        </Box>

        {/* Stats */}
        <Grid container spacing={1}>
          {goal.targetDate && (
            <Grid item xs={6}>
              <Box display="flex" alignItems="center">
                <CalendarTodayIcon sx={{ fontSize: 16, mr: 0.5, color: 'text.secondary' }} />
                <Typography variant="caption" color="textSecondary">
                  {format(new Date(goal.targetDate), 'MMM yyyy')}
                </Typography>
              </Box>
            </Grid>
          )}
          {goal.requiredMonthlySaving && (
            <Grid item xs={6}>
              <Box display="flex" alignItems="center">
                <AttachMoneyIcon sx={{ fontSize: 16, mr: 0.5, color: 'text.secondary' }} />
                <Typography variant="caption" color="textSecondary">
                  ${goal.requiredMonthlySaving.toFixed(0)}/mo
                </Typography>
              </Box>
            </Grid>
          )}
          {goal.currentStreak > 0 && (
            <Grid item xs={6}>
              <Box display="flex" alignItems="center">
                <EmojiEventsIcon sx={{ fontSize: 16, mr: 0.5, color: 'warning.main' }} />
                <Typography variant="caption" color="textSecondary">
                  {goal.currentStreak} day streak
                </Typography>
              </Box>
            </Grid>
          )}
          {goal.autoSaveEnabled && (
            <Grid item xs={6}>
              <Box display="flex" alignItems="center">
                <AutoGraphIcon sx={{ fontSize: 16, mr: 0.5, color: 'success.main' }} />
                <Typography variant="caption" color="textSecondary">
                  Auto-save on
                </Typography>
              </Box>
            </Grid>
          )}
        </Grid>

        {/* Quick Actions */}
        {goal.status === 'ACTIVE' && (
          <Box display="flex" gap={1} mt={2}>
            <Button
              size="small"
              variant="contained"
              startIcon={<AddIcon />}
              onClick={(e) => {
                e.stopPropagation();
                setSelectedGoal(goal);
                setShowContributeDialog(true);
              }}
              fullWidth
            >
              Contribute
            </Button>
          </Box>
        )}
      </CardContent>
    </Card>
  );

  const renderAnalytics = () => {
    if (!analytics) return null;

    const contributionChartData = {
      labels: analytics.contributionTrends.map(t => t.month),
      datasets: [
        {
          label: 'Monthly Contributions',
          data: analytics.contributionTrends.map(t => t.amount),
          fill: true,
          backgroundColor: alpha(theme.palette.primary.main, 0.1),
          borderColor: theme.palette.primary.main,
          tension: 0.4,
        },
      ],
    };

    const progressChartData = {
      labels: analytics.goalProgress.map(g => g.name),
      datasets: [
        {
          label: 'Progress %',
          data: analytics.goalProgress.map(g => g.progress),
          backgroundColor: analytics.goalProgress.map((_, i) => 
            `hsla(${(i * 360) / analytics.goalProgress.length}, 70%, 50%, 0.8)`
          ),
        },
      ],
    };

    return (
      <Grid container spacing={3}>
        {/* Summary Cards */}
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" variant="body2">
                    Total Saved
                  </Typography>
                  <Typography variant="h5">
                    ${analytics.totalSaved.toFixed(2)}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.success.main }}>
                  <SavingsIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" variant="body2">
                    Overall Progress
                  </Typography>
                  <Typography variant="h5">
                    {analytics.overallProgress.toFixed(1)}%
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.primary.main }}>
                  <TrendingUpIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" variant="body2">
                    Monthly Contribution
                  </Typography>
                  <Typography variant="h5">
                    ${analytics.monthlyContributions.toFixed(2)}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.warning.main }}>
                  <TimelineIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between">
                <Box>
                  <Typography color="textSecondary" variant="body2">
                    Active Goals
                  </Typography>
                  <Typography variant="h5">
                    {analytics.activeGoalsCount}
                  </Typography>
                </Box>
                <Avatar sx={{ bgcolor: theme.palette.info.main }}>
                  <EmojiEventsIcon />
                </Avatar>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Charts */}
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Contribution Trends
            </Typography>
            <Box height={300}>
              <Line 
                data={contributionChartData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: { display: false },
                  },
                  scales: {
                    y: {
                      beginAtZero: true,
                      ticks: {
                        callback: (value) => `$${value}`,
                      },
                    },
                  },
                }}
              />
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Goal Progress Distribution
            </Typography>
            <Box height={300}>
              <Doughnut
                data={progressChartData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: {
                      position: 'bottom',
                    },
                  },
                }}
              />
            </Box>
          </Paper>
        </Grid>

        {/* Auto-Save Rules */}
        <Grid item xs={12}>
          <Paper sx={{ p: 3 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6">
                Auto-Save Rules
              </Typography>
              <Button
                startIcon={<AddIcon />}
                onClick={() => setShowAutoSaveDialog(true)}
              >
                Add Rule
              </Button>
            </Box>
            <List>
              {autoSaveRules.map((rule) => (
                <ListItem key={rule.id}>
                  <ListItemAvatar>
                    <Avatar sx={{ bgcolor: rule.isActive ? 'success.main' : 'grey.500' }}>
                      <AutoGraphIcon />
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="body1">
                          {rule.ruleType.replace(/_/g, ' ')}
                        </Typography>
                        {rule.amount && (
                          <Chip label={`$${rule.amount}`} size="small" />
                        )}
                        {rule.percentage && (
                          <Chip label={`${rule.percentage}%`} size="small" />
                        )}
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography variant="caption" display="block">
                          Total saved: ${rule.totalSaved.toFixed(2)}
                        </Typography>
                        {rule.lastExecutedAt && (
                          <Typography variant="caption" color="textSecondary">
                            Last executed: {formatDistanceToNow(new Date(rule.lastExecutedAt), { addSuffix: true })}
                          </Typography>
                        )}
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Switch
                      checked={rule.isActive}
                      onChange={() => {/* Toggle rule */}}
                    />
                  </ListItemSecondaryAction>
                </ListItem>
              ))}
            </List>
          </Paper>
        </Grid>
      </Grid>
    );
  };

  const renderGoalsList = () => (
    <Grid container spacing={3}>
      {loading ? (
        [1, 2, 3, 4].map((n) => (
          <Grid item xs={12} sm={6} md={4} key={n}>
            <Skeleton variant="rectangular" height={280} sx={{ borderRadius: 2 }} />
          </Grid>
        ))
      ) : (
        goals.map((goal) => (
          <Grid item xs={12} sm={6} md={4} key={goal.id}>
            {renderGoalCard(goal)}
          </Grid>
        ))
      )}
    </Grid>
  );

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">
          Savings Goals
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setShowCreateDialog(true)}
          size="large"
        >
          Create Goal
        </Button>
      </Box>

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
          <Tab label="My Goals" />
          <Tab label="Analytics" />
          <Tab label="Recommendations" />
        </Tabs>
      </Paper>

      {/* Content */}
      {activeTab === 0 && renderGoalsList()}
      {activeTab === 1 && renderAnalytics()}

      {/* Create Goal Dialog */}
      <Dialog
        open={showCreateDialog}
        onClose={() => setShowCreateDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create Savings Goal</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Goal Name"
                value={newGoalData.name}
                onChange={(e) => setNewGoalData({ ...newGoalData, name: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Description"
                multiline
                rows={2}
                value={newGoalData.description}
                onChange={(e) => setNewGoalData({ ...newGoalData, description: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Category</InputLabel>
                <Select
                  value={newGoalData.category}
                  onChange={(e) => setNewGoalData({ ...newGoalData, category: e.target.value })}
                  label="Category"
                >
                  {Object.keys(categoryIcons).map((cat) => (
                    <MenuItem key={cat} value={cat}>
                      <Box display="flex" alignItems="center">
                        {categoryIcons[cat]}
                        <Typography sx={{ ml: 1 }}>
                          {cat.replace(/_/g, ' ')}
                        </Typography>
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Target Amount"
                type="number"
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
                value={newGoalData.targetAmount}
                onChange={(e) => setNewGoalData({ ...newGoalData, targetAmount: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Target Date"
                type="date"
                InputLabelProps={{ shrink: true }}
                value={newGoalData.targetDate}
                onChange={(e) => setNewGoalData({ ...newGoalData, targetDate: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Priority</InputLabel>
                <Select
                  value={newGoalData.priority}
                  onChange={(e) => setNewGoalData({ ...newGoalData, priority: e.target.value })}
                  label="Priority"
                >
                  <MenuItem value="LOW">Low</MenuItem>
                  <MenuItem value="MEDIUM">Medium</MenuItem>
                  <MenuItem value="HIGH">High</MenuItem>
                  <MenuItem value="CRITICAL">Critical</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={
                  <Switch
                    checked={newGoalData.autoSaveEnabled}
                    onChange={(e) => setNewGoalData({ ...newGoalData, autoSaveEnabled: e.target.checked })}
                  />
                }
                label="Enable Auto-Save"
              />
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={
                  <Switch
                    checked={newGoalData.allowWithdrawals}
                    onChange={(e) => setNewGoalData({ ...newGoalData, allowWithdrawals: e.target.checked })}
                  />
                }
                label="Allow Withdrawals"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowCreateDialog(false)}>Cancel</Button>
          <Button onClick={createGoal} variant="contained">Create</Button>
        </DialogActions>
      </Dialog>

      {/* Contribute Dialog */}
      <Dialog
        open={showContributeDialog}
        onClose={() => setShowContributeDialog(false)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>Contribute to {selectedGoal?.name}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Amount"
            type="number"
            InputProps={{
              startAdornment: <InputAdornment position="start">$</InputAdornment>,
            }}
            value={contributeAmount}
            onChange={(e) => setContributeAmount(e.target.value)}
            sx={{ mt: 2 }}
          />
          {selectedGoal && selectedGoal.requiredMonthlySaving && (
            <Alert severity="info" sx={{ mt: 2 }}>
              Recommended monthly contribution: ${selectedGoal.requiredMonthlySaving.toFixed(2)}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowContributeDialog(false)}>Cancel</Button>
          <Button 
            onClick={() => selectedGoal && contributeToGoal(selectedGoal.id)} 
            variant="contained"
            disabled={!contributeAmount || parseFloat(contributeAmount) <= 0}
          >
            Contribute
          </Button>
        </DialogActions>
      </Dialog>

      {/* Goal Menu */}
      <Menu
        anchorEl={goalMenuAnchor}
        open={Boolean(goalMenuAnchor)}
        onClose={handleGoalMenuClose}
      >
        {selectedGoalForMenu && (
          <>
            <MenuItem onClick={() => {
              handleGoalMenuClose();
              // Edit goal
            }}>
              <EditIcon fontSize="small" sx={{ mr: 1 }} />
              Edit
            </MenuItem>
            {selectedGoalForMenu.status === 'ACTIVE' ? (
              <MenuItem onClick={() => {
                handleGoalMenuClose();
                pauseGoal(selectedGoalForMenu.id);
              }}>
                <PauseIcon fontSize="small" sx={{ mr: 1 }} />
                Pause
              </MenuItem>
            ) : selectedGoalForMenu.status === 'PAUSED' && (
              <MenuItem onClick={() => {
                handleGoalMenuClose();
                resumeGoal(selectedGoalForMenu.id);
              }}>
                <PlayArrowIcon fontSize="small" sx={{ mr: 1 }} />
                Resume
              </MenuItem>
            )}
            <MenuItem onClick={() => {
              handleGoalMenuClose();
              // Share goal
            }}>
              <ShareIcon fontSize="small" sx={{ mr: 1 }} />
              Share
            </MenuItem>
            <Divider />
            <MenuItem onClick={() => {
              handleGoalMenuClose();
              deleteGoal(selectedGoalForMenu.id);
            }} sx={{ color: 'error.main' }}>
              <DeleteIcon fontSize="small" sx={{ mr: 1 }} />
              Delete
            </MenuItem>
          </>
        )}
      </Menu>
    </Box>
  );
};

export default SavingsGoalsDashboard;