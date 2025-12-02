package com.waqiti.compliance.service;

import com.waqiti.compliance.model.KycTier;
import com.waqiti.compliance.repository.KycTierRepository;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * KYC Tier Service
 * 
 * CRITICAL: Manages KYC tier upgrades and compliance tier tracking.
 * Provides comprehensive tier management for regulatory compliance.
 * 
 * COMPLIANCE IMPACT:
 * - Ensures proper KYC tier progression per regulatory requirements
 * - Maintains audit trail for tier changes
 * - Supports BSA, AML, and KYC compliance frameworks
 * 
 * BUSINESS IMPACT:
 * - Enables tier-based transaction limits
 * - Supports risk-based customer onboarding
 * - Ensures regulatory compliance
 * - Prevents penalties through proper tier management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KycTierService {

    private final KycTierRepository kycTierRepository;
    private final ComprehensiveAuditService auditService;

    /**
     * Upgrade user KYC tier
     */
    public KycTier upgradeTier(UUID userId, String newTier, String reason, LocalDateTime effectiveDate) {
        log.info("TIER_UPGRADE: Upgrading tier for user {} to {} reason: {}", userId, newTier, reason);
        
        try {
            // Get current tier
            KycTier currentTier = kycTierRepository.findByUserId(userId)
                .orElse(createDefaultTier(userId));
            
            String previousTier = currentTier.getCurrentTier();
            
            // Validate tier upgrade
            validateTierUpgrade(currentTier, newTier);
            
            // Update tier
            currentTier.setPreviousTier(previousTier);
            currentTier.setCurrentTier(newTier);
            currentTier.setUpgradeReason(reason);
            currentTier.setEffectiveDate(effectiveDate);
            currentTier.setUpdatedAt(LocalDateTime.now());
            
            KycTier savedTier = kycTierRepository.save(currentTier);
            
            // Audit tier upgrade
            auditService.auditCriticalComplianceEvent(
                "KYC_TIER_UPGRADED",
                userId.toString(),
                "KYC tier upgraded from " + previousTier + " to " + newTier,
                Map.of(
                    "userId", userId,
                    "previousTier", previousTier,
                    "newTier", newTier,
                    "reason", reason,
                    "effectiveDate", effectiveDate
                )
            );
            
            log.info("TIER_UPGRADE: Tier upgraded successfully for user {} from {} to {}", 
                    userId, previousTier, newTier);
            
            return savedTier;
            
        } catch (Exception e) {
            log.error("TIER_UPGRADE: Failed to upgrade tier for user {} to {}", userId, newTier, e);
            throw new RuntimeException("Failed to upgrade KYC tier", e);
        }
    }

    /**
     * Get user's current KYC tier
     */
    public KycTier getUserTier(UUID userId) {
        return kycTierRepository.findByUserId(userId)
            .orElse(createDefaultTier(userId));
    }

    /**
     * Check if tier upgrade is valid
     */
    public boolean canUpgradeTier(UUID userId, String targetTier) {
        try {
            KycTier currentTier = getUserTier(userId);
            return isValidUpgrade(currentTier.getCurrentTier(), targetTier);
        } catch (Exception e) {
            log.error("Failed to check tier upgrade eligibility for user {}", userId, e);
            return false;
        }
    }

    // Helper methods

    private KycTier createDefaultTier(UUID userId) {
        KycTier defaultTier = new KycTier();
        defaultTier.setUserId(userId);
        defaultTier.setCurrentTier("BASIC");
        defaultTier.setPreviousTier(null);
        defaultTier.setEffectiveDate(LocalDateTime.now());
        defaultTier.setCreatedAt(LocalDateTime.now());
        defaultTier.setUpdatedAt(LocalDateTime.now());
        return defaultTier;
    }

    private void validateTierUpgrade(KycTier currentTier, String newTier) {
        if (!isValidUpgrade(currentTier.getCurrentTier(), newTier)) {
            throw new IllegalArgumentException("Invalid tier upgrade from " + 
                currentTier.getCurrentTier() + " to " + newTier);
        }
    }

    private boolean isValidUpgrade(String currentTier, String newTier) {
        // Define valid tier progression
        return switch (currentTier) {
            case "BASIC" -> "STANDARD".equals(newTier) || "PREMIUM".equals(newTier);
            case "STANDARD" -> "PREMIUM".equals(newTier) || "VIP".equals(newTier);
            case "PREMIUM" -> "VIP".equals(newTier);
            case "VIP" -> false; // Already at highest tier
            default -> false;
        };
    }
}