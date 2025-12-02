package com.waqiti.common.kafka.dlt;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dead Letter Record Entity
 *
 * Stores failed Kafka messages that could not be processed after all retries.
 * Enables manual investigation and message replay.
 *
 * @author Waqiti Platform Engineering
 */
@Entity
@Table(name = "dead_letter_records", indexes = {
    @Index(name = "idx_dlt_topic_partition_offset",
           columnList = "original_topic, original_partition, original_offset"),
    @Index(name = "idx_dlt_failure_timestamp",
           columnList = "failure_timestamp"),
    @Index(name = "idx_dlt_investigation_status",
           columnList = "investigation_status"),
    @Index(name = "idx_dlt_consumer_group",
           columnList = "consumer_group")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Original Kafka topic name
     */
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    /**
     * Original Kafka partition
     */
    @Column(name = "original_partition", nullable = false)
    private Integer originalPartition;

    /**
     * Original Kafka offset
     */
    @Column(name = "original_offset", nullable = false)
    private Long originalOffset;

    /**
     * Original message key
     */
    @Column(name = "original_key", length = 255)
    private String originalKey;

    /**
     * Original message value (JSON)
     */
    @Column(name = "original_value", nullable = false, columnDefinition = "TEXT")
    private String originalValue;

    /**
     * Original message timestamp
     */
    @Column(name = "original_timestamp", nullable = false)
    private Instant originalTimestamp;

    /**
     * Consumer group that failed to process
     */
    @Column(name = "consumer_group", length = 255)
    private String consumerGroup;

    /**
     * Exception class name that caused failure
     */
    @Column(name = "failure_exception", nullable = false, length = 500)
    private String failureException;

    /**
     * Exception message
     */
    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    /**
     * Full stack trace
     */
    @Column(name = "failure_stack_trace", columnDefinition = "TEXT")
    private String failureStackTrace;

    /**
     * When the failure occurred
     */
    @Column(name = "failure_timestamp", nullable = false)
    private Instant failureTimestamp;

    /**
     * Number of retry attempts before failure
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    /**
     * Investigation status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "investigation_status", nullable = false, length = 50)
    @Builder.Default
    private InvestigationStatus investigationStatus = InvestigationStatus.PENDING;

    /**
     * User who investigated the failure
     */
    @Column(name = "investigated_by", length = 255)
    private String investigatedBy;

    /**
     * When investigation was completed
     */
    @Column(name = "investigated_at")
    private LocalDateTime investigatedAt;

    /**
     * Notes from investigation
     */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * Whether message was successfully replayed
     */
    @Column(name = "replayed", nullable = false)
    @Builder.Default
    private Boolean replayed = false;

    /**
     * When message was replayed
     */
    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    /**
     * Who replayed the message
     */
    @Column(name = "replayed_by", length = 255)
    private String replayedBy;

    /**
     * Record creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Record last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Optimistic locking version
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Investigation status enum
     */
    public enum InvestigationStatus {
        PENDING,           // Not yet investigated
        IN_PROGRESS,       // Currently being investigated
        RESOLVED,          // Issue identified and resolved
        WONT_FIX,          // Determined not worth fixing
        REPLAYED,          // Message successfully replayed
        DUPLICATE          // Duplicate of another failure
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
