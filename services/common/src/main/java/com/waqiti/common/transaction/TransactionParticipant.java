package com.waqiti.common.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a participant in a distributed transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionParticipant {
    
    private String participantId;
    private String serviceId;
    private String serviceName; // For compatibility with new implementation
    private String transactionId;
    private ParticipantStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime lastUpdateAt;
    private String resourceId;
    private String operationType;
    private Map<String, Object> context;
    private String compensationUrl;
    private String endpoint; // For 2PC communication
    private String resourceManager;
    private ParticipantType type;
    private Integer priority; // For ordering operations
    private Integer retryCount;
    private String errorMessage;
    
    public enum ParticipantType {
        DATABASE,
        MESSAGE_QUEUE,
        EXTERNAL_API,
        FILE_SYSTEM,
        CACHE,
        OTHER
    }
    
    public static TransactionParticipant create(String serviceId, String transactionId, String resourceId) {
        return TransactionParticipant.builder()
                .participantId(java.util.UUID.randomUUID().toString())
                .serviceId(serviceId)
                .serviceName(serviceId) // Default serviceName to serviceId
                .transactionId(transactionId)
                .resourceId(resourceId)
                .status(ParticipantStatus.PENDING)
                .joinedAt(LocalDateTime.now())
                .lastUpdateAt(LocalDateTime.now())
                .type(ParticipantType.OTHER)
                .priority(5)
                .retryCount(0)
                .build();
    }
    
    /**
     * Create a database participant
     */
    public static TransactionParticipant database(String participantId, String serviceName, String endpoint) {
        return TransactionParticipant.builder()
                .participantId(participantId)
                .serviceId(serviceName)
                .serviceName(serviceName)
                .endpoint(endpoint)
                .type(ParticipantType.DATABASE)
                .priority(1) // Database operations typically have high priority
                .status(ParticipantStatus.PENDING)
                .joinedAt(LocalDateTime.now())
                .lastUpdateAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }
    
    /**
     * Create a message queue participant
     */
    public static TransactionParticipant messageQueue(String participantId, String serviceName, String endpoint) {
        return TransactionParticipant.builder()
                .participantId(participantId)
                .serviceId(serviceName)
                .serviceName(serviceName)
                .endpoint(endpoint)
                .type(ParticipantType.MESSAGE_QUEUE)
                .priority(2)
                .status(ParticipantStatus.PENDING)
                .joinedAt(LocalDateTime.now())
                .lastUpdateAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }
    
    /**
     * Create an external API participant
     */
    public static TransactionParticipant externalApi(String participantId, String serviceName, String endpoint) {
        return TransactionParticipant.builder()
                .participantId(participantId)
                .serviceId(serviceName)
                .serviceName(serviceName)
                .endpoint(endpoint)
                .type(ParticipantType.EXTERNAL_API)
                .priority(3) // External APIs may have lower priority
                .status(ParticipantStatus.PENDING)
                .joinedAt(LocalDateTime.now())
                .lastUpdateAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }
    
    public void updateStatus(ParticipantStatus newStatus) {
        this.status = newStatus;
        this.lastUpdateAt = LocalDateTime.now();
    }
    
    public void recordError(String message) {
        this.errorMessage = message;
        this.status = ParticipantStatus.FAILED;
        this.lastUpdateAt = LocalDateTime.now();
    }

    /**
     * Get operation - alias for operationType for compatibility
     */
    public String getOperation() {
        return this.operationType;
    }

    /**
     * Set operation - alias for operationType for compatibility
     */
    public void setOperation(String operation) {
        this.operationType = operation;
    }

    /**
     * Get parameters - alias for context for compatibility
     */
    public Map<String, Object> getParameters() {
        return this.context;
    }

    /**
     * Set parameters - alias for context for compatibility
     */
    public void setParameters(Map<String, Object> parameters) {
        this.context = parameters;
    }
}