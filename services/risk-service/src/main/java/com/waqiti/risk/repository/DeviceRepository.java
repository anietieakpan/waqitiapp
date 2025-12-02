package com.waqiti.risk.repository;

import com.waqiti.risk.model.Device;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Device Repository
 *
 * MongoDB repository for managing device data
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface DeviceRepository extends MongoRepository<Device, String> {

    /**
     * Find device by device ID
     */
    Optional<Device> findByDeviceId(String deviceId);

    /**
     * Find devices by user ID
     */
    List<Device> findByUserId(String userId);

    /**
     * Find devices associated with user
     */
    @Query("{'associatedUserIds': {'$in': [?0]}}")
    List<Device> findDevicesForUser(String userId);

    /**
     * Find device profile by device ID
     */
    @Query("{'deviceId': ?0}")
    Optional<com.waqiti.risk.dto.DeviceProfile> findByDeviceId(String deviceId);

    /**
     * Find trusted devices
     */
    List<Device> findByIsTrusted(Boolean isTrusted);

    /**
     * Find trusted devices for user
     */
    @Query("{'userId': ?0, 'isTrusted': true}")
    List<Device> findTrustedDevicesForUser(String userId);

    /**
     * Find devices by trust level
     */
    List<Device> findByTrustLevel(String trustLevel);

    /**
     * Find high-risk devices
     */
    @Query("{'$or': [" +
           "{'trustScore': {'$lt': ?0}}, " +
           "{'isEmulator': true}, " +
           "{'fraudAttempts': {'$gte': 1}}" +
           "]}")
    List<Device> findHighRiskDevices(Double trustThreshold);

    /**
     * Find rooted/jailbroken devices
     */
    @Query("{'$or': [{'isRooted': true}, {'isJailbroken': true}]}")
    List<Device> findRootedOrJailbrokenDevices();

    /**
     * Find emulator devices
     */
    List<Device> findByIsEmulator(Boolean isEmulator);

    /**
     * Find devices using VPN
     */
    List<Device> findByHasVpn(Boolean hasVpn);

    /**
     * Find devices by platform
     */
    List<Device> findByPlatform(String platform);

    /**
     * Find devices by type
     */
    List<Device> findByDeviceType(String deviceType);

    /**
     * Find shared devices (multiple users)
     */
    @Query("{'userCount': {'$gt': 1}}")
    List<Device> findSharedDevices();

    /**
     * Count users for device
     */
    @Query(value = "{'deviceId': ?0}", count = true)
    int countUsersForDevice(String deviceId);

    /**
     * Find devices with high fraud attempts
     */
    @Query("{'fraudAttempts': {'$gte': ?0}}")
    List<Device> findDevicesWithFraudAttempts(Integer threshold);

    /**
     * Find devices with low success rate
     */
    @Query("{'successRate': {'$lt': ?0}}")
    List<Device> findDevicesWithLowSuccessRate(Double threshold);

    /**
     * Find new devices (first seen recently)
     */
    @Query("{'firstSeenAt': {'$gte': ?0}}")
    List<Device> findNewDevices(LocalDateTime since);

    /**
     * Find inactive devices
     */
    @Query("{'lastUsedAt': {'$lt': ?0}}")
    List<Device> findInactiveDevices(LocalDateTime before);

    /**
     * Find recently used devices
     */
    @Query("{'lastUsedAt': {'$gte': ?0}}")
    List<Device> findRecentlyUsedDevices(LocalDateTime since);

    /**
     * Find devices by country
     */
    @Query("{'primaryCountry': ?0}")
    List<Device> findDevicesByCountry(String countryCode);

    /**
     * Find devices with risk flags
     */
    @Query("{'riskFlags': {'$exists': true, '$ne': []}}")
    List<Device> findDevicesWithRiskFlags();

    /**
     * Find devices by specific risk flag
     */
    @Query("{'riskFlags': {'$in': [?0]}}")
    List<Device> findDevicesByRiskFlag(String flag);

    /**
     * Find devices with fingerprint inconsistencies
     */
    @Query("{'fingerprintInconsistencies': {'$gte': ?0}}")
    List<Device> findDevicesWithInconsistencies(Integer threshold);

    /**
     * Count devices for user
     */
    long countByUserId(String userId);

    /**
     * Check if device exists
     */
    boolean existsByDeviceId(String deviceId);

    /**
     * Find devices updated since
     */
    List<Device> findByUpdatedAtGreaterThan(LocalDateTime since);
}
