package com.waqiti.insurance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.insurance.service.PremiumPaymentService;
import com.waqiti.insurance.service.PolicyMaintenanceService;
import com.waqiti.insurance.service.PaymentProcessingService;
import com.waqiti.insurance.service.BillingService;
import com.waqiti.insurance.service.AuditService;
import com.waqiti.insurance.entity.PremiumPayment;
import com.waqiti.insurance.entity.PolicyAccount;
import com.waqiti.insurance.entity.PaymentTransaction;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Critical Event Consumer #15: Premium Payment Events Consumer
 * Processes premium payments, policy maintenance, and billing operations
 * Implements 12-step zero-tolerance processing for premium payment management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PremiumPaymentEventsConsumer extends BaseKafkaConsumer {

    private final PremiumPaymentService premiumPaymentService;
    private final PolicyMaintenanceService policyMaintenanceService;
    private final PaymentProcessingService paymentProcessingService;
    private final BillingService billingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "premium-payment-events", 
        groupId = "premium-payment-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "premium-payment-consumer")
    @Retry(name = "premium-payment-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePremiumPaymentEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "premium-payment-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing premium payment event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String paymentId = eventData.path("paymentId").asText();
            String policyId = eventData.path("policyId").asText();
            String policyholderCustomerId = eventData.path("policyholderCustomerId").asText();
            BigDecimal paymentAmount = new BigDecimal(eventData.path("paymentAmount").asText());
            LocalDate paymentDueDate = LocalDate.parse(eventData.path("paymentDueDate").asText());
            LocalDateTime paymentDate = LocalDateTime.parse(eventData.path("paymentDate").asText());
            String paymentMethod = eventData.path("paymentMethod").asText(); // CREDIT_CARD, BANK_TRANSFER, CHECK
            String paymentType = eventData.path("paymentType").asText(); // PREMIUM, LATE_FEE, REINSTATEMENT
            String currency = eventData.path("currency").asText();
            String paymentStatus = eventData.path("paymentStatus").asText(); // SUCCESSFUL, FAILED, PENDING
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted payment details: paymentId={}, policyId={}, amount={} {}, status={}", 
                    paymentId, policyId, paymentAmount, currency, paymentStatus);
            
            // Step 3: Payment validation and policy verification
            log.info("Step 3: Validating payment details and verifying policy status");
            PremiumPayment payment = premiumPaymentService.createPremiumPayment(eventData);
            
            premiumPaymentService.validatePaymentAmount(paymentAmount);
            premiumPaymentService.validatePolicyStatus(policyId);
            premiumPaymentService.validatePolicyholder(policyholderCustomerId, policyId);
            premiumPaymentService.validatePaymentMethod(paymentMethod);
            
            if (!premiumPaymentService.isValidPaymentType(paymentType)) {
                throw new IllegalStateException("Invalid payment type: " + paymentType);
            }
            
            premiumPaymentService.checkPaymentDuplication(paymentId, policyId);
            
            // Step 4: Policy account and billing reconciliation
            log.info("Step 4: Reconciling policy account and billing information");
            PolicyAccount policyAccount = billingService.getPolicyAccount(policyId);
            
            billingService.validateAccountBalance(policyAccount);
            billingService.reconcileOutstandingPremiums(policyAccount);
            billingService.calculatePaymentAllocation(payment, policyAccount);
            billingService.validateBillingPeriod(payment, policyAccount);
            
            BigDecimal outstandingBalance = billingService.getOutstandingBalance(policyAccount);
            billingService.prioritizePaymentApplication(payment, outstandingBalance);
            
            // Step 5: Payment processing and transaction handling
            log.info("Step 5: Processing payment transaction and handling payment methods");
            PaymentTransaction transaction = paymentProcessingService.createPaymentTransaction(payment);
            
            if ("SUCCESSFUL".equals(paymentStatus)) {
                paymentProcessingService.processSuccessfulPayment(transaction);
                paymentProcessingService.validatePaymentAuthenticity(transaction);
                paymentProcessingService.recordPaymentDetails(transaction);
            } else if ("FAILED".equals(paymentStatus)) {
                paymentProcessingService.handleFailedPayment(transaction);
                paymentProcessingService.analyzeFailureReason(transaction);
                paymentProcessingService.schedulePaymentRetry(transaction);
            }
            
            paymentProcessingService.updateTransactionStatus(transaction);
            
            // Step 6: Premium allocation and account posting
            log.info("Step 6: Allocating premium payments and posting to policy account");
            if ("SUCCESSFUL".equals(paymentStatus)) {
                premiumPaymentService.allocatePremiumPayment(payment, policyAccount);
                premiumPaymentService.applyPaymentToBalance(payment, policyAccount);
                premiumPaymentService.updateAccountLedger(payment, policyAccount);
                
                if ("LATE_FEE".equals(paymentType)) {
                    premiumPaymentService.processLateFeePayment(payment, policyAccount);
                } else if ("REINSTATEMENT".equals(paymentType)) {
                    premiumPaymentService.processReinstatementPayment(payment, policyAccount);
                }
                
                premiumPaymentService.recalculateAccountBalance(policyAccount);
            }
            
            // Step 7: Policy status and coverage maintenance
            log.info("Step 7: Maintaining policy status and coverage continuity");
            policyMaintenanceService.updatePolicyStatus(policyId, payment);
            policyMaintenanceService.validateCoverageContinuity(policyId);
            policyMaintenanceService.checkGracePeriodStatus(policyId);
            
            if (policyMaintenanceService.isPolicyLapsed(policyId)) {
                policyMaintenanceService.processReinstatement(policyId, payment);
                policyMaintenanceService.restoreCoverage(policyId);
            }
            
            policyMaintenanceService.updatePolicyExpirationDate(policyId, payment);
            
            // Step 8: Late payment handling and penalty processing
            log.info("Step 8: Processing late payments and penalty assessments");
            if (paymentDate.toLocalDate().isAfter(paymentDueDate)) {
                premiumPaymentService.assessLatePaymentPenalty(payment);
                premiumPaymentService.calculateLateFees(payment, policyAccount);
                premiumPaymentService.updateGracePeriodStatus(policyId);
                
                if (premiumPaymentService.exceedsGracePeriod(policyId, paymentDate)) {
                    policyMaintenanceService.initiateLapseProcess(policyId);
                }
            }
            
            premiumPaymentService.updatePaymentHistory(payment);
            
            // Step 9: Automatic payment and recurring billing management
            log.info("Step 9: Managing automatic payments and recurring billing");
            if (premiumPaymentService.hasAutomaticPayment(policyId)) {
                premiumPaymentService.validateAutomaticPaymentSetup(policyId);
                premiumPaymentService.scheduleNextAutomaticPayment(policyId);
                premiumPaymentService.updatePaymentMethodStatus(policyId);
            }
            
            billingService.generateNextBillingStatement(policyAccount);
            billingService.updateBillingCycle(policyAccount, payment);
            
            // Step 10: Commission and agent compensation
            log.info("Step 10: Processing commission calculations and agent compensation");
            premiumPaymentService.calculateAgentCommission(payment);
            premiumPaymentService.processCommissionPayments(payment);
            premiumPaymentService.updateCommissionAccounting(payment);
            
            if (premiumPaymentService.hasRenewalCommission(payment)) {
                premiumPaymentService.processRenewalCommission(payment);
            }
            
            // Step 11: Customer communication and service
            log.info("Step 11: Managing customer communications and service updates");
            premiumPaymentService.generatePaymentConfirmation(payment);
            premiumPaymentService.updateCustomerAccount(payment);
            premiumPaymentService.sendPaymentNotification(payment);
            
            if ("FAILED".equals(paymentStatus)) {
                premiumPaymentService.sendPaymentFailureNotification(payment);
                premiumPaymentService.providePaymentAlternatives(payment);
            }
            
            premiumPaymentService.updateCustomerPaymentHistory(payment);
            
            // Step 12: Audit trail and regulatory compliance
            log.info("Step 12: Completing audit trail and ensuring regulatory compliance");
            auditService.logPremiumPayment(payment);
            auditService.logPolicyAccount(policyAccount);
            auditService.logPaymentTransaction(transaction);
            
            premiumPaymentService.updatePaymentMetrics(payment);
            billingService.updateBillingStatistics(policyAccount);
            paymentProcessingService.updateTransactionMetrics(transaction);
            
            auditService.generatePaymentReport(payment);
            auditService.updateRegulatoryReporting(payment);
            
            billingService.archivePaymentDocuments(payment);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed premium payment: paymentId={}, eventId={}, amount={} {}", 
                    paymentId, eventId, paymentAmount, currency);
            
        } catch (Exception e) {
            log.error("Error processing premium payment event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("paymentId") || 
            !eventData.has("policyId") || !eventData.has("policyholderCustomerId") ||
            !eventData.has("paymentAmount") || !eventData.has("paymentDueDate") ||
            !eventData.has("paymentDate") || !eventData.has("paymentMethod") ||
            !eventData.has("paymentType") || !eventData.has("currency") ||
            !eventData.has("paymentStatus") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid premium payment event structure");
        }
    }
}