package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.LegalServiceClient;
import com.waqiti.customer.client.dto.LegalHoldResponse;
import com.waqiti.customer.client.dto.SubpoenaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for LegalServiceClient.
 * Provides circuit breaker pattern implementation with safe default values
 * when legal-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class LegalServiceClientFallback implements LegalServiceClient {

    @Override
    public List<LegalHoldResponse> getLegalHolds(String customerId) {
        log.error("LegalServiceClient.getLegalHolds fallback triggered for customerId: {}", customerId);
        return Collections.emptyList();
    }

    @Override
    public boolean hasLegalHolds(String customerId) {
        log.error("LegalServiceClient.hasLegalHolds fallback triggered for customerId: {}. Returning false as safe default.", customerId);
        return false;
    }

    @Override
    public List<SubpoenaResponse> getSubpoenas(String customerId) {
        log.error("LegalServiceClient.getSubpoenas fallback triggered for customerId: {}", customerId);
        return Collections.emptyList();
    }
}
