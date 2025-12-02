import React, { memo } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Divider,
  Chip,
  Grid,
  IconButton,
  Paper,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import CopyIcon from '@mui/icons-material/ContentCopy';
import ReceiptIcon from '@mui/icons-material/Receipt';;
import { format, parseISO } from 'date-fns';
import { formatCurrency } from '../../utils/formatters';
import { Transaction } from './TransactionItem';

interface TransactionDetailsProps {
  transaction: Transaction;
  open: boolean;
  onClose: () => void;
}

const TransactionDetails = memo<TransactionDetailsProps>(({
  transaction,
  open,
  onClose
}) => {
  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    // You could add a toast notification here
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'FAILED':
        return 'error';
      default:
        return 'default';
    }
  };

  const formatDateTime = (dateString: string) => {
    return format(parseISO(dateString), 'MMMM d, yyyy \'at\' h:mm:ss a');
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{
        sx: { maxHeight: '90vh' }
      }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h6">
          Transaction Details
        </Typography>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <DialogContent>
        <Box sx={{ py: 2 }}>
          {/* Transaction Header */}
          <Paper sx={{ p: 3, mb: 3, bgcolor: 'background.default' }}>
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} md={6}>
                <Typography variant="h4" fontWeight="bold" gutterBottom>
                  {formatCurrency(transaction.amount, transaction.currency)}
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  {transaction.description || 'Transaction'}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6} sx={{ textAlign: { md: 'right' } }}>
                <Chip
                  label={transaction.status}
                  color={getStatusColor(transaction.status) as any}
                  size="large"
                  sx={{ mb: 1 }}
                />
                <Typography variant="body2" color="text.secondary">
                  {formatDateTime(transaction.createdAt)}
                </Typography>
              </Grid>
            </Grid>
          </Paper>

          {/* Transaction Information */}
          <Grid container spacing={3}>
            <Grid item xs={12} md={6}>
              <Typography variant="h6" gutterBottom>
                Transaction Information
              </Typography>
              
              <Box sx={{ '& > div': { mb: 2 } }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    Transaction ID
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="body2" fontFamily="monospace">
                      {transaction.id}
                    </Typography>
                    <IconButton
                      size="small"
                      onClick={() => copyToClipboard(transaction.id, 'Transaction ID')}
                    >
                      <CopyIcon fontSize="small" />
                    </IconButton>
                  </Box>
                </Box>

                <Divider />

                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Type
                  </Typography>
                  <Typography variant="body2">
                    {transaction.type.replace('_', ' ')}
                  </Typography>
                </Box>

                <Divider />

                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Amount
                  </Typography>
                  <Typography variant="body2" fontWeight="bold">
                    {formatCurrency(transaction.amount, transaction.currency)}
                  </Typography>
                </Box>

                {transaction.fee && (
                  <>
                    <Divider />
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" color="text.secondary">
                        Processing Fee
                      </Typography>
                      <Typography variant="body2">
                        {formatCurrency(transaction.fee, transaction.currency)}
                      </Typography>
                    </Box>
                  </>
                )}

                <Divider />

                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Currency
                  </Typography>
                  <Typography variant="body2">
                    {transaction.currency}
                  </Typography>
                </Box>

                {transaction.reference && (
                  <>
                    <Divider />
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Typography variant="body2" color="text.secondary">
                        Reference
                      </Typography>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="body2" fontFamily="monospace">
                          {transaction.reference}
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() => copyToClipboard(transaction.reference!, 'Reference')}
                        >
                          <CopyIcon fontSize="small" />
                        </IconButton>
                      </Box>
                    </Box>
                  </>
                )}
              </Box>
            </Grid>

            <Grid item xs={12} md={6}>
              <Typography variant="h6" gutterBottom>
                Account Information
              </Typography>
              
              <Box sx={{ '& > div': { mb: 2 } }}>
                {transaction.fromAccount && (
                  <>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" color="text.secondary">
                        From Account
                      </Typography>
                      <Typography variant="body2">
                        {transaction.fromAccount}
                      </Typography>
                    </Box>
                    <Divider />
                  </>
                )}

                {transaction.toAccount && (
                  <>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" color="text.secondary">
                        To Account
                      </Typography>
                      <Typography variant="body2">
                        {transaction.toAccount}
                      </Typography>
                    </Box>
                    <Divider />
                  </>
                )}

                {transaction.merchantName && (
                  <>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" color="text.secondary">
                        Merchant
                      </Typography>
                      <Typography variant="body2">
                        {transaction.merchantName}
                      </Typography>
                    </Box>
                    <Divider />
                  </>
                )}

                {transaction.category && (
                  <>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                      <Typography variant="body2" color="text.secondary">
                        Category
                      </Typography>
                      <Chip 
                        label={transaction.category} 
                        size="small" 
                        variant="outlined" 
                      />
                    </Box>
                  </>
                )}
              </Box>

              {/* Additional Actions */}
              <Box sx={{ mt: 4 }}>
                <Typography variant="h6" gutterBottom>
                  Actions
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  <Button
                    variant="outlined"
                    startIcon={<ReceiptIcon />}
                    fullWidth
                  >
                    Download Receipt
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    fullWidth
                  >
                    Export as PDF
                  </Button>
                </Box>
              </Box>
            </Grid>
          </Grid>
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
});

TransactionDetails.displayName = 'TransactionDetails';

export default TransactionDetails;