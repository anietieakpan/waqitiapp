import axios from 'axios';
import { getAuthToken } from '../utils/auth';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
const REPORTING_SERVICE_URL = `${API_BASE_URL}/api/v1/reports`;

export interface Report {
  id: string;
  name: string;
  description?: string;
  type: 'TRANSACTION' | 'FINANCIAL' | 'TAX' | 'COMPLIANCE' | 'ANALYTICS' | 'CUSTOM';
  format: 'PDF' | 'CSV' | 'EXCEL' | 'JSON';
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'EXPIRED';
  parameters?: ReportParameters;
  createdAt: string;
  completedAt?: string;
  expiresAt?: string;
  downloadUrl?: string;
  fileSize?: number;
  error?: string;
}

export interface ReportParameters {
  startDate?: string;
  endDate?: string;
  accountIds?: string[];
  transactionTypes?: string[];
  categories?: string[];
  merchantIds?: string[];
  minAmount?: number;
  maxAmount?: number;
  currency?: string;
  groupBy?: string;
  includeDetails?: boolean;
  customFilters?: Record<string, any>;
}

export interface ReportTemplate {
  id: string;
  name: string;
  description?: string;
  type: string;
  category: string;
  parameters: ReportParameters;
  schedule?: ReportSchedule;
  isPublic: boolean;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReportSchedule {
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
  dayOfWeek?: number;
  dayOfMonth?: number;
  time?: string;
  timezone?: string;
  enabled: boolean;
  nextRunDate?: string;
  lastRunDate?: string;
  recipients?: string[];
}

export interface FinancialSummary {
  period: string;
  totalIncome: number;
  totalExpenses: number;
  netIncome: number;
  savingsRate: number;
  topExpenseCategories: CategorySummary[];
  monthlyTrend: MonthlyTrend[];
  yearOverYearChange: number;
  budgetStatus?: BudgetStatus;
}

export interface CategorySummary {
  category: string;
  amount: number;
  percentage: number;
  transactionCount: number;
  averageTransaction: number;
  trend: 'UP' | 'DOWN' | 'STABLE';
  changePercent: number;
}

export interface MonthlyTrend {
  month: string;
  income: number;
  expenses: number;
  netIncome: number;
  savingsRate: number;
}

export interface BudgetStatus {
  totalBudget: number;
  totalSpent: number;
  remainingBudget: number;
  percentUsed: number;
  categoryBudgets: CategoryBudget[];
  projectedOverspend?: number;
}

export interface CategoryBudget {
  category: string;
  budgetAmount: number;
  spentAmount: number;
  remainingAmount: number;
  percentUsed: number;
  status: 'ON_TRACK' | 'WARNING' | 'OVER_BUDGET';
}

export interface TaxDocument {
  id: string;
  year: number;
  documentType: '1099-K' | '1099-MISC' | '1099-INT' | 'SUMMARY';
  status: 'PENDING' | 'AVAILABLE' | 'FILED';
  generatedDate?: string;
  downloadUrl?: string;
  summary?: TaxSummary;
}

export interface TaxSummary {
  totalPaymentsReceived: number;
  totalPaymentsMade: number;
  totalFees: number;
  totalInterestEarned: number;
  numberOf1099s: number;
  estimatedTaxLiability?: number;
}

export interface Analytics {
  period: string;
  metrics: AnalyticsMetric[];
  insights: Insight[];
  comparisons: Comparison[];
}

export interface AnalyticsMetric {
  name: string;
  value: number;
  unit?: string;
  change: number;
  changePercent: number;
  trend: number[];
}

export interface Insight {
  type: string;
  title: string;
  description: string;
  impact: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  importance: 'LOW' | 'MEDIUM' | 'HIGH';
  actionable: boolean;
  suggestedAction?: string;
}

export interface Comparison {
  metric: string;
  currentPeriod: number;
  previousPeriod: number;
  change: number;
  changePercent: number;
  benchmark?: number;
  performanceVsBenchmark?: number;
}

export interface ExportRequest {
  type: 'TRANSACTIONS' | 'STATEMENTS' | 'TAX_DOCUMENTS';
  format: 'CSV' | 'PDF' | 'JSON';
  dateRange: {
    startDate: string;
    endDate: string;
  };
  filters?: Record<string, any>;
  includeAttachments?: boolean;
}

class ReportingService {
  private getHeaders() {
    const token = getAuthToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    };
  }

  // Report Generation
  async generateReport(type: string, parameters: ReportParameters, format: string = 'PDF'): Promise<Report> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/generate`,
      { type, parameters, format },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getReports(status?: string): Promise<Report[]> {
    const params = status ? { status } : {};
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async getReport(reportId: string): Promise<Report> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/${reportId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async downloadReport(reportId: string): Promise<Blob> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/${reportId}/download`,
      {
        headers: this.getHeaders(),
        responseType: 'blob'
      }
    );
    return response.data;
  }

  async deleteReport(reportId: string): Promise<void> {
    await axios.delete(
      `${REPORTING_SERVICE_URL}/${reportId}`,
      { headers: this.getHeaders() }
    );
  }

  // Report Templates
  async getTemplates(category?: string): Promise<ReportTemplate[]> {
    const params = category ? { category } : {};
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/templates`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async createTemplate(template: Partial<ReportTemplate>): Promise<ReportTemplate> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/templates`,
      template,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async updateTemplate(templateId: string, updates: Partial<ReportTemplate>): Promise<ReportTemplate> {
    const response = await axios.put(
      `${REPORTING_SERVICE_URL}/templates/${templateId}`,
      updates,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async deleteTemplate(templateId: string): Promise<void> {
    await axios.delete(
      `${REPORTING_SERVICE_URL}/templates/${templateId}`,
      { headers: this.getHeaders() }
    );
  }

  async generateFromTemplate(templateId: string, parameters?: ReportParameters): Promise<Report> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/templates/${templateId}/generate`,
      { parameters },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Financial Reports
  async getFinancialSummary(period: string = 'MONTHLY'): Promise<FinancialSummary> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/financial/summary`,
      { headers: this.getHeaders(), params: { period } }
    );
    return response.data;
  }

  async getIncomeStatement(startDate: string, endDate: string): Promise<{
    revenue: CategorySummary[];
    expenses: CategorySummary[];
    netIncome: number;
    profitMargin: number;
  }> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/financial/income-statement`,
      { headers: this.getHeaders(), params: { startDate, endDate } }
    );
    return response.data;
  }

  async getCashFlow(startDate: string, endDate: string): Promise<{
    inflows: CategorySummary[];
    outflows: CategorySummary[];
    netCashFlow: number;
    openingBalance: number;
    closingBalance: number;
  }> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/financial/cash-flow`,
      { headers: this.getHeaders(), params: { startDate, endDate } }
    );
    return response.data;
  }

  // Tax Documents
  async getTaxDocuments(year?: number): Promise<TaxDocument[]> {
    const params = year ? { year } : {};
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/tax/documents`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async generateTaxDocument(year: number, documentType: string): Promise<TaxDocument> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/tax/generate`,
      { year, documentType },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getTaxSummary(year: number): Promise<TaxSummary> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/tax/summary/${year}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Analytics
  async getAnalytics(period: string = '30d', metrics?: string[]): Promise<Analytics> {
    const params = { period, metrics: metrics?.join(',') };
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/analytics`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async getCustomAnalytics(query: {
    metrics: string[];
    dimensions?: string[];
    filters?: Record<string, any>;
    startDate: string;
    endDate: string;
  }): Promise<any> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/analytics/custom`,
      query,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Scheduled Reports
  async getScheduledReports(): Promise<ReportTemplate[]> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/scheduled`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async scheduleReport(templateId: string, schedule: ReportSchedule): Promise<ReportTemplate> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/templates/${templateId}/schedule`,
      schedule,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async pauseScheduledReport(templateId: string): Promise<void> {
    await axios.put(
      `${REPORTING_SERVICE_URL}/templates/${templateId}/schedule/pause`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async resumeScheduledReport(templateId: string): Promise<void> {
    await axios.put(
      `${REPORTING_SERVICE_URL}/templates/${templateId}/schedule/resume`,
      {},
      { headers: this.getHeaders() }
    );
  }

  // Export
  async exportData(request: ExportRequest): Promise<{
    exportId: string;
    status: string;
    estimatedTime?: number;
  }> {
    const response = await axios.post(
      `${REPORTING_SERVICE_URL}/export`,
      request,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getExportStatus(exportId: string): Promise<{
    exportId: string;
    status: string;
    progress: number;
    downloadUrl?: string;
    error?: string;
  }> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/export/${exportId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Real-time Dashboard Data
  async getDashboardData(): Promise<{
    summary: FinancialSummary;
    recentTransactions: any[];
    alerts: any[];
    quickStats: AnalyticsMetric[];
  }> {
    const response = await axios.get(
      `${REPORTING_SERVICE_URL}/dashboard`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }
}

export default new ReportingService();