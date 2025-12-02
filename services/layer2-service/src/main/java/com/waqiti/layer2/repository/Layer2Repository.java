package com.waqiti.layer2.repository;

import com.waqiti.layer2.model.WithdrawalRequest;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for Layer 2 operations
 * TODO: Convert to JPA repository once entities are fully implemented
 */
@Repository
public class Layer2Repository {

    private final Map<String, WithdrawalRequest> withdrawalRequests = new ConcurrentHashMap<>();

    public void saveWithdrawalRequest(WithdrawalRequest request) {
        String key = request.getUserAddress() + "-" + request.getRequestTime().toEpochMilli();
        withdrawalRequests.put(key, request);
    }

    public WithdrawalRequest findWithdrawalRequest(String userAddress) {
        return withdrawalRequests.values().stream()
            .filter(r -> r.getUserAddress().equals(userAddress))
            .findFirst()
            .orElse(null);
    }

    public void clear() {
        withdrawalRequests.clear();
    }
}
