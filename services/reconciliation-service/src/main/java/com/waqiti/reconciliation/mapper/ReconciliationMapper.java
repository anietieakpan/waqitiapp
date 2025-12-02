package com.waqiti.reconciliation.mapper;

import java.util.Map;

/**
 * Reconciliation Mapper
 * 
 * CRITICAL: Mapper interface for reconciliation data transformations.
 * Handles conversion between different data formats and entities.
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
public interface ReconciliationMapper {
    
    /**
     * Map transaction data to reconciliation format
     */
    Object mapToReconciliationFormat(Object transaction);
    
    /**
     * Map bank statement to transaction format
     */
    Object mapBankStatementToTransaction(Object bankStatement);
    
    /**
     * Map provider transaction to internal format
     */
    Object mapProviderTransactionToInternal(Object providerTransaction);
    
    /**
     * Map discrepancy data
     */
    Object mapDiscrepancy(Object source, Object target);

    /**
     * Default implementation
     */
    class ReconciliationMapperImpl implements ReconciliationMapper {
        
        @Override
        public Object mapToReconciliationFormat(Object transaction) {
            return Map.of(
                "id", extractId(transaction),
                "amount", extractAmount(transaction),
                "timestamp", java.time.LocalDateTime.now(),
                "type", "RECONCILIATION_TRANSACTION",
                "source", transaction
            );
        }
        
        @Override
        public Object mapBankStatementToTransaction(Object bankStatement) {
            return Map.of(
                "id", java.util.UUID.randomUUID().toString(),
                "amount", extractAmount(bankStatement),
                "timestamp", java.time.LocalDateTime.now(),
                "type", "BANK_TRANSACTION",
                "source", bankStatement
            );
        }
        
        @Override
        public Object mapProviderTransactionToInternal(Object providerTransaction) {
            return Map.of(
                "id", extractId(providerTransaction),
                "amount", extractAmount(providerTransaction),
                "timestamp", java.time.LocalDateTime.now(),
                "type", "PROVIDER_TRANSACTION",
                "source", providerTransaction
            );
        }
        
        @Override
        public Object mapDiscrepancy(Object source, Object target) {
            return Map.of(
                "discrepancyId", java.util.UUID.randomUUID().toString(),
                "sourceId", extractId(source),
                "targetId", extractId(target),
                "type", "AMOUNT_MISMATCH",
                "status", "OPEN",
                "timestamp", java.time.LocalDateTime.now(),
                "source", source,
                "target", target
            );
        }
        
        private String extractId(Object obj) {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                Object id = map.get("id");
                return id != null ? id.toString() : java.util.UUID.randomUUID().toString();
            }
            return java.util.UUID.randomUUID().toString();
        }
        
        private double extractAmount(Object obj) {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                Object amount = map.get("amount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue();
                }
            }
            return 0.0;
        }
    }
}