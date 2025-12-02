package com.waqiti.arpayment.repository;

import com.waqiti.arpayment.domain.ARSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ARSessionRepository extends JpaRepository<ARSession, UUID> {
    
    Optional<ARSession> findBySessionToken(String sessionToken);
    
    Optional<ARSession> findBySessionTokenAndUserId(String sessionToken, UUID userId);
    
    List<ARSession> findByUserIdAndStatus(UUID userId, ARSession.SessionStatus status);
    
    Page<ARSession> findByUserId(UUID userId, Pageable pageable);
    
    @Query("SELECT s FROM ARSession s WHERE s.userId = :userId " +
           "AND s.status IN ('ACTIVE', 'PAUSED') " +
           "ORDER BY s.startedAt DESC")
    List<ARSession> findActiveSessionsByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT s FROM ARSession s WHERE s.userId = :userId " +
           "AND s.sessionType = :sessionType " +
           "AND s.status = 'ACTIVE' " +
           "ORDER BY s.startedAt DESC")
    Optional<ARSession> findActiveSessionByUserIdAndType(
            @Param("userId") UUID userId,
            @Param("sessionType") ARSession.SessionType sessionType);
    
    @Query("SELECT COUNT(s) FROM ARSession s WHERE s.userId = :userId " +
           "AND s.status IN ('ACTIVE', 'PAUSED')")
    long countActiveSessionsByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT s FROM ARSession s WHERE s.currentLocationLat BETWEEN :minLat AND :maxLat " +
           "AND s.currentLocationLng BETWEEN :minLng AND :maxLng " +
           "AND s.status = 'ACTIVE'")
    List<ARSession> findActiveSessionsInArea(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);
    
    @Query("SELECT s FROM ARSession s WHERE s.status = 'ACTIVE' " +
           "AND s.lastActiveAt < :threshold")
    List<ARSession> findInactiveSessions(@Param("threshold") LocalDateTime threshold);
    
    @Modifying
    @Query("UPDATE ARSession s SET s.status = 'TIMEOUT', s.endedAt = :now " +
           "WHERE s.sessionToken = :sessionToken AND s.status = 'ACTIVE'")
    int timeoutSession(@Param("sessionToken") String sessionToken, @Param("now") LocalDateTime now);
    
    @Query("SELECT s FROM ARSession s WHERE s.userId = :userId " +
           "AND s.startedAt >= :startDate AND s.startedAt <= :endDate")
    List<ARSession> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT s.sessionType, COUNT(s) FROM ARSession s " +
           "WHERE s.userId = :userId AND s.status = 'ENDED' " +
           "GROUP BY s.sessionType")
    List<Object[]> getSessionTypeDistribution(@Param("userId") UUID userId);
    
    @Query("SELECT AVG(s.durationSeconds) FROM ARSession s " +
           "WHERE s.userId = :userId AND s.status = 'ENDED' " +
           "AND s.durationSeconds IS NOT NULL")
    Double getAverageSessionDuration(@Param("userId") UUID userId);
    
    @Query("SELECT s FROM ARSession s WHERE s.deviceId = :deviceId " +
           "AND s.status IN ('ACTIVE', 'PAUSED') " +
           "ORDER BY s.startedAt DESC")
    List<ARSession> findActiveSessionsByDeviceId(@Param("deviceId") String deviceId);
    
    @Query("SELECT DISTINCT s.arPlatform FROM ARSession s WHERE s.userId = :userId")
    List<String> findDistinctARPlatformsByUserId(@Param("userId") UUID userId);
    
    @Modifying
    @Query("DELETE FROM ARSession s WHERE s.endedAt < :cutoffDate")
    int deleteOldSessions(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query(value = "SELECT * FROM ar_sessions WHERE " +
           "jsonb_extract_path_text(session_metadata, 'merchantId') = :merchantId " +
           "AND status = 'ACTIVE'", nativeQuery = true)
    List<ARSession> findActiveSessionsByMerchantId(@Param("merchantId") String merchantId);
    
    @Query("SELECT COUNT(s) FROM ARSession s WHERE s.userId = :userId " +
           "AND s.sessionType = :sessionType AND s.status = 'ENDED' " +
           "AND s.paymentId IS NOT NULL")
    long countSuccessfulPaymentsByTypeAndUser(
            @Param("userId") UUID userId,
            @Param("sessionType") ARSession.SessionType sessionType);
}