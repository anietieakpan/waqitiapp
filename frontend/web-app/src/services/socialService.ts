import axios from 'axios';
import { apiClient } from './apiClient';

// Types for Social Feed API responses
export interface SocialFeedItem {
  id: string;
  userId: string;
  activityId: string;
  activityType: string;
  title: string;
  description?: string;
  amount?: number;
  currency?: string;
  emoji?: string;
  participants?: string[];
  mediaUrls?: string[];
  tags?: string[];
  location?: string;
  visibility: 'PRIVATE' | 'FRIENDS' | 'PUBLIC' | 'CUSTOM';
  isPinned?: boolean;
  likesCount: number;
  commentsCount: number;
  sharesCount: number;
  metadata?: Record<string, any>;
  createdAt: string;
  updatedAt: string;
  userProfile?: {
    id: string;
    displayName: string;
    username: string;
    avatarUrl?: string;
    isVerified?: boolean;
  };
  isLikedByCurrentUser?: boolean;
}

export interface SocialFeedResponse {
  items: SocialFeedItem[];
  hasMore: boolean;
  nextCursor?: string;
  totalCount: number;
}

export interface UserProfile {
  id: string;
  displayName: string;
  username: string;
  avatarUrl?: string;
  bio?: string;
  isVerified: boolean;
  followerCount: number;
  followingCount: number;
  transactionCount: number;
  isFollowedByCurrentUser: boolean;
  isPrivate: boolean;
  socialScore: number;
  joinedAt: string;
}

export interface Comment {
  id: string;
  userId: string;
  userProfile: {
    id: string;
    displayName: string;
    username: string;
    avatarUrl?: string;
  };
  content: string;
  replyToCommentId?: string;
  likesCount: number;
  isLikedByCurrentUser: boolean;
  createdAt: string;
  updatedAt: string;
  replies?: Comment[];
}

export interface LikeResponse {
  isLiked: boolean;
  likesCount: number;
}

export interface CommentResponse extends Comment {}

export interface FollowResponse {
  isFollowing: boolean;
  followerCount: number;
}

export interface ShareResponse {
  shareId: string;
  shareUrl: string;
  shareCount: number;
}

export interface PrivacySettings {
  profileVisibility: 'PUBLIC' | 'FRIENDS' | 'PRIVATE';
  transactionVisibility: 'PUBLIC' | 'FRIENDS' | 'PRIVATE';
  allowDirectMessages: boolean;
  allowTagging: boolean;
  showOnlineStatus: boolean;
  allowFriendRequests: boolean;
}

export interface NotificationSettings {
  newFollower: boolean;
  newLike: boolean;
  newComment: boolean;
  friendRequest: boolean;
  paymentActivity: boolean;
  groupActivity: boolean;
  mentions: boolean;
  directMessages: boolean;
}

export interface TrendingTopic {
  id: string;
  hashtag: string;
  description?: string;
  usageCount: number;
  isPromoted?: boolean;
}

export interface TrendingTopicsResponse {
  topics: TrendingTopic[];
  updatedAt: string;
}

export interface BlockResponse {
  isBlocked: boolean;
}

// Request types
export interface AddCommentRequest {
  content: string;
  replyToCommentId?: string;
}

export interface ShareTransactionRequest {
  platform?: 'INTERNAL' | 'TWITTER' | 'FACEBOOK' | 'INSTAGRAM';
  message?: string;
  visibility?: 'PUBLIC' | 'FRIENDS' | 'PRIVATE';
}

export interface UpdatePrivacySettingsRequest extends Partial<PrivacySettings> {}

export interface UpdateTransactionPrivacyRequest {
  visibility: 'PUBLIC' | 'FRIENDS' | 'PRIVATE';
}

export interface ReportContentRequest {
  type: 'USER' | 'TRANSACTION' | 'COMMENT';
  targetId: string;
  reason: string;
  description?: string;
}

export interface SendDirectMessageRequest {
  content: string;
  mediaUrls?: string[];
}

export interface UpdateNotificationSettingsRequest extends Partial<NotificationSettings> {}

// Emoji reaction types
export interface EmojiReaction {
  emoji: string;
  count: number;
  isSelectedByCurrentUser: boolean;
  users: Array<{
    id: string;
    displayName: string;
    avatarUrl?: string;
  }>;
}

export interface ReactToTransactionRequest {
  emoji: string;
}

export interface ReactionResponse {
  reactions: EmojiReaction[];
  totalReactions: number;
}

class SocialService {
  private baseUrl = '/api/v1/social';

  // Feed operations
  async getSocialFeed(filter = 'all', cursor?: string, limit = 20): Promise<SocialFeedResponse> {
    const params = new URLSearchParams({
      filter,
      limit: limit.toString(),
    });
    
    if (cursor) {
      params.append('cursor', cursor);
    }

    const response = await apiClient.get(`${this.baseUrl}/feed?${params}`);
    return response.data;
  }

  async getUserPublicTransactions(userId: string, cursor?: string, limit = 20): Promise<SocialFeedResponse> {
    const params = new URLSearchParams({
      limit: limit.toString(),
    });
    
    if (cursor) {
      params.append('cursor', cursor);
    }

    const response = await apiClient.get(`${this.baseUrl}/users/${userId}/transactions?${params}`);
    return response.data;
  }

  // User profiles
  async getUserProfile(userId: string): Promise<UserProfile> {
    const response = await apiClient.get(`${this.baseUrl}/users/${userId}/profile`);
    return response.data;
  }

  async searchUsers(query: string, limit = 20): Promise<UserProfile[]> {
    const params = new URLSearchParams({
      q: query,
      limit: limit.toString(),
    });

    const response = await apiClient.get(`${this.baseUrl}/users/search?${params}`);
    return response.data;
  }

  async getUserFriends(userId: string, page = 0, size = 20): Promise<UserProfile[]> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    const response = await apiClient.get(`${this.baseUrl}/users/${userId}/friends?${params}`);
    return response.data;
  }

  async getUserFollowers(userId: string, page = 0, size = 20): Promise<UserProfile[]> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    const response = await apiClient.get(`${this.baseUrl}/users/${userId}/followers?${params}`);
    return response.data;
  }

  async getFriendSuggestions(limit = 10): Promise<UserProfile[]> {
    const params = new URLSearchParams({
      limit: limit.toString(),
    });

    const response = await apiClient.get(`${this.baseUrl}/suggestions/friends?${params}`);
    return response.data;
  }

  // Interactions
  async toggleLike(transactionId: string): Promise<LikeResponse> {
    const response = await apiClient.post(`${this.baseUrl}/transactions/${transactionId}/like`);
    return response.data;
  }

  async addComment(transactionId: string, request: AddCommentRequest): Promise<CommentResponse> {
    const response = await apiClient.post(`${this.baseUrl}/transactions/${transactionId}/comments`, request);
    return response.data;
  }

  async getComments(transactionId: string, page = 0, size = 20): Promise<Comment[]> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    const response = await apiClient.get(`${this.baseUrl}/transactions/${transactionId}/comments?${params}`);
    return response.data;
  }

  async deleteComment(transactionId: string, commentId: string): Promise<void> {
    await apiClient.delete(`${this.baseUrl}/transactions/${transactionId}/comments/${commentId}`);
  }

  async shareTransaction(transactionId: string, request: ShareTransactionRequest): Promise<ShareResponse> {
    const response = await apiClient.post(`${this.baseUrl}/transactions/${transactionId}/share`, request);
    return response.data;
  }

  // Emoji reactions
  async reactToTransaction(transactionId: string, request: ReactToTransactionRequest): Promise<ReactionResponse> {
    const response = await apiClient.post(`${this.baseUrl}/transactions/${transactionId}/react`, request);
    return response.data;
  }

  async getTransactionReactions(transactionId: string): Promise<ReactionResponse> {
    const response = await apiClient.get(`${this.baseUrl}/transactions/${transactionId}/reactions`);
    return response.data;
  }

  async removeReaction(transactionId: string, emoji: string): Promise<ReactionResponse> {
    const response = await apiClient.delete(`${this.baseUrl}/transactions/${transactionId}/reactions/${encodeURIComponent(emoji)}`);
    return response.data;
  }

  // Social connections
  async toggleFollow(userId: string): Promise<FollowResponse> {
    const response = await apiClient.post(`${this.baseUrl}/users/${userId}/follow`);
    return response.data;
  }

  async toggleBlock(userId: string): Promise<BlockResponse> {
    const response = await apiClient.post(`${this.baseUrl}/users/${userId}/block`);
    return response.data;
  }

  // Privacy and settings
  async getPrivacySettings(): Promise<PrivacySettings> {
    const response = await apiClient.get(`${this.baseUrl}/settings/privacy`);
    return response.data;
  }

  async updatePrivacySettings(request: UpdatePrivacySettingsRequest): Promise<PrivacySettings> {
    const response = await apiClient.put(`${this.baseUrl}/settings/privacy`, request);
    return response.data;
  }

  async updateTransactionPrivacy(transactionId: string, request: UpdateTransactionPrivacyRequest): Promise<void> {
    await apiClient.put(`${this.baseUrl}/transactions/${transactionId}/privacy`, request);
  }

  async getNotificationSettings(): Promise<NotificationSettings> {
    const response = await apiClient.get(`${this.baseUrl}/settings/notifications`);
    return response.data;
  }

  async updateNotificationSettings(request: UpdateNotificationSettingsRequest): Promise<void> {
    await apiClient.put(`${this.baseUrl}/settings/notifications`, request);
  }

  // Trending and discovery
  async getTrendingTopics(): Promise<TrendingTopicsResponse> {
    const response = await apiClient.get(`${this.baseUrl}/trending`);
    return response.data;
  }

  // Reporting and moderation
  async reportContent(request: ReportContentRequest): Promise<void> {
    await apiClient.post(`${this.baseUrl}/report`, request);
  }

  // Direct messaging
  async sendDirectMessage(userId: string, request: SendDirectMessageRequest): Promise<void> {
    await apiClient.post(`${this.baseUrl}/users/${userId}/message`, request);
  }

  // Utility methods
  formatFeedItemForDisplay(item: SocialFeedItem): SocialFeedItem {
    // Add any client-side formatting logic here
    return {
      ...item,
      // Format dates, amounts, etc.
      createdAt: new Date(item.createdAt).toISOString(),
      updatedAt: new Date(item.updatedAt).toISOString(),
    };
  }

  getActivityTypeDisplayName(activityType: string): string {
    const displayNames: Record<string, string> = {
      PAYMENT_SENT: 'paid',
      PAYMENT_RECEIVED: 'received payment from',
      PAYMENT_REQUESTED: 'requested money from',
      BILL_SPLIT: 'split a bill with',
      GROUP_PAYMENT: 'paid in group',
      NEW_CONNECTION: 'connected with',
      ACHIEVEMENT: 'earned an achievement',
      MILESTONE: 'reached a milestone',
      GOAL_REACHED: 'completed a savings goal',
      INVESTMENT_GAIN: 'gained from investment',
      CRYPTO_TRADE: 'made a crypto trade',
      REWARD_EARNED: 'earned a reward',
      CHARITY_DONATION: 'donated to charity',
      BIRTHDAY_REMINDER: 'birthday reminder',
      CUSTOM_POST: 'posted',
    };

    return displayNames[activityType] || activityType.toLowerCase().replace('_', ' ');
  }

  getVisibilityIcon(visibility: string): string {
    const icons: Record<string, string> = {
      PUBLIC: '🌍',
      FRIENDS: '👥',
      PRIVATE: '🔒',
      CUSTOM: '⚙️',
    };

    return icons[visibility] || '👥';
  }

  // Popular emoji reactions for payments
  getPopularPaymentEmojis(): string[] {
    return ['💰', '🎉', '👏', '🔥', '💯', '🙌', '❤️', '😂', '🤝', '💪', '⚡', '✨'];
  }

  // Format currency amounts for display
  formatAmount(amount: number, currency = 'USD'): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  // Format relative time for feed items
  formatRelativeTime(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffInMs = now.getTime() - date.getTime();
    const diffInMinutes = Math.floor(diffInMs / (1000 * 60));
    const diffInHours = Math.floor(diffInMinutes / 60);
    const diffInDays = Math.floor(diffInHours / 24);

    if (diffInMinutes < 1) {
      return 'just now';
    } else if (diffInMinutes < 60) {
      return `${diffInMinutes}m ago`;
    } else if (diffInHours < 24) {
      return `${diffInHours}h ago`;
    } else if (diffInDays < 7) {
      return `${diffInDays}d ago`;
    } else {
      return date.toLocaleDateString();
    }
  }
}

export const socialService = new SocialService();
export default socialService;