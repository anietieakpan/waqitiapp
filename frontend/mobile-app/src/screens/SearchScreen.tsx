import React, { useState, useEffect } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  Alert,
  RefreshControl,
} from 'react-native';
import {
  Text,
  TextInput,
  useTheme,
  Surface,
  Avatar,
  IconButton,
  Chip,
  ActivityIndicator,
  Button,
  Snackbar,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useDebounce } from '../hooks/useDebounce';
import SearchService, { SearchResult } from '../services/SearchService';
import { showToast } from '../utils/toast';
import HapticFeedback from 'react-native-haptic-feedback';

/**
 * Search Screen - Global search for contacts, transactions, and merchants
 * Now with real API integration instead of mock data
 */
const SearchScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedFilter, setSelectedFilter] = useState<string>('all');
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);

  const debouncedQuery = useDebounce(query, 300);

  const filters = [
    { id: 'all', label: 'All', icon: 'magnify' },
    { id: 'contacts', label: 'Contacts', icon: 'account-group' },
    { id: 'transactions', label: 'Transactions', icon: 'history' },
    { id: 'merchants', label: 'Merchants', icon: 'store' },
  ];

  // Load recent searches on component mount
  useEffect(() => {
    loadRecentSearches();
  }, []);

  useEffect(() => {
    if (debouncedQuery.length > 0) {
      performSearch(debouncedQuery);
    } else {
      setResults([]);
      setSuggestions([]);
      setShowSuggestions(false);
    }
  }, [debouncedQuery, selectedFilter]);

  const loadRecentSearches = async () => {
    try {
      const recent = await SearchService.getRecentSearches(10);
      setRecentSearches(recent);
    } catch (error) {
      console.error('Failed to load recent searches:', error);
    }
  };

  const performSearch = async (searchQuery: string) => {
    if (searchQuery.trim().length < 2) {
      setResults([]);
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    setLoading(true);
    setError(null);
    
    try {
      // Get search suggestions for partial queries
      if (searchQuery.length >= 2 && searchQuery.length < 4) {
        const searchSuggestions = await SearchService.getSearchSuggestions(searchQuery);
        setSuggestions(searchSuggestions);
        setShowSuggestions(searchSuggestions.length > 0);
      } else {
        setSuggestions([]);
        setShowSuggestions(false);
      }

      // Only perform full search for queries of 3+ characters
      if (searchQuery.length >= 3) {
        // Determine search types based on filter
        let searchTypes: ('contact' | 'transaction' | 'merchant' | 'user')[] | undefined;
        if (selectedFilter !== 'all') {
          const filterMap = {
            contacts: ['contact', 'user'] as const,
            transactions: ['transaction'] as const,
            merchants: ['merchant'] as const,
          };
          searchTypes = filterMap[selectedFilter as keyof typeof filterMap];
        }

        // Perform the search
        const response = await SearchService.globalSearch({
          query: searchQuery,
          types: searchTypes,
          limit: 20,
          offset: 0,
        });

        setResults(response.results);
        setHasMore(response.hasMore);
        setShowSuggestions(false);
      }
      
    } catch (error: any) {
      console.error('Search error:', error);
      setError(error.message || 'Search failed');
      showToast('Search failed. Please try again.', 'error');
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  const handleResultPress = (result: SearchResult) => {
    try {
      HapticFeedback.trigger('selection');
      
      switch (result.type) {
        case 'contact':
        case 'user':
          navigation.navigate('SendMoney', { 
            recipientId: result.id,
            recipientName: result.title,
            recipientAvatar: result.avatar,
          } as never);
          break;
        case 'transaction':
          navigation.navigate('TransactionDetails', { 
            transactionId: result.id 
          } as never);
          break;
        case 'merchant':
          navigation.navigate('MerchantPayment', { 
            merchantId: result.id,
            merchantName: result.title,
            merchantAvatar: result.avatar,
          } as never);
          break;
        default:
          console.warn('Unknown result type:', result.type);
          showToast('Unable to open this item', 'warning');
      }
    } catch (error) {
      console.error('Navigation error:', error);
      showToast('Unable to open this item', 'error');
    }
  };

  const handleRecentSearchPress = (searchQuery: string) => {
    HapticFeedback.trigger('selection');
    setQuery(searchQuery);
    setShowSuggestions(false);
  };

  const handleSuggestionPress = (suggestion: string) => {
    HapticFeedback.trigger('selection');
    setQuery(suggestion);
    setShowSuggestions(false);
  };

  const clearRecentSearches = async () => {
    try {
      await SearchService.clearRecentSearches();
      setRecentSearches([]);
      showToast('Recent searches cleared', 'success');
    } catch (error) {
      showToast('Failed to clear recent searches', 'error');
    }
  };

  const loadMoreResults = async () => {
    if (!hasMore || loading || !query || query.length < 3) return;

    setLoading(true);
    try {
      let searchTypes: ('contact' | 'transaction' | 'merchant' | 'user')[] | undefined;
      if (selectedFilter !== 'all') {
        const filterMap = {
          contacts: ['contact', 'user'] as const,
          transactions: ['transaction'] as const,
          merchants: ['merchant'] as const,
        };
        searchTypes = filterMap[selectedFilter as keyof typeof filterMap];
      }

      const response = await SearchService.globalSearch({
        query,
        types: searchTypes,
        limit: 20,
        offset: results.length,
      });

      setResults(prev => [...prev, ...response.results]);
      setHasMore(response.hasMore);
    } catch (error: any) {
      showToast('Failed to load more results', 'error');
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadRecentSearches();
    if (query.length >= 3) {
      await performSearch(query);
    }
    setRefreshing(false);
  };

  const formatAmount = (amount?: number, currency?: string) => {
    if (!amount) return '';
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
    }).format(amount);
  };

  const getResultIcon = (type: string) => {
    switch (type) {
      case 'contact':
        return 'account';
      case 'user':
        return 'account-circle';
      case 'transaction':
        return 'swap-horizontal';
      case 'merchant':
        return 'store';
      default:
        return 'magnify';
    }
  };

  const getResultColor = (type: string) => {
    switch (type) {
      case 'contact':
      case 'user':
        return theme.colors.primary;
      case 'transaction':
        return theme.colors.secondary;
      case 'merchant':
        return theme.colors.tertiary;
      default:
        return theme.colors.onSurface;
    }
  };

  const renderSearchResult = ({ item }: { item: SearchResult }) => {
    const getAvatar = () => {
      if (item.avatar) {
        return <Avatar.Image size={48} source={{ uri: item.avatar }} />;
      }
      if (item.type === 'contact' || item.type === 'user') {
        return (
          <Avatar.Text
            size={48}
            label={item.title.split(' ').map(n => n[0]).join('')}
            style={{ backgroundColor: getResultColor(item.type) }}
          />
        );
      }
      return (
        <Avatar.Icon
          size={48}
          icon={getResultIcon(item.type)}
          style={{ backgroundColor: theme.colors.surfaceVariant }}
        />
      );
    };

    return (
      <TouchableOpacity
        style={[styles.resultItem, { backgroundColor: theme.colors.surface }]}
        onPress={() => handleResultPress(item)}
        activeOpacity={0.7}
      >
        {getAvatar()}
        <View style={styles.resultContent}>
          <Text style={[styles.resultTitle, { color: theme.colors.onSurface }]} numberOfLines={1}>
            {item.title}
          </Text>
          <Text style={[styles.resultSubtitle, { color: theme.colors.onSurfaceVariant }]} numberOfLines={2}>
            {item.subtitle}
          </Text>
          {item.metadata && (
            <View style={styles.resultMetadata}>
              {item.type === 'transaction' && item.metadata.status && (
                <Chip
                  mode="outlined"
                  compact
                  style={styles.statusChip}
                  textStyle={styles.statusChipText}
                >
                  {item.metadata.status}
                </Chip>
              )}
              {item.type === 'merchant' && item.metadata.isVerified && (
                <Icon name="check-decagram" size={16} color={theme.colors.primary} />
              )}
            </View>
          )}
        </View>
        <View style={styles.resultActions}>
          {item.amount && (
            <Text style={[
              styles.resultAmount,
              {
                color: item.type === 'transaction' && item.amount > 0
                  ? theme.colors.primary
                  : theme.colors.onSurface
              }
            ]}>
              {formatAmount(item.amount, item.currency)}
            </Text>
          )}
          <Icon 
            name="chevron-right" 
            size={20} 
            color={theme.colors.onSurfaceVariant} 
          />
        </View>
      </TouchableOpacity>
    );
  };

  const renderSuggestion = ({ item }: { item: string }) => (
    <TouchableOpacity
      style={[styles.suggestionItem, { backgroundColor: theme.colors.surface }]}
      onPress={() => handleSuggestionPress(item)}
    >
      <Icon name="magnify" size={16} color={theme.colors.onSurfaceVariant} />
      <Text style={[styles.suggestionText, { color: theme.colors.onSurface }]}>{item}</Text>
    </TouchableOpacity>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyState}>
      <Icon name="magnify" size={64} color={theme.colors.onSurfaceVariant} />
      <Text style={[styles.emptyTitle, { color: theme.colors.onSurface }]}>
        {query ? 'No results found' : 'Start typing to search'}
      </Text>
      <Text style={[styles.emptySubtitle, { color: theme.colors.onSurfaceVariant }]}>
        {query
          ? 'Try adjusting your search or filters'
          : 'Search for contacts, transactions, or merchants'
        }
      </Text>
      {query && (
        <Button
          mode="outlined"
          onPress={() => setQuery('')}
          style={styles.clearButton}
        >
          Clear Search
        </Button>
      )}
    </View>
  );

  const renderLoadingState = () => (
    <View style={styles.loadingContainer}>
      <ActivityIndicator size="large" color={theme.colors.primary} />
      <Text style={[styles.loadingText, { color: theme.colors.onSurface }]}>
        Searching...
      </Text>
    </View>
  );

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.header, { backgroundColor: theme.colors.surface }]}>
        <View style={styles.searchContainer}>
          <TextInput
            value={query}
            onChangeText={(text) => {
              setQuery(text);
              setError(null);
            }}
            placeholder="Search contacts, transactions, merchants..."
            mode="outlined"
            left={<TextInput.Icon icon="magnify" />}
            right={
              query ? (
                <TextInput.Icon
                  icon="close"
                  onPress={() => {
                    setQuery('');
                    setResults([]);
                    setSuggestions([]);
                    setShowSuggestions(false);
                  }}
                />
              ) : undefined
            }
            style={styles.searchInput}
            autoFocus
          />
          <IconButton
            icon="arrow-left"
            size={24}
            onPress={() => navigation.goBack()}
            style={styles.backButton}
          />
        </View>

        {/* Filter Chips */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filtersContainer}
        >
          {filters.map((filter) => (
            <Chip
              key={filter.id}
              mode={selectedFilter === filter.id ? 'flat' : 'outlined'}
              selected={selectedFilter === filter.id}
              onPress={() => {
                setSelectedFilter(filter.id);
                HapticFeedback.trigger('selection');
              }}
              icon={filter.icon}
              style={styles.filterChip}
            >
              {filter.label}
            </Chip>
          ))}
        </ScrollView>
      </View>

      {/* Error State */}
      {error && (
        <Surface style={styles.errorCard} elevation={1}>
          <Icon name="alert-circle" size={24} color={theme.colors.error} />
          <Text style={[styles.errorText, { color: theme.colors.error }]}>{error}</Text>
          <Button
            mode="outlined"
            onPress={() => performSearch(query)}
            compact
          >
            Retry
          </Button>
        </Surface>
      )}

      {/* Recent Searches */}
      {query.length === 0 && recentSearches.length > 0 && (
        <Surface style={styles.recentSearchesCard} elevation={1}>
          <View style={styles.recentHeader}>
            <Text style={[styles.recentTitle, { color: theme.colors.onSurface }]}>
              Recent Searches
            </Text>
            <Button
              mode="text"
              compact
              onPress={clearRecentSearches}
            >
              Clear
            </Button>
          </View>
          <View style={styles.recentSearches}>
            {recentSearches.map((search, index) => (
              <TouchableOpacity
                key={index}
                style={styles.recentItem}
                onPress={() => handleRecentSearchPress(search)}
              >
                <Icon name="history" size={16} color={theme.colors.onSurfaceVariant} />
                <Text style={[styles.recentText, { color: theme.colors.onSurface }]}>{search}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </Surface>
      )}

      {/* Search Suggestions */}
      {showSuggestions && suggestions.length > 0 && (
        <Surface style={styles.suggestionsCard} elevation={1}>
          <FlatList
            data={suggestions}
            renderItem={renderSuggestion}
            keyExtractor={(item, index) => `suggestion-${index}`}
            showsVerticalScrollIndicator={false}
          />
        </Surface>
      )}

      {/* Search Results */}
      <View style={styles.resultsContainer}>
        {loading && results.length === 0 ? (
          renderLoadingState()
        ) : results.length > 0 ? (
          <FlatList
            data={results}
            renderItem={renderSearchResult}
            keyExtractor={(item) => `${item.type}-${item.id}`}
            showsVerticalScrollIndicator={false}
            contentContainerStyle={styles.resultsList}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={onRefresh}
                colors={[theme.colors.primary]}
              />
            }
            onEndReached={loadMoreResults}
            onEndReachedThreshold={0.3}
            ListFooterComponent={
              hasMore ? (
                <View style={styles.loadMoreContainer}>
                  <ActivityIndicator size="small" color={theme.colors.primary} />
                </View>
              ) : null
            }
          />
        ) : (
          !showSuggestions && renderEmptyState()
        )}
      </View>

      {/* Quick Actions */}
      {query.length === 0 && !showSuggestions && (
        <Surface style={styles.quickActionsCard} elevation={1}>
          <Text style={[styles.quickActionsTitle, { color: theme.colors.onSurface }]}>
            Quick Actions
          </Text>
          <View style={styles.quickActions}>
            <TouchableOpacity
              style={styles.quickAction}
              onPress={() => {
                HapticFeedback.trigger('selection');
                navigation.navigate('QRScanner' as never);
              }}
            >
              <Icon name="qrcode-scan" size={24} color={theme.colors.primary} />
              <Text style={[styles.quickActionText, { color: theme.colors.onSurface }]}>
                Scan QR
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.quickAction}
              onPress={() => {
                HapticFeedback.trigger('selection');
                navigation.navigate('Contacts' as never);
              }}
            >
              <Icon name="account-plus" size={24} color={theme.colors.primary} />
              <Text style={[styles.quickActionText, { color: theme.colors.onSurface }]}>
                Add Contact
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.quickAction}
              onPress={() => {
                HapticFeedback.trigger('selection');
                navigation.navigate('NearbyMerchants' as never);
              }}
            >
              <Icon name="map-marker" size={24} color={theme.colors.primary} />
              <Text style={[styles.quickActionText, { color: theme.colors.onSurface }]}>
                Find Nearby
              </Text>
            </TouchableOpacity>
          </View>
        </Surface>
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 16,
    paddingTop: 16,
    elevation: 2,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  searchInput: {
    flex: 1,
    marginRight: 8,
  },
  backButton: {
    margin: 0,
  },
  filtersContainer: {
    paddingBottom: 16,
    gap: 8,
  },
  filterChip: {
    marginRight: 8,
  },
  errorCard: {
    margin: 16,
    padding: 16,
    borderRadius: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  errorText: {
    flex: 1,
    fontSize: 14,
  },
  recentSearchesCard: {
    margin: 16,
    padding: 16,
    borderRadius: 12,
  },
  recentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  recentTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  recentSearches: {
    gap: 12,
  },
  recentItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 4,
  },
  recentText: {
    marginLeft: 8,
    fontSize: 14,
  },
  suggestionsCard: {
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 12,
    maxHeight: 200,
  },
  suggestionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    gap: 8,
  },
  suggestionText: {
    fontSize: 14,
  },
  resultsContainer: {
    flex: 1,
    marginHorizontal: 16,
  },
  resultsList: {
    paddingVertical: 8,
  },
  resultItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
    elevation: 1,
  },
  resultContent: {
    flex: 1,
    marginLeft: 12,
  },
  resultTitle: {
    fontSize: 16,
    fontWeight: '500',
  },
  resultSubtitle: {
    fontSize: 14,
    marginTop: 2,
    lineHeight: 18,
  },
  resultMetadata: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
    gap: 8,
  },
  statusChip: {
    height: 24,
  },
  statusChipText: {
    fontSize: 10,
  },
  resultActions: {
    alignItems: 'flex-end',
    gap: 4,
  },
  resultAmount: {
    fontSize: 16,
    fontWeight: 'bold',
    marginRight: 8,
  },
  loadMoreContainer: {
    padding: 16,
    alignItems: 'center',
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '500',
    marginTop: 16,
    textAlign: 'center',
  },
  emptySubtitle: {
    fontSize: 14,
    marginTop: 8,
    textAlign: 'center',
    lineHeight: 20,
  },
  clearButton: {
    marginTop: 16,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  loadingText: {
    fontSize: 16,
  },
  quickActionsCard: {
    margin: 16,
    padding: 16,
    borderRadius: 12,
  },
  quickActionsTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
  },
  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  quickAction: {
    alignItems: 'center',
    padding: 12,
    flex: 1,
  },
  quickActionText: {
    fontSize: 12,
    marginTop: 8,
    textAlign: 'center',
  },
});

export default SearchScreen;