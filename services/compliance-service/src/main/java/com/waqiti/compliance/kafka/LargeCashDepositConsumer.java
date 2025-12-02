package com.waqiti.compliance.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.compliance.domain.CTRFiling;
import com.waqiti.compliance.domain.CTRStatus;
import com.waqiti.compliance.domain.RegulatoryReport;
import com.waqiti.compliance.repository.CTRFilingRepository;
import com.waqiti.compliance.repository.RegulatoryReportRepository;
import com.waqiti.compliance.service.FinCENService;
import com.waqiti.compliance.service.ComplianceNotificationService;
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
 * CRITICAL FIX #18: LargeCashDepositConsumer
 * Auto-files CTR (Currency Transaction Report) for cash deposits >$10,000
 * Compliance: Bank Secrecy Act, FinCEN CTR requirements (31 CFR 103.22)
 * Fine Risk: $10M+ per violation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LargeCashDepositConsumer {
    private final CTRFilingRepository ctrFilingRepository;
    private final RegulatoryReportRepository regulatoryReportRepository;
    private final FinCENService finCENService;
    private final ComplianceNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00");
    private static final int CTR_FILING_DEADLINE_DAYS = 15; // Must file within 15 days

    @KafkaListener(topics = "large.cash.deposit.detected", groupId = "compliance-ctr-filing")
    @Transactional
    public void handle(LargeCashDepositEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();

        try {
            log.warn("💰 LARGE CASH DEPOSIT DETECTED: transactionId={}, userId={}, amount=${}, location={}",
                event.getTransactionId(), event.getUserId(), event.getAmount(), event.getDepositLocation());

            String key = "ctr:filing:" + event.getTransactionId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            // Validate CTR threshold
            if (event.getAmount().compareTo(CTR_THRESHOLD) < 0) {
                log.warn("Amount ${} below CTR threshold, skipping", event.getAmount());
                ack.acknowledge();
                return;
            }

            // Check for structuring patterns (anti-money laundering)
            boolean structuringDetected = checkForStructuring(event);
            if (structuringDetected) {
                log.error("🚨 STRUCTURING DETECTED: userId={}, amount=${} - flagging for SAR review",
                    event.getUserId(), event.getAmount());
                metricsCollector.incrementCounter("compliance.structuring.detected");
            }

            // Create CTR filing record
            CTRFiling ctrFiling = CTRFiling.builder()
                .id(UUID.randomUUID())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .amount(event.getAmount())
                .transactionDate(event.getTransactionDate())
                .depositLocation(event.getDepositLocation())
                .depositMethod(event.getDepositMethod())
                .customerName(event.getCustomerName())
                .customerTaxId(event.getCustomerTaxId())
                .customerAddress(event.getCustomerAddress())
                .status(CTRStatus.PENDING)
                .filingDeadline(LocalDateTime.now().plusDays(CTR_FILING_DEADLINE_DAYS))
                .structuringFlagged(structuringDetected)
                .createdAt(LocalDateTime.now())
                .build();

            ctrFilingRepository.save(ctrFiling);

            log.warn("📋 CTR FILING CREATED: ctrId={}, amount=${}, deadline={}",
                ctrFiling.getId(), event.getAmount(), ctrFiling.getFilingDeadline());

            // Submit to FinCEN BSA E-Filing System
            try {
                String finCENReferenceNumber = finCENService.submitCTR(ctrFiling);

                ctrFiling.setStatus(CTRStatus.FILED);
                ctrFiling.setFinCENReferenceNumber(finCENReferenceNumber);
                ctrFiling.setFiledAt(LocalDateTime.now());
                ctrFilingRepository.save(ctrFiling);

                log.info("✅ CTR FILED WITH FINCEN: ctrId={}, finCENRef={}",
                    ctrFiling.getId(), finCENReferenceNumber);

                // Create regulatory report record
                createRegulatoryReport(ctrFiling, finCENReferenceNumber);

                metricsCollector.incrementCounter("compliance.ctr.filed");
            } catch (Exception e) {
                log.error("Failed to submit CTR to FinCEN - marking for manual review", e);
                ctrFiling.setStatus(CTRStatus.FAILED);
                ctrFiling.setFailureReason(e.getMessage());
                ctrFilingRepository.save(ctrFiling);

                // Alert compliance team for manual filing
                notificationService.alertComplianceTeam(
                    "CTR Filing Failed",
                    String.format("CTR %s for transaction %s failed to submit to FinCEN. Manual filing required.",
                        ctrFiling.getId(), event.getTransactionId()));

                metricsCollector.incrementCounter("compliance.ctr.filing_failed");
            }

            // Record metrics
            metricsCollector.incrementCounter("compliance.large.cash.deposit.processed");
            metricsCollector.recordGauge("compliance.ctr.amount", event.getAmount().doubleValue());
            metricsCollector.recordHistogram("compliance.ctr.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process large cash deposit event", e);
            dlqHandler.sendToDLQ("large.cash.deposit.detected", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private boolean checkForStructuring(LargeCashDepositEvent event) {
        // Check for deposits just under $10K in past 24 hours (classic structuring pattern)
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        BigDecimal recentDeposits = ctrFilingRepository
            .sumDepositsByUserSince(event.getUserId(), oneDayAgo);

        // If multiple deposits totaling >$10K in 24 hours, flag as potential structuring
        if (recentDeposits != null && recentDeposits.compareTo(CTR_THRESHOLD) >= 0) {
            return true;
        }

        // Check if amount is suspiciously close to threshold ($9,000 - $9,999)
        BigDecimal lowerBound = new BigDecimal("9000.00");
        BigDecimal upperBound = new BigDecimal("9999.99");
        return event.getAmount().compareTo(lowerBound) >= 0
            && event.getAmount().compareTo(upperBound) <= 0;
    }

    private void createRegulatoryReport(CTRFiling ctrFiling, String finCENReferenceNumber) {
        RegulatoryReport report = RegulatoryReport.builder()
            .id(UUID.randomUUID())
            .reportType("CTR")
            .regulatoryBody("FinCEN")
            .referenceNumber(finCENReferenceNumber)
            .filingDate(LocalDateTime.now())
            .transactionId(ctrFiling.getTransactionId())
            .userId(ctrFiling.getUserId())
            .amount(ctrFiling.getAmount())
            .retentionYears(5) // BSA requires 5-year retention
            .build();

        regulatoryReportRepository.save(report);

        log.info("📊 REGULATORY REPORT CREATED: reportId={}, type=CTR, retention=5 years",
            report.getId());
    }

    private static class LargeCashDepositEvent {
        private UUID transactionId, userId;
        private BigDecimal amount;
        private LocalDateTime transactionDate;
        private String depositLocation, depositMethod;
        private String customerName, customerTaxId, customerAddress;

        public UUID getTransactionId() { return transactionId; }
        public UUID getUserId() { return userId; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getTransactionDate() { return transactionDate; }
        public String getDepositLocation() { return depositLocation; }
        public String getDepositMethod() { return depositMethod; }
        public String getCustomerName() { return customerName; }
        public String getCustomerTaxId() { return customerTaxId; }
        public String getCustomerAddress() { return customerAddress; }
    }
}
