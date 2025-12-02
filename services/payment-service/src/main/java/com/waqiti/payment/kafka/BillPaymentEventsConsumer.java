package com.waqiti.payment.kafka;

import com.waqiti.common.events.BillPaymentEvent;
import com.waqiti.payment.domain.BillPayment;
import com.waqiti.payment.domain.RecurringBillSetup;
import com.waqiti.payment.repository.BillPaymentRepository;
import com.waqiti.payment.repository.RecurringBillSetupRepository;
import com.waqiti.payment.service.BillPaymentService;
import com.waqiti.payment.service.BillerIntegrationService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class BillPaymentEventsConsumer {
    
    private final BillPaymentRepository billPaymentRepository;
    private final RecurringBillSetupRepository recurringBillRepository;
    private final BillPaymentService billPaymentService;
    private final BillerIntegrationService billerService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"bill-payment-events", "bill-pay-scheduling", "recurring-bill-events"},
        groupId = "payment-billpay-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleBillPaymentEvent(
            @Payload BillPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("billpay-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing bill payment event: paymentId={}, type={}, biller={}", 
            event.getPaymentId(), event.getEventType(), event.getBillerName());
        
        try {
            switch (event.getEventType()) {
                case BILL_PAYMENT_SCHEDULED:
                    processBillPaymentScheduled(event, correlationId);
                    break;
                case BILL_PAYMENT_INITIATED:
                    processBillPaymentInitiated(event, correlationId);
                    break;
                case BILL_PAYMENT_PROCESSING:
                    processBillPaymentProcessing(event, correlationId);
                    break;
                case BILL_PAYMENT_COMPLETED:
                    processBillPaymentCompleted(event, correlationId);
                    break;
                case BILL_PAYMENT_FAILED:
                    processBillPaymentFailed(event, correlationId);
                    break;
                case RECURRING_BILL_SETUP:
                    processRecurringBillSetup(event, correlationId);
                    break;
                case RECURRING_BILL_EXECUTED:
                    processRecurringBillExecuted(event, correlationId);
                    break;
                case RECURRING_BILL_CANCELLED:
                    processRecurringBillCancelled(event, correlationId);
                    break;
                case BILL_DUE_REMINDER:
                    processBillDueReminder(event, correlationId);
                    break;
                case BILLER_CONFIRMATION_RECEIVED:
                    processBillerConfirmationReceived(event, correlationId);
                    break;
                default:
                    log.warn("Unknown bill payment event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logPaymentEvent(
                "BILL_PAYMENT_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "biller", event.getBillerName(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process bill payment event: {}", e.getMessage(), e);
            kafkaTemplate.send("bill-payment-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processBillPaymentScheduled(BillPaymentEvent event, String correlationId) {
        log.info("Bill payment scheduled: paymentId={}, biller={}, scheduledDate={}, amount={}", 
            event.getPaymentId(), event.getBillerName(), event.getScheduledDate(), event.getAmount());
        
        BillPayment payment = BillPayment.builder()
            .id(event.getPaymentId())
            .userId(event.getUserId())
            .billerName(event.getBillerName())
            .billerId(event.getBillerId())
            .accountNumber(event.getAccountNumber())
            .amount(event.getAmount())
            .scheduledDate(event.getScheduledDate())
            .status("SCHEDULED")
            .scheduledAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        billPaymentRepository.save(payment);
        billPaymentService.schedulePayment(event.getPaymentId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Bill Payment Scheduled",
            String.format("Payment of %.2f to %s scheduled for %s", 
                event.getAmount(), event.getBillerName(), event.getScheduledDate()),
            correlationId
        );
        
        metricsService.recordBillPaymentScheduled(event.getBillerName());
    }
    
    private void processBillPaymentInitiated(BillPaymentEvent event, String correlationId) {
        log.info("Bill payment initiated: paymentId={}, biller={}", 
            event.getPaymentId(), event.getBillerName());
        
        BillPayment payment = billPaymentRepository.findById(event.getPaymentId())
            .orElseThrow();
        
        payment.setStatus("INITIATED");
        payment.setInitiatedAt(LocalDateTime.now());
        billPaymentRepository.save(payment);
        
        billerService.submitPayment(event.getPaymentId());
        metricsService.recordBillPaymentInitiated(event.getBillerName());
    }
    
    private void processBillPaymentProcessing(BillPaymentEvent event, String correlationId) {
        log.info("Bill payment processing: paymentId={}", event.getPaymentId());
        
        BillPayment payment = billPaymentRepository.findById(event.getPaymentId())
            .orElseThrow();
        
        payment.setStatus("PROCESSING");
        payment.setProcessingStartedAt(LocalDateTime.now());
        billPaymentRepository.save(payment);
        
        metricsService.recordBillPaymentProcessing();
    }
    
    private void processBillPaymentCompleted(BillPaymentEvent event, String correlationId) {
        log.info("Bill payment completed: paymentId={}, confirmationNumber={}", 
            event.getPaymentId(), event.getConfirmationNumber());
        
        BillPayment payment = billPaymentRepository.findById(event.getPaymentId())
            .orElseThrow();
        
        payment.setStatus("COMPLETED");
        payment.setCompletedAt(LocalDateTime.now());
        payment.setConfirmationNumber(event.getConfirmationNumber());
        billPaymentRepository.save(payment);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Bill Payment Complete",
            String.format("Payment of %.2f to %s completed. Confirmation: %s", 
                event.getAmount(), event.getBillerName(), event.getConfirmationNumber()),
            correlationId
        );
        
        metricsService.recordBillPaymentCompleted(event.getBillerName(), event.getAmount());
    }
    
    private void processBillPaymentFailed(BillPaymentEvent event, String correlationId) {
        log.error("Bill payment failed: paymentId={}, reason={}", 
            event.getPaymentId(), event.getFailureReason());
        
        BillPayment payment = billPaymentRepository.findById(event.getPaymentId())
            .orElseThrow();
        
        payment.setStatus("FAILED");
        payment.setFailedAt(LocalDateTime.now());
        payment.setFailureReason(event.getFailureReason());
        billPaymentRepository.save(payment);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Bill Payment Failed",
            String.format("Payment to %s failed: %s. Please try again.", 
                event.getBillerName(), event.getFailureReason()),
            correlationId
        );
        
        metricsService.recordBillPaymentFailed(event.getBillerName(), event.getFailureReason());
    }
    
    private void processRecurringBillSetup(BillPaymentEvent event, String correlationId) {
        log.info("Recurring bill setup: setupId={}, biller={}, frequency={}", 
            event.getRecurringSetupId(), event.getBillerName(), event.getFrequency());
        
        RecurringBillSetup setup = RecurringBillSetup.builder()
            .id(event.getRecurringSetupId())
            .userId(event.getUserId())
            .billerName(event.getBillerName())
            .billerId(event.getBillerId())
            .accountNumber(event.getAccountNumber())
            .amount(event.getAmount())
            .frequency(event.getFrequency())
            .startDate(event.getStartDate())
            .nextPaymentDate(event.getNextPaymentDate())
            .status("ACTIVE")
            .setupAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        recurringBillRepository.save(setup);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Recurring Bill Payment Setup",
            String.format("Recurring payment of %.2f to %s set up. Next payment: %s", 
                event.getAmount(), event.getBillerName(), event.getNextPaymentDate()),
            correlationId
        );
        
        metricsService.recordRecurringBillSetup(event.getBillerName(), event.getFrequency());
    }
    
    private void processRecurringBillExecuted(BillPaymentEvent event, String correlationId) {
        log.info("Recurring bill executed: setupId={}, paymentId={}", 
            event.getRecurringSetupId(), event.getPaymentId());
        
        RecurringBillSetup setup = recurringBillRepository.findById(event.getRecurringSetupId())
            .orElseThrow();
        
        setup.setLastPaymentDate(LocalDateTime.now());
        setup.setNextPaymentDate(calculateNextPaymentDate(setup.getFrequency()));
        setup.setPaymentCount(setup.getPaymentCount() + 1);
        recurringBillRepository.save(setup);
        
        metricsService.recordRecurringBillExecuted(event.getBillerName());
    }
    
    private void processRecurringBillCancelled(BillPaymentEvent event, String correlationId) {
        log.info("Recurring bill cancelled: setupId={}", event.getRecurringSetupId());
        
        RecurringBillSetup setup = recurringBillRepository.findById(event.getRecurringSetupId())
            .orElseThrow();
        
        setup.setStatus("CANCELLED");
        setup.setCancelledAt(LocalDateTime.now());
        recurringBillRepository.save(setup);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Recurring Bill Payment Cancelled",
            String.format("Recurring payment to %s has been cancelled.", event.getBillerName()),
            correlationId
        );
        
        metricsService.recordRecurringBillCancelled(event.getBillerName());
    }
    
    private void processBillDueReminder(BillPaymentEvent event, String correlationId) {
        log.info("Bill due reminder: biller={}, dueDate={}, daysUntilDue={}", 
            event.getBillerName(), event.getDueDate(), event.getDaysUntilDue());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Bill Payment Reminder",
            String.format("Your %s bill of %.2f is due in %d days (%s)", 
                event.getBillerName(), event.getAmount(), 
                event.getDaysUntilDue(), event.getDueDate()),
            correlationId
        );
        
        metricsService.recordBillDueReminder(event.getDaysUntilDue());
    }
    
    private void processBillerConfirmationReceived(BillPaymentEvent event, String correlationId) {
        log.info("Biller confirmation received: paymentId={}, confirmationNumber={}", 
            event.getPaymentId(), event.getConfirmationNumber());
        
        BillPayment payment = billPaymentRepository.findById(event.getPaymentId())
            .orElseThrow();
        
        payment.setBillerConfirmationReceived(true);
        payment.setBillerConfirmationAt(LocalDateTime.now());
        payment.setBillerConfirmationNumber(event.getConfirmationNumber());
        billPaymentRepository.save(payment);
        
        metricsService.recordBillerConfirmation();
    }
    
    private LocalDateTime calculateNextPaymentDate(String frequency) {
        LocalDateTime now = LocalDateTime.now();
        return switch (frequency) {
            case "WEEKLY" -> now.plusWeeks(1);
            case "BI_WEEKLY" -> now.plusWeeks(2);
            case "MONTHLY" -> now.plusMonths(1);
            case "QUARTERLY" -> now.plusMonths(3);
            case "SEMI_ANNUAL" -> now.plusMonths(6);
            case "ANNUAL" -> now.plusYears(1);
            default -> now.plusMonths(1);
        };
    }
}