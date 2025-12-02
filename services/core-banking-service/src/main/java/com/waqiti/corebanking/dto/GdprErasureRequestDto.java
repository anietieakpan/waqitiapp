package com.waqiti.corebanking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GDPR Right to Erasure Request
 * Used for GDPR Article 17 compliance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GdprErasureRequestDto {

    @NotBlank(message = "Reason for erasure is required for audit trail")
    private String reason;

    private String requestedBy;

    private Boolean confirmZeroBalance;

    private Boolean acknowledgeLegalRetention;
}
