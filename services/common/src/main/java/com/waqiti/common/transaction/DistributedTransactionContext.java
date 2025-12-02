package com.waqiti.common.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for distributed transactions with 2PC and saga support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributedTransactionContext {
    
    private String transactionId;
    private String correlationId;
    private String initiatorService;
    private LocalDateTime startTime;
    private Long timeoutMs;
    private Map<String, Object> metadata;
    private TransactionIsolationLevel isolationLevel;
    private boolean compensateOnPartialFailure;
    
    // Additional fields for 2PC and saga support
    @Builder.Default
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    @Builder.Default  
    private final Map<String, CompensationHandler> compensationHandlers = new ConcurrentHashMap<>();
    
    public enum TransactionIsolationLevel {
        READ_UNCOMMITTED,
        READ_COMMITTED,
        REPEATABLE_READ,
        SERIALIZABLE
    }
    
    public static DistributedTransactionContext create(String initiatorService) {
        return DistributedTransactionContext.builder()
                .transactionId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .initiatorService(initiatorService)
                .startTime(LocalDateTime.now())
                .timeoutMs(30000L) // 30 seconds default
                .isolationLevel(TransactionIsolationLevel.READ_COMMITTED)
                .compensateOnPartialFailure(true)
                .build();
    }
    
    // Constructor for compatibility with DistributedTransactionManager
    public DistributedTransactionContext(String transactionId, Duration timeout) {
        this.transactionId = transactionId;
        this.timeoutMs = timeout.toMillis();
        this.startTime = LocalDateTime.now();
        this.isolationLevel = TransactionIsolationLevel.READ_COMMITTED;
        this.compensateOnPartialFailure = true;
        this.attributes = new ConcurrentHashMap<>(); // Initialize attributes
        this.compensationHandlers = new ConcurrentHashMap<>(); // Initialize compensationHandlers
    }
    
    /**
     * Get timeout as Duration
     */
    public Duration getTimeout() {
        return Duration.ofMillis(timeoutMs != null ? timeoutMs : 30000L);
    }
    
    /**
     * Add an attribute to the transaction context
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    /**
     * Get an attribute from the transaction context
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Add a compensation handler for a participant
     */
    public void addCompensationHandler(String participantId, CompensationHandler handler) {
        compensationHandlers.put(participantId, handler);
    }
    
    /**
     * Remove a compensation handler
     */
    public CompensationHandler removeCompensationHandler(String participantId) {
        return compensationHandlers.remove(participantId);
    }
    
    /**
     * Check if context has compensation handlers (indicating saga pattern)
     */
    public boolean hasSagaCompensations() {
        return !compensationHandlers.isEmpty();
    }
}