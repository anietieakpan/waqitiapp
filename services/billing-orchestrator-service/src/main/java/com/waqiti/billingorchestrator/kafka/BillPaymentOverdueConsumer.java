package com.waqiti.billingorchestrator.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.billingorchestrator.domain.Bill;
import com.waqiti.billingorchestrator.domain.BillStatus;
import com.waqiti.billingorchestrator.domain.LateFee;
import com.waqiti.billingorchestrator.repository.BillRepository;
import com.waqiti.billingorchestrator.repository.LateFeeRepository;
import com.waqiti.billingorchestrator.service.BillingNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #14: BillPaymentOverdueConsumer
 * Applies late fees when bills become overdue
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillPaymentOverdueConsumer {
    private final BillRepository billRepository;
    private final LateFeeRepository lateFeeRepository;
    private final BillingNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "bill.payment.overdue", groupId = "billing-overdue-processor")
    @Transactional
    public void handle(BillOverdueEvent event, Acknowledgment ack) {
        try {
            log.warn("⏰ BILL OVERDUE: billId={}, userId={}, amount=${}, daysOverdue={}",
                event.getBillId(), event.getUserId(), event.getAmount(), event.getDaysOverdue());

            String key = "bill:overdue:" + event.getBillId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            Bill bill = billRepository.findById(event.getBillId())
                .orElseThrow(() -> new BusinessException("Bill not found"));

            if (bill.getStatus() != BillStatus.UNPAID) {
                log.warn("Bill {} already processed - status: {}", event.getBillId(), bill.getStatus());
                ack.acknowledge();
                return;
            }

            // Calculate late fee
            BigDecimal lateFeeAmount = calculateLateFee(event.getAmount(), event.getDaysOverdue());

            // Create late fee record
            LateFee lateFee = LateFee.builder()
                .id(UUID.randomUUID())
                .billId(event.getBillId())
                .userId(event.getUserId())
                .originalAmount(event.getAmount())
                .lateFeeAmount(lateFeeAmount)
                .daysOverdue(event.getDaysOverdue())
                .appliedAt(LocalDateTime.now())
                .build();

            lateFeeRepository.save(lateFee);

            // Update bill
            bill.setStatus(BillStatus.OVERDUE);
            bill.setLateFee(lateFeeAmount);
            bill.setTotalAmountDue(event.getAmount().add(lateFeeAmount));
            billRepository.save(bill);

            log.error("💰 LATE FEE APPLIED: billId={}, lateFee=${}, newTotal=${}",
                event.getBillId(), lateFeeAmount, bill.getTotalAmountDue());

            // Notify user
            String message = String.format("""
                Your bill payment is overdue.

                Original Amount: $%s
                Days Overdue: %d
                Late Fee: $%s
                New Total Due: $%s

                Merchant: %s
                Due Date: %s

                Please pay immediately to avoid additional fees.
                """,
                event.getAmount(),
                event.getDaysOverdue(),
                lateFeeAmount,
                bill.getTotalAmountDue(),
                event.getMerchantName(),
                event.getDueDate());

            notificationService.sendOverdueNotification(event.getUserId(), event.getMerchantId(),
                event.getBillId(), bill.getTotalAmountDue(), message);

            metricsCollector.incrementCounter("billing.late.fee.applied");
            metricsCollector.recordGauge("billing.late.fee.amount", lateFeeAmount.doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process bill overdue event", e);
            dlqHandler.sendToDLQ("bill.payment.overdue", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private BigDecimal calculateLateFee(BigDecimal amount, int daysOverdue) {
        // $5 flat fee + 1% of amount per day (max 10%)
        BigDecimal flatFee = new BigDecimal("5.00");
        BigDecimal percentageFee = amount.multiply(new BigDecimal("0.01")).multiply(BigDecimal.valueOf(daysOverdue));
        BigDecimal maxPercentageFee = amount.multiply(new BigDecimal("0.10"));

        return flatFee.add(percentageFee.min(maxPercentageFee));
    }

    private static class BillOverdueEvent {
        private UUID billId, userId, merchantId;
        private String merchantName;
        private BigDecimal amount;
        private int daysOverdue;
        private LocalDateTime dueDate;
        public UUID getBillId() { return billId; }
        public UUID getUserId() { return userId; }
        public UUID getMerchantId() { return merchantId; }
        public String getMerchantName() { return merchantName; }
        public BigDecimal getAmount() { return amount; }
        public int getDaysOverdue() { return daysOverdue; }
        public LocalDateTime getDueDate() { return dueDate; }
    }
}
