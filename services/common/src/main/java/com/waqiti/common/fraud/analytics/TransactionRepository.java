package com.waqiti.common.fraud.analytics;

import com.waqiti.common.fraud.transaction.TransactionEvent;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for transaction data access
 */
@Repository
public class TransactionRepository {
    
    public List<TransactionEvent> getUserTransactionHistory(String userId, int days) {
        // Implementation would fetch user transaction history
        return List.of();
    }
    
    public List<TransactionEvent> getMerchantTransactionHistory(String merchantId, int days) {
        // Implementation would fetch merchant transaction history
        return List.of();
    }
}