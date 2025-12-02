package com.waqiti.payment.entity;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing a webhook delivery attempt
 */
@Entity
@Table(name = "webhook_delivery_attempts", indexes = {
    @Index(name = "idx_event_id_endpoint", columnList = "event_id,endpoint_url"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_attempted_at", columnList = "attempted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryAttempt {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    @Column(name = "opt_lock_version", nullable = false)
    private Long optLockVersion;
    
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;
    
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;
    
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WebhookDeliveryStatus status;
    
    @Column(name = "response_status")
    private Integer responseStatus;
    
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
    
    @Column(name = "response_headers", columnDefinition = "TEXT")
    private String responseHeaders;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "notes", length = 500)
    private String notes;
    
    @Column(name = "attempted_at", nullable = false)
    @CreationTimestamp
    private Instant attemptedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    
    @Column(name = "response_time_ms")
    private Long responseTimeMs;
}