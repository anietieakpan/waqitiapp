package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.DisputeResponse;
import com.waqiti.customer.client.fallback.DisputeServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client for inter-service communication with dispute-service.
 * Provides methods to retrieve dispute information.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "dispute-service",
    configuration = FeignClientConfig.class,
    fallback = DisputeServiceClientFallback.class
)
public interface DisputeServiceClient {

    /**
     * Retrieves all active disputes for a customer.
     *
     * @param customerId The unique customer identifier
     * @return List of active disputes
     */
    @GetMapping("/api/v1/disputes/customer/{customerId}/active")
    List<DisputeResponse> getActiveDisputes(@PathVariable("customerId") String customerId);

    /**
     * Checks if a customer has any active disputes.
     *
     * @param customerId The unique customer identifier
     * @return True if customer has active disputes, false otherwise
     */
    @GetMapping("/api/v1/disputes/customer/{customerId}/has-active")
    boolean hasActiveDisputes(@PathVariable("customerId") String customerId);

    /**
     * Retrieves all disputes for a specific account.
     *
     * @param accountId The unique account identifier
     * @return List of disputes related to the account
     */
    @GetMapping("/api/v1/disputes/account/{accountId}")
    List<DisputeResponse> getDisputesByAccount(@PathVariable("accountId") String accountId);
}
