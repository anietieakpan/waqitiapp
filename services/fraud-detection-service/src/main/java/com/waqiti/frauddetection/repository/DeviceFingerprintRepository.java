package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.DeviceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Device Fingerprint Repository
 *
 * Data access layer for device fingerprints with optimized queries for
 * device-based fraud detection, cross-device tracking, and risk assessment.
 *
 * PRODUCTION-GRADE QUERIES
 * - Indexed lookups for performance (fingerprint_hash, user_id, ip_address)
 * - Cross-device fraud analysis
 * - Anonymization detection (VPN, Proxy, Tor)
 * - Bot detection
 * - Risk-based filtering
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Repository
public interface DeviceFingerprintRepository extends JpaRepository<DeviceFingerprint, UUID> {

    /**
     * Find device fingerprint by unique hash
     * Uses unique index on fingerprint_hash for O(log n) lookup
     */
    Optional<DeviceFingerprint> findByFingerprintHash(String fingerprintHash);

    /**
     * Check if fingerprint hash exists (for duplicate detection)
     */
    boolean existsByFingerprintHash(String fingerprintHash);

    /**
     * Find all fingerprints associated with a user
     * Uses index on user_id
     */
    List<DeviceFingerprint> findByUserId(String userId);

    /**
     * Find devices by IP address (for IP-based fraud detection)
     * Uses index on ip_address
     */
    List<DeviceFingerprint> findByIpAddress(String ipAddress);

    /**
     * Find high-risk devices
     * Uses index on risk_score
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.riskScore >= :threshold ORDER BY df.riskScore DESC")
    List<DeviceFingerprint> findHighRiskDevices(@Param("threshold") Double threshold);

    /**
     * Find devices using VPN/Proxy/Tor (anonymization tools)
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.isVpn = true OR df.isProxy = true OR df.isTor = true")
    List<DeviceFingerprint> findDevicesUsingAnonymization();

    /**
     * Find bot devices
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.isBot = true ORDER BY df.lastSeen DESC")
    List<DeviceFingerprint> findBotDevices();

    /**
     * Find devices with fraud history
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.fraudCount > 0 ORDER BY df.fraudCount DESC")
    List<DeviceFingerprint> findDevicesWithFraudHistory();

    /**
     * Find devices with high fraud rate
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.fraudCount > 0 AND " +
           "(df.fraudCount * 1.0 / df.usageCount) >= :threshold ORDER BY (df.fraudCount * 1.0 / df.usageCount) DESC")
    List<DeviceFingerprint> findDevicesWithHighFraudRate(@Param("threshold") Double threshold);

    /**
     * Find devices seen in time range
     * Uses index on created_at
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.lastSeen BETWEEN :start AND :end")
    List<DeviceFingerprint> findDevicesSeenBetween(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Find recently active devices for a user
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.userId = :userId AND df.lastSeen >= :since ORDER BY df.lastSeen DESC")
    List<DeviceFingerprint> findRecentDevicesForUser(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since
    );

    /**
     * Find devices from specific country
     */
    List<DeviceFingerprint> findByIpCountry(String ipCountry);

    /**
     * Find devices by browser and OS (for pattern detection)
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.browserName = :browser AND df.operatingSystem = :os")
    List<DeviceFingerprint> findByBrowserAndOS(
        @Param("browser") String browser,
        @Param("os") String operatingSystem
    );

    /**
     * Find suspicious new devices (new + high risk)
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.firstSeen >= :since AND df.riskScore >= :riskThreshold")
    List<DeviceFingerprint> findSuspiciousNewDevices(
        @Param("since") LocalDateTime since,
        @Param("riskThreshold") Double riskThreshold
    );

    /**
     * Find devices shared across multiple users (potential account takeover)
     */
    @Query("SELECT df.fingerprintHash, COUNT(DISTINCT df.userId) FROM DeviceFingerprint df " +
           "GROUP BY df.fingerprintHash HAVING COUNT(DISTINCT df.userId) >= :threshold")
    List<Object[]> findSharedDevices(@Param("threshold") Integer threshold);

    /**
     * Count devices by user (for multi-device fraud detection)
     */
    @Query("SELECT COUNT(df) FROM DeviceFingerprint df WHERE df.userId = :userId")
    Long countDevicesByUserId(@Param("userId") String userId);

    /**
     * Find devices by device type
     */
    List<DeviceFingerprint> findByDeviceType(String deviceType);

    /**
     * Find devices with matching canvas fingerprint (suspicious)
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.canvasFingerprint = :canvasFingerprint " +
           "AND df.fingerprintHash != :excludeHash")
    List<DeviceFingerprint> findDevicesWithMatchingCanvasFingerprint(
        @Param("canvasFingerprint") String canvasFingerprint,
        @Param("excludeHash") String excludeHash
    );

    /**
     * Find devices from high-risk ISPs
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.ipIsp IN :suspiciousIsps")
    List<DeviceFingerprint> findDevicesFromHighRiskIsps(@Param("suspiciousIsps") List<String> suspiciousIsps);

    /**
     * Count total devices
     */
    @Query("SELECT COUNT(df) FROM DeviceFingerprint df")
    Long countAllDevices();

    /**
     * Count high-risk devices
     */
    @Query("SELECT COUNT(df) FROM DeviceFingerprint df WHERE df.riskScore >= :threshold")
    Long countHighRiskDevices(@Param("threshold") Double threshold);

    /**
     * Find stale devices (not seen recently - for cleanup)
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.lastSeen < :cutoffDate")
    List<DeviceFingerprint> findStaleDevices(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find devices with mismatched location and timezone
     */
    @Query("SELECT df FROM DeviceFingerprint df WHERE df.ipCountry != :expectedCountry AND df.timezone = :timezone")
    List<DeviceFingerprint> findDevicesWithLocationMismatch(
        @Param("expectedCountry") String expectedCountry,
        @Param("timezone") String timezone
    );

    /**
     * Aggregate device statistics by country
     */
    @Query("SELECT df.ipCountry, COUNT(df), AVG(df.riskScore) FROM DeviceFingerprint df " +
           "GROUP BY df.ipCountry ORDER BY COUNT(df) DESC")
    List<Object[]> getDeviceStatisticsByCountry();
}
