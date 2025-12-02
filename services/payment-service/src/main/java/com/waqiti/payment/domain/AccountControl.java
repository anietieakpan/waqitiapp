package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Account Control entity for managing account restrictions and controls
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "account_controls")
public class AccountControl {
    @Id
    private String id;
    private String referenceNumber;
    private String userId;
    private String accountId;
    private AccountControlAction action;
    private String reason;
    private String scope; // FULL, PARTIAL, TEMPORARY
    private String severity; // HIGH, MEDIUM, LOW
    private boolean isActive;
    private LocalDateTime appliedAt;
    private LocalDateTime expiresAt;
    private String appliedBy;
    private String authorizedBy;
    private String correlationId;
    private Map<String, Object> metadata;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}