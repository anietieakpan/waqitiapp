package com.waqiti.compliance.contracts.client;

import com.waqiti.compliance.contracts.dto.*;
import com.waqiti.compliance.contracts.dto.aml.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Fallback factory for ComplianceServiceClient
 *
 * Provides graceful degradation when compliance-service is unavailable.
 * This implements the Circuit Breaker pattern using Spring Cloud CircuitBreaker.
 *
 * Behavior:
 * - Logs the failure cause
 * - Returns degraded responses instead of throwing exceptions
 * - Allows security-service to continue operating with reduced functionality
 *
 * Production Considerations:
 * - Fallback responses indicate service degradation
 * - Alerts should be triggered on repeated fallback invocations
 * - Monitor circuit breaker state (OPEN, HALF_OPEN, CLOSED)
 */
@Slf4j
@Component
public class ComplianceServiceClientFallbackFactory implements FallbackFactory<ComplianceServiceClient> {

    @Override
    public ComplianceServiceClient create(Throwable cause) {
        return new ComplianceServiceClient() {

            @Override
            public ResponseEntity<ComplianceValidationResponse> validateCompliance(
                ComplianceValidationRequest request
            ) {
                log.error("Compliance validation fallback triggered for request: {}. Cause: {}",
                    request.getRequestId(), cause.getMessage(), cause);

                ComplianceValidationResponse fallbackResponse = ComplianceValidationResponse.builder()
                    .validationId("FALLBACK-" + request.getRequestId())
                    .requestId(request.getRequestId())
                    .overallStatus(ComplianceStatus.ERROR)
                    .complianceScore(0.0)
                    .checkResults(Collections.emptyList())
                    .findings(Collections.emptyList())
                    .startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .durationMs(0L)
                    .passed(false)
                    .criticalIssues(List.of("Compliance service unavailable - circuit breaker activated"))
                    .recommendations(List.of(
                        "Retry validation after service recovery",
                        "Check compliance-service health status",
                        "Review fallback logs for root cause"
                    ))
                    .performedBy("CIRCUIT_BREAKER_FALLBACK")
                    .errorMessage("Compliance service is currently unavailable: " + cause.getMessage())
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackResponse);
            }

            @Override
            public ResponseEntity<ComplianceValidationResponse> getValidationResult(String validationId) {
                log.error("Get validation result fallback triggered for ID: {}. Cause: {}",
                    validationId, cause.getMessage());

                ComplianceValidationResponse fallbackResponse = ComplianceValidationResponse.builder()
                    .validationId(validationId)
                    .overallStatus(ComplianceStatus.UNKNOWN)
                    .errorMessage("Unable to retrieve validation result - compliance service unavailable")
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackResponse);
            }

            @Override
            public ResponseEntity<ComplianceStatusDTO> getComplianceStatus(
                String entityType,
                String entityId
            ) {
                log.error("Get compliance status fallback triggered for {}/{}. Cause: {}",
                    entityType, entityId, cause.getMessage());

                ComplianceStatusDTO fallbackStatus = ComplianceStatusDTO.builder()
                    .entityId(entityId)
                    .entityType(entityType)
                    .status(ComplianceStatus.UNKNOWN)
                    .score(0.0)
                    .compliant(false)
                    .message("Compliance status unavailable - service degraded")
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackStatus);
            }

            @Override
            public ResponseEntity<AMLCaseResponse> createAMLCase(AMLCaseRequest request) {
                log.error("Create AML case fallback triggered for case: {}. Cause: {}",
                    request.getCaseId(), cause.getMessage(), cause);

                // For AML cases, we should queue them for later processing
                // rather than losing the data
                AMLCaseResponse fallbackResponse = AMLCaseResponse.builder()
                    .caseId(request.getCaseId())
                    .caseNumber("QUEUED-" + request.getCaseId())
                    .status(AMLCaseStatus.NEW)
                    .caseType(request.getCaseType())
                    .priority(request.getPriority())
                    .subjectId(request.getSubjectId())
                    .createdAt(LocalDateTime.now())
                    .success(false)
                    .message("AML case queued for processing - compliance service unavailable")
                    .errorDetails("Case will be created once compliance service recovers: " + cause.getMessage())
                    .build();

                // TODO: Publish to dead letter queue or retry queue
                log.warn("AML case {} should be queued for retry processing", request.getCaseId());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackResponse);
            }

            @Override
            public ResponseEntity<AMLCaseResponse> getAMLCase(String caseId) {
                log.error("Get AML case fallback triggered for case: {}. Cause: {}",
                    caseId, cause.getMessage());

                AMLCaseResponse fallbackResponse = AMLCaseResponse.builder()
                    .caseId(caseId)
                    .status(AMLCaseStatus.NEW)
                    .success(false)
                    .message("Unable to retrieve AML case - compliance service unavailable")
                    .errorDetails(cause.getMessage())
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackResponse);
            }

            @Override
            public ResponseEntity<AMLCaseResponse> updateAMLCaseStatus(
                String caseId,
                AMLCaseStatus status,
                String notes
            ) {
                log.error("Update AML case status fallback triggered for case: {}. Cause: {}",
                    caseId, cause.getMessage());

                AMLCaseResponse fallbackResponse = AMLCaseResponse.builder()
                    .caseId(caseId)
                    .status(status)
                    .success(false)
                    .message("Unable to update AML case status - compliance service unavailable")
                    .errorDetails(cause.getMessage())
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackResponse);
            }

            @Override
            public ResponseEntity<List<AMLCaseResponse>> getAMLCasesBySubject(String subjectId) {
                log.error("Get AML cases by subject fallback triggered for subject: {}. Cause: {}",
                    subjectId, cause.getMessage());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Collections.emptyList());
            }

            @Override
            public ResponseEntity<ComplianceReportDTO> generateComplianceReport(
                String validationId,
                String format
            ) {
                log.error("Generate compliance report fallback triggered for validation: {}. Cause: {}",
                    validationId, cause.getMessage());

                ComplianceReportDTO fallbackReport = ComplianceReportDTO.builder()
                    .reportId("FALLBACK-" + validationId)
                    .validationId(validationId)
                    .reportType("COMPLIANCE_VALIDATION")
                    .format(format)
                    .status(ReportStatus.FAILED)
                    .generatedAt(LocalDateTime.now())
                    .success(false)
                    .message("Report generation unavailable - compliance service degraded")
                    .errorDetails(cause.getMessage())
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackReport);
            }

            @Override
            public ResponseEntity<List<ComplianceFindingDTO>> getFindings(
                String entityType,
                String entityId,
                FindingSeverity severity,
                FindingStatus status
            ) {
                log.error("Get findings fallback triggered for {}/{}. Cause: {}",
                    entityType, entityId, cause.getMessage());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Collections.emptyList());
            }

            @Override
            public ResponseEntity<HealthCheckDTO> healthCheck() {
                log.error("Health check fallback triggered. Cause: {}", cause.getMessage());

                HealthCheckDTO fallbackHealth = HealthCheckDTO.builder()
                    .status(HealthStatus.DOWN)
                    .serviceName("compliance-service")
                    .timestamp(LocalDateTime.now().toString())
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackHealth);
            }

            @Override
            public ResponseEntity<ComplianceMetricsDTO> getComplianceMetrics(String from, String to) {
                log.error("Get compliance metrics fallback triggered. Cause: {}", cause.getMessage());

                ComplianceMetricsDTO fallbackMetrics = ComplianceMetricsDTO.builder()
                    .periodStart(from)
                    .periodEnd(to)
                    .totalValidations(0L)
                    .validationsPassed(0L)
                    .validationsFailed(0L)
                    .averageComplianceScore(0.0)
                    .totalFindings(0L)
                    .trend("UNKNOWN")
                    .build();

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(fallbackMetrics);
            }
        };
    }
}
