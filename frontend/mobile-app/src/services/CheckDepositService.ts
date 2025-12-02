/**
 * Check Deposit Service - Complete integration with OCR and backend processing
 * Handles check image processing, validation, and deposit creation
 */

import ApiService from './ApiService';
import OCRService, { CheckData, OCRResult } from './OCRService';

export interface CheckDepositRequest {
  frontImageData: string; // base64 or File
  backImageData: string; // base64 or File
  depositAmount: number;
  description?: string;
  walletId?: string;
  endorsement?: string;
}

export interface CheckDepositResponse {
  depositId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed' | 'rejected';
  amount: number;
  estimatedAvailability: string;
  transactionId?: string;
  checkDetails: ProcessedCheckDetails;
  validationResult: CheckValidationResult;
  createdAt: string;
}

export interface ProcessedCheckDetails {
  checkNumber?: string;
  routingNumber?: string;
  accountNumber?: string;
  payeeName?: string;
  payorName?: string;
  checkDate?: string;
  memo?: string;
  bankName?: string;
  extractedAmount?: number;
  writtenAmount?: string;
  amountMismatch?: boolean;
  confidence: number;
}

export interface CheckValidationResult {
  isValid: boolean;
  warnings: string[];
  errors: string[];
  riskScore: number; // 0-1, higher means more risky
  validationChecks: {
    imageQuality: boolean;
    micrValidation: boolean;
    amountConsistency: boolean;
    dateValidation: boolean;
    endorsementCheck: boolean;
    duplicateCheck: boolean;
    fraudCheck: boolean;
  };
}

export interface CheckDepositStatus {
  depositId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed' | 'rejected';
  substatus?: string;
  amount: number;
  processedAmount?: number;
  availableAmount?: number;
  holdAmount?: number;
  estimatedAvailability: string;
  actualAvailability?: string;
  rejectionReason?: string;
  checkImages: {
    frontUrl: string;
    backUrl: string;
    thumbnailUrl?: string;
  };
  timeline: CheckDepositEvent[];
  fees?: {
    processingFee: number;
    expediteFee?: number;
    totalFees: number;
  };
}

export interface CheckDepositEvent {
  timestamp: string;
  event: string;
  description: string;
  details?: Record<string, any>;
}

export interface CheckDepositHistory {
  deposits: CheckDepositStatus[];
  totalCount: number;
  totalAmount: number;
  pendingCount: number;
  completedCount: number;
  rejectedCount: number;
}

class CheckDepositService {
  private static instance: CheckDepositService;
  private apiService = ApiService.getInstance();

  static getInstance(): CheckDepositService {
    if (!CheckDepositService.instance) {
      CheckDepositService.instance = new CheckDepositService();
    }
    return CheckDepositService.instance;
  }

  /**
   * Process and create check deposit
   */
  async createCheckDeposit(request: CheckDepositRequest): Promise<CheckDepositResponse> {
    try {
      // Step 1: Process front image with OCR
      const frontOCRResult = await this.processCheckImage(request.frontImageData, 'front');
      
      // Step 2: Process back image for endorsement validation
      const backOCRResult = await this.processCheckImage(request.backImageData, 'back');
      
      // Step 3: Validate the extracted data
      const validationResult = await this.validateCheckData(
        frontOCRResult.extractedData as CheckData,
        request.depositAmount
      );

      // Step 4: Create deposit request
      const formData = new FormData();
      
      // Add images
      if (typeof request.frontImageData === 'string') {
        const frontBlob = this.base64ToBlob(request.frontImageData);
        formData.append('frontImage', frontBlob, 'check-front.jpg');
      } else {
        formData.append('frontImage', request.frontImageData as File);
      }
      
      if (typeof request.backImageData === 'string') {
        const backBlob = this.base64ToBlob(request.backImageData);
        formData.append('backImage', backBlob, 'check-back.jpg');
      } else {
        formData.append('backImage', request.backImageData as File);
      }

      // Add deposit details
      formData.append('depositAmount', request.depositAmount.toString());
      if (request.description) formData.append('description', request.description);
      if (request.walletId) formData.append('walletId', request.walletId);
      if (request.endorsement) formData.append('endorsement', request.endorsement);
      
      // Add extracted check data
      formData.append('ocrData', JSON.stringify({
        front: frontOCRResult.extractedData,
        back: backOCRResult.extractedData,
        frontConfidence: frontOCRResult.confidence,
        backConfidence: backOCRResult.confidence,
      }));

      // Submit deposit
      const response = await this.apiService.post<CheckDepositResponse>(
        '/payment/checks/deposit',
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      return response;
      
    } catch (error: any) {
      console.error('Check deposit creation failed:', error);
      throw new Error(error.message || 'Failed to create check deposit');
    }
  }

  /**
   * Process check image with OCR
   */
  private async processCheckImage(
    imageData: string | File,
    side: 'front' | 'back'
  ): Promise<OCRResult> {
    try {
      let file: File;
      
      if (typeof imageData === 'string') {
        const blob = this.base64ToBlob(imageData);
        file = new File([blob], `check-${side}.jpg`, { type: 'image/jpeg' });
      } else {
        file = imageData;
      }

      // Use OCR service to process the check
      const ocrResult = await OCRService.processCheckImage(file, {
        documentType: 'check',
        enhanceImage: true,
        extractStructuredData: true,
        validateData: true,
      });

      return ocrResult;
      
    } catch (error: any) {
      console.error(`Check ${side} image processing failed:`, error);
      throw new Error(`Failed to process check ${side} image: ${error.message}`);
    }
  }

  /**
   * Validate extracted check data
   */
  private async validateCheckData(
    checkData: CheckData,
    depositAmount: number
  ): Promise<CheckValidationResult> {
    try {
      const response = await this.apiService.post<CheckValidationResult>('/payment/checks/validate', {
        checkData,
        depositAmount,
      });

      return response;
    } catch (error: any) {
      console.error('Check validation failed:', error);
      // Return basic validation result
      return {
        isValid: false,
        warnings: [],
        errors: ['Check validation service unavailable'],
        riskScore: 0.5,
        validationChecks: {
          imageQuality: false,
          micrValidation: false,
          amountConsistency: false,
          dateValidation: false,
          endorsementCheck: false,
          duplicateCheck: false,
          fraudCheck: false,
        },
      };
    }
  }

  /**
   * Get check deposit status
   */
  async getCheckDepositStatus(depositId: string): Promise<CheckDepositStatus> {
    try {
      const response = await this.apiService.get<CheckDepositStatus>(
        `/payment/checks/deposits/${depositId}/status`
      );

      return response;
    } catch (error: any) {
      console.error('Failed to get check deposit status:', error);
      throw new Error(error.message || 'Failed to get deposit status');
    }
  }

  /**
   * Get check deposit history
   */
  async getCheckDepositHistory(
    limit: number = 20,
    offset: number = 0,
    status?: string
  ): Promise<CheckDepositHistory> {
    try {
      const params = {
        limit,
        offset,
        ...(status && { status }),
      };

      const response = await this.apiService.get<CheckDepositHistory>(
        '/payment/checks/deposits',
        params
      );

      return response;
    } catch (error: any) {
      console.error('Failed to get check deposit history:', error);
      throw new Error(error.message || 'Failed to get deposit history');
    }
  }

  /**
   * Cancel pending check deposit
   */
  async cancelCheckDeposit(depositId: string, reason?: string): Promise<void> {
    try {
      await this.apiService.post(`/payment/checks/deposits/${depositId}/cancel`, {
        reason,
      });
    } catch (error: any) {
      console.error('Failed to cancel check deposit:', error);
      throw new Error(error.message || 'Failed to cancel deposit');
    }
  }

  /**
   * Retry failed check deposit
   */
  async retryCheckDeposit(depositId: string): Promise<CheckDepositResponse> {
    try {
      const response = await this.apiService.post<CheckDepositResponse>(
        `/payment/checks/deposits/${depositId}/retry`
      );

      return response;
    } catch (error: any) {
      console.error('Failed to retry check deposit:', error);
      throw new Error(error.message || 'Failed to retry deposit');
    }
  }

  /**
   * Get deposit limits and requirements
   */
  async getDepositLimits(): Promise<{
    dailyLimit: number;
    monthlyLimit: number;
    perCheckLimit: number;
    remainingDaily: number;
    remainingMonthly: number;
    requiresEndorsement: boolean;
    acceptedCheckTypes: string[];
    restrictions: string[];
  }> {
    try {
      const response = await this.apiService.get('/payment/checks/limits');
      return response;
    } catch (error: any) {
      console.error('Failed to get deposit limits:', error);
      throw new Error(error.message || 'Failed to get deposit limits');
    }
  }

  /**
   * Get check deposit fees
   */
  async getDepositFees(amount: number): Promise<{
    processingFee: number;
    expediteFee?: number;
    totalFees: number;
    freeDepositsRemaining?: number;
    nextFreeDepositDate?: string;
  }> {
    try {
      const response = await this.apiService.get('/payment/checks/fees', {
        amount,
      });

      return response;
    } catch (error: any) {
      console.error('Failed to get deposit fees:', error);
      return {
        processingFee: 0,
        totalFees: 0,
      };
    }
  }

  /**
   * Download check image
   */
  async downloadCheckImage(
    depositId: string,
    side: 'front' | 'back',
    format: 'original' | 'enhanced' = 'original'
  ): Promise<Blob> {
    try {
      const response = await this.apiService.get(
        `/payment/checks/deposits/${depositId}/images/${side}`,
        { format },
        { responseType: 'blob' }
      );

      return response as Blob;
    } catch (error: any) {
      console.error('Failed to download check image:', error);
      throw new Error(error.message || 'Failed to download check image');
    }
  }

  /**
   * Report suspicious check
   */
  async reportSuspiciousCheck(
    depositId: string,
    reason: string,
    details?: Record<string, any>
  ): Promise<void> {
    try {
      await this.apiService.post(`/payment/checks/deposits/${depositId}/report`, {
        reason,
        details,
      });
    } catch (error: any) {
      console.error('Failed to report suspicious check:', error);
      throw new Error(error.message || 'Failed to report check');
    }
  }

  /**
   * Convert base64 to blob
   */
  private base64ToBlob(base64: string): Blob {
    // Remove data URL prefix if present
    const base64Data = base64.replace(/^data:image\/[a-z]+;base64,/, '');
    
    // Convert base64 to binary
    const binaryData = atob(base64Data);
    const bytes = new Uint8Array(binaryData.length);
    
    for (let i = 0; i < binaryData.length; i++) {
      bytes[i] = binaryData.charCodeAt(i);
    }
    
    return new Blob([bytes], { type: 'image/jpeg' });
  }

  /**
   * Validate check image quality
   */
  async validateCheckImage(imageData: string | File): Promise<{
    isValid: boolean;
    issues: string[];
    suggestions: string[];
    qualityScore: number;
  }> {
    try {
      let file: File;
      
      if (typeof imageData === 'string') {
        const blob = this.base64ToBlob(imageData);
        file = new File([blob], 'check.jpg', { type: 'image/jpeg' });
      } else {
        file = imageData;
      }

      const qualityCheck = await OCRService.checkImageQuality(file);
      
      return {
        isValid: qualityCheck.acceptable,
        issues: qualityCheck.issues,
        suggestions: qualityCheck.suggestions,
        qualityScore: qualityCheck.score,
      };
    } catch (error: any) {
      console.error('Check image validation failed:', error);
      return {
        isValid: false,
        issues: ['Image validation failed'],
        suggestions: ['Please try again with a clear image'],
        qualityScore: 0,
      };
    }
  }
}

export default CheckDepositService.getInstance();