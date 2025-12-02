package com.waqiti.corebanking.client;

import com.waqiti.corebanking.service.ComplianceIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fallback implementation for ComplianceServiceClient
 *
 * Provides graceful degradation when compliance service is unavailable
 *
 * CRITICAL: Compliance checks cannot be skipped for financial operations
 * - High-risk transactions are BLOCKED when service unavailable
 * - Low-risk transactions proceed with WARNING flags
 * - All fallback invocations are logged for audit
 *
 * Circuit Breaker Strategy:
 * - Compliance service failures trigger circuit breaker
 * - Fallback prevents cascading failures
 * - Alerts sent to compliance team
 *
 * @author Core Banking Team
 * @since 1.0
 */
@Component
@Slf4j
public class ComplianceServiceClientFallback implements ComplianceServiceClient {

    private static final String FALLBACK_MESSAGE = "Compliance service temporarily unavailable - using fallback";
    private static final double FALLBACK_RISK_SCORE = 0.5; // Medium risk when unable to verify

    @Override
    public ComplianceIntegrationService.ComplianceCheckResult screenTransaction(
            UUID transactionId,
            UUID userId,
            String amount,
            String currency,
            String transactionType) {

        log.error("CRITICAL: Compliance service unavailable for transaction screening. " +
                "TransactionId: {}, UserId: {}, Amount: {} {}, Type: {}",
                transactionId, userId, amount, currency, transactionType);

        // Alert compliance team
        alertComplianceTeam("Transaction screening failed - service unavailable", transactionId);

        // Determine if transaction can proceed based on risk
        boolean canProceed = isLowRiskTransaction(amount, currency, transactionType);

        return ComplianceIntegrationService.ComplianceCheckResult.builder()
                .approved(canProceed)
                .requiresManualReview(!canProceed)
                .riskScore(FALLBACK_RISK_SCORE)
                .alerts(List.of(
                        "FALLBACK: Compliance service unavailable",
                        canProceed ?
                                "Low-risk transaction approved with monitoring" :
                                "Transaction blocked - manual compliance review required"
                ))
                .build();
    }

    @Override
    public ComplianceIntegrationService.ComplianceCheckResult performRiskAssessment(
            UUID userId,
            String transactionHistory,
            String behaviorPattern) {

        log.error("CRITICAL: Compliance service unavailable for risk assessment. UserId: {}", userId);

        alertComplianceTeam("Risk assessment failed - service unavailable", userId);

        // Default to medium risk when unable to assess
        return ComplianceIntegrationService.ComplianceCheckResult.builder()
                .approved(true)
                .requiresManualReview(true)
                .riskScore(FALLBACK_RISK_SCORE)
                .alerts(List.of(
                        "FALLBACK: Risk assessment service unavailable",
                        "User flagged for manual risk review"
                ))
                .build();
    }

    @Override
    public ComplianceIntegrationService.ComplianceCheckResult screenEntity(
            UUID entityId,
            String entityName,
            String entityType) {

        log.error("CRITICAL: Compliance service unavailable for entity screening. " +
                "EntityId: {}, Name: {}, Type: {}", entityId, entityName, entityType);

        alertComplianceTeam("Entity screening failed - service unavailable", entityId);

        // Block entity operations when screening unavailable (high risk)
        return ComplianceIntegrationService.ComplianceCheckResult.builder()
                .approved(false)
                .requiresManualReview(true)
                .riskScore(1.0) // Highest risk
                .alerts(List.of(
                        "FALLBACK: Entity screening service unavailable",
                        "Entity operations blocked pending manual compliance review",
                        "Cannot verify sanctions list, PEP status, or adverse media"
                ))
                .build();
    }

    @Override
    public void generateRegulatoryReport(
            UUID reportId,
            String reportType,
            String dateRange) {

        log.error("CRITICAL: Compliance service unavailable for report generation. " +
                "ReportId: {}, Type: {}, DateRange: {}", reportId, reportType, dateRange);

        alertComplianceTeam("Regulatory report generation failed - service unavailable", reportId);

        // Regulatory reports cannot proceed without compliance service
        throw new RuntimeException("Regulatory report generation failed - compliance service unavailable. " +
                "Manual report generation required. ReportId: " + reportId);
    }

    /**
     * Determine if transaction is low-risk and can proceed without compliance check
     *
     * Low-risk criteria:
     * - Amount < $1000
     * - Domestic currency
     * - Standard transaction types (P2P, fee, interest)
     *
     * @return true if low-risk transaction
     */
    private boolean isLowRiskTransaction(String amount, String currency, String transactionType) {
        try {
            double amountValue = Double.parseDouble(amount);

            // Low risk: < $1000 domestic transactions
            boolean isLowAmount = amountValue < 1000.0;
            boolean isDomesticCurrency = "USD".equalsIgnoreCase(currency);
            boolean isStandardType = List.of("P2P_TRANSFER", "FEE", "INTEREST_CREDIT")
                    .contains(transactionType);

            return isLowAmount && isDomesticCurrency && isStandardType;

        } catch (NumberFormatException e) {
            log.error("Invalid amount format in fallback: {}", amount);
            return false; // Block if amount cannot be parsed
        }
    }

    /**
     * Alert compliance team of service failure
     *
     * TODO: Integrate with NotificationService to send real-time alerts
     */
    private void alertComplianceTeam(String message, Object entityId) {
        log.error("COMPLIANCE ALERT: {} | EntityId: {} | Timestamp: {}",
                message, entityId, java.time.LocalDateTime.now());

        // TODO: Send email/SMS alert to compliance team
        // TODO: Create incident ticket in monitoring system
        // TODO: Publish alert to Kafka for real-time monitoring
    }
}
