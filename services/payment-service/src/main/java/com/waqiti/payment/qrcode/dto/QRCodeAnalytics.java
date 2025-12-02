package com.waqiti.payment.qrcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analytics data for QR code usage and performance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRCodeAnalytics {
    
    private UUID id;
    private UUID qrCodeId;
    private String qrCodeReference;
    
    // Scan metrics
    private long totalScans;
    private long uniqueScans;
    private long successfulScans;
    private long failedScans;
    private double scanSuccessRate;
    
    // Payment metrics
    private long totalPayments;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private String primaryCurrency;
    private Map<String, BigDecimal> amountByCurrency;
    
    // Time-based metrics
    private Instant firstScanAt;
    private Instant lastScanAt;
    private Map<String, Long> scansByHour;
    private Map<String, Long> scansByDay;
    private Map<String, Long> scansByMonth;
    private double averageTimeToPayment; // in seconds
    
    // Geographic metrics
    private Map<String, Long> scansByCountry;
    private Map<String, Long> scansByCity;
    private String mostCommonLocation;
    
    // Device metrics
    private Map<String, Long> scansByDevice;
    private Map<String, Long> scansByOS;
    private Map<String, Long> scansByApp;
    
    // Performance metrics
    private double averageResponseTime; // in milliseconds
    private double p95ResponseTime;
    private double p99ResponseTime;
    private long timeouts;
    private long errors;
    
    // User behavior
    private double conversionRate;
    private double abandonmentRate;
    private List<String> commonFailureReasons;
    private Map<String, Long> userActions;
    
    // Campaign tracking
    private String campaignId;
    private String campaignName;
    private Map<String, Object> campaignMetrics;
    
    // Period
    private Instant periodStart;
    private Instant periodEnd;
    private String aggregationType; // HOURLY, DAILY, WEEKLY, MONTHLY
    
    // Metadata
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> customMetrics;
}