import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Alert,
  RefreshControl,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * DeviceManagementScreen
 *
 * Screen for managing trusted devices and sessions
 *
 * Features:
 * - List of trusted devices
 * - Current device indication
 * - Device removal
 * - Session management
 * - Security alerts
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface Device {
  id: string;
  name: string;
  type: 'mobile' | 'tablet' | 'desktop' | 'web';
  os: string;
  browser?: string;
  location: string;
  ipAddress: string;
  lastActive: string;
  isCurrent: boolean;
  isTrusted: boolean;
  firstSeen: string;
}

const DeviceManagementScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  const [devices, setDevices] = useState<Device[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    AnalyticsService.trackScreenView('DeviceManagementScreen');
    loadDevices();
  }, []);

  const loadDevices = async () => {
    try {
      // TODO: Load devices from API
      // const devicesData = await dispatch(fetchUserDevices()).unwrap();

      // Mock data
      const mockDevices: Device[] = [
        {
          id: '1',
          name: 'iPhone 14 Pro',
          type: 'mobile',
          os: 'iOS 17.2',
          location: 'San Francisco, CA',
          ipAddress: '192.168.1.100',
          lastActive: new Date().toISOString(),
          isCurrent: true,
          isTrusted: true,
          firstSeen: '2024-01-15T10:00:00Z',
        },
        {
          id: '2',
          name: 'MacBook Pro',
          type: 'desktop',
          os: 'macOS 14.2',
          browser: 'Chrome 120',
          location: 'San Francisco, CA',
          ipAddress: '192.168.1.101',
          lastActive: '2025-10-22T15:30:00Z',
          isCurrent: false,
          isTrusted: true,
          firstSeen: '2024-01-10T08:00:00Z',
        },
        {
          id: '3',
          name: 'Samsung Galaxy S23',
          type: 'mobile',
          os: 'Android 14',
          location: 'New York, NY',
          ipAddress: '10.0.0.50',
          lastActive: '2025-10-20T12:00:00Z',
          isCurrent: false,
          isTrusted: false,
          firstSeen: '2025-10-20T11:30:00Z',
        },
      ];

      setDevices(mockDevices);
    } catch (error) {
      Alert.alert('Error', 'Failed to load devices');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadDevices();
    setRefreshing(false);
  };

  const handleRemoveDevice = (device: Device) => {
    if (device.isCurrent) {
      Alert.alert('Error', 'Cannot remove current device');
      return;
    }

    Alert.alert(
      'Remove Device',
      `Are you sure you want to remove "${device.name}"? This will log out this device.`,
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            try {
              // TODO: Call API to remove device
              // await dispatch(removeDevice(device.id)).unwrap();

              AnalyticsService.trackEvent('device_removed', {
                userId: user?.id,
                deviceId: device.id,
                deviceType: device.type,
              });

              setDevices((prev) => prev.filter((d) => d.id !== device.id));
              Alert.alert('Success', 'Device removed successfully');
            } catch (error) {
              Alert.alert('Error', 'Failed to remove device');
            }
          },
        },
      ]
    );
  };

  const handleTrustDevice = async (device: Device) => {
    try {
      // TODO: Call API to trust device
      // await dispatch(trustDevice(device.id)).unwrap();

      AnalyticsService.trackEvent('device_trusted', {
        userId: user?.id,
        deviceId: device.id,
      });

      setDevices((prev) =>
        prev.map((d) => (d.id === device.id ? { ...d, isTrusted: true } : d))
      );
    } catch (error) {
      Alert.alert('Error', 'Failed to trust device');
    }
  };

  const handleRemoveAllDevices = () => {
    Alert.alert(
      'Remove All Devices',
      'This will log out all devices except this one. You will need to login again on other devices.',
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Remove All',
          style: 'destructive',
          onPress: async () => {
            try {
              // TODO: Call API to remove all devices except current
              // await dispatch(removeAllDevicesExceptCurrent()).unwrap();

              AnalyticsService.trackEvent('all_devices_removed', {
                userId: user?.id,
                count: devices.filter((d) => !d.isCurrent).length,
              });

              setDevices((prev) => prev.filter((d) => d.isCurrent));
              Alert.alert('Success', 'All other devices removed');
            } catch (error) {
              Alert.alert('Error', 'Failed to remove devices');
            }
          },
        },
      ]
    );
  };

  const getDeviceIcon = (type: string): string => {
    switch (type) {
      case 'mobile':
        return 'cellphone';
      case 'tablet':
        return 'tablet';
      case 'desktop':
        return 'laptop';
      case 'web':
        return 'web';
      default:
        return 'devices';
    }
  };

  const formatLastActive = (dateString: string): string => {
    const date = new Date(dateString);
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

  const renderDevice = ({ item }: { item: Device }) => (
    <View style={styles.deviceCard}>
      <View style={styles.deviceHeader}>
        <View style={styles.deviceIconContainer}>
          <Icon name={getDeviceIcon(item.type)} size={32} color="#6200EE" />
          {item.isCurrent && (
            <View style={styles.currentBadge}>
              <Icon name="check" size={10} color="#FFFFFF" />
            </View>
          )}
        </View>

        <View style={styles.deviceInfo}>
          <View style={styles.deviceTitleRow}>
            <Text style={styles.deviceName}>{item.name}</Text>
            {item.isCurrent && (
              <View style={styles.currentLabel}>
                <Text style={styles.currentLabelText}>Current</Text>
              </View>
            )}
            {!item.isTrusted && (
              <Icon name="alert-circle" size={16} color="#FF9800" />
            )}
          </View>
          <Text style={styles.deviceOS}>
            {item.os}
            {item.browser && ` • ${item.browser}`}
          </Text>
          <Text style={styles.deviceLocation}>
            {item.location} • {item.ipAddress}
          </Text>
          <Text style={styles.deviceLastActive}>
            Last active: {formatLastActive(item.lastActive)}
          </Text>
        </View>
      </View>

      {!item.isCurrent && (
        <View style={styles.deviceActions}>
          {!item.isTrusted && (
            <TouchableOpacity
              style={styles.trustButton}
              onPress={() => handleTrustDevice(item)}
            >
              <Icon name="shield-check" size={16} color="#4CAF50" />
              <Text style={styles.trustButtonText}>Trust</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity
            style={styles.removeButton}
            onPress={() => handleRemoveDevice(item)}
          >
            <Icon name="delete" size={16} color="#F44336" />
            <Text style={styles.removeButtonText}>Remove</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );

  const renderHeader = () => (
    <View style={styles.headerContainer}>
      <View style={styles.infoCard}>
        <Icon name="information" size={20} color="#6200EE" />
        <Text style={styles.infoText}>
          Manage devices that have accessed your account. Remove any devices you don't recognize.
        </Text>
      </View>

      <View style={styles.statsCard}>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>{devices.length}</Text>
          <Text style={styles.statLabel}>Total Devices</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statValue}>
            {devices.filter((d) => d.isTrusted).length}
          </Text>
          <Text style={styles.statLabel}>Trusted</Text>
        </View>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <Header
        title="Device Management"
        showBack
        rightActions={
          devices.filter((d) => !d.isCurrent).length > 0
            ? [
                {
                  icon: 'delete-sweep',
                  onPress: handleRemoveAllDevices,
                },
              ]
            : []
        }
      />

      <FlatList
        data={devices}
        renderItem={renderDevice}
        keyExtractor={(item) => item.id}
        ListHeaderComponent={renderHeader}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        contentContainerStyle={styles.listContent}
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
  headerContainer: {
    paddingHorizontal: 16,
    paddingTop: 16,
    marginBottom: 8,
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#E8EAF6',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
    lineHeight: 20,
  },
  statsCard: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#6200EE',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 13,
    color: '#666',
  },
  statDivider: {
    width: 1,
    backgroundColor: '#E0E0E0',
    marginHorizontal: 16,
  },
  deviceCard: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginBottom: 12,
    borderRadius: 8,
    padding: 16,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 1,
  },
  deviceHeader: {
    flexDirection: 'row',
  },
  deviceIconContainer: {
    position: 'relative',
    marginRight: 16,
  },
  currentBadge: {
    position: 'absolute',
    top: -4,
    right: -4,
    backgroundColor: '#4CAF50',
    borderRadius: 8,
    width: 16,
    height: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  deviceInfo: {
    flex: 1,
  },
  deviceTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    marginRight: 8,
  },
  currentLabel: {
    backgroundColor: '#4CAF50',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
    marginRight: 8,
  },
  currentLabelText: {
    fontSize: 10,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  deviceOS: {
    fontSize: 14,
    color: '#666',
    marginBottom: 2,
  },
  deviceLocation: {
    fontSize: 13,
    color: '#999',
    marginBottom: 2,
  },
  deviceLastActive: {
    fontSize: 12,
    color: '#999',
  },
  deviceActions: {
    flexDirection: 'row',
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#F5F5F5',
  },
  trustButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 16,
    backgroundColor: '#E8F5E9',
    marginRight: 8,
  },
  trustButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#4CAF50',
    marginLeft: 4,
  },
  removeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 16,
    backgroundColor: '#FFEBEE',
  },
  removeButtonText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#F44336',
    marginLeft: 4,
  },
});

export default DeviceManagementScreen;
