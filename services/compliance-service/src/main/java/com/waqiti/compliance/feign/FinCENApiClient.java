package com.waqiti.compliance.feign;

import com.waqiti.compliance.dto.SARFiling;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * FinCEN API Client
 *
 * FeignClient for FinCEN (Financial Crimes Enforcement Network) integration
 * to file Suspicious Activity Reports (SAR) and comply with BSA/AML regulations.
 *
 * Features:
 * - Circuit breaker for resilience
 * - Fallback for graceful degradation
 * - Automatic retry logic
 *
 * Compliance: BSA/AML, FinCEN SAR Requirements (31 CFR 1020.320)
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@FeignClient(
    name = "fincen-api",
    url = "${external.fincen.api.url}",
    fallback = FinCENApiClientFallback.class
)
public interface FinCENApiClient {

    /**
     * File Suspicious Activity Report (SAR) with FinCEN
     *
     * Endpoint: POST /api/v1/sar/file
     *
     * Requirements:
     * - Must be filed within 30 days of detection
     * - Emergency SAR can be filed immediately for critical cases
     * - Returns SAR reference number for tracking
     *
     * @param sarData SAR filing data (JSON payload)
     * @param apiKey FinCEN API key from Vault
     * @return SAR filing response with reference number
     */
    @PostMapping("/api/v1/sar/file")
    @CircuitBreaker(name = "fincen-api", fallbackMethod = "fileSARFallback")
    SARFiling fileSAR(
        @RequestBody Map<String, Object> sarData,
        @RequestHeader("X-FinCEN-API-Key") String apiKey
    );

    /**
     * File Emergency SAR with FinCEN (expedited processing)
     *
     * Endpoint: POST /api/v1/sar/file-emergency
     *
     * Use for:
     * - Terrorism financing
     * - Money laundering over $1M
     * - Immediate threat to national security
     *
     * @param sarData emergency SAR data
     * @param apiKey FinCEN API key
     * @return emergency SAR filing response
     */
    @PostMapping("/api/v1/sar/file-emergency")
    @CircuitBreaker(name = "fincen-api", fallbackMethod = "fileEmergencySARFallback")
    SARFiling fileEmergencySAR(
        @RequestBody Map<String, Object> sarData,
        @RequestHeader("X-FinCEN-API-Key") String apiKey
    );

    /**
     * Verify SAR filing status with FinCEN
     *
     * Endpoint: POST /api/v1/sar/verify
     *
     * @param sarReferenceNumber SAR reference number
     * @param apiKey FinCEN API key
     * @return SAR status response
     */
    @PostMapping("/api/v1/sar/verify")
    @CircuitBreaker(name = "fincen-api", fallbackMethod = "verifySARFallback")
    Map<String, Object> verifySARStatus(
        @RequestBody Map<String, String> sarReferenceNumber,
        @RequestHeader("X-FinCEN-API-Key") String apiKey
    );
}
