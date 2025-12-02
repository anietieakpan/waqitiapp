package com.waqiti.notification.repository;

import com.waqiti.notification.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {
    
    // Basic lookups
    Optional<DeviceToken> findByUserIdAndDeviceId(String userId, String deviceId);
    
    Optional<DeviceToken> findByTokenAndActive(String token, boolean active);
    
    List<DeviceToken> findByUserId(String userId);
    
    List<DeviceToken> findByUserIdOrderByLastUsedDesc(String userId);
    
    List<DeviceToken> findByUserIdAndActiveOrderByLastUsedDesc(String userId, boolean active);
    
    // Active device queries
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.userId = :userId AND dt.active = true")
    List<DeviceToken> findActiveByUserId(@Param("userId") String userId);
    
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.active = true AND dt.platform = :platform")
    List<DeviceToken> findActiveByPlatform(@Param("platform") DeviceToken.Platform platform);
    
    // Cleanup and maintenance queries
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.active = true AND " +
           "(dt.lastUsed < :cutoff OR (dt.lastUsed IS NULL AND dt.createdAt < :cutoff))")
    List<DeviceToken> findExpiredActiveTokens(@Param("cutoff") LocalDateTime cutoff);
    
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.active = false AND dt.updatedAt < :cutoff")
    List<DeviceToken> findStaleInactiveDevices(@Param("cutoff") LocalDateTime cutoff);
    
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.active = true AND " +
           "(dt.lastUsed < :cutoff OR (dt.lastUsed IS NULL AND dt.createdAt < :cutoff))")
    List<DeviceToken> findStaleActiveDevices(@Param("cutoff") LocalDateTime cutoff);
    
    // Statistics queries
    long countByActive(boolean active);
    
    long countByUserIdAndActive(String userId, boolean active);
    
    @Query("SELECT dt.platform as platform, COUNT(dt) as count FROM DeviceToken dt WHERE dt.active = true GROUP BY dt.platform")
    List<Map<String, Object>> countByPlatformGrouped();
    
    @Query("SELECT COUNT(dt) FROM DeviceToken dt WHERE dt.platform = :platform AND dt.active = true")
    long countByPlatformAndActive(@Param("platform") DeviceToken.Platform platform);
    
    @Query("SELECT COUNT(DISTINCT dt.userId) FROM DeviceToken dt WHERE dt.active = true")
    long countDistinctActiveUsers();
    
    // User-specific queries
    boolean existsByUserIdAndDeviceIdAndActiveTrue(String userId, String deviceId);
    
    boolean existsByTokenAndActive(String token, boolean active);
    
    // Bulk operations
    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.active = false, dt.invalidatedAt = :invalidatedAt WHERE dt.token IN :tokens")
    void deactivateTokens(@Param("tokens") List<String> tokens, @Param("invalidatedAt") LocalDateTime invalidatedAt);
    
    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.active = false, dt.invalidatedAt = :invalidatedAt WHERE dt.userId = :userId")
    void deactivateAllUserTokens(@Param("userId") String userId, @Param("invalidatedAt") LocalDateTime invalidatedAt);
    
    @Modifying
    @Query("DELETE FROM DeviceToken dt WHERE dt.active = false AND dt.updatedAt < :cutoff")
    int deleteStaleInactiveTokens(@Param("cutoff") LocalDateTime cutoff);
    
    // Platform-specific queries
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.userId = :userId AND dt.platform = :platform AND dt.active = true")
    List<DeviceToken> findActiveByUserIdAndPlatform(@Param("userId") String userId, @Param("platform") DeviceToken.Platform platform);
    
    @Query("SELECT DISTINCT dt.userId FROM DeviceToken dt WHERE dt.platform = :platform AND dt.active = true")
    List<String> findActiveUserIdsByPlatform(@Param("platform") DeviceToken.Platform platform);
    
    // Token validation queries
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.userId IN :userIds AND dt.active = true")
    List<DeviceToken> findActiveTokensByUserIds(@Param("userIds") List<String> userIds);
    
    @Query("SELECT dt FROM DeviceToken dt WHERE dt.token = :token")
    Optional<DeviceToken> findByToken(@Param("token") String token);
}