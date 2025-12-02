package com.waqiti.investment.client;

import com.waqiti.investment.client.dto.UserProfileDto;
import com.waqiti.investment.client.dto.UserTaxInfoDto;
import com.waqiti.investment.exception.UserServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * User Service Client - Feign client for user-service integration
 *
 * CRITICAL: This client retrieves sensitive user information including:
 * - Personal identification data (name, address)
 * - Tax identification numbers (SSN/EIN) - encrypted
 *
 * Security:
 * - All requests must include service-to-service authentication token
 * - TIN data is encrypted in transit and at rest
 * - Circuit breaker pattern for fault tolerance
 * - Retry logic with exponential backoff
 * - Bulkhead pattern to prevent cascade failures
 *
 * Compliance:
 * - IRS Publication 1075 (Safeguarding Tax Information)
 * - GLBA (Gramm-Leach-Bliley Act) privacy requirements
 * - SOC 2 Type II data protection controls
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${waqiti.services.user-service.url:http://user-service:8080}")
    private String userServiceBaseUrl;

    @Value("${waqiti.services.user-service.api-key:}")
    private String serviceApiKey;

    /**
     * Retrieve user profile information (name, address, contact).
     *
     * @param customerId UUID of the customer
     * @return UserProfileDto with user profile data
     * @throws UserServiceException if user service is unavailable or user not found
     */
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserProfileFallback")
    @Retry(name = "user-service")
    @Bulkhead(name = "user-service")
    public UserProfileDto getUserProfile(UUID customerId) {
        log.debug("Retrieving user profile for customer: {}", customerId);

        try {
            String url = userServiceBaseUrl + "/api/v1/users/" + customerId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Auth", serviceApiKey);
            headers.set("X-Requesting-Service", "investment-service");

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<UserProfileDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                UserProfileDto.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.debug("Successfully retrieved user profile for customer: {}", customerId);
                return response.getBody();
            } else {
                log.warn("User profile not found for customer: {}", customerId);
                throw new UserServiceException("User profile not found for customer: " + customerId);
            }

        } catch (RestClientException e) {
            log.error("Failed to retrieve user profile for customer: {}", customerId, e);
            throw new UserServiceException("Failed to retrieve user profile", e);
        }
    }

    /**
     * Retrieve user tax information (TIN/SSN - encrypted).
     *
     * CRITICAL SECURITY: This method retrieves sensitive tax identification data.
     * - TIN/SSN is encrypted in transit (TLS) and at rest
     * - Decryption should only occur in secure, isolated context
     * - Access is logged for audit and compliance
     *
     * @param customerId UUID of the customer
     * @return UserTaxInfoDto with encrypted TIN and tax-related data
     * @throws UserServiceException if user service is unavailable or data not found
     */
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserTaxInfoFallback")
    @Retry(name = "user-service")
    @Bulkhead(name = "user-service")
    public UserTaxInfoDto getUserTaxInfo(UUID customerId) {
        log.info("SECURITY AUDIT: Retrieving tax information for customer: {}", customerId);

        try {
            String url = userServiceBaseUrl + "/api/v1/users/" + customerId + "/tax-info";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Auth", serviceApiKey);
            headers.set("X-Requesting-Service", "investment-service");
            headers.set("X-Data-Classification", "PII-SENSITIVE");

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<UserTaxInfoDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                UserTaxInfoDto.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("SECURITY AUDIT: Successfully retrieved tax information for customer: {}", customerId);
                return response.getBody();
            } else {
                log.warn("Tax information not found for customer: {}", customerId);
                throw new UserServiceException("Tax information not found for customer: " + customerId);
            }

        } catch (RestClientException e) {
            log.error("Failed to retrieve tax information for customer: {}", customerId, e);
            throw new UserServiceException("Failed to retrieve tax information", e);
        }
    }

    /**
     * Fallback method for getUserProfile circuit breaker.
     */
    private UserProfileDto getUserProfileFallback(UUID customerId, Throwable t) {
        log.error("CIRCUIT BREAKER: getUserProfile fallback triggered for customer: {}", customerId, t);
        throw new UserServiceException("User service is currently unavailable - circuit breaker open", t);
    }

    /**
     * Fallback method for getUserTaxInfo circuit breaker.
     */
    private UserTaxInfoDto getUserTaxInfoFallback(UUID customerId, Throwable t) {
        log.error("CIRCUIT BREAKER: getUserTaxInfo fallback triggered for customer: {}", customerId, t);
        throw new UserServiceException("User service is currently unavailable - circuit breaker open", t);
    }
}
