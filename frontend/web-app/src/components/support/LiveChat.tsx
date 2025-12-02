import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  IconButton,
  Avatar,
  Chip,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Divider,
  Button,
  CircularProgress,
  Alert,
  Fab,
  Badge,
  Drawer,
  AppBar,
  Toolbar,
  Menu,
  MenuItem,
  InputAdornment,
  Tooltip,
  Collapse,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import CloseIcon from '@mui/icons-material/Close';
import ChatIcon from '@mui/icons-material/Chat';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import EmojiIcon from '@mui/icons-material/EmojiEmotions';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import MinimizeIcon from '@mui/icons-material/Minimize';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import PersonIcon from '@mui/icons-material/Person';
import BotIcon from '@mui/icons-material/SmartToy';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ScheduleIcon from '@mui/icons-material/Schedule';
import BlockIcon from '@mui/icons-material/Block';;
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { motion, AnimatePresence } from 'framer-motion';
import EmojiPicker from 'emoji-picker-react';

interface Message {
  id: string;
  sender: 'user' | 'agent' | 'bot';
  senderName: string;
  content: string;
  timestamp: Date;
  type: 'text' | 'image' | 'file' | 'system';
  status?: 'sent' | 'delivered' | 'read';
  metadata?: any;
}

interface ChatSession {
  id: string;
  status: 'connecting' | 'connected' | 'queued' | 'ended';
  agent?: {
    id: string;
    name: string;
    avatar?: string;
    role: string;
  };
  startedAt: Date;
  queuePosition?: number;
  estimatedWaitTime?: number;
}

const LiveChat: React.FC = () => {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  
  const [isOpen, setIsOpen] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [session, setSession] = useState<ChatSession | null>(null);
  const [isTyping, setIsTyping] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  
  const messagesEndRef = useRef<null | HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const chatContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    // Check for existing session
    const existingSession = localStorage.getItem('chatSession');
    if (existingSession) {
      const session = JSON.parse(existingSession);
      if (session.status !== 'ended') {
        setSession(session);
        setIsOpen(true);
        loadChatHistory(session.id);
      }
    }
  }, []);

  useEffect(() => {
    if (isOpen && unreadCount > 0) {
      setUnreadCount(0);
    }
  }, [isOpen]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const startChat = async () => {
    setIsOpen(true);
    setIsMinimized(false);
    
    // Initial bot greeting
    const botMessage: Message = {
      id: Date.now().toString(),
      sender: 'bot',
      senderName: 'Waqiti Assistant',
      content: 'Hello! I\'m here to help. You can ask me anything or request to speak with a human agent.',
      timestamp: new Date(),
      type: 'text',
    };
    
    setMessages([botMessage]);
    
    // Create session
    const newSession: ChatSession = {
      id: Date.now().toString(),
      status: 'connected',
      startedAt: new Date(),
    };
    
    setSession(newSession);
    localStorage.setItem('chatSession', JSON.stringify(newSession));
  };

  const loadChatHistory = async (sessionId: string) => {
    // Load chat history from API
    // For now, using mock data
    const mockHistory: Message[] = [
      {
        id: '1',
        sender: 'bot',
        senderName: 'Waqiti Assistant',
        content: 'Welcome back! How can I help you today?',
        timestamp: new Date(Date.now() - 3600000),
        type: 'text',
      },
    ];
    setMessages(mockHistory);
  };

  const sendMessage = async () => {
    if (!inputMessage.trim() || !session) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      sender: 'user',
      senderName: user?.firstName || 'You',
      content: inputMessage,
      timestamp: new Date(),
      type: 'text',
      status: 'sent',
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputMessage('');
    setShowEmojiPicker(false);

    // Simulate typing indicator
    setIsTyping(true);

    // Check if user wants human agent
    if (inputMessage.toLowerCase().includes('human') || 
        inputMessage.toLowerCase().includes('agent') ||
        inputMessage.toLowerCase().includes('speak to someone')) {
      setTimeout(() => {
        connectToHumanAgent();
      }, 1000);
    } else {
      // Bot response
      setTimeout(() => {
        const botResponse = generateBotResponse(inputMessage);
        const botMessage: Message = {
          id: Date.now().toString(),
          sender: 'bot',
          senderName: 'Waqiti Assistant',
          content: botResponse,
          timestamp: new Date(),
          type: 'text',
        };
        setMessages((prev) => [...prev, botMessage]);
        setIsTyping(false);
      }, 1500);
    }
  };

  const generateBotResponse = (userInput: string): string => {
    const input = userInput.toLowerCase();
    
    if (input.includes('balance')) {
      return 'To check your balance, go to your wallet in the app. For security reasons, I cannot display your balance here. Would you like help with something else?';
    } else if (input.includes('send money') || input.includes('transfer')) {
      return 'To send money:\n1. Open the app and tap "Send Money"\n2. Enter recipient username or scan QR code\n3. Enter amount and confirm with PIN\n\nNeed help with a specific transfer issue?';
    } else if (input.includes('fee')) {
      return 'Waqiti charges:\n• Instant transfers: 1.5%\n• Standard transfers: Free\n• International: 3%\n• Card payments: 2.9% + $0.30\n\nWould you like more details?';
    } else if (input.includes('card')) {
      return 'For card issues:\n• Freeze card: Settings > Cards > Freeze\n• Report lost card: Contact support immediately\n• Order new card: Settings > Cards > Order\n\nDo you need help with a specific card issue?';
    } else {
      return 'I\'m not sure I understand. You can:\n• Type "agent" to speak with a human\n• Ask about balance, transfers, fees, or cards\n• Visit our Help Center for more topics';
    }
  };

  const connectToHumanAgent = async () => {
    setIsTyping(false);
    
    const systemMessage: Message = {
      id: Date.now().toString(),
      sender: 'bot',
      senderName: 'System',
      content: 'Connecting you to a human agent. Please wait...',
      timestamp: new Date(),
      type: 'system',
    };
    
    setMessages((prev) => [...prev, systemMessage]);
    
    // Update session status
    const updatedSession: ChatSession = {
      ...session!,
      status: 'queued',
      queuePosition: 3,
      estimatedWaitTime: 2,
    };
    
    setSession(updatedSession);
    
    // Simulate agent connection
    setTimeout(() => {
      const agentJoinMessage: Message = {
        id: Date.now().toString(),
        sender: 'agent',
        senderName: 'Sarah',
        content: 'Hi! I\'m Sarah from Waqiti support. I\'ve reviewed your conversation. How can I help you today?',
        timestamp: new Date(),
        type: 'text',
      };
      
      const connectedSession: ChatSession = {
        ...updatedSession,
        status: 'connected',
        agent: {
          id: 'agent-1',
          name: 'Sarah Johnson',
          role: 'Support Specialist',
          avatar: '/avatars/agent-sarah.jpg',
        },
        queuePosition: undefined,
        estimatedWaitTime: undefined,
      };
      
      setSession(connectedSession);
      setMessages((prev) => [...prev, agentJoinMessage]);
    }, 3000);
  };

  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    // Handle file upload
    const fileMessage: Message = {
      id: Date.now().toString(),
      sender: 'user',
      senderName: user?.firstName || 'You',
      content: `Uploaded file: ${file.name}`,
      timestamp: new Date(),
      type: 'file',
      metadata: {
        fileName: file.name,
        fileSize: file.size,
        fileType: file.type,
      },
    };

    setMessages((prev) => [...prev, fileMessage]);
  };

  const endChat = () => {
    if (session) {
      const endMessage: Message = {
        id: Date.now().toString(),
        sender: 'bot',
        senderName: 'System',
        content: 'Chat session ended. Thank you for contacting Waqiti support!',
        timestamp: new Date(),
        type: 'system',
      };
      
      setMessages((prev) => [...prev, endMessage]);
      
      const endedSession = { ...session, status: 'ended' as const };
      setSession(endedSession);
      localStorage.removeItem('chatSession');
      
      setTimeout(() => {
        setIsOpen(false);
        setMessages([]);
        setSession(null);
      }, 2000);
    }
  };

  const renderChatHeader = () => (
    <AppBar position="static" elevation={0}>
      <Toolbar>
        <Box display="flex" alignItems="center" flex={1}>
          {session?.agent ? (
            <>
              <Avatar src={session.agent.avatar} sx={{ mr: 2 }}>
                {session.agent.name[0]}
              </Avatar>
              <Box>
                <Typography variant="subtitle1">{session.agent.name}</Typography>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                  {session.agent.role}
                </Typography>
              </Box>
            </>
          ) : (
            <>
              <Avatar sx={{ mr: 2, bgcolor: 'secondary.main' }}>
                <BotIcon />
              </Avatar>
              <Box>
                <Typography variant="subtitle1">Waqiti Support</Typography>
                <Typography variant="caption" sx={{ opacity: 0.8 }}>
                  {session?.status === 'queued' 
                    ? `Queue position: ${session.queuePosition}` 
                    : 'AI Assistant'}
                </Typography>
              </Box>
            </>
          )}
        </Box>
        
        <IconButton size="small" onClick={() => setIsMinimized(!isMinimized)}>
          {isMinimized ? <FullscreenIcon /> : <MinimizeIcon />}
        </IconButton>
        
        <IconButton 
          size="small" 
          onClick={(e) => setAnchorEl(e.currentTarget)}
        >
          <MoreVertIcon />
        </IconButton>
        
        <IconButton size="small" onClick={endChat}>
          <CloseIcon />
        </IconButton>
      </Toolbar>
    </AppBar>
  );

  const renderMessages = () => (
    <Box
      ref={chatContainerRef}
      sx={{
        flex: 1,
        overflowY: 'auto',
        p: 2,
        bgcolor: 'background.default',
      }}
    >
      <AnimatePresence>
        {messages.map((message) => (
          <motion.div
            key={message.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.3 }}
          >
            <Box
              sx={{
                display: 'flex',
                justifyContent: message.sender === 'user' ? 'flex-end' : 'flex-start',
                mb: 2,
              }}
            >
              {message.sender !== 'user' && (
                <Avatar
                  sx={{ 
                    mr: 1, 
                    width: 32, 
                    height: 32,
                    bgcolor: message.sender === 'bot' ? 'secondary.main' : 'primary.main'
                  }}
                >
                  {message.sender === 'bot' ? <BotIcon /> : message.senderName[0]}
                </Avatar>
              )}
              
              <Box maxWidth="70%">
                {message.type === 'system' ? (
                  <Alert severity="info" sx={{ py: 0.5 }}>
                    {message.content}
                  </Alert>
                ) : (
                  <Paper
                    sx={{
                      p: 1.5,
                      bgcolor: message.sender === 'user' ? 'primary.main' : 'background.paper',
                      color: message.sender === 'user' ? 'primary.contrastText' : 'text.primary',
                    }}
                  >
                    <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                      {message.content}
                    </Typography>
                    
                    {message.type === 'file' && (
                      <Chip
                        size="small"
                        icon={<AttachFileIcon />}
                        label={message.metadata.fileName}
                        sx={{ mt: 1 }}
                      />
                    )}
                  </Paper>
                )}
                
                <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                  <Typography variant="caption" color="text.secondary">
                    {new Date(message.timestamp).toLocaleTimeString([], { 
                      hour: '2-digit', 
                      minute: '2-digit' 
                    })}
                  </Typography>
                  
                  {message.sender === 'user' && message.status && (
                    <Box display="flex" alignItems="center">
                      {message.status === 'sent' && <ScheduleIcon sx={{ fontSize: 14 }} />}
                      {message.status === 'delivered' && <CheckCircleIcon sx={{ fontSize: 14 }} />}
                      {message.status === 'read' && (
                        <>
                          <CheckCircleIcon sx={{ fontSize: 14 }} />
                          <CheckCircleIcon sx={{ fontSize: 14, ml: -0.5 }} />
                        </>
                      )}
                    </Box>
                  )}
                </Box>
              </Box>
            </Box>
          </motion.div>
        ))}
      </AnimatePresence>
      
      {isTyping && (
        <Box display="flex" alignItems="center" gap={1} ml={5}>
          <Box
            sx={{
              display: 'flex',
              gap: 0.5,
              p: 1,
              bgcolor: 'background.paper',
              borderRadius: 2,
            }}
          >
            <motion.div
              animate={{ y: [0, -5, 0] }}
              transition={{ duration: 0.6, repeat: Infinity, delay: 0 }}
            >
              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'text.secondary' }} />
            </motion.div>
            <motion.div
              animate={{ y: [0, -5, 0] }}
              transition={{ duration: 0.6, repeat: Infinity, delay: 0.2 }}
            >
              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'text.secondary' }} />
            </motion.div>
            <motion.div
              animate={{ y: [0, -5, 0] }}
              transition={{ duration: 0.6, repeat: Infinity, delay: 0.4 }}
            >
              <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'text.secondary' }} />
            </motion.div>
          </Box>
        </Box>
      )}
      
      <div ref={messagesEndRef} />
    </Box>
  );

  const renderInputArea = () => (
    <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
      {session?.status === 'queued' && (
        <Alert severity="info" sx={{ mb: 1 }}>
          You're in queue. Position: {session.queuePosition} | Est. wait: {session.estimatedWaitTime} min
        </Alert>
      )}
      
      <Box display="flex" alignItems="flex-end" gap={1}>
        <IconButton
          size="small"
          onClick={() => fileInputRef.current?.click()}
          disabled={session?.status !== 'connected'}
        >
          <AttachFileIcon />
        </IconButton>
        
        <IconButton
          size="small"
          onClick={() => setShowEmojiPicker(!showEmojiPicker)}
          disabled={session?.status !== 'connected'}
        >
          <EmojiIcon />
        </IconButton>
        
        <TextField
          fullWidth
          multiline
          maxRows={4}
          placeholder={
            session?.status === 'connected'
              ? 'Type your message...'
              : 'Connecting...'
          }
          value={inputMessage}
          onChange={(e) => setInputMessage(e.target.value)}
          onKeyPress={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              sendMessage();
            }
          }}
          disabled={session?.status !== 'connected'}
          size="small"
        />
        
        <IconButton
          color="primary"
          onClick={sendMessage}
          disabled={!inputMessage.trim() || session?.status !== 'connected'}
        >
          <SendIcon />
        </IconButton>
      </Box>
      
      <input
        ref={fileInputRef}
        type="file"
        hidden
        onChange={handleFileUpload}
        accept="image/*,.pdf,.doc,.docx"
      />
      
      {showEmojiPicker && (
        <Box sx={{ position: 'absolute', bottom: 80, left: 16, zIndex: 1 }}>
          <EmojiPicker
            onEmojiClick={(emojiObject) => {
              setInputMessage((prev) => prev + emojiObject.emoji);
              setShowEmojiPicker(false);
            }}
          />
        </Box>
      )}
    </Box>
  );

  const renderChat = () => (
    <Drawer
      anchor="right"
      open={isOpen}
      onClose={() => setIsOpen(false)}
      variant="persistent"
      sx={{
        '& .MuiDrawer-paper': {
          width: { xs: '100%', sm: 400 },
          height: { xs: '100%', sm: isMinimized ? 60 : 600 },
          position: { xs: 'fixed', sm: 'fixed' },
          bottom: 0,
          right: 16,
          top: { xs: 0, sm: 'auto' },
          borderRadius: { xs: 0, sm: '8px 8px 0 0' },
        },
      }}
    >
      {renderChatHeader()}
      
      <Collapse in={!isMinimized}>
        <Box display="flex" flexDirection="column" height="calc(100% - 64px)">
          {renderMessages()}
          {renderInputArea()}
        </Box>
      </Collapse>
      
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
      >
        <MenuItem onClick={() => { /* Email transcript */ }}>
          Email Transcript
        </MenuItem>
        <MenuItem onClick={() => { /* Sound settings */ }}>
          Sound Settings
        </MenuItem>
        <MenuItem onClick={endChat}>
          End Chat
        </MenuItem>
      </Menu>
    </Drawer>
  );

  return (
    <>
      {!isOpen && (
        <Fab
          color="primary"
          aria-label="chat"
          sx={{
            position: 'fixed',
            bottom: 16,
            right: 16,
            zIndex: 1000,
          }}
          onClick={startChat}
        >
          <Badge badgeContent={unreadCount} color="error">
            <ChatIcon />
          </Badge>
        </Fab>
      )}
      
      {renderChat()}
    </>
  );
};

export default LiveChat;