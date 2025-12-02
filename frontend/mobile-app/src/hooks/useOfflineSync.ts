/**
 * useOfflineSync Hook
 * Custom hook for managing offline sync functionality
 */
import { useEffect, useCallback, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { AppState, AppStateStatus } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import BackgroundFetch from 'react-native-background-fetch';
import { 
  selectIsOffline, 
  selectPendingChanges,
  selectSyncStatus,
  updateSyncStatus 
} from '../store/offline/offlineSlice';
import { offlineStorage } from '../store/offline/offlineStorage';
import { useAuth } from './useAuth';
import { showNotification } from '../utils/notifications';

interface UseOfflineSyncOptions {
  enableBackgroundSync?: boolean;
  syncInterval?: number; // minutes
  onSyncComplete?: () => void;
  onSyncError?: (error: Error) => void;
}

export const useOfflineSync = (options: UseOfflineSyncOptions = {}) => {
  const {
    enableBackgroundSync = true,
    syncInterval = 15,
    onSyncComplete,
    onSyncError
  } = options;

  const dispatch = useDispatch();
  const { user } = useAuth();
  const isOffline = useSelector(selectIsOffline);
  const pendingChanges = useSelector(selectPendingChanges);
  const syncStatus = useSelector(selectSyncStatus);
  
  const syncInProgress = useRef(false);
  const lastSyncTime = useRef<number>(0);

  /**
   * Sync offline data with server
   */
  const syncOfflineData = useCallback(async () => {
    if (!user || syncInProgress.current || isOffline) {
      return;
    }

    // Check if enough time has passed since last sync
    const now = Date.now();
    if (now - lastSyncTime.current < 5000) { // 5 seconds minimum between syncs
      return;
    }

    syncInProgress.current = true;
    lastSyncTime.current = now;

    try {
      dispatch(updateSyncStatus({ status: 'syncing', progress: 0 }));

      // Sync pending payments
      const pendingPayments = await offlineStorage.getPendingPayments(user.id);
      for (let i = 0; i < pendingPayments.length; i++) {
        const payment = pendingPayments[i];
        
        try {
          // Dispatch sync action
          await dispatch({
            type: 'payments/syncOfflinePayment',
            payload: payment
          });

          // Update sync status
          await offlineStorage.updateSyncStatus('pending_payments', payment.id, 'synced');
          
          // Update progress
          const progress = ((i + 1) / pendingPayments.length) * 33;
          dispatch(updateSyncStatus({ progress }));
          
        } catch (error) {
          console.error('Failed to sync payment:', payment.id, error);
          await offlineStorage.updateSyncStatus(
            'pending_payments', 
            payment.id, 
            'failed', 
            true
          );
        }
      }

      // Sync transactions
      const transactions = await offlineStorage.getPendingSync('transactions');
      for (let i = 0; i < transactions.length; i++) {
        const transaction = transactions[i];
        
        try {
          await dispatch({
            type: 'transactions/syncOfflineTransaction',
            payload: transaction
          });

          await offlineStorage.updateSyncStatus('transactions', transaction.id, 'synced');
          
          const progress = 33 + ((i + 1) / transactions.length) * 33;
          dispatch(updateSyncStatus({ progress }));
          
        } catch (error) {
          console.error('Failed to sync transaction:', transaction.id, error);
          await offlineStorage.updateSyncStatus(
            'transactions', 
            transaction.id, 
            'failed', 
            true
          );
        }
      }

      // Sync user data changes
      const userData = await offlineStorage.getUserData(user.id);
      if (userData && userData.syncStatus === 'pending') {
        try {
          await dispatch({
            type: 'user/syncOfflineData',
            payload: userData
          });

          await offlineStorage.saveUserData(user.id, {
            ...userData,
            syncStatus: 'synced'
          });
          
          dispatch(updateSyncStatus({ progress: 100 }));
          
        } catch (error) {
          console.error('Failed to sync user data:', error);
        }
      }

      dispatch(updateSyncStatus({ 
        status: 'completed', 
        progress: 100,
        completedAt: Date.now()
      }));

      onSyncComplete?.();

    } catch (error) {
      console.error('Sync failed:', error);
      dispatch(updateSyncStatus({ 
        status: 'failed', 
        error: error.message 
      }));
      onSyncError?.(error);
    } finally {
      syncInProgress.current = false;
    }
  }, [user, isOffline, dispatch, onSyncComplete, onSyncError]);

  /**
   * Manual sync trigger
   */
  const triggerSync = useCallback(() => {
    if (!isOffline && pendingChanges > 0) {
      syncOfflineData();
    }
  }, [isOffline, pendingChanges, syncOfflineData]);

  /**
   * Setup background sync
   */
  const setupBackgroundSync = useCallback(async () => {
    if (!enableBackgroundSync) return;

    try {
      // Configure background fetch
      await BackgroundFetch.configure({
        minimumFetchInterval: syncInterval, // minutes
        forceAlarmManager: false,
        stopOnTerminate: false,
        startOnBoot: true,
        requiredNetworkType: BackgroundFetch.NETWORK_TYPE_ANY,
        requiresCharging: false,
        requiresDeviceIdle: false,
        requiresBatteryNotLow: false,
        requiresStorageNotLow: false,
        enableHeadless: true
      }, async (taskId) => {
        console.log('[BackgroundFetch] Task received:', taskId);
        
        // Perform sync
        await syncOfflineData();
        
        // Notify completion
        BackgroundFetch.finish(taskId);
      }, (error) => {
        console.error('[BackgroundFetch] Failed to configure:', error);
      });

      // Check status
      const status = await BackgroundFetch.status();
      console.log('[BackgroundFetch] Status:', status);

    } catch (error) {
      console.error('Failed to setup background sync:', error);
    }
  }, [enableBackgroundSync, syncInterval, syncOfflineData]);

  /**
   * Handle app state changes
   */
  useEffect(() => {
    const handleAppStateChange = (nextAppState: AppStateStatus) => {
      if (nextAppState === 'active' && !isOffline && pendingChanges > 0) {
        // Sync when app comes to foreground
        syncOfflineData();
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => subscription.remove();
  }, [isOffline, pendingChanges, syncOfflineData]);

  /**
   * Handle network state changes
   */
  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener(state => {
      if (state.isConnected && pendingChanges > 0) {
        // Delay sync to ensure stable connection
        setTimeout(() => {
          syncOfflineData();
        }, 2000);
      }
    });

    return unsubscribe;
  }, [pendingChanges, syncOfflineData]);

  /**
   * Setup background sync on mount
   */
  useEffect(() => {
    setupBackgroundSync();

    return () => {
      // Cleanup background fetch on unmount
      BackgroundFetch.stop();
    };
  }, [setupBackgroundSync]);

  /**
   * Show sync notifications
   */
  useEffect(() => {
    if (syncStatus.status === 'completed' && pendingChanges === 0) {
      showNotification({
        title: 'Sync Complete',
        message: 'All offline changes have been synced',
        type: 'success'
      });
    } else if (syncStatus.status === 'failed') {
      showNotification({
        title: 'Sync Failed',
        message: 'Some changes could not be synced. We\'ll try again later.',
        type: 'error'
      });
    }
  }, [syncStatus, pendingChanges]);

  return {
    isOffline,
    pendingChanges,
    syncStatus,
    triggerSync,
    isSyncing: syncStatus.status === 'syncing'
  };
};