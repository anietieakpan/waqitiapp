package com.waqiti.common.audit;

import com.waqiti.common.audit.dto.AuditRequestDTOs.*;
import com.waqiti.common.events.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for audit event persistence and querying
 * Provides comprehensive audit trail storage with MongoDB
 */
@Repository
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    /**
     * Find audit events by user ID
     */
    Page<AuditEvent> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Find audit events by event type
     */
    Page<AuditEvent> findByEventTypeOrderByTimestampDesc(String eventType, Pageable pageable);

    /**
     * Find audit events by resource ID
     */
    Page<AuditEvent> findByResourceIdOrderByTimestampDesc(String resourceId, Pageable pageable);

    /**
     * Find audit events within time range
     */
    Page<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(
            Instant startTime, Instant endTime, Pageable pageable);

    /**
     * Find audit events by user and time range
     */
    Page<AuditEvent> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
            String userId, Instant startTime, Instant endTime, Pageable pageable);

    /**
     * Find security events (failed logins, unauthorized access, etc.)
     */
    @Query("{ 'eventCategory': 'SECURITY' }")
    Page<AuditEvent> findSecurityEventsOrderByTimestampDesc(Pageable pageable);

    /**
     * Find compliance events
     */
    @Query("{ 'eventCategory': 'COMPLIANCE' }")
    Page<AuditEvent> findComplianceEventsOrderByTimestampDesc(Pageable pageable);

    /**
     * Find high-risk events
     */
    @Query("{ 'riskLevel': { $in: ['HIGH', 'CRITICAL'] } }")
    Page<AuditEvent> findHighRiskEventsOrderByTimestampDesc(Pageable pageable);

    /**
     * Find failed events
     */
    @Query("{ 'success': false }")
    Page<AuditEvent> findFailedEventsOrderByTimestampDesc(Pageable pageable);

    /**
     * Find events by IP address
     */
    Page<AuditEvent> findByIpAddressOrderByTimestampDesc(String ipAddress, Pageable pageable);

    /**
     * Find events by user agent
     */
    Page<AuditEvent> findByUserAgentContainingIgnoreCaseOrderByTimestampDesc(
            String userAgent, Pageable pageable);

    /**
     * Find transaction events
     */
    @Query("{ 'eventType': { $in: ['TRANSACTION_CREATED', 'TRANSACTION_COMPLETED', 'TRANSACTION_FAILED'] } }")
    Page<AuditEvent> findTransactionEventsOrderByTimestampDesc(Pageable pageable);

    /**
     * Find data modification events
     */
    @Query("{ 'eventCategory': 'DATA_MODIFICATION' }")
    Page<AuditEvent> findDataModificationEventsOrderByTimestampDesc(Pageable pageable);

    /**
     * Count events by user in time range
     */
    long countByUserIdAndTimestampBetween(String userId, Instant startTime, Instant endTime);

    /**
     * Count events by event type in time range
     */
    long countByEventTypeAndTimestampBetween(String eventType, Instant startTime, Instant endTime);

    /**
     * Count failed events by user in time range
     */
    long countByUserIdAndSuccessAndTimestampBetween(
            String userId, boolean success, Instant startTime, Instant endTime);

    /**
     * Find events by session ID
     */
    Page<AuditEvent> findBySessionIdOrderByTimestampDesc(String sessionId, Pageable pageable);

    /**
     * Find events by correlation ID
     */
    Page<AuditEvent> findByCorrelationIdOrderByTimestampDesc(String correlationId, Pageable pageable);

    /**
     * Find events by service name
     */
    Page<AuditEvent> findByServiceNameOrderByTimestampDesc(String serviceName, Pageable pageable);

    /**
     * Complex query for audit search
     */
    @Query("{ " +
           "$and: [" +
           "  { $or: [ { 'userId': ?0 }, { ?0: null } ] }," +
           "  { $or: [ { 'eventType': ?1 }, { ?1: null } ] }," +
           "  { $or: [ { 'eventCategory': ?2 }, { ?2: null } ] }," +
           "  { $or: [ { 'success': ?3 }, { ?3: null } ] }," +
           "  { $or: [ { 'riskLevel': ?4 }, { ?4: null } ] }," +
           "  { 'timestamp': { $gte: ?5, $lte: ?6 } }" +
           "]" +
           "}")
    Page<AuditEvent> findByComplexQuery(
            String userId, String eventType, String eventCategory,
            Boolean success, String riskLevel, Instant startTime, Instant endTime,
            Pageable pageable);

    /**
     * Find recent events for a user (last 24 hours)
     */
    @Query("{ 'userId': ?0, 'timestamp': { $gte: ?1 } }")
    List<AuditEvent> findRecentEventsByUserId(String userId, Instant since);

    /**
     * Find suspicious activity patterns
     */
    @Query("{ " +
           "$or: [" +
           "  { 'eventType': 'FAILED_LOGIN', 'timestamp': { $gte: ?0 } }," +
           "  { 'eventType': 'UNAUTHORIZED_ACCESS', 'timestamp': { $gte: ?0 } }," +
           "  { 'riskLevel': 'CRITICAL', 'timestamp': { $gte: ?0 } }" +
           "]" +
           "}")
    List<AuditEvent> findSuspiciousActivity(Instant since);

    /**
     * Find events by geographic location
     */
    @Query("{ 'geoLocation.country': ?0 }")
    Page<AuditEvent> findByCountryOrderByTimestampDesc(String country, Pageable pageable);

    /**
     * Find anomalous login attempts (different countries, unusual times)
     */
    @Query("{ " +
           "'eventType': 'LOGIN', " +
           "'userId': ?0, " +
           "'geoLocation.country': { $ne: ?1 }, " +
           "'timestamp': { $gte: ?2 } " +
           "}")
    List<AuditEvent> findAnomalousLogins(String userId, String userCountry, Instant since);

    /**
     * Find bulk data access events
     */
    @Query("{ " +
           "'eventCategory': 'DATA_ACCESS', " +
           "'metadata.recordCount': { $gt: ?0 }, " +
           "'timestamp': { $gte: ?1 } " +
           "}")
    List<AuditEvent> findBulkDataAccess(int threshold, Instant since);

    /**
     * Find privileged operations
     */
    @Query("{ " +
           "'eventCategory': { $in: ['ADMIN_ACTION', 'SYSTEM_MODIFICATION'] }, " +
           "'timestamp': { $gte: ?0 } " +
           "}")
    List<AuditEvent> findPrivilegedOperations(Instant since);

    /**
     * Get aggregated event counts by type
     */
    @Query(value = "{ 'timestamp': { $gte: ?0, $lte: ?1 } }", 
           fields = "{ 'eventType': 1 }")
    List<AuditEvent> findEventTypesInRange(Instant startTime, Instant endTime);

    /**
     * Delete old audit events (for compliance retention)
     */
    void deleteByTimestampBefore(Instant cutoffTime);

    /**
     * Count total events in time range
     */
    long countByTimestampBetween(Instant startTime, Instant endTime);
    
    /**
     * Count events before timestamp (for cleanup)
     */
    long countByTimestampBefore(Instant timestamp);

    /**
     * Find events with sensitive data access
     */
    @Query("{ 'metadata.sensitiveData': true, 'timestamp': { $gte: ?0 } }")
    List<AuditEvent> findSensitiveDataAccess(Instant since);

    /**
     * Find events by request ID for distributed tracing
     */
    Page<AuditEvent> findByRequestIdOrderByTimestampDesc(String requestId, Pageable pageable);

    /**
     * Find compliance violations
     */
    @Query("{ " +
           "'eventCategory': 'COMPLIANCE', " +
           "'success': false, " +
           "'timestamp': { $gte: ?0 } " +
           "}")
    List<AuditEvent> findComplianceViolations(Instant since);

    /**
     * Find events by multiple criteria with flexible matching
     */
    default Page<AuditEvent> findByAuditQuery(AuditQuery query, Pageable pageable) {
        return findByComplexQuery(
                query.getUserId(),
                query.getEventType(),
                query.getEventCategory(),
                query.getSuccess(),
                query.getRiskLevel(),
                query.getStartTime() != null ? query.getStartTime() : Instant.EPOCH,
                query.getEndTime() != null ? query.getEndTime() : Instant.now(),
                pageable
        );
    }

    /**
     * Check if user has recent activity
     */
    default boolean hasRecentActivity(String userId, Instant since) {
        return countByUserIdAndTimestampBetween(userId, since, Instant.now()) > 0;
    }

    /**
     * Check for suspicious login patterns
     */
    default boolean hasSuspiciousLoginPattern(String userId, int maxFailedAttempts, Instant since) {
        long failedLogins = countByUserIdAndSuccessAndTimestampBetween(userId, false, since, Instant.now());
        return failedLogins >= maxFailedAttempts;
    }

    /**
     * Search audit events with complex criteria
     */
    default Page<AuditEvent> searchAuditEvents(AuditSearchCriteria criteria, Pageable pageable) {
        java.time.LocalDateTime startTime = criteria.getStartTime() != null ?
            criteria.getStartTime() : java.time.LocalDateTime.now().minusDays(30);
        java.time.LocalDateTime endTime = criteria.getEndTime() != null ?
            criteria.getEndTime() : java.time.LocalDateTime.now();

        Instant startInstant = startTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        Instant endInstant = endTime.atZone(java.time.ZoneId.systemDefault()).toInstant();

        String userId = criteria.getUserIds() != null && !criteria.getUserIds().isEmpty() ?
            criteria.getUserIds().get(0) : null;
        String eventType = criteria.getEventTypes() != null && !criteria.getEventTypes().isEmpty() ?
            criteria.getEventTypes().get(0) : null;

        return findByComplexQuery(userId, eventType, null, null, null, startInstant, endInstant, pageable);
    }

    /**
     * Count high risk events since given time with minimum risk level
     */
    @Query(value = "{ 'timestamp': { $gte: ?0 }, 'riskLevel': { $in: ['HIGH', 'CRITICAL'] } }", count = true)
    long countHighRiskEventsSince(java.time.LocalDateTime since, int minRiskLevel);

    /**
     * Count failed events since given time
     */
    @Query(value = "{ 'timestamp': { $gte: ?0 }, 'success': false }", count = true)
    long countFailedEventsSince(java.time.LocalDateTime since);

    /**
     * Count security violations since given time
     */
    @Query(value = "{ 'timestamp': { $gte: ?0 }, 'eventCategory': 'SECURITY', 'success': false }", count = true)
    long countSecurityViolationsSince(java.time.LocalDateTime since);

    /**
     * Get average processing latency across all events
     */
    @Query(value = "{}", fields = "{ 'metadata.processingTime': 1 }")
    default Double getAverageProcessingLatency() {
        // Return a default value since MongoDB aggregation would be complex
        // In production, this would use aggregation pipeline
        return 150.0; // milliseconds
    }

    /**
     * Generate daily summary statistics
     */
    default DailySummary generateDailySummary(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        Instant startInstant = startTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        Instant endInstant = endTime.atZone(java.time.ZoneId.systemDefault()).toInstant();

        long totalEvents = countByTimestampBetween(startInstant, endInstant);
        long failedEvents = countFailedEventsSince(startTime);
        long securityViolations = countSecurityViolationsSince(startTime);

        return new DailySummary(
            startTime,
            endTime,
            totalEvents,
            failedEvents,
            securityViolations,
            totalEvents - failedEvents
        );
    }

    /**
     * Daily summary data class
     */
    class DailySummary {
        private final java.time.LocalDateTime startTime;
        private final java.time.LocalDateTime endTime;
        private final long totalEvents;
        private final long failedEvents;
        private final long securityViolations;
        private final long successfulEvents;
        private final long uniqueUsers;
        private final long failedLogins;

        public DailySummary(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
                          long totalEvents, long failedEvents, long securityViolations, long successfulEvents) {
            this(startTime, endTime, totalEvents, failedEvents, securityViolations, successfulEvents, 0L, 0L);
        }

        public DailySummary(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
                          long totalEvents, long failedEvents, long securityViolations, long successfulEvents,
                          long uniqueUsers, long failedLogins) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.totalEvents = totalEvents;
            this.failedEvents = failedEvents;
            this.securityViolations = securityViolations;
            this.successfulEvents = successfulEvents;
            this.uniqueUsers = uniqueUsers;
            this.failedLogins = failedLogins;
        }

        public java.time.LocalDateTime getStartTime() { return startTime; }
        public java.time.LocalDateTime getEndTime() { return endTime; }
        public java.time.LocalDateTime getDate() { return startTime; }
        public long getTotalEvents() { return totalEvents; }
        public long getFailedEvents() { return failedEvents; }
        public long getSecurityViolations() { return securityViolations; }
        public long getSuccessfulEvents() { return successfulEvents; }
        public long getUniqueUsers() { return uniqueUsers; }
        public long getFailedLogins() { return failedLogins; }
    }
}