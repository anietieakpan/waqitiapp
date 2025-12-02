package com.waqiti.compliance.client;

import com.waqiti.compliance.dto.TransactionSummary;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Feign client for transaction service integration
 * Provides comprehensive transaction data for compliance monitoring
 */
@FeignClient(
    name = "transaction-service",
    path = "/api/v1/transactions",
    fallback = TransactionServiceClient.TransactionServiceFallback.class
)
public interface TransactionServiceClient {
    
    /**
     * Get customer transactions within date range
     */
    @GetMapping("/customer/{customerId}")
    @CircuitBreaker(name = "transaction-service")
    @Retry(name = "transaction-service")
    @TimeLimiter(name = "transaction-service")
    CompletableFuture<List<TransactionSummary>> getCustomerTransactions(
        @PathVariable("customerId") UUID customerId,
        @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
        @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate
    );
    
    /**
     * Get recent transactions for a customer
     */
    @GetMapping("/customer/{customerId}/recent")
    @CircuitBreaker(name = "transaction-service")
    @Cacheable(value = "recent-transactions", key = "#customerId + '-' + #since")
    List<TransactionSummary> getRecentTransactions(
        @PathVariable("customerId") UUID customerId,
        @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    );
    
    /**
     * Get daily transaction count for a customer
     */
    @GetMapping("/customer/{customerId}/count")
    @CircuitBreaker(name = "transaction-service")
    @Cacheable(value = "daily-transaction-count", key = "#customerId + '-' + #date")
    long getDailyTransactionCount(
        @PathVariable("customerId") UUID customerId,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );
    
    /**
     * Get daily transaction volume for a customer
     */
    @GetMapping("/customer/{customerId}/volume")
    @CircuitBreaker(name = "transaction-service")
    @Cacheable(value = "daily-transaction-volume", key = "#customerId + '-' + #date")
    BigDecimal getDailyTransactionVolume(
        @PathVariable("customerId") UUID customerId,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );
    
    /**
     * Get transaction patterns for behavioral analysis
     */
    @GetMapping("/customer/{customerId}/patterns")
    @CircuitBreaker(name = "transaction-service")
    TransactionPatterns getTransactionPatterns(@PathVariable("customerId") UUID customerId);
    
    /**
     * Get high-risk transactions
     */
    @GetMapping("/customer/{customerId}/high-risk")
    @CircuitBreaker(name = "transaction-service")
    List<HighRiskTransaction> getHighRiskTransactions(
        @PathVariable("customerId") UUID customerId,
        @RequestParam(value = "limit", defaultValue = "100") int limit
    );
    
    /**
     * Get cross-border transactions
     */
    @GetMapping("/customer/{customerId}/cross-border")
    @CircuitBreaker(name = "transaction-service")
    List<CrossBorderTransaction> getCrossBorderTransactions(
        @PathVariable("customerId") UUID customerId,
        @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
        @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate
    );
    
    /**
     * Get transaction velocity metrics
     */
    @GetMapping("/customer/{customerId}/velocity")
    @CircuitBreaker(name = "transaction-service")
    VelocityMetrics getVelocityMetrics(
        @PathVariable("customerId") UUID customerId,
        @RequestParam(value = "period", defaultValue = "24h") String period
    );
    
    /**
     * Get linked transactions (potentially related)
     */
    @GetMapping("/customer/{customerId}/linked")
    @CircuitBreaker(name = "transaction-service")
    List<LinkedTransactions> getLinkedTransactions(
        @PathVariable("customerId") UUID customerId,
        @RequestParam(value = "depth", defaultValue = "2") int depth
    );
    
    /**
     * Get transaction counterparties
     */
    @GetMapping("/customer/{customerId}/counterparties")
    @CircuitBreaker(name = "transaction-service")
    List<CounterpartyInfo> getCounterparties(
        @PathVariable("customerId") UUID customerId,
        @RequestParam(value = "limit", defaultValue = "50") int limit
    );
    
    /**
     * Fallback implementation for circuit breaker
     */
    @Component
    @Slf4j
    @RequiredArgsConstructor
    class TransactionServiceFallback implements TransactionServiceClient {
        
        @Override
        public CompletableFuture<List<TransactionSummary>> getCustomerTransactions(
                UUID customerId, LocalDateTime fromDate, LocalDateTime toDate) {
            log.warn("Fallback: Unable to fetch transactions for customer: {} from {} to {}", 
                    customerId, fromDate, toDate);
            return CompletableFuture.completedFuture(generateFallbackTransactions(customerId));
        }
        
        @Override
        public List<TransactionSummary> getRecentTransactions(UUID customerId, LocalDateTime since) {
            log.warn("Fallback: Unable to fetch recent transactions for customer: {} since {}", 
                    customerId, since);
            return generateFallbackTransactions(customerId);
        }
        
        @Override
        public long getDailyTransactionCount(UUID customerId, LocalDate date) {
            log.warn("Fallback: Unable to fetch daily transaction count for customer: {} on {}", 
                    customerId, date);
            return 0L; // Return 0 count in fallback
        }
        
        @Override
        public BigDecimal getDailyTransactionVolume(UUID customerId, LocalDate date) {
            log.warn("Fallback: Unable to fetch daily transaction volume for customer: {} on {}", 
                    customerId, date);
            return BigDecimal.ZERO; // Return zero volume in fallback
        }
        
        @Override
        public TransactionPatterns getTransactionPatterns(UUID customerId) {
            log.warn("Fallback: Unable to fetch transaction patterns for customer: {}", customerId);
            return TransactionPatterns.builder()
                .customerId(customerId)
                .averageDailyTransactions(0)
                .averageTransactionAmount(BigDecimal.ZERO)
                .unusualActivityDetected(true) // Flag as unusual for safety
                .riskScore(100) // Maximum risk
                .build();
        }
        
        @Override
        public List<HighRiskTransaction> getHighRiskTransactions(UUID customerId, int limit) {
            log.warn("Fallback: Unable to fetch high-risk transactions for customer: {}", customerId);
            return List.of();
        }
        
        @Override
        public List<CrossBorderTransaction> getCrossBorderTransactions(
                UUID customerId, LocalDateTime fromDate, LocalDateTime toDate) {
            log.warn("Fallback: Unable to fetch cross-border transactions for customer: {}", customerId);
            return List.of();
        }
        
        @Override
        public VelocityMetrics getVelocityMetrics(UUID customerId, String period) {
            log.warn("Fallback: Unable to fetch velocity metrics for customer: {}", customerId);
            return VelocityMetrics.builder()
                .customerId(customerId)
                .period(period)
                .transactionCount(0)
                .totalVolume(BigDecimal.ZERO)
                .velocityScore(100) // High velocity score for safety
                .anomalyDetected(true)
                .build();
        }
        
        @Override
        public List<LinkedTransactions> getLinkedTransactions(UUID customerId, int depth) {
            log.warn("Fallback: Unable to fetch linked transactions for customer: {}", customerId);
            return List.of();
        }
        
        @Override
        public List<CounterpartyInfo> getCounterparties(UUID customerId, int limit) {
            log.warn("Fallback: Unable to fetch counterparties for customer: {}", customerId);
            return List.of();
        }
        
        private List<TransactionSummary> generateFallbackTransactions(UUID customerId) {
            // Generate minimal fallback data for safety
            return List.of(
                TransactionSummary.builder()
                    .transactionId(UUID.randomUUID())
                    .customerId(customerId)
                    .amount(BigDecimal.ZERO)
                    .currency("USD")
                    .type("UNKNOWN")
                    .status("PENDING_REVIEW")
                    .riskScore(100)
                    .flaggedForReview(true)
                    .timestamp(LocalDateTime.now())
                    .build()
            );
        }
    }
    
    // DTO classes for responses
    
    @Data
    @Builder
    class TransactionPatterns {
        private UUID customerId;
        private Integer averageDailyTransactions;
        private BigDecimal averageTransactionAmount;
        private BigDecimal standardDeviation;
        private List<String> frequentCounterparties;
        private List<String> frequentTransactionTypes;
        private Map<String, Integer> hourlyDistribution;
        private Map<String, BigDecimal> categorySpending;
        private boolean unusualActivityDetected;
        private Integer riskScore;
        private LocalDateTime analysisDate;
    }
    
    @Data
    @Builder
    class HighRiskTransaction {
        private UUID transactionId;
        private UUID customerId;
        private BigDecimal amount;
        private String currency;
        private String type;
        private String counterparty;
        private String country;
        private Integer riskScore;
        private List<String> riskFactors;
        private String status;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    class CrossBorderTransaction {
        private UUID transactionId;
        private UUID customerId;
        private String originCountry;
        private String destinationCountry;
        private BigDecimal amount;
        private BigDecimal exchangeRate;
        private String originCurrency;
        private String destinationCurrency;
        private String purpose;
        private boolean requiresReporting;
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    class VelocityMetrics {
        private UUID customerId;
        private String period;
        private Integer transactionCount;
        private BigDecimal totalVolume;
        private BigDecimal averageAmount;
        private BigDecimal maxAmount;
        private Integer uniqueCounterparties;
        private Integer velocityScore;
        private boolean anomalyDetected;
        private List<String> anomalyReasons;
        private LocalDateTime calculatedAt;
    }
    
    @Data
    @Builder
    class LinkedTransactions {
        private UUID primaryTransactionId;
        private List<UUID> linkedTransactionIds;
        private String linkType;
        private Double confidenceScore;
        private String pattern;
        private LocalDateTime identifiedAt;
    }
    
    @Data
    @Builder
    class CounterpartyInfo {
        private String counterpartyId;
        private String name;
        private String type;
        private String country;
        private Integer transactionCount;
        private BigDecimal totalVolume;
        private Integer riskScore;
        private boolean isHighRisk;
        private LocalDateTime firstTransactionDate;
        private LocalDateTime lastTransactionDate;
    }
}