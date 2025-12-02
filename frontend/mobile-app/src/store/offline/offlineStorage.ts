/**
 * Offline Storage Manager
 * Handles persistent storage for offline data using SQLite and AsyncStorage
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import SQLite from 'react-native-sqlite-storage';
import { Platform } from 'react-native';
import CryptoJS from 'crypto-js';

// Storage keys
const STORAGE_KEYS = {
  USER_DATA: '@waqiti_user_data',
  TRANSACTIONS: '@waqiti_transactions',
  PENDING_PAYMENTS: '@waqiti_pending_payments',
  CACHED_BALANCES: '@waqiti_cached_balances',
  SYNC_METADATA: '@waqiti_sync_metadata',
  ENCRYPTION_KEY: '@waqiti_storage_key',
};

// SQLite database configuration
const DB_NAME = 'waqiti_offline.db';
const DB_VERSION = 1;

export class OfflineStorageManager {
  private db: SQLite.SQLiteDatabase | null = null;
  private encryptionKey: string | null = null;

  /**
   * Initialize the storage manager
   */
  async initialize(): Promise<void> {
    try {
      // Initialize encryption key
      await this.initializeEncryption();
      
      // Open SQLite database
      this.db = await SQLite.openDatabase({
        name: DB_NAME,
        location: 'default',
        createFromLocation: '~www/waqiti_offline.db'
      });

      // Create tables if they don't exist
      await this.createTables();
      
      // Run migrations if needed
      await this.runMigrations();
      
    } catch (error) {
      console.error('Failed to initialize offline storage:', error);
      throw error;
    }
  }

  /**
   * Initialize encryption for sensitive data
   */
  private async initializeEncryption(): Promise<void> {
    let key = await AsyncStorage.getItem(STORAGE_KEYS.ENCRYPTION_KEY);
    
    if (!key) {
      // Generate new encryption key
      key = CryptoJS.lib.WordArray.random(256/8).toString();
      await AsyncStorage.setItem(STORAGE_KEYS.ENCRYPTION_KEY, key);
    }
    
    this.encryptionKey = key;
  }

  /**
   * Encrypt sensitive data
   */
  private encrypt(data: string): string {
    if (!this.encryptionKey) {
      throw new Error('Encryption key not initialized');
    }
    return CryptoJS.AES.encrypt(data, this.encryptionKey).toString();
  }

  /**
   * Decrypt sensitive data
   */
  private decrypt(encryptedData: string): string {
    if (!this.encryptionKey) {
      throw new Error('Encryption key not initialized');
    }
    const bytes = CryptoJS.AES.decrypt(encryptedData, this.encryptionKey);
    return bytes.toString(CryptoJS.enc.Utf8);
  }

  /**
   * Create database tables
   */
  private async createTables(): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const queries = [
      // User data table
      `CREATE TABLE IF NOT EXISTS user_data (
        id TEXT PRIMARY KEY,
        data TEXT NOT NULL,
        updated_at INTEGER NOT NULL,
        sync_status TEXT DEFAULT 'pending'
      )`,

      // Transactions table
      `CREATE TABLE IF NOT EXISTS transactions (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        type TEXT NOT NULL,
        amount REAL NOT NULL,
        currency TEXT NOT NULL,
        status TEXT NOT NULL,
        data TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        sync_status TEXT DEFAULT 'pending',
        sync_attempts INTEGER DEFAULT 0
      )`,

      // Pending payments table
      `CREATE TABLE IF NOT EXISTS pending_payments (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        recipient_id TEXT NOT NULL,
        amount REAL NOT NULL,
        currency TEXT NOT NULL,
        note TEXT,
        data TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        sync_status TEXT DEFAULT 'pending',
        sync_attempts INTEGER DEFAULT 0
      )`,

      // Cached data table
      `CREATE TABLE IF NOT EXISTS cached_data (
        key TEXT PRIMARY KEY,
        data TEXT NOT NULL,
        expires_at INTEGER,
        created_at INTEGER NOT NULL
      )`,

      // Sync metadata table
      `CREATE TABLE IF NOT EXISTS sync_metadata (
        entity_type TEXT PRIMARY KEY,
        last_sync_time INTEGER,
        sync_version TEXT,
        sync_data TEXT
      )`
    ];

    for (const query of queries) {
      await this.db.executeSql(query);
    }
  }

  /**
   * Run database migrations
   */
  private async runMigrations(): Promise<void> {
    // Check current version
    const version = await AsyncStorage.getItem('@waqiti_db_version');
    const currentVersion = version ? parseInt(version, 10) : 0;

    if (currentVersion < DB_VERSION) {
      // Run migrations based on version
      if (currentVersion < 1) {
        // Initial migration is handled by createTables
      }

      // Update version
      await AsyncStorage.setItem('@waqiti_db_version', DB_VERSION.toString());
    }
  }

  /**
   * Save user data for offline access
   */
  async saveUserData(userId: string, userData: any): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const encryptedData = this.encrypt(JSON.stringify(userData));
    const now = Date.now();

    await this.db.executeSql(
      `INSERT OR REPLACE INTO user_data (id, data, updated_at, sync_status) 
       VALUES (?, ?, ?, ?)`,
      [userId, encryptedData, now, 'synced']
    );
  }

  /**
   * Get user data
   */
  async getUserData(userId: string): Promise<any | null> {
    if (!this.db) throw new Error('Database not initialized');

    const results = await this.db.executeSql(
      'SELECT data FROM user_data WHERE id = ?',
      [userId]
    );

    if (results[0].rows.length > 0) {
      const encryptedData = results[0].rows.item(0).data;
      const decryptedData = this.decrypt(encryptedData);
      return JSON.parse(decryptedData);
    }

    return null;
  }

  /**
   * Save transaction for offline access
   */
  async saveTransaction(transaction: any): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const encryptedData = this.encrypt(JSON.stringify(transaction));
    const now = Date.now();

    await this.db.executeSql(
      `INSERT OR REPLACE INTO transactions 
       (id, user_id, type, amount, currency, status, data, created_at, updated_at, sync_status) 
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        transaction.id,
        transaction.userId,
        transaction.type,
        transaction.amount,
        transaction.currency,
        transaction.status,
        encryptedData,
        transaction.createdAt || now,
        now,
        transaction.syncStatus || 'pending'
      ]
    );
  }

  /**
   * Get transactions for a user
   */
  async getTransactions(userId: string, limit: number = 50): Promise<any[]> {
    if (!this.db) throw new Error('Database not initialized');

    const results = await this.db.executeSql(
      `SELECT * FROM transactions 
       WHERE user_id = ? 
       ORDER BY created_at DESC 
       LIMIT ?`,
      [userId, limit]
    );

    const transactions = [];
    for (let i = 0; i < results[0].rows.length; i++) {
      const row = results[0].rows.item(i);
      const decryptedData = this.decrypt(row.data);
      transactions.push({
        ...JSON.parse(decryptedData),
        syncStatus: row.sync_status,
        syncAttempts: row.sync_attempts
      });
    }

    return transactions;
  }

  /**
   * Save pending payment
   */
  async savePendingPayment(payment: any): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const encryptedData = this.encrypt(JSON.stringify(payment));
    const now = Date.now();

    await this.db.executeSql(
      `INSERT INTO pending_payments 
       (id, user_id, recipient_id, amount, currency, note, data, created_at, sync_status) 
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        payment.id,
        payment.userId,
        payment.recipientId,
        payment.amount,
        payment.currency,
        payment.note,
        encryptedData,
        now,
        'pending'
      ]
    );
  }

  /**
   * Get pending payments
   */
  async getPendingPayments(userId: string): Promise<any[]> {
    if (!this.db) throw new Error('Database not initialized');

    const results = await this.db.executeSql(
      'SELECT * FROM pending_payments WHERE user_id = ? AND sync_status = ?',
      [userId, 'pending']
    );

    const payments = [];
    for (let i = 0; i < results[0].rows.length; i++) {
      const row = results[0].rows.item(i);
      const decryptedData = this.decrypt(row.data);
      payments.push({
        ...JSON.parse(decryptedData),
        id: row.id,
        syncAttempts: row.sync_attempts
      });
    }

    return payments;
  }

  /**
   * Cache data with optional expiration
   */
  async cacheData(key: string, data: any, ttlSeconds?: number): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const now = Date.now();
    const expiresAt = ttlSeconds ? now + (ttlSeconds * 1000) : null;

    await this.db.executeSql(
      `INSERT OR REPLACE INTO cached_data (key, data, expires_at, created_at) 
       VALUES (?, ?, ?, ?)`,
      [key, JSON.stringify(data), expiresAt, now]
    );
  }

  /**
   * Get cached data
   */
  async getCachedData(key: string): Promise<any | null> {
    if (!this.db) throw new Error('Database not initialized');

    const now = Date.now();
    const results = await this.db.executeSql(
      'SELECT data FROM cached_data WHERE key = ? AND (expires_at IS NULL OR expires_at > ?)',
      [key, now]
    );

    if (results[0].rows.length > 0) {
      return JSON.parse(results[0].rows.item(0).data);
    }

    return null;
  }

  /**
   * Clear expired cache entries
   */
  async clearExpiredCache(): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const now = Date.now();
    await this.db.executeSql(
      'DELETE FROM cached_data WHERE expires_at IS NOT NULL AND expires_at < ?',
      [now]
    );
  }

  /**
   * Mark items for sync
   */
  async markForSync(tableName: string, ids: string[]): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const placeholders = ids.map(() => '?').join(',');
    await this.db.executeSql(
      `UPDATE ${tableName} SET sync_status = 'pending' WHERE id IN (${placeholders})`,
      ids
    );
  }

  /**
   * Get items pending sync
   */
  async getPendingSync(tableName: string): Promise<any[]> {
    if (!this.db) throw new Error('Database not initialized');

    const results = await this.db.executeSql(
      `SELECT * FROM ${tableName} WHERE sync_status = 'pending' ORDER BY created_at`
    );

    const items = [];
    for (let i = 0; i < results[0].rows.length; i++) {
      items.push(results[0].rows.item(i));
    }

    return items;
  }

  /**
   * Update sync status
   */
  async updateSyncStatus(
    tableName: string, 
    id: string, 
    status: 'synced' | 'failed', 
    incrementAttempts: boolean = false
  ): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    let query = `UPDATE ${tableName} SET sync_status = ?`;
    const params: any[] = [status];

    if (incrementAttempts) {
      query += ', sync_attempts = sync_attempts + 1';
    }

    query += ' WHERE id = ?';
    params.push(id);

    await this.db.executeSql(query, params);
  }

  /**
   * Clear all offline data
   */
  async clearAllData(): Promise<void> {
    if (!this.db) throw new Error('Database not initialized');

    const tables = ['user_data', 'transactions', 'pending_payments', 'cached_data'];
    
    for (const table of tables) {
      await this.db.executeSql(`DELETE FROM ${table}`);
    }

    // Clear AsyncStorage
    const keys = Object.values(STORAGE_KEYS);
    await AsyncStorage.multiRemove(keys);
  }

  /**
   * Close database connection
   */
  async close(): Promise<void> {
    if (this.db) {
      await this.db.close();
      this.db = null;
    }
  }
}

// Export singleton instance
export const offlineStorage = new OfflineStorageManager();