import { ApiService } from '../ApiService';
import { PhotoFile } from 'react-native-vision-camera';

export interface CheckDepositRequest {
  frontImagePath: string;
  backImagePath: string;
  amount: number;
  depositAccountId: string;
  memo?: string;
  deviceInfo: {
    deviceId: string;
    location?: {
      latitude: number;
      longitude: number;
      accuracy: number;
    };
    timestamp: string;
  };
}

export interface CheckDepositResponse {
  depositId: string;
  status: CheckDepositStatus;
  submittedAt: string;
  estimatedAvailability: string;
  confirmationNumber: string;
  processingFee?: number;
}

export enum CheckDepositStatus {
  SUBMITTED = 'SUBMITTED',
  PROCESSING = 'PROCESSING',
  UNDER_REVIEW = 'UNDER_REVIEW',
  APPROVED = 'APPROVED',
  DEPOSITED = 'DEPOSITED',
  REJECTED = 'REJECTED',
  CANCELLED = 'CANCELLED',
  FAILED = 'FAILED'
}

export interface CheckDepositStatusDetails {
  depositId: string;
  status: CheckDepositStatus;
  amount: number;
  depositAccountId: string;
  submittedAt: string;
  processedAt?: string;
  availableAt?: string;
  confirmationNumber: string;
  memo?: string;
  rejectionReason?: string;
  processingSteps: ProcessingStep[];
  supportActions: SupportAction[];
}

export interface ProcessingStep {
  step: string;
  status: 'completed' | 'processing' | 'pending' | 'failed';
  timestamp?: string;
  description: string;
}

export interface SupportAction {
  type: 'cancel' | 'contact_support' | 'resubmit' | 'view_details';
  label: string;
  available: boolean;
  description?: string;
}

export interface CheckDepositHistory {
  deposits: CheckDepositStatusDetails[];
  totalCount: number;
  page: number;
  hasMore: boolean;
}

class CheckDepositService {
  private readonly API_BASE = '/api/v1/banking/check-deposits';

  /**
   * Submit a new check deposit
   */
  async submitCheckDeposit(request: CheckDepositRequest): Promise<CheckDepositResponse> {
    try {
      console.log('Submitting check deposit request...');

      // Create FormData for multipart upload
      const formData = new FormData();
      
      // Add check images
      formData.append('frontImage', {
        uri: request.frontImagePath,
        type: 'image/jpeg',
        name: 'check_front.jpg',
      } as any);
      
      formData.append('backImage', {
        uri: request.backImagePath,
        type: 'image/jpeg',
        name: 'check_back.jpg',
      } as any);

      // Add metadata
      formData.append('amount', request.amount.toString());
      formData.append('depositAccountId', request.depositAccountId);
      formData.append('deviceInfo', JSON.stringify(request.deviceInfo));
      
      if (request.memo) {
        formData.append('memo', request.memo);
      }

      const response = await ApiService.postMultipart(`${this.API_BASE}/submit`, formData, {
        timeout: 60000, // 60 second timeout for image upload
      });

      if (!response.success) {
        throw new Error(response.message || 'Check deposit submission failed');
      }

      console.log('Check deposit submitted successfully:', response.data.depositId);
      return response.data;

    } catch (error) {
      console.error('Check deposit submission failed:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Get check deposit status by ID
   */
  async getDepositStatus(depositId: string): Promise<CheckDepositStatusDetails> {
    try {
      const response = await ApiService.get(`${this.API_BASE}/${depositId}/status`);

      if (!response.success) {
        throw new Error(response.message || 'Failed to get deposit status');
      }

      return response.data;

    } catch (error) {
      console.error('Failed to get deposit status:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Get check deposit history with pagination
   */
  async getDepositHistory(
    page: number = 1,
    limit: number = 20,
    status?: CheckDepositStatus
  ): Promise<CheckDepositHistory> {
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        limit: limit.toString(),
      });

      if (status) {
        params.append('status', status);
      }

      const response = await ApiService.get(`${this.API_BASE}/history?${params}`);

      if (!response.success) {
        throw new Error(response.message || 'Failed to get deposit history');
      }

      return response.data;

    } catch (error) {
      console.error('Failed to get deposit history:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Cancel a pending check deposit
   */
  async cancelDeposit(depositId: string, reason?: string): Promise<void> {
    try {
      const response = await ApiService.post(`${this.API_BASE}/${depositId}/cancel`, {
        reason: reason || 'User requested cancellation',
      });

      if (!response.success) {
        throw new Error(response.message || 'Failed to cancel deposit');
      }

      console.log('Check deposit cancelled successfully:', depositId);

    } catch (error) {
      console.error('Failed to cancel deposit:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Resubmit a failed or rejected check deposit
   */
  async resubmitDeposit(
    originalDepositId: string,
    newRequest: CheckDepositRequest
  ): Promise<CheckDepositResponse> {
    try {
      console.log('Resubmitting check deposit...');

      const formData = new FormData();
      
      // Add check images
      formData.append('frontImage', {
        uri: newRequest.frontImagePath,
        type: 'image/jpeg',
        name: 'check_front.jpg',
      } as any);
      
      formData.append('backImage', {
        uri: newRequest.backImagePath,
        type: 'image/jpeg',
        name: 'check_back.jpg',
      } as any);

      // Add metadata
      formData.append('amount', newRequest.amount.toString());
      formData.append('depositAccountId', newRequest.depositAccountId);
      formData.append('deviceInfo', JSON.stringify(newRequest.deviceInfo));
      formData.append('originalDepositId', originalDepositId);
      
      if (newRequest.memo) {
        formData.append('memo', newRequest.memo);
      }

      const response = await ApiService.postMultipart(
        `${this.API_BASE}/${originalDepositId}/resubmit`,
        formData,
        { timeout: 60000 }
      );

      if (!response.success) {
        throw new Error(response.message || 'Check deposit resubmission failed');
      }

      console.log('Check deposit resubmitted successfully:', response.data.depositId);
      return response.data;

    } catch (error) {
      console.error('Check deposit resubmission failed:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Get deposit limits and fees
   */
  async getDepositLimits(): Promise<{
    dailyLimit: number;
    monthlyLimit: number;
    perCheckLimit: number;
    processingFee: number;
    dailyUsed: number;
    monthlyUsed: number;
  }> {
    try {
      const response = await ApiService.get(`${this.API_BASE}/limits`);

      if (!response.success) {
        throw new Error(response.message || 'Failed to get deposit limits');
      }

      return response.data;

    } catch (error) {
      console.error('Failed to get deposit limits:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Validate check image quality before submission
   */
  async validateCheckImages(
    frontImagePath: string,
    backImagePath: string
  ): Promise<{
    isValid: boolean;
    issues: string[];
    recommendations: string[];
  }> {
    try {
      const formData = new FormData();
      
      formData.append('frontImage', {
        uri: frontImagePath,
        type: 'image/jpeg',
        name: 'check_front.jpg',
      } as any);
      
      formData.append('backImage', {
        uri: backImagePath,
        type: 'image/jpeg',
        name: 'check_back.jpg',
      } as any);

      const response = await ApiService.postMultipart(
        `${this.API_BASE}/validate-images`,
        formData,
        { timeout: 30000 }
      );

      if (!response.success) {
        throw new Error(response.message || 'Image validation failed');
      }

      return response.data;

    } catch (error) {
      console.error('Check image validation failed:', error);
      throw this.handleApiError(error);
    }
  }

  /**
   * Get support contact information for deposits
   */
  async getSupportInfo(): Promise<{
    phone: string;
    email: string;
    chatAvailable: boolean;
    businessHours: string;
  }> {
    try {
      const response = await ApiService.get(`${this.API_BASE}/support`);

      if (!response.success) {
        throw new Error(response.message || 'Failed to get support info');
      }

      return response.data;

    } catch (error) {
      console.error('Failed to get support info:', error);
      throw this.handleApiError(error);
    }
  }

  private handleApiError(error: any): Error {
    if (error.response) {
      // Server responded with error status
      const { status, data } = error.response;
      
      switch (status) {
        case 400:
          return new Error(data.message || 'Invalid request data');
        case 401:
          return new Error('Authentication required');
        case 403:
          return new Error('Insufficient permissions for check deposits');
        case 409:
          return new Error('Duplicate check deposit detected');
        case 413:
          return new Error('Check images are too large');
        case 422:
          return new Error(data.message || 'Check deposit validation failed');
        case 429:
          return new Error('Daily deposit limit exceeded');
        case 500:
          return new Error('Server error processing check deposit');
        default:
          return new Error(data.message || 'Check deposit service error');
      }
    } else if (error.request) {
      // Network error
      return new Error('Network error. Please check your connection and try again.');
    } else {
      // Other error
      return error instanceof Error ? error : new Error('Unknown error occurred');
    }
  }
}

export default new CheckDepositService();