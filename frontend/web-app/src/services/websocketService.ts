import { io, Socket } from 'socket.io-client';

/**
 * WebSocket Service for Real-Time Monitoring
 *
 * FEATURES:
 * - Real-time transaction updates
 * - Live fraud alerts
 * - System health monitoring
 * - Automatic reconnection
 * - Message queuing during offline
 * - Event subscription management
 *
 * RELIABILITY:
 * - Exponential backoff reconnection
 * - Heartbeat/ping-pong
 * - Connection state management
 * - Error recovery
 * - Message deduplication
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */

export enum WebSocketEvent {
  // Connection events
  CONNECT = 'connect',
  DISCONNECT = 'disconnect',
  ERROR = 'error',
  RECONNECT = 'reconnect',

  // Transaction events
  TRANSACTION_CREATED = 'transaction:created',
  TRANSACTION_COMPLETED = 'transaction:completed',
  TRANSACTION_FAILED = 'transaction:failed',
  PAYMENT_AUTHORIZED = 'payment:authorized',
  PAYMENT_SETTLED = 'payment:settled',

  // Fraud events
  FRAUD_ALERT = 'fraud:alert',
  FRAUD_SCORE_HIGH = 'fraud:score:high',
  SUSPICIOUS_ACTIVITY = 'fraud:suspicious',

  // Balance events
  BALANCE_UPDATED = 'balance:updated',
  WALLET_CREDITED = 'wallet:credited',
  WALLET_DEBITED = 'wallet:debited',

  // System events
  SYSTEM_HEALTH = 'system:health',
  SERVICE_DOWN = 'system:service:down',
  SERVICE_UP = 'system:service:up',

  // User events
  USER_NOTIFICATION = 'user:notification',
  USER_MESSAGE = 'user:message',
}

export interface WebSocketConfig {
  url: string;
  autoConnect: boolean;
  reconnection: boolean;
  reconnectionAttempts: number;
  reconnectionDelay: number;
  reconnectionDelayMax: number;
  timeout: number;
}

export interface TransactionEvent {
  id: string;
  type: 'PAYMENT' | 'TRANSFER' | 'WITHDRAWAL' | 'DEPOSIT';
  amount: number;
  currency: string;
  status: string;
  userId: string;
  timestamp: Date;
  metadata?: Record<string, any>;
}

export interface FraudAlertEvent {
  id: string;
  type: 'HIGH_RISK' | 'SUSPICIOUS_PATTERN' | 'VELOCITY_EXCEEDED' | 'BLACKLIST_HIT';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  transactionId?: string;
  userId: string;
  riskScore: number;
  reason: string;
  timestamp: Date;
  actionRequired: boolean;
}

export interface BalanceUpdateEvent {
  walletId: string;
  userId: string;
  previousBalance: number;
  newBalance: number;
  change: number;
  currency: string;
  reason: string;
  timestamp: Date;
}

export interface SystemHealthEvent {
  service: string;
  status: 'UP' | 'DOWN' | 'DEGRADED';
  responseTime?: number;
  errorRate?: number;
  timestamp: Date;
  details?: Record<string, any>;
}

type EventCallback<T = any> = (data: T) => void;

class WebSocketService {
  private socket: Socket | null = null;
  private config: WebSocketConfig;
  private eventHandlers: Map<string, Set<EventCallback>> = new Map();
  private connectionState: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' = 'DISCONNECTED';
  private messageQueue: Array<{ event: string; data: any }> = [];
  private reconnectAttempts = 0;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private lastMessageId: string | null = null;

  constructor() {
    this.config = {
      url: import.meta.env.VITE_WS_URL || 'ws://localhost:8080',
      autoConnect: false,
      reconnection: true,
      reconnectionAttempts: 10,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 30000,
      timeout: 20000,
    };
  }

  /**
   * Initialize and connect to WebSocket server
   */
  public connect(token?: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.socket?.connected) {
        console.log('[WebSocket] Already connected');
        resolve();
        return;
      }

      console.log('[WebSocket] Connecting to:', this.config.url);
      this.connectionState = 'CONNECTING';

      // ✅ SECURITY FIX: Socket.IO authentication via cookies
      // Configure Socket.IO to send credentials (HttpOnly cookies) with handshake
      this.socket = io(this.config.url, {
        auth: token ? { token } : {},  // Only use token if explicitly provided
        // ❌ REMOVED: localStorage.getItem('authToken') - VULNERABLE TO XSS
        withCredentials: true,  // Send HttpOnly cookies with Socket.IO handshake
        reconnection: this.config.reconnection,
        reconnectionAttempts: this.config.reconnectionAttempts,
        reconnectionDelay: this.config.reconnectionDelay,
        reconnectionDelayMax: this.config.reconnectionDelayMax,
        timeout: this.config.timeout,
        transports: ['websocket', 'polling'],
        extraHeaders: {
          // Additional headers if needed (e.g., CSRF token)
        },
      });

      this.setupEventHandlers();

      this.socket.on('connect', () => {
        console.log('[WebSocket] Connected successfully');
        this.connectionState = 'CONNECTED';
        this.reconnectAttempts = 0;
        this.startHeartbeat();
        this.flushMessageQueue();
        resolve();
      });

      this.socket.on('connect_error', (error) => {
        console.error('[WebSocket] Connection error:', error);
        this.connectionState = 'DISCONNECTED';
        reject(error);
      });
    });
  }

  /**
   * Disconnect from WebSocket server
   */
  public disconnect(): void {
    if (this.socket) {
      console.log('[WebSocket] Disconnecting...');
      this.stopHeartbeat();
      this.socket.disconnect();
      this.socket = null;
      this.connectionState = 'DISCONNECTED';
      this.eventHandlers.clear();
    }
  }

  /**
   * Check if connected
   */
  public isConnected(): boolean {
    return this.socket?.connected || false;
  }

  /**
   * Get connection state
   */
  public getConnectionState(): string {
    return this.connectionState;
  }

  /**
   * Subscribe to an event
   */
  public on<T = any>(event: WebSocketEvent | string, callback: EventCallback<T>): () => void {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }

    this.eventHandlers.get(event)!.add(callback);

    // Return unsubscribe function
    return () => {
      this.off(event, callback);
    };
  }

  /**
   * Unsubscribe from an event
   */
  public off(event: WebSocketEvent | string, callback: EventCallback): void {
    const handlers = this.eventHandlers.get(event);
    if (handlers) {
      handlers.delete(callback);
      if (handlers.size === 0) {
        this.eventHandlers.delete(event);
      }
    }
  }

  /**
   * Subscribe once to an event
   */
  public once<T = any>(event: WebSocketEvent | string, callback: EventCallback<T>): void {
    const wrappedCallback = (data: T) => {
      callback(data);
      this.off(event, wrappedCallback);
    };
    this.on(event, wrappedCallback);
  }

  /**
   * Emit an event to the server
   */
  public emit(event: string, data: any): void {
    if (this.isConnected() && this.socket) {
      this.socket.emit(event, data);
    } else {
      // Queue message for later delivery
      console.warn('[WebSocket] Not connected. Queuing message:', event);
      this.messageQueue.push({ event, data });
    }
  }

  /**
   * Subscribe to transaction events
   */
  public subscribeToTransactions(userId: string, callback: EventCallback<TransactionEvent>): () => void {
    this.emit('subscribe:transactions', { userId });
    return this.on(WebSocketEvent.TRANSACTION_CREATED, callback);
  }

  /**
   * Subscribe to fraud alerts
   */
  public subscribeToFraudAlerts(userId: string, callback: EventCallback<FraudAlertEvent>): () => void {
    this.emit('subscribe:fraud', { userId });
    return this.on(WebSocketEvent.FRAUD_ALERT, callback);
  }

  /**
   * Subscribe to balance updates
   */
  public subscribeToBalanceUpdates(userId: string, callback: EventCallback<BalanceUpdateEvent>): () => void {
    this.emit('subscribe:balance', { userId });
    return this.on(WebSocketEvent.BALANCE_UPDATED, callback);
  }

  /**
   * Subscribe to system health
   */
  public subscribeToSystemHealth(callback: EventCallback<SystemHealthEvent>): () => void {
    this.emit('subscribe:system', {});
    return this.on(WebSocketEvent.SYSTEM_HEALTH, callback);
  }

  /**
   * Setup internal event handlers
   */
  private setupEventHandlers(): void {
    if (!this.socket) return;

    // Connection events
    this.socket.on('disconnect', (reason) => {
      console.log('[WebSocket] Disconnected:', reason);
      this.connectionState = 'DISCONNECTED';
      this.stopHeartbeat();
      this.notifyHandlers(WebSocketEvent.DISCONNECT, { reason });
    });

    this.socket.on('reconnect', (attemptNumber) => {
      console.log('[WebSocket] Reconnected after', attemptNumber, 'attempts');
      this.connectionState = 'CONNECTED';
      this.reconnectAttempts = 0;
      this.startHeartbeat();
      this.flushMessageQueue();
      this.notifyHandlers(WebSocketEvent.RECONNECT, { attemptNumber });
    });

    this.socket.on('reconnect_attempt', (attemptNumber) => {
      console.log('[WebSocket] Reconnection attempt:', attemptNumber);
      this.connectionState = 'RECONNECTING';
      this.reconnectAttempts = attemptNumber;
    });

    this.socket.on('error', (error) => {
      console.error('[WebSocket] Error:', error);
      this.notifyHandlers(WebSocketEvent.ERROR, error);
    });

    // Transaction events
    this.socket.on(WebSocketEvent.TRANSACTION_CREATED, (data) => {
      this.handleIncomingMessage(WebSocketEvent.TRANSACTION_CREATED, data);
    });

    this.socket.on(WebSocketEvent.TRANSACTION_COMPLETED, (data) => {
      this.handleIncomingMessage(WebSocketEvent.TRANSACTION_COMPLETED, data);
    });

    this.socket.on(WebSocketEvent.TRANSACTION_FAILED, (data) => {
      this.handleIncomingMessage(WebSocketEvent.TRANSACTION_FAILED, data);
    });

    // Fraud events
    this.socket.on(WebSocketEvent.FRAUD_ALERT, (data) => {
      this.handleIncomingMessage(WebSocketEvent.FRAUD_ALERT, data);
    });

    // Balance events
    this.socket.on(WebSocketEvent.BALANCE_UPDATED, (data) => {
      this.handleIncomingMessage(WebSocketEvent.BALANCE_UPDATED, data);
    });

    this.socket.on(WebSocketEvent.WALLET_CREDITED, (data) => {
      this.handleIncomingMessage(WebSocketEvent.WALLET_CREDITED, data);
    });

    this.socket.on(WebSocketEvent.WALLET_DEBITED, (data) => {
      this.handleIncomingMessage(WebSocketEvent.WALLET_DEBITED, data);
    });

    // System events
    this.socket.on(WebSocketEvent.SYSTEM_HEALTH, (data) => {
      this.handleIncomingMessage(WebSocketEvent.SYSTEM_HEALTH, data);
    });

    this.socket.on(WebSocketEvent.SERVICE_DOWN, (data) => {
      this.handleIncomingMessage(WebSocketEvent.SERVICE_DOWN, data);
    });

    this.socket.on(WebSocketEvent.SERVICE_UP, (data) => {
      this.handleIncomingMessage(WebSocketEvent.SERVICE_UP, data);
    });

    // User events
    this.socket.on(WebSocketEvent.USER_NOTIFICATION, (data) => {
      this.handleIncomingMessage(WebSocketEvent.USER_NOTIFICATION, data);
    });
  }

  /**
   * Handle incoming message with deduplication
   */
  private handleIncomingMessage(event: string, data: any): void {
    // Deduplicate messages
    if (data.messageId && data.messageId === this.lastMessageId) {
      console.log('[WebSocket] Duplicate message ignored:', data.messageId);
      return;
    }

    this.lastMessageId = data.messageId;
    this.notifyHandlers(event, data);
  }

  /**
   * Notify all handlers for an event
   */
  private notifyHandlers(event: string, data: any): void {
    const handlers = this.eventHandlers.get(event);
    if (handlers) {
      handlers.forEach((callback) => {
        try {
          callback(data);
        } catch (error) {
          console.error('[WebSocket] Handler error for event', event, error);
        }
      });
    }
  }

  /**
   * Start heartbeat to keep connection alive
   */
  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatInterval = setInterval(() => {
      if (this.socket?.connected) {
        this.socket.emit('ping', { timestamp: Date.now() });
      }
    }, 30000); // Ping every 30 seconds
  }

  /**
   * Stop heartbeat
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  /**
   * Flush queued messages
   */
  private flushMessageQueue(): void {
    if (this.messageQueue.length > 0) {
      console.log('[WebSocket] Flushing', this.messageQueue.length, 'queued messages');
      this.messageQueue.forEach(({ event, data }) => {
        this.emit(event, data);
      });
      this.messageQueue = [];
    }
  }

  /**
   * Get queue size
   */
  public getQueueSize(): number {
    return this.messageQueue.length;
  }

  /**
   * Clear message queue
   */
  public clearQueue(): void {
    this.messageQueue = [];
  }

  /**
   * Get reconnection attempts
   */
  public getReconnectAttempts(): number {
    return this.reconnectAttempts;
  }
}

// Singleton instance
export const websocketService = new WebSocketService();
export default websocketService;
