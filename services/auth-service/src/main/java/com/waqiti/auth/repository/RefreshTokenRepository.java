package com.waqiti.auth.repository;

import com.waqiti.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise-grade Refresh Token Repository.
 *
 * Features:
 * - Token rotation support
 * - Token family tracking
 * - Automatic cleanup of expired tokens
 * - Security breach detection
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.token = :token AND rt.revoked = false AND rt.used = false AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    List<RefreshToken> findByUserId(UUID userId);

    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revoked = false AND rt.used = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // Token family operations (for token rotation)
    List<RefreshToken> findByTokenFamily(UUID tokenFamily);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenFamily = :tokenFamily AND rt.revoked = false")
    List<RefreshToken> findActiveTokensByFamily(@Param("tokenFamily") UUID tokenFamily);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revocationReason = :reason WHERE rt.tokenFamily = :tokenFamily")
    void revokeTokenFamily(@Param("tokenFamily") UUID tokenFamily, @Param("now") LocalDateTime now, @Param("reason") String reason);

    // Device-based queries
    List<RefreshToken> findByUserIdAndDeviceIdAndRevokedFalse(UUID userId, String deviceId);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.deviceId = :deviceId AND rt.revoked = false AND rt.used = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUserAndDevice(@Param("userId") UUID userId, @Param("deviceId") String deviceId, @Param("now") LocalDateTime now);

    // Security queries
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.used = true AND rt.usedAt > :recentTime AND rt.revoked = false")
    List<RefreshToken> findRecentlyUsedTokens(@Param("recentTime") LocalDateTime recentTime);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    Long countActiveTokensByUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.tokenFamily = :tokenFamily AND rt.used = true")
    Long countUsedTokensInFamily(@Param("tokenFamily") UUID tokenFamily);

    // Bulk operations
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revocationReason = :reason WHERE rt.user.id = :userId")
    void revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revocationReason = :reason WHERE rt.user.id = :userId AND rt.deviceId = :deviceId")
    void revokeUserDeviceTokens(@Param("userId") UUID userId, @Param("deviceId") String deviceId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    // Cleanup operations
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.expiresAt < :cutoffDate")
    List<RefreshToken> findExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoffDate OR (rt.revoked = true AND rt.revokedAt < :cutoffDate)")
    void deleteExpiredAndRevokedTokens(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id IN (SELECT u.id FROM User u WHERE u.deleted = true)")
    void deleteTokensForDeletedUsers();

    // Statistics
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.revoked = false AND rt.expiresAt > :now")
    Long countActiveTokens(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.createdAt >= :since")
    Long countTokensCreatedSince(@Param("since") LocalDateTime since);

    @Query("SELECT rt.deviceType, COUNT(rt) FROM RefreshToken rt WHERE rt.revoked = false AND rt.expiresAt > :now GROUP BY rt.deviceType")
    List<Object[]> countActiveTokensByDeviceType(@Param("now") LocalDateTime now);
}
