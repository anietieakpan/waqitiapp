package com.waqiti.payment.repository;

import com.waqiti.payment.entity.NFCDevice;
import com.waqiti.payment.entity.NFCDeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for NFC device management with comprehensive security and tracking features
 */
@Repository
public interface NFCDeviceRepository extends JpaRepository<NFCDevice, UUID>, JpaSpecificationExecutor<NFCDevice> {

    /**
     * Find device by device ID
     */
    Optional<NFCDevice> findByDeviceId(String deviceId);
    
    /**
     * Find device by device ID and user ID
     */
    Optional<NFCDevice> findByDeviceIdAndUserId(String deviceId, String userId);
    
    /**
     * Find all devices for a user
     */
    Page<NFCDevice> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find active devices for a user
     */
    List<NFCDevice> findByUserIdAndStatus(String userId, NFCDeviceStatus status);
    
    /**
     * Find devices by status
     */
    Page<NFCDevice> findByStatusOrderByLastUsedAtDesc(NFCDeviceStatus status, Pageable pageable);
    
    /**
     * Check if a device exists for a user
     */
    boolean existsByDeviceIdAndUserId(String deviceId, String userId);
    
    /**
     * Check if a device fingerprint is already registered
     */
    boolean existsByDeviceFingerprint(String deviceFingerprint);
    
    /**
     * Find device by fingerprint
     */
    Optional<NFCDevice> findByDeviceFingerprint(String deviceFingerprint);
    
    /**
     * Find devices by manufacturer and model
     */
    List<NFCDevice> findByManufacturerAndModel(String manufacturer, String model);
    
    /**
     * Count active devices for a user
     */
    @Query("SELECT COUNT(d) FROM NFCDevice d WHERE d.userId = :userId AND d.status = 'ACTIVE'")
    long countActiveDevicesByUserId(@Param("userId") String userId);
    
    /**
     * Find devices that haven't been used recently
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.status = 'ACTIVE' " +
           "AND d.lastUsedAt < :cutoffTime ORDER BY d.lastUsedAt")
    List<NFCDevice> findInactiveDevices(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Find devices with expired certificates
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.certificateExpiryDate < :now " +
           "AND d.status = 'ACTIVE'")
    List<NFCDevice> findDevicesWithExpiredCertificates(@Param("now") Instant now);
    
    /**
     * Find devices requiring re-authentication
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.requiresReauthentication = true " +
           "AND d.status = 'ACTIVE'")
    List<NFCDevice> findDevicesRequiringReauthentication();
    
    /**
     * Find compromised devices
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.isCompromised = true OR d.status = 'BLOCKED'")
    List<NFCDevice> findCompromisedDevices();
    
    /**
     * Update device last used timestamp
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCDevice d SET d.lastUsedAt = :timestamp, d.usageCount = d.usageCount + 1 " +
           "WHERE d.deviceId = :deviceId")
    int updateLastUsedAt(@Param("deviceId") String deviceId, @Param("timestamp") Instant timestamp);
    
    /**
     * Update device status
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCDevice d SET d.status = :status, d.updatedAt = :updatedAt " +
           "WHERE d.deviceId = :deviceId")
    int updateDeviceStatus(@Param("deviceId") String deviceId, 
                          @Param("status") NFCDeviceStatus status,
                          @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Mark device as compromised
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCDevice d SET d.isCompromised = true, d.status = 'BLOCKED', " +
           "d.compromisedAt = :timestamp, d.compromisedReason = :reason " +
           "WHERE d.deviceId = :deviceId")
    int markAsCompromised(@Param("deviceId") String deviceId, 
                         @Param("timestamp") Instant timestamp,
                         @Param("reason") String reason);
    
    /**
     * Update device trust score
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCDevice d SET d.trustScore = :trustScore, d.trustScoreUpdatedAt = :timestamp " +
           "WHERE d.deviceId = :deviceId")
    int updateTrustScore(@Param("deviceId") String deviceId, 
                        @Param("trustScore") Double trustScore,
                        @Param("timestamp") Instant timestamp);
    
    /**
     * Find devices by location proximity
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.lastKnownLatitude IS NOT NULL " +
           "AND d.lastKnownLongitude IS NOT NULL " +
           "AND 6371 * acos(cos(radians(:lat)) * cos(radians(d.lastKnownLatitude)) * " +
           "cos(radians(d.lastKnownLongitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(d.lastKnownLatitude))) <= :radiusKm")
    List<NFCDevice> findByLocationProximity(@Param("lat") Double latitude,
                                           @Param("lng") Double longitude,
                                           @Param("radiusKm") Double radiusKm);
    
    /**
     * Find devices with high transaction volume
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.transactionCount > :threshold " +
           "AND d.registeredAt > :sinceDate ORDER BY d.transactionCount DESC")
    List<NFCDevice> findHighVolumeDevices(@Param("threshold") Long threshold,
                                         @Param("sinceDate") Instant sinceDate);
    
    /**
     * Get device statistics
     */
    @Query("SELECT NEW com.waqiti.payment.dto.DeviceStatistics(" +
           "COUNT(d), " +
           "SUM(CASE WHEN d.status = 'ACTIVE' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.status = 'BLOCKED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.isCompromised = true THEN 1 ELSE 0 END), " +
           "AVG(d.trustScore), " +
           "SUM(d.transactionCount)) " +
           "FROM NFCDevice d WHERE d.registeredAt BETWEEN :startDate AND :endDate")
    Object getDeviceStatistics(@Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find duplicate devices (potential fraud)
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.deviceFingerprint IN " +
           "(SELECT d2.deviceFingerprint FROM NFCDevice d2 " +
           "GROUP BY d2.deviceFingerprint HAVING COUNT(d2) > 1) " +
           "ORDER BY d.deviceFingerprint, d.registeredAt")
    List<NFCDevice> findDuplicateDevices();
    
    /**
     * Clean up expired sessions
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM NFCDevice d WHERE d.status = 'EXPIRED' " +
           "AND d.lastUsedAt < :cutoffTime")
    int deleteExpiredDevices(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Find devices by NFC capability
     */
    List<NFCDevice> findByNfcCapabilityAndStatus(String nfcCapability, NFCDeviceStatus status);
    
    /**
     * Find devices requiring security update
     */
    @Query("SELECT d FROM NFCDevice d WHERE d.securityPatchLevel < :requiredPatchLevel " +
           "AND d.status = 'ACTIVE'")
    List<NFCDevice> findDevicesRequiringSecurityUpdate(@Param("requiredPatchLevel") String requiredPatchLevel);
}