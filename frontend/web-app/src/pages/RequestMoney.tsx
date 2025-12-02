import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Avatar,
  Chip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  InputAdornment,
  IconButton,
  Grid,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemButton,
  Divider,
  Paper,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tabs,
  Tab,
  Badge,
  Tooltip,
  Fade,
  Collapse,
} from '@mui/material';
import RequestIcon from '@mui/icons-material/RequestQuote';
import PersonIcon from '@mui/icons-material/Person';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CheckIcon from '@mui/icons-material/Check';
import QrCodeIcon from '@mui/icons-material/QrCode2';
import SearchIcon from '@mui/icons-material/Search';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ShareIcon from '@mui/icons-material/Share';
import CopyIcon from '@mui/icons-material/ContentCopy';
import ScheduleIcon from '@mui/icons-material/Schedule';
import CancelIcon from '@mui/icons-material/Cancel';
import InfoIcon from '@mui/icons-material/Info';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';;
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { QRCodeSVG } from 'qrcode.react';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { 
  requestMoney, 
  getMoneyRequests, 
  cancelMoneyRequest,
  searchUsers,
  generateRequestLink,
} from '../store/slices/paymentSlice';
import { formatCurrency, copyToClipboard, debounce } from '../utils/helpers';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index, ...other }) => {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`request-tabpanel-${index}`}
      aria-labelledby={`request-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
};

interface MoneyRequest {
  id: string;
  fromUserId: string;
  fromUser?: {
    username: string;
    firstName?: string;
    lastName?: string;
    avatar?: string;
  };
  amount: number;
  currency: string;
  note?: string;
  status: 'pending' | 'completed' | 'cancelled' | 'expired';
  createdAt: string;
  expiresAt: string;
  paymentLink?: string;
  qrCode?: string;
}

const RequestMoney: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { moneyRequests, searchResults, loading } = useAppSelector((state) => state.payment);

  const [tabValue, setTabValue] = useState(0);
  const [requestData, setRequestData] = useState({
    amount: '',
    note: '',
    recipient: null as any,
    expiresIn: '7', // days
  });
  const [searchQuery, setSearchQuery] = useState('');
  const [showQrDialog, setShowQrDialog] = useState(false);
  const [showShareDialog, setShowShareDialog] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState<MoneyRequest | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [successMessage, setSuccessMessage] = useState('');
  const [copiedLink, setCopiedLink] = useState(false);

  useEffect(() => {
    dispatch(getMoneyRequests());
  }, [dispatch]);

  const handleSearch = debounce((query: string) => {
    if (query.length >= 3) {
      dispatch(searchUsers(query));
    }
  }, 300);

  const validateAmount = (): boolean => {
    const newErrors: Record<string, string> = {};
    const amount = parseFloat(requestData.amount);

    if (!requestData.amount || isNaN(amount) || amount <= 0) {
      newErrors.amount = 'Please enter a valid amount';
    } else if (amount < 0.5) {
      newErrors.amount = 'Minimum amount is $0.50';
    } else if (amount > 10000) {
      newErrors.amount = 'Maximum amount is $10,000';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleCreateRequest = async () => {
    if (!validateAmount()) return;

    try {
      const result = await dispatch(
        requestMoney({
          recipientId: requestData.recipient?.id,
          amount: parseFloat(requestData.amount),
          currency: 'USD',
          note: requestData.note,
          expiresIn: parseInt(requestData.expiresIn) * 24 * 60 * 60, // Convert days to seconds
        })
      ).unwrap();

      setSuccessMessage('Money request created successfully!');
      setRequestData({ amount: '', note: '', recipient: null, expiresIn: '7' });
      setTabValue(1); // Switch to requests tab
      
      // If no specific recipient, show share options
      if (!requestData.recipient) {
        setSelectedRequest(result);
        setShowShareDialog(true);
      }
    } catch (error: any) {
      setErrors({ submit: error.message || 'Failed to create request' });
    }
  };

  const handleCancelRequest = async (requestId: string) => {
    try {
      await dispatch(cancelMoneyRequest(requestId)).unwrap();
      dispatch(getMoneyRequests());
    } catch (error) {
      console.error('Failed to cancel request:', error);
    }
  };

  const handleShareRequest = async (request: MoneyRequest) => {
    if (!request.paymentLink) {
      // Generate payment link if not available
      const link = await dispatch(generateRequestLink(request.id)).unwrap();
      request.paymentLink = link;
    }
    setSelectedRequest(request);
    setShowShareDialog(true);
  };

  const handleCopyLink = () => {
    if (selectedRequest?.paymentLink) {
      copyToClipboard(selectedRequest.paymentLink);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 3000);
    }
  };

  const getRequestStatus = (request: MoneyRequest) => {
    if (request.status === 'completed') return 'success';
    if (request.status === 'cancelled' || request.status === 'expired') return 'error';
    return 'warning';
  };

  const renderCreateRequest = () => (
    <Box>
      <Card variant="outlined" sx={{ mb: 3, bgcolor: 'primary.50' }}>
        <CardContent>
          <Typography variant="body2" color="primary.main">
            <InfoIcon sx={{ fontSize: 16, mr: 1, verticalAlign: 'middle' }} />
            You can request money from a specific person or create a payment link to share
          </Typography>
        </CardContent>
      </Card>

      <FormControl fullWidth sx={{ mb: 3 }}>
        <TextField
          fullWidth
          placeholder="Search for a user (optional)"
          value={searchQuery}
          onChange={(e) => {
            setSearchQuery(e.target.value);
            handleSearch(e.target.value);
          }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
        />
        {requestData.recipient && (
          <Chip
            avatar={
              <Avatar src={requestData.recipient.avatar}>
                {requestData.recipient.firstName?.[0] || requestData.recipient.username[0]}
              </Avatar>
            }
            label={requestData.recipient.username}
            onDelete={() => setRequestData({ ...requestData, recipient: null })}
            sx={{ mt: 1 }}
          />
        )}
      </FormControl>

      {searchResults.length > 0 && searchQuery && !requestData.recipient && (
        <Paper variant="outlined" sx={{ mb: 3, maxHeight: 200, overflow: 'auto' }}>
          <List dense>
            {searchResults.map((user) => (
              <ListItemButton
                key={user.id}
                onClick={() => {
                  setRequestData({ ...requestData, recipient: user });
                  setSearchQuery('');
                }}
              >
                <ListItemAvatar>
                  <Avatar src={user.avatar} sx={{ width: 32, height: 32 }}>
                    {user.firstName?.[0] || user.username[0]}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={user.username}
                  secondary={`${user.firstName} ${user.lastName}`}
                />
              </ListItemButton>
            ))}
          </List>
        </Paper>
      )}

      <TextField
        fullWidth
        label="Amount"
        value={requestData.amount}
        onChange={(e) => {
          const value = e.target.value.replace(/[^0-9.]/g, '');
          setRequestData({ ...requestData, amount: value });
          setErrors({ ...errors, amount: '' });
        }}
        error={!!errors.amount}
        helperText={errors.amount}
        InputProps={{
          startAdornment: <InputAdornment position="start">$</InputAdornment>,
        }}
        inputProps={{
          inputMode: 'decimal',
          pattern: '[0-9]*',
        }}
        sx={{ mb: 3 }}
      />

      <Grid container spacing={1} sx={{ mb: 3 }}>
        {[5, 10, 20, 50].map((amount) => (
          <Grid item xs={3} key={amount}>
            <Button
              fullWidth
              variant="outlined"
              size="small"
              onClick={() => setRequestData({ ...requestData, amount: amount.toString() })}
            >
              ${amount}
            </Button>
          </Grid>
        ))}
      </Grid>

      <TextField
        fullWidth
        label="What's it for? (optional)"
        value={requestData.note}
        onChange={(e) => setRequestData({ ...requestData, note: e.target.value })}
        multiline
        rows={2}
        sx={{ mb: 3 }}
      />

      <FormControl fullWidth sx={{ mb: 3 }}>
        <InputLabel>Request expires in</InputLabel>
        <Select
          value={requestData.expiresIn}
          onChange={(e) => setRequestData({ ...requestData, expiresIn: e.target.value })}
          label="Request expires in"
        >
          <MenuItem value="1">1 day</MenuItem>
          <MenuItem value="3">3 days</MenuItem>
          <MenuItem value="7">7 days</MenuItem>
          <MenuItem value="14">14 days</MenuItem>
          <MenuItem value="30">30 days</MenuItem>
        </Select>
      </FormControl>

      {errors.submit && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {errors.submit}
        </Alert>
      )}

      {successMessage && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccessMessage('')}>
          {successMessage}
        </Alert>
      )}

      <Button
        fullWidth
        variant="contained"
        size="large"
        startIcon={<RequestIcon />}
        onClick={handleCreateRequest}
        disabled={loading || !requestData.amount}
      >
        Create Request
      </Button>
    </Box>
  );

  const renderRequestsList = () => {
    const pendingRequests = moneyRequests.filter((r) => r.status === 'pending');
    const completedRequests = moneyRequests.filter((r) => r.status !== 'pending');

    return (
      <Box>
        {pendingRequests.length > 0 && (
          <>
            <Typography variant="h6" gutterBottom>
              Pending Requests
            </Typography>
            <List>
              {pendingRequests.map((request) => (
                <Card key={request.id} variant="outlined" sx={{ mb: 2 }}>
                  <CardContent>
                    <Box display="flex" alignItems="center" justifyContent="space-between">
                      <Box display="flex" alignItems="center" gap={2}>
                        {request.fromUser ? (
                          <Avatar src={request.fromUser.avatar}>
                            {request.fromUser.firstName?.[0] || request.fromUser.username[0]}
                          </Avatar>
                        ) : (
                          <Avatar>
                            <MoneyIcon />
                          </Avatar>
                        )}
                        <Box>
                          <Typography variant="h6">
                            {formatCurrency(request.amount)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {request.fromUser
                              ? `From @${request.fromUser.username}`
                              : 'Payment link'}
                          </Typography>
                          {request.note && (
                            <Typography variant="body2" color="text.secondary">
                              {request.note}
                            </Typography>
                          )}
                          <Typography variant="caption" color="text.secondary">
                            Expires {new Date(request.expiresAt).toLocaleDateString()}
                          </Typography>
                        </Box>
                      </Box>
                      <Box display="flex" gap={1}>
                        <Tooltip title="Share">
                          <IconButton onClick={() => handleShareRequest(request)}>
                            <ShareIcon />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Cancel">
                          <IconButton
                            onClick={() => handleCancelRequest(request.id)}
                            color="error"
                          >
                            <CancelIcon />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </Box>
                  </CardContent>
                </Card>
              ))}
            </List>
          </>
        )}

        {completedRequests.length > 0 && (
          <>
            <Typography variant="h6" gutterBottom sx={{ mt: 3 }}>
              History
            </Typography>
            <List>
              {completedRequests.map((request) => (
                <Card
                  key={request.id}
                  variant="outlined"
                  sx={{
                    mb: 1,
                    opacity: request.status === 'completed' ? 1 : 0.7,
                  }}
                >
                  <CardContent sx={{ py: 1.5 }}>
                    <Box display="flex" alignItems="center" justifyContent="space-between">
                      <Box display="flex" alignItems="center" gap={2}>
                        <Avatar src={request.fromUser?.avatar} sx={{ width: 32, height: 32 }}>
                          {request.fromUser?.firstName?.[0] || request.fromUser?.username[0]}
                        </Avatar>
                        <Box>
                          <Typography variant="body1">
                            {formatCurrency(request.amount)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {request.fromUser?.username || 'Payment link'} •{' '}
                            {new Date(request.createdAt).toLocaleDateString()}
                          </Typography>
                        </Box>
                      </Box>
                      <Chip
                        size="small"
                        label={request.status}
                        color={getRequestStatus(request) as any}
                        variant="outlined"
                      />
                    </Box>
                  </CardContent>
                </Card>
              ))}
            </List>
          </>
        )}

        {moneyRequests.length === 0 && (
          <Box textAlign="center" py={4}>
            <Typography color="text.secondary">No requests yet</Typography>
          </Box>
        )}
      </Box>
    );
  };

  return (
    <Box maxWidth="md" mx="auto">
      <Box display="flex" alignItems="center" gap={2} mb={4}>
        <IconButton onClick={() => navigate('/wallet')}>
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" component="h1">
          Request Money
        </Typography>
      </Box>

      <Tabs value={tabValue} onChange={(_, value) => setTabValue(value)} sx={{ mb: 3 }}>
        <Tab label="New Request" />
        <Tab
          label={
            <Badge
              badgeContent={moneyRequests.filter((r) => r.status === 'pending').length}
              color="primary"
            >
              My Requests
            </Badge>
          }
        />
      </Tabs>

      <TabPanel value={tabValue} index={0}>
        {renderCreateRequest()}
      </TabPanel>

      <TabPanel value={tabValue} index={1}>
        {renderRequestsList()}
      </TabPanel>

      <Dialog
        open={showShareDialog}
        onClose={() => setShowShareDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Share Payment Request</DialogTitle>
        <DialogContent>
          {selectedRequest && (
            <>
              <Box textAlign="center" mb={3}>
                <Typography variant="h4" gutterBottom>
                  {formatCurrency(selectedRequest.amount)}
                </Typography>
                {selectedRequest.note && (
                  <Typography variant="body2" color="text.secondary">
                    {selectedRequest.note}
                  </Typography>
                )}
              </Box>

              <Box display="flex" justifyContent="center" mb={3}>
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <QRCodeSVG
                    value={selectedRequest.paymentLink || selectedRequest.id}
                    size={200}
                    level="H"
                    includeMargin
                  />
                </Paper>
              </Box>

              <TextField
                fullWidth
                value={selectedRequest.paymentLink || 'Generating link...'}
                InputProps={{
                  readOnly: true,
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton onClick={handleCopyLink}>
                        <CopyIcon />
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
                sx={{ mb: 2 }}
              />

              <Collapse in={copiedLink}>
                <Alert severity="success" sx={{ mb: 2 }}>
                  Link copied to clipboard!
                </Alert>
              </Collapse>

              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<EmailIcon />}
                    onClick={() => {
                      window.location.href = `mailto:?subject=Payment Request&body=Please pay ${formatCurrency(
                        selectedRequest.amount
                      )} - ${selectedRequest.paymentLink}`;
                    }}
                  >
                    Email
                  </Button>
                </Grid>
                <Grid item xs={6}>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<SmsIcon />}
                    onClick={() => {
                      window.location.href = `sms:?body=Please pay ${formatCurrency(
                        selectedRequest.amount
                      )} - ${selectedRequest.paymentLink}`;
                    }}
                  >
                    SMS
                  </Button>
                </Grid>
              </Grid>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowShareDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default RequestMoney;