import React, { useState, useEffect } from 'react';
import {
  Box,
  IconButton,
  Popover,
  Grid,
  Typography,
  Chip,
  Tooltip,
  Button,
  useTheme,
  alpha,
  Fade,
  CircularProgress,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';;
import { useMutation, useQuery, useQueryClient } from 'react-query';
import { toast } from 'react-toastify';
import { socialService, EmojiReaction, ReactionResponse } from '../../services/socialService';

interface EmojiReactionsProps {
  transactionId: string;
  initialReactions?: EmojiReaction[];
  size?: 'small' | 'medium' | 'large';
  showAddButton?: boolean;
  maxDisplayedReactions?: number;
  onReactionChange?: (reactions: EmojiReaction[]) => void;
}

export const EmojiReactions: React.FC<EmojiReactionsProps> = ({
  transactionId,
  initialReactions = [],
  size = 'medium',
  showAddButton = true,
  maxDisplayedReactions = 6,
  onReactionChange,
}) => {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [showAllReactions, setShowAllReactions] = useState(false);

  // Query for transaction reactions
  const { data: reactionData, isLoading } = useQuery(
    ['transaction-reactions', transactionId],
    () => socialService.getTransactionReactions(transactionId),
    {
      initialData: initialReactions.length > 0 ? { reactions: initialReactions, totalReactions: initialReactions.reduce((sum, r) => sum + r.count, 0) } : undefined,
      staleTime: 30000, // 30 seconds
      onSuccess: (data) => {
        onReactionChange?.(data.reactions);
      },
    }
  );

  // Mutation for adding reactions
  const addReactionMutation = useMutation(
    (emoji: string) => socialService.reactToTransaction(transactionId, { emoji }),
    {
      onSuccess: (data) => {
        queryClient.setQueryData(['transaction-reactions', transactionId], data);
        onReactionChange?.(data.reactions);
        setAnchorEl(null);
        toast.success('Reaction added!');
      },
      onError: () => {
        toast.error('Failed to add reaction');
      },
    }
  );

  // Mutation for removing reactions
  const removeReactionMutation = useMutation(
    (emoji: string) => socialService.removeReaction(transactionId, emoji),
    {
      onSuccess: (data) => {
        queryClient.setQueryData(['transaction-reactions', transactionId], data);
        onReactionChange?.(data.reactions);
        toast.success('Reaction removed');
      },
      onError: () => {
        toast.error('Failed to remove reaction');
      },
    }
  );

  const reactions = reactionData?.reactions || [];
  const totalReactions = reactionData?.totalReactions || 0;

  const popularEmojis = socialService.getPopularPaymentEmojis();

  const handleEmojiClick = async (emoji: string) => {
    const existingReaction = reactions.find(r => r.emoji === emoji);
    
    if (existingReaction?.isSelectedByCurrentUser) {
      // Remove reaction
      removeReactionMutation.mutate(emoji);
    } else {
      // Add reaction
      addReactionMutation.mutate(emoji);
    }
  };

  const handleAddReactionClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClosePopover = () => {
    setAnchorEl(null);
  };

  const displayedReactions = showAllReactions 
    ? reactions 
    : reactions.slice(0, maxDisplayedReactions);

  const remainingCount = reactions.length - maxDisplayedReactions;

  const getEmojiSize = () => {
    switch (size) {
      case 'small': return { fontSize: '16px', padding: '4px 8px' };
      case 'large': return { fontSize: '24px', padding: '8px 12px' };
      default: return { fontSize: '20px', padding: '6px 10px' };
    }
  };

  const getAddButtonSize = () => {
    switch (size) {
      case 'small': return 'small' as const;
      case 'large': return 'large' as const;
      default: return 'medium' as const;
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" alignItems="center" gap={1}>
        <CircularProgress size={16} />
        <Typography variant="body2" color="text.secondary">
          Loading reactions...
        </Typography>
      </Box>
    );
  }

  return (
    <Box display="flex" alignItems="center" flexWrap="wrap" gap={1}>
      {displayedReactions.map((reaction) => (
        <Tooltip
          key={reaction.emoji}
          title={
            <Box>
              <Typography variant="body2" fontWeight="bold">
                {reaction.emoji} {reaction.count}
              </Typography>
              {reaction.users.slice(0, 5).map((user, index) => (
                <Typography key={user.id} variant="body2">
                  {index === 0 ? '' : ', '}{user.displayName}
                </Typography>
              ))}
              {reaction.users.length > 5 && (
                <Typography variant="body2" color="text.secondary">
                  and {reaction.users.length - 5} others
                </Typography>
              )}
            </Box>
          }
          arrow
          placement="top"
        >
          <Chip
            label={
              <Box display="flex" alignItems="center" gap={0.5}>
                <span style={getEmojiSize()}>{reaction.emoji}</span>
                <Typography 
                  variant="body2" 
                  color={reaction.isSelectedByCurrentUser ? 'primary' : 'text.secondary'}
                  fontWeight={reaction.isSelectedByCurrentUser ? 'bold' : 'normal'}
                >
                  {reaction.count}
                </Typography>
              </Box>
            }
            onClick={() => handleEmojiClick(reaction.emoji)}
            variant={reaction.isSelectedByCurrentUser ? 'filled' : 'outlined'}
            color={reaction.isSelectedByCurrentUser ? 'primary' : 'default'}
            size={size}
            sx={{
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              bgcolor: reaction.isSelectedByCurrentUser 
                ? alpha(theme.palette.primary.main, 0.1)
                : 'transparent',
              borderColor: reaction.isSelectedByCurrentUser 
                ? theme.palette.primary.main
                : alpha(theme.palette.text.secondary, 0.3),
              '&:hover': {
                bgcolor: reaction.isSelectedByCurrentUser
                  ? alpha(theme.palette.primary.main, 0.2)
                  : alpha(theme.palette.action.hover, 0.1),
                transform: 'scale(1.05)',
              },
            }}
          />
        </Tooltip>
      ))}

      {remainingCount > 0 && !showAllReactions && (
        <Chip
          label={`+${remainingCount}`}
          onClick={() => setShowAllReactions(true)}
          variant="outlined"
          size={size}
          sx={{
            cursor: 'pointer',
            opacity: 0.7,
            '&:hover': {
              opacity: 1,
            },
          }}
        />
      )}

      {showAddButton && (
        <Tooltip title="Add reaction" arrow>
          <IconButton
            onClick={handleAddReactionClick}
            size={getAddButtonSize()}
            sx={{
              color: 'text.secondary',
              '&:hover': {
                color: 'primary.main',
                bgcolor: alpha(theme.palette.primary.main, 0.1),
              },
            }}
          >
            <AddIcon />
          </IconButton>
        </Tooltip>
      )}

      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={handleClosePopover}
        anchorOrigin={{
          vertical: 'top',
          horizontal: 'center',
        }}
        transformOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        PaperProps={{
          sx: {
            p: 2,
            minWidth: 280,
            maxWidth: 320,
          },
        }}
      >
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6" fontWeight="bold">
            Add Reaction
          </Typography>
          <IconButton onClick={handleClosePopover} size="small">
            <CloseIcon />
          </IconButton>
        </Box>

        <Typography variant="body2" color="text.secondary" mb={2}>
          Popular reactions for payments
        </Typography>

        <Grid container spacing={1}>
          {popularEmojis.map((emoji) => {
            const isSelected = reactions.some(r => r.emoji === emoji && r.isSelectedByCurrentUser);
            return (
              <Grid item key={emoji}>
                <Button
                  onClick={() => handleEmojiClick(emoji)}
                  variant={isSelected ? 'contained' : 'outlined'}
                  size="large"
                  sx={{
                    minWidth: 50,
                    height: 50,
                    fontSize: '24px',
                    p: 1,
                    transition: 'all 0.2s ease',
                    '&:hover': {
                      transform: 'scale(1.1)',
                    },
                  }}
                  disabled={addReactionMutation.isLoading || removeReactionMutation.isLoading}
                >
                  {emoji}
                </Button>
              </Grid>
            );
          })}
        </Grid>

        {totalReactions > 0 && (
          <Box mt={2} pt={2} borderTop={1} borderColor="divider">
            <Typography variant="body2" color="text.secondary" textAlign="center">
              Total reactions: {totalReactions}
            </Typography>
          </Box>
        )}
      </Popover>
    </Box>
  );
};

export default EmojiReactions;