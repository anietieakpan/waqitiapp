import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import reportingService, {
  Report,
  ReportTemplate,
  FinancialSummary,
  TaxDocument,
  TaxSummary,
  Analytics,
  ReportParameters
} from '../../services/reportingService';

interface ReportingState {
  // Reports
  reports: Report[];
  activeReports: Report[];
  selectedReport: Report | null;
  
  // Templates
  templates: ReportTemplate[];
  selectedTemplate: ReportTemplate | null;
  scheduledReports: ReportTemplate[];
  
  // Financial Data
  financialSummary: FinancialSummary | null;
  incomeStatement: any | null;
  cashFlow: any | null;
  
  // Tax Documents
  taxDocuments: TaxDocument[];
  taxSummary: { [year: number]: TaxSummary };
  
  // Analytics
  analytics: Analytics | null;
  customAnalytics: any | null;
  dashboardData: any | null;
  
  // Exports
  exports: { [exportId: string]: any };
  activeExports: string[];
  
  // Loading States
  loading: {
    reports: boolean;
    templates: boolean;
    financial: boolean;
    tax: boolean;
    analytics: boolean;
    export: boolean;
  };
  
  // Error State
  error: string | null;
  
  // Generation Status
  generatingReports: string[];
}

const initialState: ReportingState = {
  reports: [],
  activeReports: [],
  selectedReport: null,
  templates: [],
  selectedTemplate: null,
  scheduledReports: [],
  financialSummary: null,
  incomeStatement: null,
  cashFlow: null,
  taxDocuments: [],
  taxSummary: {},
  analytics: null,
  customAnalytics: null,
  dashboardData: null,
  exports: {},
  activeExports: [],
  loading: {
    reports: false,
    templates: false,
    financial: false,
    tax: false,
    analytics: false,
    export: false
  },
  error: null,
  generatingReports: []
};

// Async thunks
// Report Generation
export const generateReport = createAsyncThunk(
  'reporting/generateReport',
  async ({ type, parameters, format }: {
    type: string;
    parameters: ReportParameters;
    format?: string;
  }) => {
    return await reportingService.generateReport(type, parameters, format);
  }
);

export const fetchReports = createAsyncThunk(
  'reporting/fetchReports',
  async (status?: string) => {
    return await reportingService.getReports(status);
  }
);

export const fetchReport = createAsyncThunk(
  'reporting/fetchReport',
  async (reportId: string) => {
    return await reportingService.getReport(reportId);
  }
);

export const downloadReport = createAsyncThunk(
  'reporting/downloadReport',
  async (reportId: string) => {
    const blob = await reportingService.downloadReport(reportId);
    // Create download link
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `report-${reportId}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    return reportId;
  }
);

export const deleteReport = createAsyncThunk(
  'reporting/deleteReport',
  async (reportId: string) => {
    await reportingService.deleteReport(reportId);
    return reportId;
  }
);

// Templates
export const fetchTemplates = createAsyncThunk(
  'reporting/fetchTemplates',
  async (category?: string) => {
    return await reportingService.getTemplates(category);
  }
);

export const createTemplate = createAsyncThunk(
  'reporting/createTemplate',
  async (template: Partial<ReportTemplate>) => {
    return await reportingService.createTemplate(template);
  }
);

export const updateTemplate = createAsyncThunk(
  'reporting/updateTemplate',
  async ({ templateId, updates }: {
    templateId: string;
    updates: Partial<ReportTemplate>;
  }) => {
    return await reportingService.updateTemplate(templateId, updates);
  }
);

export const deleteTemplate = createAsyncThunk(
  'reporting/deleteTemplate',
  async (templateId: string) => {
    await reportingService.deleteTemplate(templateId);
    return templateId;
  }
);

export const generateFromTemplate = createAsyncThunk(
  'reporting/generateFromTemplate',
  async ({ templateId, parameters }: {
    templateId: string;
    parameters?: ReportParameters;
  }) => {
    return await reportingService.generateFromTemplate(templateId, parameters);
  }
);

// Financial Reports
export const fetchFinancialSummary = createAsyncThunk(
  'reporting/fetchFinancialSummary',
  async (period: string = 'MONTHLY') => {
    return await reportingService.getFinancialSummary(period);
  }
);

export const fetchIncomeStatement = createAsyncThunk(
  'reporting/fetchIncomeStatement',
  async ({ startDate, endDate }: { startDate: string; endDate: string }) => {
    return await reportingService.getIncomeStatement(startDate, endDate);
  }
);

export const fetchCashFlow = createAsyncThunk(
  'reporting/fetchCashFlow',
  async ({ startDate, endDate }: { startDate: string; endDate: string }) => {
    return await reportingService.getCashFlow(startDate, endDate);
  }
);

// Tax Documents
export const fetchTaxDocuments = createAsyncThunk(
  'reporting/fetchTaxDocuments',
  async (year?: number) => {
    return await reportingService.getTaxDocuments(year);
  }
);

export const generateTaxDocument = createAsyncThunk(
  'reporting/generateTaxDocument',
  async ({ year, documentType }: { year: number; documentType: string }) => {
    return await reportingService.generateTaxDocument(year, documentType);
  }
);

export const fetchTaxSummary = createAsyncThunk(
  'reporting/fetchTaxSummary',
  async (year: number) => {
    const summary = await reportingService.getTaxSummary(year);
    return { year, summary };
  }
);

// Analytics
export const fetchAnalytics = createAsyncThunk(
  'reporting/fetchAnalytics',
  async ({ period, metrics }: { period?: string; metrics?: string[] }) => {
    return await reportingService.getAnalytics(period, metrics);
  }
);

export const fetchCustomAnalytics = createAsyncThunk(
  'reporting/fetchCustomAnalytics',
  async (query: any) => {
    return await reportingService.getCustomAnalytics(query);
  }
);

// Scheduled Reports
export const fetchScheduledReports = createAsyncThunk(
  'reporting/fetchScheduledReports',
  async () => {
    return await reportingService.getScheduledReports();
  }
);

export const scheduleReport = createAsyncThunk(
  'reporting/scheduleReport',
  async ({ templateId, schedule }: { templateId: string; schedule: any }) => {
    return await reportingService.scheduleReport(templateId, schedule);
  }
);

export const pauseScheduledReport = createAsyncThunk(
  'reporting/pauseScheduledReport',
  async (templateId: string) => {
    await reportingService.pauseScheduledReport(templateId);
    return templateId;
  }
);

export const resumeScheduledReport = createAsyncThunk(
  'reporting/resumeScheduledReport',
  async (templateId: string) => {
    await reportingService.resumeScheduledReport(templateId);
    return templateId;
  }
);

// Export
export const exportData = createAsyncThunk(
  'reporting/exportData',
  async (request: any) => {
    return await reportingService.exportData(request);
  }
);

export const fetchExportStatus = createAsyncThunk(
  'reporting/fetchExportStatus',
  async (exportId: string) => {
    return await reportingService.getExportStatus(exportId);
  }
);

// Dashboard
export const fetchDashboardData = createAsyncThunk(
  'reporting/fetchDashboardData',
  async () => {
    return await reportingService.getDashboardData();
  }
);

const reportingSlice = createSlice({
  name: 'reporting',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    selectReport: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedReport = state.reports.find(r => r.id === action.payload) || null;
      } else {
        state.selectedReport = null;
      }
    },
    selectTemplate: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedTemplate = state.templates.find(t => t.id === action.payload) || null;
      } else {
        state.selectedTemplate = null;
      }
    },
    updateReportStatus: (state, action: PayloadAction<{ reportId: string; status: string }>) => {
      const report = state.reports.find(r => r.id === action.payload.reportId);
      if (report) {
        report.status = action.payload.status as any;
      }
    },
    clearAnalytics: (state) => {
      state.analytics = null;
      state.customAnalytics = null;
    }
  },
  extraReducers: (builder) => {
    // Report Generation
    builder
      .addCase(generateReport.pending, (state, action) => {
        state.loading.reports = true;
        state.generatingReports.push(action.meta.requestId);
      })
      .addCase(generateReport.fulfilled, (state, action) => {
        state.loading.reports = false;
        state.reports.unshift(action.payload);
        state.activeReports.unshift(action.payload);
        state.generatingReports = state.generatingReports.filter(
          id => id !== action.meta.requestId
        );
      })
      .addCase(generateReport.rejected, (state, action) => {
        state.loading.reports = false;
        state.error = action.error.message || 'Failed to generate report';
        state.generatingReports = state.generatingReports.filter(
          id => id !== action.meta.requestId
        );
      });

    builder
      .addCase(fetchReports.pending, (state) => {
        state.loading.reports = true;
      })
      .addCase(fetchReports.fulfilled, (state, action) => {
        state.loading.reports = false;
        state.reports = action.payload;
        state.activeReports = action.payload.filter(
          r => ['PENDING', 'PROCESSING'].includes(r.status)
        );
      });

    builder
      .addCase(fetchReport.fulfilled, (state, action) => {
        const index = state.reports.findIndex(r => r.id === action.payload.id);
        if (index !== -1) {
          state.reports[index] = action.payload;
        }
        if (state.selectedReport?.id === action.payload.id) {
          state.selectedReport = action.payload;
        }
      });

    builder
      .addCase(deleteReport.fulfilled, (state, action) => {
        state.reports = state.reports.filter(r => r.id !== action.payload);
        state.activeReports = state.activeReports.filter(r => r.id !== action.payload);
        if (state.selectedReport?.id === action.payload) {
          state.selectedReport = null;
        }
      });

    // Templates
    builder
      .addCase(fetchTemplates.pending, (state) => {
        state.loading.templates = true;
      })
      .addCase(fetchTemplates.fulfilled, (state, action) => {
        state.loading.templates = false;
        state.templates = action.payload;
      });

    builder
      .addCase(createTemplate.fulfilled, (state, action) => {
        state.templates.push(action.payload);
      });

    builder
      .addCase(updateTemplate.fulfilled, (state, action) => {
        const index = state.templates.findIndex(t => t.id === action.payload.id);
        if (index !== -1) {
          state.templates[index] = action.payload;
        }
        if (state.selectedTemplate?.id === action.payload.id) {
          state.selectedTemplate = action.payload;
        }
      });

    builder
      .addCase(deleteTemplate.fulfilled, (state, action) => {
        state.templates = state.templates.filter(t => t.id !== action.payload);
        if (state.selectedTemplate?.id === action.payload) {
          state.selectedTemplate = null;
        }
      });

    builder
      .addCase(generateFromTemplate.fulfilled, (state, action) => {
        state.reports.unshift(action.payload);
        state.activeReports.unshift(action.payload);
      });

    // Financial Reports
    builder
      .addCase(fetchFinancialSummary.pending, (state) => {
        state.loading.financial = true;
      })
      .addCase(fetchFinancialSummary.fulfilled, (state, action) => {
        state.loading.financial = false;
        state.financialSummary = action.payload;
      });

    builder
      .addCase(fetchIncomeStatement.fulfilled, (state, action) => {
        state.incomeStatement = action.payload;
      });

    builder
      .addCase(fetchCashFlow.fulfilled, (state, action) => {
        state.cashFlow = action.payload;
      });

    // Tax Documents
    builder
      .addCase(fetchTaxDocuments.pending, (state) => {
        state.loading.tax = true;
      })
      .addCase(fetchTaxDocuments.fulfilled, (state, action) => {
        state.loading.tax = false;
        state.taxDocuments = action.payload;
      });

    builder
      .addCase(generateTaxDocument.fulfilled, (state, action) => {
        state.taxDocuments.unshift(action.payload);
      });

    builder
      .addCase(fetchTaxSummary.fulfilled, (state, action) => {
        state.taxSummary[action.payload.year] = action.payload.summary;
      });

    // Analytics
    builder
      .addCase(fetchAnalytics.pending, (state) => {
        state.loading.analytics = true;
      })
      .addCase(fetchAnalytics.fulfilled, (state, action) => {
        state.loading.analytics = false;
        state.analytics = action.payload;
      });

    builder
      .addCase(fetchCustomAnalytics.fulfilled, (state, action) => {
        state.customAnalytics = action.payload;
      });

    // Scheduled Reports
    builder
      .addCase(fetchScheduledReports.fulfilled, (state, action) => {
        state.scheduledReports = action.payload;
      });

    builder
      .addCase(scheduleReport.fulfilled, (state, action) => {
        const index = state.templates.findIndex(t => t.id === action.payload.id);
        if (index !== -1) {
          state.templates[index] = action.payload;
        }
        state.scheduledReports.push(action.payload);
      });

    builder
      .addCase(pauseScheduledReport.fulfilled, (state, action) => {
        const template = state.templates.find(t => t.id === action.payload);
        if (template?.schedule) {
          template.schedule.enabled = false;
        }
      });

    builder
      .addCase(resumeScheduledReport.fulfilled, (state, action) => {
        const template = state.templates.find(t => t.id === action.payload);
        if (template?.schedule) {
          template.schedule.enabled = true;
        }
      });

    // Export
    builder
      .addCase(exportData.pending, (state) => {
        state.loading.export = true;
      })
      .addCase(exportData.fulfilled, (state, action) => {
        state.loading.export = false;
        state.exports[action.payload.exportId] = action.payload;
        state.activeExports.push(action.payload.exportId);
      });

    builder
      .addCase(fetchExportStatus.fulfilled, (state, action) => {
        state.exports[action.payload.exportId] = action.payload;
        if (action.payload.status === 'COMPLETED' || action.payload.status === 'FAILED') {
          state.activeExports = state.activeExports.filter(
            id => id !== action.payload.exportId
          );
        }
      });

    // Dashboard
    builder
      .addCase(fetchDashboardData.fulfilled, (state, action) => {
        state.dashboardData = action.payload;
        state.financialSummary = action.payload.summary;
      });
  }
});

export const { 
  clearError, 
  selectReport, 
  selectTemplate, 
  updateReportStatus,
  clearAnalytics 
} = reportingSlice.actions;

export default reportingSlice.reducer;