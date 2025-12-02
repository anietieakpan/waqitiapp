package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event Processing Record
 * Tracks event processing for idempotency and debugging
 */
@Entity
@Table(name = "event_processing_records", indexes = {
    @Index(name = "idx_event_id_type", columnList = "eventId,eventType", unique = true),
    @Index(name = "idx_event_correlation_id", columnList = "correlationId"),
    @Index(name = "idx_event_status", columnList = "status"),
    @Index(name = "idx_event_created_at", columnList = "createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventProcessingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false, length = 255)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventProcessingStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "event_processing_metadata",
                     joinColumns = @JoinColumn(name = "event_record_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, String> metadata;

    @Column(length = 100)
    private String errorType;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @Column
    private LocalDateTime failedAt;

    @Column
    private Integer retryCount = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = EventProcessingStatus.PENDING;
        }
    }
}
