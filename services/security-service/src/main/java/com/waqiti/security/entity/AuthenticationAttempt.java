package com.waqiti.security.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Authentication Attempt Entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "authentication_attempts")
public class AuthenticationAttempt {

    @Id
    private String id;

    private String eventId;
    private String userId;
    private String authMethod;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
    private String geolocation;
    private LocalDateTime timestamp;
    private boolean successful;
    private String mfaCode;
    private String biometricData;
    private JsonNode eventData;
    private String failureReason;
    private Integer attemptCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
