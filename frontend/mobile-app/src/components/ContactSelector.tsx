import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Image,
  ActivityIndicator,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * ContactSelector Component
 *
 * Reusable contact picker with search, filtering, and favorites
 *
 * Features:
 * - Contact search and filtering
 * - Favorites support
 * - Recent contacts
 * - Phone/email display
 * - Avatar support
 * - Loading states
 * - Empty states
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

export interface Contact {
  id: string;
  name: string;
  email?: string;
  phoneNumber?: string;
  profilePicture?: string;
  isFavorite?: boolean;
  lastContactedAt?: string;
  waqitiUserId?: string;
}

interface ContactSelectorProps {
  contacts: Contact[];
  selectedContact?: Contact | null;
  onSelectContact: (contact: Contact) => void;
  loading?: boolean;
  showFavorites?: boolean;
  showRecent?: boolean;
  placeholder?: string;
  emptyMessage?: string;
}

const ContactSelector: React.FC<ContactSelectorProps> = ({
  contacts,
  selectedContact,
  onSelectContact,
  loading = false,
  showFavorites = true,
  showRecent = true,
  placeholder = 'Search contacts...',
  emptyMessage = 'No contacts found',
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [filteredContacts, setFilteredContacts] = useState<Contact[]>([]);

  useEffect(() => {
    filterContacts();
  }, [searchQuery, contacts]);

  const filterContacts = () => {
    if (!searchQuery.trim()) {
      setFilteredContacts(contacts);
      return;
    }

    const query = searchQuery.toLowerCase();
    const filtered = contacts.filter((contact) => {
      return (
        contact.name.toLowerCase().includes(query) ||
        contact.email?.toLowerCase().includes(query) ||
        contact.phoneNumber?.includes(query)
      );
    });

    setFilteredContacts(filtered);

    AnalyticsService.trackEvent('contact_search', {
      query: searchQuery,
      resultsCount: filtered.length,
    });
  };

  const getFavoriteContacts = (): Contact[] => {
    return contacts.filter((c) => c.isFavorite).slice(0, 5);
  };

  const getRecentContacts = (): Contact[] => {
    return contacts
      .filter((c) => c.lastContactedAt)
      .sort((a, b) => {
        const dateA = new Date(a.lastContactedAt!).getTime();
        const dateB = new Date(b.lastContactedAt!).getTime();
        return dateB - dateA;
      })
      .slice(0, 5);
  };

  const handleSelectContact = (contact: Contact) => {
    AnalyticsService.trackEvent('contact_selected', {
      contactId: contact.id,
      source: 'search',
    });
    onSelectContact(contact);
  };

  const renderContactItem = ({ item }: { item: Contact }) => {
    const isSelected = selectedContact?.id === item.id;

    return (
      <TouchableOpacity
        style={[styles.contactItem, isSelected && styles.contactItemSelected]}
        onPress={() => handleSelectContact(item)}
      >
        <View style={styles.contactItemLeft}>
          {item.profilePicture ? (
            <Image source={{ uri: item.profilePicture }} style={styles.avatar} />
          ) : (
            <View style={styles.avatarPlaceholder}>
              <Text style={styles.avatarText}>
                {item.name.charAt(0).toUpperCase()}
              </Text>
            </View>
          )}

          <View style={styles.contactInfo}>
            <View style={styles.contactNameRow}>
              <Text style={styles.contactName}>{item.name}</Text>
              {item.isFavorite && (
                <Icon name="star" size={14} color="#FFD700" />
              )}
              {item.waqitiUserId && (
                <Icon name="check-decagram" size={14} color="#6200EE" />
              )}
            </View>
            <Text style={styles.contactDetails}>
              {item.email || item.phoneNumber}
            </Text>
          </View>
        </View>

        {isSelected && (
          <Icon name="check-circle" size={24} color="#6200EE" />
        )}
      </TouchableOpacity>
    );
  };

  const renderQuickContactItem = (contact: Contact) => (
    <TouchableOpacity
      key={contact.id}
      style={styles.quickContactItem}
      onPress={() => handleSelectContact(contact)}
    >
      {contact.profilePicture ? (
        <Image source={{ uri: contact.profilePicture }} style={styles.quickAvatar} />
      ) : (
        <View style={[styles.quickAvatar, styles.avatarPlaceholder]}>
          <Text style={styles.quickAvatarText}>
            {contact.name.charAt(0).toUpperCase()}
          </Text>
        </View>
      )}
      <Text style={styles.quickContactName} numberOfLines={1}>
        {contact.name.split(' ')[0]}
      </Text>
    </TouchableOpacity>
  );

  const renderFavorites = () => {
    const favorites = getFavoriteContacts();
    if (!showFavorites || favorites.length === 0) return null;

    return (
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Favorites</Text>
        <View style={styles.quickContactsContainer}>
          {favorites.map(renderQuickContactItem)}
        </View>
      </View>
    );
  };

  const renderRecent = () => {
    const recent = getRecentContacts();
    if (!showRecent || recent.length === 0) return null;

    return (
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Recent</Text>
        <View style={styles.quickContactsContainer}>
          {recent.map(renderQuickContactItem)}
        </View>
      </View>
    );
  };

  const renderSearchBar = () => (
    <View style={styles.searchContainer}>
      <Icon name="magnify" size={20} color="#999" />
      <TextInput
        style={styles.searchInput}
        value={searchQuery}
        onChangeText={setSearchQuery}
        placeholder={placeholder}
        placeholderTextColor="#999"
        autoCapitalize="none"
        autoCorrect={false}
      />
      {searchQuery ? (
        <TouchableOpacity onPress={() => setSearchQuery('')}>
          <Icon name="close-circle" size={20} color="#999" />
        </TouchableOpacity>
      ) : null}
    </View>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <Icon name="account-search" size={64} color="#E0E0E0" />
      <Text style={styles.emptyText}>{emptyMessage}</Text>
      {searchQuery && (
        <TouchableOpacity
          style={styles.clearSearchButton}
          onPress={() => setSearchQuery('')}
        >
          <Text style={styles.clearSearchText}>Clear search</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  const renderLoadingState = () => (
    <View style={styles.loadingContainer}>
      <ActivityIndicator size="large" color="#6200EE" />
      <Text style={styles.loadingText}>Loading contacts...</Text>
    </View>
  );

  if (loading) {
    return renderLoadingState();
  }

  return (
    <View style={styles.container}>
      {renderSearchBar()}

      {!searchQuery && (
        <>
          {renderFavorites()}
          {renderRecent()}
        </>
      )}

      <View style={styles.listHeader}>
        <Text style={styles.sectionTitle}>
          {searchQuery ? 'Search Results' : 'All Contacts'}
        </Text>
        <Text style={styles.contactCount}>
          {filteredContacts.length} contact{filteredContacts.length !== 1 ? 's' : ''}
        </Text>
      </View>

      <FlatList
        data={filteredContacts}
        renderItem={renderContactItem}
        keyExtractor={(item) => item.id}
        ListEmptyComponent={renderEmptyState}
        contentContainerStyle={
          filteredContacts.length === 0 ? styles.emptyListContainer : undefined
        }
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
    color: '#212121',
    marginLeft: 12,
    padding: 0,
  },
  section: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#666',
    marginHorizontal: 16,
    marginBottom: 12,
    textTransform: 'uppercase',
  },
  quickContactsContainer: {
    flexDirection: 'row',
    paddingHorizontal: 12,
  },
  quickContactItem: {
    alignItems: 'center',
    marginHorizontal: 8,
    width: 64,
  },
  quickAvatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    marginBottom: 4,
  },
  quickAvatarText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  quickContactName: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },
  listHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#F5F5F5',
  },
  contactCount: {
    fontSize: 12,
    color: '#999',
  },
  contactItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  contactItemSelected: {
    backgroundColor: '#F3E5F5',
  },
  contactItemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
  },
  avatarPlaceholder: {
    backgroundColor: '#6200EE',
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  contactInfo: {
    marginLeft: 12,
    flex: 1,
  },
  contactNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  contactName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginRight: 6,
  },
  contactDetails: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
  },
  emptyListContainer: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#999',
    marginTop: 16,
  },
  clearSearchButton: {
    marginTop: 16,
    paddingHorizontal: 24,
    paddingVertical: 12,
    backgroundColor: '#6200EE',
    borderRadius: 20,
  },
  clearSearchText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
});

export default ContactSelector;
