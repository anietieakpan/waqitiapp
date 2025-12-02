package com.waqiti.voice.repository;

import com.waqiti.voice.domain.VoiceSession;
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
public interface VoiceSessionRepository extends JpaRepository<VoiceSession, UUID> {
    
    Optional<VoiceSession> findBySessionIdAndUserId(UUID sessionId, UUID userId);
    
    Optional<VoiceSession> findActiveSessionByUserId(UUID userId);
    
    Page<VoiceSession> findByUserId(UUID userId, Pageable pageable);
    
    Page<VoiceSession> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);
    
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.userId = :userId " +
           "AND vs.status = 'ACTIVE'")
    Optional<VoiceSession> findActiveSession(@Param("userId") UUID userId);
    
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.userId = :userId " +
           "AND vs.startTime BETWEEN :startTime AND :endTime")
    List<VoiceSession> findSessionsInTimeRange(@Param("userId") UUID userId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);
    
    @Query("SELECT COUNT(vs) FROM VoiceSession vs WHERE vs.userId = :userId " +
           "AND vs.status = 'COMPLETED' AND vs.startTime >= :since")
    Long countCompletedSessionsSince(@Param("userId") UUID userId,
                                    @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (vs.endTime - vs.startTime))/60) " +
           "FROM VoiceSession vs WHERE vs.userId = :userId " +
           "AND vs.status = 'COMPLETED'")
    Double getAverageSessionDurationMinutes(@Param("userId") UUID userId);
    
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.status = 'ACTIVE' " +
           "AND vs.lastActivityTime < :timeout")
    List<VoiceSession> findTimedOutSessions(@Param("timeout") LocalDateTime timeout);
    
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.deviceId = :deviceId " +
           "ORDER BY vs.startTime DESC")
    Page<VoiceSession> findByDeviceId(@Param("deviceId") String deviceId, Pageable pageable);
    
    @Query("SELECT COUNT(vs) FROM VoiceSession vs WHERE vs.userId = :userId " +
           "AND vs.authenticationMethod = :authMethod")
    Long countByAuthenticationMethod(@Param("userId") UUID userId,
                                    @Param("authMethod") String authMethod);
    
    @Query("SELECT DATE(vs.startTime), COUNT(vs) FROM VoiceSession vs " +
           "WHERE vs.userId = :userId AND vs.startTime >= :since " +
           "GROUP BY DATE(vs.startTime)")
    List<Object[]> getDailySessionCounts(@Param("userId") UUID userId,
                                        @Param("since") LocalDateTime since);
    
    @Modifying
    @Query("UPDATE VoiceSession vs SET vs.status = :status, " +
           "vs.endTime = :endTime WHERE vs.sessionId = :sessionId")
    void updateSessionStatus(@Param("sessionId") UUID sessionId,
                           @Param("status") String status,
                           @Param("endTime") LocalDateTime endTime);
    
    @Modifying
    @Query("UPDATE VoiceSession vs SET vs.lastActivityTime = :activityTime, " +
           "vs.commandCount = vs.commandCount + 1 WHERE vs.sessionId = :sessionId")
    void updateLastActivity(@Param("sessionId") UUID sessionId,
                          @Param("activityTime") LocalDateTime activityTime);
    
    @Modifying
    @Query("UPDATE VoiceSession vs SET vs.voicePrintVerified = :verified, " +
           "vs.voicePrintConfidence = :confidence WHERE vs.sessionId = :sessionId")
    void updateVoicePrintVerification(@Param("sessionId") UUID sessionId,
                                     @Param("verified") Boolean verified,
                                     @Param("confidence") Double confidence);
    
    @Query("SELECT vs FROM VoiceSession vs WHERE vs.userId = :userId " +
           "AND vs.voicePrintVerified = true ORDER BY vs.startTime DESC")
    List<VoiceSession> findVerifiedSessions(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT COUNT(DISTINCT vs.userId) FROM VoiceSession vs " +
           "WHERE vs.startTime >= :since")
    Long countActiveUsersSince(@Param("since") LocalDateTime since);
    
    boolean existsByUserIdAndStatus(UUID userId, String status);
    
    void deleteByStartTimeBefore(LocalDateTime date);
}