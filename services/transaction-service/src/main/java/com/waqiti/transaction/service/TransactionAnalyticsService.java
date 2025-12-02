package com.waqiti.transaction.service;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.transaction.dto.SpendingAnalyticsResponse;
import com.waqiti.transaction.dto.TransactionSummaryResponse;
import com.waqiti.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionAnalyticsService {
    
    private final TransactionRepository transactionRepository;
    
    @Cacheable(value = "transaction-summary", key = "#walletId + '-' + #startDate + '-' + #endDate")
    public TransactionSummaryResponse getTransactionSummary(String walletId, LocalDate startDate, LocalDate endDate) {
        log.debug("Generating transaction summary for wallet: {}, period: {} to {}", walletId, startDate, endDate);
        
        // Set default date range if not provided
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusMonths(1);
        }
        
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        
        List<Transaction> transactions;
        if (walletId != null) {
            transactions = transactionRepository.findByUserIdAndDateRange(walletId, startDateTime, endDateTime);
        } else {
            transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);
        }
        
        // Calculate statistics
        Map<TransactionType, BigDecimal> totalByType = new HashMap<>();
        Map<TransactionType, Integer> countByType = new HashMap<>();
        Map<TransactionStatus, Integer> statusCount = new HashMap<>();
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        
        for (Transaction tx : transactions) {
            // Group by type
            totalByType.merge(tx.getType(), tx.getAmount(), BigDecimal::add);
            countByType.merge(tx.getType(), 1, Integer::sum);
            
            // Group by status
            statusCount.merge(tx.getStatus(), 1, Integer::sum);
            
            // Calculate totals
            if (tx.getStatus() == TransactionStatus.COMPLETED) {
                totalVolume = totalVolume.add(tx.getAmount());
                if (tx.getFee() != null) {
                    totalFees = totalFees.add(tx.getFee());
                }
            }
        }
        
        // Calculate average transaction amount
        BigDecimal averageAmount = transactions.isEmpty() ? BigDecimal.ZERO : 
            totalVolume.divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
        
        return TransactionSummaryResponse.builder()
            .walletId(walletId)
            .startDate(startDate)
            .endDate(endDate)
            .totalTransactions(transactions.size())
            .totalVolume(totalVolume)
            .totalFees(totalFees)
            .averageTransactionAmount(averageAmount)
            .transactionsByType(countByType)
            .volumeByType(totalByType)
            .transactionsByStatus(statusCount)
            .build();
    }
    
    @Cacheable(value = "spending-analytics", key = "#period + '-' + #walletId")
    public SpendingAnalyticsResponse getSpendingAnalytics(String period, String walletId) {
        log.debug("Generating spending analytics for period: {}, wallet: {}", period, walletId);
        
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(period, endTime);
        
        List<Transaction> transactions;
        if (walletId != null) {
            transactions = transactionRepository.findByUserIdAndDateRange(walletId, startTime, endTime);
        } else {
            transactions = transactionRepository.findByDateRange(startTime, endTime);
        }
        
        // Filter for spending transactions (outgoing)
        List<Transaction> spendingTransactions = transactions.stream()
            .filter(tx -> tx.getType() == TransactionType.TRANSFER || 
                         tx.getType() == TransactionType.WITHDRAWAL ||
                         tx.getType() == TransactionType.PAYMENT)
            .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
            .collect(Collectors.toList());
        
        // Calculate spending by category
        Map<String, BigDecimal> spendingByCategory = new HashMap<>();
        Map<String, Integer> transactionCountByCategory = new HashMap<>();
        
        for (Transaction tx : spendingTransactions) {
            String category = tx.getCategory() != null ? tx.getCategory() : "Uncategorized";
            spendingByCategory.merge(category, tx.getAmount(), BigDecimal::add);
            transactionCountByCategory.merge(category, 1, Integer::sum);
        }
        
        // Calculate daily spending trend
        Map<LocalDate, BigDecimal> dailySpending = spendingTransactions.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getCreatedAt().toLocalDate(),
                Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
            ));
        
        // Calculate top merchants
        Map<String, BigDecimal> topMerchants = new HashMap<>();
        for (Transaction tx : spendingTransactions) {
            if (tx.getMerchantName() != null) {
                topMerchants.merge(tx.getMerchantName(), tx.getAmount(), BigDecimal::add);
            }
        }
        
        // Sort and limit to top 10 merchants
        LinkedHashMap<String, BigDecimal> sortedTopMerchants = topMerchants.entrySet()
            .stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        
        // Calculate statistics
        BigDecimal totalSpending = spendingTransactions.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageTransactionAmount = spendingTransactions.isEmpty() ? BigDecimal.ZERO :
            totalSpending.divide(BigDecimal.valueOf(spendingTransactions.size()), 2, RoundingMode.HALF_UP);
        
        BigDecimal maxTransaction = spendingTransactions.stream()
            .map(Transaction::getAmount)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        return SpendingAnalyticsResponse.builder()
            .period(period)
            .walletId(walletId)
            .totalSpending(totalSpending)
            .transactionCount(spendingTransactions.size())
            .averageTransactionAmount(averageTransactionAmount)
            .maxTransactionAmount(maxTransaction)
            .spendingByCategory(spendingByCategory)
            .transactionCountByCategory(transactionCountByCategory)
            .dailySpending(dailySpending)
            .topMerchants(sortedTopMerchants)
            .startDate(startTime.toLocalDate())
            .endDate(endTime.toLocalDate())
            .build();
    }
    
    private LocalDateTime calculateStartTime(String period, LocalDateTime endTime) {
        return switch (period.toLowerCase()) {
            case "day" -> endTime.minus(1, ChronoUnit.DAYS);
            case "week" -> endTime.minus(7, ChronoUnit.DAYS);
            case "month" -> endTime.minus(30, ChronoUnit.DAYS);
            case "quarter" -> endTime.minus(90, ChronoUnit.DAYS);
            case "year" -> endTime.minus(365, ChronoUnit.DAYS);
            default -> endTime.minus(30, ChronoUnit.DAYS); // Default to month
        };
    }
}