/**
 * SMS Session Repository
 * Data access layer for SMS banking sessions
 */
package com.waqiti.smsbanking.repository;

import com.waqiti.smsbanking.entity.SmsSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SmsSessionRepository extends JpaRepository<SmsSession, UUID> {
    
    Optional<SmsSession> findBySessionId(String sessionId);
    
    List<SmsSession> findByPhoneNumberAndStatus(String phoneNumber, SmsSession.SessionStatus status);
    
    List<SmsSession> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
    
    List<SmsSession> findByUserIdAndStatus(UUID userId, SmsSession.SessionStatus status);
    
    Page<SmsSession> findByChannelAndStatus(SmsSession.Channel channel, SmsSession.SessionStatus status, Pageable pageable);
    
    @Query("SELECT s FROM SmsSession s WHERE s.lastActivity < :expireTime AND s.status = :status")
    List<SmsSession> findExpiredSessions(@Param("expireTime") LocalDateTime expireTime, 
                                       @Param("status") SmsSession.SessionStatus status);
    
    @Query("SELECT s FROM SmsSession s WHERE s.phoneNumber = :phoneNumber AND s.status = 'ACTIVE' ORDER BY s.lastActivity DESC")
    Optional<SmsSession> findActiveSessionByPhone(@Param("phoneNumber") String phoneNumber);
    
    @Query("SELECT COUNT(s) FROM SmsSession s WHERE s.phoneNumber = :phoneNumber AND s.createdAt >= :since")
    long countSessionsByPhoneSince(@Param("phoneNumber") String phoneNumber, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(s) FROM SmsSession s WHERE s.channel = :channel AND s.createdAt >= :since")
    long countSessionsByChannelSince(@Param("channel") SmsSession.Channel channel, @Param("since") LocalDateTime since);
    
    @Modifying
    @Transactional
    @Query("UPDATE SmsSession s SET s.status = 'EXPIRED', s.endedAt = CURRENT_TIMESTAMP WHERE s.lastActivity < :expireTime AND s.status = 'ACTIVE'")
    int expireOldSessions(@Param("expireTime") LocalDateTime expireTime);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM SmsSession s WHERE s.endedAt IS NOT NULL AND s.endedAt < :deleteTime")
    int deleteOldSessions(@Param("deleteTime") LocalDateTime deleteTime);
    
    @Query("SELECT s.channel, COUNT(s) FROM SmsSession s WHERE s.createdAt >= :since GROUP BY s.channel")
    List<Object[]> getSessionStatsByChannel(@Param("since") LocalDateTime since);
    
    @Query("SELECT DATE(s.createdAt), COUNT(s) FROM SmsSession s WHERE s.createdAt >= :since GROUP BY DATE(s.createdAt) ORDER BY DATE(s.createdAt)")
    List<Object[]> getDailySessionStats(@Param("since") LocalDateTime since);
}