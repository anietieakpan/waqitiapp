import { ApiService } from './ApiService';

export interface TaxReport {
  id: string;
  taxYear: number;
  reportType: string;
  status: 'GENERATING' | 'COMPLETED' | 'ERROR';
  forms: TaxForm[];
  summary: TaxSummary;
  generatedAt: string;
  downloadUrl?: string;
}

export interface TaxForm {
  formType: string;
  formName: string;
  status: 'COMPLETE' | 'INCOMPLETE' | 'NOT_APPLICABLE';
  requiredData: string[];
  missingData: string[];
  estimatedRefund?: number;
  estimatedTax?: number;
}

export interface TaxSummary {
  totalIncome: number;
  totalDeductions: number;
  capitalGains: number;
  capitalLosses: number;
  cryptoGains: number;
  dividendIncome: number;
  interestIncome: number;
  estimatedTax: number;
  estimatedRefund: number;
}

export interface TaxDocument {
  id: string;
  type: string;
  name: string;
  uploadedAt: string;
  status: 'PROCESSING' | 'VERIFIED' | 'REJECTED';
  ocrData?: any;
  validationErrors?: string[];
}

export interface TaxReportRequest {
  taxYear: number;
  includeTransactions: boolean;
  includeCrypto: boolean;
  includeInvestments: boolean;
  includeInternational: boolean;
}

export interface TaxDocumentUpload {
  taxYear: number;
  file: {
    uri: string;
    type: string;
    name: string;
  };
  documentType?: string;
}

export interface TaxOptimization {
  strategies: TaxStrategy[];
  potentialSavings: number;
  recommendations: TaxRecommendation[];
}

export interface TaxStrategy {
  name: string;
  description: string;
  potentialSavings: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  deadline?: string;
  actionRequired: string;
}

export interface TaxRecommendation {
  category: string;
  recommendation: string;
  impact: number;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface TaxEstimate {
  federalTax: number;
  stateTax: number;
  totalTax: number;
  effectiveRate: number;
  marginalRate: number;
  estimatedRefund: number;
  quarterlyPayments?: number;
}

class TaxService {
  private apiService = new ApiService();

  async getTaxReport(taxYear: number): Promise<{
    success: boolean;
    data?: TaxReport;
    errorMessage?: string;
    errorCode?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/reports/${taxYear}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
        errorCode: error.response?.data?.code,
      };
    }
  }

  async generateTaxReport(request: TaxReportRequest): Promise<{
    success: boolean;
    data?: TaxReport;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.post('/api/v1/tax/reports/generate', request);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getTaxDocuments(taxYear: number): Promise<{
    success: boolean;
    data?: TaxDocument[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/documents`, {
        params: { taxYear },
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async uploadTaxDocument(upload: TaxDocumentUpload): Promise<{
    success: boolean;
    data?: TaxDocument;
    errorMessage?: string;
  }> {
    try {
      const formData = new FormData();
      formData.append('file', {
        uri: upload.file.uri,
        type: upload.file.type,
        name: upload.file.name,
      } as any);
      formData.append('taxYear', upload.taxYear.toString());
      if (upload.documentType) {
        formData.append('documentType', upload.documentType);
      }

      const response = await this.apiService.post('/api/v1/tax/documents/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async deleteTaxDocument(documentId: string): Promise<{
    success: boolean;
    errorMessage?: string;
  }> {
    try {
      await this.apiService.delete(`/api/v1/tax/documents/${documentId}`);

      return { success: true };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getTaxEstimate(taxYear: number): Promise<{
    success: boolean;
    data?: TaxEstimate;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/estimate/${taxYear}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getTaxOptimization(taxYear: number): Promise<{
    success: boolean;
    data?: TaxOptimization;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/optimization/${taxYear}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getCryptoTaxReport(taxYear: number): Promise<{
    success: boolean;
    data?: CryptoTaxReport;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/crypto/${taxYear}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getInternationalTaxReport(taxYear: number): Promise<{
    success: boolean;
    data?: InternationalTaxReport;
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/international/${taxYear}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async scheduleReminders(taxYear: number, reminders: TaxReminder[]): Promise<{
    success: boolean;
    errorMessage?: string;
  }> {
    try {
      await this.apiService.post(`/api/v1/tax/reminders/${taxYear}`, {
        reminders,
      });

      return { success: true };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getTaxCalendar(taxYear: number): Promise<{
    success: boolean;
    data?: TaxCalendarEvent[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get(`/api/v1/tax/calendar/${taxYear}`);

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async exportTaxData(taxYear: number, format: 'CSV' | 'PDF' | 'JSON'): Promise<{
    success: boolean;
    data?: { downloadUrl: string };
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.post(`/api/v1/tax/export/${taxYear}`, {
        format,
      });

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async getTaxPreparers(): Promise<{
    success: boolean;
    data?: TaxPreparer[];
    errorMessage?: string;
  }> {
    try {
      const response = await this.apiService.get('/api/v1/tax/preparers');

      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }

  async connectTaxPreparer(preparerId: string): Promise<{
    success: boolean;
    errorMessage?: string;
  }> {
    try {
      await this.apiService.post(`/api/v1/tax/preparers/${preparerId}/connect`);

      return { success: true };
    } catch (error: any) {
      return {
        success: false,
        errorMessage: error.response?.data?.message || error.message,
      };
    }
  }
}

// Supporting interfaces
export interface CryptoTaxReport {
  totalGains: number;
  totalLosses: number;
  shortTermGains: number;
  longTermGains: number;
  transactions: CryptoTaxTransaction[];
  summary: string;
}

export interface CryptoTaxTransaction {
  date: string;
  type: 'BUY' | 'SELL' | 'TRADE' | 'MINING' | 'STAKING';
  cryptocurrency: string;
  amount: number;
  price: number;
  fiatValue: number;
  gainLoss?: number;
  holdingPeriod?: number;
}

export interface InternationalTaxReport {
  foreignIncome: number;
  foreignTaxCredit: number;
  fbars: FbarRequirement[];
  fatcaReporting: boolean;
  treatyBenefits: TreatyBenefit[];
}

export interface FbarRequirement {
  country: string;
  accountType: string;
  maxBalance: number;
  reportingRequired: boolean;
}

export interface TreatyBenefit {
  country: string;
  treatyName: string;
  applicableBenefit: string;
  potentialSavings: number;
}

export interface TaxReminder {
  type: 'DEADLINE' | 'DOCUMENT' | 'PAYMENT';
  description: string;
  date: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface TaxCalendarEvent {
  date: string;
  type: 'DEADLINE' | 'ESTIMATED_PAYMENT' | 'DOCUMENT_AVAILABLE';
  title: string;
  description: string;
  action?: string;
}

export interface TaxPreparer {
  id: string;
  name: string;
  credentials: string[];
  rating: number;
  specializations: string[];
  location: string;
  contactInfo: {
    phone: string;
    email: string;
    website?: string;
  };
  pricing: {
    hourlyRate?: number;
    flatFee?: number;
    description: string;
  };
}

export const taxService = new TaxService();