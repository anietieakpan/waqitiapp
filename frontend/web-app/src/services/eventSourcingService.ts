import axios from 'axios';
import { getAuthToken } from '../utils/auth';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
const EVENT_SOURCING_SERVICE_URL = `${API_BASE_URL}/api/v1/events`;

export interface Event {
  eventId: string;
  aggregateId: string;
  aggregateType: string;
  eventType: string;
  eventVersion: number;
  eventData: any;
  metadata: EventMetadata;
  timestamp: string;
  sequenceNumber: number;
  causationId?: string;
  correlationId?: string;
}

export interface EventMetadata {
  userId?: string;
  userName?: string;
  ipAddress?: string;
  userAgent?: string;
  source?: string;
  sessionId?: string;
  deviceId?: string;
  additionalData?: Record<string, any>;
}

export interface EventStream {
  streamId: string;
  aggregateId: string;
  aggregateType: string;
  version: number;
  events: Event[];
  snapshot?: AggregateSnapshot;
  createdAt: string;
  updatedAt: string;
}

export interface AggregateSnapshot {
  aggregateId: string;
  aggregateType: string;
  version: number;
  data: any;
  metadata: Record<string, any>;
  createdAt: string;
}

export interface EventQuery {
  aggregateId?: string;
  aggregateType?: string;
  eventType?: string;
  fromTimestamp?: string;
  toTimestamp?: string;
  fromSequence?: number;
  limit?: number;
  offset?: number;
  orderBy?: 'ASC' | 'DESC';
}

export interface EventProjection {
  projectionId: string;
  projectionName: string;
  status: 'RUNNING' | 'STOPPED' | 'FAILED' | 'REBUILDING';
  lastProcessedEvent?: string;
  lastProcessedTimestamp?: string;
  eventsProcessed: number;
  errorCount: number;
  lastError?: string;
  metadata?: Record<string, any>;
}

export interface EventReplay {
  replayId: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  fromTimestamp?: string;
  toTimestamp?: string;
  aggregateTypes?: string[];
  eventTypes?: string[];
  totalEvents: number;
  processedEvents: number;
  progress: number;
  startedAt?: string;
  completedAt?: string;
  error?: string;
}

export interface EventStatistics {
  totalEvents: number;
  uniqueAggregates: number;
  eventsByType: { [eventType: string]: number };
  eventsByAggregate: { [aggregateType: string]: number };
  eventsPerDay: Array<{
    date: string;
    count: number;
  }>;
  averageEventsPerAggregate: number;
  peakEventTime?: string;
}

export interface AuditLog {
  auditId: string;
  entityId: string;
  entityType: string;
  action: string;
  userId: string;
  userName?: string;
  changes: Change[];
  metadata?: Record<string, any>;
  timestamp: string;
  ipAddress?: string;
  userAgent?: string;
}

export interface Change {
  field: string;
  oldValue: any;
  newValue: any;
  changeType: 'CREATE' | 'UPDATE' | 'DELETE';
}

export interface Command {
  commandId: string;
  commandType: string;
  aggregateId: string;
  aggregateType: string;
  payload: any;
  metadata?: Record<string, any>;
  expectedVersion?: number;
}

export interface CommandResult {
  commandId: string;
  success: boolean;
  aggregateVersion: number;
  events: Event[];
  error?: string;
  errorCode?: string;
}

export interface EventSubscription {
  subscriptionId: string;
  subscriberName: string;
  eventTypes: string[];
  aggregateTypes?: string[];
  filter?: string;
  endpoint: string;
  status: 'ACTIVE' | 'PAUSED' | 'FAILED';
  lastDeliveredEvent?: string;
  deliveryCount: number;
  errorCount: number;
  createdAt: string;
}

class EventSourcingService {
  private getHeaders() {
    const token = getAuthToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    };
  }

  // Event Management
  async getEvents(query: EventQuery): Promise<{
    events: Event[];
    total: number;
    hasMore: boolean;
  }> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/events`,
      { headers: this.getHeaders(), params: query }
    );
    return response.data;
  }

  async getEvent(eventId: string): Promise<Event> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/events/${eventId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getEventsByAggregate(aggregateId: string, fromVersion?: number): Promise<Event[]> {
    const params = fromVersion ? { fromVersion } : {};
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/aggregates/${aggregateId}/events`,
      { headers: this.getHeaders(), params }
    );
    return response.data;
  }

  // Event Streams
  async getEventStream(aggregateId: string): Promise<EventStream> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/streams/${aggregateId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async rebuildEventStream(aggregateId: string): Promise<{
    streamId: string;
    status: string;
    message: string;
  }> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/streams/${aggregateId}/rebuild`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Snapshots
  async getSnapshot(aggregateId: string): Promise<AggregateSnapshot | null> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/snapshots/${aggregateId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async createSnapshot(aggregateId: string): Promise<AggregateSnapshot> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/snapshots/${aggregateId}`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Projections
  async getProjections(): Promise<EventProjection[]> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/projections`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getProjection(projectionId: string): Promise<EventProjection> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/projections/${projectionId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async startProjection(projectionId: string): Promise<void> {
    await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/projections/${projectionId}/start`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async stopProjection(projectionId: string): Promise<void> {
    await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/projections/${projectionId}/stop`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async rebuildProjection(projectionId: string): Promise<{
    replayId: string;
    status: string;
  }> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/projections/${projectionId}/rebuild`,
      {},
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Event Replay
  async createEventReplay(config: {
    fromTimestamp?: string;
    toTimestamp?: string;
    aggregateTypes?: string[];
    eventTypes?: string[];
    targetProjections?: string[];
  }): Promise<EventReplay> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/replay`,
      config,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getEventReplay(replayId: string): Promise<EventReplay> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/replay/${replayId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async cancelEventReplay(replayId: string): Promise<void> {
    await axios.delete(
      `${EVENT_SOURCING_SERVICE_URL}/replay/${replayId}`,
      { headers: this.getHeaders() }
    );
  }

  // Statistics
  async getEventStatistics(timeRange: string = '30d'): Promise<EventStatistics> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/statistics`,
      { headers: this.getHeaders(), params: { timeRange } }
    );
    return response.data;
  }

  async getAggregateStatistics(aggregateType: string): Promise<{
    totalAggregates: number;
    totalEvents: number;
    averageEventsPerAggregate: number;
    mostActiveAggregates: Array<{
      aggregateId: string;
      eventCount: number;
    }>;
  }> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/statistics/aggregates/${aggregateType}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Audit Logs
  async getAuditLogs(query: {
    entityId?: string;
    entityType?: string;
    userId?: string;
    action?: string;
    fromDate?: string;
    toDate?: string;
    limit?: number;
    offset?: number;
  }): Promise<{
    logs: AuditLog[];
    total: number;
    hasMore: boolean;
  }> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/audit`,
      { headers: this.getHeaders(), params: query }
    );
    return response.data;
  }

  async getAuditLog(auditId: string): Promise<AuditLog> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/audit/${auditId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Commands
  async sendCommand(command: Command): Promise<CommandResult> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/commands`,
      command,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getCommandStatus(commandId: string): Promise<CommandResult> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/commands/${commandId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  // Event Subscriptions
  async getSubscriptions(): Promise<EventSubscription[]> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/subscriptions`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async createSubscription(subscription: {
    subscriberName: string;
    eventTypes: string[];
    aggregateTypes?: string[];
    filter?: string;
    endpoint: string;
  }): Promise<EventSubscription> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/subscriptions`,
      subscription,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async pauseSubscription(subscriptionId: string): Promise<void> {
    await axios.put(
      `${EVENT_SOURCING_SERVICE_URL}/subscriptions/${subscriptionId}/pause`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async resumeSubscription(subscriptionId: string): Promise<void> {
    await axios.put(
      `${EVENT_SOURCING_SERVICE_URL}/subscriptions/${subscriptionId}/resume`,
      {},
      { headers: this.getHeaders() }
    );
  }

  async deleteSubscription(subscriptionId: string): Promise<void> {
    await axios.delete(
      `${EVENT_SOURCING_SERVICE_URL}/subscriptions/${subscriptionId}`,
      { headers: this.getHeaders() }
    );
  }

  // Event Export
  async exportEvents(config: {
    aggregateTypes?: string[];
    eventTypes?: string[];
    fromTimestamp?: string;
    toTimestamp?: string;
    format: 'json' | 'csv' | 'parquet';
  }): Promise<{
    exportId: string;
    status: string;
    downloadUrl?: string;
  }> {
    const response = await axios.post(
      `${EVENT_SOURCING_SERVICE_URL}/export`,
      config,
      { headers: this.getHeaders() }
    );
    return response.data;
  }

  async getExportStatus(exportId: string): Promise<{
    exportId: string;
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
    progress: number;
    downloadUrl?: string;
    error?: string;
  }> {
    const response = await axios.get(
      `${EVENT_SOURCING_SERVICE_URL}/export/${exportId}`,
      { headers: this.getHeaders() }
    );
    return response.data;
  }
}

export default new EventSourcingService();