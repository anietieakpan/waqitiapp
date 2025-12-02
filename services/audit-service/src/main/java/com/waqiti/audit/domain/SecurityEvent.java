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
 * Entity for security-specific audit events
 */
@Entity
@Table(name = "security_events", indexes = {
    @Index(name = "idx_security_timestamp", columnList = "timestamp"),
    @Index(name = "idx_security_severity", columnList = "severity"),
    @Index(name = "idx_security_type", columnList = "event_type"),
    @Index(name = "idx_security_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID eventId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType; // LOGIN_ATTEMPT, ACCESS_DENIED, PRIVILEGE_ESCALATION, etc.
    
    @Column(name = "severity", nullable = false)
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW, INFO
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "source_ip", nullable = false)
    private String sourceIp;
    
    @Column(name = "target_resource")
    private String targetResource;
    
    @Column(name = "action_attempted")
    private String actionAttempted;
    
    @Column(name = "outcome")
    private String outcome; // SUCCESS, FAILURE, BLOCKED
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "threat_indicator")
    private String threatIndicator;
    
    @Column(name = "attack_pattern")
    private String attackPattern;
    
    @Column(name = "authentication_method")
    private String authenticationMethod;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;
    
    @Column(name = "geo_location")
    private String geoLocation;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "response_action")
    private String responseAction; // ALERT_SENT, ACCOUNT_LOCKED, IP_BLOCKED
    
    @Column(name = "detection_method")
    private String detectionMethod;
    
    @Column(name = "false_positive")
    private Boolean falsePositive;
    
    @ElementCollection
    @CollectionTable(name = "security_event_metadata", 
                      joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "related_incident_id")
    private String relatedIncidentId;
    
    @Column(name = "mitigation_applied")
    private Boolean mitigationApplied;
    
    @Column(name = "mitigation_details", columnDefinition = "TEXT")
    private String mitigationDetails;
}