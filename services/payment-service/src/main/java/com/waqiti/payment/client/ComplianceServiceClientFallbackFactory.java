package com.waqiti.payment.client.fallback;

import com.waqiti.common.response.ApiResponse;
import com.waqiti.payment.client.ComplianceServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fallback factory for ComplianceServiceClient providing resilient error handling
 * and graceful degradation when the compliance service is unavailable.
 * 
 * This factory implements circuit breaker patterns and provides meaningful
 * fallback responses to ensure payment processing can continue with appropriate
 * risk management when compliance services are temporarily unavailable.
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Component
@Slf4j
public class ComplianceServiceClientFallbackFactory implements FallbackFactory<ComplianceServiceClient> {

    @Override
    public ComplianceServiceClient create(Throwable cause) {
        log.error("Compliance service is unavailable, activating fallback", cause);
        return new ComplianceServiceClientFallback(cause);
    }

    /**
     * Fallback implementation providing safe default responses when compliance service is down
     */
    @Slf4j
    static class ComplianceServiceClientFallback implements ComplianceServiceClient {
        
        private final Throwable cause;

        public ComplianceServiceClientFallback(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public ResponseEntity<ApiResponse<AMLScreeningResult>> performAMLScreening(
                AMLScreeningRequest request, String correlationId, String idempotencyKey) {
            log.warn("AML screening fallback activated for user: {}, transaction: {}", 
                    request.userId(), request.transactionId());
            
            // Return a conservative screening result that requires manual review
            AMLScreeningResult fallbackResult = new AMLScreeningResult(
                    "fallback-" + System.currentTimeMillis(),
                    request.userId(),
                    request.transactionId(),
                    ComplianceStatus.PENDING, // Conservative: require review
                    RiskLevel.MEDIUM, // Conservative risk level
                    0.5, // Neutral risk score
                    List.of(new RiskFactor("SERVICE_UNAVAILABLE", "Compliance service temporarily unavailable", 0.0, "Service outage")),
                    List.of(new ComplianceAlert("SYSTEM", "WARNING", "Compliance service unavailable - manual review required", "FALLBACK_001")),
                    "MANUAL_REVIEW_REQUIRED", // Conservative recommendation
                    "Compliance service temporarily unavailable",
                    Instant.now(),
                    "fallback-v1.0",
                    new RegulatoryMetadata("FALLBACK", "EMERGENCY_PROCEDURE", "1.0")
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackResult, "Fallback AML screening - manual review required"));
        }

        @Override
        public ResponseEntity<ApiResponse<Page<AMLScreeningResult>>> getAMLHistory(
                String userId, Pageable pageable, String correlationId) {
            log.warn("AML history fallback activated for user: {}", userId);
            
            Page<AMLScreeningResult> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("AML history temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<BulkProcessingReference>> bulkAMLScreening(
                List<AMLScreeningRequest> requests, String correlationId) {
            log.warn("Bulk AML screening fallback activated for {} requests", requests.size());
            
            BulkProcessingReference fallbackRef = new BulkProcessingReference(
                    "fallback-" + System.currentTimeMillis(),
                    requests.size(),
                    "FAILED_SERVICE_UNAVAILABLE",
                    Instant.now(),
                    "/compliance/bulk/fallback-status"
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Bulk processing unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<KYCVerificationResult>> performKYCVerification(
                KYCVerificationRequest request, String correlationId) {
            log.warn("KYC verification fallback activated for user: {}", request.userId());
            
            // Return pending status requiring manual verification
            KYCVerificationResult fallbackResult = new KYCVerificationResult(
                    "fallback-" + System.currentTimeMillis(),
                    request.userId(),
                    KYCStatus.PENDING_REVIEW,
                    VerificationLevel.BASIC,
                    Collections.emptyList(),
                    List.of(new VerificationStep("MANUAL_REVIEW", "PENDING", "Service unavailable - manual verification required", null)),
                    List.of(new ComplianceAlert("SYSTEM", "WARNING", "KYC service unavailable", "FALLBACK_002")),
                    null,
                    Instant.now(),
                    Instant.now().plusDays(30), // 30-day temporary approval
                    new RegulatoryMetadata("FALLBACK", "EMERGENCY_PROCEDURE", "1.0")
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackResult, "Fallback KYC verification - manual review required"));
        }

        @Override
        public ResponseEntity<ApiResponse<KYCStatus>> getKYCStatus(String userId, String correlationId) {
            log.warn("KYC status fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("KYC status temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<KYCStatus>> updateKYCInformation(
                String userId, KYCUpdateRequest request, String correlationId) {
            log.warn("KYC update fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("KYC update temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<SanctionsScreeningResult>> performSanctionsScreening(
                SanctionsScreeningRequest request, String correlationId) {
            log.warn("Sanctions screening fallback activated for entity: {}", request.entityId());
            
            // Conservative approach: block transaction if sanctions screening is unavailable
            SanctionsScreeningResult fallbackResult = new SanctionsScreeningResult(
                    "fallback-" + System.currentTimeMillis(),
                    request.entityId(),
                    ComplianceStatus.UNDER_REVIEW, // Conservative: require review
                    Collections.emptyList(),
                    0.0,
                    Collections.emptyList(),
                    "BLOCK_PENDING_VERIFICATION", // Conservative recommendation
                    Instant.now(),
                    new RegulatoryMetadata("FALLBACK", "EMERGENCY_PROCEDURE", "1.0")
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackResult, "Fallback sanctions screening - verification required"));
        }

        @Override
        public ResponseEntity<ApiResponse<List<WatchlistMonitoringResult>>> performWatchlistMonitoring(
                List<MonitoringEntity> entities, String correlationId) {
            log.warn("Watchlist monitoring fallback activated for {} entities", entities.size());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Watchlist monitoring temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<TransactionMonitoringResult>> monitorTransaction(
                TransactionMonitoringRequest request, String correlationId) {
            log.warn("Transaction monitoring fallback activated for transaction: {}", request.transactionId());
            
            // Conservative monitoring result requiring review
            TransactionMonitoringResult fallbackResult = new TransactionMonitoringResult(
                    "fallback-" + System.currentTimeMillis(),
                    request.transactionId(),
                    MonitoringStatus.ALERT, // Conservative: flag for review
                    List.of(new MonitoringAlert("FALLBACK_001", "SYSTEM", "HIGH", "Monitoring service unavailable")),
                    RiskLevel.MEDIUM, // Conservative risk level
                    0.5, // Neutral risk score
                    List.of("SERVICE_UNAVAILABLE"),
                    "MANUAL_REVIEW_REQUIRED",
                    true, // Require manual review
                    Instant.now()
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackResult, "Fallback transaction monitoring - manual review required"));
        }

        @Override
        public ResponseEntity<ApiResponse<Page<SuspiciousActivityReport>>> getSuspiciousActivityReports(
                SuspiciousActivityFilter filter, Pageable pageable, String correlationId) {
            log.warn("Suspicious activity reports fallback activated");
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Suspicious activity reports temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<SARFilingResult>> fileSuspiciousActivityReport(
                SARFilingRequest request, String correlationId) {
            log.warn("SAR filing fallback activated for transaction: {}", request.transactionId());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("SAR filing temporarily unavailable - please use manual filing procedures", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<RiskAssessmentResult>> performRiskAssessment(
                RiskAssessmentRequest request, String correlationId) {
            log.warn("Risk assessment fallback activated for entity: {}", request.entityId());
            
            // Conservative risk assessment
            RiskAssessmentResult fallbackResult = new RiskAssessmentResult(
                    "fallback-" + System.currentTimeMillis(),
                    request.entityId(),
                    OverallRiskLevel.MEDIUM, // Conservative risk level
                    0.5, // Neutral risk score
                    Map.of("SERVICE_AVAILABILITY", new RiskFactorScore("SERVICE_AVAILABILITY", 0.5, "Risk service unavailable")),
                    List.of(new RiskMitigationRecommendation("MANUAL_REVIEW", "Conduct manual risk assessment", "HIGH")),
                    "Risk assessment service temporarily unavailable - manual assessment required",
                    Instant.now(),
                    Instant.now().plusDays(7) // Review in 7 days
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackResult, "Fallback risk assessment - manual review required"));
        }

        @Override
        public ResponseEntity<ApiResponse<RiskProfile>> updateRiskProfile(
                String userId, RiskProfileUpdateRequest request, String correlationId) {
            log.warn("Risk profile update fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Risk profile update temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<ReportGenerationReference>> generateComplianceReport(
                ComplianceReportRequest request, String correlationId) {
            log.warn("Compliance report generation fallback activated");
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Report generation temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<ReportStatus>> getReportStatus(String reportId, String correlationId) {
            log.warn("Report status fallback activated for report: {}", reportId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Report status temporarily unavailable", "SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<ServiceHealthStatus>> healthCheck() {
            log.warn("Compliance service health check fallback activated");
            
            ServiceHealthStatus fallbackHealth = new ServiceHealthStatus(
                    "DEGRADED",
                    Map.of(
                            "compliance-api", "UNAVAILABLE",
                            "sanctions-db", "UNKNOWN",
                            "aml-engine", "UNKNOWN"
                    ),
                    Instant.now(),
                    "fallback-1.0"
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackHealth, "Service degraded - fallback mode active"));
        }

        @Override
        public CompletableFuture<ResponseEntity<ApiResponse<ComprehensiveComplianceResult>>> performComprehensiveComplianceCheckAsync(
                ComprehensiveComplianceRequest request, String correlationId) {
            log.warn("Comprehensive compliance check fallback activated for user: {}, transaction: {}", 
                    request.userId(), request.transactionId());
            
            // Create a conservative fallback result that requires manual review
            ComprehensiveComplianceResult fallbackResult = new ComprehensiveComplianceResult(
                    "fallback-" + System.currentTimeMillis(),
                    request.userId(),
                    request.transactionId(),
                    ComplianceStatus.PENDING, // Conservative status
                    null, // No AML result
                    null, // No KYC result
                    null, // No sanctions result
                    null, // No risk result
                    List.of(new ComplianceRecommendation("MANUAL_REVIEW", "REVIEW_REQUIRED", "Compliance service unavailable", "HIGH")),
                    false, // Not approved automatically
                    "Compliance service temporarily unavailable - manual review required",
                    Instant.now()
            );

            ResponseEntity<ApiResponse<ComprehensiveComplianceResult>> response = 
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(ApiResponse.success(fallbackResult, "Fallback comprehensive compliance check"));

            return CompletableFuture.completedFuture(response);
        }
    }
}