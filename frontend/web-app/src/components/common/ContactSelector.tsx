import React, { useState, useEffect, useMemo } from 'react';
import {
  Autocomplete,
  TextField,
  Box,
  Avatar,
  Typography,
  Chip,
  CircularProgress,
  Paper,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemButton,
  InputAdornment,
  IconButton,
  Divider,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import PersonIcon from '@mui/icons-material/Person';
import StarIcon from '@mui/icons-material/Star';
import HistoryIcon from '@mui/icons-material/History';
import ContactPageIcon from '@mui/icons-material/ContactPage';
import QrCodeIcon from '@mui/icons-material/QrCode';;
import { useDebounce } from '../../hooks/useDebounce';
import { Contact } from '../../types/contact';
import { paymentService } from '../../services/paymentService';

interface ContactSelectorProps {
  value: Contact | null;
  onChange: (contact: Contact | null) => void;
  label?: string;
  placeholder?: string;
  error?: boolean;
  helperText?: string;
  disabled?: boolean;
  showRecent?: boolean;
  showFavorites?: boolean;
  showQRScanner?: boolean;
  onQRScan?: () => void;
  autoFocus?: boolean;
}

export const ContactSelector: React.FC<ContactSelectorProps> = ({
  value,
  onChange,
  label = 'To',
  placeholder = 'Search by name, username, email, or phone',
  error = false,
  helperText,
  disabled = false,
  showRecent = true,
  showFavorites = true,
  showQRScanner = true,
  onQRScan,
  autoFocus = false,
}) => {
  const [inputValue, setInputValue] = useState('');
  const [options, setOptions] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(false);
  const [favorites, setFavorites] = useState<Contact[]>([]);
  const [recent, setRecent] = useState<Contact[]>([]);
  const [open, setOpen] = useState(false);

  const debouncedSearchTerm = useDebounce(inputValue, 300);

  useEffect(() => {
    loadInitialContacts();
  }, []);

  useEffect(() => {
    if (debouncedSearchTerm && debouncedSearchTerm.length >= 2) {
      searchContacts(debouncedSearchTerm);
    } else if (!debouncedSearchTerm) {
      setOptions([...favorites, ...recent]);
    }
  }, [debouncedSearchTerm]);

  const loadInitialContacts = async () => {
    try {
      const contacts = await paymentService.getContacts();
      const favoriteContacts = contacts.filter((c: Contact) => c.isFavorite);
      const recentContacts = contacts.filter((c: Contact) => c.lastTransactionDate)
        .sort((a: Contact, b: Contact) => 
          new Date(b.lastTransactionDate!).getTime() - new Date(a.lastTransactionDate!).getTime()
        )
        .slice(0, 5);
      
      setFavorites(favoriteContacts);
      setRecent(recentContacts);
      setOptions([...favoriteContacts, ...recentContacts]);
    } catch (error) {
      console.error('Error loading contacts:', error);
    }
  };

  const searchContacts = async (searchTerm: string) => {
    setLoading(true);
    try {
      const results = await paymentService.searchContacts(searchTerm);
      setOptions(results);
    } catch (error) {
      console.error('Error searching contacts:', error);
      setOptions([]);
    } finally {
      setLoading(false);
    }
  };

  const getContactDisplayName = (contact: Contact) => {
    if (contact.displayName) return contact.displayName;
    if (contact.firstName && contact.lastName) {
      return `${contact.firstName} ${contact.lastName}`;
    }
    return contact.username || contact.email || contact.phoneNumber || 'Unknown';
  };

  const getContactSubtext = (contact: Contact) => {
    if (contact.username) return `@${contact.username}`;
    if (contact.email) return contact.email;
    if (contact.phoneNumber) return contact.phoneNumber;
    return '';
  };

  const renderOption = (props: any, option: Contact) => {
    const isFavorite = favorites.some(f => f.id === option.id);
    const isRecent = recent.some(r => r.id === option.id);

    return (
      <ListItem {...props} component="li">
        <ListItemAvatar>
          <Avatar src={option.avatarUrl} alt={getContactDisplayName(option)}>
            {!option.avatarUrl && <PersonIcon />}
          </Avatar>
        </ListItemAvatar>
        <ListItemText
          primary={
            <Box display="flex" alignItems="center" gap={1}>
              <Typography variant="body1">{getContactDisplayName(option)}</Typography>
              {isFavorite && <StarIcon fontSize="small" color="primary" />}
              {isRecent && !isFavorite && <HistoryIcon fontSize="small" color="action" />}
            </Box>
          }
          secondary={getContactSubtext(option)}
        />
      </ListItem>
    );
  };

  const groupedOptions = useMemo(() => {
    if (debouncedSearchTerm) {
      return options;
    }

    const groups: { group: string; options: Contact[] }[] = [];
    
    if (showFavorites && favorites.length > 0) {
      groups.push({ group: 'Favorites', options: favorites });
    }
    
    if (showRecent && recent.length > 0) {
      const recentNotInFavorites = recent.filter(
        r => !favorites.some(f => f.id === r.id)
      );
      if (recentNotInFavorites.length > 0) {
        groups.push({ group: 'Recent', options: recentNotInFavorites });
      }
    }

    const others = options.filter(
      o => !favorites.some(f => f.id === o.id) && !recent.some(r => r.id === o.id)
    );
    if (others.length > 0) {
      groups.push({ group: 'All Contacts', options: others });
    }

    return groups;
  }, [options, favorites, recent, debouncedSearchTerm, showFavorites, showRecent]);

  return (
    <Autocomplete
      value={value}
      onChange={(_, newValue) => onChange(newValue)}
      inputValue={inputValue}
      onInputChange={(_, newInputValue) => setInputValue(newInputValue)}
      options={debouncedSearchTerm ? options : groupedOptions.flatMap(g => g.options)}
      groupBy={debouncedSearchTerm ? undefined : (option) => {
        const group = groupedOptions.find(g => g.options.some(o => o.id === option.id));
        return group?.group || '';
      }}
      getOptionLabel={getContactDisplayName}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      loading={loading}
      open={open}
      onOpen={() => setOpen(true)}
      onClose={() => setOpen(false)}
      disabled={disabled}
      autoHighlight
      fullWidth
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          error={error}
          helperText={helperText}
          autoFocus={autoFocus}
          InputProps={{
            ...params.InputProps,
            startAdornment: (
              <>
                <InputAdornment position="start">
                  <SearchIcon color="action" />
                </InputAdornment>
                {params.InputProps.startAdornment}
              </>
            ),
            endAdornment: (
              <>
                {loading && <CircularProgress color="inherit" size={20} />}
                {showQRScanner && onQRScan && (
                  <InputAdornment position="end">
                    <IconButton
                      edge="end"
                      onClick={onQRScan}
                      disabled={disabled}
                      size="small"
                    >
                      <QrCodeIcon />
                    </IconButton>
                  </InputAdornment>
                )}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
      renderOption={renderOption}
      PaperComponent={({ children, ...props }) => (
        <Paper {...props} elevation={3}>
          {children}
          {!loading && options.length === 0 && inputValue.length >= 2 && (
            <Box p={2} textAlign="center">
              <ContactPageIcon color="action" sx={{ fontSize: 48, mb: 1 }} />
              <Typography color="text.secondary">
                No contacts found for "{inputValue}"
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Try searching by username, email, or phone number
              </Typography>
            </Box>
          )}
        </Paper>
      )}
    />
  );
};

export default ContactSelector;