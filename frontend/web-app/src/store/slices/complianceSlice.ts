import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import complianceService, {
  ComplianceStatus,
  ComplianceSummary,
  ComplianceDocument,
  TransactionLimit,
  ComplianceAlert,
  SuspiciousActivity,
  ComplianceCheck
} from '../../services/complianceService';

interface ComplianceState {
  status: ComplianceStatus | null;
  summary: ComplianceSummary | null;
  documents: ComplianceDocument[];
  selectedDocument: ComplianceDocument | null;
  transactionLimits: TransactionLimit[];
  alerts: ComplianceAlert[];
  activeAlerts: ComplianceAlert[];
  suspiciousActivities: SuspiciousActivity[];
  consents: any[];
  dataRequests: any[];
  kycStatus: any | null;
  loading: {
    status: boolean;
    documents: boolean;
    limits: boolean;
    alerts: boolean;
    kyc: boolean;
    gdpr: boolean;
  };
  error: string | null;
  uploadProgress: { [documentId: string]: number };
  limitCheck: {
    checking: boolean;
    lastResult: any | null;
  };
}

const initialState: ComplianceState = {
  status: null,
  summary: null,
  documents: [],
  selectedDocument: null,
  transactionLimits: [],
  alerts: [],
  activeAlerts: [],
  suspiciousActivities: [],
  consents: [],
  dataRequests: [],
  kycStatus: null,
  loading: {
    status: false,
    documents: false,
    limits: false,
    alerts: false,
    kyc: false,
    gdpr: false
  },
  error: null,
  uploadProgress: {},
  limitCheck: {
    checking: false,
    lastResult: null
  }
};

// Async thunks
// Compliance Status
export const fetchComplianceStatus = createAsyncThunk(
  'compliance/fetchStatus',
  async (customerId?: string) => {
    return await complianceService.getComplianceStatus(customerId);
  }
);

export const fetchComplianceSummary = createAsyncThunk(
  'compliance/fetchSummary',
  async () => {
    return await complianceService.getComplianceSummary();
  }
);

// Document Management
export const uploadDocument = createAsyncThunk(
  'compliance/uploadDocument',
  async ({ file, documentType, metadata }: {
    file: File;
    documentType: string;
    metadata?: Record<string, any>;
  }) => {
    return await complianceService.uploadDocument(file, documentType, metadata);
  }
);

export const fetchDocuments = createAsyncThunk(
  'compliance/fetchDocuments',
  async () => {
    return await complianceService.getDocuments();
  }
);

export const fetchDocument = createAsyncThunk(
  'compliance/fetchDocument',
  async (documentId: string) => {
    return await complianceService.getDocument(documentId);
  }
);

export const downloadDocument = createAsyncThunk(
  'compliance/downloadDocument',
  async (documentId: string) => {
    const blob = await complianceService.downloadDocument(documentId);
    // Create download link
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `document-${documentId}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    return documentId;
  }
);

export const deleteDocument = createAsyncThunk(
  'compliance/deleteDocument',
  async (documentId: string) => {
    await complianceService.deleteDocument(documentId);
    return documentId;
  }
);

// Transaction Limits
export const fetchTransactionLimits = createAsyncThunk(
  'compliance/fetchLimits',
  async () => {
    return await complianceService.getTransactionLimits();
  }
);

export const checkTransactionLimit = createAsyncThunk(
  'compliance/checkLimit',
  async ({ amount, transactionType }: { amount: number; transactionType: string }) => {
    return await complianceService.checkTransactionLimit(amount, transactionType);
  }
);

// Alerts
export const fetchAlerts = createAsyncThunk(
  'compliance/fetchAlerts',
  async (status?: 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED') => {
    return await complianceService.getAlerts(status);
  }
);

export const acknowledgeAlert = createAsyncThunk(
  'compliance/acknowledgeAlert',
  async (alertId: string) => {
    await complianceService.acknowledgeAlert(alertId);
    return alertId;
  }
);

export const completeAlertAction = createAsyncThunk(
  'compliance/completeAction',
  async ({ alertId, actionId }: { alertId: string; actionId: string }) => {
    await complianceService.completeAction(alertId, actionId);
    return { alertId, actionId };
  }
);

// Suspicious Activity
export const fetchSuspiciousActivities = createAsyncThunk(
  'compliance/fetchSuspiciousActivities',
  async () => {
    return await complianceService.getSuspiciousActivities();
  }
);

export const reportSuspiciousActivity = createAsyncThunk(
  'compliance/reportSuspiciousActivity',
  async (activity: {
    transactionId?: string;
    activityType: string;
    description: string;
  }) => {
    return await complianceService.reportSuspiciousActivity(activity);
  }
);

// KYC
export const initiateKYC = createAsyncThunk(
  'compliance/initiateKYC',
  async () => {
    return await complianceService.initiateKYC();
  }
);

export const fetchKYCStatus = createAsyncThunk(
  'compliance/fetchKYCStatus',
  async () => {
    return await complianceService.getKYCStatus();
  }
);

export const performAMLCheck = createAsyncThunk(
  'compliance/performAMLCheck',
  async (transactionId: string) => {
    return await complianceService.performAMLCheck(transactionId);
  }
);

// Consent Management
export const fetchConsents = createAsyncThunk(
  'compliance/fetchConsents',
  async () => {
    return await complianceService.getConsents();
  }
);

export const updateConsent = createAsyncThunk(
  'compliance/updateConsent',
  async ({ consentType, given }: { consentType: string; given: boolean }) => {
    await complianceService.updateConsent(consentType, given);
    return { consentType, given };
  }
);

// GDPR
export const requestDataExport = createAsyncThunk(
  'compliance/requestDataExport',
  async () => {
    return await complianceService.requestDataExport();
  }
);

export const requestDataDeletion = createAsyncThunk(
  'compliance/requestDataDeletion',
  async (reason: string) => {
    return await complianceService.requestDataDeletion(reason);
  }
);

export const fetchDataRequests = createAsyncThunk(
  'compliance/fetchDataRequests',
  async () => {
    return await complianceService.getDataRequests();
  }
);

const complianceSlice = createSlice({
  name: 'compliance',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    selectDocument: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedDocument = state.documents.find(d => d.id === action.payload) || null;
      } else {
        state.selectedDocument = null;
      }
    },
    updateUploadProgress: (state, action: PayloadAction<{ documentId: string; progress: number }>) => {
      state.uploadProgress[action.payload.documentId] = action.payload.progress;
    },
    clearLimitCheck: (state) => {
      state.limitCheck.lastResult = null;
    }
  },
  extraReducers: (builder) => {
    // Compliance Status
    builder
      .addCase(fetchComplianceStatus.pending, (state) => {
        state.loading.status = true;
      })
      .addCase(fetchComplianceStatus.fulfilled, (state, action) => {
        state.loading.status = false;
        state.status = action.payload;
      })
      .addCase(fetchComplianceStatus.rejected, (state, action) => {
        state.loading.status = false;
        state.error = action.error.message || 'Failed to fetch compliance status';
      });

    builder
      .addCase(fetchComplianceSummary.fulfilled, (state, action) => {
        state.summary = action.payload;
      });

    // Documents
    builder
      .addCase(uploadDocument.pending, (state) => {
        state.loading.documents = true;
      })
      .addCase(uploadDocument.fulfilled, (state, action) => {
        state.loading.documents = false;
        state.documents.unshift(action.payload);
        delete state.uploadProgress[action.payload.id];
      })
      .addCase(uploadDocument.rejected, (state, action) => {
        state.loading.documents = false;
        state.error = action.error.message || 'Failed to upload document';
      });

    builder
      .addCase(fetchDocuments.pending, (state) => {
        state.loading.documents = true;
      })
      .addCase(fetchDocuments.fulfilled, (state, action) => {
        state.loading.documents = false;
        state.documents = action.payload;
      });

    builder
      .addCase(fetchDocument.fulfilled, (state, action) => {
        state.selectedDocument = action.payload;
        const index = state.documents.findIndex(d => d.id === action.payload.id);
        if (index !== -1) {
          state.documents[index] = action.payload;
        }
      });

    builder
      .addCase(deleteDocument.fulfilled, (state, action) => {
        state.documents = state.documents.filter(d => d.id !== action.payload);
        if (state.selectedDocument?.id === action.payload) {
          state.selectedDocument = null;
        }
      });

    // Transaction Limits
    builder
      .addCase(fetchTransactionLimits.pending, (state) => {
        state.loading.limits = true;
      })
      .addCase(fetchTransactionLimits.fulfilled, (state, action) => {
        state.loading.limits = false;
        state.transactionLimits = action.payload;
      });

    builder
      .addCase(checkTransactionLimit.pending, (state) => {
        state.limitCheck.checking = true;
      })
      .addCase(checkTransactionLimit.fulfilled, (state, action) => {
        state.limitCheck.checking = false;
        state.limitCheck.lastResult = action.payload;
      });

    // Alerts
    builder
      .addCase(fetchAlerts.pending, (state) => {
        state.loading.alerts = true;
      })
      .addCase(fetchAlerts.fulfilled, (state, action) => {
        state.loading.alerts = false;
        state.alerts = action.payload;
        state.activeAlerts = action.payload.filter(a => !a.acknowledged);
      });

    builder
      .addCase(acknowledgeAlert.fulfilled, (state, action) => {
        const index = state.alerts.findIndex(a => a.id === action.payload);
        if (index !== -1) {
          state.alerts[index].acknowledged = true;
          state.alerts[index].acknowledgedDate = new Date().toISOString();
        }
        state.activeAlerts = state.alerts.filter(a => !a.acknowledged);
      });

    builder
      .addCase(completeAlertAction.fulfilled, (state, action) => {
        const alert = state.alerts.find(a => a.id === action.payload.alertId);
        if (alert) {
          const actionIndex = alert.actions.findIndex(a => a.actionType === action.payload.actionId);
          if (actionIndex !== -1) {
            alert.actions[actionIndex].completed = true;
            alert.actions[actionIndex].completedDate = new Date().toISOString();
          }
        }
      });

    // Suspicious Activities
    builder
      .addCase(fetchSuspiciousActivities.fulfilled, (state, action) => {
        state.suspiciousActivities = action.payload;
      });

    builder
      .addCase(reportSuspiciousActivity.fulfilled, (state, action) => {
        state.suspiciousActivities.unshift(action.payload);
      });

    // KYC
    builder
      .addCase(initiateKYC.pending, (state) => {
        state.loading.kyc = true;
      })
      .addCase(initiateKYC.fulfilled, (state, action) => {
        state.loading.kyc = false;
        state.kycStatus = action.payload;
      });

    builder
      .addCase(fetchKYCStatus.fulfilled, (state, action) => {
        state.kycStatus = action.payload;
      });

    // Consents
    builder
      .addCase(fetchConsents.fulfilled, (state, action) => {
        state.consents = action.payload;
      });

    builder
      .addCase(updateConsent.fulfilled, (state, action) => {
        const index = state.consents.findIndex(c => c.consentType === action.payload.consentType);
        if (index !== -1) {
          state.consents[index].given = action.payload.given;
          state.consents[index].givenDate = new Date().toISOString();
        }
      });

    // GDPR
    builder
      .addCase(requestDataExport.pending, (state) => {
        state.loading.gdpr = true;
      })
      .addCase(requestDataExport.fulfilled, (state, action) => {
        state.loading.gdpr = false;
        // Could add the request to dataRequests
      });

    builder
      .addCase(requestDataDeletion.pending, (state) => {
        state.loading.gdpr = true;
      })
      .addCase(requestDataDeletion.fulfilled, (state, action) => {
        state.loading.gdpr = false;
        // Could add the request to dataRequests
      });

    builder
      .addCase(fetchDataRequests.fulfilled, (state, action) => {
        state.dataRequests = action.payload;
      });
  }
});

export const { 
  clearError, 
  selectDocument, 
  updateUploadProgress, 
  clearLimitCheck 
} = complianceSlice.actions;

export default complianceSlice.reducer;