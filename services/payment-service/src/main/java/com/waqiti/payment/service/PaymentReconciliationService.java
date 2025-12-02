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
public class PaymentReconciliationService {
    
    private final Map<String, ReconciliationRecord> reconciliationRecords = new ConcurrentHashMap<>();
    
    public ReconciliationResult reconcilePayment(String transactionId, BigDecimal expectedAmount, 
                                                 BigDecimal actualAmount, String status) {
        try {
            log.info("Reconciling payment: transactionId={}, expected={}, actual={}", 
                transactionId, expectedAmount, actualAmount);
            
            boolean matched = expectedAmount.compareTo(actualAmount) == 0;
            BigDecimal discrepancy = expectedAmount.subtract(actualAmount);
            
            ReconciliationRecord record = ReconciliationRecord.builder()
                .transactionId(transactionId)
                .expectedAmount(expectedAmount)
                .actualAmount(actualAmount)
                .discrepancy(discrepancy)
                .matched(matched)
                .status(status)
                .reconciledAt(LocalDateTime.now())
                .build();
            
            reconciliationRecords.put(transactionId, record);
            
            if (!matched) {
                log.warn("Payment reconciliation mismatch: transactionId={}, discrepancy={}", 
                    transactionId, discrepancy);
            } else {
                log.info("Payment reconciled successfully: transactionId={}", transactionId);
            }
            
            return ReconciliationResult.builder()
                .transactionId(transactionId)
                .matched(matched)
                .discrepancy(discrepancy)
                .message(matched ? "Reconciliation successful" : "Amount mismatch detected")
                .build();
            
        } catch (Exception e) {
            log.error("Payment reconciliation failed: transactionId={}", transactionId, e);
            return ReconciliationResult.builder()
                .transactionId(transactionId)
                .matched(false)
                .message("Reconciliation failed: " + e.getMessage())
                .build();
        }
    }
    
    public List<ReconciliationRecord> getUnreconciledPayments() {
        return reconciliationRecords.values().stream()
            .filter(record -> !record.isMatched())
            .toList();
    }
    
    public Optional<ReconciliationRecord> getReconciliationRecord(String transactionId) {
        return Optional.ofNullable(reconciliationRecords.get(transactionId));
    }
    
    public ReconciliationSummary generateReconciliationSummary(LocalDateTime from, LocalDateTime to) {
        List<ReconciliationRecord> records = reconciliationRecords.values().stream()
            .filter(r -> r.getReconciledAt().isAfter(from) && r.getReconciledAt().isBefore(to))
            .toList();
        
        long totalReconciled = records.size();
        long matched = records.stream().filter(ReconciliationRecord::isMatched).count();
        long unmatched = totalReconciled - matched;
        
        BigDecimal totalDiscrepancy = records.stream()
            .map(ReconciliationRecord::getDiscrepancy)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return ReconciliationSummary.builder()
            .periodFrom(from)
            .periodTo(to)
            .totalReconciled(totalReconciled)
            .matched(matched)
            .unmatched(unmatched)
            .totalDiscrepancy(totalDiscrepancy)
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationRecord {
        private String transactionId;
        private BigDecimal expectedAmount;
        private BigDecimal actualAmount;
        private BigDecimal discrepancy;
        private boolean matched;
        private String status;
        private LocalDateTime reconciledAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationResult {
        private String transactionId;
        private boolean matched;
        private BigDecimal discrepancy;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationSummary {
        private LocalDateTime periodFrom;
        private LocalDateTime periodTo;
        private long totalReconciled;
        private long matched;
        private long unmatched;
        private BigDecimal totalDiscrepancy;
    }
}