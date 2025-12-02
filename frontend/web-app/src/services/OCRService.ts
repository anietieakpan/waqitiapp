/**
 * OCR Service - Real integration with backend OCR processing
 * Web app version for receipt scanning and document processing
 */

import { apiClient } from './apiClient';

export interface OCRResult {
  success: boolean;
  confidence: number;
  extractedData: ReceiptData | CheckData | DocumentData;
  originalText?: string;
  processingTimeMs: number;
}

export interface ReceiptData {
  type: 'receipt';
  merchantName?: string;
  merchantAddress?: string;
  date?: string;
  items: ReceiptItem[];
  subtotal?: number;
  tax?: number;
  tip?: number;
  total?: number;
  currency?: string;
  paymentMethod?: string;
}

export interface ReceiptItem {
  name: string;
  description?: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  category?: string;
  taxable?: boolean;
}

export interface CheckData {
  type: 'check';
  checkNumber?: string;
  routingNumber?: string;
  accountNumber?: string;
  amount?: number;
  writtenAmount?: string;
  payeeName?: string;
  payorName?: string;
  checkDate?: string;
  memo?: string;
  bankName?: string;
  confidence: number;
  amountMismatch?: boolean;
}

export interface DocumentData {
  type: 'document';
  documentType: string;
  extractedFields: Record<string, any>;
  confidence: number;
}

export interface OCRProcessingOptions {
  documentType: 'receipt' | 'check' | 'invoice' | 'business_card' | 'id_document' | 'generic';
  language?: string;
  enhanceImage?: boolean;
  extractStructuredData?: boolean;
  validateData?: boolean;
}

export interface ImageQualityCheck {
  acceptable: boolean;
  issues: string[];
  suggestions: string[];
  score: number; // 0-1
}

class OCRService {
  /**
   * Check image quality before processing
   */
  async checkImageQuality(imageFile: File | Blob): Promise<ImageQualityCheck> {
    try {
      const formData = new FormData();
      formData.append('image', imageFile, 'image.jpg');

      const response = await apiClient.post<{
        acceptable: boolean;
        reason?: string;
        resolution?: string;
        contrastRatio?: number;
        blurScore?: number;
        skewAngle?: number;
      }>('/ocr/quality-check', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const issues: string[] = [];
      const suggestions: string[] = [];
      let score = 1.0;

      if (!response.data.acceptable) {
        issues.push(response.data.reason || 'Image quality issue detected');
        score = 0.3;

        // Add specific suggestions based on the issue
        if (response.data.reason?.includes('resolution')) {
          suggestions.push('Try uploading a higher resolution image');
        }
        if (response.data.reason?.includes('contrast')) {
          suggestions.push('Ensure good lighting and clear contrast');
        }
        if (response.data.reason?.includes('blur')) {
          suggestions.push('Make sure the image is sharp and in focus');
        }
        if (response.data.reason?.includes('skew')) {
          suggestions.push('Straighten the document in the image');
        }
      } else {
        // Calculate quality score based on metrics
        score = Math.min(1.0, (response.data.contrastRatio || 0.8) * 
                             (1.0 - (response.data.blurScore || 0.1)) *
                             (1.0 - Math.abs(response.data.skewAngle || 0) / 10));
      }

      return {
        acceptable: response.data.acceptable,
        issues,
        suggestions,
        score,
      };

    } catch (error: any) {
      console.error('Image quality check failed:', error);
      return {
        acceptable: false,
        issues: ['Unable to analyze image quality'],
        suggestions: ['Please try again with a clear, well-lit image'],
        score: 0,
      };
    }
  }

  /**
   * Process receipt image with OCR
   */
  async processReceiptImage(
    imageFile: File | Blob,
    options?: Partial<OCRProcessingOptions>
  ): Promise<OCRResult> {
    try {
      const formData = new FormData();
      formData.append('image', imageFile, 'receipt.jpg');

      // Add processing options
      const processingOptions = {
        documentType: 'receipt',
        language: 'en',
        enhanceImage: true,
        extractStructuredData: true,
        validateData: true,
        ...options,
      };

      Object.entries(processingOptions).forEach(([key, value]) => {
        formData.append(key, value.toString());
      });

      const startTime = Date.now();
      
      const response = await apiClient.post<{
        success: boolean;
        merchantName?: string;
        merchantAddress?: string;
        date?: string;
        items: Array<{
          name: string;
          description?: string;
          quantity: number;
          unitPrice: number;
          totalPrice: number;
          category?: string;
          taxable?: boolean;
        }>;
        subtotal?: number;
        tax?: number;
        tip?: number;
        total?: number;
        currency?: string;
        paymentMethod?: string;
        confidence: number;
        originalText?: string;
      }>('/ocr/receipt', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const processingTimeMs = Date.now() - startTime;

      const receiptData: ReceiptData = {
        type: 'receipt',
        merchantName: response.data.merchantName,
        merchantAddress: response.data.merchantAddress,
        date: response.data.date,
        items: response.data.items || [],
        subtotal: response.data.subtotal,
        tax: response.data.tax,
        tip: response.data.tip,
        total: response.data.total,
        currency: response.data.currency || 'USD',
        paymentMethod: response.data.paymentMethod,
      };

      return {
        success: response.data.success,
        confidence: response.data.confidence || 0,
        extractedData: receiptData,
        originalText: response.data.originalText,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Receipt OCR processing failed:', error);
      throw new Error(error.response?.data?.message || error.message || 'Failed to process receipt image');
    }
  }

  /**
   * Process check image with OCR
   */
  async processCheckImage(
    imageFile: File | Blob,
    options?: Partial<OCRProcessingOptions>
  ): Promise<OCRResult> {
    try {
      const formData = new FormData();
      formData.append('image', imageFile, 'check.jpg');

      const processingOptions = {
        documentType: 'check',
        language: 'en',
        enhanceImage: true,
        extractStructuredData: true,
        validateData: true,
        ...options,
      };

      Object.entries(processingOptions).forEach(([key, value]) => {
        formData.append(key, value.toString());
      });

      const startTime = Date.now();
      
      const response = await apiClient.post<{
        success: boolean;
        checkNumber?: string;
        routingNumber?: string;
        accountNumber?: string;
        numericAmount?: number;
        writtenAmount?: string;
        payeeName?: string;
        payorName?: string;
        checkDate?: string;
        memo?: string;
        bankName?: string;
        confidence: number;
        amountMismatch?: boolean;
        originalText?: string;
      }>('/payment/checks/ocr-process', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const processingTimeMs = Date.now() - startTime;

      const checkData: CheckData = {
        type: 'check',
        checkNumber: response.data.checkNumber,
        routingNumber: response.data.routingNumber,
        accountNumber: response.data.accountNumber,
        amount: response.data.numericAmount,
        writtenAmount: response.data.writtenAmount,
        payeeName: response.data.payeeName,
        payorName: response.data.payorName,
        checkDate: response.data.checkDate,
        memo: response.data.memo,
        bankName: response.data.bankName,
        confidence: response.data.confidence || 0,
        amountMismatch: response.data.amountMismatch || false,
      };

      return {
        success: response.data.success,
        confidence: response.data.confidence || 0,
        extractedData: checkData,
        originalText: response.data.originalText,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Check OCR processing failed:', error);
      throw new Error(error.response?.data?.message || error.message || 'Failed to process check image');
    }
  }

  /**
   * Process generic document image with OCR
   */
  async processDocument(
    imageFile: File | Blob,
    documentType: string,
    options?: Partial<OCRProcessingOptions>
  ): Promise<OCRResult> {
    try {
      const formData = new FormData();
      formData.append('image', imageFile, 'document.jpg');

      const processingOptions = {
        documentType: 'generic',
        language: 'en',
        enhanceImage: true,
        extractStructuredData: false,
        validateData: false,
        ...options,
      };

      Object.entries(processingOptions).forEach(([key, value]) => {
        formData.append(key, value.toString());
      });

      const startTime = Date.now();
      
      const response = await apiClient.post<{
        success: boolean;
        extractedText: string;
        extractedFields: Record<string, any>;
        confidence: number;
      }>('/kyc/documents/ocr-process', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const processingTimeMs = Date.now() - startTime;

      const documentData: DocumentData = {
        type: 'document',
        documentType,
        extractedFields: response.data.extractedFields || {},
        confidence: response.data.confidence || 0,
      };

      return {
        success: response.data.success,
        confidence: response.data.confidence || 0,
        extractedData: documentData,
        originalText: response.data.extractedText,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Document OCR processing failed:', error);
      throw new Error(error.response?.data?.message || error.message || 'Failed to process document image');
    }
  }

  /**
   * Extract text from image (basic OCR)
   */
  async extractText(
    imageFile: File | Blob,
    language: string = 'en'
  ): Promise<{
    text: string;
    confidence: number;
    processingTimeMs: number;
  }> {
    try {
      const formData = new FormData();
      formData.append('image', imageFile, 'image.jpg');
      formData.append('language', language);

      const startTime = Date.now();
      
      const response = await apiClient.post<{
        text: string;
        confidence: number;
      }>('/ocr/extract-text', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const processingTimeMs = Date.now() - startTime;

      return {
        text: response.data.text || '',
        confidence: response.data.confidence || 0,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Text extraction failed:', error);
      throw new Error(error.response?.data?.message || error.message || 'Failed to extract text from image');
    }
  }

  /**
   * Get supported languages for OCR
   */
  async getSupportedLanguages(): Promise<Array<{ code: string; name: string }>> {
    try {
      const response = await apiClient.get<{
        languages: Array<{ code: string; name: string }>;
      }>('/ocr/languages');

      return response.data.languages || [
        { code: 'en', name: 'English' },
        { code: 'es', name: 'Spanish' },
        { code: 'fr', name: 'French' },
        { code: 'de', name: 'German' },
        { code: 'pt', name: 'Portuguese' },
      ];
    } catch (error: any) {
      console.error('Failed to get supported languages:', error);
      // Return default languages as fallback
      return [
        { code: 'en', name: 'English' },
        { code: 'es', name: 'Spanish' },
        { code: 'fr', name: 'French' },
        { code: 'de', name: 'German' },
        { code: 'pt', name: 'Portuguese' },
      ];
    }
  }

  /**
   * Validate extracted data against business rules
   */
  async validateExtractedData(
    data: ReceiptData | CheckData,
    validationType: 'receipt' | 'check'
  ): Promise<{
    isValid: boolean;
    warnings: string[];
    errors: string[];
    suggestions: string[];
  }> {
    try {
      const response = await apiClient.post<{
        isValid: boolean;
        warnings: string[];
        errors: string[];
        suggestions: string[];
      }>('/ocr/validate', {
        data,
        type: validationType,
      });

      return response.data;
    } catch (error: any) {
      console.error('Data validation failed:', error);
      return {
        isValid: false,
        warnings: [],
        errors: ['Validation service unavailable'],
        suggestions: ['Please verify the extracted data manually'],
      };
    }
  }

  /**
   * Get OCR processing statistics
   */
  async getOCRStats(): Promise<{
    totalProcessed: number;
    successRate: number;
    avgConfidence: number;
    avgProcessingTime: number;
    mostCommonIssues: Array<{ issue: string; count: number }>;
  }> {
    try {
      const response = await apiClient.get<{
        totalProcessed: number;
        successRate: number;
        avgConfidence: number;
        avgProcessingTime: number;
        mostCommonIssues: Array<{ issue: string; count: number }>;
      }>('/ocr/stats');

      return response.data;
    } catch (error: any) {
      console.error('Failed to get OCR stats:', error);
      throw new Error(error.response?.data?.message || error.message || 'Failed to get OCR statistics');
    }
  }
}

export default new OCRService();