import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  Avatar,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  LinearProgress,
  IconButton,
  Tooltip,
  Alert,
  Collapse,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  Paper,
  Stack,
  Badge,
  Fade,
  Zoom,
} from '@mui/material';
import TrophyIcon from '@mui/icons-material/EmojiEvents';
import FireIcon from '@mui/icons-material/LocalFireDepartment';
import ProgressIcon from '@mui/icons-material/Timeline';
import ShareIcon from '@mui/icons-material/Share';
import TimerIcon from '@mui/icons-material/Timer';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import GroupIcon from '@mui/icons-material/Group';
import StarIcon from '@mui/icons-material/Star';
import CheckIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import PlayIcon from '@mui/icons-material/PlayArrow';
import GameIcon from '@mui/icons-material/SportsEsports';
import RewardIcon from '@mui/icons-material/CardGiftcard';
import TrendingIcon from '@mui/icons-material/TrendingUp';;
import { useDispatch, useSelector } from 'react-redux';
import { motion, AnimatePresence } from 'framer-motion';
import confetti from 'canvas-confetti';
import { 
  createChallenge, 
  acceptChallenge, 
  updateChallengeProgress,
  completeChallenge 
} from '../../store/slices/gamificationSlice';
import { formatCurrency } from '../../utils/formatters';
import { useNotification } from '../../hooks/useNotification';
import CountdownTimer from '../common/CountdownTimer';
import UserAvatar from '../common/UserAvatar';
import AnimatedNumber from '../common/AnimatedNumber';

interface PaymentChallengeProps {
  userId: string;
}

interface Challenge {
  id: string;
  type: 'race' | 'goal' | 'streak' | 'social' | 'savings';
  title: string;
  description: string;
  challengerId: string;
  challengerName: string;
  challengerAvatar?: string;
  challengedId?: string;
  challengedName?: string;
  challengedAvatar?: string;
  amount: number;
  currency: string;
  targetAmount?: number;
  targetCount?: number;
  currentProgress: {
    [userId: string]: {
      amount: number;
      count: number;
      lastUpdate: string;
    };
  };
  wagerAmount?: number;
  reward?: {
    type: 'points' | 'badge' | 'cashback' | 'custom';
    value: number | string;
    description: string;
  };
  deadline: string;
  status: 'pending' | 'active' | 'completed' | 'expired';
  winner?: string;
  participants: string[];
  rules: string[];
  createdAt: string;
  acceptedAt?: string;
  completedAt?: string;
}

const challengeTypes = [
  {
    value: 'race',
    label: 'Payment Race',
    icon: <TrendingIcon />,
    description: 'First to reach the target wins',
  },
  {
    value: 'goal',
    label: 'Savings Goal',
    icon: <TrophyIcon />,
    description: 'Save a target amount together',
  },
  {
    value: 'streak',
    label: 'Streak Challenge',
    icon: <FireIcon />,
    description: 'Maintain daily payment streak',
  },
  {
    value: 'social',
    label: 'Social Challenge',
    icon: <GroupIcon />,
    description: 'Complete social payment tasks',
  },
  {
    value: 'savings',
    label: 'Savings Competition',
    icon: <MoneyIcon />,
    description: 'Save the most in time period',
  },
];

const PaymentChallenge: React.FC<PaymentChallengeProps> = ({ userId }) => {
  const dispatch = useDispatch();
  const { showNotification } = useNotification();
  
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [selectedChallenge, setSelectedChallenge] = useState<Challenge | null>(null);
  const [newChallenge, setNewChallenge] = useState({
    type: 'race',
    title: '',
    description: '',
    challengedId: '',
    amount: 100,
    currency: 'USD',
    targetAmount: 1000,
    targetCount: 10,
    wagerAmount: 0,
    deadline: 7,
    rules: [''],
  });

  const { 
    activeChallenges, 
    pendingChallenges, 
    completedChallenges,
    userStats 
  } = useSelector((state: any) => state.gamification);

  const handleCreateChallenge = async () => {
    try {
      const challenge = {
        ...newChallenge,
        challengerId: userId,
        deadline: new Date(Date.now() + newChallenge.deadline * 24 * 60 * 60 * 1000).toISOString(),
        rules: newChallenge.rules.filter(r => r.trim() !== ''),
      };

      await dispatch(createChallenge(challenge));
      
      showNotification('Challenge created successfully!', 'success');
      setCreateDialogOpen(false);
      resetNewChallenge();
      
      // Celebrate
      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 },
      });
    } catch (error) {
      showNotification('Failed to create challenge', 'error');
    }
  };

  const handleAcceptChallenge = async (challengeId: string) => {
    try {
      await dispatch(acceptChallenge({ challengeId, userId }));
      showNotification('Challenge accepted! Let the games begin!', 'success');
      
      // Animation
      confetti({
        particleCount: 50,
        angle: 60,
        spread: 55,
        origin: { x: 0 },
      });
      confetti({
        particleCount: 50,
        angle: 120,
        spread: 55,
        origin: { x: 1 },
      });
    } catch (error) {
      showNotification('Failed to accept challenge', 'error');
    }
  };

  const handleDeclineChallenge = async (challengeId: string) => {
    try {
      await dispatch(declineChallenge({ challengeId, userId }));
      showNotification('Challenge declined', 'info');
    } catch (error) {
      showNotification('Failed to decline challenge', 'error');
    }
  };

  const resetNewChallenge = () => {
    setNewChallenge({
      type: 'race',
      title: '',
      description: '',
      challengedId: '',
      amount: 100,
      currency: 'USD',
      targetAmount: 1000,
      targetCount: 10,
      wagerAmount: 0,
      deadline: 7,
      rules: [''],
    });
  };

  const calculateProgress = (challenge: Challenge, participantId: string): number => {
    const progress = challenge.currentProgress[participantId];
    if (!progress) return 0;

    switch (challenge.type) {
      case 'race':
      case 'savings':
        return (progress.amount / (challenge.targetAmount || 1)) * 100;
      case 'goal':
        const totalProgress = Object.values(challenge.currentProgress)
          .reduce((sum, p) => sum + p.amount, 0);
        return (totalProgress / (challenge.targetAmount || 1)) * 100;
      case 'streak':
        return (progress.count / (challenge.targetCount || 1)) * 100;
      case 'social':
        return (progress.count / (challenge.targetCount || 1)) * 100;
      default:
        return 0;
    }
  };

  const renderChallengeCard = (challenge: Challenge) => {
    const isChallenger = challenge.challengerId === userId;
    const isChallenged = challenge.challengedId === userId;
    const isParticipant = isChallenger || isChallenged;
    const myProgress = challenge.currentProgress[userId];
    const opponentId = isChallenger ? challenge.challengedId : challenge.challengerId;
    const opponentProgress = opponentId ? challenge.currentProgress[opponentId] : null;

    return (
      <motion.div
        key={challenge.id}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        whileHover={{ scale: 1.02 }}
      >
        <Card 
          sx={{ 
            mb: 2,
            background: challenge.status === 'active' 
              ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
              : undefined,
            color: challenge.status === 'active' ? 'white' : undefined,
          }}
        >
          <CardContent>
            <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
              <Box>
                <Typography variant="h6" gutterBottom>
                  {challenge.title}
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.8 }}>
                  {challenge.description}
                </Typography>
              </Box>
              <Box textAlign="right">
                <Chip
                  label={challenge.status}
                  size="small"
                  color={
                    challenge.status === 'active' ? 'success' :
                    challenge.status === 'pending' ? 'warning' :
                    challenge.status === 'completed' ? 'primary' : 'default'
                  }
                />
                {challenge.status === 'active' && challenge.deadline && (
                  <Box mt={1}>
                    <CountdownTimer 
                      targetDate={challenge.deadline}
                      onComplete={() => handleChallengeExpired(challenge.id)}
                    />
                  </Box>
                )}
              </Box>
            </Box>

            {challenge.status === 'pending' && isChallenged && (
              <Alert severity="info" sx={{ mb: 2 }}>
                You've been challenged! Accept to begin the competition.
              </Alert>
            )}

            {/* Participants */}
            <Grid container spacing={2} mb={2}>
              <Grid item xs={5}>
                <Box display="flex" alignItems="center" gap={1}>
                  <UserAvatar
                    user={{
                      id: challenge.challengerId,
                      name: challenge.challengerName,
                      avatar: challenge.challengerAvatar,
                    }}
                    size="small"
                  />
                  <Box>
                    <Typography variant="subtitle2">
                      {challenge.challengerName}
                      {isChallenger && ' (You)'}
                    </Typography>
                    {challenge.status === 'active' && myProgress && isChallenger && (
                      <Typography variant="caption">
                        {formatCurrency(myProgress.amount, challenge.currency)}
                      </Typography>
                    )}
                  </Box>
                </Box>
              </Grid>
              <Grid item xs={2} display="flex" alignItems="center" justifyContent="center">
                <Typography variant="h6">VS</Typography>
              </Grid>
              <Grid item xs={5}>
                {challenge.challengedId ? (
                  <Box display="flex" alignItems="center" gap={1} justifyContent="flex-end">
                    <Box textAlign="right">
                      <Typography variant="subtitle2">
                        {challenge.challengedName}
                        {isChallenged && ' (You)'}
                      </Typography>
                      {challenge.status === 'active' && opponentProgress && (
                        <Typography variant="caption">
                          {formatCurrency(opponentProgress.amount, challenge.currency)}
                        </Typography>
                      )}
                    </Box>
                    <UserAvatar
                      user={{
                        id: challenge.challengedId,
                        name: challenge.challengedName || 'Opponent',
                        avatar: challenge.challengedAvatar,
                      }}
                      size="small"
                    />
                  </Box>
                ) : (
                  <Box textAlign="right">
                    <Typography variant="subtitle2" color="text.secondary">
                      Waiting for opponent...
                    </Typography>
                  </Box>
                )}
              </Grid>
            </Grid>

            {/* Progress bars for active challenges */}
            {challenge.status === 'active' && (
              <Box mb={2}>
                <Box mb={1}>
                  <Box display="flex" justifyContent="space-between" mb={0.5}>
                    <Typography variant="caption">Your Progress</Typography>
                    <Typography variant="caption">
                      {myProgress ? `${Math.round(calculateProgress(challenge, userId))}%` : '0%'}
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={myProgress ? calculateProgress(challenge, userId) : 0}
                    sx={{ height: 8, borderRadius: 4 }}
                  />
                </Box>
                {opponentId && (
                  <Box>
                    <Box display="flex" justifyContent="space-between" mb={0.5}>
                      <Typography variant="caption">Opponent Progress</Typography>
                      <Typography variant="caption">
                        {opponentProgress ? `${Math.round(calculateProgress(challenge, opponentId))}%` : '0%'}
                      </Typography>
                    </Box>
                    <LinearProgress
                      variant="determinate"
                      value={opponentProgress ? calculateProgress(challenge, opponentId) : 0}
                      sx={{ height: 8, borderRadius: 4 }}
                      color="secondary"
                    />
                  </Box>
                )}
              </Box>
            )}

            {/* Challenge details */}
            <Box display="flex" gap={2} mb={2} flexWrap="wrap">
              <Chip
                icon={<MoneyIcon />}
                label={`Target: ${formatCurrency(challenge.targetAmount || 0, challenge.currency)}`}
                size="small"
                variant="outlined"
              />
              {challenge.wagerAmount && challenge.wagerAmount > 0 && (
                <Chip
                  icon={<RewardIcon />}
                  label={`Wager: ${formatCurrency(challenge.wagerAmount, challenge.currency)}`}
                  size="small"
                  variant="outlined"
                  color="warning"
                />
              )}
              {challenge.reward && (
                <Chip
                  icon={<StarIcon />}
                  label={challenge.reward.description}
                  size="small"
                  variant="outlined"
                  color="primary"
                />
              )}
            </Box>

            {/* Actions */}
            <Box display="flex" justifyContent="flex-end" gap={1}>
              {challenge.status === 'pending' && isChallenged && (
                <>
                  <Button
                    variant="contained"
                    color="success"
                    startIcon={<CheckIcon />}
                    onClick={() => handleAcceptChallenge(challenge.id)}
                  >
                    Accept
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    startIcon={<CancelIcon />}
                    onClick={() => handleDeclineChallenge(challenge.id)}
                  >
                    Decline
                  </Button>
                </>
              )}
              {challenge.status === 'active' && (
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => setSelectedChallenge(challenge)}
                >
                  View Details
                </Button>
              )}
              {challenge.status === 'completed' && challenge.winner === userId && (
                <Chip
                  icon={<TrophyIcon />}
                  label="Winner!"
                  color="primary"
                />
              )}
              <IconButton size="small" onClick={() => handleShareChallenge(challenge)}>
                <ShareIcon />
              </IconButton>
            </Box>
          </CardContent>
        </Card>
      </motion.div>
    );
  };

  const handleShareChallenge = async (challenge: Challenge) => {
    try {
      await navigator.share({
        title: challenge.title,
        text: `Join my payment challenge on Waqiti: ${challenge.description}`,
        url: `${window.location.origin}/challenge/${challenge.id}`,
      });
    } catch (error) {
      // Fallback to copy link
      navigator.clipboard.writeText(`${window.location.origin}/challenge/${challenge.id}`);
      showNotification('Challenge link copied!', 'success');
    }
  };

  const handleChallengeExpired = (challengeId: string) => {
    // Handle challenge expiration
    dispatch(expireChallenge(challengeId));
  };

  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Box>
          <Typography variant="h5" gutterBottom>
            Payment Challenges
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Compete with friends and earn rewards
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<GameIcon />}
          onClick={() => setCreateDialogOpen(true)}
        >
          Create Challenge
        </Button>
      </Box>

      {/* Stats */}
      <Grid container spacing={2} mb={3}>
        <Grid item xs={12} sm={3}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <TrophyIcon sx={{ fontSize: 40, color: 'primary.main', mb: 1 }} />
            <Typography variant="h4">
              <AnimatedNumber value={userStats?.challengesWon || 0} />
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Challenges Won
            </Typography>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <FireIcon sx={{ fontSize: 40, color: 'warning.main', mb: 1 }} />
            <Typography variant="h4">
              <AnimatedNumber value={userStats?.winStreak || 0} />
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Win Streak
            </Typography>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <MoneyIcon sx={{ fontSize: 40, color: 'success.main', mb: 1 }} />
            <Typography variant="h4">
              {formatCurrency(userStats?.totalWinnings || 0, 'USD')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Total Winnings
            </Typography>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Paper sx={{ p: 2, textAlign: 'center' }}>
            <StarIcon sx={{ fontSize: 40, color: 'secondary.main', mb: 1 }} />
            <Typography variant="h4">
              <AnimatedNumber value={userStats?.rewardsEarned || 0} />
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Rewards Earned
            </Typography>
          </Paper>
        </Grid>
      </Grid>

      {/* Active Challenges */}
      {activeChallenges.length > 0 && (
        <Box mb={4}>
          <Typography variant="h6" gutterBottom>
            Active Challenges
          </Typography>
          <AnimatePresence>
            {activeChallenges.map((challenge: Challenge) => renderChallengeCard(challenge))}
          </AnimatePresence>
        </Box>
      )}

      {/* Pending Challenges */}
      {pendingChallenges.length > 0 && (
        <Box mb={4}>
          <Typography variant="h6" gutterBottom>
            Pending Invitations
          </Typography>
          <AnimatePresence>
            {pendingChallenges.map((challenge: Challenge) => renderChallengeCard(challenge))}
          </AnimatePresence>
        </Box>
      )}

      {/* Create Challenge Dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create New Challenge</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <TextField
                select
                fullWidth
                label="Challenge Type"
                value={newChallenge.type}
                onChange={(e) => setNewChallenge({ ...newChallenge, type: e.target.value })}
              >
                {challengeTypes.map((type) => (
                  <MenuItem key={type.value} value={type.value}>
                    <Box display="flex" alignItems="center" gap={1}>
                      {type.icon}
                      <Box>
                        <Typography variant="body1">{type.label}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {type.description}
                        </Typography>
                      </Box>
                    </Box>
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Challenge Title"
                value={newChallenge.title}
                onChange={(e) => setNewChallenge({ ...newChallenge, title: e.target.value })}
                placeholder="e.g., Weekend Savings Sprint"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={2}
                label="Description"
                value={newChallenge.description}
                onChange={(e) => setNewChallenge({ ...newChallenge, description: e.target.value })}
                placeholder="Describe the challenge..."
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Challenge Friend"
                value={newChallenge.challengedId}
                onChange={(e) => setNewChallenge({ ...newChallenge, challengedId: e.target.value })}
                placeholder="Username or email"
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                type="number"
                label="Target Amount"
                value={newChallenge.targetAmount}
                onChange={(e) => setNewChallenge({ ...newChallenge, targetAmount: Number(e.target.value) })}
                InputProps={{
                  startAdornment: '$',
                }}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                type="number"
                label="Wager (Optional)"
                value={newChallenge.wagerAmount}
                onChange={(e) => setNewChallenge({ ...newChallenge, wagerAmount: Number(e.target.value) })}
                InputProps={{
                  startAdornment: '$',
                }}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                type="number"
                label="Duration (Days)"
                value={newChallenge.deadline}
                onChange={(e) => setNewChallenge({ ...newChallenge, deadline: Number(e.target.value) })}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreateChallenge}
            disabled={!newChallenge.title || !newChallenge.challengedId}
          >
            Create Challenge
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PaymentChallenge;