import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Card,
  CardContent,
  CardActions,
  Typography,
  Button,
  TextField,
  Grid,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemButton,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tabs,
  Tab,
  Chip,
  Avatar,
  IconButton,
  InputAdornment,
  Alert,
  Divider,
  Fab,
  Badge,
  Paper,
  Breadcrumbs,
  Link,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Rating,
  LinearProgress,
  Stepper,
  Step,
  StepLabel,
  CircularProgress,
  Tooltip,
  useTheme,
  alpha,
} from '@mui/material';
import HelpIcon from '@mui/icons-material/Help';
import SearchIcon from '@mui/icons-material/Search';
import ChatIcon from '@mui/icons-material/Chat';
import PhoneIcon from '@mui/icons-material/Phone';
import EmailIcon from '@mui/icons-material/Email';
import VideoCallIcon from '@mui/icons-material/VideoCall';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import FeedbackIcon from '@mui/icons-material/Feedback';
import BugReportIcon from '@mui/icons-material/BugReport';
import ContactSupportIcon from '@mui/icons-material/ContactSupport';
import LiveHelpIcon from '@mui/icons-material/LiveHelp';
import QuestionAnswerIcon from '@mui/icons-material/QuestionAnswer';
import ScheduleIcon from '@mui/icons-material/Schedule';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import HomeIcon from '@mui/icons-material/Home';
import SecurityIcon from '@mui/icons-material/Security';
import PaymentIcon from '@mui/icons-material/Payment';
import WalletIcon from '@mui/icons-material/AccountBalance';
import TransferIcon from '@mui/icons-material/SwapHoriz';
import SettingsIcon from '@mui/icons-material/Settings';
import MobileIcon from '@mui/icons-material/Smartphone';
import DesktopIcon from '@mui/icons-material/Computer';
import DeveloperIcon from '@mui/icons-material/Code';
import BusinessIcon from '@mui/icons-material/Business';
import AccountIcon from '@mui/icons-material/PersonAdd';
import PasswordIcon from '@mui/icons-material/Lock';
import CardIcon from '@mui/icons-material/CreditCard';
import ReceiptIcon from '@mui/icons-material/Receipt';
import EmojiIcon from '@mui/icons-material/EmojiEmotions';
import SadIcon from '@mui/icons-material/SentimentDissatisfied';
import NeutralIcon from '@mui/icons-material/SentimentNeutral';
import HappyIcon from '@mui/icons-material/SentimentSatisfied';
import VeryUnhappyIcon from '@mui/icons-material/SentimentVeryDissatisfied';
import VeryHappyIcon from '@mui/icons-material/SentimentVerySatisfied';;
import { format } from 'date-fns';
import toast from 'react-hot-toast';

import { formatTimeAgo } from '@/utils/formatters';
import { useAppSelector } from '@/hooks/redux';

interface FAQItem {
  id: string;
  question: string;
  answer: string;
  category: string;
  tags: string[];
  helpful: number;
  notHelpful: number;
  lastUpdated: string;
}

interface SupportTicket {
  id: string;
  subject: string;
  message: string;
  category: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  assignedAgent?: {
    id: string;
    name: string;
    avatar?: string;
    online: boolean;
  };
  attachments: Array<{
    id: string;
    name: string;
    size: number;
    type: string;
    url: string;
  }>;
  createdAt: string;
  updatedAt: string;
  responses: Array<{
    id: string;
    author: {
      id: string;
      name: string;
      type: 'USER' | 'AGENT' | 'SYSTEM';
      avatar?: string;
    };
    message: string;
    timestamp: string;
    attachments?: any[];
  }>;
}

interface ChatMessage {
  id: string;
  author: {
    id: string;
    name: string;
    type: 'USER' | 'AGENT' | 'BOT';
    avatar?: string;
  };
  message: string;
  timestamp: string;
  type: 'TEXT' | 'FILE' | 'QUICK_REPLY' | 'TYPING';
  metadata?: {
    quickReplies?: Array<{
      id: string;
      text: string;
      action?: string;
    }>;
    suggestions?: string[];
    transferredTo?: string;
  };
}

interface HelpCategory {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  color: string;
  articleCount: number;
  subcategories?: HelpCategory[];
}

interface ContactMethod {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  availability: string;
  avgResponseTime: string;
  languages: string[];
  premium?: boolean;
}

const helpCategories: HelpCategory[] = [
  {
    id: 'getting-started',
    name: 'Getting Started',
    description: 'Account setup, verification, and first steps',
    icon: <AccountIcon />,
    color: 'primary',
    articleCount: 12,
  },
  {
    id: 'payments',
    name: 'Payments & Transfers',
    description: 'Send money, receive payments, and transaction issues',
    icon: <PaymentIcon />,
    color: 'success',
    articleCount: 25,
  },
  {
    id: 'wallet',
    name: 'Wallet & Balance',
    description: 'Wallet management, balance inquiries, and limits',
    icon: <WalletIcon />,
    color: 'info',
    articleCount: 18,
  },
  {
    id: 'security',
    name: 'Security & Privacy',
    description: '2FA, password reset, and account security',
    icon: <SecurityIcon />,
    color: 'warning',
    articleCount: 15,
  },
  {
    id: 'cards',
    name: 'Cards & Banking',
    description: 'Card linking, bank accounts, and payment methods',
    icon: <CardIcon />,
    color: 'secondary',
    articleCount: 20,
  },
  {
    id: 'mobile',
    name: 'Mobile App',
    description: 'App features, notifications, and mobile-specific help',
    icon: <MobileIcon />,
    color: 'primary',
    articleCount: 14,
  },
  {
    id: 'business',
    name: 'Business Accounts',
    description: 'Business features, invoicing, and merchant tools',
    icon: <BusinessIcon />,
    color: 'info',
    articleCount: 22,
  },
  {
    id: 'developers',
    name: 'API & Developers',
    description: 'API documentation, SDKs, and integration guides',
    icon: <DeveloperIcon />,
    color: 'secondary',
    articleCount: 8,
  },
];

const contactMethods: ContactMethod[] = [
  {
    id: 'live-chat',
    name: 'Live Chat',
    description: 'Chat with our support team in real-time',
    icon: <ChatIcon />,
    availability: '24/7',
    avgResponseTime: '< 2 minutes',
    languages: ['English', 'Spanish', 'French'],
  },
  {
    id: 'video-call',
    name: 'Video Call',
    description: 'Screen sharing and face-to-face support',
    icon: <VideoCallIcon />,
    availability: 'Mon-Fri 9AM-6PM EST',
    avgResponseTime: 'Schedule required',
    languages: ['English'],
    premium: true,
  },
  {
    id: 'phone',
    name: 'Phone Support',
    description: 'Speak directly with a support specialist',
    icon: <PhoneIcon />,
    availability: '24/7',
    avgResponseTime: '< 5 minutes',
    languages: ['English', 'Spanish'],
  },
  {
    id: 'email',
    name: 'Email Support',
    description: 'Send us a detailed message about your issue',
    icon: <EmailIcon />,
    availability: '24/7',
    avgResponseTime: '< 4 hours',
    languages: ['English', 'Spanish', 'French', 'German'],
  },
];

const mockFAQs: FAQItem[] = [
  {
    id: '1',
    question: 'How do I send money to someone?',
    answer: 'To send money: 1) Open the app and tap "Send Money", 2) Enter the recipient\'s email or phone number, 3) Enter the amount, 4) Add a message (optional), 5) Review and confirm the transaction.',
    category: 'payments',
    tags: ['send', 'money', 'transfer', 'payment'],
    helpful: 245,
    notHelpful: 12,
    lastUpdated: new Date(Date.now() - 86400000 * 2).toISOString(),
  },
  {
    id: '2',
    question: 'How do I verify my account?',
    answer: 'Account verification requires: 1) A valid government-issued ID, 2) Proof of address (utility bill or bank statement), 3) A clear selfie for identity confirmation. Upload these documents in the app under Settings > Account Verification.',
    category: 'getting-started',
    tags: ['verify', 'account', 'kyc', 'identity'],
    helpful: 189,
    notHelpful: 8,
    lastUpdated: new Date(Date.now() - 86400000 * 5).toISOString(),
  },
  {
    id: '3',
    question: 'Why was my transaction declined?',
    answer: 'Transactions can be declined for several reasons: insufficient funds, daily/monthly limits exceeded, security concerns, or issues with the recipient\'s account. Check your wallet balance and limits in the app.',
    category: 'payments',
    tags: ['declined', 'failed', 'transaction', 'limits'],
    helpful: 156,
    notHelpful: 23,
    lastUpdated: new Date(Date.now() - 86400000 * 1).toISOString(),
  },
  {
    id: '4',
    question: 'How do I enable two-factor authentication?',
    answer: 'To enable 2FA: 1) Go to Settings > Security, 2) Tap "Two-Factor Authentication", 3) Choose SMS or Authenticator App, 4) Follow the setup instructions, 5) Save your backup codes in a secure location.',
    category: 'security',
    tags: ['2fa', 'security', 'authentication', 'setup'],
    helpful: 134,
    notHelpful: 7,
    lastUpdated: new Date(Date.now() - 86400000 * 3).toISOString(),
  },
];

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`help-tabpanel-${index}`}
      aria-labelledby={`help-tab-${index}`}
      {...other}
    >
      {value === index && <Box>{children}</Box>}
    </div>
  );
}

const HelpSupportEnhanced: React.FC = () => {
  const theme = useTheme();
  const { user } = useAppSelector((state) => state.auth);
  
  const [activeTab, setActiveTab] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [showContactDialog, setShowContactDialog] = useState(false);
  const [showChatDialog, setShowChatDialog] = useState(false);
  const [showFeedbackDialog, setShowFeedbackDialog] = useState(false);
  const [showTicketDialog, setShowTicketDialog] = useState(false);
  const [selectedFAQ, setSelectedFAQ] = useState<FAQItem | null>(null);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [chatConnected, setChatConnected] = useState(false);
  const [supportTickets, setSupportTickets] = useState<SupportTicket[]>([]);
  const [feedbackRating, setFeedbackRating] = useState(0);
  const [feedbackComment, setFeedbackComment] = useState('');
  const [selectedContact, setSelectedContact] = useState<ContactMethod | null>(null);
  
  const chatEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  useEffect(() => {
    loadSupportTickets();
  }, []);
  
  useEffect(() => {
    if (chatEndRef.current) {
      chatEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [chatMessages]);
  
  const loadSupportTickets = async () => {
    // Mock data - in real app, fetch from API
    const mockTickets: SupportTicket[] = [
      {
        id: 'TICK_001',
        subject: 'Payment not received',
        message: 'I sent money to my friend but they haven\'t received it yet. Can you help?',
        category: 'payments',
        priority: 'HIGH',
        status: 'IN_PROGRESS',
        assignedAgent: {
          id: 'agent_1',
          name: 'Sarah Johnson',
          avatar: '/avatars/sarah.jpg',
          online: true,
        },
        attachments: [],
        createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
        updatedAt: new Date(Date.now() - 3600000).toISOString(),
        responses: [
          {
            id: 'resp_1',
            author: {
              id: user?.id || 'user_1',
              name: user?.name || 'User',
              type: 'USER',
            },
            message: 'I sent money to my friend but they haven\'t received it yet. Can you help?',
            timestamp: new Date(Date.now() - 86400000 * 2).toISOString(),
          },
          {
            id: 'resp_2',
            author: {
              id: 'agent_1',
              name: 'Sarah Johnson',
              type: 'AGENT',
              avatar: '/avatars/sarah.jpg',
            },
            message: 'Hi! I\'d be happy to help you with this. Can you please provide the transaction ID so I can look into this for you?',
            timestamp: new Date(Date.now() - 86400000 * 2 + 1800000).toISOString(),
          },
        ],
      },
    ];
    
    setSupportTickets(mockTickets);
  };
  
  const filteredFAQs = mockFAQs.filter(faq => {
    const matchesSearch = !searchQuery || 
      faq.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
      faq.answer.toLowerCase().includes(searchQuery.toLowerCase()) ||
      faq.tags.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));
    
    const matchesCategory = !selectedCategory || faq.category === selectedCategory;
    
    return matchesSearch && matchesCategory;
  });
  
  const startLiveChat = async () => {
    setShowChatDialog(true);
    setChatConnected(false);
    
    // Simulate connection
    setTimeout(() => {
      setChatConnected(true);
      setChatMessages([
        {
          id: 'welcome',
          author: {
            id: 'bot',
            name: 'Waqiti Assistant',
            type: 'BOT',
          },
          message: 'Hi! I\'m here to help you with any questions about Waqiti. What can I assist you with today?',
          timestamp: new Date().toISOString(),
          type: 'TEXT',
          metadata: {
            quickReplies: [
              { id: 'payment-help', text: 'Payment Issue' },
              { id: 'account-help', text: 'Account Question' },
              { id: 'technical-help', text: 'Technical Problem' },
              { id: 'other-help', text: 'Other' },
            ],
          },
        },
      ]);
    }, 2000);
  };
  
  const sendChatMessage = () => {
    if (!newMessage.trim()) return;
    
    const userMessage: ChatMessage = {
      id: `msg_${Date.now()}`,
      author: {
        id: user?.id || 'user',
        name: user?.name || 'You',
        type: 'USER',
      },
      message: newMessage,
      timestamp: new Date().toISOString(),
      type: 'TEXT',
    };
    
    setChatMessages(prev => [...prev, userMessage]);
    setNewMessage('');
    setIsTyping(true);
    
    // Simulate agent response
    setTimeout(() => {
      setIsTyping(false);
      const agentMessage: ChatMessage = {
        id: `msg_${Date.now() + 1}`,
        author: {
          id: 'agent_1',
          name: 'Sarah Johnson',
          type: 'AGENT',
          avatar: '/avatars/sarah.jpg',
        },
        message: 'Thank you for reaching out! I understand your concern. Let me look into this for you right away. Can you provide more details about the specific issue you\'re experiencing?',
        timestamp: new Date().toISOString(),
        type: 'TEXT',
      };
      
      setChatMessages(prev => [...prev, agentMessage]);
    }, 2000 + Math.random() * 2000);
  };
  
  const handleQuickReply = (reply: { id: string; text: string; action?: string }) => {
    const userMessage: ChatMessage = {
      id: `msg_${Date.now()}`,
      author: {
        id: user?.id || 'user',
        name: user?.name || 'You',
        type: 'USER',
      },
      message: reply.text,
      timestamp: new Date().toISOString(),
      type: 'QUICK_REPLY',
    };
    
    setChatMessages(prev => [...prev, userMessage]);
    
    // Remove quick replies from previous message
    setChatMessages(prev => prev.map(msg => ({
      ...msg,
      metadata: msg.metadata ? { ...msg.metadata, quickReplies: undefined } : undefined,
    })));
    
    // Simulate contextual response
    setTimeout(() => {
      let response = '';
      switch (reply.id) {
        case 'payment-help':
          response = 'I can help you with payment issues. Are you having trouble sending money, receiving a payment, or is there an issue with a specific transaction?';
          break;
        case 'account-help':
          response = 'I\'m here to help with account questions. Are you looking for help with account verification, settings, or something else?';
          break;
        case 'technical-help':
          response = 'Sorry to hear you\'re experiencing technical issues. Can you describe what\'s happening? For example, is the app not loading, are you getting error messages, or is a feature not working as expected?';
          break;
        default:
          response = 'I\'m here to help with whatever you need. Can you tell me more about what you\'d like assistance with?';
      }
      
      const agentMessage: ChatMessage = {
        id: `msg_${Date.now() + 1}`,
        author: {
          id: 'agent_1',
          name: 'Sarah Johnson',
          type: 'AGENT',
          avatar: '/avatars/sarah.jpg',
        },
        message: response,
        timestamp: new Date().toISOString(),
        type: 'TEXT',
      };
      
      setChatMessages(prev => [...prev, agentMessage]);
    }, 1000);
  };
  
  const markFAQHelpful = (faqId: string, helpful: boolean) => {
    // In real app, send to API
    toast.success(`Thank you for your feedback!`);
  };
  
  const submitFeedback = () => {
    if (feedbackRating === 0) {
      toast.error('Please provide a rating');
      return;
    }
    
    // In real app, send to API
    toast.success('Thank you for your feedback! We appreciate your input.');
    setShowFeedbackDialog(false);
    setFeedbackRating(0);
    setFeedbackComment('');
  };
  
  const renderSearchAndCategories = () => (
    <Box>
      {/* Search */}
      <TextField
        fullWidth
        placeholder="Search for help..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon />
            </InputAdornment>
          ),
        }}
        sx={{ mb: 3 }}
      />
      
      {/* Categories */}
      <Typography variant="h6" gutterBottom>
        Browse by Category
      </Typography>
      
      <Grid container spacing={2}>
        {helpCategories.map(category => (
          <Grid item xs={12} sm={6} md={3} key={category.id}>
            <Card
              sx={{
                cursor: 'pointer',
                transition: 'all 0.2s',
                border: selectedCategory === category.id ? 2 : 1,
                borderColor: selectedCategory === category.id 
                  ? `${category.color}.main` 
                  : 'divider',
                '&:hover': {
                  elevation: 4,
                  borderColor: `${category.color}.main`,
                },
              }}
              onClick={() => {
                setSelectedCategory(selectedCategory === category.id ? null : category.id);
                setActiveTab(1); // Switch to FAQ tab
              }}
            >
              <CardContent sx={{ textAlign: 'center', py: 3 }}>
                <Avatar
                  sx={{
                    bgcolor: `${category.color}.main`,
                    width: 56,
                    height: 56,
                    mx: 'auto',
                    mb: 2,
                  }}
                >
                  {category.icon}
                </Avatar>
                <Typography variant="h6" gutterBottom>
                  {category.name}
                </Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {category.description}
                </Typography>
                <Chip
                  label={`${category.articleCount} articles`}
                  size="small"
                  color={category.color as any}
                  variant="outlined"
                />
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
  
  const renderFAQs = () => (
    <Box>
      {/* Breadcrumbs */}
      {selectedCategory && (
        <Breadcrumbs sx={{ mb: 2 }}>
          <Link
            component="button"
            variant="body1"
            onClick={() => setSelectedCategory(null)}
          >
            All Categories
          </Link>
          <Typography color="text.primary">
            {helpCategories.find(c => c.id === selectedCategory)?.name}
          </Typography>
        </Breadcrumbs>
      )}
      
      {/* Search */}
      <TextField
        fullWidth
        placeholder="Search FAQs..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon />
            </InputAdornment>
          ),
        }}
        sx={{ mb: 3 }}
      />
      
      {/* Results count */}
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {filteredFAQs.length} article{filteredFAQs.length !== 1 ? 's' : ''} found
        {selectedCategory && ` in ${helpCategories.find(c => c.id === selectedCategory)?.name}`}
      </Typography>
      
      {/* FAQ List */}
      {filteredFAQs.map(faq => (
        <Accordion key={faq.id}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Box display="flex" alignItems="center" width="100%">
              <Box flex={1}>
                <Typography variant="subtitle1">
                  {faq.question}
                </Typography>
                <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                  <Chip
                    label={helpCategories.find(c => c.id === faq.category)?.name || faq.category}
                    size="small"
                    variant="outlined"
                  />
                  <Typography variant="caption" color="text.secondary">
                    Updated {formatTimeAgo(faq.lastUpdated)}
                  </Typography>
                </Box>
              </Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Box display="flex" alignItems="center" gap={0.5}>
                  <ThumbUpIcon fontSize="small" color="action" />
                  <Typography variant="caption">{faq.helpful}</Typography>
                </Box>
                <Box display="flex" alignItems="center" gap={0.5}>
                  <ThumbDownIcon fontSize="small" color="action" />
                  <Typography variant="caption">{faq.notHelpful}</Typography>
                </Box>
              </Box>
            </Box>
          </AccordionSummary>
          <AccordionDetails>
            <Typography variant="body1" paragraph>
              {faq.answer}
            </Typography>
            
            {/* Tags */}
            <Box display="flex" flexWrap="wrap" gap={0.5} mb={2}>
              {faq.tags.map(tag => (
                <Chip key={tag} label={tag} size="small" variant="outlined" />
              ))}
            </Box>
            
            <Divider sx={{ mb: 2 }} />
            
            <Box display="flex" justifyContent="between" alignItems="center">
              <Typography variant="body2" color="text.secondary">
                Was this article helpful?
              </Typography>
              <Box display="flex" gap={1}>
                <Button
                  size="small"
                  startIcon={<ThumbUpIcon />}
                  onClick={() => markFAQHelpful(faq.id, true)}
                >
                  Yes
                </Button>
                <Button
                  size="small"
                  startIcon={<ThumbDownIcon />}
                  onClick={() => markFAQHelpful(faq.id, false)}
                >
                  No
                </Button>
              </Box>
            </Box>
          </AccordionDetails>
        </Accordion>
      ))}
      
      {filteredFAQs.length === 0 && (
        <Box textAlign="center" py={4}>
          <HelpIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary">
            No articles found
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Try adjusting your search terms or browse by category
          </Typography>
          <Button
            variant="outlined"
            onClick={() => {
              setSearchQuery('');
              setSelectedCategory(null);
            }}
          >
            Clear Filters
          </Button>
        </Box>
      )}
    </Box>
  );
  
  const renderContactSupport = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Contact Support
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Choose the best way to reach us based on your needs
      </Typography>
      
      <Grid container spacing={3}>
        {contactMethods.map(method => (
          <Grid item xs={12} sm={6} key={method.id}>
            <Card
              sx={{
                height: '100%',
                cursor: 'pointer',
                transition: 'all 0.2s',
                '&:hover': {
                  elevation: 4,
                  borderColor: 'primary.main',
                },
                ...(method.premium && {
                  background: `linear-gradient(135deg, ${alpha(theme.palette.warning.main, 0.1)} 0%, ${alpha(theme.palette.warning.main, 0.05)} 100%)`,
                  border: `1px solid ${alpha(theme.palette.warning.main, 0.3)}`,
                }),
              }}
              onClick={() => {
                setSelectedContact(method);
                if (method.id === 'live-chat') {
                  startLiveChat();
                } else {
                  setShowContactDialog(true);
                }
              }}
            >
              <CardContent>
                <Box display="flex" alignItems="flex-start" mb={2}>
                  <Avatar
                    sx={{
                      bgcolor: 'primary.main',
                      mr: 2,
                      mt: 0.5,
                    }}
                  >
                    {method.icon}
                  </Avatar>
                  <Box flex={1}>
                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      <Typography variant="h6">
                        {method.name}
                      </Typography>
                      {method.premium && (
                        <Chip label="Premium" size="small" color="warning" />
                      )}
                    </Box>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      {method.description}
                    </Typography>
                  </Box>
                </Box>
                
                <Grid container spacing={1} sx={{ mb: 2 }}>
                  <Grid item xs={6}>
                    <Typography variant="caption" color="text.secondary">
                      Availability
                    </Typography>
                    <Typography variant="body2">
                      {method.availability}
                    </Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="caption" color="text.secondary">
                      Response Time
                    </Typography>
                    <Typography variant="body2">
                      {method.avgResponseTime}
                    </Typography>
                  </Grid>
                </Grid>
                
                <Box display="flex" flexWrap="wrap" gap={0.5}>
                  {method.languages.map(lang => (
                    <Chip key={lang} label={lang} size="small" variant="outlined" />
                  ))}
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
      
      {/* Recent Tickets */}
      <Box mt={4}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            Your Recent Tickets
          </Typography>
          <Button
            variant="outlined"
            startIcon={<ContactSupportIcon />}
            onClick={() => setShowTicketDialog(true)}
          >
            Create Ticket
          </Button>
        </Box>
        
        {supportTickets.length > 0 ? (
          <List>
            {supportTickets.map(ticket => {
              const getStatusColor = () => {
                switch (ticket.status) {
                  case 'OPEN': return 'info';
                  case 'IN_PROGRESS': return 'warning';
                  case 'RESOLVED': return 'success';
                  case 'CLOSED': return 'default';
                  default: return 'default';
                }
              };
              
              const getPriorityColor = () => {
                switch (ticket.priority) {
                  case 'URGENT': return 'error';
                  case 'HIGH': return 'warning';
                  case 'MEDIUM': return 'info';
                  case 'LOW': return 'default';
                  default: return 'default';
                }
              };
              
              return (
                <ListItemButton key={ticket.id}>
                  <ListItemText
                    primary={
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="subtitle1">
                          {ticket.subject}
                        </Typography>
                        <Chip
                          label={ticket.status.replace('_', ' ')}
                          size="small"
                          color={getStatusColor() as any}
                        />
                        <Chip
                          label={ticket.priority}
                          size="small"
                          color={getPriorityColor() as any}
                          variant="outlined"
                        />
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                          {ticket.message.substring(0, 100)}{ticket.message.length > 100 && '...'}
                        </Typography>
                        <Box display="flex" alignItems="center" gap={2}>
                          <Typography variant="caption">
                            Created {formatTimeAgo(ticket.createdAt)}
                          </Typography>
                          {ticket.assignedAgent && (
                            <Box display="flex" alignItems="center" gap={0.5}>
                              <Avatar sx={{ width: 16, height: 16 }}>
                                {ticket.assignedAgent.name.charAt(0)}
                              </Avatar>
                              <Typography variant="caption">
                                {ticket.assignedAgent.name}
                              </Typography>
                              {ticket.assignedAgent.online && (
                                <Box
                                  sx={{
                                    width: 6,
                                    height: 6,
                                    borderRadius: '50%',
                                    bgcolor: 'success.main',
                                  }}
                                />
                              )}
                            </Box>
                          )}
                        </Box>
                      </Box>
                    }
                  />
                  <Badge
                    badgeContent={ticket.responses.length - 1}
                    color="primary"
                    sx={{ mr: 1 }}
                  >
                    <QuestionAnswerIcon />
                  </Badge>
                </ListItemButton>
              );
            })}
          </List>
        ) : (
          <Box textAlign="center" py={4}>
            <ContactSupportIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No Support Tickets
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              You haven't created any support tickets yet
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
  
  const renderChatDialog = () => (
    <Dialog
      open={showChatDialog}
      onClose={() => setShowChatDialog(false)}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: { height: '80vh', display: 'flex', flexDirection: 'column' }
      }}
    >
      <DialogTitle sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" gap={1}>
            <ChatIcon />
            <Typography variant="h6">Live Chat Support</Typography>
            {chatConnected && (
              <Chip label="Connected" color="success" size="small" />
            )}
          </Box>
          <IconButton onClick={() => setShowChatDialog(false)}>
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>
      
      <DialogContent sx={{ flex: 1, p: 0, display: 'flex', flexDirection: 'column' }}>
        {!chatConnected ? (
          <Box
            display="flex"
            alignItems="center"
            justifyContent="center"
            flex={1}
            flexDirection="column"
            gap={2}
          >
            <CircularProgress />
            <Typography>Connecting to support...</Typography>
          </Box>
        ) : (
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            {/* Messages */}
            <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
              {chatMessages.map(message => (
                <Box key={message.id} sx={{ mb: 2 }}>
                  <Box
                    display="flex"
                    justifyContent={message.author.type === 'USER' ? 'flex-end' : 'flex-start'}
                    alignItems="flex-end"
                    gap={1}
                  >
                    {message.author.type !== 'USER' && (
                      <Avatar
                        sx={{ width: 32, height: 32 }}
                        src={message.author.avatar}
                      >
                        {message.author.name.charAt(0)}
                      </Avatar>
                    )}
                    
                    <Paper
                      sx={{
                        p: 1.5,
                        maxWidth: '70%',
                        bgcolor: message.author.type === 'USER' ? 'primary.main' : 'grey.100',
                        color: message.author.type === 'USER' ? 'white' : 'text.primary',
                      }}
                    >
                      <Typography variant="body2">
                        {message.message}
                      </Typography>
                      
                      {message.metadata?.quickReplies && (
                        <Box mt={1} display="flex" flexWrap="wrap" gap={0.5}>
                          {message.metadata.quickReplies.map(reply => (
                            <Chip
                              key={reply.id}
                              label={reply.text}
                              size="small"
                              clickable
                              onClick={() => handleQuickReply(reply)}
                              sx={{
                                bgcolor: 'primary.main',
                                color: 'white',
                                '&:hover': {
                                  bgcolor: 'primary.dark',
                                },
                              }}
                            />
                          ))}
                        </Box>
                      )}
                    </Paper>
                  </Box>
                  
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{
                      display: 'block',
                      textAlign: message.author.type === 'USER' ? 'right' : 'left',
                      mt: 0.5,
                      ml: message.author.type !== 'USER' ? 5 : 0,
                    }}
                  >
                    {formatTimeAgo(message.timestamp)}
                  </Typography>
                </Box>
              ))}
              
              {isTyping && (
                <Box display="flex" alignItems="center" gap={1} ml={5}>
                  <Avatar sx={{ width: 32, height: 32 }}>
                    S
                  </Avatar>
                  <Paper sx={{ p: 1.5, bgcolor: 'grey.100' }}>
                    <Box display="flex" gap={0.5}>
                      <Box
                        sx={{
                          width: 6,
                          height: 6,
                          borderRadius: '50%',
                          bgcolor: 'text.secondary',
                          animation: 'pulse 1.4s ease-in-out infinite both',
                          animationDelay: '0s',
                        }}
                      />
                      <Box
                        sx={{
                          width: 6,
                          height: 6,
                          borderRadius: '50%',
                          bgcolor: 'text.secondary',
                          animation: 'pulse 1.4s ease-in-out infinite both',
                          animationDelay: '0.2s',
                        }}
                      />
                      <Box
                        sx={{
                          width: 6,
                          height: 6,
                          borderRadius: '50%',
                          bgcolor: 'text.secondary',
                          animation: 'pulse 1.4s ease-in-out infinite both',
                          animationDelay: '0.4s',
                        }}
                      />
                    </Box>
                  </Paper>
                </Box>
              )}
              
              <div ref={chatEndRef} />
            </Box>
            
            {/* Message Input */}
            <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
              <Box display="flex" gap={1} alignItems="flex-end">
                <TextField
                  fullWidth
                  placeholder="Type your message..."
                  value={newMessage}
                  onChange={(e) => setNewMessage(e.target.value)}
                  onKeyPress={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      sendChatMessage();
                    }
                  }}
                  multiline
                  maxRows={3}
                  size="small"
                />
                <input
                  type="file"
                  ref={fileInputRef}
                  style={{ display: 'none' }}
                  multiple
                  accept="image/*,application/pdf,.doc,.docx"
                />
                <IconButton
                  onClick={() => fileInputRef.current?.click()}
                  size="small"
                >
                  <AttachFileIcon />
                </IconButton>
                <IconButton
                  onClick={sendChatMessage}
                  disabled={!newMessage.trim()}
                  color="primary"
                >
                  <SendIcon />
                </IconButton>
              </Box>
            </Box>
          </Box>
        )}
      </DialogContent>
      
      <style jsx>{`
        @keyframes pulse {
          0%, 80%, 100% { transform: scale(0); }
          40% { transform: scale(1); }
        }
      `}</style>
    </Dialog>
  );
  
  return (
    <Box>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5">
          Help & Support
        </Typography>
        <Button
          variant="outlined"
          startIcon={<FeedbackIcon />}
          onClick={() => setShowFeedbackDialog(true)}
        >
          Give Feedback
        </Button>
      </Box>
      
      {/* Quick Help Alert */}
      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          <strong>Need immediate help?</strong> Try our live chat for instant support, 
          or browse our FAQ section for quick answers to common questions.
        </Typography>
      </Alert>
      
      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={activeTab} onChange={(e, newValue) => setActiveTab(newValue)}>
          <Tab icon={<SearchIcon />} label="Search & Browse" />
          <Tab icon={<HelpIcon />} label="FAQ" />
          <Tab icon={<ContactSupportIcon />} label="Contact Support" />
        </Tabs>
      </Box>
      
      {/* Tab Content */}
      <TabPanel value={activeTab} index={0}>
        {renderSearchAndCategories()}
      </TabPanel>
      <TabPanel value={activeTab} index={1}>
        {renderFAQs()}
      </TabPanel>
      <TabPanel value={activeTab} index={2}>
        {renderContactSupport()}
      </TabPanel>
      
      {/* Live Chat FAB */}
      <Fab
        color="primary"
        sx={{
          position: 'fixed',
          bottom: 16,
          right: 16,
          zIndex: 1000,
        }}
        onClick={startLiveChat}
      >
        <Badge badgeContent={chatMessages.length > 0 ? '•' : 0} color="error">
          <ChatIcon />
        </Badge>
      </Fab>
      
      {/* Chat Dialog */}
      {renderChatDialog()}
      
      {/* Feedback Dialog */}
      <Dialog
        open={showFeedbackDialog}
        onClose={() => setShowFeedbackDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Share Your Feedback</DialogTitle>
        <DialogContent>
          <Box textAlign="center" mb={3}>
            <Typography variant="h6" gutterBottom>
              How would you rate your experience?
            </Typography>
            <Rating
              value={feedbackRating}
              onChange={(e, newValue) => setFeedbackRating(newValue || 0)}
              size="large"
              sx={{ mb: 2 }}
            />
            <Box display="flex" justifyContent="center" gap={1}>
              {[1, 2, 3, 4, 5].map(rating => {
                const getEmoji = () => {
                  switch (rating) {
                    case 1: return <VeryUnhappyIcon />;
                    case 2: return <SadIcon />;
                    case 3: return <NeutralIcon />;
                    case 4: return <HappyIcon />;
                    case 5: return <VeryHappyIcon />;
                    default: return <NeutralIcon />;
                  }
                };
                
                return (
                  <Tooltip key={rating} title={`${rating} star${rating !== 1 ? 's' : ''}`}>
                    <Box
                      sx={{
                        opacity: feedbackRating === 0 || feedbackRating === rating ? 1 : 0.3,
                        transition: 'opacity 0.2s',
                      }}
                    >
                      {getEmoji()}
                    </Box>
                  </Tooltip>
                );
              })}
            </Box>
          </Box>
          
          <TextField
            fullWidth
            multiline
            rows={4}
            label="Tell us more (optional)"
            placeholder="What can we do to improve your experience?"
            value={feedbackComment}
            onChange={(e) => setFeedbackComment(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowFeedbackDialog(false)}>Cancel</Button>
          <Button variant="contained" onClick={submitFeedback}>
            Submit Feedback
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default HelpSupportEnhanced;