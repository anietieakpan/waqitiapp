package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for ensuring notification compliance with various regulations
 * including GDPR, CAN-SPAM, TCPA, and regional communication laws
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationComplianceService {
    
    private final NotificationPreferencesService preferencesService;
    
    // Compliance patterns
    private static final Pattern UNSUBSCRIBE_PATTERN = Pattern.compile(
        "unsubscribe|opt.?out|remove.?me|stop", Pattern.CASE_INSENSITIVE);
    
    // Quiet hours by region
    private static final Map<String, QuietHours> QUIET_HOURS_BY_REGION = Map.of(
        "US", new QuietHours(LocalTime.of(21, 0), LocalTime.of(8, 0)),
        "EU", new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0)),
        "UK", new QuietHours(LocalTime.of(21, 0), LocalTime.of(8, 0)),
        "CA", new QuietHours(LocalTime.of(21, 0), LocalTime.of(8, 0))
    );
    
    /**
     * Check if notification complies with all applicable regulations
     */
    public ComplianceResult checkCompliance(NotificationComplianceRequest request) {
        try {
            log.debug("Checking compliance for notification to user: {}", request.getUserId());
            
            ComplianceResult.ComplianceResultBuilder result = ComplianceResult.builder()
                .compliant(true)
                .checks(new ArrayList<>());
            
            // GDPR Compliance Check
            ComplianceCheck gdprCheck = checkGDPRCompliance(request);
            result.check(gdprCheck);
            
            // CAN-SPAM Compliance Check  
            ComplianceCheck canSpamCheck = checkCANSPAMCompliance(request);
            result.check(canSpamCheck);
            
            // TCPA Compliance Check (SMS/Phone)
            if ("SMS".equalsIgnoreCase(request.getChannel()) || 
                "VOICE".equalsIgnoreCase(request.getChannel())) {
                ComplianceCheck tcpaCheck = checkTCPACompliance(request);
                result.check(tcpaCheck);
            }
            
            // Quiet Hours Check
            ComplianceCheck quietHoursCheck = checkQuietHours(request);
            result.check(quietHoursCheck);
            
            // Frequency Limits Check
            ComplianceCheck frequencyCheck = checkFrequencyLimits(request);
            result.check(frequencyCheck);
            
            // Content Compliance Check
            ComplianceCheck contentCheck = checkContentCompliance(request);
            result.check(contentCheck);
            
            // Determine overall compliance
            List<ComplianceCheck> checks = result.build().getChecks();
            boolean overallCompliant = checks.stream().allMatch(ComplianceCheck::isPassed);
            
            return result.compliant(overallCompliant).build();
            
        } catch (Exception e) {
            log.error("Error checking compliance: {}", e.getMessage(), e);
            return ComplianceResult.builder()
                .compliant(false)
                .checks(List.of(ComplianceCheck.builder()
                    .checkType("ERROR")
                    .passed(false)
                    .reason("Compliance check failed: " + e.getMessage())
                    .build()))
                .build();
        }
    }
    
    /**
     * Check GDPR compliance
     */
    private ComplianceCheck checkGDPRCompliance(NotificationComplianceRequest request) {
        try {
            // Check if user is in EU region
            if (!isEUUser(request.getUserRegion())) {
                return ComplianceCheck.builder()
                    .checkType("GDPR")
                    .passed(true)
                    .reason("User not in EU region, GDPR not applicable")
                    .build();
            }
            
            // Check explicit consent for marketing communications
            if ("MARKETING".equalsIgnoreCase(request.getNotificationType())) {
                boolean hasConsent = preferencesService.hasMarketingConsent(request.getUserId());
                if (!hasConsent) {
                    return ComplianceCheck.builder()
                        .checkType("GDPR")
                        .passed(false)
                        .reason("No explicit consent for marketing communications under GDPR")
                        .build();
                }
            }
            
            // Check for lawful basis for processing
            String lawfulBasis = determineLawfulBasis(request);
            if (lawfulBasis == null) {
                return ComplianceCheck.builder()
                    .checkType("GDPR")
                    .passed(false)
                    .reason("No lawful basis for processing under GDPR")
                    .build();
            }
            
            // Check data minimization - ensure we're only processing necessary data
            if (!isDataMinimized(request)) {
                return ComplianceCheck.builder()
                    .checkType("GDPR")
                    .passed(false)
                    .reason("Data processing violates GDPR minimization principle")
                    .build();
            }
            
            return ComplianceCheck.builder()
                .checkType("GDPR")
                .passed(true)
                .reason("GDPR compliance verified")
                .metadata(Map.of("lawfulBasis", lawfulBasis))
                .build();
                
        } catch (Exception e) {
            log.error("Error in GDPR compliance check: {}", e.getMessage());
            return ComplianceCheck.builder()
                .checkType("GDPR")
                .passed(false)
                .reason("Error checking GDPR compliance: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Check CAN-SPAM Act compliance (US email regulations)
     */
    private ComplianceCheck checkCANSPAMCompliance(NotificationComplianceRequest request) {
        try {
            if (!"EMAIL".equalsIgnoreCase(request.getChannel()) || 
                !"US".equalsIgnoreCase(request.getUserRegion())) {
                return ComplianceCheck.builder()
                    .checkType("CAN-SPAM")
                    .passed(true)
                    .reason("CAN-SPAM not applicable")
                    .build();
            }
            
            // Check for valid sender identification
            if (request.getFromAddress() == null || request.getFromName() == null) {
                return ComplianceCheck.builder()
                    .checkType("CAN-SPAM")
                    .passed(false)
                    .reason("Missing sender identification required by CAN-SPAM")
                    .build();
            }
            
            // Check for unsubscribe mechanism
            if ("MARKETING".equalsIgnoreCase(request.getNotificationType())) {
                boolean hasUnsubscribe = hasUnsubscribeMechanism(request.getContent());
                if (!hasUnsubscribe) {
                    return ComplianceCheck.builder()
                        .checkType("CAN-SPAM")
                        .passed(false)
                        .reason("Marketing email missing unsubscribe mechanism required by CAN-SPAM")
                        .build();
                }
            }
            
            // Check for truthful header information
            if (hasDeceptiveHeaders(request)) {
                return ComplianceCheck.builder()
                    .checkType("CAN-SPAM")
                    .passed(false)
                    .reason("Deceptive header information violates CAN-SPAM")
                    .build();
            }
            
            return ComplianceCheck.builder()
                .checkType("CAN-SPAM")
                .passed(true)
                .reason("CAN-SPAM compliance verified")
                .build();
                
        } catch (Exception e) {
            log.error("Error in CAN-SPAM compliance check: {}", e.getMessage());
            return ComplianceCheck.builder()
                .checkType("CAN-SPAM")
                .passed(false)
                .reason("Error checking CAN-SPAM compliance: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Check TCPA compliance (US SMS/phone regulations)
     */
    private ComplianceCheck checkTCPACompliance(NotificationComplianceRequest request) {
        try {
            if (!"US".equalsIgnoreCase(request.getUserRegion())) {
                return ComplianceCheck.builder()
                    .checkType("TCPA")
                    .passed(true)
                    .reason("TCPA not applicable outside US")
                    .build();
            }
            
            // Check for prior express consent
            boolean hasConsent = preferencesService.hasSMSConsent(request.getUserId());
            if (!hasConsent) {
                return ComplianceCheck.builder()
                    .checkType("TCPA")
                    .passed(false)
                    .reason("No prior express consent for SMS communications required by TCPA")
                    .build();
            }
            
            // Check for opt-out mechanism in SMS
            if ("SMS".equalsIgnoreCase(request.getChannel()) && 
                "MARKETING".equalsIgnoreCase(request.getNotificationType())) {
                boolean hasOptOut = hasOptOutInstructions(request.getContent());
                if (!hasOptOut) {
                    return ComplianceCheck.builder()
                        .checkType("TCPA")
                        .passed(false)
                        .reason("Marketing SMS missing opt-out instructions required by TCPA")
                        .build();
                }
            }
            
            return ComplianceCheck.builder()
                .checkType("TCPA")
                .passed(true)
                .reason("TCPA compliance verified")
                .build();
                
        } catch (Exception e) {
            log.error("Error in TCPA compliance check: {}", e.getMessage());
            return ComplianceCheck.builder()
                .checkType("TCPA")
                .passed(false)
                .reason("Error checking TCPA compliance: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Check quiet hours compliance
     */
    private ComplianceCheck checkQuietHours(NotificationComplianceRequest request) {
        try {
            // Skip quiet hours for urgent/emergency notifications
            if ("URGENT".equalsIgnoreCase(request.getPriority()) || 
                "EMERGENCY".equalsIgnoreCase(request.getPriority()) ||
                "SECURITY".equalsIgnoreCase(request.getNotificationType())) {
                return ComplianceCheck.builder()
                    .checkType("QUIET_HOURS")
                    .passed(true)
                    .reason("Urgent/emergency notification exempt from quiet hours")
                    .build();
            }
            
            QuietHours quietHours = QUIET_HOURS_BY_REGION.get(request.getUserRegion());
            if (quietHours == null) {
                quietHours = QUIET_HOURS_BY_REGION.get("US"); // Default
            }
            
            ZonedDateTime userTime = getUserLocalTime(request.getUserId(), request.getUserTimezone());
            LocalTime currentTime = userTime.toLocalTime();
            
            if (quietHours.isQuietTime(currentTime)) {
                return ComplianceCheck.builder()
                    .checkType("QUIET_HOURS")
                    .passed(false)
                    .reason(String.format("Notification would be sent during quiet hours (%s-%s)", 
                        quietHours.getStartTime(), quietHours.getEndTime()))
                    .metadata(Map.of(
                        "userTime", currentTime.toString(),
                        "quietStart", quietHours.getStartTime().toString(),
                        "quietEnd", quietHours.getEndTime().toString()
                    ))
                    .build();
            }
            
            return ComplianceCheck.builder()
                .checkType("QUIET_HOURS")
                .passed(true)
                .reason("Within permitted hours")
                .metadata(Map.of("userTime", currentTime.toString()))
                .build();
                
        } catch (Exception e) {
            log.error("Error in quiet hours compliance check: {}", e.getMessage());
            return ComplianceCheck.builder()
                .checkType("QUIET_HOURS")
                .passed(false)
                .reason("Error checking quiet hours: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Check frequency limits compliance
     */
    private ComplianceCheck checkFrequencyLimits(NotificationComplianceRequest request) {
        try {
            Map<String, Integer> limits = getFrequencyLimits(request.getUserId(), request.getChannel());
            
            for (Map.Entry<String, Integer> limit : limits.entrySet()) {
                String period = limit.getKey();
                int maxCount = limit.getValue();
                
                int currentCount = getCurrentNotificationCount(
                    request.getUserId(), 
                    request.getChannel(), 
                    period
                );
                
                if (currentCount >= maxCount) {
                    return ComplianceCheck.builder()
                        .checkType("FREQUENCY_LIMITS")
                        .passed(false)
                        .reason(String.format("Frequency limit exceeded: %d/%d notifications in %s", 
                            currentCount, maxCount, period))
                        .metadata(Map.of(
                            "period", period,
                            "current", currentCount,
                            "limit", maxCount
                        ))
                        .build();
                }
            }
            
            return ComplianceCheck.builder()
                .checkType("FREQUENCY_LIMITS")
                .passed(true)
                .reason("Within frequency limits")
                .build();
                
        } catch (Exception e) {
            log.error("Error in frequency limits compliance check: {}", e.getMessage());
            return ComplianceCheck.builder()
                .checkType("FREQUENCY_LIMITS")
                .passed(false)
                .reason("Error checking frequency limits: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Check content compliance (spam detection, inappropriate content)
     */
    private ComplianceCheck checkContentCompliance(NotificationComplianceRequest request) {
        try {
            String content = request.getContent();
            if (content == null || content.trim().isEmpty()) {
                return ComplianceCheck.builder()
                    .checkType("CONTENT")
                    .passed(false)
                    .reason("Empty notification content")
                    .build();
            }
            
            // Check for spam indicators
            double spamScore = calculateSpamScore(content);
            if (spamScore > 0.7) {
                return ComplianceCheck.builder()
                    .checkType("CONTENT")
                    .passed(false)
                    .reason(String.format("Content flagged as potential spam (score: %.2f)", spamScore))
                    .metadata(Map.of("spamScore", spamScore))
                    .build();
            }
            
            // Check for inappropriate content
            if (containsInappropriateContent(content)) {
                return ComplianceCheck.builder()
                    .checkType("CONTENT")
                    .passed(false)
                    .reason("Content contains inappropriate material")
                    .build();
            }
            
            // Check content length limits
            if (isContentTooLong(content, request.getChannel())) {
                return ComplianceCheck.builder()
                    .checkType("CONTENT")
                    .passed(false)
                    .reason("Content exceeds channel length limits")
                    .build();
            }
            
            return ComplianceCheck.builder()
                .checkType("CONTENT")
                .passed(true)
                .reason("Content complies with policies")
                .metadata(Map.of("spamScore", spamScore))
                .build();
                
        } catch (Exception e) {
            log.error("Error in content compliance check: {}", e.getMessage());
            return ComplianceCheck.builder()
                .checkType("CONTENT")
                .passed(false)
                .reason("Error checking content compliance: " + e.getMessage())
                .build();
        }
    }
    
    // Helper methods
    
    private boolean isEUUser(String region) {
        Set<String> euRegions = Set.of("EU", "DE", "FR", "IT", "ES", "NL", "PL", "BE", "GR", "PT", "CZ", "HU", "SE", "AT", "DK", "FI", "SK", "IE", "HR", "LT", "SI", "LV", "EE", "CY", "LU", "MT", "BG", "RO");
        return euRegions.contains(region);
    }
    
    private String determineLawfulBasis(NotificationComplianceRequest request) {
        switch (request.getNotificationType().toUpperCase()) {
            case "SECURITY":
            case "FRAUD_ALERT":
                return "vital_interests";
            case "TRANSACTIONAL":
            case "SERVICE":
                return "contract";
            case "MARKETING":
                return "consent";
            case "LEGAL":
            case "COMPLIANCE":
                return "legal_obligation";
            default:
                return "legitimate_interests";
        }
    }
    
    private boolean isDataMinimized(NotificationComplianceRequest request) {
        // Check if we're only using necessary data for the notification
        return request.getPersonalData() == null || 
               request.getPersonalData().size() <= 5; // Reasonable limit
    }
    
    private boolean hasUnsubscribeMechanism(String content) {
        return UNSUBSCRIBE_PATTERN.matcher(content).find();
    }
    
    private boolean hasDeceptiveHeaders(NotificationComplianceRequest request) {
        // Check for common deceptive practices
        String fromName = request.getFromName();
        String subject = request.getSubject();
        
        if (fromName != null && fromName.contains("noreply") && subject != null && subject.toLowerCase().contains("urgent")) {
            return true; // Suspicious combination
        }
        
        return false;
    }
    
    private boolean hasOptOutInstructions(String content) {
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("stop") || 
               lowerContent.contains("opt out") || 
               lowerContent.contains("unsubscribe");
    }
    
    private ZonedDateTime getUserLocalTime(String userId, String timezone) {
        try {
            ZoneId zoneId = timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
            return ZonedDateTime.now(zoneId);
        } catch (Exception e) {
            log.warn("Invalid timezone {} for user {}, using system default", timezone, userId);
            return ZonedDateTime.now();
        }
    }
    
    private Map<String, Integer> getFrequencyLimits(String userId, String channel) {
        // In production, this would be configurable per user/channel
        Map<String, Integer> defaults = new HashMap<>();
        
        switch (channel.toUpperCase()) {
            case "SMS":
                defaults.put("hourly", 5);
                defaults.put("daily", 20);
                defaults.put("weekly", 50);
                break;
            case "EMAIL":
                defaults.put("hourly", 10);
                defaults.put("daily", 50);
                defaults.put("weekly", 200);
                break;
            case "PUSH":
                defaults.put("hourly", 20);
                defaults.put("daily", 100);
                defaults.put("weekly", 500);
                break;
            default:
                defaults.put("hourly", 10);
                defaults.put("daily", 30);
                defaults.put("weekly", 100);
        }
        
        return defaults;
    }
    
    private int getCurrentNotificationCount(String userId, String channel, String period) {
        // In production, this would query the database for actual counts
        // For now, return a simulated low count
        return 1;
    }
    
    private double calculateSpamScore(String content) {
        double score = 0.0;
        String lowerContent = content.toLowerCase();
        
        // Common spam indicators
        if (lowerContent.contains("free money")) score += 0.3;
        if (lowerContent.contains("click here now")) score += 0.2;
        if (lowerContent.contains("limited time")) score += 0.1;
        if (lowerContent.contains("urgent")) score += 0.1;
        if (content.chars().filter(ch -> ch == '!').count() > 3) score += 0.2;
        if (content.chars().filter(Character::isUpperCase).count() > content.length() * 0.5) score += 0.2;
        
        return Math.min(score, 1.0);
    }
    
    private boolean containsInappropriateContent(String content) {
        String lowerContent = content.toLowerCase();
        List<String> inappropriateTerms = List.of(
            "hate", "violence", "discrimination", "harassment"
        );
        
        return inappropriateTerms.stream().anyMatch(lowerContent::contains);
    }
    
    private boolean isContentTooLong(String content, String channel) {
        switch (channel.toUpperCase()) {
            case "SMS":
                return content.length() > 1600; // 10 segments
            case "PUSH":
                return content.length() > 500;
            case "EMAIL":
                return content.length() > 100000; // 100KB
            default:
                return content.length() > 5000;
        }
    }
    
    // Data classes
    
    @lombok.Builder
    @lombok.Data
    public static class NotificationComplianceRequest {
        private String userId;
        private String channel;
        private String notificationType;
        private String priority;
        private String content;
        private String subject;
        private String fromName;
        private String fromAddress;
        private String userRegion;
        private String userTimezone;
        private Map<String, Object> personalData;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ComplianceResult {
        private boolean compliant;
        private List<ComplianceCheck> checks;
        
        public static class ComplianceResultBuilder {
            public ComplianceResultBuilder check(ComplianceCheck check) {
                if (this.checks == null) {
                    this.checks = new ArrayList<>();
                }
                this.checks.add(check);
                return this;
            }
        }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ComplianceCheck {
        private String checkType;
        private boolean passed;
        private String reason;
        private Map<String, Object> metadata;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class QuietHours {
        private LocalTime startTime;
        private LocalTime endTime;
        
        public boolean isQuietTime(LocalTime time) {
            if (startTime.isBefore(endTime)) {
                // Same day quiet hours (e.g., 22:00 - 23:00)
                return !time.isBefore(startTime) && !time.isAfter(endTime);
            } else {
                // Overnight quiet hours (e.g., 22:00 - 08:00)
                return !time.isBefore(startTime) || !time.isAfter(endTime);
            }
        }
    }
}