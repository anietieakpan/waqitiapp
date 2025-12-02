package com.waqiti.common.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a message that has been moved to a Dead Letter Queue (DLQ).
 * Contains the original message, error details, and metadata for analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dlq_messages", indexes = {
    @Index(name = "idx_dlq_topic", columnList = "originalTopic"),
    @Index(name = "idx_dlq_timestamp", columnList = "failureTimestamp"),
    @Index(name = "idx_dlq_error_type", columnList = "errorType")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class DlqMessage {

    @Id
    @JsonProperty("messageId")
    private String messageId;

    @JsonProperty("originalTopic")
    private String originalTopic;

    @JsonProperty("dlqTopic")
    private String dlqTopic;

    @Column(columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    @JsonProperty("originalMessage")
    private Object originalMessage;

    @Column(length = 1000)
    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("errorType")
    private String errorType;

    @Column(columnDefinition = "TEXT")
    @JsonProperty("stackTrace")
    private String stackTrace;

    @JsonProperty("failureTimestamp")
    private Instant failureTimestamp;

    @JsonProperty("retryCount")
    private Integer retryCount;

    @JsonProperty("maxRetries")
    private Integer maxRetries;

    @JsonProperty("correlationId")
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @JsonProperty("headers")
    private Map<String, Object> headers;

    @JsonProperty("partition")
    private Integer partition;

    @JsonProperty("offset")
    private Long offset;

    @Enumerated(EnumType.STRING)
    @JsonProperty("businessImpact")
    private BusinessImpact businessImpact;

    @JsonProperty("rootCause")
    private String rootCause;

    @Enumerated(EnumType.STRING)
    @JsonProperty("errorCategory")
    private ErrorCategory errorCategory;

    @JsonProperty("requiresManualIntervention")
    private Boolean requiresManualIntervention;

    @Enumerated(EnumType.STRING)
    @JsonProperty("escalationLevel")
    private EscalationLevel escalationLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @JsonProperty("additionalMetadata")
    private Map<String, Object> additionalMetadata;

    // Reprocessing fields
    @JsonProperty("originalKey")
    private String originalKey;

    @Column(columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    @JsonProperty("originalPayload")
    private Object originalPayload;

    @JsonProperty("reprocessingAttempts")
    private Integer reprocessingAttempts;

    @JsonProperty("lastReprocessingAttempt")
    private Instant lastReprocessingAttempt;

    @JsonProperty("lastReprocessedBy")
    private String lastReprocessedBy;

    @JsonProperty("status")
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("reprocessedAt")
    private Instant reprocessedAt;

    @JsonProperty("lastReprocessingError")
    private String lastReprocessingError;

    public enum BusinessImpact {
        CRITICAL,    // Customer-facing, financial impact
        HIGH,        // Internal operations, compliance
        MEDIUM,      // Performance, analytics
        LOW          // Monitoring, logging
    }

    public enum ErrorCategory {
        SERIALIZATION_ERROR,     // Message format issues
        VALIDATION_ERROR,        // Business rule violations
        NETWORK_ERROR,          // Connectivity issues
        DATABASE_ERROR,         // Data persistence issues
        BUSINESS_LOGIC_ERROR,   // Application logic failures
        TIMEOUT_ERROR,          // Processing timeouts
        AUTHENTICATION_ERROR,   // Security failures
        AUTHORIZATION_ERROR,    // Permission issues
        CONFIGURATION_ERROR,    // System configuration issues
        EXTERNAL_SERVICE_ERROR, // Third-party service failures
        UNKNOWN_ERROR          // Unclassified errors
    }

    public enum EscalationLevel {
        NONE,           // No escalation needed
        OPERATIONAL,    // Ops team notification
        ENGINEERING,    // Engineering team involvement
        MANAGEMENT,     // Management escalation
        CRITICAL        // Immediate executive notification
    }

    /**
     * Convenience method to get ID (alias for messageId)
     */
    public String getId() {
        return messageId;
    }

    /**
     * Convenience method to set ID (alias for messageId)
     */
    public void setId(String id) {
        this.messageId = id;
    }
}