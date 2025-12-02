package com.waqiti.common.messaging.deadletter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dead Letter Record Entity
 * 
 * Database entity for storing dead letter queue messages
 * with comprehensive tracking and audit information.
 */
@Entity
@Table(name = "dead_letter_records",
    indexes = {
        @Index(name = "idx_message_id", columnList = "message_id"),
        @Index(name = "idx_original_topic", columnList = "original_topic"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_poison_message", columnList = "poison_message"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_next_retry_at", columnList = "next_retry_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;
    
    @Column(name = "original_topic", nullable = false)
    private String originalTopic;
    
    @Column(name = "dlq_topic", nullable = false)
    private String dlqTopic;
    
    @Lob
    @Column(name = "message_data", nullable = false)
    private String messageData;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "error_type")
    private String errorType;
    
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    
    @Column(name = "poison_message", nullable = false)
    private boolean poisonMessage;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeadLetterStatus status;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    @Column(name = "reprocessed_at")
    private LocalDateTime reprocessedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
}