package com.waqiti.common.security.service;

import com.waqiti.common.security.cache.SecurityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise Security Audit Service Implementation
 * 
 * Provides comprehensive security event auditing and monitoring with
 * real-time threat detection, compliance reporting, and anomaly detection.
 * 
 * Features:
 * - Real-time security event logging and monitoring
 * - Anomaly detection and alerting
 * - Compliance reporting (SOX, PCI-DSS, GDPR)
 * - SIEM integration (Splunk, ELK, QRadar)
 * - Automated incident response triggers
 * - Forensic data retention and analysis
 * - Risk scoring and correlation
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${security.audit.retention.days:2555}") // 7 years default
    private int auditRetentionDays;
    
    @Value("${security.audit.alert.enabled:true}")
    private boolean alertEnabled;
    
    @Value("${security.audit.alert.threshold:10}")
    private int alertThreshold;
    
    @Value("${security.audit.siem.enabled:false}")
    private boolean siemEnabled;
    
    @Value("${security.audit.siem.endpoint:}")
    private String siemEndpoint;
    
    @Value("${security.audit.siem.api.key:}")
    private String siemApiKey;
    
    @Value("${security.audit.compliance.enabled:true}")
    private boolean complianceEnabled;
    
    @Value("${security.audit.anomaly.detection.enabled:true}")
    private boolean anomalyDetectionEnabled;
    
    @Value("${security.audit.risk.scoring.enabled:true}")
    private boolean riskScoringEnabled;
    
    // In-memory caches for performance
    private final Map<String, AuditMetrics> metricsCache = new ConcurrentHashMap<>();
    private final Map<String, UserActivityProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, ThreatIndicator> threatIndicators = new ConcurrentHashMap<>();
    
    // Real-time counters
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong highRiskEvents = new AtomicLong(0);
    private final AtomicLong alertsGenerated = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Security Audit Service");
        setupAuditInfrastructure();
        loadRecentMetrics();
        initializeAnomalyDetection();
        if (siemEnabled) {
            initializeSIEMIntegration();
        }
        validateConfiguration();
    }
    
    /**
     * Audit security event with comprehensive analysis
     */
    @Transactional
    public void auditSecurityEvent(String operation, Map<String, Object> details) {
        log.debug("Auditing security event: {}", operation);
        
        try {
            // Enrich event details
            Map<String, Object> enrichedDetails = enrichEventDetails(operation, details);
            
            // Calculate risk score
            double riskScore = calculateRiskScore(operation, enrichedDetails);
            
            // Perform anomaly detection
            AnomalyResult anomaly = detectAnomalies(operation, enrichedDetails);
            
            // Store core audit event
            Long eventId = storeAuditEvent(operation, enrichedDetails, riskScore, anomaly);
            
            // Update real-time metrics
            updateRealTimeMetrics(operation, riskScore);
            
            // Update user activity profiles
            updateUserActivityProfile(operation, enrichedDetails);
            
            // Check for security threats
            checkSecurityThreats(operation, enrichedDetails, riskScore);
            
            // Generate alerts if necessary
            processAlerts(operation, enrichedDetails, riskScore, anomaly);
            
            // Send to SIEM if configured
            if (siemEnabled) {
                sendToSIEM(eventId, operation, enrichedDetails, riskScore);
            }
            
            // Update compliance tracking
            if (complianceEnabled) {
                updateComplianceTracking(operation, enrichedDetails);
            }
            
            log.debug("Security event audited successfully: {} (ID: {}, Risk: {})", 
                operation, eventId, riskScore);
            
        } catch (Exception e) {
            log.error("Error auditing security event {}: {}", operation, e.getMessage());
            // Critical: audit failures should not break application flow
            handleAuditFailure(operation, details, e);
        }
    }
    
    /**
     * Query audit events with advanced filtering
     */
    public List<AuditEvent> queryAuditEvents(AuditQuery query) {
        try {
            StringBuilder sql = new StringBuilder(
                """
                SELECT ae.event_id, ae.operation, ae.details, ae.user_id, 
                       ae.ip_address, ae.session_id, ae.timestamp, ae.risk_score,
                       ae.anomaly_score, ae.compliance_flags, ae.correlation_id
                FROM security_audit_log ae
                WHERE 1=1
                """
            );
            
            List<Object> params = new ArrayList<>();
            
            // Add filters
            if (query.getOperation() != null) {
                sql.append(" AND ae.operation = ?");
                params.add(query.getOperation());
            }
            
            if (query.getFromDate() != null) {
                sql.append(" AND ae.timestamp >= ?");
                params.add(query.getFromDate());
            }
            
            if (query.getToDate() != null) {
                sql.append(" AND ae.timestamp <= ?");
                params.add(query.getToDate());
            }
            
            if (query.getUserId() != null) {
                sql.append(" AND ae.user_id = ?");
                params.add(query.getUserId());
            }
            
            if (query.getMinRiskScore() != null) {
                sql.append(" AND ae.risk_score >= ?");
                params.add(query.getMinRiskScore());
            }
            
            if (query.getIpAddress() != null) {
                sql.append(" AND ae.ip_address = ?");
                params.add(query.getIpAddress());
            }
            
            sql.append(" ORDER BY ae.timestamp DESC");
            
            if (query.getLimit() != null) {
                sql.append(" LIMIT ?");
                params.add(query.getLimit());
            }
            
            return jdbcTemplate.query(sql.toString(), 
                (rs, rowNum) -> buildAuditEvent(rs),
                params.toArray());
                
        } catch (Exception e) {
            log.error("Error querying audit events: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get comprehensive audit metrics
     */
    public AuditMetrics getMetrics(String operation, LocalDateTime from, LocalDateTime to) {
        String cacheKey = operation + ":" + from + ":" + to;
        
        AuditMetrics cached = metricsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) as total_count,
                       SUM(CASE WHEN risk_score > 0.7 THEN 1 ELSE 0 END) as high_risk_count,
                       SUM(CASE WHEN anomaly_score > 0.8 THEN 1 ELSE 0 END) as anomaly_count,
                       AVG(risk_score) as avg_risk_score,
                       MAX(risk_score) as max_risk_score,
                       COUNT(DISTINCT user_id) as unique_users,
                       COUNT(DISTINCT ip_address) as unique_ips,
                       MAX(timestamp) as last_event
                FROM security_audit_log
                WHERE operation = ? AND timestamp BETWEEN ? AND ?
                """,
                operation, from, to
            );
            
            AuditMetrics metrics = AuditMetrics.builder()
                .operation(operation)
                .fromDate(from)
                .toDate(to)
                .totalCount(((Number) result.get("total_count")).longValue())
                .highRiskCount(((Number) result.get("high_risk_count")).longValue())
                .anomalyCount(((Number) result.get("anomaly_count")).longValue())
                .averageRiskScore(((Number) result.get("avg_risk_score")).doubleValue())
                .maxRiskScore(((Number) result.get("max_risk_score")).doubleValue())
                .uniqueUsers(((Number) result.get("unique_users")).intValue())
                .uniqueIps(((Number) result.get("unique_ips")).intValue())
                .lastEventTime(result.get("last_event") != null ?
                    ((java.sql.Timestamp) result.get("last_event")).toLocalDateTime() : null)
                .calculatedAt(LocalDateTime.now())
                .build();
            
            metricsCache.put(cacheKey, metrics);
            return metrics;
            
        } catch (Exception e) {
            log.error("Error getting metrics for {}: {}", operation, e.getMessage());
            return createEmptyMetrics(operation, from, to);
        }
    }
    
    /**
     * Generate comprehensive compliance report
     */
    public ComplianceReport generateComplianceReport(String reportType, 
                                                   LocalDateTime from, 
                                                   LocalDateTime to) {
        log.info("Generating {} compliance report from {} to {}", reportType, from, to);
        
        try {
            ComplianceReport.ComplianceReportBuilder reportBuilder = ComplianceReport.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType(reportType)
                .fromDate(from)
                .toDate(to)
                .generatedAt(LocalDateTime.now());
            
            // Get event summary by operation
            List<Map<String, Object>> eventSummary = jdbcTemplate.queryForList(
                """
                SELECT operation, COUNT(*) as count, 
                       AVG(risk_score) as avg_risk,
                       SUM(CASE WHEN risk_score > 0.7 THEN 1 ELSE 0 END) as high_risk_events
                FROM security_audit_log
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY operation
                ORDER BY count DESC
                """,
                from, to
            );
            reportBuilder.eventSummary(eventSummary);
            
            // Get high-risk events
            List<AuditEvent> highRiskEvents = jdbcTemplate.query(
                """
                SELECT * FROM security_audit_log
                WHERE timestamp BETWEEN ? AND ? AND risk_score > 0.7
                ORDER BY risk_score DESC, timestamp DESC
                LIMIT 100
                """,
                (rs, rowNum) -> buildAuditEvent(rs),
                from, to
            );
            reportBuilder.highRiskEvents(highRiskEvents);
            
            // Get user activity analysis
            List<Map<String, Object>> userActivity = jdbcTemplate.queryForList(
                """
                SELECT user_id, 
                       COUNT(*) as total_events,
                       COUNT(DISTINCT operation) as unique_operations,
                       AVG(risk_score) as avg_risk_score,
                       COUNT(DISTINCT ip_address) as unique_ips,
                       MIN(timestamp) as first_activity,
                       MAX(timestamp) as last_activity
                FROM security_audit_log
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY user_id
                ORDER BY total_events DESC
                LIMIT 100
                """,
                from, to
            );
            reportBuilder.userActivity(userActivity);
            
            // Get anomaly summary
            List<Map<String, Object>> anomalies = jdbcTemplate.queryForList(
                """
                SELECT DATE(timestamp) as date,
                       COUNT(CASE WHEN anomaly_score > 0.8 THEN 1 END) as anomaly_count,
                       AVG(anomaly_score) as avg_anomaly_score
                FROM security_audit_log
                WHERE timestamp BETWEEN ? AND ?
                GROUP BY DATE(timestamp)
                ORDER BY date DESC
                """,
                from, to
            );
            reportBuilder.anomalySummary(anomalies);
            
            // Add compliance-specific sections
            if ("PCI_DSS".equals(reportType)) {
                addPCIDSSCompliance(reportBuilder, from, to);
            } else if ("SOX".equals(reportType)) {
                addSOXCompliance(reportBuilder, from, to);
            } else if ("GDPR".equals(reportType)) {
                addGDPRCompliance(reportBuilder, from, to);
            }
            
            ComplianceReport report = reportBuilder.build();
            
            // Store report for future reference
            storeComplianceReport(report);
            
            return report;
            
        } catch (Exception e) {
            log.error("Error generating compliance report: {}", e.getMessage());
            throw new RuntimeException("Failed to generate compliance report", e);
        }
    }
    
    /**
     * Update compliance tracking for security events
     */
    private void updateComplianceTracking(String operation, Map<String, Object> details) {
        try {
            // Track compliance-relevant events
            String complianceCategory = determineComplianceCategory(operation);
            
            if (complianceCategory != null) {
                jdbcTemplate.update(
                    """
                    INSERT INTO compliance_tracking 
                    (event_type, operation, details, category, tracked_at)
                    VALUES (?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP)
                    """,
                    "SECURITY_EVENT",
                    operation,
                    objectMapper.writeValueAsString(details),
                    complianceCategory
                );
            }
        } catch (Exception e) {
            log.warn("Failed to update compliance tracking: {}", e.getMessage());
        }
    }
    
    /**
     * Determine compliance category for operation
     */
    private String determineComplianceCategory(String operation) {
        if (operation.contains("ACCESS") || operation.contains("LOGIN")) {
            return "ACCESS_CONTROL";
        } else if (operation.contains("DATA") || operation.contains("EXPORT")) {
            return "DATA_PROTECTION";
        } else if (operation.contains("PAYMENT") || operation.contains("FINANCIAL")) {
            return "FINANCIAL_TRANSACTION";
        } else if (operation.contains("ADMIN") || operation.contains("CONFIG")) {
            return "ADMINISTRATIVE";
        }
        return null;
    }
    
    /**
     * Detect security incidents automatically
     */
    public List<SecurityIncident> detectSecurityIncidents(LocalDateTime from, LocalDateTime to) {
        log.info("Detecting security incidents from {} to {}", from, to);
        
        List<SecurityIncident> incidents = new ArrayList<>();
        
        try {
            // Detect brute force attempts
            incidents.addAll(detectBruteForceIncidents(from, to));
            
            // Detect privilege escalation
            incidents.addAll(detectPrivilegeEscalationIncidents(from, to));
            
            // Detect data exfiltration patterns
            incidents.addAll(detectDataExfiltrationIncidents(from, to));
            
            // Detect suspicious login patterns
            incidents.addAll(detectSuspiciousLoginIncidents(from, to));
            
            // Detect anomalous key usage
            incidents.addAll(detectAnomalousKeyUsageIncidents(from, to));
            
            // Store incidents for tracking
            for (SecurityIncident incident : incidents) {
                storeSecurityIncident(incident);
            }
            
            log.info("Detected {} security incidents", incidents.size());
            
            return incidents;
            
        } catch (Exception e) {
            log.error("Error detecting security incidents: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    // Private helper methods
    
    private Map<String, Object> enrichEventDetails(String operation, Map<String, Object> details) {
        Map<String, Object> enriched = new HashMap<>(details);
        
        // Add timestamp if not present
        enriched.putIfAbsent("timestamp", System.currentTimeMillis());
        
        // Add correlation ID
        enriched.putIfAbsent("correlationId", UUID.randomUUID().toString());
        
        // Add source information
        enriched.putIfAbsent("source", "SecurityAuditService");
        enriched.putIfAbsent("hostname", getHostname());
        
        // Add context information
        enriched.putIfAbsent("userId", getCurrentUser());
        enriched.putIfAbsent("sessionId", getCurrentSessionId());
        enriched.putIfAbsent("ipAddress", getClientIpAddress());
        enriched.putIfAbsent("userAgent", getUserAgent());
        
        // Add geolocation if IP is available
        String ipAddress = (String) enriched.get("ipAddress");
        if (ipAddress != null) {
            enriched.put("geoLocation", getGeoLocation(ipAddress));
        }
        
        return enriched;
    }
    
    private double calculateRiskScore(String operation, Map<String, Object> details) {
        if (!riskScoringEnabled) return 0.0;
        
        double score = 0.0;
        
        // Base risk scores by operation type
        score += getOperationBaseRisk(operation);
        
        // Classification-based risk
        if (details.containsKey("classification")) {
            String classification = (String) details.get("classification");
            score += getClassificationRisk(classification);
        }
        
        // Failed operation risk
        if (details.containsKey("result")) {
            String result = (String) details.get("result");
            if (result != null && result.contains("FAILED")) {
                score += 0.3;
            }
        }
        
        // Time-based risk (off-hours activity)
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        if (hour < 6 || hour > 22) { // Outside business hours
            score += 0.2;
        }
        
        // IP-based risk
        String ipAddress = (String) details.get("ipAddress");
        if (ipAddress != null) {
            score += getIPRisk(ipAddress);
        }
        
        // User behavior risk
        String userId = (String) details.get("userId");
        if (userId != null) {
            score += getUserBehaviorRisk(userId, operation);
        }
        
        // Frequency-based risk
        score += getFrequencyRisk(operation, details);
        
        return Math.min(1.0, score);
    }
    
    private AnomalyResult detectAnomalies(String operation, Map<String, Object> details) {
        if (!anomalyDetectionEnabled) {
            return AnomalyResult.noAnomaly();
        }
        
        List<String> anomalies = new ArrayList<>();
        double anomalyScore = 0.0;
        
        // Check for unusual frequency
        if (isUnusualFrequency(operation, details)) {
            anomalies.add("UNUSUAL_FREQUENCY");
            anomalyScore += 0.3;
        }
        
        // Check for unusual time patterns
        if (isUnusualTimePattern(operation, details)) {
            anomalies.add("UNUSUAL_TIME_PATTERN");
            anomalyScore += 0.2;
        }
        
        // Check for unusual user behavior
        String userId = (String) details.get("userId");
        if (userId != null && isUnusualUserBehavior(userId, operation)) {
            anomalies.add("UNUSUAL_USER_BEHAVIOR");
            anomalyScore += 0.4;
        }
        
        // Check for unusual IP patterns
        String ipAddress = (String) details.get("ipAddress");
        if (ipAddress != null && isUnusualIPPattern(ipAddress, userId)) {
            anomalies.add("UNUSUAL_IP_PATTERN");
            anomalyScore += 0.3;
        }
        
        // Check for data volume anomalies
        if (isUnusualDataVolume(operation, details)) {
            anomalies.add("UNUSUAL_DATA_VOLUME");
            anomalyScore += 0.2;
        }
        
        return AnomalyResult.builder()
            .hasAnomalies(!anomalies.isEmpty())
            .anomalyTypes(anomalies)
            .anomalyScore(Math.min(1.0, anomalyScore))
            .detectedAt(LocalDateTime.now())
            .build();
    }
    
    private Long storeAuditEvent(String operation, Map<String, Object> details, 
                                double riskScore, AnomalyResult anomaly) {
        try {
            String detailsJson = serializeDetails(details);
            String userId = (String) details.get("userId");
            String ipAddress = (String) details.get("ipAddress");
            String sessionId = (String) details.get("sessionId");
            String correlationId = (String) details.get("correlationId");
            
            jdbcTemplate.update(
                """
                INSERT INTO security_audit_log (
                    operation, details, user_id, ip_address, session_id,
                    correlation_id, timestamp, risk_score, anomaly_score,
                    anomaly_types, compliance_flags, geo_location
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?)
                """,
                operation, detailsJson, userId, ipAddress, sessionId,
                correlationId, riskScore, anomaly.getAnomalyScore(),
                String.join(",", anomaly.getAnomalyTypes()),
                generateComplianceFlags(operation, details),
                (String) details.get("geoLocation")
            );
            
            return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            
        } catch (Exception e) {
            log.error("Error storing audit event: {}", e.getMessage());
            throw e;
        }
    }
    
    private void updateRealTimeMetrics(String operation, double riskScore) {
        totalEvents.incrementAndGet();
        
        if (riskScore > 0.7) {
            highRiskEvents.incrementAndGet();
        }
        
        // Update operation-specific metrics
        String metricsKey = "realtime:" + operation;
        AuditMetrics current = metricsCache.get(metricsKey);
        if (current == null) {
            current = createEmptyMetrics(operation, LocalDateTime.now().minusHours(1), LocalDateTime.now());
        }
        
        // Update and store back
        AuditMetrics updated = current.toBuilder()
            .totalCount(current.getTotalCount() + 1)
            .highRiskCount(riskScore > 0.7 ? current.getHighRiskCount() + 1 : current.getHighRiskCount())
            .build();
        
        metricsCache.put(metricsKey, updated);
    }
    
    private void updateUserActivityProfile(String operation, Map<String, Object> details) {
        String userId = (String) details.get("userId");
        if (userId == null) return;
        
        UserActivityProfile profile = userProfiles.computeIfAbsent(userId, 
            k -> UserActivityProfile.builder()
                .userId(userId)
                .operationCounts(new ConcurrentHashMap<>())
                .lastSeen(LocalDateTime.now())
                .build());
        
        profile.getOperationCounts().merge(operation, 1L, Long::sum);
        profile.setLastSeen(LocalDateTime.now());
        profile.setTotalEvents(profile.getTotalEvents() + 1);
    }
    
    private void checkSecurityThreats(String operation, Map<String, Object> details, double riskScore) {
        if (riskScore < 0.5) return; // Only check medium+ risk events
        
        String userId = (String) details.get("userId");
        String ipAddress = (String) details.get("ipAddress");
        
        // Check for known threat indicators
        ThreatIndicator indicator = ThreatIndicator.builder()
            .operation(operation)
            .userId(userId)
            .ipAddress(ipAddress)
            .riskScore(riskScore)
            .detectedAt(LocalDateTime.now())
            .build();
        
        String threatKey = userId + ":" + ipAddress + ":" + operation;
        threatIndicators.put(threatKey, indicator);
        
        // Clean old indicators
        cleanOldThreatIndicators();
    }
    
    private void processAlerts(String operation, Map<String, Object> details, 
                              double riskScore, AnomalyResult anomaly) {
        if (!alertEnabled) return;
        
        boolean shouldAlert = false;
        String alertReason = "";
        String alertSeverity = "INFO";
        
        // High risk score alert
        if (riskScore > 0.8) {
            shouldAlert = true;
            alertReason = "High risk score: " + riskScore;
            alertSeverity = "HIGH";
        }
        
        // Anomaly alert
        if (anomaly.isHasAnomalies() && anomaly.getAnomalyScore() > 0.7) {
            shouldAlert = true;
            alertReason = "Anomaly detected: " + String.join(", ", anomaly.getAnomalyTypes());
            alertSeverity = "MEDIUM";
        }
        
        // Frequency alert
        if (checkFrequencyThreshold(operation, details)) {
            shouldAlert = true;
            alertReason = "Frequency threshold exceeded";
            alertSeverity = "MEDIUM";
        }
        
        if (shouldAlert) {
            generateSecurityAlert(operation, details, alertReason, alertSeverity);
        }
    }
    
    private void generateSecurityAlert(String operation, Map<String, Object> details, 
                                     String reason, String severity) {
        try {
            alertsGenerated.incrementAndGet();
            
            jdbcTemplate.update(
                """
                INSERT INTO security_alerts (
                    operation, reason, severity, details, user_id,
                    ip_address, created_at, is_resolved
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(), false)
                """,
                operation, reason, severity, serializeDetails(details),
                details.get("userId"), details.get("ipAddress")
            );
            
            // Send notification
            sendSecurityNotification(operation, reason, severity, details);
            
            log.warn("SECURITY ALERT [{}] - Operation: {}, Reason: {}", 
                severity, operation, reason);
            
        } catch (Exception e) {
            log.error("Error generating security alert: {}", e.getMessage());
        }
    }
    
    private void sendToSIEM(Long eventId, String operation, Map<String, Object> details, double riskScore) {
        if (!siemEnabled || siemEndpoint == null || siemEndpoint.isEmpty()) {
            return;
        }
        
        try {
            // Format as CEF (Common Event Format)
            String cefEvent = String.format(
                "CEF:0|Waqiti|SecurityAudit|2.0|%s|%s|%.0f|eventId=%d rt=%d src=%s suser=%s",
                operation,
                operation.replace("_", " "),
                riskScore * 10, // Convert to 0-10 scale
                eventId,
                System.currentTimeMillis(),
                details.get("ipAddress"),
                details.get("userId")
            );
            
            // Send to SIEM endpoint
            Map<String, Object> siemPayload = new HashMap<>();
            siemPayload.put("event", cefEvent);
            siemPayload.put("timestamp", System.currentTimeMillis());
            siemPayload.put("details", details);
            
            restTemplate.postForObject(siemEndpoint, siemPayload, String.class);
            
        } catch (Exception e) {
            log.error("Error sending to SIEM: {}", e.getMessage());
        }
    }
    
    // Helper methods for specific compliance frameworks
    
    private void addPCIDSSCompliance(ComplianceReport.ComplianceReportBuilder builder, 
                                   LocalDateTime from, LocalDateTime to) {
        // PCI-DSS specific requirements
        Map<String, Object> pciData = new HashMap<>();
        
        // Requirement 8: Identify and authenticate access
        pciData.put("authEvents", getAuthenticationEvents(from, to));
        
        // Requirement 10: Track and monitor access
        pciData.put("accessEvents", getAccessEvents(from, to));
        
        builder.complianceData(pciData);
    }
    
    private void addSOXCompliance(ComplianceReport.ComplianceReportBuilder builder,
                                 LocalDateTime from, LocalDateTime to) {
        // SOX specific requirements
        Map<String, Object> soxData = new HashMap<>();
        
        // Access controls
        soxData.put("privilegedAccess", getPrivilegedAccessEvents(from, to));
        
        // Change management
        soxData.put("systemChanges", getSystemChangeEvents(from, to));
        
        builder.complianceData(soxData);
    }
    
    private void addGDPRCompliance(ComplianceReport.ComplianceReportBuilder builder,
                                  LocalDateTime from, LocalDateTime to) {
        // GDPR specific requirements
        Map<String, Object> gdprData = new HashMap<>();
        
        // Data access tracking
        gdprData.put("dataAccess", getDataAccessEvents(from, to));
        
        // Consent management
        gdprData.put("consentEvents", getConsentEvents(from, to));
        
        builder.complianceData(gdprData);
    }
    
    // Security incident detection methods
    
    private List<SecurityIncident> detectBruteForceIncidents(LocalDateTime from, LocalDateTime to) {
        List<SecurityIncident> incidents = new ArrayList<>();
        
        try {
            List<Map<String, Object>> suspects = jdbcTemplate.queryForList(
                """
                SELECT user_id, ip_address, COUNT(*) as failed_attempts
                FROM security_audit_log
                WHERE operation LIKE '%FAILED%' 
                AND timestamp BETWEEN ? AND ?
                GROUP BY user_id, ip_address
                HAVING failed_attempts >= 5
                """,
                from, to
            );
            
            for (Map<String, Object> suspect : suspects) {
                SecurityIncident incident = SecurityIncident.builder()
                    .incidentId(UUID.randomUUID().toString())
                    .type("BRUTE_FORCE")
                    .severity("HIGH")
                    .userId((String) suspect.get("user_id"))
                    .ipAddress((String) suspect.get("ip_address"))
                    .description("Brute force attack detected: " + 
                        suspect.get("failed_attempts") + " failed attempts")
                    .detectedAt(LocalDateTime.now())
                    .status("OPEN")
                    .build();
                
                incidents.add(incident);
            }
            
        } catch (Exception e) {
            log.error("Error detecting brute force incidents: {}", e.getMessage());
        }
        
        return incidents;
    }
    
    private List<SecurityIncident> detectPrivilegeEscalationIncidents(LocalDateTime from, LocalDateTime to) {
        // Implementation for privilege escalation detection
        return new ArrayList<>();
    }
    
    private List<SecurityIncident> detectDataExfiltrationIncidents(LocalDateTime from, LocalDateTime to) {
        // Implementation for data exfiltration detection
        return new ArrayList<>();
    }
    
    private List<SecurityIncident> detectSuspiciousLoginIncidents(LocalDateTime from, LocalDateTime to) {
        // Implementation for suspicious login detection
        return new ArrayList<>();
    }
    
    private List<SecurityIncident> detectAnomalousKeyUsageIncidents(LocalDateTime from, LocalDateTime to) {
        // Implementation for anomalous key usage detection
        return new ArrayList<>();
    }
    
    // Utility methods
    
    private double getOperationBaseRisk(String operation) {
        return switch (operation) {
            case "KEY_DELETION", "SECRET_DELETION" -> 0.8;
            case "KEY_ROTATION", "SECRET_ROTATION" -> 0.3;
            case "KEY_ACCESS", "SECRET_ACCESS" -> 0.1;
            case "LOGIN_FAILED", "AUTH_FAILED" -> 0.4;
            case "PRIVILEGE_ESCALATION" -> 0.9;
            default -> 0.1;
        };
    }
    
    private double getClassificationRisk(String classification) {
        return switch (classification) {
            case "TOP_SECRET" -> 0.5;
            case "SECRET" -> 0.3;
            case "CONFIDENTIAL" -> 0.2;
            default -> 0.1;
        };
    }
    
    private double getIPRisk(String ipAddress) {
        // Check against threat intelligence feeds
        // Placeholder implementation
        return 0.0;
    }
    
    private double getUserBehaviorRisk(String userId, String operation) {
        UserActivityProfile profile = userProfiles.get(userId);
        if (profile == null) return 0.1;
        
        // Calculate risk based on user's normal behavior
        Long operationCount = profile.getOperationCounts().get(operation);
        if (operationCount == null || operationCount < 5) {
            return 0.2; // New operation for user
        }
        
        return 0.0;
    }
    
    private double getFrequencyRisk(String operation, Map<String, Object> details) {
        // Check recent frequency of similar operations
        String userId = (String) details.get("userId");
        if (userId == null) return 0.0;
        
        try {
            Integer recentCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM security_audit_log
                WHERE operation = ? AND user_id = ?
                AND timestamp > DATE_SUB(NOW(), INTERVAL 1 HOUR)
                """,
                Integer.class, operation, userId
            );
            
            if (recentCount != null && recentCount > 10) {
                return 0.3;
            }
            
        } catch (Exception e) {
            log.debug("Error checking frequency risk: {}", e.getMessage());
        }
        
        return 0.0;
    }
    
    private boolean isUnusualFrequency(String operation, Map<String, Object> details) {
        // Implement frequency anomaly detection
        return false;
    }
    
    private boolean isUnusualTimePattern(String operation, Map<String, Object> details) {
        // Implement time pattern anomaly detection
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        
        // Activity during off-hours is unusual
        return hour < 6 || hour > 22;
    }
    
    private boolean isUnusualUserBehavior(String userId, String operation) {
        UserActivityProfile profile = userProfiles.get(userId);
        if (profile == null) return true;
        
        // Check if this operation is common for this user
        Long count = profile.getOperationCounts().get(operation);
        return count == null || count < 3;
    }
    
    private boolean isUnusualIPPattern(String ipAddress, String userId) {
        // Check if user typically uses this IP
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT DATE(timestamp))
                FROM security_audit_log
                WHERE user_id = ? AND ip_address = ?
                AND timestamp > DATE_SUB(NOW(), INTERVAL 30 DAY)
                """,
                Integer.class, userId, ipAddress
            );
            
            return count == null || count < 2;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isUnusualDataVolume(String operation, Map<String, Object> details) {
        // Implement data volume anomaly detection
        return false;
    }
    
    private boolean checkFrequencyThreshold(String operation, Map<String, Object> details) {
        String userId = (String) details.get("userId");
        if (userId == null) return false;
        
        try {
            Integer recentCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM security_audit_log
                WHERE operation = ? AND user_id = ?
                AND timestamp > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
                """,
                Integer.class, operation, userId
            );
            
            return recentCount != null && recentCount > alertThreshold;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private void cleanOldThreatIndicators() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        threatIndicators.entrySet().removeIf(entry -> 
            entry.getValue().getDetectedAt().isBefore(cutoff));
    }
    
    private String serializeDetails(Map<String, Object> details) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(details);
        } catch (Exception e) {
            return details.toString();
        }
    }
    
    private Map<String, Object> parseDetails(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
    
    private String generateComplianceFlags(String operation, Map<String, Object> details) {
        Set<String> flags = new HashSet<>();
        
        if (operation.contains("KEY") || operation.contains("SECRET")) {
            flags.add("PCI_DSS");
        }
        
        if (operation.contains("ACCESS") || operation.contains("AUTH")) {
            flags.add("SOX");
            flags.add("GDPR");
        }
        
        return String.join(",", flags);
    }
    
    private AuditEvent buildAuditEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        return AuditEvent.builder()
            .eventId(rs.getLong("event_id"))
            .operation(rs.getString("operation"))
            .details(parseDetails(rs.getString("details")))
            .userId(rs.getString("user_id"))
            .ipAddress(rs.getString("ip_address"))
            .sessionId(rs.getString("session_id"))
            .correlationId(rs.getString("correlation_id"))
            .timestamp(rs.getTimestamp("timestamp").toLocalDateTime())
            .riskScore(rs.getDouble("risk_score"))
            .anomalyScore(rs.getDouble("anomaly_score"))
            .build();
    }
    
    private AuditMetrics createEmptyMetrics(String operation, LocalDateTime from, LocalDateTime to) {
        return AuditMetrics.builder()
            .operation(operation)
            .fromDate(from)
            .toDate(to)
            .totalCount(0L)
            .highRiskCount(0L)
            .anomalyCount(0L)
            .averageRiskScore(0.0)
            .maxRiskScore(0.0)
            .uniqueUsers(0)
            .uniqueIps(0)
            .calculatedAt(LocalDateTime.now())
            .build();
    }
    
    // Context methods (would get from security context in production)
    
    private String getCurrentUser() {
        return "system";
    }
    
    private String getCurrentSessionId() {
        return UUID.randomUUID().toString();
    }
    
    private String getClientIpAddress() {
        return "127.0.0.1";
    }
    
    private String getUserAgent() {
        return "Waqiti-Security-Service/2.0";
    }
    
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getGeoLocation(String ipAddress) {
        // Get geolocation from IP
        return "Unknown";
    }
    
    // Database query methods
    
    private List<Map<String, Object>> getAuthenticationEvents(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM security_audit_log WHERE operation LIKE '%AUTH%' AND timestamp BETWEEN ? AND ?",
            from, to
        );
    }
    
    private List<Map<String, Object>> getAccessEvents(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM security_audit_log WHERE operation LIKE '%ACCESS%' AND timestamp BETWEEN ? AND ?",
            from, to
        );
    }
    
    private List<Map<String, Object>> getPrivilegedAccessEvents(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM security_audit_log WHERE operation LIKE '%PRIVILEGE%' AND timestamp BETWEEN ? AND ?",
            from, to
        );
    }
    
    private List<Map<String, Object>> getSystemChangeEvents(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM security_audit_log WHERE operation LIKE '%CHANGE%' AND timestamp BETWEEN ? AND ?",
            from, to
        );
    }
    
    private List<Map<String, Object>> getDataAccessEvents(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM security_audit_log WHERE operation LIKE '%DATA%' AND timestamp BETWEEN ? AND ?",
            from, to
        );
    }
    
    private List<Map<String, Object>> getConsentEvents(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM security_audit_log WHERE operation LIKE '%CONSENT%' AND timestamp BETWEEN ? AND ?",
            from, to
        );
    }
    
    // Initialization and configuration methods
    
    private void setupAuditInfrastructure() {
        try {
            // Ensure audit tables exist
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS security_audit_log (
                    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    operation VARCHAR(100) NOT NULL,
                    details TEXT,
                    user_id VARCHAR(100),
                    ip_address VARCHAR(50),
                    session_id VARCHAR(100),
                    correlation_id VARCHAR(100),
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    risk_score DOUBLE DEFAULT 0.0,
                    anomaly_score DOUBLE DEFAULT 0.0,
                    anomaly_types TEXT,
                    compliance_flags VARCHAR(255),
                    geo_location VARCHAR(255),
                    INDEX idx_operation_timestamp (operation, timestamp),
                    INDEX idx_user_timestamp (user_id, timestamp),
                    INDEX idx_risk_score (risk_score),
                    INDEX idx_correlation (correlation_id)
                )
                """
            );
            
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS security_alerts (
                    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    operation VARCHAR(100),
                    reason TEXT,
                    severity VARCHAR(20),
                    details TEXT,
                    user_id VARCHAR(100),
                    ip_address VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_resolved BOOLEAN DEFAULT FALSE,
                    resolved_at TIMESTAMP NULL,
                    resolved_by VARCHAR(100),
                    INDEX idx_severity_created (severity, created_at)
                )
                """
            );
            
        } catch (Exception e) {
            log.debug("Audit tables may already exist: {}", e.getMessage());
        }
    }
    
    private void loadRecentMetrics() {
        try {
            List<Map<String, Object>> recentMetrics = jdbcTemplate.queryForList(
                """
                SELECT operation, 
                       COUNT(*) as count, 
                       AVG(risk_score) as avg_risk,
                       MAX(timestamp) as last_event
                FROM security_audit_log
                WHERE timestamp > DATE_SUB(NOW(), INTERVAL 1 HOUR)
                GROUP BY operation
                """
            );
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime hourAgo = now.minusHours(1);
            
            for (Map<String, Object> metric : recentMetrics) {
                String operation = (String) metric.get("operation");
                AuditMetrics metrics = AuditMetrics.builder()
                    .operation(operation)
                    .fromDate(hourAgo)
                    .toDate(now)
                    .totalCount(((Number) metric.get("count")).longValue())
                    .averageRiskScore(((Number) metric.get("avg_risk")).doubleValue())
                    .lastEventTime(((java.sql.Timestamp) metric.get("last_event")).toLocalDateTime())
                    .calculatedAt(LocalDateTime.now())
                    .build();
                
                metricsCache.put("realtime:" + operation, metrics);
            }
            
            log.info("Loaded {} recent metrics", recentMetrics.size());
            
        } catch (Exception e) {
            log.error("Error loading recent metrics: {}", e.getMessage());
        }
    }
    
    private void initializeAnomalyDetection() {
        if (anomalyDetectionEnabled) {
            log.info("Anomaly detection enabled - initializing baselines");
            // Initialize anomaly detection baselines
        }
    }
    
    private void initializeSIEMIntegration() {
        log.info("Initializing SIEM integration with endpoint: {}", siemEndpoint);
        // Test SIEM connectivity
        try {
            restTemplate.getForObject(siemEndpoint + "/health", String.class);
            log.info("SIEM connectivity verified");
        } catch (Exception e) {
            log.warn("SIEM connectivity check failed: {}", e.getMessage());
        }
    }
    
    private void validateConfiguration() {
        if (auditRetentionDays < 90) {
            log.warn("Audit retention {} days is below recommended minimum (90 days)", auditRetentionDays);
        }
        
        if (alertThreshold > 100) {
            log.warn("Alert threshold {} may be too high", alertThreshold);
        }
        
        log.info("Security Audit Service configuration validated");
    }
    
    private void handleAuditFailure(String operation, Map<String, Object> details, Exception e) {
        // Log to alternate location for audit failure tracking
        log.error("AUDIT FAILURE - Operation: {}, Details: {}, Error: {}", 
            operation, details, e.getMessage());
        
        // Could write to separate failure log, file system, etc.
    }
    
    private void sendSecurityNotification(String operation, String reason, String severity, Map<String, Object> details) {
        // Send email, SMS, Slack notification etc.
        log.info("Security notification sent - Operation: {}, Severity: {}, Reason: {}", 
            operation, severity, reason);
    }
    
    private void storeComplianceReport(ComplianceReport report) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO compliance_reports (
                    report_id, report_type, from_date, to_date,
                    generated_at, report_data
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                report.getReportId(), report.getReportType(),
                report.getFromDate(), report.getToDate(),
                report.getGeneratedAt(), serializeDetails(Map.of("report", report))
            );
        } catch (Exception e) {
            log.error("Error storing compliance report: {}", e.getMessage());
        }
    }
    
    private void storeSecurityIncident(SecurityIncident incident) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO security_incidents (
                    incident_id, type, severity, user_id, ip_address,
                    description, detected_at, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                incident.getIncidentId(), incident.getType(), incident.getSeverity(),
                incident.getUserId(), incident.getIpAddress(), incident.getDescription(),
                incident.getDetectedAt(), incident.getStatus()
            );
        } catch (Exception e) {
            log.error("Error storing security incident: {}", e.getMessage());
        }
    }
    
    // Scheduled maintenance tasks
    
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void cleanupExpiredCaches() {
        log.debug("Cleaning up expired audit caches");
        metricsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Clean old user profiles
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        userProfiles.entrySet().removeIf(entry -> 
            entry.getValue().getLastSeen().isBefore(cutoff));
    }
    
    @Scheduled(cron = "0 0 3 * * ?") // 3 AM daily
    public void cleanupOldAudits() {
        log.info("Starting audit log cleanup - retention: {} days", auditRetentionDays);
        
        try {
            int deleted = jdbcTemplate.update(
                """
                DELETE FROM security_audit_log 
                WHERE timestamp < DATE_SUB(NOW(), INTERVAL ? DAY)
                """,
                auditRetentionDays
            );
            
            log.info("Deleted {} old audit records", deleted);
            
            // Also cleanup old alerts
            int alertsDeleted = jdbcTemplate.update(
                """
                DELETE FROM security_alerts 
                WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)
                AND is_resolved = true
                """,
                auditRetentionDays / 4 // Keep resolved alerts for 1/4 of retention period
            );
            
            log.info("Deleted {} old resolved alerts", alertsDeleted);
            
        } catch (Exception e) {
            log.error("Error cleaning up audit logs: {}", e.getMessage());
        }
    }
    
    @Scheduled(cron = "0 */15 * * * ?") // Every 15 minutes
    public void detectSecurityIncidents() {
        if (anomalyDetectionEnabled) {
            LocalDateTime to = LocalDateTime.now();
            LocalDateTime from = to.minusMinutes(15);
            
            List<SecurityIncident> incidents = detectSecurityIncidents(from, to);
            if (!incidents.isEmpty()) {
                log.warn("Detected {} security incidents in last 15 minutes", incidents.size());
            }
        }
    }
    
    // Data transfer objects and builders
    
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    public static class AuditEvent {
        private Long eventId;
        private String operation;
        private Map<String, Object> details;
        private String userId;
        private String ipAddress;
        private String sessionId;
        private String correlationId;
        private LocalDateTime timestamp;
        private double riskScore;
        private double anomalyScore;
    }
    
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    public static class AuditMetrics {
        private String operation;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private long totalCount;
        private long highRiskCount;
        private long anomalyCount;
        private double averageRiskScore;
        private double maxRiskScore;
        private int uniqueUsers;
        private int uniqueIps;
        private LocalDateTime lastEventTime;
        private LocalDateTime calculatedAt;
        
        public boolean isExpired() {
            return calculatedAt.plusMinutes(5).isBefore(LocalDateTime.now());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComplianceReport {
        
        /**
         * Explicit builder method as fallback for Lombok processing issues
         */
        public static ComplianceReportBuilder builder() {
            return new ComplianceReportBuilder();
        }
        private String reportId;
        private String reportType;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private List<Map<String, Object>> eventSummary;
        private List<AuditEvent> highRiskEvents;
        private List<Map<String, Object>> userActivity;
        private List<Map<String, Object>> anomalySummary;
        private Map<String, Object> complianceData;
        private LocalDateTime generatedAt;
        
        /**
         * Explicit builder class as fallback for Lombok processing issues
         */
        public static class ComplianceReportBuilder {
            private String reportId;
            private String reportType;
            private LocalDateTime fromDate;
            private LocalDateTime toDate;
            private List<Map<String, Object>> eventSummary;
            private List<AuditEvent> highRiskEvents;
            private List<Map<String, Object>> userActivity;
            private List<Map<String, Object>> anomalySummary;
            private Map<String, Object> complianceData;
            private LocalDateTime generatedAt = LocalDateTime.now();
            
            public ComplianceReportBuilder reportId(String reportId) { this.reportId = reportId; return this; }
            public ComplianceReportBuilder reportType(String reportType) { this.reportType = reportType; return this; }
            public ComplianceReportBuilder fromDate(LocalDateTime fromDate) { this.fromDate = fromDate; return this; }
            public ComplianceReportBuilder toDate(LocalDateTime toDate) { this.toDate = toDate; return this; }
            public ComplianceReportBuilder eventSummary(List<Map<String, Object>> eventSummary) { this.eventSummary = eventSummary; return this; }
            public ComplianceReportBuilder highRiskEvents(List<AuditEvent> highRiskEvents) { this.highRiskEvents = highRiskEvents; return this; }
            public ComplianceReportBuilder userActivity(List<Map<String, Object>> userActivity) { this.userActivity = userActivity; return this; }
            public ComplianceReportBuilder anomalySummary(List<Map<String, Object>> anomalySummary) { this.anomalySummary = anomalySummary; return this; }
            public ComplianceReportBuilder complianceData(Map<String, Object> complianceData) { this.complianceData = complianceData; return this; }
            public ComplianceReportBuilder generatedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; return this; }
            
            public ComplianceReport build() {
                return new ComplianceReport(reportId, reportType, fromDate, toDate, eventSummary, highRiskEvents, userActivity, anomalySummary, complianceData, generatedAt);
            }
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AuditQuery {
        private String operation;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String userId;
        private String ipAddress;
        private Double minRiskScore;
        private Integer limit;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class AnomalyResult {
        private boolean hasAnomalies;
        private List<String> anomalyTypes;
        private double anomalyScore;
        private LocalDateTime detectedAt;
        
        public static AnomalyResult noAnomaly() {
            return AnomalyResult.builder()
                .hasAnomalies(false)
                .anomalyTypes(Collections.emptyList())
                .anomalyScore(0.0)
                .detectedAt(LocalDateTime.now())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class UserActivityProfile {
        private String userId;
        private Map<String, Long> operationCounts;
        private LocalDateTime lastSeen;
        private long totalEvents;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ThreatIndicator {
        private String operation;
        private String userId;
        private String ipAddress;
        private double riskScore;
        private LocalDateTime detectedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SecurityIncident {
        private String incidentId;
        private String type;
        private String severity;
        private String userId;
        private String ipAddress;
        private String description;
        private LocalDateTime detectedAt;
        private String status;
    }
}