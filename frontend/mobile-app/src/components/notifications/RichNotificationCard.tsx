/**
 * RichNotificationCard Component
 * Displays rich notifications with images, actions, and expanded views
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Image,
  Animated,
  Dimensions,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useNotification } from '../../contexts/NotificationContext';
import { useLocalization } from '../../contexts/LocalizationContext';

interface RichNotificationCardProps {
  notification: any; // Using any to avoid circular dependency with NotificationContext
  onDismiss?: () => void;
  onAction?: (actionId: string) => void;
}

const { width: screenWidth } = Dimensions.get('window');

export const RichNotificationCard: React.FC<RichNotificationCardProps> = ({
  notification,
  onDismiss,
  onAction,
}) => {
  const { markAsRead, removeNotification } = useNotification();
  const { t } = useLocalization();
  const [expanded, setExpanded] = useState(false);
  const [imageError, setImageError] = useState(false);
  
  // Animation values
  const fadeAnim = useState(new Animated.Value(0))[0];
  const slideAnim = useState(new Animated.Value(-100))[0];

  React.useEffect(() => {
    // Animate notification entrance
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }),
      Animated.timing(slideAnim, {
        toValue: 0,
        duration: 300,
        useNativeDriver: true,
      }),
    ]).start();
  }, []);

  const handleDismiss = () => {
    // Animate notification exit
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 200,
        useNativeDriver: true,
      }),
      Animated.timing(slideAnim, {
        toValue: -100,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start(() => {
      if (!notification.persistent) {
        removeNotification(notification.id);
      }
      onDismiss?.();
    });
  };

  const handlePress = () => {
    if (!notification.read) {
      markAsRead(notification.id);
    }
    
    if (notification.richContent?.expandedView) {
      setExpanded(!expanded);
    }
  };

  const handleAction = (action: any) => {
    onAction?.(action.id);
    action.handler?.();
    
    if (notification.autoCancel) {
      handleDismiss();
    }
  };

  const getNotificationColor = () => {
    if (notification.richContent?.color) {
      return notification.richContent.color;
    }
    
    switch (notification.type) {
      case 'success':
        return '#4CAF50';
      case 'warning':
        return '#FF9800';
      case 'error':
        return '#F44336';
      default:
        return '#2196F3';
    }
  };

  const getNotificationIcon = () => {
    switch (notification.category) {
      case 'transaction':
        return 'payment';
      case 'security':
        return 'security';
      case 'marketing':
        return 'local-offer';
      case 'social':
        return 'people';
      case 'reminder':
        return 'alarm';
      case 'achievement':
        return 'emoji-events';
      default:
        return 'notifications';
    }
  };

  return (
    <Animated.View
      style={[
        styles.container,
        {
          opacity: fadeAnim,
          transform: [{ translateY: slideAnim }],
        },
      ]}
    >
      <TouchableOpacity
        activeOpacity={0.95}
        onPress={handlePress}
        style={[
          styles.card,
          { borderLeftColor: getNotificationColor() },
          !notification.read && styles.unreadCard,
        ]}
      >
        {/* Header */}
        <View style={styles.header}>
          <View style={styles.iconContainer}>
            {notification.richContent?.largeIcon ? (
              <Image
                source={{ uri: notification.richContent.largeIcon }}
                style={styles.largeIcon}
                onError={() => setImageError(true)}
              />
            ) : (
              <Icon
                name={getNotificationIcon()}
                size={24}
                color={getNotificationColor()}
              />
            )}
          </View>
          
          <View style={styles.headerContent}>
            <Text style={styles.title} numberOfLines={2}>
              {notification.title}
            </Text>
            <Text style={styles.timestamp}>
              {new Date(notification.timestamp).toLocaleTimeString()}
            </Text>
          </View>
          
          {!notification.persistent && (
            <TouchableOpacity
              onPress={handleDismiss}
              style={styles.closeButton}
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            >
              <Icon name="close" size={20} color="#999" />
            </TouchableOpacity>
          )}
        </View>

        {/* Content */}
        <View style={styles.content}>
          <Text style={styles.message} numberOfLines={expanded ? undefined : 3}>
            {notification.message}
          </Text>

          {/* Big Picture */}
          {notification.richContent?.imageUrl && !imageError && (
            <Image
              source={{ uri: notification.richContent.imageUrl }}
              style={styles.bigPicture}
              resizeMode="cover"
              onError={() => setImageError(true)}
            />
          )}

          {/* Progress Bar */}
          {notification.richContent?.progressBar && (
            <View style={styles.progressContainer}>
              <View style={styles.progressBar}>
                <View
                  style={[
                    styles.progressFill,
                    {
                      width: notification.richContent.progressBar.indeterminate
                        ? '100%'
                        : `${(notification.richContent.progressBar.value / notification.richContent.progressBar.max) * 100}%`,
                      backgroundColor: getNotificationColor(),
                    },
                  ]}
                />
              </View>
              {!notification.richContent.progressBar.indeterminate && (
                <Text style={styles.progressText}>
                  {notification.richContent.progressBar.value} / {notification.richContent.progressBar.max}
                </Text>
              )}
            </View>
          )}

          {/* Expanded View */}
          {expanded && notification.richContent?.expandedView && (
            <View style={styles.expandedContent}>
              {notification.richContent.expandedView.bigText && (
                <Text style={styles.bigText}>
                  {notification.richContent.expandedView.bigText}
                </Text>
              )}
              
              {notification.richContent.expandedView.inboxStyle && (
                <View style={styles.inboxStyle}>
                  {notification.richContent.expandedView.inboxStyle.map((line, index) => (
                    <Text key={index} style={styles.inboxLine}>
                      • {line}
                    </Text>
                  ))}
                </View>
              )}
            </View>
          )}
        </View>

        {/* Actions */}
        {notification.richContent?.buttons && notification.richContent.buttons.length > 0 && (
          <View style={styles.actions}>
            {notification.richContent.buttons.map((action) => (
              <TouchableOpacity
                key={action.id}
                style={[
                  styles.actionButton,
                  action.actionType === 'primary' && styles.primaryAction,
                  action.actionType === 'destructive' && styles.destructiveAction,
                ]}
                onPress={() => handleAction(action)}
              >
                <Text
                  style={[
                    styles.actionText,
                    action.actionType === 'primary' && styles.primaryActionText,
                    action.actionType === 'destructive' && styles.destructiveActionText,
                  ]}
                >
                  {action.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* Priority Indicator */}
        {notification.priority === 'urgent' && (
          <View style={[styles.priorityIndicator, { backgroundColor: getNotificationColor() }]} />
        )}
      </TouchableOpacity>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    borderLeftWidth: 4,
    overflow: 'hidden',
  },
  unreadCard: {
    backgroundColor: '#f8f9fa',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    paddingBottom: 0,
  },
  iconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#f5f5f5',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  largeIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
  },
  headerContent: {
    flex: 1,
  },
  title: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 2,
  },
  timestamp: {
    fontSize: 12,
    color: '#999',
  },
  closeButton: {
    padding: 4,
  },
  content: {
    padding: 16,
  },
  message: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  bigPicture: {
    width: '100%',
    height: 180,
    borderRadius: 8,
    marginTop: 12,
  },
  progressContainer: {
    marginTop: 12,
  },
  progressBar: {
    height: 4,
    backgroundColor: '#e0e0e0',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 2,
  },
  progressText: {
    fontSize: 12,
    color: '#999',
    textAlign: 'right',
    marginTop: 4,
  },
  expandedContent: {
    marginTop: 12,
  },
  bigText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  inboxStyle: {
    marginTop: 8,
  },
  inboxLine: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
    marginBottom: 4,
  },
  actions: {
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#e1e1e1',
  },
  actionButton: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderRightWidth: StyleSheet.hairlineWidth,
    borderRightColor: '#e1e1e1',
  },
  primaryAction: {
    backgroundColor: '#007AFF',
  },
  destructiveAction: {
    backgroundColor: '#fff',
  },
  actionText: {
    fontSize: 14,
    color: '#007AFF',
    fontWeight: '500',
  },
  primaryActionText: {
    color: '#fff',
  },
  destructiveActionText: {
    color: '#FF3B30',
  },
  priorityIndicator: {
    position: 'absolute',
    top: 0,
    right: 0,
    width: 0,
    height: 0,
    borderStyle: 'solid',
    borderLeftWidth: 30,
    borderBottomWidth: 30,
    borderLeftColor: 'transparent',
    borderBottomColor: 'transparent',
  },
});