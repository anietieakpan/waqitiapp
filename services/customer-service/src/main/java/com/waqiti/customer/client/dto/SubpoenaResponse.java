package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for subpoena information from legal-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubpoenaResponse {

    /**
     * Subpoena identifier
     */
    @NotBlank(message = "Subpoena ID is required")
    private String subpoenaId;

    /**
     * Customer identifier
     */
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    /**
     * Type of subpoena (CIVIL, CRIMINAL, ADMINISTRATIVE, etc.)
     */
    @NotBlank(message = "Subpoena type is required")
    private String subpoenaType;

    /**
     * Current status (RECEIVED, IN_PROGRESS, COMPLETED, etc.)
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Issuing court or authority
     */
    @NotBlank(message = "Issuing authority is required")
    private String issuingAuthority;

    /**
     * Case number
     */
    private String caseNumber;

    /**
     * Case name
     */
    private String caseName;

    /**
     * Date subpoena was received
     */
    @NotNull(message = "Received date is required")
    private LocalDate receivedDate;

    /**
     * Response due date
     */
    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    /**
     * Information requested
     */
    private String informationRequested;

    /**
     * Date response was submitted
     */
    private LocalDate responseSubmittedDate;

    /**
     * Subpoena creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Assigned attorney or handler
     */
    private String assignedTo;
}
