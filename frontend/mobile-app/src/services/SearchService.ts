/**
 * SearchService - Comprehensive search service for the mobile app
 * Handles global search across contacts, transactions, merchants, and users
 */

import ApiService from './ApiService';

export interface SearchResult {
  id: string;
  type: 'contact' | 'transaction' | 'merchant' | 'user';
  title: string;
  subtitle: string;
  avatar?: string;
  amount?: number;
  currency?: string;
  date?: string;
  metadata?: Record<string, any>;
}

export interface SearchRequest {
  query: string;
  types?: ('contact' | 'transaction' | 'merchant' | 'user')[];
  limit?: number;
  offset?: number;
  filters?: SearchFilters;
}

export interface SearchFilters {
  dateRange?: {
    start: string;
    end: string;
  };
  amountRange?: {
    min: number;
    max: number;
  };
  currency?: string;
  status?: string;
  location?: {
    latitude: number;
    longitude: number;
    radius: number; // in meters
  };
}

export interface SearchResponse {
  results: SearchResult[];
  totalCount: number;
  hasMore: boolean;
  suggestions?: string[];
}

export interface RecentSearchResponse {
  searches: string[];
  totalCount: number;
}

class SearchService {
  private static instance: SearchService;
  private apiService = ApiService.getInstance();

  static getInstance(): SearchService {
    if (!SearchService.instance) {
      SearchService.instance = new SearchService();
    }
    return SearchService.instance;
  }

  /**
   * Perform global search across all data types
   */
  async globalSearch(request: SearchRequest): Promise<SearchResponse> {
    try {
      const params = {
        q: request.query,
        types: request.types?.join(',') || 'contact,transaction,merchant,user',
        limit: request.limit || 20,
        offset: request.offset || 0,
        ...this.buildFilterParams(request.filters),
      };

      const response = await this.apiService.get<SearchResponse>('/search/global', params);
      
      // Save search query for recent searches
      if (request.query.trim()) {
        await this.saveRecentSearch(request.query.trim());
      }

      return response;
    } catch (error: any) {
      console.error('Global search error:', error);
      throw new Error(error.message || 'Search failed');
    }
  }

  /**
   * Search for contacts
   */
  async searchContacts(query: string, limit: number = 20): Promise<SearchResult[]> {
    try {
      const response = await this.apiService.get<{ contacts: any[] }>('/contacts/search', {
        q: query,
        limit,
      });

      return response.contacts.map(contact => ({
        id: contact.id,
        type: 'contact' as const,
        title: contact.name || `${contact.firstName} ${contact.lastName}`,
        subtitle: contact.email || contact.phoneNumber || contact.username,
        avatar: contact.avatar,
        metadata: {
          isWaqitiUser: contact.isWaqitiUser,
          isBlocked: contact.isBlocked,
          isFavorite: contact.isFavorite,
        },
      }));
    } catch (error: any) {
      console.error('Contact search error:', error);
      throw new Error(error.message || 'Contact search failed');
    }
  }

  /**
   * Search for transactions
   */
  async searchTransactions(
    query: string, 
    filters?: Omit<SearchFilters, 'location'>,
    limit: number = 20
  ): Promise<SearchResult[]> {
    try {
      const params = {
        q: query,
        limit,
        ...this.buildFilterParams(filters),
      };

      const response = await this.apiService.get<{ transactions: any[] }>('/transactions/search', params);

      return response.transactions.map(transaction => ({
        id: transaction.id,
        type: 'transaction' as const,
        title: transaction.description || `${transaction.type} transaction`,
        subtitle: this.formatTransactionSubtitle(transaction),
        amount: transaction.amount,
        currency: transaction.currency,
        date: transaction.createdAt,
        metadata: {
          status: transaction.status,
          type: transaction.type,
          counterparty: transaction.counterparty,
        },
      }));
    } catch (error: any) {
      console.error('Transaction search error:', error);
      throw new Error(error.message || 'Transaction search failed');
    }
  }

  /**
   * Search for merchants
   */
  async searchMerchants(query: string, location?: SearchFilters['location'], limit: number = 20): Promise<SearchResult[]> {
    try {
      const params = {
        q: query,
        limit,
        ...(location && {
          lat: location.latitude,
          lng: location.longitude,
          radius: location.radius,
        }),
      };

      const response = await this.apiService.get<{ merchants: any[] }>('/merchants/search', params);

      return response.merchants.map(merchant => ({
        id: merchant.id,
        type: 'merchant' as const,
        title: merchant.name || merchant.businessName,
        subtitle: this.formatMerchantSubtitle(merchant),
        avatar: merchant.logo,
        metadata: {
          category: merchant.category,
          rating: merchant.rating,
          distance: merchant.distance,
          isVerified: merchant.isVerified,
          acceptsWaqiti: merchant.acceptsWaqiti,
        },
      }));
    } catch (error: any) {
      console.error('Merchant search error:', error);
      throw new Error(error.message || 'Merchant search failed');
    }
  }

  /**
   * Search for users
   */
  async searchUsers(query: string, limit: number = 20): Promise<SearchResult[]> {
    try {
      const response = await this.apiService.get<{ users: any[] }>('/users/search', {
        q: query,
        limit,
      });

      return response.users.map(user => ({
        id: user.id,
        type: 'user' as const,
        title: user.displayName || `${user.firstName} ${user.lastName}`,
        subtitle: user.username ? `@${user.username}` : user.email,
        avatar: user.profilePicture,
        metadata: {
          isVerified: user.isVerified,
          isBlocked: user.isBlocked,
          mutualConnections: user.mutualConnections,
        },
      }));
    } catch (error: any) {
      console.error('User search error:', error);
      throw new Error(error.message || 'User search failed');
    }
  }

  /**
   * Get search suggestions based on partial query
   */
  async getSearchSuggestions(query: string): Promise<string[]> {
    try {
      if (query.length < 2) {
        return [];
      }

      const response = await this.apiService.get<{ suggestions: string[] }>('/search/suggestions', {
        q: query,
        limit: 10,
      });

      return response.suggestions;
    } catch (error: any) {
      console.error('Search suggestions error:', error);
      return [];
    }
  }

  /**
   * Get recent searches for the user
   */
  async getRecentSearches(limit: number = 10): Promise<string[]> {
    try {
      const response = await this.apiService.get<RecentSearchResponse>('/search/recent', {
        limit,
      });

      return response.searches;
    } catch (error: any) {
      console.error('Recent searches error:', error);
      // Fallback to local storage
      return this.getLocalRecentSearches();
    }
  }

  /**
   * Clear recent searches
   */
  async clearRecentSearches(): Promise<void> {
    try {
      await this.apiService.delete('/search/recent');
      
      // Also clear local storage
      await this.clearLocalRecentSearches();
    } catch (error: any) {
      console.error('Clear recent searches error:', error);
      // Fallback to clearing local storage only
      await this.clearLocalRecentSearches();
    }
  }

  /**
   * Save a search query to recent searches
   */
  private async saveRecentSearch(query: string): Promise<void> {
    try {
      await this.apiService.post('/search/recent', {
        query: query.trim(),
      });

      // Also save to local storage as backup
      await this.saveLocalRecentSearch(query);
    } catch (error: any) {
      console.error('Save recent search error:', error);
      // Fallback to local storage only
      await this.saveLocalRecentSearch(query);
    }
  }

  /**
   * Build filter parameters for API requests
   */
  private buildFilterParams(filters?: SearchFilters): Record<string, any> {
    const params: Record<string, any> = {};

    if (filters?.dateRange) {
      params.startDate = filters.dateRange.start;
      params.endDate = filters.dateRange.end;
    }

    if (filters?.amountRange) {
      params.minAmount = filters.amountRange.min;
      params.maxAmount = filters.amountRange.max;
    }

    if (filters?.currency) {
      params.currency = filters.currency;
    }

    if (filters?.status) {
      params.status = filters.status;
    }

    if (filters?.location) {
      params.lat = filters.location.latitude;
      params.lng = filters.location.longitude;
      params.radius = filters.location.radius;
    }

    return params;
  }

  /**
   * Format transaction subtitle for display
   */
  private formatTransactionSubtitle(transaction: any): string {
    const parts = [];
    
    if (transaction.counterparty?.name) {
      parts.push(transaction.counterparty.name);
    }
    
    if (transaction.status) {
      parts.push(transaction.status.toLowerCase());
    }
    
    if (transaction.createdAt) {
      const date = new Date(transaction.createdAt);
      parts.push(this.formatRelativeDate(date));
    }

    return parts.join(' • ');
  }

  /**
   * Format merchant subtitle for display
   */
  private formatMerchantSubtitle(merchant: any): string {
    const parts = [];
    
    if (merchant.address) {
      parts.push(merchant.address);
    }
    
    if (merchant.category) {
      parts.push(merchant.category);
    }
    
    if (merchant.distance !== undefined) {
      const distance = merchant.distance < 1000 
        ? `${Math.round(merchant.distance)}m away`
        : `${(merchant.distance / 1000).toFixed(1)}km away`;
      parts.push(distance);
    }

    return parts.join(' • ');
  }

  /**
   * Format relative date for display
   */
  private formatRelativeDate(date: Date): string {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMinutes = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMinutes < 1) {
      return 'Just now';
    } else if (diffMinutes < 60) {
      return `${diffMinutes}m ago`;
    } else if (diffHours < 24) {
      return `${diffHours}h ago`;
    } else if (diffDays < 7) {
      return `${diffDays}d ago`;
    } else {
      return date.toLocaleDateString();
    }
  }

  /**
   * Local storage fallback methods
   */
  private async getLocalRecentSearches(): Promise<string[]> {
    try {
      const AsyncStorage = require('@react-native-async-storage/async-storage').default;
      const searches = await AsyncStorage.getItem('@waqiti_recent_searches');
      return searches ? JSON.parse(searches) : [];
    } catch (error) {
      return [];
    }
  }

  private async saveLocalRecentSearch(query: string): Promise<void> {
    try {
      const AsyncStorage = require('@react-native-async-storage/async-storage').default;
      const searches = await this.getLocalRecentSearches();
      
      // Remove if already exists
      const filtered = searches.filter(s => s !== query);
      
      // Add to beginning
      const updated = [query, ...filtered].slice(0, 20);
      
      await AsyncStorage.setItem('@waqiti_recent_searches', JSON.stringify(updated));
    } catch (error) {
      console.error('Failed to save local recent search:', error);
    }
  }

  private async clearLocalRecentSearches(): Promise<void> {
    try {
      const AsyncStorage = require('@react-native-async-storage/async-storage').default;
      await AsyncStorage.removeItem('@waqiti_recent_searches');
    } catch (error) {
      console.error('Failed to clear local recent searches:', error);
    }
  }
}

export default SearchService.getInstance();