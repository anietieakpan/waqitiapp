import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Avatar,
  IconButton,
  Button,
  Card,
  CardContent,
  CardActions,
  TextField,
  Chip,
  Menu,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemIcon,
  Divider,
  Tabs,
  Tab,
  CircularProgress,
  Skeleton,
  useTheme,
  Tooltip,
} from '@mui/material';
import LikeIcon from '@mui/icons-material/Favorite';
import LikeOutlineIcon from '@mui/icons-material/FavoriteBorder';
import CommentIcon from '@mui/icons-material/Comment';
import ShareIcon from '@mui/icons-material/Share';
import MoreIcon from '@mui/icons-material/MoreVert';
import PublicIcon from '@mui/icons-material/Public';
import FriendsIcon from '@mui/icons-material/Group';
import PrivateIcon from '@mui/icons-material/Lock';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import SendIcon from '@mui/icons-material/Send';
import StarIcon from '@mui/icons-material/Star';
import VerifiedIcon from '@mui/icons-material/Verified';
import LocationIcon from '@mui/icons-material/LocationOn';
import FlagIcon from '@mui/icons-material/Flag';
import BlockIcon from '@mui/icons-material/Block';
import RefreshIcon from '@mui/icons-material/Refresh';
import CloseIcon from '@mui/icons-material/Close';
import ReceiptIcon from '@mui/icons-material/Receipt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import SwapIcon from '@mui/icons-material/SwapHoriz';;
import { useInView } from 'react-intersection-observer';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from 'react-query';
import { toast } from 'react-toastify';
import { useAppSelector } from '../../hooks/redux';
import EmojiReactions from './EmojiReactions';
import { socialService, SocialFeedItem } from '../../services/socialService';

interface SocialFeedEnhancedProps {
  filter?: 'all' | 'following' | 'trending';
  userId?: string;
}

const SocialFeedEnhanced: React.FC<SocialFeedEnhancedProps> = ({ filter = 'all', userId }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAppSelector((state) => state.auth);
  
  const [selectedTab, setSelectedTab] = useState(filter);
  const [selectedItem, setSelectedItem] = useState<SocialFeedItem | null>(null);
  const [commentDialogOpen, setCommentDialogOpen] = useState(false);
  const [shareDialogOpen, setShareDialogOpen] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  
  const { ref: infiniteScrollRef, inView } = useInView({
    threshold: 0,
  });

  // Infinite query for social feed
  const {
    data: feedData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isRefetching,
    refetch,
  } = useInfiniteQuery(
    ['social-feed', selectedTab, userId],
    ({ pageParam }) => {
      if (userId) {
        return socialService.getUserPublicTransactions(userId, pageParam);
      }
      return socialService.getSocialFeed(selectedTab, pageParam);
    },
    {
      getNextPageParam: (lastPage) => lastPage.hasMore ? lastPage.nextCursor : undefined,
      staleTime: 60000, // 1 minute
      cacheTime: 300000, // 5 minutes
    }
  );

  // Mutations for interactions
  const likeMutation = useMutation(
    (transactionId: string) => socialService.toggleLike(transactionId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['social-feed']);
        toast.success('Like updated!');
      },
      onError: () => {
        toast.error('Failed to update like');
      },
    }
  );

  const commentMutation = useMutation(
    ({ transactionId, content }: { transactionId: string; content: string }) =>
      socialService.addComment(transactionId, { content }),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['social-feed']);
        setNewComment('');
        setCommentDialogOpen(false);
        toast.success('Comment added!');
      },
      onError: () => {
        toast.error('Failed to add comment');
      },
    }
  );

  const shareMutation = useMutation(
    ({ transactionId, platform, message }: { transactionId: string; platform?: string; message?: string }) =>
      socialService.shareTransaction(transactionId, { platform: platform as any, message }),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['social-feed']);
        setShareDialogOpen(false);
        toast.success('Transaction shared!');
      },
      onError: () => {
        toast.error('Failed to share transaction');
      },
    }
  );

  // Get all feed items from pages
  const feedItems = feedData?.pages.flatMap(page => page.items) || [];

  // Load more items when scrolling
  useEffect(() => {
    if (inView && hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [inView, hasNextPage, isFetchingNextPage, fetchNextPage]);

  // Handle tab changes
  useEffect(() => {
    setSelectedTab(filter);
  }, [filter]);

  // Event handlers
  const handleLike = (transactionId: string) => {
    likeMutation.mutate(transactionId);
  };

  const handleComment = (item: SocialFeedItem) => {
    setSelectedItem(item);
    setCommentDialogOpen(true);
  };

  const handleShare = (item: SocialFeedItem) => {
    setSelectedItem(item);
    setShareDialogOpen(true);
  };

  const handleAddComment = () => {
    if (selectedItem && newComment.trim()) {
      commentMutation.mutate({
        transactionId: selectedItem.activityId,
        content: newComment.trim(),
      });
    }
  };

  const handleShareTransaction = (platform?: string, message?: string) => {
    if (selectedItem) {
      shareMutation.mutate({
        transactionId: selectedItem.activityId,
        platform,
        message,
      });
    }
  };

  const handleRefresh = () => {
    refetch();
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: string) => {
    setSelectedTab(newValue);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
    setSelectedItemId(null);
  };

  const getVisibilityIcon = (visibility: string) => {
    switch (visibility) {
      case 'PUBLIC':
        return <PublicIcon fontSize="small" />;
      case 'FRIENDS':
        return <FriendsIcon fontSize="small" />;
      case 'PRIVATE':
        return <PrivateIcon fontSize="small" />;
      default:
        return <FriendsIcon fontSize="small" />;
    }
  };

  const getActivityIcon = (activityType: string) => {
    switch (activityType) {
      case 'PAYMENT_SENT':
      case 'PAYMENT_RECEIVED':
        return <MoneyIcon />;
      case 'PAYMENT_REQUESTED':
        return <ReceiptIcon />;
      case 'BILL_SPLIT':
        return <SwapIcon />;
      case 'GROUP_PAYMENT':
        return <FriendsIcon />;
      case 'ACHIEVEMENT':
        return <StarIcon />;
      case 'MILESTONE':
        return <CheckCircleIcon />;
      default:
        return <MoneyIcon />;
    }
  };

  const renderFeedItem = (item: SocialFeedItem) => (
    <Card
      key={item.id}
      sx={{
        mb: 2,
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: theme.shadows[4],
        },
      }}
    >
      <CardContent sx={{ pb: 1 }}>
        {/* Header */}
        <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
          <Box display="flex" alignItems="center" gap={1.5}>
            <Avatar
              src={item.userProfile?.avatarUrl}
              sx={{ width: 40, height: 40 }}
              onClick={() => navigate(`/profile/${item.userId}`)}
              style={{ cursor: 'pointer' }}
            >
              {item.userProfile?.displayName?.charAt(0) || 'U'}
            </Avatar>
            <Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography
                  variant="subtitle2"
                  fontWeight="bold"
                  onClick={() => navigate(`/profile/${item.userId}`)}
                  sx={{ cursor: 'pointer', '&:hover': { color: 'primary.main' } }}
                >
                  {item.userProfile?.displayName}
                </Typography>
                {item.userProfile?.isVerified && (
                  <VerifiedIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                )}
                {getVisibilityIcon(item.visibility)}
              </Box>
              <Typography variant="body2" color="text.secondary">
                {socialService.formatRelativeTime(item.createdAt)}
              </Typography>
            </Box>
          </Box>
          
          <Box display="flex" alignItems="center" gap={1}>
            {getActivityIcon(item.activityType)}
            <IconButton
              size="small"
              onClick={(e) => {
                setMenuAnchor(e.currentTarget);
                setSelectedItemId(item.id);
              }}
            >
              <MoreIcon />
            </IconButton>
          </Box>
        </Box>

        {/* Content */}
        <Box mb={2}>
          <Typography variant="body1" gutterBottom>
            <strong>{item.userProfile?.displayName}</strong>{' '}
            {socialService.getActivityTypeDisplayName(item.activityType)}{' '}
            {item.participants && item.participants.length > 0 && (
              <>
                <strong>{item.participants[0]}</strong>
                {item.participants.length > 1 && ` and ${item.participants.length - 1} others`}
              </>
            )}
          </Typography>
          
          {item.description && (
            <Typography variant="body1" sx={{ mb: 1 }}>
              {item.description} {item.emoji}
            </Typography>
          )}
          
          {item.amount && (
            <Typography variant="h6" color="primary" fontWeight="bold">
              {socialService.formatAmount(item.amount, item.currency)}
            </Typography>
          )}
          
          {item.location && (
            <Box display="flex" alignItems="center" gap={0.5} mt={1}>
              <LocationIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
              <Typography variant="body2" color="text.secondary">
                {item.location}
              </Typography>
            </Box>
          )}
          
          {item.tags && item.tags.length > 0 && (
            <Box mt={1} display="flex" flexWrap="wrap" gap={0.5}>
              {item.tags.map((tag) => (
                <Chip
                  key={tag}
                  label={`#${tag}`}
                  size="small"
                  variant="outlined"
                  onClick={() => navigate(`/explore?tag=${tag}`)}
                  sx={{ cursor: 'pointer' }}
                />
              ))}
            </Box>
          )}
        </Box>

        {/* Emoji Reactions */}
        <Box mb={2}>
          <EmojiReactions
            transactionId={item.activityId}
            size="medium"
            showAddButton={true}
            maxDisplayedReactions={5}
          />
        </Box>
      </CardContent>

      {/* Actions */}
      <CardActions sx={{ pt: 0, justifyContent: 'space-between' }}>
        <Box display="flex" gap={1}>
          <Button
            startIcon={<LikeOutlineIcon />}
            size="small"
            onClick={() => handleLike(item.activityId)}
            disabled={likeMutation.isLoading}
            sx={{
              color: 'text.secondary',
              '&:hover': { color: 'error.main' },
            }}
          >
            {item.likesCount || 0}
          </Button>
          
          <Button
            startIcon={<CommentIcon />}
            size="small"
            onClick={() => handleComment(item)}
            sx={{
              color: 'text.secondary',
              '&:hover': { color: 'primary.main' },
            }}
          >
            {item.commentsCount || 0}
          </Button>
          
          <Button
            startIcon={<ShareIcon />}
            size="small"
            onClick={() => handleShare(item)}
            sx={{
              color: 'text.secondary',
              '&:hover': { color: 'success.main' },
            }}
          >
            {item.sharesCount || 0}
          </Button>
        </Box>
      </CardActions>
    </Card>
  );

  // Main render
  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', py: 2 }}>
      {/* Header with filters */}
      <Paper elevation={1} sx={{ mb: 3, borderRadius: 2 }}>
        <Box sx={{ p: 2 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h5" fontWeight="bold">
              Social Feed
            </Typography>
            <IconButton
              onClick={handleRefresh}
              disabled={isRefetching}
              sx={{ 
                animation: isRefetching ? 'spin 1s linear infinite' : 'none',
                '@keyframes spin': {
                  '0%': { transform: 'rotate(0deg)' },
                  '100%': { transform: 'rotate(360deg)' },
                },
              }}
            >
              <RefreshIcon />
            </IconButton>
          </Box>
          
          {!userId && (
            <Tabs
              value={selectedTab}
              onChange={handleTabChange}
              variant="fullWidth"
            >
              <Tab label="All" value="all" />
              <Tab label="Following" value="following" />
              <Tab label="Trending" value="trending" />
            </Tabs>
          )}
        </Box>
      </Paper>

      {/* Loading skeleton */}
      {isLoading && feedItems.length === 0 && (
        <Box>
          {[...Array(3)].map((_, index) => (
            <Card key={index} sx={{ mb: 2 }}>
              <CardContent>
                <Box display="flex" alignItems="center" gap={2} mb={2}>
                  <Skeleton variant="circular" width={40} height={40} />
                  <Box flex={1}>
                    <Skeleton variant="text" width="40%" />
                    <Skeleton variant="text" width="60%" />
                  </Box>
                </Box>
                <Skeleton variant="text" width="80%" />
                <Skeleton variant="text" width="60%" />
                <Skeleton variant="rectangular" height={60} sx={{ mt: 2 }} />
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      {/* Feed items */}
      {feedItems.length > 0 && (
        <Box>
          {feedItems.map(renderFeedItem)}
          
          {/* Load more trigger */}
          <div ref={infiniteScrollRef} />
          
          {isFetchingNextPage && (
            <Box display="flex" justifyContent="center" py={2}>
              <CircularProgress size={24} />
            </Box>
          )}
          
          {!hasNextPage && feedItems.length > 0 && (
            <Box textAlign="center" py={4}>
              <Typography variant="body2" color="text.secondary">
                You've reached the end of the feed
              </Typography>
            </Box>
          )}
        </Box>
      )}

      {/* Empty state */}
      {!isLoading && feedItems.length === 0 && (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No feed items yet
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Follow some friends to see their payment activity!
          </Typography>
        </Paper>
      )}

      {/* Comment Dialog */}
      <Dialog
        open={commentDialogOpen}
        onClose={() => setCommentDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">Add Comment</Typography>
            <IconButton onClick={() => setCommentDialogOpen(false)}>
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            rows={3}
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="Write a comment..."
            variant="outlined"
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCommentDialogOpen(false)}>
            Cancel
          </Button>
          <Button 
            onClick={handleAddComment}
            variant="contained"
            disabled={!newComment.trim() || commentMutation.isLoading}
            startIcon={commentMutation.isLoading ? <CircularProgress size={16} /> : <SendIcon />}
          >
            {commentMutation.isLoading ? 'Posting...' : 'Post Comment'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Share Dialog */}
      <Dialog
        open={shareDialogOpen}
        onClose={() => setShareDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Share Transaction</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            Share this transaction with others
          </Typography>
          <Box display="flex" flexDirection="column" gap={2} mt={2}>
            <Button
              variant="outlined"
              onClick={() => handleShareTransaction('INTERNAL')}
              disabled={shareMutation.isLoading}
            >
              Share Internally
            </Button>
            <Button
              variant="outlined"
              onClick={() => handleShareTransaction('TWITTER')}
              disabled={shareMutation.isLoading}
            >
              Share on Twitter
            </Button>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShareDialogOpen(false)}>
            Cancel
          </Button>
        </DialogActions>
      </Dialog>

      {/* Item Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <ReceiptIcon />
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
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <BlockIcon />
          </ListItemIcon>
          <ListItemText primary="Hide Posts from User" />
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default SocialFeedEnhanced;