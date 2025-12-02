package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.LegalHoldResponse;
import com.waqiti.customer.client.dto.SubpoenaResponse;
import com.waqiti.customer.client.fallback.LegalServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client for inter-service communication with legal-service.
 * Provides methods to retrieve legal holds and subpoena information.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "legal-service",
    configuration = FeignClientConfig.class,
    fallback = LegalServiceClientFallback.class
)
public interface LegalServiceClient {

    /**
     * Retrieves all legal holds for a customer.
     *
     * @param customerId The unique customer identifier
     * @return List of legal holds
     */
    @GetMapping("/api/v1/legal/customer/{customerId}/holds")
    List<LegalHoldResponse> getLegalHolds(@PathVariable("customerId") String customerId);

    /**
     * Checks if a customer has any active legal holds.
     *
     * @param customerId The unique customer identifier
     * @return True if customer has active legal holds, false otherwise
     */
    @GetMapping("/api/v1/legal/customer/{customerId}/has-holds")
    boolean hasLegalHolds(@PathVariable("customerId") String customerId);

    /**
     * Retrieves all subpoenas for a customer.
     *
     * @param customerId The unique customer identifier
     * @return List of subpoenas
     */
    @GetMapping("/api/v1/legal/customer/{customerId}/subpoenas")
    List<SubpoenaResponse> getSubpoenas(@PathVariable("customerId") String customerId);
}
