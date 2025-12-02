package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.entity.CustomerProfile;
import com.waqiti.frauddetection.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer Profile Service
 *
 * Manages customer behavioral profiles for fraud detection.
 * Tracks transaction patterns, risk history, and behavioral analytics.
 *
 * PRODUCTION-GRADE IMPLEMENTATION
 * - Thread-safe with optimistic locking
 * - Cache-enabled for performance
 * - Comprehensive audit logging
 * - Transactional consistency
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;

    /**
     * Get customer profile with caching for performance
     */
    @Cacheable(value = "customerProfiles", key = "#customerId", unless = "#result == null")
    public Optional<CustomerProfile> getCustomerProfile(UUID customerId) {
        log.debug("Fetching customer profile for: {}", customerId);

        if (customerId == null) {
            log.warn("Attempted to fetch profile with null customer ID");
            return Optional.empty();
        }

        return customerProfileRepository.findByCustomerId(customerId);
    }

    /**
     * Update customer profile after fraud check
     *
     * This method is called after each transaction to update the customer's
     * behavioral profile, risk score, and transaction history.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @CacheEvict(value = "customerProfiles", key = "#customerId")
    public void updateProfileAfterTransaction(
            UUID customerId,
            FraudCheckRequest request,
            double riskScore,
            boolean fraudDetected) {

        log.debug("Updating customer profile for: {} after transaction", customerId);

        try {
            CustomerProfile profile = customerProfileRepository
                .findByCustomerId(customerId)
                .orElseGet(() -> createNewCustomerProfile(customerId));

            // Update transaction statistics
            profile.setTotalTransactions(profile.getTotalTransactions() + 1);
            profile.setLastTransactionDate(LocalDateTime.now());
            profile.setLastTransactionAmount(request.getAmount());

            // Update cumulative transaction volume
            BigDecimal newVolume = profile.getTotalTransactionVolume()
                .add(request.getAmount());
            profile.setTotalTransactionVolume(newVolume);

            // Update average transaction amount (weighted)
            BigDecimal currentAvg = profile.getAverageTransactionAmount();
            long txCount = profile.getTotalTransactions();
            BigDecimal newAvg = currentAvg
                .multiply(BigDecimal.valueOf(txCount - 1))
                .add(request.getAmount())
                .divide(BigDecimal.valueOf(txCount), 2, java.math.RoundingMode.HALF_EVEN);
            profile.setAverageTransactionAmount(newAvg);

            // Update risk metrics
            updateRiskMetrics(profile, riskScore, fraudDetected);

            // Update behavioral patterns
            updateBehavioralPatterns(profile, request);

            // Update location history
            if (request.getCountryCode() != null) {
                updateLocationHistory(profile, request.getCountryCode());
            }

            // Update device history
            if (request.getDeviceFingerprint() != null) {
                updateDeviceHistory(profile, request.getDeviceFingerprint());
            }

            // Save updated profile
            customerProfileRepository.save(profile);

            log.info("Customer profile updated successfully for: {}", customerId);

        } catch (Exception e) {
            log.error("Error updating customer profile for: {}", customerId, e);
            // Don't throw - profile update is not critical to transaction processing
            // Log for monitoring and alerting
        }
    }

    /**
     * Update risk metrics in customer profile
     */
    private void updateRiskMetrics(CustomerProfile profile, double riskScore, boolean fraudDetected) {
        // Update running average risk score (exponential moving average)
        double alpha = 0.3; // Weight for new observation
        double currentAvgRisk = profile.getAverageRiskScore();
        double newAvgRisk = (alpha * riskScore) + ((1 - alpha) * currentAvgRisk);
        profile.setAverageRiskScore(newAvgRisk);

        // Update fraud flags
        if (fraudDetected) {
            profile.setFraudCount(profile.getFraudCount() + 1);
            profile.setLastFraudDate(LocalDateTime.now());
        }

        // Update risk level based on recent behavior
        profile.setCurrentRiskLevel(determineRiskLevel(profile));
    }

    /**
     * Update behavioral patterns
     */
    private void updateBehavioralPatterns(CustomerProfile profile, FraudCheckRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // Update typical transaction hours
        int hour = now.getHour();
        profile.addTypicalTransactionHour(hour);

        // Update typical transaction days
        int dayOfWeek = now.getDayOfWeek().getValue();
        profile.addTypicalTransactionDay(dayOfWeek);

        // Update velocity patterns
        profile.updateVelocityPattern(now);
    }

    /**
     * Update location history
     */
    private void updateLocationHistory(CustomerProfile profile, String countryCode) {
        profile.addCountryToHistory(countryCode);
        profile.setLastKnownCountry(countryCode);
    }

    /**
     * Update device history
     */
    private void updateDeviceHistory(CustomerProfile profile, String deviceFingerprint) {
        profile.addDeviceToHistory(deviceFingerprint);
        profile.setLastKnownDevice(deviceFingerprint);
    }

    /**
     * Create new customer profile for first-time user
     */
    private CustomerProfile createNewCustomerProfile(UUID customerId) {
        log.info("Creating new customer profile for: {}", customerId);

        return CustomerProfile.builder()
            .customerId(customerId)
            .createdDate(LocalDateTime.now())
            .totalTransactions(0L)
            .totalTransactionVolume(BigDecimal.ZERO)
            .averageTransactionAmount(BigDecimal.ZERO)
            .averageRiskScore(0.0)
            .fraudCount(0)
            .currentRiskLevel("LOW")
            .accountAge(0)
            .build();
    }

    /**
     * Determine risk level based on profile metrics
     */
    private String determineRiskLevel(CustomerProfile profile) {
        double avgRisk = profile.getAverageRiskScore();
        long fraudCount = profile.getFraudCount();
        long totalTx = profile.getTotalTransactions();

        // High risk criteria
        if (fraudCount >= 3 || avgRisk > 0.75) {
            return "HIGH";
        }

        // Medium risk criteria
        if (fraudCount >= 1 || avgRisk > 0.50) {
            return "MEDIUM";
        }

        // Check fraud ratio
        if (totalTx > 0 && (fraudCount * 1.0 / totalTx) > 0.1) {
            return "MEDIUM";
        }

        return "LOW";
    }

    /**
     * Get customer risk assessment
     */
    @Cacheable(value = "customerRiskAssessment", key = "#customerId")
    public CustomerRiskAssessment getRiskAssessment(UUID customerId) {
        log.debug("Getting risk assessment for customer: {}", customerId);

        Optional<CustomerProfile> profileOpt = getCustomerProfile(customerId);

        if (profileOpt.isEmpty()) {
            return CustomerRiskAssessment.builder()
                .customerId(customerId)
                .riskLevel("UNKNOWN")
                .riskScore(0.5) // Medium default for unknown customers
                .isNewCustomer(true)
                .requiresEnhancedDueDiligence(true)
                .build();
        }

        CustomerProfile profile = profileOpt.get();

        return CustomerRiskAssessment.builder()
            .customerId(customerId)
            .riskLevel(profile.getCurrentRiskLevel())
            .riskScore(profile.getAverageRiskScore())
            .fraudCount(profile.getFraudCount())
            .totalTransactions(profile.getTotalTransactions())
            .accountAge(calculateAccountAge(profile.getCreatedDate()))
            .isNewCustomer(profile.getTotalTransactions() < 5)
            .requiresEnhancedDueDiligence(shouldRequireEDD(profile))
            .lastTransactionDate(profile.getLastTransactionDate())
            .build();
    }

    /**
     * Calculate account age in days
     */
    private long calculateAccountAge(LocalDateTime createdDate) {
        if (createdDate == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(createdDate, LocalDateTime.now());
    }

    /**
     * Determine if Enhanced Due Diligence is required
     */
    private boolean shouldRequireEDD(CustomerProfile profile) {
        // EDD required if:
        // - High risk level
        // - Recent fraud activity
        // - High transaction volume in short time

        if ("HIGH".equals(profile.getCurrentRiskLevel())) {
            return true;
        }

        if (profile.getLastFraudDate() != null) {
            long daysSinceLastFraud = java.time.temporal.ChronoUnit.DAYS
                .between(profile.getLastFraudDate(), LocalDateTime.now());
            if (daysSinceLastFraud < 90) {
                return true;
            }
        }

        // Check for suspicious velocity
        if (profile.getTotalTransactions() > 100 &&
            calculateAccountAge(profile.getCreatedDate()) < 30) {
            return true;
        }

        return false;
    }

    /**
     * DTO for customer risk assessment
     */
    @lombok.Builder
    @lombok.Data
    public static class CustomerRiskAssessment {
        private UUID customerId;
        private String riskLevel;
        private double riskScore;
        private long fraudCount;
        private long totalTransactions;
        private long accountAge;
        private boolean isNewCustomer;
        private boolean requiresEnhancedDueDiligence;
        private LocalDateTime lastTransactionDate;
    }
}
