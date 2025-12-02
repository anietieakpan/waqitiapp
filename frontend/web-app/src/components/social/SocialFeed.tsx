import React, { useState, useEffect, useCallback } from 'react';
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
  ListItemSecondaryAction,
  Divider,
  Badge,
  Tabs,
  Tab,
  InputAdornment,
  CircularProgress,
  Alert,
  Skeleton,
  Collapse,
  useTheme,
  alpha,
  Fade,
  Grow,
  Tooltip,
  Grid,
  ToggleButton,
  ToggleButtonGroup,
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
import EmojiIcon from '@mui/icons-material/EmojiEmotions';
import SendIcon from '@mui/icons-material/Send';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import OfferIcon from '@mui/icons-material/LocalOffer';
import StarIcon from '@mui/icons-material/Star';
import VerifiedIcon from '@mui/icons-material/Verified';
import CameraIcon from '@mui/icons-material/PhotoCamera';
import GifIcon from '@mui/icons-material/Gif';
import LocationIcon from '@mui/icons-material/LocationOn';
import FlagIcon from '@mui/icons-material/Flag';
import BlockIcon from '@mui/icons-material/Block';
import FollowIcon from '@mui/icons-material/PersonAdd';
import FilterIcon from '@mui/icons-material/FilterList';
import RefreshIcon from '@mui/icons-material/Refresh';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import BackIcon from '@mui/icons-material/ArrowBack';
import ForwardIcon from '@mui/icons-material/ArrowForward';
import ReceiptIcon from '@mui/icons-material/Receipt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';;
import { format, formatDistanceToNow, isToday, isYesterday, parseISO } from 'date-fns';
import { useInView } from 'react-intersection-observer';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from 'react-query';
import { toast } from 'react-toastify';
import { useAppDispatch, useAppSelector } from '../../hooks/redux';
import { formatCurrency } from '../../utils/formatters';
import EmojiPicker from '../common/EmojiPicker';
import EmojiReactions from './EmojiReactions';
import { socialService, SocialFeedItem, SocialFeedResponse } from '../../services/socialService';

interface SocialFeedProps {
  filter?: 'all' | 'following' | 'trending';
  userId?: string;
}

const SocialFeed: React.FC<SocialFeedProps> = ({ filter = 'all', userId }) => {
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

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadFeedItems();
    setRefreshing(false);
  };

  const handleLike = (itemId: string) => {
    setFeedItems(prev =>
      prev.map(item =>
        item.id === itemId
          ? {
              ...item,
              engagement: {
                ...item.engagement,
                liked: !item.engagement.liked,
                likes: item.engagement.liked
                  ? item.engagement.likes - 1
                  : item.engagement.likes + 1,
              },
            }
          : item
      )
    );
  };

  const handleComment = (item: FeedItem) => {
    setSelectedItem(item);
    setCommentDialogOpen(true);
  };

  const handleShare = (item: FeedItem) => {
    setSelectedItem(item);
    setShareDialogOpen(true);
  };

  const handleAddComment = () => {
    if (!newComment.trim() || !selectedItem) return;
    
    const newCommentObj = {
      id: Date.now().toString(),
      userId: user?.id || '',
      userName: `${user?.firstName} ${user?.lastName}`,
      userAvatar: user?.avatar,
      text: newComment,
      timestamp: new Date().toISOString(),
      likes: 0,
      liked: false,
    };
    
    setFeedItems(prev =>
      prev.map(item =>
        item.id === selectedItem.id
          ? {
              ...item,
              comments: [...(item.comments || []), newCommentObj],
              engagement: {
                ...item.engagement,
                comments: item.engagement.comments + 1,
              },
            }
          : item
      )
    );
    
    setNewComment('');
    setCommentDialogOpen(false);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, itemId: string) => {
    setMenuAnchor(event.currentTarget);
    setSelectedItemId(itemId);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
    setSelectedItemId(null);
  };

  const getPrivacyIcon = (privacy: FeedItem['privacy']) => {
    switch (privacy) {
      case 'public':
        return <PublicIcon fontSize="small" />;
      case 'friends':
        return <FriendsIcon fontSize="small" />;
      case 'private':
        return <PrivateIcon fontSize="small" />;
    }
  };

  const getTimeDisplay = (timestamp: string) => {
    const date = parseISO(timestamp);
    if (isToday(date)) {
      return formatDistanceToNow(date, { addSuffix: true });
    } else if (isYesterday(date)) {
      return `Yesterday at ${format(date, 'h:mm a')}`;
    } else {
      return format(date, 'MMM d, yyyy');
    }
  };

  const renderFeedItem = (item: FeedItem) => {
    switch (item.type) {
      case 'payment':
        return renderPaymentItem(item);
      case 'split':
        return renderSplitItem(item);
      case 'achievement':
        return renderAchievementItem(item);
      case 'milestone':
        return renderMilestoneItem(item);
      default:
        return null;
    }
  };

  const renderPaymentItem = (item: FeedItem) => (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Badge
              overlap="circular"
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              badgeContent={
                item.userVerified && (
                  <VerifiedIcon sx={{ fontSize: 16, color: theme.palette.primary.main }} />
                )
              }
            >
              <Avatar src={item.userAvatar} sx={{ cursor: 'pointer' }}>
                {item.userName[0]}
              </Avatar>
            </Badge>
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, cursor: 'pointer' }}>
                  {item.userName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  paid
                </Typography>
                <Typography variant="subtitle2" sx={{ fontWeight: 600, cursor: 'pointer' }}>
                  {item.content.recipientName}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {getTimeDisplay(item.timestamp)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  •
                </Typography>
                {getPrivacyIcon(item.privacy)}
              </Box>
            </Box>
          </Box>
          
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 700, color: theme.palette.success.main }}>
              +{formatCurrency(item.content.amount!, item.content.currency)}
            </Typography>
            <IconButton size="small" onClick={(e) => handleMenuOpen(e, item.id)}>
              <MoreIcon />
            </IconButton>
          </Box>
        </Box>
        
        {item.content.description && (
          <Typography variant="body1" sx={{ mb: 1 }}>
            {item.content.description}
          </Typography>
        )}
        
        {item.content.tags && item.content.tags.length > 0 && (
          <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
            {item.content.tags.map((tag) => (
              <Chip
                key={tag}
                label={`#${tag}`}
                size="small"
                variant="outlined"
                onClick={() => {/* Handle tag click */}}
              />
            ))}
          </Box>
        )}
        
        {item.content.location && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 2 }}>
            <LocationIcon fontSize="small" color="action" />
            <Typography variant="caption" color="text.secondary">
              {item.content.location}
            </Typography>
          </Box>
        )}
      </CardContent>
      
      <Divider />
      
      <CardActions sx={{ px: 2, py: 1 }}>
        <Button
          size="small"
          startIcon={item.engagement.liked ? <LikeIcon /> : <LikeOutlineIcon />}
          onClick={() => handleLike(item.id)}
          sx={{
            color: item.engagement.liked ? theme.palette.error.main : 'text.secondary',
          }}
        >
          {item.engagement.likes}
        </Button>
        <Button
          size="small"
          startIcon={<CommentIcon />}
          onClick={() => handleComment(item)}
          color="inherit"
        >
          {item.engagement.comments}
        </Button>
        <Button
          size="small"
          startIcon={<ShareIcon />}
          onClick={() => handleShare(item)}
          color="inherit"
        >
          {item.engagement.shares > 0 && item.engagement.shares}
        </Button>
      </CardActions>
      
      {item.comments && item.comments.length > 0 && (
        <>
          <Divider />
          <Box sx={{ p: 2 }}>
            {item.comments.slice(-2).map((comment) => (
              <Box key={comment.id} sx={{ display: 'flex', gap: 1, mb: 1 }}>
                <Avatar src={comment.userAvatar} sx={{ width: 32, height: 32 }}>
                  {comment.userName[0]}
                </Avatar>
                <Box sx={{ flex: 1 }}>
                  <Box sx={{ bgcolor: 'background.default', p: 1, borderRadius: 1 }}>
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>
                      {comment.userName}
                    </Typography>
                    <Typography variant="body2">{comment.text}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 2, mt: 0.5 }}>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ cursor: 'pointer' }}
                    >
                      Like {comment.likes > 0 && `(${comment.likes})`}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatDistanceToNow(parseISO(comment.timestamp), { addSuffix: true })}
                    </Typography>
                  </Box>
                </Box>
              </Box>
            ))}
            {item.comments.length > 2 && (
              <Button size="small" onClick={() => handleComment(item)}>
                View all {item.comments.length} comments
              </Button>
            )}
          </Box>
        </>
      )}
    </Card>
  );

  const renderSplitItem = (item: FeedItem) => (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar src={item.userAvatar} sx={{ cursor: 'pointer' }}>
              {item.userName[0]}
            </Avatar>
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                {item.userName} split a bill
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {getTimeDisplay(item.timestamp)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  •
                </Typography>
                {getPrivacyIcon(item.privacy)}
              </Box>
            </Box>
          </Box>
          
          <IconButton size="small" onClick={(e) => handleMenuOpen(e, item.id)}>
            <MoreIcon />
          </IconButton>
        </Box>
        
        <Paper sx={{ p: 2, bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            {item.content.description}
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
            {formatCurrency(item.content.amount!, item.content.currency)}
          </Typography>
          
          <List sx={{ py: 0 }}>
            {item.content.participants?.map((participant) => (
              <ListItem key={participant.id} sx={{ px: 0 }}>
                <ListItemAvatar>
                  <Avatar src={participant.avatar} sx={{ width: 32, height: 32 }}>
                    {participant.name[0]}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={participant.name}
                  secondary={formatCurrency(participant.amount!, item.content.currency)}
                />
                <ListItemSecondaryAction>
                  {participant.paid ? (
                    <Chip
                      icon={<CheckCircleIcon />}
                      label="Paid"
                      color="success"
                      size="small"
                    />
                  ) : (
                    <Chip
                      label="Pending"
                      color="warning"
                      size="small"
                    />
                  )}
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </Paper>
        
        {item.content.location && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 2 }}>
            <LocationIcon fontSize="small" color="action" />
            <Typography variant="caption" color="text.secondary">
              {item.content.location}
            </Typography>
          </Box>
        )}
      </CardContent>
      
      <Divider />
      
      <CardActions sx={{ px: 2, py: 1 }}>
        <Button
          size="small"
          startIcon={item.engagement.liked ? <LikeIcon /> : <LikeOutlineIcon />}
          onClick={() => handleLike(item.id)}
          sx={{
            color: item.engagement.liked ? theme.palette.error.main : 'text.secondary',
          }}
        >
          {item.engagement.likes}
        </Button>
        <Button
          size="small"
          startIcon={<CommentIcon />}
          onClick={() => handleComment(item)}
          color="inherit"
        >
          {item.engagement.comments}
        </Button>
        <Button
          size="small"
          startIcon={<ShareIcon />}
          onClick={() => handleShare(item)}
          color="inherit"
        >
          {item.engagement.shares > 0 && item.engagement.shares}
        </Button>
      </CardActions>
    </Card>
  );

  const renderAchievementItem = (item: FeedItem) => (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar src={item.userAvatar} sx={{ cursor: 'pointer' }}>
              {item.userName[0]}
            </Avatar>
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                {item.userName} earned an achievement
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {getTimeDisplay(item.timestamp)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  •
                </Typography>
                {getPrivacyIcon(item.privacy)}
              </Box>
            </Box>
          </Box>
          
          <IconButton size="small" onClick={(e) => handleMenuOpen(e, item.id)}>
            <MoreIcon />
          </IconButton>
        </Box>
        
        <Paper
          sx={{
            p: 3,
            textAlign: 'center',
            background: `linear-gradient(135deg, ${alpha(theme.palette.warning.main, 0.1)} 0%, ${alpha(theme.palette.warning.light, 0.05)} 100%)`,
          }}
        >
          <Typography variant="h1" sx={{ mb: 2 }}>
            {item.content.achievement?.icon}
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
            {item.content.achievement?.title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {item.content.achievement?.description}
          </Typography>
        </Paper>
      </CardContent>
      
      <Divider />
      
      <CardActions sx={{ px: 2, py: 1 }}>
        <Button
          size="small"
          startIcon={item.engagement.liked ? <LikeIcon /> : <LikeOutlineIcon />}
          onClick={() => handleLike(item.id)}
          sx={{
            color: item.engagement.liked ? theme.palette.error.main : 'text.secondary',
          }}
        >
          {item.engagement.likes}
        </Button>
        <Button
          size="small"
          startIcon={<CommentIcon />}
          onClick={() => handleComment(item)}
          color="inherit"
        >
          {item.engagement.comments}
        </Button>
        <Button
          size="small"
          startIcon={<ShareIcon />}
          onClick={() => handleShare(item)}
          color="inherit"
        >
          {item.engagement.shares > 0 && item.engagement.shares}
        </Button>
      </CardActions>
    </Card>
  );

  const renderMilestoneItem = (item: FeedItem) => (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar src={item.userAvatar} sx={{ cursor: 'pointer' }}>
              {item.userName[0]}
            </Avatar>
            <Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                {item.userName} reached a milestone
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  {getTimeDisplay(item.timestamp)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  •
                </Typography>
                {getPrivacyIcon(item.privacy)}
              </Box>
            </Box>
          </Box>
          
          <IconButton size="small" onClick={(e) => handleMenuOpen(e, item.id)}>
            <MoreIcon />
          </IconButton>
        </Box>
        
        <Paper
          sx={{
            p: 3,
            textAlign: 'center',
            background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.1)} 0%, ${alpha(theme.palette.primary.light, 0.05)} 100%)`,
          }}
        >
          <StarIcon sx={{ fontSize: 48, color: theme.palette.warning.main, mb: 2 }} />
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
            {item.content.milestone?.title}
          </Typography>
          <Typography variant="h6" color="text.secondary">
            {item.content.milestone?.value} {item.content.milestone?.type}
          </Typography>
        </Paper>
      </CardContent>
      
      <Divider />
      
      <CardActions sx={{ px: 2, py: 1 }}>
        <Button
          size="small"
          startIcon={item.engagement.liked ? <LikeIcon /> : <LikeOutlineIcon />}
          onClick={() => handleLike(item.id)}
          sx={{
            color: item.engagement.liked ? theme.palette.error.main : 'text.secondary',
          }}
        >
          {item.engagement.likes}
        </Button>
        <Button
          size="small"
          startIcon={<CommentIcon />}
          onClick={() => handleComment(item)}
          color="inherit"
        >
          {item.engagement.comments}
        </Button>
        <Button
          size="small"
          startIcon={<ShareIcon />}
          onClick={() => handleShare(item)}
          color="inherit"
        >
          {item.engagement.shares > 0 && item.engagement.shares}
        </Button>
      </CardActions>
    </Card>
  );

  const renderNewPost = () => (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Avatar src={user?.avatar}>
          {user?.firstName?.[0]}{user?.lastName?.[0]}
        </Avatar>
        <Box sx={{ flex: 1 }}>
          <TextField
            fullWidth
            multiline
            rows={showNewPost ? 3 : 1}
            placeholder="Share a payment or activity..."
            onFocus={() => setShowNewPost(true)}
            sx={{
              '& .MuiOutlinedInput-root': {
                borderRadius: 3,
              },
            }}
          />
          <Collapse in={showNewPost}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2 }}>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <IconButton size="small">
                  <CameraIcon />
                </IconButton>
                <IconButton size="small">
                  <GifIcon />
                </IconButton>
                <IconButton size="small">
                  <EmojiIcon />
                </IconButton>
                <IconButton size="small">
                  <LocationIcon />
                </IconButton>
              </Box>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button
                  size="small"
                  onClick={() => setShowNewPost(false)}
                >
                  Cancel
                </Button>
                <Button
                  size="small"
                  variant="contained"
                  endIcon={<SendIcon />}
                >
                  Post
                </Button>
              </Box>
            </Box>
          </Collapse>
        </Box>
      </Box>
    </Paper>
  );

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Activity Feed
          </Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <IconButton onClick={handleRefresh} disabled={refreshing}>
              <RefreshIcon
                sx={refreshing ? {
                  '@keyframes rotate': {
                    from: { transform: 'rotate(0deg)' },
                    to: { transform: 'rotate(360deg)' }
                  },
                  animation: 'rotate 1s linear infinite'
                } : {}}
              />
            </IconButton>
            <IconButton>
              <FilterIcon />
            </IconButton>
          </Box>
        </Box>
        
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="All Activity" value="all" />
          <Tab label="Following" value="following" />
          <Tab label="Trending" value="trending" icon={<TrendingUpIcon />} iconPosition="end" />
        </Tabs>
      </Box>
      
      {/* New Post */}
      {renderNewPost()}
      
      {/* Feed Items */}
      {loading && feedItems.length === 0 ? (
        <Box>
          {[...Array(3)].map((_, index) => (
            <Card key={index} sx={{ mb: 2 }}>
              <CardContent>
                <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                  <Skeleton variant="circular" width={40} height={40} />
                  <Box sx={{ flex: 1 }}>
                    <Skeleton variant="text" width="30%" />
                    <Skeleton variant="text" width="20%" />
                  </Box>
                </Box>
                <Skeleton variant="text" />
                <Skeleton variant="text" width="60%" />
              </CardContent>
            </Card>
          ))}
        </Box>
      ) : (
        <Box>
          {feedItems.map((item) => (
            <Grow in key={item.id}>
              {renderFeedItem(item)}
            </Grow>
          ))}
          
          {hasMore && (
            <Box ref={infiniteScrollRef} sx={{ textAlign: 'center', py: 2 }}>
              {loading ? (
                <CircularProgress size={32} />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Scroll for more
                </Typography>
              )}
            </Box>
          )}
          
          {!hasMore && feedItems.length > 0 && (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography variant="body2" color="text.secondary">
                You've reached the end of the feed
              </Typography>
            </Box>
          )}
        </Box>
      )}
      
      {/* Comment Dialog */}
      <Dialog
        open={commentDialogOpen}
        onClose={() => setCommentDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="h6">Comments</Typography>
            <IconButton onClick={() => setCommentDialogOpen(false)}>
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          {selectedItem?.comments && selectedItem.comments.length > 0 ? (
            <List>
              {selectedItem.comments.map((comment) => (
                <ListItem key={comment.id} sx={{ px: 0 }}>
                  <ListItemAvatar>
                    <Avatar src={comment.userAvatar}>
                      {comment.userName[0]}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={comment.userName}
                    secondary={
                      <Box>
                        <Typography variant="body2">{comment.text}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatDistanceToNow(parseISO(comment.timestamp), { addSuffix: true })}
                        </Typography>
                      </Box>
                    }
                  />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
              No comments yet. Be the first to comment!
            </Typography>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <TextField
            fullWidth
            placeholder="Add a comment..."
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleAddComment()}
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={handleAddComment} disabled={!newComment.trim()}>
                    <SendIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
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
          <Typography variant="body2" sx={{ mb: 2 }}>
            Share this transaction with your friends
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={6}>
              <Button fullWidth variant="outlined" startIcon={<SendIcon />}>
                Send in Chat
              </Button>
            </Grid>
            <Grid item xs={6}>
              <Button fullWidth variant="outlined" startIcon={<ShareIcon />}>
                Share Link
              </Button>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShareDialogOpen(false)}>Cancel</Button>
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

export default SocialFeed;