package com.waqiti.insurance.kafka;

import com.waqiti.common.events.InsurancePremiumPaymentEvent;
import com.waqiti.insurance.domain.PremiumPayment;
import com.waqiti.insurance.repository.PremiumPaymentRepository;
import com.waqiti.insurance.repository.PolicyRepository;
import com.waqiti.insurance.service.PremiumPaymentService;
import com.waqiti.insurance.service.PolicyMaintenanceService;
import com.waqiti.insurance.metrics.InsuranceMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
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
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class InsurancePremiumPaymentEventsConsumer {

    private final PremiumPaymentRepository premiumPaymentRepository;
    private final PolicyRepository policyRepository;
    private final PremiumPaymentService premiumPaymentService;
    private final PolicyMaintenanceService policyMaintenanceService;
    private final InsuranceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("insurance_premium_payment_processed_total")
            .description("Total number of successfully processed insurance premium payment events")
            .register(meterRegistry);
        errorCounter = Counter.builder("insurance_premium_payment_errors_total")
            .description("Total number of insurance premium payment processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("insurance_premium_payment_processing_duration")
            .description("Time taken to process insurance premium payment events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"insurance-premium-payment-events", "premium-collection-workflow", "policy-billing-requests"},
        groupId = "insurance-premium-payment-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "insurance-premium-payment", fallbackMethod = "handleInsurancePremiumPaymentEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInsurancePremiumPaymentEvent(
            @Payload InsurancePremiumPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("premium-payment-%s-p%d-o%d", event.getPolicyId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPolicyId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing premium payment event: policyId={}, paymentId={}, amount={}",
                event.getPolicyId(), event.getPaymentId(), event.getPaymentAmount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case PREMIUM_DUE:
                    processPremiumDue(event, correlationId);
                    break;

                case PAYMENT_RECEIVED:
                    processPaymentReceived(event, correlationId);
                    break;

                case PAYMENT_FAILED:
                    processPaymentFailed(event, correlationId);
                    break;

                case GRACE_PERIOD_INITIATED:
                    initiateGracePeriod(event, correlationId);
                    break;

                case POLICY_LAPSE_WARNING:
                    sendLapseWarning(event, correlationId);
                    break;

                case POLICY_LAPSED:
                    processLapse(event, correlationId);
                    break;

                case REINSTATEMENT_PAYMENT:
                    processReinstatementPayment(event, correlationId);
                    break;

                case POLICY_REINSTATED:
                    reinstatePOlicy(event, correlationId);
                    break;

                case COMMISSION_CALCULATION:
                    calculateCommissions(event, correlationId);
                    break;

                case BILLING_ADJUSTMENT:
                    processBillingAdjustment(event, correlationId);
                    break;

                default:
                    log.warn("Unknown premium payment event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("INSURANCE_PREMIUM_PAYMENT_EVENT_PROCESSED", event.getPolicyId(),
                Map.of("eventType", event.getEventType(), "paymentId", event.getPaymentId(),
                    "paymentAmount", event.getPaymentAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process premium payment event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("insurance-premium-payment-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInsurancePremiumPaymentEventFallback(
            InsurancePremiumPaymentEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("premium-payment-fallback-%s-p%d-o%d", event.getPolicyId(), partition, offset);

        log.error("Circuit breaker fallback triggered for premium payment: policyId={}, error={}",
            event.getPolicyId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("insurance-premium-payment-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Insurance Premium Payment Circuit Breaker Triggered",
                String.format("Premium payment %s failed: %s", event.getPolicyId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInsurancePremiumPaymentEvent(
            @Payload InsurancePremiumPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-premium-payment-%s-%d", event.getPolicyId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Premium payment permanently failed: policyId={}, topic={}, error={}",
            event.getPolicyId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INSURANCE_PREMIUM_PAYMENT_DLT_EVENT", event.getPolicyId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Insurance Premium Payment Dead Letter Event",
                String.format("Premium payment %s sent to DLT: %s", event.getPolicyId(), exceptionMessage),
                Map.of("policyId", event.getPolicyId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processPremiumDue(InsurancePremiumPaymentEvent event, String correlationId) {
        PremiumPayment payment = PremiumPayment.builder()
            .policyId(event.getPolicyId())
            .policyHolderId(event.getPolicyHolderId())
            .premiumAmount(event.getPaymentAmount())
            .dueDate(event.getDueDate())
            .status("DUE")
            .billingPeriod(event.getBillingPeriod())
            .correlationId(correlationId)
            .build();
        premiumPaymentRepository.save(payment);

        premiumPaymentService.generateBill(event.getPolicyId(), event.getPaymentAmount(), event.getDueDate());
        premiumPaymentService.schedulePaymentReminders(event.getPolicyId(), event.getDueDate());

        notificationService.sendNotification(event.getPolicyHolderId(), "Premium Payment Due",
            String.format("Your premium payment of %s is due on %s", event.getPaymentAmount(), event.getDueDate()),
            correlationId);

        kafkaTemplate.send("premium-collection-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "PAYMENT_REMINDER_SCHEDULED",
            "dueDate", event.getDueDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPremiumDue();

        log.info("Premium due processed: policyId={}, amount={}, dueDate={}",
            event.getPolicyId(), event.getPaymentAmount(), event.getDueDate());
    }

    private void processPaymentReceived(InsurancePremiumPaymentEvent event, String correlationId) {
        PremiumPayment payment = premiumPaymentRepository.findByPolicyIdAndDueDate(event.getPolicyId(), event.getDueDate())
            .orElseThrow(() -> new RuntimeException("Premium payment record not found"));

        payment.setStatus("PAID");
        payment.setPaymentId(event.getPaymentId());
        payment.setPaymentDate(event.getPaymentDate());
        payment.setPaymentMethod(event.getPaymentMethod());
        payment.setPaidAt(LocalDateTime.now());
        premiumPaymentRepository.save(payment);

        premiumPaymentService.recordPayment(event.getPolicyId(), event.getPaymentId(),
            event.getPaymentAmount(), event.getPaymentMethod());

        policyMaintenanceService.updatePolicyStatus(event.getPolicyId(), "CURRENT");
        premiumPaymentService.calculateNextPremiumDue(event.getPolicyId(), event.getBillingPeriod());

        // Check if policy was in grace period and reinstate if needed
        boolean wasInGracePeriod = policyMaintenanceService.checkGracePeriodStatus(event.getPolicyId());
        if (wasInGracePeriod) {
            policyMaintenanceService.clearGracePeriod(event.getPolicyId());

            notificationService.sendNotification(event.getPolicyHolderId(), "Policy Current",
                "Thank you for your payment. Your policy is now current and coverage is active.",
                correlationId);
        } else {
            notificationService.sendNotification(event.getPolicyHolderId(), "Payment Received",
                "Thank you for your premium payment. Your policy remains active.",
                correlationId);
        }

        kafkaTemplate.send("premium-collection-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "COMMISSION_CALCULATION",
            "paymentAmount", event.getPaymentAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPaymentReceived(event.getPaymentMethod());

        log.info("Payment received: policyId={}, paymentId={}, amount={}",
            event.getPolicyId(), event.getPaymentId(), event.getPaymentAmount());
    }

    private void processPaymentFailed(InsurancePremiumPaymentEvent event, String correlationId) {
        PremiumPayment payment = premiumPaymentRepository.findByPolicyIdAndDueDate(event.getPolicyId(), event.getDueDate())
            .orElseThrow(() -> new RuntimeException("Premium payment record not found"));

        payment.setStatus("FAILED");
        payment.setPaymentFailureReason(event.getFailureReason());
        payment.setFailedAt(LocalDateTime.now());
        premiumPaymentRepository.save(payment);

        premiumPaymentService.recordFailedPayment(event.getPolicyId(), event.getFailureReason());

        // Determine next action based on policy status
        boolean shouldEnterGracePeriod = policyMaintenanceService.evaluateGracePeriodEligibility(event.getPolicyId());

        if (shouldEnterGracePeriod) {
            kafkaTemplate.send("premium-collection-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "GRACE_PERIOD_INITIATED",
                "failureReason", event.getFailureReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("premium-collection-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "POLICY_LAPSE_WARNING",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendNotification(event.getPolicyHolderId(), "Payment Failed",
            String.format("Your premium payment failed: %s. Please update your payment method.",
                event.getFailureReason()),
            correlationId);

        metricsService.recordPaymentFailed(event.getFailureReason());

        log.warn("Payment failed: policyId={}, reason={}",
            event.getPolicyId(), event.getFailureReason());
    }

    private void initiateGracePeriod(InsurancePremiumPaymentEvent event, String correlationId) {
        var policy = policyRepository.findById(event.getPolicyId()).orElseThrow();
        policy.setStatus("GRACE_PERIOD");
        policy.setGracePeriodStartDate(LocalDateTime.now().toLocalDate());
        policyRepository.save(policy);

        policyMaintenanceService.initiateGracePeriod(event.getPolicyId());
        premiumPaymentService.scheduleGracePeriodReminders(event.getPolicyId());

        notificationService.sendNotification(event.getPolicyHolderId(), "Grace Period Initiated",
            "Your policy has entered a grace period. Please make your payment to avoid cancellation.",
            correlationId);

        // Schedule lapse warning for end of grace period
        kafkaTemplate.send("premium-collection-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "POLICY_LAPSE_WARNING",
            "gracePeriodEnd", policy.getGracePeriodStartDate().plusDays(30), // 30-day grace period
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGracePeriodInitiated();

        log.info("Grace period initiated: policyId={}", event.getPolicyId());
    }

    private void sendLapseWarning(InsurancePremiumPaymentEvent event, String correlationId) {
        premiumPaymentService.sendFinalNotice(event.getPolicyId());

        notificationService.sendCriticalNotification(event.getPolicyHolderId(), "Final Notice - Policy Lapse",
            "URGENT: Your policy will lapse if payment is not received within 72 hours. Contact us immediately.",
            correlationId);

        // Schedule actual lapse if no payment received
        kafkaTemplate.send("premium-collection-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "POLICY_LAPSED",
            "scheduledFor", LocalDateTime.now().plusHours(72),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordLapseWarning();

        log.warn("Lapse warning sent: policyId={}", event.getPolicyId());
    }

    private void processLapse(InsurancePremiumPaymentEvent event, String correlationId) {
        var policy = policyRepository.findById(event.getPolicyId()).orElseThrow();
        policy.setStatus("LAPSED");
        policy.setLapseDate(LocalDateTime.now().toLocalDate());
        policyRepository.save(policy);

        policyMaintenanceService.processLapse(event.getPolicyId());
        premiumPaymentService.suspendCoverageAndCommissions(event.getPolicyId());

        notificationService.sendNotification(event.getPolicyHolderId(), "Policy Lapsed",
            "Your insurance policy has lapsed due to non-payment. Coverage is no longer in effect.",
            correlationId);

        kafkaTemplate.send("insurance-policy-cancellation-events", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "LAPSE_INITIATED",
            "lapseReason", "NON_PAYMENT",
            "effectiveDate", LocalDateTime.now().toLocalDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPolicyLapsed();

        log.warn("Policy lapsed: policyId={}", event.getPolicyId());
    }

    private void processReinstatementPayment(InsurancePremiumPaymentEvent event, String correlationId) {
        premiumPaymentService.processReinstatementPayment(event.getPolicyId(),
            event.getPaymentId(), event.getPaymentAmount());

        kafkaTemplate.send("premium-collection-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "POLICY_REINSTATED",
            "reinstatementPayment", event.getPaymentAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Reinstatement payment processed: policyId={}, amount={}",
            event.getPolicyId(), event.getPaymentAmount());
    }

    private void reinstatePOlicy(InsurancePremiumPaymentEvent event, String correlationId) {
        var policy = policyRepository.findById(event.getPolicyId()).orElseThrow();
        policy.setStatus("ACTIVE");
        policy.setReinstatementDate(LocalDateTime.now().toLocalDate());
        policy.setLapseDate(null);
        policyRepository.save(policy);

        policyMaintenanceService.reinstatePolicy(event.getPolicyId());
        premiumPaymentService.restoreCoverageAndCommissions(event.getPolicyId());

        notificationService.sendNotification(event.getPolicyHolderId(), "Policy Reinstated",
            "Your insurance policy has been reinstated and coverage is now active.",
            correlationId);

        kafkaTemplate.send("insurance-lifecycle-events", Map.of(
            "policyId", event.getPolicyId(),
            "policyHolderId", event.getPolicyHolderId(),
            "eventType", "POLICY_REINSTATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPolicyReinstated();

        log.info("Policy reinstated: policyId={}", event.getPolicyId());
    }

    private void calculateCommissions(InsurancePremiumPaymentEvent event, String correlationId) {
        premiumPaymentService.calculateAgentCommissions(event.getPolicyId(), event.getPaymentAmount());
        premiumPaymentService.updateCommissionAccounting(event.getPolicyId(), event.getPaymentAmount());

        metricsService.recordCommissionCalculated();

        log.info("Commissions calculated: policyId={}, premiumAmount={}",
            event.getPolicyId(), event.getPaymentAmount());
    }

    private void processBillingAdjustment(InsurancePremiumPaymentEvent event, String correlationId) {
        premiumPaymentService.processBillingAdjustment(event.getPolicyId(),
            event.getAdjustmentAmount(), event.getAdjustmentReason());

        notificationService.sendNotification(event.getPolicyHolderId(), "Billing Adjustment",
            String.format("A billing adjustment of %s has been applied to your policy: %s",
                event.getAdjustmentAmount(), event.getAdjustmentReason()),
            correlationId);

        metricsService.recordBillingAdjustment();

        log.info("Billing adjustment processed: policyId={}, amount={}, reason={}",
            event.getPolicyId(), event.getAdjustmentAmount(), event.getAdjustmentReason());
    }
}