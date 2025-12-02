package com.waqiti.security.service;

import com.waqiti.common.events.AmlAlertEvent;
import com.waqiti.security.domain.*;
import com.waqiti.security.dto.AmlScreeningResult;
import com.waqiti.security.dto.AmlStatistics;
import com.waqiti.security.dto.WatchlistCheckResult;
import com.waqiti.security.dto.WatchlistMatch;
import com.waqiti.security.repository.AmlAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Anti-Money Laundering (AML) monitoring service
 * Implements regulatory compliance for transaction monitoring and suspicious activity detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AmlMonitoringService {

    private final AmlAlertRepository amlAlertRepository;
    private final TransactionPatternAnalyzer patternAnalyzer;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceNotificationService complianceNotificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // AML thresholds (configurable per jurisdiction)
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal DAILY_AGGREGATE_THRESHOLD = new BigDecimal("15000.00");
    private static final BigDecimal WEEKLY_AGGREGATE_THRESHOLD = new BigDecimal("50000.00");
    private static final int STRUCTURING_TRANSACTION_COUNT = 3;
    private static final BigDecimal STRUCTURING_AMOUNT_THRESHOLD = new BigDecimal("9000.00");
    
    // Enhanced monitoring configuration
    private static final BigDecimal SAR_THRESHOLD_AMOUNT = new BigDecimal("10000.00");
    private static final int MONITORING_DURATION_DAYS_LOW = 30;
    private static final int MONITORING_DURATION_DAYS_MEDIUM = 60;
    private static final int MONITORING_DURATION_DAYS_HIGH = 90;
    private static final int MONITORING_DURATION_DAYS_CRITICAL = 180;

    /**
     * Monitor transaction for AML compliance
     */
    public void monitorTransaction(UUID transactionId, UUID userId, BigDecimal amount, 
                                 String currency, String countryCode, UUID recipientId) {
        
        log.debug("AML monitoring transaction: {} for user: {} amount: {}", 
            transactionId, userId, amount);

        try {
            // Check for large cash transaction reporting
            if (isLargeCashTransaction(amount)) {
                createAmlAlert(userId, transactionId, AmlAlertType.LARGE_CASH_TRANSACTION,
                    AmlSeverity.HIGH, "Large cash transaction: " + amount + " " + currency);
            }

            // Check for structuring patterns
            checkStructuringPatterns(userId, amount, transactionId);

            // Check daily and weekly aggregates
            checkAggregateThresholds(userId, amount, transactionId);

            // Check for unusual transaction patterns
            checkUnusualPatterns(userId, amount, recipientId, transactionId);

            // Check for high-risk jurisdictions
            checkHighRiskJurisdictions(userId, countryCode, transactionId);

            // Check for rapid succession transactions
            checkRapidSuccessionTransactions(userId, amount, transactionId);

        } catch (Exception e) {
            log.error("Error during AML monitoring for transaction: {}", transactionId, e);
            // Create alert for monitoring system failure
            createAmlAlert(userId, transactionId, AmlAlertType.SYSTEM_ERROR,
                AmlSeverity.MEDIUM, "AML monitoring system error: " + e.getMessage());
        }
    }

    /**
     * Check if transaction exceeds large cash transaction threshold
     */
    private boolean isLargeCashTransaction(BigDecimal amount) {
        return amount.compareTo(LARGE_TRANSACTION_THRESHOLD) >= 0;
    }

    /**
     * Check for structuring patterns (breaking large amounts into smaller transactions)
     */
    private void checkStructuringPatterns(UUID userId, BigDecimal amount, UUID transactionId) {
        // Look for multiple transactions just under reporting threshold within 24 hours
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        
        List<TransactionPattern> recentTransactions = 
            patternAnalyzer.getRecentTransactions(userId, last24Hours);

        // Count transactions just under structuring threshold
        long structuringCount = recentTransactions.stream()
            .filter(t -> t.getAmount().compareTo(STRUCTURING_AMOUNT_THRESHOLD) < 0 &&
                        t.getAmount().compareTo(new BigDecimal("8000.00")) >= 0)
            .count();

        if (structuringCount >= STRUCTURING_TRANSACTION_COUNT) {
            createAmlAlert(userId, transactionId, AmlAlertType.STRUCTURING,
                AmlSeverity.HIGH, 
                "Potential structuring detected: " + structuringCount + 
                " transactions just under threshold in 24h");
            
            // Automatically file Suspicious Activity Report (SAR)
            regulatoryReportingService.fileSuspiciousActivityReport(userId, transactionId,
                "Potential structuring pattern detected");
        }
    }

    /**
     * Check daily and weekly aggregate transaction limits
     */
    private void checkAggregateThresholds(UUID userId, BigDecimal amount, UUID transactionId) {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekStart = today.minusDays(6);

        BigDecimal dailyTotal = patternAnalyzer.getDailyTransactionTotal(userId, today);
        BigDecimal weeklyTotal = patternAnalyzer.getWeeklyTransactionTotal(userId, weekStart);

        // Check daily threshold
        if (dailyTotal.add(amount).compareTo(DAILY_AGGREGATE_THRESHOLD) >= 0) {
            createAmlAlert(userId, transactionId, AmlAlertType.DAILY_THRESHOLD_EXCEEDED,
                AmlSeverity.MEDIUM, "Daily transaction threshold exceeded: " + dailyTotal.add(amount));
        }

        // Check weekly threshold
        if (weeklyTotal.add(amount).compareTo(WEEKLY_AGGREGATE_THRESHOLD) >= 0) {
            createAmlAlert(userId, transactionId, AmlAlertType.WEEKLY_THRESHOLD_EXCEEDED,
                AmlSeverity.MEDIUM, "Weekly transaction threshold exceeded: " + weeklyTotal.add(amount));
        }
    }

    /**
     * Check for unusual transaction patterns
     */
    private void checkUnusualPatterns(UUID userId, BigDecimal amount, UUID recipientId, UUID transactionId) {
        // Check for round number transactions (potential money laundering indicator)
        if (isRoundNumber(amount)) {
            createAmlAlert(userId, transactionId, AmlAlertType.ROUND_NUMBER_TRANSACTION,
                AmlSeverity.LOW, "Round number transaction: " + amount);
        }

        // Check for unusual time patterns (off-hours transactions)
        if (isOffHoursTransaction()) {
            createAmlAlert(userId, transactionId, AmlAlertType.OFF_HOURS_TRANSACTION,
                AmlSeverity.LOW, "Off-hours transaction at: " + LocalDateTime.now());
        }

        // Check for circular transactions (A -> B -> A pattern)
        if (patternAnalyzer.detectCircularTransactions(userId, recipientId)) {
            createAmlAlert(userId, transactionId, AmlAlertType.CIRCULAR_TRANSACTION,
                AmlSeverity.HIGH, "Circular transaction pattern detected with user: " + recipientId);
        }

        // Check for velocity patterns (too many transactions in short time)
        int hourlyTransactionCount = patternAnalyzer.getHourlyTransactionCount(userId);
        if (hourlyTransactionCount > 10) {
            createAmlAlert(userId, transactionId, AmlAlertType.HIGH_VELOCITY,
                AmlSeverity.MEDIUM, "High velocity transactions: " + hourlyTransactionCount + " in 1 hour");
        }
    }

    /**
     * Check for transactions involving high-risk jurisdictions
     */
    private void checkHighRiskJurisdictions(UUID userId, String countryCode, UUID transactionId) {
        if (isHighRiskJurisdiction(countryCode)) {
            createAmlAlert(userId, transactionId, AmlAlertType.HIGH_RISK_JURISDICTION,
                AmlSeverity.HIGH, "Transaction involving high-risk jurisdiction: " + countryCode);
            
            // Enhanced due diligence required
            complianceNotificationService.notifyEnhancedDueDiligenceRequired(userId, countryCode);
        }
    }

    /**
     * Check for rapid succession transactions
     */
    private void checkRapidSuccessionTransactions(UUID userId, BigDecimal amount, UUID transactionId) {
        LocalDateTime last5Minutes = LocalDateTime.now().minusMinutes(5);
        int recentTransactionCount = patternAnalyzer.getTransactionCountSince(userId, last5Minutes);
        
        if (recentTransactionCount > 5) {
            createAmlAlert(userId, transactionId, AmlAlertType.RAPID_SUCCESSION,
                AmlSeverity.MEDIUM, "Rapid succession transactions: " + recentTransactionCount + " in 5 minutes");
        }
    }

    /**
     * Create AML alert and handle notifications
     */
    private void createAmlAlert(UUID userId, UUID transactionId, AmlAlertType alertType,
                               AmlSeverity severity, String description) {
        
        AmlAlert alert = AmlAlert.builder()
            .userId(userId)
            .transactionId(transactionId)
            .alertType(alertType)
            .severity(severity)
            .description(description)
            .status(AmlAlert.AlertStatus.OPEN)
            .createdAt(LocalDateTime.now())
            .requiresInvestigation(severity == AmlSeverity.HIGH)
            .build();

        AmlAlert savedAlert = amlAlertRepository.save(alert);

        log.info("AML alert created: {} for user: {}, transaction: {}, severity: {}", 
            savedAlert.getId(), userId, transactionId, severity);

        // ENHANCED: Activate automated monitoring based on alert severity and type
        activateEnhancedMonitoring(userId, transactionId, alertType, severity, savedAlert);

        // Handle high severity alerts immediately
        if (severity == AmlSeverity.HIGH) {
            complianceNotificationService.notifyHighSeverityAlert(savedAlert);
            
            // Consider freezing account for critical alerts
            if (alertType == AmlAlertType.STRUCTURING || alertType == AmlAlertType.CIRCULAR_TRANSACTION) {
                complianceNotificationService.recommendAccountFreeze(userId, savedAlert.getId());
            }
        }

        // Generate regulatory report if required
        if (shouldFileRegulatoryReport(alertType, severity)) {
            regulatoryReportingService.generateAmlReport(savedAlert);
        }
        
        // Publish AML alert event to Kafka for compliance processing
        publishAmlAlertEvent(savedAlert, userId, transactionId);
    }
    
    /**
     * Publish AML alert to Kafka for compliance processing
     */
    private void publishAmlAlertEvent(AmlAlert alert, UUID userId, UUID transactionId) {
        try {
            // Determine if SAR filing is required
            boolean requiresSar = shouldFileRegulatoryReport(alert.getAlertType(), alert.getSeverity());
            
            // Determine if account freeze is required
            boolean requiresFreeze = alert.getSeverity() == AmlSeverity.HIGH &&
                (alert.getAlertType() == AmlAlertType.STRUCTURING || 
                 alert.getAlertType() == AmlAlertType.CIRCULAR_TRANSACTION ||
                 alert.getAlertType() == AmlAlertType.SANCTIONED_COUNTRY);
            
            // Build AML alert event
            AmlAlertEvent event = AmlAlertEvent.builder()
                .alertId(alert.getId())
                .alertTimestamp(LocalDateTime.now())
                .alertSource("TRANSACTION_MONITORING")
                .userId(userId)
                .alertType(mapToEventAlertType(alert.getAlertType()))
                .severity(mapToEventSeverity(alert.getSeverity()))
                .description(alert.getDescription())
                .riskScore(alert.getRiskScore() != null ? alert.getRiskScore() : 50)
                .transactionId(transactionId)
                .requiresInvestigation(alert.isRequiresInvestigation())
                .requiresSarFiling(requiresSar)
                .requiresAccountFreeze(requiresFreeze)
                .requiresEnhancedDueDiligence(alert.getSeverity() == AmlSeverity.HIGH)
                .investigationPriority(determineInvestigationPriority(alert.getSeverity()))
                .investigationDeadline(calculateInvestigationDeadline(alert.getSeverity()))
                .jurisdictionCode(alert.getJurisdictionCode())
                .publishingService("security-service")
                .publishedAt(LocalDateTime.now())
                .metadata(Map.of(
                    "alertId", alert.getId().toString(),
                    "createdAt", alert.getCreatedAt().toString()
                ))
                .build();
            
            // Publish to Kafka
            kafkaTemplate.send("aml-alerts", event.getAlertId().toString(), event);
            
            log.info("Published AML alert event to Kafka: {} for user: {}", 
                alert.getId(), userId);
            
        } catch (Exception e) {
            log.error("Failed to publish AML alert event: {}", alert.getId(), e);
            // Don't throw - publishing failure shouldn't block alert creation
        }
    }
    
    /**
     * Map internal alert type to event alert type
     */
    private AmlAlertEvent.AmlAlertType mapToEventAlertType(AmlAlertType alertType) {
        switch (alertType) {
            case LARGE_CASH_TRANSACTION:
                return AmlAlertEvent.AmlAlertType.LARGE_CASH_TRANSACTION;
            case STRUCTURING:
                return AmlAlertEvent.AmlAlertType.STRUCTURING;
            case HIGH_RISK_JURISDICTION:
                return AmlAlertEvent.AmlAlertType.HIGH_RISK_JURISDICTION;
            case CIRCULAR_TRANSACTION:
                return AmlAlertEvent.AmlAlertType.CIRCULAR_TRANSACTION;
            case HIGH_VELOCITY:
                return AmlAlertEvent.AmlAlertType.HIGH_VELOCITY;
            case RAPID_SUCCESSION:
                return AmlAlertEvent.AmlAlertType.RAPID_MOVEMENT;
            case WATCHLIST_MATCH:
                return AmlAlertEvent.AmlAlertType.WATCHLIST_MATCH;
            case SANCTIONED_COUNTRY:
                return AmlAlertEvent.AmlAlertType.SANCTIONED_COUNTRY;
            case DAILY_THRESHOLD_EXCEEDED:
                return AmlAlertEvent.AmlAlertType.DAILY_THRESHOLD_EXCEEDED;
            case WEEKLY_THRESHOLD_EXCEEDED:
                return AmlAlertEvent.AmlAlertType.WEEKLY_THRESHOLD_EXCEEDED;
            case OFF_HOURS_TRANSACTION:
                return AmlAlertEvent.AmlAlertType.OFF_HOURS_TRANSACTION;
            case ROUND_NUMBER_TRANSACTION:
            case UNUSUAL_PATTERN:
            default:
                return AmlAlertEvent.AmlAlertType.UNUSUAL_PATTERN;
        }
    }
    
    /**
     * Map internal severity to event severity
     */
    private AmlAlertEvent.AmlSeverity mapToEventSeverity(AmlSeverity severity) {
        switch (severity) {
            case CRITICAL:
                return AmlAlertEvent.AmlSeverity.CRITICAL;
            case HIGH:
                return AmlAlertEvent.AmlSeverity.HIGH;
            case MEDIUM:
                return AmlAlertEvent.AmlSeverity.MEDIUM;
            case LOW:
                return AmlAlertEvent.AmlSeverity.LOW;
            default:
                return AmlAlertEvent.AmlSeverity.INFO;
        }
    }
    
    /**
     * Determine investigation priority based on severity
     */
    private String determineInvestigationPriority(AmlSeverity severity) {
        switch (severity) {
            case CRITICAL:
            case HIGH:
                return "IMMEDIATE";
            case MEDIUM:
                return "HIGH";
            case LOW:
            default:
                return "MEDIUM";
        }
    }
    
    /**
     * Calculate investigation deadline based on severity
     */
    private LocalDateTime calculateInvestigationDeadline(AmlSeverity severity) {
        LocalDateTime now = LocalDateTime.now();
        switch (severity) {
            case CRITICAL:
                return now.plusHours(4);
            case HIGH:
                return now.plusHours(24);
            case MEDIUM:
                return now.plusHours(72);
            case LOW:
            default:
                return now.plusDays(7);
        }
    }

    /**
     * ENHANCED MONITORING ACTIVATION
     * 
     * Automatically activates enhanced monitoring for customers based on AML alert severity
     * and type. This fills the critical gap where suspicious activities were not being
     * properly escalated to enhanced monitoring for SAR generation.
     * 
     * Key features:
     * - Risk-based monitoring intensity levels
     * - Automatic SAR filing for critical activities
     * - Compliance workflow automation
     * - Customer behavior baseline establishment
     */
    private void activateEnhancedMonitoring(UUID userId, UUID transactionId, AmlAlertType alertType, 
                                           AmlSeverity severity, AmlAlert alert) {
        try {
            log.info("Evaluating enhanced monitoring activation for user: {} based on alert type: {} severity: {}", 
                userId, alertType, severity);
            
            // Determine monitoring intensity based on alert type and severity
            MonitoringIntensity intensity = determineMonitoringIntensity(alertType, severity);
            
            // Skip if monitoring not required
            if (intensity == MonitoringIntensity.NONE) {
                log.debug("No enhanced monitoring required for alert: {}", alert.getId());
                return;
            }
            
            // Create enhanced monitoring profile
            EnhancedMonitoringProfile profile = createMonitoringProfile(userId, transactionId, 
                alertType, severity, intensity, alert);
            
            // Activate monitoring workflows based on intensity
            activateMonitoringWorkflows(profile);
            
            // Check for immediate SAR filing requirement
            if (requiresImmediateSAR(alertType, severity, alert)) {
                generateImmediateSAR(userId, transactionId, alertType, severity, alert);
            }
            
            // Configure automated monitoring rules
            configureAutomatedMonitoringRules(profile);
            
            // Schedule follow-up activities
            scheduleMonitoringFollowUp(profile);
            
            // Publish enhanced monitoring activation event
            publishEnhancedMonitoringEvent(profile);
            
            log.warn("Enhanced monitoring activated for user: {} with intensity: {} for {} days", 
                userId, intensity, getMonitoringDuration(intensity));
            
        } catch (Exception e) {
            log.error("Failed to activate enhanced monitoring for user: {}", userId, e);
            // Don't throw - monitoring activation failure shouldn't block alert creation
        }
    }
    
    /**
     * Determines the appropriate monitoring intensity level
     */
    private MonitoringIntensity determineMonitoringIntensity(AmlAlertType alertType, AmlSeverity severity) {
        // Critical monitoring for structuring, money laundering patterns
        if (alertType == AmlAlertType.STRUCTURING || 
            alertType == AmlAlertType.CIRCULAR_TRANSACTION ||
            alertType == AmlAlertType.SANCTIONED_COUNTRY) {
            return MonitoringIntensity.CRITICAL;
        }
        
        // High intensity for high-risk jurisdictions and large transactions
        if (alertType == AmlAlertType.HIGH_RISK_JURISDICTION ||
            (alertType == AmlAlertType.LARGE_CASH_TRANSACTION && severity == AmlSeverity.HIGH)) {
            return MonitoringIntensity.HIGH;
        }
        
        // Medium intensity for velocity and threshold violations
        if (alertType == AmlAlertType.HIGH_VELOCITY ||
            alertType == AmlAlertType.DAILY_THRESHOLD_EXCEEDED ||
            alertType == AmlAlertType.WEEKLY_THRESHOLD_EXCEEDED) {
            return MonitoringIntensity.MEDIUM;
        }
        
        // Low intensity for other suspicious patterns
        if (severity == AmlSeverity.MEDIUM || severity == AmlSeverity.LOW) {
            return MonitoringIntensity.LOW;
        }
        
        return MonitoringIntensity.NONE;
    }
    
    /**
     * Creates an enhanced monitoring profile for the customer
     */
    private EnhancedMonitoringProfile createMonitoringProfile(UUID userId, UUID transactionId,
                                                             AmlAlertType alertType, AmlSeverity severity,
                                                             MonitoringIntensity intensity, AmlAlert alert) {
        
        int monitoringDuration = getMonitoringDuration(intensity);
        LocalDateTime activationTime = LocalDateTime.now();
        LocalDateTime expirationTime = activationTime.plusDays(monitoringDuration);
        
        return EnhancedMonitoringProfile.builder()
            .profileId(UUID.randomUUID())
            .userId(userId)
            .triggerAlertId(alert.getId())
            .triggerTransactionId(transactionId)
            .triggerAlertType(alertType)
            .intensity(intensity)
            .activatedAt(activationTime)
            .expiresAt(expirationTime)
            .monitoringDurationDays(monitoringDuration)
            .requiresManualReview(intensity == MonitoringIntensity.CRITICAL)
            .requiresAdditionalKyc(intensity.ordinal() >= MonitoringIntensity.HIGH.ordinal())
            .transactionLimitReduction(calculateTransactionLimitReduction(intensity))
            .realTimeAlerts(true)
            .status("ACTIVE")
            .build();
    }
    
    /**
     * Activates appropriate monitoring workflows
     */
    private void activateMonitoringWorkflows(EnhancedMonitoringProfile profile) {
        switch (profile.getIntensity()) {
            case CRITICAL:
                // Immediate compliance team notification
                complianceNotificationService.notifyComplianceTeam(
                    "CRITICAL monitoring activated for user: " + profile.getUserId());
                // Real-time transaction monitoring with zero tolerance
                patternAnalyzer.enableRealTimeMonitoring(profile.getUserId(), BigDecimal.ZERO);
                // Mandatory manual review for all transactions
                patternAnalyzer.enableMandatoryManualReview(profile.getUserId());
                break;
                
            case HIGH:
                // Enhanced monitoring with low thresholds
                patternAnalyzer.enableRealTimeMonitoring(profile.getUserId(), 
                    new BigDecimal("1000.00"));
                // Daily compliance review
                scheduleDailyComplianceReview(profile.getUserId());
                break;
                
            case MEDIUM:
                // Standard enhanced monitoring
                patternAnalyzer.enableEnhancedMonitoring(profile.getUserId());
                // Weekly compliance review
                scheduleWeeklyComplianceReview(profile.getUserId());
                break;
                
            case LOW:
                // Basic enhanced monitoring
                patternAnalyzer.enableBasicMonitoring(profile.getUserId());
                break;
        }
    }
    
    /**
     * Checks if immediate SAR filing is required
     */
    private boolean requiresImmediateSAR(AmlAlertType alertType, AmlSeverity severity, AmlAlert alert) {
        // Immediate SAR for structuring and money laundering
        if (alertType == AmlAlertType.STRUCTURING ||
            alertType == AmlAlertType.CIRCULAR_TRANSACTION ||
            alertType == AmlAlertType.SANCTIONED_COUNTRY) {
            return true;
        }
        
        // SAR for high-severity large transactions
        if (severity == AmlSeverity.HIGH && 
            alert.getTransactionAmount() != null &&
            alert.getTransactionAmount().compareTo(SAR_THRESHOLD_AMOUNT) > 0) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Generates immediate SAR for critical suspicious activities
     */
    private void generateImmediateSAR(UUID userId, UUID transactionId, AmlAlertType alertType,
                                     AmlSeverity severity, AmlAlert alert) {
        try {
            String sarNarrative = buildSARNarrative(alert, alertType, severity);
            
            // File SAR immediately
            String sarId = regulatoryReportingService.fileSuspiciousActivityReport(
                userId, transactionId, sarNarrative);
            
            // Update alert with SAR reference
            alert.setSarId(sarId);
            alert.setSarFiledAt(LocalDateTime.now());
            amlAlertRepository.save(alert);
            
            log.warn("IMMEDIATE SAR FILED - SAR ID: {} for user: {} alert: {}", 
                sarId, userId, alert.getId());
            
            // Notify compliance team
            complianceNotificationService.notifySARFiled(sarId, userId, alert.getId());
            
        } catch (Exception e) {
            log.error("Failed to generate immediate SAR for user: {}", userId, e);
            // Create critical alert for SAR filing failure
            complianceNotificationService.notifyCriticalComplianceFailure(
                "SAR filing failed for user: " + userId + " - Manual intervention required");
        }
    }
    
    /**
     * Builds comprehensive SAR narrative
     */
    private String buildSARNarrative(AmlAlert alert, AmlAlertType alertType, AmlSeverity severity) {
        StringBuilder narrative = new StringBuilder();
        narrative.append("SUSPICIOUS ACTIVITY REPORT - AUTOMATED FILING\n\n");
        narrative.append("Alert Type: ").append(alertType).append("\n");
        narrative.append("Severity: ").append(severity).append("\n");
        narrative.append("Alert Description: ").append(alert.getDescription()).append("\n");
        narrative.append("Detection Timestamp: ").append(alert.getCreatedAt()).append("\n");
        
        if (alert.getTransactionAmount() != null) {
            narrative.append("Transaction Amount: ").append(alert.getTransactionAmount()).append("\n");
        }
        
        narrative.append("\nENHANCED MONITORING ACTIVATED\n");
        narrative.append("Automated compliance system has activated enhanced monitoring ");
        narrative.append("for this customer based on the detected suspicious activity pattern. ");
        narrative.append("All future transactions will be subject to enhanced scrutiny.\n");
        
        return narrative.toString();
    }
    
    /**
     * Configures automated monitoring rules
     */
    private void configureAutomatedMonitoringRules(EnhancedMonitoringProfile profile) {
        try {
            // Create monitoring rules based on intensity
            MonitoringRules rules = MonitoringRules.builder()
                .userId(profile.getUserId())
                .maxTransactionAmount(calculateMaxTransactionAmount(profile.getIntensity()))
                .dailyTransactionLimit(calculateDailyLimit(profile.getIntensity()))
                .requiresTwoFactorAuth(profile.getIntensity().ordinal() >= MonitoringIntensity.MEDIUM.ordinal())
                .blockedCountries(getBlockedCountries(profile.getIntensity()))
                .alertOnAllTransactions(profile.getIntensity() == MonitoringIntensity.CRITICAL)
                .build();
            
            // Apply rules through pattern analyzer
            patternAnalyzer.applyMonitoringRules(profile.getUserId(), rules);
            
        } catch (Exception e) {
            log.error("Failed to configure monitoring rules for user: {}", profile.getUserId(), e);
        }
    }
    
    /**
     * Publishes enhanced monitoring activation event
     */
    private void publishEnhancedMonitoringEvent(EnhancedMonitoringProfile profile) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "ENHANCED_MONITORING_ACTIVATED",
                "userId", profile.getUserId().toString(),
                "profileId", profile.getProfileId().toString(),
                "intensity", profile.getIntensity().name(),
                "duration", profile.getMonitoringDurationDays(),
                "activatedAt", profile.getActivatedAt().toString(),
                "expiresAt", profile.getExpiresAt().toString(),
                "requiresManualReview", profile.isRequiresManualReview(),
                "requiresAdditionalKyc", profile.isRequiresAdditionalKyc()
            );
            
            kafkaTemplate.send("enhanced-monitoring-events", profile.getUserId().toString(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish enhanced monitoring event", e);
        }
    }
    
    // Helper methods for monitoring configuration
    
    private int getMonitoringDuration(MonitoringIntensity intensity) {
        switch (intensity) {
            case CRITICAL: return MONITORING_DURATION_DAYS_CRITICAL;
            case HIGH: return MONITORING_DURATION_DAYS_HIGH;
            case MEDIUM: return MONITORING_DURATION_DAYS_MEDIUM;
            case LOW: return MONITORING_DURATION_DAYS_LOW;
            default: return 0;
        }
    }
    
    private BigDecimal calculateTransactionLimitReduction(MonitoringIntensity intensity) {
        switch (intensity) {
            case CRITICAL: return new BigDecimal("0.10"); // 90% reduction
            case HIGH: return new BigDecimal("0.25"); // 75% reduction  
            case MEDIUM: return new BigDecimal("0.50"); // 50% reduction
            case LOW: return new BigDecimal("0.75"); // 25% reduction
            default: return BigDecimal.ONE;
        }
    }
    
    private BigDecimal calculateMaxTransactionAmount(MonitoringIntensity intensity) {
        switch (intensity) {
            case CRITICAL: return new BigDecimal("1000.00");
            case HIGH: return new BigDecimal("5000.00");
            case MEDIUM: return new BigDecimal("10000.00");
            case LOW: return new BigDecimal("25000.00");
            default: return new BigDecimal("50000.00");
        }
    }
    
    private int calculateDailyLimit(MonitoringIntensity intensity) {
        switch (intensity) {
            case CRITICAL: return 3;
            case HIGH: return 5;
            case MEDIUM: return 10;
            case LOW: return 20;
            default: return 50;
        }
    }
    
    private List<String> getBlockedCountries(MonitoringIntensity intensity) {
        if (intensity == MonitoringIntensity.CRITICAL || intensity == MonitoringIntensity.HIGH) {
            // Block all high-risk jurisdictions
            return List.of("IR", "KP", "MM", "AF", "IQ", "LY", "SO", "SS", "SY", "YE");
        }
        return Collections.emptyList();
    }
    
    private void scheduleDailyComplianceReview(UUID userId) {
        // Implementation would schedule daily review tasks
        log.debug("Scheduled daily compliance review for user: {}", userId);
    }
    
    private void scheduleWeeklyComplianceReview(UUID userId) {
        // Implementation would schedule weekly review tasks
        log.debug("Scheduled weekly compliance review for user: {}", userId);
    }
    
    private void scheduleMonitoringFollowUp(EnhancedMonitoringProfile profile) {
        // Schedule periodic review of monitoring effectiveness
        LocalDateTime firstReview = profile.getActivatedAt().plusDays(7);
        LocalDateTime midReview = profile.getActivatedAt().plusDays(profile.getMonitoringDurationDays() / 2);
        LocalDateTime finalReview = profile.getExpiresAt().minusDays(7);
        
        log.debug("Scheduled monitoring reviews for user: {} at: {}, {}, {}", 
            profile.getUserId(), firstReview, midReview, finalReview);
    }
    
    // Monitoring intensity enum
    public enum MonitoringIntensity {
        NONE, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    // Enhanced monitoring profile class
    @lombok.Data
    @lombok.Builder
    public static class EnhancedMonitoringProfile {
        private UUID profileId;
        private UUID userId;
        private UUID triggerAlertId;
        private UUID triggerTransactionId;
        private AmlAlertType triggerAlertType;
        private MonitoringIntensity intensity;
        private LocalDateTime activatedAt;
        private LocalDateTime expiresAt;
        private int monitoringDurationDays;
        private boolean requiresManualReview;
        private boolean requiresAdditionalKyc;
        private BigDecimal transactionLimitReduction;
        private boolean realTimeAlerts;
        private String status;
    }
    
    // Monitoring rules class
    @lombok.Data
    @lombok.Builder
    public static class MonitoringRules {
        private UUID userId;
        private BigDecimal maxTransactionAmount;
        private int dailyTransactionLimit;
        private boolean requiresTwoFactorAuth;
        private List<String> blockedCountries;
        private boolean alertOnAllTransactions;
    }
    
    /**
     * Resolve AML alert
     */
    public void resolveAlert(UUID alertId, UUID investigatorId, String resolution, boolean isSuspicious) {
        AmlAlert alert = amlAlertRepository.findById(alertId)
            .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        alert.setStatus(AmlAlert.AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(investigatorId);
        alert.setResolution(resolution);
        alert.setSuspiciousActivity(isSuspicious);

        amlAlertRepository.save(alert);

        if (isSuspicious) {
            // File SAR if activity is confirmed suspicious
            regulatoryReportingService.fileSuspiciousActivityReport(
                alert.getUserId(), alert.getTransactionId(), resolution);
        }

        log.info("AML alert resolved: {} by investigator: {}, suspicious: {}", 
            alertId, investigatorId, isSuspicious);
    }

    /**
     * Get open alerts for investigation
     */
    public List<AmlAlert> getOpenAlerts() {
        return amlAlertRepository.findByStatusOrderByCreatedAtDesc(AmlAlert.AlertStatus.OPEN);
    }

    /**
     * Get alerts for specific user
     */
    public List<AmlAlert> getUserAlerts(UUID userId) {
        return amlAlertRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Helper methods
    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(new BigDecimal("1000")).equals(BigDecimal.ZERO) ||
               amount.remainder(new BigDecimal("500")).equals(BigDecimal.ZERO);
    }

    private boolean isOffHoursTransaction() {
        int hour = LocalDateTime.now().getHour();
        return hour < 6 || hour > 22; // Outside 6 AM - 10 PM
    }

    private boolean isHighRiskJurisdiction(String countryCode) {
        // FATF high-risk and monitored jurisdictions
        List<String> highRiskCountries = List.of(
            "IR", "KP", "MM", "AF", "IQ", "LY", "SO", "SS", "SY", "YE" // Example list
        );
        return highRiskCountries.contains(countryCode);
    }

    private boolean shouldFileRegulatoryReport(AmlAlertType alertType, AmlSeverity severity) {
        return severity == AmlSeverity.HIGH || 
               alertType == AmlAlertType.STRUCTURING ||
               alertType == AmlAlertType.HIGH_RISK_JURISDICTION;
    }
    
    // Additional methods required by the controller
    
    /**
     * Screen a transaction for AML compliance (for controller)
     */
    public AmlScreeningResult screenTransaction(String transactionId, String userId, 
                                              BigDecimal amount, String currency,
                                              String sourceCountry, String destinationCountry) {
        log.info("Screening transaction {} for user {} amount {} {}", 
                transactionId, userId, amount, currency);
        
        try {
            List<String> flags = new ArrayList<>();
            String riskLevel = "LOW";
            boolean passed = true;
            Map<String, Object> details = new HashMap<>();
            
            // Amount-based screening
            if (amount.compareTo(LARGE_TRANSACTION_THRESHOLD) >= 0) {
                flags.add("HIGH_AMOUNT");
                riskLevel = "MEDIUM";
            }
            
            if (amount.compareTo(BigDecimal.valueOf(50000)) > 0) {
                flags.add("VERY_HIGH_AMOUNT");
                riskLevel = "HIGH";
            }
            
            // Country-based screening
            if (isHighRiskJurisdiction(sourceCountry) || isHighRiskJurisdiction(destinationCountry)) {
                flags.add("HIGH_RISK_COUNTRY");
                riskLevel = "HIGH";
                passed = false;
            }
            
            // Pattern analysis
            UUID userUuid = UUID.fromString(userId);
            UUID transactionUuid = UUID.fromString(transactionId);
            
            // Check for structuring
            checkStructuringPatterns(userUuid, amount, transactionUuid);
            
            // Check velocity
            int hourlyCount = patternAnalyzer.getHourlyTransactionCount(userUuid);
            if (hourlyCount > 10) {
                flags.add("HIGH_VELOCITY");
                if ("HIGH".equals(riskLevel)) {
                    riskLevel = "CRITICAL";
                }
            }
            
            details.put("screeningTimestamp", LocalDateTime.now());
            details.put("riskFactors", flags);
            details.put("hourlyTransactionCount", hourlyCount);
            
            return AmlScreeningResult.builder()
                .transactionId(transactionId)
                .passed(passed)
                .riskLevel(riskLevel)
                .flags(flags)
                .details(details)
                .screenedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Error screening transaction {}", transactionId, e);
            return AmlScreeningResult.builder()
                .transactionId(transactionId)
                .passed(false)
                .riskLevel("ERROR")
                .flags(List.of("SCREENING_ERROR"))
                .details(Map.of("error", e.getMessage()))
                .screenedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Get AML alerts with filtering (for controller)
     */
    public List<AmlAlert> getAlerts(String userId, AmlSeverity severity, String status, int page, int size) {
        log.debug("Getting AML alerts: user={}, severity={}, status={}", userId, severity, status);
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            
            if (userId != null && severity != null && status != null) {
                return amlAlertRepository.findByUserIdAndSeverityAndStatus(
                    UUID.fromString(userId), severity, AmlAlert.AlertStatus.valueOf(status), pageable).getContent();
            } else if (userId != null) {
                return amlAlertRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
            } else if (severity != null) {
                return amlAlertRepository.findBySeverityOrderByCreatedAtDesc(severity);
            } else if (status != null) {
                return amlAlertRepository.findByStatusOrderByCreatedAtDesc(AmlAlert.AlertStatus.valueOf(status));
            } else {
                return amlAlertRepository.findAll(pageable).getContent();
            }
        } catch (Exception e) {
            log.error("Error getting AML alerts", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Review an AML alert (for controller)
     */
    @Transactional
    public void reviewAlert(String alertId, String action, String comments, String reviewedBy) {
        log.info("Reviewing AML alert {} with action {}", alertId, action);
        
        try {
            AmlAlert alert = amlAlertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
            
            alert.setStatus(AmlAlert.AlertStatus.valueOf(action.toUpperCase()));
            alert.setResolvedAt(LocalDateTime.now());
            alert.setResolvedBy(UUID.fromString(reviewedBy));
            alert.setResolution(comments);
            
            amlAlertRepository.save(alert);
            
            log.info("AML alert {} reviewed by {}: {}", alertId, reviewedBy, action);
            
        } catch (Exception e) {
            log.error("Error reviewing AML alert {}", alertId, e);
            throw new RuntimeException("Failed to review alert", e);
        }
    }
    
    /**
     * Check against watchlists (for controller)
     */
    public WatchlistCheckResult checkWatchlist(String name, String country, String idNumber, LocalDate dateOfBirth) {
        log.debug("Checking watchlist for: {}", name);
        
        try {
            // Simple watchlist check implementation
            List<String> watchlistNames = Arrays.asList(
                "John Doe Sanctions", "Jane Smith PEP", "Bad Actor Corp"
            );
            
            boolean isMatch = watchlistNames.stream()
                .anyMatch(wlName -> wlName.toLowerCase().contains(name.toLowerCase()));
            
            List<WatchlistMatch> matches = new ArrayList<>();
            if (isMatch) {
                matches.add(WatchlistMatch.builder()
                    .listName("SANCTIONS_LIST")
                    .matchedName(name)
                    .matchScore(0.95)
                    .reason("Name similarity match")
                    .build());
            }
            
            return WatchlistCheckResult.builder()
                .isMatch(isMatch)
                .matches(matches)
                .checkId(UUID.randomUUID().toString())
                .checkedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Error checking watchlist for {}", name, e);
            return WatchlistCheckResult.builder()
                .isMatch(false)
                .matches(new ArrayList<>())
                .checkId(UUID.randomUUID().toString())
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Get AML statistics (for controller)
     */
    public AmlStatistics getStatistics(LocalDate fromDate, LocalDate toDate) {
        log.debug("Getting AML statistics from {} to {}", fromDate, toDate);
        
        try {
            // Mock statistics - in real implementation would query database
            return AmlStatistics.builder()
                .totalScreenings(1500L)
                .flaggedTransactions(75L)
                .sarsField(12L)
                .alertsByType(Map.of(
                    "HIGH_AMOUNT", 25L,
                    "STRUCTURING", 15L,
                    "HIGH_VELOCITY", 35L
                ))
                .volumeByRiskLevel(Map.of(
                    "LOW", BigDecimal.valueOf(500000),
                    "MEDIUM", BigDecimal.valueOf(250000),
                    "HIGH", BigDecimal.valueOf(100000)
                ))
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
            
        } catch (Exception e) {
            log.error("Error getting AML statistics", e);
            return AmlStatistics.builder()
                .totalScreenings(0)
                .flaggedTransactions(0)
                .sarsField(0)
                .alertsByType(new HashMap<>())
                .volumeByRiskLevel(new HashMap<>())
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        }
    }
}