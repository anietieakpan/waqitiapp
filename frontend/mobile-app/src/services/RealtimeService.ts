/**
 * Realtime Service - WebSocket and real-time updates management
 * Handles transaction status updates, notifications, and live data synchronization
 */

import { io, Socket } from 'socket.io-client';
import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';
import { EventEmitter } from 'events';
import Config from 'react-native-config';

export enum RealtimeEvent {
  // Transaction Events
  TRANSACTION_CREATED = 'transaction:created',
  TRANSACTION_UPDATED = 'transaction:updated',
  TRANSACTION_COMPLETED = 'transaction:completed',
  TRANSACTION_FAILED = 'transaction:failed',
  TRANSACTION_PENDING = 'transaction:pending',
  
  // Payment Events
  PAYMENT_RECEIVED = 'payment:received',
  PAYMENT_SENT = 'payment:sent',
  PAYMENT_REQUESTED = 'payment:requested',
  PAYMENT_CANCELLED = 'payment:cancelled',
  
  // Account Events
  ACCOUNT_UPDATED = 'account:updated',
  BALANCE_CHANGED = 'balance:changed',
  LIMIT_EXCEEDED = 'limit:exceeded',
  
  // Check Deposit Events
  CHECK_DEPOSIT_RECEIVED = 'check:deposit:received',
  CHECK_DEPOSIT_PROCESSING = 'check:deposit:processing',
  CHECK_DEPOSIT_COMPLETED = 'check:deposit:completed',
  CHECK_DEPOSIT_REJECTED = 'check:deposit:rejected',
  
  // Notification Events
  NOTIFICATION_RECEIVED = 'notification:received',
  ALERT_RECEIVED = 'alert:received',
  
  // Connection Events
  CONNECTED = 'connected',
  DISCONNECTED = 'disconnected',
  RECONNECTING = 'reconnecting',
  ERROR = 'error',
}

export interface TransactionUpdate {
  transactionId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed' | 'cancelled';
  previousStatus?: string;
  amount: number;
  currency: string;
  timestamp: string;
  details?: {
    fromWallet?: string;
    toWallet?: string;
    type?: string;
    description?: string;
    fee?: number;
    errorReason?: string;
  };
  metadata?: Record<string, any>;
}

export interface BalanceUpdate {
  walletId: string;
  previousBalance: number;
  currentBalance: number;
  availableBalance: number;
  pendingBalance: number;
  currency: string;
  timestamp: string;
  triggeringTransaction?: string;
}

export interface CheckDepositUpdate {
  depositId: string;
  status: 'received' | 'processing' | 'completed' | 'rejected';
  amount: number;
  estimatedAvailability?: string;
  actualAvailability?: string;
  rejectionReason?: string;
  timestamp: string;
}

export interface RealtimeNotification {
  id: string;
  type: 'info' | 'success' | 'warning' | 'error';
  title: string;
  message: string;
  timestamp: string;
  data?: Record<string, any>;
  actions?: Array<{
    label: string;
    action: string;
    data?: any;
  }>;
}

interface RealtimeConfig {
  url?: string;
  autoConnect?: boolean;
  reconnection?: boolean;
  reconnectionAttempts?: number;
  reconnectionDelay?: number;
  reconnectionDelayMax?: number;
  timeout?: number;
  transports?: string[];
  secure?: boolean;
}

class RealtimeService extends EventEmitter {
  private static instance: RealtimeService;
  private socket: Socket | null = null;
  private config: RealtimeConfig;
  private isConnected: boolean = false;
  private reconnectAttempts: number = 0;
  private subscriptions: Map<string, Set<string>> = new Map();
  private messageQueue: any[] = [];
  private authToken: string | null = null;
  private userId: string | null = null;
  private networkUnsubscribe: (() => void) | null = null;

  private constructor() {
    super();
    
    this.config = {
      url: Config.WEBSOCKET_URL || 'wss://api.example.com',
      autoConnect: false,
      reconnection: true,
      reconnectionAttempts: 5,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 5000,
      timeout: 20000,
      transports: ['websocket'],
      secure: true,
    };

    this.setupNetworkListener();
  }

  static getInstance(): RealtimeService {
    if (!RealtimeService.instance) {
      RealtimeService.instance = new RealtimeService();
    }
    return RealtimeService.instance;
  }

  /**
   * Initialize and connect to WebSocket server
   */
  async initialize(userId: string, authToken: string): Promise<void> {
    this.userId = userId;
    this.authToken = authToken;

    if (this.socket?.connected) {
      console.log('WebSocket already connected');
      return;
    }

    return new Promise((resolve, reject) => {
      try {
        this.socket = io(this.config.url!, {
          ...this.config,
          auth: {
            token: authToken,
            userId,
          },
          query: {
            userId,
            platform: 'mobile',
          },
        });

        this.setupEventHandlers();

        const connectTimeout = setTimeout(() => {
          reject(new Error('WebSocket connection timeout'));
        }, this.config.timeout!);

        this.socket.once('connect', () => {
          clearTimeout(connectTimeout);
          this.isConnected = true;
          this.reconnectAttempts = 0;
          this.processQueuedMessages();
          this.emit(RealtimeEvent.CONNECTED);
          console.log('WebSocket connected successfully');
          resolve();
        });

        this.socket.once('connect_error', (error) => {
          clearTimeout(connectTimeout);
          console.error('WebSocket connection error:', error);
          reject(error);
        });

      } catch (error) {
        console.error('Failed to initialize WebSocket:', error);
        reject(error);
      }
    });
  }

  /**
   * Setup WebSocket event handlers
   */
  private setupEventHandlers(): void {
    if (!this.socket) return;

    // Connection events
    this.socket.on('connect', () => {
      this.isConnected = true;
      this.reconnectAttempts = 0;
      this.resubscribeAll();
      this.emit(RealtimeEvent.CONNECTED);
    });

    this.socket.on('disconnect', (reason) => {
      this.isConnected = false;
      this.emit(RealtimeEvent.DISCONNECTED, reason);
      console.log('WebSocket disconnected:', reason);
    });

    this.socket.on('reconnecting', (attemptNumber) => {
      this.reconnectAttempts = attemptNumber;
      this.emit(RealtimeEvent.RECONNECTING, attemptNumber);
    });

    this.socket.on('error', (error) => {
      console.error('WebSocket error:', error);
      this.emit(RealtimeEvent.ERROR, error);
    });

    // Transaction events
    this.socket.on(RealtimeEvent.TRANSACTION_UPDATED, (data: TransactionUpdate) => {
      this.handleTransactionUpdate(data);
    });

    this.socket.on(RealtimeEvent.TRANSACTION_COMPLETED, (data: TransactionUpdate) => {
      this.handleTransactionCompleted(data);
    });

    this.socket.on(RealtimeEvent.TRANSACTION_FAILED, (data: TransactionUpdate) => {
      this.handleTransactionFailed(data);
    });

    // Payment events
    this.socket.on(RealtimeEvent.PAYMENT_RECEIVED, (data: any) => {
      this.emit(RealtimeEvent.PAYMENT_RECEIVED, data);
    });

    this.socket.on(RealtimeEvent.PAYMENT_SENT, (data: any) => {
      this.emit(RealtimeEvent.PAYMENT_SENT, data);
    });

    // Balance events
    this.socket.on(RealtimeEvent.BALANCE_CHANGED, (data: BalanceUpdate) => {
      this.handleBalanceUpdate(data);
    });

    // Check deposit events
    this.socket.on(RealtimeEvent.CHECK_DEPOSIT_PROCESSING, (data: CheckDepositUpdate) => {
      this.emit(RealtimeEvent.CHECK_DEPOSIT_PROCESSING, data);
    });

    this.socket.on(RealtimeEvent.CHECK_DEPOSIT_COMPLETED, (data: CheckDepositUpdate) => {
      this.emit(RealtimeEvent.CHECK_DEPOSIT_COMPLETED, data);
    });

    this.socket.on(RealtimeEvent.CHECK_DEPOSIT_REJECTED, (data: CheckDepositUpdate) => {
      this.emit(RealtimeEvent.CHECK_DEPOSIT_REJECTED, data);
    });

    // Notification events
    this.socket.on(RealtimeEvent.NOTIFICATION_RECEIVED, (data: RealtimeNotification) => {
      this.handleNotification(data);
    });

    this.socket.on(RealtimeEvent.ALERT_RECEIVED, (data: RealtimeNotification) => {
      this.handleAlert(data);
    });
  }

  /**
   * Handle transaction update
   */
  private handleTransactionUpdate(update: TransactionUpdate): void {
    // Store update in cache
    this.cacheTransactionUpdate(update);
    
    // Emit event for listeners
    this.emit(RealtimeEvent.TRANSACTION_UPDATED, update);
    
    // Emit specific status events
    switch (update.status) {
      case 'pending':
        this.emit(RealtimeEvent.TRANSACTION_PENDING, update);
        break;
      case 'processing':
        // Already emitting TRANSACTION_UPDATED
        break;
      case 'completed':
        this.emit(RealtimeEvent.TRANSACTION_COMPLETED, update);
        break;
      case 'failed':
        this.emit(RealtimeEvent.TRANSACTION_FAILED, update);
        break;
    }
  }

  /**
   * Handle transaction completion
   */
  private handleTransactionCompleted(update: TransactionUpdate): void {
    this.cacheTransactionUpdate(update);
    this.emit(RealtimeEvent.TRANSACTION_COMPLETED, update);
    
    // Show success notification
    this.handleNotification({
      id: `txn-${update.transactionId}`,
      type: 'success',
      title: 'Transaction Completed',
      message: `Your transaction of ${update.amount} ${update.currency} has been completed successfully.`,
      timestamp: update.timestamp,
      data: { transactionId: update.transactionId },
    });
  }

  /**
   * Handle transaction failure
   */
  private handleTransactionFailed(update: TransactionUpdate): void {
    this.cacheTransactionUpdate(update);
    this.emit(RealtimeEvent.TRANSACTION_FAILED, update);
    
    // Show error notification
    this.handleNotification({
      id: `txn-${update.transactionId}`,
      type: 'error',
      title: 'Transaction Failed',
      message: update.details?.errorReason || 'Your transaction could not be completed.',
      timestamp: update.timestamp,
      data: { transactionId: update.transactionId },
      actions: [
        {
          label: 'Retry',
          action: 'retry_transaction',
          data: { transactionId: update.transactionId },
        },
        {
          label: 'View Details',
          action: 'view_transaction',
          data: { transactionId: update.transactionId },
        },
      ],
    });
  }

  /**
   * Handle balance update
   */
  private handleBalanceUpdate(update: BalanceUpdate): void {
    // Cache balance update
    this.cacheBalanceUpdate(update);
    
    // Emit event
    this.emit(RealtimeEvent.BALANCE_CHANGED, update);
    
    // Check for significant changes
    const change = update.currentBalance - update.previousBalance;
    if (Math.abs(change) > 100) {
      this.handleNotification({
        id: `balance-${update.walletId}-${Date.now()}`,
        type: 'info',
        title: 'Balance Updated',
        message: `Your balance has ${change > 0 ? 'increased' : 'decreased'} by ${Math.abs(change)} ${update.currency}`,
        timestamp: update.timestamp,
        data: { walletId: update.walletId },
      });
    }
  }

  /**
   * Handle notification
   */
  private handleNotification(notification: RealtimeNotification): void {
    // Store notification
    this.storeNotification(notification);
    
    // Emit event
    this.emit(RealtimeEvent.NOTIFICATION_RECEIVED, notification);
  }

  /**
   * Handle alert
   */
  private handleAlert(alert: RealtimeNotification): void {
    // Store alert with higher priority
    this.storeNotification(alert, true);
    
    // Emit event
    this.emit(RealtimeEvent.ALERT_RECEIVED, alert);
  }

  /**
   * Subscribe to specific transaction updates
   */
  subscribeToTransaction(transactionId: string): void {
    if (!this.socket?.connected) {
      console.warn('WebSocket not connected. Queueing subscription.');
      this.messageQueue.push({
        type: 'subscribe',
        channel: 'transaction',
        id: transactionId,
      });
      return;
    }

    this.socket.emit('subscribe:transaction', { transactionId });
    
    // Track subscription
    if (!this.subscriptions.has('transaction')) {
      this.subscriptions.set('transaction', new Set());
    }
    this.subscriptions.get('transaction')!.add(transactionId);
  }

  /**
   * Unsubscribe from transaction updates
   */
  unsubscribeFromTransaction(transactionId: string): void {
    if (this.socket?.connected) {
      this.socket.emit('unsubscribe:transaction', { transactionId });
    }
    
    // Remove from tracking
    this.subscriptions.get('transaction')?.delete(transactionId);
  }

  /**
   * Subscribe to wallet updates
   */
  subscribeToWallet(walletId: string): void {
    if (!this.socket?.connected) {
      console.warn('WebSocket not connected. Queueing subscription.');
      this.messageQueue.push({
        type: 'subscribe',
        channel: 'wallet',
        id: walletId,
      });
      return;
    }

    this.socket.emit('subscribe:wallet', { walletId });
    
    // Track subscription
    if (!this.subscriptions.has('wallet')) {
      this.subscriptions.set('wallet', new Set());
    }
    this.subscriptions.get('wallet')!.add(walletId);
  }

  /**
   * Subscribe to check deposit updates
   */
  subscribeToCheckDeposit(depositId: string): void {
    if (!this.socket?.connected) {
      console.warn('WebSocket not connected. Queueing subscription.');
      this.messageQueue.push({
        type: 'subscribe',
        channel: 'check_deposit',
        id: depositId,
      });
      return;
    }

    this.socket.emit('subscribe:check_deposit', { depositId });
    
    // Track subscription
    if (!this.subscriptions.has('check_deposit')) {
      this.subscriptions.set('check_deposit', new Set());
    }
    this.subscriptions.get('check_deposit')!.add(depositId);
  }

  /**
   * Process queued messages after connection
   */
  private processQueuedMessages(): void {
    while (this.messageQueue.length > 0) {
      const message = this.messageQueue.shift();
      
      switch (message.type) {
        case 'subscribe':
          if (message.channel === 'transaction') {
            this.subscribeToTransaction(message.id);
          } else if (message.channel === 'wallet') {
            this.subscribeToWallet(message.id);
          } else if (message.channel === 'check_deposit') {
            this.subscribeToCheckDeposit(message.id);
          }
          break;
      }
    }
  }

  /**
   * Resubscribe to all channels after reconnection
   */
  private resubscribeAll(): void {
    this.subscriptions.forEach((ids, channel) => {
      ids.forEach((id) => {
        switch (channel) {
          case 'transaction':
            this.subscribeToTransaction(id);
            break;
          case 'wallet':
            this.subscribeToWallet(id);
            break;
          case 'check_deposit':
            this.subscribeToCheckDeposit(id);
            break;
        }
      });
    });
  }

  /**
   * Cache transaction update
   */
  private async cacheTransactionUpdate(update: TransactionUpdate): Promise<void> {
    try {
      const key = `@transaction_update_${update.transactionId}`;
      await AsyncStorage.setItem(key, JSON.stringify(update));
      
      // Also update transaction list cache
      const listKey = '@transaction_updates';
      const existingData = await AsyncStorage.getItem(listKey);
      const updates = existingData ? JSON.parse(existingData) : [];
      updates.unshift(update);
      
      // Keep only last 100 updates
      if (updates.length > 100) {
        updates.splice(100);
      }
      
      await AsyncStorage.setItem(listKey, JSON.stringify(updates));
    } catch (error) {
      console.error('Failed to cache transaction update:', error);
    }
  }

  /**
   * Cache balance update
   */
  private async cacheBalanceUpdate(update: BalanceUpdate): Promise<void> {
    try {
      const key = `@balance_${update.walletId}`;
      await AsyncStorage.setItem(key, JSON.stringify(update));
    } catch (error) {
      console.error('Failed to cache balance update:', error);
    }
  }

  /**
   * Store notification
   */
  private async storeNotification(notification: RealtimeNotification, isAlert: boolean = false): Promise<void> {
    try {
      const key = isAlert ? '@alerts' : '@notifications';
      const existingData = await AsyncStorage.getItem(key);
      const notifications = existingData ? JSON.parse(existingData) : [];
      
      notifications.unshift(notification);
      
      // Keep only last 50 notifications/alerts
      if (notifications.length > 50) {
        notifications.splice(50);
      }
      
      await AsyncStorage.setItem(key, JSON.stringify(notifications));
    } catch (error) {
      console.error('Failed to store notification:', error);
    }
  }

  /**
   * Setup network connectivity listener
   */
  private setupNetworkListener(): void {
    this.networkUnsubscribe = NetInfo.addEventListener((state) => {
      if (state.isConnected && !this.isConnected && this.authToken && this.userId) {
        console.log('Network reconnected, attempting WebSocket reconnection');
        this.reconnect();
      }
    });
  }

  /**
   * Reconnect to WebSocket
   */
  private async reconnect(): Promise<void> {
    if (!this.authToken || !this.userId) {
      console.warn('Cannot reconnect: missing auth credentials');
      return;
    }

    try {
      await this.initialize(this.userId, this.authToken);
    } catch (error) {
      console.error('Failed to reconnect:', error);
      
      // Retry with exponential backoff
      if (this.reconnectAttempts < this.config.reconnectionAttempts!) {
        const delay = Math.min(
          this.config.reconnectionDelay! * Math.pow(2, this.reconnectAttempts),
          this.config.reconnectionDelayMax!
        );
        
        setTimeout(() => {
          this.reconnect();
        }, delay);
      }
    }
  }

  /**
   * Get cached transaction updates
   */
  async getCachedTransactionUpdates(): Promise<TransactionUpdate[]> {
    try {
      const data = await AsyncStorage.getItem('@transaction_updates');
      return data ? JSON.parse(data) : [];
    } catch (error) {
      console.error('Failed to get cached transaction updates:', error);
      return [];
    }
  }

  /**
   * Get cached balance for wallet
   */
  async getCachedBalance(walletId: string): Promise<BalanceUpdate | null> {
    try {
      const data = await AsyncStorage.getItem(`@balance_${walletId}`);
      return data ? JSON.parse(data) : null;
    } catch (error) {
      console.error('Failed to get cached balance:', error);
      return null;
    }
  }

  /**
   * Disconnect WebSocket
   */
  disconnect(): void {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
    }
    
    if (this.networkUnsubscribe) {
      this.networkUnsubscribe();
      this.networkUnsubscribe = null;
    }
    
    this.isConnected = false;
    this.subscriptions.clear();
    this.messageQueue = [];
    this.authToken = null;
    this.userId = null;
  }

  /**
   * Check connection status
   */
  isConnectedToServer(): boolean {
    return this.isConnected && this.socket?.connected === true;
  }

  /**
   * Get connection info
   */
  getConnectionInfo(): {
    connected: boolean;
    reconnectAttempts: number;
    subscriptions: number;
    queuedMessages: number;
  } {
    let totalSubscriptions = 0;
    this.subscriptions.forEach((ids) => {
      totalSubscriptions += ids.size;
    });

    return {
      connected: this.isConnected,
      reconnectAttempts: this.reconnectAttempts,
      subscriptions: totalSubscriptions,
      queuedMessages: this.messageQueue.length,
    };
  }
}

export default RealtimeService.getInstance();