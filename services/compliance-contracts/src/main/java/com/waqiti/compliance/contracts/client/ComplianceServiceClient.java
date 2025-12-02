package com.waqiti.compliance.contracts.client;

import com.waqiti.compliance.contracts.dto.*;
import com.waqiti.compliance.contracts.dto.aml.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Feign client interface for Compliance Service
 *
 * This interface defines the contract for compliance service operations.
 * Security-service and other services use this client instead of direct dependencies.
 *
 * Configuration:
 * - Service name: compliance-service (Eureka discovery)
 * - Fallback: ComplianceServiceClientFallback (circuit breaker)
 * - Error decoder: ComplianceServiceErrorDecoder (custom error handling)
 *
 * Usage in security-service:
 * <pre>
 * {@code
 * @Autowired
 * private ComplianceServiceClient complianceClient;
 *
 * ComplianceValidationResponse result = complianceClient.validateCompliance(request);
 * }
 * </pre>
 */
@FeignClient(
    name = "compliance-service",
    path = "/api/v1/compliance",
    fallbackFactory = ComplianceServiceClientFallbackFactory.class
)
public interface ComplianceServiceClient {

    /**
     * Run comprehensive compliance validation
     *
     * @param request validation request with parameters
     * @return validation response with results
     */
    @PostMapping("/validate")
    ResponseEntity<ComplianceValidationResponse> validateCompliance(
        @Valid @RequestBody ComplianceValidationRequest request
    );

    /**
     * Get compliance validation result by ID
     *
     * @param validationId validation identifier
     * @return validation response
     */
    @GetMapping("/validate/{validationId}")
    ResponseEntity<ComplianceValidationResponse> getValidationResult(
        @PathVariable("validationId") String validationId
    );

    /**
     * Check compliance status for an entity
     *
     * @param entityType type of entity (USER, TRANSACTION, etc.)
     * @param entityId entity identifier
     * @return compliance status summary
     */
    @GetMapping("/status/{entityType}/{entityId}")
    ResponseEntity<ComplianceStatusDTO> getComplianceStatus(
        @PathVariable("entityType") String entityType,
        @PathVariable("entityId") String entityId
    );

    /**
     * Create an AML case
     *
     * @param request AML case creation request
     * @return AML case response with case ID
     */
    @PostMapping("/aml/cases")
    ResponseEntity<AMLCaseResponse> createAMLCase(
        @Valid @RequestBody AMLCaseRequest request
    );

    /**
     * Get AML case by ID
     *
     * @param caseId case identifier
     * @return AML case details
     */
    @GetMapping("/aml/cases/{caseId}")
    ResponseEntity<AMLCaseResponse> getAMLCase(
        @PathVariable("caseId") String caseId
    );

    /**
     * Update AML case status
     *
     * @param caseId case identifier
     * @param status new status
     * @param notes update notes
     * @return updated case
     */
    @PutMapping("/aml/cases/{caseId}/status")
    ResponseEntity<AMLCaseResponse> updateAMLCaseStatus(
        @PathVariable("caseId") String caseId,
        @RequestParam("status") AMLCaseStatus status,
        @RequestParam(value = "notes", required = false) String notes
    );

    /**
     * Get AML cases for a subject
     *
     * @param subjectId subject identifier (user, merchant, etc.)
     * @return list of AML cases
     */
    @GetMapping("/aml/cases/subject/{subjectId}")
    ResponseEntity<List<AMLCaseResponse>> getAMLCasesBySubject(
        @PathVariable("subjectId") String subjectId
    );

    /**
     * Generate compliance report
     *
     * @param validationId validation ID to generate report for
     * @param format report format (PDF, JSON, etc.)
     * @return report generation response with download URL
     */
    @PostMapping("/reports/generate")
    ResponseEntity<ComplianceReportDTO> generateComplianceReport(
        @RequestParam("validationId") String validationId,
        @RequestParam(value = "format", defaultValue = "PDF") String format
    );

    /**
     * Get compliance findings by entity
     *
     * @param entityType type of entity
     * @param entityId entity identifier
     * @param severity filter by severity (optional)
     * @param status filter by status (optional)
     * @return list of findings
     */
    @GetMapping("/findings/{entityType}/{entityId}")
    ResponseEntity<List<ComplianceFindingDTO>> getFindings(
        @PathVariable("entityType") String entityType,
        @PathVariable("entityId") String entityId,
        @RequestParam(value = "severity", required = false) FindingSeverity severity,
        @RequestParam(value = "status", required = false) FindingStatus status
    );

    /**
     * Health check for compliance service
     *
     * @return health status
     */
    @GetMapping("/health")
    ResponseEntity<HealthCheckDTO> healthCheck();

    /**
     * Get compliance metrics
     *
     * @param from start date (ISO format)
     * @param to end date (ISO format)
     * @return compliance metrics
     */
    @GetMapping("/metrics")
    ResponseEntity<ComplianceMetricsDTO> getComplianceMetrics(
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to
    );
}
