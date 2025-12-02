package com.waqiti.familyaccount.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * KYC (Know Your Customer) Service Feign Client
 *
 * Feign client for interacting with the KYC service microservice.
 * Handles identity verification and compliance checks required for
 * family account operations.
 *
 * All parents must be KYC-verified to create or manage family accounts
 * in compliance with financial regulations (BSA/AML, FATF recommendations).
 *
 * Timeout Configuration:
 * - Connect Timeout: 5 seconds
 * - Read Timeout: 10 seconds (KYC checks can be slower)
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@FeignClient(
    name = "kyc-service",
    url = "${feign.client.config.kyc-service.url:http://localhost:8086}",
    configuration = KYCServiceClientConfig.class
)
public interface KYCServiceClient {

    /**
     * Check if user has completed KYC verification
     *
     * Verifies that a user has successfully completed the KYC process
     * and their identity has been validated.
     *
     * KYC verification includes:
     * - Government-issued ID verification
     * - Address verification
     * - Date of birth confirmation
     * - Photo verification (liveness check)
     * - Sanctions screening (OFAC, UN, EU lists)
     * - PEP (Politically Exposed Person) screening
     * - Adverse media screening
     *
     * @param userId The unique identifier of the user
     * @return true if KYC is complete and verified, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/is-verified")
    Boolean isKycVerified(@PathVariable("userId") String userId);

    /**
     * Get KYC verification status
     *
     * Retrieves the current KYC verification status for a user.
     *
     * Possible statuses:
     * - NOT_STARTED: User has not begun KYC process
     * - IN_PROGRESS: KYC documents submitted, under review
     * - VERIFIED: KYC successfully completed
     * - REJECTED: KYC verification failed
     * - EXPIRED: KYC verification has expired (periodic re-verification required)
     * - PENDING_RESUBMISSION: User needs to resubmit documents
     *
     * @param userId The unique identifier of the user
     * @return KYC status string
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/status")
    String getKycStatus(@PathVariable("userId") String userId);

    /**
     * Get KYC verification details
     *
     * Retrieves comprehensive KYC verification information including:
     * - Verification status
     * - Verification date
     * - Document types submitted
     * - Risk level assessment
     * - Sanctions screening results
     * - PEP status
     * - Next re-verification date
     * - Verification method used
     *
     * @param userId The unique identifier of the user
     * @return Map containing KYC details
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/details")
    Map<String, Object> getKycDetails(@PathVariable("userId") String userId);

    /**
     * Check if user is on sanctions list
     *
     * Performs real-time sanctions screening against:
     * - OFAC (Office of Foreign Assets Control) lists
     * - UN Security Council Consolidated List
     * - EU Consolidated Financial Sanctions List
     * - UK HM Treasury Sanctions List
     * - Country-specific sanctions lists
     *
     * @param userId The unique identifier of the user
     * @return true if user is on any sanctions list, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/sanctions-check")
    Boolean isOnSanctionsList(@PathVariable("userId") String userId);

    /**
     * Check if user is a Politically Exposed Person (PEP)
     *
     * Determines if a user is classified as a PEP, which requires
     * enhanced due diligence under AML regulations.
     *
     * PEP categories:
     * - Foreign PEP: Government officials from other countries
     * - Domestic PEP: Government officials from same country
     * - International Organization PEP: Officials from international bodies
     * - PEP Associate: Close business associates of PEPs
     * - PEP Family Member: Immediate family members of PEPs
     *
     * @param userId The unique identifier of the user
     * @return true if user is classified as PEP, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/pep-check")
    Boolean isPoliticallyExposedPerson(@PathVariable("userId") String userId);

    /**
     * Get user's risk level
     *
     * Retrieves the AML/CFT risk level assigned to a user based on:
     * - KYC verification results
     * - Country of residence (high-risk jurisdictions)
     * - Transaction patterns
     * - PEP status
     * - Sanctions screening results
     * - Source of funds
     * - Occupation
     *
     * Risk Levels:
     * - LOW: Standard due diligence
     * - MEDIUM: Enhanced monitoring
     * - HIGH: Enhanced due diligence, closer scrutiny
     * - PROHIBITED: Account creation/transactions blocked
     *
     * @param userId The unique identifier of the user
     * @return Risk level string (LOW, MEDIUM, HIGH, PROHIBITED)
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/risk-level")
    String getUserRiskLevel(@PathVariable("userId") String userId);

    /**
     * Initiate KYC verification for user
     *
     * Starts the KYC verification process for a user.
     * Generates a KYC session and returns verification URL.
     *
     * @param userId The unique identifier of the user
     * @return Map containing KYC session ID and verification URL
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/kyc/{userId}/initiate")
    Map<String, Object> initiateKycVerification(@PathVariable("userId") String userId);

    /**
     * Check if KYC re-verification is required
     *
     * Determines if a user needs to undergo KYC re-verification.
     * Re-verification may be required due to:
     * - Periodic review (annually or biannually)
     * - Significant change in transaction patterns
     * - Regulatory requirements
     * - Risk level escalation
     * - Document expiration
     *
     * @param userId The unique identifier of the user
     * @return true if re-verification is required, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/requires-reverification")
    Boolean requiresReverification(@PathVariable("userId") String userId);

    /**
     * Get KYC verification date
     *
     * Retrieves the date when the user's KYC was last verified.
     *
     * @param userId The unique identifier of the user
     * @return ISO 8601 formatted date string
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/verification-date")
    String getKycVerificationDate(@PathVariable("userId") String userId);

    /**
     * Verify age for family account eligibility
     *
     * Verifies that a user meets the minimum age requirement for
     * creating or managing a family account (typically 18+).
     *
     * Age is verified from government-issued ID during KYC process,
     * not self-reported.
     *
     * @param userId The unique identifier of the user
     * @param minimumAge The minimum required age
     * @return true if user meets or exceeds minimum age, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/verify-age")
    Boolean verifyMinimumAge(
        @PathVariable("userId") String userId,
        @RequestParam("minimumAge") int minimumAge
    );

    /**
     * Bulk KYC status check
     *
     * Checks KYC verification status for multiple users at once.
     * Useful when adding multiple family members.
     *
     * @param userIds Comma-separated list of user IDs
     * @return Map with userId as key and verification status as value
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/bulk-check")
    Map<String, String> bulkKycStatusCheck(@RequestParam("userIds") String userIds);

    /**
     * Get enhanced due diligence status
     *
     * Checks if user has undergone enhanced due diligence (EDD).
     * Required for high-risk customers.
     *
     * @param userId The unique identifier of the user
     * @return true if EDD completed, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/kyc/{userId}/edd-status")
    Boolean hasEnhancedDueDiligence(@PathVariable("userId") String userId);
}
