import React, { memo, useCallback, useMemo } from 'react';
import {
  Card,
  CardContent,
  CardActions,
  Avatar,
  Typography,
  IconButton,
  Box,
  Chip,
  Badge,
  Tooltip,
} from '@mui/material';
import LikeIcon from '@mui/icons-material/Favorite';
import LikeOutlineIcon from '@mui/icons-material/FavoriteBorder';
import CommentIcon from '@mui/icons-material/Comment';
import ShareIcon from '@mui/icons-material/Share';
import MoreIcon from '@mui/icons-material/MoreVert';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import VerifiedIcon from '@mui/icons-material/Verified';;
import { formatDistanceToNow, parseISO } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';
import { SocialFeedItem as SocialFeedItemType } from '../../services/socialService';

interface SocialFeedItemProps {
  item: SocialFeedItemType;
  currentUserId: string;
  onLike: (itemId: string) => void;
  onComment: (itemId: string) => void;
  onShare: (itemId: string) => void;
  onMenuOpen: (itemId: string, anchorEl: HTMLElement) => void;
}

const SocialFeedItem = memo<SocialFeedItemProps>(({
  item,
  currentUserId,
  onLike,
  onComment,
  onShare,
  onMenuOpen
}) => {
  const isLiked = useMemo(() => 
    item.likes?.includes(currentUserId) || false, 
    [item.likes, currentUserId]
  );

  const relativeTime = useMemo(() => 
    formatDistanceToNow(parseISO(item.createdAt)), 
    [item.createdAt]
  );

  const handleLike = useCallback(() => {
    onLike(item.id);
  }, [onLike, item.id]);

  const handleComment = useCallback(() => {
    onComment(item.id);
  }, [onComment, item.id]);

  const handleShare = useCallback(() => {
    onShare(item.id);
  }, [onShare, item.id]);

  const handleMenuClick = useCallback((event: React.MouseEvent<HTMLElement>) => {
    onMenuOpen(item.id, event.currentTarget);
  }, [onMenuOpen, item.id]);

  const getActivityIcon = () => {
    switch (item.activityType) {
      case 'PAYMENT_SENT':
      case 'PAYMENT_RECEIVED':
        return <MoneyIcon color="primary" />;
      case 'PAYMENT_REQUEST':
        return <SwapIcon color="secondary" />;
      default:
        return <SwapIcon />;
    }
  };

  const formatAmount = (amount: number, currency: string) => {
    return formatCurrency(amount, currency);
  };

  return (
    <Card sx={{ mb: 2, transition: 'all 0.2s ease-in-out' }}>
      <CardContent>
        {/* User Info */}
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Avatar
            src={item.user?.profilePicture}
            alt={item.user?.displayName}
            sx={{ mr: 2 }}
          >
            {item.user?.displayName?.charAt(0)}
          </Avatar>
          
          <Box sx={{ flexGrow: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="subtitle1" fontWeight="bold">
                {item.user?.displayName}
              </Typography>
              {item.user?.isVerified && (
                <VerifiedIcon fontSize="small" color="primary" />
              )}
            </Box>
            <Typography variant="caption" color="text.secondary">
              {relativeTime} ago
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {getActivityIcon()}
            <IconButton size="small" onClick={handleMenuClick}>
              <MoreIcon />
            </IconButton>
          </Box>
        </Box>

        {/* Content */}
        <Box sx={{ mb: 2 }}>
          <Typography variant="h6" gutterBottom>
            {item.title}
          </Typography>
          
          {item.description && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {item.description}
            </Typography>
          )}

          {/* Amount Display */}
          {item.amount && (
            <Box sx={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: 1,
              p: 2,
              bgcolor: 'background.default',
              borderRadius: 1,
              mb: 2
            }}>
              <Typography variant="h5" color="primary" fontWeight="bold">
                {formatAmount(item.amount, item.currency || 'USD')}
              </Typography>
              {item.emoji && (
                <Typography variant="h5">
                  {item.emoji}
                </Typography>
              )}
            </Box>
          )}

          {/* Tags */}
          {item.tags && item.tags.length > 0 && (
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
              {item.tags.map((tag, index) => (
                <Chip
                  key={index}
                  label={tag}
                  size="small"
                  variant="outlined"
                />
              ))}
            </Box>
          )}

          {/* Participants */}
          {item.participants && item.participants.length > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
              <Typography variant="caption" color="text.secondary">
                With:
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.5 }}>
                {item.participants.slice(0, 3).map((participantId) => (
                  <Avatar
                    key={participantId}
                    sx={{ width: 24, height: 24 }}
                    // Would need to fetch participant data
                  />
                ))}
                {item.participants.length > 3 && (
                  <Chip
                    label={`+${item.participants.length - 3}`}
                    size="small"
                    sx={{ height: 24 }}
                  />
                )}
              </Box>
            </Box>
          )}
        </Box>
      </CardContent>

      {/* Actions */}
      <CardActions sx={{ px: 2, pb: 2 }}>
        <IconButton
          onClick={handleLike}
          color={isLiked ? "error" : "default"}
          size="small"
        >
          <Badge badgeContent={item.likesCount || 0} color="primary">
            {isLiked ? <LikeIcon /> : <LikeOutlineIcon />}
          </Badge>
        </IconButton>

        <IconButton onClick={handleComment} size="small">
          <Badge badgeContent={item.commentsCount || 0} color="primary">
            <CommentIcon />
          </Badge>
        </IconButton>

        <IconButton onClick={handleShare} size="small">
          <ShareIcon />
        </IconButton>

        {/* Visibility Indicator */}
        <Box sx={{ flexGrow: 1 }} />
        <Chip
          label={item.visibility || 'PUBLIC'}
          size="small"
          variant="outlined"
          sx={{ fontSize: '0.75rem' }}
        />
      </CardActions>
    </Card>
  );
});

SocialFeedItem.displayName = 'SocialFeedItem';

export default SocialFeedItem;