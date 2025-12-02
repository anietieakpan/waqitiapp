package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.DeviceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Device Profile Repository
 *
 * Data access layer for device profiles with optimized queries for
 * device fraud detection, device farm identification, and risk assessment.
 *
 * PRODUCTION-GRADE QUERIES
 * - Indexed lookups for performance
 * - Device farm detection queries
 * - Risk-based filtering
 * - Impossible travel analysis support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Repository
public interface DeviceProfileRepository extends JpaRepository<DeviceProfile, UUID> {

    /**
     * Find device profile by device fingerprint
     * Uses unique index on device_fingerprint for O(log n) lookup
     */
    Optional<DeviceProfile> findByDeviceFingerprint(String deviceFingerprint);

    /**
     * Find devices by risk level
     * Uses index on current_risk_level
     */
    List<DeviceProfile> findByCurrentRiskLevel(String riskLevel);

    /**
     * Find high-risk devices seen recently
     * Combines risk level and temporal filtering
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.currentRiskLevel = 'HIGH' AND dp.lastSeen >= :since")
    List<DeviceProfile> findHighRiskDevicesSince(@Param("since") LocalDateTime since);

    /**
     * Find critical risk devices (device farms, high fraud)
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.currentRiskLevel = 'CRITICAL' ORDER BY dp.lastSeen DESC")
    List<DeviceProfile> findCriticalRiskDevices();

    /**
     * Find potential device farms (many users on single device)
     * Device farm threshold typically 10+ users
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE SIZE(dp.associatedUserIds) >= :threshold ORDER BY SIZE(dp.associatedUserIds) DESC")
    List<DeviceProfile> findPotentialDeviceFarms(@Param("threshold") int threshold);

    /**
     * Find devices with impossible travel detected
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.impossibleTravelDetected = true AND dp.lastSeen >= :since")
    List<DeviceProfile> findDevicesWithImpossibleTravel(@Param("since") LocalDateTime since);

    /**
     * Find devices with high fraud rates (>10%)
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.fraudRate > :threshold ORDER BY dp.fraudRate DESC")
    List<DeviceProfile> findDevicesWithHighFraudRate(@Param("threshold") double threshold);

    /**
     * Find devices associated with specific user
     * For cross-device fraud analysis
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE :userId MEMBER OF dp.associatedUserIds")
    List<DeviceProfile> findDevicesByUserId(@Param("userId") UUID userId);

    /**
     * Find shared devices (multiple users, not yet device farm level)
     * Typically 4-9 users (above normal 3, below farm threshold 10)
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE SIZE(dp.associatedUserIds) BETWEEN :minUsers AND :maxUsers")
    List<DeviceProfile> findSharedDevices(@Param("minUsers") int minUsers, @Param("maxUsers") int maxUsers);

    /**
     * Find new devices with high activity (suspicious)
     * New = <7 days old, High activity = >50 transactions
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.firstSeen >= :since AND dp.totalTransactions > :txThreshold")
    List<DeviceProfile> findNewDevicesWithHighActivity(
        @Param("since") LocalDateTime since,
        @Param("txThreshold") long txThreshold
    );

    /**
     * Count devices by risk level (for analytics)
     */
    @Query("SELECT dp.currentRiskLevel, COUNT(dp) FROM DeviceProfile dp GROUP BY dp.currentRiskLevel")
    List<Object[]> countDevicesByRiskLevel();

    /**
     * Find devices from specific country
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.lastKnownCountry = :countryCode")
    List<DeviceProfile> findDevicesByCountry(@Param("countryCode") String countryCode);

    /**
     * Check if device fingerprint exists
     */
    boolean existsByDeviceFingerprint(String deviceFingerprint);

    /**
     * Find recently active devices (for monitoring)
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.lastSeen >= :since ORDER BY dp.lastSeen DESC")
    List<DeviceProfile> findRecentlyActiveDevices(@Param("since") LocalDateTime since);

    /**
     * Find devices with recent fraud activity
     */
    @Query("SELECT dp FROM DeviceProfile dp WHERE dp.lastFraudDate >= :since ORDER BY dp.lastFraudDate DESC")
    List<DeviceProfile> findDevicesWithRecentFraud(@Param("since") LocalDateTime since);
}
