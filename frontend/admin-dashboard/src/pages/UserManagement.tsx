import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  IconButton,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  Typography,
  Avatar,
  Menu,
  MenuItem,
  Alert,
  Snackbar,
  FormControl,
  InputLabel,
  Select,
} from '@mui/material';
import {
  Search,
  MoreVert,
  Block,
  CheckCircle,
  Edit,
  Visibility,
  PersonAdd,
  FilterList,
  Download,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { format } from 'date-fns';

interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'active' | 'suspended' | 'pending' | 'blocked';
  verified: boolean;
  registeredAt: string;
  lastActive: string;
  riskLevel: 'low' | 'medium' | 'high';
  totalTransactions: number;
  totalVolume: number;
  avatar?: string;
}

interface UserFilters {
  status?: string;
  verified?: boolean;
  riskLevel?: string;
  dateFrom?: string;
  dateTo?: string;
}

const UserManagement: React.FC = () => {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchTerm, setSearchTerm] = useState('');
  const [filters, setFilters] = useState<UserFilters>({});
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [actionMenuAnchor, setActionMenuAnchor] = useState<null | HTMLElement>(null);
  const [userDetailOpen, setUserDetailOpen] = useState(false);
  const [filterOpen, setFilterOpen] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' as 'success' | 'error' });

  const queryClient = useQueryClient();

  const { data: usersData, isLoading } = useQuery(
    ['users', page, rowsPerPage, searchTerm, filters],
    () => fetchUsers(page, rowsPerPage, searchTerm, filters),
    { keepPreviousData: true }
  );

  const updateUserMutation = useMutation(
    (data: { userId: string; updates: Partial<User> }) => updateUser(data.userId, data.updates),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['users']);
        setSnackbar({ open: true, message: 'User updated successfully', severity: 'success' });
      },
      onError: () => {
        setSnackbar({ open: true, message: 'Failed to update user', severity: 'error' });
      }
    }
  );

  const handleChangePage = (event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleSearch = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(event.target.value);
    setPage(0);
  };

  const handleActionClick = (event: React.MouseEvent<HTMLElement>, user: User) => {
    setSelectedUser(user);
    setActionMenuAnchor(event.currentTarget);
  };

  const handleActionClose = () => {
    setActionMenuAnchor(null);
    setSelectedUser(null);
  };

  const handleUserAction = async (action: string) => {
    if (!selectedUser) return;

    let updates: Partial<User> = {};
    switch (action) {
      case 'suspend':
        updates = { status: 'suspended' };
        break;
      case 'activate':
        updates = { status: 'active' };
        break;
      case 'block':
        updates = { status: 'blocked' };
        break;
      case 'verify':
        updates = { verified: true };
        break;
    }

    updateUserMutation.mutate({ userId: selectedUser.id, updates });
    handleActionClose();
  };

  const handleViewDetails = () => {
    setUserDetailOpen(true);
    handleActionClose();
  };

  const handleExportUsers = async () => {
    try {
      const response = await fetch('/api/v1/admin/users/export', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
        }
      });
      
      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `users-${format(new Date(), 'yyyy-MM-dd')}.csv`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        setSnackbar({ open: true, message: 'Export started', severity: 'success' });
      }
    } catch (error) {
      setSnackbar({ open: true, message: 'Export failed', severity: 'error' });
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'active': return 'success';
      case 'suspended': return 'warning';
      case 'blocked': return 'error';
      case 'pending': return 'info';
      default: return 'default';
    }
  };

  const getRiskLevelColor = (level: string) => {
    switch (level) {
      case 'low': return 'success';
      case 'medium': return 'warning';
      case 'high': return 'error';
      default: return 'default';
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" fontWeight="bold">
          User Management
        </Typography>
        <Box display="flex" gap={2}>
          <Button
            startIcon={<FilterList />}
            onClick={() => setFilterOpen(true)}
            variant="outlined"
          >
            Filters
          </Button>
          <Button
            startIcon={<Download />}
            onClick={handleExportUsers}
            variant="outlined"
          >
            Export
          </Button>
          <Button
            startIcon={<PersonAdd />}
            variant="contained"
          >
            Add User
          </Button>
        </Box>
      </Box>

      {/* Search */}
      <Box mb={3}>
        <TextField
          fullWidth
          placeholder="Search users by name, email, or ID..."
          value={searchTerm}
          onChange={handleSearch}
          InputProps={{
            startAdornment: <Search sx={{ mr: 1, color: 'text.secondary' }} />
          }}
        />
      </Box>

      {/* Users Table */}
      <Paper>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>User</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Verified</TableCell>
                <TableCell>Risk Level</TableCell>
                <TableCell>Transactions</TableCell>
                <TableCell>Volume</TableCell>
                <TableCell>Registered</TableCell>
                <TableCell>Last Active</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {usersData?.users?.map((user: User) => (
                <TableRow key={user.id} hover>
                  <TableCell>
                    <Box display="flex" alignItems="center" gap={2}>
                      <Avatar src={user.avatar}>
                        {user.firstName[0]}{user.lastName[0]}
                      </Avatar>
                      <Box>
                        <Typography variant="body2" fontWeight="medium">
                          {user.firstName} {user.lastName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {user.email}
                        </Typography>
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={user.status}
                      color={getStatusColor(user.status) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {user.verified ? (
                      <CheckCircle color="success" fontSize="small" />
                    ) : (
                      <Chip label="Unverified" color="warning" size="small" />
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={user.riskLevel}
                      color={getRiskLevelColor(user.riskLevel) as any}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {user.totalTransactions.toLocaleString()}
                  </TableCell>
                  <TableCell>
                    ${user.totalVolume.toLocaleString()}
                  </TableCell>
                  <TableCell>
                    {format(new Date(user.registeredAt), 'MMM dd, yyyy')}
                  </TableCell>
                  <TableCell>
                    {format(new Date(user.lastActive), 'MMM dd, yyyy')}
                  </TableCell>
                  <TableCell>
                    <IconButton
                      onClick={(e) => handleActionClick(e, user)}
                      size="small"
                    >
                      <MoreVert />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, 50]}
          component="div"
          count={usersData?.totalCount || 0}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      </Paper>

      {/* Action Menu */}
      <Menu
        anchorEl={actionMenuAnchor}
        open={Boolean(actionMenuAnchor)}
        onClose={handleActionClose}
      >
        <MenuItem onClick={handleViewDetails}>
          <Visibility sx={{ mr: 1 }} fontSize="small" />
          View Details
        </MenuItem>
        <MenuItem onClick={() => handleUserAction('verify')}>
          <CheckCircle sx={{ mr: 1 }} fontSize="small" />
          Verify User
        </MenuItem>
        {selectedUser?.status === 'active' ? (
          <MenuItem onClick={() => handleUserAction('suspend')}>
            <Block sx={{ mr: 1 }} fontSize="small" />
            Suspend
          </MenuItem>
        ) : (
          <MenuItem onClick={() => handleUserAction('activate')}>
            <CheckCircle sx={{ mr: 1 }} fontSize="small" />
            Activate
          </MenuItem>
        )}
        <MenuItem onClick={() => handleUserAction('block')}>
          <Block sx={{ mr: 1 }} fontSize="small" />
          Block User
        </MenuItem>
      </Menu>

      {/* User Detail Dialog */}
      <Dialog
        open={userDetailOpen}
        onClose={() => setUserDetailOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          User Details: {selectedUser?.firstName} {selectedUser?.lastName}
        </DialogTitle>
        <DialogContent>
          {selectedUser && (
            <Grid container spacing={3} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <Typography variant="h6" gutterBottom>
                  Basic Information
                </Typography>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Email</Typography>
                  <Typography>{selectedUser.email}</Typography>
                </Box>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Status</Typography>
                  <Chip
                    label={selectedUser.status}
                    color={getStatusColor(selectedUser.status) as any}
                    size="small"
                  />
                </Box>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Risk Level</Typography>
                  <Chip
                    label={selectedUser.riskLevel}
                    color={getRiskLevelColor(selectedUser.riskLevel) as any}
                    size="small"
                  />
                </Box>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="h6" gutterBottom>
                  Activity Summary
                </Typography>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Total Transactions</Typography>
                  <Typography>{selectedUser.totalTransactions.toLocaleString()}</Typography>
                </Box>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Total Volume</Typography>
                  <Typography>${selectedUser.totalVolume.toLocaleString()}</Typography>
                </Box>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Registered</Typography>
                  <Typography>{format(new Date(selectedUser.registeredAt), 'PPpp')}</Typography>
                </Box>
                <Box sx={{ mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">Last Active</Typography>
                  <Typography>{format(new Date(selectedUser.lastActive), 'PPpp')}</Typography>
                </Box>
              </Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUserDetailOpen(false)}>Close</Button>
          <Button variant="contained">Edit User</Button>
        </DialogActions>
      </Dialog>

      {/* Filter Dialog */}
      <Dialog
        open={filterOpen}
        onClose={() => setFilterOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Filter Users</DialogTitle>
        <DialogContent>
          <Grid container spacing={3} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Status</InputLabel>
                <Select
                  value={filters.status || ''}
                  onChange={(e) => setFilters({ ...filters, status: e.target.value })}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="active">Active</MenuItem>
                  <MenuItem value="suspended">Suspended</MenuItem>
                  <MenuItem value="blocked">Blocked</MenuItem>
                  <MenuItem value="pending">Pending</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Risk Level</InputLabel>
                <Select
                  value={filters.riskLevel || ''}
                  onChange={(e) => setFilters({ ...filters, riskLevel: e.target.value })}
                >
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="low">Low</MenuItem>
                  <MenuItem value="medium">Medium</MenuItem>
                  <MenuItem value="high">High</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFilterOpen(false)}>Cancel</Button>
          <Button
            onClick={() => {
              setPage(0);
              setFilterOpen(false);
            }}
            variant="contained"
          >
            Apply Filters
          </Button>
        </DialogActions>
      </Dialog>

      {/* Snackbar */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          severity={snackbar.severity}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

// API Functions
const fetchUsers = async (page: number, limit: number, search: string, filters: UserFilters) => {
  const params = new URLSearchParams({
    page: page.toString(),
    limit: limit.toString(),
    ...(search && { search }),
    ...Object.fromEntries(Object.entries(filters).filter(([_, v]) => v !== undefined))
  });

  const response = await fetch(`/api/v1/admin/users?${params}`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
    }
  });

  if (!response.ok) {
    throw new Error('Failed to fetch users');
  }

  return response.json();
};

const updateUser = async (userId: string, updates: Partial<User>) => {
  const response = await fetch(`/api/v1/admin/users/${userId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
    },
    body: JSON.stringify(updates)
  });

  if (!response.ok) {
    throw new Error('Failed to update user');
  }

  return response.json();
};

export default UserManagement;