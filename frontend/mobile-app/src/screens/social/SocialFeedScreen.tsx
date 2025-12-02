/**
 * Social Feed Screen
 * Venmo-style social payment feed showing public transactions
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
  Image,
  Alert,
  Modal,
  TextInput,
  Share,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../contexts/ThemeContext';
import { socialFeedService } from '../../services/socialFeedService';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';

interface SocialTransaction {
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

interface Comment {
  id: string;
  userId: string;
  username: string;
  text: string;
  timestamp: Date;
}

const SocialFeedScreen: React.FC = () => {
  const navigation = useNavigation();
  const { colors, isDark } = useTheme();
  
  const [transactions, setTransactions] = useState<SocialTransaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedTransaction, setSelectedTransaction] = useState<SocialTransaction | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [newComment, setNewComment] = useState('');
  const [showComments, setShowComments] = useState(false);
  const [filter, setFilter] = useState<'all' | 'friends' | 'trending'>('all');

  useEffect(() => {
    loadFeed();
  }, [filter]);

  const loadFeed = async () => {
    try {
      setLoading(true);
      const feedData = await socialFeedService.getFeed(filter);
      setTransactions(feedData);
    } catch (error) {
      console.error('Error loading social feed:', error);
      Alert.alert('Error', 'Failed to load social feed');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadFeed();
    setRefreshing(false);
  }, [filter]);

  const handleLike = async (transactionId: string) => {
    try {
      const updatedTransaction = await socialFeedService.toggleLike(transactionId);
      setTransactions(prev => 
        prev.map(tx => 
          tx.id === transactionId 
            ? { ...tx, isLiked: updatedTransaction.isLiked, likes: updatedTransaction.likes }
            : tx
        )
      );
    } catch (error) {
      console.error('Error toggling like:', error);
      Alert.alert('Error', 'Failed to update like');
    }
  };

  const handleComment = async () => {
    if (!selectedTransaction || !newComment.trim()) return;

    try {
      const comment = await socialFeedService.addComment(selectedTransaction.id, newComment.trim());
      setComments(prev => [...prev, comment]);
      setNewComment('');
      
      // Update transaction comment count
      setTransactions(prev => 
        prev.map(tx => 
          tx.id === selectedTransaction.id 
            ? { ...tx, comments: tx.comments + 1 }
            : tx
        )
      );
    } catch (error) {
      console.error('Error adding comment:', error);
      Alert.alert('Error', 'Failed to add comment');
    }
  };

  const showCommentModal = async (transaction: SocialTransaction) => {
    try {
      setSelectedTransaction(transaction);
      const transactionComments = await socialFeedService.getComments(transaction.id);
      setComments(transactionComments);
      setShowComments(true);
    } catch (error) {
      console.error('Error loading comments:', error);
      Alert.alert('Error', 'Failed to load comments');
    }
  };

  const handleShare = async (transaction: SocialTransaction) => {
    try {
      const shareContent = `${transaction.sender.name} paid ${transaction.recipient.name} for ${transaction.description} on Waqiti`;
      await Share.share({
        message: shareContent,
        title: 'Waqiti Payment',
      });
    } catch (error) {
      console.error('Error sharing:', error);
    }
  };

  const navigateToProfile = (userId: string) => {
    navigation.navigate('Profile', { userId });
  };

  const formatAmount = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const formatTimestamp = (timestamp: Date): string => {
    const now = new Date();
    const diff = now.getTime() - timestamp.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m`;
    if (hours < 24) return `${hours}h`;
    if (days < 7) return `${days}d`;
    return timestamp.toLocaleDateString();
  };

  const renderAvatar = (user: { name: string; avatar?: string }) => (
    <View style={[styles.avatar, { backgroundColor: colors.surfaceVariant }]}>
      {user.avatar ? (
        <Image source={{ uri: user.avatar }} style={styles.avatarImage} />
      ) : (
        <Text style={[styles.avatarText, { color: colors.onSurfaceVariant }]}>
          {user.name.charAt(0).toUpperCase()}
        </Text>
      )}
    </View>
  );

  const renderTransaction = ({ item: transaction }: { item: SocialTransaction }) => (
    <View style={[styles.transactionCard, { backgroundColor: colors.surface }]}>
      <View style={styles.transactionHeader}>
        <TouchableOpacity
          style={styles.userInfo}
          onPress={() => navigateToProfile(transaction.sender.id)}
        >
          {renderAvatar(transaction.sender)}
          <View style={styles.userDetails}>
            <Text style={[styles.userName, { color: colors.onSurface }]}>
              {transaction.sender.name}
            </Text>
            <Text style={[styles.userHandle, { color: colors.onSurfaceVariant }]}>
              @{transaction.sender.username}
            </Text>
          </View>
        </TouchableOpacity>
        <Text style={[styles.timestamp, { color: colors.onSurfaceVariant }]}>
          {formatTimestamp(transaction.timestamp)}
        </Text>
      </View>

      <View style={styles.transactionContent}>
        <Text style={[styles.transactionText, { color: colors.onSurface }]}>
          paid{' '}
          <TouchableOpacity onPress={() => navigateToProfile(transaction.recipient.id)}>
            <Text style={[styles.recipientName, { color: colors.primary }]}>
              {transaction.recipient.name}
            </Text>
          </TouchableOpacity>
        </Text>
        
        <View style={styles.descriptionContainer}>
          {transaction.emoji && (
            <Text style={styles.emoji}>{transaction.emoji}</Text>
          )}
          <Text style={[styles.description, { color: colors.onSurface }]}>
            {transaction.description}
          </Text>
        </View>
      </View>

      <View style={styles.transactionActions}>
        <TouchableOpacity
          style={styles.actionButton}
          onPress={() => handleLike(transaction.id)}
        >
          <Ionicons
            name={transaction.isLiked ? 'heart' : 'heart-outline'}
            size={20}
            color={transaction.isLiked ? colors.error : colors.onSurfaceVariant}
          />
          <Text style={[styles.actionText, { color: colors.onSurfaceVariant }]}>
            {transaction.likes}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionButton}
          onPress={() => showCommentModal(transaction)}
        >
          <Ionicons
            name="chatbubble-outline"
            size={20}
            color={colors.onSurfaceVariant}
          />
          <Text style={[styles.actionText, { color: colors.onSurfaceVariant }]}>
            {transaction.comments}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionButton}
          onPress={() => handleShare(transaction)}
        >
          <Ionicons
            name="share-outline"
            size={20}
            color={colors.onSurfaceVariant}
          />
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderFilterTabs = () => (
    <View style={[styles.filterTabs, { backgroundColor: colors.surface }]}>
      {(['all', 'friends', 'trending'] as const).map((filterOption) => (
        <TouchableOpacity
          key={filterOption}
          style={[
            styles.filterTab,
            filter === filterOption && { backgroundColor: colors.primaryContainer },
          ]}
          onPress={() => setFilter(filterOption)}
        >
          <Text
            style={[
              styles.filterTabText,
              {
                color: filter === filterOption ? colors.onPrimaryContainer : colors.onSurfaceVariant,
              },
            ]}
          >
            {filterOption.charAt(0).toUpperCase() + filterOption.slice(1)}
          </Text>
        </TouchableOpacity>
      ))}
    </View>
  );

  const renderCommentModal = () => (
    <Modal
      visible={showComments}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={() => setShowComments(false)}
    >
      <SafeAreaView style={[styles.commentModal, { backgroundColor: colors.background }]}>
        <View style={[styles.commentHeader, { borderBottomColor: colors.outline }]}>
          <TouchableOpacity onPress={() => setShowComments(false)}>
            <Ionicons name="close" size={24} color={colors.onSurface} />
          </TouchableOpacity>
          <Text style={[styles.commentTitle, { color: colors.onSurface }]}>
            Comments
          </Text>
          <View style={{ width: 24 }} />
        </View>

        <FlatList
          data={comments}
          keyExtractor={(item) => item.id}
          style={styles.commentsList}
          renderItem={({ item: comment }) => (
            <View style={styles.commentItem}>
              <Text style={[styles.commentUsername, { color: colors.primary }]}>
                @{comment.username}
              </Text>
              <Text style={[styles.commentText, { color: colors.onSurface }]}>
                {comment.text}
              </Text>
              <Text style={[styles.commentTime, { color: colors.onSurfaceVariant }]}>
                {formatTimestamp(comment.timestamp)}
              </Text>
            </View>
          )}
          showsVerticalScrollIndicator={false}
        />

        <View style={[styles.commentInput, { borderTopColor: colors.outline }]}>
          <TextInput
            style={[
              styles.commentTextInput,
              {
                backgroundColor: colors.surfaceVariant,
                color: colors.onSurface,
              },
            ]}
            placeholder="Add a comment..."
            placeholderTextColor={colors.onSurfaceVariant}
            value={newComment}
            onChangeText={setNewComment}
            multiline
            maxLength={280}
          />
          <TouchableOpacity
            style={[
              styles.commentSendButton,
              {
                backgroundColor: newComment.trim() ? colors.primary : colors.surfaceVariant,
              },
            ]}
            onPress={handleComment}
            disabled={!newComment.trim()}
          >
            <Ionicons
              name="send"
              size={20}
              color={newComment.trim() ? colors.onPrimary : colors.onSurfaceVariant}
            />
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    </Modal>
  );

  if (loading && transactions.length === 0) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
        <LoadingSpinner />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={[styles.header, { backgroundColor: colors.surface }]}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Social Feed</Text>
        <TouchableOpacity onPress={() => navigation.navigate('SocialSettings')}>
          <Ionicons name="settings-outline" size={24} color={colors.onSurface} />
        </TouchableOpacity>
      </View>

      {renderFilterTabs()}

      {transactions.length === 0 ? (
        <EmptyState
          icon="people-outline"
          title="No social activity yet"
          subtitle="Follow friends or make your payments public to see activity here"
          actionText="Explore Users"
          onAction={() => navigation.navigate('UserSearch')}
        />
      ) : (
        <FlatList
          data={transactions}
          keyExtractor={(item) => item.id}
          renderItem={renderTransaction}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={handleRefresh}
              tintColor={colors.primary}
            />
          }
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.feedContent}
        />
      )}

      {renderCommentModal()}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.1)',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  filterTabs: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  filterTab: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    marginRight: 12,
    borderRadius: 20,
  },
  filterTabText: {
    fontSize: 14,
    fontWeight: '600',
  },
  feedContent: {
    paddingVertical: 8,
  },
  transactionCard: {
    marginHorizontal: 16,
    marginVertical: 8,
    padding: 16,
    borderRadius: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  transactionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  userInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  userDetails: {
    marginLeft: 12,
  },
  userName: {
    fontSize: 16,
    fontWeight: '600',
  },
  userHandle: {
    fontSize: 14,
  },
  timestamp: {
    fontSize: 12,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarImage: {
    width: 40,
    height: 40,
    borderRadius: 20,
  },
  avatarText: {
    fontSize: 16,
    fontWeight: '600',
  },
  transactionContent: {
    marginBottom: 12,
  },
  transactionText: {
    fontSize: 16,
    marginBottom: 8,
  },
  recipientName: {
    fontWeight: '600',
  },
  descriptionContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  emoji: {
    fontSize: 18,
    marginRight: 8,
  },
  description: {
    fontSize: 16,
    flex: 1,
  },
  transactionActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0,0,0,0.1)',
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  actionText: {
    marginLeft: 4,
    fontSize: 14,
  },
  commentModal: {
    flex: 1,
  },
  commentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  commentTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  commentsList: {
    flex: 1,
    paddingHorizontal: 20,
  },
  commentItem: {
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.05)',
  },
  commentUsername: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
  },
  commentText: {
    fontSize: 16,
    marginBottom: 4,
  },
  commentTime: {
    fontSize: 12,
  },
  commentInput: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderTopWidth: 1,
  },
  commentTextInput: {
    flex: 1,
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginRight: 12,
    maxHeight: 100,
    fontSize: 16,
  },
  commentSendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default SocialFeedScreen;