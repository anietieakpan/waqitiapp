package com.waqiti.currency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.waqiti.currency.repository.ConversionAuditRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Conversion Compliance Service
 *
 * Handles financial compliance validation:
 * - AML (Anti-Money Laundering) checks
 * - High-value transaction validation
 * - Cross-border compliance
 * - Sanctions screening
 * - Regulatory reporting
 * - Audit trail management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversionComplianceService {

    private final ConversionAuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    private static final BigDecimal AML_THRESHOLD = BigDecimal.valueOf(10000);
    private static final BigDecimal HIGH_RISK_THRESHOLD = BigDecimal.valueOf(50000);
    private static final BigDecimal CRITICAL_THRESHOLD = BigDecimal.valueOf(100000);

    // High-risk countries (simplified list - in production, use OFAC/EU sanctions lists)
    private static final Set<String> HIGH_RISK_JURISDICTIONS = Set.of(
        "OFAC_SANCTIONED", "EU_SANCTIONED", "HIGH_RISK_AML"
    );

    /**
     * Validate conversion for compliance
     */
    public CurrencyConversionService.ComplianceValidationResult validateConversion(
            String conversionId, String sourceCurrency, String targetCurrency,
            BigDecimal sourceAmount, BigDecimal targetAmount, JsonNode conversionNode,
            String correlationId) {

        log.info("Validating compliance: conversionId={} {}→{} amount={} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, sourceAmount, correlationId);

        List<String> violations = new ArrayList<>();
        String riskLevel = "LOW";

        try {
            // 1. AML threshold check
            if (sourceAmount.compareTo(AML_THRESHOLD) >= 0) {
                violations.addAll(performAmlCheck(conversionId, sourceAmount, sourceCurrency,
                        targetCurrency, conversionNode, correlationId));
                riskLevel = "MEDIUM";
            }

            // 2. High-value transaction validation
            if (sourceAmount.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
                violations.addAll(performHighValueValidation(conversionId, sourceAmount,
                        sourceCurrency, targetCurrency, conversionNode, correlationId));
                riskLevel = "HIGH";
            }

            // 3. Critical value checks
            if (sourceAmount.compareTo(CRITICAL_THRESHOLD) >= 0) {
                violations.addAll(performCriticalValueCheck(conversionId, sourceAmount,
                        sourceCurrency, targetCurrency, conversionNode, correlationId));
                riskLevel = "CRITICAL";
            }

            // 4. Cross-border compliance
            if (!sourceCurrency.equals(targetCurrency)) {
                violations.addAll(performCrossBorderCheck(conversionId, sourceCurrency,
                        targetCurrency, sourceAmount, conversionNode, correlationId));
            }

            // 5. Sanctions screening
            violations.addAll(performSanctionsScreening(conversionId, sourceCurrency,
                    targetCurrency, conversionNode, correlationId));

            // 6. Conversion rate validation
            if (targetAmount != null) {
                violations.addAll(validateConversionRate(conversionId, sourceAmount,
                        targetAmount, sourceCurrency, targetCurrency, correlationId));
            }

            // Record audit trail
            auditRepository.save(conversionId, "COMPLIANCE_CHECK",
                    String.format("Compliance validation: riskLevel=%s violations=%d",
                        riskLevel, violations.size()),
                    correlationId);

            boolean isCompliant = violations.isEmpty();

            if (!isCompliant) {
                log.warn("Compliance violations detected: conversionId={} violations={} correlationId={}",
                        conversionId, violations, correlationId);
                incrementCounter("currency.compliance.violations");
            } else {
                incrementCounter("currency.compliance.passed");
            }

            return CurrencyConversionService.ComplianceValidationResult.builder()
                    .compliant(isCompliant)
                    .violations(violations)
                    .riskLevel(riskLevel)
                    .build();

        } catch (Exception e) {
            log.error("Error during compliance validation: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.compliance.error");

            violations.add("COMPLIANCE_CHECK_ERROR: " + e.getMessage());

            return CurrencyConversionService.ComplianceValidationResult.builder()
                    .compliant(false)
                    .violations(violations)
                    .riskLevel("ERROR")
                    .build();
        }
    }

    /**
     * Perform AML check
     */
    private List<String> performAmlCheck(String conversionId, BigDecimal amount,
                                         String sourceCurrency, String targetCurrency,
                                         JsonNode conversionNode, String correlationId) {

        log.debug("Performing AML check: conversionId={} amount={} correlationId={}",
                conversionId, amount, correlationId);

        List<String> violations = new ArrayList<>();

        // Check for structured transactions (multiple transactions just below threshold)
        if (conversionNode.has("previousConversionsToday")) {
            int previousConversions = conversionNode.get("previousConversionsToday").asInt();
            if (previousConversions > 5) {
                violations.add("POTENTIAL_STRUCTURING: Multiple conversions detected today");
                log.warn("Potential structuring detected: conversionId={} count={} correlationId={}",
                        conversionId, previousConversions, correlationId);
            }
        }

        // Check for rapid succession conversions
        if (conversionNode.has("lastConversionMinutesAgo")) {
            int minutesAgo = conversionNode.get("lastConversionMinutesAgo").asInt();
            if (minutesAgo < 5) {
                violations.add("RAPID_CONVERSIONS: Conversion made within 5 minutes of previous");
            }
        }

        // Check for unusual patterns
        if (amount.compareTo(AML_THRESHOLD) >= 0) {
            auditRepository.save(conversionId, "AML_CHECK",
                    String.format("High-value AML check: %s %s", amount, sourceCurrency),
                    correlationId);
        }

        return violations;
    }

    /**
     * Perform high-value transaction validation
     */
    private List<String> performHighValueValidation(String conversionId, BigDecimal amount,
                                                    String sourceCurrency, String targetCurrency,
                                                    JsonNode conversionNode, String correlationId) {

        log.debug("Performing high-value validation: conversionId={} amount={} correlationId={}",
                conversionId, amount, correlationId);

        List<String> violations = new ArrayList<>();

        // Check if customer is verified for high-value transactions
        if (conversionNode.has("customerVerificationLevel")) {
            String verificationLevel = conversionNode.get("customerVerificationLevel").asText();
            if (!"FULL_VERIFICATION".equals(verificationLevel)) {
                violations.add("INSUFFICIENT_VERIFICATION: Full verification required for high-value transactions");
                log.warn("Insufficient verification for high-value: conversionId={} level={} correlationId={}",
                        conversionId, verificationLevel, correlationId);
            }
        }

        // Check account age
        if (conversionNode.has("accountAgeDays")) {
            int accountAgeDays = conversionNode.get("accountAgeDays").asInt();
            if (accountAgeDays < 30) {
                violations.add("NEW_ACCOUNT_HIGH_VALUE: Account less than 30 days old");
            }
        }

        auditRepository.save(conversionId, "HIGH_VALUE_CHECK",
                String.format("High-value validation: %s %s", amount, sourceCurrency),
                correlationId);

        return violations;
    }

    /**
     * Perform critical value check
     */
    private List<String> performCriticalValueCheck(String conversionId, BigDecimal amount,
                                                   String sourceCurrency, String targetCurrency,
                                                   JsonNode conversionNode, String correlationId) {

        log.warn("Performing critical value check: conversionId={} amount={} {} correlationId={}",
                conversionId, amount, sourceCurrency, correlationId);

        List<String> violations = new ArrayList<>();

        // Critical transactions require manual approval
        violations.add("CRITICAL_VALUE_MANUAL_APPROVAL_REQUIRED: Amount exceeds critical threshold");

        auditRepository.save(conversionId, "CRITICAL_VALUE_CHECK",
                String.format("Critical value detected: %s %s - Manual approval required",
                    amount, sourceCurrency),
                correlationId);

        incrementCounter("currency.compliance.critical_value");

        return violations;
    }

    /**
     * Perform cross-border compliance check
     */
    private List<String> performCrossBorderCheck(String conversionId, String sourceCurrency,
                                                 String targetCurrency, BigDecimal amount,
                                                 JsonNode conversionNode, String correlationId) {

        log.debug("Performing cross-border check: conversionId={} {}→{} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, correlationId);

        List<String> violations = new ArrayList<>();

        // Check source and target jurisdiction
        String sourceJurisdiction = getJurisdiction(sourceCurrency);
        String targetJurisdiction = getJurisdiction(targetCurrency);

        // Check if either jurisdiction is high-risk
        if (HIGH_RISK_JURISDICTIONS.contains(sourceJurisdiction) ||
            HIGH_RISK_JURISDICTIONS.contains(targetJurisdiction)) {

            violations.add("HIGH_RISK_JURISDICTION: Cross-border transaction with high-risk jurisdiction");
            log.warn("High-risk jurisdiction detected: conversionId={} source={} target={} correlationId={}",
                    conversionId, sourceJurisdiction, targetJurisdiction, correlationId);
        }

        // Check reporting requirements
        if (amount.compareTo(BigDecimal.valueOf(3000)) >= 0) {
            auditRepository.save(conversionId, "CROSS_BORDER_REPORTING",
                    String.format("Cross-border transaction reporting: %s %s → %s",
                        amount, sourceCurrency, targetCurrency),
                    correlationId);
        }

        return violations;
    }

    /**
     * Perform sanctions screening
     */
    private List<String> performSanctionsScreening(String conversionId, String sourceCurrency,
                                                   String targetCurrency, JsonNode conversionNode,
                                                   String correlationId) {

        log.debug("Performing sanctions screening: conversionId={} {}→{} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, correlationId);

        List<String> violations = new ArrayList<>();

        // Check if customer is on sanctions list
        if (conversionNode.has("customerSanctionsStatus")) {
            String sanctionsStatus = conversionNode.get("customerSanctionsStatus").asText();
            if ("SANCTIONED".equals(sanctionsStatus)) {
                violations.add("SANCTIONS_VIOLATION: Customer is on sanctions list");
                log.error("Sanctions violation: conversionId={} correlationId={}",
                        conversionId, correlationId);
                incrementCounter("currency.compliance.sanctions_violation");
            }
        }

        return violations;
    }

    /**
     * Validate conversion rate for anomalies
     */
    private List<String> validateConversionRate(String conversionId, BigDecimal sourceAmount,
                                                BigDecimal targetAmount, String sourceCurrency,
                                                String targetCurrency, String correlationId) {

        log.debug("Validating conversion rate: conversionId={} {}→{} correlationId={}",
                conversionId, sourceCurrency, targetCurrency, correlationId);

        List<String> violations = new ArrayList<>();

        // Calculate implied rate
        BigDecimal impliedRate = targetAmount.divide(sourceAmount, 6, java.math.RoundingMode.HALF_UP);

        // In production, compare against market rates with acceptable deviation
        // For now, just check for obviously suspicious rates (>100x or <0.01x)
        if (impliedRate.compareTo(BigDecimal.valueOf(100)) > 0 ||
            impliedRate.compareTo(BigDecimal.valueOf(0.01)) < 0) {

            violations.add("SUSPICIOUS_RATE: Conversion rate is outside acceptable range");
            log.warn("Suspicious rate detected: conversionId={} rate={} correlationId={}",
                    conversionId, impliedRate, correlationId);
        }

        return violations;
    }

    /**
     * Get jurisdiction for currency (simplified)
     */
    private String getJurisdiction(String currency) {
        // In production, map currencies to actual jurisdictions
        return switch (currency) {
            case "USD" -> "US";
            case "EUR" -> "EU";
            case "GBP" -> "UK";
            case "JPY" -> "JP";
            case "CNY" -> "CN";
            case "RUB" -> "OFAC_SANCTIONED"; // Example
            default -> "OTHER";
        };
    }

    /**
     * Record audit trail
     */
    public void recordAuditTrail(String conversionId, String action, String details,
                                String correlationId) {

        log.debug("Recording audit trail: conversionId={} action={} correlationId={}",
                conversionId, action, correlationId);

        auditRepository.save(conversionId, action, details, correlationId);
        incrementCounter("currency.compliance.audit_recorded");
    }

    /**
     * Check regulatory requirements
     */
    public RegulatoryRequirements checkRegulatoryRequirements(String sourceCurrency,
                                                              String targetCurrency,
                                                              BigDecimal amount,
                                                              String correlationId) {

        log.debug("Checking regulatory requirements: {}→{} amount={} correlationId={}",
                sourceCurrency, targetCurrency, amount, correlationId);

        boolean requiresReporting = amount.compareTo(BigDecimal.valueOf(3000)) >= 0;
        boolean requiresManualApproval = amount.compareTo(CRITICAL_THRESHOLD) >= 0;
        boolean requiresEnhancedDueDiligence = amount.compareTo(HIGH_RISK_THRESHOLD) >= 0;

        return RegulatoryRequirements.builder()
                .requiresReporting(requiresReporting)
                .requiresManualApproval(requiresManualApproval)
                .requiresEnhancedDueDiligence(requiresEnhancedDueDiligence)
                .reportingDeadline(requiresReporting ? Instant.now().plusSeconds(86400) : null) // 24 hours
                .build();
    }

    /**
     * Increment counter metric
     */
    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }

    // Inner classes

    @lombok.Data
    @lombok.Builder
    public static class RegulatoryRequirements {
        private boolean requiresReporting;
        private boolean requiresManualApproval;
        private boolean requiresEnhancedDueDiligence;
        private Instant reportingDeadline;
    }
}
