package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.TransactionSummary;
import com.waqiti.reporting.dto.RegulatoryReportResult.CurrencyTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.waqiti.reporting.client.TransactionServiceClient.*;

@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    @Override
    public List<TransactionSummary> getAccountTransactions(UUID accountId, LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve account transactions - Transaction Service unavailable. " +
                "AccountId: {}, DateRange: {} to {}", accountId, fromDate, toDate);
        return Collections.emptyList();
    }

    @Override
    public List<CurrencyTransaction> getCTRTransactions(LocalDate fromDate, LocalDate toDate) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot retrieve CTR transactions (regulatory requirement). " +
                "DateRange: {} to {}", fromDate, toDate);
        return Collections.emptyList();
    }

    @Override
    public BigDecimal getDailyTransactionVolume(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve daily volume - Transaction Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Long getDailyTransactionCount(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve daily count - Transaction Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Double getTransactionSuccessRate(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve success rate - Transaction Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public Double getAverageProcessingTime(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve processing time - Transaction Service unavailable. Date: {}", date);
        return null;
    }

    @Override
    public List<PaymentMethodVolume> getTransactionVolumeByPaymentMethod(LocalDate fromDate, LocalDate toDate) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve payment method volume - Transaction Service unavailable. " +
                "DateRange: {} to {}", fromDate, toDate);
        return Collections.emptyList();
    }

    @Override
    public List<TransactionTrend> getTransactionTrends(LocalDate fromDate, LocalDate toDate, String granularity) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve transaction trends - Transaction Service unavailable. " +
                "DateRange: {} to {}, Granularity: {}", fromDate, toDate, granularity);
        return Collections.emptyList();
    }

    @Override
    public List<TransactionFailure> getFailedTransactions(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve failed transactions - Transaction Service unavailable. Date: {}", date);
        return Collections.emptyList();
    }

    @Override
    public List<TransactionSummary> getHighValueTransactions(LocalDate fromDate, LocalDate toDate, BigDecimal threshold) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot retrieve high-value transactions (monitoring requirement). " +
                "DateRange: {} to {}, Threshold: {}", fromDate, toDate, threshold);
        return Collections.emptyList();
    }

    @Override
    public TransactionStatistics getTransactionStatistics(LocalDate date) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve transaction statistics - Transaction Service unavailable. Date: {}", date);
        
        return new TransactionStatistics(
                null, null, null,
                null, null,
                null, null
        );
    }
}