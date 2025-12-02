package com.waqiti.reporting.client;

import com.waqiti.reporting.dto.TransactionSummary;
import com.waqiti.reporting.dto.RegulatoryReportResult.CurrencyTransaction;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "transaction-service", 
    path = "/api/transactions",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    /**
     * Get account transactions for statement generation
     */
    @GetMapping("/account/{accountId}")
    List<TransactionSummary> getAccountTransactions(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get Currency Transaction Report (CTR) transactions
     */
    @GetMapping("/regulatory/ctr")
    List<CurrencyTransaction> getCTRTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get daily transaction volume
     */
    @GetMapping("/metrics/daily-volume")
    BigDecimal getDailyTransactionVolume(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get daily transaction count
     */
    @GetMapping("/metrics/daily-count")
    Long getDailyTransactionCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get transaction success rate for a date
     */
    @GetMapping("/metrics/success-rate")
    Double getTransactionSuccessRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get average transaction processing time
     */
    @GetMapping("/metrics/processing-time")
    Double getAverageProcessingTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get transaction volume by payment method
     */
    @GetMapping("/analytics/payment-method-volume")
    List<PaymentMethodVolume> getTransactionVolumeByPaymentMethod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Get transaction trends for analytics
     */
    @GetMapping("/analytics/trends")
    List<TransactionTrend> getTransactionTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam String granularity
    );

    /**
     * Get failed transactions for a date
     */
    @GetMapping("/failures")
    List<TransactionFailure> getFailedTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    /**
     * Get high-value transactions for monitoring
     */
    @GetMapping("/high-value")
    List<TransactionSummary> getHighValueTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam BigDecimal threshold
    );

    /**
     * Get transaction statistics for operational reporting
     */
    @GetMapping("/metrics/statistics")
    TransactionStatistics getTransactionStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );

    // Supporting DTOs
    record PaymentMethodVolume(String paymentMethod, BigDecimal volume, Long count) {}
    
    record TransactionTrend(LocalDate date, BigDecimal volume, Long count, BigDecimal avgAmount) {}
    
    record TransactionFailure(String transactionId, LocalDate date, String failureReason, String errorCode) {}
    
    record TransactionStatistics(
            Long totalCount,
            BigDecimal totalVolume,
            BigDecimal averageAmount,
            Double successRate,
            Double averageProcessingTime,
            Long pendingCount,
            Long failedCount
    ) {}
}