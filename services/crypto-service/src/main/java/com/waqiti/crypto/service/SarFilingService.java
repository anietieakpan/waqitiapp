package com.waqiti.crypto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * SAR Filing Service
 * Wrapper around CryptoRegulatoryService for Suspicious Activity Report filing
 * Simplifies SAR filing from DLQ consumers and compliance workflows
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SarFilingService {

    private final CryptoRegulatoryService cryptoRegulatoryService;

    /**
     * File SAR for suspicious crypto transaction
     */
    @Async
    public void fileSar(
            String transactionId,
            String customerId,
            String violationType,
            String correlationId) {

        log.warn("Filing SAR via wrapper: transaction={} customer={} violation={} correlationId={}",
                transactionId, customerId, violationType, correlationId);

        try {
            cryptoRegulatoryService.fileSuspiciousActivityReport(
                    transactionId,
                    customerId,
                    violationType,
                    correlationId
            );

            log.warn("SAR filed successfully via wrapper: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to file SAR via wrapper: transaction={} correlationId={}",
                    transactionId, correlationId, e);
            throw new RuntimeException("SAR filing failed", e);
        }
    }

    /**
     * File CTR for large crypto transaction (>$10k)
     */
    @Async
    public void fileCtr(
            String transactionId,
            String customerId,
            BigDecimal amount,
            String currency,
            String correlationId) {

        log.info("Filing CTR via wrapper: transaction={} customer={} amount={} {} correlationId={}",
                transactionId, customerId, amount, currency, correlationId);

        try {
            cryptoRegulatoryService.fileCurrencyTransactionReport(
                    transactionId,
                    customerId,
                    amount,
                    currency,
                    correlationId
            );

            log.info("CTR filed successfully via wrapper: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to file CTR via wrapper: transaction={} correlationId={}",
                    transactionId, correlationId, e);
            throw new RuntimeException("CTR filing failed", e);
        }
    }

    /**
     * File general compliance report
     */
    @Async
    public void fileComplianceReport(
            String transactionId,
            String complianceType,
            String reportData,
            String correlationId) {

        log.info("Filing compliance report via wrapper: transaction={} type={} correlationId={}",
                transactionId, complianceType, correlationId);

        try {
            cryptoRegulatoryService.fileComplianceReport(
                    transactionId,
                    complianceType,
                    reportData,
                    correlationId
            );

            log.info("Compliance report filed successfully via wrapper: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to file compliance report via wrapper: transaction={} correlationId={}",
                    transactionId, correlationId, e);
            throw new RuntimeException("Compliance report filing failed", e);
        }
    }
}
