package com.waqiti.analytics.service;

import com.waqiti.analytics.model.*;
import com.waqiti.analytics.repository.UserEventRepository;
import com.waqiti.analytics.repository.UserMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Predictive analytics service using machine learning models
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictiveAnalyticsService {
    
    private final UserEventRepository eventRepository;
    private final UserMetricsRepository metricsRepository;
    
    /**
     * Get predictions for a user
     */
    public UserPredictions getPredictions(String userId, UserMetrics metrics, List<UserEvent> events) {
        log.debug("Generating predictions for user: {}", userId);
        
        return UserPredictions.builder()
                .userId(userId)
                .churnRisk(calculateChurnRisk(userId))
                .nextTransactionProbability(calculateNextTransactionProbability(userId, events))
                .lifetimeValue(predictLifetimeValue(userId, metrics))
                .nextBestAction(predictNextBestAction(userId, events))
                .engagementTrend(predictEngagementTrend(userId, events))
                .conversionProbability(calculateConversionProbability(userId, events))
                .riskScore(calculateRiskScore(userId, events))
                .predictedSegments(predictUserSegments(userId, metrics))
                .recommendations(generatePredictiveRecommendations(userId, metrics, events))
                .confidence(calculatePredictionConfidence(userId, events))
                .generatedAt(Instant.now())
                .build();
    }
    
    /**
     * Calculate churn risk for a user
     */
    public double calculateChurnRisk(String userId) {
        log.debug("Calculating churn risk for user: {}", userId);
        
        try {
            // Get recent activity
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            List<UserEvent> recentEvents = eventRepository.findByUserIdAndTimestampBetween(
                    userId, thirtyDaysAgo, Instant.now());
            
            if (recentEvents.isEmpty()) {
                return 0.95; // High churn risk if no recent activity
            }
            
            // Calculate various risk factors
            double inactivityRisk = calculateInactivityRisk(userId, recentEvents);
            double engagementRisk = calculateEngagementRisk(userId, recentEvents);
            double transactionRisk = calculateTransactionRisk(userId, recentEvents);
            double sessionRisk = calculateSessionRisk(userId);
            
            // Weighted average of risk factors
            double churnRisk = (inactivityRisk * 0.3) + 
                              (engagementRisk * 0.25) + 
                              (transactionRisk * 0.25) + 
                              (sessionRisk * 0.2);
            
            return Math.min(Math.max(churnRisk, 0.0), 1.0); // Clamp between 0 and 1
            
        } catch (Exception e) {
            log.error("Error calculating churn risk for user {}: {}", userId, e.getMessage(), e);
            return 0.5; // Default medium risk
        }
    }
    
    /**
     * Calculate probability of next transaction
     */
    private double calculateNextTransactionProbability(String userId, List<UserEvent> events) {
        List<UserEvent> transactionEvents = events.stream()
                .filter(e -> e.getEventName().contains("transaction"))
                .collect(Collectors.toList());
        
        if (transactionEvents.isEmpty()) {
            return 0.1; // Low probability if no transaction history
        }
        
        // Calculate transaction frequency
        long daysSinceFirstTransaction = ChronoUnit.DAYS.between(
                transactionEvents.get(transactionEvents.size() - 1).getTimestamp(),
                Instant.now());
        
        double avgDaysBetweenTransactions = (double) daysSinceFirstTransaction / transactionEvents.size();
        
        // Calculate days since last transaction
        Instant lastTransaction = transactionEvents.get(0).getTimestamp();
        long daysSinceLastTransaction = ChronoUnit.DAYS.between(lastTransaction, Instant.now());
        
        // Probability decreases as time since last transaction increases
        if (avgDaysBetweenTransactions == 0) {
            return 0.9; // High probability if very frequent transactions
        }
        
        double probability = Math.max(0.1, 1.0 - (daysSinceLastTransaction / (avgDaysBetweenTransactions * 2)));
        return Math.min(probability, 0.95);
    }
    
    /**
     * Predict lifetime value
     */
    private BigDecimal predictLifetimeValue(String userId, UserMetrics metrics) {
        if (metrics.getTransactionCount() == 0) {
            return BigDecimal.ZERO;
        }
        
        // Simple LTV calculation: Average Transaction Value * Transaction Frequency * Predicted Lifespan
        BigDecimal avgTransactionValue = metrics.getAvgTransactionAmount();
        double transactionFrequency = calculateTransactionFrequency(userId);
        double predictedLifespan = predictUserLifespan(userId);
        
        return avgTransactionValue
                .multiply(BigDecimal.valueOf(transactionFrequency))
                .multiply(BigDecimal.valueOf(predictedLifespan))
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Predict next best action for user
     */
    private String predictNextBestAction(String userId, List<UserEvent> events) {
        // Analyze user behavior patterns to suggest next action
        Map<String, Long> eventCounts = events.stream()
                .collect(Collectors.groupingBy(UserEvent::getEventName, Collectors.counting()));
        
        // Check if user has low engagement
        long totalEvents = events.size();
        if (totalEvents < 10) {
            return "ONBOARDING_TUTORIAL";
        }
        
        // Check transaction behavior
        long transactionEvents = eventCounts.getOrDefault("transaction_completed", 0L);
        if (transactionEvents == 0 && totalEvents > 20) {
            return "FIRST_TRANSACTION_INCENTIVE";
        }
        
        // Check login frequency
        long loginEvents = eventCounts.getOrDefault("login", 0L);
        if (loginEvents < 5 && totalEvents > 15) {
            return "RE_ENGAGEMENT_CAMPAIGN";
        }
        
        // Check feature usage
        long featureEvents = eventCounts.getOrDefault("feature_used", 0L);
        if (featureEvents < 3) {
            return "FEATURE_DISCOVERY";
        }
        
        return "PERSONALIZED_OFFER";
    }
    
    /**
     * Predict engagement trend
     */
    private String predictEngagementTrend(String userId, List<UserEvent> events) {
        if (events.size() < 10) {
            return "INSUFFICIENT_DATA";
        }
        
        // Analyze engagement over time periods
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Instant twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);
        
        long recentWeekEvents = events.stream()
                .filter(e -> e.getTimestamp().isAfter(weekAgo))
                .count();
        
        long previousWeekEvents = events.stream()
                .filter(e -> e.getTimestamp().isAfter(twoWeeksAgo) && e.getTimestamp().isBefore(weekAgo))
                .count();
        
        if (previousWeekEvents == 0) {
            return recentWeekEvents > 0 ? "INCREASING" : "STABLE";
        }
        
        double changeRatio = (double) recentWeekEvents / previousWeekEvents;
        
        if (changeRatio > 1.2) {
            return "INCREASING";
        } else if (changeRatio < 0.8) {
            return "DECREASING";
        } else {
            return "STABLE";
        }
    }
    
    /**
     * Calculate conversion probability
     */
    private double calculateConversionProbability(String userId, List<UserEvent> events) {
        // Define conversion as completing first transaction
        boolean hasTransacted = events.stream()
                .anyMatch(e -> e.getEventName().contains("transaction"));
        
        if (hasTransacted) {
            return 1.0; // Already converted
        }
        
        // Analyze behavior leading to conversion
        long screenViews = events.stream()
                .filter(e -> "screen_view".equals(e.getEventName()))
                .count();
        
        long featureUsage = events.stream()
                .filter(e -> "feature_used".equals(e.getEventName()))
                .count();
        
        long buttonClicks = events.stream()
                .filter(e -> "button_click".equals(e.getEventName()))
                .count();
        
        // Simple conversion probability model
        double probability = 0.1; // Base probability
        probability += Math.min(screenViews * 0.02, 0.3); // Screen views contribute up to 30%
        probability += Math.min(featureUsage * 0.05, 0.4); // Feature usage up to 40%
        probability += Math.min(buttonClicks * 0.01, 0.2); // Button clicks up to 20%
        
        return Math.min(probability, 0.95);
    }
    
    /**
     * Calculate risk score
     */
    private double calculateRiskScore(String userId, List<UserEvent> events) {
        double riskScore = 0.0;
        
        // Check for error events
        long errorEvents = events.stream()
                .filter(e -> "error_occurred".equals(e.getEventName()))
                .count();
        
        riskScore += Math.min(errorEvents * 0.1, 0.3);
        
        // Check for rapid activity (potential fraud)
        long recentEvents = events.stream()
                .filter(e -> e.getTimestamp().isAfter(Instant.now().minus(1, ChronoUnit.HOURS)))
                .count();
        
        if (recentEvents > 100) {
            riskScore += 0.4;
        }
        
        // Check for unusual transaction patterns
        List<UserEvent> transactionEvents = events.stream()
                .filter(e -> e.getEventName().contains("transaction"))
                .collect(Collectors.toList());
        
        if (!transactionEvents.isEmpty()) {
            // Check for large amounts
            boolean hasLargeTransactions = transactionEvents.stream()
                    .anyMatch(e -> {
                        if (e.getEventProperties().containsKey("amount")) {
                            try {
                                BigDecimal amount = new BigDecimal(e.getEventProperties().get("amount").toString());
                                return amount.compareTo(BigDecimal.valueOf(10000)) > 0;
                            } catch (NumberFormatException ex) {
                                return false;
                            }
                        }
                        return false;
                    });
            
            if (hasLargeTransactions) {
                riskScore += 0.3;
            }
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    /**
     * Predict user segments
     */
    private List<String> predictUserSegments(String userId, UserMetrics metrics) {
        List<String> segments = new ArrayList<>();
        
        // Transaction-based segments
        if (metrics.getTransactionCount() > 50) {
            segments.add("HIGH_VOLUME_TRANSACTOR");
        } else if (metrics.getTransactionCount() > 10) {
            segments.add("REGULAR_TRANSACTOR");
        } else if (metrics.getTransactionCount() > 0) {
            segments.add("OCCASIONAL_TRANSACTOR");
        } else {
            segments.add("NON_TRANSACTOR");
        }
        
        // Engagement-based segments
        if (metrics.getEngagementScore() > 80) {
            segments.add("HIGH_ENGAGEMENT");
        } else if (metrics.getEngagementScore() > 50) {
            segments.add("MEDIUM_ENGAGEMENT");
        } else {
            segments.add("LOW_ENGAGEMENT");
        }
        
        // Value-based segments
        BigDecimal avgTransactionAmount = metrics.getAvgTransactionAmount();
        if (avgTransactionAmount.compareTo(BigDecimal.valueOf(1000)) > 0) {
            segments.add("HIGH_VALUE");
        } else if (avgTransactionAmount.compareTo(BigDecimal.valueOf(100)) > 0) {
            segments.add("MEDIUM_VALUE");
        } else {
            segments.add("LOW_VALUE");
        }
        
        return segments;
    }
    
    /**
     * Generate predictive recommendations
     */
    private List<String> generatePredictiveRecommendations(String userId, UserMetrics metrics, List<UserEvent> events) {
        List<String> recommendations = new ArrayList<>();
        
        // Churn risk recommendations
        double churnRisk = calculateChurnRisk(userId);
        if (churnRisk > 0.7) {
            recommendations.add("URGENT: Implement retention campaign");
            recommendations.add("Offer personalized incentives");
        } else if (churnRisk > 0.4) {
            recommendations.add("Monitor user engagement closely");
            recommendations.add("Send re-engagement notifications");
        }
        
        // Engagement recommendations
        if (metrics.getEngagementScore() < 30) {
            recommendations.add("Focus on user onboarding");
            recommendations.add("Simplify user interface");
        }
        
        // Transaction recommendations
        if (metrics.getTransactionCount() == 0 && events.size() > 20) {
            recommendations.add("Offer first transaction bonus");
            recommendations.add("Provide transaction tutorials");
        }
        
        return recommendations;
    }
    
    /**
     * Calculate prediction confidence
     */
    private double calculatePredictionConfidence(String userId, List<UserEvent> events) {
        // Confidence based on data availability
        if (events.size() < 10) {
            return 0.3;
        } else if (events.size() < 50) {
            return 0.6;
        } else if (events.size() < 200) {
            return 0.8;
        } else {
            return 0.95;
        }
    }
    
    // Helper methods
    
    private double calculateInactivityRisk(String userId, List<UserEvent> events) {
        if (events.isEmpty()) return 1.0;
        
        Instant lastActivity = events.get(0).getTimestamp();
        long daysSinceLastActivity = ChronoUnit.DAYS.between(lastActivity, Instant.now());
        
        return Math.min(daysSinceLastActivity / 30.0, 1.0);
    }
    
    private double calculateEngagementRisk(String userId, List<UserEvent> events) {
        if (events.isEmpty()) return 1.0;
        
        // Calculate engagement based on event frequency
        long totalEvents = events.size();
        long daysSinceFirstEvent = ChronoUnit.DAYS.between(
                events.get(events.size() - 1).getTimestamp(), Instant.now());
        
        double eventsPerDay = daysSinceFirstEvent > 0 ? (double) totalEvents / daysSinceFirstEvent : totalEvents;
        
        return Math.max(0.0, 1.0 - (eventsPerDay / 10.0)); // 10 events per day = low risk
    }
    
    private double calculateTransactionRisk(String userId, List<UserEvent> events) {
        long transactionCount = events.stream()
                .filter(e -> e.getEventName().contains("transaction"))
                .count();
        
        if (transactionCount == 0) return 0.8; // High risk if no transactions
        
        // Calculate days since last transaction
        Optional<UserEvent> lastTransaction = events.stream()
                .filter(e -> e.getEventName().contains("transaction"))
                .findFirst();
        
        if (lastTransaction.isPresent()) {
            long daysSinceLastTransaction = ChronoUnit.DAYS.between(
                    lastTransaction.get().getTimestamp(), Instant.now());
            return Math.min(daysSinceLastTransaction / 60.0, 1.0); // 60 days = high risk
        }
        
        return 0.5;
    }
    
    private double calculateSessionRisk(String userId) {
        // This would analyze session patterns
        // For now, return medium risk
        return 0.5;
    }
    
    private double calculateTransactionFrequency(String userId) {
        // Calculate transaction frequency (transactions per month)
        return 2.0; // Default
    }
    
    private double predictUserLifespan(String userId) {
        // Predict how long user will remain active (in months)
        return 24.0; // Default 2 years
    }
}