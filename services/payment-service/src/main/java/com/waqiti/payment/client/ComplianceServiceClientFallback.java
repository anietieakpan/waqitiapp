package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.client.ComplianceServiceClient.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ComplianceServiceClientFallback implements ComplianceServiceClient {

    @Override
    public ResponseEntity<CreateComplianceAlertResponse> createComplianceAlert(CreateComplianceAlertRequest request) {
        log.error("CRITICAL: Compliance Service fallback - createComplianceAlert failed for alertId={}. " +
                "Manual compliance notification required.",
                request.getAlertId());
        
        throw new RuntimeException("CRITICAL: Compliance alert creation failed - manual intervention required");
    }

    @Override
    public ResponseEntity<ComplianceAlert> getComplianceAlert(String alertId) {
        log.warn("Compliance Service fallback: getComplianceAlert - alertId={}", alertId);
        
        ComplianceAlert alert = ComplianceAlert.builder()
            .alertId(alertId)
            .status("UNKNOWN")
            .message("Compliance service unavailable")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(alert);
    }

    @Override
    public ResponseEntity<SuspiciousActivityReport> reportSuspiciousActivity(SuspiciousActivityReportRequest request) {
        log.error("CRITICAL: Compliance Service fallback - reportSuspiciousActivity failed for userId={}. " +
                "Manual SAR filing required.",
                request.getUserId());
        
        throw new RuntimeException("CRITICAL: Suspicious activity reporting failed - manual SAR filing required");
    }

    @Override
    public ResponseEntity<UserComplianceStatus> getUserComplianceStatus(String userId) {
        log.warn("Compliance Service fallback: getUserComplianceStatus - userId={}", userId);
        
        UserComplianceStatus status = UserComplianceStatus.builder()
            .userId(userId)
            .status("UNKNOWN")
            .message("Compliance service unavailable")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
    }

    @Override
    public ResponseEntity<List<ComplianceAlert>> getUserComplianceAlerts(String userId, int page, int size) {
        log.warn("Compliance Service fallback: getUserComplianceAlerts - userId={}", userId);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(List.of());
    }

    @Override
    public ResponseEntity<KYCVerificationResponse> verifyKYC(KYCVerificationRequest request) {
        log.warn("Compliance Service fallback: verifyKYC - userId={}", request.getUserId());
        
        KYCVerificationResponse response = KYCVerificationResponse.builder()
            .userId(request.getUserId())
            .verified(false)
            .requiresManualReview(true)
            .message("Compliance service unavailable - manual KYC review required")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @Override
    public ResponseEntity<AMLCheckResponse> performAMLCheck(AMLCheckRequest request) {
        log.warn("Compliance Service fallback: performAMLCheck - userId={}", request.getUserId());
        
        AMLCheckResponse response = AMLCheckResponse.builder()
            .userId(request.getUserId())
            .passed(false)
            .requiresManualReview(true)
            .message("Compliance service unavailable - manual AML check required")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @Override
    public GovernmentVerificationResponse verifyWithGovernment(GovernmentVerificationRequest request) {
        log.warn("Compliance Service fallback: verifyWithGovernment - taxId={}", request.taxId());
        
        return new GovernmentVerificationResponse(
            false,
            null,
            Map.of("error", "Service unavailable"),
            Instant.now()
        );
    }

    @Override
    public SanctionsCheckResponse checkSanctions(SanctionsCheckRequest request) {
        log.warn("Compliance Service fallback: checkSanctions - entityName={}", request.entityName());
        
        return new SanctionsCheckResponse(
            false,
            List.of(),
            List.of(),
            null,
            Instant.now()
        );
    }

    @Override
    public KYBScreeningResponse performKYBScreening(KYBScreeningRequest request) {
        log.warn("Compliance Service fallback: performKYBScreening - businessId={}", request.businessId());
        
        return new KYBScreeningResponse(
            null,
            50,
            "MEDIUM",
            false,
            List.of("Service unavailable - manual review required"),
            false,
            false,
            false,
            List.of("MANUAL_REVIEW"),
            Instant.now()
        );
    }

    @Override
    public ComplianceReportResponse generateReport(ComplianceReportRequest request) {
        log.warn("Compliance Service fallback: generateReport - businessId={}", request.businessId());
        
        return new ComplianceReportResponse(
            null,
            0,
            "UNKNOWN",
            List.of(),
            List.of("Service unavailable"),
            LocalDate.now().plusMonths(1),
            Instant.now()
        );
    }

    @Override
    public String healthCheck() {
        return "UNAVAILABLE";
    }
}