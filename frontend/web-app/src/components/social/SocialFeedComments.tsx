import React, { memo, useState, useCallback } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Typography,
  IconButton,
  Box,
  Divider,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import CloseIcon from '@mui/icons-material/Close';
import LikeIcon from '@mui/icons-material/Favorite';
import LikeOutlineIcon from '@mui/icons-material/FavoriteBorder';;
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { formatDistanceToNow, parseISO } from 'date-fns';
import { toast } from 'react-toastify';
import { socialService } from '../../services/socialService';
import { useAppSelector } from '../../hooks/redux';

interface SocialFeedCommentsProps {
  itemId: string;
  open: boolean;
  onClose: () => void;
}

const SocialFeedComments = memo<SocialFeedCommentsProps>(({
  itemId,
  open,
  onClose
}) => {
  const { user } = useAppSelector((state) => state.auth);
  const queryClient = useQueryClient();
  const [comment, setComment] = useState('');

  // Fetch comments
  const { data: comments = [], isLoading } = useQuery({
    queryKey: ['comments', itemId],
    queryFn: () => socialService.getComments(itemId),
    enabled: open,
    staleTime: 1 * 60 * 1000, // 1 minute
  });

  // Add comment mutation
  const addCommentMutation = useMutation({
    mutationFn: (content: string) => socialService.addComment(itemId, content),
    onSuccess: () => {
      setComment('');
      queryClient.invalidateQueries(['comments', itemId]);
      toast.success('Comment added');
    },
    onError: () => {
      toast.error('Failed to add comment');
    },
  });

  // Like comment mutation
  const likeCommentMutation = useMutation({
    mutationFn: (commentId: string) => socialService.toggleCommentLike(commentId),
    onMutate: async (commentId) => {
      await queryClient.cancelQueries(['comments', itemId]);
      
      const previousComments = queryClient.getQueryData(['comments', itemId]);
      
      queryClient.setQueryData(['comments', itemId], (old: any[]) => 
        old?.map(comment => 
          comment.id === commentId 
            ? {
                ...comment,
                liked: !comment.liked,
                likesCount: comment.liked 
                  ? (comment.likesCount || 0) - 1 
                  : (comment.likesCount || 0) + 1
              }
            : comment
        ) || []
      );

      return { previousComments };
    },
    onError: (err, commentId, context) => {
      if (context?.previousComments) {
        queryClient.setQueryData(['comments', itemId], context.previousComments);
      }
      toast.error('Failed to update like');
    },
  });

  const handleSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    if (comment.trim()) {
      addCommentMutation.mutate(comment.trim());
    }
  }, [comment, addCommentMutation]);

  const handleLikeComment = useCallback((commentId: string) => {
    likeCommentMutation.mutate(commentId);
  }, [likeCommentMutation]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: { height: '80vh', display: 'flex', flexDirection: 'column' }
      }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h6">
          Comments ({comments.length})
        </Typography>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ flex: 1, overflow: 'auto', p: 0 }}>
        {isLoading ? (
          <Box sx={{ p: 2, textAlign: 'center' }}>
            <Typography color="text.secondary">Loading comments...</Typography>
          </Box>
        ) : comments.length === 0 ? (
          <Box sx={{ p: 3, textAlign: 'center' }}>
            <Typography color="text.secondary">
              No comments yet. Be the first to comment!
            </Typography>
          </Box>
        ) : (
          <List sx={{ p: 0 }}>
            {comments.map((comment, index) => (
              <React.Fragment key={comment.id}>
                <ListItem
                  sx={{ px: 2, py: 1.5, alignItems: 'flex-start' }}
                  secondaryAction={
                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <IconButton
                        size="small"
                        onClick={() => handleLikeComment(comment.id)}
                        color={comment.liked ? "error" : "default"}
                      >
                        {comment.liked ? <LikeIcon /> : <LikeOutlineIcon />}
                      </IconButton>
                      {comment.likesCount > 0 && (
                        <Typography variant="caption" color="text.secondary">
                          {comment.likesCount}
                        </Typography>
                      )}
                    </Box>
                  }
                >
                  <ListItemAvatar>
                    <Avatar
                      src={comment.user?.profilePicture}
                      alt={comment.user?.displayName}
                      sx={{ width: 32, height: 32 }}
                    >
                      {comment.user?.displayName?.charAt(0)}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                        <Typography variant="subtitle2" fontWeight="bold">
                          {comment.user?.displayName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatDistanceToNow(parseISO(comment.createdAt))} ago
                        </Typography>
                      </Box>
                    }
                    secondary={
                      <Typography variant="body2" sx={{ mt: 0.5 }}>
                        {comment.content}
                      </Typography>
                    }
                    sx={{ pr: 6 }}
                  />
                </ListItem>
                {index < comments.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </List>
        )}
      </DialogContent>

      <DialogActions sx={{ p: 2, pt: 1 }}>
        <Box
          component="form"
          onSubmit={handleSubmit}
          sx={{ display: 'flex', width: '100%', gap: 1 }}
        >
          <Avatar
            src={user?.profilePicture}
            alt={user?.displayName}
            sx={{ width: 32, height: 32, mr: 1 }}
          >
            {user?.displayName?.charAt(0)}
          </Avatar>
          <TextField
            fullWidth
            size="small"
            placeholder="Add a comment..."
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            disabled={addCommentMutation.isLoading}
            InputProps={{
              endAdornment: (
                <IconButton
                  type="submit"
                  size="small"
                  disabled={!comment.trim() || addCommentMutation.isLoading}
                >
                  <SendIcon />
                </IconButton>
              ),
            }}
          />
        </Box>
      </DialogActions>
    </Dialog>
  );
});

SocialFeedComments.displayName = 'SocialFeedComments';

export default SocialFeedComments;