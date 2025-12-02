package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.DisputeServiceClient;
import com.waqiti.customer.client.dto.DisputeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for DisputeServiceClient.
 * Provides circuit breaker pattern implementation with safe default values
 * when dispute-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class DisputeServiceClientFallback implements DisputeServiceClient {

    @Override
    public List<DisputeResponse> getActiveDisputes(String customerId) {
        log.error("DisputeServiceClient.getActiveDisputes fallback triggered for customerId: {}", customerId);
        return Collections.emptyList();
    }

    @Override
    public boolean hasActiveDisputes(String customerId) {
        log.error("DisputeServiceClient.hasActiveDisputes fallback triggered for customerId: {}. Returning false as safe default.", customerId);
        return false;
    }

    @Override
    public List<DisputeResponse> getDisputesByAccount(String accountId) {
        log.error("DisputeServiceClient.getDisputesByAccount fallback triggered for accountId: {}", accountId);
        return Collections.emptyList();
    }
}
