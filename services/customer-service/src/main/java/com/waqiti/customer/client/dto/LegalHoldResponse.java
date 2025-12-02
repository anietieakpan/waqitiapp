package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for legal hold information from legal-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalHoldResponse {

    /**
     * Legal hold identifier
     */
    @NotBlank(message = "Hold ID is required")
    private String holdId;

    /**
     * Customer identifier
     */
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    /**
     * Account identifier (if applicable)
     */
    private String accountId;

    /**
     * Type of legal hold (GARNISHMENT, LEVY, FREEZE, etc.)
     */
    @NotBlank(message = "Hold type is required")
    private String holdType;

    /**
     * Current status (ACTIVE, RELEASED, EXPIRED, etc.)
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Amount on hold
     */
    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /**
     * Currency code
     */
    @NotBlank(message = "Currency is required")
    private String currency;

    /**
     * Issuing authority (court, agency, etc.)
     */
    @NotBlank(message = "Issuing authority is required")
    private String issuingAuthority;

    /**
     * Case number
     */
    private String caseNumber;

    /**
     * Reason for hold
     */
    private String reason;

    /**
     * Hold effective date
     */
    @NotNull(message = "Effective date is required")
    private LocalDateTime effectiveDate;

    /**
     * Hold expiration date (if applicable)
     */
    private LocalDateTime expirationDate;

    /**
     * Hold creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Hold release date (if released)
     */
    private LocalDateTime releasedAt;
}
