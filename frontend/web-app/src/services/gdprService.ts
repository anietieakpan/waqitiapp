import { apiClient } from '../utils/apiClient';
import {
  ConsentRecord,
  DataSubjectRequest,
  GrantConsentDTO,
  CreateRequestDTO,
  UpdateConsentPreferencesDTO,
  ConsentHistory,
  ConsentForm,
  DataExport,
  PrivacyPolicy,
  DataSubjectRights,
  ProcessingActivity,
  ConsentPurpose,
  RequestType,
  ExportFormat,
} from '../types/gdpr';

const GDPR_BASE_URL = '/api/v1/gdpr';

class GDPRService {
  // Data Subject Request APIs
  
  async createDataRequest(request: CreateRequestDTO): Promise<DataSubjectRequest> {
    const response = await apiClient.post(`${GDPR_BASE_URL}/requests`, request);
    return response.data.data;
  }

  async verifyRequest(requestId: string, token: string): Promise<DataSubjectRequest> {
    const response = await apiClient.post(
      `${GDPR_BASE_URL}/requests/${requestId}/verify`,
      null,
      { params: { token } }
    );
    return response.data.data;
  }

  async getUserRequests(): Promise<DataSubjectRequest[]> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/requests`);
    return response.data.data;
  }

  async getRequest(requestId: string): Promise<DataSubjectRequest> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/requests/${requestId}`);
    return response.data.data;
  }

  async getRequestStatus(requestId: string): Promise<any> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/requests/${requestId}/status`);
    return response.data.data;
  }

  async cancelRequest(requestId: string): Promise<void> {
    await apiClient.delete(`${GDPR_BASE_URL}/requests/${requestId}`);
  }

  // Consent Management APIs

  async grantConsent(consent: GrantConsentDTO): Promise<ConsentRecord> {
    const response = await apiClient.post(`${GDPR_BASE_URL}/consent`, consent);
    return response.data.data;
  }

  async withdrawConsent(purpose: ConsentPurpose, reason?: string): Promise<ConsentRecord> {
    const response = await apiClient.delete(
      `${GDPR_BASE_URL}/consent/${purpose}`,
      { params: { reason } }
    );
    return response.data.data;
  }

  async getUserConsents(activeOnly: boolean = false): Promise<ConsentRecord[]> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/consent`, {
      params: { activeOnly }
    });
    return response.data.data;
  }

  async getConsentStatus(): Promise<Record<ConsentPurpose, boolean>> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/consent/status`);
    return response.data.data;
  }

  async updateConsentPreferences(preferences: UpdateConsentPreferencesDTO): Promise<void> {
    await apiClient.put(`${GDPR_BASE_URL}/consent/preferences`, preferences);
  }

  async getConsentHistory(): Promise<ConsentHistory> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/consent/history`);
    return response.data.data;
  }

  async getConsentForm(purpose: ConsentPurpose, language: string = 'en'): Promise<ConsentForm> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/consent/form/${purpose}`, {
      params: { language }
    });
    return response.data.data;
  }

  // Data Export APIs

  async exportUserData(
    format: ExportFormat = ExportFormat.JSON,
    categories?: string[]
  ): Promise<DataExport> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/export`, {
      params: { format, categories }
    });
    return response.data.data;
  }

  async downloadExport(exportId: string): Promise<Blob> {
    const response = await apiClient.get(
      `${GDPR_BASE_URL}/export/${exportId}/download`,
      { responseType: 'blob' }
    );
    return response.data;
  }

  // Privacy Policy APIs

  async getPrivacyPolicy(language: string = 'en'): Promise<PrivacyPolicy> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/privacy/policy`, {
      params: { language }
    });
    return response.data.data;
  }

  async getDataSubjectRights(language: string = 'en'): Promise<DataSubjectRights> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/privacy/rights`, {
      params: { language }
    });
    return response.data.data;
  }

  // Processing Activities APIs

  async getProcessingActivities(): Promise<ProcessingActivity[]> {
    const response = await apiClient.get(`${GDPR_BASE_URL}/processing/activities`);
    return response.data.data;
  }

  // Utility methods

  async requestDataAccess(categories: string[]): Promise<DataSubjectRequest> {
    return this.createDataRequest({
      requestType: RequestType.ACCESS,
      dataCategories: categories,
      exportFormat: ExportFormat.JSON,
    });
  }

  async requestDataPortability(
    categories: string[],
    format: ExportFormat = ExportFormat.JSON
  ): Promise<DataSubjectRequest> {
    return this.createDataRequest({
      requestType: RequestType.PORTABILITY,
      dataCategories: categories,
      exportFormat: format,
    });
  }

  async requestDataErasure(categories: string[], notes?: string): Promise<DataSubjectRequest> {
    return this.createDataRequest({
      requestType: RequestType.ERASURE,
      dataCategories: categories,
      notes: notes || 'User requested data erasure',
    });
  }

  async requestDataRectification(notes: string): Promise<DataSubjectRequest> {
    return this.createDataRequest({
      requestType: RequestType.RECTIFICATION,
      dataCategories: ['PERSONAL_INFO'],
      notes,
    });
  }

  async requestProcessingRestriction(
    categories: string[],
    notes?: string
  ): Promise<DataSubjectRequest> {
    return this.createDataRequest({
      requestType: RequestType.RESTRICTION,
      dataCategories: categories,
      notes: notes || 'User requested processing restriction',
    });
  }

  async objectToProcessing(categories: string[], notes?: string): Promise<DataSubjectRequest> {
    return this.createDataRequest({
      requestType: RequestType.OBJECTION,
      dataCategories: categories,
      notes: notes || 'User objects to data processing',
    });
  }

  // Batch operations

  async withdrawAllMarketingConsents(): Promise<void> {
    const marketingPurposes = [
      ConsentPurpose.MARKETING_EMAILS,
      ConsentPurpose.PROMOTIONAL_SMS,
      ConsentPurpose.PUSH_NOTIFICATIONS,
      ConsentPurpose.PROFILING,
    ];

    await Promise.all(
      marketingPurposes.map(purpose => 
        this.withdrawConsent(purpose, 'Bulk marketing opt-out')
      )
    );
  }

  async grantEssentialConsents(): Promise<void> {
    const essentialPurposes = [
      ConsentPurpose.ESSENTIAL_SERVICE,
    ];

    await Promise.all(
      essentialPurposes.map(purpose =>
        this.grantConsent({
          purpose,
          collectionMethod: CollectionMethod.IMPLICIT_SIGNUP,
        })
      )
    );
  }

  // Download helpers

  async downloadExportFile(exportId: string, filename?: string): Promise<void> {
    const blob = await this.downloadExport(exportId);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename || `waqiti-data-export-${exportId}.zip`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }
}

export const gdprService = new GDPRService();