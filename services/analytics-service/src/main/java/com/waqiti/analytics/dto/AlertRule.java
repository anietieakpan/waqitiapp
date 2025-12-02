package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Alert rule definition for real-time alerting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "alert_rules")
public class AlertRule {
    
    @Id
    private String ruleId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private String category; // TRANSACTION, PERFORMANCE, SYSTEM, SECURITY, ACCOUNT, etc.
    
    @Column(nullable = false, length = 2000)
    private String condition; // SpEL expression
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Alert.Severity severity;
    
    @Column(name = "time_window_seconds")
    private Integer timeWindow; // Time window in seconds for aggregation
    
    @Column(name = "throttle_minutes")
    private Integer throttleMinutes; // Minimum minutes between alerts
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    // Notification settings
    @ElementCollection
    @CollectionTable(name = "alert_rule_channels")
    private Map<String, String> notificationChannels; // email, sms, push, webhook
    
    @ElementCollection
    @CollectionTable(name = "alert_rule_recipients")
    private Map<String, String> recipients;
    
    // Metadata
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    private Long version;
    
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