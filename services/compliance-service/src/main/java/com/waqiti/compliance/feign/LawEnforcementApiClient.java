package com.waqiti.compliance.feign;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Law Enforcement API Client
 *
 * FeignClient for law enforcement integration (FBI IC3, SEC, local authorities)
 * to report financial crimes and comply with regulatory notification requirements.
 *
 * Features:
 * - Circuit breaker for resilience
 * - Fallback for graceful degradation
 * - Automatic retry logic
 * - Multi-agency support
 *
 * Compliance: USA PATRIOT Act Section 314, SEC regulations, FBI IC3
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@FeignClient(
    name = "law-enforcement-api",
    url = "${external.law-enforcement.api.url}",
    fallback = LawEnforcementApiClientFallback.class
)
public interface LawEnforcementApiClient {

    /**
     * Notify FBI Internet Crime Complaint Center (IC3)
     *
     * Endpoint: POST /api/v1/fbi/ic3/notify
     *
     * Use for:
     * - Wire fraud
     * - Internet fraud
     * - Cryptocurrency fraud
     * - Identity theft
     *
     * @param crimeData crime data payload
     * @param apiKey FBI IC3 API key
     * @return notification response with case number
     */
    @PostMapping("/api/v1/fbi/ic3/notify")
    @CircuitBreaker(name = "law-enforcement-api", fallbackMethod = "notifyFBIFallback")
    Map<String, Object> notifyFBI(
        @RequestBody Map<String, Object> crimeData,
        @RequestHeader("X-FBI-API-Key") String apiKey
    );

    /**
     * Notify SEC (Securities and Exchange Commission)
     *
     * Endpoint: POST /api/v1/sec/notify
     *
     * Use for:
     * - Securities fraud
     * - Insider trading
     * - Market manipulation
     *
     * @param crimeData crime data payload
     * @param apiKey SEC API key
     * @return notification response with TCR number (Tips, Complaints, Referrals)
     */
    @PostMapping("/api/v1/sec/notify")
    @CircuitBreaker(name = "law-enforcement-api", fallbackMethod = "notifySECFallback")
    Map<String, Object> notifySEC(
        @RequestBody Map<String, Object> crimeData,
        @RequestHeader("X-SEC-API-Key") String apiKey
    );

    /**
     * Notify local law enforcement
     *
     * Endpoint: POST /api/v1/local/notify
     *
     * Use for:
     * - Local jurisdiction crimes
     * - Coordination with local police
     *
     * @param crimeData crime data payload
     * @param apiKey Local LE API key
     * @return notification response
     */
    @PostMapping("/api/v1/local/notify")
    @CircuitBreaker(name = "law-enforcement-api", fallbackMethod = "notifyLocalLEFallback")
    Map<String, Object> notifyLocalLawEnforcement(
        @RequestBody Map<String, Object> crimeData,
        @RequestHeader("X-Local-LE-API-Key") String apiKey
    );

    /**
     * Emergency law enforcement notification (all agencies)
     *
     * Endpoint: POST /api/v1/emergency/notify-all
     *
     * Use for:
     * - Terrorism financing
     * - Immediate threat to national security
     * - High-value money laundering
     *
     * @param crimeData emergency crime data
     * @param apiKey Emergency notification API key
     * @return emergency notification response
     */
    @PostMapping("/api/v1/emergency/notify-all")
    @CircuitBreaker(name = "law-enforcement-api", fallbackMethod = "notifyEmergencyFallback")
    Map<String, Object> notifyEmergency(
        @RequestBody Map<String, Object> crimeData,
        @RequestHeader("X-Emergency-API-Key") String apiKey
    );
}
