import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  Alert,
  Image,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { notificationService } from '../../services/notificationService';
import { formatRelativeTime } from '../../utils/formatters';
import Swipeable from 'react-native-gesture-handler/Swipeable';

interface Notification {
  id: string;
  userId: string;
  type: 'PAYMENT_RECEIVED' | 'PAYMENT_SENT' | 'PAYMENT_REQUEST' | 'SECURITY_ALERT' | 'SYSTEM' | 'PROMOTIONAL';
  title: string;
  message: string;
  data?: any;
  isRead: boolean;
  createdAt: string;
  actionUrl?: string;
}

const NotificationsScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<'all' | 'unread' | 'read'>('all');

  useFocusEffect(
    useCallback(() => {
      loadNotifications();
    }, [])
  );

  const loadNotifications = async () => {
    try {
      setLoading(true);
      const response = await notificationService.getNotifications();
      setNotifications(response.notifications);
    } catch (error) {
      console.error('Failed to load notifications:', error);
      Alert.alert('Error', 'Failed to load notifications');
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadNotifications();
    setRefreshing(false);
  }, []);

  const markAsRead = async (notificationId: string) => {
    try {
      await notificationService.markAsRead(notificationId);
      setNotifications(prev =>
        prev.map(notification =>
          notification.id === notificationId
            ? { ...notification, isRead: true }
            : notification
        )
      );
    } catch (error) {
      console.error('Failed to mark notification as read:', error);
    }
  };

  const markAllAsRead = async () => {
    try {
      await notificationService.markAllAsRead();
      setNotifications(prev =>
        prev.map(notification => ({ ...notification, isRead: true }))
      );
    } catch (error) {
      console.error('Failed to mark all notifications as read:', error);
      Alert.alert('Error', 'Failed to mark all notifications as read');
    }
  };

  const deleteNotification = async (notificationId: string) => {
    try {
      await notificationService.deleteNotification(notificationId);
      setNotifications(prev =>
        prev.filter(notification => notification.id !== notificationId)
      );
    } catch (error) {
      console.error('Failed to delete notification:', error);
      Alert.alert('Error', 'Failed to delete notification');
    }
  };

  const handleNotificationPress = async (notification: Notification) => {
    if (!notification.isRead) {
      await markAsRead(notification.id);
    }

    // Handle different notification types
    switch (notification.type) {
      case 'PAYMENT_RECEIVED':
      case 'PAYMENT_SENT':
        if (notification.data?.paymentId) {
          navigation.navigate('TransactionDetails', {
            transactionId: notification.data.paymentId,
          });
        }
        break;
      case 'PAYMENT_REQUEST':
        if (notification.data?.requestId) {
          navigation.navigate('PaymentRequest', {
            requestId: notification.data.requestId,
          });
        }
        break;
      case 'SECURITY_ALERT':
        navigation.navigate('SecuritySettings');
        break;
      default:
        if (notification.actionUrl) {
          // Handle custom action URL
          console.log('Navigate to:', notification.actionUrl);
        }
        break;
    }
  };

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case 'PAYMENT_RECEIVED':
        return 'arrow-downward';
      case 'PAYMENT_SENT':
        return 'arrow-upward';
      case 'PAYMENT_REQUEST':
        return 'request-page';
      case 'SECURITY_ALERT':
        return 'security';
      case 'SYSTEM':
        return 'info';
      case 'PROMOTIONAL':
        return 'local-offer';
      default:
        return 'notifications';
    }
  };

  const getNotificationColor = (type: string) => {
    switch (type) {
      case 'PAYMENT_RECEIVED':
        return '#4CAF50';
      case 'PAYMENT_SENT':
        return '#2196F3';
      case 'PAYMENT_REQUEST':
        return '#FF9800';
      case 'SECURITY_ALERT':
        return '#F44336';
      case 'SYSTEM':
        return '#9C27B0';
      case 'PROMOTIONAL':
        return '#E91E63';
      default:
        return theme.colors.primary;
    }
  };

  const renderRightActions = (notification: Notification) => {
    return (
      <View style={styles.swipeActions}>
        {!notification.isRead && (
          <TouchableOpacity
            style={[styles.swipeAction, { backgroundColor: theme.colors.primary }]}
            onPress={() => markAsRead(notification.id)}
          >
            <Icon name="mark-email-read" size={20} color="#FFFFFF" />
            <Text style={styles.swipeActionText}>Mark Read</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity
          style={[styles.swipeAction, { backgroundColor: '#F44336' }]}
          onPress={() => {
            Alert.alert(
              'Delete Notification',
              'Are you sure you want to delete this notification?',
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Delete',
                  style: 'destructive',
                  onPress: () => deleteNotification(notification.id),
                },
              ]
            );
          }}
        >
          <Icon name="delete" size={20} color="#FFFFFF" />
          <Text style={styles.swipeActionText}>Delete</Text>
        </TouchableOpacity>
      </View>
    );
  };

  const renderNotificationItem = ({ item }: { item: Notification }) => {
    return (
      <Swipeable
        renderRightActions={() => renderRightActions(item)}
        rightThreshold={40}
      >
        <TouchableOpacity
          style={[
            styles.notificationItem,
            {
              backgroundColor: item.isRead ? theme.colors.surface : theme.colors.background,
              borderLeftColor: getNotificationColor(item.type),
            },
          ]}
          onPress={() => handleNotificationPress(item)}
          activeOpacity={0.7}
        >
          <View style={styles.notificationContent}>
            <View style={[
              styles.iconContainer,
              { backgroundColor: getNotificationColor(item.type) + '20' }
            ]}>
              <Icon
                name={getNotificationIcon(item.type)}
                size={24}
                color={getNotificationColor(item.type)}
              />
            </View>
            
            <View style={styles.textContainer}>
              <View style={styles.header}>
                <Text
                  style={[
                    styles.title,
                    { color: theme.colors.text },
                    !item.isRead && styles.unreadTitle,
                  ]}
                  numberOfLines={1}
                >
                  {item.title}
                </Text>
                <Text style={[styles.time, { color: theme.colors.textSecondary }]}>
                  {formatRelativeTime(new Date(item.createdAt))}
                </Text>
              </View>
              
              <Text
                style={[
                  styles.message,
                  { color: theme.colors.textSecondary },
                  !item.isRead && styles.unreadMessage,
                ]}
                numberOfLines={2}
              >
                {item.message}
              </Text>
            </View>
            
            {!item.isRead && (
              <View style={[styles.unreadIndicator, { backgroundColor: theme.colors.primary }]} />
            )}
          </View>
        </TouchableOpacity>
      </Swipeable>
    );
  };

  const filteredNotifications = notifications.filter(notification => {
    switch (filter) {
      case 'unread':
        return !notification.isRead;
      case 'read':
        return notification.isRead;
      default:
        return true;
    }
  });

  const unreadCount = notifications.filter(n => !n.isRead).length;

  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <Icon name="notifications-none" size={64} color={theme.colors.textSecondary} />
      <Text style={[styles.emptyTitle, { color: theme.colors.text }]}>
        No notifications
      </Text>
      <Text style={[styles.emptyMessage, { color: theme.colors.textSecondary }]}>
        When you receive payments or other updates, they'll appear here
      </Text>
    </View>
  );

  if (loading) {
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
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Icon name="arrow-back" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <Text style={[styles.headerTitle, { color: theme.colors.text }]}>
          Notifications
        </Text>
        
        <TouchableOpacity
          onPress={() => navigation.navigate('NotificationSettings')}
          style={styles.settingsButton}
        >
          <Icon name="settings" size={24} color={theme.colors.text} />
        </TouchableOpacity>
      </View>

      <View style={[styles.filterContainer, { backgroundColor: theme.colors.surface }]}>
        <View style={styles.filterTabs}>
          {[
            { key: 'all', label: 'All', count: notifications.length },
            { key: 'unread', label: 'Unread', count: unreadCount },
            { key: 'read', label: 'Read', count: notifications.length - unreadCount },
          ].map(tab => (
            <TouchableOpacity
              key={tab.key}
              style={[
                styles.filterTab,
                filter === tab.key && { backgroundColor: theme.colors.primary },
              ]}
              onPress={() => setFilter(tab.key as any)}
            >
              <Text
                style={[
                  styles.filterTabText,
                  { color: filter === tab.key ? '#FFFFFF' : theme.colors.text },
                ]}
              >
                {tab.label} ({tab.count})
              </Text>
            </TouchableOpacity>
          ))}
        </View>
        
        {unreadCount > 0 && (
          <TouchableOpacity
            style={[styles.markAllButton, { backgroundColor: theme.colors.primary }]}
            onPress={markAllAsRead}
          >
            <Text style={styles.markAllButtonText}>Mark All Read</Text>
          </TouchableOpacity>
        )}
      </View>

      <FlatList
        data={filteredNotifications}
        renderItem={renderNotificationItem}
        keyExtractor={item => item.id}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={[theme.colors.primary]}
          />
        }
        ListEmptyComponent={renderEmptyState}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={
          filteredNotifications.length === 0 ? styles.emptyList : styles.list
        }
      />
    </SafeAreaView>
  );
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
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '600',
  },
  settingsButton: {
    padding: 8,
  },
  filterContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  filterTabs: {
    flexDirection: 'row',
    flex: 1,
  },
  filterTab: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    marginRight: 8,
  },
  filterTabText: {
    fontSize: 12,
    fontWeight: '500',
  },
  markAllButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  markAllButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '500',
  },
  list: {
    paddingBottom: 20,
  },
  emptyList: {
    flex: 1,
  },
  notificationItem: {
    borderLeftWidth: 4,
    marginHorizontal: 16,
    marginVertical: 4,
    borderRadius: 8,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  notificationContent: {
    flexDirection: 'row',
    padding: 16,
    alignItems: 'flex-start',
  },
  iconContainer: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  textContainer: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 4,
  },
  title: {
    fontSize: 16,
    fontWeight: '500',
    flex: 1,
    marginRight: 8,
  },
  unreadTitle: {
    fontWeight: '600',
  },
  time: {
    fontSize: 12,
  },
  message: {
    fontSize: 14,
    lineHeight: 20,
  },
  unreadMessage: {
    fontWeight: '500',
  },
  unreadIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginLeft: 8,
    marginTop: 4,
  },
  swipeActions: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 4,
    marginRight: 16,
  },
  swipeAction: {
    width: 80,
    height: '100%',
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 8,
    marginLeft: 4,
  },
  swipeActionText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '500',
    marginTop: 2,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyMessage: {
    fontSize: 16,
    textAlign: 'center',
    lineHeight: 24,
  },
});

export default NotificationsScreen;