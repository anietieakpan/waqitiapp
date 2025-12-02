package com.waqiti.transaction.service;

import com.waqiti.common.optimization.N1QueryOptimizer;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.repository.TransactionItemRepository;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionItem;
import com.waqiti.transaction.dto.TransactionSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized transaction service that avoids N+1 query issues
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OptimizedTransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;

    /**
     * Get transactions with their items - optimized to avoid N+1 queries
     */
    public Page<Transaction> getTransactionsWithItems(UUID userId, Pageable pageable) {
        log.info("Getting transactions with items for user: {}", userId);
        
        return N1QueryOptimizer.monitorBatchOperation("getTransactionsWithItems", 
                pageable.getPageSize(), (unused) -> {
            
            // First, get the transactions page
            Page<Transaction> transactions = transactionRepository.findByUserId(userId, pageable);
            
            if (transactions.isEmpty()) {
                return transactions;
            }
            
            // Extract transaction IDs
            List<UUID> transactionIds = transactions.getContent().stream()
                    .map(Transaction::getId)
                    .collect(Collectors.toList());
            
            // Batch load transaction items
            Map<UUID, List<TransactionItem>> transactionToItems = 
                    N1QueryOptimizer.createKeyToEntityListMap(
                        transactionItemRepository.findByTransactionIdIn(transactionIds),
                        TransactionItem::getTransactionId
                    );
            
            // Enrich transactions with their items
            return N1QueryOptimizer.optimizePaginatedRelations(
                transactions,
                Transaction::getId,
                (ids) -> transactionToItems,
                (transaction, items) -> {
                    transaction.setItems(items != null ? items : new ArrayList<>());
                    return transaction;
                }
            );
        });
    }

    /**
     * Get transaction summary for multiple users - batch optimized
     */
    @Cacheable(value = "transactionSummaries", key = "#userIds.hashCode() + '_' + #startDate + '_' + #endDate")
    public Map<UUID, TransactionSummaryResponse> getTransactionSummariesForUsers(
            List<UUID> userIds, LocalDate startDate, LocalDate endDate) {
        
        log.info("Getting transaction summaries for {} users", userIds.size());
        
        return N1QueryOptimizer.monitorBatchOperation("getTransactionSummariesBatch", 
                userIds.size(), (unused) -> {
            
            // Batch load transaction summaries using a single query
            List<TransactionSummaryProjection> summaries = 
                    transactionRepository.getTransactionSummariesByUserIds(userIds, startDate, endDate);
            
            // Convert to response DTOs
            return summaries.stream()
                    .collect(Collectors.toMap(
                        TransactionSummaryProjection::getUserId,
                        this::mapToSummaryResponse
                    ));
        });
    }

    /**
     * Get merchant transactions with customer details - optimized
     */
    public Page<Transaction> getMerchantTransactionsWithCustomers(UUID merchantId, Pageable pageable) {
        log.info("Getting merchant transactions with customer details for merchant: {}", merchantId);
        
        return N1QueryOptimizer.monitorBatchOperation("getMerchantTransactionsWithCustomers", 
                pageable.getPageSize(), (unused) -> {
            
            // Get transactions using optimized query that joins with user data
            return transactionRepository.findByMerchantIdWithCustomerDetails(merchantId, pageable);
        });
    }

    /**
     * Get user transaction history with merchant details - optimized
     */
    public Page<Transaction> getUserTransactionsWithMerchants(UUID userId, Pageable pageable) {
        log.info("Getting user transactions with merchant details for user: {}", userId);
        
        return N1QueryOptimizer.monitorBatchOperation("getUserTransactionsWithMerchants", 
                pageable.getPageSize(), (unused) -> {
            
            // Use optimized query that joins with merchant data
            return transactionRepository.findByUserIdWithMerchantDetails(userId, pageable);
        });
    }

    /**
     * Get transactions by status with all related entities - optimized
     */
    public List<Transaction> getTransactionsByStatusWithRelatedEntities(String status, int limit) {
        log.info("Getting transactions by status with related entities: {}", status);
        
        return N1QueryOptimizer.monitorBatchOperation("getTransactionsByStatusOptimized", 
                limit, (unused) -> {
            
            // Use single query with multiple JOIN FETCH to load all related data
            return transactionRepository.findByStatusWithAllRelatedEntities(status, limit);
        });
    }

    /**
     * Bulk update transaction statuses - optimized for performance
     */
    @Transactional
    public int bulkUpdateTransactionStatuses(List<UUID> transactionIds, String newStatus, String reason) {
        log.info("Bulk updating {} transaction statuses to: {}", transactionIds.size(), newStatus);
        
        return N1QueryOptimizer.monitorBatchOperation("bulkUpdateTransactionStatuses", 
                transactionIds.size(), (unused) -> {
            
            // Use batch update to minimize database round trips
            return transactionRepository.bulkUpdateStatus(transactionIds, newStatus, reason);
        });
    }

    /**
     * Get daily transaction counts for multiple merchants - batch optimized
     */
    public Map<UUID, Map<LocalDate, Long>> getDailyTransactionCounts(
            List<UUID> merchantIds, LocalDate startDate, LocalDate endDate) {
        
        log.info("Getting daily transaction counts for {} merchants", merchantIds.size());
        
        return N1QueryOptimizer.monitorBatchOperation("getDailyTransactionCounts", 
                merchantIds.size(), (unused) -> {
            
            // Single query to get all counts
            List<DailyTransactionCount> counts = 
                    transactionRepository.getDailyTransactionCounts(merchantIds, startDate, endDate);
            
            // Group by merchant ID and date
            return counts.stream()
                    .collect(Collectors.groupingBy(
                        DailyTransactionCount::getMerchantId,
                        Collectors.toMap(
                            DailyTransactionCount::getDate,
                            DailyTransactionCount::getCount
                        )
                    ));
        });
    }

    private TransactionSummaryResponse mapToSummaryResponse(TransactionSummaryProjection projection) {
        return TransactionSummaryResponse.builder()
                .userId(projection.getUserId())
                .totalTransactions(projection.getTotalTransactions())
                .totalAmount(projection.getTotalAmount())
                .averageAmount(projection.getAverageAmount())
                .successfulTransactions(projection.getSuccessfulTransactions())
                .failedTransactions(projection.getFailedTransactions())
                .build();
    }

    // Projection interfaces for optimized queries
    public interface TransactionSummaryProjection {
        UUID getUserId();
        Long getTotalTransactions();
        java.math.BigDecimal getTotalAmount();
        java.math.BigDecimal getAverageAmount();
        Long getSuccessfulTransactions();
        Long getFailedTransactions();
    }

    public interface DailyTransactionCount {
        UUID getMerchantId();
        LocalDate getDate();
        Long getCount();
    }
}