package com.waqiti.payment.cash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-Grade Cash Deposit System Metrics DTO
 * 
 * Comprehensive metrics and performance data for cash deposit systems including:
 * - Real-time transaction volumes and success rates
 * - Network performance and availability metrics
 * - Error rates and failure analysis
 * - Settlement timing and reconciliation status
 * - Resource utilization and capacity metrics
 * - Compliance and audit trail data
 * 
 * @author Waqiti Cash Management Team
 * @version 2.1.0
 * @since 2025-01-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositSystemMetricsDto {
    
    // Transaction Volume Metrics
    private Long totalTransactions;
    private Long successfulTransactions;
    private Long failedTransactions;
    private BigDecimal totalAmount;
    private BigDecimal averageTransactionAmount;
    private Double successRate;
    private Double errorRate;
    
    // Performance Metrics
    private Double averageProcessingTimeMs;
    private Double p95ProcessingTimeMs;
    private Double p99ProcessingTimeMs;
    private Long maxProcessingTimeMs;
    private Long minProcessingTimeMs;
    
    // Network Metrics
    private Integer activeNetworks;
    private Integer healthyNetworks;
    private Integer degradedNetworks;
    private Integer unavailableNetworks;
    private Map<String, Double> networkAvailability;
    private Map<String, Long> networkTransactionCounts;
    
    // Settlement Metrics
    private Long pendingSettlements;
    private Long completedSettlements;
    private Long failedSettlements;
    private Double averageSettlementTimeHours;
    private BigDecimal pendingSettlementAmount;
    
    // Error Analysis
    private Map<String, Long> errorTypes;
    private Map<String, Long> errorsByNetwork;
    private String mostCommonError;
    private Double errorTrend;
    
    // Capacity and Load Metrics
    private Double currentLoad;
    private Double maxCapacity;
    private Double utilizationPercentage;
    private Long queueDepth;
    private Long peakTransactionsPerHour;
    private Long currentTransactionsPerHour;
    
    // Business Metrics
    private BigDecimal totalFees;
    private BigDecimal averageFee;
    private Long uniqueCustomers;
    private Long repeatCustomers;
    private Double customerRetentionRate;
    
    // Compliance Metrics
    private Long complianceChecks;
    private Long passedChecks;
    private Long flaggedTransactions;
    private Long suspiciousTransactions;
    private Double complianceRate;
    
    // Geographic Distribution
    private Map<String, Long> transactionsByCountry;
    private Map<String, Long> transactionsByRegion;
    private String topPerformingRegion;
    
    // Time-based Metrics
    private LocalDateTime metricsTimestamp;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String timePeriod; // hourly, daily, weekly, monthly
    
    // Trend Analysis
    private Double volumeTrend;
    private Double successRateTrend;
    private Double performanceTrend;
    private String trendDirection; // IMPROVING, STABLE, DECLINING
    
    // Alert and Notification Status
    private Integer activeAlerts;
    private Integer criticalAlerts;
    private Integer warningAlerts;
    private String systemHealth; // HEALTHY, DEGRADED, CRITICAL
    
    // Resource Utilization
    private Double cpuUtilization;
    private Double memoryUtilization;
    private Double diskUtilization;
    private Double networkUtilization;
    
    // Cache and Storage Metrics
    private Double cacheHitRate;
    private Long cacheSize;
    private Long databaseConnections;
    private Double databaseResponseTime;
    
    // API Metrics
    private Long apiRequests;
    private Long successfulApiRequests;
    private Double apiSuccessRate;
    private Double averageApiResponseTime;
    
    // Security Metrics
    private Long securityIncidents;
    private Long blockedTransactions;
    private Long fraudulentTransactions;
    private Double fraudRate;
    
    // Integration Metrics
    private Map<String, String> partnerSystemStatus;
    private Map<String, Double> partnerResponseTimes;
    private Long failedIntegrations;
}