import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * NotificationsScreen
 *
 * Screen for viewing and managing notifications
 *
 * Features:
 * - List of notifications
 * - Mark as read/unread
 * - Delete notifications
 * - Filter by type
 * - Pull to refresh
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

type NotificationType =
  | 'payment_received'
  | 'payment_sent'
  | 'security_alert'
  | 'kyc_update'
  | 'promotion'
  | 'system';

interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  timestamp: string;
  isRead: boolean;
  actionUrl?: string;
  metadata?: Record<string, any>;
}

const NotificationsScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');

  useEffect(() => {
    AnalyticsService.trackScreenView('NotificationsScreen');
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    try {
      // TODO: Load notifications from API
      // const data = await dispatch(fetchNotifications()).unwrap();

      // Mock data
      const mockNotifications: Notification[] = [
        {
          id: '1',
          type: 'payment_received',
          title: 'Payment Received',
          message: 'You received $50.00 from John Doe',
          timestamp: new Date().toISOString(),
          isRead: false,
          metadata: { amount: 50, senderId: 'user-123' },
        },
        {
          id: '2',
          type: 'security_alert',
          title: 'New Device Login',
          message: 'A new device logged into your account from San Francisco, CA',
          timestamp: new Date(Date.now() - 3600000).toISOString(),
          isRead: false,
        },
        {
          id: '3',
          type: 'kyc_update',
          title: 'KYC Verification Complete',
          message: 'Your identity has been verified successfully',
          timestamp: new Date(Date.now() - 86400000).toISOString(),
          isRead: true,
        },
        {
          id: '4',
          type: 'payment_sent',
          title: 'Payment Sent',
          message: 'You sent $25.00 to Jane Smith',
          timestamp: new Date(Date.now() - 172800000).toISOString(),
          isRead: true,
          metadata: { amount: 25, recipientId: 'user-456' },
        },
      ];

      setNotifications(mockNotifications);
    } catch (error) {
      Alert.alert('Error', 'Failed to load notifications');
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadNotifications();
    setRefreshing(false);
  };

  const handleMarkAsRead = async (notificationId: string) => {
    try {
      // TODO: Call API to mark as read
      // await dispatch(markNotificationAsRead(notificationId)).unwrap();

      setNotifications((prev) =>
        prev.map((n) => (n.id === notificationId ? { ...n, isRead: true } : n))
      );

      AnalyticsService.trackEvent('notification_marked_read', {
        notificationId,
      });
    } catch (error) {
      Alert.alert('Error', 'Failed to mark notification as read');
    }
  };

  const handleMarkAllAsRead = async () => {
    try {
      // TODO: Call API to mark all as read
      // await dispatch(markAllNotificationsAsRead()).unwrap();

      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));

      AnalyticsService.trackEvent('all_notifications_marked_read');
    } catch (error) {
      Alert.alert('Error', 'Failed to mark all as read');
    }
  };

  const handleDeleteNotification = (notificationId: string) => {
    Alert.alert(
      'Delete Notification',
      'Are you sure you want to delete this notification?',
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              // TODO: Call API to delete notification
              // await dispatch(deleteNotification(notificationId)).unwrap();

              setNotifications((prev) => prev.filter((n) => n.id !== notificationId));

              AnalyticsService.trackEvent('notification_deleted', {
                notificationId,
              });
            } catch (error) {
              Alert.alert('Error', 'Failed to delete notification');
            }
          },
        },
      ]
    );
  };

  const handleNotificationPress = (notification: Notification) => {
    if (!notification.isRead) {
      handleMarkAsRead(notification.id);
    }

    AnalyticsService.trackEvent('notification_opened', {
      notificationId: notification.id,
      type: notification.type,
    });

    // Navigate based on notification type
    switch (notification.type) {
      case 'payment_received':
      case 'payment_sent':
        navigation.navigate('Activity' as never);
        break;
      case 'security_alert':
        navigation.navigate('DeviceManagement' as never);
        break;
      case 'kyc_update':
        navigation.navigate('KYCVerification' as never);
        break;
    }
  };

  const getNotificationIcon = (type: NotificationType): string => {
    switch (type) {
      case 'payment_received':
        return 'arrow-down-circle';
      case 'payment_sent':
        return 'arrow-up-circle';
      case 'security_alert':
        return 'shield-alert';
      case 'kyc_update':
        return 'shield-check';
      case 'promotion':
        return 'tag';
      case 'system':
        return 'information';
      default:
        return 'bell';
    }
  };

  const getNotificationColor = (type: NotificationType): string => {
    switch (type) {
      case 'payment_received':
        return '#4CAF50';
      case 'payment_sent':
        return '#2196F3';
      case 'security_alert':
        return '#F44336';
      case 'kyc_update':
        return '#9C27B0';
      case 'promotion':
        return '#FF9800';
      case 'system':
        return '#666';
      default:
        return '#6200EE';
    }
  };

  const formatTimestamp = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  const filteredNotifications =
    filter === 'unread'
      ? notifications.filter((n) => !n.isRead)
      : notifications;

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  const renderNotification = ({ item }: { item: Notification }) => (
    <TouchableOpacity
      style={[styles.notificationCard, !item.isRead && styles.notificationUnread]}
      onPress={() => handleNotificationPress(item)}
      onLongPress={() => handleDeleteNotification(item.id)}
    >
      <View
        style={[
          styles.notificationIcon,
          { backgroundColor: getNotificationColor(item.type) + '20' },
        ]}
      >
        <Icon
          name={getNotificationIcon(item.type)}
          size={24}
          color={getNotificationColor(item.type)}
        />
      </View>

      <View style={styles.notificationContent}>
        <View style={styles.notificationHeader}>
          <Text style={styles.notificationTitle}>{item.title}</Text>
          {!item.isRead && <View style={styles.unreadDot} />}
        </View>
        <Text style={styles.notificationMessage} numberOfLines={2}>
          {item.message}
        </Text>
        <Text style={styles.notificationTimestamp}>
          {formatTimestamp(item.timestamp)}
        </Text>
      </View>
    </TouchableOpacity>
  );

  const renderHeader = () => (
    <View style={styles.headerContainer}>
      <View style={styles.filterContainer}>
        <TouchableOpacity
          style={[styles.filterButton, filter === 'all' && styles.filterButtonActive]}
          onPress={() => setFilter('all')}
        >
          <Text
            style={[
              styles.filterButtonText,
              filter === 'all' && styles.filterButtonTextActive,
            ]}
          >
            All ({notifications.length})
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.filterButton,
            filter === 'unread' && styles.filterButtonActive,
          ]}
          onPress={() => setFilter('unread')}
        >
          <Text
            style={[
              styles.filterButtonText,
              filter === 'unread' && styles.filterButtonTextActive,
            ]}
          >
            Unread ({unreadCount})
          </Text>
        </TouchableOpacity>
      </View>

      {unreadCount > 0 && (
        <TouchableOpacity
          style={styles.markAllButton}
          onPress={handleMarkAllAsRead}
        >
          <Icon name="check-all" size={16} color="#6200EE" />
          <Text style={styles.markAllButtonText}>Mark all as read</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <Icon name="bell-off" size={64} color="#E0E0E0" />
      <Text style={styles.emptyTitle}>No Notifications</Text>
      <Text style={styles.emptyText}>
        {filter === 'unread'
          ? "You're all caught up!"
          : "You'll see notifications here"}
      </Text>
    </View>
  );

  return (
    <View style={styles.container}>
      <Header
        title="Notifications"
        showBack
        rightActions={[
          {
            icon: 'cog',
            onPress: () => navigation.navigate('NotificationSettings' as never),
          },
        ]}
      />

      <FlatList
        data={filteredNotifications}
        renderItem={renderNotification}
        keyExtractor={(item) => item.id}
        ListHeaderComponent={renderHeader}
        ListEmptyComponent={renderEmptyState}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        contentContainerStyle={
          filteredNotifications.length === 0
            ? styles.emptyListContent
            : styles.listContent
        }
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  listContent: {
    paddingBottom: 16,
  },
  emptyListContent: {
    flexGrow: 1,
  },
  headerContainer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
    marginBottom: 8,
  },
  filterContainer: {
    flexDirection: 'row',
    marginBottom: 12,
  },
  filterButton: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 20,
    backgroundColor: '#F5F5F5',
    alignItems: 'center',
    marginHorizontal: 4,
  },
  filterButtonActive: {
    backgroundColor: '#6200EE',
  },
  filterButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  filterButtonTextActive: {
    color: '#FFFFFF',
  },
  markAllButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 8,
  },
  markAllButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6200EE',
    marginLeft: 6,
  },
  notificationCard: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  notificationUnread: {
    backgroundColor: '#F3E5F5',
  },
  notificationIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  notificationContent: {
    flex: 1,
  },
  notificationHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  notificationTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    flex: 1,
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#6200EE',
    marginLeft: 8,
  },
  notificationMessage: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 4,
  },
  notificationTimestamp: {
    fontSize: 12,
    color: '#999',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 64,
    paddingHorizontal: 32,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 16,
  },
  emptyText: {
    fontSize: 14,
    color: '#666',
    marginTop: 8,
    textAlign: 'center',
  },
});

export default NotificationsScreen;
