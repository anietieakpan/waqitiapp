import React, { useState, useCallback, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  FlatList,
  TouchableOpacity,
  Image,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Keyboard,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useNavigation } from '@react-navigation/native';
import { useQuery, useMutation } from 'react-query';
import debounce from 'lodash/debounce';
import { useTheme } from '../../contexts/ThemeContext';
import { userService } from '../../services/userService';
import { contactService } from '../../services/contactService';
import { User, Contact } from '../../types';
import EmptyState from '../../components/EmptyState';
import UserAvatar from '../../components/UserAvatar';
import { formatPhoneNumber } from '../../utils/formatters';

interface SearchResult extends User {
  isContact: boolean;
  mutualContacts?: number;
}

const UserSearchScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // Load recent searches from storage
  useEffect(() => {
    loadRecentSearches();
  }, []);

  // Fetch user's contacts
  const { data: contacts } = useQuery('contacts', contactService.getContacts);

  // Add contact mutation
  const addContactMutation = useMutation(
    (userId: string) => contactService.addContact(userId),
    {
      onSuccess: () => {
        // Refresh contacts and search results
        queryClient.invalidateQueries('contacts');
        handleSearch(searchQuery);
      },
      onError: (error: any) => {
        Alert.alert('Error', error.message || 'Failed to add contact');
      },
    }
  );

  const loadRecentSearches = async () => {
    try {
      const searches = await AsyncStorage.getItem('recentSearches');
      if (searches) {
        setRecentSearches(JSON.parse(searches));
      }
    } catch (error) {
      console.error('Failed to load recent searches:', error);
    }
  };

  const saveRecentSearch = async (query: string) => {
    try {
      const updatedSearches = [
        query,
        ...recentSearches.filter(s => s !== query),
      ].slice(0, 5); // Keep only 5 recent searches
      
      setRecentSearches(updatedSearches);
      await AsyncStorage.setItem('recentSearches', JSON.stringify(updatedSearches));
    } catch (error) {
      console.error('Failed to save recent search:', error);
    }
  };

  const handleSearch = useCallback(
    debounce(async (query: string) => {
      if (!query.trim()) {
        setSearchResults([]);
        setIsSearching(false);
        return;
      }

      setIsSearching(true);
      try {
        const results = await userService.searchUsers(query);
        
        // Mark contacts and add mutual contacts count
        const enrichedResults = results.map(user => ({
          ...user,
          isContact: contacts?.some(c => c.userId === user.id) || false,
          mutualContacts: calculateMutualContacts(user.id),
        }));
        
        setSearchResults(enrichedResults);
        
        // Save to recent searches
        if (query.length > 2) {
          saveRecentSearch(query);
        }
      } catch (error) {
        console.error('Search failed:', error);
        setSearchResults([]);
      } finally {
        setIsSearching(false);
      }
    }, 300),
    [contacts]
  );

  const calculateMutualContacts = (userId: string) => {
    // In a real app, this would come from the API
    return Math.floor(Math.random() * 20);
  };

  const handleAddContact = (user: SearchResult) => {
    Alert.alert(
      'Add Contact',
      `Add ${user.firstName} ${user.lastName} to your contacts?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Add',
          onPress: () => addContactMutation.mutate(user.id),
        },
      ]
    );
  };

  const handleUserPress = (user: SearchResult) => {
    if (user.isContact) {
      navigation.navigate('SendMoney', { recipient: user });
    } else {
      navigation.navigate('UserProfile', { userId: user.id });
    }
  };

  const handleRecentSearchPress = (query: string) => {
    setSearchQuery(query);
    handleSearch(query);
  };

  const clearRecentSearches = () => {
    Alert.alert(
      'Clear Recent Searches',
      'Are you sure you want to clear all recent searches?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear',
          style: 'destructive',
          onPress: async () => {
            setRecentSearches([]);
            await AsyncStorage.removeItem('recentSearches');
          },
        },
      ]
    );
  };

  const renderSearchResult = ({ item }: { item: SearchResult }) => (
    <TouchableOpacity
      style={[styles.resultItem, { backgroundColor: theme.colors.surface }]}
      onPress={() => handleUserPress(item)}
      activeOpacity={0.7}
    >
      <UserAvatar
        user={item}
        size={50}
        style={styles.avatar}
      />
      
      <View style={styles.userInfo}>
        <View style={styles.nameRow}>
          <Text style={[styles.userName, { color: theme.colors.text }]}>
            {item.firstName} {item.lastName}
          </Text>
          {item.verified && (
            <Icon name="verified" size={16} color={theme.colors.primary} />
          )}
        </View>
        
        <Text style={[styles.userHandle, { color: theme.colors.textSecondary }]}>
          @{item.username}
        </Text>
        
        {item.mutualContacts > 0 && (
          <Text style={[styles.mutualContacts, { color: theme.colors.textSecondary }]}>
            {item.mutualContacts} mutual contacts
          </Text>
        )}
      </View>
      
      {item.isContact ? (
        <TouchableOpacity
          style={[styles.actionButton, styles.sendButton, { backgroundColor: theme.colors.primary }]}
          onPress={() => navigation.navigate('SendMoney', { recipient: item })}
        >
          <Text style={styles.actionButtonText}>Send</Text>
        </TouchableOpacity>
      ) : (
        <TouchableOpacity
          style={[styles.actionButton, styles.addButton, { borderColor: theme.colors.primary }]}
          onPress={() => handleAddContact(item)}
        >
          <Icon name="person-add" size={20} color={theme.colors.primary} />
        </TouchableOpacity>
      )}
    </TouchableOpacity>
  );

  const renderRecentSearch = ({ item }: { item: string }) => (
    <TouchableOpacity
      style={[styles.recentSearchItem, { backgroundColor: theme.colors.surface }]}
      onPress={() => handleRecentSearchPress(item)}
    >
      <Icon name="history" size={20} color={theme.colors.textSecondary} />
      <Text style={[styles.recentSearchText, { color: theme.colors.text }]}>
        {item}
      </Text>
    </TouchableOpacity>
  );

  const renderContent = () => {
    if (searchQuery.trim() && isSearching) {
      return (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
          <Text style={[styles.loadingText, { color: theme.colors.textSecondary }]}>
            Searching users...
          </Text>
        </View>
      );
    }

    if (searchQuery.trim() && searchResults.length === 0 && !isSearching) {
      return (
        <EmptyState
          icon="search-off"
          title="No users found"
          message={`We couldn't find any users matching "${searchQuery}"`}
        />
      );
    }

    if (searchQuery.trim() && searchResults.length > 0) {
      return (
        <FlatList
          data={searchResults}
          keyExtractor={(item) => item.id}
          renderItem={renderSearchResult}
          contentContainerStyle={styles.resultsList}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          keyboardShouldPersistTaps="handled"
        />
      );
    }

    if (recentSearches.length > 0) {
      return (
        <View style={styles.recentSearchesContainer}>
          <View style={styles.recentSearchesHeader}>
            <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
              Recent Searches
            </Text>
            <TouchableOpacity onPress={clearRecentSearches}>
              <Text style={[styles.clearButton, { color: theme.colors.primary }]}>
                Clear All
              </Text>
            </TouchableOpacity>
          </View>
          
          <FlatList
            data={recentSearches}
            keyExtractor={(item, index) => `${item}-${index}`}
            renderItem={renderRecentSearch}
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.recentSearchesList}
          />
        </View>
      );
    }

    return (
      <EmptyState
        icon="search"
        title="Search for users"
        message="Find friends, family, or businesses by name, username, email, or phone number"
      />
    );
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardAvoid}
      >
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backButton}
          >
            <Icon name="arrow-back" size={24} color={theme.colors.text} />
          </TouchableOpacity>
          
          <Text style={[styles.title, { color: theme.colors.text }]}>
            Find People
          </Text>
          
          <TouchableOpacity
            onPress={() => navigation.navigate('ScanQR')}
            style={styles.qrButton}
          >
            <Icon name="qr-code-scanner" size={24} color={theme.colors.text} />
          </TouchableOpacity>
        </View>

        <View style={[styles.searchContainer, { backgroundColor: theme.colors.surface }]}>
          <Icon name="search" size={24} color={theme.colors.textSecondary} />
          <TextInput
            style={[styles.searchInput, { color: theme.colors.text }]}
            placeholder="Search by name, @username, email, or phone"
            placeholderTextColor={theme.colors.textSecondary}
            value={searchQuery}
            onChangeText={(text) => {
              setSearchQuery(text);
              handleSearch(text);
            }}
            autoCapitalize="none"
            autoCorrect={false}
            returnKeyType="search"
            clearButtonMode="while-editing"
          />
        </View>

        <View style={styles.content}>
          {renderContent()}
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  keyboardAvoid: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
  },
  qrButton: {
    padding: 8,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 16,
    marginBottom: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 12,
  },
  searchInput: {
    flex: 1,
    marginLeft: 12,
    fontSize: 16,
  },
  content: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
  },
  resultsList: {
    paddingHorizontal: 16,
  },
  resultItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
  },
  avatar: {
    marginRight: 12,
  },
  userInfo: {
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  userName: {
    fontSize: 16,
    fontWeight: '600',
  },
  userHandle: {
    fontSize: 14,
    marginTop: 2,
  },
  mutualContacts: {
    fontSize: 12,
    marginTop: 4,
  },
  actionButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
  },
  sendButton: {
    backgroundColor: '#007AFF',
  },
  addButton: {
    borderWidth: 1,
  },
  actionButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  separator: {
    height: 8,
  },
  recentSearchesContainer: {
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  recentSearchesHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  clearButton: {
    fontSize: 14,
  },
  recentSearchesList: {
    paddingVertical: 8,
  },
  recentSearchItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    marginRight: 8,
    gap: 8,
  },
  recentSearchText: {
    fontSize: 14,
  },
});

export default UserSearchScreen;