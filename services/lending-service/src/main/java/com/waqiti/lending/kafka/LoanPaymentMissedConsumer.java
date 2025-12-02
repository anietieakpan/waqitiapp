package com.waqiti.lending.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanStatus;
import com.waqiti.lending.domain.MissedPayment;
import com.waqiti.lending.repository.LoanRepository;
import com.waqiti.lending.repository.MissedPaymentRepository;
import com.waqiti.lending.service.LoanNotificationService;
import com.waqiti.lending.service.CreditReportingService;
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
 * CRITICAL FIX #24: LoanPaymentMissedConsumer
 * Notifies borrowers of missed loan payments and applies late fees
 * Impact: Reduces default rate, improves credit reporting compliance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentMissedConsumer {
    private final LoanRepository loanRepository;
    private final MissedPaymentRepository missedPaymentRepository;
    private final LoanNotificationService notificationService;
    private final CreditReportingService creditReportingService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    private static final BigDecimal LATE_FEE_AMOUNT = new BigDecimal("25.00");
    private static final int DAYS_BEFORE_CREDIT_REPORT = 30;

    @KafkaListener(topics = "loan.payment.missed", groupId = "loan-missed-payment-processor")
    @Transactional
    public void handle(LoanPaymentMissedEvent event, Acknowledgment ack) {
        try {
            log.warn("⚠️ LOAN PAYMENT MISSED: loanId={}, userId={}, amount=${}, daysOverdue={}",
                event.getLoanId(), event.getUserId(), event.getPaymentAmount(), event.getDaysOverdue());

            String key = "loan:payment:missed:" + event.getLoanId() + ":" + event.getPaymentDueDate();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            Loan loan = loanRepository.findById(event.getLoanId())
                .orElseThrow(() -> new BusinessException("Loan not found"));

            if (loan.getStatus() == LoanStatus.PAID_OFF || loan.getStatus() == LoanStatus.CLOSED) {
                log.warn("Loan {} already paid off or closed", event.getLoanId());
                ack.acknowledge();
                return;
            }

            // Record missed payment
            MissedPayment missedPayment = MissedPayment.builder()
                .id(UUID.randomUUID())
                .loanId(event.getLoanId())
                .userId(event.getUserId())
                .paymentAmount(event.getPaymentAmount())
                .dueDate(event.getPaymentDueDate())
                .daysOverdue(event.getDaysOverdue())
                .lateFee(LATE_FEE_AMOUNT)
                .totalAmountDue(event.getPaymentAmount().add(LATE_FEE_AMOUNT))
                .missedAt(LocalDateTime.now())
                .build();

            missedPaymentRepository.save(missedPayment);

            // Update loan status
            loan.setStatus(LoanStatus.DELINQUENT);
            loan.setMissedPaymentCount(loan.getMissedPaymentCount() + 1);
            loan.setTotalLateFees(loan.getTotalLateFees().add(LATE_FEE_AMOUNT));
            loan.setNextPaymentAmount(missedPayment.getTotalAmountDue());
            loanRepository.save(loan);

            log.error("💰 LATE FEE APPLIED: loanId={}, lateFee=${}, totalDue=${}, missedCount={}",
                event.getLoanId(), LATE_FEE_AMOUNT, missedPayment.getTotalAmountDue(),
                loan.getMissedPaymentCount());

            // Notify borrower
            notifyMissedPayment(event, loan, missedPayment);

            // Credit reporting (if overdue >30 days)
            if (event.getDaysOverdue() >= DAYS_BEFORE_CREDIT_REPORT) {
                log.error("🚨 CREDIT BUREAU REPORTING: loanId={}, daysOverdue={} - reporting to credit bureaus",
                    event.getLoanId(), event.getDaysOverdue());

                creditReportingService.reportMissedPayment(
                    event.getUserId(), event.getLoanId(), event.getDaysOverdue());

                metricsCollector.incrementCounter("loan.payment.missed.credit_reported");
            }

            // High-risk flag (3+ missed payments)
            if (loan.getMissedPaymentCount() >= 3) {
                log.error("⚠️ HIGH-RISK LOAN: loanId={}, missedPayments={} - flagging for collections",
                    event.getLoanId(), loan.getMissedPaymentCount());

                loan.setStatus(LoanStatus.DEFAULT);
                loanRepository.save(loan);

                notificationService.alertCollectionsTeam(event.getLoanId(), event.getUserId(),
                    loan.getMissedPaymentCount(), loan.getTotalOutstandingBalance());

                metricsCollector.incrementCounter("loan.payment.missed.default");
            }

            metricsCollector.incrementCounter("loan.payment.missed");
            metricsCollector.recordGauge("loan.payment.missed.days_overdue", event.getDaysOverdue());
            metricsCollector.recordGauge("loan.late.fee.amount", LATE_FEE_AMOUNT.doubleValue());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process loan payment missed event", e);
            dlqHandler.sendToDLQ("loan.payment.missed", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private void notifyMissedPayment(LoanPaymentMissedEvent event, Loan loan, MissedPayment missedPayment) {
        String urgencyLevel = event.getDaysOverdue() >= DAYS_BEFORE_CREDIT_REPORT ? "URGENT" : "HIGH";

        String message = String.format("""
            %s IMPORTANT: Loan Payment Missed

            Your loan payment is overdue. Immediate action is required.

            Loan Details:
            - Loan Type: %s
            - Original Loan Amount: $%s
            - Current Balance: $%s

            Missed Payment:
            - Payment Amount: $%s
            - Due Date: %s
            - Days Overdue: %d
            - Late Fee: $%s
            - Total Amount Now Due: $%s

            %s

            Consequences of Continued Non-Payment:
            %s

            How to Make Payment:
            1. Online: https://example.com/loans/payments
            2. Mobile App: Loans > Make Payment
            3. Phone: 1-800-WAQITI (automated payment)
            4. Auto-pay: Set up recurring payments to avoid future missed payments

            Payment Assistance Available:
            If you're experiencing financial hardship:
            • Loan modification options may be available
            • Hardship programs: loans-hardship@example.com
            • Free financial counseling: 1-800-WAQITI-HELP

            Questions? Contact loan support:
            Email: loan-support@example.com
            Phone: 1-800-WAQITI-LOAN
            Reference: Loan ID %s
            """,
            urgencyLevel,
            loan.getLoanType(),
            loan.getOriginalLoanAmount(),
            loan.getTotalOutstandingBalance(),
            event.getPaymentAmount(),
            event.getPaymentDueDate().toLocalDate(),
            event.getDaysOverdue(),
            LATE_FEE_AMOUNT,
            missedPayment.getTotalAmountDue(),
            getCreditReportingWarning(event.getDaysOverdue()),
            getConsequencesWarning(loan.getMissedPaymentCount()),
            event.getLoanId());

        notificationService.sendMissedPaymentNotification(
            event.getUserId(), event.getLoanId(), missedPayment.getTotalAmountDue(), message);
    }

    private String getCreditReportingWarning(int daysOverdue) {
        int daysUntilReport = DAYS_BEFORE_CREDIT_REPORT - daysOverdue;

        if (daysOverdue >= DAYS_BEFORE_CREDIT_REPORT) {
            return String.format("""
                ⚠️ CREDIT BUREAU REPORTING:
                This missed payment HAS BEEN reported to credit bureaus (Equifax, Experian, TransUnion).
                This will negatively impact your credit score and remain on your credit report for 7 years.
                """);
        } else if (daysUntilReport <= 7) {
            return String.format("""
                ⚠️ CREDIT REPORTING IMMINENT:
                If not paid within %d days, this missed payment will be reported to credit bureaus,
                which will significantly damage your credit score.
                """, daysUntilReport);
        } else {
            return String.format("""
                If not paid within %d days, this missed payment will be reported to credit bureaus.
                """, daysUntilReport);
        }
    }

    private String getConsequencesWarning(int missedPaymentCount) {
        if (missedPaymentCount >= 3) {
            return """
                🚨 DEFAULT STATUS - IMMEDIATE ACTION REQUIRED:
                • Your loan has been flagged for collections
                • Legal action may be initiated
                • Your entire loan balance may be declared immediately due
                • Additional collection fees will be added
                • Your credit score will be severely damaged
                • Future loan applications will be denied
                """;
        } else if (missedPaymentCount == 2) {
            return """
                ⚠️ WARNING - 2 MISSED PAYMENTS:
                • One more missed payment will result in default status
                • Your loan will be sent to collections
                • Legal action may be pursued
                • Your credit score will be significantly damaged
                """;
        } else {
            return """
                • Additional late fees will accrue
                • Future missed payments will damage your credit score
                • Continued non-payment may result in loan default
                """;
        }
    }

    private static class LoanPaymentMissedEvent {
        private UUID loanId, userId;
        private BigDecimal paymentAmount;
        private LocalDateTime paymentDueDate;
        private int daysOverdue;

        public UUID getLoanId() { return loanId; }
        public UUID getUserId() { return userId; }
        public BigDecimal getPaymentAmount() { return paymentAmount; }
        public LocalDateTime getPaymentDueDate() { return paymentDueDate; }
        public int getDaysOverdue() { return daysOverdue; }
    }
}
