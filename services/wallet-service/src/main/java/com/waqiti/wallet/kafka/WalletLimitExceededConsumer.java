package com.waqiti.wallet.kafka;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletLimit;
import com.waqiti.wallet.domain.LimitViolation;
import com.waqiti.wallet.dto.WalletLimitExceededEvent;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletLimitRepository;
import com.waqiti.wallet.repository.LimitViolationRepository;
import com.waqiti.wallet.service.*;
import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade consumer for wallet limit exceeded events
 * Enforces transaction limits, prevents overspending, and ensures regulatory compliance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLimitExceededConsumer {

    private final WalletRepository walletRepository;
    private final WalletLimitRepository limitRepository;
    private final LimitViolationRepository violationRepository;
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    private final RiskService riskService;
    private final DistributedLockService lockService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    @Value("${wallet.limits.hard-block-threshold:3}")
    private int hardBlockThreshold;

    @Value("${wallet.limits.compliance-report-threshold:10000}")
    private BigDecimal complianceReportThreshold;

    @Value("${wallet.limits.cooling-period-hours:24}")
    private int coolingPeriodHours;

    @KafkaListener(
        topics = "wallet-limit-exceeded",
        groupId = "wallet-limit-enforcement",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processLimitExceeded(
            @Payload WalletLimitExceededEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String lockKey = "wallet-limit-" + event.getWalletId();
        Counter limitExceededCounter = meterRegistry.counter("wallet.limits.exceeded",
            "type", event.getLimitType(), "severity", event.getSeverity());
        
        try {
            log.warn("Wallet limit exceeded: {} - Type: {}, Amount: {}, Limit: {}", 
                event.getWalletId(), event.getLimitType(), event.getAttemptedAmount(), event.getLimitAmount());

            // Acquire distributed lock for wallet operations
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.error("Could not acquire lock for wallet: {}", event.getWalletId());
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // 1. Validate wallet and limits
                Wallet wallet = validateWallet(event.getWalletId());
                WalletLimit limit = validateLimit(event);
                
                // 2. Record limit violation
                LimitViolation violation = recordViolation(wallet, limit, event);
                
                // 3. Assess violation severity and patterns
                ViolationAssessment assessment = assessViolation(wallet, violation, event);
                
                // 4. Apply enforcement actions based on severity
                List<EnforcementAction> actions = applyEnforcement(wallet, assessment, event);
                
                // 5. Block or reject the transaction
                blockTransaction(event.getTransactionId(), assessment.getBlockReason());
                
                // 6. Update wallet restrictions
                updateWalletRestrictions(wallet, assessment, actions);
                
                // 7. Check for fraud indicators
                checkFraudIndicators(wallet, violation, assessment);
                
                // 8. Send notifications
                sendNotifications(wallet, violation, assessment, actions);
                
                // 9. Report to compliance if needed
                reportToCompliance(wallet, violation, assessment, event);
                
                // 10. Schedule limit reset or review
                scheduleLimitReview(wallet, assessment);
                
                // 11. Update risk profile
                updateRiskProfile(wallet, violation, assessment);
                
                limitExceededCounter.increment();
                log.info("Limit enforcement completed for wallet {} with {} actions", 
                    event.getWalletId(), actions.size());
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing wallet limit exceeded: walletId={}, topic={}, partition={}, offset={}, error={}",
                    event.getWalletId(), topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Wallet limit exceeded event sent to DLQ: walletId={}, destination={}, category={}",
                        event.getWalletId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet limit exceeded - MESSAGE MAY BE LOST! " +
                            "walletId={}, topic={}, partition={}, offset={}, error={}",
                            event.getWalletId(), topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet limit exceeded event processing failed", e);
        }
    }

    private Wallet validateWallet(String walletId) {
        return walletRepository.findById(UUID.fromString(walletId))
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
    }

    private WalletLimit validateLimit(WalletLimitExceededEvent event) {
        return limitRepository.findByWalletIdAndLimitType(
            event.getWalletId(), event.getLimitType())
            .orElseGet(() -> createDefaultLimit(event));
    }

    private LimitViolation recordViolation(Wallet wallet, WalletLimit limit, WalletLimitExceededEvent event) {
        LimitViolation violation = new LimitViolation();
        violation.setViolationId(UUID.randomUUID().toString());
        violation.setWalletId(wallet.getId());
        violation.setUserId(wallet.getUserId());
        violation.setLimitType(event.getLimitType());
        violation.setLimitAmount(event.getLimitAmount());
        violation.setAttemptedAmount(event.getAttemptedAmount());
        violation.setExcessAmount(event.getAttemptedAmount().subtract(event.getLimitAmount()));
        violation.setTransactionId(event.getTransactionId());
        violation.setViolationTime(LocalDateTime.now());
        violation.setSeverity(calculateSeverity(event));
        violation.setDescription(buildViolationDescription(event));
        
        // Track violation frequency
        int recentViolations = violationRepository.countByWalletIdAndViolationTimeAfter(
            wallet.getId(), LocalDateTime.now().minusHours(24));
        violation.setRecentViolationCount(recentViolations + 1);
        
        return violationRepository.save(violation);
    }

    private ViolationAssessment assessViolation(Wallet wallet, LimitViolation violation, 
                                               WalletLimitExceededEvent event) {
        ViolationAssessment assessment = new ViolationAssessment();
        
        // Check violation patterns
        List<LimitViolation> recentViolations = violationRepository.findByWalletIdAndViolationTimeAfter(
            wallet.getId(), LocalDateTime.now().minusDays(30));
        
        assessment.setViolationCount(recentViolations.size());
        assessment.setRepeatOffender(recentViolations.size() >= hardBlockThreshold);
        assessment.setMaxViolationAmount(calculateMaxViolation(recentViolations));
        
        // Determine severity
        if (violation.getRecentViolationCount() >= hardBlockThreshold) {
            assessment.setSeverity("CRITICAL");
            assessment.setRequiresManualReview(true);
            assessment.setBlockDuration(72); // 72 hours
            assessment.setBlockReason("Multiple limit violations detected");
        } else if (event.getAttemptedAmount().compareTo(event.getLimitAmount().multiply(new BigDecimal("2"))) > 0) {
            assessment.setSeverity("HIGH");
            assessment.setRequiresManualReview(true);
            assessment.setBlockDuration(24); // 24 hours
            assessment.setBlockReason("Attempted amount significantly exceeds limit");
        } else {
            assessment.setSeverity("MEDIUM");
            assessment.setRequiresManualReview(false);
            assessment.setBlockDuration(1); // 1 hour cooling period
            assessment.setBlockReason("Transaction limit exceeded");
        }
        
        // Check for suspicious patterns
        assessment.setSuspiciousPattern(detectSuspiciousPattern(wallet, recentViolations));
        
        return assessment;
    }

    private List<EnforcementAction> applyEnforcement(Wallet wallet, ViolationAssessment assessment, 
                                                    WalletLimitExceededEvent event) {
        List<EnforcementAction> actions = new ArrayList<>();
        
        switch (assessment.getSeverity()) {
            case "CRITICAL":
                // Apply maximum enforcement
                actions.add(freezeWallet(wallet, assessment.getBlockDuration()));
                actions.add(blockAllTransactions(wallet));
                actions.add(requireIdentityReverification(wallet.getUserId()));
                actions.add(escalateToCompliance(wallet, assessment));
                break;
                
            case "HIGH":
                // Apply moderate enforcement
                actions.add(temporarilyRestrictWallet(wallet, assessment.getBlockDuration()));
                actions.add(reduceLimits(wallet, new BigDecimal("0.5"))); // Reduce limits by 50%
                actions.add(requireAdditionalVerification(wallet.getUserId()));
                break;
                
            case "MEDIUM":
                // Apply light enforcement
                actions.add(applyCoolingPeriod(wallet, coolingPeriodHours));
                actions.add(flagForMonitoring(wallet));
                break;
        }
        
        // Log all enforcement actions
        actions.forEach(action -> {
            log.info("Enforcement action applied: {} for wallet {}", action.getType(), wallet.getId());
            kafkaTemplate.send("wallet-enforcement-actions", action);
        });
        
        return actions;
    }

    private void blockTransaction(String transactionId, String reason) {
        if (transactionId != null) {
            transactionService.blockTransaction(transactionId, reason);
            log.info("Transaction {} blocked: {}", transactionId, reason);
        }
    }

    private void updateWalletRestrictions(Wallet wallet, ViolationAssessment assessment, 
                                         List<EnforcementAction> actions) {
        // Update wallet status based on enforcement
        if (assessment.getSeverity().equals("CRITICAL")) {
            wallet.setStatus("FROZEN");
            wallet.setFrozenAt(LocalDateTime.now());
            wallet.setFrozenUntil(LocalDateTime.now().plusHours(assessment.getBlockDuration()));
            wallet.setFreezeReason("LIMIT_VIOLATIONS_CRITICAL");
        } else if (assessment.getSeverity().equals("HIGH")) {
            wallet.setStatus("RESTRICTED");
            wallet.setRestrictedAt(LocalDateTime.now());
            wallet.setRestrictedUntil(LocalDateTime.now().plusHours(assessment.getBlockDuration()));
        }
        
        // Add restriction metadata
        Map<String, Object> restrictions = new HashMap<>();
        restrictions.put("enforcement_actions", actions.size());
        restrictions.put("severity", assessment.getSeverity());
        restrictions.put("manual_review_required", assessment.isRequiresManualReview());
        restrictions.put("restriction_expires", LocalDateTime.now().plusHours(assessment.getBlockDuration()));
        
        wallet.setRestrictions(restrictions);
        walletRepository.save(wallet);
    }

    private void checkFraudIndicators(Wallet wallet, LimitViolation violation, ViolationAssessment assessment) {
        if (assessment.isSuspiciousPattern()) {
            Map<String, Object> fraudIndicators = Map.of(
                "type", "LIMIT_VIOLATION_PATTERN",
                "walletId", wallet.getId(),
                "userId", wallet.getUserId(),
                "violationCount", assessment.getViolationCount(),
                "riskScore", calculateFraudRiskScore(violation, assessment),
                "requiresInvestigation", true
            );
            
            kafkaTemplate.send("fraud-indicators-detected", fraudIndicators);
            
            // Trigger fraud investigation if high risk
            if (calculateFraudRiskScore(violation, assessment) > 0.7) {
                riskService.triggerFraudInvestigation(wallet.getUserId(), "LIMIT_VIOLATION_PATTERN");
            }
        }
    }

    private void sendNotifications(Wallet wallet, LimitViolation violation, 
                                  ViolationAssessment assessment, List<EnforcementAction> actions) {
        // Customer notification
        String message = buildCustomerMessage(violation, assessment, actions);
        notificationService.sendTransactionDeclinedNotification(
            wallet.getUserId(),
            violation.getTransactionId(),
            message,
            assessment.getSeverity()
        );
        
        // Internal alerts for critical violations
        if (assessment.getSeverity().equals("CRITICAL")) {
            notificationService.alertRiskTeam(
                "CRITICAL_LIMIT_VIOLATION",
                wallet.getId(),
                String.format("User %s has %d limit violations in 30 days", 
                    wallet.getUserId(), assessment.getViolationCount())
            );
        }
    }

    private void reportToCompliance(Wallet wallet, LimitViolation violation, 
                                   ViolationAssessment assessment, WalletLimitExceededEvent event) {
        // Report large violations to compliance
        if (event.getAttemptedAmount().compareTo(complianceReportThreshold) > 0) {
            Map<String, Object> complianceReport = Map.of(
                "reportType", "LARGE_LIMIT_VIOLATION",
                "walletId", wallet.getId(),
                "userId", wallet.getUserId(),
                "attemptedAmount", event.getAttemptedAmount(),
                "limitAmount", event.getLimitAmount(),
                "violationType", event.getLimitType(),
                "assessmentSeverity", assessment.getSeverity(),
                "suspiciousPattern", assessment.isSuspiciousPattern()
            );
            
            complianceService.submitComplianceReport(complianceReport);
        }
        
        // Check for regulatory reporting requirements
        if (requiresRegulatoryReporting(violation, assessment)) {
            complianceService.triggerRegulatoryReport(wallet.getUserId(), "TRANSACTION_LIMIT_VIOLATION");
        }
    }

    private void scheduleLimitReview(Wallet wallet, ViolationAssessment assessment) {
        if (assessment.isRequiresManualReview()) {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "LIMIT_VIOLATION_REVIEW",
                "walletId", wallet.getId(),
                "priority", assessment.getSeverity(),
                "dueDate", LocalDateTime.now().plusHours(4),
                "assessment", assessment
            );
            
            kafkaTemplate.send("manual-review-tasks", reviewTask);
        }
        
        // Schedule automatic limit reset if appropriate
        if (!assessment.isRepeatOffender()) {
            Map<String, Object> resetTask = Map.of(
                "walletId", wallet.getId(),
                "resetType", "VIOLATION_COOLDOWN",
                "scheduledFor", LocalDateTime.now().plusHours(assessment.getBlockDuration())
            );
            
            kafkaTemplate.send("scheduled-limit-resets", resetTask);
        }
    }

    private void updateRiskProfile(Wallet wallet, LimitViolation violation, ViolationAssessment assessment) {
        Map<String, Object> riskUpdate = Map.of(
            "userId", wallet.getUserId(),
            "eventType", "LIMIT_VIOLATION",
            "severity", assessment.getSeverity(),
            "violationCount", assessment.getViolationCount(),
            "suspiciousPattern", assessment.isSuspiciousPattern(),
            "riskDelta", calculateRiskDelta(assessment)
        );
        
        riskService.updateUserRiskProfile(wallet.getUserId(), riskUpdate);
    }

    // Helper methods...
    

    @lombok.Data
    private static class ViolationAssessment {
        private String severity;
        private int violationCount;
        private boolean repeatOffender;
        private BigDecimal maxViolationAmount;
        private boolean requiresManualReview;
        private int blockDuration;
        private String blockReason;
        private boolean suspiciousPattern;
    }

    @lombok.Data
    private static class EnforcementAction {
        private String type;
        private String walletId;
        private LocalDateTime appliedAt;
        private Map<String, Object> details;
    }

    // Additional helper method implementations...
}