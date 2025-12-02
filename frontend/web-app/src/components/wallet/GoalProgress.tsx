import React, { useState } from 'react';
import {
  Paper,
  Typography,
  Box,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Chip,
  Button,
  IconButton,
  LinearProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Collapse,
  useTheme,
  alpha,
} from '@mui/material';
import SavingsIcon from '@mui/icons-material/Savings';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import CalendarIcon from '@mui/icons-material/CalendarToday';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import FlagIcon from '@mui/icons-material/Flag';
import HomeIcon from '@mui/icons-material/Home';
import CarIcon from '@mui/icons-material/DirectionsCar';
import SchoolIcon from '@mui/icons-material/School';
import VacationIcon from '@mui/icons-material/FlightTakeoff';
import PhoneIcon from '@mui/icons-material/Phone';
import ComputerIcon from '@mui/icons-material/Computer';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ScheduleIcon from '@mui/icons-material/Schedule';;
import { format, differenceInDays, parseISO } from 'date-fns';
import { Goal } from '../../types/wallet';
import { formatCurrency, formatPercentage } from '../../utils/formatters';

interface GoalProgressProps {
  goals: Goal[];
  onManage?: () => void;
  onAddGoal?: () => void;
  onEditGoal?: (goal: Goal) => void;
  onAddToGoal?: (goalId: string, amount: number) => void;
}

const GoalProgress: React.FC<GoalProgressProps> = ({
  goals = [],
  onManage,
  onAddGoal,
  onEditGoal,
  onAddToGoal,
}) => {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(false);
  const [selectedGoal, setSelectedGoal] = useState<Goal | null>(null);
  const [addAmount, setAddAmount] = useState('');
  const [showAddDialog, setShowAddDialog] = useState(false);

  const getCategoryIcon = (category: string) => {
    switch (category.toLowerCase()) {
      case 'house':
      case 'home':
        return <HomeIcon />;
      case 'car':
      case 'vehicle':
        return <CarIcon />;
      case 'education':
      case 'school':
        return <SchoolIcon />;
      case 'vacation':
      case 'travel':
        return <VacationIcon />;
      case 'electronics':
      case 'gadgets':
        return <ComputerIcon />;
      case 'emergency':
        return <FlagIcon />;
      default:
        return <SavingsIcon />;
    }
  };

  const getCategoryColor = (category: string) => {
    switch (category.toLowerCase()) {
      case 'house':
      case 'home':
        return theme.palette.success.main;
      case 'car':
      case 'vehicle':
        return theme.palette.info.main;
      case 'education':
      case 'school':
        return theme.palette.warning.main;
      case 'vacation':
      case 'travel':
        return theme.palette.error.main;
      case 'electronics':
      case 'gadgets':
        return theme.palette.secondary.main;
      case 'emergency':
        return theme.palette.warning.main;
      default:
        return theme.palette.primary.main;
    }
  };

  const getProgressPercentage = (goal: Goal) => {
    return Math.min((goal.currentAmount / goal.targetAmount) * 100, 100);
  };

  const getDaysRemaining = (targetDate: string) => {
    return differenceInDays(parseISO(targetDate), new Date());
  };

  const getGoalStatus = (goal: Goal) => {
    const progress = getProgressPercentage(goal);
    const daysRemaining = getDaysRemaining(goal.targetDate);
    
    if (progress >= 100) return { status: 'Completed', color: 'success' };
    if (daysRemaining < 0) return { status: 'Overdue', color: 'error' };
    if (daysRemaining <= 30) return { status: 'Due Soon', color: 'warning' };
    if (progress >= 75) return { status: 'On Track', color: 'success' };
    if (progress >= 50) return { status: 'Good Progress', color: 'info' };
    return { status: 'Just Started', color: 'default' };
  };

  const handleAddToGoal = () => {
    if (selectedGoal && addAmount) {
      onAddToGoal?.(selectedGoal.id, parseFloat(addAmount));
      setShowAddDialog(false);
      setAddAmount('');
      setSelectedGoal(null);
    }
  };

  const activeGoals = goals.filter(goal => goal.isActive);
  const completedGoals = goals.filter(goal => getProgressPercentage(goal) >= 100);

  const renderGoalSummary = () => {
    const totalTargetAmount = activeGoals.reduce((sum, goal) => sum + goal.targetAmount, 0);
    const totalCurrentAmount = activeGoals.reduce((sum, goal) => sum + goal.currentAmount, 0);
    const overallProgress = totalTargetAmount > 0 ? (totalCurrentAmount / totalTargetAmount) * 100 : 0;

    return (
      <Box sx={{ mb: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
          Overall Progress
        </Typography>
        
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {formatCurrency(totalCurrentAmount)} of {formatCurrency(totalTargetAmount)}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 500 }}>
            {formatPercentage(overallProgress)}
          </Typography>
        </Box>
        
        <LinearProgress
          variant="determinate"
          value={overallProgress}
          sx={{
            height: 8,
            borderRadius: 4,
            backgroundColor: alpha(theme.palette.grey[300], 0.3),
            '& .MuiLinearProgress-bar': {
              borderRadius: 4,
            },
          }}
        />
        
        <Box sx={{ display: 'flex', gap: 2, mt: 2 }}>
          <Chip
            label={`${activeGoals.length} Active`}
            size="small"
            color="primary"
            variant="outlined"
          />
          <Chip
            label={`${completedGoals.length} Completed`}
            size="small"
            color="success"
            variant="outlined"
          />
        </Box>
      </Box>
    );
  };

  const renderGoalsList = () => (
    <List sx={{ py: 0 }}>
      {activeGoals.slice(0, expanded ? activeGoals.length : 3).map((goal) => {
        const progress = getProgressPercentage(goal);
        const daysRemaining = getDaysRemaining(goal.targetDate);
        const status = getGoalStatus(goal);
        const categoryColor = getCategoryColor(goal.category);

        return (
          <ListItem key={goal.id} sx={{ px: 0, py: 2 }}>
            <ListItemIcon>
              <Avatar
                sx={{
                  bgcolor: alpha(categoryColor, 0.1),
                  color: categoryColor,
                }}
              >
                {getCategoryIcon(goal.category)}
              </Avatar>
            </ListItemIcon>
            
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 500 }}>
                    {goal.name}
                  </Typography>
                  <Chip
                    label={status.status}
                    size="small"
                    color={status.color as any}
                    variant="outlined"
                  />
                  {goal.autoSaveEnabled && (
                    <Chip
                      icon={<AutoAwesomeIcon />}
                      label="Auto"
                      size="small"
                      color="info"
                      variant="outlined"
                    />
                  )}
                </Box>
              }
              secondary={
                <Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" color="text.secondary">
                      {formatCurrency(goal.currentAmount, goal.currency)} of {formatCurrency(goal.targetAmount, goal.currency)}
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {formatPercentage(progress)}
                    </Typography>
                  </Box>
                  
                  <LinearProgress
                    variant="determinate"
                    value={progress}
                    sx={{
                      height: 6,
                      borderRadius: 3,
                      backgroundColor: alpha(theme.palette.grey[300], 0.3),
                      '& .MuiLinearProgress-bar': {
                        backgroundColor: categoryColor,
                        borderRadius: 3,
                      },
                      mb: 1,
                    }}
                  />
                  
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <CalendarIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                      <Typography variant="caption" color="text.secondary">
                        {daysRemaining > 0 
                          ? `${daysRemaining} days left`
                          : daysRemaining === 0 
                            ? 'Due today'
                            : `${Math.abs(daysRemaining)} days overdue`
                        }
                      </Typography>
                    </Box>
                    
                    {goal.autoSaveEnabled && (
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <ScheduleIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                        <Typography variant="caption" color="text.secondary">
                          {formatCurrency(goal.autoSaveAmount || 0)} {goal.autoSaveFrequency?.toLowerCase()}
                        </Typography>
                      </Box>
                    )}
                  </Box>
                </Box>
              }
            />
            
            <ListItemSecondaryAction>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<AddIcon />}
                  onClick={() => {
                    setSelectedGoal(goal);
                    setShowAddDialog(true);
                  }}
                  disabled={progress >= 100}
                >
                  Add
                </Button>
                <IconButton
                  size="small"
                  onClick={() => onEditGoal?.(goal)}
                >
                  <EditIcon />
                </IconButton>
              </Box>
            </ListItemSecondaryAction>
          </ListItem>
        );
      })}
    </List>
  );

  return (
    <Paper sx={{ p: 0 }}>
      <Box
        sx={{
          p: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
        }}
        onClick={() => setExpanded(!expanded)}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <SavingsIcon />
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Goals
          </Typography>
          {activeGoals.length > 0 && (
            <Chip
              label={activeGoals.length}
              size="small"
              color="primary"
            />
          )}
        </Box>
        <IconButton size="small">
          {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
        </IconButton>
      </Box>

      <Box sx={{ px: 2, pb: 2 }}>
        {activeGoals.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 3 }}>
            <SavingsIcon sx={{ fontSize: 40, color: 'text.secondary', mb: 1 }} />
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              No savings goals yet
            </Typography>
            <Button
              variant="outlined"
              startIcon={<AddIcon />}
              onClick={onAddGoal}
            >
              Create Your First Goal
            </Button>
          </Box>
        ) : (
          <>
            {renderGoalSummary()}
            {renderGoalsList()}
            
            {!expanded && activeGoals.length > 3 && (
              <Box sx={{ textAlign: 'center', mt: 1 }}>
                <Button
                  size="small"
                  onClick={() => setExpanded(true)}
                >
                  Show {activeGoals.length - 3} more goals
                </Button>
              </Box>
            )}
          </>
        )}
        
        <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
          <Button
            fullWidth
            variant="outlined"
            startIcon={<AddIcon />}
            onClick={onAddGoal}
          >
            New Goal
          </Button>
          <Button
            fullWidth
            variant="contained"
            startIcon={<TrendingUpIcon />}
            onClick={onManage}
          >
            Manage
          </Button>
        </Box>
      </Box>

      {/* Add Money to Goal Dialog */}
      <Dialog
        open={showAddDialog}
        onClose={() => setShowAddDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar
              sx={{
                bgcolor: alpha(getCategoryColor(selectedGoal?.category || ''), 0.1),
                color: getCategoryColor(selectedGoal?.category || ''),
              }}
            >
              {getCategoryIcon(selectedGoal?.category || '')}
            </Avatar>
            <Box>
              <Typography variant="h6">
                Add to {selectedGoal?.name}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Current: {formatCurrency(selectedGoal?.currentAmount || 0, selectedGoal?.currency)}
              </Typography>
            </Box>
          </Box>
        </DialogTitle>
        
        <DialogContent>
          <TextField
            fullWidth
            label="Amount to add"
            value={addAmount}
            onChange={(e) => setAddAmount(e.target.value)}
            type="number"
            inputProps={{ min: 0, step: 0.01 }}
            InputProps={{
              startAdornment: (
                <Typography sx={{ mr: 1 }}>
                  {selectedGoal?.currency === 'USD' ? '$' : selectedGoal?.currency}
                </Typography>
              ),
            }}
            sx={{ mb: 2 }}
          />
          
          {selectedGoal && (
            <Box sx={{ p: 2, bgcolor: alpha(theme.palette.info.main, 0.05), borderRadius: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Remaining needed: {formatCurrency(
                  Math.max(0, selectedGoal.targetAmount - selectedGoal.currentAmount),
                  selectedGoal.currency
                )}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Target date: {format(parseISO(selectedGoal.targetDate), 'MMM d, yyyy')}
              </Typography>
            </Box>
          )}
        </DialogContent>
        
        <DialogActions>
          <Button onClick={() => setShowAddDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleAddToGoal}
            disabled={!addAmount || parseFloat(addAmount) <= 0}
            startIcon={<AddIcon />}
          >
            Add {addAmount && formatCurrency(parseFloat(addAmount), selectedGoal?.currency)}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
};

export default GoalProgress;