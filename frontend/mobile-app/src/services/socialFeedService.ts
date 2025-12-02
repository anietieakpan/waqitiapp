/**
 * Social Feed Service
 * Handles social payment feed functionality similar to Venmo
 */
import { apiClient } from './apiClient';

export interface SocialTransaction {
  id: string;
  sender: {
    id: string;
    name: string;
    username: string;
    avatar?: string;
  };
  recipient: {
    id: string;
    name: string;
    username: string;
    avatar?: string;
  };
  amount: number;
  description: string;
  emoji?: string;
  timestamp: Date;
  isPublic: boolean;
  likes: number;
  comments: number;
  isLiked: boolean;
  privacy: 'public' | 'friends' | 'private';
}

export interface Comment {
  id: string;
  userId: string;
  username: string;
  text: string;
  timestamp: Date;
}

export interface SocialFeedResponse {
  transactions: SocialTransaction[];
  hasMore: boolean;
  nextCursor?: string;
}

export interface UserProfile {
  id: string;
  name: string;
  username: string;
  avatar?: string;
  bio?: string;
  friendsCount: number;
  transactionsCount: number;
  isFollowing: boolean;
  isFollowedBy: boolean;
  joinedDate: Date;
}

export interface PrivacySettings {
  defaultPrivacy: 'public' | 'friends' | 'private';
  allowComments: boolean;
  allowLikes: boolean;
  showInSearch: boolean;
  allowDirectMessages: boolean;
}

class SocialFeedService {
  private baseURL = '/api/v1/social';

  /**
   * Get social feed with filtering options
   */
  async getFeed(
    filter: 'all' | 'friends' | 'trending' = 'all',
    cursor?: string,
    limit: number = 20
  ): Promise<SocialTransaction[]> {
    try {
      const params = new URLSearchParams({
        filter,
        limit: limit.toString(),
      });

      if (cursor) {
        params.append('cursor', cursor);
      }

      const response = await apiClient.get(`${this.baseURL}/feed?${params}`);
      
      // Transform response data
      const transactions = response.data.transactions.map((tx: any) => ({
        ...tx,
        timestamp: new Date(tx.timestamp),
      }));

      return transactions;
    } catch (error) {
      console.error('Error fetching social feed:', error);
      throw new Error('Failed to load social feed');
    }
  }

  /**
   * Get user's social profile
   */
  async getUserProfile(userId: string): Promise<UserProfile> {
    try {
      const response = await apiClient.get(`${this.baseURL}/users/${userId}/profile`);
      return {
        ...response.data,
        joinedDate: new Date(response.data.joinedDate),
      };
    } catch (error) {
      console.error('Error fetching user profile:', error);
      throw new Error('Failed to load user profile');
    }
  }

  /**
   * Toggle like on a transaction
   */
  async toggleLike(transactionId: string): Promise<{ isLiked: boolean; likes: number }> {
    try {
      const response = await apiClient.post(`${this.baseURL}/transactions/${transactionId}/like`);
      return response.data;
    } catch (error) {
      console.error('Error toggling like:', error);
      throw new Error('Failed to update like');
    }
  }

  /**
   * Add comment to a transaction
   */
  async addComment(transactionId: string, text: string): Promise<Comment> {
    try {
      const response = await apiClient.post(`${this.baseURL}/transactions/${transactionId}/comments`, {
        text: text.trim(),
      });
      
      return {
        ...response.data,
        timestamp: new Date(response.data.timestamp),
      };
    } catch (error) {
      console.error('Error adding comment:', error);
      throw new Error('Failed to add comment');
    }
  }

  /**
   * Get comments for a transaction
   */
  async getComments(transactionId: string): Promise<Comment[]> {
    try {
      const response = await apiClient.get(`${this.baseURL}/transactions/${transactionId}/comments`);
      
      return response.data.map((comment: any) => ({
        ...comment,
        timestamp: new Date(comment.timestamp),
      }));
    } catch (error) {
      console.error('Error fetching comments:', error);
      throw new Error('Failed to load comments');
    }
  }

  /**
   * Follow or unfollow a user
   */
  async toggleFollow(userId: string): Promise<{ isFollowing: boolean }> {
    try {
      const response = await apiClient.post(`${this.baseURL}/users/${userId}/follow`);
      return response.data;
    } catch (error) {
      console.error('Error toggling follow:', error);
      throw new Error('Failed to update follow status');
    }
  }

  /**
   * Search for users
   */
  async searchUsers(query: string, limit: number = 20): Promise<UserProfile[]> {
    try {
      const params = new URLSearchParams({
        q: query,
        limit: limit.toString(),
      });

      const response = await apiClient.get(`${this.baseURL}/users/search?${params}`);
      
      return response.data.map((user: any) => ({
        ...user,
        joinedDate: new Date(user.joinedDate),
      }));
    } catch (error) {
      console.error('Error searching users:', error);
      throw new Error('Failed to search users');
    }
  }

  /**
   * Get user's friends/following list
   */
  async getUserFriends(userId: string): Promise<UserProfile[]> {
    try {
      const response = await apiClient.get(`${this.baseURL}/users/${userId}/friends`);
      
      return response.data.map((user: any) => ({
        ...user,
        joinedDate: new Date(user.joinedDate),
      }));
    } catch (error) {
      console.error('Error fetching user friends:', error);
      throw new Error('Failed to load friends');
    }
  }

  /**
   * Get user's followers list
   */
  async getUserFollowers(userId: string): Promise<UserProfile[]> {
    try {
      const response = await apiClient.get(`${this.baseURL}/users/${userId}/followers`);
      
      return response.data.map((user: any) => ({
        ...user,
        joinedDate: new Date(user.joinedDate),
      }));
    } catch (error) {
      console.error('Error fetching user followers:', error);
      throw new Error('Failed to load followers');
    }
  }

  /**
   * Update user's privacy settings
   */
  async updatePrivacySettings(settings: Partial<PrivacySettings>): Promise<PrivacySettings> {
    try {
      const response = await apiClient.put(`${this.baseURL}/settings/privacy`, settings);
      return response.data;
    } catch (error) {
      console.error('Error updating privacy settings:', error);
      throw new Error('Failed to update privacy settings');
    }
  }

  /**
   * Get user's privacy settings
   */
  async getPrivacySettings(): Promise<PrivacySettings> {
    try {
      const response = await apiClient.get(`${this.baseURL}/settings/privacy`);
      return response.data;
    } catch (error) {
      console.error('Error fetching privacy settings:', error);
      throw new Error('Failed to load privacy settings');
    }
  }

  /**
   * Make transaction public/private
   */
  async updateTransactionPrivacy(
    transactionId: string,
    privacy: 'public' | 'friends' | 'private',
    description?: string,
    emoji?: string
  ): Promise<void> {
    try {
      await apiClient.put(`${this.baseURL}/transactions/${transactionId}/privacy`, {
        privacy,
        description,
        emoji,
      });
    } catch (error) {
      console.error('Error updating transaction privacy:', error);
      throw new Error('Failed to update transaction privacy');
    }
  }

  /**
   * Report inappropriate content
   */
  async reportContent(
    type: 'transaction' | 'comment' | 'user',
    contentId: string,
    reason: string,
    details?: string
  ): Promise<void> {
    try {
      await apiClient.post(`${this.baseURL}/report`, {
        type,
        contentId,
        reason,
        details,
      });
    } catch (error) {
      console.error('Error reporting content:', error);
      throw new Error('Failed to report content');
    }
  }

  /**
   * Block or unblock a user
   */
  async toggleBlock(userId: string): Promise<{ isBlocked: boolean }> {
    try {
      const response = await apiClient.post(`${this.baseURL}/users/${userId}/block`);
      return response.data;
    } catch (error) {
      console.error('Error toggling block:', error);
      throw new Error('Failed to update block status');
    }
  }

  /**
   * Get trending hashtags or topics
   */
  async getTrendingTopics(): Promise<string[]> {
    try {
      const response = await apiClient.get(`${this.baseURL}/trending`);
      return response.data.topics;
    } catch (error) {
      console.error('Error fetching trending topics:', error);
      throw new Error('Failed to load trending topics');
    }
  }

  /**
   * Get friend suggestions
   */
  async getFriendSuggestions(limit: number = 10): Promise<UserProfile[]> {
    try {
      const params = new URLSearchParams({
        limit: limit.toString(),
      });

      const response = await apiClient.get(`${this.baseURL}/suggestions/friends?${params}`);
      
      return response.data.map((user: any) => ({
        ...user,
        joinedDate: new Date(user.joinedDate),
      }));
    } catch (error) {
      console.error('Error fetching friend suggestions:', error);
      throw new Error('Failed to load friend suggestions');
    }
  }

  /**
   * Get user's public transaction history
   */
  async getUserPublicTransactions(
    userId: string,
    cursor?: string,
    limit: number = 20
  ): Promise<SocialTransaction[]> {
    try {
      const params = new URLSearchParams({
        limit: limit.toString(),
      });

      if (cursor) {
        params.append('cursor', cursor);
      }

      const response = await apiClient.get(`${this.baseURL}/users/${userId}/transactions?${params}`);
      
      return response.data.transactions.map((tx: any) => ({
        ...tx,
        timestamp: new Date(tx.timestamp),
      }));
    } catch (error) {
      console.error('Error fetching user transactions:', error);
      throw new Error('Failed to load user transactions');
    }
  }

  /**
   * Send direct message to a user
   */
  async sendDirectMessage(userId: string, message: string): Promise<void> {
    try {
      await apiClient.post(`${this.baseURL}/users/${userId}/message`, {
        message: message.trim(),
      });
    } catch (error) {
      console.error('Error sending direct message:', error);
      throw new Error('Failed to send message');
    }
  }

  /**
   * Get notification preferences for social features
   */
  async getNotificationSettings(): Promise<{
    likes: boolean;
    comments: boolean;
    follows: boolean;
    directMessages: boolean;
  }> {
    try {
      const response = await apiClient.get(`${this.baseURL}/settings/notifications`);
      return response.data;
    } catch (error) {
      console.error('Error fetching notification settings:', error);
      throw new Error('Failed to load notification settings');
    }
  }

  /**
   * Update notification preferences for social features
   */
  async updateNotificationSettings(settings: {
    likes?: boolean;
    comments?: boolean;
    follows?: boolean;
    directMessages?: boolean;
  }): Promise<void> {
    try {
      await apiClient.put(`${this.baseURL}/settings/notifications`, settings);
    } catch (error) {
      console.error('Error updating notification settings:', error);
      throw new Error('Failed to update notification settings');
    }
  }
}

export const socialFeedService = new SocialFeedService();