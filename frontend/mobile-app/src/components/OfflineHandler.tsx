import React, { useEffect, useState, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Animated,
  Dimensions,
  Platform,
} from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Ionicons } from '@expo/vector-icons';

import { useAppDispatch, useAppSelector } from '../store/hooks';
import { setNetworkStatus, syncOfflineData } from '../store/slices/offlineSlice';
import { COLORS, FONTS, SIZES } from '../constants/theme';
import { hapticFeedback } from '../utils/haptics';
import { showToast } from '../utils/toast';

interface OfflineHandlerProps {
  children: React.ReactNode;
}

interface QueuedTransaction {
  id: string;
  type: 'send' | 'request' | 'contact_add';
  data: any;
  timestamp: number;
  retries: number;
}

const { width } = Dimensions.get('window');

export const OfflineHandler: React.FC<OfflineHandlerProps> = ({ children }) => {
  const dispatch = useAppDispatch();
  const { isOnline } = useAppSelector((state) => state.offline);
  
  const [showOfflineBanner, setShowOfflineBanner] = useState(false);
  const [queuedTransactions, setQueuedTransactions] = useState<QueuedTransaction[]>([]);
  const [syncingData, setSyncingData] = useState(false);
  
  const bannerAnimation = useRef(new Animated.Value(0)).current;
  const networkChangeTimeout = useRef<NodeJS.Timeout>();

  useEffect(() => {
    // Subscribe to network state changes
    const unsubscribe = NetInfo.addEventListener(handleNetworkStateChange);
    
    // Load queued transactions from storage
    loadQueuedTransactions();
    
    return () => {
      unsubscribe();
      if (networkChangeTimeout.current) {
        clearTimeout(networkChangeTimeout.current);
      }
    };
  }, []);

  useEffect(() => {
    // Sync data when coming back online
    if (isOnline && queuedTransactions.length > 0) {
      syncQueuedTransactions();
    }
  }, [isOnline, queuedTransactions]);

  const handleNetworkStateChange = (state: any) => {
    const wasOffline = !isOnline;
    const isNowOnline = state.isConnected && state.isInternetReachable;
    
    dispatch(setNetworkStatus({
      isOnline: isNowOnline,
      connectionType: state.type,
      isWiFi: state.type === 'wifi',
    }));

    // Clear any existing timeout
    if (networkChangeTimeout.current) {
      clearTimeout(networkChangeTimeout.current);
    }

    if (!isNowOnline) {
      // Device went offline
      setShowOfflineBanner(true);
      animateBanner(true);
      hapticFeedback.warning();
      
    } else if (wasOffline && isNowOnline) {
      // Device came back online
      hapticFeedback.success();
      showToast('Back online! Syncing data...');
      
      // Hide banner after a delay
      networkChangeTimeout.current = setTimeout(() => {
        setShowOfflineBanner(false);
        animateBanner(false);
      }, 2000);
    }
  };

  const animateBanner = (show: boolean) => {
    Animated.timing(bannerAnimation, {
      toValue: show ? 1 : 0,
      duration: 300,
      useNativeDriver: true,
    }).start();
  };

  const loadQueuedTransactions = async () => {
    try {
      const stored = await AsyncStorage.getItem('@waqiti_offline_queue');
      if (stored) {
        const parsed: QueuedTransaction[] = JSON.parse(stored);
        setQueuedTransactions(parsed);
      }
    } catch (error) {
      console.error('Error loading queued transactions:', error);
    }
  };

  const saveQueuedTransactions = async (transactions: QueuedTransaction[]) => {
    try {
      await AsyncStorage.setItem('@waqiti_offline_queue', JSON.stringify(transactions));
    } catch (error) {
      console.error('Error saving queued transactions:', error);
    }
  };

  const queueTransaction = async (type: QueuedTransaction['type'], data: any) => {
    if (isOnline) {
      return; // Don't queue if online
    }

    const transaction: QueuedTransaction = {
      id: `offline_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type,
      data,
      timestamp: Date.now(),
      retries: 0,
    };

    const updatedQueue = [...queuedTransactions, transaction];
    setQueuedTransactions(updatedQueue);
    await saveQueuedTransactions(updatedQueue);

    showToast('Transaction queued for when you\'re back online');
    hapticFeedback.light();
  };

  const syncQueuedTransactions = async () => {
    if (!isOnline || queuedTransactions.length === 0) {
      return;
    }

    setSyncingData(true);

    try {
      const results = await Promise.allSettled(
        queuedTransactions.map(transaction => syncSingleTransaction(transaction))
      );

      let successful = 0;
      let failed = 0;
      const remainingQueue: QueuedTransaction[] = [];

      results.forEach((result, index) => {
        const transaction = queuedTransactions[index];
        
        if (result.status === 'fulfilled' && result.value) {
          successful++;
        } else {
          failed++;
          
          // Retry failed transactions up to 3 times
          if (transaction.retries < 3) {
            remainingQueue.push({
              ...transaction,
              retries: transaction.retries + 1,
            });
          }
        }
      });

      // Update queue with remaining transactions
      setQueuedTransactions(remainingQueue);
      await saveQueuedTransactions(remainingQueue);

      // Show results
      if (successful > 0) {
        showToast(`${successful} transactions synced successfully`);
        hapticFeedback.success();
      }
      
      if (failed > 0) {
        showToast(`${failed} transactions failed to sync`);
      }

      // Dispatch sync action to update store
      dispatch(syncOfflineData({
        successful,
        failed,
        remaining: remainingQueue.length,
      }));

    } catch (error) {
      console.error('Error syncing queued transactions:', error);
      showToast('Failed to sync offline transactions');
    } finally {
      setSyncingData(false);
    }
  };

  const syncSingleTransaction = async (transaction: QueuedTransaction): Promise<boolean> => {
    try {
      // This would integrate with your actual API calls
      switch (transaction.type) {
        case 'send':
          // await sendMoney(transaction.data);
          console.log('Syncing send transaction:', transaction.data);
          break;
          
        case 'request':
          // await requestMoney(transaction.data);
          console.log('Syncing request transaction:', transaction.data);
          break;
          
        case 'contact_add':
          // await addContact(transaction.data);
          console.log('Syncing contact add:', transaction.data);
          break;
          
        default:
          throw new Error(`Unknown transaction type: ${transaction.type}`);
      }
      
      return true;
    } catch (error) {
      console.error(`Error syncing transaction ${transaction.id}:`, error);
      return false;
    }
  };

  const clearFailedTransactions = async () => {
    setQueuedTransactions([]);
    await saveQueuedTransactions([]);
    showToast('Offline queue cleared');
  };

  const retryFailedTransactions = () => {
    if (isOnline) {
      syncQueuedTransactions();
    } else {
      showToast('Please connect to the internet to retry');
    }
  };

  const renderOfflineBanner = () => {
    if (!showOfflineBanner) return null;

    const translateY = bannerAnimation.interpolate({
      inputRange: [0, 1],
      outputRange: [-60, 0],
    });

    return (
      <Animated.View 
        style={[
          styles.offlineBanner,
          {
            transform: [{ translateY }],
          }
        ]}
      >
        <View style={styles.bannerContent}>
          <Ionicons 
            name="cloud-offline-outline" 
            size={20} 
            color={COLORS.white} 
            style={styles.bannerIcon}
          />
          <View style={styles.bannerTextContainer}>
            <Text style={styles.bannerTitle}>No Internet Connection</Text>
            <Text style={styles.bannerSubtitle}>
              {queuedTransactions.length > 0 
                ? `${queuedTransactions.length} transactions queued`
                : 'You\'re currently offline'
              }
            </Text>
          </View>
          {syncingData && (
            <View style={styles.syncIndicator}>
              <Ionicons name="sync" size={16} color={COLORS.white} />
            </View>
          )}
        </View>
      </Animated.View>
    );
  };

  // Provide offline utilities through context
  const offlineUtils = {
    queueTransaction,
    retryFailedTransactions,
    clearFailedTransactions,
    queuedCount: queuedTransactions.length,
    isOnline,
    syncingData,
  };

  return (
    <View style={styles.container}>
      {renderOfflineBanner()}
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  offlineBanner: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    backgroundColor: COLORS.warning,
    zIndex: 1000,
    paddingTop: Platform.OS === 'ios' ? SIZES.statusBarHeight : 0,
  },
  bannerContent: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: SIZES.padding,
    paddingVertical: SIZES.base,
  },
  bannerIcon: {
    marginRight: SIZES.base,
  },
  bannerTextContainer: {
    flex: 1,
  },
  bannerTitle: {
    fontSize: SIZES.body3,
    fontFamily: FONTS.semiBold,
    color: COLORS.white,
  },
  bannerSubtitle: {
    fontSize: SIZES.caption,
    fontFamily: FONTS.regular,
    color: COLORS.white,
    opacity: 0.9,
  },
  syncIndicator: {
    padding: SIZES.base / 2,
  },
});

// Hook for accessing offline utilities
export const useOffline = () => {
  const { isOnline, connectionType, isWiFi } = useAppSelector((state) => state.offline);
  
  const isSlowConnection = connectionType === '2g' || connectionType === '3g';
  const isFastConnection = connectionType === '4g' || connectionType === '5g' || isWiFi;
  
  return {
    isOnline,
    isOffline: !isOnline,
    connectionType,
    isWiFi,
    isSlowConnection,
    isFastConnection,
  };
};

export default OfflineHandler;