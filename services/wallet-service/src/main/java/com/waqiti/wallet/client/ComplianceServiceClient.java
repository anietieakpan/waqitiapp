package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.RegulatoryClosureRequest;
import com.waqiti.wallet.dto.WalletClosureComplianceRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Compliance Service
 *
 * Handles communication with the compliance-service for regulatory
 * reporting and compliance tracking.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@FeignClient(
    name = "compliance-service",
    url = "${services.compliance-service.url:http://compliance-service:8085}",
    fallback = ComplianceServiceClientFallback.class
)
public interface ComplianceServiceClient {

    /**
     * Report wallet closure to compliance service
     *
     * @param request wallet closure compliance request
     */
    @PostMapping("/api/v1/compliance/wallet-closure")
    @CircuitBreaker(name = "compliance-service")
    void reportWalletClosure(@RequestBody WalletClosureComplianceRequest request);

    /**
     * Report regulatory wallet closure to regulators
     *
     * @param request regulatory closure request
     */
    @PostMapping("/api/v1/compliance/regulatory-wallet-closure")
    @CircuitBreaker(name = "compliance-service")
    void reportRegulatoryWalletClosure(@RequestBody RegulatoryClosureRequest request);
}
