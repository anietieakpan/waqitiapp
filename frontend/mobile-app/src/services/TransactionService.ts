/**
 * Enhanced Transaction Service with comprehensive receipt management
 * Handles transaction operations and proof of payment functionality
 */

import axios, { AxiosInstance, AxiosResponse } from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import RNFS from 'react-native-fs';
import Share from 'react-native-share';
import { Alert } from 'react-native';

interface Transaction {
  id: string;
  amount: number;
  currency: string;
  status: string;
  type: string;
  description?: string;
  reference?: string;
  fromWalletId?: string;
  toWalletId?: string;
  createdAt: string;
  updatedAt?: string;
  feeAmount?: number;
}

interface ReceiptGenerationOptions {
  format?: 'STANDARD' | 'DETAILED' | 'MINIMAL' | 'PROOF_OF_PAYMENT' | 'TAX_DOCUMENT';
  includeDetailedFees?: boolean;
  includeTimeline?: boolean;
  includeQrCode?: boolean;
  includeWatermark?: boolean;
  includeComplianceInfo?: boolean;
  locale?: string;
}

interface ReceiptMetadata {
  receiptId: string;
  transactionId: string;
  generatedAt: string;
  fileSize: number;
  securityHash: string;
  format: string;
  version: string;
  expiresAt?: string;
}

interface ShareOptions {
  email?: string;
  saveToDevice?: boolean;
  shareVia?: 'email' | 'whatsapp' | 'social' | 'all';
}

class TransactionService {
  private api: AxiosInstance;
  private baseUrl: string;

  constructor() {
    this.baseUrl = __DEV__ 
      ? 'http://localhost:8080/api/v1'
      : 'https://api.example.com/api/v1';
    
    this.api = axios.create({
      baseURL: this.baseUrl,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors() {
    // Request interceptor for auth
    this.api.interceptors.request.use(
      async (config) => {
        const token = await AsyncStorage.getItem('auth_token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    // Response interceptor for error handling
    this.api.interceptors.response.use(
      (response) => response,
      async (error) => {
        if (error.response?.status === 401) {
          // Handle token expiration
          await AsyncStorage.removeItem('auth_token');
          // Navigate to login screen
        }
        return Promise.reject(error);
      }
    );
  }

  /**
   * Get transaction details
   */
  async getTransaction(transactionId: string): Promise<Transaction> {
    try {
      const response: AxiosResponse<{ data: Transaction }> = await this.api.get(
        `/transactions/${transactionId}`
      );
      return response.data.data;
    } catch (error) {
      console.error('Error fetching transaction:', error);
      throw new Error('Failed to fetch transaction details');
    }
  }

  /**
   * Download receipt PDF
   */
  async downloadReceipt(
    transactionId: string, 
    options?: ReceiptGenerationOptions
  ): Promise<Blob> {
    try {
      const response = await this.api.get(`/transactions/${transactionId}/receipt`, {
        responseType: 'blob',
        params: options,
        headers: {
          'Accept': 'application/pdf'
        }
      });
      
      return response.data;
    } catch (error) {
      console.error('Error downloading receipt:', error);
      throw new Error('Failed to download receipt');
    }
  }

  /**
   * Generate and store receipt with metadata
   */
  async generateReceipt(
    transactionId: string,
    options?: ReceiptGenerationOptions
  ): Promise<ReceiptMetadata> {
    try {
      const response: AxiosResponse<{ data: ReceiptMetadata }> = await this.api.post(
        `/transactions/${transactionId}/receipt/generate`,
        options || {}
      );
      
      return response.data.data;
    } catch (error) {
      console.error('Error generating receipt:', error);
      throw new Error('Failed to generate receipt');
    }
  }

  /**
   * Share receipt with enhanced options
   */
  async shareReceipt(
    transactionId: string, 
    shareOptions?: ShareOptions,
    receiptOptions?: ReceiptGenerationOptions
  ): Promise<void> {
    try {
      // Generate receipt
      const receiptBlob = await this.downloadReceipt(transactionId, receiptOptions);
      
      // Convert blob to file
      const fileName = `receipt-${transactionId}-${Date.now()}.pdf`;
      const filePath = `${RNFS.DocumentDirectoryPath}/${fileName}`;
      
      // Save to device temporarily
      const reader = new FileReader();
      reader.onload = async () => {
        const base64Data = (reader.result as string).split(',')[1];
        await RNFS.writeFile(filePath, base64Data, 'base64');
        
        // Save to device if requested
        if (shareOptions?.saveToDevice) {
          await this.saveToDeviceStorage(filePath, fileName);
        }

        // Share receipt
        const shareData = {
          title: `Transaction Receipt - ${transactionId}`,
          message: `Your transaction receipt for ${transactionId}`,
          url: `file://${filePath}`,
          type: 'application/pdf',
        };

        if (shareOptions?.email) {
          await this.emailReceipt(transactionId, shareOptions.email);
        } else {
          await Share.open(shareData);
        }

        // Clean up temporary file after sharing
        setTimeout(() => {
          RNFS.unlink(filePath).catch(console.error);
        }, 5000);
      };
      
      reader.readAsDataURL(receiptBlob as any);
      
    } catch (error) {
      console.error('Error sharing receipt:', error);
      Alert.alert('Error', 'Failed to share receipt. Please try again.');
      throw error;
    }
  }

  /**
   * Email receipt to specified address
   */
  async emailReceipt(transactionId: string, email: string): Promise<boolean> {
    try {
      const response: AxiosResponse<{ success: boolean }> = await this.api.post(
        `/transactions/${transactionId}/receipt/email`,
        { email }
      );
      
      return response.data.success;
    } catch (error) {
      console.error('Error emailing receipt:', error);
      throw new Error('Failed to email receipt');
    }
  }

  /**
   * Verify receipt integrity
   */
  async verifyReceipt(
    receiptData: Blob, 
    transactionId: string, 
    expectedHash?: string
  ): Promise<boolean> {
    try {
      const formData = new FormData();
      formData.append('receiptFile', receiptData as any, 'receipt.pdf');
      formData.append('transactionId', transactionId);
      if (expectedHash) {
        formData.append('expectedHash', expectedHash);
      }

      const response: AxiosResponse<{ valid: boolean }> = await this.api.post(
        '/receipts/verify',
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );
      
      return response.data.valid;
    } catch (error) {
      console.error('Error verifying receipt:', error);
      return false;
    }
  }

  /**
   * Get receipt access token for secure sharing
   */
  async getReceiptAccessToken(
    transactionId: string,
    email: string,
    validityHours: number = 24
  ): Promise<string> {
    try {
      const response: AxiosResponse<{ token: string }> = await this.api.post(
        `/transactions/${transactionId}/receipt/access-token`,
        {
          email,
          validityHours
        }
      );
      
      return response.data.token;
    } catch (error) {
      console.error('Error getting access token:', error);
      throw new Error('Failed to generate access token');
    }
  }

  /**
   * Generate proof of payment document
   */
  async generateProofOfPayment(transactionId: string): Promise<Blob> {
    const options: ReceiptGenerationOptions = {
      format: 'PROOF_OF_PAYMENT',
      includeDetailedFees: true,
      includeTimeline: true,
      includeComplianceInfo: true,
      includeWatermark: true,
      includeQrCode: true
    };
    
    return this.downloadReceipt(transactionId, options);
  }

  /**
   * Generate tax document
   */
  async generateTaxDocument(transactionId: string): Promise<Blob> {
    const options: ReceiptGenerationOptions = {
      format: 'TAX_DOCUMENT',
      includeDetailedFees: true,
      includeComplianceInfo: true,
      includeWatermark: true
    };
    
    return this.downloadReceipt(transactionId, options);
  }

  /**
   * Get receipt history for a transaction
   */
  async getReceiptHistory(transactionId: string): Promise<ReceiptMetadata[]> {
    try {
      const response: AxiosResponse<{ data: ReceiptMetadata[] }> = await this.api.get(
        `/transactions/${transactionId}/receipts`
      );
      
      return response.data.data;
    } catch (error) {
      console.error('Error fetching receipt history:', error);
      throw new Error('Failed to fetch receipt history');
    }
  }

  /**
   * Save receipt to device storage
   */
  private async saveToDeviceStorage(filePath: string, fileName: string): Promise<void> {
    try {
      const downloadsPath = Platform.OS === 'android' 
        ? RNFS.DownloadDirectoryPath 
        : RNFS.DocumentDirectoryPath;
      
      const destinationPath = `${downloadsPath}/${fileName}`;
      await RNFS.copyFile(filePath, destinationPath);
      
      Alert.alert(
        'Success', 
        `Receipt saved to ${Platform.OS === 'android' ? 'Downloads' : 'Files'} folder`
      );
    } catch (error) {
      console.error('Error saving to device:', error);
      Alert.alert('Error', 'Failed to save receipt to device');
    }
  }

  /**
   * Check if receipt exists for transaction
   */
  async hasReceipt(transactionId: string): Promise<boolean> {
    try {
      const response = await this.api.head(`/transactions/${transactionId}/receipt`);
      return response.status === 200;
    } catch (error) {
      return false;
    }
  }

  /**
   * Get multiple receipts in a zip file
   */
  async downloadMultipleReceipts(
    transactionIds: string[],
    options?: ReceiptGenerationOptions
  ): Promise<Blob> {
    try {
      const response = await this.api.post('/receipts/bulk-download', {
        transactionIds,
        options
      }, {
        responseType: 'blob',
        headers: {
          'Accept': 'application/zip'
        }
      });
      
      return response.data;
    } catch (error) {
      console.error('Error downloading multiple receipts:', error);
      throw new Error('Failed to download receipts');
    }
  }

  /**
   * Get receipt analytics
   */
  async getReceiptAnalytics(timeframe: 'week' | 'month' | 'year' = 'month') {
    try {
      const response = await this.api.get('/receipts/analytics', {
        params: { timeframe }
      });
      
      return response.data.data;
    } catch (error) {
      console.error('Error fetching receipt analytics:', error);
      throw new Error('Failed to fetch receipt analytics');
    }
  }
}

export default new TransactionService();
export type { Transaction, ReceiptGenerationOptions, ReceiptMetadata, ShareOptions };