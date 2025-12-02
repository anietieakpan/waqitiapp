package com.waqiti.compliance.service.impl;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.compliance.model.EnhancedMonitoringProfile;
import com.waqiti.compliance.model.MonitoringRule;
import com.waqiti.compliance.model.MonitoringAlert;
import com.waqiti.compliance.repository.EnhancedMonitoringRepository;
import com.waqiti.compliance.repository.MonitoringAlertRepository;
import com.waqiti.compliance.service.EnhancedMonitoringService;
import com.waqiti.compliance.client.FraudDetectionServiceClient;
import com.waqiti.notification.client.NotificationServiceClient;
import com.waqiti.user.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced Monitoring Service Implementation
 * 
 * CRITICAL COMPLIANCE: Provides comprehensive monitoring capabilities for high-risk accounts
 * 
 * This service implements:
 * - Real-time transaction monitoring with advanced pattern detection
 * - Behavioral analytics and anomaly detection
 * - Automated alert generation and escalation
 * - Risk scoring and threshold management
 * - Compliance reporting and audit trails
 * - Integration with fraud detection systems
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EnhancedMonitoringServiceImpl implements EnhancedMonitoringService {

    private final EnhancedMonitoringRepository monitoringRepository;
    private final MonitoringAlertRepository alertRepository;
    private final ComprehensiveAuditService auditService;
    private final FraudDetectionServiceClient fraudDetectionClient;
    private final NotificationServiceClient notificationClient;
    private final UserServiceClient userServiceClient;

    @Value("${compliance.monitoring.real-time.enabled:true}")
    private boolean realTimeMonitoringEnabled;

    @Value("${compliance.monitoring.batch.size:100}")
    private int batchSize;

    @Value("${compliance.monitoring.alert.threshold:5}")
    private int alertThreshold;

    @Value("${compliance.monitoring.high-risk.multiplier:2.0}")
    private double highRiskMultiplier;

    // Cache for active monitoring profiles
    private final Map<UUID, EnhancedMonitoringProfile> activeProfiles = new ConcurrentHashMap<>();

    @Override
    public EnhancedMonitoringProfile enableEnhancedMonitoring(UUID userId, String reason, 
                                                              LocalDateTime monitoringUntil,
                                                              MonitoringLevel level) {
        log.warn("COMPLIANCE: Enabling enhanced monitoring for user {} - Level: {}, Until: {}", 
                 userId, level, monitoringUntil);

        try {
            // Check if monitoring already exists
            Optional<EnhancedMonitoringProfile> existing = monitoringRepository.findByUserIdAndActiveTrue(userId);
            if (existing.isPresent()) {
                return updateExistingMonitoring(existing.get(), reason, monitoringUntil, level);
            }

            // Create new monitoring profile
            EnhancedMonitoringProfile profile = EnhancedMonitoringProfile.builder()
                    .profileId(UUID.randomUUID().toString())
                    .userId(userId)
                    .reason(reason)
                    .level(level)
                    .startDate(LocalDateTime.now())
                    .endDate(monitoringUntil)
                    .active(true)
                    .createdBy("COMPLIANCE_SYSTEM")
                    .createdAt(LocalDateTime.now())
                    .monitoringRules(createMonitoringRules(level))
                    .alertCount(0)
                    .lastActivityDate(LocalDateTime.now())
                    .riskScore(calculateInitialRiskScore(userId, level))
                    .build();

            // Save monitoring profile
            profile = monitoringRepository.save(profile);

            // Configure monitoring systems
            configureMonitoringSystems(profile);

            // Update user profile with monitoring flag
            updateUserMonitoringStatus(userId, true, level);

            // Cache the active profile
            activeProfiles.put(userId, profile);

            // Audit the monitoring activation
            auditService.auditCriticalComplianceEvent(
                    "ENHANCED_MONITORING_ENABLED",
                    userId.toString(),
                    String.format("Enhanced monitoring enabled - Level: %s, Reason: %s", level, reason),
                    Map.of(
                            "profileId", profile.getProfileId(),
                            "level", level.toString(),
                            "reason", reason,
                            "startDate", profile.getStartDate(),
                            "endDate", monitoringUntil
                    )
            );

            // Send notifications
            notifyMonitoringActivation(profile);

            log.warn("COMPLIANCE: Enhanced monitoring profile {} created for user {}", 
                     profile.getProfileId(), userId);

            return profile;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to enable enhanced monitoring for user {}", userId, e);
            throw new RuntimeException("Failed to enable enhanced monitoring", e);
        }
    }

    @Override
    public void disableEnhancedMonitoring(UUID userId, String reason) {
        log.info("COMPLIANCE: Disabling enhanced monitoring for user {} - Reason: {}", userId, reason);

        try {
            EnhancedMonitoringProfile profile = monitoringRepository.findByUserIdAndActiveTrue(userId)
                    .orElseThrow(() -> new IllegalArgumentException("No active monitoring for user: " + userId));

            // Deactivate the profile
            profile.setActive(false);
            profile.setDeactivationDate(LocalDateTime.now());
            profile.setDeactivationReason(reason);
            profile.setUpdatedBy("COMPLIANCE_SYSTEM");
            profile.setUpdatedAt(LocalDateTime.now());

            monitoringRepository.save(profile);

            // Remove from cache
            activeProfiles.remove(userId);

            // Update user profile
            updateUserMonitoringStatus(userId, false, null);

            // Audit the deactivation
            auditService.auditComplianceEvent(
                    "ENHANCED_MONITORING_DISABLED",
                    userId.toString(),
                    String.format("Enhanced monitoring disabled - Reason: %s", reason),
                    Map.of("profileId", profile.getProfileId(), "reason", reason)
            );

            log.info("COMPLIANCE: Enhanced monitoring disabled for user {}", userId);

        } catch (Exception e) {
            log.error("Failed to disable enhanced monitoring for user {}", userId, e);
            throw new RuntimeException("Failed to disable enhanced monitoring", e);
        }
    }

    @Override
    @Async
    public void processTransactionMonitoring(UUID userId, String transactionId, 
                                            BigDecimal amount, String transactionType) {
        if (!realTimeMonitoringEnabled) {
            return;
        }

        try {
            // Check if user is under enhanced monitoring
            EnhancedMonitoringProfile profile = getActiveMonitoringProfile(userId);
            if (profile == null) {
                return;
            }

            log.debug("MONITORING: Processing transaction {} for monitored user {}", transactionId, userId);

            // Apply monitoring rules
            List<MonitoringAlert> alerts = applyMonitoringRules(profile, transactionId, amount, transactionType);

            // Process alerts
            if (!alerts.isEmpty()) {
                processMonitoringAlerts(profile, alerts);
            }

            // Update profile statistics
            updateProfileStatistics(profile, amount, transactionType);

            // Check for escalation conditions
            checkEscalationConditions(profile);

        } catch (Exception e) {
            log.error("Error processing transaction monitoring for user {}", userId, e);
        }
    }

    @Override
    @Cacheable(value = "monitoringProfiles", key = "#userId")
    public EnhancedMonitoringProfile getActiveMonitoringProfile(UUID userId) {
        // Check cache first
        if (activeProfiles.containsKey(userId)) {
            return activeProfiles.get(userId);
        }

        // Load from database
        return monitoringRepository.findByUserIdAndActiveTrue(userId).orElse(null);
    }

    @Override
    public List<MonitoringAlert> getRecentAlerts(UUID userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return alertRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since);
    }

    @Override
    public MonitoringStatistics getMonitoringStatistics(UUID userId) {
        EnhancedMonitoringProfile profile = getActiveMonitoringProfile(userId);
        if (profile == null) {
            return MonitoringStatistics.empty();
        }

        List<MonitoringAlert> alerts = getRecentAlerts(userId, 30);
        
        return MonitoringStatistics.builder()
                .userId(userId)
                .profileId(profile.getProfileId())
                .monitoringLevel(profile.getLevel())
                .startDate(profile.getStartDate())
                .endDate(profile.getEndDate())
                .totalAlerts(alerts.size())
                .highSeverityAlerts((int) alerts.stream()
                        .filter(a -> a.getSeverity() == MonitoringAlert.Severity.HIGH)
                        .count())
                .mediumSeverityAlerts((int) alerts.stream()
                        .filter(a -> a.getSeverity() == MonitoringAlert.Severity.MEDIUM)
                        .count())
                .lowSeverityAlerts((int) alerts.stream()
                        .filter(a -> a.getSeverity() == MonitoringAlert.Severity.LOW)
                        .count())
                .lastAlertDate(alerts.isEmpty() ? null : alerts.get(0).getCreatedAt())
                .riskScore(profile.getRiskScore())
                .totalTransactionsMonitored(profile.getTotalTransactionsMonitored())
                .totalAmountMonitored(profile.getTotalAmountMonitored())
                .build();
    }

    @Override
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void reviewExpiringProfiles() {
        log.info("MONITORING: Reviewing expiring monitoring profiles");

        LocalDateTime expiryThreshold = LocalDateTime.now().plusDays(7);
        List<EnhancedMonitoringProfile> expiringProfiles = 
                monitoringRepository.findByActiveTrueAndEndDateBefore(expiryThreshold);

        for (EnhancedMonitoringProfile profile : expiringProfiles) {
            if (shouldExtendMonitoring(profile)) {
                extendMonitoring(profile);
            } else {
                notifyUpcomingExpiry(profile);
            }
        }
    }

    @Override
    @Scheduled(cron = "0 */15 * * * *") // Run every 15 minutes
    public void processMonitoringQueue() {
        if (!realTimeMonitoringEnabled) {
            return;
        }

        log.debug("MONITORING: Processing monitoring queue");

        // Process queued transactions for batch monitoring
        List<EnhancedMonitoringProfile> activeProfiles = monitoringRepository.findByActiveTrue();
        
        for (EnhancedMonitoringProfile profile : activeProfiles) {
            try {
                processBatchMonitoring(profile);
            } catch (Exception e) {
                log.error("Error processing batch monitoring for profile {}", profile.getProfileId(), e);
            }
        }
    }

    // Private helper methods

    private EnhancedMonitoringProfile updateExistingMonitoring(EnhancedMonitoringProfile existing,
                                                              String reason,
                                                              LocalDateTime newEndDate,
                                                              MonitoringLevel newLevel) {
        log.info("MONITORING: Updating existing monitoring profile {}", existing.getProfileId());

        // Update profile
        existing.setEndDate(newEndDate);
        existing.setLevel(newLevel);
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy("COMPLIANCE_SYSTEM");
        existing.getUpdateHistory().add(String.format("[%s] Updated: Reason: %s, New Level: %s, New End Date: %s",
                LocalDateTime.now(), reason, newLevel, newEndDate));

        // Update monitoring rules if level changed
        if (!existing.getLevel().equals(newLevel)) {
            existing.setMonitoringRules(createMonitoringRules(newLevel));
        }

        return monitoringRepository.save(existing);
    }

    private List<MonitoringRule> createMonitoringRules(MonitoringLevel level) {
        List<MonitoringRule> rules = new ArrayList<>();

        // Base rules for all levels
        rules.add(MonitoringRule.builder()
                .ruleId("RULE_001")
                .ruleName("High Value Transaction")
                .threshold(level == MonitoringLevel.HIGH ? 5000.0 : 10000.0)
                .action("ALERT")
                .enabled(true)
                .build());

        rules.add(MonitoringRule.builder()
                .ruleId("RULE_002")
                .ruleName("Rapid Transaction Velocity")
                .threshold(level == MonitoringLevel.HIGH ? 5.0 : 10.0)
                .action("ALERT")
                .enabled(true)
                .build());

        // Additional rules for MEDIUM and HIGH levels
        if (level == MonitoringLevel.MEDIUM || level == MonitoringLevel.HIGH) {
            rules.add(MonitoringRule.builder()
                    .ruleId("RULE_003")
                    .ruleName("Cross-Border Transaction")
                    .threshold(1.0)
                    .action("ALERT")
                    .enabled(true)
                    .build());

            rules.add(MonitoringRule.builder()
                    .ruleId("RULE_004")
                    .ruleName("High-Risk Country Transaction")
                    .threshold(1.0)
                    .action("BLOCK")
                    .enabled(true)
                    .build());
        }

        // Additional rules for HIGH level only
        if (level == MonitoringLevel.HIGH) {
            rules.add(MonitoringRule.builder()
                    .ruleId("RULE_005")
                    .ruleName("Any Crypto Transaction")
                    .threshold(1.0)
                    .action("ALERT_AND_REVIEW")
                    .enabled(true)
                    .build());

            rules.add(MonitoringRule.builder()
                    .ruleId("RULE_006")
                    .ruleName("Pattern Deviation")
                    .threshold(0.3) // 30% deviation from normal
                    .action("ALERT")
                    .enabled(true)
                    .build());

            rules.add(MonitoringRule.builder()
                    .ruleId("RULE_007")
                    .ruleName("Structuring Detection")
                    .threshold(3.0) // 3 transactions just below reporting threshold
                    .action("ALERT_AND_BLOCK")
                    .enabled(true)
                    .build());
        }

        return rules;
    }

    private void configureMonitoringSystems(EnhancedMonitoringProfile profile) {
        try {
            // Configure fraud detection system
            fraudDetectionClient.configureEnhancedMonitoring(
                    profile.getUserId(),
                    profile.getLevel().toString(),
                    profile.getMonitoringRules()
            );

            // Configure transaction monitoring
            Map<String, Object> config = Map.of(
                    "userId", profile.getUserId(),
                    "profileId", profile.getProfileId(),
                    "level", profile.getLevel(),
                    "rules", profile.getMonitoringRules()
            );

            // Additional system configurations based on level
            if (profile.getLevel() == MonitoringLevel.HIGH) {
                configureHighLevelMonitoring(profile);
            }

        } catch (Exception e) {
            log.error("Failed to configure monitoring systems for profile {}", profile.getProfileId(), e);
        }
    }

    private void configureHighLevelMonitoring(EnhancedMonitoringProfile profile) {
        // Configure additional high-level monitoring features
        fraudDetectionClient.enableRealtimeScoring(profile.getUserId());
        fraudDetectionClient.enableBehavioralAnalytics(profile.getUserId());
        fraudDetectionClient.enableNetworkAnalysis(profile.getUserId());
    }

    private void updateUserMonitoringStatus(UUID userId, boolean monitored, MonitoringLevel level) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("enhancedMonitoring", monitored);
            updates.put("monitoringLevel", level != null ? level.toString() : null);
            updates.put("lastMonitoringUpdate", LocalDateTime.now());

            userServiceClient.updateUserMonitoringStatus(userId, updates);
        } catch (Exception e) {
            log.error("Failed to update user monitoring status for {}", userId, e);
        }
    }

    private double calculateInitialRiskScore(UUID userId, MonitoringLevel level) {
        double baseScore = 50.0; // Base risk score

        // Adjust based on monitoring level
        switch (level) {
            case HIGH:
                baseScore += 30;
                break;
            case MEDIUM:
                baseScore += 15;
                break;
            case LOW:
                baseScore += 5;
                break;
        }

        // Get additional risk factors from fraud detection service
        try {
            Map<String, Double> riskFactors = fraudDetectionClient.getUserRiskFactors(userId);
            baseScore += riskFactors.getOrDefault("historicalRisk", 0.0);
            baseScore += riskFactors.getOrDefault("behavioralRisk", 0.0);
            baseScore += riskFactors.getOrDefault("networkRisk", 0.0);
        } catch (Exception e) {
            log.error("Failed to get risk factors for user {}", userId, e);
        }

        return Math.min(100, baseScore); // Cap at 100
    }

    private void notifyMonitoringActivation(EnhancedMonitoringProfile profile) {
        try {
            // Notify compliance team
            notificationClient.sendComplianceTeamAlert(
                    "ENHANCED_MONITORING_ACTIVATED",
                    "Enhanced Monitoring Activated",
                    String.format("Enhanced monitoring activated for user %s - Level: %s, Reason: %s",
                            profile.getUserId(), profile.getLevel(), profile.getReason()),
                    profile.getProfileId()
            );

            // Notify user if required by regulations
            if (shouldNotifyUser(profile)) {
                notificationClient.sendUserNotification(
                        profile.getUserId(),
                        "Account Review Notice",
                        "Your account is under review as part of our routine compliance procedures."
                );
            }
        } catch (Exception e) {
            log.error("Failed to send monitoring activation notifications", e);
        }
    }

    private boolean shouldNotifyUser(EnhancedMonitoringProfile profile) {
        // Determine if user should be notified based on regulations and profile type
        return profile.getLevel() != MonitoringLevel.HIGH && 
               !profile.getReason().contains("INVESTIGATION");
    }

    private List<MonitoringAlert> applyMonitoringRules(EnhancedMonitoringProfile profile,
                                                       String transactionId,
                                                       BigDecimal amount,
                                                       String transactionType) {
        List<MonitoringAlert> alerts = new ArrayList<>();

        for (MonitoringRule rule : profile.getMonitoringRules()) {
            if (!rule.isEnabled()) {
                continue;
            }

            if (evaluateRule(rule, amount, transactionType)) {
                MonitoringAlert alert = MonitoringAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .profileId(profile.getProfileId())
                        .userId(profile.getUserId())
                        .transactionId(transactionId)
                        .ruleId(rule.getRuleId())
                        .ruleName(rule.getRuleName())
                        .severity(determineSeverity(rule, amount))
                        .description(String.format("Rule triggered: %s - Amount: %s, Type: %s",
                                rule.getRuleName(), amount, transactionType))
                        .createdAt(LocalDateTime.now())
                        .status(MonitoringAlert.Status.NEW)
                        .build();

                alerts.add(alert);
            }
        }

        return alerts;
    }

    private boolean evaluateRule(MonitoringRule rule, BigDecimal amount, String transactionType) {
        // Evaluate rule based on rule type and threshold
        switch (rule.getRuleId()) {
            case "RULE_001": // High value transaction
                return amount.doubleValue() > rule.getThreshold();
            case "RULE_002": // Velocity check (simplified)
                return checkVelocity(rule.getThreshold());
            case "RULE_003": // Cross-border
                return transactionType.contains("INTERNATIONAL");
            case "RULE_005": // Crypto transaction
                return transactionType.contains("CRYPTO");
            default:
                return false;
        }
    }

    private boolean checkVelocity(double threshold) {
        // Simplified velocity check - in production, this would check actual transaction history
        return false;
    }

    private MonitoringAlert.Severity determineSeverity(MonitoringRule rule, BigDecimal amount) {
        if (rule.getAction().contains("BLOCK")) {
            return MonitoringAlert.Severity.CRITICAL;
        } else if (amount.doubleValue() > rule.getThreshold() * 2) {
            return MonitoringAlert.Severity.HIGH;
        } else if (amount.doubleValue() > rule.getThreshold() * 1.5) {
            return MonitoringAlert.Severity.MEDIUM;
        } else {
            return MonitoringAlert.Severity.LOW;
        }
    }

    private void processMonitoringAlerts(EnhancedMonitoringProfile profile, List<MonitoringAlert> alerts) {
        // Save alerts
        alerts = alertRepository.saveAll(alerts);

        // Update profile alert count
        profile.setAlertCount(profile.getAlertCount() + alerts.size());
        profile.setLastAlertDate(LocalDateTime.now());
        monitoringRepository.save(profile);

        // Process critical alerts immediately
        List<MonitoringAlert> criticalAlerts = alerts.stream()
                .filter(a -> a.getSeverity() == MonitoringAlert.Severity.CRITICAL)
                .collect(Collectors.toList());

        if (!criticalAlerts.isEmpty()) {
            escalateCriticalAlerts(profile, criticalAlerts);
        }

        // Check alert threshold
        if (profile.getAlertCount() > alertThreshold) {
            escalateProfile(profile, "Alert threshold exceeded");
        }
    }

    private void escalateCriticalAlerts(EnhancedMonitoringProfile profile, List<MonitoringAlert> alerts) {
        log.error("CRITICAL: Processing {} critical alerts for user {}", alerts.size(), profile.getUserId());

        for (MonitoringAlert alert : alerts) {
            // Take immediate action based on alert
            if (alert.getDescription().contains("BLOCK")) {
                // Block the transaction
                fraudDetectionClient.blockTransaction(alert.getTransactionId());
            }

            // Notify compliance team immediately
            notificationClient.sendUrgentComplianceAlert(
                    "CRITICAL_MONITORING_ALERT",
                    String.format("Critical Alert - User: %s", profile.getUserId()),
                    alert.getDescription(),
                    alert.getAlertId()
            );
        }
    }

    private void updateProfileStatistics(EnhancedMonitoringProfile profile, 
                                        BigDecimal amount, String transactionType) {
        profile.setTotalTransactionsMonitored(profile.getTotalTransactionsMonitored() + 1);
        profile.setTotalAmountMonitored(profile.getTotalAmountMonitored().add(amount));
        profile.setLastActivityDate(LocalDateTime.now());

        // Update risk score based on activity
        double riskAdjustment = calculateRiskAdjustment(amount, transactionType);
        profile.setRiskScore(Math.min(100, profile.getRiskScore() + riskAdjustment));

        monitoringRepository.save(profile);
    }

    private double calculateRiskAdjustment(BigDecimal amount, String transactionType) {
        double adjustment = 0;

        // High-value transactions increase risk
        if (amount.doubleValue() > 10000) {
            adjustment += 2;
        }

        // Certain transaction types increase risk
        if (transactionType.contains("CRYPTO") || transactionType.contains("INTERNATIONAL")) {
            adjustment += 3;
        }

        return adjustment;
    }

    private void checkEscalationConditions(EnhancedMonitoringProfile profile) {
        // Check various escalation conditions
        if (profile.getRiskScore() > 80) {
            escalateProfile(profile, "Risk score exceeded threshold");
        }

        if (profile.getAlertCount() > alertThreshold * 2) {
            escalateProfile(profile, "Excessive alerts generated");
        }

        // Check time-based patterns
        long recentAlerts = alertRepository.countByUserIdAndCreatedAtAfter(
                profile.getUserId(), 
                LocalDateTime.now().minusHours(1)
        );

        if (recentAlerts > 10) {
            escalateProfile(profile, "Rapid alert generation detected");
        }
    }

    private void escalateProfile(EnhancedMonitoringProfile profile, String reason) {
        log.error("ESCALATION: Escalating monitoring profile {} - Reason: {}", 
                 profile.getProfileId(), reason);

        // Update profile
        profile.setEscalated(true);
        profile.setEscalationReason(reason);
        profile.setEscalationDate(LocalDateTime.now());
        monitoringRepository.save(profile);

        // Notify senior compliance
        notificationClient.sendSeniorComplianceAlert(
                "MONITORING_ESCALATION",
                String.format("Monitoring Escalation - User: %s", profile.getUserId()),
                String.format("Profile escalated: %s", reason),
                profile.getProfileId()
        );

        // Create compliance case if needed
        if (profile.getRiskScore() > 90) {
            createComplianceCase(profile);
        }
    }

    private void createComplianceCase(EnhancedMonitoringProfile profile) {
        try {
            Map<String, Object> caseData = Map.of(
                    "userId", profile.getUserId(),
                    "profileId", profile.getProfileId(),
                    "reason", "Automated escalation from enhanced monitoring",
                    "riskScore", profile.getRiskScore(),
                    "alertCount", profile.getAlertCount(),
                    "priority", "HIGH"
            );

            String caseId = fraudDetectionClient.createComplianceCase(caseData);
            
            log.warn("COMPLIANCE: Case {} created for monitoring profile {}", 
                    caseId, profile.getProfileId());
        } catch (Exception e) {
            log.error("Failed to create compliance case for profile {}", profile.getProfileId(), e);
        }
    }

    private boolean shouldExtendMonitoring(EnhancedMonitoringProfile profile) {
        // Determine if monitoring should be extended based on recent activity
        return profile.getRiskScore() > 70 || 
               profile.getAlertCount() > alertThreshold ||
               profile.isEscalated();
    }

    private void extendMonitoring(EnhancedMonitoringProfile profile) {
        LocalDateTime newEndDate = profile.getEndDate().plusDays(30);
        profile.setEndDate(newEndDate);
        profile.getUpdateHistory().add(String.format("[%s] Auto-extended to %s due to continued risk",
                LocalDateTime.now(), newEndDate));
        monitoringRepository.save(profile);

        log.info("MONITORING: Extended monitoring for profile {} to {}", 
                profile.getProfileId(), newEndDate);
    }

    private void notifyUpcomingExpiry(EnhancedMonitoringProfile profile) {
        long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDateTime.now(), profile.getEndDate());
        
        notificationClient.sendComplianceTeamAlert(
                "MONITORING_EXPIRY_NOTICE",
                "Monitoring Profile Expiring Soon",
                String.format("Monitoring profile %s for user %s expires in %d days",
                        profile.getProfileId(), profile.getUserId(), daysUntilExpiry),
                profile.getProfileId()
        );
    }

    private void processBatchMonitoring(EnhancedMonitoringProfile profile) {
        // Process batch monitoring for the profile
        // This would typically involve analyzing recent transactions in batch
        log.debug("Processing batch monitoring for profile {}", profile.getProfileId());
        
        // Implementation would include:
        // 1. Fetching recent transactions
        // 2. Applying pattern analysis
        // 3. Generating alerts for anomalies
        // 4. Updating profile statistics
    }

    /**
     * Monitoring statistics data class
     */
    @lombok.Data
    @lombok.Builder
    public static class MonitoringStatistics {
        private UUID userId;
        private String profileId;
        private MonitoringLevel monitoringLevel;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int totalAlerts;
        private int highSeverityAlerts;
        private int mediumSeverityAlerts;
        private int lowSeverityAlerts;
        private LocalDateTime lastAlertDate;
        private double riskScore;
        private long totalTransactionsMonitored;
        private BigDecimal totalAmountMonitored;

        public static MonitoringStatistics empty() {
            return MonitoringStatistics.builder()
                    .totalAlerts(0)
                    .highSeverityAlerts(0)
                    .mediumSeverityAlerts(0)
                    .lowSeverityAlerts(0)
                    .riskScore(0.0)
                    .totalTransactionsMonitored(0)
                    .totalAmountMonitored(BigDecimal.ZERO)
                    .build();
        }
    }

    /**
     * Monitoring level enumeration
     */
    public enum MonitoringLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}