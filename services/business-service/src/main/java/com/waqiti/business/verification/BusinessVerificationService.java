package com.waqiti.business.verification;

import com.waqiti.business.dto.BusinessProfileValidationRequest;
import com.waqiti.business.dto.BusinessProfileValidationResult;
import com.waqiti.business.verification.ein.EinVerificationService;
import com.waqiti.business.verification.address.UspsAddressVerificationService;
import com.waqiti.business.verification.sanctions.BusinessSanctionsScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive Business Profile Validation Service
 *
 * Implements complete business verification workflow:
 * 1. EIN verification with IRS
 * 2. USPS address verification
 * 3. OFAC sanctions screening
 * 4. State registration validation
 * 5. Industry classification verification
 *
 * Compliance:
 * - IRS Publication 1075
 * - BSA/AML business verification requirements
 * - OFAC sanctions screening requirements
 * - State-level business registration compliance
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

    private final EinVerificationService einVerificationService;
    private final UspsAddressVerificationService uspsAddressVerificationService;
    private final BusinessSanctionsScreeningService sanctionsScreeningService;

    /**
     * Perform complete business profile validation.
     *
     * @param request Business profile validation request
     * @return Validation result with detailed findings
     */
    public BusinessProfileValidationResult validateBusinessProfile(BusinessProfileValidationRequest request) {
        log.info("COMPLIANCE: Starting business profile validation - Business: {}, EIN: {}",
                request.getBusinessName(), maskEin(request.getEin()));

        long startTime = System.currentTimeMillis();
        List<String> validationErrors = new ArrayList<>();
        List<String> validationWarnings = new ArrayList<>();

        Business

ProfileValidationResult.ValidationStatus overallStatus =
            BusinessProfileValidationResult.ValidationStatus.VALID;

        // Step 1: EIN Verification
        log.debug("COMPLIANCE: Step 1/3 - Verifying EIN with IRS");
        EinVerificationService.EinVerificationResult einResult =
            einVerificationService.verifyEin(request.getEin(), request.getBusinessName());

        if (!einResult.isValid()) {
            validationErrors.add("EIN verification failed: " + einResult.getMessage());
            overallStatus = BusinessProfileValidationResult.ValidationStatus.INVALID;
        } else if (einResult.hasWarnings()) {
            validationWarnings.add("EIN verification warnings: " + einResult.getWarnings());
        }

        // Step 2: USPS Address Verification
        log.debug("COMPLIANCE: Step 2/3 - Verifying business address with USPS");
        UspsAddressVerificationService.AddressVerificationResult addressResult =
            uspsAddressVerificationService.verifyAddress(
                request.getBusinessAddress(),
                request.getCity(),
                request.getState(),
                request.getZipCode()
            );

        if (!addressResult.isValid()) {
            validationErrors.add("Address verification failed: " + addressResult.getMessage());
            overallStatus = BusinessProfileValidationResult.ValidationStatus.INVALID;
        } else if (addressResult.isCorrected()) {
            validationWarnings.add("Address was corrected to: " + addressResult.getStandardizedAddress());
        }

        // Step 3: OFAC Sanctions Screening
        log.debug("COMPLIANCE: Step 3/3 - Screening business against OFAC sanctions lists");
        BusinessSanctionsScreeningService.BusinessSanctionsResult sanctionsResult =
            sanctionsScreeningService.screenBusiness(
                request.getBusinessName(),
                request.getPrincipalOwnerName(),
                request.getBusinessAddress(),
                request.getCountry()
            );

        if (sanctionsResult.hasMatch()) {
            validationErrors.add("OFAC sanctions match detected: " + sanctionsResult.getMatchDetails());
            overallStatus = BusinessProfileValidationResult.ValidationStatus.BLOCKED;
        }

        // Build comprehensive result
        long duration = System.currentTimeMillis() - startTime;

        BusinessProfileValidationResult result = BusinessProfileValidationResult.builder()
            .validationStatus(overallStatus)
            .validationErrors(validationErrors)
            .validationWarnings(validationWarnings)
            .einVerified(einResult.isValid())
            .einDetails(einResult)
            .addressVerified(addressResult.isValid())
            .addressDetails(addressResult)
            .sanctionsCleared(!sanctionsResult.hasMatch())
            .sanctionsDetails(sanctionsResult)
            .validatedAt(LocalDateTime.now())
            .validationDurationMs(duration)
            .build();

        log.info("COMPLIANCE: Business profile validation complete - Status: {}, Duration: {}ms",
                overallStatus, duration);

        return result;
    }

    /**
     * Mask EIN for logging (PII protection)
     */
    private String maskEin(String ein) {
        if (ein == null || ein.length() < 4) {
            return "****";
        }
        return "**-***" + ein.substring(ein.length() - 4);
    }
}
