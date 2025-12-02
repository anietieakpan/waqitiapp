package com.waqiti.common.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.domain.AuditLog;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.common.audit.domain.AuditLog.OperationResult;
import com.waqiti.common.audit.dto.AuditSearchRequest;
import com.waqiti.common.audit.dto.AuditSearchResponse;
import com.waqiti.common.audit.dto.AuditSummaryResponse;
import com.waqiti.common.audit.repository.AuditLogRepository;
import com.waqiti.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive Audit Search Service for SOX, PCI DSS, GDPR, and SOC 2 Compliance
 * 
 * Provides enterprise-grade audit log search, filtering, export, and analysis capabilities
 * with support for forensic investigation, compliance reporting, and security monitoring.
 * 
 * FEATURES:
 * - Dynamic query building with JPA Specifications
 * - Full-text search capabilities
 * - Compliance-specific filtering (PCI/GDPR/SOX/SOC2)
 * - Risk-based searching and fraud detection
 * - Multi-format export (CSV, JSON)
 * - Audit trail integrity validation
 * - Performance-optimized caching
 * - Comprehensive aggregation and statistics
 * 
 * SECURITY:
 * - Read-only operations (immutable audit logs)
 * - Role-based access control enforcement
 * - Sensitive data masking support
 * - Audit of audit searches
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditSearchService {
    
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_EXPORT_SIZE = 100000;
    private static final int DEFAULT_TOP_LIMIT = 10;
    
    /**
     * Comprehensive audit log search with dynamic filtering
     * 
     * @param searchRequest Search criteria with multiple filters
     * @param pageable Pagination and sorting parameters
     * @return Search response with results and metadata
     */
    public AuditSearchResponse searchAuditLogs(AuditSearchRequest searchRequest, Pageable pageable) {
        log.info("Executing audit log search with criteria: {}", searchRequest);
        
        if (!searchRequest.isValid()) {
            throw new IllegalArgumentException("Invalid search request: date range is invalid");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Build dynamic specification based on search criteria
            Specification<AuditLog> spec = buildSearchSpecification(searchRequest);
            
            // Execute search with pagination
            Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
            
            // Build aggregations if requested
            Map<String, Object> aggregations = new HashMap<>();
            if (Boolean.TRUE.equals(searchRequest.getIncludeStatistics())) {
                aggregations = buildAggregations(searchRequest, spec);
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return AuditSearchResponse.builder()
                    .results(page.getContent())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .currentPage(page.getNumber())
                    .pageSize(page.getSize())
                    .hasNext(page.hasNext())
                    .hasPrevious(page.hasPrevious())
                    .aggregations(aggregations)
                    .metadata(AuditSearchResponse.AuditSearchMetadata.builder()
                            .executionTimeMs(executionTime)
                            .filtersApplied(countFiltersApplied(searchRequest))
                            .cacheHit(false)
                            .build())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error executing audit log search", e);
            throw new RuntimeException("Failed to search audit logs", e);
        }
    }
    
    /**
     * Get audit logs for a specific user
     */
    public Page<AuditLog> getUserAuditLogs(String userId, LocalDateTime startDate, 
                                          LocalDateTime endDate, Pageable pageable) {
        log.info("Fetching audit logs for user: {} from {} to {}", userId, startDate, endDate);
        
        if (startDate != null && endDate != null) {
            return auditLogRepository.findByUserIdAndTimestampUtcBetween(
                    userId, startDate, endDate, pageable);
        } else if (startDate != null) {
            return auditLogRepository.findByUserIdAndTimestampUtcAfter(
                    userId, startDate, pageable);
        } else {
            return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }
    }
    
    /**
     * Get audit logs for a session
     */
    public List<AuditLog> getSessionAuditLogs(String sessionId) {
        log.info("Fetching audit logs for session: {}", sessionId);
        return auditLogRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }
    
    /**
     * Get correlated audit logs
     */
    public List<AuditLog> getCorrelatedAuditLogs(String correlationId) {
        log.info("Fetching correlated audit logs for: {}", correlationId);
        return auditLogRepository.findByCorrelationIdOrderByTimestampAsc(correlationId);
    }
    
    /**
     * Get financial transaction audit trail
     */
    public List<AuditLog> getFinancialTransactionAuditTrail(String transactionId) {
        log.info("Fetching financial transaction audit trail for: {}", transactionId);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(
                "TRANSACTION", transactionId);
    }
    
    /**
     * Get audit logs by event type
     */
    public Page<AuditLog> getAuditLogsByEventType(AuditEventType eventType, 
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate, 
                                                   Pageable pageable) {
        log.info("Fetching audit logs by event type: {} from {} to {}", 
                eventType, startDate, endDate);
        
        if (startDate != null && endDate != null) {
            return auditLogRepository.findByEventTypeAndTimestampUtcBetween(
                    eventType, startDate, endDate, pageable);
        } else {
            return auditLogRepository.findByEventTypeOrderByTimestampDesc(eventType, pageable);
        }
    }
    
    /**
     * Get audit logs by category
     */
    public Page<AuditLog> getAuditLogsByCategory(EventCategory category, Pageable pageable) {
        log.info("Fetching audit logs by category: {}", category);
        return auditLogRepository.findByEventCategoryOrderByTimestampDesc(category, pageable);
    }
    
    /**
     * Get audit logs by severity
     */
    public Page<AuditLog> getAuditLogsBySeverity(Severity severity, Pageable pageable) {
        log.info("Fetching audit logs by severity: {}", severity);
        return auditLogRepository.findBySeverityOrderByTimestampDesc(severity, pageable);
    }
    
    /**
     * Get failed operations
     */
    public Page<AuditLog> getFailedOperations(LocalDateTime startDate, Pageable pageable) {
        log.info("Fetching failed operations since: {}", startDate);
        
        if (startDate != null) {
            return auditLogRepository.findByResultAndTimestampUtcAfter(
                    OperationResult.FAILURE, startDate, pageable);
        } else {
            return auditLogRepository.findByResultOrderByTimestampDesc(
                    OperationResult.FAILURE, pageable);
        }
    }
    
    /**
     * Get suspicious activities
     */
    public List<AuditLog> getSuspiciousActivities(LocalDateTime startDate, Integer riskThreshold) {
        log.info("Fetching suspicious activities since: {} with risk threshold: {}", 
                startDate, riskThreshold);
        
        LocalDateTime effectiveStartDate = startDate != null ? 
                startDate : LocalDateTime.now().minusDays(7);
        int effectiveThreshold = riskThreshold != null ? riskThreshold : 50;
        
        return auditLogRepository.findSuspiciousActivities(
                effectiveStartDate, effectiveThreshold);
    }
    
    /**
     * Get events requiring investigation
     */
    public List<AuditLog> getEventsRequiringInvestigation() {
        log.info("Fetching events requiring investigation");
        return auditLogRepository.findByInvestigationRequiredTrueOrderByTimestampDesc();
    }
    
    /**
     * Get PCI DSS relevant logs
     */
    @Cacheable(value = "pci-audit-logs", key = "#startDate.toString() + '-' + #endDate.toString()")
    public Page<AuditLog> getPciRelevantLogs(LocalDateTime startDate, 
                                            LocalDateTime endDate, 
                                            Pageable pageable) {
        log.info("Fetching PCI relevant logs from {} to {}", startDate, endDate);
        return auditLogRepository.findByPciRelevantTrueAndTimestampUtcBetween(
                startDate, endDate, pageable);
    }
    
    /**
     * Get GDPR relevant logs
     */
    @Cacheable(value = "gdpr-audit-logs", key = "#startDate.toString() + '-' + #endDate.toString()")
    public Page<AuditLog> getGdprRelevantLogs(LocalDateTime startDate, 
                                             LocalDateTime endDate, 
                                             Pageable pageable) {
        log.info("Fetching GDPR relevant logs from {} to {}", startDate, endDate);
        return auditLogRepository.findByGdprRelevantTrueAndTimestampUtcBetween(
                startDate, endDate, pageable);
    }
    
    /**
     * Get SOX relevant logs
     */
    @Cacheable(value = "sox-audit-logs", key = "#startDate.toString() + '-' + #endDate.toString()")
    public Page<AuditLog> getSoxRelevantLogs(LocalDateTime startDate, 
                                            LocalDateTime endDate, 
                                            Pageable pageable) {
        log.info("Fetching SOX relevant logs from {} to {}", startDate, endDate);
        return auditLogRepository.findBySoxRelevantTrueAndTimestampUtcBetween(
                startDate, endDate, pageable);
    }
    
    /**
     * Get comprehensive audit summary with statistics
     */
    public AuditSummaryResponse getAuditSummary(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating audit summary from {} to {}", startDate, endDate);
        
        try {
            // Get all logs in the period for analysis
            List<AuditLog> logs = auditLogRepository.findByTimestampUtcBetween(
                    startDate, endDate);
            
            long totalEvents = logs.size();
            long successfulEvents = logs.stream()
                    .filter(log -> log.getResult() == OperationResult.SUCCESS)
                    .count();
            long failedEvents = logs.stream()
                    .filter(log -> log.getResult() == OperationResult.FAILURE)
                    .count();
            long criticalEvents = logs.stream()
                    .filter(log -> log.getSeverity() == Severity.CRITICAL)
                    .count();
            long warningEvents = logs.stream()
                    .filter(log -> log.getSeverity() == Severity.WARNING)
                    .count();
            
            // Event type distribution (top 10)
            Map<String, Long> eventsByType = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getEventType().name(),
                            Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(DEFAULT_TOP_LIMIT)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            
            // Category distribution
            Map<String, Long> eventsByCategory = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getEventCategory().name(),
                            Collectors.counting()));
            
            // Severity distribution
            Map<String, Long> eventsBySeverity = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getSeverity().name(),
                            Collectors.counting()));
            
            // Result distribution
            Map<String, Long> eventsByResult = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getResult().name(),
                            Collectors.counting()));
            
            // Top users (top 10)
            Map<String, Long> topUsers = logs.stream()
                    .filter(log -> log.getUserId() != null)
                    .collect(Collectors.groupingBy(
                            AuditLog::getUserId,
                            Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(DEFAULT_TOP_LIMIT)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            
            // Top IP addresses (top 10)
            Map<String, Long> topIpAddresses = logs.stream()
                    .filter(log -> log.getIpAddress() != null)
                    .collect(Collectors.groupingBy(
                            AuditLog::getIpAddress,
                            Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(DEFAULT_TOP_LIMIT)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            
            // Compliance summary
            AuditSummaryResponse.ComplianceSummary complianceSummary = 
                    AuditSummaryResponse.ComplianceSummary.builder()
                    .pciRelevantCount(logs.stream().filter(AuditLog::getPciRelevant).count())
                    .gdprRelevantCount(logs.stream().filter(AuditLog::getGdprRelevant).count())
                    .soxRelevantCount(logs.stream().filter(AuditLog::getSoxRelevant).count())
                    .soc2RelevantCount(logs.stream().filter(AuditLog::getSoc2Relevant).count())
                    .build();
            
            // Security summary
            AuditSummaryResponse.SecuritySummary securitySummary = 
                    AuditSummaryResponse.SecuritySummary.builder()
                    .fraudDetections(logs.stream()
                            .filter(log -> log.getEventType() == AuditEventType.FRAUD_DETECTED)
                            .count())
                    .suspiciousActivities(logs.stream()
                            .filter(log -> log.getEventType() == AuditEventType.SUSPICIOUS_ACTIVITY)
                            .count())
                    .securityAlerts(logs.stream()
                            .filter(log -> log.getEventType() == AuditEventType.SECURITY_ALERT)
                            .count())
                    .authenticationFailures(logs.stream()
                            .filter(log -> log.getEventType() == AuditEventType.LOGIN_FAILURE)
                            .count())
                    .highRiskEvents(logs.stream()
                            .filter(log -> log.getRiskScore() != null && log.getRiskScore() > 70)
                            .count())
                    .build();
            
            // Performance metrics
            AuditSummaryResponse.PerformanceMetrics performanceMetrics = 
                    AuditSummaryResponse.PerformanceMetrics.builder()
                    .averageEventsPerDay(totalEvents / Math.max(1, 
                            java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)))
                    .peakHour(findPeakHour(logs))
                    .slowestOperations(findSlowestOperations(logs))
                    .build();
            
            return AuditSummaryResponse.builder()
                    .totalEvents(totalEvents)
                    .successfulEvents(successfulEvents)
                    .failedEvents(failedEvents)
                    .criticalEvents(criticalEvents)
                    .warningEvents(warningEvents)
                    .eventsByType(eventsByType)
                    .eventsByCategory(eventsByCategory)
                    .eventsBySeverity(eventsBySeverity)
                    .eventsByResult(eventsByResult)
                    .topUsers(topUsers)
                    .topIpAddresses(topIpAddresses)
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .complianceSummary(complianceSummary)
                    .securitySummary(securitySummary)
                    .performanceMetrics(performanceMetrics)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating audit summary", e);
            throw new RuntimeException("Failed to generate audit summary", e);
        }
    }
    
    /**
     * Get audit log by ID
     */
    public AuditLog getAuditLogById(UUID id) {
        log.info("Fetching audit log by ID: {}", id);
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audit log not found with ID: " + id));
    }
    
    /**
     * Full-text search on audit logs
     */
    public Page<AuditLog> searchAuditLogsByText(String query, Pageable pageable) {
        log.info("Performing text search for query: {}", query);
        return auditLogRepository.searchByText(query, pageable);
    }
    
    /**
     * Get audit logs by IP address
     */
    public Page<AuditLog> getAuditLogsByIpAddress(String ipAddress, 
                                                   LocalDateTime startDate, 
                                                   Pageable pageable) {
        log.info("Fetching audit logs by IP address: {} since: {}", ipAddress, startDate);
        
        if (startDate != null) {
            return auditLogRepository.findByIpAddressAndTimestampUtcAfter(
                    ipAddress, startDate, pageable);
        } else {
            return auditLogRepository.findByIpAddressOrderByTimestampDesc(
                    ipAddress, pageable);
        }
    }
    
    /**
     * Get audit log volume metrics
     */
    public Map<String, Object> getAuditLogVolumeMetrics() {
        log.info("Calculating audit log volume metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        
        metrics.put("last24Hours", auditLogRepository.countByTimestampUtcAfter(
                now.minusDays(1)));
        metrics.put("last7Days", auditLogRepository.countByTimestampUtcAfter(
                now.minusDays(7)));
        metrics.put("last30Days", auditLogRepository.countByTimestampUtcAfter(
                now.minusDays(30)));
        metrics.put("last90Days", auditLogRepository.countByTimestampUtcAfter(
                now.minusDays(90)));
        metrics.put("totalCount", auditLogRepository.count());
        
        return metrics;
    }
    
    /**
     * Get top users by activity
     */
    public Map<String, Long> getTopUsersByActivity(LocalDateTime startDate, int limit) {
        log.info("Fetching top {} users by activity since: {}", limit, startDate);
        
        List<AuditLog> logs = auditLogRepository.findByTimestampUtcAfter(startDate);
        
        return logs.stream()
                .filter(log -> log.getUserId() != null)
                .collect(Collectors.groupingBy(AuditLog::getUserId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }
    
    /**
     * Export audit logs to CSV
     */
    public byte[] exportAuditLogsToCsv(AuditSearchRequest searchRequest) {
        log.info("Exporting audit logs to CSV with criteria: {}", searchRequest);
        
        try {
            // Build specification and fetch logs
            Specification<AuditLog> spec = buildSearchSpecification(searchRequest);
            Pageable pageable = PageRequest.of(0, MAX_EXPORT_SIZE);
            Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
            
            if (page.getTotalElements() > MAX_EXPORT_SIZE) {
                log.warn("Export size exceeds maximum: {} > {}", 
                        page.getTotalElements(), MAX_EXPORT_SIZE);
            }
            
            // Build CSV
            StringBuilder csv = new StringBuilder();
            
            // Headers
            csv.append("ID,Timestamp,Event Type,Category,Severity,User ID,Action,");
            csv.append("Description,Result,IP Address,Risk Score,PCI Relevant,");
            csv.append("GDPR Relevant,SOX Relevant\n");
            
            // Data rows
            for (AuditLog log : page.getContent()) {
                csv.append(escapeCsv(log.getId().toString())).append(",");
                csv.append(escapeCsv(log.getTimestampUtc().toString())).append(",");
                csv.append(escapeCsv(log.getEventType().name())).append(",");
                csv.append(escapeCsv(log.getEventCategory().name())).append(",");
                csv.append(escapeCsv(log.getSeverity().name())).append(",");
                csv.append(escapeCsv(log.getUserId())).append(",");
                csv.append(escapeCsv(log.getAction())).append(",");
                csv.append(escapeCsv(log.getDescription())).append(",");
                csv.append(escapeCsv(log.getResult().name())).append(",");
                csv.append(escapeCsv(log.getIpAddress())).append(",");
                csv.append(log.getRiskScore() != null ? log.getRiskScore() : "").append(",");
                csv.append(log.getPciRelevant()).append(",");
                csv.append(log.getGdprRelevant()).append(",");
                csv.append(log.getSoxRelevant()).append("\n");
            }
            
            return csv.toString().getBytes(StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Error exporting audit logs to CSV", e);
            throw new RuntimeException("Failed to export audit logs to CSV", e);
        }
    }
    
    /**
     * Export audit logs to JSON
     */
    public byte[] exportAuditLogsToJson(AuditSearchRequest searchRequest) {
        log.info("Exporting audit logs to JSON with criteria: {}", searchRequest);
        
        try {
            // Build specification and fetch logs
            Specification<AuditLog> spec = buildSearchSpecification(searchRequest);
            Pageable pageable = PageRequest.of(0, MAX_EXPORT_SIZE);
            Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
            
            if (page.getTotalElements() > MAX_EXPORT_SIZE) {
                log.warn("Export size exceeds maximum: {} > {}", 
                        page.getTotalElements(), MAX_EXPORT_SIZE);
            }
            
            // Convert to JSON
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(page.getContent());
            
            return json.getBytes(StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Error exporting audit logs to JSON", e);
            throw new RuntimeException("Failed to export audit logs to JSON", e);
        }
    }
    
    /**
     * Validate audit log integrity using hash chains
     */
    public Map<String, Object> validateAuditLogIntegrity(Long startSequence, Long endSequence) {
        log.info("Validating audit log integrity from sequence {} to {}", 
                startSequence, endSequence);
        
        try {
            List<AuditLog> logs = auditLogRepository
                    .findBySequenceNumberBetweenOrderBySequenceNumberAsc(
                            startSequence, endSequence);
            
            Map<String, Object> result = new HashMap<>();
            result.put("startSequence", startSequence);
            result.put("endSequence", endSequence);
            result.put("totalLogs", logs.size());
            
            boolean valid = true;
            List<String> violations = new ArrayList<>();
            
            for (int i = 0; i < logs.size(); i++) {
                AuditLog log = logs.get(i);
                
                // Verify hash chain
                if (i > 0) {
                    AuditLog previousLog = logs.get(i - 1);
                    if (!log.getPreviousHash().equals(previousLog.getHash())) {
                        valid = false;
                        violations.add(String.format(
                                "Hash chain broken at sequence %d", log.getSequenceNumber()));
                    }
                }
                
                // Verify hash computation
                String computedHash = computeHash(log);
                if (!computedHash.equals(log.getHash())) {
                    valid = false;
                    violations.add(String.format(
                            "Hash mismatch at sequence %d", log.getSequenceNumber()));
                }
            }
            
            result.put("valid", valid);
            result.put("violations", violations);
            result.put("violationCount", violations.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating audit log integrity", e);
            throw new RuntimeException("Failed to validate audit log integrity", e);
        }
    }
    
    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================
    
    /**
     * Build JPA Specification for dynamic query construction
     */
    private Specification<AuditLog> buildSearchSpecification(AuditSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Date range (required)
            if (request.getStartDate() != null && request.getEndDate() != null) {
                predicates.add(cb.between(root.get("timestampUtc"), 
                        request.getStartDate(), request.getEndDate()));
            }
            
            // User filters
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }
            if (request.getUsername() != null) {
                predicates.add(cb.like(root.get("username"), 
                        "%" + request.getUsername() + "%"));
            }
            if (request.getSessionId() != null) {
                predicates.add(cb.equal(root.get("sessionId"), request.getSessionId()));
            }
            if (request.getCorrelationId() != null) {
                predicates.add(cb.equal(root.get("correlationId"), request.getCorrelationId()));
            }
            
            // Event filters
            if (request.getEventTypes() != null && !request.getEventTypes().isEmpty()) {
                predicates.add(root.get("eventType").in(request.getEventTypes()));
            }
            if (request.getEventCategories() != null && !request.getEventCategories().isEmpty()) {
                predicates.add(root.get("eventCategory").in(request.getEventCategories()));
            }
            if (request.getSeverityLevels() != null && !request.getSeverityLevels().isEmpty()) {
                predicates.add(root.get("severity").in(request.getSeverityLevels()));
            }
            if (request.getOperationResults() != null && !request.getOperationResults().isEmpty()) {
                predicates.add(root.get("result").in(request.getOperationResults()));
            }
            
            // Entity filters
            if (request.getEntityType() != null) {
                predicates.add(cb.equal(root.get("entityType"), request.getEntityType()));
            }
            if (request.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), request.getEntityId()));
            }
            
            // Network filters
            if (request.getIpAddress() != null) {
                predicates.add(cb.equal(root.get("ipAddress"), request.getIpAddress()));
            }
            
            // Location filters
            if (request.getLocationCountry() != null) {
                predicates.add(cb.equal(root.get("locationCountry"), 
                        request.getLocationCountry()));
            }
            
            // Content filters
            if (request.getActionContains() != null) {
                predicates.add(cb.like(root.get("action"), 
                        "%" + request.getActionContains() + "%"));
            }
            if (request.getDescriptionContains() != null) {
                predicates.add(cb.like(root.get("description"), 
                        "%" + request.getDescriptionContains() + "%"));
            }
            
            // Risk filters
            if (request.getMinRiskScore() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("riskScore"), 
                        request.getMinRiskScore()));
            }
            if (request.getMaxRiskScore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("riskScore"), 
                        request.getMaxRiskScore()));
            }
            
            // Compliance filters
            if (Boolean.TRUE.equals(request.getPciRelevant())) {
                predicates.add(cb.isTrue(root.get("pciRelevant")));
            }
            if (Boolean.TRUE.equals(request.getGdprRelevant())) {
                predicates.add(cb.isTrue(root.get("gdprRelevant")));
            }
            if (Boolean.TRUE.equals(request.getSoxRelevant())) {
                predicates.add(cb.isTrue(root.get("soxRelevant")));
            }
            if (Boolean.TRUE.equals(request.getSoc2Relevant())) {
                predicates.add(cb.isTrue(root.get("soc2Relevant")));
            }
            if (Boolean.TRUE.equals(request.getInvestigationRequired())) {
                predicates.add(cb.isTrue(root.get("investigationRequired")));
            }
            
            // Result filters
            if (Boolean.TRUE.equals(request.getSuccessfulOnly())) {
                predicates.add(cb.equal(root.get("result"), OperationResult.SUCCESS));
            }
            if (Boolean.TRUE.equals(request.getFailedOnly())) {
                predicates.add(cb.equal(root.get("result"), OperationResult.FAILURE));
            }
            
            // Advanced filters
            if (Boolean.TRUE.equals(request.getHasFraudIndicators())) {
                predicates.add(cb.isNotNull(root.get("fraudIndicators")));
            }
            if (Boolean.TRUE.equals(request.getIsArchived())) {
                predicates.add(cb.isTrue(root.get("archived")));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Build aggregations for search results
     */
    private Map<String, Object> buildAggregations(AuditSearchRequest request, 
                                                  Specification<AuditLog> spec) {
        Map<String, Object> aggregations = new HashMap<>();
        
        // Get all matching logs for aggregation
        List<AuditLog> allLogs = auditLogRepository.findAll(spec);
        
        // Event type counts
        Map<String, Long> eventTypeCounts = allLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getEventType().name(), 
                        Collectors.counting()));
        aggregations.put("eventTypes", eventTypeCounts);
        
        // Severity counts
        Map<String, Long> severityCounts = allLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getSeverity().name(), 
                        Collectors.counting()));
        aggregations.put("severities", severityCounts);
        
        // Result counts
        Map<String, Long> resultCounts = allLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getResult().name(), 
                        Collectors.counting()));
        aggregations.put("results", resultCounts);
        
        return aggregations;
    }
    
    /**
     * Count filters applied in search request
     */
    private int countFiltersApplied(AuditSearchRequest request) {
        int count = 2; // Start and end date always present
        
        if (request.getUserId() != null) count++;
        if (request.getEventTypes() != null && !request.getEventTypes().isEmpty()) count++;
        if (request.getEventCategories() != null && !request.getEventCategories().isEmpty()) count++;
        if (request.getSeverityLevels() != null && !request.getSeverityLevels().isEmpty()) count++;
        if (Boolean.TRUE.equals(request.getPciRelevant())) count++;
        if (Boolean.TRUE.equals(request.getGdprRelevant())) count++;
        if (Boolean.TRUE.equals(request.getSoxRelevant())) count++;
        if (request.getMinRiskScore() != null) count++;
        
        return count;
    }
    
    /**
     * Find peak hour from logs
     */
    private Integer findPeakHour(List<AuditLog> logs) {
        if (logs.isEmpty()) return 0;
        
        Map<Integer, Long> hourCounts = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getTimestampUtc().getHour(),
                        Collectors.counting()));
        
        return hourCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
    }
    
    /**
     * Find slowest operations
     */
    private List<String> findSlowestOperations(List<AuditLog> logs) {
        // This is a placeholder - in production, would analyze operation durations
        return logs.stream()
                .filter(log -> log.getResult() == OperationResult.FAILURE)
                .map(AuditLog::getAction)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }
    
    /**
     * Escape CSV values
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Compute hash for audit log
     */
    private String computeHash(AuditLog auditLog) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = auditLog.getSequenceNumber() + "|" +
                         auditLog.getTimestampUtc() + "|" +
                         auditLog.getEventType() + "|" +
                         auditLog.getUserId() + "|" +
                         auditLog.getAction();
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error computing hash", e);
            return "";
        }
    }
}