import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Switch,
  FormControlLabel,
  Alert,
  CircularProgress,
  Paper,
  Divider,
  Chip,
  Avatar,
  Grid,
  Menu,
  MenuItem,
  ListItemIcon,
  Tab,
  Tabs,
  Badge,
  Tooltip,
  LinearProgress,
  useTheme,
  alpha,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Select,
  FormControl,
  InputLabel,
  OutlinedInput,
  Checkbox,
  ListSubheader,
  Fab,
  Zoom,
} from '@mui/material';
import NotificationIcon from '@mui/icons-material/NotificationsActive';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SendIcon from '@mui/icons-material/Send';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import MoreIcon from '@mui/icons-material/MoreVert';
import PersonIcon from '@mui/icons-material/Person';
import GroupIcon from '@mui/icons-material/Group';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import WarningIcon from '@mui/icons-material/Warning';
import CheckIcon from '@mui/icons-material/CheckCircle';
import InfoIcon from '@mui/icons-material/Info';
import AddIcon from '@mui/icons-material/Add';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';
import PushIcon from '@mui/icons-material/Notifications';
import HistoryIcon from '@mui/icons-material/History';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TimeIcon from '@mui/icons-material/AccessTime';
import CalendarIcon from '@mui/icons-material/CalendarToday';
import SettingsIcon from '@mui/icons-material/Settings';
import PauseIcon from '@mui/icons-material/Pause';
import PlayIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import FilterIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import RefreshIcon from '@mui/icons-material/Refresh';;
import { format, parseISO, addDays, differenceInDays, isAfter, isBefore } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';
import toast from 'react-hot-toast';

interface PaymentReminder {
  id: string;
  title: string;
  description?: string;
  recipientId: string;
  recipientName: string;
  recipientEmail: string;
  recipientAvatar?: string;
  amount: number;
  currency: string;
  billId?: string;
  billTitle?: string;
  groupId?: string;
  groupName?: string;
  dueDate: string;
  createdAt: string;
  updatedAt: string;
  status: 'active' | 'paused' | 'completed' | 'cancelled';
  priority: 'low' | 'medium' | 'high' | 'urgent';
  frequency: 'once' | 'daily' | 'weekly' | 'biweekly' | 'monthly';
  channels: ('email' | 'sms' | 'push')[];
  reminderDays: number[]; // Days before due date to send reminders
  lastSent?: string;
  nextSend?: string;
  sentCount: number;
  maxReminders: number;
  isAutomatic: boolean;
  template?: {
    subject: string;
    message: string;
  };
  escalation?: {
    enabled: boolean;
    days: number;
    channels: ('email' | 'sms' | 'push')[];
    template?: {
      subject: string;
      message: string;
    };
  };
}

interface ReminderTemplate {
  id: string;
  name: string;
  type: 'friendly' | 'formal' | 'urgent' | 'custom';
  subject: string;
  message: string;
  isDefault: boolean;
}

interface PaymentRemindersProps {
  groupId?: string;
  billId?: string;
  recipientId?: string;
}

const PaymentReminders: React.FC<PaymentRemindersProps> = ({
  groupId,
  billId,
  recipientId,
}) => {
  const theme = useTheme();
  
  const [reminders, setReminders] = useState<PaymentReminder[]>([]);
  const [templates, setTemplates] = useState<ReminderTemplate[]>([]);
  const [selectedTab, setSelectedTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [createDialog, setCreateDialog] = useState(false);
  const [editDialog, setEditDialog] = useState(false);
  const [deleteDialog, setDeleteDialog] = useState(false);
  const [templateDialog, setTemplateDialog] = useState(false);
  const [previewDialog, setPreviewDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedReminder, setSelectedReminder] = useState<PaymentReminder | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [filterPriority, setFilterPriority] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'dueDate' | 'priority' | 'created' | 'amount'>('dueDate');

  // Form states
  const [reminderForm, setReminderForm] = useState({
    title: '',
    description: '',
    recipientId: '',
    amount: '',
    billId: '',
    groupId: '',
    dueDate: '',
    priority: 'medium' as const,
    frequency: 'once' as const,
    channels: ['email'] as ('email' | 'sms' | 'push')[],
    reminderDays: [7, 3, 1] as number[],
    maxReminders: 5,
    isAutomatic: true,
    templateId: '',
    escalationEnabled: false,
    escalationDays: 7,
    escalationChannels: ['email'] as ('email' | 'sms' | 'push')[],
  });

  const [templateForm, setTemplateForm] = useState({
    name: '',
    type: 'friendly' as const,
    subject: '',
    message: '',
  });

  // Mock data
  const mockReminders: PaymentReminder[] = [
    {
      id: '1',
      title: 'Rent Payment Due',
      description: 'Monthly rent payment for Apartment 4B',
      recipientId: 'user2',
      recipientName: 'Sarah Wilson',
      recipientEmail: 'sarah@example.com',
      recipientAvatar: 'https://i.pravatar.cc/150?img=2',
      amount: 800.00,
      currency: 'USD',
      billId: 'bill1',
      billTitle: 'Rent - March 2024',
      groupId: 'group1',
      groupName: 'Apartment 4B',
      dueDate: addDays(new Date(), 3).toISOString(),
      createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      status: 'active',
      priority: 'high',
      frequency: 'monthly',
      channels: ['email', 'push'],
      reminderDays: [7, 3, 1],
      lastSent: new Date(Date.now() - 86400000 * 1).toISOString(),
      nextSend: addDays(new Date(), 1).toISOString(),
      sentCount: 2,
      maxReminders: 5,
      isAutomatic: true,
      template: {
        subject: 'Rent Payment Due Soon',
        message: 'Hi {{name}}, your rent payment of {{amount}} is due on {{dueDate}}. Please make sure to pay on time to avoid any late fees.',
      },
    },
    {
      id: '2',
      title: 'Utilities Bill Reminder',
      recipientId: 'user3',
      recipientName: 'Mike Johnson',
      recipientEmail: 'mike@example.com',
      recipientAvatar: 'https://i.pravatar.cc/150?img=3',
      amount: 125.75,
      currency: 'USD',
      billId: 'bill2',
      billTitle: 'Electricity Bill',
      groupId: 'group1',
      groupName: 'Apartment 4B',
      dueDate: addDays(new Date(), 1).toISOString(),
      createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
      status: 'active',
      priority: 'urgent',
      frequency: 'once',
      channels: ['email', 'sms'],
      reminderDays: [3, 1],
      lastSent: new Date(Date.now() - 86400000 * 2).toISOString(),
      nextSend: new Date().toISOString(),
      sentCount: 1,
      maxReminders: 3,
      isAutomatic: false,
    },
    {
      id: '3',
      title: 'Grocery Split Payment',
      recipientId: 'user4',
      recipientName: 'Emma Davis',
      recipientEmail: 'emma@example.com',
      amount: 45.50,
      currency: 'USD',
      billId: 'bill3',
      billTitle: 'Weekly Groceries',
      dueDate: addDays(new Date(), -2).toISOString(),
      createdAt: new Date(Date.now() - 86400000 * 7).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      status: 'paused',
      priority: 'medium',
      frequency: 'weekly',
      channels: ['email'],
      reminderDays: [2],
      sentCount: 3,
      maxReminders: 4,
      isAutomatic: true,
      escalation: {
        enabled: true,
        days: 5,
        channels: ['email', 'sms'],
        template: {
          subject: 'Urgent: Payment Overdue',
          message: 'Hi {{name}}, your payment of {{amount}} is now overdue. Please pay immediately to avoid further action.',
        },
      },
    },
  ];

  const mockTemplates: ReminderTemplate[] = [
    {
      id: '1',
      name: 'Friendly Reminder',
      type: 'friendly',
      subject: 'Friendly reminder: {{title}}',
      message: 'Hey {{name}}, just a friendly reminder that {{title}} of {{amount}} is due on {{dueDate}}. Thanks!',
      isDefault: true,
    },
    {
      id: '2',
      name: 'Formal Notice',
      type: 'formal',
      subject: 'Payment Due: {{title}}',
      message: 'Dear {{name}}, this is a formal reminder that your payment of {{amount}} for {{title}} is due on {{dueDate}}. Please ensure timely payment.',
      isDefault: false,
    },
    {
      id: '3',
      name: 'Urgent Notice',
      type: 'urgent',
      subject: 'URGENT: {{title}} - Payment Required',
      message: 'URGENT: {{name}}, your payment of {{amount}} for {{title}} is overdue as of {{dueDate}}. Immediate payment is required.',
      isDefault: false,
    },
  ];

  const priorityConfig = {
    low: { color: '#4CAF50', icon: InfoIcon, label: 'Low' },
    medium: { color: '#FF9800', icon: ScheduleIcon, label: 'Medium' },
    high: { color: '#F44336', icon: WarningIcon, label: 'High' },
    urgent: { color: '#E91E63', icon: NotificationIcon, label: 'Urgent' },
  };

  const statusConfig = {
    active: { color: 'success', icon: PlayIcon, label: 'Active' },
    paused: { color: 'warning', icon: PauseIcon, label: 'Paused' },
    completed: { color: 'info', icon: CheckIcon, label: 'Completed' },
    cancelled: { color: 'error', icon: StopIcon, label: 'Cancelled' },
  };

  const channelIcons = {
    email: EmailIcon,
    sms: SmsIcon,
    push: PushIcon,
  };

  useEffect(() => {
    loadReminders();
    loadTemplates();
  }, [groupId, billId, recipientId]);

  const loadReminders = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      let filteredReminders = mockReminders;
      
      if (groupId) {
        filteredReminders = filteredReminders.filter(r => r.groupId === groupId);
      }
      if (billId) {
        filteredReminders = filteredReminders.filter(r => r.billId === billId);
      }
      if (recipientId) {
        filteredReminders = filteredReminders.filter(r => r.recipientId === recipientId);
      }
      
      setReminders(filteredReminders);
    } catch (error) {
      toast.error('Failed to load reminders');
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

  const handleCreateReminder = async () => {
    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const template = templates.find(t => t.id === reminderForm.templateId);
      const newReminder: PaymentReminder = {
        id: `reminder_${Date.now()}`,
        title: reminderForm.title,
        description: reminderForm.description,
        recipientId: reminderForm.recipientId,
        recipientName: 'User Name', // Would come from API
        recipientEmail: 'user@example.com', // Would come from API
        amount: parseFloat(reminderForm.amount),
        currency: 'USD',
        billId: reminderForm.billId || undefined,
        groupId: reminderForm.groupId || undefined,
        dueDate: reminderForm.dueDate,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        status: 'active',
        priority: reminderForm.priority,
        frequency: reminderForm.frequency,
        channels: reminderForm.channels,
        reminderDays: reminderForm.reminderDays,
        maxReminders: reminderForm.maxReminders,
        isAutomatic: reminderForm.isAutomatic,
        sentCount: 0,
        template: template ? {
          subject: template.subject,
          message: template.message,
        } : undefined,
        escalation: reminderForm.escalationEnabled ? {
          enabled: true,
          days: reminderForm.escalationDays,
          channels: reminderForm.escalationChannels,
        } : undefined,
      };

      setReminders([newReminder, ...reminders]);
      setCreateDialog(false);
      resetForm();
      toast.success('Reminder created successfully');
    } catch (error) {
      toast.error('Failed to create reminder');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateReminder = async () => {
    if (!selectedReminder) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const updatedReminder = {
        ...selectedReminder,
        ...reminderForm,
        amount: parseFloat(reminderForm.amount),
        updatedAt: new Date().toISOString(),
      };

      setReminders(reminders.map(r => 
        r.id === selectedReminder.id ? updatedReminder : r
      ));
      setEditDialog(false);
      setSelectedReminder(null);
      toast.success('Reminder updated successfully');
    } catch (error) {
      toast.error('Failed to update reminder');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteReminder = async () => {
    if (!selectedReminder) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      setReminders(reminders.filter(r => r.id !== selectedReminder.id));
      setDeleteDialog(false);
      setSelectedReminder(null);
      toast.success('Reminder deleted successfully');
    } catch (error) {
      toast.error('Failed to delete reminder');
    } finally {
      setLoading(false);
    }
  };

  const handleSendReminder = async (reminder: PaymentReminder) => {
    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1500));

      const updatedReminder = {
        ...reminder,
        lastSent: new Date().toISOString(),
        sentCount: reminder.sentCount + 1,
        nextSend: addDays(new Date(), 7).toISOString(), // Next reminder in 7 days
        updatedAt: new Date().toISOString(),
      };

      setReminders(reminders.map(r => 
        r.id === reminder.id ? updatedReminder : r
      ));
      toast.success(`Reminder sent to ${reminder.recipientName}`);
    } catch (error) {
      toast.error('Failed to send reminder');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (reminder: PaymentReminder) => {
    try {
      const newStatus = reminder.status === 'active' ? 'paused' : 'active';
      
      const updatedReminder = {
        ...reminder,
        status: newStatus,
        updatedAt: new Date().toISOString(),
      };

      setReminders(reminders.map(r => 
        r.id === reminder.id ? updatedReminder : r
      ));
      toast.success(`Reminder ${newStatus}`);
    } catch (error) {
      toast.error('Failed to update reminder status');
    }
  };

  const resetForm = () => {
    setReminderForm({
      title: '',
      description: '',
      recipientId: '',
      amount: '',
      billId: '',
      groupId: '',
      dueDate: '',
      priority: 'medium',
      frequency: 'once',
      channels: ['email'],
      reminderDays: [7, 3, 1],
      maxReminders: 5,
      isAutomatic: true,
      templateId: '',
      escalationEnabled: false,
      escalationDays: 7,
      escalationChannels: ['email'],
    });
  };

  const getFilteredReminders = () => {
    return reminders.filter(reminder => {
      const matchesSearch = reminder.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           reminder.recipientName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           reminder.description?.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesStatus = filterStatus === 'all' || reminder.status === filterStatus;
      const matchesPriority = filterPriority === 'all' || reminder.priority === filterPriority;
      
      return matchesSearch && matchesStatus && matchesPriority;
    });
  };

  const getSortedReminders = (filteredReminders: PaymentReminder[]) => {
    return [...filteredReminders].sort((a, b) => {
      switch (sortBy) {
        case 'dueDate':
          return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
        case 'priority':
          const priorityOrder = { urgent: 4, high: 3, medium: 2, low: 1 };
          return priorityOrder[b.priority] - priorityOrder[a.priority];
        case 'created':
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        case 'amount':
          return b.amount - a.amount;
        default:
          return 0;
      }
    });
  };

  const getDaysUntilDue = (dueDate: string) => {
    return differenceInDays(parseISO(dueDate), new Date());
  };

  const renderReminderCard = (reminder: PaymentReminder) => {
    const daysUntilDue = getDaysUntilDue(reminder.dueDate);
    const isOverdue = daysUntilDue < 0;
    const isDueSoon = daysUntilDue <= 3 && daysUntilDue >= 0;
    const priorityConfig_ = priorityConfig[reminder.priority];
    const statusConfig_ = statusConfig[reminder.status];

    return (
      <Card key={reminder.id} sx={{ mb: 2 }}>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box sx={{ flex: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {reminder.title}
                </Typography>
                <Chip
                  size="small"
                  label={priorityConfig_.label}
                  sx={{ bgcolor: priorityConfig_.color, color: 'white' }}
                />
                <Chip
                  size="small"
                  label={statusConfig_.label}
                  color={statusConfig_.color}
                  icon={<statusConfig_.icon />}
                />
              </Box>
              {reminder.description && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  {reminder.description}
                </Typography>
              )}
            </Box>
            <IconButton
              onClick={(e) => {
                setSelectedReminder(reminder);
                setMenuAnchor(e.currentTarget);
              }}
            >
              <MoreIcon />
            </IconButton>
          </Box>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} sm={6}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar src={reminder.recipientAvatar}>
                  {reminder.recipientName[0]}
                </Avatar>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    {reminder.recipientName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {reminder.recipientEmail}
                  </Typography>
                </Box>
              </Box>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Box sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {formatCurrency(reminder.amount)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {reminder.billTitle || 'Payment due'}
                </Typography>
              </Box>
            </Grid>
          </Grid>

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <CalendarIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
              <Typography
                variant="body2"
                sx={{
                  color: isOverdue ? 'error.main' : isDueSoon ? 'warning.main' : 'text.secondary',
                  fontWeight: isOverdue || isDueSoon ? 600 : 400,
                }}
              >
                {isOverdue
                  ? `Overdue by ${Math.abs(daysUntilDue)} day${Math.abs(daysUntilDue) > 1 ? 's' : ''}`
                  : daysUntilDue === 0
                  ? 'Due today'
                  : `Due in ${daysUntilDue} day${daysUntilDue > 1 ? 's' : ''}`
                }
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              {reminder.channels.map((channel) => {
                const ChannelIcon = channelIcons[channel];
                return (
                  <Tooltip key={channel} title={channel.toUpperCase()}>
                    <ChannelIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                  </Tooltip>
                );
              })}
            </Box>
          </Box>

          {reminder.sentCount > 0 && (
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="caption" color="text.secondary">
                Sent {reminder.sentCount} of {reminder.maxReminders} reminders
              </Typography>
              {reminder.lastSent && (
                <Typography variant="caption" color="text.secondary">
                  Last sent: {format(parseISO(reminder.lastSent), 'MMM d, h:mm a')}
                </Typography>
              )}
            </Box>
          )}

          <LinearProgress
            variant="determinate"
            value={(reminder.sentCount / reminder.maxReminders) * 100}
            sx={{
              height: 6,
              borderRadius: 3,
              bgcolor: alpha(theme.palette.primary.main, 0.1),
              mb: 2,
            }}
          />

          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
            <Button
              size="small"
              variant="outlined"
              startIcon={reminder.status === 'active' ? <PauseIcon /> : <PlayIcon />}
              onClick={() => handleToggleStatus(reminder)}
              disabled={loading}
            >
              {reminder.status === 'active' ? 'Pause' : 'Resume'}
            </Button>
            <Button
              size="small"
              variant="contained"
              startIcon={<SendIcon />}
              onClick={() => handleSendReminder(reminder)}
              disabled={loading || reminder.status !== 'active'}
            >
              Send Now
            </Button>
          </Box>
        </CardContent>
      </Card>
    );
  };

  if (loading && reminders.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  const filteredReminders = getFilteredReminders();
  const sortedReminders = getSortedReminders(filteredReminders);
  const activeReminders = reminders.filter(r => r.status === 'active').length;
  const overdueReminders = reminders.filter(r => getDaysUntilDue(r.dueDate) < 0).length;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" sx={{ fontWeight: 600 }}>
          Payment Reminders
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateDialog(true)}
        >
          Create Reminder
        </Button>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar sx={{ bgcolor: 'success.main' }}>
                  <NotificationIcon />
                </Avatar>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {activeReminders}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Active Reminders
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar sx={{ bgcolor: 'error.main' }}>
                  <WarningIcon />
                </Avatar>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {overdueReminders}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Overdue Payments
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar sx={{ bgcolor: 'info.main' }}>
                  <HistoryIcon />
                </Avatar>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {reminders.reduce((sum, r) => sum + r.sentCount, 0)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Total Sent
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Search and Filter Controls */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search reminders..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
              }}
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              select
              label="Status"
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
              SelectProps={{ native: true }}
            >
              <option value="all">All Status</option>
              <option value="active">Active</option>
              <option value="paused">Paused</option>
              <option value="completed">Completed</option>
              <option value="cancelled">Cancelled</option>
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              select
              label="Priority"
              value={filterPriority}
              onChange={(e) => setFilterPriority(e.target.value)}
              SelectProps={{ native: true }}
            >
              <option value="all">All Priority</option>
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
              <option value="urgent">Urgent</option>
            </TextField>
          </Grid>
          <Grid item xs={12} md={2}>
            <TextField
              fullWidth
              size="small"
              select
              label="Sort by"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              SelectProps={{ native: true }}
            >
              <option value="dueDate">Due Date</option>
              <option value="priority">Priority</option>
              <option value="created">Created Date</option>
              <option value="amount">Amount</option>
            </TextField>
          </Grid>
          <Grid item xs={12} md={2}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={loadReminders}
              disabled={loading}
            >
              Refresh
            </Button>
          </Grid>
        </Grid>
      </Paper>

      {/* Reminders List */}
      {sortedReminders.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <NotificationIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            {searchQuery || filterStatus !== 'all' || filterPriority !== 'all'
              ? 'No reminders found'
              : 'No reminders yet'
            }
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {searchQuery || filterStatus !== 'all' || filterPriority !== 'all'
              ? 'Try adjusting your search or filter criteria'
              : 'Create your first payment reminder to get started'
            }
          </Typography>
          {!searchQuery && filterStatus === 'all' && filterPriority === 'all' && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateDialog(true)}
            >
              Create Reminder
            </Button>
          )}
        </Paper>
      ) : (
        sortedReminders.map(renderReminderCard)
      )}

      {/* Floating Action Button */}
      <Zoom in={true}>
        <Fab
          color="primary"
          sx={{ position: 'fixed', bottom: 16, right: 16 }}
          onClick={() => setCreateDialog(true)}
        >
          <AddIcon />
        </Fab>
      </Zoom>

      {/* Create Reminder Dialog */}
      <Dialog open={createDialog} onClose={() => setCreateDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle>Create Payment Reminder</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Reminder Title"
                value={reminderForm.title}
                onChange={(e) => setReminderForm({ ...reminderForm, title: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Amount"
                type="number"
                value={reminderForm.amount}
                onChange={(e) => setReminderForm({ ...reminderForm, amount: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Description"
                multiline
                rows={2}
                value={reminderForm.description}
                onChange={(e) => setReminderForm({ ...reminderForm, description: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Due Date"
                type="datetime-local"
                value={reminderForm.dueDate}
                onChange={(e) => setReminderForm({ ...reminderForm, dueDate: e.target.value })}
                InputLabelProps={{ shrink: true }}
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                select
                label="Priority"
                value={reminderForm.priority}
                onChange={(e) => setReminderForm({ ...reminderForm, priority: e.target.value as any })}
                SelectProps={{ native: true }}
              >
                <option value="low">Low</option>
                <option value="medium">Medium</option>
                <option value="high">High</option>
                <option value="urgent">Urgent</option>
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreateReminder}
            disabled={!reminderForm.title || !reminderForm.amount || !reminderForm.dueDate || loading}
          >
            Create Reminder
          </Button>
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
            if (selectedReminder) {
              handleSendReminder(selectedReminder);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><SendIcon /></ListItemIcon>
          Send Now
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (selectedReminder) {
              // Populate edit form
              setReminderForm({
                ...reminderForm,
                title: selectedReminder.title,
                description: selectedReminder.description || '',
                amount: selectedReminder.amount.toString(),
                dueDate: selectedReminder.dueDate,
                priority: selectedReminder.priority,
              });
              setEditDialog(true);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><EditIcon /></ListItemIcon>
          Edit
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (selectedReminder) {
              handleToggleStatus(selectedReminder);
            }
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon>
            {selectedReminder?.status === 'active' ? <PauseIcon /> : <PlayIcon />}
          </ListItemIcon>
          {selectedReminder?.status === 'active' ? 'Pause' : 'Resume'}
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            setDeleteDialog(true);
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><DeleteIcon /></ListItemIcon>
          Delete
        </MenuItem>
      </Menu>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialog} onClose={() => setDeleteDialog(false)}>
        <DialogTitle>Delete Reminder</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete "{selectedReminder?.title}"? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDeleteReminder}
            disabled={loading}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PaymentReminders;