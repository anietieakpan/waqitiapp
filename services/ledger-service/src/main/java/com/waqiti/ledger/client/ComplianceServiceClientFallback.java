package com.waqiti.ledger.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.waqiti.ledger.client.ComplianceServiceClient.*;

/**
 * Fallback implementation for ComplianceServiceClient
 * Provides graceful degradation when compliance-service is unavailable
 *
 * IMPORTANT: In degraded mode, transactions are allowed to proceed but flagged
 * for retrospective compliance review when service recovers
 */
@Slf4j
@Component
public class ComplianceServiceClientFallback implements ComplianceServiceClient {

    @Override
    public ResponseEntity<ComplianceValidationResponse> validateTransaction(TransactionComplianceRequest request) {
        log.warn("FALLBACK: compliance-service unavailable for validateTransaction. TransactionId: {}. " +
                "Transaction will proceed with DEGRADED_MODE flag for retrospective review.",
                request.transactionId());

        // Allow transaction to proceed but flag for review
        var response = new ComplianceValidationResponse(
            true, // compliant (degraded mode)
            "DEGRADED_MODE",
            "Compliance service unavailable - transaction allowed with retrospective review required",
            Map.of(
                "degradedMode", true,
                "requiresReview", true,
                "transactionId", request.transactionId().toString()
            )
        );

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ComplianceValidationResponse> validateAccount(AccountComplianceRequest request) {
        log.warn("FALLBACK: compliance-service unavailable for validateAccount. AccountId: {}. " +
                "Account assumed compliant with retrospective review required.",
                request.accountId());

        var response = new ComplianceValidationResponse(
            true, // compliant (degraded mode)
            "UNKNOWN",
            "Compliance service unavailable - account validation will be performed retrospectively",
            Map.of(
                "degradedMode", true,
                "requiresReview", true,
                "accountId", request.accountId().toString()
            )
        );

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AMLScreeningResponse> performAMLScreening(AMLScreeningRequest request) {
        log.error("FALLBACK: compliance-service unavailable for AML screening. EntityId: {}. " +
                 "CRITICAL: AML check bypassed - IMMEDIATE review required when service recovers.",
                 request.entityId());

        // AML screening critical - flag for urgent review
        var response = new AMLScreeningResponse(
            true, // passed (degraded mode - very risky!)
            "DEGRADED_MODE",
            "FALLBACK-" + System.currentTimeMillis(),
            Map.of(
                "degradedMode", true,
                "CRITICAL", "AML_CHECK_BYPASSED",
                "urgentReview", true,
                "entityId", request.entityId().toString()
            )
        );

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SARResponse> reportSuspiciousActivity(SuspiciousActivityRequest request) {
        log.error("FALLBACK: compliance-service unavailable for SAR reporting. AccountId: {}, TransactionId: {}. " +
                 "CRITICAL: SAR report NOT filed - will retry when service recovers.",
                 request.accountId(), request.transactionId());

        // Cannot file SAR when service down - must queue for retry
        // Return failed response to trigger retry logic
        throw new RuntimeException("Compliance service unavailable - SAR filing failed and will be retried. " +
                                  "AccountId: " + request.accountId());
    }

    @Override
    public ResponseEntity<ComplianceRulesResponse> getAccountTypeRules(String accountType) {
        log.warn("FALLBACK: compliance-service unavailable for getAccountTypeRules. AccountType: {}. " +
                "Returning default/cached rules.", accountType);

        // Return default conservative rules
        Map<String, Object> defaultRules = new HashMap<>();
        defaultRules.put("enabled", true);
        defaultRules.put("requiresKYC", true);
        defaultRules.put("requiresAML", true);

        Map<String, Object> defaultLimits = new HashMap<>();
        defaultLimits.put("dailyLimit", 10000);
        defaultLimits.put("monthlyLimit", 50000);
        defaultLimits.put("degradedMode", true);

        var response = new ComplianceRulesResponse(
            accountType,
            defaultRules,
            defaultLimits
        );

        return ResponseEntity.ok(response);
    }
}
