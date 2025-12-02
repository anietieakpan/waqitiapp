package com.waqiti.compliance.feign;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * User Service Client
 *
 * FeignClient for user-service integration to suspend accounts
 * and manage user access as part of compliance response.
 *
 * Features:
 * - Circuit breaker for resilience
 * - Fallback for graceful degradation
 * - Automatic retry logic
 *
 * Compliance: Account suspension for regulatory compliance
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@FeignClient(
    name = "user-service",
    url = "${services.user.url}",
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    /**
     * Suspend user account
     *
     * Endpoint: POST /api/v1/users/{userId}/suspend
     *
     * Suspends user account preventing login and platform access.
     * Used for:
     * - Financial crime investigation
     * - Compliance violations
     * - Security incidents
     *
     * @param userId user ID to suspend
     * @param suspendRequest suspend request with reason and case ID
     * @param authToken service-to-service auth token
     * @return suspend response
     */
    @PostMapping("/api/v1/users/{userId}/suspend")
    @CircuitBreaker(name = "user-service", fallbackMethod = "suspendUserFallback")
    Map<String, Object> suspendUser(
        @PathVariable("userId") String userId,
        @RequestBody Map<String, Object> suspendRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Unsuspend user account
     *
     * Endpoint: POST /api/v1/users/{userId}/unsuspend
     *
     * Restores user account access after investigation completion.
     *
     * @param userId user ID to unsuspend
     * @param unsuspendRequest unsuspend request with reason
     * @param authToken service-to-service auth token
     * @return unsuspend response
     */
    @PostMapping("/api/v1/users/{userId}/unsuspend")
    @CircuitBreaker(name = "user-service", fallbackMethod = "unsuspendUserFallback")
    Map<String, Object> unsuspendUser(
        @PathVariable("userId") String userId,
        @RequestBody Map<String, Object> unsuspendRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Flag user account for monitoring
     *
     * Endpoint: POST /api/v1/users/{userId}/flag
     *
     * Flags user for enhanced monitoring without suspension.
     * Used for:
     * - Suspicious activity patterns
     * - Ongoing investigations
     * - Risk mitigation
     *
     * @param userId user ID to flag
     * @param flagRequest flag request with reason and monitoring level
     * @param authToken service-to-service auth token
     * @return flag response
     */
    @PostMapping("/api/v1/users/{userId}/flag")
    @CircuitBreaker(name = "user-service", fallbackMethod = "flagUserFallback")
    Map<String, Object> flagUser(
        @PathVariable("userId") String userId,
        @RequestBody Map<String, Object> flagRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Remove user flag
     *
     * Endpoint: POST /api/v1/users/{userId}/unflag
     *
     * Removes monitoring flag from user account.
     *
     * @param userId user ID to unflag
     * @param unflagRequest unflag request with reason
     * @param authToken service-to-service auth token
     * @return unflag response
     */
    @PostMapping("/api/v1/users/{userId}/unflag")
    @CircuitBreaker(name = "user-service", fallbackMethod = "unflagUserFallback")
    Map<String, Object> unflagUser(
        @PathVariable("userId") String userId,
        @RequestBody Map<String, Object> unflagRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Get user compliance status
     *
     * Endpoint: POST /api/v1/users/{userId}/compliance-status
     *
     * Retrieves user's current compliance status and flags.
     *
     * @param userId user ID
     * @param authToken service-to-service auth token
     * @return compliance status
     */
    @PostMapping("/api/v1/users/{userId}/compliance-status")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getComplianceStatusFallback")
    Map<String, Object> getComplianceStatus(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authToken
    );
}
