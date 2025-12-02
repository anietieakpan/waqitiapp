import axios from 'axios';
import { getAuthToken } from '../utils/auth';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
const COMPLIANCE_SERVICE_URL = `${API_BASE_URL}/api/v1/compliance`;

export interface ComplianceStatus {
  customerId: string;
  kycStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'EXPIRED' | 'UNDER_REVIEW';
  amlStatus: 'CLEAR' | 'PENDING_REVIEW' | 'HIGH_RISK' | 'BLOCKED';
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
  riskScore: number;
  lastVerificationDate?: string;
  nextReviewDate?: string;
  restrictions: ComplianceRestriction[];
  requiredDocuments: RequiredDocument[];
  complianceChecks: ComplianceCheck[];
}

export interface ComplianceRestriction {
  type: string;
  description: string;
  appliedDate: string;
  expiryDate?: string;
  reason: string;
}

export interface RequiredDocument {
  documentType: 'ID' | 'PROOF_OF_ADDRESS' | 'TAX_ID' | 'BANK_STATEMENT' | 'INCOME_PROOF' | 'OTHER';
  description: string;
  status: 'PENDING' | 'SUBMITTED' | 'VERIFIED' | 'REJECTED';
  submittedDate?: string;
  verifiedDate?: string;
  rejectionReason?: string;
  expiryDate?: string;
}

export interface ComplianceCheck {
  checkType: 'KYC' | 'AML' | 'OFAC' | 'PEP' | 'SANCTIONS' | 'FRAUD';
  status: 'PASSED' | 'FAILED' | 'PENDING' | 'MANUAL_REVIEW';
  performedDate: string;
  provider?: string;
  details?: string;
  score?: number;
}

export interface ComplianceDocument {
  id: string;
  customerId: string;
  documentType: string;
  fileName: string;
  fileSize: number;
  uploadedDate: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED';
  verifiedBy?: string;
  verifiedDate?: string;
  rejectionReason?: string;
  metadata?: Record<string, any>;
}

export interface TransactionLimit {
  limitType: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'PER_TRANSACTION';
  category: 'TRANSFER' | 'PAYMENT' | 'WITHDRAWAL' | 'INTERNATIONAL';
  currentLimit: number;
  usedAmount: number;
  remainingAmount: number;
  resetDate?: string;
  currency: string;
}

export interface ComplianceAlert {
  id: string;
  alertType: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  title: string;
  description: string;
  createdDate: string;
  acknowledged: boolean;
  acknowledgedDate?: string;
  actions: ComplianceAction[];
}

export interface ComplianceAction {
  actionType: string;
  description: string;
  required: boolean;
  completed: boolean;
  completedDate?: string;
  deadline?: string;
}

export interface SuspiciousActivity {
  id: string;
  transactionId?: string;
  activityType: string;
  description: string;
  riskScore: number;
  detectedDate: string;
  status: 'PENDING_REVIEW' | 'CLEARED' | 'REPORTED' | 'BLOCKED';
  reviewedBy?: string;
  reviewedDate?: string;
  decision?: string;
  reportedToAuthorities: boolean;
}

export interface ComplianceSummary {
  overallStatus: 'COMPLIANT' | 'NEEDS_ACTION' | 'RESTRICTED' | 'BLOCKED';
  pendingActions: number;
  activeAlerts: number;
  documentsPending: number;
  lastReviewDate?: string;
  nextReviewDate?: string;
  riskTrend: 'INCREASING' | 'STABLE' | 'DECREASING';
}

class ComplianceService {
  private getHeaders() {
    const token = getAuthToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    };
  }

  // Compliance Status
  async getComplianceStatus(customerId?: string): Promise<ComplianceStatus> {
    const url = customerId 
      ? `${COMPLIANCE_SERVICE_URL}/status/${customerId}`
      : `${COMPLIANCE_SERVICE_URL}/status`;
    const response = await axios.get(url, { headers: this.getHeaders() });
    return response.data;
  }

  async getComplianceSummary(): Promise<ComplianceSummary> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/summary`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Document Management
  async uploadDocument(file: File, documentType: string, metadata?: Record<string, any>): Promise<ComplianceDocument> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('documentType', documentType);
    if (metadata) {
      formData.append('metadata', JSON.stringify(metadata));
    }

    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/documents`,
      formData,
      {
        headers: {
          ...this.getHeaders(),
          'Content-Type': 'multipart/form-data'
        }
      }
    );
    return response.data;
  }

  async getDocuments(): Promise<ComplianceDocument[]> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/documents`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getDocument(documentId: string): Promise<ComplianceDocument> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/documents/${documentId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async downloadDocument(documentId: string): Promise<Blob> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/documents/${documentId}/download`,
      {
        headers: this.getHeaders(),
        responseType: 'blob'
      }
    );
    return response.data;
  }

  async deleteDocument(documentId: string): Promise<void> {
    await axios.delete(
      `${COMPLIANCE_SERVICE_URL}/documents/${documentId}`,
      { headers: this.getHeaders() }
    );
  }

  // Transaction Limits
  async getTransactionLimits(): Promise<TransactionLimit[]> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/limits`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async checkTransactionLimit(amount: number, transactionType: string): Promise<{
    allowed: boolean;
    reason?: string;
    remainingLimit?: number;
  }> {
    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/limits/check`,
      { amount, transactionType },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Compliance Alerts
  async getAlerts(status?: 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'): Promise<ComplianceAlert[]> {
    const params = status ? { status } : {};
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/alerts`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  async acknowledgeAlert(alertId: string): Promise<void> {
    await axios.put(
      `${COMPLIANCE_SERVICE_URL}/alerts/${alertId}/acknowledge`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async completeAction(alertId: string, actionId: string): Promise<void> {
    await axios.put(
      `${COMPLIANCE_SERVICE_URL}/alerts/${alertId}/actions/${actionId}/complete`,
      {},
      { headers: this.getHeaders() }
    );
  }

  // Suspicious Activity
  async getSuspiciousActivities(): Promise<SuspiciousActivity[]> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/suspicious-activities`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async reportSuspiciousActivity(activity: {
    transactionId?: string;
    activityType: string;
    description: string;
  }): Promise<SuspiciousActivity> {
    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/suspicious-activities`,
      activity,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Compliance Checks
  async initiateKYC(): Promise<{
    checkId: string;
    status: string;
    nextSteps: string[];
  }> {
    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/kyc/initiate`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getKYCStatus(): Promise<{
    status: string;
    completionPercentage: number;
    missingItems: string[];
  }> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/kyc/status`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async performAMLCheck(transactionId: string): Promise<ComplianceCheck> {
    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/aml/check`,
      { transactionId },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Consent Management
  async getConsents(): Promise<{
    consentType: string;
    description: string;
    given: boolean;
    givenDate?: string;
    expiryDate?: string;
  }[]> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/consents`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async updateConsent(consentType: string, given: boolean): Promise<void> {
    await axios.put(
      `${COMPLIANCE_SERVICE_URL}/consents/${consentType}`,
      { given },
      { headers: this.getHeaders() }
    );
  }

  // Data Subject Rights (GDPR)
  async requestDataExport(): Promise<{
    requestId: string;
    status: string;
    estimatedCompletionDate: string;
  }> {
    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/gdpr/export`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async requestDataDeletion(reason: string): Promise<{
    requestId: string;
    status: string;
    reviewRequired: boolean;
  }> {
    const response = await axios.post(
      `${COMPLIANCE_SERVICE_URL}/gdpr/delete`,
      { reason },
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getDataRequests(): Promise<{
    id: string;
    type: 'EXPORT' | 'DELETION' | 'CORRECTION';
    status: string;
    requestedDate: string;
    completedDate?: string;
  }[]> {
    const response = await axios.get(
      `${COMPLIANCE_SERVICE_URL}/gdpr/requests`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }
}

export default new ComplianceService();