/**
 * Offline Middleware
 * Redux middleware for handling offline/online state transitions and action queueing
 */
import { Middleware, MiddlewareAPI, Dispatch, AnyAction } from 'redux';
import NetInfo, { NetInfoState } from '@react-native-community/netinfo';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import { RootState } from '../types';
import { 
  setOfflineStatus, 
  queueOfflineAction, 
  clearOfflineQueue,
  syncOfflineData,
  updateSyncStatus 
} from './offlineSlice';

const OFFLINE_QUEUE_KEY = '@waqiti_offline_queue';
const SYNC_STATUS_KEY = '@waqiti_sync_status';

/**
 * Actions that should be queued when offline
 */
const QUEUEABLE_ACTION_TYPES = [
  'payment/send',
  'payment/request',
  'transfer/initiate',
  'user/updateProfile',
  'crypto/sendTransaction',
  'investment/createOrder',
  'bnpl/createApplication',
];

/**
 * Actions that need optimistic updates
 */
const OPTIMISTIC_ACTION_TYPES = [
  'payment/send',
  'transfer/initiate',
  'user/updateProfile',
];

export const createOfflineMiddleware = (): Middleware => {
  let isOnline = true;
  let syncInProgress = false;
  let unsubscribeNetInfo: (() => void) | null = null;

  return (store: MiddlewareAPI<Dispatch<AnyAction>, RootState>) => {
    // Initialize network listener
    const initializeNetworkListener = () => {
      unsubscribeNetInfo = NetInfo.addEventListener((state: NetInfoState) => {
        const wasOffline = !isOnline;
        isOnline = state.isConnected ?? false;

        store.dispatch(setOfflineStatus(!isOnline));

        // If we just came online, sync offline data
        if (wasOffline && isOnline && !syncInProgress) {
          syncOfflineData(store);
        }
      });
    };

    // Load queued actions from storage on startup
    const loadQueuedActions = async () => {
      try {
        const queuedActionsJson = await AsyncStorage.getItem(OFFLINE_QUEUE_KEY);
        if (queuedActionsJson) {
          const queuedActions = JSON.parse(queuedActionsJson);
          store.dispatch(queueOfflineAction(queuedActions));
        }
      } catch (error) {
        console.error('Failed to load offline queue:', error);
      }
    };

    // Initialize on first call
    initializeNetworkListener();
    loadQueuedActions();

    return (next: Dispatch<AnyAction>) => async (action: AnyAction) => {
      // Check if action should be queued when offline
      const shouldQueue = QUEUEABLE_ACTION_TYPES.some(type => 
        action.type.startsWith(type)
      );

      // If offline and action should be queued
      if (!isOnline && shouldQueue) {
        // Create offline action with metadata
        const offlineAction = {
          ...action,
          meta: {
            ...action.meta,
            offline: {
              queuedAt: Date.now(),
              deviceId: Platform.OS,
              retry: {
                count: 0,
                maxRetries: 3,
                backoff: 'exponential'
              }
            }
          }
        };

        // Queue the action
        store.dispatch(queueOfflineAction(offlineAction));

        // Save to AsyncStorage
        await saveOfflineQueue(store.getState().offline.queue);

        // Apply optimistic update if needed
        if (OPTIMISTIC_ACTION_TYPES.includes(action.type)) {
          return next({
            ...action,
            meta: {
              ...action.meta,
              optimistic: true
            }
          });
        }

        // Prevent action from proceeding
        return;
      }

      return next(action);
    };
  };
};

/**
 * Sync offline data when connection is restored
 */
const syncOfflineData = async (store: MiddlewareAPI<Dispatch<AnyAction>, RootState>) => {
  store.dispatch(updateSyncStatus({ status: 'syncing', progress: 0 }));

  try {
    const state = store.getState();
    const queue = state.offline.queue;
    const totalActions = queue.length;

    // Process queued actions
    for (let i = 0; i < queue.length; i++) {
      const action = queue[i];
      
      try {
        // Dispatch the action
        await processOfflineAction(store, action);
        
        // Update progress
        const progress = ((i + 1) / totalActions) * 100;
        store.dispatch(updateSyncStatus({ 
          status: 'syncing', 
          progress,
          currentAction: action.type 
        }));

      } catch (error) {
        console.error('Failed to sync action:', action.type, error);
        
        // Handle retry logic
        if (shouldRetry(action)) {
          await retryAction(store, action);
        } else {
          // Move to failed queue
          store.dispatch({
            type: 'offline/moveToFailedQueue',
            payload: { action, error: error.message }
          });
        }
      }
    }

    // Clear successfully synced actions
    store.dispatch(clearOfflineQueue());
    await AsyncStorage.removeItem(OFFLINE_QUEUE_KEY);

    // Sync other offline data
    await syncOfflineTransactions(store);
    await syncOfflineUserData(store);

    store.dispatch(updateSyncStatus({ 
      status: 'completed', 
      progress: 100,
      completedAt: Date.now()
    }));

  } catch (error) {
    console.error('Sync failed:', error);
    store.dispatch(updateSyncStatus({ 
      status: 'failed', 
      error: error.message 
    }));
  }
};

/**
 * Process a single offline action
 */
const processOfflineAction = async (
  store: MiddlewareAPI<Dispatch<AnyAction>, RootState>, 
  action: AnyAction
): Promise<void> => {
  // Remove offline metadata
  const { meta: { offline, ...metaRest }, ...actionRest } = action;
  const cleanAction = {
    ...actionRest,
    meta: metaRest
  };

  // Add sync flag to prevent re-queueing
  cleanAction.meta.isSync = true;

  // Dispatch the action
  return new Promise((resolve, reject) => {
    store.dispatch({
      ...cleanAction,
      meta: {
        ...cleanAction.meta,
        onSuccess: resolve,
        onError: reject
      }
    });
  });
};

/**
 * Check if action should be retried
 */
const shouldRetry = (action: AnyAction): boolean => {
  const offline = action.meta?.offline;
  if (!offline) return false;

  const { retry } = offline;
  return retry.count < retry.maxRetries;
};

/**
 * Retry failed action with backoff
 */
const retryAction = async (
  store: MiddlewareAPI<Dispatch<AnyAction>, RootState>, 
  action: AnyAction
): Promise<void> => {
  const offline = action.meta.offline;
  const retryCount = offline.retry.count;
  
  // Calculate backoff delay
  const delay = Math.min(1000 * Math.pow(2, retryCount), 30000); // Max 30 seconds
  
  await new Promise(resolve => setTimeout(resolve, delay));
  
  // Update retry count
  const updatedAction = {
    ...action,
    meta: {
      ...action.meta,
      offline: {
        ...offline,
        retry: {
          ...offline.retry,
          count: retryCount + 1
        }
      }
    }
  };
  
  return processOfflineAction(store, updatedAction);
};

/**
 * Save offline queue to AsyncStorage
 */
const saveOfflineQueue = async (queue: AnyAction[]): Promise<void> => {
  try {
    await AsyncStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(queue));
  } catch (error) {
    console.error('Failed to save offline queue:', error);
  }
};

/**
 * Sync offline transactions
 */
const syncOfflineTransactions = async (
  store: MiddlewareAPI<Dispatch<AnyAction>, RootState>
): Promise<void> => {
  try {
    // Get locally stored transactions
    const localTransactions = await AsyncStorage.getItem('@waqiti_offline_transactions');
    if (!localTransactions) return;

    const transactions = JSON.parse(localTransactions);
    
    // Sync each transaction
    for (const transaction of transactions) {
      await store.dispatch({
        type: 'transactions/sync',
        payload: transaction
      });
    }

    // Clear local storage
    await AsyncStorage.removeItem('@waqiti_offline_transactions');
  } catch (error) {
    console.error('Failed to sync offline transactions:', error);
  }
};

/**
 * Sync offline user data
 */
const syncOfflineUserData = async (
  store: MiddlewareAPI<Dispatch<AnyAction>, RootState>
): Promise<void> => {
  try {
    // Get locally stored user updates
    const localUserData = await AsyncStorage.getItem('@waqiti_offline_user_data');
    if (!localUserData) return;

    const userData = JSON.parse(localUserData);
    
    // Sync user data
    await store.dispatch({
      type: 'user/syncOfflineData',
      payload: userData
    });

    // Clear local storage
    await AsyncStorage.removeItem('@waqiti_offline_user_data');
  } catch (error) {
    console.error('Failed to sync offline user data:', error);
  }
};

export default createOfflineMiddleware;