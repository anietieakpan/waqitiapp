package com.waqiti.compliance.contracts.dto.aml;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request to create an AML case
 * Shared contract for AML monitoring and case management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLCaseRequest {

    /**
     * Unique case identifier
     */
    @NotBlank(message = "Case ID is required")
    private String caseId;

    /**
     * Type of AML case
     */
    @NotNull(message = "Case type is required")
    private AMLCaseType caseType;

    /**
     * Case priority
     */
    @NotNull(message = "Priority is required")
    @Builder.Default
    private AMLPriority priority = AMLPriority.MEDIUM;

    /**
     * User/entity under investigation
     */
    @NotBlank(message = "Subject ID is required")
    private String subjectId;

    /**
     * Subject type (USER, MERCHANT, etc.)
     */
    private String subjectType;

    /**
     * Transaction IDs related to this case
     */
    private List<String> transactionIds;

    /**
     * Total amount involved
     */
    private BigDecimal totalAmount;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Suspicious activity indicators
     */
    private List<String> indicators;

    /**
     * Risk score (0-100)
     */
    private Double riskScore;

    /**
     * Alert IDs that triggered this case
     */
    private List<String> alertIds;

    /**
     * Case description
     */
    @NotBlank(message = "Description is required")
    private String description;

    /**
     * Detailed notes
     */
    private String notes;

    /**
     * Supporting documents/evidence
     */
    private List<String> documentIds;

    /**
     * Assigned to (compliance officer)
     */
    private String assignedTo;

    /**
     * Due date for review
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueDate;

    /**
     * Case tags
     */
    private List<String> tags;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Requesting service
     */
    @NotBlank(message = "Requesting service is required")
    private String requestingService;

    /**
     * Created timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Created by (user/system)
     */
    private String createdBy;
}
