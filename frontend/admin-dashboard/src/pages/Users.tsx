import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
  TextField,
  IconButton,
  Chip,
  Avatar,
  AvatarGroup,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  Stack,
  Tooltip,
  Alert,
  LinearProgress,
  Tab,
  Tabs,
  Badge,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  Switch,
  FormControlLabel,
  useTheme,
} from '@mui/material';
import {
  DataGrid,
  GridColDef,
  GridToolbar,
  GridValueGetterParams,
  GridRenderCellParams,
  GridSelectionModel,
} from '@mui/x-data-grid';
import {
  People,
  PersonAdd,
  Search,
  FilterList,
  Download,
  Block,
  CheckCircle,
  Warning,
  Edit,
  Delete,
  Visibility,
  VerifiedUser,
  Cancel,
  AccountCircle,
  Security,
  PhoneIphone,
  Email,
  LocationOn,
  CalendarToday,
  AttachMoney,
  Flag,
  MoreVert,
  LockReset,
  AdminPanelSettings,
  Groups,
  PersonOff,
  TrendingUp,
  Refresh,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format, formatDistanceToNow } from 'date-fns';

import { userService } from '../services/userService';
import UserDetails from '../components/users/UserDetails';
import UserFilters from '../components/users/UserFilters';
import KYCReview from '../components/users/KYCReview';
import UserActivity from '../components/users/UserActivity';
import BulkUserActions from '../components/users/BulkUserActions';
import UserRoles from '../components/users/UserRoles';
import { useNotification } from '../hooks/useNotification';
import { User, UserStatus, KYCStatus } from '../types/user';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const Users: React.FC = () => {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const { showNotification } = useNotification();
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [selectedUsers, setSelectedUsers] = useState<GridSelectionModel>([]);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [createUserOpen, setCreateUserOpen] = useState(false);
  const [kycReviewOpen, setKycReviewOpen] = useState(false);
  const [filters, setFilters] = useState({
    search: '',
    status: 'all',
    kycStatus: 'all',
    role: 'all',
    dateFrom: null as Date | null,
    dateTo: null as Date | null,
    country: 'all',
  });

  // Fetch users
  const { data: usersData, isLoading, refetch } = useQuery({
    queryKey: ['users', filters],
    queryFn: () => userService.getUsers(filters),
  });

  // Fetch user statistics
  const { data: userStats } = useQuery({
    queryKey: ['user-stats'],
    queryFn: () => userService.getUserStatistics(),
  });

  // Suspend user mutation
  const suspendUserMutation = useMutation({
    mutationFn: ({ userId, reason }: { userId: string; reason: string }) =>
      userService.suspendUser(userId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries(['users']);
      showNotification('User suspended successfully', 'warning');
    },
  });

  // Verify KYC mutation
  const verifyKycMutation = useMutation({
    mutationFn: (userId: string) => userService.verifyKyc(userId),
    onSuccess: () => {
      queryClient.invalidateQueries(['users']);
      showNotification('KYC verified successfully', 'success');
      setKycReviewOpen(false);
    },
  });

  // Reset password mutation
  const resetPasswordMutation = useMutation({
    mutationFn: (userId: string) => userService.resetPassword(userId),
    onSuccess: () => {
      showNotification('Password reset email sent', 'info');
    },
  });

  const columns: GridColDef[] = [
    {
      field: 'avatar',
      headerName: '',
      width: 50,
      renderCell: (params: GridRenderCellParams) => (
        <Avatar
          src={params.row.avatarUrl}
          alt={params.row.name}
          sx={{ width: 32, height: 32 }}
        >
          {params.row.name?.charAt(0)}
        </Avatar>
      ),
    },
    {
      field: 'name',
      headerName: 'Name',
      width: 200,
      renderCell: (params: GridRenderCellParams) => (
        <Box>
          <Typography variant="body2" fontWeight="medium">
            {params.value}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {params.row.email}
          </Typography>
        </Box>
      ),
    },
    {
      field: 'userId',
      headerName: 'User ID',
      width: 120,
      renderCell: (params: GridRenderCellParams) => (
        <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
          {params.value}
        </Typography>
      ),
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 120,
      renderCell: (params: GridRenderCellParams) => {
        const getStatusColor = (status: UserStatus) => {
          switch (status) {
            case 'ACTIVE':
              return 'success';
            case 'SUSPENDED':
              return 'error';
            case 'PENDING':
              return 'warning';
            case 'INACTIVE':
              return 'default';
            default:
              return 'default';
          }
        };

        return (
          <Chip
            label={params.value}
            size="small"
            color={getStatusColor(params.value)}
          />
        );
      },
    },
    {
      field: 'kycStatus',
      headerName: 'KYC',
      width: 130,
      renderCell: (params: GridRenderCellParams) => {
        const getKycColor = (status: KYCStatus) => {
          switch (status) {
            case 'VERIFIED':
              return 'success';
            case 'PENDING':
              return 'warning';
            case 'REJECTED':
              return 'error';
            case 'NOT_STARTED':
              return 'default';
            default:
              return 'default';
          }
        };

        const getKycIcon = (status: KYCStatus) => {
          switch (status) {
            case 'VERIFIED':
              return <VerifiedUser fontSize="small" />;
            case 'PENDING':
              return <Warning fontSize="small" />;
            case 'REJECTED':
              return <Cancel fontSize="small" />;
            default:
              return null;
          }
        };

        return (
          <Chip
            icon={getKycIcon(params.value)}
            label={params.value}
            size="small"
            color={getKycColor(params.value)}
          />
        );
      },
    },
    {
      field: 'role',
      headerName: 'Role',
      width: 120,
      renderCell: (params: GridRenderCellParams) => (
        <Chip
          label={params.value}
          size="small"
          variant="outlined"
          icon={params.value === 'ADMIN' ? <AdminPanelSettings fontSize="small" /> : null}
        />
      ),
    },
    {
      field: 'balance',
      headerName: 'Balance',
      width: 150,
      renderCell: (params: GridRenderCellParams) => (
        <Typography variant="body2" fontWeight="medium">
          ${params.value?.toLocaleString() || 0}
        </Typography>
      ),
    },
    {
      field: 'transactions',
      headerName: 'Transactions',
      width: 120,
      valueGetter: (params: GridValueGetterParams) => params.row.transactionCount || 0,
    },
    {
      field: 'joinDate',
      headerName: 'Join Date',
      width: 150,
      valueGetter: (params: GridValueGetterParams) =>
        format(new Date(params.value), 'MMM dd, yyyy'),
    },
    {
      field: 'lastActive',
      headerName: 'Last Active',
      width: 150,
      renderCell: (params: GridRenderCellParams) => (
        <Typography variant="body2" color="text.secondary">
          {params.value
            ? formatDistanceToNow(new Date(params.value), { addSuffix: true })
            : 'Never'
          }
        </Typography>
      ),
    },
    {
      field: 'country',
      headerName: 'Country',
      width: 120,
      renderCell: (params: GridRenderCellParams) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <LocationOn fontSize="small" sx={{ mr: 0.5 }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'flags',
      headerName: 'Flags',
      width: 80,
      renderCell: (params: GridRenderCellParams) => {
        const flags = params.value || [];
        if (flags.length === 0) return null;
        
        return (
          <Badge badgeContent={flags.length} color="error">
            <Flag color="error" fontSize="small" />
          </Badge>
        );
      },
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 150,
      sortable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Stack direction="row" spacing={1}>
          <Tooltip title="View Details">
            <IconButton
              size="small"
              onClick={() => handleViewDetails(params.row)}
            >
              <Visibility fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Edit User">
            <IconButton
              size="small"
              onClick={() => handleEditUser(params.row)}
            >
              <Edit fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="More Actions">
            <IconButton
              size="small"
              onClick={(e) => handleMoreActions(e, params.row)}
            >
              <MoreVert fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      ),
    },
  ];

  const handleViewDetails = (user: User) => {
    setSelectedUser(user);
    setDetailsOpen(true);
  };

  const handleEditUser = (user: User) => {
    // Open edit dialog
  };

  const handleMoreActions = (event: React.MouseEvent<HTMLElement>, user: User) => {
    // Open action menu
  };

  const pendingKyc = usersData?.users.filter(u => u.kycStatus === 'PENDING').length || 0;
  const suspendedUsers = usersData?.users.filter(u => u.status === 'SUSPENDED').length || 0;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4" fontWeight="bold">
          User Management
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => refetch()}
          >
            Refresh
          </Button>
          <Button
            variant="outlined"
            startIcon={<Download />}
          >
            Export
          </Button>
          <Button
            variant="contained"
            startIcon={<PersonAdd />}
            onClick={() => setCreateUserOpen(true)}
          >
            Create User
          </Button>
        </Stack>
      </Box>

      {/* User Statistics */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <People sx={{ fontSize: 40, color: theme.palette.primary.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Total Users
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {userStats?.totalUsers || 0}
                  </Typography>
                </Box>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center' }}>
                <TrendingUp color="success" fontSize="small" />
                <Typography variant="body2" color="success.main" sx={{ ml: 0.5 }}>
                  +{userStats?.userGrowth || 0}% this month
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Groups sx={{ fontSize: 40, color: theme.palette.success.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Active Users
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {userStats?.activeUsers || 0}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {((userStats?.activeUsers / userStats?.totalUsers) * 100).toFixed(1)}% of total
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: pendingKyc > 0 ? alpha(theme.palette.warning.main, 0.1) : 'background.paper' }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <VerifiedUser sx={{ fontSize: 40, color: theme.palette.warning.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Pending KYC
                  </Typography>
                  <Typography variant="h4" fontWeight="bold" color="warning.main">
                    {pendingKyc}
                  </Typography>
                </Box>
              </Box>
              <Button size="small" color="warning">
                Review Now
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ bgcolor: suspendedUsers > 0 ? alpha(theme.palette.error.main, 0.1) : 'background.paper' }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <PersonOff sx={{ fontSize: 40, color: theme.palette.error.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Suspended
                  </Typography>
                  <Typography variant="h4" fontWeight="bold" color="error">
                    {suspendedUsers}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Requires review
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          indicatorColor="primary"
          textColor="primary"
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab 
            label="All Users" 
            icon={<Badge badgeContent={userStats?.totalUsers || 0} color="primary" max={9999} />}
          />
          <Tab 
            label="KYC Pending" 
            icon={<Badge badgeContent={pendingKyc} color="warning" />}
          />
          <Tab 
            label="Suspended" 
            icon={<Badge badgeContent={suspendedUsers} color="error" />}
          />
          <Tab label="User Roles" icon={<AdminPanelSettings />} />
          <Tab label="Activity Monitor" icon={<Timeline />} />
        </Tabs>
      </Paper>

      {/* Tab Panels */}
      <TabPanel value={selectedTab} index={0}>
        <Paper sx={{ p: 3 }}>
          {/* Filters */}
          <UserFilters filters={filters} onFiltersChange={setFilters} />
          
          {/* Data Grid */}
          <Box sx={{ height: 600, width: '100%', mt: 3 }}>
            {isLoading && <LinearProgress />}
            <DataGrid
              rows={usersData?.users || []}
              columns={columns}
              pageSize={10}
              rowsPerPageOptions={[10, 25, 50, 100]}
              checkboxSelection
              disableSelectionOnClick
              selectionModel={selectedUsers}
              onSelectionModelChange={setSelectedUsers}
              components={{
                Toolbar: GridToolbar,
              }}
              loading={isLoading}
            />
          </Box>

          {/* Bulk Actions */}
          {selectedUsers.length > 0 && (
            <BulkUserActions
              selectedIds={selectedUsers as string[]}
              onComplete={() => {
                setSelectedUsers([]);
                refetch();
              }}
            />
          )}
        </Paper>
      </TabPanel>

      <TabPanel value={selectedTab} index={1}>
        <KYCReview users={usersData?.users.filter(u => u.kycStatus === 'PENDING') || []} />
      </TabPanel>

      <TabPanel value={selectedTab} index={2}>
        <Paper sx={{ p: 3 }}>
          <Alert severity="warning" sx={{ mb: 2 }}>
            These users have been suspended due to policy violations or security concerns.
          </Alert>
          <Box sx={{ height: 600, width: '100%' }}>
            <DataGrid
              rows={usersData?.users.filter(u => u.status === 'SUSPENDED') || []}
              columns={columns}
              pageSize={10}
              rowsPerPageOptions={[10, 25, 50]}
              disableSelectionOnClick
            />
          </Box>
        </Paper>
      </TabPanel>

      <TabPanel value={selectedTab} index={3}>
        <UserRoles />
      </TabPanel>

      <TabPanel value={selectedTab} index={4}>
        <UserActivity />
      </TabPanel>

      {/* User Details Dialog */}
      {selectedUser && (
        <UserDetails
          user={selectedUser}
          open={detailsOpen}
          onClose={() => {
            setDetailsOpen(false);
            setSelectedUser(null);
          }}
          onUpdate={() => refetch()}
        />
      )}

      {/* Create User Dialog */}
      <Dialog
        open={createUserOpen}
        onClose={() => setCreateUserOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Create New User</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="First Name"
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Last Name"
                required
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Email"
                type="email"
                required
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Phone Number"
                type="tel"
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Role</InputLabel>
                <Select value="" label="Role">
                  <MenuItem value="USER">User</MenuItem>
                  <MenuItem value="MERCHANT">Merchant</MenuItem>
                  <MenuItem value="ADMIN">Admin</MenuItem>
                  <MenuItem value="SUPPORT">Support</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth>
                <InputLabel>Country</InputLabel>
                <Select value="" label="Country">
                  <MenuItem value="US">United States</MenuItem>
                  <MenuItem value="UK">United Kingdom</MenuItem>
                  <MenuItem value="CA">Canada</MenuItem>
                  <MenuItem value="AU">Australia</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={<Switch />}
                label="Send welcome email"
              />
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={<Switch />}
                label="Require KYC verification"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateUserOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => {}}>
            Create User
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Add missing imports
import { alpha } from '@mui/material/styles';
import { Timeline } from '@mui/icons-material';

export default Users;