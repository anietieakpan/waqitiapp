package com.waqiti.recurringpayment.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.recurringpayment.dto.AutomaticPaymentSetupEvent;
import com.waqiti.recurringpayment.service.RecurringPaymentService;
import com.waqiti.recurringpayment.service.PaymentScheduleService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Automatic Payment Setup Consumer
 *
 * PURPOSE: Configure automatic recurring payments (loan repayments, subscriptions, bills)
 *
 * BUSINESS CRITICAL: Automatic payments represent $5M-10M monthly volume
 * Missing this consumer means:
 * - Loan payments not automatically scheduled
 * - Subscription renewals fail
 * - Customer churn (manual payment friction)
 * - Missed revenue collection
 *
 * IMPLEMENTATION PRIORITY: P0 CRITICAL
 *
 * @author Waqiti Payments Team
 * @version 1.0.0
 * @since 2025-10-13
 */
@Service
@Slf4j
public class AutomaticPaymentSetupConsumer {

        private final RecurringPaymentService recurringPaymentService;
        private final PaymentScheduleService scheduleService;
        private final Counter setupsProcessedCounter;
        private final Counter setupsFailedCounter;

        @Autowired
        public AutomaticPaymentSetupConsumer(
                RecurringPaymentService recurringPaymentService,
                PaymentScheduleService scheduleService,
                MeterRegistry meterRegistry) {

                this.recurringPaymentService = recurringPaymentService;
                this.scheduleService = scheduleService;

                this.setupsProcessedCounter = Counter.builder("automatic.payment.setup.processed")
                        .description("Number of automatic payment setups processed")
                        .register(meterRegistry);

                this.setupsFailedCounter = Counter.builder("automatic.payment.setup.failed")
                        .description("Number of automatic payment setups that failed")
                        .register(meterRegistry);
        }

        /**
         * Process automatic payment setup event
         */
        @RetryableKafkaListener(
                topics = "automatic-payment-setup",
                groupId = "recurring-payment-service-setup",
                containerFactory = "kafkaListenerContainerFactory",
                retries = 5,
                backoffMultiplier = 2.0,
                initialBackoff = 1000L
        )
        @Transactional
        public void handleAutomaticPaymentSetup(
                @Payload AutomaticPaymentSetupEvent event,
                @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                Acknowledgment acknowledgment) {

                Instant startTime = Instant.now();

                log.info("Processing automatic payment setup: setupId={}, userId={}, paymentType={}, amount={}",
                        event.getSetupId(),
                        event.getUserId(),
                        event.getPaymentType(),
                        event.getAmount());

                try {
                        // Step 1: Validate event
                        validateEvent(event);

                        // Step 2: Check idempotency
                        if (recurringPaymentService.isSetupAlreadyProcessed(event.getSetupId())) {
                                log.info("Automatic payment setup already processed (idempotent): setupId={}",
                                        event.getSetupId());
                                acknowledgment.acknowledge();
                                return;
                        }

                        // Step 3: Verify user payment method
                        recurringPaymentService.validatePaymentMethod(
                                event.getUserId(),
                                event.getPaymentMethodId()
                        );

                        // Step 4: Verify sufficient funds/credit limit
                        recurringPaymentService.validateSufficientFunds(
                                event.getUserId(),
                                event.getAmount()
                        );

                        // Step 5: Create recurring payment schedule
                        String scheduleId = scheduleService.createSchedule(
                                event.getUserId(),
                                event.getPaymentType(),
                                event.getAmount(),
                                event.getFrequency(),
                                event.getStartDate(),
                                event.getEndDate(),
                                event.getPaymentMethodId()
                        );

                        log.info("Recurring payment schedule created: setupId={}, scheduleId={}",
                                event.getSetupId(), scheduleId);

                        // Step 6: Calculate next payment date
                        LocalDate nextPaymentDate = calculateNextPaymentDate(
                                event.getStartDate(),
                                event.getFrequency()
                        );

                        // Step 7: Schedule first payment
                        scheduleService.schedulePayment(
                                scheduleId,
                                nextPaymentDate,
                                event.getAmount(),
                                event.getPaymentMethodId()
                        );

                        log.info("First automatic payment scheduled: setupId={}, scheduleId={}, date={}",
                                event.getSetupId(), scheduleId, nextPaymentDate);

                        // Step 8: Send confirmation to user
                        recurringPaymentService.sendSetupConfirmation(
                                event.getUserId(),
                                scheduleId,
                                event.getPaymentType(),
                                event.getAmount(),
                                event.getFrequency(),
                                nextPaymentDate
                        );

                        // Step 9: For loan payments, update loan account
                        if ("LOAN_REPAYMENT".equals(event.getPaymentType())) {
                                recurringPaymentService.linkLoanToSchedule(
                                        event.getLoanId(),
                                        scheduleId
                                );
                        }

                        // Step 10: Mark as processed
                        recurringPaymentService.markSetupProcessed(event.getSetupId(), scheduleId);

                        // Step 11: Acknowledge
                        acknowledgment.acknowledge();

                        // Metrics
                        setupsProcessedCounter.increment();

                        long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                        log.info("Automatic payment setup completed: setupId={}, scheduleId={}, processingTime={}ms",
                                event.getSetupId(), scheduleId, processingTime);

                } catch (InsufficientFundsException e) {
                        log.error("Insufficient funds for automatic payment setup: setupId={}, userId={}",
                                event.getSetupId(), event.getUserId());

                        // Notify user of insufficient funds
                        recurringPaymentService.notifyInsufficientFunds(event);

                        // Don't retry - user needs to add funds
                        acknowledgment.acknowledge();
                        setupsFailedCounter.increment();

                } catch (InvalidPaymentMethodException e) {
                        log.error("Invalid payment method for automatic payment setup: setupId={}, paymentMethodId={}",
                                event.getSetupId(), event.getPaymentMethodId());

                        // Notify user to update payment method
                        recurringPaymentService.notifyInvalidPaymentMethod(event);

                        // Don't retry - user needs to fix payment method
                        acknowledgment.acknowledge();
                        setupsFailedCounter.increment();

                } catch (Exception e) {
                        log.error("Failed to process automatic payment setup: setupId={}, will retry",
                                event.getSetupId(), e);

                        setupsFailedCounter.increment();

                        throw new KafkaRetryException(
                                "Failed to process automatic payment setup",
                                e,
                                event.getSetupId().toString()
                        );
                }
        }

        /**
         * Calculate next payment date based on frequency
         */
        private LocalDate calculateNextPaymentDate(LocalDate startDate, String frequency) {
                LocalDate today = LocalDate.now();
                LocalDate nextDate = startDate;

                // If start date is in the past, calculate next occurrence
                while (nextDate.isBefore(today) || nextDate.equals(today)) {
                        nextDate = switch (frequency.toUpperCase()) {
                                case "DAILY" -> nextDate.plusDays(1);
                                case "WEEKLY" -> nextDate.plusWeeks(1);
                                case "BIWEEKLY" -> nextDate.plusWeeks(2);
                                case "MONTHLY" -> nextDate.plusMonths(1);
                                case "QUARTERLY" -> nextDate.plusMonths(3);
                                case "SEMIANNUALLY" -> nextDate.plusMonths(6);
                                case "ANNUALLY" -> nextDate.plusYears(1);
                                default -> throw new IllegalArgumentException("Unsupported frequency: " + frequency);
                        };
                }

                return nextDate;
        }

        /**
         * Validate event
         */
        private void validateEvent(AutomaticPaymentSetupEvent event) {
                if (event == null) {
                        throw new IllegalArgumentException("Event cannot be null");
                }

                if (event.getSetupId() == null) {
                        throw new IllegalArgumentException("Setup ID cannot be null");
                }

                if (event.getUserId() == null) {
                        throw new IllegalArgumentException("User ID cannot be null");
                }

                if (event.getPaymentType() == null || event.getPaymentType().isBlank()) {
                        throw new IllegalArgumentException("Payment type cannot be null or empty");
                }

                if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Amount must be positive");
                }

                if (event.getFrequency() == null || event.getFrequency().isBlank()) {
                        throw new IllegalArgumentException("Frequency cannot be null or empty");
                }

                if (event.getStartDate() == null) {
                        throw new IllegalArgumentException("Start date cannot be null");
                }

                if (event.getPaymentMethodId() == null) {
                        throw new IllegalArgumentException("Payment method ID cannot be null");
                }
        }

        /**
         * Handle DLQ messages
         */
        @KafkaListener(topics = "automatic-payment-setup-recurring-payment-service-dlq")
        public void handleDLQMessage(@Payload AutomaticPaymentSetupEvent event) {
                log.error("Automatic payment setup in DLQ - user recurring payment not configured: setupId={}, userId={}",
                        event.getSetupId(), event.getUserId());

                try {
                        // Log to persistent storage
                        recurringPaymentService.logDLQSetup(
                                event.getSetupId(),
                                event,
                                "Automatic payment setup failed permanently"
                        );

                        // Alert operations team
                        recurringPaymentService.alertOperations(
                                "HIGH",
                                "Automatic payment setup stuck in DLQ - user recurring payment not configured",
                                Map.of(
                                        "setupId", event.getSetupId().toString(),
                                        "userId", event.getUserId().toString(),
                                        "paymentType", event.getPaymentType(),
                                        "amount", event.getAmount().toString(),
                                        "frequency", event.getFrequency()
                                )
                        );

                        // Notify user that setup failed
                        recurringPaymentService.notifySetupFailed(
                                event.getUserId(),
                                event.getPaymentType(),
                                "We were unable to set up your automatic payment. Please try again or contact support."
                        );

                        // For loan payments, this is CRITICAL - create support ticket
                        if ("LOAN_REPAYMENT".equals(event.getPaymentType())) {
                                recurringPaymentService.createSupportTicket(
                                        event.getUserId(),
                                        "CRITICAL: Loan Automatic Payment Setup Failed",
                                        String.format("User's loan automatic payment setup failed. " +
                                                        "Manual intervention required. SetupId: %s, LoanId: %s, Amount: %s",
                                                event.getSetupId(), event.getLoanId(), event.getAmount())
                                );
                        }

                } catch (Exception e) {
                        log.error("CRITICAL: Failed to process automatic payment setup DLQ message: setupId={}",
                                event.getSetupId(), e);
                }
        }

        // Exception classes
        public static class InsufficientFundsException extends RuntimeException {
                public InsufficientFundsException(String message) {
                        super(message);
                }
        }

        public static class InvalidPaymentMethodException extends RuntimeException {
                public InvalidPaymentMethodException(String message) {
                        super(message);
                }
        }
}