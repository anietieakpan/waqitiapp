package com.waqiti.security.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Security Event Entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "security_events")
public class SecurityEvent {

    @Id
    private String id;

    private String eventId;
    private String eventType;
    private String userId;
    private String resourceId;
    private String action;
    private String severity;
    private String ipAddress;
    private String userAgent;
    private String geolocation;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    private String status;
    private LocalDateTime createdAt;
}
