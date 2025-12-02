/**
 * NotificationChannelSettings Component
 * Allows users to configure notification settings per channel
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  Switch,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useNotification, NotificationCategory } from '../../contexts/NotificationContext';
import { useLocalization } from '../../contexts/LocalizationContext';

const CHANNEL_INFO: Record<NotificationCategory, { icon: string; color: string; description: string }> = {
  transaction: {
    icon: 'payment',
    color: '#4CAF50',
    description: 'Payment confirmations, transfers, and transaction alerts',
  },
  security: {
    icon: 'security',
    color: '#F44336',
    description: 'Login alerts, password changes, and security warnings',
  },
  marketing: {
    icon: 'local-offer',
    color: '#FF9800',
    description: 'Promotions, offers, and product updates',
  },
  system: {
    icon: 'info',
    color: '#2196F3',
    description: 'App updates, maintenance notices, and system messages',
  },
  social: {
    icon: 'people',
    color: '#9C27B0',
    description: 'Friend requests, messages, and social interactions',
  },
  reminder: {
    icon: 'alarm',
    color: '#00BCD4',
    description: 'Payment reminders, scheduled tasks, and deadlines',
  },
  achievement: {
    icon: 'emoji-events',
    color: '#FFC107',
    description: 'Rewards, milestones, and achievement notifications',
  },
};

const PRIORITY_OPTIONS = [
  { value: 'low', label: 'Low', description: 'Silent, no interruption' },
  { value: 'normal', label: 'Normal', description: 'Sound and vibration' },
  { value: 'high', label: 'High', description: 'Heads-up display' },
  { value: 'urgent', label: 'Urgent', description: 'Override Do Not Disturb' },
];

export const NotificationChannelSettings: React.FC = () => {
  const { settings, updateChannelSettings, loading } = useNotification();
  const { t } = useLocalization();
  const [expandedChannel, setExpandedChannel] = useState<NotificationCategory | null>(null);
  const [updating, setUpdating] = useState<string | null>(null);

  const handleChannelToggle = async (channel: NotificationCategory, enabled: boolean) => {
    try {
      setUpdating(channel);
      await updateChannelSettings(channel, { enabled });
    } catch (error) {
      console.error('Failed to update channel settings:', error);
    } finally {
      setUpdating(null);
    }
  };

  const handleSettingChange = async (
    channel: NotificationCategory,
    setting: string,
    value: any
  ) => {
    try {
      setUpdating(`${channel}_${setting}`);
      await updateChannelSettings(channel, { [setting]: value });
    } catch (error) {
      console.error('Failed to update channel settings:', error);
    } finally {
      setUpdating(null);
    }
  };

  if (loading || !settings) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.sectionTitle}>{t('settings.notificationChannels')}</Text>
      <Text style={styles.sectionDescription}>
        {t('settings.notificationChannelsDescription')}
      </Text>

      {Object.entries(settings.channels).map(([channelKey, channelSettings]) => {
        const channel = channelKey as NotificationCategory;
        const channelInfo = CHANNEL_INFO[channel];
        const isExpanded = expandedChannel === channel;
        const isUpdating = updating?.startsWith(channel);

        return (
          <View key={channel} style={styles.channelContainer}>
            <TouchableOpacity
              style={styles.channelHeader}
              onPress={() => setExpandedChannel(isExpanded ? null : channel)}
              activeOpacity={0.7}
            >
              <View style={styles.channelInfo}>
                <View
                  style={[
                    styles.channelIcon,
                    { backgroundColor: `${channelInfo.color}20` },
                  ]}
                >
                  <Icon name={channelInfo.icon} size={24} color={channelInfo.color} />
                </View>
                <View style={styles.channelText}>
                  <Text style={styles.channelName}>
                    {t(`notification.channel.${channel}`)}
                  </Text>
                  <Text style={styles.channelDescription} numberOfLines={1}>
                    {channelInfo.description}
                  </Text>
                </View>
              </View>
              
              <View style={styles.channelActions}>
                {updating === channel ? (
                  <ActivityIndicator size="small" color="#007AFF" />
                ) : (
                  <Switch
                    value={channelSettings.enabled}
                    onValueChange={(value) => handleChannelToggle(channel, value)}
                    trackColor={{ false: '#e0e0e0', true: '#81c784' }}
                    thumbColor={channelSettings.enabled ? '#4CAF50' : '#f5f5f5'}
                  />
                )}
                <Icon
                  name={isExpanded ? 'expand-less' : 'expand-more'}
                  size={24}
                  color="#999"
                  style={styles.expandIcon}
                />
              </View>
            </TouchableOpacity>

            {isExpanded && (
              <View style={styles.channelSettings}>
                {/* Priority Setting */}
                <View style={styles.settingItem}>
                  <Text style={styles.settingLabel}>{t('settings.priority')}</Text>
                  <View style={styles.priorityOptions}>
                    {PRIORITY_OPTIONS.map((option) => (
                      <TouchableOpacity
                        key={option.value}
                        style={[
                          styles.priorityOption,
                          channelSettings.priority === option.value && styles.priorityOptionSelected,
                        ]}
                        onPress={() => handleSettingChange(channel, 'priority', option.value)}
                        disabled={isUpdating}
                      >
                        <Text
                          style={[
                            styles.priorityLabel,
                            channelSettings.priority === option.value && styles.priorityLabelSelected,
                          ]}
                        >
                          {option.label}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>

                {/* Sound Setting */}
                <View style={styles.settingRow}>
                  <View style={styles.settingInfo}>
                    <Icon name="volume-up" size={20} color="#666" style={styles.settingIcon} />
                    <Text style={styles.settingLabel}>{t('settings.sound')}</Text>
                  </View>
                  {updating === `${channel}_sound` ? (
                    <ActivityIndicator size="small" color="#007AFF" />
                  ) : (
                    <Switch
                      value={channelSettings.sound}
                      onValueChange={(value) => handleSettingChange(channel, 'sound', value)}
                      trackColor={{ false: '#e0e0e0', true: '#81c784' }}
                      thumbColor={channelSettings.sound ? '#4CAF50' : '#f5f5f5'}
                    />
                  )}
                </View>

                {/* Vibration Setting */}
                <View style={styles.settingRow}>
                  <View style={styles.settingInfo}>
                    <Icon name="vibration" size={20} color="#666" style={styles.settingIcon} />
                    <Text style={styles.settingLabel}>{t('settings.vibration')}</Text>
                  </View>
                  {updating === `${channel}_vibration` ? (
                    <ActivityIndicator size="small" color="#007AFF" />
                  ) : (
                    <Switch
                      value={channelSettings.vibration}
                      onValueChange={(value) => handleSettingChange(channel, 'vibration', value)}
                      trackColor={{ false: '#e0e0e0', true: '#81c784' }}
                      thumbColor={channelSettings.vibration ? '#4CAF50' : '#f5f5f5'}
                    />
                  )}
                </View>

                {/* Badge Setting */}
                <View style={styles.settingRow}>
                  <View style={styles.settingInfo}>
                    <Icon name="fiber-manual-record" size={20} color="#666" style={styles.settingIcon} />
                    <Text style={styles.settingLabel}>{t('settings.showBadge')}</Text>
                  </View>
                  {updating === `${channel}_showBadge` ? (
                    <ActivityIndicator size="small" color="#007AFF" />
                  ) : (
                    <Switch
                      value={channelSettings.showBadge}
                      onValueChange={(value) => handleSettingChange(channel, 'showBadge', value)}
                      trackColor={{ false: '#e0e0e0', true: '#81c784' }}
                      thumbColor={channelSettings.showBadge ? '#4CAF50' : '#f5f5f5'}
                    />
                  )}
                </View>

                {/* LED Color (if supported) */}
                {settings.ledColorEnabled && channelSettings.ledColor && (
                  <View style={styles.settingRow}>
                    <View style={styles.settingInfo}>
                      <View
                        style={[
                          styles.ledIndicator,
                          { backgroundColor: channelSettings.ledColor },
                        ]}
                      />
                      <Text style={styles.settingLabel}>{t('settings.ledColor')}</Text>
                    </View>
                    <Text style={styles.ledColorValue}>{channelSettings.ledColor}</Text>
                  </View>
                )}
              </View>
            )}
          </View>
        );
      })}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    paddingHorizontal: 16,
    paddingTop: 20,
    paddingBottom: 8,
  },
  sectionDescription: {
    fontSize: 14,
    color: '#666',
    paddingHorizontal: 16,
    paddingBottom: 16,
  },
  channelContainer: {
    backgroundColor: '#fff',
    marginBottom: 1,
  },
  channelHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
  },
  channelInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  channelIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  channelText: {
    flex: 1,
  },
  channelName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 2,
  },
  channelDescription: {
    fontSize: 12,
    color: '#999',
  },
  channelActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  expandIcon: {
    marginLeft: 8,
  },
  channelSettings: {
    paddingHorizontal: 16,
    paddingBottom: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#e1e1e1',
  },
  settingItem: {
    marginTop: 16,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
  },
  settingInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  settingIcon: {
    marginRight: 12,
  },
  settingLabel: {
    fontSize: 14,
    color: '#666',
  },
  priorityOptions: {
    flexDirection: 'row',
    marginTop: 8,
  },
  priorityOption: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: '#e0e0e0',
    alignItems: 'center',
    marginRight: 8,
    borderRadius: 8,
  },
  priorityOptionSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#f0f8ff',
  },
  priorityLabel: {
    fontSize: 12,
    color: '#666',
  },
  priorityLabelSelected: {
    color: '#007AFF',
    fontWeight: '500',
  },
  ledIndicator: {
    width: 20,
    height: 20,
    borderRadius: 10,
    marginRight: 12,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  ledColorValue: {
    fontSize: 12,
    color: '#999',
  },
});