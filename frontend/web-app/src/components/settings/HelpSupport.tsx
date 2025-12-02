import React, { useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  TextField,
  Button,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  InputAdornment,
  Chip,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Rating,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Paper,
  IconButton,
  Divider,
  CircularProgress,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SearchIcon from '@mui/icons-material/Search';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import ContactSupportIcon from '@mui/icons-material/ContactSupport';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import ChatIcon from '@mui/icons-material/Chat';
import ArticleIcon from '@mui/icons-material/Article';
import SchoolIcon from '@mui/icons-material/School';
import SecurityIcon from '@mui/icons-material/Security';
import PaymentIcon from '@mui/icons-material/Payment';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ScheduleIcon from '@mui/icons-material/Schedule';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import SendIcon from '@mui/icons-material/Send';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';;
import toast from 'react-hot-toast';

interface FAQ {
  id: string;
  category: string;
  question: string;
  answer: string;
  helpful?: number;
  notHelpful?: number;
}

interface SupportTicket {
  id: string;
  subject: string;
  category: string;
  status: 'open' | 'in_progress' | 'resolved' | 'closed';
  createdAt: string;
  lastUpdate: string;
  priority: 'low' | 'medium' | 'high';
}

const HelpSupport: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [showContactDialog, setShowContactDialog] = useState(false);
  const [showTicketDialog, setShowTicketDialog] = useState(false);
  const [loading, setLoading] = useState(false);
  const [expandedFaq, setExpandedFaq] = useState<string | false>(false);
  const [contactForm, setContactForm] = useState({
    category: '',
    subject: '',
    description: '',
    attachment: null as File | null,
  });

  // Mock FAQ data
  const faqs: FAQ[] = [
    {
      id: '1',
      category: 'account',
      question: 'How do I verify my account?',
      answer: 'To verify your account, go to Settings > Profile and click on "Start Verification". You\'ll need to provide a government-issued ID and complete the verification process. This usually takes 1-2 business days.',
      helpful: 245,
      notHelpful: 12,
    },
    {
      id: '2',
      category: 'payments',
      question: 'What are the transaction limits?',
      answer: 'Daily limits are $5,000 for verified accounts and $1,000 for unverified accounts. Weekly limits are $15,000 and $3,000 respectively. You can request higher limits from Payment Settings.',
      helpful: 189,
      notHelpful: 8,
    },
    {
      id: '3',
      category: 'payments',
      question: 'How long do transfers take?',
      answer: 'Instant transfers complete within minutes but may incur a small fee. Standard transfers are free and typically complete within 1-3 business days.',
      helpful: 156,
      notHelpful: 5,
    },
    {
      id: '4',
      category: 'security',
      question: 'How do I enable two-factor authentication?',
      answer: 'Go to Settings > Security and click on "Enable Two-Factor Authentication". You can choose between authenticator app, SMS, or email verification.',
      helpful: 203,
      notHelpful: 7,
    },
    {
      id: '5',
      category: 'fees',
      question: 'What fees does Waqiti charge?',
      answer: 'Waqiti is free for standard bank transfers and debit card payments. Credit card payments incur a 3% fee. Instant transfers have a 1.5% fee (minimum $0.25).',
      helpful: 298,
      notHelpful: 15,
    },
  ];

  // Mock support tickets
  const tickets: SupportTicket[] = [
    {
      id: 'TKT-001',
      subject: 'Unable to add bank account',
      category: 'payments',
      status: 'resolved',
      createdAt: '2024-01-10T10:30:00Z',
      lastUpdate: '2024-01-11T14:20:00Z',
      priority: 'medium',
    },
    {
      id: 'TKT-002',
      subject: 'Account verification pending',
      category: 'account',
      status: 'in_progress',
      createdAt: '2024-01-12T09:15:00Z',
      lastUpdate: '2024-01-13T11:45:00Z',
      priority: 'high',
    },
  ];

  const categories = [
    { id: 'all', label: 'All Topics', icon: <HelpOutline /> },
    { id: 'account', label: 'Account & Verification', icon: <AccountBalance /> },
    { id: 'payments', label: 'Payments & Transfers', icon: <Payment /> },
    { id: 'security', label: 'Security & Privacy', icon: <Security /> },
    { id: 'fees', label: 'Fees & Limits', icon: <AttachFile /> },
  ];

  const contactMethods = [
    {
      id: 'chat',
      title: 'Live Chat',
      description: 'Chat with our support team',
      availability: 'Available 24/7',
      icon: <Chat />,
      action: () => toast.success('Starting live chat...'),
    },
    {
      id: 'email',
      title: 'Email Support',
      description: 'Get help via email',
      availability: 'Response within 24 hours',
      icon: <Email />,
      action: () => setShowContactDialog(true),
    },
    {
      id: 'phone',
      title: 'Phone Support',
      description: 'Call us directly',
      availability: 'Mon-Fri 9AM-6PM EST',
      icon: <Phone />,
      action: () => toast.info('Phone: 1-800-WAQITI'),
    },
  ];

  const filteredFaqs = faqs.filter(faq => {
    const matchesSearch = searchQuery === '' || 
      faq.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
      faq.answer.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = selectedCategory === 'all' || faq.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  const handleFaqFeedback = (faqId: string, helpful: boolean) => {
    toast.success('Thank you for your feedback!');
  };

  const handleSubmitTicket = async () => {
    setLoading(true);
    try {
      // In a real app, submit to API
      await new Promise(resolve => setTimeout(resolve, 2000));
      toast.success('Support ticket submitted successfully!');
      setShowContactDialog(false);
      setContactForm({
        category: '',
        subject: '',
        description: '',
        attachment: null,
      });
    } catch (error) {
      toast.error('Failed to submit ticket');
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'open':
        return 'info';
      case 'in_progress':
        return 'warning';
      case 'resolved':
        return 'success';
      case 'closed':
        return 'default';
      default:
        return 'default';
    }
  };

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'high':
        return 'error';
      case 'medium':
        return 'warning';
      case 'low':
        return 'info';
      default:
        return 'default';
    }
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Help & Support
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Find answers to common questions or contact our support team
      </Typography>

      {/* Search Bar */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <TextField
            fullWidth
            placeholder="Search for help..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              ),
            }}
          />
        </CardContent>
      </Card>

      {/* Quick Links */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {contactMethods.map((method) => (
          <Grid item xs={12} sm={4} key={method.id}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' },
              }}
              onClick={method.action}
            >
              <CardContent>
                <Box display="flex" alignItems="center" mb={2}>
                  {method.icon}
                  <Typography variant="h6" sx={{ ml: 1 }}>
                    {method.title}
                  </Typography>
                </Box>
                <Typography variant="body2" color="text.secondary" paragraph>
                  {method.description}
                </Typography>
                <Chip 
                  label={method.availability} 
                  size="small" 
                  color={method.id === 'chat' ? 'success' : 'default'}
                />
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Categories */}
      <Box sx={{ mb: 3 }}>
        <Grid container spacing={1}>
          {categories.map((category) => (
            <Grid item key={category.id}>
              <Chip
                icon={category.icon}
                label={category.label}
                onClick={() => setSelectedCategory(category.id)}
                color={selectedCategory === category.id ? 'primary' : 'default'}
                clickable
              />
            </Grid>
          ))}
        </Grid>
      </Box>

      {/* FAQs */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Frequently Asked Questions
          </Typography>
          
          {filteredFaqs.length === 0 ? (
            <Box textAlign="center" py={4}>
              <HelpOutline sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
              <Typography variant="h6" color="text.secondary">
                No results found
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Try different search terms or browse by category
              </Typography>
            </Box>
          ) : (
            filteredFaqs.map((faq) => (
              <Accordion
                key={faq.id}
                expanded={expandedFaq === faq.id}
                onChange={(_, isExpanded) => setExpandedFaq(isExpanded ? faq.id : false)}
              >
                <AccordionSummary expandIcon={<ExpandMore />}>
                  <Typography>{faq.question}</Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Typography variant="body2" color="text.secondary" paragraph>
                    {faq.answer}
                  </Typography>
                  <Divider sx={{ my: 2 }} />
                  <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Typography variant="caption" color="text.secondary">
                      Was this helpful?
                    </Typography>
                    <Box>
                      <IconButton 
                        size="small" 
                        onClick={() => handleFaqFeedback(faq.id, true)}
                      >
                        <ThumbUp fontSize="small" />
                      </IconButton>
                      <Typography variant="caption" sx={{ mx: 1 }}>
                        {faq.helpful}
                      </Typography>
                      <IconButton 
                        size="small" 
                        onClick={() => handleFaqFeedback(faq.id, false)}
                      >
                        <ThumbDown fontSize="small" />
                      </IconButton>
                      <Typography variant="caption" sx={{ mx: 1 }}>
                        {faq.notHelpful}
                      </Typography>
                    </Box>
                  </Box>
                </AccordionDetails>
              </Accordion>
            ))
          )}
        </CardContent>
      </Card>

      {/* Support Tickets */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">My Support Tickets</Typography>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={() => setShowContactDialog(true)}
            >
              New Ticket
            </Button>
          </Box>
          
          {tickets.length === 0 ? (
            <Alert severity="info">
              You don't have any support tickets. Contact us if you need help!
            </Alert>
          ) : (
            <List>
              {tickets.map((ticket, index) => (
                <React.Fragment key={ticket.id}>
                  <ListItemButton onClick={() => setShowTicketDialog(true)}>
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="subtitle2">{ticket.subject}</Typography>
                          <Chip 
                            label={ticket.status.replace('_', ' ').toUpperCase()} 
                            size="small" 
                            color={getStatusColor(ticket.status) as any}
                          />
                          <Chip 
                            label={ticket.priority.toUpperCase()} 
                            size="small" 
                            color={getPriorityColor(ticket.priority) as any}
                          />
                        </Box>
                      }
                      secondary={
                        <Box>
                          <Typography variant="caption" color="text.secondary">
                            {ticket.id} • Created {new Date(ticket.createdAt).toLocaleDateString()}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItemButton>
                  {index < tickets.length - 1 && <Divider />}
                </React.Fragment>
              ))}
            </List>
          )}
        </CardContent>
      </Card>

      {/* Help Resources */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Help Resources
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <ListItemButton>
                <ListItemIcon>
                  <School />
                </ListItemIcon>
                <ListItemText 
                  primary="Getting Started Guide"
                  secondary="Learn the basics of using Waqiti"
                />
              </ListItemButton>
            </Grid>
            <Grid item xs={12} sm={6}>
              <ListItemButton>
                <ListItemIcon>
                  <Article />
                </ListItemIcon>
                <ListItemText 
                  primary="Video Tutorials"
                  secondary="Watch step-by-step guides"
                />
              </ListItemButton>
            </Grid>
            <Grid item xs={12} sm={6}>
              <ListItemButton>
                <ListItemIcon>
                  <Security />
                </ListItemIcon>
                <ListItemText 
                  primary="Security Best Practices"
                  secondary="Keep your account safe"
                />
              </ListItemButton>
            </Grid>
            <Grid item xs={12} sm={6}>
              <ListItemButton>
                <ListItemIcon>
                  <ContactSupport />
                </ListItemIcon>
                <ListItemText 
                  primary="Community Forum"
                  secondary="Get help from other users"
                />
              </ListItemButton>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Contact Support Dialog */}
      <Dialog
        open={showContactDialog}
        onClose={() => setShowContactDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Contact Support</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Category</InputLabel>
                <Select
                  value={contactForm.category}
                  onChange={(e) => setContactForm({ ...contactForm, category: e.target.value })}
                  label="Category"
                >
                  <MenuItem value="account">Account & Verification</MenuItem>
                  <MenuItem value="payments">Payments & Transfers</MenuItem>
                  <MenuItem value="security">Security & Privacy</MenuItem>
                  <MenuItem value="technical">Technical Issues</MenuItem>
                  <MenuItem value="other">Other</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Subject"
                value={contactForm.subject}
                onChange={(e) => setContactForm({ ...contactForm, subject: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={4}
                label="Description"
                value={contactForm.description}
                onChange={(e) => setContactForm({ ...contactForm, description: e.target.value })}
                placeholder="Please describe your issue in detail..."
              />
            </Grid>
            <Grid item xs={12}>
              <Button
                variant="outlined"
                component="label"
                startIcon={<AttachFile />}
              >
                Attach File
                <input
                  type="file"
                  hidden
                  onChange={(e) => setContactForm({ 
                    ...contactForm, 
                    attachment: e.target.files?.[0] || null 
                  })}
                />
              </Button>
              {contactForm.attachment && (
                <Typography variant="caption" sx={{ ml: 2 }}>
                  {contactForm.attachment.name}
                </Typography>
              )}
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowContactDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmitTicket}
            disabled={loading || !contactForm.category || !contactForm.subject || !contactForm.description}
            startIcon={loading ? <CircularProgress size={20} /> : <Send />}
          >
            Submit Ticket
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default HelpSupport;