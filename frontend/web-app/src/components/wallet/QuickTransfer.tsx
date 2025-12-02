import React, { useState } from 'react';
import {
  Paper,
  Typography,
  Box,
  Avatar,
  Button,
  IconButton,
  TextField,
  InputAdornment,
  Grid,
  Chip,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Collapse,
  useTheme,
  alpha,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import AddIcon from '@mui/icons-material/Add';
import PersonIcon from '@mui/icons-material/Person';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import HistoryIcon from '@mui/icons-material/History';
import PhoneIcon from '@mui/icons-material/Phone';
import EmailIcon from '@mui/icons-material/Email';
import QrCodeIcon from '@mui/icons-material/QrCode';;
import { format } from 'date-fns';
import { Contact } from '../../types/wallet';
import { formatCurrency } from '../../utils/formatters';

interface QuickTransferProps {
  contacts: Contact[];
  onTransfer: (recipient: Contact, amount: number) => void;
  onAddContact?: () => void;
  maxAmount?: number;
}

const QuickTransfer: React.FC<QuickTransferProps> = ({
  contacts = [],
  onTransfer,
  onAddContact,
  maxAmount = 1000,
}) => {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(false);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [amount, setAmount] = useState('');
  const [showTransferDialog, setShowTransferDialog] = useState(false);

  // Quick amount presets
  const quickAmounts = [10, 25, 50, 100];

  // Sort contacts by favorites first, then by recent activity
  const sortedContacts = [...contacts]
    .sort((a, b) => {
      if (a.isFavorite && !b.isFavorite) return -1;
      if (!a.isFavorite && b.isFavorite) return 1;
      
      const aLastPayment = a.lastPaymentDate ? new Date(a.lastPaymentDate).getTime() : 0;
      const bLastPayment = b.lastPaymentDate ? new Date(b.lastPaymentDate).getTime() : 0;
      return bLastPayment - aLastPayment;
    })
    .slice(0, 6); // Show only top 6 contacts

  const handleContactSelect = (contact: Contact) => {
    setSelectedContact(contact);
    setShowTransferDialog(true);
  };

  const handleTransfer = () => {
    if (selectedContact && amount) {
      onTransfer(selectedContact, parseFloat(amount));
      setShowTransferDialog(false);
      setAmount('');
      setSelectedContact(null);
    }
  };

  const handleQuickAmount = (quickAmount: number) => {
    setAmount(quickAmount.toString());
  };

  const isValidAmount = () => {
    const numAmount = parseFloat(amount);
    return numAmount > 0 && numAmount <= maxAmount;
  };

  const renderQuickContacts = () => (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
        Quick Send
      </Typography>
      
      {sortedContacts.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 3 }}>
          <PersonIcon sx={{ fontSize: 40, color: 'text.secondary', mb: 1 }} />
          <Typography variant="body2" color="text.secondary">
            No contacts available
          </Typography>
          <Button
            size="small"
            startIcon={<AddIcon />}
            onClick={onAddContact}
            sx={{ mt: 1 }}
          >
            Add Contact
          </Button>
        </Box>
      ) : (
        <Grid container spacing={1}>
          {sortedContacts.map((contact) => (
            <Grid item xs={4} sm={2} key={contact.id}>
              <Box
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  p: 1,
                  borderRadius: 2,
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                  '&:hover': {
                    bgcolor: alpha(theme.palette.primary.main, 0.05),
                  },
                }}
                onClick={() => handleContactSelect(contact)}
              >
                <Box sx={{ position: 'relative', mb: 1 }}>
                  <Avatar
                    src={contact.avatar}
                    sx={{
                      width: 48,
                      height: 48,
                      bgcolor: alpha(theme.palette.primary.main, 0.1),
                    }}
                  >
                    {contact.name.charAt(0)}
                  </Avatar>
                  {contact.isFavorite && (
                    <StarIcon
                      sx={{
                        position: 'absolute',
                        top: -4,
                        right: -4,
                        fontSize: 16,
                        color: theme.palette.warning.main,
                        bgcolor: 'background.paper',
                        borderRadius: '50%',
                      }}
                    />
                  )}
                </Box>
                <Typography
                  variant="caption"
                  sx={{
                    textAlign: 'center',
                    maxWidth: '100%',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {contact.name.split(' ')[0]}
                </Typography>
              </Box>
            </Grid>
          ))}
          
          {/* Add new contact option */}
          <Grid item xs={4} sm={2}>
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                p: 1,
                borderRadius: 2,
                cursor: 'pointer',
                border: `2px dashed ${alpha(theme.palette.primary.main, 0.3)}`,
                transition: 'all 0.2s',
                '&:hover': {
                  borderColor: theme.palette.primary.main,
                  bgcolor: alpha(theme.palette.primary.main, 0.05),
                },
              }}
              onClick={onAddContact}
            >
              <Avatar
                sx={{
                  width: 48,
                  height: 48,
                  bgcolor: alpha(theme.palette.primary.main, 0.1),
                  color: theme.palette.primary.main,
                  mb: 1,
                }}
              >
                <AddIcon />
              </Avatar>
              <Typography variant="caption" color="primary">
                Add
              </Typography>
            </Box>
          </Grid>
        </Grid>
      )}
    </Box>
  );

  const renderRecentContacts = () => (
    <Collapse in={expanded}>
      <Box sx={{ mt: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
          Recent Contacts
        </Typography>
        
        <List dense>
          {contacts.slice(0, 5).map((contact) => (
            <ListItem
              key={contact.id}
              button
              onClick={() => handleContactSelect(contact)}
              sx={{ borderRadius: 1 }}
            >
              <ListItemAvatar>
                <Avatar src={contact.avatar}>
                  {contact.name.charAt(0)}
                </Avatar>
              </ListItemAvatar>
              
              <ListItemText
                primary={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="subtitle2">
                      {contact.name}
                    </Typography>
                    {contact.isFavorite && (
                      <StarIcon sx={{ fontSize: 16, color: theme.palette.warning.main }} />
                    )}
                  </Box>
                }
                secondary={
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      @{contact.username}
                    </Typography>
                    {contact.lastPaymentDate && (
                      <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                        • Last: {format(new Date(contact.lastPaymentDate), 'MMM d')}
                      </Typography>
                    )}
                  </Box>
                }
              />
              
              <ListItemSecondaryAction>
                <IconButton size="small" onClick={() => handleContactSelect(contact)}>
                  <SendIcon />
                </IconButton>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>
      </Box>
    </Collapse>
  );

  return (
    <Paper sx={{ p: 0 }}>
      <Box
        sx={{
          p: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
        }}
        onClick={() => setExpanded(!expanded)}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <SendIcon />
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Quick Transfer
          </Typography>
        </Box>
        <IconButton size="small">
          {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
        </IconButton>
      </Box>

      <Box sx={{ px: 2, pb: 2 }}>
        {renderQuickContacts()}
        {renderRecentContacts()}
      </Box>

      {/* Transfer Dialog */}
      <Dialog
        open={showTransferDialog}
        onClose={() => setShowTransferDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Avatar src={selectedContact?.avatar}>
              {selectedContact?.name.charAt(0)}
            </Avatar>
            <Box>
              <Typography variant="h6">
                Send to {selectedContact?.name}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                @{selectedContact?.username}
              </Typography>
            </Box>
          </Box>
        </DialogTitle>
        
        <DialogContent>
          <TextField
            fullWidth
            label="Amount"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            type="number"
            inputProps={{ min: 0, max: maxAmount, step: 0.01 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <MoneyIcon />
                </InputAdornment>
              ),
            }}
            sx={{ mb: 2 }}
            helperText={`Maximum: ${formatCurrency(maxAmount)}`}
          />
          
          <Box sx={{ mb: 2 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Quick amounts
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {quickAmounts.map((quickAmount) => (
                <Chip
                  key={quickAmount}
                  label={`$${quickAmount}`}
                  onClick={() => handleQuickAmount(quickAmount)}
                  variant={amount === quickAmount.toString() ? 'filled' : 'outlined'}
                  color="primary"
                />
              ))}
            </Box>
          </Box>
          
          {selectedContact?.lastPaymentDate && (
            <Box sx={{ p: 2, bgcolor: alpha(theme.palette.info.main, 0.05), borderRadius: 1 }}>
              <Typography variant="body2" color="text.secondary">
                <HistoryIcon sx={{ fontSize: 16, mr: 0.5, verticalAlign: 'middle' }} />
                Last payment: {format(new Date(selectedContact.lastPaymentDate), 'MMM d, yyyy')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total sent: {formatCurrency(selectedContact.totalAmountSent)}
              </Typography>
            </Box>
          )}
        </DialogContent>
        
        <DialogActions>
          <Button onClick={() => setShowTransferDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleTransfer}
            disabled={!isValidAmount()}
            startIcon={<SendIcon />}
          >
            Send {amount && formatCurrency(parseFloat(amount))}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
};

export default QuickTransfer;