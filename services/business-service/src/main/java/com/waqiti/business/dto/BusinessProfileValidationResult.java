package com.waqiti.business.dto;

import com.waqiti.business.verification.ein.EinVerificationService;
import com.waqiti.business.verification.address.UspsAddressVerificationService;
import com.waqiti.business.verification.sanctions.BusinessSanctionsScreeningService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Business Profile Validation Result DTO
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfileValidationResult {
    private ValidationStatus validationStatus;
    private List<String> validationErrors;
    private List<String> validationWarnings;

    private Boolean einVerified;
    private EinVerificationService.EinVerificationResult einDetails;

    private Boolean addressVerified;
    private UspsAddressVerificationService.AddressVerificationResult addressDetails;

    private Boolean sanctionsCleared;
    private BusinessSanctionsScreeningService.BusinessSanctionsResult sanctionsDetails;

    private LocalDateTime validatedAt;
    private Long validationDurationMs;

    public enum ValidationStatus {
        VALID,
        INVALID,
        BLOCKED,
        PENDING_REVIEW
    }

    public boolean isValid() {
        return validationStatus == ValidationStatus.VALID;
    }

    public boolean isBlocked() {
        return validationStatus == ValidationStatus.BLOCKED;
    }
}
