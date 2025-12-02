package com.waqiti.common.sanctions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Sanctions Screening Service Interface
 *
 * Provides sanctions screening capabilities for OFAC/UN/EU sanctions lists.
 *
 * DEPRECATED: This is a placeholder implementation.
 * Use the dedicated compliance-service for actual sanctions screening.
 *
 * CRITICAL SECURITY: For production use, integrate with:
 * - OFAC SDN (Specially Designated Nationals) List
 * - OFAC Consolidated Sanctions List
 * - EU Sanctions List
 * - UN Security Council Sanctions List
 * - Country-based sanctions
 *
 * Compliance:
 * - 31 CFR Part 501 (OFAC Regulations)
 * - Bank Secrecy Act (BSA)
 * - USA PATRIOT Act Section 326
 * - FinCEN SAR Requirements
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @deprecated Use compliance-service ProductionOfacScreeningService instead
 */
@Deprecated
@Service
@Slf4j
public class SanctionsScreeningService {

    /**
     * Check if user/entity is on sanctions list.
     *
     * STUB IMPLEMENTATION: Integrate with compliance-service ProductionOfacScreeningService for production use.
     *
     * @param entityId User ID, merchant ID, or entity identifier
     * @return true if entity is sanctioned, false otherwise
     * @deprecated Use compliance-service ProductionOfacScreeningService directly via Feign client
     */
    @Deprecated
    public boolean isOnSanctionsList(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            log.warn("Sanctions check called with null/blank entityId - returning false");
            return false;
        }

        log.warn("STUB: Sanctions screening called for entity: {} - integrate with compliance-service for production", entityId);

        // STUB: Return false (not sanctioned)
        // In production, integrate with compliance-service via Feign client
        return false;
    }

    /**
     * Perform enhanced sanctions screening with full customer details.
     *
     * @param customerId Customer identifier
     * @param fullName Full legal name
     * @param dateOfBirth Date of birth (optional)
     * @param countryCode ISO country code
     * @param address Physical address (optional)
     * @return true if sanctioned, false otherwise
     * @deprecated Use compliance-service ProductionOfacScreeningService directly via Feign client
     */
    @Deprecated
    public boolean performEnhancedScreening(String customerId, String fullName,
                                           LocalDate dateOfBirth, String countryCode,
                                           String address) {
        log.warn("STUB: Enhanced sanctions screening called for customerId: {} - integrate with compliance-service", customerId);
        return false;
    }

    /**
     * Check if country is sanctioned.
     *
     * @param countryCode ISO 2-letter country code
     * @return true if country is sanctioned
     * @deprecated Use compliance-service ProductionOfacScreeningService directly via Feign client
     */
    @Deprecated
    public boolean isCountrySanctioned(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            return false;
        }
        log.warn("STUB: Country sanctions check called for: {} - integrate with compliance-service", countryCode);
        return false;
    }

    /**
     * Mark a sanctions match as false positive (for compliance review workflow).
     *
     * @param customerId Customer ID
     * @param matchId Match ID
     * @param reason Reason for false positive determination
     * @deprecated Use compliance-service ProductionOfacScreeningService directly via Feign client
     */
    @Deprecated
    public void markFalsePositive(String customerId, String matchId, String reason) {
        log.warn("STUB: Mark false positive called - integrate with compliance-service");
    }

    /**
     * Get sanctions screening statistics.
     *
     * @return null (stub implementation)
     * @deprecated Use compliance-service ProductionOfacScreeningService directly via Feign client
     */
    @Deprecated
    public Object getStatistics() {
        log.warn("STUB: Get statistics called - integrate with compliance-service");
        return null;
    }
}
