import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Button,
  Divider,
  Grid,
  Card,
  CardContent,
  Chip,
  Avatar,
  IconButton,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  ListItemIcon,
  Alert,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Tab,
  Tabs,
  Badge,
  Tooltip,
  Menu,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Skeleton,
  useTheme,
  alpha,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ShareIcon from '@mui/icons-material/Share';
import DownloadIcon from '@mui/icons-material/Download';
import ReceiptIcon from '@mui/icons-material/Receipt';
import SecurityIcon from '@mui/icons-material/Security';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ScheduleIcon from '@mui/icons-material/Schedule';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import WalletIcon from '@mui/icons-material/Wallet';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import FlagIcon from '@mui/icons-material/Flag';
import SupportIcon from '@mui/icons-material/Support';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import TimelineIcon from '@mui/icons-material/Timeline';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import MapIcon from '@mui/icons-material/Map';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CopyIcon from '@mui/icons-material/FileCopy';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import BlockIcon from '@mui/icons-material/Block';;
import { format } from 'date-fns';
import toast from 'react-hot-toast';

import { Transaction, TransactionStatus, TransactionType } from '@/types/transaction';
import { formatCurrency, formatDate, formatTimeAgo } from '@/utils/formatters';
import { transactionService } from '@/services/transactionService';
import { useAppSelector } from '@/hooks/redux';

interface TransactionDetailsEnhancedProps {
  open: boolean;
  onClose: () => void;
  transactionId: string;
  onAction?: (action: string, transactionId: string) => void;
}

interface TransactionDetails extends Transaction {
  timeline: Array<{
    id: string;
    timestamp: string;
    status: string;
    description: string;
    actor?: {
      id: string;
      name: string;
      type: 'USER' | 'SYSTEM' | 'MERCHANT';
    };
    metadata?: Record<string, any>;
  }>;
  fees: Array<{
    type: string;
    amount: number;
    currency: string;
    description: string;
  }>;
  location?: {
    country: string;
    city?: string;
    coordinates?: { lat: number; lng: number };
    ipAddress?: string;
  };
  device?: {
    type: string;
    os: string;
    browser?: string;
    fingerprint: string;
  };
  riskScore?: {
    score: number;
    level: 'LOW' | 'MEDIUM' | 'HIGH';
    factors: string[];
  };
  relatedTransactions?: Transaction[];
  attachments?: Array<{
    id: string;
    name: string;
    type: string;
    url: string;
    size: number;
  }>;
  disputes?: Array<{
    id: string;
    status: 'OPEN' | 'RESOLVED' | 'CLOSED';
    reason: string;
    createdAt: string;
    resolution?: string;
  }>;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`transaction-tabpanel-${index}`}
      aria-labelledby={`transaction-tab-${index}`}
      {...other}
    >
      {value === index && <Box>{children}</Box>}
    </div>
  );
}

const TransactionDetailsEnhanced: React.FC<TransactionDetailsEnhancedProps> = ({
  open,
  onClose,
  transactionId,
  onAction,
}) => {
  const theme = useTheme();
  const { user } = useAppSelector((state) => state.auth);
  
  const [transaction, setTransaction] = useState<TransactionDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState(0);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [expandedSection, setExpandedSection] = useState<string | false>(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (open && transactionId) {
      loadTransactionDetails();
    }
  }, [open, transactionId]);

  const loadTransactionDetails = async () => {
    setLoading(true);
    try {
      const details = await transactionService.getTransactionDetails(transactionId);
      setTransaction(details);
    } catch (error) {
      console.error('Failed to load transaction details:', error);
      toast.error('Failed to load transaction details');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadTransactionDetails();
    setRefreshing(false);
  };

  const handleCopyTransactionId = () => {
    navigator.clipboard.writeText(transaction?.id || '');
    toast.success('Transaction ID copied to clipboard');
  };

  const handleShareTransaction = () => {
    const shareData = {
      title: 'Waqiti Transaction',
      text: `Transaction: ${transaction?.description}\nAmount: ${formatCurrency(transaction?.amount || 0)}`,
      url: `${window.location.origin}/transactions/${transaction?.id}`,
    };

    if (navigator.share) {
      navigator.share(shareData);
    } else {
      navigator.clipboard.writeText(shareData.url);
      toast.success('Transaction link copied to clipboard');
    }
  };

  const handleDownloadReceipt = async () => {
    try {
      const receipt = await transactionService.downloadReceipt(transactionId);
      // Handle receipt download
      toast.success('Receipt downloaded');
    } catch (error) {
      toast.error('Failed to download receipt');
    }
  };

  const handleDispute = () => {
    onAction?.('dispute', transactionId);
  };

  const handleRefund = () => {
    onAction?.('refund', transactionId);
  };

  const handleCancel = () => {
    onAction?.('cancel', transactionId);
  };

  const getTransactionIcon = () => {
    if (!transaction) return <InfoIcon />;
    
    switch (transaction.type) {
      case TransactionType.CREDIT:
        return <TrendingUpIcon color="success" />;
      case TransactionType.DEBIT:
        return <TrendingDownIcon color="error" />;
      case TransactionType.TRANSFER:
        return <SwapIcon color="primary" />;
      default:
        return <InfoIcon />;
    }
  };

  const getStatusColor = (status: TransactionStatus) => {
    switch (status) {
      case TransactionStatus.COMPLETED:
        return 'success';
      case TransactionStatus.PENDING:
      case TransactionStatus.PROCESSING:
        return 'warning';
      case TransactionStatus.FAILED:
      case TransactionStatus.CANCELLED:
        return 'error';
      case TransactionStatus.REVERSED:
        return 'info';
      default:
        return 'default';
    }
  };

  const getRiskLevelColor = (level: string) => {
    switch (level) {
      case 'LOW':
        return 'success';
      case 'MEDIUM':
        return 'warning';
      case 'HIGH':
        return 'error';
      default:
        return 'default';
    }
  };

  const renderHeader = () => (
    <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
      <Box display="flex" alignItems="center">
        <Avatar sx={{ mr: 2, bgcolor: 'primary.main' }}>
          {getTransactionIcon()}
        </Avatar>
        <Box>
          <Typography variant="h6">
            {transaction?.description}
          </Typography>
          <Box display="flex" alignItems="center" gap={1}>
            <Chip
              label={transaction?.status}
              color={getStatusColor(transaction?.status as TransactionStatus)}
              size="small"
            />
            <Typography variant="caption" color="text.secondary">
              {transaction?.id}
            </Typography>
            <IconButton size="small" onClick={handleCopyTransactionId}>
              <CopyIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>
      </Box>
      
      <Box display="flex" gap={1}>
        <Tooltip title="Refresh">
          <IconButton onClick={handleRefresh} disabled={refreshing}>
            <RefreshIcon className={refreshing ? 'rotating' : ''} />
          </IconButton>
        </Tooltip>
        <Tooltip title="Share">
          <IconButton onClick={handleShareTransaction}>
            <ShareIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Download Receipt">
          <IconButton onClick={handleDownloadReceipt}>
            <DownloadIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="More Actions">
          <IconButton onClick={(e) => setMenuAnchor(e.currentTarget)}>
            <MoreVertIcon />
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  );

  const renderOverviewTab = () => {
    if (!transaction) return null;

    return (
      <Box>
        {/* Main Transaction Info */}
        <Grid container spacing={3} mb={3}>
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Transaction Details
                </Typography>
                
                <List>
                  <ListItem divider>
                    <ListItemText
                      primary="Amount"
                      secondary={
                        <Typography variant="h6" color="primary">
                          {transaction.type === TransactionType.CREDIT ? '+' : '-'}
                          {formatCurrency(transaction.amount)} {transaction.currency}
                        </Typography>
                      }
                    />
                  </ListItem>
                  
                  <ListItem divider>
                    <ListItemText
                      primary="Date & Time"
                      secondary={formatDate(transaction.createdAt)}
                    />
                  </ListItem>
                  
                  <ListItem divider>
                    <ListItemText
                      primary="Reference"
                      secondary={transaction.reference}
                    />
                  </ListItem>
                  
                  <ListItem divider>
                    <ListItemText
                      primary="Category"
                      secondary={
                        <Chip label={transaction.metadata?.category || 'General'} size="small" />
                      }
                    />
                  </ListItem>
                  
                  {transaction.completedAt && (
                    <ListItem>
                      <ListItemText
                        primary="Completed At"
                        secondary={formatDate(transaction.completedAt)}
                      />
                    </ListItem>
                  )}
                </List>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Parties Involved
                </Typography>
                
                <List>
                  {transaction.fromUser && (
                    <ListItem>
                      <ListItemAvatar>
                        <Avatar>
                          {transaction.fromUser.name.charAt(0)}
                        </Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={transaction.fromUser.name}
                        secondary={`From: ${transaction.fromUser.email}`}
                      />
                      <Chip label="Sender" size="small" color="error" variant="outlined" />
                    </ListItem>
                  )}
                  
                  {transaction.toUser && (
                    <ListItem>
                      <ListItemAvatar>
                        <Avatar>
                          {transaction.toUser.name.charAt(0)}
                        </Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={transaction.toUser.name}
                        secondary={`To: ${transaction.toUser.email}`}
                      />
                      <Chip label="Recipient" size="small" color="success" variant="outlined" />
                    </ListItem>
                  )}
                </List>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Fees Breakdown */}
        {transaction.fees && transaction.fees.length > 0 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Fee Breakdown
              </Typography>
              
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Fee Type</TableCell>
                      <TableCell>Description</TableCell>
                      <TableCell align="right">Amount</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {transaction.fees.map((fee, index) => (
                      <TableRow key={index}>
                        <TableCell>{fee.type}</TableCell>
                        <TableCell>{fee.description}</TableCell>
                        <TableCell align="right">
                          {formatCurrency(fee.amount)} {fee.currency}
                        </TableCell>
                      </TableRow>
                    ))}
                    <TableRow>
                      <TableCell colSpan={2}><strong>Total Fees</strong></TableCell>
                      <TableCell align="right">
                        <strong>
                          {formatCurrency(transaction.fees.reduce((sum, fee) => sum + fee.amount, 0))}
                        </strong>
                      </TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}

        {/* Related Transactions */}
        {transaction.relatedTransactions && transaction.relatedTransactions.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Related Transactions
              </Typography>
              
              <List>
                {transaction.relatedTransactions.map((relatedTx) => (
                  <ListItem key={relatedTx.id} button>
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: 'secondary.main' }}>
                        {relatedTx.type === TransactionType.CREDIT ? (
                          <TrendingUpIcon />
                        ) : (
                          <TrendingDownIcon />
                        )}
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={relatedTx.description}
                      secondary={formatDate(relatedTx.createdAt)}
                    />
                    <Typography variant="body2">
                      {formatCurrency(relatedTx.amount)}
                    </Typography>
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        )}
      </Box>
    );
  };

  const renderTimelineTab = () => {
    if (!transaction?.timeline) return null;

    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          Transaction Timeline
        </Typography>
        
        <Timeline>
          {transaction.timeline.map((event, index) => (
            <TimelineItem key={event.id}>
              <TimelineSeparator>
                <TimelineDot 
                  color={
                    event.status === 'COMPLETED' ? 'success' :
                    event.status === 'FAILED' ? 'error' :
                    event.status === 'PENDING' ? 'warning' : 'primary'
                  }
                >
                  {event.status === 'COMPLETED' && <CheckCircleIcon />}
                  {event.status === 'FAILED' && <CancelIcon />}
                  {event.status === 'PENDING' && <ScheduleIcon />}
                  {!['COMPLETED', 'FAILED', 'PENDING'].includes(event.status) && <InfoIcon />}
                </TimelineDot>
                {index < transaction.timeline.length - 1 && <TimelineConnector />}
              </TimelineSeparator>
              
              <TimelineContent>
                <Paper sx={{ p: 2, mb: 1 }}>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="subtitle2">
                      {event.description}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatTimeAgo(event.timestamp)}
                    </Typography>
                  </Box>
                  
                  {event.actor && (
                    <Box display="flex" alignItems="center" mb={1}>
                      <Avatar sx={{ width: 24, height: 24, mr: 1 }}>
                        {event.actor.type === 'USER' ? (
                          <PersonIcon fontSize="small" />
                        ) : event.actor.type === 'MERCHANT' ? (
                          <BusinessIcon fontSize="small" />
                        ) : (
                          <SecurityIcon fontSize="small" />
                        )}
                      </Avatar>
                      <Typography variant="caption">
                        {event.actor.name} ({event.actor.type})
                      </Typography>
                    </Box>
                  )}
                  
                  <Typography variant="caption" color="text.secondary">
                    {format(new Date(event.timestamp), 'PPpp')}
                  </Typography>
                  
                  {event.metadata && Object.keys(event.metadata).length > 0 && (
                    <Box mt={1}>
                      <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                          <Typography variant="caption">Additional Details</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                          <pre style={{ fontSize: '0.75rem', margin: 0 }}>
                            {JSON.stringify(event.metadata, null, 2)}
                          </pre>
                        </AccordionDetails>
                      </Accordion>
                    </Box>
                  )}
                </Paper>
              </TimelineContent>
            </TimelineItem>
          ))}
        </Timeline>
      </Box>
    );
  };

  const renderSecurityTab = () => {
    if (!transaction) return null;

    return (
      <Box>
        <Grid container spacing={3}>
          {/* Risk Assessment */}
          {transaction.riskScore && (
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                    <SecurityIcon sx={{ mr: 1 }} />
                    Risk Assessment
                  </Typography>
                  
                  <Box display="flex" alignItems="center" mb={2}>
                    <Typography variant="h4" color="primary" sx={{ mr: 2 }}>
                      {transaction.riskScore.score}/100
                    </Typography>
                    <Chip 
                      label={transaction.riskScore.level} 
                      color={getRiskLevelColor(transaction.riskScore.level)}
                    />
                  </Box>
                  
                  <Typography variant="subtitle2" gutterBottom>
                    Risk Factors:
                  </Typography>
                  <List dense>
                    {transaction.riskScore.factors.map((factor, index) => (
                      <ListItem key={index}>
                        <ListItemIcon>
                          <WarningIcon color="warning" fontSize="small" />
                        </ListItemIcon>
                        <ListItemText primary={factor} />
                      </ListItem>
                    ))}
                  </List>
                </CardContent>
              </Card>
            </Grid>
          )}

          {/* Device & Location */}
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                  <MapIcon sx={{ mr: 1 }} />
                  Device & Location
                </Typography>
                
                {transaction.device && (
                  <Box mb={2}>
                    <Typography variant="subtitle2" gutterBottom>
                      Device Information:
                    </Typography>
                    <List dense>
                      <ListItem>
                        <ListItemText
                          primary="Device Type"
                          secondary={transaction.device.type}
                        />
                      </ListItem>
                      <ListItem>
                        <ListItemText
                          primary="Operating System"
                          secondary={transaction.device.os}
                        />
                      </ListItem>
                      {transaction.device.browser && (
                        <ListItem>
                          <ListItemText
                            primary="Browser"
                            secondary={transaction.device.browser}
                          />
                        </ListItem>
                      )}
                    </List>
                  </Box>
                )}
                
                {transaction.location && (
                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Location Information:
                    </Typography>
                    <List dense>
                      <ListItem>
                        <ListItemText
                          primary="Country"
                          secondary={transaction.location.country}
                        />
                      </ListItem>
                      {transaction.location.city && (
                        <ListItem>
                          <ListItemText
                            primary="City"
                            secondary={transaction.location.city}
                          />
                        </ListItem>
                      )}
                      {transaction.location.ipAddress && (
                        <ListItem>
                          <ListItemText
                            primary="IP Address"
                            secondary={transaction.location.ipAddress}
                          />
                        </ListItem>
                      )}
                    </List>
                  </Box>
                )}
              </CardContent>
            </Card>
          </Grid>

          {/* Disputes */}
          {transaction.disputes && transaction.disputes.length > 0 && (
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom display="flex" alignItems="center">
                    <FlagIcon sx={{ mr: 1 }} />
                    Disputes
                  </Typography>
                  
                  <List>
                    {transaction.disputes.map((dispute) => (
                      <ListItem key={dispute.id}>
                        <ListItemText
                          primary={dispute.reason}
                          secondary={
                            <Box>
                              <Typography variant="body2" component="span">
                                Status: 
                                <Chip 
                                  label={dispute.status} 
                                  size="small" 
                                  color={
                                    dispute.status === 'RESOLVED' ? 'success' :
                                    dispute.status === 'OPEN' ? 'warning' : 'default'
                                  }
                                  sx={{ ml: 1 }}
                                />
                              </Typography>
                              <br />
                              <Typography variant="caption">
                                Created: {formatDate(dispute.createdAt)}
                              </Typography>
                              {dispute.resolution && (
                                <>
                                  <br />
                                  <Typography variant="body2">
                                    Resolution: {dispute.resolution}
                                  </Typography>
                                </>
                              )}
                            </Box>
                          }
                        />
                      </ListItem>
                    ))}
                  </List>
                </CardContent>
              </Card>
            </Grid>
          )}
        </Grid>
      </Box>
    );
  };

  const renderAttachmentsTab = () => {
    if (!transaction?.attachments || transaction.attachments.length === 0) {
      return (
        <Box textAlign="center" py={4}>
          <ReceiptIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary">
            No Attachments
          </Typography>
          <Typography variant="body2" color="text.secondary">
            No files have been attached to this transaction.
          </Typography>
        </Box>
      );
    }

    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          Attachments ({transaction.attachments.length})
        </Typography>
        
        <Grid container spacing={2}>
          {transaction.attachments.map((attachment) => (
            <Grid item xs={12} sm={6} md={4} key={attachment.id}>
              <Card>
                <CardContent>
                  <Box display="flex" alignItems="center" mb={1}>
                    <ReceiptIcon sx={{ mr: 1 }} />
                    <Typography variant="subtitle2" noWrap>
                      {attachment.name}
                    </Typography>
                  </Box>
                  
                  <Typography variant="caption" color="text.secondary" gutterBottom>
                    {attachment.type} • {(attachment.size / 1024).toFixed(1)} KB
                  </Typography>
                  
                  <Box display="flex" gap={1} mt={2}>
                    <Button size="small" variant="outlined" fullWidth>
                      View
                    </Button>
                    <Button size="small" variant="outlined">
                      <DownloadIcon fontSize="small" />
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  };

  if (loading) {
    return (
      <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
        <DialogContent>
          <Box py={4}>
            <Skeleton variant="rectangular" height={200} sx={{ mb: 2 }} />
            <Skeleton variant="text" height={40} sx={{ mb: 1 }} />
            <Skeleton variant="text" height={40} sx={{ mb: 1 }} />
            <Skeleton variant="text" height={40} />
          </Box>
        </DialogContent>
      </Dialog>
    );
  }

  if (!transaction) {
    return (
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogContent>
          <Alert severity="error">
            Transaction not found or failed to load.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        {renderHeader()}
      </DialogTitle>
      
      <DialogContent dividers>
        {/* Tabs */}
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
          <Tabs value={activeTab} onChange={(e, newValue) => setActiveTab(newValue)}>
            <Tab label="Overview" />
            <Tab 
              label="Timeline" 
              icon={<Badge badgeContent={transaction.timeline?.length || 0} color="primary" />}
            />
            <Tab 
              label="Security" 
              icon={transaction.riskScore?.level === 'HIGH' ? 
                <Badge badgeContent="!" color="error" /> : undefined
              }
            />
            <Tab 
              label="Attachments" 
              icon={<Badge badgeContent={transaction.attachments?.length || 0} color="primary" />}
            />
          </Tabs>
        </Box>

        {/* Tab Content */}
        <TabPanel value={activeTab} index={0}>
          {renderOverviewTab()}
        </TabPanel>
        <TabPanel value={activeTab} index={1}>
          {renderTimelineTab()}
        </TabPanel>
        <TabPanel value={activeTab} index={2}>
          {renderSecurityTab()}
        </TabPanel>
        <TabPanel value={activeTab} index={3}>
          {renderAttachmentsTab()}
        </TabPanel>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        
        {/* Conditional Action Buttons */}
        {transaction.status === TransactionStatus.PENDING && (
          <Button onClick={handleCancel} color="error">
            Cancel
          </Button>
        )}
        
        {transaction.status === TransactionStatus.COMPLETED && 
         transaction.type === TransactionType.DEBIT && (
          <Button onClick={handleDispute} color="warning">
            Dispute
          </Button>
        )}
        
        {transaction.status === TransactionStatus.COMPLETED && 
         transaction.type === TransactionType.CREDIT && (
          <Button onClick={handleRefund} color="warning">
            Request Refund
          </Button>
        )}
      </DialogActions>

      {/* Actions Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem onClick={() => {
          handleDownloadReceipt();
          setMenuAnchor(null);
        }}>
          <ReceiptIcon sx={{ mr: 1 }} /> Download Receipt
        </MenuItem>
        
        <MenuItem onClick={() => {
          handleShareTransaction();
          setMenuAnchor(null);
        }}>
          <ShareIcon sx={{ mr: 1 }} /> Share Transaction
        </MenuItem>
        
        <MenuItem onClick={() => {
          // Export to accounting software
          setMenuAnchor(null);
        }}>
          <DownloadIcon sx={{ mr: 1 }} /> Export to CSV
        </MenuItem>
        
        <Divider />
        
        <MenuItem onClick={() => {
          onAction?.('support', transactionId);
          setMenuAnchor(null);
        }}>
          <SupportIcon sx={{ mr: 1 }} /> Contact Support
        </MenuItem>
        
        {transaction.status !== TransactionStatus.FAILED && (
          <MenuItem onClick={() => {
            onAction?.('report', transactionId);
            setMenuAnchor(null);
          }}>
            <FlagIcon sx={{ mr: 1 }} /> Report Issue
          </MenuItem>
        )}
      </Menu>

      <style jsx>{`
        @keyframes rotate {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .rotating {
          animation: rotate 1s linear infinite;
        }
      `}</style>
    </Dialog>
  );
};

export default TransactionDetailsEnhanced;