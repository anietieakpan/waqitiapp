package com.waqiti.common.events;

import java.time.Instant;
import java.util.Map;

/**
 * Enhanced Domain Event interface for comprehensive event sourcing support.
 * This interface extends the basic event contract with aggregate tracking,
 * versioning, and rich metadata support for enterprise-grade event processing.
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
public interface DomainEvent {
    
    /**
     * Unique identifier for this specific event instance.
     * Used for idempotency, deduplication, and event correlation.
     * 
     * @return UUID string representing the unique event identifier
     */
    String getEventId();
    
    /**
     * Type of the event, typically in format "Domain.Entity.Action".
     * Examples: "Payment.Transaction.Completed", "User.Account.Created"
     * 
     * @return String representation of the event type
     */
    String getEventType();
    
    /**
     * ISO-8601 timestamp when the event occurred in the business domain.
     * This represents the actual business time, not technical processing time.
     * 
     * @return Instant representing when the domain event occurred
     */
    Instant getTimestamp();
    
    /**
     * Kafka topic or message queue destination for this event.
     * Supports dynamic routing based on event type and business rules.
     * 
     * @return Target topic name for event publication
     */
    String getTopic();
    
    /**
     * Identifier of the aggregate root this event belongs to.
     * In DDD terms, this identifies the entity that generated the event.
     * 
     * @return String identifier of the aggregate root (e.g., transaction ID, user ID)
     */
    String getAggregateId();
    
    /**
     * Type of the aggregate root that generated this event.
     * Used for event stream partitioning and aggregate reconstruction.
     * 
     * @return String representing the aggregate type (e.g., "Transaction", "User", "Wallet")
     */
    String getAggregateType();
    
    /**
     * Human-readable name of the aggregate root for logging and monitoring.
     * Provides descriptive context for operational observability and debugging.
     * Distinct from getAggregateType() which may be a technical identifier.
     * 
     * @return String representing the human-readable aggregate name (e.g., "Payment Transaction", "User Account")
     */
    String getAggregateName();
    
    /**
     * Version number of this event within the aggregate's event stream.
     * Supports optimistic concurrency control and event ordering.
     * 
     * @return Long representing the sequential version number
     */
    Long getVersion();
    
    /**
     * Correlation ID for distributed tracing across microservices.
     * Links related events and operations across service boundaries.
     * 
     * @return String correlation identifier for distributed tracing
     */
    String getCorrelationId();
    
    /**
     * User or system that triggered this event.
     * Used for auditing and authorization tracking.
     * 
     * @return String identifier of the event initiator
     */
    String getUserId();
    
    /**
     * Service instance that generated this event.
     * Supports debugging and service mesh routing.
     * 
     * @return String identifier of the source service
     */
    String getSourceService();
    
    /**
     * Optional metadata for extensibility without schema changes.
     * Supports custom attributes, feature flags, and experimental data.
     * 
     * @return Map of additional metadata key-value pairs
     */
    Map<String, Object> getMetadata();
    
    /**
     * Checks if this event should be processed asynchronously.
     * Allows for priority-based event processing strategies.
     * 
     * @return true if the event should be processed asynchronously
     */
    default boolean isAsync() {
        return true;
    }
    
    /**
     * Priority level for event processing (0-10, where 10 is highest).
     * Enables priority queues and SLA-based processing.
     * 
     * @return Integer priority level
     */
    default Integer getPriority() {
        return 5;
    }
    
    /**
     * Time-to-live in seconds for this event.
     * Events older than TTL can be safely discarded or archived.
     * 
     * @return Long TTL in seconds, null for infinite
     */
    default Long getTtlSeconds() {
        return 86400L; // 24 hours default
    }
    
    /**
     * Indicates if this event contains sensitive data requiring encryption.
     * Triggers automatic encryption/masking in logs and persistence.
     * 
     * @return true if the event contains sensitive information
     */
    default boolean containsSensitiveData() {
        return false;
    }
    
    /**
     * Schema version for backward compatibility and migration support.
     * Enables graceful schema evolution and consumer compatibility.
     * 
     * @return String representing the schema version (e.g., "1.0.0")
     */
    default String getSchemaVersion() {
        return "1.0.0";
    }
    
    /**
     * Retry count for failed event processing.
     * Supports exponential backoff and dead letter queue strategies.
     * 
     * @return Integer count of processing attempts
     */
    default Integer getRetryCount() {
        return 0;
    }
    
    /**
     * Maximum retries allowed for this event type.
     * After max retries, event moves to dead letter queue.
     * 
     * @return Integer maximum retry attempts
     */
    default Integer getMaxRetries() {
        return 3;
    }
    
    /**
     * Indicates if this event should be persisted in event store.
     * Some events may be transient and not require long-term storage.
     * 
     * @return true if the event should be persisted
     */
    default boolean isPersistent() {
        return true;
    }
    
    /**
     * Partition key for distributed event streaming.
     * Ensures ordered processing within a partition.
     * 
     * @return String partition key, defaults to aggregate ID
     */
    default String getPartitionKey() {
        return getAggregateId();
    }
    
    /**
     * Validates the event data integrity and business rules.
     * Called before event publication to ensure data quality.
     * 
     * @return true if the event is valid for processing
     */
    default boolean isValid() {
        return getEventId() != null && 
               getEventType() != null && 
               getTimestamp() != null &&
               getAggregateId() != null &&
               getAggregateType() != null;
    }
}