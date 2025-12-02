import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface NetworkStatus {
  isOnline: boolean;
  connectionType?: string;
  isWiFi?: boolean;
}

interface SyncResult {
  successful: number;
  failed: number;
  remaining: number;
}

interface OfflineState {
  isOnline: boolean;
  connectionType: string | null;
  isWiFi: boolean;
  lastSyncTime: number | null;
  syncInProgress: boolean;
  queuedTransactions: number;
  syncStats: {
    totalSynced: number;
    totalFailed: number;
    lastSyncResult: SyncResult | null;
  };
}

const initialState: OfflineState = {
  isOnline: true,
  connectionType: null,
  isWiFi: false,
  lastSyncTime: null,
  syncInProgress: false,
  queuedTransactions: 0,
  syncStats: {
    totalSynced: 0,
    totalFailed: 0,
    lastSyncResult: null,
  },
};

const offlineSlice = createSlice({
  name: 'offline',
  initialState,
  reducers: {
    setNetworkStatus: (state, action: PayloadAction<NetworkStatus>) => {
      state.isOnline = action.payload.isOnline;
      state.connectionType = action.payload.connectionType || null;
      state.isWiFi = action.payload.isWiFi || false;
    },
    
    setSyncInProgress: (state, action: PayloadAction<boolean>) => {
      state.syncInProgress = action.payload;
    },
    
    updateQueuedTransactions: (state, action: PayloadAction<number>) => {
      state.queuedTransactions = action.payload;
    },
    
    syncOfflineData: (state, action: PayloadAction<SyncResult>) => {
      state.lastSyncTime = Date.now();
      state.syncInProgress = false;
      state.queuedTransactions = action.payload.remaining;
      state.syncStats.totalSynced += action.payload.successful;
      state.syncStats.totalFailed += action.payload.failed;
      state.syncStats.lastSyncResult = action.payload;
    },
    
    resetSyncStats: (state) => {
      state.syncStats = {
        totalSynced: 0,
        totalFailed: 0,
        lastSyncResult: null,
      };
    },
  },
});

export const {
  setNetworkStatus,
  setSyncInProgress,
  updateQueuedTransactions,
  syncOfflineData,
  resetSyncStats,
} = offlineSlice.actions;

export default offlineSlice.reducer;