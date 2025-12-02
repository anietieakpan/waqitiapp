package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comprehensive observability dashboard aggregating all platform metrics
 * Provides real-time insights into system health, performance, and business KPIs
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservabilityDashboard {
    
    private LocalDateTime timestamp;
    private String dashboardVersion;
    private long refreshIntervalSeconds;
    
    // Core metric summaries
    private BusinessMetricsSummary businessMetrics;
    private SecurityMetricsSummary securityMetrics;
    private PerformanceMetricsSummary performanceMetrics;
    private SystemHealthSummary systemHealth;
    
    // Aggregated insights
    private OverallSystemStatus overallStatus;
    private List<CriticalAlert> criticalAlerts;
    private List<TrendAnalysis> keyTrends;
    private Map<String, ServiceHealth> serviceHealthMap;
    private PlatformInsights insights;
    
    // SLA and compliance
    private SLAComplianceSummary slaCompliance;
    private List<ComplianceViolation> complianceIssues;
    
    /**
     * Calculate overall system status based on all metrics
     */
    public OverallSystemStatus calculateOverallStatus() {
        if (overallStatus != null) {
            return overallStatus;
        }
        
        try {
            // Weighted scoring system
            double businessScore = calculateBusinessHealthScore();
            double securityScore = calculateSecurityHealthScore();
            double performanceScore = calculatePerformanceHealthScore();
            double systemScore = calculateSystemHealthScore();
            
            // Weights: Performance (30%), Security (25%), Business (25%), System (20%)
            double overallScore = (performanceScore * 0.30) + 
                                 (securityScore * 0.25) + 
                                 (businessScore * 0.25) + 
                                 (systemScore * 0.20);
            
            SystemStatusLevel level;
            String description;
            
            if (overallScore >= 95.0) {
                level = SystemStatusLevel.OPTIMAL;
                description = "All systems operating at optimal performance";
            } else if (overallScore >= 85.0) {
                level = SystemStatusLevel.HEALTHY;
                description = "Systems operating normally with minor issues";
            } else if (overallScore >= 70.0) {
                level = SystemStatusLevel.DEGRADED;
                description = "Performance degradation detected - attention required";
            } else if (overallScore >= 50.0) {
                level = SystemStatusLevel.CRITICAL;
                description = "Critical issues affecting system performance";
            } else {
                level = SystemStatusLevel.EMERGENCY;
                description = "Emergency - immediate intervention required";
            }
            
            this.overallStatus = OverallSystemStatus.builder()
                .level(level)
                .score(overallScore)
                .description(description)
                .lastUpdated(LocalDateTime.now())
                .affectedServices(getAffectedServices())
                .recommendedActions(generateRecommendedActions(level))
                .build();
                
        } catch (Exception e) {
            log.error("Error calculating overall system status", e);
            this.overallStatus = OverallSystemStatus.builder()
                .level(SystemStatusLevel.UNKNOWN)
                .score(0.0)
                .description("Unable to determine system status due to monitoring issues")
                .lastUpdated(LocalDateTime.now())
                .build();
        }
        
        return overallStatus;
    }
    
    /**
     * Get top priority alerts requiring immediate attention
     */
    public List<CriticalAlert> getTopPriorityAlerts() {
        if (criticalAlerts == null) {
            return List.of();
        }
        
        return criticalAlerts.stream()
            .filter(alert -> alert.getSeverity() == AlertSeverity.CRITICAL || 
                           alert.getSeverity() == AlertSeverity.EMERGENCY)
            .sorted((a1, a2) -> {
                // Sort by severity first, then by timestamp (newest first)
                int severityCompare = a2.getSeverity().getPriority() - a1.getSeverity().getPriority();
                if (severityCompare != 0) return severityCompare;
                return a2.getTimestamp().compareTo(a1.getTimestamp());
            })
            .limit(10)
            .collect(Collectors.toList());
    }
    
    /**
     * Generate actionable insights based on current metrics
     */
    public PlatformInsights generateInsights() {
        if (insights != null) {
            return insights;
        }
        
        try {
            List<String> recommendations = generateRecommendations();
            List<String> optimizations = identifyOptimizationOpportunities();
            List<String> riskFactors = identifyRiskFactors();
            
            this.insights = PlatformInsights.builder()
                .generatedAt(LocalDateTime.now())
                .recommendations(recommendations)
                .optimizationOpportunities(optimizations)
                .riskFactors(riskFactors)
                .predictedTrends(predictUpcomingTrends())
                .costOptimizations(identifyCostOptimizations())
                .securityRecommendations(generateSecurityRecommendations())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating platform insights", e);
            this.insights = PlatformInsights.empty();
        }
        
        return insights;
    }
    
    /**
     * Check if system is operating within acceptable parameters
     */
    public boolean isSystemHealthy() {
        OverallSystemStatus status = calculateOverallStatus();
        return status.getLevel() == SystemStatusLevel.OPTIMAL || 
               status.getLevel() == SystemStatusLevel.HEALTHY;
    }
    
    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAttention() {
        OverallSystemStatus status = calculateOverallStatus();
        return status.getLevel() == SystemStatusLevel.CRITICAL || 
               status.getLevel() == SystemStatusLevel.EMERGENCY ||
               !getTopPriorityAlerts().isEmpty();
    }
    
    private double calculateBusinessHealthScore() {
        if (businessMetrics == null) return 50.0;
        
        double score = 100.0;
        
        // Payment success rate impact (40% weight)
        if (businessMetrics.getPendingTransactions() > 1000) score -= 15;
        if (businessMetrics.getTotalPlatformBalance() < 100000) score -= 10;
        
        // Active users trend (30% weight)
        if (businessMetrics.getActiveUsers() < 1000) score -= 20;
        
        // Transaction volume health (30% weight)
        // Add more business-specific scoring logic
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    private double calculateSecurityHealthScore() {
        if (securityMetrics == null) return 50.0;
        
        double score = 100.0;
        
        // Security alerts impact
        long securityAlerts = securityMetrics.getActiveSecurityAlerts();
        if (securityAlerts > 50) score -= 40;
        else if (securityAlerts > 20) score -= 20;
        else if (securityAlerts > 10) score -= 10;
        
        // Authentication failure rate impact
        double authFailureRate = securityMetrics.getAuthenticationFailureRate();
        if (authFailureRate > 15.0) score -= 30;
        else if (authFailureRate > 10.0) score -= 15;
        else if (authFailureRate > 5.0) score -= 5;
        
        // Blocked IPs and suspended accounts
        if (securityMetrics.getBlockedIPAddresses() > 1000) score -= 10;
        if (securityMetrics.getSuspendedAccounts() > 100) score -= 10;
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    private double calculatePerformanceHealthScore() {
        if (performanceMetrics == null) return 50.0;
        
        double score = 100.0;
        
        // Response time impact (40% weight)
        double avgResponseTime = performanceMetrics.getAverageResponseTime();
        if (avgResponseTime > 5000) score -= 40;
        else if (avgResponseTime > 2000) score -= 20;
        else if (avgResponseTime > 1000) score -= 10;
        
        // Error rate impact (35% weight)
        double errorRate = performanceMetrics.getErrorRate();
        if (errorRate > 10.0) score -= 35;
        else if (errorRate > 5.0) score -= 20;
        else if (errorRate > 2.0) score -= 10;
        
        // System availability (25% weight)
        double availability = performanceMetrics.getSystemAvailability();
        if (availability < 99.0) score -= 25;
        else if (availability < 99.5) score -= 15;
        else if (availability < 99.9) score -= 5;
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    private double calculateSystemHealthScore() {
        if (systemHealth == null) return 50.0;
        
        double score = 100.0;
        
        // System component health
        if (!"UP".equals(systemHealth.getDatabase())) score -= 30;
        if (!"UP".equals(systemHealth.getRedis())) score -= 20;
        if (!"UP".equals(systemHealth.getMessageQueue())) score -= 20;
        if (!"UP".equals(systemHealth.getExternalServices())) score -= 15;
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    private List<String> getAffectedServices() {
        return serviceHealthMap != null ? 
            serviceHealthMap.entrySet().stream()
                .filter(entry -> entry.getValue().getStatus() != ServiceStatus.HEALTHY)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()) : List.of();
    }
    
    private List<String> generateRecommendedActions(SystemStatusLevel level) {
        return switch (level) {
            case OPTIMAL -> List.of("Continue monitoring", "Consider performance optimizations");
            case HEALTHY -> List.of("Monitor trending metrics", "Review capacity planning");
            case DEGRADED -> List.of("Investigate performance issues", "Scale resources if needed", "Review error logs");
            case CRITICAL -> List.of("Immediate investigation required", "Scale up resources", "Activate incident response", "Notify stakeholders");
            case EMERGENCY -> List.of("EMERGENCY: All hands on deck", "Execute disaster recovery plan", "Notify executive team", "Consider system maintenance");
            case UNKNOWN -> List.of("Investigate monitoring system issues", "Restore observability", "Manual system checks");
        };
    }
    
    private List<String> generateRecommendations() {
        List<String> recommendations = List.of();
        
        // Performance recommendations
        if (performanceMetrics != null && performanceMetrics.getAverageResponseTime() > 1000) {
            recommendations.add("Consider implementing response caching for frequently accessed endpoints");
            recommendations.add("Review database query optimization opportunities");
        }
        
        // Security recommendations
        if (securityMetrics != null && securityMetrics.getAuthenticationFailureRate() > 5.0) {
            recommendations.add("Implement additional rate limiting on authentication endpoints");
            recommendations.add("Consider implementing CAPTCHA for repeated failed attempts");
        }
        
        // Business recommendations
        if (businessMetrics != null && businessMetrics.getPendingTransactions() > 500) {
            recommendations.add("Review payment processing pipeline for bottlenecks");
            recommendations.add("Consider implementing transaction prioritization");
        }
        
        return recommendations;
    }
    
    private List<String> identifyOptimizationOpportunities() {
        return List.of(
            "Database connection pool optimization",
            "Redis cache hit ratio improvement",
            "API response compression implementation",
            "Background job processing optimization"
        );
    }
    
    private List<String> identifyRiskFactors() {
        return List.of(
            "High error rate during peak hours",
            "Increasing authentication failures",
            "Database connection pool saturation",
            "External service dependency timeouts"
        );
    }
    
    private List<String> predictUpcomingTrends() {
        return List.of(
            "Transaction volume expected to increase 15% next week",
            "Peak usage shifting to earlier hours",
            "Mobile traffic growing faster than web traffic"
        );
    }
    
    private List<String> identifyCostOptimizations() {
        return List.of(
            "Right-size oversized compute instances",
            "Implement data archiving for old transactions",
            "Optimize cloud storage usage patterns"
        );
    }
    
    private List<String> generateSecurityRecommendations() {
        return List.of(
            "Review and rotate API keys older than 90 days",
            "Update security scanning rules",
            "Implement additional fraud detection patterns"
        );
    }
    
    // Supporting enums and classes
    public enum SystemStatusLevel {
        OPTIMAL(5, "Optimal"),
        HEALTHY(4, "Healthy"), 
        DEGRADED(3, "Degraded"),
        CRITICAL(2, "Critical"),
        EMERGENCY(1, "Emergency"),
        UNKNOWN(0, "Unknown");
        
        private final int priority;
        private final String displayName;
        
        SystemStatusLevel(int priority, String displayName) {
            this.priority = priority;
            this.displayName = displayName;
        }
        
        public int getPriority() { return priority; }
        public String getDisplayName() { return displayName; }
    }
    
    @Data
    @Builder
    public static class OverallSystemStatus {
        private SystemStatusLevel level;
        private double score;
        private String description;
        private LocalDateTime lastUpdated;
        private List<String> affectedServices;
        private List<String> recommendedActions;
    }
    
    @Data
    @Builder
    public static class PlatformInsights {
        private LocalDateTime generatedAt;
        private List<String> recommendations;
        private List<String> optimizationOpportunities;
        private List<String> riskFactors;
        private List<String> predictedTrends;
        private List<String> costOptimizations;
        private List<String> securityRecommendations;
        
        public static PlatformInsights empty() {
            return PlatformInsights.builder()
                .generatedAt(LocalDateTime.now())
                .recommendations(List.of())
                .optimizationOpportunities(List.of())
                .riskFactors(List.of())
                .predictedTrends(List.of())
                .costOptimizations(List.of())
                .securityRecommendations(List.of())
                .build();
        }
    }
}