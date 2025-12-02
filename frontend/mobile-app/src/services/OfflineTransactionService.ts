import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo, { NetInfoState } from '@react-native-community/netinfo';
import { store } from '../store';
import { 
  addOfflineTransaction, 
  removeOfflineTransaction, 
  updateTransactionStatus,
  syncOfflineTransactions 
} from '../store/slices/offlineSlice';
import { ApiService } from './ApiService';
import EncryptionService from './encryption/EncryptionService';
import { BiometricService } from './BiometricService';
import { EventEmitter } from 'eventemitter3';
import uuid from 'react-native-uuid';

interface OfflineTransaction {
  id: string;
  type: 'payment' | 'request' | 'transfer';
  amount: number;
  currency: string;
  recipientId?: string;
  recipientPhone?: string;
  recipientEmail?: string;
  description?: string;
  metadata: {
    createdAt: string;
    deviceId: string;
    location?: {
      latitude: number;
      longitude: number;
    };
    networkType?: string;
    batteryLevel?: number;
  };
  status: 'pending' | 'syncing' | 'completed' | 'failed';
  retryCount: number;
  encryptedData?: string;
  signature?: string;
}

interface SyncResult {
  successful: string[];
  failed: Array<{
    transactionId: string;
    error: string;
  }>;
  totalSynced: number;
}

class OfflineTransactionService extends EventEmitter {
  private static instance: OfflineTransactionService;
  private isOnline: boolean = true;
  private syncInProgress: boolean = false;
  private syncQueue: OfflineTransaction[] = [];
  private maxRetries: number = 3;
  private syncInterval: NodeJS.Timeout | null = null;
  private readonly STORAGE_KEY = '@offline_transactions';
  private readonly MAX_OFFLINE_TRANSACTIONS = 50;
  private readonly MAX_OFFLINE_AMOUNT = 5000; // Maximum amount for offline transactions

  static getInstance(): OfflineTransactionService {
    if (!OfflineTransactionService.instance) {
      OfflineTransactionService.instance = new OfflineTransactionService();
    }
    return OfflineTransactionService.instance;
  }

  constructor() {
    super();
    this.initialize();
  }

  private async initialize(): Promise<void> {
    // Monitor network connectivity
    NetInfo.addEventListener(this.handleConnectivityChange.bind(this));
    
    // Get initial network state
    const netState = await NetInfo.fetch();
    this.isOnline = netState.isConnected || false;
    
    // Load pending transactions from storage
    await this.loadPendingTransactions();
    
    // Start sync interval
    this.startSyncInterval();
    
    // Listen for app state changes
    this.setupAppStateListener();
  }

  private handleConnectivityChange(state: NetInfoState): void {
    const wasOffline = !this.isOnline;
    this.isOnline = state.isConnected || false;
    
    console.log(`Network status changed: ${this.isOnline ? 'Online' : 'Offline'}`);
    
    // Emit connectivity change event
    this.emit('connectivityChange', this.isOnline);
    
    // If we just came online, trigger sync
    if (wasOffline && this.isOnline) {
      this.triggerSync();
    }
  }

  private async loadPendingTransactions(): Promise<void> {
    try {
      const storedTransactions = await AsyncStorage.getItem(this.STORAGE_KEY);
      if (storedTransactions) {
        const transactions = JSON.parse(storedTransactions) as OfflineTransaction[];
        this.syncQueue = transactions.filter(t => t.status === 'pending');
        
        // Update Redux store
        store.dispatch(syncOfflineTransactions(this.syncQueue));
      }
    } catch (error) {
      console.error('Failed to load offline transactions:', error);
    }
  }

  private async saveTransactions(): Promise<void> {
    try {
      await AsyncStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.syncQueue));
    } catch (error) {
      console.error('Failed to save offline transactions:', error);
    }
  }

  private startSyncInterval(): void {
    // Sync every 30 seconds when online
    this.syncInterval = setInterval(() => {
      if (this.isOnline && !this.syncInProgress && this.syncQueue.length > 0) {
        this.triggerSync();
      }
    }, 30000);
  }

  private setupAppStateListener(): void {
    // Import AppState from react-native
    const { AppState } = require('react-native');
    
    AppState.addEventListener('change', (nextAppState: string) => {
      if (nextAppState === 'active' && this.isOnline) {
        // App came to foreground, trigger sync
        this.triggerSync();
      }
    });
  }

  async createOfflineTransaction(
    type: OfflineTransaction['type'],
    data: {
      amount: number;
      currency: string;
      recipientId?: string;
      recipientPhone?: string;
      recipientEmail?: string;
      description?: string;
    }
  ): Promise<OfflineTransaction> {
    // Validate transaction limits
    if (data.amount > this.MAX_OFFLINE_AMOUNT) {
      throw new Error(`Offline transactions cannot exceed ${this.MAX_OFFLINE_AMOUNT} ${data.currency}`);
    }
    
    if (this.syncQueue.length >= this.MAX_OFFLINE_TRANSACTIONS) {
      throw new Error('Maximum offline transaction limit reached');
    }
    
    // Create transaction object
    const transaction: OfflineTransaction = {
      id: uuid.v4() as string,
      type,
      amount: data.amount,
      currency: data.currency,
      recipientId: data.recipientId,
      recipientPhone: data.recipientPhone,
      recipientEmail: data.recipientEmail,
      description: data.description,
      metadata: {
        createdAt: new Date().toISOString(),
        deviceId: await this.getDeviceId(),
        location: await this.getCurrentLocation(),
        networkType: await this.getNetworkType(),
        batteryLevel: await this.getBatteryLevel(),
      },
      status: 'pending',
      retryCount: 0,
    };
    
    // Encrypt sensitive data
    const encryptedData = await EncryptionService.encryptOfflineTransaction(transaction);
    transaction.encryptedData = encryptedData;
    
    // Generate digital signature
    const signature = await this.generateTransactionSignature(transaction);
    transaction.signature = signature;
    
    // Add to queue
    this.syncQueue.push(transaction);
    
    // Save to storage
    await this.saveTransactions();
    
    // Update Redux store
    store.dispatch(addOfflineTransaction(transaction));
    
    // Emit event
    this.emit('transactionCreated', transaction);
    
    // Try to sync immediately if online
    if (this.isOnline) {
      this.triggerSync();
    }
    
    return transaction;
  }

  private async generateTransactionSignature(transaction: OfflineTransaction): Promise<string> {
    // Use biometric authentication to sign transaction
    const biometricAuth = await BiometricService.authenticate({
      reason: 'Sign offline transaction',
      fallbackToPasscode: true,
    });
    
    if (!biometricAuth.success) {
      throw new Error('Biometric authentication failed');
    }
    
    // Generate signature using device-specific key
    const dataToSign = JSON.stringify({
      id: transaction.id,
      type: transaction.type,
      amount: transaction.amount,
      currency: transaction.currency,
      recipientId: transaction.recipientId,
      timestamp: transaction.metadata.createdAt,
    });
    
    return await EncryptionService.signData(dataToSign);
  }

  async triggerSync(): Promise<SyncResult> {
    if (this.syncInProgress || !this.isOnline || this.syncQueue.length === 0) {
      return {
        successful: [],
        failed: [],
        totalSynced: 0,
      };
    }
    
    this.syncInProgress = true;
    this.emit('syncStarted');
    
    const result: SyncResult = {
      successful: [],
      failed: [],
      totalSynced: 0,
    };
    
    try {
      // Sort transactions by creation time
      const sortedQueue = [...this.syncQueue].sort((a, b) => 
        new Date(a.metadata.createdAt).getTime() - new Date(b.metadata.createdAt).getTime()
      );
      
      for (const transaction of sortedQueue) {
        try {
          // Update status to syncing
          store.dispatch(updateTransactionStatus({
            id: transaction.id,
            status: 'syncing',
          }));
          
          // Attempt to sync transaction
          const syncResult = await this.syncSingleTransaction(transaction);
          
          if (syncResult.success) {
            result.successful.push(transaction.id);
            result.totalSynced++;
            
            // Remove from queue
            this.syncQueue = this.syncQueue.filter(t => t.id !== transaction.id);
            store.dispatch(removeOfflineTransaction(transaction.id));
            
            // Emit success event
            this.emit('transactionSynced', transaction);
          } else {
            throw new Error(syncResult.error);
          }
          
        } catch (error: any) {
          console.error(`Failed to sync transaction ${transaction.id}:`, error);
          
          transaction.retryCount++;
          
          if (transaction.retryCount >= this.maxRetries) {
            // Mark as failed after max retries
            store.dispatch(updateTransactionStatus({
              id: transaction.id,
              status: 'failed',
            }));
            
            result.failed.push({
              transactionId: transaction.id,
              error: error.message,
            });
            
            // Remove from queue
            this.syncQueue = this.syncQueue.filter(t => t.id !== transaction.id);
            
            // Emit failure event
            this.emit('transactionFailed', transaction, error);
          } else {
            // Keep in queue for retry
            store.dispatch(updateTransactionStatus({
              id: transaction.id,
              status: 'pending',
            }));
          }
        }
        
        // Add delay between transactions
        await this.delay(500);
      }
      
      // Save updated queue
      await this.saveTransactions();
      
    } catch (error) {
      console.error('Sync process failed:', error);
    } finally {
      this.syncInProgress = false;
      this.emit('syncCompleted', result);
    }
    
    return result;
  }

  private async syncSingleTransaction(transaction: OfflineTransaction): Promise<{
    success: boolean;
    error?: string;
    onlineTransactionId?: string;
  }> {
    try {
      // Verify signature
      const isValid = await EncryptionService.verifySignature(
        transaction.encryptedData!,
        transaction.signature!
      );
      
      if (!isValid) {
        throw new Error('Invalid transaction signature');
      }
      
      // Decrypt transaction data
      const decryptedData = await EncryptionService.decryptOfflineTransaction(
        transaction.encryptedData!
      );
      
      // Submit to API based on transaction type
      let response;
      switch (transaction.type) {
        case 'payment':
          response = await ApiService.createPayment({
            amount: transaction.amount,
            currency: transaction.currency,
            recipientId: transaction.recipientId,
            recipientPhone: transaction.recipientPhone,
            recipientEmail: transaction.recipientEmail,
            description: transaction.description,
            offlineTransactionId: transaction.id,
            metadata: transaction.metadata,
          });
          break;
          
        case 'request':
          response = await ApiService.createMoneyRequest({
            amount: transaction.amount,
            currency: transaction.currency,
            fromUserId: transaction.recipientId,
            description: transaction.description,
            offlineTransactionId: transaction.id,
            metadata: transaction.metadata,
          });
          break;
          
        case 'transfer':
          response = await ApiService.createTransfer({
            amount: transaction.amount,
            currency: transaction.currency,
            toAccountId: transaction.recipientId!,
            description: transaction.description,
            offlineTransactionId: transaction.id,
            metadata: transaction.metadata,
          });
          break;
      }
      
      return {
        success: true,
        onlineTransactionId: response.transactionId,
      };
      
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Unknown error occurred',
      };
    }
  }

  async cancelOfflineTransaction(transactionId: string): Promise<void> {
    // Remove from queue
    this.syncQueue = this.syncQueue.filter(t => t.id !== transactionId);
    
    // Update storage
    await this.saveTransactions();
    
    // Update Redux store
    store.dispatch(removeOfflineTransaction(transactionId));
    
    // Emit event
    this.emit('transactionCancelled', transactionId);
  }

  async getOfflineTransactions(): Promise<OfflineTransaction[]> {
    return [...this.syncQueue];
  }

  async getOfflineBalance(): Promise<{
    [currency: string]: {
      total: number;
      count: number;
    };
  }> {
    const balance: { [currency: string]: { total: number; count: number } } = {};
    
    for (const transaction of this.syncQueue) {
      if (!balance[transaction.currency]) {
        balance[transaction.currency] = { total: 0, count: 0 };
      }
      
      balance[transaction.currency].total += transaction.amount;
      balance[transaction.currency].count += 1;
    }
    
    return balance;
  }

  private async getDeviceId(): Promise<string> {
    let deviceId = await AsyncStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = uuid.v4() as string;
      await AsyncStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  }

  private async getCurrentLocation(): Promise<{ latitude: number; longitude: number } | undefined> {
    try {
      const { Geolocation } = require('@react-native-community/geolocation');
      
      return new Promise((resolve) => {
        Geolocation.getCurrentPosition(
          (position) => {
            resolve({
              latitude: position.coords.latitude,
              longitude: position.coords.longitude,
            });
          },
          () => resolve(undefined),
          { enableHighAccuracy: false, timeout: 5000 }
        );
      });
    } catch {
      return undefined;
    }
  }

  private async getNetworkType(): Promise<string | undefined> {
    try {
      const netState = await NetInfo.fetch();
      return netState.type;
    } catch {
      return undefined;
    }
  }

  private async getBatteryLevel(): Promise<number | undefined> {
    try {
      const { NativeModules } = require('react-native');
      const batteryLevel = await NativeModules.BatteryManager?.getBatteryLevel();
      return batteryLevel;
    } catch {
      return undefined;
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  isNetworkOnline(): boolean {
    return this.isOnline;
  }

  isSyncing(): boolean {
    return this.syncInProgress;
  }

  getPendingCount(): number {
    return this.syncQueue.length;
  }

  destroy(): void {
    if (this.syncInterval) {
      clearInterval(this.syncInterval);
    }
    this.removeAllListeners();
  }
}

export default OfflineTransactionService.getInstance();