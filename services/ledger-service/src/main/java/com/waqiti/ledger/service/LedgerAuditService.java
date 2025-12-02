package com.waqiti.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.controller.LedgerAuditController.*;
import com.waqiti.ledger.domain.AuditLogEntry;
import com.waqiti.ledger.dto.LedgerAuditTrailResponse;
import com.waqiti.ledger.repository.AuditLogRepository;
import com.waqiti.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive Ledger Audit Service
 * 
 * Provides advanced audit trail operations including:
 * - Complex audit trail searches
 * - Compliance report generation
 * - Anomaly detection
 * - Statistical analysis
 * - Export functionality
 * - Real-time monitoring
 * - Payment event processing via Kafka consumers
 * 
 * @author Waqiti Ledger Team
 * @since 2.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LedgerAuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    /**
     * Advanced audit trail search with multiple filters and pagination
     */
    public Page<LedgerAuditTrailResponse> searchAuditTrail(AuditSearchRequest request, Pageable pageable) {
        log.debug("Performing advanced audit search with filters: {}", request);

        try {
            // Build dynamic query based on request filters
            List<AuditLogEntry> allResults = auditLogRepository.findByQuery(
                request.getEntityType(),
                request.getEntityId(),
                request.getAction(),
                request.getPerformedBy(),
                request.getStartDate(),
                request.getEndDate()
            );

            // Apply additional filters
            List<AuditLogEntry> filteredResults = allResults.stream()
                .filter(entry -> filterBySuccess(entry, request.getSuccessful()))
                .filter(entry -> filterByIpAddress(entry, request.getIpAddress()))
                .filter(entry -> filterByCorrelationId(entry, request.getCorrelationId()))
                .filter(entry -> filterByMetadata(entry, request.getMetadata()))
                .collect(Collectors.toList());

            // Apply pagination
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filteredResults.size());
            
            List<AuditLogEntry> pageContent = start < filteredResults.size() ? 
                filteredResults.subList(start, end) : Collections.emptyList();

            // Convert to DTOs
            List<LedgerAuditTrailResponse> responses = pageContent.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

            return new PageImpl<>(responses, pageable, filteredResults.size());

        } catch (Exception e) {
            log.error("Failed to perform audit trail search", e);
            throw new RuntimeException("Audit search failed", e);
        }
    }

    /**
     * Process Payment Initiated Event for Ledger Entries
     * Called by Kafka consumer when payment is initiated
     */
    @Transactional
    public void processPaymentInitiatedEvent(PaymentInitiatedEvent event) {
        log.info("Processing PaymentInitiatedEvent for payment: {}", event.getPaymentId());
        
        try {
            // Create audit entry for payment initiation
            AuditTrailService.AuditEvent auditEvent = AuditTrailService.AuditEvent.builder()
                .entityType("PAYMENT")
                .entityId(event.getPaymentId())
                .action("PAYMENT_INITIATED")
                .performedBy("system")
                .performedByRole("SYSTEM")
                .correlationId(event.getCorrelationId())
                .description(String.format("Payment initiated from %s to %s for amount %s %s", 
                    event.getSenderId(), event.getReceiverId(), event.getAmount(), event.getCurrency()))
                .source("payment-service")
                .successful(true)
                .metadata(Map.of(
                    "paymentId", event.getPaymentId(),
                    "senderId", event.getSenderId(),
                    "receiverId", event.getReceiverId(),
                    "amount", event.getAmount().toString(),
                    "currency", event.getCurrency(),
                    "eventType", "PaymentInitiated"
                ))
                .build();

            auditTrailService.logAuditEvent(auditEvent);
            
            log.debug("Successfully processed PaymentInitiatedEvent audit for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to process PaymentInitiatedEvent for payment: {}", event.getPaymentId(), e);
            
            // Create error audit entry
            AuditTrailService.AuditEvent errorEvent = AuditTrailService.AuditEvent.builder()
                .entityType("PAYMENT")
                .entityId(event.getPaymentId())
                .action("PAYMENT_INITIATED_AUDIT_FAILED")
                .performedBy("system")
                .performedByRole("SYSTEM")
                .correlationId(event.getCorrelationId())
                .description("Failed to process payment initiated audit")
                .source("ledger-service")
                .successful(false)
                .errorMessage(e.getMessage())
                .metadata(Map.of(
                    "paymentId", event.getPaymentId(),
                    "error", e.getClass().getSimpleName()
                ))
                .build();

            try {
                auditTrailService.logAuditEvent(errorEvent);
            } catch (Exception auditError) {
                log.error("Failed to log audit error for payment: {}", event.getPaymentId(), auditError);
            }
            
            throw new RuntimeException("Failed to process payment initiated audit", e);
        }
    }

    /**
     * Process Payment Completed Event for Ledger Finalization
     */
    @Transactional
    public void processPaymentCompletedEvent(PaymentCompletedEvent event) {
        log.info("Processing PaymentCompletedEvent for payment: {}", event.getPaymentId());
        
        try {
            // Create audit entry for payment completion
            AuditTrailService.AuditEvent auditEvent = AuditTrailService.AuditEvent.builder()
                .entityType("PAYMENT")
                .entityId(event.getPaymentId())
                .action("PAYMENT_COMPLETED")
                .performedBy("system")
                .performedByRole("SYSTEM")
                .correlationId(event.getCorrelationId())
                .description(String.format("Payment completed from %s to %s for amount %s %s", 
                    event.getSenderId(), event.getReceiverId(), event.getAmount(), event.getCurrency()))
                .source("payment-service")
                .successful(true)
                .metadata(Map.of(
                    "paymentId", event.getPaymentId(),
                    "senderId", event.getSenderId(),
                    "receiverId", event.getReceiverId(),
                    "amount", event.getAmount().toString(),
                    "currency", event.getCurrency(),
                    "completedAt", event.getCompletedAt().toString(),
                    "eventType", "PaymentCompleted"
                ))
                .build();

            auditTrailService.logAuditEvent(auditEvent);
            
            log.debug("Successfully processed PaymentCompletedEvent audit for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to process PaymentCompletedEvent for payment: {}", event.getPaymentId(), e);
            throw new RuntimeException("Failed to process payment completed audit", e);
        }
    }

    /**
     * Process Compliance Audit Trail Event
     */
    @Transactional
    public void processComplianceAuditTrailEvent(ComplianceAuditTrailEvent event) {
        log.info("Processing ComplianceAuditTrailEvent: {}", event.getEventId());
        
        try {
            // Create detailed audit entry for compliance event
            AuditTrailService.AuditEvent auditEvent = AuditTrailService.AuditEvent.builder()
                .entityType("COMPLIANCE")
                .entityId(event.getEventId())
                .action(event.getComplianceAction())
                .performedBy(event.getPerformedBy())
                .performedByRole(event.getPerformedByRole())
                .correlationId(event.getCorrelationId())
                .description(event.getDescription())
                .source(event.getSource())
                .successful(event.isSuccessful())
                .errorMessage(event.getErrorMessage())
                .metadata(Map.of(
                    "eventId", event.getEventId(),
                    "complianceType", event.getComplianceType(),
                    "riskLevel", event.getRiskLevel(),
                    "eventType", "ComplianceAuditTrail"
                ))
                .build();

            auditTrailService.logAuditEvent(auditEvent);
            
            log.debug("Successfully processed ComplianceAuditTrailEvent: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process ComplianceAuditTrailEvent: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to process compliance audit trail event", e);
        }
    }

    /**
     * Log audit verification attempt for compliance tracking
     */
    @Transactional
    public void logAuditVerification(String userId, LocalDateTime startDate, LocalDateTime endDate, 
                                   boolean isValid, int violationCount, String clientIp) {
        try {
            AuditTrailService.AuditEvent verificationEvent = AuditTrailService.AuditEvent.builder()
                .entityType("AUDIT_VERIFICATION")
                .entityId(UUID.randomUUID().toString())
                .action("VERIFY_INTEGRITY")
                .performedBy(userId)
                .performedByRole(SecurityUtils.getCurrentUserRole())
                .correlationId(UUID.randomUUID().toString())
                .description(String.format("Audit integrity verification for period %s to %s", startDate, endDate))
                .source("ledger-audit-service")
                .successful(isValid)
                .errorMessage(violationCount > 0 ? violationCount + " violations found" : null)
                .metadata(Map.of(
                    "startDate", startDate.toString(),
                    "endDate", endDate.toString(),
                    "violationCount", violationCount,
                    "clientIp", clientIp
                ))
                .build();

            auditTrailService.logAuditEvent(verificationEvent);

        } catch (Exception e) {
            log.error("Failed to log audit verification", e);
        }
    }

    /**
     * Generate comprehensive compliance audit report
     */
    public byte[] generateComplianceReport(LocalDateTime startDate, LocalDateTime endDate, 
                                         String format, String reportType) {
        log.info("Generating compliance report: {} format: {} for period: {} to {}", 
                reportType, format, startDate, endDate);

        try {
            // Gather audit statistics
            AuditStatisticsResponse stats = getAuditStatistics(startDate, endDate);
            
            // Get critical audit events
            List<LedgerAuditTrailResponse> criticalEvents = getCriticalAuditEvents(startDate, endDate);
            
            // Get failed operations
            List<LedgerAuditTrailResponse> failures = getFailedOperations(startDate, endDate, 100);
            
            // Detect anomalies
            List<AuditAnomalyResponse> anomalies = detectAnomalies(startDate, endDate, "MEDIUM");

            switch (format.toUpperCase()) {
                case "PDF":
                    return generatePDFReport(stats, criticalEvents, failures, anomalies, reportType);
                case "EXCEL":
                    return generateExcelReport(stats, criticalEvents, failures, anomalies, reportType);
                case "CSV":
                    return generateCSVReport(stats, criticalEvents, failures, anomalies);
                case "JSON":
                    return generateJSONReport(stats, criticalEvents, failures, anomalies);
                default:
                    throw new IllegalArgumentException("Unsupported format: " + format);
            }

        } catch (Exception e) {
            log.error("Failed to generate compliance report", e);
            throw new RuntimeException("Report generation failed", e);
        }
    }

    /**
     * Get comprehensive audit statistics and metrics
     */
    public AuditStatisticsResponse getAuditStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Calculating audit statistics for period: {} to {}", startDate, endDate);

        try {
            List<AuditLogEntry> entries = auditLogRepository.findByTimestampBetweenOrderByTimestamp(startDate, endDate);

            AuditStatisticsResponse stats = new AuditStatisticsResponse();
            stats.setPeriodStart(startDate);
            stats.setPeriodEnd(endDate);
            stats.setTotalEntries(entries.size());

            // Calculate success metrics
            long successfulOps = entries.stream().mapToLong(e -> e.isSuccessful() ? 1 : 0).sum();
            long failedOps = entries.size() - successfulOps;
            
            stats.setSuccessfulOperations(successfulOps);
            stats.setFailedOperations(failedOps);
            stats.setSuccessRate(entries.size() > 0 ? (double) successfulOps / entries.size() * 100 : 0.0);

            // Group by operation type
            Map<String, Long> operationsByType = entries.stream()
                .collect(Collectors.groupingBy(AuditLogEntry::getAction, Collectors.counting()));
            stats.setOperationsByType(operationsByType);

            // Group by user
            Map<String, Long> operationsByUser = entries.stream()
                .collect(Collectors.groupingBy(AuditLogEntry::getPerformedBy, Collectors.counting()));
            stats.setOperationsByUser(operationsByUser);

            // Group by entity type
            Map<String, Long> operationsByEntity = entries.stream()
                .collect(Collectors.groupingBy(AuditLogEntry::getEntityType, Collectors.counting()));
            stats.setOperationsByEntity(operationsByEntity);

            return stats;

        } catch (Exception e) {
            log.error("Failed to calculate audit statistics", e);
            throw new RuntimeException("Statistics calculation failed", e);
        }
    }

    /**
     * Get failed operations for analysis
     */
    public List<LedgerAuditTrailResponse> getFailedOperations(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        log.debug("Retrieving failed operations for period: {} to {}", startDate, endDate);

        try {
            List<AuditLogEntry> failures = auditLogRepository.findBySuccessfulFalseAndTimestampBetween(startDate, endDate);
            
            return failures.stream()
                .limit(limit)
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to retrieve failed operations", e);
            throw new RuntimeException("Failed operations retrieval failed", e);
        }
    }

    /**
     * Get audit trail by correlation ID for distributed tracing
     */
    public List<LedgerAuditTrailResponse> getAuditByCorrelationId(String correlationId) {
        log.debug("Retrieving audit trail for correlation ID: {}", correlationId);

        try {
            List<AuditLogEntry> entries = auditLogRepository.findByCorrelationIdOrderByTimestamp(correlationId);
            
            return entries.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to retrieve audit trail for correlation ID: {}", correlationId, e);
            throw new RuntimeException("Correlation audit retrieval failed", e);
        }
    }

    /**
     * Get real-time audit feed for monitoring
     */
    public List<LedgerAuditTrailResponse> getLiveAuditFeed(int limit, String entityType, String action) {
        log.debug("Retrieving live audit feed with limit: {}", limit);

        try {
            // Get recent entries (last hour)
            LocalDateTime cutoff = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
            
            List<AuditLogEntry> entries = auditLogRepository.findByQuery(
                entityType, null, action, null, cutoff, LocalDateTime.now());
            
            return entries.stream()
                .limit(limit)
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to retrieve live audit feed", e);
            throw new RuntimeException("Live feed retrieval failed", e);
        }
    }

    /**
     * Get audit trail summary with period grouping
     */
    public AuditSummaryResponse getAuditSummary(LocalDateTime startDate, LocalDateTime endDate, String groupBy) {
        log.debug("Generating audit summary for period: {} to {} grouped by: {}", startDate, endDate, groupBy);

        try {
            AuditSummaryResponse summary = new AuditSummaryResponse();
            
            // Get overall statistics
            AuditStatisticsResponse overallStats = getAuditStatistics(startDate, endDate);
            summary.setOverallStats(overallStats);

            // Generate period summaries based on groupBy
            List<AuditPeriodSummary> periods = generatePeriodSummaries(startDate, endDate, groupBy);
            summary.setPeriods(periods);

            // Get top users and actions
            summary.setTopUsers(getTopUsers(startDate, endDate, 10));
            summary.setTopActions(getTopActions(startDate, endDate, 10));

            return summary;

        } catch (Exception e) {
            log.error("Failed to generate audit summary", e);
            throw new RuntimeException("Summary generation failed", e);
        }
    }

    /**
     * Export audit trail data in various formats
     */
    public byte[] exportAuditTrail(AuditExportRequest request) {
        log.info("Exporting audit trail in format: {}", request.getFormat());

        try {
            // Get audit data based on request filters
            List<AuditLogEntry> entries = auditLogRepository.findByQuery(
                null, null, null, null, request.getStartDate(), request.getEndDate());

            // Apply entity type filter if specified
            if (request.getEntityTypes() != null && !request.getEntityTypes().isEmpty()) {
                entries = entries.stream()
                    .filter(e -> request.getEntityTypes().contains(e.getEntityType()))
                    .collect(Collectors.toList());
            }

            // Apply action filter if specified
            if (request.getActions() != null && !request.getActions().isEmpty()) {
                entries = entries.stream()
                    .filter(e -> request.getActions().contains(e.getAction()))
                    .collect(Collectors.toList());
            }

            switch (request.getFormat().toUpperCase()) {
                case "CSV":
                    return exportToCSV(entries, request.getIncludeMetadata());
                case "JSON":
                    return exportToJSON(entries, request.getIncludeMetadata());
                case "XML":
                    return exportToXML(entries, request.getIncludeMetadata());
                case "EXCEL":
                    return exportToExcel(entries, request.getIncludeMetadata());
                default:
                    throw new IllegalArgumentException("Unsupported export format: " + request.getFormat());
            }

        } catch (Exception e) {
            log.error("Failed to export audit trail", e);
            throw new RuntimeException("Export failed", e);
        }
    }

    /**
     * Detect anomalous patterns in audit trail
     */
    public List<AuditAnomalyResponse> detectAnomalies(LocalDateTime startDate, LocalDateTime endDate, String sensitivity) {
        log.debug("Detecting audit anomalies for period: {} to {} with sensitivity: {}", startDate, endDate, sensitivity);

        List<AuditAnomalyResponse> anomalies = new ArrayList<>();

        try {
            List<AuditLogEntry> entries = auditLogRepository.findByTimestampBetweenOrderByTimestamp(startDate, endDate);

            // Detect unusual activity patterns
            anomalies.addAll(detectUnusualVolumePatterns(entries, sensitivity));
            anomalies.addAll(detectUnusualFailureRates(entries, sensitivity));
            anomalies.addAll(detectSuspiciousUserActivity(entries, sensitivity));
            anomalies.addAll(detectOffHoursActivity(entries, sensitivity));
            anomalies.addAll(detectUnusualIPPatterns(entries, sensitivity));
            anomalies.addAll(detectPrivilegeEscalation(entries, sensitivity));

            // Sort by risk score
            anomalies.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));

            return anomalies;

        } catch (Exception e) {
            log.error("Failed to detect audit anomalies", e);
            throw new RuntimeException("Anomaly detection failed", e);
        }
    }

    /**
     * Get audit system health status
     */
    public AuditHealthResponse getAuditSystemHealth() {
        log.debug("Checking audit system health");

        try {
            AuditHealthResponse health = new AuditHealthResponse();
            
            // Get last audit entry
            Optional<AuditLogEntry> lastEntry = auditLogRepository.findTopByOrderByTimestampDesc();
            health.setLastAuditEntry(lastEntry.map(AuditLogEntry::getTimestamp).orElse(null));

            // Calculate audit backlog (entries in last hour)
            LocalDateTime hourAgo = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
            List<AuditLogEntry> recentEntries = auditLogRepository.findByTimestampBetweenOrderByTimestamp(
                hourAgo, LocalDateTime.now());
            health.setAuditBacklog(recentEntries.size());

            // Check integrity score (last 24 hours)
            LocalDateTime dayAgo = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
            AuditTrailService.VerificationResult verification = 
                auditTrailService.verifyAuditTrailIntegrity(dayAgo, LocalDateTime.now());
            health.setIntegrityScore(verification.isValid() ? 100.0 : 
                Math.max(0, 100.0 - (verification.getViolations().size() * 10)));

            // Determine overall status
            List<String> issues = new ArrayList<>();
            if (health.getLastAuditEntry() == null || 
                health.getLastAuditEntry().isBefore(LocalDateTime.now().minus(10, ChronoUnit.MINUTES))) {
                issues.add("No recent audit entries");
            }
            if (health.getAuditBacklog() > 1000) {
                issues.add("High audit backlog");
            }
            if (health.getIntegrityScore() < 95.0) {
                issues.add("Integrity violations detected");
            }

            health.setHealthIssues(issues);
            health.setStatus(issues.isEmpty() ? "HEALTHY" : "DEGRADED");

            // Add system metrics
            Map<String, String> metrics = new HashMap<>();
            metrics.put("totalAuditEntries", String.valueOf(auditLogRepository.count()));
            metrics.put("recentEntriesPerHour", String.valueOf(recentEntries.size()));
            metrics.put("integrityScore", String.format("%.2f%%", health.getIntegrityScore()));
            health.setSystemMetrics(metrics);

            return health;

        } catch (Exception e) {
            log.error("Failed to check audit system health", e);
            
            AuditHealthResponse errorHealth = new AuditHealthResponse();
            errorHealth.setStatus("ERROR");
            errorHealth.setHealthIssues(List.of("Health check failed: " + e.getMessage()));
            return errorHealth;
        }
    }

    // Private helper methods

    private boolean filterBySuccess(AuditLogEntry entry, Boolean successful) {
        return successful == null || entry.isSuccessful() == successful;
    }

    private boolean filterByIpAddress(AuditLogEntry entry, String ipAddress) {
        return ipAddress == null || Objects.equals(entry.getIpAddress(), ipAddress);
    }

    private boolean filterByCorrelationId(AuditLogEntry entry, String correlationId) {
        return correlationId == null || Objects.equals(entry.getCorrelationId(), correlationId);
    }

    private boolean filterByMetadata(AuditLogEntry entry, Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }
        
        Map<String, Object> entryMetadata = entry.getMetadata();
        if (entryMetadata == null) {
            return false;
        }

        return metadata.entrySet().stream()
            .allMatch(filter -> Objects.equals(
                String.valueOf(entryMetadata.get(filter.getKey())), 
                filter.getValue()));
    }

    private LedgerAuditTrailResponse mapToResponse(AuditLogEntry entry) {
        return LedgerAuditTrailResponse.builder()
            .auditId(entry.getId())
            .entityType(entry.getEntityType())
            .entityId(entry.getEntityId())
            .action(entry.getAction())
            .performedBy(entry.getPerformedBy())
            .performedAt(entry.getTimestamp())
            .ipAddress(entry.getIpAddress())
            .userAgent(entry.getUserAgent())
            .previousState(deserializeState(entry.getPreviousState()))
            .newState(deserializeState(entry.getNewState()))
            .changes(deserializeState(entry.getChanges()))
            .description(entry.getDescription())
            .source(entry.getSource())
            .successful(entry.isSuccessful())
            .errorMessage(entry.getErrorMessage())
            .build();
    }

    private Map<String, Object> deserializeState(String state) {
        if (state == null) return null;
        try {
            return objectMapper.readValue(state, Map.class);
        } catch (Exception e) {
            return Map.of("raw", state);
        }
    }

    private List<LedgerAuditTrailResponse> getCriticalAuditEvents(LocalDateTime startDate, LocalDateTime endDate) {
        // Define critical actions
        List<String> criticalActions = List.of("DELETE", "PURGE", "APPROVE_HIGH_VALUE", "OVERRIDE_LIMIT", "FORCE_SETTLEMENT");
        
        List<AuditLogEntry> entries = auditLogRepository.findByTimestampBetweenOrderByTimestamp(startDate, endDate);
        
        return entries.stream()
            .filter(e -> criticalActions.contains(e.getAction()) || !e.isSuccessful())
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private byte[] generatePDFReport(AuditStatisticsResponse stats, List<LedgerAuditTrailResponse> criticalEvents,
                                   List<LedgerAuditTrailResponse> failures, List<AuditAnomalyResponse> anomalies, String reportType) {
        // Implementation would use iText or similar PDF library
        try {
            String report = String.format("""
                COMPLIANCE AUDIT REPORT (%s)
                Period: %s to %s
                
                SUMMARY STATISTICS:
                Total Entries: %d
                Success Rate: %.2f%%
                Failed Operations: %d
                
                CRITICAL EVENTS: %d
                ANOMALIES DETECTED: %d
                
                Report generated at: %s
                """, 
                reportType,
                stats.getPeriodStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                stats.getPeriodEnd().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                stats.getTotalEntries(),
                stats.getSuccessRate(),
                stats.getFailedOperations(),
                criticalEvents.size(),
                anomalies.size(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            return report.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private byte[] generateExcelReport(AuditStatisticsResponse stats, List<LedgerAuditTrailResponse> criticalEvents,
                                     List<LedgerAuditTrailResponse> failures, List<AuditAnomalyResponse> anomalies, String reportType) {
        // Implementation would use Apache POI
        return generateCSVReport(stats, criticalEvents, failures, anomalies);
    }

    private byte[] generateCSVReport(AuditStatisticsResponse stats, List<LedgerAuditTrailResponse> criticalEvents,
                                   List<LedgerAuditTrailResponse> failures, List<AuditAnomalyResponse> anomalies) {
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("Audit Report Summary\n");
            csv.append("Total Entries,").append(stats.getTotalEntries()).append("\n");
            csv.append("Success Rate,").append(stats.getSuccessRate()).append("%\n");
            csv.append("Failed Operations,").append(stats.getFailedOperations()).append("\n");
            csv.append("Critical Events,").append(criticalEvents.size()).append("\n");
            csv.append("Anomalies,").append(anomalies.size()).append("\n");
            
            return csv.toString().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("CSV generation failed", e);
        }
    }

    private byte[] generateJSONReport(AuditStatisticsResponse stats, List<LedgerAuditTrailResponse> criticalEvents,
                                    List<LedgerAuditTrailResponse> failures, List<AuditAnomalyResponse> anomalies) {
        try {
            Map<String, Object> report = Map.of(
                "statistics", stats,
                "criticalEvents", criticalEvents,
                "failures", failures,
                "anomalies", anomalies,
                "generatedAt", LocalDateTime.now()
            );
            
            return objectMapper.writeValueAsBytes(report);
        } catch (Exception e) {
            throw new RuntimeException("JSON generation failed", e);
        }
    }

    private List<AuditPeriodSummary> generatePeriodSummaries(LocalDateTime startDate, LocalDateTime endDate, String groupBy) {
        List<AuditPeriodSummary> summaries = new ArrayList<>();
        
        ChronoUnit unit = switch (groupBy.toUpperCase()) {
            case "HOURLY" -> ChronoUnit.HOURS;
            case "DAILY" -> ChronoUnit.DAYS;
            case "WEEKLY" -> ChronoUnit.WEEKS;
            case "MONTHLY" -> ChronoUnit.MONTHS;
            default -> ChronoUnit.DAYS;
        };

        LocalDateTime current = startDate;
        while (current.isBefore(endDate)) {
            LocalDateTime periodEnd = current.plus(1, unit);
            if (periodEnd.isAfter(endDate)) {
                periodEnd = endDate;
            }

            AuditStatisticsResponse periodStats = getAuditStatistics(current, periodEnd);
            
            AuditPeriodSummary summary = new AuditPeriodSummary();
            summary.setPeriodStart(current);
            summary.setPeriodEnd(periodEnd);
            summary.setTotalOperations(periodStats.getTotalEntries());
            summary.setSuccessRate(periodStats.getSuccessRate());
            
            // Find most active user and action for period
            if (!periodStats.getOperationsByUser().isEmpty()) {
                summary.setMostActiveUser(periodStats.getOperationsByUser().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("N/A"));
            }
            
            if (!periodStats.getOperationsByType().isEmpty()) {
                summary.setMostCommonAction(periodStats.getOperationsByType().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("N/A"));
            }

            summaries.add(summary);
            current = periodEnd;
        }

        return summaries;
    }

    private List<String> getTopUsers(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        AuditStatisticsResponse stats = getAuditStatistics(startDate, endDate);
        return stats.getOperationsByUser().entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private List<String> getTopActions(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        AuditStatisticsResponse stats = getAuditStatistics(startDate, endDate);
        return stats.getOperationsByType().entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private byte[] exportToCSV(List<AuditLogEntry> entries, Boolean includeMetadata) {
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("ID,Timestamp,EntityType,EntityId,Action,PerformedBy,IPAddress,Successful,Description");
            if (Boolean.TRUE.equals(includeMetadata)) {
                csv.append(",Metadata");
            }
            csv.append("\n");

            for (AuditLogEntry entry : entries) {
                csv.append(entry.getId()).append(",")
                   .append(entry.getTimestamp()).append(",")
                   .append(entry.getEntityType()).append(",")
                   .append(entry.getEntityId()).append(",")
                   .append(entry.getAction()).append(",")
                   .append(entry.getPerformedBy()).append(",")
                   .append(entry.getIpAddress()).append(",")
                   .append(entry.isSuccessful()).append(",")
                   .append("\"").append(entry.getDescription()).append("\"");
                
                if (Boolean.TRUE.equals(includeMetadata) && entry.getMetadata() != null) {
                    csv.append(",\"").append(objectMapper.writeValueAsString(entry.getMetadata())).append("\"");
                }
                csv.append("\n");
            }

            return csv.toString().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("CSV export failed", e);
        }
    }

    private byte[] exportToJSON(List<AuditLogEntry> entries, Boolean includeMetadata) {
        try {
            if (Boolean.FALSE.equals(includeMetadata)) {
                // Strip metadata for export
                List<Map<String, Object>> sanitized = entries.stream()
                    .map(this::entryToMapWithoutMetadata)
                    .collect(Collectors.toList());
                return objectMapper.writeValueAsBytes(sanitized);
            } else {
                return objectMapper.writeValueAsBytes(entries);
            }
        } catch (Exception e) {
            throw new RuntimeException("JSON export failed", e);
        }
    }

    private byte[] exportToXML(List<AuditLogEntry> entries, Boolean includeMetadata) {
        try {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<auditTrail>\n");

            for (AuditLogEntry entry : entries) {
                xml.append("  <entry>\n");
                xml.append("    <id>").append(entry.getId()).append("</id>\n");
                xml.append("    <timestamp>").append(entry.getTimestamp()).append("</timestamp>\n");
                xml.append("    <entityType>").append(entry.getEntityType()).append("</entityType>\n");
                xml.append("    <entityId>").append(entry.getEntityId()).append("</entityId>\n");
                xml.append("    <action>").append(entry.getAction()).append("</action>\n");
                xml.append("    <performedBy>").append(entry.getPerformedBy()).append("</performedBy>\n");
                xml.append("    <successful>").append(entry.isSuccessful()).append("</successful>\n");
                xml.append("  </entry>\n");
            }

            xml.append("</auditTrail>\n");
            return xml.toString().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("XML export failed", e);
        }
    }

    private byte[] exportToExcel(List<AuditLogEntry> entries, Boolean includeMetadata) {
        // For now, return CSV format (would implement with Apache POI)
        return exportToCSV(entries, includeMetadata);
    }

    private Map<String, Object> entryToMapWithoutMetadata(AuditLogEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entry.getId());
        map.put("timestamp", entry.getTimestamp());
        map.put("entityType", entry.getEntityType());
        map.put("entityId", entry.getEntityId());
        map.put("action", entry.getAction());
        map.put("performedBy", entry.getPerformedBy());
        map.put("successful", entry.isSuccessful());
        map.put("description", entry.getDescription());
        return map;
    }

    // Anomaly detection methods

    private List<AuditAnomalyResponse> detectUnusualVolumePatterns(List<AuditLogEntry> entries, String sensitivity) {
        List<AuditAnomalyResponse> anomalies = new ArrayList<>();
        
        // Group by hour to detect volume spikes
        Map<Integer, Long> hourlyVolume = entries.stream()
            .collect(Collectors.groupingBy(
                e -> e.getTimestamp().getHour(),
                Collectors.counting()
            ));

        double avgVolume = hourlyVolume.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
        double threshold = getSensitivityMultiplier(sensitivity) * avgVolume;

        for (Map.Entry<Integer, Long> hourEntry : hourlyVolume.entrySet()) {
            if (hourEntry.getValue() > threshold) {
                AuditAnomalyResponse anomaly = new AuditAnomalyResponse();
                anomaly.setAnomalyType("VOLUME_SPIKE");
                anomaly.setDescription(String.format("Unusual volume spike at hour %d: %d events (avg: %.1f)", 
                    hourEntry.getKey(), hourEntry.getValue(), avgVolume));
                anomaly.setRiskScore(Math.min(10.0, (hourEntry.getValue() / avgVolume) * 2));
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of("hour", hourEntry.getKey(), "volume", hourEntry.getValue(), "average", avgVolume));
                anomaly.setRecommendedAction("Investigate cause of volume spike");
                anomalies.add(anomaly);
            }
        }

        return anomalies;
    }

    private List<AuditAnomalyResponse> detectUnusualFailureRates(List<AuditLogEntry> entries, String sensitivity) {
        List<AuditAnomalyResponse> anomalies = new ArrayList<>();
        
        long totalEntries = entries.size();
        long failedEntries = entries.stream().mapToLong(e -> e.isSuccessful() ? 0 : 1).sum();
        
        if (totalEntries > 0) {
            double failureRate = (double) failedEntries / totalEntries * 100;
            double threshold = getSensitivityThreshold(sensitivity, 5.0, 10.0, 20.0); // 5%, 10%, 20% based on sensitivity

            if (failureRate > threshold) {
                AuditAnomalyResponse anomaly = new AuditAnomalyResponse();
                anomaly.setAnomalyType("HIGH_FAILURE_RATE");
                anomaly.setDescription(String.format("Unusually high failure rate: %.2f%% (%d/%d)", 
                    failureRate, failedEntries, totalEntries));
                anomaly.setRiskScore(Math.min(10.0, failureRate / 5.0));
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of("failureRate", failureRate, "threshold", threshold, "failedCount", failedEntries));
                anomaly.setRecommendedAction("Investigate system stability and error patterns");
                anomalies.add(anomaly);
            }
        }

        return anomalies;
    }

    private List<AuditAnomalyResponse> detectSuspiciousUserActivity(List<AuditLogEntry> entries, String sensitivity) {
        List<AuditAnomalyResponse> anomalies = new ArrayList<>();
        
        // Group by user and detect unusual patterns
        Map<String, List<AuditLogEntry>> userActivity = entries.stream()
            .collect(Collectors.groupingBy(AuditLogEntry::getPerformedBy));

        for (Map.Entry<String, List<AuditLogEntry>> userEntry : userActivity.entrySet()) {
            String userId = userEntry.getKey();
            List<AuditLogEntry> userEntries = userEntry.getValue();

            // Check for rapid successive operations
            for (int i = 1; i < userEntries.size(); i++) {
                LocalDateTime prev = userEntries.get(i-1).getTimestamp();
                LocalDateTime curr = userEntries.get(i).getTimestamp();
                
                if (ChronoUnit.SECONDS.between(prev, curr) < 1) {
                    AuditAnomalyResponse anomaly = new AuditAnomalyResponse();
                    anomaly.setAnomalyType("RAPID_OPERATIONS");
                    anomaly.setDescription(String.format("User %s performed rapid successive operations", userId));
                    anomaly.setRiskScore(6.0);
                    anomaly.setDetectedAt(LocalDateTime.now());
                    anomaly.setEvidence(Map.of("userId", userId, "operationCount", userEntries.size()));
                    anomaly.setRecommendedAction("Verify user legitimacy and check for automated scripts");
                    anomalies.add(anomaly);
                    break; // Only add one anomaly per user
                }
            }
        }

        return anomalies;
    }

    private List<AuditAnomalyResponse> detectOffHoursActivity(List<AuditLogEntry> entries, String sensitivity) {
        List<AuditAnomalyResponse> anomalies = new ArrayList<>();
        
        long offHoursCount = entries.stream()
            .mapToLong(e -> {
                int hour = e.getTimestamp().getHour();
                return (hour < 6 || hour > 22) ? 1 : 0; // Off hours: before 6 AM or after 10 PM
            })
            .sum();

        double offHoursRate = entries.size() > 0 ? (double) offHoursCount / entries.size() * 100 : 0;
        double threshold = getSensitivityThreshold(sensitivity, 5.0, 10.0, 20.0);

        if (offHoursRate > threshold) {
            AuditAnomalyResponse anomaly = new AuditAnomalyResponse();
            anomaly.setAnomalyType("OFF_HOURS_ACTIVITY");
            anomaly.setDescription(String.format("High off-hours activity: %.2f%% (%d operations)", offHoursRate, offHoursCount));
            anomaly.setRiskScore(Math.min(8.0, offHoursRate / 3.0));
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of("offHoursRate", offHoursRate, "offHoursCount", offHoursCount));
            anomaly.setRecommendedAction("Review justification for off-hours operations");
            anomalies.add(anomaly);
        }

        return anomalies;
    }

    private List<AuditAnomalyResponse> detectUnusualIPPatterns(List<AuditLogEntry> entries, String sensitivity) {
        List<AuditAnomalyResponse> anomalies = new ArrayList<>();
        
        // Group by user and check for multiple IPs
        Map<String, Set<String>> userIPs = entries.stream()
            .filter(e -> e.getIpAddress() != null)
            .collect(Collectors.groupingBy(
                AuditLogEntry::getPerformedBy,
                Collectors.mapping(AuditLogEntry::getIpAddress, Collectors.toSet())
            ));

        int threshold = getSensitivityThreshold(sensitivity, 2, 3, 5).intValue();

        for (Map.Entry<String, Set<String>> userEntry : userIPs.entrySet()) {
            if (userEntry.getValue().size() > threshold) {
                AuditAnomalyResponse anomaly = new AuditAnomalyResponse();
                anomaly.setAnomalyType("MULTIPLE_IP_ADDRESSES");
                anomaly.setDescription(String.format("User %s accessed from %d different IP addresses", 
                    userEntry.getKey(), userEntry.getValue().size()));
                anomaly.setRiskScore(Math.min(9.0, userEntry.getValue().size() * 1.5));
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of("userId", userEntry.getKey(), "ipCount", userEntry.getValue().size(), "ips", userEntry.getValue()));
                anomaly.setRecommendedAction("Verify user account security and check for credential compromise");
                anomalies.add(anomaly);
            }
        }

        return anomalies;
    }

    private List<AuditAnomalyResponse> detectPrivilegeEscalation(List<AuditLogEntry> entries, String sensitivity) {
        List<AuditAnomalyResponse> anomalies = new ArrayList<>();
        
        // Look for privilege-related actions
        List<String> privilegeActions = List.of("GRANT_ROLE", "REVOKE_ROLE", "ELEVATE_PRIVILEGES", "ADMIN_ACCESS");
        
        List<AuditLogEntry> privilegeEntries = entries.stream()
            .filter(e -> privilegeActions.contains(e.getAction()))
            .collect(Collectors.toList());

        if (!privilegeEntries.isEmpty()) {
            AuditAnomalyResponse anomaly = new AuditAnomalyResponse();
            anomaly.setAnomalyType("PRIVILEGE_CHANGES");
            anomaly.setDescription(String.format("Detected %d privilege-related operations", privilegeEntries.size()));
            anomaly.setRiskScore(7.0);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of("privilegeOperations", privilegeEntries.size()));
            anomaly.setRecommendedAction("Review all privilege changes for authorization");
            anomalies.add(anomaly);
        }

        return anomalies;
    }

    private double getSensitivityMultiplier(String sensitivity) {
        return switch (sensitivity.toUpperCase()) {
            case "LOW" -> 3.0;
            case "MEDIUM" -> 2.0;
            case "HIGH" -> 1.5;
            default -> 2.0;
        };
    }

    private Double getSensitivityThreshold(String sensitivity, double low, double medium, double high) {
        return switch (sensitivity.toUpperCase()) {
            case "LOW" -> high;
            case "MEDIUM" -> medium;
            case "HIGH" -> low;
            default -> medium;
        };
    }

    // Event DTOs for Kafka consumption
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentInitiatedEvent {
        private String paymentId;
        private String senderId;
        private String receiverId;
        private java.math.BigDecimal amount;
        private String currency;
        private String correlationId;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentCompletedEvent {
        private String paymentId;
        private String senderId;
        private String receiverId;
        private java.math.BigDecimal amount;
        private String currency;
        private String correlationId;
        private java.time.Instant completedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceAuditTrailEvent {
        private String eventId;
        private String complianceAction;
        private String performedBy;
        private String performedByRole;
        private String correlationId;
        private String description;
        private String source;
        private boolean successful;
        private String errorMessage;
        private String complianceType;
        private String riskLevel;
    }
}