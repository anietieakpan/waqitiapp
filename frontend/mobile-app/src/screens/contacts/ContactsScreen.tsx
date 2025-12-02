import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  TextInput,
  SectionList,
  ActivityIndicator,
  RefreshControl,
  Alert,
  Animated,
  LayoutAnimation,
  Platform,
  UIManager,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { useTheme } from '../../contexts/ThemeContext';
import { contactService } from '../../services/contactService';
import { Contact } from '../../types';
import UserAvatar from '../../components/UserAvatar';
import EmptyState from '../../components/EmptyState';
import SwipeableRow from '../../components/SwipeableRow';
import { formatPhoneNumber } from '../../utils/formatters';
import Haptics from 'react-native-haptic-feedback';

// Enable LayoutAnimation on Android
if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

interface ContactSection {
  title: string;
  data: Contact[];
}

const ContactsScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedContacts, setSelectedContacts] = useState<string[]>([]);
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [showFavorites, setShowFavorites] = useState(false);
  const searchInputRef = useRef<TextInput>(null);
  const fadeAnim = useRef(new Animated.Value(0)).current;

  // Fetch contacts
  const { data: contacts, isLoading, refetch, isRefreshing } = useQuery(
    'contacts',
    contactService.getContacts,
    {
      staleTime: 5 * 60 * 1000, // 5 minutes
    }
  );

  // Remove contact mutation
  const removeContactMutation = useMutation(
    (contactId: string) => contactService.removeContact(contactId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      },
      onError: (error: any) => {
        Alert.alert('Error', error.message || 'Failed to remove contact');
      },
    }
  );

  // Toggle favorite mutation
  const toggleFavoriteMutation = useMutation(
    ({ contactId, isFavorite }: { contactId: string; isFavorite: boolean }) =>
      contactService.toggleFavorite(contactId, !isFavorite),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      },
    }
  );

  // Block contact mutation
  const blockContactMutation = useMutation(
    (contactId: string) => contactService.blockContact(contactId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries('contacts');
        Alert.alert('Contact Blocked', 'This contact has been blocked successfully.');
      },
    }
  );

  useEffect(() => {
    Animated.timing(fadeAnim, {
      toValue: 1,
      duration: 300,
      useNativeDriver: true,
    }).start();
  }, []);

  // Filter contacts based on search query and favorites
  const filteredContacts = contacts?.filter(contact => {
    const matchesSearch = !searchQuery || 
      contact.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      contact.username?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      contact.email?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      contact.phoneNumber?.includes(searchQuery);
    
    const matchesFavorites = !showFavorites || contact.isFavorite;
    
    return matchesSearch && matchesFavorites;
  }) || [];

  // Group contacts alphabetically
  const groupedContacts: ContactSection[] = React.useMemo(() => {
    const groups: { [key: string]: Contact[] } = {};
    
    // Add favorites section if showing all contacts
    if (!showFavorites && filteredContacts.some(c => c.isFavorite)) {
      groups['★'] = filteredContacts.filter(c => c.isFavorite);
    }
    
    // Group remaining contacts
    filteredContacts
      .filter(c => !c.isFavorite || showFavorites)
      .forEach(contact => {
        const firstLetter = contact.displayName[0].toUpperCase();
        const key = /[A-Z]/.test(firstLetter) ? firstLetter : '#';
        
        if (!groups[key]) {
          groups[key] = [];
        }
        groups[key].push(contact);
      });
    
    // Convert to sections array and sort
    return Object.entries(groups)
      .map(([title, data]) => ({
        title,
        data: data.sort((a, b) => a.displayName.localeCompare(b.displayName)),
      }))
      .sort((a, b) => {
        if (a.title === '★') return -1;
        if (b.title === '★') return 1;
        if (a.title === '#') return 1;
        if (b.title === '#') return -1;
        return a.title.localeCompare(b.title);
      });
  }, [filteredContacts, showFavorites]);

  const handleContactPress = (contact: Contact) => {
    if (isSelectionMode) {
      toggleContactSelection(contact.id);
    } else {
      navigation.navigate('ContactDetails', { contact });
    }
  };

  const toggleContactSelection = (contactId: string) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setSelectedContacts(prev =>
      prev.includes(contactId)
        ? prev.filter(id => id !== contactId)
        : [...prev, contactId]
    );
  };

  const handleSendToSelected = () => {
    if (selectedContacts.length === 0) {
      Alert.alert('No Contacts Selected', 'Please select at least one contact');
      return;
    }
    
    const recipients = contacts?.filter(c => selectedContacts.includes(c.id)) || [];
    navigation.navigate('SendMoney', { recipients });
    exitSelectionMode();
  };

  const handleRequestFromSelected = () => {
    if (selectedContacts.length === 0) {
      Alert.alert('No Contacts Selected', 'Please select at least one contact');
      return;
    }
    
    const recipients = contacts?.filter(c => selectedContacts.includes(c.id)) || [];
    navigation.navigate('RequestMoney', { recipients });
    exitSelectionMode();
  };

  const exitSelectionMode = () => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setIsSelectionMode(false);
    setSelectedContacts([]);
  };

  const handleRemoveContact = (contact: Contact) => {
    Alert.alert(
      'Remove Contact',
      `Remove ${contact.displayName} from your contacts?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: () => removeContactMutation.mutate(contact.id),
        },
      ]
    );
  };

  const handleBlockContact = (contact: Contact) => {
    Alert.alert(
      'Block Contact',
      `Block ${contact.displayName}? They won't be able to send you money or requests.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Block',
          style: 'destructive',
          onPress: () => blockContactMutation.mutate(contact.id),
        },
      ]
    );
  };

  const renderContact = ({ item, section }: { item: Contact; section: ContactSection }) => {
    const isSelected = selectedContacts.includes(item.id);
    
    return (
      <SwipeableRow
        rightActions={[
          {
            text: 'Remove',
            backgroundColor: '#FF3B30',
            onPress: () => handleRemoveContact(item),
          },
          {
            text: 'Block',
            backgroundColor: '#FF9500',
            onPress: () => handleBlockContact(item),
          },
        ]}
        enabled={!isSelectionMode}
      >
        <TouchableOpacity
          style={[
            styles.contactItem,
            { backgroundColor: theme.colors.surface },
            isSelected && styles.selectedContact,
          ]}
          onPress={() => handleContactPress(item)}
          onLongPress={() => {
            if (!isSelectionMode) {
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
              setIsSelectionMode(true);
              toggleContactSelection(item.id);
            }
          }}
          activeOpacity={0.7}
        >
          {isSelectionMode && (
            <View style={styles.checkboxContainer}>
              <Icon
                name={isSelected ? 'check-box' : 'check-box-outline-blank'}
                size={24}
                color={isSelected ? theme.colors.primary : theme.colors.textSecondary}
              />
            </View>
          )}
          
          <UserAvatar user={item} size={48} style={styles.avatar} />
          
          <View style={styles.contactInfo}>
            <View style={styles.nameRow}>
              <Text style={[styles.contactName, { color: theme.colors.text }]}>
                {item.displayName}
              </Text>
              {item.verified && (
                <Icon name="verified" size={16} color={theme.colors.primary} />
              )}
            </View>
            
            {item.username && (
              <Text style={[styles.contactUsername, { color: theme.colors.textSecondary }]}>
                @{item.username}
              </Text>
            )}
            
            {item.lastTransactionDate && (
              <Text style={[styles.lastTransaction, { color: theme.colors.textSecondary }]}>
                Last transaction: {formatRelativeTime(item.lastTransactionDate)}
              </Text>
            )}
          </View>
          
          <View style={styles.contactActions}>
            <TouchableOpacity
              onPress={() => toggleFavoriteMutation.mutate({
                contactId: item.id,
                isFavorite: item.isFavorite,
              })}
              style={styles.favoriteButton}
            >
              <Icon
                name={item.isFavorite ? 'star' : 'star-border'}
                size={24}
                color={item.isFavorite ? '#FFD700' : theme.colors.textSecondary}
              />
            </TouchableOpacity>
            
            {!isSelectionMode && (
              <TouchableOpacity
                onPress={() => navigation.navigate('SendMoney', { recipient: item })}
                style={[styles.sendButton, { backgroundColor: theme.colors.primary }]}
              >
                <Text style={styles.sendButtonText}>Send</Text>
              </TouchableOpacity>
            )}
          </View>
        </TouchableOpacity>
      </SwipeableRow>
    );
  };

  const renderSectionHeader = ({ section }: { section: ContactSection }) => (
    <View style={[styles.sectionHeader, { backgroundColor: theme.colors.background }]}>
      <Text style={[styles.sectionTitle, { color: theme.colors.textSecondary }]}>
        {section.title}
      </Text>
    </View>
  );

  const ListEmptyComponent = () => (
    <EmptyState
      icon="group"
      title={searchQuery ? 'No contacts found' : 'No contacts yet'}
      message={searchQuery 
        ? `No contacts matching "${searchQuery}"`
        : 'Start adding friends and family to send money quickly'
      }
      actionLabel="Find People"
      onAction={() => navigation.navigate('UserSearch')}
    />
  );

  const ListHeaderComponent = () => (
    <View style={styles.listHeader}>
      <View style={[styles.searchContainer, { backgroundColor: theme.colors.surface }]}>
        <Icon name="search" size={20} color={theme.colors.textSecondary} />
        <TextInput
          ref={searchInputRef}
          style={[styles.searchInput, { color: theme.colors.text }]}
          placeholder="Search contacts"
          placeholderTextColor={theme.colors.textSecondary}
          value={searchQuery}
          onChangeText={setSearchQuery}
          autoCapitalize="none"
          autoCorrect={false}
          clearButtonMode="while-editing"
        />
      </View>
      
      <View style={styles.filterRow}>
        <TouchableOpacity
          style={[
            styles.filterButton,
            showFavorites && styles.filterButtonActive,
            { borderColor: theme.colors.primary }
          ]}
          onPress={() => setShowFavorites(!showFavorites)}
        >
          <Icon 
            name="star" 
            size={16} 
            color={showFavorites ? theme.colors.primary : theme.colors.textSecondary} 
          />
          <Text style={[
            styles.filterButtonText,
            { color: showFavorites ? theme.colors.primary : theme.colors.textSecondary }
          ]}>
            Favorites
          </Text>
        </TouchableOpacity>
        
        <Text style={[styles.contactCount, { color: theme.colors.textSecondary }]}>
          {filteredContacts.length} contacts
        </Text>
      </View>
    </View>
  );

  if (isLoading && !contacts) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <Animated.View style={[styles.container, { opacity: fadeAnim }]}>
        <View style={styles.header}>
          <Text style={[styles.title, { color: theme.colors.text }]}>
            Contacts
          </Text>
          
          <View style={styles.headerActions}>
            {isSelectionMode ? (
              <>
                <TouchableOpacity
                  onPress={exitSelectionMode}
                  style={styles.headerButton}
                >
                  <Text style={[styles.headerButtonText, { color: theme.colors.primary }]}>
                    Cancel
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => {
                    const allSelected = filteredContacts.every(c => 
                      selectedContacts.includes(c.id)
                    );
                    if (allSelected) {
                      setSelectedContacts([]);
                    } else {
                      setSelectedContacts(filteredContacts.map(c => c.id));
                    }
                  }}
                  style={styles.headerButton}
                >
                  <Text style={[styles.headerButtonText, { color: theme.colors.primary }]}>
                    {selectedContacts.length === filteredContacts.length ? 'Deselect All' : 'Select All'}
                  </Text>
                </TouchableOpacity>
              </>
            ) : (
              <>
                <TouchableOpacity
                  onPress={() => navigation.navigate('UserSearch')}
                  style={styles.headerButton}
                >
                  <Icon name="person-add" size={24} color={theme.colors.primary} />
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => navigation.navigate('ScanQR')}
                  style={styles.headerButton}
                >
                  <Icon name="qr-code-scanner" size={24} color={theme.colors.primary} />
                </TouchableOpacity>
              </>
            )}
          </View>
        </View>

        <SectionList
          sections={groupedContacts}
          keyExtractor={(item) => item.id}
          renderItem={renderContact}
          renderSectionHeader={renderSectionHeader}
          ListEmptyComponent={ListEmptyComponent}
          ListHeaderComponent={ListHeaderComponent}
          contentContainerStyle={styles.listContent}
          stickySectionHeadersEnabled
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={refetch}
              tintColor={theme.colors.primary}
            />
          }
        />

        {isSelectionMode && selectedContacts.length > 0 && (
          <View style={[styles.selectionActions, { backgroundColor: theme.colors.surface }]}>
            <TouchableOpacity
              style={[styles.selectionButton, { backgroundColor: theme.colors.primary }]}
              onPress={handleSendToSelected}
            >
              <Icon name="send" size={20} color="#FFFFFF" />
              <Text style={styles.selectionButtonText}>
                Send ({selectedContacts.length})
              </Text>
            </TouchableOpacity>
            
            <TouchableOpacity
              style={[styles.selectionButton, styles.requestButton]}
              onPress={handleRequestFromSelected}
            >
              <Icon name="request-quote" size={20} color={theme.colors.primary} />
              <Text style={[styles.selectionButtonText, { color: theme.colors.primary }]}>
                Request ({selectedContacts.length})
              </Text>
            </TouchableOpacity>
          </View>
        )}
      </Animated.View>
    </SafeAreaView>
  );
};

const formatRelativeTime = (date: Date): string => {
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);
  
  if (diffInSeconds < 60) return 'just now';
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
  if (diffInSeconds < 604800) return `${Math.floor(diffInSeconds / 86400)}d ago`;
  
  return date.toLocaleDateString();
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  headerActions: {
    flexDirection: 'row',
    gap: 16,
  },
  headerButton: {
    padding: 4,
  },
  headerButtonText: {
    fontSize: 16,
    fontWeight: '500',
  },
  listHeader: {
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
    marginBottom: 12,
  },
  searchInput: {
    flex: 1,
    marginLeft: 8,
    fontSize: 16,
  },
  filterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  filterButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    gap: 4,
  },
  filterButtonActive: {
    backgroundColor: 'rgba(0, 122, 255, 0.1)',
  },
  filterButtonText: {
    fontSize: 14,
  },
  contactCount: {
    fontSize: 14,
  },
  listContent: {
    flexGrow: 1,
  },
  sectionHeader: {
    paddingHorizontal: 16,
    paddingVertical: 6,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
  },
  contactItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  selectedContact: {
    backgroundColor: 'rgba(0, 122, 255, 0.05)',
  },
  checkboxContainer: {
    marginRight: 12,
  },
  avatar: {
    marginRight: 12,
  },
  contactInfo: {
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  contactName: {
    fontSize: 16,
    fontWeight: '500',
  },
  contactUsername: {
    fontSize: 14,
    marginTop: 2,
  },
  lastTransaction: {
    fontSize: 12,
    marginTop: 4,
  },
  contactActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  favoriteButton: {
    padding: 4,
  },
  sendButton: {
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 16,
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  selectionActions: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(0, 0, 0, 0.1)',
  },
  selectionButton: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 12,
    borderRadius: 12,
    gap: 8,
  },
  requestButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  selectionButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default ContactsScreen;