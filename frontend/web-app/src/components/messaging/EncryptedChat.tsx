import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  IconButton,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Avatar,
  Chip,
  InputAdornment,
  CircularProgress,
  Alert,
  Tooltip,
  Badge,
  Menu,
  MenuItem,
  Divider,
  Snackbar,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import EmojiIcon from '@mui/icons-material/EmojiEmotions';
import LockIcon from '@mui/icons-material/Lock';
import TimerIcon from '@mui/icons-material/Timer';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DeleteIcon from '@mui/icons-material/Delete';
import ReplyIcon from '@mui/icons-material/Reply';
import DownloadIcon from '@mui/icons-material/Download';
import CheckIcon from '@mui/icons-material/Check';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import SecurityIcon from '@mui/icons-material/Security';;
import { formatDistanceToNow } from 'date-fns';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { sendMessage, loadMessages, markAsRead } from '../../store/slices/messagingSlice';
import { MessageType, ConversationType } from '../../types/messaging';
import MessageBubble from './MessageBubble';
import TypingIndicator from './TypingIndicator';
import AttachmentPicker from './AttachmentPicker';
import EmojiPicker from '../common/EmojiPicker';
import MessageReactions from './MessageReactions';

interface EncryptedChatProps {
  conversationId: string;
  onClose?: () => void;
}

const EncryptedChat: React.FC<EncryptedChatProps> = ({ conversationId, onClose }) => {
  const dispatch = useAppDispatch();
  const { 
    messages, 
    conversations, 
    loading, 
    encryptionStatus,
    typingUsers 
  } = useAppSelector((state) => state.messaging);
  
  const [messageText, setMessageText] = useState('');
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [showAttachments, setShowAttachments] = useState(false);
  const [isEphemeral, setIsEphemeral] = useState(false);
  const [ephemeralDuration, setEphemeralDuration] = useState(86400); // 24 hours
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [selectedMessages, setSelectedMessages] = useState<Set<string>>(new Set());
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  
  const messagesEndRef = useRef<null | HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  
  const conversation = conversations.find(c => c.id === conversationId);
  const conversationMessages = messages[conversationId] || [];
  
  useEffect(() => {
    // Load messages for conversation
    dispatch(loadMessages(conversationId));
    
    // Mark messages as read
    const unreadMessages = conversationMessages.filter(m => !m.readAt && m.senderId !== 'current-user');
    unreadMessages.forEach(message => {
      dispatch(markAsRead(message.id));
    });
  }, [conversationId, dispatch]);
  
  useEffect(() => {
    // Scroll to bottom when new messages arrive
    scrollToBottom();
  }, [conversationMessages]);
  
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };
  
  const handleSendMessage = useCallback(async () => {
    if (!messageText.trim() && !showAttachments) return;
    
    try {
      await dispatch(sendMessage({
        conversationId,
        content: messageText,
        messageType: MessageType.TEXT,
        isEphemeral,
        ephemeralDuration: isEphemeral ? ephemeralDuration : undefined,
        replyToMessageId: replyTo,
      })).unwrap();
      
      setMessageText('');
      setReplyTo(null);
      setIsEphemeral(false);
      inputRef.current?.focus();
    } catch (error) {
      setSnackbarMessage('Failed to send message');
    }
  }, [messageText, conversationId, isEphemeral, ephemeralDuration, replyTo, dispatch]);
  
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };
  
  const handleAttachment = async (file: File) => {
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('conversationId', conversationId);
      formData.append('isEphemeral', String(isEphemeral));
      
      // Upload and send attachment
      await dispatch(sendMessage({
        conversationId,
        content: '',
        messageType: MessageType.FILE,
        attachments: [file],
        isEphemeral,
        ephemeralDuration: isEphemeral ? ephemeralDuration : undefined,
      })).unwrap();
      
      setShowAttachments(false);
    } catch (error) {
      setSnackbarMessage('Failed to send attachment');
    }
  };
  
  const handleDeleteMessage = (messageId: string, forEveryone: boolean) => {
    // Implement delete message
    dispatch(deleteMessage({ messageId, forEveryone }));
    setAnchorEl(null);
  };
  
  const handleReaction = (messageId: string, emoji: string) => {
    dispatch(addReaction({ messageId, emoji }));
  };
  
  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };
  
  const handleMenuClose = () => {
    setAnchorEl(null);
  };
  
  const renderEncryptionStatus = () => {
    if (!conversation?.isEncrypted) return null;
    
    return (
      <Tooltip title="End-to-end encrypted">
        <Chip
          icon={<LockIcon />}
          label="Encrypted"
          size="small"
          color="success"
          variant="outlined"
          sx={{ ml: 1 }}
        />
      </Tooltip>
    );
  };
  
  const renderTypingIndicator = () => {
    const typingInConversation = typingUsers[conversationId] || [];
    if (typingInConversation.length === 0) return null;
    
    return <TypingIndicator users={typingInConversation} />;
  };
  
  if (!conversation) {
    return (
      <Box p={3}>
        <Alert severity="error">Conversation not found</Alert>
      </Box>
    );
  }
  
  return (
    <Paper
      elevation={3}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
      }}
    >
      {/* Header */}
      <Box
        sx={{
          p: 2,
          borderBottom: 1,
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Avatar src={conversation.avatarUrl} sx={{ mr: 2 }}>
            {conversation.name?.charAt(0)}
          </Avatar>
          <Box>
            <Typography variant="h6">{conversation.name}</Typography>
            <Typography variant="caption" color="text.secondary">
              {conversation.participantCount} participants
            </Typography>
          </Box>
          {renderEncryptionStatus()}
        </Box>
        
        <IconButton onClick={handleMenuOpen}>
          <MoreVertIcon />
        </IconButton>
      </Box>
      
      {/* Messages */}
      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          p: 2,
          bgcolor: 'background.default',
        }}
      >
        {loading ? (
          <Box display="flex" justifyContent="center" p={3}>
            <CircularProgress />
          </Box>
        ) : (
          <List>
            {conversationMessages.map((message, index) => {
              const showDate = index === 0 || 
                new Date(message.sentAt).toDateString() !== 
                new Date(conversationMessages[index - 1].sentAt).toDateString();
              
              return (
                <React.Fragment key={message.id}>
                  {showDate && (
                    <Divider sx={{ my: 2 }}>
                      <Chip 
                        label={new Date(message.sentAt).toLocaleDateString()} 
                        size="small"
                      />
                    </Divider>
                  )}
                  
                  <MessageBubble
                    message={message}
                    isOwn={message.senderId === 'current-user'}
                    onReply={() => setReplyTo(message.id)}
                    onReact={(emoji) => handleReaction(message.id, emoji)}
                    onDelete={(forEveryone) => handleDeleteMessage(message.id, forEveryone)}
                    onSelect={(selected) => {
                      const newSelected = new Set(selectedMessages);
                      if (selected) {
                        newSelected.add(message.id);
                      } else {
                        newSelected.delete(message.id);
                      }
                      setSelectedMessages(newSelected);
                    }}
                    selected={selectedMessages.has(message.id)}
                  />
                </React.Fragment>
              );
            })}
          </List>
        )}
        
        {renderTypingIndicator()}
        <div ref={messagesEndRef} />
      </Box>
      
      {/* Reply indicator */}
      {replyTo && (
        <Box
          sx={{
            px: 2,
            py: 1,
            bgcolor: 'action.selected',
            borderTop: 1,
            borderColor: 'divider',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <ReplyIcon sx={{ mr: 1 }} fontSize="small" />
            <Typography variant="body2">
              Replying to {conversationMessages.find(m => m.id === replyTo)?.senderName}
            </Typography>
          </Box>
          <IconButton size="small" onClick={() => setReplyTo(null)}>
            <DeleteIcon fontSize="small" />
          </IconButton>
        </Box>
      )}
      
      {/* Input area */}
      <Box
        sx={{
          p: 2,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
        }}
      >
        {/* Ephemeral message toggle */}
        {isEphemeral && (
          <Box sx={{ mb: 1, display: 'flex', alignItems: 'center' }}>
            <TimerIcon fontSize="small" sx={{ mr: 1 }} />
            <Typography variant="caption" sx={{ mr: 2 }}>
              Message will disappear after {ephemeralDuration / 3600} hours
            </Typography>
            <Chip
              label="Cancel"
              size="small"
              onDelete={() => setIsEphemeral(false)}
            />
          </Box>
        )}
        
        <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 1 }}>
          <IconButton onClick={() => setShowAttachments(!showAttachments)}>
            <AttachFileIcon />
          </IconButton>
          
          <IconButton onClick={() => setShowEmojiPicker(!showEmojiPicker)}>
            <EmojiIcon />
          </IconButton>
          
          <IconButton 
            onClick={() => setIsEphemeral(!isEphemeral)}
            color={isEphemeral ? 'primary' : 'default'}
          >
            <TimerIcon />
          </IconButton>
          
          <TextField
            ref={inputRef}
            fullWidth
            multiline
            maxRows={4}
            placeholder="Type a message..."
            value={messageText}
            onChange={(e) => setMessageText(e.target.value)}
            onKeyPress={handleKeyPress}
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    onClick={handleSendMessage}
                    disabled={!messageText.trim() && !showAttachments}
                    color="primary"
                  >
                    <SendIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
        </Box>
      </Box>
      
      {/* Emoji Picker */}
      {showEmojiPicker && (
        <Box
          sx={{
            position: 'absolute',
            bottom: 80,
            right: 16,
            zIndex: 1000,
          }}
        >
          <EmojiPicker
            onSelect={(emoji) => {
              setMessageText(messageText + emoji);
              setShowEmojiPicker(false);
            }}
            onClose={() => setShowEmojiPicker(false)}
          />
        </Box>
      )}
      
      {/* Attachment Picker */}
      {showAttachments && (
        <AttachmentPicker
          onSelect={handleAttachment}
          onClose={() => setShowAttachments(false)}
        />
      )}
      
      {/* Context Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => {
          // Clear messages
          handleMenuClose();
        }}>
          Clear Chat
        </MenuItem>
        <MenuItem onClick={() => {
          // Export chat
          handleMenuClose();
        }}>
          Export Chat
        </MenuItem>
        <MenuItem onClick={() => {
          // View encryption keys
          handleMenuClose();
        }}>
          <SecurityIcon fontSize="small" sx={{ mr: 1 }} />
          Encryption Info
        </MenuItem>
      </Menu>
      
      {/* Snackbar */}
      <Snackbar
        open={!!snackbarMessage}
        autoHideDuration={6000}
        onClose={() => setSnackbarMessage('')}
        message={snackbarMessage}
      />
    </Paper>
  );
};

export default EncryptedChat;