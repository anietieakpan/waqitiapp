import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  Alert,
  ActivityIndicator,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * NearbyPaymentScreen
 *
 * Screen for discovering and sending payments to nearby Waqiti users
 * Uses BLE (Bluetooth Low Energy) and local network discovery
 *
 * Features:
 * - Bluetooth device scanning
 * - Nearby user discovery
 * - Quick payment to nearby users
 * - Permission handling
 * - Analytics tracking
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

interface NearbyUser {
  id: string;
  name: string;
  distance: number;
  signal: number;
  profilePicture?: string;
}

const NearbyPaymentScreen: React.FC = () => {
  const navigation = useNavigation();

  const [isScanning, setIsScanning] = useState(false);
  const [nearbyUsers, setNearbyUsers] = useState<NearbyUser[]>([]);
  const [hasPermissions, setHasPermissions] = useState(false);
  const [selectedUser, setSelectedUser] = useState<NearbyUser | null>(null);

  useEffect(() => {
    AnalyticsService.trackScreenView('NearbyPaymentScreen');
    checkPermissions();
  }, []);

  const checkPermissions = async () => {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        ]);

        const allGranted = Object.values(granted).every(
          (status) => status === PermissionsAndroid.RESULTS.GRANTED
        );

        setHasPermissions(allGranted);

        if (!allGranted) {
          Alert.alert(
            'Permissions Required',
            'Bluetooth and location permissions are required to discover nearby users.'
          );
        }
      } catch (err) {
        console.error('Permission error:', err);
      }
    } else {
      // iOS - assume permissions granted, will prompt on BLE usage
      setHasPermissions(true);
    }
  };

  const startScanning = () => {
    if (!hasPermissions) {
      checkPermissions();
      return;
    }

    setIsScanning(true);
    AnalyticsService.trackEvent('nearby_scan_started');

    // TODO: Implement actual BLE scanning
    // For now, simulate discovery
    setTimeout(() => {
      const mockUsers: NearbyUser[] = [
        {
          id: '1',
          name: 'John Doe',
          distance: 5,
          signal: -50,
        },
        {
          id: '2',
          name: 'Jane Smith',
          distance: 12,
          signal: -65,
        },
        {
          id: '3',
          name: 'Bob Johnson',
          distance: 8,
          signal: -58,
        },
      ];

      setNearbyUsers(mockUsers);
      setIsScanning(false);

      AnalyticsService.trackEvent('nearby_scan_completed', {
        usersFound: mockUsers.length,
      });
    }, 3000);
  };

  const stopScanning = () => {
    setIsScanning(false);
    AnalyticsService.trackEvent('nearby_scan_stopped');
  };

  const handleSelectUser = (user: NearbyUser) => {
    setSelectedUser(user);

    AnalyticsService.trackEvent('nearby_user_selected', {
      userId: user.id,
      distance: user.distance,
    });

    navigation.navigate('PaymentDetails' as never, {
      contact: {
        id: user.id,
        name: user.name,
        waqitiUserId: user.id,
      },
      mode: 'nearby',
    } as never);
  };

  const getSignalStrength = (signal: number): string => {
    if (signal > -55) return 'excellent';
    if (signal > -70) return 'good';
    if (signal > -80) return 'fair';
    return 'weak';
  };

  const getSignalColor = (signal: number): string => {
    if (signal > -55) return '#4CAF50';
    if (signal > -70) return '#8BC34A';
    if (signal > -80) return '#FF9800';
    return '#F44336';
  };

  const renderNearbyUser = ({ item }: { item: NearbyUser }) => {
    const signalStrength = getSignalStrength(item.signal);
    const signalColor = getSignalColor(item.signal);

    return (
      <TouchableOpacity
        style={styles.userCard}
        onPress={() => handleSelectUser(item)}
      >
        <View style={styles.userInfo}>
          <View style={styles.avatarPlaceholder}>
            <Icon name="account" size={32} color="#FFFFFF" />
          </View>

          <View style={styles.userDetails}>
            <Text style={styles.userName}>{item.name}</Text>
            <View style={styles.distanceRow}>
              <Icon name="map-marker" size={14} color="#666" />
              <Text style={styles.distance}>~{item.distance}m away</Text>
            </View>
          </View>
        </View>

        <View style={styles.signalContainer}>
          <Icon
            name="wifi"
            size={24}
            color={signalColor}
            style={{ transform: [{ rotate: '45deg' }] }}
          />
          <Text style={[styles.signalText, { color: signalColor }]}>
            {signalStrength}
          </Text>
        </View>
      </TouchableOpacity>
    );
  };

  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <Icon name="radar" size={80} color="#E0E0E0" />
      <Text style={styles.emptyTitle}>No Nearby Users</Text>
      <Text style={styles.emptyText}>
        {isScanning
          ? 'Scanning for nearby Waqiti users...'
          : 'Start scanning to discover nearby users'}
      </Text>
    </View>
  );

  const renderPermissionDenied = () => (
    <View style={styles.emptyContainer}>
      <Icon name="bluetooth-off" size={80} color="#F44336" />
      <Text style={styles.emptyTitle}>Permissions Required</Text>
      <Text style={styles.emptyText}>
        Bluetooth and location permissions are required to discover nearby users.
      </Text>
      <TouchableOpacity
        style={styles.permissionButton}
        onPress={checkPermissions}
      >
        <Text style={styles.permissionButtonText}>Grant Permissions</Text>
      </TouchableOpacity>
    </View>
  );

  if (!hasPermissions) {
    return (
      <View style={styles.container}>
        <Header title="Nearby Payment" showBack />
        {renderPermissionDenied()}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Header title="Nearby Payment" showBack />

      <View style={styles.infoCard}>
        <Icon name="information" size={20} color="#6200EE" />
        <Text style={styles.infoText}>
          Make sure Bluetooth is enabled and the recipient has the app open.
        </Text>
      </View>

      <View style={styles.content}>
        <FlatList
          data={nearbyUsers}
          renderItem={renderNearbyUser}
          keyExtractor={(item) => item.id}
          ListEmptyComponent={renderEmptyState}
          contentContainerStyle={
            nearbyUsers.length === 0 ? styles.emptyListContainer : undefined
          }
        />
      </View>

      <View style={styles.footer}>
        {!isScanning ? (
          <TouchableOpacity
            style={styles.scanButton}
            onPress={startScanning}
          >
            <Icon name="radar" size={24} color="#FFFFFF" />
            <Text style={styles.scanButtonText}>
              {nearbyUsers.length > 0 ? 'Scan Again' : 'Start Scanning'}
            </Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={[styles.scanButton, styles.scanButtonActive]}
            onPress={stopScanning}
          >
            <ActivityIndicator size="small" color="#FFFFFF" />
            <Text style={styles.scanButtonText}>Scanning...</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  infoCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#E8EAF6',
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    marginTop: 16,
    borderRadius: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginLeft: 12,
    flex: 1,
  },
  content: {
    flex: 1,
    marginTop: 16,
  },
  userCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 8,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  userInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  avatarPlaceholder: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#6200EE',
    justifyContent: 'center',
    alignItems: 'center',
  },
  userDetails: {
    marginLeft: 12,
    flex: 1,
  },
  userName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  distanceRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  distance: {
    fontSize: 14,
    color: '#666',
    marginLeft: 4,
  },
  signalContainer: {
    alignItems: 'center',
  },
  signalText: {
    fontSize: 10,
    fontWeight: '600',
    marginTop: 2,
    textTransform: 'capitalize',
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
    paddingHorizontal: 32,
  },
  emptyListContainer: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginTop: 16,
  },
  emptyText: {
    fontSize: 16,
    color: '#666',
    marginTop: 8,
    textAlign: 'center',
  },
  permissionButton: {
    backgroundColor: '#6200EE',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    marginTop: 24,
  },
  permissionButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
  footer: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  scanButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#6200EE',
    paddingVertical: 16,
    borderRadius: 8,
  },
  scanButtonActive: {
    backgroundColor: '#9C27B0',
  },
  scanButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
    marginLeft: 12,
  },
});

export default NearbyPaymentScreen;
