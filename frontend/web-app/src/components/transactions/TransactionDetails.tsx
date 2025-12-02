import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Button,
  IconButton,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  Alert,
  Paper,
  Grid,
  Tooltip,
  Menu,
  MenuItem,
  TextField,
  useTheme,
  alpha,
  Stepper,
  Step,
  StepLabel,
  StepContent,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import CopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import PrintIcon from '@mui/icons-material/Print';
import ReceiptIcon from '@mui/icons-material/Receipt';
import FlagIcon from '@mui/icons-material/Flag';
import UndoIcon from '@mui/icons-material/Undo';
import InfoIcon from '@mui/icons-material/Info';
import PersonIcon from '@mui/icons-material/Person';
import BusinessIcon from '@mui/icons-material/Business';
import LocationIcon from '@mui/icons-material/LocationOn';
import ScheduleIcon from '@mui/icons-material/Schedule';
import CategoryIcon from '@mui/icons-material/Category';
import LabelIcon from '@mui/icons-material/Label';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import CardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';
import SwapIcon from '@mui/icons-material/SwapHoriz';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DownloadIcon from '@mui/icons-material/Download';
import EmailIcon from '@mui/icons-material/Email';
import SmsIcon from '@mui/icons-material/Sms';
import QrCodeIcon from '@mui/icons-material/QrCode';;
import { format, parseISO } from 'date-fns';
import { Transaction, TransactionType, TransactionStatus } from '../../types/wallet';
import { formatCurrency, formatDate, formatTime, formatTransactionId } from '../../utils/formatters';
import { QRCodeSVG } from 'qrcode.react';

interface TransactionDetailsProps {
  transaction: Transaction | null;
  open: boolean;
  onClose: () => void;
  onPrint?: () => void;
  onDispute?: () => void;
  onRefund?: () => void;
}

const TransactionDetails: React.FC<TransactionDetailsProps> = ({
  transaction,
  open,
  onClose,
  onPrint,
  onDispute,
  onRefund,
}) => {
  const theme = useTheme();
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [showQR, setShowQR] = useState(false);
  const [showTimeline, setShowTimeline] = useState(false);
  const [disputeDialogOpen, setDisputeDialogOpen] = useState(false);
  const [disputeReason, setDisputeReason] = useState('');

  if (!transaction) return null;

  const getStatusIcon = (status: TransactionStatus) => {
    switch (status) {
      case TransactionStatus.COMPLETED:
        return <CheckCircleIcon color="success" />;
      case TransactionStatus.PENDING:
      case TransactionStatus.PROCESSING:
        return <ScheduleIcon color="warning" />;
      case TransactionStatus.FAILED:
      case TransactionStatus.CANCELLED:
        return <ErrorIcon color="error" />;
      case TransactionStatus.REVERSED:
        return <UndoIcon color="info" />;
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

  const getTransactionTypeIcon = () => {
    switch (transaction.type) {
      case TransactionType.CREDIT:
      case TransactionType.DEPOSIT:
        return <MoneyIcon />;
      case TransactionType.DEBIT:
      case TransactionType.WITHDRAWAL:
        return <MoneyIcon />;
      case TransactionType.TRANSFER:
        return <SwapIcon />;
      default:
        return <MoneyIcon />;
    }
  };

  const getPaymentMethodIcon = () => {
    switch (transaction.paymentMethodType) {
      case 'BANK_ACCOUNT':
        return <BankIcon />;
      case 'DEBIT_CARD':
      case 'CREDIT_CARD':
        return <CardIcon />;
      default:
        return <MoneyIcon />;
    }
  };

  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    // Show toast notification
  };

  const handleShare = async () => {
    const shareData = {
      title: 'Transaction Details',
      text: `Transaction ${transaction.reference}: ${formatCurrency(transaction.amount, transaction.currency)}`,
      url: `${window.location.origin}/transaction/${transaction.id}`,
    };

    if (navigator.share) {
      try {
        await navigator.share(shareData);
      } catch (error) {
        console.error('Error sharing:', error);
      }
    }
  };

  const handleExport = (format: 'pdf' | 'csv') => {
    // Handle export
  };

  const handleDispute = () => {
    if (disputeReason.trim()) {
      onDispute?.();
      setDisputeDialogOpen(false);
      setDisputeReason('');
    }
  };

  const renderHeader = () => (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Avatar
          sx={{
            bgcolor: alpha(theme.palette.primary.main, 0.1),
            color: theme.palette.primary.main,
            width: 48,
            height: 48,
          }}
        >
          {getTransactionTypeIcon()}
        </Avatar>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            {formatCurrency(transaction.amount, transaction.currency)}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
            {getStatusIcon(transaction.status)}
            <Typography variant="body2" color="text.secondary">
              {transaction.status}
            </Typography>
          </Box>
        </Box>
      </Box>
      
      <Box sx={{ display: 'flex', gap: 1 }}>
        <IconButton onClick={() => setShowQR(!showQR)}>
          <QrCodeIcon />
        </IconButton>
        <IconButton onClick={handleShare}>
          <ShareIcon />
        </IconButton>
        <IconButton onClick={(e) => setMenuAnchor(e.currentTarget)}>
          <MoreVertIcon />
        </IconButton>
        <IconButton onClick={onClose}>
          <CloseIcon />
        </IconButton>
      </Box>
    </Box>
  );

  const renderMainInfo = () => (
    <List sx={{ py: 0 }}>
      <ListItem sx={{ px: 0 }}>
        <ListItemIcon>
          <ReceiptIcon />
        </ListItemIcon>
        <ListItemText
          primary="Transaction ID"
          secondary={formatTransactionId(transaction.id)}
        />
        <ListItemSecondaryAction>
          <IconButton size="small" onClick={() => handleCopy(transaction.id, 'Transaction ID')}>
            <CopyIcon />
          </IconButton>
        </ListItemSecondaryAction>
      </ListItem>

      {transaction.reference && (
        <ListItem sx={{ px: 0 }}>
          <ListItemIcon>
            <InfoIcon />
          </ListItemIcon>
          <ListItemText
            primary="Reference"
            secondary={transaction.reference}
          />
          <ListItemSecondaryAction>
            <IconButton size="small" onClick={() => handleCopy(transaction.reference!, 'Reference')}>
              <CopyIcon />
            </IconButton>
          </ListItemSecondaryAction>
        </ListItem>
      )}

      <ListItem sx={{ px: 0 }}>
        <ListItemIcon>
          <ScheduleIcon />
        </ListItemIcon>
        <ListItemText
          primary="Date & Time"
          secondary={
            <>
              {formatDate(transaction.createdAt, 'long')}
              <br />
              {formatTime(transaction.createdAt)}
            </>
          }
        />
      </ListItem>

      {transaction.description && (
        <ListItem sx={{ px: 0 }}>
          <ListItemIcon>
            <InfoIcon />
          </ListItemIcon>
          <ListItemText
            primary="Description"
            secondary={transaction.description}
          />
        </ListItem>
      )}

      {transaction.note && (
        <ListItem sx={{ px: 0 }}>
          <ListItemIcon>
            <InfoIcon />
          </ListItemIcon>
          <ListItemText
            primary="Note"
            secondary={transaction.note}
          />
        </ListItem>
      )}
    </List>
  );

  const renderParticipants = () => (
    <Box sx={{ mt: 3 }}>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        Participants
      </Typography>
      
      <Grid container spacing={2}>
        {transaction.fromUserId && (
          <Grid item xs={12} sm={6}>
            <Paper sx={{ p: 2, bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <PersonIcon color="primary" />
                <Typography variant="subtitle2">From</Typography>
              </Box>
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                {transaction.fromUserName || 'Unknown'}
              </Typography>
              {transaction.fromUserId && (
                <Typography variant="caption" color="text.secondary">
                  ID: {formatTransactionId(transaction.fromUserId)}
                </Typography>
              )}
            </Paper>
          </Grid>
        )}

        {transaction.toUserId && (
          <Grid item xs={12} sm={6}>
            <Paper sx={{ p: 2, bgcolor: alpha(theme.palette.success.main, 0.05) }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <PersonIcon color="success" />
                <Typography variant="subtitle2">To</Typography>
              </Box>
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                {transaction.toUserName || 'Unknown'}
              </Typography>
              {transaction.toUserId && (
                <Typography variant="caption" color="text.secondary">
                  ID: {formatTransactionId(transaction.toUserId)}
                </Typography>
              )}
            </Paper>
          </Grid>
        )}

        {transaction.merchantId && (
          <Grid item xs={12}>
            <Paper sx={{ p: 2, bgcolor: alpha(theme.palette.info.main, 0.05) }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <BusinessIcon color="info" />
                <Typography variant="subtitle2">Merchant</Typography>
              </Box>
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                {transaction.merchantName || 'Unknown Merchant'}
              </Typography>
              {transaction.location && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
                  <LocationIcon sx={{ fontSize: 16 }} />
                  <Typography variant="caption" color="text.secondary">
                    {transaction.location}
                  </Typography>
                </Box>
              )}
            </Paper>
          </Grid>
        )}
      </Grid>
    </Box>
  );

  const renderPaymentDetails = () => (
    <Box sx={{ mt: 3 }}>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        Payment Details
      </Typography>
      
      <List sx={{ py: 0 }}>
        <ListItem sx={{ px: 0 }}>
          <ListItemIcon>
            <MoneyIcon />
          </ListItemIcon>
          <ListItemText
            primary="Amount"
            secondary={formatCurrency(transaction.amount, transaction.currency)}
          />
        </ListItem>

        {transaction.fee && transaction.fee > 0 && (
          <ListItem sx={{ px: 0 }}>
            <ListItemIcon>
              <MoneyIcon />
            </ListItemIcon>
            <ListItemText
              primary="Fee"
              secondary={formatCurrency(transaction.fee, transaction.currency)}
            />
          </ListItem>
        )}

        {transaction.netAmount !== transaction.amount && (
          <ListItem sx={{ px: 0 }}>
            <ListItemIcon>
              <MoneyIcon />
            </ListItemIcon>
            <ListItemText
              primary="Net Amount"
              secondary={formatCurrency(transaction.netAmount, transaction.currency)}
            />
          </ListItem>
        )}

        {transaction.exchangeRate && transaction.exchangeRate !== 1 && (
          <ListItem sx={{ px: 0 }}>
            <ListItemIcon>
              <SwapIcon />
            </ListItemIcon>
            <ListItemText
              primary="Exchange Rate"
              secondary={`1 ${transaction.originalCurrency} = ${transaction.exchangeRate} ${transaction.currency}`}
            />
          </ListItem>
        )}

        {transaction.paymentMethodId && (
          <ListItem sx={{ px: 0 }}>
            <ListItemIcon>
              {getPaymentMethodIcon()}
            </ListItemIcon>
            <ListItemText
              primary="Payment Method"
              secondary={
                <>
                  {transaction.paymentMethodType?.replace(/_/g, ' ')}
                  {transaction.paymentMethodLast4 && ` •••• ${transaction.paymentMethodLast4}`}
                </>
              }
            />
          </ListItem>
        )}
      </List>
    </Box>
  );

  const renderMetadata = () => (
    <Box sx={{ mt: 3 }}>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        Additional Information
      </Typography>
      
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
        {transaction.category && (
          <Chip
            icon={<CategoryIcon />}
            label={transaction.category}
            size="small"
            variant="outlined"
          />
        )}
        
        {transaction.tags?.map((tag) => (
          <Chip
            key={tag}
            icon={<LabelIcon />}
            label={tag}
            size="small"
            variant="outlined"
          />
        ))}
      </Box>

      {transaction.metadata && Object.keys(transaction.metadata).length > 0 && (
        <Box sx={{ mt: 2 }}>
          {Object.entries(transaction.metadata).map(([key, value]) => (
            <Box key={key} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.5 }}>
              <Typography variant="caption" color="text.secondary">
                {key.replace(/_/g, ' ').charAt(0).toUpperCase() + key.slice(1)}
              </Typography>
              <Typography variant="caption">
                {String(value)}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );

  const renderQRCode = () => (
    <Box sx={{ mt: 3, textAlign: 'center' }}>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        Transaction QR Code
      </Typography>
      <QRCodeSVG
        value={JSON.stringify({
          id: transaction.id,
          amount: transaction.amount,
          currency: transaction.currency,
          date: transaction.createdAt,
        })}
        size={200}
      />
      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
        Scan to view transaction details
      </Typography>
    </Box>
  );

  const renderTimeline = () => (
    <Box sx={{ mt: 3 }}>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        Transaction Timeline
      </Typography>
      
      <Stepper orientation="vertical">
        <Step active>
          <StepLabel>
            Created
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              {formatDate(transaction.createdAt, 'medium')} at {formatTime(transaction.createdAt)}
            </Typography>
          </StepLabel>
        </Step>
        
        {transaction.processedAt && (
          <Step active>
            <StepLabel>
              Processed
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {formatDate(transaction.processedAt, 'medium')} at {formatTime(transaction.processedAt)}
              </Typography>
            </StepLabel>
          </Step>
        )}
        
        {transaction.settledAt && (
          <Step active>
            <StepLabel>
              Settled
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {formatDate(transaction.settledAt, 'medium')} at {formatTime(transaction.settledAt)}
              </Typography>
            </StepLabel>
          </Step>
        )}
      </Stepper>
    </Box>
  );

  return (
    <>
      <Dialog
        open={open}
        onClose={onClose}
        maxWidth="md"
        fullWidth
        PaperProps={{
          sx: { minHeight: '80vh' }
        }}
      >
        <DialogTitle sx={{ p: 3 }}>
          {renderHeader()}
        </DialogTitle>
        
        <DialogContent dividers sx={{ p: 3 }}>
          {transaction.status === TransactionStatus.FAILED && (
            <Alert severity="error" sx={{ mb: 3 }}>
              This transaction failed. {transaction.metadata?.failureReason || 'Please contact support for more information.'}
            </Alert>
          )}
          
          {transaction.status === TransactionStatus.PENDING && (
            <Alert severity="warning" sx={{ mb: 3 }}>
              This transaction is still being processed. It may take a few minutes to complete.
            </Alert>
          )}
          
          {renderMainInfo()}
          <Divider sx={{ my: 3 }} />
          
          {renderParticipants()}
          
          {renderPaymentDetails()}
          
          {renderMetadata()}
          
          {showQR && renderQRCode()}
          
          {showTimeline && renderTimeline()}
        </DialogContent>
        
        <DialogActions sx={{ p: 3 }}>
          {transaction.status === TransactionStatus.COMPLETED && (
            <>
              {transaction.type === TransactionType.DEBIT && onRefund && (
                <Button startIcon={<UndoIcon />} onClick={onRefund}>
                  Request Refund
                </Button>
              )}
              <Button startIcon={<FlagIcon />} onClick={() => setDisputeDialogOpen(true)}>
                Report Issue
              </Button>
            </>
          )}
          
          <Button startIcon={<ReceiptIcon />} onClick={onPrint}>
            Receipt
          </Button>
          
          <Button variant="contained" onClick={onClose}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Action Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={() => setMenuAnchor(null)}
      >
        <MenuItem onClick={() => { handleExport('pdf'); setMenuAnchor(null); }}>
          <ListItemIcon>
            <DownloadIcon />
          </ListItemIcon>
          <ListItemText primary="Download PDF" />
        </MenuItem>
        <MenuItem onClick={() => { handleExport('csv'); setMenuAnchor(null); }}>
          <ListItemIcon>
            <DownloadIcon />
          </ListItemIcon>
          <ListItemText primary="Download CSV" />
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => { setShowTimeline(!showTimeline); setMenuAnchor(null); }}>
          <ListItemIcon>
            <ScheduleIcon />
          </ListItemIcon>
          <ListItemText primary="Show Timeline" />
        </MenuItem>
        <MenuItem onClick={() => { /* Send receipt */ setMenuAnchor(null); }}>
          <ListItemIcon>
            <EmailIcon />
          </ListItemIcon>
          <ListItemText primary="Email Receipt" />
        </MenuItem>
        <MenuItem onClick={() => { /* Send SMS */ setMenuAnchor(null); }}>
          <ListItemIcon>
            <SmsIcon />
          </ListItemIcon>
          <ListItemText primary="SMS Receipt" />
        </MenuItem>
      </Menu>

      {/* Dispute Dialog */}
      <Dialog
        open={disputeDialogOpen}
        onClose={() => setDisputeDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Report an Issue</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>
            Please describe the issue with this transaction
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={4}
            placeholder="Describe the issue..."
            value={disputeReason}
            onChange={(e) => setDisputeReason(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDisputeDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleDispute}
            disabled={!disputeReason.trim()}
          >
            Submit Report
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default TransactionDetails;