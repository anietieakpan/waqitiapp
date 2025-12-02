import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Button,
  Avatar,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
  Divider,
  Alert,
  Paper,
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Menu,
  MenuItem,
  TextField,
  InputAdornment,
} from '@mui/material';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import SendIcon from '@mui/icons-material/Send';
import CallReceivedIcon from '@mui/icons-material/CallReceived';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import MoreIcon from '@mui/icons-material/More';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';;
import { format, isAfter, isBefore, addDays } from 'date-fns';

import { PaymentRequest } from '@/types/payment';
import { paymentService } from '@/services/paymentService';
import toast from 'react-hot-toast';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <div role="tabpanel" hidden={value !== index}>
    {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
  </div>
);

const PaymentRequests: React.FC = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedRequest, setSelectedRequest] = useState<PaymentRequest | null>(null);
  const [showDetails, setShowDetails] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [searchTerm, setSearchTerm] = useState('');

  // Mock data for demonstration
  const mockRequests: PaymentRequest[] = [
    {
      id: 'req_001',
      fromUser: {
        id: 'user_001',
        name: 'John Doe',
        email: 'john@example.com',
        avatar: undefined,
      },
      toUser: {
        id: 'user_002',
        name: 'Jane Smith',
        email: 'jane@example.com',
        avatar: undefined,
      },
      amount: 75.00,
      currency: 'USD',
      note: 'Lunch payment',
      status: 'pending',
      createdAt: '2024-01-15T12:00:00Z',
      expiresAt: '2024-01-22T12:00:00Z',
    },
    {
      id: 'req_002',
      fromUser: {
        id: 'user_003',
        name: 'Mike Johnson',
        email: 'mike@example.com',
        avatar: undefined,
      },
      toUser: {
        id: 'user_002',
        name: 'Jane Smith',
        email: 'jane@example.com',
        avatar: undefined,
      },
      amount: 150.00,
      currency: 'USD',
      note: 'Rent split',
      status: 'pending',
      createdAt: '2024-01-14T09:30:00Z',
      expiresAt: '2024-01-21T09:30:00Z',
    },
    {
      id: 'req_003',
      fromUser: {
        id: 'user_002',
        name: 'Jane Smith',
        email: 'jane@example.com',
        avatar: undefined,
      },
      toUser: {
        id: 'user_004',
        name: 'Sarah Wilson',
        email: 'sarah@example.com',
        avatar: undefined,
      },
      amount: 25.50,
      currency: 'USD',
      note: 'Coffee',
      status: 'paid',
      createdAt: '2024-01-13T16:45:00Z',
      paidAt: '2024-01-13T17:30:00Z',
    },
    {
      id: 'req_004',
      fromUser: {
        id: 'user_002',
        name: 'Jane Smith',
        email: 'jane@example.com',
        avatar: undefined,
      },
      toUser: {
        id: 'user_005',
        name: 'Tom Brown',
        email: 'tom@example.com',
        avatar: undefined,
      },
      amount: 200.00,
      currency: 'USD',
      note: 'Event tickets',
      status: 'declined',
      createdAt: '2024-01-12T14:15:00Z',
      expiresAt: '2024-01-19T14:15:00Z',
    },
    {
      id: 'req_005',
      fromUser: {
        id: 'user_006',
        name: 'Alex Davis',
        email: 'alex@example.com',
        avatar: undefined,
      },
      toUser: {
        id: 'user_002',
        name: 'Jane Smith',
        email: 'jane@example.com',
        avatar: undefined,
      },
      amount: 50.00,
      currency: 'USD',
      note: 'Grocery split',
      status: 'expired',
      createdAt: '2024-01-05T11:20:00Z',
      expiresAt: '2024-01-12T11:20:00Z',
    },
  ];

  const { data: requests, isLoading } = useQuery(
    ['paymentRequests', activeTab],
    () => paymentService.getPaymentRequests(activeTab === 0 ? 'received' : 'sent'),
    {
      // Use mock data for demonstration
      select: () => mockRequests,
    }
  );

  const payRequestMutation = useMutation(
    (requestId: string) => paymentService.payRequest(requestId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('paymentRequests');
        toast.success('Payment sent successfully!');
        setShowDetails(false);
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to send payment');
      },
    }
  );

  const declineRequestMutation = useMutation(
    (requestId: string) => paymentService.declineRequest(requestId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('paymentRequests');
        toast.success('Request declined');
        setShowDetails(false);
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to decline request');
      },
    }
  );

  const cancelRequestMutation = useMutation(
    (requestId: string) => paymentService.cancelRequest(requestId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('paymentRequests');
        toast.success('Request cancelled');
        setShowDetails(false);
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to cancel request');
      },
    }
  );

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'paid':
        return 'success';
      case 'pending':
        return 'warning';
      case 'declined':
      case 'expired':
        return 'error';
      default:
        return 'default';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'paid':
        return <Check />;
      case 'pending':
        return <RequestQuote />;
      case 'declined':
      case 'expired':
        return <Close />;
      default:
        return <RequestQuote />;
    }
  };

  const isExpiringSoon = (expiresAt?: string) => {
    if (!expiresAt) return false;
    const expiryDate = new Date(expiresAt);
    const threeDaysFromNow = addDays(new Date(), 3);
    return isAfter(expiryDate, new Date()) && isBefore(expiryDate, threeDaysFromNow);
  };

  const filteredRequests = requests?.filter(request => {
    const matchesSearch = searchTerm === '' || 
      request.fromUser.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.toUser.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.note?.toLowerCase().includes(searchTerm.toLowerCase());
    
    if (activeTab === 0) {
      // Received requests - requests where current user is the recipient
      return matchesSearch && request.toUser.email === 'jane@example.com';
    } else {
      // Sent requests - requests where current user is the sender
      return matchesSearch && request.fromUser.email === 'jane@example.com';
    }
  }) || [];

  const handlePayRequest = (request: PaymentRequest) => {
    payRequestMutation.mutate(request.id);
  };

  const handleDeclineRequest = (request: PaymentRequest) => {
    declineRequestMutation.mutate(request.id);
  };

  const handleCancelRequest = (request: PaymentRequest) => {
    cancelRequestMutation.mutate(request.id);
  };

  const handleShareRequest = (request: PaymentRequest) => {
    const shareUrl = `https://waqiti.com/pay/request/${request.id}`;
    if (navigator.share) {
      navigator.share({
        title: 'Payment Request',
        text: `Payment request for $${request.amount}`,
        url: shareUrl,
      });
    } else {
      navigator.clipboard.writeText(shareUrl);
      toast.success('Request link copied to clipboard!');
    }
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, request: PaymentRequest) => {
    setMenuAnchor(event.currentTarget);
    setSelectedRequest(request);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const renderRequestItem = (request: PaymentRequest) => {
    const isReceived = activeTab === 0;
    const counterparty = isReceived ? request.fromUser : request.toUser;
    const canPay = isReceived && request.status === 'pending';
    const canCancel = !isReceived && request.status === 'pending';
    const isExpiring = isExpiringSoon(request.expiresAt);

    return (
      <Card key={request.id} sx={{ mb: 2 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box display="flex" alignItems="center" flex={1}>
              <Avatar sx={{ mr: 2 }}>
                {counterparty.name.charAt(0)}
              </Avatar>
              <Box flex={1}>
                <Typography variant="subtitle2" fontWeight="medium">
                  {isReceived ? `From ${counterparty.name}` : `To ${counterparty.name}`}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {request.note || 'No note provided'}
                </Typography>
                <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                  <Chip
                    icon={getStatusIcon(request.status)}
                    label={request.status}
                    color={getStatusColor(request.status) as any}
                    size="small"
                  />
                  {isExpiring && (
                    <Chip
                      label="Expiring Soon"
                      color="warning"
                      size="small"
                      variant="outlined"
                    />
                  )}
                </Box>
              </Box>
            </Box>
            <Box textAlign="right">
              <Typography variant="h6" fontWeight="medium">
                ${request.amount.toFixed(2)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {request.currency}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {format(new Date(request.createdAt), 'MMM dd')}
              </Typography>
            </Box>
            <Box ml={2}>
              {canPay && (
                <Button
                  variant="contained"
                  color="primary"
                  size="small"
                  startIcon={<Send />}
                  onClick={() => handlePayRequest(request)}
                  disabled={payRequestMutation.isLoading}
                  sx={{ mr: 1 }}
                >
                  Pay
                </Button>
              )}
              {canPay && (
                <Button
                  variant="outlined"
                  color="error"
                  size="small"
                  onClick={() => handleDeclineRequest(request)}
                  disabled={declineRequestMutation.isLoading}
                  sx={{ mr: 1 }}
                >
                  Decline
                </Button>
              )}
              <IconButton
                onClick={(e) => handleMenuOpen(e, request)}
                size="small"
              >
                <More />
              </IconButton>
            </Box>
          </Box>
          
          {request.expiresAt && request.status === 'pending' && (
            <Box mt={2}>
              <Alert 
                severity={isExpiring ? 'warning' : 'info'} 
                sx={{ py: 0.5 }}
              >
                <Typography variant="body2">
                  Expires on {format(new Date(request.expiresAt), 'MMM dd, yyyy')}
                </Typography>
              </Alert>
            </Box>
          )}
        </CardContent>
      </Card>
    );
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        Payment Requests
      </Typography>

      {/* Search */}
      <TextField
        fullWidth
        placeholder="Search requests..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Search />
            </InputAdornment>
          ),
        }}
        sx={{ mb: 3 }}
      />

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, newValue) => setActiveTab(newValue)}
          variant="fullWidth"
        >
          <Tab
            icon={<CallReceived />}
            label="Received"
            iconPosition="start"
          />
          <Tab
            icon={<Send />}
            label="Sent"
            iconPosition="start"
          />
        </Tabs>
      </Paper>

      {/* Summary Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="subtitle2" color="text.secondary">
                Total Pending
              </Typography>
              <Typography variant="h6" color="warning.main">
                {filteredRequests.filter(r => r.status === 'pending').length}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="subtitle2" color="text.secondary">
                Total Paid
              </Typography>
              <Typography variant="h6" color="success.main">
                {filteredRequests.filter(r => r.status === 'paid').length}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="subtitle2" color="text.secondary">
                Total Amount
              </Typography>
              <Typography variant="h6">
                ${filteredRequests.reduce((sum, r) => sum + r.amount, 0).toFixed(2)}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="subtitle2" color="text.secondary">
                Expiring Soon
              </Typography>
              <Typography variant="h6" color="warning.main">
                {filteredRequests.filter(r => isExpiringSoon(r.expiresAt)).length}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Request List */}
      <TabPanel value={activeTab} index={0}>
        {filteredRequests.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <RequestQuote sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No payment requests received
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Requests from others will appear here
            </Typography>
          </Paper>
        ) : (
          <Box>
            {filteredRequests.map(renderRequestItem)}
          </Box>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        {filteredRequests.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Send sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No payment requests sent
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Requests you send will appear here
            </Typography>
          </Paper>
        ) : (
          <Box>
            {filteredRequests.map(renderRequestItem)}
          </Box>
        )}
      </TabPanel>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => {
          setShowDetails(true);
          handleMenuClose();
        }}>
          View Details
        </MenuItem>
        {selectedRequest && (
          <MenuItem onClick={() => {
            handleShareRequest(selectedRequest);
            handleMenuClose();
          }}>
            <Share sx={{ mr: 1 }} />
            Share Request
          </MenuItem>
        )}
        {selectedRequest && selectedRequest.status === 'pending' && activeTab === 1 && (
          <MenuItem 
            onClick={() => {
              handleCancelRequest(selectedRequest);
              handleMenuClose();
            }}
            sx={{ color: 'error.main' }}
          >
            <Delete sx={{ mr: 1 }} />
            Cancel Request
          </MenuItem>
        )}
      </Menu>

      {/* Details Dialog */}
      <Dialog
        open={showDetails}
        onClose={() => setShowDetails(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="h6">Request Details</Typography>
            <IconButton onClick={() => setShowDetails(false)}>
              <Close />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          {selectedRequest && (
            <Box>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Request ID
                  </Typography>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                    {selectedRequest.id}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Status
                  </Typography>
                  <Chip
                    label={selectedRequest.status}
                    color={getStatusColor(selectedRequest.status) as any}
                    size="small"
                  />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Amount
                  </Typography>
                  <Typography variant="body2" fontWeight="medium">
                    ${selectedRequest.amount.toFixed(2)} {selectedRequest.currency}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Created
                  </Typography>
                  <Typography variant="body2">
                    {format(new Date(selectedRequest.createdAt), 'MMM dd, yyyy HH:mm')}
                  </Typography>
                </Grid>
                {selectedRequest.expiresAt && (
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Expires
                    </Typography>
                    <Typography variant="body2">
                      {format(new Date(selectedRequest.expiresAt), 'MMM dd, yyyy HH:mm')}
                    </Typography>
                  </Grid>
                )}
                {selectedRequest.paidAt && (
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Paid
                    </Typography>
                    <Typography variant="body2">
                      {format(new Date(selectedRequest.paidAt), 'MMM dd, yyyy HH:mm')}
                    </Typography>
                  </Grid>
                )}
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Note
                  </Typography>
                  <Typography variant="body2">
                    {selectedRequest.note || 'No note provided'}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    From
                  </Typography>
                  <Box display="flex" alignItems="center" mt={1}>
                    <Avatar sx={{ mr: 1, width: 32, height: 32 }}>
                      {selectedRequest.fromUser.name.charAt(0)}
                    </Avatar>
                    <Box>
                      <Typography variant="body2" fontWeight="medium">
                        {selectedRequest.fromUser.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {selectedRequest.fromUser.email}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>
                <Grid item xs={12}>
                  <Typography variant="subtitle2" color="text.secondary">
                    To
                  </Typography>
                  <Box display="flex" alignItems="center" mt={1}>
                    <Avatar sx={{ mr: 1, width: 32, height: 32 }}>
                      {selectedRequest.toUser.name.charAt(0)}
                    </Avatar>
                    <Box>
                      <Typography variant="body2" fontWeight="medium">
                        {selectedRequest.toUser.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {selectedRequest.toUser.email}
                      </Typography>
                    </Box>
                  </Box>
                </Grid>
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDetails(false)}>
            Close
          </Button>
          {selectedRequest && selectedRequest.status === 'pending' && activeTab === 0 && (
            <Button
              variant="contained"
              onClick={() => handlePayRequest(selectedRequest)}
              disabled={payRequestMutation.isLoading}
              startIcon={<Send />}
            >
              Pay Now
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PaymentRequests;