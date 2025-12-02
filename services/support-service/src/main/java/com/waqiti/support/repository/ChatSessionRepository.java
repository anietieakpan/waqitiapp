package com.waqiti.support.repository;

import com.waqiti.support.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    
    /**
     * Find session by ID and status
     */
    Optional<ChatSession> findBySessionIdAndStatus(String sessionId, ChatSession.Status status);
    
    /**
     * Find active sessions for user
     */
    List<ChatSession> findByUserIdAndStatus(String userId, ChatSession.Status status);
    
    /**
     * Find sessions by user ID
     */
    List<ChatSession> findByUserIdOrderByStartTimeDesc(String userId);
    
    /**
     * Find expired sessions
     */
    @Query("SELECT cs FROM ChatSession cs WHERE cs.status = 'ACTIVE' AND cs.lastActivity < :cutoffTime")
    List<ChatSession> findExpiredSessions(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find sessions by agent ID
     */
    List<ChatSession> findByAgentIdAndStatus(String agentId, ChatSession.Status status);
    
    /**
     * Count active sessions for user
     */
    long countByUserIdAndStatus(String userId, ChatSession.Status status);
    
    /**
     * Find sessions created within time range
     */
    @Query("SELECT cs FROM ChatSession cs WHERE cs.startTime BETWEEN :startTime AND :endTime")
    List<ChatSession> findSessionsInTimeRange(@Param("startTime") LocalDateTime startTime, 
                                            @Param("endTime") LocalDateTime endTime);
}