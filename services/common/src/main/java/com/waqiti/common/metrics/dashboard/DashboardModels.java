package com.waqiti.common.metrics.dashboard;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data models for business metrics dashboard components
 */

@Data
@Builder
class TransactionMetrics {
    private double totalTransactions;
    private double successfulTransactions;
    private double failedTransactions;
    private double averageProcessingTime;
    private Map<String, Double> transactionsByType;
    private Map<String, Double> transactionsByCurrency;
    
    public double getSuccessRate() {
        return totalTransactions > 0 ? (successfulTransactions / totalTransactions) * 100 : 0;
    }
    
    public double getFailureRate() {
        return totalTransactions > 0 ? (failedTransactions / totalTransactions) * 100 : 0;
    }
}

@Data
@Builder
class UserMetrics {
    private double totalUsers;
    private double activeUsers;
    private double newRegistrations;
    private Map<String, Double> usersByRegion;
    private double verificationRate;
    
    public double getActiveUserRate() {
        return totalUsers > 0 ? (activeUsers / totalUsers) * 100 : 0;
    }
}

@Data
@Builder
class PaymentMetrics {
    private double totalPayments;
    private double successfulPayments;
    private double failedPayments;
    private double averagePaymentAmount;
    private Map<String, Double> paymentMethodDistribution;
    
    public double getPaymentSuccessRate() {
        return totalPayments > 0 ? (successfulPayments / totalPayments) * 100 : 0;
    }
}

@Data
@Builder
class FraudMetrics {
    private double totalFraudChecks;
    private double blockedTransactions;
    private double flaggedTransactions;
    private double averageRiskScore;
    private Map<String, Double> fraudByRiskLevel;
    private double averageDetectionTime;
    
    public double getFraudDetectionRate() {
        return totalFraudChecks > 0 ? ((blockedTransactions + flaggedTransactions) / totalFraudChecks) * 100 : 0;
    }
}

@Data
@Builder
class KycMetrics {
    private double totalVerifications;
    private double successfulVerifications;
    private double rejectedVerifications;
    private double averageConfidenceScore;
    private Map<String, Double> verificationsByProvider;
    private double averageProcessingTime;
    
    public double getVerificationSuccessRate() {
        return totalVerifications > 0 ? (successfulVerifications / totalVerifications) * 100 : 0;
    }
}

@Data
@Builder
class SystemMetrics {
    private double jvmMemoryUsage;
    private double cpuUsage;
    private double diskUsage;
    private double activeConnections;
    private double threadPoolUtilization;
}

@Data
@Builder
class ApiMetrics {
    private double totalRequests;
    private double successfulRequests;
    private double clientErrors;
    private double serverErrors;
    private double averageResponseTime;
    private Map<String, Double> requestsByEndpoint;
    
    public double getErrorRate() {
        return totalRequests > 0 ? ((clientErrors + serverErrors) / totalRequests) * 100 : 0;
    }
}

@Data
@Builder
class CacheMetrics {
    private double totalOperations;
    private double cacheHits;
    private double cacheMisses;
    private double hitRate;
    private Map<String, Double> operationsByCache;
}

@Data
@Builder
class ExternalServiceMetrics {
    private double totalCalls;
    private double successfulCalls;
    private double failedCalls;
    private double averageResponseTime;
    private double circuitBreakerTriggers;
    private double retries;
    private Map<String, Double> callsByService;
    
    public double getServiceSuccessRate() {
        return totalCalls > 0 ? (successfulCalls / totalCalls) * 100 : 0;
    }
}

@Data
@Builder
class RealTimeMetrics {
    private Instant timestamp;
    private double activeTransactions;
    private double totalUsers;
    private double currentTps;
    private double errorRate;
    private double averageResponseTime;
    private double cacheHitRate;
    private double fraudDetectionRate;
    private String systemHealth;
    private String errorMessage;
    
    public static RealTimeMetrics disabled() {
        return RealTimeMetrics.builder()
            .timestamp(Instant.now())
            .systemHealth("DISABLED")
            .build();
    }
    
    public static RealTimeMetrics error(String errorMessage) {
        return RealTimeMetrics.builder()
            .timestamp(Instant.now())
            .errorMessage(errorMessage)
            .systemHealth("ERROR")
            .build();
    }
    
    public boolean hasError() {
        return errorMessage != null;
    }
}

@Data
@Builder
class FinancialDashboard {
    private boolean enabled;
    private Duration timeWindow;
    private Instant generatedAt;
    private double totalTransactionValue;
    private long totalTransactionCount;
    private Map<String, Double> transactionVolumeByCurrency;
    private Map<String, Long> transactionCountByType;
    private double averageTransactionValue;
    private double successRate;
    private Map<String, Long> failureReasons;
    private List<String> topCurrencies;
    private RevenueMetrics revenueMetrics;
    private String errorMessage;
    
    public static FinancialDashboard disabled() {
        return FinancialDashboard.builder()
            .enabled(false)
            .generatedAt(Instant.now())
            .build();
    }
    
    public static FinancialDashboard error(String errorMessage) {
        return FinancialDashboard.builder()
            .enabled(true)
            .generatedAt(Instant.now())
            .errorMessage(errorMessage)
            .build();
    }
}

@Data
@Builder
class OperationalDashboard {
    private boolean enabled;
    private Instant generatedAt;
    private ServiceHealth serviceHealth;
    private DatabaseHealthMetrics databaseMetrics;
    private CachePerformanceMetrics cachePerformance;
    private ExternalServiceHealth externalServiceHealth;
    private SystemResourceUsage systemResourceUsage;
    private AlertSummary alertSummary;
    private UptimeMetrics uptimeMetrics;
    private String errorMessage;
    
    public static OperationalDashboard disabled() {
        return OperationalDashboard.builder()
            .enabled(false)
            .generatedAt(Instant.now())
            .build();
    }
    
    public static OperationalDashboard error(String errorMessage) {
        return OperationalDashboard.builder()
            .enabled(true)
            .generatedAt(Instant.now())
            .errorMessage(errorMessage)
            .build();
    }
}

@Data
@Builder
class RevenueMetrics {
    private double totalRevenue;
    private double revenueGrowthRate;
    private double averageRevenuePerUser;
}

@Data
@Builder
class ServiceHealth {
    private String overallStatus;
    private String apiHealth;
    private String databaseHealth;
    private String cacheHealth;
}

@Data
@Builder
class DatabaseHealthMetrics {
    private long activeConnections;
    private double averageQueryTime;
    private long slowQueries;
    private double connectionPoolUtilization;
}

@Data
@Builder
class CachePerformanceMetrics {
    private double hitRate;
    private double averageResponseTime;
    private double evictionRate;
    private double memoryUtilization;
}

@Data
@Builder
class ExternalServiceHealth {
    private String overallHealth;
    private Map<String, String> serviceStatus;
}

@Data
@Builder
class SystemResourceUsage {
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private double networkIO;
}

@Data
@Builder
class AlertSummary {
    private long criticalAlerts;
    private long warningAlerts;
    private long infoAlerts;
    private List<String> recentAlerts;
}

@Data
@Builder
class UptimeMetrics {
    private Duration uptime;
    private double availability;
    private Duration mttr; // Mean time to recovery
    private Duration mtbf; // Mean time between failures
}