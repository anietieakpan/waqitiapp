/**
 * OCR Service - Real integration with backend OCR processing
 * Handles receipt scanning, check processing, and document OCR
 */

import ApiService from './ApiService';

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
  private static instance: OCRService;
  private apiService = ApiService.getInstance();

  static getInstance(): OCRService {
    if (!OCRService.instance) {
      OCRService.instance = new OCRService();
    }
    return OCRService.instance;
  }

  /**
   * Check image quality before processing
   */
  async checkImageQuality(imageData: string | File): Promise<ImageQualityCheck> {
    try {
      const formData = new FormData();
      
      if (typeof imageData === 'string') {
        // Convert base64 to blob
        const response = await fetch(imageData);
        const blob = await response.blob();
        formData.append('image', blob, 'image.jpg');
      } else {
        formData.append('image', imageData);
      }

      const response = await this.apiService.post<{
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

      if (!response.acceptable) {
        issues.push(response.reason || 'Image quality issue detected');
        score = 0.3;

        // Add specific suggestions based on the issue
        if (response.reason?.includes('resolution')) {
          suggestions.push('Try taking a closer, higher resolution photo');
        }
        if (response.reason?.includes('contrast')) {
          suggestions.push('Ensure good lighting and avoid shadows');
        }
        if (response.reason?.includes('blur')) {
          suggestions.push('Hold the device steady and ensure the image is in focus');
        }
        if (response.reason?.includes('skew')) {
          suggestions.push('Align the document straight with the camera');
        }
      } else {
        // Calculate quality score based on metrics
        score = Math.min(1.0, (response.contrastRatio || 0.8) * 
                             (1.0 - (response.blurScore || 0.1)) *
                             (1.0 - Math.abs(response.skewAngle || 0) / 10));
      }

      return {
        acceptable: response.acceptable,
        issues,
        suggestions,
        score,
      };

    } catch (error: any) {
      console.error('Image quality check failed:', error);
      return {
        acceptable: false,
        issues: ['Unable to analyze image quality'],
        suggestions: ['Please try again with a clear, well-lit photo'],
        score: 0,
      };
    }
  }

  /**
   * Process receipt image with OCR
   */
  async processReceiptImage(
    imageData: string | File,
    options?: Partial<OCRProcessingOptions>
  ): Promise<OCRResult> {
    try {
      const formData = new FormData();
      
      if (typeof imageData === 'string') {
        // Convert base64 to blob
        const response = await fetch(imageData);
        const blob = await response.blob();
        formData.append('image', blob, 'receipt.jpg');
      } else {
        formData.append('image', imageData);
      }

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
      
      const response = await this.apiService.post<{
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
        merchantName: response.merchantName,
        merchantAddress: response.merchantAddress,
        date: response.date,
        items: response.items || [],
        subtotal: response.subtotal,
        tax: response.tax,
        tip: response.tip,
        total: response.total,
        currency: response.currency || 'USD',
        paymentMethod: response.paymentMethod,
      };

      return {
        success: response.success,
        confidence: response.confidence || 0,
        extractedData: receiptData,
        originalText: response.originalText,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Receipt OCR processing failed:', error);
      throw new Error(error.message || 'Failed to process receipt image');
    }
  }

  /**
   * Process check image with OCR
   */
  async processCheckImage(
    imageData: string | File,
    options?: Partial<OCRProcessingOptions>
  ): Promise<OCRResult> {
    try {
      const formData = new FormData();
      
      if (typeof imageData === 'string') {
        const response = await fetch(imageData);
        const blob = await response.blob();
        formData.append('image', blob, 'check.jpg');
      } else {
        formData.append('image', imageData);
      }

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
      
      const response = await this.apiService.post<{
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
        checkNumber: response.checkNumber,
        routingNumber: response.routingNumber,
        accountNumber: response.accountNumber,
        amount: response.numericAmount,
        writtenAmount: response.writtenAmount,
        payeeName: response.payeeName,
        payorName: response.payorName,
        checkDate: response.checkDate,
        memo: response.memo,
        bankName: response.bankName,
        confidence: response.confidence || 0,
        amountMismatch: response.amountMismatch || false,
      };

      return {
        success: response.success,
        confidence: response.confidence || 0,
        extractedData: checkData,
        originalText: response.originalText,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Check OCR processing failed:', error);
      throw new Error(error.message || 'Failed to process check image');
    }
  }

  /**
   * Process generic document image with OCR
   */
  async processDocument(
    imageData: string | File,
    documentType: string,
    options?: Partial<OCRProcessingOptions>
  ): Promise<OCRResult> {
    try {
      const formData = new FormData();
      
      if (typeof imageData === 'string') {
        const response = await fetch(imageData);
        const blob = await response.blob();
        formData.append('image', blob, 'document.jpg');
      } else {
        formData.append('image', imageData);
      }

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
      
      const response = await this.apiService.post<{
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
        extractedFields: response.extractedFields || {},
        confidence: response.confidence || 0,
      };

      return {
        success: response.success,
        confidence: response.confidence || 0,
        extractedData: documentData,
        originalText: response.extractedText,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Document OCR processing failed:', error);
      throw new Error(error.message || 'Failed to process document image');
    }
  }

  /**
   * Extract text from image (basic OCR)
   */
  async extractText(
    imageData: string | File,
    language: string = 'en'
  ): Promise<{
    text: string;
    confidence: number;
    processingTimeMs: number;
  }> {
    try {
      const formData = new FormData();
      
      if (typeof imageData === 'string') {
        const response = await fetch(imageData);
        const blob = await response.blob();
        formData.append('image', blob, 'image.jpg');
      } else {
        formData.append('image', imageData);
      }

      formData.append('language', language);

      const startTime = Date.now();
      
      const response = await this.apiService.post<{
        text: string;
        confidence: number;
      }>('/ocr/extract-text', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const processingTimeMs = Date.now() - startTime;

      return {
        text: response.text || '',
        confidence: response.confidence || 0,
        processingTimeMs,
      };

    } catch (error: any) {
      console.error('Text extraction failed:', error);
      throw new Error(error.message || 'Failed to extract text from image');
    }
  }

  /**
   * Get supported languages for OCR
   */
  async getSupportedLanguages(): Promise<Array<{ code: string; name: string }>> {
    try {
      const response = await this.apiService.get<{
        languages: Array<{ code: string; name: string }>;
      }>('/ocr/languages');

      return response.languages || [
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
      const response = await this.apiService.post<{
        isValid: boolean;
        warnings: string[];
        errors: string[];
        suggestions: string[];
      }>('/ocr/validate', {
        data,
        type: validationType,
      });

      return response;
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
      const response = await this.apiService.get<{
        totalProcessed: number;
        successRate: number;
        avgConfidence: number;
        avgProcessingTime: number;
        mostCommonIssues: Array<{ issue: string; count: number }>;
      }>('/ocr/stats');

      return response;
    } catch (error: any) {
      console.error('Failed to get OCR stats:', error);
      throw new Error(error.message || 'Failed to get OCR statistics');
    }
  }
}

export default OCRService.getInstance();