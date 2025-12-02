package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@FeignClient(
    name = "compliance-service", 
    url = "${services.compliance-service.url:http://compliance-service:8091}",
    fallback = ComplianceServiceClientFallback.class
)
public interface ComplianceServiceClient {

    @PostMapping("/api/compliance/alerts")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<CreateComplianceAlertResponse> createComplianceAlert(@Valid @RequestBody CreateComplianceAlertRequest request);

    @GetMapping("/api/compliance/alerts/{alertId}")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<ComplianceAlert> getComplianceAlert(@PathVariable String alertId);

    @PostMapping("/api/compliance/suspicious-activity")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<SuspiciousActivityReport> reportSuspiciousActivity(@Valid @RequestBody SuspiciousActivityReportRequest request);

    @GetMapping("/api/compliance/user/{userId}/status")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<UserComplianceStatus> getUserComplianceStatus(@PathVariable String userId);

    @GetMapping("/api/compliance/alerts/user/{userId}")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<List<ComplianceAlert>> getUserComplianceAlerts(@PathVariable String userId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size);

    @PostMapping("/api/compliance/kyc/verify")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<KYCVerificationResponse> verifyKYC(@Valid @RequestBody KYCVerificationRequest request);

    @PostMapping("/api/compliance/aml/check")
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    ResponseEntity<AMLCheckResponse> performAMLCheck(@Valid @RequestBody AMLCheckRequest request);

    @PostMapping("/api/v1/compliance/government/verify")
    @CircuitBreaker(name = "compliance-service", fallbackMethod = "verifyWithGovernmentFallback")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    GovernmentVerificationResponse verifyWithGovernment(@Valid @RequestBody GovernmentVerificationRequest request);

    @PostMapping("/api/v1/compliance/sanctions/check")
    @CircuitBreaker(name = "compliance-service", fallbackMethod = "checkSanctionsFallback")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    SanctionsCheckResponse checkSanctions(@Valid @RequestBody SanctionsCheckRequest request);

    @PostMapping("/api/v1/compliance/kyb/screen")
    @CircuitBreaker(name = "compliance-service", fallbackMethod = "performKYBScreeningFallback")
    @Retry(name = "compliance-service")
    @TimeLimiter(name = "compliance-service")
    KYBScreeningResponse performKYBScreening(@Valid @RequestBody KYBScreeningRequest request);

    @PostMapping("/api/v1/compliance/reports/generate")
    ComplianceReportResponse generateReport(@Valid @RequestBody ComplianceReportRequest request);

    @GetMapping("/api/v1/compliance/health")
    String healthCheck();

    // DTOs
    record GovernmentVerificationRequest(
            String taxId,
            String registrationNumber,
            String country,
            String verificationType
    ) {}

    record GovernmentVerificationResponse(
            boolean verified,
            String verificationId,
            Map<String, Object> details,
            Instant verifiedAt
    ) {}

    record SanctionsCheckRequest(
            String entityName,
            String taxId,
            String entityType,
            List<String> sanctionLists
    ) {}

    record SanctionsCheckResponse(
            boolean matchFound,
            List<String> matchedLists,
            List<SanctionsMatch> matches,
            String checkId,
            Instant checkedAt
    ) {}

    record SanctionsMatch(
            String listName,
            String matchedName,
            double confidenceScore,
            Map<String, Object> additionalData
    ) {}

    record KYBScreeningRequest(
            String businessId,
            String businessName,
            String legalName,
            String taxId,
            String registrationNumber,
            String country,
            String industry,
            List<BeneficialOwner> beneficialOwners,
            List<String> screeningTypes
    ) {}

    record BeneficialOwner(
            String name,
            String dateOfBirth,
            String nationality,
            double ownershipPercentage,
            String role
    ) {}

    record KYBScreeningResponse(
            String screeningId,
            int overallRiskScore,
            String riskLevel,
            boolean passed,
            List<String> findings,
            boolean sanctions,
            boolean pep,
            boolean adverseMedia,
            List<String> recommendedActions,
            Instant completedAt
    ) {}

    record ComplianceReportRequest(
            String businessId,
            String reportType,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    record ComplianceReportResponse(
            String reportId,
            int complianceScore,
            String riskAssessment,
            List<String> violations,
            List<String> recommendations,
            LocalDate nextReviewDate,
            Instant generatedAt
    ) {}
}