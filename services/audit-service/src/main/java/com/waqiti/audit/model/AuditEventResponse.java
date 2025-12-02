package com.waqiti.audit.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Industrial-grade audit event response model for high-performance audit systems.
 * 
 * Features:
 * - Comprehensive correlation and tracing information
 * - Processing metadata and performance metrics
 * - Error handling and validation results
 * - Compliance and regulatory data mapping
 * - Support for real-time analytics and monitoring
 * 
 * This response model is optimized for:
 * - High-volume audit logging (1M+ events/hour)
 * - Financial regulatory compliance (SOX, PCI DSS, GDPR)
 * - Real-time audit analytics and alerting
 * - Comprehensive audit trail reconstruction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEventResponse {

    // Core audit event identification
    @NotNull
    @EqualsAndHashCode.Include
    @JsonProperty("id")
    private String id;

    @NotNull
    @JsonProperty("event_type")
    private String eventType;

    @NotNull
    @JsonProperty("service_name")
    private String serviceName;

    @NotNull
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant timestamp;

    // Business context
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("correlation_id")
    private String correlationId;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("resource_id")
    private String resourceId;

    @JsonProperty("resource_type")
    private String resourceType;

    @NotNull
    @JsonProperty("action")
    private String action;

    @JsonProperty("description")
    private String description;

    @NotNull
    @JsonProperty("result")
    private AuditEvent.AuditResult result;

    // Technical context
    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("user_agent")
    private String userAgent;

    @JsonProperty("duration_ms")
    private Long durationMs;

    // Risk and compliance
    @NotNull
    @JsonProperty("severity")
    private AuditSeverity severity;

    @JsonProperty("compliance_tags")
    private String complianceTags;

    @JsonProperty("risk_score")
    private Integer riskScore;

    @JsonProperty("data_classification")
    private String dataClassification;

    // Processing metadata
    @JsonProperty("processing_metadata")
    private ProcessingMetadata processingMetadata;

    // Integrity and security
    @JsonProperty("integrity_verified")
    private Boolean integrityVerified;

    @JsonProperty("integrity_hash")
    private String integrityHash;

    @JsonProperty("digital_signature_valid")
    private Boolean digitalSignatureValid;

    // Extensible data
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @JsonProperty("tags")
    private Set<String> tags;

    // Error handling
    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("validation_errors")
    private List<ValidationError> validationErrors;

    // Archival and retention
    @JsonProperty("retention_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant retentionDate;

    @JsonProperty("archived")
    private Boolean archived;

    // Geographical and regulatory context
    @JsonProperty("geographical_region")
    private String geographicalRegion;

    @JsonProperty("regulatory_jurisdiction")
    private String regulatoryJurisdiction;

    // Chain integrity
    @JsonProperty("previous_event_hash")
    private String previousEventHash;

    @JsonProperty("chain_position")
    private Long chainPosition;

    // Event versioning
    @JsonProperty("event_version")
    private String eventVersion;

    @JsonProperty("schema_version")
    private String schemaVersion;

    /**
     * Processing metadata containing performance and operational information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessingMetadata {
        
        @JsonProperty("processing_time_ms")
        private Long processingTimeMs;

        @JsonProperty("queue_time_ms")
        private Long queueTimeMs;

        @JsonProperty("persistence_time_ms")
        private Long persistenceTimeMs;

        @JsonProperty("indexing_time_ms")
        private Long indexingTimeMs;

        @JsonProperty("validation_time_ms")
        private Long validationTimeMs;

        @JsonProperty("enrichment_time_ms")
        private Long enrichmentTimeMs;

        @JsonProperty("processor_node_id")
        private String processorNodeId;

        @JsonProperty("processing_thread")
        private String processingThread;

        @JsonProperty("batch_id")
        private String batchId;

        @JsonProperty("batch_size")
        private Integer batchSize;

        @JsonProperty("batch_position")
        private Integer batchPosition;

        @JsonProperty("kafka_partition")
        private Integer kafkaPartition;

        @JsonProperty("kafka_offset")
        private Long kafkaOffset;

        @JsonProperty("retry_count")
        private Integer retryCount;

        @JsonProperty("processing_status")
        private ProcessingStatus processingStatus;

        @JsonProperty("performance_metrics")
        private Map<String, Object> performanceMetrics;

        @JsonProperty("processing_warnings")
        private List<String> processingWarnings;

        @JsonProperty("processing_flags")
        private Set<String> processingFlags;
    }

    /**
     * Processing status enumeration
     */
    public enum ProcessingStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        RETRYING,
        SKIPPED,
        CANCELLED,
        TIMEOUT
    }

    /**
     * Validation error details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationError {
        
        @JsonProperty("field")
        private String field;

        @JsonProperty("rejected_value")
        private Object rejectedValue;

        @JsonProperty("message")
        private String message;

        @JsonProperty("code")
        private String code;

        @JsonProperty("severity")
        private String severity;

        @JsonProperty("constraint")
        private String constraint;
    }

    /**
     * Compliance information
     */
    @JsonProperty("compliance_info")
    private ComplianceInfo complianceInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComplianceInfo {
        
        @JsonProperty("sox_applicable")
        private Boolean soxApplicable;

        @JsonProperty("gdpr_applicable")
        private Boolean gdprApplicable;

        @JsonProperty("pci_dss_applicable")
        private Boolean pciDssApplicable;

        @JsonProperty("ffiec_applicable")
        private Boolean ffiecApplicable;

        @JsonProperty("basel_applicable")
        private Boolean baselApplicable;

        @JsonProperty("compliance_score")
        private Integer complianceScore;

        @JsonProperty("regulatory_alerts")
        private List<RegulatoryAlert> regulatoryAlerts;

        @JsonProperty("retention_policy")
        private String retentionPolicy;

        @JsonProperty("legal_hold")
        private Boolean legalHold;
    }

    /**
     * Regulatory alert information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RegulatoryAlert {
        
        @JsonProperty("framework")
        private String framework;

        @JsonProperty("rule_id")
        private String ruleId;

        @JsonProperty("rule_description")
        private String ruleDescription;

        @JsonProperty("violation_type")
        private String violationType;

        @JsonProperty("severity")
        private String severity;

        @JsonProperty("action_required")
        private String actionRequired;

        @JsonProperty("due_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        private Instant dueDate;
    }

    /**
     * Analytics information for real-time monitoring
     */
    @JsonProperty("analytics_info")
    private AnalyticsInfo analyticsInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnalyticsInfo {
        
        @JsonProperty("event_frequency")
        private EventFrequency eventFrequency;

        @JsonProperty("anomaly_score")
        private Double anomalyScore;

        @JsonProperty("baseline_deviation")
        private Double baselineDeviation;

        @JsonProperty("trend_indicators")
        private Map<String, Object> trendIndicators;

        @JsonProperty("similar_events_count")
        private Long similarEventsCount;

        @JsonProperty("user_behavior_score")
        private Double userBehaviorScore;

        @JsonProperty("fraud_indicators")
        private List<String> fraudIndicators;

        @JsonProperty("pattern_match")
        private List<String> patternMatch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventFrequency {
        
        @JsonProperty("last_hour")
        private Long lastHour;

        @JsonProperty("last_day")
        private Long lastDay;

        @JsonProperty("last_week")
        private Long lastWeek;

        @JsonProperty("last_month")
        private Long lastMonth;
    }

    /**
     * Performance metrics for the audit event
     */
    @JsonProperty("performance_metrics")
    private PerformanceMetrics performanceMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PerformanceMetrics {
        
        @JsonProperty("ingestion_rate_per_second")
        private Double ingestionRatePerSecond;

        @JsonProperty("processing_throughput")
        private Double processingThroughput;

        @JsonProperty("storage_efficiency")
        private Double storageEfficiency;

        @JsonProperty("query_performance_ms")
        private Long queryPerformanceMs;

        @JsonProperty("index_size_bytes")
        private Long indexSizeBytes;

        @JsonProperty("compression_ratio")
        private Double compressionRatio;

        @JsonProperty("replication_lag_ms")
        private Long replicationLagMs;
    }

    // Convenience methods for common operations

    /**
     * Check if the event represents a successful operation
     */
    public boolean isSuccessful() {
        return result == AuditEvent.AuditResult.SUCCESS || 
               result == AuditEvent.AuditResult.PARTIAL_SUCCESS;
    }

    /**
     * Check if the event is critical or higher severity
     */
    public boolean isCritical() {
        return severity != null && 
               (severity == AuditSeverity.CRITICAL || 
                severity == AuditSeverity.REGULATORY || 
                severity == AuditSeverity.FRAUD);
    }

    /**
     * Check if the event has integrity verification
     */
    public boolean hasIntegrityVerification() {
        return integrityVerified != null && integrityVerified && 
               integrityHash != null && !integrityHash.isEmpty();
    }

    /**
     * Check if the event requires regulatory notification
     */
    public boolean requiresRegulatoryNotification() {
        return severity != null && severity.requiresRegulatoryNotification();
    }

    /**
     * Check if the event is subject to legal hold
     */
    public boolean isSubjectToLegalHold() {
        return (complianceInfo != null && Boolean.TRUE.equals(complianceInfo.getLegalHold())) ||
               (metadata != null && Boolean.parseBoolean(metadata.get("legal_hold")));
    }

    /**
     * Get the total processing time including all stages
     */
    public Long getTotalProcessingTime() {
        if (processingMetadata == null) {
            return null;
        }
        
        long total = 0;
        if (processingMetadata.getQueueTimeMs() != null) {
            total += processingMetadata.getQueueTimeMs();
        }
        if (processingMetadata.getProcessingTimeMs() != null) {
            total += processingMetadata.getProcessingTimeMs();
        }
        if (processingMetadata.getPersistenceTimeMs() != null) {
            total += processingMetadata.getPersistenceTimeMs();
        }
        if (processingMetadata.getIndexingTimeMs() != null) {
            total += processingMetadata.getIndexingTimeMs();
        }
        
        return total > 0 ? total : null;
    }

    /**
     * Check if the event has any validation errors
     */
    public boolean hasValidationErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }

    /**
     * Get compliance framework applicability summary
     */
    public String getComplianceFrameworks() {
        if (complianceInfo == null) {
            return complianceTags;
        }

        StringBuilder frameworks = new StringBuilder();
        if (Boolean.TRUE.equals(complianceInfo.getSoxApplicable())) {
            frameworks.append("SOX,");
        }
        if (Boolean.TRUE.equals(complianceInfo.getGdprApplicable())) {
            frameworks.append("GDPR,");
        }
        if (Boolean.TRUE.equals(complianceInfo.getPciDssApplicable())) {
            frameworks.append("PCI_DSS,");
        }
        if (Boolean.TRUE.equals(complianceInfo.getFfiecApplicable())) {
            frameworks.append("FFIEC,");
        }
        if (Boolean.TRUE.equals(complianceInfo.getBaselApplicable())) {
            frameworks.append("BASEL,");
        }

        String result = frameworks.toString();
        return result.isEmpty() ? null : result.substring(0, result.length() - 1);
    }
}