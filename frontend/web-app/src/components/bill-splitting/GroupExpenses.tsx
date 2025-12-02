import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Avatar,
  AvatarGroup,
  Chip,
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
  Switch,
  FormControlLabel,
  Alert,
  CircularProgress,
  Paper,
  Divider,
  Tooltip,
  Badge,
  Tab,
  Tabs,
  LinearProgress,
  Menu,
  MenuItem,
  ListItemIcon,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  useTheme,
  alpha,
  Fab,
  Zoom,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import GroupIcon from '@mui/icons-material/Group';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import CalendarIcon from '@mui/icons-material/CalendarToday';
import SettingsIcon from '@mui/icons-material/Settings';
import NotificationIcon from '@mui/icons-material/Notifications';
import ShareIcon from '@mui/icons-material/Share';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import ArchiveIcon from '@mui/icons-material/Archive';
import UnarchiveIcon from '@mui/icons-material/Unarchive';
import MoreIcon from '@mui/icons-material/MoreVert';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import LeaveIcon from '@mui/icons-material/ExitToApp';
import ReceiptIcon from '@mui/icons-material/Receipt';
import TimelineIcon from '@mui/icons-material/Timeline';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import CategoryIcon from '@mui/icons-material/Category';
import CheckIcon from '@mui/icons-material/CheckCircle';
import PendingIcon from '@mui/icons-material/Schedule';
import WarningIcon from '@mui/icons-material/Warning';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ViewIcon from '@mui/icons-material/Visibility';
import ExportIcon from '@mui/icons-material/GetApp';
import FilterIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';;
import { format, startOfMonth, endOfMonth, subMonths, parseISO, isAfter, isBefore } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { formatCurrency } from '../../utils/formatters';
import toast from 'react-hot-toast';

interface GroupMember {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  role: 'admin' | 'member';
  joinedAt: string;
  isActive: boolean;
  totalOwed: number;
  totalPaid: number;
  balance: number;
}

interface ExpenseItem {
  id: string;
  title: string;
  description?: string;
  amount: number;
  category: string;
  paidBy: string;
  splitBetween: string[];
  date: string;
  status: 'pending' | 'settled' | 'disputed';
  receipts?: string[];
  settlementDate?: string;
  notes?: string;
  tags?: string[];
}

interface ExpenseGroup {
  id: string;
  name: string;
  description?: string;
  category: string;
  coverImage?: string;
  createdBy: string;
  createdAt: string;
  members: GroupMember[];
  expenses: ExpenseItem[];
  totalExpenses: number;
  currency: string;
  isArchived: boolean;
  settings: {
    autoSettle: boolean;
    requireApproval: boolean;
    allowFileUploads: boolean;
    reminderFrequency: 'daily' | 'weekly' | 'monthly' | 'never';
    simplifyDebts: boolean;
  };
  inviteCode?: string;
}

interface GroupExpensesProps {
  groupId?: string;
}

const GroupExpenses: React.FC<GroupExpensesProps> = ({ groupId }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  
  const [groups, setGroups] = useState<ExpenseGroup[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<ExpenseGroup | null>(null);
  const [selectedTab, setSelectedTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [createGroupDialog, setCreateGroupDialog] = useState(false);
  const [addExpenseDialog, setAddExpenseDialog] = useState(false);
  const [inviteMemberDialog, setInviteMemberDialog] = useState(false);
  const [groupSettingsDialog, setGroupSettingsDialog] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [selectedExpense, setSelectedExpense] = useState<ExpenseItem | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterCategory, setFilterCategory] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'date' | 'amount' | 'status'>('date');
  const [dateRange, setDateRange] = useState({
    start: startOfMonth(new Date()),
    end: endOfMonth(new Date()),
  });

  // Form states
  const [groupForm, setGroupForm] = useState({
    name: '',
    description: '',
    category: 'general',
    currency: 'USD',
  });

  const [expenseForm, setExpenseForm] = useState({
    title: '',
    description: '',
    amount: '',
    category: 'general',
    paidBy: '',
    splitBetween: [] as string[],
    notes: '',
    tags: [] as string[],
  });

  const [inviteForm, setInviteForm] = useState({
    email: '',
    role: 'member' as 'admin' | 'member',
  });

  // Mock data
  const mockGroups: ExpenseGroup[] = [
    {
      id: '1',
      name: 'Apartment 4B',
      description: 'Shared apartment expenses',
      category: 'household',
      createdBy: 'user1',
      createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
      members: [
        {
          id: 'user1',
          name: 'Alex Thompson',
          email: 'alex@example.com',
          avatar: 'https://i.pravatar.cc/150?img=1',
          role: 'admin',
          joinedAt: new Date(Date.now() - 86400000 * 30).toISOString(),
          isActive: true,
          totalOwed: 425.50,
          totalPaid: 380.00,
          balance: -45.50,
        },
        {
          id: 'user2',
          name: 'Sarah Wilson',
          email: 'sarah@example.com',
          avatar: 'https://i.pravatar.cc/150?img=2',
          role: 'member',
          joinedAt: new Date(Date.now() - 86400000 * 25).toISOString(),
          isActive: true,
          totalOwed: 320.75,
          totalPaid: 400.00,
          balance: 79.25,
        },
        {
          id: 'user3',
          name: 'Mike Johnson',
          email: 'mike@example.com',
          avatar: 'https://i.pravatar.cc/150?img=3',
          role: 'member',
          joinedAt: new Date(Date.now() - 86400000 * 20).toISOString(),
          isActive: true,
          totalOwed: 280.25,
          totalPaid: 245.50,
          balance: -34.75,
        },
      ],
      expenses: [
        {
          id: 'exp1',
          title: 'Groceries - Weekly Shop',
          description: 'Whole Foods grocery run',
          amount: 156.75,
          category: 'groceries',
          paidBy: 'user1',
          splitBetween: ['user1', 'user2', 'user3'],
          date: new Date(Date.now() - 86400000 * 2).toISOString(),
          status: 'pending',
          tags: ['food', 'weekly'],
        },
        {
          id: 'exp2',
          title: 'Electricity Bill',
          description: 'Monthly electricity bill',
          amount: 89.50,
          category: 'utilities',
          paidBy: 'user2',
          splitBetween: ['user1', 'user2', 'user3'],
          date: new Date(Date.now() - 86400000 * 5).toISOString(),
          status: 'settled',
          settlementDate: new Date(Date.now() - 86400000 * 1).toISOString(),
        },
      ],
      totalExpenses: 1452.75,
      currency: 'USD',
      isArchived: false,
      settings: {
        autoSettle: false,
        requireApproval: true,
        allowFileUploads: true,
        reminderFrequency: 'weekly',
        simplifyDebts: true,
      },
      inviteCode: 'APT4B-2024',
    },
  ];

  const expenseCategories = [
    { value: 'groceries', label: 'Groceries', color: '#4CAF50' },
    { value: 'utilities', label: 'Utilities', color: '#FF9800' },
    { value: 'rent', label: 'Rent', color: '#2196F3' },
    { value: 'transportation', label: 'Transportation', color: '#9C27B0' },
    { value: 'dining', label: 'Dining Out', color: '#F44336' },
    { value: 'entertainment', label: 'Entertainment', color: '#E91E63' },
    { value: 'general', label: 'General', color: '#607D8B' },
  ];

  useEffect(() => {
    loadGroups();
    if (groupId) {
      const group = mockGroups.find(g => g.id === groupId);
      if (group) setSelectedGroup(group);
    }
  }, [groupId]);

  const loadGroups = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      setGroups(mockGroups);
      if (!selectedGroup && mockGroups.length > 0) {
        setSelectedGroup(mockGroups[0]);
      }
    } catch (error) {
      toast.error('Failed to load groups');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGroup = async () => {
    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      const newGroup: ExpenseGroup = {
        id: `group_${Date.now()}`,
        ...groupForm,
        createdBy: 'current_user',
        createdAt: new Date().toISOString(),
        members: [
          {
            id: 'current_user',
            name: 'Current User',
            email: 'user@example.com',
            role: 'admin',
            joinedAt: new Date().toISOString(),
            isActive: true,
            totalOwed: 0,
            totalPaid: 0,
            balance: 0,
          },
        ],
        expenses: [],
        totalExpenses: 0,
        isArchived: false,
        settings: {
          autoSettle: false,
          requireApproval: true,
          allowFileUploads: true,
          reminderFrequency: 'weekly',
          simplifyDebts: true,
        },
        inviteCode: `${groupForm.name.toUpperCase().substring(0, 3)}-${Math.random().toString(36).substring(2, 6).toUpperCase()}`,
      };

      setGroups([newGroup, ...groups]);
      setSelectedGroup(newGroup);
      setCreateGroupDialog(false);
      setGroupForm({ name: '', description: '', category: 'general', currency: 'USD' });
      toast.success('Group created successfully');
    } catch (error) {
      toast.error('Failed to create group');
    } finally {
      setLoading(false);
    }
  };

  const handleAddExpense = async () => {
    if (!selectedGroup) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const newExpense: ExpenseItem = {
        id: `exp_${Date.now()}`,
        ...expenseForm,
        amount: parseFloat(expenseForm.amount),
        date: new Date().toISOString(),
        status: 'pending',
      };

      const updatedGroup = {
        ...selectedGroup,
        expenses: [newExpense, ...selectedGroup.expenses],
        totalExpenses: selectedGroup.totalExpenses + newExpense.amount,
      };

      setSelectedGroup(updatedGroup);
      setGroups(groups.map(g => g.id === selectedGroup.id ? updatedGroup : g));
      setAddExpenseDialog(false);
      setExpenseForm({
        title: '',
        description: '',
        amount: '',
        category: 'general',
        paidBy: '',
        splitBetween: [],
        notes: '',
        tags: [],
      });
      toast.success('Expense added successfully');
    } catch (error) {
      toast.error('Failed to add expense');
    } finally {
      setLoading(false);
    }
  };

  const handleInviteMember = async () => {
    if (!selectedGroup) return;

    try {
      setLoading(true);
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      const newMember: GroupMember = {
        id: `member_${Date.now()}`,
        name: inviteForm.email.split('@')[0],
        email: inviteForm.email,
        role: inviteForm.role,
        joinedAt: new Date().toISOString(),
        isActive: true,
        totalOwed: 0,
        totalPaid: 0,
        balance: 0,
      };

      const updatedGroup = {
        ...selectedGroup,
        members: [...selectedGroup.members, newMember],
      };

      setSelectedGroup(updatedGroup);
      setGroups(groups.map(g => g.id === selectedGroup.id ? updatedGroup : g));
      setInviteMemberDialog(false);
      setInviteForm({ email: '', role: 'member' });
      toast.success('Member invited successfully');
    } catch (error) {
      toast.error('Failed to invite member');
    } finally {
      setLoading(false);
    }
  };

  const calculateGroupBalance = (group: ExpenseGroup) => {
    return group.members.reduce((sum, member) => sum + Math.abs(member.balance), 0);
  };

  const getFilteredExpenses = (expenses: ExpenseItem[]) => {
    return expenses.filter(expense => {
      const matchesSearch = expense.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           expense.description?.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesCategory = filterCategory === 'all' || expense.category === filterCategory;
      const expenseDate = parseISO(expense.date);
      const matchesDate = isAfter(expenseDate, dateRange.start) && isBefore(expenseDate, dateRange.end);
      
      return matchesSearch && matchesCategory && matchesDate;
    });
  };

  const getSortedExpenses = (expenses: ExpenseItem[]) => {
    return [...expenses].sort((a, b) => {
      switch (sortBy) {
        case 'date':
          return new Date(b.date).getTime() - new Date(a.date).getTime();
        case 'amount':
          return b.amount - a.amount;
        case 'status':
          return a.status.localeCompare(b.status);
        default:
          return 0;
      }
    });
  };

  const renderGroupCard = (group: ExpenseGroup) => {
    const pendingExpenses = group.expenses.filter(e => e.status === 'pending').length;
    const totalBalance = calculateGroupBalance(group);

    return (
      <Card
        key={group.id}
        sx={{
          mb: 2,
          cursor: 'pointer',
          transition: 'all 0.2s',
          border: selectedGroup?.id === group.id ? 2 : 1,
          borderColor: selectedGroup?.id === group.id ? 'primary.main' : 'divider',
          '&:hover': {
            transform: 'translateY(-2px)',
            boxShadow: theme.shadows[4],
          },
        }}
        onClick={() => setSelectedGroup(group)}
      >
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                {group.name}
              </Typography>
              {group.description && (
                <Typography variant="body2" color="text.secondary">
                  {group.description}
                </Typography>
              )}
            </Box>
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation();
                setMenuAnchor(e.currentTarget);
                setSelectedGroup(group);
              }}
            >
              <MoreIcon />
            </IconButton>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <AvatarGroup max={4}>
              {group.members.map(member => (
                <Tooltip key={member.id} title={member.name}>
                  <Avatar src={member.avatar} sx={{ width: 32, height: 32 }}>
                    {member.name[0]}
                  </Avatar>
                </Tooltip>
              ))}
            </AvatarGroup>
            <Typography variant="body2" color="text.secondary">
              {group.members.length} members
            </Typography>
          </Box>

          <Grid container spacing={2}>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">
                Total Expenses
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                {formatCurrency(group.totalExpenses)}
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="body2" color="text.secondary">
                Unsettled
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 600, color: 'warning.main' }}>
                {formatCurrency(totalBalance)}
              </Typography>
            </Grid>
          </Grid>

          {pendingExpenses > 0 && (
            <Alert severity="info" sx={{ mt: 2 }}>
              <Typography variant="body2">
                {pendingExpenses} pending expense{pendingExpenses > 1 ? 's' : ''} need attention
              </Typography>
            </Alert>
          )}
        </CardContent>
      </Card>
    );
  };

  const renderExpenseList = () => {
    if (!selectedGroup) return null;

    const filteredExpenses = getFilteredExpenses(selectedGroup.expenses);
    const sortedExpenses = getSortedExpenses(filteredExpenses);

    return (
      <Box>
        {/* Search and Filter Controls */}
        <Paper sx={{ p: 2, mb: 3 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                size="small"
                placeholder="Search expenses..."
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
                label="Category"
                value={filterCategory}
                onChange={(e) => setFilterCategory(e.target.value)}
                SelectProps={{ native: true }}
              >
                <option value="all">All Categories</option>
                {expenseCategories.map(cat => (
                  <option key={cat.value} value={cat.value}>
                    {cat.label}
                  </option>
                ))}
              </TextField>
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
                <option value="status">Status</option>
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button
                  variant="outlined"
                  startIcon={<FilterIcon />}
                  size="small"
                >
                  Filter
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<ExportIcon />}
                  size="small"
                >
                  Export
                </Button>
              </Box>
            </Grid>
          </Grid>
        </Paper>

        {/* Expense List */}
        <List>
          {sortedExpenses.map((expense, index) => {
            const paidByMember = selectedGroup.members.find(m => m.id === expense.paidBy);
            const category = expenseCategories.find(c => c.value === expense.category);
            
            return (
              <React.Fragment key={expense.id}>
                {index > 0 && <Divider />}
                <ListItem sx={{ py: 2 }}>
                  <ListItemAvatar>
                    <Avatar
                      sx={{
                        bgcolor: category?.color || theme.palette.primary.main,
                        color: 'white',
                      }}
                    >
                      <ReceiptIcon />
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {expense.title}
                        </Typography>
                        <Chip
                          label={expense.status}
                          size="small"
                          color={
                            expense.status === 'settled' ? 'success' :
                            expense.status === 'pending' ? 'warning' : 'error'
                          }
                          icon={
                            expense.status === 'settled' ? <CheckIcon /> :
                            expense.status === 'pending' ? <PendingIcon /> : <WarningIcon />
                          }
                        />
                      </Box>
                    }
                    secondary={
                      <Box>
                        {expense.description && (
                          <Typography variant="body2" sx={{ mb: 0.5 }}>
                            {expense.description}
                          </Typography>
                        )}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          <Typography variant="caption" color="text.secondary">
                            Paid by {paidByMember?.name}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {format(parseISO(expense.date), 'MMM d, yyyy')}
                          </Typography>
                          <Chip
                            label={category?.label}
                            size="small"
                            variant="outlined"
                            sx={{ height: 20 }}
                          />
                        </Box>
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Box sx={{ textAlign: 'right' }}>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {formatCurrency(expense.amount)}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Split {expense.splitBetween.length} ways
                      </Typography>
                    </Box>
                  </ListItemSecondaryAction>
                </ListItem>
              </React.Fragment>
            );
          })}
        </List>

        {sortedExpenses.length === 0 && (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <ReceiptIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h6" color="text.secondary" gutterBottom>
              No expenses found
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              {searchQuery || filterCategory !== 'all' 
                ? 'Try adjusting your search or filter criteria'
                : 'Add your first expense to get started'
              }
            </Typography>
            {!searchQuery && filterCategory === 'all' && (
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setAddExpenseDialog(true)}
              >
                Add Expense
              </Button>
            )}
          </Paper>
        )}
      </Box>
    );
  };

  const renderMembersList = () => {
    if (!selectedGroup) return null;

    return (
      <Box>
        <List>
          {selectedGroup.members.map((member) => (
            <ListItem key={member.id}>
              <ListItemAvatar>
                <Badge
                  overlap="circular"
                  anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                  badgeContent={
                    member.role === 'admin' && (
                      <Box
                        sx={{
                          width: 18,
                          height: 18,
                          borderRadius: '50%',
                          bgcolor: 'warning.main',
                          color: 'white',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 10,
                          fontWeight: 600,
                        }}
                      >
                        A
                      </Box>
                    )
                  }
                >
                  <Avatar src={member.avatar}>
                    {member.name[0]}
                  </Avatar>
                </Badge>
              </ListItemAvatar>
              <ListItemText
                primary={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="subtitle1">{member.name}</Typography>
                    {member.role === 'admin' && (
                      <Chip label="Admin" size="small" color="warning" />
                    )}
                  </Box>
                }
                secondary={
                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      {member.email}
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                      <Typography variant="caption">
                        Owes: {formatCurrency(Math.max(0, -member.balance))}
                      </Typography>
                      <Typography variant="caption">
                        Owed: {formatCurrency(Math.max(0, member.balance))}
                      </Typography>
                    </Box>
                  </Box>
                }
              />
              <ListItemSecondaryAction>
                <Typography
                  variant="h6"
                  sx={{
                    fontWeight: 600,
                    color: member.balance > 0 ? 'success.main' : 
                           member.balance < 0 ? 'error.main' : 'text.primary',
                  }}
                >
                  {member.balance > 0 ? '+' : ''}{formatCurrency(member.balance)}
                </Typography>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>

        <Button
          fullWidth
          variant="outlined"
          startIcon={<PersonAddIcon />}
          onClick={() => setInviteMemberDialog(true)}
          sx={{ mt: 2 }}
        >
          Invite Member
        </Button>
      </Box>
    );
  };

  if (loading && groups.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" sx={{ fontWeight: 600 }}>
          Group Expenses
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateGroupDialog(true)}
        >
          Create Group
        </Button>
      </Box>

      <Grid container spacing={3}>
        {/* Groups Sidebar */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, height: 'fit-content' }}>
            <Typography variant="h6" gutterBottom>
              Your Groups
            </Typography>
            {groups.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 4 }}>
                <GroupIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  No groups yet
                </Typography>
                <Button
                  variant="contained"
                  startIcon={<AddIcon />}
                  onClick={() => setCreateGroupDialog(true)}
                >
                  Create First Group
                </Button>
              </Box>
            ) : (
              groups.map(renderGroupCard)
            )}
          </Paper>
        </Grid>

        {/* Main Content */}
        <Grid item xs={12} md={8}>
          {selectedGroup ? (
            <Paper sx={{ p: 3 }}>
              {/* Group Header */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 3 }}>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 600 }}>
                    {selectedGroup.name}
                  </Typography>
                  {selectedGroup.description && (
                    <Typography variant="body1" color="text.secondary">
                      {selectedGroup.description}
                    </Typography>
                  )}
                </Box>
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button
                    variant="outlined"
                    startIcon={<SettingsIcon />}
                    onClick={() => setGroupSettingsDialog(true)}
                  >
                    Settings
                  </Button>
                  <Button
                    variant="contained"
                    startIcon={<AddIcon />}
                    onClick={() => setAddExpenseDialog(true)}
                  >
                    Add Expense
                  </Button>
                </Box>
              </Box>

              {/* Group Stats */}
              <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={4}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {formatCurrency(selectedGroup.totalExpenses)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Total Expenses
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {selectedGroup.expenses.filter(e => e.status === 'pending').length}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Pending Expenses
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {selectedGroup.members.length}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Members
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>

              {/* Tabs */}
              <Tabs
                value={selectedTab}
                onChange={(_, value) => setSelectedTab(value)}
                sx={{ mb: 3 }}
              >
                <Tab label="Expenses" />
                <Tab label="Members" />
                <Tab label="Analytics" />
              </Tabs>

              {/* Tab Content */}
              {selectedTab === 0 && renderExpenseList()}
              {selectedTab === 1 && renderMembersList()}
              {selectedTab === 2 && (
                <Box sx={{ textAlign: 'center', py: 8 }}>
                  <AnalyticsIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                  <Typography variant="h6" color="text.secondary">
                    Analytics coming soon
                  </Typography>
                </Box>
              )}
            </Paper>
          ) : (
            <Paper sx={{ p: 4, textAlign: 'center' }}>
              <GroupIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
              <Typography variant="h6" gutterBottom>
                Select a group to view expenses
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Choose a group from the sidebar or create a new one
              </Typography>
            </Paper>
          )}
        </Grid>
      </Grid>

      {/* Floating Action Button */}
      {selectedGroup && (
        <Zoom in={true}>
          <Fab
            color="primary"
            sx={{ position: 'fixed', bottom: 16, right: 16 }}
            onClick={() => setAddExpenseDialog(true)}
          >
            <AddIcon />
          </Fab>
        </Zoom>
      )}

      {/* Create Group Dialog */}
      <Dialog open={createGroupDialog} onClose={() => setCreateGroupDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create New Group</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Group Name"
            value={groupForm.name}
            onChange={(e) => setGroupForm({ ...groupForm, name: e.target.value })}
            margin="normal"
            required
          />
          <TextField
            fullWidth
            label="Description"
            value={groupForm.description}
            onChange={(e) => setGroupForm({ ...groupForm, description: e.target.value })}
            margin="normal"
            multiline
            rows={2}
          />
          <TextField
            fullWidth
            select
            label="Category"
            value={groupForm.category}
            onChange={(e) => setGroupForm({ ...groupForm, category: e.target.value })}
            margin="normal"
            SelectProps={{ native: true }}
          >
            <option value="household">Household</option>
            <option value="travel">Travel</option>
            <option value="friends">Friends</option>
            <option value="couple">Couple</option>
            <option value="general">General</option>
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateGroupDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreateGroup}
            disabled={!groupForm.name || loading}
          >
            Create Group
          </Button>
        </DialogActions>
      </Dialog>

      {/* Add Expense Dialog */}
      <Dialog open={addExpenseDialog} onClose={() => setAddExpenseDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add New Expense</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Expense Title"
            value={expenseForm.title}
            onChange={(e) => setExpenseForm({ ...expenseForm, title: e.target.value })}
            margin="normal"
            required
          />
          <TextField
            fullWidth
            label="Amount"
            type="number"
            value={expenseForm.amount}
            onChange={(e) => setExpenseForm({ ...expenseForm, amount: e.target.value })}
            margin="normal"
            required
          />
          <TextField
            fullWidth
            select
            label="Category"
            value={expenseForm.category}
            onChange={(e) => setExpenseForm({ ...expenseForm, category: e.target.value })}
            margin="normal"
            SelectProps={{ native: true }}
          >
            {expenseCategories.map(cat => (
              <option key={cat.value} value={cat.value}>
                {cat.label}
              </option>
            ))}
          </TextField>
          <TextField
            fullWidth
            select
            label="Paid By"
            value={expenseForm.paidBy}
            onChange={(e) => setExpenseForm({ ...expenseForm, paidBy: e.target.value })}
            margin="normal"
            SelectProps={{ native: true }}
          >
            <option value="">Select member...</option>
            {selectedGroup?.members.map(member => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddExpenseDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleAddExpense}
            disabled={!expenseForm.title || !expenseForm.amount || !expenseForm.paidBy || loading}
          >
            Add Expense
          </Button>
        </DialogActions>
      </Dialog>

      {/* Invite Member Dialog */}
      <Dialog open={inviteMemberDialog} onClose={() => setInviteMemberDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Invite Member</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="Email Address"
            type="email"
            value={inviteForm.email}
            onChange={(e) => setInviteForm({ ...inviteForm, email: e.target.value })}
            margin="normal"
            required
          />
          <TextField
            fullWidth
            select
            label="Role"
            value={inviteForm.role}
            onChange={(e) => setInviteForm({ ...inviteForm, role: e.target.value as any })}
            margin="normal"
            SelectProps={{ native: true }}
          >
            <option value="member">Member</option>
            <option value="admin">Admin</option>
          </TextField>
          
          {selectedGroup?.inviteCode && (
            <Alert severity="info" sx={{ mt: 2 }}>
              <Typography variant="body2">
                Share invite code: <strong>{selectedGroup.inviteCode}</strong>
              </Typography>
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInviteMemberDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleInviteMember}
            disabled={!inviteForm.email || loading}
          >
            Send Invite
          </Button>
        </DialogActions>
      </Dialog>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem onClick={() => { setGroupSettingsDialog(true); setMenuAnchor(null); }}>
          <ListItemIcon><SettingsIcon /></ListItemIcon>
          Group Settings
        </MenuItem>
        <MenuItem onClick={() => setMenuAnchor(null)}>
          <ListItemIcon><ShareIcon /></ListItemIcon>
          Share Group
        </MenuItem>
        <MenuItem onClick={() => setMenuAnchor(null)}>
          <ListItemIcon><ArchiveIcon /></ListItemIcon>
          Archive Group
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => setMenuAnchor(null)}>
          <ListItemIcon><LeaveIcon /></ListItemIcon>
          Leave Group
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default GroupExpenses;