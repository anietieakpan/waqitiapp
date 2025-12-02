package com.waqiti.transaction.saga.steps;

import com.waqiti.transaction.client.ComplianceServiceClient;
import com.waqiti.transaction.dto.ComplianceCheckRequest;
import com.waqiti.transaction.dto.ComplianceCheckResponse;
import com.waqiti.transaction.saga.TransactionSagaContext;
import com.waqiti.transaction.saga.SagaStepResult;
import com.waqiti.common.saga.SagaStep;
import com.waqiti.common.saga.SagaStepStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-READY Compliance Screening Saga Step
 *
 * Performs AML/KYC/sanctions screening for regulatory compliance
 *
 * Features:
 * - AML/KYC verification
 * - OFAC sanctions screening
 * - PEP (Politically Exposed Person) screening via Dow Jones API
 * - Travel Rule compliance (≥$3K transfers)
 * - Transaction limits enforcement
 * - Regulatory reporting triggers
 * - Circuit breaker for resilience
 *
 * Compensation:
 * - Logs compliance violation for audit trail
 * - No actual compensation needed (read-only screening)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceScreeningSagaStep implements SagaStep<TransactionSagaContext> {

    private final ComplianceServiceClient complianceClient;
    private final MeterRegistry meterRegistry;

    private static final String STEP_NAME = "COMPLIANCE_SCREENING";
    private static final BigDecimal TRAVEL_RULE_THRESHOLD = new BigDecimal("3000");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    @CircuitBreaker(name = "complianceService", fallbackMethod = "fallbackComplianceCheck")
    @Retry(name = "complianceService")
    public CompletableFuture<SagaStepResult> execute(TransactionSagaContext context) {
        Timer.Sample timer = Timer.start(meterRegistry);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("COMPLIANCE SCREENING: Performing AML/KYC/sanctions check for transaction: {} amount: {} {}",
                    context.getTransactionId(), context.getAmount(), context.getCurrency());

                // Determine compliance check level based on amount
                String checkLevel = determineCheckLevel(context.getAmount());

                // Build compliance check request
                ComplianceCheckRequest request = ComplianceCheckRequest.builder()
                    .transactionId(context.getTransactionId())
                    .userId(context.getUserId())
                    .sourceWalletId(context.getSourceWalletId())
                    .destinationWalletId(context.getDestinationWalletId())
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .transactionType(context.getTransactionType().name())
                    .checkLevel(checkLevel)
                    .ipAddress(context.getIpAddress())
                    .geolocation(context.getGeolocation())
                    .timestamp(LocalDateTime.now())
                    .build();

                // Perform compliance screening
                ComplianceCheckResponse response = complianceClient.screenTransaction(request);

                if (response == null) {
                    log.error("COMPLIANCE SCREENING FAILED: Null response from compliance service for transaction: {}",
                        context.getTransactionId());

                    timer.stop(Timer.builder("transaction.saga.step.compliance.time")
                        .tag("result", "error")
                        .register(meterRegistry));

                    meterRegistry.counter("transaction.compliance.errors").increment();

                    return SagaStepResult.builder()
                        .stepName(STEP_NAME)
                        .status(SagaStepStatus.FAILED)
                        .errorMessage("Compliance service unavailable")
                        .timestamp(LocalDateTime.now())
                        .build();
                }

                // Analyze compliance response
                SagaStepResult result = analyzeComplianceResponse(context, response);

                timer.stop(Timer.builder("transaction.saga.step.compliance.time")
                    .tag("result", result.getStatus().name())
                    .tag("check_level", checkLevel)
                    .tag("compliance_status", response.getComplianceStatus())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.compliance.checks",
                    "result", result.getStatus().name(),
                    "status", response.getComplianceStatus()).increment();

                return result;

            } catch (Exception e) {
                log.error("COMPLIANCE SCREENING ERROR: Error during compliance check for transaction: {}",
                    context.getTransactionId(), e);

                timer.stop(Timer.builder("transaction.saga.step.compliance.time")
                    .tag("result", "error")
                    .tag("error_type", e.getClass().getSimpleName())
                    .register(meterRegistry));

                meterRegistry.counter("transaction.compliance.errors").increment();

                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Compliance screening failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Determine compliance check level based on transaction amount
     */
    private String determineCheckLevel(BigDecimal amount) {
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            return "ENHANCED"; // Enhanced due diligence for high-value
        } else if (amount.compareTo(TRAVEL_RULE_THRESHOLD) >= 0) {
            return "STANDARD_PLUS"; // Travel Rule compliance required
        } else {
            return "STANDARD"; // Standard AML/KYC check
        }
    }

    /**
     * Analyze compliance response and make decision
     */
    private SagaStepResult analyzeComplianceResponse(TransactionSagaContext context,
                                                     ComplianceCheckResponse response) {
        String complianceStatus = response.getComplianceStatus();

        // CRITICAL: Block transactions with sanctions hits
        if ("SANCTIONS_HIT".equals(complianceStatus) || "OFAC_MATCH".equals(complianceStatus)) {
            log.warn("COMPLIANCE BLOCKED: Sanctions hit detected for transaction: {} - Reasons: {}",
                context.getTransactionId(), response.getViolations());

            context.setComplianceBlocked(true);
            context.setComplianceViolations(response.getViolations());
            context.setSanctionsHit(true);

            return SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.FAILED)
                .errorMessage("Transaction blocked - Sanctions screening violation: " +
                    String.join(", ", response.getViolations()))
                .data("complianceStatus", complianceStatus)
                .data("violations", response.getViolations())
                .data("sanctionsHit", true)
                .timestamp(LocalDateTime.now())
                .build();
        }

        // WARNING: PEP match requires enhanced monitoring
        if ("PEP_MATCH".equals(complianceStatus)) {
            log.warn("COMPLIANCE WARNING: PEP match detected for transaction: {} - Screening ID: {}",
                context.getTransactionId(), response.getScreeningId());

            context.setPepMatch(true);
            context.setEnhancedMonitoring(true);
            context.setComplianceScreeningId(response.getScreeningId());

            // Allow but flag for enhanced monitoring
            return SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.SUCCESS)
                .message("PEP match - transaction allowed with enhanced monitoring")
                .data("complianceStatus", complianceStatus)
                .data("pepMatch", true)
                .data("enhancedMonitoring", true)
                .data("screeningId", response.getScreeningId())
                .timestamp(LocalDateTime.now())
                .build();
        }

        // Travel Rule compliance for ≥$3K transfers
        if (context.getAmount().compareTo(TRAVEL_RULE_THRESHOLD) >= 0) {
            context.setTravelRuleApplicable(true);
            context.setRequiresAdditionalInfo(response.isRequiresAdditionalInfo());

            if (response.isRequiresAdditionalInfo()) {
                log.warn("COMPLIANCE HOLD: Travel Rule additional info required for transaction: {}",
                    context.getTransactionId());

                // Hold for additional information gathering
                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Travel Rule compliance - additional information required")
                    .data("complianceStatus", "PENDING_INFO")
                    .data("travelRuleApplicable", true)
                    .data("requiresAdditionalInfo", true)
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        }

        // SUCCESS: Compliance check passed
        log.info("COMPLIANCE PASSED: Transaction cleared all compliance checks: {}",
            context.getTransactionId());

        context.setComplianceStatus(complianceStatus);
        context.setComplianceScreeningId(response.getScreeningId());

        return SagaStepResult.builder()
            .stepName(STEP_NAME)
            .status(SagaStepStatus.SUCCESS)
            .message("Compliance screening passed: " + complianceStatus)
            .data("complianceStatus", complianceStatus)
            .data("screeningId", response.getScreeningId())
            .data("riskScore", response.getRiskScore())
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Fallback when compliance service is unavailable
     */
    private CompletableFuture<SagaStepResult> fallbackComplianceCheck(TransactionSagaContext context, Exception ex) {
        log.error("COMPLIANCE FALLBACK: Compliance service unavailable for transaction: {} - {}",
            context.getTransactionId(), ex.getMessage());

        meterRegistry.counter("transaction.compliance.fallback").increment();

        // CRITICAL: Block high-value transactions when compliance service is down
        if (context.getAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            log.warn("COMPLIANCE FALLBACK BLOCK: High-value transaction blocked due to compliance service unavailability. ID: {}, Amount: {}",
                context.getTransactionId(), context.getAmount());

            return CompletableFuture.completedFuture(SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.FAILED)
                .errorMessage("High-value transaction blocked - compliance service unavailable")
                .data("fallbackActivated", true)
                .data("amount", context.getAmount())
                .timestamp(LocalDateTime.now())
                .build());
        } else {
            // ALLOW low-value transactions with post-screening
            log.warn("COMPLIANCE FALLBACK ALLOW: Low-value transaction allowed with post-screening. ID: {}, Amount: {}",
                context.getTransactionId(), context.getAmount());

            context.setComplianceCheckBypassed(true);
            context.setRequiresPostScreening(true);

            return CompletableFuture.completedFuture(SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.SUCCESS)
                .message("Compliance check bypassed (service unavailable) - queued for post-screening")
                .data("fallbackActivated", true)
                .data("bypassed", true)
                .data("requiresPostScreening", true)
                .timestamp(LocalDateTime.now())
                .build());
        }
    }

    @Override
    public CompletableFuture<SagaStepResult> compensate(TransactionSagaContext context, SagaStepResult originalResult) {
        // Compliance screening is a read-only check - no actual compensation needed
        // Just log the compensation for audit trail

        log.info("COMPLIANCE COMPENSATION: Recording compliance check bypass for transaction: {}",
            context.getTransactionId());

        return CompletableFuture.completedFuture(SagaStepResult.builder()
            .stepName(STEP_NAME + "_COMPENSATION")
            .status(SagaStepStatus.SUCCESS)
            .message("Compliance check compensation completed (audit trail updated)")
            .data("complianceViolationLogged", context.isComplianceBlocked())
            .timestamp(LocalDateTime.now())
            .build());
    }
}
