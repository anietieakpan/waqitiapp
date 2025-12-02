package com.waqiti.audit.repository;

import com.waqiti.audit.domain.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for security event persistence and queries
 */
@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
    
    /**
     * Find security events by severity
     */
    List<SecurityEvent> findBySeverityAndTimestampBetween(String severity, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find security events by user
     */
    Page<SecurityEvent> findByUserId(String userId, Pageable pageable);
    
    /**
     * Find failed authentication attempts
     */
    @Query("SELECT s FROM SecurityEvent s WHERE s.eventType = 'LOGIN_ATTEMPT' AND s.outcome = 'FAILURE' " +
           "AND s.timestamp >= :startDate")
    List<SecurityEvent> findFailedLoginAttempts(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find security events by IP address
     */
    List<SecurityEvent> findBySourceIpAndTimestampBetween(String sourceIp, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Count security events by type
     */
    @Query("SELECT s.eventType, COUNT(s) FROM SecurityEvent s WHERE s.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY s.eventType")
    List<Object[]> countEventsByType(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find critical security events
     */
    @Query("SELECT s FROM SecurityEvent s WHERE s.severity IN ('CRITICAL', 'HIGH') " +
           "AND s.timestamp >= :startDate AND s.falsePositive = false")
    List<SecurityEvent> findCriticalEvents(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find events by threat indicator
     */
    List<SecurityEvent> findByThreatIndicatorContainingIgnoreCase(String threatIndicator);
    
    /**
     * Find brute force attack patterns
     */
    @Query("SELECT s.sourceIp, s.userId, COUNT(s) as attemptCount FROM SecurityEvent s " +
           "WHERE s.eventType = 'LOGIN_ATTEMPT' AND s.outcome = 'FAILURE' " +
           "AND s.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY s.sourceIp, s.userId " +
           "HAVING COUNT(s) > :threshold")
    List<Object[]> findBruteForcePatterns(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate,
                                           @Param("threshold") Long threshold);
    
    /**
     * Find privilege escalation attempts
     */
    @Query("SELECT s FROM SecurityEvent s WHERE s.eventType = 'PRIVILEGE_ESCALATION' " +
           "AND s.timestamp >= :startDate")
    List<SecurityEvent> findPrivilegeEscalationAttempts(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find events requiring incident response
     */
    @Query("SELECT s FROM SecurityEvent s WHERE s.severity = 'CRITICAL' " +
           "AND s.mitigationApplied = false AND s.timestamp >= :startDate")
    List<SecurityEvent> findUnmitigatedCriticalEvents(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find events by correlation ID
     */
    List<SecurityEvent> findByCorrelationId(String correlationId);
    
    /**
     * Find events related to an incident
     */
    List<SecurityEvent> findByRelatedIncidentId(String incidentId);
}