import React, { useEffect, useState, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Avatar,
  Typography,
  IconButton,
  Chip,
  TextField,
  Button,
  Divider,
  Menu,
  MenuItem,
  Skeleton,
  Alert,
  ToggleButtonGroup,
  ToggleButton,
  InputAdornment,
  Collapse,
} from '@mui/material';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import FavoriteIcon from '@mui/icons-material/Favorite';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import ShareIcon from '@mui/icons-material/Share';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PublicIcon from '@mui/icons-material/Public';
import GroupIcon from '@mui/icons-material/Group';
import LockIcon from '@mui/icons-material/Lock';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import SendIcon from '@mui/icons-material/Send';
import EmojiEmotionsIcon from '@mui/icons-material/EmojiEmotions';
import LocationOnIcon from '@mui/icons-material/LocationOn';;
import { useDispatch, useSelector } from 'react-redux';
import { useInView } from 'react-intersection-observer';
import { formatDistanceToNow } from 'date-fns';

import { RootState, AppDispatch } from '../../store/store';
import {
  fetchPaymentFeed,
  fetchPaymentComments,
  addComment,
  addReaction,
  removeReaction,
  setFeedFilter,
} from '../../store/slices/socialPaymentSlice';
import { formatCurrency } from '../../utils/formatters';
import EmojiPicker from '../common/EmojiPicker';

interface PaymentFeedProps {
  showFilters?: boolean;
  userId?: string;
}

/**
 * Social Payment Feed Component - Venmo-style payment feed with social interactions
 */
const PaymentFeed: React.FC<PaymentFeedProps> = ({ showFilters = true, userId }) => {
  const dispatch = useDispatch<AppDispatch>();
  const { 
    feedItems, 
    feedLoading, 
    hasMore, 
    currentPage, 
    feedFilter,
    comments,
    userReactions,
    error 
  } = useSelector((state: RootState) => state.socialPayment);
  const currentUser = useSelector((state: RootState) => state.auth.user);

  const [expandedComments, setExpandedComments] = useState<Set<string>>(new Set());
  const [commentInputs, setCommentInputs] = useState<Record<string, string>>({});
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedPaymentId, setSelectedPaymentId] = useState<string | null>(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState<string | null>(null);

  const { ref: loadMoreRef, inView } = useInView({
    threshold: 0,
    rootMargin: '100px',
  });

  // Load initial feed
  useEffect(() => {
    dispatch(fetchPaymentFeed({ page: 1, filter: feedFilter }));
  }, [dispatch, feedFilter]);

  // Load more when scrolling
  useEffect(() => {
    if (inView && hasMore && !feedLoading) {
      dispatch(fetchPaymentFeed({ page: currentPage + 1, filter: feedFilter }));
    }
  }, [inView, hasMore, feedLoading, currentPage, feedFilter, dispatch]);

  const handleFilterChange = (event: React.MouseEvent<HTMLElement>, newFilter: string) => {
    if (newFilter !== null) {
      dispatch(setFeedFilter(newFilter as 'all' | 'friends' | 'following'));
    }
  };

  const handleReaction = async (paymentId: string) => {
    if (userReactions[paymentId]) {
      await dispatch(removeReaction(paymentId));
    } else {
      await dispatch(addReaction({ paymentId, reactionType: 'like' }));
    }
  };

  const toggleComments = (paymentId: string) => {
    const newExpanded = new Set(expandedComments);
    if (newExpanded.has(paymentId)) {
      newExpanded.delete(paymentId);
    } else {
      newExpanded.add(paymentId);
      // Load comments if not already loaded
      if (!comments[paymentId]) {
        dispatch(fetchPaymentComments(paymentId));
      }
    }
    setExpandedComments(newExpanded);
  };

  const handleAddComment = async (paymentId: string) => {
    const comment = commentInputs[paymentId]?.trim();
    if (comment) {
      await dispatch(addComment({ paymentId, comment }));
      setCommentInputs({ ...commentInputs, [paymentId]: '' });
    }
  };

  const handleShare = (paymentId: string) => {
    // Implement share functionality
    navigator.share?.({
      title: 'Check out this payment on Waqiti',
      url: `${window.location.origin}/payment/${paymentId}`,
    });
  };

  const handleMenuClick = (event: React.MouseEvent<HTMLElement>, paymentId: string) => {
    setAnchorEl(event.currentTarget);
    setSelectedPaymentId(paymentId);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setSelectedPaymentId(null);
  };

  const getVisibilityIcon = (visibility: string) => {
    switch (visibility) {
      case 'public':
        return <Public fontSize="small" />;
      case 'friends':
        return <Group fontSize="small" />;
      case 'private':
        return <Lock fontSize="small" />;
      default:
        return null;
    }
  };

  const renderPaymentCard = (payment: any) => {
    const isLiked = userReactions[payment.id];
    const paymentComments = comments[payment.id] || [];
    const showComments = expandedComments.has(payment.id);

    return (
      <Card key={payment.id} sx={{ mb: 2 }}>
        <CardContent>
          {/* Header */}
          <Box display="flex" alignItems="center" mb={2}>
            <Avatar src={payment.senderAvatar} sx={{ mr: 2 }} />
            <Box flex={1}>
              <Box display="flex" alignItems="center" gap={0.5}>
                <Typography variant="subtitle2">
                  {payment.senderName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  paid
                </Typography>
                <Typography variant="subtitle2">
                  {payment.recipientName}
                </Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="caption" color="text.secondary">
                  {formatDistanceToNow(new Date(payment.createdAt), { addSuffix: true })}
                </Typography>
                {getVisibilityIcon(payment.visibility)}
              </Box>
            </Box>
            <IconButton
              size="small"
              onClick={(e) => handleMenuClick(e, payment.id)}
            >
              <MoreVert />
            </IconButton>
          </Box>

          {/* Payment Details */}
          <Box mb={2}>
            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <Typography variant="h4" component="span">
                {payment.emoji || '💸'}
              </Typography>
              <Typography variant="body1">
                {payment.description}
              </Typography>
            </Box>
            
            {payment.tags && payment.tags.length > 0 && (
              <Box display="flex" gap={1} flexWrap="wrap" mb={1}>
                {payment.tags.map((tag: string, index: number) => (
                  <Chip
                    key={index}
                    label={`#${tag}`}
                    size="small"
                    variant="outlined"
                    color="primary"
                  />
                ))}
              </Box>
            )}

            {payment.location && (
              <Box display="flex" alignItems="center" gap={0.5}>
                <LocationOn fontSize="small" color="action" />
                <Typography variant="caption" color="text.secondary">
                  {payment.location}
                </Typography>
              </Box>
            )}
          </Box>

          {/* Interaction Bar */}
          <Divider sx={{ my: 1 }} />
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box display="flex" gap={2}>
              <Button
                size="small"
                startIcon={isLiked ? <Favorite color="error" /> : <FavoriteBorder />}
                onClick={() => handleReaction(payment.id)}
                sx={{ color: isLiked ? 'error.main' : 'text.secondary' }}
              >
                {payment.reactionCount || 0}
              </Button>
              <Button
                size="small"
                startIcon={<ChatBubbleOutline />}
                onClick={() => toggleComments(payment.id)}
                color="inherit"
              >
                {payment.commentCount || 0}
              </Button>
              <IconButton size="small" onClick={() => handleShare(payment.id)}>
                <Share />
              </IconButton>
            </Box>
          </Box>

          {/* Comments Section */}
          <Collapse in={showComments}>
            <Divider sx={{ my: 2 }} />
            <Box>
              {paymentComments.map((comment: any) => (
                <Box key={comment.id} display="flex" gap={1} mb={2}>
                  <Avatar src={comment.userAvatar} sx={{ width: 32, height: 32 }} />
                  <Box flex={1}>
                    <Typography variant="subtitle2">
                      {comment.userName}
                    </Typography>
                    <Typography variant="body2">
                      {comment.text}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatDistanceToNow(new Date(comment.createdAt), { addSuffix: true })}
                    </Typography>
                  </Box>
                </Box>
              ))}

              {/* Add Comment */}
              <Box display="flex" gap={1} alignItems="center">
                <Avatar src={currentUser?.avatar} sx={{ width: 32, height: 32 }} />
                <TextField
                  fullWidth
                  size="small"
                  placeholder="Add a comment..."
                  value={commentInputs[payment.id] || ''}
                  onChange={(e) => setCommentInputs({
                    ...commentInputs,
                    [payment.id]: e.target.value
                  })}
                  onKeyPress={(e) => {
                    if (e.key === 'Enter') {
                      handleAddComment(payment.id);
                    }
                  }}
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          size="small"
                          onClick={() => setShowEmojiPicker(payment.id)}
                        >
                          <EmojiEmotions />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => handleAddComment(payment.id)}
                          disabled={!commentInputs[payment.id]?.trim()}
                        >
                          <Send />
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                />
              </Box>
            </Box>
          </Collapse>
        </CardContent>
      </Card>
    );
  };

  return (
    <Box>
      {/* Filters */}
      {showFilters && (
        <Box mb={3}>
          <ToggleButtonGroup
            value={feedFilter}
            exclusive
            onChange={handleFilterChange}
            aria-label="feed filter"
            fullWidth
          >
            <ToggleButton value="all" aria-label="all payments">
              All
            </ToggleButton>
            <ToggleButton value="friends" aria-label="friends only">
              Friends
            </ToggleButton>
            <ToggleButton value="following" aria-label="following only">
              Following
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>
      )}

      {/* Error State */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* Feed Items */}
      {feedItems.map(renderPaymentCard)}

      {/* Loading State */}
      {feedLoading && (
        <>
          {[1, 2, 3].map((i) => (
            <Card key={`skeleton-${i}`} sx={{ mb: 2 }}>
              <CardContent>
                <Box display="flex" alignItems="center" mb={2}>
                  <Skeleton variant="circular" width={40} height={40} sx={{ mr: 2 }} />
                  <Box flex={1}>
                    <Skeleton variant="text" width="60%" />
                    <Skeleton variant="text" width="40%" />
                  </Box>
                </Box>
                <Skeleton variant="text" width="80%" />
                <Skeleton variant="rectangular" height={40} sx={{ mt: 2 }} />
              </CardContent>
            </Card>
          ))}
        </>
      )}

      {/* Load More Trigger */}
      {hasMore && !feedLoading && (
        <Box ref={loadMoreRef} py={2} textAlign="center">
          <Typography variant="body2" color="text.secondary">
            Loading more...
          </Typography>
        </Box>
      )}

      {/* Empty State */}
      {!feedLoading && feedItems.length === 0 && (
        <Box textAlign="center" py={4}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No payments to show
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Start following people to see their payments here
          </Typography>
        </Box>
      )}

      {/* Options Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>View Details</MenuItem>
        <MenuItem onClick={handleMenuClose}>Copy Link</MenuItem>
        {currentUser?.id === selectedPaymentId && (
          <MenuItem onClick={handleMenuClose}>Edit Privacy</MenuItem>
        )}
        <MenuItem onClick={handleMenuClose}>Report</MenuItem>
      </Menu>

      {/* Emoji Picker */}
      {showEmojiPicker && (
        <EmojiPicker
          open={Boolean(showEmojiPicker)}
          onClose={() => setShowEmojiPicker(null)}
          onSelect={(emoji) => {
            if (showEmojiPicker) {
              setCommentInputs({
                ...commentInputs,
                [showEmojiPicker]: (commentInputs[showEmojiPicker] || '') + emoji
              });
            }
            setShowEmojiPicker(null);
          }}
        />
      )}
    </Box>
  );
};

export default PaymentFeed;