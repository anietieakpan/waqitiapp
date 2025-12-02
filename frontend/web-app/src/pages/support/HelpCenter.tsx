import React, { useState, useEffect } from 'react';
import {
  Box,
  Container,
  Typography,
  Grid,
  Card,
  CardContent,
  CardActionArea,
  TextField,
  InputAdornment,
  Button,
  Chip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  IconButton,
  Paper,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Breadcrumbs,
  Link,
  Divider,
  Tab,
  Tabs,
  Alert,
  Skeleton,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpIcon from '@mui/icons-material/HelpOutline';
import AccountIcon from '@mui/icons-material/AccountBalance';
import PaymentIcon from '@mui/icons-material/Payment';
import SecurityIcon from '@mui/icons-material/Security';
import CardIcon from '@mui/icons-material/CreditCard';
import MobileIcon from '@mui/icons-material/PhoneAndroid';
import BugIcon from '@mui/icons-material/BugReport';
import LiveHelpIcon from '@mui/icons-material/LiveHelp';
import ArticleIcon from '@mui/icons-material/Article';
import ChatIcon from '@mui/icons-material/Chat';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import HomeIcon from '@mui/icons-material/Home';;
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { motion } from 'framer-motion';

interface FAQ {
  id: string;
  question: string;
  answer: string;
  category: string;
  helpful: number;
  notHelpful: number;
}

interface Article {
  id: string;
  title: string;
  summary: string;
  category: string;
  readTime: number;
  views: number;
  lastUpdated: string;
}

interface Category {
  id: string;
  name: string;
  icon: React.ReactNode;
  description: string;
  articleCount: number;
}

const HelpCenter: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [tabValue, setTabValue] = useState(0);
  const [faqs, setFaqs] = useState<FAQ[]>([]);
  const [articles, setArticles] = useState<Article[]>([]);
  const [loading, setLoading] = useState(true);
  const [helpfulFeedback, setHelpfulFeedback] = useState<Record<string, boolean>>({});

  const categories: Category[] = [
    {
      id: 'account',
      name: 'Account & Profile',
      icon: <AccountIcon />,
      description: 'Manage your account settings and profile',
      articleCount: 15,
    },
    {
      id: 'payments',
      name: 'Payments & Transfers',
      icon: <PaymentIcon />,
      description: 'Send money, payment issues, and transactions',
      articleCount: 22,
    },
    {
      id: 'security',
      name: 'Security & Privacy',
      icon: <SecurityIcon />,
      description: 'Keep your account safe and secure',
      articleCount: 18,
    },
    {
      id: 'cards',
      name: 'Cards & Banking',
      icon: <CardIcon />,
      description: 'Virtual cards, bank accounts, and more',
      articleCount: 12,
    },
    {
      id: 'mobile',
      name: 'Mobile App',
      icon: <MobileIcon />,
      description: 'Using the Waqiti mobile application',
      articleCount: 10,
    },
    {
      id: 'troubleshooting',
      name: 'Troubleshooting',
      icon: <BugIcon />,
      description: 'Fix common issues and errors',
      articleCount: 25,
    },
  ];

  const popularFAQs: FAQ[] = [
    {
      id: '1',
      question: 'How do I send money to someone?',
      answer: 'To send money: 1) Open the app and tap "Send Money", 2) Enter the recipient\'s username or scan their QR code, 3) Enter the amount and optional note, 4) Review and confirm with your PIN.',
      category: 'payments',
      helpful: 245,
      notHelpful: 12,
    },
    {
      id: '2',
      question: 'Is my money safe with Waqiti?',
      answer: 'Yes! Waqiti uses bank-level security including 256-bit encryption, secure data centers, and is regulated by financial authorities. Your funds are FDIC insured up to $250,000.',
      category: 'security',
      helpful: 189,
      notHelpful: 8,
    },
    {
      id: '3',
      question: 'How do I add money to my wallet?',
      answer: 'You can add money via: 1) Bank transfer (ACH), 2) Debit card instant transfer, 3) Direct deposit, 4) Cash deposit at partner locations. Go to your wallet and tap "Add Money" to get started.',
      category: 'account',
      helpful: 167,
      notHelpful: 15,
    },
    {
      id: '4',
      question: 'What are the transfer limits?',
      answer: 'Standard limits: $500/day for new accounts, $2,500/week. Verified accounts: $5,000/day, $15,000/week. Business accounts have higher limits. You can request limit increases in settings.',
      category: 'payments',
      helpful: 134,
      notHelpful: 10,
    },
    {
      id: '5',
      question: 'How do I reset my password?',
      answer: 'To reset your password: 1) On the login screen, tap "Forgot Password", 2) Enter your email or phone number, 3) Enter the verification code sent to you, 4) Create a new password.',
      category: 'security',
      helpful: 122,
      notHelpful: 7,
    },
  ];

  useEffect(() => {
    // Simulate loading articles and FAQs
    setTimeout(() => {
      setFaqs(popularFAQs);
      setArticles([
        {
          id: '1',
          title: 'Getting Started with Waqiti',
          summary: 'Learn the basics of using Waqiti for payments and money management.',
          category: 'account',
          readTime: 5,
          views: 1234,
          lastUpdated: '2024-01-15',
        },
        {
          id: '2',
          title: 'Understanding Transaction Fees',
          summary: 'A complete guide to Waqiti fees and how to minimize costs.',
          category: 'payments',
          readTime: 3,
          views: 876,
          lastUpdated: '2024-01-10',
        },
        {
          id: '3',
          title: 'Two-Factor Authentication Setup',
          summary: 'Secure your account with 2FA for enhanced protection.',
          category: 'security',
          readTime: 4,
          views: 654,
          lastUpdated: '2024-01-12',
        },
      ]);
      setLoading(false);
    }, 1000);
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/support/search?q=${encodeURIComponent(searchQuery)}`);
    }
  };

  const handleCategorySelect = (categoryId: string) => {
    setSelectedCategory(categoryId);
    navigate(`/support/category/${categoryId}`);
  };

  const handleArticleClick = (articleId: string) => {
    navigate(`/support/article/${articleId}`);
  };

  const handleFeedback = (faqId: string, helpful: boolean) => {
    setHelpfulFeedback({ ...helpfulFeedback, [faqId]: helpful });
    // API call to record feedback
  };

  const renderSearchSection = () => (
    <Box
      sx={{
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white',
        py: 8,
        mb: 6,
      }}
    >
      <Container maxWidth="md">
        <Typography variant="h3" align="center" gutterBottom fontWeight="bold">
          How can we help you?
        </Typography>
        <Typography variant="h6" align="center" paragraph sx={{ opacity: 0.9 }}>
          Search our knowledge base or browse topics below
        </Typography>
        
        <Paper
          component="form"
          onSubmit={handleSearch}
          sx={{
            p: '2px 4px',
            display: 'flex',
            alignItems: 'center',
            mt: 4,
          }}
          elevation={3}
        >
          <InputAdornment position="start" sx={{ ml: 2 }}>
            <SearchIcon />
          </InputAdornment>
          <TextField
            fullWidth
            placeholder="Search for help..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            variant="standard"
            InputProps={{
              disableUnderline: true,
              sx: { px: 2, py: 1 },
            }}
          />
          <Button type="submit" variant="contained" sx={{ m: 1 }}>
            Search
          </Button>
        </Paper>

        <Box sx={{ mt: 3, display: 'flex', flexWrap: 'wrap', gap: 1, justifyContent: 'center' }}>
          <Chip
            label="How to send money"
            onClick={() => setSearchQuery('send money')}
            sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: 'white' }}
          />
          <Chip
            label="Reset password"
            onClick={() => setSearchQuery('reset password')}
            sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: 'white' }}
          />
          <Chip
            label="Transaction failed"
            onClick={() => setSearchQuery('transaction failed')}
            sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: 'white' }}
          />
          <Chip
            label="Account verification"
            onClick={() => setSearchQuery('verification')}
            sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: 'white' }}
          />
        </Box>
      </Container>
    </Box>
  );

  const renderCategories = () => (
    <Container maxWidth="lg" sx={{ mb: 6 }}>
      <Typography variant="h4" gutterBottom fontWeight="bold">
        Browse by Topic
      </Typography>
      <Grid container spacing={3}>
        {categories.map((category, index) => (
          <Grid item xs={12} sm={6} md={4} key={category.id}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
            >
              <Card
                sx={{
                  height: '100%',
                  transition: 'all 0.3s',
                  '&:hover': {
                    transform: 'translateY(-4px)',
                    boxShadow: 4,
                  },
                }}
              >
                <CardActionArea
                  onClick={() => handleCategorySelect(category.id)}
                  sx={{ height: '100%' }}
                >
                  <CardContent>
                    <Box display="flex" alignItems="center" mb={2}>
                      <Box
                        sx={{
                          p: 1.5,
                          borderRadius: 2,
                          bgcolor: 'primary.light',
                          color: 'primary.main',
                          mr: 2,
                        }}
                      >
                        {category.icon}
                      </Box>
                      <Typography variant="h6" component="div">
                        {category.name}
                      </Typography>
                    </Box>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      {category.description}
                    </Typography>
                    <Typography variant="caption" color="primary">
                      {category.articleCount} articles
                    </Typography>
                  </CardContent>
                </CardActionArea>
              </Card>
            </motion.div>
          </Grid>
        ))}
      </Grid>
    </Container>
  );

  const renderFAQs = () => (
    <Container maxWidth="lg" sx={{ mb: 6 }}>
      <Typography variant="h4" gutterBottom fontWeight="bold">
        Frequently Asked Questions
      </Typography>
      {loading ? (
        <Box>
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} variant="rectangular" height={60} sx={{ mb: 2 }} />
          ))}
        </Box>
      ) : (
        <Box>
          {faqs.map((faq) => (
            <Accordion key={faq.id} sx={{ mb: 1 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography>{faq.question}</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Typography variant="body2" color="text.secondary" paragraph>
                  {faq.answer}
                </Typography>
                <Box display="flex" alignItems="center" gap={2}>
                  <Typography variant="caption" color="text.secondary">
                    Was this helpful?
                  </Typography>
                  <IconButton
                    size="small"
                    onClick={() => handleFeedback(faq.id, true)}
                    color={helpfulFeedback[faq.id] === true ? 'primary' : 'default'}
                  >
                    <ThumbUpIcon fontSize="small" />
                  </IconButton>
                  <Typography variant="caption">{faq.helpful}</Typography>
                  <IconButton
                    size="small"
                    onClick={() => handleFeedback(faq.id, false)}
                    color={helpfulFeedback[faq.id] === false ? 'primary' : 'default'}
                  >
                    <ThumbDownIcon fontSize="small" />
                  </IconButton>
                  <Typography variant="caption">{faq.notHelpful}</Typography>
                </Box>
              </AccordionDetails>
            </Accordion>
          ))}
        </Box>
      )}
      <Box textAlign="center" mt={3}>
        <Button
          variant="outlined"
          endIcon={<ArrowForwardIcon />}
          onClick={() => navigate('/support/faqs')}
        >
          View All FAQs
        </Button>
      </Box>
    </Container>
  );

  const renderContactOptions = () => (
    <Container maxWidth="lg">
      <Paper sx={{ p: 4, bgcolor: 'background.default' }}>
        <Typography variant="h4" gutterBottom fontWeight="bold" align="center">
          Still Need Help?
        </Typography>
        <Typography variant="body1" color="text.secondary" align="center" paragraph>
          Our support team is here to assist you
        </Typography>
        
        <Grid container spacing={3} sx={{ mt: 2 }}>
          <Grid item xs={12} md={4}>
            <Card sx={{ textAlign: 'center', height: '100%' }}>
              <CardContent>
                <ChatIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Live Chat
                </Typography>
                <Typography variant="body2" color="text.secondary" paragraph>
                  Chat with our support team in real-time. Available 24/7.
                </Typography>
                <Button
                  variant="contained"
                  fullWidth
                  onClick={() => navigate('/support/chat')}
                >
                  Start Chat
                </Button>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} md={4}>
            <Card sx={{ textAlign: 'center', height: '100%' }}>
              <CardContent>
                <EmailIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Submit a Ticket
                </Typography>
                <Typography variant="body2" color="text.secondary" paragraph>
                  Create a support ticket and we'll get back to you within 24 hours.
                </Typography>
                <Button
                  variant="contained"
                  fullWidth
                  onClick={() => navigate('/support/ticket/new')}
                >
                  Create Ticket
                </Button>
              </CardContent>
            </Card>
          </Grid>
          
          <Grid item xs={12} md={4}>
            <Card sx={{ textAlign: 'center', height: '100%' }}>
              <CardContent>
                <PhoneIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom>
                  Phone Support
                </Typography>
                <Typography variant="body2" color="text.secondary" paragraph>
                  Call us for urgent matters. Premium support for verified accounts.
                </Typography>
                <Button
                  variant="outlined"
                  fullWidth
                  disabled={!user?.isVerified}
                >
                  {user?.isVerified ? '1-800-WAQITI' : 'Verify Account First'}
                </Button>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Paper>
    </Container>
  );

  return (
    <Box>
      {renderSearchSection()}
      {renderCategories()}
      {renderFAQs()}
      {renderContactOptions()}
    </Box>
  );
};

export default HelpCenter;