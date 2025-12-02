package com.waqiti.analytics.service;

import com.waqiti.analytics.model.UserEvent;
import com.waqiti.analytics.model.UserMetrics;
import com.waqiti.analytics.model.UserSegment;
import com.waqiti.analytics.repository.UserEventRepository;
import com.waqiti.analytics.repository.UserMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User segmentation service for advanced analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentationService {
    
    private final UserMetricsRepository metricsRepository;
    private final UserEventRepository eventRepository;
    
    /**
     * Get user segments for a specific user
     */
    public List<UserSegment> getUserSegments(String userId) {
        log.debug("Getting user segments for user: {}", userId);
        
        Optional<UserMetrics> metricsOpt = metricsRepository.findByUserId(userId);
        if (metricsOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        UserMetrics metrics = metricsOpt.get();
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<UserEvent> recentEvents = eventRepository.findByUserIdAndTimestampBetween(
                userId, thirtyDaysAgo, Instant.now());
        
        List<UserSegment> segments = new ArrayList<>();
        
        // Value-based segmentation
        segments.addAll(getValueBasedSegments(userId, metrics));
        
        // Behavioral segmentation
        segments.addAll(getBehavioralSegments(userId, metrics, recentEvents));
        
        // Engagement segmentation
        segments.addAll(getEngagementSegments(userId, metrics, recentEvents));
        
        // Transaction frequency segmentation
        segments.addAll(getTransactionFrequencySegments(userId, metrics));
        
        // Lifecycle segmentation
        segments.addAll(getLifecycleSegments(userId, metrics, recentEvents));
        
        // Risk-based segmentation
        segments.addAll(getRiskBasedSegments(userId, metrics, recentEvents));
        
        // Platform segmentation
        segments.addAll(getPlatformSegments(userId, recentEvents));
        
        // Demographic segmentation (if data available)
        segments.addAll(getDemographicSegments(userId, metrics));
        
        return segments;
    }
    
    /**
     * Bulk segment multiple users
     */
    public Map<String, List<UserSegment>> bulkSegmentUsers(List<String> userIds) {
        log.info("Bulk segmenting {} users", userIds.size());
        
        return userIds.stream()
                .collect(Collectors.toMap(
                        userId -> userId,
                        this::getUserSegments
                ));
    }
    
    /**
     * Get all users in a specific segment (paginated to prevent OOM)
     */
    public List<String> getUsersInSegment(String segmentName) {
        log.debug("Finding users in segment: {}", segmentName);
        
        List<String> usersInSegment = new ArrayList<>();
        int pageSize = 100;
        int pageNumber = 0;
        Page<UserMetrics> page;
        
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = metricsRepository.findAll(pageable);
            
            for (UserMetrics metrics : page.getContent()) {
                List<UserSegment> userSegments = getUserSegments(metrics.getUserId());
                boolean hasSegment = userSegments.stream()
                        .anyMatch(segment -> segment.getSegmentName().equals(segmentName));
                
                if (hasSegment) {
                    usersInSegment.add(metrics.getUserId());
                }
            }
            
            pageNumber++;
            log.debug("Processed page {} of {} for segment search", pageNumber, page.getTotalPages());
        } while (page.hasNext());
        
        log.info("Found {} users in segment: {}", usersInSegment.size(), segmentName);
        return usersInSegment;
    }
    
    /**
     * Get segment statistics (paginated to prevent OOM)
     */
    public Map<String, Integer> getSegmentStatistics() {
        log.info("Calculating segment statistics");
        
        Map<String, Integer> segmentCounts = new HashMap<>();
        int pageSize = 100;
        int pageNumber = 0;
        Page<UserMetrics> page;
        
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = metricsRepository.findAll(pageable);
            
            for (UserMetrics metrics : page.getContent()) {
                List<UserSegment> userSegments = getUserSegments(metrics.getUserId());
                for (UserSegment segment : userSegments) {
                    segmentCounts.merge(segment.getSegmentName(), 1, Integer::sum);
                }
            }
            
            pageNumber++;
            if (pageNumber % 10 == 0) {
                log.info("Processed {} pages for segment statistics", pageNumber);
            }
        } while (page.hasNext());
        
        log.info("Segment statistics calculated for {} users", pageNumber * pageSize);
        
        return segmentCounts;
    }
    
    /**
     * Value-based segmentation
     */
    private List<UserSegment> getValueBasedSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        BigDecimal totalValue = metrics.getTotalTransactionVolume();
        
        if (totalValue.compareTo(BigDecimal.valueOf(50000)) > 0) {
            segments.add(createSegment("HIGH_VALUE_CUSTOMER", 
                    "Customer with transaction volume > $50,000", 0.95));
        } else if (totalValue.compareTo(BigDecimal.valueOf(10000)) > 0) {
            segments.add(createSegment("MEDIUM_VALUE_CUSTOMER", 
                    "Customer with transaction volume > $10,000", 0.90));
        } else if (totalValue.compareTo(BigDecimal.valueOf(1000)) > 0) {
            segments.add(createSegment("LOW_VALUE_CUSTOMER", 
                    "Customer with transaction volume > $1,000", 0.85));
        } else if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
            segments.add(createSegment("MINIMAL_VALUE_CUSTOMER", 
                    "Customer with minimal transaction volume", 0.80));
        } else {
            segments.add(createSegment("NON_TRANSACTING_USER", 
                    "User who has not made any transactions", 0.95));
        }
        
        return segments;
    }
    
    /**
     * Behavioral segmentation
     */
    private List<UserSegment> getBehavioralSegments(String userId, UserMetrics metrics, List<UserEvent> events) {
        List<UserSegment> segments = new ArrayList<>();
        
        // Transaction behavior
        if (metrics.getTransactionCount() > 100) {
            segments.add(createSegment("FREQUENT_TRANSACTOR", 
                    "User who makes frequent transactions", 0.90));
        } else if (metrics.getTransactionCount() > 20) {
            segments.add(createSegment("REGULAR_TRANSACTOR", 
                    "User who makes regular transactions", 0.85));
        } else if (metrics.getTransactionCount() > 0) {
            segments.add(createSegment("OCCASIONAL_TRANSACTOR", 
                    "User who makes occasional transactions", 0.80));
        }
        
        // Feature usage behavior
        Map<String, Long> eventCounts = events.stream()
                .collect(Collectors.groupingBy(UserEvent::getEventName, Collectors.counting()));
        
        long featureUsageCount = eventCounts.getOrDefault("feature_used", 0L);
        if (featureUsageCount > 50) {
            segments.add(createSegment("POWER_USER", 
                    "User who heavily uses platform features", 0.92));
        } else if (featureUsageCount > 10) {
            segments.add(createSegment("FEATURE_EXPLORER", 
                    "User who explores different features", 0.88));
        }
        
        // Error behavior
        long errorCount = eventCounts.getOrDefault("error_occurred", 0L);
        if (errorCount > 20) {
            segments.add(createSegment("HIGH_ERROR_USER", 
                    "User who encounters frequent errors", 0.85));
        }
        
        return segments;
    }
    
    /**
     * Engagement segmentation
     */
    private List<UserSegment> getEngagementSegments(String userId, UserMetrics metrics, List<UserEvent> events) {
        List<UserSegment> segments = new ArrayList<>();
        
        double engagementScore = metrics.getEngagementScore();
        
        if (engagementScore > 90) {
            segments.add(createSegment("HIGHLY_ENGAGED", 
                    "Highly engaged user with active participation", 0.95));
        } else if (engagementScore > 70) {
            segments.add(createSegment("MODERATELY_ENGAGED", 
                    "Moderately engaged user", 0.90));
        } else if (engagementScore > 40) {
            segments.add(createSegment("LOW_ENGAGEMENT", 
                    "User with low engagement levels", 0.85));
        } else {
            segments.add(createSegment("DISENGAGED", 
                    "User with very low engagement", 0.95));
        }
        
        // Recent activity
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long recentActivity = events.stream()
                .filter(e -> e.getTimestamp().isAfter(weekAgo))
                .count();
        
        if (recentActivity == 0) {
            segments.add(createSegment("INACTIVE_USER", 
                    "User with no recent activity", 0.90));
        } else if (recentActivity > 50) {
            segments.add(createSegment("HYPERACTIVE_USER", 
                    "User with extremely high activity", 0.88));
        }
        
        return segments;
    }
    
    /**
     * Transaction frequency segmentation
     */
    private List<UserSegment> getTransactionFrequencySegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        if (metrics.getTransactionCount() == 0) {
            return segments; // Already handled in value-based segmentation
        }
        
        // Calculate approximate frequency
        long daysSinceFirstTransaction = ChronoUnit.DAYS.between(
                metrics.getCreatedAt(), Instant.now());
        
        if (daysSinceFirstTransaction > 0) {
            double transactionsPerDay = (double) metrics.getTransactionCount() / daysSinceFirstTransaction;
            
            if (transactionsPerDay > 2.0) {
                segments.add(createSegment("DAILY_TRANSACTOR", 
                        "User who transacts multiple times daily", 0.90));
            } else if (transactionsPerDay > 0.5) {
                segments.add(createSegment("WEEKLY_TRANSACTOR", 
                        "User who transacts weekly", 0.85));
            } else if (transactionsPerDay > 0.1) {
                segments.add(createSegment("MONTHLY_TRANSACTOR", 
                        "User who transacts monthly", 0.80));
            } else {
                segments.add(createSegment("RARE_TRANSACTOR", 
                        "User who rarely transacts", 0.88));
            }
        }
        
        return segments;
    }
    
    /**
     * Lifecycle segmentation
     */
    private List<UserSegment> getLifecycleSegments(String userId, UserMetrics metrics, List<UserEvent> events) {
        List<UserSegment> segments = new ArrayList<>();
        
        long daysSinceRegistration = ChronoUnit.DAYS.between(metrics.getCreatedAt(), Instant.now());
        
        if (daysSinceRegistration < 7) {
            segments.add(createSegment("NEW_USER", 
                    "User registered within the last 7 days", 0.85));
        } else if (daysSinceRegistration < 30) {
            segments.add(createSegment("RECENT_USER", 
                    "User registered within the last 30 days", 0.80));
        } else if (daysSinceRegistration < 90) {
            segments.add(createSegment("ESTABLISHED_USER", 
                    "User registered within the last 90 days", 0.75));
        } else if (daysSinceRegistration < 365) {
            segments.add(createSegment("MATURE_USER", 
                    "User registered within the last year", 0.70));
        } else {
            segments.add(createSegment("VETERAN_USER", 
                    "Long-time user (over 1 year)", 0.90));
        }
        
        // Onboarding completion
        boolean hasCompletedTransaction = metrics.getTransactionCount() > 0;
        Map<String, Long> eventCounts = events.stream()
                .collect(Collectors.groupingBy(UserEvent::getEventName, Collectors.counting()));
        
        long profileViews = eventCounts.getOrDefault("profile_view", 0L);
        long featureUsage = eventCounts.getOrDefault("feature_used", 0L);
        
        if (!hasCompletedTransaction && profileViews == 0 && featureUsage == 0) {
            segments.add(createSegment("ONBOARDING_INCOMPLETE", 
                    "User who hasn't completed onboarding", 0.92));
        } else if (hasCompletedTransaction && featureUsage > 5) {
            segments.add(createSegment("FULLY_ONBOARDED", 
                    "User who has completed full onboarding", 0.80));
        }
        
        return segments;
    }
    
    /**
     * Risk-based segmentation
     */
    private List<UserSegment> getRiskBasedSegments(String userId, UserMetrics metrics, List<UserEvent> events) {
        List<UserSegment> segments = new ArrayList<>();
        
        // Calculate risk score
        double riskScore = calculateRiskScore(userId, metrics, events);
        
        if (riskScore > 0.8) {
            segments.add(createSegment("HIGH_RISK_USER", 
                    "User with high risk indicators", 0.95));
        } else if (riskScore > 0.5) {
            segments.add(createSegment("MEDIUM_RISK_USER", 
                    "User with moderate risk indicators", 0.90));
        } else if (riskScore > 0.2) {
            segments.add(createSegment("LOW_RISK_USER", 
                    "User with low risk indicators", 0.85));
        } else {
            segments.add(createSegment("MINIMAL_RISK_USER", 
                    "User with minimal risk indicators", 0.75));
        }
        
        return segments;
    }
    
    /**
     * Platform segmentation
     */
    private List<UserSegment> getPlatformSegments(String userId, List<UserEvent> events) {
        List<UserSegment> segments = new ArrayList<>();
        
        Map<String, Long> platformUsage = events.stream()
                .filter(e -> e.getPlatform() != null)
                .collect(Collectors.groupingBy(UserEvent::getPlatform, Collectors.counting()));
        
        if (platformUsage.isEmpty()) {
            return segments;
        }
        
        // Find dominant platform
        String dominantPlatform = platformUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
        
        segments.add(createSegment(dominantPlatform + "_PRIMARY_USER", 
                "Primary user of " + dominantPlatform + " platform", 0.80));
        
        // Multi-platform usage
        if (platformUsage.size() > 2) {
            segments.add(createSegment("MULTI_PLATFORM_USER", 
                    "User who uses multiple platforms", 0.85));
        }
        
        return segments;
    }
    
    /**
     * Demographic segmentation based on comprehensive user profile data
     */
    private List<UserSegment> getDemographicSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        try {
            // Language-based segmentation
            if (metrics.getPreferredLanguage() != null) {
                String language = metrics.getPreferredLanguage().toUpperCase();
                segments.add(createSegment(language + "_SPEAKER", 
                        "User who speaks " + metrics.getPreferredLanguage(), 0.85));
                
                // Language-specific financial behavior patterns
                switch (language) {
                    case "EN" -> segments.add(createSegment("ENGLISH_FINANCE_USER", 
                            "English-speaking financial services user", 0.80));
                    case "ES" -> segments.add(createSegment("SPANISH_FINANCE_USER", 
                            "Spanish-speaking financial services user with distinct patterns", 0.80));
                    case "FR" -> segments.add(createSegment("FRENCH_FINANCE_USER", 
                            "French-speaking user with European financial patterns", 0.80));
                    case "DE" -> segments.add(createSegment("GERMAN_FINANCE_USER", 
                            "German-speaking user with European financial patterns", 0.80));
                    case "ZH" -> segments.add(createSegment("CHINESE_FINANCE_USER", 
                            "Chinese-speaking user with Asian financial patterns", 0.80));
                    case "AR" -> segments.add(createSegment("ARABIC_FINANCE_USER", 
                            "Arabic-speaking user with Middle Eastern financial patterns", 0.80));
                    case "PT" -> segments.add(createSegment("PORTUGUESE_FINANCE_USER", 
                            "Portuguese-speaking user with Latin American patterns", 0.80));
                    case "HI" -> segments.add(createSegment("HINDI_FINANCE_USER", 
                            "Hindi-speaking user with South Asian financial patterns", 0.80));
                }
            }
            
            // Geographic and timezone-based segmentation
            if (metrics.getTimeZone() != null) {
                String timezone = metrics.getTimeZone();
                String normalizedTimezone = timezone.replace("/", "_").replace("-", "_");
                segments.add(createSegment("TIMEZONE_" + normalizedTimezone, 
                        "User in " + timezone + " timezone", 0.75));
                
                // Regional financial behavior patterns based on timezone
                if (timezone.startsWith("America/")) {
                    segments.add(createSegment("AMERICAS_REGION_USER", 
                            "User in Americas region with distinct financial patterns", 0.82));
                    
                    // North America vs Latin America distinction
                    if (timezone.contains("New_York") || timezone.contains("Chicago") || 
                        timezone.contains("Denver") || timezone.contains("Los_Angeles") ||
                        timezone.contains("Toronto") || timezone.contains("Vancouver")) {
                        segments.add(createSegment("NORTH_AMERICA_USER", 
                                "North American user with established banking preferences", 0.85));
                    } else {
                        segments.add(createSegment("LATIN_AMERICA_USER", 
                                "Latin American user with emerging market patterns", 0.85));
                    }
                } else if (timezone.startsWith("Europe/")) {
                    segments.add(createSegment("EUROPE_REGION_USER", 
                            "European user with EU financial regulations awareness", 0.82));
                    
                    // Western Europe vs Eastern Europe
                    if (timezone.contains("London") || timezone.contains("Paris") || 
                        timezone.contains("Berlin") || timezone.contains("Rome") ||
                        timezone.contains("Madrid") || timezone.contains("Amsterdam")) {
                        segments.add(createSegment("WESTERN_EUROPE_USER", 
                                "Western European user with mature financial markets", 0.85));
                    } else {
                        segments.add(createSegment("EASTERN_EUROPE_USER", 
                                "Eastern European user with emerging financial preferences", 0.85));
                    }
                } else if (timezone.startsWith("Asia/")) {
                    segments.add(createSegment("ASIA_REGION_USER", 
                            "Asian user with digital-first financial preferences", 0.82));
                    
                    // Developed vs Developing Asian markets
                    if (timezone.contains("Tokyo") || timezone.contains("Seoul") || 
                        timezone.contains("Singapore") || timezone.contains("Hong_Kong")) {
                        segments.add(createSegment("DEVELOPED_ASIA_USER", 
                                "User from developed Asian financial markets", 0.85));
                    } else if (timezone.contains("Shanghai") || timezone.contains("Beijing") ||
                               timezone.contains("Mumbai") || timezone.contains("Delhi")) {
                        segments.add(createSegment("EMERGING_ASIA_USER", 
                                "User from emerging Asian markets with mobile-first approach", 0.85));
                    }
                } else if (timezone.startsWith("Africa/")) {
                    segments.add(createSegment("AFRICA_REGION_USER", 
                            "African user with mobile money preferences", 0.82));
                } else if (timezone.startsWith("Australia/") || timezone.startsWith("Pacific/")) {
                    segments.add(createSegment("OCEANIA_REGION_USER", 
                            "Oceania user with developed market preferences", 0.82));
                }
            }
            
            // Age group estimation based on transaction patterns and engagement
            segments.addAll(estimateAgeGroupSegments(userId, metrics));
            
            // Income level estimation based on transaction patterns
            segments.addAll(estimateIncomeSegments(userId, metrics));
            
            // Financial sophistication level based on product usage
            segments.addAll(estimateFinancialSophisticationSegments(userId, metrics));
            
            // Device and technology adoption patterns
            segments.addAll(estimateTechnologyAdoptionSegments(userId, metrics));
            
            // Banking relationship patterns
            segments.addAll(estimateBankingRelationshipSegments(userId, metrics));
            
        } catch (Exception e) {
            log.error("Error in demographic segmentation for user {}: {}", userId, e.getMessage(), e);
        }
        
        return segments;
    }
    
    /**
     * Estimate age group based on transaction patterns and digital behavior
     */
    private List<UserSegment> estimateAgeGroupSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        try {
            // Age estimation based on digital engagement patterns and transaction behavior
            double digitalNativeScore = calculateDigitalNativeScore(metrics);
            BigDecimal avgTransaction = metrics.getAvgTransactionAmount();
            int transactionCount = metrics.getTransactionCount();
            
            if (digitalNativeScore > 0.8 && avgTransaction.compareTo(BigDecimal.valueOf(500)) < 0) {
                segments.add(createSegment("ESTIMATED_GEN_Z", 
                        "Likely Gen Z user (18-26) with mobile-first behavior", 0.75));
            } else if (digitalNativeScore > 0.6 && transactionCount > 10) {
                segments.add(createSegment("ESTIMATED_MILLENNIAL", 
                        "Likely Millennial (27-42) with high digital engagement", 0.75));
            } else if (avgTransaction.compareTo(BigDecimal.valueOf(1000)) > 0 && transactionCount > 5) {
                segments.add(createSegment("ESTIMATED_GEN_X", 
                        "Likely Gen X (43-58) with established financial patterns", 0.75));
            } else if (avgTransaction.compareTo(BigDecimal.valueOf(2000)) > 0 && digitalNativeScore < 0.4) {
                segments.add(createSegment("ESTIMATED_BOOMER", 
                        "Likely Baby Boomer (59+) with traditional financial preferences", 0.75));
            }
        } catch (Exception e) {
            log.error("Error estimating age group for user {}: {}", userId, e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Estimate income level based on transaction patterns
     */
    private List<UserSegment> estimateIncomeSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        try {
            BigDecimal totalVolume = metrics.getTotalTransactionVolume();
            BigDecimal avgTransaction = metrics.getAvgTransactionAmount();
            
            // Income estimation based on spending patterns
            if (totalVolume.compareTo(BigDecimal.valueOf(100000)) > 0 && 
                avgTransaction.compareTo(BigDecimal.valueOf(5000)) > 0) {
                segments.add(createSegment("ESTIMATED_HIGH_INCOME", 
                        "Likely high-income user ($100K+ annually)", 0.70));
            } else if (totalVolume.compareTo(BigDecimal.valueOf(50000)) > 0 && 
                       avgTransaction.compareTo(BigDecimal.valueOf(1000)) > 0) {
                segments.add(createSegment("ESTIMATED_UPPER_MIDDLE_INCOME", 
                        "Likely upper-middle income user ($75K-$100K annually)", 0.70));
            } else if (totalVolume.compareTo(BigDecimal.valueOf(25000)) > 0) {
                segments.add(createSegment("ESTIMATED_MIDDLE_INCOME", 
                        "Likely middle income user ($50K-$75K annually)", 0.70));
            } else if (totalVolume.compareTo(BigDecimal.valueOf(10000)) > 0) {
                segments.add(createSegment("ESTIMATED_LOWER_MIDDLE_INCOME", 
                        "Likely lower-middle income user ($30K-$50K annually)", 0.70));
            } else if (totalVolume.compareTo(BigDecimal.valueOf(1000)) > 0) {
                segments.add(createSegment("ESTIMATED_LOW_INCOME", 
                        "Likely low income user (under $30K annually)", 0.70));
            }
        } catch (Exception e) {
            log.error("Error estimating income for user {}: {}", userId, e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Estimate financial sophistication based on product usage patterns
     */
    private List<UserSegment> estimateFinancialSophisticationSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        try {
            // Financial sophistication based on transaction complexity and patterns
            boolean hasInvestmentActivity = metrics.getInvestmentTransactions() != null && 
                                          metrics.getInvestmentTransactions() > 0;
            boolean hasInternationalTransfers = metrics.getInternationalTransfers() != null && 
                                              metrics.getInternationalTransfers() > 0;
            boolean hasBusinessTransactions = metrics.getBusinessTransactions() != null && 
                                            metrics.getBusinessTransactions() > 0;
            
            int sophisticationScore = 0;
            if (hasInvestmentActivity) sophisticationScore += 3;
            if (hasInternationalTransfers) sophisticationScore += 2;
            if (hasBusinessTransactions) sophisticationScore += 2;
            if (metrics.getTransactionCount() > 50) sophisticationScore += 1;
            if (metrics.getAvgTransactionAmount().compareTo(BigDecimal.valueOf(1000)) > 0) sophisticationScore += 1;
            
            if (sophisticationScore >= 7) {
                segments.add(createSegment("FINANCIALLY_SOPHISTICATED", 
                        "Highly sophisticated financial user with complex needs", 0.80));
            } else if (sophisticationScore >= 4) {
                segments.add(createSegment("MODERATELY_SOPHISTICATED", 
                        "Moderately sophisticated financial user", 0.75));
            } else if (sophisticationScore >= 2) {
                segments.add(createSegment("BASIC_FINANCIAL_USER", 
                        "Basic financial user with simple needs", 0.75));
            } else {
                segments.add(createSegment("FINANCIAL_BEGINNER", 
                        "Beginning financial user needing guidance", 0.75));
            }
        } catch (Exception e) {
            log.error("Error estimating financial sophistication for user {}: {}", userId, e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Estimate technology adoption level
     */
    private List<UserSegment> estimateTechnologyAdoptionSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        try {
            double digitalNativeScore = calculateDigitalNativeScore(metrics);
            
            if (digitalNativeScore > 0.9) {
                segments.add(createSegment("TECH_EARLY_ADOPTER", 
                        "Early technology adopter with cutting-edge preferences", 0.80));
            } else if (digitalNativeScore > 0.7) {
                segments.add(createSegment("TECH_SAVVY", 
                        "Technology-savvy user comfortable with digital tools", 0.75));
            } else if (digitalNativeScore > 0.4) {
                segments.add(createSegment("TECH_MODERATE", 
                        "Moderate technology user with basic digital skills", 0.75));
            } else {
                segments.add(createSegment("TECH_TRADITIONAL", 
                        "Traditional user preferring conventional financial channels", 0.75));
            }
        } catch (Exception e) {
            log.error("Error estimating technology adoption for user {}: {}", userId, e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Estimate banking relationship patterns
     */
    private List<UserSegment> estimateBankingRelationshipSegments(String userId, UserMetrics metrics) {
        List<UserSegment> segments = new ArrayList<>();
        
        try {
            // Banking relationship estimation based on usage patterns
            boolean highEngagement = metrics.getEngagementScore() > 80;
            boolean frequentUser = metrics.getTransactionCount() > 20;
            boolean highValue = metrics.getTotalTransactionVolume().compareTo(BigDecimal.valueOf(10000)) > 0;
            
            if (highEngagement && frequentUser && highValue) {
                segments.add(createSegment("PRIMARY_BANK_RELATIONSHIP", 
                        "User likely using this as primary banking relationship", 0.85));
            } else if (frequentUser || highValue) {
                segments.add(createSegment("SECONDARY_BANK_RELATIONSHIP", 
                        "User likely using this as secondary banking service", 0.80));
            } else {
                segments.add(createSegment("EXPLORATORY_BANK_RELATIONSHIP", 
                        "User exploring or occasionally using financial services", 0.75));
            }
            
            // Multi-banking behavior estimation
            if (metrics.getTransactionCount() > 0 && metrics.getEngagementScore() < 50) {
                segments.add(createSegment("MULTI_BANK_USER", 
                        "Likely user of multiple financial institutions", 0.70));
            }
        } catch (Exception e) {
            log.error("Error estimating banking relationship for user {}: {}", userId, e.getMessage());
        }
        
        return segments;
    }
    
    /**
     * Calculate digital native score based on engagement patterns
     */
    private double calculateDigitalNativeScore(UserMetrics metrics) {
        double score = 0.0;
        
        try {
            // High engagement score indicates digital comfort
            if (metrics.getEngagementScore() > 80) score += 0.3;
            else if (metrics.getEngagementScore() > 60) score += 0.2;
            else if (metrics.getEngagementScore() > 40) score += 0.1;
            
            // Frequent transactions indicate digital adoption
            if (metrics.getTransactionCount() > 50) score += 0.3;
            else if (metrics.getTransactionCount() > 20) score += 0.2;
            else if (metrics.getTransactionCount() > 5) score += 0.1;
            
            // Mobile usage patterns (if available)
            if (metrics.getMobileUsagePercentage() != null) {
                if (metrics.getMobileUsagePercentage() > 80) score += 0.4;
                else if (metrics.getMobileUsagePercentage() > 60) score += 0.3;
                else if (metrics.getMobileUsagePercentage() > 40) score += 0.2;
            } else {
                // Default moderate score if mobile data unavailable
                score += 0.2;
            }
            
        } catch (Exception e) {
            log.error("Error calculating digital native score: {}", e.getMessage());
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Calculate risk score for user
     */
    private double calculateRiskScore(String userId, UserMetrics metrics, List<UserEvent> events) {
        double riskScore = 0.0;
        
        // Error frequency risk
        Map<String, Long> eventCounts = events.stream()
                .collect(Collectors.groupingBy(UserEvent::getEventName, Collectors.counting()));
        
        long errorEvents = eventCounts.getOrDefault("error_occurred", 0L);
        if (events.size() > 0) {
            double errorRate = (double) errorEvents / events.size();
            riskScore += Math.min(errorRate * 2, 0.4); // Max 40% from errors
        }
        
        // Transaction pattern risk
        if (metrics.getTransactionCount() > 0) {
            BigDecimal avgAmount = metrics.getAvgTransactionAmount();
            if (avgAmount.compareTo(BigDecimal.valueOf(10000)) > 0) {
                riskScore += 0.3; // Large transactions increase risk
            }
        }
        
        // Activity pattern risk
        Instant hourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentEvents = events.stream()
                .filter(e -> e.getTimestamp().isAfter(hourAgo))
                .count();
        
        if (recentEvents > 100) {
            riskScore += 0.3; // Unusually high activity
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    /**
     * Create a user segment
     */
    private UserSegment createSegment(String name, String description, double confidence) {
        return UserSegment.builder()
                .segmentName(name)
                .description(description)
                .confidence(confidence)
                .createdAt(Instant.now())
                .build();
    }
    
    /**
     * Update user segments (could be called periodically) - paginated to prevent OOM
     */
    public void updateAllUserSegments() {
        log.info("Starting bulk user segmentation update with pagination");
        
        int pageSize = 100;
        int pageNumber = 0;
        int processed = 0;
        Page<UserMetrics> page;
        
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = metricsRepository.findAll(pageable);
            
            for (UserMetrics metrics : page.getContent()) {
                try {
                    List<UserSegment> segments = getUserSegments(metrics.getUserId());
                    // Store segments (this would typically involve persisting to a segments table)
                    log.debug("Updated segments for user {}: {} segments", 
                            metrics.getUserId(), segments.size());
                    processed++;
                    
                    if (processed % 100 == 0) {
                        log.info("Processed {} users for segmentation", processed);
                    }
                } catch (Exception e) {
                    log.error("Error updating segments for user {}: {}", 
                            metrics.getUserId(), e.getMessage(), e);
                }
            }
            
            pageNumber++;
        } while (page.hasNext());
        
        log.info("Completed bulk user segmentation update. Processed {} users", processed);
    }
    
    /**
     * Get segment recommendations for marketing campaigns
     */
    public Map<String, List<String>> getSegmentRecommendations() {
        Map<String, List<String>> recommendations = new HashMap<>();
        
        recommendations.put("HIGH_VALUE_CUSTOMER", Arrays.asList(
                "Offer premium services and features",
                "Provide dedicated customer support",
                "Invite to VIP programs"
        ));
        
        recommendations.put("DISENGAGED", Arrays.asList(
                "Send re-engagement campaigns",
                "Offer special promotions",
                "Survey for feedback and improvements"
        ));
        
        recommendations.put("NEW_USER", Arrays.asList(
                "Provide onboarding tutorials",
                "Offer welcome bonuses",
                "Guide through first transaction"
        ));
        
        recommendations.put("HIGH_RISK_USER", Arrays.asList(
                "Implement additional security measures",
                "Monitor transactions closely",
                "Require additional verification"
        ));
        
        return recommendations;
    }
}