import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  Paper,
  Avatar,
  AvatarGroup,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  CircularProgress,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  Menu,
  MenuItem,
  ListItemIcon,
  useTheme,
  alpha,
  LinearProgress,
  Fab,
  Zoom,
  Tab,
  Tabs,
  Badge,
  Tooltip,
  FormControl,
  InputLabel,
  Select,
  OutlinedInput,
  InputAdornment,
  Switch,
  FormControlLabel,
  ToggleButton,
  ToggleButtonGroup,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RequestIcon from '@mui/icons-material/RequestQuote';
import SendIcon from '@mui/icons-material/Send';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import ScheduleIcon from '@mui/icons-material/Schedule';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import LinkIcon from '@mui/icons-material/Link';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import CopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import MoreIcon from '@mui/icons-material/MoreVert';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import CheckIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import PendingIcon from '@mui/icons-material/Schedule';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import GroupIcon from '@mui/icons-material/Group';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import CalendarIcon from '@mui/icons-material/CalendarToday';
import TimerIcon from '@mui/icons-material/Timer';
import SpeedIcon from '@mui/icons-material/Speed';
import RepeatIcon from '@mui/icons-material/Repeat';
import HistoryIcon from '@mui/icons-material/History';
import FilterIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import NotificationIcon from '@mui/icons-material/Notifications';
import SettingsIcon from '@mui/icons-material/Settings';
import AutoIcon from '@mui/icons-material/AutoAwesome';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import StarIcon from '@mui/icons-material/Star';
import FavoriteIcon from '@mui/icons-material/FavoriteBorder';;
import { format, parseISO, addDays, differenceInDays, startOfWeek, endOfWeek, startOfMonth, endOfMonth } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';
import toast from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';

interface MoneyRequest {
  id: string;
  title: string;
  description?: string;
  amount: number;
  currency: string;
  type: 'single' | 'recurring' | 'split' | 'invoice';
  status: 'draft' | 'pending' | 'partial' | 'paid' | 'cancelled' | 'expired';
  priority: 'low' | 'normal' | 'high' | 'urgent';
  recipient: {
    id: string;
    name: string;
    email: string;
    phone?: string;
    avatar?: string;
    type: 'individual' | 'business';
    verified?: boolean;
  };
  sender: {
    id: string;
    name: string;
    email: string;
    avatar?: string;
  };
  createdAt: string;
  updatedAt: string;
  dueDate?: string;
  expiresAt?: string;
  paidAt?: string;
  paidAmount: number;
  paymentMethod?: string;
  paymentReference?: string;
  tags?: string[];
  category?: string;
  shareLink?: string;
  qrCode?: string;
  templateId?: string;
  recurringSettings?: {
    frequency: 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'yearly';
    interval: number;
    occurrences?: number;
    nextDue?: string;
    completedOccurrences: number;
  };
  splitSettings?: {
    participants: Array<{
      id: string;
      name: string;
      email: string;
      amount: number;
      paid: boolean;
      paidAt?: string;
    }>;
    splitMethod: 'equal' | 'percentage' | 'custom';
  };
  reminders?: {
    enabled: boolean;
    frequency: 'once' | 'daily' | 'weekly';
    lastSent?: string;
    count: number;
  };
  attachments?: Array<{
    id: string;
    name: string;
    type: string;
    size: number;
    url: string;
  }>;
  notes?: string;
  metadata?: Record<string, any>;
}

interface RequestTemplate {
  id: string;
  name: string;
  description?: string;
  category: string;
  icon: string;
  color: string;
  amount?: number;
  frequency?: string;
  tags?: string[];
  isDefault: boolean;
  usage: number;
  lastUsed?: string;
}

interface RequestMoneyDashboardProps {
  userId?: string;
}

const RequestMoneyDashboard: React.FC<RequestMoneyDashboardProps> = ({ userId }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  
  const [requests, setRequests] = useState<MoneyRequest[]>([]);
  const [templates, setTemplates] = useState<RequestTemplate[]>([]);
  const [selectedRequest, setSelectedRequest] = useState<MoneyRequest | null>(null);
  const [selectedTab, setSelectedTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [createDialog, setCreateDialog] = useState(false);
  const [requestType, setRequestType] = useState<'single' | 'recurring' | 'split' | 'invoice'>('single');
  const [detailsDialog, setDetailsDialog] = useState(false);
  const [shareDialog, setShareDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [filterPriority, setFilterPriority] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'date' | 'amount' | 'dueDate' | 'status'>('date');
  const [dateRange, setDateRange] = useState<'week' | 'month' | 'all'>('month');
  const [speedDialOpen, setSpeedDialOpen] = useState(false);

  // Mock data
  const mockRequests: MoneyRequest[] = [
    {
      id: '1',
      title: 'Monthly Rent Payment',
      description: 'Rent for apartment 4B',
      amount: 1200.00,
      currency: 'USD',
      type: 'recurring',
      status: 'pending',
      priority: 'high',
      recipient: {
        id: 'landlord1',
        name: 'Property Management Co.',
        email: 'payments@propertyco.com',
        type: 'business',
        verified: true,
      },
      sender: {
        id: 'user1',
        name: 'John Doe',
        email: 'john@example.com',
        avatar: 'https://i.pravatar.cc/150?img=1',
      },
      createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      dueDate: addDays(new Date(), 3).toISOString(),
      expiresAt: addDays(new Date(), 10).toISOString(),
      paidAmount: 0,
      tags: ['rent', 'monthly', 'essential'],
      category: 'housing',
      shareLink: 'https://waqiti.com/pay/req123456',
      recurringSettings: {
        frequency: 'monthly',
        interval: 1,
        completedOccurrences: 11,
        nextDue: addDays(new Date(), 3).toISOString(),
      },
      reminders: {
        enabled: true,
        frequency: 'weekly',
        lastSent: new Date(Date.now() - 86400000 * 2).toISOString(),
        count: 2,
      },
    },
    {
      id: '2',
      title: 'Team Lunch Split',
      description: 'Friday team lunch at Italian restaurant',
      amount: 245.80,
      currency: 'USD',
      type: 'split',
      status: 'partial',
      priority: 'normal',
      recipient: {
        id: 'user2',
        name: 'Sarah Wilson',
        email: 'sarah@example.com',
        avatar: 'https://i.pravatar.cc/150?img=2',
        type: 'individual',
      },
      sender: {
        id: 'user1',
        name: 'John Doe',
        email: 'john@example.com',
        avatar: 'https://i.pravatar.cc/150?img=1',
      },
      createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      dueDate: addDays(new Date(), 7).toISOString(),
      paidAmount: 122.90,
      tags: ['lunch', 'team', 'split'],
      category: 'dining',
      splitSettings: {
        splitMethod: 'equal',
        participants: [
          {
            id: 'user3',
            name: 'Mike Johnson',
            email: 'mike@example.com',
            amount: 61.45,
            paid: true,
            paidAt: new Date(Date.now() - 86400000).toISOString(),
          },
          {
            id: 'user4',
            name: 'Emma Davis',
            email: 'emma@example.com',
            amount: 61.45,
            paid: true,
            paidAt: new Date(Date.now() - 86400000).toISOString(),
          },
          {
            id: 'user5',
            name: 'Alex Brown',
            email: 'alex@example.com',
            amount: 61.45,
            paid: false,
          },
          {
            id: 'user6',
            name: 'Lisa Chen',
            email: 'lisa@example.com',
            amount: 61.45,
            paid: false,
          },
        ],
      },
    },
    {
      id: '3',
      title: 'Freelance Project Invoice',
      description: 'Website development - Phase 1',
      amount: 2500.00,
      currency: 'USD',
      type: 'invoice',
      status: 'paid',
      priority: 'normal',
      recipient: {
        id: 'client1',
        name: 'Tech Startup Inc.',
        email: 'finance@techstartup.com',
        type: 'business',
        verified: true,
      },
      sender: {
        id: 'user1',
        name: 'John Doe',
        email: 'john@example.com',
        avatar: 'https://i.pravatar.cc/150?img=1',
      },
      createdAt: new Date(Date.now() - 86400000 * 15).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      dueDate: new Date(Date.now() - 86400000 * 8).toISOString(),
      paidAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      paidAmount: 2500.00,
      paymentMethod: 'Bank Transfer',
      paymentReference: 'INV-2024-001',
      tags: ['invoice', 'freelance', 'development'],
      category: 'business',
      attachments: [
        {
          id: 'att1',
          name: 'invoice_2024_001.pdf',
          type: 'application/pdf',
          size: 245678,
          url: 'https://example.com/invoice.pdf',
        },
      ],
    },
  ];

  const mockTemplates: RequestTemplate[] = [
    {
      id: '1',
      name: 'Monthly Rent',
      description: 'Monthly rent payment request',
      category: 'housing',
      icon: 'home',
      color: '#2196F3',
      amount: 1200,
      frequency: 'monthly',
      tags: ['rent', 'monthly', 'recurring'],
      isDefault: true,
      usage: 45,
      lastUsed: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
    {
      id: '2',
      name: 'Split Bill',
      description: 'Split restaurant or shopping bills',
      category: 'dining',
      icon: 'restaurant',
      color: '#4CAF50',
      tags: ['split', 'dining', 'group'],
      isDefault: true,
      usage: 32,
      lastUsed: new Date(Date.now() - 86400000 * 5).toISOString(),
    },
    {
      id: '3',
      name: 'Freelance Invoice',
      description: 'Invoice for freelance work',
      category: 'business',
      icon: 'business',
      color: '#FF9800',
      tags: ['invoice', 'business', 'freelance'],
      isDefault: true,
      usage: 18,
      lastUsed: new Date(Date.now() - 86400000 * 10).toISOString(),
    },
  ];

  const statusConfig = {
    draft: { color: 'default', icon: InfoIcon, label: 'Draft' },
    pending: { color: 'warning', icon: PendingIcon, label: 'Pending' },
    partial: { color: 'info', icon: WarningIcon, label: 'Partial' },
    paid: { color: 'success', icon: CheckIcon, label: 'Paid' },
    cancelled: { color: 'error', icon: CancelIcon, label: 'Cancelled' },
    expired: { color: 'error', icon: TimerIcon, label: 'Expired' },
  };

  const priorityConfig = {
    low: { color: '#4CAF50', label: 'Low' },
    normal: { color: '#2196F3', label: 'Normal' },
    high: { color: '#FF9800', label: 'High' },
    urgent: { color: '#F44336', label: 'Urgent' },
  };

  const typeIcons = {
    single: <MoneyIcon />,
    recurring: <RepeatIcon />,
    split: <GroupIcon />,
    invoice: <BusinessIcon />,
  };

  const speedDialActions = [
    { icon: <MoneyIcon />, name: 'Single Request', type: 'single' },
    { icon: <RepeatIcon />, name: 'Recurring Request', type: 'recurring' },
    { icon: <GroupIcon />, name: 'Split Bill', type: 'split' },
    { icon: <BusinessIcon />, name: 'Invoice', type: 'invoice' },
  ];

  useEffect(() => {
    loadRequests();
    loadTemplates();
  }, [userId]);

  const loadRequests = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      setRequests(mockRequests);
    } catch (error) {
      toast.error('Failed to load requests');
    } finally {
      setLoading(false);
    }
  };

  const loadTemplates = async () => {
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 500));
      setTemplates(mockTemplates);
    } catch (error) {
      toast.error('Failed to load templates');
    }
  };

  const handleCreateRequest = (type: string) => {
    setRequestType(type as any);
    setCreateDialog(true);
    navigate(`/requests/create?type=${type}`);
  };

  const handleShareRequest = (request: MoneyRequest) => {
    setSelectedRequest(request);
    setShareDialog(true);
  };

  const handleCopyLink = () => {
    if (selectedRequest?.shareLink) {
      navigator.clipboard.writeText(selectedRequest.shareLink);
      toast.success('Link copied to clipboard');
    }
  };

  const handleShareVia = (method: string) => {
    if (!selectedRequest) return;

    const message = `Payment request: ${selectedRequest.title} - ${formatCurrency(selectedRequest.amount)}`;
    const url = selectedRequest.shareLink;

    switch (method) {
      case 'whatsapp':
        window.open(`https://wa.me/?text=${encodeURIComponent(message + ' ' + url)}`);
        break;
      case 'email':
        window.location.href = `mailto:?subject=Payment Request&body=${encodeURIComponent(message + '\n\n' + url)}`;
        break;
      case 'sms':
        window.location.href = `sms:?body=${encodeURIComponent(message + ' ' + url)}`;
        break;
      default:
        if (navigator.share) {
          navigator.share({
            title: 'Payment Request',
            text: message,
            url: url,
          });
        }
    }
  };

  const calculateStats = () => {
    const totalRequests = requests.length;
    const totalAmount = requests.reduce((sum, req) => sum + req.amount, 0);
    const totalPaid = requests.reduce((sum, req) => sum + req.paidAmount, 0);
    const pendingCount = requests.filter(req => req.status === 'pending').length;
    const overdueCount = requests.filter(req => 
      req.dueDate && new Date(req.dueDate) < new Date() && req.status === 'pending'
    ).length;
    const completionRate = totalRequests > 0 ? (requests.filter(req => req.status === 'paid').length / totalRequests) * 100 : 0;

    return {
      totalRequests,
      totalAmount,
      totalPaid,
      pendingCount,
      overdueCount,
      completionRate,
    };
  };

  const getFilteredRequests = () => {
    let filtered = requests;

    // Search filter
    if (searchQuery) {
      filtered = filtered.filter(req =>
        req.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        req.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        req.recipient.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        req.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()))
      );
    }

    // Status filter
    if (filterStatus !== 'all') {
      filtered = filtered.filter(req => req.status === filterStatus);
    }

    // Priority filter
    if (filterPriority !== 'all') {
      filtered = filtered.filter(req => req.priority === filterPriority);
    }

    // Date range filter
    if (dateRange !== 'all') {
      const now = new Date();
      const startDate = dateRange === 'week' ? startOfWeek(now) : startOfMonth(now);
      const endDate = dateRange === 'week' ? endOfWeek(now) : endOfMonth(now);
      
      filtered = filtered.filter(req => {
        const createdDate = parseISO(req.createdAt);
        return createdDate >= startDate && createdDate <= endDate;
      });
    }

    return filtered;
  };

  const getSortedRequests = (requests: MoneyRequest[]) => {
    return [...requests].sort((a, b) => {
      switch (sortBy) {
        case 'date':
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        case 'amount':
          return b.amount - a.amount;
        case 'dueDate':
          if (!a.dueDate) return 1;
          if (!b.dueDate) return -1;
          return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
        case 'status':
          return a.status.localeCompare(b.status);
        default:
          return 0;
      }
    });
  };

  const getDaysUntilDue = (dueDate?: string) => {
    if (!dueDate) return null;
    return differenceInDays(parseISO(dueDate), new Date());
  };

  const renderRequestCard = (request: MoneyRequest) => {
    const statusConfig_ = statusConfig[request.status];
    const priorityConfig_ = priorityConfig[request.priority];
    const daysUntilDue = getDaysUntilDue(request.dueDate);
    const isOverdue = daysUntilDue !== null && daysUntilDue < 0;
    const progress = (request.paidAmount / request.amount) * 100;

    return (
      <Card
        key={request.id}
        sx={{
          mb: 2,
          cursor: 'pointer',
          transition: 'all 0.2s',
          '&:hover': {
            transform: 'translateY(-2px)',
            boxShadow: theme.shadows[4],
          },
        }}
        onClick={() => {
          setSelectedRequest(request);
          setDetailsDialog(true);
        }}
      >
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box sx={{ flex: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {request.title}
                </Typography>
                {typeIcons[request.type]}
                <Chip
                  size="small"
                  label={statusConfig_.label}
                  color={statusConfig_.color}
                  icon={<statusConfig_.icon />}
                />
                <Chip
                  size="small"
                  label={priorityConfig_.label}
                  sx={{
                    bgcolor: priorityConfig_.color,
                    color: 'white',
                    fontWeight: 600,
                  }}
                />
              </Box>
              {request.description && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  {request.description}
                </Typography>
              )}
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Avatar src={request.recipient.avatar} sx={{ width: 24, height: 24 }}>
                    {request.recipient.name[0]}
                  </Avatar>
                  <Typography variant="body2">
                    {request.recipient.name}
                  </Typography>
                  {request.recipient.verified && (
                    <CheckIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                  )}
                </Box>
                {request.recipient.type === 'business' && (
                  <Chip label="Business" size="small" icon={<BusinessIcon />} />
                )}
              </Box>
            </Box>
            <IconButton
              onClick={(e) => {
                e.stopPropagation();
                setSelectedRequest(request);
                setMenuAnchor(e.currentTarget);
              }}
            >
              <MoreIcon />
            </IconButton>
          </Box>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} sm={6}>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                {formatCurrency(request.amount)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total amount
              </Typography>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Typography variant="h6" sx={{ fontWeight: 600, color: isOverdue ? 'error.main' : 'text.primary' }}>
                {daysUntilDue !== null
                  ? isOverdue
                    ? `${Math.abs(daysUntilDue)} days overdue`
                    : daysUntilDue === 0
                    ? 'Due today'
                    : `Due in ${daysUntilDue} days`
                  : 'No due date'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {request.dueDate ? format(parseISO(request.dueDate), 'MMM d, yyyy') : 'Due date'}
              </Typography>
            </Grid>
          </Grid>

          {request.status === 'partial' && (
            <Box sx={{ mb: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Payment Progress
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {formatCurrency(request.paidAmount)} of {formatCurrency(request.amount)}
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={progress}
                sx={{
                  height: 8,
                  borderRadius: 4,
                  bgcolor: alpha(theme.palette.primary.main, 0.1),
                }}
              />
            </Box>
          )}

          {request.type === 'split' && request.splitSettings && (
            <Box sx={{ mb: 2 }}>
              <AvatarGroup max={4} sx={{ justifyContent: 'flex-start' }}>
                {request.splitSettings.participants.map(participant => (
                  <Tooltip key={participant.id} title={participant.name}>
                    <Avatar sx={{ width: 32, height: 32 }}>
                      {participant.name[0]}
                    </Avatar>
                  </Tooltip>
                ))}
              </AvatarGroup>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
                {request.splitSettings.participants.filter(p => p.paid).length} of{' '}
                {request.splitSettings.participants.length} paid
              </Typography>
            </Box>
          )}

          {request.tags && request.tags.length > 0 && (
            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 1 }}>
              {request.tags.map((tag, index) => (
                <Chip
                  key={index}
                  label={tag}
                  size="small"
                  variant="outlined"
                  sx={{ height: 24 }}
                />
              ))}
            </Box>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="caption" color="text.secondary">
              Created {format(parseISO(request.createdAt), 'MMM d, yyyy')}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              {request.reminders?.enabled && (
                <Tooltip title="Reminders enabled">
                  <NotificationIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                </Tooltip>
              )}
              {request.recurringSettings && (
                <Tooltip title={`Recurring ${request.recurringSettings.frequency}`}>
                  <RepeatIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                </Tooltip>
              )}
              {request.attachments && request.attachments.length > 0 && (
                <Tooltip title={`${request.attachments.length} attachments`}>
                  <Badge badgeContent={request.attachments.length} color="primary">
                    <UploadIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                  </Badge>
                </Tooltip>
              )}
            </Box>
          </Box>
        </CardContent>
      </Card>
    );
  };

  if (loading && requests.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  const stats = calculateStats();
  const filteredRequests = getFilteredRequests();
  const sortedRequests = getSortedRequests(filteredRequests);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" sx={{ fontWeight: 600 }}>
          Request Money
        </Typography>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadRequests}
            disabled={loading}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setSpeedDialOpen(true)}
          >
            New Request
          </Button>
        </Box>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <RequestIcon sx={{ color: 'primary.main' }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {stats.totalRequests}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Total Requests
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <MoneyIcon sx={{ color: 'success.main' }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {formatCurrency(stats.totalAmount)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Total Amount
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CheckIcon sx={{ color: 'success.main' }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {formatCurrency(stats.totalPaid)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Collected
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <PendingIcon sx={{ color: 'warning.main' }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {stats.pendingCount}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Pending
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <WarningIcon sx={{ color: 'error.main' }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {stats.overdueCount}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Overdue
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={2}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <TrendingUpIcon sx={{ color: 'info.main' }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {stats.completionRate.toFixed(0)}%
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Success Rate
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Search and Filters */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search requests..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel>Status</InputLabel>
              <Select
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
                label="Status"
              >
                <MenuItem value="all">All Status</MenuItem>
                <MenuItem value="pending">Pending</MenuItem>
                <MenuItem value="partial">Partial</MenuItem>
                <MenuItem value="paid">Paid</MenuItem>
                <MenuItem value="cancelled">Cancelled</MenuItem>
                <MenuItem value="expired">Expired</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel>Priority</InputLabel>
              <Select
                value={filterPriority}
                onChange={(e) => setFilterPriority(e.target.value)}
                label="Priority"
              >
                <MenuItem value="all">All Priority</MenuItem>
                <MenuItem value="low">Low</MenuItem>
                <MenuItem value="normal">Normal</MenuItem>
                <MenuItem value="high">High</MenuItem>
                <MenuItem value="urgent">Urgent</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={2}>
            <FormControl fullWidth size="small">
              <InputLabel>Sort By</InputLabel>
              <Select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as any)}
                label="Sort By"
              >
                <MenuItem value="date">Date Created</MenuItem>
                <MenuItem value="amount">Amount</MenuItem>
                <MenuItem value="dueDate">Due Date</MenuItem>
                <MenuItem value="status">Status</MenuItem>
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={6} md={2}>
            <ToggleButtonGroup
              value={dateRange}
              exclusive
              onChange={(_, value) => value && setDateRange(value)}
              size="small"
              fullWidth
            >
              <ToggleButton value="week">Week</ToggleButton>
              <ToggleButton value="month">Month</ToggleButton>
              <ToggleButton value="all">All</ToggleButton>
            </ToggleButtonGroup>
          </Grid>
        </Grid>
      </Paper>

      {/* Templates Section */}
      {templates.length > 0 && (
        <Box sx={{ mb: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">Quick Templates</Typography>
            <Button size="small" endIcon={<MoreIcon />}>
              View All
            </Button>
          </Box>
          <Grid container spacing={2}>
            {templates.slice(0, 3).map((template) => (
              <Grid item xs={12} sm={6} md={4} key={template.id}>
                <Card
                  sx={{
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    '&:hover': {
                      transform: 'translateY(-2px)',
                      boxShadow: theme.shadows[4],
                    },
                  }}
                  onClick={() => navigate(`/requests/create?template=${template.id}`)}
                >
                  <CardContent>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                      <Avatar sx={{ bgcolor: template.color, color: 'white' }}>
                        {template.icon === 'home' && <CalendarIcon />}
                        {template.icon === 'restaurant' && <GroupIcon />}
                        {template.icon === 'business' && <BusinessIcon />}
                      </Avatar>
                      <Box sx={{ flex: 1 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {template.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {template.description}
                        </Typography>
                      </Box>
                    </Box>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Typography variant="caption" color="text.secondary">
                        Used {template.usage} times
                      </Typography>
                      {template.amount && (
                        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                          {formatCurrency(template.amount)}
                        </Typography>
                      )}
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="All Requests" />
          <Tab label="Sent" />
          <Tab label="Received" />
          <Tab label="Recurring" />
          <Tab label="Split Bills" />
          <Tab label="Invoices" />
        </Tabs>
      </Paper>

      {/* Request List */}
      {sortedRequests.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <RequestIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No requests found
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {searchQuery || filterStatus !== 'all' || filterPriority !== 'all'
              ? 'Try adjusting your filters'
              : 'Create your first money request'}
          </Typography>
          {!searchQuery && filterStatus === 'all' && filterPriority === 'all' && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => handleCreateRequest('single')}
            >
              Create Request
            </Button>
          )}
        </Paper>
      ) : (
        sortedRequests.map(renderRequestCard)
      )}

      {/* Speed Dial for Creating Requests */}
      <SpeedDial
        ariaLabel="Create request"
        sx={{ position: 'fixed', bottom: 16, right: 16 }}
        icon={<SpeedDialIcon openIcon={<CloseIcon />} />}
        open={speedDialOpen}
        onOpen={() => setSpeedDialOpen(true)}
        onClose={() => setSpeedDialOpen(false)}
      >
        {speedDialActions.map((action) => (
          <SpeedDialAction
            key={action.name}
            icon={action.icon}
            tooltipTitle={action.name}
            onClick={() => {
              handleCreateRequest(action.type);
              setSpeedDialOpen(false);
            }}
          />
        ))}
      </SpeedDial>

      {/* Share Dialog */}
      <Dialog open={shareDialog} onClose={() => setShareDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Share Request</DialogTitle>
        <DialogContent>
          {selectedRequest && (
            <Box>
              <Paper sx={{ p: 2, mb: 3, bgcolor: 'grey.50' }}>
                <Typography variant="subtitle2" gutterBottom>
                  Request Link
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <TextField
                    fullWidth
                    size="small"
                    value={selectedRequest.shareLink}
                    InputProps={{ readOnly: true }}
                  />
                  <IconButton onClick={handleCopyLink}>
                    <CopyIcon />
                  </IconButton>
                </Box>
              </Paper>

              <Typography variant="subtitle2" gutterBottom>
                Share via
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<WhatsAppIcon />}
                    onClick={() => handleShareVia('whatsapp')}
                  >
                    WhatsApp
                  </Button>
                </Grid>
                <Grid item xs={6}>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<EmailIcon />}
                    onClick={() => handleShareVia('email')}
                  >
                    Email
                  </Button>
                </Grid>
                <Grid item xs={6}>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<SmsIcon />}
                    onClick={() => handleShareVia('sms')}
                  >
                    SMS
                  </Button>
                </Grid>
                <Grid item xs={6}>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<QrCodeIcon />}
                    onClick={() => toast.info('QR code generation coming soon')}
                  >
                    QR Code
                  </Button>
                </Grid>
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShareDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem
          onClick={() => {
            handleShareRequest(selectedRequest!);
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><ShareIcon /></ListItemIcon>
          Share Request
        </MenuItem>
        <MenuItem
          onClick={() => {
            navigate(`/requests/edit/${selectedRequest?.id}`);
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><AddIcon /></ListItemIcon>
          Edit Request
        </MenuItem>
        <MenuItem
          onClick={() => {
            toast.info('Send reminder feature coming soon');
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><NotificationIcon /></ListItemIcon>
          Send Reminder
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            toast.info('Cancel request feature coming soon');
            setMenuAnchor(null);
          }}
          sx={{ color: 'error.main' }}
        >
          <ListItemIcon><CancelIcon sx={{ color: 'error.main' }} /></ListItemIcon>
          Cancel Request
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default RequestMoneyDashboard;