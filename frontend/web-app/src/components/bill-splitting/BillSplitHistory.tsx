import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
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
  Alert,
  CircularProgress,
  Paper,
  Divider,
  Chip,
  Avatar,
  AvatarGroup,
  Tooltip,
  Menu,
  MenuItem,
  ListItemIcon,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  useTheme,
  alpha,
  LinearProgress,
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  timelineItemClasses,
  Badge,
  Tab,
  Tabs,
  FormControl,
  InputLabel,
  Select,
  OutlinedInput,
  Checkbox,
  ListSubheader,
} from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import ReceiptIcon from '@mui/icons-material/Receipt';
import GroupIcon from '@mui/icons-material/Group';
import PersonIcon from '@mui/icons-material/Person';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CheckIcon from '@mui/icons-material/CheckCircle';
import PendingIcon from '@mui/icons-material/Schedule';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import MoreIcon from '@mui/icons-material/MoreVert';
import DownloadIcon from '@mui/icons-material/Download';
import ShareIcon from '@mui/icons-material/Share';
import PrintIcon from '@mui/icons-material/Print';
import ViewIcon from '@mui/icons-material/Visibility';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import FilterIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import SearchIcon from '@mui/icons-material/Search';
import CalendarIcon from '@mui/icons-material/CalendarToday';
import DateRangeIcon from '@mui/icons-material/DateRange';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import PaymentIcon from '@mui/icons-material/Payment';
import SplitscreenIcon from '@mui/icons-material/SplitscreenIcon';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import WalletIcon from '@mui/icons-material/Wallet';
import CloseIcon from '@mui/icons-material/Close';
import RefreshIcon from '@mui/icons-material/Refresh';
import CategoryIcon from '@mui/icons-material/Category';
import TimelineIconAltIcon from '@mui/icons-material/Timeline';;
import { format, parseISO, startOfMonth, endOfMonth, subMonths, isWithinInterval } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';
import toast from 'react-hot-toast';

interface BillSplitHistoryItem {
  id: string;
  title: string;
  description?: string;
  totalAmount: number;
  finalAmount: number;
  currency: string;
  createdBy: {
    id: string;
    name: string;
    avatar?: string;
  };
  createdAt: string;
  completedAt?: string;
  updatedAt: string;
  status: 'draft' | 'pending' | 'partial' | 'completed' | 'cancelled';
  category: string;
  groupId?: string;
  groupName?: string;
  participants: Array<{
    id: string;
    name: string;
    avatar?: string;
    amountOwed: number;
    amountPaid: number;
    balance: number;
    paidAt?: string;
    paymentMethod?: string;
  }>;
  items: Array<{
    id: string;
    name: string;
    amount: number;
    quantity: number;
    sharedBy: string[];
  }>;
  splitMethod: 'equal' | 'percentage' | 'itemized' | 'custom';
  paymentHistory: Array<{
    id: string;
    participantId: string;
    participantName: string;
    amount: number;
    paidAt: string;
    method: string;
    status: 'completed' | 'pending' | 'failed';
    transactionId?: string;
  }>;
  tax: number;
  tip: number;
  discount: number;
  receiptUrl?: string;
  notes?: string;
  tags?: string[];
}

interface BillSplitHistoryProps {
  groupId?: string;
  userId?: string;
  showAnalytics?: boolean;
}

const BillSplitHistory: React.FC<BillSplitHistoryProps> = ({
  groupId,
  userId,
  showAnalytics = true,
}) => {
  const theme = useTheme();
  
  const [bills, setBills] = useState<BillSplitHistoryItem[]>([]);
  const [selectedBill, setSelectedBill] = useState<BillSplitHistoryItem | null>(null);
  const [selectedTab, setSelectedTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detailsDialog, setDetailsDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterStatus, setFilterStatus] = useState<string[]>([]);
  const [filterCategories, setFilterCategories] = useState<string[]>([]);
  const [sortBy, setSortBy] = useState<'date' | 'amount' | 'participants' | 'status'>('date');
  const [dateRange, setDateRange] = useState({
    start: startOfMonth(subMonths(new Date(), 2)),
    end: endOfMonth(new Date()),
  });
  const [showFilters, setShowFilters] = useState(false);

  // Mock data
  const mockBills: BillSplitHistoryItem[] = [
    {
      id: '1',
      title: 'Dinner at Italian Restaurant',
      description: 'Team dinner celebration',
      totalAmount: 245.80,
      finalAmount: 278.50,
      currency: 'USD',
      createdBy: {
        id: 'user1',
        name: 'Alex Thompson',
        avatar: 'https://i.pravatar.cc/150?img=1',
      },
      createdAt: new Date(Date.now() - 86400000 * 7).toISOString(),
      completedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      status: 'completed',
      category: 'dining',
      groupId: 'group1',
      groupName: 'Work Team',
      participants: [
        {
          id: 'user1',
          name: 'Alex Thompson',
          avatar: 'https://i.pravatar.cc/150?img=1',
          amountOwed: 69.63,
          amountPaid: 69.63,
          balance: 0,
          paidAt: new Date(Date.now() - 86400000 * 6).toISOString(),
          paymentMethod: 'Credit Card',
        },
        {
          id: 'user2',
          name: 'Sarah Wilson',
          avatar: 'https://i.pravatar.cc/150?img=2',
          amountOwed: 69.63,
          amountPaid: 69.63,
          balance: 0,
          paidAt: new Date(Date.now() - 86400000 * 5).toISOString(),
          paymentMethod: 'Digital Wallet',
        },
        {
          id: 'user3',
          name: 'Mike Johnson',
          avatar: 'https://i.pravatar.cc/150?img=3',
          amountOwed: 69.63,
          amountPaid: 69.63,
          balance: 0,
          paidAt: new Date(Date.now() - 86400000 * 5).toISOString(),
          paymentMethod: 'Bank Transfer',
        },
        {
          id: 'user4',
          name: 'Emma Davis',
          avatar: 'https://i.pravatar.cc/150?img=4',
          amountOwed: 69.61,
          amountPaid: 69.61,
          balance: 0,
          paidAt: new Date(Date.now() - 86400000 * 5).toISOString(),
          paymentMethod: 'Credit Card',
        },
      ],
      items: [
        {
          id: 'item1',
          name: 'Pasta Carbonara',
          amount: 18.50,
          quantity: 2,
          sharedBy: ['user1', 'user2'],
        },
        {
          id: 'item2',
          name: 'Margherita Pizza',
          amount: 16.90,
          quantity: 1,
          sharedBy: ['user3', 'user4'],
        },
        {
          id: 'item3',
          name: 'Caesar Salad',
          amount: 12.40,
          quantity: 2,
          sharedBy: ['user1', 'user2', 'user3', 'user4'],
        },
      ],
      splitMethod: 'equal',
      paymentHistory: [
        {
          id: 'pay1',
          participantId: 'user1',
          participantName: 'Alex Thompson',
          amount: 69.63,
          paidAt: new Date(Date.now() - 86400000 * 6).toISOString(),
          method: 'Credit Card',
          status: 'completed',
          transactionId: 'TXN123456',
        },
      ],
      tax: 22.70,
      tip: 40.00,
      discount: 30.00,
      tags: ['work', 'celebration', 'dinner'],
    },
    {
      id: '2',
      title: 'Monthly Apartment Utilities',
      description: 'Electricity, water, and internet bills',
      totalAmount: 340.25,
      finalAmount: 340.25,
      currency: 'USD',
      createdBy: {
        id: 'user2',
        name: 'Sarah Wilson',
        avatar: 'https://i.pravatar.cc/150?img=2',
      },
      createdAt: new Date(Date.now() - 86400000 * 15).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
      status: 'partial',
      category: 'utilities',
      groupId: 'group2',
      groupName: 'Apartment 4B',
      participants: [
        {
          id: 'user2',
          name: 'Sarah Wilson',
          avatar: 'https://i.pravatar.cc/150?img=2',
          amountOwed: 113.42,
          amountPaid: 113.42,
          balance: 0,
          paidAt: new Date(Date.now() - 86400000 * 10).toISOString(),
          paymentMethod: 'Bank Transfer',
        },
        {
          id: 'user3',
          name: 'Mike Johnson',
          avatar: 'https://i.pravatar.cc/150?img=3',
          amountOwed: 113.42,
          amountPaid: 113.42,
          balance: 0,
          paidAt: new Date(Date.now() - 86400000 * 5).toISOString(),
          paymentMethod: 'Digital Wallet',
        },
        {
          id: 'user5',
          name: 'Lisa Chen',
          avatar: 'https://i.pravatar.cc/150?img=5',
          amountOwed: 113.41,
          amountPaid: 0,
          balance: -113.41,
        },
      ],
      items: [
        {
          id: 'util1',
          name: 'Electricity Bill',
          amount: 145.60,
          quantity: 1,
          sharedBy: ['user2', 'user3', 'user5'],
        },
        {
          id: 'util2',
          name: 'Water Bill',
          amount: 78.30,
          quantity: 1,
          sharedBy: ['user2', 'user3', 'user5'],
        },
        {
          id: 'util3',
          name: 'Internet Bill',
          amount: 116.35,
          quantity: 1,
          sharedBy: ['user2', 'user3', 'user5'],
        },
      ],
      splitMethod: 'equal',
      paymentHistory: [
        {
          id: 'pay2',
          participantId: 'user2',
          participantName: 'Sarah Wilson',
          amount: 113.42,
          paidAt: new Date(Date.now() - 86400000 * 10).toISOString(),
          method: 'Bank Transfer',
          status: 'completed',
          transactionId: 'TXN789012',
        },
        {
          id: 'pay3',
          participantId: 'user3',
          participantName: 'Mike Johnson',
          amount: 113.42,
          paidAt: new Date(Date.now() - 86400000 * 5).toISOString(),
          method: 'Digital Wallet',
          status: 'completed',
          transactionId: 'TXN345678',
        },
      ],
      tax: 0,
      tip: 0,
      discount: 0,
      tags: ['utilities', 'monthly', 'apartment'],
    },
  ];

  const categories = [
    { value: 'dining', label: 'Dining', color: '#4CAF50' },
    { value: 'utilities', label: 'Utilities', color: '#FF9800' },
    { value: 'groceries', label: 'Groceries', color: '#2196F3' },
    { value: 'transport', label: 'Transport', color: '#9C27B0' },
    { value: 'entertainment', label: 'Entertainment', color: '#E91E63' },
    { value: 'other', label: 'Other', color: '#607D8B' },
  ];

  const statusConfig = {
    draft: { color: 'default', icon: InfoIcon, label: 'Draft' },
    pending: { color: 'warning', icon: PendingIcon, label: 'Pending' },
    partial: { color: 'info', icon: WarningIcon, label: 'Partial' },
    completed: { color: 'success', icon: CheckIcon, label: 'Completed' },
    cancelled: { color: 'error', icon: WarningIcon, label: 'Cancelled' },
  };

  const paymentMethodIcons = {
    'Credit Card': <CardIcon />,
    'Bank Transfer': <BankIcon />,
    'Digital Wallet': <WalletIcon />,
    'Cash': <MoneyIcon />,
  };

  useEffect(() => {
    loadBillHistory();
  }, [groupId, userId]);

  const loadBillHistory = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      let filteredBills = mockBills;
      
      if (groupId) {
        filteredBills = filteredBills.filter(bill => bill.groupId === groupId);
      }
      if (userId) {
        filteredBills = filteredBills.filter(bill => 
          bill.participants.some(p => p.id === userId) || bill.createdBy.id === userId
        );
      }
      
      setBills(filteredBills);
    } catch (error) {
      toast.error('Failed to load bill history');
    } finally {
      setLoading(false);
    }
  };

  const getFilteredBills = () => {
    return bills.filter(bill => {
      const matchesSearch = bill.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           bill.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           bill.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));
      
      const matchesStatus = filterStatus.length === 0 || filterStatus.includes(bill.status);
      const matchesCategory = filterCategories.length === 0 || filterCategories.includes(bill.category);
      
      const billDate = parseISO(bill.createdAt);
      const matchesDateRange = isWithinInterval(billDate, { start: dateRange.start, end: dateRange.end });
      
      return matchesSearch && matchesStatus && matchesCategory && matchesDateRange;
    });
  };

  const getSortedBills = (filteredBills: BillSplitHistoryItem[]) => {
    return [...filteredBills].sort((a, b) => {
      switch (sortBy) {
        case 'date':
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        case 'amount':
          return b.finalAmount - a.finalAmount;
        case 'participants':
          return b.participants.length - a.participants.length;
        case 'status':
          return a.status.localeCompare(b.status);
        default:
          return 0;
      }
    });
  };

  const calculateAnalytics = (billsList: BillSplitHistoryItem[]) => {
    const totalAmount = billsList.reduce((sum, bill) => sum + bill.finalAmount, 0);
    const completedBills = billsList.filter(bill => bill.status === 'completed');
    const completedAmount = completedBills.reduce((sum, bill) => sum + bill.finalAmount, 0);
    const averageAmount = billsList.length > 0 ? totalAmount / billsList.length : 0;
    const completionRate = billsList.length > 0 ? (completedBills.length / billsList.length) * 100 : 0;
    
    return {
      totalBills: billsList.length,
      totalAmount,
      completedBills: completedBills.length,
      completedAmount,
      averageAmount,
      completionRate,
    };
  };

  const renderBillCard = (bill: BillSplitHistoryItem) => {
    const statusConfig_ = statusConfig[bill.status];
    const category = categories.find(c => c.value === bill.category);
    const completedPayments = bill.participants.filter(p => p.amountPaid === p.amountOwed).length;
    const paymentProgress = (completedPayments / bill.participants.length) * 100;

    return (
      <Card key={bill.id} sx={{ mb: 2 }}>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box sx={{ flex: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {bill.title}
                </Typography>
                <Chip
                  size="small"
                  label={statusConfig_.label}
                  color={statusConfig_.color}
                  icon={<statusConfig_.icon />}
                />
                {category && (
                  <Chip
                    size="small"
                    label={category.label}
                    sx={{ bgcolor: category.color, color: 'white' }}
                  />
                )}
              </Box>
              {bill.description && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  {bill.description}
                </Typography>
              )}
              {bill.groupName && (
                <Typography variant="caption" color="text.secondary">
                  Group: {bill.groupName}
                </Typography>
              )}
            </Box>
            <IconButton
              onClick={(e) => {
                setSelectedBill(bill);
                setMenuAnchor(e.currentTarget);
              }}
            >
              <MoreIcon />
            </IconButton>
          </Box>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid item xs={12} sm={6}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar src={bill.createdBy.avatar}>
                  {bill.createdBy.name[0]}
                </Avatar>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    Created by {bill.createdBy.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {format(parseISO(bill.createdAt), 'MMM d, yyyy h:mm a')}
                  </Typography>
                </Box>
              </Box>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Box sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {formatCurrency(bill.finalAmount)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Total amount
                </Typography>
              </Box>
            </Grid>
          </Grid>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
            <AvatarGroup max={4} sx={{ '& .MuiAvatar-root': { width: 32, height: 32 } }}>
              {bill.participants.map(participant => (
                <Tooltip key={participant.id} title={participant.name}>
                  <Avatar src={participant.avatar}>
                    {participant.name[0]}
                  </Avatar>
                </Tooltip>
              ))}
            </AvatarGroup>
            <Typography variant="body2" color="text.secondary">
              {bill.participants.length} participant{bill.participants.length > 1 ? 's' : ''}
            </Typography>
          </Box>

          <Box sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Payment Progress
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {completedPayments} of {bill.participants.length} paid
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={paymentProgress}
              sx={{
                height: 8,
                borderRadius: 4,
                bgcolor: alpha(theme.palette.primary.main, 0.1),
                '& .MuiLinearProgress-bar': {
                  bgcolor: paymentProgress === 100 ? theme.palette.success.main : theme.palette.primary.main,
                },
              }}
            />
          </Box>

          {bill.tags && bill.tags.length > 0 && (
            <Box sx={{ display: 'flex', gap: 0.5, mb: 2, flexWrap: 'wrap' }}>
              {bill.tags.map((tag, index) => (
                <Chip
                  key={index}
                  label={tag}
                  size="small"
                  variant="outlined"
                  sx={{ height: 24, fontSize: '0.75rem' }}
                />
              ))}
            </Box>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="caption" color="text.secondary">
              {bill.completedAt 
                ? `Completed on ${format(parseISO(bill.completedAt), 'MMM d, yyyy')}`
                : `Last updated ${format(parseISO(bill.updatedAt), 'MMM d, yyyy')}`
              }
            </Typography>
            <Button
              size="small"
              startIcon={<ViewIcon />}
              onClick={() => {
                setSelectedBill(bill);
                setDetailsDialog(true);
              }}
            >
              View Details
            </Button>
          </Box>
        </CardContent>
      </Card>
    );
  };

  const renderBillDetails = () => {
    if (!selectedBill) return null;

    return (
      <Box>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          sx={{ mb: 3 }}
        >
          <Tab label="Overview" />
          <Tab label="Participants" />
          <Tab label="Items" />
          <Tab label="Payment History" />
        </Tabs>

        {/* Overview Tab */}
        {selectedTab === 0 && (
          <Box>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Paper sx={{ p: 3 }}>
                  <Typography variant="h6" gutterBottom>
                    Bill Summary
                  </Typography>
                  <List>
                    <ListItem>
                      <ListItemText
                        primary="Subtotal"
                        secondary={formatCurrency(selectedBill.totalAmount)}
                      />
                    </ListItem>
                    {selectedBill.tax > 0 && (
                      <ListItem>
                        <ListItemText
                          primary="Tax"
                          secondary={formatCurrency(selectedBill.tax)}
                        />
                      </ListItem>
                    )}
                    {selectedBill.tip > 0 && (
                      <ListItem>
                        <ListItemText
                          primary="Tip"
                          secondary={formatCurrency(selectedBill.tip)}
                        />
                      </ListItem>
                    )}
                    {selectedBill.discount > 0 && (
                      <ListItem>
                        <ListItemText
                          primary="Discount"
                          secondary={`-${formatCurrency(selectedBill.discount)}`}
                        />
                      </ListItem>
                    )}
                    <Divider />
                    <ListItem>
                      <ListItemText
                        primary={<Typography variant="h6">Total</Typography>}
                        secondary={<Typography variant="h6">{formatCurrency(selectedBill.finalAmount)}</Typography>}
                      />
                    </ListItem>
                  </List>
                </Paper>
              </Grid>
              <Grid item xs={12} md={6}>
                <Paper sx={{ p: 3 }}>
                  <Typography variant="h6" gutterBottom>
                    Details
                  </Typography>
                  <List>
                    <ListItem>
                      <ListItemText
                        primary="Split Method"
                        secondary={selectedBill.splitMethod.charAt(0).toUpperCase() + selectedBill.splitMethod.slice(1)}
                      />
                    </ListItem>
                    <ListItem>
                      <ListItemText
                        primary="Created"
                        secondary={format(parseISO(selectedBill.createdAt), 'MMMM d, yyyy h:mm a')}
                      />
                    </ListItem>
                    {selectedBill.completedAt && (
                      <ListItem>
                        <ListItemText
                          primary="Completed"
                          secondary={format(parseISO(selectedBill.completedAt), 'MMMM d, yyyy h:mm a')}
                        />
                      </ListItem>
                    )}
                    <ListItem>
                      <ListItemText
                        primary="Status"
                        secondary={
                          <Chip
                            size="small"
                            label={statusConfig[selectedBill.status].label}
                            color={statusConfig[selectedBill.status].color as any}
                            icon={React.createElement(statusConfig[selectedBill.status].icon)}
                          />
                        }
                      />
                    </ListItem>
                  </List>
                </Paper>
              </Grid>
            </Grid>
          </Box>
        )}

        {/* Participants Tab */}
        {selectedTab === 1 && (
          <Paper>
            <List>
              {selectedBill.participants.map((participant, index) => (
                <React.Fragment key={participant.id}>
                  {index > 0 && <Divider />}
                  <ListItem>
                    <ListItemAvatar>
                      <Badge
                        overlap="circular"
                        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                        badgeContent={
                          participant.balance === 0 && (
                            <CheckIcon sx={{ fontSize: 16, color: 'success.main' }} />
                          )
                        }
                      >
                        <Avatar src={participant.avatar}>
                          {participant.name[0]}
                        </Avatar>
                      </Badge>
                    </ListItemAvatar>
                    <ListItemText
                      primary={participant.name}
                      secondary={
                        <Box>
                          <Typography variant="body2">
                            Owes: {formatCurrency(participant.amountOwed)} | 
                            Paid: {formatCurrency(participant.amountPaid)}
                          </Typography>
                          {participant.paidAt && (
                            <Typography variant="caption" color="text.secondary">
                              Paid on {format(parseISO(participant.paidAt), 'MMM d, yyyy')} 
                              {participant.paymentMethod && ` via ${participant.paymentMethod}`}
                            </Typography>
                          )}
                        </Box>
                      }
                    />
                    <ListItemSecondaryAction>
                      <Typography
                        variant="h6"
                        sx={{
                          color: participant.balance === 0 ? 'success.main' : 'error.main',
                          fontWeight: 600,
                        }}
                      >
                        {participant.balance === 0 ? 'Settled' : formatCurrency(Math.abs(participant.balance))}
                      </Typography>
                    </ListItemSecondaryAction>
                  </ListItem>
                </React.Fragment>
              ))}
            </List>
          </Paper>
        )}

        {/* Items Tab */}
        {selectedTab === 2 && (
          <Paper>
            <List>
              {selectedBill.items.map((item, index) => (
                <React.Fragment key={item.id}>
                  {index > 0 && <Divider />}
                  <ListItem>
                    <ListItemText
                      primary={`${item.quantity}x ${item.name}`}
                      secondary={
                        <Box>
                          <Typography variant="body2">
                            {formatCurrency(item.amount)} each • Total: {formatCurrency(item.amount * item.quantity)}
                          </Typography>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                            <Typography variant="caption">Shared by:</Typography>
                            <AvatarGroup max={3} sx={{ '& .MuiAvatar-root': { width: 24, height: 24 } }}>
                              {item.sharedBy.map(participantId => {
                                const participant = selectedBill.participants.find(p => p.id === participantId);
                                return participant ? (
                                  <Tooltip key={participantId} title={participant.name}>
                                    <Avatar src={participant.avatar}>
                                      {participant.name[0]}
                                    </Avatar>
                                  </Tooltip>
                                ) : null;
                              })}
                            </AvatarGroup>
                          </Box>
                        </Box>
                      }
                    />
                  </ListItem>
                </React.Fragment>
              ))}
            </List>
          </Paper>
        )}

        {/* Payment History Tab */}
        {selectedTab === 3 && (
          <Box>
            {selectedBill.paymentHistory.length > 0 ? (
              <Timeline
                sx={{
                  [`& .${timelineItemClasses.root}:before`]: {
                    flex: 0,
                    padding: 0,
                  },
                }}
              >
                {selectedBill.paymentHistory.map((payment, index) => (
                  <TimelineItem key={payment.id}>
                    <TimelineSeparator>
                      <TimelineDot color={payment.status === 'completed' ? 'success' : 'warning'}>
                        {paymentMethodIcons[payment.method] || <PaymentIcon />}
                      </TimelineDot>
                      {index < selectedBill.paymentHistory.length - 1 && <TimelineConnector />}
                    </TimelineSeparator>
                    <TimelineContent>
                      <Paper sx={{ p: 2, mb: 2 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {payment.participantName}
                        </Typography>
                        <Typography variant="h6" color="primary.main">
                          {formatCurrency(payment.amount)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Paid via {payment.method} on {format(parseISO(payment.paidAt), 'MMM d, yyyy h:mm a')}
                        </Typography>
                        {payment.transactionId && (
                          <Typography variant="caption" color="text.secondary">
                            Transaction ID: {payment.transactionId}
                          </Typography>
                        )}
                      </Paper>
                    </TimelineContent>
                  </TimelineItem>
                ))}
              </Timeline>
            ) : (
              <Paper sx={{ p: 4, textAlign: 'center' }}>
                <PaymentIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                <Typography variant="h6" color="text.secondary">
                  No payment history
                </Typography>
              </Paper>
            )}
          </Box>
        )}
      </Box>
    );
  };

  if (loading && bills.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  const filteredBills = getFilteredBills();
  const sortedBills = getSortedBills(filteredBills);
  const analytics = calculateAnalytics(filteredBills);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" sx={{ fontWeight: 600 }}>
          Bill Split History
        </Typography>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={loadBillHistory}
          disabled={loading}
        >
          Refresh
        </Button>
      </Box>

      {/* Analytics Cards */}
      {showAnalytics && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Avatar sx={{ bgcolor: 'primary.main' }}>
                    <ReceiptIcon />
                  </Avatar>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      {analytics.totalBills}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Total Bills
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Avatar sx={{ bgcolor: 'success.main' }}>
                    <MoneyIcon />
                  </Avatar>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      {formatCurrency(analytics.totalAmount)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Total Amount
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Avatar sx={{ bgcolor: 'info.main' }}>
                    <CheckIcon />
                  </Avatar>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      {analytics.completedBills}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Completed
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Avatar sx={{ bgcolor: 'warning.main' }}>
                    <AnalyticsIcon />
                  </Avatar>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      {formatCurrency(analytics.averageAmount)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Average Amount
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Search and Filter Controls */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              size="small"
              placeholder="Search bills..."
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
              label="Sort by"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              SelectProps={{ native: true }}
            >
              <option value="date">Date</option>
              <option value="amount">Amount</option>
              <option value="participants">Participants</option>
              <option value="status">Status</option>
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<FilterIcon />}
              onClick={() => setShowFilters(!showFilters)}
            >
              Filters
            </Button>
          </Grid>
          <Grid item xs={12} md={4}>
            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                size="small"
              >
                Export
              </Button>
              <Button
                variant="outlined"
                startIcon={<AnalyticsIcon />}
                size="small"
              >
                Analytics
              </Button>
            </Box>
          </Grid>
        </Grid>

        {/* Advanced Filters */}
        <Accordion expanded={showFilters} onChange={() => setShowFilters(!showFilters)}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography>Advanced Filters</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <FormControl fullWidth size="small">
                  <InputLabel>Status</InputLabel>
                  <Select
                    multiple
                    value={filterStatus}
                    onChange={(e) => setFilterStatus(e.target.value as string[])}
                    input={<OutlinedInput label="Status" />}
                    renderValue={(selected) => selected.join(', ')}
                  >
                    {Object.entries(statusConfig).map(([value, config]) => (
                      <MenuItem key={value} value={value}>
                        <Checkbox checked={filterStatus.indexOf(value) > -1} />
                        <ListItemText primary={config.label} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} sm={6}>
                <FormControl fullWidth size="small">
                  <InputLabel>Categories</InputLabel>
                  <Select
                    multiple
                    value={filterCategories}
                    onChange={(e) => setFilterCategories(e.target.value as string[])}
                    input={<OutlinedInput label="Categories" />}
                    renderValue={(selected) => selected.join(', ')}
                  >
                    {categories.map((category) => (
                      <MenuItem key={category.value} value={category.value}>
                        <Checkbox checked={filterCategories.indexOf(category.value) > -1} />
                        <ListItemText primary={category.label} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </AccordionDetails>
        </Accordion>
      </Paper>

      {/* Bills List */}
      {sortedBills.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <HistoryIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            {searchQuery || filterStatus.length > 0 || filterCategories.length > 0
              ? 'No bills found'
              : 'No bill history yet'
            }
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {searchQuery || filterStatus.length > 0 || filterCategories.length > 0
              ? 'Try adjusting your search or filter criteria'
              : 'Your completed bills will appear here'
            }
          </Typography>
        </Paper>
      ) : (
        sortedBills.map(renderBillCard)
      )}

      {/* Bill Details Dialog */}
      <Dialog
        open={detailsDialog}
        onClose={() => {
          setDetailsDialog(false);
          setSelectedTab(0);
        }}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="h6">{selectedBill?.title}</Typography>
            <IconButton onClick={() => setDetailsDialog(false)}>
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          {renderBillDetails()}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsDialog(false)}>Close</Button>
          <Button variant="outlined" startIcon={<DownloadIcon />}>
            Download Receipt
          </Button>
          <Button variant="outlined" startIcon={<ShareIcon />}>
            Share
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
            setDetailsDialog(true);
            setMenuAnchor(null);
          }}
        >
          <ListItemIcon><ViewIcon /></ListItemIcon>
          View Details
        </MenuItem>
        <MenuItem onClick={() => setMenuAnchor(null)}>
          <ListItemIcon><DownloadIcon /></ListItemIcon>
          Download Receipt
        </MenuItem>
        <MenuItem onClick={() => setMenuAnchor(null)}>
          <ListItemIcon><ShareIcon /></ListItemIcon>
          Share Bill
        </MenuItem>
        <MenuItem onClick={() => setMenuAnchor(null)}>
          <ListItemIcon><PrintIcon /></ListItemIcon>
          Print
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default BillSplitHistory;