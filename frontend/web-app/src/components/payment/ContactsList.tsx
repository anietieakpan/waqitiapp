import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Button,
  Avatar,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Grid,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Fab,
  Paper,
  Chip,
  Menu,
  MenuItem,
  Alert,
  InputAdornment,
  Tabs,
  Tab,
  Divider,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import SendIcon from '@mui/icons-material/Send';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import MoreIcon from '@mui/icons-material/More';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import HistoryIcon from '@mui/icons-material/History';
import BlockIcon from '@mui/icons-material/Block';
import CloseIcon from '@mui/icons-material/Close';
import ContactPhoneIcon from '@mui/icons-material/ContactPhone';
import PersonAddIcon from '@mui/icons-material/PersonAdd';;
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { format } from 'date-fns';

import { Contact, ContactRequest } from '@/types/contact';
import { paymentService } from '@/services/paymentService';
import toast from 'react-hot-toast';

const schema = yup.object().shape({
  name: yup.string().required('Name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phone: yup.string().optional(),
});

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <div role="tabpanel" hidden={value !== index}>
    {value === index && <Box sx={{ pt: 2 }}>{children}</Box>}
  </div>
);

const ContactsList: React.FC = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [showContactDetails, setShowContactDetails] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ContactRequest>({
    resolver: yupResolver(schema),
    defaultValues: {
      name: '',
      email: '',
      phone: '',
    },
  });

  // Mock data for demonstration
  const mockContacts: Contact[] = [
    {
      id: 'contact_001',
      name: 'John Doe',
      email: 'john@example.com',
      phone: '+1234567890',
      isFavorite: true,
      lastTransactionDate: '2024-01-15T10:30:00Z',
      totalTransactions: 5,
      createdAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 'contact_002',
      name: 'Jane Smith',
      email: 'jane@example.com',
      phone: '+1234567891',
      isFavorite: false,
      lastTransactionDate: '2024-01-14T14:20:00Z',
      totalTransactions: 3,
      createdAt: '2024-01-02T00:00:00Z',
    },
    {
      id: 'contact_003',
      name: 'Mike Johnson',
      email: 'mike@example.com',
      phone: '+1234567892',
      isFavorite: true,
      lastTransactionDate: '2024-01-13T09:15:00Z',
      totalTransactions: 8,
      createdAt: '2024-01-03T00:00:00Z',
    },
    {
      id: 'contact_004',
      name: 'Sarah Wilson',
      email: 'sarah@example.com',
      phone: '+1234567893',
      isFavorite: false,
      lastTransactionDate: '2024-01-12T19:45:00Z',
      totalTransactions: 2,
      createdAt: '2024-01-04T00:00:00Z',
    },
    {
      id: 'contact_005',
      name: 'Alex Davis',
      email: 'alex@example.com',
      phone: '+1234567894',
      isFavorite: false,
      lastTransactionDate: '2024-01-11T16:00:00Z',
      totalTransactions: 1,
      createdAt: '2024-01-05T00:00:00Z',
    },
  ];

  const { data: contacts, isLoading } = useQuery(
    ['contacts'],
    () => paymentService.getContacts(),
    {
      // Use mock data for demonstration
      select: () => mockContacts,
    }
  );

  const addContactMutation = useMutation(
    (contactData: ContactRequest) => paymentService.addContact(contactData),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        toast.success('Contact added successfully!');
        setShowAddDialog(false);
        reset();
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to add contact');
      },
    }
  );

  const toggleFavoriteMutation = useMutation(
    ({ contactId, isFavorite }: { contactId: string; isFavorite: boolean }) =>
      paymentService.toggleFavorite(contactId, isFavorite),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        toast.success('Contact updated successfully!');
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to update contact');
      },
    }
  );

  const deleteContactMutation = useMutation(
    (contactId: string) => paymentService.deleteContact(contactId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        toast.success('Contact deleted successfully!');
        setShowContactDetails(false);
      },
      onError: (error: any) => {
        toast.error(error.message || 'Failed to delete contact');
      },
    }
  );

  const filteredContacts = contacts?.filter(contact => {
    const matchesSearch = searchTerm === '' || 
      contact.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      contact.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
      contact.phone?.toLowerCase().includes(searchTerm.toLowerCase());
    
    const matchesTab = activeTab === 0 || 
      (activeTab === 1 && contact.isFavorite) ||
      (activeTab === 2 && (contact.totalTransactions || 0) > 0);
    
    return matchesSearch && matchesTab;
  }) || [];

  const handleAddContact = (data: ContactRequest) => {
    addContactMutation.mutate(data);
  };

  const handleToggleFavorite = (contact: Contact) => {
    toggleFavoriteMutation.mutate({
      contactId: contact.id,
      isFavorite: !contact.isFavorite,
    });
  };

  const handleDeleteContact = (contact: Contact) => {
    if (window.confirm(`Are you sure you want to delete ${contact.name}?`)) {
      deleteContactMutation.mutate(contact.id);
    }
  };

  const handleSendMoney = (contact: Contact) => {
    // Navigate to send money with pre-filled contact
    window.location.href = `/payment/send?contact=${contact.id}`;
  };

  const handleRequestMoney = (contact: Contact) => {
    // Navigate to request money with pre-filled contact
    window.location.href = `/payment/request?contact=${contact.id}`;
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, contact: Contact) => {
    setMenuAnchor(event.currentTarget);
    setSelectedContact(contact);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const renderContactItem = (contact: Contact) => (
    <Card key={contact.id} sx={{ mb: 2 }}>
      <CardContent>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" flex={1}>
            <Avatar sx={{ mr: 2, width: 48, height: 48 }}>
              {contact.name.charAt(0)}
            </Avatar>
            <Box flex={1}>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="h6" fontWeight="medium">
                  {contact.name}
                </Typography>
                {contact.isFavorite && (
                  <Star color="warning" fontSize="small" />
                )}
              </Box>
              <Typography variant="body2" color="text.secondary">
                {contact.email}
              </Typography>
              {contact.phone && (
                <Typography variant="body2" color="text.secondary">
                  {contact.phone}
                </Typography>
              )}
              <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                {contact.totalTransactions && contact.totalTransactions > 0 && (
                  <Chip
                    label={`${contact.totalTransactions} transactions`}
                    size="small"
                    variant="outlined"
                  />
                )}
                {contact.lastTransactionDate && (
                  <Chip
                    label={`Last: ${format(new Date(contact.lastTransactionDate), 'MMM dd')}`}
                    size="small"
                    variant="outlined"
                  />
                )}
              </Box>
            </Box>
          </Box>
          <Box display="flex" gap={1}>
            <Button
              variant="outlined"
              size="small"
              startIcon={<Send />}
              onClick={() => handleSendMoney(contact)}
            >
              Send
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<RequestQuote />}
              onClick={() => handleRequestMoney(contact)}
            >
              Request
            </Button>
            <IconButton
              onClick={(e) => handleMenuOpen(e, contact)}
              size="small"
            >
              <More />
            </IconButton>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );

  const getTabLabel = (index: number) => {
    switch (index) {
      case 0:
        return `All (${contacts?.length || 0})`;
      case 1:
        return `Favorites (${contacts?.filter(c => c.isFavorite).length || 0})`;
      case 2:
        return `Recent (${contacts?.filter(c => c.totalTransactions && c.totalTransactions > 0).length || 0})`;
      default:
        return 'All';
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" gutterBottom>
        Contacts
      </Typography>

      {/* Search */}
      <TextField
        fullWidth
        placeholder="Search contacts..."
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

      {/* Quick Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <ContactPhone color="primary" sx={{ mr: 1 }} />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Total Contacts
                  </Typography>
                  <Typography variant="h6">
                    {contacts?.length || 0}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <Star color="warning" sx={{ mr: 1 }} />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Favorites
                  </Typography>
                  <Typography variant="h6">
                    {contacts?.filter(c => c.isFavorite).length || 0}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <History color="info" sx={{ mr: 1 }} />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Recent Activity
                  </Typography>
                  <Typography variant="h6">
                    {contacts?.filter(c => c.totalTransactions && c.totalTransactions > 0).length || 0}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <Send color="success" sx={{ mr: 1 }} />
                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Total Transactions
                  </Typography>
                  <Typography variant="h6">
                    {contacts?.reduce((sum, c) => sum + (c.totalTransactions || 0), 0) || 0}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, newValue) => setActiveTab(newValue)}
          variant="fullWidth"
        >
          <Tab label={getTabLabel(0)} />
          <Tab label={getTabLabel(1)} />
          <Tab label={getTabLabel(2)} />
        </Tabs>
      </Paper>

      {/* Contact List */}
      <TabPanel value={activeTab} index={0}>
        {filteredContacts.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Person sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No contacts found
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              {searchTerm ? 'Try a different search term' : 'Start by adding your first contact'}
            </Typography>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={() => setShowAddDialog(true)}
            >
              Add Contact
            </Button>
          </Paper>
        ) : (
          <Box>
            {filteredContacts.map(renderContactItem)}
          </Box>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        {filteredContacts.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Star sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No favorite contacts
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Star contacts to add them to your favorites
            </Typography>
          </Paper>
        ) : (
          <Box>
            {filteredContacts.map(renderContactItem)}
          </Box>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={2}>
        {filteredContacts.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <History sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              No recent activity
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Contacts with recent transactions will appear here
            </Typography>
          </Paper>
        ) : (
          <Box>
            {filteredContacts.map(renderContactItem)}
          </Box>
        )}
      </TabPanel>

      {/* Add Contact FAB */}
      <Fab
        color="primary"
        aria-label="add contact"
        sx={{ position: 'fixed', bottom: 16, right: 16 }}
        onClick={() => setShowAddDialog(true)}
      >
        <PersonAdd />
      </Fab>

      {/* Add Contact Dialog */}
      <Dialog
        open={showAddDialog}
        onClose={() => setShowAddDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center">
            <PersonAdd sx={{ mr: 1 }} />
            Add New Contact
          </Box>
        </DialogTitle>
        <DialogContent>
          <form onSubmit={handleSubmit(handleAddContact)}>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <Controller
                  name="name"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Name"
                      error={!!errors.name}
                      helperText={errors.name?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Person />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12}>
                <Controller
                  name="email"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Email"
                      type="email"
                      error={!!errors.email}
                      helperText={errors.email?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Email />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
              <Grid item xs={12}>
                <Controller
                  name="phone"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      fullWidth
                      label="Phone (optional)"
                      type="tel"
                      error={!!errors.phone}
                      helperText={errors.phone?.message}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <Phone />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
            </Grid>
          </form>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowAddDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmit(handleAddContact)}
            disabled={addContactMutation.isLoading}
          >
            Add Contact
          </Button>
        </DialogActions>
      </Dialog>

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => {
          setShowContactDetails(true);
          handleMenuClose();
        }}>
          View Details
        </MenuItem>
        <MenuItem onClick={() => {
          if (selectedContact) {
            handleToggleFavorite(selectedContact);
          }
          handleMenuClose();
        }}>
          {selectedContact?.isFavorite ? (
            <>
              <StarBorder sx={{ mr: 1 }} />
              Remove from Favorites
            </>
          ) : (
            <>
              <Star sx={{ mr: 1 }} />
              Add to Favorites
            </>
          )}
        </MenuItem>
        <MenuItem onClick={() => {
          if (selectedContact) {
            handleSendMoney(selectedContact);
          }
          handleMenuClose();
        }}>
          <Send sx={{ mr: 1 }} />
          Send Money
        </MenuItem>
        <MenuItem onClick={() => {
          if (selectedContact) {
            handleRequestMoney(selectedContact);
          }
          handleMenuClose();
        }}>
          <RequestQuote sx={{ mr: 1 }} />
          Request Money
        </MenuItem>
        <Divider />
        <MenuItem 
          onClick={() => {
            if (selectedContact) {
              handleDeleteContact(selectedContact);
            }
            handleMenuClose();
          }}
          sx={{ color: 'error.main' }}
        >
          <Delete sx={{ mr: 1 }} />
          Delete Contact
        </MenuItem>
      </Menu>

      {/* Contact Details Dialog */}
      <Dialog
        open={showContactDetails}
        onClose={() => setShowContactDetails(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="h6">Contact Details</Typography>
            <IconButton onClick={() => setShowContactDetails(false)}>
              <Close />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          {selectedContact && (
            <Box>
              <Box display="flex" alignItems="center" mb={3}>
                <Avatar sx={{ width: 64, height: 64, mr: 2 }}>
                  {selectedContact.name.charAt(0)}
                </Avatar>
                <Box>
                  <Typography variant="h6">
                    {selectedContact.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedContact.email}
                  </Typography>
                  {selectedContact.phone && (
                    <Typography variant="body2" color="text.secondary">
                      {selectedContact.phone}
                    </Typography>
                  )}
                </Box>
              </Box>
              
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Total Transactions
                  </Typography>
                  <Typography variant="h6">
                    {selectedContact.totalTransactions || 0}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Favorite
                  </Typography>
                  <Typography variant="h6">
                    {selectedContact.isFavorite ? 'Yes' : 'No'}
                  </Typography>
                </Grid>
                {selectedContact.lastTransactionDate && (
                  <Grid item xs={6}>
                    <Typography variant="subtitle2" color="text.secondary">
                      Last Transaction
                    </Typography>
                    <Typography variant="body2">
                      {format(new Date(selectedContact.lastTransactionDate), 'MMM dd, yyyy')}
                    </Typography>
                  </Grid>
                )}
                <Grid item xs={6}>
                  <Typography variant="subtitle2" color="text.secondary">
                    Added
                  </Typography>
                  <Typography variant="body2">
                    {format(new Date(selectedContact.createdAt || Date.now()), 'MMM dd, yyyy')}
                  </Typography>
                </Grid>
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowContactDetails(false)}>
            Close
          </Button>
          <Button
            variant="outlined"
            onClick={() => {
              if (selectedContact) {
                handleToggleFavorite(selectedContact);
              }
            }}
            startIcon={selectedContact?.isFavorite ? <StarBorder /> : <Star />}
          >
            {selectedContact?.isFavorite ? 'Unfavorite' : 'Favorite'}
          </Button>
          <Button
            variant="contained"
            onClick={() => {
              if (selectedContact) {
                handleSendMoney(selectedContact);
              }
            }}
            startIcon={<Send />}
          >
            Send Money
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContactsList;