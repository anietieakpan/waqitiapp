import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import eventSourcingService, {
  Event,
  EventStream,
  EventProjection,
  EventReplay,
  EventStatistics,
  AuditLog,
  CommandResult,
  EventSubscription,
  EventQuery
} from '../../services/eventSourcingService';

interface EventSourcingState {
  // Events
  events: Event[];
  eventStreams: { [aggregateId: string]: EventStream };
  selectedEvent: Event | null;
  
  // Projections
  projections: EventProjection[];
  selectedProjection: EventProjection | null;
  
  // Replays
  replays: { [replayId: string]: EventReplay };
  activeReplays: string[];
  
  // Statistics
  statistics: EventStatistics | null;
  aggregateStatistics: { [aggregateType: string]: any };
  
  // Audit Logs
  auditLogs: AuditLog[];
  selectedAuditLog: AuditLog | null;
  
  // Commands
  commandResults: { [commandId: string]: CommandResult };
  pendingCommands: string[];
  
  // Subscriptions
  subscriptions: EventSubscription[];
  
  // Exports
  exports: { [exportId: string]: any };
  activeExports: string[];
  
  // Pagination
  pagination: {
    events: {
      total: number;
      hasMore: boolean;
      currentPage: number;
    };
    auditLogs: {
      total: number;
      hasMore: boolean;
      currentPage: number;
    };
  };
  
  // Loading States
  loading: {
    events: boolean;
    projections: boolean;
    replays: boolean;
    statistics: boolean;
    audit: boolean;
    commands: boolean;
    subscriptions: boolean;
  };
  
  // Error State
  error: string | null;
}

const initialState: EventSourcingState = {
  events: [],
  eventStreams: {},
  selectedEvent: null,
  projections: [],
  selectedProjection: null,
  replays: {},
  activeReplays: [],
  statistics: null,
  aggregateStatistics: {},
  auditLogs: [],
  selectedAuditLog: null,
  commandResults: {},
  pendingCommands: [],
  subscriptions: [],
  exports: {},
  activeExports: [],
  pagination: {
    events: {
      total: 0,
      hasMore: false,
      currentPage: 0
    },
    auditLogs: {
      total: 0,
      hasMore: false,
      currentPage: 0
    }
  },
  loading: {
    events: false,
    projections: false,
    replays: false,
    statistics: false,
    audit: false,
    commands: false,
    subscriptions: false
  },
  error: null
};

// Async thunks
// Events
export const fetchEvents = createAsyncThunk(
  'eventSourcing/fetchEvents',
  async (query: EventQuery) => {
    return await eventSourcingService.getEvents(query);
  }
);

export const fetchEvent = createAsyncThunk(
  'eventSourcing/fetchEvent',
  async (eventId: string) => {
    return await eventSourcingService.getEvent(eventId);
  }
);

export const fetchEventsByAggregate = createAsyncThunk(
  'eventSourcing/fetchEventsByAggregate',
  async ({ aggregateId, fromVersion }: { aggregateId: string; fromVersion?: number }) => {
    return await eventSourcingService.getEventsByAggregate(aggregateId, fromVersion);
  }
);

// Event Streams
export const fetchEventStream = createAsyncThunk(
  'eventSourcing/fetchEventStream',
  async (aggregateId: string) => {
    return await eventSourcingService.getEventStream(aggregateId);
  }
);

export const rebuildEventStream = createAsyncThunk(
  'eventSourcing/rebuildEventStream',
  async (aggregateId: string) => {
    return await eventSourcingService.rebuildEventStream(aggregateId);
  }
);

// Projections
export const fetchProjections = createAsyncThunk(
  'eventSourcing/fetchProjections',
  async () => {
    return await eventSourcingService.getProjections();
  }
);

export const fetchProjection = createAsyncThunk(
  'eventSourcing/fetchProjection',
  async (projectionId: string) => {
    return await eventSourcingService.getProjection(projectionId);
  }
);

export const startProjection = createAsyncThunk(
  'eventSourcing/startProjection',
  async (projectionId: string) => {
    await eventSourcingService.startProjection(projectionId);
    return projectionId;
  }
);

export const stopProjection = createAsyncThunk(
  'eventSourcing/stopProjection',
  async (projectionId: string) => {
    await eventSourcingService.stopProjection(projectionId);
    return projectionId;
  }
);

export const rebuildProjection = createAsyncThunk(
  'eventSourcing/rebuildProjection',
  async (projectionId: string) => {
    return await eventSourcingService.rebuildProjection(projectionId);
  }
);

// Event Replay
export const createEventReplay = createAsyncThunk(
  'eventSourcing/createEventReplay',
  async (config: any) => {
    return await eventSourcingService.createEventReplay(config);
  }
);

export const fetchEventReplay = createAsyncThunk(
  'eventSourcing/fetchEventReplay',
  async (replayId: string) => {
    return await eventSourcingService.getEventReplay(replayId);
  }
);

export const cancelEventReplay = createAsyncThunk(
  'eventSourcing/cancelEventReplay',
  async (replayId: string) => {
    await eventSourcingService.cancelEventReplay(replayId);
    return replayId;
  }
);

// Statistics
export const fetchEventStatistics = createAsyncThunk(
  'eventSourcing/fetchEventStatistics',
  async (timeRange: string = '30d') => {
    return await eventSourcingService.getEventStatistics(timeRange);
  }
);

export const fetchAggregateStatistics = createAsyncThunk(
  'eventSourcing/fetchAggregateStatistics',
  async (aggregateType: string) => {
    const stats = await eventSourcingService.getAggregateStatistics(aggregateType);
    return { aggregateType, stats };
  }
);

// Audit Logs
export const fetchAuditLogs = createAsyncThunk(
  'eventSourcing/fetchAuditLogs',
  async (query: any) => {
    return await eventSourcingService.getAuditLogs(query);
  }
);

export const fetchAuditLog = createAsyncThunk(
  'eventSourcing/fetchAuditLog',
  async (auditId: string) => {
    return await eventSourcingService.getAuditLog(auditId);
  }
);

// Commands
export const sendCommand = createAsyncThunk(
  'eventSourcing/sendCommand',
  async (command: any) => {
    return await eventSourcingService.sendCommand(command);
  }
);

export const fetchCommandStatus = createAsyncThunk(
  'eventSourcing/fetchCommandStatus',
  async (commandId: string) => {
    return await eventSourcingService.getCommandStatus(commandId);
  }
);

// Subscriptions
export const fetchSubscriptions = createAsyncThunk(
  'eventSourcing/fetchSubscriptions',
  async () => {
    return await eventSourcingService.getSubscriptions();
  }
);

export const createSubscription = createAsyncThunk(
  'eventSourcing/createSubscription',
  async (subscription: any) => {
    return await eventSourcingService.createSubscription(subscription);
  }
);

export const pauseSubscription = createAsyncThunk(
  'eventSourcing/pauseSubscription',
  async (subscriptionId: string) => {
    await eventSourcingService.pauseSubscription(subscriptionId);
    return subscriptionId;
  }
);

export const resumeSubscription = createAsyncThunk(
  'eventSourcing/resumeSubscription',
  async (subscriptionId: string) => {
    await eventSourcingService.resumeSubscription(subscriptionId);
    return subscriptionId;
  }
);

export const deleteSubscription = createAsyncThunk(
  'eventSourcing/deleteSubscription',
  async (subscriptionId: string) => {
    await eventSourcingService.deleteSubscription(subscriptionId);
    return subscriptionId;
  }
);

// Export
export const exportEvents = createAsyncThunk(
  'eventSourcing/exportEvents',
  async (config: any) => {
    return await eventSourcingService.exportEvents(config);
  }
);

export const fetchExportStatus = createAsyncThunk(
  'eventSourcing/fetchExportStatus',
  async (exportId: string) => {
    return await eventSourcingService.getExportStatus(exportId);
  }
);

const eventSourcingSlice = createSlice({
  name: 'eventSourcing',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    selectEvent: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedEvent = state.events.find(e => e.eventId === action.payload) || null;
      } else {
        state.selectedEvent = null;
      }
    },
    selectProjection: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedProjection = state.projections.find(p => p.projectionId === action.payload) || null;
      } else {
        state.selectedProjection = null;
      }
    },
    selectAuditLog: (state, action: PayloadAction<string | null>) => {
      if (action.payload) {
        state.selectedAuditLog = state.auditLogs.find(l => l.auditId === action.payload) || null;
      } else {
        state.selectedAuditLog = null;
      }
    },
    clearEvents: (state) => {
      state.events = [];
      state.pagination.events = {
        total: 0,
        hasMore: false,
        currentPage: 0
      };
    }
  },
  extraReducers: (builder) => {
    // Events
    builder
      .addCase(fetchEvents.pending, (state) => {
        state.loading.events = true;
      })
      .addCase(fetchEvents.fulfilled, (state, action) => {
        state.loading.events = false;
        state.events = action.payload.events;
        state.pagination.events = {
          total: action.payload.total,
          hasMore: action.payload.hasMore,
          currentPage: state.pagination.events.currentPage + 1
        };
      })
      .addCase(fetchEvents.rejected, (state, action) => {
        state.loading.events = false;
        state.error = action.error.message || 'Failed to fetch events';
      });

    builder
      .addCase(fetchEvent.fulfilled, (state, action) => {
        state.selectedEvent = action.payload;
        const index = state.events.findIndex(e => e.eventId === action.payload.eventId);
        if (index === -1) {
          state.events.push(action.payload);
        } else {
          state.events[index] = action.payload;
        }
      });

    // Event Streams
    builder
      .addCase(fetchEventStream.fulfilled, (state, action) => {
        state.eventStreams[action.payload.aggregateId] = action.payload;
      });

    // Projections
    builder
      .addCase(fetchProjections.pending, (state) => {
        state.loading.projections = true;
      })
      .addCase(fetchProjections.fulfilled, (state, action) => {
        state.loading.projections = false;
        state.projections = action.payload;
      })
      .addCase(fetchProjections.rejected, (state, action) => {
        state.loading.projections = false;
        state.error = action.error.message || 'Failed to fetch projections';
      });

    builder
      .addCase(fetchProjection.fulfilled, (state, action) => {
        state.selectedProjection = action.payload;
        const index = state.projections.findIndex(p => p.projectionId === action.payload.projectionId);
        if (index !== -1) {
          state.projections[index] = action.payload;
        }
      });

    builder
      .addCase(startProjection.fulfilled, (state, action) => {
        const index = state.projections.findIndex(p => p.projectionId === action.payload);
        if (index !== -1) {
          state.projections[index].status = 'RUNNING';
        }
      });

    builder
      .addCase(stopProjection.fulfilled, (state, action) => {
        const index = state.projections.findIndex(p => p.projectionId === action.payload);
        if (index !== -1) {
          state.projections[index].status = 'STOPPED';
        }
      });

    // Event Replay
    builder
      .addCase(createEventReplay.pending, (state) => {
        state.loading.replays = true;
      })
      .addCase(createEventReplay.fulfilled, (state, action) => {
        state.loading.replays = false;
        state.replays[action.payload.replayId] = action.payload;
        state.activeReplays.push(action.payload.replayId);
      });

    builder
      .addCase(fetchEventReplay.fulfilled, (state, action) => {
        state.replays[action.payload.replayId] = action.payload;
        if (action.payload.status === 'COMPLETED' || action.payload.status === 'FAILED') {
          state.activeReplays = state.activeReplays.filter(id => id !== action.payload.replayId);
        }
      });

    builder
      .addCase(cancelEventReplay.fulfilled, (state, action) => {
        delete state.replays[action.payload];
        state.activeReplays = state.activeReplays.filter(id => id !== action.payload);
      });

    // Statistics
    builder
      .addCase(fetchEventStatistics.pending, (state) => {
        state.loading.statistics = true;
      })
      .addCase(fetchEventStatistics.fulfilled, (state, action) => {
        state.loading.statistics = false;
        state.statistics = action.payload;
      })
      .addCase(fetchEventStatistics.rejected, (state, action) => {
        state.loading.statistics = false;
        state.error = action.error.message || 'Failed to fetch statistics';
      });

    builder
      .addCase(fetchAggregateStatistics.fulfilled, (state, action) => {
        state.aggregateStatistics[action.payload.aggregateType] = action.payload.stats;
      });

    // Audit Logs
    builder
      .addCase(fetchAuditLogs.pending, (state) => {
        state.loading.audit = true;
      })
      .addCase(fetchAuditLogs.fulfilled, (state, action) => {
        state.loading.audit = false;
        state.auditLogs = action.payload.logs;
        state.pagination.auditLogs = {
          total: action.payload.total,
          hasMore: action.payload.hasMore,
          currentPage: state.pagination.auditLogs.currentPage + 1
        };
      })
      .addCase(fetchAuditLogs.rejected, (state, action) => {
        state.loading.audit = false;
        state.error = action.error.message || 'Failed to fetch audit logs';
      });

    builder
      .addCase(fetchAuditLog.fulfilled, (state, action) => {
        state.selectedAuditLog = action.payload;
      });

    // Commands
    builder
      .addCase(sendCommand.pending, (state, action) => {
        state.loading.commands = true;
        state.pendingCommands.push(action.meta.arg.commandId);
      })
      .addCase(sendCommand.fulfilled, (state, action) => {
        state.loading.commands = false;
        state.commandResults[action.payload.commandId] = action.payload;
        state.pendingCommands = state.pendingCommands.filter(id => id !== action.payload.commandId);
      })
      .addCase(sendCommand.rejected, (state, action) => {
        state.loading.commands = false;
        state.error = action.error.message || 'Failed to send command';
      });

    builder
      .addCase(fetchCommandStatus.fulfilled, (state, action) => {
        state.commandResults[action.payload.commandId] = action.payload;
      });

    // Subscriptions
    builder
      .addCase(fetchSubscriptions.pending, (state) => {
        state.loading.subscriptions = true;
      })
      .addCase(fetchSubscriptions.fulfilled, (state, action) => {
        state.loading.subscriptions = false;
        state.subscriptions = action.payload;
      });

    builder
      .addCase(createSubscription.fulfilled, (state, action) => {
        state.subscriptions.push(action.payload);
      });

    builder
      .addCase(pauseSubscription.fulfilled, (state, action) => {
        const index = state.subscriptions.findIndex(s => s.subscriptionId === action.payload);
        if (index !== -1) {
          state.subscriptions[index].status = 'PAUSED';
        }
      });

    builder
      .addCase(resumeSubscription.fulfilled, (state, action) => {
        const index = state.subscriptions.findIndex(s => s.subscriptionId === action.payload);
        if (index !== -1) {
          state.subscriptions[index].status = 'ACTIVE';
        }
      });

    builder
      .addCase(deleteSubscription.fulfilled, (state, action) => {
        state.subscriptions = state.subscriptions.filter(s => s.subscriptionId !== action.payload);
      });

    // Export
    builder
      .addCase(exportEvents.fulfilled, (state, action) => {
        state.exports[action.payload.exportId] = action.payload;
        state.activeExports.push(action.payload.exportId);
      });

    builder
      .addCase(fetchExportStatus.fulfilled, (state, action) => {
        state.exports[action.payload.exportId] = action.payload;
        if (action.payload.status === 'COMPLETED' || action.payload.status === 'FAILED') {
          state.activeExports = state.activeExports.filter(id => id !== action.payload.exportId);
        }
      });
  }
});

export const { 
  clearError, 
  selectEvent, 
  selectProjection, 
  selectAuditLog,
  clearEvents 
} = eventSourcingSlice.actions;

export default eventSourcingSlice.reducer;