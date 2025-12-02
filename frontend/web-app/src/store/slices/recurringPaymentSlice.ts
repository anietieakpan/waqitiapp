import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import recurringPaymentService, { 
  RecurringPaymentSchedule, 
  CreateRecurringPaymentRequest,
  UpdateRecurringPaymentRequest,
  RecurringPaymentExecution,
  RecurringPaymentSummary,
  RecurringPaymentTemplate
} from '../../services/recurringPaymentService';

interface RecurringPaymentState {
  schedules: RecurringPaymentSchedule[];
  activeSchedules: RecurringPaymentSchedule[];
  selectedSchedule: RecurringPaymentSchedule | null;
  executions: { [scheduleId: string]: RecurringPaymentExecution[] };
  summary: RecurringPaymentSummary | null;
  templates: RecurringPaymentTemplate[];
  upcomingPayments: RecurringPaymentSchedule[];
  calendar: { [date: string]: RecurringPaymentSchedule[] };
  loading: boolean;
  error: string | null;
  operationStatus: {
    creating: boolean;
    updating: boolean;
    deleting: boolean;
    pausing: boolean;
    resuming: boolean;
  };
}

const initialState: RecurringPaymentState = {
  schedules: [],
  activeSchedules: [],
  selectedSchedule: null,
  executions: {},
  summary: null,
  templates: [],
  upcomingPayments: [],
  calendar: {},
  loading: false,
  error: null,
  operationStatus: {
    creating: false,
    updating: false,
    deleting: false,
    pausing: false,
    resuming: false
  }
};

// Async thunks
export const fetchSchedules = createAsyncThunk(
  'recurringPayment/fetchSchedules',
  async (status?: string) => {
    return await recurringPaymentService.getSchedules(status);
  }
);

export const fetchSchedule = createAsyncThunk(
  'recurringPayment/fetchSchedule',
  async (scheduleId: string) => {
    return await recurringPaymentService.getSchedule(scheduleId);
  }
);

export const createSchedule = createAsyncThunk(
  'recurringPayment/createSchedule',
  async (request: CreateRecurringPaymentRequest) => {
    return await recurringPaymentService.createSchedule(request);
  }
);

export const updateSchedule = createAsyncThunk(
  'recurringPayment/updateSchedule',
  async ({ scheduleId, request }: { scheduleId: string; request: UpdateRecurringPaymentRequest }) => {
    return await recurringPaymentService.updateSchedule(scheduleId, request);
  }
);

export const pauseSchedule = createAsyncThunk(
  'recurringPayment/pauseSchedule',
  async (scheduleId: string) => {
    return await recurringPaymentService.pauseSchedule(scheduleId);
  }
);

export const resumeSchedule = createAsyncThunk(
  'recurringPayment/resumeSchedule',
  async (scheduleId: string) => {
    return await recurringPaymentService.resumeSchedule(scheduleId);
  }
);

export const cancelSchedule = createAsyncThunk(
  'recurringPayment/cancelSchedule',
  async ({ scheduleId, reason }: { scheduleId: string; reason?: string }) => {
    await recurringPaymentService.cancelSchedule(scheduleId, reason);
    return scheduleId;
  }
);

export const fetchExecutionHistory = createAsyncThunk(
  'recurringPayment/fetchExecutionHistory',
  async ({ scheduleId, limit }: { scheduleId: string; limit?: number }) => {
    const executions = await recurringPaymentService.getExecutionHistory(scheduleId, limit);
    return { scheduleId, executions };
  }
);

export const retryExecution = createAsyncThunk(
  'recurringPayment/retryExecution',
  async (executionId: string) => {
    return await recurringPaymentService.retryExecution(executionId);
  }
);

export const fetchSummary = createAsyncThunk(
  'recurringPayment/fetchSummary',
  async () => {
    return await recurringPaymentService.getSummary();
  }
);

export const fetchUpcomingPayments = createAsyncThunk(
  'recurringPayment/fetchUpcomingPayments',
  async (days: number = 30) => {
    return await recurringPaymentService.getUpcomingPayments(days);
  }
);

export const fetchPaymentCalendar = createAsyncThunk(
  'recurringPayment/fetchPaymentCalendar',
  async ({ year, month }: { year: number; month: number }) => {
    return await recurringPaymentService.getPaymentCalendar(year, month);
  }
);

export const fetchTemplates = createAsyncThunk(
  'recurringPayment/fetchTemplates',
  async (category?: string) => {
    return await recurringPaymentService.getTemplates(category);
  }
);

export const createFromTemplate = createAsyncThunk(
  'recurringPayment/createFromTemplate',
  async ({ templateId, customization }: { templateId: string; customization: Partial<CreateRecurringPaymentRequest> }) => {
    return await recurringPaymentService.createFromTemplate(templateId, customization);
  }
);

export const pauseMultipleSchedules = createAsyncThunk(
  'recurringPayment/pauseMultiple',
  async (scheduleIds: string[]) => {
    await recurringPaymentService.pauseMultipleSchedules(scheduleIds);
    return scheduleIds;
  }
);

export const cancelMultipleSchedules = createAsyncThunk(
  'recurringPayment/cancelMultiple',
  async ({ scheduleIds, reason }: { scheduleIds: string[]; reason?: string }) => {
    await recurringPaymentService.cancelMultipleSchedules(scheduleIds, reason);
    return scheduleIds;
  }
);

const recurringPaymentSlice = createSlice({
  name: 'recurringPayment',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    selectSchedule: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedSchedule = state.schedules.find(s => s.id === action.payload) || null;
      } else {
        state.selectedSchedule = null;
      }
    },
    updateScheduleInState: (state, action: PayloadAction<RecurringPaymentSchedule>) => {
      const index = state.schedules.findIndex(s => s.id === action.payload.id);
      if (index !== -1) {
        state.schedules[index] = action.payload;
      }
      if (state.selectedSchedule?.id === action.payload.id) {
        state.selectedSchedule = action.payload;
      }
    }
  },
  extraReducers: (builder) => {
    // Fetch schedules
    builder
      .addCase(fetchSchedules.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchSchedules.fulfilled, (state, action) => {
        state.loading = false;
        state.schedules = action.payload;
        state.activeSchedules = action.payload.filter(s => s.status === 'ACTIVE');
      })
      .addCase(fetchSchedules.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch schedules';
      });

    // Fetch single schedule
    builder
      .addCase(fetchSchedule.fulfilled, (state, action) => {
        state.selectedSchedule = action.payload;
        const index = state.schedules.findIndex(s => s.id === action.payload.id);
        if (index !== -1) {
          state.schedules[index] = action.payload;
        } else {
          state.schedules.push(action.payload);
        }
      });

    // Create schedule
    builder
      .addCase(createSchedule.pending, (state) => {
        state.operationStatus.creating = true;
        state.error = null;
      })
      .addCase(createSchedule.fulfilled, (state, action) => {
        state.operationStatus.creating = false;
        state.schedules.unshift(action.payload);
        if (action.payload.status === 'ACTIVE') {
          state.activeSchedules.unshift(action.payload);
        }
      })
      .addCase(createSchedule.rejected, (state, action) => {
        state.operationStatus.creating = false;
        state.error = action.error.message || 'Failed to create schedule';
      });

    // Update schedule
    builder
      .addCase(updateSchedule.pending, (state) => {
        state.operationStatus.updating = true;
      })
      .addCase(updateSchedule.fulfilled, (state, action) => {
        state.operationStatus.updating = false;
        const index = state.schedules.findIndex(s => s.id === action.payload.id);
        if (index !== -1) {
          state.schedules[index] = action.payload;
        }
        if (state.selectedSchedule?.id === action.payload.id) {
          state.selectedSchedule = action.payload;
        }
      })
      .addCase(updateSchedule.rejected, (state, action) => {
        state.operationStatus.updating = false;
        state.error = action.error.message || 'Failed to update schedule';
      });

    // Pause schedule
    builder
      .addCase(pauseSchedule.pending, (state) => {
        state.operationStatus.pausing = true;
      })
      .addCase(pauseSchedule.fulfilled, (state, action) => {
        state.operationStatus.pausing = false;
        const index = state.schedules.findIndex(s => s.id === action.payload.id);
        if (index !== -1) {
          state.schedules[index] = action.payload;
        }
        state.activeSchedules = state.activeSchedules.filter(s => s.id !== action.payload.id);
      });

    // Resume schedule
    builder
      .addCase(resumeSchedule.pending, (state) => {
        state.operationStatus.resuming = true;
      })
      .addCase(resumeSchedule.fulfilled, (state, action) => {
        state.operationStatus.resuming = false;
        const index = state.schedules.findIndex(s => s.id === action.payload.id);
        if (index !== -1) {
          state.schedules[index] = action.payload;
        }
        if (action.payload.status === 'ACTIVE') {
          state.activeSchedules.push(action.payload);
        }
      });

    // Cancel schedule
    builder
      .addCase(cancelSchedule.pending, (state) => {
        state.operationStatus.deleting = true;
      })
      .addCase(cancelSchedule.fulfilled, (state, action) => {
        state.operationStatus.deleting = false;
        state.schedules = state.schedules.filter(s => s.id !== action.payload);
        state.activeSchedules = state.activeSchedules.filter(s => s.id !== action.payload);
        if (state.selectedSchedule?.id === action.payload) {
          state.selectedSchedule = null;
        }
      });

    // Fetch execution history
    builder
      .addCase(fetchExecutionHistory.fulfilled, (state, action) => {
        state.executions[action.payload.scheduleId] = action.payload.executions;
      });

    // Fetch summary
    builder
      .addCase(fetchSummary.fulfilled, (state, action) => {
        state.summary = action.payload;
      });

    // Fetch upcoming payments
    builder
      .addCase(fetchUpcomingPayments.fulfilled, (state, action) => {
        state.upcomingPayments = action.payload;
      });

    // Fetch payment calendar
    builder
      .addCase(fetchPaymentCalendar.fulfilled, (state, action) => {
        state.calendar = action.payload;
      });

    // Fetch templates
    builder
      .addCase(fetchTemplates.fulfilled, (state, action) => {
        state.templates = action.payload;
      });

    // Create from template
    builder
      .addCase(createFromTemplate.fulfilled, (state, action) => {
        state.schedules.unshift(action.payload);
        if (action.payload.status === 'ACTIVE') {
          state.activeSchedules.unshift(action.payload);
        }
      });

    // Bulk operations
    builder
      .addCase(pauseMultipleSchedules.fulfilled, (state, action) => {
        action.payload.forEach(scheduleId => {
          const index = state.schedules.findIndex(s => s.id === scheduleId);
          if (index !== -1) {
            state.schedules[index].status = 'PAUSED';
          }
        });
        state.activeSchedules = state.schedules.filter(s => s.status === 'ACTIVE');
      })
      .addCase(cancelMultipleSchedules.fulfilled, (state, action) => {
        state.schedules = state.schedules.filter(s => !action.payload.includes(s.id));
        state.activeSchedules = state.activeSchedules.filter(s => !action.payload.includes(s.id));
      });
  }
});

export const { clearError, selectSchedule, updateScheduleInState } = recurringPaymentSlice.actions;
export default recurringPaymentSlice.reducer;