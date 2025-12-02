package com.waqiti.frauddetection.sanctions.dto;

import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord.CheckSource;
import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord.EntityType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for OFAC sanctions screening.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionsScreeningRequest {

    /**
     * User ID (if screening a user)
     */
    private UUID userId;

    /**
     * Entity type being screened
     */
    @NotNull(message = "Entity type is required")
    private EntityType entityType;

    /**
     * Entity ID being screened
     */
    @NotNull(message = "Entity ID is required")
    private UUID entityId;

    /**
     * Full name to screen
     */
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 500, message = "Name must be between 2 and 500 characters")
    private String fullName;

    /**
     * Date of birth (optional, enhances matching accuracy)
     */
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    /**
     * Nationality (ISO 3166-1 alpha-3)
     */
    @Size(min = 3, max = 3, message = "Nationality must be 3-letter ISO code")
    @Pattern(regexp = "[A-Z]{3}", message = "Nationality must be uppercase ISO 3166-1 alpha-3 code")
    private String nationality;

    /**
     * Address
     */
    @Size(max = 1000, message = "Address cannot exceed 1000 characters")
    private String address;

    /**
     * Country (ISO 3166-1 alpha-3)
     */
    @Size(min = 3, max = 3, message = "Country must be 3-letter ISO code")
    @Pattern(regexp = "[A-Z]{3}", message = "Country must be uppercase ISO 3166-1 alpha-3 code")
    private String country;

    /**
     * Identification type
     */
    @Size(max = 50, message = "Identification type cannot exceed 50 characters")
    private String identificationType;

    /**
     * Identification number (will be encrypted)
     */
    @Size(max = 255, message = "Identification number cannot exceed 255 characters")
    private String identificationNumber;

    /**
     * Related transaction ID (if triggered by transaction)
     */
    private UUID relatedTransactionId;

    /**
     * Transaction amount (if applicable)
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "Transaction amount must be positive")
    private BigDecimal transactionAmount;

    /**
     * Transaction currency (ISO 4217)
     */
    @Size(min = 3, max = 3, message = "Currency must be 3-letter ISO code")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be uppercase ISO 4217 code")
    private String transactionCurrency;

    /**
     * Check source
     */
    @NotNull(message = "Check source is required")
    private CheckSource checkSource;

    /**
     * Requested by user ID
     */
    private UUID requestedBy;

    /**
     * Request IP address
     */
    @Size(max = 45, message = "IP address cannot exceed 45 characters")
    private String requestIpAddress;

    /**
     * Request user agent
     */
    @Size(max = 500, message = "User agent cannot exceed 500 characters")
    private String requestUserAgent;

    /**
     * Skip OFAC SDN check (default: false)
     */
    @Builder.Default
    private Boolean skipOfacSdn = false;

    /**
     * Skip EU sanctions check (default: false)
     */
    @Builder.Default
    private Boolean skipEuSanctions = false;

    /**
     * Skip UN sanctions check (default: false)
     */
    @Builder.Default
    private Boolean skipUnSanctions = false;

    /**
     * Include UK sanctions check (default: false)
     */
    @Builder.Default
    private Boolean includeUkSanctions = false;
}
