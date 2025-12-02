package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit Verification Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditVerificationResponse {
    private UUID eventId;
    private boolean verified;
    private String verificationMethod;
    private String actualHash;
    private String expectedHash;
    private LocalDateTime verifiedAt;
    private String message;
}
