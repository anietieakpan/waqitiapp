import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  TextField,
  Button,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  IconButton,
  Chip,
  InputAdornment,
  Menu,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tabs,
  Tab,
  Paper,
  Grid,
  Alert,
  Divider,
  CircularProgress,
  Fab,
  Card,
  CardContent,
  Badge,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import PersonIcon from '@mui/icons-material/Person';
import PhoneIcon from '@mui/icons-material/Phone';
import EmailIcon from '@mui/icons-material/Email';
import HistoryIcon from '@mui/icons-material/History';
import SendIcon from '@mui/icons-material/Send';
import QrCodeIcon from '@mui/icons-material/QrCode';
import ContactPageIcon from '@mui/icons-material/ContactPage';
import GroupIcon from '@mui/icons-material/Group';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import BlockIcon from '@mui/icons-material/Block';
import FlagIcon from '@mui/icons-material/Flag';;
import { useDebounce } from '../../hooks/useDebounce';
import { Contact, ContactRequest } from '../../types/contact';
import { paymentService } from '../../services/paymentService';
import { formatCurrency } from '../../utils/formatters';

interface ContactsManagerProps {
  onSendMoney: (contact: Contact) => void;
  onRequestMoney: (contact: Contact) => void;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <div role="tabpanel" hidden={value !== index}>
    {value === index && <Box sx={{ py: 2 }}>{children}</Box>}
  </div>
);

export const ContactsManager: React.FC<ContactsManagerProps> = ({
  onSendMoney,
  onRequestMoney,
}) => {
  const [activeTab, setActiveTab] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [filteredContacts, setFilteredContacts] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  
  // Dialog states
  const [addContactDialog, setAddContactDialog] = useState(false);
  const [editContactDialog, setEditContactDialog] = useState(false);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [contactMenu, setContactMenu] = useState<{
    anchor: HTMLElement | null;
    contact: Contact | null;
  }>({ anchor: null, contact: null });

  // Form states
  const [newContact, setNewContact] = useState<ContactRequest>({
    username: '',
    email: '',
    phoneNumber: '',
    firstName: '',
    lastName: '',
    displayName: '',
    notes: '',
  });

  const debouncedSearchQuery = useDebounce(searchQuery, 300);

  useEffect(() => {
    loadContacts();
  }, []);

  useEffect(() => {
    filterContacts();
  }, [contacts, debouncedSearchQuery, activeTab]);

  const loadContacts = async () => {
    setLoading(true);
    try {
      const data = await paymentService.getContacts();
      setContacts(data);
    } catch (err: any) {
      setError(err.message || 'Failed to load contacts');
    } finally {
      setLoading(false);
    }
  };

  const filterContacts = () => {
    let filtered = contacts;

    // Filter by tab
    switch (activeTab) {
      case 0: // All
        break;
      case 1: // Favorites
        filtered = filtered.filter(c => c.isFavorite);
        break;
      case 2: // Recent
        filtered = filtered.filter(c => c.lastTransactionDate)
          .sort((a, b) => new Date(b.lastTransactionDate!).getTime() - new Date(a.lastTransactionDate!).getTime());
        break;
      case 3: // Frequent
        filtered = filtered.filter(c => c.transactionCount && c.transactionCount > 0)
          .sort((a, b) => (b.transactionCount || 0) - (a.transactionCount || 0));
        break;
    }

    // Filter by search query
    if (debouncedSearchQuery) {
      const query = debouncedSearchQuery.toLowerCase();
      filtered = filtered.filter(contact =>
        contact.displayName?.toLowerCase().includes(query) ||
        contact.firstName?.toLowerCase().includes(query) ||
        contact.lastName?.toLowerCase().includes(query) ||
        contact.username?.toLowerCase().includes(query) ||
        contact.email?.toLowerCase().includes(query) ||
        contact.phoneNumber?.includes(query)
      );
    }

    setFilteredContacts(filtered);
  };

  const handleAddContact = async () => {
    try {
      const contact = await paymentService.addContact(newContact);
      setContacts([...contacts, contact]);
      setAddContactDialog(false);
      setNewContact({
        username: '',
        email: '',
        phoneNumber: '',
        firstName: '',
        lastName: '',
        displayName: '',
        notes: '',
      });
    } catch (err: any) {
      setError(err.message || 'Failed to add contact');
    }
  };

  const handleEditContact = async () => {
    if (!selectedContact) return;

    try {
      const updated = await paymentService.updateContact(selectedContact.id, newContact);
      setContacts(contacts.map(c => c.id === selectedContact.id ? updated : c));
      setEditContactDialog(false);
      setSelectedContact(null);
    } catch (err: any) {
      setError(err.message || 'Failed to update contact');
    }
  };

  const handleDeleteContact = async (contactId: string) => {
    try {
      await paymentService.deleteContact(contactId);
      setContacts(contacts.filter(c => c.id !== contactId));
    } catch (err: any) {
      setError(err.message || 'Failed to delete contact');
    }
  };

  const handleToggleFavorite = async (contact: Contact) => {
    try {
      await paymentService.toggleFavorite(contact.id, !contact.isFavorite);
      setContacts(contacts.map(c => 
        c.id === contact.id ? { ...c, isFavorite: !c.isFavorite } : c
      ));
    } catch (err: any) {
      setError(err.message || 'Failed to update favorite status');
    }
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, contact: Contact) => {
    setContactMenu({ anchor: event.currentTarget, contact });
  };

  const handleMenuClose = () => {
    setContactMenu({ anchor: null, contact: null });
  };

  const openEditDialog = (contact: Contact) => {
    setSelectedContact(contact);
    setNewContact({
      username: contact.username || '',
      email: contact.email || '',
      phoneNumber: contact.phoneNumber || '',
      firstName: contact.firstName || '',
      lastName: contact.lastName || '',
      displayName: contact.displayName || '',
      notes: contact.notes || '',
    });
    setEditContactDialog(true);
    handleMenuClose();
  };

  const getContactDisplayName = (contact: Contact) => {
    if (contact.displayName) return contact.displayName;
    if (contact.firstName && contact.lastName) {
      return `${contact.firstName} ${contact.lastName}`;
    }
    return contact.username || contact.email || contact.phoneNumber || 'Unknown';
  };

  const getContactSubtext = (contact: Contact) => {
    const parts = [];
    if (contact.username) parts.push(`@${contact.username}`);
    if (contact.email) parts.push(contact.email);
    if (contact.phoneNumber) parts.push(contact.phoneNumber);
    return parts.join(' • ');
  };

  const renderContactList = () => (
    <List>
      {filteredContacts.map((contact) => (
        <React.Fragment key={contact.id}>
          <ListItem>
            <ListItemAvatar>
              <Badge
                overlap="circular"
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                badgeContent={
                  contact.isFavorite ? (
                    <StarIcon sx={{ fontSize: 16, color: 'primary.main' }} />
                  ) : null
                }
              >
                <Avatar src={contact.avatarUrl} alt={getContactDisplayName(contact)}>
                  {!contact.avatarUrl && <PersonIcon />}
                </Avatar>
              </Badge>
            </ListItemAvatar>
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="subtitle1">
                    {getContactDisplayName(contact)}
                  </Typography>
                  {contact.isVerified && (
                    <Chip label="Verified" size="small" color="primary" />
                  )}
                </Box>
              }
              secondary={
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    {getContactSubtext(contact)}
                  </Typography>
                  {contact.lastTransactionDate && (
                    <Typography variant="caption" color="text.secondary">
                      Last transaction: {new Date(contact.lastTransactionDate).toLocaleDateString()}
                    </Typography>
                  )}
                  {contact.transactionCount && contact.transactionCount > 0 && (
                    <Typography variant="caption" color="text.secondary">
                      {contact.transactionCount} transactions
                    </Typography>
                  )}
                </Box>
              }
            />
            <ListItemSecondaryAction>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <IconButton
                  size="small"
                  onClick={() => onSendMoney(contact)}
                  color="primary"
                >
                  <SendIcon />
                </IconButton>
                <IconButton
                  size="small"
                  onClick={() => handleToggleFavorite(contact)}
                  color={contact.isFavorite ? 'primary' : 'default'}
                >
                  {contact.isFavorite ? <StarIcon /> : <StarBorderIcon />}
                </IconButton>
                <IconButton
                  size="small"
                  onClick={(e) => handleMenuOpen(e, contact)}
                >
                  <MoreVertIcon />
                </IconButton>
              </Box>
            </ListItemSecondaryAction>
          </ListItem>
          <Divider />
        </React.Fragment>
      ))}
    </List>
  );

  const renderEmptyState = () => (
    <Box sx={{ textAlign: 'center', py: 6 }}>
      <ContactPageIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
      <Typography variant="h6" gutterBottom>
        {searchQuery ? 'No contacts found' : 'No contacts yet'}
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        {searchQuery
          ? `No contacts match "${searchQuery}"`
          : 'Add contacts to send money quickly and easily'}
      </Typography>
      {!searchQuery && (
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setAddContactDialog(true)}
        >
          Add Contact
        </Button>
      )}
    </Box>
  );

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" gutterBottom>
          Contacts
        </Typography>
        <TextField
          fullWidth
          placeholder="Search contacts..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {/* Tabs */}
      <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)} sx={{ mb: 2 }}>
        <Tab label="All" />
        <Tab label="Favorites" />
        <Tab label="Recent" />
        <Tab label="Frequent" />
      </Tabs>

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>
          {error}
        </Alert>
      )}

      {/* Content */}
      <Box sx={{ position: 'relative', minHeight: 400 }}>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : filteredContacts.length === 0 ? (
          renderEmptyState()
        ) : (
          renderContactList()
        )}
      </Box>

      {/* Floating Action Button */}
      <Fab
        color="primary"
        aria-label="add contact"
        sx={{ position: 'fixed', bottom: 16, right: 16 }}
        onClick={() => setAddContactDialog(true)}
      >
        <AddIcon />
      </Fab>

      {/* Context Menu */}
      <Menu
        anchorEl={contactMenu.anchor}
        open={Boolean(contactMenu.anchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => onSendMoney(contactMenu.contact!)}>
          <SendIcon sx={{ mr: 1 }} />
          Send Money
        </MenuItem>
        <MenuItem onClick={() => onRequestMoney(contactMenu.contact!)}>
          <HistoryIcon sx={{ mr: 1 }} />
          Request Money
        </MenuItem>
        <MenuItem onClick={() => handleToggleFavorite(contactMenu.contact!)}>
          {contactMenu.contact?.isFavorite ? <StarIcon sx={{ mr: 1 }} /> : <StarBorderIcon sx={{ mr: 1 }} />}
          {contactMenu.contact?.isFavorite ? 'Remove from Favorites' : 'Add to Favorites'}
        </MenuItem>
        <MenuItem onClick={() => openEditDialog(contactMenu.contact!)}>
          <EditIcon sx={{ mr: 1 }} />
          Edit Contact
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => handleDeleteContact(contactMenu.contact!.id)} sx={{ color: 'error.main' }}>
          <DeleteIcon sx={{ mr: 1 }} />
          Delete Contact
        </MenuItem>
      </Menu>

      {/* Add Contact Dialog */}
      <Dialog
        open={addContactDialog}
        onClose={() => setAddContactDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Add New Contact</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="First Name"
                value={newContact.firstName}
                onChange={(e) => setNewContact({ ...newContact, firstName: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Last Name"
                value={newContact.lastName}
                onChange={(e) => setNewContact({ ...newContact, lastName: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Username"
                value={newContact.username}
                onChange={(e) => setNewContact({ ...newContact, username: e.target.value })}
                InputProps={{
                  startAdornment: <InputAdornment position="start">@</InputAdornment>,
                }}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Email"
                type="email"
                value={newContact.email}
                onChange={(e) => setNewContact({ ...newContact, email: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Phone Number"
                value={newContact.phoneNumber}
                onChange={(e) => setNewContact({ ...newContact, phoneNumber: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Display Name"
                value={newContact.displayName}
                onChange={(e) => setNewContact({ ...newContact, displayName: e.target.value })}
                helperText="How you'd like this contact to appear in your list"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Notes"
                multiline
                rows={2}
                value={newContact.notes}
                onChange={(e) => setNewContact({ ...newContact, notes: e.target.value })}
                helperText="Optional notes about this contact"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddContactDialog(false)}>Cancel</Button>
          <Button onClick={handleAddContact} variant="contained">
            Add Contact
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Contact Dialog */}
      <Dialog
        open={editContactDialog}
        onClose={() => setEditContactDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Edit Contact</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="First Name"
                value={newContact.firstName}
                onChange={(e) => setNewContact({ ...newContact, firstName: e.target.value })}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Last Name"
                value={newContact.lastName}
                onChange={(e) => setNewContact({ ...newContact, lastName: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Display Name"
                value={newContact.displayName}
                onChange={(e) => setNewContact({ ...newContact, displayName: e.target.value })}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Notes"
                multiline
                rows={2}
                value={newContact.notes}
                onChange={(e) => setNewContact({ ...newContact, notes: e.target.value })}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditContactDialog(false)}>Cancel</Button>
          <Button onClick={handleEditContact} variant="contained">
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContactsManager;