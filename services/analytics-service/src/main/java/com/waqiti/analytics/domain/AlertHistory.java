package com.waqiti.analytics.domain;

import com.waqiti.analytics.dto.Alert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for storing alert history and audit trail
 */
@Entity
@Table(name = "alert_history", indexes = {
    @Index(name = "idx_alert_history_timestamp", columnList = "timestamp"),
    @Index(name = "idx_alert_history_user_id", columnList = "userId"),
    @Index(name = "idx_alert_history_type", columnList = "alertType"),
    @Index(name = "idx_alert_history_severity", columnList = "severity"),
    @Index(name = "idx_alert_history_resolved", columnList = "resolved")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AlertHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Alert.AlertType alertType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Alert.Severity severity;
    
    @Column(nullable = false, length = 1000)
    private String message;
    
    @Column
    private UUID entityId;
    
    @Column(length = 100)
    private String entityType;
    
    @Column
    private UUID transactionId;
    
    @Column
    private UUID userId;
    
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private boolean resolved;
    
    @Column
    private LocalDateTime resolvedAt;
    
    @Column(length = 500)
    private String resolvedBy;
    
    @Column(length = 1000)
    private String resolutionNotes;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Business methods
    public void resolve(String resolvedBy, String notes) {
        this.resolved = true;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = notes;
    }
    
    public boolean isCritical() {
        return severity == Alert.Severity.CRITICAL;
    }
    
    public boolean isHighPriority() {
        return severity == Alert.Severity.CRITICAL || severity == Alert.Severity.HIGH;
    }
    
    public long getResolutionTimeMinutes() {
        if (resolvedAt != null && timestamp != null) {
            return java.time.Duration.between(timestamp, resolvedAt).toMinutes();
        }
        return 0;
    }
}