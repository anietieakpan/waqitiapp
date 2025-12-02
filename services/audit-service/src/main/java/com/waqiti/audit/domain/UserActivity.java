package com.waqiti.audit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for tracking user activity patterns and behavior
 */
@Entity
@Table(name = "user_activities", indexes = {
    @Index(name = "idx_activity_user", columnList = "user_id"),
    @Index(name = "idx_activity_timestamp", columnList = "timestamp"),
    @Index(name = "idx_activity_type", columnList = "activity_type"),
    @Index(name = "idx_activity_session", columnList = "session_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID activityId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "activity_type", nullable = false)
    private String activityType;
    
    @Column(name = "activity_description")
    private String activityDescription;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "device_type")
    private String deviceType;
    
    @Column(name = "browser_info")
    private String browserInfo;
    
    @Column(name = "geo_location")
    private String geoLocation;
    
    @Column(name = "page_visited")
    private String pageVisited;
    
    @Column(name = "action_performed")
    private String actionPerformed;
    
    @Column(name = "resource_accessed")
    private String resourceAccessed;
    
    @Column(name = "response_time_ms")
    private Long responseTimeMs;
    
    @Column(name = "success")
    private Boolean success;
    
    @Column(name = "error_code")
    private String errorCode;
    
    @Column(name = "risk_score")
    private Double riskScore;
    
    @Column(name = "anomaly_detected")
    private Boolean anomalyDetected;
    
    @Column(name = "anomaly_type")
    private String anomalyType;
    
    @ElementCollection
    @CollectionTable(name = "activity_metadata", 
                      joinColumns = @JoinColumn(name = "activity_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "previous_activity_id")
    private UUID previousActivityId;
    
    @Column(name = "time_since_last_activity_seconds")
    private Long timeSinceLastActivitySeconds;
    
    @Column(name = "activity_pattern")
    private String activityPattern;
    
    @Column(name = "business_context")
    private String businessContext;
}