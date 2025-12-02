package com.waqiti.audit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Audit Verification Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditVerificationRequest {
    @NotNull
    private UUID eventId;

    private String verificationMethod;
    private String expectedHash;
}
