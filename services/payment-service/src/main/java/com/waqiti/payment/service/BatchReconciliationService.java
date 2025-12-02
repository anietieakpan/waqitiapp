package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class BatchReconciliationService {
    
    private final Map<String, BatchReconciliationResult> batchResults = new ConcurrentHashMap<>();
    private final PaymentReconciliationService paymentReconciliationService;
    
    public BatchReconciliationResult reconcileBatch(String batchId, List<PaymentReconciliationItem> items) {
        try {
            log.info("Starting batch reconciliation: batchId={}, items={}", batchId, items.size());
            
            LocalDateTime startTime = LocalDateTime.now();
            List<PaymentReconciliationService.ReconciliationResult> results = new ArrayList<>();
            
            for (PaymentReconciliationItem item : items) {
                PaymentReconciliationService.ReconciliationResult result = 
                    paymentReconciliationService.reconcilePayment(
                        item.getTransactionId(),
                        item.getExpectedAmount(),
                        item.getActualAmount(),
                        item.getStatus()
                    );
                results.add(result);
            }
            
            long matched = results.stream().filter(PaymentReconciliationService.ReconciliationResult::isMatched).count();
            long unmatched = results.size() - matched;
            
            BigDecimal totalDiscrepancy = results.stream()
                .map(PaymentReconciliationService.ReconciliationResult::getDiscrepancy)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            LocalDateTime endTime = LocalDateTime.now();
            
            BatchReconciliationResult batchResult = BatchReconciliationResult.builder()
                .batchId(batchId)
                .totalItems(items.size())
                .matched(matched)
                .unmatched(unmatched)
                .totalDiscrepancy(totalDiscrepancy)
                .startTime(startTime)
                .endTime(endTime)
                .status("COMPLETED")
                .results(results)
                .build();
            
            batchResults.put(batchId, batchResult);
            
            log.info("Batch reconciliation completed: batchId={}, matched={}, unmatched={}", 
                batchId, matched, unmatched);
            
            return batchResult;
            
        } catch (Exception e) {
            log.error("Batch reconciliation failed: batchId={}", batchId, e);
            
            BatchReconciliationResult errorResult = BatchReconciliationResult.builder()
                .batchId(batchId)
                .status("FAILED")
                .errorMessage(e.getMessage())
                .build();
            
            batchResults.put(batchId, errorResult);
            
            return errorResult;
        }
    }
    
    public Optional<BatchReconciliationResult> getBatchResult(String batchId) {
        return Optional.ofNullable(batchResults.get(batchId));
    }
    
    public List<BatchReconciliationResult> getAllBatchResults() {
        return new ArrayList<>(batchResults.values());
    }
    
    public List<BatchReconciliationResult> getFailedBatches() {
        return batchResults.values().stream()
            .filter(result -> "FAILED".equals(result.getStatus()) || result.getUnmatched() > 0)
            .toList();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentReconciliationItem {
        private String transactionId;
        private BigDecimal expectedAmount;
        private BigDecimal actualAmount;
        private String status;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BatchReconciliationResult {
        private String batchId;
        private int totalItems;
        private long matched;
        private long unmatched;
        private BigDecimal totalDiscrepancy;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String errorMessage;
        private List<PaymentReconciliationService.ReconciliationResult> results;
    }
}