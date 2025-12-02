import axios from 'axios';
import { getAuthToken } from '../utils/auth';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
const RECURRING_PAYMENT_SERVICE_URL = `${API_BASE_URL}/api/v1/recurring-payments`;

export interface RecurringPaymentSchedule {
  id: string;
  customerId: string;
  payeeId: string;
  payeeName: string;
  payeeType: 'USER' | 'MERCHANT' | 'BILL';
  amount: number;
  currency: string;
  frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
  dayOfWeek?: number;
  dayOfMonth?: number;
  startDate: string;
  endDate?: string;
  nextExecutionDate?: string;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';
  category?: string;
  description?: string;
  notificationsEnabled: boolean;
  autoRetry: boolean;
  maxRetries: number;
  retryDelay: number;
  totalExecutions: number;
  successfulExecutions: number;
  failedExecutions: number;
  totalAmount: number;
  lastExecutionDate?: string;
  lastExecutionStatus?: string;
  lastExecutionError?: string;
  metadata?: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRecurringPaymentRequest {
  payeeId: string;
  payeeType: 'USER' | 'MERCHANT' | 'BILL';
  amount: number;
  currency: string;
  frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
  dayOfWeek?: number;
  dayOfMonth?: number;
  startDate: string;
  endDate?: string;
  category?: string;
  description?: string;
  notificationsEnabled?: boolean;
  autoRetry?: boolean;
  maxRetries?: number;
  metadata?: Record<string, any>;
}

export interface UpdateRecurringPaymentRequest {
  amount?: number;
  frequency?: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
  dayOfWeek?: number;
  dayOfMonth?: number;
  endDate?: string;
  category?: string;
  description?: string;
  notificationsEnabled?: boolean;
  autoRetry?: boolean;
  maxRetries?: number;
  metadata?: Record<string, any>;
}

export interface RecurringPaymentExecution {
  id: string;
  scheduleId: string;
  executionDate: string;
  amount: number;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  transactionId?: string;
  error?: string;
  retryCount: number;
  createdAt: string;
  completedAt?: string;
}

export interface RecurringPaymentSummary {
  totalSchedules: number;
  activeSchedules: number;
  pausedSchedules: number;
  completedSchedules: number;
  totalMonthlyAmount: number;
  upcomingPayments: number;
  nextPaymentDate?: string;
  recentExecutions: RecurringPaymentExecution[];
}

export interface RecurringPaymentTemplate {
  id: string;
  name: string;
  description?: string;
  category: string;
  suggestedAmount?: number;
  suggestedFrequency?: string;
  merchantId?: string;
  merchantName?: string;
  logoUrl?: string;
  popular: boolean;
  createdAt: string;
}

class RecurringPaymentService {
  private getHeaders() {
    const token = getAuthToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    };
  }

  // Schedule Management
  async createSchedule(request: CreateRecurringPaymentRequest): Promise<RecurringPaymentSchedule> {
    const response = await axios.post(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules`,
      request,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getSchedules(status?: string): Promise<RecurringPaymentSchedule[]> {
    const params = status ? { status } : {};
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async getSchedule(scheduleId: string): Promise<RecurringPaymentSchedule> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async updateSchedule(scheduleId: string, request: UpdateRecurringPaymentRequest): Promise<RecurringPaymentSchedule> {
    const response = await axios.put(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}`,
      request,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async pauseSchedule(scheduleId: string): Promise<RecurringPaymentSchedule> {
    const response = await axios.put(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}/pause`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async resumeSchedule(scheduleId: string): Promise<RecurringPaymentSchedule> {
    const response = await axios.put(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}/resume`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async cancelSchedule(scheduleId: string, reason?: string): Promise<void> {
    await axios.delete(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}`,
      { 
        headers: this.getHeaders(),
        params: reason ? { reason } : {}
      }
    );
  }

  // Execution History
  async getExecutionHistory(scheduleId: string, limit?: number): Promise<RecurringPaymentExecution[]> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}/executions`,
      { 
        headers: this.getHeaders(),
        params: { limit: limit || 50 }
      }
    );
    return response.data;
  }

  async getExecution(executionId: string): Promise<RecurringPaymentExecution> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/executions/${executionId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async retryExecution(executionId: string): Promise<RecurringPaymentExecution> {
    const response = await axios.post(
      `${RECURRING_PAYMENT_SERVICE_URL}/executions/${executionId}/retry`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Summary and Analytics
  async getSummary(): Promise<RecurringPaymentSummary> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/summary`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getUpcomingPayments(days: number = 30): Promise<RecurringPaymentSchedule[]> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/upcoming`,
      { 
        headers: this.getHeaders(),
        params: { days }
      }
    );
    return response.data;
  }

  async getPaymentCalendar(year: number, month: number): Promise<{[date: string]: RecurringPaymentSchedule[]}> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/calendar/${year}/${month}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Templates
  async getTemplates(category?: string): Promise<RecurringPaymentTemplate[]> {
    const params = category ? { category } : {};
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/templates`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async createFromTemplate(templateId: string, customization: Partial<CreateRecurringPaymentRequest>): Promise<RecurringPaymentSchedule> {
    const response = await axios.post(
      `${RECURRING_PAYMENT_SERVICE_URL}/templates/${templateId}/create`,
      customization,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Notifications
  async updateNotificationSettings(scheduleId: string, settings: {
    enabled: boolean;
    daysBefore?: number;
    timeOfDay?: string;
  }): Promise<void> {
    await axios.put(
      `${RECURRING_PAYMENT_SERVICE_URL}/schedules/${scheduleId}/notifications`,
      settings,
      { headers: this.getHeaders() }
    );
  }

  // Bulk Operations
  async pauseMultipleSchedules(scheduleIds: string[]): Promise<void> {
    await axios.post(
      `${RECURRING_PAYMENT_SERVICE_URL}/bulk/pause`,
      { scheduleIds },
      { headers: this.getHeaders() }
    );
  }

  async cancelMultipleSchedules(scheduleIds: string[], reason?: string): Promise<void> {
    await axios.post(
      `${RECURRING_PAYMENT_SERVICE_URL}/bulk/cancel`,
      { scheduleIds, reason },
      { headers: this.getHeaders() }
    );
  }

  // Export
  async exportSchedules(format: 'csv' | 'pdf' = 'csv'): Promise<Blob> {
    const response = await axios.get(
      `${RECURRING_PAYMENT_SERVICE_URL}/export`,
      { 
        headers: this.getHeaders(),
        params: { format },
        responseType: 'blob'
      }
    );
    return response.data;
  }
}

export default new RecurringPaymentService();