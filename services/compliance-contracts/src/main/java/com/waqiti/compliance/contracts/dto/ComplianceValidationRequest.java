package com.waqiti.compliance.contracts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for compliance validation
 * Shared contract between security-service and compliance-service
 *
 * Purpose: Decouple services by using DTOs instead of direct class imports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceValidationRequest {

    /**
     * Unique identifier for this validation request
     */
    @NotBlank(message = "Validation request ID is required")
    private String requestId;

    /**
     * Type of compliance validation to perform
     */
    @NotNull(message = "Validation type is required")
    private ComplianceValidationType validationType;

    /**
     * Target entity ID being validated (user, transaction, account, etc.)
     */
    @NotBlank(message = "Target entity ID is required")
    private String targetEntityId;

    /**
     * Type of entity being validated
     */
    @NotNull(message = "Entity type is required")
    private EntityType entityType;

    /**
     * Requesting service name (for audit trail)
     */
    @NotBlank(message = "Requesting service is required")
    private String requestingService;

    /**
     * Priority of this validation request
     */
    @NotNull(message = "Priority is required")
    @Builder.Default
    private ValidationPriority priority = ValidationPriority.NORMAL;

    /**
     * Frameworks to validate against (PCI-DSS, SOC2, GDPR, etc.)
     */
    private List<ComplianceFramework> frameworks;

    /**
     * Additional context data for validation
     */
    private Map<String, Object> contextData;

    /**
     * Whether to run validation asynchronously
     */
    @Builder.Default
    private boolean async = true;

    /**
     * Callback URL for async validation results
     */
    private String callbackUrl;

    /**
     * Request timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    /**
     * User ID who initiated the request (for audit)
     */
    private String initiatedBy;

    /**
     * Tags for categorizing and filtering validation requests
     */
    private List<String> tags;

    /**
     * Whether this is a scheduled/automated validation
     */
    @Builder.Default
    private boolean automated = false;

    /**
     * Timeout in seconds for this validation
     */
    @Builder.Default
    private Integer timeoutSeconds = 300; // 5 minutes default
}
