/**
 * Offline Redux Slice
 * Manages offline state, queued actions, and sync status
 */
import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { AnyAction } from 'redux';

export interface OfflineState {
  isOffline: boolean;
  queue: AnyAction[];
  failedQueue: Array<{
    action: AnyAction;
    error: string;
    failedAt: number;
  }>;
  syncStatus: {
    status: 'idle' | 'syncing' | 'completed' | 'failed';
    progress: number;
    currentAction?: string;
    completedAt?: number;
    error?: string;
  };
  lastSyncTime: number | null;
  pendingChanges: number;
}

const initialState: OfflineState = {
  isOffline: false,
  queue: [],
  failedQueue: [],
  syncStatus: {
    status: 'idle',
    progress: 0
  },
  lastSyncTime: null,
  pendingChanges: 0
};

const offlineSlice = createSlice({
  name: 'offline',
  initialState,
  reducers: {
    setOfflineStatus: (state, action: PayloadAction<boolean>) => {
      state.isOffline = action.payload;
    },
    
    queueOfflineAction: (state, action: PayloadAction<AnyAction | AnyAction[]>) => {
      const actions = Array.isArray(action.payload) ? action.payload : [action.payload];
      state.queue.push(...actions);
      state.pendingChanges = state.queue.length;
    },
    
    removeFromQueue: (state, action: PayloadAction<number>) => {
      state.queue.splice(action.payload, 1);
      state.pendingChanges = state.queue.length;
    },
    
    clearOfflineQueue: (state) => {
      state.queue = [];
      state.pendingChanges = 0;
    },
    
    moveToFailedQueue: (state, action: PayloadAction<{ action: AnyAction; error: string }>) => {
      state.failedQueue.push({
        ...action.payload,
        failedAt: Date.now()
      });
    },
    
    retryFailedAction: (state, action: PayloadAction<number>) => {
      const failed = state.failedQueue[action.payload];
      if (failed) {
        state.queue.push(failed.action);
        state.failedQueue.splice(action.payload, 1);
        state.pendingChanges = state.queue.length;
      }
    },
    
    clearFailedQueue: (state) => {
      state.failedQueue = [];
    },
    
    updateSyncStatus: (state, action: PayloadAction<Partial<OfflineState['syncStatus']>>) => {
      state.syncStatus = {
        ...state.syncStatus,
        ...action.payload
      };
      
      if (action.payload.status === 'completed') {
        state.lastSyncTime = Date.now();
      }
    },
    
    incrementPendingChanges: (state) => {
      state.pendingChanges += 1;
    },
    
    decrementPendingChanges: (state) => {
      state.pendingChanges = Math.max(0, state.pendingChanges - 1);
    },
    
    resetOfflineState: (state) => {
      Object.assign(state, initialState);
    }
  }
});

export const {
  setOfflineStatus,
  queueOfflineAction,
  removeFromQueue,
  clearOfflineQueue,
  moveToFailedQueue,
  retryFailedAction,
  clearFailedQueue,
  updateSyncStatus,
  incrementPendingChanges,
  decrementPendingChanges,
  resetOfflineState
} = offlineSlice.actions;

export default offlineSlice.reducer;

// Selectors
export const selectIsOffline = (state: { offline: OfflineState }) => state.offline.isOffline;
export const selectOfflineQueue = (state: { offline: OfflineState }) => state.offline.queue;
export const selectFailedQueue = (state: { offline: OfflineState }) => state.offline.failedQueue;
export const selectSyncStatus = (state: { offline: OfflineState }) => state.offline.syncStatus;
export const selectPendingChanges = (state: { offline: OfflineState }) => state.offline.pendingChanges;
export const selectLastSyncTime = (state: { offline: OfflineState }) => state.offline.lastSyncTime;